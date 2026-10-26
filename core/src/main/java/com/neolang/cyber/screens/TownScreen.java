package com.neolang.cyber.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.ClassChoice;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.ItemDef;
import com.neolang.cyber.model.Items;
import com.neolang.cyber.model.ObjectiveType;
import com.neolang.cyber.model.Player;
import com.neolang.cyber.model.QuestDef;
import com.neolang.cyber.model.Survival;
import com.neolang.cyber.ui.Draw;
import com.neolang.cyber.ui.Hud;
import com.neolang.cyber.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** The town: a 2800px strip on the Mother Tree trunk — pods row behind, flat ground below,
 *  minimal corner HUD, NPCs with name tags and speech bubbles, class station in the middle. */
public class TownScreen implements Screen {
    public static final float WORLD_W = 2800;
    public static final float GROUND = 170;

    private final NeoLangGame game;
    private final ExtendViewport worldVp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);
    private final ExtendViewport hudVp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);

    private float time;
    private float playerX = 300;
    private float facing = 1;
    private float walkPhase;
    private String panel; // null | menu bag mail chat char quest settings pause chief cafe miro bank pet station emote
    private float saveT;
    private int shotFrames;
    private boolean gone;

    private String lastNotice;
    private float noticeT = 99;

    private boolean moving; // hero moved this frame (drives side-profile sprite + bob)

    private int joyMode; // 0 none, 1 fixed bottom-left stick, 2 floating stick (secondary)
    private final Vector2 joyBase = new Vector2();
    private float joyVec;
    private boolean stickRun; // moving at run speed (full stick tilt or Shift held)
    private static final float JOY_X = 108, JOY_Y = 108; // fixed joystick centre, bottom-left

    private int emote; // 0 none, 1 wave, 2 smile, 3 sit
    private float emoteT;

    private final List<Npc> npcs = new ArrayList<Npc>();
    private final List<String> chatLog = new ArrayList<String>();
    private int chatReplyIdx;
    private static final String[] CHAT_REPLIES = {
        "Hôm nay mạch cây chạy êm lắm, bạn thấy không?",
        "Nghe nói Khu Vành Đai Neon có nhiều Nhựa Sáng rơi sau bão đó.",
        "Cafe Pina vừa có món mới, ghé thử đi!",
        "Đạt cấp 5 là qua Trạm Định Hướng chọn nghề được rồi.",
        "Pet của bạn trông khoẻ đấy, cho nó ăn pin chưa?"
    };
    private static final String[] QUICK_CHAT = {"Xin chào!", "Hôm nay có bão không?", "Cảm ơn nhé!"};

    private static class Npc {
        String id, name;
        float x, home, range, speed, dir = 1, walk;
        Gender gender;
        int suit, hair, style, eye;
        int fig; // painted customization-sheet figure used as this NPC's sprite
        String[] lines;
        float lineT;
        int lineIdx;
        Npc(String id, String name, float home, float range, Gender g, int suit, int hair, int style, int fig, String... lines) {
            this.id = id; this.name = name; this.home = home; this.x = home; this.range = range;
            this.gender = g; this.suit = suit; this.hair = hair; this.style = style; this.eye = 0;
            this.fig = fig;
            this.lines = lines; this.speed = 12 + (float) Math.random() * 10;
        }
    }

    private static class Spot {
        String id, label;
        float x;
        Spot(String id, String label, float x) { this.id = id; this.label = label; this.x = x; }
    }

    public TownScreen(NeoLangGame game) {
        this.game = game;
        Player p = game.state.player;
        playerX = clamp((float) p.x, 70, WORLD_W - 70);
        if (playerX < 70 || playerX > WORLD_W - 70) playerX = 300;
        npcs.add(new Npc("chief", "Trưởng Trạm Gốc", 1230, 130, Gender.NAM, 1, 1, 0, 2,
            "Chào cư dân mới! Neo Làng luôn rộng cửa.",
            "Hoàn thành nhiệm vụ rồi quay lại gặp ta nhé.",
            "Cây Mẹ hôm nay phát sáng đẹp quá..."));
        npcs.add(new Npc("cafe", "Chủ Cafe Pina", 545, 55, Gender.NU, 3, 3, 0, 0,
            "Cafe Lá Non, mời bạn ghé!", "Đói thì qua Pina nhé, đừng để kiệt sức."));
        npcs.add(new Npc("miro", "Thợ Thời Trang Miro", 990, 55, Gender.NU, 2, 4, 2, 1,
            "Đồ mới về liên tục nè!", "Mặc đẹp thì farm cũng vui hơn."));
        npcs.add(new Npc("bank", "Thu Ngân Lyn", 2090, 45, Gender.NU, 1, 5, 1, 2,
            "Ngân Hàng Lá xin chào.", "Pin đầy thì đời mới vui."));
        npcs.add(new Npc("pet", "Bé Robot Bán Pet", 2445, 45, Gender.NAM, 4, 2, 2, 0,
            "Pet khoẻ, chủ vui!", "Nâng cấp pet để nhặt đồ nhanh hơn nhé."));
        npcs.add(new Npc("station", "Hướng Đạo Viên Rô", 1520, 0, Gender.NAM, 0, 0, 1, 1,
            "Cấp 5 và xong nhiệm vụ mở đầu là chọn nghề được.", "Bốn nghề, bốn lối đi — chọn kỹ nhé."));
        chatLog.add("Pina: Chào bạn mới! Ghé cafe khi đói nhé.");
        chatLog.add("Miro: Bộ Street Neon vừa về hàng đó.");
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    private int petTier() {
        return Math.min(3, 1 + game.state.player.countItem("pet_chip"));
    }

    private float dayness() {
        if (game.state.clock.isNight()) return 0.14f;
        double f = ((double) game.state.clock.phase() - 0.05) / 0.65;
        f = Math.max(0, Math.min(1, f));
        return (float) (0.25 + 0.75 * Math.sin(f * Math.PI));
    }

    private boolean btn(float x, float y, float w, float h, String label) {
        return Ui.button(game.batch, game.shapes, game.assets.fontSmall, hudVp, x, y, w, h, label);
    }

    private List<Spot> spots() {
        List<Spot> s = new ArrayList<Spot>();
        s.add(new Spot("plot", "Về Khu Nhà Cành", 70));
        for (Npc n : npcs) s.add(new Spot(n.id, "Nói chuyện: " + n.name, n.x));
        s.add(new Spot("board", "Bảng Nhiệm Vụ", 1760));
        s.add(new Spot("adv", "Tới Khu Vành Đai Neon", 2730));
        return s;
    }

    private Spot nearestSpot() {
        Spot best = null;
        float bd = 130;
        for (Spot s : spots()) {
            float d = Math.abs(s.x - playerX);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    private boolean nearPod(float x, float r) { return Math.abs(playerX - x) < r; }

    // ------------------------------------------------------------------ update
    private void update(float dt) {
        Player p = game.state.player;
        game.state.clock.tick(dt);
        game.state.storm.tick(dt);
        game.state.playSeconds += dt;
        boolean nearHeat = nearPod(545, 170) || nearPod(1520, 170);
        Survival.tick(p, dt, false, game.state.clock.isNight(), game.state.storm.active, nearHeat, false);

        if (Survival.isFainted(p)) {
            p.hp = (int) p.maxHp();
            p.satiety += (100 - (double) p.satiety) * 0.5;
            p.warmth += (100 - (double) p.warmth) * 0.5;
            p.battery += (100 - (double) p.battery) * 0.5;
            game.state.notice = "Bạn kiệt sức, pet kéo bạn về Khu Nhà Cành và chăm sóc cho bạn...";
            game.saveGame();
            gone = true;
            game.gotoPlot();
            return;
        }

        for (Npc n : npcs) {
            if (n.range > 0) {
                n.x += n.dir * n.speed * dt;
                n.walk += dt * 6;
                if (n.x > n.home + n.range) n.dir = -1;
                if (n.x < n.home - n.range) n.dir = 1;
            }
            n.lineT += dt;
            if (n.lineT > 5) { n.lineT = 0; n.lineIdx = (n.lineIdx + 1) % n.lines.length; }
        }

        if (emoteT > 0) { emoteT -= dt; if (emoteT <= 0) emote = 0; }

        float move = 0;
        float speedFactor = 1f; // 1 = walk, 1.5 = run
        stickRun = false;
        if (panel == null) {
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
                move -= 1; if (shift) speedFactor = 1.5f;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                move += 1; if (shift) speedFactor = 1.5f;
            }
            // movement sticks: fixed stick bottom-left is primary; a floating stick
            // anywhere else in the lower area still works as a secondary control.
            if (Gdx.input.justTouched()) {
                Vector2 hp = Ui.pointer(hudVp);
                float jdx = hp.x - JOY_X, jdy = hp.y - JOY_Y;
                boolean onFixedJoy = jdx * jdx + jdy * jdy <= 120 * 120;
                Spot near = nearestSpot();
                boolean onInteractBtn = near != null && hp.x > 1046 && hp.x < 1264 && hp.y > 44 && hp.y < 102;
                boolean onHud = (hp.x >= 344 && hp.x <= 936 && hp.y <= 138)
                    || (hp.x >= 232 && hp.x <= 992 && hp.y >= 16 && hp.y <= 132);
                if (onFixedJoy) { joyMode = 1; joyBase.set(JOY_X, JOY_Y); joyVec = 0; }
                else if (hp.y < 330 && !onInteractBtn && !onHud) { joyMode = 2; joyBase.set(hp); joyVec = 0; }
            }
            if (joyMode > 0 && Gdx.input.isTouched()) {
                Vector2 hp = Ui.pointer(hudVp);
                joyVec = clamp((hp.x - joyBase.x) / 55f, -1, 1);
            }
            if (!Gdx.input.isTouched()) { joyMode = 0; joyVec = 0; }
            // Screenshot self-test: hold the fixed stick at full tilt through the
            // capture frame (shotFrames hits 90 in render) so the shot shows the
            // run state: knob at the rim, gold, hero moving at run speed.
            if (game.screenshotDir != null && shotFrames >= 20 && shotFrames < 90) {
                joyMode = 1; joyBase.set(JOY_X, JOY_Y); joyVec = 1f;
            }
            // Walk/run comes from stick deflection, there is no run toggle button:
            // small dead zone at the centre, partial tilt = walk, tilt to the rim
            // (>= 85% of radius) = run. The floating secondary stick always walks.
            if (joyMode > 0 && Math.abs(joyVec) > 0.22f) {
                move = Math.signum(joyVec);
                speedFactor = (joyMode == 1 && Math.abs(joyVec) >= 0.85f) ? 1.5f : 1.0f;
            }
        } else {
            joyMode = 0;
        }
        stickRun = move != 0 && speedFactor > 1f;
        moving = move != 0;

        if (move != 0) {
            facing = move > 0 ? 1 : -1;
            double spd = p.speed() * speedFactor;
            playerX = clamp(playerX + (float) (move * spd * dt), 50, WORLD_W - 50);
            walkPhase += dt * (stickRun ? 15 : 11);
        }
        p.x = playerX;

        Spot near = nearestSpot();
        if (panel == null && near != null && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            openSpot(near);
            if (gone) return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            panel = panel == null ? "pause" : null;
            game.sound.play("click");
        }

        saveT += dt;
        if (saveT > 30) { saveT = 0; game.saveGame(); }

        if (game.state.notice != null && !game.state.notice.isEmpty() && !game.state.notice.equals(lastNotice)) {
            lastNotice = game.state.notice;
            noticeT = 0;
        }
        noticeT += dt;
    }

    private void openSpot(Spot s) {
        game.sound.play("click");
        if ("plot".equals(s.id)) {
            game.state.player.x = 300;
            game.saveGame();
            gone = true;
            game.gotoPlot();
        } else if ("adv".equals(s.id)) {
            game.state.player.x = 200;
            game.saveGame();
            gone = true;
            game.gotoAdventure();
        } else {
            panel = s.id;
        }
    }

    // ------------------------------------------------------------------ render
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

        // ---- world ----
        float viewW = worldVp.getWorldWidth();
        float camX = clamp(playerX, viewW / 2, WORLD_W - viewW / 2);
        OrthographicCamera cam = (OrthographicCamera) worldVp.getCamera();
        cam.position.set(camX, NeoLangGame.VH / 2f, 0);
        worldVp.apply();
        game.batch.setProjectionMatrix(cam.combined);

        boolean storm = game.state.storm.active;
        if (game.assets.townPlate != null) {
            // Layered map: the far plate is ONLY distant scenery (city skyline
            // on the trunk, sky, an empty tiled plaza) — no baked-in buildings
            // or people, so the game's own building sprites, NPCs and name tags
            // never pile onto a finished painting the way the old full-scene
            // town.jpg did.
            game.batch.begin();
            Texture plate = game.assets.townPlate;
            float tileW = NeoLangGame.VH * plate.getWidth() / (float) plate.getHeight();
            float off = -((camX * 0.5f) % tileW);
            for (float bx = camX - viewW / 2 + off - tileW; bx < camX + viewW / 2 + tileW; bx += tileW) {
                game.batch.draw(plate, bx, 0, tileW, NeoLangGame.VH);
            }
            game.batch.end();
        } else if (game.assets.town != null) {
            game.batch.begin();
            for (float bx = 0; bx < WORLD_W; bx += 1400) {
                game.batch.draw(game.assets.town, bx, 0, 1400, NeoLangGame.VH);
            }
            game.batch.end();
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0.07f, 0.10f, 0.10f, 0.42f);
            game.shapes.rect(camX - viewW / 2 - 10, 0, viewW + 20, GROUND);
            game.shapes.setColor(0.25f, 0.95f, 1f, 0.55f);
            game.shapes.rect(camX - viewW / 2 - 10, GROUND - 3, viewW + 20, 3);
            game.shapes.end();
        } else {
            Draw.drawSky(game.shapes, camX - viewW / 2 - 10, viewW + 20, dayness(), storm, time);
            Draw.drawTrunkWorld(game.shapes, camX - viewW / 2 - 10, WORLD_W, GROUND, 0, time);
        }

        Spot near = nearestSpot();
        // Building sprites stand at the interaction points; feedback is a small
        // floating name plate + a soft glow at the base — NO dark boxes over art.
        drawBuilding(game.assets.bldCafe, 545, 216, 430, 230, 215,
            new Color(1f, 0.55f, 0.35f, 1), "Cafe Pina", near != null && "cafe".equals(near.id));
        drawBuilding(game.assets.bldFashion, 990, 202, 880, 220, 200,
            new Color(1f, 0.40f, 0.75f, 1), "Thời Trang Miro", near != null && "miro".equals(near.id));
        drawBuilding(game.assets.bldStation, 1520, 238, 1400, 240, 225,
            new Color(0.35f, 0.95f, 1f, 1), "Trạm Định Hướng", near != null && "station".equals(near.id));
        drawBuilding(game.assets.bldBank, 2090, 198, 1980, 220, 190,
            new Color(1f, 0.82f, 0.25f, 1), "Ngân Hàng Lá", near != null && "bank".equals(near.id));
        drawBuilding(game.assets.bldPet, 2440, 208, 2330, 220, 190,
            new Color(0.45f, 0.95f, 0.55f, 1), "Pet Shop", near != null && "pet".equals(near.id));

        drawPortalGate(85, "Khu Nhà Cành", -1, near != null && "plot".equals(near.id));
        drawPortalGate(2725, "Khu Vành Đai Neon — Farm", 1, near != null && "adv".equals(near.id));
        drawQuestBoard(near != null && "board".equals(near.id));

        // sparse painted props as decoration (never blocking the walk line)
        drawDecor(game.assets.propLamp, 300, 148);
        drawDecor(game.assets.propLamp, 1330, 148);
        drawDecor(game.assets.propTable, 452, 60);
        drawDecor(game.assets.propFlower, 1185, 82);
        drawDecor(game.assets.propFlower, 1905, 82);
        drawDecor(game.assets.propChest, 2248, 52);

        // NPCs (painted figures; shape hero as fallback)
        for (Npc n : npcs) {
            if (!Draw.drawFigureSprite(game.batch, game.assets, n.gender, n.fig, n.x, GROUND, 1.12f,
                    n.dir, n.range > 0, n.walk, time)) {
                Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, n.x, GROUND, 1.12f, n.gender,
                    n.suit, n.hair, n.style, n.eye, 1, "bo_tan_cu_dan", GameClass.NONE, n.dir, n.walk, time);
            }
        }
        // pet + player (painted sprites; shapes as fallback)
        if (!Draw.drawPetSprite(game.batch, game.assets, playerX - facing * 44, GROUND, time, petTier())) {
            Draw.drawPet(game.shapes, playerX - facing * 44, GROUND, time, petTier());
        }
        float heroY = GROUND - (emote == 3 ? 5 : 0);
        if (!Draw.drawHeroSprite(game.batch, game.shapes, game.assets, playerX, heroY, 1.18f, p.gender,
                p.outfitId, p.gameClass, facing, moving, walkPhase, time)) {
            Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, playerX, heroY, 1.18f, p.gender,
                p.suitColorIdx, p.hairColorIdx, p.hairStyleIdx, p.eyeColorIdx, p.skinToneIdx, p.outfitId, p.gameClass,
                facing, walkPhase, time);
        }
        if (emote == 1) { // wave: sparkle trail over the raised hand
            game.shapes.begin(ShapeType.Filled);
            for (int i = 0; i < 3; i++) {
                float a = 0.5f + 0.5f * (float) Math.sin(time * 8 + i);
                game.shapes.setColor(0.35f, 0.95f, 1f, a);
                game.shapes.circle(playerX + facing * (20 + i * 6), GROUND + 82 + i * 5, 2.4f);
            }
            game.shapes.end();
        } else if (emote == 2) {
            Ui.iconSmile(game.shapes, playerX, GROUND + 96, 10, Ui.GOLD);
        }

        // name tags + bubbles
        for (Npc n : npcs) {
            Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, n.name, n.x, GROUND + 84);
            drawBubble(n.x, GROUND + 138, n.lines[n.lineIdx]);
        }
        Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, p.name + " — Cấp " + p.level, playerX, GROUND + 88);

        // night + storm overlays
        float nightA = game.state.clock.isNight() ? 0.32f : 0f;
        if (storm) nightA += 0.10f;
        if (nightA > 0) {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0.05f, 0.02f, 0.15f, nightA);
            game.shapes.rect(camX - viewW / 2 - 10, 0, viewW + 20, NeoLangGame.VH);
            if (storm && (time % 3.7f) < 0.07f) {
                game.shapes.setColor(0.85f, 0.7f, 1f, 0.25f);
                game.shapes.rect(camX - viewW / 2 - 10, 0, viewW + 20, NeoLangGame.VH);
            }
            game.shapes.end();
        }

        drawHud(near);

        if (game.screenshotDir != null) {
            shotFrames++;
            if (shotFrames == 90) {
                game.captureShot("town.png");
                game.gotoAdventure();
            }
        }
    }

    /** Soft contact shadow under a building/prop, plus a warm pulsing glow at
     *  its base while the player is near (the interaction feedback now that the
     *  dark shop panels are gone). */
    private void groundGlow(float cx, float w, boolean active) {
        game.shapes.begin(ShapeType.Filled);
        if (active) {
            float p = 0.5f + 0.5f * (float) Math.sin(time * 5);
            game.shapes.setColor(1f, 0.85f, 0.3f, 0.15f + 0.13f * p);
            game.shapes.ellipse(cx - w / 2 - 12, GROUND - 13, w + 24, 26);
        }
        game.shapes.setColor(0, 0, 0, 0.22f);
        game.shapes.ellipse(cx - w / 2, GROUND - 6, w, 13);
        game.shapes.end();
    }

    /** Small floating name plate above a building (replaces the old sign band
     *  baked into the dark pod box). Active = gold trim + bobbing arrow. */
    private void drawSign(float cx, float topY, String label, Color color, boolean active,
                          String sub, boolean arrow) {
        float w = Ui.textWidth(game.assets.fontSmall, label) + 26;
        float y = topY + 8;
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.05f, 0.09f, 0.13f, 0.85f);
        game.shapes.rect(cx - w / 2, y, w, 26);
        game.shapes.setColor(active ? Ui.GOLD : color);
        game.shapes.rect(cx - w / 2, y, w, 2);
        game.shapes.rect(cx - w / 2, y + 24, w, 2);
        if (active && arrow) {
            float bob = (float) Math.sin(time * 4) * 4;
            game.shapes.setColor(1f, 0.8f, 0.25f, 1);
            float ay = y + 40 + bob;
            game.shapes.triangle(cx - 10, ay, cx + 10, ay, cx, ay - 12);
        }
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, label, cx, y + 18, Color.WHITE, Align.center);
        if (sub != null) {
            Ui.label(game.batch, game.assets.fontSmall, sub, cx, y + 48,
                active ? Ui.GOLD : Ui.TEXT_DIM, Align.center);
        }
        game.batch.end();
    }

    /** One town building: painted sprite at its interaction point, or the old
     *  shape pod (same geometry) when the sprite failed to load. */
    private void drawBuilding(Texture t, float cx, float h, float podX, float podW, float podH,
                              Color signColor, String label, boolean active) {
        if (t != null) {
            float w = h * t.getWidth() / (float) t.getHeight();
            groundGlow(cx, w, active);
            Draw.drawSprite(game.batch, t, cx, GROUND - 2, h);
            drawSign(cx, GROUND + h, label, signColor, active, null, true);
        } else {
            Draw.drawPod(game.shapes, game.batch, game.assets.fontSmall, podX, GROUND, podW, podH,
                signColor, label, active, time);
        }
    }

    /** World gates use the painted portal sprite (the flat cyan box is gone). */
    private void drawPortalGate(float cx, String label, float dir, boolean active) {
        Texture t = game.assets.bldPortal;
        if (t != null) {
            groundGlow(cx, 150, active);
            Draw.drawSprite(game.batch, t, cx, GROUND - 2, 172);
            drawSign(cx, GROUND + 172, label, Ui.CYAN, active, null, true);
        } else {
            drawGate(cx - 45, label, dir, active);
        }
    }

    /** The quest board is a painted prop now, standing by the career station. */
    private void drawQuestBoard(boolean active) {
        Texture t = game.assets.propBoard;
        if (t != null) {
            groundGlow(1760, 150, active);
            Draw.drawSprite(game.batch, t, 1760, GROUND - 2, 116);
            drawSign(1760, GROUND + 116, "Bảng Nhiệm Vụ", Ui.CYAN, active,
                active ? "Chạm để mở" : "Hologram", false);
        } else {
            drawBoard(active);
        }
    }

    /** Pure decoration: a painted prop with a soft contact shadow. */
    private void drawDecor(Texture t, float cx, float h) {
        if (t == null) return;
        float w = h * t.getWidth() / (float) t.getHeight();
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0, 0, 0, 0.18f);
        game.shapes.ellipse(cx - w / 2, GROUND - 5, w, 10);
        game.shapes.end();
        Draw.drawSprite(game.batch, t, cx, GROUND - 2, h);
    }

    private void drawGate(float x, String label, float dir, boolean highlight) {
        float p = 0.5f + 0.5f * (float) Math.sin(time * 4);
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.20f, 0.30f, 0.38f, 1);
        game.shapes.rect(x, GROUND, 90, 150);
        game.shapes.setColor(0.35f, 0.85f, 0.95f, 0.5f + 0.3f * p);
        game.shapes.rect(x + 8, GROUND + 10, 74, 130);
        game.shapes.setColor(1f, 0.85f, 0.3f, 1);
        float ax = x + 45 + dir * (6 + p * 5);
        game.shapes.triangle(ax + dir * 14, GROUND + 78, ax - dir * 10, GROUND + 64, ax - dir * 10, GROUND + 92);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, label, x + 45, GROUND + 168, Ui.TEXT, Align.center);
        game.batch.end();
    }

    private void drawBoard(boolean highlight) {
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.10f, 0.16f, 0.24f, 1);
        game.shapes.rect(1690, GROUND, 140, 120);
        float p = 0.5f + 0.5f * (float) Math.sin(time * 3);
        game.shapes.setColor(0.30f, 0.90f, 1f, 0.35f + 0.3f * p);
        game.shapes.rect(1698, GROUND + 34, 124, 78);
        game.shapes.setColor(0.4f, 0.45f, 0.5f, 1);
        game.shapes.rect(1752, GROUND - 2, 16, 36);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Bảng Nhiệm Vụ", 1760, GROUND + 96, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, highlight ? "Chạm để mở" : "Hologram", 1760, GROUND + 66, Ui.TEXT_DIM, Align.center);
        game.batch.end();
    }

    private void drawBubble(float cx, float y, String text) {
        float w = Ui.textWidth(game.assets.fontSmall, text) + 18;
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(1, 1, 1, 0.88f);
        game.shapes.rect(cx - w / 2, y, w, 24);
        game.shapes.triangle(cx - 5, y, cx + 5, y, cx, y - 7);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, text, cx, y + 17, new Color(0.10f, 0.14f, 0.18f, 1), Align.center);
        game.batch.end();
    }

    // ------------------------------------------------------------------ HUD
    private boolean iconBtn(float x, float y, String kind) {
        // glass rounded square + PAINTED icon asset (same slot/function as before)
        Texture t = "mail".equals(kind) ? game.assets.icoMail
            : "smile".equals(kind) ? game.assets.icoSmile
            : "bag".equals(kind) ? game.assets.icoBag : game.assets.icoGear;
        if (t == null) {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(Ui.BTN);
            game.shapes.rect(x, y, 46, 46);
            game.shapes.end();
            if ("mail".equals(kind)) Ui.iconMail(game.shapes, x + 23, y + 23, 11, Ui.CYAN);
            else if ("smile".equals(kind)) Ui.iconSmile(game.shapes, x + 23, y + 23, 11, Ui.CYAN);
            else if (!"bag".equals(kind)) Ui.iconGear(game.shapes, x + 23, y + 23, 11, Ui.CYAN);
            return Ui.tapped(hudVp, x, y, 46, 46);
        }
        return Hud.glassIconBtn(game, hudVp, t, x, y, 46);
    }

    private void drawHud(Spot near) {
        hudVp.apply();
        game.batch.setProjectionMatrix(hudVp.getCamera().combined);
        Player p = game.state.player;

        // ---- top-left: slim character card (shared with Adventure) ----
        // Portrait in the painted cyber frame + level badge, name/class, XP bar
        // with numbers; survival chips hang in a loose row under the card.
        float clBottom = Hud.drawCharCluster(game, false, 0);
        if (panel == null && Ui.tapped(hudVp, Hud.CL_X, clBottom, Hud.CL_W, Hud.CL_TOP - clBottom)) {
            panel = "char"; game.sound.play("click");
        }

        // ---- top-right: minimap (building/NPC gold dots, portal dots, player)
        // with the currency pills as their own row under the frame ----
        drawMinimap(p);
        Hud.drawCurrency(game, Hud.MM_Y - 8);

        // storm banner (top centre, clear of the cluster and the minimap)
        if (game.state.storm.active) {
            Ui.panel(game.shapes, 415, 662, 450, 44, new Color(0.30f, 0.08f, 0.38f, 0.92f));
            game.batch.begin();
            Hud.icon(game.batch, game.assets.icoQuest, 439, 684, 26);
            Ui.label(game.batch, game.assets.fontSmall,
                "BÃO DỮ LIỆU — còn " + (int) Math.ceil((double) game.state.storm.activeLeft) + "s — hạ quái: "
                    + game.state.storm.kills + "/" + game.state.storm.objectiveKills,
                652, 690, new Color(1f, 0.75f, 0.95f, 1), Align.center);
            game.batch.end();
        }

        // ---- bottom-left: movement joystick ----
        drawJoystick();

        // ---- button columns flanking the chat frame (user layout): LEFT column
        // mail on TOP of settings, RIGHT column emote on TOP of the bag ----
        // (no walk/run toggle: speed comes from joystick deflection)
        if (iconBtn(240, 78, "mail")) { panel = "mail"; game.sound.play("click"); }
        if (iconBtn(240, 24, "menu")) { panel = "menu"; game.sound.play("click"); }
        if (iconBtn(938, 78, "smile")) { panel = "emote"; game.sound.play("click"); }
        if (iconBtn(938, 24, "bag")) { panel = "bag"; game.sound.play("click"); }

        // ---- bottom-centre: chat frame ----
        drawChatFrame();

        // ---- bottom-right: action cluster (context interact) ----
        if (panel == null && near != null) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, fitText(near.label, 250), 1155, 128, Ui.TEXT_DIM, Align.center);
            Ui.label(game.batch, game.assets.fontSmall, "hoặc phím E", 1155, 112, Ui.TEXT_DIM, Align.center);
            game.batch.end();
            if (Ui.button(game.batch, game.shapes, game.assets.font, hudVp, 1046, 44, 218, 58, shortAction(near))) {
                openSpot(near);
                if (gone) return;
            }
        }

        // toast
        if (lastNotice != null && !lastNotice.isEmpty() && noticeT < 4.5f) {
            float w = Math.min(790, Ui.textWidth(game.assets.fontSmall, lastNotice) + 66);
            Ui.panel(game.shapes, 640 - w / 2, 470, w, 40, new Color(0.06f, 0.14f, 0.10f, 0.94f));
            game.batch.begin();
            Hud.icon(game.batch, game.assets.icoQuest, 640 - w / 2 + 21, 490, 24);
            Ui.label(game.batch, game.assets.fontSmall, lastNotice, 655, 496, Ui.GREEN, Align.center);
            game.batch.end();
        }

        if (panel != null) drawPanel();
    }

    /** Town minimap: gold dots for the main buildings/NPCs + quest board,
     *  green dots for the two world portals, white-cyan player marker. */
    private void drawMinimap(Player p) {
        Hud.drawMinimapBase(game, "Neo Làng");
        game.shapes.begin(ShapeType.Filled);
        Color gold = new Color(1f, 0.8f, 0.3f, 1);
        float[] spots = {545, 990, 1230, 1520, 1760, 2090, 2445};
        for (float sx : spots) Hud.dot(game.shapes, Hud.mmX(sx, WORLD_W), Hud.mmGroundY(), 3, gold);
        Color portal = new Color(0.35f, 1f, 0.75f, 1);
        Hud.dot(game.shapes, Hud.mmX(70, WORLD_W), Hud.mmGroundY(), 3.6f, portal);
        Hud.dot(game.shapes, Hud.mmX(2730, WORLD_W), Hud.mmGroundY(), 3.6f, portal);
        Hud.playerDot(game.shapes, Hud.mmX(p.x, WORLD_W), Hud.mmGroundY(), facing < 0 ? -1 : 1);
        game.shapes.end();
    }

    /** Fixed movement stick, bottom-left: painted base + knob assets (shared
     *  with Adventure via Hud). The floating stick (mode 2) still appears
     *  wherever else the player touches low. */
    private void drawJoystick() {
        Hud.drawJoystick(game, JOY_X, JOY_Y, joyMode, joyBase, joyVec, stickRun);
    }

    private Npc nearestNpc() {
        Npc best = null;
        float bd = Float.MAX_VALUE;
        for (Npc n : npcs) {
            float d = Math.abs(n.x - playerX);
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }

    private String fitText(String s, float maxW) {
        if (s == null) return "";
        if (Ui.textWidth(game.assets.fontSmall, s) <= maxW) return s;
        while (s.length() > 4 && Ui.textWidth(game.assets.fontSmall, s + "..") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private String shortAction(Spot s) {
        if ("plot".equals(s.id)) return "Về nhà";
        if ("adv".equals(s.id)) return "Ra Vành Đai Neon";
        if ("board".equals(s.id)) return "Xem nhiệm vụ";
        return "Nói chuyện";
    }

    /** Bottom-centre chat frame: short log (recent chat + nearest NPC's current line)
     *  over a "tap to chat" strip; tapping the frame opens the full chat panel. */
    private void drawChatFrame() {
        Ui.panel(game.shapes, 352, 16, 576, 114, new Color(0.05f, 0.10f, 0.15f, 0.88f));
        List<String> lines = new ArrayList<String>();
        int from = Math.max(0, chatLog.size() - 2);
        for (int i = from; i < chatLog.size(); i++) lines.add(chatLog.get(i));
        Npc nn = nearestNpc();
        if (nn != null) lines.add(nn.name + ": " + nn.lines[nn.lineIdx]);
        game.batch.begin();
        float y = 114;
        for (String s : lines) {
            Ui.label(game.batch, game.assets.fontSmall, fitText(s, 540), 364, y, Ui.TEXT, Align.left);
            y -= 22;
        }
        game.batch.end();
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(Ui.BTN);
        game.shapes.rect(364, 24, 552, 28);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Chạm để chat...", 376, 44, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        if (panel == null && Ui.tapped(hudVp, 352, 16, 576, 114)) { panel = "chat"; game.sound.play("click"); }
    }

    // ------------------------------------------------------------------ panels
    private void drawPanel() {
        Player p = game.state.player;
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0, 0, 0, 0.45f);
        game.shapes.rect(0, 0, NeoLangGame.VW, NeoLangGame.VH);
        game.shapes.end();
        switch (panel) {
            case "menu": panelMenu(); break;
            case "bag": panelBag(); break;
            case "mail": panelMail(); break;
            case "chat": panelChat(); break;
            case "char": panelChar(); break;
            case "quest": case "board": panelQuest(); break;
            case "settings": panelSettings(); break;
            case "pause": panelPause(); break;
            case "chief": panelChief(); break;
            case "cafe": panelCafe(); break;
            case "miro": panelMiro(); break;
            case "bank": panelBank(); break;
            case "pet": panelPet(); break;
            case "station": panelStation(); break;
            case "emote": panelEmote(); break;
            default: panel = null; break;
        }
    }

    private boolean closeBtn(float x, float y) {
        return btn(x, y, 90, 36, "Đóng");
    }

    private void panelMenu() {
        Ui.panel(game.shapes, 440, 190, 400, 380);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Menu", 640, 545, Ui.CYAN, Align.center);
        game.batch.end();
        if (btn(480, 452, 320, 46, "Túi đồ")) panel = "bag";
        if (btn(480, 396, 320, 46, "Nhiệm vụ")) panel = "quest";
        if (btn(480, 340, 320, 46, "Cài đặt")) panel = "settings";
        if (btn(480, 284, 320, 46, "Lưu game")) { game.saveGame(); game.state.notice = "Đã lưu game."; game.sound.play("coin"); }
        if (btn(480, 228, 320, 46, "Về màn hình chính")) { game.saveGame(); gone = true; game.gotoTitle(); }
    }

    private void panelBag() {
        Ui.panel(game.shapes, 390, 150, 500, 440);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Túi đồ", 640, 562, Ui.CYAN, Align.center);
        float y = 516;
        int shown = 0;
        for (ItemDef d : Items.ALL.values()) {
            int n = game.state.player.countItem(d.id);
            if (n <= 0) continue;
            Ui.label(game.batch, game.assets.fontSmall, d.name + "  x" + n, 420, y, Ui.TEXT, Align.left);
            y -= 26;
            shown++;
            if (y < 210) break;
        }
        if (shown == 0) Ui.label(game.batch, game.assets.fontSmall, "Túi trống. Farm ở Vành Đai Neon hoặc ghé Cafe Pina nhé.", 420, y, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        if (closeBtn(735, 168)) panel = null;
    }

    private void panelMail() {
        Ui.panel(game.shapes, 390, 170, 500, 400);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Hộp thư", 640, 542, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Từ Trưởng Trạm Gốc: Chào cư dân mới! Gặp ta ở", 420, 500, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "giữa làng để nhận nhiệm vụ đầu tiên nhé.", 420, 476, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Từ Pina: Cafe khai trương, giảm giá đồ ăn tuần này!", 420, 436, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Từ Hệ Thống Cây Mẹ: Bão dữ liệu có thể tới bất cứ lúc nào.", 420, 396, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Hãy để ý banner tím trên màn hình khi bão tới.", 420, 372, Ui.TEXT, Align.left);
        game.batch.end();
        if (closeBtn(735, 188)) panel = null;
    }

    private void panelChat() {
        Ui.panel(game.shapes, 390, 140, 500, 460);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Chat làng", 640, 572, Ui.CYAN, Align.center);
        float y = 528;
        int from = Math.max(0, chatLog.size() - 7);
        for (int i = from; i < chatLog.size(); i++) {
            Ui.label(game.batch, game.assets.fontSmall, chatLog.get(i), 420, y, Ui.TEXT, Align.left);
            y -= 28;
        }
        game.batch.end();
        for (int i = 0; i < QUICK_CHAT.length; i++) {
            if (btn(420 + i * 156, 196, 148, 40, QUICK_CHAT[i])) {
                Player p = game.state.player;
                chatLog.add(p.name + ": " + QUICK_CHAT[i]);
                chatLog.add("NPC: " + CHAT_REPLIES[chatReplyIdx % CHAT_REPLIES.length]);
                chatReplyIdx++;
                game.sound.play("click");
                // speech bubble over the hero for a moment
                emote = 2; emoteT = 2;
            }
        }
        if (closeBtn(735, 152)) panel = null;
    }

    private void panelChar() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 340, 120, 600, 500);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Nhân vật — " + p.name, 640, 592, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall,
            "Cấp " + p.level + "   Nghề: " + (p.gameClass == null || p.gameClass == GameClass.NONE ? "Chưa chọn" : p.gameClass.displayName),
            370, 556, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall,
            "Công " + (int) (double) p.atk() + "   Thủ " + (int) (double) p.def() + "   Tốc độ " + (int) (double) p.speed(),
            370, 528, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Xu Lá: " + p.xuLa + "   Tinh Thể: " + p.tinhThe, 370, 500, Ui.GOLD, Align.left);
        game.batch.end();
        statBar(370, 452, "HP", (double) p.hp / Math.max(1, (double) p.maxHp()), Ui.RED, (int) (double) p.hp + "/" + (int) (double) p.maxHp());
        statBar(370, 422, "No", (double) p.satiety / 100.0, Ui.GREEN, (int) (double) p.satiety + "/100");
        statBar(370, 392, "Ấm", (double) p.warmth / 100.0, Ui.ACCENT, (int) (double) p.warmth + "/100");
        statBar(370, 362, "Pin", (double) p.battery / 100.0, Ui.CYAN, (int) (double) p.battery + "/100");
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Đồ ăn trong túi (bấm để ăn):", 370, 330, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        float by = 282;
        boolean any = false;
        for (ItemDef d : Items.ALL.values()) {
            if (!"FOOD".equals(String.valueOf(d.type))) continue;
            int n = p.countItem(d.id);
            if (n <= 0) continue;
            any = true;
            if (btn(370, by, 380, 38, "Ăn: " + d.name + " (x" + n + ")")) {
                Survival.applyItem(p, d);
                game.state.quests.onEvent(ObjectiveType.EAT, 1);
                game.sound.play("eat");
                game.state.notice = "Đã ăn " + d.name + ".";
            }
            by -= 46;
            if (by < 150) break;
        }
        if (!any) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Không có đồ ăn. Ghé Cafe Pina hoặc tự nấu ở nhà.", 370, 300, Ui.TEXT_DIM, Align.left);
            game.batch.end();
        }
        if (closeBtn(815, 140)) panel = null;
    }

    private void statBar(float x, float y, String name, double frac, Color c, String val) {
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, name, x, y + 14, Ui.TEXT_DIM, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, val, x + 300, y + 14, Ui.TEXT, Align.left);
        game.batch.end();
        Ui.bar(game.shapes, x + 44, y, 240, 12, (float) frac, new Color(0.1f, 0.12f, 0.16f, 1), c);
    }

    private void panelQuest() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 340, 110, 600, 520);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Nhiệm vụ", 640, 602, Ui.CYAN, Align.center);
        QuestDef main = game.state.quests.currentMain();
        float y = 566;
        if (main != null) {
            Ui.label(game.batch, game.assets.fontSmall, "Chính: " + main.name, 370, y, Ui.GOLD, Align.left); y -= 24;
            Ui.label(game.batch, game.assets.fontSmall, main.desc, 370, y, Ui.TEXT, Align.left); y -= 24;
            Ui.label(game.batch, game.assets.fontSmall,
                "Tiến độ: " + game.state.quests.mainProgress + "/" + main.target
                    + "   Thưởng: " + main.rewardXp + " XP, " + main.rewardXu + " Xu", 370, y, Ui.TEXT_DIM, Align.left); y -= 8;
        } else {
            Ui.label(game.batch, game.assets.fontSmall, "Đã hoàn thành mọi nhiệm vụ chính. Tuyệt vời!", 370, y, Ui.GOLD, Align.left); y -= 24;
        }
        game.batch.end();
        if (main != null && game.state.quests.currentComplete()) {
            if (btn(370, y - 40, 220, 38, "Nhận thưởng")) {
                game.state.quests.claimMain(p);
                game.sound.play("levelup");
                game.state.notice = "Đã nhận thưởng nhiệm vụ chính!";
            }
            y -= 48;
        }
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Hằng ngày:", 370, y - 6, Ui.CYAN, Align.left);
        game.batch.end();
        y -= 34;
        List<QuestDef> dailies = game.state.quests.dailies();
        for (int i = 0; i < dailies.size(); i++) {
            QuestDef q = dailies.get(i);
            final int idx = i;
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall,
                q.name + " — " + game.state.quests.dailyProgress(idx) + "/" + q.target, 370, y + 24, Ui.TEXT, Align.left);
            game.batch.end();
            if (game.state.quests.dailyDone(idx)) {
                if (btn(730, y, 170, 36, "Nhận thưởng")) {
                    game.state.quests.claimDaily(p, idx);
                    game.sound.play("coin");
                    game.state.notice = "Đã nhận thưởng nhiệm vụ ngày!";
                }
            }
            y -= 46;
            if (y < 170) break;
        }
        if (closeBtn(815, 128)) panel = null;
    }

    private void panelSettings() {
        Ui.panel(game.shapes, 440, 190, 400, 340);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Cài đặt", 640, 505, Ui.CYAN, Align.center);
        game.batch.end();
        Ui.drawSettings(game, hudVp, 470, 440, 340);
        if (closeBtn(735, 208)) panel = null;
    }

    private void panelPause() {
        Ui.panel(game.shapes, 440, 210, 400, 330);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Tạm dừng", 640, 515, Ui.CYAN, Align.center);
        game.batch.end();
        if (btn(480, 424, 320, 46, "Tiếp tục")) panel = null;
        if (btn(480, 368, 320, 46, "Lưu game")) { game.saveGame(); game.state.notice = "Đã lưu game."; game.sound.play("coin"); }
        if (btn(480, 312, 320, 46, "Cài đặt")) panel = "settings";
        if (btn(480, 256, 320, 46, "Màn hình chính")) { game.saveGame(); gone = true; game.gotoTitle(); }
    }

    private void panelChief() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 340, 170, 600, 380);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Trưởng Trạm Gốc", 640, 522, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Chào mừng con tới Neo Làng, ngôi làng trên Cây Mẹ.", 370, 486, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Hãy làm quen với làng, ăn uống đầy đủ và lớn lên từng ngày.", 370, 460, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Khi đạt cấp 5 và xong nhiệm vụ mở đầu, qua Trạm Định", 370, 434, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Hướng ở giữa làng để chọn nghề cho mình.", 370, 408, Ui.TEXT, Align.left);
        QuestDef main = game.state.quests.currentMain();
        if (main != null) {
            Ui.label(game.batch, game.assets.fontSmall, "Nhiệm vụ hiện tại: " + main.name + " (" + game.state.quests.mainProgress + "/" + main.target + ")", 370, 366, Ui.GOLD, Align.left);
        }
        game.batch.end();
        if (main != null && main.type == ObjectiveType.TALK && !game.state.quests.currentComplete()) {
            if (btn(370, 300, 260, 42, "Trò chuyện (nhiệm vụ)")) {
                game.state.quests.onEvent(ObjectiveType.TALK, 1);
                game.sound.play("click");
                game.state.notice = "Đã trò chuyện với Trưởng Trạm.";
            }
        }
        if (game.state.quests.currentComplete()) {
            if (btn(370, 248, 260, 42, "Nhận thưởng nhiệm vụ")) {
                game.state.quests.claimMain(p);
                game.sound.play("levelup");
                game.state.notice = "Đã nhận thưởng từ Trưởng Trạm!";
            }
        }
        if (closeBtn(815, 188)) panel = null;
    }

    private void panelCafe() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 320, 110, 640, 520);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Cafe Pina — Mua đồ ăn", 640, 602, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Xu Lá: " + p.xuLa, 350, 570, Ui.GOLD, Align.left);
        game.batch.end();
        float y = 516;
        for (ItemDef d : Items.ALL.values()) {
            if (!"FOOD".equals(String.valueOf(d.type))) continue;
            if (btn(350, y, 360, 40, d.name + " — " + d.buyPrice + " Xu")) {
                if (p.spendXu(d.buyPrice)) {
                    p.addItem(d.id, 1);
                    game.state.quests.onEvent(ObjectiveType.BUY, 1);
                    game.sound.play("buy");
                    game.state.notice = "Đã mua " + d.name + ".";
                } else {
                    game.sound.play("error");
                    game.state.notice = "Không đủ Xu Lá.";
                }
            }
            y -= 48;
            if (y < 330) break;
        }
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Bán lại (đồ trong túi):", 350, 322, Ui.CYAN, Align.left);
        game.batch.end();
        y = 268;
        boolean any = false;
        for (ItemDef d : Items.ALL.values()) {
            int n = p.countItem(d.id);
            if (n <= 0 || (double) d.sellPrice <= 0) continue;
            if (!"FOOD".equals(String.valueOf(d.type)) && !"CROP".equals(String.valueOf(d.type))
                && !"MATERIAL".equals(String.valueOf(d.type)) && !"SEED".equals(String.valueOf(d.type))) continue;
            any = true;
            if (btn(350, y, 360, 38, "Bán " + d.name + " (x" + n + ") +" + d.sellPrice + " Xu")) {
                p.removeItem(d.id, 1);
                p.xuLa += d.sellPrice;
                game.sound.play("coin");
            }
            y -= 44;
            if (y < 140) break;
        }
        if (!any) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Chưa có gì để bán.", 350, 282, Ui.TEXT_DIM, Align.left);
            game.batch.end();
        }
        if (closeBtn(835, 128)) panel = null;
    }

    private void panelMiro() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 320, 120, 640, 500);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Thời Trang Miro", 640, 592, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Xu Lá: " + p.xuLa + "   Tinh Thể: " + p.tinhThe, 350, 560, Ui.GOLD, Align.left);
        game.batch.end();
        // outfit list: the 3 known sets first, then any other OUTFIT items
        List<ItemDef> outfits = new ArrayList<ItemDef>();
        for (String id : Draw.OUTFIT_IDS) {
            ItemDef d = Items.get(id);
            if (d != null) outfits.add(d);
        }
        for (ItemDef d : Items.ALL.values()) {
            if ("OUTFIT".equals(String.valueOf(d.type)) && !outfits.contains(d)) outfits.add(d);
        }
        float y = 496;
        for (ItemDef d : outfits) {
            boolean owned = d.id.equals(p.outfitId) || p.countItem(d.id) > 0 || "bo_tan_cu_dan".equals(d.id);
            boolean wearing = d.id.equals(p.outfitId);
            boolean byTinh = d.currency == ItemDef.CURRENCY_TINH_THE;
            String price = byTinh ? d.buyPrice + " Tinh" : d.buyPrice + " Xu";
            String label = wearing ? "Đang mặc: " + d.name
                : owned ? "Trang bị: " + d.name : "Mua: " + d.name + " — " + price;
            if (btn(350, y, 420, 44, label)) {
                if (wearing) {
                    game.state.notice = "Bạn đang mặc bộ này rồi.";
                } else if (owned) {
                    equipOutfit(d);
                } else {
                    boolean ok = byTinh ? p.spendTinh(d.buyPrice) : p.spendXu(d.buyPrice);
                    if (ok) {
                        p.addItem(d.id, 1);
                        game.state.quests.onEvent(ObjectiveType.BUY, 1);
                        equipOutfit(d);
                        game.sound.play("buy");
                    } else {
                        game.sound.play("error");
                        game.state.notice = "Không đủ tiền mua bộ này.";
                    }
                }
            }
            y -= 54;
            if (y < 170) break;
        }
        if (outfits.isEmpty()) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Cửa hàng đang nhập hàng, quay lại sau nhé.", 350, 500, Ui.TEXT_DIM, Align.left);
            game.batch.end();
        }
        if (closeBtn(835, 138)) panel = null;
    }

    private void equipOutfit(ItemDef d) {
        Player p = game.state.player;
        p.outfitId = d.id; // Draw resolves the palette from the outfit id / ItemDef.suitColor hex
        game.state.notice = "Đã mặc bộ " + d.name + ".";
        game.saveGame();
    }

    private void panelBank() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 360, 180, 560, 360);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Ngân Hàng Lá", 640, 512, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Số dư: " + p.xuLa + " Xu Lá — " + p.tinhThe + " Tinh Thể Mạch", 390, 472, Ui.GOLD, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Pin thiết bị hiện tại: " + (int) (double) p.battery + "/100", 390, 440, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Nhựa Sáng đang có: " + p.countItem("nhua_sang"), 390, 412, Ui.TEXT, Align.left);
        game.batch.end();
        if (btn(390, 340, 420, 46, "Nạp đầy pin thiết bị — 20 Xu")) {
            if (p.spendXu(20)) {
                p.battery = 100;
                game.sound.play("buy");
                game.state.notice = "Pin đã đầy 100.";
            } else {
                game.sound.play("error");
                game.state.notice = "Không đủ Xu Lá.";
            }
        }
        if (btn(390, 282, 420, 46, "Đổi 5 Nhựa Sáng -> 1 Tinh Thể")) {
            if (p.countItem("nhua_sang") >= 5) {
                p.removeItem("nhua_sang", 5);
                p.tinhThe += 1;
                game.sound.play("coin");
                game.state.notice = "Đã đổi được 1 Tinh Thể Mạch.";
            } else {
                game.sound.play("error");
                game.state.notice = "Cần 5 Nhựa Sáng (ra Vành Đai Neon farm quái nhé).";
            }
        }
        if (closeBtn(795, 198)) panel = null;
    }

    private void panelPet() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 360, 160, 560, 400);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Pet Shop — Bé Robot", 640, 532, Ui.CYAN, Align.center);
        Ui.label(game.batch, game.assets.fontSmall, "Pet của bạn: cấp " + petTier() + " — nhặt đồ ở Vành Đai Neon nhanh hơn theo cấp.", 390, 494, Ui.TEXT, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "Xu Lá: " + p.xuLa, 390, 462, Ui.GOLD, Align.left);
        game.batch.end();
        float y = 402;
        for (String id : new String[]{"pin_pet", "do_choi_pet"}) {
            ItemDef d = Items.get(id);
            if (d == null) continue;
            if (btn(390, y, 420, 44, "Mua " + d.name + " — " + d.buyPrice + " Xu (có x" + p.countItem(id) + ")")) {
                if (p.spendXu(d.buyPrice)) {
                    p.addItem(id, 1);
                    game.state.quests.onEvent(ObjectiveType.BUY, 1);
                    game.sound.play("buy");
                    game.state.notice = "Đã mua " + d.name + " cho pet.";
                } else {
                    game.sound.play("error");
                    game.state.notice = "Không đủ Xu Lá.";
                }
            }
            y -= 54;
        }
        int cost = 100 + petTier() * 100;
        if (petTier() < 3) {
            if (btn(390, y, 420, 44, "Nâng cấp pet lên cấp " + (petTier() + 1) + " — " + cost + " Xu")) {
                if (p.spendXu(cost)) {
                    p.addItem("pet_chip", 1);
                    game.sound.play("levelup");
                    game.state.notice = "Pet đã lên cấp " + petTier() + "!";
                } else {
                    game.sound.play("error");
                    game.state.notice = "Không đủ Xu Lá.";
                }
            }
        } else {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Pet đã đạt cấp tối đa.", 390, y + 30, Ui.TEXT_DIM, Align.left);
            game.batch.end();
        }
        if (closeBtn(795, 178)) panel = null;
    }

    private void panelStation() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 240, 90, 800, 540);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Trạm Định Hướng — Chọn nghề", 640, 602, Ui.CYAN, Align.center);
        game.batch.end();
        boolean can = ClassChoice.canChoose(game.state);
        if (p.gameClass != null && p.gameClass != GameClass.NONE && p.level < 10) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall,
                "Bạn đang là " + p.gameClass.displayName + ". Đổi nghề mở ở cấp 10 (hiện cấp " + p.level + ").", 640, 566, Ui.TEXT, Align.center);
            game.batch.end();
        } else if (!can) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Chưa thể chọn nghề:", 640, 566, Ui.RED, Align.center);
            Ui.label(game.batch, game.assets.fontSmall,
                "Cần cấp 5 — cấp hiện tại của bạn: " + p.level, 640, 536, Ui.TEXT, Align.center);
            Ui.label(game.batch, game.assets.fontSmall,
                "Cần hoàn thành nhiệm vụ giới thiệu mở đầu (gặp Trưởng Trạm Gốc).", 640, 508, Ui.TEXT, Align.center);
            Ui.label(game.batch, game.assets.fontSmall,
                "Hãy làm nhiệm vụ chính và farm ở Vành Đai Neon để lên cấp nhé.", 640, 480, Ui.TEXT_DIM, Align.center);
            game.batch.end();
        } else {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall,
                p.gameClass != null && p.gameClass != GameClass.NONE ? "Chọn nghề mới để thay đổi:" : "Chọn một nghề cho mình:",
                640, 566, Ui.TEXT, Align.center);
            game.batch.end();
            if (game.assets.classesTex != null) {
                game.batch.begin();
                game.batch.draw(game.assets.classesTex, 940, 470, 76, 100);
                game.batch.end();
            }
            int i = 0;
            for (GameClass gc : GameClass.values()) {
                if (gc == GameClass.NONE) continue;
                float cx = 262 + (i % 2) * 392;
                float cy = (i < 2) ? 330 : 140;
                Ui.panel(game.shapes, cx, cy, 372, 168, new Color(0.07f, 0.11f, 0.16f, 0.97f));
                game.shapes.begin(ShapeType.Filled);
                game.shapes.setColor(Draw.rgb(gc.color));
                game.shapes.rect(cx, cy + 162, 372, 6);
                game.shapes.end();
                game.batch.begin();
                Ui.label(game.batch, game.assets.font, gc.displayName, cx + 16, cy + 138, Draw.rgb(gc.color), Align.left);
                Ui.label(game.batch, game.assets.fontSmall, gc.desc, cx + 16, cy + 106, Ui.TEXT, Align.left);
                Ui.label(game.batch, game.assets.fontSmall,
                    "HP " + gc.baseHp + " — Công " + gc.baseAtk + " — Thủ " + gc.baseDef + " — " + gc.skillCount() + " kỹ năng",
                    cx + 16, cy + 78, Ui.TEXT_DIM, Align.left);
                game.batch.end();
                String label = (p.gameClass == gc) ? "Nghề hiện tại" : (p.gameClass != null && p.gameClass != GameClass.NONE ? "Đổi sang nghề này" : "Chọn nghề này");
                if (btn(cx + 16, cy + 14, 220, 40, label) && p.gameClass != gc) {
                    ClassChoice.Result r = ClassChoice.tryChoose(game.state, gc);
                    if (r == ClassChoice.Result.OK) {
                        game.state.quests.onEvent(ObjectiveType.CHOOSE_CLASS, 1);
                        game.sound.play("levelup");
                        game.state.notice = "Đã chọn nghề " + gc.displayName + "! Ra Vành Đai Neon thử kỹ năng mới nhé.";
                        game.saveGame();
                    } else {
                        game.sound.play("error");
                        game.state.notice = "Chưa chọn được nghề: " + r.name();
                    }
                }
                i++;
            }
        }
        if (closeBtn(915, 108)) panel = null;
    }

    private void panelEmote() {
        // popup above the emote button beside the chat frame
        Ui.panel(game.shapes, 856, 112, 248, 176);
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Cảm xúc", 980, 268, Ui.CYAN, Align.center);
        game.batch.end();
        if (btn(880, 214, 200, 38, "Vẫy tay")) { emote = 1; emoteT = 2; panel = null; }
        if (btn(880, 168, 200, 38, "Cười")) { emote = 2; emoteT = 2; panel = null; }
        if (btn(880, 122, 200, 38, "Ngồi nghỉ")) { emote = 3; emoteT = 2; panel = null; }
    }

    @Override public void resize(int w, int h) { worldVp.update(w, h, false); hudVp.update(w, h, true); }
    @Override public void show() {
        worldVp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
        hudVp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        game.sound.startMusic();
    }
    @Override public void hide() { }
    @Override public void pause() { game.saveGame(); }
    @Override public void resume() { }
    @Override public void dispose() { }
}
