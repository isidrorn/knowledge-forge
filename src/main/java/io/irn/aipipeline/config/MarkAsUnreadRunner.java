package io.irn.aipipeline.config;

import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Profile 'unread': marks all messages from the configured allowed-sender as UNSEEN.
 * Useful to reset the mailbox between test runs.
 * Activate with: --spring.profiles.active=unread
 */
@Slf4j
@Component
@Profile("unread")
@RequiredArgsConstructor
public class MarkAsUnreadRunner implements ApplicationRunner {

    private final IngestionProperties ingestionProperties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IngestionProperties.Email cfg = ingestionProperties.email();

        if (!cfg.enabled()) {
            log.warn("Profile 'unread' active but IMAP ingestion is disabled — nothing to do");
            return;
        }

        log.warn("Profile 'unread' active — marking messages as UNSEEN [sender={}]", cfg.allowedSender());

        Properties props = buildMailProperties(cfg);
        Session session = Session.getInstance(props);

        try (Store store = session.getStore("imaps")) {
            store.connect(cfg.host(), cfg.port(), cfg.username(), cfg.password());

            try (Folder inbox = store.getFolder(cfg.folder())) {
                inbox.open(Folder.READ_WRITE);

                SearchTerm term = buildSearchTerm(cfg);
                Message[] messages = inbox.search(term);

                log.info("Found {} messages to mark as unread", messages.length);
                for (Message message : messages) {
                    message.setFlag(Flags.Flag.SEEN, false);
                }
                log.warn("Marked {} messages as UNSEEN", messages.length);
            }
        }
    }

    private SearchTerm buildSearchTerm(IngestionProperties.Email cfg) {
        // Mark ALL (read + unread) from the allowed sender as unread
        if (cfg.hasSenderFilter()) {
            return new FromStringTerm(cfg.allowedSender());
        }
        // Without sender filter: mark all SEEN messages as unread
        return new FlagTerm(new Flags(Flags.Flag.SEEN), true);
    }

    private Properties buildMailProperties(IngestionProperties.Email cfg) {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", cfg.host());
        props.put("mail.imaps.port", String.valueOf(cfg.port()));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.timeout", "10000");
        props.put("mail.imaps.connectiontimeout", "10000");
        return props;
    }
}
