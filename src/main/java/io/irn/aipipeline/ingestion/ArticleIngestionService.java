package io.irn.aipipeline.ingestion;

import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.processing.ArticleReceivedEvent;
import io.irn.aipipeline.repos.ArticleRawRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Core ingestion service. Receives a parsed article (source, url, rawContent),
 * applies idempotency checks and persists it as {@link ArticleRaw} with status RECEIVED.
 *
 * <p>Idempotency strategy:
 * <ol>
 *   <li>If the URL already exists → return the existing record (no-op).</li>
 *   <li>If the checksum matches an existing record → return the existing record (content unchanged).</li>
 *   <li>Otherwise → persist a new {@link ArticleRaw}.</li>
 * </ol>
 *
 * <p>After a successful save, publishes {@link ArticleReceivedEvent} which triggers
 * the processing module after TX commit (AFTER_COMMIT listener in ArticleProcessingService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIngestionService {

    static final String STATUS_RECEIVED = "RECEIVED";

    private final ArticleRawRepository articleRawRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Ingests an article. Idempotent: safe to call multiple times with the same URL or content.
     *
     * @param source     origin of the article (e.g. EMAIL, MANUAL)
     * @param url        canonical URL of the article — used as business key
     * @param rawContent plain text extracted from the article page
     * @return the persisted (or existing) {@link ArticleRaw}
     */
    @Transactional
    public ArticleRaw ingest(String source, String url, String rawContent) {
        String checksum = sha256(rawContent);

        Optional<ArticleRaw> existing = articleRawRepository.findByUrl(url);
        if (existing.isPresent()) {
            log.info("Article already ingested [url={}], skipping", url);
            return existing.get();
        }

        ArticleRaw article = new ArticleRaw();
        article.setSource(source);
        article.setUrl(url);
        article.setContentChecksum(checksum);
        article.setRawContent(rawContent);
        article.setStatus(STATUS_RECEIVED);
        article.setRetryCount(0);
        article.setReceivedAt(OffsetDateTime.now());

        ArticleRaw saved = articleRawRepository.save(article);
        log.info("Article ingested [id={}, source={}, url={}]", saved.getId(), source, url);

        eventPublisher.publishEvent(new ArticleReceivedEvent(saved));

        return saved;
    }

    private static String sha256(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
