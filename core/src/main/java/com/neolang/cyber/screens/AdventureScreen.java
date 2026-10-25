package com.neolang.cyber.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
import com.neolang.cyber.ui.Ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Forest edge farm zone: joystick + attack/skill buttons, monsters, pet loot pickup, data storm. */
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

    private boolean joyActive;
    private final Vector2 joyBase = new Vector2();
    private float joyVec;

    private static class Mon {
        MonsterDef def;
        int lvl;
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

    public AdventureScreen(NeoLangGame game) {
        this.game = game;
        px = 300;
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
            game.state.notice = "Bạn gục ở Bìa Rừng — pet kéo bạn về làng, không mất đồ gì cả.";
            game.saveGame();
            gone = true;
            game.gotoTown();
            return;
        }

        energy = Math.min(100, energy + 8 * dt);
        for (int i = 0; i < 3; i++) cds[i] = Math.max(0, cds[i] - dt);
        if (shieldT > 0) shieldT -= dt;

        float move = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) move -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) move += 1;
        if (Gdx.input.justTouched()) {
            Vector2 hp = Ui.pointer(hudVp);
            if (hp.x < 560 && hp.y < 330) { joyActive = true; joyBase.set(hp); joyVec = 0; }
        }
        if (joyActive && Gdx.input.isTouched()) {
            Vector2 hp = Ui.pointer(hudVp);
            joyVec = clamp((hp.x - joyBase.x) / 55f, -1, 1);
            if (Math.abs(joyVec) > 0.22f) move = joyVec;
        }
        if (!Gdx.input.isTouched()) { joyActive = false; joyVec = 0; }

        if (dashT > 0) {
            dashT -= dt;
            px = clamp(px + dashDir * 620 * dt, 50, WORLD_W - 50);
        } else if (move != 0) {
            facing = move > 0 ? 1 : -1;
            px = clamp(px + (float) (move * (double) p.speed() * dt), 50, WORLD_W - 50);
            walkPhase += dt * 11;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.J)) doAttack();
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) useSkill(0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) useSkill(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) useSkill(2);

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
        if (!gone) update(dt);
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
        // far parallax art strip
        if (game.assets.adventure != null) {
            game.batch.begin();
            float tw = 900;
            float off = -camX * 0.4f % tw;
            for (float bx = camX - viewW / 2 + off - tw; bx < camX + viewW / 2 + tw; bx += tw) {
                game.batch.draw(game.assets.adventure, bx, GROUND - 10, tw, 330);
            }
            game.batch.end();
        }
        Draw.drawSky(game.shapes, camX - viewW / 2 - 10, viewW + 20, dayness, storm, time);
        if (game.assets.adventure == null) {
            Draw.drawTrunkWorld(game.shapes, camX - viewW / 2 - 10, WORLD_W, GROUND, 1, time);
        } else {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(0.16f, 0.12f, 0.08f, 1);
            game.shapes.rect(camX - viewW / 2 - 10, 0, viewW + 20, GROUND);
            game.shapes.setColor(0.25f, 0.95f, 1f, 0.4f);
            game.shapes.rect(camX - viewW / 2 - 10, GROUND - 3, viewW + 20, 3);
            game.shapes.end();
        }

        // exit gate
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.20f, 0.30f, 0.38f, 1);
        game.shapes.rect(30, GROUND, 80, 140);
        game.shapes.setColor(0.35f, 0.85f, 0.95f, 0.6f);
        game.shapes.rect(38, GROUND + 10, 64, 120);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Về Làng", 70, GROUND + 158, Ui.TEXT, Align.center);
        game.batch.end();

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

        for (Mon m : mons) {
            Draw.drawMonster(game.shapes, m.x, GROUND, m.def, m.hp / m.maxHp, time, m.flash);
            Ui.nameTag(game.batch, game.shapes, game.assets.fontSmall, m.def.displayName + " Lv" + m.lvl, m.x, GROUND + 66);
        }
        Draw.drawPet(game.shapes, petX, GROUND, time, petTier());
        if (shieldT > 0) {
            game.shapes.begin(ShapeType.Line);
            game.shapes.setColor(0.4f, 0.85f, 1f, 0.6f);
            game.shapes.circle(px, GROUND + 34, 34);
            game.shapes.end();
        }
        Draw.drawHero(game.shapes, game.batch, game.assets.fontSmall, px, GROUND, 1.18f, p.gender,
            p.suitColorIdx, p.hairColorIdx, p.hairStyleIdx, p.eyeColorIdx, p.skinToneIdx, p.outfitId, p.gameClass,
            facing, walkPhase, time);

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

        // top-left: HP + energy + survival minis
        Ui.panel(game.shapes, 16, 606, 330, 98);
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "HP", 28, 686, Ui.TEXT_DIM, Align.left);
        Ui.label(game.batch, game.assets.fontSmall, "NL", 28, 656, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        Ui.bar(game.shapes, 62, 672, 268, 13, (float) ((double) p.hp / Math.max(1, (double) p.maxHp())),
            new Color(0.1f, 0.1f, 0.14f, 1), Ui.RED);
        Ui.bar(game.shapes, 62, 642, 268, 13, energy / 100f,
            new Color(0.1f, 0.1f, 0.14f, 1), Ui.CYAN);
        miniSurv(370, 678, "No", (double) p.satiety / 100.0, Ui.GREEN);
        miniSurv(370, 648, "Ấm", (double) p.warmth / 100.0, Ui.ACCENT);
        miniSurv(370, 618, "Pin", (double) p.battery / 100.0, Ui.CYAN);

        // minimap top-right
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.04f, 0.08f, 0.12f, 0.85f);
        game.shapes.rect(1052, 646, 212, 58);
        game.shapes.setColor(Ui.PANEL_EDGE);
        game.shapes.rect(1052, 702, 212, 2);
        game.shapes.setColor(1f, 0.3f, 0.3f, 1);
        for (Mon m : mons) game.shapes.circle(1056 + (m.x / WORLD_W) * 204, 668, 2.4f);
        game.shapes.setColor(0.3f, 0.95f, 0.4f, 1);
        game.shapes.circle(1056 + (30f / WORLD_W) * 204, 668, 3);
        game.shapes.setColor(Color.WHITE);
        game.shapes.circle(1056 + (px / WORLD_W) * 204, 668, 3.4f);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Bìa Rừng Mạch Điện", 1158, 640, Ui.TEXT_DIM, Align.center);
        game.batch.end();

        if (Ui.button(game.batch, game.shapes, game.assets.fontSmall, hudVp, 1090, 560, 174, 40, "Về Làng")) {
            p.x = 2600;
            game.saveGame();
            gone = true;
            game.gotoTown();
            return;
        }
        if (px < 130) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, "Lại gần cổng hoặc bấm Về Làng", 640, 210, Ui.TEXT_DIM, Align.center);
            game.batch.end();
        }

        // storm reward
        if (game.state.storm.active && game.state.storm.objectiveDone()) {
            if (Ui.button(game.batch, game.shapes, game.assets.font, hudVp, 465, 620, 350, 48, "Nhận thưởng bão")) {
                game.state.storm.claimReward(p);
                game.sound.play("levelup");
                game.state.notice = "Đã nhận thưởng Bão Dữ Liệu!";
                game.saveGame();
            }
        } else if (game.state.storm.active) {
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall,
                "BÃO DỮ LIỆU — hạ quái: " + game.state.storm.kills + "/" + game.state.storm.objectiveKills,
                640, 700, new Color(1f, 0.75f, 0.95f, 1), Align.center);
            game.batch.end();
        }

        // joystick visual
        if (joyActive) {
            game.shapes.begin(ShapeType.Filled);
            game.shapes.setColor(1, 1, 1, 0.15f);
            game.shapes.circle(joyBase.x, joyBase.y, 52);
            game.shapes.setColor(0.35f, 0.95f, 1f, 0.5f);
            game.shapes.circle(joyBase.x + joyVec * 38, joyBase.y, 22);
            game.shapes.end();
        }

        // attack button (big) + skills
        boolean atkTap = Ui.tapped(hudVp, 1130, 30, 120, 120);
        game.shapes.begin(ShapeType.Filled);
        game.shapes.setColor(0.85f, 0.25f, 0.20f, 0.92f);
        game.shapes.circle(1190, 90, 58);
        game.shapes.setColor(1f, 0.6f, 0.4f, 1);
        game.shapes.circle(1190, 90, 48);
        game.shapes.end();
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Đánh", 1190, 96, Color.WHITE, Align.center);
        game.batch.end();
        if (atkTap) doAttack();

        List<Skill> list = skills();
        for (int i = 0; i < 3; i++) {
            float bx = 830 + i * 96;
            float by = 30;
            boolean ready = cds[i] <= 0 && energy >= list.get(i).cost;
            if (Ui.button(game.batch, game.shapes, game.assets.fontSmall, hudVp, bx, by, 88, 88,
                "", ready)) {
                useSkill(i);
            }
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, list.get(i).name, bx + 44, by + 62, ready ? Ui.TEXT : Ui.TEXT_DIM, Align.center);
            Ui.label(game.batch, game.assets.fontSmall, "NL " + (int) list.get(i).cost, bx + 44, by + 42, Ui.CYAN, Align.center);
            if (cds[i] > 0) {
                Ui.label(game.batch, game.assets.font, String.format("%.0f", Math.ceil(cds[i])), bx + 44, by + 24, Ui.RED, Align.center);
            }
            game.batch.end();
            // cooldown sweep hint: dark overlay proportion
            if (cds[i] > 0) {
                game.shapes.begin(ShapeType.Filled);
                game.shapes.setColor(0, 0, 0, 0.45f);
                float h = 88 * Math.min(1, cds[i] / Math.max(0.1f, list.get(i).cd));
                game.shapes.rect(bx, by, 88, h);
                game.shapes.end();
            }
        }
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, "Phím: A/D di chuyển — Space đánh — 1/2/3 kỹ năng", 26, 30, Ui.TEXT_DIM, Align.left);
        game.batch.end();

        if (lastNotice != null && !lastNotice.isEmpty() && noticeT < 4.5f) {
            float w = Math.min(760, Ui.textWidth(game.assets.fontSmall, lastNotice) + 36);
            Ui.panel(game.shapes, 640 - w / 2, 540, w, 40, new Color(0.06f, 0.14f, 0.10f, 0.94f));
            game.batch.begin();
            Ui.label(game.batch, game.assets.fontSmall, lastNotice, 640, 566, Ui.GREEN, Align.center);
            game.batch.end();
        }
    }

    private void miniSurv(float x, float y, String name, double frac, Color c) {
        game.batch.begin();
        Ui.label(game.batch, game.assets.fontSmall, name, x, y + 10, Ui.TEXT_DIM, Align.left);
        game.batch.end();
        Ui.bar(game.shapes, x + 30, y, 64, 9, (float) frac, new Color(0.1f, 0.12f, 0.16f, 1), c);
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
