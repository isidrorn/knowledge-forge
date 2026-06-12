package io.irn.aipipeline.ingestion.rest;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/**
 * Request body for manual article ingestion via REST.
 *
 * @param url the canonical URL of the article to ingest
 */
public record IngestRequest(
        @NotBlank(message = "url must not be blank")
        @URL(message = "url must be a valid HTTP/HTTPS URL")
        String url
) {}
