package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Phần dùng chung của luồng vào game "Thị Trấn Mặt Trời" (đã chốt qua mockup v3):
 * tranh banner vẽ riêng, logo trên banner, 3 nút dọc góc phải trên
 * (Thông báo / Xoá cache / Hỗ trợ — icon Font Awesome thật), panel thông tin
 * và dòng toast. BannerScreen và ServerSelectScreen dùng chung để hai màn khớp nhau.
 */
public final class EntryUi {
    public static final String GAME_NAME = "Thị Trấn Mặt Trời";
    public static final String GAME_NAME_EN = "S U N N Y   T O W N";
    public static final String TAGLINE = "Gặp gỡ · Kết bạn · Vui cả ngày";
    public static final String VERSION = "Phiên bản 2.5.8";

    public static final Color GOLD = new Color(1f, 0.77f, 0.25f, 1f);
    public static final Color ORANGE_TOP = new Color(0.965f, 0.52f, 0.125f, 1f);
    public static final Color ORANGE_BOT = new Color(0.835f, 0.345f, 0.03f, 1f);
    public static final Color PANEL_FILL = new Color(0.03f, 0.07f, 0.13f, 0.92f);
    public static final Color PANEL_RIM = new Color(0.55f, 0.69f, 0.86f, 1f);
    private static final Color UTIL_FILL = new Color(0.03f, 0.06f, 0.11f, 0.72f);
    private static final Color UTIL_RIM = new Color(0.82f, 0.89f, 0.97f, 0.8f);
    private static final Color SHADOW = new Color(0.06f, 0.16f, 0.24f, 0.9f);

    /** Tâm 3 nút tiện ích (tọa độ LibGDX, y từ đáy lên). */
    public static final float UTIL_X = Ui.W - 62f;
    public static final float[] UTIL_CY = {Ui.H - 53f, Ui.H - 155f, Ui.H - 257f};
    private static final String[] UTIL_GLYPHS = {Ui.FA_BELL, Ui.FA_TRASH, Ui.FA_HEADSET};
    private static final String[] UTIL_LABELS = {"Thông báo", "Xoá cache", "Hỗ trợ"};

    private static Texture bannerTex;
    /** Panel thông tin đang mở: 0 = đóng, 1 = Thông báo, 2 = Hỗ trợ. */
    public static int infoPanel = 0;
    private static String toast = null;
    private static long toastUntilMs = 0;

    private EntryUi() {}

    public static Texture banner() {
        if (bannerTex == null) {
            bannerTex = new Texture(Gdx.files.internal("banner.jpg"));
            bannerTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        return bannerTex;
    }

    public static void drawBanner(SpriteBatch batch) {
        batch.draw(banner(), 0, 0, Ui.W, Ui.H);
    }

    /** Logo + tagline + 3 nút dọc, giống hệt màn banner đã duyệt. */
    public static void drawHeader(SpriteBatch batch) {
        batch.draw(Ui.topShade((int) Ui.W, 300, 0.47f), 0, Ui.H - 300);
        // logo có viền nâu: vẽ 4 bản lệch 2px rồi bản chính vàng ở giữa
        String name = GAME_NAME;
        float w = Ui.textWidth(Ui.fontLogo, name);
        float x = Ui.W / 2f - w / 2f, base = Ui.H - 62;
        Ui.fontLogo.setColor(new Color(0.35f, 0.16f, 0f, 1f));
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dy = -2; dy <= 2; dy += 2) {
                if (dx != 0 || dy != 0) Ui.fontLogo.draw(batch, name, x + dx, base + dy);
            }
        }
        Ui.fontLogo.setColor(GOLD);
        Ui.fontLogo.draw(batch, name, x, base);
        Ui.centered(batch, Ui.font, GAME_NAME_EN, Ui.W / 2f, Ui.H - 160, Color.WHITE);
        Ui.centered(batch, Ui.fontBig, TAGLINE, Ui.W / 2f, Ui.H - 200, Color.WHITE);
        drawUtilButtons(batch);
    }

    public static void drawUtilButtons(SpriteBatch batch) {
        for (int i = 0; i < 3; i++) {
            float cy = UTIL_CY[i];
            batch.draw(Ui.roundedRect(54, 54, 27, UTIL_FILL, UTIL_RIM), UTIL_X - 27, cy - 27);
            Ui.faIcon(batch, Ui.fa26, UTIL_GLYPHS[i], UTIL_X, cy, Color.WHITE);
            Ui.centered(batch, Ui.fontTiny, UTIL_LABELS[i], UTIL_X, cy - 42, Color.WHITE);
        }
    }

    /** Trả về chỉ số nút tiện ích bị chạm (0/1/2) hoặc -1. */
    public static int utilHit(float x, float y) {
        for (int i = 0; i < 3; i++) {
            float dx = x - UTIL_X, dy = y - UTIL_CY[i];
            if (dx * dx + dy * dy <= 32f * 32f) return i;
        }
        return -1;
    }

    /** Xử lý chạm vào 1 nút tiện ích; trả về true nếu đã xử lý. */
    public static boolean onUtil(AvatarDkGame game, int idx) {
        if (idx == 0) { infoPanel = 1; return true; }
        if (idx == 2) { infoPanel = 2; return true; }
        if (idx == 1) {
            game.clearTextureCache();
            showToast("Đã xoá bộ nhớ đệm");
            return true;
        }
        return false;
    }

    public static void showToast(String text) {
        toast = text;
        toastUntilMs = System.currentTimeMillis() + 2200;
    }

    public static void drawToast(SpriteBatch batch) {
        if (toast == null || System.currentTimeMillis() > toastUntilMs) return;
        float w = Ui.textWidth(Ui.fontSmall, toast) + 44;
        batch.draw(Ui.roundedRect((int) w, 40, 20,
            new Color(0.03f, 0.06f, 0.11f, 0.88f), PANEL_RIM), Ui.W / 2f - w / 2f, 18);
        Ui.centered(batch, Ui.fontSmall, toast, Ui.W / 2f, 46, Color.WHITE);
    }

    private static final float INFO_W = 620f, INFO_H = 300f;
    private static final float INFO_X = Ui.W / 2f - INFO_W / 2f, INFO_Y = Ui.H / 2f - INFO_H / 2f;

    private static final String[] NOTICE_TITLE = {"Thông báo"};
    private static final String[][] NOTICE_BODY = {{
        "• Thị Trấn Mặt Trời mở cửa đợt port Java: đã có phố, nông trại,",
        "  bản đồ thành phố và biểu cảm nhân vật.",
        "• Sự kiện cuối tuần: đua thú và quay số tại Khu giải trí.",
    }};
    private static final String[] SUPPORT_TITLE = {"Hỗ trợ"};
    private static final String[][] SUPPORT_BODY = {{
        "Gặp sự cố khi chơi? Gửi yêu cầu hỗ trợ kèm tên tài khoản,",
        "đội ngũ thị trấn sẽ phản hồi trong giờ hành chính.",
        "Kênh chính thức: trang cộng đồng Thị Trấn Mặt Trời.",
    }};

    public static void drawInfoPanel(SpriteBatch batch) {
        if (infoPanel == 0) return;
        batch.draw(Ui.roundedRect((int) Ui.W, (int) Ui.H, 0,
            new Color(0.01f, 0.03f, 0.06f, 0.45f), null), 0, 0);
        batch.draw(Ui.roundedRect((int) INFO_W, (int) INFO_H, 20, PANEL_FILL, PANEL_RIM), INFO_X, INFO_Y);
        String title = infoPanel == 1 ? NOTICE_TITLE[0] : SUPPORT_TITLE[0];
        String[] body = infoPanel == 1 ? NOTICE_BODY[0] : SUPPORT_BODY[0];
        Ui.fontBaloo36.setColor(GOLD);
        Ui.fontBaloo36.draw(batch, title, INFO_X + 28, INFO_Y + INFO_H - 28);
        Ui.faIcon(batch, Ui.fa26, Ui.FA_XMARK, INFO_X + INFO_W - 36, INFO_Y + INFO_H - 42, Color.WHITE);
        Ui.fontSmall.setColor(0.85f, 0.9f, 0.97f, 1f);
        for (int i = 0; i < body.length; i++) {
            Ui.fontSmall.draw(batch, body[i], INFO_X + 28, INFO_Y + INFO_H - 92 - i * 30);
        }
    }

    /** Panel thông tin có đang chắn chạm ở (x, y) không; chạm X hoặc ra ngoài thì đóng. */
    public static boolean infoTouch(float x, float y) {
        if (infoPanel == 0) return false;
        boolean inside = x >= INFO_X && x <= INFO_X + INFO_W && y >= INFO_Y && y <= INFO_Y + INFO_H;
        boolean closeBtn = Math.abs(x - (INFO_X + INFO_W - 36)) < 26 && Math.abs(y - (INFO_Y + INFO_H - 42)) < 26;
        if (!inside || closeBtn) infoPanel = 0;
        return true;
    }

    public static void dispose() {
        if (bannerTex != null) bannerTex.dispose();
        bannerTex = null;
    }
}
