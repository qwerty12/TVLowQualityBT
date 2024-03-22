package pk.q12.tvlowqualitybt;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Toast;

public final class MainActivity extends Activity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 93270);
    }

    private void createNotificationChannel() {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel notificationChannel = new NotificationChannel(getString(R.string.notification_channel), getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode != 93270)
            return;

        for (int i = 0; i < permissions.length; ++i) {
            if (permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                if (!BluetoothService.isRunning()) {
                    startService(new Intent(this, BluetoothService.class));
                    if (!((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                        Toast.makeText(this, "Turn off energy optimisation and remove permissions if app is unused for TV Low Quality BT", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }

        finish();
    }
}
