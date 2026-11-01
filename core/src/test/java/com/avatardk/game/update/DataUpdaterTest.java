package com.avatardk.game.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Tải dữ liệu lần đầu: manifest parse, tải qua file:// (offline), kiểm
 * sha256, giải nén, mốc phiên bản; gói sai hash bị từ chối; zip-slip bị
 * chặn.
 */
public class DataUpdaterTest {

    private static File zipOf(File dir, String name, String[][] entries) throws Exception {
        File zip = new File(dir, name);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String[] e : entries) {
                z.putNextEntry(new ZipEntry(e[0]));
                z.write(e[1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return zip;
    }

    private static String manifestJson(int version, File... zips) throws Exception {
        StringBuilder sb = new StringBuilder();
        long total = 0;
        sb.append("{\"version\":").append(version).append(",\"bundles\":[");
        for (int i = 0; i < zips.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"file\":\"").append(zips[i].getName()).append("\",\"size\":")
                .append(zips[i].length()).append(",\"sha256\":\"")
                .append(DataUpdater.sha256Hex(zips[i])).append("\"}");
            total += zips[i].length();
        }
        sb.append("],\"totalSize\":").append(total).append('}');
        return sb.toString();
    }

    @Test
    void parseManifest_FieldsAndTotalFallback() {
        DataUpdater.Manifest m = DataUpdater.parseManifest(
            "{\"version\":3,\"bundles\":[{\"file\":\"a.zip\",\"size\":10,\"sha256\":\"x\"},"
                + "{\"file\":\"b.zip\",\"size\":5,\"sha256\":\"y\"}]}");
        assertEquals(3, m.version);
        assertEquals(2, m.bundles.size());
        assertEquals("a.zip", m.bundles.get(0).file);
        assertEquals(10, m.bundles.get(0).size);
        assertEquals(15, m.totalSize, "thiếu totalSize thì cộng từ các gói");
    }

    @Test
    void fullCycle_DownloadVerifyExtractMarkVersion() throws Exception {
        File serverDir = Files.createTempDirectory("dataserver").toFile();
        File zip1 = zipOf(serverDir, "gamedata-1.zip", new String[][]{
            {"data/items.json", "{\"items\":[]}"},
            {"big/0.png", "PNGDATA-0123456789"},
        });
        File zip2 = zipOf(serverDir, "gamedata-2.zip", new String[][]{
            {"music/cover1.png", "COVER"},
        });
        Files.writeString(new File(serverDir, "manifest.json").toPath(),
            manifestJson(7, zip1, zip2), StandardCharsets.UTF_8);

        File root = Files.createTempDirectory("gamedata").toFile();
        DataUpdater up = new DataUpdater(root, serverDir.toURI().toString());
        assertEquals(-1, up.installedVersion());
        DataUpdater.Manifest m = up.fetchManifest();
        assertEquals(7, m.version);
        assertEquals(2, m.bundles.size());
        assertNotNull(up.cachedManifest(), "manifest tải về phải được lưu lại");

        List<Long> progress = new ArrayList<>();
        up.downloadAll(m, (done, total, st) -> progress.add(done));
        assertEquals("{\"items\":[]}",
            Files.readString(new File(root, "data/items.json").toPath(), StandardCharsets.UTF_8));
        assertEquals("PNGDATA-0123456789",
            Files.readString(new File(root, "big/0.png").toPath(), StandardCharsets.UTF_8));
        assertEquals("COVER",
            Files.readString(new File(root, "music/cover1.png").toPath(), StandardCharsets.UTF_8));
        assertEquals(7, up.installedVersion(), "xong hết mới ghi mốc phiên bản");
        assertFalse(progress.isEmpty());
        assertEquals(m.totalSize, progress.get(progress.size() - 1).longValue(),
            "tiến độ cuối = tổng dung lượng");
        assertFalse(new File(root, "tmp/gamedata-1.zip.part").exists(), "gói tạm bị dọn sau giải nén");
    }

    @Test
    void shaMismatch_RejectedAndNoVersionMark() throws Exception {
        File serverDir = Files.createTempDirectory("dataserver").toFile();
        File zip1 = zipOf(serverDir, "gamedata-1.zip", new String[][]{
            {"data/items.json", "{}"},
        });
        String bad = manifestJson(1, zip1).replace(
            DataUpdater.sha256Hex(zip1), "0000000000000000000000000000000000000000000000000000000000000000");
        Files.writeString(new File(serverDir, "manifest.json").toPath(), bad, StandardCharsets.UTF_8);

        File root = Files.createTempDirectory("gamedata").toFile();
        DataUpdater up = new DataUpdater(root, serverDir.toURI().toString());
        DataUpdater.Manifest m = up.fetchManifest();
        Exception ex = assertThrows(Exception.class, () -> up.downloadAll(m, null));
        assertTrue(ex.getMessage().contains("không khớp"), ex.getMessage());
        assertEquals(-1, up.installedVersion(), "gói sai hash: không ghi mốc, lần sau tải lại");
        assertFalse(new File(root, "data/items.json").exists(), "gói sai hash không được giải nén");
    }

    @Test
    void unzip_ZipSlipBlocked() throws Exception {
        File dir = Files.createTempDirectory("slip").toFile();
        File zip = zipOf(dir, "evil.zip", new String[][]{
            {"../evil.txt", "pwned"},
            {"ok.txt", "fine"},
        });
        File dest = new File(dir, "dest");
        dest.mkdirs();
        assertThrows(Exception.class, () -> DataUpdater.unzip(zip, dest));
        assertFalse(new File(dir, "evil.txt").exists(), "file không được thoát ra ngoài thư mục dữ liệu");
    }
}
