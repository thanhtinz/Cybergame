package com.neolang.cyber.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.neolang.cyber.NeoLangGame;
import com.neolang.cyber.model.Combat;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.Items;
import com.neolang.cyber.model.MonsterDef;
import com.neolang.cyber.model.ObjectiveType;
import com.neolang.cyber.model.Player;
import com.neolang.cyber.model.SkillKind;
import com.neolang.cyber.model.Survival;
import com.neolang.cyber.ui.Draw;
import com.neolang.cyber.ui.Hud;
import com.neolang.cyber.ui.Ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Neon belt farm zone (city outskirts): joystick + attack/skill buttons, monsters, pet loot pickup, data storm. */
public class AdventureScreen implements Screen {
    public static final float WORLD_W = 3400;
    public static final float GROUND = 170;

    private final NeoLangGame game;
    private final ExtendViewport worldVp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);
    private final ExtendViewport hudVp = new ExtendViewport(NeoLangGame.VW, NeoLangGame.VH);
    private final Random rng = new Random();

    private float time;
    private float px = 300;
    private float facing = 1;
    private float walkPhase;
    private float petX = 250;
    private float energy = 100;
    private final float[] cds = new float[3];
    private float shieldT, dashT, dashDir = 1;
    private float spawnT, wraithT = 4;
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

    private String panel; // null | chat mail emote pause
    private int emote; // 0 none, 1 wave, 2 smile, 3 sit
    private float emoteT;
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

    private static class Mon {
        MonsterDef def;
        int lvl;
        /** Sprite variant: 0 = base painting, 1 = malware spike ball (storm wraiths),
         *  2 = drone jellyfish (high-level beetle-bots). */
        int variant;
        float hp, maxHp, x, atkCd, flash, wanderT, dir = 1;
    }

    private static class Loot {
        String id;
        float x, t;
        boolean picked;
    }

    private static class Skill {
        String name;
        SkillKind kind;
        double mult;
        float cd, cost;
        Skill(String n, SkillKind k, double m, float cd, float cost) { name = n; kind = k; mult = m; this.cd = cd; this.cost = cost; }
    }

    private final List<Mon> mons = new ArrayList<Mon>();
    private final List<Loot> loots = new ArrayList<Loot>();
    private Mon lastHitMon;   // most recently hit monster: keeps the full name tag briefly
    private float lastHitT;

    public AdventureScreen(NeoLangGame game) {
        this.game = game;
        px = 300;
        chatLog.add("Pina: Chào bạn mới! Ghé cafe khi đói nhé.");
        chatLog.add("Miro: Bộ Street Neon vừa về hàng đó.");
        for (int i = 0; i < 10; i++) spawnMonster(500 + rng.nextFloat() * 2700);
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    private int petTier() {
        return Math.min(3, 1 + game.state.player.countItem("pet_chip"));
    }

    private void spawnMonster(float x) {
        Player p = game.state.player;
        Mon m = new Mon();
        MonsterDef[] all = MonsterDef.values();
        m.def = all[rng.nextInt(all.length)];
        if (game.state.storm.active && rng.nextFloat() < 0.3f) m.def = MonsterDef.DATA_WRAITH;
        m.lvl = Math.max(1, p.level + rng.nextInt(5) - 2);
        if (game.state.storm.active && m.def == MonsterDef.DATA_WRAITH) m.variant = 1;
        if (m.def == MonsterDef.BEETLE_BOT && m.lvl >= 6) m.variant = 2;
        m.maxHp = (float) (double) m.def.hpForLevel(m.lvl);
        if (game.state.clock.isNight()) m.maxHp *= 1.15f;
        m.hp = m.maxHp;
        m.x = clamp(x, 120, WORLD_W - 120);
        m.dir = rng.nextBoolean() ? 1 : -1;
        mons.add(m);
    }

    private List<Skill> skills() {
        // The model exposes the full 3-skill set for every class (NONE included: basic attack,
        // Lan Ne dash, energy ray) through skillName/skillMult/skillCd/skillCost/skillKind.
        List<Skill> list = new ArrayList<Skill>();
        Player p = game.state.player;
        GameClass gc = p.gameClass == null ? GameClass.NONE : p.gameClass;
        int n = Math.min(3, gc.basicSkillCount());
        for (int i = 0; i < n; i++) {
            list.add(new Skill(gc.skillName(i), gc.skillKind(i),
                (double) gc.skillMult(i), gc.skillCd(i), (float) gc.skillCost(i)));
        }
        while (list.size() < 3) list.add(new Skill("Đòn Cơ Bản", SkillKind.DAMAGE, 1.3, 0.8f, 0));
        return list;
    }

    /** Painted sprite for a monster: base painting per type; storm wraiths use the
     *  malware spike ball, high-level beetle-bots the drone jellyfish. */
    private Texture monTexture(Mon m) {
        switch (m.def) {
            case BEETLE_BOT:
                return m.variant == 2 && game.assets.monJelly != null ? game.assets.monJelly : game.assets.monBeetle;
            case DATA_WRAITH:
                return m.variant == 1 && game.assets.monSpike != null ? game.assets.monSpike : game.assets.monWraith;
            case GLITCH_BLOB:
                return game.assets.monBlob;
            case MUSH_BUG:
                return game.assets.monMush;
            default:
                return null;
        }
    }

    private Mon nearestMon(float range) {
        Mon best = null;
        float bd = range;
        for (Mon m : mons) {
            float d = Math.abs(m.x - px);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private void hitMon(Mon m, double mult) {
        Player p = game.state.player;
        int dmg = Combat.damage((int) (double) p.atk(), (float) mult, (int) (double) m.def.defForLevel(m.lvl), rng);
        m.hp -= dmg;
        m.flash = 0.6f;
        lastHitMon = m;
        lastHitT = 2.5f;
        m.x += facing * 8;
        game.sound.play("hit");
        if (m.hp <= 0) killMon(m);
    }

    private void killMon(Mon m) {
        Player p = game.state.player;
        mons.remove(m);
        boolean up = p.gainXp((int) (double) m.def.xpForLevel(m.lvl));
        p.xuLa += (int) (double) m.def.xuForLevel(m.lvl);
        game.state.quests.onEvent(ObjectiveType.KILL, 1);
        if (game.state.storm.active) game.state.storm.onKill();
        if (up) {
            game.sound.play("levelup");
            game.state.notice = "Lên cấp " + p.level + "!";
        }
        if (rng.nextFloat() < 0.30f) {
            String id = null;
            float r = rng.nextFloat();
            if (r < 0.4f) id = "linh_kien";
            else if (r < 0.75f) id = "nhua_sang";
            else id = firstSeedId();
            if (id != null && Items.get(id) != null) {
                Loot l = new Loot();
                l.id = id;
                l.x = m.x;
                loots.add(l);
            }
        }
    }

    private String firstSeedId() {
        for (com.neolang.cyber.model.ItemDef d : Items.ALL.values()) {
            if ("SEED".equals(String.valueOf(d.type))) return d.id;
        }
        return null;
    }

    private void doAttack() {
        Mon m = nearestMon(100);
        if (m != null) {
            facing = m.x >= px ? 1 : -1;
            hitMon(m, 1.0);
        }
    }

    private void useSkill(int i) {
        Player p = game.state.player;
        List<Skill> list = skills();
        if (i >= list.size()) return;
        Skill s = list.get(i);
        if (cds[i] > 0 || energy < s.cost) { game.sound.play("error"); return; }
        energy -= s.cost;
        cds[i] = s.cd;
        switch (s.kind) {
            case DAMAGE: {
                Mon m = nearestMon(120);
                if (m != null) { facing = m.x >= px ? 1 : -1; hitMon(m, s.mult); }
                break;
            }
            case AOE: {
                List<Mon> targets = new ArrayList<Mon>();
                for (Mon m : mons) if (Math.abs(m.x - px) <= 135) targets.add(m);
                for (Mon m : targets) hitMon(m, s.mult);
                break;
            }
            case HEAL: {
                p.hp += (double) p.maxHp() * 0.25;
                if ((double) p.hp > (double) p.maxHp()) p.hp = (int) (double) p.maxHp();
                game.sound.play("harvest");
                game.state.notice = "Đã hồi máu.";
                break;
            }
            case SHIELD: {
                shieldT = 5;
                Mon m = nearestMon(120);
                if (m != null && s.mult > 0) hitMon(m, s.mult);
                game.sound.play("click");
                break;
            }
            case DASH: {
                dashT = 0.25f;
                dashDir = facing;
                Mon m = nearestMon(120);
                if (m != null && s.mult > 0) hitMon(m, s.mult);
                game.sound.play("click");
                break;
            }
        }
    }

    private void update(float dt) {
        Player p = game.state.player;
        game.state.clock.tick(dt);
        game.state.storm.tick(dt);
        game.state.playSeconds += dt;
        Survival.tick(p, dt, true, game.state.clock.isNight(), game.state.storm.active, false, false);

        if ((double) p.hp <= 0 || Survival.isFainted(p)) {
            p.hp = (int) ((double) p.maxHp() * 0.6);
            p.satiety += (100 - (double) p.satiety) * 0.4;
            p.warmth += (100 - (double) p.warmth) * 0.4;
            p.battery += (100 - (double) p.battery) * 0.4;
            p.x = 2600;
            game.state.notice = "Bạn gục ở Vành Đai Neon — pet kéo bạn về làng, không mất đồ gì cả.";
            game.saveGame();
            gone = true;
            game.gotoTown();
            return;
        }

        energy = Math.min(100, energy + 8 * dt);
        for (int i = 0; i < 3; i++) cds[i] = Math.max(0, cds[i] - dt);
        if (shieldT > 0) shieldT -= dt;

        if (emoteT > 0) { emoteT -= dt; if (emoteT <= 0) emote = 0; }

        float move = 0;
        float speedFactor = 1f; // 1 = walk, 1.5 = run (full stick tilt or Shift held)
        stickRun = false;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            move -= 1; if (shift) speedFactor = 1.5f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            move += 1; if (shift) speedFactor = 1.5f;
        }
        if (Gdx.input.justTouched()) {
            Vector2 hp = Ui.pointer(hudVp);
            float jdx = hp.x - JOY_X, jdy = hp.y - JOY_Y;
            boolean onChat = hp.x >= 338 && hp.y <= 138; // chat cluster bottom-centre (frame + snug button columns): don't steal its taps
            if (jdx * jdx + jdy * jdy <= 120 * 120) { joyMode = 1; joyBase.set(JOY_X, JOY_Y); joyVec = 0; }
            else if (hp.x < 560 && hp.y < 330 && !onChat) { joyMode = 2; joyBase.set(hp); joyVec = 0; }
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
        // Walk/run comes from stick deflection (no run toggle): dead zone at the
        // centre, partial tilt = walk, tilt to the rim (>= 85%) = run. The
        // floating secondary stick always walks.
        if (joyMode > 0 && Math.abs(joyVec) > 0.22f) {
            move = Math.signum(joyVec);
            speedFactor = (joyMode == 1 && Math.abs(joyVec) >= 0.85f) ? 1.5f : 1.0f;
        }

        moving = move != 0 || dashT > 0;
        if (dashT > 0) {
            dashT -= dt;
            px = clamp(px + dashDir * 620 * dt, 50, WORLD_W - 50);
        } else if (move != 0) {
            facing = move > 0 ? 1 : -1;
            px = clamp(px + (float) (move * (double) p.speed() * speedFactor * dt), 50, WORLD_W - 50);
            walkPhase += dt * (speedFactor > 1f ? 15 : 11);
            stickRun = speedFactor > 1f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.J)) doAttack();
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) useSkill(0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) useSkill(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) useSkill(2);

        if (lastHitT > 0) lastHitT -= dt;

        // monsters
        Iterator<Mon> it = mons.iterator();
        while (it.hasNext()) {
            Mon m = it.next();
            m.flash = Math.max(0, m.flash - dt);
            m.atkCd -= dt;
            float d = px - m.x;
            if (Math.abs(d) < 250) {
                m.x += Math.signum(d) * 52 * dt;
                if (Math.abs(d) < 48 && m.atkCd <= 0) {
                    m.atkCd = 1.0f;
                    int dmg = Combat.damage((int) (double) m.def.atkForLevel(m.lvl), 1.0f, (int) (double) p.def(), rng);
                    if (shieldT > 0) dmg = (int) (dmg * 0.3);
                    p.hp -= dmg;
                    game.sound.play("hit");
                }
            } else {
                m.wanderT -= dt;
                if (m.wanderT <= 0) { m.wanderT = 2 + rng.nextFloat() * 2; m.dir = rng.nextBoolean() ? 1 : -1; }
                m.x = clamp(m.x + m.dir * 22 * dt, 80, WORLD_W - 80);
            }
        }
        // respawn + storm wraiths
        spawnT -= dt;
        if (spawnT <= 0) {
            spawnT = 2.5f;
            if (mons.size() < 8) spawnMonster(px + (rng.nextBoolean() ? 700 : -700) + rng.nextFloat() * 300);
        }
        if (game.state.storm.active) {
            wraithT -= dt;
            if (wraithT <= 0) {
                wraithT = 6;
                int wraiths = 0;
                for (Mon m : mons) if (m.def == MonsterDef.DATA_WRAITH) wraiths++;
                if (wraiths < 4) {
                    Mon m = new Mon();
                    m.def = MonsterDef.DATA_WRAITH;
                    m.variant = 1; // storm wraiths wear the malware spike-ball sprite
                    m.lvl = Math.max(1, p.level);
                    m.maxHp = (float) (double) m.def.hpForLevel(m.lvl);
                    m.hp = m.maxHp;
                    m.x = rng.nextBoolean() ? 200 : WORLD_W - 200;
                    mons.add(m);
                }
            }
        }

        // pet: runs to loot first, otherwise follows the player
        Loot target = null;
        for (Loot l : loots) if (!l.picked) { target = l; break; }
        float petGoal = target != null ? target.x : px - facing * 46;
        float pd = petGoal - petX;
        float petSpeed = 130 + petTier() * 55;
        if (Math.abs(pd) > 6) petX += Math.signum(pd) * Math.min(Math.abs(pd), petSpeed * dt);
        Iterator<Loot> li = loots.iterator();
        while (li.hasNext()) {
            Loot l = li.next();
            if (!l.picked && Math.abs(petX - l.x) < 22) {
                l.t += dt;
                float need = Math.max(0.15f, 0.6f - petTier() * 0.15f);
                if (l.t >= need) {
                    l.picked = true;
                    p.addItem(l.id, 1);
                    game.sound.play("coin");
                }
            }
            if (l.picked) li.remove();
        }

        if (game.state.notice != null && !game.state.notice.isEmpty() && !game.state.notice.equals(lastNotice)) {
            lastNotice = game.state.notice;
            noticeT = 0;
        }
        noticeT += dt;
    }

    @Override
    public void render(float dt) {
        time += dt;
        if (!gone && panel == null) update(dt); // world freezes while a HUD panel is open
        if (gone) return;
        Player p = game.state.player;

        Gdx.gl.glClearColor(0.02f, 0.03f, 0.07f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float viewW = worldVp.getWorldWidth();
        float camX = clamp(px, viewW / 2, WORLD_W - viewW / 2);
        OrthographicCamera cam = (OrthographicCamera) worldVp.getCamera();
        cam.position.set(camX, NeoLangGame.VH / 2f, 0);
        worldVp.apply();
        game.batch.setProjectionMatrix(cam.combined);

        boolean storm = game.state.storm.active;
        float dayness = game.state.clock.isNight() ? 0.25f : 0.9f;
        Texture bgPlate = game.assets.advPlate != null ? game.assets.advPlate : game.assets.adventure;
        if (bgPlate != null) {
            // Clean far plate (empty night street, no baked characters/UI) tiled
            // at full screen height with 0.5x parallax: the camera can travel the
            // whole 3400px map and the painting never runs out.
            game.batch.begin();
            float tileW = NeoLangGame.VH * bgPlate.getWidth() / (float) bgPlate.getHeight();
            float startX = camX - viewW / 2;
            float off = -((camX * 0.5f) % tileW);
            for (float bx = startX + off - tileW; bx < startX + viewW + tileW; bx += tileW) {
                game.batch.draw(bgPlate, bx, 0, tileW, NeoLangGame.VH);
            }
            game.batch.end();
            // walkable ground band so feet meet the path at GROUND
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0.08f, 0.07f, 0.05f, 0.45f);
            game.shapes.rect(startX - 10, 0, viewW + 20, GROUND);
            game.shapes.setColor(0.25f, 0.95f, 1f, 0.4f);
            game.shapes.rect(startX - 10, GROUND - 3, viewW + 20, 3);
            game.shapes.end();
        } else {
            Draw.drawSky(game.shapes, camX - viewW / 2 - 10, viewW + 20, dayness, storm, time);
            Draw.drawTrunkWorld(game.shapes, camX - viewW / 2 - 10, WORLD_W, GROUND, 1, time);
        }

        // exit gate: painted portal sprite (the flat cyan box is gone)
        if (game.assets.bldPortal != null) {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0, 0, 0, 0.25f);
            game.shapes.ellipse(70 - 62, GROUND - 6, 124, 13);
            game.shapes.end();
            Draw.drawSprite(game.batch, game.assets.bldPortal, 70, GROUND - 2, 168);
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Về Làng", 70, GROUND + 182, Ui.TEXT, Align.center);
            game.batch.end();
        } else {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0.20f, 0.30f, 0.38f, 1);
            game.shapes.rect(30, GROUND, 80, 140);
            game.shapes.setColor(0.35f, 0.85f, 0.95f, 0.6f);
            game.shapes.rect(38, GROUND + 10, 64, 120);
            game.shapes.end();
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Về Làng", 70, GROUND + 158, Ui.TEXT, Align.center);
            game.batch.end();
        }

        // loot markers
        game.shapes.begin(ShapeType.Filled);
        for (Loot l : loots) {
            float bob = (float) Math.sin(time * 5 + l.x) * 3;
            game.shapes.setColor(1f, 0.82f, 0.25f, 1);
            game.shapes.circle(l.x, GROUND + 16 + bob, 6);
            game.shapes.setColor(1, 1, 1, 0.8f);
            game.shapes.circle(l.x - 2, GROUND + 18 + bob, 2);
        }
        game.shapes.end();

        // Only ONE monster wears the full name tag: the one just hit, else the
        // nearest in reach. Every other monster shows just its HP bar, so tags
        // no longer pile onto each other in a crowd.
        Mon focusMon = (lastHitMon != null && lastHitT > 0 && mons.contains(lastHitMon))
            ? lastHitMon : nearestMon(150);
        for (Mon m : mons) {
            Texture mt = monTexture(m);
            if (mt != null) {
                Draw.drawMonsterSprite(game.batch, game.shapes, mt, m.x, GROUND, m.hp / m.maxHp, time, m.flash);
                if (m == focusMon) {
                    Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, m.def.displayName + " Lv" + m.lvl, m.x, GROUND + 92);
                }
            } else {
                Draw.drawMonster(game.shapes, m.x, GROUND, m.def, m.hp / m.maxHp, time, m.flash);
                if (m == focusMon) {
                    Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, m.def.displayName + " Lv" + m.lvl, m.x, GROUND + 66);
                }
            }
        }
        if (!Draw.drawPetSprite(game.batch, game.assets, petX, GROUND, time, petTier())) {
            Draw.drawPet(game.shapes, petX, GROUND, time, petTier());
        }
        if (shieldT > 0) {
            game.shapes.begin(ShapeType.Line);
            game.shapes.setColor(0.4f, 0.85f, 1f, 0.6f);
            game.shapes.circle(px, GROUND + 34, 34);
            game.shapes.end();
        }
        float heroY = GROUND - (emote == 3 ? 5 : 0);
        if (!Draw.drawHeroSprite(game.batch, game.shapes, game.assets, px, heroY, 1.18f, p.gender,
                p.outfitId, p.gameClass, facing, moving, walkPhase, time)) {
            Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, px, heroY, 1.18f, p.gender,
                p.suitColorIdx, p.hairColorIdx, p.hairStyleIdx, p.eyeColorIdx, p.skinToneIdx, p.outfitId, p.gameClass,
                facing, walkPhase, time);
        }
        if (emote == 1) { // wave: sparkle trail over the raised hand
            game.shapes.begin(ShapeType.Filled);
            for (int i = 0; i < 3; i++) {
                float a = 0.5f + 0.5f * (float) Math.sin(time * 8 + i);
                game.shapes.setColor(0.35f, 0.95f, 1f, a);
                game.shapes.circle(px + facing * (20 + i * 6), GROUND + 82 + i * 5, 2.4f);
            }
            game.shapes.end();
        } else if (emote == 2) {
            Ui.iconSmile(game.shapes, px, GROUND + 96, 10, Ui.GOLD);
        }

        if (game.state.clock.isNight() || storm) {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0.05f, 0.02f, 0.15f, game.state.clock.isNight() ? 0.30f : 0.12f);
            game.shapes.rect(camX - viewW / 2 - 10, 0, viewW + 20, NeoLangGame.VH);
            game.shapes.end();
        }

        drawHud();

        if (game.screenshotDir != null) {
            shotFrames++;
            if (shotFrames == 90) {
                game.captureShot("adventure.png");
                game.gotoPlot();
            }
        }
    }

    private void drawHud() {
        hudVp.apply();
        game.batch.setProjectionMatrix(hudVp.getCamera().combined);
        Player p = game.state.player;

        // top-left: shared slim character card (portrait in the cyber frame,
        // HP/NL bars with numbers) + loose survival-chip row under the card.
        float clBottom = Hud.drawCharCluster(game, true, energy);
        if (panel == null && Ui.tapped(hudVp, Hud.CL_X, clBottom, Hud.CL_W, Hud.CL_TOP - clBottom)) {
            panel = "pause"; game.sound.play("click");
        }

        // minimap top-right: red dots = living monsters, green = home portal,
        // map name inside the frame's top strip (no loose label under the box)
        Hud.drawMinimapBase(game, "Khu Vành Đai Neon");
        game.shapes.begin(ShapeType.Filled);
        Color monDot = new Color(1f, 0.32f, 0.32f, 1);
        for (Mon m : mons) if (m.hp > 0) Hud.dot(game.shapes, Hud.mmX(m.x, WORLD_W), Hud.mmGroundY(), 2.6f, monDot);
        Hud.dot(game.shapes, Hud.mmX(30, WORLD_W), Hud.mmGroundY(), 3.6f, new Color(0.35f, 1f, 0.75f, 1));
        Hud.playerDot(game.shapes, Hud.mmX(px, WORLD_W), Hud.mmGroundY(), facing < 0 ? -1 : 1);
        game.shapes.end();

        if (panel == null && Hud.glassPillBtn(game, hudVp, "Về Làng", Hud.MM_X, Hud.MM_Y - 50, Hud.MM_W, 40)) {
            p.x = 2600;
            game.saveGame();
            gone = true;
            game.gotoTown();
            return;
        }
        // currency pills under the "Về Làng" pill (right cluster: minimap ->
        // Về Làng -> currency)
        Hud.drawCurrency(game, Hud.MM_Y - 58);
        if (px < 130) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Lại gần cổng hoặc bấm Về Làng", 640, 210, Ui.TEXT_DIM, Align.center);
            game.batch.end();
        }

        // storm reward
        if (game.state.storm.active && game.state.storm.objectiveDone()) {
            if (panel == null && Ui.button(game.batch, game.shapes, game.assets.font, hudVp, 465, 620, 350, 48, "Nhận thưởng bão")) {
                game.state.storm.claimReward(p);
                game.sound.play("levelup");
                game.state.notice = "Đã nhận thưởng Bão Dữ Liệu!";
                game.saveGame();
            }
        } else if (game.state.storm.active) {
            String stormTxt = "BÃO DỮ LIỆU — hạ quái: " + game.state.storm.kills + "/" + game.state.storm.objectiveKills;
            game.batch.begin();
            Hud.icon(game.batch, game.assets.icoQuest, 640 - Ui.textWidth(game.assets.fontSmall, stormTxt) / 2 - 20, 694, 24);
            Ui.label(game.batch, game.assets.fontSmall, stormTxt,
                640, 700, new Color(1f, 0.75f, 0.95f, 1), Align.center);
            game.batch.end();
        }

        // joystick visual: painted base + knob assets, shared with town
        Hud.drawJoystick(game, JOY_X, JOY_Y, joyMode, joyBase, joyVec, stickRun);

        // chat cluster, same layout as town: LEFT column mail on TOP of settings,
        // RIGHT column emote on TOP of the bag
        if (iconBtn(346, 78, "mail")) { panel = "mail"; game.sound.play("click"); }
        if (iconBtn(346, 24, "menu")) { panel = "pause"; game.sound.play("click"); }
        if (iconBtn(888, 78, "smile")) { panel = "emote"; game.sound.play("click"); }
        if (iconBtn(888, 24, "bag")) { panel = "bag"; game.sound.play("click"); }
        drawChatFrame();

        // attack button (big) + skills
        boolean atkTap = Ui.tapped(hudVp, 1130, 30, 120, 120);
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.85f, 0.25f, 0.20f, 0.92f);
        game.shapes.circle(1190, 90, 58);
        game.shapes.setColor(1f, 0.6f, 0.4f, 1);
        game.shapes.circle(1190, 90, 48);
        game.shapes.end();
        game.batch.begin();
        Hud.icon(game.batch, game.assets.icoSword, 1190, 102, 54);
        Ui.label(game.batch, game.assets.fontSmall, "Đánh", 1190, 62, Color.WHITE, Align.center);
        game.batch.end();
        if (atkTap && panel == null) doAttack();

        // skills: quarter arc above-left of the big attack button (centre 1190,90, r 58);
        // radius 132, angles 172/132/92 degrees — clear of the chat frame and the gate.
        List<Skill> list = skills();
        float[] arcDeg = {172f, 132f, 92f};
        for (int i = 0; i < 3; i++) {
            double rad = Math.toRadians(arcDeg[i]);
            float cx = 1190 + (float) Math.cos(rad) * 132;
            float cy = 90 + (float) Math.sin(rad) * 132;
            float r = 42;
            boolean ready = cds[i] <= 0 && energy >= list.get(i).cost;
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(ready ? Ui.PANEL_EDGE : Ui.TEXT_DIM);
            game.shapes.circle(cx, cy, r);
            game.shapes.setColor(ready ? Ui.BTN : Ui.BTN_OFF);
            game.shapes.circle(cx, cy, r - 2.5f);
            if (cds[i] > 0) { // cooldown: dark veil over the disc
                game.shapes.setColor(0, 0, 0, 0.45f);
                game.shapes.circle(cx, cy, r - 2.5f);
            }
            game.shapes.end();
            if (panel == null && Ui.tapped(hudVp, cx - r, cy - r, r * 2, r * 2)) useSkill(i);
            // skill labels are drawn at 85% font scale so long names fit the disc
            float fs = game.assets.fontSmall.getData().scaleX;
            game.assets.fontSmall.getData().setScale(fs * 0.85f);
            String[] lines = wrapSkillName(list.get(i).name);
            game.batch.begin();
            if (cds[i] > 0) {
                Ui.label(game.batch, game.assets.font, String.format("%.0f", Math.ceil(cds[i])), cx, cy + 10, Ui.RED, Align.center);
            } else {
                // painted skill icon in the disc's upper half (sword = basic /
                // damage, swirl = dash, orb = energy & other kinds)
                Texture sIco = list.get(i).kind == SkillKind.DASH ? game.assets.icoSwirl
                    : i == 0 ? game.assets.icoSword : game.assets.icoOrb;
                if (sIco != null) Hud.icon(game.batch, sIco, cx, cy + 18, 33);
                Color nameCol = ready ? Ui.TEXT : Ui.TEXT_DIM;
                if (lines.length == 1) {
                    Ui.label(game.batch, game.assets.fontSmall, lines[0], cx, cy - 1, nameCol, Align.center);
                } else {
                    Ui.label(game.batch, game.assets.fontSmall, lines[0], cx, cy + 3, nameCol, Align.center);
                    Ui.label(game.batch, game.assets.fontSmall, lines[1], cx, cy - 13, nameCol, Align.center);
                }
                Ui.label(game.batch, game.assets.fontSmall, "NL " + (int) list.get(i).cost, cx, cy - 27, Ui.CYAN, Align.center);
            }
            game.batch.end();
            game.assets.fontSmall.getData().setScale(fs);
        }
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Phím: A/D đi — giữ Shift chạy — Space đánh — 1/2/3 kỹ năng", 180, 190, Ui.TEXT_DIM, Align.left);
        game.batch.end();

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

    private boolean btn(float x, float y, float w, float h, String label) {
        return Ui.button(game.batch, game.shapes, game.assets.fontSmall, hudVp, x, y, w, h, label);
    }

    private boolean closeBtn(float x, float y) {
        return btn(x, y, 90, 36, "Đóng");
    }

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

    private String fitText(String s, float maxW) {
        if (s == null) return "";
        if (Ui.textWidth(game.assets.fontSmall, s) <= maxW) return s;
        while (s.length() > 4 && Ui.textWidth(game.assets.fontSmall, s + "..") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    /** Greedy word wrap of a skill name into 1-2 lines that fit the skill disc.
     *  Measured with the font at the scale the caller draws with (85%). */
    private String[] wrapSkillName(String name) {
        if (Ui.textWidth(game.assets.fontSmall, name) <= 72) return new String[]{name};
        String[] words = name.split(" ");
        String l1 = words[0];
        int i = 1;
        while (i < words.length && Ui.textWidth(game.assets.fontSmall, l1 + " " + words[i]) <= 72) {
            l1 += " " + words[i];
            i++;
        }
        String l2 = "";
        while (i < words.length) {
            l2 += (l2.isEmpty() ? "" : " ") + words[i];
            i++;
        }
        if (Ui.textWidth(game.assets.fontSmall, l2) > 72) l2 = fitText(l2, 72);
        return new String[]{l1, l2};
    }

    /** Bottom-centre chat frame (slightly narrower than town's so the skill arc
     *  keeps its space): short log over a "tap to chat" strip; tap opens chat panel. */
    private void drawChatFrame() {
        Ui.panel(game.shapes, 400, 16, 480, 114, new Color(0.05f, 0.10f, 0.15f, 0.88f));
        game.batch.begin();
        float y = 114;
        int from = Math.max(0, chatLog.size() - 3);
        for (int i = from; i < chatLog.size(); i++) {
            Ui.label(game.batch, game.assets.fontSmall, fitText(chatLog.get(i), 448), 412, y, Ui.TEXT, Align.left);
            y -= 22;
        }
        game.batch.end();
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(Ui.BTN);
        game.shapes.rect(412, 24, 456, 28);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Chạm để chat...", 424, 44, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        if (panel == null && Ui.tapped(hudVp, 400, 16, 480, 114)) { panel = "chat"; game.sound.play("click"); }
    }

    // ------------------------------------------------------------------ panels
    private void drawPanel() {
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0, 0, 0, 0.45f);
        game.shapes.rect(0, 0, NeoLangGame.VW, NeoLangGame.VH);
        game.shapes.end();
        switch (panel) {
            case "chat": panelChat(); break;
            case "mail": panelMail(); break;
            case "emote": panelEmote(); break;
            case "bag": panelBag(); break;
            case "pause": panelPause(); break;
            default: panel = null; break;
        }
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
                emote = 2; emoteT = 2;
            }
        }
        if (closeBtn(735, 152)) panel = null;
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

    private void panelEmote() {
        // popup above the emote button beside the chat frame
        Ui.panel(game.shapes, 806, 112, 248, 176);
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Cảm xúc", 930, 268, Ui.CYAN, Align.center);
        game.batch.end();
        if (btn(830, 214, 200, 38, "Vẫy tay")) { emote = 1; emoteT = 2; panel = null; }
        if (btn(830, 168, 200, 38, "Cười")) { emote = 2; emoteT = 2; panel = null; }
        if (btn(830, 122, 200, 38, "Ngồi nghỉ")) { emote = 3; emoteT = 2; panel = null; }
    }

    /** Same bag panel as town (the game's one inventory view): item counts
     *  from the player's bag, read-only here — using/eating stays in town. */
    private void panelBag() {
        Ui.panel(game.shapes, 390, 150, 500, 440);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Túi đồ", 640, 562, Ui.CYAN, Align.center);
        float y = 516;
        int shown = 0;
        for (com.neolang.cyber.model.ItemDef d : Items.ALL.values()) {
            int n = game.state.player.countItem(d.id);
            if (n <= 0) continue;
            Ui.label(game.batch, game.assets.fontSmall, d.name + "  x" + n, 420, y, Ui.TEXT, Align.left);
            y -= 26;
            shown++;
            if (y < 210) break;
        }
        if (shown == 0) Ui.label(game.batch, game.assets.fontSmall, "Túi trống. Farm quái ở đây hoặc ghé Cafe Pina nhé.", 420, y, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        if (closeBtn(735, 168)) panel = null;
    }

    private void panelPause() {
        Player p = game.state.player;
        Ui.panel(game.shapes, 440, 140, 400, 450);
        game.batch.begin();
        Ui.label(game.batch, game.assets.font, "Tạm dừng", 640, 562, Ui.CYAN, Align.center);
        game.batch.end();
        Ui.drawSettings(game, hudVp, 470, 478, 340);
        if (btn(480, 286, 320, 44, "Tiếp tục")) panel = null;
        if (btn(480, 234, 320, 44, "Lưu game")) { game.saveGame(); game.state.notice = "Đã lưu game."; game.sound.play("coin"); }
        if (btn(480, 182, 320, 44, "Về Làng")) {
            p.x = 2600;
            game.saveGame();
            gone = true;
            game.gotoTown();
        }
    }

    @Override public void resize(int w, int h) { worldVp.update(w, h, false); hudVp.update(w, h, true); }
    @Override public void show() {
        worldVp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
        hudVp.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
    }
    @Override public void hide() { }
    @Override public void pause() { game.saveGame(); }
    @Override public void resume() { }
    @Override public void dispose() { }
}
