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
 * Màn chọn máy chủ của Thị Trấn Mặt Trời (mockup v3 đã duyệt): nền banner KHÔNG
 * làm mờ, có logo và 3 nút dọc như màn banner; cụm chọn server nằm THẤP ở nửa
 * dưới — một khung nhỏ chứa tên server đang chọn + nút Đổi, bên dưới là nút
 * VÀO THỊ TRẤN và dòng Đổi tài khoản đậm rõ. Bấm Đổi mở panel chọn server:
 * cột trái là các CỤM, bên phải là các server trong cụm.
 */
public class ServerSelectScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);

    private static final Color ROW_FILL = new Color(0.09f, 0.14f, 0.21f, 0.95f);
    private static final Color ROW_SEL_FILL = new Color(0.2f, 0.26f, 0.35f, 0.97f);
    private static final Color SEL_RIM = new Color(1f, 0.67f, 0.24f, 1f);
    private static final Color SUB_TEXT = new Color(0.67f, 0.75f, 0.84f, 1f);
    private static final Color GREEN = new Color(0.25f, 0.78f, 0.35f, 1f);
    private static final Color AMBER = new Color(0.96f, 0.65f, 0.16f, 1f);

    // Khung server nhỏ + nút vào thị trấn (nửa dưới màn hình)
    private static final float FRAME_W = 500f, FRAME_X = Ui.W / 2f - FRAME_W / 2f, FRAME_Y = 156f, FRAME_H = 84f;
    private static final float ENTER_Y = 66f, ENTER_H = 66f;

    // Panel chọn server
    private static final float P_W = 820f, P_H = 545f;
    private static final float P_X = Ui.W / 2f - P_W / 2f, P_Y = Ui.H / 2f - P_H / 2f;
    private boolean panelOpen = false;
    private int tmpCluster, tmpServer;

    public ServerSelectScreen(AvatarDkGame game) {
        this.game = game;
    }

    public void debugOpenPanel() {
        tmpCluster = Servers.selectedCluster();
        tmpServer = Servers.selectedServer();
        panelOpen = true;
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
        if (panelOpen) {
            onPanelTouch(x, y);
            return;
        }
        int util = EntryUi.utilHit(x, y);
        if (util >= 0) {
            EntryUi.onUtil(game, util);
            return;
        }
        // nút Đổi trong khung server
        if (x >= FRAME_X + FRAME_W - 118 && x <= FRAME_X + FRAME_W - 18
            && y >= FRAME_Y + 18 && y <= FRAME_Y + 66) {
            debugOpenPanel();
            return;
        }
        // nút VÀO THỊ TRẤN
        if (x >= FRAME_X && x <= FRAME_X + FRAME_W && y >= ENTER_Y && y <= ENTER_Y + ENTER_H) {
            game.setScreen(game.world);
            return;
        }
        // dòng Đổi tài khoản
        if (x >= Ui.W / 2f - 110 && x <= Ui.W / 2f + 110 && y >= 26 && y <= 58) {
            game.setScreen(game.login);
        }
    }

    private float clusterRowY(int i) { return P_Y + P_H - 92 - 64 - i * 76; }
    private float serverRowY(int i) { return P_Y + P_H - 92 - 70 - i * 84; }

    private void onPanelTouch(float x, float y) {
        boolean inside = x >= P_X && x <= P_X + P_W && y >= P_Y && y <= P_Y + P_H;
        boolean closeBtn = Math.abs(x - (P_X + P_W - 40)) < 26 && Math.abs(y - (P_Y + P_H - 42)) < 26;
        if (!inside || closeBtn) {
            panelOpen = false;
            return;
        }
        float lx = P_X + 24, rx = P_X + 272, rw = P_W - 272 - 24;
        for (int i = 0; i < Servers.CLUSTERS.size(); i++) {
            float ry = clusterRowY(i);
            if (x >= lx && x <= lx + 224 && y >= ry && y <= ry + 64) {
                tmpCluster = i;
                tmpServer = Math.min(tmpServer, Servers.CLUSTERS.get(i).servers.size() - 1);
                return;
            }
        }
        int count = Servers.CLUSTERS.get(tmpCluster).servers.size();
        for (int i = 0; i < count; i++) {
            float ry = serverRowY(i);
            if (x >= rx && x <= rx + rw && y >= ry && y <= ry + 70) {
                tmpServer = i;
                return;
            }
        }
        float by = P_Y + 24;
        if (y >= by && y <= by + 50) {
            if (x >= P_X + P_W - 300 && x <= P_X + P_W - 176) {
                panelOpen = false; // Huỷ
            } else if (x >= P_X + P_W - 164 && x <= P_X + P_W - 24) {
                Servers.select(tmpCluster, tmpServer);
                panelOpen = false;
            }
        }
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
        drawServerBlock(batch);
        if (panelOpen) drawPanel(batch);
        EntryUi.drawInfoPanel(batch);
        EntryUi.drawToast(batch);
        batch.end();
    }

    private void drawServerBlock(SpriteBatch batch) {
        Servers.Server s = Servers.selected();
        String name = game.playerName == null || game.playerName.isEmpty() ? "bạn" : game.playerName;
        Ui.centered(batch, Ui.fontBig, "Xin chào, " + name, Ui.W / 2f, 284, Color.WHITE);
        // khung server nhỏ
        batch.draw(Ui.roundedRect((int) FRAME_W, (int) FRAME_H, 18,
            EntryUi.PANEL_FILL, EntryUi.PANEL_RIM), FRAME_X, FRAME_Y);
        Ui.faIcon(batch, Ui.fa30, Ui.FA_SUN, FRAME_X + 40, FRAME_Y + 42, EntryUi.GOLD);
        Ui.fontBig.setColor(Color.WHITE);
        Ui.fontBig.draw(batch, s.label, FRAME_X + 64, FRAME_Y + 56);
        batch.draw(Ui.dot(10, s.crowded ? AMBER : GREEN), FRAME_X + 64, FRAME_Y + 17);
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch, s.statusText + " · " + s.players, FRAME_X + 82, FRAME_Y + 27);
        // nút Đổi
        batch.draw(Ui.gradientRect(100, 48, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            FRAME_X + FRAME_W - 118, FRAME_Y + 18);
        Ui.centered(batch, Ui.font, "Đổi", FRAME_X + FRAME_W - 68, FRAME_Y + 49, Color.WHITE);
        // nút VÀO THỊ TRẤN
        batch.draw(Ui.gradientRect((int) FRAME_W, (int) ENTER_H, 16,
            EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), FRAME_X, ENTER_Y);
        Ui.centered(batch, Ui.fontBaloo30, "VÀO THỊ TRẤN", Ui.W / 2f, ENTER_Y + 43, Color.WHITE);
        // Đổi tài khoản: trắng đậm có bóng tối cho rõ trên nền đường
        Ui.centered(batch, Ui.font, "Đổi tài khoản", Ui.W / 2f, 44, new Color(0.06f, 0.12f, 0.2f, 0.9f));
        Ui.centered(batch, Ui.font, "Đổi tài khoản", Ui.W / 2f, 46, Color.WHITE);
    }

    private void drawPanel(SpriteBatch batch) {
        batch.draw(Ui.roundedRect((int) Ui.W, (int) Ui.H, 0,
            new Color(0.01f, 0.03f, 0.06f, 0.43f), null), 0, 0);
        batch.draw(Ui.roundedRect((int) P_W, (int) P_H, 22,
            EntryUi.PANEL_FILL, EntryUi.PANEL_RIM), P_X, P_Y);
        float top = P_Y + P_H;
        Ui.fontBaloo36.setColor(EntryUi.GOLD);
        Ui.fontBaloo36.draw(batch, "CHỌN MÁY CHỦ", P_X + 28, top - 28);
        Ui.faIcon(batch, Ui.fa26, Ui.FA_XMARK, P_X + P_W - 40, top - 42, Color.WHITE);
        float lx = P_X + 24, rx = P_X + 272, rw = P_W - 272 - 24;
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch, "CỤM MÁY CHỦ", lx + 4, top - 66);
        Ui.fontSmall.draw(batch, "MÁY CHỦ TRONG " + Servers.CLUSTERS.get(tmpCluster).name.toUpperCase(),
            rx + 4, top - 66);
        // cột trái: các cụm
        for (int i = 0; i < Servers.CLUSTERS.size(); i++) {
            Servers.Cluster c = Servers.CLUSTERS.get(i);
            float ry = clusterRowY(i);
            boolean sel = i == tmpCluster;
            batch.draw(Ui.roundedRect(224, 64, 13, sel ? ROW_SEL_FILL : ROW_FILL,
                sel ? SEL_RIM : EntryUi.PANEL_RIM), lx, ry);
            Ui.font.setColor(Color.WHITE);
            Ui.font.draw(batch, c.name, lx + 16, ry + 40);
            Ui.fontSmall.setColor(SUB_TEXT);
            Ui.fontSmall.draw(batch, c.servers.size() + " máy chủ", lx + 16, ry + 18);
        }
        // cột phải: server trong cụm
        java.util.List<Servers.Server> list = Servers.CLUSTERS.get(tmpCluster).servers;
        for (int i = 0; i < list.size(); i++) {
            Servers.Server s = list.get(i);
            float ry = serverRowY(i);
            boolean sel = i == tmpServer;
            batch.draw(Ui.roundedRect((int) rw, 70, 14, sel ? ROW_SEL_FILL : ROW_FILL,
                sel ? SEL_RIM : EntryUi.PANEL_RIM), rx, ry);
            batch.draw(Ui.dot(16, s.crowded ? AMBER : GREEN), rx + 18, ry + 27);
            Ui.font.setColor(Color.WHITE);
            Ui.font.draw(batch, s.label, rx + 46, ry + 46);
            Ui.fontSmall.setColor(SUB_TEXT);
            Ui.fontSmall.draw(batch, s.statusText, rx + 46, ry + 20);
            float pw = Ui.textWidth(Ui.fontSmall, s.players);
            Ui.fontSmall.setColor(0.75f, 0.81f, 0.9f, 1f);
            Ui.fontSmall.draw(batch, s.players, rx + rw - 18 - pw, ry + 46);
            if (sel) {
                String tag = "Đang chọn";
                float tw = Ui.textWidth(Ui.fontSmall, tag);
                Ui.fontSmall.setColor(1f, 0.75f, 0.31f, 1f);
                Ui.fontSmall.draw(batch, tag, rx + rw - 18 - tw, ry + 20);
            }
        }
        // hàng nút cuối
        float by = P_Y + 24;
        batch.draw(Ui.roundedRect(124, 50, 12, new Color(0.12f, 0.18f, 0.26f, 1f),
            EntryUi.PANEL_RIM), P_X + P_W - 300, by);
        Ui.centered(batch, Ui.font, "HUỶ", P_X + P_W - 238, by + 32, Color.WHITE);
        batch.draw(Ui.gradientRect(140, 50, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            P_X + P_W - 164, by);
        Ui.centered(batch, Ui.font, "XÁC NHẬN", P_X + P_W - 94, by + 32, Color.WHITE);
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
