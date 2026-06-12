package io.irn.aipipeline.processing.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.llm.openrouter")
public record OpenRouterProperties(
        String baseUrl,
        String apiKey,
        String model,
        int    timeoutSeconds,
        int    requestDelayMs
) {
    public OpenRouterProperties {
        if (requestDelayMs < 0) requestDelayMs = 0;
    }
}
