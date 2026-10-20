package com.rdenarie;

import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

/** Reads the latest version of a Secret Manager secret of this project, with the App Engine service account. */
public final class SecretManager {

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private SecretManager() {
  }

  public static String access(String secret) throws IOException {
    String project = System.getProperty("com.google.appengine.application.id", "rank-opcvm");
    URL url = new URL("https://secretmanager.googleapis.com/v1/projects/" + project + "/secrets/" + secret + "/versions/latest:access");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    String token = AppIdentityServiceFactory.getAppIdentityService().getAccessToken(Collections.singletonList(SCOPE)).getAccessToken();
    conn.setRequestProperty("Authorization", "Bearer " + token);
    int code = conn.getResponseCode();
    if (code != HttpURLConnection.HTTP_OK) {
      throw new IOException("Secret Manager answered HTTP " + code + " for secret " + secret);
    }
    try (InputStream in = conn.getInputStream()) {
      return decode(new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
    }
  }

  static String decode(JsonObject response) {
    String data = response.getAsJsonObject("payload").get("data").getAsString();
    return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8).trim();
  }
}
