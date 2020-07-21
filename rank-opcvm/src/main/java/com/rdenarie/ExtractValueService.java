package com.rdenarie;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebServlet(name = "ExtractValueService", value = "/extractValueService")
public class ExtractValueService extends HttpServlet {

  private static final Logger log = Logger.getLogger(ExtractValueService.class.getName());

  private static double MAX_EUR_FACE_VALUE=50000.0;

  static String[] indexes= {"1erjanvier","1mois","6mois","1an","3ans","5ans","10ans"};

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    Utils.setTimeZone();

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

      if (!checkLastValue(doc)) {
        return null;
      }


      String isin = extractIsin(result, doc);
      if (isin!=null) {
        log.fine("Read datas for isin="+isin);
        extractValues(result, doc);
        JsonObject values = result.getAsJsonObject("values");
        if (values.size() == 0) {
          //some opcvm have no score.
          //ignore it without exception
          return null;
        }
        double scoreFond = calculScoreFond(values.getAsJsonObject("fond"), values.getAsJsonObject("category"));
        double scoreCategory = calculScore(values.getAsJsonObject("category"));
        double scorePercentile = calculScorePercentile(values.getAsJsonObject("percentile"));
        result.addProperty("scoreFond", scoreFond);
        result.addProperty("scoreCategory", scoreCategory);
        result.addProperty("scorePercentile", scorePercentile);

        extractName(result, doc);
        extractMSRating(result, doc);

        extractCostsConditions(result, doc);
        extractFacePrice(result, doc);
        extractGerant(result, doc);
        result.addProperty(Utils.CATEGORY_PERSO_PROPERTY, categoryPersoName);








        if (!shouldWeKeepFund(result)) {
          //do not take in accountfund which are too costly
          return null;
        }
      } else {
        return null;
      }

    } catch (Exception e) {
      storeException(id,e);
      e.printStackTrace();
      return null;


    }
    return result;
  }

  private static boolean checkLastValue(Document boursoResponse) {
    //return false if the last known value is older than 1 year

    Element name = boursoResponse.selectFirst("div.c-faceplate__real-time");
    if (name==null) return true;
    String date = name.text().substring(name.text().length()-10);

    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    Instant instant = null;
    try {
      instant = dateFormat.parse(date).toInstant();
      Instant now = Instant.now();

      return !instant.isBefore(now.minus(Period.ofDays(365)));
    } catch (ParseException e) {
      log.fine("Error when parsing date : "+date);
      e.printStackTrace();
    }
    return true;


  }

  private static boolean shouldWeKeepFund(JsonObject result) {
    return true;

    //keep only funds for which the ticketIn is less that MAX_FACE_PRICE
//    try {
//      String initial = result.get(Utils.TICKET_IN_PROPERTY).getAsString();
//      if (initial.contains("ND") || !initial.contains(" ")) {
//        return true;
//      }
//
//      double priceInEur = result.get(Utils.PRICE_EUR_PROPERTY).getAsDouble();
//      double priceInCurrency = result.get(Utils.PRICE_PROPERTY).getAsDouble();
//      double ratio = priceInCurrency / priceInEur;
//
//      double ticketInFaceValue;
//      String[] initials = initial.split(" ");
//      if (initials[1].equals("PART") || initials[1].equals("parts")) {
//        ticketInFaceValue = Double.valueOf(initials[0]) * priceInEur;
//      } else if (initials[1].equals("EUR")) {
//        ticketInFaceValue = Double.valueOf(initials[0]);
//      } else {
//        ticketInFaceValue = Double.valueOf(initials[0])  / ratio;
//      }
//
//      log.fine("TicketIn eur value " + ticketInFaceValue);
//      return ticketInFaceValue <= MAX_EUR_FACE_VALUE;
//    } catch (Exception e) {
//      log.severe("Problem when calculating ticketInEur value. TicketInProperty="+result.get(Utils.TICKET_IN_PROPERTY).getAsString());
//      e.printStackTrace();
//      return true;
//    }
  }

  private static String extractIsin(JsonObject result, Document boursoResponse) {
    Element name = boursoResponse.selectFirst("h2.c-faceplate__isin");
    if (name==null) return null;
    result.addProperty("id",name.text().split(" -")[0]);
    return name.text().split(" -")[0];
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


  private static double calculScore(JsonObject fondValues) {
    double score;
    int current = 1;
    int nbElement=0;
    double somme=0d;
    double lastValue = 0d;
    while (current<=indexes.length) {
      if (fondValues.get(indexes[current-1]) == null) {
        current ++;
        continue;
      }
      String val = fondValues.get(indexes[current-1]).getAsString();
      if (!val.equals("")) {
        somme+=new Double(val);
        lastValue = new Double(val);
        nbElement++;
      }
      current++;
    }

    score=somme/nbElement;
    if (nbElement==0) {
      return 0d;
    }
    if (nbElement>4) {
      if (fondValues.get("3ans")!=null) {
        score = score + fondValues.get("3ans").getAsDouble();
      } else if (fondValues.get("5ans")!=null) {
        score = score + fondValues.get("5ans").getAsDouble();
      } else {
        score = score + fondValues.get("10ans").getAsDouble();
      }
    } else {
      score=score+lastValue;
    }
    return Math.round(score * 100.0) / 100.0;

  }



  private static double calculScoreFond(JsonObject fondValues, JsonObject categoryValues) {
    JsonObject fondValuesCompleted =Utils.deepCopy(fondValues);
    if (hasMissingValues(fondValuesCompleted)) {
      log.fine("hasMissingValues");
      int current=indexes.length;
      boolean startModification = false;
      while (current>0) {
        log.fine("current="+current);
        log.fine("startModification="+startModification);
        if (!startModification) {
          if (fondValuesCompleted.get(indexes[current - 1]) != null) {
            startModification = true;
          }
        } else {
          if (fondValuesCompleted.get(indexes[current - 1]) == null) {
            fondValuesCompleted.addProperty(indexes[current - 1],categoryValues.get(indexes[current - 1]).getAsDouble());
          }
        }
        current--;
      }
    }
    fondValues=sort(fondValuesCompleted);

    double score;
    int current = 1;
    int nbElement=0;
    double somme=0d;
    double lastValue = 0d;
    while (current<=indexes.length) {
      if (fondValues.get(indexes[current-1]) == null) {
        current ++;
        continue;
      }
      String val = fondValues.get(indexes[current-1]).getAsString();
      if (!val.equals("")) {
        somme+=new Double(val);
        lastValue = new Double(val);
        nbElement++;
      }
      current++;
    }

    score=somme/nbElement;
    if (nbElement==0) {
      return 0d;
    }
    if (nbElement>4) {
      if (fondValues.get("3ans")!=null) {
        score = score + fondValues.get("3ans").getAsDouble();
      } else if (fondValues.get("5ans")!=null) {
        score = score + fondValues.get("5ans").getAsDouble();
      } else {
        score = score + fondValues.get("10ans").getAsDouble();
      }
    } else {
      score=score+lastValue;
    }
    return Math.round(score * 100.0) / 100.0;

  }

  private static JsonObject sort(JsonObject objectToSort) {
    JsonObject result = new JsonObject();
    int current = 1;
    while (current<=indexes.length) {
      if (objectToSort.get(indexes[current-1]) != null) {
        result.addProperty(indexes[current - 1],objectToSort.get(indexes[current - 1]).getAsDouble());
      }
      current ++;
    }
    return result;
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
    if (nbElement==0) {
      return 0;
    }
    if (nbElement>5) {
      score=score+object.get("5ans").getAsInt();
    } else {
      score = score + object.get(indexes[nbElement - 1]).getAsDouble();
    }

    return (int) Math.round(score);
  }

  private static void extractValues(JsonObject result, Document boursoResponse) {
    Element fundPerf = boursoResponse.selectFirst("div.c-fund-performances__table");
    JsonObject values = new JsonObject();
    List<String> thead = new ArrayList<String>();
    if (fundPerf!=null) {
      Elements trs = fundPerf.select("tr.c-table__row");
      int i=0;
      for (Element tr: trs) {
        if (i==0) {
          //thead
          Elements tds=tr.select("th.c-table__title");
          for (Element td : tds) {
            if (!td.text().equals("")) {
              thead.add(td.text());
            }
          }
          i++;
          continue;
        }
        Elements tds=tr.select("td.c-table__cell");
        JsonObject listeValue = new JsonObject();
        int j=0;
        for (Element td : tds) {
          if (!td.text().equals("-") && !td.text().equals("")) {
            int index = Arrays.asList(indexes).indexOf(thead.get(j).toLowerCase().replace(" ","").replace("janv.","janvier"));
            if (i == 3) {
              //percentile
              listeValue.addProperty(indexes[index], new Integer(td.text().replace("%", "")));
            } else if (i == 0) {
              //do nothing, it tr in thead
            } else {
              //fond ou category
              listeValue.addProperty(indexes[index], new Double(td.text().replace("%", "")));
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

    }

    JsonObject fundValue = values.get("fond").getAsJsonObject();
    hasMissingValues(fundValue);
    result.addProperty("missingValues",hasMissingValues(fundValue));
    result.add("values", values);

  }

  private static boolean hasMissingValues(JsonObject fundValue) {
    int current = 1;
    int firstNullIndex=-1;
    boolean foundNull=false;
    int lastValueIndex=-1;
    while (current<=indexes.length) {
      if (fundValue.get(indexes[current - 1]) == null) {
        if (!foundNull) {
          foundNull=true;
          firstNullIndex=current-1;
        }
      } else {
        lastValueIndex=current-1;
      }
      current++;
    }
    log.fine("FirstNullIndex="+firstNullIndex);
    log.fine("lastValueIndex="+lastValueIndex);
    return (firstNullIndex!= -1 && firstNullIndex<lastValueIndex);
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
    return Utils.getBoursoResponse("https://www.boursorama.com/recherche/"+id);

  }
  public static String getFondValueOnBoursoByBoursoId(String id) throws IOException {
    try {
      //need to sleep here to not reach quota of 22Mo/min
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
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
    result.addProperty(Utils.PRICE_PROPERTY,new Double(price.text().replace(" ","")));

    Element currentcy = facePrice.select("span").last();
    result.addProperty(Utils.CURRENCY_PROPERTY,currentcy.text());

    if (currentcy.text().equals("EUR")) {
      result.addProperty(Utils.PRICE_EUR_PROPERTY,new Double(price.text().replace(" ","")));
    } else {
      Element facePriceEur = doc.select("div.c-faceplate__indicative").first();
      Element priceEur = facePriceEur.selectFirst("span.c-faceplate__indicative-value");
      result.addProperty(Utils.PRICE_EUR_PROPERTY,new Double(priceEur.text().replace("EUR","").replace(" ","")));
    }


    Element faceQuotation = doc.select("div.c-faceplate__quotation").get(2);
    String actif = faceQuotation.select("li.c-list-info__item").get(0).text().split("/")[1].replace(" ","");
    result.addProperty(Utils.ACTIF_PROPERTY,new Double(actif));



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
