package org.hissylizard.vibr8;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.hissylizard.vibr8.service.LovenseToy;
import org.hissylizard.vibr8.service.ToyManagerCallback;
import org.hissylizard.vibr8.service.ToyManagerService;

import java.util.List;

public class ToyListActivity extends Activity {

    private ToyManagerService toyManagerService;
    private ServiceConnection connectionCallback = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            toyManagerService = ((ToyManagerService.LocalBinder) service).getService();
            toyManagerService.registerObserver(toyManagerCallback);
            rebuildToyList();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            toyManagerService = null;
            // TODO show a warning message that the service died unexpectedly and disable ui
            rebuildToyList();
        }
    };

    private ToyManagerCallback toyManagerCallback = new ToyManagerCallback() {
        @Override
        public void toyFound(final LovenseToy toy) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeLog(toy, "found");
                    rebuildToyList();
                }
            });
        }

        @Override
        public void toyConnected(final LovenseToy toy) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeLog(toy, "connected");
                    rebuildToyList();
                }
            });
        }

        @Override
        public void toyDisconnected(final LovenseToy toy) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeLog(toy, "disconnected");
                    rebuildToyList();
                }
            });
        }

        @Override
        public void log(final LovenseToy toy, final String message) {
            runOnUiThread(new Runnable() {
                public void run() {
                    safeLog(toy, message);
                }
            });
        }

        private void safeLog(LovenseToy toy, String message) {
            Toast.makeText(getApplicationContext(), toy.getName() + ": " + message, Toast.LENGTH_SHORT).show();
        }
    };

    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toylist);

        Intent intent = new Intent(this, ToyManagerService.class);
        bindService(intent, connectionCallback, Context.BIND_AUTO_CREATE);

        listView = (ListView) findViewById(R.id.listView);
        ArrayAdapter<String> listViewAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(listViewAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (toyManagerService != null) {
                    LovenseToy toy = toyManagerService.getKnownToys().get(position);
                    toy.setOpen(!toy.isOpen());
                    if (toy.isOpen()) {
                        toy.connect();
                    } else {
                        toy.disconnect();
                    }
                    rebuildToyList();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toyManagerService != null) {
            unbindService(connectionCallback);
            toyManagerService = null;
        }
    }

    public void startScanClick(View v) {
        if (toyManagerService != null) {
            toyManagerService.startScan();
        }
    }

    public void stopScanClick(View v) {
        if (toyManagerService != null) {
            toyManagerService.stopScan();
        }
    }

    public void buzzClick(View v) {
        if (toyManagerService != null) {
            List<LovenseToy> toys = toyManagerService.getKnownToys();
            for (LovenseToy toy : toys) {
                if (toy.isOpen()) {
                    toy.beat(50);
                }
            }
        }
    }

    private void rebuildToyList() {
        if (toyManagerService != null) {
            List<LovenseToy> toys = toyManagerService.getKnownToys();
            ArrayAdapter arrayAdapter = (ArrayAdapter) listView.getAdapter();
            arrayAdapter.clear();
            for (LovenseToy toy : toys) {
                arrayAdapter.add(toy.getName() + ": " + toy.getBluetoothDevice().getAddress() + " open " + toy.isOpen() + " connected " + toy.isConnected());
            }
            arrayAdapter.notifyDataSetChanged();
        }
    }
}
