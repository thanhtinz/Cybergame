package avatar.admin;

import avatar.db.DbManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cấu hình số của game, lưu trong bảng `settings` (key-value có sẵn
 *  của server gốc) — trang admin sửa ở đây, các handler đọc qua lớp
 *  này (cache trong bộ nhớ, tự nạp lại mỗi 20 giây và ngay khi admin
 *  đổi, cùng mẫu với FeatureFlags).
 *
 *  Hiện có nhóm "wheel." cho Vòng quay may mắn:
 *  price_xu / price_luong (giá một lượt quay), weight_item / weight_xu
 *  / weight_xp / weight_luong (tỉ trọng loại quà), xu_min / xu_max,
 *  xp_min / xp_max, luong_min / luong_max (thang quà), expire_days
 *  (các mốc hạn dùng của quà đồ khi không trúng đúng món đã chọn). */
public class GameSettings {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile long lastLoad = 0;

    public static String get(String key, String def) {
        long now = System.currentTimeMillis();
        if (now - lastLoad > 20_000) {
            synchronized (GameSettings.class) {
                if (System.currentTimeMillis() - lastLoad > 20_000) {
                    reload();
                }
            }
        }
        String v = CACHE.get(key);
        return v != null ? v : def;
    }

    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static synchronized void reload() {
        lastLoad = System.currentTimeMillis();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT name, value FROM settings WHERE name LIKE 'wheel.%'");
             ResultSet rs = ps.executeQuery()) {
            CACHE.clear();
            while (rs.next()) {
                CACHE.put(rs.getString(1), rs.getString(2));
            }
        } catch (Exception e) {
            System.out.println("[Admin] GameSettings reload loi: " + e.getMessage());
        }
    }

    public static void set(String key, String value) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO settings (name, value) VALUES (?, ?) "
                 + "ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Luu cau hinh that bai: " + e.getMessage(), e);
        }
        CACHE.put(key, value);
        lastLoad = System.currentTimeMillis();
    }

    public static void setInt(String key, int value) {
        set(key, String.valueOf(value));
    }

    // ===== Vòng quay may mắn =====
    public static int wheelPriceXu()    { return Math.max(0, getInt("wheel.price_xu", 25000)); }
    public static int wheelPriceLuong() { return Math.max(0, getInt("wheel.price_luong", 5)); }
    public static int wheelWeightItem()  { return Math.max(0, getInt("wheel.weight_item", 18)); }
    public static int wheelWeightXu()    { return Math.max(0, getInt("wheel.weight_xu", 45)); }
    public static int wheelWeightXp()    { return Math.max(0, getInt("wheel.weight_xp", 36)); }
    public static int wheelWeightLuong() { return Math.max(0, getInt("wheel.weight_luong", 1)); }
    public static int wheelXuMin() { return Math.max(0, getInt("wheel.xu_min", 10)); }
    public static int wheelXuMax() { return Math.max(wheelXuMin(), getInt("wheel.xu_max", 3000)); }
    public static int wheelXpMin() { return Math.max(0, getInt("wheel.xp_min", 10)); }
    public static int wheelXpMax() { return Math.max(wheelXpMin(), getInt("wheel.xp_max", 100)); }
    public static int wheelLuongMin() { return Math.max(0, getInt("wheel.luong_min", 1)); }
    public static int wheelLuongMax() { return Math.max(wheelLuongMin(), getInt("wheel.luong_max", 2)); }

    /** Các mốc hạn dùng (ngày) của quà đồ không trúng đúng món đã chọn. */
    public static int[] wheelExpireDays() {
        String s = get("wheel.expire_days", "3,7,15,30");
        try {
            String[] parts = s.split(",");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
            if (out.length > 0) return out;
        } catch (Exception ignored) {
        }
        return new int[]{3, 7, 15, 30};
    }
}
