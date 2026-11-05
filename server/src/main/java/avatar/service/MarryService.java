package avatar.service;

import avatar.db.DbManager;
import avatar.model.User;
import avatar.server.UserManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * CẶP ĐÔI [PORT theo bản chính 3.4.4: tab "Cặp Đôi" của MyInfoScr +
 * CommandCouple (avatar/partner/dateDay/level/perLevel/commandList)].
 *
 * Nền dữ liệu là bảng marry của bản fan (idNam, idNu, level,
 * perLevel): level 0 = hẹn hò, >= 1 = đã cưới. Bản port bổ sung:
 *  - cột marry.ngay: ngày bắt đầu quan hệ (dateDay của gốc), ghi
 *    khi hẹn hò được chấp nhận, giữ nguyên qua lễ cưới.
 *  - cơ chế tăng cấp đôi (bản fan không có): điểm danh mỗi ngày
 *    +10% tiến độ cho cặp của người điểm danh; đủ 100% lên 1 cấp.
 *    Tên quan hệ theo mốc nhẫn 1153..1157 của gốc.
 */
public final class MarryService {

    private static boolean schemaReady = false;

    private MarryService() {
    }

    public static void ensureSchema() {
        if (schemaReady) return;
        synchronized (MarryService.class) {
            if (schemaReady) return;
            try (Connection c = DbManager.getInstance().getConnection()) {
                try {
                    c.createStatement().execute(
                            "ALTER TABLE marry ADD COLUMN ngay DATETIME NULL");
                } catch (Exception ignored) {
                    // cột đã tồn tại
                }
                schemaReady = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Tên quan hệ (tenQuanHe) theo cấp đôi. Mốc 1-4 là tên nhẫn
     *  1153 của bản fan "Cặp đôi mới cưới"; các mốc sau [PORT]. */
    public static String relationName(int level) {
        if (level <= 0) return "Hẹn hò";
        if (level < 5) return "Cặp đôi mới cưới";
        if (level < 10) return "Cặp đôi hạnh phúc";
        if (level < 15) return "Cặp đôi viên mãn";
        if (level < 20) return "Cặp đôi vàng";
        return "Cặp đôi kim cương";
    }

    /** Cộng tiến độ cấp đôi cho cặp của user (nếu có). Đủ 100%
     *  lên cấp (trần 99). Ghi DB ngay + đồng bộ RAM cho cả hai
     *  nếu đang online. Lỗi ở đây không làm hỏng luồng chính. */
    public static void addCouplePoints(User us, int pts) {
        if (us == null || pts <= 0 || us.getIdUsHenHo() == 0) return;
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection()) {
            int idNam = 0, idNu = 0, level = 0, per = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT idNam, idNu, level, perLevel FROM marry"
                    + " WHERE idNam = ? OR idNu = ?")) {
                ps.setInt(1, us.getId());
                ps.setInt(2, us.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return;
                    idNam = rs.getInt(1);
                    idNu = rs.getInt(2);
                    level = rs.getInt(3);
                    per = rs.getInt(4);
                }
            }
            per += pts;
            // Level 0 = đang hẹn hò: tiến độ dồn tới tối đa 99,
            // chỉ sau lễ cưới (level >= 1) mới lên cấp theo tiến độ
            // (level >= 1 đồng nghĩa đã cưới trong mô hình gốc).
            while (per >= 100 && level >= 1 && level < 99) {
                per -= 100;
                level++;
            }
            if (level == 0 || level >= 99) per = Math.min(per, 99);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE marry SET level = ?, perLevel = ?"
                    + " WHERE idNam = ? AND idNu = ?")) {
                ps.setInt(1, level);
                ps.setInt(2, per);
                ps.setInt(3, idNam);
                ps.setInt(4, idNu);
                ps.executeUpdate();
            }
            int[] ids = {idNam, idNu};
            for (int id : ids) {
                User u = UserManager.getInstance().find(id);
                if (u != null) {
                    u.setLevelMarry(level);
                    u.setPerLevelMarry(per);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
