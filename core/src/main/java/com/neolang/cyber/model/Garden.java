package com.neolang.cyber.model;

/**
 * Vườn thủy canh 6 ô. Mọi phép tính thời gian đều nhận giây epoch truyền vào
 * để test được; các overload không có now dùng giờ hệ thống.
 */
public class Garden {
    public static final int PLOT_COUNT = 6;

    public static class Plot {
        public String seedId;
        public long plantedAtEpochSec;
        public int growSeconds;
        public boolean planted;

        public void clear() {
            seedId = null;
            plantedAtEpochSec = 0;
            growSeconds = 0;
            planted = false;
        }
    }

    public final Plot[] plots = new Plot[PLOT_COUNT];

    public Garden() {
        for (int i = 0; i < PLOT_COUNT; i++) plots[i] = new Plot();
    }

    private static long nowSec() {
        return System.currentTimeMillis() / 1000L;
    }

    public boolean isValidIdx(int idx) {
        return idx >= 0 && idx < PLOT_COUNT;
    }

    /** Trồng hạt vào ô trống. Chỉ nhận vật phẩm loại SEED có trong Items. */
    public boolean plant(int idx, String seedId) {
        return plant(idx, seedId, nowSec());
    }

    public boolean plant(int idx, String seedId, long nowSec) {
        if (!isValidIdx(idx) || seedId == null) return false;
        Plot plot = plots[idx];
        if (plot.planted) return false;
        ItemDef def = Items.get(seedId);
        if (def == null || def.type != ItemType.SEED || def.cropId == null) return false;
        plot.seedId = seedId;
        plot.growSeconds = def.growSeconds;
        plot.plantedAtEpochSec = nowSec;
        plot.planted = true;
        return true;
    }

    /** Tiến độ lớn 0..1 (0 nếu ô trống). */
    public float progress(int idx, long nowSec) {
        if (!isValidIdx(idx)) return 0f;
        Plot plot = plots[idx];
        if (!plot.planted) return 0f;
        if (plot.growSeconds <= 0) return 1f;
        long elapsed = nowSec - plot.plantedAtEpochSec;
        if (elapsed <= 0) return 0f;
        float p = (float) elapsed / (float) plot.growSeconds;
        return p >= 1f ? 1f : p;
    }

    public float progress(int idx) {
        return progress(idx, nowSec());
    }

    public boolean isReady(int idx, long nowSec) {
        return isValidIdx(idx) && plots[idx].planted && progress(idx, nowSec) >= 1f;
    }

    public boolean isReady(int idx) {
        return isReady(idx, nowSec());
    }

    /**
     * Thu hoạch: trả cropId và dọn ô nếu đã chín, ngược lại trả null
     * và giữ nguyên ô.
     */
    public String harvest(int idx, long nowSec) {
        if (!isReady(idx, nowSec)) return null;
        ItemDef def = Items.get(plots[idx].seedId);
        String cropId = def == null ? null : def.cropId;
        plots[idx].clear();
        return cropId;
    }

    public String harvest(int idx) {
        return harvest(idx, nowSec());
    }

    /** Số giây còn lại tới khi chín (0 nếu trống hoặc đã chín). */
    public float growLeft(int idx, long nowSec) {
        if (!isValidIdx(idx)) return 0f;
        Plot plot = plots[idx];
        if (!plot.planted) return 0f;
        long left = plot.growSeconds - (nowSec - plot.plantedAtEpochSec);
        return left <= 0 ? 0f : (float) left;
    }

    public float growLeft(int idx) {
        return growLeft(idx, nowSec());
    }

    /**
     * Trồng lại một ô từ số giây còn lại (dùng khi load game: SaveData lưu
     * growLeft thay vì epoch tuyệt đối).
     */
    public boolean plantWithGrowLeft(int idx, String seedId, float growLeftSec, long nowSec) {
        if (!isValidIdx(idx) || seedId == null) return false;
        Plot plot = plots[idx];
        if (plot.planted) return false;
        ItemDef def = Items.get(seedId);
        if (def == null || def.type != ItemType.SEED || def.cropId == null) return false;
        long left = Math.round(growLeftSec);
        if (left < 0) left = 0;
        if (left > def.growSeconds) left = def.growSeconds;
        plot.seedId = seedId;
        plot.growSeconds = def.growSeconds;
        plot.plantedAtEpochSec = nowSec - (def.growSeconds - left);
        plot.planted = true;
        return true;
    }

    public boolean plantWithGrowLeft(int idx, String seedId, float growLeftSec) {
        return plantWithGrowLeft(idx, seedId, growLeftSec, nowSec());
    }

    public void clearAll() {
        for (Plot p : plots) p.clear();
    }
}
