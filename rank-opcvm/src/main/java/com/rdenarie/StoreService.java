package com.rdenarie;



import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.google.appengine.api.datastore.*;
import com.google.appengine.repackaged.com.google.gson.JsonObject;
import com.google.appengine.repackaged.com.google.gson.JsonPrimitive;

import java.util.*;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "StoreService", value = "/storeService")
public class StoreService extends HttpServlet {



    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String id=request.getParameter("id");
        if (id!=null) {
            store(id,false);
            //storeOld();
            response.getWriter().println("Finished");

        } else {
            storeAll();
            response.getWriter().println("Finished");
        }
    }

    private static void storeOld() {

        //mockup to create passed dates

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        List<Entity> entityList = new ArrayList<>();

        Calendar now = Calendar.getInstance();
        now.add(Calendar.MONTH,-1);
        String key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);

        Entity timeElement = new Entity(Utils.TIME_ELEMENT_ENTITY,key);
        timeElement.setProperty("date", now.getTime());
        entityList.add(timeElement);

        now.add(Calendar.MONTH,-1);
        key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);

        Entity timeElement2 = new Entity(Utils.TIME_ELEMENT_ENTITY,key);
        timeElement2.setProperty("date", now.getTime());
        entityList.add(timeElement2);

        now.add(Calendar.MONTH,-1);
        key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);

        Entity timeElement3 = new Entity(Utils.TIME_ELEMENT_ENTITY,key);
        timeElement3.setProperty("date", now.getTime());
        entityList.add(timeElement3);


        now.add(Calendar.MONTH,-1);
        key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);

        Entity timeElement4 = new Entity(Utils.TIME_ELEMENT_ENTITY,key);
        timeElement4.setProperty("date", now.getTime());
        entityList.add(timeElement4);

        now.add(Calendar.MONTH,-1);
        key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);

        Entity timeElement5 = new Entity(Utils.TIME_ELEMENT_ENTITY,key);
        timeElement5.setProperty("date", now.getTime());
        entityList.add(timeElement5);


        datastore.put(entityList);


    }

    private static void store(String id, boolean isBoursoId) {
        List<Entity> entityList = new ArrayList<>();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        entityList.addAll(getEntitiesListToStore(id, isBoursoId));
        datastore.put(entityList);
    }

    private static List<Entity> getEntitiesListToStore(String idAndCategory, boolean isBoursoId) {
        List<Entity> entityList = new ArrayList<>();

        String[] splitted = idAndCategory.split("#");
        String id=splitted[0];
        String categoryPersoName = splitted.length==2 ? splitted[1] : "TBD Claude Category";

        JsonObject jsonObject= ExtractValueService.getValue(id,isBoursoId,categoryPersoName);

        if (jsonObject!=null) {
            Calendar now = Calendar.getInstance();
            String key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);

            Entity timeElement = new Entity(Utils.TIME_ELEMENT_ENTITY,key);
            timeElement.setProperty("date", now.getTime());
            entityList.add(timeElement);

            Entity dataEntry = new Entity(Utils.DATA_ENTRY_ENTITY, id, timeElement.getKey());
            Entity dataFond = new Entity(Utils.VALUE_ENTITY,dataEntry.getKey());
            Entity dataCategory = new Entity(Utils.VALUE_ENTITY,dataEntry.getKey());
            Entity dataPercentile = new Entity(Utils.VALUE_ENTITY,dataEntry.getKey());

            entityList.add(dataEntry);
            entityList.add(dataFond);
            entityList.add(dataCategory);
            entityList.add(dataPercentile);




            for (Map.Entry entry : jsonObject.entrySet()) {
                if (!(entry.getValue() instanceof JsonObject)) {
                    JsonPrimitive value = (JsonPrimitive) entry.getValue();
                    if (value.isString()) {
                        dataEntry.setProperty((String) entry.getKey(), value.getAsString());
                    } else if (value.isNumber()) {
                        dataEntry.setProperty((String) entry.getKey(), value.getAsNumber());

                    } else if (value.isBoolean()) {
                        dataEntry.setProperty((String) entry.getKey(), value.getAsBoolean());
                    }
                } else {
                    for (Map.Entry subEntry : ((JsonObject) entry.getValue()).entrySet()) {
                        for (Map.Entry subSubEntry : ((JsonObject) subEntry.getValue()).entrySet()) {
                            if (subEntry.getKey().equals("fond")) {
                                dataFond.setProperty((String) subSubEntry.getKey(), subSubEntry.getValue().toString());
                            } else if (subEntry.getKey().equals("category")) {
                                dataCategory.setProperty((String) subSubEntry.getKey(), subSubEntry.getValue().toString());
                            } else if (subEntry.getKey().equals("percentile")) {
                                dataPercentile.setProperty((String) subSubEntry.getKey(), subSubEntry.getValue()
                                        .toString());
                            }

                        }
                    }

                }

            }
        }
        return entityList;
    }


    private static void storeAll() {

        Calendar startTime = Calendar.getInstance();

        List<String> fonds = CategoriesService.getIdFonds();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        List<Entity> entityList = new ArrayList<>();
        int current=1;
        for (String fond : fonds) {
            System.out.println("Store fond "+current+"/"+fonds.size()+"");
            entityList.addAll(getEntitiesListToStore(fond, true));
            current++;
        }

        Calendar endTime = Calendar.getInstance();

        Entity durationImportationElement = new Entity(Utils.DURATION_IMPORTATION_ELEMENT_ENTITY);
        durationImportationElement.setProperty("startTime",startTime.getTime());
        durationImportationElement.setProperty("endTime",endTime.getTime());
        Long duration = endTime.getTimeInMillis()-startTime.getTimeInMillis();
        durationImportationElement.setProperty("duration",duration);
        entityList.add(durationImportationElement);
        datastore.put(entityList);


    }


}
