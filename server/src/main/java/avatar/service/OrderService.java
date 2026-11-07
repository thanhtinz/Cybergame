package avatar.service;

import avatar.Farm.FarmProductData;
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
import java.util.Random;
import java.util.Set;

/**
 * [PORT] Đơn hàng ủy thác của Lái Buôn — kiểu bảng đơn Hay Day
 * (lệnh Tín 2026-11-07: bỏ bán thẳng trong Kho, nông sản chỉ bán
 * qua giao đơn tại NPC Lái Buôn 850). Phong bì −125,
 * docs/order-spec.md.
 *
 * Sub: OPEN 0 · DELIVER 1 · TRASH 2 · STATE 10 · RESULT 11.
 * Dữ liệu: bảng player_orders, ghi DB ngay mỗi thao tác.
 * Giao xong ô chờ 10 phút, bỏ đơn chờ 5 phút mới có đơn mới.
 * Thưởng Xu = 130% tổng giá gốc; ~25% đơn kèm 1–3 lượng.
 * Giao 1 đơn được +5 điểm nhiệm vụ ngày.
 */
public class OrderService extends Service {

    public static final byte O_OPEN = 0;
    public static final byte O_DELIVER = 1;
    public static final byte O_TRASH = 2;
    public static final byte O_STATE = 10;
    public static final byte O_RESULT = 11;

    static final int SLOTS = 6;
    static final int REGEN_DELIVER_SEC = 600;
    static final int REGEN_TRASH_SEC = 300;
    static final int SCORE_PER_ORDER = 5;

    static final String[] CUSTOMERS = {
            "Cô Ba", "Chú Tư", "Bà Năm", "Anh Sáu", "Chị Bảy",
            "Ông Tám", "Cô Chín", "Bác Mười", "Thím Hai", "Cậu Út",
    };

    static boolean schemaReady = false;

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS player_orders ("
                    + "user_id INT, slot INT, customer VARCHAR(64) DEFAULT '',"
                    + " avatar INT DEFAULT 0, items VARCHAR(255) DEFAULT '',"
                    + " reward_xu INT DEFAULT 0, reward_luong INT DEFAULT 0,"
                    + " regen_at BIGINT DEFAULT 0,"
                    + " PRIMARY KEY(user_id, slot))");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Slot {
        String customer = "";
        int avatar;
        String items = ""; // "pid:qty,pid:qty"
        int rewardXu;
        int rewardLuong;
        long regenAt; // epoch giây; 0 = đang có đơn mở
    }

    public OrderService(Session cl) {
        super(cl);
        ensureSchema();
    }

    // ---------- dữ liệu ----------

    Slot loadSlot(int userId, int slot) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT customer, avatar, items, reward_xu, reward_luong, regen_at"
                     + " FROM player_orders WHERE user_id = ? AND slot = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, slot);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Slot s = new Slot();
                s.customer = rs.getString(1) == null ? "" : rs.getString(1);
                s.avatar = rs.getInt(2);
                s.items = rs.getString(3) == null ? "" : rs.getString(3);
                s.rewardXu = rs.getInt(4);
                s.rewardLuong = rs.getInt(5);
                s.regenAt = rs.getLong(6);
                return s;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    void saveSlot(int userId, int slot, Slot s) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_orders (user_id, slot, customer, avatar, items,"
                     + " reward_xu, reward_luong, regen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE customer = VALUES(customer),"
                     + " avatar = VALUES(avatar), items = VALUES(items),"
                     + " reward_xu = VALUES(reward_xu), reward_luong = VALUES(reward_luong),"
                     + " regen_at = VALUES(regen_at)")) {
            ps.setInt(1, userId);
            ps.setInt(2, slot);
            ps.setString(3, s.customer);
            ps.setInt(4, s.avatar);
            ps.setString(5, s.items);
            ps.setInt(6, s.rewardXu);
            ps.setInt(7, s.rewardLuong);
            ps.setLong(8, s.regenAt);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Ô sẵn sàng hiển thị: chưa có đơn hoặc đã hết giờ chờ thì sinh đơn mới ngay. */
    Slot readySlot(User me, int slotIdx, Random rnd) {
        Slot s = loadSlot(me.getId(), slotIdx);
        long now = System.currentTimeMillis() / 1000L;
        if (s == null || (s.regenAt > 0 && s.regenAt <= now)) {
            s = generate(me, rnd);
            saveSlot(me.getId(), slotIdx, s);
        }
        return s;
    }

    int priceOf(User me, int pid) {
        int price = FarmProductData.sellPriceOf(pid);
        if (price > 0 && Farm2Service.upOf(me.getId(), Farm2Service.UP_PRODUCT)) {
            price = price * 110 / 100;
        }
        return price;
    }

    Slot generate(User me, Random rnd) {
        List<int[]> pool = new ArrayList<>(); // pid, gia goc
        for (java.util.Map.Entry<Integer, Integer> e : FarmProductData.SELL_PRICE_XU.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                pool.add(new int[]{e.getKey(), e.getValue()});
            }
        }
        List<int[]> inStock = new ArrayList<>();
        for (int[] p : pool) {
            if (Farm2Service.countProduct(me, p[0]) > 0) inStock.add(p);
        }
        int roll = rnd.nextInt(10);
        int nItems = roll < 3 ? 1 : (roll < 8 ? 2 : 3);
        Set<Integer> used = new LinkedHashSet<>();
        StringBuilder items = new StringBuilder();
        long base = 0;
        for (int k = 0; k < nItems && used.size() < pool.size(); k++) {
            int[] pick = null;
            List<int[]> stockLeft = new ArrayList<>();
            for (int[] p : inStock) if (!used.contains(p[0])) stockLeft.add(p);
            if (!stockLeft.isEmpty() && rnd.nextInt(10) < 7) {
                pick = stockLeft.get(rnd.nextInt(stockLeft.size()));
            } else {
                for (int tries = 0; tries < 12 && pick == null; tries++) {
                    int[] cand = pool.get(rnd.nextInt(pool.size()));
                    if (!used.contains(cand[0])) pick = cand;
                }
                if (pick == null) break;
            }
            used.add(pick[0]);
            int price = priceOf(me, pick[0]);
            int qty;
            if (price <= 10) qty = 15 + rnd.nextInt(26);
            else if (price <= 100) qty = 8 + rnd.nextInt(13);
            else if (price <= 1000) qty = 4 + rnd.nextInt(7);
            else qty = 1 + rnd.nextInt(3);
            base += (long) price * qty;
            if (items.length() > 0) items.append(',');
            items.append(pick[0]).append(':').append(qty);
        }
        Slot s = new Slot();
        s.customer = CUSTOMERS[rnd.nextInt(CUSTOMERS.length)];
        s.avatar = rnd.nextInt(10);
        s.items = items.toString();
        s.rewardXu = (int) Math.round(base * 1.3);
        s.rewardLuong = rnd.nextInt(4) == 0 ? 1 + rnd.nextInt(3) : 0;
        s.regenAt = 0;
        return s;
    }

    static List<int[]> parseItems(String items) {
        List<int[]> out = new ArrayList<>();
        if (items == null || items.isEmpty()) return out;
        for (String p : items.split(",")) {
            String[] kv = p.split(":");
            if (kv.length == 2) {
                try {
                    out.add(new int[]{Integer.parseInt(kv[0].trim()),
                            Integer.parseInt(kv[1].trim())});
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    // ---------- gói ----------

    static Message msg(byte sub) {
        Message m = new Message(Cmd.ORDER);
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
            Message m = msg(O_RESULT);
            DataOutputStream w = m.writer();
            w.writeBoolean(ok);
            w.writeUTF(text == null ? "" : text);
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    void sendState(User me) {
        try {
            Message m = msg(O_STATE);
            DataOutputStream w = m.writer();
            Random rnd = new Random();
            long now = System.currentTimeMillis() / 1000L;
            w.writeByte(SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                Slot s = readySlot(me, i, rnd);
                if (s.regenAt > now) {
                    w.writeByte(1);
                    w.writeInt((int) (s.regenAt - now));
                } else {
                    w.writeByte(0);
                    w.writeUTF(s.customer);
                    w.writeByte(s.avatar);
                    List<int[]> reqs = parseItems(s.items);
                    w.writeByte(reqs.size());
                    for (int[] r : reqs) {
                        w.writeShort(r[0]);
                        w.writeShort(r[1]);
                        w.writeShort(Math.min(32767, Farm2Service.countProduct(me, r[0])));
                    }
                    w.writeInt(s.rewardXu);
                    w.writeByte(s.rewardLuong);
                }
            }
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    public void onMessage(Message mss) {
        User me = session != null ? session.user : null;
        if (me == null) return;
        try {
            byte sub = mss.reader().readByte();
            switch (sub) {
                case O_OPEN: {
                    sendState(me);
                    break;
                }
                case O_DELIVER: {
                    doDeliver(me, mss.reader().readByte());
                    break;
                }
                case O_TRASH: {
                    doTrash(me, mss.reader().readByte());
                    break;
                }
                default: break;
            }
        } catch (IOException ignored) { }
    }

    void doDeliver(User me, int slotIdx) {
        if (slotIdx < 0 || slotIdx >= SLOTS) return;
        Slot s = loadSlot(me.getId(), slotIdx);
        long now = System.currentTimeMillis() / 1000L;
        if (s == null || s.regenAt > now || s.items.isEmpty()) {
            result(false, "Đơn này không còn nữa.");
            sendState(me);
            return;
        }
        List<int[]> reqs = parseItems(s.items);
        for (int[] r : reqs) {
            if (Farm2Service.countProduct(me, r[0]) < r[1]) {
                result(false, "Chưa đủ hàng cho đơn này.");
                sendState(me);
                return;
            }
        }
        for (int[] r : reqs) Farm2Service.takeProduct(me, r[0], r[1]);
        me.updateXu(s.rewardXu);
        if (s.rewardLuong > 0) me.updateLuong(s.rewardLuong);
        DailyService.addQuestScore(session, SCORE_PER_ORDER);
        s.regenAt = now + REGEN_DELIVER_SEC;
        s.items = "";
        saveSlot(me.getId(), slotIdx, s);
        result(true, "Đã giao đơn của " + s.customer + "! Nhận "
                + fmtXu(s.rewardXu) + " xu"
                + (s.rewardLuong > 0 ? " + " + s.rewardLuong + " lượng" : "")
                + " và +" + SCORE_PER_ORDER + " điểm nhiệm vụ.");
        sendState(me);
    }

    void doTrash(User me, int slotIdx) {
        if (slotIdx < 0 || slotIdx >= SLOTS) return;
        Slot s = loadSlot(me.getId(), slotIdx);
        long now = System.currentTimeMillis() / 1000L;
        if (s == null || s.regenAt > now) {
            sendState(me);
            return;
        }
        s.regenAt = now + REGEN_TRASH_SEC;
        s.items = "";
        saveSlot(me.getId(), slotIdx, s);
        result(true, "Đã bỏ đơn, đơn mới sẽ tới sau 5 phút.");
        sendState(me);
    }

    static String fmtXu(int v) {
        String s = Integer.toString(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
