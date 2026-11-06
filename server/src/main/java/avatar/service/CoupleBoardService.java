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

/**
 * [PORT] Bảng xếp hạng cặp đôi — bản chính 3.4.4 có ListScr.readCoupleBoard
 * + CoupleList (docs/couple-spec.md) nhưng không còn thân giao thức; bản
 * port dùng phong bì riêng Cmd.COUPLE (-122):
 *   C->S sub 0: xin bảng.
 *   S->C sub 10: {byte n; n x {UTF tenNam, UTF tenNu, byte level,
 *          byte per, boolean laToi}; short hangCuaToi} — hạng 0 = chưa có
 *          cặp hoặc ngoài bảng. Xếp theo level rồi per giảm dần, tối đa 20.
 */
public class CoupleBoardService extends Service {

    static final byte S_BOARD = 10;

    public CoupleBoardService(Session session) {
        super(session);
    }

    public void onMessage(Message ms) {
        try {
            if (ms.reader().readByte() != 0) return;
            sendBoard();
        } catch (Exception e) {
            System.out.println("[CoupleBoard] loi: " + e.getMessage());
        }
    }

    private void sendBoard() {
        User me = session.user;
        if (me == null) return;
        try (Connection c = DbManager.getInstance().getConnection()) {
            Message m = new Message(Cmd.COUPLE);
            DataOutputStream w = m.writer();
            w.writeByte(S_BOARD);
            java.util.List<Object[]> rows = new java.util.ArrayList<>();
            int myRank = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT m.idNam, m.idNu, m.level, m.perLevel,"
                    + " COALESCE(NULLIF(p1.name,''), u1.username) AS n1,"
                    + " COALESCE(NULLIF(p2.name,''), u2.username) AS n2"
                    + " FROM marry m"
                    + " LEFT JOIN players p1 ON p1.user_id = m.idNam"
                    + " LEFT JOIN players p2 ON p2.user_id = m.idNu"
                    + " LEFT JOIN users u1 ON u1.id = m.idNam"
                    + " LEFT JOIN users u2 ON u2.id = m.idNu"
                    + " ORDER BY m.level DESC, m.perLevel DESC")) {
                ResultSet rs = ps.executeQuery();
                int rank = 0;
                while (rs.next()) {
                    rank++;
                    int idNam = rs.getInt(1), idNu = rs.getInt(2);
                    boolean mine = idNam == me.getId() || idNu == me.getId();
                    if (mine) myRank = rank;
                    if (rows.size() < 20) {
                        rows.add(new Object[]{rs.getString(5), rs.getString(6),
                            rs.getInt(3), rs.getInt(4), mine});
                    }
                }
            }
            w.writeByte(rows.size());
            for (Object[] r : rows) {
                w.writeUTF(r[0] == null ? "?" : (String) r[0]);
                w.writeUTF(r[1] == null ? "?" : (String) r[1]);
                w.writeByte((Integer) r[2]);
                w.writeByte((Integer) r[3]);
                w.writeBoolean((Boolean) r[4]);
            }
            w.writeShort(myRank);
            w.flush();
            session.sendMessage(m);
        } catch (Exception e) {
            System.out.println("[CoupleBoard] sendBoard loi: " + e.getMessage());
        }
    }
}
