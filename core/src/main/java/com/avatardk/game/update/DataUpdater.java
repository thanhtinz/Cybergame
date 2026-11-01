package com.avatardk.game.update;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tải dữ liệu game về máy ở lần vào đầu tiên (và cập nhật khi có phiên
 * bản dữ liệu mới): máy chủ dữ liệu chỉ gồm một {@code manifest.json} +
 * vài gói {@code .zip} (mỗi gói kèm sha256). Tải xong kiểm tra hash mới
 * giải nén vào thư mục gốc dữ liệu; xong hết mới ghi mốc phiên bản —
 * tải dở dang thì lần mở sau tải lại, không bao giờ dùng dữ liệu nửa vời.
 *
 * <p>Bản cài phát hành chỉ đóng gói fonts (để hiện được màn hình tải);
 * mọi asset khác đi qua đường này. Không có mạng + không có dữ liệu sẵn
 * thì màn hình tải báo lỗi và cho thử lại.
 */
public class DataUpdater {

    /** Nơi phát hành dữ liệu game (GitHub Releases của repo dự án). */
    public static final String DEFAULT_BASE_URL =
        "https://github.com/thanhtinz/Cybergame/releases/download/gamedata-v1/";

    public interface Progress {
        void onProgress(long doneBytes, long totalBytes, String status);
    }

    public static class Bundle {
        public String file = "";
        public long size = 0;
        public String sha256 = "";
    }

    public static class Manifest {
        public int version = 0;
        public long totalSize = 0;
        public List<Bundle> bundles = new ArrayList<>();
    }

    private final File rootDir;
    private final String baseUrl;

    public DataUpdater(File rootDir, String baseUrl) {
        this.rootDir = rootDir;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /** URL gốc dữ liệu: system property avatardk.dataUrl ghi đè (test/dev). */
    public static String baseUrl() {
        return System.getProperty("avatardk.dataUrl", DEFAULT_BASE_URL);
    }

    private File markerFile() {
        return new File(rootDir, "version.json");
    }

    private File manifestCacheFile() {
        return new File(rootDir, "manifest-cache.json");
    }

    /** Phiên bản dữ liệu đã cài xong trên máy; -1 = chưa có. */
    public int installedVersion() {
        try {
            if (!markerFile().isFile()) return -1;
            String text = Files.readString(markerFile().toPath(), StandardCharsets.UTF_8);
            JsonValue v = new JsonReader().parse(text).get("version");
            return v != null && v.isNumber() ? v.asInt() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Manifest đã tải lần trước và lưu lại (để vào game nhanh không cần
     *  hỏi mạng khi dữ liệu đã khớp phiên bản). */
    public Manifest cachedManifest() {
        try {
            if (!manifestCacheFile().isFile()) return null;
            return parseManifest(Files.readString(manifestCacheFile().toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** Tải manifest mới nhất từ máy chủ dữ liệu (và lưu bản sao). */
    public Manifest fetchManifest() throws IOException {
        byte[] bytes = get(baseUrl + "manifest.json");
        String text = new String(bytes, StandardCharsets.UTF_8);
        Manifest m = parseManifest(text);
        rootDir.mkdirs();
        Files.writeString(manifestCacheFile().toPath(), text, StandardCharsets.UTF_8);
        return m;
    }

    /** Tải + kiểm tra + giải nén toàn bộ gói còn thiếu, rồi ghi mốc. */
    public void downloadAll(Manifest manifest, Progress progress) throws IOException {
        rootDir.mkdirs();
        File tmpDir = new File(rootDir, "tmp");
        tmpDir.mkdirs();
        long total = Math.max(1, manifest.totalSize);
        long doneBefore = 0;
        for (Bundle b : manifest.bundles) {
            File part = new File(tmpDir, b.file + ".part");
            final long base = doneBefore;
            download(baseUrl + b.file, part, (done, len) -> {
                if (progress != null) {
                    progress.onProgress(base + done, total, "Đang tải " + b.file + "…");
                }
            });
            if (b.size > 0 && part.length() != b.size) {
                part.delete();
                throw new IOException("Gói " + b.file + " tải về thiếu dung lượng — thử lại.");
            }
            if (progress != null) progress.onProgress(base, total, "Đang kiểm tra " + b.file + "…");
            String hash = sha256Hex(part);
            if (b.sha256 != null && !b.sha256.isEmpty() && !hash.equalsIgnoreCase(b.sha256)) {
                part.delete();
                throw new IOException("Gói " + b.file + " không khớp mã kiểm tra (mạng chập chờn?) — thử lại.");
            }
            if (progress != null) progress.onProgress(base, total, "Đang giải nén " + b.file + "…");
            unzip(part, rootDir);
            part.delete();
            doneBefore += b.size;
            if (progress != null) progress.onProgress(doneBefore, total, "Đã xong " + b.file);
        }
        Files.writeString(markerFile().toPath(),
            "{\"version\":" + manifest.version + "}", StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ Tĩnh (có test)

    public static Manifest parseManifest(String jsonText) {
        JsonValue root = new JsonReader().parse(jsonText);
        Manifest m = new Manifest();
        JsonValue v = root.get("version");
        m.version = v != null && v.isNumber() ? v.asInt() : 0;
        JsonValue total = root.get("totalSize");
        m.totalSize = total != null && total.isNumber() ? (long) total.asDouble() : 0;
        JsonValue arr = root.get("bundles");
        if (arr != null) {
            for (JsonValue jb : arr) {
                Bundle b = new Bundle();
                b.file = jb.getString("file", "");
                JsonValue size = jb.get("size");
                b.size = size != null && size.isNumber() ? (long) size.asDouble() : 0;
                b.sha256 = jb.getString("sha256", "");
                m.bundles.add(b);
            }
        }
        if (m.totalSize <= 0) {
            long sum = 0;
            for (Bundle b : m.bundles) sum += b.size;
            m.totalSize = sum;
        }
        return m;
    }

    public static String sha256Hex(File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("Thiếu SHA-256", e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Giải nén zip vào destDir; chặn zip-slip (mục chứa .. hoặc tuyệt đối). */
    public static void unzip(File zipFile, File destDir) throws IOException {
        String destRoot = destDir.getCanonicalPath();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[65536];
            while ((entry = zin.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                String outPath = out.getCanonicalPath();
                if (!outPath.equals(destRoot) && !outPath.startsWith(destRoot + File.separator)) {
                    throw new IOException("Gói dữ liệu chứa mục lạ: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zin.read(buf)) >= 0) os.write(buf, 0, n);
                    }
                }
                zin.closeEntry();
            }
        }
    }

    // ------------------------------------------------------------ Mạng

    private interface ByteProgress {
        void onBytes(long done, long total);
    }

    private byte[] get(String url) throws IOException {
        File tmp = File.createTempFile("manifest", ".bin");
        try {
            download(url, tmp, null);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
        }
    }

    private void download(String url, File dest, ByteProgress progress) throws IOException {
        URLConnection conn = open(new URL(url));
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (conn instanceof HttpURLConnection) {
            ((HttpURLConnection) conn).setInstanceFollowRedirects(true);
        }
        long total = conn.getContentLengthLong();
        if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            long done = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) progress.onBytes(done, total);
            }
        }
    }

    private URLConnection open(URL url) throws IOException {
        Proxy proxy = proxyFromEnv();
        return proxy == null ? url.openConnection() : url.openConnection(proxy);
    }

    private static Proxy proxyFromEnv() {
        String p = System.getenv("https_proxy");
        if (p == null || p.isEmpty()) p = System.getenv("HTTPS_PROXY");
        if (p == null || p.isEmpty()) p = System.getenv("http_proxy");
        if (p == null || p.isEmpty()) return null;
        try {
            URL u = new URL(p);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(u.getHost(), u.getPort()));
        } catch (Exception e) {
            return null;
        }
    }
}
