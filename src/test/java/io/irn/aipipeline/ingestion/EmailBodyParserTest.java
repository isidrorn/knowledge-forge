package io.irn.aipipeline.ingestion;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailBodyParser")
class EmailBodyParserTest {

    private EmailBodyParser parser;
    private Session session;

    @BeforeEach
    void setUp() {
        parser = new EmailBodyParser();
        session = Session.getInstance(new Properties());
    }

    @Nested
    @DisplayName("findFirstUrl")
    class FindFirstUrl {

        @Test
        @DisplayName("extracts URL from plain text")
        void extractsUrlFromPlainText() {
            Optional<String> url = parser.findFirstUrl("Check this out: https://example.com/article/123");
            assertThat(url).contains("https://example.com/article/123");
        }

        @Test
        @DisplayName("extracts first URL when multiple present")
        void extractsFirstUrlWhenMultiple() {
            Optional<String> url = parser.findFirstUrl(
                    "See https://first.com and also https://second.com");
            assertThat(url).contains("https://first.com");
        }

        @Test
        @DisplayName("returns empty when no URL present")
        void returnsEmptyWhenNoUrl() {
            assertThat(parser.findFirstUrl("Hello, no links here")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for blank input")
        void returnsEmptyForBlank() {
            assertThat(parser.findFirstUrl("   ")).isEmpty();
            assertThat(parser.findFirstUrl(null)).isEmpty();
        }

        @Test
        @DisplayName("handles URL with query params and fragments")
        void handlesComplexUrl() {
            String url = "https://example.com/path?q=spring+ai&page=1#section";
            assertThat(parser.findFirstUrl("Read this: " + url)).contains(url);
        }
    }

    @Nested
    @DisplayName("extractBody — text/plain message")
    class PlainTextMessage {

        @Test
        @DisplayName("returns plain text as-is")
        void returnsPlainText() throws Exception {
            MimeMessage msg = new MimeMessage(session);
            msg.setContent("Hello https://example.com/article", "text/plain");
            msg.saveChanges();

            String body = parser.extractBody(msg);
            assertThat(body).contains("https://example.com/article");
        }
    }

    @Nested
    @DisplayName("extractBody — text/html message")
    class HtmlMessage {

        @Test
        @DisplayName("strips HTML tags and returns text")
        void stripsHtmlTags() throws Exception {
            MimeMessage msg = new MimeMessage(session);
            // Use setText with subtype "html" — consistent with how MimeBodyPart works
            msg.setText("<p>Read <a href='https://example.com'>this article</a></p>", "utf-8", "html");
            msg.saveChanges();

            String body = parser.extractBody(msg);
            assertThat(body).contains("Read this article");
        }
    }

    @Nested
    @DisplayName("extractBody — multipart/alternative (Gmail mobile format)")
    class MultipartAlternative {

        @Test
        @DisplayName("prefers text/plain over text/html")
        void prefersPlainTextOverHtml() throws Exception {
            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart plainPart = new MimeBodyPart();
            plainPart.setText("Plain: https://example.com/from-plain", "utf-8", "plain");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setText("<p><a href='https://example.com/from-html'>link</a></p>", "utf-8", "html");

            multipart.addBodyPart(plainPart);
            multipart.addBodyPart(htmlPart);

            MimeMessage msg = new MimeMessage(session);
            msg.setContent(multipart);
            msg.saveChanges();

            String body = parser.extractBody(msg);
            assertThat(body).contains("https://example.com/from-plain");
        }

        @Test
        @DisplayName("falls back to text/html when no text/plain part")
        void fallsBackToHtmlWhenNoPlain() throws Exception {
            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setText("<p>Check <a href='https://example.com'>this</a></p>", "utf-8", "html");
            multipart.addBodyPart(htmlPart);

            MimeMessage msg = new MimeMessage(session);
            msg.setContent(multipart);
            msg.saveChanges();

            String body = parser.extractBody(msg);
            assertThat(body).isNotBlank();
        }

        @Test
        @DisplayName("handles nested multipart (wrapped multipart/alternative inside multipart/mixed)")
        void handlesNestedMultipart() throws Exception {
            MimeMultipart inner = new MimeMultipart("alternative");
            MimeBodyPart plain = new MimeBodyPart();
            plain.setText("Nested: https://example.com/nested", "utf-8", "plain");
            inner.addBodyPart(plain);

            MimeBodyPart outerPart = new MimeBodyPart();
            outerPart.setContent(inner);

            MimeMultipart outer = new MimeMultipart("mixed");
            outer.addBodyPart(outerPart);

            MimeMessage msg = new MimeMessage(session);
            msg.setContent(outer);
            msg.saveChanges();

            String body = parser.extractBody(msg);
            assertThat(body).contains("https://example.com/nested");
        }
    }

    @Nested
    @DisplayName("extractUrl (end-to-end)")
    class ExtractUrl {

        @Test
        @DisplayName("extracts URL from multipart Gmail-style email")
        void extractsUrlFromGmailStyleEmail() throws Exception {
            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart plain = new MimeBodyPart();
            plain.setText("Hey, read this: https://blog.example.com/spring-ai-guide", "utf-8", "plain");
            multipart.addBodyPart(plain);

            MimeBodyPart html = new MimeBodyPart();
            html.setText("<p>Hey, read <a href='https://blog.example.com/spring-ai-guide'>this</a></p>", "utf-8", "html");
            multipart.addBodyPart(html);

            MimeMessage msg = new MimeMessage(session);
            msg.setContent(multipart);
            msg.saveChanges();

            Optional<String> url = parser.extractUrl(msg);
            assertThat(url).contains("https://blog.example.com/spring-ai-guide");
        }
    }
}
