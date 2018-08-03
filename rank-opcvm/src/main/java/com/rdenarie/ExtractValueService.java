package com.rdenarie;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.repackaged.com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;

@WebServlet(name = "ExtractValueService", value = "/extractValueService")
public class ExtractValueService extends HttpServlet {

  static String[] indexes= {"1erjanvier","1mois","6mois","1an","3ans","5ans","10ans"};

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String id=request.getParameter("id");
    JsonObject result = getValue(id, false,"TBD Claude Category");
    response.setContentType("application/json");
    response.getWriter().println(result == null ? "" : result.toString());
  }

  public static JsonObject getValue(String id, boolean isBoursoId, String categoryPersoName) {
    JsonObject result = new JsonObject();

    try {
      String boursoResponse = isBoursoId ? getFondValueOnBoursoByBoursoId(id ) : getFondValueOnBoursoByIsin(id);

      Document doc = Jsoup.parse(boursoResponse);

      extractIsin(result, doc);
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
      result.addProperty(Utils.CATEGORY_PERSO_PROPERTY,categoryPersoName);


    } catch (Exception e) {
      storeException(id,e);
      e.printStackTrace();
      return null;


    }
    return result;
  }

  private static void extractIsin(JsonObject result, Document boursoResponse) {
    Element name = boursoResponse.selectFirst("h2.c-faceplate__isin");
    result.addProperty("id",name.text().split(" - ")[0]);
  }

  private static void storeException(String id, Exception e) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Entity exceptionElement = new Entity(Utils.EXCEPTION_ENTITY);
    Calendar now = Calendar.getInstance();
    exceptionElement.setProperty("id", id);
    exceptionElement.setProperty("date", now.getTime());
    exceptionElement.setProperty("stack", e.toString());
    exceptionElement.setProperty("message", e.getMessage());
    datastore.put(exceptionElement);


  }


  private static double calculScore(JsonObject object) {
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

  private static int calculScorePercentile(JsonObject object) {
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

  private static void extractValues(JsonObject result, Document boursoResponse) {

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

  private static void extractName(JsonObject result, Document boursoResponse) {
    Element name = boursoResponse.selectFirst("a.c-faceplate__company-link");
    result.addProperty("name",name.text());
  }

  private static void extractMSRating(JsonObject result, Document boursoResponse) {
    Element input = boursoResponse.selectFirst("input.c-rating__check[checked]");
    result.addProperty("msrating",input==null ? "ND" : input.attr("value"));
  }




  public static String getFondValueOnBoursoByIsin(String id) throws IOException {
    return Utils.getBoursoResponse("https://bourse.boursorama.com/recherche/"+id);

  }
  public static String getFondValueOnBoursoByBoursoId(String id) throws IOException {
    return Utils.getBoursoResponse("https://www.boursorama.com/bourse/opcvm/cours/"+id);

  }

  private static void extractCostsConditions(JsonObject result, Document boursoResponse) {


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

  private static void extractFacePrice(JsonObject result, Document doc) {
    Element facePrice = doc.select("div.c-faceplate__price").first();
    Element price = facePrice.selectFirst("span");
    result.addProperty("price",new Double(price.text().replace(" ","")));

    Element currentcy = facePrice.select("span").last();
    result.addProperty("currency",currentcy.text());


    Element faceQuotation = doc.selectFirst("div.c-faceplate__quotation");
    String actif = faceQuotation.select("li.c-list-info__item").get(2).text().split("/")[1].replace(" ","");
    result.addProperty("actifM",new Double(actif));



  }
  private static void extractGerant(JsonObject result, Document doc) {
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

    result.addProperty(Utils.CATEGORY_GEN_PROPERTY,categoryGen.text());
    result.addProperty(Utils.CATEGORY_MS_PROPERTY,categoryMS.text());
    result.addProperty(Utils.CATEGORY_AMF_PROPERTY,categoryAMF.text());




  }


}
