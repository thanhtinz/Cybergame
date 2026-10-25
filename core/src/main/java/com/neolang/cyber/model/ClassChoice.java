package com.neolang.cyber.model;

/**
 * Luật Trạm Định Hướng: nơi duy nhất được gán/đổi nghề cho người chơi.
 *
 * Quy tắc:
 * - Chọn nghề lần đầu: cấp >= 5 VÀ đã hoàn thành nhiệm vụ giới thiệu
 *   "q0_chao_lang" (xem QuestLog.introCompleted).
 * - Đổi nghề: miễn phí tại trạm, nhưng chỉ từ cấp 10 trở đi. Đã chọn nghề
 *   mà chưa tới cấp 10 thì không đổi được (TOO_EARLY_CHANGE).
 * - Khi chọn thành công, sự kiện CHOOSE_CLASS được bắn vào QuestLog
 *   để nhiệm vụ q4_chon_nghe tiến triển.
 */
public final class ClassChoice {
    /** Cấp tối thiểu để chọn nghề lần đầu. */
    public static final int MIN_LEVEL_FIRST_CHOICE = 5;
    /** Cấp tối thiểu để đổi nghề. */
    public static final int MIN_LEVEL_CHANGE = 10;

    public enum Result {
        OK,
        LOCKED_LEVEL,
        LOCKED_INTRO,
        TOO_EARLY_CHANGE,
        INVALID
    }

    private ClassChoice() {
    }

    /** Có đủ điều kiện chọn nghề lần đầu hay chưa (chưa xét việc đổi nghề). */
    public static boolean canChoose(GameState s) {
        if (s == null || s.player == null || s.quests == null) return false;
        return s.player.level >= MIN_LEVEL_FIRST_CHOICE && s.quests.introCompleted();
    }

    /** Có được đổi nghề ở thời điểm hiện tại hay không. */
    public static boolean canChangeClass(GameState s) {
        if (s == null || s.player == null) return false;
        if (s.player.gameClass == null || s.player.gameClass == GameClass.NONE) {
            return canChoose(s);
        }
        return s.player.level >= MIN_LEVEL_CHANGE;
    }

    /**
     * Thử chọn (hoặc đổi) nghề cho người chơi tại Trạm Định Hướng.
     *
     * @return OK nếu đã gán nghề; LOCKED_LEVEL nếu chưa đủ cấp (lần đầu);
     * LOCKED_INTRO nếu chưa xong nhiệm vụ giới thiệu;
     * TOO_EARLY_CHANGE nếu đã có nghề mà chưa tới cấp 10 để đổi;
     * INVALID nếu nghề đích null/NONE hoặc state không hợp lệ.
     * Khi không OK, nghề của người chơi không thay đổi.
     */
    public static Result tryChoose(GameState s, GameClass target) {
        if (s == null || s.player == null || target == null || target == GameClass.NONE) {
            return Result.INVALID;
        }
        Player p = s.player;
        boolean hasClass = p.gameClass != null && p.gameClass != GameClass.NONE;
        if (hasClass) {
            if (p.level < MIN_LEVEL_CHANGE) return Result.TOO_EARLY_CHANGE;
        } else {
            if (p.level < MIN_LEVEL_FIRST_CHOICE) return Result.LOCKED_LEVEL;
            if (s.quests == null || !s.quests.introCompleted()) return Result.LOCKED_INTRO;
        }
        int oldMax = p.maxHp();
        boolean wasFull = p.hp >= oldMax;
        p.gameClass = target;
        int newMax = p.maxHp();
        p.hp = wasFull ? newMax : Math.min(newMax, Math.max(1f, p.hp));
        if (s.quests != null) {
            s.quests.onEvent(ObjectiveType.CHOOSE_CLASS, 1);
        }
        return Result.OK;
    }
}
