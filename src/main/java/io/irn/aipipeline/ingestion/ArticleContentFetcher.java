package io.irn.aipipeline.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fetches the HTML content of a URL and extracts clean text using jsoup.
 * Single responsibility: URL → plain text. Protocol-agnostic.
 */
@Slf4j
@Component
public class ArticleContentFetcher {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; AIPipeline/1.0)";

    /**
     * Downloads the page at {@code url} and returns its text content.
     *
     * @param url the article URL
     * @return extracted plain text (never null, may be empty if page has no body)
     * @throws ArticleFetchException if the HTTP call fails or the URL is unreachable
     */
    public String fetch(String url) {
        log.debug("Fetching article content from URL: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            String text = doc.body() != null ? doc.body().text() : "";
            log.debug("Fetched {} chars from {}", text.length(), url);
            return text;
        } catch (IOException e) {
            log.warn("Failed to fetch content from URL: {} — {}", url, e.getMessage());
            throw new ArticleFetchException("Could not fetch article from URL: " + url, e);
        }
    }
}
