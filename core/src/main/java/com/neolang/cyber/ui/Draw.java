package com.neolang.cyber.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Align;
import com.neolang.cyber.Assets;
import com.neolang.cyber.model.BuildModule;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.MonsterDef;

/** All world/character drawing with shapes. Pure Cyber Trunk style: techwear citizens, neon
 *  circuits on a giant mother-tree trunk, glass pods. No emoji, no traditional ninja/farmer motifs. */
public class Draw {
    /** Suit/hair palettes mirror the model constants (Player.SUIT_COLORS / HAIR_COLORS). */
    public static final Color[] SUIT = buildColors(com.neolang.cyber.model.Player.SUIT_COLORS);
    public static final Color[] HAIR = buildColors(com.neolang.cyber.model.Player.HAIR_COLORS);
    /** Eye colors: 0 chestnut brown, 1 circuit cyan, 2 neon violet (model constants). */
    public static final int[] EYE_COLORS = com.neolang.cyber.model.Player.EYE_COLORS;

    private static Color[] buildColors(int[] hexes) {
        Color[] out = new Color[hexes.length];
        for (int i = 0; i < hexes.length; i++) out[i] = rgb(hexes[i]);
        return out;
    }
    public static final String[] EYE_NAMES = {"Nâu Hạt Dẻ", "Cyan Mạch Điện", "Tím Neon"};
    public static final String[] OUTFIT_IDS = {"bo_tan_cu_dan", "bo_street_neon", "bo_tham_hiem"};
    public static final String[] OUTFIT_NAMES = {"Tân Cư Dân", "Street Neon", "Thám Hiểm"};

    private static final Color SKIN = new Color(1f, 0.85f, 0.70f, 1);
    private static final Color CYAN = new Color(0.25f, 0.95f, 1f, 1);
    private static final Color PINK = new Color(1f, 0.35f, 0.75f, 1);
    private static final Color ORANGE = new Color(1f, 0.55f, 0.15f, 1);

    public static Color rgb(int hex) {
        return new Color(((hex >> 16) & 255) / 255f, ((hex >> 8) & 255) / 255f, (hex & 255) / 255f, 1f);
    }

    public static Color rgb(int hex, float a) {
        Color c = rgb(hex);
        c.a = a;
        return c;
    }

    private static Color lerp(Color a, Color b, float t) {
        return new Color(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, 1f);
    }

    // ------------------------------------------------------------------ sky / world
    /** Gradient sky in world space from x0 over width w, full 720 height. dayness 1=day, 0=night. */
    public static void drawSky(ShapeRenderer sr, float x0, float w, float dayness, boolean storm, float time) {
        Color dayTop = new Color(0.35f, 0.75f, 0.95f, 1), dayBot = new Color(0.75f, 0.95f, 0.90f, 1);
        Color nightTop = new Color(0.02f, 0.04f, 0.12f, 1), nightBot = new Color(0.10f, 0.16f, 0.30f, 1);
        if (storm) {
            dayTop.lerp(new Color(0.30f, 0.15f, 0.45f, 1), 0.6f);
            dayBot.lerp(new Color(0.35f, 0.20f, 0.50f, 1), 0.6f);
            nightTop.lerp(new Color(0.20f, 0.05f, 0.35f, 1), 0.6f);
            nightBot.lerp(new Color(0.30f, 0.10f, 0.45f, 1), 0.6f);
        }
        int bands = 24;
        sr.begin(ShapeType.Filled);
        for (int i = 0; i < bands; i++) {
            float t = i / (float) bands;
            Color top = lerp(nightTop, dayTop, dayness);
            Color bot = lerp(nightBot, dayBot, dayness);
            sr.setColor(lerp(bot, top, t));
            sr.rect(x0, 720 * t, w, 720f / bands + 1);
        }
        // stars / drifting spores at night
        if (dayness < 0.7f) {
            float a = (0.7f - dayness) / 0.7f;
            sr.setColor(1, 1, 1, a * 0.8f);
            for (int i = 0; i < 60; i++) {
                float sx = x0 + ((i * 733) % 1400);
                float sy = 380 + ((i * 389) % 320);
                float tw = 0.6f + 0.4f * (float) Math.sin(time * 2 + i);
                sr.circle(sx, sy, 1.6f * tw);
            }
        }
        sr.end();
    }

    /** Giant mother-tree trunk backdrop + flat ground strip in the lower half. variant 1 = darker forest edge. */
    public static void drawTrunkWorld(ShapeRenderer sr, float camX, float worldW, float groundY, int variant, float time) {
        float dark = variant == 1 ? 0.55f : 1f;
        sr.begin(ShapeType.Filled);
        // repeating trunk segments
        int start = (int) Math.floor(camX / 560) - 1;
        int end = start + 6;
        for (int s = start; s <= end; s++) {
            float tx = s * 560f;
            if (tx > worldW) continue;
            Color bark = (s % 2 == 0)
                ? new Color(0.30f * dark, 0.20f * dark, 0.14f * dark, 1)
                : new Color(0.26f * dark, 0.17f * dark, 0.12f * dark, 1);
            sr.setColor(bark);
            sr.rect(tx, groundY, 300, 720 - groundY);
            sr.setColor(0, 0, 0, 0.18f);
            sr.rect(tx + 40, groundY, 14, 720 - groundY);
            sr.rect(tx + 200, groundY, 10, 720 - groundY);
            // glowing circuit veins
            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 2 + s * 1.7f);
            sr.setColor(0.25f, 0.95f, 1f, 0.5f * pulse * dark);
            sr.rect(tx + 120, groundY + 30, 5, 260);
            sr.rect(tx + 120, groundY + 150, 90, 5);
            sr.setColor(1f, 0.35f, 0.75f, 0.4f * pulse * dark);
            sr.rect(tx + 230, groundY + 60, 4, 180);
            sr.rect(tx + 160, groundY + 235, 74, 4);
            // hanging glow leaves / vines
            sr.setColor(0.20f * dark, 0.70f * dark, 0.45f * dark, 1);
            sr.ellipse(tx + 60, 640, 60, 22);
            sr.ellipse(tx + 190, 660, 80, 24);
        }
        // ground strip
        sr.setColor(new Color(0.23f * dark, 0.16f * dark, 0.11f * dark, 1));
        sr.rect(camX - 60, 0, 1420, groundY);
        sr.setColor(new Color(0.32f * dark, 0.24f * dark, 0.16f * dark, 1));
        sr.rect(camX - 60, groundY - 26, 1420, 26);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3);
        sr.setColor(0.25f, 0.95f, 1f, 0.35f + 0.3f * pulse);
        sr.rect(camX - 60, groundY - 3, 1420, 3);
        sr.end();
    }

    /** A metal-glass shop pod with a glowing sign. highlight = player nearby (pulsing arrow + border). */
    public static void drawPod(ShapeRenderer sr, SpriteBatch batch, BitmapFont font,
                               float x, float groundY, float w, float h, Color signColor,
                               String label, boolean highlight, float time) {
        sr.begin(ShapeType.Filled);
        sr.setColor(0.13f, 0.18f, 0.24f, 1);
        sr.rect(x, groundY, w, h);
        sr.setColor(0.45f, 0.60f, 0.70f, 1);
        sr.rect(x, groundY + h - 8, w, 8);
        sr.rect(x, groundY, 6, h);
        sr.rect(x + w - 6, groundY, 6, h);
        // glass front
        sr.setColor(0.35f, 0.75f, 0.85f, 0.55f);
        sr.rect(x + 14, groundY + 12, w - 28, h - 64);
        sr.setColor(1, 1, 1, 0.20f);
        sr.triangle(x + 20, groundY + h - 60, x + 54, groundY + h - 60, x + 20, groundY + 30);
        // door
        sr.setColor(0.05f, 0.08f, 0.12f, 1);
        sr.rect(x + w / 2 - 16, groundY, 32, 52);
        sr.setColor(CYAN);
        sr.circle(x + w / 2 + 9, groundY + 26, 2.2f);
        // sign band
        sr.setColor(signColor);
        sr.rect(x + 6, groundY + h - 44, w - 12, 30);
        if (highlight) {
            float p = 0.5f + 0.5f * (float) Math.sin(time * 5);
            sr.setColor(1f, 0.85f, 0.3f, 0.35f + 0.4f * p);
            sr.rect(x - 4, groundY - 2, w + 8, 3);
            sr.rect(x - 4, groundY + h - 1, w + 8, 3);
            float bob = (float) Math.sin(time * 4) * 5;
            sr.setColor(1f, 0.8f, 0.25f, 1);
            float ay = groundY + h + 26 + bob;
            sr.triangle(x + w / 2 - 11, ay, x + w / 2 + 11, ay, x + w / 2, ay - 14);
        }
        sr.end();
        batch.begin();
        Ui.label(batch, font, label, x + w / 2, groundY + h - 22, Color.WHITE, Align.center);
        batch.end();
    }

    // ------------------------------------------------------------------ hero
    /**
     * Chibi citizen, ~64px tall at scale 1, feet at (x, y). Starter set is gender-neutral techwear;
     * outfit palettes: bo_tan_cu_dan = suit color + cyan trim, bo_street_neon = black + pink trim,
     * bo_tham_hiem = olive (NAM) / mint (NU) + orange accents. Class trim + accessories only when
     * gameClass != NONE.
     */
    public static void drawHero(ShapeRenderer sr, SpriteBatch batch, BitmapFont font,
                                float x, float y, float scale, Gender gender,
                                int suitColorIdx, int hairColorIdx, int hairStyleIdx, int eyeColorIdx, int skinToneIdx,
                                String outfitId, GameClass gameClass,
                                float facing, float walkPhase, float time) {
        boolean female = gender == Gender.NU;
        Color skin = rgb(com.neolang.cyber.model.Player.SKIN_COLORS[Math.abs(skinToneIdx) % com.neolang.cyber.model.Player.SKIN_COLORS.length]);
        float s = scale;
        Color hair = HAIR[Math.abs(hairColorIdx) % HAIR.length];
        // outfit palette
        Color base, trim;
        if ("bo_street_neon".equals(outfitId)) {
            base = new Color(0.08f, 0.08f, 0.11f, 1);
            trim = PINK;
        } else if ("bo_tham_hiem".equals(outfitId)) {
            base = female ? new Color(0.45f, 0.80f, 0.65f, 1) : new Color(0.42f, 0.48f, 0.25f, 1);
            trim = ORANGE;
        } else if (outfitId == null || "bo_tan_cu_dan".equals(outfitId)) {
            base = SUIT[Math.abs(suitColorIdx) % SUIT.length];
            trim = CYAN;
        } else {
            // any other outfit item: use the hex suit color stored on its ItemDef
            com.neolang.cyber.model.ItemDef od = com.neolang.cyber.model.Items.get(outfitId);
            base = od != null ? rgb(od.suitColor) : SUIT[Math.abs(suitColorIdx) % SUIT.length];
            trim = CYAN;
        }
        if (gameClass != null && gameClass != GameClass.NONE) {
            trim = rgb(gameClass.color);
        }
        float swing = (float) Math.sin(walkPhase) * 4 * s;
        float bob = Math.abs((float) Math.sin(walkPhase)) * 1.6f * s;
        float gy = y + bob;

        sr.begin(ShapeType.Filled);
        // contact shadow
        sr.setColor(0, 0, 0, 0.28f);
        sr.ellipse(x - 15 * s, y - 3 * s, 30 * s, 7 * s);
        // battery backpack (behind body, opposite the facing side)
        float bx = x - facing * 13 * s;
        sr.setColor(base.r * 0.6f, base.g * 0.6f, base.b * 0.6f, 1);
        sr.rect(bx - 5 * s, gy + 20 * s, 10 * s, 21 * s);
        sr.setColor(CYAN);
        for (int i = 0; i < 3; i++) sr.rect(bx - 3 * s, gy + 24 * s + i * 5 * s, 6 * s, 3 * s);
        // female hime: long straight back hair behind body
        if (female && hairStyleIdx == 1) {
            sr.setColor(hair);
            sr.rect(x - 13 * s, gy + 14 * s, 26 * s, 46 * s);
        }
        // female twin tails behind shoulders (style 0, the default)
        if (female && hairStyleIdx == 0) {
            sr.setColor(hair);
            sr.ellipse(x - 19 * s, gy + 20 * s, 10 * s, 36 * s);
            sr.ellipse(x + 9 * s, gy + 20 * s, 10 * s, 36 * s);
        }
        // legs + LED boots
        sr.setColor(0.16f, 0.18f, 0.24f, 1);
        if (female) {
            sr.rect(x - 8 * s, gy + 4 * s, 6 * s, 12 * s);
            sr.rect(x + 2 * s, gy + 4 * s, 6 * s, 12 * s);
        } else {
            sr.rect(x - 8 * s + swing * 0.4f, gy + 4 * s, 6 * s, 13 * s);
            sr.rect(x + 2 * s - swing * 0.4f, gy + 4 * s, 6 * s, 13 * s);
        }
        sr.setColor(0.10f, 0.10f, 0.14f, 1);
        sr.rect(x - 9 * s + swing * 0.4f, gy, 8 * s, 5 * s);
        sr.rect(x + 1 * s - swing * 0.4f, gy, 8 * s, 5 * s);
        sr.setColor(trim);
        sr.rect(x - 9 * s + swing * 0.4f, gy + 4 * s, 8 * s, 1.6f * s);
        sr.rect(x + 1 * s - swing * 0.4f, gy + 4 * s, 8 * s, 1.6f * s);
        // pleated tech skirt (female)
        if (female) {
            sr.setColor(base);
            sr.triangle(x - 13 * s, gy + 16 * s, x + 13 * s, gy + 16 * s, x + 9 * s, gy + 28 * s);
            sr.triangle(x - 13 * s, gy + 16 * s, x - 9 * s, gy + 28 * s, x + 9 * s, gy + 28 * s);
            sr.setColor(trim);
            for (int i = -1; i <= 1; i++) sr.rect(x + i * 7 * s - 0.8f * s, gy + 17 * s, 1.6f * s, 9 * s);
        }
        // jacket body
        sr.setColor(base);
        sr.rect(x - 12 * s, gy + 24 * s, 24 * s, 20 * s);
        sr.setColor(trim);
        sr.rect(x - 1 * s, gy + 24 * s, 2 * s, 20 * s);           // glowing zipper circuit
        sr.rect(x - 12 * s, gy + 41 * s, 24 * s, 3 * s);           // collar trim
        sr.circle(x + facing * 6 * s, gy + 36 * s, 2 * s);        // chest light
        // arms
        sr.setColor(base.r * 0.85f, base.g * 0.85f, base.b * 0.85f, 1);
        sr.rect(x - 16 * s, gy + 26 * s - swing * 0.5f, 5 * s, 15 * s);
        sr.rect(x + 11 * s, gy + 26 * s + swing * 0.5f, 5 * s, 15 * s);
        sr.setColor(skin);
        sr.circle(x - 13.5f * s, gy + 25 * s - swing * 0.5f, 2.6f * s);
        sr.circle(x + 13.5f * s, gy + 25 * s + swing * 0.5f, 2.6f * s);
        // scarf (male) — orange chip scarf
        if (!female) {
            sr.setColor(ORANGE);
            sr.rect(x - 10 * s, gy + 42 * s, 20 * s, 4.5f * s);
            sr.rect(x - facing * 10 * s - 2 * s, gy + 36 * s, 4 * s, 7 * s);
        }
        // head
        sr.setColor(skin);
        sr.circle(x, gy + 53 * s, 11 * s);
        // hair back for bob (female style 2)
        // (drawn after head so it frames the face)
        sr.end();

        drawHair(sr, x, gy, s, female, hairStyleIdx, hair, trim, facing, time, skin);

        sr.begin(ShapeType.Filled);
        // face: CUTE CHIBI eyes only — vertical ovals, smooth gradient iris, dark pupil,
        // exactly 2 white sparkles (big top-left, small bottom-right). eyeColorIdx changes
        // ONLY the iris color. Never circuit patterns, never realistic/giant eyes.
        Color iris = rgb(EYE_COLORS[Math.abs(eyeColorIdx) % EYE_COLORS.length]);
        float ex = facing * 1.6f * s;
        drawChibiEye(sr, x - 4.2f * s + ex, gy + 53 * s, s, iris);
        drawChibiEye(sr, x + 4.2f * s + ex, gy + 53 * s, s, iris);
        // blush + mouth
        sr.setColor(1f, 0.55f, 0.55f, 0.55f);
        sr.ellipse(x - 8.5f * s, gy + 48.5f * s, 4 * s, 2.2f * s);
        sr.ellipse(x + 4.5f * s, gy + 48.5f * s, 4 * s, 2.2f * s);
        sr.end();
        sr.begin(ShapeType.Line);
        sr.setColor(0.45f, 0.25f, 0.20f, 1);
        sr.arc(x + ex, gy + 49.5f * s, 2.2f * s, 200, 140);
        sr.end();

        drawClassGear(sr, x, gy, s, gameClass, facing, time);
    }

    /** One cute chibi eye: vertical oval, concentric gradient iris (dark rim -> iris -> lighter
     *  bottom), dark pupil, exactly 2 white sparkles. Assumes ShapeRenderer is in Filled mode. */
    private static void drawChibiEye(ShapeRenderer sr, float cx, float cy, float s, Color iris) {
        // dark rim
        sr.setColor(0.16f, 0.10f, 0.08f, 1);
        sr.ellipse(cx - 1.8f * s, cy - 2.6f * s, 3.6f * s, 5.2f * s);
        // iris
        sr.setColor(iris);
        sr.ellipse(cx - 1.5f * s, cy - 2.25f * s, 3.0f * s, 4.5f * s);
        // lighter gradient at the bottom of the iris
        sr.setColor(iris.r + (1 - iris.r) * 0.45f, iris.g + (1 - iris.g) * 0.45f, iris.b + (1 - iris.b) * 0.45f, 1);
        sr.ellipse(cx - 1.2f * s, cy - 2.25f * s, 2.4f * s, 2.6f * s);
        // dark pupil
        sr.setColor(0.10f, 0.06f, 0.08f, 1);
        sr.ellipse(cx - 0.75f * s, cy - 1.2f * s, 1.5f * s, 2.4f * s);
        // exactly 2 sparkles: big top-left, small bottom-right
        sr.setColor(Color.WHITE);
        sr.circle(cx - 0.8f * s, cy + 1.2f * s, 0.85f * s);
        sr.circle(cx + 0.8f * s, cy - 1.3f * s, 0.45f * s);
    }

    private static void drawHair(ShapeRenderer sr, float x, float gy, float s, boolean female,
                                 int style, Color hair, Color trim, float facing, float time, Color skin) {
        float hy = gy + 53 * s;
        sr.begin(ShapeType.Filled);
        sr.setColor(hair);
        if (!female) {
            if (style == 1) {
                // undercut: short cap + glowing cyan headband
                sr.circle(x, hy + 2.5f * s, 11 * s);
                sr.setColor(skin);
                sr.circle(x, hy - 1.5f * s, 10.2f * s);
                sr.setColor(hair);
                sr.rect(x - 11 * s, hy + 5 * s, 22 * s, 5 * s);
                float p = 0.6f + 0.4f * (float) Math.sin(time * 4);
                sr.setColor(0.25f, 0.95f, 1f, p);
                sr.rect(x - 11 * s, hy + 3.4f * s, 22 * s, 2 * s);
            } else if (style == 2) {
                // curly puff: cluster of circles + a tiny sprout
                sr.circle(x - 7 * s, hy + 6 * s, 5.5f * s);
                sr.circle(x, hy + 8.5f * s, 6.5f * s);
                sr.circle(x + 7 * s, hy + 6 * s, 5.5f * s);
                sr.circle(x - 3.5f * s, hy + 10 * s, 5 * s);
                sr.circle(x + 3.5f * s, hy + 10 * s, 5 * s);
                sr.setColor(0.30f, 0.85f, 0.35f, 1);
                sr.ellipse(x + 0.5f * s, hy + 15.5f * s, 5 * s, 3 * s);
                sr.ellipse(x - 5 * s, hy + 15.5f * s, 5 * s, 3 * s);
            } else {
                // messy natural: untidy triangles on top
                sr.circle(x, hy + 2 * s, 11.2f * s);
                sr.setColor(skin);
                sr.circle(x, hy - 2 * s, 10.4f * s);
                sr.setColor(hair);
                sr.triangle(x - 10 * s, hy + 6 * s, x - 4 * s, hy + 15 * s, x - 1 * s, hy + 6 * s);
                sr.triangle(x - 3 * s, hy + 7 * s, x + 2 * s, hy + 16.5f * s, x + 5 * s, hy + 7 * s);
                sr.triangle(x + 4 * s, hy + 6 * s, x + 9 * s, hy + 14 * s, x + 11 * s, hy + 5 * s);
            }
        } else {
            if (style == 1) {
                // hime: straight bangs + long back hair (back already drawn)
                sr.rect(x - 11 * s, hy + 3 * s, 22 * s, 7 * s);
                sr.rect(x - 11 * s, hy - 6 * s, 3.4f * s, 12 * s);
                sr.rect(x + 7.6f * s, hy - 6 * s, 3.4f * s, 12 * s);
            } else if (style == 2) {
                // bob framing the face + big headphones
                sr.circle(x, hy + 1 * s, 12.6f * s);
                sr.setColor(skin);
                sr.circle(x, hy - 1.8f * s, 10.3f * s);
                sr.setColor(hair);
                sr.rect(x - 10.3f * s, hy + 4 * s, 20.6f * s, 6 * s);
                sr.rect(x - 12.6f * s, hy - 9 * s, 3.6f * s, 13 * s);
                sr.rect(x + 9 * s, hy - 9 * s, 3.6f * s, 13 * s);
                sr.setColor(0.12f, 0.12f, 0.16f, 1);
                sr.rect(x - 14.5f * s, hy - 4 * s, 4.6f * s, 9 * s);
                sr.rect(x + 9.9f * s, hy - 4 * s, 4.6f * s, 9 * s);
                sr.setColor(trim);
                sr.rect(x - 14.5f * s, hy - 0.5f * s, 4.6f * s, 1.8f * s);
                sr.rect(x + 9.9f * s, hy - 0.5f * s, 4.6f * s, 1.8f * s);
            } else {
                // twin tails (default): top cap + LED rings at the roots + orange chip bow
                sr.circle(x, hy + 2 * s, 11.2f * s);
                sr.setColor(skin);
                sr.circle(x, hy - 2 * s, 10.4f * s);
                sr.setColor(hair);
                sr.rect(x - 11 * s, hy + 4.5f * s, 22 * s, 5.5f * s);
                float p = 0.6f + 0.4f * (float) Math.sin(time * 4);
                sr.setColor(0.25f, 0.95f, 1f, p);
                sr.circle(x - 13.5f * s, hy + 6 * s, 3 * s);
                sr.circle(x + 13.5f * s, hy + 6 * s, 3 * s);
                sr.setColor(ORANGE);
                sr.triangle(x + 9 * s, hy + 9 * s, x + 15 * s, hy + 12 * s, x + 14 * s, hy + 6 * s);
                sr.triangle(x + 9 * s, hy + 9 * s, x + 14 * s, hy + 6 * s, x + 10 * s, hy + 4.5f * s);
            }
        }
        sr.end();
    }

    private static void drawClassGear(ShapeRenderer sr, float x, float gy, float s,
                                      GameClass gc, float facing, float time) {
        if (gc == null || gc == GameClass.NONE) return;
        float hx = x + facing * 16 * s;
        float hy = gy + 30 * s;
        sr.begin(ShapeType.Filled);
        switch (gc) {
            case BLADE_RUNNER: {
                float p = 0.7f + 0.3f * (float) Math.sin(time * 6);
                sr.setColor(0.30f, 0.95f, 1f, p);
                sr.triangle(hx, hy + 10 * s, hx + facing * 4 * s, hy + 10 * s, hx + facing * 2 * s, hy - 12 * s);
                sr.setColor(0.9f, 0.95f, 1f, 0.6f);
                sr.circle(hx + facing * 2 * s, hy + 4 * s, 1.6f * s);
                break;
            }
            case DRONE_MASTER: {
                for (int i = 0; i < 2; i++) {
                    double a = time * 2.4 + i * Math.PI;
                    float dx = x + (float) Math.cos(a) * 22 * s;
                    float dy = gy + 62 * s + (float) Math.sin(a * 2) * 4 * s;
                    sr.setColor(0.55f, 0.60f, 0.68f, 1);
                    sr.circle(dx, dy, 4.5f * s);
                    sr.setColor(CYAN);
                    sr.circle(dx, dy, 1.8f * s);
                    sr.setColor(0.3f, 0.35f, 0.4f, 1);
                    sr.rect(dx - 7 * s, dy + 4 * s, 14 * s, 1.6f * s);
                }
                break;
            }
            case BIO_HACKER: {
                sr.setColor(0.30f, 0.95f, 0.40f, 0.85f);
                sr.rect(hx - 2.5f * s, hy - 6 * s, 5 * s, 10 * s);
                sr.setColor(0.8f, 1f, 0.85f, 1);
                sr.circle(hx, hy + 1 * s, 1.6f * s);
                float dx = x - facing * 20 * s;
                float dy = gy + 66 * s + (float) Math.sin(time * 3) * 3 * s;
                sr.setColor(0.55f, 0.65f, 0.55f, 1);
                sr.circle(dx, dy, 4 * s);
                sr.setColor(0.35f, 0.85f, 1f, 1);
                sr.circle(dx, dy - 5 * s, 1.4f * s);
                break;
            }
            case ENGINEER: {
                sr.setColor(1f, 0.75f, 0.25f, 1);
                sr.rect(hx - 1.5f * s, hy - 8 * s, 3 * s, 11 * s);
                sr.circle(hx, hy + 4.5f * s, 3 * s);
                break;
            }
            default:
                break;
        }
        sr.end();
        if (gc == GameClass.ENGINEER) {
            // faint energy shield bubble
            sr.begin(ShapeType.Line);
            float p = 0.35f + 0.2f * (float) Math.sin(time * 3);
            sr.setColor(0.4f, 0.85f, 1f, p);
            sr.circle(x, gy + 32 * s, 30 * s);
            sr.end();
        }
    }

    // ------------------------------------------------------------------ painted sprites
    /** Gender index into the Assets sprite arrays: 0 = NAM, 1 = NU. */
    public static int gi(Gender g) { return g == Gender.NU ? 1 : 0; }

    public static int outfitIndex(String outfitId) {
        for (int i = 0; i < OUTFIT_IDS.length; i++) if (OUTFIT_IDS[i].equals(outfitId)) return i;
        return 0;
    }

    /** Draw one painted texture centred at cx with its feet/base at feetY. flip mirrors it. */
    private static void drawTex(SpriteBatch batch, Texture t, float cx, float feetY, float h, boolean flip) {
        float w = h * t.getWidth() / (float) t.getHeight();
        batch.draw(t, cx - w / 2, feetY, w, h, 0, 0, t.getWidth(), t.getHeight(), flip, false);
    }

    /**
     * Painted hero sprite (feet at x/y): front view when idle, side profile while moving.
     * The male side painting faces LEFT, the female side painting faces RIGHT — flips are
     * resolved per gender. Outfits other than the starter set use the painted full figure
     * from the customization sheet (front view only, mirrored toward facing). A soft bob
     * plays while moving; the class-gear overlay is drawn on top when a class is chosen.
     * Height matches the old shape hero (~74px at scale 1). Returns false when no sprite
     * is loaded so the caller can fall back to shape drawing.
     */
    public static boolean drawHeroSprite(SpriteBatch batch, ShapeRenderer sr, Assets a,
                                         float x, float y, float scale, Gender gender, String outfitId,
                                         GameClass gc, float facing, boolean moving,
                                         float walkPhase, float time) {
        int g = gi(gender);
        int oi = outfitIndex(outfitId);
        Texture t;
        boolean flip;
        if (oi > 0 && a.custFig[g][oi] != null) {
            t = a.custFig[g][oi];
            flip = facing < 0;
        } else if (moving && a.heroSide[g] != null) {
            t = a.heroSide[g];
            flip = gender == Gender.NAM ? facing > 0 : facing < 0;
        } else {
            t = a.heroFront[g] != null ? a.heroFront[g] : a.custFig[g][0];
            flip = false;
        }
        if (t == null) return false;
        float bob = moving ? Math.abs((float) Math.sin(walkPhase)) * 3f
                           : (float) Math.sin(time * 2) * 0.8f;
        batch.begin();
        drawTex(batch, t, x, y + bob - 2, 74f * scale, flip);
        batch.end();
        if (gc != null && gc != GameClass.NONE) {
            drawClassGear(sr, x, y + bob, scale * 1.12f, gc, facing, time);
        }
        return true;
    }

    /** Painted full-figure sprite for NPCs (customization-sheet figures), feet at x/y. */
    public static boolean drawFigureSprite(SpriteBatch batch, Assets a, Gender gender, int figIdx,
                                           float x, float y, float scale, float facing,
                                           boolean moving, float walkPhase, float time) {
        Texture t = a.custFig[gi(gender)][Math.abs(figIdx) % 3];
        if (t == null) return false;
        float bob = moving ? Math.abs((float) Math.sin(walkPhase)) * 2.4f : 0;
        batch.begin();
        drawTex(batch, t, x, y + bob - 2, 72f * scale, facing < 0);
        batch.end();
        return true;
    }

    /** Painted robot-pet sprite, feet/base at x/y with its idle hover bob. */
    public static boolean drawPetSprite(SpriteBatch batch, Assets a, float x, float y, float time, int tier) {
        if (a.petSpr == null) return false;
        float h = 34 + Math.min(3, Math.max(1, tier)) * 5;
        float bob = (float) Math.sin(time * 3) * 2.5f;
        batch.begin();
        drawTex(batch, a.petSpr, x, y + bob - 2, h, false);
        batch.end();
        return true;
    }

    /** Painted monster sprite (feet at x/y, ~66px tall) + HP bar. Flash tints it red. */
    public static void drawMonsterSprite(SpriteBatch batch, ShapeRenderer sr, Texture t,
                                          float x, float y, float hpFrac, float time, float hitFlash) {
        float wob = (float) Math.sin(time * 4 + x * 0.05f) * 2;
        sr.begin(ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.28f);
        sr.ellipse(x - 20, y - 3, 40, 8);
        sr.end();
        batch.begin();
        if (hitFlash > 0) batch.setColor(1f, 1f - 0.55f * hitFlash, 1f - 0.55f * hitFlash, 1f);
        drawTex(batch, t, x, y + wob * 0.4f, 66, false);
        batch.setColor(1, 1, 1, 1);
        batch.end();
        if (hpFrac < 1f) {
            Ui.bar(sr, x - 18, y + 70, 36, 5, hpFrac,
                new Color(0.1f, 0.05f, 0.08f, 0.8f), new Color(1f, 0.3f, 0.3f, 1));
        }
    }

    // ------------------------------------------------------------------ pet & monsters
    public static void drawPet(ShapeRenderer sr, float x, float y, float time, int tier) {
        float s = 0.8f + 0.18f * Math.min(3, Math.max(1, tier));
        float bob = (float) Math.sin(time * 3) * 3;
        float cy = y + 14 * s + bob;
        sr.begin(ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.25f);
        sr.ellipse(x - 10 * s, y - 2, 20 * s, 5 * s);
        sr.setColor(0.80f, 0.85f, 0.92f, 1);
        sr.circle(x, cy, 11 * s);
        sr.setColor(0.55f, 0.62f, 0.72f, 1);
        sr.circle(x, cy - 8 * s, 6 * s);
        // LED eyes
        float blink = (float) Math.sin(time * 0.9f) > 0.97f ? 0.3f : 1f;
        sr.setColor(CYAN);
        sr.ellipse(x - 6 * s, cy + 0.5f * s, 3.6f * s, 3.6f * s * blink);
        sr.ellipse(x + 2.4f * s, cy + 0.5f * s, 3.6f * s, 3.6f * s * blink);
        // antenna + ears
        sr.setColor(0.4f, 0.45f, 0.55f, 1);
        sr.rect(x - 0.8f * s, cy + 10 * s, 1.6f * s, 6 * s);
        sr.setColor(1f, 0.45f, 0.6f, 1);
        sr.circle(x, cy + 17 * s, 2.4f * s);
        sr.setColor(0.65f, 0.70f, 0.80f, 1);
        sr.triangle(x - 11 * s, cy + 6 * s, x - 15 * s, cy + 12 * s, x - 8 * s, cy + 11 * s);
        sr.triangle(x + 11 * s, cy + 6 * s, x + 15 * s, cy + 12 * s, x + 8 * s, cy + 11 * s);
        if (tier >= 2) {
            sr.setColor(1f, 0.80f, 0.25f, 1);
            sr.circle(x, cy - 3 * s, 2 * s);
        }
        sr.end();
    }

    public static void drawMonster(ShapeRenderer sr, float x, float y, MonsterDef def,
                                   float hpFrac, float time, float hitFlash) {
        Color main = rgb(def.color);
        if (hitFlash > 0) main.lerp(Color.WHITE, Math.min(1, hitFlash * 2));
        float wob = (float) Math.sin(time * 4 + x * 0.05f) * 2;
        sr.begin(ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.25f);
        switch (def) {
            case GLITCH_BLOB: {
                sr.ellipse(x - 18, y - 3, 36, 7);
                sr.setColor(main);
                sr.circle(x, y + 16 + wob * 0.3f, 17);
                sr.circle(x - 12, y + 10, 10);
                sr.circle(x + 13, y + 9, 9);
                sr.setColor(0.1f, 0.9f, 0.4f, 0.8f);
                sr.rect(x - 20, y + 24, 6, 6);
                sr.rect(x + 15, y + 18, 5, 5);
                sr.setColor(Color.WHITE);
                sr.circle(x - 5, y + 19, 3);
                sr.circle(x + 6, y + 19, 3);
                sr.setColor(0.05f, 0.1f, 0.05f, 1);
                sr.circle(x - 5, y + 19, 1.4f);
                sr.circle(x + 6, y + 19, 1.4f);
                break;
            }
            case MUSH_BUG: {
                sr.ellipse(x - 15, y - 3, 30, 6);
                sr.setColor(main);
                sr.ellipse(x - 9, y + 4, 18, 14);
                sr.setColor(0.95f, 0.35f, 0.45f, 1);
                sr.arc(x, y + 15, 15, 0, 180);
                sr.circle(x, y + 15, 15);
                sr.setColor(1, 1, 1, 0.85f);
                sr.circle(x - 7, y + 22, 3);
                sr.circle(x + 6, y + 25, 2.4f);
                sr.setColor(0.1f, 0.1f, 0.1f, 1);
                sr.circle(x - 3, y + 10, 1.8f);
                sr.circle(x + 4, y + 10, 1.8f);
                sr.setColor(main);
                sr.rect(x - 12, y, 3, 5);
                sr.rect(x + 9, y, 3, 5);
                break;
            }
            case BEETLE_BOT: {
                sr.ellipse(x - 19, y - 3, 38, 7);
                sr.setColor(main);
                sr.rect(x - 17, y + 6, 34, 17);
                sr.circle(x - 17, y + 14, 8);
                sr.circle(x + 17, y + 14, 8);
                sr.setColor(0.25f, 0.28f, 0.34f, 1);
                sr.rect(x - 12, y + 17, 24, 4);
                sr.setColor(1f, 0.25f, 0.25f, 1);
                sr.circle(x + 10, y + 13, 3);
                sr.setColor(0.85f, 0.88f, 0.92f, 1);
                sr.triangle(x + 14, y + 20, x + 24, y + 27, x + 15, y + 14);
                sr.setColor(0.15f, 0.16f, 0.2f, 1);
                sr.circle(x - 9, y + 3, 5);
                sr.circle(x + 9, y + 3, 5);
                break;
            }
            case DATA_WRAITH: {
                float fl = (float) Math.sin(time * 2.2f) * 4;
                sr.ellipse(x - 22, y - 3, 44, 8);
                sr.setColor(main.r, main.g, main.b, 0.9f);
                sr.triangle(x - 22, y + 6 + fl, x + 22, y + 6 + fl, x + 12, y + 52 + fl);
                sr.triangle(x - 22, y + 6 + fl, x - 12, y + 52 + fl, x + 12, y + 52 + fl);
                sr.circle(x, y + 48 + fl, 15);
                sr.setColor(0.85f, 0.4f, 1f, 1);
                sr.circle(x - 5, y + 49 + fl, 2.6f);
                sr.circle(x + 5, y + 49 + fl, 2.6f);
                sr.setColor(0.2f, 0.05f, 0.3f, 1);
                sr.ellipse(x - 3, y + 41 + fl, 6, 3);
                break;
            }
            default:
                sr.setColor(main);
                sr.circle(x, y + 14, 14);
                break;
        }
        sr.end();
        if (hpFrac < 1f) {
            Ui.bar(sr, x - 18, y + 58, 36, 5, hpFrac,
                new Color(0.1f, 0.05f, 0.08f, 0.8f), new Color(1f, 0.3f, 0.3f, 1));
        }
    }

    // ------------------------------------------------------------------ plot pieces
    public static void drawGardenBed(ShapeRenderer sr, float x, float y, float w, float h,
                                     boolean planted, float progress, boolean ready, int seedIdx, float time) {
        sr.begin(ShapeType.Filled);
        // glass box
        sr.setColor(0.45f, 0.85f, 0.95f, 0.16f);
        sr.rect(x, y, w, h);
        sr.setColor(0.25f, 0.95f, 1f, 0.9f);
        sr.rect(x, y + h - 3, w, 3);
        sr.rect(x, y, w, 3);
        sr.rect(x, y, 3, h);
        sr.rect(x + w - 3, y, 3, h);
        // hydroponic soil / gel
        sr.setColor(0.16f, 0.30f, 0.22f, 1);
        sr.rect(x + 6, y + 6, w - 12, 20);
        sr.setColor(0.25f, 0.95f, 1f, 0.35f);
        sr.rect(x + 6, y + 24, w - 12, 2);
        if (planted) {
            Color leaf = new Color(0.30f, 0.85f, 0.35f, 1);
            Color fruit = seedIdx % 3 == 0 ? new Color(1f, 0.35f, 0.3f, 1)
                : seedIdx % 3 == 1 ? new Color(1f, 0.75f, 0.25f, 1) : new Color(0.75f, 0.45f, 1f, 1);
            float grow = 8 + progress * (h - 46);
            float cx = x + w / 2;
            sr.setColor(0.25f, 0.65f, 0.30f, 1);
            sr.rect(cx - 1.5f, y + 24, 3, grow);
            sr.setColor(leaf);
            float ls = 4 + progress * 7;
            sr.ellipse(cx - ls - 3, y + 24 + grow * 0.55f, ls, ls * 0.55f);
            sr.ellipse(cx + 3, y + 24 + grow * 0.7f, ls, ls * 0.55f);
            sr.circle(cx, y + 26 + grow, 3 + progress * 4);
            if (progress > 0.55f) {
                sr.setColor(fruit);
                sr.circle(cx - 8, y + 26 + grow * 0.75f, 3.4f);
                sr.circle(cx + 8, y + 26 + grow * 0.85f, 3.4f);
            }
            if (ready) {
                float p = 0.5f + 0.5f * (float) Math.sin(time * 5);
                sr.setColor(1f, 0.85f, 0.3f, 0.35f + 0.35f * p);
                sr.rect(x - 3, y - 3, w + 6, 3);
                sr.rect(x - 3, y + h, w + 6, 3);
            }
        }
        sr.end();
    }

    public static void drawModule(ShapeRenderer sr, BuildModule m, float cx, float cy, float cell, float time) {
        float r = cell * 0.36f;
        sr.begin(ShapeType.Filled);
        switch (m) {
            case POD_TRON:
                sr.setColor(0.55f, 0.65f, 0.75f, 1);
                sr.circle(cx, cy, r);
                sr.setColor(0.45f, 0.80f, 0.90f, 0.8f);
                sr.circle(cx, cy + r * 0.15f, r * 0.55f);
                break;
            case GIUONG:
                sr.setColor(0.35f, 0.45f, 0.80f, 1);
                sr.rect(cx - r, cy - r * 0.5f, r * 2, r);
                sr.setColor(0.95f, 0.95f, 1f, 1);
                sr.rect(cx - r, cy + r * 0.15f, r * 0.7f, r * 0.35f);
                break;
            case BEP:
                sr.setColor(0.75f, 0.45f, 0.25f, 1);
                sr.rect(cx - r * 0.85f, cy - r * 0.7f, r * 1.7f, r * 1.4f);
                sr.setColor(0.2f, 0.2f, 0.25f, 1);
                sr.circle(cx, cy + r * 0.1f, r * 0.38f);
                sr.setColor(1f, 0.55f, 0.2f, 0.8f);
                sr.circle(cx, cy + r * 0.1f, r * 0.2f);
                break;
            case XUONG:
                sr.setColor(0.55f, 0.55f, 0.60f, 1);
                sr.rect(cx - r * 0.8f, cy - r * 0.8f, r * 1.6f, r * 1.6f);
                sr.setColor(1f, 0.75f, 0.25f, 1);
                sr.circle(cx - r * 0.35f, cy + r * 0.35f, r * 0.16f);
                sr.circle(cx + r * 0.35f, cy + r * 0.35f, r * 0.16f);
                sr.rect(cx - r * 0.1f, cy - r * 0.5f, r * 0.2f, r * 0.6f);
                break;
            case TRAM_SAC: {
                sr.setColor(0.30f, 0.35f, 0.45f, 1);
                sr.rect(cx - r * 0.6f, cy - r * 0.85f, r * 1.2f, r * 1.7f);
                float p = 0.6f + 0.4f * (float) Math.sin(time * 6);
                sr.setColor(0.30f, 0.95f, 1f, p);
                sr.triangle(cx + r * 0.12f, cy + r * 0.55f, cx - r * 0.22f, cy - r * 0.05f, cx + r * 0.02f, cy - r * 0.05f);
                sr.triangle(cx + r * 0.02f, cy - r * 0.05f, cx - r * 0.12f, cy - r * 0.55f, cx + r * 0.22f, cy - r * 0.05f);
                break;
            }
            case DOCK_PET:
                sr.setColor(0.45f, 0.50f, 0.60f, 1);
                sr.ellipse(cx - r, cy - r * 0.45f, r * 2, r * 0.9f);
                sr.setColor(0.25f, 0.95f, 1f, 0.7f);
                sr.ellipse(cx - r * 0.6f, cy - r * 0.25f, r * 1.2f, r * 0.5f);
                break;
            case DEN_MACH: {
                sr.setColor(0.35f, 0.35f, 0.40f, 1);
                sr.rect(cx - 2, cy - r, 4, r * 1.2f);
                float p = 0.7f + 0.3f * (float) Math.sin(time * 3);
                sr.setColor(1f, 0.85f, 0.40f, p);
                sr.circle(cx, cy + r * 0.45f, r * 0.34f);
                break;
            }
            case BAN_HOLO: {
                sr.setColor(0.25f, 0.30f, 0.38f, 1);
                sr.rect(cx - r * 0.9f, cy - r * 0.7f, r * 1.8f, r * 0.35f);
                float p = 0.35f + 0.15f * (float) Math.sin(time * 2.5f);
                sr.setColor(0.30f, 0.90f, 1f, p);
                sr.rect(cx - r * 0.7f, cy - r * 0.3f, r * 1.4f, r * 1.1f);
                break;
            }
            case CHAU_CAY:
                sr.setColor(0.70f, 0.40f, 0.25f, 1);
                sr.triangle(cx - r * 0.7f, cy - r * 0.6f, cx + r * 0.7f, cy - r * 0.6f, cx + r * 0.45f, cy + r * 0.1f);
                sr.triangle(cx - r * 0.7f, cy - r * 0.6f, cx - r * 0.45f, cy + r * 0.1f, cx + r * 0.45f, cy + r * 0.1f);
                sr.setColor(0.30f, 0.85f, 0.35f, 1);
                sr.circle(cx, cy + r * 0.45f, r * 0.4f);
                sr.circle(cx - r * 0.35f, cy + r * 0.25f, r * 0.26f);
                sr.circle(cx + r * 0.35f, cy + r * 0.25f, r * 0.26f);
                break;
            default:
                sr.setColor(0.5f, 0.5f, 0.55f, 1);
                sr.rect(cx - r, cy - r, r * 2, r * 2);
                break;
        }
        sr.end();
    }

    /** Level pips under a placed module. */
    public static void drawLevelPips(ShapeRenderer sr, float cx, float y, int level) {
        sr.begin(ShapeType.Filled);
        for (int i = 0; i < Math.min(5, level); i++) {
            sr.setColor(1f, 0.82f, 0.25f, 1);
            sr.rect(cx - 14 + i * 7, y, 5, 3);
        }
        sr.end();
    }
}
