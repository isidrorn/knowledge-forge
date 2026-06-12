package io.irn.aipipeline.publisher.rest;

import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.publisher.MarkdownFileWriter;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/publisher")
@RequiredArgsConstructor
public class PublisherResource {

    private final OutboxEventsRepository outboxRepository;
    private final MarkdownFileWriter     markdownFileWriter;

    @GetMapping("/failed")
    public List<UUID> getFailed() {
        return outboxRepository.findAllFailed().stream()
                .map(OutboxEvents::getId)
                .toList();
    }

    @PostMapping("/retry/{outboxId}")
    public ResponseEntity<Void> retryOne(@PathVariable UUID outboxId) {
        OutboxEvents event = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("OutboxEvent not found: " + outboxId));
        event.setStatus("PENDING");
        event.setAttempts(0);
        outboxRepository.save(event);
        markdownFileWriter.write(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/retry")
    public ResponseEntity<Void> retryAll() {
        List<OutboxEvents> failed = outboxRepository.findAllFailed();
        failed.forEach(e -> {
            e.setStatus("PENDING");
            e.setAttempts(0);
            outboxRepository.save(e);
            markdownFileWriter.write(e);
        });
        log.info("Bulk retry triggered for {} failed outbox events", failed.size());
        return ResponseEntity.accepted().build();
    }
}
