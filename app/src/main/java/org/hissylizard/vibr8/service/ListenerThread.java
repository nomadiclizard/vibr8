package org.hissylizard.vibr8.service;

import android.util.Log;

import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Created by sungazer on 24/02/2017.
 */

public class ListenerThread extends Thread {

    private final ToyManagerService service;
    volatile boolean active = true;

    public ListenerThread(ToyManagerService service) {
        this.service = service;
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(6970);
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, 256);
            while (active) {
                socket.receive(packet);
                String message = new String(buffer, 0, packet.getLength(), "ASCII");
                service.handleMessage(message.trim()); // chomp the \n
            }
        } catch (InterruptedIOException ee) {
            // thread closing (I hope)
            Log.i("Listener", ee.getMessage(), ee);
        } catch (Exception e) {
            Log.e("Listener", e.getMessage(), e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
