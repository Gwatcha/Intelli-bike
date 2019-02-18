package ca.ubc.zachrivard.self.test;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;


public class Disclaimer extends AppCompatActivity {
    private static final String TAG = Disclaimer.class.getSimpleName();

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int PERMISSIONS_REQUEST_CALL_ACCESS = 2;
    private static final int PErMISSIONS_REQUEST_READ_EXTERNAL = 3;
    private static final int PErMISSIONS_REQUEST_WRITE_EXTERNAL = 4;
    private static final int PERMISSIONS_REQUEST_INTERNET = 5;
    private boolean mLocationServicesEnabled;
    Context context;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disclaimer);


        context = this;

        // Prompt the user for permissions.
        // Follow the chain in the callback as we
        // can only request one at a time
        getLocationPermission();

        //See if we have location access
        checkLocationServices();

        //Prompt the user for location access
        if (!mLocationServicesEnabled) {
            getLocationServices();
        }

        Button accept = (Button) findViewById(R.id.accept_button);
        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkLocationServices();

                if (!mLocationServicesEnabled) {
                    getLocationServices();
                } else {
                    Intent intent = new Intent(getApplicationContext(), ScanBtActivity.class);
                    startActivity(intent);
                }
            }
        });
    }


    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Fine location permissions granted");
        } else {
            displayLocationSettingsRequest(this);
        }
    }

    /**
     * Prompts the user for permission to write to external storage
     */
    private void getExternalWritePermissions() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "External write permissions granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PErMISSIONS_REQUEST_WRITE_EXTERNAL);
        }
    }

    /**
     * Prompts the user for permission to read from external storage
     */
    private void getExternalReadPermissions() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "External read permissions granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    PErMISSIONS_REQUEST_READ_EXTERNAL);
        }
    }

    /**
     * Prompts the user for permission to use the device's phone
     */
    private void getCallPermission(){


        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Call permissions granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CALL_PHONE},
                    PERMISSIONS_REQUEST_CALL_ACCESS);
        }
    }


    /**
     * Prompts the user for permission to use internet
     */
    private void getInternetPermissions(){

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Call permissions granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    PERMISSIONS_REQUEST_CALL_ACCESS);
        }
    }


    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {

        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Log.d(TAG, "Fine location permissions granted");
                    getCallPermission(); //Next in the chain


                }else{

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Location permission not enabled");
                    builder.setMessage("Location services are required in order for the app to run");
                    builder.setPositiveButton("Try agian", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getLocationPermission();
                        }
                    });
                    builder.setNegativeButton("Quit app", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            System.exit(0); //FORCE QUIT THE APP
                        }
                    });

                }
                break;
            }
            case PERMISSIONS_REQUEST_CALL_ACCESS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Call permissions granted");
                    getExternalReadPermissions(); //Next in the chain
                }else{

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Call services not enabled");
                    builder.setMessage("Calling services are required in order for the app to run");
                    builder.setPositiveButton("Try agian", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getCallPermission();
                        }
                    });
                    builder.setNegativeButton("Quit app", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            System.exit(0); //FORCE QUIT THE APP
                        }
                    });

                }
                break;
            }
            case PErMISSIONS_REQUEST_READ_EXTERNAL: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "External read permissions granted");
                    getExternalWritePermissions(); //Next in the chain
                }else{

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("External Read Permission Denied");
                    builder.setMessage("External reading services are required in order for the app to run");
                    builder.setPositiveButton("Try agian", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getExternalReadPermissions();
                        }
                    });
                    builder.setNegativeButton("Quit app", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            System.exit(0); //FORCE QUIT THE APP
                        }
                    });

                }
                break;
            }
            case PErMISSIONS_REQUEST_WRITE_EXTERNAL: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "External write permissions granted");
                    getInternetPermissions();
                }else{

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Internal Read Permission Denied");
                    builder.setMessage("Internal reading services are required in order for the app to run");
                    builder.setPositiveButton("Try agian", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getExternalWritePermissions();
                        }
                    });
                    builder.setNegativeButton("Quit app", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            System.exit(0); //FORCE QUIT THE APP
                        }
                    });

                }
                break;
            }

            case PERMISSIONS_REQUEST_INTERNET: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Internet permissions granted");
                }else{

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Internet Permission Denied");
                    builder.setMessage("Internet services are required in order for the app to run");
                    builder.setPositiveButton("Try agian", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getInternetPermissions();
                        }
                    });
                    builder.setNegativeButton("Quit app", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            System.exit(0); //FORCE QUIT THE APP
                        }
                    });

                }
                break;
            }
        }
    }


    /**
     * Updates the status of the mLocationServicesEnabled
     * flag based on network and GPS location services
     */
    public void checkLocationServices(){
        LocationManager manager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        try {
            gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {
            Log.d(TAG, "GPS location not enabled");
        }

        try {
            networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception ex) {
            Log.d(TAG, "Network location not enabled");
        }

        if(gpsEnabled || networkEnabled){
            mLocationServicesEnabled = true;
        }
    }

    /**
     * Opens a dialog that prompts the user to enable
     * location services on their device
     */
    public void getLocationServices(){

        // notify user
        final AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle("Enable location services");
        dialog.setMessage("Location services are needed for the app to run. Hit the back button once you enable location services");
        dialog.setPositiveButton("Enable", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                context.startActivity(myIntent);
            }
        });
        dialog.setNegativeButton("Close app", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                finish();
                System.exit(0); //FORCE QUIT THE APP
            }
        });
        dialog.show();
    }


    /**
     * Asks the user to allow location
     * services to be enabled for the app
     * @param context
     */
    private void displayLocationSettingsRequest(Context context) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000 / 2);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.i(TAG, "All location settings are satisfied.");
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings ");

                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result
                            // in onActivityResult().
                            status.startResolutionForResult(Disclaimer.this, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
                        } catch (IntentSender.SendIntentException e) {
                            Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        break;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        displayLocationSettingsRequest(this);
                        break;
                }
                break;
        }
    }
}
