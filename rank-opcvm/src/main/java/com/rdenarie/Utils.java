package com.rdenarie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 18/07/18.
 */
public class Utils {


    public static final String TIME_ELEMENT_ENTITY = "TimeElement";
    public static final String DATA_ENTRY_ENTITY = "DataEntry";
    public static final String VALUE_ENTITY = "Value";
    public static final String EXCEPTION_ENTITY = "Exception";

    public static final String NAME_PROPERTY = "name";
    public static final String SCORE_FOND_PROPERTY = "scoreFond";
    public static final String MSRATING_PROPERTY = "msrating";
    public static final String ENTREE_PROPERTY = "entree";
    public static final String SORTIE_PROPERTY = "sortie";
    public static final String PRICE_PROPERTY = "price";
    public static final String CURRENCY_PROPERTY = "currency";
    public static final String PRICE_EUR_PROPERTY = "priceEur";
    public static final String TICKET_IN_PROPERTY = "ticketIn";
    public static final String TICKET_RENEW_PROPERTY = "ticketRenew";
    public static final String ID_PROPERTY = "id";
    public static final String COURANT_PROPERTY = "courant";
    public static final String ACTIF_PROPERTY = "actifM";
    public static final String GERANT_PROPERTY = "gerant";


    public static final String CATEGORY_GEN_PROPERTY = "categGenerale";
    public static final String CATEGORY_MS_PROPERTY = "categMS";
    public static final String CATEGORY_AMF_PROPERTY = "categAMF";

    public static final String CATEGORY_PERSO_PROPERTY = "categPerso";
    public static final String TYPE_PROPERTY = "type";
    public static final String DURATION_IMPORTATION_ELEMENT_ENTITY = "DurationEntity";
    public static final String RANK_IN_CATEGORY_PROPERTY = "rankInCategory";
    public static final String NUMBER_FUNDS_IN_CATEGORY_PROPERTY = "numberFundsInCategory";
    public static final String SCORE_CATEGORY = "scoreCategory";
    public static final String CATEGORY_RANK_PROPERTY = "categoryRank";
    public static final String NUMBER_OF_CATEGORIES = "numberOfCategories";
    public static final String MISSING_VALUES = "missingValues";


    private static final Logger log = Logger.getLogger(Utils.class.getName());

    private static final long timerPause = 500;

    public static String getBoursoResponse(String urlString) {

        //wait timerPause ms to prevent to hit GAE quota on url fetch (22Mo/min)
        try {
            Thread.sleep(timerPause);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.fine("Call url "+urlString);
        Long startTime = Calendar.getInstance().getTimeInMillis();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.addRequestProperty("User-Agent", "Mozilla");
            conn.addRequestProperty("Referer", "google.com");
//            String myCookie = "B20_TRADING_ENABLED=1";
//            conn.setRequestProperty("Cookie", myCookie);


            int respCode = conn.getResponseCode(); // New items get NOT_FOUND on PUT
            boolean redirect = false;

            // normally, 3xx is redirect
            if (respCode != HttpURLConnection.HTTP_OK) {
                if (respCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || respCode == HttpURLConnection.HTTP_MOVED_PERM
                        || respCode == HttpURLConnection.HTTP_SEE_OTHER)
                    redirect = true;
            }
            log.fine("Response Code ... " + respCode);

            while (redirect) {

                // get redirect url from "location" header field
                String newUrl = conn.getHeaderField("Location");

                // get the cookie if need, for login
                String cookies = conn.getHeaderField("Set-Cookie");

                // open the new connnection again
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("Cookie", cookies);
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
                conn.addRequestProperty("User-Agent", "Mozilla");
                conn.addRequestProperty("Referer", "google.com");

                log.fine("Redirect to URL : " + newUrl);
                respCode = conn.getResponseCode(); // New items get NOT_FOUND on PUT
                // normally, 3xx is redirect
                if (respCode != HttpURLConnection.HTTP_OK) {
                    if (respCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || respCode == HttpURLConnection.HTTP_MOVED_PERM
                            || respCode == HttpURLConnection.HTTP_SEE_OTHER)
                        redirect = true;
                }


            }



            if (respCode == HttpURLConnection.HTTP_OK || respCode == HttpURLConnection.HTTP_NOT_FOUND) {
                StringBuffer response = new StringBuffer();
                String line;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                return null;
            }
        } catch (IOException IO) {
            log.info("Unable to get url content "+urlString+"," +IO.getMessage());
            IO.printStackTrace();
        } finally {
            Long endTime = Calendar.getInstance().getTimeInMillis();
            Long duration=endTime - startTime;
            log.finest("Get url "+urlString+" takes "+duration+" ms");
        }

        return null;
    }

    public static String getMSResponse(String urlString) {

        //wait timerPause ms to prevent to hit GAE quota on url fetch (22Mo/min)
        try {
            Thread.sleep(timerPause);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.finest("Call url "+urlString);
        Long startTime = Calendar.getInstance().getTimeInMillis();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
//            String myCookie = "B20_TRADING_ENABLED=1";
//            conn.setRequestProperty("Cookie", myCookie);


            int respCode = conn.getResponseCode(); // New items get NOT_FOUND on PUT
            if (respCode == HttpURLConnection.HTTP_OK || respCode == HttpURLConnection.HTTP_NOT_FOUND) {
                StringBuffer response = new StringBuffer();
                String line;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return response.toString();
            } else {
                return null;
            }
        } catch (IOException IO) {
            log.info("Unable to get url content "+urlString+"," +IO.getMessage());
            IO.printStackTrace();
        } finally {
            Long endTime = Calendar.getInstance().getTimeInMillis();
            Long duration=endTime - startTime;
            log.finest("Get url "+urlString+" takes "+duration+" ms");
        }

        return null;
    }

    public static void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
    }

    public static JsonObject deepCopy(JsonObject jsonObject) {
        JsonObject result = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                result.add(entry.getKey(), deepCopy(entry.getValue().getAsJsonObject()));
            } else if (entry.getValue().isJsonArray()) {
                result.add(entry.getKey(), deepCopy(entry.getValue().getAsJsonArray()));
            } else if (entry.getValue().isJsonPrimitive()) {
                result.add(entry.getKey(), new JsonPrimitive(entry.getValue().getAsString()));
            }

        }

        return result;
    }
    private static JsonArray deepCopy(JsonArray jsonArray) {
        JsonArray result = new JsonArray();

        for (JsonElement jsonElement : jsonArray) {
            if (jsonElement.isJsonObject()) {
                result.add(deepCopy(jsonElement.getAsJsonObject()));
            } else if (jsonElement.isJsonArray()) {
                result.add(deepCopy(jsonElement.getAsJsonArray()));
            } else if (jsonElement.isJsonPrimitive()) {
                result.add(new JsonPrimitive(jsonElement.getAsString()));
            }
        }

        return result;
    }
}
