package com.rdenarie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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
    public static final String DURATION_IMPORTATION_ELEMENT_ENTITY = "DurationEntity";


    public static String getBoursoResponse(String urlString) throws IOException {
        System.out.println("Call url "+urlString);
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        String myCookie = "B20_TRADING_ENABLED=1";
        conn.setRequestProperty("Cookie", myCookie);


        int status = conn.getResponseCode();
        int nbRedirect=0;
        while ((status==301 ||status==302)&& nbRedirect<5) {
            nbRedirect++;
            String newUrl = conn.getHeaderField("Location");
            // open the new connnection again
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
            conn.setRequestProperty("Cookie", myCookie);
            status = conn.getResponseCode();
        }

        InputStream inputStream = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuffer response = new StringBuffer();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();


        return response.toString();
    }
}
