package com.example.uwbdemoapp;

import com.example.uwbdemoapp.R;

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
        Switch isControllerSwitch = findViewById(R.id.is_controller);

        TextView distanceDisplay = findViewById(R.id.distance_display);
        TextView elevationDisplay = findViewById(R.id.elevation_display);
        TextView azimuthDisplay = findViewById(R.id.azimuth_display);


        new Thread(() -> {
            AtomicReference<UwbClientSessionScope> currentUwbSessionScope = new AtomicReference<>(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());

            initRangingButton.setOnClickListener((view) -> {
                if (rangingResultObservable.get() != null) {
                    rangingResultObservable.get().dispose();
                    rangingResultObservable.set(null);
                }

                currentUwbSessionScope.set(UwbManagerRx.controllerSessionScopeSingle(uwbManager).blockingGet());
                if (isControllerSwitch.isChecked()) {
                    // CONTROLLER
                    UwbControllerSessionScope controllerSessionScope = (UwbControllerSessionScope) currentUwbSessionScope.get();
                    MacAddressAlertDialog(view, controllerSessionScope.getLocalAddress().getAddress());
                } else {
                    // CONTROLLEE;
                    UwbControleeSessionScope controleeSessionScope = (UwbControleeSessionScope) currentUwbSessionScope.get();
                    MacAddressAlertDialog(view, controleeSessionScope.getLocalAddress().getAddress());
                }
            });

            startRangingButton.setOnClickListener((view -> {
                try {
                    if (isControllerSwitch.isChecked())
                        //CONTROLLER
                        rangingResultObservable.get();

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
                                            view.getContext()).setTitle("CONTROLLER / SERVER").
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
            distanceDisplay.setText(String.valueOf((Float.parseFloat(distanceDisplay.getText().toString()) + rangingResult.getPosition().getDistance().getValue()) / 2));
            Log.d(TAG,"Distance: " + rangingResult.getPosition().getDistance().getValue());
        }
        if (rangingResult.getPosition().getAzimuth() != null) {
            azimuthDisplay.setText(String.valueOf((Float.parseFloat(azimuthDisplay.getText().toString()) + rangingResult.getPosition().getAzimuth().getValue()) / 2));
            Log.d(TAG,"Azimuth: " + rangingResult.getPosition().getAzimuth().getValue());
        }
        if (rangingResult.getPosition().getElevation() != null) {
            elevationDisplay.setText(String.valueOf((Float.parseFloat(elevationDisplay.getText().toString()) + rangingResult.getPosition().getElevation().getValue()) / 2));
            Log.d(TAG,"Elevation: " + rangingResult.getPosition().getElevation().getValue());
        }
    }

    private void MacAddressAlertDialog(View view, byte[] macAddress){
        new AlertDialog.Builder(
                view.getContext()).setTitle("CONTROLLER").
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