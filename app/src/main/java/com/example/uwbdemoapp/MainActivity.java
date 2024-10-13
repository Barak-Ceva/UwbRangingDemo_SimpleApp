package com.example.uwbdemoapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.uwb.RangingParameters;
import androidx.core.uwb.RangingResult;
import androidx.core.uwb.UwbAddress;
import androidx.core.uwb.UwbClientSessionScope;
import androidx.core.uwb.UwbComplexChannel;
import androidx.core.uwb.UwbControleeSessionScope;
import androidx.core.uwb.UwbControllerSessionScope;
import androidx.core.uwb.UwbDevice;
import androidx.core.uwb.UwbManager;
import androidx.core.uwb.rxjava3.UwbClientSessionScopeRx;
import androidx.core.uwb.rxjava3.UwbManagerRx;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.disposables.Disposable;



import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements BluetoothHelper.BluetoothListener, DialogHelper.DialogListener {

    private Button initRangingButton;
    private Button startRangingButton;
    private Button stopRangingButton;
    private Switch roleSwitch;
    private TextView role;

    private TextView mvaDistanceDisplay;
    private TextView rawDistanceDisplay;


    private static int mOffset_cm = 0;

    private final AtomicReference<Disposable> rangingResultObservable = new AtomicReference<>(null);
    private AtomicReference<UwbClientSessionScope> currentUwbSessionScope;
    private UwbManager uwbManager;

    private static final String TAG = "DemoUwbApp";

    // Default UWB Ranging configuration parameters
    public static final int UWB_CHANNEL = 5;
    public static final int UWB_PREAMBLE_INDEX = 9;
    public static final int PROFILE_ID = RangingParameters.CONFIG_UNICAST_DS_TWR;
    private static final int SESSION_ID = 0x0A0A0A0A;
    private static final String DEVICE_ADDRESS_STR = "E0:E0";
    public static final byte[] SESSION_KEY = new byte[]{1, 1, 1, 1, 1, 1, 1, 1};

    private static final int MVA_NUM_OF_MEASUREMENTS = 10;
    private static final double[] distanceMvaArray = new double[MVA_NUM_OF_MEASUREMENTS];
    private static int mvaIndex = 0;
    private static final int DISTANCE_HYSTERESIS_METERS = 2;
    private static boolean isFirstMeasurement = true;

    private static final double[] azimuthArray = new double[MVA_NUM_OF_MEASUREMENTS];
    private static final double[] elevationArray = new double[MVA_NUM_OF_MEASUREMENTS];


    private BluetoothHelper bluetoothHelper;
    private DialogHelper dialogHelper;
    private ArrayList<BluetoothDevice> pairedDevicesList;
    private ArrayList<String> pairedDevicesNames;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        bluetoothHelper = new BluetoothHelper(this);  // Initialize Bluetooth helper
        dialogHelper = new DialogHelper(this);        // Initialize Dialog helper
        pairedDevicesList = new ArrayList<>();        // Paired Bluetooth devices list
        pairedDevicesNames = new ArrayList<>();       // Paired devices names list for displaying

        // Button to show paired devices in a dialog
        Button pairedDevicesButton = findViewById(R.id.pairedDevicesButton);
        pairedDevicesButton.setOnClickListener(v -> bluetoothHelper.listPairedDevices());


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.UWB_RANGING) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.UWB_RANGING}, 123);
        }

        uwbManager = UwbManager.createInstance(this);

        InitUIElements();


        manageUWBSession();
    }


    private void setupInitRangingButton(AtomicReference<UwbClientSessionScope> currentUwbSessionScope) {
        initRangingButton.setOnClickListener(view -> {
            if (rangingResultObservable.get() != null) {
                rangingResultObservable.get().dispose();
                rangingResultObservable.set(null);
            }

            if (roleSwitch.isChecked()) {
                // CONTROLLER
                // Creating the controller session scope
                currentUwbSessionScope.set(UwbManagerRx.controllerSessionScopeSingle(uwbManager).blockingGet());
                UwbControllerSessionScope controllerSessionScope = (UwbControllerSessionScope) currentUwbSessionScope.get();
                MacAddressAlertDialog(view, controllerSessionScope.getLocalAddress().getAddress(), "Controller");
            } else {
                // CONTROLLEE
                currentUwbSessionScope.set(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());
                UwbControleeSessionScope controleeSessionScope = (UwbControleeSessionScope) currentUwbSessionScope.get();
                MacAddressAlertDialog(view, controleeSessionScope.getLocalAddress().getAddress(), "Controlee");
            }
            startRangingButton.setEnabled(true);
            initRangingButton.setEnabled(false);
        });
    }



    private void setupStartRangingButton(AtomicReference<UwbClientSessionScope> currentUwbSessionScope) {
        startRangingButton.setOnClickListener(view -> {
            try {
                UwbComplexChannel uwbComplexChannel = new UwbComplexChannel(UWB_CHANNEL, UWB_PREAMBLE_INDEX);
                UwbDevice uwbDevice = new UwbDevice(new UwbAddress(DEVICE_ADDRESS_STR));
                RangingParameters rangingParameters = new RangingParameters(
                        PROFILE_ID,
                        SESSION_ID,
                        0,
                        SESSION_KEY,
                        null,
                        uwbComplexChannel,
                        Collections.singletonList(uwbDevice),
                        RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                );

                rangingResultObservable.set(UwbClientSessionScopeRx.rangingResultsObservable(currentUwbSessionScope.get(), rangingParameters).subscribe(
                        rangingResult -> {
                            if (rangingResult instanceof RangingResult.RangingResultPosition) {
                                handleRangingResult((RangingResult.RangingResultPosition) rangingResult, mvaDistanceDisplay, rawDistanceDisplay);
                            } else if (rangingResult instanceof RangingResult.RangingResultPeerDisconnected) {
//                                // Display dialog to inform about lost connection
//                                new AlertDialog.Builder(view.getContext()).setTitle("Controller")
//                                        .setMessage("Connection lost..").setNeutralButton("OK", (a, b) -> {}).create().show();

                                stopRanging(rangingResultObservable);
                            }
                        }, // onNext
                        System.out::println, // onError
                        () -> Log.d(TAG, "Completed the observing of RangingResults") // onCompleted
                ));

                stopRangingButton.setEnabled(true);
                startRangingButton.setEnabled(false);

            } catch (NumberFormatException e) {
                Log.d(TAG, "Caught Exception: " + e);
            }
        });
    }


    private void setupStopRangingButton() {
        stopRangingButton.setOnClickListener(view -> {
            showRangingStopAlertDialog();
        });
    }


    private void manageUWBSession() {
        new Thread(() -> {
            currentUwbSessionScope = new AtomicReference<>(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());

            setupInitRangingButton(currentUwbSessionScope);
            setupStartRangingButton(currentUwbSessionScope);
            setupStopRangingButton();
        }).start();
    }

    private void InitUIElements() {
        initRangingButton = findViewById(R.id.init_ranging_button);
        startRangingButton = findViewById(R.id.start_ranging_button);
        stopRangingButton = findViewById(R.id.stop_ranging_button); // New button
        roleSwitch = findViewById(R.id.is_controller);
        role = findViewById(R.id.role_text_view);

        mvaDistanceDisplay = findViewById(R.id.distance_display);
        rawDistanceDisplay = findViewById(R.id.raw_distance_display);



    }


    @Override
    protected void onPause() {
        super.onPause();
        stopRanging(rangingResultObservable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRanging(rangingResultObservable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRanging(rangingResultObservable);
    }

    private void showRangingStopAlertDialog() {
         new AlertDialog.Builder(MainActivity.this)
                .setTitle("Stop Ranging")
                .setMessage("Are you sure you want to stop the ranging?")
                .setPositiveButton("Yes", (dialog, which) -> stopRanging(rangingResultObservable))
                .setNegativeButton("No", null)
                .show();
    }

    private void stopRanging(AtomicReference<Disposable> rangingResultObservable) {

        // Dispose the observable if it's active
        if (rangingResultObservable.get() != null) {
            rangingResultObservable.get().dispose();
            rangingResultObservable.set(null);

            // Nullify the session scope reference to indicate the session is stopped
            currentUwbSessionScope.set(null);

            // Optionally show a confirmation dialog or log the stopping of the session
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Ranging Stopped")
                    .setMessage("Ranging has been stopped.")
                    .setNeutralButton("OK", null)
                    .show();
            initRangingButton.setEnabled(true);
            stopRangingButton.setEnabled(false);

            Log.d(TAG, "Ranging stopped.");
        } else {
            Log.d(TAG, "No active ranging session to stop.");
        }
    }

    private static void handleRangingResult(RangingResult.RangingResultPosition rangingResult, TextView mvaDistanceDisplay, TextView rawDistanceDisplay) {
        if (rangingResult.getPosition().getDistance() != null) {

            double rawDistance;
            double mvaDistance;

            rawDistance = rangingResult.getPosition().getDistance().getValue();
            rawDistance -= (double) mOffset_cm / 100;

            // Initialize all array cells with the first measurement
            if (isFirstMeasurement) {
                isFirstMeasurement = false;
                Arrays.fill(distanceMvaArray, rawDistance);
                mvaDistance = rawDistance;

            // Update the moving average with the new measurement if it is valid
            } else {
                if (isValidDistance(rawDistance)) {
                    mvaDistance = CalculateMovingAverage(distanceMvaArray, rawDistance, mvaIndex);
                } else {
                    return;
                }
            }

            mvaIndex = (mvaIndex + 1) % MVA_NUM_OF_MEASUREMENTS;
            updateDistanceUI(mvaDistanceDisplay, rawDistanceDisplay, mvaDistance, rawDistance);

        }
        if (rangingResult.getPosition().getAzimuth() != null) {
            String azimuthString = String.valueOf(rangingResult.getPosition().getAzimuth().getValue());
            rawDistanceDisplay.setText(azimuthString);
            Log.d(TAG,"Azimuth: " + azimuthString);
        }
    }

    private static void updateDistanceUI(TextView mvaDistanceDisplay, TextView rawDistanceDisplay, double MvaDistance, double rawDistance) {
        String rawDistanceString;
        String mvaDistanceString;
        mvaDistanceString = String.format("%.2f", MvaDistance);
        mvaDistanceDisplay.setText(mvaDistanceString);
        Log.d(TAG,"MVA Distance: " + mvaDistanceString);

        rawDistanceString = String.format("%.2f", rawDistance);
        rawDistanceDisplay.setText(rawDistanceString);
        Log.d(TAG,"RAW Distance: " + rawDistanceString);
    }

    private static boolean isValidDistance(double distance){

        // Adjust newMeas if it deviates too much from current average
        boolean condition = Math.abs(getArrayAverage(distanceMvaArray) - distance) < DISTANCE_HYSTERESIS_METERS;
        Log.d(TAG,"Invalid Distance: " + distance + ". "
                + "Current Average: " + getArrayAverage(distanceMvaArray) + ". "
                + "Hysteresis: " + DISTANCE_HYSTERESIS_METERS
        );
        return condition;
    }

    public static double CalculateMovingAverage(double[] arr, double newMeas, int index) {

        // Update array element at index position (circular)
        arr[index] = newMeas;

        // Return the updated average
        return getArrayAverage(arr);
    }

    private static double getArrayAverage(double[] arr) {
        double sum = 0;
        for (double i : arr) {
            sum += i;
        }
        return sum / arr.length;

//        return Arrays.stream(arr).sum() / arr.length;
    }

    private void MacAddressAlertDialog(View view, byte[] macAddress, String role){
        new AlertDialog.Builder(
                view.getContext()).setTitle(role).
                setMessage("Your MAC Address is: " + Utils.convertBytesToHexLittleEndian(macAddress))
                .setNeutralButton("OK", (a, b) -> {
                }).create().show();
    }


    @Override
    public void onPairedDevicesAvailable(ArrayList<BluetoothDevice> pairedDevices) {
        pairedDevicesNames.clear();
        pairedDevicesList.clear();

        pairedDevicesList.addAll(pairedDevices);
        for (BluetoothDevice device : pairedDevices) {
            pairedDevicesNames.add(device.getName() + "\n" + device.getAddress());
        }

        // Show the paired devices dialog
        dialogHelper.showPairedDevicesDialog(pairedDevicesNames, this);
    }

    @Override
    public void onDeviceSelected(String deviceName) {
        // Find the selected BluetoothDevice based on the name and address and connect
        for (BluetoothDevice device : pairedDevicesList) {
            if ((device.getName() + "\n" + device.getAddress()).equals(deviceName)) {
                bluetoothHelper.connectToDevice(device);  // Call connectToDevice here
                break;
            }
        }
    }


    @Override
    public void onDeviceConnected(String deviceName) {
        Toast.makeText(this, "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed() {
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDataSent() {
        Toast.makeText(this, "Data Sent", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected() {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
    }

}