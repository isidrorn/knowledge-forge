package io.irn.aipipeline.processing;

import io.irn.aipipeline.domain.ArticleProcessed;
import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.domain.OutboxEvents;
import io.irn.aipipeline.domain.PipelineStatusLog;
import io.irn.aipipeline.processing.dto.LlmArticleResponse;
import io.irn.aipipeline.repos.ArticleProcessedRepository;
import io.irn.aipipeline.repos.ArticleRawRepository;
import io.irn.aipipeline.repos.OutboxEventsRepository;
import io.irn.aipipeline.repos.PipelineStatusLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleProcessor {

    static final String STATUS_PROCESSING      = "PROCESSING";
    static final String STATUS_DONE            = "DONE";
    static final String STATUS_FAILED          = "FAILED";
    static final String STATUS_TOO_LARGE       = "TOO_LARGE";
    static final String REASON_PREFIX_TIMEOUT  = "TIMEOUT:";
    static final String REASON_PREFIX_PARSE    = "ParseError:";

    private final ArticleRawRepository        articleRawRepository;
    private final ArticleProcessedRepository  articleProcessedRepository;
    private final PipelineStatusLogRepository statusLogRepository;
    private final OutboxEventsRepository      outboxEventsRepository;
    private final LlmArticleProcessor         llmProcessor;
    private final ArticleContentPreprocessor  preprocessor;
    private final ProcessingProperties        props;

    public void process(UUID articleRawId) {
        process(articleRawId, ProcessingStrategy.STANDARD);
    }

    public void process(UUID articleRawId, ProcessingStrategy strategy) {
        Optional<ArticleRaw> opt = articleRawRepository.findById(articleRawId);
        if (opt.isEmpty()) {
            log.error("ArticleRaw not found, skipping [id={}]", articleRawId);
            return;
        }
        ArticleRaw article = opt.get();

        if (preprocessor.isTooLarge(article.getRawContent(), props.maxContentChars())) {
            log.warn("Article too large, skipping LLM [id={}, chars={}] — pending chunking (ADR-006)",
                    article.getId(), article.getRawContent().length());
            transition(article, article.getStatus(), STATUS_TOO_LARGE,
                    "Content length " + article.getRawContent().length()
                    + " exceeds max " + props.maxContentChars() + " chars. Pending chunking strategy (ADR-006).");
            return;
        }

        try {
            doProcess(article, strategy);
        } catch (Exception e) {
            log.error("Unhandled error after retries exhausted [id={}]: {}", articleRawId, e.getMessage(), e);
        }
    }

    @Retryable(
            retryFor   = {ResourceAccessException.class, Exception.class},
            noRetryFor = {IllegalArgumentException.class},
            maxAttemptsExpression    = "#{@processingProperties.retryMaxAttempts()}",
            backoff = @Backoff(
                    delayExpression      = "#{@processingProperties.retryInitialIntervalMs()}",
                    multiplierExpression = "#{@processingProperties.retryMultiplier()}",
                    maxDelay             = 60_000
            )
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doProcess(ArticleRaw article, ProcessingStrategy strategy) {
        transition(article, article.getStatus(), STATUS_PROCESSING, null);
        String prepared  = preprocessor.prepare(article.getRawContent());
        LlmArticleResponse response = llmProcessor.process(prepared, article.getUrl(), strategy);
        ArticleProcessed saved = articleProcessedRepository.save(mapToEntity(response, article));

        OutboxEvents outbox = new OutboxEvents();
        outbox.setArticleProcessed(saved);
        outbox.setEventType("ARTICLE_PUBLISHED");
        outbox.setPayload("{\"articleProcessedId\":\"" + saved.getId() + "\"}");
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setCreatedAt(OffsetDateTime.now());
        outboxEventsRepository.save(outbox);

        article.setProcessedAt(OffsetDateTime.now());
        transition(article, STATUS_PROCESSING, STATUS_DONE, null);
        log.info("Article processed [id={}, strategy={}, tags={}, difficulty={}]",
                article.getId(), strategy, response.tags(), response.difficulty());
    }

    @Recover
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverDoProcess(Exception e, ArticleRaw article, ProcessingStrategy strategy) {
        String reason = e instanceof ResourceAccessException
                ? REASON_PREFIX_TIMEOUT + " " + e.getMessage()
                : REASON_PREFIX_PARSE + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
        log.error("Processing failed after {} attempts [id={}, strategy={}]: {}",
                props.retryMaxAttempts(), article.getId(), strategy, reason);
        articleRawRepository.findById(article.getId()).ifPresentOrElse(
                managed -> {
                    managed.setRetryCount(managed.getRetryCount() + 1);
                    transition(managed, STATUS_PROCESSING, STATUS_FAILED, reason);
                },
                () -> log.error("Could not reload ArticleRaw for recovery [id={}]", article.getId())
        );
    }

    void transition(ArticleRaw article, String from, String to, String reason) {
        article.setStatus(to);
        articleRawRepository.save(article);
        PipelineStatusLog entry = new PipelineStatusLog();
        entry.setArticleRaw(article);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setReason(reason);
        entry.setChangedAt(OffsetDateTime.now());
        statusLogRepository.save(entry);
    }

    private ArticleProcessed mapToEntity(LlmArticleResponse r, ArticleRaw raw) {
        var ap = new ArticleProcessed();
        ap.setArticleRaw(raw);
        ap.setTldr(r.tldr());
        ap.setKeyPoints(r.keyPoints());
        ap.setTags(r.tags());
        ap.setDifficulty(r.difficulty());
        ap.setMarkdownContent(r.markdownContent());
        ap.setModelUsed(props.modelName());
        ap.setStatus("PROCESSED");
        ap.setCreatedAt(OffsetDateTime.now());
        return ap;
    }
}
