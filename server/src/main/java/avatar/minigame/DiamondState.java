package avatar.minigame;

/** Trạng thái một ván Diamond (Kim Cương) trên một bàn casino
 *  (2 ghế = ghế 0 và 1 của BoardInfo). Xem docs/diamond-spec.md. */
public class DiamondState {
    public boolean playing = false;
    /** Bàn cờ 8x8, index = hàng*8 + cột, giá trị = loại 0..5. */
    public final int[] cells = new int[64];
    /** Chỉ số theo ghế: HP / MP / defence (khiên). */
    public final int[] hp = new int[2];
    public final int[] mp = new int[2];
    public final int[] def = new int[2];
    /** User id của 2 ghế dự ván (ảnh chụp lúc START). */
    public final int[] seatIds = new int[2];
    public int turnSeat = -1;
    /** Tổng số lượt đã đi (để chốt hoà/thắng theo %HP khi quá dài). */
    public int turnCount = 0;
    /** Tăng mỗi lượt — hẹn giờ lượt cũ tự vô hiệu. */
    public int gen = 0;

    public void reset() {
        playing = false;
        turnSeat = -1;
        turnCount = 0;
        for (int s = 0; s < 2; s++) {
            hp[s] = 100;
            mp[s] = 40;
            def[s] = 0;
            seatIds[s] = -1;
        }
    }
}
