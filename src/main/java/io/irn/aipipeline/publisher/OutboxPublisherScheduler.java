package io.irn.aipipeline.publisher;

import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_FAILED  = "FAILED";

    private final OutboxEventsRepository outboxRepository;
    private final MarkdownFileWriter     markdownFileWriter;
    private final PublisherProperties    props;

    @Scheduled(cron = "${publisher.scheduler-cron:0 */1 * * * *}")
    public void drain() {
        List<OutboxEvents> pending = outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(STATUS_PENDING);
        if (pending.isEmpty()) return;

        log.info("Outbox drain: {} pending events", pending.size());
        pending.forEach(this::process);
    }

    private void process(OutboxEvents event) {
        try {
            markdownFileWriter.write(event);
        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            if (attempts >= props.maxAttempts()) {
                event.setStatus(STATUS_FAILED);
                log.error("Outbox event FAILED after {} attempts [id={}]: {}", attempts, event.getId(), e.getMessage());
            } else {
                log.warn("Outbox event attempt {}/{} failed [id={}]: {}", attempts, props.maxAttempts(), event.getId(), e.getMessage());
            }
            outboxRepository.save(event);
        }
    }
}
