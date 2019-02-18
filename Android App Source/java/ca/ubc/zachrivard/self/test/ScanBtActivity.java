package ca.ubc.zachrivard.self.test;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import java.util.Set;

@TargetApi(25)
public class ScanBtActivity extends AppCompatActivity {

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    public static String EXTRA_DEVICE_NAME = "device_name";
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int BT_SCAN_TIME = 15000; //15 seconds

    // Member fields
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter, mScannedDevicesArrayAdapter;
    Set<BluetoothDevice> pairedDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_bt);

        Toolbar toolbar = (Toolbar) findViewById(R.id.btscantoolbar);
        toolbar.setTitle("Classic Bluetooth Scan");
        setActionBar(toolbar);

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // If BT is not on, request that it be enabled.
        if (!mBtAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);


        // Initialize the button to perform device discovery
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mScannedDevicesArrayAdapter.clear();
                mScannedDevicesArrayAdapter.notifyDataSetChanged();
                Toast.makeText(getApplicationContext(), String.format("Device Scan enabled for %d seconds", BT_SCAN_TIME/1000),Toast.LENGTH_LONG).show();
                mBtAdapter.startDiscovery();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(mBtAdapter.isDiscovering()) {
                            mBtAdapter.cancelDiscovery();
                            Toast.makeText(getApplicationContext(), "Scan Finished", Toast.LENGTH_SHORT).show();
                        }
                    }
                },BT_SCAN_TIME);
            }
        });

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        mPairedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);
        mScannedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

        // Find and set up the ListView for paired devices
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);
        updatePairedDevices();

        // Find and set up the ListView for scanned devices devices
        ListView scannedListView = (ListView) findViewById(R.id.new_devices);
        scannedListView.setAdapter(mScannedDevicesArrayAdapter);
        scannedListView.setOnItemClickListener(mDeviceClickListener);
    }

    protected void updatePairedDevices(){
        // Get a set of currently paired devices
        pairedDevices = mBtAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            mPairedDevicesArrayAdapter.clear();
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = "NO PAIRED DEVICES";
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
            unregisterReceiver(mReceiver);
        }
    }

    // The on-click listener for all devices in the ListViews
    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            if(mBtAdapter.isDiscovering()) {
                // Cancel discovery because it's costly and we're about to connect
                mBtAdapter.cancelDiscovery();
                Toast.makeText(getApplicationContext(), "Scan Terminated",Toast.LENGTH_SHORT).show();
            }

            if(av.getId() == R.id.new_devices){
                Toast.makeText(getApplicationContext(),"Please Accept Pairing Request", Toast.LENGTH_SHORT).show();
            }

            // Get the device MAC address, and name
            String info = ((TextView) v).getText().toString();
            //address is last 17 chars of the view
            String address = info.substring(info.length() - 17);
            //name is up to the \n
            String name = info.substring(0, info.indexOf('\n') - 1);

            // Create the result Intent and include the MAC address + name
            Intent intent = new Intent(getApplicationContext(), MapsActivity.class);
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
            intent.putExtra(EXTRA_DEVICE_NAME, name);
            startActivity(intent);
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode != Activity.RESULT_OK) {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this, "Bluetooth must be enabled",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                else {
                    //Make sure the user can see all the paired devices
                    updatePairedDevices();
                }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                if(deviceName == null){
                    deviceName = "Device Name Not Available";
                }
                String deviceHardwareAddress = device.getAddress(); // MAC address
                findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
                mScannedDevicesArrayAdapter.add(deviceName + "\n" + deviceHardwareAddress);
                mScannedDevicesArrayAdapter.notifyDataSetChanged();
            }
        }
    };
}

