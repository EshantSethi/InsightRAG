package com.eshant.insightrag.agent;

import com.eshant.insightrag.agent.tools.SqlAnswer;
import com.eshant.insightrag.agent.tools.SqlQueryTool;
import com.eshant.insightrag.generation.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The agent entry point: routes a question, then answers it via the right tool.
 *
 * <p>This is the project's "agentic" behaviour in one place — an LLM-or-rules router picks between the
 * documentation RAG pipeline and the structured SQL tool, the decision is logged, and the answer is
 * returned with its provenance. If a question routes to SQL but no query matches (e.g. it mentioned a
 * component but was really conceptual), it falls back to RAG rather than failing — a small but
 * important bit of robustness.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final IntentRouter router;
    private final SqlQueryTool sqlTool;
    private final RagService ragService;

    public AgentService(IntentRouter router, SqlQueryTool sqlTool, RagService ragService) {
        this.router = router;
        this.sqlTool = sqlTool;
        this.ragService = ragService;
    }

    public AgentResponse ask(String question) {
        RouteDecision decision = router.route(question);

        if (decision.intent() == Intent.STRUCTURED_DATA) {
            SqlAnswer sql = sqlTool.answer(question);
            if (sql.found()) {
                return new AgentResponse(question, sql.text(), Intent.STRUCTURED_DATA,
                        decision.reason(), false, List.of(), sql.sqlUsed(), null, null);
            }
            log.info("SQL tool found no match — falling back to RAG for: \"{}\"", question);
            return answerWithRag(question, "routed to SQL but no row matched; fell back to RAG");
        }

        return answerWithRag(question, decision.reason());
    }

    private AgentResponse answerWithRag(String question, String routeReason) {
        RagService.RagResult result = ragService.ask(question);
        var answer = result.answer();
        return new AgentResponse(question, answer.text(), Intent.DOC_QA, routeReason,
                answer.abstained(), answer.citations(), null,
                answer.groundingScore(), result.trace());
    }
}
