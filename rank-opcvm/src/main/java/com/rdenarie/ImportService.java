package com.rdenarie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The quarterly import, run by cron: reads Morningstar, the ECB rates and the Boursorama partner lists, ranks the
 * funds, publishes the JSON files the pages read, then mails the report. A few minutes, no Datastore.
 */
@WebServlet(name = "ImportService", value = "/importService")
public class ImportService extends HttpServlet {

  private static final Logger log = Logger.getLogger(ImportService.class.getName());
  /** Below this share of the previous import's funds, the import is not published: something broke. */
  static final double MIN_RATIO_TO_PREVIOUS_IMPORT = 0.5;
  private static final int IMPORT_CACHE_SECONDS = 86400;
  private static final int HISTORY_CACHE_SECONDS = 3600;
  private static final int INDEX_CACHE_SECONDS = 300;

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    //App Engine strips this header from outside requests: only cron (or "Run now" in the console) can start an import
    if (!"true".equals(request.getHeader("X-Appengine-Cron"))) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "The import is started by cron only");
      return;
    }
    response.setContentType("text/plain; charset=utf-8");
    response.getWriter().println(runImport());
  }

  static String runImport() {
    LocalDate date = LocalDate.now(ZoneId.of("Europe/Paris"));
    try {
      Map<String, String> persoByCategoryId = CategoriesService.getPersoByCategoryId();
      Map<String, String> categoryNames = new HashMap<>();
      List<Fund> universe = MorningstarClient.fetchUniverse(categoryNames);
      if (universe == null) {
        return abort("Morningstar n'a pas renvoyé l'univers complet des fonds.");
      }
      Map<String, Double> rates = EcbRates.fetch();
      if (rates == null) {
        return abort("Les taux de change de la BCE sont indisponibles.");
      }
      BoursoPartners partners = BoursoPartners.fetch();

      ImportBuilder.Result result = ImportBuilder.build(date, universe, categoryNames, persoByCategoryId, rates, partners);
      JsonObject index = CloudStorage.readJson("index.json");
      int previousFunds = previousFunds(index, date.toString());
      if (result.funds.isEmpty() || result.funds.size() < previousFunds * MIN_RATIO_TO_PREVIOUS_IMPORT) {
        return abort("L'import ne garde que " + result.funds.size() + " fonds, contre " + previousFunds
                     + " au précédent : il n'est pas publié. Univers Morningstar : " + universe.size() + " parts, rejets : "
                     + result.rejections);
      }

      CloudStorage.writeJson(ImportJson.importPath(date.toString()), ImportJson.toJson(result), IMPORT_CACHE_SECONDS);
      publishHistory(result);
      //the index last: the pages switch to the new import only once all its files are there
      CloudStorage.writeJson("index.json", ImportJson.addToIndex(index, result), INDEX_CACHE_SECONDS);

      MailService.sendImportReport(result, previousFunds, universe.size(), partners.size());
      return "Import " + date + " published: " + result.funds.size() + " funds, " + result.categories.size() + " categories";
    } catch (Exception e) {
      log.log(Level.SEVERE, "Import failed", e);
      return abort("L'import a échoué : " + e);
    }
  }

  private static void publishHistory(ImportBuilder.Result result) throws IOException {
    List<List<Fund>> shards = new ArrayList<>();
    for (int i = 0; i < ImportJson.HISTORY_SHARDS; i++) {
      shards.add(new ArrayList<>());
    }
    for (Fund fund : result.funds) {
      shards.get(ImportJson.shardOf(fund.isin)).add(fund);
    }
    for (int i = 0; i < ImportJson.HISTORY_SHARDS; i++) {
      JsonObject shard = CloudStorage.readJson(ImportJson.historyPath(i));
      if (shard == null) {
        shard = new JsonObject();
      }
      ImportJson.addToHistory(shard, result, shards.get(i));
      CloudStorage.writeJson(ImportJson.historyPath(i), shard, HISTORY_CACHE_SECONDS);
    }
  }

  /** Funds of the most recent import other than the one of this date (a rerun of the same day replaces it). */
  static int previousFunds(JsonObject index, String date) {
    if (index == null || !index.has("imports")) {
      return 0;
    }
    JsonArray imports = index.getAsJsonArray("imports");
    for (JsonElement entry : imports) {
      if (!entry.getAsJsonObject().get("date").getAsString().equals(date)) {
        return entry.getAsJsonObject().get("funds").getAsInt();
      }
    }
    return 0;
  }

  private static String abort(String reason) {
    log.severe(reason);
    MailService.sendAlert("Rank-opcvm : import non publié", reason
        + "\n\nLe site continue d'afficher l'import précédent. Une fois la cause corrigée, relancer le cron /importService "
        + "depuis la console App Engine (Cron jobs > Run now).");
    return "Import not published: " + reason;
  }
}
