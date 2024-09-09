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


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DemoUwbApp";

    // Default UWB Ranging configuration parameters
    public static final int UWB_CHANNEL = 5;
    public static final int UWB_PREAMBLE_INDEX = 9;
    public static final int PROFILE_ID = RangingParameters.CONFIG_UNICAST_DS_TWR;
    private static final int SESSION_ID = 0x0A0A0A0A;
    private static final String DEVICE_ADDRESS_STR = "E0:E0";
    public static final byte[] SESSION_KEY = new byte[]{1, 1, 1, 1, 1, 1, 1, 1};

    private static final int NUM_MEASUREMENTS = 10;
    private static final double[] distanceArray = new double[NUM_MEASUREMENTS];
    private static int distanceIndex = 0;
    private static final int DISTANCE_HYSTERESIS = 10;

    private static final double[] azimuthArray = new double[NUM_MEASUREMENTS];
    private static final double[] elevationArray = new double[NUM_MEASUREMENTS];





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.UWB_RANGING) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.UWB_RANGING}, 123);
        }

        UwbManager uwbManager = UwbManager.createInstance(this);
        AtomicReference<Disposable> rangingResultObservable = new AtomicReference<>(null);


        Button initRangingButton = findViewById(R.id.get_values_button);
        Button startRangingButton = findViewById(R.id.communicate_button);
        Switch roleSwitch = findViewById(R.id.is_controller);
        TextView role = findViewById(R.id.role_text_view);

        TextView distanceDisplay = findViewById(R.id.distance_display);
        TextView elevationDisplay = findViewById(R.id.elevation_display);
        TextView azimuthDisplay = findViewById(R.id.azimuth_display);


        new Thread(() -> {
            AtomicReference<UwbClientSessionScope> currentUwbSessionScope = new AtomicReference<>(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());


            roleSwitch.setOnClickListener((v -> {
                if(roleSwitch.isChecked()){
                    role.setText("Controller");
                }
                else
                {
                    role.setText("Controlee");
                }
            }));
            roleSwitch.callOnClick();

            initRangingButton.setOnClickListener((view) -> {
                if (rangingResultObservable.get() != null) {
                    rangingResultObservable.get().dispose();
                    rangingResultObservable.set(null);
                }

                if (roleSwitch.isChecked()) {
                    // CONTROLLER
                    currentUwbSessionScope.set(UwbManagerRx.controllerSessionScopeSingle(uwbManager).blockingGet());
                    UwbControllerSessionScope controllerSessionScope = (UwbControllerSessionScope) currentUwbSessionScope.get();
//                    Log.d(TAG,"Ranging capabilities:isAzimuthalAngleSupported-" + controllerSessionScope.getRangingCapabilities().isAzimuthalAngleSupported());
//                    Log.d(TAG,"Ranging capabilities:isDistanceSupported-" + controllerSessionScope.getRangingCapabilities().isDistanceSupported());
//                    Log.d(TAG,"Ranging capabilities:isElevationAngleSupported-" + controllerSessionScope.getRangingCapabilities().isElevationAngleSupported());
                    MacAddressAlertDialog(view, controllerSessionScope.getLocalAddress().getAddress(), "Controller");
                } else {
                    // CONTROLLEE;
                    currentUwbSessionScope.set(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());
                    UwbControleeSessionScope controleeSessionScope = (UwbControleeSessionScope) currentUwbSessionScope.get();
                    MacAddressAlertDialog(view, controleeSessionScope.getLocalAddress().getAddress(), "Controlee");
                }
            });

            startRangingButton.setOnClickListener((view -> {
                try {
//                    if (isControllerSwitch.isChecked()) {
//                        //CONTROLLER
//                        rangingResultObservable.get();
//                    }
//                    else{
//                        int channelPreamble = Integer.parseInt(preambleInputField.getText().toString());
//                        uwbComplexChannel = new UwbComplexChannel(9, channelPreamble);
//                    }

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

                    rangingResultObservable.set(UwbClientSessionScopeRx.rangingResultsObservable(currentUwbSessionScope.get(), rangingParameters).subscribe(rangingResult -> {
                                if (rangingResult instanceof RangingResult.RangingResultPosition) {
                                    handleRangingResult((RangingResult.RangingResultPosition) rangingResult, distanceDisplay, azimuthDisplay, elevationDisplay);
                                }  else if (rangingResult instanceof RangingResult.RangingResultPeerDisconnected) {

                                    // Display dialog to inform about lost connection
                                    new AlertDialog.Builder(
                                            view.getContext()).setTitle("Controller").
                                            setMessage("Connection lost..").setNeutralButton("OK", (a, b) -> {}).create().show();

                                }
                            }, // onNext
                            System.out::println, // onError
                            () -> Log.d(TAG,"Completed the observing of RangingResults") //onCompleted
                    ));

                } catch (NumberFormatException e) {
                    Log.d(TAG,"Caught Exception: " + e);
                }
            }));
        }).start();
    }

    private static void handleRangingResult(RangingResult.RangingResultPosition rangingResult, TextView distanceDisplay, TextView azimuthDisplay, TextView elevationDisplay) {
        if (rangingResult.getPosition().getDistance() != null) {

            double distance = rangingResult.getPosition().getDistance().getValue();
            distance = movingAverage(distanceArray, distance, distanceIndex);
            distanceIndex++;
            String distanceString = String.format("%.2f", distance);
            distanceDisplay.setText(distanceString);
            Log.d(TAG,"Distance: " + distanceString);
        }
        if (rangingResult.getPosition().getAzimuth() != null) {
            String azimuthString = String.valueOf(rangingResult.getPosition().getAzimuth().getValue());
            azimuthDisplay.setText(azimuthString);
            Log.d(TAG,"Azimuth: " + azimuthString);
        }
        if (rangingResult.getPosition().getElevation() != null) {
            String elevationString = String.valueOf(rangingResult.getPosition().getElevation().getValue());
            elevationDisplay.setText(elevationString);
            Log.d(TAG,"Elevation: " + elevationString);
        }
    }
    public static double movingAverage(double[] arr, double newMeas, int index) {

        // Check if the new measurement is too far from the current average
        double currentAverage = getArrayAverage(arr);
        if(Math.abs(currentAverage - newMeas) > DISTANCE_HYSTERESIS){
            newMeas = currentAverage;
        }

        // Handle the case when the array is not full
        if(index == 0){
            Arrays.fill(arr, newMeas);
            return newMeas;
        }
        else {
            arr[index % arr.length] = newMeas;
        }

        return getArrayAverage(arr);
    }

    private static double getArrayAverage(double[] arr) {
        double sum = 0;
        for (double i : arr) {
            sum += i;
        }
        return sum / arr.length;
    }

    private void MacAddressAlertDialog(View view, byte[] macAddress, String role){
        new AlertDialog.Builder(
                view.getContext()).setTitle(role).
                setMessage("Your MAC Address is: " + toHexLittleEndian(macAddress))
                .setNeutralButton("OK", (a, b) -> {
                }).create().show();
    }

    private String toHexLittleEndian(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (i < bytes.length - 1) {
                hexString.append(":");
            }
            hexString.append(String.format("%02X", bytes[i]));
        }
        return hexString.toString();
    }
}