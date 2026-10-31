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
    public final List<Frame> frames = new ArrayList<>();

    public PartData(int id, String name, int zorder, int gender, int level, int icon) {
        this.id = id; this.name = name; this.zorder = zorder;
        this.gender = gender; this.level = level; this.icon = icon;
    }

    /** Part động (id >= 2000): ảnh do server stream riêng (APartInfo.paintAvatar rẽ nhánh
     *  IDPart >= 2000 -> AvatarData.getImagePart), không có trong atlas offline. */
    public boolean isDynamic() { return id >= 2000; }
}
