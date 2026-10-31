package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.net.AvatarProtocol;
import com.avatardk.game.net.GameConnection;
import com.avatardk.game.net.Msg;
import com.avatardk.game.net.PlayerInfo;
import java.io.DataInputStream;

/**
 * Màn hình đăng nhập / đăng ký / chọn server (đợt 2).
 *
 * Server gốc không có danh sách server động: client gốc gắn cứng host:port
 * (14.225.213.114:19128) nên ở đây ô "Máy chủ" cho sửa host:port tự do, mặc
 * định trỏ về server local 127.0.0.1:19128. Đăng ký dùng gói REQUEST_REGISTER
 * (-30) mà phía server của bản port đã bổ sung (bản gốc không xử lý).
 * Nút "Chơi thử offline" vào thẳng thế giới với nhân vật mẫu như đợt 1.
 */
public class LoginScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);
    private final GlyphLayout layout = new GlyphLayout();

    private static final Color GLASS_FILL = new Color(0.05f, 0.11f, 0.18f, 0.72f);
    private static final Color GLASS_RIM = new Color(0.62f, 0.82f, 1f, 0.38f);
    private static final float PANEL_W = 470f, PANEL_H = 452f;
    private static final float PANEL_X = Ui.W / 2f - PANEL_W / 2f, PANEL_Y = Ui.H / 2f - PANEL_H / 2f + 10;

    // 0 = máy chủ, 1 = tài khoản, 2 = mật khẩu
    private final StringBuilder[] fields = {
        new StringBuilder("127.0.0.1:19128"), new StringBuilder(), new StringBuilder()};
    private int focus = 1;
    private String status = "Nhập tài khoản để đăng nhập, hoặc đăng ký tài khoản mới.";
    private boolean statusError = false;
    private boolean busy = false;
    private boolean pendingRegister = false;

    public LoginScreen(AvatarDkGame game) {
        this.game = game;
    }

    private float fieldY(int i) {
        return PANEL_Y + PANEL_H - 168 - i * 64;
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
                    submit(false);
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
        for (int i = 0; i < fields.length; i++) {
            float fy = fieldY(i);
            if (x >= PANEL_X + 28 && x <= PANEL_X + PANEL_W - 28 && y >= fy && y <= fy + 40) {
                focus = i;
                return;
            }
        }
        float by = PANEL_Y + 108;
        if (y >= by && y <= by + 46) {
            if (x >= PANEL_X + 28 && x <= PANEL_X + 228) { submit(false); return; }
            if (x >= PANEL_X + 242 && x <= PANEL_X + PANEL_W - 28) { submit(true); return; }
        }
        float oy = PANEL_Y + 52;
        if (y >= oy && y <= oy + 34 && x >= PANEL_X + 28 && x <= PANEL_X + PANEL_W - 28) {
            game.setScreen(game.world); // chơi thử offline với nhân vật mẫu
        }
    }

    private void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
    }

    /** Bắt đầu (hoặc dùng lại) kết nối rồi gửi đăng nhập/đăng ký. */
    private void submit(boolean register) {
        if (busy) return;
        String user = fields[1].toString().trim();
        String pass = fields[2].toString();
        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Chưa nhập tài khoản hoặc mật khẩu.", true);
            return;
        }
        pendingRegister = register;
        busy = true;
        setStatus(register ? "Đang đăng ký…" : "Đang kết nối máy chủ…", false);
        final String serverText = fields[0].toString().trim();
        final GameConnection existing = game.connection;
        if (existing != null && existing.isKeyReady() && !existing.isClosed()) {
            sendAuth(existing, register, user, pass);
            return;
        }
        new Thread(() -> {
            try {
                String host = serverText;
                int port = 19128;
                int colon = serverText.lastIndexOf(':');
                if (colon > 0) {
                    host = serverText.substring(0, colon);
                    port = Integer.parseInt(serverText.substring(colon + 1));
                }
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
                    setStatus("Không nối được tới máy chủ " + serverText + ".", true);
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

    /** Quét gói trả lời mỗi frame: -4 = đăng nhập thành công, -9 = thông báo. */
    private void pollReplies() {
        GameConnection conn = game.connection;
        if (conn == null) return;
        Msg m;
        while ((m = conn.poll()) != null) {
            try {
                if (m.command == AvatarProtocol.LOGIN_SUCESS) {
                    PlayerInfo p = PlayerInfo.parse(m);
                    game.player = p;
                    busy = false;
                    game.world.applyPlayerInfo(p);
                    game.setScreen(game.world);
                    return;
                } else if (m.command == AvatarProtocol.SERVER_MESSAGE) {
                    DataInputStream r = m.reader();
                    String text = r.readUTF();
                    busy = false;
                    boolean okReg = pendingRegister && text.contains("thành công");
                    setStatus(text, !okReg && !pendingRegister);
                    pendingRegister = false;
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
        Gdx.gl.glClearColor(0.36f, 0.74f, 0.91f, 1f); // màu trời ngày của gốc #5DBCE7
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        // silhouette thành phố của nền gốc ở đáy màn hình
        Texture bg = game.texture("bghd/00.png");
        batch.draw(bg, 0, 0, Ui.W, bg.getHeight() * (Ui.W / bg.getWidth()) * 0.5f + 120);
        // tiêu đề
        layout.setText(Ui.fontTitle, "AvatarDK");
        Ui.fontTitle.setColor(Color.WHITE);
        Ui.fontTitle.draw(batch, "AvatarDK", Ui.W / 2f - layout.width / 2f, PANEL_Y + PANEL_H + 64);
        // panel kính
        batch.draw(Ui.roundedRect((int) PANEL_W, (int) PANEL_H, 18, GLASS_FILL, GLASS_RIM), PANEL_X, PANEL_Y);
        String[] labels = {"Máy chủ", "Tài khoản", "Mật khẩu"};
        for (int i = 0; i < fields.length; i++) {
            float fy = fieldY(i);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
            Ui.fontSmall.draw(batch, labels[i], PANEL_X + 28, fy + 58);
            Color rim = focus == i ? new Color(1f, 0.8f, 0.35f, 0.95f) : GLASS_RIM;
            batch.draw(Ui.roundedRect((int) (PANEL_W - 56), 40, 10,
                new Color(1f, 1f, 1f, 0.94f), rim), PANEL_X + 28, fy);
            String text = fields[i].toString();
            if (i == 2) text = "*".repeat(text.length());
            if (focus == i) text += "|";
            Ui.fontSmall.setColor(0.1f, 0.1f, 0.12f, 1f);
            Ui.fontSmall.draw(batch, text, PANEL_X + 42, fy + 26);
        }
        // hai nút chính
        float by = PANEL_Y + 108;
        batch.draw(Ui.roundedRect(200, 46, 12,
            new Color(0.85f, 0.45f, 0.08f, 0.97f), new Color(1f, 0.7f, 0.3f, 0.9f)), PANEL_X + 28, by);
        centerText("Đăng nhập", PANEL_X + 28, 200, by + 30);
        batch.draw(Ui.roundedRect((int) (PANEL_W - 242 - 28), 46, 12,
            new Color(1f, 1f, 1f, 0.16f), GLASS_RIM), PANEL_X + 242, by);
        centerText("Đăng ký", PANEL_X + 242, PANEL_W - 242 - 28, by + 30);
        // nút offline
        float oy = PANEL_Y + 52;
        batch.draw(Ui.roundedRect((int) (PANEL_W - 56), 34, 10,
            new Color(1f, 1f, 1f, 0.10f), GLASS_RIM), PANEL_X + 28, oy);
        centerText("Chơi thử offline (không cần tài khoản)", PANEL_X + 28, PANEL_W - 56, oy + 23);
        // dòng trạng thái
        Ui.fontSmall.setColor(statusError ? new Color(1f, 0.55f, 0.45f, 1f)
            : new Color(1f, 0.95f, 0.75f, 1f));
        layout.setText(Ui.fontSmall, status);
        float maxW = PANEL_W - 40;
        String shown = status;
        if (layout.width > maxW) {
            while (shown.length() > 0 && layout.width > maxW) {
                shown = shown.substring(0, shown.length() - 1);
                layout.setText(Ui.fontSmall, shown + "…");
            }
            shown += "…";
        }
        Ui.fontSmall.draw(batch, shown, PANEL_X + PANEL_W / 2f - layout.width / 2f, PANEL_Y + 28);
        batch.end();
    }

    private void centerText(String text, float x, float w, float baselineY) {
        layout.setText(Ui.font, text);
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, text, x + (w - layout.width) / 2f, baselineY);
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
