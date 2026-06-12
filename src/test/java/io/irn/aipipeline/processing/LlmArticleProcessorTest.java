package io.irn.aipipeline.processing;

import io.irn.aipipeline.processing.dto.LlmArticleResponse;
import io.irn.aipipeline.processing.llm.LlmGateway;
import io.irn.aipipeline.processing.llm.LlmResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("LlmArticleProcessor")
@ExtendWith(MockitoExtension.class)
class LlmArticleProcessorTest {

    @Mock LlmGateway llmGateway;

    private LlmArticleProcessor processor;

    @BeforeEach
    void setUp() {
        var parser = new LlmArticleResponseParser(
                new JsonMapper(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        processor = new LlmArticleProcessor(llmGateway, new LlmArticlePromptBuilder(), parser);
    }

    @Test
    @DisplayName("retries once with a strict JSON prompt when the first LLM response is malformed")
    void retriesWithStrictJsonPromptAfterMalformedResponse() {
        when(llmGateway.complete(anyString()))
                .thenReturn(new LlmResponse(malformedResponse(), "openrouter", "model", 10))
                .thenReturn(new LlmResponse(validResponse(), "openrouter", "model", 10));

        LlmArticleResponse response = processor.process("Spring Boot native image article", "https://example.com/article");

        assertThat(response.tldr()).contains("Native images");
        assertThat(response.keyPoints()).hasSize(3);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmGateway, times(2)).complete(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1))
                .contains("previous response was not valid JSON")
                .contains("INVALID PREVIOUS RESPONSE");
    }

    private String malformedResponse() {
        return """
                {
                  "difficulty": 2,
                  "keyPoints": ["specific instructions", "simplification"},
                  "tags": ["java", "clarity"],
                  "markdownContent": "summary"
                }
                """;
    }

    private String validResponse() {
        return """
                {
                  "tldr": "Native images reduce startup time. Build time is the main trade-off.",
                  "keyPoints": ["Native images start faster", "GraalVM compiles ahead of time", "Builds take longer"],
                  "tags": ["spring-boot", "graalvm"],
                  "difficulty": 2,
                  "markdownContent": "## Summary\\nNative images improve startup latency."
                }
                """;
    }
}
