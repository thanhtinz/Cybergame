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
        System.setProperty("avatardk.dataUrl", "off");
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
                            world.debugBusSkip(); // sân khấu tĩnh: không chạy sự kiện xe buýt
                            stage = 0; frame = 0;
                        }
                        break;
                    case 0:
                        if (frame == 20) world.debugInteract(); // NPC gần nhất cất lời, bong bóng hiện ở frame chụp
                        if (frame == 29) System.out.println("DEBUG WORLD: " + world.debugInfo()
                            + " feelIds=" + world.debugFeelIds());
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
                            System.out.println("FEEL STAGE: tong bieu cam = " + world.feelCount());
                            world.debugExpandFeel(150f); // nấc mở rộng + đã cuộn xuống
                            stage = 125; frame = 0;
                        }
                        break;
                    case 125:
                        if (frame == 25) {
                            shot("shot-feel-big.png");
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
                            stage = 13; frame = 0;
                        }
                        break;
                    case 13:
                        if (frame == 25) {
                            System.out.println("ONLINE STAGE: remotes=" + world.remoteCount());
                            if (world.remoteCount() != 2)
                                throw new IllegalStateException("Kỳ vọng 2 người chơi khác, thấy " + world.remoteCount());
                            shot("shot-online.png");
                            // Sân khấu xe buýt: map 24 có trạm (ô 139), xe đang vào
                            world.debugSetMap(24);
                            world.debugCloseFeel();
                            world.debugClearRemotes();
                            world.debugBusArrive();
                            stage = 14; frame = 0;
                        }
                        break;
                    case 14:
                        if (frame == 25) {
                            shot("shot-bus-arrive.png");
                            world.debugBusDwell();
                            // lùi người chơi lên vỉa hè trên để không bị khung chat che trong ảnh kiểm
                            world.debugSetPlayer(684f, 110f);
                            stage = 15; frame = 0;
                        }
                        break;
                    case 15:
                        if (frame == 25) {
                            shot("shot-bus-stop.png");
                            // Sân khấu lớp vật thể: map 12 nhiều vật thể nhất (13)
                            world.debugSetMap(12);
                            world.debugSetPlayer(560f, 150f);
                            stage = 16; frame = 0;
                        }
                        break;
                    case 16:
                        if (frame == 25) {
                            shot("shot-objects.png");
                            // Kiểm va chạm + cảnh ban ngày ở map 24
                            world.debugSetMap(24);
                            world.debugForceDay();
                            System.out.println("COLLIDE bien(636,132) blocked="
                                + !world.debugCanStand(636f, 132f)
                                + " truoc-bien(636,156) free=" + world.debugCanStand(636f, 156f)
                                + " den(12,55) blocked=" + !world.debugCanStand(12f, 55f)
                                + " duong(684,175) blocked=" + !world.debugCanStand(684f, 175f)
                                + " mat-tien-shop(60,36) blocked=" + !world.debugCanStand(60f, 36f)
                                + " loi-giua-nha(156,36) free=" + world.debugCanStand(156f, 36f));
                            world.debugBusDwell();
                            stage = 17; frame = 0;
                        }
                        break;
                    case 17:
                        if (frame == 25) {
                            shot("shot-day-bus.png");
                            world.debugSetMap(12);
                            world.debugForceDay();
                            world.debugSetPlayer(560f, 128f);
                            stage = 18; frame = 0;
                        }
                        break;
                    case 18:
                        if (frame == 25) {
                            shot("shot-day-park.png");
                            // Panel chọn KHU ở map 24 (khu 3 đầy, 1/8 đông)
                            world.debugSetMap(24);
                            world.debugForceDay();
                            world.debugOpenBoard(new int[]{2, 1, 2, 0, 2, 2, 2, 2, 1, 2}, 0);
                            stage = 19; frame = 0;
                        }
                        break;
                    case 19:
                        if (frame == 25) {
                            shot("shot-board.png");
                            // Kiểm panel chat: nội dung cách ô nhập + nút phóng to
                            world.debugCloseBoard();
                            world.debugChatDemo();
                            stage = 20; frame = 0;
                        }
                        break;
                    case 20:
                        if (frame == 25) {
                            shot("shot-chat.png");
                            world.debugToggleChatBig();
                            stage = 21; frame = 0;
                        }
                        break;
                    case 21:
                        if (frame == 40) {
                            shot("shot-chat-big.png");
                            // Kiểm đồng bộ biểu cảm (gói 57): Linh (id 7) đang
                            // ở map 10 nhận mặt 107 — so với shot-online (mặt
                            // thường) phải thấy khác rõ ở ảnh feel-sync.
                            world.debugToggleChatBig();
                            world.debugSetMap(10);
                            world.debugBusSkip();
                            world.debugSetPlayer(455f, 95f);
                            try {
                                java.util.List<Integer> items = game.data.npcs.get(3).items;
                                int[] linhParts = items.stream().mapToInt(Integer::intValue).toArray();
                                world.debugClearRemotes();
                                world.debugInjectNet((byte) 51, joinPayload(7, "Linh", linhParts, 395, 100));
                                world.debugInjectNet((byte) 57, feelPayload(7, 107 + 100));
                            } catch (java.io.IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                            stage = 22; frame = 0;
                        }
                        break;
                    case 22:
                        if (frame == 25) {
                            shot("shot-feel-sync.png");
                            // Kiểm ô emoji của khung chat (mở popover)
                            world.debugOpenEmoji();
                            stage = 23; frame = 0;
                        }
                        break;
                    case 23:
                        if (frame == 25) {
                            shot("shot-chat-emoji.png");
                            // Kiểm tin nhắn thoại: chip nghe lại trong nhật ký
                            world.debugCloseEmoji();
                            world.debugVoiceDemo();
                            stage = 24; frame = 0;
                        }
                        break;
                    case 24:
                        if (frame == 25) {
                            shot("shot-chat-voice.png");
                            // Sân khấu panel tương tác người chơi khác: Linh +
                            // Tuấn đứng cạnh Sơn ở map 10, chạm vào Linh để
                            // mở panel kính của cô ấy
                            world.debugSetMap(10);
                            world.debugSetPlayer(455f, 85f);
                            world.debugClearRemotes();
                            try {
                                int[][] npcParts = new int[2][];
                                for (int i = 0; i < 2; i++) {
                                    java.util.List<Integer> items = game.data.npcs.get(i + 3).items;
                                    npcParts[i] = items.stream().mapToInt(Integer::intValue).toArray();
                                }
                                world.debugInjectNet((byte) 51, joinPayload(7, "Linh", npcParts[0], 395, 88));
                                world.debugInjectNet((byte) 51, joinPayload(8, "Tuấn", npcParts[1], 525, 80));
                            } catch (java.io.IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                            world.debugOpenPlayerPanel();
                            stage = 25; frame = 0;
                        }
                        break;
                    case 25:
                        if (frame == 20) {
                            System.out.println("PLAYER PANEL STAGE: panelId=" + world.debugPanelPlayerId());
                            if (world.debugPanelPlayerId() != 7)
                                throw new IllegalStateException("Panel không mở đúng người chơi Linh (7)");
                            shot("shot-player-panel.png");
                            // mở hàng con Tương tác + bơm gói 59 Hôn vào Linh
                            world.debugPanelSub(true);
                            try {
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                java.io.DataOutputStream w = new java.io.DataOutputStream(bos);
                                w.writeInt(999); w.writeInt(7); w.writeShort(101); w.writeShort(5);
                                w.flush();
                                world.debugInjectNet((byte) 59, bos.toByteArray());
                            } catch (java.io.IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                            stage = 26; frame = 0;
                        }
                        break;
                    case 26:
                        if (frame == 6) {
                            // bơm gói −22: thẻ thông tin của Linh hiện trên panel
                            try {
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                java.io.DataOutputStream w = new java.io.DataOutputStream(bos);
                                w.writeInt(7);
                                w.writeByte(12); w.writeByte(40); w.writeByte(80);
                                w.writeByte(0); w.writeByte(65); w.writeByte(90); w.writeByte(55);
                                w.flush();
                                world.debugInjectNet((byte) -22, bos.toByteArray());
                            } catch (java.io.IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                        }
                        if (frame == 25) {
                            shot("shot-player-panel-sub.png");
                            // Sân khấu panel nhân vật: bơm gói −22 của CHÍNH
                            // Sơn (id 999) kèm khối người yêu (Linh) rồi mở
                            game.player.xu = 12500;
                            game.player.luong = 260;
                            game.player.luongKhoa = 1500;
                            game.player.xeng = 42;
                            game.player.gender = 1; // Sơn = Nam
                            try {
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                java.io.DataOutputStream w = new java.io.DataOutputStream(bos);
                                w.writeInt(999);
                                w.writeByte(50); w.writeByte(35); w.writeByte(90);
                                w.writeByte(10); w.writeByte(75); w.writeByte(95); w.writeByte(60);
                                w.writeInt(7);
                                w.writeUTF("Linh");
                                java.util.List<Integer> lp = game.data.npcs.get(3).items;
                                w.writeByte(lp.size());
                                for (int pid : lp) w.writeShort(pid);
                                w.writeUTF("Mãi mãi bên nhau");
                                // idImage -1: bản demo vẽ icon tim; dữ liệu
                                // thật có idImage thì client vẽ đúng ảnh gốc
                                w.writeShort(-1);
                                w.writeByte(12); w.writeByte(40);
                                w.writeUTF("Người yêu");
                                w.writeShort(-1);
                                // đuôi mở rộng: short cấp của mình + UTF ngày vào game
                                w.writeShort(50);
                                w.writeUTF("12/09/2024");
                                w.flush();
                                world.debugInjectNet((byte) -22, bos.toByteArray());
                            } catch (java.io.IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                            world.debugOpenCharPanel();
                            stage = 27; frame = 0;
                        }
                        break;
                    case 27:
                        if (frame == 20) {
                            shot("shot-char-panel.png");
                            world.debugSetCharVariant(1);
                            stage = 28; frame = 0;
                        }
                        break;
                    case 28:
                        if (frame == 15) {
                            shot("shot-char-v1.png");
                            world.debugSetCharVariant(2);
                            stage = 29; frame = 0;
                        }
                        break;
                    case 29:
                        if (frame == 15) {
                            shot("shot-char-v2.png");
                            world.debugSetCharVariant(3);
                            stage = 30; frame = 0;
                        }
                        break;
                    case 30:
                        if (frame == 15) {
                            shot("shot-char-v3.png");
                            world.debugOpenBag();
                            stage = 31; frame = 0;
                        }
                        break;
                    case 31:
                        if (frame == 15) {
                            shot("shot-bag.png");
                            world.debugOpenChest();
                            stage = 32; frame = 0;
                        }
                        break;
                    case 32:
                        if (frame == 15) {
                            shot("shot-chest.png");
                            world.debugOpenBagWearing();
                            stage = 33; frame = 0;
                        }
                        break;
                    case 33:
                        if (frame == 15) {
                            shot("shot-bag-wearing.png");
                            world.debugChestConfirm();
                            stage = 34; frame = 0;
                        }
                        break;
                    case 34:
                        if (frame == 15) {
                            shot("shot-chest-confirm.png");
                            world.debugMapZoomMock(0);
                            stage = 35; frame = 0;
                        }
                        break;
                    case 35:
                        if (frame == 15) {
                            shot("shot-mapzoom.png");
                            world.debugMapZoomMock(1);
                            stage = 36; frame = 0;
                        }
                        break;
                    case 36:
                        if (frame == 15) {
                            shot("shot-mapzoom-in.png");
                            world.debugOpenShop(0, 2169);
                            stage = 37; frame = 0;
                        }
                        break;
                    case 37:
                        if (frame == 15) {
                            shot("shot-shop.png");
                            world.debugOpenShop(5, 480);
                            stage = 38; frame = 0;
                        }
                        break;
                    case 38:
                        if (frame == 15) {
                            shot("shot-shop-vehicle.png");
                            world.debugOpenNpcMenu();
                            stage = 39; frame = 0;
                        }
                        break;
                    case 39:
                        if (frame == 15) {
                            shot("shot-npc-menu.png");
                            world.debugOpenShop(1, 2321);
                            stage = 40; frame = 0;
                        }
                        break;
                    case 40:
                        if (frame == 15) {
                            shot("shot-shop-quan.png");
                            world.debugOpenShopChoiceMock();
                            stage = 41; frame = 0;
                        }
                        break;
                    case 41:
                        if (frame == 15) {
                            shot("shot-shop-choice.png");
                            world.debugOpenBillMockXu();
                            stage = 42; frame = 0;
                        }
                        break;
                    case 42:
                        if (frame == 15) {
                            shot("shot-shop-bill-xu.png");
                            world.debugCloseShop();
                            world.debugOpenSettings();
                            stage = 43; frame = 0;
                        }
                        break;
                    case 43:
                        if (frame == 15) {
                            shot("shot-settings.png");
                            world.debugOpenPassForm();
                            stage = 44; frame = 0;
                        }
                        break;
                    case 44:
                        if (frame == 15) {
                            shot("shot-pass-form.png");
                            world.debugOpenActionSheet();
                            stage = 45; frame = 0;
                        }
                        break;
                    case 45:
                        if (frame == 15) {
                            shot("shot-action-sheet.png");
                            world.debugSitPose();
                            stage = 46; frame = 0;
                        }
                        break;
                    case 46:
                        if (frame == 15) {
                            shot("shot-action-pose.png");
                            world.debugSeedMusic(
                                new java.io.File("tools/yt-fixtures/search_seed.json").getAbsolutePath(),
                                new java.io.File("tools/yt-fixtures/thumbs").getAbsolutePath());
                            world.debugOpenMusic(0);
                            stage = 47; frame = 0;
                        }
                        break;
                    case 47:
                        if (frame == 15) {
                            shot("shot-music-search.png");
                            world.debugOpenMusic(1);
                            stage = 48; frame = 0;
                        }
                        break;
                    case 48:
                        if (frame == 15) {
                            shot("shot-music-playlist.png");
                            game.setScreen(new com.avatardk.game.ui.DataDownloadScreen(game, null, true));
                            stage = 49; frame = 0;
                        }
                        break;
                    case 49:
                        if (frame == 55) {
                            shot("shot-download.png");
                            game.setScreen(world);
                            world.debugOpenMsgInbox();
                            stage = 50; frame = 0;
                        }
                        break;
                    case 50:
                        if (frame == 15) {
                            shot("shot-msg-inbox.png");
                            world.debugOpenMsgChat();
                            stage = 51; frame = 0;
                        }
                        break;
                    case 51:
                        if (frame == 15) {
                            shot("shot-msg-chat.png");
                            world.debugOpenFriendPanel();
                            stage = 52; frame = 0;
                        }
                        break;
                    case 52:
                        if (frame == 15) {
                            shot("shot-friends.png");
                            world.debugFriendTapMessage();
                            stage = 53; frame = 0;
                        }
                        break;
                    case 53:
                        if (frame == 15) {
                            shot("shot-friend-tap-chat.png");
                            world.debugOpenMailPanel();
                            stage = 54; frame = 0;
                        }
                        break;
                    case 54:
                        if (frame == 15) {
                            shot("shot-mail.png");
                            world.debugOpenGiftForm();
                            stage = 57; frame = 0;
                        }
                        break;
                    case 55:
                        if (frame == 15) {
                            shot("shot-createchar.png");
                            game.createChar.debugShowcase();
                            stage = 56; frame = 0;
                        }
                        break;
                    case 56:
                        if (frame == 15) {
                            shot("shot-createchar-nu.png");
                            world.debugShowFarm();
                            game.setScreen(world);
                            stage = 58; frame = 0;
                        }
                        break;
                    case 58:
                        // ô chín + pill gợi ý "Thu hoạch ..." — KHÔNG bấm:
                        // chứng minh nút tròn làm ngay, không còn bảng chọn
                        if (frame == 15) {
                            shot("shot-farm.png");
                            world.debugOpenFarmSeeds();
                            stage = 59; frame = 0;
                        }
                        break;
                    case 59:
                        if (frame == 15) {
                            shot("shot-farm-seeds.png");
                            world.debugShowFarmShed();
                            stage = 60; frame = 0;
                        }
                        break;
                    case 60:
                        // nửa trái nhà kho + nút Tương tác -> mở thẳng Kho
                        if (frame == 10) world.debugInteract();
                        if (frame == 15) {
                            shot("shot-farm-store.png");
                            world.debugShowFarmShopFront();
                            stage = 61; frame = 0;
                        }
                        break;
                    case 61:
                        // nửa phải nhà kho + nút Tương tác -> mở thẳng Cửa hàng
                        if (frame == 10) world.debugInteract();
                        if (frame == 15) {
                            shot("shot-farm-shop.png");
                            world.debugOpenFarmShopAnimals();
                            stage = 62; frame = 0;
                        }
                        break;
                    case 62:
                        if (frame == 15) {
                            shot("shot-farm-shop-animals.png");
                            world.debugShowFarmKitchen();
                            stage = 63; frame = 0;
                        }
                        break;
                    case 63:
                        if (frame == 10) world.debugInteract();
                        if (frame == 15) {
                            shot("shot-farm-cook.png");
                            world.debugShowFarmAnimal();
                            stage = 64; frame = 0;
                        }
                        break;
                    case 64:
                        // đứng trên BỜ hồ (không xuống nước) + tương tác cá
                        if (frame == 10) world.debugInteract();
                        if (frame == 15) {
                            shot("shot-farm-animal.png");
                            world.debugShowFarmStar();
                            stage = 65; frame = 0;
                        }
                        break;
                    case 65:
                        if (frame == 15) {
                            shot("shot-farm-star.png");
                            System.out.println("SCREENSHOT DONE");
                            Gdx.app.exit();
                        }
                        break;
                    case 57:
                        if (frame == 15) {
                            shot("shot-giftform.png");
                            game.playerName = "nguoimoi01";
                            game.createChar.prepare();
                            game.setScreen(game.createChar);
                            stage = 55; frame = 0;
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

            /** Thân gói 57 AVATAR_FEEL server phát: int id + byte feel. */
            private byte[] feelPayload(int id, int feelByte) throws java.io.IOException {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream w = new java.io.DataOutputStream(bos);
                w.writeInt(id);
                w.writeByte(feelByte);
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
