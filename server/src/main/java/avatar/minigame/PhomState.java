package avatar.minigame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trạng thái một ván Phỏm theo bàn (docs/phom-spec.md).
 * Mọi truy cập đều trong synchronized(board).
 */
public class PhomState {
    public boolean playing = false;
    /** Bài trên tay từng ghế (đã sắp tăng dần). */
    public final List<Integer>[] hands = new List[4];
    /** Lá thuộc phỏm ĂN — khoá, không được đánh ra. */
    public final Set<Integer>[] locked = new Set[4];
    /** Mọi phỏm đã hình thành theo ghế (ăn + hạ), mỗi phỏm một
     *  danh sách lá đã sắp. Dùng cho gửi bài + tính cháy. */
    public final List<List<Integer>>[] melds = new List[4];
    /** Chồng bài đã đánh theo ghế, theo thứ tự đánh. */
    public final List<Integer>[] discards = new List[4];
    /** Nọc còn lại. */
    public final List<Integer> deck = new ArrayList<>();
    public final boolean[] inGame = new boolean[4];
    public final int[] fireCount = new int[4];
    public final boolean[] haDone = new boolean[4];
    /** Thứ tự hạ (1, 2, 3, 4 — 0 = chưa hạ) để phân thắng hoà điểm. */
    public final int[] haOrder = new int[4];
    public int haCounter = 0;
    public int playerCount = 0;
    /** Ghế đang tới lượt; pha: 0 chờ Ăn/Bốc · 1 chờ Hạ (nếu lượt
     *  cuối) rồi Đánh. */
    public int turnSeat = -1;
    public int phase = 0;
    /** Lá trên bàn đang ăn được + ghế đã đánh ra (−1 = không có). */
    public int tableCard = -1;
    public int tableFromSeat = -1;
    /** Tăng mỗi lượt — hẹn giờ lượt cũ tự vô hiệu. */
    public int gen = 0;
    public int lastWinnerSeat = -1;

    public PhomState() {
        for (int i = 0; i < 4; i++) {
            hands[i] = new ArrayList<>();
            locked[i] = new HashSet<>();
            melds[i] = new ArrayList<>();
            discards[i] = new ArrayList<>();
        }
    }

    public void resetHand() {
        for (int i = 0; i < 4; i++) {
            hands[i].clear();
            locked[i].clear();
            melds[i].clear();
            discards[i].clear();
            inGame[i] = false;
            fireCount[i] = 0;
            haDone[i] = false;
            haOrder[i] = 0;
        }
        deck.clear();
        haCounter = 0;
        playerCount = 0;
        turnSeat = -1;
        phase = 0;
        tableCard = -1;
        tableFromSeat = -1;
    }
}
