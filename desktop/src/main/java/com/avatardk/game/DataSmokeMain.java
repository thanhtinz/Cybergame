package com.avatardk.game;

import com.avatardk.game.data.AvatarDataSet;
import com.avatardk.game.data.PaperDoll;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Smoke test không giao diện: nạp dữ liệu gốc từ assets/, lắp nhân vật Sơn + 2 NPC,
 *  in kết quả và thoát khác 0 nếu có gì không khớp. Chạy: gradle --offline :desktop:smoke */
public class DataSmokeMain {
    public static void main(String[] args) throws Exception {
        Path base = Path.of("assets", "data");
        AvatarDataSet ds = AvatarDataSet.fromJson(
            Files.readString(base.resolve("items.json")),
            Files.readString(base.resolve("imgdata.json")),
            Files.readString(base.resolve("npcs.json")));
        System.out.println("parts=" + ds.parts.size() + " imgInfo=" + ds.imgByItemId.size() + " npcs=" + ds.npcs.size());
        PaperDoll son = new PaperDoll(ds, List.of(628, 474, 0, 80, 46));
        System.out.println("Sơn: lắp được " + son.parts().size() + "/5 part, bỏ qua " + son.skippedParts);
        int[] b = son.bounds(0);
        System.out.println("Sơn frame 0 bounds (medium): " + b[0] + "," + b[1] + " -> " + b[2] + "," + b[3]
            + " (" + (b[2] - b[0]) + "x" + (b[3] - b[1]) + ")");
        if (ds.parts.size() != 5496 || ds.imgByItemId.size() != 1139 || ds.npcs.size() != 28)
            throw new IllegalStateException("Số lượng dữ liệu gốc không khớp kiểm kê Pack5");
        if (son.parts().size() != 5) throw new IllegalStateException("Sơn phải lắp đủ 5 part");
        System.out.println("SMOKE OK");
    }
}
