package com.eshant.insightrag.api;

import com.eshant.insightrag.agent.AgentResponse;
import com.eshant.insightrag.agent.AgentService;
import com.eshant.insightrag.api.dto.AskRequest;
import com.eshant.insightrag.api.dto.AskResponse;
import com.eshant.insightrag.api.dto.HealthResponse;
import com.eshant.insightrag.api.dto.IngestResponse;
import com.eshant.insightrag.generation.LlmClient;
import com.eshant.insightrag.ingestion.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public HTTP surface: ask a question, re-ingest the corpus, check health. All real work is
 * delegated to the {@link AgentService} (which routes between RAG and SQL) and
 * {@link IngestionService}; this layer just validates input and maps to/from DTOs.
 */
@RestController
@Tag(name = "InsightRAG", description = "Ask questions, manage the corpus, check health")
public class ApiController {

    private final AgentService agentService;
    private final IngestionService ingestionService;
    private final LlmClient llmClient;

    public ApiController(AgentService agentService, IngestionService ingestionService, LlmClient llmClient) {
        this.agentService = agentService;
        this.ingestionService = ingestionService;
        this.llmClient = llmClient;
    }

    @Operation(summary = "Ask a question",
            description = "Routes to RAG or the SQL tool. Pass debug=true for routing + retrieval trace.")
    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request,
                           @RequestParam(defaultValue = "false") boolean debug) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("'question' must not be blank");
        }
        AgentResponse response = agentService.ask(request.question().trim());
        return AskResponse.from(response, debug);
    }

    @Operation(summary = "Re-ingest the corpus",
            description = "Reloads and re-indexes the documents in the configured data directory.")
    @PostMapping("/ingest")
    public IngestResponse ingest() {
        IngestionService.IngestionStats stats = ingestionService.ingest();
        return new IngestResponse(stats.documents(), stats.chunks(),
                "Re-indexed " + stats.documents() + " document(s).");
    }

    @Operation(summary = "Health check")
    @GetMapping("/health")
    public HealthResponse health() {
        IngestionService.IngestionStats stats = ingestionService.lastStats();
        return new HealthResponse("UP", llmClient.name(), stats.documents(), stats.chunks());
    }
}
