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
import java.util.logging.Level;
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

    /** Funds checked before an import: long-lived, cheap, and carrying every field the display needs. */
    static final String[] CANARY_FUNDS = {"FR0010135103", "LU0048578792", "FR0000292278"};
    private static final String[] CANARY_REQUIRED_FIELDS = {Utils.ID_PROPERTY, Utils.NAME_PROPERTY, Utils.SCORE_FOND_PROPERTY,
            Utils.SCORE_CATEGORY, Utils.CATEGORY_MS_PROPERTY, Utils.PRICE_EUR_PROPERTY, Utils.CURRENCY_PROPERTY,
            Utils.ENTREE_PROPERTY, Utils.SORTIE_PROPERTY, Utils.COURANT_PROPERTY, Utils.TICKET_IN_PROPERTY};
    /** The import is cancelled when this many canary funds fail: the scraping is broken, the import would be empty. */
    private static final int CANARY_FAILURES_TO_CANCEL = 2;
    private static final int MAX_DETAIL_LENGTH = 500;

    public static String getTodayKeyName() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR)+"-"+(now.get(Calendar.MONTH)+1)+"-"+now.get(Calendar.DAY_OF_MONTH);
    }

    public static Entity getTodayTimeElement() {

        try {
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            Key key = KeyFactory.createKey(Utils.TIME_ELEMENT_ENTITY, getTodayKeyName());

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
            response.getWriter().println(storeAll());
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

        ExtractValueService.Extraction extraction = ExtractValueService.extract(id,isBoursoId,categoryPersoName);
        JsonObject jsonObject= extraction.value;
        log.finer("Value extracted : "+jsonObject);

        if (jsonObject==null) {
            entityList.add(createRejection(id, extraction));
        } else {
            Entity timeElement = getTodayTimeElement();
            if (timeElement==null) {
                timeElement=createTimeElement();
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

                log.info(entry.getKey().toString());

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


    /** A new import date stays unpublished until the rank computation finds funds in it. */
    private static Entity createTimeElement() {
        Entity timeElement = new Entity(Utils.TIME_ELEMENT_ENTITY, getTodayKeyName());
        timeElement.setProperty("date", Calendar.getInstance().getTime());
        timeElement.setProperty(Utils.COMPLETE_PROPERTY, false);
        return timeElement;
    }

    private static Entity createRejection(String id, ExtractValueService.Extraction extraction) {
        String detail = extraction.detail == null ? "" : extraction.detail;
        if (detail.length() > MAX_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_DETAIL_LENGTH);
        }
        Level level = extraction.rejection.anomaly ? Level.WARNING : Level.FINE;
        log.log(level, "Fund " + id + " rejected: " + extraction.rejection + ", " + detail);

        Entity rejection = new Entity(Utils.IMPORT_REJECTION_ENTITY);
        rejection.setProperty(Utils.IMPORT_KEY_PROPERTY, getTodayKeyName());
        rejection.setUnindexedProperty("reason", extraction.rejection.name());
        rejection.setUnindexedProperty("fundId", id);
        rejection.setUnindexedProperty("detail", detail);
        return rejection;
    }

    /**
     * Extracts the canary funds and returns one line per failure: a rejection, or a field the display needs that
     * the page no longer yields. An empty list means the scraping still works.
     */
    static List<String> checkCanaryFunds() {
        List<String> failures = new ArrayList<>();
        for (String isin : CANARY_FUNDS) {
            ExtractValueService.Extraction extraction = ExtractValueService.extract(isin, false, "canary");
            if (extraction.value == null) {
                failures.add(isin + " : " + extraction.rejection + " (" + extraction.detail + ")");
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String field : CANARY_REQUIRED_FIELDS) {
                JsonElement value = extraction.value.get(field);
                if (value == null || value.getAsString().isEmpty() || value.getAsString().equals("ND")) {
                    missing.add(field);
                }
            }
            if (!missing.isEmpty()) {
                failures.add(isin + " : champs non lus " + missing);
            }
        }
        return failures;
    }

    private static void deletePreviousRejections() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        List<Key> keys = new ArrayList<>();
        for (Entity rejection : datastore.prepare(new Query(Utils.IMPORT_REJECTION_ENTITY).setKeysOnly())
                .asIterable(FetchOptions.Builder.withChunkSize(500))) {
            keys.add(rejection.getKey());
            if (keys.size() == 500) {
                datastore.delete(keys);
                keys.clear();
            }
        }
        datastore.delete(keys);
    }

    private static String storeAll() {
        log.info("Check canary funds before importing");
        List<String> canaryFailures = checkCanaryFunds();
        if (canaryFailures.size() >= CANARY_FAILURES_TO_CANCEL) {
            String body = "L'import est annulé : les fonds témoins ne sont plus lus correctement sur Boursorama.\n\n"
                    + String.join("\n", canaryFailures)
                    + "\n\nLe format des pages a probablement changé. Une fois le parsing corrigé, relancer /storeService.";
            log.severe(body);
            MailService.sendAlert("Rank-opcvm : import annulé", body);
            return "Import cancelled\n" + body;
        }
        if (!canaryFailures.isEmpty()) {
            String body = "L'import continue, mais un fonds témoin est en échec :\n\n" + String.join("\n", canaryFailures);
            log.warning(body);
            MailService.sendAlert("Rank-opcvm : fonds témoin en échec", body);
        }

        log.info("Store all opcvm");
        deletePreviousRejections();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity timeElement = getTodayTimeElement();
        if (timeElement==null) {
            timeElement=createTimeElement();
            datastore.put(timeElement);
        }

        JsonObject categories = CategoriesService.getCategories();
        JsonArray personalCategories = categories.getAsJsonArray("personalCategories");
        int current=1;

        for (JsonElement personalCategory : personalCategories) {
            JsonArray msCategories = personalCategory.getAsJsonObject().get("categoriesMs").getAsJsonArray();
            String categoryId=personalCategory.getAsJsonObject().get("categoryName").getAsString();

            //if (!categoryId.equals("Asie et Pacifique")) continue;
            int currentMs=1;
            for (JsonElement msCategory : msCategories) {
                String categoryMsId=msCategory.getAsJsonObject().get("categoryName").getAsString();
                //if (!(categoryMsId.equals("Actions Pacifique hors Japon") || categoryMsId.equals("Actions Marchés Emergents "
                //                                                                                      + "Petites & "  + "Moy. "
                //    + "Cap."))) continue;
                //log.info("categoryMsId="+categoryMsId);

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
        return "Finished";
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
                            log.info("Queue fond "+fond+" "+ current + "/" + fonds.size() + " for categoryMsId="+categoryMsId+", categoryId="+categoryId);
                            Queue queue;
                            if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Production) {
                                queue = QueueFactory.getQueue("slow-queue");
                            } else {
                                queue = QueueFactory.getDefaultQueue();

                            }
                            queue.addAsync(TaskOptions.Builder.withUrl("/storeService").method(TaskOptions.Method.GET).param("id", fond).param("isBoursoId", "false"));
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
