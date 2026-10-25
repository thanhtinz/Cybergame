package com.neolang.cyber.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Lưu/nạp game dạng text key=value, mỗi dòng một khóa, không cần thư viện ngoài.
 *
 * Vườn được lưu bằng số giây còn lại tới khi chín (growLeft) thay vì epoch
 * tuyệt đối, để file save không phụ thuộc đồng hồ hệ thống.
 * Giá trị chuỗi được escape: % -> %25, = -> %3D, xuống dòng -> %0A, CR -> %0D.
 * Load lỗi (thiếu khóa bắt buộc, số không hợp lệ...) trả null.
 */
public final class SaveData {
    private SaveData() {
    }

    public static String save(GameState s) {
        if (s == null || s.player == null) return null;
        Player p = s.player;
        StringBuilder b = new StringBuilder();
        line(b, "version", "1");
        line(b, "player.name", esc(p.name));
        line(b, "player.gender", p.gender == null ? Gender.NAM.name() : p.gender.name());
        line(b, "player.gameClass", p.gameClass == null ? GameClass.NONE.name() : p.gameClass.name());
        line(b, "player.level", Integer.toString(p.level));
        line(b, "player.xp", Integer.toString(p.xp));
        line(b, "player.xuLa", Integer.toString(p.xuLa));
        line(b, "player.tinhThe", Integer.toString(p.tinhThe));
        line(b, "player.hp", Float.toString(p.hp));
        line(b, "player.satiety", Float.toString(p.satiety));
        line(b, "player.warmth", Float.toString(p.warmth));
        line(b, "player.battery", Float.toString(p.battery));
        line(b, "player.x", Float.toString(p.x));
        line(b, "player.y", Float.toString(p.y));
        line(b, "player.suitColorIdx", Integer.toString(p.suitColorIdx));
        line(b, "player.hairColorIdx", Integer.toString(p.hairColorIdx));
        line(b, "player.hairStyleIdx", Integer.toString(p.hairStyleIdx));
        line(b, "player.eyeColorIdx", Integer.toString(p.eyeColorIdx));
        line(b, "player.skinToneIdx", Integer.toString(p.skinToneIdx));
        line(b, "player.outfitId", esc(p.outfitId == null ? "bo_tan_cu_dan" : p.outfitId));
        for (Map.Entry<String, Integer> e : p.inventory.entrySet()) {
            line(b, "inv." + e.getKey(), Integer.toString(e.getValue()));
        }

        long now = System.currentTimeMillis() / 1000L;
        for (int i = 0; i < Garden.PLOT_COUNT; i++) {
            Garden.Plot plot = s.garden.plots[i];
            line(b, "garden." + i + ".planted", plot.planted ? "1" : "0");
            if (plot.planted) {
                line(b, "garden." + i + ".seed", esc(plot.seedId));
                line(b, "garden." + i + ".growLeft", Float.toString(s.garden.growLeft(i, now)));
            }
        }

        int n = BuildPlot.COLS * BuildPlot.ROWS;
        int[] cells = new int[n];
        int[] levels = new int[n];
        int[] anchors = new int[n];
        s.build.exportData(cells, levels, anchors);
        line(b, "build.cells", csv(cells));
        line(b, "build.levels", csv(levels));
        line(b, "build.anchors", csv(anchors));

        line(b, "quests.mainIndex", Integer.toString(s.quests.mainIndex));
        line(b, "quests.mainProgress", Integer.toString(s.quests.mainProgress));
        line(b, "quests.dailyProgress", csv(s.quests.dailyProgressArray()));
        boolean[] claimed = s.quests.dailyClaimedArray();
        StringBuilder cb = new StringBuilder();
        for (int i = 0; i < claimed.length; i++) {
            if (i > 0) cb.append(',');
            cb.append(claimed[i] ? '1' : '0');
        }
        line(b, "quests.dailyClaimed", cb.toString());

        line(b, "storm.timeToNext", Float.toString(s.storm.timeToNext));
        line(b, "storm.activeLeft", Float.toString(s.storm.activeLeft));
        line(b, "storm.active", s.storm.active ? "1" : "0");
        line(b, "storm.kills", Integer.toString(s.storm.kills));

        line(b, "clock.t", Float.toString(s.clock.t));
        line(b, "playSeconds", Long.toString(s.playSeconds));
        line(b, "notice", esc(s.notice == null ? "" : s.notice));

        line(b, "settings.uiScale", Float.toString(s.settings.uiScale));
        line(b, "settings.fps60", s.settings.fps60 ? "1" : "0");
        line(b, "settings.sound", s.settings.sound ? "1" : "0");
        line(b, "settings.music", s.settings.music ? "1" : "0");
        return b.toString();
    }

    public static GameState load(String text) {
        if (text == null) return null;
        try {
            Map<String, String> kv = new HashMap<>();
            String[] lines = text.split("\n");
            for (String raw : lines) {
                String ln = raw.trim();
                if (ln.isEmpty()) continue;
                int eq = ln.indexOf('=');
                if (eq <= 0) continue;
                kv.put(ln.substring(0, eq).trim(), ln.substring(eq + 1).trim());
            }
            String name = kv.get("player.name");
            if (name == null) return null;
            Gender gender = Gender.valueOf(kv.getOrDefault("player.gender", "NAM"));
            int suit = parseInt(kv, "player.suitColorIdx", 0);
            int hair = parseInt(kv, "player.hairColorIdx", 0);

            GameState s = new GameState(unesc(name), gender, suit, hair);
            Player p = s.player;
            p.gameClass = GameClass.valueOf(kv.getOrDefault("player.gameClass", "NONE"));
            p.level = parseInt(kv, "player.level", 1);
            p.xp = parseInt(kv, "player.xp", 0);
            p.xuLa = parseInt(kv, "player.xuLa", Player.START_XU_LA);
            p.tinhThe = parseInt(kv, "player.tinhThe", Player.START_TINH_THE);
            p.hp = parseFloat(kv, "player.hp", p.maxHp());
            p.satiety = parseFloat(kv, "player.satiety", 90f);
            p.warmth = parseFloat(kv, "player.warmth", 90f);
            p.battery = parseFloat(kv, "player.battery", 90f);
            p.x = parseFloat(kv, "player.x", 300f);
            p.y = parseFloat(kv, "player.y", 0f);
            p.hairStyleIdx = parseInt(kv, "player.hairStyleIdx", 0);
            p.eyeColorIdx = parseInt(kv, "player.eyeColorIdx", 0);
            p.skinToneIdx = parseInt(kv, "player.skinToneIdx", 0);
            String outfit = kv.get("player.outfitId");
            if (outfit != null) p.outfitId = unesc(outfit);

            p.inventory.clear();
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if (e.getKey().startsWith("inv.")) {
                    String id = e.getKey().substring(4);
                    int count = Integer.parseInt(e.getValue());
                    if (count > 0) p.inventory.put(id, count);
                }
            }

            long now = System.currentTimeMillis() / 1000L;
            for (int i = 0; i < Garden.PLOT_COUNT; i++) {
                if ("1".equals(kv.get("garden." + i + ".planted"))) {
                    String seedId = unesc(kv.getOrDefault("garden." + i + ".seed", ""));
                    float growLeft = parseFloat(kv, "garden." + i + ".growLeft", 0f);
                    s.garden.plantWithGrowLeft(i, seedId, growLeft, now);
                }
            }

            int[] cells = parseCsvInts(kv.get("build.cells"));
            int[] levels = parseCsvInts(kv.get("build.levels"));
            int[] anchors = parseCsvInts(kv.get("build.anchors"));
            if (cells != null && levels != null && anchors != null) {
                s.build.importData(cells, levels, anchors);
            }

            s.quests.mainIndex = parseInt(kv, "quests.mainIndex", 0);
            int maxMain = QuestLog.mainQuests().size();
            if (s.quests.mainIndex < 0) s.quests.mainIndex = 0;
            if (s.quests.mainIndex > maxMain) s.quests.mainIndex = maxMain;
            s.quests.mainProgress = parseInt(kv, "quests.mainProgress", 0);
            int[] dProg = parseCsvInts(kv.get("quests.dailyProgress"));
            int[] dClaim = parseCsvInts(kv.get("quests.dailyClaimed"));
            boolean[] dClaimed = null;
            if (dClaim != null) {
                dClaimed = new boolean[dClaim.length];
                for (int i = 0; i < dClaim.length; i++) dClaimed[i] = dClaim[i] != 0;
            }
            s.quests.restoreDailies(dProg, dClaimed);

            s.storm.timeToNext = parseFloat(kv, "storm.timeToNext", DataStorm.CYCLE_SECONDS);
            s.storm.activeLeft = parseFloat(kv, "storm.activeLeft", 0f);
            s.storm.active = "1".equals(kv.getOrDefault("storm.active", "0"));
            s.storm.kills = parseInt(kv, "storm.kills", 0);

            s.clock.t = parseFloat(kv, "clock.t", WorldClock.START_T);
            s.playSeconds = Long.parseLong(kv.getOrDefault("playSeconds", "0"));
            String notice = kv.get("notice");
            if (notice != null) s.notice = unesc(notice);

            s.settings.uiScale = parseFloat(kv, "settings.uiScale", 1f);
            s.settings.fps60 = !"0".equals(kv.getOrDefault("settings.fps60", "1"));
            s.settings.sound = !"0".equals(kv.getOrDefault("settings.sound", "1"));
            s.settings.music = !"0".equals(kv.getOrDefault("settings.music", "1"));
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- helpers ----

    private static void line(StringBuilder b, String key, String value) {
        b.append(key).append('=').append(value).append('\n');
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("%", "%25").replace("=", "%3D")
                .replace("\n", "%0A").replace("\r", "%0D");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("%0D", "\r").replace("%0A", "\n")
                .replace("%3D", "=").replace("%25", "%");
    }

    private static String csv(int[] arr) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) b.append(',');
            b.append(arr[i]);
        }
        return b.toString();
    }

    private static int[] parseCsvInts(String v) {
        if (v == null || v.isEmpty()) return null;
        String[] parts = v.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
        return out;
    }

    private static int parseInt(Map<String, String> kv, String key, int def) {
        String v = kv.get(key);
        return v == null ? def : Integer.parseInt(v);
    }

    private static float parseFloat(Map<String, String> kv, String key, float def) {
        String v = kv.get(key);
        return v == null ? def : Float.parseFloat(v);
    }
}
