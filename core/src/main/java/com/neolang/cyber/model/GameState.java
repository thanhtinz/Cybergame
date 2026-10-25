package com.neolang.cyber.model;

/** Toàn bộ trạng thái một phiên chơi / một file save. */
public class GameState {
    public static class Settings {
        public float uiScale = 1f;
        public boolean fps60 = true;
        public boolean sound = true;
        public boolean music = true;
    }

    public Player player;
    public Garden garden;
    public BuildPlot build;
    public QuestLog quests;
    public DataStorm storm;
    public WorldClock clock;
    public Settings settings = new Settings();
    /** Tổng thời gian đã chơi, giây. */
    public long playSeconds = 0;
    /** Thông báo gần nhất hiển thị cho người chơi. */
    public String notice = "";

    public GameState(String name, Gender gender, int suitColorIdx, int hairColorIdx) {
        player = new Player(name, gender);
        player.suitColorIdx = suitColorIdx;
        player.hairColorIdx = hairColorIdx;
        player.outfitId = "bo_tan_cu_dan";
        // Đồ khởi đầu cho cư dân mới.
        player.addItem("banh_la", 2);
        player.addItem("seed_ca_chua", 1);
        player.addItem("seed_nam", 1);

        garden = new Garden();
        build = new BuildPlot();
        quests = new QuestLog();
        storm = new DataStorm();
        clock = new WorldClock();
        notice = "Chào mừng bạn đến với Neo Làng: Cyber Grove.";
    }
}
