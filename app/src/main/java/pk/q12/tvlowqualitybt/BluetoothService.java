package pk.q12.tvlowqualitybt;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static boolean isRunning;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothA2dp a2dpProfile;
    private BluetoothHeadset headsetProfile;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        startForeground(1, new Notification.Builder(this, getString(R.string.notification_channel))
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("TV Low Quality BT")
                .setContentText("Bluetooth Service is active")
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + getPackageName())), PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .build());

        HiddenApiBypass.addHiddenApiExemptions("");

        bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        if ((bluetoothManager == null) || (bluetoothAdapter = bluetoothManager.getAdapter()) == null) {
            stopSelf();
            return;
        }

        getBluetoothProfiles();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        //filter.addAction(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED);
        registerReceiver(activeDeviceReceiver, filter);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        unregisterReceiver(activeDeviceReceiver);
        unregisterBluetoothProfiles();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void disconnectHeadsetProfile(BluetoothDevice device) {
        if (device == null || headsetProfile == null)
            return;

        try {
            headsetProfile.disconnect(device);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setLdacSettings(BluetoothDevice device) {
        if (device == null || a2dpProfile == null)
            return;

        try {
            BluetoothCodecStatus codecStatus = a2dpProfile.getCodecStatus(device);
            BluetoothCodecConfig currentConfig = codecStatus.getCodecConfig();
            if (currentConfig.getCodecType() != BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC)
                return;

            BluetoothCodecConfig newConfig =
                    new BluetoothCodecConfig(currentConfig.getCodecType(),
                            currentConfig.getCodecPriority(),
                            currentConfig.getSampleRate(),
                            currentConfig.getBitsPerSample(),
                            currentConfig.getChannelMode(),
                            1001, // Balanced Audio And Connection Quality (660kbps/606kbps)
                            currentConfig.getCodecSpecific2(),
                            currentConfig.getCodecSpecific3(),
                            currentConfig.getCodecSpecific4());

            for (int i = 0; i < 3; ++i) {
                a2dpProfile.setCodecConfigPreference(device, newConfig);
                Thread.sleep(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getBluetoothProfiles() {
        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);
        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
    }

    private void unregisterBluetoothProfiles() {
        if (headsetProfile != null) {
            if (bluetoothAdapter != null)
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile);
            headsetProfile = null;
        }

        if (a2dpProfile != null) {
            if (bluetoothAdapter != null)
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile);
            a2dpProfile = null;
        }
    }

    private final BroadcastReceiver activeDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED.equals(action)) {
                setLdacSettings(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
            } else if (BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED.equals(action)) {
                disconnectHeadsetProfile(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
            }
        }
    };

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                headsetProfile = (BluetoothHeadset) proxy;

                if (headsetProfile != null) {
                    for (BluetoothDevice connectedDevice : headsetProfile.getConnectedDevices())
                        disconnectHeadsetProfile(connectedDevice);
                }
            } else if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = (BluetoothA2dp) proxy;

                if (a2dpProfile != null) {
                    for (BluetoothDevice connectedDevice : a2dpProfile.getConnectedDevices())
                        setLdacSettings(connectedDevice);
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                headsetProfile = null;
            } else if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = null;
            }
        }
    };

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}