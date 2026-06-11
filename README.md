# InsightRAG

A production-style **Retrieval-Augmented Generation (RAG)** backend that answers questions over
technical documentation — built to demonstrate **RAG robustness and failure-mode handling, proven
with measured before/after numbers**, not just a happy-path Q&A demo.

Java 21 · Spring Boot 3.3 · LangChain4j · in-process embeddings · runs **100% free and offline by
default** (no API key required).

---

## The short version (for anyone)

Most "chat with your documents" demos look great in a screenshot and then fall apart in production:
they **make things up** when they can't find an answer, and they **retrieve the wrong context** and
answer confidently anyway. That's the difference between a toy and something you'd actually ship.

InsightRAG is built around handling exactly those failure modes:

- It **refuses to guess.** Ask it something the documentation doesn't cover and it says so, instead
  of inventing a plausible-sounding answer.
- It **finds the right context** by combining two search techniques (meaning-based and keyword-based)
  and re-ranking the results.
- It **shows its work** — every answer can return the exact steps and evidence behind it.
- And critically, the improvement is **measured**: I built an evaluation harness that scores a
  "naive" version against the "improved" version on 25 test questions and reports the gains.

**The headline result: the naive pipeline confidently answered all 7 trick questions it should have
refused. The improved pipeline refused all 7 — zero confident hallucinations — while also more than
doubling retrieval precision.**

---

## Where naive RAG failed, and how I fixed it

The project ships two switchable configurations and an eval harness that runs both over the same 25
questions (18 answerable, 7 deliberately unanswerable "adversarial" questions). These are **real
numbers** from `eval/results/eval-latest.json`, produced with the free deterministic mock LLM:

| Metric | v0 — naive | v1 — improved | Δ |
|---|---:|---:|---:|
| Retrieval **precision@k** | 0.250 | **0.583** | **+0.333** |
| Retrieval **MRR** (rank of first right doc) | 0.718 | **1.000** | **+0.282** |
| Retrieval recall@k / hit-rate | 1.000 | 1.000 | — |
| Faithfulness (rule-based grounding) | 1.000 | 1.000 | — |
| Answer relevance (fact coverage) | 0.889 | 0.889 | — |
| **Abstention accuracy** | 0.720 | **1.000** | **+0.280** |
| **Confident hallucinations** (answered an unanswerable question) | **7** | **0** | **−7** |
| Correct refusals | 0 | 7 | +7 |
| Over-cautious refusals | 0 | 0 | — |

### What each change fixed

| Failure mode in v0 | Fix in v1 | Effect on the numbers |
|---|---|---|
| **Hallucinates on out-of-scope questions** — answers everything, even what isn't in the docs. | **Abstention gate** on two complementary confidence signals — raw cosine similarity (captured *before* re-ranking) **and** IDF-weighted keyword coverage. Refuse only when *both* are weak. | Confident hallucinations **7 → 0**; abstention accuracy **0.72 → 1.00**. |
| **Retrieves the wrong chunk** — pure vector search blurs exact tokens (`NIMBUS_PROFILE`, port `842`). | **Hybrid retrieval**: dense vectors **+** from-scratch BM25 keyword search, fused with **Reciprocal Rank Fusion**, then **re-ranked**. | Precision **0.25 → 0.58**; MRR **0.72 → 1.00** (the right document is now ranked #1). |
| **Splits facts from their headings** — fixed-size chunking cuts mid-section. | **Structure-aware chunking**: splits on markdown headings, keeps code blocks intact, records the heading path. | Feeds the precision/MRR gains above. |

### Why the abstention gate uses *two* signals

A cosine-similarity threshold alone is **brittle to phrasing**. all-MiniLM cosines run high and
compressed, so the gate has to sit at ~0.815 — and a perfectly answerable question asked *without* the
framework name (e.g. "What is the default connection pool size?" instead of "…in Nimbus?") drops just
below it and gets wrongly refused. Early on, that cost the eval one over-cautious refusal.

The fix is a second, **phrasing-independent** signal: **IDF-weighted keyword coverage** from the BM25
index — of the query's important terms, how many does the best-matching chunk contain? Out-of-scope
questions have distinctive terms (`smtp`, `graphql`, `kubernetes`) that appear in **no** chunk, so they
score near zero no matter how they're worded; answerable questions score high whether or not they name
the framework. The gate refuses only when **both** signals are weak. In the eval the two classes
separate cleanly (out-of-scope keyword coverage tops out at 0.40; the rescued in-corpus question scores
0.95), giving **zero hallucinations and zero over-cautious refusals**.

> The thresholds (0.815 cosine / 0.50 keyword coverage) are still **calibrated to this corpus and
> embedding model** — a different corpus would want re-calibration, and a learned cross-encoder gate
> would generalise further. The design is honest about being a tuned heuristic, not magic.

> **A note on faithfulness:** the default LLM is a deterministic **extractive mock** — it only ever
> quotes retrieved text, so it's grounded-by-construction (faithfulness 1.0 for both versions). That's
> intentional honesty: the always-real, model-independent gains come from **retrieval quality** and
> **abstention**, not from a model pretending to reason. An optional **LLM-as-judge** faithfulness
> pass activates automatically when a real Claude key is present.

---

## Architecture

```
                 ┌─────────────────────────────────────────────────────────────┐
                 │                      REST API (Spring MVC)                    │
                 │   POST /ask   POST /ingest   GET /health   (+?debug=true)     │
                 └───────────────┬───────────────────────────────┬─────────────┘
                                 │                                 │
                          ┌──────▼──────┐                          │
                          │ IntentRouter│  doc question vs. precise fact (logged)
                          └──┬───────┬──┘                          │
                  DOC_QA     │       │   STRUCTURED_DATA            │
                 ┌───────────▼─┐   ┌─▼───────────────┐             │
                 │  RagService │   │  SqlQueryTool    │  read-only  │
                 └──────┬──────┘   │  (H2, parametrised SELECTs)    │
                        │          └─────────────────┘             │
        ┌───────────────▼────────────────┐                        │
        │         RetrievalService        │                        │
        │  ┌────────────┐  ┌───────────┐  │                        │
        │  │ VectorIndex │  │ BM25 Index │  │  → RRF fuse → re-rank │
        │  │ (cosine)    │  │ (keyword)  │  │                        │
        │  └────────────┘  └───────────┘  │                        │
        └───────────────┬────────────────┘                        │
                        │  retrieved chunks + confidence + trace    │
                ┌───────▼────────┐                                 │
                │ AnswerGenerator │  abstention gates + grounding   │
                │   + LlmClient   │  (mock by default, Claude opt-in)│
                └───────┬────────┘                                 │
                        └──────────────► Answer (text, citations, grounded?, trace)

   Ingestion:  data/*.md ─► DocumentLoader ─► Structure-aware Chunker ─► embed ─► Vector + BM25 indexes

   Eval harness (the spine):  builds v0 & v1 over throwaway indexes, scores 25 cases, writes eval/results/
```

---

## How it works

**Ingestion.** Markdown docs in `data/` are loaded and split by a **structure-aware chunker** that
respects headings (recording each chunk's heading path for citation) and never splits code blocks.
Each chunk is embedded with the in-process **all-MiniLM-L6-v2** model (384-dim, ONNX, no network) and
indexed in both a vector store and a from-scratch **BM25** keyword index.

**Retrieval (v1).** Vector and BM25 each fetch a wide candidate pool; **Reciprocal Rank Fusion**
merges them on *rank* (so the two incomparable score scales need no normalisation); a deterministic
**re-ranker** then blends the fusion prior with query-term coverage and an exact-phrase bonus and
trims to the top *k*. Every query produces a `QueryTrace` of each stage.

**Generation.** A prompt instructs the model to cite sources with `[n]` markers and to abstain when
the answer isn't present. Two gates enforce trustworthiness:
1. **Pre-generation** — if retrieval is weak by **both** a vector-confidence signal (max raw cosine,
   pre-rerank) **and** a keyword-coverage signal, refuse without even calling the model.
2. **Post-generation** — a rule-based **grounding check** measures how much of the answer is supported
   by the context; an ungrounded answer is discarded in favour of abstaining.

**Agentic SQL tool.** An `IntentRouter` classifies each question as a documentation question (→ RAG)
or a precise-data question (→ SQL), and **logs every routing decision**. Structured questions hit a
read-only, parameterised query tool over an H2 table of framework releases — so "What is the *latest
version* of nimbus-core?" returns an exact `3.2.0` from data, not a paraphrase from prose.

---

## Tech stack

- **Java 21**, **Spring Boot 3.3.5** (Web, configuration properties, exception handling)
- **LangChain4j 0.35.0** — embeddings, vector store abstraction, Anthropic client
- **Embeddings:** in-process **all-MiniLM-L6-v2** (ONNX) — free, offline, deterministic
- **Vector store:** in-memory by default; **pgvector** (Postgres) via the `pgvector` profile
- **Keyword search:** **Okapi BM25** implemented from scratch (LangChain4j ships none)
- **LLM:** deterministic **mock** by default; **Anthropic Claude** (`claude-haiku-4-5`) when a key is set
- **SQL tool:** **H2** (PostgreSQL mode), seeded at startup
- **Docs:** springdoc OpenAPI / Swagger UI
- **Tests:** JUnit 5 + AssertJ + Mockito + MockMvc (33 tests)
- **Ops:** multi-stage Docker, docker-compose (app + pgvector), GitHub Actions CI

## Key design decisions

- **Free and offline by default.** No API key, no paid services — embeddings run in-process and the
  LLM is a deterministic mock. Real Claude is a drop-in upgrade behind one interface (`LlmClient`).
- **Two switchable configs, one eval.** v0 (naive) and v1 (improved) are real, runnable pipelines the
  eval compares head-to-head, so every claim of improvement is a number, not an assertion.
- **Confidence ≠ rerank score, and one signal isn't enough.** Re-ranked scores are normalised and run
  high even for off-topic queries, so the gate keys off *raw cosine confidence* instead — and pairs it
  with a phrasing-independent *keyword-coverage* signal so it refuses out-of-scope questions without
  over-refusing answerable ones. This is what makes principled refusal robust.
- **Honest metrics.** The extractive mock can't hallucinate, so the reported before/after gains come
  from retrieval and abstention — effects that are real regardless of which LLM is plugged in.
- **Everything traceable.** `QueryTrace` records every retrieval stage, candidate counts, timings, and
  the confidence score; `?debug=true` surfaces it over the API.

---

## Running it

**Prerequisites:** JDK 21 and Maven. (Docker optional.)

### Local — free, offline (mock LLM)
```bash
mvn spring-boot:run
```
- API at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.
- On startup it ingests the 6 sample docs in `data/` (6 documents → 36 chunks).

### Use real Claude (optional)
```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run            # provider=auto picks up the key
```

### Run the evaluation (the before/after numbers)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=eval
```
Prints the before/after table and writes `eval/results/eval-latest.json`.

### Run the test suite
```bash
mvn verify                     # 33 JUnit tests + packaged jar
```

### Docker (app + pgvector)
```bash
docker build -t insightrag:latest .   # standalone image (in-memory store, mock LLM)
docker compose up --build             # full stack: app on :8080 + pgvector-backed vector store
docker compose down -v                # stop and remove the pgvector volume
```

> **Verified on Docker:** both the standalone image (default in-memory profile) and the full
> `docker compose` stack (app + pgvector Postgres) have been run end-to-end — startup ingestion,
> documentation Q&A through pgvector vector search, runtime `/ingest` re-indexing, and abstention all
> work, with the 36 embeddings confirmed persisted in Postgres.

---

## API examples

**Ask a documentation question:**
```bash
curl -s -X POST localhost:8080/ask -H 'Content-Type: application/json' \
  -d '{"question":"What port does Nimbus listen on by default?"}'
```
```json
{
  "question": "What port does Nimbus listen on by default?",
  "answer": "By default the embedded server listens on port 842. [1]",
  "route": "DOC_QA",
  "abstained": false,
  "citations": ["nimbus-getting-started.md"]
}
```

**A precise-data question routes to the SQL tool:**
```bash
curl -s -X POST localhost:8080/ask -H 'Content-Type: application/json' \
  -d '{"question":"What is the latest version of nimbus-core?"}'
```
```json
{ "question": "...",
  "answer": "The latest nimbus-core release is 3.2.0, released 2024-11-05 (STABLE, Java 17+).",
  "route": "STRUCTURED_DATA", "abstained": false, "citations": [] }
```

**An out-of-scope question is refused (not hallucinated):**
```bash
curl -s -X POST localhost:8080/ask -H 'Content-Type: application/json' \
  -d '{"question":"How do I configure Nimbus to send emails over SMTP?"}'
```
```json
{ "answer": "I don't have enough information in the provided documentation to answer that.",
  "route": "DOC_QA", "abstained": true, "citations": [] }
```

**See the full trace with `?debug=true`** — note this question omits "Nimbus", so cosine `confidence`
(0.78) falls below the 0.815 gate, but the high `lexicalConfidence` (1.0) keeps it from being refused:
```bash
curl -s -X POST 'localhost:8080/ask?debug=true' -H 'Content-Type: application/json' \
  -d '{"question":"What is the default connection pool size?"}'
```
```json
{
  "answer": "The default pool size is 10 connections. [1]",
  "route": "DOC_QA", "abstained": false, "citations": ["nimbus-data-access.md"],
  "debug": {
    "routeReason": "no structured signals",
    "groundingScore": 1.0,
    "trace": {
      "strategy": "HYBRID_RERANK",
      "confidence": 0.780,
      "lexicalConfidence": 1.0,
      "totalMillis": 12,
      "stages": [
        { "name": "vector",        "candidateCount": 16, "note": "candidateK=16" },
        { "name": "keyword(bm25)",  "candidateCount": 16, "note": "candidateK=16" },
        { "name": "rrf-fusion",     "candidateCount": 16, "note": "k=60" },
        { "name": "rerank",         "candidateCount": 4,  "note": "deterministic, topK=4" }
      ]
    }
  }
}
```

Other endpoints: `POST /ingest` (re-index the corpus at runtime) and `GET /health` (status, active
LLM, document/chunk counts).

---

## Evaluation methodology

- **Dataset** (`src/main/resources/eval/dataset.json`): 25 labelled cases — 18 in-corpus (with the
  expected source document and the facts a correct answer must contain) and 7 adversarial
  out-of-corpus questions (the only correct behaviour is to abstain).
- **Retrieval metrics:** precision@k, recall@k, hit-rate, MRR — scored at source-document granularity.
- **Answer metrics:** rule-based faithfulness (always on), expected-keyword answer relevance,
  four-way abstention correctness, and optional LLM-as-judge faithfulness when a real model is set.
- **Runner:** builds v0 and v1 over their own throwaway in-memory indexes (sharing only the embedding
  model), scores every case through both, prints the comparison, and saves JSON to `eval/results/`.

---

## Folder structure

```
InsightRAG/
├── data/                         # sample docs (a fictional framework, "Nimbus") = the corpus
├── src/main/java/com/eshant/insightrag/
│   ├── config/                   # typed properties, beans, OpenAPI, live pipeline wiring
│   ├── ingestion/                # document loading
│   │   └── chunking/             # fixed-size (v0) + structure-aware (v1) chunkers
│   ├── embedding/                # embedding service over all-MiniLM
│   ├── retrieval/                # VectorIndex, BM25, RRF, re-rankers, RetrievalService, QueryTrace
│   ├── generation/               # PromptBuilder, GroundingChecker, AnswerGenerator, LLM clients
│   ├── agent/                    # IntentRouter + tools/ (read-only SQL query tool)
│   ├── api/                      # REST controller, DTOs, global exception handler
│   └── eval/                     # dataset, metrics, LLM judge, report, EvalRunner (the spine)
├── src/main/resources/
│   ├── application.yml           # config (all values env-overridable)
│   ├── schema.sql / data.sql     # H2 table seeded for the SQL tool
│   └── eval/dataset.json         # the 25 evaluation cases
├── src/test/java/...             # 33 JUnit tests across every layer
├── eval/results/                 # eval output (eval-latest.json committed as evidence)
├── Dockerfile · docker-compose.yml · .github/workflows/ci.yml
└── pom.xml
```

---

## Limitations & honest notes

- The default mock LLM is **extractive** (quotes context verbatim), so faithfulness is 1.0 by
  construction for both versions — the measured gains are in retrieval and abstention. Plug in Claude
  for generative answers and the LLM-judge faithfulness metric.
- The abstention thresholds (0.815 cosine / 0.50 keyword coverage) are **calibrated to this corpus and
  embedding model**. A different corpus would want re-calibration; a learned cross-encoder gate would
  generalise better.
- The corpus is small (6 docs), so recall@k and hit-rate are saturated at 1.0 for both versions — the
  retrieval improvement shows up in **precision and ranking (MRR)**, which is where it matters here.
