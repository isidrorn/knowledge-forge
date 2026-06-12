package io.irn.aipipeline.publisher.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.irn.aipipeline.publisher.PublisherException;
import io.irn.aipipeline.publisher.PublisherProperties.GitHub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.Optional;

/**
 * Thin wrapper over the GitHub Contents API.
 *
 * PUT /repos/{owner}/{repo}/contents/{path}
 *   - Creates the file if it doesn't exist.
 *   - Updates it if it does (requires the current blob sha).
 *
 * GET /repos/{owner}/{repo}/contents/{path}
 *   - Fetches the current blob sha (needed for updates).
 *   - Returns empty if the file doesn't exist (404).
 */
@Slf4j
public class GitHubClient {

    private static final String BASE_URL = "https://api.github.com";

    private final WebClient webClient;
    private final GitHub cfg;

    public GitHubClient(GitHub cfg) {
        this.cfg = cfg;
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + cfg.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /**
     * Creates or updates a file in the repository.
     *
     * @param path    path within the repo (e.g. "articles/2025-01-01-my-slug.md")
     * @param content raw file content (will be base64-encoded internally)
     * @param message commit message
     */
    public void upsert(String path, String content, String message) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Optional<String> existingSha = getFileSha(path);

        PutRequest body = new PutRequest(
                message,
                encoded,
                existingSha.orElse(null),
                cfg.branch(),
                new Committer(cfg.committerName(), cfg.committerEmail())
        );

        try {
            webClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{path}", cfg.owner(), cfg.repo(), path)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("GitHub upsert OK [path={}, repo={}/{}]", path, cfg.owner(), cfg.repo());
        } catch (WebClientResponseException e) {
            throw new PublisherException(
                    "GitHub PUT failed [%d %s] for path=%s".formatted(e.getStatusCode().value(), e.getStatusText(), path), e);
        }
    }

    private Optional<String> getFileSha(String path) {
        try {
            FileResponse response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", cfg.owner(), cfg.repo(), path)
                    .retrieve()
                    .bodyToMono(FileResponse.class)
                    .block();
            return Optional.ofNullable(response).map(FileResponse::sha);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new PublisherException("GitHub GET sha failed for path=" + path, e);
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PutRequest(String message, String content, String sha, String branch, Committer committer) {}

    record Committer(String name, String email) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileResponse(String sha) {}
}
