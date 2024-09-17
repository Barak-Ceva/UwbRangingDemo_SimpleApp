package com.example.uwbdemoapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.graphics.Color;


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

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.reactivex.rxjava3.disposables.Disposable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;


public class MainActivity extends AppCompatActivity {

    private Button initRangingButton;
    private Button startRangingButton;
    private Button stopRangingButton;
    private Switch roleSwitch;
    private TextView role;

    private TextView distanceDisplay;
    private TextView rawDistanceDisplay;

    private LineChart lineChart;
    private Button resetGraphButton;
    private Button sendEmailButton;

    private AtomicReference<Disposable> rangingResultObservable = new AtomicReference<>(null);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.UWB_RANGING) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.UWB_RANGING}, 123);
        }

        uwbManager = UwbManager.createInstance(this);

        InitUIElements();

        InitUIListeners();

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
                currentUwbSessionScope.set(UwbManagerRx.controllerSessionScopeSingle(uwbManager).blockingGet());
                UwbControllerSessionScope controllerSessionScope = (UwbControllerSessionScope) currentUwbSessionScope.get();
                MacAddressAlertDialog(view, controllerSessionScope.getLocalAddress().getAddress(), "Controller");
            } else {
                // CONTROLLEE
                currentUwbSessionScope.set(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());
                UwbControleeSessionScope controleeSessionScope = (UwbControleeSessionScope) currentUwbSessionScope.get();
                MacAddressAlertDialog(view, controleeSessionScope.getLocalAddress().getAddress(), "Controlee");
            }
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

                rangingResultObservable.set(UwbClientSessionScopeRx.rangingResultsObservable(currentUwbSessionScope.get(), rangingParameters).subscribe(rangingResult -> {
                            if (rangingResult instanceof RangingResult.RangingResultPosition) {
                                handleRangingResult((RangingResult.RangingResultPosition) rangingResult, distanceDisplay, rawDistanceDisplay, lineChart);
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
            AtomicReference<UwbClientSessionScope> currentUwbSessionScope = new AtomicReference<>(UwbManagerRx.controleeSessionScopeSingle(uwbManager).blockingGet());

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

        distanceDisplay = findViewById(R.id.distance_display);
        rawDistanceDisplay = findViewById(R.id.raw_distance_display);

        lineChart = findViewById(R.id.line_chart);
        resetGraphButton = findViewById(R.id.reset_graph_button);
        sendEmailButton = findViewById(R.id.send_email_button);

        initGraph(lineChart);
    }

    private void InitUIListeners() {
        sendEmailButton.setOnClickListener(v -> {
            try {
                File csvFile = generateCSVFile(lineChart);
                sendEmail(csvFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        resetGraphButton.setOnClickListener(v -> {
            resetGraph(lineChart);
        });
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

        if (rangingResultObservable.get() != null) {
            rangingResultObservable.get().dispose();
            rangingResultObservable.set(null);

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Ranging Stopped")
                    .setMessage("Ranging has been stopped.")
                    .setNeutralButton("OK", null)
                    .show();

            Log.d(TAG, "Ranging stopped.");
        }
    }

    private static void handleRangingResult(RangingResult.RangingResultPosition rangingResult, TextView mvaDistanceDisplay, TextView rawDistanceDisplay, LineChart lineChart) {
        if (rangingResult.getPosition().getDistance() != null) {

            double rawDistance;
            double mvaDistance;

            rawDistance = rangingResult.getPosition().getDistance().getValue();

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
            addValuesToGraph(lineChart, rawDistance, mvaDistance);

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

    private void initGraph(LineChart lineChart) {
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

    private void resetGraph(LineChart lineChart) {
        LineData lineData = lineChart.getData();
        LineDataSet rawDataSet = (LineDataSet) lineData.getDataSetByIndex(0);
        LineDataSet mvaDataSet = (LineDataSet) lineData.getDataSetByIndex(1);

        rawDataSet.clear();
        mvaDataSet.clear();

        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }

    private static void addValuesToGraph(LineChart lineChart, double rawDistance, double mvaDistance){
        LineData lineData = lineChart.getData();
        LineDataSet rawDataSet = (LineDataSet) lineData.getDataSetByIndex(0);
        LineDataSet mvaDataSet = (LineDataSet) lineData.getDataSetByIndex(1);

        Entry rawEntry = new Entry(rawDataSet.getEntryCount() + 1, (float) rawDistance);
        Entry mvaEntry = new Entry(mvaDataSet.getEntryCount() + 1, (float) mvaDistance);

        rawDataSet.addEntry(rawEntry);
        mvaDataSet.addEntry(mvaEntry);

        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
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

    private File generateCSVFile(LineChart lineChart) throws IOException {
        // Generate a timestamp using the current date and time for unique file naming
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        // Create a new CSV file in the external files directory with the timestamp in the filename
        File csvFile = new File(getExternalFilesDir(null), "chart_data_" + timestamp + ".csv");

        // Initialize a FileWriter to write data into the CSV file
        FileWriter writer = new FileWriter(csvFile);

        // Retrieve the data object from the LineChart, which contains all datasets
        LineData lineData = lineChart.getData();
        if (lineData != null) {
            // Assuming there are exactly two datasets of equal length, retrieve them by index
            LineDataSet dataSet1 = (LineDataSet) lineData.getDataSetByIndex(0); // First dataset
            LineDataSet dataSet2 = (LineDataSet) lineData.getDataSetByIndex(1); // Second dataset

            // Write the header line to the CSV with the labels of the two datasets
            writer.append("Index,")
                    .append(dataSet1.getLabel()).append(",")
                    .append(dataSet2.getLabel()).append("\n");

            // Iterate through the entries of the datasets, assuming equal lengths
            for (int i = 0; i < dataSet1.getEntryCount(); i++) {
                // Retrieve entries from both datasets
                Entry entry1 = dataSet1.getEntryForIndex(i);
                Entry entry2 = dataSet2.getEntryForIndex(i);

                // Write the index (1-based) and the corresponding Y-values from both datasets to the CSV
                writer.append(String.valueOf(i + 1))  // Index starts at 1 for readability
                        .append(",")                    // Separate index with a comma
                        .append(String.valueOf(entry1.getY())) // Append Y-value from the first dataset
                        .append(",")                    // Separate values with a comma
                        .append(String.valueOf(entry2.getY())) // Append Y-value from the second dataset
                        .append("\n");                  // End the line
            }
        }

        // Flush and close the writer to ensure all data is written and resources are released
        writer.flush();
        writer.close();

        // Return the created CSV file to the caller
        return csvFile;
    }

    private void MacAddressAlertDialog(View view, byte[] macAddress, String role){
        new AlertDialog.Builder(
                view.getContext()).setTitle(role).
                setMessage("Your MAC Address is: " + Utils.convertBytesToHexLittleEndian(macAddress))
                .setNeutralButton("OK", (a, b) -> {
                }).create().show();
    }

}