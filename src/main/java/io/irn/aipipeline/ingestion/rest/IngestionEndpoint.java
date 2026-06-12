package io.irn.aipipeline.ingestion.rest;

import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.ingestion.ArticleContentFetcher;
import io.irn.aipipeline.ingestion.ArticleIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoint for manual article ingestion.
 * Accepts a URL, fetches its content and delegates to {@link ArticleIngestionService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionEndpoint {

    private static final String SOURCE_MANUAL = "MANUAL";

    private final ArticleContentFetcher contentFetcher;
    private final ArticleIngestionService ingestionService;

    /**
     * POST /api/ingestion/articles
     * Fetches the article at the given URL and ingests it.
     *
     * @return 200 with the ingested article id (idempotent: same id if already exists)
     */
    @PostMapping("/articles")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        log.info("Manual ingestion requested [url={}]", request.url());

        String rawContent = contentFetcher.fetch(request.url());
        ArticleRaw article = ingestionService.ingest(SOURCE_MANUAL, request.url(), rawContent);

        return ResponseEntity.ok(new IngestResponse(article.getId(), article.getStatus()));
    }

    public record IngestResponse(UUID id, String status) {}
}
