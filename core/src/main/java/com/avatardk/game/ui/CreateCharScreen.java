package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.data.ImgInfo;
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
 * Màn TẠO NHÂN VẬT — port của RegisterScr.cs gốc, mở rộng theo lệnh
 * user: sau khi đăng nhập mà nhân vật chưa được tạo (gender = 0 trong
 * gói LOGIN_SUCESS), người chơi vào đây TOÀN MÀN HÌNH trên nền trời
 * sáng: đặt TÊN NHÂN VẬT (ô nhập riêng, lưu vào players.name — bản port
 * bổ sung, gốc không có), chọn giới tính (1 = nam, 2 = nữ), và chọn
 * trang phục khởi đầu bằng các Ô ICON có sẵn — mỗi mục Tóc (zOrder 50),
 * Áo (20), Quần (10) đúng 3 kiểu cấp 0 (kiểu mặc định của gốc + 2 kiểu
 * kế tiếp). Doll xem trước mặc đúng bộ đang chọn và hiện tên đang gõ.
 * Hoàn thành gửi gói CRE_CHARACTER (-35): seriPart theo thứ tự server
 * gốc kiểm tra [quần, áo, thân (0), mặt (4), tóc] + UTF tên ở cuối.
 */
public class CreateCharScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);

    private static final Color LABEL_DARK = new Color(0.13f, 0.26f, 0.40f, 1f);
    private static final Color TITLE_DARK = new Color(0.05f, 0.16f, 0.30f, 1f);
    private static final Color HINT = new Color(0.25f, 0.38f, 0.50f, 1f);
    private static final Color BAR_FILL = new Color(0.06f, 0.11f, 0.18f, 1f);
    private static final Color FIELD_FILL = new Color(0.95f, 0.97f, 0.985f, 1f);
    private static final Color FIELD_TEXT = new Color(0.16f, 0.18f, 0.22f, 1f);
    private static final Color FIELD_HINT = new Color(0.55f, 0.60f, 0.65f, 1f);
    private static final Color ICON_GREY = new Color(0.43f, 0.49f, 0.57f, 1f);
    private static final Color ERR_RED = new Color(0.85f, 0.20f, 0.15f, 1f);
    private static final Color CELL_NAME = new Color(0.85f, 0.90f, 0.97f, 1f);

    /** Ô xem trước bên trái. */
    private static final float IX = 64, IY = 96, IW = 440, IH = 526;
    /** Vùng điều khiển bên phải. */
    private static final float RX = 560, RW = 656;
    /** Y (mép dưới) của 3 hàng ô đồ: Tóc / Áo / Quần. */
    private static final int[] ROW_Y = {362, 258, 148};
    private static final float CELL_W = 210;

    private int gender = 1;
    private final List<PartData> hairOpts = new ArrayList<>();
    private final List<PartData> clothOpts = new ArrayList<>();
    private final List<PartData> pantOpts = new ArrayList<>();
    private int hairSel, clothSel, pantSel;
    private final StringBuilder nameField = new StringBuilder();
    private PaperDoll doll;
    private String status = "";
    private boolean statusError = false;
    private boolean busy = false;
    private float elapsed = 0f;

    public CreateCharScreen(AvatarDkGame game) {
        this.game = game;
    }

    /** Bản trưng bày cho harness chụp ảnh: nhân vật nữ, tên mẫu, chọn
     *  ô thứ hai ở mỗi mục để thấy rõ trạng thái đã chọn. */
    public void debugShowcase() {
        setGender(2);
        nameField.setLength(0);
        nameField.append("Linh Chi");
        hairSel = 1;
        clothSel = 2;
        pantSel = 1;
        refreshDoll();
    }

    /** Chuẩn bị lại từ đầu (LoginScreen gọi trước khi chuyển màn). */
    public void prepare() {
        gender = 1;
        busy = false;
        status = "";
        statusError = false;
        nameField.setLength(0);
        String pre = game.playerName == null ? "" : game.playerName.trim();
        if (pre.length() > 16) pre = pre.substring(0, 16);
        nameField.append(pre);
        buildOptions();
    }

    @Override
    public void show() {
        if (hairOpts.isEmpty()) prepare();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector3 v = viewport.unproject(new Vector3(sx, sy, 0));
                onTouch(v.x, v.y);
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACKSPACE) {
                    if (nameField.length() > 0) nameField.deleteCharAt(nameField.length() - 1);
                    return true;
                }
                if (keycode == Input.Keys.ENTER) {
                    submit();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(char c) {
                if (c < 32 || c == 127) return false;
                if (nameField.length() < 16) nameField.append(c);
                return true;
            }
        });
    }

    // ---------- dữ liệu ----------

    /** Đúng 3 kiểu cho một mục: kiểu mặc định của gốc + 2 kiểu kế tiếp
     *  trong danh sách cấp 0 đúng giới tính (chỉ part vẽ được). */
    private List<PartData> threeOptions(int zorder, int defaultId) {
        List<PartData> all = new ArrayList<>();
        for (PartData p : game.data.parts.values()) {
            if (p.level != 0 || p.isDynamic() || p.name == null || p.name.isEmpty()) continue;
            if (p.gender != gender && p.gender != 0) continue;
            if (p.zorder == zorder) all.add(p);
        }
        all.sort(Comparator.comparingInt(p -> p.id));
        List<PartData> out = new ArrayList<>(3);
        if (all.isEmpty()) return out;
        int di = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id == defaultId) { di = i; break; }
        for (int k = 0; k < Math.min(3, all.size()); k++) out.add(all.get((di + k) % all.size()));
        return out;
    }

    private void buildOptions() {
        hairOpts.clear();
        clothOpts.clear();
        pantOpts.clear();
        hairOpts.addAll(threeOptions(50, gender == 1 ? 14 : 49));
        clothOpts.addAll(threeOptions(20, 88));
        pantOpts.addAll(threeOptions(10, 89));
        hairSel = 0;
        clothSel = 0;
        pantSel = 0;
        refreshDoll();
    }

    private void setGender(int g) {
        if (gender == g || busy) return;
        gender = g;
        buildOptions();
    }

    private void select(int row, int idx) {
        if (busy) return;
        if (row == 0) hairSel = idx;
        if (row == 1) clothSel = idx;
        if (row == 2) pantSel = idx;
        refreshDoll();
    }

    /** seriPart theo đúng thứ tự server gốc: [quần, áo, thân 0, mặt 4, tóc]. */
    private List<Integer> currentParts() {
        List<Integer> parts = new ArrayList<>(5);
        parts.add(pantOpts.isEmpty() ? 89 : pantOpts.get(pantSel).id);
        parts.add(clothOpts.isEmpty() ? 88 : clothOpts.get(clothSel).id);
        parts.add(0);
        parts.add(4);
        parts.add(hairOpts.isEmpty() ? (gender == 1 ? 14 : 49) : hairOpts.get(hairSel).id);
        return parts;
    }

    private void refreshDoll() {
        doll = game.newDoll(currentParts());
    }

    private String charName() {
        return nameField.toString().trim();
    }

    // ---------- mạng ----------

    private void submit() {
        if (busy) return;
        String name = charName();
        if (name.length() < 3 || name.length() > 16) {
            status = "Tên nhân vật cần từ 3 đến 16 ký tự.";
            statusError = true;
            return;
        }
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
            AvatarProtocol.createCharacter(conn, gender, currentParts(), name);
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
                        game.playerName = charName();
                        if (game.player != null) {
                            List<Integer> parts = currentParts();
                            game.player.gender = gender;
                            game.player.username = charName();
                            game.player.wearing = new int[parts.size()];
                            for (int i = 0; i < parts.size(); i++) {
                                game.player.wearing[i] = parts.get(i);
                            }
                            game.world.applyPlayerInfo(game.player);
                        }
                        game.setScreen(game.serverSelect);
                    } else {
                        status = "Tạo nhân vật thất bại, hãy thử lại.";
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
        Gdx.gl.glClearColor(0.55f, 0.78f, 0.94f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawSky();
        drawHeader();
        drawPreview();
        drawControls();
        drawButton();
        batch.end();
    }

    /** Nền trời ban ngày: gradient 3 tông xanh trời. */
    private void drawSky() {
        batch.draw(Ui.gradientRect((int) Ui.W, 360, 0,
            new Color(0.25f, 0.55f, 0.85f, 1f), new Color(0.55f, 0.78f, 0.94f, 1f)), 0, 360);
        batch.draw(Ui.gradientRect((int) Ui.W, 360, 0,
            new Color(0.55f, 0.78f, 0.94f, 1f), new Color(0.85f, 0.94f, 0.99f, 1f)), 0, 0);
    }

    private void drawHeader() {
        Ui.faIcon(batch, Ui.fa30, Ui.FA_USER_PLUS, 88, 681,
            new Color(0.90f, 0.45f, 0.05f, 1f));
        Ui.fontBaloo36.setColor(TITLE_DARK);
        Ui.fontBaloo36.draw(batch, "Tạo nhân vật", 116, 692);
        Ui.fontSmall.setColor(LABEL_DARK);
        Ui.fontSmall.draw(batch, "Chọn giới tính, đặt tên và trang phục khởi đầu", 117, 662);
    }

    private void drawPreview() {
        batch.draw(Ui.roundedRect((int) IW, (int) IH, 20, BAR_FILL, EntryUi.PANEL_RIM), IX, IY);
        String name = charName();
        if (name.isEmpty()) {
            Ui.centered(batch, Ui.font, "Tên nhân vật", IX + IW / 2f, IY + IH - 28,
                new Color(0.6f, 0.68f, 0.78f, 1f));
        } else {
            Ui.centered(batch, Ui.font, name, IX + IW / 2f, IY + IH - 28,
                new Color(1f, 0.85f, 0.45f, 1f));
        }
        // sàn sáng nhẹ dưới chân + bóng + doll xem trước (nhấp nhô nhẹ)
        batch.draw(Ui.roundedRect((int) IW - 48, 10, 5,
            new Color(1f, 1f, 1f, 0.06f), null), IX + 24, 134);
        float feetX = IX + IW / 2f, feetY = 158 + (float) Math.sin(elapsed * 2.2f) * 2.5f;
        batch.draw(Ui.roundedRect(170, 16, 8, new Color(0f, 0f, 0f, 0.25f), null),
            feetX - 85, 142);
        if (doll != null) {
            game.dollRenderer.draw(batch, doll, 0, feetX, feetY, 7.2f, false);
        }
    }

    private void drawControls() {
        // tên nhân vật
        Ui.fontSmall.setColor(LABEL_DARK);
        Ui.fontSmall.draw(batch, "Tên nhân vật", RX, 626);
        drawNameField();
        // giới tính
        Ui.fontSmall.setColor(LABEL_DARK);
        Ui.fontSmall.draw(batch, "Giới tính", RX, 534);
        drawGenderPill(RX, 1, "Nam", Ui.FA_MARS);
        drawGenderPill(RX + 336, 2, "Nữ", Ui.FA_VENUS);
        // ba hàng ô đồ
        drawRow(0, "Tóc", hairOpts, hairSel);
        drawRow(1, "Áo", clothOpts, clothSel);
        drawRow(2, "Quần", pantOpts, pantSel);
        // trạng thái / gợi ý
        if (!status.isEmpty()) {
            Ui.fontSmall.setColor(statusError ? ERR_RED : LABEL_DARK);
            Ui.fontSmall.draw(batch, status, RX, 126);
        } else {
            Ui.fontSmall.setColor(HINT);
            Ui.fontSmall.draw(batch,
                "Trang phục khởi đầu có thể thay đổi bất cứ lúc nào trong game.", RX, 126);
        }
    }

    private void drawNameField() {
        batch.draw(Ui.roundedRect((int) RW, 52, 12, FIELD_FILL,
            new Color(1f, 0.62f, 0.20f, 1f)), RX, 552);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_USER, RX + 26, 578, ICON_GREY);
        String name = nameField.toString();
        if (name.isEmpty()) {
            Ui.font.setColor(FIELD_HINT);
            Ui.font.draw(batch, "Nhập tên nhân vật", RX + 52, 586);
        } else {
            Ui.font.setColor(FIELD_TEXT);
            Ui.font.draw(batch, name, RX + 52, 586);
            // con trỏ nhấp nháy ở cuối tên
            if (((int) (elapsed * 2)) % 2 == 0) {
                float cx = RX + 52 + Ui.textWidth(Ui.font, name) + 3;
                batch.draw(Ui.roundedRect(3, 24, 1, FIELD_TEXT, null), cx, 566);
            }
        }
    }

    private void drawGenderPill(float x, int g, String label, String icon) {
        float w = 320, y = 470, h = 44;
        boolean sel = gender == g;
        if (sel) {
            batch.draw(Ui.gradientRect((int) w, (int) h, 12,
                EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), x, y);
        } else {
            batch.draw(Ui.roundedRect((int) w, (int) h, 12, BAR_FILL, EntryUi.PANEL_RIM), x, y);
        }
        float tw = Ui.textWidth(Ui.font, label);
        float gx = x + w / 2f - (26 + tw) / 2f;
        Ui.faIcon(batch, Ui.fa20, icon, gx + 13, y + 22,
            sel ? Color.WHITE : CELL_NAME);
        Ui.font.setColor(sel ? Color.WHITE : CELL_NAME);
        Ui.font.draw(batch, label, gx + 28, y + 30);
    }

    private void drawRow(int row, String label, List<PartData> opts, int sel) {
        float labelY = row == 0 ? 458 : row == 1 ? 344 : 240;
        Ui.fontSmall.setColor(LABEL_DARK);
        Ui.fontSmall.draw(batch, label, RX, labelY);
        float y = ROW_Y[row];
        for (int i = 0; i < opts.size(); i++) {
            float x = RX + i * (CELL_W + 13);
            boolean isSel = i == sel;
            if (isSel) {
                batch.draw(Ui.gradientRect((int) CELL_W, 64, 12,
                    EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), x, y);
            } else {
                batch.draw(Ui.roundedRect((int) CELL_W, 64, 12, BAR_FILL, EntryUi.PANEL_RIM), x, y);
            }
            drawPartIcon(opts.get(i).id, x + CELL_W / 2f, y + 41, 34);
            String nm = trimToWidth(Ui.fontTiny, opts.get(i).name, CELL_W - 16);
            Ui.centered(batch, Ui.fontTiny, nm, x + CELL_W / 2f, y + 17,
                isSel ? Color.WHITE : CELL_NAME);
        }
    }

    /** Icon của part tĩnh (đúng Part.cs gốc: lấy từ imgdata theo icon). */
    private void drawPartIcon(int partId, float cx, float cy, float maxBox) {
        PartData pd = game.data.part(partId);
        if (pd == null) return;
        ImgInfo ii = game.data.imgByItemId.get(pd.icon);
        if (ii == null) return;
        Texture tex = game.texture("big/" + ii.bigId + ".png");
        TextureRegion reg = new TextureRegion(tex, ii.x * 2, ii.y * 2, ii.w * 2, ii.h * 2);
        float sc = Math.min(maxBox / Math.max(1, ii.w * 2), maxBox / Math.max(1, ii.h * 2));
        float dw = ii.w * 2 * sc, dh = ii.h * 2 * sc;
        batch.draw(reg, cx - dw / 2f, cy - dh / 2f, dw, dh);
    }

    private void drawButton() {
        batch.draw(Ui.gradientRect(1152, 56, 16,
            EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), 64, 24);
        if (busy) {
            Ui.centered(batch, Ui.fontBaloo30, "Đang tạo nhân vật…", Ui.W / 2f, 61, Color.WHITE);
        } else {
            float tw = Ui.textWidth(Ui.fontBaloo30, "Hoàn thành");
            Ui.faIcon(batch, Ui.fa20, Ui.FA_CHECK, Ui.W / 2f - tw / 2f - 22, 52, Color.WHITE);
            Ui.centered(batch, Ui.fontBaloo30, "Hoàn thành", Ui.W / 2f, 61, Color.WHITE);
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
        // nút Hoàn thành (trải cả chiều ngang ở đáy)
        if (x >= 64 && x <= 1216 && y >= 24 && y <= 80) {
            submit();
            return;
        }
        // hai ô giới tính
        if (y >= 470 && y <= 514) {
            if (x >= RX && x <= RX + 320) { setGender(1); return; }
            if (x >= RX + 336 && x <= RX + RW) { setGender(2); return; }
        }
        // các ô đồ: chạm ô nào chọn ô đó
        for (int r = 0; r < 3; r++) {
            if (y >= ROW_Y[r] && y <= ROW_Y[r] + 64) {
                int n = r == 0 ? hairOpts.size() : r == 1 ? clothOpts.size() : pantOpts.size();
                for (int i = 0; i < n; i++) {
                    float cx = RX + i * (CELL_W + 13);
                    if (x >= cx && x <= cx + CELL_W) { select(r, i); return; }
                }
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
