package io.irn.aipipeline.ingestion;

import io.irn.aipipeline.config.IngestionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the IMAP poll on a fixed delay configured via {@code ingestion.email.poll-interval-seconds}.
 * Thin scheduler — all logic lives in {@link ImapArticlePoller}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final ImapArticlePoller imapArticlePoller;
    private final IngestionProperties ingestionProperties;

    @Scheduled(fixedDelayString = "${ingestion.email.poll-interval-seconds:300}000")
    public void pollImap() {
        log.debug("Scheduler triggered IMAP poll [interval={}s]",
                ingestionProperties.email().pollIntervalSeconds());
        imapArticlePoller.poll();
    }
}
