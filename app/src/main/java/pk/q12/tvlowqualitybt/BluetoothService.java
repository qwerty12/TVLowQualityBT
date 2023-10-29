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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static boolean isRunning;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothA2dp a2dpProfile;
    private BluetoothHeadset headsetProfile;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable headsetRunnable = new Runnable() {
        @Override
        public void run() {
            if (headsetProfile != null) {
                for (BluetoothDevice connectedDevice : headsetProfile.getConnectedDevices())
                    headsetProfile.disconnect(connectedDevice);
                if (bluetoothAdapter != null) {
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile);
                    headsetProfile = null;
                }
            }
        }
    };

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

        bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        if ((bluetoothManager == null) || (bluetoothAdapter = bluetoothManager.getAdapter()) == null) {
            stopSelf();
            return;
        }

        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        //filter.addAction(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED);
        registerReceiver(activeDeviceReceiver, filter);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(headsetRunnable);
        unregisterReceiver(activeDeviceReceiver);
        unregisterBluetoothProfiles();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void setLdacSettings(BluetoothDevice device) {
        if (a2dpProfile == null)
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
                final BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (remoteDevice == null)
                    return;

                setLdacSettings(remoteDevice);
                if (headsetProfile == null)
                    bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET);
            }
        }
    };

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        private Context context;

        public BluetoothProfile.ServiceListener init(Context context) {
            this.context = context;
            return this;
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                headsetProfile = (BluetoothHeadset) proxy;

                if (headsetProfile != null) {
                    if (headsetProfile.getActiveDevice() == null) {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile);
                        headsetProfile = null;
                        return;
                    }
                    handler.removeCallbacksAndMessages(null);
                    handler.postDelayed(headsetRunnable, 10000);
                }
            } else if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = (BluetoothA2dp) proxy;

                if (a2dpProfile != null) {
                    for (BluetoothDevice connectedDevice : a2dpProfile.getConnectedDevices())
                        setLdacSettings(connectedDevice);
                }

                if (headsetProfile == null)
                    bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET);
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
    }.init(this);

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}