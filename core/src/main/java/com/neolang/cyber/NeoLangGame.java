package com.neolang.cyber;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.neolang.cyber.model.GameState;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.SaveData;
import com.neolang.cyber.screens.AdventureScreen;
import com.neolang.cyber.screens.CreateScreen;
import com.neolang.cyber.screens.PlotScreen;
import com.neolang.cyber.screens.TitleScreen;
import com.neolang.cyber.screens.TownScreen;

/**
 * Game root. Virtual resolution 1280x720 (landscape only).
 * Screenshot mode: when screenshotDir != null the screens run an auto script
 * (title -> create hero -> town -> adventure -> plot), each captures after ~90 frames
 * and hands over to the next; PlotScreen exits the app at the end.
 */
public class NeoLangGame extends Game {
    public static final int VW = 1280;
    public static final int VH = 720;

    public GameState state;
    public Assets assets;
    public SoundSynth sound;
    public String screenshotDir;
    public SpriteBatch batch;
    public ShapeRenderer shapes;

    public NeoLangGame() {
        this(null);
    }

    public NeoLangGame(String screenshotDir) {
        this.screenshotDir = screenshotDir;
    }

    /** ShapeRenderer that always enables alpha blending first.
     *  SpriteBatch.end() switches GL_BLEND off and ShapeRenderer never turns it
     *  back on, which would make every translucent shape render opaque. */
    public static class BlendingShapes extends ShapeRenderer {
        @Override
        public void begin(ShapeType type) {
            com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
            com.badlogic.gdx.Gdx.gl.glBlendFunc(
                com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
            super.begin(type);
        }
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new BlendingShapes();
        shapes.setAutoShapeType(true);
        loadGame(); // preload so Title can show "Choi tiep"
        assets = new Assets();
        float scale = 1f;
        if (state != null) scale = (float) state.settings.uiScale;
        assets.load(scale);
        sound = new SoundSynth();
        if (state != null) sound.setEnabled(state.settings.sound, state.settings.music);
        sound.startMusic();
        setScreen(new TitleScreen(this));
    }

    public void newGame(String name, Gender gender, int suit, int hair) {
        state = new GameState(name, gender, suit, hair);
        saveGame();
    }

    public void saveGame() {
        if (state == null) return;
        try {
            Gdx.files.local("neolang-save.txt").writeString(SaveData.save(state), false, "UTF-8");
        } catch (Exception ignored) {
        }
    }

    public boolean loadGame() {
        try {
            FileHandle f = Gdx.files.local("neolang-save.txt");
            if (f.exists()) {
                GameState loaded = SaveData.load(f.readString("UTF-8"));
                if (loaded != null) {
                    state = loaded;
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void gotoTitle() {
        setScreen(new TitleScreen(this));
    }

    public void gotoTown() {
        setScreen(new TownScreen(this));
    }

    public void gotoAdventure() {
        setScreen(new AdventureScreen(this));
    }

    public void gotoPlot() {
        setScreen(new PlotScreen(this));
    }

    public void startCreate() {
        setScreen(new CreateScreen(this));
    }

    /** Screenshot helper used by every screen when screenshotDir != null. */
    public void captureShot(String fileName) {
        if (screenshotDir == null) return;
        try {
            Gdx.files.absolute(screenshotDir).mkdirs();
            Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            flipVertically(pm);
            PixmapIO.writePNG(Gdx.files.absolute(screenshotDir + "/" + fileName), pm);
            pm.dispose();
        } catch (Exception ignored) {
        }
    }

    /** Framebuffer pixmaps are upside-down (GL origin); flip rows so PNGs view correctly. */
    private static void flipVertically(Pixmap pm) {
        int w = pm.getWidth(), h = pm.getHeight();
        int bpp = 4; // ScreenUtils returns RGBA8888
        java.nio.ByteBuffer buf = pm.getPixels();
        byte[] row = new byte[w * bpp];
        for (int y = 0; y < h / 2; y++) {
            int top = y * w * bpp, bot = (h - 1 - y) * w * bpp;
            buf.position(top);
            buf.get(row, 0, row.length);
            byte[] tmp = new byte[w * bpp];
            buf.position(bot);
            buf.get(tmp, 0, tmp.length);
            buf.position(bot);
            buf.put(row, 0, row.length);
            buf.position(top);
            buf.put(tmp, 0, tmp.length);
        }
        buf.position(0);
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (assets != null) assets.dispose();
    }
}
