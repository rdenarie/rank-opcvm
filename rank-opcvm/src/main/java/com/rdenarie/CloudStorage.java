package com.rdenarie;

import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes the published JSON files in the Cloud Storage bucket named by the system property
 * {@code rank.bucket} (appengine-web.xml), through the JSON API and the App Engine service account. Files are stored
 * gzip-compressed with Content-Encoding gzip: browsers decompress them transparently.
 */
public final class CloudStorage {

  private static final Logger log = Logger.getLogger(CloudStorage.class.getName());
  private static final String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
  /** The upload is refused unless the data part repeats exactly the content type of the metadata part. */
  static final String CONTENT_TYPE = "application/json; charset=utf-8";

  private CloudStorage() {
  }

  public static String bucket() {
    return System.getProperty("rank.bucket");
  }

  /** Returns the parsed file, or null when it does not exist yet. */
  public static JsonObject readJson(String path) throws IOException {
    URL url = new URL("https://storage.googleapis.com/storage/v1/b/" + bucket() + "/o/" + encode(path) + "?alt=media");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Authorization", "Bearer " + token());
    int code = conn.getResponseCode();
    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
      return null;
    }
    if (code != HttpURLConnection.HTTP_OK) {
      throw new IOException("Cloud Storage answered HTTP " + code + " reading " + path);
    }
    try (InputStream in = conn.getInputStream()) {
      return new JsonParser().parse(new String(readAll(in), StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }

  /** Writes the file gzip-compressed, with the given browser cache duration. */
  public static void writeJson(String path, JsonObject json, int cacheSeconds) throws IOException {
    byte[] gzipped = gzip(json.toString().getBytes(StandardCharsets.UTF_8));
    String boundary = "rank-opcvm-" + System.nanoTime();
    ByteArrayOutputStream body = multipartBody(path, gzipped, cacheSeconds, boundary);

    URL url = new URL("https://storage.googleapis.com/upload/storage/v1/b/" + bucket() + "/o?uploadType=multipart");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Authorization", "Bearer " + token());
    conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
    try (OutputStream out = conn.getOutputStream()) {
      body.writeTo(out);
    }
    int code = conn.getResponseCode();
    if (code != HttpURLConnection.HTTP_OK) {
      String error = "";
      try (InputStream in = conn.getErrorStream()) {
        if (in != null) {
          error = new String(readAll(in), StandardCharsets.UTF_8);
        }
      }
      throw new IOException("Cloud Storage answered HTTP " + code + " writing " + path + ": " + error);
    }
    log.info("Published " + path + " (" + gzipped.length / 1024 + " KB compressed)");
  }

  /** The metadata part (name, gzip encoding, cache) then the gzipped JSON, as a multipart/related upload. */
  static ByteArrayOutputStream multipartBody(String path, byte[] gzipped, int cacheSeconds, String boundary) throws IOException {
    JsonObject metadata = new JsonObject();
    metadata.addProperty("name", path);
    metadata.addProperty("contentType", CONTENT_TYPE);
    metadata.addProperty("contentEncoding", "gzip");
    metadata.addProperty("cacheControl", "public, max-age=" + cacheSeconds);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n--" + boundary
                + "\r\nContent-Type: " + CONTENT_TYPE + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(gzipped);
    body.write(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
    return body;
  }

  private static String token() {
    return AppIdentityServiceFactory.getAppIdentityService().getAccessToken(Collections.singletonList(SCOPE)).getAccessToken();
  }

  private static String encode(String path) throws IOException {
    return URLEncoder.encode(path, StandardCharsets.UTF_8.name()).replace("+", "%20");
  }

  static byte[] gzip(byte[] data) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(data);
    }
    return out.toByteArray();
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }
}
