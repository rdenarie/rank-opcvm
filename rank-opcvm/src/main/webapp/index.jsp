<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<html>
<head>
 <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
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

           #title > h1 {
            display:inline;
           }

           #title button {
            line-height:22px;
            display:inline-flex;
           }

           #previousDate {
            margin-right:10px;
           }

           #nextDate {
            margin-left:10px;
           }

           .top {
                position: fixed;
                top: 0;
                left: 0;
                z-index: 999;
                width: 100%;
                height: 120px;
                background-color: white;
           }

           .dashboardDiv {
            margin-top:120px;
           }

            .displayAll {
                float:right;
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
        <div class="top">
            <ul class="listCategory">
                <li><a class="linkCategory" href="/index.jsp">Tout</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Afrique et M.O.">Afrique et M.O.</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Allocations Diversifiées">Allocations Diversifiées</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Amérique du Nord">Amérique du Nord</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Asie et Pacifique">Asie et Pacifique</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Europe et Zone Euro">Europe et Zone Euro</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Internationales">Internationales</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Obligataires">Obligataires</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Pays Emergents">Pays Emergents</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Pays Européens">Pays Européens</a></li>
                <li><a class="linkCategory" href="/index.jsp?category=Sectoriels">Sectoriels</a></li>
            </ul>
            <div id="title">
                <span id="previousDate" style="display:none"><button id="previousButton"><i class="material-icons">keyboard_arrow_left</i></button></span>
                <h1>

                </h1>
                <span id="nextDate" style="display:none"><button id="nextButton"><i class="material-icons">keyboard_arrow_right</i></button></span>
            </div>
        </div>

        <div class="dashboardDiv">
            <span style="display:none" id="cursor"></span>
            <span class="displayAll">
                <button id="displayAll">Afficher Tout</button>
            </span>
            <div id="filterDiv">


            </div>
            <div id="dataTable">


            </div>
        </div>


         <script type="text/javascript">
                google.charts.load('current', {'packages':['table', 'controls']});
                google.charts.setOnLoadCallback(drawTable);
                var url="getData";
                var dateUrl="/index.jsp";
                var parameter = "<%=request.getParameter("category")%>";
                var dateParam = "<%=request.getParameter("date")%>";
                if (parameter!=null && parameter!="" && parameter!="null") {
                    url+="?category="+parameter;
                    dateUrl+="?category="+parameter;
                    if (dateParam!=null && dateParam!="" && dateParam!="null") {
                        url+="&date="+dateParam;
                    }
                }  else  {
                    if (dateParam!=null && dateParam!="" && dateParam!="null") {
                        url+="?date="+dateParam;
                    }
                }

                if (dateParam!=null) {
                    $(".linkCategory").each(function() {
                        if ($(this).attr("href").indexOf("?")!=-1) {
                            $(this).attr("href",$(this).attr("href")+"&date="+dateParam);
                        } else {
                            $(this).attr("href",$(this).attr("href")+"?date="+dateParam);
                        }
                    });
                }


                function infiniteScroll() {
                    // vérifie si c'est un iPhone, iPod ou iPad

                    $(window).data('ajaxready', true);
                    var deviceAgent = navigator.userAgent.toLowerCase();
                    var agentID = deviceAgent.match(/(iphone|ipod|ipad)/);

                    // on déclence une fonction lorsque l'utilisateur utilise sa molette
                    $(window).scroll(function() {
                        if ($(window).data('ajaxready') == false) return;
                        // cette condition vaut true lorsque le visiteur atteint le bas de page
                        // si c'est un iDevice, l'évènement est déclenché 150px avant le bas de page
                        if(($(window).scrollTop() + $(window).height()) + 300>= $(document).height()
                        || agentID && ($(window).scrollTop() + $(window).height()) + 450 > $(document).height()) {
                            $(window).data('ajaxready', false);
                            // on effectue nos traitements
                            var cursor=$("#cursor").text();
                            if (cursor!="") {
                                var nextUrl=url;
                                if (nextUrl.indexOf("?")>-1) {
                                    nextUrl+="&";
                                } else {
                                    nextUrl+="?";
                                }
                                nextUrl+="startCursorString="+cursor;
                            }

                            var jsonData = $.ajax({
                                url: nextUrl,
                                dataType: "json",
                                success : function(jsonData)
                                {
                                    readAndDraw(jsonData);
                                    $(window).data('ajaxready', true);
                                },
                                error:function (xhr, ajaxOptions, thrownError){
                                     if(xhr.status==404) {
                                        $("#dataTable").html("No Data");
                                     }
                                 }
                            });
                        }
                    });
                };


                function readAndDraw(jsonData) {
                    var date = jsonData.date;
                    var startCursorString=jsonData.cursorString;
                    if (startCursorString!=null) {
                        $("#cursor").text(startCursorString);
                    }
                    $("#title h1").html("Donn&eacutees du "+date);

                    var previousDate=jsonData.previousDate;
                    if (previousDate!=null) {
                        $("#previousButton").append(previousDate);
                        $("#previousDate").show();
                        $("#previousButton").click(function() {
                            var previousDateUrl=dateUrl;
                            if (dateUrl.indexOf("?")==-1) {
                                previousDateUrl+="?date="+jsonData.previousTime;
                            } else {
                                previousDateUrl+="&date="+jsonData.previousTime;
                            }
                            window.location = previousDateUrl;
                        });
                    }


                    var nextDate=jsonData.nextDate;
                    if (nextDate!=null) {
                        $("#nextButton").append(nextDate);
                        $("#nextDate").show();
                        $("#nextButton").click(function() {
                            var nextDateUrl=dateUrl;
                            if (dateUrl.indexOf("?")==-1) {
                                nextDateUrl+="?date="+jsonData.nextTime;
                            } else {
                                nextDateUrl+="&date="+jsonData.nextTime;
                            }
                            window.location = nextDateUrl;
                        });

                    }
                    if (!$(window).data('data')) {
                        $(window).data('jsonData', jsonData);
                        $(window).data('data', new google.visualization.DataTable($(window).data('jsonData').data,0.6));
                    } else {
                        $.merge($(window).data('jsonData').data.rows, jsonData.data.rows);
                    }
                    var options = {
                        sortColumn: 1,
                        sortAscending: false,
                        showRowNumber: true,
                        width: '100%',
                        height:'100%'
                    };


                    //var table = new google.visualization.Table(document.getElementById('dataTable'));
                    // Create a dashboard.
                    if (!$(window).data('dashboard')) {
                        $(window).data('dashboard', new google.visualization.Dashboard(document.getElementById('dashboardDiv')));
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
                        $(window).data('tableChart',tableChart);

                        $(window).data('dashboard').bind(categorySelector, tableChart);
                        $(window).data('dashboard').draw($(window).data('data'));
                    } else {

                        var scrollTop=$(window).scrollTop();
                        google.visualization.events.addListener($(window).data('tableChart'), 'ready', function() {
                            $(window).scrollTop(scrollTop);
                        });
                        $(window).data('tableChart').draw()

                    }
                }

                function drawTable() {
                    var jsonData = $.ajax({
                        url: url,
                        dataType: "json",
                        success : function(jsonData)
                        {
                            readAndDraw(jsonData);
                        },
                        error:function (xhr, ajaxOptions, thrownError){
                             if(xhr.status==404) {
                                $("#dataTable").html("No Data");
                             }
                         }
                    });
                }
                infiniteScroll();

                $("#displayAll").click(function() {
                    $(window).data('ajaxready', false);
                    // on effectue nos traitements
                    var cursor=$("#cursor").text();
                    if (cursor!="") {
                        var nextUrl=url;
                        if (nextUrl.indexOf("?")>-1) {
                            nextUrl+="&";
                        } else {
                            nextUrl+="?";
                        }
                        nextUrl+="startCursorString="+cursor;
                    }

                    if (nextUrl.indexOf("?")>-1) {
                        nextUrl+="&";
                    } else {
                        nextUrl+="?";
                    }
                    nextUrl+="displayAll=true";

                    var jsonData = $.ajax({
                        url: nextUrl,
                        dataType: "json",
                        success : function(jsonData)
                        {
                            readAndDraw(jsonData);
                        },
                        error:function (xhr, ajaxOptions, thrownError){
                             if(xhr.status==404) {
                                $("#dataTable").html("No Data");
                             }
                         }
                    });
                });

         </script>

    </body>
</html>
