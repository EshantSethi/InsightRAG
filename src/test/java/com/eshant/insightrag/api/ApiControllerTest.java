package com.eshant.insightrag.api;

import com.eshant.insightrag.agent.AgentResponse;
import com.eshant.insightrag.agent.AgentService;
import com.eshant.insightrag.agent.Intent;
import com.eshant.insightrag.generation.LlmClient;
import com.eshant.insightrag.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for the HTTP surface. Uses {@code @WebMvcTest} with mocked services so it exercises
 * request/response mapping, the debug flag, and the {@link GlobalExceptionHandler} fast — without
 * loading the embedding model or booting the full pipeline.
 */
@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AgentService agentService;
    @MockBean
    private IngestionService ingestionService;
    @MockBean
    private LlmClient llmClient;

    private static AgentResponse docAnswer() {
        return new AgentResponse("What port?", "The server uses port 842 [1]", Intent.DOC_QA,
                "doc keywords", false, List.of("nimbus-getting-started.md"), null, 1.0, null);
    }

    @Test
    void askReturnsAnswerAndRoute() throws Exception {
        when(agentService.ask(anyString())).thenReturn(docAnswer());

        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What port?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The server uses port 842 [1]"))
                .andExpect(jsonPath("$.route").value("DOC_QA"))
                .andExpect(jsonPath("$.abstained").value(false))
                // debug block is omitted unless ?debug=true
                .andExpect(jsonPath("$.debug").doesNotExist());
    }

    @Test
    void blankQuestionReturns400WithErrorBody() throws Exception {
        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void healthReportsStatusAndCorpusStats() throws Exception {
        when(ingestionService.lastStats()).thenReturn(new IngestionService.IngestionStats(6, 36));
        when(llmClient.name()).thenReturn("mock-extractive");

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.documents").value(6))
                .andExpect(jsonPath("$.chunks").value(36));
    }
}
