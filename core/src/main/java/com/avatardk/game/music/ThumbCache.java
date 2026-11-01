package com.avatardk.game.music;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tải ảnh bìa YouTube ở thread nền rồi tạo Texture trên thread vẽ.
 *  Lấy qua {@link #get} — chưa có thì trả null (nơi vẽ dùng bìa thay thế)
 *  và tự nạp ngầm; lần vẽ sau sẽ có. */
public class ThumbCache {
    private final YoutubeClient client;
    private final Map<String, Texture> textures = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final ExecutorService exec = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "thumb-loader");
        t.setDaemon(true);
        return t;
    });

    public ThumbCache(YoutubeClient client) {
        this.client = client;
    }

    /** Texture đã sẵn sàng hoặc null. primary lỗi thì tự thử fallback. */
    public Texture get(String key, String primaryUrl, String fallbackUrl) {
        Texture t = textures.get(key);
        if (t != null) return t;
        if (primaryUrl == null || primaryUrl.isEmpty() || failed.contains(key)) return null;
        if (loading.add(key)) {
            exec.execute(() -> {
                byte[] bytes = null;
                try {
                    bytes = client.fetchBytes(primaryUrl);
                } catch (Exception e) {
                    if (fallbackUrl != null && !fallbackUrl.isEmpty() && !fallbackUrl.equals(primaryUrl)) {
                        try {
                            bytes = client.fetchBytes(fallbackUrl);
                        } catch (Exception ignored) {
                        }
                    }
                }
                loading.remove(key);
                if (bytes == null || bytes.length == 0) {
                    failed.add(key);
                    return;
                }
                final byte[] data = bytes;
                Gdx.app.postRunnable(() -> {
                    try {
                        Pixmap pm = new Pixmap(data, 0, data.length);
                        textures.put(key, new Texture(pm));
                        pm.dispose();
                    } catch (Exception ignored) {
                        failed.add(key);
                    }
                });
            });
        }
        return null;
    }

    /** Nạp sẵn một texture có sẵn (dùng cho harness chụp hình). */
    public void putLocal(String key, Texture texture) {
        textures.put(key, texture);
    }

    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        textures.clear();
        exec.shutdownNow();
    }
}
