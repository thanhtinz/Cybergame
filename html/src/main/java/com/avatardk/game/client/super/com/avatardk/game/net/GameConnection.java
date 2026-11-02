package com.avatardk.game.net;

import java.io.IOException;

/**
 * BẢN WEB của kết nối server: trình duyệt không mở được TCP thô tới
 * server game (cổng 19128) nên mọi lần kết nối đều thất bại êm với
 * thông báo rõ ràng — bản web chỉ chơi offline. Muốn online trên web
 * phải thêm cầu WebSocket phía server (đợt riêng).
 */
public class GameConnection {
    public static final byte CMD_HANDSHAKE = -27;

    public GameConnection() {
    }

    public void connect(String host, int port) throws IOException {
        throw new IOException("Bản web chưa hỗ trợ chơi online.");
    }

    public void startHandshake() throws IOException {
        throw new IOException("Bản web chưa hỗ trợ chơi online.");
    }

    public boolean isKeyReady() {
        return false;
    }

    public boolean isClosed() {
        return true;
    }

    public String error() {
        return "Bản web chưa hỗ trợ chơi online.";
    }

    public Msg poll() {
        return null;
    }

    public synchronized void send(Msg m) throws IOException {
        // bản web không có kết nối — bỏ qua êm như chưa từng gửi
    }

    public void close() {
    }
}
