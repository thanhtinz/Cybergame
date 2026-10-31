package com.avatardk.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.avatardk.game.ui.AvatarDkGame;
import com.avatardk.game.ui.CharacterZoomScreen;
import com.avatardk.game.ui.WorldScreen;
import java.io.File;

/** Công cụ kiểm chứng hình ảnh đợt 1: chụp (1) map thành phố 10 có người chơi Sơn +
 *  NPC + bong bóng chat, (2) zoom nhân vật lắp từ part so với ảnh đối chiếu gốc,
 *  (3) map nông trại 25. Chạy: xvfb-run -a gradle --offline :desktop:shot */
public class ScreenshotMain {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("AvatarDK Screenshot");
        cfg.setWindowedMode(1280, 720);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new ApplicationAdapter() {
            AvatarDkGame game;
            int stage = -1, frame;
            WorldScreen world;
            @Override public void create() {
                game = new AvatarDkGame();
                game.create();
                world = game.world;
                world.debugSetPlayer(455f, 95f); // đứng gần NPC đấu giá (410,48) để nút Tương tác sáng
                world.debugChat("Xin chào, AvatarDK đã chạy bằng Java!");
            }
            @Override public void resize(int width, int height) {
                if (game != null) game.resize(width, height);
            }
            @Override public void render() {
                frame++;
                float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
                game.getScreen().render(dt);
                switch (stage) {
                    case -1:
                        if (frame == 25) {
                            shot("shot-login.png");
                            game.setScreen(world);
                            stage = 0; frame = 0;
                        }
                        break;
                    case 0:
                        if (frame == 20) world.debugInteract(); // NPC gần nhất cất lời, bong bóng hiện ở frame chụp
                        if (frame == 29) System.out.println("DEBUG WORLD: " + world.debugInfo());
                        if (frame == 30) {
                            if (world.npcCount() < 1) throw new IllegalStateException("Map 10 không có NPC nào");
                            shot("shot-city.png");
                            game.setScreen(new CharacterZoomScreen(game));
                            stage++; frame = 0;
                        }
                        break;
                    case 1:
                        if (frame == 25) {
                            shot("shot-character.png");
                            world.debugSetMap(25);
                            world.debugSetPlayer(120f, 150f);
                            game.setScreen(world);
                            stage++; frame = 0;
                        }
                        break;
                    case 2:
                        if (frame == 25) {
                            shot("shot-farm.png");
                            world.debugSetMap(10);
                            world.debugSetPlayer(455f, 95f);
                            world.debugOpenMenu();
                            game.setScreen(world);
                            stage++; frame = 0;
                        }
                        break;
                    case 3:
                        if (frame == 25) {
                            shot("shot-hub-menu.png");
                            world.debugCloseMenu();
                            world.debugChatTab(0); // tab Hệ thống
                            stage++; frame = 0;
                        }
                        break;
                    case 4:
                        if (frame == 25) {
                            shot("shot-chat-system.png");
                            Gdx.app.exit();
                        }
                        break;
                }
            }
            private void shot(String name) {
                // Bắt frame bằng glReadPixels trực tiếp: ScreenUtils.getFrameBufferPixmap
                // trả về toàn màu clear trong môi trường LWJGL3 + Xvfb này (đã kiểm chứng:
                // cùng một frame, glReadPixels đọc được nội dung thật, ScreenUtils thì không).
                int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
                java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(w * h * 4);
                Gdx.gl.glReadPixels(0, 0, w, h, com.badlogic.gdx.graphics.GL20.GL_RGBA,
                    com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE, buf);
                Pixmap flipped = new Pixmap(w, h, Pixmap.Format.RGBA8888);
                java.nio.ByteBuffer dst = flipped.getPixels();
                int rowBytes = w * 4;
                byte[] row = new byte[rowBytes];
                for (int y = 0; y < h; y++) {
                    buf.position((h - 1 - y) * rowBytes);
                    buf.get(row);
                    dst.put(row);
                }
                dst.rewind();
                com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(new File(name).getAbsolutePath()), flipped);
                flipped.dispose();
                System.out.println("Đã chụp " + name);
            }
            @Override public void dispose() { if (game != null) game.dispose(); }
        }, cfg);
    }
}
