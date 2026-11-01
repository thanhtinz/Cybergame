package com.avatardk.game.music;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Điều phối toàn bộ tính năng nhạc cho giao diện: tìm kiếm, nhập
 * playlist, hàng đợi phát (từ kết quả tìm kiếm hoặc một playlist),
 * chuyển bài/trộn/lặp, tua, âm lượng. Mọi việc nặng (mạng, resolve
 * luồng) chạy trên một executor riêng; callback có thể đến từ thread
 * nền — phía UI tự đưa về thread vẽ.
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

        /** Hết bài cuối của hàng đợi (không lặp). */
        void onQueueEnded();
    }

    private final YoutubeClient client = new YoutubeClient();
    private final MusicPlayer player;
    private final ExecutorService exec;
    private final AtomicInteger playToken = new AtomicInteger();
    private final Map<String, YtStream> streamCache = new HashMap<>();
    private final Map<String, Long> streamCacheTime = new HashMap<>();
    private static final long STREAM_TTL_MS = 5L * 3600 * 1000;
    private final Random random = new Random();

    private volatile Listener listener;
    public volatile List<YtVideo> searchResults = new ArrayList<>();
    public volatile boolean searching;
    public volatile boolean importing;
    public volatile List<YtVideo> queue = new ArrayList<>();
    public volatile int queueIndex = -1;
    public volatile YtVideo current;
    public volatile boolean loadingTrack;
    public volatile boolean shuffle;
    public volatile boolean repeatOne;
    public volatile int currentDurationSec;

    private MusicLibrary library;
    private File libraryFile;

    public MusicService(MusicPlayer.Sink sink) {
        this.player = new MusicPlayer(sink);
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "music-service");
            t.setDaemon(true);
            return t;
        });
        this.player.setListener(new MusicPlayer.Listener() {
            @Override
            public void onEnded() {
                handleEnded();
            }

            @Override
            public void onError(String message) {
                loadingTrack = false;
                Listener l = listener;
                if (l != null) l.onTrackError(message);
            }
        });
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

    // ------------------------------------------------------------ Thư viện

    public void initLibrary(File file) {
        libraryFile = file;
        library = MusicLibrary.load(file);
    }

    public MusicLibrary library() {
        return library;
    }

    public void saveLibrary() {
        if (library == null) return;
        try {
            library.save(libraryFile);
        } catch (Exception ignored) {
            // lưu nhạc lỗi không được làm phiền luồng chơi game
        }
    }

    // ------------------------------------------------------------ Tìm kiếm / nhập

    public void searchAsync(String query) {
        searching = true;
        exec.execute(() -> {
            try {
                List<YtVideo> results = client.search(query);
                searchResults = results;
                searching = false;
                Listener l = listener;
                if (l != null) l.onSearchDone(results);
            } catch (Exception e) {
                searching = false;
                Listener l = listener;
                if (l != null) l.onSearchError(friendly(e));
            }
        });
    }

    public void importPlaylistAsync(String link) {
        importing = true;
        exec.execute(() -> {
            try {
                YtPlaylistData data = client.fetchPlaylist(link);
                MusicLibrary.Playlist p = new MusicLibrary.Playlist(
                    data.name == null || data.name.isEmpty() ? "Playlist YouTube" : data.name);
                p.tracks.addAll(data.videos);
                library.playlists.add(p);
                saveLibrary();
                importing = false;
                Listener l = listener;
                if (l != null) l.onPlaylistImported(p);
            } catch (Exception e) {
                importing = false;
                Listener l = listener;
                if (l != null) l.onImportError(friendly(e));
            }
        });
    }

    // ------------------------------------------------------------ Phát

    /** Phát một danh sách (kết quả tìm kiếm hoặc playlist) từ vị trí index. */
    public void playQueue(List<YtVideo> tracks, int index) {
        if (tracks == null || tracks.isEmpty() || index < 0 || index >= tracks.size()) return;
        queue = new ArrayList<>(tracks);
        queueIndex = index;
        startCurrent(0);
    }

    public void togglePlayPause() {
        if (player.isActive()) {
            if (player.isPaused()) player.resume();
            else player.pause();
        } else if (current != null) {
            startCurrent(0);
        }
    }

    public boolean isPlaying() {
        return player.isActive() && !player.isPaused();
    }

    public void next(boolean manual) {
        if (queue.isEmpty()) return;
        int n;
        if (shuffle && queue.size() > 1) {
            do {
                n = random.nextInt(queue.size());
            } while (n == queueIndex);
        } else {
            n = queueIndex + 1;
            if (n >= queue.size()) {
                if (!manual) {
                    Listener l = listener;
                    if (l != null) l.onQueueEnded();
                    return;
                }
                n = 0;
            }
        }
        queueIndex = n;
        startCurrent(0);
    }

    public void prev() {
        if (queue.isEmpty()) return;
        // Quá 3 giây đầu thì phát lại từ đầu, chưa tới thì lùi bài.
        if (player.positionSec() > 3) {
            seek(0);
            return;
        }
        queueIndex = (queueIndex - 1 + queue.size()) % queue.size();
        startCurrent(0);
    }

    public void seek(double sec) {
        if (current == null) return;
        startCurrent(Math.max(0, sec));
    }

    public double positionSec() {
        return player.positionSec();
    }

    public void setVolume(float v) {
        player.setVolume(v);
    }

    public float volume() {
        return player.volume();
    }

    public void shutdown() {
        player.stop();
        exec.shutdownNow();
    }

    // ------------------------------------------------------------ Nội bộ

    private void handleEnded() {
        if (repeatOne && current != null) {
            startCurrent(0);
        } else {
            next(false);
        }
    }

    private void startCurrent(double startAtSec) {
        if (queueIndex < 0 || queueIndex >= queue.size()) return;
        YtVideo track = queue.get(queueIndex);
        current = track;
        currentDurationSec = track.durationSec;
        loadingTrack = true;
        int token = playToken.incrementAndGet();
        Listener l0 = listener;
        if (l0 != null) l0.onTrackLoading(track);
        exec.execute(() -> {
            try {
                YtStream stream = cachedStream(track.id);
                if (token != playToken.get()) return; // đã bị bài khác giành
                if (stream.durationSec > 0) currentDurationSec = stream.durationSec;
                player.play(new FfmpegPcmSource(stream.url, startAtSec), startAtSec);
                loadingTrack = false;
                Listener l = listener;
                if (l != null) l.onTrackStarted(track);
            } catch (Exception e) {
                if (token != playToken.get()) return;
                loadingTrack = false;
                Listener l = listener;
                if (l != null) l.onTrackError(friendly(e));
            }
        });
    }

    private YtStream cachedStream(String videoId) throws Exception {
        synchronized (streamCache) {
            YtStream s = streamCache.get(videoId);
            Long t = streamCacheTime.get(videoId);
            if (s != null && t != null && System.currentTimeMillis() - t < STREAM_TTL_MS) return s;
        }
        YtStream s = client.resolveStream(videoId);
        synchronized (streamCache) {
            streamCache.put(videoId, s);
            streamCacheTime.put(videoId, System.currentTimeMillis());
        }
        return s;
    }

    private static String friendly(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = "Đã có lỗi xảy ra.";
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException) {
            return "Không nối được tới YouTube — kiểm tra mạng rồi thử lại.";
        }
        return m;
    }
}
