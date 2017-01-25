package org.hissylizard.vibr8;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.hissylizard.vibr8.service.LovenseToy;
import org.hissylizard.vibr8.service.ToyManagerService;

import java.util.List;

public class MetronomeActivity extends Activity {

    private ToyManagerService toyManagerService;
    private ServiceConnection connectionCallback = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            toyManagerService = ((ToyManagerService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            toyManagerService = null;
            // TODO show a warning message that the service died unexpectedly and disable ui
        }
    };

    private TextView bpm;
    private ToggleButton toggleButton;

    private Handler timerHandler;
    private Runnable timerRunnable;

    private class MetronomeTimer implements Runnable {
        private long scheduledBeatTime;

        @Override
        public void run() {
            if (toyManagerService != null) {
                if (toggleButton.isChecked()) {
                    List<LovenseToy> toys = toyManagerService.getKnownToys();
                    for (LovenseToy toy : toys) {
                        if (toy.isOpen()) {
                            toy.beat(50);
                        }
                    }
                }
                long beatDelayMillis = 60000 / getBpmValue();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metronome);

        Intent intent = new Intent(this, ToyManagerService.class);
        bindService(intent, connectionCallback, Context.BIND_AUTO_CREATE);

        bpm = (TextView) findViewById(R.id.bpm);
        toggleButton = (ToggleButton) findViewById(R.id.toggleButton);

        timerHandler = new Handler();
        timerRunnable = new MetronomeTimer();
        timerHandler.postDelayed(timerRunnable, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toyManagerService != null) {
            unbindService(connectionCallback);
            toyManagerService = null;
        }
    }

    private int getBpmValue() {
        return Integer.parseInt(bpm.getText().toString());
    }

    public void decreaseBpmClick(View v) {
        if (getBpmValue() > 1) {
            bpm.setText(Integer.toString(getBpmValue() - 5));
        }
    }

    public void increaseBpmClick(View v) {
        if (getBpmValue() < 240) {
            bpm.setText(Integer.toString(getBpmValue() + 5));
        }
    }
}
