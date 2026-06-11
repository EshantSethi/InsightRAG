package com.eshant.insightrag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata surfaced at {@code /swagger-ui.html} and {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insightRagOpenApi() {
        return new OpenAPI().info(new Info()
                .title("InsightRAG API")
                .version("0.1.0")
                .description("Retrieval-Augmented Generation over technical documentation, "
                        + "with an agentic SQL tool for precise-data questions.")
                .license(new License().name("MIT")));
    }
}
