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
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

      Document doc = Jsoup.parse(boursoResponse);

      result.addProperty("id",id);
      extractValues(result, doc);
      JsonObject values = result.getAsJsonObject("values");
      double scoreFond=calculScore(values.getAsJsonObject("fond"));
      double scoreCategory=calculScore(values.getAsJsonObject("category"));
      double scorePercentile=calculScorePercentile(values.getAsJsonObject("percentile"));
      result.addProperty("scoreFond",scoreFond);
      result.addProperty("scoreCategory",scoreCategory);
      result.addProperty("scorePercentile",scorePercentile);

      extractName(result,doc);
      extractMSRating(result,doc);

      extractCostsConditions(result,doc);
      extractFacePrice(result,doc);
      extractGerant(result,doc);
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
    while (current<=object.size()) {
      String val = object.get(indexes[current-1]).getAsString();
      if (!val.equals("")) {
        somme+=new Double(val);
        nbElement++;
      }
      current++;
    }
    score=somme/nbElement;
    if (nbElement>4) {
      score=score+object.get("3ans").getAsDouble();
    } else {
      score=score+object.get(indexes[nbElement-1]).getAsDouble();
    }
    return Math.round(score * 100.0) / 100.0;



  }

  private int calculScorePercentile(JsonObject object) {
    double score;
    int current = 1;
    int nbElement=0;
    double somme=0d;
    while (current<=object.size()) {
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

  private void extractValues(JsonObject result, Document boursoResponse) {

    Element fundPerf = boursoResponse.selectFirst("div.c-fund-performances__table");

    Elements trs = fundPerf.select("tr.c-table__row");
    JsonObject values = new JsonObject();
    int i=0;
    for (Element tr: trs) {
      Elements tds=tr.select("td.c-table__cell");
      JsonObject listeValue = new JsonObject();
      int j=0;
      for (Element td : tds) {
        if (!td.text().equals("-")) {
          if (i == 3) {
            //percentile
            listeValue.addProperty(indexes[j], new Integer(td.text().replace("%", "")));
          } else if (i == 0) {
            //do nothing, it tr in thead
          } else {
            //fond ou category
            listeValue.addProperty(indexes[j], new Double(td.text().replace("%", "")));
          }
        }
        j++;
      }
      if (i==1) {
        values.add("fond", listeValue);
      } else if (i==2) {
        values.add("category", listeValue);
      }else if (i==3) {
        values.add("percentile", listeValue);
      }
      i++;
    }

    result.add("values", values);
  }

  private void extractName(JsonObject result, Document boursoResponse) {
    Element name = boursoResponse.selectFirst("a.c-faceplate__company-link");
    result.addProperty("name",name.text());
  }

  private void extractMSRating(JsonObject result, Document boursoResponse) {
    Element input = boursoResponse.selectFirst("input.c-rating__check[checked]");
    result.addProperty("msrating",input==null ? "ND" : input.attr("value"));
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


    return response.toString();
  }

  private void extractCostsConditions(JsonObject result, Document boursoResponse) {


    Element costsConditions = boursoResponse.select("div.c-costs-conditions").first();

    Elements costs = costsConditions.select("tr.c-table__row--to-list");
    int i=1;
    for (Element cost : costs) {

      if (i==1) {
        //Frais d'entrée
        String fraisEntreeValue = "";
        Elements fraisEntree = cost.select("td");
        for (Element entree : fraisEntree) {
          fraisEntreeValue = entree.text();
        }
        fraisEntreeValue=fraisEntreeValue.trim();
        result.addProperty("entree",fraisEntreeValue.equals("") ? "ND" : fraisEntreeValue);
      } else if (i==2) {
        //Frais de sortie
        String fraisSortieValue = "";
        Elements fraisSortie = cost.select("td");
        for (Element sortie : fraisSortie) {
          fraisSortieValue = sortie.text();
        }
        fraisSortieValue=fraisSortieValue.trim();
        result.addProperty("sortie",fraisSortieValue.equals("") ? "ND" : fraisSortieValue);
      }else if (i==3) {
        //Frais courant
        String fraisCourantValue = "";
        Elements fraisCourant = cost.select("td");
        for (Element courant : fraisCourant) {
          fraisCourantValue = courant.text();
        }
        fraisCourantValue=fraisCourantValue.trim();

        if (fraisCourantValue.contains(" ")) {
          fraisCourantValue=fraisCourantValue.substring(0,fraisCourantValue.indexOf("%")+1);
        }
        result.addProperty("courant",fraisCourantValue.equals("") ? "ND" : fraisCourantValue);
      }



      i++;
    }

    Elements parts = costsConditions.select("div.c-list-info").first().select("li");
    i=1;
    for (Element part : parts){
      if (i==1) {
        String ticketIn=part.selectFirst("p.c-list-info__value").text();
        result.addProperty("ticketIn",ticketIn.equals("") ? "ND" : ticketIn);
      }
      if (i==3) {
        String ticketRenew=part.selectFirst("p.c-list-info__value").text();
        result.addProperty("ticketRenew",ticketRenew.equals("") ? "ND" : ticketRenew);
      }
      i++;
    }




  }

  private void extractFacePrice(JsonObject result, Document doc) {
    Element facePrice = doc.select("div.c-faceplate__price").first();
    Element price = facePrice.selectFirst("span");
    result.addProperty("price",new Double(price.text()));

    Element currentcy = facePrice.select("span").last();
    result.addProperty("currency",currentcy.text());


    Element faceQuotation = doc.selectFirst("div.c-faceplate__quotation");
    String actif = faceQuotation.select("li.c-list-info__item").get(2).text().split("/")[1].replace(" ","");
    result.addProperty("actifM",new Double(actif));



  }
  private void extractGerant(JsonObject result, Document doc) {
    Element gerant = doc.selectFirst("div.c-fund-characteristics")
            .selectFirst("ul.c-list-info__list")
            .select("li.c-list-info__item").get(2)
            .selectFirst("li.c-list-info__value");
    result.addProperty("gerant",gerant.text());


    Element categories = doc.selectFirst("div.c-fund-characteristics")
            .select("ul.c-list-info__list").get(1);
    Element categoryGen = categories.select("li.c-list-info__item").get(0).selectFirst("p.c-list-info__value").selectFirst("a");
    Element categoryMS = categories.select("li.c-list-info__item").get(1).selectFirst("p.c-list-info__value").selectFirst("a");
    Element categoryAMF = categories.select("li.c-list-info__item").get(2).selectFirst("p.c-list-info__value").selectFirst("a");

    result.addProperty("categGenerale",categoryGen.text());
    result.addProperty("categMS",categoryMS.text());
    result.addProperty("categAMF",categoryAMF.text());

  }


}
