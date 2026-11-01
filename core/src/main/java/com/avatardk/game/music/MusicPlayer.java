package com.avatardk.game.music;

/**
 * Máy phát nhạc: một thread bơm PCM từ {@link PcmSource} vào Sink (thiết
 * bị âm thanh). Tạm dừng = ngừng bơm (nguồn tự nghẽn), vị trí phát tính
 * theo số frame PCM đã bơm. Đổi bài/tua = thay nguồn mới (ffmpeg có -ss).
 */
public class MusicPlayer {

    /** Nơi nhận PCM đã giải mã (bản thật bọc AudioDevice của LibGDX). */
    public interface Sink {
        void write(byte[] buf, int off, int len);

        void setVolume(float volume01);
    }

    public interface Listener {
        /** Luồng PCM kết thúc tự nhiên (hết bài). */
        void onEnded();

        void onError(String message);
    }

    private final Sink sink;
    private volatile Listener listener;
    private Thread thread;
    private volatile PcmSource source;
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile long framesWritten;
    private volatile double startOffsetSec;
    private volatile int curSampleRate = FfmpegPcmSource.SAMPLE_RATE;
    private volatile float volume = 0.7f;
    /** Số thế hệ phát: mỗi lần play tăng một — thread bơm của bài cũ
     *  không được phép báo hết bài/lỗi cho bài mới. */
    private volatile int generation;

    public MusicPlayer(Sink sink) {
        this.sink = sink;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void play(PcmSource src, double startAtSec) {
        stopLocked();
        source = src;
        startOffsetSec = startAtSec;
        framesWritten = 0;
        paused = false;
        stopRequested = false;
        int gen = ++generation;
        thread = new Thread(() -> pump(gen), "music-player");
        thread.setDaemon(true);
        thread.start();
    }

    private void pump(int gen) {
        PcmSource src = source;
        boolean ended = false;
        try {
            src.open();
            curSampleRate = src.sampleRate();
            byte[] buf = new byte[16384];
            while (!stopRequested) {
                if (paused) {
                    Thread.sleep(40);
                    continue;
                }
                int n = src.read(buf, 0, buf.length);
                if (n < 0) {
                    ended = true;
                    break;
                }
                if (n == 0) {
                    Thread.sleep(5);
                    continue;
                }
                sink.write(buf, 0, n);
                framesWritten += n / 4; // s16le stereo: 4 byte / frame
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!stopRequested && gen == generation) {
                Listener l = listener;
                if (l != null) l.onError(e.getMessage() == null ? "Lỗi phát nhạc." : e.getMessage());
            }
        } finally {
            try {
                src.close();
            } catch (Exception ignored) {
            }
            if (ended && !stopRequested && gen == generation) {
                Listener l = listener;
                if (l != null) l.onEnded();
            }
        }
    }

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isActive() {
        return thread != null && thread.isAlive();
    }

    public synchronized void stop() {
        stopLocked();
    }

    private void stopLocked() {
        stopRequested = true;
        generation++;
        paused = false;
        if (source != null) {
            try {
                source.close();
            } catch (Exception ignored) {
            }
            source = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /** Vị trí phát hiện tại (giây), đã gồm điểm bắt đầu khi tua. */
    public double positionSec() {
        return startOffsetSec + framesWritten / (double) curSampleRate;
    }

    public void setVolume(float volume01) {
        volume = Math.max(0f, Math.min(1f, volume01));
        sink.setVolume(volume);
    }

    public float volume() {
        return volume;
    }
}
