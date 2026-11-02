package com.avatardk.game.util;

/**
 * BẢN WEB của BgThread: trình duyệt đơn luồng nên "luồng nền" chạy
 * ngay inline trong start() — đủ cho các việc phụ (kết nối đăng nhập
 * sẽ thất bại êm vì GameConnection bản web không có mạng; ghi/phát
 * thoại chỉ phát sinh khi online nên không tới được đây).
 */
public class BgThread {
    private final Runnable target;
    private boolean alive;

    public BgThread(Runnable target) {
        this.target = target;
    }

    public BgThread(Runnable target, String name) {
        this.target = target;
    }

    public void setDaemon(boolean daemon) {
    }

    public void start() {
        alive = true;
        try {
            target.run();
        } finally {
            alive = false;
        }
    }

    public void join(long millis) throws InterruptedException {
    }

    public boolean isAlive() {
        return alive;
    }

    public static void sleep(long millis) throws InterruptedException {
    }
}
