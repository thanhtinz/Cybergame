package com.avatardk.game.music;

import java.io.File;
import java.util.List;

/**
 * BẢN WEB của dịch vụ nhạc: giao diện panel vẫn mở và thao tác được
 * (playlist lưu trong bộ nhớ), nhưng tìm kiếm/tải playlist/phát nhạc
 * YouTube không chạy trên trình duyệt (không có ffmpeg, API YouTube
 * chặn CORS) — mọi thao tác đều trả thông báo rõ ràng qua Listener.
 */
public class MusicService {

    public interface Listener {
        void onSearchDone(List<YtVideo> results);

        void onSearchError(String message);

        void onPlaylistImported(MusicLibrary.Playlist playlist);

        void onImportError(String message);

        void onTrackLoading(YtVideo track);

        void onTrackStarted(YtVideo track);

        void onTrackError(String message);

        void onQueueEnded();
    }

    public volatile List<YtVideo> searchResults = new java.util.ArrayList<>();
    public volatile boolean searching;
    public volatile boolean importing;
    public volatile List<YtVideo> queue = new java.util.ArrayList<>();
    public volatile int queueIndex = -1;
    public volatile YtVideo current;
    public volatile boolean loadingTrack;
    public volatile boolean shuffle;
    public volatile boolean repeatOne;
    public volatile int currentDurationSec;

    private final MusicPlayer player;
    private final YoutubeClient client = new YoutubeClient();
    private MusicLibrary library = new MusicLibrary();
    private Listener listener;
    private float volume = 0.7f;

    public MusicService(MusicPlayer.Sink sink) {
        this.player = new MusicPlayer(sink);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public YoutubeClient client() {
        return client;
    }

    public MusicPlayer player() {
        return player;
    }

    public void initLibrary(File file) {
        library = MusicLibrary.load(file);
    }

    public MusicLibrary library() {
        return library;
    }

    public void saveLibrary() {
    }

    public void searchAsync(String query) {
        searching = false;
        if (listener != null) {
            listener.onSearchError("Bản web chưa tìm được nhạc YouTube — nghe nhạc ở bản cài nhé.");
        }
    }

    public void importPlaylistAsync(String url) {
        importing = false;
        if (listener != null) {
            listener.onImportError("Bản web chưa thêm được playlist YouTube.");
        }
    }

    public void playQueue(List<YtVideo> tracks, int index) {
        if (tracks == null || tracks.isEmpty() || index < 0 || index >= tracks.size()) return;
        queue = new java.util.ArrayList<>(tracks);
        queueIndex = index;
        current = tracks.get(index);
        currentDurationSec = current.durationSec;
        loadingTrack = false;
        if (listener != null) {
            listener.onTrackError("Bản web chưa phát được nhạc — nghe nhạc ở bản cài nhé.");
        }
    }

    public void togglePlayPause() {
    }

    public boolean isPlaying() {
        return false;
    }

    public void next(boolean manual) {
    }

    public void prev() {
    }

    public void seek(double seconds) {
    }

    public double positionSec() {
        return 0;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        player.setVolume(volume);
    }

    public float volume() {
        return volume;
    }

    public void shutdown() {
    }
}
