package com.avatardk.game.update;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * BẢN WEB của DataUpdater: dữ liệu game được đóng sẵn cùng trang web
 * nên luôn báo "đã cài phiên bản 1" — AvatarDkGame thấy vậy sẽ vào
 * thẳng game, không hiện màn tải.
 */
public class DataUpdater {

    public static final String DEFAULT_BASE_URL =
        "https://github.com/thanhtinz/Cybergame/releases/download/gamedata-v1/";

    public interface Progress {
        void onProgress(long doneBytes, long totalBytes, String status);
    }

    public static class Bundle {
        public String file = "";
        public long size = 0;
        public String sha256 = "";
    }

    public static class Manifest {
        public int version = 1;
        public long totalSize = 0;
        public List<Bundle> bundles = new ArrayList<>();
    }

    public DataUpdater(File rootDir, String baseUrl) {
    }

    public static String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    public int installedVersion() {
        return 1;
    }

    public Manifest cachedManifest() {
        return new Manifest();
    }

    public Manifest fetchManifest() throws IOException {
        return new Manifest();
    }

    public void downloadAll(Manifest manifest, Progress progress) throws IOException {
    }
}
