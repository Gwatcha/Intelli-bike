<?php
error_reporting(-1);
ini_set('display_errors', 'On');

include 'loginPhone.php';
include 'uploadedFilesInfo.php';

$into_table = $_GET['table'];
$file_name = $_GET['type'].'.txt';
$file_path = $rootFolder.$file_name;


$conn = new mysqli($servername, $username, $password, $db);
if ($conn->connect_error) {
    die("Could not connect: " . $conn->connect_error);
}

$sql = "LOAD DATA LOCAL INFILE '".$file_path."' INTO TABLE $into_table FIELDS TERMINATED BY '|'";

$result = $conn->query($sql);

$conn->close(); //All the data is now in the table


?>
