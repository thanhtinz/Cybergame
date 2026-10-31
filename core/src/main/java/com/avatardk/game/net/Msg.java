package com.avatardk.game.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Một gói tin của giao thức gốc (docs/protocol-spec.md mục 1):
 * [command 1 byte][độ dài 2 byte big-endian][payload], payload xây bằng
 * DataOutputStream của Java (writeUTF = modified UTF-8 + tiền tố 2 byte).
 */
public class Msg {
    public final byte command;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final DataOutputStream writer = new DataOutputStream(buffer);
    private byte[] payload;

    public Msg(byte command) {
        this.command = command;
    }

    public Msg(byte command, byte[] payload) {
        this.command = command;
        this.payload = payload;
    }

    public DataOutputStream writer() {
        return writer;
    }

    public byte[] payload() {
        if (payload == null) {
            try {
                writer.flush();
            } catch (IOException ignored) {
            }
            payload = buffer.toByteArray();
        }
        return payload;
    }

    public DataInputStream reader() {
        return new DataInputStream(new ByteArrayInputStream(payload()));
    }
}
