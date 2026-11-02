package com.avatardk.game.music;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * BẢN WEB của thư viện playlist: chỉ giữ trong bộ nhớ phiên chơi
 * (trình duyệt không lưu file library.json như bản cài). Mặc định có
 * sẵn playlist "Yêu thích" trống như bản cài lần đầu.
 */
public class MusicLibrary {

    public static class Playlist {
        public String name = "";
        public List<YtVideo> tracks = new ArrayList<>();

        public Playlist() {
        }

        public Playlist(String name) {
            this.name = name;
        }

        public boolean contains(String videoId) {
            for (YtVideo v : tracks) if (v.id.equals(videoId)) return true;
            return false;
        }

        public int totalSec() {
            int sum = 0;
            for (YtVideo v : tracks) sum += v.durationSec;
            return sum;
        }
    }

    public List<Playlist> playlists = new ArrayList<>();

    public MusicLibrary() {
        playlists.add(new Playlist("Yêu thích"));
    }

    public static MusicLibrary load(File file) {
        return new MusicLibrary();
    }

    public void save(File file) throws IOException {
        // bản web không lưu xuống đĩa
    }
}
