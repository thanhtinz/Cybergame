package com.neolang.cyber.model;

/**
 * Hệ sinh tồn nhẹ: độ no, độ ấm, pin. Mọi chỉ số nằm trong khoảng 0..100;
 * bất kỳ chỉ số nào (hoặc HP) về 0 là ngất.
 */
public final class Survival {
    /** Thời gian (giây chơi) để độ no tụt từ 100 về 0 ở chế độ bình thường. */
    public static final float SATIETY_FULL_DRAIN_SECONDS = 900f;
    /** Hệ số hao độ no nhanh hơn khi đang phiêu lưu. */
    public static final float ADVENTURE_SATIETY_MULT = 1.6f;
    /** Độ ấm tụt mỗi giây khi trời đêm mà không có nguồn nhiệt/không ở nhà. */
    public static final float WARMTH_DRAIN_NIGHT_PER_SEC = 100f / 600f;
    /** Hệ số hao độ ấm thêm khi có bão dữ liệu. */
    public static final float WARMTH_DRAIN_STORM_MULT = 1.5f;
    /** Pin tụt mỗi giây khi đang phiêu lưu. */
    public static final float BATTERY_DRAIN_ADVENTURE_PER_SEC = 100f / 1200f;
    /** Độ ấm hồi mỗi giây khi gần nguồn nhiệt. */
    public static final float WARMTH_REGEN_HEAT_PER_SEC = 10f;
    /** Độ ấm hồi mỗi giây khi ở nhà. */
    public static final float WARMTH_REGEN_HOME_PER_SEC = 5f;
    /** Pin hồi mỗi giây khi ở nhà (có trạm sạc). */
    public static final float BATTERY_REGEN_HOME_PER_SEC = 6f;

    private Survival() {
    }

    public static void tick(Player p, float dtSec, boolean inAdventure, boolean night,
                            boolean storm, boolean nearHeat, boolean atHome) {
        if (p == null || dtSec <= 0f) return;

        float satietyRate = 100f / SATIETY_FULL_DRAIN_SECONDS;
        if (inAdventure) satietyRate *= ADVENTURE_SATIETY_MULT;
        p.satiety -= satietyRate * dtSec;

        if (nearHeat) {
            p.warmth += WARMTH_REGEN_HEAT_PER_SEC * dtSec;
        } else if (atHome) {
            p.warmth += WARMTH_REGEN_HOME_PER_SEC * dtSec;
        } else if (night || storm) {
            float drain = 0f;
            if (night) drain += WARMTH_DRAIN_NIGHT_PER_SEC;
            if (storm) drain += WARMTH_DRAIN_NIGHT_PER_SEC * WARMTH_DRAIN_STORM_MULT;
            p.warmth -= drain * dtSec;
        }

        if (atHome) {
            p.battery += BATTERY_REGEN_HOME_PER_SEC * dtSec;
        } else if (inAdventure) {
            p.battery -= BATTERY_DRAIN_ADVENTURE_PER_SEC * dtSec;
        }

        p.satiety = Player.clampStat(p.satiety);
        p.warmth = Player.clampStat(p.warmth);
        p.battery = Player.clampStat(p.battery);
    }

    /** Ngất khi HP hoặc bất kỳ chỉ số sinh tồn nào về 0. */
    public static boolean isFainted(Player p) {
        if (p == null) return true;
        return p.hp <= 0f || p.satiety <= 0f || p.warmth <= 0f || p.battery <= 0f;
    }

    /** Dùng một vật phẩm: cộng các chỉ số theo định nghĩa item, kẹp 0..100. */
    public static void applyItem(Player p, ItemDef it) {
        if (p == null || it == null) return;
        p.satiety = Player.clampStat(p.satiety + it.foodSatiety);
        p.warmth = Player.clampStat(p.warmth + it.foodWarmth);
        p.battery = Player.clampStat(p.battery + it.batteryCharge);
    }
}
