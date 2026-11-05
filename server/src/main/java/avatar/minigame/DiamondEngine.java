package avatar.minigame;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import avatar.model.BoardInfo;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

/**
 * Engine Diamond (Kim Cương) — casino gameType 21 [PORT theo client
 * gốc Pack5 DiamondScr/DiamondMessageHandler; server fan chỉ có
 * stub rỗng]. Match-3 đối kháng trên bàn 8x8 chung: hoán đổi ô kề
 * tạo hàng >= 3 thì xoá, rơi + dây chuyền, quy ra chỉ số chiến đấu
 * (docs/diamond-spec.md). Mọi hàm chạy trong synchronized(board)
 * trừ điểm vào đã khoá ở CasinoMsgHandler (start/move/skip tự khoá).
 */
public final class DiamondEngine {

    public static final int INTERVAL = 30; // giây mỗi lượt (gói 20)
    public static final int MAX_HP = 100;
    public static final int MAX_MP = 100;
    /** Quá số lượt này thì so %HP. */
    private static final int MAX_TURNS = 80;

    private static final ScheduledExecutorService TIMER =
        Executors.newScheduledThreadPool(2);
    private static final Random RND = new Random();

    private DiamondEngine() {
    }

    // ------------------------------------------------------------------
    // Tiện ích gói tin

    private static Message msg(byte cmd, byte room, byte board) throws IOException {
        Message ms = new Message(cmd);
        DataOutputStream ds = ms.writer();
        ds.writeByte(room);
        ds.writeByte(board);
        return ms;
    }

    private static void broadcast(BoardInfo b, Message ms) {
        for (User u : new ArrayList<>(b.getLstUsers())) {
            if (u != null && u.session != null) u.session.sendMessage(ms);
        }
    }

    private static void send(User u, Message ms) {
        if (u != null && u.session != null) u.session.sendMessage(ms);
    }

    // ------------------------------------------------------------------
    // Bàn cờ: sinh / tìm hàng / nước đi khả thi

    /** Tất cả ô thuộc một hàng ngang/dọc >= 3 cùng loại. */
    private static boolean[] findMatches(int[] c) {
        boolean[] m = new boolean[64];
        for (int r = 0; r < 8; r++) {
            int run = 1;
            for (int col = 1; col <= 8; col++) {
                if (col < 8 && c[r * 8 + col] == c[r * 8 + col - 1]) {
                    run++;
                } else {
                    if (run >= 3) {
                        for (int k = col - run; k < col; k++) m[r * 8 + k] = true;
                    }
                    run = 1;
                }
            }
        }
        for (int col = 0; col < 8; col++) {
            int run = 1;
            for (int r = 1; r <= 8; r++) {
                if (r < 8 && c[r * 8 + col] == c[(r - 1) * 8 + col]) {
                    run++;
                } else {
                    if (run >= 3) {
                        for (int k = r - run; k < r; k++) m[k * 8 + col] = true;
                    }
                    run = 1;
                }
            }
        }
        return m;
    }

    private static boolean anyMatch(boolean[] m) {
        for (boolean x : m) if (x) return true;
        return false;
    }

    /** Còn nước hoán đổi nào tạo hàng không (kiểm mọi cặp kề). */
    private static boolean hasMove(int[] c) {
        int[][] dirs = {{0, 1}, {1, 0}};
        for (int i = 0; i < 64; i++) {
            for (int[] d : dirs) {
                int r2 = i / 8 + d[0], c2 = i % 8 + d[1];
                if (r2 >= 8 || c2 >= 8) continue;
                int j = r2 * 8 + c2;
                int t = c[i]; c[i] = c[j]; c[j] = t;
                boolean ok = anyMatch(findMatches(c));
                t = c[i]; c[i] = c[j]; c[j] = t;
                if (ok) return true;
            }
        }
        return false;
    }

    /** Sinh bàn mới: không có hàng sẵn, có ít nhất một nước đi. */
    private static void genBoard(int[] c) {
        for (int attempt = 0; attempt < 200; attempt++) {
            for (int i = 0; i < 64; i++) c[i] = RND.nextInt(6);
            // phá các hàng sẵn có bằng cách đổi loại một ô trong hàng
            for (int guard = 0; guard < 64; guard++) {
                boolean[] m = findMatches(c);
                if (!anyMatch(m)) break;
                for (int i = 0; i < 64; i++) {
                    if (m[i]) { c[i] = RND.nextInt(6); break; }
                }
            }
            if (!anyMatch(findMatches(c)) && hasMove(c)) return;
        }
    }

    // ------------------------------------------------------------------
    // START

    public static void start(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            DiamondState dm = b.diamond;
            if (dm.playing) return;
            User owner = b.owner();
            if (owner == null || owner.getId() != us.getId()) return;
            if (b.seats[0] == null || b.seats[1] == null) {
                us.getAvatarService().serverDialog("Diamond cần đúng 2 người chơi");
                return;
            }
            User other = b.seats[0].getId() == us.getId() ? b.seats[1] : b.seats[0];
            if (!b.readyIds.contains(other.getId())) {
                us.getAvatarService().serverDialog("Chờ đối thủ sẵn sàng đã");
                return;
            }
            if (b.getMoney() <= 0) {
                us.getAvatarService().serverDialog("Hãy đặt mức tiền của bàn trước");
                return;
            }
            dm.reset();
            dm.playing = true;
            b.setPlaying(true);
            for (int s = 0; s < 2; s++) dm.seatIds[s] = b.seats[s].getId();
            genBoard(dm.cells);
            dm.turnSeat = RND.nextInt(2);
            dm.gen++;
            try {
                Message ms = msg((byte) 20, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeByte(INTERVAL);
                ds.writeInt(dm.seatIds[dm.turnSeat]);
                for (int i = 0; i < 64; i++) ds.writeByte(dm.cells[i]);
                for (int s = 0; s < 2; s++) {
                    ds.writeInt(dm.seatIds[s]);
                    ds.writeShort(dm.def[s]);
                    ds.writeShort(dm.hp[s]);
                    ds.writeShort(dm.mp[s]);
                    ds.writeShort(MAX_MP);
                }
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
            scheduleTimeout(b, room);
        }
    }

    /** Gói 71 cho người vào bàn giữa ván: bàn cờ + chỉ số + lượt. */
    public static void sendData(User us, BoardInfo b, byte room) {
        DiamondState dm = b.diamond;
        if (!dm.playing) return;
        try {
            Message ms = msg((byte) 71, room, b.boardID);
            DataOutputStream ds = ms.writer();
            for (int i = 0; i < 64; i++) ds.writeByte(dm.cells[i]);
            for (int s = 0; s < 2; s++) {
                ds.writeInt(dm.seatIds[s]);
                ds.writeShort(dm.def[s]);
                ds.writeShort(dm.hp[s]);
                ds.writeShort(dm.mp[s]);
                ds.writeShort(MAX_MP);
            }
            ds.writeInt(dm.turnSeat >= 0 ? dm.seatIds[dm.turnSeat] : -1);
            ds.flush();
            send(us, ms);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // MOVE / giải dây chuyền

    public static void move(Session ss, BoardInfo b, byte room, int iSel, int sel) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            DiamondState dm = b.diamond;
            if (!dm.playing) return;
            int seat = dm.turnSeat;
            if (seat < 0 || dm.seatIds[seat] != us.getId()) return;
            if (iSel < 0 || iSel >= 64 || sel < 0 || sel >= 64) return;
            int dr = Math.abs(iSel / 8 - sel / 8), dc = Math.abs(iSel % 8 - sel % 8);
            if (dr + dc != 1) return;

            int[] c = dm.cells;
            int t = c[iSel]; c[iSel] = c[sel]; c[sel] = t;
            if (!anyMatch(findMatches(c))) {
                // Hoán đổi không tạo hàng: trả lại + báo riêng người đi
                // gói 21 đảo ngược để client chạy hoạt ảnh trả ô.
                t = c[iSel]; c[iSel] = c[sel]; c[sel] = t;
                try {
                    Message ms = msg((byte) 21, room, b.boardID);
                    DataOutputStream ds = ms.writer();
                    ds.writeInt(us.getId());
                    ds.writeByte(sel);
                    ds.writeByte(iSel);
                    ds.flush();
                    send(us, ms);
                } catch (IOException ignored) {
                }
                return;
            }
            // Đầu lượt: +2 MP cho người đi (phản ánh ở gói 64).
            dm.mp[seat] = Math.min(MAX_MP, dm.mp[seat] + 2);
            dm.turnCount++;
            try {
                Message ms = msg((byte) 21, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeInt(us.getId());
                ds.writeByte(iSel);
                ds.writeByte(sel);
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
            resolve(b, room, seat);
        }
    }

    /** Giải toàn bộ dây chuyền sau một hoán đổi hợp lệ, mỗi đợt một
     *  gói 64, sau đó phân thắng / đổi lượt / phát bàn mới. */
    private static void resolve(BoardInfo b, byte room, int mover) {
        DiamondState dm = b.diamond;
        int[] c = dm.cells;
        int opp = 1 - mover;
        int combo = 0;
        while (true) {
            boolean[] match = findMatches(c);
            if (!anyMatch(match)) break;
            combo++;
            // Ô bom (loại 4) trong hàng: nổ lan 4 ô kề trực giao.
            boolean[] clear = match.clone();
            for (int i = 0; i < 64; i++) {
                if (match[i] && c[i] == 4) {
                    int r = i / 8, col = i % 8;
                    if (r > 0) clear[i - 8] = true;
                    if (r < 7) clear[i + 8] = true;
                    if (col > 0) clear[i - 1] = true;
                    if (col < 7) clear[i + 1] = true;
                }
            }
            // Chỉ số từ các ô bị xoá (theo loại của người đi).
            int atk = 0;
            for (int i = 0; i < 64; i++) {
                if (!clear[i]) continue;
                switch (c[i]) {
                    case 0: atk += 6; break;
                    case 1: dm.def[mover] = Math.min(30, dm.def[mover] + 3); break;
                    case 2: dm.hp[mover] = Math.min(MAX_HP, dm.hp[mover] + 4); break;
                    case 3: dm.mp[mover] = Math.min(MAX_MP, dm.mp[mover] + 5); break;
                    case 4: atk += 3; break;
                    case 5:
                        switch (RND.nextInt(4)) {
                            case 0: atk += 4; break;
                            case 1: dm.def[mover] = Math.min(30, dm.def[mover] + 4); break;
                            case 2: dm.hp[mover] = Math.min(MAX_HP, dm.hp[mover] + 4); break;
                            default: dm.mp[mover] = Math.min(MAX_MP, dm.mp[mover] + 4); break;
                        }
                        break;
                    default: break;
                }
            }
            atk *= combo;
            // Tấn công: ATK >= 5 và MP >= 10; khiên đối thủ hấp thụ trước.
            int fight = 0;
            boolean isNo = false;
            if (atk >= 5) {
                if (dm.mp[mover] >= 10) {
                    dm.mp[mover] -= 10;
                    fight = atk;
                    int absorbed = Math.min(dm.def[opp], atk);
                    dm.def[opp] -= absorbed;
                    dm.hp[opp] -= (atk - absorbed);
                } else {
                    isNo = true;
                }
            }
            // Trọng lực + sinh ô mới; ghi các ô thay đổi cho gói 64.
            List<int[]> changed = new ArrayList<>();
            for (int col = 0; col < 8; col++) {
                int[] pre = new int[8];
                for (int r = 0; r < 8; r++) pre[r] = c[r * 8 + col];
                List<int[]> survivors = new ArrayList<>(); // {loại, hàng gốc}
                for (int r = 7; r >= 0; r--) {
                    if (!clear[r * 8 + col]) survivors.add(new int[]{pre[r], r});
                }
                int nNew = 8 - survivors.size();
                for (int r = 7; r >= 0; r--) {
                    int idx = 7 - r; // vị trí trong danh sách từ dưới lên
                    int type, fall;
                    if (idx < survivors.size()) {
                        type = survivors.get(idx)[0];
                        fall = r - survivors.get(idx)[1];
                    } else {
                        type = RND.nextInt(6);
                        fall = r + 1 + (idx - survivors.size());
                    }
                    if (type != pre[r] || fall > 0) {
                        changed.add(new int[]{r * 8 + col, fall, type});
                    }
                    c[r * 8 + col] = type;
                }
            }
            try {
                Message ms = msg((byte) 64, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeByte(changed.size());
                for (int[] e : changed) {
                    ds.writeByte(e[0]);
                    ds.writeByte(e[1]);
                    ds.writeByte(e[2]);
                }
                ds.writeByte(combo);
                ds.writeByte(0); // nText — bản port không dùng text bay
                for (int s = 0; s < 2; s++) {
                    ds.writeInt(dm.seatIds[s]);
                    ds.writeByte(s == mover ? fight : 0);
                    ds.writeShort(dm.def[s]);
                    ds.writeShort(dm.hp[s]);
                    ds.writeShort(dm.mp[s]);
                    ds.writeBoolean(s == mover && isNo);
                }
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
            if (dm.hp[opp] <= 0 || dm.hp[mover] <= 0) {
                finish(b, room, dm.hp[opp] <= 0 ? mover : opp);
                return;
            }
        }
        if (dm.turnCount >= MAX_TURNS) {
            int winner = dm.hp[mover] == dm.hp[opp] ? -1
                : (dm.hp[mover] > dm.hp[opp] ? mover : opp);
            finish(b, room, winner);
            return;
        }
        // Bàn hết nước đi: phát bàn mới, lượt vẫn chuyển như thường.
        int next = 1 - mover;
        dm.turnSeat = next;
        dm.gen++;
        if (!hasMove(c)) {
            genBoard(c);
            try {
                Message ms = msg((byte) 24, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeInt(dm.seatIds[next]);
                for (int i = 0; i < 64; i++) ds.writeByte(c[i]);
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
        } else {
            sendSkip(b, room, next);
        }
        scheduleTimeout(b, room);
    }

    /** Phát gói 49 {int whoMove} = người tới lượt. */
    private static void sendSkip(BoardInfo b, byte room, int nextSeat) {
        try {
            Message ms = msg((byte) 49, room, b.boardID);
            ms.writer().writeInt(b.diamond.seatIds[nextSeat]);
            ms.writer().flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // SKIP / hết giờ / rời bàn

    public static void skip(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            DiamondState dm = b.diamond;
            if (!dm.playing || dm.turnSeat < 0) return;
            if (dm.seatIds[dm.turnSeat] != us.getId()) return;
            passTurn(b, room);
        }
    }

    private static void passTurn(BoardInfo b, byte room) {
        DiamondState dm = b.diamond;
        dm.turnCount++;
        if (dm.turnCount >= MAX_TURNS) {
            int winner = dm.hp[0] == dm.hp[1] ? -1 : (dm.hp[0] > dm.hp[1] ? 0 : 1);
            finish(b, room, winner);
            return;
        }
        dm.turnSeat = 1 - dm.turnSeat;
        dm.gen++;
        sendSkip(b, room, dm.turnSeat);
        scheduleTimeout(b, room);
    }

    private static void scheduleTimeout(BoardInfo b, byte room) {
        final int gen = b.diamond.gen;
        TIMER.schedule(() -> {
            synchronized (b) {
                DiamondState dm = b.diamond;
                if (!dm.playing || dm.gen != gen) return;
                passTurn(b, room);
            }
        }, (INTERVAL + 3) * 1000L, TimeUnit.MILLISECONDS);
    }

    /** Người dự ván rời giữa ván = thua (đối thủ thắng mức bàn). */
    public static void onLeave(BoardInfo b, byte room, User leaver) {
        DiamondState dm = b.diamond;
        if (!dm.playing) return;
        int seat = dm.seatIds[0] == leaver.getId() ? 0
            : dm.seatIds[1] == leaver.getId() ? 1 : -1;
        if (seat < 0) return;
        finish(b, room, 1 - seat);
    }

    // ------------------------------------------------------------------
    // FINISH

    /** winnerSeat: 0/1, hoặc -1 = hoà. Cộng/trừ xu zero-sum. */
    private static void finish(BoardInfo b, byte room, int winnerSeat) {
        DiamondState dm = b.diamond;
        dm.playing = false;
        dm.gen++;
        b.setPlaying(false);
        b.readyIds.clear();
        int money = b.getMoney();
        int[] delta = new int[2];
        if (winnerSeat >= 0 && money > 0) {
            User loser = b.seats[1 - winnerSeat];
            User winner = b.seats[winnerSeat];
            // Người thua trả tối đa số xu đang có; thắng nhận đúng phần đó.
            long pay = loser == null ? money
                : Math.min((long) money, Math.max(0L, loser.getXu()));
            if (loser != null && pay > 0) {
                loser.updateXu(-pay);
                delta[1 - winnerSeat] = (int) -pay;
            }
            if (winner != null && pay > 0) {
                winner.updateXu(pay);
                delta[winnerSeat] = (int) pay;
            }
        }
        try {
            Message ms = msg((byte) 51, room, b.boardID);
            DataOutputStream ds = ms.writer();
            for (int s = 0; s < 2; s++) {
                ds.writeInt(dm.seatIds[s]);
                ds.writeInt(delta[s]);
            }
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        dm.turnSeat = -1;
    }
}
