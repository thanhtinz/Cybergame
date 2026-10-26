package com.neolang.cyber.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.ui.Draw;
import com.neolang.cyber.ui.Ui;

public class TitleScreen implements Screen {
    private final NeoLangGame game;
    private final ExtendViewport vp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);
    private float time;
    private boolean settingsOpen;
    private int shotFrames;

    public TitleScreen(NeoLangGame game) {
        this.game = game;
    }

    @Override
    public void render(float dt) {
        time += dt;
        Gdx.gl.glClearColor(0.20f, 0.14f, 0.08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        vp.apply();
        game.batch.setProjectionMatrix(vp.getCamera().combined);

        // background art + soft dark veil
        game.batch.begin();
        if (game.assets.keyartWide != null) {
            game.batch.draw(game.assets.keyartWide, 0, 0, NeoLangGame.VW, NeoLangGame.VH);
        } else if (game.assets.town != null) {
            game.batch.draw(game.assets.town, 0, 0, NeoLangGame.VW, NeoLangGame.VH);
        }
        game.batch.end();
        if (game.assets.keyartWide == null && game.assets.town == null) {
            Draw.drawSky(game.shapes, 0, NeoLangGame.VW, 0.85f, false, time);
            Draw.drawTrunkWorld(game.shapes, 0, NeoLangGame.VW, 170, 0, time);
        }
        game.shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        game.shapes.setColor(0.15f, 0.10f, 0.05f, 0.28f);
        game.shapes.rect(0, 0, NeoLangGame.VW, NeoLangGame.VH);
        game.shapes.end();

        // hero duo flanking the logo
        Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, 235, 120, 2.5f, Gender.NAM,
            0, 0, 0, 1, 1, "bo_tan_cu_dan", GameClass.NONE, 1, 0, time);
        Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, 1045, 120, 2.5f, Gender.NU,
            0, 3, 0, 0, 0, "bo_tan_cu_dan", GameClass.NONE, -1, 0, time);
        Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, "Cư Dân Mới", 235, 330);
        Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, "Cư Dân Mới", 1045, 330);

        // cream sign board behind the title: the key art's canopy is dark, so
        // the brown title text needs the pale board to stay readable (COZY).
        Ui.panel(game.shapes, NeoLangGame.VW / 2f - 265, 552, 530, 118);
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontBig, "NEO LÀNG", NeoLangGame.VW / 2f, 640,
            new Color(0.35f, 0.22f, 0.12f, 1), Align.center);
        Ui.label(game.batch, game.assets.font, "Cyber Grove — Làng trên Cây Mẹ", NeoLangGame.VW / 2f, 585,
            new Color(0.55f, 0.35f, 0.15f, 1), Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Game 2D chibi — Java + LibGDX", NeoLangGame.VW / 2f, 42,
            Ui.TEXT_DIM, Align.center);
        game.batch.end();

        float bx = NeoLangGame.VW / 2f - 130;
        if (Ui.button(game.batch, game.shapes, game.assets.font, vp, bx, 452, 260, 52, "Chơi mới")) {
            game.sound.play("click");
            game.startCreate();
            return;
        }
        if (game.state != null) {
            if (Ui.button(game.batch, game.shapes, game.assets.font, vp, bx, 388, 260, 52,
                "Chơi tiếp: " + game.state.player.name)) {
                game.sound.play("click");
                game.gotoTown();
                return;
            }
        }
        if (Ui.button(game.batch, game.shapes, game.assets.font, vp, bx, 324, 260, 52, "Cài đặt")) {
            game.sound.play("click");
            settingsOpen = !settingsOpen;
        }
        if (settingsOpen) {
            if (game.state == null) {
                Ui.panel(game.shapes, NeoLangGame.VW / 2f - 170, 96, 340, 84);
                game.batch.begin();
                Ui.label(game.batch, game.assets.fontSmall, "Hãy tạo nhân vật trước, cài đặt sẽ mở trong game.",
                    NeoLangGame.VW / 2f, 132, Ui.TEXT_DIM, Align.center);
                game.batch.end();
            } else {
                Ui.panel(game.shapes, NeoLangGame.VW / 2f - 170, 60, 340, 226);
                game.batch.begin();
                Ui.label(game.batch, game.assets.font, "Cài đặt", NeoLangGame.VW / 2f, 262, Ui.CYAN, Align.center);
                game.batch.end();
                Ui.drawSettings(game, vp, NeoLangGame.VW / 2f - 150, 196, 300);
            }
        }

        if (game.screenshotDir != null) {
            shotFrames++;
            if (shotFrames == 90) {
                game.captureShot("title.png");
                // Flow qua man tao nhan vat (no se tu chup create.png roi XAC NHAN vao lang)
                game.startCreate();
            }
        }
    }

    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void show() { vp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true); }
    @Override public void hide() { }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void dispose() { }
}
