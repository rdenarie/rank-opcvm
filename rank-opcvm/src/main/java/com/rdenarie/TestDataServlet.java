package com.rdenarie;


import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "TestDataServlet", value = "/testDataServlet")
public class TestDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(TestDataServlet.class.getName());
    private static final int LIMIT = 50;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {


        Utils.setTimeZone();

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity lastDate=GetDataServlet.getLastDate();
//        Filter categoryFilter = new FilterPredicate(Utils.CATEGORY_MS_PROPERTY, FilterOperator.EQUAL, "");
        Query distinctCategoryQuery = new Query(Utils.DATA_ENTRY_ENTITY)
                .addProjection(new PropertyProjection(Utils.CATEGORY_MS_PROPERTY, String.class))
                .addProjection(new PropertyProjection(Utils.SCORE_CATEGORY, Double.class))
                .setDistinct(true)
                .setAncestor(lastDate.getKey())
                .addSort(Utils.SCORE_CATEGORY, Query.SortDirection.DESCENDING);
        PreparedQuery preparedDistinctCategoryQuery = datastore.prepare(distinctCategoryQuery);
        List<Entity> categories=preparedDistinctCategoryQuery.asList(FetchOptions.Builder.withDefaults());

        JsonArray arrayValues = new JsonArray();
        int i=1;
        for (Entity data : categories) {
            JsonArray array = new JsonArray();
            array.add(i);
            array.add(data.getParent().getName());
            array.add((String)data.getProperty(Utils.CATEGORY_MS_PROPERTY));
            array.add((Double)data.getProperty(Utils.SCORE_CATEGORY));

            Filter categoryFilter= new FilterPredicate(Utils.CATEGORY_MS_PROPERTY,FilterOperator.EQUAL,
                    (String)data.getProperty(Utils.CATEGORY_MS_PROPERTY));
            Filter categoryScoreFilter= new FilterPredicate(Utils.SCORE_CATEGORY, FilterOperator.EQUAL,
                    (Double)data.getProperty(Utils.SCORE_CATEGORY));
            Query fund = new Query(Utils.DATA_ENTRY_ENTITY)
                    .setAncestor(lastDate.getKey())
                    .setFilter(categoryFilter)
                    .setFilter(categoryScoreFilter);
            PreparedQuery preparedQuery = datastore.prepare(fund);
            Entity fundEntity = preparedQuery.asList(FetchOptions.Builder.withLimit(1)).get(0);

            array.add((String)fundEntity.getProperty(Utils.ID_PROPERTY));

            arrayValues.add(array);
            i++;
        }

//        List<Key> keys= entities.stream().map(entity -> entity.getKey()).collect(Collectors.toList());
//        datastore.delete(keys);

        JsonObject result = new JsonObject();
        result.add("data", arrayValues);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());
    }

}
