package org.hissylizard.vibr8.service;

/**
 * Created by sungazer on 23/01/2017.
 */

public abstract class ToyManagerCallback {

    public void toyFound(LovenseToy toy) {
    }

    public void toyConnected(LovenseToy toy) {
    }

    public void toyDisconnected(LovenseToy toy) {
    }

    public void log(LovenseToy toy, String message) {
    }
}
