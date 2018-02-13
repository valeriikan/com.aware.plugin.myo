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
import android.widget.TextView;
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
    static final String CONTEXT_MAC_ADRESS = "CONTEXT_MAC_ADRESS";

    private TextView myoStatus = null;
    private TextView gyroX = null;
    private TextView gyroY = null;
    private TextView gyroZ = null;
    private TextView myoPose = null;
    private ToggleButton connectBtn = null;
    private ProgressBar progress = null;
    private String macaddress = null;

    @Override
    public View getContextCard(final Context context) {
        //Load card layout
        View card = LayoutInflater.from(context).inflate(R.layout.card, null);
        myoStatus = card.findViewById(R.id.myoStatus);
        gyroX = card.findViewById(R.id.gyroX);
        gyroY = card.findViewById(R.id.gyroY);
        gyroZ = card.findViewById(R.id.gyroZ);
        myoPose = card.findViewById(R.id.myoPose);
        connectBtn = card.findViewById(R.id.connectBtn);
        progress = card.findViewById(R.id.progress);

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (connectBtn.isChecked()) {

                    Log.d(Plugin.MYO_TAG, "ON CLICKED");
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    myoStatus.setText("Connecting...");

                    Intent intent = new Intent(ContextCard.CONTEXT_ACTION_MYO_CONNECT);
                    intent.putExtra(ContextCard.CONTEXT_TOGGLE_STATUS, ContextCard.CONTEXT_TOGGLE_ON);
                    context.sendBroadcast(intent);
                    
                } else {

                    Log.d(Plugin.MYO_TAG, "OFF CLICKED");
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    myoStatus.setText("Disconnecting...");
                    
                    Intent intent = new Intent(ContextCard.CONTEXT_ACTION_MYO_CONNECT);
                    intent.putExtra(ContextCard.CONTEXT_TOGGLE_STATUS, ContextCard.CONTEXT_TOGGLE_OFF);
                    intent.putExtra(ContextCard.CONTEXT_MAC_ADRESS, macaddress);
                    context.sendBroadcast(intent);

                }
            }
        });

        IntentFilter myofilter = new IntentFilter();
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_CONNECTED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_POSE);
        context.registerReceiver(myoListener, myofilter);

        //Return the card to AWARE/apps
        return card;
    }

    private MyoListener myoListener = new MyoListener();
    public class MyoListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_CONNECTED)){
                macaddress = intent.getStringExtra(Plugin.MYO_MAC_ADDRESS);
                connectBtn.setChecked(true);
                connectBtn.setEnabled(true);
                connectBtn.setVisibility(View.VISIBLE);
                gyroX.setVisibility(View.VISIBLE);
                gyroY.setVisibility(View.VISIBLE);
                gyroZ.setVisibility(View.VISIBLE);
                myoPose.setVisibility(View.VISIBLE);
                progress.setVisibility(View.INVISIBLE);
                myoStatus.setText("Connected to " + macaddress);
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED)){
                connectBtn.setChecked(false);
                connectBtn.setEnabled(true);
                connectBtn.setVisibility(View.VISIBLE);
                gyroX.setVisibility(View.INVISIBLE);
                gyroY.setVisibility(View.INVISIBLE);
                gyroZ.setVisibility(View.INVISIBLE);
                myoPose.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.INVISIBLE);
                myoStatus.setText("Disconnected");
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE)){
                ContentValues gyro = intent.getParcelableExtra(Plugin.MYO_GYROVALUES);
                String x = gyro.getAsString("x");
                String y = gyro.getAsString("y");
                String z = gyro.getAsString("z");
                gyroX.setText(x);
                gyroY.setText(y);
                gyroZ.setText(z);
            }
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_POSE)){
                String pose = "Pose: " + intent.getStringExtra(Plugin.MYO_POSE);
                myoPose.setText(pose);
            }
        }
    }
}
