package com.rdenarie;


import com.google.appengine.api.datastore.*;
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

        Entity lastDate= getLastDate();
        String category=request.getParameter("category");
        log.fine("Category in param "+category);
        if (lastDate==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            List<Entity> datas;
            JsonObject jsonData = new JsonObject();
            addColumnNames(jsonData);
            JsonArray arrayValues = new JsonArray();
            if (category==null) {
                datas = getDataByDate(lastDate);
            } else {
                datas = getDataByDateAndCategory(lastDate,category);
            }
            for (Entity data : datas) {
                JsonObject json = createJsonObjectValueRow(data);
                if (json!=null) {
                    arrayValues.add(json);
                }
            }

            jsonData.add("rows", arrayValues);

            JsonObject result = new JsonObject();

            Date date = (Date) lastDate.getProperty("date");
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            result.addProperty("date", cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.YEAR));

            result.add("data", jsonData);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(result.toString());
        }
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
        System.out.println(ticketIn);
        if (ticketIn.contains(" ")) {
            System.out.println("Contains space");
            String[] arrayIn = ticketIn.split(" ");
            if (!arrayIn[0].contains(".")) {
                try {
                    int value = new Integer(arrayIn[0]);
                    System.out.println("Value " + value + ", return " + (value > 10000));
                    return value > 10000;
                } catch (NumberFormatException NFE) {
                    return false;
                }
            } else {
                try {
                    Double value = new Double(arrayIn[0]);
                    System.out.println("Value " + value + ", return " + (value > 10000d));
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
