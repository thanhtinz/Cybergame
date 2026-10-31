package com.avatardk.game.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bộ lắp nhân vật paper-doll — port đúng thuật toán của client gốc:
 *
 *  - Avatar.paint (Avatar.cs): duyệt seriPart ĐÃ SẮP theo zOrder tăng dần
 *    (Avatar.orderSeriesPath sắp lại seriPart theo Part.zOrder), với mỗi part gọi
 *    part.paintAvatar(g, frame, x*hd, y*hd, direct, 0).
 *  - APartInfo.paintAvatar (APartInfo.cs): nếu IDPart >= 2000 -> ảnh động do server stream
 *    (bỏ qua ở bản offline, ghi vào skippedParts). Ngược lại:
 *    ImageInfo = AvatarData.listImgInfo[imgID[frame]] rồi paintPart(g, bigID, x0, y0, w, h,
 *    x + dx[frame]*hd - (direct==LEFT ? dx[frame]*hd*2 + w*hd : 0), y + dy[frame]*hd, direct).
 *    Tức vùng atlas = big[bigID] tại (x0*hd, y0*hd, w*hd, h*hd); vị trí vẽ = điểm chân nhân vật
 *    cộng (dx*hd, dy*hd); khi quay trái (transform LEFT=2) thì lật ngang quanh trục của part.
 *  - AvatarData.paintPart (AvatarData.cs): drawRegion(bigImg, x0*hd, y0*hd, w*hd, h*hd, ...).
 *
 *  Lớp này tính thuần (không đụng Texture): cho ra, theo từng frame 0..14, danh sách
 *  DrawPart đã sắp z-order với vùng atlas (đơn vị medium) + offset (đơn vị medium).
 *  Tầng vẽ (UI) nhân hd=2 và xử lý lật ngang. */
public class PaperDoll {
    /** Một lớp vẽ của một frame: vùng atlas + offset so với điểm chân. */
    public static class DrawPart {
        public final int partId;
        public final int zorder;
        public final ImgInfo img;
        public final int dx, dy;
        public DrawPart(int partId, int zorder, ImgInfo img, int dx, int dy) {
            this.partId = partId; this.zorder = zorder; this.img = img; this.dx = dx; this.dy = dy;
        }
    }

    public static final int FRAMES = 15;

    private final AvatarDataSet data;
    /** partId đã yêu cầu nhưng không lắp được (động >= 2000, không có trong items, hoặc frame thiếu ảnh). */
    public final List<Integer> skippedParts = new ArrayList<>();
    private final List<PartData> ordered = new ArrayList<>();

    public PaperDoll(AvatarDataSet data, List<Integer> partIds) {
        this.data = data;
        for (int id : partIds) {
            PartData p = data.part(id);
            if (p == null || p.isDynamic() || p.frames.size() != FRAMES) {
                if (!skippedParts.contains(id)) skippedParts.add(id);
                continue;
            }
            // mọi frame phải tra được ImageInfo, nếu không coi như part hỏng
            boolean ok = true;
            for (PartData.Frame f : p.frames) if (data.imgByItemId.get(f.img) == null) { ok = false; break; }
            if (!ok) { if (!skippedParts.contains(id)) skippedParts.add(id); continue; }
            ordered.add(p);
        }
        // orderSeriesPath của gốc: sắp tăng dần theo zOrder (ổn định theo thứ tự vào)
        ordered.sort(Comparator.comparingInt(p -> p.zorder));
    }

    /** Các part tĩnh lắp được, đã sắp z-order. */
    public List<PartData> parts() { return ordered; }

    /** Danh sách lớp vẽ của một frame (0..14), đã sắp z-order tăng dần (vẽ sau đè lên trước). */
    public List<DrawPart> frame(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= FRAMES) throw new IllegalArgumentException("frame " + frameIndex);
        List<DrawPart> out = new ArrayList<>();
        for (PartData p : ordered) {
            PartData.Frame f = p.frames.get(frameIndex);
            out.add(new DrawPart(p.id, p.zorder, data.imgByItemId.get(f.img), f.dx, f.dy));
        }
        return out;
    }

    /** Khung bao (đơn vị medium) của một frame quanh điểm chân (0,0): [minX, minY, maxX, maxY]. */
    public int[] bounds(int frameIndex) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (DrawPart d : frame(frameIndex)) {
            minX = Math.min(minX, d.dx);
            minY = Math.min(minY, d.dy);
            maxX = Math.max(maxX, d.dx + d.img.w);
            maxY = Math.max(maxY, d.dy + d.img.h);
        }
        if (minX == Integer.MAX_VALUE) return new int[]{0, 0, 0, 0};
        return new int[]{minX, minY, maxX, maxY};
    }
}
