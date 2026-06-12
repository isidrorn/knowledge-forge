package io.irn.aipipeline.publisher;

import io.irn.aipipeline.domain.ArticleProcessed;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Resolves the canonical filename for a published article.
 * Format: {yyyy-MM-dd-HHmmss}-{slug}.md
 * The timestamp ensures uniqueness across multiple runs of the same article in the same day.
 */
public class ArticleFilenameResolver {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private ArticleFilenameResolver() {}

    public static String resolve(ArticleProcessed processed) {
        String timestamp = OffsetDateTime.now().format(FORMATTER);
        String slug = buildSlug(processed.getTldr(), processed.getId().toString());
        return timestamp + "-" + slug + ".md";
    }

    private static String buildSlug(String tldr, String fallbackId) {
        if (tldr == null || tldr.isBlank()) return fallbackId;
        String slug = Arrays.stream(tldr.trim().split("\\s+"))
                .limit(6)
                .collect(Collectors.joining("-"))
                .toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "")
                .replaceAll("-{2,}", "-");
        return slug.isBlank() ? fallbackId : slug;
    }
}
