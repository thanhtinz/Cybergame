package com.avatardk.game.music;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Kiểm chứng bộ bóc tách innertube trên fixture bắt từ YouTube THẬT
 * (2026-11-01, xem tools/yt_ref.py): search "Sơn Tùng M-TP Nơi Này Có
 * Anh", player của chính video đó (client ANDROID_VR + visitorData) và
 * playlist uploads của kênh Sơn Tùng M-TP Official. Fixture đã tỉa bớt
 * (giữ đầu mỗi danh sách) cho nhẹ repo — nội dung kiểm chứng không đổi.
 */
public class YoutubeClientParseTest {

    private JsonValue fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/yt/" + name)) {
            assertNotNull(in, "thiếu fixture " + name);
            return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void search_ParseRealResults() throws Exception {
        List<YtVideo> vids = YoutubeClient.parseSearch(fixture("search.json"));
        assertTrue(vids.size() >= 3, "phải có ít nhất 3 kết quả, có " + vids.size());
        YtVideo first = vids.get(0);
        assertEquals("FN7ALfpGxiI", first.id);
        assertTrue(first.title.contains("NƠI NÀY CÓ ANH"), first.title);
        assertEquals("Sơn Tùng M-TP Official", first.channel);
        assertEquals(279, first.durationSec);
        assertTrue(first.thumbUrl.startsWith("https://i.ytimg.com/"), first.thumbUrl);
    }

    @Test
    void player_PicksDirectAudioStream() throws Exception {
        YtStream s = YoutubeClient.parsePlayer(fixture("player.json"), "FN7ALfpGxiI");
        assertNotNull(s.url);
        assertTrue(s.url.contains("googlevideo.com"), s.url.substring(0, 60));
        assertTrue(s.mimeType.startsWith("audio/"), s.mimeType);
        assertTrue(s.bitrate >= 100_000, "bitrate " + s.bitrate);
        assertEquals(278, s.durationSec);
        assertTrue(s.title.contains("NƠI NÀY CÓ ANH"), s.title);
        assertEquals("Sơn Tùng M-TP Official", s.author);
    }

    @Test
    void playlist_ParseLockupItems() throws Exception {
        YtPlaylistData pl = YoutubeClient.parsePlaylist(
            fixture("playlist.json"), "UUlyA28-01x4z60eWQ2kiNbA");
        assertEquals("Video tải lên từ Sơn Tùng M-TP Official", pl.name);
        assertTrue(pl.videos.size() >= 10, "có " + pl.videos.size());
        YtVideo first = pl.videos.get(0);
        assertEquals("prw-laHI2i8", first.id);
        assertEquals("Sơn Tùng M-TP Official", first.channel);
        assertEquals(27, first.durationSec);
        assertTrue(first.thumbUrl.startsWith("https://i.ytimg.com/"), first.thumbUrl);
    }

    @Test
    void extractPlaylistId_FromLinks() {
        assertEquals("PLabc123_-XYZ",
            YoutubeClient.extractPlaylistId("https://www.youtube.com/playlist?list=PLabc123_-XYZ"));
        assertEquals("PLabc123_-XYZ",
            YoutubeClient.extractPlaylistId("https://music.youtube.com/watch?v=x&list=PLabc123_-XYZ&si=q"));
        assertEquals("UUlyA28-01x4z60eWQ2kiNbA",
            YoutubeClient.extractPlaylistId("UUlyA28-01x4z60eWQ2kiNbA"));
        assertNull(YoutubeClient.extractPlaylistId("https://www.youtube.com/watch?v=abc"));
        assertNull(YoutubeClient.extractPlaylistId(""));
    }
}
