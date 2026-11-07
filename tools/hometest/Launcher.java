package avatar.hometest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Bộ chạy test ở máy nhà (Windows): một lệnh duy nhất lo hết —
 * khởi tạo + chạy MariaDB portable, nạp dữ liệu game lần đầu,
 * chạy game server (19128 + admin 19129 + WebSocket 19130),
 * phục vụ bản web ở cổng 8080 để điện thoại cùng WiFi mở chơi.
 */
public class Launcher {
    private static final String DB_PASS = "avatardk-local";
    private static final int HTTP_PORT = 8080;
    private static Path root;
    private static Process mysqld;
    private static Process gameServer;

    public static void main(String[] args) throws Exception {
        root = Path.of("").toAbsolutePath();
        System.out.println("=== AvatarDK — chay test tai may nha ===");
        System.out.println("Thu muc: " + root);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopQuietly(gameServer);
            shutdownDb();
            stopQuietly(mysqld);
        }));

        startMariaDb();
        importDbOnce();
        startGameServer();
        startHttpServer();

        String ip = lanIp();
        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println("MO DIEN THOAI (cung WiFi voi may nay) VA VAO:");
        System.out.println("    http://" + ip + ":" + HTTP_PORT + "/");
        System.out.println("Dang nhap: tu dang ky tai khoan moi trong game.");
        System.out.println("Neu Windows hoi quyen mang cho Java/MariaDB: chon Allow.");
        System.out.println("Dong cua so nay (hoac Ctrl+C) de tat het.");
        System.out.println("--------------------------------------------------");
        Thread.currentThread().join();
    }

    // ---------- MariaDB ----------

    private static void startMariaDb() throws Exception {
        Path maria = root.resolve("mariadb");
        Path data = maria.resolve("data");
        Path mysqldExe = maria.resolve("bin/mysqld.exe");
        if (!Files.exists(mysqldExe)) {
            throw new IllegalStateException("Khong thay mariadb/bin/mysqld.exe");
        }
        if (!Files.exists(data.resolve("mysql"))) {
            System.out.println("[DB] Khoi tao du lieu MariaDB lan dau...");
            Path installDb = maria.resolve("bin/mariadb-install-db.exe");
            Process p = new ProcessBuilder(
                installDb.toString(),
                "--datadir=" + data.toAbsolutePath(),
                "--password=" + DB_PASS,
                "--port=3306")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
            pump(p, "[DB] ");
            int rc = p.waitFor();
            if (rc != 0) throw new IllegalStateException("mariadb-install-db that bai, ma " + rc);
        }
        System.out.println("[DB] Dang chay MariaDB...");
        mysqld = new ProcessBuilder(
            mysqldExe.toString(),
            "--basedir=" + maria.toAbsolutePath(),
            "--datadir=" + data.toAbsolutePath(),
            "--port=3306",
            "--bind-address=127.0.0.1",
            "--console")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(root.resolve("mariadb.log").toFile())
            .start();
        waitPort(3306, 90);
        System.out.println("[DB] MariaDB san sang (3306).");
    }

    private static void importDbOnce() throws Exception {
        Path flag = root.resolve("db-imported.flag");
        if (Files.exists(flag)) {
            System.out.println("[DB] Du lieu game da nap tu truoc, bo qua.");
            return;
        }
        Path mysql = root.resolve("mariadb/bin/mysql.exe");
        Path sql = root.resolve("server/database/avatar_2x.sql");
        System.out.println("[DB] Tao database avatar_2x...");
        runMysql(mysql, null, "CREATE DATABASE IF NOT EXISTS avatar_2x CHARACTER SET utf8mb4;");
        System.out.println("[DB] Nap du lieu game (vai chuc giay)...");
        Process p = new ProcessBuilder(
            mysql.toString(), "-u", "root", "-p" + DB_PASS, "avatar_2x")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
        try (InputStream in = new FileInputStream(sql.toFile())) {
            in.transferTo(p.getOutputStream());
        }
        p.getOutputStream().close();
        pump(p, "[DB] ");
        int rc = p.waitFor();
        if (rc != 0) throw new IllegalStateException("Nap du lieu that bai, ma " + rc);
        Files.writeString(flag, "ok");
        System.out.println("[DB] Nap du lieu xong.");
    }

    private static void runMysql(Path mysql, String db, String sql) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
            mysql.toString(), "-u", "root", "-p" + DB_PASS, "-e", sql));
        if (db != null) cmd.add(4, db);
        Process p = new ProcessBuilder(cmd).directory(root.toFile())
            .redirectErrorStream(true).start();
        pump(p, "[DB] ");
        if (p.waitFor() != 0) throw new IllegalStateException("mysql -e that bai: " + sql);
    }

    private static void shutdownDb() {
        try {
            Path admin = root.resolve("mariadb/bin/mysqladmin.exe");
            if (Files.exists(admin)) {
                new ProcessBuilder(admin.toString(), "-u", "root", "-p" + DB_PASS, "shutdown")
                    .directory(root.toFile()).start().waitFor();
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- Game server ----------

    private static void startGameServer() throws Exception {
        Path serverDir = root.resolve("server");
        String java = root.resolve("jre/bin/java.exe").toString();
        System.out.println("[Game] Dang chay game server...");
        gameServer = new ProcessBuilder(
            java, "-cp", "target/classes;lib/*", "avatar.server.Avatar")
            .directory(serverDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(root.resolve("gameserver.log").toFile())
            .start();
        waitPort(19128, 90);
        System.out.println("[Game] Game server san sang (19128, WebSocket 19130).");
    }

    // ---------- HTTP tĩnh cho bản web ----------

    private static void startHttpServer() throws IOException {
        Path web = root.resolve("web");
        ServerSocket ss = new ServerSocket(HTTP_PORT);
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Socket s = ss.accept();
                    Thread h = new Thread(() -> serve(s, web), "http-file");
                    h.setDaemon(true);
                    h.start();
                } catch (IOException e) {
                    return;
                }
            }
        }, "http-server");
        t.setDaemon(true);
        t.start();
        System.out.println("[Web] Ban web dang phuc vu o cong " + HTTP_PORT + ".");
    }

    private static void serve(Socket s, Path web) {
        try (Socket sock = s) {
            BufferedReader r = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "ISO-8859-1"));
            String line = r.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) return;
            String path = parts[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            if (path.equals("/")) path = "/index.html";
            String decoded = java.net.URLDecoder.decode(path, "UTF-8");
            Path f = web.resolve("." + decoded).normalize();
            if (!f.startsWith(web) || !Files.isRegularFile(f)) {
                writeHead(sock.getOutputStream(), 404, "text/plain", 9);
                sock.getOutputStream().write("not found".getBytes());
                return;
            }
            long len = Files.size(f);
            writeHead(sock.getOutputStream(), 200, contentType(f.getFileName().toString()), len);
            try (InputStream in = Files.newInputStream(f)) {
                in.transferTo(sock.getOutputStream());
            }
        } catch (Exception ignored) {
        }
    }

    private static void writeHead(OutputStream out, int code, String type, long len)
            throws IOException {
        String head = "HTTP/1.1 " + code + " OK\r\n"
            + "Content-Type: " + type + "\r\n"
            + "Content-Length: " + len + "\r\n"
            + "Cache-Control: no-cache\r\n"
            + "Connection: close\r\n\r\n";
        out.write(head.getBytes("ISO-8859-1"));
    }

    private static String contentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ---------- tiện ích ----------

    private static void waitPort(int port, int seconds) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Qua " + seconds + "s ma cong " + port + " chua mo");
    }

    private static void pump(Process p, String prefix) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) System.out.println(prefix + line);
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void stopQuietly(Process p) {
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) { }
        }
    }

    private static String lanIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> ads = ni.getInetAddresses();
                while (ads.hasMoreElements()) {
                    InetAddress ad = ads.nextElement();
                    if (ad instanceof Inet4Address && !ad.isLoopbackAddress()) {
                        return ad.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
