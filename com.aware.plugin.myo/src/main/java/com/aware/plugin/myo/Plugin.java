package com.aware.plugin.myo;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.PermissionChecker;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

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
        ImuProcessor.ImuDataListener,
        Myo.BatteryCallback,
        Myo.ReadDeviceNameCallback{


    // Notification component variables
    private NotificationCompat.Builder nBuilder;
    private NotificationCompat.Action connectMac;
    private PendingIntent connectIntent;
    private PendingIntent disconnectIntent;
    private PendingIntent connectMacIntent;
    private BroadcastReceiver notifyReceiver = null;

    // Myo variables
    private MyoConnector connector = null;
    private Myo myo = null;
    private EmgProcessor emgProcessor = null;
    private ImuProcessor imuProcessor = null;
    //private List<Myo> myos = null;
    private boolean connected = false;

    // Bluetooth variables for MAC connection
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bt;

    // JSON objects variables
    private JSONObject myoDataObject, batteryObj;
    private JSONArray accArray, gyroArray, orientArray, emgArray, batteryArray;

    private static final int NOTIFICATION_ID = 1;

    private static final String MYO_TAG = "MYO_TAG";
    private static final String MYO_MAC_ADDRESS = "MYO_MAC_ADDRESS";

    // Broadcast receiver flags
    private static final String INTENT_CONNECT = "CONNECT";
    private static final String INTENT_CONNECT_MAC = "CONNECT_MAC";
    private static final String INTENT_DISCONNECT = "DISCONNECT";

    // Sampling keys for JSON handling
    private static final String SAMPLE_KEY_TIMESTAMP = "timestamp";
    private static final String SAMPLE_KEY_IMU_X = "x";
    private static final String SAMPLE_KEY_IMU_Y = "y";
    private static final String SAMPLE_KEY_IMU_Z = "z";
    private static final String SAMPLE_KEY_EMG_0 = "emg0";
    private static final String SAMPLE_KEY_EMG_1 = "emg1";
    private static final String SAMPLE_KEY_EMG_2 = "emg2";
    private static final String SAMPLE_KEY_EMG_3 = "emg3";
    private static final String SAMPLE_KEY_EMG_4 = "emg4";
    private static final String SAMPLE_KEY_EMG_5 = "emg5";
    private static final String SAMPLE_KEY_EMG_6 = "emg6";
    private static final String SAMPLE_KEY_EMG_7 = "emg7";
    private static final String SAMPLE_KEY_BATTERY_LEVEL = "battery_level";
    private static final String SAMPLE_KEY_MAC_ADDRESS = "mac";
    private static final String SAMPLE_KEY_LABEL = "label";
    private static final String SAMPLE_KEY_BATTERY = "battery";
    private static final String SAMPLE_KEY_IMU = "IMU";
    private static final String SAMPLE_KEY_EMG = "EMG";
    private static final String SAMPLE_KEY_MYO_DATA = "myo_data";
    private static final String SAMPLE_KEY_ACCELEROMETER = "accelerometer";
    private static final String SAMPLE_KEY_GYROSCOPE = "gyroscope";
    private static final String SAMPLE_KEY_ORIENTATION = "orientation";


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
        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        //Request to turn bluetooth on
        if (bluetoothAdapter == null) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBT.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(enableBT);
        }

        // Notification builder components
        nBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_myo)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentTitle(getString(R.string.notification_title));

        // Broadcast event intents
        connectIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(INTENT_CONNECT), PendingIntent.FLAG_CANCEL_CURRENT);
        disconnectIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(INTENT_DISCONNECT), PendingIntent.FLAG_CANCEL_CURRENT);
        connectMacIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(INTENT_CONNECT_MAC), PendingIntent.FLAG_CANCEL_CURRENT);

        // Connect via MAC action set up
        connectMac = new NotificationCompat.Action.Builder
                (0, getString(R.string.notification_action_connect_mac), connectMacIntent)
                .addRemoteInput(new RemoteInput.Builder(MYO_MAC_ADDRESS)
                        .setLabel(getString(R.string.type_mac))
                        .build())
                .build();

        //Show notification to control plugin functionalities
        nBuilder.setContentText(getString(R.string.notification_state_disconnected))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_state_disconnected)))
                .mActions.clear();
        nBuilder.addAction(0, getString(R.string.notification_action_connect), connectIntent).addAction(connectMac);
        startForeground(NOTIFICATION_ID, nBuilder.getNotification());
        nBuilder.setStyle(null);

        // JSON variables declaration
        myoDataObject = new JSONObject();
        accArray = new JSONArray();
        gyroArray = new JSONArray();
        orientArray = new JSONArray();
        emgArray = new JSONArray();
        batteryArray = new JSONArray();
        batteryObj = new JSONObject();
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);

        // List of required permission
        ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (PermissionChecker.checkSelfPermission(this, p) != PermissionChecker.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, true);

            //Initialize Connector on first Plugin run
            if (connector == null) connector = new MyoConnector(getApplicationContext());

            //BroadcastReceiver to listen Notification click events
            if (notifyReceiver == null) {

                notifyReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.d(MYO_TAG, "BROADCAST RECEIVED: ");

                        if (!connected && intent.getAction().equals(INTENT_CONNECT)) {

                            Log.d(MYO_TAG, "BROADCAST: CONNECT");
                            connectMyo();
                        }

                        if (!connected && intent.getAction().equals(INTENT_CONNECT_MAC)) {

                            Log.d(MYO_TAG, "BROADCAST: CONNECT VIA MAC");
                            connectMacMyo(RemoteInput.getResultsFromIntent(intent).getCharSequence(MYO_MAC_ADDRESS).toString());
                        }

                        if (connected && intent.getAction().equals(INTENT_DISCONNECT)) {

                            Log.d(MYO_TAG, "BROADCAST: DISCONNECT");
                            disconnectMyo();
                        }
                    }
                };

                IntentFilter myoConnectFilter = new IntentFilter();
                myoConnectFilter.addAction(INTENT_CONNECT);
                myoConnectFilter.addAction(INTENT_CONNECT_MAC);
                myoConnectFilter.addAction(INTENT_DISCONNECT);
                registerReceiver(notifyReceiver, myoConnectFilter);
            }

            //Initialise AWARE instance in plugin
            Aware.startAWARE(this);
        } else {

            // Request permissions if not granted yet
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return START_STICKY;
    }


    // Connecting to Myo
    private void connectMyo() {

        // Update notification status
        nBuilder.setContentText(getString(R.string.notification_state_scanning_myos))
                .setProgress(0,0,true)
                .mActions.clear();
        startForeground(NOTIFICATION_ID, nBuilder.getNotification());

        // First try to connect with autoscanning
        if (connector == null) connector = new MyoConnector(this);

        connector.scan(15000, new MyoConnector.ScannerCallback() {
            @Override
            public void onScanFinished(List<Myo> scannedMyos) {
                Log.d(MYO_TAG, "Found " + scannedMyos.size() + " Myo: " + scannedMyos.toString());

                // offer either to scan again or to connect straight wih MAC if no Myos were found
                if (scannedMyos.size() == 0) {
                    Log.d(Plugin.MYO_TAG, "No adjacent Myos found");

                    // inflate inline input for N+ versions
                    // TODO for <N versions
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        nBuilder.setContentText(getString(R.string.notification_state_no_myos_found))
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_state_no_myos_found)))
                                .setProgress(0,0,false)
                                .mActions.clear();
                        nBuilder.addAction(0, getString(R.string.notification_action_connect), connectIntent).addAction(connectMac);
                        startForeground(NOTIFICATION_ID, nBuilder.getNotification());
                        nBuilder.setStyle(null);
                    }

                } else {
                    // connect to found Myo
                    // Update notification status
                    nBuilder.setContentText(getString(R.string.notification_state_connecting))
                            .setProgress(0,0,true)
                            .mActions.clear();
                    startForeground(NOTIFICATION_ID, nBuilder.getNotification());

                    myo = scannedMyos.get(0);
                    myo.addConnectionListener(Plugin.this);
                    myo.connect();
                }
            }
        });
    }

    // Connecting to Myo via Mac
    private void connectMacMyo(String mac) {

        nBuilder.setContentText(getString(R.string.notification_state_connecting))
                .setProgress(0,0,true)
                .mActions.clear();
        startForeground(NOTIFICATION_ID, nBuilder.getNotification());

        //"C4:EF:50:4D:29:BD"
        if (bluetoothAdapter == null) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bt = bluetoothAdapter.getRemoteDevice(mac);

        myo = new Myo(getApplicationContext(), bt);
        myo.addConnectionListener(Plugin.this);
        myo.connect();

        // Remove values and set notification to default state if not connected after 100 seconds
        final Handler handler = new Handler();
        final Runnable r = new Runnable() {
            public void run() {
                if (!connected) {
                    nBuilder.setContentText(getString(R.string.notification_state_disconnected))
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_state_disconnected)))
                            .setProgress(0,0,false)
                            .mActions.clear();
                    nBuilder.addAction(0, getString(R.string.notification_action_connect), connectIntent).addAction(connectMac);
                    startForeground(NOTIFICATION_ID, nBuilder.getNotification());
                    nBuilder.setStyle(null);

                    bluetoothAdapter = null;
                    bt = null;
                    myo = null;
                }
            }
        };
        handler.postDelayed(r, 100000);
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
                    connected = false;

                    nBuilder.setContentText(getString(R.string.notification_state_disconnected))
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_state_disconnected)))
                            .setProgress(0,0,false)
                            .mActions.clear();
                    nBuilder.addAction(0, getString(R.string.notification_action_connect),connectIntent).addAction(connectMac);
                    startForeground(NOTIFICATION_ID, nBuilder.getNotification());
                    nBuilder.setStyle(null);


                    try {
                        myoDataObject.put(SAMPLE_KEY_BATTERY, batteryArray);

                        JSONObject imuObject = new JSONObject();
                        imuObject.put(SAMPLE_KEY_ACCELEROMETER, accArray);
                        imuObject.put(SAMPLE_KEY_GYROSCOPE, gyroArray);
                        imuObject.put(SAMPLE_KEY_ORIENTATION, orientArray);

                        JSONObject result = new JSONObject();
                        result.put(SAMPLE_KEY_MYO_DATA, myoDataObject);
                        result.put(SAMPLE_KEY_IMU, imuObject);
                        result.put(SAMPLE_KEY_EMG, emgArray);

                        // Recording result to local .txt file
                        // for testing only
                        File root = new File(Environment.getExternalStorageDirectory().toString());
                        Long tsLong = System.currentTimeMillis()/1000;
                        String ts = tsLong.toString();
                        File gpxfile = new File(root, "samples" + ts +".txt");
                        FileWriter writer = null;
                        try {
                            writer = new FileWriter(gpxfile);
                            writer.append(result.toString());
                            writer.flush();
                            writer.close();
                            Log.d(MYO_TAG, "Logging done");
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.d(MYO_TAG, "Logging not done");
                            Log.d(MYO_TAG, e.getMessage());
                        }

                        //TEST
                        // Inserting data to database
//                        ContentValues values = new ContentValues();
//                        values.put(Provider.Myo_Data.TIMESTAMP, System.currentTimeMillis());
//                        values.put(Provider.Myo_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
//                        values.put(Provider.Myo_Data.MYO_DATA, result.toString());
//                        getApplicationContext().getContentResolver().insert(Provider.Myo_Data.CONTENT_URI, values);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

//                    removeValues();
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
        //removeValues();

        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Stop AWARE instance in plugin
        Aware.stopAWARE(this);
    }


    //Myo connection state listener
    @Override
    public void onConnectionStateChanged(final BaseMyo baseMyo, BaseMyo.ConnectionState state) {

        if (state == BaseMyo.ConnectionState.CONNECTED) {
            Log.d(MYO_TAG, "STATE CONNECTED");

            // Applying settings to connected Myo
            // First run does not work
            Myo.MyoCommandCallback callbackSleep = new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "Test Sleep applied");
                }
            };

            Myo.MyoCommandCallback callbackUnlock = new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "Test Unlock applied");
                }
            };

            Myo.MyoCommandCallback callbackWrite = new Myo.MyoCommandCallback() {
                @Override
                public void onCommandDone(Myo myo, MyoMsg msg) {
                    Log.d(MYO_TAG, "Test WriteMode applied");
                }
            };
            myo.setConnectionSpeed(BaseMyo.ConnectionSpeed.HIGH);
            myo.setConnectionSpeed(BaseMyo.ConnectionSpeed.HIGH);
            myo.writeSleepMode(MyoCmds.SleepMode.NEVER,callbackSleep);
            myo.writeSleepMode(MyoCmds.SleepMode.NEVER,callbackSleep);
            myo.writeUnlock(MyoCmds.UnlockType.HOLD, callbackUnlock);
            myo.writeUnlock(MyoCmds.UnlockType.HOLD, callbackUnlock);
            myo.writeMode(MyoCmds.EmgMode.FILTERED, MyoCmds.ImuMode.RAW, MyoCmds.ClassifierMode.DISABLED,callbackWrite);
            myo.writeMode(MyoCmds.EmgMode.FILTERED, MyoCmds.ImuMode.RAW, MyoCmds.ClassifierMode.DISABLED,callbackWrite);

            // Second run makes actual changes
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
                public void onCommandDone(Myo mMyo, MyoMsg msg) {
                    // Setting up Imu and EMG sensors
                    Log.d(MYO_TAG, "EMG and Imu: +");
                    imuProcessor = new ImuProcessor();
                    emgProcessor = new EmgProcessor();
                    imuProcessor.addListener(Plugin.this);
                    emgProcessor.addListener(Plugin.this);
                    myo.addProcessor(imuProcessor);
                    myo.addProcessor(emgProcessor);

                    nBuilder.setContentText(getString(R.string.notification_state_connected))
                            .setProgress(0,0,false)
                            .mActions.clear();
                    nBuilder.addAction(0, getString(R.string.notification_action_disconnect), disconnectIntent);
                    startForeground(NOTIFICATION_ID, nBuilder.getNotification());
                    connected = true;
//                    uiConnected(true);

                    myo.readDeviceName(Plugin.this);
                    try {
                        myoDataObject.put(SAMPLE_KEY_MAC_ADDRESS, myo.getDeviceAddress());;

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

        }

        if (state == BaseMyo.ConnectionState.DISCONNECTED) {
            Log.d(MYO_TAG, "STATE DISCONNECTED");
            Log.d(MYO_TAG, "Disconnected from Myo: " + baseMyo.toString());
            connected = false;

            nBuilder.setContentText(getString(R.string.notification_state_disconnected))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_state_disconnected)))
                    .setProgress(0,0,false)
                    .mActions.clear();
            nBuilder.addAction(0, getString(R.string.notification_action_connect), connectIntent).addAction(connectMac);
            startForeground(NOTIFICATION_ID, nBuilder.getNotification());
            nBuilder.setStyle(null);

//            uiConnected(false);
            //removeValues();
        }

    }


    //Myo device name reader
    @Override
    public void onDeviceNameRead(Myo myo, MyoMsg msg, String deviceName) {
        try {
            myoDataObject.put(SAMPLE_KEY_LABEL, deviceName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //Myo battery level listener
    @Override
    public void onBatteryLevelRead(Myo myo, MyoMsg msg, int batteryLevel) {
        try {
            // Record battery level to JSON object
            batteryObj.put(SAMPLE_KEY_BATTERY_LEVEL, batteryLevel);
            batteryArray.put(batteryObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(MYO_TAG, "LISTENER: BATTERY: " + batteryLevel);
    }


    //Myo IMU data (accelerometer, gyroscope, orientation) listener
    private long mLastImuUpdate = 0;
    private long mLastBatteryUpdate = 0;
    @Override
    public void onNewImuData(ImuData imuData) {
        // Check for sensor updates twice per second
        if (System.currentTimeMillis() - mLastImuUpdate > 500) {
            mLastImuUpdate = System.currentTimeMillis();

            try {
                JSONObject accObj = new JSONObject();
                accObj.put(SAMPLE_KEY_TIMESTAMP, imuData.getTimeStamp())
                        .put(SAMPLE_KEY_IMU_X, imuData.getAccelerometerData()[0])
                        .put(SAMPLE_KEY_IMU_Y, imuData.getAccelerometerData()[1])
                        .put(SAMPLE_KEY_IMU_Z, imuData.getAccelerometerData()[2]);
                accArray.put(accObj);
                accObj = null;

                JSONObject gyroObj = new JSONObject();
                gyroObj.put(SAMPLE_KEY_TIMESTAMP, imuData.getTimeStamp())
                        .put(SAMPLE_KEY_IMU_X, imuData.getGyroData()[0])
                        .put(SAMPLE_KEY_IMU_Y, imuData.getGyroData()[1])
                        .put(SAMPLE_KEY_IMU_Z, imuData.getGyroData()[2]);
                gyroArray.put(gyroObj);
                gyroObj = null;

                JSONObject orientObj = new JSONObject();
                orientObj.put(SAMPLE_KEY_TIMESTAMP, imuData.getTimeStamp())
                        .put(SAMPLE_KEY_IMU_X, imuData.getOrientationData()[0])
                        .put(SAMPLE_KEY_IMU_Y, imuData.getOrientationData()[1])
                        .put(SAMPLE_KEY_IMU_Z, imuData.getOrientationData()[2]);
                orientArray.put(orientObj);
                orientObj = null;

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Check for battery level every 2 minutes
        if (System.currentTimeMillis() - mLastBatteryUpdate > 120000) {
            mLastBatteryUpdate = System.currentTimeMillis();
            myo.readBatteryLevel(Plugin.this);

            try {
                // Record battery sampling timestamp to JSON object
                batteryObj.put(SAMPLE_KEY_TIMESTAMP, imuData.getTimeStamp());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    //Myo EMG data listener
    private long mLastEmgUpdate = 0;
    @Override
    public void onNewEmgData(EmgData emgData) {
        // Check for Emg updates twice per second
        if (System.currentTimeMillis() - mLastEmgUpdate > 500) {
            mLastEmgUpdate = System.currentTimeMillis();

            try {
                JSONObject emgObj = new JSONObject();
                emgObj.put(SAMPLE_KEY_TIMESTAMP, emgData.getTimestamp());
                emgObj.put(SAMPLE_KEY_EMG_0, emgData.getData()[0]);
                emgObj.put(SAMPLE_KEY_EMG_1, emgData.getData()[1]);
                emgObj.put(SAMPLE_KEY_EMG_2, emgData.getData()[2]);
                emgObj.put(SAMPLE_KEY_EMG_3, emgData.getData()[3]);
                emgObj.put(SAMPLE_KEY_EMG_4, emgData.getData()[4]);
                emgObj.put(SAMPLE_KEY_EMG_5, emgData.getData()[5]);
                emgObj.put(SAMPLE_KEY_EMG_6, emgData.getData()[6]);
                emgObj.put(SAMPLE_KEY_EMG_7, emgData.getData()[7]);

                emgArray.put(emgObj);
                emgObj = null;

            } catch (JSONException e) {
                e.printStackTrace();;
            }
        }
    }



//    // Initializing Connector and connecting to Myo
//    private void connectMyo() {
//        if (connector == null) connector = new MyoConnector(this);
//        connector.scan(5000, new MyoConnector.ScannerCallback() {
//            @Override
//            public void onScanFinished(List<Myo> scannedMyos) {
//
//                Log.d(MYO_TAG, "Found " + scannedMyos.size() + " Myo: " + scannedMyos.toString());
//
//                if (scannedMyos.size() == 0) {
//                    Log.d(Plugin.MYO_TAG, "Connection failed, cannot find adjacent Myo");
//                    startForeground(NOTIFICATION_ID, showNotification("Myo is disconnected"));
//                    if (awareSensor != null) awareSensor.onMyoConnectionFailed();
//
//                } else {
//                    myo = scannedMyos.get(0);
//                    myo.addConnectionListener(Plugin.this);
//                    myo.connect();
//                }
//            }
//        });
//    }

//    // Disconnecting from Myo
//    private void disconnectMyo() {
//        if (myo != null) {
//            //Applying settings to disconnected Myo
//            myo.setConnectionSpeed(BaseMyo.ConnectionSpeed.BALANCED);
//            myo.writeSleepMode(MyoCmds.SleepMode.NORMAL, new Myo.MyoCommandCallback() {
//                @Override
//                public void onCommandDone(Myo myo, MyoMsg msg) {
//                    Log.d(MYO_TAG, "Sleep mode: -");
//                }
//            });
//            myo.writeMode(MyoCmds.EmgMode.NONE, MyoCmds.ImuMode.NONE, MyoCmds.ClassifierMode.DISABLED, new Myo.MyoCommandCallback() {
//                @Override
//                public void onCommandDone(Myo myo, MyoMsg msg) {
//                    Log.d(MYO_TAG, "EMG and Imu: -");
//
//                    // Disconnecting from Myo and aplying UI changes
//                    Log.d(MYO_TAG, "Disconnected");
//                    myo.disconnect();
//                    startForeground(NOTIFICATION_ID, showNotification("Myo is disconnected"));
//                    removeValues();
//                    if (awareSensor != null) awareSensor.onMyoDisconnected();
//                }
//            });
//        }
//    }

//    // Removing values when Myo is detached
//    private void removeValues() {
//        if (myo != null) {
//            if (emgProcessor != null) {
//                myo.removeProcessor(emgProcessor);
//                emgProcessor = null;
//            }
//            if (imuProcessor != null) {
//                myo.removeProcessor(imuProcessor);
//                imuProcessor = null;
//            }
//            myo.removeConnectionListener(this);
//            myo = null;
//        }
//
//        if (connector != null) {
//            connector = null;
//        }
//    }

}
