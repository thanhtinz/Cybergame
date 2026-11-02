package com.avatardk.game.util;

/**
 * Luồng chạy nền tối giản cho các việc phụ (ghi/phát tin nhắn thoại,
 * kết nối đăng nhập). Bản cài dùng java.lang.Thread thật; bản WEB có
 * bản thay thế cùng tên (super-source) chạy inline vì GWT không mô
 * phỏng được Thread — các luồng này trên web đều là đường phụ không
 * thiết yếu (ghi âm chỉ khi online, phát thoại, chờ bắt tay).
 */
public class BgThread {
    private final Thread thread;

    public BgThread(Runnable target) {
        thread = new Thread(target);
    }

    public BgThread(Runnable target, String name) {
        thread = new Thread(target, name);
    }

    public void setDaemon(boolean daemon) {
        thread.setDaemon(daemon);
    }

    public void start() {
        thread.start();
    }

    public void join(long millis) throws InterruptedException {
        thread.join(millis);
    }

    public boolean isAlive() {
        return thread.isAlive();
    }

    public static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
