package com.rdenarie;


import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "GetDataServlet", value = "/getDataServlet")
public class GetDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(GetDataServlet.class.getName());
    private static final int LIMIT = 200;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Utils.setTimeZone();

        String dateParameter=request.getParameter("date");
        Entity currentDate;
        if (dateParameter==null) {
            currentDate = getLastDate();
        } else {
            currentDate=getDateFromParameter(new Date(Long.parseLong(dateParameter)));
        }

        boolean groupDataByCategory = false;
        if(request.getParameter("groupByCategory") !=null) {
            groupDataByCategory=Boolean.parseBoolean(request.getParameter("groupByCategory"));
        }

        String category=request.getParameter("category");

        if (currentDate==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            JsonObject jsonData = new JsonObject();
            addColumnNames(jsonData);
            JsonArray arrayValues = new JsonArray();

            String cursorString=request.getParameter("startCursorString");
            int limit=request.getParameter("displayAll") != null ? (request.getParameter("displayAll").equals("true") ? -1 : LIMIT) : LIMIT;

            List<Entity> datas = getDatasEntity(cursorString,limit, category, currentDate, groupDataByCategory);

            JsonObject json;
            for (Entity data : datas) {
                json = createJsonObjectValueRow(data);
                if (json!=null) {
                    arrayValues.add(json);
                }
            }

            log.fine("Length : "+arrayValues.size());
            jsonData.add("rows", arrayValues);

            JsonObject result = new JsonObject();

            Date date = (Date) currentDate.getProperty("date");
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            result.addProperty("date", cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.YEAR));
            result.addProperty("dateTime",
                    date.getTime());


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
            result.addProperty("cursorString",cursorString);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(result.toString());
        }
    }

    public static List<Entity> getDatasEntity(String cursorString, int limit, String category, Entity currentDate, boolean groupByCategory) {

        log.fine(category);
        List<Entity> datas;

        QueryResult queryResult;

        log.fine("Category is "+category);

        boolean filterTop=true;
        if (category==null) {
            queryResult = getDataByDate(currentDate, cursorString, limit,groupByCategory);
        } else if (category.equals("Partenaires")) {
            queryResult = getDataPartenairesByDate(currentDate,cursorString,limit,groupByCategory);
            filterTop=false;
        } else {
            queryResult = getDataByDateAndCategory(currentDate,category,cursorString,limit,groupByCategory);
        }
        datas=queryResult.results;

        if (filterTop) {
            //filter elements which are not in top position :
            //we keep 20% of better funds
            //or 100 better in less than 500 in category
            datas = datas.parallelStream()
                         .filter(entity -> {
                             if (entity.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY) == null
                                 || entity.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY) == null) {
                                 log.info("Problem with fund " + entity);
                                 return false;
                             }
                             return ((Long) entity.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY) <= 500
                                 && (Long) entity.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY) <= 100) ||
                                 ((Long) entity.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY) <= (
                                     (Long) entity.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY) * 0.2));

                         })
                         .collect(Collectors.toList());
        }
        if (groupByCategory) {
            Comparator comparator = Comparator.comparingDouble(e -> Double.parseDouble(((Entity)e).getProperty(Utils.SCORE_CATEGORY).toString()))
                                                      .thenComparingDouble(e -> Double.parseDouble(((Entity)e).getProperty(Utils.SCORE_FOND_PROPERTY).toString()));
            Collections.sort(datas, comparator);
            Collections.reverse(datas);
        } else {
            Collections.sort(datas, Comparator.comparingDouble(p -> (double)p.getProperty(Utils.SCORE_FOND_PROPERTY)));
            Collections.reverse(datas);
        }
        cursorString = queryResult.cursor;
        return datas;
    }

    public static Entity getDateFromParameter(Date date) {
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
        if (entities!=null && !entities.isEmpty()) return entities.get(0);
        else return null;

    }

    public static Entity getPreviousDate(Entity currentDate) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Filter dateFilter = new FilterPredicate("date", FilterOperator.LESS_THAN, currentDate.getProperty("date"));

        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).setFilter(dateFilter).addSort("date", Query.SortDirection.DESCENDING);

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> entities = pq.asList(FetchOptions.Builder.withLimit(1));
        if (entities!=null && !entities.isEmpty()) return entities.get(0);
        else return null;

    }
    public static Entity getNextDate(Entity currentDate) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Filter dateFilter = new FilterPredicate("date", FilterOperator.GREATER_THAN, currentDate.getProperty("date"));

        Query q = new Query(Utils.TIME_ELEMENT_ENTITY).setFilter(dateFilter).addSort("date", SortDirection.ASCENDING);

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> entities = pq.asList(FetchOptions.Builder.withLimit(1));
        if (entities!=null && !entities.isEmpty()) return entities.get(0);
        else return null;

    }

    private static QueryResult getDataByDateAndCategory(Entity lastDate, String category, String startCursorString, int limit, boolean groupByCategory) {
        FilterPredicate filter = new Query.FilterPredicate(Utils.CATEGORY_PERSO_PROPERTY, Query.FilterOperator.EQUAL, category);

        log.fine("getDataByDateAndCategory : "+category);
        return doQuery(lastDate, filter, startCursorString, limit, groupByCategory);
    }

    private static QueryResult getDataPartenairesByDate(Entity lastDate, String startCursorString, int limit, boolean groupByCategory) {
        FilterPredicate filterPartenaire = new Query.FilterPredicate(Utils.TYPE_PROPERTY, Query.FilterOperator.EQUAL,
                                                                    "partenaire");
        FilterPredicate filterBoursoMarkets = new Query.FilterPredicate(Utils.TYPE_PROPERTY, Query.FilterOperator.EQUAL,
                                                                    "boursomarkets");

        return doQuery(lastDate, Query.CompositeFilterOperator.or(filterPartenaire,filterBoursoMarkets), startCursorString, limit,groupByCategory);
    }

    private static QueryResult doQuery(Entity lastDate, Filter filter, String startCursorString, int limit, boolean groupByCategory) {


        //FilterPredicate rankPropertyFilter = new FilterPredicate(Utils.RANK_IN_CATEGORY_PROPERTY, FilterOperator.LESS_THAN_OR_EQUAL, 100);
//        if (filter==null) {
//            filter=rankPropertyFilter;n
//        } else {
//            // Use CompositeFilter to combine multiple filters
//            CompositeFilter andFilter =
//                    CompositeFilterOperator.and(filter, rankPropertyFilter);
//            filter = andFilter;
//        }

//        log.fine("DoQuery : " + filter.toString());


        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();
        if (limit>-1) {
            fetchOptions = FetchOptions.Builder.withLimit(limit);
        }

        if (startCursorString != null && !startCursorString.equals("")) {
            fetchOptions.startCursor(Cursor.fromWebSafeString(startCursorString)); // Where we left off
        }

        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey())
//                .addSort(Utils.RANK_IN_CATEGORY_PROPERTY)
                .addSort(Utils.SCORE_FOND_PROPERTY,SortDirection.DESCENDING);
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
        if (cursor != null && limit>-1 && resultList.size() == LIMIT) {         // Are we paging? Save Cursor
            startCursorString = cursor.toWebSafeString();               // Cursors are WebSafe
        }

        return new QueryResult(resultList,startCursorString);
    }

    public static JsonObject createJsonObjectValueRow(Entity data) {
/*
        {"c":[{v: 'a'},{"v":3,"f":null}]}
        */
        if (data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY)==null){
            computeRank(data);
        }


        JsonArray values = new JsonArray();

        Map<String, Object> properties=data.getProperties();

        values.add(createJsonObjectValueString(properties.get(Utils.RANK_IN_CATEGORY_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY).toString()));

        values.add(createJsonObjectValueString(properties.get(Utils.NAME_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.SCORE_FOND_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.MSRATING_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.ENTREE_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.SORTIE_PROPERTY).toString()));
//        values.add(createJsonObjectValueAsFloat(properties.get(Utils.PRICE_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.PRICE_EUR_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.CURRENCY_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.TICKET_IN_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.TICKET_RENEW_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.ID_PROPERTY).toString()));
//        values.add(createJsonObjectValueString("<a href='/detailFund.jsp?id="+properties.get(Utils.ID_PROPERTY).toString()+"'>"+properties.get(Utils.ID_PROPERTY).toString()+"</a>"));
        values.add(createJsonObjectValueString(properties.get(Utils.COURANT_PROPERTY).toString()));
//        values.add(createJsonObjectValueAsFloat(properties.get(Utils.ACTIF_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.GERANT_PROPERTY).toString()));
        //values.add(createJsonObjectValueString(properties.get(Utils.CATEGORY_GEN_PROPERTY).toString()));
        values.add(createJsonObjectValueString(properties.get(Utils.CATEGORY_MS_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.SCORE_CATEGORY).toString()));
        values.add(createJsonObjectValueAsFloat(properties.get(Utils.CATEGORY_RANK_PROPERTY) != null ?
                properties.get(Utils.CATEGORY_RANK_PROPERTY).toString() : "N/A"));
        values.add(createJsonObjectValueString(properties.get(Utils.NUMBER_OF_CATEGORIES) != null ?
                properties.get(Utils.NUMBER_OF_CATEGORIES).toString() :  "N/A"));
        //values.add(createJsonObjectValueString(properties.get(Utils.CATEGORY_PERSO_PROPERTY).toString()));

        values.add(createJsonObjectValueString(properties.get(Utils.MISSING_VALUES) != null && properties.get(Utils.MISSING_VALUES).toString().equals("true") ?
                properties.get(Utils.MISSING_VALUES).toString() : ""));



//        if (!isTooExpensive(data.getProperty(Utils.TICKET_IN_PROPERTY).toString())) {
            JsonObject result = new JsonObject();
            result.add("c", values);
            return result;
//        } else {
//            return null;
//        }

    }

    public static void computeRank(Entity data) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();


        //1) récupération de tous les fond de la meme catégorie a la meme date
        FilterPredicate filterCategory = new Query.FilterPredicate(Utils.CATEGORY_MS_PROPERTY, Query.FilterOperator.EQUAL, data.getProperty(Utils.CATEGORY_MS_PROPERTY));
        Query q = new Query(Utils.DATA_ENTRY_ENTITY)
                .setFilter(filterCategory)
                .setAncestor(data.getParent())
                .addSort(Utils.SCORE_FOND_PROPERTY,SortDirection.DESCENDING);

        //q.addProjection(new PropertyProjection(Utils.SCORE_FOND_PROPERTY, String.class));
        //q.addProjection(new PropertyProjection(Utils.CATEGORY_MS_PROPERTY, String.class));
        q.addProjection(new PropertyProjection(Utils.ID_PROPERTY, String.class));

        PreparedQuery preparedQuery = datastore.prepare(q);
        List<Entity> entityOfSameCategory=preparedQuery.asList(FetchOptions.Builder.withDefaults());


        FilterPredicate filterBetterScore = new Query.FilterPredicate(Utils.SCORE_FOND_PROPERTY, FilterOperator.GREATER_THAN, data.getProperty(Utils.SCORE_FOND_PROPERTY));
        CompositeFilter fondOfSameCategoryWithBetterScore =
                CompositeFilterOperator.and(filterBetterScore, filterCategory);

        Query qBetterScore = new Query(Utils.DATA_ENTRY_ENTITY)
                .setFilter(fondOfSameCategoryWithBetterScore)
                .setAncestor(data.getParent())
                .addSort(Utils.SCORE_FOND_PROPERTY,SortDirection.DESCENDING);
        PreparedQuery preparedQueryBetterThan = datastore.prepare(qBetterScore);
        List<Entity> entityOfSameCategoryWithBetterScore=preparedQueryBetterThan.asList(FetchOptions.Builder.withDefaults());

        int position=entityOfSameCategoryWithBetterScore.size()+1;
        int nbElementsInCategory=entityOfSameCategory.size();

        data.setProperty(Utils.RANK_IN_CATEGORY_PROPERTY,position);
        data.setProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY,nbElementsInCategory);

        //recuperation du nombre de catégories différentes avec pour chacune  leur score

        Query categoryQuery = new Query(Utils.DATA_ENTRY_ENTITY)
                .addProjection(new PropertyProjection(Utils.CATEGORY_MS_PROPERTY, String.class))
                .addProjection(new PropertyProjection(Utils.SCORE_CATEGORY, Double.class))
                .setDistinct(true)
                .setAncestor(data.getParent())
                .addSort(Utils.SCORE_CATEGORY,SortDirection.DESCENDING);
        PreparedQuery preparedCategoryQuery = datastore.prepare(categoryQuery);
        List<Entity> categories=preparedCategoryQuery.asList(FetchOptions.Builder.withDefaults());

        AtomicInteger i = new AtomicInteger(); // any mutable integer wrapper
        int index = categories.stream()
                .peek(v -> i.incrementAndGet())
                .anyMatch(cateory -> cateory.getProperty(Utils.CATEGORY_MS_PROPERTY).equals(data.getProperty(Utils.CATEGORY_MS_PROPERTY))) ? // your
                // predicate
                i.get() - 1 : -1;
        log.fine("Data Category : "+data.getProperty(Utils.CATEGORY_MS_PROPERTY)+", "+categories+", result="+index);

        data.setProperty(Utils.CATEGORY_RANK_PROPERTY,index+1);
        data.setProperty(Utils.NUMBER_OF_CATEGORIES,categories.size());
        datastore.put(data);
        log.info("Fond "+data.getProperty(Utils.ID_PROPERTY)+" : "+data.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY)+"/"+data.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY));




    }


    public static JsonObject createJsonObjectValueString(String value) {
        JsonObject result = new JsonObject();
        if (value==null) {
            value="";
        }
        result.addProperty("v",value);
        return result;
    }
    public static JsonObject createJsonObjectValueAsFloat(String value) {
        JsonObject result = new JsonObject();
        result.addProperty("v",Double.parseDouble(value));
        return result;
    }

    private static void addColumnNames(JsonObject result) {
        JsonArray columns=new JsonArray();
        columns.add(createJsonObjectColumnName(Utils.RANK_IN_CATEGORY_PROPERTY,"Position","","string"));
        columns.add(createJsonObjectColumnName(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY,"Sur","","string"));
        columns.add(createJsonObjectColumnName(Utils.NAME_PROPERTY,"Nom","","string"));
        columns.add(createJsonObjectColumnName(Utils.SCORE_FOND_PROPERTY,"Score","","number"));
        columns.add(createJsonObjectColumnName(Utils.MSRATING_PROPERTY,"Etoiles MS","","number"));
        columns.add(createJsonObjectColumnName(Utils.ENTREE_PROPERTY,"F-In","","string"));
        columns.add(createJsonObjectColumnName(Utils.SORTIE_PROPERTY,"F-Out","","string"));
//        columns.add(createJsonObjectColumnName(Utils.PRICE_PROPERTY,"Cours","","number"));
        columns.add(createJsonObjectColumnName(Utils.PRICE_EUR_PROPERTY,"Cours (EUR)","","number"));
        columns.add(createJsonObjectColumnName(Utils.CURRENCY_PROPERTY,"Devise","","string"));
        columns.add(createJsonObjectColumnName(Utils.TICKET_IN_PROPERTY,"Ticket Initial","","string"));
        columns.add(createJsonObjectColumnName(Utils.TICKET_RENEW_PROPERTY,"Ticket Renew","","string"));
        columns.add(createJsonObjectColumnName(Utils.ID_PROPERTY,"Code ISIN","","string"));
        columns.add(createJsonObjectColumnName(Utils.COURANT_PROPERTY,"F-Courant","","string"));
//        columns.add(createJsonObjectColumnName(Utils.ACTIF_PROPERTY,"Actifs (en m)","","number"));
        columns.add(createJsonObjectColumnName(Utils.GERANT_PROPERTY,"Gérant","","string"));
//        columns.add(createJsonObjectColumnName(Utils.CATEGORY_GEN_PROPERTY,"Catégorie Générale","","string"));
        columns.add(createJsonObjectColumnName(Utils.CATEGORY_MS_PROPERTY,"Catégorie MS","","string"));
        columns.add(createJsonObjectColumnName(Utils.SCORE_CATEGORY,"Score Catégorie","","number"));
        columns.add(createJsonObjectColumnName(Utils.CATEGORY_RANK_PROPERTY,"Rang catégorie","","number"));
        columns.add(createJsonObjectColumnName(Utils.NUMBER_OF_CATEGORIES,"Nombre Catégories","","number"));
//        columns.add(createJsonObjectColumnName(Utils.CATEGORY_PERSO_PROPERTY,"Catégorie Claude","","string"));
        columns.add(createJsonObjectColumnName(Utils.MISSING_VALUES,"Valeurs manquantes","","string"));
        result.add("cols",columns);
    }

    private static JsonObject createJsonObjectColumnName(String id, String label, String pattern, String type) {
        JsonObject result = new JsonObject();
        result.addProperty("id",id);
        result.addProperty("label",label);
        result.addProperty("pattern",pattern);
        result.addProperty("type",type);

        return result;


    }

    public static Entity getLastDate() {
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

    public static QueryResult getDataByDate(Entity date, String startCursorString,int limit, boolean groupByCategory) {
        return doQuery(date,null, startCursorString, limit, groupByCategory);
    }


    public static class QueryResult {
        String cursor;
        List<Entity> results;

        public QueryResult(List<Entity> results, String cursor) {
            this.cursor=cursor;
            this.results=results;
        }



    }
}
