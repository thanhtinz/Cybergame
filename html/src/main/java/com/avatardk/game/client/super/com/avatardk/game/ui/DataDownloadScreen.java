package com.avatardk.game.ui;

import com.badlogic.gdx.ScreenAdapter;
import com.avatardk.game.update.DataUpdater;

/**
 * BẢN WEB của màn tải dữ liệu: bản web đóng sẵn dữ liệu nên màn này
 * không bao giờ hiện (DataUpdater bản web luôn báo dữ liệu đã sẵn
 * sàng). Chỉ tồn tại để AvatarDkGame biên dịch được trên GWT.
 */
public class DataDownloadScreen extends ScreenAdapter {
    public DataDownloadScreen(AvatarDkGame game, DataUpdater updater, boolean simulate) {
    }
}
