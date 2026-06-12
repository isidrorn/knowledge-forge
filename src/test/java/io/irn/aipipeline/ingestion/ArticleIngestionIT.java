package io.irn.aipipeline.ingestion;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.repos.ArticleRawRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import io.irn.aipipeline.processing.LlmArticleProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full integration test: Spring context + Testcontainers (PostgreSQL) + GreenMail (IMAP).
 * Validates the ArticleIngestionService with a real DB.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("ArticleIngestionService — integration")
class ArticleIngestionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("ramsrib/pgvector:15")
                            .asCompatibleSubstituteFor("postgres")).withStartupTimeoutSeconds(60).withStartupAttempts(3);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("test@example.com", "testpass"))
            .withPerMethodLifecycle(false);

    @Autowired
    private ArticleIngestionService ingestionService;

    @Autowired
    private ArticleRawRepository repository;

    @TestConfiguration
    static class MockFetcherConfig {
        /**
         * Evita HTTP real en tests de ingestión.
         */
        @Bean
        ArticleContentFetcher articleContentFetcher() {
            ArticleContentFetcher mock = mock(ArticleContentFetcher.class);
            when(mock.fetch(anyString())).thenReturn("Mocked article content for integration test.");
            return mock;
        }

        /**
         * Evita que Spring AI intente conectar a Ollama al arrancar el contexto.
         * Este IT prueba ingestión, no processing.
         */
        @Bean
        LlmArticleProcessor llmArticleProcessor() {
            return mock(LlmArticleProcessor.class);
        }
    }

    @Test
    @DisplayName("persists article with status RECEIVED")
    void persistsArticleWithStatusReceived() {
        ArticleRaw result = ingestionService.ingest("MANUAL", "https://example.com/it-test-1", "Some content");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("RECEIVED");

        Optional<ArticleRaw> fromDb = repository.findByUrl("https://example.com/it-test-1");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getId()).isEqualTo(result.getId());
    }

    @Test
    @DisplayName("is idempotent — second call with same URL returns existing record")
    void isIdempotentOnSameUrl() {
        String url = "https://example.com/it-test-idempotent";

        ArticleRaw first = ingestionService.ingest("MANUAL", url, "Content A");
        ArticleRaw second = ingestionService.ingest("MANUAL", url, "Content B — different");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.findAll().stream().filter(a -> url.equals(a.getUrl()))).hasSize(1);
    }

    @Test
    @DisplayName("stores SHA-256 checksum (64 chars)")
    void storesChecksum() {
        ArticleRaw result = ingestionService.ingest("MANUAL", "https://example.com/it-test-checksum", "Content to hash");

        assertThat(result.getContentChecksum())
                .isNotBlank()
                .hasSize(64)
                .matches("[a-f0-9]+");
    }

    @Test
    @DisplayName("different content produces different checksums")
    void differentContentProducesDifferentChecksums() {
        ArticleRaw a = ingestionService.ingest("MANUAL", "https://example.com/it-ck-a", "Content A");
        ArticleRaw b = ingestionService.ingest("MANUAL", "https://example.com/it-ck-b", "Content B");

        assertThat(a.getContentChecksum()).isNotEqualTo(b.getContentChecksum());
    }
}
