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
 * Engine Phỏm / Tá lả (casino gameType 7) — [PORT] viết mới theo
 * giao thức + luật client gốc Pack5 (docs/phom-spec.md), dùng
 * chung khung lobby casino với Bầu Cua / Tiến Lên. Mọi hàm đều
 * chạy trong synchronized(board).
 */
public final class PhomEngine {

    public static final int INTERVAL = 30; // giây mỗi lượt

    private static final ScheduledExecutorService TIMER =
        Executors.newScheduledThreadPool(2);

    private PhomEngine() {
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

    private static int idAt(BoardInfo b, int seat) {
        return seat >= 0 && b.seats[seat] != null ? b.seats[seat].getId() : -1;
    }

    private static int nextActiveSeat(PhomState ph, int from) {
        for (int k = 1; k <= 4; k++) {
            int s = (from + k) % 4;
            if (ph.inGame[s] && ph.fireCount[s] < 4) return s;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Bắt đầu ván

    public static void start(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            PhomState ph = b.phom;
            if (ph.playing) return;
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
            ph.resetHand();
            ph.playing = true;
            b.setPlaying(true);
            ph.playerCount = seats.size();
            // Chia 9 lá mỗi người; lá 0 (A bích) luôn vào ván và
            // người giữ nó đi trước ở ván đầu (như lá 0 của TL).
            List<Integer> deck = new ArrayList<>();
            for (int c = 1; c < 52; c++) deck.add(c);
            Collections.shuffle(deck);
            int holder0 = seats.get(new java.util.Random().nextInt(seats.size()));
            int idx = 0;
            for (int s : seats) {
                ph.inGame[s] = true;
                if (s == holder0) ph.hands[s].add(0);
                int n = s == holder0 ? 8 : 9;
                for (int k = 0; k < n; k++) ph.hands[s].add(deck.get(idx++));
                Collections.sort(ph.hands[s]);
            }
            for (int k = idx; k < deck.size(); k++) ph.deck.add(deck.get(k));
            int first = ph.lastWinnerSeat >= 0 && ph.inGame[ph.lastWinnerSeat]
                ? ph.lastWinnerSeat : holder0;
            ph.turnSeat = first;
            ph.phase = 0;
            int firstId = idAt(b, first);
            for (int s : seats) {
                try {
                    Message ms = msg((byte) 20, room, b.boardID);
                    DataOutputStream ds = ms.writer();
                    ds.writeByte(INTERVAL);
                    for (int c : ph.hands[s]) ds.writeByte(c);
                    ds.writeInt(firstId);
                    ds.writeInt(firstId); // firHa khởi tạo = người đi trước
                    ds.flush();
                    send(b.seats[s], ms);
                } catch (IOException ignored) {
                }
            }
            scheduleTurn(b, room);
        }
    }

    // ------------------------------------------------------------------
    // Bốc bài (63)

    public static void getCard(Session ss, BoardInfo b, byte room) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            PhomState ph = b.phom;
            if (!ph.playing) return;
            int seat = b.seatOf(us);
            if (seat < 0 || seat != ph.turnSeat || ph.phase != 0) return;
            if (ph.deck.isEmpty()) {
                // hết nọc mà không ăn được → bỏ lượt
                skipTurn(b, room, seat);
                return;
            }
            int card = ph.deck.remove(ph.deck.size() - 1);
            ph.hands[seat].add(card);
            Collections.sort(ph.hands[seat]);
            ph.phase = 1;
            try {
                Message ms = msg((byte) 63, room, b.boardID);
                ms.writer().writeByte(card);
                ms.writer().flush();
                send(us, ms);
            } catch (IOException ignored) {
            }
            if (checkU(b, room, seat, -1)) return;
            scheduleTurn(b, room);
        }
    }

    // ------------------------------------------------------------------
    // Ăn bài (64): handCards = các lá trên tay tạo phỏm với lá trên bàn

    public static void eat(Session ss, BoardInfo b, byte room, int[] handCards) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            PhomState ph = b.phom;
            if (!ph.playing) return;
            int seat = b.seatOf(us);
            int firer = ph.tableFromSeat;
            int firHaId = firer >= 0 ? idAt(b, firer) : -1;
            boolean ok = seat >= 0 && seat == ph.turnSeat && ph.phase == 0
                && ph.tableCard >= 0 && firer >= 0 && firer != seat
                && handCards.length >= 2;
            List<Integer> group = new ArrayList<>();
            if (ok) {
                for (int c : handCards) {
                    if (!ph.hands[seat].contains(c) || group.contains(c)) {
                        ok = false;
                        break;
                    }
                    group.add(c);
                }
            }
            if (ok) {
                group.add(ph.tableCard);
                Collections.sort(group);
                ok = PhomRules.isPhom(group);
            }
            if (!ok) {
                try {
                    Message ms = msg((byte) 64, room, b.boardID);
                    DataOutputStream ds = ms.writer();
                    ds.writeInt(firHaId);
                    ds.writeBoolean(false);
                    ds.writeByte(0); // nCard
                    ds.flush();
                    send(us, ms);
                } catch (IOException ignored) {
                }
                return;
            }
            int eaten = ph.tableCard;
            int numEat = handCards.length;
            for (int c : handCards) ph.hands[seat].remove(Integer.valueOf(c));
            ph.hands[seat].add(eaten);
            Collections.sort(ph.hands[seat]);
            ph.locked[seat].addAll(group);
            ph.melds[seat].add(group);
            // gỡ lá bị ăn khỏi chồng bài của người đánh
            List<Integer> pile = ph.discards[firer];
            if (!pile.isEmpty() && pile.get(pile.size() - 1) == eaten) {
                pile.remove(pile.size() - 1);
            } else {
                pile.remove(Integer.valueOf(eaten));
            }
            ph.tableCard = -1;
            ph.tableFromSeat = -1;
            ph.phase = 1;
            try {
                Message ms = msg((byte) 64, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeInt(firHaId);
                ds.writeBoolean(true);
                ds.writeByte(numEat);
                ds.writeByte(pile.size() - 1);
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
            // Ù do ăn: lá bị ăn là lá chốt (người đánh đã đánh lá
            // thứ 4 của họ) → người đánh đền ù
            int denSeat = ph.fireCount[firer] == 4 ? firer : -1;
            if (checkU(b, room, seat, denSeat)) return;
            scheduleTurn(b, room);
        }
    }

    // ------------------------------------------------------------------
    // Đánh bài (21)

    public static void fire(Session ss, BoardInfo b, byte room, int card) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            PhomState ph = b.phom;
            if (!ph.playing) return;
            int seat = b.seatOf(us);
            if (seat < 0 || seat != ph.turnSeat) return;
            if (!canFire(ph, seat, card)) {
                fireError(b, room, seat);
                return;
            }
            doFire(b, room, seat, card);
        }
    }

    private static boolean canFire(PhomState ph, int seat, int card) {
        if (ph.phase != 1) return false;
        if (ph.fireCount[seat] >= 3 && !ph.haDone[seat]) return false; // phải hạ trước
        if (!ph.hands[seat].contains(card)) return false;
        return !ph.locked[seat].contains(card);
    }

    private static void fireError(BoardInfo b, byte room, int seat) {
        try {
            Message ms = msg((byte) 21, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(idAt(b, seat));
            ds.writeInt(idAt(b, seat));
            ds.writeByte(-1);
            ds.writeByte(0);
            ds.flush();
            send(b.seats[seat], ms);
        } catch (IOException ignored) {
        }
    }

    private static void doFire(BoardInfo b, byte room, int seat, int card) {
        PhomState ph = b.phom;
        ph.hands[seat].remove(Integer.valueOf(card));
        ph.discards[seat].add(card);
        ph.fireCount[seat]++;
        ph.tableCard = card;
        ph.tableFromSeat = seat;
        int next = nextActiveSeat(ph, seat);
        try {
            Message ms = msg((byte) 21, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(next >= 0 ? idAt(b, next) : -1);
            ds.writeInt(idAt(b, seat));
            ds.writeByte(card);
            ds.writeByte(ph.discards[seat].size() - 1);
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        if (next < 0) {
            finishGame(b, room);
            return;
        }
        ph.turnSeat = next;
        ph.phase = 0;
        scheduleTurn(b, room);
    }

    // ------------------------------------------------------------------
    // Hạ phỏm (65): groups = các nhóm lá đã kiểm

    public static void haPhom(Session ss, BoardInfo b, byte room,
                              List<List<Integer>> groups) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            PhomState ph = b.phom;
            if (!ph.playing) return;
            int seat = b.seatOf(us);
            // Hạ trắng (không nhóm nào) hợp lệ: người không có
            // phỏm nào vẫn phải hạ để đánh lá cuối như gốc.
            boolean ok = seat >= 0 && seat == ph.turnSeat && ph.phase == 1
                && ph.fireCount[seat] == 3 && !ph.haDone[seat]
                && groups != null;
            List<Integer> used = new ArrayList<>();
            if (ok) {
                for (List<Integer> g : groups) {
                    List<Integer> gs = new ArrayList<>(g);
                    Collections.sort(gs);
                    if (!PhomRules.isPhom(gs)) { ok = false; break; }
                    for (int c : gs) {
                        if (!ph.hands[seat].contains(c) || used.contains(c)
                            || ph.locked[seat].contains(c)) {
                            ok = false;
                            break;
                        }
                        used.add(c);
                    }
                    if (!ok) break;
                }
            }
            if (!ok) {
                try {
                    Message ms = msg((byte) 65, room, b.boardID);
                    DataOutputStream ds = ms.writer();
                    ds.writeInt(idAt(b, seat));
                    ds.writeBoolean(false);
                    ds.writeBoolean(false);
                    ds.flush();
                    send(us, ms);
                } catch (IOException ignored) {
                }
                return;
            }
            applyHa(b, room, seat, groups);
        }
    }

    /** Áp một lần hạ hợp lệ + phát 65; trả true nếu ván kết thúc
     *  vì Ù khi hạ. */
    private static boolean applyHa(BoardInfo b, byte room, int seat,
                                   List<List<Integer>> groups) {
        PhomState ph = b.phom;
        List<List<Integer>> sorted = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        for (List<Integer> g : groups) {
            List<Integer> gs = new ArrayList<>(g);
            Collections.sort(gs);
            sorted.add(gs);
            used.addAll(gs);
        }
        ph.melds[seat].addAll(sorted);
        ph.haDone[seat] = true;
        ph.haOrder[seat] = ++ph.haCounter;
        // Ù khi hạ: mọi lá trên tay đều đã vào phỏm (ăn + hạ)
        List<Integer> covered = coveredCards(ph, seat);
        boolean isU = covered.size() == ph.hands[seat].size()
            && !ph.hands[seat].isEmpty();
        int[] deltas = new int[4];
        if (isU) deltas = computeUDeltas(b, seat, -1);
        try {
            Message ms = msg((byte) 65, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(idAt(b, seat));
            ds.writeBoolean(true);
            ds.writeBoolean(isU);
            for (int s = 0; s < 4; s++) ds.writeInt(deltas[s]);
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) ds.writeByte(-1);
                for (int c : sorted.get(i)) ds.writeByte(c);
            }
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        if (isU) {
            applyDeltas(b, deltas);
            endGame(b, seat);
            return true;
        }
        scheduleTurn(b, room); // vẫn lượt người này — chờ đánh lá cuối
        return false;
    }

    // ------------------------------------------------------------------
    // Gửi bài tay (67)

    public static void addCard(Session ss, BoardInfo b, byte room, int[] cards) {
        User us = ss.user;
        if (us == null) return;
        synchronized (b) {
            PhomState ph = b.phom;
            if (!ph.playing) return;
            int seat = b.seatOf(us);
            if (seat < 0 || !ph.haDone[seat]) return;
            for (int card : cards) {
                boolean ok = ph.hands[seat].contains(card)
                    && !ph.locked[seat].contains(card);
                List<Integer> target = null;
                if (ok) {
                    outer:
                    for (int s = 0; s < 4; s++) {
                        for (List<Integer> meld : ph.melds[s]) {
                            if (PhomRules.extendsPhom(meld, card)) {
                                target = meld;
                                break outer;
                            }
                        }
                    }
                    ok = target != null;
                }
                if (ok) {
                    target.add(card);
                    Collections.sort(target);
                    try {
                        Message ms = msg((byte) 67, room, b.boardID);
                        DataOutputStream ds = ms.writer();
                        ds.writeBoolean(true);
                        ds.writeByte(card);
                        ds.flush();
                        broadcast(b, ms);
                    } catch (IOException ignored) {
                    }
                } else {
                    try {
                        Message ms = msg((byte) 67, room, b.boardID);
                        DataOutputStream ds = ms.writer();
                        ds.writeBoolean(false);
                        ds.writeByte(card);
                        ds.flush();
                        send(us, ms);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Ù + quyết toán

    /** Kiểm tra ù cho ghế vừa Ăn/Bốc. denSeat ≥ 0 = ghế đền ù. */
    private static boolean checkU(BoardInfo b, byte room, int seat, int denSeat) {
        PhomState ph = b.phom;
        if (!PhomRules.isFullPhom(ph.hands[seat])) return false;
        int[] deltas = computeUDeltas(b, seat, denSeat);
        applyDeltas(b, deltas);
        if (denSeat >= 0) {
            try {
                Message ms = msg((byte) 69, room, b.boardID);
                DataOutputStream ds = ms.writer();
                ds.writeInt(idAt(b, seat));
                for (int s = 0; s < 4; s++) ds.writeInt(deltas[s]);
                ds.flush();
                broadcast(b, ms);
            } catch (IOException ignored) {
            }
        } else {
            try {
                Message ms = msg((byte) 70, room, b.boardID);
                ms.writer().writeInt(deltas[seat]);
                ms.writer().flush();
                send(b.seats[seat], ms);
            } catch (IOException ignored) {
            }
            // những người còn lại nhận FINISH với score −1 cho người ù
            sendFinish(b, room, deltas, seat);
        }
        endGame(b, seat);
        return true;
    }

    /** Điểm rác hiện tại của một ghế: phần tay chưa vào phỏm ăn/hạ,
     *  phân hoạch tối ưu phần còn lại. */
    private static int currentRacScore(PhomState ph, int seat) {
        List<Integer> covered = coveredCards(ph, seat);
        List<Integer> rest = new ArrayList<>(ph.hands[seat]);
        rest.removeAll(covered);
        return PhomRules.bestPartition(rest).racScore;
    }

    private static List<Integer> coveredCards(PhomState ph, int seat) {
        List<Integer> covered = new ArrayList<>();
        for (List<Integer> meld : ph.melds[seat]) covered.addAll(meld);
        return covered;
    }

    private static int[] computeUDeltas(BoardInfo b, int winnerSeat, int denSeat) {
        PhomState ph = b.phom;
        int money = b.getMoney();
        int[] deltas = new int[4];
        int total = 0;
        for (int s = 0; s < 4; s++) {
            if (!ph.inGame[s] || s == winnerSeat) continue;
            int share = (10 + currentRacScore(ph, s)) * money;
            if (denSeat >= 0) {
                total += share;
            } else {
                int pay = clampPay(b.seats[s], share);
                deltas[s] = -pay;
                total += pay;
            }
        }
        if (denSeat >= 0) {
            int pay = clampPay(b.seats[denSeat], total);
            deltas[denSeat] = -pay;
            deltas[winnerSeat] = pay;
        } else {
            deltas[winnerSeat] = total;
        }
        return deltas;
    }

    private static int clampPay(User u, int amount) {
        if (u == null) return 0;
        return (int) Math.min(amount, Math.max(0, u.getXu()));
    }

    private static void applyDeltas(BoardInfo b, int[] deltas) {
        for (int s = 0; s < 4; s++) {
            if (deltas[s] != 0 && b.seats[s] != null) {
                b.seats[s].updateXu(deltas[s]);
            }
        }
    }

    private static void endGame(BoardInfo b, int winnerSeat) {
        PhomState ph = b.phom;
        ph.playing = false;
        ph.gen++;
        ph.lastWinnerSeat = winnerSeat;
        b.setPlaying(false);
        b.readyIds.clear();
    }

    // ------------------------------------------------------------------
    // Kết ván thường: gửi tự động → tính điểm → FINISH (51)

    private static void finishGame(BoardInfo b, byte room) {
        PhomState ph = b.phom;
        // gửi tự động: lá rác gắn được vào phỏm trên bàn thì gắn
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int s = 0; s < 4 && !changed; s++) {
                if (!ph.inGame[s]) continue;
                List<Integer> covered = coveredCards(ph, s);
                for (int card : new ArrayList<>(ph.hands[s])) {
                    if (covered.contains(card)) continue;
                    boolean attached = false;
                    outer:
                    for (int t = 0; t < 4; t++) {
                        for (List<Integer> meld : ph.melds[t]) {
                            if (PhomRules.extendsPhom(meld, card)) {
                                meld.add(card);
                                Collections.sort(meld);
                                attached = true;
                                break outer;
                            }
                        }
                    }
                    if (attached) {
                        changed = true;
                        break;
                    }
                }
            }
        }
        int money = b.getMoney();
        int[] scores = new int[4];
        int[] racScores = new int[4];
        int[] deltas = new int[4];
        List<Integer>[] rac = new List[4];
        boolean[] chay = new boolean[4];
        for (int s = 0; s < 4; s++) {
            rac[s] = new ArrayList<>();
            if (!ph.inGame[s]) {
                scores[s] = -1;
                continue;
            }
            List<Integer> covered = coveredCards(ph, s);
            int sc = 0;
            for (int c : ph.hands[s]) {
                if (!covered.contains(c)) {
                    rac[s].add(c);
                    sc += PhomRules.points(c);
                }
            }
            racScores[s] = sc;
            chay[s] = ph.melds[s].isEmpty();
            scores[s] = chay[s] ? -2 : sc;
        }
        int winner = -1;
        for (int s = 0; s < 4; s++) {
            if (!ph.inGame[s] || chay[s]) continue;
            if (winner < 0 || scores[s] < scores[winner]
                || (scores[s] == scores[winner]
                    && ph.haOrder[s] < ph.haOrder[winner])) {
                winner = s;
            }
        }
        if (winner >= 0) {
            int total = 0;
            for (int s = 0; s < 4; s++) {
                if (!ph.inGame[s] || s == winner) continue;
                int share = racScores[s] * money * (chay[s] ? 2 : 1);
                int pay = clampPay(b.seats[s], share);
                deltas[s] = -pay;
                total += pay;
            }
            deltas[winner] = total;
        }
        applyDeltas(b, deltas);
        sendFinishFull(b, room, deltas, scores, rac);
        endGame(b, winner);
    }

    /** FINISH cho ván thường: tiền[4], điểm[4], rác theo ghế. */
    private static void sendFinishFull(BoardInfo b, byte room, int[] deltas,
                                       int[] scores, List<Integer>[] rac) {
        try {
            Message ms = msg((byte) 51, room, b.boardID);
            DataOutputStream ds = ms.writer();
            for (int s = 0; s < 4; s++) ds.writeInt(deltas[s]);
            for (int s = 0; s < 4; s++) ds.writeInt(scores[s]);
            for (int s = 0; s < 4; s++) {
                if (s > 0) ds.writeByte(-1);
                for (int c : rac[s]) ds.writeByte(c);
            }
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
    }

    /** FINISH cho ù thường: người ù score −1, còn lại điểm rác
     *  hiện tại + bài rác tương ứng. */
    private static void sendFinish(BoardInfo b, byte room, int[] deltas, int uSeat) {
        PhomState ph = b.phom;
        int[] scores = new int[4];
        List<Integer>[] rac = new List[4];
        for (int s = 0; s < 4; s++) {
            rac[s] = new ArrayList<>();
            if (!ph.inGame[s]) {
                scores[s] = -1;
                continue;
            }
            if (s == uSeat) {
                scores[s] = -1;
                continue;
            }
            List<Integer> covered = coveredCards(ph, s);
            List<Integer> rest = new ArrayList<>(ph.hands[s]);
            rest.removeAll(covered);
            PhomRules.Partition p = PhomRules.bestPartition(rest);
            List<Integer> inMelds = new ArrayList<>();
            for (List<Integer> g : p.melds) inMelds.addAll(g);
            int sc = 0;
            for (int c : rest) {
                if (!inMelds.contains(c)) {
                    rac[s].add(c);
                    sc += PhomRules.points(c);
                }
            }
            scores[s] = sc;
        }
        sendFinishFull(b, room, deltas, scores, rac);
    }

    // ------------------------------------------------------------------
    // Bỏ lượt + hết giờ + rời bàn

    private static void skipTurn(BoardInfo b, byte room, int seat) {
        PhomState ph = b.phom;
        int next = nextActiveSeat(ph, seat);
        if (next < 0) {
            finishGame(b, room);
            return;
        }
        try {
            Message ms = msg((byte) 49, room, b.boardID);
            DataOutputStream ds = ms.writer();
            ds.writeInt(idAt(b, next));
            ds.writeInt(ph.tableFromSeat >= 0 ? idAt(b, ph.tableFromSeat) : -1);
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        ph.turnSeat = next;
        ph.phase = 0;
        scheduleTurn(b, room);
    }

    private static void scheduleTurn(BoardInfo b, byte room) {
        PhomState ph = b.phom;
        final int gen = ++ph.gen;
        TIMER.schedule(() -> {
            synchronized (b) {
                if (!ph.playing || ph.gen != gen) return;
                int seat = ph.turnSeat;
                if (seat < 0) return;
                if (ph.phase == 0) {
                    // chưa Ăn/Bốc → bỏ cả lượt (người kế vẫn ăn
                    // được lá trên bàn như gốc)
                    skipTurn(b, room, seat);
                } else {
                    autoPlay(b, room, seat);
                }
            }
        }, INTERVAL, TimeUnit.SECONDS);
    }

    /** Hết giờ ở pha đã Ăn/Bốc: tự hạ (nếu lượt cuối) rồi tự đánh
     *  lá rác nhỏ nhất không bị khoá. */
    private static void autoPlay(BoardInfo b, byte room, int seat) {
        PhomState ph = b.phom;
        if (ph.fireCount[seat] >= 3 && !ph.haDone[seat]) {
            List<Integer> covered = coveredCards(ph, seat);
            List<Integer> rest = new ArrayList<>(ph.hands[seat]);
            rest.removeAll(covered);
            PhomRules.Partition p = PhomRules.bestPartition(rest);
            if (!p.melds.isEmpty()) {
                if (applyHa(b, room, seat, p.melds)) return; // ù khi hạ
            } else {
                // không có phỏm nào để hạ: đánh dấu đã hạ rỗng để
                // không kẹt ván (điểm tính như không hạ)
                ph.haDone[seat] = true;
                ph.haOrder[seat] = ++ph.haCounter;
            }
        }
        if (!ph.playing || ph.turnSeat != seat) return;
        int best = -1;
        for (int c : ph.hands[seat]) {
            if (ph.locked[seat].contains(c)) continue;
            if (best < 0 || c < best) best = c;
        }
        if (best >= 0) doFire(b, room, seat, best);
    }

    /** Người đang chơi rời bàn giữa ván → huỷ ván (STOP 50). */
    public static void onLeave(BoardInfo b, byte room, User leaver) {
        PhomState ph = b.phom;
        if (ph == null || !ph.playing) return;
        int seat = b.seatOf(leaver);
        if (seat < 0 || !ph.inGame[seat]) return;
        ph.playing = false;
        ph.gen++;
        b.setPlaying(false);
        b.readyIds.clear();
        try {
            Message ms = msg((byte) 50, room, b.boardID);
            DataOutputStream ds = ms.writer();
            List<Integer> cards = ph.hands[seat];
            if (!cards.isEmpty()) {
                ds.writeInt(leaver.getId());
                ds.writeByte(cards.size());
                for (int c : cards) ds.writeByte(c);
            }
            ds.flush();
            broadcast(b, ms);
        } catch (IOException ignored) {
        }
        ph.resetHand();
    }
}
