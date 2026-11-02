package com.avatardk.game.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.avatardk.game.ui.AvatarDkGame;

/**
 * Điểm vào của bản WEB (chơi thử trên trình duyệt/iPhone): canvas phủ
 * kín cửa sổ trình duyệt. Chế độ chơi = offline qua nút "Chơi thử ngay"
 * ở màn đăng nhập (bản web chưa có cầu nối tới server game).
 */
public class HtmlLauncher extends GwtApplication {
    @Override
    public GwtApplicationConfiguration getConfig() {
        GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
        cfg.padHorizontal = 0;
        cfg.padVertical = 0;
        cfg.antialiasing = true;
        return cfg;
    }

    @Override
    public ApplicationListener createApplicationListener() {
        return new AvatarDkGame();
    }
}
