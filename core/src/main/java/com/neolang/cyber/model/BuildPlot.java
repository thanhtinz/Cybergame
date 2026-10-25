package com.neolang.cyber.model;

/**
 * Lưới xây dựng trong pod: 10 cột x 6 hàng.
 * cells[cy][cx] = 0 nếu trống, ngược lại = ordinal của BuildModule + 1.
 * Mỗi ô nhớ ô neo (anchor) của module chiếm nó, để module 2 ô nâng cấp/xóa
 * như một thể thống nhất.
 */
public class BuildPlot {
    public static final int COLS = 10;
    public static final int ROWS = 6;

    private final int[][] cells = new int[ROWS][COLS];
    private final int[][] levels = new int[ROWS][COLS];
    private final int[][] anchorX = new int[ROWS][COLS];
    private final int[][] anchorY = new int[ROWS][COLS];

    public BuildPlot() {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                anchorX[y][x] = -1;
                anchorY[y][x] = -1;
            }
        }
    }

    public static boolean inBounds(int cx, int cy) {
        return cx >= 0 && cx < COLS && cy >= 0 && cy < ROWS;
    }

    /** Module tại một ô: 0 = trống, ngược lại ordinal + 1. */
    public int moduleAt(int cx, int cy) {
        if (!inBounds(cx, cy)) return 0;
        return cells[cy][cx];
    }

    /** Cấp của module tại một ô (0 nếu trống). Mọi ô của module đều cho cùng kết quả. */
    public int levelAt(int cx, int cy) {
        if (!inBounds(cx, cy) || cells[cy][cx] == 0) return 0;
        int ax = anchorX[cy][cx];
        int ay = anchorY[cy][cx];
        if (ax >= 0 && ay >= 0) return levels[ay][ax];
        return levels[cy][cx];
    }

    public boolean canPlace(BuildModule m, int cx, int cy) {
        if (m == null) return false;
        if (cy < 0 || cy >= ROWS) return false;
        if (cx < 0 || cx + m.size > COLS) return false;
        for (int i = 0; i < m.size; i++) {
            if (cells[cy][cx + i] != 0) return false;
        }
        return true;
    }

    /**
     * Đặt module, trừ Xu Lá của người chơi.
     *
     * @return false nếu không đặt được hoặc không đủ xu (không trừ xu khi thất bại)
     */
    public boolean place(Player p, BuildModule m, int cx, int cy) {
        if (p == null || !canPlace(m, cx, cy)) return false;
        if (!p.spendXu(m.cost)) return false;
        int v = m.ordinal() + 1;
        for (int i = 0; i < m.size; i++) {
            cells[cy][cx + i] = v;
            levels[cy][cx + i] = 1;
            anchorX[cy][cx + i] = cx;
            anchorY[cy][cx + i] = cy;
        }
        return true;
    }

    /** Chi phí nâng cấp từ cấp hiện tại: cost / 2 * cấp hiện tại. */
    public int upgradeCost(int cx, int cy) {
        int v = moduleAt(cx, cy);
        if (v == 0) return 0;
        BuildModule m = BuildModule.values()[v - 1];
        int lv = levelAt(cx, cy);
        if (lv >= m.maxLevel) return 0;
        return m.cost / 2 * lv;
    }

    /**
     * Nâng cấp module tại ô (cx, cy), trừ Xu Lá.
     *
     * @return false nếu ô trống, đã tối đa cấp, hoặc không đủ xu
     */
    public boolean upgrade(Player p, int cx, int cy) {
        if (p == null || !inBounds(cx, cy)) return false;
        int v = cells[cy][cx];
        if (v == 0) return false;
        BuildModule m = BuildModule.values()[v - 1];
        int lv = levelAt(cx, cy);
        if (lv >= m.maxLevel) return false;
        int cost = m.cost / 2 * lv;
        if (!p.spendXu(cost)) return false;
        int ax = anchorX[cy][cx];
        int ay = anchorY[cy][cx];
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (cells[y][x] == v && anchorX[y][x] == ax && anchorY[y][x] == ay) {
                    levels[y][x] = lv + 1;
                }
            }
        }
        return true;
    }

    /** Tổng số module đã đặt (module 2 ô tính là 1). */
    public int countModules() {
        int n = 0;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (cells[y][x] != 0 && anchorX[y][x] == x && anchorY[y][x] == y) n++;
            }
        }
        return n;
    }

    // ---- Hỗ trợ SaveData ----

    /** Xuất toàn bộ lưới thành 3 mảng phẳng (hàng chính): cells, levels, anchors (chỉ số ô neo, -1 = trống). */
    public void exportData(int[] outCells, int[] outLevels, int[] outAnchors) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int i = y * COLS + x;
                outCells[i] = cells[y][x];
                outLevels[i] = levels[y][x];
                outAnchors[i] = cells[y][x] == 0 ? -1 : anchorY[y][x] * COLS + anchorX[y][x];
            }
        }
    }

    /** Nạp lại lưới từ 3 mảng phẳng do exportData tạo ra. */
    public void importData(int[] inCells, int[] inLevels, int[] inAnchors) {
        int n = COLS * ROWS;
        if (inCells == null || inLevels == null || inAnchors == null
                || inCells.length < n || inLevels.length < n || inAnchors.length < n) return;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int i = y * COLS + x;
                cells[y][x] = inCells[i];
                levels[y][x] = inLevels[i];
                if (inCells[i] == 0 || inAnchors[i] < 0) {
                    anchorX[y][x] = -1;
                    anchorY[y][x] = -1;
                } else {
                    anchorX[y][x] = inAnchors[i] % COLS;
                    anchorY[y][x] = inAnchors[i] / COLS;
                }
            }
        }
    }
}
