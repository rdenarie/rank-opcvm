package com.rdenarie;


import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
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
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();
        Entity lastDate=GetDataServlet.getLastDate();
        Filter categoryFilter = new FilterPredicate(Utils.CATEGORY_MS_PROPERTY, FilterOperator.EQUAL, "");
//        Filter idFilter = new FilterPredicate(Utils.ID_PROPERTY, FilterOperator., "");

        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setFilter(categoryFilter);
        List<Entity> entities=datastore.prepare(q).asList(fetchOptions);

        JsonArray arrayValues = new JsonArray();
        for (Entity data : entities) {
            JsonArray array = new JsonArray();
            array.add(data.getParent().getName());
            array.add((String)data.getProperty(Utils.ID_PROPERTY));
            array.add((Double)data.getProperty(Utils.PRICE_EUR_PROPERTY));
            array.add((String)data.getProperty(Utils.CATEGORY_MS_PROPERTY));
            arrayValues.add(array);
        }

//        List<Key> keys= entities.stream().map(entity -> entity.getKey()).collect(Collectors.toList());
//        datastore.delete(keys);

        JsonObject result = new JsonObject();
//        result.add("data", arrayValues);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());
    }

}
