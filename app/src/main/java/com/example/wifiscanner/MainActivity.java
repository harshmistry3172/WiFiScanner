package com.example.wifiscanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private TextView wifiDataTextView;
    private Button startButton, stopButton, exportButton, resetButton;
    private EditText locationEditText;
    private boolean isScanning = false;
    private Handler handler;
    private Runnable scanRunnable;
    private StringBuilder scanData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize views and Wi-Fi manager
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiDataTextView = findViewById(R.id.wifiDataTextView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        exportButton = findViewById(R.id.exportButton);
        locationEditText = findViewById(R.id.locationEditText);
        resetButton = findViewById(R.id.resetButton); // Initialize reset button

        handler = new Handler(Looper.getMainLooper());
        scanData = new StringBuilder();

        // Make TextView scrollable
        wifiDataTextView.setMovementMethod(new ScrollingMovementMethod());

        // Ensure necessary permissions are granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 100);
        }

        // Start scanning Wi-Fi on button click
        startButton.setOnClickListener(view -> startScanning());

        // Stop scanning Wi-Fi on button click
        stopButton.setOnClickListener(view -> stopScanning());

        // Export Wi-Fi data to CSV on button click
        exportButton.setOnClickListener(view -> exportDataToCSV());

        resetButton.setOnClickListener(view -> resetData());

    }

    private void resetData() {
        // Clear scan data and reset TextView
        scanData.setLength(0);
        wifiDataTextView.setText("Wi-Fi data will appear here...");
        locationEditText.setText("");
        Toast.makeText(this, "Data reset", Toast.LENGTH_SHORT).show();
    }

    private void startScanning() {
        if (wifiManager != null && !isScanning) {
            isScanning = true;
            // Don't clear previous data, just append new data
            if (scanData.length() == 0) {
                scanData.append("Timestamp, Location, SSID, BSSID, Signal Strength, Frequency\n"); // Add headers only once
            }

            wifiManager.startScan();
            scanRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isScanning) {
                        scanWifiData();
                        handler.postDelayed(this, 1000);  // Re-run every second
                    }
                }
            };
            handler.post(scanRunnable);

            // Stop scanning after 10 seconds and show Toast
            handler.postDelayed(() -> {
                stopScanning();
                Toast.makeText(MainActivity.this, "Wi-Fi scanning stopped after 15 seconds", Toast.LENGTH_SHORT).show();
            }, 15000); // 10 seconds delay

            Toast.makeText(this, "Wi-Fi scanning started", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Scanning is already in progress", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopScanning() {
        if (isScanning) {
            isScanning = false;
            handler.removeCallbacks(scanRunnable);
            Toast.makeText(this, "Wi-Fi scanning stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void scanWifiData() {
        List<ScanResult> results = wifiManager.getScanResults();
        StringBuilder newData = new StringBuilder();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date()); // Current timestamp
        String location = locationEditText.getText().toString(); // Get the location from the EditText

        for (ScanResult result : results) {
            newData.append(timestamp).append(", ")
                    .append(location).append(", ")  // Add location to the row
                    .append(result.SSID).append(", ")
                    .append(result.BSSID).append(", ")
                    .append(result.level).append(", ")
                    .append(result.frequency).append("MHz\n");
        }

        if (!TextUtils.isEmpty(newData)) {
            scanData.append(newData.toString());

            // Update the TextView with only the latest scan data (limit the size if necessary)
            wifiDataTextView.setText(newData.toString());
        }
    }

    private void exportDataToCSV() {
        if (scanData.length() == 0) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create an AlertDialog to ask for the file name
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter File Name");

        // Set up the input field for the file name
        final EditText input = new EditText(this);
        input.setHint("File name (e.g., wifi_scan_results)");
        builder.setView(input);

        // Set up the dialog buttons
        builder.setPositiveButton("Export", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (fileName.isEmpty()) {
                fileName = "wifi_scan_results"; // Default file name if input is empty
            }

            // Get the path to the Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs(); // Ensure the directory exists
            }

            // Define the CSV file with the user-specified name
            File csvFile = new File(downloadsDir, fileName + ".csv");

            try (FileOutputStream fos = new FileOutputStream(csvFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {

                writer.write(scanData.toString());
                Toast.makeText(this, "Data exported to " + csvFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error exporting data", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScanning(); // Ensure scanning is stopped when the activity is destroyed
    }
}
