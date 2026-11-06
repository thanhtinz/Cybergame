package avatar.admin;

import avatar.db.DbManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cờ bật/tắt tính năng của game, lưu trong bảng `settings` (key-value
 *  có sẵn của server gốc) với tiền tố "flag." — trang admin sửa ở đây,
 *  các handler trong game đọc qua lớp này (cache trong bộ nhớ, tự nạp
 *  lại mỗi 20 giây và ngay khi admin đổi). Tên cờ chuẩn:
 *  maintenance (bảo trì — chặn đăng nhập trừ admin), register (cho
 *  đăng ký mới), shop (mua bán ở Cửa Hàng), wheel (vòng quay may mắn),
 *  giftcode (nhập giftcode), farm (vào nông trại), chat (chat trong game),
 *  event (NPC + shop sự kiện: Vô Thường, Pay/Chay To Win, Saitama, 861). */
public class FeatureFlags {

    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
    private static volatile long lastLoad = 0;

    public static boolean get(String name, boolean def) {
        long now = System.currentTimeMillis();
        if (now - lastLoad > 20_000) {
            synchronized (FeatureFlags.class) {
                if (System.currentTimeMillis() - lastLoad > 20_000) {
                    reload();
                }
            }
        }
        Boolean v = CACHE.get(name);
        return v != null ? v : def;
    }

    public static synchronized void reload() {
        lastLoad = System.currentTimeMillis();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT name, value FROM settings WHERE name LIKE 'flag.%'");
             ResultSet rs = ps.executeQuery()) {
            CACHE.clear();
            while (rs.next()) {
                String key = rs.getString(1).substring("flag.".length());
                CACHE.put(key, "1".equals(rs.getString(2)) || "true".equalsIgnoreCase(rs.getString(2)));
            }
        } catch (Exception e) {
            // DB chưa sẵn sàng: giữ cache cũ/mặc định, game vẫn chạy
            System.out.println("[Admin] FeatureFlags reload loi: " + e.getMessage());
        }
    }

    public static void set(String name, boolean value) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO settings (name, value) VALUES (?, ?) "
                 + "ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
            ps.setString(1, "flag." + name);
            ps.setString(2, value ? "1" : "0");
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Luu co that bai: " + e.getMessage(), e);
        }
        CACHE.put(name, value);
        lastLoad = System.currentTimeMillis();
    }

    public static Map<String, Boolean> all() {
        get("maintenance", false); // bảo đảm cache đã nạp
        return CACHE;
    }

    // ===== các cờ chuẩn dùng trong handler =====
    public static boolean maintenance() { return get("maintenance", false); }
    public static boolean registerOpen() { return get("register", true); }
    public static boolean shopOpen() { return get("shop", true); }
    public static boolean wheelOpen() { return get("wheel", true); }
    public static boolean giftcodeOpen() { return get("giftcode", true); }
    public static boolean farmOpen() { return get("farm", true); }
    public static boolean chatOpen() { return get("chat", true); }
    public static boolean eventOpen() { return get("event", true); }
}
