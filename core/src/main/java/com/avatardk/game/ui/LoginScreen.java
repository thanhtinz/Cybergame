package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.net.AvatarProtocol;
import com.avatardk.game.net.GameConnection;
import com.avatardk.game.net.Msg;
import com.avatardk.game.net.PlayerInfo;
import java.io.DataInputStream;

/**
 * Màn đăng nhập / đăng ký của Thị Trấn Mặt Trời (mockup v3 đã duyệt): panel
 * gradient xanh đậm trên nền banner mờ, logo mặt trời (glyph Font Awesome),
 * tab Đăng nhập / Đăng ký dạng thanh trượt, ô nhập có icon FA + mắt hiện mật
 * khẩu, ô nhớ đăng nhập, nút gradient cam, hai nút Google / Apple (glyph
 * brands thật). Đăng nhập dùng endpoint của máy chủ đang chọn trong
 * {@link Servers}; đăng ký dùng gói REQUEST_REGISTER (-30) mà phía server của
 * bản port đã bổ sung. Đăng nhập thành công → màn chọn máy chủ.
 *
 * Google/Apple chưa nối OAuth (ngoài giao thức gốc): bấm vào sẽ báo rõ là
 * chưa khả dụng thay vì giả vờ đăng nhập.
 */
public class LoginScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);

    private static final float PW = 540f, PH = 628f;
    private static final float PX = Ui.W / 2f - PW / 2f, PY = Ui.H / 2f - PH / 2f;
    private static final float TOP = PY + PH;
    private static final Color FIELD_FILL = new Color(0.95f, 0.97f, 0.985f, 1f);
    private static final Color ICON_GREY = new Color(0.43f, 0.49f, 0.57f, 1f);
    private static final Color SUB_TEXT = new Color(0.67f, 0.75f, 0.84f, 1f);
    private static final Color LINK_BLUE = new Color(0.55f, 0.75f, 1f, 1f);
    private static final Color DARK_TEXT = new Color(0.16f, 0.18f, 0.22f, 1f);

    private final StringBuilder[] fields = {new StringBuilder(), new StringBuilder()}; // 0 = user, 1 = pass
    private int focus = 0;
    private int tab = 0; // 0 = đăng nhập, 1 = đăng ký
    private boolean showPass = false;
    private boolean remember = true;
    private String status = "";
    private boolean statusError = false;
    private boolean busy = false;
    private boolean pendingRegister = false;

    public LoginScreen(AvatarDkGame game) {
        this.game = game;
    }

    private float fieldY(int i) { return TOP - 252 - i * 92; } // mép dưới của ô nhập

    @Override
    public void show() {
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
                    StringBuilder f = fields[focus];
                    if (f.length() > 0) f.deleteCharAt(f.length() - 1);
                    return true;
                }
                if (keycode == Input.Keys.TAB) {
                    focus = (focus + 1) % fields.length;
                    return true;
                }
                if (keycode == Input.Keys.ENTER) {
                    submit(tab == 1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(char c) {
                if (c < 32 || c == 127) return false;
                if (fields[focus].length() < 40) fields[focus].append(c);
                return true;
            }
        });
    }

    private void onTouch(float x, float y) {
        // tab Đăng nhập / Đăng ký
        float tabY = TOP - 162;
        if (y >= tabY && y <= tabY + 50 && x >= PX + 32 && x <= PX + PW - 32) {
            tab = x < Ui.W / 2f ? 0 : 1;
            return;
        }
        // hai ô nhập
        for (int i = 0; i < fields.length; i++) {
            float fy = fieldY(i);
            if (x >= PX + 32 && x <= PX + PW - 32 && y >= fy && y <= fy + 48) {
                if (i == 1 && x >= PX + PW - 84) {
                    showPass = !showPass;
                    focus = 1;
                } else {
                    focus = i;
                }
                return;
            }
        }
        // nhớ đăng nhập / quên mật khẩu
        if (y >= TOP - 392 && y <= TOP - 356) {
            if (x >= PX + 32 && x <= PX + 240) {
                remember = !remember;
                return;
            }
            if (x >= PX + PW - 190 && x <= PX + PW - 32) {
                setStatus("Hãy liên hệ Hỗ trợ ở màn banner để đặt lại mật khẩu.", false);
                return;
            }
        }
        // nút chính
        float by = TOP - 462;
        if (x >= PX + 32 && x <= PX + PW - 32 && y >= by && y <= by + 58) {
            submit(tab == 1);
            return;
        }
        // Google / Apple
        float gy = TOP - 568;
        if (y >= gy && y <= gy + 52 && x >= PX + 32 && x <= PX + PW - 32) {
            setStatus("Đăng nhập bằng " + (x < Ui.W / 2f ? "Google" : "Apple")
                + " chưa nối — sẽ có ở bản online sau. Hãy dùng tài khoản thị trấn.", true);
            return;
        }
        // chơi thử ngay (dưới panel)
        if (y >= 6 && y <= 38 && x >= Ui.W / 2f - 150 && x <= Ui.W / 2f + 150) {
            game.playerName = AvatarDkGame.SON_NAME;
            game.setScreen(game.world);
        }
    }

    private void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
    }

    private void submit(boolean register) {
        if (busy) return;
        String user = fields[0].toString().trim();
        String pass = fields[1].toString();
        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Chưa nhập tài khoản hoặc mật khẩu.", true);
            return;
        }
        pendingRegister = register;
        busy = true;
        setStatus(register ? "Đang đăng ký…" : "Đang kết nối máy chủ…", false);
        Servers.Server target = Servers.selected();
        final String host = target.host;
        final int port = target.port;
        final GameConnection existing = game.connection;
        if (existing != null && existing.isKeyReady() && !existing.isClosed()) {
            sendAuth(existing, register, user, pass);
            return;
        }
        new Thread(() -> {
            try {
                GameConnection conn = new GameConnection();
                conn.connect(host, port);
                game.connection = conn;
                long deadline = System.currentTimeMillis() + 8000;
                while (!conn.isKeyReady() && !conn.isClosed()
                    && System.currentTimeMillis() < deadline) {
                    Thread.sleep(30);
                }
                if (!conn.isKeyReady()) {
                    busy = false;
                    setStatus("Không nối được tới máy chủ " + target.label + ".", true);
                    return;
                }
                AvatarProtocol.sendIntro(conn, (int) Ui.W, (int) Ui.H);
                sendAuth(conn, register, user, pass);
            } catch (Exception e) {
                busy = false;
                setStatus("Lỗi kết nối: " + e.getMessage(), true);
            }
        }, "login-connect").start();
    }

    private void sendAuth(GameConnection conn, boolean register, String user, String pass) {
        try {
            if (register) AvatarProtocol.register(conn, user, pass);
            else AvatarProtocol.login(conn, user, pass);
            setStatus(register ? "Đã gửi đăng ký, chờ máy chủ trả lời…"
                : "Đã gửi đăng nhập, chờ máy chủ trả lời…", false);
        } catch (Exception e) {
            busy = false;
            setStatus("Không gửi được: " + e.getMessage(), true);
        }
    }

    private void pollReplies() {
        GameConnection conn = game.connection;
        if (conn == null) return;
        Msg m;
        while ((m = conn.poll()) != null) {
            try {
                if (m.command == AvatarProtocol.LOGIN_SUCESS) {
                    PlayerInfo p = PlayerInfo.parse(m);
                    game.player = p;
                    game.playerName = fields[0].toString().trim();
                    busy = false;
                    game.world.applyPlayerInfo(p);
                    game.setScreen(game.serverSelect);
                    return;
                } else if (m.command == AvatarProtocol.SERVER_MESSAGE) {
                    DataInputStream r = m.reader();
                    String text = r.readUTF();
                    busy = false;
                    if (pendingRegister && text.contains("thành công")) {
                        pendingRegister = false;
                        tab = 0;
                        setStatus("Đăng ký thành công! Đang đăng nhập…", false);
                        submit(false);
                    } else {
                        setStatus(text, true);
                        pendingRegister = false;
                    }
                }
            } catch (Exception e) {
                busy = false;
                setStatus("Gói trả lời không đọc được: " + e.getMessage(), true);
            }
        }
        if (busy && conn.isClosed()) {
            busy = false;
            setStatus("Máy chủ đã đóng kết nối" + (conn.error() != null ? ": " + conn.error() : "."), true);
        }
    }

    @Override
    public void render(float delta) {
        pollReplies();
        Gdx.gl.glClearColor(0.05f, 0.08f, 0.14f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        EntryUi.drawBanner(batch);
        batch.draw(Ui.roundedRect((int) Ui.W, (int) Ui.H, 0,
            new Color(0.01f, 0.03f, 0.06f, 0.45f), null), 0, 0);
        drawPanel(batch);
        Ui.centered(batch, Ui.fontSmall, "Chơi thử ngay, không cần tài khoản", Ui.W / 2f, 24,
            new Color(1f, 1f, 1f, 0.75f));
        batch.end();
    }

    private void drawPanel(SpriteBatch batch) {
        batch.draw(Ui.gradientRect((int) PW, (int) PH, 24,
            new Color(0.07f, 0.13f, 0.23f, 0.98f), new Color(0.03f, 0.06f, 0.12f, 0.98f)), PX, PY);
        batch.draw(Ui.outlineRect((int) PW, (int) PH, 24, EntryUi.PANEL_RIM), PX, PY);
        // header: mặt trời FA + tên game
        Ui.faIcon(batch, Ui.fa30, Ui.FA_SUN, PX + 52, TOP - 40, EntryUi.GOLD);
        Ui.fontBaloo36.setColor(EntryUi.GOLD);
        Ui.fontBaloo36.draw(batch, EntryUi.GAME_NAME, PX + 78, TOP - 28);
        Ui.fontSmall.setColor(SUB_TEXT);
        Ui.fontSmall.draw(batch, "Chào mừng bạn đến với thị trấn", PX + 79, TOP - 56);
        // thanh tab
        float tabY = TOP - 162;
        batch.draw(Ui.roundedRect((int) (PW - 64), 50, 14,
            new Color(0.06f, 0.11f, 0.18f, 1f), null), PX + 32, tabY);
        float half = (PW - 72) / 2f;
        float pillX = tab == 0 ? PX + 36 : PX + 36 + half;
        batch.draw(Ui.gradientRect((int) half, 42, 11, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            pillX, tabY + 4);
        Color tabOff = new Color(0.78f, 0.84f, 0.92f, 1f);
        Ui.centered(batch, Ui.font, "Đăng nhập", PX + 36 + half / 2f, tabY + 33,
            tab == 0 ? Color.WHITE : tabOff);
        Ui.centered(batch, Ui.font, "Đăng ký", PX + 36 + half + half / 2f, tabY + 33,
            tab == 1 ? Color.WHITE : tabOff);
        // hai ô nhập
        drawField(batch, 0, "Tài khoản", Ui.FA_USER, "Nhập tài khoản");
        drawField(batch, 1, "Mật khẩu", Ui.FA_LOCK, "••••••••");
        // nhớ đăng nhập + quên mật khẩu
        float ry = TOP - 374;
        batch.draw(Ui.roundedRect(18, 18, 5, remember ? EntryUi.ORANGE_BOT
            : new Color(0.9f, 0.93f, 0.97f, 1f), null), PX + 36, ry - 14);
        if (remember) Ui.faIcon(batch, Ui.fa20, Ui.FA_CHECK, PX + 45, ry - 5, Color.WHITE);
        Ui.fontSmall.setColor(0.85f, 0.9f, 0.96f, 1f);
        Ui.fontSmall.draw(batch, "Nhớ đăng nhập", PX + 62, ry);
        String forgot = "Quên mật khẩu?";
        Ui.fontSmall.setColor(LINK_BLUE);
        Ui.fontSmall.draw(batch, forgot, PX + PW - 36 - Ui.textWidth(Ui.fontSmall, forgot), ry);
        // nút chính
        float by = TOP - 462;
        batch.draw(Ui.gradientRect((int) (PW - 64), 58, 14, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            PX + 32, by);
        Ui.centered(batch, Ui.fontBaloo30, tab == 0 ? "ĐĂNG NHẬP" : "ĐĂNG KÝ", Ui.W / 2f, by + 39, Color.WHITE);
        // divider
        float dy = TOP - 492;
        batch.draw(Ui.roundedRect(138, 2, 1, new Color(0.59f, 0.67f, 0.8f, 0.8f), null), PX + 40, dy);
        Ui.centered(batch, Ui.fontSmall, "hoặc tiếp tục với", Ui.W / 2f, dy + 6, SUB_TEXT);
        batch.draw(Ui.roundedRect(138, 2, 1, new Color(0.59f, 0.67f, 0.8f, 0.8f), null), PX + PW - 178, dy);
        // Google / Apple
        float gy = TOP - 568;
        batch.draw(Ui.roundedRect((int) half, 52, 12, Color.WHITE, null), PX + 32, gy);
        Ui.faIcon(batch, Ui.faBrands26, Ui.FA_GOOGLE, PX + 62, gy + 26, new Color(0.24f, 0.25f, 0.27f, 1f));
        Ui.font.setColor(DARK_TEXT);
        Ui.font.draw(batch, "Google", PX + 88, gy + 33);
        float ax = PX + 40 + half;
        batch.draw(Ui.roundedRect((int) half, 52, 12, new Color(0.04f, 0.04f, 0.05f, 1f), null), ax, gy);
        Ui.faIcon(batch, Ui.faBrands30, Ui.FA_APPLE, ax + 30, gy + 26, Color.WHITE);
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, "Apple", ax + 56, gy + 33);
        // trạng thái + điều khoản
        if (!status.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall, status, Ui.W / 2f, PY + 40,
                statusError ? new Color(1f, 0.55f, 0.45f, 1f) : new Color(1f, 0.85f, 0.55f, 1f));
        }
        Ui.centered(batch, Ui.fontTiny, "Tiếp tục nghĩa là bạn đồng ý với Điều khoản & Chính sách bảo mật",
            Ui.W / 2f, PY + 18, new Color(0.55f, 0.63f, 0.73f, 1f));
    }

    private void drawField(SpriteBatch batch, int i, String label, String glyph, String placeholder) {
        float fy = fieldY(i);
        Ui.fontSmall.setColor(0.76f, 0.84f, 0.94f, 1f);
        Ui.fontSmall.draw(batch, label, PX + 36, fy + 70);
        Color rim = focus == i ? new Color(1f, 0.67f, 0.24f, 1f) : null;
        if (rim != null) {
            batch.draw(Ui.roundedRect((int) (PW - 64), 48, 13, FIELD_FILL, rim), PX + 32, fy);
        } else {
            batch.draw(Ui.roundedRect((int) (PW - 64), 48, 13, FIELD_FILL, null), PX + 32, fy);
        }
        Ui.faIcon(batch, Ui.fa20, glyph, PX + 58, fy + 24, ICON_GREY);
        String text = fields[i].toString();
        boolean empty = text.isEmpty();
        if (i == 1 && !showPass) text = "•".repeat(text.length());
        if (empty) text = placeholder;
        if (focus == i && !empty) text += "|";
        Ui.font.setColor(empty ? new Color(0.47f, 0.53f, 0.61f, 1f) : DARK_TEXT);
        Ui.font.draw(batch, text, PX + 80, fy + 31);
        if (i == 1) {
            Ui.faIcon(batch, Ui.fa20, showPass ? Ui.FA_EYE_SLASH : Ui.FA_EYE,
                PX + PW - 57, fy + 24, ICON_GREY);
        }
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
