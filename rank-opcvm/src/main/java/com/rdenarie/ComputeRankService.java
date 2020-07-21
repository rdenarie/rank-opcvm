package com.rdenarie;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.utils.SystemProperty;

import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.datastore.v1.EntityOrBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.rmi.CORBA.Util;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        boolean computeAll=request.getParameter("computeAll")!=null && request.getParameter("computeAll").equals("true");
        if (id!=null) {
            computeRank(id,force);
        } else if (category!=null) {
            computeRankByCategory(category,force);
        } else if (computeAll) {
            computeAllRankWithoutQueue(force);

        } else {
            computeAllRank(force);
        }
        response.getWriter().println("Finished");

    }

    private void computeAllRankWithoutQueue(boolean force) {
        log.info("Compute all ranks");
        Entity lastDate=GetDataServlet.getLastDate();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();



        Query distinctCategoryQuery = new Query(Utils.DATA_ENTRY_ENTITY)
                .addProjection(new PropertyProjection(Utils.CATEGORY_MS_PROPERTY, String.class))
                .addProjection(new PropertyProjection(Utils.SCORE_CATEGORY, Double.class))
                .setDistinct(true)
                .setAncestor(lastDate.getKey())
                .addSort(Utils.SCORE_CATEGORY, Query.SortDirection.DESCENDING);
        PreparedQuery preparedDistinctCategoryQuery = datastore.prepare(distinctCategoryQuery);
        List<Entity> categories=preparedDistinctCategoryQuery.asList(FetchOptions.Builder.withDefaults());

        harmonizeCategoriesScores(categories);

        preparedDistinctCategoryQuery = datastore.prepare(distinctCategoryQuery);
        categories=preparedDistinctCategoryQuery.asList(FetchOptions.Builder.withDefaults());
        calculate(force, categories);
    }

    private void calculate(boolean force, List<Entity> categories) {
        Entity lastDate=GetDataServlet.getLastDate();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        int currentBatchNbResult=0;
        int totalResult=0;
        int limit=100;
        String startCursorString=null;
        Map<String, List<Entity>> entitiesByCategory = new HashMap<>();
        do {
            FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();
            fetchOptions = FetchOptions.Builder.withLimit(limit);
            if (startCursorString != null && !startCursorString.equals("")) {
                fetchOptions.startCursor(Cursor.fromWebSafeString(startCursorString)); // Where we left off
            }
            Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey());
            PreparedQuery preparedQuery = datastore.prepare(q);

            QueryResultIterator<Entity> resultsIterator = preparedQuery.asQueryResultIterator(fetchOptions);
            currentBatchNbResult=0;

            while (resultsIterator.hasNext()) {
                Entity data = resultsIterator.next();
                totalResult++;
                log.fine("Treat result "+totalResult);
                if (force ||data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY)==null
                        || data.getProperty(Utils.CATEGORY_RANK_PROPERTY)==null
                        || data.getProperty(Utils.NUMBER_OF_CATEGORIES)==null){
                    log.info("Compute Rank for id "+data.getProperty(Utils.ID_PROPERTY));
                    String categoryMs = (String)data.getProperty(Utils.CATEGORY_MS_PROPERTY);
                    if (!entitiesByCategory.containsKey(categoryMs)) {
                        //récupération de tous les fond de la meme catégorie a la meme date
                        FilterPredicate filterCategory = new FilterPredicate(Utils.CATEGORY_MS_PROPERTY, FilterOperator.EQUAL, categoryMs);
                        Query categoryQuery = new Query(Utils.DATA_ENTRY_ENTITY)
                                .setFilter(filterCategory)
                                .setAncestor(data.getParent())
                                .addSort(Utils.SCORE_FOND_PROPERTY, Query.SortDirection.DESCENDING);

                        //q.addProjection(new PropertyProjection(Utils.CATEGORY_MS_PROPERTY, String.class));
//                    categoryQuery.addProjection(new PropertyProjection(Utils.ID_PROPERTY, String.class));
//                    categoryQuery.addProjection(new PropertyProjection(Utils.SCORE_FOND_PROPERTY, String.class));

                        PreparedQuery preparedCategoryQuery = datastore.prepare(categoryQuery);
                        List<Entity> entityOfSameCategory=preparedCategoryQuery.asList(FetchOptions.Builder.withDefaults());
                        entitiesByCategory.put(categoryMs,entityOfSameCategory);
                    }
                    List<Entity> entitiesOfSameCategory=entitiesByCategory.get(categoryMs);
                    AtomicInteger i = new AtomicInteger(); // any mutable integer wrapper
                    int position = entitiesOfSameCategory.stream()
                            .peek(v -> i.incrementAndGet())
                            .anyMatch(entity -> (Double)entity.getProperty(Utils.SCORE_FOND_PROPERTY) <= (Double)data.getProperty(Utils.SCORE_FOND_PROPERTY)) ?
                            // your
                            // predicate
                            i.get() : entitiesOfSameCategory.size();
                    data.setProperty(Utils.RANK_IN_CATEGORY_PROPERTY,position);
                    data.setProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY,entitiesOfSameCategory.size());

                    AtomicInteger j = new AtomicInteger(); // any mutable integer wrapper
                    int index = categories.stream()
                            .peek(v -> j.incrementAndGet())
                            .anyMatch(cateory -> cateory.getProperty(Utils.CATEGORY_MS_PROPERTY).equals(data.getProperty(Utils.CATEGORY_MS_PROPERTY))) ? // your
                            // predicate
                            j.get() : -1;

                    data.setProperty(Utils.CATEGORY_RANK_PROPERTY,index);
                    data.setProperty(Utils.NUMBER_OF_CATEGORIES,categories.size());
                    datastore.put(data);
                    log.info("Fond "+data.getProperty(Utils.ID_PROPERTY)+" : "
                            +data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY)+"/"+data.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY)
                            +", categorie : "+data.getProperty(Utils.CATEGORY_RANK_PROPERTY)+"/"+data.getProperty(Utils.NUMBER_OF_CATEGORIES));

                } else {
                    log.info("Rank exists for id "+data.getProperty(Utils.ID_PROPERTY));
                }
                currentBatchNbResult++;
            }

            Cursor cursor = resultsIterator.getCursor();
            if (cursor != null && currentBatchNbResult == limit) {         // Are we paging? Save Cursor
                startCursorString = cursor.toWebSafeString();               // Cursors are WebSafe
            }

            log.fine("CurrentBatchNbResult="+currentBatchNbResult+", limit="+limit+", cursorString="+startCursorString);
        } while (currentBatchNbResult == limit);
    }

    private void harmonizeCategoriesScores(List<Entity> categories) {
        Entity lastDate=GetDataServlet.getLastDate();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        categories.stream().forEach(entity -> {
            log.info("Check category "+entity.getProperty(Utils.CATEGORY_MS_PROPERTY));
            FilterPredicate categoryNameFilter = new FilterPredicate(Utils.CATEGORY_MS_PROPERTY,
                    FilterOperator.EQUAL, entity.getProperty(Utils.CATEGORY_MS_PROPERTY));
            Query categoriesWithScore = new Query(Utils.DATA_ENTRY_ENTITY)
                    .setDistinct(true)
                    .setAncestor(lastDate.getKey())
                    .setFilter(categoryNameFilter)
                    .addProjection(new PropertyProjection(Utils.SCORE_CATEGORY, Double.class));
            PreparedQuery preparedCategoriesWithScore = datastore.prepare(categoriesWithScore);
            int results = preparedCategoriesWithScore.countEntities(FetchOptions.Builder.withDefaults());
            if (results!=1) {
                log.fine("Category "+entity.getProperty(Utils.CATEGORY_MS_PROPERTY)+ " found "+results+" results");
                double scoreToKeep=0d;
                String valuesToKeep="";
                int maxCorresponding=0;
                List<Entity> entitiesToCheck=preparedCategoriesWithScore.asList(FetchOptions.Builder.withDefaults());
                for (Entity entityToCheck : entitiesToCheck) {
                    FilterPredicate scoreFilter = new FilterPredicate(Utils.SCORE_CATEGORY,
                            FilterOperator.EQUAL, entityToCheck.getProperty(Utils.SCORE_CATEGORY));
                    FilterPredicate categoryNameFilterToCheck = new FilterPredicate(Utils.CATEGORY_MS_PROPERTY,
                            FilterOperator.EQUAL, entity.getProperty(Utils.CATEGORY_MS_PROPERTY));
                    Filter finalFilter = Query.CompositeFilterOperator.and(categoryNameFilterToCheck,scoreFilter);
                    Query query = new Query(Utils.DATA_ENTRY_ENTITY)
                            .setAncestor(lastDate.getKey())
                            .setFilter(finalFilter);
                    PreparedQuery preparedQuery = datastore.prepare(query);
                    int nbCateg = preparedQuery.countEntities(FetchOptions.Builder.withDefaults());
                    log.info("Found "+nbCateg+" funds for score "+entityToCheck.getProperty(Utils.SCORE_CATEGORY));
                    if (nbCateg>maxCorresponding) {
                        maxCorresponding=nbCateg;
                        scoreToKeep=(Double)entityToCheck.getProperty(Utils.SCORE_CATEGORY);
                        valuesToKeep=(String)entityToCheck.getProperty("values");
                    }

                }
                log.info("For category "+entity.getProperty(Utils.CATEGORY_MS_PROPERTY)+" we keep score : "+scoreToKeep+ ", and" +
                        " values = "+valuesToKeep);
                FilterPredicate scoreFilterToModify = new FilterPredicate(Utils.SCORE_CATEGORY,
                        FilterOperator.NOT_EQUAL, scoreToKeep);
                FilterPredicate categoryNameFilterToCheck = new FilterPredicate(Utils.CATEGORY_MS_PROPERTY,
                        FilterOperator.EQUAL, entity.getProperty(Utils.CATEGORY_MS_PROPERTY));
                Filter finalFilter = Query.CompositeFilterOperator.and(categoryNameFilterToCheck,scoreFilterToModify);
                Query queryToClean = new Query(Utils.DATA_ENTRY_ENTITY)
                        .setAncestor(lastDate.getKey())
                        .setFilter(finalFilter);
                PreparedQuery preparedQueryToClean = datastore.prepare(queryToClean);
                List<Entity> entitiesFixed= new ArrayList<>();
                List<Entity> entitiesToClean = preparedQueryToClean.asList(FetchOptions.Builder.withDefaults());
                for (Entity entity1 : entitiesToClean) {
                    entity1.setProperty(Utils.SCORE_CATEGORY,scoreToKeep);
                    entity1.setProperty("values",valuesToKeep);
                    entitiesFixed.add(entity1);
                }
                datastore.put(entitiesFixed);

            }

        });
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
