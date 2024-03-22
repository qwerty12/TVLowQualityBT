/*
    From https://github.com/ClusterM/sony-headphones-control which is GPL-3.0 licensed, but one of
    the core files had this in it:

    * Copyright 2013 two forty four a.m. LLC <http://www.twofortyfouram.com>
    *
    * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
    * compliance with the License.
*/

package pk.q12.tvlowqualitybt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

final class XMHeadphoneSettings {
    private static final UUID[] HEADSET_UUIDS = new UUID[]{
            UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba"),
            UUID.fromString("ba69e0f5-16e3-2db3-ad46-68503e20cc96")
    };
    private static final int RECV_TIMEOUT = 200;
    private static final byte MAGIC_PACKET_START = 0x3e;
    private static final byte MAGIC_PACKET_END = 0x3c;
    private static final byte COMMAND_SET_MODE = 0x08;
    public static final byte MODE_NOISE_CANCELLING = 2;
    public static final byte MODE_WIND_CANCELLING = 1;
    public static final byte MODE_AMBIENT_SOUND = 0;

    static void setAmbientSound(final BluetoothDevice device, final boolean enabled, final byte noiseCancelling, final int volume, final boolean voice)
            throws IOException {
        findByUUIDAndSend(device, HEADSET_UUIDS, COMMAND_SET_MODE,
                new byte[]{0x68, 0x02,
                        (byte) (enabled ? 0x10 : 0x00), 0x02, noiseCancelling, 0x01,
                        (byte) (voice ? 1 : 0), (byte) volume});
    }

    @SuppressLint("MissingPermission")
    private static void findByUUIDAndSend(final BluetoothDevice device, final UUID[] uuids, final byte command, final byte[] data)
            throws IOException {
        UUID uuid = null;

        if (device == null)
            return;

        final ParcelUuid[] DeviceUUIDs = device.getUuids();
        if (uuids == null)
            return;

        for (final ParcelUuid deviceUUID : DeviceUUIDs) {
            for (final UUID allowedUUIDs : uuids) {
                if (deviceUUID.toString().equals(allowedUUIDs.toString())) {
                    uuid = allowedUUIDs;
                    break;
                }
            }
            if (uuid != null) break;
        }

        if (uuid == null)
            return;

        try (final BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid)) {
            socket.connect();
            final OutputStream o = socket.getOutputStream();
            final InputStream i = socket.getInputStream();
            sendPacket(o, command, false, data);
            recvPacket(i, RECV_TIMEOUT); // I don't care if this succeeds or fails, but the headphones expects its buffer to be drained
        }
    }

    private static void sendPacket(final OutputStream o, final byte command, final boolean toggle, final byte[] data)
            throws IOException {
        final byte[] packet = new byte[data.length + 9];
        packet[0] = MAGIC_PACKET_START;
        packet[1] = (byte) (data.length + 4);
        packet[2] = toggle ? (byte) 1 : (byte) 0;
        packet[3] = packet[4] = packet[5] = 0;
        packet[6] = command;
        for (int j = 0; j < data.length; ++j) {
            packet[j + 7] = data[j];
        }
        byte crc = 0;
        for (int j = 1; j < packet.length - 2; ++j) {
            crc += packet[j];
        }
        packet[packet.length - 2] = crc;
        packet[packet.length - 1] = MAGIC_PACKET_END;

        o.write(packet);
    }

    private static void recvPacket(final InputStream i, final int timeout) throws IOException {
        final byte[] recvBuffer = new byte[256];
        int received = 0;
        int packetLength = -1;
        int time = 0;
        while ((time < timeout) && ((packetLength < 0) || (received < packetLength))) {
            if (i.available() > 0) {
                final int r = i.read(recvBuffer, received, recvBuffer.length - received);
                received += r;
                time = 0;
                if ((received >= 1) && (recvBuffer[0] != MAGIC_PACKET_START))
                    return;
                if (received >= 2) {
                    packetLength = recvBuffer[1] + 8;
                }
            }
            try {
                Thread.sleep(1);
            } catch (final InterruptedException e) {
                return;
            }
            time++;
        }
    }
}
