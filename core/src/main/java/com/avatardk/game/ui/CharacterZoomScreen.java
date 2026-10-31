package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.data.PaperDoll;

/** Màn zoom nhân vật Sơn lắp từ part gốc (dùng cho kiểm chứng bằng mắt + sau này là
 *  màn xem nhân vật). Vẽ ở unit = 12 pixel / đơn vị medium (atlas hd phóng 6 lần,
 *  lọc Nearest nên vẫn nét pixel). Bên phải là ảnh đối chiếu gốc pack5/chars/son.png. */
public class CharacterZoomScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);
    private final GlyphLayout layout = new GlyphLayout();
    private final PaperDoll doll;

    public CharacterZoomScreen(AvatarDkGame game) {
        this.game = game;
        this.doll = game.newDoll(AvatarDkGame.SON_PARTS);
    }

    @Override
    public void render(float dt) {
        Gdx.gl.glClearColor(0.07f, 0.09f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        // nhân vật lắp bằng Java (trái)
        game.dollRenderer.draw(batch, doll, 0, 330f, 210f, 12f, false);
        Ui.fontBig.setColor(Color.WHITE);
        layout.setText(Ui.fontBig, "Lắp bằng Java (đợt 1)");
        Ui.fontBig.draw(batch, "Lắp bằng Java (đợt 1)", 330f - layout.width / 2f, 620f);
        // ảnh đối chiếu gốc (phải) — son.png 44x64 (medium x2), phóng 6 lần
        com.badlogic.gdx.graphics.Texture ref = game.texture("reference/son.png");
        batch.draw(ref, 830f - ref.getWidth() * 3f, 210f, ref.getWidth() * 6f, ref.getHeight() * 6f);
        layout.setText(Ui.fontBig, "Đối chiếu gốc (chars/son.png)");
        Ui.fontBig.draw(batch, "Đối chiếu gốc (chars/son.png)", 830f - layout.width / 2f, 620f);
        // thông tin part
        Ui.fontSmall.setColor(1f, 0.95f, 0.7f, 1f);
        StringBuilder sb = new StringBuilder("Part: ");
        for (com.avatardk.game.data.PartData p : doll.parts()) {
            sb.append(p.id).append(" (z").append(p.zorder).append(")  ");
        }
        layout.setText(Ui.fontSmall, sb.toString());
        Ui.fontSmall.draw(batch, sb.toString(), Ui.W / 2f - layout.width / 2f, 130f);
        batch.end();
    }

    @Override
    public void dispose() { batch.dispose(); }
}
