package io.irn.aipipeline.processing.llm;

/**
 * Typed response from any LLM provider.
 * Carries providerName and modelUsed so the caller can persist them
 * in article_processed.model_used without knowing the provider internals.
 */
public record LlmResponse(
        String content,
        String providerName,
        String modelUsed,
        long   latencyMs
) {}
