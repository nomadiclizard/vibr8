package org.hissylizard.vibr8.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.UUID;

/**
 * Created by sungazer on 21/01/2017.
 */

public class LovenseToy {

    public static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    // persisted
    private final BluetoothDevice bluetoothDevice;
    private final String name;
    private boolean open;

    // populated once bluetooth is connected each time
    private transient Collection<ToyManagerCallback> observers;
    private transient Context context;
    private transient BluetoothGatt gatt;
    private transient BluetoothGattCharacteristic uartTxCharacteristic;
    private transient boolean connected;

    public LovenseToy(Collection<ToyManagerCallback> observers, Context context, BluetoothAdapter bluetoothAdapter, String bluetoothDevice, String name) {
        this.observers = observers;
        this.context = context;
        this.bluetoothDevice = bluetoothAdapter.getRemoteDevice(bluetoothDevice);
        this.name = name;
    }

    public LovenseToy(Collection<ToyManagerCallback> observers, Context context, BluetoothDevice bluetoothDevice, String name) {
        this.observers = observers;
        this.context = context;
        this.bluetoothDevice = bluetoothDevice;
        this.name = name;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public String getName() {
        return name;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        boolean oldConnected = this.connected;
        this.connected = connected;
        if (!oldConnected && connected) {
            for (ToyManagerCallback observer : observers) {
                observer.toyConnected(this);
            }
        } else if (oldConnected && !connected) {
            for (ToyManagerCallback observer : observers) {
                observer.toyDisconnected(this);
            }
        }
    }

    public void connect() {
        if (gatt == null) {
            gatt = bluetoothDevice.connectGatt(context, false, new GattCallback());
        } else {
            gatt.connect();
        }
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
            uartTxCharacteristic = null;
            setConnected(false);
        }
    }

    public void vibrate(int intensity) {
        sendCommand("Vibrate:" + intensity + ";");
    }

    public void beat(int durationMillis) {
        sendCommand("Beat:" + durationMillis + ";");
    }

    public void powerOff() {
        sendCommand("PowerOff;");
    }

    private void sendCommand(String command) {
        if (gatt != null && uartTxCharacteristic != null) {
            try {
                byte[] bytes = command.getBytes("ASCII");
                uartTxCharacteristic.setValue(bytes);
                gatt.writeCharacteristic(uartTxCharacteristic);
                for (ToyManagerCallback observer : observers) {
                    observer.log(LovenseToy.this, "sent " + command);
                }
            } catch (UnsupportedEncodingException e) {
                // will never happen
            }
        }
    }

    private class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            for (ToyManagerCallback observer : observers) {
                observer.log(LovenseToy.this, "connection state change status " + status + " newState " + newState);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    // try to grab characteristics from the cache if this toy is reconnecting
                    BluetoothGattService uartService = gatt.getService(UART_SERVICE_UUID);
                    if (uartService != null) {
                        uartTxCharacteristic = uartService.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);
                        if(uartTxCharacteristic != null) {
                            setConnected(true);
                        }
                    }
                    // not there? first connection perhaps so services need discovering
                    if (uartTxCharacteristic == null) {
                        gatt.discoverServices();
                    }
                } else {
                    setConnected(false);
                }
            } else if (status == 8) {
                setConnected(false);
            } else if (status == 133) {
                // weird error - to clear it try destroying and recreating the gatt
                disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            for (ToyManagerCallback observer : observers) {
                observer.log(LovenseToy.this, "services discovered status " + status);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService uartService = gatt.getService(UART_SERVICE_UUID);
                if (uartService != null) {
                    uartTxCharacteristic = uartService.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);
                    if (uartTxCharacteristic != null) {
                        setConnected(true);
                    }
                }
            }
        }
    }
}