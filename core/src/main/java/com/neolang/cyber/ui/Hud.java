package com.neolang.cyber.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.neolang.cyber.Assets;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.Player;

/**
 * Shared MMORPG-style HUD pieces for Town + Adventure: the top-left character
 * cluster (round painted portrait in the painted ring frame, level badge,
 * name/class, rounded stat bars WITH numbers, survival chips, currency pills —
 * all on one compact glass panel) and the top-right minimap frame.
 *
 * Every icon here is a PAINTED asset from the HUD icon sheet (Assets.ico*) —
 * no shape-drawn glyphs. Minimap markers stay plain dots on purpose: they are
 * position markers, not icons.
 */
public final class Hud {
    private Hud() { }

    // ---- character cluster geometry (virtual 1280x720, origin bottom-left)
    public static final float CL_X = 10, CL_TOP = 708, CL_W = 372;
    private static final float PCX = CL_X + 62, PCY = CL_TOP - 62;   // portrait centre
    private static final float RX = CL_X + 140;                       // right column x
    private static final float RW = CL_W - 140 - 10;                  // right column width

    // ---- minimap geometry
    public static final float MM_X = 1078, MM_Y = 568, MM_W = 190, MM_H = 140;
    private static final float MM_GROUND = MM_Y + 34;                 // ground line y

    private static final Color GLASS = new Color(0.03f, 0.07f, 0.11f, 0.60f);
    private static final Color GLASS_EDGE = new Color(0.45f, 0.85f, 1f, 0.55f);
    private static final Color TRACK = new Color(0.04f, 0.07f, 0.11f, 0.88f);
    private static final Color HP_C = new Color(0.93f, 0.26f, 0.22f, 1);
    private static final Color NL_C = new Color(0.16f, 0.76f, 0.96f, 1);
    private static final Color XP_C = new Color(1f, 0.78f, 0.25f, 1);
    private static final Color GOLD = new Color(1f, 0.80f, 0.25f, 1);
    private static final Color BADGE_TXT = new Color(0.25f, 0.16f, 0.02f, 1);
    private static final Color CLASS_C = new Color(0.72f, 0.90f, 1f, 1);

    // ------------------------------------------------------------------ shapes
    public static void roundRect(ShapeRenderer sr, float x, float y, float w, float h, float r, Color c) {
        // One triangle fan around the centre over the rounded outline: the fan
        // tiles the shape exactly once, so translucent fills blend uniformly
        // (overlapping rects/circles double-blend into dark corner blotches).
        sr.setColor(c);
        float cx = x + w / 2, cy = y + h / 2;
        int seg = 5, n = 4 * (seg + 1);
        float[] ox = new float[n], oy = new float[n];
        float[][] corner = {{x + r, y + r}, {x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}};
        int[] start = {180, 270, 0, 90};
        int k = 0;
        for (int cn = 0; cn < 4; cn++) {
            for (int i = 0; i <= seg; i++) {
                double ang = Math.toRadians(start[cn] + 90.0 * i / seg);
                ox[k] = corner[cn][0] + (float) Math.cos(ang) * r;
                oy[k] = corner[cn][1] + (float) Math.sin(ang) * r;
                k++;
            }
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sr.triangle(cx, cy, ox[i], oy[i], ox[j], oy[j]);
        }
    }

    /** Glass panel: thin bright rim + translucent dark fill, true rounded corners. */
    public static void glass(ShapeRenderer sr, float x, float y, float w, float h, float r) {
        roundRect(sr, x, y, w, h, r, GLASS_EDGE);
        roundRect(sr, x + 1.5f, y + 1.5f, w - 3, h - 3, Math.max(2, r - 1.5f), GLASS);
    }

    private static void pillBarShape(ShapeRenderer sr, float x, float y, float w, float h,
                                       float frac, Color fill) {
        roundRect(sr, x, y, w, h, h / 2, TRACK);
        float f = Math.max(0, Math.min(1, frac));
        float fw = w * f;
        if (fw < 1) return;
        float r = h / 2;
        if (fw >= w - 1) {
            roundRect(sr, x, y, w, h, r, fill);
        } else {
            sr.setColor(fill);
            sr.circle(x + r, y + r, r);
            if (fw > r) sr.rect(x + r, y, fw - r, h);
        }
        // gloss strip on the filled part
        sr.setColor(1, 1, 1, 0.14f);
        float gw = Math.min(fw, w) - 4;
        if (gw > 6) sr.rect(x + 3, y + h * 0.58f, gw - 2, h * 0.26f);
    }

    // ------------------------------------------------------------------ batch
    /** Draw a texture centred at (cx, cy), fitted inside a size×size box. */
    public static void icon(SpriteBatch batch, Texture t, float cx, float cy, float size) {
        if (t == null) return;
        float s = size / Math.max(t.getWidth(), t.getHeight());
        float w = t.getWidth() * s, h = t.getHeight() * s;
        batch.draw(t, cx - w / 2, cy - h / 2, w, h);
    }

    private static void shadowLabel(SpriteBatch batch, BitmapFont font, String text,
                                    float x, float y, Color c, int align) {
        font.setColor(0, 0, 0, 0.55f);
        font.draw(batch, text, x + 1, y - 1, 0, align, false);
        font.setColor(c);
        font.draw(batch, text, x, y, 0, align, false);
    }

    private static String fit(BitmapFont font, String s, float maxW) {
        if (s == null) return "";
        if (Ui.textWidth(font, s) <= maxW) return s;
        while (s.length() > 3 && Ui.textWidth(font, s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    // ------------------------------------------------------- character cluster
    /**
     * Draws the whole top-left cluster and returns the panel's bottom y (the
     * cluster rect is CL_X..CL_X+CL_W × bottom..CL_TOP for tap tests).
     * adventure=true shows HP + NL bars; town shows the thin XP bar instead.
     */
    public static float drawCharCluster(NeoLangGame game, boolean adventure, float energy) {
        Player p = game.state.player;
        Assets a = game.assets;
        ShapeRenderer sr = game.shapes;
        SpriteBatch batch = game.batch;

        // rows walked down from the top
        float bar1Y = CL_TOP - 75;                       // HP (adv) / XP (town)
        float bar2Y = CL_TOP - 99;                       // NL (adventure only)
        float chipsY = adventure ? CL_TOP - 136 : CL_TOP - 110;
        float pillsY = chipsY - 34;
        float bottom = pillsY - 12;

        // ---------- shapes pass ----------
        sr.begin(ShapeType.Filled);
        glass(sr, CL_X, bottom, CL_W, CL_TOP - bottom, 14);
        // portrait glow + fallback disc under the painted frame
        sr.setColor(0.20f, 0.75f, 0.95f, 0.30f);
        sr.circle(PCX, PCY, 52);
        sr.setColor(0.10f, 0.16f, 0.22f, 0.9f);
        sr.circle(PCX, PCY, 50);
        // bars (icon sits at the head of each bar, so bars start after it)
        float bx = RX + 28, bw = RW - 28;
        if (adventure) {
            pillBarShape(sr, bx, bar1Y, bw, 17, p.hp / Math.max(1, p.maxHp()), HP_C);
            pillBarShape(sr, bx, bar2Y, bw, 17, energy / 100f, NL_C);
        } else {
            pillBarShape(sr, bx, bar1Y + 1, bw, 15, (float) ((double) p.xp / Math.max(1, (double) p.xpNext())), XP_C);
        }
        // survival chips
        float chipW = (RW - 16) / 3f;
        for (int i = 0; i < 3; i++) {
            roundRect(sr, RX + i * (chipW + 8), chipsY, chipW, 26, 13, TRACK);
        }
        // currency pills
        float pillW = (RW - 8) / 2f;
        roundRect(sr, RX, pillsY, pillW, 26, 13, TRACK);
        roundRect(sr, RX + pillW + 8, pillsY, pillW, 26, 13, TRACK);
        // level badge: gold disc on the portrait's bottom-right
        sr.setColor(0.45f, 0.30f, 0.05f, 1);
        sr.circle(PCX + 46, PCY - 46, 15);
        sr.setColor(GOLD);
        sr.circle(PCX + 46, PCY - 46, 13.5f);
        sr.end();

        // ---------- batch pass ----------
        batch.begin();
        // portrait: painted face under the painted ring frame (frame hole ≈ 66px)
        Texture face = a.heroFace[p.gender == Gender.NU ? 1 : 0];
        if (face != null) batch.draw(face, PCX - 50, PCY - 50, 100, 100);
        if (a.icoRing != null) {
            float rh = 132, rw = rh * a.icoRing.getWidth() / a.icoRing.getHeight();
            batch.draw(a.icoRing, PCX - rw / 2, PCY - rh / 2, rw, rh);
        }
        // name + class line
        shadowLabel(batch, a.font, fit(a.font, p.name, RW), RX, CL_TOP - 18, Ui.TEXT, Align.left);
        String cls = p.gameClass == GameClass.NONE ? "Tân Cư Dân" : p.gameClass.displayName;
        shadowLabel(batch, a.fontSmall, cls, RX, CL_TOP - 42, CLASS_C, Align.left);
        // bar icons + numbers
        if (adventure) {
            icon(batch, a.icoHeart, RX + 13, bar1Y + 8.5f, 24);
            icon(batch, a.icoBolt, RX + 13, bar2Y + 8.5f, 24);
            barText(batch, a, bx, bar1Y, bw, 17, (int) p.hp + "/" + p.maxHp());
            barText(batch, a, bx, bar2Y, bw, 17, (int) energy + "/100");
        } else {
            icon(batch, a.icoStar, RX + 13, bar1Y + 8.5f, 23);
            barText(batch, a, bx, bar1Y + 1, bw, 15, p.xp + "/" + p.xpNext());
        }
        // chips: painted icon + percent
        Texture[] chipIco = {a.icoFood, a.icoFlame, a.icoBattery};
        double[] chipVal = {p.satiety, p.warmth, p.battery};
        a.fontSmall.getData().setScale(0.92f);
        for (int i = 0; i < 3; i++) {
            float cxp = RX + i * (chipW + 8);
            icon(batch, chipIco[i], cxp + 15, chipsY + 13, 19);
            Ui.label(batch, a.fontSmall, (int) chipVal[i] + "%", cxp + 27, chipsY + 17.5f, Ui.TEXT, Align.left);
        }
        // currency pills: painted icon + amount
        icon(batch, a.icoCoin, RX + 15, pillsY + 13, 21);
        Ui.label(batch, a.fontSmall, String.valueOf(p.xuLa), RX + 28, pillsY + 17.5f, Ui.TEXT, Align.left);
        icon(batch, a.icoCrystal, RX + pillW + 8 + 15, pillsY + 13, 21);
        Ui.label(batch, a.fontSmall, String.valueOf(p.tinhThe), RX + pillW + 8 + 28, pillsY + 17.5f, Ui.TEXT, Align.left);
        a.fontSmall.getData().setScale(1f);
        // level number on the badge
        a.font.getData().setScale(0.72f);
        shadowLabel(batch, a.font, String.valueOf(p.level), PCX + 46, PCY - 46 + 5.5f, BADGE_TXT, Align.center);
        a.font.getData().setScale(1f);
        batch.end();
        return bottom;
    }

    private static void barText(SpriteBatch batch, Assets a, float x, float y, float w, float h, String text) {
        a.fontSmall.getData().setScale(0.85f);
        shadowLabel(batch, a.fontSmall, text, x + w / 2, y + h / 2 + 5f, Color.WHITE, Align.center);
        a.fontSmall.getData().setScale(1f);
    }

    // ---------------------------------------------------------------- minimap
    /** Frame + title strip + ground line. Markers are drawn by the caller with dot(). */
    public static void drawMinimapBase(NeoLangGame game, String title) {
        ShapeRenderer sr = game.shapes;
        sr.begin(ShapeType.Filled);
        // metal rim + dark glass
        roundRect(sr, MM_X, MM_Y, MM_W, MM_H, 12, new Color(0.42f, 0.55f, 0.68f, 0.95f));
        roundRect(sr, MM_X + 1.5f, MM_Y + 1.5f, MM_W - 3, MM_H - 3, 10.5f, new Color(0.02f, 0.06f, 0.10f, 0.78f));
        // title strip inside the top edge
        sr.setColor(0.02f, 0.05f, 0.08f, 0.65f);
        sr.rect(MM_X + 3, MM_Y + MM_H - 25, MM_W - 6, 22);
        // ground band + line
        sr.setColor(0.30f, 0.22f, 0.12f, 0.55f);
        sr.rect(MM_X + 8, MM_Y + 12, MM_W - 16, MM_GROUND - MM_Y - 12);
        sr.setColor(0.55f, 0.85f, 0.95f, 0.5f);
        sr.rect(MM_X + 8, MM_GROUND, MM_W - 16, 1.6f);
        sr.end();
        game.batch.begin();
        game.assets.fontSmall.getData().setScale(0.95f);
        shadowLabel(game.batch, game.assets.fontSmall, title, MM_X + MM_W / 2, MM_Y + MM_H - 8, Ui.CYAN, Align.center);
        game.assets.fontSmall.getData().setScale(1f);
        game.batch.end();
    }

    /** World x -> minimap x (full map width squeezed into the frame). */
    public static float mmX(float worldX, float worldW) {
        return MM_X + 10 + (worldX / worldW) * (MM_W - 20);
    }

    /** Plain position dot (markers are not icons). Call inside a Filled shapes pass. */
    public static void dot(ShapeRenderer sr, float cx, float cy, float r, Color c) {
        sr.setColor(c);
        sr.circle(cx, cy, r);
    }

    /** Player marker: cyan ring, white core, facing tick. Filled shapes pass. */
    public static void playerDot(ShapeRenderer sr, float cx, float cy, int facing) {
        sr.setColor(0.25f, 0.95f, 1f, 1);
        sr.circle(cx, cy, 5.5f);
        sr.setColor(1, 1, 1, 1);
        sr.circle(cx, cy, 3.2f);
        sr.triangle(cx + facing * 5.5f, cy - 3, cx + facing * 5.5f, cy + 3, cx + facing * 10, cy);
    }

    public static float mmGroundY() { return MM_GROUND + 5; }

    // ---------------------------------------------------------------- buttons
    /** Glass rounded-square button with a PAINTED icon inside (chat-flank buttons). */
    public static boolean glassIconBtn(NeoLangGame game, Viewport hudVp, Texture iconTex,
                                       float x, float y, float size) {
        ShapeRenderer sr = game.shapes;
        sr.begin(ShapeType.Filled);
        glass(sr, x, y, size, size, 10);
        sr.end();
        game.batch.begin();
        icon(game.batch, iconTex, x + size / 2, y + size / 2, size * 0.68f);
        game.batch.end();
        return Ui.tapped(hudVp, x, y, size, size);
    }

    /** Glass pill button (e.g. "Về Làng" under the adventure minimap). */
    public static boolean glassPillBtn(NeoLangGame game, Viewport hudVp, String label,
                                       float x, float y, float w, float h) {
        ShapeRenderer sr = game.shapes;
        sr.begin(ShapeType.Filled);
        glass(sr, x, y, w, h, h / 2);
        sr.end();
        game.batch.begin();
        shadowLabel(game.batch, game.assets.fontSmall, label, x + w / 2, y + h / 2 + 6, Ui.TEXT, Align.center);
        game.batch.end();
        return Ui.tapped(hudVp, x, y, w, h);
    }
}
