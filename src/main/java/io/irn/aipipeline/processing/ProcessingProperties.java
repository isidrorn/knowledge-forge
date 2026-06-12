package io.irn.aipipeline.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "processing")
public record ProcessingProperties(
        int maxContentChars,
        String modelName,
        int timeoutSeconds,
        int maxRetries,
        int retryMaxAttempts,
        long retryInitialIntervalMs,
        double retryMultiplier,
        String retrySchedulerCron
) {
    public ProcessingProperties {
        if (maxContentChars <= 0) maxContentChars = 20_000;
        if (modelName == null || modelName.isBlank()) modelName = "mistral";
        if (timeoutSeconds <= 0) timeoutSeconds = 300;
        if (maxRetries < 0) maxRetries = 5;
        if (retryMaxAttempts <= 0) retryMaxAttempts = 5;
        if (retryInitialIntervalMs <= 0) retryInitialIntervalMs = 5_000;
        if (retryMultiplier <= 0) retryMultiplier = 2.0;
        if (retrySchedulerCron == null || retrySchedulerCron.isBlank()) retrySchedulerCron = "0 0 2 * * *";
    }
}
