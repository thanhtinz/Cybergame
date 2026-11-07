package com.avatardk.game.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Cài đặt trong game — port của OptionScr bản gốc: gốc lưu RMS
 *  "avatarShowName" gồm 1 byte âm lượng + mảng mapFocus (0 = hiện,
 *  1 = ẩn); bản port lưu bằng Preferences của LibGDX với cùng tên khoá.
 *  - showName  ⇔ mapFocus[0] "Tên": hiện tên trên đầu nhân vật
 *    (tắt thì chỉ còn tên của chính mình — đúng luật Avatar.paint gốc).
 *  - showDir   ⇔ mapFocus[1] "Chỉ đường": hiện các chỉ dẫn đường đi
 *    (gốc là marker PopupName trong map; bản port hiện áp cho đường
 *    chấm tuyến trên bản đồ thành phố).
 *  - volume    ⇔ âm thanh 0..100, nấc 10 như gốc (SoundManager.setVolume
 *    nhận volume/100; bản port áp cho phát tin nhắn thoại và các âm
 *    thanh sẽ port sau). */
public class GameSettings {

    public static boolean showName = true;
    /** Nhân vật pixel mới (bản THỬ theo ảnh chủ dự án gửi 2026-11-06):
     *  bật = nhân vật của mình vẽ bằng bộ sprite mới thay doll gốc. */
    public static boolean showDir = true;
    public static int volume = 100;
    /** Mức âm lượng trước khi tắt tiếng, để bật lại khôi phục đúng mức cũ. */
    public static int lastVolume = 100;

    private static Preferences prefs() {
        return Gdx.app.getPreferences("avatarShowName");
    }

    public static void load() {
        try {
            Preferences p = prefs();
            showName = p.getBoolean("showName", true);
            showDir = p.getBoolean("showDir", true);
            volume = p.getInteger("volume", 100);
            lastVolume = p.getInteger("lastVolume", 100);
            if (volume < 0) volume = 0;
            if (volume > 100) volume = 100;
        } catch (Exception e) {
            // môi trường headless/test không có Preferences: giữ mặc định
        }
    }

    public static void save() {
        try {
            Preferences p = prefs();
            p.putBoolean("showName", showName);
            p.putBoolean("showDir", showDir);
            p.putInteger("volume", volume);
            p.putInteger("lastVolume", lastVolume);
            p.flush();
        } catch (Exception e) {
            // không lưu được cũng không chặn việc đóng panel
        }
    }

    /** Hệ số âm lượng 0..1 cho các API âm thanh của LibGDX. */
    public static float volumeScale() {
        return volume / 100f;
    }
}
