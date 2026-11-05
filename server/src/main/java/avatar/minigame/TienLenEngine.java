package avatar.minigame;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import avatar.model.BoardInfo;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

/**
 * Engine Tiến Lên (casino gameType 3) — [PORT] viết mới theo giao
 * thức client gốc Pack5 (docs/tienlen-spec.md), dùng chung khung
 * lobby casino với Bầu Cua. Mọi hàm đều chạy trong
 * synchronized(board).
 */
public final class TienLenEngine {

    public static final int INTERVAL = 30; // giây mỗi lượt

    private static final ScheduledExecutorService TIMER =
        Executors.newScheduledThreadPool(2);

    private TienLenEngine() {
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

    private static void moveError(User u, byte room, BoardInfo b, String info) {
        try {
            Message ms = msg((byte) 54, room, b.boardID);
            ms.writer().writeUTF(info);
            ms.writer().flush();
            send(u, ms);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Bắt đầu ván

    public static void start(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            TlState tl = b.tl;
            if (tl.playing) return;
            User owner = b.owner();
            if (owner == null || owner.getId() != us.getId()) return;
            List<Integer> seats = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                if (b.seats[s] != null) seats.add(s);
            }
            if (seats.size() < 2) {
                us.getAvatarService().serverDialog("Cần ít nhất 2 người chơi mới bắt đầu được");
                return;
            }
            for (int s : seats) {
                User p = b.seats[s];
                if (p.getId() != us.getId() && !b.readyIds.contains(p.getId())) {
                    us.getAvatarService().serverDialog("Chờ mọi người sẵn sàng đã");
                    return;
                }
            }
            if (b.getMoney() <= 0) {
                us.getAvatarService().serverDialog("Hãy đặt mức tiền của bàn trước");
                return;
            }
            tl.resetHand();
            tl.playing = true;
            b.setPlaying(true);
            tl.playerCount = seats.size();
            // Xáo + chia 13 lá mỗi người. Bàn ít hơn 4 người thì bộ
            // bài không chia hết — lá 0 (3 bích) PHẢI nằm trong ván
            // (nước đầu ván đầu cần nó) nên bốc riêng: một ghế ngẫu
            // nhiên nhận lá 0 + 12 lá xáo, các ghế còn lại 13 lá xáo.
            List<Integer> deck = new ArrayList<>();
            for (int c = 1; c < 52; c++) deck.add(c);
            Collections.shuffle(deck);
            int holder0 = seats.get(new java.util.Random().nextInt(seats.size()));
            int idx = 0;
            for (int s : seats) {
                tl.inGame[s] = true;
                int n = s == holder0 ? 12 : 13;
                for (int k = 0; k < n; k++) tl.hands[s].add(deck.get(idx++));
                if (s == holder0) tl.hands[s].add(0);
                Collections.sort(tl.hands[s]);
            }
            // Người đi trước: ván đầu = người giữ lá 0; ván sau =
            // người thắng ván trước (còn ngồi) nếu không thì lá 0.
            int first = seatHolding(tl, 0);
            if (!tl.firstMatch && tl.lastWinnerSeat >= 0 && tl.inGame[tl.lastWinnerSeat]) {
                first = tl.lastWinnerSeat;
            }
            if (first < 0) first = seats.get(0);
            tl.firstMovePending = tl.firstMatch;
            tl.turnSeat = first;
            int firstId = b.seats[first].getId();
            try {
                for (int s : seats) {
                    Message ms = msg((byte) 20, room, b.boardID);
                    DataOutputStream ds = ms.writer();
                    ds.writeByte(INTERVAL);
                    for (int c : tl.hands[s]) ds.writeByte(c);
                    ds.writeInt(firstId);
                    ds.flush();
                    send(b.seats[s], ms);
                }
            } catch (IOException ignored) {
            }
            scheduleTurn(b, room);
        }
    }

    private static int seatHolding(TlState tl, int card) {
        for (int s = 0; s < 4; s++) {
            if (tl.inGame[s] && tl.hands[s].contains(card)) return s;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Đánh / bỏ lượt

    public static void move(Session ss, BoardInfo b, byte room, byte[] cards) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            TlState tl = b.tl;
            int seat = b.seatOf(us);
            if (!tl.playing || seat < 0 || !tl.inGame[seat] || tl.finished[seat]) return;
            if (tl.turnSeat != seat) return;
            byte[] play = cards.clone();
            CardRules.sort(play);
            // Phải là bài của mình.
            List<Integer> hand = new ArrayList<>(tl.hands[seat]);
            for (byte c : play) {
                if (!hand.remove(Integer.valueOf(c))) {
                    moveError(us, room, b, "Bài không hợp lệ");
                    return;
                }
            }
            int type = CardRules.getType(play);
            if (type < 0) {
                moveError(us, room, b, "Bài không hợp lệ");
                return;
            }
            if (tl.firstMovePending) {
                boolean has0 = false;
                for (byte c : play) if (c == 0) has0 = true;
                if (!has0) {
                    moveError(us, room, b, "Nước đầu phải có 3 bích");
                    return;
                }
            }
            int penalty = CardRules.available(play, type, tl.tableCards,
                tl.tableType, b.getMoney());
            if (penalty < 0) {
                moveError(us, room, b, "Không đè được bài trên bàn");
                return;
            }
            doMove(b, room, seat, play, type, penalty);
        }
    }

    /** Thực thi một nước đánh đã hợp lệ (cũng dùng cho tự đánh khi
     *  hết giờ). Đang ở trong synchronized(board). */
    private static void doMove(BoardInfo b, byte room, int seat, byte[] play,
            int type, int penalty) {
        TlState tl = b.tl;
        if (penalty > 0 && tl.tableSeat >= 0) {
            tl.penaltyNet[tl.tableSeat] -= penalty;
            tl.penaltyNet[seat] += penalty;
        }
        for (byte c : play) tl.hands[seat].remove(Integer.valueOf(c));
        tl.tableCards = play;
        tl.tableType = type;
        tl.tableSeat = seat;
        tl.firstMovePending = false;
        clearSkipFlags(tl);
        boolean done = tl.hands[seat].isEmpty();
        int next = nextActive(tl, seat);
        try {
            Message ms = msg((byte) 21, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(b.seats[seat].getId());
            ds.writeByte(play.length);
            for (byte c : play) ds.writeByte(c);
            ds.writeInt(done || next < 0 ? -1 : b.seats[next].getId());
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        if (done) {
            finishPlayer(b, room, seat);
            if (!tl.playing) return;
        }
        tl.turnSeat = next;
        scheduleTurn(b, room);
    }

    public static void skip(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            TlState tl = b.tl;
            int seat = b.seatOf(us);
            if (!tl.playing || seat < 0 || !tl.inGame[seat] || tl.finished[seat]) return;
            if (tl.turnSeat != seat) return;
            if (tl.tableType < 0) {
                moveError(us, room, b, "Đang dẫn vòng, không thể bỏ lượt");
                return;
            }
            doSkip(b, room, seat);
        }
    }

    private static void clearSkipFlags(TlState tl) {
        for (int i = 0; i < 4; i++) tl.skipped[i] = false;
    }

    private static void doSkip(BoardInfo b, byte room, int seat) {
        TlState tl = b.tl;
        tl.skipped[seat] = true;
        // Vòng kết thúc khi mọi người chưa về (trừ chủ bài trên bàn)
        // đều đã bỏ qua bài trên bàn.
        boolean clear = true;
        for (int s = 0; s < 4; s++) {
            if (!tl.inGame[s] || tl.finished[s] || s == tl.tableSeat) continue;
            if (!tl.skipped[s]) {
                clear = false;
                break;
            }
        }
        int next;
        if (clear) {
            // Người dẫn vòng mới: chủ bài trên bàn; nếu họ đã về thì
            // người chưa về tiếp theo sau họ.
            next = !tl.finished[tl.tableSeat] ? tl.tableSeat : nextActive(tl, tl.tableSeat);
        } else {
            next = nextActive(tl, seat);
        }
        try {
            Message ms = msg((byte) 49, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(b.seats[seat].getId());
            ds.writeInt(next < 0 ? -1 : b.seats[next].getId());
            ds.writeBoolean(clear);
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        if (clear) {
            tl.tableCards = new byte[0];
            tl.tableType = -1;
            tl.tableSeat = -1;
            clearSkipFlags(tl);
        }
        tl.turnSeat = next;
        scheduleTurn(b, room);
    }

    /** Ghế chưa về tiếp theo sau ghế from (vòng tròn), −1 nếu hết. */
    private static int nextActive(TlState tl, int from) {
        for (int k = 1; k <= 4; k++) {
            int s = (from + k) % 4;
            if (tl.inGame[s] && !tl.finished[s]) return s;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Về đích / kết thúc

    private static void finishPlayer(BoardInfo b, byte room, int seat) {
        TlState tl = b.tl;
        int pos = tl.finishCount++;
        tl.finished[seat] = true;
        tl.finishPos[seat] = pos;
        settleAndNotify(b, room, seat, pos);
        int remaining = 0, lastSeat = -1;
        for (int s = 0; s < 4; s++) {
            if (tl.inGame[s] && !tl.finished[s]) {
                remaining++;
                lastSeat = s;
            }
        }
        if (remaining == 1) {
            int pos2 = tl.finishCount++;
            tl.finished[lastSeat] = true;
            tl.finishPos[lastSeat] = pos2;
            settleAndNotify(b, room, lastSeat, pos2);
            endGame(b, room);
        }
    }

    private static void settleAndNotify(BoardInfo b, byte room, int seat, int pos) {
        TlState tl = b.tl;
        User u = b.seats[seat];
        if (u == null) return;
        int n = tl.playerCount;
        int delta = (n - 1 - 2 * pos) * b.getMoney() + tl.penaltyNet[seat];
        if (u.getXu() + delta < 0) delta = (int) -u.getXu();
        if (delta != 0) u.updateXu(delta);
        int dExp = (n - pos) * 2;
        try {
            Message ms = msg((byte) 51, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(u.getId());
            ds.writeByte(pos);
            ds.writeInt(delta);
            ds.writeInt(dExp);
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
    }

    private static void endGame(BoardInfo b, byte room) {
        TlState tl = b.tl;
        tl.playing = false;
        tl.gen++;
        b.setPlaying(false);
        tl.firstMatch = false;
        for (int s = 0; s < 4; s++) {
            if (tl.finishPos[s] == 0) tl.lastWinnerSeat = s;
        }
        b.readyIds.clear();
        try {
            Message ms = msg((byte) 50, room, b.boardID);
            ms.writer().flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Tới trắng

    public static void forceFinish(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            TlState tl = b.tl;
            int seat = b.seatOf(us);
            if (!tl.playing || seat < 0 || !tl.inGame[seat] || tl.finished[seat]) return;
            if (tl.hands[seat].size() != 13) return;
            byte[] hand = new byte[13];
            for (int i = 0; i < 13; i++) hand[i] = tl.hands[seat].get(i).byteValue();
            CardRules.sort(hand);
            if (!CardRules.checkForceFinish(hand, tl.firstMatch)) return;
            // Thắng tuyệt đối: mình về nhất; còn lại xếp theo số bài
            // còn trên tay tăng dần (hoà thì ghế nhỏ trước).
            List<Integer> others = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                if (tl.inGame[s] && s != seat) others.add(s);
            }
            others.sort((x, y) -> {
                int d = tl.hands[x].size() - tl.hands[y].size();
                return d != 0 ? d : x - y;
            });
            tl.finishPos[seat] = 0;
            tl.finished[seat] = true;
            tl.finishCount = 1;
            for (int i = 0; i < others.size(); i++) {
                tl.finishPos[others.get(i)] = i + 1;
                tl.finished[others.get(i)] = true;
                tl.finishCount++;
            }
            try {
                Message ms = msg((byte) 53, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeInt(us.getId());
                for (byte c : hand) ds.writeByte(c);
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
            settleAndNotify(b, room, seat, 0);
            for (int i = 0; i < others.size(); i++) {
                settleAndNotify(b, room, others.get(i), i + 1);
            }
            endGame(b, room);
        }
    }

    // ------------------------------------------------------------------
    // Rời bàn giữa ván → huỷ ván (STOP + lộ bài người rời)

    /** Phải gọi TRƯỚC khi gỡ người khỏi ghế. Người xem rời bàn
     *  không ảnh hưởng ván; người đang chơi rời → huỷ ván. */
    public static void onLeave(BoardInfo b, byte room, User leaver) {
        TlState tl = b.tl;
        if (tl == null || !tl.playing) return;
        int seat = b.seatOf(leaver);
        if (seat < 0 || !tl.inGame[seat]) return;
        tl.playing = false;
        tl.gen++;
        b.setPlaying(false);
        b.readyIds.clear();
        try {
            Message ms = msg((byte) 50, room, b.boardID);
            DataOutputStream ds = ms.writer();
            List<Integer> cards = tl.hands[seat];
            if (!cards.isEmpty()) {
                ds.writeInt(leaver.getId());
                ds.writeByte(cards.size());
                for (int c : cards) ds.writeByte(c);
            }
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        tl.resetHand();
    }

    // ------------------------------------------------------------------
    // Hẹn giờ lượt: hết giờ → đang dẫn thì đánh lá nhỏ nhất, đang
    // theo thì bỏ lượt.

    private static void scheduleTurn(BoardInfo b, byte room) {
        TlState tl = b.tl;
        final int gen = ++tl.gen;
        TIMER.schedule(() -> {
            synchronized (b) {
                if (!tl.playing || tl.gen != gen) return;
                int seat = tl.turnSeat;
                if (seat < 0 || !tl.inGame[seat] || tl.finished[seat]) return;
                if (tl.tableType < 0) {
                    if (tl.hands[seat].isEmpty()) return;
                    byte lowest = tl.hands[seat].get(0).byteValue();
                    doMove(b, room, seat, new byte[]{lowest},
                        CardRules.T_SINGLE, 0);
                } else {
                    doSkip(b, room, seat);
                }
            }
        }, INTERVAL, TimeUnit.SECONDS);
    }
}
