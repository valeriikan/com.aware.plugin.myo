package com.aware.plugin.template;

import android.content.BroadcastReceiver;
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
    private ToggleButton connectBtn = null;
    private ProgressBar progress = null;
    private String macaddress = null;

    @Override
    public View getContextCard(final Context context) {
        //Load card layout
        View card = LayoutInflater.from(context).inflate(R.layout.card, null);
        myoStatus = card.findViewById(R.id.myoStatus);
        connectBtn = card.findViewById(R.id.connectBtn);
        progress = card.findViewById(R.id.progress);

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (connectBtn.isChecked()) {

                    Log.wtf("AWAREMYO", "ON CLICKED");
                    connectBtn.setEnabled(false);
                    connectBtn.setVisibility(View.INVISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    myoStatus.setText("Connecting...");

                    Intent intent = new Intent("MYO_CONNECT");
                    intent.putExtra("toggleStatus", "on");
                    context.sendBroadcast(intent);
                    
                } else {

                    Log.wtf("AWAREMYO", "OFF CLICKED");
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

        BroadcastReceiver myoDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String dataConnection = intent.getStringExtra("dataConnection");
                macaddress = intent.getStringExtra("dataMac");

                if (dataConnection.equals("connected")) {
                    connectBtn.setEnabled(true);
                    connectBtn.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.INVISIBLE);
                    myoStatus.setText("Connected to " + macaddress);

                }
                if (dataConnection.equals("disconnected")) {
                    connectBtn.setEnabled(true);
                    connectBtn.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.INVISIBLE);
                    myoStatus.setText("Disconnected");
                }
            }
        };

        IntentFilter myoDataFilter = new IntentFilter("MYO_DATA");
        context.registerReceiver(myoDataReceiver, myoDataFilter);

        //Return the card to AWARE/apps
        return card;
    }



}
