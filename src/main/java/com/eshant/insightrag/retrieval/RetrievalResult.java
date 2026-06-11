package com.eshant.insightrag.retrieval;

import java.util.List;

/**
 * The output of a retrieval call: the ranked chunks plus the {@link QueryTrace} explaining how they
 * were obtained. Pairing the two means every result the generator sees can be audited end-to-end.
 *
 * @param chunks     the final ranked chunks (already truncated to topK)
 * @param trace      the stage-by-stage record of this query's journey through the pipeline
 * @param confidence the highest <em>raw</em> cosine similarity seen from the vector arm, captured
 *                   <em>before</em> fusion/re-ranking rescore the chunks ([0,1], 0 if nothing matched).
 *                   This is the honest "did we actually find anything relevant?" signal the abstention
 *                   gate keys off — the final chunks' scores are post-rerank blends and run high even
 *                   for off-topic queries, so they can't be trusted for that decision.
 * @param lexicalConfidence the IDF-weighted keyword coverage of the best-matching chunk ([0,1]); a
 *                   phrasing-independent in-scope signal that complements {@code confidence}. The
 *                   abstention gate refuses only when <em>both</em> signals are weak, so an answerable
 *                   question still answers even when it doesn't name the framework (which would
 *                   otherwise drag the cosine confidence below threshold).
 */
public record RetrievalResult(List<RetrievedChunk> chunks, QueryTrace trace, double confidence,
                              double lexicalConfidence) {
}
