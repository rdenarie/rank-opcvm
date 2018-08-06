<!DOCTYPE html>

<html>
</head>
    <body>

        <style>
            #dataTable, #title {
                text-align:center;
            }

            table.google-visualization-table-table > thead {
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

        <div id="title">
            <h1>

            </h1>
        </div>

        <div id="dataTable">

        </div>


         <script type="text/javascript">
                google.charts.load('current', {'packages':['table']});
                google.charts.setOnLoadCallback(drawTable);
                function drawTable() {
                    var jsonData = $.ajax({
                        url: "getData",
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
                                height: '800'
                            };


                            var table = new google.visualization.Table(document.getElementById('dataTable'));
                            table.draw(data, options);
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
