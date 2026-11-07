package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DANH HIỆU [PORT theo TITLE của bản chính 3.4.4].
 *
 * Bản chính: nhân vật có idTitle + timeTitle và icon danh hiệu kèm
 * tên trong map (Avatar._iconTitle), panel nhân vật có tab "Danh
 * hiệu đang sở hữu" (T.ownedTitleTab), MapScr có popup chọn danh
 * hiệu (openTitlePopup/listTitle). Bản port dùng số riêng -120 (số -98 của bản chính đã là REQUEST_IMAGE_PART ở bản fan):
 *
 *   sub 0 LIST (C->S, rỗng) -> STATE
 *   sub 1 SET  (C->S) {short titleId} (0 = gỡ danh hiệu)
 *   sub 5 ZONE_REQ (C->S, rỗng) -> ZONE_STATE
 *   STATE (S->C): {byte 0, short equipped, short nDef,
 *       nDef * {short id, UTF tên, byte màu, UTF điều kiện},
 *       short nOwned, nOwned * {short id},
 *       byte nNew, nNew * {short id}} — nNew là danh hiệu vừa được
 *       cấp trong lần quét này (client báo tin vui).
 *   sub 3 WEAR (S->C, phát cả khu): {byte 3, int userId, short titleId}
 *   ZONE_STATE (S->C): {byte 4, short n, n * {int userId, short titleId}}
 *
 * Danh hiệu được CẤP TỰ ĐỘNG khi quét điều kiện (mở panel) từ các
 * hệ đã có: cấp bậc, xu, mini game, câu cá, clan, kết hôn, farm 2,
 * điểm danh, sưu tầm đồ. Sở hữu thường là vĩnh viễn; sự kiện/admin
 * cấp được danh hiệu CÓ HẠN qua grantTimed (cột expires_at, hết hạn
 * tự xoá khi quét) (bảng player_titles);
 * danh hiệu đang đeo lưu ở players.title_id.
 */
public class TitleService extends Service {

    // [PORT-DEFS] 7 danh hiệu x 3 hạng (Đồng/Bạc/Vàng) theo ĐÚNG
    // ảnh gốc Title1..Title21 của bản chính (đã giải mã: mỗi file
    // TextAsset là short id + short len + PNG). Thứ tự id theo file
    // gốc: trong mỗi bộ ba, id đầu = Vàng, giữa = Bạc, cuối = Đồng.
    // Ngưỡng đạt là [PORT] (server nhà phát hành không công bố):
    // đếm bằng bảng title_counters (quiz/fish/harvest/cook) +
    // mg_stats (mini game). Màu: 4 vàng, 7 bạc, 8 đồng.
    // id, tên, màu, điều kiện, bộ đếm, ngưỡng
    public static final Object[][] DEFS = {
        {1, "Nhà Thông Thái (Vàng)", 4, "Trả lời đúng 75 câu đố vui", "quiz", 75},
        {2, "Nhà Thông Thái (Bạc)", 7, "Trả lời đúng 30 câu đố vui", "quiz", 30},
        {3, "Nhà Thông Thái (Đồng)", 8, "Trả lời đúng 10 câu đố vui", "quiz", 10},
        {4, "Thần Nông (Vàng)", 4, "Thu hoạch 300 lần", "harvest", 300},
        {5, "Thần Nông (Bạc)", 7, "Thu hoạch 120 lần", "harvest", 120},
        {6, "Thần Nông (Đồng)", 8, "Thu hoạch 40 lần", "harvest", 40},
        {7, "Thần Câu (Vàng)", 4, "Câu được 200 con cá", "fish", 200},
        {8, "Thần Câu (Bạc)", 7, "Câu được 80 con cá", "fish", 80},
        {9, "Thần Câu (Đồng)", 8, "Câu được 30 con cá", "fish", 30},
        {10, "Cao Thủ 3X (Vàng)", 4, "Thắng 50 ván mini game", "mgwins", 50},
        {11, "Cao Thủ 3X (Bạc)", 7, "Thắng 25 ván mini game", "mgwins", 25},
        {12, "Cao Thủ 3X (Đồng)", 8, "Thắng 10 ván mini game", "mgwins", 10},
        {13, "Vua Đầu Bếp (Vàng)", 4, "Nấu 100 món ăn", "cook", 100},
        {14, "Vua Đầu Bếp (Bạc)", 7, "Nấu 40 món ăn", "cook", 40},
        {15, "Vua Đầu Bếp (Đồng)", 8, "Nấu 15 món ăn", "cook", 15},
        {16, "Nông Dân Chăm Chỉ (Vàng)", 4, "Thu hoạch 150 lần", "harvest", 150},
        {17, "Nông Dân Chăm Chỉ (Bạc)", 7, "Thu hoạch 60 lần", "harvest", 60},
        {18, "Nông Dân Chăm Chỉ (Đồng)", 8, "Thu hoạch 15 lần", "harvest", 15},
        {19, "Ngư Dân Chăm Chỉ (Vàng)", 4, "Câu được 60 con cá", "fish", 60},
        {20, "Ngư Dân Chăm Chỉ (Bạc)", 7, "Câu được 25 con cá", "fish", 25},
        {21, "Ngư Dân Chăm Chỉ (Đồng)", 8, "Câu được 10 con cá", "fish", 10},
    };

    private static volatile boolean schemaReady = false;

    public TitleService(Session session) {
        super(session);
        ensureSchema();
    }

    public static void ensureSchema() {
        if (schemaReady) return;
        synchronized (TitleService.class) {
            if (schemaReady) return;
            try (Connection c = DbManager.getInstance().getConnection()) {
                c.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS player_titles ("
                        + "user_id INT NOT NULL, title_id INT NOT NULL,"
                        + " expires_at BIGINT NOT NULL DEFAULT -1,"
                        + " PRIMARY KEY (user_id, title_id))");
                try {
                    c.createStatement().execute(
                            "ALTER TABLE player_titles ADD COLUMN"
                            + " expires_at BIGINT NOT NULL DEFAULT -1");
                } catch (Exception ignored) {
                    // cột đã tồn tại
                }
                try {
                    c.createStatement().execute(
                            "ALTER TABLE players ADD COLUMN title_id INT DEFAULT 0");
                } catch (Exception ignored) {
                    // cột đã tồn tại
                }
                c.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS title_counters ("
                        + "user_id INT NOT NULL PRIMARY KEY,"
                        + "quiz_ok INT DEFAULT 0, fish INT DEFAULT 0,"
                        + "harvest INT DEFAULT 0, cook INT DEFAULT 0)");
                c.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS title_meta ("
                        + "k VARCHAR(32) NOT NULL PRIMARY KEY, v INT DEFAULT 0)");
                // Migration 1 lần sang bộ định nghĩa 21 danh hiệu:
                // danh hiệu cũ (bộ 12 tự soạn) bị xoá, quét lại từ đầu.
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT v FROM title_meta WHERE k = 'defs_v2'");
                     ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.createStatement().execute("DELETE FROM player_titles");
                        c.createStatement().execute(
                                "UPDATE players SET title_id = 0");
                        c.createStatement().execute(
                                "INSERT INTO title_meta (k, v) VALUES ('defs_v2', 1)");
                    }
                }
                schemaReady = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Cộng 1 vào bộ đếm thành tích (cột trong whitelist). Lỗi ở
     *  đây KHÔNG được làm hỏng luồng chơi chính — nuốt hết. */
    public static void bump(int userId, String column) {
        if (!column.equals("quiz_ok") && !column.equals("fish")
                && !column.equals("harvest") && !column.equals("cook")) return;
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO title_counters (user_id, " + column + ")"
                     + " VALUES (?, 1) ON DUPLICATE KEY UPDATE "
                     + column + " = " + column + " + 1")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /** Đọc 4 bộ đếm + tổng thắng mini game của 1 người chơi. */
    private static java.util.Map<String, Integer> counters(User u) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        m.put("quiz", 0); m.put("fish", 0); m.put("harvest", 0);
        m.put("cook", 0); m.put("mgwins", 0);
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT quiz_ok, fish, harvest, cook FROM title_counters"
                    + " WHERE user_id = ?")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    m.put("quiz", rs.getInt(1));
                    m.put("fish", rs.getInt(2));
                    m.put("harvest", rs.getInt(3));
                    m.put("cook", rs.getInt(4));
                }
            } catch (Exception ignored) { }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(wins),0) FROM mg_stats WHERE user_id = ?")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) m.put("mgwins", rs.getInt(1));
            } catch (Exception ignored) { }
        } catch (Exception ignored) {
        }
        return m;
    }

    /** Nạp danh hiệu đang đeo từ DB vào RAM (an toàn khi chưa có cột).
     *  Danh hiệu có hạn đã hết hạn thì tự gỡ (cập nhật cả DB). */
    public static void loadEquipped(User u) {
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT title_id FROM players WHERE user_id = ?")) {
            ps.setInt(1, u.getId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) u.titleId = rs.getInt(1);
        } catch (Exception ignored) {
            u.titleId = 0;
        }
        if (u.titleId > 0 && !ownedMap(u).containsKey(u.titleId)) {
            u.titleId = 0;
            try (Connection c = DbManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE players SET title_id = 0 WHERE user_id = ?")) {
                ps.setInt(1, u.getId());
                ps.executeUpdate();
            } catch (Exception ignored) {
            }
        }
    }

    /** Phát danh hiệu của người vừa vào khu cho cả khu (sub 3).
     *  Bỏ qua Boss của bản fan: boss dùng session nội bộ mô phỏng,
     *  chèn thao tác DB/phát gói vào Zone.enter làm boss bị ngắt
     *  giữa chừng và kéo sập luồng khởi động server (bắt gặp khi
     *  test sống 2026-11-05). */
    public static void announceEnter(User u) {
        if (u instanceof avatar.model.Boss) return;
        try {
            loadEquipped(u);
            if (u.getMapService() != null) {
                u.getMapService().titlePart(u.getId(), (short) u.titleId);
            }
        } catch (Exception ignored) {
        }
    }

    public void onMessage(Message ms) throws IOException {
        User u = this.session.user;
        if (u == null) return;
        byte sub = ms.reader().readByte();
        switch (sub) {
            case 0: {
                List<Integer> fresh = grantCheck(u);
                sendState(u, fresh);
                break;
            }
            case 1: {
                short tid = ms.reader().readShort();
                if (tid == 0 || owned(u).contains((int) tid)) {
                    u.titleId = tid;
                    try (Connection c = DbManager.getInstance().getConnection();
                         PreparedStatement ps = c.prepareStatement(
                                 "UPDATE players SET title_id = ? WHERE user_id = ?")) {
                        ps.setInt(1, tid);
                        ps.setInt(2, u.getId());
                        ps.executeUpdate();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (u.getMapService() != null) {
                        u.getMapService().titlePart(u.getId(), tid);
                    }
                    sendState(u, new ArrayList<Integer>());
                } else {
                    u.getAvatarService().serverDialog("Bạn chưa sở hữu danh hiệu này.");
                }
                break;
            }
            case 5: {
                sendZoneState(u);
                break;
            }
            default: break;
        }
    }

    private Set<Integer> owned(User u) {
        return ownedMap(u).keySet();
    }

    /** Danh hiệu đang sở hữu kèm hạn dùng (epoch ms; -1 = vĩnh viễn).
     *  Bản ghi đã hết hạn bị xoá luôn khỏi DB khi quét qua. */
    public static java.util.Map<Integer, Long> ownedMap(User u) {
        java.util.Map<Integer, Long> out = new java.util.LinkedHashMap<>();
        long now = System.currentTimeMillis();
        List<Integer> expired = new ArrayList<>();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT title_id, expires_at FROM player_titles"
                     + " WHERE user_id = ?")) {
            ps.setInt(1, u.getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int tid = rs.getInt(1);
                long exp = rs.getLong(2);
                if (exp >= 0 && exp <= now) {
                    expired.add(tid);
                } else {
                    out.put(tid, exp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int tid : expired) {
            try (Connection c = DbManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM player_titles"
                         + " WHERE user_id = ? AND title_id = ?")) {
                ps.setInt(1, u.getId());
                ps.setInt(2, tid);
                ps.executeUpdate();
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Cấp danh hiệu CÓ HẠN (sự kiện/admin): hạn = now + days ngày.
     *  Đang có vĩnh viễn thì giữ vĩnh viễn; đang có hạn thì lấy hạn
     *  xa hơn. Trả false nếu titleId không tồn tại. */
    public static boolean grantTimed(int userId, int titleId, int days) {
        ensureSchema();
        boolean known = false;
        for (Object[] d : DEFS) if ((Integer) d[0] == titleId) known = true;
        if (!known || days <= 0) return false;
        long exp = System.currentTimeMillis() + days * 86400000L;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_titles (user_id, title_id, expires_at)"
                     + " VALUES (?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE expires_at ="
                     + " IF(expires_at = -1, -1,"
                     + " GREATEST(expires_at, VALUES(expires_at)))")) {
            ps.setInt(1, userId);
            ps.setInt(2, titleId);
            ps.setLong(3, exp);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Đẩy lại trạng thái danh hiệu cho người chơi (admin cấp xong gọi). */
    public void pushState(User u) throws IOException {
        sendState(u, new ArrayList<Integer>());
    }

    /** Quét điều kiện theo bộ đếm, cấp danh hiệu mới đạt; trả
     *  danh sách vừa cấp. */
    private List<Integer> grantCheck(User u) {
        Set<Integer> have = owned(u);
        List<Integer> fresh = new ArrayList<>();
        java.util.Map<String, Integer> cnt = counters(u);
        for (Object[] d : DEFS) {
            int tid = (Integer) d[0];
            String counter = (String) d[4];
            int need = (Integer) d[5];
            if (have.contains(tid)) continue;
            Integer cur = cnt.get(counter);
            if (cur == null || cur < need) continue;
            try (Connection c = DbManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT IGNORE INTO player_titles (user_id, title_id)"
                         + " VALUES (?, ?)")) {
                ps.setInt(1, u.getId());
                ps.setInt(2, tid);
                ps.executeUpdate();
                fresh.add(tid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fresh;
    }

    private void sendState(User u, List<Integer> fresh) throws IOException {
        Message ms = new Message(Cmd.TITLE);
        DataOutputStream ds = ms.writer();
        ds.writeByte(0);
        ds.writeShort(u.titleId);
        ds.writeShort(DEFS.length);
        for (Object[] d : DEFS) {
            ds.writeShort((Integer) d[0]);
            ds.writeUTF((String) d[1]);
            ds.writeByte((Integer) d[2]);
            ds.writeUTF((String) d[3]);
        }
        java.util.Map<Integer, Long> own = ownedMap(u);
        ds.writeShort(own.size());
        for (int tid : own.keySet()) ds.writeShort(tid);
        ds.writeByte(fresh.size());
        for (int tid : fresh) ds.writeShort(tid);
        // Đuôi mở rộng [PORT]: hạn dùng của từng danh hiệu đang có
        // (cùng thứ tự với danh sách sở hữu ở trên; -1 = vĩnh viễn).
        ds.writeShort(own.size());
        for (java.util.Map.Entry<Integer, Long> e : own.entrySet()) {
            ds.writeShort(e.getKey());
            ds.writeLong(e.getValue());
        }
        ds.flush();
        this.session.sendMessage(ms);
    }

    private void sendZoneState(User u) throws IOException {
        Message ms = new Message(Cmd.TITLE);
        DataOutputStream ds = ms.writer();
        ds.writeByte(4);
        List<User> players = u.getZone() == null
                ? new ArrayList<User>() : u.getZone().getPlayers();
        int n = 0;
        for (User pl : players) if (!(pl instanceof avatar.model.Npc)) n++;
        ds.writeShort(n);
        for (User pl : players) {
            if (pl instanceof avatar.model.Npc) continue;
            if (pl.titleId == 0 && pl.getId() != u.getId()) loadEquipped(pl);
            ds.writeInt(pl.getId());
            ds.writeShort(pl.titleId);
        }
        ds.flush();
        this.session.sendMessage(ms);
    }
}
