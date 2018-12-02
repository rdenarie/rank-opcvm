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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "GetDataServlet", value = "/getDataServlet")
public class GetDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(GetDataServlet.class.getName());

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
            if (category==null) {
                datas = getDataByDate(currentDate);
            } else {
                datas = getDataByDateAndCategory(currentDate,category);
            }
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

    private List<Entity> getDataByDateAndCategory(Entity lastDate, String category) {
        log.fine("getDataByDateAndCategory : "+category);
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(lastDate.getKey()).setFilter(new Query.FilterPredicate(Utils.CATEGORY_PERSO_PROPERTY, Query.FilterOperator.EQUAL, category));
        return datastore.prepare(q).asList(FetchOptions.Builder.withChunkSize(10));
    }

    private JsonObject createJsonObjectValueRow(Entity data) {
/*
        {"c":[{v: 'a'},{"v":3,"f":null}]}
        */
        JsonArray values = new JsonArray();
        values.add(createJsonObjectValueString(data.getProperty(Utils.NAME_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(data.getProperty(Utils.SCORE_FOND_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.MSRATING_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.ENTREE_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.SORTIE_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(data.getProperty(Utils.PRICE_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.CURRENCY_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(data.getProperty(Utils.PRICE_EUR_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.TICKET_IN_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.TICKET_RENEW_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.ID_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.COURANT_PROPERTY).toString()));
        values.add(createJsonObjectValueAsFloat(data.getProperty(Utils.ACTIF_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.GERANT_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.CATEGORY_GEN_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.CATEGORY_MS_PROPERTY).toString()));
        values.add(createJsonObjectValueString(data.getProperty(Utils.CATEGORY_PERSO_PROPERTY).toString()));

        if (!isTooExpensive(data.getProperty(Utils.TICKET_IN_PROPERTY).toString())) {
            JsonObject result = new JsonObject();
            result.add("c", values);
            return result;
        } else {
            return null;
        }

    }

    private boolean isTooExpensive(String ticketIn) {
        if (ticketIn.contains(" ")) {
            String[] arrayIn = ticketIn.split(" ");
            if (!arrayIn[0].contains(".")) {
                try {
                    int value = new Integer(arrayIn[0]);
                    return value > 10000;
                } catch (NumberFormatException NFE) {
                    return false;
                }
            } else {
                try {
                    Double value = new Double(arrayIn[0]);
                    return value > 10000;
                } catch (NumberFormatException NFE) {
                    return false;
                }
            }
        }
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
        columns.add(createJsonObjectColumnName(Utils.CATEGORY_GEN_PROPERTY,"Catégorie Générale","","string"));
        columns.add(createJsonObjectColumnName(Utils.CATEGORY_MS_PROPERTY,"Catégorie MS","","string"));
        columns.add(createJsonObjectColumnName(Utils.CATEGORY_PERSO_PROPERTY,"Catégorie Claude","","string"));
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

    public List<Entity> getDataByDate(Entity date) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(date.getKey());
        return datastore.prepare(q).asList(FetchOptions.Builder.withChunkSize(10));

    }
}
