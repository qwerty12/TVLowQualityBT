# TVLowQualityBT

If you connect your most-likely low-powered TV box to a Bluetooth headset, this will do the following:

* disconnect the Headset/Call profile (using `setConnectionPolicy` to disable the headset profile on a device without disconnection afterwards requires your app have `android.permission.BLUETOOTH_PRIVILEGED`, which obviously we aren't going to get) after 30 seconds (to stop Android from rapidly reconnecting, and to give time for the A2DP profile to connect)

* set the LDAC codec to these settings:

    * Bluetooth audio LDAC codec playback quality: Optimized for Connection Quality (330kbps/303kbps)

Why? On Android/Google TV, the Settings app is neutered and offers relatively little compared to your phone.

To build, make sure the android-31 android.jar from [Reginer's aosp-android-jar](https://github.com/Reginer/aosp-android-jar) is installed. Instructions can be found [here](https://github.com/1fexd/aosp-android-jar-mirror#installation). On that note, this probably won't work past Android 12.

You should make sure the setting in Android's App Info to remove permissions if app is unused is turned off.

## Automating headset connection

You can connect to a Bluetooth headset with this using the following: `am startservice -n pk.q12.tvlowqualitybt/pk.q12.tvlowqualitybt.BluetoothService -a pk.q12.tvlowqualitybt.ACTION_A2DP_CONNECT --es alias 'XM3'`

Replace "XM3" with the name of your headset in the Bluetooth settings. Case-sensitive, but a substring search is performed (the name doesn't have to be exact) and the first matching device will be connected. You may need to be more specific.

If you're connecting to (or are already connected to with the A2DP profile) a Sony headset listed [here](https://github.com/ClusterM/sony-headphones-control/blob/d88b49d1e4e7d2f258848a3cb739c8b83aea1a51/README.md#sony-headphones-control), you can manipulate the noise cancelling / ambient sound setting.
To set the mode, add ` --ei xm_mode -1` to the `am` command above. Replace `-1` with one of the following modes:

* `-1`: noise cancelling/ambient sound off

* `0`: ambient sound on. Note, here you can append ` --ei xm_volume 20 --ez xm_voice false` to control the ambient sound volume (0-2) and voice optimisation setting

* `1`: wind cancelling on

* `2`: noise cancelling on

## Thanks

* ChatGPT

* [ExtA2DP](https://github.com/anonymix007/ExtA2DP)

* [sony-headphones-control](https://github.com/ClusterM/sony-headphones-control.git)
