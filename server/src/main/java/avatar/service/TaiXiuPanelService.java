package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.minigame.TaiXiu;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * [PORT] Bảng Tài Xỉu (gói −124): bản chính không có TaiXiuScr —
 * cược vẫn qua menu NPC Thần Tài 864 như gốc; bảng này chỉ TRÌNH
 * BÀY dữ liệu phiên đang chạy / lịch sử / top / thành tích của
 * chính hệ Tài Xỉu phía server (trước nay xem qua tin nhắn chữ).
 * Mở bảng: mục "Bảng Tài Xỉu" trong menu NPC 864, hoặc client
 * gửi sub 0 để làm mới.
 */
public class TaiXiuPanelService extends Service {
    public TaiXiuPanelService(Session session) {
        super(session);
    }

    public void onMessage(Message ms) throws IOException {
        User u = this.session.user;
        if (u == null) return;
        byte sub = ms.reader().readByte();
        if (sub == 0) sendSnapshot(u);
    }

    public void sendSnapshot(User u) throws IOException {
        TaiXiu tx = TaiXiu.getInstance();
        int gameId = tx.currentGameId();
        long[] pools = new long[4]; // TaiXu, XiuXu, TaiLuong, XiuLuong
        int[] mine = new int[4];
        List<String[]> hist = new ArrayList<>();
        List<String[]> topXu = new ArrayList<>();
        List<String[]> topLuong = new ArrayList<>();
        int myBets = 0, myWins = 0;
        long myNetXu = 0, myNetLuong = 0;
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT bet_type, currency, SUM(bet_amount) FROM betgame"
                    + " WHERE game_id = ? GROUP BY bet_type, currency")) {
                ps.setInt(1, gameId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int idx = idx(rs.getString(1), rs.getString(2));
                    if (idx >= 0) pools[idx] = rs.getLong(3);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT bet_type, currency, SUM(bet_amount) FROM betgame"
                    + " WHERE game_id = ? AND user_id = ?"
                    + " GROUP BY bet_type, currency")) {
                ps.setInt(1, gameId);
                ps.setInt(2, u.getId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int idx = idx(rs.getString(1), rs.getString(2));
                    if (idx >= 0) mine[idx] = (int) Math.min(Integer.MAX_VALUE, rs.getLong(3));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT game_id, result FROM game_rounds"
                    + " WHERE result <> 'Pending'"
                    + " ORDER BY created_at DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hist.add(new String[]{String.valueOf(rs.getInt(1)), rs.getString(2)});
                }
            }
            topXu = top(c, "Xu");
            topLuong = top(c, "Lượng");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT bg.currency, bg.bet_amount,"
                    + " (gr.result LIKE CONCAT('%', bg.bet_type, '%')) AS win"
                    + " FROM betgame bg JOIN game_rounds gr"
                    + " ON bg.game_id = gr.game_id"
                    + " WHERE bg.user_id = ? AND gr.game_status = 'Closed'")) {
                ps.setInt(1, u.getId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    myBets++;
                    long amt = rs.getLong(2);
                    boolean win = rs.getBoolean(3);
                    if (win) myWins++;
                    long delta = win ? (long) (amt * 0.95) : -amt;
                    if ("Xu".equals(rs.getString(1))) myNetXu += delta;
                    else myNetLuong += delta;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Message ms = new Message(Cmd.TAIXIU);
        DataOutputStream ds = ms.writer();
        ds.writeByte(10);
        ds.writeInt(gameId);
        ds.writeShort(tx.secondsLeft());
        for (long v : pools) ds.writeLong(v);
        for (int v : mine) ds.writeInt(v);
        ds.writeByte(hist.size());
        for (String[] h : hist) {
            ds.writeInt(Integer.parseInt(h[0]));
            ds.writeUTF(h[1] == null ? "" : h[1]);
        }
        writeTop(ds, topXu);
        writeTop(ds, topLuong);
        ds.writeInt(myBets);
        ds.writeInt(myWins);
        ds.writeLong(myNetXu);
        ds.writeLong(myNetLuong);
        ds.flush();
        this.session.sendMessage(ms);
    }

    private static int idx(String betType, String currency) {
        if (betType == null || currency == null) return -1;
        boolean tai = betType.contains("Tài");
        boolean xu = currency.equals("Xu");
        return tai ? (xu ? 0 : 2) : (xu ? 1 : 3);
    }

    /** Top 5 thắng ròng theo một loại tiền (cùng công thức bản fan). */
    private List<String[]> top(Connection c, String currency) throws Exception {
        List<String[]> out = new ArrayList<>();
        String q = "SELECT COALESCE(NULLIF(p.name, ''), us.username) AS dn,"
            + " SUM(CASE WHEN gr.result LIKE CONCAT('%', bg.bet_type, '%')"
            + " THEN bg.bet_amount * 0.95 ELSE 0 END)"
            + " - SUM(CASE WHEN gr.result NOT LIKE CONCAT('%', bg.bet_type, '%')"
            + " THEN bg.bet_amount ELSE 0 END) AS net"
            + " FROM betgame bg JOIN game_rounds gr ON bg.game_id = gr.game_id"
            + " JOIN users us ON bg.user_id = us.id"
            + " LEFT JOIN players p ON p.user_id = us.id"
            + " WHERE gr.game_status = 'Closed' AND bg.currency = ?"
            + " GROUP BY bg.user_id, dn HAVING net > 0"
            + " ORDER BY net DESC LIMIT 5";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(new String[]{rs.getString(1), String.valueOf(rs.getLong(2))});
            }
        }
        return out;
    }

    private void writeTop(DataOutputStream ds, List<String[]> top) throws IOException {
        ds.writeByte(top.size());
        for (String[] t : top) {
            ds.writeUTF(t[0]);
            ds.writeLong(Long.parseLong(t[1]));
        }
    }
}
