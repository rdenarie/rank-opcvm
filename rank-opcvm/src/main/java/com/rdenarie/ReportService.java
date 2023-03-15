package com.rdenarie;

import com.google.appengine.api.datastore.Entity;
import jdk.jshell.execution.Util;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ReportService {
  private static final Logger log = Logger.getLogger(ReportService.class.getName());

  public byte[] generateReport() {

    // Stream containing excel data
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){

      // Create Excel WorkBook and Sheet
      InputStream reportTemplate = new FileInputStream("WEB-INF/classes/reportTemplate.xlsx");
      XSSFWorkbook myWorkBook = new XSSFWorkbook (reportTemplate);
      List<Entity> datas = GetDataServlet.getDatasEntity(null, -1, null, GetDataServlet.getLastDate(),true);

      generateGlobalTab(datas,outputStream, myWorkBook);
      generateCategoriesTab(datas,outputStream, myWorkBook);
      return outputStream.toByteArray();
    }catch (Exception e) {
      log.fine("Unable to generate xls file");
      e.printStackTrace();
    }

    return null;

  }

  private static void generateCategoriesTab(List<Entity> datas,ByteArrayOutputStream outputStream, XSSFWorkbook myWorkBook) throws IOException {

    XSSFSheet categoriesSheet = myWorkBook.getSheetAt(1);
    AtomicInteger currentRow = new AtomicInteger(8);
    Row styleRow = categoriesSheet.getRow(7);
    Calendar calendar = Calendar.getInstance();
    int year = calendar.get(Calendar.YEAR);
    int quarter = (calendar.get(Calendar.MONTH) + 1) / 4;
    quarter++;
    String lastCellValue = quarter+"° TR "+year;
    datas.stream().collect(
        Collectors.groupingBy(entity -> entity.getProperty(Utils.CATEGORY_MS_PROPERTY),
                              Collectors.collectingAndThen(
                                  Collectors.toList(),
                                  values -> values.get(0)))).values().stream().sorted(Comparator.comparingDouble(entity -> Double.parseDouble(((Entity)entity).getProperty(Utils.SCORE_CATEGORY).toString())).reversed()).forEach((entity) -> {
                                    Row row = categoriesSheet.createRow(currentRow.get());
                                    Cell cell = row.createCell(0);
                                    cell.setCellStyle(styleRow.getCell(0).getCellStyle());
                                    cell.setCellValue(entity.getProperty(Utils.CATEGORY_MS_PROPERTY).toString());

                                    cell =row.createCell(1);
                                    cell.setCellStyle(styleRow.getCell(1).getCellStyle());
                                    cell.setCellValue(entity.getProperty(Utils.SCORE_CATEGORY).toString());

                                    cell =row.createCell(2);
                                    cell.setCellStyle(styleRow.getCell(2).getCellStyle());
                                    cell.setCellValue(entity.getProperty(Utils.CATEGORY_RANK_PROPERTY).toString());

                                    cell =row.createCell(3);
                                    cell.setCellStyle(styleRow.getCell(3).getCellStyle());
                                    cell.setCellValue(entity.getProperty(Utils.NUMBER_OF_CATEGORIES).toString());

                                    cell = row.createCell(4);
                                    cell.setCellStyle(styleRow.getCell(4).getCellStyle());
                                    cell.setCellValue(lastCellValue);
                                    currentRow.getAndIncrement();
    });
    myWorkBook.write(outputStream);


  }
  private static void generateGlobalTab(List<Entity> datas,ByteArrayOutputStream outputStream, XSSFWorkbook myWorkBook) throws IOException {

    XSSFSheet globalSheet = myWorkBook.getSheetAt(0);
    AtomicInteger currentRow = new AtomicInteger(22);
    Row styleRow = globalSheet.getRow(21);

    Calendar calendar = Calendar.getInstance();
    int year = calendar.get(Calendar.YEAR);
    int quarter = (calendar.get(Calendar.MONTH) + 1) / 4;
    quarter++;
    String lastCellValue = quarter+"° TR "+year;

    datas.stream().forEach(entity -> {
      Row row = globalSheet.getRow(currentRow.get());

      Cell cell = row.createCell(0);
      cell.setCellStyle(styleRow.getCell(0).getCellStyle());
      cell.setCellValue(currentRow.get() - 21);

      cell =row.createCell(1);
      cell.setCellStyle(styleRow.getCell(1).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.RANK_IN_CATEGORY_PROPERTY).toString());

      cell = row.createCell(2);
      cell.setCellStyle(styleRow.getCell(2).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.NUMBER_FUNDS_IN_CATEGORY_PROPERTY).toString());

      cell = row.createCell(3);
      cell.setCellStyle(styleRow.getCell(3).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.NAME_PROPERTY).toString());

      cell = row.createCell(4);
      cell.setCellStyle(styleRow.getCell(4).getCellStyle());
      cell.setCellValue(Double.parseDouble(entity.getProperty(Utils.SCORE_FOND_PROPERTY).toString()));

      cell = row.createCell(5);
      cell.setCellStyle(styleRow.getCell(5).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.MSRATING_PROPERTY).toString());

      cell = row.createCell(6);
      cell.setCellStyle(styleRow.getCell(6).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.ENTREE_PROPERTY).toString());

      cell = row.createCell(7);
      cell.setCellStyle(styleRow.getCell(7).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.SORTIE_PROPERTY).toString());

      cell = row.createCell(8);
      cell.setCellStyle(styleRow.getCell(8).getCellStyle());
      cell.setCellValue(Double.parseDouble(entity.getProperty(Utils.PRICE_EUR_PROPERTY).toString()));

      cell = row.createCell(9);
      cell.setCellStyle(styleRow.getCell(9).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.CURRENCY_PROPERTY).toString());

      cell = row.createCell(10);
      cell.setCellStyle(styleRow.getCell(10).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.TICKET_IN_PROPERTY).toString());

      cell = row.createCell(11);
      cell.setCellStyle(styleRow.getCell(11).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.TICKET_RENEW_PROPERTY).toString());

      cell = row.createCell(12);
      cell.setCellStyle(styleRow.getCell(12).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.ID_PROPERTY).toString());

      cell = row.createCell(13);
      cell.setCellStyle(styleRow.getCell(13).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.COURANT_PROPERTY).toString());

      cell = row.createCell(14);
      cell.setCellStyle(styleRow.getCell(14).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.GERANT_PROPERTY).toString());

      cell = row.createCell(15);
      cell.setCellStyle(styleRow.getCell(15).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.CATEGORY_MS_PROPERTY).toString());

      cell = row.createCell(16);
      cell.setCellStyle(styleRow.getCell(16).getCellStyle());
      cell.setCellValue(Double.parseDouble(entity.getProperty(Utils.SCORE_CATEGORY).toString()));

      cell = row.createCell(17);
      cell.setCellStyle(styleRow.getCell(17).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.CATEGORY_RANK_PROPERTY).toString());

      cell = row.createCell(18);
      cell.setCellStyle(styleRow.getCell(18).getCellStyle());
      cell.setCellValue(entity.getProperty(Utils.NUMBER_OF_CATEGORIES).toString());

      cell = row.createCell(19);
      cell.setCellStyle(styleRow.getCell(19).getCellStyle());
      cell.setCellValue(lastCellValue);
      currentRow.getAndIncrement();
    });

    myWorkBook.write(outputStream);
  }
}
