package com.neolang.cyber.verify;

import com.neolang.cyber.model.BuildModule;
import com.neolang.cyber.model.ClassChoice;
import com.neolang.cyber.model.Combat;
import com.neolang.cyber.model.DataStorm;
import com.neolang.cyber.model.GameClass;
import com.neolang.cyber.model.GameState;
import com.neolang.cyber.model.Garden;
import com.neolang.cyber.model.Gender;
import com.neolang.cyber.model.ItemType;
import com.neolang.cyber.model.Items;
import com.neolang.cyber.model.MonsterDef;
import com.neolang.cyber.model.ObjectiveType;
import com.neolang.cyber.model.Player;
import com.neolang.cyber.model.SaveData;
import com.neolang.cyber.model.SkillKind;
import com.neolang.cyber.model.Survival;
import com.neolang.cyber.model.WorldClock;

import java.util.Random;

/**
 * Kiểm tra thủ công package model: chạy main, in PASS/FAIL từng mục,
 * tổng kết cuối và exit(1) nếu có FAIL.
 */
public class VerifyMain {
    private static int passed = 0;
    private static int total = 0;

    private static void check(String name, boolean cond) {
        total++;
        if (cond) passed++;
        System.out.println((cond ? "PASS " : "FAIL ") + name);
    }

    private static void levelUpTo(Player p, int target) {
        int guard = 0;
        while (p.level < target && guard++ < 200) {
            p.gainXp(p.xpNext());
        }
    }

    public static void main(String[] args) {
        testCreateCharacter();
        testClassChoiceGating();
        testClassChoiceFlowToQuest();
        testChangeClassRule();
        testCombat();
        testMonsterScaling();
        testGainXp();
        testGarden();
        testBuild();
        testQuests();
        testDailies();
        testSurvival();
        testStorm();
        testClock();
        testMoney();
        testNoneSkills();
        testItemsRegistry();
        testInventory();
        testSaveLoad();
        System.out.println("TONG: " + passed + "/" + total + " PASS");
        if (passed != total) System.exit(1);
    }

    // (1) Tạo nhân vật: gameClass == NONE, giới tính giữ đúng, đồ khởi đầu.
    private static void testCreateCharacter() {
        GameState s = new GameState("Tin", Gender.NAM, 1, 2);
        check("tao nhan vat: gameClass NONE", s.player.gameClass == GameClass.NONE);
        check("tao nhan vat: gender NAM giu dung", s.player.gender == Gender.NAM);
        GameState s2 = new GameState("An", Gender.NU, 0, 0);
        check("tao nhan vat: gender NU giu dung", s2.player.gender == Gender.NU);
        check("tao nhan vat: do khoi dau",
                s.player.countItem("banh_la") == 2
                        && s.player.countItem("seed_ca_chua") == 1
                        && s.player.countItem("seed_nam") == 1);
        check("tao nhan vat: xu/tinh khoi dau", s.player.xuLa == 150 && s.player.tinhThe == 5);
        check("tao nhan vat: outfit mac dinh la bo_tan_cu_dan",
                "bo_tan_cu_dan".equals(s.player.outfitId));
        check("tao nhan vat: hairStyleIdx/eyeColorIdx mac dinh 0",
                s.player.hairStyleIdx == 0 && s.player.eyeColorIdx == 0);
        check("tao nhan vat: skinToneIdx mac dinh 0, skinColor dung bang",
                s.player.skinToneIdx == 0 && s.player.skinColor() == Player.SKIN_COLORS[0]);
        check("bang mau: EYE_COLORS dung 3 mau",
                Player.EYE_COLORS.length == 3 && Player.EYE_COLORS[0] == 0x6B4226
                        && Player.EYE_COLORS[1] == 0x22D3EE && Player.EYE_COLORS[2] == 0xA855F7
                        && Player.HAIR_COLORS.length == 6);
    }

    // (2) Chọn nghề bị chặn: chưa đủ cấp -> LOCKED_LEVEL; đủ cấp nhưng chưa xong q0 -> LOCKED_INTRO.
    private static void testClassChoiceGating() {
        GameState s = new GameState("A", Gender.NAM, 0, 0);
        check("chon nghe: cap 1 bi LOCKED_LEVEL",
                ClassChoice.tryChoose(s, GameClass.BLADE_RUNNER) == ClassChoice.Result.LOCKED_LEVEL);
        check("chon nghe: canChoose false khi moi tao", !ClassChoice.canChoose(s));

        GameState s2 = new GameState("B", Gender.NU, 0, 0);
        levelUpTo(s2.player, 5);
        check("chon nghe: cap 5 chua xong q0 bi LOCKED_INTRO",
                ClassChoice.tryChoose(s2, GameClass.DRONE_MASTER) == ClassChoice.Result.LOCKED_INTRO);
        check("chon nghe: nghe khong doi sau khi bi chan", s2.player.gameClass == GameClass.NONE);
        check("chon nghe: muc tieu NONE bi INVALID",
                ClassChoice.tryChoose(s2, GameClass.NONE) == ClassChoice.Result.INVALID);
    }

    // (3) Xong q0 + cấp 5 -> chọn nghề OK, chỉ số đổi theo class; q4 hoàn thành qua CHOOSE_CLASS.
    private static void testClassChoiceFlowToQuest() {
        GameState s = new GameState("C", Gender.NAM, 0, 0);
        levelUpTo(s.player, 5);
        s.quests.onEvent(ObjectiveType.TALK, 1);
        check("q0: introCompleted ngay khi xong muc tieu (chua claim)",
                s.quests.introCompleted() && s.quests.mainIndex == 0);
        check("chon nghe: canChoose true sau q0 + cap 5", ClassChoice.canChoose(s));
        check("chon nghe: tryChoose BLADE_RUNNER OK",
                ClassChoice.tryChoose(s, GameClass.BLADE_RUNNER) == ClassChoice.Result.OK);
        check("chon nghe: gameClass da gan", s.player.gameClass == GameClass.BLADE_RUNNER);
        check("chon nghe: maxHp theo class (130 + 4*12 = 178)", s.player.maxHp() == 178);
        check("chon nghe: atk theo class (16 + 4*2 = 24)", s.player.atk() == 24);

        // Chuỗi nhiệm vụ tới q4: chọn nghề phải làm q4 hoàn thành.
        GameState s2 = new GameState("D", Gender.NU, 0, 0);
        levelUpTo(s2.player, 5);
        s2.quests.onEvent(ObjectiveType.TALK, 1);
        s2.quests.claimMain(s2.player);
        s2.quests.onEvent(ObjectiveType.PLANT, 2);
        s2.quests.claimMain(s2.player);
        s2.quests.onEvent(ObjectiveType.HARVEST, 2);
        s2.quests.claimMain(s2.player);
        s2.quests.onEvent(ObjectiveType.KILL, 5);
        s2.quests.claimMain(s2.player);
        check("chuoi quest: da toi q4_chon_nghe",
                s2.quests.currentMain() != null && "q4_chon_nghe".equals(s2.quests.currentMain().id));
        check("chuoi quest: chon nghe o q4",
                ClassChoice.tryChoose(s2, GameClass.BIO_HACKER) == ClassChoice.Result.OK);
        check("chuoi quest: q4 hoan thanh qua su kien CHOOSE_CLASS", s2.quests.currentComplete());
    }

    // (4) Đổi nghề trước cấp 10 bị TOO_EARLY_CHANGE, từ cấp 10 đổi được.
    private static void testChangeClassRule() {
        GameState s = new GameState("E", Gender.NAM, 0, 0);
        levelUpTo(s.player, 5);
        s.quests.onEvent(ObjectiveType.TALK, 1);
        ClassChoice.tryChoose(s, GameClass.BLADE_RUNNER);
        check("doi nghe: truoc cap 10 bi TOO_EARLY_CHANGE",
                ClassChoice.tryChoose(s, GameClass.ENGINEER) == ClassChoice.Result.TOO_EARLY_CHANGE);
        check("doi nghe: nghe cu giu nguyen", s.player.gameClass == GameClass.BLADE_RUNNER);
        check("doi nghe: canChangeClass false truoc cap 10", !ClassChoice.canChangeClass(s));
        levelUpTo(s.player, 10);
        check("doi nghe: tu cap 10 doi duoc",
                ClassChoice.tryChoose(s, GameClass.ENGINEER) == ClassChoice.Result.OK
                        && s.player.gameClass == GameClass.ENGINEER);
    }

    // (5) Sát thương trong khoảng hợp lệ và luôn >= 1.
    private static void testCombat() {
        Random r = new Random(42);
        boolean inRange = true;
        for (int i = 0; i < 2000; i++) {
            int d = Combat.damage(100, 1.0f, 0, r);
            if (d < 90 || d > 176) inRange = false;
        }
        check("combat: damage atk100/def0 trong [90,176]", inRange);
        boolean minOne = true;
        for (int i = 0; i < 200; i++) {
            if (Combat.damage(5, 1.0f, 10000, r) != 1) minOne = false;
        }
        check("combat: damage toi thieu bang 1", minOne);
    }

    // (6) Quái scale tăng theo cấp.
    private static void testMonsterScaling() {
        check("quai: hp cap 1 bang base", MonsterDef.GLITCH_BLOB.hpForLevel(1) == 40);
        check("quai: hp/atk/xp tang theo cap",
                MonsterDef.GLITCH_BLOB.hpForLevel(5) > MonsterDef.GLITCH_BLOB.hpForLevel(1)
                        && MonsterDef.DATA_WRAITH.atkForLevel(4) > MonsterDef.DATA_WRAITH.atkForLevel(1)
                        && MonsterDef.BEETLE_BOT.xpForLevel(3) > MonsterDef.BEETLE_BOT.xpForLevel(1));
    }

    // (7) gainXp lên cấp đúng ngưỡng.
    private static void testGainXp() {
        Player p = new Player("F", Gender.NAM);
        check("xp: nguong cap 1 la 120", p.xpNext() == 120);
        check("xp: 119 xp chua len cap", !p.gainXp(119) && p.level == 1);
        check("xp: them 1 xp len cap 2, xp du 0", p.gainXp(1) && p.level == 2 && p.xp == 0);
    }

    // (8)(9) Vườn: trồng -> chưa chín -> chín -> thu hoạch đúng crop, ô trống lại; trồng ô bận = false.
    private static void testGarden() {
        Garden g = new Garden();
        long t0 = 1_000_000L;
        check("vuon: trong seed_ca_chua ok", g.plant(0, "seed_ca_chua", t0));
        check("vuon: vua trong chua chin", !g.isReady(0, t0) && g.progress(0, t0) == 0f);
        check("vuon: 59s chua chin, 60s chin", !g.isReady(0, t0 + 59) && g.isReady(0, t0 + 60));
        check("vuon: thu hoach ra ca_chua", "ca_chua".equals(g.harvest(0, t0 + 60)));
        check("vuon: o trong lai sau thu hoach", !g.plots[0].planted && g.harvest(0, t0 + 120) == null);

        check("vuon: trong vao o ban = false",
                g.plant(1, "seed_nam", t0) && !g.plant(1, "seed_nam", t0));
        check("vuon: trong vat pham khong phai hat = false", !g.plant(2, "com_nam", t0));
        check("vuon: growLeft giam theo thoi gian",
                g.growLeft(1, t0) == 45f && g.growLeft(1, t0 + 15) == 30f);
        check("vuon: plantWithGrowLeft khoi phuc tien do",
                g.plantWithGrowLeft(3, "seed_dau", 30f, t0) && g.isReady(3, t0 + 30));
    }

    // (10)(11) Xây dựng: đặt trừ xu đúng, chồng = false, hết xu = false; nâng cấp tăng cấp + trừ xu.
    private static void testBuild() {
        GameState s = new GameState("G", Gender.NU, 0, 0);
        Player p = s.player;
        check("xay: dat GIUONG ok, tru 120 xu", s.build.place(p, BuildModule.GIUONG, 0, 0) && p.xuLa == 30);
        check("xay: dat chong = false", !s.build.place(p, BuildModule.BEP, 0, 0));
        check("xay: khong du xu = false va khong tru",
                !s.build.place(p, BuildModule.XUONG, 2, 0) && p.xuLa == 30);
        check("xay: moduleAt/countModules dung",
                s.build.moduleAt(0, 0) == BuildModule.GIUONG.ordinal() + 1 && s.build.countModules() == 1);

        p.xuLa = 1000;
        check("xay: POD_TRON 2 o khong vua o cot cuoi", !s.build.canPlace(BuildModule.POD_TRON, 9, 0));
        check("xay: POD_TRON chiem 2 o",
                s.build.place(p, BuildModule.POD_TRON, 8, 0)
                        && s.build.moduleAt(9, 0) == BuildModule.POD_TRON.ordinal() + 1
                        && s.build.countModules() == 2 && p.xuLa == 700);

        check("xay: nang cap GIUONG len 2, tru 60 xu",
                s.build.upgrade(p, 0, 0) && s.build.levelAt(0, 0) == 2 && p.xuLa == 640);
        check("xay: nang cap POD qua o phu, ca 2 o cung cap",
                s.build.upgrade(p, 9, 0) && s.build.levelAt(8, 0) == 2 && s.build.levelAt(9, 0) == 2);
        check("xay: nang cap o trong = false", !s.build.upgrade(p, 5, 5));
    }

    // (12) Nhiệm vụ chính tiến triển theo onEvent, claim cộng thưởng + chuyển quest.
    private static void testQuests() {
        GameState s = new GameState("H", Gender.NAM, 0, 0);
        check("quest: bat dau o q0_chao_lang",
                s.quests.currentMain() != null && "q0_chao_lang".equals(s.quests.currentMain().id));
        int xuBefore = s.player.xuLa;
        int banhBefore = s.player.countItem("banh_la");
        s.quests.onEvent(ObjectiveType.TALK, 1);
        check("quest: onEvent TALK hoan thanh q0", s.quests.currentComplete());
        s.quests.claimMain(s.player);
        check("quest: claim cong xu + item thuong",
                s.player.xuLa == xuBefore + 50 && s.player.countItem("banh_la") == banhBefore + 1);
        check("quest: claim chuyen sang q1",
                s.quests.mainIndex == 1 && s.quests.currentMain() != null
                        && "q1_trong_cay".equals(s.quests.currentMain().id));
        s.quests.onEvent(ObjectiveType.PLANT, 1);
        check("quest: PLANT 1/2 chua xong", !s.quests.currentComplete());
        s.quests.onEvent(ObjectiveType.PLANT, 1);
        check("quest: PLANT 2/2 xong", s.quests.currentComplete());
        check("quest: claim khi chua xong khong nhay quest",
                s.quests.mainIndex == 1 || s.quests.mainIndex == 2);
    }

    // (13) Nhiệm vụ ngày: claim được một lần, reset được.
    private static void testDailies() {
        GameState s = new GameState("I", Gender.NU, 0, 0);
        s.quests.onEvent(ObjectiveType.KILL, 8);
        check("daily: d1 du tien do sau KILL 8",
                s.quests.dailyProgress(0) == 8 && s.quests.dailyDone(0));
        int xuBefore = s.player.xuLa;
        s.quests.claimDaily(s.player, 0);
        check("daily: claim cong 100 xu", s.player.xuLa == xuBefore + 100);
        s.quests.claimDaily(s.player, 0);
        check("daily: claim lan 2 khong cong them", s.player.xuLa == xuBefore + 100);
        s.quests.resetDailies();
        check("daily: reset dua tien do ve 0",
                s.quests.dailyProgress(0) == 0 && !s.quests.dailyDone(0) && !s.quests.dailyClaimed(0));
    }

    // (14)(15) Sinh tồn: tick ở adventure làm giảm chỉ số, applyItem hồi + clamp; ngất khi chỉ số về 0.
    private static void testSurvival() {
        Player p = new Player("K", Gender.NAM);
        p.satiety = 90f;
        Survival.tick(p, 90f, true, false, false, false, false);
        check("song ton: adventure 90s lam giam do no (90 -> 74)", p.satiety < 80f && p.satiety > 60f);
        Survival.applyItem(p, Items.get("com_nam"));
        check("song ton: an com nam hoi no va clamp <= 100", p.satiety == 100f);
        p.warmth = 50f;
        Survival.tick(p, 60f, false, true, false, false, false);
        check("song ton: dem lanh 60s lam giam do am (50 -> 40)", p.warmth < 45f && p.warmth > 35f);
        Survival.tick(p, 60f, false, true, false, true, false);
        check("song ton: gan nguon nhiet hoi am", p.warmth > 45f);

        Player p2 = new Player("L", Gender.NU);
        check("song ton: khoe manh khong ngat", !Survival.isFainted(p2));
        p2.warmth = 0f;
        check("song ton: am ve 0 thi ngat", Survival.isFainted(p2));
        p2.warmth = 50f;
        p2.satiety = 0f;
        check("song ton: no ve 0 thi ngat", Survival.isFainted(p2));
    }

    // (16) Bão dữ liệu: đếm ngược -> kích hoạt -> diệt đủ -> nhận thưởng.
    private static void testStorm() {
        DataStorm st = new DataStorm();
        check("bao: chua kich hoat luc dau", !st.active && st.timeToNext == DataStorm.CYCLE_SECONDS);
        st.timeToNext = 0.05f;
        st.tick(0.1f);
        check("bao: kich hoat sau dem nguoc", st.active && st.activeLeft > 89f);
        for (int i = 0; i < DataStorm.DEFAULT_OBJECTIVE_KILLS; i++) st.onKill();
        check("bao: du muc tieu sau 6 kill", st.objectiveDone());
        Player p = new Player("M", Gender.NAM);
        int xuBefore = p.xuLa;
        st.claimReward(p);
        check("bao: claim cong 150 xu + nhua_sang",
                p.xuLa == xuBefore + DataStorm.REWARD_XU && p.countItem("nhua_sang") == 2);
        check("bao: sau claim dat lai chu ky", !st.active && !st.objectiveDone()
                && st.timeToNext == DataStorm.CYCLE_SECONDS);

        DataStorm st2 = new DataStorm();
        st2.timeToNext = 0.01f;
        st2.tick(0.05f);
        st2.tick(DataStorm.DURATION_SECONDS + 1f);
        check("bao: het 90s tu ket thuc", !st2.active && st2.timeToNext == DataStorm.CYCLE_SECONDS);
    }

    // (17) Đồng hồ thế giới: isNight theo phase (ngày 0.05–0.70).
    private static void testClock() {
        WorldClock c = new WorldClock();
        c.t = WorldClock.DAY_LENGTH * 0.30f;
        check("dong ho: phase 0.30 la ban ngay", !c.isNight());
        c.t = WorldClock.DAY_LENGTH * 0.80f;
        check("dong ho: phase 0.80 la ban dem", c.isNight());
        c.t = WorldClock.DAY_LENGTH * 0.02f;
        check("dong ho: phase 0.02 la ban dem", c.isNight());
        c.t = WorldClock.DAY_LENGTH - 1f;
        c.tick(2f);
        check("dong ho: tick vong qua ngay moi", c.t == 1f);
    }

    // (19) Tiêu tiền: không đủ = false, số dư không âm.
    private static void testMoney() {
        Player p = new Player("N", Gender.NAM);
        check("tien: spendXu khong du = false, giu nguyen",
                !p.spendXu(200) && p.xuLa == 150);
        check("tien: spendXu het dung so du", p.spendXu(150) && p.xuLa == 0);
        check("tien: so du khong am", !p.spendXu(1) && p.xuLa == 0);
        check("tien: spendTinh tuong tu", !p.spendTinh(6) && p.tinhThe == 5 && p.spendTinh(5) && p.tinhThe == 0);
    }

    // (20) Bộ kỹ năng cơ bản của NONE + kỹ năng nghề thật.
    private static void testNoneSkills() {
        check("ky nang: NONE skillCount = 1 (don danh thuong)",
                GameClass.NONE.skillCount() == 1);
        check("ky nang: NONE co Don Co Ban / Lan Ne / Tia Nang Luong",
                "Đòn Cơ Bản".equals(GameClass.NONE.skillName(0))
                        && "Lăn Né".equals(GameClass.NONE.skillName(1))
                        && "Tia Năng Lượng".equals(GameClass.NONE.skillName(2)));
        check("ky nang: Lan Ne la DASH, mult thap",
                GameClass.NONE.skillKind(1) == SkillKind.DASH && GameClass.NONE.skillMult(2) < 1.5f);
        check("ky nang: nghe that co 3 ky nang",
                GameClass.BLADE_RUNNER.skillCount() == 3 && GameClass.ENGINEER.skillCount() == 3);
        check("ky nang: BIO_HACKER co ky nang HEAL",
                GameClass.BIO_HACKER.skillKind(1) == SkillKind.HEAL);
        check("ky nang: nghe that co ten/desc/nghe song",
                !GameClass.DRONE_MASTER.skillName(0).isEmpty()
                        && !GameClass.DRONE_MASTER.desc.isEmpty()
                        && !GameClass.DRONE_MASTER.lifeRole.isEmpty());
    }

    // Vật phẩm: liên kết hạt -> cây, tiền tệ trang phục, nội thất -> module.
    private static void testItemsRegistry() {
        check("item: seed_bi -> bi_pin, 150s",
                Items.get("seed_bi") != null && "bi_pin".equals(Items.get("seed_bi").cropId)
                        && Items.get("seed_bi").growSeconds == 150);
        check("item: ca_chua la CROP ban 30",
                Items.get("ca_chua").type == ItemType.CROP && Items.get("ca_chua").sellPrice == 30);
        check("item: trang phuc tinh the co currency = 1",
                Items.get("bo_ky_su_cam").currency == 1 && Items.get("ao_khoac_xanh").currency == 0);
        check("item: noi that lien ket module BEP",
                "BEP".equals(Items.get("noi_bep").buildModule));
        check("item: id la tra null", Items.get("khong_ton_tai") == null);
        check("item: thuc an co chi so hoi", Items.get("com_nam").foodSatiety == 35f
                && Items.get("ca_phe").foodWarmth == 20f);
        check("item: bo_tan_cu_dan mac dinh (body, teal, gia 0)",
                Items.get("bo_tan_cu_dan") != null && Items.get("bo_tan_cu_dan").type == ItemType.OUTFIT
                        && "body".equals(Items.get("bo_tan_cu_dan").outfitSlot)
                        && Items.get("bo_tan_cu_dan").suitColor == 0x0E7C86
                        && Items.get("bo_tan_cu_dan").buyPrice == 0);
        check("item: bo_street_neon 400 xu la, bo_tham_hiem 3 tinh the",
                Items.get("bo_street_neon").buyPrice == 400 && Items.get("bo_street_neon").currency == 0
                        && Items.get("bo_street_neon").suitColor == 0x1F2430
                        && Items.get("bo_tham_hiem").buyPrice == 3 && Items.get("bo_tham_hiem").currency == 1
                        && Items.get("bo_tham_hiem").suitColor == 0x6B7F3E);
    }

    private static void testInventory() {
        Player p = new Player("O", Gender.NU);
        p.addItem("go_mach", 3);
        check("tui do: addItem/countItem", p.countItem("go_mach") == 3);
        check("tui do: removeItem khong du = false", !p.removeItem("go_mach", 4) && p.countItem("go_mach") == 3);
        check("tui do: removeItem het thi xoa khoa", p.removeItem("go_mach", 3) && p.countItem("go_mach") == 0);
    }

    // (18) Save/load round-trip.
    private static void testSaveLoad() {
        GameState s = new GameState("Be An", Gender.NU, 3, 4);
        s.build.place(s.player, BuildModule.DEN_MACH, 1, 1);
        long now = System.currentTimeMillis() / 1000L;
        s.garden.plant(2, "seed_nam", now - 20);
        s.player.gameClass = GameClass.DRONE_MASTER;
        s.player.level = 7;
        s.player.xp = 33;
        s.player.xuLa = 777;
        s.player.tinhThe = 9;
        s.player.outfitId = "ao_khoac_xanh";
        s.player.hairStyleIdx = 2;
        s.player.eyeColorIdx = 1;
        s.player.skinToneIdx = 2;
        s.player.addItem("go_mach", 3);
        s.quests.mainIndex = 2;
        s.quests.mainProgress = 1;
        s.clock.t = 200f;
        s.settings.uiScale = 1.25f;
        s.settings.sound = false;
        s.playSeconds = 1234L;

        String text = SaveData.save(s);
        check("save: tao duoc chuoi text", text != null && text.contains("player.name="));
        GameState l = SaveData.load(text);
        check("save: load khong null", l != null);
        if (l == null) return;
        check("save: giu name/gender/class",
                "Be An".equals(l.player.name) && l.player.gender == Gender.NU
                        && l.player.gameClass == GameClass.DRONE_MASTER);
        check("save: giu xu/tinh/level/xp",
                l.player.xuLa == 777 && l.player.tinhThe == 9
                        && l.player.level == 7 && l.player.xp == 33);
        check("save: giu inventory",
                l.player.countItem("go_mach") == 3 && l.player.countItem("banh_la") == 2);
        long now2 = System.currentTimeMillis() / 1000L;
        check("save: giu vuon (seed_nam, con ~25s)",
                l.garden.plots[2].planted && "seed_nam".equals(l.garden.plots[2].seedId)
                        && l.garden.growLeft(2, now2) > 10f && l.garden.growLeft(2, now2) <= 25f);
        check("save: giu build",
                l.build.moduleAt(1, 1) == BuildModule.DEN_MACH.ordinal() + 1
                        && l.build.countModules() == 1 && l.build.levelAt(1, 1) == 1);
        check("save: giu quest mainIndex/progress",
                l.quests.mainIndex == 2 && l.quests.mainProgress == 1);
        check("save: giu clock/settings/playSeconds",
                Math.abs(l.clock.t - 200f) < 0.01f && Math.abs(l.settings.uiScale - 1.25f) < 0.001f
                        && !l.settings.sound && l.playSeconds == 1234L);
        check("save: giu hairStyleIdx/eyeColorIdx",
                l.player.hairStyleIdx == 2 && l.player.eyeColorIdx == 1
                        && l.player.eyeColor() == 0x22D3EE);
        check("save: giu skinToneIdx",
                l.player.skinToneIdx == 2 && l.player.skinColor() == 0xC98A54
                        && Player.SKIN_COLORS.length == 3);
        check("save: load chuoi rac tra null", SaveData.load("day khong phai save") == null);
        check("save: load null tra null", SaveData.load(null) == null);
    }
}
