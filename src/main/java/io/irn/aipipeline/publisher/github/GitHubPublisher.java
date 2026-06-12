package io.irn.aipipeline.publisher.github;

import io.irn.aipipeline.domain.ArticleProcessed;
import io.irn.aipipeline.publisher.PublisherProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubPublisher {

    private final PublisherProperties props;
    private GitHubClient client;

    @PostConstruct
    public void init() {
        if (props.github().enabled()) {
            client = new GitHubClient(props.github());
            log.info("GitHubPublisher enabled [repo={}/{}]", props.github().owner(), props.github().repo());
        } else {
            log.info("GitHubPublisher disabled — set publisher.github.enabled=true to activate");
        }
    }

    public boolean isEnabled() {
        return props.github().enabled();
    }

    /**
     * Pushes the article's markdown content to the configured GitHub repo.
     * The filename is the same as the local file to keep both in sync.
     */
    public void publish(ArticleProcessed processed, String filename) {
        if (!isEnabled()) return;

        String repoPath = props.github().pathPrefix() + "/" + filename;
        String commitMessage = "feat(articles): add " + filename;

        log.info("Pushing to GitHub [path={}, repo={}/{}]", repoPath, props.github().owner(), props.github().repo());
        client.upsert(repoPath, processed.getMarkdownContent(), commitMessage);
    }
}
