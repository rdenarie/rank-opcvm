package com.rdenarie;


import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "GetFundDataServlet", value = "/getFundDataServlet")
public class GetFundDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(GetFundDataServlet.class.getName());
    private static final int LIMIT = 50;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String fundId=request.getParameter("id");

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();


        Filter idFilter = new FilterPredicate(Utils.ID_PROPERTY, FilterOperator.EQUAL, fundId);

        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setFilter(idFilter);
        List<Entity> entities=datastore.prepare(q).asList(fetchOptions);

        JsonArray arrayValues = new JsonArray();
        for (Entity data : entities) {
            JsonArray array = new JsonArray();
            array.add(data.getParent().getName());
            array.add((Double)data.getProperty(Utils.PRICE_EUR_PROPERTY));
            arrayValues.add(array);
        }
        JsonObject result = new JsonObject();
        result.add("data", arrayValues);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());
    }

}
