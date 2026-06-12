package io.irn.aipipeline.ingestion;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration test: verifica la conectividad IMAP y el parsing de emails
 * contra un servidor GreenMail embebido. Sin Spring context.
 *
 * Es el test que ejecutar para validar la config IMAP antes de arrancar la app.
 *
 * GreenMail 2.x eliminó deliver() — los mensajes se entregan vía SMTP usando
 * la sesión que expone GreenMail, o con GreenMailUtil.sendTextEmail().
 */
@DisplayName("IMAP connection and email parsing (GreenMail)")
class ImapConnectionIT {

    private static final String USER     = "test@example.com";
    private static final String PASS     = "testpass";
    private static final String FROM     = "sender@example.com";

    // Levantamos SMTP + IMAP — necesitamos SMTP para entregar mensajes en 2.x
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(USER, PASS))
            .withPerMethodLifecycle(true); // inbox limpio por test

    private EmailBodyParser bodyParser;
    private Session smtpSession;

    @BeforeEach
    void setUp() {
        bodyParser   = new EmailBodyParser();
        smtpSession  = greenMail.getSmtp().createSession();
    }

    // -------------------------------------------------------------------------
    // Conectividad
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("connects to IMAP server and opens inbox")
    void connectsToImapAndReadsInbox() {
        assertThatNoException().isThrownBy(() -> {
            try (Store store = openImapStore();
                 Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_ONLY);
                assertThat(inbox.isOpen()).isTrue();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Entrega y lectura
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reads message after delivering a plain-text email via SMTP")
    void readsMessageAfterDelivery() throws Exception {
        sendPlainTextEmail("Article link", "https://example.com/article");

        try (Store store = openImapStore();
             Folder inbox = store.getFolder("INBOX")) {
            inbox.open(Folder.READ_ONLY);
            assertThat(inbox.getMessageCount()).isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // Extracción de URL
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extracts URL from plain-text email body")
    void extractsUrlFromPlainTextEmail() throws Exception {
        String articleUrl = "https://blog.example.com/spring-ai-guide";
        sendPlainTextEmail("Interesting article", "Check this: " + articleUrl);

        try (Store store = openImapStore();
             Folder inbox = store.getFolder("INBOX")) {
            inbox.open(Folder.READ_ONLY);
            Message msg = inbox.getMessage(1);
            assertThat(bodyParser.extractUrl(msg)).contains(articleUrl);
        }
    }

    @Test
    @DisplayName("extracts URL from multipart/alternative email (Gmail mobile format)")
    void extractsUrlFromMultipartEmail() throws Exception {
        String articleUrl = "https://blog.example.com/multipart-test";
        sendMultipartEmail("Gmail mobile email", articleUrl);

        try (Store store = openImapStore();
             Folder inbox = store.getFolder("INBOX")) {
            inbox.open(Folder.READ_ONLY);
            Message msg = inbox.getMessage(1);
            assertThat(bodyParser.extractUrl(msg)).contains(articleUrl);
        }
    }

    @Test
    @DisplayName("returns empty when email body has no URL")
    void returnsEmptyWhenNoUrl() throws Exception {
        sendPlainTextEmail("Hello", "Just a friendly message, no links.");

        try (Store store = openImapStore();
             Folder inbox = store.getFolder("INBOX")) {
            inbox.open(Folder.READ_ONLY);
            Message msg = inbox.getMessage(1);
            assertThat(bodyParser.extractUrl(msg)).isEmpty();
        }
    }

    @Test
    @DisplayName("FromStringTerm filters out emails from non-allowed sender")
    void fromStringTermFiltersUnallowedSender() throws Exception {
        // Deliver one email from an allowed sender and one from a different sender
        sendPlainTextEmailFrom("https://blog.example.com/allowed", FROM, "https://blog.example.com/allowed");
        sendPlainTextEmailFrom("https://blog.example.com/blocked", "other@example.com",
                "https://blog.example.com/blocked");

        try (Store store = openImapStore();
             Folder inbox = store.getFolder("INBOX")) {
            inbox.open(Folder.READ_ONLY);

            jakarta.mail.search.SearchTerm term = new jakarta.mail.search.AndTerm(
                    new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false),
                    new jakarta.mail.search.FromStringTerm(FROM)
            );

            Message[] filtered = inbox.search(term);
            assertThat(filtered).hasSize(1);
            assertThat(filtered[0].getFrom()[0].toString()).contains(FROM);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Store openImapStore() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", "127.0.0.1");
        props.put("mail.imap.port", String.valueOf(ServerSetupTest.IMAP.getPort()));

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect("127.0.0.1", ServerSetupTest.IMAP.getPort(), USER, PASS);
        return store;
    }

    /**
     * Envía un email text/plain vía el SMTP de GreenMail.
     * GreenMail lo enruta automáticamente al buzón del destinatario.
     */
    private void sendPlainTextEmail(String subject, String body) throws Exception {
        sendPlainTextEmailFrom(subject, FROM, body);
    }

    /**
     * Envía un email text/plain desde un remitente arbitrario.
     */
    private void sendPlainTextEmailFrom(String subject, String from, String body) throws Exception {
        MimeMessage msg = new MimeMessage(smtpSession);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(USER));
        msg.setSubject(subject);
        msg.setText(body, "utf-8", "plain");
        Transport.send(msg);
    }

    /**
     * Envía un email multipart/alternative (text/plain + text/html) — formato Gmail móvil.
     */
    private void sendMultipartEmail(String subject, String articleUrl) throws Exception {
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("Hey, read this: " + articleUrl, "utf-8", "plain");

        MimeBodyPart html = new MimeBodyPart();
        html.setText("<p>Hey, read <a href='" + articleUrl + "'>this article</a></p>", "utf-8", "html");

        MimeMultipart multipart = new MimeMultipart("alternative");
        multipart.addBodyPart(plain);
        multipart.addBodyPart(html);

        MimeMessage msg = new MimeMessage(smtpSession);
        msg.setFrom(new InternetAddress(FROM));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(USER));
        msg.setSubject(subject);
        msg.setContent(multipart);
        Transport.send(msg);
    }
}
