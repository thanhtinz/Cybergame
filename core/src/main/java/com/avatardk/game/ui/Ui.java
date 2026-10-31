package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.util.HashMap;
import java.util.Map;

/** Font tiếng Việt qua FreeType (Nunito/Baloo 2 — hai font này có subset tiếng Việt đầy đủ,
 *  cùng bộ font dự án LTS dùng) + tiện ích vẽ panel bo góc bằng Pixmap. Không dùng emoji. */
public final class Ui {
    public static final float W = 1280f, H = 720f;

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        + "àáâãèéêìíòóôõùúũýăđơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"
        + "ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚŨÝĂĐƠƯẠẢẤẦẨẪẬẮẰẲẴẶẸẺẼẾỀỂỄỆỈỊỌỎỐỒỔỖỘỚỜỞỠỢỤỦỨỪỬỮỰỲỴỶỸ"
        + " .,;:!?()[]{}+-*/=%'\"<>|_@#&•·—–…";

    public static BitmapFont fontTitle, fontBig, font, fontSmall;

    public static void initFonts() {
        fontTitle = makeFont("fonts/Baloo2-ExtraBold.ttf", 44);
        fontBig = makeFont("fonts/Nunito-ExtraBold.ttf", 30);
        font = makeFont("fonts/Nunito-ExtraBold.ttf", 24);
        fontSmall = makeFont("fonts/Nunito-Bold.ttf", 19);
    }

    private static BitmapFont makeFont(String path, int size) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(path));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = CHARS;
        p.color = Color.WHITE;
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        return f;
    }

    private static final Map<String, Texture> rectCache = new HashMap<>();

    public static Texture roundedRect(int w, int h, int r, Color fill, Color rim) {
        String key = w + "x" + h + "r" + r + "f" + fill.toIntBits() + "o" + (rim == null ? 0 : rim.toIntBits());
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        if (rim != null) {
            pm.setColor(rim);
            fillRounded(pm, 0, 0, w, h, r);
            pm.setColor(fill);
            fillRounded(pm, 2, 2, w - 4, h - 4, Math.max(2, r - 2));
        } else {
            pm.setColor(fill);
            fillRounded(pm, 0, 0, w, h, r);
        }
        Texture t = new Texture(pm);
        pm.dispose();
        rectCache.put(key, t);
        return t;
    }

    private static void fillRounded(Pixmap pm, int x, int y, int w, int h, int r) {
        pm.fillRectangle(x + r, y, w - 2 * r, h);
        pm.fillRectangle(x, y + r, w, h - 2 * r);
        pm.fillCircle(x + r, y + r, r);
        pm.fillCircle(x + w - r - 1, y + r, r);
        pm.fillCircle(x + r, y + h - r - 1, r);
        pm.fillCircle(x + w - r - 1, y + h - r - 1, r);
    }

    public static void dispose() {
        for (Texture t : rectCache.values()) t.dispose();
        rectCache.clear();
        if (fontTitle != null) fontTitle.dispose();
        if (fontBig != null) fontBig.dispose();
        if (font != null) font.dispose();
        if (fontSmall != null) fontSmall.dispose();
    }
}
