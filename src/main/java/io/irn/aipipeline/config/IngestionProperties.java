package io.irn.aipipeline.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(Email email) {

    public record Email(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            String folder,
            int pollIntervalSeconds,
            boolean markAsRead,
            int maxMessagesPerPoll,
            String allowedSender
    ) {
        public Email {
            if (folder == null || folder.isBlank()) folder = "INBOX";
            if (pollIntervalSeconds <= 0) pollIntervalSeconds = 300;
            if (maxMessagesPerPoll <= 0) maxMessagesPerPoll = 20;
        }

        public boolean hasSenderFilter() {
            return allowedSender != null && !allowedSender.isBlank();
        }
    }
}

/*
application.yml:
ingestion:
  email:
    enabled: true
    host: imap.gmail.com
    port: 993
    username: ${EMAIL_USER}
    password: ${EMAIL_APP_PASSWORD}   # contraseña de aplicación Gmail, no la real
    folder: INBOX
    poll-interval-seconds: 300
    mark-as-read: true
    max-messages-per-poll: 20
    allowed-sender: ${EMAIL_ALLOWED_SENDER:}  # vacío = sin filtro
*/
