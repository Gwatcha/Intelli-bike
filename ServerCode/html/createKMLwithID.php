<?php
include 'loginPhone.php';

//Parameters
$RideID = $_GET['id'];

$TableName = 'logged_data';
$OutputFile = "journey_$RideID.kml";
$Folder = '/var/www/html/kml/';

//Fill in these paths with a HTML request
//$RideID = $_GET['rideid']
$OutputPath = $Folder . $OutputFile; //Combines the directory with the file name

//Globals
error_reporting(-1); 
ini_set('display_errors', 'On');

$START_ICON = 'https://cdn0.iconfinder.com/data/icons/small-n-flat/24/678111-map-marker-512.png';
$START_PATH_ICON = 'https://emojipedia-us.s3.amazonaws.com/thumbs/160/facebook/65/bicycle_1f6b2.png';
$FINISH_PATH_ICON = 'https://emojipedia-us.s3.amazonaws.com/thumbs/160/facebook/65/bicycle_1f6b2.png';
$FINISH_ICON = 'https://cdn2.iconfinder.com/data/icons/ios-7-icons/50/finish_flag-128.png';
$MARKER_ICON = 'https://cdn4.iconfinder.com/data/icons/iconsimple-places/512/pin_1-512.png';

//Ride statistic variables.
$totalReadings = 0;
$speedSummation = 0;
$avgspeed = 0;
$topspeed = 0;
$topaccel = 2;
$initialAlt = 0;

//Keep track of how many paths we have so we can label them numerically/
$PATHS = 1;

// Creates the Document.
$dom = new DOMDocument('1.0', 'UTF-8');
$dom->preserveWhiteSpace = true;
$dom->formatOutput = true;

//Tier 0
//Creates the root KML element and appends it to the root document.
$node = $dom->createElementNS('http://www.opengis.net/kml/2.2', 'kml');
$rootNode = $dom->appendChild($node);
$mainDoc = $dom->createElement('Document');
$parNode = $rootNode->appendChild($mainDoc);

//Tier 1
$name = $dom->createElement('name', "KML for journey: $RideID");
$waypointsFolder = $dom->createElement('Folder');
$tracksFolder = $dom->createElement('Folder');

$parNode->appendChild($name);
$parNode->appendChild($waypointsFolder);
$parNode->appendChild($tracksFolder);

//Tier 2, Setup the folders
$waypointsFolder->setAttribute('id', 'Waypoints');
$tracksFolder->setAttribute('id', 'Tracks');

//Connect with Server and populate the rest of the data!
//Create the connection
$conn = new mysqli($servername, $username, $password, $db);
//Check if it is alive
if($conn->connect_error){
    die("Connection failed: " . $conn->connect_error);
}

//Select from the table specified and the correct ride number.
$sql = "SELECT * FROM " . $TableName . " WHERE ride_id = ". $RideID;
echo $sql;
$result = $conn->query($sql);

//Create the first track folder.
$currentTrackFolder = $dom->createElement('Folder');
$currentTrackFolder->setAttribute('id', 'TrackOne');

//prevRow is initially the start row.
$prevRow = $result->fetch_assoc();
$startPlacemark = $dom->createElement('Placemark');

//Add auxillary things for start node, (style, name).. 
$name = $dom->createElement('name');
$name->nodeValue = '<!CDATA['.'<b><div> Journey '.$GLOBALS['TableName'].' Start</div></b>';
$startPlacemark->appendChild($name); 

//Add rest of info by reading row.
$startDesc = $dom->createElement('description'); 
$startDesc->nodeValue = 
'Coordinates :'.$prevRow["COOR_lng"].', '.$prevRow["COOR_lat"];
$startPlacemark->appendChild($startDesc);
appendWaypointAuxillaryElements($startPlacemark, $dom, 'START');
$startCoorString = $prevRow["COOR_lng"].','.$prevRow["COOR_lat"].','.$prevRow["COOR_alt"];
$startCoor = $dom->createElement('coordinates', $startCoorString);
$startPoint = $dom->createElement('Point');
$startPoint->appendChild($startCoor);

$GLOBAL['initialAlt'] = $prevRow["COOR_alt"];

//This is what needs to be done for each placemark. Start is done now.
$startPlacemark->appendChild($startPoint);

//Append it to the waypoints.
$waypointsFolder->appendChild($startPlacemark);

    //Convert rest of the data.
    while($row = $result->fetch_assoc()){

        //Update journey statistics.
        updateStatistics($row, $prevRow);

        //In the case this is a new path start, create a new track folder with a waypoint indicating the start.
        if ($row["type"] == "START_P") {
            $PATHS++;
            $currentTrackFolder = $dom->createElement('Folder');
            $currentTrackFolder->setAttribute('id', "Track".$PATHS);

            $placemarkNode = $dom->createElement('Placemark');
	   $name = $dom->createElement('name', "Start of Path " . $GLOBALS['PATHS']);
	   $placemarkNode->appendChild($name); 
            appendWaypointAuxillaryElements($placemarkNode, $dom, 'START_P');
			//Add waypoint point data
		$CoorString = $prevRow["COOR_lng"].','.$row["COOR_lat"].','.$row["COOR_alt"];
		$Coor= $dom->createElement('coordinates', $CoorString);
		$Point= $dom->createElement('Point');
		$Point->appendChild($Coor);
		$placemarkNode->appendChild($Point);
            $waypointsFolder->appendChild($placemarkNode);
        }

        //A Path is always added from the point in previous row to the point in current row unless 
        //The current row is the start of a new path.
        else {
            addPath($prevRow, $row, $currentTrackFolder, $dom);

            //Determine what kind of way point to add, if any.
            //Also determines if we should append the track folder.
            switch( $row["type"]) {
                case 'MARKER' :
                   $placemarkNode = $dom->createElement('Placemark'); 
		   $name = $dom->createElement('name', 'User Marker');
		   $placemarkNode->appendChild($name); 
                   appendWaypointAuxillaryElements($placemarkNode, $dom,'MARKER');

				//Add waypoint point data
			$CoorString = $prevRow["COOR_lng"].','.$row["COOR_lat"].','.$row["COOR_alt"];
			$Coor= $dom->createElement('coordinates', $CoorString);
			$Point= $dom->createElement('Point');
			$Point->appendChild($Coor);
			$placemarkNode->appendChild($Point);
                   $waypointsFolder->appendChild($placemarkNode);
                   break;
                case 'FINISH_P':
                   $placemarkNode = $dom->createElement('Placemark'); 
		   $name = $dom->createElement('name', "End of Path ".$GLOBALS['PATHS']);
		   $placemarkNode->appendChild($name); 
                   appendWaypointAuxillaryElements($placemarkNode, $dom,'FINISH_P');
				//Add waypoint point data
			$CoorString = $prevRow["COOR_lng"].','.$row["COOR_lat"].','.$row["COOR_alt"];
			$Coor= $dom->createElement('coordinates', $CoorString);
			$Point= $dom->createElement('Point');
			$Point->appendChild($Coor);
			$placemarkNode->appendChild($Point);
                   $waypointsFolder->appendChild($placemarkNode);
                   $tracksFolder->appendChild($currentTrackFolder);
                   break;
                case 'FINISH' :
                   $placemarkNode = $dom->createElement('Placemark'); 
	     	   $name = $dom->createElement('name');
	         	$name->nodeValue = '<!CDATA['.'<b><div> Journey '.$GLOBALS['TableName'].' End</div></b>';
			$placemarkNode->appendChild($name); 
		
		   global $initialAlt;
		   $netAlt = $row["COOR_alt"] - $initialAlt;

                    $finishDesc = $dom->createElement('description'); 
		    $finishDesc->nodeValue = '<!CDATA['.
                      'Finish time: 	       '.date('Y-m-d h:i:s',$row['worldTime']).'<br>'
                      .'Coordinates:           '.$row['COOR_lng'].', '.$row['COOR_lat'].'<br>'
                      .'Altitude:              '.$row['COOR_alt'].'<br><br>' 
                      .'<u><b>STATISTICS</b></u><br>'
                      .'Distance Travelled: '.$row['distance'].' m<br><br>'
                      .'Top Speed:          '.$GLOBALS['topspeed'].' m/s   <br>'
                      .'Average Speed:      '.$GLOBALS['avgspeed'].' m/s <br><br>'
                      .'Top Acceleration:   '.$GLOBALS['topaccel'].' m/s^2<br><br>'
                      .'Net Altitude Gain:  '.$netAlt.' m ';

                   $placemarkNode->appendChild($finishDesc);
                   appendWaypointAuxillaryElements($placemarkNode, $dom,'FINISH');
				//Add waypoint point data
			$CoorString = $row["COOR_lng"].','.$row["COOR_lat"].','.$row["COOR_alt"];
			$Coor= $dom->createElement('coordinates', $CoorString);
			$Point= $dom->createElement('Point');
			$Point->appendChild($Coor);
			$placemarkNode->appendChild($Point);
                   $waypointsFolder->appendChild($placemarkNode);
                   $tracksFolder->appendChild($currentTrackFolder);
                   break;
                case 'PATH' : break;
                case 'START': echo "Found Another START node!"; break;
                default :
                
                    echo "Bad row type read from SQL. Got: ".$row["type"]." END";
                }
         }

        $prevRow = $row;
    }


//Wrap things up.
$conn->close();
$dom->save($OutputPath);
$kmlOutput = $dom->saveXML();

echo $kmlOutput;

//Appends a Placemark child to the trackfolder specified, the placemark will contain
//A correctly colored path from sqlRowPrev to sqlRowCurr with data that is the readings of 
//sqlRowPrev
function addPath($sqlRowPrev, $sqlRowCurr, &$currentTrackFolder, &$dom) {
    $placemarkNode = $dom->createElement('Placemark');
    $name = $dom->createElement('name');
    $name->nodeValue = '<!CDATA['.'<u><span style=\'color:#bfe600\'>'.date('Y-m-d h:i:s',$sqlRowPrev['worldTime']).'</span></u>';

    $desc = $dom->createElement('description'); 
    $desc->nodeValue = '<![CDATA['
          .'<b><div> READINGS </div></b><br>'
          .'Speed:        '.$sqlRowPrev['speed'].' km/h<br>'
          .'Acceleration: '.$sqlRowPrev['acceleration'].' m/s^2<br>'
          .'Altitude:     '.$sqlRowPrev['COOR_alt'].' m<br>'
          .'Distance:     '.$sqlRowPrev['distance'].' m'
          ;
          
    $coordinatesString =  $sqlRowPrev["COOR_lng"].','.$sqlRowPrev["COOR_lat"].' '.$sqlRowCurr["COOR_lng"].','.$sqlRowCurr["COOR_lat"].' ' ;    
    $coordinates = $dom->createElement('coordinates', $coordinatesString);
    
    $LineString = $dom->createElement('LineString');

   //The line color is based on the speed of the first point.
   $colorhex = getLineColor($sqlRowPrev["speed"]);

   $Style = $dom->createElement("Style"); 
   $LineStyle = $dom->createElement("LineStyle"); 
   $width = $dom->createElement("width", '3'); 
   $color = $dom->createElement("color", $colorhex); 

   $LineString->appendChild($coordinates);

   $LineStyle->appendChild($color);
   $LineStyle->appendChild($width);
   $Style->appendChild($LineStyle);
   $placemarkNode->appendChild($name);
   $placemarkNode->appendChild($desc);
   $placemarkNode->appendChild($Style);
   $placemarkNode->appendChild($LineString);

   //Finnaly..
   $currentTrackFolder->appendChild($placemarkNode);
}

//Returns a <color> value eg. ff00e6bf, depending on the speed passed.
//Maxes at 
function getLineColor($speed) {
    $speedUsed = $speed;
    if ($speed > 70)
        $speedUsed = 70;

    //Max value RED at 70km/h w/ r = 255 g = 0 b =0
    //at 35km/h YELLOW w/ r= 255 g = 255 b =0
    //At 0km/h GREEN w/ r= 0 g = 255 b = 0
    //Returns hex in form ABGR
    // a = 0x80 = 128 << 6 = 2147483648

    //70 -> 255 
    //0 -> 65280  
    //so m = -928.9285714
    $hexVal = (int) ((-928.9285714)*($speedUsed) + 65280 + 2147483648);
    return (dechex($hexVal));
}

//Updates global journey statistics variables
function updateStatistics($row, $prevRow) {
    $speed = $row["speed"];
    $accel = $row["acceleration"];
    global $topaccel;
    global $topspeed;
    global $totalReadings;
    global $speedSummation;

    if ($speed != 0) {
        $totalReadings++;
        $speedSummation += $speed;
    }

    if ($speed > $topspeed) {
        $topspeed = $speed;
    }

    if ($accel > $topaccel) {
        $topaccel = $accel;
    }
}

//Helper function for creating a Waypoint, 
//The Placemarker node of the waypoint is passed along with a type specifer,
//which is either
//  START, FINISH, START_P, FINISH_P, or MARKER 
//This will append <name> and <style> for the node, thus the only fields left to
//fill will be <desc> and <Point>
function appendWaypointAuxillaryElements(&$PlacemarkNode, &$dom, $type) {
    //All types have these fields.
    $Style = $dom->createElement('Style');
    $IconStyle = $dom->createElement('IconStyle');
    $scale = $dom->createElement('scale', 1);
    $IconStyle->appendChild($scale);
    $Icon = $dom->createElement('Icon');

    switch ($type) {
    case 'START':
        $href = $dom->createElement('href', $GLOBALS['START_ICON']);
        $PlacemarkNode->appendChild($Style)->appendChild($IconStyle)->appendChild($Icon)->appendChild($href);
        break;
    case 'FINISH':
        $href = $dom->createElement('href', $GLOBALS['FINISH_ICON']);
        $PlacemarkNode->appendChild($Style)->appendChild($IconStyle)->appendChild($Icon)->appendChild($href);
        break;
    case 'START_P':
        $href = $dom->createElement('href', $GLOBALS['START_PATH_ICON']);
        $PlacemarkNode->appendChild($Style)->appendChild($IconStyle)->appendChild($Icon)->appendChild($href);
        break;
    case 'FINISH_P':
        $href = $dom->createElement('href', $GLOBALS['FINISH_PATH_ICON']);
        $PlacemarkNode->appendChild($Style)->appendChild($IconStyle)->appendChild($Icon)->appendChild($href);
        break;
    case 'MARKER':
        $href = $dom->createElement('href', $GLOBALS['MARKER_ICON']);
        $PlacemarkNode->appendChild($Style)->appendChild($IconStyle)->appendChild($Icon)->appendChild($href);
        break;
        
    default :
        echo "AHHHH BUG IN createKML.php AHHHH! Invalid Placemark type!";
    }
}
        

