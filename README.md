# TVLowQualityBT

If you connect your most-likely low-powered TV box to a Bluetooth headset, this will do the following:

* disconnect the Headset/Call profile (using `setConnectionPolicy` to disable the headset profile on a device without disconnection afterwards requires your app have `android.permission.BLUETOOTH_PRIVILEGED`, which obviously we aren't going to get)

* set the LDAC codec to these settings:

    * Bluetooth audio LDAC codec playback quality: Balanced Audio and Connection Quality (660 kbps/606 kbps)

Why? On Android/Google TV, the Settings app is neutered and offers relatively little compared to your phone.

To build, make sure the android-31 android.jar from [Reginer's aosp-android-jar](https://github.com/Reginer/aosp-android-jar) is installed. Instructions can be found [here](https://github.com/1fexd/aosp-android-jar-mirror#installation). On that note, this probably won't work past Android 12.

You should make sure the setting in Android's App Info to remove permissions if app is unused is turned off.

## Thanks

* ChatGPT

* [ExtA2DP](https://github.com/anonymix007/ExtA2DP)
