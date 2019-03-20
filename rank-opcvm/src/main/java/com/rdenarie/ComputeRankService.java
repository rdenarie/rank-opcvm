package com.rdenarie;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 06/08/18.
 */
@WebServlet(name = "ComputeRankService", value = "/computeRankService")
public class ComputeRankService extends HttpServlet {

    private static final Logger log = Logger.getLogger(ComputeRankService.class.getName());


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String id=request.getParameter("id");
        if (id!=null) {
            computeRank(id);
        } else {
            computeAllRank();
        }
        response.getWriter().println("Finished");

    }

    private void computeRank(String id) {


        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query.Filter filter = new Query.FilterPredicate("id", Query.FilterOperator.EQUAL,id);
        Query entityQuery = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(GetDataServlet.getLastDate().getKey()).setFilter(filter);
        PreparedQuery preparedQuery = datastore.prepare(entityQuery);
        Entity data = preparedQuery.asSingleEntity();

        if (data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY)==null){
            log.info("Compute Rank for id "+id);
            GetDataServlet.computeRank(data);
        } else {
            log.info("Rank exists for id "+id);

        }

    }

    private void computeAllRank() {
        log.info("Compute all ranks");

        Entity lastDate=GetDataServlet.getLastDate();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey());

        PreparedQuery preparedQuery = datastore.prepare(q);
        QueryResultIterator<Entity> resultsIterator = preparedQuery.asQueryResultIterator(fetchOptions);
        List<Entity> resultList = new ArrayList<>();
        while (resultsIterator.hasNext()) {

            Queue queue = QueueFactory.getQueue("slow-queue");
//        Queue queue = QueueFactory.getDefaultQueue();
            queue.addAsync(TaskOptions.Builder.withUrl("/computeRankService").method(TaskOptions.Method.GET).param("id",resultsIterator.next().getProperty("id").toString() ).retryOptions(RetryOptions.Builder.withTaskRetryLimit(1)));

        }


    }
}
