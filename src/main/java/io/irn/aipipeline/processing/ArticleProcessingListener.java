package io.irn.aipipeline.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Escucha ArticleReceivedEvent y delega el procesado a ArticleProcessor.
 * La separación en dos beans es necesaria para que @Transactional en ArticleProcessor.process()
 * se active a través del proxy Spring AOP — una llamada this.process() no lo haría.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleProcessingListener {

    private final ArticleProcessor articleProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleReceived(ArticleReceivedEvent event) {
        UUID articleRawId = event.articleRaw().getId();
        log.debug("ArticleReceivedEvent received [id={}]", articleRawId);
        articleProcessor.process(articleRawId);
    }
}
