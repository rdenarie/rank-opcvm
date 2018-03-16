package com.rdenarie;

import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.repackaged.com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.appengine.repackaged.com.google.api.client.http.HttpStatusCodes.isRedirect;

@WebServlet(name = "OpcvmService", value = "/opcvmService")
public class OpcvmService extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    Properties properties = System.getProperties();
    String id=request.getParameter("id");

    JsonObject result = new JsonObject();


    try {
      String boursoResponse = getBoursoResponse(id);

      result.addProperty("id",id);
      result.addProperty("score",150);
      System.out.println(boursoResponse);
      result.addProperty("page",boursoResponse);



    } catch (Exception e) {
      e.printStackTrace();

    }
    response.setContentType("application/json");
    response.getWriter().println(result.toString());
  }

  private String getBoursoResponse(String id) throws IOException {

    URL url = new URL("https://bourse.boursorama.com/recherche/"+id);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    int status = conn.getResponseCode();

    while (isRedirect(status)) {
      String newUrl = conn.getHeaderField("Location");
      // open the new connnection again
      conn = (HttpURLConnection) new URL(newUrl).openConnection();
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
    System.out.println(response.toString());


    Pattern pattern = Pattern.compile("<div class=\"c-fund-performances__table\">(.*?)</div>");
    Matcher matcher = pattern.matcher(response.toString());
    if (matcher.find()) {
      return (matcher.group(1));
    }
    return "";
  }

}
