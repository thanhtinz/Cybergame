package com.avatardk.game.net;

import com.google.gwt.core.client.JavaScriptObject;
import java.io.IOException;
import java.util.LinkedList;

/**
 * BẢN WEB của kết nối server: đi qua cầu WebSocket (WsBridge phía
 * server, cổng game + 2) — trình duyệt không mở được TCP thô. Byte
 * truyền qua cầu y hệt luồng TCP nên giao thức giữ nguyên: gửi gói
 * -27 dạng rõ, nhận khoá phiên, từ đó XOR lăn hai chiều. WebSocket
 * là sự kiện đơn luồng nên bộ phân tích gói chạy theo từng byte được
 * đẩy vào từ onmessage thay cho luồng đọc nền của bản desktop.
 */
public class GameConnection {
    public static final byte CMD_HANDSHAKE = -27;

    private JavaScriptObject ws = null;
    private boolean handshakeSent = false;
    private byte[] key = null;
    private int curR = 0, curW = 0;
    private boolean keyReady = false;
    private boolean closed = false;
    private String error = null;
    private final LinkedList<Msg> inbox = new LinkedList<>();

    // Trạng thái bộ phân tích khung [cmd][lenHi][lenLo][payload...]
    private int stage = 0; // 0 cmd, 1 lenHi, 2 lenLo, 3 payload
    private int curCmd = 0, curLen = 0, curPos = 0;
    private byte[] curPayload = null;

    public GameConnection() {
    }

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
        return inbox.isEmpty() ? null : inbox.removeFirst();
    }

    /** URL cầu: ?server=host[:wsport] trên địa chỉ trang thắng mọi thứ;
     *  mặc định wss/ws theo giao thức trang tới host được chọn, cổng +2. */
    private static native String wsUrl(String host, int port) /*-{
        var loc = $wnd.location;
        var m = /[?&]server=([^&]+)/.exec(loc.search || "");
        var h = host, p = port + 2;
        // Test ở máy nhà: server chọn trong game là 127.0.0.1 (máy chạy
        // server) nhưng trang lại mở trên điện thoại — khi đó dùng luôn
        // tên máy đã phục vụ trang này (cùng một máy với game server).
        if ((h === "127.0.0.1" || h === "localhost") && loc.hostname
                && loc.hostname !== "127.0.0.1" && loc.hostname !== "localhost") {
            h = loc.hostname;
        }
        if (m) {
            var v = decodeURIComponent(m[1]);
            var c = v.lastIndexOf(":");
            if (c > 0) { h = v.substring(0, c); p = parseInt(v.substring(c + 1), 10) || p; }
            else h = v;
        }
        var scheme = (loc.protocol === "https:") ? "wss://" : "ws://";
        return scheme + h + ":" + p + "/";
    }-*/;

    public void connect(String host, int port) throws IOException {
        String url = wsUrl(host, port);
        ws = wsOpen(url, this);
        if (ws == null) throw new IOException("trình duyệt không tạo được WebSocket");
    }

    /** Bản desktop gọi sau khi mở socket; bản web việc này do onopen lo. */
    public void startHandshake() throws IOException {
        sendHandshakeFrame();
    }

    private void sendHandshakeFrame() {
        if (ws == null || handshakeSent) return;
        handshakeSent = true;
        byte[] f = new byte[]{(byte) -27, 0, 0};
        wsSend(ws, f);
    }

    // ---- sự kiện từ JS (JSNI gọi ngược vào) ----

    private void onOpen() {
        sendHandshakeFrame();
    }

    private void onClosed(String why) {
        if (!closed) {
            error = why;
        }
        closed = true;
    }

    private void feedByte(int b) {
        if (closed) return;
        int v = b & 0xFF;
        if (keyReady) {
            v = (key[curR] & 0xFF) ^ v;
            curR = (curR + 1) % key.length;
        }
        switch (stage) {
            case 0:
                curCmd = (byte) v;
                stage = 1;
                break;
            case 1:
                curLen = v << 8;
                stage = 2;
                break;
            case 2:
                curLen |= v;
                curPayload = new byte[curLen];
                curPos = 0;
                stage = 3;
                if (curLen == 0) finishFrame();
                break;
            default:
                curPayload[curPos++] = (byte) v;
                if (curPos >= curLen) finishFrame();
                break;
        }
    }

    private void finishFrame() {
        stage = 0;
        if (!keyReady && curCmd == CMD_HANDSHAKE) {
            // payload: [keyLen][khoá đã mã hoá vi phân] — khôi phục như bản desktop
            int klen = curPayload[0] & 0xFF;
            key = new byte[klen];
            for (int i = 0; i < klen; i++) key[i] = curPayload[1 + i];
            for (int i = 1; i < klen; i++) key[i] = (byte) (key[i] ^ key[i - 1]);
            curR = 0;
            curW = 0;
            keyReady = true;
            return;
        }
        inbox.add(new Msg((byte) curCmd, curPayload));
    }

    public synchronized void send(Msg m) throws IOException {
        if (!keyReady) throw new IOException("chưa bắt tay xong");
        if (ws == null || closed) throw new IOException("kết nối đã đóng");
        byte[] payload = m.payload();
        byte[] f = new byte[3 + payload.length];
        f[0] = m.command;
        f[1] = (byte) (payload.length >> 8);
        f[2] = (byte) payload.length;
        System.arraycopy(payload, 0, f, 3, payload.length);
        for (int i = 0; i < f.length; i++) {
            f[i] = (byte) ((key[curW] & 0xFF) ^ (f[i] & 0xFF));
            curW = (curW + 1) % key.length;
        }
        wsSend(ws, f);
    }

    public void close() {
        closed = true;
        if (ws != null) {
            wsClose(ws);
            ws = null;
        }
    }

    // ---- cầu JSNI ----

    private static native JavaScriptObject wsOpen(String url, GameConnection self) /*-{
        try {
            var ws = new WebSocket(url);
            ws.binaryType = "arraybuffer";
            ws.onopen = function() {
                self.@com.avatardk.game.net.GameConnection::onOpen()();
            };
            ws.onmessage = function(e) {
                var u8 = new Uint8Array(e.data);
                for (var i = 0; i < u8.length; i++) {
                    self.@com.avatardk.game.net.GameConnection::feedByte(I)(u8[i]);
                }
            };
            ws.onerror = function() {
                self.@com.avatardk.game.net.GameConnection::onClosed(Ljava/lang/String;)("lỗi WebSocket");
            };
            ws.onclose = function() {
                self.@com.avatardk.game.net.GameConnection::onClosed(Ljava/lang/String;)("máy chủ đã đóng kết nối");
            };
            return ws;
        } catch (err) {
            return null;
        }
    }-*/;

    private static native void wsSend(JavaScriptObject ws, byte[] data) /*-{
        var u8 = new Uint8Array(data.length);
        for (var i = 0; i < data.length; i++) u8[i] = data[i] & 0xFF;
        ws.send(u8.buffer);
    }-*/;

    private static native void wsClose(JavaScriptObject ws) /*-{
        try { ws.close(); } catch (err) {}
    }-*/;
}
