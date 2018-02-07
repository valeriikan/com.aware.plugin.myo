package com.aware.plugin.myo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
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
    
    public static final String ACTION_PLUGIN_MYO_CONNECTED = "ACTION_PLUGIN_MYO_CONNECTED";
    public static final String ACTION_PLUGIN_MYO_DISCONNECTED = "ACTION_PLUGIN_MYO_DISCONNECTED";
    public static final String ACTION_PLUGIN_MYO_GYROSCOPE = "ACTION_PLUGIN_MYO_GYROSCOPE";
    public static final String ACTION_PLUGIN_MYO_POSE = "ACTION_PLUGIN_MYO_POSE";
    public static final String MAC_ADDRESS = "MAC_ADDRESS";
    public static final String MYO_GYROVALUES = "MYO_GYROVALUES";
    public static final String MYO_POSE = "MYO_POSE";
    public static final String MYO_TAG = "MYO_TAG";

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
        void onMyoConnected(String macaddress);
        void onMyoDisconnected();
        void onMyoGyroscopeChanged(ContentValues data);
        void onMyoPoseChanged(String pose);
    }

    // Myo component
    private Hub myoHub;
    private String myo_macaddress = null;

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

            if (myoHub == null) {
                setupMyo();
                startForeground(1, showNotification("Myo is not connected"));
            }

            setSensorObserver(new AWARESensorObserver() {
                @Override
                public void onMyoConnected(String macaddress) {
                    Intent myoConnected = new Intent(Plugin.ACTION_PLUGIN_MYO_CONNECTED);
                    myoConnected.putExtra(Plugin.MAC_ADDRESS, macaddress);
                    sendBroadcast(myoConnected);
                }

                @Override
                public void onMyoDisconnected() {
                    Intent myoDisconnected = new Intent(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED);
                    sendBroadcast(myoDisconnected);
                }

                @Override
                public void onMyoGyroscopeChanged(ContentValues data) {
                    Intent myoGyroscope = new Intent(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE);
                    myoGyroscope.putExtra(Plugin.MYO_GYROVALUES, data);
                    sendBroadcast(myoGyroscope);
                }

                @Override
                public void onMyoPoseChanged(String pose) {
                    Intent myoPose = new Intent(Plugin.ACTION_PLUGIN_MYO_POSE);
                    myoPose.putExtra(Plugin.MYO_POSE, pose);
                    sendBroadcast(myoPose);
                }
            });
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
        if (myoHub!=null && myo_macaddress!=null) {
            myoHub.detach(myo_macaddress);
            stopForeground(true);
        }

        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Stop AWARE instance in plugin
        Aware.stopAWARE(this);
    }

    // Initializing Hub and attaching listener to it
    public void setupMyo() {

        Log.wtf(Plugin.MYO_TAG, "setupMyo started");

        myoHub = Hub.getInstance();
        myoHub.init(getApplicationContext());

        myoHub.addListener(new DeviceListener() {
            @Override
            public void onAttach(Myo myo, long l) {
                Log.wtf(Plugin.MYO_TAG, "Attached to Myo:" + myo.toString());
            }

            @Override
            public void onDetach(Myo myo, long l) {
                Log.wtf(Plugin.MYO_TAG, "Detached Myo:" + myo.toString());
            }

            @Override
            public void onConnect(Myo myo, long l) {
                Log.d(Plugin.MYO_TAG, "Connected to Myo:" + myo.toString());
                startForeground(1, showNotification("Myo is connected"));

                if (awareSensor != null) awareSensor.onMyoConnected(myo.getMacAddress());
            }

            @Override
            public void onDisconnect(Myo myo, long l) {
                Log.wtf(Plugin.MYO_TAG, "Disconnected from Myo:" + myo.toString());
                startForeground(1, showNotification("Myo is disconnected"));

                if (awareSensor != null) awareSensor.onMyoDisconnected();
            }

            @Override
            public void onArmSync(Myo myo, long l, Arm arm, XDirection xDirection) {
               // Log.wtf(Plugin.MYO_TAG, "onArmSync: " + myo.toString());
            }

            @Override
            public void onArmUnsync(Myo myo, long l) {
                //Log.wtf(Plugin.MYO_TAG, "onArmUnsync: " + myo.toString());
            }

            @Override
            public void onUnlock(Myo myo, long l) {
                //Log.wtf(Plugin.MYO_TAG, "Unlock: " + myo.toString());
            }

            @Override
            public void onLock(Myo myo, long l) {
                //Log.wtf(Plugin.MYO_TAG, "onLock: " + myo.toString());
            }

            @Override
            public void onPose(Myo myo, long l, Pose pose) {
                Log.wtf(Plugin.MYO_TAG, "onPose: " + pose.name());
                if (awareSensor != null) awareSensor.onMyoPoseChanged(pose.name());
            }

            @Override
            public void onOrientationData(Myo myo, long l, Quaternion quaternion) {
                //Log.wtf(Plugin.MYO_TAG, "onOrientationData: " + myo.toString());
            }

            @Override
            public void onAccelerometerData(Myo myo, long l, Vector3 vector3) {
                //Log.wtf(Plugin.MYO_TAG, "onAccelerometerData: " + myo.toString());
            }

            @Override
            public void onGyroscopeData(Myo myo, long l, Vector3 vector3) {
                //Log.wtf(Plugin.MYO_TAG, "onGyroscopeData: " + myo.toString());
                ContentValues gyroscope = new ContentValues();
                gyroscope.put("x", vector3.x());
                gyroscope.put("y", vector3.y());
                gyroscope.put("z", vector3.z());

                if (awareSensor != null) awareSensor.onMyoGyroscopeChanged(gyroscope);
            }

            @Override
            public void onRssi(Myo myo, long l, int i) {
                //Log.wtf(Plugin.MYO_TAG, "onRssi: " + myo.toString());
            }
        });

        BroadcastReceiver myoConnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String toggleStatus = intent.getStringExtra("toggleStatus");
                myo_macaddress = intent.getStringExtra("connMac");

                if (toggleStatus.equals("on")) {
                    Log.wtf(Plugin.MYO_TAG, "Connecting to adjacent Myo...");
                    myoHub.attachToAdjacentMyo();

                }
                if (toggleStatus.equals("off")) {
                    Log.wtf(Plugin.MYO_TAG, "Disonnecting from Myo...");
                    myoHub.detach(myo_macaddress);
                }
            }
        };

        IntentFilter myoConnectFilter = new IntentFilter("MYO_CONNECT");
        registerReceiver(myoConnectReceiver, myoConnectFilter);

    }

    private void showNotification1() {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setOngoing(true)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!");

        int mNotificationId = 001;
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    private Notification showNotification(String notifyText) {

        return new Notification.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setContentTitle("AWARE: Myo armband")
                .setContentText(notifyText)
                .getNotification();
    }
}
