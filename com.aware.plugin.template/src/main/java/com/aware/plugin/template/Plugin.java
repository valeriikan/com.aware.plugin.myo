package com.aware.plugin.template;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.Vector3;
import com.thalmic.myo.XDirection;

public class Plugin extends Aware_Plugin {

    @Override
    public void onCreate() {
        super.onCreate();

        //This allows plugin data to be synced on demand from broadcast Aware#ACTION_AWARE_SYNC_DATA
        AUTHORITY = Provider.getAuthority(this);

        TAG = "AWARE::"+getResources().getString(R.string.app_name);

        /**
         * Plugins share their current status, i.e., context using this method.
         * This method is called automatically when triggering
         * {@link Aware#ACTION_AWARE_CURRENT_CONTEXT}
         **/
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        //Add permissions you need (Android M+).
        //By default, AWARE asks access to the #Manifest.permission.WRITE_EXTERNAL_STORAGE
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /**
     * Allow callback to other applications when data is stored in provider
     */
    private static AWARESensorObserver awareSensor;
    public static void setSensorObserver(AWARESensorObserver observer) {
        awareSensor = observer;
    }
    public static AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onDataChanged(ContentValues data);
    }

    // Myo component
    private Hub myoHub;

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, true);

            //Initialise AWARE instance in plugin
            Aware.startAWARE(this);

            setupMyo();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //Turn off the sync-adapter if part of a study
        if (Aware.isStudy(this) && (getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone))) {
            ContentResolver.removePeriodicSync(
                    Aware.getAWAREAccount(this),
                    Provider.getAuthority(this),
                    Bundle.EMPTY
            );
        }

        //Removing values


        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Stop AWARE instance in plugin
        Aware.stopAWARE(this);
    }

    // Initializing Hub and attaching listener to it
    public void setupMyo() {

        Log.wtf("AWAREMYO", "setupMyo started");
        if (myoHub == null) {

            myoHub = Hub.getInstance();
            myoHub.init(getApplicationContext());

            myoHub.addListener(new DeviceListener() {
                @Override
                public void onAttach(Myo myo, long l) {
                    Log.wtf("AWAREMYO", "Attached to Myo:" + myo.toString());
                }

                @Override
                public void onDetach(Myo myo, long l) {
                    Log.wtf("AWAREMYO", "Detached Myo:" + myo.toString());
                }

                @Override
                public void onConnect(Myo myo, long l) {
                    Log.d("AWAREMYO", "Connected to Myo:" + myo.toString());

                    Intent intent = new Intent("MYO_DATA");
                    intent.putExtra("dataConnection", "connected");
                    intent.putExtra("dataMac", myo.getMacAddress());
                    sendBroadcast(intent);
                }

                @Override
                public void onDisconnect(Myo myo, long l) {
                    Log.wtf("AWAREMYO", "Disconnected from Myo:" + myo.toString());

                    Intent intent = new Intent("MYO_DATA");
                    intent.putExtra("dataConnection", "disconnected");
                    sendBroadcast(intent);
                }

                @Override
                public void onArmSync(Myo myo, long l, Arm arm, XDirection xDirection) {
                    Log.wtf("AWAREMYO", "onArmSync: " + myo.toString());
                }

                @Override
                public void onArmUnsync(Myo myo, long l) {
                    Log.wtf("AWAREMYO", "onArmUnsync: " + myo.toString());
                }

                @Override
                public void onUnlock(Myo myo, long l) {
                    Log.wtf("AWAREMYO", "Unlock: " + myo.toString());
                }

                @Override
                public void onLock(Myo myo, long l) {
                    Log.wtf("AWAREMYO", "onLock: " + myo.toString());
                }

                @Override
                public void onPose(Myo myo, long l, Pose pose) {
                    Log.wtf("AWAREMYO", "onPose: " + myo.toString());
                }

                @Override
                public void onOrientationData(Myo myo, long l, Quaternion quaternion) {
                    //Log.wtf("AWAREMYO", "onOrientationData: " + myo.toString());
                }

                @Override
                public void onAccelerometerData(Myo myo, long l, Vector3 vector3) {
                    //Log.wtf("AWAREMYO", "onAccelerometerData: " + myo.toString());
                }

                @Override
                public void onGyroscopeData(Myo myo, long l, Vector3 vector3) {
                    //Log.wtf("AWAREMYO", "onGyroscopeData: " + myo.toString());
                }

                @Override
                public void onRssi(Myo myo, long l, int i) {
                    Log.wtf("AWAREMYO", "onRssi: " + myo.toString());
                }
            });
        }

        BroadcastReceiver myoConnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String toggleStatus = intent.getStringExtra("toggleStatus");
                String macaddress = intent.getStringExtra("connMac");

                if (toggleStatus.equals("on")) {
                    Log.wtf("AWAREMYO", "Connecting to adjacent Myo...");
                    myoHub.attachToAdjacentMyo();
                }
                if (toggleStatus.equals("off")) {
                    Log.wtf("AWAREMYO", "Disonnecting from Myo...");
                    myoHub.detach(macaddress);
                }
            }
        };

        IntentFilter myoConnectFilter = new IntentFilter("MYO_CONNECT");
        registerReceiver(myoConnectReceiver, myoConnectFilter);
    }
}
