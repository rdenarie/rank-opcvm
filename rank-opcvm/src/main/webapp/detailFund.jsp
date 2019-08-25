<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<html>
<head>
 <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
</head>
    <body>
        <!--
        <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
        -->
        <script type="text/javascript" src="./jsLocal/loader.js"></script>
        <!--
            <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js"></script>
        -->
        <script type="text/javascript" src="./jsLocal/jquery.min.js"></script>

        <div class="dashboardDiv">

            <div id="dataTable">


            </div>
        </div>
         <script type="text/javascript">


            var parameter = "<%=request.getParameter("id")%>";
            if (parameter!=null && parameter!="" && parameter!="null") {
                google.charts.load('current', {'packages':['line']});
                google.charts.setOnLoadCallback(drawTable);


            }  else  {
                window.location="index.jsp";
            }


            function drawTable() {
                var url="getFundData?id="+parameter;


                var jsonData = $.ajax({
                    url: url,
                    dataType: "json",
                    success : function(jsonData)
                    {

                        var dataArray = [];
                        $.each(jsonData.data, function (i, item) {
                            var d = new Date(item[0]);
                            dataArray.push([d, item[1]]);
                        });

                        var id = jsonData.isin;
                        var name=jsonData.name;
                        readAndDraw(dataArray,id,name);
                    },
                    error:function (xhr, ajaxOptions, thrownError){
                         if(xhr.status==404) {
                            $("#dataTable").html("No Data");
                         }
                     }
                });
            }

            function readAndDraw(jsonData,id,name) {
                var data = new google.visualization.DataTable();
                data.addColumn('date', 'Date');
                data.addColumn('number', 'Cours');
                data.addRows(jsonData);

                 var options = {
                    chart: {
                      title: id+" - "+name,
                    },
                    width: 900,
                    height: 500
                  };

                  var chart = new google.charts.Line(document.getElementById('dataTable'));
                  chart.draw(data, google.charts.Line.convertOptions(options));
            }



        </script>
    </body>
</html>
