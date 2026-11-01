package com.avatardk.game.data;

import java.util.ArrayList;
import java.util.List;

/** Một part (món đồ / bộ phận) của nhân vật paper-doll, đúng dữ liệu gốc items.json.
 *  Ánh xạ từ client gốc: Part.cs (IDPart, zOrder, name) + APartInfo.cs (imgID[], dx[], dy[]
 *  cho từng frame). Trong items.json, 3 mảng đó được gộp trong chuỗi "animation":
 *  15 phần tử {img, dx, dy} — img là chỉ số tra sang avatar_img_data (cột item_id). */
public class PartData {
    public static class Frame {
        public final int img;
        public final int dx;
        public final int dy;
        public Frame(int img, int dx, int dy) { this.img = img; this.dx = dx; this.dy = dy; }
    }

    public final int id;
    public final String name;
    public final int zorder;
    public final int gender;
    public final int level;
    public final int icon;
    /** Giá Xu khi shop bán (−1 = không bán bằng Xu) — cột coin của gốc. */
    public final int coin;
    /** Giá Lượng khi shop bán (−1 = không bán bằng Lượng) — cột gold của gốc. */
    public final int gold;
    /** Id shop bán món này (cột sell của gốc; −1/null trong JSON = không shop nào bán). */
    public final int sell;
    /** Số ngày hạn dùng khi mua (0 = vĩnh viễn). Pack5 zero hoá cột này;
     *  bản port đã khôi phục từ số ngày ghi trong tên món (user chốt 2026-11-01). */
    public final int expiredDay;
    /** Cột type của gốc: −1 = mặc được, −2 = cấm chuyển vào rương. */
    public final int type;
    public final List<Frame> frames = new ArrayList<>();

    public PartData(int id, String name, int zorder, int gender, int level, int icon) {
        this(id, name, zorder, gender, level, icon, -1, -1, -1, 0, -1);
    }

    public PartData(int id, String name, int zorder, int gender, int level, int icon,
                    int coin, int gold, int sell, int expiredDay, int type) {
        this.id = id; this.name = name; this.zorder = zorder;
        this.gender = gender; this.level = level; this.icon = icon;
        this.coin = coin; this.gold = gold; this.sell = sell;
        this.expiredDay = expiredDay; this.type = type;
    }

    /** Part động (id >= 2000): ảnh do server stream riêng (APartInfo.paintAvatar rẽ nhánh
     *  IDPart >= 2000 -> AvatarData.getImagePart), không có trong atlas offline. */
    public boolean isDynamic() { return id >= 2000; }
}
