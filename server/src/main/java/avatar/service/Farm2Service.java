package avatar.service;

import avatar.Farm.NongSan;
import avatar.Farm.NongSanDacBiet;
import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [PORT] Farm cấp 2 + Nuôi ong theo bản chính 3.4.4 — phong bì
 * Cmd.FARM2 (−115), byte đầu là lệnh con (docs/farmlvl2-spec.md).
 * 7 hạng mục của DataFarmLvl2: 0 Kho, 1 Chuồng gà, 2 Chuồng bò,
 * 3 Chuồng chó, 4 Bếp, 5 Sản phẩm, 6 Ong.
 */
public class Farm2Service extends Service {

    public static final int F_INFO = 0, F_UPGRADE = 1, F_RESULT = 2,
            F_BEE_BUY = 3, F_BEE_COLLECT = 4, F_BEE_UPGRADE = 5;

    public static final int UP_STORAGE = 0, UP_CHICKEN = 1, UP_COW = 2,
            UP_DOG = 3, UP_COOKING = 4, UP_PRODUCT = 5, UP_BEE = 6;

    public static final int HONEY_ID = 288;
    public static final int BEE_BUY_XU = 50_000;

    /** Nguyên liệu mỗi hạng mục: {{productId, qty}...}. */
    static final int[][][] UPGRADE_MATS = {
            {{215, 10}, {200, 20}},
            {{201, 15}, {202, 15}},
            {{203, 15}, {204, 15}},
            {{248, 10}, {252, 10}},
            {{249, 10}, {253, 10}},
            {{215, 20}},
            {{266, 10}, {267, 10}},
    };
    static final int[] UPGRADE_XU = {30_000, 20_000, 20_000, 20_000, 25_000, 30_000, 40_000};

    public static class State {
        public int[] up = new int[7];
        public int beeLevel = 0;
        public long beeBank = 0;
        public long beeLast = 0;
    }

    private static final Map<Integer, State> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean schemaReady = false;

    public Farm2Service(Session cl) {
        super(cl);
        ensureSchema();
    }

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            try {
                c.createStatement().execute(
                        "ALTER TABLE farm_data ADD COLUMN lvl2_data LONGTEXT");
            } catch (Exception ignored) { }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- trạng thái ----------

    public static State stateOf(int userId) {
        State st = CACHE.get(userId);
        if (st != null) return st;
        st = new State();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT lvl2_data FROM farm_data WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String json = rs.getString(1);
                if (json != null && !json.isEmpty()) {
                    org.json.simple.JSONObject o = (org.json.simple.JSONObject)
                            org.json.simple.JSONValue.parse(json);
                    if (o != null) {
                        org.json.simple.JSONArray up = (org.json.simple.JSONArray) o.get("up");
                        if (up != null) {
                            for (int i = 0; i < Math.min(7, up.size()); i++) {
                                st.up[i] = ((Number) up.get(i)).intValue();
                            }
                        }
                        if (o.get("beeLevel") != null) st.beeLevel = ((Number) o.get("beeLevel")).intValue();
                        if (o.get("beeBank") != null) st.beeBank = ((Number) o.get("beeBank")).longValue();
                        if (o.get("beeLast") != null) st.beeLast = ((Number) o.get("beeLast")).longValue();
                    }
                }
            }
        } catch (Exception ignored) { }
        CACHE.put(userId, st);
        return st;
    }

    static void save(int userId, State st) {
        CACHE.put(userId, st);
        StringBuilder sb = new StringBuilder("{\"up\":[");
        for (int i = 0; i < 7; i++) {
            if (i > 0) sb.append(',');
            sb.append(st.up[i]);
        }
        sb.append("],\"beeLevel\":").append(st.beeLevel)
                .append(",\"beeBank\":").append(st.beeBank)
                .append(",\"beeLast\":").append(st.beeLast).append('}');
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE farm_data SET lvl2_data = ? WHERE user_id = ?")) {
            ps.setString(1, sb.toString());
            ps.setInt(2, userId);
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO farm_data (user_id, lvl2_data) VALUES (?, ?)")) {
                    ins.setInt(1, userId);
                    ins.setString(2, sb.toString());
                    ins.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean upOf(int userId, int idx) {
        return stateOf(userId).up[idx] == 1;
    }

    public static int storageCap(int userId) {
        return upOf(userId, UP_STORAGE) ? 2000 : 500;
    }

    public static int totalNongSan(User u) {
        int t = 0;
        for (NongSan ns : u.NongSan) t += ns.getSoluong();
        for (NongSanDacBiet ns : u.NongSanDacBiet) t += ns.getSoluong();
        return t;
    }

    /** Số lượng còn cộng được vào kho (theo sức chứa). */
    public static int roomFor(User u, int want) {
        int room = storageCap(u.getId()) - totalNongSan(u);
        return Math.max(0, Math.min(want, room));
    }

    /** Giới hạn số con mỗi loài theo hạng chuồng. */
    public static int animalCap(int userId, int species) {
        int idx;
        if (species == 50 || species == 56) idx = UP_CHICKEN;
        else if (species == 51 || species == 52 || species == 55 || species == 58) idx = UP_COW;
        else if (species == 53) idx = UP_DOG;
        else return Integer.MAX_VALUE;
        return upOf(userId, idx) ? 8 : 5;
    }

    static int countProduct(User u, int pid) {
        int n = 0;
        for (NongSan ns : u.NongSan) if (ns.getId() == pid) n += ns.getSoluong();
        for (NongSanDacBiet ns : u.NongSanDacBiet) if (ns.getId() == pid) n += ns.getSoluong();
        return n;
    }

    static void takeProduct(User u, int pid, int qty) {
        int need = qty;
        for (java.util.Iterator<NongSan> it = u.NongSan.iterator(); it.hasNext() && need > 0;) {
            NongSan p = it.next();
            if (p.getId() != pid) continue;
            int take = Math.min(need, p.getSoluong());
            p.setSoluong(p.getSoluong() - take);
            need -= take;
            if (p.getSoluong() <= 0) it.remove();
        }
        for (java.util.Iterator<NongSanDacBiet> it = u.NongSanDacBiet.iterator(); it.hasNext() && need > 0;) {
            NongSanDacBiet p = it.next();
            if (p.getId() != pid) continue;
            int take = Math.min(need, p.getSoluong());
            p.setSoluong(p.getSoluong() - take);
            need -= take;
            if (p.getSoluong() <= 0) it.remove();
        }
    }

    public static void addNongSan(User u, int pid, int qty) {
        if (qty <= 0) return;
        for (NongSan ns : u.NongSan) {
            if (ns.getId() == pid) {
                ns.setSoluong(ns.getSoluong() + qty);
                return;
            }
        }
        u.NongSan.add(new NongSan(pid, qty));
    }

    // ---------- ong ----------

    static int beeCap(State st) { return st.beeLevel * 10; }
    static int beePerH(State st) { return st.beeLevel; }

    static void settleBee(State st) {
        if (st.beeLevel <= 0) return;
        long now = System.currentTimeMillis();
        if (st.beeLast == 0) {
            st.beeLast = now;
            return;
        }
        long hours = (now - st.beeLast) / 3_600_000L;
        if (hours <= 0) return;
        long add = hours * beePerH(st);
        long room = beeCap(st) - st.beeBank;
        if (room > 0) st.beeBank += Math.min(room, add);
        st.beeLast += hours * 3_600_000L;
    }

    // ---------- gói ----------

    static Message msg(int sub) {
        Message m = new Message(Cmd.FARM2);
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
            Message m = msg(F_RESULT);
            DataOutputStream w = m.writer();
            w.writeBoolean(ok);
            w.writeUTF(text == null ? "" : text);
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    void sendInfo(User me) {
        State st = stateOf(me.getId());
        settleBee(st);
        save(me.getId(), st);
        try {
            Message m = msg(F_INFO);
            DataOutputStream w = m.writer();
            for (int i = 0; i < 7; i++) w.writeByte(st.up[i]);
            w.writeByte(st.beeLevel);
            w.writeInt((int) st.beeBank);
            w.writeInt(beeCap(st));
            w.writeInt(beePerH(st));
            w.flush();
            sendMe(m);
        } catch (IOException ignored) { }
    }

    public void onMessage(Message mss) {
        User me = session.user;
        if (me == null) return;
        try {
            DataInputStream dis = mss.reader();
            if (dis.available() < 1) return;
            int sub = dis.readByte();
            switch (sub) {
                case F_INFO: sendInfo(me); break;
                case F_UPGRADE: doUpgrade(me, dis.readByte()); break;
                case F_BEE_BUY: doBeeBuy(me); break;
                case F_BEE_COLLECT: doBeeCollect(me); break;
                case F_BEE_UPGRADE: doBeeUpgrade(me); break;
                default: break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void doUpgrade(User me, int idx) {
        if (idx < 0 || idx >= 7) return;
        State st = stateOf(me.getId());
        if (st.up[idx] == 1) {
            result(false, "Hạng mục này đã nâng cấp rồi.");
            return;
        }
        int[][] mats = UPGRADE_MATS[idx];
        int xu = UPGRADE_XU[idx];
        for (int[] mt : mats) {
            if (countProduct(me, mt[0]) < mt[1]) {
                result(false, "Chưa đủ nguyên liệu trong kho nông sản.");
                return;
            }
        }
        if (me.getXu() < xu) {
            result(false, "Bạn không đủ xu.");
            return;
        }
        for (int[] mt : mats) takeProduct(me, mt[0], mt[1]);
        me.updateXu(-xu);
        st.up[idx] = 1;
        save(me.getId(), st);
        result(true, "Nâng cấp thành công!");
        sendInfo(me);
    }

    void doBeeBuy(User me) {
        State st = stateOf(me.getId());
        if (st.up[UP_BEE] != 1) {
            result(false, "Cần nâng cấp hạng mục Ong ở Farm cấp 2 trước.");
            return;
        }
        if (st.beeLevel > 0) {
            result(false, "Bạn đã có tổ ong rồi.");
            return;
        }
        if (me.getXu() < BEE_BUY_XU) {
            result(false, "Mua tổ ong cần " + BEE_BUY_XU + " xu.");
            return;
        }
        me.updateXu(-BEE_BUY_XU);
        st.beeLevel = 1;
        st.beeBank = 0;
        st.beeLast = System.currentTimeMillis();
        save(me.getId(), st);
        result(true, "Đã mua tổ ong cấp 1 — ong bắt đầu làm mật!");
        sendInfo(me);
    }

    void doBeeCollect(User me) {
        State st = stateOf(me.getId());
        if (st.beeLevel <= 0) {
            result(false, "Bạn chưa có tổ ong.");
            return;
        }
        settleBee(st);
        if (st.beeBank <= 0) {
            result(false, "Chưa có mật để thu — chờ ong làm mật đã.");
            save(me.getId(), st);
            return;
        }
        int take = roomFor(me, (int) st.beeBank);
        if (take <= 0) {
            result(false, "Kho nông sản đã đầy.");
            return;
        }
        addNongSan(me, HONEY_ID, take);
        st.beeBank -= take;
        save(me.getId(), st);
        result(true, "Đã thu " + take + " mật ong vào kho!");
        sendInfo(me);
    }

    void doBeeUpgrade(User me) {
        State st = stateOf(me.getId());
        if (st.beeLevel <= 0) {
            result(false, "Bạn chưa có tổ ong.");
            return;
        }
        if (st.beeLevel >= 5) {
            result(false, "Tổ ong đã đạt cấp tối đa (5).");
            return;
        }
        int xu = st.beeLevel * 30_000;
        int honey = st.beeLevel * 5;
        if (me.getXu() < xu) {
            result(false, "Nâng cấp ong cần " + xu + " xu.");
            return;
        }
        if (countProduct(me, HONEY_ID) < honey) {
            result(false, "Cần " + honey + " mật ong trong kho để nâng cấp.");
            return;
        }
        takeProduct(me, HONEY_ID, honey);
        me.updateXu(-xu);
        settleBee(st);
        st.beeLevel++;
        save(me.getId(), st);
        result(true, "Tổ ong đã lên cấp " + st.beeLevel + "!");
        sendInfo(me);
    }
}
