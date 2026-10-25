package com.neolang.cyber.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nhật ký nhiệm vụ: chuỗi chính tuyến tính + 3 nhiệm vụ hằng ngày lặp lại.
 *
 * Nhiệm vụ giới thiệu là nhiệm vụ đầu chuỗi chính, "q0_chao_lang". Hoàn thành
 * mục tiêu của nó (kể cả trước khi nhận thưởng) cùng cấp >= 5 sẽ mở khóa
 * Trạm Định Hướng chọn nghề — xem {@link ClassChoice} và {@link #introCompleted()}.
 */
public class QuestLog {
    /** Id nhiệm vụ giới thiệu. */
    public static final String INTRO_QUEST_ID = "q0_chao_lang";

    private static final List<QuestDef> MAIN = new ArrayList<>();
    private static final List<QuestDef> DAILY = new ArrayList<>();

    static {
        MAIN.add(new QuestDef("q0_chao_lang", "Chào Neo Làng",
                "Gặp Trưởng Trạm để nhận nhà pod và nghe giới thiệu về làng.",
                ObjectiveType.TALK, 1, 20, 50, "banh_la", 1));
        MAIN.add(new QuestDef("q1_trong_cay", "Mầm Xanh Đầu Tiên",
                "Trồng 2 cây trong vườn thủy canh của pod.",
                ObjectiveType.PLANT, 2, 40, 60, null, 0));
        MAIN.add(new QuestDef("q2_thu_hoach", "Vụ Thu Hoạch Đầu",
                "Thu hoạch 2 nông sản đã chín.",
                ObjectiveType.HARVEST, 2, 50, 80, "seed_dau", 1));
        MAIN.add(new QuestDef("q3_diet_glitch", "Dọn Sạch Glitch",
                "Tiêu diệt 5 quái Glitch ở khu phiêu lưu.",
                ObjectiveType.KILL, 5, 80, 120, "linh_kien", 1));
        MAIN.add(new QuestDef("q4_chon_nghe", "Định Hướng Tương Lai",
                "Tới Trạm Định Hướng và chọn một nghề cho mình.",
                ObjectiveType.CHOOSE_CLASS, 1, 100, 100, null, 0));
        MAIN.add(new QuestDef("q5_xay_pod", "Tổ Ấm Riêng",
                "Xây 2 module trong pod của bạn.",
                ObjectiveType.BUILD, 2, 120, 150, null, 0));
        MAIN.add(new QuestDef("q6_an_ngon", "Bữa Ăn Ngon",
                "Ăn 1 món ăn để hồi phục.",
                ObjectiveType.EAT, 1, 60, 80, "com_nam", 1));
        MAIN.add(new QuestDef("q7_bao_du_lieu", "Qua Cơn Bão Dữ Liệu",
                "Hoàn thành 1 đợt Bão Dữ Liệu.",
                ObjectiveType.STORM, 1, 150, 200, "nhua_sang", 2));
        MAIN.add(new QuestDef("q8_len_cap", "Cư Dân Kỳ Cựu",
                "Đạt cấp 8.",
                ObjectiveType.REACH_LEVEL, 8, 200, 250, "hat_giong_hiem", 1));

        DAILY.add(new QuestDef("d1_san_glitch", "Săn Glitch Hằng Ngày",
                "Tiêu diệt 8 quái Glitch.", ObjectiveType.KILL, 8, 60, 100, null, 0));
        DAILY.add(new QuestDef("d2_thu_hoach_ngay", "Thu Hoạch Hằng Ngày",
                "Thu hoạch 3 nông sản.", ObjectiveType.HARVEST, 3, 50, 80, null, 0));
        DAILY.add(new QuestDef("d3_xay_dung_ngay", "Xây Dựng Hằng Ngày",
                "Xây hoặc nâng cấp 1 module.", ObjectiveType.BUILD, 1, 50, 90, null, 0));
    }

    public static List<QuestDef> mainQuests() {
        return Collections.unmodifiableList(MAIN);
    }

    public static List<QuestDef> dailyQuests() {
        return Collections.unmodifiableList(DAILY);
    }

    /** Thứ tự của nhiệm vụ chính hiện tại trong chuỗi (0-based). */
    public int mainIndex = 0;
    /** Tiến độ của nhiệm vụ chính hiện tại. */
    public int mainProgress = 0;

    private final int[] dailyProg = new int[DAILY.size()];
    private final boolean[] dailyClaimedFlags = new boolean[DAILY.size()];

    /** Nhiệm vụ chính hiện tại, null nếu đã xong toàn bộ chuỗi. */
    public QuestDef currentMain() {
        if (mainIndex < 0 || mainIndex >= MAIN.size()) return null;
        return MAIN.get(mainIndex);
    }

    public boolean currentComplete() {
        QuestDef q = currentMain();
        return q != null && mainProgress >= q.target;
    }

    /**
     * Đã hoàn thành mục tiêu nhiệm vụ giới thiệu hay chưa. Đúng khi nhiệm vụ
     * đầu đã xong mục tiêu (dù chưa nhận thưởng) hoặc đã qua nó.
     */
    public boolean introCompleted() {
        if (mainIndex > 0) return true;
        QuestDef q = currentMain();
        return q != null && INTRO_QUEST_ID.equals(q.id) && mainProgress >= q.target;
    }

    /**
     * Ghi nhận một sự kiện gameplay. Tự so với nhiệm vụ chính hiện tại và
     * mọi nhiệm vụ hằng ngày chưa nhận thưởng. Với REACH_LEVEL, amount là
     * cấp hiện tại (lấy max) thay vì cộng dồn.
     *
     * @return true nếu có ít nhất một tiến độ thay đổi
     */
    public boolean onEvent(ObjectiveType type, int amount) {
        if (type == null || amount <= 0) return false;
        boolean changed = false;

        QuestDef q = currentMain();
        if (q != null && q.type == type && mainProgress < q.target) {
            int before = mainProgress;
            mainProgress = advance(mainProgress, q.target, type, amount);
            if (mainProgress != before) changed = true;
        }
        for (int i = 0; i < DAILY.size(); i++) {
            QuestDef d = DAILY.get(i);
            if (d.type == type && !dailyClaimedFlags[i] && dailyProg[i] < d.target) {
                int before = dailyProg[i];
                dailyProg[i] = advance(dailyProg[i], d.target, type, amount);
                if (dailyProg[i] != before) changed = true;
            }
        }
        return changed;
    }

    private static int advance(int progress, int target, ObjectiveType type, int amount) {
        int next = type == ObjectiveType.REACH_LEVEL
                ? Math.max(progress, amount)
                : progress + amount;
        return Math.min(target, next);
    }

    /**
     * Nhận thưởng nhiệm vụ chính hiện tại và chuyển sang nhiệm vụ kế.
     * Không làm gì nếu nhiệm vụ chưa hoàn thành.
     */
    public void claimMain(Player p) {
        if (p == null || !currentComplete()) return;
        QuestDef q = currentMain();
        giveReward(p, q);
        mainIndex++;
        mainProgress = 0;
    }

    public List<QuestDef> dailies() {
        return dailyQuests();
    }

    public int dailyProgress(int i) {
        return (i >= 0 && i < dailyProg.length) ? dailyProg[i] : 0;
    }

    /** True khi nhiệm vụ ngày thứ i đã đủ tiến độ (dù đã nhận thưởng hay chưa). */
    public boolean dailyDone(int i) {
        if (i < 0 || i >= DAILY.size()) return false;
        return dailyProg[i] >= DAILY.get(i).target;
    }

    /** True khi nhiệm vụ ngày thứ i đã nhận thưởng rồi. */
    public boolean dailyClaimed(int i) {
        return i >= 0 && i < dailyClaimedFlags.length && dailyClaimedFlags[i];
    }

    /** Nhận thưởng nhiệm vụ ngày thứ i. Chỉ nhận được một lần trước khi reset. */
    public void claimDaily(Player p, int i) {
        if (p == null || i < 0 || i >= DAILY.size()) return;
        if (!dailyDone(i) || dailyClaimedFlags[i]) return;
        giveReward(p, DAILY.get(i));
        dailyClaimedFlags[i] = true;
    }

    /** Đặt lại toàn bộ nhiệm vụ hằng ngày (tiến độ + đã nhận thưởng). */
    public void resetDailies() {
        for (int i = 0; i < dailyProg.length; i++) {
            dailyProg[i] = 0;
            dailyClaimedFlags[i] = false;
        }
    }

    private static void giveReward(Player p, QuestDef q) {
        if (q.rewardXu > 0) p.xuLa += q.rewardXu;
        if (q.rewardItem != null && q.rewardItemCount > 0) p.addItem(q.rewardItem, q.rewardItemCount);
        if (q.rewardXp > 0) p.gainXp(q.rewardXp);
    }

    // ---- Hỗ trợ SaveData ----

    public int[] dailyProgressArray() {
        return dailyProg.clone();
    }

    public boolean[] dailyClaimedArray() {
        return dailyClaimedFlags.clone();
    }

    public void restoreDailies(int[] prog, boolean[] claimed) {
        if (prog != null) {
            for (int i = 0; i < dailyProg.length && i < prog.length; i++) dailyProg[i] = prog[i];
        }
        if (claimed != null) {
            for (int i = 0; i < dailyClaimedFlags.length && i < claimed.length; i++) {
                dailyClaimedFlags[i] = claimed[i];
            }
        }
    }
}
