package com.avatardk.game.music;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Thư viện playlist của người chơi — lưu JSON trên máy (không qua
 *  server game), nạp lại mỗi lần mở game. */
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
            int t = 0;
            for (YtVideo v : tracks) t += v.durationSec;
            return t;
        }
    }

    public List<Playlist> playlists = new ArrayList<>();

    public static MusicLibrary load(File file) {
        if (file != null && file.isFile()) {
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                MusicLibrary lib = new Json().fromJson(MusicLibrary.class, text);
                if (lib != null && lib.playlists != null && !lib.playlists.isEmpty()) return lib;
            } catch (Exception ignored) {
                // file hỏng -> tạo mới ở dưới, không để sập game vì dữ liệu nhạc
            }
        }
        MusicLibrary lib = new MusicLibrary();
        lib.playlists.add(new Playlist("Yêu thích"));
        return lib;
    }

    public void save(File file) throws IOException {
        if (file == null) return;
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        String text = new Json(JsonWriter.OutputType.json).toJson(this);
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
