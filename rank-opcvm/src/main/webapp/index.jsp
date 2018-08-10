<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<html>
</head>
    <body>

        <style>
            #dataTable, #title {
                text-align:center;
            }
            #dataTable {
                max-height:1000px;
            }

           .listCategory > li {
                display:inline;
                margin: 0px 10px;
           }

           .google-visualization-table-table > thead {
            position:relative;
            top:0;
           }

        </style>
        <!--
        <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
        -->
        <script type="text/javascript" src="./jsLocal/loader.js"></script>
        <!--
            <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js"></script>
        -->
        <script type="text/javascript" src="./jsLocal/jquery.min.js"></script>
        <ul class="listCategory">
            <li><a href="/index.jsp">Tout</a></li>
            <li><a href="/index.jsp?category=Afrique et M.O.">Afrique et M.O.</a></li>
            <li><a href="/index.jsp?category=Allocations Diversifiées">Allocations Diversifiées</a></li>
            <li><a href="/index.jsp?category=Amérique du Nord">Amérique du Nord</a></li>
            <li><a href="/index.jsp?category=Asie et Pacifique">Asie et Pacifique</a></li>
            <li><a href="/index.jsp?category=Europe et Zone Euro">Europe et Zone Euro</a></li>
            <li><a href="/index.jsp?category=Internationales">Internationales</a></li>
            <li><a href="/index.jsp?category=Obligataires">Obligataires</a></li>
            <li><a href="/index.jsp?category=Pays Emergents">Pays Emergents</a></li>
            <li><a href="/index.jsp?category=Pays Européens">Pays Européens</a></li>
            <li><a href="/index.jsp?category=Sectoriels">Sectoriels</a></li>
        </ul>
        <div id="title">
            <h1>

            </h1>
        </div>
        <div class="dashboardDiv">
            <div id="filterDiv">


            </div>
            <div id="dataTable">


            </div>
        </div>


         <script type="text/javascript">
                google.charts.load('current', {'packages':['table', 'controls']});
                google.charts.setOnLoadCallback(drawTable);
                var url="getData";
                var parameter = "<%=request.getParameter("category")%>";
                if (parameter!=null && parameter!="" && parameter!="null") {
                    url+="?category="+parameter;
                }
                function drawTable() {
                    var jsonData = $.ajax({
                        url: url,
                        dataType: "json",
                        success : function(jsonData)
                        {
                            var date = jsonData.date;
                            $("#title h1").html("Donn&eacutees du "+date);


                            var data =  new google.visualization.DataTable(jsonData.data,0.6)
                            var options = {
                                sortColumn: 1,
                                sortAscending: false,
                                showRowNumber: true,
                                width: '100%',
                                height:'100%'
                            };


                            //var table = new google.visualization.Table(document.getElementById('dataTable'));
                            // Create a dashboard.
                            var dashboard = new google.visualization.Dashboard(document.getElementById('dashboardDiv'));

                            // Create a category selector
                            var categorySelector = new google.visualization.ControlWrapper({
                              'controlType': 'CategoryFilter',
                              'containerId': 'filterDiv',
                              'options': {
                                'filterColumnLabel': 'Catégorie MS'
                              }
                            });
                            // Create a table chart, passing some options
                            var tableChart = new google.visualization.ChartWrapper({
                              'chartType': 'Table',
                              'containerId': 'dataTable',
                              'options': {
                                'sortColumn': 1,
                                'sortAscending': false,
                                'showRowNumber': true,
                                'width': '100%',
                              }
                            });
                            dashboard.bind(categorySelector, tableChart);


                            dashboard.draw(data);

                        },
                        error:function (xhr, ajaxOptions, thrownError){
                             if(xhr.status==404) {
                                $("#dataTable").html("No Data");
                             }
                         }
                    });

                }

         </script>

    </body>
</html>
