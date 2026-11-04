package com.avatardk.game.ui;

import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Cầu bàn phím mềm cho các ô nhập tự vẽ của game. Trên desktop/Android,
 * phím cứng đi qua keyTyped như cũ nên bản core này là no-op. Trên bản
 * WEB, bản thay thế ở html super-source phủ một ô &lt;input&gt; DOM thật
 * lên đúng vị trí ô nhập: bấm vào ô là trình duyệt tự mở bàn phím
 * (iOS/Android), chữ gõ vào được đồng bộ về StringBuilder của game mỗi
 * khung hình.
 *
 * Cách dùng: màn hình gọi setViewport() khi show/resize, beginFrame()
 * đầu render(), register(...) trong lúc vẽ mỗi ô nhập (toạ độ game),
 * endFrame() cuối render(), hide() khi đổi màn hình.
 */
public class SoftInput {

    /** Ô nhập đích: đọc/ghi nội dung + hành động khi bấm Enter. */
    public interface Target {
        String get();
        void set(String value);
        void onEnter();
    }

    /** Target bọc một StringBuilder có giới hạn độ dài. */
    public static Target of(final StringBuilder b, final int max, final Runnable enter) {
        return new Target() {
            @Override public String get() { return b.toString(); }
            @Override public void set(String v) {
                if (v == null) v = "";
                v = v.replace('\n', ' ').replace('\r', ' ');
                if (v.length() > max) v = v.substring(0, max);
                if (!v.contentEquals(b)) {
                    b.setLength(0);
                    b.append(v);
                }
            }
            @Override public void onEnter() { if (enter != null) enter.run(); }
        };
    }

    public static void setViewport(Viewport v) { }

    public static void beginFrame() { }

    public static void register(String id, Target t, boolean password, boolean dark,
                                float x, float y, float w, float h, float padLeft) { }

    /** Vùng bấm vào không mở ô nhập nhưng cũng không đóng bàn phím
     *  (ví dụ nút con mắt hiện/ẩn mật khẩu nằm sát ô nhập). */
    public static void registerKeepAlive(String id, float x, float y, float w, float h) { }

    public static void endFrame() { }

    public static void hide() { }
}
