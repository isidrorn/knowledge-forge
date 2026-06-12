package io.irn.aipipeline.ingestion;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts text body and the first HTTP/HTTPS URL from a MIME email message.
 * Separated from {@link ImapArticlePoller} for testability.
 *
 * <p>Caveats handled:
 * <ul>
 *   <li>Calls {@code getContent()} once per part to avoid double-invocation issues.</li>
 *   <li>Uses {@code getContentType().startsWith()} instead of {@code isMimeType()} for
 *       {@code MimeMessage} constructed without a live transport session — {@code isMimeType()}
 *       can fail to parse the content-type header in that context.</li>
 *   <li>URL regex excludes single-quote {@code '} to avoid capturing trailing HTML attribute delimiters.</li>
 * </ul>
 */
@Slf4j
@Component
class EmailBodyParser {

    // Excludes ' to avoid capturing trailing quote from href='...' in HTML bodies
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&()*+,;=%]+", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts the first HTTP/HTTPS URL found in the email body.
     */
    Optional<String> extractUrl(Message message) throws Exception {
        String body = extractBody(message);
        return findFirstUrl(body);
    }

    /**
     * Extracts plain text from the message body.
     * Handles text/plain, text/html, and multipart/alternative (Gmail mobile format).
     */
    String extractBody(Message message) throws Exception {
        Object content = message.getContent();
        String contentType = message.getContentType();

        if (content instanceof String text) {
            return isHtml(contentType) ? stripHtml(text) : text;
        }
        if (content instanceof Multipart multipart) {
            return extractFromMultipart(multipart);
        }
        return "";
    }

    /**
     * Finds the first HTTP/HTTPS URL in a plain-text string.
     */
    Optional<String> findFirstUrl(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private String extractFromMultipart(Multipart multipart) throws Exception {
        String plainText = null;
        String htmlText = null;

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object partContent = part.getContent(); // getContent() called once per part
            String partContentType = part.getContentType();

            if (partContent instanceof Multipart nested) {
                // Recurse into nested multipart (e.g. multipart/mixed wrapping multipart/alternative)
                String nestedText = extractFromMultipart(nested);
                if (!nestedText.isBlank()) return nestedText;
            } else if (partContent instanceof String text) {
                if (isPlain(partContentType) && plainText == null) {
                    plainText = text;
                } else if (isHtml(partContentType) && htmlText == null) {
                    htmlText = stripHtml(text);
                }
            }
        }

        // Prefer plain text — cleaner for URL extraction via regex
        if (plainText != null && !plainText.isBlank()) return plainText;
        if (htmlText != null && !htmlText.isBlank()) return htmlText;
        return "";
    }

    private boolean isPlain(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/plain");
    }

    private boolean isHtml(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }

    private String stripHtml(String html) {
        var body = Jsoup.parse(html).body();
        return body != null ? body.text() : "";
    }
}
