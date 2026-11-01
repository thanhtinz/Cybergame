package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import java.util.HashMap;
import java.util.Map;

/** Hệ nhân vật PHONG CÁCH MỚI (asset user gửi 2026-10-31, docs/newstyle-spec.md):
 *  nhân vật chibi gồm 3 lớp phối được — trang phục / đầu base / tóc — cộng 2
 *  hình mẫu nguyên khối (hero). Khác hệ paper-doll gốc: asset chỉ có mặt
 *  trước, một tư thế đứng; hình phối sẵn thành Texture một lần rồi vẽ tĩnh
 *  (nhịp thở nhẹ do WorldScreen cộng khi vẽ).
 *
 *  Game chỉ nạp bản CRISP (assets/newstyle-crisp/, do tools/crisp_newstyle.py
 *  sinh từ bản full-res): posterize phẳng tông + hạ cỡ về lưới pixel của game
 *  (canvas cao 152 = 2x cỡ vẽ 76) + alpha nhị phân, để khớp ngôn ngữ hình của
 *  asset gốc. Mọi lớp crisp ĐÃ ở cỡ phối cuối nên compose chỉ BLIT 1:1, tuyệt
 *  đối không nội suy; texture khóa filter Nearest. Hiệu chuẩn trong
 *  assets/data/newstyle.json (khối maleCrisp/femaleCrisp) — sửa số ở JSON. */
public final class NewStyle {

    /** Khung hiệu chuẩn của một giới (POJO cho LibGDX Json). */
    public static class Calib {
        public int[] canvas;
        public float faceCx, bodyCx, kHair, kOutfit, hairFrac, hairDy;
    }

    /** POJO gốc của newstyle.json. */
    public static class Root {
        public Calib male, female, maleCrisp, femaleCrisp;
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

    private static Texture crispTexture(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    /** Phối một nhân vật từ các lớp CRISP (blit 1:1): outfit (đáy chạm đất,
     *  tâm thân) -> đầu base -> tóc (neo theo tâm mặt). gender: "m" | "f";
     *  hairId/outfitId: tên file không đuôi, ví dụ "hair_m_05". */
    public static Texture compose(String gender, String hairId, String outfitId) {
        String key = gender + "|" + hairId + "|" + outfitId;
        Texture t = cache.get(key);
        if (t != null) return t;
        boolean male = gender.equals("m");
        Calib c = male ? root().maleCrisp : root().femaleCrisp;
        int w = c.canvas[0], h = c.canvas[1];
        Pixmap canvas = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        canvas.setBlending(Pixmap.Blending.SourceOver);

        Pixmap outfit = loadPixmap("newstyle-crisp/outfit_" + gender + "/" + outfitId + ".png");
        canvas.drawPixmap(outfit,
            Math.round(c.bodyCx - outfit.getWidth() / 2f), h - outfit.getHeight());
        outfit.dispose();

        Pixmap base = loadPixmap("newstyle-crisp/base/base_" + gender + ".png");
        canvas.drawPixmap(base, 0, 0);
        base.dispose();

        Pixmap hair = loadPixmap("newstyle-crisp/hair_" + gender + "/" + hairId + ".png");
        canvas.drawPixmap(hair,
            Math.round(c.faceCx - c.hairFrac * hair.getWidth()), Math.round(c.hairDy));
        hair.dispose();

        t = crispTexture(canvas);
        canvas.dispose();
        cache.put(key, t);
        return t;
    }

    /** Hình mẫu nguyên khối bản CRISP (hero_m / hero_f) — không phối lớp. */
    public static Texture hero(String gender) {
        String key = "hero|" + gender;
        Texture t = cache.get(key);
        if (t == null) {
            Pixmap pm = loadPixmap("newstyle-crisp/hero/hero_" + gender + ".png");
            t = crispTexture(pm);
            pm.dispose();
            cache.put(key, t);
        }
        return t;
    }
}
