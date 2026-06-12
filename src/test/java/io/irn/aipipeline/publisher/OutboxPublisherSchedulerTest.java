package io.irn.aipipeline.publisher;

import io.irn.aipipeline.domain.ArticleProcessed;
import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {

    @Mock OutboxEventsRepository outboxRepository;
    @Mock MarkdownFileWriter markdownFileWriter;

    PublisherProperties props;
    OutboxPublisherScheduler scheduler;

    @BeforeEach
    void setUp() {
        PublisherProperties.GitHub githubDisabled = new PublisherProperties.GitHub(false, "", "", "", "main", "articles", "Bot", "bot@test.io");
        props = new PublisherProperties("./output", 3, "0 */1 * * * *", githubDisabled);
        scheduler = new OutboxPublisherScheduler(outboxRepository, markdownFileWriter, props);
    }

    @Test
    void drain_doesNothing_whenNoPendingEvents() {
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of());

        scheduler.drain();

        verifyNoInteractions(markdownFileWriter);
    }

    @Test
    void drain_callsWriter_forEachPendingEvent() {
        OutboxEvents e1 = buildEvent();
        OutboxEvents e2 = buildEvent();
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(e1, e2));

        scheduler.drain();

        verify(markdownFileWriter).write(e1);
        verify(markdownFileWriter).write(e2);
    }

    @Test
    void drain_incrementsAttempts_onWriterFailure() {
        OutboxEvents event = buildEvent();
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        doThrow(new PublisherException("disk full", new RuntimeException())).when(markdownFileWriter).write(event);

        scheduler.drain();

        ArgumentCaptor<OutboxEvents> captor = ArgumentCaptor.forClass(OutboxEvents.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING"); // not yet FAILED
    }

    @Test
    void drain_marksAsFailed_whenMaxAttemptsReached() {
        OutboxEvents event = buildEvent();
        event.setAttempts(2); // one more will hit max (3)
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        doThrow(new PublisherException("disk full", new RuntimeException())).when(markdownFileWriter).write(event);

        scheduler.drain();

        ArgumentCaptor<OutboxEvents> captor = ArgumentCaptor.forClass(OutboxEvents.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getAttempts()).isEqualTo(3);
    }

    // --- helpers ---

    private OutboxEvents buildEvent() {
        ArticleProcessed processed = new ArticleProcessed();
        processed.setId(UUID.randomUUID());

        OutboxEvents event = new OutboxEvents();
        event.setId(UUID.randomUUID());
        event.setArticleProcessed(processed);
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }
}
