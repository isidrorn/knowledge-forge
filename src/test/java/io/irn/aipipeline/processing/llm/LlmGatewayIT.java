package io.irn.aipipeline.processing.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the LlmGateway chain wiring.
 * Verifies that the Spring context wires providers correctly and that
 * the fallback chain behaves as expected with stub providers.
 *
 * Does NOT call real LLM endpoints — providers are replaced with stubs.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("LlmGateway — integration")
class LlmGatewayIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("ramsrib/pgvector:15")
                            .asCompatibleSubstituteFor("postgres"))
                    .withStartupTimeoutSeconds(60)
                    .withStartupAttempts(3);

    @Autowired
    LlmGateway llmGateway;

    @TestConfiguration
    static class StubProviderConfig {

        /**
         * Replaces the real OllamaProvider and OpenRouterProvider with stubs
         * so no actual LLM connectivity is needed in CI.
         * @Order(0) — ahead of OpenRouterProvider (@Order(1)) and OllamaProvider (@Order(2)).
         */
        @Order(0)
        @Bean
        LlmProvider stubPrimaryProvider() {
            return new LlmProvider() {
                @Override public String name() { return "stub-primary"; }
                @Override public LlmResponse complete(String prompt) {
                    return new LlmResponse("stub response", name(), "stub-model", 1L);
                }
            };
        }
    }

    @Test
    @DisplayName("gateway bean is correctly wired in Spring context")
    void gatewayIsWired() {
        assertThat(llmGateway).isNotNull();
        assertThat(llmGateway).isInstanceOf(ProviderChainLlmGateway.class);
    }

    @Test
    @DisplayName("complete() returns response from first available provider")
    void returnsResponseFromProvider() {
        LlmResponse response = llmGateway.complete("test prompt");

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotBlank();
        assertThat(response.providerName()).isEqualTo("stub-primary");
        assertThat(response.modelUsed()).isEqualTo("stub-model");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("complete() throws LlmUnavailableException when all providers fail")
    void throwsWhenAllFail() {
        // Build a gateway manually with a single always-failing provider
        LlmProvider alwaysFails = new LlmProvider() {
            @Override public String name() { return "always-fails"; }
            @Override public LlmResponse complete(String prompt) {
                throw new LlmProviderException(name(), new RuntimeException("simulated failure"));
            }
        };

        ProviderChainLlmGateway failingGateway = new ProviderChainLlmGateway(List.of(alwaysFails));

        assertThatThrownBy(() -> failingGateway.complete("prompt"))
                .isInstanceOf(LlmUnavailableException.class);
    }
}
