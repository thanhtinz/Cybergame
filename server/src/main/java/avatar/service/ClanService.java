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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * [PORT] Clan đầy đủ theo bản chính 3.4.4 — phong bì Cmd.CLAN (−112),
 * byte đầu payload là lệnh con theo thứ tự khai báo Cmd_Message của
 * bản chính (xem docs/clan-spec.md). Dữ liệu dùng bảng gốc clans /
 * clan_members (đã có sẵn) + mở rộng level/exp + bảng phụ quest/item.
 *
 * Vai trò (byte): 0 = thành viên, 1 = phó chủ, 2 = chủ bang.
 * role trong DB giữ chữ gốc: 'Chủ Bang' / 'Phó Bang' / 'Thành viên'.
 */
public class ClanService extends Service {

    // ---- lệnh con (sub) ----
    public static final int S_REGIS = 0;
    public static final int S_INFO = 1;
    public static final int S_LIST = 2;
    public static final int S_REQUEST_JOIN = 3;
    public static final int S_LIST_REQUEST = 4;
    public static final int S_ACCEPT = 5;
    public static final int S_DENY = 6;
    public static final int S_OUT = 7;
    public static final int S_KICK = 8;
    public static final int S_CHAT = 9;
    public static final int S_BLOCK_CHAT = 10;
    public static final int S_DONATION = 11;
    public static final int S_CHECK_INFO = 12;
    public static final int S_CHECK_CREATE = 13;
    public static final int S_NOTIFY = 14;
    public static final int S_LEVEL_INFO = 15;
    public static final int S_LEVEL_UP = 16;
    public static final int S_SET_DEPUTY = 17;
    public static final int S_FIRE_DEPUTY = 18;
    public static final int S_QUEST_PING = 19;
    public static final int S_LIST_QUEST = 20;
    public static final int S_INFO_QUEST = 21;
    public static final int S_SUBMIT_QUEST = 22;
    public static final int S_SUBMIT_ITEM = 23;
    public static final int S_QUEST_DONE = 24;
    public static final int S_LIST_NEW = 25;
    public static final int S_SET_PLAYER = 26;
    public static final int S_REQ_ITEMS = 27;
    public static final int S_ITEM_OWN = 28;
    public static final int S_BUY_ITEM = 29;
    public static final int S_RECEIVE = 30;
    public static final int S_INVITE = 31;

    // [PORT] số liệu (bản chính không công khai — docs/clan-spec.md mục 5)
    static final int CREATE_COST_XU = 100000;
    static int expNeed(int level) { return level * 1000; }
    static int levelCostXu(int level) { return level * 50000; }
    static int levelCostLuong(int level) { return level * 10; }
    static int maxMember(int level) { return 30 + (level - 1) * 5; }
    static int maxDeputy(int level) { return 1 + (level >= 3 ? 1 : 0) + (level >= 5 ? 1 : 0); }

    /** Nhiệm vụ clan tĩnh: id, tên, độ khó, part cần nộp, số lượng,
     *  thưởng xu quỹ, điểm exp clan. */
    static final Object[][] QUESTS = {
        {1, "Tiếp tế cá tươi", 1, 444, 3, 25000, 120},
        {2, "Thu gom trứng kiến", 1, 448, 5, 15000, 80},
        {3, "Cần câu dự phòng", 2, 442, 1, 30000, 150},
        {4, "Vé câu cá mập", 2, 460, 1, 50000, 200},
        {5, "Săn cá lớn", 2, 455, 3, 35000, 160},
        {6, "Trang bị cần VIP", 3, 446, 1, 120000, 320},
    };

    /** Shop clan tĩnh: partId, giá xu quỹ, giá lượng quỹ, số ngày
     *  (0 = vĩnh viễn), cấp clan yêu cầu. */
    static final Object[][] SHOP = {
        {2090, 200000, 0, 30, 1},
        {2052, 150000, 0, 30, 1},
        {2053, 150000, 0, 30, 1},
        {2019, 0, 50, 30, 2},
        {2343, 0, 30, 30, 2},
        {126, 100000, 0, 30, 3},
        {127, 100000, 0, 30, 3},
        {2099, 0, 80, 30, 4},
        {2100, 0, 80, 30, 4},
        {620, 0, 120, 0, 5},
    };

    private static volatile boolean schemaReady = false;

    public ClanService(Session cl) {
        super(cl);
        ensureSchema();
    }

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection();
             Statement st = c.createStatement()) {
            try { st.execute("ALTER TABLE clans ADD COLUMN level INT NOT NULL DEFAULT 1"); }
            catch (SQLException ignore) { }
            try { st.execute("ALTER TABLE clans ADD COLUMN exp BIGINT NOT NULL DEFAULT 0"); }
            catch (SQLException ignore) { }
            try { st.execute("ALTER TABLE clan_members ADD COLUMN chat_blocked TINYINT NOT NULL DEFAULT 0"); }
            catch (SQLException ignore) { }
            st.execute("CREATE TABLE IF NOT EXISTS clan_items_own ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, clan_id INT NOT NULL,"
                + " user_id INT NOT NULL, part_id INT NOT NULL,"
                + " bought_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " expire_days INT NOT NULL DEFAULT 30, received TINYINT NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS clan_quest_state ("
                + "clan_id INT NOT NULL, quest_id INT NOT NULL,"
                + " progress INT NOT NULL DEFAULT 0,"
                + " PRIMARY KEY (clan_id))");
            st.execute("CREATE TABLE IF NOT EXISTS clan_invites ("
                + "clan_id INT NOT NULL, user_id INT NOT NULL,"
                + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " PRIMARY KEY (clan_id, user_id))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ---------- dữ liệu ----------

    static class ClanRow {
        int id; int ownerId; String name; int icon; long xu; long luong;
        int level; long exp; String slogan; int maxMembersCfg;
    }

    static class MemberRow {
        int userId; String name; int role; int points; long donXu; long donLuong;
        boolean blocked; boolean online;
    }

    private Connection conn() throws SQLException {
        return DbManager.getInstance().getConnection();
    }

    /** Clan mà user là thành viên (accept = 1); null nếu chưa có. */
    ClanRow myClan(int userId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT cl.* FROM clans cl JOIN clan_members cm ON cm.clan_id = cl.id"
                 + " WHERE cm.user_id = ? AND cm.accept = 1")) {
            ps.setInt(1, userId);
            ResultSet r = ps.executeQuery();
            if (r.next()) return readClan(r);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    ClanRow clanById(int clanId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM clans WHERE id = ?")) {
            ps.setInt(1, clanId);
            ResultSet r = ps.executeQuery();
            if (r.next()) return readClan(r);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private ClanRow readClan(ResultSet r) throws SQLException {
        ClanRow cl = new ClanRow();
        cl.id = r.getInt("id");
        cl.ownerId = r.getInt("users_id");
        cl.name = r.getString("name");
        String ic = r.getString("icon");
        try { cl.icon = ic == null ? 0 : Integer.parseInt(ic.trim()); }
        catch (NumberFormatException e) { cl.icon = 0; }
        cl.xu = r.getLong("xu");
        cl.luong = r.getLong("luong");
        try { cl.level = r.getInt("level"); } catch (SQLException e) { cl.level = 1; }
        try { cl.exp = r.getLong("exp"); } catch (SQLException e) { cl.exp = 0; }
        cl.slogan = r.getString("description");
        if (cl.slogan == null) cl.slogan = "";
        cl.maxMembersCfg = r.getInt("max_members");
        return cl;
    }

    MemberRow memberOf(int clanId, int userId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT cm.*, u.username FROM clan_members cm JOIN users u ON u.id = cm.user_id"
                 + " WHERE cm.clan_id = ? AND cm.user_id = ? AND cm.accept = 1")) {
            ps.setInt(1, clanId);
            ps.setInt(2, userId);
            ResultSet r = ps.executeQuery();
            if (r.next()) return readMember(r);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private MemberRow readMember(ResultSet r) throws SQLException {
        MemberRow m = new MemberRow();
        m.userId = r.getInt("user_id");
        String name = null;
        try {
            // tên nhân vật trong bảng players nếu có
            name = null;
        } catch (Exception ignore) { }
        m.name = r.getString("username");
        String role = r.getString("role");
        m.role = "Chủ Bang".equals(role) ? 2 : ("Phó Bang".equals(role) ? 1 : 0);
        m.points = r.getInt("points");
        m.donXu = r.getLong("xu");
        m.donLuong = r.getLong("luong");
        try { m.blocked = r.getInt("chat_blocked") == 1; } catch (SQLException e) { m.blocked = false; }
        m.online = UserManager.getInstance().find(m.userId) != null;
        return m;
    }

    List<MemberRow> members(int clanId) {
        List<MemberRow> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT cm.*, u.username FROM clan_members cm JOIN users u ON u.id = cm.user_id"
                 + " WHERE cm.clan_id = ? AND cm.accept = 1"
                 + " ORDER BY cm.role DESC, cm.points DESC")) {
            ps.setInt(1, clanId);
            ResultSet r = ps.executeQuery();
            while (r.next()) out.add(readMember(r));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    int memberCount(int clanId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM clan_members WHERE clan_id = ? AND accept = 1")) {
            ps.setInt(1, clanId);
            ResultSet r = ps.executeQuery();
            if (r.next()) return r.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    // ---------- gửi gói ----------

    static Message msg(int sub) {
        Message m = new Message(Cmd.CLAN);
        try { m.writer().writeByte(sub); } catch (IOException e) { }
        return m;
    }

    static void send(User u, Message m) {
        if (u == null || u.session == null) return;
        try { u.session.sendMessage(m); } catch (Exception e) { }
    }

    void sendMe(Message m) {
        if (session != null && session.user != null) send(session.user, m);
    }

    void notifyUser(int userId, int kind, String text) {
        User u = UserManager.getInstance().find(userId);
        if (u == null) return;
        try {
            Message m = msg(S_NOTIFY);
            DataOutputStream w = m.writer();
            w.writeByte(kind);
            w.writeUTF(text);
            w.flush();
            send(u, m);
        } catch (IOException e) { }
    }

    void notifyClanOnline(int clanId, int kind, String text, int exceptUserId) {
        for (MemberRow mb : members(clanId)) {
            if (mb.userId == exceptUserId) continue;
            notifyUser(mb.userId, kind, text);
        }
    }

    // ---------- điểm vào ----------

    public void onMessage(Message ms) {
        User me = session.user;
        if (me == null) return;
        try {
            DataInputStream r = ms.reader();
            if (r.available() < 1) return;
            int sub = r.readByte();
            switch (sub) {
                case S_REGIS: doRegis(me, r); break;
                case S_INFO: sendInfo(me); break;
                case S_LIST: sendList(me, r.readByte()); break;
                case S_LIST_NEW: sendList(me, (byte) 1); break;
                case S_REQUEST_JOIN: doRequestJoin(me, r); break;
                case S_LIST_REQUEST: sendRequests(me); break;
                case S_ACCEPT: doAccept(me, r.readInt(), true); break;
                case S_DENY: doAccept(me, r.readInt(), false); break;
                case S_OUT: doOut(me); break;
                case S_KICK: doKick(me, r.readInt()); break;
                case S_CHAT: doChat(me, r.readUTF()); break;
                case S_BLOCK_CHAT: doBlockChat(me, r.readInt(), r.readBoolean()); break;
                case S_DONATION: doDonation(me, r.readInt(), r.readInt()); break;
                case S_CHECK_INFO: sendCheckInfo(me, r.readInt()); break;
                case S_CHECK_CREATE: doCheckCreate(me, r.readUTF()); break;
                case S_LEVEL_INFO: sendLevelInfo(me); break;
                case S_LEVEL_UP: doLevelUp(me); break;
                case S_SET_DEPUTY: doDeputy(me, r.readInt(), true); break;
                case S_FIRE_DEPUTY: doDeputy(me, r.readInt(), false); break;
                case S_QUEST_PING: sendQuests(me); break;
                case S_LIST_QUEST: sendQuests(me); break;
                case S_INFO_QUEST: sendQuests(me); break;
                case S_SUBMIT_QUEST: doSubmitQuest(me, r.readByte()); break;
                case S_SUBMIT_ITEM: doSubmitItem(me, r.readByte()); break;
                case S_REQ_ITEMS: sendShop(me); break;
                case S_ITEM_OWN: sendOwn(me); break;
                case S_BUY_ITEM: doBuyItem(me, r.readShort()); break;
                case S_RECEIVE: doReceive(me, r.readInt()); break;
                case S_INVITE: doInvite(me, r); break;
                default: break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- tạo / info / danh sách ----------

    private void doCheckCreate(User me, String name) throws IOException {
        String err = validateName(name);
        Message m = msg(S_CHECK_CREATE);
        DataOutputStream w = m.writer();
        w.writeBoolean(err == null);
        w.writeUTF(err == null ? "Tên hợp lệ, có thể tạo clan." : err);
        w.flush();
        sendMe(m);
    }

    private String validateName(String name) {
        if (name == null) return "Tên clan không hợp lệ.";
        name = name.trim();
        if (name.length() < 3 || name.length() > 20) return "Tên clan phải từ 3 đến 20 ký tự.";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM clans WHERE name = ?")) {
            ps.setString(1, name);
            if (ps.executeQuery().next()) return "Tên clan đã có người dùng.";
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void doRegis(User me, DataInputStream r) throws IOException {
        String name = r.readUTF().trim();
        String slogan = r.readUTF().trim();
        int icon = r.readShort();
        if (myClan(me.getId()) != null) {
            serverDialog("Bạn đã ở trong một clan.");
            return;
        }
        String err = validateName(name);
        if (err != null) { serverDialog(err); return; }
        if (me.getXu() < CREATE_COST_XU) {
            serverDialog("Tạo clan cần " + CREATE_COST_XU + " xu trong tài khoản.");
            return;
        }
        try (Connection c = conn()) {
            me.updateXu(-CREATE_COST_XU);
            int newId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clans (users_id, name, icon, xu, luong, max_members, description, level, exp)"
                    + " VALUES (?, ?, ?, 0, 0, 30, ?, 1, 0)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, me.getId());
                ps.setString(2, name);
                ps.setString(3, String.valueOf(icon));
                ps.setString(4, slogan);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                newId = keys.next() ? keys.getInt(1) : -1;
            }
            if (newId < 0) { me.updateXu(CREATE_COST_XU); serverDialog("Tạo clan thất bại."); return; }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_members (clan_id, user_id, role, points, xu, luong, accept)"
                    + " VALUES (?, ?, 'Chủ Bang', 0, 0, 0, 1)")) {
                ps.setInt(1, newId);
                ps.setInt(2, me.getId());
                ps.executeUpdate();
            }
            // xoá các đơn xin vào đang treo của người tạo
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM clan_members WHERE user_id = ? AND accept = 0")) {
                ps.setInt(1, me.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            serverDialog("Tạo clan thất bại.");
            return;
        }
        serverDialog("Đã tạo clan " + name + "!");
        sendInfo(me);
        sendSetPlayer(me);
    }

    void sendSetPlayer(User me) {
        ClanRow cl = myClan(me.getId());
        try {
            Message m = msg(S_SET_PLAYER);
            DataOutputStream w = m.writer();
            w.writeInt(cl == null ? -1 : cl.id);
            w.writeShort(cl == null ? -1 : cl.icon);
            w.flush();
            send(me, m);
        } catch (IOException e) { }
    }

    private void writeClanInfo(DataOutputStream w, ClanRow cl, MemberRow my) throws IOException {
        List<MemberRow> mem = members(cl.id);
        w.writeBoolean(true);
        w.writeInt(cl.id);
        w.writeUTF(cl.name);
        w.writeUTF(cl.slogan);
        w.writeShort(cl.icon);
        w.writeByte(cl.level);
        w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.exp));
        w.writeInt(expNeed(cl.level));
        w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.xu));
        w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.luong));
        w.writeByte(my == null ? 0 : my.role);
        w.writeByte(mem.size());
        w.writeByte(maxMember(cl.level));
        w.writeInt(my == null ? 0 : my.points);
        w.writeInt((int) Math.min(Integer.MAX_VALUE, my == null ? 0 : my.donXu));
        w.writeInt((int) Math.min(Integer.MAX_VALUE, my == null ? 0 : my.donLuong));
        w.writeByte(mem.size());
        for (MemberRow mb : mem) {
            w.writeInt(mb.userId);
            w.writeUTF(displayNameOf(mb.userId, mb.name));
            w.writeByte(mb.role);
            w.writeInt(mb.points);
            w.writeBoolean(mb.online);
            w.writeBoolean(mb.blocked);
        }
    }

    /** Tên hiển thị: players.name nếu có, không thì username. */
    static String displayNameOf(int userId, String username) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT name FROM players WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet r = ps.executeQuery();
            if (r.next()) {
                String n = r.getString(1);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (SQLException e) { }
        return username == null ? ("#" + userId) : username;
    }

    private void sendInfo(User me) throws IOException {
        ClanRow cl = myClan(me.getId());
        Message m = msg(S_INFO);
        DataOutputStream w = m.writer();
        if (cl == null) {
            w.writeBoolean(false);
        } else {
            writeClanInfo(w, cl, memberOf(cl.id, me.getId()));
        }
        w.flush();
        sendMe(m);
    }

    private void sendList(User me, byte kind) throws IOException {
        Message m = msg(kind == 1 ? S_LIST_NEW : S_LIST);
        DataOutputStream w = m.writer();
        List<ClanRow> all = new ArrayList<>();
        String sql = "SELECT * FROM clans ORDER BY " + (kind == 1 ? "id DESC" : "level DESC, exp DESC")
            + " LIMIT 40";
        try (Connection c = conn(); Statement st = c.createStatement()) {
            ResultSet r = st.executeQuery(sql);
            while (r.next()) all.add(readClan(r));
        } catch (SQLException e) { e.printStackTrace(); }
        w.writeByte(all.size());
        for (ClanRow cl : all) {
            w.writeInt(cl.id);
            w.writeUTF(cl.name);
            w.writeUTF(cl.slogan);
            w.writeShort(cl.icon);
            w.writeByte(cl.level);
            w.writeByte(memberCount(cl.id));
            w.writeByte(maxMember(cl.level));
            w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.xu));
            w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.luong));
        }
        w.flush();
        sendMe(m);
    }

    private void sendCheckInfo(User me, int clanId) throws IOException {
        ClanRow cl = clanById(clanId);
        if (cl == null) { serverDialog("Không tìm thấy clan."); return; }
        Message m = msg(S_CHECK_INFO);
        DataOutputStream w = m.writer();
        w.writeInt(cl.id);
        w.writeUTF(cl.name);
        w.writeUTF(cl.slogan);
        w.writeShort(cl.icon);
        w.writeByte(cl.level);
        w.writeByte(memberCount(cl.id));
        w.writeByte(maxMember(cl.level));
        w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.xu));
        w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.luong));
        User owner = null;
        String ownerName = "#" + cl.ownerId;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT username FROM users WHERE id = ?")) {
            ps.setInt(1, cl.ownerId);
            ResultSet r = ps.executeQuery();
            if (r.next()) ownerName = displayNameOf(cl.ownerId, r.getString(1));
        } catch (SQLException e) { }
        w.writeUTF(ownerName);
        w.flush();
        sendMe(m);
    }

    // ---------- thành viên: xin vào / duyệt / mời / rời / kick / phó ----------

    private void doRequestJoin(User me, DataInputStream r) throws IOException {
        int clanId = r.readInt();
        boolean viaInvite = r.available() > 0 && r.readByte() == 1;
        if (myClan(me.getId()) != null) { serverDialog("Bạn đã ở trong một clan."); return; }
        ClanRow cl = clanById(clanId);
        if (cl == null) { serverDialog("Không tìm thấy clan."); return; }
        if (memberCount(clanId) >= maxMember(cl.level)) {
            serverDialog("Clan đã đủ thành viên.");
            return;
        }
        try (Connection c = conn()) {
            if (viaInvite) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT clan_id FROM clan_invites WHERE clan_id = ? AND user_id = ?")) {
                    ps.setInt(1, clanId);
                    ps.setInt(2, me.getId());
                    if (!ps.executeQuery().next()) {
                        serverDialog("Lời mời không còn hiệu lực.");
                        return;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM clan_invites WHERE clan_id = ? AND user_id = ?")) {
                    ps.setInt(1, clanId);
                    ps.setInt(2, me.getId());
                    ps.executeUpdate();
                }
                addMember(c, clanId, me.getId());
                serverDialog("Bạn đã gia nhập clan " + cl.name + "!");
                notifyClanOnline(clanId, 7, me.getDisplayName() + " vừa gia nhập clan.", me.getId());
                sendInfo(me);
                sendSetPlayer(me);
                return;
            }
            // xin vào thường: ghi dòng chờ duyệt (accept = 0)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM clan_members WHERE clan_id = ? AND user_id = ?")) {
                ps.setInt(1, clanId);
                ps.setInt(2, me.getId());
                if (ps.executeQuery().next()) {
                    serverDialog("Bạn đã gửi yêu cầu tới clan này rồi.");
                    return;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_members (clan_id, user_id, role, points, xu, luong, accept)"
                    + " VALUES (?, ?, 'Thành viên', 0, 0, 0, 0)")) {
                ps.setInt(1, clanId);
                ps.setInt(2, me.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); serverDialog("Gửi yêu cầu thất bại."); return; }
        serverDialog("Đã gửi yêu cầu gia nhập clan " + cl.name + ".");
        // báo chủ + phó đang online
        for (MemberRow mb : members(clanId)) {
            if (mb.role >= 1) notifyUser(mb.userId, 4, me.getDisplayName() + " xin gia nhập clan.");
        }
    }

    private void addMember(Connection c, int clanId, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clan_members (clan_id, user_id, role, points, xu, luong, accept)"
                + " VALUES (?, ?, 'Thành viên', 0, 0, 0, 1)")) {
            ps.setInt(1, clanId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM clan_members WHERE user_id = ? AND accept = 0 AND clan_id <> ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, clanId);
            ps.executeUpdate();
        }
    }

    private void sendRequests(User me) throws IOException {
        ClanRow cl = myClan(me.getId());
        Message m = msg(S_LIST_REQUEST);
        DataOutputStream w = m.writer();
        List<MemberRow> req = new ArrayList<>();
        if (cl != null) {
            MemberRow my = memberOf(cl.id, me.getId());
            if (my != null && my.role >= 1) {
                try (Connection c = conn();
                     PreparedStatement ps = c.prepareStatement(
                         "SELECT cm.*, u.username FROM clan_members cm JOIN users u ON u.id = cm.user_id"
                         + " WHERE cm.clan_id = ? AND cm.accept = 0")) {
                    ps.setInt(1, cl.id);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) req.add(readMember(rs));
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
        w.writeByte(req.size());
        for (MemberRow mb : req) {
            w.writeInt(mb.userId);
            w.writeUTF(displayNameOf(mb.userId, mb.name));
        }
        w.flush();
        sendMe(m);
    }

    private void doAccept(User me, int targetId, boolean ok) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        MemberRow my = memberOf(cl.id, me.getId());
        if (my == null || my.role < 1) { serverDialog("Chỉ chủ bang và phó chủ mới duyệt được."); return; }
        try (Connection c = conn()) {
            if (ok) {
                if (memberCount(cl.id) >= maxMember(cl.level)) {
                    serverDialog("Clan đã đủ thành viên.");
                    return;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan_members SET accept = 1 WHERE clan_id = ? AND user_id = ? AND accept = 0")) {
                    ps.setInt(1, cl.id);
                    ps.setInt(2, targetId);
                    if (ps.executeUpdate() == 0) { serverDialog("Yêu cầu không còn."); return; }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM clan_members WHERE user_id = ? AND accept = 0 AND clan_id <> ?")) {
                    ps.setInt(1, targetId);
                    ps.setInt(2, cl.id);
                    ps.executeUpdate();
                }
                notifyUser(targetId, 1, "Bạn đã được duyệt vào clan " + cl.name + "!");
                User t = UserManager.getInstance().find(targetId);
                if (t != null) { ClanService cs = new ClanService(t.session); cs.sendSetPlayer(t); }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM clan_members WHERE clan_id = ? AND user_id = ? AND accept = 0")) {
                    ps.setInt(1, cl.id);
                    ps.setInt(2, targetId);
                    ps.executeUpdate();
                }
                notifyUser(targetId, 2, "Clan " + cl.name + " đã từ chối yêu cầu của bạn.");
            }
        } catch (SQLException e) { e.printStackTrace(); return; }
        sendRequests(me);
        sendInfo(me);
    }

    private void doInvite(User me, DataInputStream r) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) { serverDialog("Bạn chưa có clan."); return; }
        MemberRow my = memberOf(cl.id, me.getId());
        if (my == null || my.role < 1) { serverDialog("Chỉ chủ bang và phó chủ mới mời được."); return; }
        String targetName = r.readUTF().trim();
        int targetId = -1;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT u.id FROM users u LEFT JOIN players p ON p.user_id = u.id"
                 + " WHERE u.username = ? OR p.name = ? LIMIT 1")) {
            ps.setString(1, targetName);
            ps.setString(2, targetName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) targetId = rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        if (targetId < 0) { serverDialog("Không tìm thấy người chơi " + targetName + "."); return; }
        if (myClan(targetId) != null) { serverDialog("Người chơi đã có clan."); return; }
        User t = UserManager.getInstance().find(targetId);
        if (t == null) { serverDialog("Người chơi không trực tuyến."); return; }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO clan_invites (clan_id, user_id) VALUES (?, ?)"
                 + " ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP")) {
            ps.setInt(1, cl.id);
            ps.setInt(2, targetId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return; }
        try {
            Message m = msg(S_NOTIFY);
            DataOutputStream w = m.writer();
            w.writeByte(6);
            w.writeUTF("Clan " + cl.name + " mời bạn gia nhập. Mở panel Clan để đồng ý.");
            w.writeInt(cl.id);
            w.flush();
            send(t, m);
        } catch (IOException e) { }
        serverDialog("Đã gửi lời mời tới " + targetName + ".");
    }

    private void doOut(User me) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        MemberRow my = memberOf(cl.id, me.getId());
        List<MemberRow> before = members(cl.id); // chụp danh sách trước khi xoá
        try (Connection c = conn()) {
            if (my != null && my.role == 2) {
                // chủ bang rời = giải tán clan [PORT]
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_members WHERE clan_id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_items_own WHERE clan_id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_quest_state WHERE clan_id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_invites WHERE clan_id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM clans WHERE id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.executeUpdate();
                }
                for (MemberRow mb : before) {
                    if (mb.userId != me.getId()) {
                        notifyUser(mb.userId, 3, "Clan " + cl.name + " đã giải tán.");
                    }
                }
                serverDialog("Bạn đã giải tán clan " + cl.name + ".");
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM clan_members WHERE clan_id = ? AND user_id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.setInt(2, me.getId());
                    ps.executeUpdate();
                }
                notifyClanOnline(cl.id, 7, me.getDisplayName() + " đã rời clan.", me.getId());
                serverDialog("Bạn đã rời clan " + cl.name + ".");
            }
        } catch (SQLException e) { e.printStackTrace(); return; }
        sendInfo(me);
        sendSetPlayer(me);
    }

    private void doKick(User me, int targetId) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null || targetId == me.getId()) return;
        MemberRow my = memberOf(cl.id, me.getId());
        MemberRow tg = memberOf(cl.id, targetId);
        if (my == null || tg == null) return;
        if (my.role < 1 || tg.role >= my.role) {
            serverDialog("Bạn không có quyền trục xuất người này.");
            return;
        }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM clan_members WHERE clan_id = ? AND user_id = ?")) {
            ps.setInt(1, cl.id);
            ps.setInt(2, targetId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return; }
        notifyUser(targetId, 3, "Bạn đã bị trục xuất khỏi clan " + cl.name + ".");
        User t = UserManager.getInstance().find(targetId);
        if (t != null) { ClanService cs = new ClanService(t.session); cs.sendSetPlayer(t); cs.sendInfo(t); }
        sendInfo(me);
    }

    private void doDeputy(User me, int targetId, boolean set) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        MemberRow my = memberOf(cl.id, me.getId());
        MemberRow tg = memberOf(cl.id, targetId);
        if (my == null || my.role != 2 || tg == null) {
            serverDialog("Chỉ chủ bang mới phong/cách chức phó chủ được.");
            return;
        }
        if (set) {
            int deputies = 0;
            for (MemberRow mb : members(cl.id)) if (mb.role == 1) deputies++;
            if (tg.role == 1) { serverDialog("Người này đã là phó chủ."); return; }
            if (deputies >= maxDeputy(cl.level)) {
                serverDialog("Clan cấp " + cl.level + " chỉ có tối đa " + maxDeputy(cl.level) + " phó chủ.");
                return;
            }
        }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE clan_members SET role = ? WHERE clan_id = ? AND user_id = ?")) {
            ps.setString(1, set ? "Phó Bang" : "Thành viên");
            ps.setInt(2, cl.id);
            ps.setInt(3, targetId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return; }
        notifyUser(targetId, 7, set
            ? "Bạn được phong làm Phó chủ clan " + cl.name + "."
            : "Bạn không còn là Phó chủ clan " + cl.name + ".");
        sendInfo(me);
    }

    // ---------- chat + góp quỹ + cấp clan ----------

    private void doChat(User me, String text) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        MemberRow my = memberOf(cl.id, me.getId());
        if (my == null) return;
        if (my.blocked) { serverDialog("Bạn đang bị chặn chat clan."); return; }
        text = text == null ? "" : text.trim();
        if (text.isEmpty() || text.length() > 120) return;
        for (MemberRow mb : members(cl.id)) {
            User u = UserManager.getInstance().find(mb.userId);
            if (u == null) continue;
            try {
                Message m = msg(S_CHAT);
                DataOutputStream w = m.writer();
                w.writeInt(me.getId());
                w.writeUTF(me.getDisplayName());
                w.writeUTF(text);
                w.flush();
                send(u, m);
            } catch (IOException e) { }
        }
    }

    private void doBlockChat(User me, int targetId, boolean block) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        MemberRow my = memberOf(cl.id, me.getId());
        MemberRow tg = memberOf(cl.id, targetId);
        if (my == null || tg == null || my.role < 1 || tg.role >= my.role) {
            serverDialog("Bạn không có quyền chặn chat người này.");
            return;
        }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE clan_members SET chat_blocked = ? WHERE clan_id = ? AND user_id = ?")) {
            ps.setInt(1, block ? 1 : 0);
            ps.setInt(2, cl.id);
            ps.setInt(3, targetId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return; }
        notifyUser(targetId, 7, block ? "Bạn bị chặn chat clan." : "Bạn được mở lại chat clan.");
        sendInfo(me);
    }

    private void doDonation(User me, int xu, int luong) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        if (xu < 0 || luong < 0 || (xu == 0 && luong == 0)) return;
        if (me.getXu() < xu) { serverDialog("Bạn không đủ xu."); return; }
        if (me.getLuong() < luong) { serverDialog("Bạn không đủ lượng."); return; }
        me.updateXu(-xu);
        me.updateLuong(-luong);
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clans SET xu = xu + ?, luong = luong + ? WHERE id = ?")) {
                ps.setLong(1, xu);
                ps.setLong(2, luong);
                ps.setInt(3, cl.id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clan_members SET xu = xu + ?, luong = luong + ? WHERE clan_id = ? AND user_id = ?")) {
                ps.setLong(1, xu);
                ps.setLong(2, luong);
                ps.setInt(3, cl.id);
                ps.setInt(4, me.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            me.updateXu(xu);
            me.updateLuong(luong);
            serverDialog("Góp quỹ thất bại.");
            return;
        }
        serverDialog("Đã góp " + xu + " xu và " + luong + " lượng vào quỹ clan.");
        notifyClanOnline(cl.id, 7, me.getDisplayName() + " vừa góp quỹ clan.", me.getId());
        sendInfo(me);
    }

    private void sendLevelInfo(User me) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        Message m = msg(S_LEVEL_INFO);
        DataOutputStream w = m.writer();
        w.writeByte(cl.level);
        w.writeInt((int) Math.min(Integer.MAX_VALUE, cl.exp));
        w.writeInt(expNeed(cl.level));
        w.writeInt(levelCostXu(cl.level));
        w.writeInt(levelCostLuong(cl.level));
        w.writeByte(maxMember(cl.level + 1));
        w.writeByte(maxDeputy(cl.level + 1));
        w.flush();
        sendMe(m);
    }

    private void doLevelUp(User me) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        MemberRow my = memberOf(cl.id, me.getId());
        if (my == null || my.role < 1) { serverDialog("Chỉ chủ bang và phó chủ mới nâng cấp clan được."); return; }
        if (cl.exp < expNeed(cl.level)) { serverDialog("Clan chưa đủ điểm để lên cấp."); return; }
        if (cl.xu < levelCostXu(cl.level) || cl.luong < levelCostLuong(cl.level)) {
            serverDialog("Quỹ clan chưa đủ chi phí lên cấp.");
            return;
        }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE clans SET level = level + 1, exp = exp - ?, xu = xu - ?, luong = luong - ? WHERE id = ?")) {
            ps.setInt(1, expNeed(cl.level));
            ps.setInt(2, levelCostXu(cl.level));
            ps.setInt(3, levelCostLuong(cl.level));
            ps.setInt(4, cl.id);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return; }
        notifyClanOnline(cl.id, 5, "Clan đã lên cấp " + (cl.level + 1) + "!", -1);
        sendInfo(me);
    }

    // ---------- nhiệm vụ clan ----------

    private int[] questState(int clanId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT quest_id, progress FROM clan_quest_state WHERE clan_id = ?")) {
            ps.setInt(1, clanId);
            ResultSet r = ps.executeQuery();
            if (r.next()) return new int[]{r.getInt(1), r.getInt(2)};
        } catch (SQLException e) { e.printStackTrace(); }
        return new int[]{0, 0};
    }

    private void sendQuests(User me) throws IOException {
        ClanRow cl = myClan(me.getId());
        Message m = msg(S_LIST_QUEST);
        DataOutputStream w = m.writer();
        int[] st = cl == null ? new int[]{0, 0} : questState(cl.id);
        w.writeByte(st[0]);
        w.writeShort(st[1]);
        w.writeByte(QUESTS.length);
        for (Object[] q : QUESTS) {
            w.writeByte((Integer) q[0]);
            w.writeUTF((String) q[1]);
            w.writeByte((Integer) q[2]);
            w.writeShort((Integer) q[3]);
            w.writeByte((Integer) q[4]);
            w.writeInt((Integer) q[5]);
            w.writeInt((Integer) q[6]);
        }
        w.flush();
        sendMe(m);
    }

    private Object[] questById(int id) {
        for (Object[] q : QUESTS) if ((Integer) q[0] == id) return q;
        return null;
    }

    private void doSubmitQuest(User me, int questId) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        if (questById(questId) == null) return;
        int[] st = questState(cl.id);
        if (st[0] != 0) { serverDialog("Clan đang có nhiệm vụ khác chưa hoàn thành."); return; }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO clan_quest_state (clan_id, quest_id, progress) VALUES (?, ?, 0)"
                 + " ON DUPLICATE KEY UPDATE quest_id = VALUES(quest_id), progress = 0")) {
            ps.setInt(1, cl.id);
            ps.setInt(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return; }
        notifyClanOnline(cl.id, 7, me.getDisplayName() + " đã nhận nhiệm vụ clan.", me.getId());
        sendQuests(me);
    }

    private void doSubmitItem(User me, int questId) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        int[] st = questState(cl.id);
        if (st[0] != questId) { serverDialog("Nhiệm vụ này chưa được nhận."); return; }
        Object[] q = questById(questId);
        int needPart = (Integer) q[3];
        int needQty = (Integer) q[4];
        int remain = needQty - st[1];
        if (remain <= 0) return;
        // đếm + trừ vật phẩm trong rương
        int have = 0;
        synchronized (me.chests) {
            for (Item it : me.chests) if (it.getId() == needPart) have += Math.max(1, it.getQuantity());
        }
        if (have <= 0) { serverDialog("Bạn không có vật phẩm cần nộp trong rương."); return; }
        int submit = Math.min(have, remain);
        int left = submit;
        synchronized (me.chests) {
            for (int i = me.chests.size() - 1; i >= 0 && left > 0; i--) {
                Item it = me.chests.get(i);
                if (it.getId() != needPart) continue;
                int qn = Math.max(1, it.getQuantity());
                if (qn <= left) {
                    left -= qn;
                    me.chests.remove(i);
                } else {
                    it.setQuantity(qn - left);
                    left = 0;
                }
            }
        }
        int submitted = submit - left;
        int newProgress = st[1] + submitted;
        int pointsShare = (Integer) q[6] * submitted / needQty;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clan_members SET points = points + ? WHERE clan_id = ? AND user_id = ?")) {
                ps.setInt(1, pointsShare);
                ps.setInt(2, cl.id);
                ps.setInt(3, me.getId());
                ps.executeUpdate();
            }
            if (newProgress >= needQty) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM clan_quest_state WHERE clan_id = ?")) {
                    ps.setInt(1, cl.id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clans SET xu = xu + ?, exp = exp + ? WHERE id = ?")) {
                    ps.setInt(1, (Integer) q[5]);
                    ps.setInt(2, (Integer) q[6]);
                    ps.setInt(3, cl.id);
                    ps.executeUpdate();
                }
                // phát QUEST_DONE cho cả clan online
                for (MemberRow mb : members(cl.id)) {
                    User u = UserManager.getInstance().find(mb.userId);
                    if (u == null) continue;
                    try {
                        Message done = msg(S_QUEST_DONE);
                        DataOutputStream dw = done.writer();
                        dw.writeByte(questId);
                        dw.writeInt((Integer) q[5]);
                        dw.writeInt((Integer) q[6]);
                        dw.flush();
                        send(u, done);
                    } catch (IOException e) { }
                }
                serverDialog("Hoàn thành nhiệm vụ! Quỹ + " + q[5] + " xu, điểm clan + " + q[6] + ".");
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan_quest_state SET progress = ? WHERE clan_id = ?")) {
                    ps.setInt(1, newProgress);
                    ps.setInt(2, cl.id);
                    ps.executeUpdate();
                }
                serverDialog("Đã nộp " + submitted + " vật phẩm (" + newProgress + "/" + needQty + ").");
            }
        } catch (SQLException e) { e.printStackTrace(); return; }
        sendQuests(me);
        sendInfo(me);
    }

    // ---------- shop clan ----------

    private void sendShop(User me) throws IOException {
        Message m = msg(S_REQ_ITEMS);
        DataOutputStream w = m.writer();
        w.writeByte(SHOP.length);
        for (Object[] s : SHOP) {
            w.writeShort((Integer) s[0]);
            w.writeInt((Integer) s[1]);
            w.writeInt((Integer) s[2]);
            w.writeByte((Integer) s[3]);
            w.writeByte((Integer) s[4]);
        }
        w.flush();
        sendMe(m);
    }

    private void sendOwn(User me) throws IOException {
        Message m = msg(S_ITEM_OWN);
        DataOutputStream w = m.writer();
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, part_id, expire_days, received FROM clan_items_own"
                 + " WHERE user_id = ? ORDER BY id DESC")) {
            ps.setInt(1, me.getId());
            ResultSet r = ps.executeQuery();
            while (r.next()) {
                rows.add(new Object[]{r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4)});
            }
        } catch (SQLException e) { e.printStackTrace(); }
        w.writeByte(rows.size());
        for (Object[] row : rows) {
            w.writeInt((Integer) row[0]);
            w.writeShort((Integer) row[1]);
            w.writeInt((Integer) row[2]);
            w.writeBoolean((Integer) row[3] == 1);
        }
        w.flush();
        sendMe(m);
    }

    private Object[] shopByPart(int partId) {
        for (Object[] s : SHOP) if ((Integer) s[0] == partId) return s;
        return null;
    }

    private void doBuyItem(User me, int partId) throws IOException {
        ClanRow cl = myClan(me.getId());
        if (cl == null) return;
        Object[] s = shopByPart(partId);
        if (s == null) return;
        int priceXu = (Integer) s[1];
        int priceLuong = (Integer) s[2];
        int days = (Integer) s[3];
        int lvReq = (Integer) s[4];
        if (cl.level < lvReq) {
            serverDialog("Cần clan cấp " + lvReq + " mới mua được món này.");
            return;
        }
        if (cl.xu < priceXu || cl.luong < priceLuong) {
            serverDialog("Quỹ clan không đủ để mua món này.");
            return;
        }
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clans SET xu = xu - ?, luong = luong - ? WHERE id = ?")) {
                ps.setInt(1, priceXu);
                ps.setInt(2, priceLuong);
                ps.setInt(3, cl.id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_items_own (clan_id, user_id, part_id, expire_days, received)"
                    + " VALUES (?, ?, ?, ?, 0)")) {
                ps.setInt(1, cl.id);
                ps.setInt(2, me.getId());
                ps.setInt(3, partId);
                ps.setInt(4, days);
                ps.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); return; }
        serverDialog("Đã mua đồ clan bằng quỹ. Vào tab Đồ clan để nhận.");
        sendOwn(me);
        sendInfo(me);
    }

    private void doReceive(User me, int ownId) throws IOException {
        int partId = -1;
        int days = 0;
        int clanId = -1;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT clan_id, part_id, expire_days FROM clan_items_own"
                 + " WHERE id = ? AND user_id = ? AND received = 0")) {
            ps.setInt(1, ownId);
            ps.setInt(2, me.getId());
            ResultSet r = ps.executeQuery();
            if (r.next()) {
                clanId = r.getInt(1);
                partId = r.getInt(2);
                days = r.getInt(3);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        if (partId < 0) { serverDialog("Món đồ không còn hoặc đã nhận."); return; }
        long expire = days <= 0 ? -1L : System.currentTimeMillis() + days * 86400000L;
        me.addItemToChests(new Item(partId, expire, 1));
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE clan_items_own SET received = 1 WHERE id = ?")) {
            ps.setInt(1, ownId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
        serverDialog("Đã nhận đồ vào rương.");
        sendOwn(me);
    }
}
