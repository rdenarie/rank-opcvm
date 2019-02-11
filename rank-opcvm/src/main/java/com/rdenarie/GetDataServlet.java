package com.rdenarie;


import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.*;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;



import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "GetDataServlet", value = "/getDataServlet")
public class GetDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(GetDataServlet.class.getName());
    private static final int LIMIT = 50;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {


        String dateParameter=request.getParameter("date");
        Entity currentDate;
        if (dateParameter==null) {
            currentDate = getLastDate();
        } else {
            currentDate=getDateFromParameter(new Date(new Long(dateParameter)));
        }


        String category=request.getParameter("category");

        if (currentDate==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            List<Entity> datas;
            JsonObject jsonData = new JsonObject();
            addColumnNames(jsonData);
            JsonArray arrayValues = new JsonArray();

            String cursorString=request.getParameter("startCursorString");

            int limit=request.getParameter("displayAll") != null ? (request.getParameter("displayAll").equals("true") ? -1 : LIMIT) : LIMIT;

            QueryResult queryResult;
            if (category==null) {
                queryResult = getDataByDate(currentDate,cursorString,limit);
            } else {
                queryResult = getDataByDateAndCategory(currentDate,category,cursorString,limit);
            }
            datas=queryResult.results;
            for (Entity data : datas) {
                JsonObject json = createJsonObjectValueRow(data);
                if (json!=null) {
                    arrayValues.add(json);
                }
            }

            jsonData.add("rows", arrayValues);

            JsonObject result = new JsonObject();

            Date date = (Date) currentDate.getProperty("date");
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            result.addProperty("date", cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.YEAR));


            Entity previousDateEntity= getPreviousDate(currentDate);
            if (previousDateEntity!=null) {
                Date previousDate = (Date) previousDateEntity.getProperty("date");
                Calendar previousCal = Calendar.getInstance();
                previousCal.setTime(previousDate);
                result.addProperty("previousDate", previousCal.get(Calendar.DAY_OF_MONTH) + "/" + (previousCal.get(Calendar.MONTH) + 1) + "/" + previousCal.get(Calendar.YEAR));
                result.addProperty("previousTime", ((Date) previousDateEntity.getProperty("date")).getTime());

            }
            Entity nextDateEntity= getNextDate(currentDate);
            if (nextDateEntity!=null) {
                Date nextDate = (Date) nextDateEntity.getProperty("date");
                Calendar nextCal = Calendar.getInstance();
                nextCal.setTime(nextDate);
                result.addProperty("nextDate", nextCal.get(Calendar.DAY_OF_MONTH) + "/" + (nextCal.get(Calendar.MONTH) + 1) + "/" + nextCal.get(Calendar.YEAR));
                result.addProperty("nextTime", ((Date) nextDateEntity.getProperty("date")).getTime());

            }


            result.add("data", jsonData);
            result.addProperty("cursorString",queryResult.cursor);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(result.toString());
        }
    }

    private Entity getDateFromParameter(Date date) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Filter dateFilter = new FilterPredicate("date", FilterOperator.EQUAL,date);

        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).setFilter(dateFilter).addSort("date", Query.SortDirection.DESCENDING);

        PreparedQuery pq = datastore.prepare(q);

        List<Entity> entities = pq.asList(FetchOptions.Builder.withLimit(1));
//        List<Entity> entities = pq.asList(FetchOptions.Builder.withDefaults());
//        for (Entity current : entities) {
//            System.out.println(current.getProperty("date"));
//
//        }
//        return null;
        if (entities!=null && entities.size()>0) return entities.get(0);
        else return null;

    }

    private Entity getPreviousDate(Entity currentDate) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Filter dateFilter = new FilterPredicate("date", FilterOperator.LESS_THAN, currentDate.getProperty("date"));

        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).setFilter(dateFilter).addSort("date", Query.SortDirection.DESCENDING);

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> entities = pq.asList(FetchOptions.Builder.withLimit(1));
        if (entities!=null && entities.size()>0) return entities.get(0);
        else return null;

    }
    private Entity getNextDate(Entity currentDate) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Filter dateFilter = new FilterPredicate("date", FilterOperator.GREATER_THAN, currentDate.getProperty("date"));

        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).setFilter(dateFilter).addSort("date", SortDirection.ASCENDING);

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> entities = pq.asList(FetchOptions.Builder.withLimit(1));
        if (entities!=null && entities.size()>0) return entities.get(0);
        else return null;

    }

    private QueryResult getDataByDateAndCategory(Entity lastDate, String category, String startCursorString, int limit) {
        FilterPredicate filter = new Query.FilterPredicate(Utils.CATEGORY_PERSO_PROPERTY, Query.FilterOperator.EQUAL, category);

        log.fine("getDataByDateAndCategory : "+category);
        return doQuery(lastDate, filter, startCursorString, limit);
    }

    private QueryResult doQuery(Entity lastDate, FilterPredicate filter, String startCursorString, int limit) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();
        if (limit>-1) {
            fetchOptions = FetchOptions.Builder.withLimit(limit);
        }

        if (startCursorString != null && !startCursorString.equals("")) {
            fetchOptions.startCursor(Cursor.fromWebSafeString(startCursorString)); // Where we left off
        }

        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey()).addSort(Utils.SCORE_FOND_PROPERTY,SortDirection.DESCENDING);
        if (filter!=null) {
            q=q.setFilter(filter);
        }

        PreparedQuery preparedQuery = datastore.prepare(q);
        QueryResultIterator<Entity> resultsIterator = preparedQuery.asQueryResultIterator(fetchOptions);
        List<Entity> resultList = new ArrayList<>();
        while (resultsIterator.hasNext()) {
            resultList.add(resultsIterator.next());
        }
        Cursor cursor = resultsIterator.getCursor();
        String cursorString = null;
        if (cursor != null && limit>-1 && resultList.size() == LIMIT) {         // Are we paging? Save Cursor
            startCursorString = cursor.toWebSafeString();               // Cursors are WebSafe
        }

        return new QueryResult(resultList,startCursorString);
    }

    private JsonObject createJsonObjectValueRow(Entity data) {
/*
        {"c":[{v: 'a'},{"v":3,"f":null}]}
        */
        JsonArray values = new JsonArray();

        Map<String, Object> properties=data.getProperties();

        values.add(createJsonObjectValueString(properties.get(Utils.NAME_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.SCORE_FOND_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.MSRATING_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.ENTREE_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.SORTIE_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.PRICE_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.CURRENCY_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.PRICE_EUR_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.TICKET_IN_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.TICKET_RENEW_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.ID_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.COURANT_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.ACTIF_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.GERANT_PROPERTY).toString()));
        //values.add(createJsonObjectValueString(properties.get(Utils.CATEGORY_GEN_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.CATEGORY_MS_PROPERTY).toString()));
        //values.add(createJsonObjectValueString(properties.get(Utils.CATEGORY_PERSO_PROPERTY).toString()));


//        if (properties.get(Utils.RANK_IN_CATEGORY_PROPERTY)!=null){
//            values.add(createJsonObjectValueString(properties.get(Utils.RANK_IN_CATEGORY_PROPERTY).toString()));
//            values.add(createJsonObjectValueString(properties.get(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY).toString()));
//        } else {
//            computeRank(data);
//        }


//        if (!isTooExpensive(data.getProperty(Utils.TICKET_IN_PROPERTY).toString())) {
            JsonObject result = new JsonObject();
            result.add("c", values);
            return result;
//        } else {
//            return null;
//        }

    }

    private void computeRank(Entity data) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(data.getParent());
        FilterPredicate filter = new Query.FilterPredicate(Utils.CATEGORY_MS_PROPERTY, Query.FilterOperator.EQUAL, data.getProperty(Utils.CATEGORY_MS_PROPERTY));
        q.setFilter(filter);


    }

    private boolean isTooExpensive(String ticketIn) {
//        if (ticketIn.contains(" ")) {
//            String[] arrayIn = ticketIn.split(" ");
//            if (!arrayIn[0].contains(".")) {
//                try {
//                    int value = new Integer(arrayIn[0]);
//                    return value > 10000;
//                } catch (NumberFormatException NFE) {
//                    return false;
//                }
//            } else {
//                try {
//                    Double value = new Double(arrayIn[0]);
//                    return value > 10000;
//                } catch (NumberFormatException NFE) {
//                    return false;
//                }
//            }
//        }
        return false;

    }

    private JsonObject createJsonObjectValueString(String value) {
        JsonObject result = new JsonObject();
        result.addProperty("v",value);
        return result;
    }
    private JsonObject createJsonObjectValueAsFloat(String value) {

        JsonObject result = new JsonObject();
        result.addProperty("v",new Double(value));
        return result;
    }

    private void addColumnNames(JsonObject result) {
        JsonArray columns=new JsonArray();
        columns.add(createJsonObjectColumnName(Utils.NAME_PROPERTY,"Nom","","string"));
        columns.add(createJsonObjectColumnName(Utils.SCORE_FOND_PROPERTY,"Score","","number"));
        columns.add(createJsonObjectColumnName(Utils.MSRATING_PROPERTY,"Etoiles MS","","number"));
        columns.add(createJsonObjectColumnName(Utils.ENTREE_PROPERTY,"F-In","","string"));
        columns.add(createJsonObjectColumnName(Utils.SORTIE_PROPERTY,"F-Out","","string"));
        columns.add(createJsonObjectColumnName(Utils.PRICE_PROPERTY,"Cours","","number"));
        columns.add(createJsonObjectColumnName(Utils.CURRENCY_PROPERTY,"Devise","","string"));
        columns.add(createJsonObjectColumnName(Utils.PRICE_EUR_PROPERTY,"Cours (EUR)","","number"));
        columns.add(createJsonObjectColumnName(Utils.TICKET_IN_PROPERTY,"Ticket Initial","","string"));
        columns.add(createJsonObjectColumnName(Utils.TICKET_RENEW_PROPERTY,"Ticket Renew","","string"));
        columns.add(createJsonObjectColumnName(Utils.ID_PROPERTY,"Code ISIN","","string"));
        columns.add(createJsonObjectColumnName(Utils.COURANT_PROPERTY,"F-Courant","","string"));
        columns.add(createJsonObjectColumnName(Utils.ACTIF_PROPERTY,"Actifs (en m)","","number"));
        columns.add(createJsonObjectColumnName(Utils.GERANT_PROPERTY,"Gérant","","string"));
//        columns.add(createJsonObjectColumnName(Utils.CATEGORY_GEN_PROPERTY,"Catégorie Générale","","string"));
        columns.add(createJsonObjectColumnName(Utils.CATEGORY_MS_PROPERTY,"Catégorie MS","","string"));
//        columns.add(createJsonObjectColumnName(Utils.CATEGORY_PERSO_PROPERTY,"Catégorie Claude","","string"));
        result.add("cols",columns);
    }

    private JsonObject createJsonObjectColumnName(String id, String label, String pattern, String type) {
        JsonObject result = new JsonObject();
        result.addProperty("id",id);
        result.addProperty("label",label);
        result.addProperty("pattern",pattern);
        result.addProperty("type",type);

        return result;


    }

    public Entity getLastDate() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).addSort("date", Query.SortDirection.DESCENDING);

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> entities = pq.asList(FetchOptions.Builder.withLimit(1));
        if (entities!=null && entities.size()>0) return entities.get(0);
        else return null;

    }

    public List<Entity> getLastDates() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).addSort("date", Query.SortDirection.DESCENDING);

        PreparedQuery pq = datastore.prepare(q);
        return pq.asList(FetchOptions.Builder.withLimit(5));

    }

    public QueryResult getDataByDate(Entity date, String startCursorString,int limit) {
        return doQuery(date,null, startCursorString, limit);
    }


    public class QueryResult {
        String cursor;
        List<Entity> results;

        public QueryResult(List<Entity> results, String cursor) {
            this.cursor=cursor;
            this.results=results;
        }



    }
}
