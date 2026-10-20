package com.rdenarie;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 06/08/18.
 *
 * The mails of the import: its report with the Excel attachment, or an alert when it is not published. Sent through
 * Gmail's SMTP server with an app password kept in Secret Manager: since October 2026 the mails of the App Engine Mail
 * API no longer reach Gmail, without any error on the sending side.
 */
public class MailService {

    private static final Logger log = Logger.getLogger(MailService.class.getName());
    /** Gmail sends only as the account it authenticates: the report goes from and to the same address. */
    private static final String TO = "romain.denarie@gmail.com";
    private static final String FROM = TO;
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 465;
    /** Secret Manager secret holding a Gmail app password of {@link #TO}. */
    static final String PASSWORD_SECRET = "gmail-app-password";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static void sendImportReport(ImportBuilder.Result result, int previousFunds, int universe, int partners) {
        String date = result.date.format(DAY);
        StringBuilder body = new StringBuilder();
        body.append("Import du ").append(date).append(" : ").append(result.funds.size()).append(" fonds classés dans ")
            .append(result.categories.size()).append(" catégories.\n");
        body.append("Import précédent : ").append(previousFunds).append(" fonds.\n");
        body.append("Univers Morningstar : ").append(universe).append(" parts.\n");
        body.append("Fonds listés par Boursorama (partenaires et Boursomarkets) : ").append(partners).append(".\n");
        if (partners == 0) {
            body.append("ATTENTION : aucune liste Boursorama lue, l'onglet Partenaires est vide.\n");
        }
        body.append("\nFonds écartés, par motif :\n");
        if (result.rejections.isEmpty()) {
            body.append("  aucun\n");
        }
        for (Map.Entry<ImportBuilder.Rejection, Integer> entry : result.rejections.entrySet()) {
            body.append("  ").append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }

        try {
            Message msg = new MimeMessage(session());
            msg.setFrom(new InternetAddress(FROM));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(TO));
            //date and count in the subject: Gmail would otherwise thread every report into one conversation
            msg.setSubject("Rank-opcvm : import du " + date + ", " + result.funds.size() + " fonds");

            BodyPart text = new MimeBodyPart();
            text.setText(body.toString());
            Multipart parts = new MimeMultipart();
            parts.addBodyPart(text);
            byte[] report = ReportService.generateReport(result);
            if (report != null) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(report, "application/vnd.ms-excel")));
                attachment.setFileName(result.date.toString().replace("-", "") + "-report.xlsx");
                parts.addBodyPart(attachment);
            }
            msg.setContent(parts);

            send(msg);
            log.info("Import report sent to " + TO + ": " + msg.getSubject());
        } catch (MessagingException | IOException e) {
            log.log(Level.SEVERE, "Unable to send the import report", e);
        }
    }

    /** Sends a plain-text alert, used when an import is not published. */
    public static void sendAlert(String subject, String body) {
        try {
            Message msg = new MimeMessage(session());
            msg.setFrom(new InternetAddress(FROM));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(TO));
            msg.setSubject(subject);
            msg.setText(body);
            send(msg);
            log.info("Alert sent to " + TO + ": " + subject);
        } catch (MessagingException | IOException e) {
            log.log(Level.SEVERE, "Unable to send alert '" + subject + "'", e);
        }
    }

    static Session session() {
        Properties props = new Properties();
        props.put("mail.smtps.host", SMTP_HOST);
        props.put("mail.smtps.port", String.valueOf(SMTP_PORT));
        props.put("mail.smtps.auth", "true");
        //JavaMail 1.4.7 offers TLSv1 by default, which Gmail and Java 17 refuse
        props.put("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtps.connectiontimeout", "30000");
        props.put("mail.smtps.timeout", "60000");
        return Session.getInstance(props);
    }

    /** The "smtps" transport is JavaMail's own: App Engine maps only its "gm" protocol to the Mail API. */
    private static void send(Message msg) throws MessagingException, IOException {
        String password = SecretManager.access(PASSWORD_SECRET);
        Transport transport = session().getTransport("smtps");
        try {
            transport.connect(SMTP_HOST, SMTP_PORT, TO, password);
            transport.sendMessage(msg, msg.getAllRecipients());
        } finally {
            transport.close();
        }
    }
}
