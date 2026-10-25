package com.neolang.cyber.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.neolang.cyber.NeoLangGame;

/** Immediate-mode UI helpers. All coordinates are world/HUD space (1280x720). No emoji anywhere:
 *  icons are drawn with shapes. Buttons report a click on the frame the touch is released-tapped
 *  (justTouched inside bounds). */
public class Ui {
    public static final Color PANEL = new Color(0.05f, 0.10f, 0.15f, 0.93f);
    public static final Color PANEL_EDGE = new Color(0.15f, 0.85f, 0.90f, 0.9f);
    public static final Color BTN = new Color(0.07f, 0.22f, 0.28f, 0.97f);
    public static final Color BTN_HOVER = new Color(0.10f, 0.34f, 0.40f, 0.97f);
    public static final Color BTN_OFF = new Color(0.16f, 0.18f, 0.20f, 0.9f);
    public static final Color TEXT = new Color(0.92f, 0.98f, 1f, 1f);
    public static final Color TEXT_DIM = new Color(0.62f, 0.75f, 0.80f, 1f);
    public static final Color ACCENT = new Color(1f, 0.62f, 0.15f, 1f);
    public static final Color CYAN = new Color(0.25f, 0.95f, 1f, 1f);
    public static final Color GREEN = new Color(0.35f, 0.95f, 0.45f, 1f);
    public static final Color RED = new Color(1f, 0.32f, 0.30f, 1f);
    public static final Color GOLD = new Color(1f, 0.82f, 0.25f, 1f);

    private static final GlyphLayout LAYOUT = new GlyphLayout();
    private static final Vector2 TMP = new Vector2();

    public static Vector2 pointer(Viewport vp) {
        TMP.set(Gdx.input.getX(), Gdx.input.getY());
        return vp.unproject(TMP);
    }

    public static boolean inside(Viewport vp, float x, float y, float w, float h) {
        Vector2 p = pointer(vp);
        return p.x >= x && p.x <= x + w && p.y >= y && p.y <= y + h;
    }

    /** True on the frame a tap lands inside the rect. */
    public static boolean tapped(Viewport vp, float x, float y, float w, float h) {
        return Gdx.input.justTouched() && inside(vp, x, y, w, h);
    }

    public static void panel(ShapeRenderer sr, float x, float y, float w, float h, Color fill) {
        sr.begin(ShapeType.Filled);
        sr.setColor(fill);
        sr.rect(x, y, w, h);
        sr.setColor(PANEL_EDGE);
        sr.rect(x, y + h - 3, w, 3);
        sr.rect(x, y, w, 3);
        sr.rect(x, y, 3, h);
        sr.rect(x + w - 3, y, 3, h);
        sr.end();
    }

    public static void panel(ShapeRenderer sr, float x, float y, float w, float h) {
        panel(sr, x, y, w, h, PANEL);
    }

    /** Draws a button; returns true when tapped this frame. Manages its own begin/end. */
    public static boolean button(SpriteBatch batch, ShapeRenderer sr, BitmapFont font, Viewport vp,
                                 float x, float y, float w, float h, String label) {
        return button(batch, sr, font, vp, x, y, w, h, label, true);
    }

    public static boolean button(SpriteBatch batch, ShapeRenderer sr, BitmapFont font, Viewport vp,
                                 float x, float y, float w, float h, String label, boolean enabled) {
        boolean hover = enabled && inside(vp, x, y, w, h);
        sr.begin(ShapeType.Filled);
        sr.setColor(!enabled ? BTN_OFF : (hover ? BTN_HOVER : BTN));
        sr.rect(x, y, w, h);
        sr.setColor(enabled ? PANEL_EDGE : TEXT_DIM);
        sr.rect(x, y + h - 2, w, 2);
        sr.rect(x, y, w, 2);
        sr.rect(x, y, 2, h);
        sr.rect(x + w - 2, y, 2, h);
        sr.end();
        batch.begin();
        label(batch, font, label, x + w / 2, y + h / 2 + font.getCapHeight() / 2,
            enabled ? TEXT : TEXT_DIM, Align.center);
        batch.end();
        return enabled && Gdx.input.justTouched() && hover;
    }

    /** Text draw; caller must have batch begun. y is the baseline. align: Align.left/center/right relative to x. */
    public static void label(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color, int align) {
        if (text == null) text = "";
        font.setColor(color);
        LAYOUT.setText(font, text);
        float tx = x;
        if (align == Align.center) tx = x - LAYOUT.width / 2;
        else if (align == Align.right) tx = x - LAYOUT.width;
        font.draw(batch, text, tx, y);
        font.setColor(Color.WHITE);
    }

    public static float textWidth(BitmapFont font, String text) {
        LAYOUT.setText(font, text == null ? "" : text);
        return LAYOUT.width;
    }

    public static void bar(ShapeRenderer sr, float x, float y, float w, float h, float frac, Color bg, Color fg) {
        float f = Math.max(0, Math.min(1, frac));
        sr.begin(ShapeType.Filled);
        sr.setColor(bg);
        sr.rect(x, y, w, h);
        sr.setColor(fg);
        if (f > 0) sr.rect(x + 1, y + 1, (w - 2) * f, h - 2);
        sr.end();
    }

    /** Name tag: soft dark pill + centered text. Caller manages nothing; own begin/end. */
    public static void nameTag(SpriteBatch batch, ShapeRenderer sr, BitmapFont font, String name, float cx, float y) {
        float w = textWidth(font, name) + 16;
        sr.begin(ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.45f);
        sr.rect(cx - w / 2, y - 4, w, font.getLineHeight() + 6);
        sr.end();
        batch.begin();
        label(batch, font, name, cx, y + font.getCapHeight(), TEXT, Align.center);
        batch.end();
    }

    // ---- shape icons (no emoji) ----
    public static void iconMail(ShapeRenderer sr, float cx, float cy, float s, Color c) {
        sr.begin(ShapeType.Filled);
        sr.setColor(c);
        sr.rect(cx - s, cy - s * 0.7f, s * 2, s * 1.4f);
        sr.setColor(PANEL);
        sr.triangle(cx - s, cy + s * 0.7f, cx + s, cy + s * 0.7f, cx, cy - s * 0.1f);
        sr.end();
    }

    public static void iconChat(ShapeRenderer sr, float cx, float cy, float s, Color c) {
        sr.begin(ShapeType.Filled);
        sr.setColor(c);
        sr.circle(cx, cy + s * 0.15f, s);
        sr.triangle(cx - s * 0.5f, cy - s * 0.6f, cx + s * 0.1f, cy - s * 0.6f, cx - s * 0.3f, cy - s * 1.2f);
        sr.setColor(PANEL);
        sr.circle(cx - s * 0.35f, cy + s * 0.2f, s * 0.14f);
        sr.circle(cx + s * 0.05f, cy + s * 0.2f, s * 0.14f);
        sr.circle(cx + s * 0.45f, cy + s * 0.2f, s * 0.14f);
        sr.end();
    }

    public static void iconRun(ShapeRenderer sr, float cx, float cy, float s, Color c) {
        sr.begin(ShapeType.Filled);
        sr.setColor(c);
        sr.triangle(cx - s, cy - s * 0.8f, cx - s, cy + s * 0.8f, cx + s, cy);
        sr.end();
    }

    public static void iconSmile(ShapeRenderer sr, float cx, float cy, float s, Color c) {
        sr.begin(ShapeType.Line);
        sr.setColor(c);
        sr.circle(cx, cy, s);
        sr.arc(cx, cy - s * 0.1f, s * 0.55f, 200, 140);
        sr.end();
        sr.begin(ShapeType.Filled);
        sr.setColor(c);
        sr.circle(cx - s * 0.35f, cy + s * 0.25f, s * 0.12f);
        sr.circle(cx + s * 0.35f, cy + s * 0.25f, s * 0.12f);
        sr.end();
    }

    public static void iconGear(ShapeRenderer sr, float cx, float cy, float s, Color c) {
        sr.begin(ShapeType.Filled);
        sr.setColor(c);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            sr.circle(cx + (float) Math.cos(a) * s * 0.85f, cy + (float) Math.sin(a) * s * 0.85f, s * 0.28f);
        }
        sr.circle(cx, cy, s * 0.8f);
        sr.setColor(PANEL);
        sr.circle(cx, cy, s * 0.38f);
        sr.end();
    }

    /** Shared settings block (UI scale / FPS / sound / music). Drawn inside a panel at (x,y,w). */
    public static void drawSettings(NeoLangGame game, Viewport vp, float x, float y, float w) {
        float[] scales = {0.9f, 1.0f, 1.15f, 1.3f};
        String scaleLabel = "Cỡ giao diện: " + trimScale(game.state.settings.uiScale) + "x";
        if (button(game.batch, game.shapes, game.assets.fontSmall, vp, x, y, w, 36, scaleLabel)) {
            float cur = game.state.settings.uiScale;
            int idx = 0;
            for (int i = 0; i < scales.length; i++) if (Math.abs(scales[i] - cur) < 0.01f) idx = i;
            float next = scales[(idx + 1) % scales.length];
            game.state.settings.uiScale = next;
            game.assets.rebuildFonts(next);
            game.sound.play("click");
            game.saveGame();
        }
        String fpsLabel = "Khung hình: " + (game.state.settings.fps60 ? "60 FPS" : "30 FPS");
        if (button(game.batch, game.shapes, game.assets.fontSmall, vp, x, y - 46, w, 36, fpsLabel)) {
            game.state.settings.fps60 = !game.state.settings.fps60;
            game.sound.play("click");
            game.saveGame();
        }
        String sndLabel = "Âm thanh: " + (game.state.settings.sound ? "Bật" : "Tắt");
        if (button(game.batch, game.shapes, game.assets.fontSmall, vp, x, y - 92, w, 36, sndLabel)) {
            game.state.settings.sound = !game.state.settings.sound;
            game.sound.setEnabled(game.state.settings.sound, game.state.settings.music);
            game.sound.play("click");
            game.saveGame();
        }
        String musLabel = "Nhạc nền: " + (game.state.settings.music ? "Bật" : "Tắt");
        if (button(game.batch, game.shapes, game.assets.fontSmall, vp, x, y - 138, w, 36, musLabel)) {
            game.state.settings.music = !game.state.settings.music;
            game.sound.setEnabled(game.state.settings.sound, game.state.settings.music);
            game.saveGame();
        }
    }

    private static String trimScale(float s) {
        if (Math.abs(s - Math.round(s)) < 0.01f) return Integer.toString(Math.round(s));
        return Float.toString(Math.round(s * 100) / 100f);
    }
}
