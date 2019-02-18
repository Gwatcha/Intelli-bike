package ca.ubc.zachrivard.self.test;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by zachr on 2018-03-24.
 */

public class GPSLogger{
    private static final String TAG = GPSLogger.class.getSimpleName();
    private static final String FILE_NAME = "cpen291Log.txt";

    private HashMap<Date, LatLng> log;
    private Context context;

    public GPSLogger(Context context) {
        this.log = new HashMap<>();
        this.context = context;
    }

    public void addLogPoint(Date date, LatLng latLng){
        if(!log.containsKey(date)){
            log.put(date, latLng);
        }
    }


    public void saveLogDataToFile(){
        OutputStreamWriter outputStream;
        try{
            outputStream = new OutputStreamWriter(context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE));
            for(Map.Entry<Date, LatLng> e : log.entrySet()){
                String date = e.getKey().toString();
                String location = e.getValue().toString();
                location = location.substring(location.indexOf("("), location.indexOf(")") + 1);

                String line = date + ", " + location + "\n";
                outputStream.write(line);
            }
            outputStream.close();
        }catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    public String readLogDataFromFile(){
        String logData = "";
        Log.d(TAG, "BEGGING LOG FILE READ");

        try {
            InputStream inputStream = context.openFileInput(FILE_NAME);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString); //Will need to split on ')' before writing to server
                }

                inputStream.close();
                logData = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
        }

        return logData;
    }

    public void printCurrentLog(){
        for(Map.Entry<Date, LatLng> e : log.entrySet()){
            String date = e.getKey().toString();
            String location = e.getValue().toString();
            location = location.substring(location.indexOf("("), location.indexOf(")") + 1);

            String line = date + ", " + location + "\n";
            Log.d(TAG, line);
        }
    }

    public void clear(){
        log.clear();
    }
}
