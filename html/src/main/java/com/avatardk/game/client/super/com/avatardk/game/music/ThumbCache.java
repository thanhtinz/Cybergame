package com.avatardk.game.music;

import com.badlogic.gdx.graphics.Texture;
import java.util.HashMap;
import java.util.Map;

/**
 * BẢN WEB của cache ảnh bìa: không tải bìa từ YouTube (CORS) — panel
 * nhạc tự rơi về bìa mẫu đóng sẵn như thiết kế. Chỉ giữ lại các bìa
 * nạp cục bộ (putLocal, dùng cho seed kiểm thử).
 */
public class ThumbCache {
    private final Map<String, Texture> local = new HashMap<>();

    public ThumbCache(YoutubeClient client) {
    }

    public Texture get(String key, String url, String fallbackKey) {
        return local.get(key);
    }

    public void putLocal(String key, Texture texture) {
        local.put(key, texture);
    }

    public void dispose() {
    }
}
