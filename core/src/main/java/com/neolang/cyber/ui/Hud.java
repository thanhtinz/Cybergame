package com.neolang.cyber.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.neolang.cyber.Assets;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.Player;

/**
 * Shared MMORPG-style HUD pieces for Town + Adventure: the top-left character
 * card (round painted portrait in the painted wooden leaf frame, level badge,
 * name/class and the main stat bars WITH numbers — kept slim like a mobile
 * MMO card), a loose row of survival chips hanging under the card, the
 * top-right minimap frame with the currency pills under it, and the painted
 * joystick.
 *
 * Every icon here is a PAINTED asset (Assets.ico*, framePortrait, joyBase,
 * joyKnob) — no shape-drawn glyphs. Minimap markers stay plain dots on
 * purpose: they are position markers, not icons.
 */
public final class Hud {
    private Hud() { }

    // ---- character cluster geometry (virtual 1280x720, origin bottom-left)
    // Compact card: 276 wide total, portrait frame 76 across — the user asked
    // for a slimmer card twice, so every row is packed tight (12-14px bars).
    public static final float CL_X = 10, CL_TOP = 708, CL_W = 276;
    private static final float PCX = CL_X + 44, PCY = CL_TOP - 46;   // portrait centre
    private static final float RX = CL_X + 92;                        // right column x
    private static final float RW = CL_W - 92 - 8;                    // right column width
    // COZY wooden leaf-carved frame's inner hole measures 140/256 of the
    // sprite height (prep_sprites.py --cozy); the face disc is drawn ~6%
    // wider than the hole to tuck under the rim bevel.
    private static final float FRAME_H = 76;
    private static final float FACE_D = FRAME_H * (140f / 256f) * 1.06f;

    // ---- minimap geometry: painted COZY frame asset (wood rim + parchment
    // pane, sprite 327x256) drawn at its native aspect 169x132, right edge
    // flush where the old frame's right edge was, top edge flush with the
    // character card's top. The parchment rect inside the sprite was measured
    // by prep_sprites.py --cozy (fractions of the sprite: left .2202,
    // right .8104, bottom .2305, top .7461); markers map into it exactly.
    public static final float MM_X = 1101, MM_Y = 576, MM_W = 169, MM_H = 132;
    private static final float MM_GX = MM_X + MM_W * 0.2202f;         // parchment left
    private static final float MM_GW = MM_W * (0.8104f - 0.2202f);    // parchment width
    private static final float MM_GY = MM_Y + MM_H * 0.2305f;         // parchment bottom
    private static final float MM_GH = MM_H * (0.7461f - 0.2305f);    // parchment height
    private static final float MM_GROUND = MM_GY + 13;                // ground line y

    private static final Color GLASS = new Color(0.98f, 0.93f, 0.82f, 0.88f);
    private static final Color GLASS_EDGE = new Color(0.55f, 0.38f, 0.22f, 0.75f);
    private static final Color TRACK = new Color(0.85f, 0.76f, 0.62f, 0.9f);
    private static final Color HP_C = new Color(0.93f, 0.26f, 0.22f, 1);
    private static final Color NL_C = new Color(0.30f, 0.62f, 0.80f, 1);
    private static final Color XP_C = new Color(1f, 0.78f, 0.25f, 1);
    private static final Color GOLD = new Color(1f, 0.80f, 0.25f, 1);
    private static final Color BADGE_TXT = new Color(0.25f, 0.16f, 0.02f, 1);
    private static final Color CLASS_C = new Color(0.45f, 0.32f, 0.18f, 1);

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
     * Draws the slim top-left character card (portrait + level badge, name,
     * class line, main bars only) plus the loose survival-chip row hanging
     * under it, and returns the CARD's bottom y (the card rect is
     * CL_X..CL_X+CL_W × bottom..CL_TOP for tap tests; the chip row below the
     * card is display-only). Currency lives under the minimap instead
     * (drawCurrency). adventure=true shows HP + NL bars; town shows the thin
     * XP bar instead.
     */
    public static float drawCharCluster(NeoLangGame game, boolean adventure, float energy) {
        Player p = game.state.player;
        Assets a = game.assets;
        ShapeRenderer sr = game.shapes;
        SpriteBatch batch = game.batch;

        // rows walked down from the top (compact: 13px bars, tight padding)
        float bar1Y = CL_TOP - 64;                       // HP (adv) / XP (town)
        float bar2Y = CL_TOP - 83;                       // NL (adventure only)
        float bottom = PCY - FRAME_H / 2 - 6;            // card ends under the frame
        float chipsY = bottom - 30;                      // loose chip row under the card
        float chipW = (CL_W - 16) / 3f;

        // ---------- shapes pass ----------
        sr.begin(ShapeType.Filled);
        glass(sr, CL_X, bottom, CL_W, CL_TOP - bottom, 12);
        // portrait glow + fallback disc under the painted frame
        sr.setColor(1f, 0.75f, 0.35f, 0.35f);
        sr.circle(PCX, PCY, 30);
        sr.setColor(0.96f, 0.90f, 0.78f, 0.95f);
        sr.circle(PCX, PCY, 28);
        // bars (icon sits at the head of each bar, so bars start after it)
        float bx = RX + 24, bw = RW - 24;
        if (adventure) {
            pillBarShape(sr, bx, bar1Y, bw, 13, p.hp / Math.max(1, p.maxHp()), HP_C);
            pillBarShape(sr, bx, bar2Y, bw, 13, energy / 100f, NL_C);
        } else {
            pillBarShape(sr, bx, bar1Y, bw, 13, (float) ((double) p.xp / Math.max(1, (double) p.xpNext())), XP_C);
        }
        // survival chips: separate small glass pills, NOT part of the card
        for (int i = 0; i < 3; i++) {
            glass(sr, CL_X + i * (chipW + 8), chipsY, chipW, 22, 11);
        }
        sr.end();

        // ---------- batch pass ----------
        batch.begin();
        // portrait: painted face disc sized to the wooden frame's hole (see
        // FACE_D) under the painted frame itself
        Texture face = a.heroFace[p.gender == Gender.NU ? 1 : 0];
        if (face != null) batch.draw(face, PCX - FACE_D / 2, PCY - FACE_D / 2, FACE_D, FACE_D);
        if (a.framePortrait != null) {
            float fw = FRAME_H * a.framePortrait.getWidth() / a.framePortrait.getHeight();
            batch.draw(a.framePortrait, PCX - fw / 2, PCY - FRAME_H / 2, fw, FRAME_H);
        }
        // name + class line
        shadowLabel(batch, a.font, fit(a.font, p.name, RW), RX, CL_TOP - 18, Ui.TEXT, Align.left);
        String cls = p.gameClass == GameClass.NONE ? "Tân Cư Dân" : p.gameClass.displayName;
        shadowLabel(batch, a.fontSmall, cls, RX, CL_TOP - 40, CLASS_C, Align.left);
        // bar icons + numbers
        if (adventure) {
            icon(batch, a.icoHeart, RX + 10, bar1Y + 6.5f, 20);
            icon(batch, a.icoBolt, RX + 10, bar2Y + 6.5f, 20);
            barText(batch, a, bx, bar1Y, bw, 13, (int) p.hp + "/" + p.maxHp());
            barText(batch, a, bx, bar2Y, bw, 13, (int) energy + "/100");
        } else {
            icon(batch, a.icoStar, RX + 10, bar1Y + 6.5f, 20);
            barText(batch, a, bx, bar1Y, bw, 13, p.xp + "/" + p.xpNext());
        }
        // chips under the card: painted icon + percent
        Texture[] chipIco = {a.icoFood, a.icoFlame, a.icoBattery};
        double[] chipVal = {p.satiety, p.warmth, p.battery};
        a.fontSmall.getData().setScale(0.85f);
        for (int i = 0; i < 3; i++) {
            float cxp = CL_X + i * (chipW + 8);
            icon(batch, chipIco[i], cxp + 13, chipsY + 11, 16);
            Ui.label(batch, a.fontSmall, (int) chipVal[i] + "%", cxp + 23, chipsY + 15, Ui.TEXT, Align.left);
        }
        a.fontSmall.getData().setScale(1f);
        batch.end();

        // level badge OVER the frame: gold disc on the portrait's bottom-right.
        // Its centre sits on the rim, so if it were drawn in the shapes pass
        // before the frame, the frame would clip half the disc and the number.
        // Bottom-centre of the frame carries a small orange gem; the badge at
        // the bottom-right stays clear of it.
        sr.begin(ShapeType.Filled);
        sr.setColor(0.45f, 0.30f, 0.05f, 1);
        sr.circle(PCX + 27, PCY - 27, 11.5f);
        sr.setColor(GOLD);
        sr.circle(PCX + 27, PCY - 27, 10.2f);
        sr.end();
        batch.begin();
        a.font.getData().setScale(0.6f);
        shadowLabel(batch, a.font, String.valueOf(p.level), PCX + 27, PCY - 27 + 4.5f, BADGE_TXT, Align.center);
        a.font.getData().setScale(1f);
        batch.end();
        return bottom;
    }

    /**
     * Currency row (Xu Lá / Tinh Thể, painted icons) as its own little cluster
     * under the minimap: two glass pills side by side, top edge at topY.
     * Adventure stacks it under the "Về Làng" pill; town directly under the map.
     */
    public static void drawCurrency(NeoLangGame game, float topY) {
        Player p = game.state.player;
        Assets a = game.assets;
        ShapeRenderer sr = game.shapes;
        float y = topY - 26, pillW = (MM_W - 8) / 2f;
        sr.begin(ShapeType.Filled);
        glass(sr, MM_X, y, pillW, 26, 13);
        glass(sr, MM_X + pillW + 8, y, pillW, 26, 13);
        sr.end();
        game.batch.begin();
        a.fontSmall.getData().setScale(0.92f);
        icon(game.batch, a.icoCoin, MM_X + 15, y + 13, 20);
        Ui.label(game.batch, a.fontSmall, String.valueOf(p.xuLa), MM_X + 27, y + 17.5f, Ui.TEXT, Align.left);
        icon(game.batch, a.icoCrystal, MM_X + pillW + 8 + 15, y + 13, 20);
        Ui.label(game.batch, a.fontSmall, String.valueOf(p.tinhThe), MM_X + pillW + 8 + 27, y + 17.5f, Ui.TEXT, Align.left);
        a.fontSmall.getData().setScale(1f);
        game.batch.end();
    }

    // ------------------------------------------------------------- joystick
    /**
     * Painted joystick (shared Town + Adventure visuals): wooden disc base
     * asset at (jx, jy), knob asset following the drag offset. Mode 1 = fixed
     * stick, mode 2 = floating stick at base. ASSET ONLY — no ShapeRenderer
     * anywhere in this draw (an earlier version painted a flat glow disc
     * behind the knob, which read as a leftover shape-drawn stick under the
     * asset and was removed at the user's direction). While running, the
     * knob asset itself warms up (tint) and swells slightly, and the "Chạy"
     * label shows under the fixed stick. Movement logic and the 0.22 dead
     * zone / 0.85 run threshold live in the screens, unchanged.
     */
    public static void drawJoystick(NeoLangGame game, float jx, float jy, int mode,
                                    Vector2 base, float vec, boolean running) {
        Assets a = game.assets;
        float knobX = mode == 1 ? jx + vec * 38 : jx;
        SpriteBatch batch = game.batch;
        batch.begin();
        batch.setColor(1, 1, 1, 0.95f);
        icon(batch, a.joyBase, jx, jy, 116);
        if (running) batch.setColor(1f, 0.80f, 0.52f, 1f);
        else batch.setColor(1, 1, 1, 1);
        icon(batch, a.joyKnob, knobX, jy, running ? 58 : 54);
        batch.setColor(1, 1, 1, 1);
        if (mode == 2) {
            batch.setColor(1, 1, 1, 0.95f);
            icon(batch, a.joyBase, base.x, base.y, 108);
            batch.setColor(1, 1, 1, 1);
            icon(batch, a.joyKnob, base.x + vec * 38, base.y, 50);
        }
        if (running) Ui.label(batch, a.fontSmall, "Chạy", jx, jy - 64, Ui.GOLD, Align.center);
        batch.end();
        batch.setColor(1, 1, 1, 1);
    }

    private static void barText(SpriteBatch batch, Assets a, float x, float y, float w, float h, String text) {
        // Dark-brown numbers with a light halo: readable both on the coloured
        // fill and on the pale tan track of the COZY bars.
        a.fontSmall.getData().setScale(0.85f);
        a.fontSmall.setColor(1f, 0.99f, 0.95f, 0.85f);
        a.fontSmall.draw(batch, text, x + w / 2 + 1, y + h / 2 + 4f, 0, Align.center, false);
        a.fontSmall.setColor(0.30f, 0.20f, 0.12f, 1f);
        a.fontSmall.draw(batch, text, x + w / 2, y + h / 2 + 5f, 0, Align.center, false);
        a.fontSmall.setColor(1, 1, 1, 1);
        a.fontSmall.getData().setScale(1f);
    }

    // ---------------------------------------------------------------- minimap
    /**
     * Painted COZY frame asset (wood rim + parchment pane) + title strip + ground
     * line, all INSIDE the measured glass rect (MM_GX/MM_GY/MM_GW/MM_GH).
     * Markers are drawn by the caller with dot(). If the frame asset is
     * missing, a shape-drawn frame in the same rect is the fallback.
     */
    public static void drawMinimapBase(NeoLangGame game, String title) {
        ShapeRenderer sr = game.shapes;
        float glassTop = MM_GY + MM_GH;
        if (game.assets.minimapFrame != null) {
            game.batch.begin();
            game.batch.draw(game.assets.minimapFrame, MM_X, MM_Y, MM_W, MM_H);
            game.batch.end();
        }
        sr.begin(ShapeType.Filled);
        if (game.assets.minimapFrame == null) {
            roundRect(sr, MM_X, MM_Y, MM_W, MM_H, 12, new Color(0.62f, 0.45f, 0.28f, 0.95f));
            roundRect(sr, MM_GX, MM_GY, MM_GW, MM_GH, 6, new Color(0.96f, 0.90f, 0.76f, 0.95f));
        }
        // title strip inside the top edge of the parchment
        sr.setColor(0.35f, 0.24f, 0.14f, 0.45f);
        sr.rect(MM_GX + 2, glassTop - 19, MM_GW - 4, 18);
        // ground band + line, clipped to the parchment
        sr.setColor(0.55f, 0.42f, 0.25f, 0.50f);
        sr.rect(MM_GX + 3, MM_GY + 2, MM_GW - 6, MM_GROUND - MM_GY - 2);
        sr.setColor(0.45f, 0.32f, 0.18f, 0.6f);
        sr.rect(MM_GX + 3, MM_GROUND, MM_GW - 6, 1.6f);
        sr.end();
        game.batch.begin();
        game.assets.fontSmall.getData().setScale(0.95f);
        shadowLabel(game.batch, game.assets.fontSmall, title, MM_X + MM_W / 2, glassTop - 5,
            new Color(1f, 0.96f, 0.88f, 1), Align.center);
        game.assets.fontSmall.getData().setScale(1f);
        game.batch.end();
    }

    /** World x -> minimap x (full map width squeezed into the glass pane). */
    public static float mmX(float worldX, float worldW) {
        return MM_GX + 6 + (worldX / worldW) * (MM_GW - 12);
    }

    /** Plain position dot (markers are not icons). Call inside a Filled shapes pass. */
    public static void dot(ShapeRenderer sr, float cx, float cy, float r, Color c) {
        sr.setColor(c);
        sr.circle(cx, cy, r);
    }

    /** Player marker: dark-amber ring (reads on the cream parchment), white core,
     *  facing tick. Filled shapes pass. */
    public static void playerDot(ShapeRenderer sr, float cx, float cy, int facing) {
        sr.setColor(0.85f, 0.45f, 0.12f, 1);
        sr.circle(cx, cy, 5.5f);
        sr.setColor(1, 1, 1, 1);
        sr.circle(cx, cy, 3.2f);
        sr.triangle(cx + facing * 5.5f, cy - 3, cx + facing * 5.5f, cy + 3, cx + facing * 10, cy);
    }

    public static float mmGroundY() { return MM_GROUND + 4; }

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

    /**
     * Round PAINTED action button (town context interact): the painted disc
     * asset with the context label under it in small shadowed text, like the
     * labels under the adventure skill buttons. Tap area = the disc's
     * bounding square. Falls back to a plain glass disc if the asset is gone.
     */
    public static boolean roundActionBtn(NeoLangGame game, Viewport hudVp, Texture tex,
                                         float cx, float cy, float d, String label) {
        SpriteBatch batch = game.batch;
        if (tex == null) {
            ShapeRenderer sr = game.shapes;
            sr.begin(ShapeType.Filled);
            sr.setColor(Ui.BTN);
            sr.circle(cx, cy, d / 2);
            sr.end();
        }
        batch.begin();
        if (tex != null) icon(batch, tex, cx, cy, d);
        if (label != null) shadowLabel(batch, game.assets.fontSmall, label, cx, cy - d / 2 - 7, Ui.TEXT, Align.center);
        batch.end();
        return Ui.tapped(hudVp, cx - d / 2, cy - d / 2, d, d);
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
