package com.avatardk.game;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.avatardk.game.ui.AvatarDkGame;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // APK đóng sẵn toàn bộ dữ liệu trong assets nên tắt luồng tải
        // dữ liệu lần đầu (luồng đó dành cho bản cài gọn trên PC).
        System.setProperty("avatardk.dataUrl", "off");
        AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
        cfg.useImmersiveMode = true;
        initialize(new AvatarDkGame(), cfg);
    }
}
