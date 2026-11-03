package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Hòm Thư (tính năng của bản port, không có trong Pack5 gốc): thư hệ
 * thống / sự kiện / bạn bè kèm vật phẩm đính kèm. Gói dùng dải mã trống
 * của Cmd gốc: −108 danh sách, −109 đánh dấu đã đọc, −110 nhận quà,
 * −111 nhận tất cả, −112 xoá thư đã đọc.
 * Đính kèm là JSON: [{"t":"xu","n":50000},{"t":"luong","n":100},
 * {"t":"item","id":<partId>,"n":2}].
 */
public class MailService extends Service {

    public MailService(Session cl) {
        super(cl);
    }

    private static class Letter {
        int id;
        String kind, title, sender, body, attachments;
        boolean read, claimed;
        long createdMillis;
    }

    /** Thư quà đăng nhập hằng ngày: tạo nếu hôm nay chưa có. */
    private void ensureDailyLetter(int userId) {
        try (Connection c = DbManager.getInstance().getConnection()) {
            // Chống tạo lại thư ngày sau khi người chơi xoá thư đã đọc:
            // mốc nhận quà ngày nằm ở bảng daily_claims riêng, thư bị xoá
            // cũng không làm quà ngày xuất hiện lại.
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM daily_claims WHERE user_id = ? AND day = CURDATE()")) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO daily_claims (user_id, day) VALUES (?, CURDATE())")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
            JSONArray atts = new JSONArray();
            JSONObject xu = new JSONObject();
            xu.put("t", "xu");
            xu.put("n", 2000);
            atts.add(xu);
            JSONObject luong = new JSONObject();
            luong.put("t", "luong");
            luong.put("n", 2);
            atts.add(luong);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO letters (user_id, kind, title, sender, body, attachments)"
                            + " VALUES (?, 'daily', ?, 'Hệ Thống', ?, ?)")) {
                ps.setInt(1, userId);
                ps.setString(2, "Quà đăng nhập hằng ngày");
                ps.setString(3, "Xin chào!\nĐây là phần quà đăng nhập hằng ngày của bạn.\n"
                        + "Cảm ơn bạn đã đồng hành cùng Thị Trấn Mặt Trời!");
                ps.setString(4, atts.toJSONString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.out.println("[Mail] ensureDaily loi: " + e.getMessage());
        }
    }

    private List<Letter> loadLetters(int userId) {
        List<Letter> out = new ArrayList<>();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, kind, title, sender, body, attachments, is_read, claimed,"
                             + " created_at FROM letters WHERE user_id = ?"
                             + " ORDER BY created_at DESC, id DESC LIMIT 100")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Letter l = new Letter();
                l.id = rs.getInt(1);
                l.kind = rs.getString(2);
                l.title = rs.getString(3);
                l.sender = rs.getString(4);
                l.body = rs.getString(5);
                l.attachments = rs.getString(6);
                l.read = rs.getInt(7) != 0;
                l.claimed = rs.getInt(8) != 0;
                java.sql.Timestamp ts = rs.getTimestamp(9);
                l.createdMillis = ts != null ? ts.getTime() : System.currentTimeMillis();
                out.add(l);
            }
        } catch (Exception e) {
            System.out.println("[Mail] load loi: " + e.getMessage());
        }
        return out;
    }

    /** Gửi một thư vào Hòm Thư của người chơi — dùng cho quà
     *  giftcode, quà admin gửi và thư sự kiện. attachments theo đúng
     *  format của grant(): [{"t":"xu","n":..},{"t":"luong","n":..},
     *  {"t":"item","id":..,"n":..,"d":<số ngày hạn, bỏ qua = vĩnh viễn>}]. */
    public static boolean sendLetter(int userId, String kind, String title,
            String sender, String body, String attachmentsJson) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO letters (user_id, kind, title, sender, body, attachments)"
                             + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, kind);
            ps.setString(3, title);
            ps.setString(4, sender);
            ps.setString(5, body);
            ps.setString(6, attachmentsJson);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            System.out.println("[Mail] sendLetter loi: " + e.getMessage());
            return false;
        }
    }

    /** Gói −108 (C→S không payload): trả toàn bộ danh sách thư. */
    public void sendList() {
        User me = this.session.user;
        if (me == null) return;
        ensureDailyLetter(me.getId());
        List<Letter> letters = loadLetters(me.getId());
        try {
            Message ms = new Message(Cmd.MAIL_LIST);
            DataOutputStream ds = ms.writer();
            ds.writeByte(letters.size());
            for (Letter l : letters) {
                ds.writeInt(l.id);
                ds.writeUTF(l.kind != null ? l.kind : "system");
                ds.writeUTF(l.title != null ? l.title : "");
                ds.writeUTF(l.sender != null ? l.sender : "");
                ds.writeUTF(l.body != null ? l.body : "");
                ds.writeUTF(l.attachments != null ? l.attachments : "");
                ds.writeByte(l.read ? 1 : 0);
                ds.writeByte(l.claimed ? 1 : 0);
                ds.writeLong(l.createdMillis);
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            System.out.println("[Mail] sendList loi: " + e.getMessage());
        }
    }

    /** Gói −109: {int id} — đánh dấu đã đọc. */
    public void markRead(Message m) {
        User me = this.session.user;
        if (me == null) return;
        try {
            int id = m.reader().readInt();
            try (Connection c = DbManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE letters SET is_read = 1 WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, id);
                ps.setInt(2, me.getId());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.out.println("[Mail] markRead loi: " + e.getMessage());
        }
    }

    /** Phát quà của một thư; trả true nếu phát được (chưa nhận trước đó). */
    private boolean grant(Letter l) {
        if (l.claimed || l.attachments == null) return false;
        Object parsed = JSONValue.parse(l.attachments);
        if (!(parsed instanceof JSONArray)) return false;
        JSONArray atts = (JSONArray) parsed;
        if (atts.isEmpty()) return false;
        User me = this.session.user;
        for (Object o : atts) {
            if (!(o instanceof JSONObject)) continue;
            JSONObject a = (JSONObject) o;
            String t = String.valueOf(a.get("t"));
            long n = a.get("n") instanceof Number ? ((Number) a.get("n")).longValue() : 0;
            if ("xu".equals(t)) {
                me.updateXu(n);
            } else if ("luong".equals(t)) {
                me.updateLuong((int) n);
            } else if ("item".equals(t)) {
                long pid = a.get("id") instanceof Number ? ((Number) a.get("id")).longValue() : 0;
                long day = a.get("d") instanceof Number ? ((Number) a.get("d")).longValue() : 0;
                if (pid > 0 && n > 0) {
                    long exp = day > 0 ? System.currentTimeMillis() + day * 86400000L : -1;
                    me.addItemToChests(new Item((int) pid, exp, (int) n));
                }
            }
        }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE letters SET claimed = 1, is_read = 1 WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, l.id);
            ps.setInt(2, me.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            System.out.println("[Mail] grant loi: " + e.getMessage());
            return false;
        }
        return true;
    }

    /** Gói −110: {int id} — nhận quà của một thư. Trả −110 {int id, byte ok}. */
    public void claim(Message m) {
        User me = this.session.user;
        if (me == null) return;
        int id = -1;
        boolean ok = false;
        try {
            id = m.reader().readInt();
            for (Letter l : loadLetters(me.getId())) {
                if (l.id == id) {
                    ok = grant(l);
                    break;
                }
            }
            if (ok) me.getAvatarService().updateMoney(0);
            Message out = new Message(Cmd.MAIL_CLAIM);
            DataOutputStream ds = out.writer();
            ds.writeInt(id);
            ds.writeByte(ok ? 1 : 0);
            ds.flush();
            sendMessage(out);
        } catch (Exception e) {
            System.out.println("[Mail] claim loi: " + e.getMessage());
        }
    }

    /** Gói −111: nhận quà mọi thư chưa nhận; trả −111 {byte số thư} + danh sách mới. */
    public void claimAll() {
        User me = this.session.user;
        if (me == null) return;
        int count = 0;
        for (Letter l : loadLetters(me.getId())) {
            if (!l.claimed && grant(l)) count++;
        }
        try {
            if (count > 0) me.getAvatarService().updateMoney(0);
            Message out = new Message(Cmd.MAIL_CLAIM_ALL);
            DataOutputStream ds = out.writer();
            ds.writeByte(count);
            ds.flush();
            sendMessage(out);
        } catch (Exception e) {
            System.out.println("[Mail] claimAll loi: " + e.getMessage());
        }
        sendList();
    }

    /** Gói −112: xoá thư đã đọc mà không còn quà chưa nhận; trả danh sách mới. */
    public void deleteRead() {
        User me = this.session.user;
        if (me == null) return;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM letters WHERE user_id = ? AND is_read = 1"
                             + " AND (claimed = 1 OR attachments IS NULL"
                             + " OR attachments = '' OR attachments = '[]')")) {
            ps.setInt(1, me.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            System.out.println("[Mail] deleteRead loi: " + e.getMessage());
        }
        sendList();
    }
}
