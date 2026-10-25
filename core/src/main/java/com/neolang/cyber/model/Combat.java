package com.neolang.cyber.model;

import java.util.Random;

/** Công thức chiến đấu thuần, dùng chung cho người chơi và quái. */
public final class Combat {
    /** Tỉ lệ chí mạng. */
    public static float critChance = 0.1f;
    /** Hệ số sát thương khi chí mạng. */
    public static float critMult = 1.6f;
    /** Dao động sát thương thấp nhất. */
    public static final float VARIANCE_MIN = 0.9f;
    /** Dao động sát thương cao nhất. */
    public static final float VARIANCE_MAX = 1.1f;
    /** Tỉ lệ DEF giảm sát thương nhận vào. */
    public static final float DEF_FACTOR = 0.4f;

    private Combat() {
    }

    /**
     * Tính sát thương: (atk * mult - def * 0.4), dao động ngẫu nhiên 0.9–1.1,
     * chí mạng 10% nhân 1.6. Kết quả luôn >= 1.
     */
    public static int damage(int atk, float mult, int def, Random r) {
        Random rnd = r == null ? new Random() : r;
        float raw = atk * mult - def * DEF_FACTOR;
        float variance = VARIANCE_MIN + rnd.nextFloat() * (VARIANCE_MAX - VARIANCE_MIN);
        raw *= variance;
        if (rnd.nextFloat() < critChance) {
            raw *= critMult;
        }
        int dmg = Math.round(raw);
        return Math.max(1, dmg);
    }
}
