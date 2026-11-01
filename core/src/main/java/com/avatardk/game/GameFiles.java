package com.avatardk.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Điểm phân giải asset duy nhất của game: ưu tiên dữ liệu đã TẢI VỀ MÁY
 * (thư mục {@code gamedata} cạnh nơi chạy game — xem DataUpdater), không
 * có mới dùng bản đóng gói sẵn trong bản cài. Nhờ vậy bản cài phát hành
 * chỉ cần chứa tối thiểu (fonts để hiện màn hình tải), toàn bộ dữ liệu
 * game tải về ở lần vào đầu tiên và được cập nhật theo phiên bản.
 */
public final class GameFiles {
    public static final String DATA_DIR = "gamedata";

    private GameFiles() {
    }

    public static FileHandle data(String path) {
        FileHandle local = Gdx.files.local(DATA_DIR + "/" + path);
        return local.exists() ? local : Gdx.files.internal(path);
    }

    /** Máy đã có dữ liệu tải về (ít nhất là dữ liệu lõi) hay chưa. */
    public static boolean hasLocalData() {
        return Gdx.files.local(DATA_DIR + "/data/items.json").exists();
    }
}
