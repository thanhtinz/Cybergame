package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Màn banner mở đầu của Thị Trấn Mặt Trời (mockup v3 đã duyệt): tranh key art
 * vẽ riêng toàn màn, logo ở trên, 3 nút dọc góc phải (Thông báo / Xoá cache /
 * Hỗ trợ), nút cam "CHẠM ĐỂ BẮT ĐẦU" ở dưới. Chạm nút (hoặc nền) → màn đăng nhập.
 */
public class BannerScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);

    private static final float BTN_W = 352f, BTN_H = 52f;
    private static final float BTN_X = Ui.W / 2f - BTN_W / 2f, BTN_Y = 44f;

    public BannerScreen(AvatarDkGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector3 v = viewport.unproject(new Vector3(sx, sy, 0));
                onTouch(v.x, v.y);
                return true;
            }
        });
    }

    private void onTouch(float x, float y) {
        if (EntryUi.infoTouch(x, y)) return;
        int util = EntryUi.utilHit(x, y);
        if (util >= 0) {
            EntryUi.onUtil(game, util);
            return;
        }
        game.setScreen(game.login);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.2f, 0.35f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        EntryUi.drawBanner(batch);
        EntryUi.drawHeader(batch);
        batch.draw(Ui.gradientRect((int) BTN_W, (int) BTN_H, 26,
            EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), BTN_X, BTN_Y);
        Ui.centered(batch, Ui.font, "CHẠM ĐỂ BẮT ĐẦU", Ui.W / 2f, BTN_Y + 34, Color.WHITE);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
        Ui.fontSmall.draw(batch, EntryUi.VERSION, 18, 26);
        EntryUi.drawInfoPanel(batch);
        EntryUi.drawToast(batch);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
