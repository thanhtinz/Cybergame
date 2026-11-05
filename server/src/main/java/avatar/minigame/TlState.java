package avatar.minigame;

import java.util.ArrayList;
import java.util.List;

/** Trạng thái một ván Tiến Lên trên một bàn casino (4 ghế). */
public class TlState {
    public boolean playing = false;
    /** Ván đầu tiên kể từ khi bàn trống — bật luật 4 lá 3 tới trắng
     *  và nước đầu phải có lá 0 (3 bích) như client gốc. */
    public boolean firstMatch = true;
    /** Bài từng ghế (đã sắp tăng dần), null nếu ghế không dự ván. */
    public final List<Integer>[] hands = new List[4];
    public final boolean[] inGame = new boolean[4];
    public final boolean[] finished = new boolean[4];
    public final int[] finishPos = {-1, -1, -1, -1};
    public int finishCount = 0;
    public int playerCount = 0;
    /** Bài đang nằm trên bàn + ghế đã đánh + loại tổ hợp. */
    public byte[] tableCards = new byte[0];
    public int tableType = -1;
    public int tableSeat = -1;
    public int turnSeat = -1;
    /** Net phạt chặt theo ghế: dương = được nhận, âm = phải trả. */
    public final int[] penaltyNet = new int[4];
    /** Ai đã bỏ qua bài trên bàn trong vòng hiện tại. */
    public final boolean[] skipped = new boolean[4];
    /** Tăng mỗi lượt — hẹn giờ lượt cũ tự vô hiệu. */
    public int gen = 0;
    public int lastWinnerSeat = -1;
    /** Nước đi đầu tiên của ván đầu: bắt buộc gồm lá 0. */
    public boolean firstMovePending = false;

    public TlState() {
        for (int i = 0; i < 4; i++) hands[i] = new ArrayList<>();
    }

    public void resetHand() {
        for (int i = 0; i < 4; i++) {
            hands[i].clear();
            inGame[i] = false;
            finished[i] = false;
            finishPos[i] = -1;
            penaltyNet[i] = 0;
            skipped[i] = false;
        }
        finishCount = 0;
        playerCount = 0;
        tableCards = new byte[0];
        tableType = -1;
        tableSeat = -1;
        turnSeat = -1;
        firstMovePending = false;
    }
}
