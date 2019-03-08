package com.rdenarie;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 06/08/18.
 */
@WebServlet(name = "QueueService", value = "/queueService")
public class QueueService extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        //todo change default queue
        Queue queue = QueueFactory.getQueue("slow-queue");
//        Queue queue = QueueFactory.getDefaultQueue();
        queue.addAsync(TaskOptions.Builder.withUrl("/storeService").method(TaskOptions.Method.GET).retryOptions(RetryOptions.Builder.withTaskRetryLimit(1)));

        response.getWriter().println("Task queued");


    }
}
