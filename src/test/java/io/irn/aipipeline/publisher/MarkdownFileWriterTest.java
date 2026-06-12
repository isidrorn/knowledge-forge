package io.irn.aipipeline.publisher;

import io.irn.aipipeline.domain.ArticleProcessed;
import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.repos.ArticleProcessedRepository;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import io.irn.aipipeline.publisher.github.GitHubPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarkdownFileWriterTest {

    @TempDir
    Path tempDir;

    @Mock ArticleProcessedRepository processedRepository;
    @Mock OutboxEventsRepository outboxRepository;

    MarkdownFileWriter writer;

    private static final PublisherProperties.GitHub GITHUB_DISABLED =
            new PublisherProperties.GitHub(false, "", "", "", "main", "articles", "Bot", "bot@test.io");

    @BeforeEach
    void setUp() {
        PublisherProperties props = new PublisherProperties(tempDir.toString(), 3, "0 */1 * * * *", GITHUB_DISABLED);
        GitHubPublisher gitHubPublisher = new GitHubPublisher(props);
        gitHubPublisher.init();
        writer = new MarkdownFileWriter(props, processedRepository, outboxRepository, gitHubPublisher);
    }

    @Test
    void write_createsMarkdownFile() throws IOException {
        OutboxEvents event = buildEvent("Spring Boot makes things easy and productive for developers", "# Content");

        writer.write(event);

        Path[] files = Files.list(tempDir).toArray(Path[]::new);
        assertThat(files).hasSize(1);
        assertThat(Files.readString(files[0])).isEqualTo("# Content");
        assertThat(files[0].getFileName().toString()).contains("spring-boot-makes-things-easy-and");
    }

    @Test
    void write_updatesOutboxToSent() {
        OutboxEvents event = buildEvent("Some tldr text", "# Content");

        writer.write(event);

        ArgumentCaptor<OutboxEvents> captor = ArgumentCaptor.forClass(OutboxEvents.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        assertThat(captor.getValue().getSentAt()).isNotNull();
    }

    @Test
    void write_updatesArticleProcessedToPublished() {
        OutboxEvents event = buildEvent("Some tldr text", "# Content");

        writer.write(event);

        ArgumentCaptor<ArticleProcessed> captor = ArgumentCaptor.forClass(ArticleProcessed.class);
        verify(processedRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    void write_usesIdAsSlug_whenTldrIsBlank() throws IOException {
        UUID id = UUID.randomUUID();
        OutboxEvents event = buildEvent("", "# Content", id);

        writer.write(event);

        Path[] files = Files.list(tempDir).toArray(Path[]::new);
        assertThat(files[0].getFileName().toString()).contains(id.toString());
    }

    @Test
    void write_throwsPublisherException_onIoFailure() throws IOException {
        // Make outputDir a file so createDirectories fails
        Path blockedDir = tempDir.resolve("blocked");
        Files.createFile(blockedDir);
        PublisherProperties.GitHub githubDisabled = new PublisherProperties.GitHub(false, "", "", "", "main", "articles", "Bot", "bot@test.io");
        PublisherProperties props = new PublisherProperties(blockedDir.toString(), 3, "0 */1 * * * *", githubDisabled);
        GitHubPublisher gitHubPublisher = new GitHubPublisher(props);
        gitHubPublisher.init();
        MarkdownFileWriter failingWriter = new MarkdownFileWriter(props, processedRepository, outboxRepository, gitHubPublisher);

        OutboxEvents event = buildEvent("some tldr", "# Content");

        assertThatThrownBy(() -> failingWriter.write(event))
                .isInstanceOf(PublisherException.class)
                .hasMessageContaining("Failed to write markdown file");
    }

    // --- helpers ---

    private OutboxEvents buildEvent(String tldr, String markdownContent) {
        return buildEvent(tldr, markdownContent, UUID.randomUUID());
    }

    private OutboxEvents buildEvent(String tldr, String markdownContent, UUID processedId) {
        ArticleProcessed processed = new ArticleProcessed();
        processed.setId(processedId);
        processed.setTldr(tldr);
        processed.setMarkdownContent(markdownContent);
        processed.setStatus("PROCESSED");
        processed.setCreatedAt(OffsetDateTime.now());

        OutboxEvents event = new OutboxEvents();
        event.setId(UUID.randomUUID());
        event.setArticleProcessed(processed);
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }
}
