package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

/**
 * BẢN WEB của FontFactory: trình duyệt không có FreeType nên nạp font
 * bitmap đã nướng sẵn (.fnt trong assets/fonts-baked/, do BakeFontsMain
 * sinh từ đúng các .ttf + cỡ + bộ ký tự mà Ui yêu cầu).
 */
public final class FontFactory {
    private FontFactory() {
    }

    public static BitmapFont create(String path, int size, String characters) {
        String base = path.substring(path.lastIndexOf('/') + 1);
        String key;
        if (base.startsWith("Baloo2")) key = "baloo";
        else if (base.startsWith("Nunito-ExtraBold")) key = "nunito-xbold";
        else if (base.startsWith("Nunito-Bold")) key = "nunito-bold";
        else if (base.startsWith("fa-solid")) key = "fa-solid";
        else if (base.startsWith("fa-brands")) key = "fa-brands";
        else key = base.replace(".ttf", "");
        BitmapFont f = new BitmapFont(Gdx.files.internal("fonts-baked/" + key + "-" + size + ".fnt"));
        f.getData().markupEnabled = false;
        return f;
    }
}
