AWARE Plugin: Myo
==========================

This plugin allows to collect the data from a Myo armband: IMU (acceleroeter, gyroscope, orientation) and EMG.

The plugin does not have UI context card, all the control functionalities are handled via notification.

The plugin supports an automatic connection to a nearby Myo (does not work properly on all devices) and manual connection via Myo MAC address.

![Plugin control notification](https://i.imgur.com/sXegTP6.png)


# Settings
Parameters adjustable on the dashboard and client:
- **status_plugin_myo**: (boolean) activate/deactivate plugin

# Providers
##  Template Data
> content://com.aware.plugin.myo.provider.myo/plugin_myo

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
myo_data | TEXT | IMU and EMG data in JSON format
