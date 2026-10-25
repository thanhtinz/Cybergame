package com.neolang.cyber.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.BuildModule;
import com.neolang.cyber.model.ItemDef;
import com.neolang.cyber.model.Items;
import com.neolang.cyber.model.ObjectiveType;
import com.neolang.cyber.model.Player;
import com.neolang.cyber.model.Survival;
import com.neolang.cyber.ui.Draw;
import com.neolang.cyber.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Home branch: 6 glass hydroponic beds (left) + a 10x6 building grid (right) on one fixed screen. */
public class PlotScreen implements Screen {
    private final NeoLangGame game;
    private final ExtendViewport vp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);
    private float time;
    private int shotFrames;
    private boolean gone;

    private String lastNotice;
    private float noticeT = 99;

    private int seedBed = -1;          // bed waiting for a seed choice
    private BuildModule selectedModule; // build mode selection
    private int selCx = -1, selCy = -1; // selected placed cell
    private boolean cookOpen;
    private boolean charging;

    private static final float BED_X = 50, BED_Y = 330, BED_W = 168, BED_H = 118, BED_GAP = 14;
    private static final float GRID_X = 668, GRID_Y = 196, CELL = 46;

    public PlotScreen(NeoLangGame game) {
        this.game = game;
    }

    private long nowSec() {
        return System.currentTimeMillis() / 1000L;
    }

    private boolean btn(float x, float y, float w, float h, String label) {
        return Ui.button(game.batch, game.shapes, game.assets.fontSmall, vp, x, y, w, h, label);
    }

    private boolean hasModule(BuildModule m) {
        for (int cx = 0; cx < com.neolang.cyber.model.BuildPlot.COLS; cx++) {
            for (int cy = 0; cy < com.neolang.cyber.model.BuildPlot.ROWS; cy++) {
                if (game.state.build.moduleAt(cx, cy) == m.ordinal() + 1) return true;
            }
        }
        return false;
    }

    private void update(float dt) {
        Player p = game.state.player;
        game.state.clock.tick(dt);
        game.state.storm.tick(dt);
        game.state.playSeconds += dt;
        boolean nearHeat = hasModule(BuildModule.BEP) || hasModule(BuildModule.DEN_MACH);
        Survival.tick(p, dt, false, game.state.clock.isNight(), game.state.storm.active, nearHeat, true);
        if (Survival.isFainted(p)) {
            p.hp = (int) p.maxHp();
            p.satiety += (100 - (double) p.satiety) * 0.5;
            p.warmth += (100 - (double) p.warmth) * 0.5;
            p.battery += (100 - (double) p.battery) * 0.5;
            game.state.notice = "Bạn ngất ngay tại nhà — pet chăm sóc và bạn tỉnh lại khoẻ hơn.";
        }
        if (charging) {
            p.battery += 35 * dt;
            if ((double) p.battery >= 100) {
                p.battery = 100;
                charging = false;
                game.sound.play("coin");
                game.state.notice = "Pin đã sạc đầy tại Trạm Sạc nhà bạn.";
            }
        }
        if (game.state.notice != null && !game.state.notice.isEmpty() && !game.state.notice.equals(lastNotice)) {
            lastNotice = game.state.notice;
            noticeT = 0;
        }
        noticeT += dt;
    }

    @Override
    public void render(float dt) {
        time += dt;
        if (!gone) update(dt);
        if (gone) return;
        Player p = game.state.player;

        Gdx.gl.glClearColor(0.02f, 0.03f, 0.07f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        vp.apply();
        game.batch.setProjectionMatrix(vp.getCamera().combined);

        float dayness = game.state.clock.isNight() ? 0.3f : 0.95f;
        Draw.drawSky(game.shapes, 0, NeoLangGame.VW, dayness, game.state.storm.active, time);
        Draw.drawTrunkWorld(game.shapes, 0, NeoLangGame.VW, 150, 0, time);
        // house pod backdrop on the right
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.10f, 0.15f, 0.20f, 0.85f);
        game.shapes.rect(640, 150, 610, 420);
        game.shapes.setColor(0.25f, 0.95f, 1f, 0.5f);
        game.shapes.rect(640, 566, 610, 4);
        game.shapes.end();

        // header
        int houseLv = 1 + game.state.build.countModules() / 3;
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Khu Nhà Cành — Nhà cấp " + houseLv, 40, 700, Ui.CYAN, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Xu Lá: " + p.xuLa + "   Tinh Thể: " + p.tinhThe, 40, 668, Ui.GOLD, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Vườn thủy canh", 50, 640, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Khu xây dựng (chọn module ở thanh dưới, chạm ô để đặt)", 668, 640, Ui.TEXT, Align.left);
        game.batch.end();
        if (btn(1090, 656, 160, 44, "Về Làng")) {
            p.x = 200;
            game.saveGame();
            gone = true;
            game.gotoTown();
            return;
        }

        drawGarden();
        drawBuildGrid();
        drawModuleBar();
        drawHomeActions();

        // hero + pet idling at home + watering drone over the garden
        Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, 330, 168, 1.15f, p.gender,
            p.suitColorIdx, p.hairColorIdx, p.hairStyleIdx, p.eyeColorIdx, p.skinToneIdx, p.outfitId, p.gameClass, 1, 0, time);
        Draw.drawPet(game.shapes, 285, 168, time, petTier());
        float droneX = 320 + (float) Math.sin(time * 0.5f) * 240;
        Draw.drawPet(game.shapes, droneX, 600 + (float) Math.sin(time * 2) * 6, time, 1);
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall,
            p.gameClass == com.neolang.cyber.model.GameClass.BIO_HACKER
                ? "Drone tưới của Bio-Hacker: cây lớn nhanh hơn thấy rõ." : "Drone tưới tự động của khu vườn.",
            50, 300, Ui.TEXT_DIM, Align.left);
        game.batch.end();

        if (seedBed >= 0) drawSeedPanel();
        if (cookOpen) drawCookPanel();
        if (selCx >= 0) drawCellPanel();

        if (lastNotice != null && !lastNotice.isEmpty() && noticeT < 4.5f) {
            float w = Math.min(760, Ui.textWidth(game.assets.fontSmall, lastNotice) + 36);
            Ui.panel(game.shapes, 640 - w / 2, 116, w, 40, new Color(0.06f, 0.14f, 0.10f, 0.94f));
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, lastNotice, 640, 142, Ui.GREEN, Align.center);
            game.batch.end();
        }

        if (game.screenshotDir != null) {
            shotFrames++;
            if (shotFrames == 90) {
                game.captureShot("plot.png");
                Gdx.app.exit();
            }
        }
    }

    private int petTier() {
        return Math.min(3, 1 + game.state.player.countItem("pet_chip"));
    }

    private void drawGarden() {
        Player p = game.state.player;
        long now = nowSec();
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            float x = BED_X + col * (BED_W + BED_GAP);
            float y = BED_Y + (1 - row) * (BED_H + BED_GAP);
            boolean planted = game.state.garden.plots[i].planted;
            float prog = planted ? game.state.garden.progress(i, now) : 0f;
            boolean ready = planted && game.state.garden.isReady(i, now);
            String seedId = planted ? game.state.garden.plots[i].seedId : null;
            int seedIdx = seedId != null ? Math.abs(seedId.hashCode()) : 0;
            Draw.drawGardenBed(game.shapes, x, y, BED_W, BED_H, planted, prog, ready, seedIdx, time);
            game.batch.begin();
            String label = !planted ? "Ô trống — chạm để trồng"
                : ready ? "Chín rồi — chạm để thu hoạch!"
                : "Đang lớn: " + (int) (prog * 100) + "%";
            Ui.label(game.batch, game.assets.fontSmall, label, x + BED_W / 2, y + BED_H - 10, ready ? Ui.GOLD : Ui.TEXT_DIM, Align.center);
            game.batch.end();
            if (Ui.tapped(vp, x, y, BED_W, BED_H)) {
                if (!planted) {
                    seedBed = i;
                    game.sound.play("click");
                } else if (ready) {
                    String crop = game.state.garden.harvest(i, now);
                    if (crop != null) {
                        p.addItem(crop, 1);
                        game.state.quests.onEvent(ObjectiveType.HARVEST, 1);
                        game.sound.play("harvest");
                        com.neolang.cyber.model.ItemDef cd = Items.get(crop);
                        game.state.notice = "Thu hoạch được " + (cd != null ? cd.name : crop) + "!";
                        game.saveGame();
                    }
                } else {
                    game.state.notice = "Cây đang lớn: " + (int) (prog * 100) + "% — chờ chín rồi thu hoạch nhé.";
                    game.sound.play("click");
                }
            }
        }
    }

    private void drawSeedPanel() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 380, 210, 520, 300);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Chọn hạt giống cho ô " + (seedBed + 1), 640, 482, Ui.CYAN, Align.center);
        game.batch.end();
        float y = 420;
        boolean any = false;
        for (ItemDef d : Items.ALL.values()) {
            if (!"SEED".equals(String.valueOf(d.type))) continue;
            int n = p.countItem(d.id);
            if (n <= 0) continue;
            any = true;
            final String seedId = d.id;
            if (btn(410, y, 460, 42, "Trồng: " + d.name + " (có x" + n + ")")) {
                if (game.state.garden.plant(seedBed, seedId)) {
                    game.state.quests.onEvent(ObjectiveType.PLANT, 1);
                    game.sound.play("plant");
                    game.state.notice = "Đã trồng " + d.name + ".";
                    game.saveGame();
                } else {
                    game.sound.play("error");
                }
                seedBed = -1;
                return;
            }
            y -= 52;
            if (y < 280) break;
        }
        if (!any) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Chưa có hạt giống. Farm quái ở Bìa Rừng để nhặt hạt nhé.", 640, 400, Ui.TEXT_DIM, Align.center);
            game.batch.end();
        }
        if (btn(790, 228, 90, 36, "Đóng")) seedBed = -1;
    }

    private void drawBuildGrid() {
        for (int cx = 0; cx < com.neolang.cyber.model.BuildPlot.COLS; cx++) {
            for (int cy = 0; cy < com.neolang.cyber.model.BuildPlot.ROWS; cy++) {
                float x = GRID_X + cx * CELL;
                float y = GRID_Y + cy * CELL;
                int v = game.state.build.moduleAt(cx, cy);
                game.shapes.begin(ShapeType.Filled);
                game.shapes.setColor(v == 0 ? new Color(0.14f, 0.18f, 0.22f, 0.9f) : new Color(0.20f, 0.26f, 0.30f, 1));
                game.shapes.rect(x + 1, y + 1, CELL - 2, CELL - 2);
                if (selCx == cx && selCy == cy) {
                    game.shapes.setColor(Ui.GOLD);
                    game.shapes.rect(x, y, CELL, 2);
                    game.shapes.rect(x, y + CELL - 2, CELL, 2);
                }
                game.shapes.end();
                if (v > 0) {
                    BuildModule m = BuildModule.values()[v - 1];
                    Draw.drawModule(game.shapes, m, x + CELL / 2, y + CELL / 2 + 3, CELL, time);
                    Draw.drawLevelPips(game.shapes, x + CELL / 2, y + 3, game.state.build.levelAt(cx, cy));
                }
                if (Ui.tapped(vp, x, y, CELL, CELL)) {
                    onCellTap(cx, cy, v);
                    if (gone) return;
                }
            }
        }
    }

    private void onCellTap(int cx, int cy, int v) {
        Player p = game.state.player;
        if (selectedModule != null) {
            if (game.state.build.canPlace(selectedModule, cx, cy)) {
                if (game.state.build.place(p, selectedModule, cx, cy)) {
                    game.state.quests.onEvent(ObjectiveType.BUILD, 1);
                    game.sound.play("buy");
                    game.state.notice = "Đã xây " + selectedModule.displayName + ".";
                    game.saveGame();
                } else {
                    game.sound.play("error");
                    game.state.notice = "Không xây được (thiếu Xu hoặc vị trí không hợp).";
                }
            } else {
                game.sound.play("error");
                game.state.notice = "Ô này không đặt được " + selectedModule.displayName + ".";
            }
        } else if (v > 0) {
            selCx = cx;
            selCy = cy;
            game.sound.play("click");
        }
    }

    private void drawCellPanel() {
        Player p = game.state.player;
        int v = game.state.build.moduleAt(selCx, selCy);
        if (v <= 0) { selCx = -1; return; }
        BuildModule m = BuildModule.values()[v - 1];
        int lv = game.state.build.levelAt(selCx, selCy);
        Ui.panel(game.shapes, 420, 250, 440, 210);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, m.displayName + " — Cấp " + lv, 640, 432, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Nâng cấp để tăng hiệu quả của module này.", 640, 398, Ui.TEXT_DIM, Align.center);
        game.batch.end();
        if (btn(450, 330, 240, 44, "Nâng cấp module")) {
            if (game.state.build.upgrade(p, selCx, selCy)) {
                game.sound.play("levelup");
                game.state.notice = m.displayName + " đã lên cấp " + game.state.build.levelAt(selCx, selCy) + ".";
                game.saveGame();
            } else {
                game.sound.play("error");
                game.state.notice = "Không nâng cấp được (thiếu Xu hoặc đã tối đa).";
            }
        }
        if (btn(730, 330, 100, 44, "Đóng")) selCx = -1;
        if (btn(450, 276, 380, 40, "Bỏ chọn (chạm ô khác để xem)")) selCx = -1;
    }

    private void drawModuleBar() {
        // two rows of module choices at the bottom
        BuildModule[] all = BuildModule.values();
        for (int i = 0; i < all.length; i++) {
            BuildModule m = all[i];
            int col = i % 5, row = i / 5;
            float x = 668 + col * 118;
            float y = row == 0 ? 108 : 58;
            boolean sel = selectedModule == m;
            if (sel) {
                game.shapes.begin(ShapeType.Filled);
                game.shapes.setColor(Ui.GOLD);
                game.shapes.rect(x - 3, y - 3, 112 + 6, 42 + 6);
                game.shapes.end();
            }
            if (btn(x, y, 112, 42, m.displayName + " " + m.cost)) {
                selectedModule = sel ? null : m;
                selCx = -1;
                game.sound.play("click");
            }
        }
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, selectedModule != null
            ? "Đang chọn: " + selectedModule.displayName + " — chạm ô trống để đặt"
            : "Chế độ Xây: chọn một module rồi chạm ô trống.", 668, 168, Ui.TEXT_DIM, Align.left);
        game.batch.end();
    }

    private void drawHomeActions() {
        float x = 50;
        if (hasModule(BuildModule.GIUONG)) {
            if (btn(x, 108, 190, 46, "Ngủ qua đêm")) {
                Player p = game.state.player;
                double dl = (double) com.neolang.cyber.model.WorldClock.DAY_LENGTH;
                double cur = (double) game.state.clock.t;
                double delta = dl - (cur % dl) + dl * 0.02;
                game.state.clock.t += (float) delta;
                p.hp = (int) p.maxHp();
                p.satiety += (100 - (double) p.satiety) * 0.6;
                p.warmth += (100 - (double) p.warmth) * 0.6;
                p.battery += (100 - (double) p.battery) * 0.6;
                game.sound.play("harvest");
                game.state.notice = "Bạn ngủ một giấc ngon — trời đã sáng, chỉ số hồi phục.";
                game.saveGame();
            }
            x += 200;
        }
        if (hasModule(BuildModule.BEP)) {
            if (btn(x, 108, 170, 46, "Nấu ăn (Bếp)")) { cookOpen = true; game.sound.play("click"); }
            x += 180;
        }
        if (hasModule(BuildModule.TRAM_SAC)) {
            if (btn(x, 108, 190, 46, charging ? "Đang sạc pin..." : "Sạc pin (Trạm Sạc)")) {
                charging = true;
                game.sound.play("click");
            }
            x += 200;
        }
        if (hasModule(BuildModule.DOCK_PET)) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Dock Pet: pet nhặt đồ nhanh hơn.", x, 132, Ui.TEXT_DIM, Align.left);
            game.batch.end();
        }
    }

    private void drawCookPanel() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 380, 200, 520, 320);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Bếp — Nấu ăn", 640, 492, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Đổi 2 nông sản bất kỳ thành 1 món ăn:", 640, 462, Ui.TEXT_DIM, Align.center);
        game.batch.end();
        ItemDef target = Items.get("banh_la");
        if (target == null) {
            for (ItemDef d : Items.ALL.values()) {
                if ("FOOD".equals(String.valueOf(d.type))) { target = d; break; }
            }
        }
        float y = 408;
        boolean any = false;
        for (ItemDef d : Items.ALL.values()) {
            String t = String.valueOf(d.type);
            if ("SEED".equals(t) || "OUTFIT".equals(t) || "FOOD".equals(t)) continue;
            int n = p.countItem(d.id);
            if (n < 2) continue;
            any = true;
            final ItemDef src = d;
            final ItemDef tgt = target;
            if (btn(410, y, 460, 42, "Nấu 2 " + src.name + " (có x" + n + ") -> 1 " + (tgt != null ? tgt.name : "món ăn"))) {
                if (tgt != null) {
                    p.removeItem(src.id, 2);
                    p.addItem(tgt.id, 1);
                    game.sound.play("eat");
                    game.state.notice = "Đã nấu được " + tgt.name + "!";
                    game.saveGame();
                }
            }
            y -= 52;
            if (y < 280) break;
        }
        if (!any) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Chưa đủ nông sản (cần x2 cùng loại). Ra vườn thu hoạch nhé.", 640, 400, Ui.TEXT_DIM, Align.center);
            game.batch.end();
        }
        if (btn(790, 218, 90, 36, "Đóng")) cookOpen = false;
    }

    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void show() { vp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true); }
    @Override public void hide() { }
    @Override public void pause() { game.saveGame(); }
    @Override public void resume() { }
    @Override public void dispose() { }
}
