package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * [PORT] Rương phụ cá nhân theo BoxScr của bản chính 3.4.4
 * (docs/box-research.md). Bản chính dùng CHANGE_PASS_CONTAINER=-54,
 * nhưng -54 của bản fan đã là SMS nên bản port dùng phong bì -118.
 *
 * Rương phụ là kho thứ hai mang theo người (khác rương NHÀ -87):
 * mở được mọi nơi từ panel Giỏ đồ, chuyển đồ qua lại với giỏ,
 * có mật khẩu bảo vệ (đặt/đổi/mở khoá theo phiên) và nâng cấp
 * sức chứa theo đúng BẢNG GIÁ của rương nhà (ô 10 + 5/cấp,
 * tối đa 55 ô; giá xu/lượng theo cấp như upgradeChestHome).
 *
 * Sub: OPEN 0 · UNLOCK 1 · SETPASS 2 · CHANGEPASS 3 · TRANS 4 ·
 * UPGRADE 5 · STATE 10 (S->C).
 * STATE: {byte sub, byte hasPass, byte unlocked, byte level,
 * byte slot, byte chestSlot} + nếu unlocked: {short n, mỗi món:
 * short id, byte reliability, UTF expired}.
 * Dữ liệu: bảng player_box, ghi DB ngay mỗi thao tác. Trạng thái
 * mở khoá chỉ giữ trong RAM theo phiên đăng nhập.
 */
public class BoxService extends Service {

    public static final byte B_OPEN = 0;
    public static final byte B_UNLOCK = 1;
    public static final byte B_SETPASS = 2;
    public static final byte B_CHANGEPASS = 3;
    public static final byte B_TRANS = 4;
    public static final byte B_UPGRADE = 5;
    public static final byte B_STATE = 10;

    static final int[] SLOTS = {10, 15, 20, 25, 30, 35, 40, 45, 50, 55};
    static final int[] COST_XU = {10000, 20000, 50000, 100000, 200000,
            500000, 1000000, 2000000, 3000000, 5000000};
    static final int[] COST_LUONG = {10, 20, 30, 40, 50, 100, 200, 500, 800, 1200};

    static class BoxState {
        List<Item> items = new ArrayList<>();
        int slot = 10;
        String passHash = "";
        boolean unlocked = false;
        boolean loaded = false;
    }

    static final Map<Integer, BoxState> CACHE = new ConcurrentHashMap<>();
    static boolean schemaReady = false;

    public BoxService(Session session) {
        super(session);
    }

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS player_box ("
                    + "user_id INT PRIMARY KEY, items TEXT,"
                    + "slot INT DEFAULT 10, pass_hash VARCHAR(64) DEFAULT '')");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static int levelOf(int slot) {
        for (int i = SLOTS.length - 1; i >= 0; i--) {
            if (slot >= SLOTS[i]) return i + 1;
        }
        return 1;
    }

    static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    BoxState state() {
        ensureSchema();
        int uid = session.user.getId();
        BoxState st = CACHE.computeIfAbsent(uid, k -> new BoxState());
        if (!st.loaded) {
            st.loaded = true;
            try (Connection c = DbManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT items, slot, pass_hash FROM player_box WHERE user_id=?")) {
                ps.setInt(1, uid);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    st.slot = rs.getInt("slot");
                    st.passHash = rs.getString("pass_hash");
                    if (st.passHash == null) st.passHash = "";
                    String js = rs.getString("items");
                    if (js != null && !js.isEmpty()) {
                        JSONArray arr = (JSONArray) JSONValue.parse(js);
                        if (arr != null) {
                            for (Object o : arr) {
                                JSONObject obj = (JSONObject) o;
                                int id = ((Long) obj.get("id")).intValue();
                                long expired = ((Long) obj.get("expired"));
                                int qty = 1;
                                if (obj.containsKey("quantity")) {
                                    qty = ((Long) obj.get("quantity")).intValue();
                                }
                                Item it = Item.builder().id(id)
                                        .quantity(qty).expired(expired).build();
                                if (it.reliability() > 0) st.items.add(it);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Chưa đặt mật khẩu thì coi như đã mở khoá
            st.unlocked = st.passHash.isEmpty();
        }
        return st;
    }

    void save(BoxState st) {
        JSONArray arr = new JSONArray();
        for (Item it : st.items) {
            JSONObject obj = new JSONObject();
            obj.put("id", it.getId());
            obj.put("expired", it.getExpired());
            obj.put("quantity", it.getQuantity());
            arr.add(obj);
        }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_box (user_id, items, slot, pass_hash) VALUES (?,?,?,?) "
                     + "ON DUPLICATE KEY UPDATE items=VALUES(items), slot=VALUES(slot), pass_hash=VALUES(pass_hash)")) {
            ps.setInt(1, session.user.getId());
            ps.setString(2, arr.toJSONString());
            ps.setInt(3, st.slot);
            ps.setString(4, st.passHash);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendState(BoxState st) throws IOException {
        Message ms = new Message(Cmd.BOX);
        DataOutputStream ds = ms.writer();
        ds.writeByte(B_STATE);
        ds.writeByte(st.passHash.isEmpty() ? 0 : 1);
        ds.writeByte(st.unlocked ? 1 : 0);
        ds.writeByte(levelOf(st.slot));
        ds.writeByte(st.slot);
        ds.writeByte(session.user.getChestSlot());
        if (st.unlocked) {
            ds.writeShort(st.items.size());
            for (Item it : st.items) {
                ds.writeShort(it.getId());
                ds.writeByte(100 - it.reliability());
                ds.writeUTF(it.expiredString());
            }
            // kèm phía GIỎ chính (lọc id 40 như onCustomChest) để
            // client đồng bộ cả hai danh sách sau mỗi lần chuyển đồ
            java.util.List<Item> lst = new java.util.ArrayList<>();
            for (Item it : session.user.chests) {
                if (it.getId() != 40) lst.add(it);
            }
            ds.writeShort(lst.size());
            for (Item it : lst) {
                ds.writeShort(it.getId());
                ds.writeByte(100 - it.reliability());
                ds.writeUTF(it.expiredString());
            }
        }
        ds.flush();
        session.sendMessage(ms);
    }

    public void onMessage(Message ms) throws IOException {
        DataInputStream r = ms.reader();
        byte sub = r.readByte();
        BoxState st = state();
        switch (sub) {
            case B_OPEN: {
                sendState(st);
                break;
            }
            case B_UNLOCK: {
                String pass = r.readUTF();
                if (!st.passHash.isEmpty() && st.passHash.equals(md5(pass))) {
                    st.unlocked = true;
                    sendState(st);
                } else {
                    session.user.getAvatarService()
                            .serverDialog("Mật khẩu rương không đúng.");
                }
                break;
            }
            case B_SETPASS: {
                String pass = r.readUTF();
                if (!st.passHash.isEmpty()) {
                    session.user.getAvatarService()
                            .serverDialog("Rương đã có mật khẩu.");
                    return;
                }
                if (pass.length() < 4) {
                    session.user.getAvatarService()
                            .serverDialog("Mật khẩu phải có ít nhất 4 ký tự.");
                    return;
                }
                st.passHash = md5(pass);
                st.unlocked = true;
                save(st);
                sendState(st);
                break;
            }
            case B_CHANGEPASS: {
                String oldPass = r.readUTF();
                String newPass = r.readUTF();
                if (st.passHash.isEmpty() || !st.passHash.equals(md5(oldPass))) {
                    session.user.getAvatarService()
                            .serverDialog("Mật khẩu cũ không đúng.");
                    return;
                }
                if (!newPass.isEmpty() && newPass.length() < 4) {
                    session.user.getAvatarService()
                            .serverDialog("Mật khẩu mới phải có ít nhất 4 ký tự.");
                    return;
                }
                // Mật khẩu mới rỗng = gỡ mật khẩu khỏi rương
                st.passHash = newPass.isEmpty() ? "" : md5(newPass);
                save(st);
                sendState(st);
                break;
            }
            case B_TRANS: {
                if (!st.unlocked) {
                    session.user.getAvatarService()
                            .serverDialog("Rương đang khoá.");
                    return;
                }
                byte dir = r.readByte(); // 0: giỏ -> rương, 1: rương -> giỏ
                short itemId = r.readShort();
                if (dir == 0) {
                    Item chest = session.user.findItemInChests(itemId);
                    if (chest == null) {
                        session.user.getAvatarService().serverDialog("Không tìm thấy vật phẩm.");
                        return;
                    }
                    if (findInBox(st, itemId) != null) {
                        session.user.getAvatarService().serverDialog("Rương phụ đã có món này.");
                        return;
                    }
                    if (chest.getQuantity() > 1 || chest.getExpired() != -1) {
                        session.user.getAvatarService()
                                .serverDialog("Đồ có hạn dùng hoặc số lượng lớn không cất vào rương phụ được.");
                        return;
                    }
                    if (st.items.size() >= st.slot) {
                        session.user.getAvatarService().serverDialog("Rương phụ đã đầy!");
                        return;
                    }
                    session.user.removeItemFromChests(chest);
                    st.items.add(chest);
                } else {
                    Item box = findInBox(st, itemId);
                    if (box == null) {
                        session.user.getAvatarService().serverDialog("Không tìm thấy vật phẩm.");
                        return;
                    }
                    if (session.user.findItemInChests(itemId) != null) {
                        session.user.getAvatarService().serverDialog("Giỏ đã có món này.");
                        return;
                    }
                    if (session.user.chests.size() >= session.user.getChestSlot()) {
                        session.user.getAvatarService().serverDialog("Giỏ đồ đã đầy!");
                        return;
                    }
                    st.items.remove(box);
                    session.user.addItemToChests(box);
                }
                save(st);
                sendState(st);
                break;
            }
            case B_UPGRADE: {
                if (!st.unlocked) {
                    session.user.getAvatarService()
                            .serverDialog("Rương đang khoá.");
                    return;
                }
                byte type = r.readByte();
                int level = levelOf(st.slot);
                if (level >= SLOTS.length) {
                    session.user.getAvatarService()
                            .serverDialog("Rương phụ đã nâng cấp tối đa!");
                    return;
                }
                if (type == 0) {
                    Message q = new Message(Cmd.BOX);
                    DataOutputStream ds = q.writer();
                    ds.writeByte(B_STATE);
                    ds.writeByte(st.passHash.isEmpty() ? 0 : 1);
                    ds.writeByte(1);
                    ds.writeByte(level);
                    ds.writeByte(st.slot);
                    ds.writeByte(session.user.getChestSlot());
                    ds.writeShort(-1); // cờ: đây là gói HỎI giá nâng cấp
                    ds.writeUTF("Bạn có muốn nâng cấp Rương phụ từ cấp " + level
                            + " lên cấp " + (level + 1) + " (" + SLOTS[level] + " ô) bằng "
                            + COST_XU[level] + " xu và " + COST_LUONG[level] + " lượng không?");
                    ds.flush();
                    session.sendMessage(q);
                } else {
                    int costXu = COST_XU[level];
                    int costLuong = COST_LUONG[level];
                    if (session.user.xu >= costXu && session.user.luong >= costLuong) {
                        session.user.updateXu(-costXu);
                        session.user.getAvatarService().updateMoney(0);
                        session.user.updateLuong(-costLuong);
                        session.user.getAvatarService().updateMoney(0);
                        st.slot = SLOTS[level];
                        save(st);
                        session.user.getAvatarService().serverDialog(
                                "Đã nâng cấp Rương phụ lên cấp " + (level + 1)
                                + " (" + st.slot + " ô)");
                        sendState(st);
                    } else {
                        session.user.getAvatarService()
                                .serverDialog("Bạn không đủ xu hoặc lượng để nâng cấp rương!");
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    /** Ngắt kết nối: khoá lại rương cho phiên sau (mở khoá chỉ
     *  có hiệu lực trong phiên đăng nhập). */
    public static void onUserGone(avatar.model.User u) {
        if (u == null) return;
        BoxState st = CACHE.get(u.getId());
        if (st != null) st.unlocked = st.passHash.isEmpty();
    }

    static Item findInBox(BoxState st, int itemId) {
        for (Item it : st.items) {
            if (it.getId() == itemId) return it;
        }
        return null;
    }
}
