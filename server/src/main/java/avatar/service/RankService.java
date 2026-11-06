package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * [PORT] Bảng xếp hạng TỔNG HỢP — chủ dự án lệnh 2026-11-06: một panel
 * BXH riêng chứa mọi thể loại, không gộp vào panel khác. Phong bì
 * Cmd.RANK (-123):
 *   C->S: {byte category}
 *   S->C: {byte category, byte n (<=20), n x {UTF ten, UTF giaTri,
 *          boolean laToi}, short hangCuaToi (0 = chưa có hạng),
 *          UTF giaTriCuaToi}
 * Thể loại: 0 Cấp độ · 1 Đại gia (Xu) · 2 Cặp đôi · 3 Clan ·
 *           4 Mini game (tổng thắng) · 5 Câu cá · 6 Thu hoạch · 7 Đố vui.
 * Tên hiển thị: players.name, trống thì users.username.
 */
public class RankService extends Service {

    public RankService(Session session) {
        super(session);
    }

    private static class Row {
        String name, value;
        boolean mine;
        Row(String n, String v, boolean m) { name = n; value = v; mine = m; }
    }

    public void onMessage(Message ms) {
        try {
            int cat = ms.reader().readByte();
            sendBoard(cat);
        } catch (Exception e) {
            System.out.println("[Rank] loi: " + e.getMessage());
        }
    }

    private static String fmt(long v) {
        return String.format("%,d", v).replace(',', '.');
    }

    private void sendBoard(int cat) throws Exception {
        User me = session.user;
        if (me == null) return;
        int myId = me.getId();
        List<Row> rows = new ArrayList<>();
        int myRank = 0;
        String myValue = "";
        try (Connection c = DbManager.getInstance().getConnection()) {
            switch (cat) {
                case 0: { // Cấp độ
                    String sql = "SELECT p.user_id, p.level_main, p.exp_main,"
                        + " COALESCE(NULLIF(p.name,''), u.username) ten"
                        + " FROM players p JOIN users u ON u.id = p.user_id"
                        + " ORDER BY p.level_main DESC, p.exp_main DESC";
                    int rank = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rank++;
                            int uid = rs.getInt(1);
                            String v = "Cấp " + rs.getInt(2);
                            if (uid == myId) { myRank = rank; myValue = v; }
                            if (rank <= 20) rows.add(new Row(rs.getString(4), v, uid == myId));
                        }
                    }
                    break;
                }
                case 1: { // Đại gia (Xu)
                    String sql = "SELECT p.user_id, p.xu,"
                        + " COALESCE(NULLIF(p.name,''), u.username) ten"
                        + " FROM players p JOIN users u ON u.id = p.user_id"
                        + " ORDER BY p.xu DESC";
                    int rank = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rank++;
                            int uid = rs.getInt(1);
                            String v = fmt(rs.getLong(2)) + " xu";
                            if (uid == myId) { myRank = rank; myValue = v; }
                            if (rank <= 20) rows.add(new Row(rs.getString(3), v, uid == myId));
                        }
                    }
                    break;
                }
                case 2: { // Cặp đôi (cùng cách xếp của CoupleBoardService)
                    String sql = "SELECT m.idNam, m.idNu, m.level, m.perLevel,"
                        + " COALESCE(NULLIF(pa.name,''), ua.username) tenNam,"
                        + " COALESCE(NULLIF(pb.name,''), ub.username) tenNu"
                        + " FROM marry m"
                        + " LEFT JOIN players pa ON pa.user_id = m.idNam"
                        + " LEFT JOIN users ua ON ua.id = m.idNam"
                        + " LEFT JOIN players pb ON pb.user_id = m.idNu"
                        + " LEFT JOIN users ub ON ub.id = m.idNu"
                        + " ORDER BY m.level DESC, m.perLevel DESC";
                    int rank = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rank++;
                            boolean mine = rs.getInt(1) == myId || rs.getInt(2) == myId;
                            String v = "Cấp " + rs.getInt(3) + " - " + rs.getInt(4) + "%";
                            if (mine) { myRank = rank; myValue = v; }
                            if (rank <= 20) {
                                rows.add(new Row(rs.getString(5) + " + " + rs.getString(6), v, mine));
                            }
                        }
                    }
                    break;
                }
                case 3: { // Clan
                    String myClan = "SELECT clan_id FROM players WHERE user_id = ?";
                    int myClanId = -1;
                    try (PreparedStatement ps = c.prepareStatement(myClan)) {
                        ps.setInt(1, myId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) myClanId = rs.getInt(1);
                        }
                    }
                    String sql = "SELECT id, name, level, exp FROM clans"
                        + " ORDER BY level DESC, exp DESC";
                    int rank = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rank++;
                            boolean mine = rs.getInt(1) == myClanId;
                            String v = "Cấp " + rs.getInt(3);
                            if (mine) { myRank = rank; myValue = v; }
                            if (rank <= 20) rows.add(new Row(rs.getString(2), v, mine));
                        }
                    }
                    break;
                }
                case 4: { // Mini game: tổng số ván thắng
                    String sql = "SELECT s.user_id, SUM(s.wins) tong,"
                        + " COALESCE(NULLIF(p.name,''), u.username) ten"
                        + " FROM mg_stats s"
                        + " LEFT JOIN players p ON p.user_id = s.user_id"
                        + " LEFT JOIN users u ON u.id = s.user_id"
                        + " GROUP BY s.user_id ORDER BY tong DESC";
                    int rank = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rank++;
                            int uid = rs.getInt(1);
                            String v = rs.getLong(2) + " thắng";
                            if (uid == myId) { myRank = rank; myValue = v; }
                            if (rank <= 20) rows.add(new Row(rs.getString(3), v, uid == myId));
                        }
                    }
                    break;
                }
                case 5: case 6: case 7: { // title_counters: fish/harvest/quiz_ok
                    String col = cat == 5 ? "fish" : cat == 6 ? "harvest" : "quiz_ok";
                    String unit = cat == 5 ? " cá" : cat == 6 ? " vụ" : " câu đúng";
                    String sql = "SELECT t.user_id, t." + col + ","
                        + " COALESCE(NULLIF(p.name,''), u.username) ten"
                        + " FROM title_counters t"
                        + " LEFT JOIN players p ON p.user_id = t.user_id"
                        + " LEFT JOIN users u ON u.id = t.user_id"
                        + " WHERE t." + col + " > 0 ORDER BY t." + col + " DESC";
                    int rank = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rank++;
                            int uid = rs.getInt(1);
                            String v = rs.getLong(2) + unit;
                            if (uid == myId) { myRank = rank; myValue = v; }
                            if (rank <= 20) rows.add(new Row(rs.getString(3), v, uid == myId));
                        }
                    }
                    break;
                }
                default:
                    return;
            }
        }
        Message m = new Message(Cmd.RANK);
        DataOutputStream w = m.writer();
        w.writeByte(cat);
        w.writeByte(rows.size());
        for (Row r : rows) {
            w.writeUTF(r.name);
            w.writeUTF(r.value);
            w.writeBoolean(r.mine);
        }
        w.writeShort(myRank);
        w.writeUTF(myValue);
        w.flush();
        session.sendMessage(m);
    }
}
