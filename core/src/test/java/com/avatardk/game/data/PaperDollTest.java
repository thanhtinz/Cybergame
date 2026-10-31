package com.avatardk.game.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Kiểm chứng bộ lắp paper-doll trên dữ liệu gốc thật (assets/data của project). */
public class PaperDollTest {
    static AvatarDataSet ds;
    static Path assetsDir;
    static final Map<Integer, int[]> atlasSizes = new HashMap<>();

    @BeforeAll
    static void load() throws Exception {
        assetsDir = findAssets();
        ds = AvatarDataSet.fromJson(
            Files.readString(assetsDir.resolve("data/items.json")),
            Files.readString(assetsDir.resolve("data/imgdata.json")),
            Files.readString(assetsDir.resolve("data/npcs.json")));
        // đọc kích thước thật của các atlas big/*.png từ header IHDR
        for (int id = 0; id <= 4; id++) {
            File f = assetsDir.resolve("big/" + id + ".png").toFile();
            try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                in.skipBytes(16);
                atlasSizes.put(id, new int[]{in.readInt(), in.readInt()});
            }
        }
    }

    static Path findAssets() {
        Path[] candidates = {
            Path.of("assets"), Path.of("../assets"),
            Path.of(System.getProperty("user.home"), "workspace/avatardk-java/assets"),
        };
        for (Path p : candidates) if (Files.exists(p.resolve("data/items.json"))) return p;
        throw new IllegalStateException("Không tìm thấy assets/data/items.json");
    }

    @Test
    void dataCountsMatchPack5Inventory() {
        assertEquals(5496, ds.parts.size(), "items.json phải có 5.496 part");
        assertEquals(1139, ds.imgByItemId.size(), "imgdata.json phải có 1.139 ImageInfo");
        assertEquals(28, ds.npcs.size(), "npcs.json phải có 28 NPC");
    }

    @Test
    void sonAssemblesFullyInZOrder() {
        // bộ đối chiếu pack5/chars/son.png: mặt 0, mắt 80, tóc 46, áo 474, quần 628
        PaperDoll doll = new PaperDoll(ds, List.of(628, 474, 0, 80, 46));
        assertTrue(doll.skippedParts.isEmpty(), "Không part nào của Sơn được phép bị bỏ qua: " + doll.skippedParts);
        assertEquals(5, doll.parts().size());
        int[] expectIds = {628, 474, 0, 80, 46};
        int[] expectZ = {10, 20, 30, 40, 50};
        for (int i = 0; i < 5; i++) {
            assertEquals(expectIds[i], doll.parts().get(i).id, "Thứ tự z-order ở vị trí " + i);
            assertEquals(expectZ[i], doll.parts().get(i).zorder);
        }
    }

    @Test
    void sonAllFramesResolveToValidAtlasRegions() {
        PaperDoll doll = new PaperDoll(ds, List.of(628, 474, 0, 80, 46));
        for (int frame = 0; frame < 15; frame++) {
            List<PaperDoll.DrawPart> layers = doll.frame(frame);
            assertEquals(5, layers.size(), "Frame " + frame + " phải đủ 5 lớp");
            for (PaperDoll.DrawPart d : layers) {
                int[] size = atlasSizes.get(d.img.bigId);
                assertNotNull(size, "Không có atlas big " + d.img.bigId);
                // vùng theo đơn vị medium nhân hd=2 phải nằm trong atlas hd
                assertTrue(d.img.x * 2 >= 0 && d.img.y * 2 >= 0);
                assertTrue((d.img.x + d.img.w) * 2 <= size[0],
                    "Part " + d.partId + " frame " + frame + " tràn atlas theo chiều ngang");
                assertTrue((d.img.y + d.img.h) * 2 <= size[1],
                    "Part " + d.partId + " frame " + frame + " tràn atlas theo chiều dọc");
                assertTrue(d.img.w > 0 && d.img.h > 0);
            }
        }
        int[] b = doll.bounds(0);
        int w = b[2] - b[0], h = b[3] - b[1];
        // cỡ lắp gốc ~24-30 x 30-37 px (medium) theo INVENTORY của Pack5 — cho biên rộng hơn chút
        assertTrue(w >= 20 && w <= 34, "Bề ngang lắp " + w + "px (medium) ngoài khoảng gốc");
        assertTrue(h >= 28 && h <= 42, "Chiều cao lắp " + h + "px (medium) ngoài khoảng gốc");
    }

    @Test
    void twoNpcsAssemble() {
        NpcData dauGia = ds.npcs.stream().filter(n -> n.name.equals("đấu giá")).findFirst().orElseThrow();
        NpcData laiBuon = ds.npcs.stream().filter(n -> n.name.equals("lai.buon")).findFirst().orElseThrow();
        assertEquals(9, dauGia.map);
        assertEquals(25, laiBuon.map);
        // đấu giá (map 9): đủ 7 part tĩnh, lắp trọn 15 frame
        PaperDoll doll = new PaperDoll(ds, dauGia.items);
        assertTrue(doll.skippedParts.isEmpty(),
            "NPC đấu giá phải lắp đủ, bị bỏ qua: " + doll.skippedParts);
        assertEquals(dauGia.items.size(), doll.parts().size());
        for (int frame = 0; frame < 15; frame++) assertEquals(dauGia.items.size(), doll.frame(frame).size());
        // lai.buon (map 25): 6/7 part lắp được; part 273 có animation rỗng trong DB —
        // nó là PartFollow của gốc (PartFollow.cs), dữ kiện follow/color chỉ nằm trong
        // luồng binary do server cấp (AvatarData.readAvatarPart), không có trong bản
        // trích items.json, nên offline phải bỏ qua và ghi rõ, không bịa.
        PaperDoll doll2 = new PaperDoll(ds, laiBuon.items);
        assertEquals(List.of(273), doll2.skippedParts);
        assertEquals(laiBuon.items.size() - 1, doll2.parts().size());
        for (int frame = 0; frame < 15; frame++) assertEquals(6, doll2.frame(frame).size());
    }

    @Test
    void dynamicPartsAreReportedNotInvented() {
        // Chủ Hôn (map 10) chỉ mặc part 2480 >= 2000: part động do server stream,
        // bản offline phải ghi rõ là bị bỏ qua chứ không được bịa ảnh.
        NpcData chuHon = ds.npcs.stream().filter(n -> n.map == 10).findFirst().orElseThrow();
        assertEquals(List.of(2480), chuHon.items);
        PaperDoll doll = new PaperDoll(ds, chuHon.items);
        assertEquals(0, doll.parts().size());
        assertEquals(List.of(2480), doll.skippedParts);
    }

    @Test
    void fullPartsHave15FramesAndFollowPartsAreEmptyInDb() {
        // Sự thật dữ liệu gốc (đã đếm trên items.json): 4.740 part có animation đầy đủ
        // 15 frame và MỌI img đều tra được ImageInfo; 756 part còn lại có animation "[]"
        // trong DB — đó là các PartFollow/PartSmall của gốc: hình vẽ lấy theo part gốc
        // (follow) + bảng màu (color) do server stream dạng binary, DB không chứa.
        int full = 0, empty = 0, staticFull = 0;
        for (PartData p : ds.parts.values()) {
            if (p.frames.isEmpty()) { empty++; continue; }
            assertEquals(15, p.frames.size(), "Part " + p.id + " (" + p.name + ") phải có đúng 15 frame");
            full++;
            if (!p.isDynamic()) {
                // chỉ part tĩnh mới tra atlas offline; part động >= 2000 trỏ tới ảnh do
                // server stream (img có thể vượt quá 1.139 ImageInfo của avatar_img_data)
                staticFull++;
                for (PartData.Frame f : p.frames)
                    assertNotNull(ds.imgByItemId.get(f.img), "Part " + p.id + " trỏ img " + f.img + " không có ImageInfo");
            }
        }
        assertEquals(4740, full);
        assertEquals(404, staticFull);
        assertEquals(756, empty);
    }
}
