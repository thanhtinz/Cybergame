package avatar.minigame;

import avatar.constants.Cmd;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.UserManager;
import avatar.service.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hub MiniGame + Tàu Chiến (game id 4) — port theo bản chính 3.4.4
 * (docs/tauchien-hub-spec.md). Code viết mới; luồng + số lệnh con giữ
 * theo bản chính: hub MINIGAME, mời INVITE (ván giao hữu, không quà,
 * khóa chống spam 4263ms [GỐC]), khán giả SPECTATE; Tàu Chiến 15 lệnh
 * con JOIN..SNAPSHOT. Phong bì dùng Cmd.MINIGAME_HUB (-113) vì -90 của
 * gốc đã là UPGRADE_CHEST trong server Pack5.
 *
 * [PORT] Các thông số bản chính không đọc được từ metadata:
 * - Bàn 10x10 mỗi người (BOARD_SIZE=20 của gốc hiểu là tổng hai bàn).
 * - Hạm đội 14 tàu: dài 5x1, 4x1, 3x2, 2x4, 1x6 (FLEET_SIZE=14 [GỐC]
 *   hiểu là số tàu; ánh xạ độ dài theo sprite Ship_lv1..5).
 * - Lượt bắn tối đa 30 giây, hết giờ tự mất lượt.
 * - Trúng được bắn tiếp, trượt mới đổi lượt (server quyết qua
 *   CHANGE_TURN như cấu trúc lệnh của gốc).
 * - Hạn trả lời lời mời 60 giây.
 */
public class MiniGameService extends Service {

    // ===== Hằng số game =====
    public static final int GAME_TAUCHIEN = 4;
    public static final int GAME_CARO = 3;
    public static final int GAME_TRUCXANH = 1;
    public static final int BOARD = 10;                 // ô mỗi chiều/bàn
    /** Số tàu theo độ dài: chỉ số = độ dài (1..5). Tổng 14 tàu. */
    public static final int[] FLEET_QTY = {0, 6, 4, 2, 1, 1};
    public static final long INVITE_LOCK_MS = 4263L;    // [GỐC]
    public static final long INVITE_TTL_MS = 60000L;    // [PORT]
    public static final int TURN_SECONDS = 30;          // [PORT]
    // Cờ Caro [PORT]: bàn 15x15, 5 ô liên tiếp là thắng (kể cả bị
    // chặn hai đầu), hoà khi kín bàn. Bản chính có configureBoardSize
    // (cỡ bàn cấu hình được) — chưa đọc được cỡ mặc định của server gốc.
    public static final int CARO_N = 15;
    // Trúc Xanh [PORT]: lưới 6x5 = 30 thẻ, 15 cặp (docs/trucxanh-caro-research.md)
    public static final int TX_COLS = 6, TX_ROWS = 5, TX_CARDS = TX_COLS * TX_ROWS;

    // ===== Hub: lệnh con (gameId 0) =====
    static final int H_LIST = 0, H_CREATE = 1, H_JOIN = 2, H_JOIN_CODE = 3,
            H_INVITE = 4, H_INVITE_REPLY = 5, H_LEAVE = 6, H_WATCH = 7,
            H_STOP_WATCH = 8, H_REMATCH = 15;
    // Hub: S→C
    static final int H_JOINED = 10, H_OPP_JOINED = 11, H_OPP_LEFT = 12,
            H_SPEC_COUNT = 14, H_REMATCH_OFFER = 16, H_INVITE_IN = 20,
            H_INVITE_RESULT = 21, H_ERROR = 23;

    // ===== Tàu Chiến: lệnh con (gameId 4), theo bản chính =====
    static final int TC_START_PLACE = 1, TC_PLACE_SHIPS = 2, TC_PLACE_RESULT = 3,
            TC_READY = 4, TC_START_BATTLE = 6, TC_SHOOT = 7, TC_SHOT_RESULT = 8,
            TC_CHANGE_TURN = 9, TC_END = 10, TC_LEAVE = 11, TC_KICK = 12,
            TC_SNAPSHOT = 13, TC_SPEC_COUNT = 14;

    // ===== Cờ Caro: lệnh con (gameId 3) [PORT theo cấu trúc bản chính] =====
    static final int CR_START = 1, CR_MOVE = 2, CR_MOVED = 3,
            CR_CHANGE_TURN = 4, CR_END = 5, CR_SNAPSHOT = 13, CR_SPEC_COUNT = 14;

    // ===== Trúc Xanh: lệnh con (gameId 1) [PORT theo cấu trúc bản chính] =====
    static final int TX_DEAL = 1, TX_FLIP = 2, TX_FLIP_RESULT = 3,
            TX_MATCH = 4, TX_MISS = 5, TX_CHANGE_TURN = 6, TX_END = 7,
            TX_SNAPSHOT = 13, TX_SPEC_COUNT = 14;

    // Trạng thái bàn
    static final int ST_WAIT = 0, ST_PLACE = 1, ST_BATTLE = 2, ST_END = 3;
    // Ô trên bàn của một ghế (góc nhìn phát bắn vào ghế đó):
    // 0 chưa bắn, 1 trượt (nước), 2 trúng (cháy), 3 ô thuộc tàu đã chìm.

    static class Ship {
        int len, row, col;
        boolean vert;
        int hits;
        boolean sunk() { return hits >= len; }
    }

    static class Room {
        int id;
        int gameId;
        boolean priv;
        boolean friendly;
        String code = "";
        final User[] seats = new User[2];
        final List<User> spectators = new ArrayList<>();
        int status = ST_WAIT;
        // Tàu Chiến
        final List<Ship>[] fleets = new List[]{new ArrayList<>(), new ArrayList<>()};
        final boolean[] placed = new boolean[2];
        final boolean[] ready = new boolean[2];
        final byte[][] shots = new byte[2][BOARD * BOARD];
        int turn = 0;
        final boolean[] rematch = new boolean[2];
        ScheduledFuture<?> turnTimer;
        // Cờ Caro: 0 trống, 1 = ghế 0 (X), 2 = ghế 1 (O)
        final byte[] caroBoard = new byte[CARO_N * CARO_N];
        int caroMoves = 0;
        // Trúc Xanh: deck[i] = giá trị cặp (0..14), txState: 0 úp, 1 đang mở, 2 đã ăn
        final int[] txDeck = new int[TX_CARDS];
        final byte[] txState = new byte[TX_CARDS];
        final int[] txScores = new int[2];
        int txUp1 = -1, txUp2 = -1;
    }

    static class Invite {
        int fromId, toId, gameId;
        long expiry;
    }

    private static final Map<Integer, Room> ROOMS = new HashMap<>();
    private static final Map<Integer, Invite> INVITES = new HashMap<>(); // theo fromId
    private static final Map<Integer, Long> INVITE_LAST = new HashMap<>();
    private static int nextRoomId = 1;
    private static final ScheduledExecutorService TIMER =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "minigame-timer");
                t.setDaemon(true);
                return t;
            });

    public MiniGameService(Session cl) {
        super(cl);
    }

    // ================= Điều phối =================

    public void onMessage(Message ms) {
        try {
            DataInputStream dis = ms.reader();
            int gameId = dis.readByte();
            int sub = dis.readByte();
            User me = this.session.user;
            if (me == null) return;
            if (gameId == 0) {
                switch (sub) {
                    case H_LIST: sendList(me); break;
                    case H_CREATE: {
                        int g = dis.readByte();
                        boolean priv = dis.readBoolean();
                        createRoom(me, g, priv);
                        break;
                    }
                    case H_JOIN: joinRoom(me, dis.readInt()); break;
                    case H_JOIN_CODE: joinByCode(me, dis.readUTF()); break;
                    case H_INVITE: {
                        int target = dis.readInt();
                        int g = dis.readByte();
                        invite(me, target, g);
                        break;
                    }
                    case H_INVITE_REPLY: {
                        int from = dis.readInt();
                        boolean ok = dis.readBoolean();
                        inviteReply(me, from, ok);
                        break;
                    }
                    case H_LEAVE: leaveRoom(me, false); break;
                    case H_WATCH: watch(me, dis.readInt()); break;
                    case H_STOP_WATCH: stopWatch(me); break;
                    case H_REMATCH: rematch(me); break;
                    default: break;
                }
            } else if (gameId == GAME_TAUCHIEN) {
                switch (sub) {
                    case TC_PLACE_SHIPS: placeShips(me, dis); break;
                    case TC_READY: setReady(me); break;
                    case TC_SHOOT: {
                        int row = dis.readByte(), col = dis.readByte();
                        shoot(me, row, col);
                        break;
                    }
                    case TC_LEAVE: leaveRoom(me, false); break;
                    case TC_KICK: kick(me); break;
                    default: break;
                }
            } else if (gameId == GAME_CARO) {
                switch (sub) {
                    case CR_MOVE: {
                        int row = dis.readByte(), col = dis.readByte();
                        caroMove(me, row, col);
                        break;
                    }
                    case TC_LEAVE: leaveRoom(me, false); break;
                    default: break;
                }
            } else if (gameId == GAME_TRUCXANH) {
                switch (sub) {
                    case TX_FLIP: txFlip(me, dis.readByte()); break;
                    case TC_LEAVE: leaveRoom(me, false); break;
                    default: break;
                }
            }
        } catch (Exception ex) {
            // gói lỗi không được làm sập phiên
        }
    }

    // ================= Tiện ích gửi gói =================

    private static Message msg(int gameId, int sub) throws IOException {
        Message m = new Message(Cmd.MINIGAME_HUB);
        DataOutputStream w = m.writer();
        w.writeByte(gameId);
        w.writeByte(sub);
        return m;
    }

    private static void send(User u, Message m) {
        if (u != null && u.session != null) u.sendMessage(m);
    }

    private void error(User u, String text) throws IOException {
        Message m = msg(0, H_ERROR);
        m.writer().writeUTF(text);
        send(u, m);
    }

    private static Room roomOf(User u) {
        for (Room r : ROOMS.values()) {
            if (r.seats[0] == u || r.seats[1] == u) return r;
        }
        return null;
    }

    private static int seatOf(Room r, User u) {
        return r.seats[0] == u ? 0 : (r.seats[1] == u ? 1 : -1);
    }

    private static boolean isSpectator(User u) {
        for (Room r : ROOMS.values()) if (r.spectators.contains(u)) return true;
        return false;
    }

    // ================= Hub =================

    private void sendList(User me) throws IOException {
        List<Room> list = new ArrayList<>();
        for (Room r : ROOMS.values()) if (!r.friendly) list.add(r);
        Message m = msg(0, H_LIST);
        DataOutputStream w = m.writer();
        w.writeShort(list.size());
        for (Room r : list) {
            w.writeInt(r.id);
            w.writeByte(r.gameId);
            w.writeUTF(r.seats[0] == null ? "" : r.seats[0].getDisplayName());
            w.writeUTF(r.seats[1] == null ? "" : r.seats[1].getDisplayName());
            w.writeByte(r.spectators.size());
            w.writeBoolean(r.priv);
            w.writeByte(r.status);
        }
        send(me, m);
    }

    private Room newRoom(User host, int gameId, boolean priv, boolean friendly) {
        Room r = new Room();
        r.id = nextRoomId++;
        r.gameId = gameId;
        r.priv = priv;
        r.friendly = friendly;
        r.seats[0] = host;
        if (priv || friendly) {
            r.code = String.format("%04d", 1000 + (r.id * 7919) % 9000);
        }
        ROOMS.put(r.id, r);
        return r;
    }

    private static boolean gamePlayable(int gameId) {
        return gameId == GAME_TAUCHIEN || gameId == GAME_CARO
            || gameId == GAME_TRUCXANH;
    }

    private void createRoom(User me, int gameId, boolean priv) throws IOException {
        if (!gamePlayable(gameId)) { error(me, "Game này sắp có."); return; }
        if (roomOf(me) != null || isSpectator(me)) {
            error(me, "Bạn đang ở trong một bàn khác."); return;
        }
        Room r = newRoom(me, gameId, priv, false);
        sendJoined(me, r, 0);
    }

    private void sendJoined(User u, Room r, int seat) throws IOException {
        Message m = msg(0, H_JOINED);
        DataOutputStream w = m.writer();
        w.writeInt(r.id);
        w.writeByte(seat);
        w.writeByte(r.gameId);
        User opp = seat < 2 ? r.seats[1 - seat] : null; // seat 2 = khán giả
        w.writeUTF(opp == null ? "" : opp.getDisplayName());
        w.writeBoolean(r.priv);
        w.writeUTF(r.code == null ? "" : r.code);
        w.writeBoolean(r.friendly);
        send(u, m);
    }

    private void joinRoom(User me, int roomId) throws IOException {
        Room r = ROOMS.get(roomId);
        if (r == null) { error(me, "Bàn không còn tồn tại."); return; }
        joinSeat(me, r);
    }

    private void joinByCode(User me, String code) throws IOException {
        for (Room r : ROOMS.values()) {
            if (!r.code.isEmpty() && r.code.equals(code.trim())) {
                joinSeat(me, r);
                return;
            }
        }
        error(me, "Không tìm thấy bàn với mã này.");
    }

    private void joinSeat(User me, Room r) throws IOException {
        if (roomOf(me) != null || isSpectator(me)) {
            error(me, "Bạn đang ở trong một bàn khác."); return;
        }
        if (r.seats[0] == me || r.seats[1] == me) return;
        if (r.seats[1] != null) { error(me, "Bàn đã đủ hai người."); return; }
        if (r.status != ST_WAIT) { error(me, "Ván đã bắt đầu."); return; }
        r.seats[1] = me;
        sendJoined(me, r, 1);
        // báo cho chủ bàn + mở pha xếp tàu cho cả hai
        Message m = msg(0, H_OPP_JOINED);
        m.writer().writeUTF(me.getDisplayName());
        send(r.seats[0], m);
        startGame(r);
    }

    /** Bắt đầu (hoặc bắt đầu lại) một ván theo game của bàn. */
    private void startGame(Room r) throws IOException {
        if (r.gameId == GAME_CARO) { startCaro(r); return; }
        if (r.gameId == GAME_TRUCXANH) { startTrucXanh(r); return; }
        startPlacing(r);
    }

    private void startPlacing(Room r) throws IOException {
        r.status = ST_PLACE;
        for (int i = 0; i < 2; i++) {
            r.fleets[i].clear();
            r.placed[i] = false;
            r.ready[i] = false;
            r.rematch[i] = false;
            java.util.Arrays.fill(r.shots[i], (byte) 0);
        }
        Message m = msg(r.gameId, TC_START_PLACE);
        send(r.seats[0], m);
        send(r.seats[1], m);
        broadcastSnapshot(r);
    }

    // ================= Mời (ván giao hữu) =================

    private void invite(User me, int targetId, int gameId) throws IOException {
        long now = System.currentTimeMillis();
        Long last = INVITE_LAST.get(me.getId());
        if (last != null && now - last < INVITE_LOCK_MS) {
            error(me, "Bạn vừa gửi lời mời, vui lòng chờ vài giây");
            return;
        }
        if (!gamePlayable(gameId)) { error(me, "Game này sắp có."); return; }
        if (roomOf(me) != null || isSpectator(me)) {
            error(me, "Bạn đang ở trong một bàn khác."); return;
        }
        User target = UserManager.getInstance().find(targetId);
        if (target == null || target.session == null) {
            error(me, "Người chơi không trực tuyến."); return;
        }
        if (roomOf(target) != null || isSpectator(target)) {
            error(me, "Người chơi đang bận."); return;
        }
        INVITE_LAST.put(me.getId(), now);
        Invite inv = new Invite();
        inv.fromId = me.getId();
        inv.toId = targetId;
        inv.gameId = gameId;
        inv.expiry = now + INVITE_TTL_MS;
        INVITES.put(me.getId(), inv);
        Message m = msg(0, H_INVITE_IN);
        DataOutputStream w = m.writer();
        w.writeInt(me.getId());
        w.writeUTF(me.getDisplayName());
        w.writeByte(gameId);
        send(target, m);
    }

    private void inviteReply(User me, int fromId, boolean ok) throws IOException {
        Invite inv = INVITES.get(fromId);
        if (inv == null || inv.toId != me.getId()) return;
        INVITES.remove(fromId);
        User from = UserManager.getInstance().find(fromId);
        if (from == null || from.session == null) return;
        if (!ok || System.currentTimeMillis() > inv.expiry) {
            Message m = msg(0, H_INVITE_RESULT);
            m.writer().writeBoolean(false);
            m.writer().writeUTF(me.getDisplayName());
            send(from, m);
            return;
        }
        if (roomOf(from) != null || roomOf(me) != null) return;
        Room r = newRoom(from, inv.gameId, true, true);
        r.seats[1] = me;
        sendJoined(from, r, 0);
        sendJoined(me, r, 1);
        startGame(r);
    }

    // ================= Rời bàn / đuổi / ngắt kết nối =================

    /** Người chơi rời bàn (ghế hoặc khán giả). byDisconnect: không gửi gói cho người rời. */
    public static void leaveRoomStatic(User u, boolean byDisconnect) {
        try {
            new MiniGameService(u.session).leaveRoom(u, byDisconnect);
        } catch (Exception ignored) {
        }
    }

    private void leaveRoom(User me, boolean byDisconnect) throws IOException {
        // khán giả rời
        for (Room r : new ArrayList<>(ROOMS.values())) {
            if (r.spectators.remove(me)) {
                broadcastSpecCount(r);
                return;
            }
        }
        Room r = roomOf(me);
        if (r == null) return;
        int seat = seatOf(r, me);
        cancelTimer(r);
        if (r.status == ST_BATTLE) {
            // xử thua: đối thủ thắng (đúng chuỗi gốc "Đối thủ thoát. Bạn thắng")
            int winner = 1 - seat;
            r.status = ST_END;
            sendEndGame(r, winner, 1);
            r.seats[seat] = null;
            broadcastSnapshot(r);
            dissolveIfEmpty(r);
            return;
        }
        User opp = r.seats[1 - seat];
        r.seats[seat] = null;
        if (opp == null) {
            dissolve(r);
            return;
        }
        if (seat == 0) {
            // chủ bàn rời: bàn giải tán (giống bàn riêng của gốc)
            Message m = msg(0, H_OPP_LEFT);
            send(opp, m);
            for (User s : new ArrayList<>(r.spectators)) {
                Message e = msg(0, H_OPP_LEFT);
                send(s, e);
            }
            dissolve(r);
        } else {
            // ghế 2 rời trước khi đấu: bàn quay lại chờ
            r.status = ST_WAIT;
            r.placed[0] = r.placed[1] = false;
            r.ready[0] = r.ready[1] = false;
            Message m = msg(0, H_OPP_LEFT);
            send(opp, m);
            broadcastSnapshot(r);
        }
    }

    private void kick(User me) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.seats[0] != me || r.seats[1] == null) return;
        if (r.status != ST_PLACE && r.status != ST_WAIT) return;
        User opp = r.seats[1];
        r.seats[1] = null;
        r.status = ST_WAIT;
        r.placed[0] = r.placed[1] = false;
        r.ready[0] = r.ready[1] = false;
        Message m = msg(r.gameId, TC_KICK);
        send(opp, m);
        Message m2 = msg(0, H_OPP_LEFT);
        send(me, m2);
        broadcastSnapshot(r);
    }

    private void dissolve(Room r) {
        cancelTimer(r);
        r.spectators.clear();
        ROOMS.remove(r.id);
    }

    private void dissolveIfEmpty(Room r) {
        if (r.seats[0] == null && r.seats[1] == null) dissolve(r);
    }

    // ================= Khán giả =================

    private void watch(User me, int roomId) throws IOException {
        Room r = ROOMS.get(roomId);
        if (r == null) { error(me, "Bàn không còn tồn tại."); return; }
        if (roomOf(me) != null) { error(me, "Bạn đang ở trong một bàn khác."); return; }
        if (r.priv || r.friendly) { error(me, "Bàn riêng không cho khán giả."); return; }
        if (!r.spectators.contains(me)) r.spectators.add(me);
        sendJoined(me, r, 2); // seat 2 = khán giả
        sendSnapshot(me, r);
        broadcastSpecCount(r);
    }

    private void stopWatch(User me) throws IOException {
        for (Room r : new ArrayList<>(ROOMS.values())) {
            if (r.spectators.remove(me)) broadcastSpecCount(r);
        }
    }

    private void broadcastSpecCount(Room r) throws IOException {
        Message m = msg(r.gameId, TC_SPEC_COUNT);
        m.writer().writeByte(r.spectators.size());
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : r.spectators) send(s, m);
    }

    private void sendSnapshot(User u, Room r) throws IOException {
        if (r.gameId == GAME_CARO) { sendSnapshotCaro(u, r); return; }
        if (r.gameId == GAME_TRUCXANH) { sendSnapshotTx(u, r); return; }
        Message m = msg(r.gameId, TC_SNAPSHOT);
        DataOutputStream w = m.writer();
        w.writeInt(r.id);
        w.writeByte(r.status);
        w.writeUTF(r.seats[0] == null ? "" : r.seats[0].getDisplayName());
        w.writeUTF(r.seats[1] == null ? "" : r.seats[1].getDisplayName());
        w.writeByte(r.turn);
        w.write(r.shots[0]);
        w.write(r.shots[1]);
        writeSunkShips(w, r, 0);
        writeSunkShips(w, r, 1);
        w.writeByte(r.spectators.size());
        send(u, m);
    }

    private void broadcastSnapshot(Room r) throws IOException {
        for (User s : new ArrayList<>(r.spectators)) sendSnapshot(s, r);
    }

    private void writeSunkShips(DataOutputStream w, Room r, int seat) throws IOException {
        List<Ship> sunk = new ArrayList<>();
        for (Ship s : r.fleets[seat]) if (s.sunk()) sunk.add(s);
        w.writeByte(sunk.size());
        for (Ship s : sunk) {
            w.writeByte(s.len);
            w.writeByte(s.row);
            w.writeByte(s.col);
            w.writeByte(s.vert ? 1 : 0);
        }
    }

    // ================= Tàu Chiến: xếp tàu =================

    static String validateFleet(List<Ship> fleet) {
        int[] cnt = new int[6];
        for (Ship s : fleet) {
            if (s.len < 1 || s.len > 5) return "Hướng đặt tàu không hợp lệ";
            cnt[s.len]++;
        }
        for (int len = 1; len <= 5; len++) {
            if (cnt[len] != FLEET_QTY[len]) return "Hướng đặt tàu không hợp lệ";
        }
        for (Ship s : fleet) {
            for (int i = 0; i < s.len; i++) {
                int rr = s.row + (s.vert ? i : 0);
                int cc = s.col + (s.vert ? 0 : i);
                if (rr < 0 || rr >= BOARD || cc < 0 || cc >= BOARD) {
                    return "Có tàu nằm ngoài bàn cờ";
                }
            }
        }
        for (int i = 0; i < fleet.size(); i++) {
            for (int j = i + 1; j < fleet.size(); j++) {
                Ship a = fleet.get(i), b = fleet.get(j);
                for (int x = 0; x < a.len; x++) {
                    int ar = a.row + (a.vert ? x : 0), ac = a.col + (a.vert ? 0 : x);
                    for (int y = 0; y < b.len; y++) {
                        int br = b.row + (b.vert ? y : 0), bc = b.col + (b.vert ? 0 : y);
                        int d = Math.max(Math.abs(ar - br), Math.abs(ac - bc));
                        if (d == 0) return "Có hai tàu đè lên nhau";
                        if (d < 2) return "Hai tàu phải cách nhau ít nhất 1 ô nước";
                    }
                }
            }
        }
        return null;
    }

    private void placeShips(User me, DataInputStream dis) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.status != ST_PLACE) return;
        int seat = seatOf(r, me);
        int n = dis.readByte();
        List<Ship> fleet = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Ship s = new Ship();
            s.len = dis.readByte();
            s.row = dis.readByte();
            s.col = dis.readByte();
            s.vert = dis.readByte() != 0;
            fleet.add(s);
        }
        String err = validateFleet(fleet);
        Message m = msg(r.gameId, TC_PLACE_RESULT);
        m.writer().writeBoolean(err == null);
        m.writer().writeUTF(err == null ? "" : err);
        send(me, m);
        if (err == null) {
            r.fleets[seat] = fleet;
            r.placed[seat] = true;
            maybeStartBattle(r);
        }
    }

    private void setReady(User me) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.status != ST_PLACE) return;
        int seat = seatOf(r, me);
        if (!r.placed[seat]) return; // phải xếp xong mới sẵn sàng
        r.ready[seat] = true;
        Message m = msg(r.gameId, TC_READY);
        m.writer().writeByte(seat);
        send(r.seats[0], m);
        send(r.seats[1], m);
        maybeStartBattle(r);
    }

    private void maybeStartBattle(Room r) throws IOException {
        if (r.status != ST_PLACE) return;
        if (!(r.placed[0] && r.placed[1] && r.ready[0] && r.ready[1])) return;
        r.status = ST_BATTLE;
        r.turn = 0;
        Message m = msg(r.gameId, TC_START_BATTLE);
        m.writer().writeByte(r.turn);
        send(r.seats[0], m);
        send(r.seats[1], m);
        broadcastSnapshot(r);
        armTurnTimer(r);
    }

    // ================= Tàu Chiến: bắn =================

    private void armTurnTimer(Room r) {
        cancelTimer(r);
        final int roomId = r.id;
        final int turnSeat = r.turn;
        r.turnTimer = TIMER.schedule(() -> {
            try {
                Room room = ROOMS.get(roomId);
                if (room == null || room.status != ST_BATTLE || room.turn != turnSeat) return;
                onTurnTimeout(room);
            } catch (Exception ignored) {
            }
        }, TURN_SECONDS, TimeUnit.SECONDS);
    }

    /** Hết giờ một lượt: mất lượt (Trúc Xanh úp lại thẻ đang mở trước). */
    private void onTurnTimeout(Room room) throws IOException {
        if (room.gameId == GAME_TRUCXANH && room.txUp1 >= 0) {
            Message miss = msg(room.gameId, TX_MISS);
            miss.writer().writeByte(room.txUp1);
            miss.writer().writeByte(room.txUp2);
            room.txState[room.txUp1] = 0;
            if (room.txUp2 >= 0) room.txState[room.txUp2] = 0;
            room.txUp1 = room.txUp2 = -1;
            send(room.seats[0], miss);
            send(room.seats[1], miss);
            for (User s : new ArrayList<>(room.spectators)) send(s, miss);
        }
        room.turn = 1 - room.turn;
        int sub = room.gameId == GAME_CARO ? CR_CHANGE_TURN
            : room.gameId == GAME_TRUCXANH ? TX_CHANGE_TURN : TC_CHANGE_TURN;
        Message m = msg(room.gameId, sub);
        m.writer().writeByte(room.turn);
        send(room.seats[0], m);
        send(room.seats[1], m);
        broadcastSnapshot(room);
        armTurnTimer(room);
    }

    private void cancelTimer(Room r) {
        if (r.turnTimer != null) {
            r.turnTimer.cancel(false);
            r.turnTimer = null;
        }
    }

    private void shoot(User me, int row, int col) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.status != ST_BATTLE) return;
        int seat = seatOf(r, me);
        if (seat != r.turn) return;
        if (row < 0 || row >= BOARD || col < 0 || col >= BOARD) return;
        int def = 1 - seat;
        int cell = row * BOARD + col;
        if (r.shots[def][cell] != 0) return; // đã bắn ô này
        cancelTimer(r);
        Ship hit = null;
        for (Ship s : r.fleets[def]) {
            for (int i = 0; i < s.len; i++) {
                int rr = s.row + (s.vert ? i : 0);
                int cc = s.col + (s.vert ? 0 : i);
                if (rr == row && cc == col) { hit = s; break; }
            }
            if (hit != null) break;
        }
        int result; // 0 trượt, 1 trúng, 2 chìm
        int sunkLen = 0;
        if (hit == null) {
            r.shots[def][cell] = 1;
            result = 0;
        } else {
            hit.hits++;
            if (hit.sunk()) {
                for (int i = 0; i < hit.len; i++) {
                    int rr = hit.row + (hit.vert ? i : 0);
                    int cc = hit.col + (hit.vert ? 0 : i);
                    r.shots[def][rr * BOARD + cc] = 3;
                }
                result = 2;
                sunkLen = hit.len;
            } else {
                r.shots[def][cell] = 2;
                result = 1;
            }
        }
        Message m = msg(r.gameId, TC_SHOT_RESULT);
        DataOutputStream w = m.writer();
        w.writeByte(seat);
        w.writeByte(row);
        w.writeByte(col);
        w.writeByte(result);
        w.writeByte(sunkLen);
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : new ArrayList<>(r.spectators)) send(s, m);
        // thắng?
        boolean allSunk = true;
        for (Ship s : r.fleets[def]) if (!s.sunk()) { allSunk = false; break; }
        if (result == 2 && allSunk) {
            r.status = ST_END;
            sendEnd(r, seat, 0);
            broadcastSnapshot(r);
            return;
        }
        if (result == 0) {
            r.turn = def;
            Message t = msg(r.gameId, TC_CHANGE_TURN);
            t.writer().writeByte(r.turn);
            send(r.seats[0], t);
            send(r.seats[1], t);
        }
        broadcastSnapshot(r);
        if (r.status == ST_BATTLE) armTurnTimer(r);
    }

    private void sendEnd(Room r, int winnerSeat, int reason) throws IOException {
        Message m = msg(r.gameId, TC_END);
        DataOutputStream w = m.writer();
        w.writeByte(winnerSeat);
        w.writeByte(reason); // 0 = chìm hết hạm đội, 1 = đối thủ thoát
        writeFleet(w, r.fleets[0]);
        writeFleet(w, r.fleets[1]);
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : new ArrayList<>(r.spectators)) send(s, m);
    }

    private void writeFleet(DataOutputStream w, List<Ship> fleet) throws IOException {
        w.writeByte(fleet.size());
        for (Ship s : fleet) {
            w.writeByte(s.len);
            w.writeByte(s.row);
            w.writeByte(s.col);
            w.writeByte(s.vert ? 1 : 0);
        }
    }

    // ================= Kết thúc theo game =================

    private void sendEndGame(Room r, int winner, int reason) throws IOException {
        if (r.gameId == GAME_CARO) { sendCaroEnd(r, winner, reason, null); return; }
        if (r.gameId == GAME_TRUCXANH) { sendTxEnd(r, winner); return; }
        sendEnd(r, winner, reason);
    }

    // ================= Cờ Caro =================

    private void startCaro(Room r) throws IOException {
        r.status = ST_BATTLE;
        r.turn = 0;
        r.caroMoves = 0;
        java.util.Arrays.fill(r.caroBoard, (byte) 0);
        r.rematch[0] = r.rematch[1] = false;
        Message m = msg(r.gameId, CR_START);
        m.writer().writeByte(0);
        send(r.seats[0], m);
        send(r.seats[1], m);
        broadcastSnapshot(r);
        armTurnTimer(r);
    }

    private void caroMove(User me, int row, int col) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.gameId != GAME_CARO || r.status != ST_BATTLE) return;
        int seat = seatOf(r, me);
        if (seat != r.turn) return;
        if (row < 0 || row >= CARO_N || col < 0 || col >= CARO_N) return;
        int idx = row * CARO_N + col;
        if (r.caroBoard[idx] != 0) return;
        cancelTimer(r);
        r.caroBoard[idx] = (byte) (seat + 1);
        r.caroMoves++;
        Message m = msg(r.gameId, CR_MOVED);
        DataOutputStream w = m.writer();
        w.writeByte(seat);
        w.writeByte(row);
        w.writeByte(col);
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : new ArrayList<>(r.spectators)) send(s, m);
        List<int[]> line = caroWinLine(r, row, col, seat + 1);
        if (line != null) {
            r.status = ST_END;
            sendCaroEnd(r, seat, 0, line);
            broadcastSnapshot(r);
            return;
        }
        if (r.caroMoves >= CARO_N * CARO_N) {
            r.status = ST_END;
            sendCaroEnd(r, 2, 2, null);
            broadcastSnapshot(r);
            return;
        }
        r.turn = 1 - seat;
        Message t = msg(r.gameId, CR_CHANGE_TURN);
        t.writer().writeByte(r.turn);
        send(r.seats[0], t);
        send(r.seats[1], t);
        broadcastSnapshot(r);
        armTurnTimer(r);
    }

    /** Hàng thắng qua ô vừa đánh (mọi hướng), null nếu chưa đủ 5. */
    private List<int[]> caroWinLine(Room r, int row, int col, int mark) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            List<int[]> line = new ArrayList<>();
            line.add(new int[]{row, col});
            for (int s = -1; s <= 1; s += 2) {
                int rr = row + d[0] * s, cc = col + d[1] * s;
                while (rr >= 0 && rr < CARO_N && cc >= 0 && cc < CARO_N
                        && r.caroBoard[rr * CARO_N + cc] == mark) {
                    line.add(new int[]{rr, cc});
                    rr += d[0] * s;
                    cc += d[1] * s;
                }
            }
            if (line.size() >= 5) return line;
        }
        return null;
    }

    private void sendCaroEnd(Room r, int winner, int reason, List<int[]> line)
            throws IOException {
        Message m = msg(r.gameId, CR_END);
        DataOutputStream w = m.writer();
        w.writeByte(winner); // 0/1 ghế thắng, 2 = hoà
        w.writeByte(reason); // 0 = đủ 5 ô, 1 = đối thủ thoát, 2 = kín bàn
        w.writeByte(line == null ? 0 : line.size());
        if (line != null) {
            for (int[] c : line) {
                w.writeByte(c[0]);
                w.writeByte(c[1]);
            }
        }
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : new ArrayList<>(r.spectators)) send(s, m);
    }

    private void sendSnapshotCaro(User u, Room r) throws IOException {
        Message m = msg(r.gameId, CR_SNAPSHOT);
        DataOutputStream w = m.writer();
        w.writeInt(r.id);
        w.writeByte(r.status);
        w.writeUTF(r.seats[0] == null ? "" : r.seats[0].getDisplayName());
        w.writeUTF(r.seats[1] == null ? "" : r.seats[1].getDisplayName());
        w.writeByte(r.turn);
        w.write(r.caroBoard);
        w.writeByte(r.spectators.size());
        send(u, m);
    }

    // ================= Trúc Xanh =================

    private void startTrucXanh(Room r) throws IOException {
        r.status = ST_BATTLE;
        r.turn = 0;
        r.txScores[0] = r.txScores[1] = 0;
        r.txUp1 = r.txUp2 = -1;
        r.rematch[0] = r.rematch[1] = false;
        List<Integer> vals = new ArrayList<>();
        for (int v = 0; v < TX_CARDS / 2; v++) {
            vals.add(v);
            vals.add(v);
        }
        java.util.Collections.shuffle(vals);
        for (int i = 0; i < TX_CARDS; i++) {
            r.txDeck[i] = vals.get(i);
            r.txState[i] = 0;
        }
        Message m = msg(r.gameId, TX_DEAL);
        DataOutputStream w = m.writer();
        w.writeByte(TX_COLS);
        w.writeByte(TX_ROWS);
        w.writeByte(0);
        send(r.seats[0], m);
        send(r.seats[1], m);
        broadcastSnapshot(r);
        armTurnTimer(r);
    }

    private void txFlip(User me, int idx) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.gameId != GAME_TRUCXANH || r.status != ST_BATTLE) return;
        int seat = seatOf(r, me);
        if (seat != r.turn) return;
        if (idx < 0 || idx >= TX_CARDS) return;
        if (r.txState[idx] != 0) return;
        cancelTimer(r);
        r.txState[idx] = 1;
        Message m = msg(r.gameId, TX_FLIP_RESULT);
        DataOutputStream w = m.writer();
        w.writeByte(seat);
        w.writeByte(idx);
        w.writeByte(r.txDeck[idx]);
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : new ArrayList<>(r.spectators)) send(s, m);
        if (r.txUp1 < 0) {
            r.txUp1 = idx;
            armTurnTimer(r);
            return;
        }
        int first = r.txUp1;
        r.txUp1 = -1;
        if (r.txDeck[first] == r.txDeck[idx]) {
            r.txState[first] = 2;
            r.txState[idx] = 2;
            r.txScores[seat]++;
            Message mm = msg(r.gameId, TX_MATCH);
            DataOutputStream mw = mm.writer();
            mw.writeByte(seat);
            mw.writeByte(first);
            mw.writeByte(idx);
            mw.writeByte(r.txScores[0]);
            mw.writeByte(r.txScores[1]);
            send(r.seats[0], mm);
            send(r.seats[1], mm);
            for (User s : new ArrayList<>(r.spectators)) send(s, mm);
            boolean allMatched = true;
            for (byte st : r.txState) if (st != 2) { allMatched = false; break; }
            if (allMatched) {
                r.status = ST_END;
                int winner = r.txScores[0] > r.txScores[1] ? 0
                    : r.txScores[1] > r.txScores[0] ? 1 : 2;
                sendTxEnd(r, winner);
            }
            broadcastSnapshot(r);
            if (r.status == ST_BATTLE) armTurnTimer(r);
        } else {
            r.txState[first] = 0;
            r.txState[idx] = 0;
            Message mm = msg(r.gameId, TX_MISS);
            mm.writer().writeByte(first);
            mm.writer().writeByte(idx);
            send(r.seats[0], mm);
            send(r.seats[1], mm);
            for (User s : new ArrayList<>(r.spectators)) send(s, mm);
            r.turn = 1 - seat;
            Message t = msg(r.gameId, TX_CHANGE_TURN);
            t.writer().writeByte(r.turn);
            send(r.seats[0], t);
            send(r.seats[1], t);
            broadcastSnapshot(r);
            armTurnTimer(r);
        }
    }

    private void sendTxEnd(Room r, int winner) throws IOException {
        Message m = msg(r.gameId, TX_END);
        DataOutputStream w = m.writer();
        w.writeByte(winner); // 0/1 ghế thắng, 2 = hoà điểm
        w.writeByte(r.txScores[0]);
        w.writeByte(r.txScores[1]);
        send(r.seats[0], m);
        send(r.seats[1], m);
        for (User s : new ArrayList<>(r.spectators)) send(s, m);
    }

    private void sendSnapshotTx(User u, Room r) throws IOException {
        Message m = msg(r.gameId, TX_SNAPSHOT);
        DataOutputStream w = m.writer();
        w.writeInt(r.id);
        w.writeByte(r.status);
        w.writeUTF(r.seats[0] == null ? "" : r.seats[0].getDisplayName());
        w.writeUTF(r.seats[1] == null ? "" : r.seats[1].getDisplayName());
        w.writeByte(r.turn);
        w.writeByte(r.txScores[0]);
        w.writeByte(r.txScores[1]);
        w.write(r.txState);
        for (int i = 0; i < TX_CARDS; i++) {
            w.writeByte(r.txState[i] > 0 ? r.txDeck[i] : -1);
        }
        w.writeByte(r.spectators.size());
        send(u, m);
    }

    // ================= Ván mới (rematch) =================

    private void rematch(User me) throws IOException {
        Room r = roomOf(me);
        if (r == null || r.status != ST_END) return;
        int seat = seatOf(r, me);
        if (r.seats[1 - seat] == null) return;
        r.rematch[seat] = true;
        if (r.rematch[0] && r.rematch[1]) {
            startGame(r);
        } else {
            Message m = msg(0, H_REMATCH_OFFER);
            m.writer().writeByte(seat);
            send(r.seats[1 - seat], m);
        }
    }
}
