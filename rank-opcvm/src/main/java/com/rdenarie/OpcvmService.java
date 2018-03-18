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

  String[] indexes= {"1erjanvier","1mois","6mois","1an","3ans","5ans","10ans"};

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    Properties properties = System.getProperties();
    String id=request.getParameter("id");

    JsonObject result = new JsonObject();


    try {
      String boursoResponse = getBoursoResponse(id);

      result.addProperty("id",id);
      JsonObject values = extractValues(boursoResponse);
      result.add("values",values);
      double scoreFond=calculScore(values.getAsJsonObject("fond"));
      double scoreCategory=calculScore(values.getAsJsonObject("category"));
      double scorePercentile=calculScorePercentile(values.getAsJsonObject("percentile"));
      result.addProperty("scoreFond",scoreFond);
      result.addProperty("scoreCategory",scoreCategory);
      result.addProperty("scorePercentile",scorePercentile);

    } catch (Exception e) {
      e.printStackTrace();

    }
    response.setContentType("application/json");
    response.getWriter().println(result.toString());
  }

  private double calculScore(JsonObject object) {
    double score;
    int current = 1;
    int nbElement=0;
    double somme=0d;
    while (current<=indexes.length) {
      String val = object.get(indexes[current-1]).getAsString();
      if (!val.equals("")) {
        somme+=new Double(val);
        nbElement++;
      }
      current++;
    }
    System.out.println("Somme="+somme+" nbElement="+nbElement);
    score=somme/nbElement;
    System.out.println("Score="+score);
    if (nbElement>4) {
      score=score+object.get("3ans").getAsDouble();
    } else {
      score=score+object.get(indexes[nbElement-1]).getAsDouble();
    }
    System.out.println("Score="+score);

    return score;



  }

  private int calculScorePercentile(JsonObject object) {
    double score;
    int current = 1;
    int nbElement=0;
    double somme=0d;
    while (current<=indexes.length) {
      String val = object.get(indexes[current-1]).getAsString();
      if (!val.equals("")) {
        somme+=new Double(val);
        nbElement++;
      }
      current++;
    }
    score=somme/nbElement;
    if (nbElement>5) {
      score=score+object.get("5ans").getAsInt();
    } else {
      score=score+object.get(indexes[current-1]).getAsDouble();
    }

    return (int) Math.round(score);
  }

  private JsonObject extractValues(String boursoResponse) {
    Pattern pattern = Pattern.compile("<td class = 'c-table__cell c-table__cell--dotted c-table__cell--(?:negative|positive)'>(.*?)</td>");
    Matcher matcher = pattern.matcher(boursoResponse);
    JsonObject result = new JsonObject();
    int current=1;
    //we want only the 8 first value for scoreFond
    JsonObject fond = new JsonObject();
    JsonObject category = new JsonObject();
    JsonObject percentile = new JsonObject();


    while (matcher.find()) {
      String group = matcher.group(1).trim();
      System.out.println(group);
      //4.57%
      //or -4.57%

      //remove %
      group = group.substring(0, group.length() - 1);
      if (current<=indexes.length) {
        fond.addProperty(indexes[current-1], group);
      } else {
        int index=current-indexes.length;
        category.addProperty(indexes[index-1],group);
      }
      current++;

    }
    result.add("fond",fond);
    result.add("category",category);

    pattern = Pattern.compile("<td class=\"c-table__cell c-table__cell--dotted\">(.*?)</td>");
    matcher = pattern.matcher(boursoResponse);
    current=1;
    while (matcher.find()) {
      String group = matcher.group(1).trim();
      System.out.println(group);
      //4.57%
      //or -4.57%

      //remove %
      group = group.substring(0, group.length() - 1);
      percentile.addProperty(indexes[current-1], group);
      current++;
    }
    result.add("percentile",percentile);

    return result;
  }

  private String getBoursoResponse(String id) throws IOException {

    URL url = new URL("https://bourse.boursorama.com/recherche/"+id);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    String myCookie = "B20_TRADING_ENABLED=1";
    conn.setRequestProperty("Cookie", myCookie);


    int status = conn.getResponseCode();
    int nbRedirect=0;
    while (isRedirect(status) && nbRedirect<5) {
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


    Pattern pattern = Pattern.compile("<div class=\"c-fund-performances__table\">(.*?)</div>");
    Matcher matcher = pattern.matcher(response.toString());
    if (matcher.find()) {
      return (matcher.group(1));
    }
    return "";
  }

}
