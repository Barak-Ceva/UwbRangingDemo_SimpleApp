package com.example.uwbdemoapp;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainBTActivity extends AppCompatActivity implements BluetoothHelper.BluetoothListener, DialogHelper.DialogListener {

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

        // Start Bluetooth communication button
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> bluetoothHelper.sendData("START;aabb"));

        // Stop Bluetooth communication button
        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v -> bluetoothHelper.sendData("STOP"));
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
