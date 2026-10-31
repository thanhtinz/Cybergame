package com.avatardk.game.sim;

/** Chu trình frame của nhân vật, chép nguyên bảng Avatar.FRAME trong Avatar.cs (static init):
 *  FRAME[action][cFrame], cFrame chạy vòng 0..9 (Avatar.updateFrame: cFrame++, >= 10 -> 0;
 *  frame = FRAME[action][cFrame]).
 *  action theo Base.cs: STAND = 0, RUN = 1. Hai action đầu có nhịp riêng:
 *  STAND: 0,0,0,0,0, 1,1,1,1,1 (đứng yên, nửa sau chu trình chuyển frame 1 — nhịp thở/chớp)
 *  RUN:   0,0,0,0,0, 2,2,2,2,2
 *  Các action 2..13: mỗi action dùng đúng một frame = action + 1 (vd action 2 -> frame 3).
 *  action 14 (TAM) dùng lại nhịp của STAND.
 *  Hướng: Base.RIGHT = 0 (vẽ thường), Base.LEFT = 2 (lật ngang khi drawRegion).
 *  Di chuyển lên/xuống KHÔNG đổi hàng frame trong gốc — nhân vật vẫn dùng frame của
 *  STAND/RUN, chỉ vị trí thay đổi; hướng trái/phải quyết định lật ngang. */
public class AvatarAnimator {
    public static final int STAND = 0;
    public static final int RUN = 1;

    public static final int[][] FRAME = {
        {0, 0, 0, 0, 0, 1, 1, 1, 1, 1},
        {0, 0, 0, 0, 0, 2, 2, 2, 2, 2},
        {3, 3, 3, 3, 3, 3, 3, 3, 3, 3},
        {4, 4, 4, 4, 4, 4, 4, 4, 4, 4},
        {5, 5, 5, 5, 5, 5, 5, 5, 5, 5},
        {6, 6, 6, 6, 6, 6, 6, 6, 6, 6},
        {7, 7, 7, 7, 7, 7, 7, 7, 7, 7},
        {8, 8, 8, 8, 8, 8, 8, 8, 8, 8},
        {9, 9, 9, 9, 9, 9, 9, 9, 9, 9},
        {10, 10, 10, 10, 10, 10, 10, 10, 10, 10},
        {11, 11, 11, 11, 11, 11, 11, 11, 11, 11},
        {12, 12, 12, 12, 12, 12, 12, 12, 12, 12},
        {13, 13, 13, 13, 13, 13, 13, 13, 13, 13},
        {14, 14, 14, 14, 14, 14, 14, 14, 14, 14},
        {0, 0, 0, 0, 0, 1, 1, 1, 1, 1},
    };

    private int cFrame = 0;
    private float acc = 0f;
    public int action = STAND;
    /** true = quay trái (direct LEFT=2 trong gốc, lật ngang khi vẽ). */
    public boolean facingLeft = false;

    /** Gốc updateFrame mỗi tick (~20fps J2ME). Ở 60fps LibGDX, giữ nhịp 10 bước / 0.5s như gốc:
     *  một vòng cFrame 0..9 trong khoảng 0.5 giây. */
    public void update(float delta) {
        acc += delta;
        while (acc >= 0.05f) {
            acc -= 0.05f;
            cFrame++;
            if (cFrame >= 10) cFrame = 0;
        }
    }

    public int frameIndex() {
        int a = Math.max(0, Math.min(FRAME.length - 1, action));
        return FRAME[a][cFrame];
    }

    public int cFrame() { return cFrame; }
}
