package com.rdenarie;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.repackaged.org.joda.time.DateTimeField;
import com.google.appengine.repackaged.org.joda.time.DateTimeFieldType;
import com.google.appengine.repackaged.org.joda.time.Instant;

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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 06/08/18.
 */
@WebServlet(name = "MailService", value = "/mailService")
public class MailService extends HttpServlet {

    private static final Logger log = Logger.getLogger(MailService.class.getName());
    private static final String FROM = "noreply@rank-opcvm.appspotmail.com";
    private static final String TO = "romain.denarie@gmail.com";
    /** Below this share of the previous import's funds, the report is sent as an alert. */
    private static final double MIN_RATIO_TO_PREVIOUS_IMPORT = 0.5;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(FROM));
            msg.addRecipient(Message.RecipientType.TO,
                             new InternetAddress(TO));

            Entity lastDate = GetDataServlet.getLastDate();
            Query entityQuery = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey());
            PreparedQuery preparedQuery = datastore.prepare(entityQuery);
            int nbElementForLastDate = preparedQuery.countEntities(FetchOptions.Builder.withDefaults());

            Query.Filter missingValueFilter = new Query.FilterPredicate(Utils.MISSING_VALUES, Query.FilterOperator.EQUAL,
                                                                        true);
            Query
                entityQueryWithProblem =
                new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey()).setFilter(missingValueFilter);
            PreparedQuery preparedQueryWithProblem = datastore.prepare(entityQueryWithProblem);
            int nbElementWithProblem = preparedQueryWithProblem.countEntities(FetchOptions.Builder.withDefaults());

            Date date = (Date) lastDate.getProperty("date");
            Calendar lastCal = Calendar.getInstance();
            lastCal.setTime(date);

            int nbElementForPreviousLastDate = -1;
            String result = "Dernier import ("+lastCal.get(Calendar.DAY_OF_MONTH) + "/" + (lastCal.get(Calendar.MONTH) + 1) + "/" + lastCal.get(Calendar.YEAR)+") : "+nbElementForLastDate+" éléments importés.\n";
            Entity previousLastDate = GetDataServlet.getPreviousDate(lastDate);
            if (previousLastDate != null) {
                entityQuery = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(previousLastDate.getKey());
                preparedQuery = datastore.prepare(entityQuery);
                nbElementForPreviousLastDate = preparedQuery.countEntities(FetchOptions.Builder.withDefaults());
                date = (Date) previousLastDate.getProperty("date");
                Calendar previousLastCal = Calendar.getInstance();
                previousLastCal.setTime(date);
                result += "Avant dernier import ("+previousLastCal.get(Calendar.DAY_OF_MONTH) + "/" + (previousLastCal.get(Calendar.MONTH) + 1) + "/" + previousLastCal.get(Calendar.YEAR)+") : "+nbElementForPreviousLastDate+" éléments importés.\n";
            }

            result += "Elements avec probleme ("+lastCal.get(Calendar.DAY_OF_MONTH) + "/" + (lastCal.get(Calendar.MONTH) + 1) +
                    "/" + lastCal.get(Calendar.YEAR)+") : "+nbElementWithProblem+"\n";

            boolean importFailed = nbElementForLastDate == 0
                    || nbElementForLastDate < nbElementForPreviousLastDate * MIN_RATIO_TO_PREVIOUS_IMPORT;
            if (importFailed) {
                result = "ALERTE : l'import a stocké " + nbElementForLastDate + " fonds, contre " + nbElementForPreviousLastDate
                        + " la fois précédente. Il n'est pas affiché s'il est vide.\n\n" + result;
            }
            result += "\n" + summarizeRejections(lastDate.getKey().getName());
            //date and count in the subject: Gmail would otherwise thread every report into one conversation
            String importDate = lastCal.get(Calendar.DAY_OF_MONTH) + "/" + (lastCal.get(Calendar.MONTH) + 1) + "/" + lastCal.get(Calendar.YEAR);
            msg.setSubject((importFailed ? "Rank-opcvm : ALERTE import incomplet du " : "Rank-opcvm : import du ")
                    + importDate + ", " + nbElementForLastDate + " fonds");

            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(result);

            byte[] report = ReportService.generateReport();


            //Add attachments
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(report, "application/vnd.ms-excel")));
            String year = String.valueOf(lastCal.get(Calendar.YEAR));
            String month = String.valueOf(lastCal.get(Calendar.MONTH)+1);
            if (month.length()==1) {
                month="0"+month;
            }
            String day = String.valueOf(lastCal.get(Calendar.DAY_OF_MONTH));
            if (day.length()==1) {
                day="0"+day;
            }
            String filename = year+month+day+"-report.xlsx";
            attachment.setFileName(filename);
            Multipart mp = new MimeMultipart();
            mp.addBodyPart(messageBodyPart);
            mp.addBodyPart(attachment);
            msg.setContent(mp);

            Transport.send(msg);
            log.info("Import report sent to " + TO + ": " + msg.getSubject());
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Unable to send the import report", e);
        }


    }

    /** Counts the funds the import did not store, by reason, anomalies first. */
    static String summarizeRejections(String importKey) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query rejections = new Query(Utils.IMPORT_REJECTION_ENTITY)
                .setFilter(new Query.FilterPredicate(Utils.IMPORT_KEY_PROPERTY, Query.FilterOperator.EQUAL, importKey));
        Map<ExtractValueService.Rejection, Integer> countByReason = new EnumMap<>(ExtractValueService.Rejection.class);
        Map<ExtractValueService.Rejection, String> exampleByReason = new EnumMap<>(ExtractValueService.Rejection.class);
        for (Entity rejection : datastore.prepare(rejections).asIterable(FetchOptions.Builder.withChunkSize(500))) {
            ExtractValueService.Rejection reason = ExtractValueService.Rejection.valueOf((String) rejection.getProperty("reason"));
            countByReason.merge(reason, 1, Integer::sum);
            exampleByReason.putIfAbsent(reason, rejection.getProperty("fundId") + " : " + rejection.getProperty("detail"));
        }
        if (countByReason.isEmpty()) {
            return "Fonds non stockés : aucun.\n";
        }
        StringBuilder summary = new StringBuilder("Fonds non stockés, par motif :\n");
        for (boolean anomaly : new boolean[] {true, false}) {
            for (Map.Entry<ExtractValueService.Rejection, Integer> entry : countByReason.entrySet()) {
                if (entry.getKey().anomaly == anomaly) {
                    summary.append(anomaly ? "  [anomalie] " : "  ").append(entry.getKey()).append(" : ").append(entry.getValue())
                           .append("  (ex. ").append(exampleByReason.get(entry.getKey())).append(")\n");
                }
            }
        }
        return summary.toString();
    }

    /** Sends a plain-text alert, used when an import is cancelled or degraded before any data is stored. */
    public static void sendAlert(String subject, String body) {
        try {
            Message msg = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
            msg.setFrom(new InternetAddress(FROM));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(TO));
            msg.setSubject(subject);
            msg.setText(body);
            Transport.send(msg);
            log.info("Alert sent to " + TO + ": " + subject);
        } catch (MessagingException e) {
            log.log(Level.SEVERE, "Unable to send alert '" + subject + "'", e);
        }
    }
}
