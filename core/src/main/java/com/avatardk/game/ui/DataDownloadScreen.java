package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.update.DataUpdater;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Màn hình tải dữ liệu game ở lần vào đầu tiên (hoặc khi có phiên bản dữ
 * liệu mới): kiểm tra manifest, tải các gói zip có thanh tiến độ thật,
 * kiểm tra sha256 rồi giải nén — xong mới vào game. Không có mạng mà máy
 * đã có dữ liệu (đã tải trước hoặc đóng gói sẵn) thì dùng dữ liệu đó và
 * vào luôn; không có gì cả thì báo lỗi + nút Thử lại.
 *
 * <p>Chế độ simulate (chỉ harness chụp ảnh dùng): chạy tiến độ giả,
 * không đụng mạng.
 */
public class DataDownloadScreen extends ScreenAdapter {
    private final AvatarDkGame game;
    private final DataUpdater updater;
    private final boolean simulate;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport viewport = new FitViewport(Ui.W, Ui.H);
    private Texture whiteTex;
    private Texture trackTex;
    private Texture retryTex;

    private volatile long progressDone = 0;
    private volatile long progressTotal = 0;
    private volatile String status = "Đang kiểm tra dữ liệu…";
    private volatile String error = null;
    private final AtomicBoolean proceeded = new AtomicBoolean(false);
    private float simProgress = 0f;
    private Boolean bannerOk = null;

    // Không khung panel: chỉ banner + logo, thanh tiến trình nằm sát đáy
    // màn hình, chữ đang tải + số liệu ngay bên trên thanh.
    private static final float BAR_W = 720f, BAR_H = 22f;
    private static final float BAR_X = (Ui.W - BAR_W) / 2f, BAR_Y = 56f;
    private static final float RETRY_W = 240f, RETRY_H = 56f;
    private static final float RETRY_X = (Ui.W - RETRY_W) / 2f, RETRY_Y = 196f;

    public DataDownloadScreen(AvatarDkGame game, DataUpdater updater, boolean simulate) {
        this.game = game;
        this.updater = updater;
        this.simulate = simulate;
        if (updater != null) {
            DataUpdater.Manifest cached = updater.cachedManifest();
            if (cached != null) progressTotal = cached.totalSize;
        }
    }

    @Override
    public void show() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whiteTex = new Texture(pm);
        pm.dispose();
        trackTex = Ui.roundedRect((int) BAR_W, (int) BAR_H, 11,
            new Color(0.05f, 0.09f, 0.16f, 1f), new Color(0.40f, 0.55f, 0.75f, 0.5f));
        retryTex = Ui.roundedRect((int) RETRY_W, (int) RETRY_H, 16,
            new Color(1f, 0.55f, 0.15f, 1f), new Color(1f, 0.75f, 0.40f, 0.9f));
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector3 v = viewport.unproject(new Vector3(sx, sy, 0));
                if (error != null && v.x >= RETRY_X && v.x <= RETRY_X + RETRY_W
                    && v.y >= RETRY_Y && v.y <= RETRY_Y + RETRY_H) {
                    error = null;
                    status = "Đang thử lại…";
                    startDownload();
                }
                return true;
            }
        });
        if (!simulate) startDownload();
    }

    private void startDownload() {
        new Thread(() -> {
            try {
                DataUpdater.Manifest m = updater.fetchManifest();
                if (updater.installedVersion() >= m.version && m.version > 0) {
                    status = "Dữ liệu đã đầy đủ.";
                    finishOk();
                    return;
                }
                progressTotal = m.totalSize;
                updater.downloadAll(m, (done, total, st) -> {
                    progressDone = done;
                    progressTotal = total;
                    status = st;
                });
                status = "Tải xong — đang vào game…";
                finishOk();
            } catch (Exception e) {
                if (updater.installedVersion() >= 0
                    || Gdx.files.internal("data/items.json").exists()) {
                    status = "Không tải được bản mới — dùng dữ liệu sẵn có trên máy.";
                    try {
                        Thread.sleep(1400);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    finishOk();
                } else {
                    String msg = e.getMessage();
                    error = "Không tải được dữ liệu game."
                        + (msg != null && !msg.isEmpty() ? "\n" + msg : "")
                        + "\nKiểm tra mạng rồi bấm Thử lại nhé.";
                }
            }
        }, "data-download").start();
    }

    private void finishOk() {
        if (proceeded.compareAndSet(false, true)) {
            Gdx.app.postRunnable(game::initGameData);
        }
    }

    /** Chữ có bóng đổ nhẹ để đọc rõ trên nền tranh banner. */
    private static void centeredShadow(SpriteBatch batch, com.badlogic.gdx.graphics.g2d.BitmapFont f,
                                       String text, float cx, float baselineY, Color color) {
        Ui.centered(batch, f, text, cx + 2f, baselineY - 2f, new Color(0f, 0f, 0f, 0.55f));
        Ui.centered(batch, f, text, cx, baselineY, color);
    }

    private static String mb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f", bytes / 1048576.0).replace('.', ',');
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.11f, 0.22f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float frac;
        String statusText = status;
        String sizeText;
        if (simulate) {
            simProgress = Math.min(1f, simProgress + delta * 0.22f);
            frac = simProgress;
            long total = 37_900_000L;
            long done = (long) (total * frac);
            statusText = frac < 0.45f ? "Đang tải gamedata-1.zip…"
                : frac < 0.8f ? "Đang tải gamedata-2.zip…" : "Đang giải nén gamedata-3.zip…";
            sizeText = mb(done) + " / " + mb(total) + " MB";
        } else if (progressTotal > 0) {
            frac = Math.min(1f, progressDone / (float) progressTotal);
            sizeText = mb(progressDone) + " / " + mb(progressTotal) + " MB";
        } else {
            frac = 0f;
            sizeText = "";
        }
        if (error != null) frac = 0f;

        // Nền = banner của game + header logo như màn vào game; bản cài gọn
        // đóng sẵn banner.jpg nên lần đầu chưa tải data vẫn hiện được.
        if (bannerOk == null) {
            try {
                EntryUi.banner();
                bannerOk = Boolean.TRUE;
            } catch (Exception e) {
                bannerOk = Boolean.FALSE;
            }
        }
        if (Boolean.TRUE.equals(bannerOk)) {
            EntryUi.drawBanner(batch);
            EntryUi.drawLogoOnly(batch);
        }

        batch.draw(trackTex, BAR_X, BAR_Y);
        if (frac > 0.003f) {
            batch.setColor(1f, 0.55f, 0.15f, 1f);
            batch.draw(whiteTex, BAR_X + 3f, BAR_Y + 3f, (BAR_W - 6f) * frac, BAR_H - 6f);
            batch.setColor(Color.WHITE);
        }

        if (error != null) {
            String[] lines = error.split("\n");
            for (int i = 0; i < lines.length; i++) {
                centeredShadow(batch, Ui.fontSmall, lines[i], Ui.W / 2f,
                    BAR_Y + 118f - i * 30f, new Color(1f, 0.62f, 0.52f, 1f));
            }
            batch.draw(retryTex, RETRY_X, RETRY_Y);
            Ui.centered(batch, Ui.fontTitle, "Thử lại", Ui.W / 2f, RETRY_Y + 38f, Color.WHITE);
        } else {
            centeredShadow(batch, Ui.font, statusText, Ui.W / 2f, BAR_Y + 88f, Color.WHITE);
            if (error == null) {
                String pct = Math.round(frac * 100f) + "%";
                centeredShadow(batch, Ui.font, sizeText.isEmpty() ? pct : pct + "  •  " + sizeText,
                    Ui.W / 2f, BAR_Y + 48f, new Color(0.88f, 0.94f, 1f, 1f));
            }
        }
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        if (whiteTex != null) whiteTex.dispose();
        if (trackTex != null) trackTex.dispose();
        if (retryTex != null) retryTex.dispose();
    }
}
