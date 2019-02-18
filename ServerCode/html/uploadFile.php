<?php
include 'uploadedFilesInfo.php';


$file_name = $_GET['type'].'.txt';
$file_path = $rootFolder.$file_name;


if($_SERVER['REQUEST_METHOD']=='POST'){

    try{
        //Move the file into the server
        copy($_FILES['image']['tmp_name'], $file_path);

    }catch(Exception $e){
        die($e->getMessage());
    }
}   
?>
