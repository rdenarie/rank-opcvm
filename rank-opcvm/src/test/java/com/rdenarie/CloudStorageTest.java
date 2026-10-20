package com.rdenarie;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class CloudStorageTest {

  @Test
  public void repeatsTheMetadataContentTypeOnTheDataPart() throws Exception {
    String body = new String(CloudStorage.multipartBody("index.json", new byte[] {1, 2}, 300, "B").toByteArray(), StandardCharsets.ISO_8859_1);

    //Cloud Storage answers HTTP 400 when the two content types differ
    assertTrue(body, body.contains("\"contentType\":\"" + CloudStorage.CONTENT_TYPE + "\""));
    assertTrue(body, body.contains("--B\r\nContent-Type: " + CloudStorage.CONTENT_TYPE + "\r\n\r\n"));
    assertTrue(body, body.contains("\"contentEncoding\":\"gzip\""));
  }
}
