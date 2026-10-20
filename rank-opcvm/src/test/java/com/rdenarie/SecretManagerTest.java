package com.rdenarie;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonParser;
import org.junit.Test;

public class SecretManagerTest {

  @Test
  public void decodesTheSecretPayload() {
    //"abcd efgh ijkl mnop\n" in base64, as Secret Manager returns it
    String response = "{\"name\":\"projects/1/secrets/s/versions/1\",\"payload\":{\"data\":\"YWJjZCBlZmdoIGlqa2wgbW5vcAo=\"}}";

    assertEquals("abcd efgh ijkl mnop", SecretManager.decode(new JsonParser().parse(response).getAsJsonObject()));
  }
}
