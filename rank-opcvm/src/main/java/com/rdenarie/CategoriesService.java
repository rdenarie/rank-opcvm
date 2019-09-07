package com.rdenarie;



import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "CategoriesService", value = "/categoriesService")
public class CategoriesService  extends HttpServlet {
    private static final Logger log = Logger.getLogger(CategoriesService.class.getName());

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {


        response.setContentType("text/html");
        response.getWriter().println(getIdFonds().toString());
    }

    public static List<String> getIdFonds() {
        List<String> result=new ArrayList<>();

        JsonObject categories = getCategories();

        JsonArray personalCategories = categories.getAsJsonArray("personalCategories");
        int currentPersonalCategory = 1;
        for (JsonElement personalCategory : personalCategories) {



            log.fine("Treat personal category "+personalCategory.getAsJsonObject().get("categoryName").getAsString() + "("+currentPersonalCategory+"/"+personalCategories.size()+")");

            JsonArray categoriesMs = personalCategory.getAsJsonObject().getAsJsonArray("categoriesMs");
            int currentMsCategory = 1;
            //if  (currentPersonalCategory>2) break;
            for (JsonElement categoryMs : categoriesMs) {
                //if (currentMsCategory>2) break;
                log.fine("\tTreat ms category " + categoryMs.getAsJsonObject().get("categoryName").getAsString() + "(" + currentMsCategory + "/" + categoriesMs.size() + ")");
                String categorySearchCode = categoryMs.getAsJsonObject().get("categorySearchCode").getAsString();
                result.addAll(getIdFondsByCategorySearchCodeFromMS(categorySearchCode,personalCategory.getAsJsonObject().get("categoryName").getAsString()));
                currentMsCategory++;
            }

            currentPersonalCategory++;
        }
        return result;


    }

    public static List<String> getIdFondsByMsCategory(JsonElement categoryMs, JsonElement personalCategory) {
        List<String> result=new ArrayList<>();

        log.fine("\tTreat ms category " + categoryMs.getAsJsonObject().get("categoryName").getAsString());
        String categorySearchCode = categoryMs.getAsJsonObject().get("categorySearchCode").getAsString();
        result.addAll(getIdFondsByCategorySearchCodeFromMS(categorySearchCode,personalCategory.getAsJsonObject().get("categoryName").getAsString()));

        return result;

    }

    private static List<String> getIdFondsByCategorySearchCodeFromBourso(String categorySearchCode, String personalCategoryName) {
        List<String> result=new ArrayList<>();
        String url = "/bourse/opcvm/recherche/?fundSearch%5Bclasse%5D=all&fundSearch%5Bcritgen%5D=morningstar&fundSearch%5Bsouscritgen%5D="+categorySearchCode;
        Element nextPage=null;
        do {
            String boursoResponse = getCategoriesBoursoResponse(url);

            if (boursoResponse != null) {
                Document doc = Jsoup.parse(boursoResponse);
                Elements links = doc.selectFirst("div.c-tabs__content.is-active").select("a[href^=/bourse/opcvm/cours/]");
                //System.out.println(doc.selectFirst("div.c-tabs__content.is-active").html());
                for (Element link : links) {
                    String[] splitted = link.attr("href").split("/");
                    result.add(splitted[splitted.length - 1]+"#"+personalCategoryName);
                }
                Element pageNumberElement = doc.selectFirst("span.c-pagination__content.is-active");
                if (pageNumberElement==null) {
                    nextPage=null;
                } else {
                    int pageNumber = new Integer(pageNumberElement.text()).intValue();
                    pageNumber++;
                    nextPage = doc.selectFirst("a[href^=/bourse/opcvm/recherche/page-" + pageNumber + "]");
                    if (nextPage != null) {
                        System.out.println("Goto Page " + pageNumber);
                        url = nextPage.attr("href");
                    }
                }
            }
        } while (nextPage!=null);

        return result;


    }

    private static List<String> getIdFondsByCategorySearchCodeFromMS(String categorySearchCode, String personalCategoryName) {
        List<String> result=new ArrayList<>();


        String msResponse;
        int pageSize=200;
        int offset=0;
        int total=0;
        int currentPage=1;
        do {

            String url = "http://lt.morningstar.com/api/rest.svc/klr5zyak8x/security/screener?page="+currentPage+"&pageSize="+pageSize+"&sortOrder=LegalName%20asc&outputType=json&version=1&languageId=fr-FR&currencyId=EUR&universeIds=FOFRA%24%24ALL&securityDataPoints=SecId%7CName%7CPriceCurrency%7CTenforeId%7CLegalName%7CClosePrice%7CYield_M12%7COngoingCharge%7CCategoryName%7CAnalystRatingScale%7CStarRatingM255%7CSustainabilityRank%7CGBRReturnD1%7CGBRReturnW1%7CGBRReturnM1%7CGBRReturnM3%7CGBRReturnM6%7CGBRReturnM0%7CGBRReturnM12%7CGBRReturnM36%7CGBRReturnM60%7CGBRReturnM120%7CMaxFrontEndLoad%7COngoingCostActual%7CPerformanceFeeActual%7CTransactionFeeActual%7CMaximumExitCostAcquired%7CFeeLevel%7CManagerTenure%7CMaxDeferredLoad%7CInitialPurchase%7CFundTNAV%7CEquityStyleBox%7CBondStyleBox%7CAverageMarketCapital%7CAverageCreditQualityCode%7CEffectiveDuration%7CMorningstarRiskM255%7CAlphaM36%7CBetaM36%7CR2M36%7CStandardDeviationM36%7CSharpeM36%7CInvestorTypeRetail%7CInvestorTypeProfessional%7CInvestorTypeEligibleCounterparty%7CExpertiseBasic%7CExpertiseAdvanced%7CExpertiseInformed%7CReturnProfilePreservation%7CReturnProfileGrowth%7CReturnProfileIncome%7CReturnProfileHedging%7CReturnProfileOther%7CTrackRecordExtension&filters=CategoryId%3AIN%3A"+categorySearchCode+"&term=&subUniverseId=";
            msResponse = Utils.getMSResponse(url);
            if (msResponse != null) {
                JsonObject jsonObject = (new JsonParser()).parse(msResponse).getAsJsonObject();
                total = jsonObject.get("total").getAsInt();
                JsonArray rows = jsonObject.getAsJsonArray("rows");
                log.fine("Total rows=" + rows.size());

                for (JsonElement element : rows) {
                    JsonElement property = element.getAsJsonObject().get("TenforeId");
                    if (property != null) {
                        String id = element.getAsJsonObject().get("TenforeId").getAsString();
                        id = id.substring(id.lastIndexOf(".") + 1);
                        result.add(id + "#" + personalCategoryName);
                    }
                }
                offset+=pageSize;
                currentPage++;
            }
        } while (offset<total && msResponse!=null);
        log.fine("Category : "+categorySearchCode+" : found "+result.size()+" funds to store.");
        return result;


    }


    public static JsonObject getCategories() {
        JsonObject result = new JsonObject();
        JsonArray persoCategories = new JsonArray();

        Map<String,String> categoriesMsCodes = getCategoriesMsCodes();

        JsonObject categoryAfriqueEtMoyenOrient = new JsonObject();
        categoryAfriqueEtMoyenOrient.addProperty("categoryName","Afrique et M.O.");
        JsonArray categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions Afrique",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Afrique & Moyen-Orient",categoriesMsCodes));
        categoryAfriqueEtMoyenOrient.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryAfriqueEtMoyenOrient);

        JsonObject categoryAllocationDiversifiees = new JsonObject();
        categoryAllocationDiversifiees.addProperty("categoryName","Allocations Diversifiées");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Alt - Global Macro",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Flexible - International",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Flexible",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Agressive",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Agressive - International",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation USD Flexible",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Modérée",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation USD Agressive",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Modérée - International",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation EUR Prudente",categoriesMsCodes));

        //tocheck next import
        categoriesMs.add(createMsCategory("Allocation Marchés Emergents",categoriesMsCodes));

        categoriesMs.add(createMsCategory("Allocation EUR Prudente - International",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation USD Modérée",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation USD Prudente",categoriesMsCodes));
        categoryAllocationDiversifiees.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryAllocationDiversifiees);


        JsonObject categoryAmeriqueDuNord = new JsonObject();
        categoryAmeriqueDuNord.addProperty("categoryName","Amérique du Nord");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions Etats-Unis Gdes Cap. Croissance",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Etats-Unis Flex Cap",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Etats-Unis Gdes Cap. Mixte",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Etats-Unis Moyennes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Etats-Unis Petites Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Etats-Unis Gdes Cap. \"Value\"",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Canada",categoriesMsCodes));
        categoryAmeriqueDuNord.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryAmeriqueDuNord);

        JsonObject categoryAsieEtPacifique = new JsonObject();
        categoryAsieEtPacifique.addProperty("categoryName","Asie et Pacifique");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions Japon Petites & Moy. Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Chine",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Grande Chine",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Japon Grandes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Inde",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Asie-Pacifique hors Japon",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Asie hors Japon",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Asia ex-Japan Small/Mid-Cap Equity",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Asie-Pacifique avec Japon",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Thaïlande",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Taiwan Grandes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Corée",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Hong Kong",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Australie & Nouvelle-Zélande",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Singapour",categoriesMsCodes));
        categoryAsieEtPacifique.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryAsieEtPacifique);

        JsonObject categoryEuropeEtZoneEuro = new JsonObject();
        categoryEuropeEtZoneEuro.addProperty("categoryName","Europe et Zone Euro");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions Zone Euro Moyennes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Zone Euro Petites Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Petites Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Flex Cap",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe hors UK Petites & Moy. Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Moyennes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe du Nord",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe du Nord Petites & Moy. Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Zone Euro Flex Cap",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Zone Euro Grandes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Gdes Cap. Croissance",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe hors UK Gdes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Gdes Cap. Mixte",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Gdes Cap. \"Value\"",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Rendement",categoriesMsCodes));
        categoryEuropeEtZoneEuro.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryEuropeEtZoneEuro);

        JsonObject categoryInternationales = new JsonObject();
        categoryInternationales.addProperty("categoryName","Internationales");
        categoriesMs = new JsonArray();
        //categoriesMs.add(createMsCategory("Actions Secteur International Autres",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions International Gdes Cap. Croissance",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions International Gdes Cap. Mixte",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions International Petites Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions International Flex-Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions International Gdes Cap. \"Value\"",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions International Rendement",categoriesMsCodes));
        categoryInternationales.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryInternationales);

        JsonObject categoryObligataires = new JsonObject();
        categoryObligataires.addProperty("categoryName","Obligataires");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Obligations EUR Flexibles",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Obligations EUR Haut Rendement",categoriesMsCodes));
        //categoriesMs.add(createMsCategory("Secteur Oblig Autres",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Obligations Marchés Emergents Dominante EUR",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Obligations USD Haut Rendement",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Obligations EUR Long Terme",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Obligations Asie Haut Rendement",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Obligations International Dominante EUR",categoriesMsCodes));
        categoryObligataires.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryObligataires);

        JsonObject categoryPaysEmergents = new JsonObject();
        categoryPaysEmergents.addProperty("categoryName","Pays Emergents");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions Europe Emergente",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Marchés Frontiéres",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Vietnam",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Marchés Emergents",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Russie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions BRIC",categoriesMsCodes));
//        categoriesMs.add(createMsCategory("Actions Secteur Marchés Emergents Autres",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Europe Emergente hors Russie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Amérique Latine",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Allocation Marchés Emergents",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Brésil",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions ASEAN",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Indonésie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Turquie",categoriesMsCodes));
        categoryPaysEmergents.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryPaysEmergents);

        JsonObject categoryPaysEuropeens = new JsonObject();
        categoryPaysEuropeens.addProperty("categoryName","Pays Européens");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions France Petites & Moy. Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Royaume-Uni Petites Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Allemagne Petites & Moy. Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Suisse Petites & Moy. Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Allemagne Gdes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions France Grandes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Royaume Uni Flex Cap",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Suisse Grandes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Belgique",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Royaume-Uni Moyennes Cap.",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Norvège",categoriesMsCodes));

        //vide, mais n'existe pas/plus sur MS
        categoriesMs.add(createMsCategory("Actions Pays-Bas",categoriesMsCodes));

        categoriesMs.add(createMsCategory("Actions Italie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Royaume-Uni Gdes Cap. Mixte",categoriesMsCodes));

        //vide, mais n'existe pas/plus sur MS
        categoriesMs.add(createMsCategory("Actions Royaume-Uni Gdes Cap. Croissance",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Espagne",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Action Royaume Uni Rendement",categoriesMsCodes));
        categoryPaysEuropeens.add("categoriesMs",categoriesMs);
        persoCategories.add(categoryPaysEuropeens);

        JsonObject categorySectoriels = new JsonObject();
        categorySectoriels.addProperty("categoryName","Sectoriels");
        categoriesMs = new JsonArray();
        categoriesMs.add(createMsCategory("Actions Secteur Technologies",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Biens Conso. & Services",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Autres",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Biotechnologie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Ecologie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Matériaux & Industrie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Finance",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Santé",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Eau",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Infrastructures",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Immobilier - Indirect Europe",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Métaux Précieux",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Immobilier - Indirect Autres",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Immobilier - Indirect Zone Euro",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Communication",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Energies Alternatives",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Ressources Naturelles",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Immobilier - Indirect International",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Services Publics",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Immobilier - Indirect Amérique du Nord",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Agriculture",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Immobilier - Indirect Asie",categoriesMsCodes));
        categoriesMs.add(createMsCategory("Actions Secteur Energie",categoriesMsCodes));
        categorySectoriels.add("categoriesMs",categoriesMs);
        persoCategories.add(categorySectoriels);

        result.add("personalCategories",persoCategories);

        return result;

        //TODO a rajouter Actions Secteur Autres


    }

    private static Map<String, String> getCategoriesMsCodes() {
        Map<String,String> result = new HashMap();
        JsonParser parser = new JsonParser();
        JsonObject object = parser.parse(
                "{\"all\":{\"all\":{\"all\":\"Toutes catégories\",\"0\":\"Actions Afrique\",\"1\":\"Actions Amérique du Nord\",\"2\":\"Actions Asie/Pacifique\",\"3\":\"Actions Autres\",\"4\":\"Actions Europe\",\"5\":\"Actions Internationales\",\"6\":\"Actions Pays Emergents\",\"7\":\"Actions Secteurs\",\"8\":\"Alternatifs\",\"9\":\"Autres\",\"10\":\"Mixtes Autres\",\"11\":\"Mixtes Euro\",\"12\":\"Mixtes USD\",\"13\":\"Monétaire Autres Devises\",\"14\":\"Monétaire Euro\",\"15\":\"Monétaire Euro Dyn.\",\"16\":\"Obligations Autres\",\"17\":\"Obligations Convertibles\",\"18\":\"Obligations Euro\",\"19\":\"Obligations US\"},\"equity\":{\"1\":\"Actions Afrique\",\"2\":\"Actions Amérique du Nord\",\"3\":\"Actions Asie/Pacifique\",\"4\":\"Actions Autres\",\"5\":\"Actions Europe\",\"6\":\"Actions Internationales\",\"7\":\"Actions Pays Emergents\",\"8\":\"Actions Secteurs\"},\"monetary\":{\"9\":\"Monétaire Autres Devises\",\"10\":\"Monétaire Euro\",\"11\":\"Monétaire Euro Dyn.\"},\"bond\":{\"12\":\"Obligations Autres\",\"13\":\"Obligations Convertibles\",\"14\":\"Obligations Euro\",\"15\":\"Obligations US\"},\"mix\":{\"16\":\"Mixtes Autres\",\"17\":\"Mixtes Euro\",\"18\":\"Mixtes USD\"},\"alternative\":{\"20\":\"Alternatifs\"},\"other\":{\"19\":\"Autres\"}},\"amf\":{\"all\":{\"all\":\"Toutes catégories\",\"LC00000036\":\"Actions de la zone Euro\",\"LC00000085\":\"Actions des pays de l'Union Européenne\",\"LC00000035\":\"Actions franaises\",\"LC00000037\":\"Actions internationales\",\"LC00000042\":\"Diversifié\",\"LC00000267\":\"Fonds etrangers\",\"LC00000142\":\"Fonds à formule\",\"LC00002067\":\"Monétaire\",\"LC00002042\":\"Monétaire Court Terme\",\"LC00000143\":\"OPCVM de fonds alternatifs\",\"LC00000038\":\"Obligations en Euro\",\"LC00000039\":\"Obligations internationales\",\"LC00000264\":\"Unclassified\"},\"equity\":{\"LC00000036\":\"Actions de la zone Euro\",\"LC00000085\":\"Actions des pays de l'Union Européenne\",\"LC00000035\":\"Actions françaises\",\"LC00000037\":\"Actions internationales\"},\"other\":{\"LC00000042\":\"Diversifié\",\"LC00000267\":\"Fonds etrangers\",\"LC00000142\":\"Fonds à formule\",\"LC00002067\":\"Monétaire\",\"LC00002042\":\"Monétaire Court Terme\",\"LC00000143\":\"OPCVM de fonds alternatifs\",\"LC00000264\":\"Unclassified\"},\"bond\":{\"LC00000038\":\"Obligations en Euro\",\"LC00000039\":\"Obligations internationales\"}},\"morningstar\":{\"all\":{\"all\":\"Toutes catégories\",\"EUCA000846\":\"Action Royaume Uni Rendement \",\"EUCA000669\":\"Actions ASEAN\",\"EUCA000697\":\"Actions Afrique\",\"EUCA000698\":\"Actions Afrique & Moyen-Orient\",\"EUCA000712\":\"Actions Afrique & Moyen-Orient Autres\",\"EUCA000715\":\"Actions Afrique du Sud & Namibie\",\"EUCA000638\":\"Actions Allemagne Gdes Cap.\",\"EUCA000639\":\"Actions Allemagne Petites & Moy. Cap.\",\"EUCA000524\":\"Actions Amérique Latine\",\"EUCA000711\":\"Actions Amériques Autres\",\"EUCA000779\":\"Actions Asie hors Japon\",\"EUCA000883\":\"Actions Asie hors Japon - Devises Couvertes\",\"EUCA000884\":\"Actions Asie-Pacifique - Devises Couvertes\",\"EUCA000506\":\"Actions Asie-Pacifique Autres\",\"EUCA000502\":\"Actions Asie-Pacifique avec Japon\",\"EUCA000501\":\"Actions Asie-Pacifique hors Japon\",\"EUCA000503\":\"Actions Australie & Nouvelle-Zélande\",\"EUCA000630\":\"Actions Autriche\",\"EUCA000700\":\"Actions BRIC\",\"EUCA000631\":\"Actions Belgique\",\"EUCA000699\":\"Actions Brésil\",\"EUCA000632\":\"Actions Canada\",\"EUCA000673\":\"Actions Chariah Islamique Autres\",\"EUCA000504\":\"Actions Chine\",\"EUCA000896\":\"Actions Chine - A Shares\",\"EUCA000523\":\"Actions Corée\",\"EUCA000505\":\"Actions Danemark\",\"EUCA000643\":\"Actions Espagne\",\"EUCA000849\":\"Actions Etats-Unis - Devises Couvertes\",\"EUCA000529\":\"Actions Etats-Unis Flex Cap\",\"EUCA000528\":\"Actions Etats-Unis Gdes Cap. \\\"Value\\\"\",\"EUCA000527\":\"Actions Etats-Unis Gdes Cap. Croissance\",\"EUCA000526\":\"Actions Etats-Unis Gdes Cap. Mixte\",\"EUCA000853\":\"Actions Etats-Unis Moyennes Cap.\",\"EUCA000530\":\"Actions Etats-Unis Petites Cap.\",\"EUCA000885\":\"Actions Europe - Devises Couvertes\",\"EUCA000634\":\"Actions Europe Autres\",\"EUCA000508\":\"Actions Europe Emergente\",\"EUCA000633\":\"Actions Europe Emergente hors Russie\",\"EUCA000514\":\"Actions Europe Flex Cap\",\"EUCA000513\":\"Actions Europe Gdes Cap. \\\"Value\\\"\",\"EUCA000512\":\"Actions Europe Gdes Cap. Croissance\",\"EUCA000511\":\"Actions Europe Gdes Cap. Mixte\",\"EUCA000850\":\"Actions Europe Moyennes Cap.\",\"EUCA000515\":\"Actions Europe Petites Cap.\",\"EUCA000875\":\"Actions Europe Rendement\",\"EUCA000525\":\"Actions Europe du Nord\",\"EUCA000920\":\"Actions Europe du Nord Petites & Moy. Cap.\",\"EUCA000509\":\"Actions Europe hors UK Gdes Cap.\",\"EUCA000510\":\"Actions Europe hors UK Petites & Moy. Cap.\",\"EUCA000636\":\"Actions France Grandes Cap.\",\"EUCA000637\":\"Actions France Petites & Moy. Cap.\",\"EUCA000500\":\"Actions Grande Chine\",\"EUCA000701\":\"Actions Grèce\",\"EUCA000519\":\"Actions Hong Kong\",\"EUCA000520\":\"Actions Inde\",\"EUCA000780\":\"Actions Indonésie\",\"EUCA000840\":\"Actions International Chariah Islamique\",\"EUCA000558\":\"Actions International Flex-Cap.\",\"EUCA000557\":\"Actions International Gdes Cap. \\\"Value\\\"\",\"EUCA000556\":\"Actions International Gdes Cap. Croissance\",\"EUCA000555\":\"Actions International Gdes Cap. Mixte\",\"EUCA000836\":\"Actions International Petites Cap.\",\"EUCA000876\":\"Actions International Rendement\",\"EUCA000848\":\"Actions Internationnales - Devises Couvertes\",\"EUCA000640\":\"Actions Italie\",\"EUCA000521\":\"Actions Japon Grandes Cap.\",\"EUCA000522\":\"Actions Japon Petites & Moy. Cap.\",\"EUCA000887\":\"Actions Japonnaise - Devises Courvertes\",\"EUCA000672\":\"Actions Malaisie\",\"EUCA000507\":\"Actions Marchés Emergents\",\"EUCA000886\":\"Actions Marchés Emergents - Devises Couvertes\",\"EUCA000845\":\"Actions Marchés Frontiéres\",\"EUCA000531\":\"Actions Norvège\",\"EUCA000641\":\"Actions Pays-Bas\",\"EUCA000702\":\"Actions Pologne\",\"EUCA000553\":\"Actions Royaume Uni Flex Cap\",\"EUCA000552\":\"Actions Royaume-Uni Gdes Cap. \\\"Value\\\"\",\"EUCA000551\":\"Actions Royaume-Uni Gdes Cap. Croissance\",\"EUCA000550\":\"Actions Royaume-Uni Gdes Cap. Mixte\",\"EUCA000852\":\"Actions Royaume-Uni Moyennes Cap.\",\"EUCA000554\":\"Actions Royaume-Uni Petites Cap.\",\"EUCA000642\":\"Actions Russie\",\"EUCA000827\":\"Actions Secteur Agriculture\",\"EUCA000539\":\"Actions Secteur Autres\",\"EUCA000534\":\"Actions Secteur Biens Conso. & Services\",\"EUCA000532\":\"Actions Secteur Biotechnologie\",\"EUCA000533\":\"Actions Secteur Communication\",\"EUCA000828\":\"Actions Secteur Eau\",\"EUCA000706\":\"Actions Secteur Ecologie\",\"EUCA000535\":\"Actions Secteur Energie\",\"EUCA000705\":\"Actions Secteur Energies Alternatives\",\"EUCA000536\":\"Actions Secteur Finance\",\"EUCA000707\":\"Actions Secteur Infrastructures\",\"EUCA000538\":\"Actions Secteur Matériaux & Industrie\",\"EUCA000646\":\"Actions Secteur Métaux Précieux\",\"EUCA000708\":\"Actions Secteur Non-Coté\",\"EUCA000704\":\"Actions Secteur Ressources Naturelles\",\"EUCA000537\":\"Actions Secteur Santé\",\"EUCA000543\":\"Actions Secteur Services Publics\",\"EUCA000542\":\"Actions Secteur Technologies\",\"EUCA000544\":\"Actions Singapour\",\"EUCA000548\":\"Actions Suisse Grandes Cap.\",\"EUCA000644\":\"Actions Suisse Petites & Moy. Cap.\",\"EUCA000545\":\"Actions Suède Grandes Cap.\",\"EUCA000546\":\"Actions Suède Petites & Moyennes Cap.\",\"EUCA000670\":\"Actions Taiwan Grandes Cap. \",\"EUCA000783\":\"Actions Thaïlande\",\"EUCA000703\":\"Actions Turquie\",\"EUCA000829\":\"Actions Vietnam\",\"EUCA000517\":\"Actions Zone Euro Flex Cap\",\"EUCA000516\":\"Actions Zone Euro Grandes Cap.\",\"EUCA000851\":\"Actions Zone Euro Moyennes Cap.\",\"EUCA000518\":\"Actions Zone Euro Petites Cap.\",\"EUCA000824\":\"Actions EMEA\",\"EUCA000559\":\"Allocation Asie\",\"EUCA000743\":\"Allocation Autres\",\"EUCA000618\":\"Allocation CHF Agressive\",\"EUCA000738\":\"Allocation CHF Modérée\",\"EUCA000619\":\"Allocation CHF Prudente\",\"EUCA000862\":\"Allocation EUR Agressive\",\"EUCA000563\":\"Allocation EUR Agressive - International\",\"EUCA000864\":\"Allocation EUR Flexible\",\"EUCA000739\":\"Allocation EUR Flexible - International\",\"EUCA000865\":\"Allocation EUR Modérée\",\"EUCA000565\":\"Allocation EUR Modérée - International\",\"EUCA000863\":\"Allocation EUR Prudente\",\"EUCA000564\":\"Allocation EUR Prudente - International\",\"EUCA000620\":\"Allocation GBP Agressive\",\"EUCA000740\":\"Allocation GBP Flexible\",\"EUCA000796\":\"Allocation GBP Modérée\",\"EUCA000621\":\"Allocation GBP Prudente\",\"EUCA000741\":\"Allocation Marchés Emergents\",\"EUCA000566\":\"Allocation NOK\",\"EUCA000616\":\"Allocation SEK Agressive\",\"EUCA000745\":\"Allocation SEK Flexible\",\"EUCA000898\":\"Allocation SEK Modérée\",\"EUCA000617\":\"Allocation SEK Prudente\",\"EUCA000693\":\"Allocation USD Agressive\",\"EUCA000746\":\"Allocation USD Flexible\",\"EUCA000694\":\"Allocation USD Modérée\",\"EUCA000695\":\"Allocation USD Prudente\",\"EUCA000798\":\"Alt - Arbitrage Dette\",\"EUCA000799\":\"Alt - Arbitrages Diversifiés \",\"EUCA000881\":\"Alt - Autres\",\"EUCA000797\":\"Alt - Devises\",\"EUCA000800\":\"Alt - Event Driven\",\"EUCA000801\":\"Alt - Fonds de Fonds Alternatifs - Actions\",\"EUCA000803\":\"Alt - Fonds de Fonds Alternatifs - Autres\",\"EUCA000802\":\"Alt - Fonds de Fonds Alternatifs - Multistratégies\",\"EUCA000804\":\"Alt - Global Macro\",\"EUCA000880\":\"Alt - Long/Short Actions - Autres\",\"EUCA000807\":\"Alt - Long/Short Actions - Europe\",\"EUCA000808\":\"Alt - Long/Short Actions - International\",\"EUCA000806\":\"Alt - Long/Short Actions - Marchés Emergents\",\"EUCA000809\":\"Alt - Long/Short Actions - UK\",\"EUCA000810\":\"Alt - Long/Short Actions - US\",\"EUCA000805\":\"Alt - Long/Short Obligations\",\"EUCA000811\":\"Alt - Market Neutral - Actions\",\"EUCA000812\":\"Alt - Multistratégies\",\"EUCA000813\":\"Alt - Systematic Futures\",\"EUCA000814\":\"Alt - Volatilité\",\"EUCA000907\":\"Asia ex-Japan Small/Mid-Cap Equity\",\"EUCA000918\":\"Asia-Pacific ex-Japan Equity Income\",\"EUCA000999\":\"Autres\",\"HSTG000009\":\"Convertible Arbitrage\",\"EUCA000627\":\"Convertibles Asie/Japon\",\"EUCA000751\":\"Convertibles Autres\",\"EUCA000752\":\"Convertibles Couvertes Autres Devises\",\"EUCA000748\":\"Convertibles Europe\",\"EUCA000749\":\"Convertibles International\",\"EUCA000821\":\"Convertibles International Couvertes CHF\",\"EUCA000822\":\"Convertibles International Couvertes GBP\",\"EUCA000823\":\"Convertibles International Couvertes USD\",\"EUCA000750\":\"Convertibles International Couvertes en EUR\",\"EUCA000911\":\"DKK Domestic Bond\",\"HSTG000011\":\"Debt Arbitrage\",\"HSTG000004\":\"Europe Long/Short Equity\",\"EUCA000611\":\"Fonds Garantis\",\"EUCA000615\":\"Fonds à Capital Protégé\",\"EUCA000867\":\"Fonds à horizon 2011-2015\",\"EUCA000868\":\"Fonds à horizon 2016-2020\",\"EUCA000869\":\"Fonds à horizon 2021-2025\",\"EUCA000870\":\"Fonds à horizon 2026-2030\",\"EUCA000871\":\"Fonds à horizon 2031-2035\",\"EUCA000872\":\"Fonds à horizon 2036-2040\",\"EUCA000873\":\"Fonds à horizon 2041-2046\",\"EUCA000874\":\"Fonds à horizon 2046+\",\"HSTG000023\":\"Fund of  Funds - Macro/Systematic\",\"HSTG000020\":\"Fund of Funds - Debt\",\"HSTG000019\":\"Fund of Funds - Equity\",\"HSTG000021\":\"Fund of Funds - Multistrategy\",\"HSTG000018\":\"Fund of Funds - Relative Value\",\"EUCA000916\":\"GBP Moderately Adventurous Allocation\",\"EUCA000917\":\"GBP Moderately Cautious Allocation\",\"EUCA000910\":\"Global Emerging Markets Small/Mid-Cap Equity\",\"HSTG000013\":\"Global Macro\",\"EUCA000919\":\"Greater China Allocation\",\"EUCA000793\":\"Immobilier - Direct Europe\",\"EUCA000794\":\"Immobilier - Direct International\",\"EUCA000781\":\"Immobilier - Indirect Amérique du Nord\",\"EUCA000687\":\"Immobilier - Indirect Asie\",\"EUCA000825\":\"Immobilier - Indirect Autres\",\"EUCA000688\":\"Immobilier - Indirect Europe\",\"EUCA000541\":\"Immobilier - Indirect International\",\"EUCA000847\":\"Immobilier - Indirect Zone Euro\",\"EUCA000915\":\"Japan Flex-Cap Equity\",\"EUCA000612\":\"Long-Short\",\"EUCA000784\":\"Matières Premières - Agriculture\",\"EUCA000789\":\"Matières Premières - Bétail\",\"EUCA000787\":\"Matières Premières - Céréales\",\"EUCA000785\":\"Matières Premières - Divers\",\"EUCA000786\":\"Matières Premières - Energie\",\"EUCA000788\":\"Matières Premières - Industrie & Métaux\",\"EUCA000791\":\"Matières Premières - Métaux Précieux\",\"EUCA000792\":\"Matières Premières - Softs\",\"EUCA000858\":\"Monétaires  Court Terme\",\"EUCA000833\":\"Monétaires Autres Devises\",\"EUCA000575\":\"Monétaires CHF\",\"EUCA000859\":\"Monétaires Court Terme Couverte en EUR\",\"EUCA000861\":\"Monétaires Court Terme Couverte en USD\",\"EUCA000591\":\"Monétaires EUR\",\"EUCA000830\":\"Monétaires EUR Court Terme\",\"EUCA000608\":\"Monétaires GBP\",\"EUCA000831\":\"Monétaires GBP Court Terme\",\"EUCA000602\":\"Monétaires SEK\",\"EUCA000584\":\"Monétaires USD\",\"EUCA000832\":\"Monétaires USD Court Terme\",\"EUCA000860\":\"Monétaires USD Court Terme Couverte en GBP\",\"HSTG000016\":\"Multistrategy\",\"EUCA000908\":\"NOK Aggressive Allocation\",\"EUCA000913\":\"NOK High Yield Bond\",\"EUCA000570\":\"Obligations Asie\",\"EUCA000897\":\"Obligations Asie - Devise Locale\",\"EUCA000877\":\"Obligations Asie Haut Rendement\",\"EUCA000771\":\"Obligations Autres\",\"EUCA000770\":\"Obligations Autres Devises Indexées sur l'Inflation\",\"EUCA000572\":\"Obligations CAD\",\"EUCA000573\":\"Obligations CHF\",\"EUCA000747\":\"Obligations CHF Court Terme\",\"EUCA000576\":\"Obligations DKK\",\"EUCA000753\":\"Obligations DKK Long Terme\",\"EUCA000587\":\"Obligations EUR Diversifiées\",\"EUCA000593\":\"Obligations EUR Diversifiées Court Terme\",\"EUCA000690\":\"Obligations EUR Emprunts Privés\",\"EUCA000819\":\"Obligations EUR Emprunts Privés Court Terme\",\"EUCA000589\":\"Obligations EUR Emprunts d'Etat\",\"EUCA000837\":\"Obligations EUR Emprunts d'Etat Court Terme\",\"EUCA000754\":\"Obligations EUR Flexibles\",\"EUCA000590\":\"Obligations EUR Haut Rendement\",\"EUCA000625\":\"Obligations EUR Indexées sur l'Inflation\",\"EUCA000622\":\"Obligations EUR Long Terme\",\"EUCA000592\":\"Obligations EUR Très Court Terme\",\"EUCA000594\":\"Obligations Europe\",\"EUCA000623\":\"Obligations Europe Emergente\",\"EUCA000755\":\"Obligations Europe Haut Rendement\",\"EUCA000604\":\"Obligations GBP Diversifiées\",\"EUCA000756\":\"Obligations GBP Diversifiées Court Terme\",\"EUCA000692\":\"Obligations GBP Emprunts Privés\",\"EUCA000606\":\"Obligations GBP Emprunts d'Etat\",\"EUCA000757\":\"Obligations GBP Flexibles\",\"EUCA000607\":\"Obligations GBP Haut Rendement\",\"EUCA000758\":\"Obligations GBP Indexées sur l'Inflation\",\"EUCA000768\":\"Obligations HDK\",\"EUCA000820\":\"Obligations Haut Rendement-Couvertes Autres Devises\",\"EUCA000759\":\"Obligations International\",\"EUCA000680\":\"Obligations International Chariah Islamique\",\"EUCA000762\":\"Obligations International Couvertes en Autres Devises\",\"EUCA000760\":\"Obligations International Couvertes en CHF\",\"EUCA000624\":\"Obligations International Couvertes en EUR\",\"EUCA000761\":\"Obligations International Couvertes en GBP\",\"EUCA000882\":\"Obligations International Couvertes en NOK\",\"EUCA000763\":\"Obligations International Couvertes en USD\",\"EUCA000574\":\"Obligations International Dominante CHF\",\"EUCA000588\":\"Obligations International Dominante EUR\",\"EUCA000605\":\"Obligations International Dominante GBP\",\"EUCA000766\":\"Obligations International Haut Rendement\",\"EUCA000854\":\"Obligations International Haut Rendement Couvertes en EUR\",\"EUCA000855\":\"Obligations International Haut Rendement Couvertes en GBP\",\"EUCA000731\":\"Obligations International ILS\",\"EUCA000889\":\"Obligations Internationales Emprunts Privés\",\"EUCA000890\":\"Obligations Internationales Emprunts Privés - Couvertes en CHF\",\"EUCA000891\":\"Obligations Internationales Emprunts Privés - Couvertes en EUR\",\"EUCA000892\":\"Obligations Internationales Emprunts Privés - Couvertes en GBP\",\"EUCA000893\":\"Obligations Internationales Emprunts Privés Couvertes en USD\",\"EUCA000899\":\"Obligations Internationales Flexibles\",\"EUCA000900\":\"Obligations Internationales Flexibles Couvertes en CHF\",\"EUCA000901\":\"Obligations Internationales Flexibles Couvertes en EUR\",\"EUCA000903\":\"Obligations Internationales Flexibles Couvertes en GBP\",\"EUCA000902\":\"Obligations Internationales Flexibles Couvertes en USD\",\"EUCA000894\":\"Obligations Internationales Haut Rendement Couvertes en CHF\",\"EUCA000595\":\"Obligations JPY\",\"EUCA000586\":\"Obligations Marchés Emergents\",\"EUCA000765\":\"Obligations Marchés Emergents Devise Locale\",\"EUCA000764\":\"Obligations Marchés Emergents Dominante EUR\",\"EUCA000878\":\"Obligations Marchés Emergents Emprunts Privés\",\"EUCA000879\":\"Obligations Marchés Emergents Emprunts Privés Dominante EUR\",\"EUCA000596\":\"Obligations NOK\",\"EUCA000599\":\"Obligations NOK Court Terme\",\"EUCA000772\":\"Obligations PLN\",\"EUCA000856\":\"Obligations RMB\",\"EUCA000600\":\"Obligations SEK\",\"EUCA000905\":\"Obligations SEK Emprunts Privés\",\"EUCA000580\":\"Obligations USD Diversifiées\",\"EUCA000585\":\"Obligations USD Diversifiées Court Terme\",\"EUCA000691\":\"Obligations USD Emprunts Privés\",\"EUCA000582\":\"Obligations USD Emprunts d'Etat\",\"EUCA000775\":\"Obligations USD Flexibles\",\"EUCA000583\":\"Obligations USD Haut Rendement\",\"EUCA000767\":\"Obligations USD Indexées sur l'Inflation\",\"EUCA000888\":\"Obligations à échéance\",\"EUCA000921\":\"Pacific ex-Japan Equity\",\"EUCA000649\":\"Performance Absolue Euro\",\"EUCA000650\":\"Performance Absolue Non-Euro\",\"EUCA000906\":\"RMB Bond - Onshore\",\"EUCA000904\":\"RMB High Yield Bond\",\"EUCA000857\":\"Swap EONIA PEA\",\"HSTG000012\":\"Systematic Futures\",\"EUCA000816\":\"Trading - Leveraged/Inverse Actions\",\"EUCA000815\":\"Trading - Leveraged/Inverse Matières Premières\",\"EUCA000817\":\"Trading - Leveraged/Inverse Obligations\",\"HSTG000028\":\"Volatility\"},\"other\":{\"EUCA000846\":\"Action Royaume Uni Rendement \",\"EUCA000559\":\"Allocation Asie\",\"EUCA000743\":\"Allocation Autres\",\"EUCA000618\":\"Allocation CHF Agressive\",\"EUCA000738\":\"Allocation CHF Modérée\",\"EUCA000619\":\"Allocation CHF Prudente\",\"EUCA000862\":\"Allocation EUR Agressive\",\"EUCA000563\":\"Allocation EUR Agressive - International\",\"EUCA000864\":\"Allocation EUR Flexible\",\"EUCA000739\":\"Allocation EUR Flexible - International\",\"EUCA000865\":\"Allocation EUR Modérée\",\"EUCA000565\":\"Allocation EUR Modérée - International\",\"EUCA000863\":\"Allocation EUR Prudente\",\"EUCA000564\":\"Allocation EUR Prudente - International\",\"EUCA000620\":\"Allocation GBP Agressive\",\"EUCA000740\":\"Allocation GBP Flexible\",\"EUCA000796\":\"Allocation GBP Modérée\",\"EUCA000621\":\"Allocation GBP Prudente\",\"EUCA000741\":\"Allocation Marchés Emergents\",\"EUCA000566\":\"Allocation NOK\",\"EUCA000616\":\"Allocation SEK Agressive\",\"EUCA000745\":\"Allocation SEK Flexible\",\"EUCA000898\":\"Allocation SEK Modérée\",\"EUCA000617\":\"Allocation SEK Prudente\",\"EUCA000693\":\"Allocation USD Agressive\",\"EUCA000746\":\"Allocation USD Flexible\",\"EUCA000694\":\"Allocation USD Modérée\",\"EUCA000695\":\"Allocation USD Prudente\",\"EUCA000798\":\"Alt - Arbitrage Dette\",\"EUCA000799\":\"Alt - Arbitrages Diversifiés \",\"EUCA000881\":\"Alt - Autres\",\"EUCA000797\":\"Alt - Devises\",\"EUCA000800\":\"Alt - Event Driven\",\"EUCA000803\":\"Alt - Fonds de Fonds Alternatifs - Autres\",\"EUCA000802\":\"Alt - Fonds de Fonds Alternatifs - Multistratégies\",\"EUCA000804\":\"Alt - Global Macro\",\"EUCA000812\":\"Alt - Multistratégies\",\"EUCA000813\":\"Alt - Systematic Futures\",\"EUCA000814\":\"Alt - Volatilité\",\"EUCA000999\":\"Autres\",\"HSTG000009\":\"Convertible Arbitrage\",\"EUCA000627\":\"Convertibles Asie/Japon\",\"EUCA000751\":\"Convertibles Autres\",\"EUCA000752\":\"Convertibles Couvertes Autres Devises\",\"EUCA000748\":\"Convertibles Europe\",\"EUCA000749\":\"Convertibles International\",\"EUCA000821\":\"Convertibles International Couvertes CHF\",\"EUCA000822\":\"Convertibles International Couvertes GBP\",\"EUCA000823\":\"Convertibles International Couvertes USD\",\"EUCA000750\":\"Convertibles International Couvertes en EUR\",\"EUCA000911\":\"DKK Domestic Bond\",\"HSTG000011\":\"Debt Arbitrage\",\"EUCA000611\":\"Fonds Garantis\",\"EUCA000615\":\"Fonds à Capital Protégé\",\"EUCA000867\":\"Fonds à horizon 2011-2015\",\"EUCA000868\":\"Fonds à horizon 2016-2020\",\"EUCA000869\":\"Fonds à horizon 2021-2025\",\"EUCA000870\":\"Fonds à horizon 2026-2030\",\"EUCA000871\":\"Fonds à horizon 2031-2035\",\"EUCA000872\":\"Fonds à horizon 2036-2040\",\"EUCA000873\":\"Fonds à horizon 2041-2046\",\"EUCA000874\":\"Fonds à horizon 2046+\",\"HSTG000023\":\"Fund of  Funds - Macro/Systematic\",\"HSTG000020\":\"Fund of Funds - Debt\",\"HSTG000021\":\"Fund of Funds - Multistrategy\",\"HSTG000018\":\"Fund of Funds - Relative Value\",\"EUCA000916\":\"GBP Moderately Adventurous Allocation\",\"EUCA000917\":\"GBP Moderately Cautious Allocation\",\"HSTG000013\":\"Global Macro\",\"EUCA000919\":\"Greater China Allocation\",\"EUCA000793\":\"Immobilier - Direct Europe\",\"EUCA000794\":\"Immobilier - Direct International\",\"EUCA000781\":\"Immobilier - Indirect Amérique du Nord\",\"EUCA000687\":\"Immobilier - Indirect Asie\",\"EUCA000825\":\"Immobilier - Indirect Autres\",\"EUCA000688\":\"Immobilier - Indirect Europe\",\"EUCA000541\":\"Immobilier - Indirect International\",\"EUCA000847\":\"Immobilier - Indirect Zone Euro\",\"EUCA000612\":\"Long-Short\",\"EUCA000784\":\"Matières Premières - Agriculture\",\"EUCA000789\":\"Matières Premières - Bétail\",\"EUCA000787\":\"Matières Premières - Céréales\",\"EUCA000785\":\"Matières Premières - Divers\",\"EUCA000786\":\"Matières Premières - Energie\",\"EUCA000788\":\"Matières Premières - Industrie & Métaux\",\"EUCA000791\":\"Matières Premières - Métaux Précieux\",\"EUCA000792\":\"Matières Premières - Softs\",\"HSTG000016\":\"Multistrategy\",\"EUCA000908\":\"NOK Aggressive Allocation\",\"EUCA000913\":\"NOK High Yield Bond\",\"EUCA000649\":\"Performance Absolue Euro\",\"EUCA000650\":\"Performance Absolue Non-Euro\",\"EUCA000906\":\"RMB Bond - Onshore\",\"EUCA000904\":\"RMB High Yield Bond\",\"EUCA000857\":\"Swap EONIA PEA\",\"HSTG000012\":\"Systematic Futures\",\"EUCA000815\":\"Trading - Leveraged/Inverse Matières Premières\",\"HSTG000028\":\"Volatility\"},\"equity\":{\"EUCA000669\":\"Actions ASEAN\",\"EUCA000697\":\"Actions Afrique\",\"EUCA000698\":\"Actions Afrique & Moyen-Orient\",\"EUCA000712\":\"Actions Afrique & Moyen-Orient Autres\",\"EUCA000715\":\"Actions Afrique du Sud & Namibie\",\"EUCA000638\":\"Actions Allemagne Gdes Cap.\",\"EUCA000639\":\"Actions Allemagne Petites & Moy. Cap.\",\"EUCA000524\":\"Actions Amérique Latine\",\"EUCA000711\":\"Actions Amériques Autres\",\"EUCA000779\":\"Actions Asie hors Japon\",\"EUCA000883\":\"Actions Asie hors Japon - Devises Couvertes\",\"EUCA000884\":\"Actions Asie-Pacifique - Devises Couvertes\",\"EUCA000506\":\"Actions Asie-Pacifique Autres\",\"EUCA000502\":\"Actions Asie-Pacifique avec Japon\",\"EUCA000501\":\"Actions Asie-Pacifique hors Japon\",\"EUCA000503\":\"Actions Australie & Nouvelle-Zélande\",\"EUCA000630\":\"Actions Autriche\",\"EUCA000700\":\"Actions BRIC\",\"EUCA000631\":\"Actions Belgique\",\"EUCA000699\":\"Actions Brésil\",\"EUCA000632\":\"Actions Canada\",\"EUCA000673\":\"Actions Chariah Islamique Autres\",\"EUCA000504\":\"Actions Chine\",\"EUCA000896\":\"Actions Chine - A Shares\",\"EUCA000523\":\"Actions Corée\",\"EUCA000505\":\"Actions Danemark\",\"EUCA000643\":\"Actions Espagne\",\"EUCA000849\":\"Actions Etats-Unis - Devises Couvertes\",\"EUCA000529\":\"Actions Etats-Unis Flex Cap\",\"EUCA000528\":\"Actions Etats-Unis Gdes Cap. \\\"Value\\\"\",\"EUCA000527\":\"Actions Etats-Unis Gdes Cap. Croissance\",\"EUCA000853\":\"Actions Etats-Unis Moyennes Cap.\",\"EUCA000530\":\"Actions Etats-Unis Petites Cap.\",\"EUCA000885\":\"Actions Europe - Devises Couvertes\",\"EUCA000634\":\"Actions Europe Autres\",\"EUCA000508\":\"Actions Europe Emergente\",\"EUCA000633\":\"Actions Europe Emergente hors Russie\",\"EUCA000514\":\"Actions Europe Flex Cap\",\"EUCA000513\":\"Actions Europe Gdes Cap. \\\"Value\\\"\",\"EUCA000512\":\"Actions Europe Gdes Cap. Croissance\",\"EUCA000850\":\"Actions Europe Moyennes Cap.\",\"EUCA000515\":\"Actions Europe Petites Cap.\",\"EUCA000875\":\"Actions Europe Rendement\",\"EUCA000525\":\"Actions Europe du Nord\",\"EUCA000920\":\"Actions Europe du Nord Petites & Moy. Cap.\",\"EUCA000509\":\"Actions Europe hors UK Gdes Cap.\",\"EUCA000510\":\"Actions Europe hors UK Petites & Moy. Cap.\",\"EUCA000636\":\"Actions France Grandes Cap.\",\"EUCA000637\":\"Actions France Petites & Moy. Cap.\",\"EUCA000500\":\"Actions Grande Chine\",\"EUCA000701\":\"Actions Grèce\",\"EUCA000519\":\"Actions Hong Kong\",\"EUCA000520\":\"Actions Inde\",\"EUCA000780\":\"Actions Indonésie\",\"EUCA000840\":\"Actions International Chariah Islamique\",\"EUCA000558\":\"Actions International Flex-Cap.\",\"EUCA000557\":\"Actions International Gdes Cap. \\\"Value\\\"\",\"EUCA000556\":\"Actions International Gdes Cap. Croissance\",\"EUCA000836\":\"Actions International Petites Cap.\",\"EUCA000876\":\"Actions International Rendement\",\"EUCA000848\":\"Actions Internationnales - Devises Couvertes\",\"EUCA000640\":\"Actions Italie\",\"EUCA000521\":\"Actions Japon Grandes Cap.\",\"EUCA000522\":\"Actions Japon Petites & Moy. Cap.\",\"EUCA000887\":\"Actions Japonnaise - Devises Courvertes\",\"EUCA000672\":\"Actions Malaisie\",\"EUCA000507\":\"Actions Marchés Emergents\",\"EUCA000886\":\"Actions Marchés Emergents - Devises Couvertes\",\"EUCA000845\":\"Actions Marchés Frontiéres\",\"EUCA000531\":\"Actions Norvège\",\"EUCA000641\":\"Actions Pays-Bas\",\"EUCA000702\":\"Actions Pologne\",\"EUCA000553\":\"Actions Royaume Uni Flex Cap\",\"EUCA000552\":\"Actions Royaume-Uni Gdes Cap. \\\"Value\\\"\",\"EUCA000551\":\"Actions Royaume-Uni Gdes Cap. Croissance\",\"EUCA000852\":\"Actions Royaume-Uni Moyennes Cap.\",\"EUCA000554\":\"Actions Royaume-Uni Petites Cap.\",\"EUCA000642\":\"Actions Russie\",\"EUCA000827\":\"Actions Secteur Agriculture\",\"EUCA000539\":\"Actions Secteur Autres\",\"EUCA000534\":\"Actions Secteur Biens Conso. & Services\",\"EUCA000532\":\"Actions Secteur Biotechnologie\",\"EUCA000533\":\"Actions Secteur Communication\",\"EUCA000828\":\"Actions Secteur Eau\",\"EUCA000706\":\"Actions Secteur Ecologie\",\"EUCA000535\":\"Actions Secteur Energie\",\"EUCA000705\":\"Actions Secteur Energies Alternatives\",\"EUCA000536\":\"Actions Secteur Finance\",\"EUCA000707\":\"Actions Secteur Infrastructures\",\"EUCA000538\":\"Actions Secteur Matériaux & Industrie\",\"EUCA000646\":\"Actions Secteur Métaux Précieux\",\"EUCA000708\":\"Actions Secteur Non-Coté\",\"EUCA000704\":\"Actions Secteur Ressources Naturelles\",\"EUCA000537\":\"Actions Secteur Santé\",\"EUCA000543\":\"Actions Secteur Services Publics\",\"EUCA000542\":\"Actions Secteur Technologies\",\"EUCA000544\":\"Actions Singapour\",\"EUCA000548\":\"Actions Suisse Grandes Cap.\",\"EUCA000644\":\"Actions Suisse Petites & Moy. Cap.\",\"EUCA000545\":\"Actions Suède Grandes Cap.\",\"EUCA000546\":\"Actions Suède Petites & Moyennes Cap.\",\"EUCA000670\":\"Actions Taiwan Grandes Cap. \",\"EUCA000783\":\"Actions Thaïlande\",\"EUCA000703\":\"Actions Turquie\",\"EUCA000829\":\"Actions Vietnam\",\"EUCA000517\":\"Actions Zone Euro Flex Cap\",\"EUCA000516\":\"Actions Zone Euro Grandes Cap.\",\"EUCA000851\":\"Actions Zone Euro Moyennes Cap.\",\"EUCA000518\":\"Actions Zone Euro Petites Cap.\",\"EUCA000824\":\"Actions EMEA\",\"EUCA000801\":\"Alt - Fonds de Fonds Alternatifs - Actions\",\"EUCA000880\":\"Alt - Long/Short Actions - Autres\",\"EUCA000807\":\"Alt - Long/Short Actions - Europe\",\"EUCA000808\":\"Alt - Long/Short Actions - International\",\"EUCA000806\":\"Alt - Long/Short Actions - Marchés Emergents\",\"EUCA000809\":\"Alt - Long/Short Actions - UK\",\"EUCA000810\":\"Alt - Long/Short Actions - US\",\"EUCA000811\":\"Alt - Market Neutral - Actions\",\"EUCA000907\":\"Asia ex-Japan Small/Mid-Cap Equity\",\"EUCA000918\":\"Asia-Pacific ex-Japan Equity Income\",\"HSTG000004\":\"Europe Long/Short Equity\",\"HSTG000019\":\"Fund of Funds - Equity\",\"EUCA000910\":\"Global Emerging Markets Small/Mid-Cap Equity\",\"EUCA000915\":\"Japan Flex-Cap Equity\",\"EUCA000921\":\"Pacific ex-Japan Equity\",\"EUCA000816\":\"Trading - Leveraged/Inverse Actions\"},\"mix\":{\"EUCA000526\":\"Actions Etats-Unis Gdes Cap. Mixte\",\"EUCA000511\":\"Actions Europe Gdes Cap. Mixte\",\"EUCA000555\":\"Actions International Gdes Cap. Mixte\",\"EUCA000550\":\"Actions Royaume-Uni Gdes Cap. Mixte\"},\"bond\":{\"EUCA000805\":\"Alt - Long/Short Obligations\",\"EUCA000570\":\"Obligations Asie\",\"EUCA000897\":\"Obligations Asie - Devise Locale\",\"EUCA000877\":\"Obligations Asie Haut Rendement\",\"EUCA000771\":\"Obligations Autres\",\"EUCA000770\":\"Obligations Autres Devises Indexées sur l'Inflation\",\"EUCA000572\":\"Obligations CAD\",\"EUCA000573\":\"Obligations CHF\",\"EUCA000747\":\"Obligations CHF Court Terme\",\"EUCA000576\":\"Obligations DKK\",\"EUCA000753\":\"Obligations DKK Long Terme\",\"EUCA000587\":\"Obligations EUR Diversifiées\",\"EUCA000593\":\"Obligations EUR Diversifiées Court Terme\",\"EUCA000690\":\"Obligations EUR Emprunts Privés\",\"EUCA000819\":\"Obligations EUR Emprunts Privés Court Terme\",\"EUCA000589\":\"Obligations EUR Emprunts d'Etat\",\"EUCA000837\":\"Obligations EUR Emprunts d'Etat Court Terme\",\"EUCA000754\":\"Obligations EUR Flexibles\",\"EUCA000590\":\"Obligations EUR Haut Rendement\",\"EUCA000625\":\"Obligations EUR Indexées sur l'Inflation\",\"EUCA000622\":\"Obligations EUR Long Terme\",\"EUCA000592\":\"Obligations EUR Très Court Terme\",\"EUCA000594\":\"Obligations Europe\",\"EUCA000623\":\"Obligations Europe Emergente\",\"EUCA000755\":\"Obligations Europe Haut Rendement\",\"EUCA000604\":\"Obligations GBP Diversifiées\",\"EUCA000756\":\"Obligations GBP Diversifiées Court Terme\",\"EUCA000692\":\"Obligations GBP Emprunts Privés\",\"EUCA000606\":\"Obligations GBP Emprunts d'Etat\",\"EUCA000757\":\"Obligations GBP Flexibles\",\"EUCA000607\":\"Obligations GBP Haut Rendement\",\"EUCA000758\":\"Obligations GBP Indexées sur l'Inflation\",\"EUCA000768\":\"Obligations HDK\",\"EUCA000820\":\"Obligations Haut Rendement-Couvertes Autres Devises\",\"EUCA000759\":\"Obligations International\",\"EUCA000680\":\"Obligations International Chariah Islamique\",\"EUCA000762\":\"Obligations International Couvertes en Autres Devises\",\"EUCA000760\":\"Obligations International Couvertes en CHF\",\"EUCA000624\":\"Obligations International Couvertes en EUR\",\"EUCA000761\":\"Obligations International Couvertes en GBP\",\"EUCA000882\":\"Obligations International Couvertes en NOK\",\"EUCA000763\":\"Obligations International Couvertes en USD\",\"EUCA000574\":\"Obligations International Dominante CHF\",\"EUCA000588\":\"Obligations International Dominante EUR\",\"EUCA000605\":\"Obligations International Dominante GBP\",\"EUCA000766\":\"Obligations International Haut Rendement\",\"EUCA000854\":\"Obligations International Haut Rendement Couvertes en EUR\",\"EUCA000855\":\"Obligations International Haut Rendement Couvertes en GBP\",\"EUCA000731\":\"Obligations International ILS\",\"EUCA000889\":\"Obligations Internationales Emprunts Privés\",\"EUCA000890\":\"Obligations Internationales Emprunts Privés - Couvertes en CHF\",\"EUCA000891\":\"Obligations Internationales Emprunts Privés - Couvertes en EUR\",\"EUCA000892\":\"Obligations Internationales Emprunts Privés - Couvertes en GBP\",\"EUCA000893\":\"Obligations Internationales Emprunts Privés Couvertes en USD\",\"EUCA000899\":\"Obligations Internationales Flexibles\",\"EUCA000900\":\"Obligations Internationales Flexibles Couvertes en CHF\",\"EUCA000901\":\"Obligations Internationales Flexibles Couvertes en EUR\",\"EUCA000903\":\"Obligations Internationales Flexibles Couvertes en GBP\",\"EUCA000902\":\"Obligations Internationales Flexibles Couvertes en USD\",\"EUCA000894\":\"Obligations Internationales Haut Rendement Couvertes en CHF\",\"EUCA000595\":\"Obligations JPY\",\"EUCA000586\":\"Obligations Marchés Emergents\",\"EUCA000765\":\"Obligations Marchés Emergents Devise Locale\",\"EUCA000764\":\"Obligations Marchés Emergents Dominante EUR\",\"EUCA000878\":\"Obligations Marchés Emergents Emprunts Privés\",\"EUCA000879\":\"Obligations Marchés Emergents Emprunts Privés Dominante EUR\",\"EUCA000596\":\"Obligations NOK\",\"EUCA000599\":\"Obligations NOK Court Terme\",\"EUCA000772\":\"Obligations PLN\",\"EUCA000856\":\"Obligations RMB\",\"EUCA000600\":\"Obligations SEK\",\"EUCA000905\":\"Obligations SEK Emprunts Privés\",\"EUCA000580\":\"Obligations USD Diversifiées\",\"EUCA000585\":\"Obligations USD Diversifiées Court Terme\",\"EUCA000691\":\"Obligations USD Emprunts Privés\",\"EUCA000582\":\"Obligations USD Emprunts d'Etat\",\"EUCA000775\":\"Obligations USD Flexibles\",\"EUCA000583\":\"Obligations USD Haut Rendement\",\"EUCA000767\":\"Obligations USD Indexées sur l'Inflation\",\"EUCA000888\":\"Obligations à échéance\",\"EUCA000817\":\"Trading - Leveraged/Inverse Obligations\"},\"monetary\":{\"EUCA000858\":\"Monétaires  Court Terme\",\"EUCA000833\":\"Monétaires Autres Devises\",\"EUCA000575\":\"Monétaires CHF\",\"EUCA000859\":\"Monétaires Court Terme Couverte en EUR\",\"EUCA000861\":\"Monétaires Court Terme Couverte en USD\",\"EUCA000591\":\"Monétaires EUR\",\"EUCA000830\":\"Monétaires EUR Court Terme\",\"EUCA000608\":\"Monétaires GBP\",\"EUCA000831\":\"Monétaires GBP Court Terme\",\"EUCA000602\":\"Monétaires SEK\",\"EUCA000584\":\"Monétaires USD\",\"EUCA000832\":\"Monétaires USD Court Terme\",\"EUCA000860\":\"Monétaires USD Court Terme Couverte en GBP\"}}}").getAsJsonObject();

        JsonObject ms = object.get("morningstar").getAsJsonObject().get("all").getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : ms.entrySet()) {
            if (entry.getKey().equals("EUCA000739")) {
                log.fine("key=" + entry.getKey() + " value=" + entry.getValue().getAsString());
            }
            result.put(entry.getValue().getAsString().trim(),entry.getKey());
        }


        return result;
    }

    private static JsonObject createMsCategory(String categoryName, Map<String, String> categoriesMsCodes) {

        if (categoriesMsCodes.get(categoryName)!=null) {
            JsonObject categoryMs = new JsonObject();
            categoryMs.addProperty("categoryName", categoryName);
            categoryMs.addProperty("categorySearchCode", categoriesMsCodes.get(categoryName));
            return categoryMs;
        } else {
            System.out.println("Category "+categoryName+" not founded");
            return null;
        }
    }

    public static String getCategoriesBoursoResponse(String absolutePart) {

        String url = "https://www.boursorama.com" + absolutePart;
        return Utils.getBoursoResponse(url);

    }
}
