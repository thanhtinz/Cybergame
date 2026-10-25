package com.neolang.cyber.model;

/**
 * Bão Dữ Liệu: sự kiện định kỳ. Đếm ngược tới cơn bão kế tiếp, bão kéo dài
 * 90 giây, trong bão diệt đủ số quái yêu cầu để nhận thưởng khi bão qua.
 */
public class DataStorm {
    /** Thời gian chờ tới cơn bão đầu tiên / giữa hai cơn bão, giây. */
    public static final float CYCLE_SECONDS = 300f;
    /** Thời lượng một cơn bão, giây. */
    public static final float DURATION_SECONDS = 90f;
    /** Số quái cần diệt trong bão. */
    public static final int DEFAULT_OBJECTIVE_KILLS = 6;
    /** Xu Lá thưởng khi hoàn thành mục tiêu bão. */
    public static final int REWARD_XU = 150;
    /** Vật phẩm thưởng khi hoàn thành mục tiêu bão. */
    public static final String REWARD_ITEM = "nhua_sang";
    public static final int REWARD_ITEM_COUNT = 2;

    public float timeToNext = CYCLE_SECONDS;
    public float activeLeft = 0f;
    public int objectiveKills = DEFAULT_OBJECTIVE_KILLS;
    public int kills = 0;
    public boolean active = false;

    public void tick(float dt) {
        if (dt <= 0f) return;
        if (active) {
            activeLeft -= dt;
            if (activeLeft <= 0f) {
                active = false;
                activeLeft = 0f;
                timeToNext = CYCLE_SECONDS;
                // Giữ nguyên kills để người chơi vẫn nhận thưởng được nếu đã đủ mục tiêu.
            }
        } else {
            timeToNext -= dt;
            if (timeToNext <= 0f) {
                active = true;
                activeLeft = DURATION_SECONDS;
                timeToNext = 0f;
                kills = 0;
            }
        }
    }

    /** Ghi nhận một quái bị diệt; chỉ tính khi bão đang hoạt động. */
    public void onKill() {
        if (active) kills++;
    }

    public boolean objectiveDone() {
        return kills >= objectiveKills;
    }

    /**
     * Nhận thưởng bão (Xu Lá + Nhựa Sáng) nếu đã đủ mục tiêu, rồi đặt lại
     * chu kỳ. Chưa đủ mục tiêu thì không làm gì.
     * Lưu ý: sự kiện STORM cho nhiệm vụ do tầng UI/game bắn vào QuestLog.
     */
    public void claimReward(Player p) {
        if (p == null || !objectiveDone()) return;
        p.xuLa += REWARD_XU;
        p.addItem(REWARD_ITEM, REWARD_ITEM_COUNT);
        kills = 0;
        active = false;
        activeLeft = 0f;
        timeToNext = CYCLE_SECONDS;
    }
}
