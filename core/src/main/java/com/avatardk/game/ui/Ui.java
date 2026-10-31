package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.util.HashMap;
import java.util.Map;

/** Font tiếng Việt qua FreeType (Nunito/Baloo 2 — hai font này có subset tiếng Việt đầy đủ,
 *  cùng bộ font dự án LTS dùng) + icon Font Awesome thật (font chính chủ, theo yêu cầu
 *  của chủ dự án: icon dùng glyph FA chứ không vẽ tay) + tiện ích vẽ panel bo góc,
 *  nút gradient, chấm tròn bằng Pixmap. Không dùng emoji. */
public final class Ui {
    public static final float W = 1280f, H = 720f;

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        + "àáâãèéêìíòóôõùúũýăđơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"
        + "ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚŨÝĂĐƠƯẠẢẤẦẨẪẬẮẰẲẴẶẸẺẼẾỀỂỄỆỈỊỌỎỐỒỔỖỘỚỜỞỠỢỤỦỨỪỬỮỰỲỴỶỸ"
        + " .,;:!?()[]{}+-*/=%'\"<>|_@#&•·—–…";

    // Glyph Font Awesome (solid) dùng trong game — mã private-use của FA Free 6.
    public static final String FA_BELL = "\uf0f3";
    public static final String FA_TRASH = "\uf2ed";
    public static final String FA_HEADSET = "\uf590";
    public static final String FA_SUN = "\uf185";
    public static final String FA_USER = "\uf007";
    public static final String FA_LOCK = "\uf023";
    public static final String FA_EYE = "\uf06e";
    public static final String FA_EYE_SLASH = "\uf070";
    public static final String FA_CHECK = "\uf00c";
    public static final String FA_XMARK = "\uf00d";
    private static final String FA_SOLID_CHARS = FA_BELL + FA_TRASH + FA_HEADSET + FA_SUN
        + FA_USER + FA_LOCK + FA_EYE + FA_EYE_SLASH + FA_CHECK + FA_XMARK;
    // Glyph Font Awesome (brands)
    public static final String FA_GOOGLE = "\uf1a0";
    public static final String FA_APPLE = "\uf179";
    private static final String FA_BRANDS_CHARS = FA_GOOGLE + FA_APPLE;

    public static BitmapFont fontTitle, fontBig, font, fontSmall;
    public static BitmapFont fontLogo, fontBaloo36, fontBaloo30, fontTiny;
    public static BitmapFont fa20, fa26, fa30, faBrands26, faBrands30;

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    public static void initFonts() {
        fontTitle = makeFont("fonts/Baloo2-ExtraBold.ttf", 44);
        fontBig = makeFont("fonts/Nunito-ExtraBold.ttf", 30);
        font = makeFont("fonts/Nunito-ExtraBold.ttf", 24);
        fontSmall = makeFont("fonts/Nunito-Bold.ttf", 19);
        fontLogo = makeFont("fonts/Baloo2-ExtraBold.ttf", 84);
        fontBaloo36 = makeFont("fonts/Baloo2-ExtraBold.ttf", 36);
        fontBaloo30 = makeFont("fonts/Baloo2-ExtraBold.ttf", 30);
        fontTiny = makeFont("fonts/Nunito-Bold.ttf", 15);
        fa20 = makeFaFont("fonts/fa-solid-900.ttf", 20, FA_SOLID_CHARS);
        fa26 = makeFaFont("fonts/fa-solid-900.ttf", 26, FA_SOLID_CHARS);
        fa30 = makeFaFont("fonts/fa-solid-900.ttf", 30, FA_SOLID_CHARS);
        faBrands26 = makeFaFont("fonts/fa-brands-400.ttf", 26, FA_BRANDS_CHARS);
        faBrands30 = makeFaFont("fonts/fa-brands-400.ttf", 30, FA_BRANDS_CHARS);
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

    private static BitmapFont makeFaFont(String path, int size, String glyphs) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(path));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = glyphs;
        p.color = Color.WHITE;
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        return f;
    }

    /** Vẽ chữ căn giữa theo tâm ngang cx, baseline y cho trước. */
    public static void centered(SpriteBatch batch, BitmapFont f, String text, float cx, float baselineY, Color color) {
        LAYOUT.setText(f, text);
        f.setColor(color);
        f.draw(batch, text, cx - LAYOUT.width / 2f, baselineY);
    }

    /** Vẽ 1 glyph Font Awesome căn giữa tại (cx, cy). */
    public static void faIcon(SpriteBatch batch, BitmapFont f, String glyph, float cx, float cy, Color color) {
        LAYOUT.setText(f, glyph);
        f.setColor(color);
        f.draw(batch, glyph, cx - LAYOUT.width / 2f, cy + LAYOUT.height / 2f);
    }

    public static float textWidth(BitmapFont f, String text) {
        LAYOUT.setText(f, text);
        return LAYOUT.width;
    }

    private static final Map<String, Texture> rectCache = new HashMap<>();

    public static Texture roundedRect(int w, int h, int r, Color fill, Color rim) {
        String key = w + "x" + h + "r" + r + "f" + fill.toIntBits() + "o" + (rim == null ? 0 : rim.toIntBits());
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        // Blending.None: hai lượt vẽ rim/fill phải THAY pixel, không cộng alpha —
        // SourceOver làm 4 hình tròn ở góc bị cộng sáng thành "chấm tròn" trên kính
        // (chủ dự án yêu cầu bỏ hẳn các chấm này).
        pm.setBlending(Pixmap.Blending.None);
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

    /** Nút/panel gradient dọc từ màu trên xuống màu dưới, bo góc r. */
    public static Texture gradientRect(int w, int h, int r, Color top, Color bottom) {
        String key = "g" + w + "x" + h + "r" + r + "t" + top.toIntBits() + "b" + bottom.toIntBits();
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        Color c = new Color();
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0 : (float) y / (h - 1); // Pixmap y=0 = mép TRÊN khi vẽ ra
            c.set(top).lerp(bottom, t);
            pm.setColor(c);
            int inset = cornerInset(y, h, r);
            pm.fillRectangle(inset, y, w - 2 * inset, 1);
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        rectCache.put(key, tex);
        return tex;
    }

    /** Độ thụt ngang của góc bo tại hàng y (tọa độ Pixmap, y=0 ở trên). */
    private static int cornerInset(int y, int h, int r) {
        int dy = y < r ? r - y : (y >= h - r ? y - (h - r - 1) : -1);
        if (dy < 0) return 0;
        double dx = r - Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy));
        return Math.max(0, (int) Math.ceil(dx));
    }

    /** Chỉ viền bo góc (giữa trong suốt) để đè lên nền gradient/màu khác. */
    public static Texture outlineRect(int w, int h, int r, Color rim) {
        String key = "ol" + w + "x" + h + "r" + r + "o" + rim.toIntBits();
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(rim);
        fillRounded(pm, 0, 0, w, h, r);
        pm.setColor(0, 0, 0, 0);
        fillRounded(pm, 2, 2, w - 4, h - 4, Math.max(2, r - 2));
        Texture t = new Texture(pm);
        pm.dispose();
        rectCache.put(key, t);
        return t;
    }

    /** Chấm tròn đặc (chấm trạng thái server…). */
    public static Texture dot(int size, Color fill) {
        return roundedRect(size, size, size / 2, fill, null);
    }

    /** Dải tối gradient ở mép trên màn hình (để logo/nút nổi trên tranh banner). */
    public static Texture topShade(int w, int h, float maxAlpha) {
        String key = "shade" + w + "x" + h + "a" + maxAlpha;
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0 : (float) y / (h - 1); // y=0 trên cùng = tối nhất
            pm.setColor(0.02f, 0.04f, 0.08f, maxAlpha * (1f - t));
            pm.fillRectangle(0, y, w, 1);
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        rectCache.put(key, tex);
        return tex;
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
        if (fontLogo != null) fontLogo.dispose();
        if (fontBaloo36 != null) fontBaloo36.dispose();
        if (fontBaloo30 != null) fontBaloo30.dispose();
        if (fontTiny != null) fontTiny.dispose();
        if (fa20 != null) fa20.dispose();
        if (fa26 != null) fa26.dispose();
        if (fa30 != null) fa30.dispose();
        if (faBrands26 != null) faBrands26.dispose();
        if (faBrands30 != null) faBrands30.dispose();
    }
}
