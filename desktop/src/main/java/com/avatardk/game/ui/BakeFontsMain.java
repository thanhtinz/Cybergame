package com.avatardk.game.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Nướng sẵn toàn bộ font của Ui thành dạng bitmap (.fnt + .png) cho bản
 * WEB (GWT không có FreeType). Chạy trên desktop bằng chính FreeType với
 * cùng tham số như FontFactory nên hình dáng/metrics trùng bản máy cài.
 * Kết quả ghi vào assets/fonts-baked/. Chạy: gradle :desktop:bakeFonts
 */
public class BakeFontsMain {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Bake Fonts");
        cfg.setWindowedMode(320, 200);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                try {
                    bakeAll();
                    System.out.println("BAKE FONTS DONE");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("BAKE FONTS FAILED: " + e);
                }
                Gdx.app.exit();
            }
        }, cfg);
    }

    private static void bakeAll() throws Exception {
        File outDir = new File("assets/fonts-baked");
        outDir.mkdirs();
        // (đường dẫn ttf, cỡ, bộ ký tự, khoá file) — khớp Ui.initFonts
        bake("fonts/Baloo2-ExtraBold.ttf", 44, Ui.CHARS, "baloo-44", outDir);
        bake("fonts/Baloo2-ExtraBold.ttf", 84, Ui.CHARS, "baloo-84", outDir);
        bake("fonts/Baloo2-ExtraBold.ttf", 36, Ui.CHARS, "baloo-36", outDir);
        bake("fonts/Baloo2-ExtraBold.ttf", 30, Ui.CHARS, "baloo-30", outDir);
        bake("fonts/Nunito-ExtraBold.ttf", 30, Ui.CHARS, "nunito-xbold-30", outDir);
        bake("fonts/Nunito-ExtraBold.ttf", 24, Ui.CHARS, "nunito-xbold-24", outDir);
        bake("fonts/Nunito-Bold.ttf", 19, Ui.CHARS, "nunito-bold-19", outDir);
        bake("fonts/Nunito-Bold.ttf", 15, Ui.CHARS, "nunito-bold-15", outDir);
        bake("fonts/fa-solid-900.ttf", 20, Ui.FA_SOLID_CHARS, "fa-solid-20", outDir);
        bake("fonts/fa-solid-900.ttf", 26, Ui.FA_SOLID_CHARS, "fa-solid-26", outDir);
        bake("fonts/fa-solid-900.ttf", 30, Ui.FA_SOLID_CHARS, "fa-solid-30", outDir);
        bake("fonts/fa-brands-400.ttf", 26, Ui.FA_BRANDS_CHARS, "fa-brands-26", outDir);
        bake("fonts/fa-brands-400.ttf", 30, Ui.FA_BRANDS_CHARS, "fa-brands-30", outDir);
    }

    private static void bake(String ttf, int size, String chars, String key, File outDir) throws Exception {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(ttf));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = chars;
        p.color = Color.WHITE;
        FreeTypeFontGenerator.FreeTypeBitmapFontData data = gen.generateData(p);

        // ghi từng trang pixmap ra PNG
        List<String> pageFiles = new ArrayList<>();
        for (int i = 0; i < data.regions.size; i++) {
            TextureRegion region = data.regions.get(i);
            Pixmap pixmap = region.getTexture().getTextureData().consumePixmap();
            String name = key + (i == 0 ? "" : "_" + i) + ".png";
            PixmapIO.writePNG(Gdx.files.absolute(new File(outDir, name).getAbsolutePath()), pixmap);
            pageFiles.add(name);
        }

        // thu thập glyph theo id tăng dần (ép kiểu lên BitmapFontData vì
        // FreeTypeBitmapFontData che field glyphs của lớp cha)
        BitmapFont.BitmapFontData bd = data;
        List<BitmapFont.Glyph> glyphs = new ArrayList<>();
        for (BitmapFont.Glyph[] page : bd.glyphs) {
            if (page == null) continue;
            for (BitmapFont.Glyph g : page) if (g != null) glyphs.add(g);
        }
        glyphs.sort((a, b) -> Integer.compare(a.id, b.id));

        int pageW = data.regions.size > 0 ? data.regions.get(0).getTexture().getWidth() : 0;
        int pageH = data.regions.size > 0 ? data.regions.get(0).getTexture().getHeight() : 0;
        File fnt = new File(outDir, key + ".fnt");
        try (PrintWriter w = new PrintWriter(fnt, "UTF-8")) {
            w.println("info face=\"" + key + "\" size=" + size
                + " bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1"
                + " padding=0,0,0,0 spacing=0,0 outline=0");
            w.println("common lineHeight=" + Math.round(data.lineHeight)
                + " base=" + Math.round(data.capHeight)
                + " scaleW=" + pageW + " scaleH=" + pageH
                + " pages=" + pageFiles.size() + " packed=0 alphaChnl=0 redChnl=4 greenChnl=4 blueChnl=4");
            for (int i = 0; i < pageFiles.size(); i++) {
                w.println("page id=" + i + " file=\"" + pageFiles.get(i) + "\"");
            }
            w.println("chars count=" + glyphs.size());
            for (BitmapFont.Glyph g : glyphs) {
                w.println("char id=" + g.id + " x=" + g.srcX + " y=" + g.srcY
                    + " width=" + g.width + " height=" + g.height
                    + " xoffset=" + g.xoffset + " yoffset=" + g.yoffset
                    + " xadvance=" + g.xadvance + " page=" + g.page + " chnl=15");
            }
            // kerning giữa các glyph có mặt
            StringBuilder kb = new StringBuilder();
            int kCount = 0;
            for (BitmapFont.Glyph g1 : glyphs) {
                for (BitmapFont.Glyph g2 : glyphs) {
                    int amt = g1.getKerning((char) g2.id);
                    if (amt != 0) {
                        kb.append("kerning first=").append(g1.id)
                            .append(" second=").append(g2.id)
                            .append(" amount=").append(amt).append('\n');
                        kCount++;
                    }
                }
            }
            w.println("kernings count=" + kCount);
            w.print(kb);
        }
        gen.dispose();
        System.out.println("baked " + key + ": " + glyphs.size() + " glyphs, "
            + pageFiles.size() + " page(s)");
    }
}
