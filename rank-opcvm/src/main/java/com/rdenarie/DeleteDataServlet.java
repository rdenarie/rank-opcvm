package com.rdenarie;


import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultIterator;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Created by Romain Dénarié (romain.denarie@exoplatform.com) on 14/07/18.
 */

@WebServlet(name = "DeleteDataServlet", value = "/deleteDataServlet")
public class DeleteDataServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(DeleteDataServlet.class.getName());

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {


        Utils.setTimeZone();
        String dateParameter=request.getParameter("date");
        Entity currentDate;
        if (dateParameter!=null) {
            currentDate=GetDataServlet.getDateFromParameter(new Date(Long.parseLong(dateParameter)));
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

            FetchOptions fetchOptions;
            int limit=1000;
            int currentBatchNbResult=0;
            String startCursorString=null;
            int totalDeleted=0;

            do {
                fetchOptions = FetchOptions.Builder.withLimit(limit);
                if (startCursorString != null && !startCursorString.equals("")) {
                    fetchOptions.startCursor(Cursor.fromWebSafeString(startCursorString)); // Where we left off
                }
                Query q = new Query(Utils.DATA_ENTRY_ENTITY).setAncestor(currentDate.getKey());
                PreparedQuery preparedQuery = datastore.prepare(q);

                QueryResultIterator<Entity> resultsIterator = preparedQuery.asQueryResultIterator(fetchOptions);
                List<Entity> resultEntities = preparedQuery.asList(fetchOptions);
                currentBatchNbResult=resultEntities.size();
                Cursor cursor = resultsIterator.getCursor();
                if (cursor != null && currentBatchNbResult == limit) {         // Are we paging? Save Cursor
                    startCursorString = cursor.toWebSafeString();               // Cursors are WebSafe
                }
                datastore.delete(resultEntities.stream().map(Entity::getKey).collect(Collectors.toList()));
                totalDeleted=totalDeleted+currentBatchNbResult;
                log.fine(totalDeleted+" elements deleted.");
            } while (currentBatchNbResult==limit);

            datastore.delete(currentDate.getKey());
            response.getWriter().println("Date "+dateParameter+" deleted.");

        }
    }

}
