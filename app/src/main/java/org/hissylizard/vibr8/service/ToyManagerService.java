package org.hissylizard.vibr8.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by sungazer on 21/01/2017.
 */

public class ToyManagerService extends Service {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private ScanCallback scanCallback;
    private boolean scanning;
    private ListenerThread listenerThread;

    public static final String TOYS_MANAGER_PREFS = "ToyManager";
    private LinkedHashMap<String, LovenseToy> knownToys = new LinkedHashMap<String, LovenseToy>();
    private Set<ToyManagerCallback> observers = new HashSet<ToyManagerCallback>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public class LocalBinder extends Binder {
        public ToyManagerService getService() {
            return ToyManagerService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // open bluetooth
        bluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        scanCallback = new LovenseScanCallback();

        // restore the toy registry
        SharedPreferences prefs = getSharedPreferences(TOYS_MANAGER_PREFS, MODE_PRIVATE);
        int size = prefs.getInt("size", 0);
        for (int i = 0; i < size; i++) {
            String toyPrefix = i + ".";
            String bluetoothDevice = prefs.getString(toyPrefix + "bluetoothDevice", "");
            String name = prefs.getString(toyPrefix + "name", "");
            LovenseToy toy = new LovenseToy(observers, getApplicationContext(), bluetoothAdapter, bluetoothDevice, name);
            toy.setOpen(prefs.getBoolean(toyPrefix + "open", false));
            knownToys.put(toy.getBluetoothDevice().getAddress(), toy);
        }

        // reconnect to any open toys
        // TODO turn this into a timer that checks for toys marked open and tries to connect to them
        for (LovenseToy toy : knownToys.values()) {
            if (toy.isOpen()) {
                toy.connect();
            }
        }

        // start listening on UDP in a background thread
        listenerThread = new ListenerThread(this);
        listenerThread.start();
    }

    // called from UDP listener TODO make a unified public interface to control toys on this service
    // and get Android activities and UDP clients to use it, allow addressing toy by class/id etc
    void handleMessage(String message) {
        String[] parts = message.split(":");
        for (LovenseToy toy : knownToys.values()) {
            if ("*".equals(parts[0]) || toy.getName().equals(parts[0])) {
                for (ToyManagerCallback observer : observers) {
                    observer.log(toy, "got UDP " + message);
                }
                if ("vibrate".equals(parts[1])) {
                    toy.vibrate(Integer.parseInt(parts[2]) / 5); // TODO toys should all use 0..100
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // close the UDP listener thread
        listenerThread.active = false;
        listenerThread.interrupt();

        // cancel any scans in progress
        stopScan();

        // disconnect from any connected toys
        for (LovenseToy toy : knownToys.values()) {
            toy.disconnect();
        }

        // store the toy registry
        SharedPreferences.Editor editor = getSharedPreferences(TOYS_MANAGER_PREFS, MODE_PRIVATE).edit();
        editor.clear();
        editor.putInt("size", knownToys.size());
        LovenseToy[] toysArray = knownToys.values().toArray(new LovenseToy[knownToys.size()]);
        for (int i = 0; i < knownToys.size(); i++) {
            String toyPrefix = i + ".";
            LovenseToy toy = toysArray[i];
            editor.putString(toyPrefix + "bluetoothDevice", toy.getBluetoothDevice().getAddress());
            editor.putString(toyPrefix + "name", toy.getName());
            editor.putBoolean(toyPrefix + "open", toy.isOpen());
        }
        editor.apply();
    }

    public void startScan() {
        if (!scanning) {
            if (bluetoothScanner == null) {
                bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
            if (bluetoothScanner != null) {
                ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
                scanFilterBuilder.setServiceUuid(new ParcelUuid(LovenseToy.UART_SERVICE_UUID));
                ScanFilter scanFilter = scanFilterBuilder.build();

                ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
                scanSettingsBuilder.setReportDelay(1000);
                scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                ScanSettings scanSettings = scanSettingsBuilder.build();

                bluetoothScanner.startScan(Arrays.asList(scanFilter), scanSettings, scanCallback);
                scanning = true;
            }
        }
    }

    public void stopScan() {
        if (scanning) {
            if (bluetoothScanner != null) {
                bluetoothScanner.stopScan(scanCallback);
            }
            scanning = false;
            bluetoothScanner = null;
        }
    }

    public List<LovenseToy> getKnownToys() {
        return Collections.unmodifiableList(new ArrayList<LovenseToy>(knownToys.values()));
    }

    public void registerObserver(ToyManagerCallback observer) {
        observers.add(observer);
    }

    public void unregisterObserver(ToyManagerCallback observer) {
        observers.remove(observer);
    }

    private class LovenseScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            stopScan();
        }

        private void addScanResult(ScanResult result) {
            // lovense devices are added to the list of discovered toys
            if (result.getScanRecord().getDeviceName().startsWith("LVS-")) {
                LovenseToy toy = new LovenseToy(observers, getApplicationContext(), result.getDevice(), result.getScanRecord().getDeviceName());
                if (!knownToys.containsKey(toy.getBluetoothDevice().getAddress())) {
                    knownToys.put(toy.getBluetoothDevice().getAddress(), toy);
                    for (ToyManagerCallback observer : observers) {
                        observer.toyFound(toy);
                    }
                }
            }
        }
    }
}
