package io.irn.aipipeline.processing.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual connectivity test for OpenRouterProvider.
 *
 * NOT part of the regular test suite — requires a valid OPENROUTER_API_KEY.
 * Remove @Disabled and set OPENROUTER_API_KEY to run.
 *
 * PowerShell:
 *   $env:OPENROUTER_API_KEY='YOUR_OPENROUTER_API_KEY'; $env:OPENROUTER_MODEL='openrouter/free'; .\mvnw.cmd test -Dtest=OpenRouterProviderManualTest -DfailIfNoTests=false
 */
//@Disabled("Manual test — requires live OpenRouter connectivity")
class OpenRouterProviderManualTest {

    private static final String API_KEY  = System.getenv().getOrDefault("OPENROUTER_API_KEY", "YOUR_OPENROUTER_API_KEY");
    private static final String MODEL    = System.getenv().getOrDefault("OPENROUTER_MODEL", "openrouter/free");
    private static final int    TIMEOUT  = 60;

    OpenRouterProvider provider;

    @BeforeEach
    void setUp() {
        assumeTrue(hasText(API_KEY), "Set OPENROUTER_API_KEY to run live OpenRouter manual tests");
        provider = new OpenRouterProvider(new OpenRouterProperties(
                "https://openrouter.ai/api/v1",
                API_KEY.trim(),
                MODEL,
                TIMEOUT,
                0
        ));
    }

    @Test
    @DisplayName("complete() — receives non-blank response for a simple prompt")
    void simplePromptReturnsResponse() {
        LlmResponse response = provider.complete("Reply with exactly: OK");

        System.out.println("=== OpenRouter response ===");
        System.out.println("Provider : " + response.providerName());
        System.out.println("Model    : " + response.modelUsed());
        System.out.println("Latency  : " + response.latencyMs() + "ms");
        System.out.println("Content  : " + response.content());

        assertThat(response.content()).isNotBlank();
        assertThat(response.providerName()).isEqualTo("openrouter");
        assertThat(response.modelUsed()).isEqualTo(MODEL);
        assertThat(response.latencyMs()).isPositive();
    }

    @Test
    @DisplayName("complete() — invalid API key throws LlmProviderException")
    void invalidKeyThrowsLlmProviderException() {
        OpenRouterProvider badProvider = new OpenRouterProvider(new OpenRouterProperties(
                "https://openrouter.ai/api/v1",
                "sk-or-invalid-key",
                MODEL,
                TIMEOUT,
                0
        ));

        assertThatThrownBy(() -> badProvider.complete("hello"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("openrouter");
    }

    @Test
    @DisplayName("complete() — JSON structure matches the article processing prompt")
    void articleProcessingPromptReturnsValidJson() {
        String prompt = """
                Analyze the following technical article and respond ONLY with a valid JSON object with these fields:
                - tldr (string): 2-3 sentence summary
                - keyPoints (array of strings): 3 key takeaways
                - tags (array of strings): relevant technical tags
                - difficulty (integer 1-5): complexity level

                Article:
                Spring Boot 3 introduces native compilation support via GraalVM, reducing startup time dramatically.
                Applications that previously took 2-3 seconds to start now boot in under 100ms.
                The trade-off is a longer build time during the native compilation step.
                """;

        LlmResponse response = provider.complete(prompt);

        System.out.println("=== Article processing response ===");
        System.out.println(response.content());

        assertThat(response.content()).isNotBlank();
        // Basic structural check — full parsing is LlmArticleProcessor's responsibility
        assertThat(response.content()).containsAnyOf("tldr", "keyPoints", "tags", "difficulty");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
