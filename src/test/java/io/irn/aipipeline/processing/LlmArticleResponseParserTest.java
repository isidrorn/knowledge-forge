package io.irn.aipipeline.processing;

import io.irn.aipipeline.processing.dto.LlmArticleResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LlmArticleResponseParser")
class LlmArticleResponseParserTest {

    private LlmArticleResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new LlmArticleResponseParser(
                new JsonMapper(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
    }

    @Test
    @DisplayName("extracts and maps a valid JSON object from fenced model output")
    void parsesJsonFromFencedOutput() {
        String raw = """
                Here is the result:
                ```json
                {
                  "tldr": "Spring Boot native images reduce startup time. Build time is the main trade-off.",
                  "keyPoints": ["Native images start faster", "GraalVM performs ahead-of-time compilation", "Builds take longer"],
                  "tags": ["spring-boot", "graalvm"],
                  "difficulty": 2,
                  "markdownContent": "## Summary\\nSpring Boot applications can use GraalVM native images."
                }
                ```
                """;

        LlmArticleResponse response = parser.parse(raw);

        assertThat(response.difficulty()).isEqualTo(2);
        assertThat(response.tags()).containsExactly("spring-boot", "graalvm");
        assertThat(response.keyPoints()).hasSize(3);
    }

    @Test
    @DisplayName("throws a domain parse exception for malformed JSON arrays")
    void throwsDomainExceptionForMalformedJson() {
        String raw = """
                {
                  "difficulty": 2,
                  "keyPoints": ["specific instructions", "simplification"},
                  "tags": ["java", "clarity"],
                  "markdownContent": "summary"
                }
                """;

        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(LlmArticleParseException.class)
                .hasMessageContaining("Invalid LLM article JSON");
    }

    @Test
    @DisplayName("validates required response fields after mapping")
    void validatesRequiredFields() {
        String raw = """
                {
                  "tldr": "",
                  "keyPoints": ["one"],
                  "tags": ["java"],
                  "difficulty": 2,
                  "markdownContent": "summary"
                }
                """;

        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(LlmArticleParseException.class)
                .hasMessageContaining("failed validation")
                .hasMessageContaining("tldr");
    }
}
