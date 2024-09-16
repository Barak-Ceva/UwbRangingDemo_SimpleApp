package com.example.uwbdemoapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static long distanceIndex = 0;
    private static final int DISTANCE_HYSTERESIS_METERS = 10;

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
        TextView rawDistanceDisplay = findViewById(R.id.raw_distance_display);
        LineChart lineChart = findViewById(R.id.line_chart);
        Button sendEmailButton = findViewById(R.id.send_email_button);

        initChart(lineChart);

        sendEmailButton.setOnClickListener(v -> {
            try {
                File csvFile = generateCSVFile(lineChart);
                sendEmail(csvFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

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
                                    handleRangingResult((RangingResult.RangingResultPosition) rangingResult, distanceDisplay, rawDistanceDisplay, lineChart);
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

    private static void handleRangingResult(RangingResult.RangingResultPosition rangingResult, TextView mvaDistanceDisplay, TextView rawDistanceDisplay, LineChart lineChart) {
        if (rangingResult.getPosition().getDistance() != null) {
//            Log.d("q1w2e3r4","Tick");

            double MvaDistance;
            String mvaDistanceString;
            String rawDistanceString;
            double rawDistance = rangingResult.getPosition().getDistance().getValue();

            // Initialize array with newMeas if index is 0
            if (0 == distanceIndex) {
                Arrays.fill(distanceArray, rawDistance);
                MvaDistance = rawDistance;
            }
            else{
                MvaDistance = movingAverage(distanceArray, rawDistance, distanceIndex);
            }

            distanceIndex++;
            mvaDistanceString = String.format("%.2f", MvaDistance);
            mvaDistanceDisplay.setText(mvaDistanceString);
            Log.d(TAG,"MVA Distance: " + mvaDistanceString);

            rawDistanceString = String.format("%.2f", rawDistance);
            rawDistanceDisplay.setText(rawDistanceString);
            Log.d(TAG,"RAW Distance: " + rawDistanceString);

            addValuesToGraph(lineChart, distanceIndex, rawDistance, MvaDistance);

        }
        if (rangingResult.getPosition().getAzimuth() != null) {
            String azimuthString = String.valueOf(rangingResult.getPosition().getAzimuth().getValue());
            rawDistanceDisplay.setText(azimuthString);
            Log.d(TAG,"Azimuth: " + azimuthString);
        }
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

    public static double movingAverage(double[] arr, double newMeas, long index) {

        // Get the current average of the array
        double currentAverage = getArrayAverage(arr);

        // Adjust newMeas if it deviates too much from current average
        if (Math.abs(currentAverage - newMeas) > DISTANCE_HYSTERESIS_METERS) {
            newMeas = currentAverage;
        }

        // Update array element at index position (circular)
        arr[(int) (index % arr.length)] = newMeas;

        // Return the updated average
        return getArrayAverage(arr);
    }

    // Calculate the average of the array
    private static double getArrayAverage(double[] arr) {
        double sum = 0;
        for (double i : arr) {
            sum += i;
        }
        return sum / arr.length;
    }

    private void initChart(LineChart lineChart) {
        List<Entry> rawEntries = new ArrayList<>();
        List<Entry> mvaEntries = new ArrayList<>();

        LineDataSet rawDataSet = new LineDataSet(rawEntries, "Raw Distance");
        LineDataSet mvaDataSet = new LineDataSet(mvaEntries, "MVA Distance");
//
//        rawDataSet.addEntry(new Entry(1, 0));
//        rawDataSet.addEntry(new Entry(2, 10));
//
//        mvaDataSet.addEntry(new Entry(1, 20));
//        mvaDataSet.addEntry(new Entry(2, 30));


        // Set colors for the datasets
        rawDataSet.setColor(Color.RED); // Line color
        rawDataSet.setCircleColor(Color.RED); // Dot color

        mvaDataSet.setColor(Color.GREEN); // Line color
        mvaDataSet.setCircleColor(Color.GREEN); // Dot color

        LineData lineData = new LineData(rawDataSet, mvaDataSet);
        lineChart.setData(lineData);
        lineChart.invalidate(); // Refresh the chart
    }

    private static void addValuesToGraph(LineChart lineChart, long index, double rawDistance, double mvaDistance){
        LineData lineData = lineChart.getData();
        LineDataSet rawDataSet = (LineDataSet) lineData.getDataSetByIndex(0);
        LineDataSet mvaDataSet = (LineDataSet) lineData.getDataSetByIndex(1);

        Entry rawEntry = new Entry(index, (float) rawDistance);
        Entry mvaEntry = new Entry(index, (float) mvaDistance);

        rawDataSet.addEntry(rawEntry);
        mvaDataSet.addEntry(mvaEntry);

        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }

    private File generateCSVFile(LineChart lineChart) throws IOException {
        File csvFile = new File(getExternalFilesDir(null), "chart_data.csv");
        FileWriter writer = new FileWriter(csvFile);

        LineData lineData = lineChart.getData();
        if (lineData != null) {
            // Assuming there are exactly two data sets
            LineDataSet dataSet1 = (LineDataSet) lineData.getDataSetByIndex(0);
            LineDataSet dataSet2 = (LineDataSet) lineData.getDataSetByIndex(1);

            List<Entry> entries1 = dataSet1.getEntriesForXValue(0);
            List<Entry> entries2 = dataSet2.getEntriesForXValue(0);

            // Write header
            writer.append(dataSet1.getLabel()).append(",").append(dataSet2.getLabel()).append("\n");

            // Write data
            for (int i = 0; i < entries1.size(); i++) {
                Entry entry1 = entries1.get(i);
                Entry entry2 = entries2.get(i);
                Log.d(TAG, "Entry1: " + entry1.getX() + ", " + entry1.getY());
                writer.append(String.valueOf(entry1.getX())).append(",").append(String.valueOf(entry1.getY())).append("\n");
                writer.append(String.valueOf(entry2.getX())).append(",").append(String.valueOf(entry2.getY())).append("\n");
            }
        }

        writer.flush();
        writer.close();
        return csvFile;
    }

    private void sendEmail(File csvFile) {
        Uri contentUri = FileProvider.getUriForFile(this, "com.example.uwbdemoapp.fileprovider", csvFile);

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/csv");
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Chart Data");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Please find the chart data attached.");
        emailIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(emailIntent, "Send email using:"));
    }


}