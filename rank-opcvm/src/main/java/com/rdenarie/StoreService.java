package com.rdenarie;



import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.google.appengine.api.datastore.*;
import com.google.appengine.api.utils.SystemProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.*;
import java.util.logging.Logger;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;



/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "StoreService", value = "/storeService")
public class StoreService extends HttpServlet {
    private static final Logger log = Logger.getLogger(StoreService.class.getName());
    private static final int MAX_CHUNCK = 100;

    public static Entity getTodayTimeElement() {

        try {
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            Calendar now = Calendar.getInstance();
            String keyString=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);
            Key key = KeyFactory.createKey(Utils.TIME_ELEMENT_ENTITY, keyString);

            return datastore.get(key);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Utils.setTimeZone();

        String id=request.getParameter("id");
        Boolean isBoursoId=request.getParameter("isBoursoId")!= null ? request.getParameter("isBoursoId").equals("true") : false;
        String categoryMsId = request.getParameter("categoryMsId");
        String categoryId = request.getParameter("categoryId");

        if (id!=null) {
            store(id, isBoursoId);
            //storeOld();
            response.getWriter().println("Finished");
        } else if (categoryMsId!=null && categoryId!=null) {
            storeByMsCategory(categoryMsId,categoryId);
        } else {
            storeAll();
            response.getWriter().println("Finished");
        }
    }
    private static void store(String id, boolean isBoursoId) {
        log.info("Store opcvm with id "+id+", isBoursoId:"+isBoursoId);
        List<Entity> entityList = new ArrayList<>();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        entityList.addAll(getEntitiesListToStore(id, isBoursoId));
        log.info("Found "+entityList.size()+" entities to store.");
        if (entityList.size()>0) {
            datastore.put(entityList);
        }
    }

    private static List<Entity> getEntitiesListToStore(String idAndCategory, boolean isBoursoId) {
        List<Entity> entityList = new ArrayList<>();

        String[] splitted = idAndCategory.split("#");
        String id=splitted[0];
        String categoryPersoName = splitted.length==2 ? splitted[1] : "TBD Claude Category";

        JsonObject jsonObject= ExtractValueService.getValue(id,isBoursoId,categoryPersoName);
        log.finer("Value extracted : "+jsonObject);

        if (jsonObject!=null) {
            Calendar now = Calendar.getInstance();
            String key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);


            Entity timeElement = getTodayTimeElement();
            if (timeElement==null) {
                timeElement=new Entity(Utils.TIME_ELEMENT_ENTITY, key);
                timeElement.setProperty("date", now.getTime());
                entityList.add(timeElement);
            }

            Entity dataEntry = new Entity(Utils.DATA_ENTRY_ENTITY, id, timeElement.getKey());
            //Entity dataFond = new Entity(Utils.VALUE_ENTITY,dataEntry.getKey());
            //Entity dataCategory = new Entity(Utils.VALUE_ENTITY,dataEntry.getKey());
            //Entity dataPercentile = new Entity(Utils.VALUE_ENTITY,dataEntry.getKey());

            entityList.add(dataEntry);
            //entityList.add(dataFond);
            //entityList.add(dataCategory);
            //entityList.add(dataPercentile);



            String dataString="";
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
                    dataEntry.setProperty((String)entry.getKey(), entry.getValue().toString());
//                    for (Map.Entry subEntry : ((JsonObject) entry.getValue()).entrySet()) {
//                        log.info(entry.getValue().toString());
//                        for (Map.Entry subSubEntry : ((JsonObject) subEntry.getValue()).entrySet()) {
//                            if (subEntry.getKey().equals("fond")) {
//                                dataFond.setProperty((String) subSubEntry.getKey(), subSubEntry.getValue().toString());
//                            } else if (subEntry.getKey().equals("category")) {
//                                dataCategory.setProperty((String) subSubEntry.getKey(), subSubEntry.getValue().toString());
//                            } else if (subEntry.getKey().equals("percentile")) {
//                                dataPercentile.setProperty((String) subSubEntry.getKey(), subSubEntry.getValue()
//                                        .toString());
//                            }
//
//                        }
//                    }

                }

            }
        }
        return entityList;
    }


    private static void storeAll() {
        log.info("Store all opcvm");
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity timeElement = getTodayTimeElement();
        if (timeElement==null) {
            Calendar now = Calendar.getInstance();
            String key=now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);
            timeElement=new Entity(Utils.TIME_ELEMENT_ENTITY, key);
            timeElement.setProperty("date", now.getTime());
            datastore.put(timeElement);
        }

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


                queue.addAsync(TaskOptions.Builder.withUrl("/storeService").method(TaskOptions.Method.GET).param("categoryMsId", categoryMsId).param("categoryId", categoryId));
                log.info("Queue msCategory "+categoryMsId+" "+currentMs+"/"+msCategories.size()+"");
                currentMs++;
            }

            log.info("Queue personnal category "+categoryId+" "+current+"/"+personalCategories.size()+"");
            current++;
        }

    }

    private static void storeByMsCategory(String categoryMsId, String categoryId) {
        log.info("Store categoryMsId : "+categoryMsId+", categoryId : "+categoryId);


        JsonObject categories = CategoriesService.getCategories();
        JsonArray personalCategories = categories.getAsJsonArray("personalCategories");
        for (JsonElement personalCategory : personalCategories) {
            if (personalCategory.getAsJsonObject().get("categoryName").getAsString().equals(categoryId)) {
                JsonArray msCategories = personalCategory.getAsJsonObject().get("categoriesMs").getAsJsonArray();
                log.info("msCategories : "+msCategories);
                for (JsonElement msCategory : msCategories) {
                    log.info("msCategory : "+msCategory);
                    log.info("personalCategory : "+personalCategory);
                    if (msCategory.getAsJsonObject().get("categoryName").getAsString().equals(categoryMsId)) {
                        List<String> fonds = CategoriesService.getIdFondsByMsCategory(msCategory, personalCategory);
                        int current = 1;
                        for (String fond : fonds) {
                            log.info("Queue fond " + current + "/" + fonds.size() + " for categoryMsId="+categoryMsId+", categoryId="+categoryId);
                            Queue queue;
                            if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Production) {
                                queue = QueueFactory.getQueue("slow-queue");
                            } else {
                                queue = QueueFactory.getDefaultQueue();

                            }
                            queue.addAsync(TaskOptions.Builder.withUrl("/storeService").method(TaskOptions.Method.GET).param("id", fond).param("isBoursoId", "true"));
                            current++;
                        }
                        return;
                    }

                }
            }
       }

    }

    private static void checkAndStoreIfNeeded(List<Entity> entityList, DatastoreService datastore) {
        if (entityList.size()>=MAX_CHUNCK) {
            log.info("Entity list size : "+entityList.size()+", need to store");
            datastore.put(entityList);
            entityList.clear();
        }

    }


}
