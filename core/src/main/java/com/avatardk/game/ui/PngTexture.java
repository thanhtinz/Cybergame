package com.avatardk.game.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/** Tạo Texture từ bytes PNG nhận qua mạng (ảnh mũi tên trong gói cá
 *  cắn 91). Bản desktop/LWJGL decode trực tiếp bằng Pixmap(byte[]);
 *  bản WEB dùng bản super-source riêng vì GWT không có constructor
 *  Pixmap(byte[], int, int) — xem html/.../client/super/. */
public final class PngTexture {
    private PngTexture() {
    }

    /** Texture từ bytes PNG, null nếu decode lỗi. */
    public static Texture fromPng(byte[] png, int len) {
        try {
            return new Texture(new Pixmap(png, 0, len));
        } catch (Exception e) {
            return null;
        }
    }
}
