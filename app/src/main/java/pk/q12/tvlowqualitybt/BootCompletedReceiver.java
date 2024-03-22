package pk.q12.tvlowqualitybt;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public final class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            return;

        if (context.getApplicationContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return;

        final Intent bluetoothService = new Intent(context, BluetoothService.class);
        try {
            context.startService(bluetoothService);
        } catch (final IllegalStateException ignored) {
            context.startForegroundService(bluetoothService);
        }
    }
}