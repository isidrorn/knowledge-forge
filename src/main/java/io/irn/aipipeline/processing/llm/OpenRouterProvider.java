package io.irn.aipipeline.processing.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Primary LLM provider via OpenRouter API.
 * Disabled automatically if ai.llm.openrouter.api-key is blank/missing.
 *
 * Uses a single-permit Semaphore to enforce sequential requests — OpenRouter
 * rate-limits aggressively under burst load. An optional inter-request delay
 * (request-delay-ms) adds breathing room between calls.
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "ai.llm.openrouter.api-key")
public class OpenRouterProvider implements LlmProvider {

    private final WebClient webClient;
    private final OpenRouterProperties props;
    private final Semaphore semaphore = new Semaphore(1);

    public OpenRouterProvider(OpenRouterProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("HTTP-Referer", "https://github.com/aipipeline")
                .build();
    }

    @Override
    public String name() {
        return "openrouter";
    }

    @Override
    public LlmResponse complete(String prompt) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException(name(), "Interrupted waiting for semaphore", e);
        }
        try {
            return doComplete(prompt);
        } finally {
            if (props.requestDelayMs() > 0) {
                try {
                    Thread.sleep(props.requestDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            semaphore.release();
        }
    }

    private LlmResponse doComplete(String prompt) {
        long start = System.currentTimeMillis();
        try {
            OpenRouterResponse response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(new OpenRouterRequest(
                            props.model(),
                            List.of(new Message("user", prompt))
                    ))
                    .retrieve()
                    .bodyToMono(OpenRouterResponse.class)
                    .block(Duration.ofSeconds(props.timeoutSeconds()));

            String content = response.choices().getFirst().message().content();
            return new LlmResponse(content, name(), props.model(),
                    System.currentTimeMillis() - start);

        } catch (WebClientResponseException e) {
            throw new LlmProviderException(name(), openRouterErrorDetail(e), e);
        } catch (Exception e) {
            throw new LlmProviderException(name(), e);
        }
    }

    private static String openRouterErrorDetail(WebClientResponseException e) {
        String detail = "%s %s".formatted(e.getStatusCode().value(), e.getStatusText());
        if (e.getRequest() != null) {
            detail += " from %s %s".formatted(e.getRequest().getMethod(), e.getRequest().getURI());
        }
        String responseBody = e.getResponseBodyAsString();
        if (!responseBody.isBlank()) {
            detail += ": " + truncate(responseBody, 1000);
        }
        return detail;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    record OpenRouterRequest(String model, List<Message> messages) {}

    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenRouterResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}
}
