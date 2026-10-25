package com.neolang.cyber.model;

/**
 * Đồng hồ thế giới: một ngày game dài 480 giây.
 * phase() chạy 0..1, 0 = bình minh. Ban ngày là phase 0.05–0.70;
 * ngoài khoảng đó là ban đêm (isNight).
 */
public class WorldClock {
    public static final float DAY_LENGTH = 480f;
    /** Phase bắt đầu ban ngày. */
    public static final float DAY_PHASE_START = 0.05f;
    /** Phase kết thúc ban ngày. */
    public static final float DAY_PHASE_END = 0.70f;
    /** Thời điểm bắt đầu mặc định: buổi sáng (phase 0.25). */
    public static final float START_T = DAY_LENGTH * 0.25f;

    /** Thời gian trong ngày, giây, 0..DAY_LENGTH. */
    public float t = START_T;

    public void tick(float dt) {
        if (dt <= 0f) return;
        t += dt;
        while (t >= DAY_LENGTH) t -= DAY_LENGTH;
        while (t < 0f) t += DAY_LENGTH;
    }

    /** Pha trong ngày 0..1, 0 = bình minh. */
    public float phase() {
        float p = t / DAY_LENGTH;
        if (p < 0f) return 0f;
        if (p >= 1f) return 0.999f;
        return p;
    }

    /** Đêm khi phase ngoài khoảng ngày 0.05–0.70. */
    public boolean isNight() {
        float p = phase();
        return p < DAY_PHASE_START || p >= DAY_PHASE_END;
    }
}
