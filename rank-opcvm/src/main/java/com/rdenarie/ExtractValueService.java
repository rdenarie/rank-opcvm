package com.rdenarie;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.gson.JsonObject;
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
import java.util.Locale;
import java.util.logging.Level;
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
    response.getWriter().println(result == null ? "{}" : result.toString());
  }

  /**
   * Why a fund was not stored. The anomalies are the reasons that, in bulk, mean the scraping is broken
   * (Boursorama unreachable or its page format changed) rather than the fund being legitimately filtered out.
   */
  public enum Rejection {
    FETCH_FAILED(true), NO_ISIN(true), NO_SCORES(true), PARSE_ERROR(true), TOO_OLD(false), TOO_EXPENSIVE(false);

    public final boolean anomaly;

    Rejection(boolean anomaly) {
      this.anomaly = anomaly;
    }
  }

  /** The extracted fund, or the reason it was rejected. */
  public static final class Extraction {
    public final JsonObject value;
    public final Rejection rejection;
    public final String detail;

    private Extraction(JsonObject value, Rejection rejection, String detail) {
      this.value = value;
      this.rejection = rejection;
      this.detail = detail;
    }

    static Extraction ok(JsonObject value) {
      return new Extraction(value, null, null);
    }

    static Extraction rejected(Rejection rejection, String detail) {
      return new Extraction(null, rejection, detail);
    }
  }

  public static JsonObject getValue(String id, boolean isBoursoId, String categoryPersoName) {
    return extract(id, isBoursoId, categoryPersoName).value;
  }

  public static Extraction extract(String id, boolean isBoursoId, String categoryPersoName) {
    String boursoResponse;
    try {
      boursoResponse = isBoursoId ? getFondValueOnBoursoByBoursoId(id) : getFondValueOnBoursoByIsin(id);
    } catch (IOException e) {
      return Extraction.rejected(Rejection.FETCH_FAILED, e.toString());
    }
    if (boursoResponse == null) {
      return Extraction.rejected(Rejection.FETCH_FAILED, "no response from Boursorama");
    }
    try {
      return parse(Jsoup.parse(boursoResponse), categoryPersoName, Instant.now());
    } catch (Exception e) {
      storeException(id, e);
      log.log(Level.WARNING, "Unable to parse Boursorama page of " + id, e);
      return Extraction.rejected(Rejection.PARSE_ERROR, e.toString());
    }
  }

  static Extraction parse(Document doc, String categoryPersoName, Instant now) {
    JsonObject result = new JsonObject();

    if (!checkLastValue(doc, now)) {
      return Extraction.rejected(Rejection.TOO_OLD, "last value older than one year");
    }

    String isin = extractIsin(result, doc);
    if (isin == null) {
      return Extraction.rejected(Rejection.NO_ISIN, "not a fund page: " + doc.title());
    }
    log.fine("Read datas for isin=" + isin);
    extractValues(result, doc);
    JsonObject values = result.getAsJsonObject("values");
    if (values.getAsJsonObject("fond").size() == 0 || values.getAsJsonObject("category").size() == 0) {
      //some opcvm have no score, but all of them having none means the performance table changed
      return Extraction.rejected(Rejection.NO_SCORES, "isin=" + isin + ", values=" + values);
    }
    double scoreFond = calculScoreFond(values.getAsJsonObject("fond"), values.getAsJsonObject("category"));
    double scoreCategory = calculScore(values.getAsJsonObject("category"));
    double scorePercentile = calculScorePercentile(values.getAsJsonObject("percentile"));
    result.addProperty("scoreFond", scoreFond);
    result.addProperty("scoreCategory", scoreCategory);
    result.addProperty("scorePercentile", scorePercentile);

    extractName(result, doc);
    extractType(result, doc);
    extractMSRating(result, doc);

    extractCostsConditions(result, doc);
    extractFacePrice(result, doc);
    extractGerant(result, doc);
    result.addProperty(Utils.CATEGORY_PERSO_PROPERTY, categoryPersoName);

    if (!shouldWeKeepFund(result)) {
      //do not take in accountfund which are too costly
      return Extraction.rejected(Rejection.TOO_EXPENSIVE, "ticketIn=" + result.get(Utils.TICKET_IN_PROPERTY));
    }
    return Extraction.ok(result);
  }

  private static boolean checkLastValue(Document boursoResponse, Instant now) {
    //return false if the last known value is older than 1 year

    Element name = boursoResponse.selectFirst("div.c-faceplate__real-time");
    if (name==null) return true;
    String date = name.text().substring(name.text().length()-10);

    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    Instant instant = null;
    try {
      instant = dateFormat.parse(date).toInstant();

      return !instant.isBefore(now.minus(Period.ofDays(365)));
    } catch (ParseException ignored) {
      //if parseException, return true, we do not want to ignore

    }
    return true;


  }

  private static boolean shouldWeKeepFund(JsonObject result) {
//    return true;
//
    //keep only funds for which the ticketIn is less that MAX_FACE_PRICE
    try {
      String initial = result.get(Utils.TICKET_IN_PROPERTY).getAsString();
      if (initial.contains("ND") || !initial.contains(" ")) {
        return true;
      }

      double priceInEur = result.get(Utils.PRICE_EUR_PROPERTY).getAsDouble();
      double priceInCurrency = result.get(Utils.PRICE_PROPERTY).getAsDouble();
      double ratio = priceInCurrency / priceInEur;

      double ticketInFaceValue;
      String[] initials = initial.split(" ");
      if (initials[1].equals("PART") || initials[1].equals("parts")) {
        ticketInFaceValue = Double.valueOf(initials[0]) * priceInEur;
      } else if (initials[1].equals("EUR")) {
        ticketInFaceValue = Double.valueOf(initials[0]);
      } else {
        ticketInFaceValue = Double.valueOf(initials[0])  / ratio;
      }

      log.fine("TicketIn eur value=" + ticketInFaceValue+", ticketFaceValue="+initial+", ratio="+ratio);
      return ticketInFaceValue <= MAX_EUR_FACE_VALUE;
    } catch (Exception e) {
      if (result.get(Utils.TICKET_IN_PROPERTY)==null) {
        return true;
      }
      log.severe("\n\nProblem when calculating ticketInEur value. TicketInProperty="+result.get(Utils.TICKET_IN_PROPERTY).getAsString()+"\n\n");
      e.printStackTrace();
      return true;
    }
  }

  private static String extractIsin(JsonObject result, Document boursoResponse) {
    Element name = boursoResponse.selectFirst("h2.c-faceplate__isin");
    if (name==null) return null;
    result.addProperty("id",name.text().split(" -")[0]);
    return name.text().split(" -")[0];
  }

  private static String extractType(JsonObject result, Document boursoResponse) {
    Element type = boursoResponse.selectFirst("div.c-faceplate__offer-logo");
    if (type==null) {
      result.addProperty("type", "");
      return "";
    }
    String typeFond="";
    String typeClass = type.attr("title");
    if (typeClass != null && typeClass.contains("partenaire")) {
      typeFond="partenaire";
    } else if (typeClass != null && typeClass.contains("Boursomarkets")) {
      typeFond="boursomarkets";
    }
    result.addProperty("type",typeFond);
    return typeFond;
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
        somme+=Double.parseDouble(val);
        lastValue = Double.parseDouble(val);
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
    //s'il manque des valeurs, on va completer pour ne pas avoir de trous (apres cela, toutes les valeurs entre la premiere et
    // la derniere seront remplies
    if (hasMissingValues(fondValuesCompleted)) {
      int current=indexes.length;
      boolean startModification = false;
      while (current>0) {
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

    log.fine("values for calculating "+fondValues);

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
        somme+=Double.parseDouble(val);
        lastValue =Double.parseDouble(val);
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
    double lastValue=0d;
    while (current<=indexes.length) {
      //a period Boursorama shows as "-" is absent: skip it instead of failing the whole fund
      if (object.get(indexes[current-1]) != null && !object.get(indexes[current-1]).getAsString().equals("")) {
        lastValue=Double.parseDouble(object.get(indexes[current-1]).getAsString());
        somme+=lastValue;
        nbElement++;
      }
      current++;
    }
    if (nbElement==0) {
      return 0;
    }
    score=somme/nbElement;
    if (nbElement>5 && object.get("5ans") != null) {
      score=score+object.get("5ans").getAsInt();
    } else {
      score = score + lastValue;
    }

    return (int) Math.round(score);
  }

  /**
   * Reads the end-of-month performance table. Boursorama has published it in two orientations:
   * periods as columns with one row per series (fund, category, percentile), and, since October 2026,
   * periods as rows with one column per series (FONDS, CATEGORIE, RANG*). Both are recognised by their labels.
   */
  static void extractValues(JsonObject result, Document boursoResponse) {
    Element table = boursoResponse.selectFirst("div.c-fund-performances__table table");
    boolean isFonds = table != null;
    if (table == null) {
      table = boursoResponse.selectFirst("div.c-etf-performances__table table");
    }
    JsonObject fond = new JsonObject();
    JsonObject category = new JsonObject();
    JsonObject percentile = new JsonObject();
    JsonObject[] seriesByRow = isFonds ? new JsonObject[] {fond, category, percentile} : new JsonObject[] {fond, category};

    if (table != null) {
      Elements rows = table.select("tr");
      List<String> header = new ArrayList<>();
      if (!rows.isEmpty()) {
        for (Element th : rows.first().select("th")) {
          header.add(th.text().trim());
        }
      }
      boolean periodsAsColumns = header.stream().anyMatch(label -> toPeriod(label) != null);

      for (Element row : rows.subList(Math.min(1, rows.size()), rows.size())) {
        Element rowLabel = row.selectFirst("th");
        Elements cells = row.select("td");
        if (periodsAsColumns) {
          //old layout: a row per series, its cells aligned with the non empty header labels
          int rowIndex = rows.indexOf(row) - 1;
          if (rowIndex >= seriesByRow.length) {
            continue;
          }
          List<String> periods = new ArrayList<>();
          for (String label : header) {
            if (!label.isEmpty()) {
              periods.add(label);
            }
          }
          for (int j = 0; j < cells.size() && j < periods.size(); j++) {
            addValue(seriesByRow[rowIndex], toPeriod(periods.get(j)), cells.get(j).text(), seriesByRow[rowIndex] == percentile);
          }
        } else {
          //current layout: a row per period, a column per series named in the header
          String period = rowLabel == null ? null : toPeriod(rowLabel.text());
          if (period == null) {
            continue;
          }
          int firstValueColumn = header.size() - cells.size();
          for (int j = 0; j < cells.size(); j++) {
            int column = firstValueColumn + j;
            JsonObject series = column >= 0 && column < header.size() ? toSeries(header.get(column), fond, category, percentile) : null;
            if (series != null) {
              addValue(series, period, cells.get(j).text(), series == percentile);
            }
          }
        }
      }
    }

    JsonObject values = new JsonObject();
    values.add("fond", fond);
    values.add("category", category);
    values.add("percentile", percentile);
    result.add("values", values);
  }

  /** Maps a Boursorama period label ("1er JANV.", "3 ANS"...) to its key in {@link #indexes}, or null when not used. */
  static String toPeriod(String label) {
    String normalized = label.toLowerCase(Locale.FRENCH).replace(" ", "").replace(" ", "").replace("janv.", "janvier");
    return Arrays.asList(indexes).contains(normalized) ? normalized : null;
  }

  private static JsonObject toSeries(String label, JsonObject fond, JsonObject category, JsonObject percentile) {
    String normalized = label.toUpperCase(Locale.FRENCH);
    if (normalized.startsWith("FOND")) {
      return fond;
    } else if (normalized.startsWith("CAT")) {
      return category;
    } else if (normalized.startsWith("RANG")) {
      return percentile;
    }
    return null;
  }

  private static void addValue(JsonObject series, String period, String text, boolean asInteger) {
    String value = text.replace("%", "").replace(",", ".").replace(" ", "").replace(" ", "").trim();
    if (period == null || value.isEmpty() || value.equals("-")) {
      return;
    }
    if (asInteger) {
      series.addProperty(period, (int) Math.round(Double.parseDouble(value)));
    } else {
      series.addProperty(period, Double.valueOf(value));
    }
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
    return Utils.getBoursoResponse("https://www.boursorama.com/recherche/?query="+id+"&searchId=");

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
    if (costsConditions==null) {
      return;
    }
    //the display needs the three fees: "ND" unless the row is found
    result.addProperty("entree", "ND");
    result.addProperty("sortie", "ND");
    result.addProperty("courant", "ND");
    //rows are recognised by their label ("Droits d'entrée", "Droits de sortie", "Frais courant"), not their position
    for (Element cost : costsConditions.select("tr")) {
      Elements cells = cost.select("td");
      if (cells.size() < 2) {
        continue;
      }
      String label = cells.first().text().toLowerCase(Locale.FRENCH);
      String value = cells.last().text().trim();
      if (value.isEmpty()) {
        continue;
      }
      if (label.contains("entrée")) {
        result.addProperty("entree", value);
      } else if (label.contains("sortie")) {
        result.addProperty("sortie", value);
      } else if (label.contains("courant")) {
        //"1,07 % 0,6 %": the ongoing charge, followed by the management fee it includes
        if (value.contains(" ") && value.contains("%")) {
          value = value.substring(0, value.indexOf("%") + 1);
        }
        result.addProperty("courant", value);
      }
    }

    Elements parts = costsConditions.select("div.c-list-info").first().select("li");
    int i=1;
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
    result.addProperty(Utils.PRICE_PROPERTY,Double.parseDouble(price.text().replace(" ","").replace(",",".")));

    Element currentcy = facePrice.select("span").last();
    result.addProperty(Utils.CURRENCY_PROPERTY,currentcy.text());

    if (currentcy.text().equals("EUR")) {
      result.addProperty(Utils.PRICE_EUR_PROPERTY,Double.parseDouble(price.text().replace(" ","").replace(",",".")));
    } else {
      Element facePriceEur = doc.select("div.c-faceplate__indicative").first();
      Element priceEur = facePriceEur.selectFirst("span.c-faceplate__indicative-value");
      result.addProperty(Utils.PRICE_EUR_PROPERTY,Double.parseDouble(priceEur.text().replace("EUR","").replace(" ","").replace(",",".")));
    }


    Element faceQuotation = doc.select("div.c-faceplate__quotation").get(2);
    String actif = faceQuotation.select("li.c-list-info__item").get(0).text().split("/")[1].replace(" ","");
    try {
      result.addProperty(Utils.ACTIF_PROPERTY, Double.parseDouble(actif.replace(",", ".")));
    } catch (NumberFormatException e) {
      result.addProperty(Utils.ACTIF_PROPERTY, 0d);
    }



  }
  private static void extractGerant(JsonObject result, Document doc) {
    if (doc.selectFirst("div.c-fund-characteristics")==null) {
      return;
    }
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
