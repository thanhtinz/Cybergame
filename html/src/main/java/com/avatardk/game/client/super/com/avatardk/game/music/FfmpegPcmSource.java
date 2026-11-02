package com.avatardk.game.music;

/**
 * BẢN WEB của nguồn PCM qua ffmpeg: trình duyệt không chạy được tiến
 * trình ngoài nên luôn báo không khả dụng — panel nhạc hiện đúng dòng
 * cảnh báo như khi máy cài thiếu ffmpeg.
 */
public class FfmpegPcmSource {
    /** Tần số mẫu PCM đầu ra (khớp bản cài — WorldScreen mở AudioDevice theo hằng này). */
    public static final int SAMPLE_RATE = 44100;

    public static boolean isAvailable() {
        return false;
    }
}
