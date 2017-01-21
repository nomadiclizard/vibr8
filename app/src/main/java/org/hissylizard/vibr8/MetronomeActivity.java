package org.hissylizard.vibr8;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetronomeActivity extends AppCompatActivity {

    private static final int MAX_SCAN_DURATION_MILLIS = 5000;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private ScanCallback scanCallback = new MetronomeScanCallback();
    private long scanStartTimeMillis;
    private Set<BluetoothDevice> scanResults = new HashSet<BluetoothDevice>();
    private Set<LovenseToy> connectedToys = new HashSet<LovenseToy>();

    private int bpm = 120;
    private String status = "Click Connect to connect to toys within range";
    private boolean startStop;

    private Handler timerHandler;
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metronome);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        refreshUi();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                        }
                    }
                });
                builder.show();
            }
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();

        timerHandler = new Handler();
        timerRunnable = new MetronomeTimer();
        timerHandler.postDelayed(timerRunnable, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_metronome, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        bluetoothScanner.stopScan(scanCallback);
        for (LovenseToy toy : connectedToys) {
            toy.disconnect();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("" + R.id.bpm, bpm);
        outState.putString("" + R.id.status, status);
        outState.putBoolean("" + R.id.startStop, startStop);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        bpm = savedInstanceState.getInt("" + R.id.bpm);
        status = savedInstanceState.getString("" + R.id.status);
        startStop = savedInstanceState.getBoolean("" + R.id.startStop);
        refreshUi();
    }

    private void refreshUi() {
        TextView bpmText = (TextView) findViewById(R.id.bpm);
        bpmText.setText(Integer.toString(bpm));
        TextView statusText = (TextView) findViewById(R.id.status);
        statusText.setText(status);
        ToggleButton startStopButton = (ToggleButton) findViewById(R.id.startStop);
        startStopButton.setChecked(startStop);
    }

    public void bpmDecreaseClick(View v) {
        bpm--;
        refreshUi();
    }

    public void bpmIncreaseClick(View v) {
        bpm++;
        refreshUi();
    }

    public void connectClick(View v) {
        if (!bluetoothAdapter.isEnabled()) {
            status = "Requesting user enable bluetooth";
            refreshUi();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        } else {
            startScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                status = "Location permission obtained!";
                refreshUi();
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Functionality limited");
                builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                    }
                });
                builder.show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                status = "User enabled bluetooth!";
                refreshUi();
                startScan();
            } else {
                status = "User didn't enable bluetooth BOOOO";
                refreshUi();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startScan() {
        status = "Disconnecting from toys";
        refreshUi();
        for (LovenseToy toy : connectedToys) {
            toy.disconnect();
        }
        connectedToys.clear();

        status = "Starting bluetooth LE scan...";
        refreshUi();
        scanStartTimeMillis = System.currentTimeMillis();

        ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
        scanFilterBuilder.setServiceUuid(new ParcelUuid(LovenseToy.UART_SERVICE_UUID));
        ScanFilter scanFilter = scanFilterBuilder.build();

        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        scanSettingsBuilder.setReportDelay(1000);
        scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        ScanSettings scanSettings = scanSettingsBuilder.build();

        bluetoothScanner.startScan(Arrays.asList(scanFilter), scanSettings, scanCallback);
    }

    private void stopScan() {
        status = "Stopped bluetooth LE scan - found " + scanResults.size() + " toys";
        refreshUi();
        bluetoothScanner.stopScan(scanCallback);

        for (BluetoothDevice device : scanResults) {
            LovenseToy toy = new LovenseToy(device, getApplicationContext());
            toy.connect();
            connectedToys.add(toy);
        }
    }

    private class MetronomeScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            status = "Scan returned onScanResult";
            refreshUi();
            handleScanResult(result);
            if (System.currentTimeMillis() > scanStartTimeMillis + MAX_SCAN_DURATION_MILLIS) {
                stopScan();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            status = "Scan returned onBatchScanResults - size " + results.size();
            refreshUi();
            for (ScanResult result : results) {
                handleScanResult(result);
            }
            if (System.currentTimeMillis() > scanStartTimeMillis + MAX_SCAN_DURATION_MILLIS) {
                stopScan();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            status = "Scan failed - errorcode " + errorCode;
            refreshUi();
            stopScan();
        }

        private void handleScanResult(ScanResult scanResult) {
            status = "Scan result - found a " + scanResult.getScanRecord().getDeviceName();
            refreshUi();
            if (scanResult.getScanRecord().getDeviceName().startsWith("LVS-")) {
                scanResults.add(scanResult.getDevice());
            }
        }
    }

    private class MetronomeTimer implements Runnable {
        private long scheduledBeatTime;

        @Override
        public void run() {
            ToggleButton startStopButton = (ToggleButton) findViewById(R.id.startStop);
            startStop = startStopButton.isChecked();

            if (startStop) {
                for (LovenseToy toy : connectedToys) {
                    if (toy.isConnected()) {
                        toy.beat(50);
                    } else {
                        status = "Toy disconnected? " + toy.getBluetoothDevice();
                        refreshUi();
                        toy.connect();
                    }
                }
            }
            long beatDelayMillis = 60000 / bpm;
            long nextScheduledBeatTime = scheduledBeatTime + beatDelayMillis;
            if (nextScheduledBeatTime < System.currentTimeMillis()) {
                nextScheduledBeatTime = System.currentTimeMillis() + beatDelayMillis;
            }
            long timeTilNextBeat = nextScheduledBeatTime - System.currentTimeMillis();
            scheduledBeatTime = nextScheduledBeatTime;
            timerHandler.postDelayed(timerRunnable, timeTilNextBeat > 0 ? timeTilNextBeat : 0);
        }
    }
}
