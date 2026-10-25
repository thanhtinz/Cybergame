package com.neolang.cyber;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

import java.util.HashSet;

/** Loads textures, the 1x1 white texture and the Vietnamese fonts. Missing art is tolerated (null). */
public class Assets {
    public BitmapFont font;      // 22px regular
    public BitmapFont fontBig;   // 40px bold
    public BitmapFont fontSmall; // 17px regular
    public Texture keyartWide;
    public Texture town;
    public Texture classesTex;
    public Texture adventure;
    public Texture heroMale;
    // NOTE: hero_female.jpg is the twin-tails (toc hai ben) version of the heroine.
    public Texture heroFemale;
    // Customization sheets, v2: cute chibi eyes (round, shiny, gradient iris + sparkles).
    public Texture customMale;
    public Texture customFemale;
    public Texture createMale;
    public Texture createFemale;
    public Texture white;
    public float uiScale = 1f;

    private static final String VIET =
        "ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ" +
        "ĂÂĐÊÔƠƯÁÀẢÃẠẤẦẨẪẬẮẰẲẴẶÉÈẺẼẸẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌỐỒỔỖỘỚỜỞỠỢÚÙỦŨỤỨỪỬỮỰÝỲỶỸỴ" +
        "₫·—…";

    private static String buildChars() {
        StringBuilder sb = new StringBuilder();
        HashSet<Character> seen = new HashSet<Character>();
        for (char c = 32; c < 127; c++) { if (seen.add(c)) sb.append(c); }
        for (int i = 0; i < VIET.length(); i++) {
            char c = VIET.charAt(i);
            if (seen.add(c)) sb.append(c);
        }
        return sb.toString();
    }

    private Texture tex(String path) {
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal(path);
            if (!fh.exists()) {
                System.err.println("[NeoLang/Assets] Khong tim thay art: " + path);
                return null;
            }
            Texture t = new Texture(fh);
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            System.err.println("[NeoLang/Assets] Da nap " + path + " -> " + t.getWidth() + "x" + t.getHeight());
            return t;
        } catch (Exception e) {
            System.err.println("[NeoLang/Assets] Loi nap art " + path + ": " + e);
            return null;
        }
    }

    public void load(float scale) {
        keyartWide = tex("art/keyart_wide.jpg");
        town = tex("art/town.jpg");
        classesTex = tex("art/classes.jpg");
        adventure = tex("art/adventure.jpg");
        heroMale = tex("art/hero_male.jpg");
        heroFemale = tex("art/hero_female.jpg");
        customMale = tex("art/custom_male.jpg");
        customFemale = tex("art/custom_female.jpg");
        createMale = tex("art/create_male.jpg");
        createFemale = tex("art/create_female.jpg");
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        white = new Texture(pm);
        pm.dispose();
        rebuildFonts(scale);
    }

    public void rebuildFonts(float scale) {
        uiScale = scale;
        if (font != null) font.dispose();
        if (fontBig != null) fontBig.dispose();
        if (fontSmall != null) fontSmall.dispose();
        String chars = buildChars();
        try {
            FreeTypeFontGenerator reg = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Regular.ttf"));
            FreeTypeFontGenerator bold = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Bold.ttf"));
            font = gen(reg, Math.max(12, Math.round(22 * scale)), chars);
            fontSmall = gen(reg, Math.max(10, Math.round(17 * scale)), chars);
            fontBig = gen(bold, Math.max(18, Math.round(40 * scale)), chars);
            reg.dispose();
            bold.dispose();
        } catch (Exception e) {
            // Fallback: default bitmap font (no Vietnamese glyphs, but never crash).
            if (font == null) font = new BitmapFont();
            if (fontSmall == null) fontSmall = new BitmapFont();
            if (fontBig == null) fontBig = new BitmapFont();
        }
    }

    private BitmapFont gen(FreeTypeFontGenerator g, int size, String chars) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size;
        p.characters = chars;
        p.color = Color.WHITE;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        return g.generateFont(p);
    }

    public void dispose() {
        if (font != null) font.dispose();
        if (fontBig != null) fontBig.dispose();
        if (fontSmall != null) fontSmall.dispose();
        Texture[] all = {keyartWide, town, classesTex, adventure, heroMale, heroFemale, customMale, customFemale, createMale, createFemale, white};
        for (Texture t : all) if (t != null) t.dispose();
    }
}
