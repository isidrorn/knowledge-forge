package io.irn.aipipeline.processing.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("ProviderChainLlmGateway")
@ExtendWith(MockitoExtension.class)
class ProviderChainLlmGatewayTest {

    @Mock LlmProvider primary;
    @Mock LlmProvider fallback;

    private static final LlmResponse OK_RESPONSE =
            new LlmResponse("result", "primary", "model-x", 100L);

    private static final String PROMPT = "test prompt";

    @Nested
    @DisplayName("complete — provider selection")
    class ProviderSelection {

        @Test
        @DisplayName("returns primary response and never calls fallback when primary succeeds")
        void returnsPrimaryAndSkipsFallback() {
            var gateway = new ProviderChainLlmGateway(List.of(primary, fallback));
            when(primary.complete(PROMPT)).thenReturn(OK_RESPONSE);

            LlmResponse result = gateway.complete(PROMPT);

            assertThat(result).isEqualTo(OK_RESPONSE);
            verifyNoInteractions(fallback);
        }

        @Test
        @DisplayName("falls back to secondary provider when primary throws LlmProviderException")
        void fallsBackWhenPrimaryFails() {
            var gateway = new ProviderChainLlmGateway(List.of(primary, fallback));
            when(primary.complete(PROMPT)).thenThrow(new LlmProviderException("primary", new RuntimeException("timeout")));
            when(fallback.complete(PROMPT)).thenReturn(OK_RESPONSE);

            LlmResponse result = gateway.complete(PROMPT);

            assertThat(result).isEqualTo(OK_RESPONSE);
            verify(primary).complete(PROMPT);
            verify(fallback).complete(PROMPT);
        }

        @Test
        @DisplayName("throws LlmUnavailableException when all providers fail")
        void throwsWhenAllProvidersFail() {
            var gateway = new ProviderChainLlmGateway(List.of(primary, fallback));
            when(primary.complete(anyString())).thenThrow(new LlmProviderException("primary", new RuntimeException()));
            when(fallback.complete(anyString())).thenThrow(new LlmProviderException("fallback", new RuntimeException()));

            assertThatThrownBy(() -> gateway.complete(PROMPT))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("All LLM providers failed");
        }

        @Test
        @DisplayName("works with a single provider that succeeds")
        void singleProviderSuccess() {
            var gateway = new ProviderChainLlmGateway(List.of(primary));
            when(primary.complete(PROMPT)).thenReturn(OK_RESPONSE);

            assertThat(gateway.complete(PROMPT)).isEqualTo(OK_RESPONSE);
        }

        @Test
        @DisplayName("throws LlmUnavailableException with single failing provider")
        void singleProviderFails() {
            var gateway = new ProviderChainLlmGateway(List.of(primary));
            when(primary.complete(anyString())).thenThrow(new LlmProviderException("primary", new RuntimeException()));

            assertThatThrownBy(() -> gateway.complete(PROMPT))
                    .isInstanceOf(LlmUnavailableException.class);
        }
    }
}
