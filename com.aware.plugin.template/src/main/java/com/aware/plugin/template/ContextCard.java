package com.aware.plugin.template;

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

    private TextView myoStatus = null;
    private TextView gyroX = null;
    private TextView gyroY = null;
    private TextView gyroZ = null;
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
        connectBtn = card.findViewById(R.id.connectBtn);
        progress = card.findViewById(R.id.progress);

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (connectBtn.isChecked()) {

                    Log.wtf(Plugin.MYO_TAG, "ON CLICKED");
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    myoStatus.setText("Connecting...");

                    Intent intent = new Intent("MYO_CONNECT");
                    intent.putExtra("toggleStatus", "on");
                    context.sendBroadcast(intent);
                    
                } else {

                    Log.wtf(Plugin.MYO_TAG, "OFF CLICKED");
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    myoStatus.setText("Disconnecting...");
                    
                    Intent intent = new Intent("MYO_CONNECT");
                    intent.putExtra("toggleStatus", "off");
                    intent.putExtra("connMac", macaddress);
                    context.sendBroadcast(intent);

                }
            }
        });

        IntentFilter myofilter = new IntentFilter();
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_CONNECTED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED);
        myofilter.addAction(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE);

        //Return the card to AWARE/apps
        return card;
    }

    private MyoListener myoListener = new MyoListener();
    public class MyoListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Plugin.ACTION_PLUGIN_MYO_CONNECTED)){
                
            }
            switch (intent.getAction()) {

                case Plugin.ACTION_PLUGIN_MYO_CONNECTED: {
                    macaddress = intent.getStringExtra(Plugin.MAC_ADDRESS);
                    connectBtn.setChecked(true);
                    connectBtn.setEnabled(true);
                    connectBtn.setVisibility(View.VISIBLE);
                    gyroX.setVisibility(View.VISIBLE);
                    gyroY.setVisibility(View.VISIBLE);
                    gyroZ.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.INVISIBLE);
                    myoStatus.setText("Connected to " + macaddress);
                    break;
                }

                case Plugin.ACTION_PLUGIN_MYO_DISCONNECTED: {
                    connectBtn.setChecked(false);
                    connectBtn.setEnabled(true);
                    connectBtn.setVisibility(View.VISIBLE);
                    gyroX.setVisibility(View.INVISIBLE);
                    gyroY.setVisibility(View.INVISIBLE);
                    gyroZ.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.INVISIBLE);
                    myoStatus.setText("Disconnected");
                    break;
                }

                case Plugin.ACTION_PLUGIN_MYO_GYROSCOPE: {
                    ContentValues gyro = intent.getParcelableExtra(Plugin.MYO_GYROVALUES);
                    String x = gyro.getAsString("x");
                    String y = gyro.getAsString("y");
                    String z = gyro.getAsString("z");
                    gyroX.setText(x);
                    gyroY.setText(y);
                    gyroZ.setText(z);
                    break;
                }

            }
        }
    }
}
