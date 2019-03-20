package com.rdenarie;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 06/08/18.
 */
@WebServlet(name = "MailService", value = "/mailService")
public class MailService extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress("noreply@rank-opcvm.appspotmail.com"));
            msg.addRecipient(Message.RecipientType.TO,
                    new InternetAddress("romain.denarie@gmail.com"));
            msg.setSubject("Rank-opcvm import status");

            Entity lastDate=GetDataServlet.getLastDate();
            Query entityQuery = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey());
            PreparedQuery preparedQuery = datastore.prepare(entityQuery);
            int nbElementForLastDate = preparedQuery.countEntities(FetchOptions.Builder.withDefaults());

            Date date = (Date) lastDate.getProperty("date");
            Calendar lastCal = Calendar.getInstance();
            lastCal.setTime(date);

            Entity previousLastDate=GetDataServlet.getPreviousDate(lastDate);
            entityQuery = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(previousLastDate.getKey());
            preparedQuery = datastore.prepare(entityQuery);
            int nbElementForPreviousLastDate = preparedQuery.countEntities(FetchOptions.Builder.withDefaults());

            date = (Date) previousLastDate.getProperty("date");
            Calendar previousLastCal = Calendar.getInstance();
            previousLastCal.setTime(date);

            String result = "Dernier import ("+lastCal.get(Calendar.DAY_OF_MONTH) + "/" + (lastCal.get(Calendar.MONTH) + 1) + "/" + lastCal.get(Calendar.YEAR)+") : "+nbElementForLastDate+" éléments importés.\n";
            result += "Avant dernier import ("+previousLastCal.get(Calendar.DAY_OF_MONTH) + "/" + (previousLastCal.get(Calendar.MONTH) + 1) + "/" + previousLastCal.get(Calendar.YEAR)+") : "+nbElementForPreviousLastDate+" éléments importés.\n";

            msg.setText(result);
            Transport.send(msg);
        } catch (AddressException e) {
            // ...
        } catch (MessagingException e) {
            // ...
        }


    }
}
