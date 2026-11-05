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
import java.util.ArrayList;
import java.util.List;

/**
 * [PORT] Đấu giá NGƯỢC theo bản chính 3.4.4 (AuctionScr —
 * "REVERSE AUCTION", docs/daily-auction-spec.md). Bản chính không
 * có nhóm lệnh riêng trong Cmd_Message và kênh gốc không khôi phục
 * được → giao thức port: phong bì −117.
 *
 * Luật [PORT]: phiên 30 phút theo đồng hồ (slot = now / 30 phút),
 * vật phẩm xoay vòng theo slot. Giá giảm tuyến tính từ giá khởi điểm
 * xuống giá sàn trong phiên. Ai BID (chốt) đầu tiên ở giá hiện tại
 * thì thắng: trừ xu, part vào rương vĩnh viễn, phiên đóng ngay.
 * Lịch sử các phiên đã đóng lưu bảng auction_history.
 *
 * Sub: INFO 0 · BID 1 · RESULT 2.
 */
public class AuctionService extends Service {

    public static final byte A_INFO = 0;
    public static final byte A_BID = 1;
    public static final byte A_RESULT = 2;

    static final long SLOT_MS = 30L * 60 * 1000;
    static final int[] PARTS = {2090, 2052, 2019, 2343, 620, 2099};
    static final int[] START = {600_000, 450_000, 380_000, 520_000, 150_000, 300_000};

    static boolean schemaReady = false;

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS auction_history ("
                    + "slot BIGINT PRIMARY KEY, winner_id INT, winner_name VARCHAR(64),"
                    + "part_id INT, price INT, end_at BIGINT)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AuctionService(Session cl) {
        super(cl);
        ensureSchema();
    }

    static long slotOf(long now) {
        return now / SLOT_MS;
    }

    static int partIdx(long slot) {
        return (int) Math.floorMod(slot, PARTS.length);
    }

    static int floorOf(int idx) {
        return (int) (START[idx] * 0.35 / 100) * 100;
    }

    /** Giá hiện tại: giảm tuyến tính theo tiến độ phiên, tròn 100. */
    static int priceNow(long now) {
        long slot = slotOf(now);
        int idx = partIdx(slot);
        double progress = (now - slot * SLOT_MS) / (double) SLOT_MS;
        double p = START[idx] - (START[idx] - floorOf(idx)) * progress;
        return (int) (Math.round(p / 100.0) * 100);
    }

    /** Người thắng của một slot (null nếu phiên chưa đóng). */
    Object[] winnerOf(long slot) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT winner_name, part_id, price, end_at FROM auction_history"
                     + " WHERE slot = ?")) {
            ps.setLong(1, slot);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Object[]{rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)};
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    String displayNameOf(int userId, String username) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM players WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String n = rs.getString(1);
                if (n != null && !n.trim().isEmpty()) return n;
            }
        } catch (Exception ignored) { }
        return username != null ? username : ("#" + userId);
    }

    static Message msg(byte sub) {
        Message m = new Message(Cmd.AUCTION);
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
            Message m = msg(A_RESULT);
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
                case A_INFO: sendInfo(); break;
                case A_BID: doBid(me); break;
                default: break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendInfo() {
        long now = System.currentTimeMillis();
        long slot = slotOf(now);
        int idx = partIdx(slot);
        Object[] win = winnerOf(slot);
        boolean active = win == null;
        int secondsLeft = (int) ((SLOT_MS - (now - slot * SLOT_MS)) / 1000);
        List<Object[]> hist = new ArrayList<>();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT winner_name, part_id, price, end_at FROM auction_history"
                     + " ORDER BY end_at DESC LIMIT 8")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                hist.add(new Object[]{rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Message m = msg(A_INFO);
            DataOutputStream w = m.writer();
            w.writeBoolean(active);
            w.writeShort(PARTS[idx]);
            w.writeInt(priceNow(now));
            w.writeInt(START[idx]);
            w.writeInt(floorOf(idx));
            w.writeInt(secondsLeft);
            w.writeUTF(active ? "" : (String) win[0]);
            w.writeByte(hist.size());
            for (Object[] h : hist) {
                w.writeUTF((String) h[0]);
                w.writeShort((Integer) h[1]);
                w.writeInt((Integer) h[2]);
                w.writeLong((Long) h[3]);
            }
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    synchronized void doBid(User me) {
        long now = System.currentTimeMillis();
        long slot = slotOf(now);
        int idx = partIdx(slot);
        if (winnerOf(slot) != null) {
            result(false, "Phiên này đã có người chốt trước rồi!");
            sendInfo();
            return;
        }
        int price = priceNow(now);
        if (me.getXu() < price) {
            result(false, "Bạn không đủ xu để chốt giá " + price + ".");
            return;
        }
        me.updateXu(-price);
        me.addItemToChests(new Item(PARTS[idx], -1, 1));
        String myName = displayNameOf(me.getId(), me.getUsername());
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO auction_history (slot, winner_id, winner_name,"
                     + " part_id, price, end_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, slot);
            ps.setInt(2, me.getId());
            ps.setString(3, myName);
            ps.setInt(4, PARTS[idx]);
            ps.setInt(5, price);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        result(true, "Chốt giá thành công! Bạn thắng phiên đấu giá ngược với giá "
                + price + " xu — vật phẩm đã vào rương.");
        sendInfo();
    }
}
