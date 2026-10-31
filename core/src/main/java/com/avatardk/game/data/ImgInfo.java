package com.avatardk.game.data;

/** Một vùng ảnh trong atlas big/<imageId>.png — tương ứng ImageInfo của client gốc
 *  (AvatarData.readImageData: ID, bigID, x0, y0, w, h; mọi tọa độ theo đơn vị MEDIUM,
 *  khi vẽ nhân với AvMain.hd — bản hd hd=2).
 *  LƯU Ý dữ liệu đã trích imgdata.json: cột "item_id" (0..1138) mới là khóa tra cứu mà
 *  animation.img trỏ tới; cột "id" của bảng chỉ là id dòng (= item_id + 1 trên toàn bộ
 *  1.139 dòng, đã kiểm). Cột "image_id" của file trích = bigID của gốc. */
public class ImgInfo {
    public final int itemId;
    public final int bigId;
    public final int x, y, w, h;
    public ImgInfo(int itemId, int bigId, int x, int y, int w, int h) {
        this.itemId = itemId; this.bigId = bigId;
        this.x = x; this.y = y; this.w = w; this.h = h;
    }
}
