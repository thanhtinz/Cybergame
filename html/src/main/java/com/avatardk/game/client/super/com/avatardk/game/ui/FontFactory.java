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
        // QUAN TRỌNG: định dạng .fnt không lưu ascent/descent, loader để
        // ascent=0 trong khi bản FreeType có ascent riêng từng font —
        // thiếu nó, draw() đặt dòng chữ đầu tiên thấp hơn đúng bằng
        // ascent (Baloo36: 16px, Nunito19: 6px...) khiến MỌI chữ trên
        // web lệch xuống so với bản cài. Bảng dưới là số đo thật từ
        // FreeTypeBitmapFontData (DumpFontMain) cho từng font/cỡ.
        float[] ad = ascentDescent(key + "-" + size);
        if (ad != null) {
            f.getData().ascent = ad[0];
            f.getData().descent = ad[1];
        }
        return f;
    }

    private static float[] ascentDescent(String key) {
        switch (key) {
            case "baloo-44": return new float[] {20f, -24f};
            case "baloo-84": return new float[] {39f, -45f};
            case "baloo-36": return new float[] {16f, -19f};
            case "baloo-30": return new float[] {15f, -16f};
            case "nunito-xbold-30": return new float[] {10f, -11f};
            case "nunito-xbold-24": return new float[] {8f, -9f};
            case "nunito-bold-19": return new float[] {6f, -7f};
            case "nunito-bold-15": return new float[] {5f, -6f};
            case "fa-solid-20": return new float[] {0f, -3f};
            case "fa-solid-26": return new float[] {1f, -4f};
            case "fa-solid-30": return new float[] {-1f, -5f};
            case "fa-brands-26": return new float[] {1f, -4f};
            case "fa-brands-30": return new float[] {-1f, -5f};
            default: return null;
        }
    }
}
