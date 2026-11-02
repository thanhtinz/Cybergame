package com.avatardk.game;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Điểm phân giải asset duy nhất của game: ưu tiên dữ liệu đã TẢI VỀ MÁY
 * (thư mục {@code gamedata} cạnh nơi chạy game — xem DataUpdater), không
 * có mới dùng bản đóng gói sẵn trong bản cài. Nhờ vậy bản cài phát hành
 * chỉ cần chứa tối thiểu (fonts để hiện màn hình tải), toàn bộ dữ liệu
 * game tải về ở lần vào đầu tiên và được cập nhật theo phiên bản.
 *
 * Bản WEB không có khái niệm file local (backend GWT ném lỗi khi gọi
 * Gdx.files.local) — trên web mọi asset đều là internal đóng sẵn.
 */
public final class GameFiles {
    public static final String DATA_DIR = "gamedata";

    private GameFiles() {
    }

    /** local() chỉ tồn tại trên bản cài; bản web (WebGL) bỏ qua. */
    private static boolean localOk() {
        return Gdx.app == null
            || Gdx.app.getType() != Application.ApplicationType.WebGL;
    }

    public static FileHandle data(String path) {
        if (localOk()) {
            FileHandle local = Gdx.files.local(DATA_DIR + "/" + path);
            if (local.exists()) return local;
        }
        return Gdx.files.internal(path);
    }

    /** Máy đã có dữ liệu tải về (ít nhất là dữ liệu lõi) hay chưa. */
    public static boolean hasLocalData() {
        return localOk() && Gdx.files.local(DATA_DIR + "/data/items.json").exists();
    }

    /** Thư mục gốc cho DataUpdater ghi dữ liệu tải về; bản web = null. */
    public static java.io.File dataRootFile() {
        return localOk() ? Gdx.files.local(DATA_DIR).file() : null;
    }
}
