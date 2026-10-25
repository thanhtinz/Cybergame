package com.neolang.cyber.model;

/** Định nghĩa quái; chỉ số scale khoảng +12% mỗi cấp. */
public enum MonsterDef {
    GLITCH_BLOB("Blob Glitch", 40, 8, 2, 25, 12, 0xB388FF),
    MUSH_BUG("Bọ Nấm Neon", 55, 10, 3, 35, 16, 0x69F0AE),
    BEETLE_BOT("Bọ Máy Beetle-Bot", 80, 12, 6, 50, 24, 0x40C4FF),
    DATA_WRAITH("Bóng Ma Dữ Liệu", 110, 16, 5, 75, 36, 0xFF4081);

    /** Tỉ lệ tăng chỉ số mỗi cấp trên cấp 1. */
    public static final float SCALE_PER_LEVEL = 0.12f;

    public final String displayName;
    public final int baseHp;
    public final int baseAtk;
    public final int baseDef;
    public final int baseXp;
    public final int baseXu;
    public final int color;

    MonsterDef(String displayName, int baseHp, int baseAtk, int baseDef,
               int baseXp, int baseXu, int color) {
        this.displayName = displayName;
        this.baseHp = baseHp;
        this.baseAtk = baseAtk;
        this.baseDef = baseDef;
        this.baseXp = baseXp;
        this.baseXu = baseXu;
        this.color = color;
    }

    private static int scaled(int base, int level) {
        int lv = Math.max(1, level);
        return Math.round(base * (1f + SCALE_PER_LEVEL * (lv - 1)));
    }

    public int hpForLevel(int level) {
        return scaled(baseHp, level);
    }

    public int atkForLevel(int level) {
        return scaled(baseAtk, level);
    }

    public int defForLevel(int level) {
        return scaled(baseDef, level);
    }

    public int xpForLevel(int level) {
        return scaled(baseXp, level);
    }

    public int xuForLevel(int level) {
        return scaled(baseXu, level);
    }
}
