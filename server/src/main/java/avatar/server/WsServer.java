package avatar.server;

import avatar.network.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Cổng WebSocket tích hợp trong tiến trình game server (cổng game + 2)
 * để bản web (trình duyệt, không mở được TCP thô) chơi online: mỗi
 * kết nối WS trở thành một Session bình thường qua hai luồng khung —
 * giao thức game (bắt tay -27, khoá XOR) không đổi một byte.
 * Triển khai công khai sau HTTPS phải đặt sau TLS (wss).
 */
public class WsServer {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static boolean started = false;

    public static synchronized void start(int wsPort) {
        if (started) return;
        started = true;
        Thread t = new Thread(() -> {
            try {
                ServerSocket ss = new ServerSocket(wsPort);
                System.out.println("WsServer: nghe ws " + wsPort);
                while (true) {
                    Socket s = ss.accept();
                    Thread h = new Thread(() -> handle(s), "ws-accept");
                    h.setDaemon(true);
                    h.start();
                }
            } catch (IOException e) {
                System.out.println("WsServer không mở được cổng " + wsPort + ": " + e.getMessage());
            }
        }, "ws-listener");
        t.setDaemon(true);
        t.start();
    }

    private static void handle(Socket client) {
        try {
            client.setTcpNoDelay(true);
            InputStream cin = client.getInputStream();
            OutputStream cout = client.getOutputStream();
            String key = readHandshake(cin);
            if (key == null) {
                client.close();
                return;
            }
            String accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1")
                    .digest((key + GUID).getBytes(StandardCharsets.ISO_8859_1)));
            String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            cout.write(resp.getBytes(StandardCharsets.ISO_8859_1));
            cout.flush();

            WsIn in = new WsIn(client, cin, cout);
            WsOut out = new WsOut(client, cout);
            String ip = client.getInetAddress() != null
                ? client.getInetAddress().getHostAddress() : "ws";
            Session cl = new Session(in, out, ++ServerManager.id, ip);
            ServerManager.clients.add(cl);
            ++ServerManager.numClients;
            System.out.println("WsServer: phiên web mới " + cl);
        } catch (Exception e) {
            try { client.close(); } catch (IOException ignored) { }
        }
    }

    /** Đọc header HTTP upgrade, trả Sec-WebSocket-Key (null nếu không hợp lệ). */
    private static String readHandshake(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int matched = 0;
        int b;
        byte[] end = {'\r', '\n', '\r', '\n'};
        while ((b = in.read()) >= 0) {
            buf.write(b);
            if (buf.size() > 16384) return null;
            if (b == end[matched]) {
                matched++;
                if (matched == 4) break;
            } else {
                matched = (b == '\r') ? 1 : 0;
            }
        }
        if (matched != 4) return null;
        String head = buf.toString(StandardCharsets.ISO_8859_1);
        for (String line : head.split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
                return line.substring(c + 1).trim();
            }
        }
        return null;
    }

    static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream(payload.length + 10);
        f.write(0x80 | opcode);
        int len = payload.length;
        if (len < 126) {
            f.write(len);
        } else if (len <= 0xFFFF) {
            f.write(126);
            f.write((len >> 8) & 0xFF);
            f.write(len & 0xFF);
        } else {
            f.write(127);
            for (int i = 7; i >= 0; i--) f.write((int) ((len >> (8 * i)) & 0xFF));
        }
        f.write(payload);
        byte[] raw = f.toByteArray();
        synchronized (out) {
            out.write(raw);
            out.flush();
        }
    }

    /** Luồng vào: gộp payload các khung binary/text thành một dòng byte. */
    private static class WsIn extends InputStream {
        private final Socket sock;
        private final InputStream in;
        private final OutputStream out;
        private byte[] cur = new byte[0];
        private int pos = 0;
        private boolean eof = false;

        WsIn(Socket sock, InputStream in, OutputStream out) {
            this.sock = sock;
            this.in = in;
            this.out = out;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            while (pos >= cur.length) {
                if (eof) return -1;
                if (!nextFrame()) {
                    eof = true;
                    return -1;
                }
            }
            int n = Math.min(len, cur.length - pos);
            System.arraycopy(cur, pos, b, off, n);
            pos += n;
            return n;
        }

        /** Đọc một khung dữ liệu; tự trả pong cho ping; false khi close/eof. */
        private boolean nextFrame() throws IOException {
            while (true) {
                int b0 = in.read();
                if (b0 < 0) return false;
                int b1 = in.read();
                if (b1 < 0) return false;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) {
                    len = ((long) rb() << 8) | rb();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | rb();
                }
                byte[] mask = new byte[4];
                if (masked) readFully(mask);
                byte[] payload = new byte[(int) len];
                readFully(payload);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                }
                switch (opcode) {
                    case 0:
                    case 1:
                    case 2:
                        if (payload.length == 0) continue;
                        cur = payload;
                        pos = 0;
                        return true;
                    case 8:
                        return false;
                    case 9:
                        writeFrame(out, 10, payload);
                        break;
                    default:
                        break;
                }
            }
        }

        private int rb() throws IOException {
            int b = in.read();
            if (b < 0) throw new IOException("eof");
            return b;
        }

        private void readFully(byte[] dst) throws IOException {
            int off = 0;
            while (off < dst.length) {
                int n = in.read(dst, off, dst.length - off);
                if (n < 0) throw new IOException("eof");
                off += n;
            }
        }

        @Override
        public void close() throws IOException {
            eof = true;
            sock.close();
        }
    }

    /** Luồng ra: gom byte tới lần flush thì đóng thành một khung binary. */
    private static class WsOut extends OutputStream {
        private final Socket sock;
        private final OutputStream out;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private boolean closed = false;

        WsOut(Socket sock, OutputStream out) {
            this.sock = sock;
            this.out = out;
        }

        @Override
        public synchronized void write(int b) {
            buf.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            buf.write(b, off, len);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (closed || buf.size() == 0) return;
            byte[] payload = buf.toByteArray();
            buf.reset();
            writeFrame(out, 2, payload);
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                flush();
                writeFrame(out, 8, new byte[0]);
            } catch (IOException ignored) {
            }
            sock.close();
        }
    }
}
