package io.irn.aipipeline.processing.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback chain: tries each provider in order (@Order).
 * Returns on the first success; moves to the next on LlmProviderException.
 * Throws LlmUnavailableException if all providers fail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderChainLlmGateway implements LlmGateway {

    private final List<LlmProvider> providers;  // ordered by @Order on each bean

    @Override
    public LlmResponse complete(String prompt) {
        for (LlmProvider provider : providers) {
            try {
                LlmResponse response = provider.complete(prompt);
                log.info("llm.complete provider={} model={} latencyMs={}",
                        response.providerName(), response.modelUsed(), response.latencyMs());
                return response;  // first success wins — remaining providers are never called
            } catch (LlmProviderException e) {
                log.warn("llm.provider.failed provider={} reason={}", e.getProviderName(), e.getMessage());
                // fall through to next provider
            }
        }
        throw new LlmUnavailableException("All LLM providers failed. Check logs for details.");
    }
}
