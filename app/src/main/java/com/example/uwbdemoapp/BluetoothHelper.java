package com.example.uwbdemoapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class BluetoothHelper {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private BluetoothListener listener;
    private Handler handler = new Handler();

    // Define the interface for Bluetooth events
    public interface BluetoothListener {
        void onDeviceConnected(String deviceName);

        void onConnectionFailed();

        void onDataSent();

        void onDisconnected();

        void onPairedDevicesAvailable(ArrayList<BluetoothDevice> pairedDevices);
    }

    public BluetoothHelper(BluetoothListener listener) {
        this.listener = listener;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (ActivityCompat.checkSelfPermission((Context) this.listener, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) this.listener, new String[]{android.Manifest.permission.BLUETOOTH}, 123);
        }
        if (ActivityCompat.checkSelfPermission((Context) this.listener, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) this.listener, new String[]{android.Manifest.permission.BLUETOOTH_ADMIN}, 1);
        }
        if (ActivityCompat.checkSelfPermission((Context) this.listener, android.Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) this.listener, new String[]{android.Manifest.permission.BLUETOOTH_PRIVILEGED}, 1);
        }
        if (ActivityCompat.checkSelfPermission((Context) this.listener, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) this.listener, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }
        if (ActivityCompat.checkSelfPermission((Context) this.listener, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) this.listener, new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, 1);
        }
    }

    // Fetch paired devices and notify listener
    public void listPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);

        // Notify the listener with the list of paired devices
        if (listener != null) {
            listener.onPairedDevicesAvailable(deviceList);
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                handler.post(() -> listener.onDeviceConnected(device.getName()));
            } catch (IOException e) {
                handler.post(() -> listener.onConnectionFailed());
            }
        }).start();
    }

    public void sendData(String data) {
        new Thread(() -> {
            try {
                outputStream.write(data.getBytes());
                handler.post(() -> listener.onDataSent());
            } catch (IOException e) {
                handler.post(() -> listener.onDisconnected());
            }
        }).start();
    }

    public void disconnect() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            handler.post(() -> listener.onDisconnected());
        } catch (IOException e) {
            Log.e("Bluetooth", "Error closing socket", e);
        }
    }
}
