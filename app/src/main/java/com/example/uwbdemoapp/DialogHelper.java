package com.example.uwbdemoapp;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class DialogHelper {

    private Context context;

    public DialogHelper(Context context) {
        this.context = context;
    }

    // Method to show paired devices dialog
    public void showPairedDevicesDialog(ArrayList<String> devices, DialogListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_paired_devices, null);

        ListView pairedDevicesListView = dialogView.findViewById(R.id.pairedDevicesListView);
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, devices);
        pairedDevicesListView.setAdapter(pairedDevicesAdapter);

        pairedDevicesListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDevice = devices.get(position);
            listener.onDeviceSelected(selectedDevice);  // Notify listener of selection
        });

        builder.setView(dialogView)
                .setTitle("Paired Bluetooth Devices")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    // Interface to handle dialog callbacks
    public interface DialogListener {
        void onDeviceSelected(String deviceName);
    }
}
