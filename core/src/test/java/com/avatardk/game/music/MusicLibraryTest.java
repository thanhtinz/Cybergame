package com.avatardk.game.music;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** Thư viện playlist: lưu JSON rồi nạp lại phải nguyên vẹn. */
public class MusicLibraryTest {

    @Test
    void saveLoad_RoundTrip() throws Exception {
        File dir = Files.createTempDirectory("musiclib").toFile();
        File file = new File(dir, "library.json");
        MusicLibrary lib = new MusicLibrary();
        MusicLibrary.Playlist p1 = new MusicLibrary.Playlist("Yêu thích");
        p1.tracks.add(new YtVideo("FN7ALfpGxiI", "Nơi Này Có Anh", "Sơn Tùng M-TP Official", 279, "https://i.ytimg.com/x.jpg"));
        MusicLibrary.Playlist p2 = new MusicLibrary.Playlist("Chill đêm khuya");
        p2.tracks.add(new YtVideo("abc123XYZ_-", "Lofi", "Chill Beats", 228, ""));
        lib.playlists.add(p1);
        lib.playlists.add(p2);
        lib.save(file);
        assertTrue(file.isFile());

        MusicLibrary back = MusicLibrary.load(file);
        assertEquals(2, back.playlists.size());
        assertEquals("Yêu thích", back.playlists.get(0).name);
        assertEquals(1, back.playlists.get(0).tracks.size());
        YtVideo v = back.playlists.get(0).tracks.get(0);
        assertEquals("FN7ALfpGxiI", v.id);
        assertEquals("Nơi Này Có Anh", v.title);
        assertEquals(279, v.durationSec);
        assertTrue(back.playlists.get(0).contains("FN7ALfpGxiI"));
        assertEquals(279, back.playlists.get(0).totalSec());
    }

    @Test
    void load_MissingFile_GivesDefaultPlaylist() throws Exception {
        File dir = Files.createTempDirectory("musiclib2").toFile();
        MusicLibrary lib = MusicLibrary.load(new File(dir, "nope.json"));
        assertEquals(1, lib.playlists.size());
        assertEquals("Yêu thích", lib.playlists.get(0).name);
        assertTrue(lib.playlists.get(0).tracks.isEmpty());
    }
}
