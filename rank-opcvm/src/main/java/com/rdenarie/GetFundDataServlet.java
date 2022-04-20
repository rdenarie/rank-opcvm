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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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


        Utils.setTimeZone();

        String fundId=request.getParameter("id");

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();


        Filter idFilter = new FilterPredicate(Utils.ID_PROPERTY, FilterOperator.EQUAL, fundId);

        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setFilter(idFilter);
        List<Entity> entities=datastore.prepare(q).asList(fetchOptions);

        entities.sort((entity1, entity2) -> {
            String dateString1 = entity1.getParent().getName();
            String dateString2 = entity2.getParent().getName();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            try {
                Date date1 = sdf.parse(dateString1);
                Date date2 = sdf.parse(dateString2);

                return date1.compareTo(date2);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return 0;

        });

        JsonArray arrayValues = new JsonArray();
        for (Entity data : entities) {
            JsonArray array = new JsonArray();
            array.add(data.getParent().getName());
            array.add((Double)data.getProperty(Utils.PRICE_EUR_PROPERTY));
            array.add((String)data.getProperty(Utils.CATEGORY_MS_PROPERTY));
            array.add((Long)data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY));
            array.add((Long)data.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY));
            array.add((Double)data.getProperty(Utils.SCORE_CATEGORY));
            array.add((Double)data.getProperty(Utils.SCORE_FOND_PROPERTY));
            //array.add(data.getProperty("values").toString());
            //String missingValues = data.getProperty("missingValues") != null ? data.getProperty("missingValues").toString() :
            // "";
            //array.add(missingValues);
            arrayValues.add(array);
        }




        JsonObject result = new JsonObject();


        result.addProperty("isin",fundId);
        if (entities.size()>0) {
            result.addProperty("name", entities.get(0).getProperty(Utils.NAME_PROPERTY).toString());
        }

        result.add("data", arrayValues);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());
    }

}
