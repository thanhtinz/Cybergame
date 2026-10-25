package com.neolang.cyber.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.Player;
import com.neolang.cyber.ui.Draw;
import com.neolang.cyber.ui.Ui;

import java.util.Random;

/**
 * Man tao nhan vat theo bo cuc mockup da duyet:
 * TRAI = nhan vat lon tren be vong sang trong pod kinh (nen than cay cyber) + pet robot.
 * PHAI = panel: toggle NAM/NU tren cung, 4 tab (Toc / Mat / Da / Trang phuc),
 * luoi 3 the lon cua tab dang chon (the chon co vien cyan + dau tick),
 * o nhap ten + nut xuc xac ngau nhien, nut XAC NHAN cam lon duoi cung.
 * Chi cham chon the - khong slider. KHONG chon class o day (chon sau o Tram Dinh Huong).
 */
public class CreateScreen implements Screen, InputProcessor {
    private static final String[] HAIR_STYLE_NAM = {"Rối Tự Nhiên", "Undercut + Băng Phát Sáng", "Xoăn Phồng + Mầm Cây"};
    private static final String[] HAIR_STYLE_NU = {"Tóc Hai Bên", "Dài Thẳng Hime", "Bob + Tai Nghe"};
    private static final String[] TAB_NAMES = {"Tóc", "Mắt", "Da", "Trang phục"};
    private static final String[] NAMES_NAM = {"Bin Mạch", "Kha Neon", "Rin", "Tí Hon", "Bảo Chip", "Đông", "Huy Sáng", "Phát", "Tèo", "Cu Mạch"};
    private static final String[] NAMES_NU = {"Mây", "Bơ Nhỏ", "Chip", "Linh Neon", "Na", "Hồng Mạch", "Tú", "Yến", "Mầm Xanh", "Suri"};

    private final NeoLangGame game;
    private final ExtendViewport vp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);
    private final Random rng = new Random();

    private String name = "";
    private Gender gender = Gender.NAM;
    private int tab = 0; // 0 Toc, 1 Mat, 2 Da, 3 Trang phuc
    private int suit = 0;
    private int hair = 0;
    private int hairStyle = 0; // NU mac dinh 0 = toc hai ben
    private int eye = 0;
    private int skin = 0;
    private int outfitIdx = 0;
    private boolean sheetOpen;
    private float time;
    private int shotFrames;

    public CreateScreen(NeoLangGame game) {
        this.game = game;
    }

    // ---------------------------------------------------------------- helpers

    private void border(float x, float y, float w, float h, Color c, float t) {
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(c);
        game.shapes.rect(x - t, y - t, w + 2 * t, t);
        game.shapes.rect(x - t, y + h, w + 2 * t, t);
        game.shapes.rect(x - t, y, t, h);
        game.shapes.rect(x + w, y, t, h);
        game.shapes.end();
    }

    /** Dau tick o goc the da chon: vong tron cyan + dau check trang ve bang 2 doan line. */
    private void tick(float cx, float cy) {
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(Ui.CYAN);
        game.shapes.circle(cx, cy, 13);
        game.shapes.end();
        Gdx.gl.glLineWidth(3);
        game.shapes.begin(ShapeType.Line);
        game.shapes.setColor(Color.WHITE);
        game.shapes.line(cx - 6, cy, cx - 1.5f, cy - 5);
        game.shapes.line(cx - 1.5f, cy - 5, cx + 7, cy + 6);
        game.shapes.end();
        Gdx.gl.glLineWidth(1);
    }

    private boolean card(float x, float y, float w, float h, boolean selected) {
        Ui.panel(game.shapes, x, y, w, h, selected
            ? new Color(0.06f, 0.20f, 0.24f, 0.97f) : new Color(0.05f, 0.10f, 0.15f, 0.93f));
        if (selected) {
            border(x, y, w, h, Ui.CYAN, 3);
            tick(x + w - 20, y + h - 20);
        }
        return Ui.tapped(vp, x, y, w, h);
    }

    private boolean swatch(float x, float y, float size, Color c, boolean selected) {
        game.shapes.begin(ShapeType.Filled);
        if (selected) {
            game.shapes.setColor(Ui.CYAN);
            game.shapes.rect(x - 3, y - 3, size + 6, size + 6);
        }
        game.shapes.setColor(c);
        game.shapes.rect(x, y, size, size);
        game.shapes.end();
        return Ui.tapped(vp, x, y, size, size);
    }

    /** Mat chibi don gian cho the Mat/Da: da tron, 2 mat long lanh, mieng cuoi. */
    private void drawFace(float cx, float cy, float r, Color skinC, int eyeHex) {
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(skinC);
        game.shapes.circle(cx, cy, r);
        game.shapes.setColor(1, 1, 1, 1);
        game.shapes.ellipse(cx - r * 0.52f, cy - r * 0.05f, r * 0.34f, r * 0.46f);
        game.shapes.ellipse(cx + r * 0.18f, cy - r * 0.05f, r * 0.34f, r * 0.46f);
        Color iris = Draw.rgb(eyeHex);
        game.shapes.setColor(iris);
        game.shapes.circle(cx - r * 0.35f, cy + r * 0.16f, r * 0.115f);
        game.shapes.circle(cx + r * 0.35f, cy + r * 0.16f, r * 0.115f);
        game.shapes.setColor(0.08f, 0.06f, 0.10f, 1);
        game.shapes.circle(cx - r * 0.35f, cy + r * 0.16f, r * 0.055f);
        game.shapes.circle(cx + r * 0.35f, cy + r * 0.16f, r * 0.055f);
        game.shapes.setColor(1, 1, 1, 1);
        game.shapes.circle(cx - r * 0.42f, cy + r * 0.26f, r * 0.045f);
        game.shapes.circle(cx + r * 0.28f, cy + r * 0.26f, r * 0.045f);
        game.shapes.setColor(1f, 0.55f, 0.55f, 0.8f);
        game.shapes.circle(cx - r * 0.62f, cy - r * 0.18f, r * 0.10f);
        game.shapes.circle(cx + r * 0.62f, cy - r * 0.18f, r * 0.10f);
        game.shapes.end();
        game.shapes.begin(ShapeType.Line);
        game.shapes.setColor(0.45f, 0.25f, 0.18f, 1);
        game.shapes.arc(cx, cy - r * 0.12f, r * 0.30f, 200, 140);
        game.shapes.end();
    }

    private int gi() { return gender == Gender.NU ? 1 : 0; }

    /** Painted preview figure for the current gender + outfit selection. */
    private Texture previewTexture() {
        int g = gi();
        Texture t = outfitIdx == 0 ? game.assets.heroFront[g] : game.assets.custFig[g][outfitIdx];
        if (t == null) t = game.assets.heroFront[g];
        if (t == null) t = game.assets.custFig[g][0];
        return t;
    }

    /** Draw a painted card crop centred at (cx, cy). Returns false when missing. */
    private boolean cardTex(Texture t, float cx, float cy, float h) {
        if (t == null) return false;
        float w = h * t.getWidth() / (float) t.getHeight();
        game.batch.begin();
        game.batch.draw(t, cx - w / 2, cy - h / 2, w, h);
        game.batch.end();
        return true;
    }

    private void randomize() {
        hairStyle = rng.nextInt(3);
        hair = rng.nextInt(Draw.HAIR.length);
        eye = rng.nextInt(3);
        skin = rng.nextInt(3);
        suit = rng.nextInt(Draw.SUIT.length);
        outfitIdx = rng.nextInt(3);
        String[] pool = gender == Gender.NAM ? NAMES_NAM : NAMES_NU;
        name = pool[rng.nextInt(pool.length)];
        game.sound.play("coin");
    }

    private void confirm() {
        String finalName = name.trim().isEmpty() ? "Tân Cư Dân" : name.trim();
        game.newGame(finalName, gender, suit, hair);
        game.state.player.hairStyleIdx = hairStyle;
        game.state.player.eyeColorIdx = eye;
        game.state.player.skinToneIdx = skin;
        game.state.player.outfitId = Draw.OUTFIT_IDS[outfitIdx];
        game.state.notice = "Chào mừng đến Neo Làng! Hãy gặp Trưởng Trạm Gốc ở giữa làng để nhận nhiệm vụ đầu tiên.";
        game.saveGame();
        game.sound.play("levelup");
        game.gotoTown();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(float dt) {
        time += dt;
        Gdx.gl.glClearColor(0.03f, 0.06f, 0.10f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        vp.apply();
        game.batch.setProjectionMatrix(vp.getCamera().combined);

        Draw.drawSky(game.shapes, 0, NeoLangGame.VW, 0.8f, false, time);
        Draw.drawTrunkWorld(game.shapes, 0, NeoLangGame.VW, 120, 0, time);

        if (Ui.button(game.batch, game.shapes, game.assets.fontSmall, vp, 24, 664, 110, 36, "Quay lại")) {
            game.sound.play("click");
            game.gotoTitle();
            return;
        }

        // ================= LEFT: glass pod + glowing platform + hero + pet =================
        float podX = 48, podY = 92, podW = 400, podH = 548;
        // pod frame + glass
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.10f, 0.22f, 0.28f, 0.95f);
        game.shapes.rect(podX - 8, podY - 8, podW + 16, podH + 16);
        game.shapes.setColor(0.55f, 0.85f, 0.92f, 0.20f);
        game.shapes.rect(podX, podY, podW, podH);
        game.shapes.setColor(1, 1, 1, 0.13f);
        game.shapes.triangle(podX + 26, podY + podH - 30, podX + 120, podY + podH - 30, podX + 26, podY + 210);
        game.shapes.triangle(podX + 140, podY + podH - 30, podX + 190, podY + podH - 30, podX + 60, podY + 300);
        game.shapes.end();
        border(podX - 8, podY - 8, podW + 16, podH + 16, new Color(0.25f, 0.95f, 1f, 0.85f), 3);
        // glowing circular platform
        float platCx = podX + podW / 2f, platCy = podY + 66;
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 2.4f);
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.20f, 0.90f, 1f, 0.25f * pulse);
        game.shapes.ellipse(platCx - 128, platCy - 24, 256, 48);
        game.shapes.setColor(0.30f, 0.95f, 1f, 0.55f * pulse);
        game.shapes.ellipse(platCx - 100, platCy - 17, 200, 34);
        game.shapes.setColor(0.75f, 0.98f, 1f, 0.9f);
        game.shapes.ellipse(platCx - 72, platCy - 11, 144, 22);
        game.shapes.end();
        // hero big + pet beside: PAINTED figure for the chosen gender + outfit
        // (starter outfit = hero-sheet front view; other outfits = sheet figures).
        // Hair/eye/skin are separate painted options shown on the cards themselves;
        // the preview figure is a fixed painting, so those picks are not composited.
        Texture prev = previewTexture();
        if (prev != null) {
            float ph = 300, pw2 = ph * prev.getWidth() / (float) prev.getHeight();
            float bob = (float) Math.sin(time * 2) * 2;
            game.batch.begin();
            game.batch.draw(prev, platCx - 18 - pw2 / 2, platCy + 4 + bob, pw2, ph);
            if (game.assets.petSpr != null) {
                Texture pt = game.assets.petSpr;
                float pth = 58, ptw = pth * pt.getWidth() / (float) pt.getHeight();
                game.batch.draw(pt, platCx + 108 - ptw / 2, platCy + bob * 0.6f, ptw, pth);
            }
            game.batch.end();
        } else {
            Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, platCx - 18, platCy + 6, 3.0f, gender,
                suit, hair, hairStyle, eye, skin, Draw.OUTFIT_IDS[outfitIdx], GameClass.NONE, 1, 0, time);
            Draw.drawPet(game.shapes, platCx + 108, platCy + 2, time, 1);
        }
        Ui.nameTag(game.batch, game.shapes, game.assets.font, name.isEmpty() ? "Tân Cư Dân" : name, platCx, podY + 16);
        if (Ui.button(game.batch, game.shapes, game.assets.fontSmall, vp, podX + 90, 40, 220, 38,
            sheetOpen ? "Đóng mẫu thiết kế" : "Xem mẫu thiết kế")) {
            sheetOpen = !sheetOpen;
            game.sound.play("click");
        }
        if (sheetOpen) {
            Texture sheet = gender == Gender.NAM ? game.assets.createMale : game.assets.createFemale;
            Ui.panel(game.shapes, 470, 300, 330, 210);
            if (sheet != null) {
                game.batch.begin();
                game.batch.draw(sheet, 478, 308, 314, 194);
                game.batch.end();
            }
        }

        // ================= RIGHT: panel =================
        float px = 500, pw = 736;
        Ui.panel(game.shapes, px, 60, pw, 580);
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontBig, "Tạo nhân vật", px + 28, 618, Ui.CYAN, Align.left);
        game.batch.end();

        // --- gender toggle on top ---
        float gx = px + pw - 316, gy = 580;
        border(gx, gy, 140, 44, gender == Gender.NAM ? Ui.CYAN : Ui.PANEL_EDGE, gender == Gender.NAM ? 3 : 1);
        if (Ui.button(game.batch, game.shapes, game.assets.font, vp, gx, gy, 140, 44, "NAM")) {
            gender = Gender.NAM;
            game.sound.play("click");
        }
        border(gx + 152, gy, 140, 44, gender == Gender.NU ? Ui.CYAN : Ui.PANEL_EDGE, gender == Gender.NU ? 3 : 1);
        if (Ui.button(game.batch, game.shapes, game.assets.font, vp, gx + 152, gy, 140, 44, "NỮ")) {
            gender = Gender.NU;
            game.sound.play("click");
        }

        // --- tabs ---
        float tabY = 516, tabW = (pw - 48 - 24) / 4f;
        for (int i = 0; i < 4; i++) {
            float tx = px + 24 + i * (tabW + 8);
            boolean sel = tab == i;
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(sel ? new Color(0.10f, 0.42f, 0.48f, 1) : new Color(0.07f, 0.16f, 0.20f, 1));
            game.shapes.rect(tx, tabY, tabW, 42);
            if (sel) {
                game.shapes.setColor(Ui.CYAN);
                game.shapes.rect(tx, tabY, tabW, 4);
            }
            game.shapes.end();
            game.batch.begin();
            Ui.label(game.batch, game.assets.font, TAB_NAMES[i], tx + tabW / 2f, tabY + 28,
                sel ? Ui.TEXT : Ui.TEXT_DIM, Align.center);
            game.batch.end();
            if (Ui.tapped(vp, tx, tabY, tabW, 42)) {
                tab = i;
                game.sound.play("click");
            }
        }

        // --- card grid (3 big cards) ---
        float cardY = 330, cardW = (pw - 48 - 28) / 3f, cardH = 168;
        String[] styles = gender == Gender.NAM ? HAIR_STYLE_NAM : HAIR_STYLE_NU;
        for (int i = 0; i < 3; i++) {
            float cx = px + 24 + i * (cardW + 14);
            boolean sel = (tab == 0 && hairStyle == i) || (tab == 1 && eye == i)
                || (tab == 2 && skin == i) || (tab == 3 && outfitIdx == i);
            boolean tappedCard = card(cx, cardY, cardW, cardH, sel);
            // card content: painted crops from the v2 customization sheets
            if (tab == 0) {
                if (!cardTex(game.assets.custHead[gi()][i], cx + cardW / 2f, cardY + 102, 106)) {
                    Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, cx + cardW / 2f, cardY + 42, 1.5f,
                        gender, suit, hair, i, eye, skin, Draw.OUTFIT_IDS[outfitIdx], GameClass.NONE, 1, 0, time);
                }
                game.batch.begin();
                Ui.label(game.batch, game.assets.fontSmall, styles[i], cx + cardW / 2f, cardY + 26, Ui.TEXT, Align.center);
                game.batch.end();
            } else if (tab == 1) {
                Texture et = game.assets.custEyes[gi()][i];
                float eh = et != null ? Math.min(112, (cardW - 20) * et.getHeight() / (float) et.getWidth()) : 0;
                if (et == null || !cardTex(et, cx + cardW / 2f, cardY + 100, eh)) {
                    drawFace(cx + cardW / 2f, cardY + 104, 44, Draw.rgb(Player.SKIN_COLORS[skin]), Draw.EYE_COLORS[i]);
                }
                game.batch.begin();
                Ui.label(game.batch, game.assets.fontSmall, Draw.EYE_NAMES[i], cx + cardW / 2f, cardY + 26, Ui.TEXT, Align.center);
                game.batch.end();
            } else if (tab == 2) {
                drawFace(cx + cardW / 2f, cardY + 104, 44, Draw.rgb(Player.SKIN_COLORS[i]), Draw.EYE_COLORS[eye]);
                game.batch.begin();
                Ui.label(game.batch, game.assets.fontSmall, Player.SKIN_NAMES[i], cx + cardW / 2f, cardY + 26, Ui.TEXT, Align.center);
                game.batch.end();
            } else {
                if (!cardTex(game.assets.custFig[gi()][i], cx + cardW / 2f, cardY + 102, 108)) {
                    Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, cx + cardW / 2f, cardY + 42, 1.5f,
                        gender, suit, hair, hairStyle, eye, skin, Draw.OUTFIT_IDS[i], GameClass.NONE, 1, 0, time);
                }
                game.batch.begin();
                Ui.label(game.batch, game.assets.fontSmall, Draw.OUTFIT_NAMES[i], cx + cardW / 2f, cardY + 26, Ui.TEXT, Align.center);
                game.batch.end();
            }
            if (tappedCard) {
                if (tab == 0) hairStyle = i;
                else if (tab == 1) eye = i;
                else if (tab == 2) skin = i;
                else outfitIdx = i;
                game.sound.play("click");
            }
        }

        // --- swatch row under cards: hair colors (tab Toc) / suit colors (tab Trang phuc) ---
        if (tab == 0 || tab == 3) {
            boolean hairRow = tab == 0;
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, hairRow ? "Màu tóc" : "Màu áo (cho Bộ Tân Cư Dân)",
                px + 24, 316, Ui.TEXT_DIM, Align.left);
            game.batch.end();
            int n = hairRow ? Draw.HAIR.length : Draw.SUIT.length;
            for (int i = 0; i < n; i++) {
                Color c = hairRow ? Draw.HAIR[i] : Draw.SUIT[i];
                boolean sel = hairRow ? hair == i : suit == i;
                if (swatch(px + 24 + i * 52, 262, 40, c, sel)) {
                    if (hairRow) hair = i; else suit = i;
                    game.sound.play("click");
                }
            }
        } else {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall,
                tab == 1 ? "Mắt chibi tròn, long lanh — chỉ đổi màu mắt." : "Chọn tông da cho nhân vật.",
                px + 24, 300, Ui.TEXT_DIM, Align.left);
            game.batch.end();
        }

        // --- name field + dice ---
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Tên cư dân", px + 24, 240, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        Ui.panel(game.shapes, px + 24, 186, pw - 48 - 70, 46, new Color(0.04f, 0.08f, 0.12f, 0.95f));
        game.batch.begin();
        boolean blink = (time % 1f) < 0.55f;
        String shown = name.isEmpty() ? "Tân Cư Dân" : name;
        Ui.label(game.batch, game.assets.font, shown + (blink && !name.isEmpty() ? "|" : ""),
            px + 40, 218, name.isEmpty() ? Ui.TEXT_DIM : Ui.TEXT, Align.left);
        game.batch.end();
        // dice button
        float diceX = px + pw - 24 - 56, diceY = 186;
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.10f, 0.34f, 0.40f, 1);
        game.shapes.rect(diceX, diceY, 56, 46);
        game.shapes.setColor(1, 1, 1, 1);
        float[][] pips = {{14, 32}, {42, 32}, {28, 23}, {14, 14}, {42, 14}};
        for (float[] pip : pips) game.shapes.circle(diceX + pip[0], diceY + pip[1], 3.4f);
        game.shapes.end();
        border(diceX, diceY, 56, 46, Ui.PANEL_EDGE, 2);
        if (Ui.tapped(vp, diceX, diceY, 56, 46)) randomize();

        // --- big orange CONFIRM ---
        float cfY = 92, cfH = 70;
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(new Color(1f, 0.55f, 0.12f, 1));
        game.shapes.rect(px + 24, cfY, pw - 48, cfH);
        game.shapes.setColor(new Color(1f, 0.68f, 0.25f, 1));
        game.shapes.rect(px + 24, cfY + cfH - 10, pw - 48, 10);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontBig, "XÁC NHẬN", px + pw / 2f, cfY + 46, Color.WHITE, Align.center);
        game.batch.end();
        if (Ui.tapped(vp, px + 24, cfY, pw - 48, cfH)) {
            confirm();
            return;
        }

        // --- screenshot driver: capture the create screen, then auto-confirm ---
        if (game.screenshotDir != null) {
            shotFrames++;
            if (shotFrames == 90) {
                game.captureShot("create.png");
                name = "BeMuse";
                gender = Gender.NAM;
                eye = 1;
                confirm();
            }
        }
    }

    // ---- name typing ----
    @Override public boolean keyTyped(char c) {
        if (c >= 32 && c != 127 && name.length() < 18) name += c;
        return true;
    }

    @Override public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.BACKSPACE && !name.isEmpty()) {
            name = name.substring(0, name.length() - 1);
            return true;
        }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean touchDown(int x, int y, int p, int b) { return false; }
    @Override public boolean touchUp(int x, int y, int p, int b) { return false; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { return false; }
    @Override public boolean touchDragged(int x, int y, int p) { return false; }
    @Override public boolean mouseMoved(int x, int y) { return false; }
    @Override public boolean scrolled(float ax, float ay) { return false; }

    @Override public void show() {
        vp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        Gdx.input.setInputProcessor(this);
    }

    @Override public void hide() { Gdx.input.setInputProcessor(null); }
    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void dispose() { }
}
