package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * [PORT] Điểm danh + Nhiệm vụ ngày theo bản chính 3.4.4
 * (docs/daily-auction-spec.md). Bản chính dùng nhóm DAILY_GIFT=20
 * trong không gian handler riêng; bản port dùng phong bì −116 vì
 * gói 20 phía fan là START của board game.
 *
 * Sub: DASHBOARD 0 · BAODANH 1 · MILESTONE 2 · GUIDE 3 ·
 * RESULT 4 · Q_SHOW 10 · Q_CHANGE 11 · Q_DONE 12 · Q_BOX 13.
 * Ngày tính theo giờ Việt Nam (Asia/Ho_Chi_Minh).
 * Dữ liệu: bảng daily_state, ghi DB ngay mỗi thao tác.
 */
public class DailyService extends Service {

    public static final byte D_DASHBOARD = 0;
    public static final byte D_BAODANH = 1;
    public static final byte D_MILESTONE = 2;
    public static final byte D_GUIDE = 3;
    public static final byte D_RESULT = 4;
    public static final byte D_QSHOW = 10;
    public static final byte D_QCHANGE = 11;
    public static final byte D_QDONE = 12;
    public static final byte D_QBOX = 13;

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int POINT_PER_DAY = 10;
    static final int[] MILE_POINTS = {50, 120, 220, 350, 500};
    static final int[] BOX_POINTS = {40, 90, 150, 240, 360};
    static final int CHANGE_XU = 5_000;

    /** Nhiệm vụ tĩnh [PORT]: {tên, yêu cầu {pid, qty}..., thưởng {type, a, b}...}
     *  type thưởng: 0 xu (a) · 1 lượng (a) · 2 exp (a) · 3 nông sản (a=pid, b=qty). */
    static final Object[][] QUESTS = {
            {"Thu hoạch đồng quê", new int[][]{{215, 5}, {200, 5}}, new int[][]{{0, 4000, 0}}},
            {"Tiệc rau củ", new int[][]{{203, 8}, {204, 8}}, new int[][]{{0, 5000, 0}, {2, 20, 0}}},
            {"Giỏ trái cây", new int[][]{{249, 6}, {253, 6}}, new int[][]{{0, 6000, 0}}},
            {"Mật ngọt đầu ngày", new int[][]{{288, 3}}, new int[][]{{1, 2, 0}}},
            {"Hoa thơm tặng nàng", new int[][]{{266, 6}, {267, 6}}, new int[][]{{0, 7000, 0}}},
            {"Bữa sáng nông trại", new int[][]{{50, 5}}, new int[][]{{0, 3500, 0}}},
            {"Đặc sản miền quê", new int[][]{{248, 6}, {252, 6}}, new int[][]{{0, 6500, 0}, {2, 30, 0}}},
            {"Kho đầy ắp", new int[][]{{215, 10}}, new int[][]{{1, 1, 0}, {0, 4000, 0}}},
    };

    static boolean schemaReady = false;

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS daily_state ("
                    + "user_id INT PRIMARY KEY, last_day VARCHAR(10), month_key VARCHAR(7),"
                    + "claimed_days TEXT, total_point INT DEFAULT 0, milestones INT DEFAULT 0,"
                    + "quest_idx INT DEFAULT -1, quest_day VARCHAR(10), score INT DEFAULT 0,"
                    + "box_mask INT DEFAULT 0, change_used INT DEFAULT 0)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class State {
        String lastDay = "";
        String monthKey = "";
        Set<Integer> claimed = new HashSet<>();
        int totalPoint;
        int milestones;
        int questIdx = -1;
        String questDay = "";
        int score;
        int boxMask;
        int changeUsed;
    }

    public DailyService(Session cl) {
        super(cl);
        ensureSchema();
    }

    State load(int userId) {
        State st = new State();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_day, month_key, claimed_days, total_point, milestones,"
                     + " quest_idx, quest_day, score, box_mask, change_used"
                     + " FROM daily_state WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                st.lastDay = rs.getString(1) == null ? "" : rs.getString(1);
                st.monthKey = rs.getString(2) == null ? "" : rs.getString(2);
                String cd = rs.getString(3);
                if (cd != null && !cd.isEmpty()) {
                    for (String p : cd.split(",")) {
                        try { st.claimed.add(Integer.parseInt(p.trim())); } catch (Exception ignored) { }
                    }
                }
                st.totalPoint = rs.getInt(4);
                st.milestones = rs.getInt(5);
                st.questIdx = rs.getInt(6);
                st.questDay = rs.getString(7) == null ? "" : rs.getString(7);
                st.score = rs.getInt(8);
                st.boxMask = rs.getInt(9);
                st.changeUsed = rs.getInt(10);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return st;
    }

    void save(int userId, State st) {
        StringBuilder cd = new StringBuilder();
        for (int d : st.claimed) {
            if (cd.length() > 0) cd.append(',');
            cd.append(d);
        }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO daily_state (user_id, last_day, month_key, claimed_days,"
                     + " total_point, milestones, quest_idx, quest_day, score, box_mask, change_used)"
                     + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE last_day = VALUES(last_day),"
                     + " month_key = VALUES(month_key), claimed_days = VALUES(claimed_days),"
                     + " total_point = VALUES(total_point), milestones = VALUES(milestones),"
                     + " quest_idx = VALUES(quest_idx), quest_day = VALUES(quest_day),"
                     + " score = VALUES(score), box_mask = VALUES(box_mask),"
                     + " change_used = VALUES(change_used)")) {
            ps.setInt(1, userId);
            ps.setString(2, st.lastDay);
            ps.setString(3, st.monthKey);
            ps.setString(4, cd.toString());
            ps.setInt(5, st.totalPoint);
            ps.setInt(6, st.milestones);
            ps.setInt(7, st.questIdx);
            ps.setString(8, st.questDay);
            ps.setInt(9, st.score);
            ps.setInt(10, st.boxMask);
            ps.setInt(11, st.changeUsed);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Sang ngày mới: reset phần nhiệm vụ ngày; sang tháng mới: reset ô đã báo danh. */
    void rollDay(State st, LocalDate today) {
        String mk = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
        if (!mk.equals(st.monthKey)) {
            st.monthKey = mk;
            st.claimed.clear();
        }
        String td = today.toString();
        if (!td.equals(st.questDay)) {
            st.questDay = td;
            st.score = 0;
            st.boxMask = 0;
            st.changeUsed = 0;
            st.questIdx = new Random().nextInt(QUESTS.length);
        }
    }

    // ---------- gói ----------

    static Message msg(byte sub) {
        Message m = new Message(Cmd.DAILY);
        try { m.writer().writeByte(sub); } catch (IOException e) { }
        return m;
    }

    void sendMe(Message m) {
        if (session != null) {
            try { session.sendMessage(m); } catch (Exception ignored) { }
        }
    }

    void result(boolean ok, String text) {
        try {
            Message m = msg(D_RESULT);
            DataOutputStream w = m.writer();
            w.writeBoolean(ok);
            w.writeUTF(text == null ? "" : text);
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    public void onMessage(Message mss) {
        User me = session != null ? session.user : null;
        if (me == null) return;
        try {
            int sub = mss.reader().readByte();
            switch (sub) {
                case D_DASHBOARD: sendDashboard(me); break;
                case D_BAODANH: doBaoDanh(me); break;
                case D_MILESTONE: doMilestone(me, mss.reader().readByte()); break;
                case D_GUIDE: result(true, ""); break;
                case D_QSHOW: sendQuest(me); break;
                case D_QCHANGE: doQuestChange(me); break;
                case D_QDONE: doQuestDone(me); break;
                case D_QBOX: doQuestBox(me, mss.reader().readByte()); break;
                default: break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- điểm danh ----------

    void sendDashboard(User me) {
        State st = load(me.getId());
        LocalDate today = LocalDate.now(ZONE);
        rollDay(st, today);
        save(me.getId(), st);
        LocalDateTime now = LocalDateTime.now(ZONE);
        long secs = ChronoUnit.SECONDS.between(now,
                today.plusDays(1).atStartOfDay(ZONE).toLocalDateTime());
        try {
            Message m = msg(D_DASHBOARD);
            DataOutputStream w = m.writer();
            w.writeByte(today.getDayOfMonth());
            w.writeByte(today.lengthOfMonth());
            // dayCount: mỗi ngày báo danh +10 điểm và điểm không bao
            // giờ giảm → tổng số ngày đã báo danh = totalPoint / 10.
            w.writeInt(st.totalPoint / POINT_PER_DAY);
            w.writeInt(st.totalPoint);
            w.writeByte(today.toString().equals(st.lastDay) ? 1 : 0);
            w.writeInt((int) Math.max(0, secs));
            w.writeInt(st.milestones);
            java.util.List<Integer> days = new java.util.ArrayList<>(st.claimed);
            java.util.Collections.sort(days);
            w.writeByte(days.size());
            for (int d : days) w.writeByte(d);
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    /** Quà theo ngày trong tháng [PORT]: trả mô tả + trao quà. */
    String grantDailyGift(User me, int dayOfMonth) {
        if (dayOfMonth == 15) {
            long exp = System.currentTimeMillis() + 7L * 24 * 3600 * 1000;
            me.addItemToChests(new Item(620, exp, 1));
            return "một món đồ (kính) hạn 7 ngày trong rương";
        }
        if (dayOfMonth == 30 || dayOfMonth == 31) {
            me.updateXu(10_000);
            me.updateLuong(5);
            return "10.000 xu + 5 lượng";
        }
        if (dayOfMonth % 7 == 0) {
            me.updateXu(5_000);
            me.updateLuong(2);
            return "5.000 xu + 2 lượng";
        }
        me.updateXu(2_000);
        return "2.000 xu";
    }

    void doBaoDanh(User me) {
        State st = load(me.getId());
        LocalDate today = LocalDate.now(ZONE);
        rollDay(st, today);
        if (today.toString().equals(st.lastDay)) {
            result(false, "Hôm nay bạn đã báo danh rồi — quay lại vào ngày mai nhé.");
            sendDashboard(me);
            return;
        }
        String gift = grantDailyGift(me, today.getDayOfMonth());
        st.lastDay = today.toString();
        st.claimed.add(today.getDayOfMonth());
        st.totalPoint += POINT_PER_DAY;
        save(me.getId(), st);
        // Cap doi [PORT]: diem danh cong +10% tien do cap doi
        MarryService.addCouplePoints(me, 10);
        result(true, "Báo danh thành công! Bạn nhận " + gift
                + " và +" + POINT_PER_DAY + " điểm chuyên cần.");
        sendDashboard(me);
    }

    void doMilestone(User me, int idx) {
        if (idx < 0 || idx >= MILE_POINTS.length) return;
        State st = load(me.getId());
        rollDay(st, LocalDate.now(ZONE));
        if (st.totalPoint < MILE_POINTS[idx]) {
            result(false, "Chưa đủ điểm chuyên cần cho mốc này.");
            return;
        }
        if ((st.milestones & (1 << idx)) != 0) {
            result(false, "Bạn đã nhận quà mốc này rồi.");
            return;
        }
        String gift;
        switch (idx) {
            case 0: me.updateXu(5_000); gift = "5.000 xu"; break;
            case 1: me.updateLuong(5); gift = "5 lượng"; break;
            case 2: me.updateXu(15_000); gift = "15.000 xu"; break;
            case 3: {
                long exp = System.currentTimeMillis() + 15L * 24 * 3600 * 1000;
                me.addItemToChests(new Item(2052, exp, 1));
                gift = "một món đồ hạn 15 ngày trong rương";
                break;
            }
            default: me.updateLuong(20); gift = "20 lượng"; break;
        }
        st.milestones |= (1 << idx);
        save(me.getId(), st);
        result(true, "Đã nhận quà mốc " + MILE_POINTS[idx] + " điểm: " + gift + "!");
        sendDashboard(me);
    }

    // ---------- nhiệm vụ ngày ----------

    void sendQuest(User me) {
        State st = load(me.getId());
        rollDay(st, LocalDate.now(ZONE));
        save(me.getId(), st);
        if (st.questIdx < 0 || st.questIdx >= QUESTS.length) return;
        Object[] q = QUESTS[st.questIdx];
        int[][] reqs = (int[][]) q[1];
        int[][] rews = (int[][]) q[2];
        try {
            Message m = msg(D_QSHOW);
            DataOutputStream w = m.writer();
            w.writeUTF((String) q[0]);
            w.writeByte(reqs.length);
            for (int[] r : reqs) {
                w.writeShort(r[0]);
                w.writeInt(r[1]);
                w.writeInt(Farm2Service.countProduct(me, r[0]));
            }
            w.writeByte(rews.length);
            for (int[] r : rews) {
                w.writeByte(r[0]);
                w.writeInt(r[1]);
                w.writeInt(r[2]);
            }
            w.writeInt(st.score);
            w.writeByte(BOX_POINTS.length);
            for (int i = 0; i < BOX_POINTS.length; i++) {
                w.writeInt(BOX_POINTS[i]);
                w.writeBoolean((st.boxMask & (1 << i)) != 0);
            }
            w.writeByte(st.changeUsed == 0 ? 0 : 1);
            w.writeInt(st.changeUsed == 0 ? 0 : CHANGE_XU);
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    int newQuestIdx(int avoid) {
        Random rnd = new Random();
        int n;
        do { n = rnd.nextInt(QUESTS.length); } while (n == avoid && QUESTS.length > 1);
        return n;
    }

    void doQuestChange(User me) {
        State st = load(me.getId());
        rollDay(st, LocalDate.now(ZONE));
        if (st.changeUsed > 0) {
            if (me.getXu() < CHANGE_XU) {
                result(false, "Đổi nhiệm vụ cần " + CHANGE_XU + " xu.");
                return;
            }
            me.updateXu(-CHANGE_XU);
        }
        st.changeUsed++;
        st.questIdx = newQuestIdx(st.questIdx);
        save(me.getId(), st);
        result(true, st.changeUsed == 1 ? "Đã đổi nhiệm vụ (miễn phí lần đầu hôm nay)."
                : "Đã đổi nhiệm vụ.");
        sendQuest(me);
    }

    void doQuestDone(User me) {
        State st = load(me.getId());
        rollDay(st, LocalDate.now(ZONE));
        if (st.questIdx < 0 || st.questIdx >= QUESTS.length) return;
        Object[] q = QUESTS[st.questIdx];
        int[][] reqs = (int[][]) q[1];
        int[][] rews = (int[][]) q[2];
        for (int[] r : reqs) {
            if (Farm2Service.countProduct(me, r[0]) < r[1]) {
                result(false, "Chưa đủ vật phẩm cho nhiệm vụ này.");
                return;
            }
        }
        for (int[] r : reqs) Farm2Service.takeProduct(me, r[0], r[1]);
        StringBuilder got = new StringBuilder();
        for (int[] r : rews) {
            switch (r[0]) {
                case 0: me.updateXu(r[1]); got.append(r[1]).append(" xu "); break;
                case 1: me.updateLuong(r[1]); got.append(r[1]).append(" lượng "); break;
                case 2: me.updateXP(r[1]); got.append(r[1]).append(" exp "); break;
                case 3: Farm2Service.addNongSan(me, r[1], r[2]); got.append("nông sản "); break;
                default: break;
            }
        }
        int scoreAdd = 20 + 5 * reqs.length;
        st.score += scoreAdd;
        st.questIdx = newQuestIdx(st.questIdx);
        save(me.getId(), st);
        result(true, "Hoàn thành nhiệm vụ! Nhận " + got.toString().trim()
                + " và +" + scoreAdd + " điểm nhiệm vụ.");
        sendQuest(me);
    }

    void doQuestBox(User me, int idx) {
        if (idx < 0 || idx >= BOX_POINTS.length) return;
        State st = load(me.getId());
        rollDay(st, LocalDate.now(ZONE));
        if (st.score < BOX_POINTS[idx]) {
            result(false, "Chưa đủ điểm nhiệm vụ cho hộp này.");
            return;
        }
        if ((st.boxMask & (1 << idx)) != 0) {
            result(false, "Bạn đã mở hộp này rồi.");
            return;
        }
        String gift;
        switch (idx) {
            case 0: me.updateXu(3_000); gift = "3.000 xu"; break;
            case 1: me.updateLuong(3); gift = "3 lượng"; break;
            case 2: me.updateXu(10_000); gift = "10.000 xu"; break;
            case 3: me.updateLuong(10); gift = "10 lượng"; break;
            default: {
                long exp = System.currentTimeMillis() + 15L * 24 * 3600 * 1000;
                me.addItemToChests(new Item(2090, exp, 1));
                gift = "một món đồ hiếm hạn 15 ngày trong rương";
                break;
            }
        }
        st.boxMask |= (1 << idx);
        save(me.getId(), st);
        result(true, "Mở hộp quà mốc " + BOX_POINTS[idx] + " điểm: nhận " + gift + "!");
        sendQuest(me);
    }
}
