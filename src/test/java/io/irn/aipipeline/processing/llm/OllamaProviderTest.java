package io.irn.aipipeline.processing.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("OllamaProvider")
@ExtendWith(MockitoExtension.class)
class OllamaProviderTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec callResponseSpec;

    OllamaProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OllamaProvider(chatClient, new OllamaProperties("llama3.2"));
    }

    @Test
    @DisplayName("name() returns 'ollama'")
    void nameIsOllama() {
        assertThat(provider.name()).isEqualTo("ollama");
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("returns LlmResponse with correct providerName and modelUsed")
        void returnsCorrectMetadata() {
            mockChatClientResponse("response content");

            LlmResponse result = provider.complete("prompt");

            assertThat(result.providerName()).isEqualTo("ollama");
            assertThat(result.modelUsed()).isEqualTo("llama3.2");
            assertThat(result.content()).isEqualTo("response content");
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("wraps ChatClient exception in LlmProviderException")
        void wrapsException() {
            when(chatClient.prompt()).thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> provider.complete("prompt"))
                    .isInstanceOf(LlmProviderException.class)
                    .hasMessageContaining("ollama");
        }

        private void mockChatClientResponse(String content) {
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn(content);
        }
    }
}
