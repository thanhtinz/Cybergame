package com.avatardk.game.ui;

import com.avatardk.game.GameFiles;
import com.badlogic.gdx.graphics.Texture;

/** BẢN WEB (super-source) của PngTexture: GWT không decode được PNG
 *  từ byte[] bằng Pixmap. Ảnh mũi tên cá cắn mà server gửi chính là
 *  10 file đóng gói sẵn fish/arrow0..9.png nên so khớp từng byte rồi
 *  nạp Texture từ file; bytes lạ (server khác) thì trả null — nơi gọi
 *  tự bỏ qua ảnh bước đó. */
public final class PngTexture {
    private PngTexture() {
    }

    private static byte[][] refs;
    private static Texture[] texs;

    public static Texture fromPng(byte[] png, int len) {
        if (refs == null) {
            refs = new byte[10][];
            texs = new Texture[10];
            for (int i = 0; i < 10; i++) {
                try {
                    refs[i] = GameFiles.data("fish/arrow" + i + ".png").readBytes();
                } catch (Exception ignored) {
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            byte[] ref = refs[i];
            if (ref == null || ref.length != len) continue;
            boolean same = true;
            for (int k = 0; k < len; k++) {
                if (ref[k] != png[k]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                if (texs[i] == null) {
                    try {
                        texs[i] = new Texture(GameFiles.data("fish/arrow" + i + ".png"));
                    } catch (Exception ignored) {
                    }
                }
                return texs[i];
            }
        }
        return null;
    }
}
