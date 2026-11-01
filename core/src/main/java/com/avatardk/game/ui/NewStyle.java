package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import java.util.HashMap;
import java.util.Map;

/** Hệ nhân vật PHONG CÁCH MỚI (asset user gửi 2026-10-31, docs/newstyle-spec.md):
 *  nhân vật chibi vẽ tay gồm 3 lớp phối được — trang phục / đầu base / tóc —
 *  cộng 2 hình mẫu nguyên khối (hero). Khác hệ paper-doll gốc: asset chỉ có
 *  mặt trước, một tư thế đứng; hình phối sẵn thành Texture một lần rồi vẽ
 *  tĩnh (nhịp thở nhẹ do WorldScreen cộng khi vẽ).
 *
 *  Hiệu chuẩn phối (k/neo/dy) đo từ cặp hero trong tools/composite_test.py
 *  và chốt vào assets/data/newstyle.json — sửa số ở JSON, không sửa code. */
public final class NewStyle {

    /** Khung hiệu chuẩn của một giới (POJO cho LibGDX Json). */
    public static class Calib {
        public int[] canvas;
        public float faceCx, bodyCx, kHair, kOutfit, hairFrac, hairDy;
    }

    /** POJO gốc của newstyle.json (chỉ cần 2 khối hiệu chuẩn). */
    public static class Root {
        public Calib male, female;
    }

    private static Root root;
    private static final Map<String, Texture> cache = new HashMap<>();

    private NewStyle() {}

    private static Root root() {
        if (root == null) {
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            root = json.fromJson(Root.class,
                Gdx.files.internal("data/newstyle.json").readString("UTF-8"));
        }
        return root;
    }

    private static Pixmap loadPixmap(String path) {
        return new Pixmap(Gdx.files.internal(path));
    }

    private static void drawScaled(Pixmap dst, Pixmap src, int dx, int dy) {
        dst.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(),
            dx, dy, src.getWidth(), src.getHeight());
    }

    private static void drawScaledTo(Pixmap dst, Pixmap src, int dx, int dy, int dw, int dh) {
        dst.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), dx, dy, dw, dh);
    }

    /** Phối một nhân vật: outfit (đáy chạm đất, tâm thân) -> đầu base ->
     *  tóc (neo theo tâm mặt). gender: "m" | "f"; hairId/outfitId: tên file
     *  không đuôi, ví dụ "hair_m_05", "outfit_f_10". */
    public static Texture compose(String gender, String hairId, String outfitId) {
        String key = gender + "|" + hairId + "|" + outfitId;
        Texture t = cache.get(key);
        if (t != null) return t;
        boolean male = gender.equals("m");
        Calib c = male ? root().male : root().female;
        int w = c.canvas[0], h = c.canvas[1];
        Pixmap canvas = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        canvas.setBlending(Pixmap.Blending.SourceOver);

        Pixmap outfit = loadPixmap("newstyle/outfit_" + gender + "/" + outfitId + ".png");
        int ow = Math.round(outfit.getWidth() * c.kOutfit);
        int oh = Math.round(outfit.getHeight() * c.kOutfit);
        drawScaledTo(canvas, outfit, Math.round(c.bodyCx - ow / 2f), h - oh, ow, oh);
        outfit.dispose();

        Pixmap base = loadPixmap("newstyle/base/base_" + gender + ".png");
        drawScaled(canvas, base, 0, 0);
        base.dispose();

        Pixmap hair = loadPixmap("newstyle/hair_" + gender + "/" + hairId + ".png");
        int hw = Math.round(hair.getWidth() * c.kHair);
        int hh = Math.round(hair.getHeight() * c.kHair);
        drawScaledTo(canvas, hair,
            Math.round(c.faceCx - c.hairFrac * hw), Math.round(c.hairDy), hw, hh);
        hair.dispose();

        t = new Texture(canvas);
        canvas.dispose();
        cache.put(key, t);
        return t;
    }

    /** Hình mẫu nguyên khối (hero_m / hero_f) — không phối lớp. */
    public static Texture hero(String gender) {
        String key = "hero|" + gender;
        Texture t = cache.get(key);
        if (t == null) {
            t = new Texture(Gdx.files.internal("newstyle/hero/hero_" + gender + ".png"));
            cache.put(key, t);
        }
        return t;
    }
}
