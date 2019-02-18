package ca.ubc.zachrivard.self.test;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bluecreation.melody.SppService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static ca.ubc.zachrivard.self.test.JSONParser.decodePoly;
import static ca.ubc.zachrivard.self.test.ScanBtActivity.EXTRA_DEVICE_ADDRESS;
import static ca.ubc.zachrivard.self.test.ScanBtActivity.EXTRA_DEVICE_NAME;

/**
 * An activity that displays a map showing the place at the device's current location.
 */
public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {
    private boolean SEND_REQS = true; //Disable this if you don't want to do GPS

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private static final long LOG_DELAY = 5000; //5 seconds
    private static String PHONE_CONTACT = "17787132535"; //Thanks Brennan
    private static String HOME_ADDRESS = "2356 Main Mall"; //Macleod Building

    private static String TYPE_START = "START";
    private static String TYPE_FINISH = "FINISH";
    private static String TYPE_PATH = "PATH";
    private static String TYPE_START_P = "START_P";
    private static String TYPE_FINISH_P = "FINISH_P";
    private static String TYPE_PAUSE = "PAUSE";

    String currentType = TYPE_START;
    static int rideNumber;
    long rideStartTime;



    //URLs to execute
    String UPLOAD_URL = "http://intellibikeubc.com/uploadFile.php?type="; //need to append a type ("history", or "log");
    //Data logging
    String TO_TABLE_URL = "http://intellibikeubc.com/loadFileIntoTable.php?type="; //need to append a table and a type
    String makeKMLURL = "http://intellibikeubc.com/createKMLwithID.php?id="; //need to append the id

    private final Context context = this;
    private  GoogleMap mMap;
    private CameraPosition mCameraPosition;


    private volatile boolean isLogging = true;



    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;


    // The geographical location where the device is currently located.
    private Location mLastKnownLocation;
    private LatLng currentLatLng;

    //String representation of the route start and end points
    private String startLocation;
    private String endLocation;

    //Line that will show the route on the map
    private Polyline polyline;

    //Bluetooth stuff
    SppService mSppService;

    BluetoothAdapter mBluetoothAdapter;
    String deviceAddress, deviceName;

    ProgressDialog btDialog;


    //Data logging
    //Path to the log file
    public String path = Environment
            .getExternalStorageDirectory()
            .getAbsolutePath() + "/intellibike";
    File directory;
    File logFile;
    File rideNumberFile;
    File rideHistoryFile;



    /**
     * PRECONDITION
     *      Location services are enabled and turned on
     *      Location permissions are granted
     *
     * Both of these conditions are forced to be satisfied
     * by the Disclaimer class as it will not let users
     * through without it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        directory = new File(path);
        logFile = new File(path + "/datalog.txt");
        rideNumberFile = new File(path + "/ridenum.txt");
        rideHistoryFile = new File(path + "/ridehistory.txt");

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Bundle extras = getIntent().getExtras();

        if (extras!= null) {

            btDialog = new ProgressDialog(context);
            btDialog.setTitle("Connecting to Device");
            btDialog.setMessage("Please wait...");
            btDialog.setIndeterminate(true);
            btDialog.show();


            deviceAddress = extras.getString(EXTRA_DEVICE_ADDRESS);
            deviceName = extras.getString(EXTRA_DEVICE_NAME);

            mSppService = SppService.getInstance();
            mSppService.registerListener(sppListener);

            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
            try {
                mSppService.connect(device);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }


        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);

        //will only init map once BT is connected

        final Button endRideButton = (Button) findViewById(R.id.endRideButton);
        endRideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(endRideButton.getText().toString().equals(getString(R.string.start_ride))){
                    endRideButton.setText(getString(R.string.end_ride));
                    isLogging = true;
                    startRide();
                    currentType = TYPE_START;
                    initPiData();
                }else {
                    endRideButton.setText(getString(R.string.start_ride));
                    currentType = TYPE_FINISH;
                    endRide();
                    initPiData();
                }
            }
        });

        final Button changeStateButton = (Button) findViewById(R.id.pauseRideButton);
        changeStateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(currentType.equals(TYPE_PATH)){ //Pause
                    currentType = TYPE_FINISH_P;
                    initPiData();
                    changeStateButton.setText(getString(R.string.resume_ride));
                }else if (currentType.equals(TYPE_PAUSE)){ //Unpause
                    currentType = TYPE_START_P;
                    initPiData();
                    changeStateButton.setText(getString(R.string.pause_ride));
                }
            }
        });

    }


    /**
     * Creates and initializes all of the objects needed for the
     * Google Maps API to function correctly.
     */
    protected void initializeMap(){
        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);

        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    /**
     * Begins the ride sequence by activating a
     * GPS logger if there was no destination
     * selected.
     */
    private void startRide(){
        updateRideNumberAndTime();
        isLogging = true;
        runLoggingThread();
    }


    /**
     * Begins the end ride sequence including
     */
    private void endRide(){
        isLogging = false;
    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return true;
    }

    /**
     * Handles a click on the menu option to get a place.
     * @param item The menu item to handle.
     * @return Boolean.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){

            case R.id.go_home:
                goHome();
                break;

            case R.id.change_home:
                updateHomeAddress();
                break;

            case R.id.call_contact:
                callContact();
                break;

            case R.id.change_phone:
                updatePhoneNumber();
                break;

            case R.id.new_dest:
                makeNewRoute();
                break;

            case R.id.upload_ride:
                uploadLogToServer();
                break;
            default:
                return false; //Failed
        }
        return true;
    }

    /**
     * Immediately begins a route from
     * the current location back to
     * HOME_ADDRESS
     */
    private void goHome(){
        startLocation = ""; //Will default to use current location
        endLocation = HOME_ADDRESS;

        extractDesiredRoute();
    }

    /**
     * Generates a dialog into which users can
     * update their home address
     */
    private void updateHomeAddress(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Home Address");

        // Set up the input
        final EditText input = new EditText(context);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(getString(R.string.update_field));
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                HOME_ADDRESS = input.getText().toString().trim();
                Toast.makeText(context, "Home address successfully updated", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    /**
     * Calls the emergency contact
     */
    private void callContact(){
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:"+ PHONE_CONTACT));
        try {
            startActivity(callIntent);
        }catch (SecurityException e){
            Log.e(TAG, e.getMessage());
        }

    }

    /**
     * Generates a dialog into which users can
     * update their emergency cell number
     */
    private void updatePhoneNumber(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Emergency Contact");

        // Set up the input
        final EditText input = new EditText(context);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(getString(R.string.update_field));
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PHONE_CONTACT = input.getText().toString().trim();
                PHONE_CONTACT = PHONE_CONTACT.replaceAll("-", "");
                PHONE_CONTACT = PHONE_CONTACT.replaceAll("\\.", "");
                PHONE_CONTACT = PHONE_CONTACT.replaceAll("\\(", "");
                PHONE_CONTACT = PHONE_CONTACT.replaceAll("\\)", "");
                Toast.makeText(context, "Emergency contact successfully updated", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();

    }


    /**
     * Opens a dialog for the user to input
     * a new start and end destination
     */
    private void makeNewRoute(){

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Plan Route");

        LinearLayout layout = new LinearLayout(context);
        View child = getLayoutInflater().inflate(R.layout.new_route_dialog, null);
        layout.addView(child);


        // Add a TextView here for the startLocation
        final EditText origin = (EditText) child.findViewById(R.id.origin_field);
        // Add another TextView here for the endLocation
        final EditText destination= (EditText) child.findViewById(R.id.dest_field);

        builder.setView(layout); // Again this is a set method, not add

        builder.setPositiveButton("Go!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startLocation = origin.getText().toString();
                endLocation = destination.getText().toString();

                extractDesiredRoute();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Do nothing :(
            }
        });

        builder.show();

    }


    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.map), false);

                TextView title = ((TextView) infoWindow.findViewById(R.id.title));
                title.setText(marker.getTitle());

                TextView snippet = ((TextView) infoWindow.findViewById(R.id.snippet));
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        updateDeviceLocation();

        //Begin plotting a new route
        makeNewRoute();
    }


    /**
     * Gets the current location of the device,
     * and positions the map's camera.
                                    *
     */
    private void updateDeviceLocation(){

        try{
            Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
            locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    if(task.isSuccessful()){
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            Location location = task.getResult();
                            currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM));

                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                            currentLatLng = mDefaultLocation;

                        }
                    }
                }
            });
        }catch (SecurityException e){
            Log.e(TAG, e.getMessage());
        }

    }


    /**
     * Makes the URL based on the start and end points and
     * calls to send off the URL via HTTPS
     *
     * Requires:
     *              startLocation and endLocation have been updated
     *              to the desired string representation of the
     *              origin/destination
     *
     */
    private void extractDesiredRoute() {

        //If any of the fields are blank, use the current
        //location to fill them

        if(startLocation.length() < 1) {
            startLocation = currentLatLng.toString(); //In the form of (lat,lng)
            startLocation = startLocation.substring(startLocation.indexOf('(') + 1, startLocation.lastIndexOf(')'));
        }

        if(endLocation.length() < 1) {
            endLocation = currentLatLng.toString(); //In the form of (lat,lng)
            endLocation = endLocation.substring(endLocation.indexOf('(') + 1, endLocation.lastIndexOf(')'));
        }

        //Make the URL for the API call
        String url = makeURL(startLocation, endLocation);
        Log.d(TAG, url);

        if(SEND_REQS)
            new connectAsyncTask(url).execute(); //Make the request
    }


    /**
     * Class that will make the HTTPS requests to Google Directions
     */

    private class connectAsyncTask extends AsyncTask<Void, Void, String> {
        private ProgressDialog progressDialog;
        String url;

        connectAsyncTask(String urlPass) {
            url = urlPass;
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Fetching route, Please wait...");
            progressDialog.setIndeterminate(true);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... params) {
            JSONParser jParser = new JSONParser();
            String json = jParser.getJsonStringFromUrl(url);
            return json;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            progressDialog.hide();
            if (result != null) {
                drawPath(result);
            }
        }
    }


    /**
     * Draws a line on the map representing the best route
     * to take to get from origin to destination.
     *
     * Begins the data logger
     *
     * @param result - the String representation of the line
     */
    public void drawPath(String result) {
        if(SEND_REQS) {
            if (polyline != null) {
                mMap.clear();
            }

            startRide();

            try {
                // Tranform the string into a json object
                final JSONObject json = new JSONObject(result);
                JSONArray routeArray = json.getJSONArray("routes");
                JSONObject routes = routeArray.getJSONObject(0);
                JSONObject overviewPolylines = routes.getJSONObject("overview_polyline");
                String encodedString = overviewPolylines.getString("points");

                List<LatLng> list = decodePoly(encodedString); //Decode the line

                PolylineOptions options = new PolylineOptions().color(Color.BLUE);
                for (int i = 0; i < list.size(); i++) {
                    LatLng point = list.get(i);
                    options.add(point);
                }

                polyline = mMap.addPolyline(options);

                //Get the exact start and end locations
                JSONObject legsArray = routes.getJSONArray("legs").getJSONObject(0);

                JSONObject endLoca = legsArray.getJSONObject("end_location");
                String endAddress = legsArray.getString("end_address");
                endAddress = endAddress.substring(0, endAddress.indexOf(','));
                String endLat = endLoca.getString("lat");
                String endLng = endLoca.getString("lng");

                JSONObject startLoc = legsArray.getJSONObject("start_location");
                String startAddress = legsArray.getString("start_address");
                startAddress = startAddress.substring(0, startAddress.indexOf(','));
                String startLat = startLoc.getString("lat");
                String startLng = startLoc.getString("lng");

                LatLng originLatLng = new LatLng(Double.valueOf(startLat), Double.valueOf(startLng));
                LatLng destLatLng = new LatLng(Double.valueOf(endLat), Double.valueOf(endLng));

                plotLocationOnMap(originLatLng, startAddress);
                plotLocationOnMap(destLatLng, endAddress);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    /**
     * Adds a marker on the map at the desired location
     *
     * @param loc - Location on the map where the marker will be placed
     * @param title - Title for the marker when clicked on
     */
    private void plotLocationOnMap(LatLng loc, String title){
        if(mMap == null)
            return;


        if(SEND_REQS) {
            // Add a marker for the selected place, with an info window
            // showing information about that place.
            mMap.addMarker(new MarkerOptions()
                    .title(title)
                    .position(loc));
        }
    }


    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        if(SEND_REQS) {
            try {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } catch (SecurityException e) {
                Log.e("Exception: %s", e.getMessage());
            }
        }
    }


    /**
     *
     * @param start - the starting location of the ride
     * @param end - the end location of the ride
     * @return the String representation of the URL that needs to be send to
     *              determine the best route to take
     */
    private String makeURL(String start, String end){
        StringBuilder url = new StringBuilder();
        start = start.trim().replaceAll("\\s+", "+"); //Format for google API
        end = end.trim().replaceAll("\\s+", "+");

        url.append("https://maps.googleapis.com/maps/api/directions/json");
        url.append("?origin=");// from
        url.append(start);
        url.append("&destination=");// to
        url.append(end);
        url.append("&mode=bicycling"); //We are biking
        url.append("&key="); //Add our API key
        url.append(getString(R.string.API_KEY));

        return url.toString();
    }


    /**
     *Bluetooth listener that will write the retrieved
     * data from the Pi back into the log file
     */
    SppService.Listener sppListener = new SppService.Listener() {
        @Override
        public void onStateChanged(final SppService.ConnectionState state) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(state.equals(SppService.ConnectionState.STATE_NONE)){
                        finish();
                    }
                }
            });
        }

        @Override
        public void onRemoteDeviceConnected(final String deviceName) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(btDialog != null && btDialog.isShowing()) {
                        btDialog.dismiss();
                    }
                    initializeMap();
                }
            });
        }

        @Override
        public void onDataReceived(final byte[] data, final int length) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    String rideData = new String(data, 0, length);
                    /*
                        TEXT file format (spaces added for clarity, there will be none in the file)

                        rideNum | "TYPE" | COOR_lat | COOR_lng | time | COOR_alt | speed | distance | acceleration |
                        |____________________________________________|_______________________________________________|
                                                |                                               |
                                          Supplied by App                                 Supplied by Pi
                     */
                    String appData = String.valueOf(rideNumber) + "|" + currentType + "|" + getLatitude()
                            + "|" + getLongitude() + "|" + String.valueOf(System.currentTimeMillis()/1000);

                    rideData = appData + rideData;
                    Log.d("Data update... ", rideData);
                    //Write it into a file

                    if(!currentType.equals(TYPE_PAUSE)) { //If paused do not log
                        writeDataToLog(rideData);
                    }

                    if(currentType.equals(TYPE_START) || currentType.equals(TYPE_START_P)){ //Next will always be a normal path
                        currentType = TYPE_PATH;
                    }

                    if(currentType.equals(TYPE_FINISH_P)){ //We are going into a pause
                        currentType = TYPE_PAUSE;
                    }


                }
            });

        }

        @Override
        public void onConnectionLost() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Connection Lost",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }

        @Override
        public void onConnectionFailed() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),
                            "Connection Failed", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }
    };

    private void writeDataToLog(String data){
        FileOutputStream outputStream;
        try{
            outputStream = new FileOutputStream(logFile, true);
            outputStream.write((data + "\n").getBytes());
            outputStream.close();
        }catch(IOException e){
            e.printStackTrace();
        }

        Log.d(TAG, "Data write to file!");

    }



    /**
     * Will begin a separate thread to log GPS every LOG_DELAY ms.
     *
     * The log will contain Lat/Lng and a timestamp
     */
    private void runLoggingThread(){

        if(SEND_REQS) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isLogging) {
                        //Init the data request
                        initPiData();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                updateDeviceLocation(); //This is a really slow thing...
                            }
                        }).start();

                        try {
                            Thread.sleep(LOG_DELAY);
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Logging thread was interrupted");
                        }
                    }
                }
            }).start();
        }

    }

    /**
     * Initiate the echange of data between the
     * phone and the Pi
     */
    private void initPiData(){
        mSppService.send(("Data").getBytes());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop the melody services
        if (mSppService != null) {
            mSppService.unregisterListener(sppListener);
            mSppService.stop();
            mSppService = null;
        }
    }


    /**
     * Reads the current ride number from a file
     * and increments it for the next ride
     */
    private void updateRideNumberAndTime(){
        FileOutputStream outputStream;
        FileInputStream inputStream;
        String contents = "";


        if(!rideNumberFile.exists()) { //Init the file if this is the first time
            Log.d("File did not exist", "Weird");
            try {
                outputStream = new FileOutputStream(rideNumberFile, false);
                Log.d("Writing back... ", (String.valueOf(1)));
                outputStream.write((String.valueOf(1)).getBytes());
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try{ //Read the current ride number
            inputStream = new FileInputStream(rideNumberFile);
            InputStreamReader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);

            contents = bufferedReader.readLine(); //There will only be one line for ride num
            Log.d(TAG, "Read number from file is... " + contents);
            rideNumber = Integer.valueOf(contents); //Set the ride number
            rideStartTime = System.currentTimeMillis() / 1000; //The start time of the ride
        }catch(IOException e){
            e.printStackTrace();
        }

        //Log this as a ride (ride_id, time_in_epoch_seconds)
        try {
            outputStream = new FileOutputStream(rideHistoryFile, false);
            Log.d("Add ride to history...",(String.valueOf(rideNumber) + "|" + String.valueOf(rideStartTime)));
            outputStream.write((String.valueOf(rideNumber) + "|" + String.valueOf(rideStartTime)).getBytes());
        }catch (IOException e){
            e.printStackTrace();
        }

        try { //Update the ride number for the next one
            outputStream = new FileOutputStream(rideNumberFile, false);
            Log.d("Writing back... ", (String.valueOf(rideNumber + 1)));
            outputStream.write((String.valueOf(rideNumber + 1)).getBytes());
            outputStream.close();
            outputStream = new FileOutputStream(logFile, false);
            Log.d("Clearing log... ", "wow");
            outputStream.write(("").getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("Current ride number:", String.valueOf(rideNumber));
        Log.d("Current ride ST:", String.valueOf(rideStartTime));
    }


    /**     *
     * @return The string representation of the current
     * latitude
     */
    private String getLatitude(){
        return String.valueOf(currentLatLng.latitude);
    }


    /**     *
     * @return The string representation of the current
     * longitude
     */
    private String getLongitude(){
        return String.valueOf(currentLatLng.longitude);
    }


    /**
     * Pushes the log files to the server
     * and calls scripts to make the KML
     * based off of the log
     */
    private boolean uploadLogToServer (){

        String historyURL = TO_TABLE_URL + "history&table=ride_history";
        String logURL = TO_TABLE_URL + "log&table=logged_data";


        //Add all of the HTTP requests to be sent in sequential order
        ArrayList<ScriptExecutor> list = new ArrayList<>();
        list.add( new ScriptExecutor(UPLOAD_URL + "history", path + "/ridehistory.txt", context)); //Send the ride history)
        list.add( new ScriptExecutor(UPLOAD_URL + "log", path + "/datalog.txt", context)); //Send the log)
        list.add( new ScriptExecutor(historyURL, null, context)); //Upload the history into a table);
        list.add( new ScriptExecutor(logURL, null, context)); //Upload the log into a table);
        Log.d("RIDE NUMBER PRE ADD", String.valueOf(rideNumber));
        list.add( new ScriptExecutor(makeKMLURL + String.valueOf(rideNumber), null, context)); //Make all the kmls);

        //Pause to allow all the objects to me made
        try {
            Thread.sleep(1000);
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        //Build the caller
        ScriptExecutor.multiScript(list);

        try {
            Thread.sleep(1000);
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        //Execute all of the scripts
        ScriptExecutor.run();

        return false; //return httpResult = 200;
    }
}
