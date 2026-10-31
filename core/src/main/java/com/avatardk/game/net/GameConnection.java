package com.avatardk.game.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kết nối tới server game theo đúng giao thức gốc (docs/protocol-spec.md):
 * <ol>
 *   <li>Kết nối TCP, gửi ngay gói rỗng -27 dạng rõ (plaintext).</li>
 *   <li>Server trả gói -27 chứa khoá phiên mã hoá vi phân (delta-XOR);
 *       khôi phục khoá bằng XOR luỹ kế.</li>
 *   <li>Từ đó mọi byte (command + độ dài + payload) đều XOR với khoá lăn —
 *       hai con trỏ độc lập cho chiều đọc/ghi, chạy liên tục không reset.</li>
 * </ol>
 * Luồng đọc chạy nền; màn hình game lấy gói qua {@link #poll()}.
 */
public class GameConnection {
    public static final byte CMD_HANDSHAKE = -27;

    private Socket socket;
    private InputStream rawIn;
    private OutputStream rawOut;
    private byte[] key;
    private int curR = 0, curW = 0;
    private volatile boolean keyReady = false;
    private volatile boolean closed = false;
    private volatile String error = null;
    private final ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<>();

    public boolean isKeyReady() {
        return keyReady;
    }

    public boolean isClosed() {
        return closed;
    }

    public String error() {
        return error;
    }

    public Msg poll() {
        return inbox.poll();
    }

    /** Mở kết nối và bắt đầu bắt tay. Chạy trên luồng gọi (nên gọi từ luồng nền). */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        rawIn = socket.getInputStream();
        rawOut = socket.getOutputStream();
        // gói -27 rỗng, dạng rõ
        writeRawFrame((byte) -27, new byte[0]);
        Thread t = new Thread(this::readLoop, "game-net-reader");
        t.setDaemon(true);
        t.start();
    }

    private void writeRawFrame(byte cmd, byte[] payload) throws IOException {
        rawOut.write(cmd);
        rawOut.write((payload.length >> 8) & 0xFF);
        rawOut.write(payload.length & 0xFF);
        rawOut.write(payload);
        rawOut.flush();
    }

    private int readRawByte() throws IOException {
        int b = rawIn.read();
        if (b < 0) throw new EOFException("server đóng kết nối");
        return b;
    }

    private byte readKeyByte() throws IOException {
        int b = readRawByte();
        int out = (key[curR] & 0xFF) ^ b;
        curR = (curR + 1) % key.length;
        return (byte) out;
    }

    private void readLoop() {
        try {
            while (!closed) {
                byte cmd;
                int size;
                byte[] payload;
                if (!keyReady) {
                    cmd = (byte) readRawByte();
                    size = (readRawByte() << 8) | readRawByte();
                    payload = new byte[size];
                    for (int i = 0; i < size; i++) payload[i] = (byte) readRawByte();
                    if (cmd == CMD_HANDSHAKE) {
                        // payload: [keyLen][key0][key_i XOR key_{i-1}...] — giải vi phân
                        int klen = payload[0] & 0xFF;
                        key = new byte[klen];
                        for (int i = 0; i < klen; i++) key[i] = payload[1 + i];
                        for (int i = 1; i < klen; i++) key[i] = (byte) (key[i] ^ key[i - 1]);
                        keyReady = true;
                        continue;
                    }
                } else {
                    cmd = readKeyByte();
                    size = ((readKeyByte() & 0xFF) << 8) | (readKeyByte() & 0xFF);
                    payload = new byte[size];
                    for (int i = 0; i < size; i++) payload[i] = readKeyByte();
                }
                inbox.add(new Msg(cmd, payload));
            }
        } catch (Exception e) {
            if (!closed) error = e.getMessage();
            closed = true;
        }
    }

    /** Gửi một gói (chỉ gọi sau khi bắt tay xong). */
    public synchronized void send(Msg m) throws IOException {
        if (!keyReady) throw new IOException("chưa bắt tay xong");
        byte[] payload = m.payload();
        writeKeyByte(m.command);
        writeKeyByte((byte) (payload.length >> 8));
        writeKeyByte((byte) payload.length);
        for (byte b : payload) writeKeyByte(b);
        rawOut.flush();
    }

    private void writeKeyByte(byte b) throws IOException {
        int out = (key[curW] & 0xFF) ^ (b & 0xFF);
        curW = (curW + 1) % key.length;
        rawOut.write(out);
    }

    public void close() {
        closed = true;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}
