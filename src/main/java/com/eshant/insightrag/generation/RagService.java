package com.eshant.insightrag.generation;

import com.eshant.insightrag.retrieval.QueryTrace;
import com.eshant.insightrag.retrieval.RetrievalResult;
import com.eshant.insightrag.retrieval.RetrievalService;
import org.springframework.stereotype.Service;

/**
 * The application-facing RAG facade: retrieve, then generate, in one call. The REST API and the
 * agent's documentation-question branch both go through here, so the full retrieve→answer flow lives
 * in exactly one place.
 */
@Service
public class RagService {

    private final RetrievalService retrievalService;
    private final AnswerGenerator answerGenerator;

    public RagService(RetrievalService retrievalService, AnswerGenerator answerGenerator) {
        this.retrievalService = retrievalService;
        this.answerGenerator = answerGenerator;
    }

    /** Answers a documentation question, returning the answer plus the retrieval trace. */
    public RagResult ask(String question) {
        RetrievalResult retrieval = retrievalService.retrieve(question);
        Answer answer = answerGenerator.generate(question, retrieval);
        return new RagResult(answer, retrieval.trace());
    }

    /** An answer paired with the trace of how its context was retrieved. */
    public record RagResult(Answer answer, QueryTrace trace) {
    }
}
