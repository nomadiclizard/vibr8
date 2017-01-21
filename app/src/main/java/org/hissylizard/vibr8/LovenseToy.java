package org.hissylizard.vibr8;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

/**
 * Created by sungazer on 21/01/2017.
 */

public class LovenseToy {

    public static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    private final BluetoothDevice bluetoothDevice;
    private final Context context;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic uartTxCharacteristic;
    private boolean connected;

    public LovenseToy(BluetoothDevice bluetoothDevice, Context context) {
        this.bluetoothDevice = bluetoothDevice;
        this.context = context;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public boolean isConnected() {
        return connected;
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
            uartTxCharacteristic = null;
            gatt = null;
            connected = false;
        }
    }

    public void vibrate(int intensity) {
        sendCommand("Vibrate:" + intensity + ";");
    }

    public void beat(int intensity) {
        sendCommand("Beat:" + intensity + ";");
    }

    public void powerOff() {
        sendCommand("PowerOff;");
    }

    private void sendCommand(String command) {
        if (gatt != null && uartTxCharacteristic != null && connected) {
            try {
                byte[] bytes = command.getBytes("ASCII");
                uartTxCharacteristic.setValue(bytes);
                gatt.writeCharacteristic(uartTxCharacteristic);
            } catch (UnsupportedEncodingException e) {
                // will never happen
            }
        }
    }

    private class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    connected = true;
                    if (uartTxCharacteristic == null) {
                        gatt.discoverServices();
                    }
                } else {
                    connected = false;
                }
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && uartTxCharacteristic == null) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    if (service.getUuid().equals(UART_SERVICE_UUID)) {
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            if (characteristic.getUuid().equals(UART_TX_CHARACTERISTIC_UUID)) {
                                uartTxCharacteristic = characteristic;
                            }
                        }
                    }
                }
            }
        }
    }
}
