package io.irn.aipipeline.publisher;

import io.irn.aipipeline.domain.ArticleProcessed;
import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.processing.LlmArticleProcessor;
import io.irn.aipipeline.repos.ArticleProcessedRepository;
import io.irn.aipipeline.repos.ArticleRawRepository;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Publisher — integration")
class PublisherIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("ramsrib/pgvector:15")
                            .asCompatibleSubstituteFor("postgres"))
                    .withStartupTimeoutSeconds(60)
                    .withStartupAttempts(3);

    @Autowired ArticleRawRepository       rawRepository;
    @Autowired ArticleProcessedRepository processedRepository;
    @Autowired OutboxEventsRepository     outboxRepository;
    @Autowired MarkdownFileWriter         markdownFileWriter;
    @Autowired OutboxPublisherScheduler   scheduler;
    @Autowired PublisherProperties        publisherProperties;
    @Autowired JdbcClient                 jdbcClient;

    // Resolved from PublisherProperties after context starts — always consistent with what the app uses
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        jdbcClient.sql("""
                TRUNCATE TABLE outbox_events, pipeline_status_log, article_processed, article_raw
                RESTART IDENTITY CASCADE
                """).update();

        outputDir = Path.of(publisherProperties.outputDir());
        Files.createDirectories(outputDir);
        // Clear any files from previous test
        try (var stream = Files.list(outputDir)) {
            stream.forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        }
    }

    @AfterEach
    void cleanOutputDir() throws IOException {
        if (outputDir != null && Files.exists(outputDir)) {
            try (var stream = Files.list(outputDir)) {
                stream.forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }
        }
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        LlmArticleProcessor llmArticleProcessor() {
            return mock(LlmArticleProcessor.class);
        }
    }

    @Test
    @DisplayName("scheduler drains PENDING events and writes markdown file")
    void scheduler_drainsPendingEvents_andWritesFile() throws Exception {
        OutboxEvents event = buildPersistedEvent("# Hello World", "Java makes concurrency straightforward at last");

        scheduler.drain();

        Path[] files = Files.list(outputDir).toArray(Path[]::new);
        assertThat(files).hasSize(1);
        assertThat(Files.readString(files[0])).isEqualTo("# Hello World");

        OutboxEvents saved = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getSentAt()).isNotNull();

        ArticleProcessed processed = processedRepository.findById(event.getArticleProcessed().getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo("PUBLISHED");
        assertThat(processed.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markdownFileWriter is idempotent — overwrites existing file on retry")
    void writer_isIdempotent_onRetry() throws Exception {
        OutboxEvents event = buildPersistedEvent("# Content v1", "Some tldr about spring retry");

        markdownFileWriter.write(event);

        event.setStatus("PENDING");
        event.getArticleProcessed().setMarkdownContent("# Content v2");
        markdownFileWriter.write(event);

        Path[] files = Files.list(outputDir).toArray(Path[]::new);
        assertThat(files).hasSize(1);
        assertThat(Files.readString(files[0])).isEqualTo("# Content v2");
    }

    @Test
    @DisplayName("scheduler marks event as FAILED after max-attempts exceeded")
    void scheduler_marksAsFailed_afterMaxAttempts() throws IOException {
        OutboxEvents event = buildPersistedEvent("# Content", "some tldr");
        event.setAttempts(2);
        outboxRepository.save(event);

        Files.delete(outputDir);
        try {
            Files.writeString(outputDir, "not a directory");
            scheduler.drain();
        } finally {
            Files.deleteIfExists(outputDir);
            Files.createDirectories(outputDir);
        }

        OutboxEvents saved = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("findAllFailed returns only FAILED outbox events")
    void findAllFailed_returnsFailed() {
        OutboxEvents e1 = buildPersistedEvent("# Content", "some tldr");
        e1.setStatus("FAILED");
        outboxRepository.save(e1);

        List<OutboxEvents> failed = outboxRepository.findAllFailed();
        assertThat(failed).hasSize(1);
        assertThat(failed.getFirst().getId()).isEqualTo(e1.getId());
    }

    // --- helpers ---

    private OutboxEvents buildPersistedEvent(String markdownContent, String tldr) {
        ArticleRaw raw = new ArticleRaw();
        raw.setSource("MANUAL");
        raw.setUrl("https://example.com/" + UUID.randomUUID());
        raw.setRawContent("raw content");
        raw.setContentChecksum("a".repeat(64));
        raw.setStatus("DONE");
        raw.setRetryCount(0);
        raw.setReceivedAt(OffsetDateTime.now());
        rawRepository.save(raw);

        ArticleProcessed processed = new ArticleProcessed();
        processed.setArticleRaw(raw);
        processed.setTldr(tldr);
        processed.setMarkdownContent(markdownContent);
        processed.setModelUsed("test-model");
        processed.setStatus("PROCESSED");
        processed.setCreatedAt(OffsetDateTime.now());
        processedRepository.save(processed);

        OutboxEvents event = new OutboxEvents();
        event.setArticleProcessed(processed);
        event.setEventType("ARTICLE_PUBLISHED");
        event.setPayload("{\"articleProcessedId\":\"" + processed.getId() + "\"}");
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(OffsetDateTime.now());
        return outboxRepository.save(event);
    }
}
