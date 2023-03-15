package com.rdenarie;


import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "TestDataServlet", value = "/testDataServlet")
public class TestDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(TestDataServlet.class.getName());
    private static final int LIMIT = 50;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        ReportService reportService = new ReportService();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment; filename=report.xlsx");


        byte[] answer = reportService.generateReport();
        response.setContentLength(answer.length);
        OutputStream os = response.getOutputStream();

        try {
            os.write(answer , 0, answer.length);
        } catch (Exception excp) {
            //handle error
        } finally {
            os.close();
        }

    }

}
