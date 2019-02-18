<!-- Maps API Key: AIzaSyAQLGxd2t-CdzhMMVCgLRqJcpNEj1s114Q -->
<!-- https://developers.google.com/maps/documentation/javascript/kml#sidebar -->
<!DOCTYPE html>
<html>
  <head>
    <meta name="viewport" content="initial-scale=1.0, user-scalable=no">
    <meta charset="utf-8">
    <title>Route Data</title>
    <style>
      html, body {
        height: 100%;
        padding: 0;
        margin: 0;
        }

      #wrapper{
        height: 93%;
        width: 100%;
      }

      #gui{
        height: 100%;
        width: 22%;
        float: left;
        overflow: hidden;
        clear: left;
        bottom: -20px; 
        position: relative;
        top: -7px;
        border: 1px solid white;
      }


      #map {
        height: 100%;
        overflow: hidden;
        border: 1px solid white;
       }
      
      /* The container <div> - needed to position the dropdown content */
      .dropdown {
        position: relative;
        display: inline-block;
      }

      /* Dropdown Contetn (Hidden by Default) */
      .dropdown-content {
        display: none;
        position: absolute;
        background-color: #f1f1f1;
        min-width: 160px;
        box-shadow: 0px 8px 16px 0px rgba(0,0,0,0.2);
        z-index: 1;
        font-size: 70%;
      }

      /* Links inside the dropdown */
      .dropdown-content a {
        color: black;
        padding: 12px 16px;
        text-decoration: none;
        display: block;
      }

      /* Change color of dropdown links on hover */
      .dropdown-content a:hover {
          background-color: #ddd;
      }

      /* Show the dropdown menu (use JS to add this class to the .dropdown-content
         container when the user clicks on the dropdown button0 */
      .show {
          display:block;
      }
    
    /* Brennan's edits */
    .header {
        background-color: #0094FF; 
        text-align: center;
        color: black;
        font-size: 150%;
        font-family: Helvetica;
        padding 0;
        border: 1px solid white; 
        text-shadow: 1px 1px #fff;
     }

    h5 {
        margin-top: 10px;
        margin-bottom: 10px;
    }
    /* Menu button in to left corner used to select rides */
    #button {
        float: left;
        background-color: #085DAD;
        position: relative;
        top: -9px;
        text-decoration: none;
        border: none;
       background: url(https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/VisualEditor_-_Icon_-_Menu.svg/32px-VisualEditor_-_Icon_-_Menu.svg.png) 10px 10px no-repeat;
        padding: 10px 10px 10px 43px;
        font-family: Helvetica;
        font-weight: bold;
        background-position: 7px 4px;
        height: 40px;
    }
      /* Dropdown button on hover & focus */
      #button:hover, #button:focus {
        background-color: #77A6F7;
      }
    
    /* Formatting for table that holds rider statistics */
    #ride-stats {
        font-family: "Trebuchet MS", Arial, Helvetica;
        border-collapse: collapse;
        width: 100%;
    }
    
    #ride-stats td, #ride-stats th {
        border: 1px solid #ddd;
        padding: 8px;
        font-size: 80%;
    }
    
    #ride-stats tr:nth-child(even) {
        background-color: #f2f2f2;
    }

    #ride-stats tr:hover {
        background-color: #ddd;
    }

    #ride-stats th { 
        font-size: 100%;
        background-color: #8DDF00;
    } 
    /* bike image in top right corner */
    #bike-image {             
        height: "30px";
        width: "30px"; 
        float: "right"; 
        position: "relative";
        padding: "10px";
    }
   
    </style>

 </head>
  <body>
    <!-- Title Banner -->
    <div class="header">
        <h5>IntelliBike
        
         <!-- Top left dropdown menue button -->
             <button onclick="showDrop()"  id="button">Select Ride</button>
    
                <img src="bike-icon2.png" alt="BIKE" height="25px" width="35px" style="float: right; margin-right: 8px" align="left">
                    <!-- Dropdown menu -->            
                    <div id="myDropdown" class="dropdown-content">
                    
                    <!-- Template for what I want from mySQL
                    <a href="#">Link 1</a>
                    <a href="#">Link 2</a>
                    <a href="#">Link 3</a>
                    -->

                    <?php
                        include 'loginPhone.php';
                        
                        $conn = new mysqli($servername, $username, $password, $db);
                        if($conn->connect_error){
                            die("Connection failed: " . $conn->connect_error);
                        }

                        $sql = "SELECT * FROM ride_history";
                        $result = $conn->query($sql);
                        

                        while($row = $result->fetch_assoc()){
                            $timestamp = $row['date'];
                            $date = date("M jS, y - gA", $timestamp);

                            echo '<a href="http://intellibikeubc.com/?id='.$row["ride_id"].'">' .$date. '</a>'; 
                        }

                    ?>

                </div>
    
             <script>

                /* When the user click on the button, toggle between hding
                   and showing the dropdown content */
                function showDrop(){
                    document.getElementById("myDropdown").classList.toggle("show");
                }

                /* Close the dropdown if the user clicks outside of it */
                window.onclick = function(event) {
                    if(!event.target.matches('#button')){
                        var dropdowns = document.getElementsByClassName("dropdown-content");
                        var i;

                        for(i = 0; i < dropdowns.length; i++){
                            var openDropdown = dropdowns[i];
                            if(openDropdown.classList.contains('show')){
                                openDropdown.classList.remove('show');
                            }
                        }
                    }
                }
            </script>
        </h5>
    </div>    
 
      <div id="wrapper">
        <div style="background-color:white" id="gui">
            <!-- Rider statistics table -->
            <table id="ride-stats">
                <tr>
                    <th colspan="3">Rider Statistics</th>
                </tr>
<?php
                        include 'loginPhone.php';

                        $conn = new mysqli($servername, $username, $password, $db);
                        //Check if it is alive
                        if($conn->connect_error){
                            die("Connection failed: " . $conn->connect_error);
                        }

               echo" <tr>";
                   echo" <td>Top Speed</td>";
                

                        $sql = "SELECT * FROM logged_data ORDER BY speed DESC LIMIT 1";
                        $result = $conn->query($sql);
                        $row = $result->fetch_assoc();

                    
                       echo "<td>".$row['speed']." km/h</td>";
                    
                echo"</tr>";

                echo" <tr>";
                   echo" <td>Total Distance</td>";
                

                        $sql = "SELECT ride_id, MAX(distance) FROM logged_data GROUP BY ride_id";
                        $result_d = $conn->query($sql);

                        $totDist = 0;

                        foreach ($result_d as $val){
                            //echo $val['distance'];
                            $totDist += $val['MAX(distance)'];
                        }

                        $kms = $totDist / 1000;

                    
                       echo "<td>".number_format((float)$kms, 2, '.', '')." km</td>";
                    
                echo"</tr>";

                echo" <tr>";
                   echo" <td>Total Time</td>";
                

                        $sql = "SELECT ride_id, MAX(worldTime) FROM logged_data GROUP BY ride_id";
                        $result = $conn->query($sql);

                        $time = 0;

                        foreach ($result as $val){
                            //echo $val['distance'];
                            $time += $val['MAX(worldTime)'];
                        }

                        $sql = "SELECT ride_id, MIN(worldTime) FROM logged_data GROUP BY ride_id";
                        $result = $conn->query($sql);

                        foreach ($result as $val){
                            //echo $val['distance'];
                            $time -= $val['MIN(worldTime)'];
                        }

                        $hrs = $time / 3600;
                    
                       echo "<td>". number_format((float)$hrs, 2, '.', '')." hrs</td>";
                    
                echo"</tr>";

                echo" <tr>";
                   echo" <td>Average Speed</td>";

                        $avgSpeed = $kms / $hrs;

                    echo "<td>".number_format((float)$avgSpeed, 2, '.', '')." km/h</td>";                
                echo"</tr>";


                echo" <tr>";
                   echo" <td>Average Distance</td>";
                

                        $avgKms = $kms / count($result_d);

                    
                       echo "<td>".number_format((float)$avgKms, 2, '.', '')." km</td>";
                    
                echo"</tr>";


                echo" <tr>";
                   echo" <td>Average Time</td>";
                

                        $avgHrs = $hrs / count($result_d);

                    
                       echo "<td>".number_format((float)$avgHrs, 2, '.', '')." hrs</td>";
                    
                echo"</tr>";
?>                

            </table>        
        </div>

        <div id="map"></div>
        <div id="capture"></div>

        <script>
          var map;

          function loadMap() {
            map = new google.maps.Map(document.getElementById('map'), {
              center: new google.maps.LatLng(-19.257753, 146.823688),
              zoom: 16,
              mapTypeId: 'terrain'
            });

            var kmlLayer = new google.maps.KmlLayer(
                        <?php 
                            $Folder = 'http://intellibikeubc.com/kml/';
                            $id = $_GET["id"];
                            if($id != ""){
                                $path = $Folder."journey_$id.kml";    
                            }else{
                                $path = $Folder.'DefaultKML.kml';
                            }
                            echo "'".$path."'";
                        ?>, {
              suppressInfoWindows: false,
              preserveViewport: false,
              map: map
            });
          }
        </script>

    <script  defer
    src="https://maps.googleapis.com/maps/api/js?key=AIzaSyAQLGxd2t-CdzhMMVCgLRqJcpNEj1s114Q&callback=loadMap">
    </script>
      </div>
    
    
  </body>
</html>
