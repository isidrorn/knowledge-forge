package io.irn.aipipeline.processing;

import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.repos.ArticleRawRepository;
import io.irn.aipipeline.repos.PipelineStatusLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch nocturno que reencola artículos en estado FAILED según el motivo del último error.
 * TOO_LARGE se ignora explícitamente — pendiente de estrategia de chunking (ADR-006).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingRetryScheduler {

    private final ArticleRawRepository        articleRawRepository;
    private final PipelineStatusLogRepository statusLogRepository;
    private final ArticleProcessor            articleProcessor;
    private final ProcessingProperties        props;

    @Scheduled(cron = "${processing.retry-scheduler-cron:0 0 2 * * *}")
    public void retryFailed() {
        List<ArticleRaw> failed = articleRawRepository.findByStatus(ArticleProcessor.STATUS_FAILED);

        if (failed.isEmpty()) {
            log.info("Retry batch: no FAILED articles found");
            return;
        }

        log.info("Retry batch started: {} FAILED articles found", failed.size());
        int requeued = 0;
        int skipped  = 0;

        for (ArticleRaw article : failed) {
            try {
                if (article.getRetryCount() >= props.maxRetries()) {
                    log.warn("Retry batch: skipping [id={}] — max retries reached ({})",
                            article.getId(), props.maxRetries());
                    skipped++;
                    continue;
                }

                ProcessingStrategy strategy = resolveStrategy(article);
                log.info("Retry batch: requeueing [id={}, retryCount={}, strategy={}]",
                        article.getId(), article.getRetryCount(), strategy);

                articleProcessor.process(article.getId(), strategy);
                requeued++;

            } catch (Exception e) {
                // Nunca debe llegar aquí — ArticleProcessor absorbe todo, pero por seguridad
                log.error("Retry batch: unexpected error for [id={}]: {}", article.getId(), e.getMessage(), e);
            }
        }

        log.info("Retry batch finished: {} requeued, {} skipped (max retries)", requeued, skipped);
    }

    private ProcessingStrategy resolveStrategy(ArticleRaw article) {
        return statusLogRepository
                .findTopByArticleRawOrderByChangedAtDesc(article)
                .map(log -> {
                    String reason = log.getReason();
                    if (reason != null && reason.startsWith(ArticleProcessor.REASON_PREFIX_TIMEOUT)) {
                        // Timeout: reintenta con el mismo prompt, Ollama puede estar más libre
                        return ProcessingStrategy.STANDARD;
                    }
                    // Parse error u otro: prompt simplificado para reducir tamaño de respuesta
                    return ProcessingStrategy.SIMPLIFIED_PROMPT;
                })
                .orElse(ProcessingStrategy.STANDARD);
    }
}
