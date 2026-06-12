package io.irn.aipipeline.ingestion;

import io.irn.aipipeline.config.IngestionProperties;
import io.irn.aipipeline.domain.ArticleRaw;
import jakarta.mail.*;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

/**
 * Connects to the configured IMAP mailbox, reads unread messages,
 * extracts the article URL from the email body (HTML or plain text),
 * fetches the article content and delegates to {@link ArticleIngestionService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImapArticlePoller {

    private static final String SOURCE = "EMAIL";

    private final IngestionProperties ingestionProperties;
    private final EmailBodyParser emailBodyParser;
    private final ArticleContentFetcher contentFetcher;
    private final ArticleIngestionService ingestionService;

    /**
     * Polls the IMAP inbox and processes up to {@code maxMessagesPerPoll} unread messages.
     * Called by {@link IngestionScheduler}.
     */
    public void poll() {
        IngestionProperties.Email cfg = ingestionProperties.email();

        if (!cfg.enabled()) {
            log.debug("IMAP ingestion is disabled, skipping poll");
            return;
        }

        log.info("Starting IMAP poll [host={}, folder={}, maxMessages={}]",
                cfg.host(), cfg.folder(), cfg.maxMessagesPerPoll());

        Properties props = buildMailProperties(cfg);
        Session session = Session.getInstance(props);

        try (Store store = session.getStore("imaps")) {
            store.connect(cfg.host(), cfg.port(), cfg.username(), cfg.password());

            try (Folder inbox = store.getFolder(cfg.folder())) {
                inbox.open(cfg.markAsRead() ? Folder.READ_WRITE : Folder.READ_ONLY);

                Message[] unread = inbox.search(buildSearchTerm(cfg));

                int toProcess = Math.min(unread.length, cfg.maxMessagesPerPoll());

                if (cfg.hasSenderFilter()) {
                    log.info("Found {} unread messages from [{}], processing {}",
                            unread.length, cfg.allowedSender(), toProcess);
                } else {
                    log.info("Found {} unread messages, processing {}", unread.length, toProcess);
                }

                for (int i = 0; i < toProcess; i++) {
                    processMessage(unread[i], cfg.markAsRead());
                }
            }
        } catch (Exception e) {
            log.error("IMAP poll failed: {}", e.getMessage(), e);
        }
    }

    private void processMessage(Message message, boolean markAsRead) {
        try {
            String subject = message.getSubject();
            log.debug("Processing email: {}", subject);

            Optional<String> urlOpt = emailBodyParser.extractUrl(message);

            if (urlOpt.isEmpty()) {
                log.warn("No URL found in email [subject={}], skipping", subject);
                return;
            }

            String articleUrl = urlOpt.get();
            log.info("Extracted URL from email [subject={}, url={}]", subject, articleUrl);

            String rawContent = contentFetcher.fetch(articleUrl);
            ArticleRaw ingested = ingestionService.ingest(SOURCE, articleUrl, rawContent);
            log.info("Email processed successfully [articleId={}, url={}]", ingested.getId(), articleUrl);

            if (markAsRead) {
                message.setFlag(Flags.Flag.SEEN, true);
            }
        } catch (ArticleFetchException e) {
            log.warn("Could not fetch article content from email, skipping: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing email message: {}", e.getMessage(), e);
        }
    }

    private SearchTerm buildSearchTerm(IngestionProperties.Email cfg) {
        SearchTerm unseen = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        if (cfg.hasSenderFilter()) {
            return new AndTerm(unseen, new FromStringTerm(cfg.allowedSender()));
        }
        return unseen;
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
