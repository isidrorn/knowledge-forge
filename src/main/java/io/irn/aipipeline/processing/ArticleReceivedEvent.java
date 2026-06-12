package io.irn.aipipeline.processing;

import io.irn.aipipeline.domain.ArticleRaw;

/**
 * Spring Application Event publicado por ArticleIngestionService tras persistir un ArticleRaw.
 * Retenido hasta AFTER_COMMIT por el listener en ArticleProcessingService.
 */
public record ArticleReceivedEvent(ArticleRaw articleRaw) {}
