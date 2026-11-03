package avatar.admin;

import avatar.db.DbManager;
import avatar.item.PartManager;
import avatar.lucky.DialLuckyManager;
import avatar.model.Npc;
import avatar.model.User;
import avatar.service.MailService;
import avatar.play.MapManager;
import avatar.play.NpcManager;
import avatar.server.ServerManager;
import avatar.server.UserManager;
import avatar.server.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/** Trang ADMIN quản trị game (yêu cầu của chủ dự án 2026-11-01): một
 *  HTTP server nhỏ chạy CÙNG tiến trình game server (JDK có sẵn
 *  com.sun.net.httpserver, không thêm thư viện), phục vụ giao diện web
 *  ở thư mục admin-web/ và API JSON ở /api/*.
 *
 *  Đăng nhập bằng TÀI KHOẢN GAME có role 0 hoặc 1 (mật khẩu MD5
 *  như game; chủ dự án chốt 2026-11-01: admin là role 0/1, người
 *  chơi thường role -1). Tài khoản admin đầu tiên:
 *  UPDATE users SET role = 1 WHERE username = '...';
 *
 *  Cổng mặc định 19129, đổi bằng key admin_port trong database.properties.
 *  Mọi thay đổi (giá/shop/NPC/giftcode/cờ) ghi thẳng vào MariaDB và nạp
 *  lại vào game ngay khi có thể (PartManager.load, loadNpcData,
 *  DialLucky.load, GiftCodeService tự nạp mỗi lần dùng). */
public class AdminServer {

    private static final Map<String, Long> TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, String> TOKEN_USER = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL = 12L * 3600 * 1000;
    private static long startTime = System.currentTimeMillis();

    public static void start(int port) {
        try {
            ensureSchema();
            HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/api/", AdminServer::handleApi);
            http.createContext("/", AdminServer::handleStatic);
            http.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            http.start();
            System.out.println("[Admin] Trang quan tri chay tai http://0.0.0.0:" + port + "/");
        } catch (Exception e) {
            System.out.println("[Admin] Khong mo duoc trang quan tri: " + e.getMessage());
        }
    }

    /** Bổ sung cột còn thiếu cho tính năng admin (idempotent). */
    public static void ensureSchema() {
        try (Connection c = DbManager.getInstance().getConnection();
             Statement st = c.createStatement()) {
            try {
                st.execute("ALTER TABLE `npc` ADD COLUMN `active` TINYINT NOT NULL DEFAULT 1");
            } catch (Exception ignore) {
                // cột đã tồn tại
            }
            try {
                // Tên nhân vật (màn Tạo nhân vật của bản port)
                st.execute("ALTER TABLE `players` ADD COLUMN `name` VARCHAR(32) NOT NULL DEFAULT ''");
            } catch (Exception ignore) {
                // cột đã tồn tại
            }
            try {
                // Trạng thái nấu ăn của farm (gói 91/92 của bản port)
                st.execute("ALTER TABLE `farm_data` ADD COLUMN `food_id` INT NOT NULL DEFAULT 0");
            } catch (Exception ignore) {
                // cột đã tồn tại
            }
            try {
                st.execute("ALTER TABLE `farm_data` ADD COLUMN `cook_finish` BIGINT NOT NULL DEFAULT 0");
            } catch (Exception ignore) {
                // cột đã tồn tại
            }
            // Bảng bạn bè (hệ bạn bè của bản port): mỗi quan hệ lưu 2 chiều
            st.execute("CREATE TABLE IF NOT EXISTS `friends` ("
                    + "`user_id` INT NOT NULL, `friend_id` INT NOT NULL,"
                    + " PRIMARY KEY (`user_id`, `friend_id`))");
            // Bảng thư (Hòm Thư của bản port): thư hệ thống/bạn bè kèm
            // vật phẩm đính kèm dạng JSON [{t:'xu'|'luong'|'item', n, id}]
            st.execute("CREATE TABLE IF NOT EXISTS `daily_claims` ("
                    + "`user_id` INT NOT NULL, `day` DATE NOT NULL,"
                    + " PRIMARY KEY (`user_id`, `day`))");
            st.execute("CREATE TABLE IF NOT EXISTS `letters` ("
                    + "`id` INT NOT NULL AUTO_INCREMENT,"
                    + "`user_id` INT NOT NULL,"
                    + "`kind` VARCHAR(16) NOT NULL DEFAULT 'system',"
                    + "`title` VARCHAR(255) NOT NULL,"
                    + "`sender` VARCHAR(64) NOT NULL DEFAULT 'Hệ Thống',"
                    + "`body` TEXT,"
                    + "`attachments` TEXT,"
                    + "`is_read` TINYINT NOT NULL DEFAULT 0,"
                    + "`claimed` TINYINT NOT NULL DEFAULT 0,"
                    + "`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " PRIMARY KEY (`id`), KEY `idx_user` (`user_id`))");
        } catch (Exception e) {
            System.out.println("[Admin] ensureSchema loi: " + e.getMessage());
        }
    }

    // ======================== HTTP helpers ========================

    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (!path.matches("^/[A-Za-z0-9_.-]+$")) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }
        Path file = Paths.get("admin-web", path.substring(1));
        if (!Files.exists(file)) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }
        String type = path.endsWith(".css") ? "text/css"
            : path.endsWith(".js") ? "application/javascript" : "text/html";
        send(ex, 200, type, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange ex, int code, JSONObject obj) throws IOException {
        send(ex, code, "application/json", obj.toJSONString());
    }

    private static JSONObject readJson(HttpExchange ex) throws Exception {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) return new JSONObject();
        return (JSONObject) new JSONParser().parse(body);
    }

    private static boolean auth(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Admin-Token");
        if (token == null) return false;
        Long exp = TOKENS.get(token);
        if (exp == null || exp < System.currentTimeMillis()) return false;
        return true;
    }

    private static JSONObject err(String msg) {
        JSONObject o = new JSONObject();
        o.put("ok", false);
        o.put("error", msg);
        return o;
    }

    private static JSONObject ok() {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        return o;
    }

    private static long num(JSONObject o, String key, long def) {
        Object v = o.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static String str(JSONObject o, String key, String def) {
        Object v = o.get(key);
        return v == null ? def : v.toString();
    }

    private static java.util.Map<String, String> query(HttpExchange ex) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    m.put(java.net.URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                          java.net.URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return m;
    }

    // ======================== API router ========================

    private static void handleApi(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/login")) {
                login(ex);
                return;
            }
            if (!auth(ex)) {
                sendJson(ex, 401, err("Chưa đăng nhập hoặc phiên đã hết hạn"));
                return;
            }
            switch (path) {
                case "/api/overview": overview(ex); break;
                case "/api/players": players(ex); break;
                case "/api/players/money": playerMoney(ex); break;
                case "/api/players/ban": playerBan(ex); break;
                case "/api/players/role": playerRole(ex); break;
                case "/api/items": items(ex); break;
                case "/api/items/update": itemUpdate(ex); break;
                case "/api/items/create": itemCreate(ex); break;
                case "/api/items/delete": itemDelete(ex); break;
                case "/api/npcs": npcs(ex); break;
                case "/api/npcs/update": npcUpdate(ex); break;
                case "/api/npcs/create": npcCreate(ex); break;
                case "/api/npcs/delete": npcDelete(ex); break;
                case "/api/wheel": wheel(ex); break;
                case "/api/wheel/update": wheelUpdate(ex); break;
                case "/api/wheel/create": wheelCreate(ex); break;
                case "/api/wheel/delete": wheelDelete(ex); break;
                case "/api/giftcodes": giftcodes(ex); break;
                case "/api/giftcodes/save": giftcodeSave(ex); break;
                case "/api/giftcodes/delete": giftcodeDelete(ex); break;
                case "/api/mail/send": mailSend(ex); break;
                case "/api/flags": flags(ex); break;
                case "/api/flags/set": flagSet(ex); break;
                case "/api/announce": announce(ex); break;
                default: sendJson(ex, 404, err("Không có API này"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, err("Lỗi server: " + e.getMessage()));
        }
    }

    private static void login(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        String user = str(body, "username", "").trim();
        String pass = str(body, "password", "");
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, password, role FROM users WHERE username = ?")) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && Utils.md5(pass).equals(rs.getString("password"))) {
                    int role = rs.getInt("role");
                    if (role != 0 && role != 1) {
                        sendJson(ex, 403, err("Tài khoản này không có quyền admin (cần role 0 hoặc 1)"));
                        return;
                    }
                    String token = UUID.randomUUID().toString();
                    TOKENS.put(token, System.currentTimeMillis() + TOKEN_TTL);
                    TOKEN_USER.put(token, user);
                    JSONObject o = ok();
                    o.put("token", token);
                    o.put("username", user);
                    sendJson(ex, 200, o);
                    return;
                }
            }
        }
        sendJson(ex, 401, err("Sai tài khoản hoặc mật khẩu"));
    }

    private static void overview(HttpExchange ex) throws Exception {
        JSONObject o = ok();
        try (Connection c = DbManager.getInstance().getConnection();
             Statement st = c.createStatement()) {
            o.put("users", count(st, "SELECT COUNT(*) FROM users"));
            o.put("playersOnline", count(st, "SELECT COUNT(*) FROM players WHERE is_online = 1"));
            o.put("items", count(st, "SELECT COUNT(*) FROM items"));
            o.put("itemsInShop", count(st, "SELECT COUNT(*) FROM items WHERE sell IN (8, 14)"));
            o.put("npcs", count(st, "SELECT COUNT(*) FROM npc"));
            o.put("npcsActive", count(st, "SELECT COUNT(*) FROM npc WHERE active = 1"));
            o.put("giftcodes", count(st, "SELECT COUNT(*) FROM giftcode WHERE end_time >= NOW()"));
            o.put("onlineNow", UserManager.users.size());
            o.put("uptimeMin", (System.currentTimeMillis() - startTime) / 60000);
            try (ResultSet rs = st.executeQuery(
                    "SELECT value FROM settings WHERE name = 'thong_bao'")) {
                o.put("notify", rs.next() ? rs.getString(1) : "");
            }
        }
        sendJson(ex, 200, o);
    }

    private static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ======================== Người chơi ========================

    private static void players(HttpExchange ex) throws Exception {
        java.util.Map<String, String> q = query(ex);
        String kw = q.getOrDefault("q", "").trim();
        int page = Math.max(1, Integer.parseInt(q.getOrDefault("page", "1")));
        int size = 20;
        JSONObject o = ok();
        JSONArray arr = new JSONArray();
        String where = kw.isEmpty() ? "" : "WHERE u.username LIKE ?";
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM users u " + where)) {
                if (!kw.isEmpty()) ps.setString(1, "%" + kw + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    o.put("total", rs.next() ? rs.getLong(1) : 0);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT u.id, u.username, u.role, u.ban, u.login_lock, u.timeCreate, "
                    + "p.xu, p.luong, p.luong_khoa, p.xeng, p.is_online, p.gender "
                    + "FROM users u LEFT JOIN players p ON p.user_id = u.id " + where
                    + " ORDER BY u.id ASC LIMIT ? OFFSET ?")) {
                int i = 1;
                if (!kw.isEmpty()) ps.setString(i++, "%" + kw + "%");
                ps.setInt(i++, size);
                ps.setInt(i, (page - 1) * size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject p = new JSONObject();
                        p.put("id", rs.getInt("id"));
                        p.put("username", rs.getString("username"));
                        p.put("role", rs.getInt("role"));
                        p.put("ban", rs.getString("ban"));
                        p.put("loginLock", rs.getInt("login_lock"));
                        p.put("timeCreate", String.valueOf(rs.getDate("timeCreate")));
                        p.put("xu", rs.getLong("xu"));
                        p.put("luong", rs.getInt("luong"));
                        p.put("luongKhoa", rs.getInt("luong_khoa"));
                        p.put("xeng", rs.getInt("xeng"));
                        p.put("online", rs.getInt("is_online"));
                        arr.add(p);
                    }
                }
            }
        }
        o.put("players", arr);
        o.put("page", page);
        sendJson(ex, 200, o);
    }

    /** Sửa tiền: người chơi đang ONLINE thì sửa thẳng trên User đang
     *  sống (bản lưu DB khi thoát sẽ lấy số này) và đẩy gói cập nhật
     *  tiền xuống client; offline thì sửa DB. */
    private static void playerMoney(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        User live = UserManager.getInstance().find(id);
        if (live != null) {
            if (body.containsKey("xu")) live.xu = num(body, "xu", live.xu);
            if (body.containsKey("luong")) live.luong = (int) num(body, "luong", live.luong);
            if (body.containsKey("luongKhoa")) live.luongKhoa = (int) num(body, "luongKhoa", live.luongKhoa);
            if (body.containsKey("xeng")) live.xeng = (int) num(body, "xeng", live.xeng);
            try {
                live.getAvatarService().updateMoney(0);
            } catch (Exception ignore) {
                // client chưa vào hẳn game: tiền vẫn đã sửa trên User
            }
            sendJson(ex, 200, ok());
            return;
        }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE players SET xu = ?, luong = ?, luong_khoa = ?, xeng = ? WHERE user_id = ?")) {
            ps.setLong(1, num(body, "xu", 0));
            ps.setInt(2, (int) num(body, "luong", 0));
            ps.setInt(3, (int) num(body, "luongKhoa", 0));
            ps.setInt(4, (int) num(body, "xeng", 0));
            ps.setInt(5, id);
            ps.executeUpdate();
        }
        sendJson(ex, 200, ok());
    }

    private static void playerBan(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        boolean ban = num(body, "ban", 0) == 1;
        String reason = str(body, "reason", "Vi phạm nội quy");
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE users SET ban = ?, login_lock = ? WHERE id = ?")) {
            if (ban) {
                ps.setString(1, reason);
                ps.setInt(2, 1);
            } else {
                ps.setString(1, null);
                ps.setInt(2, 0);
            }
            ps.setInt(3, id);
            ps.executeUpdate();
        }
        if (ban) {
            User live = UserManager.getInstance().find(id);
            if (live != null && live.getSession() != null) {
                try {
                    live.getSession().close();
                } catch (Exception ignore) {
                }
            }
        }
        sendJson(ex, 200, ok());
    }

    private static void playerRole(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        int role = (int) num(body, "role", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE users SET role = ? WHERE id = ?")) {
            ps.setInt(1, role);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
        sendJson(ex, 200, ok());
    }
    // ======================== Vật phẩm / Shop ========================

    private static void items(HttpExchange ex) throws Exception {
        java.util.Map<String, String> q = query(ex);
        String kw = q.getOrDefault("q", "").trim();
        String sellF = q.getOrDefault("sell", "").trim();
        int page = Math.max(1, Integer.parseInt(q.getOrDefault("page", "1")));
        int size = 30;
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (!kw.isEmpty()) where.append(" AND (name LIKE ? OR id = ?)");
        if (sellF.equals("shop")) where.append(" AND sell IN (8, 14)");
        if (sellF.equals("hidden")) where.append(" AND sell NOT IN (8, 14)");
        JSONObject o = ok();
        JSONArray arr = new JSONArray();
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM items " + where)) {
                int i = 1;
                if (!kw.isEmpty()) {
                    ps.setString(i++, "%" + kw + "%");
                    ps.setString(i++, kw);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    o.put("total", rs.next() ? rs.getLong(1) : 0);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, name, coin, gold, sell, expired_day, zorder, gender, level, type "
                    + "FROM items " + where + " ORDER BY id ASC LIMIT ? OFFSET ?")) {
                int i = 1;
                if (!kw.isEmpty()) {
                    ps.setString(i++, "%" + kw + "%");
                    ps.setString(i++, kw);
                }
                ps.setInt(i++, size);
                ps.setInt(i, (page - 1) * size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject p = new JSONObject();
                        p.put("id", rs.getInt("id"));
                        p.put("name", rs.getString("name"));
                        p.put("coin", rs.getInt("coin"));
                        p.put("gold", rs.getInt("gold"));
                        p.put("sell", rs.getInt("sell"));
                        p.put("expiredDay", rs.getInt("expired_day"));
                        p.put("zorder", rs.getInt("zorder"));
                        p.put("gender", rs.getInt("gender"));
                        p.put("level", rs.getInt("level"));
                        p.put("type", rs.getInt("type"));
                        arr.add(p);
                    }
                }
            }
        }
        o.put("items", arr);
        o.put("page", page);
        sendJson(ex, 200, o);
    }

    /** Sửa vật phẩm: tên/giá Xu/giá Lượng/hạn dùng/bán tại shop nào.
     *  sell = 8 hoặc 14 ⇔ hiện trong Cửa Hàng NPC; sửa xong nạp lại
     *  PartManager để game áp dụng ngay không cần restart. */
    private static void itemUpdate(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE items SET name = ?, coin = ?, gold = ?, sell = ?, expired_day = ? WHERE id = ?")) {
            ps.setString(1, str(body, "name", ""));
            ps.setInt(2, (int) num(body, "coin", 0));
            ps.setInt(3, (int) num(body, "gold", 0));
            ps.setInt(4, (int) num(body, "sell", 0));
            ps.setInt(5, (int) num(body, "expiredDay", 0));
            ps.setInt(6, id);
            if (ps.executeUpdate() == 0) {
                sendJson(ex, 404, err("Không tìm thấy vật phẩm " + id));
                return;
            }
        }
        PartManager.getInstance().load();
        sendJson(ex, 200, ok());
    }

    /** Thêm vật phẩm MỚI bằng cách nhân bản một vật phẩm mẫu (giữ nguyên
     *  hình/animation của mẫu — admin đổi tên/giá sau). */
    private static void itemCreate(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int template = (int) num(body, "template", -1);
        String name = str(body, "name", "").trim();
        if (template < 0 || name.isEmpty()) {
            sendJson(ex, 400, err("Cần id vật phẩm mẫu và tên vật phẩm mới"));
            return;
        }
        int newId;
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT MAX(id) FROM items")) {
                newId = (rs.next() ? rs.getInt(1) : 0) + 1;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO items (id, name, coin, gold, sell, type, icon, "
                    + "expired_day, zorder, gender, level, animation) "
                    + "SELECT ?, ?, coin, gold, sell, type, icon, expired_day, "
                    + "zorder, gender, level, animation FROM items WHERE id = ?")) {
                ps.setInt(1, newId);
                ps.setString(2, name);
                ps.setInt(3, template);
                if (ps.executeUpdate() == 0) {
                    sendJson(ex, 404, err("Không tìm thấy vật phẩm mẫu " + template));
                    return;
                }
            }
        }
        PartManager.getInstance().load();
        JSONObject o = ok();
        o.put("newId", newId);
        sendJson(ex, 200, o);
    }

    private static void itemDelete(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM items WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        PartManager.getInstance().load();
        sendJson(ex, 200, ok());
    }

    // ======================== NPC ========================

    private static void npcs(HttpExchange ex) throws Exception {
        JSONObject o = ok();
        JSONArray arr = new JSONArray();
        try (Connection c = DbManager.getInstance().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, name, map, x, y, star, active FROM npc ORDER BY map, id")) {
            while (rs.next()) {
                JSONObject p = new JSONObject();
                p.put("id", rs.getInt("id"));
                p.put("name", rs.getString("name"));
                p.put("map", rs.getInt("map"));
                p.put("x", rs.getInt("x"));
                p.put("y", rs.getInt("y"));
                p.put("star", rs.getInt("star"));
                p.put("active", rs.getInt("active"));
                arr.add(p);
            }
        }
        o.put("npcs", arr);
        sendJson(ex, 200, o);
    }

    private static void npcUpdate(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE npc SET name = ?, map = ?, x = ?, y = ?, active = ? WHERE id = ?")) {
            ps.setString(1, str(body, "name", ""));
            ps.setInt(2, (int) num(body, "map", 0));
            ps.setInt(3, (int) num(body, "x", 0));
            ps.setInt(4, (int) num(body, "y", 0));
            ps.setInt(5, (int) num(body, "active", 1));
            ps.setInt(6, id);
            if (ps.executeUpdate() == 0) {
                sendJson(ex, 404, err("Không tìm thấy NPC " + id));
                return;
            }
        }
        reloadNpcs();
        sendJson(ex, 200, ok());
    }

    private static void npcCreate(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        String name = str(body, "name", "").trim();
        if (name.isEmpty()) {
            sendJson(ex, 400, err("Cần tên NPC"));
            return;
        }
        int template = (int) num(body, "template", -1);
        int newId;
        try (Connection c = DbManager.getInstance().getConnection()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT MAX(id) FROM npc")) {
                newId = (rs.next() ? rs.getInt(1) : 0) + 1;
            }
            if (template >= 0) {
                // nhân bản NPC mẫu (hình + câu thoại), đặt sang map/toạ độ mới
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO npc (id, name, items, map, x, y, star, chat, active) "
                        + "SELECT ?, ?, items, ?, ?, ?, star, chat, 1 FROM npc WHERE id = ?")) {
                    ps.setInt(1, newId);
                    ps.setString(2, name);
                    ps.setInt(3, (int) num(body, "map", 0));
                    ps.setInt(4, (int) num(body, "x", 100));
                    ps.setInt(5, (int) num(body, "y", 100));
                    ps.setInt(6, template);
                    if (ps.executeUpdate() == 0) {
                        sendJson(ex, 404, err("Không tìm thấy NPC mẫu " + template));
                        return;
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO npc (id, name, items, map, x, y, star, chat, active) "
                        + "VALUES (?, ?, '[]', ?, ?, ?, 0, '[]', 1)")) {
                    ps.setInt(1, newId);
                    ps.setString(2, name);
                    ps.setInt(3, (int) num(body, "map", 0));
                    ps.setInt(4, (int) num(body, "x", 100));
                    ps.setInt(5, (int) num(body, "y", 100));
                    ps.executeUpdate();
                }
            }
        }
        reloadNpcs();
        JSONObject o = ok();
        o.put("newId", newId);
        sendJson(ex, 200, o);
    }

    private static void npcDelete(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM npc WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        reloadNpcs();
        sendJson(ex, 200, ok());
    }

    /** Nạp lại toàn bộ NPC vào thế giới: gỡ NPC cũ khỏi zone/NpcManager
     *  rồi chạy lại loadNpcData (nó chỉ nạp NPC active = 1). */
    public static synchronized void reloadNpcs() {
        try {
            java.util.List<Npc> olds = NpcManager.getInstance().snapshot();
            for (Npc npc : olds) {
                try {
                    if (npc.getZone() != null) npc.getZone().leave(npc);
                } catch (Exception ignore) {
                }
                NpcManager.getInstance().remove(npc);
            }
            NpcManager.getInstance().clearAll();
            // Khu 0 chưa từng được loadNpcData nạp NPC ở bản gốc (chỉ khu
            // 1..3) — vẫn giữ nguyên hành vi đó khi nạp lại.
            for (avatar.play.Map m : MapManager.getInstance().allMaps()) {
                for (avatar.play.Zone z : m.getZones()) {
                    z.clearNpcs();
                }
            }
            ServerManager.loadNpcData();
        } catch (Exception e) {
            System.out.println("[Admin] reloadNpcs loi: " + e.getMessage());
        }
    }

    // ======================== Vòng quay may mắn ========================

    private static void wheel(HttpExchange ex) throws Exception {
        JSONObject o = ok();
        JSONArray arr = new JSONArray();
        try (Connection c = DbManager.getInstance().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT d.id, d.item_id, d.xu, d.luong, d.free, d.ratio, i.name "
                 + "FROM dial_lucky d LEFT JOIN items i ON i.id = d.item_id ORDER BY d.id")) {
            while (rs.next()) {
                JSONObject p = new JSONObject();
                p.put("id", rs.getInt("id"));
                p.put("itemId", rs.getInt("item_id"));
                p.put("itemName", rs.getString("name"));
                p.put("xu", rs.getInt("xu"));
                p.put("luong", rs.getInt("luong"));
                p.put("free", rs.getInt("free"));
                p.put("ratio", rs.getInt("ratio"));
                arr.add(p);
            }
        }
        o.put("rows", arr);
        sendJson(ex, 200, o);
    }

    private static void wheelUpdate(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dial_lucky SET item_id = ?, ratio = ? WHERE id = ?")) {
            ps.setInt(1, (int) num(body, "itemId", 0));
            ps.setInt(2, (int) num(body, "ratio", 0));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
        reloadWheel();
        sendJson(ex, 200, ok());
    }

    private static void wheelCreate(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dial_lucky (item_id, xu, luong, free, ratio) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, (int) num(body, "itemId", 0));
            ps.setInt(2, num(body, "xu", 0) == 1 ? 1 : 0);
            ps.setInt(3, num(body, "luong", 0) == 1 ? 1 : 0);
            ps.setInt(4, num(body, "free", 0) == 1 ? 1 : 0);
            ps.setInt(5, (int) num(body, "ratio", 10));
            ps.executeUpdate();
        }
        reloadWheel();
        sendJson(ex, 200, ok());
    }

    private static void wheelDelete(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM dial_lucky WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        reloadWheel();
        sendJson(ex, 200, ok());
    }

    private static void reloadWheel() {
        try {
            for (byte t = 0; t <= 2; t++) {
                avatar.lucky.DialLucky dl = DialLuckyManager.getInstance().find(t);
                if (dl != null) dl.load();
            }
        } catch (Exception e) {
            System.out.println("[Admin] reloadWheel loi: " + e.getMessage());
        }
    }

    // ======================== Giftcode ========================

    private static void giftcodes(HttpExchange ex) throws Exception {
        JSONObject o = ok();
        JSONArray arr = new JSONArray();
        try (Connection c = DbManager.getInstance().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, code, message, data, num, start_time, end_time FROM giftcode ORDER BY id DESC")) {
            while (rs.next()) {
                JSONObject p = new JSONObject();
                p.put("id", rs.getInt("id"));
                p.put("code", rs.getString("code"));
                p.put("message", rs.getString("message"));
                p.put("data", rs.getString("data"));
                p.put("num", rs.getInt("num"));
                p.put("startTime", String.valueOf(rs.getTimestamp("start_time")));
                p.put("endTime", String.valueOf(rs.getTimestamp("end_time")));
                arr.add(p);
            }
        }
        o.put("codes", arr);
        sendJson(ex, 200, o);
    }

    /** Tạo mới hoặc sửa giftcode (có id = sửa). data là JSON quà theo
     *  đúng định dạng GiftCodeService đọc: {"xu":..,"luong":..,"items":[{"id":..,"day":..,"quantity":..}]}. */
    private static void giftcodeSave(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        String code = str(body, "code", "").trim();
        if (code.isEmpty()) {
            sendJson(ex, 400, err("Cần mã giftcode"));
            return;
        }
        String data = str(body, "data", "{}").trim();
        try {
            new JSONParser().parse(data);
        } catch (Exception e) {
            sendJson(ex, 400, err("Data quà không phải JSON hợp lệ"));
            return;
        }
        long id = num(body, "id", 0);
        String start = str(body, "startTime", "").trim();
        String end = str(body, "endTime", "").trim();
        try (Connection c = DbManager.getInstance().getConnection()) {
            if (id > 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE giftcode SET code = ?, message = ?, data = ?, num = ?, "
                        + "start_time = ?, end_time = ? WHERE id = ?")) {
                    ps.setString(1, code);
                    ps.setString(2, str(body, "message", ""));
                    ps.setString(3, data);
                    ps.setInt(4, (int) num(body, "num", 1));
                    ps.setString(5, start.isEmpty() ? "2020-01-01 00:00:00" : start.replace('T', ' '));
                    ps.setString(6, end.isEmpty() ? "2037-12-31 23:59:59" : end.replace('T', ' '));
                    ps.setLong(7, id);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO giftcode (code, message, data, num, start_time, end_time, create_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0)")) {
                    ps.setString(1, code);
                    ps.setString(2, str(body, "message", ""));
                    ps.setString(3, data);
                    ps.setInt(4, (int) num(body, "num", 1));
                    ps.setString(5, start.isEmpty() ? "2020-01-01 00:00:00" : start.replace('T', ' '));
                    ps.setString(6, end.isEmpty() ? "2037-12-31 23:59:59" : end.replace('T', ' '));
                    ps.executeUpdate();
                }
            }
        }
        sendJson(ex, 200, ok());
    }

    private static void giftcodeDelete(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        int id = (int) num(body, "id", -1);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM giftcode WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        sendJson(ex, 200, ok());
    }

    /** Gửi quà vào Hòm Thư của một người chơi hoặc toàn server
     *  (user chốt: quà admin gửi vào thư như quà giftcode). Body:
     *  {to: tên tài khoản | "*", title, body, xu, luong,
     *   items: [{id, day, quantity}]}. */
    private static void mailSend(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        String to = str(body, "to", "").trim();
        String title = str(body, "title", "").trim();
        if (title.isEmpty()) title = "Quà từ Hệ Thống";
        String text = str(body, "body", "");
        long xu = num(body, "xu", 0);
        long luong = num(body, "luong", 0);
        JSONArray atts = new JSONArray();
        if (xu > 0) {
            JSONObject a = new JSONObject();
            a.put("t", "xu");
            a.put("n", xu);
            atts.add(a);
        }
        if (luong > 0) {
            JSONObject a = new JSONObject();
            a.put("t", "luong");
            a.put("n", luong);
            atts.add(a);
        }
        Object itemsObj = body.get("items");
        if (itemsObj instanceof JSONArray) {
            for (Object o : (JSONArray) itemsObj) {
                if (!(o instanceof JSONObject)) continue;
                JSONObject it = (JSONObject) o;
                long pid = num(it, "id", 0);
                long qty = num(it, "quantity", 1);
                long day = num(it, "day", 0);
                if (pid <= 0 || qty <= 0) continue;
                JSONObject a = new JSONObject();
                a.put("t", "item");
                a.put("id", pid);
                a.put("n", qty);
                if (day > 0) a.put("d", day);
                atts.add(a);
            }
        }
        if (atts.isEmpty()) {
            sendJson(ex, 400, err("Chưa có quà nào để gửi — cần ít nhất Xu, Lượng hoặc một vật phẩm."));
            return;
        }
        List<Integer> targets = new ArrayList<>();
        try (Connection c = DbManager.getInstance().getConnection()) {
            if (to.equals("*")) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id FROM users")) {
                    while (rs.next()) targets.add(rs.getInt(1));
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM users WHERE username = ?")) {
                    ps.setString(1, to);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) targets.add(rs.getInt(1));
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            sendJson(ex, 404, err("Không tìm thấy người chơi: " + to));
            return;
        }
        int sent = 0;
        for (int uid : targets) {
            if (MailService.sendLetter(uid, "gift", title, "Hệ Thống", text, atts.toJSONString())) {
                sent++;
            }
        }
        JSONObject r = ok();
        r.put("sent", sent);
        sendJson(ex, 200, r);
    }

    // ======================== Cờ tính năng / sự kiện ========================

    private static void flags(HttpExchange ex) throws Exception {
        JSONObject o = ok();
        JSONObject f = new JSONObject();
        f.put("maintenance", FeatureFlags.maintenance());
        f.put("register", FeatureFlags.registerOpen());
        f.put("shop", FeatureFlags.shopOpen());
        f.put("wheel", FeatureFlags.wheelOpen());
        f.put("giftcode", FeatureFlags.giftcodeOpen());
        f.put("farm", FeatureFlags.farmOpen());
        f.put("chat", FeatureFlags.chatOpen());
        o.put("flags", f);
        sendJson(ex, 200, o);
    }

    private static void flagSet(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        String name = str(body, "name", "").trim();
        boolean value = num(body, "value", 0) == 1;
        java.util.Set<String> known = new java.util.HashSet<>(java.util.Arrays.asList(
            "maintenance", "register", "shop", "wheel", "giftcode", "farm", "chat"));
        if (!known.contains(name)) {
            sendJson(ex, 400, err("Cờ không hợp lệ: " + name));
            return;
        }
        FeatureFlags.set(name, value);
        sendJson(ex, 200, ok());
    }

    /** Thông báo server (settings.thong_bao — hiện ở màn hình vào game)
     *  + tuỳ chọn phát ngay cho mọi người đang online. */
    private static void announce(HttpExchange ex) throws Exception {
        JSONObject body = readJson(ex);
        String text = str(body, "text", "");
        boolean broadcast = num(body, "broadcast", 0) == 1;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO settings (name, value) VALUES ('thong_bao', ?) "
                 + "ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
            ps.setString(1, text);
            ps.executeUpdate();
        }
        ServerManager.notify = text;
        if (broadcast) {
            for (User u : UserManager.users) {
                try {
                    u.getAvatarService().serverInfo(text);
                } catch (Exception ignore) {
                }
            }
        }
        sendJson(ex, 200, ok());
    }
}
