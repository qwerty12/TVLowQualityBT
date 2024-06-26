package pk.q12.tvlowqualitybt;

import android.annotation.SuppressLint;
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
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;

import java.util.Set;

public final class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final String ACTION_A2DP_CONNECT = "pk.q12.tvlowqualitybt.ACTION_A2DP_CONNECT";
    private static final long HEADSET_DISCONNECT_DELAY_MS = 30000;
    private static boolean isRunning;
    private BluetoothManager bluetoothManager;
    private DisplayManager displayManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothA2dp a2dpProfile;
    private BluetoothHeadset headsetProfile;
    private boolean disconectHeadsetsNow = false;
    private long lastManualConnectTime = 0;
    private String lastManualConnectMac = null;
    private final Handler xmModesetHandler = new Handler(Looper.getMainLooper());
    private final Handler headsetDisconnecthandler = new Handler(Looper.getMainLooper());
    private final Runnable headsetDisconnectRunnable = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            if (headsetProfile != null) {
                for (final BluetoothDevice connectedDevice : headsetProfile.getConnectedDevices())
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
        isRunning = true;
        super.onCreate();

        startForeground(1, new Notification.Builder(this, getString(R.string.notification_channel))
                .setSmallIcon(R.mipmap.ic_launcher)
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

        displayManager = (DisplayManager) this.getSystemService(Context.DISPLAY_SERVICE);
        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        //filter.addAction(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED);
        registerReceiver(activeDeviceReceiver, filter);
    }

    @Override
    public void onDestroy() {
        xmModesetHandler.removeCallbacksAndMessages(null);
        headsetDisconnecthandler.removeCallbacksAndMessages(null);
        unregisterReceiver(activeDeviceReceiver);
        unregisterBluetoothProfiles();
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        handleActionA2dpConnect(intent);
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void handleActionA2dpConnect(final Intent intent) {
        if (intent == null)
            return;

        final String action = intent.getAction();
        if (!ACTION_A2DP_CONNECT.equals(action))
            return;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())
            return;

        final String wantedAlias = intent.getStringExtra("alias");
        if (TextUtils.isEmpty(wantedAlias))
            return;

        final Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices == null)
            return;

        for (final BluetoothDevice bondedDevice : bondedDevices) {
            final String bondedAlias = bondedDevice.getAlias();
            if (!TextUtils.isEmpty(bondedAlias) && bondedAlias.contains(wantedAlias)) {
                if (a2dpProfile != null) {
                    final boolean isDeviceAlreadyConnected = a2dpProfile.getConnectedDevices().contains(bondedDevice);
                    if (!isDeviceAlreadyConnected) {
                        if (a2dpProfile.connect(bondedDevice)) {
                            lastManualConnectTime = SystemClock.elapsedRealtime();
                            lastManualConnectMac = bondedDevice.getAddress();
                        }
                    }

                    final int xmMode = intent.getIntExtra("xm_mode", Integer.MAX_VALUE);
                    if (xmMode >= -1 && xmMode <= XMHeadphoneSettings.MODE_NOISE_CANCELLING) {
                        final int xmVolume = intent.getIntExtra("xm_volume", 20);
                        final boolean xmVoice = intent.getBooleanExtra("xm_voice", false);
                        xmModesetHandler.removeCallbacksAndMessages(null);
                        xmModesetHandler.postDelayed(() -> {
                            final boolean enabled = xmMode != -1;
                            final byte mode = enabled ? (byte) xmMode : 0;
                            int volume = 0;
                            boolean voiceOptimized = false;

                            if (mode == XMHeadphoneSettings.MODE_AMBIENT_SOUND) {
                                volume = Math.min(Math.max(xmVolume, 0), 20);
                                voiceOptimized = xmVoice;
                            }

                            try {
                                XMHeadphoneSettings.setAmbientSound(bondedDevice, enabled, mode, volume, voiceOptimized);
                            } catch (final Throwable e) {
                                e.printStackTrace();
                            }
                        }, isDeviceAlreadyConnected ? 1000 : 2000);
                    }
                }
                return;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void setLdacSettings(final BluetoothDevice device) {
        final long CODEC_SPECIFIC_1_CONN_QUALITY = 1002; // Optimized for Connection Quality (330kbps/303kbps)
        final BluetoothCodecStatus codecStatus = a2dpProfile.getCodecStatus(device);
        final BluetoothCodecConfig currentConfig = codecStatus.getCodecConfig();
        if (currentConfig.getCodecType() != BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC)
            return;

        if (currentConfig.getCodecSpecific1() == CODEC_SPECIFIC_1_CONN_QUALITY)
            return;

        final BluetoothCodecConfig newConfig =
                new BluetoothCodecConfig(currentConfig.getCodecType(),
                        currentConfig.getCodecPriority(),
                        currentConfig.getSampleRate(),
                        currentConfig.getBitsPerSample(),
                        currentConfig.getChannelMode(),
                        CODEC_SPECIFIC_1_CONN_QUALITY,
                        currentConfig.getCodecSpecific2(),
                        currentConfig.getCodecSpecific3(),
                        currentConfig.getCodecSpecific4());

        for (int i = 0; i < 3; ++i) {
            a2dpProfile.setCodecConfigPreference(device, newConfig);
            SystemClock.sleep(100);
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
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED.equals(action)) {
            boolean skipDisplayCheck = false;
            final BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (remoteDevice == null)
                return;

            if (a2dpProfile == null)
                return;

            if (lastManualConnectTime != 0) {
                if (lastManualConnectMac != null) {
                    if (SystemClock.elapsedRealtime() - lastManualConnectTime <= 3100 && lastManualConnectMac.equals(remoteDevice.getAddress()))
                        skipDisplayCheck = true;

                    lastManualConnectMac = null;
                }

                lastManualConnectTime = 0;
            }

            if (!skipDisplayCheck) {
                for (final Display display : displayManager.getDisplays()) {
                    if (display.getState() == Display.STATE_OFF) {
                        a2dpProfile.disconnect(remoteDevice);
                        if (headsetProfile != null) {
                            headsetProfile.disconnect(remoteDevice);
                        } else {
                            if (remoteDevice.isConnected()) {
                                disconectHeadsetsNow = true;
                                bluetoothAdapter.getProfileProxy(BluetoothService.this, profileListener, BluetoothProfile.HEADSET);
                            }
                        }
                        return;
                    }
                }
            }

            setLdacSettings(remoteDevice);
        } else if (BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED.equals(action)) {
            final BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (remoteDevice == null)
                return;

            if (headsetProfile == null)
                bluetoothAdapter.getProfileProxy(BluetoothService.this, profileListener, BluetoothProfile.HEADSET);
        }
        }
    };

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onServiceConnected(final int profile, final BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                final boolean arriba = disconectHeadsetsNow;
                if (arriba)
                    disconectHeadsetsNow = false;

                headsetProfile = (BluetoothHeadset) proxy;
                if (headsetProfile == null)
                    return;

                headsetDisconnecthandler.removeCallbacksAndMessages(null);
                if (!arriba)
                    headsetDisconnecthandler.postDelayed(headsetDisconnectRunnable, HEADSET_DISCONNECT_DELAY_MS);
                else
                    headsetDisconnectRunnable.run();
            } else if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = (BluetoothA2dp) proxy;
                if (a2dpProfile == null)
                    return;

                for (final BluetoothDevice connectedDevice : a2dpProfile.getConnectedDevices())
                    setLdacSettings(connectedDevice);
            }
        }

        @Override
        public void onServiceDisconnected(final int profile) {
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
    public IBinder onBind(final Intent intent) {
        return null;
    }
}