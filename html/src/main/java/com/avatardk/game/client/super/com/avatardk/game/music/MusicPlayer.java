package com.avatardk.game.music;

/**
 * BẢN WEB của trình phát PCM: không phát thật (bản web chưa có engine
 * giải mã nhạc) — chỉ giữ đúng bề mặt API để MusicService biên dịch.
 */
public class MusicPlayer {

    public interface Sink {
        void write(byte[] data, int offset, int length);

        void setVolume(float volume);
    }

    public interface Listener {
        void onEnded();

        void onError(String message);
    }

    private final Sink sink;
    private float volume = 1f;

    public MusicPlayer(Sink sink) {
        this.sink = sink;
    }

    public void setListener(Listener listener) {
    }

    public synchronized void play(PcmSource source, double startSec) {
    }

    public synchronized void pause() {
    }

    public synchronized void resume() {
    }

    public boolean isPaused() {
        return false;
    }

    public boolean isActive() {
        return false;
    }

    public synchronized void stop() {
    }

    public double positionSec() {
        return 0;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (sink != null) sink.setVolume(volume);
    }

    public float volume() {
        return volume;
    }
}
