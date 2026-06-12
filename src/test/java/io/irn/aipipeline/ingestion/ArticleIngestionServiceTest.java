package io.irn.aipipeline.ingestion;

import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.repos.ArticleRawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ArticleIngestionService")
@ExtendWith(MockitoExtension.class)
class ArticleIngestionServiceTest {

    @Mock
    private ArticleRawRepository repository;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private ArticleIngestionService service;

    private static final String SOURCE = "MANUAL";
    private static final String URL = "https://example.com/article";
    private static final String CONTENT = "This is the article content.";

    @Nested
    @DisplayName("ingest — new article")
    class NewArticle {

        @BeforeEach
        void setUp() {
            when(repository.findByUrl(URL)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> {
                ArticleRaw a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });
        }

        @Test
        @DisplayName("persists with status RECEIVED")
        void persistsWithStatusReceived() {
            ArticleRaw result = service.ingest(SOURCE, URL, CONTENT);
            assertThat(result.getStatus()).isEqualTo("RECEIVED");
        }

        @Test
        @DisplayName("persists correct source and url")
        void persistsSourceAndUrl() {
            ArticleRaw result = service.ingest(SOURCE, URL, CONTENT);
            assertThat(result.getSource()).isEqualTo(SOURCE);
            assertThat(result.getUrl()).isEqualTo(URL);
        }

        @Test
        @DisplayName("sets receivedAt to non-null timestamp")
        void setsReceivedAt() {
            ArticleRaw result = service.ingest(SOURCE, URL, CONTENT);
            assertThat(result.getReceivedAt()).isNotNull().isBefore(OffsetDateTime.now().plusSeconds(1));
        }

        @Test
        @DisplayName("computes and stores SHA-256 checksum (64 hex chars)")
        void computesChecksum() {
            ArgumentCaptor<ArticleRaw> captor = ArgumentCaptor.forClass(ArticleRaw.class);
            service.ingest(SOURCE, URL, CONTENT);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getContentChecksum())
                    .isNotBlank()
                    .hasSize(64)
                    .matches("[a-f0-9]+");
        }

        @Test
        @DisplayName("checksum is deterministic for same content")
        void checksumIsDeterministic() {
            ArgumentCaptor<ArticleRaw> captor = ArgumentCaptor.forClass(ArticleRaw.class);
            service.ingest(SOURCE, URL, CONTENT);
            service.ingest(SOURCE, "https://other.com", CONTENT);

            verify(repository, times(2)).save(captor.capture());
            var checksums = captor.getAllValues().stream()
                    .map(ArticleRaw::getContentChecksum).toList();
            assertThat(checksums.get(0)).isEqualTo(checksums.get(1));
        }

        @Test
        @DisplayName("sets retryCount to zero")
        void setsRetryCountToZero() {
            ArgumentCaptor<ArticleRaw> captor = ArgumentCaptor.forClass(ArticleRaw.class);
            service.ingest(SOURCE, URL, CONTENT);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getRetryCount()).isZero();
        }
    }

    @Nested
    @DisplayName("ingest — idempotency")
    class Idempotency {

        @Test
        @DisplayName("returns existing article when URL already ingested")
        void returnsExistingWhenUrlExists() {
            ArticleRaw existing = existingArticle();
            when(repository.findByUrl(URL)).thenReturn(Optional.of(existing));

            ArticleRaw result = service.ingest(SOURCE, URL, CONTENT);

            assertThat(result.getId()).isEqualTo(existing.getId());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("does not call save when URL already exists")
        void doesNotSaveWhenDuplicate() {
            when(repository.findByUrl(URL)).thenReturn(Optional.of(existingArticle()));
            service.ingest(SOURCE, URL, CONTENT);
            verify(repository, never()).save(any());
        }

        private ArticleRaw existingArticle() {
            ArticleRaw a = new ArticleRaw();
            a.setId(UUID.randomUUID());
            a.setUrl(URL);
            a.setSource(SOURCE);
            a.setStatus("RECEIVED");
            a.setRetryCount(0);
            a.setReceivedAt(OffsetDateTime.now().minusHours(1));
            return a;
        }
    }
}
