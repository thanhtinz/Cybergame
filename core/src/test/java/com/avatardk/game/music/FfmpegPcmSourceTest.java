package com.avatardk.game.music;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * Giải mã thật bằng ffmpeg trên hai mẩu audio bắt từ YouTube (mỗi mẩu
 * là 400KB đầu của luồng — đủ khoảng 20-30 giây nhạc): PCM ra phải
 * đúng 44.1kHz stereo, có tín hiệu thật (peak đủ lớn, không im lặng).
 */
public class FfmpegPcmSourceTest {

    private File fixture(String name) throws Exception {
        URL url = getClass().getResource("/yt/" + name);
        assertNotNull(url, "thiếu fixture " + name);
        return new File(url.toURI());
    }

    private double[] decodeStats(File file) throws Exception {
        long total = 0;
        int peak = 0;
        try (FfmpegPcmSource src = new FfmpegPcmSource(file.getAbsolutePath(), 0)) {
            src.open();
            assertEquals(44100, src.sampleRate());
            assertEquals(2, src.channels());
            byte[] buf = new byte[16384];
            int n;
            while ((n = src.read(buf, 0, buf.length)) >= 0) {
                total += n;
                for (int i = 0; i + 1 < n; i += 2) {
                    int s = Math.abs((short) ((buf[i] & 0xFF) | (buf[i + 1] << 8)));
                    if (s > peak) peak = s;
                }
            }
        }
        double seconds = total / (44100.0 * 2 * 2);
        return new double[] {seconds, peak};
    }

    @Test
    void decode_AacM4aPrefix() throws Exception {
        assumeTrue(FfmpegPcmSource.isAvailable(), "máy không có ffmpeg");
        double[] st = decodeStats(fixture("sample_140.m4a"));
        assertTrue(st[0] > 10 && st[0] < 90, "giây PCM = " + st[0]);
        assertTrue(st[1] > 2000, "peak = " + st[1]);
    }

    @Test
    void decode_OpusWebmPrefix() throws Exception {
        assumeTrue(FfmpegPcmSource.isAvailable(), "máy không có ffmpeg");
        double[] st = decodeStats(fixture("sample_251.webm"));
        assertTrue(st[0] > 5 && st[0] < 90, "giây PCM = " + st[0]);
        assertTrue(st[1] > 2000, "peak = " + st[1]);
    }

    @Test
    void decode_SeekOffset() throws Exception {
        assumeTrue(FfmpegPcmSource.isAvailable(), "máy không có ffmpeg");
        // tua vào giữa mẩu: vẫn phải ra PCM có tiếng (không lỗi -ss)
        long total = 0;
        try (FfmpegPcmSource src = new FfmpegPcmSource(fixture("sample_140.m4a").getAbsolutePath(), 10)) {
            src.open();
            byte[] buf = new byte[16384];
            int n;
            while ((n = src.read(buf, 0, buf.length)) >= 0) total += n;
        }
        double seconds = total / (44100.0 * 4);
        assertTrue(seconds > 3, "giây PCM sau tua = " + seconds);
    }
}
