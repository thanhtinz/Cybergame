package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.UserManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [PORT] Chợ người chơi + Giao dịch trực tiếp theo bản chính 3.4.4 —
 * phong bì Cmd.MARKET (127), byte đầu payload là lệnh con
 * (docs/market-trade-spec.md; bản chính không công khai định dạng gói
 * con nên phần này dựng theo mô hình MarketScr/TradingScr).
 */
public class MarketTradeService extends Service {

    // sub chợ
    public static final int M_LIST = 0, M_SELL = 1, M_BUY = 2, M_CANCEL = 3,
            M_MY = 4, M_RESULT = 5, M_CHEST = 6;
    // sub giao dịch
    public static final int T_INVITE = 10, T_INVITE_IN = 11, T_REPLY = 12,
            T_OPEN = 13, T_SET = 14, T_PARTNER_SET = 15, T_LOCK = 16,
            T_LOCK_STATE = 17, T_ACCEPT = 18, T_DONE = 19, T_CANCEL = 20,
            T_CHAT = 21;

    private static final int PAGE_SIZE = 20;
    private static final int MAX_LISTINGS = 10;
    private static volatile boolean schemaReady = false;

    public static class TradeSide {
        public List<int[]> items = new ArrayList<>(); // {partId, qty}
        public List<Long> exps = new ArrayList<>();
        public int xu = 0;
        public boolean locked = false;
        public boolean accepted = false;
    }

    public static class TradeSession {
        public int aId, bId;
        public TradeSide a = new TradeSide(), b = new TradeSide();
        TradeSide sideOf(int uid) { return uid == aId ? a : b; }
        TradeSide otherOf(int uid) { return uid == aId ? b : a; }
        int otherId(int uid) { return uid == aId ? bId : aId; }
    }

    private static final Map<Integer, TradeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> INVITES = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> INVITE_AT = new ConcurrentHashMap<>();

    public MarketTradeService(Session cl) {
        super(cl);
        ensureSchema();
    }

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS market_listings ("
                            + "id INT PRIMARY KEY AUTO_INCREMENT, seller_id INT NOT NULL,"
                            + "part_id INT NOT NULL, qty INT NOT NULL DEFAULT 1,"
                            + "price_xu INT NOT NULL, expire_at BIGINT NOT NULL DEFAULT -1,"
                            + "created_at BIGINT NOT NULL DEFAULT 0)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- gửi gói ----------

    static Message msg(int sub) {
        Message m = new Message(Cmd.MARKET);
        try { m.writer().writeByte(sub); } catch (IOException e) { }
        return m;
    }

    static void send(User u, Message m) {
        if (u == null || u.session == null) return;
        try { u.session.sendMessage(m); } catch (Exception e) { }
    }

    void result(User me, boolean ok, String text) {
        try {
            Message m = msg(M_RESULT);
            DataOutputStream w = m.writer();
            w.writeBoolean(ok);
            w.writeUTF(text == null ? "" : text);
            w.flush();
            send(me, m);
        } catch (IOException e) { }
    }

    void dialog(User u, String text) {
        if (u == null) return;
        try {
            Message m = new Message(Cmd.SET_MONEY_ERROR);
            m.writer().writeUTF(text);
            m.writer().flush();
            send(u, m);
        } catch (IOException e) { }
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

    static int countInChest(User u, int partId, long expireAt) {
        int n = 0;
        synchronized (u.chests) {
            for (Item it : u.chests) {
                if (it.getId() == partId && it.getExpired() == expireAt) n += it.getQuantity();
            }
        }
        return n;
    }

    static boolean takeFromChest(User u, int partId, long expireAt, int qty) {
        synchronized (u.chests) {
            if (countInChest(u, partId, expireAt) < qty) return false;
            int remain = qty;
            for (int i = u.chests.size() - 1; i >= 0 && remain > 0; i--) {
                Item it = u.chests.get(i);
                if (it.getId() != partId || it.getExpired() != expireAt) continue;
                int take = Math.min(remain, it.getQuantity());
                remain -= take;
                if (take >= it.getQuantity()) u.chests.remove(i);
                else it.setQuantity(it.getQuantity() - take);
            }
            return true;
        }
    }

    // ---------- điểm vào ----------

    public void onMessage(Message mss) {
        User me = session.user;
        if (me == null) return;
        try {
            DataInputStream dis = mss.reader();
            if (dis.available() < 1) return;
            int sub = dis.readByte();
            switch (sub) {
                case M_LIST: sendList(me, dis.readByte()); break;
                case M_SELL: doSell(me, dis); break;
                case M_BUY: doBuy(me, dis.readInt()); break;
                case M_CANCEL: doCancelListing(me, dis.readInt()); break;
                case M_MY: sendMyList(me); break;
                case M_CHEST: sendChest(me); break;
                case T_INVITE: doInvite(me, dis.readInt()); break;
                case T_REPLY: doReply(me, dis); break;
                case T_SET: doSet(me, dis); break;
                case T_LOCK: doLock(me, dis.readBoolean()); break;
                case T_ACCEPT: doAccept(me); break;
                case T_CANCEL: cancelTrade(me, "Đối tác đã huỷ giao dịch."); break;
                case T_CHAT: doChat(me, dis.readUTF()); break;
                default: break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- CHỢ ----------

    void writeListings(User me, List<Object[]> rows, int page, int totalPages) {
        try {
            Message m = msg(M_LIST);
            DataOutputStream w = m.writer();
            w.writeByte(page);
            w.writeByte(totalPages);
            w.writeByte(rows.size());
            for (Object[] r : rows) {
                w.writeInt((Integer) r[0]);
                w.writeInt((Integer) r[1]);
                w.writeUTF((String) r[2]);
                w.writeShort((Integer) r[3]);
                w.writeInt((Integer) r[4]);
                w.writeInt((Integer) r[5]);
                w.writeLong((Long) r[6]);
            }
            w.flush();
            send(me, m);
        } catch (IOException e) { }
    }

    List<Object[]> queryListings(String where, int limit, int offset) {
        List<Object[]> out = new ArrayList<>();
        String sql = "SELECT id, seller_id, part_id, qty, price_xu, expire_at "
                + "FROM market_listings " + where + " ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int seller = rs.getInt(2);
                User su = UserManager.getInstance().find(seller);
                String name = displayNameOf(seller, su != null ? su.getUsername() : null);
                out.add(new Object[]{rs.getInt(1), seller, name, rs.getInt(3),
                        rs.getInt(4), rs.getInt(5), rs.getLong(6)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    void sendList(User me, int page) {
        int total = 0;
        try (Connection c = DbManager.getInstance().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM market_listings")) {
            if (rs.next()) total = rs.getInt(1);
        } catch (Exception ignored) { }
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.max(0, Math.min(page, pages - 1));
        writeListings(me, queryListings("", PAGE_SIZE, p * PAGE_SIZE), p, pages);
    }

    void sendMyList(User me) {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, part_id, qty, price_xu, expire_at FROM market_listings "
                             + "WHERE seller_id = ? ORDER BY id DESC")) {
            ps.setInt(1, me.getId());
            ResultSet rs = ps.executeQuery();
            String myName = displayNameOf(me.getId(), me.getUsername());
            while (rs.next()) {
                rows.add(new Object[]{rs.getInt(1), me.getId(), myName, rs.getInt(2),
                        rs.getInt(3), rs.getInt(4), rs.getLong(5)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        writeListings(me, rows, 0, 1);
    }

    void doSell(User me, DataInputStream dis) throws IOException {
        int partId = dis.readShort();
        int qty = dis.readInt();
        int price = dis.readInt();
        if (qty < 1 || qty > 999) { result(me, false, "Số lượng không hợp lệ."); return; }
        if (price < 100 || price > 999_999_999) {
            result(me, false, "Giá phải từ 100 xu trở lên.");
            return;
        }
        int mine = 0;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM market_listings WHERE seller_id = ?")) {
            ps.setInt(1, me.getId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) mine = rs.getInt(1);
        } catch (Exception ignored) { }
        if (mine >= MAX_LISTINGS) {
            result(me, false, "Bạn đã đăng tối đa " + MAX_LISTINGS + " tin bán.");
            return;
        }
        Item found = null;
        synchronized (me.chests) {
            for (Item it : me.chests) {
                if (it.getId() == partId) { found = it; break; }
            }
        }
        if (found == null) {
            result(me, false, "Bạn không có món đồ này trong rương.");
            return;
        }
        long exp = found.getExpired();
        if (!takeFromChest(me, partId, exp, qty)) {
            result(me, false, "Không đủ số lượng trong rương.");
            return;
        }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO market_listings (seller_id, part_id, qty, price_xu, expire_at, created_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, me.getId());
            ps.setInt(2, partId);
            ps.setInt(3, qty);
            ps.setInt(4, price);
            ps.setLong(5, exp);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            // hoàn đồ nếu ghi DB lỗi
            me.addItemToChests(new Item(partId, exp, qty));
            result(me, false, "Đăng bán thất bại, thử lại sau.");
            return;
        }
        result(me, true, "Đã đăng bán lên chợ.");
        sendMyList(me);
    }

    void doBuy(User me, int listingId) {
        Object[] row = null;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT seller_id, part_id, qty, price_xu, expire_at FROM market_listings WHERE id = ?")) {
            ps.setInt(1, listingId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                row = new Object[]{rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5)};
            }
        } catch (Exception ignored) { }
        if (row == null) { result(me, false, "Tin bán không còn tồn tại."); return; }
        int sellerId = (Integer) row[0];
        int partId = (Integer) row[1];
        int qty = (Integer) row[2];
        int price = (Integer) row[3];
        long exp = (Long) row[4];
        if (sellerId == me.getId()) {
            result(me, false, "Không thể mua tin bán của chính mình.");
            return;
        }
        if (me.getXu() < price) { result(me, false, "Bạn không đủ xu."); return; }
        int deleted = 0;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM market_listings WHERE id = ?")) {
            ps.setInt(1, listingId);
            deleted = ps.executeUpdate();
        } catch (Exception ignored) { }
        if (deleted == 0) { result(me, false, "Tin bán vừa bị người khác mua mất."); return; }
        me.updateXu(-price);
        me.addItemToChests(new Item(partId, exp, qty));
        int gain = price - price * 5 / 100; // thuế chợ 5%
        User seller = UserManager.getInstance().find(sellerId);
        if (seller != null) {
            seller.updateXu(gain);
        } else {
            try (Connection c = DbManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE players SET xu = xu + ? WHERE user_id = ?")) {
                ps.setInt(1, gain);
                ps.setInt(2, sellerId);
                ps.executeUpdate();
            } catch (Exception ignored) { }
        }
        result(me, true, "Mua thành công! Đồ đã vào rương của bạn.");
    }

    void doCancelListing(User me, int listingId) {
        Object[] row = null;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT part_id, qty, expire_at FROM market_listings WHERE id = ? AND seller_id = ?")) {
            ps.setInt(1, listingId);
            ps.setInt(2, me.getId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) row = new Object[]{rs.getInt(1), rs.getInt(2), rs.getLong(3)};
        } catch (Exception ignored) { }
        if (row == null) { result(me, false, "Không tìm thấy tin bán của bạn."); return; }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM market_listings WHERE id = ? AND seller_id = ?")) {
            ps.setInt(1, listingId);
            ps.setInt(2, me.getId());
            ps.executeUpdate();
        } catch (Exception ignored) { }
        me.addItemToChests(new Item((Integer) row[0], (Long) row[2], (Integer) row[1]));
        result(me, true, "Đã gỡ tin bán, đồ đã trả về rương.");
        sendMyList(me);
    }

    void sendChest(User me) {
        // gộp rương theo (partId, expireAt)
        java.util.LinkedHashMap<String, Object[]> groups = new java.util.LinkedHashMap<>();
        synchronized (me.chests) {
            for (Item it : me.chests) {
                String key = it.getId() + "|" + it.getExpired();
                Object[] g = groups.get(key);
                if (g == null) {
                    groups.put(key, new Object[]{it.getId(), it.getQuantity(), it.getExpired()});
                } else {
                    g[1] = (Integer) g[1] + it.getQuantity();
                }
            }
        }
        try {
            Message m = msg(M_CHEST);
            DataOutputStream w = m.writer();
            w.writeByte(Math.min(255, groups.size()));
            int sent = 0;
            for (Object[] g : groups.values()) {
                if (sent++ >= 255) break;
                w.writeShort((Integer) g[0]);
                w.writeInt((Integer) g[1]);
                w.writeLong((Long) g[2]);
            }
            w.flush();
            send(me, m);
        } catch (IOException ignored) { }
    }

    // ---------- GIAO DỊCH ----------

    public static void onUserGone(User u) {
        if (u == null) return;
        INVITES.values().removeIf(v -> v == u.getId());
        INVITE_AT.remove(u.getId());
        TradeSession s = SESSIONS.get(u.getId());
        if (s != null) {
            SESSIONS.remove(s.aId);
            SESSIONS.remove(s.bId);
            User other = UserManager.getInstance().find(s.otherId(u.getId()));
            if (other != null) {
                Message m = msg(T_CANCEL);
                try {
                    m.writer().writeUTF("Đối tác đã rời khỏi giao dịch.");
                    m.writer().flush();
                } catch (IOException ignored) { }
                send(other, m);
            }
        }
    }

    void doInvite(User me, int targetId) {
        if (SESSIONS.containsKey(me.getId())) {
            result(me, false, "Bạn đang trong một giao dịch khác.");
            return;
        }
        User target = UserManager.getInstance().find(targetId);
        if (target == null || target == me) {
            result(me, false, "Người chơi không trực tuyến.");
            return;
        }
        if (SESSIONS.containsKey(targetId)) {
            result(me, false, "Người đó đang giao dịch với người khác.");
            return;
        }
        INVITES.put(targetId, me.getId());
        INVITE_AT.put(targetId, System.currentTimeMillis());
        try {
            Message m = msg(T_INVITE_IN);
            DataOutputStream w = m.writer();
            w.writeInt(me.getId());
            w.writeUTF(displayNameOf(me.getId(), me.getUsername()));
            w.flush();
            send(target, m);
        } catch (IOException ignored) { }
        result(me, true, "Đã gửi lời mời giao dịch.");
    }

    void doReply(User me, DataInputStream dis) throws IOException {
        int fromId = dis.readInt();
        boolean accept = dis.readBoolean();
        Integer inv = INVITES.get(me.getId());
        Long at = INVITE_AT.get(me.getId());
        INVITES.remove(me.getId());
        INVITE_AT.remove(me.getId());
        if (inv == null || inv != fromId || at == null
                || System.currentTimeMillis() - at > 60_000) return;
        User from = UserManager.getInstance().find(fromId);
        if (from == null) return;
        if (!accept) {
            dialog(from, displayNameOf(me.getId(), me.getUsername()) + " đã từ chối giao dịch.");
            return;
        }
        if (SESSIONS.containsKey(me.getId()) || SESSIONS.containsKey(fromId)) return;
        TradeSession s = new TradeSession();
        s.aId = fromId;
        s.bId = me.getId();
        SESSIONS.put(s.aId, s);
        SESSIONS.put(s.bId, s);
        try {
            Message m1 = msg(T_OPEN);
            m1.writer().writeInt(me.getId());
            m1.writer().writeUTF(displayNameOf(me.getId(), me.getUsername()));
            m1.writer().flush();
            send(from, m1);
            Message m2 = msg(T_OPEN);
            m2.writer().writeInt(fromId);
            m2.writer().writeUTF(displayNameOf(fromId, from.getUsername()));
            m2.writer().flush();
            send(me, m2);
        } catch (IOException ignored) { }
    }

    void sendSide(User to, TradeSide side) {
        try {
            Message m = msg(T_PARTNER_SET);
            DataOutputStream w = m.writer();
            w.writeByte(side.items.size());
            for (int i = 0; i < side.items.size(); i++) {
                w.writeShort(side.items.get(i)[0]);
                w.writeInt(side.items.get(i)[1]);
                w.writeLong(side.exps.get(i));
            }
            w.writeInt(side.xu);
            w.flush();
            send(to, m);
        } catch (IOException ignored) { }
    }

    void sendLockState(TradeSession s, int whoId, boolean locked) {
        for (int uid : new int[]{s.aId, s.bId}) {
            User u = UserManager.getInstance().find(uid);
            if (u == null) continue;
            try {
                Message m = msg(T_LOCK_STATE);
                m.writer().writeByte(uid == whoId ? 0 : 1);
                m.writer().writeBoolean(locked);
                m.writer().flush();
                send(u, m);
            } catch (IOException ignored) { }
        }
    }

    void doSet(User me, DataInputStream dis) throws IOException {
        TradeSession s = SESSIONS.get(me.getId());
        if (s == null) return;
        int n = dis.readByte();
        List<int[]> items = new ArrayList<>();
        List<Long> exps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int partId = dis.readShort();
            int qty = dis.readInt();
            long exp = dis.readLong();
            if (qty >= 1 && countInChest(me, partId, exp) >= qty) {
                items.add(new int[]{partId, qty});
                exps.add(exp);
            }
        }
        int xu = dis.readInt();
        if (xu < 0) xu = 0;
        if (xu > me.getXu()) xu = (int) Math.min(Integer.MAX_VALUE, me.getXu());
        TradeSide side = s.sideOf(me.getId());
        side.items = items;
        side.exps = exps;
        side.xu = xu;
        side.locked = false;
        side.accepted = false;
        TradeSide other = s.otherOf(me.getId());
        other.locked = false;
        other.accepted = false;
        User otherUser = UserManager.getInstance().find(s.otherId(me.getId()));
        sendSide(otherUser, side);
        sendLockState(s, me.getId(), false);
        sendLockState(s, s.otherId(me.getId()), false);
    }

    void doLock(User me, boolean lock) {
        TradeSession s = SESSIONS.get(me.getId());
        if (s == null) return;
        TradeSide side = s.sideOf(me.getId());
        side.locked = lock;
        if (!lock) side.accepted = false;
        sendLockState(s, me.getId(), lock);
    }

    void doAccept(User me) {
        TradeSession s = SESSIONS.get(me.getId());
        if (s == null) return;
        TradeSide mine = s.sideOf(me.getId());
        TradeSide other = s.otherOf(me.getId());
        if (!mine.locked || !other.locked) {
            result(me, false, "Cả hai bên phải Khoá trước khi Chấp nhận.");
            return;
        }
        mine.accepted = true;
        User otherUser = UserManager.getInstance().find(s.otherId(me.getId()));
        if (!other.accepted) {
            dialog(otherUser, "Đối tác đã chấp nhận giao dịch — chờ bạn chấp nhận.");
            return;
        }
        User a = UserManager.getInstance().find(s.aId);
        User b = UserManager.getInstance().find(s.bId);
        SESSIONS.remove(s.aId);
        SESSIONS.remove(s.bId);
        String fail = null;
        if (a == null || b == null) fail = "Một bên đã thoát khỏi giao dịch.";
        for (int i = 0; fail == null && i < s.a.items.size(); i++) {
            if (countInChest(a, s.a.items.get(i)[0], s.a.exps.get(i)) < s.a.items.get(i)[1]) {
                fail = "Đồ của một bên không còn đủ trong rương.";
            }
        }
        for (int i = 0; fail == null && i < s.b.items.size(); i++) {
            if (countInChest(b, s.b.items.get(i)[0], s.b.exps.get(i)) < s.b.items.get(i)[1]) {
                fail = "Đồ của một bên không còn đủ trong rương.";
            }
        }
        if (fail == null && (a.getXu() < s.a.xu || b.getXu() < s.b.xu)) {
            fail = "Một bên không đủ xu.";
        }
        if (fail != null) {
            done(a, false, fail);
            done(b, false, fail);
            return;
        }
        for (int i = 0; i < s.a.items.size(); i++) {
            takeFromChest(a, s.a.items.get(i)[0], s.a.exps.get(i), s.a.items.get(i)[1]);
            b.addItemToChests(new Item(s.a.items.get(i)[0], s.a.exps.get(i), s.a.items.get(i)[1]));
        }
        for (int i = 0; i < s.b.items.size(); i++) {
            takeFromChest(b, s.b.items.get(i)[0], s.b.exps.get(i), s.b.items.get(i)[1]);
            a.addItemToChests(new Item(s.b.items.get(i)[0], s.b.exps.get(i), s.b.items.get(i)[1]));
        }
        if (s.a.xu > 0) { a.updateXu(-s.a.xu); b.updateXu(s.a.xu); }
        if (s.b.xu > 0) { b.updateXu(-s.b.xu); a.updateXu(s.b.xu); }
        done(a, true, "Giao dịch thành công!");
        done(b, true, "Giao dịch thành công!");
    }

    void done(User u, boolean ok, String text) {
        if (u == null) return;
        try {
            Message m = msg(T_DONE);
            m.writer().writeBoolean(ok);
            m.writer().writeUTF(text);
            m.writer().flush();
            send(u, m);
        } catch (IOException ignored) { }
    }

    void cancelTrade(User me, String msgForOther) {
        TradeSession s = SESSIONS.get(me.getId());
        if (s == null) return;
        SESSIONS.remove(s.aId);
        SESSIONS.remove(s.bId);
        User other = UserManager.getInstance().find(s.otherId(me.getId()));
        if (other != null) {
            try {
                Message m = msg(T_CANCEL);
                m.writer().writeUTF(msgForOther);
                m.writer().flush();
                send(other, m);
            } catch (IOException ignored) { }
        }
    }

    void doChat(User me, String text) {
        TradeSession s = SESSIONS.get(me.getId());
        if (s == null || text == null) return;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 120) return;
        User other = UserManager.getInstance().find(s.otherId(me.getId()));
        try {
            Message m = msg(T_CHAT);
            m.writer().writeUTF(displayNameOf(me.getId(), me.getUsername()));
            m.writer().writeUTF(t);
            m.writer().flush();
            send(other, m);
        } catch (IOException ignored) { }
    }
}
