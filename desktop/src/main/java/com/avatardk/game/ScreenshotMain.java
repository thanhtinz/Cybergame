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
    private static boolean tourMode = false;

    public static void main(String[] args) {
        tourMode = args.length > 0 && args[0].equals("tour");
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("AvatarDK Screenshot");
        cfg.setWindowedMode(1280, 720);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new ApplicationAdapter() {
            AvatarDkGame game;
            int stage = -1, frame;
            WorldScreen world;
            java.util.List<Integer> tourIds;
            int tourIdx = 0;
            @Override public void create() {
                game = new AvatarDkGame();
                game.create();
                world = game.world;
                if (tourMode) {
                    game.setScreen(world);
                    tourIds = world.mapIds();
                    world.debugSetMap(tourIds.get(0));
                } else {
                    tourIds = java.util.List.of();
                    world.debugSetPlayer(455f, 95f); // đứng gần NPC đấu giá (410,48) để nút Tương tác sáng
                    world.debugChat("Xin chào, Thị Trấn Mặt Trời đã chạy bằng Java!");
                }
            }
            @Override public void resize(int width, int height) {
                if (game != null) game.resize(width, height);
            }
            @Override public void render() {
                frame++;
                float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
                game.getScreen().render(dt);
                if (tourMode) {
                    if (frame == 22) {
                        shot("tour/map-" + world.currentMapId() + ".png");
                        tourIdx++;
                        if (tourIdx >= tourIds.size()) { Gdx.app.exit(); return; }
                        world.debugSetMap(tourIds.get(tourIdx));
                        frame = 0;
                    }
                    return;
                }
                switch (stage) {
                    case -1:
                        if (frame == 25) {
                            shot("shot-banner.png");
                            game.setScreen(game.login);
                            stage = -2; frame = 0;
                        }
                        break;
                    case -2:
                        if (frame == 25) {
                            shot("shot-login.png");
                            game.playerName = "Tín";
                            game.setScreen(game.serverSelect);
                            stage = -3; frame = 0;
                        }
                        break;
                    case -3:
                        if (frame == 25) {
                            shot("shot-server.png");
                            game.serverSelect.debugOpenPanel();
                            stage = -4; frame = 0;
                        }
                        break;
                    case -4:
                        if (frame == 25) {
                            shot("shot-server-panel.png");
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
                            world.debugChatTab(2);
                            world.debugSetMap(11);           // Khu lễ đường — Chủ Hôn
                            world.debugSetPlayer(300f, 95f);
                            stage++; frame = 0;
                        }
                        break;
                    case 5:
                        if (frame == 25) {
                            shot("shot-map11.png");
                            world.debugSetMap(12);           // Bến xe công viên — Tài Xỉu, vé số
                            world.debugSetPlayer(115f, 80f);
                            stage++; frame = 0;
                        }
                        break;
                    case 6:
                        if (frame == 25) {
                            shot("shot-map12.png");
                            world.debugSetMap(26);           // đường ra nông trại — lái buôn
                            world.debugSetPlayer(105f, 75f);
                            stage++; frame = 0;
                        }
                        break;
                    case 7:
                        if (frame == 25) {
                            shot("shot-map26.png");
                            world.debugSetMap(108);          // Trường đua thú (Hmap 16) — dùng điểm spawn của map
                            stage++; frame = 0;
                        }
                        break;
                    case 8:
                        if (frame == 25) {
                            shot("shot-map108.png");
                            world.debugSetMap(1);            // Khu Mặt trời — Bạch/Hắc vô thường
                            world.debugSetPlayer(90f, 95f);
                            stage++; frame = 0;
                        }
                        break;
                    case 9:
                        if (frame == 25) {
                            shot("shot-map1.png");
                            world.debugSetMap(10);
                            world.debugSetPlayer(455f, 95f);
                            world.debugOpenMenu();
                            stage++; frame = 0;
                        }
                        break;
                    case 10:
                        if (frame == 25) {
                            shot("shot-menu-grid.png");
                            world.debugCloseMenu();
                            world.debugOpenCityMap();
                            stage++; frame = 0;
                        }
                        break;
                    case 11:
                        if (frame == 25) {
                            shot("shot-citymap.png");
                            world.debugCloseCityMap();
                            world.applyFeel(10);        // mặt cười to
                            world.debugOpenFeel();
                            stage++; frame = 0;
                        }
                        break;
                    case 12:
                        if (frame == 25) {
                            shot("shot-feel.png");
                            // Sân khấu online giả (đợt 2d): bơm gói 51/55 qua đúng
                            // đường xử lý thật của WorldScreen để kiểm chứng hình
                            // người chơi khác + bong bóng chat của họ.
                            com.avatardk.game.net.PlayerInfo fake =
                                new com.avatardk.game.net.PlayerInfo();
                            fake.id = 999;
                            fake.username = "Sơn";
                            game.player = fake;
                            world.debugSetMap(10);
                            world.debugSetPlayer(455f, 95f);
                            world.debugChatTab(2);
                            try {
                                int[][] npcParts = new int[2][];
                                for (int i = 0; i < 2; i++) {
                                    java.util.List<Integer> items = game.data.npcs.get(i + 3).items;
                                    npcParts[i] = items.stream().mapToInt(Integer::intValue).toArray();
                                }
                                world.debugInjectNet((byte) 51, joinPayload(7, "Linh", npcParts[0], 395, 100));
                                world.debugInjectNet((byte) 51, joinPayload(8, "Tuấn", npcParts[1], 525, 88));
                                world.debugInjectNet((byte) 55, chatPayload(7, "Chào Sơn! Map đông vui quá nè"));
                            } catch (java.io.IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                            stage++; frame = 0;
                        }
                        break;
                    case 13:
                        if (frame == 25) {
                            System.out.println("ONLINE STAGE: remotes=" + world.remoteCount());
                            if (world.remoteCount() != 2)
                                throw new IllegalStateException("Kỳ vọng 2 người chơi khác, thấy " + world.remoteCount());
                            shot("shot-online.png");
                            Gdx.app.exit();
                        }
                        break;
                }
            }
            /** Thân gói 51 PLAYER_JOIN_PARK theo docs/park-spec.md. */
            private byte[] joinPayload(int id, String name, int[] wearing, int x, int y)
                throws java.io.IOException {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream w = new java.io.DataOutputStream(bos);
                w.writeInt(id);
                w.writeUTF(name);
                w.writeByte(wearing.length);
                for (int p : wearing) w.writeShort(p);
                w.writeShort(x);
                w.writeShort(y);
                w.writeByte(1);   // role: người chơi thật
                w.writeByte(-1);
                w.writeShort(-1);
                w.writeShort(-1);
                w.flush();
                return bos.toByteArray();
            }

            /** Thân gói 55 CHAT_PARK theo docs/park-spec.md. */
            private byte[] chatPayload(int id, String text) throws java.io.IOException {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream w = new java.io.DataOutputStream(bos);
                w.writeInt(id);
                w.writeUTF(text);
                w.flush();
                return bos.toByteArray();
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
                File out = new File(name);
                if (out.getParentFile() != null) out.getParentFile().mkdirs();
                com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(out.getAbsolutePath()), flipped);
                flipped.dispose();
                System.out.println("Đã chụp " + name);
            }
            @Override public void dispose() { if (game != null) game.dispose(); }
        }, cfg);
    }
}
