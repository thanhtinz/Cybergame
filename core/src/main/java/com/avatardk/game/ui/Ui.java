package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.HashMap;
import java.util.Map;

/** Font tiếng Việt qua FreeType (Nunito/Baloo 2 — hai font này có subset tiếng Việt đầy đủ,
 *  cùng bộ font dự án LTS dùng) + icon Font Awesome thật (font chính chủ, theo yêu cầu
 *  của chủ dự án: icon dùng glyph FA chứ không vẽ tay) + tiện ích vẽ panel bo góc,
 *  nút gradient, chấm tròn bằng Pixmap. Không dùng emoji. */
public final class Ui {
    public static final float W = 1280f, H = 720f;

    /** Hệ màu nút thống nhất mọi panel (user chốt 2026-11-02):
     *  nút TẮT = đỏ, nút HUỶ / QUAY LẠI = đen, nút XÁC NHẬN = cam
     *  (gradient EntryUi.ORANGE_TOP → ORANGE_BOT). */
    public static final Color BTN_CLOSE = new Color(0.85f, 0.17f, 0.17f, 1f);
    public static final Color BTN_CANCEL = new Color(0.03f, 0.03f, 0.04f, 0.95f);

    static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        + "àáâãèéêìíĩòóôõùúũýăđơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"
        + "ÀÁÂÃÈÉÊÌÍĨÒÓÔÕÙÚŨÝĂĐƠƯẠẢẤẦẨẪẬẮẰẲẴẶẸẺẼẾỀỂỄỆỈỊỌỎỐỒỔỖỘỚỜỞỠỢỤỦỨỪỬỮỰỲỴỶỸ"
        + " .,;:!?()[]{}+-*/=%'\"<>|_@#&•·—–…";

    // Glyph Font Awesome (solid) dùng trong game — mã private-use của FA Free 6.
    public static final String FA_BELL = "\uf0f3";
    public static final String FA_TRASH = "\uf2ed";
    public static final String FA_HEADSET = "\uf590";
    public static final String FA_SUN = "\uf185";
    public static final String FA_USER = "\uf007";
    public static final String FA_LOCK = "\uf023";
    public static final String FA_EYE = "\uf06e";
    public static final String FA_EYE_SLASH = "\uf070";
    public static final String FA_CHECK = "\uf00c";
    public static final String FA_XMARK = "\uf00d";
    public static final String FA_SEND = "\uf1d8";
    public static final String FA_EXPAND = "\uf065";
    public static final String FA_COMPRESS = "\uf066";
    public static final String FA_FACE_SMILE = "\uf118";
    public static final String FA_MICROPHONE = "\uf130";
    public static final String FA_PLAY = "\uf04b";
    public static final String FA_STOP = "\uf04d";
    // Bộ icon dùng làm emoji trong chat (không có icon mặt — mặt đã có
    // sheet đổi biểu cảm riêng cho nhân vật, chủ dự án chốt 2026-10-31)
    public static final String FA_HEART = "\uf004";
    public static final String FA_STAR = "\uf005";
    public static final String FA_THUMBS_UP = "\uf164";
    public static final String FA_THUMBS_DOWN = "\uf165";
    public static final String FA_FIRE = "\uf06d";
    public static final String FA_GIFT = "\uf06b";
    public static final String FA_BULLHORN = "\uf0a1";
    public static final String FA_DOWNLOAD = "\uf019";
    public static final String FA_CLOCK = "\uf017";
    public static final String FA_CALENDAR = "\uf073"; // panel nhân vật kiểu mockup
    public static final String FA_HEART_CRACK = "\uf7a9";
    public static final String FA_SHIELD_HALVED = "\uf3ed";
    public static final String FA_COINS = "\uf51e";
    public static final String FA_MUSIC = "\uf001";
    public static final String FA_BOLT = "\uf0e7";
    public static final String FA_COMMENT_DOTS = "\uf4ad";
    public static final String FA_USER_PLUS = "\uf234";
    public static final String FA_HAND_FIST = "\uf6de";
    public static final String FA_HOUSE = "\uf015";
    public static final String FA_CIRCLE_INFO = "\uf05a";
    public static final String FA_CROWN = "\uf521";
    public static final String FA_MARS = "\uf222";
    public static final String FA_VENUS = "\uf221";
    public static final String FA_BOX_ARCHIVE = "\uf187"; // ô Rương trong menu burger
    public static final String FA_STORE = "\uf54e"; // ô Shop trong menu burger
    public static final String FA_GEAR = "\uf013"; // ô Cài đặt trong menu burger
    // Cụm điều khiển của panel Nhạc (tính năng mới nghe nhạc YouTube)
    public static final String FA_PAUSE = "\uf04c";
    public static final String FA_FORWARD_STEP = "\uf051";
    public static final String FA_BACKWARD_STEP = "\uf048";
    public static final String FA_SHUFFLE = "\uf074";
    public static final String FA_REPEAT = "\uf363";
    public static final String FA_MAGNIFYING_GLASS = "\uf002";
    public static final String FA_VOLUME_HIGH = "\uf028";
    public static final String FA_LIST = "\uf03a";
    public static final String FA_CHEVRON_LEFT = "\uf053"; // nút ‹ ở panel Cài đặt
    public static final String FA_CHEVRON_RIGHT = "\uf054"; // nút › ở panel Cài đặt
    public static final String FA_ARROW_RIGHT = "\uf061"; // nút Di chuyển ở panel rương
    public static final String FA_ARROW_LEFT = "\uf060";
    public static final String FA_ARROW_UP = "\uf062"; // đệm hướng câu cá (QTE)
    public static final String FA_ARROW_DOWN = "\uf063"; // đệm hướng câu cá (QTE)
    public static final String FA_PLUS = "\uf067"; // cụm zoom bản đồ
    public static final String FA_MINUS = "\uf068";
    public static final String FA_LOCATION_DOT = "\uf3c5"; // ghim điểm đến trên bản đồ
    public static final String FA_LOCATION_CROSSHAIRS = "\uf601"; // nút về vị trí của bạn
    public static final String FA_ENVELOPE = "\uf0e0"; // panel Tin nhắn
    public static final String FA_USER_GROUP = "\uf500"; // panel Bạn bè
    public static final String FA_ROTATE_RIGHT = "\uf2f1"; // nút làm mới danh sách bạn
    public static final String FA_GEM = "\uf3a5";
    public static final String FA_TROPHY = "\uf091";
    public static final String FA_CAKE = "\uf1fd";
    public static final String FA_PIZZA = "\uf818";
    public static final String FA_ICE_CREAM = "\uf810";
    public static final String FA_CAT = "\uf6be";
    public static final String FA_DOG = "\uf6d3";
    public static final String FA_ROCKET = "\uf135";
    public static final String FA_DICE = "\uf522"; // xúc xắc random tên (màn Tạo nhân vật)
    static final String FA_SOLID_CHARS = FA_BELL + FA_TRASH + FA_HEADSET + FA_SUN
        + FA_USER + FA_LOCK + FA_EYE + FA_EYE_SLASH + FA_CHECK + FA_XMARK + FA_SEND
        + FA_EXPAND + FA_COMPRESS + FA_FACE_SMILE + FA_MICROPHONE + FA_PLAY + FA_STOP
        + FA_HEART + FA_STAR + FA_THUMBS_UP + FA_THUMBS_DOWN + FA_FIRE + FA_GIFT
        + FA_MUSIC + FA_BOLT + FA_CROWN + FA_GEM + FA_TROPHY + FA_CAKE + FA_PIZZA
        + FA_ICE_CREAM + FA_CAT + FA_DOG + FA_ROCKET
        + FA_COMMENT_DOTS + FA_USER_PLUS + FA_HAND_FIST + FA_HOUSE + FA_CIRCLE_INFO
        + FA_MARS + FA_VENUS + FA_BOX_ARCHIVE + FA_ARROW_RIGHT + FA_ARROW_LEFT
        + FA_ARROW_UP + FA_ARROW_DOWN
        + FA_PLUS + FA_MINUS + FA_LOCATION_DOT + FA_LOCATION_CROSSHAIRS + FA_STORE
        + FA_GEAR + FA_CHEVRON_LEFT + FA_CHEVRON_RIGHT
        + FA_PAUSE + FA_FORWARD_STEP + FA_BACKWARD_STEP + FA_SHUFFLE + FA_REPEAT
        + FA_MAGNIFYING_GLASS + FA_VOLUME_HIGH + FA_LIST
        + FA_ENVELOPE + FA_USER_GROUP + FA_ROTATE_RIGHT
        + FA_BULLHORN + FA_DOWNLOAD + FA_CLOCK + FA_COINS + FA_DICE
        + FA_CALENDAR + FA_HEART_CRACK + FA_SHIELD_HALVED;
    // Glyph Font Awesome (brands)
    public static final String FA_GOOGLE = "\uf1a0";
    public static final String FA_APPLE = "\uf179";
    static final String FA_BRANDS_CHARS = FA_GOOGLE + FA_APPLE;

    public static BitmapFont fontTitle, fontBig, font, fontSmall;
    public static BitmapFont fontLogo, fontBaloo36, fontBaloo30, fontTiny;
    public static BitmapFont fa20, fa26, fa30, faBrands26, faBrands30;

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    public static void initFonts() {
        fontTitle = makeFont("fonts/Baloo2-ExtraBold.ttf", 44);
        fontBig = makeFont("fonts/Nunito-ExtraBold.ttf", 30);
        font = makeFont("fonts/Nunito-ExtraBold.ttf", 24);
        fontSmall = makeFont("fonts/Nunito-Bold.ttf", 19);
        fontLogo = makeFont("fonts/Baloo2-ExtraBold.ttf", 84);
        fontBaloo36 = makeFont("fonts/Baloo2-ExtraBold.ttf", 36);
        fontBaloo30 = makeFont("fonts/Baloo2-ExtraBold.ttf", 30);
        fontTiny = makeFont("fonts/Nunito-Bold.ttf", 15);
        fa20 = makeFaFont("fonts/fa-solid-900.ttf", 20, FA_SOLID_CHARS);
        fa26 = makeFaFont("fonts/fa-solid-900.ttf", 26, FA_SOLID_CHARS);
        fa30 = makeFaFont("fonts/fa-solid-900.ttf", 30, FA_SOLID_CHARS);
        faBrands26 = makeFaFont("fonts/fa-brands-400.ttf", 26, FA_BRANDS_CHARS);
        faBrands30 = makeFaFont("fonts/fa-brands-400.ttf", 30, FA_BRANDS_CHARS);
    }

    private static BitmapFont makeFont(String path, int size) {
        return FontFactory.create(path, size, CHARS);
    }

    private static BitmapFont makeFaFont(String path, int size, String glyphs) {
        return FontFactory.create(path, size, glyphs);
    }


    /** Vẽ chữ căn giữa theo tâm ngang cx, baseline y cho trước. */
    public static void centered(SpriteBatch batch, BitmapFont f, String text, float cx, float baselineY, Color color) {
        LAYOUT.setText(f, text);
        f.setColor(color);
        f.draw(batch, text, cx - LAYOUT.width / 2f, baselineY);
    }

    /** Vẽ 1 glyph Font Awesome căn giữa tại (cx, cy). */
    public static void faIcon(SpriteBatch batch, BitmapFont f, String glyph, float cx, float cy, Color color) {
        LAYOUT.setText(f, glyph);
        f.setColor(color);
        f.draw(batch, glyph, cx - LAYOUT.width / 2f, cy + LAYOUT.height / 2f);
    }

    public static float textWidth(BitmapFont f, String text) {
        LAYOUT.setText(f, text);
        return LAYOUT.width;
    }

    private static final Map<String, Texture> rectCache = new HashMap<>();

    public static Texture roundedRect(int w, int h, int r, Color fill, Color rim) {
        String key = w + "x" + h + "r" + r + "f" + fill.toIntBits() + "o" + (rim == null ? 0 : rim.toIntBits());
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        // Blending.None: hai lượt vẽ rim/fill phải THAY pixel, không cộng alpha —
        // SourceOver làm 4 hình tròn ở góc bị cộng sáng thành "chấm tròn" trên kính
        // (chủ dự án yêu cầu bỏ hẳn các chấm này).
        pm.setBlending(Pixmap.Blending.None);
        if (rim != null) {
            pm.setColor(rim);
            fillRounded(pm, 0, 0, w, h, r);
            pm.setColor(fill);
            fillRounded(pm, 2, 2, w - 4, h - 4, Math.max(2, r - 2));
        } else {
            pm.setColor(fill);
            fillRounded(pm, 0, 0, w, h, r);
        }
        Texture t = new Texture(pm);
        pm.dispose();
        rectCache.put(key, t);
        return t;
    }

    /** Nút/panel gradient dọc từ màu trên xuống màu dưới, bo góc r. */
    public static Texture gradientRect(int w, int h, int r, Color top, Color bottom) {
        String key = "g" + w + "x" + h + "r" + r + "t" + top.toIntBits() + "b" + bottom.toIntBits();
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        Color c = new Color();
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0 : (float) y / (h - 1); // Pixmap y=0 = mép TRÊN khi vẽ ra
            c.set(top).lerp(bottom, t);
            pm.setColor(c);
            int inset = cornerInset(y, h, r);
            pm.fillRectangle(inset, y, w - 2 * inset, 1);
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        rectCache.put(key, tex);
        return tex;
    }

    /** Độ thụt ngang của góc bo tại hàng y (tọa độ Pixmap, y=0 ở trên). */
    private static int cornerInset(int y, int h, int r) {
        int dy = y < r ? r - y : (y >= h - r ? y - (h - r - 1) : -1);
        if (dy < 0) return 0;
        double dx = r - Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy));
        return Math.max(0, (int) Math.ceil(dx));
    }

    /** Chỉ viền bo góc (giữa trong suốt) để đè lên nền gradient/màu khác. */
    public static Texture outlineRect(int w, int h, int r, Color rim) {
        String key = "ol" + w + "x" + h + "r" + r + "o" + rim.toIntBits();
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(rim);
        fillRounded(pm, 0, 0, w, h, r);
        pm.setColor(0, 0, 0, 0);
        fillRounded(pm, 2, 2, w - 4, h - 4, Math.max(2, r - 2));
        Texture t = new Texture(pm);
        pm.dispose();
        rectCache.put(key, t);
        return t;
    }

    /** Chấm tròn đặc (chấm trạng thái server…). */
    public static Texture dot(int size, Color fill) {
        return roundedRect(size, size, size / 2, fill, null);
    }

    /** Dải tối gradient ở mép trên màn hình (để logo/nút nổi trên tranh banner). */
    public static Texture topShade(int w, int h, float maxAlpha) {
        String key = "shade" + w + "x" + h + "a" + maxAlpha;
        Texture cached = rectCache.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0 : (float) y / (h - 1); // y=0 trên cùng = tối nhất
            pm.setColor(0.02f, 0.04f, 0.08f, maxAlpha * (1f - t));
            pm.fillRectangle(0, y, w, 1);
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        rectCache.put(key, tex);
        return tex;
    }

    private static void fillRounded(Pixmap pm, int x, int y, int w, int h, int r) {
        // BẪY ĐÃ DÍNH (2026-11-03, tour chụp treo 688s CPU): rect nhỏ
        // (vd 12×6 của phao câu, vòng sóng cá cắn) làm h - 2r ÂM →
        // fillRect native chạy loạn. Kẹp r theo cạnh nhỏ nhất và bỏ
        // qua rect không hợp lệ.
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            pm.fillRectangle(x, y, w, h);
            return;
        }
        pm.fillRectangle(x + r, y, w - 2 * r, h);
        pm.fillRectangle(x, y + r, w, h - 2 * r);
        pm.fillCircle(x + r, y + r, r);
        pm.fillCircle(x + w - r - 1, y + r, r);
        pm.fillCircle(x + r, y + h - r - 1, r);
        pm.fillCircle(x + w - r - 1, y + h - r - 1, r);
    }

    public static void dispose() {
        for (Texture t : rectCache.values()) t.dispose();
        rectCache.clear();
        if (fontTitle != null) fontTitle.dispose();
        if (fontBig != null) fontBig.dispose();
        if (font != null) font.dispose();
        if (fontSmall != null) fontSmall.dispose();
        if (fontLogo != null) fontLogo.dispose();
        if (fontBaloo36 != null) fontBaloo36.dispose();
        if (fontBaloo30 != null) fontBaloo30.dispose();
        if (fontTiny != null) fontTiny.dispose();
        if (fa20 != null) fa20.dispose();
        if (fa26 != null) fa26.dispose();
        if (fa30 != null) fa30.dispose();
        if (faBrands26 != null) faBrands26.dispose();
        if (faBrands30 != null) faBrands30.dispose();
    }
}
