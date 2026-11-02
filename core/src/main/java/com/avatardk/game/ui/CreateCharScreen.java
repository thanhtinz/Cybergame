package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.data.PaperDoll;
import com.avatardk.game.data.PartData;
import com.avatardk.game.net.AvatarProtocol;
import com.avatardk.game.net.GameConnection;
import com.avatardk.game.net.Msg;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Màn TẠO NHÂN VẬT — port của RegisterScr.cs gốc: sau khi đăng nhập mà
 * nhân vật chưa được tạo (gender = 0 trong gói LOGIN_SUCESS), người chơi
 * vào đây chọn giới tính (1 = nam, 2 = nữ) và trang phục khởi đầu từ kho
 * đồ cấp 0 của gốc: Tóc (zOrder 50), Áo (20), Quần (10) — lọc đúng luật
 * gốc (level 0, gender khớp hoặc 0). Doll xem trước mặc đúng bộ đang
 * chọn. Hoàn thành gửi gói CRE_CHARACTER (-35) với seriPart theo đúng
 * thứ tự server gốc kiểm tra: [quần, áo, thân (0), mặt (4), tóc].
 * Bố cục TOÀN MÀN HÌNH (user chốt 2026-11-02): không nền banner, không
 * panel nổi — nền gradient navy kín màn, doll lớn bên trái, điều khiển
 * bên phải.
 */
public class CreateCharScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);

    private static final Color SUB_TEXT = new Color(0.67f, 0.75f, 0.84f, 1f);
    private static final Color BAR_FILL = new Color(0.06f, 0.11f, 0.18f, 1f);
    private static final Color CHIP_FILL = new Color(0.10f, 0.17f, 0.26f, 1f);
    private static final Color ERR_RED = new Color(1f, 0.45f, 0.40f, 1f);

    /** Ô xem trước bên trái. */
    private static final float IX = 64, IY = 84, IW = 440, IH = 538;
    /** Vùng điều khiển bên phải. */
    private static final float RX = 560, RW = 656;
    /** Y (mép dưới) của 3 thanh chọn đồ: Tóc / Áo / Quần. */
    private static final int[] BAR_Y = {444, 344, 244};

    private int gender = 1;
    private final List<PartData> hairList = new ArrayList<>();
    private final List<PartData> clothList = new ArrayList<>();
    private final List<PartData> pantList = new ArrayList<>();
    private int hairIdx, clothIdx, pantIdx;
    private PaperDoll doll;
    private String status = "";
    private boolean statusError = false;
    private boolean busy = false;
    private float elapsed = 0f;

    public CreateCharScreen(AvatarDkGame game) {
        this.game = game;
    }

    /** Bản trưng bày cho harness chụp ảnh: nhân vật nữ với vài lựa chọn
     *  khác mặc định để thấy rõ cơ chế đổi đồ. */
    public void debugShowcase() {
        setGender(2);
        cycle(0, 3);
        cycle(1, 2);
        cycle(2, 1);
    }

    /** Chuẩn bị lại từ đầu (LoginScreen gọi trước khi chuyển màn). */
    public void prepare() {
        gender = 1;
        busy = false;
        status = "";
        statusError = false;
        buildLists();
    }

    @Override
    public void show() {
        if (hairList.isEmpty()) prepare();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector3 v = viewport.unproject(new Vector3(sx, sy, 0));
                onTouch(v.x, v.y);
                return true;
            }
        });
    }

    // ---------- dữ liệu ----------

    private void buildLists() {
        hairList.clear();
        clothList.clear();
        pantList.clear();
        List<PartData> all = new ArrayList<>(game.data.parts.values());
        all.sort(Comparator.comparingInt(p -> p.id));
        for (PartData p : all) {
            if (p.level != 0 || p.isDynamic() || p.name == null || p.name.isEmpty()) continue;
            if (p.gender != gender && p.gender != 0) continue;
            if (p.zorder == 50) hairList.add(p);
            else if (p.zorder == 20) clothList.add(p);
            else if (p.zorder == 10) pantList.add(p);
        }
        hairIdx = indexOf(hairList, gender == 1 ? 14 : 49);
        clothIdx = indexOf(clothList, 88);
        pantIdx = indexOf(pantList, 89);
        refreshDoll();
    }

    private static int indexOf(List<PartData> list, int id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) return i;
        return 0;
    }

    private void setGender(int g) {
        if (gender == g || busy) return;
        gender = g;
        buildLists();
    }

    private void cycle(int row, int dir) {
        if (busy) return;
        if (row == 0 && !hairList.isEmpty()) hairIdx = Math.floorMod(hairIdx + dir, hairList.size());
        if (row == 1 && !clothList.isEmpty()) clothIdx = Math.floorMod(clothIdx + dir, clothList.size());
        if (row == 2 && !pantList.isEmpty()) pantIdx = Math.floorMod(pantIdx + dir, pantList.size());
        refreshDoll();
    }

    /** seriPart theo đúng thứ tự server gốc: [quần, áo, thân 0, mặt 4, tóc]. */
    private List<Integer> currentParts() {
        List<Integer> parts = new ArrayList<>(5);
        parts.add(pantList.isEmpty() ? 89 : pantList.get(pantIdx).id);
        parts.add(clothList.isEmpty() ? 88 : clothList.get(clothIdx).id);
        parts.add(0);
        parts.add(4);
        parts.add(hairList.isEmpty() ? (gender == 1 ? 14 : 49) : hairList.get(hairIdx).id);
        return parts;
    }

    private void refreshDoll() {
        doll = game.newDoll(currentParts());
    }

    // ---------- mạng ----------

    private void submit() {
        if (busy) return;
        GameConnection conn = game.connection;
        if (conn == null || conn.isClosed()) {
            status = "Chưa kết nối máy chủ, không tạo được nhân vật.";
            statusError = true;
            return;
        }
        busy = true;
        status = "";
        statusError = false;
        try {
            AvatarProtocol.createCharacter(conn, gender, currentParts());
        } catch (Exception e) {
            busy = false;
            status = "Không gửi được: " + e.getMessage();
            statusError = true;
        }
    }

    private void pollReplies() {
        GameConnection conn = game.connection;
        if (conn == null) return;
        Msg m;
        while ((m = conn.poll()) != null) {
            try {
                if (m.command == AvatarProtocol.CRE_CHARACTER) {
                    DataInputStream r = m.reader();
                    boolean ok = r.readBoolean();
                    busy = false;
                    if (ok) {
                        // cập nhật thông tin nhân vật tại chỗ rồi vào luồng
                        // chọn máy chủ như đăng nhập bình thường
                        if (game.player != null) {
                            List<Integer> parts = currentParts();
                            game.player.gender = gender;
                            game.player.wearing = new int[parts.size()];
                            for (int i = 0; i < parts.size(); i++) {
                                game.player.wearing[i] = parts.get(i);
                            }
                            game.world.applyPlayerInfo(game.player);
                        }
                        game.setScreen(game.serverSelect);
                    } else {
                        status = "Tạo nhân vật thất bại, hãy thử bộ khác.";
                        statusError = true;
                    }
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- vẽ ----------

    @Override
    public void render(float delta) {
        elapsed += delta;
        pollReplies();
        Gdx.gl.glClearColor(0.03f, 0.06f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        // nền navy kín toàn màn (không banner, không panel nổi)
        batch.draw(Ui.gradientRect((int) Ui.W, (int) Ui.H, 0,
            new Color(0.10f, 0.18f, 0.30f, 1f), new Color(0.03f, 0.06f, 0.12f, 1f)), 0, 0);
        drawHeader();
        drawPreview();
        drawControls();
        drawButton();
        batch.end();
    }

    private void drawHeader() {
        Ui.faIcon(batch, Ui.fa30, Ui.FA_USER_PLUS, 88, 681, EntryUi.GOLD);
        Ui.fontBaloo36.setColor(EntryUi.GOLD);
        Ui.fontBaloo36.draw(batch, "Tạo nhân vật", 116, 692);
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch, "Chọn giới tính và trang phục khởi đầu", 117, 662);
    }

    private void drawPreview() {
        batch.draw(Ui.roundedRect((int) IW, (int) IH, 20, BAR_FILL, EntryUi.PANEL_RIM), IX, IY);
        if (!game.playerName.isEmpty()) {
            Ui.centered(batch, Ui.font, game.playerName, IX + IW / 2f, IY + IH - 28,
                new Color(1f, 0.85f, 0.45f, 1f));
        }
        // sàn sáng nhẹ dưới chân + bóng + doll xem trước (nhấp nhô nhẹ)
        batch.draw(Ui.roundedRect((int) IW - 48, 10, 5,
            new Color(1f, 1f, 1f, 0.06f), null), IX + 24, 148);
        float feetX = IX + IW / 2f, feetY = 172 + (float) Math.sin(elapsed * 2.2f) * 2.5f;
        batch.draw(Ui.roundedRect(170, 16, 8, new Color(0f, 0f, 0f, 0.25f), null),
            feetX - 85, 156);
        if (doll != null) {
            game.dollRenderer.draw(batch, doll, 0, feetX, feetY, 7.2f, false);
        }
    }

    private void drawControls() {
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch, "Giới tính", RX, 622);
        drawGenderPill(RX, 1, "Nam", Ui.FA_MARS);
        drawGenderPill(RX + 336, 2, "Nữ", Ui.FA_VENUS);
        drawRow(0, "Tóc", hairList, hairIdx, 528);
        drawRow(1, "Áo", clothList, clothIdx, 428);
        drawRow(2, "Quần", pantList, pantIdx, 328);
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch,
            "Trang phục khởi đầu có thể thay đổi bất cứ lúc nào trong game.", RX, 208);
        if (!status.isEmpty()) {
            Ui.fontSmall.setColor(statusError ? ERR_RED : SUB_TEXT);
            Ui.fontSmall.draw(batch, status, RX, 176);
        }
    }

    private void drawGenderPill(float x, int g, String label, String icon) {
        float w = 320, y = 540, h = 56;
        boolean sel = gender == g;
        if (sel) {
            batch.draw(Ui.gradientRect((int) w, (int) h, 14,
                EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), x, y);
        } else {
            batch.draw(Ui.roundedRect((int) w, (int) h, 14, BAR_FILL, EntryUi.PANEL_RIM), x, y);
        }
        float tw = Ui.textWidth(Ui.font, label);
        float gx = x + w / 2f - (26 + tw) / 2f;
        Ui.faIcon(batch, Ui.fa20, icon, gx + 13, y + 28,
            sel ? Color.WHITE : SUB_TEXT);
        Ui.font.setColor(sel ? Color.WHITE : SUB_TEXT);
        Ui.font.draw(batch, label, gx + 28, y + 36);
    }

    private void drawRow(int row, String label, List<PartData> list, int idx, float labelY) {
        float barY = BAR_Y[row];
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch, label, RX, labelY);
        if (!list.isEmpty()) {
            String cnt = (idx + 1) + "/" + list.size();
            Ui.fontTiny.setColor(SUB_TEXT);
            Ui.fontTiny.draw(batch, cnt, RX + RW - Ui.textWidth(Ui.fontTiny, cnt), labelY - 2);
        }
        batch.draw(Ui.roundedRect((int) RW, 64, 14, BAR_FILL, EntryUi.PANEL_RIM), RX, barY);
        batch.draw(Ui.roundedRect(54, 54, 11, CHIP_FILL, EntryUi.PANEL_RIM), RX + 5, barY + 5);
        batch.draw(Ui.roundedRect(54, 54, 11, CHIP_FILL, EntryUi.PANEL_RIM), RX + RW - 59, barY + 5);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_CHEVRON_LEFT, RX + 32, barY + 32,
            new Color(0.9f, 0.93f, 1f, 1f));
        Ui.faIcon(batch, Ui.fa20, Ui.FA_CHEVRON_RIGHT, RX + RW - 32, barY + 32,
            new Color(0.9f, 0.93f, 1f, 1f));
        if (!list.isEmpty()) {
            String name = trimToWidth(Ui.font, list.get(idx).name, RW - 140);
            Ui.centered(batch, Ui.font, name, RX + RW / 2f, barY + 41, Color.WHITE);
        }
    }

    private void drawButton() {
        batch.draw(Ui.gradientRect((int) RW, 72, 18,
            EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), RX, 84);
        if (busy) {
            Ui.centered(batch, Ui.fontBaloo30, "Đang tạo nhân vật…", RX + RW / 2f, 129,
                Color.WHITE);
        } else {
            float tw = Ui.textWidth(Ui.fontBaloo30, "Hoàn thành");
            Ui.faIcon(batch, Ui.fa20, Ui.FA_CHECK, RX + RW / 2f - tw / 2f - 22, 120, Color.WHITE);
            Ui.centered(batch, Ui.fontBaloo30, "Hoàn thành", RX + RW / 2f, 129, Color.WHITE);
        }
    }

    private static String trimToWidth(com.badlogic.gdx.graphics.g2d.BitmapFont font,
                                      String s, float maxW) {
        if (Ui.textWidth(font, s) <= maxW) return s;
        while (s.length() > 1 && Ui.textWidth(font, s + "...") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    // ---------- chạm ----------

    private void onTouch(float x, float y) {
        // nút Hoàn thành
        if (x >= RX && x <= RX + RW && y >= 84 && y <= 156) {
            submit();
            return;
        }
        // hai ô giới tính
        if (y >= 540 && y <= 596) {
            if (x >= RX && x <= RX + 320) { setGender(1); return; }
            if (x >= RX + 336 && x <= RX + RW) { setGender(2); return; }
        }
        // nút qua lại của 3 hàng đồ
        for (int r = 0; r < 3; r++) {
            if (y >= BAR_Y[r] + 5 && y <= BAR_Y[r] + 59) {
                if (x >= RX + 5 && x <= RX + 59) { cycle(r, -1); return; }
                if (x >= RX + RW - 59 && x <= RX + RW - 5) { cycle(r, 1); return; }
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
