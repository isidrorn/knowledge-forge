package io.irn.aipipeline.publisher;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "publisher")
public record PublisherProperties(
        String outputDir,
        int maxAttempts,
        String schedulerCron,
        GitHub github
) {
    public PublisherProperties {
        if (outputDir == null || outputDir.isBlank()) outputDir = "./output";
        if (maxAttempts <= 0) maxAttempts = 3;
        if (schedulerCron == null || schedulerCron.isBlank()) schedulerCron = "0 */1 * * * *";
        if (github == null) github = new GitHub(false, "", "", "", "main", "articles", "AI Pipeline Bot", "bot@aipipeline.io");
    }

    public record GitHub(
            boolean enabled,
            String token,
            String owner,
            String repo,
            String branch,
            String pathPrefix,
            String committerName,
            String committerEmail
    ) {}
}
