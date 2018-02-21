package com.aware.plugin.myo;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.aware.utils.IContextCard;

public class ContextCard implements IContextCard {

    //Constructor used to instantiate this card
    public ContextCard() {
    }

    static final String CONTEXT_ACTION_MYO_CONNECT = "CONTEXT_ACTION_MYO_CONNECT";
    static final String CONTEXT_TOGGLE_STATUS = "CONTEXT_TOGGLE_STATUS";
    static final String CONTEXT_TOGGLE_ON = "CONTEXT_TOGGLE_ON";
    static final String CONTEXT_TOGGLE_OFF = "CONTEXT_TOGGLE_OFF";

    private TextView tvMyoStatus, tvMyoMac, tvMyoBattery, tvGyroX, tvGyroY, tvGyroZ,
                        tvEmg0, tvEmg1, tvEmg2, tvEmg3, tvEmg4, tvEmg5, tvEmg6, tvEmg7;
    private ToggleButton connectBtn = null;
    private ProgressBar progress = null;
    private RelativeLayout myoData;


    @Override
    public View getContextCard(final Context context) {
        //Load card layout
        View card = LayoutInflater.from(context).inflate(R.layout.card, null);
        myoData = card.findViewById(R.id.myoData);
        tvMyoStatus = card.findViewById(R.id.myoStatus);
        tvMyoMac = card.findViewById(R.id.myoMac);
        tvMyoBattery = card.findViewById(R.id.myoBattery);
        tvGyroX = card.findViewById(R.id.gyroX);
        tvGyroY = card.findViewById(R.id.gyroY);
        tvGyroZ = card.findViewById(R.id.gyroZ);
        tvEmg0 = card.findViewById(R.id.emg0);
        tvEmg1 = card.findViewById(R.id.emg1);
        tvEmg2 = card.findViewById(R.id.emg2);
        tvEmg3 = card.findViewById(R.id.emg3);
        tvEmg4 = card.findViewById(R.id.emg4);
        tvEmg5 = card.findViewById(R.id.emg5);
        tvEmg6 = card.findViewById(R.id.emg6);
        tvEmg7 = card.findViewById(R.id.emg7);
        connectBtn = card.findViewById(R.id.connectBtn);
        progress = card.findViewById(R.id.progress);

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (connectBtn.isChecked()) {
                    Log.d(Plugin.MYO_TAG, "ON CLICKED : CONTEXT CARD");

                    //Send "toggle_on" broadcast to Plugin
                    Intent intent = new Intent(ContextCard.CONTEXT_ACTION_MYO_CONNECT);
                    intent.putExtra(ContextCard.CONTEXT_TOGGLE_STATUS, ContextCard.CONTEXT_TOGGLE_ON);
                    context.sendBroadcast(intent);

                    //Update views to "on" state
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    tvMyoStatus.setText("Connecting...");

                } else {
                    Log.d(Plugin.MYO_TAG, "OFF CLICKED : CONTEXT CARD");

                    //Send "toggle_off" broadcast to Plugin
                    Intent intent = new Intent(ContextCard.CONTEXT_ACTION_MYO_CONNECT);
                    intent.putExtra(ContextCard.CONTEXT_TOGGLE_STATUS, ContextCard.CONTEXT_TOGGLE_OFF);
                    context.sendBroadcast(intent);

                    //Update views to "off" state
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    myoData.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    tvMyoStatus.setText("Disconnecting...");
                }
            }
        });

        //Registering Plugin actions
        IntentFilter myofilter = new IntentFilter();
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_CONNECTED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_CONNECTION_FAILED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_BATTERY_LEVEL);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_EMG);
        context.registerReceiver(myoListener, myofilter);

        //Return the card to AWARE/apps
        return card;
    }

    //Registering Plugin actions
    private MyoListener myoListener = new MyoListener();
    public class MyoListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_CONNECTED)){
                connectBtn.setChecked(true);
                connectBtn.setEnabled(true);
                connectBtn.setVisibility(View.VISIBLE);
                myoData.setVisibility(View.VISIBLE);
                progress.setVisibility(View.INVISIBLE);
                tvMyoStatus.setText("Connected to Myo");
                tvMyoMac.setText(intent.getStringExtra(Plugin.MYO_MAC_ADDRESS));
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_CONNECTION_FAILED)){
                Toast.makeText(context, "Connection failed, please check your Myo", Toast.LENGTH_SHORT).show();
                connectBtn.setChecked(false);
                connectBtn.setEnabled(true);
                connectBtn.setVisibility(View.VISIBLE);
                progress.setVisibility(View.INVISIBLE);
                tvMyoStatus.setText("Connect to Myo first");
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED)){
                connectBtn.setChecked(false);
                connectBtn.setEnabled(true);
                connectBtn.setVisibility(View.VISIBLE);
                myoData.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.INVISIBLE);
                tvMyoStatus.setText("Disconnected");
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_BATTERY_LEVEL)){
                String battery = intent.getStringExtra(Plugin.MYO_BATTERY_LEVEL) + "%";
                tvMyoBattery.setText(battery);
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE)){
                ContentValues gyroData = intent.getParcelableExtra(Plugin.MYO_GYROVALUES);
                tvGyroX.setText(gyroData.getAsString("gyroX"));
                tvGyroY.setText(gyroData.getAsString("gyroY"));
                tvGyroZ.setText(gyroData.getAsString("gyroZ"));
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_EMG)){
                ContentValues gyroData = intent.getParcelableExtra(Plugin.MYO_EMG);
                tvEmg0.setText(gyroData.getAsString("emg0"));
                tvEmg1.setText(gyroData.getAsString("emg1"));
                tvEmg2.setText(gyroData.getAsString("emg2"));
                tvEmg3.setText(gyroData.getAsString("emg3"));
                tvEmg4.setText(gyroData.getAsString("emg4"));
                tvEmg5.setText(gyroData.getAsString("emg5"));
                tvEmg6.setText(gyroData.getAsString("emg6"));
                tvEmg7.setText(gyroData.getAsString("emg7"));

            }
        }
    }
}
