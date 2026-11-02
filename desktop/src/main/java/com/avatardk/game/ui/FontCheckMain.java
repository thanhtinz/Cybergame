package com.avatardk.game.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.File;

/** So sánh trực tiếp font FreeType (bản cài) với font .fnt đã nướng
 *  (bản web): cùng chuỗi, cùng toạ độ vẽ, kẻ đường tham chiếu đỏ tại
 *  đúng y truyền vào draw() — lệch mực giữa hai bản là thấy ngay. */
public class FontCheckMain {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Font Check");
        cfg.setWindowedMode(900, 460);
        cfg.setForegroundFPS(10);
        new Lwjgl3Application(new ApplicationAdapter() {
            private SpriteBatch batch;
            private ShapeRenderer shapes;
            private int frames;

            @Override
            public void create() {
                batch = new SpriteBatch();
                shapes = new ShapeRenderer();
            }

            @Override
            public void render() {
                ScreenUtils.clear(0.08f, 0.1f, 0.16f, 1f);
                BitmapFont ftBaloo = FontFactory.create("fonts/Baloo2-ExtraBold.ttf", 36, Ui.CHARS);
                BitmapFont bkBaloo = new BitmapFont(Gdx.files.internal("fonts-baked/baloo-36.fnt"));
                BitmapFont ftSmall = FontFactory.create("fonts/Nunito-Bold.ttf", 19, Ui.CHARS);
                BitmapFont bkSmall = new BitmapFont(Gdx.files.internal("fonts-baked/nunito-bold-19.fnt"));
                shapes.begin(ShapeRenderer.ShapeType.Line);
                shapes.setColor(Color.RED);
                for (int y : new int[] {400, 330, 240, 170}) shapes.line(20, y, 880, y);
                shapes.end();
                batch.begin();
                ftBaloo.setColor(Color.WHITE);
                bkBaloo.setColor(Color.YELLOW);
                ftSmall.setColor(Color.WHITE);
                bkSmall.setColor(Color.YELLOW);
                ftBaloo.draw(batch, "Thị Trấn Mặt Trời", 30, 400);
                bkBaloo.draw(batch, "Thị Trấn Mặt Trời", 470, 400);
                ftSmall.draw(batch, "Chào mừng bạn đến với thị trấn", 30, 330);
                bkSmall.draw(batch, "Chào mừng bạn đến với thị trấn", 470, 330);
                ftBaloo.draw(batch, "Gặp gỡ · Kết bạn · Vui cả ngày", 30, 240);
                bkBaloo.draw(batch, "Gặp gỡ · Kết bạn · Vui cả ngày", 470, 240);
                ftSmall.draw(batch, "Nhập tài khoản 1234567890", 30, 170);
                bkSmall.draw(batch, "Nhập tài khoản 1234567890", 470, 170);
                batch.end();
                com.badlogic.gdx.graphics.g2d.GlyphLayout layFt = new com.badlogic.gdx.graphics.g2d.GlyphLayout(ftBaloo, "Thị");
                com.badlogic.gdx.graphics.g2d.GlyphLayout layBk = new com.badlogic.gdx.graphics.g2d.GlyphLayout(bkBaloo, "Thị");
                System.out.println("LAYOUT ft runs=" + layFt.runs.size + " y=" + (layFt.runs.size > 0 ? layFt.runs.get(0).y : -999)
                    + " h=" + layFt.height + " | bk runs=" + layBk.runs.size + " y=" + (layBk.runs.size > 0 ? layBk.runs.get(0).y : -999)
                    + " h=" + layBk.height);
                for (char c : new char[] {'G', 'ặ', 'p', 'Đ', 'à'}) {
                    com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph gf = ftBaloo.getData().getGlyph(c);
                    com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph gb = bkBaloo.getData().getGlyph(c);
                    System.out.println("GLYPH " + (int) c + " ft[yoff=" + gf.yoffset + " h=" + gf.height + " srcY=" + gf.srcY
                        + "] bk[yoff=" + gb.yoffset + " h=" + gb.height + " srcY=" + gb.srcY + "]");
                }
                System.out.println("ftBaloo cap=" + ftBaloo.getCapHeight() + " line=" + ftBaloo.getLineHeight()
                    + " | bkBaloo cap=" + bkBaloo.getCapHeight() + " line=" + bkBaloo.getLineHeight());
                System.out.println("ftSmall cap=" + ftSmall.getCapHeight() + " line=" + ftSmall.getLineHeight()
                    + " | bkSmall cap=" + bkSmall.getCapHeight() + " line=" + bkSmall.getLineHeight());
                if (++frames == 5) {
                    Pixmap pm = Pixmap.createFromFrameBuffer(0, 0,
                        Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                    PixmapIO.writePNG(Gdx.files.absolute(
                        new File("font-check.png").getAbsolutePath()), pm);
                    System.out.println("FONT CHECK DONE");
                    Gdx.app.exit();
                }
            }
        }, cfg);
    }
}
