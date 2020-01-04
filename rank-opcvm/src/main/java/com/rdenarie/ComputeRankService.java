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
import com.google.appengine.api.utils.SystemProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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


        Utils.setTimeZone();

        boolean force=request.getParameter("force")!=null && request.getParameter("force").equals("true");
        log.fine("Force="+force);
        String id=request.getParameter("id");

        String category=request.getParameter("categoryId");
        if (id!=null) {
            computeRank(id,force);
        } else if (category!=null) {
            computeRankByCategory(category,force);
        } else {
            computeAllRank(force);
        }
        response.getWriter().println("Finished");

    }

    private void computeRankByCategory(String category,boolean force) {
        Entity lastDate=GetDataServlet.getLastDate();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

        Query.Filter categoryFilter = new Query.FilterPredicate(Utils.CATEGORY_MS_PROPERTY, Query.FilterOperator.EQUAL, category);


        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey()).setFilter(categoryFilter);

        PreparedQuery preparedQuery = datastore.prepare(q);
        QueryResultIterator<Entity> resultsIterator = preparedQuery.asQueryResultIterator(fetchOptions);
        while (resultsIterator.hasNext()) {
            Queue queue;
            if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Production) {
                queue = QueueFactory.getQueue("slow-queue");
            } else {
                queue = QueueFactory.getDefaultQueue();

            }
            queue.addAsync(TaskOptions.Builder.withUrl("/computeRankService").method(TaskOptions.Method.GET)
                    .param("id",resultsIterator.next().getProperty("id").toString())
                    .param("force",""+force)
                    .retryOptions(RetryOptions.Builder.withTaskRetryLimit(1)));

        }
    }

    private void computeRank(String id, boolean force) {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query.Filter filter = new Query.FilterPredicate("id", Query.FilterOperator.EQUAL,id);
        Query entityQuery = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(GetDataServlet.getLastDate().getKey()).setFilter(filter);

        PreparedQuery preparedQuery = datastore.prepare(entityQuery);
        Entity data = preparedQuery.asList(FetchOptions.Builder.withDefaults()).get(0);

        if (force ||data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY)==null
                || data.getProperty(Utils.CATEGORY_RANK_PROPERTY)==null
                || data.getProperty(Utils.NUMBER_OF_CATEGORIES)==null){
            log.info("Compute Rank for id "+id);
            GetDataServlet.computeRank(data);
        } else {
            log.info("Rank exists for id "+id);
        }


    }

    private void computeAllRank(boolean force) {
        log.info("Compute all ranks");

        JsonObject categories = CategoriesService.getCategories();
        JsonArray personalCategories = categories.getAsJsonArray("personalCategories");
        int current=1;

        for (JsonElement personalCategory : personalCategories) {
            JsonArray msCategories = personalCategory.getAsJsonObject().get("categoriesMs").getAsJsonArray();
            String categoryId=personalCategory.getAsJsonObject().get("categoryName").getAsString();

            int currentMs=1;
            for (JsonElement msCategory : msCategories) {
                log.fine(msCategory.toString());
                String categoryMsId=msCategory.getAsJsonObject().get("categoryName").getAsString();
                //if (!categoryMsId.equals("Actions International Gdes Cap. Mixte")) continue;
                Queue queue;
                if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Production) {
                    queue = QueueFactory.getQueue("slow-queue");
                } else {
                    queue = QueueFactory.getDefaultQueue();

                }


                queue.addAsync(TaskOptions.Builder.withUrl("/computeRankService").method(TaskOptions.Method.GET).param(
                        "categoryId", categoryMsId).param("force", ""+force));
                log.info("Queue msCategory "+categoryMsId+" "+currentMs+"/"+msCategories.size()+"");
                currentMs++;
            }

            log.info("Queue personnal category "+categoryId+" "+current+"/"+personalCategories.size()+"");
            current++;
        }

    }
}
