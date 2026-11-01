package com.avatardk.game.music;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Giải mã audio bằng tiến trình ffmpeg ngoài (đọc được mọi định dạng
 * YouTube trả về — opus/webm, AAC/mp4 — kể cả luồng fragmented-DASH mà
 * các bộ tách MP4 thuần Java không đọc nổi). ffmpeg xuất PCM s16le
 * 44.1kHz stereo ra stdout; khi tạm dừng, máy phát ngừng đọc nên ffmpeg
 * tự nghẽn ở pipe — không tốn CPU/mạng.
 *
 * <p>Đường tìm ffmpeg: system property {@code avatardk.ffmpeg} → file
 * {@code ffmpeg(.exe)} cạnh thư mục chạy → PATH. Thiếu ffmpeg thì tính
 * năng nhạc báo rõ thay vì im lặng (xem {@link #isAvailable()}).
 */
public class FfmpegPcmSource implements PcmSource {
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 2;

    private final String input;
    private final double startSec;
    private Process process;
    private InputStream stream;
    private volatile String lastError = "";
    private static Boolean availableCache;

    /** @param input URL googlevideo hoặc đường dẫn file local. */
    public FfmpegPcmSource(String input, double startSec) {
        this.input = input;
        this.startSec = Math.max(0, startSec);
    }

    public static synchronized boolean isAvailable() {
        if (availableCache == null) {
            availableCache = probe(ffmpegPath());
        }
        return availableCache;
    }

    public static String ffmpegPath() {
        String prop = System.getProperty("avatardk.ffmpeg");
        if (prop != null && !prop.isEmpty()) return prop;
        for (String name : new String[] {"ffmpeg", "ffmpeg.exe"}) {
            File f = new File(name);
            if (f.isFile()) return f.getAbsolutePath();
            File f2 = new File("bin", name);
            if (f2.isFile()) return f2.getAbsolutePath();
        }
        return "ffmpeg";
    }

    private static boolean probe(String path) {
        try {
            Process p = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void open() throws IOException {
        if (!isAvailable()) {
            throw new IOException("Máy chưa có ffmpeg nên chưa phát được nhạc. "
                + "Cài ffmpeg (hoặc để file ffmpeg cạnh game) rồi thử lại.");
        }
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(ffmpegPath());
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        // Tuỳ chọn reconnect/user_agent CHỈ hợp lệ cho đầu vào HTTP —
        // truyền cho file local là ffmpeg chết ngay ("Option not found").
        if (input.startsWith("http://") || input.startsWith("https://")) {
            cmd.add("-reconnect");
            cmd.add("1");
            cmd.add("-reconnect_streamed");
            cmd.add("1");
            cmd.add("-reconnect_delay_max");
            cmd.add("5");
            cmd.add("-user_agent");
            cmd.add("Mozilla/5.0");
        }
        if (startSec > 0.25) {
            cmd.add("-ss");
            cmd.add(String.format(java.util.Locale.US, "%.2f", startSec));
        }
        cmd.add("-i");
        cmd.add(input);
        cmd.add("-vn");
        cmd.add("-ac");
        cmd.add(String.valueOf(CHANNELS));
        cmd.add("-ar");
        cmd.add(String.valueOf(SAMPLE_RATE));
        cmd.add("-f");
        cmd.add("s16le");
        cmd.add("pipe:1");
        process = new ProcessBuilder(cmd).start();
        stream = process.getInputStream();
        // Nuốt stderr vào bộ đệm nhỏ để báo lỗi khi ffmpeg chết sớm.
        InputStream err = process.getErrorStream();
        Thread drain = new Thread(() -> {
            try {
                byte[] b = err.readAllBytes();
                lastError = new String(b, StandardCharsets.UTF_8).trim();
            } catch (IOException ignored) {
            }
        }, "ffmpeg-stderr");
        drain.setDaemon(true);
        drain.start();
    }

    @Override
    public int sampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public int channels() {
        return CHANNELS;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (stream == null) throw new IOException("Nguồn PCM chưa mở.");
        int n = stream.read(buf, off, len);
        if (n < 0 && process != null && !process.isAlive() && process.exitValue() != 0
            && !lastError.isEmpty()) {
            throw new IOException("ffmpeg dừng giữa chừng: " + lastError);
        }
        return n;
    }

    public String lastError() {
        return lastError;
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        stream = null;
    }
}
