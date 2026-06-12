package io.irn.aipipeline.processing.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fallback LLM provider via local Ollama (Spring AI ChatClient).
 * Always present in the chain — no conditional, Ollama is the last resort.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class OllamaProvider implements LlmProvider {

    private final ChatClient chatClient;
    private final OllamaProperties props;

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public LlmResponse complete(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return new LlmResponse(content, name(), props.model(),
                    System.currentTimeMillis() - start);

        } catch (Exception e) {
            throw new LlmProviderException(name(), e);
        }
    }
}
