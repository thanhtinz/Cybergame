package com.avatardk.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.avatardk.game.GameFiles;

/**
 * Tạo font chữ cho game. Bản máy cài (desktop/Android/iOS) sinh font từ
 * file .ttf bằng FreeType như bản gốc. Bản WEB không có FreeType nên có
 * bản thay thế cùng tên (super-source trong module html) nạp font đã
 * nướng sẵn thành .fnt trong assets/fonts-baked/ — xem BakeFontsMain.
 */
public final class FontFactory {
    private FontFactory() {
    }

    public static BitmapFont create(String path, int size, String characters) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(GameFiles.data(path));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = characters;
        p.color = Color.WHITE;
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        return f;
    }
}
