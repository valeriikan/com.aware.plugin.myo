package com.aware.plugin.myo;

import android.Manifest;
import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
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

import java.util.List;

import eu.darken.myolib.BaseMyo;
import eu.darken.myolib.Myo;
import eu.darken.myolib.MyoCmds;
import eu.darken.myolib.MyoConnector;
import eu.darken.myolib.msgs.MyoMsg;
import eu.darken.myolib.processor.emg.EmgData;
import eu.darken.myolib.processor.emg.EmgProcessor;
import eu.darken.myolib.processor.imu.ImuData;
import eu.darken.myolib.processor.imu.ImuProcessor;

public class Plugin extends Aware_Plugin implements
        BaseMyo.ConnectionListener,
        EmgProcessor.EmgDataListener,
        Myo.BatteryCallback,
        ImuProcessor.ImuDataListener {

    public static final String ACTION_PLUGIN_MYO_CONNECTED = "ACTION_PLUGIN_MYO_CONNECTED";
    public static final String ACTION_PLUGIN_MYO_CONNECTION_FAILED = "ACTION_PLUGIN_MYO_CONNECTION_FAILED";
    public static final String ACTION_PLUGIN_MYO_DISCONNECTED = "ACTION_PLUGIN_MYO_DISCONNECTED";
    public static final String ACTION_PLUGIN_MYO_BATTERY_LEVEL = "ACTION_PLUGIN_MYO_BATTERY_LEVEL";
    public static final String ACTION_PLUGIN_MYO_GYROSCOPE = "ACTION_PLUGIN_MYO_GYROSCOPE";
    public static final String ACTION_PLUGIN_MYO_EMG = "ACTION_PLUGIN_MYO_EMG";
    public static final String MYO_MAC_ADDRESS = "MYO_MAC_ADDRESS";
    public static final String MYO_BATTERY_LEVEL = "MYO_BATTERY_LEVEL";
    public static final String MYO_GYROVALUES = "MYO_GYROVALUES";
    public static final String MYO_EMG = "MYO_EMG";
    public static final String MYO_TAG = "MYO_TAG";

    @Override
    public void onCreate() {
        super.onCreate();

        //This allows plugin data to be synced on demand from broadcast Aware#ACTION_AWARE_SYNC_DATA
        AUTHORITY = Provider.getAuthority(this);

        TAG = "AWARE::" + getResources().getString(R.string.app_name);

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

        //Request to turn bluetooth on
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBT.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(enableBT);
        }
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
        void onMyoConnectionFailed();
        void onMyoDisconnected();
        void onMyoBatteryChanged(String battery);
        void onMyoGyroChanged(ContentValues data);
        void onMyoEmgChanged(ContentValues data);
    }

    private BroadcastReceiver myoConnectReceiver = null;
    private MyoConnector connector = null;
    private Myo myo = null;
    private EmgProcessor emgProcessor = null;
    private ImuProcessor imuProcessor = null;
    //private List<Myo> myos = null;

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, true);

            //Initialize Connector on first Plugin run
            //if (connector == null) connector = new MyoConnector(this);

            //Initialize listener to Button events in ContextCard
            if (myoConnectReceiver == null) {

                startForeground(1, showNotification("Myo is not connected"));
                myoConnectReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        // Checks the toggle status
                        String toggleStatus = intent.getStringExtra(ContextCard.CONTEXT_TOGGLE_STATUS);
                        if (toggleStatus.equals(ContextCard.CONTEXT_TOGGLE_ON)) {
                            Log.d(Plugin.MYO_TAG, "Connecting to Myo...");
                            connectMyo();
                        }

                        if (toggleStatus.equals(ContextCard.CONTEXT_TOGGLE_OFF)) {
                            Log.d(Plugin.MYO_TAG, "Disonnecting from Myo...");
                            disconnectMyo();
                        }
                    }
                };

                IntentFilter myoConnectFilter = new IntentFilter(ContextCard.CONTEXT_ACTION_MYO_CONNECT);
                registerReceiver(myoConnectReceiver, myoConnectFilter);
            }

            //Sensor observer set up
            setSensorObserver(new AWARESensorObserver() {
                @Override
                public void onMyoConnected(String macaddress) {
                    Intent myoConnected = new Intent(Plugin.ACTION_PLUGIN_MYO_CONNECTED);
                    myoConnected.putExtra(Plugin.MYO_MAC_ADDRESS, macaddress);
                    sendBroadcast(myoConnected);
                }

                @Override
                public void onMyoConnectionFailed() {
                    Intent myoConnectionFailed = new Intent(Plugin.ACTION_PLUGIN_MYO_CONNECTION_FAILED);
                    sendBroadcast(myoConnectionFailed);
                }

                @Override
                public void onMyoDisconnected() {
                    Intent myoDisconnected = new Intent(Plugin.ACTION_PLUGIN_MYO_DISCONNECTED);
                    sendBroadcast(myoDisconnected);
                }

                @Override
                public void onMyoBatteryChanged(String battery) {
                    Intent myoBattery = new Intent(Plugin.ACTION_PLUGIN_MYO_BATTERY_LEVEL);
                    myoBattery.putExtra(Plugin.MYO_BATTERY_LEVEL, battery);
                    sendBroadcast(myoBattery);
                }

                @Override
                public void onMyoGyroChanged(ContentValues data) {
                    Intent myoGyroscope = new Intent(Plugin.ACTION_PLUGIN_MYO_GYROSCOPE);
                    myoGyroscope.putExtra(Plugin.MYO_GYROVALUES, data);
                    sendBroadcast(myoGyroscope);
                }

                @Override
                public void onMyoEmgChanged(ContentValues data) {
                    Intent myoEmg = new Intent(Plugin.ACTION_PLUGIN_MYO_EMG);
                    myoEmg.putExtra(Plugin.MYO_EMG, data);
                    sendBroadcast(myoEmg);
                }
            });

            //Initialise AWARE instance in plugin
            Aware.startAWARE(this);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //Turn off the sync-adapter if part of a study
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Provider.getAuthority(this),
                Bundle.EMPTY
        );

        //Removing values
        myoConnectReceiver = null;
        removeValues();

        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Stop AWARE instance in plugin
        Aware.stopAWARE(this);
    }

    //Myo connection state listener
    @Override
    public void onConnectionStateChanged(final BaseMyo baseMyo, BaseMyo.ConnectionState state) {

        if (state == BaseMyo.ConnectionState.CONNECTED) {
            Log.d(MYO_TAG, "STATE CONNECTED");

            //Applying settings to connected Myo
            myo.setConnectionSpeed(BaseMyo.ConnectionSpeed.HIGH);
            myo.writeSleepMode(MyoCmds.SleepMode.NEVER, new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "Sleep mode: +");
                }
            });
            myo.writeUnlock(MyoCmds.UnlockType.HOLD, new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "Unlock: +");
                    myo.writeVibrate(MyoCmds.VibrateType.LONG, null);
                }
            });
            myo.writeMode(MyoCmds.EmgMode.FILTERED, MyoCmds.ImuMode.RAW, MyoCmds.ClassifierMode.DISABLED, new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    // Setting up Imu and EMG sensors
                    Log.d(MYO_TAG, "EMG and Imu: +");
                    imuProcessor = new ImuProcessor();
                    emgProcessor = new EmgProcessor();
                    imuProcessor.addListener(Plugin.this);
                    emgProcessor.addListener(Plugin.this);
                    myo.addProcessor(imuProcessor);
                    myo.addProcessor(emgProcessor);

                    // Applying UI updates
                    Log.d(Plugin.MYO_TAG, "Connected to: " + baseMyo.toString());
                    startForeground(1, showNotification("Myo is connected"));
                    if (awareSensor != null) awareSensor.onMyoConnected(baseMyo.getDeviceAddress());
                }
            });
        }

        if (state == BaseMyo.ConnectionState.DISCONNECTED) {
            Log.d(MYO_TAG, "STATE DISCONNECTED");
            Log.d(Plugin.MYO_TAG, "Disconnected from Myo: " + baseMyo.toString());
            startForeground(1, showNotification("Myo is disconnected"));
            if (awareSensor != null) awareSensor.onMyoDisconnected();

            removeValues();
        }
    }

    //Myo battery level listener
    private int batteryLvl = -100;
    @Override
    public void onBatteryLevelRead(Myo myo, MyoMsg msg, int batteryLevel) {
        if (batteryLvl != batteryLevel) {
            batteryLvl = batteryLevel;
            if (awareSensor != null) awareSensor.onMyoBatteryChanged(String.valueOf(batteryLvl));
        }
    }

    //Myo Imu data (accelerometer, gyroscope, orientation) listener
    private long mLastImuUpdate = 0;
    private long mLastBatteryUpdate = 0;
    @Override
    public void onNewImuData(ImuData imuData) {
        // Check for Gyro updates twice per second
        if (System.currentTimeMillis() - mLastImuUpdate > 500) {
            ContentValues gyroData = new ContentValues();
            gyroData.put("gyroX", imuData.getGyroData()[0]);
            gyroData.put("gyroY", imuData.getGyroData()[1]);
            gyroData.put("gyroZ", imuData.getGyroData()[2]);
            if (awareSensor != null) awareSensor.onMyoGyroChanged(gyroData);
            mLastImuUpdate = System.currentTimeMillis();
        }

        // Check for battery level every 2 minutes
        if (System.currentTimeMillis() - mLastBatteryUpdate > 120000) {
            myo.readBatteryLevel(Plugin.this);
            mLastBatteryUpdate = System.currentTimeMillis();
        }
    }

    //Myo EMG data listener
    private long mLastEmgUpdate = 0;
    @Override
    public void onNewEmgData(EmgData emgData) {
        // Check for Emg updates twice per second
        if (System.currentTimeMillis() - mLastEmgUpdate > 500) {
            byte[] data = emgData.getData();
            ContentValues emg = new ContentValues();
            emg.put("emg0", String.valueOf(data[0]));
            emg.put("emg1", String.valueOf(data[1]));
            emg.put("emg2", String.valueOf(data[2]));
            emg.put("emg3", String.valueOf(data[3]));
            emg.put("emg4", String.valueOf(data[4]));
            emg.put("emg5", String.valueOf(data[5]));
            emg.put("emg6", String.valueOf(data[6]));
            emg.put("emg7", String.valueOf(data[7]));
            if (awareSensor != null) awareSensor.onMyoEmgChanged(emg);
            mLastEmgUpdate = System.currentTimeMillis();
        }
    }

    // Initializing Connector and connecting to Myo
    private void connectMyo() {
        if (connector == null) connector = new MyoConnector(this);
        connector.scan(5000, new MyoConnector.ScannerCallback() {
            @Override
            public void onScanFinished(List<Myo> scannedMyos) {

                Log.d(MYO_TAG, "Found " + scannedMyos.size() + " Myo: " + scannedMyos.toString());

                if (scannedMyos.size() == 0) {
                    Log.d(Plugin.MYO_TAG, "Connection failed, cannot find adjacent Myo");
                    startForeground(1, showNotification("Myo is disconnected"));
                    if (awareSensor != null) awareSensor.onMyoConnectionFailed();

                } else {
                    myo = scannedMyos.get(0);
                    myo.addConnectionListener(Plugin.this);
                    myo.connect();
                }
            }
        });
    }

    // Disconnecting from Myo
    private void disconnectMyo() {
        if (myo != null) {
            //Applying settings to disconnected Myo
            myo.setConnectionSpeed(BaseMyo.ConnectionSpeed.BALANCED);
            myo.writeSleepMode(MyoCmds.SleepMode.NORMAL, new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "Sleep mode: -");
                }
            });
            myo.writeMode(MyoCmds.EmgMode.NONE, MyoCmds.ImuMode.NONE, MyoCmds.ClassifierMode.DISABLED, new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "EMG and Imu: -");

                    // Disconnecting from Myo and aplying UI changes
                    Log.d(MYO_TAG, "Disconnected");
                    myo.disconnect();
                    startForeground(1, showNotification("Myo is disconnected"));
                    removeValues();
                    if (awareSensor != null) awareSensor.onMyoDisconnected();
                }
            });
        }
    }

    // Removing values when Myo is detached
    private void removeValues() {
        if (myo != null) {
            if (emgProcessor != null) {
                myo.removeProcessor(emgProcessor);
                emgProcessor = null;
            }
            if (imuProcessor != null) {
                myo.removeProcessor(imuProcessor);
                imuProcessor = null;
            }
            myo.removeConnectionListener(this);
            myo = null;
        }

        if (connector != null) {
            connector = null;
        }
    }

    // Notification set up
    private Notification showNotification(String notifyText) {
        return new Notification.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_myo)
                .setOngoing(true)
                .setContentTitle("AWARE: Myo armband")
                .setContentText(notifyText)
                .getNotification();
    }
}
