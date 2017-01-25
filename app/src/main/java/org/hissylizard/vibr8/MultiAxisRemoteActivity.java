package org.hissylizard.vibr8;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.hissylizard.vibr8.service.LovenseToy;
import org.hissylizard.vibr8.service.ToyManagerService;

import java.util.List;

public class MultiAxisRemoteActivity extends Activity {

    private ToyManagerService toyManagerService;
    private ServiceConnection connectionCallback = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            toyManagerService = ((ToyManagerService.LocalBinder) service).getService();
            rebuildSpinnerList();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            toyManagerService = null;
            // TODO show a warning message that the service died unexpectedly and disable ui
        }
    };

    private Spinner xAxis;
    private Spinner yAxis;
    private ArrayAdapter<String> spinnerAdapter;
    private ImageView imageView;
    private TextView locationStatus;

    private Handler timerHandler;
    private Runnable timerRunnable;
    private float xTouch;
    private float yTouch;
    private boolean touching;

    private class MultiAxisRemoteTimer implements Runnable {
        private long scheduledBeatTime;

        @Override
        public void run() {
            if (toyManagerService != null) {
                List<LovenseToy> toys = toyManagerService.getKnownToys();

                // update x axis toy
                if (xAxis.getSelectedItemPosition() >= 0 && xAxis.getSelectedItemPosition() < toys.size()) {
                    LovenseToy xAxisToy = toys.get(xAxis.getSelectedItemPosition());
                    if (touching) {
                        int xAxisIntensity = (int) (xTouch < 0.1f ? 0 : xTouch > 0.9f ? 20 : (xTouch - 0.1f) * 25);
                        xAxisToy.vibrate(xAxisIntensity);
                    } else {
                        xAxisToy.vibrate(0);
                    }
                }

                // update y axis toy
                if (yAxis.getSelectedItemPosition() >= 0 && yAxis.getSelectedItemPosition() < toys.size()) {
                    LovenseToy yAxisToy = toys.get(yAxis.getSelectedItemPosition());
                    if (touching) {
                        int yAxisIntensity = (int) (yTouch < 0.1f ? 0 : yTouch > 0.9f ? 20 : (yTouch - 0.1f) * 25);
                        yAxisToy.vibrate(yAxisIntensity);
                    } else {
                        yAxisToy.vibrate(0);
                    }
                }

                // update the toy vibe level every 100ms
                long beatDelayMillis = 100;
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
        setContentView(R.layout.activity_multi_axis_remote);

        Intent intent = new Intent(this, ToyManagerService.class);
        bindService(intent, connectionCallback, Context.BIND_AUTO_CREATE);

        xAxis = (Spinner) findViewById(R.id.xAxis);
        yAxis = (Spinner) findViewById(R.id.yAxis);
        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        imageView = (ImageView) findViewById(R.id.imageView);
        locationStatus = (TextView) findViewById(R.id.locationStatus);

        xAxis.setAdapter(spinnerAdapter);
        xAxis.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        yAxis.setAdapter(spinnerAdapter);
        yAxis.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                locationStatus.setText(event.toString());
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    touching = true;
                    xTouch = (float) event.getX() / (float) v.getWidth();
                    yTouch = 1.0f - (float) event.getY() / (float) v.getHeight();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    touching = false;
                }
                return true;
            }
        });

        timerHandler = new Handler();
        timerRunnable = new MultiAxisRemoteTimer();
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

    private void rebuildSpinnerList() {
        if (toyManagerService != null) {
            List<LovenseToy> toys = toyManagerService.getKnownToys();
            spinnerAdapter.clear();
            for (LovenseToy toy : toys) {
                spinnerAdapter.add(toy.getName() + ": " + toy.getBluetoothDevice().getAddress() + " open " + toy.isOpen() + " connected " + toy.isConnected());
            }
            spinnerAdapter.notifyDataSetChanged();
        }
    }
}
