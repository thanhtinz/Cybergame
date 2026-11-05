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
 * điểm danh, sưu tầm đồ. Sở hữu là vĩnh viễn (bảng player_titles);
 * danh hiệu đang đeo lưu ở players.title_id.
 */
public class TitleService extends Service {

    // id, tên, màu (0 trắng 1 xanh lá 2 xanh dương 3 tím 4 vàng 5 cam 6 hồng), điều kiện
    public static final Object[][] DEFS = {
        {1, "Người Mới", 0, "Danh hiệu khởi đầu của mọi cư dân"},
        {2, "Cư Dân Thị Trấn", 1, "Đạt cấp 5"},
        {3, "Lão Làng", 2, "Đạt cấp 15"},
        {4, "Huyền Thoại Thị Trấn", 3, "Đạt cấp 30"},
        {5, "Đại Gia", 4, "Có lúc nắm giữ 1.000.000 xu"},
        {6, "Cao Thủ Mini Game", 5, "Thắng 25 ván mini game"},
        {7, "Vua Câu Cá", 2, "Sở hữu cần câu xịn (447/448)"},
        {8, "Chủ Clan", 5, "Là chủ một clan"},
        {9, "Phu Thê Song Toàn", 6, "Đã kết hôn"},
        {10, "Nông Dân Vàng", 1, "Đã nâng cấp Farm cấp 2"},
        {11, "Siêng Năng", 4, "Điểm danh tích luỹ 7 điểm"},
        {12, "Nhà Sưu Tầm", 3, "Sở hữu 30 món đồ khác nhau"},
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
                        + " PRIMARY KEY (user_id, title_id))");
                try {
                    c.createStatement().execute(
                            "ALTER TABLE players ADD COLUMN title_id INT DEFAULT 0");
                } catch (Exception ignored) {
                    // cột đã tồn tại
                }
                schemaReady = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Nạp danh hiệu đang đeo từ DB vào RAM (an toàn khi chưa có cột). */
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
        Set<Integer> out = new LinkedHashSet<>();
        out.add(1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT title_id FROM player_titles WHERE user_id = ?")) {
            ps.setInt(1, u.getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getInt(1));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /** Quét điều kiện, cấp danh hiệu mới đạt; trả danh sách vừa cấp. */
    private List<Integer> grantCheck(User u) {
        Set<Integer> have = owned(u);
        List<Integer> fresh = new ArrayList<>();
        List<Integer> grant = new ArrayList<>();
        if (u.getLeverMain() >= 5) grant.add(2);
        if (u.getLeverMain() >= 15) grant.add(3);
        if (u.getLeverMain() >= 30) grant.add(4);
        if (u.getXu() >= 1000000L) grant.add(5);
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(wins),0) FROM mg_stats WHERE user_id = ?")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) >= 25) grant.add(6);
            } catch (Exception ignored) { }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM clans WHERE users_id = ?")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) grant.add(8);
            } catch (Exception ignored) { }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM marry WHERE idNam = ? OR idNu = ?")) {
                ps.setInt(1, u.getId());
                ps.setInt(2, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) grant.add(9);
            } catch (Exception ignored) { }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT lvl2_data FROM farm_data WHERE user_id = ?")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String d = rs.getString(1);
                    if (d != null && d.length() > 2) grant.add(10);
                }
            } catch (Exception ignored) { }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT total_point FROM daily_state WHERE user_id = ?")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) >= 7) grant.add(11);
            } catch (Exception ignored) { }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // cần câu xịn trong giỏ hoặc đang mặc
        java.util.Set<Integer> distinct = new java.util.HashSet<>();
        boolean hasRod = false;
        for (avatar.item.Item it : u.chests) {
            distinct.add(it.getId());
            if (it.getId() == 447 || it.getId() == 448) hasRod = true;
        }
        for (avatar.item.Item it : u.getWearing()) {
            distinct.add(it.getId());
            if (it.getId() == 447 || it.getId() == 448) hasRod = true;
        }
        if (hasRod) grant.add(7);
        if (distinct.size() >= 30) grant.add(12);
        for (int tid : grant) {
            if (have.contains(tid)) continue;
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
        Set<Integer> own = owned(u);
        ds.writeShort(own.size());
        for (int tid : own) ds.writeShort(tid);
        ds.writeByte(fresh.size());
        for (int tid : fresh) ds.writeShort(tid);
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
