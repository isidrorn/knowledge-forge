package io.irn.aipipeline.publisher;

import io.irn.aipipeline.domain.ArticleProcessed;
import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.publisher.github.GitHubPublisher;
import io.irn.aipipeline.repos.ArticleProcessedRepository;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarkdownFileWriter {

    static final String STATUS_SENT      = "SENT";
    static final String STATUS_PUBLISHED = "PUBLISHED";

    private final PublisherProperties        props;
    private final ArticleProcessedRepository processedRepository;
    private final OutboxEventsRepository     outboxRepository;
    private final GitHubPublisher            gitHubPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OutboxEvents event) {
        ArticleProcessed processed = event.getArticleProcessed();
        String filename = ArticleFilenameResolver.resolve(processed);
        Path outputPath = Path.of(props.outputDir()).resolve(filename);

        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, processed.getMarkdownContent());
            log.info("Markdown written locally [file={}]", outputPath);
        } catch (IOException e) {
            throw new PublisherException("Failed to write markdown file: " + outputPath, e);
        }

        // GitHub push — if it fails the exception propagates, outbox stays PENDING
        gitHubPublisher.publish(processed, filename);

        processed.setStatus(STATUS_PUBLISHED);
        processed.setPublishedAt(OffsetDateTime.now());
        processedRepository.save(processed);

        event.setStatus(STATUS_SENT);
        event.setSentAt(OffsetDateTime.now());
        outboxRepository.save(event);
    }
}
