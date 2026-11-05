package avatar.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import avatar.constants.Cmd;
import avatar.item.Item;
import avatar.message.minigame.BauCuaMsgHandler;
import avatar.minigame.TienLenEngine;
import avatar.model.BoardInfo;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.BoardManager;
import avatar.server.Utils;

/**
 * Handler Casino (GET_HANDLER 3) — bản port viết lại theo client gốc
 * Pack5 (xem docs/baucua-spec.md). Bầu Cua = gameType 22.
 *
 * Các sửa/làm mới so với server fan:
 * - Xúc xắc đủ 6 mặt (fan nextInt(5) không bao giờ ra Tôm).
 * - Trả thưởng THẬT: delta từng ghế từ tiền đặt × mức bàn so với 3 mặt
 *   xúc xắc (ra k mặt trùng: +k lần cược; không trúng: mất cược), nhà
 *   cái đối ứng zero-sum, cộng/trừ xu (User.xu) thật.
 * - HA_PHOM (65) đọc đúng 6 byte {room, board, room, board, from, to}
 *   và dời TOÀN BỘ tiền một ô sang ô khác.
 * - SKIP (49) chấp nhận gói rỗng như client gốc gửi.
 * - BoardList đóng gói byte packed nPlayer|maxPlayer + byte flags.
 * - JOIN_BOARD ghi tiền TỪNG người; SOMEONE_JOINBOARD đúng định dạng
 *   gốc (không thừa int, có short idImg); CHAT ghi fromID kiểu int.
 * - Ghế cố định theo BoardInfo.seats; ảnh chụp ghế lúc bắt đầu ván để
 *   người rời giữa ván không làm lệch số ghế của người còn lại.
 * - Hẹn giờ từng pha: hết giờ mà chưa chốt đặt / chưa tả thì server tự
 *   tiến hành (client gốc cũng tự chốt/tự bỏ lượt khi hết giờ).
 * - Thêm KICK (11) và SET_PASSWORD (18) mà fan chưa xử lý.
 */
public class CasinoMsgHandler extends MessageHandler {
    private final BauCuaMsgHandler service;
    /** Game casino phiên này đang mở: 22 Bầu Cua · 3 Tiến Lên. */
    private int casinoGame = 22;

    /** Giây cho pha đặt cược và cho từng lượt tả (số của server fan). */
    private static final byte PHASE_TIME = 10;
    /** Nghỉ giữa GAME_RESULT và WIN/FINISH (fan dùng 2,5 giây). */
    private static final int RESULT_DELAY_MS = 2500;

    private static final ScheduledExecutorService TIMER =
        Executors.newScheduledThreadPool(2);

    public CasinoMsgHandler(Session client) {
        super(client);
        this.service = new BauCuaMsgHandler(client);
    }

    @Override
    public void onMessage(Message mss) {
        try {
            switch (mss.getCommand()) {
                case 61:
                    joinCasino(mss);
                    break;
                case 53: // FORCE_FINISH — chỉ Tiến Lên
                    forceFinishTl();
                    break;
                case Cmd.REQUEST_ROOMLIST:
                    requestRoomList();
                    break;
                case Cmd.GET_IMG_ICON:
                    if (this.client.user != null) this.client.doGetImgIcon(mss);
                    break;
                case Cmd.REQUEST_BOARDLIST:
                    boardList(mss);
                    break;
                case Cmd.JOIN_BOARD:
                    joinBoard(mss, this.client.user);
                    break;
                case Cmd.CHAT_TO_BOARD:
                    chatToBoard(mss);
                    break;
                case Cmd.KICK:
                    kick(mss, this.client.user);
                    break;
                case Cmd.LEAVE_BOARD:
                    leaveBoard(mss, this.client.user);
                    break;
                case Cmd.READY:
                    ready(mss, this.client.user);
                    break;
                case 18: // SET_PASSWORD (Cmd.java không có hằng)
                    setPassword(mss, this.client.user);
                    break;
                case Cmd.SET_MONEY:
                    setMoney(mss, this.client.user);
                    break;
                case Cmd.START:
                    start(mss, this.client.user);
                    break;
                case Cmd.TO_XONG:
                    toXong(mss, this.client.user);
                    break;
                case Cmd.HA_PHOM: // 65: Bầu Cua = tả · Phỏm = hạ phỏm
                    haPhomDispatch(mss, this.client.user);
                    break;
                case Cmd.GET_CARD: // 63 — Phỏm: bốc bài
                    getCardPhom(mss, this.client.user);
                    break;
                case Cmd.EAT_CARD: // 64 — Phỏm: ăn bài
                    eatCardPhom(mss, this.client.user);
                    break;
                case Cmd.ADD_CARD: // 67 — Phỏm: gửi bài
                    addCardPhom(mss, this.client.user);
                    break;
                case Cmd.RESET_PHOM_EAT: // 68 — [PORT] không dùng (ăn kiểm nguyên tử)
                    break;
                case 49: // SKIP — client Bầu Cua gửi gói rỗng
                    skip(mss, this.client.user);
                    break;
                case 56: // SET_MAX_PLAYER: bàn Bầu Cua cố định 5 ghế — bỏ qua
                    break;
                default:
                    System.out.println("casino mess chua xu ly: " + mss.getCommand());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Chọn game casino (gói 61 {byte gameType}): nhận 22 Bầu Cua
     *  và 3 Tiến Lên; game khác báo chưa mở như trước. */
    private void joinCasino(Message ms) throws IOException {
        int gameType = ms.reader().available() > 0 ? ms.reader().readByte() : 22;
        if (gameType != 22 && gameType != 3 && gameType != 7 && gameType != 21) {
            this.client.user.getAvatarService().serverDialog("Game casino này chưa mở");
            return;
        }
        casinoGame = gameType;
        Message out = new Message(61);
        out.writer().writeByte(gameType);
        out.writer().flush();
        this.client.user.sendMessage(out);
    }

    private void forceFinishTl() {
        if (this.client.user == null) return;
        BoardInfo board = BoardManager.getInstance().findUserBoard(this.client.user);
        if (board == null || board.gameType != 3) return;
        TienLenEngine.forceFinish(this.client, board,
            (byte) this.client.user.getRoomID());
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private static Message boardMsg(byte cmd, byte roomID, byte boardID) throws IOException {
        Message ms = new Message(cmd);
        DataOutputStream ds = ms.writer();
        ds.writeByte(roomID);
        ds.writeByte(boardID);
        return ms;
    }

    /** Gửi cho mọi người đang ở bàn (theo ghế). */
    private static void broadcast(BoardInfo board, Message ms) {
        for (User u : new ArrayList<>(board.getLstUsers())) {
            if (u != null && u.session != null) u.session.sendMessage(ms);
        }
    }

    private static int xuInt(User u) {
        long x = u.getXu();
        if (x > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (x < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) x;
    }

    private static void writeSeat(DataOutputStream ds, User u, BoardInfo board) throws IOException {
        if (u == null) {
            ds.writeInt(-1); // ghế trống: chỉ IDDB = -1 (client gốc đọc đúng vậy)
            return;
        }
        ds.writeInt(u.getId());
        ds.writeUTF(u.getDisplayName());
        ds.writeInt(xuInt(u));
        List<Item> wearing = u.getWearing();
        ds.writeByte(wearing == null ? 0 : wearing.size());
        if (wearing != null) {
            for (Item item : wearing) ds.writeShort(item.getId());
        }
        ds.writeInt(10); // exp (server fan ghi cứng 10)
        ds.writeBoolean(board.readyIds.contains(u.getId()));
        ds.writeShort(u.getIdImg());
    }

    // ------------------------------------------------------------------
    // Phòng / bàn
    // ------------------------------------------------------------------

    private void requestRoomList() throws IOException {
        Message ms = new Message(Cmd.REQUEST_ROOMLIST);
        DataOutputStream ds = ms.writer();
        int roomBase = casinoGame == 3 ? 46 : casinoGame == 7 ? 49
            : casinoGame == 21 ? 52 : 43; // TL 46–48 · Phỏm 49–51 · Diamond 52–54 [PORT]
        for (int i = 0; i < 3; i++) {
            ds.writeByte(roomBase + i); // id phòng (số của server fan)
            ds.writeByte(1);      // roomFree
            ds.writeByte(i);      // roomWait
            ds.writeByte(i);      // lv
        }
        ds.flush();
        this.client.user.sendMessage(ms);
    }

    private void boardList(Message ms) throws IOException {
        byte roomID = ms.reader().readByte();
        ms = new Message(Cmd.REQUEST_BOARDLIST);
        DataOutputStream ds = ms.writer();
        ds.writeByte(roomID);
        for (BoardInfo a : BoardManager.getInstance().boardList) {
            if (a.gameType != casinoGame) continue;
            ds.writeByte(a.boardID);
            // byte đóng gói của gốc: nPlayer = v % 16, maxPlayer = v / 16
            ds.writeByte(a.presentCount() + (a.seatCap << 4));
            boolean playing = a.gameType == 3 ? a.tl.playing
                : a.gameType == 7 ? a.phom.playing
                : a.gameType == 21 ? a.diamond.playing : a.phase != 0;
            int flags = (a.isPass ? 1 : 0) | (playing ? 2 : 0);
            ds.writeByte(flags);
            ds.writeInt(a.getMoney());
        }
        ds.flush();
        this.client.user.sendMessage(ms);
    }

    // ------------------------------------------------------------------
    // Vào / rời bàn
    // ------------------------------------------------------------------

    private void joinBoard(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        String pass = rd.readUTF();
        BoardInfo board;
        if (boardID == -1) {
            // [PORT] Tạo bàn mới: bản gốc không có gói tạo bàn riêng (vào
            // bàn trống là thành chủ bàn); bản port cho client gửi
            // boardID −1, server cấp một bàn trắng mới rồi vào ngay.
            board = BoardManager.getInstance().createBoard(casinoGame);
            if (board == null) {
                us.getAvatarService().serverDialog("Đã đạt số bàn tối đa — hãy vào một bàn có sẵn");
                return;
            }
            boardID = board.boardID;
        } else {
            board = BoardManager.getInstance().find(boardID);
            if (board == null) return;
        }

        // Đang ở bàn khác thì rời bàn đó trước.
        BoardInfo other = BoardManager.getInstance().findUserBoard(us);
        if (other != null && other != board) {
            removeFromBoard(other, us, (byte) us.getRoomID(), true);
        }

        synchronized (board) {
            if (board.seatOf(us) < 0) {
                if (board.isPass && !board.password.equals(pass)) {
                    us.getAvatarService().serverDialog("Mật khẩu bàn không đúng");
                    return;
                }
                if (board.presentCount() >= board.seatCap) {
                    us.getAvatarService().serverDialog("Bàn đã đầy");
                    return;
                }
                int seat = board.addUser(us);
                if (seat < 0) return;
                us.setRoomID(roomID);

                // Báo cho những người đã ở bàn: có người mới vào.
                Message join = new Message(Cmd.SOMEONE_JOINBOARD);
                DataOutputStream dj = join.writer();
                dj.writeByte(seat);
                dj.writeInt(us.getId());
                dj.writeUTF(us.getDisplayName());
                dj.writeInt(xuInt(us));
                List<Item> wearing = us.getWearing();
                dj.writeByte(wearing == null ? 0 : wearing.size());
                if (wearing != null) {
                    for (Item item : wearing) dj.writeShort(item.getId());
                }
                dj.writeInt(10); // exp
                dj.writeShort(us.getIdImg());
                dj.flush();
                for (User u : new ArrayList<>(board.getLstUsers())) {
                    if (u != null && u.getId() != us.getId() && u.session != null) {
                        u.session.sendMessage(join);
                    }
                }
            }

            // Gói vào bàn cho chính người vào: đủ ghế theo thứ tự ghế.
            Message msj = new Message(Cmd.JOIN_BOARD);
            DataOutputStream ds = msj.writer();
            ds.writeByte(roomID);
            ds.writeByte(boardID);
            User owner = board.owner();
            ds.writeInt(owner == null ? -1 : owner.getId());
            ds.writeInt(board.getMoney());
            for (int s = 0; s < board.seatCap; s++) writeSeat(ds, board.seats[s], board);
            ds.flush();
            us.sendMessage(msj);

            // Vào giữa ván Diamond: gửi bàn cờ + chỉ số để xem/chơi tiếp.
            if (board.gameType == 21 && board.diamond.playing) {
                avatar.minigame.DiamondEngine.sendData(us, board, roomID);
            }

            // Vào giữa ván Bầu Cua: gửi trạng thái ván để xem.
            if (board.gameType == 22 && board.phase != 0) {
                us.setToXong(true);
                us.setHaPhom(true);
                Message ms2 = new Message(Cmd.PLAYING);
                DataOutputStream ds2 = ms2.writer();
                ds2.writeByte(roomID);
                ds2.writeByte(boardID);
                long remain = (board.phaseDeadline - System.currentTimeMillis()) / 1000;
                ds2.writeByte((int) Math.max(0, Math.min((long) PHASE_TIME, remain)));
                for (int s = 0; s < 5; s++) {
                    for (int d = 0; d < 6; d++) ds2.writeByte(betAt(board, s, d));
                }
                ds2.flush();
                us.sendMessage(ms2);
            }
        }
    }

    /** Tiền đặt hiện tại của một ghế (ảnh chụp sau khi chốt, danh sách
     *  đặt trực tiếp trong pha đặt). Ghế trống/không có = 0. */
    private static int betAt(BoardInfo board, int seat, int door) {
        if (board.phase >= 2 && board.seatSnap != null) {
            return board.seatBets[seat][door];
        }
        User u = board.seats[seat];
        if (u == null) return 0;
        List<Byte> put = u.getMoneyPutList();
        return (put != null && door < put.size()) ? put.get(door) : 0;
    }

    private void leaveBoard(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        byte roomID = rd.available() > 0 ? rd.readByte() : (byte) us.getRoomID();
        byte boardID = rd.available() > 0 ? rd.readByte() : -1;
        BoardInfo board = boardID >= 0 ? BoardManager.getInstance().find(boardID) : null;
        if (board == null || board.seatOf(us) < 0) {
            board = BoardManager.getInstance().findUserBoard(us);
        }
        if (board == null) return;
        removeFromBoard(board, us, roomID, true);
    }

    /**
     * Gỡ một người khỏi bàn + báo SOMEONE_LEAVEBOARD + tiến hành ván
     * nếu cần. Trả true nếu ván bị huỷ vì chủ bàn (ghế 0) rời giữa ván.
     */
    private void removeFromBoard(BoardInfo board, User us, byte roomID, boolean notify) {
        synchronized (board) {
            int seat = board.seatOf(us);
            if (seat < 0) return;
            boolean wasPlaying = board.phase != 0;
            // Tiến Lên: người đang chơi rời giữa ván → huỷ ván trước
            // khi gỡ ghế (engine cần ghế còn nguyên để tra tay bài).
            if (board.gameType == 3) TienLenEngine.onLeave(board, roomID, us);
            if (board.gameType == 7) {
                avatar.minigame.PhomEngine.onLeave(board, roomID, us);
            }
            if (board.gameType == 21) {
                avatar.minigame.DiamondEngine.onLeave(board, roomID, us);
            }
            board.removeUser(us);

            if (board.presentCount() == 0) {
                board.phase = 0;
                board.phaseGen++;
                board.turnSeat = -1;
                board.seatSnap = null;
                board.setPlaying(false);
                if (board.gameType == 3) {
                    board.tl.resetHand();
                    board.tl.firstMatch = true;
                }
                if (board.gameType == 7) board.phom.resetHand();
                BoardManager.getInstance().cleanupEmptyDynamic(board);
                return;
            }
            if (notify) {
                try {
                    Message ms = new Message(Cmd.SOMEONE_LEAVEBOARD);
                    DataOutputStream ds = ms.writer();
                    ds.writeInt(us.getId());
                    User owner = board.owner();
                    ds.writeInt(owner == null ? -1 : owner.getId());
                    ds.flush();
                    broadcast(board, ms);
                } catch (IOException ignored) {
                }
            }
            if (!wasPlaying) return;

            if (seat == 0) {
                // Chủ bàn rời giữa ván: huỷ ván, mọi delta = 0.
                abortRound(board, roomID);
                return;
            }
            if (board.phase == 1) {
                // Người rời coi như chốt với số đã gửi (thường là 0).
                us.setToXong(true);
                checkAllBetDone(board, roomID);
            } else if (board.phase == 2 && board.turnSeat == seat) {
                us.setHaPhom(true);
                advanceTa(board, roomID);
            }
        }
    }

    /** Huỷ ván đang chơi: FINISH toàn 0 cho người còn lại, về pha rảnh. */
    private void abortRound(BoardInfo board, byte roomID) {
        board.phase = 0;
        board.phaseGen++;
        board.turnSeat = -1;
        board.setPlaying(false);
        try {
            Message ms = boardMsg(Cmd.FINISH, roomID, board.boardID);
            DataOutputStream ds = ms.writer();
            for (int s = 0; s < 5; s++) ds.writeInt(0);
            ds.flush();
            broadcast(board, ms);
        } catch (IOException ignored) {
        }
        clearRoundState(board);
    }

    private void clearRoundState(BoardInfo board) {
        board.seatSnap = null;
        for (User u : board.getLstUsers()) {
            if (u == null) continue;
            u.getMoneyPutList().clear();
            u.setToXong(false);
            u.setHaPhom(false);
        }
    }

    private void kick(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        int kickID = rd.readInt();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null) return;
        synchronized (board) {
            User owner = board.owner();
            if (owner == null || owner.getId() != us.getId()) return; // chỉ chủ bàn
            User target = null;
            for (User u : board.getLstUsers()) {
                if (u != null && u.getId() == kickID) target = u;
            }
            if (target == null || target.getId() == us.getId()) return;
            // Báo cho cả bàn (kể cả người bị đuổi, vẫn đang ở bàn) trước khi gỡ.
            Message mk = boardMsg(Cmd.KICK, roomID, boardID);
            mk.writer().writeInt(kickID);
            mk.writer().flush();
            broadcast(board, mk);
            removeFromBoard(board, target, roomID, false);
        }
    }

    private void setPassword(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        rd.readByte(); // roomID
        byte boardID = rd.readByte();
        String pass = rd.readUTF();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null) return;
        synchronized (board) {
            User owner = board.owner();
            if (owner == null || owner.getId() != us.getId()) return;
            board.password = pass == null ? "" : pass;
            board.isPass = !board.password.isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Chuẩn bị ván
    // ------------------------------------------------------------------

    private void chatToBoard(Message ms) throws IOException {
        DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        String text = rd.readUTF();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null || this.client.user == null) return;
        Message out = boardMsg(Cmd.CHAT_TO_BOARD, roomID, boardID);
        DataOutputStream ds = out.writer();
        ds.writeInt(this.client.user.getId());
        ds.writeUTF(text);
        ds.flush();
        broadcast(board, out);
    }

    private void ready(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        rd.readByte(); // roomID
        byte boardID = rd.readByte();
        boolean isReady = rd.readBoolean();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null || board.seatOf(us) < 0) return;
        synchronized (board) {
            if (isReady) board.readyIds.add(us.getId());
            else board.readyIds.remove(us.getId());
            Message out = new Message(Cmd.READY);
            DataOutputStream ds = out.writer();
            ds.writeInt(us.getId());
            ds.writeBoolean(isReady);
            ds.flush();
            broadcast(board, out);
        }
    }

    private void setMoney(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        int money = rd.readInt();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null) return;
        synchronized (board) {
            User owner = board.owner();
            if (owner == null || owner.getId() != us.getId()) return; // chỉ chủ bàn
            if (money > 100000) {
                us.getAvatarService().serverDialog("Vui lòng đặt nhỏ hơn 100.000");
                return;
            }
            // Bầu Cua cần dự trữ ×48 (đối ứng cả bàn); Tiến Lên/Phỏm ×8 [PORT].
            long need = (long) money * ((board.gameType == 3 || board.gameType == 7
                || board.gameType == 21) ? 8 : 48);
            if (money < 0 || us.getXu() < need) {
                us.getAvatarService().serverDialog(
                    "Để đặt được " + money + " thì bạn cần có số tiền là " + need);
                return;
            }
            board.setMoney(money);
            Message out = boardMsg(Cmd.SET_MONEY, roomID, boardID);
            out.writer().writeInt(money);
            out.writer().flush();
            broadcast(board, out);
        }
    }

    // ------------------------------------------------------------------
    // Luồng ván: START → đặt (21) → tả (100/65/49) → kết quả (37/60/51)
    // ------------------------------------------------------------------

    private void start(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null) return;
        if (board.gameType == 3) { // Tiến Lên: engine riêng
            TienLenEngine.start(this.client, board, roomID);
            return;
        }
        if (board.gameType == 7) { // Phỏm: engine riêng
            avatar.minigame.PhomEngine.start(this.client, board, roomID);
            return;
        }
        if (board.gameType == 21) { // Diamond: engine riêng
            avatar.minigame.DiamondEngine.start(this.client, board, roomID);
            return;
        }
        synchronized (board) {
            User owner = board.owner();
            if (owner == null || owner.getId() != us.getId()) return; // chỉ chủ bàn
            if (board.phase != 0) return;
            if (board.presentCount() < 2) {
                us.getAvatarService().serverDialog("Cần ít nhất 2 người chơi mới bắt đầu được");
                return;
            }
            if (board.getMoney() <= 0) {
                us.getAvatarService().serverDialog("Hãy đặt mức tiền của bàn trước");
                return;
            }
            // Ảnh chụp ghế cho cả ván + xoá trạng thái cũ.
            board.seatSnap = new User[5];
            for (int s = 0; s < 5; s++) {
                board.seatSnap[s] = board.seats[s];
                board.seatBets[s] = new byte[6];
                board.seatDeltas[s] = 0;
            }
            for (User u : board.getLstUsers()) {
                if (u == null) continue;
                u.getMoneyPutList().clear();
                boolean dealer = board.seatOf(u) == 0;
                u.setToXong(dealer);   // chủ bàn không đặt cược
                u.setHaPhom(dealer);   // chủ bàn không tả
            }
            board.setPlaying(true);
            board.phase = 1;
            board.phaseGen++;
            board.turnSeat = -1;
            board.phaseDeadline = System.currentTimeMillis() + PHASE_TIME * 1000L;

            Message out = boardMsg(Cmd.START, roomID, boardID);
            out.writer().writeByte(PHASE_TIME);
            out.writer().flush();
            broadcast(board, out);

            final int gen = board.phaseGen;
            TIMER.schedule(() -> betTimeout(board, roomID, gen),
                PHASE_TIME * 1000L + 3000, TimeUnit.MILLISECONDS);
        }
    }

    /** Hết giờ pha đặt: ai chưa chốt thì chốt với số đã gửi (thường 0). */
    private void betTimeout(BoardInfo board, byte roomID, int gen) {
        synchronized (board) {
            if (board.phase != 1 || board.phaseGen != gen) return;
            for (int s = 1; s < 5; s++) {
                User u = board.seats[s];
                if (u != null && !u.isToXong()) {
                    u.setToXong(true);
                    broadcastBets(board, roomID, s);
                }
            }
            checkAllBetDone(board, roomID);
        }
    }

    private void toXong(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null) return;
        if (board.gameType == 3) { // Tiến Lên: gói 21 = MOVE {n, cards}
            int n = rd.available() > 0 ? rd.readByte() : 0;
            byte[] cards = new byte[Math.max(0, n)];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = rd.available() > 0 ? rd.readByte() : 0;
            }
            TienLenEngine.move(this.client, board, roomID, cards);
            return;
        }
        if (board.gameType == 7) { // Phỏm: gói 21 = FIRE {byte cardID}
            int card = rd.available() > 0 ? rd.readByte() : -1;
            avatar.minigame.PhomEngine.fire(this.client, board, roomID, card);
            return;
        }
        if (board.gameType == 21) { // Diamond: gói 21 = MOVE {iSelected, selected}
            int iSel = rd.available() > 0 ? rd.readByte() : -1;
            int sel = rd.available() > 0 ? rd.readByte() : -1;
            avatar.minigame.DiamondEngine.move(this.client, board, roomID, iSel, sel);
            return;
        }
        synchronized (board) {
            int seat = board.seatOf(us);
            if (board.phase != 1 || seat <= 0 || us.isToXong()) return;

            List<Byte> bets = new ArrayList<>();
            while (rd.available() > 0 && bets.size() < 6) bets.add(rd.readByte());
            while (bets.size() < 6) bets.add((byte) 0);

            // Luật gốc: tối đa 6 đơn vị/người/ván — cắt phần thừa từ ô cuối.
            int total = 0;
            for (byte b : bets) total += Math.max(0, b);
            for (int d = 5; d >= 0 && total > 6; d--) {
                int cut = Math.min(Math.max(0, bets.get(d)), total - 6);
                bets.set(d, (byte) (Math.max(0, bets.get(d)) - cut));
                total -= cut;
            }
            // Không đủ xu trả cược thì coi như không đặt.
            if ((long) total * board.getMoney() > us.getXu()) {
                us.getAvatarService().serverDialog("Không đủ xu để đặt cược");
                bets.replaceAll(b -> (byte) 0);
            }

            List<Byte> put = us.getMoneyPutList();
            put.clear();
            put.addAll(bets);
            us.setToXong(true);
            broadcastBets(board, roomID, seat);
            checkAllBetDone(board, roomID);
        }
    }

    /** Phát gói 21 (tiền đã đặt của một ghế) cho cả bàn. */
    private void broadcastBets(BoardInfo board, byte roomID, int seat) {
        User u = board.seats[seat];
        if (u == null) return;
        try {
            Message out = boardMsg(Cmd.TO_XONG, roomID, board.boardID);
            DataOutputStream ds = out.writer();
            ds.writeByte(seat);
            List<Byte> put = u.getMoneyPutList();
            for (int d = 0; d < 6; d++) {
                ds.writeByte(put != null && d < put.size() ? put.get(d) : 0);
            }
            ds.flush();
            broadcast(board, out);
        } catch (IOException ignored) {
        }
    }

    /** Mọi người đặt đã chốt → chụp tiền đặt, chuyển sang pha tả. */
    private void checkAllBetDone(BoardInfo board, byte roomID) {
        if (board.phase != 1) return;
        for (int s = 1; s < 5; s++) {
            User u = board.seats[s];
            if (u != null && !u.isToXong()) return;
        }
        // Chụp tiền đặt theo ghế (người đã rời vẫn giữ cược đã chốt).
        if (board.seatSnap != null) {
            for (int s = 1; s < 5; s++) {
                User u = board.seatSnap[s];
                if (u == null) continue;
                List<Byte> put = u.getMoneyPutList();
                for (int d = 0; d < 6; d++) {
                    board.seatBets[s][d] = (byte) Math.max(0,
                        put != null && d < put.size() ? put.get(d) : 0);
                }
            }
        }
        board.phase = 2;
        board.phaseGen++;
        board.turnSeat = -1;
        advanceTa(board, roomID);
    }

    /** Gọi lượt tả cho ghế đặt tiếp theo còn ở bàn; hết người thì mở kết quả. */
    private void advanceTa(BoardInfo board, byte roomID) {
        if (board.phase != 2) return;
        for (int s = board.turnSeat + 1; s < 5; s++) {
            User u = board.seats[s];
            if (u == null || u.isHaPhom()) continue;
            if (board.seatSnap == null || board.seatSnap[s] == null) continue;
            board.turnSeat = s;
            board.phaseGen++;
            board.phaseDeadline = System.currentTimeMillis() + PHASE_TIME * 1000L;
            try {
                Message out = boardMsg(Cmd.SET_TURN, roomID, board.boardID);
                out.writer().writeByte(s);
                out.writer().flush();
                broadcast(board, out);
            } catch (IOException ignored) {
            }
            final int gen = board.phaseGen;
            final int turnSeat = s;
            TIMER.schedule(() -> taTimeout(board, roomID, gen, turnSeat),
                PHASE_TIME * 1000L + 3000, TimeUnit.MILLISECONDS);
            return;
        }
        board.turnSeat = -1;
        gameResult(board, roomID);
    }

    /** Hết giờ lượt tả: tự bỏ lượt cho ghế đó. */
    private void taTimeout(BoardInfo board, byte roomID, int gen, int turnSeat) {
        synchronized (board) {
            if (board.phase != 2 || board.phaseGen != gen || board.turnSeat != turnSeat) return;
            User u = board.seats[turnSeat];
            if (u != null) u.setHaPhom(true);
            advanceTa(board, roomID);
        }
    }

    /** Gói 65: Bầu Cua = tả (dời tiền) · Phỏm = hạ phỏm — rẽ theo
     *  game của bàn người gửi đang ngồi (không đọc gói trước). */
    private void haPhomDispatch(Message ms, User us) throws IOException {
        if (us == null) return;
        BoardInfo board = BoardManager.getInstance().findUserBoard(us);
        if (board != null && board.gameType == 7) {
            java.io.DataInputStream rd = ms.reader();
            byte roomID = rd.readByte();
            rd.readByte(); // boardID
            java.util.List<java.util.List<Integer>> groups = new java.util.ArrayList<>();
            java.util.List<Integer> cur = new java.util.ArrayList<>();
            while (rd.available() > 0) {
                byte v = rd.readByte();
                if (v == -1) {
                    if (!cur.isEmpty()) {
                        groups.add(cur);
                        cur = new java.util.ArrayList<>();
                    }
                } else {
                    cur.add((int) v);
                }
            }
            if (!cur.isEmpty()) groups.add(cur);
            avatar.minigame.PhomEngine.haPhom(this.client, board, roomID, groups);
            return;
        }
        haPhom(ms, us);
    }

    /** Gói 63 — Phỏm: bốc bài. */
    private void getCardPhom(Message ms, User us) {
        if (us == null) return;
        BoardInfo board = BoardManager.getInstance().findUserBoard(us);
        if (board == null || board.gameType != 7) return;
        avatar.minigame.PhomEngine.getCard(this.client, board,
            (byte) us.getRoomID());
    }

    /** Gói 64 — Phỏm: ăn bài {room, board, các lá trên tay, index}. */
    private void eatCardPhom(Message ms, User us) throws IOException {
        if (us == null) return;
        BoardInfo board = BoardManager.getInstance().findUserBoard(us);
        if (board == null || board.gameType != 7) return;
        java.io.DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        rd.readByte(); // boardID
        java.util.List<Byte> raw = new java.util.ArrayList<>();
        while (rd.available() > 0) raw.add(rd.readByte());
        // byte cuối là index vị trí lá ăn của client gốc — bỏ qua
        int n = Math.max(0, raw.size() - 1);
        int[] cards = new int[n];
        for (int i = 0; i < n; i++) cards[i] = raw.get(i);
        avatar.minigame.PhomEngine.eat(this.client, board, roomID, cards);
    }

    /** Gói 67 — Phỏm: gửi bài {room, board, các lá}. */
    private void addCardPhom(Message ms, User us) throws IOException {
        if (us == null) return;
        BoardInfo board = BoardManager.getInstance().findUserBoard(us);
        if (board == null || board.gameType != 7) return;
        java.io.DataInputStream rd = ms.reader();
        byte roomID = rd.readByte();
        rd.readByte(); // boardID
        java.util.List<Integer> list = new java.util.ArrayList<>();
        while (rd.available() > 0) list.add((int) rd.readByte());
        int[] cards = new int[list.size()];
        for (int i = 0; i < cards.length; i++) cards[i] = list.get(i);
        avatar.minigame.PhomEngine.addCard(this.client, board, roomID, cards);
    }

    private void haPhom(Message ms, User us) throws IOException {
        if (us == null) return;
        DataInputStream rd = ms.reader();
        // Client gốc gửi 6 byte: room, board, room, board, from, to.
        byte roomID = rd.readByte();
        byte boardID = rd.readByte();
        rd.readByte(); // room lặp lại (lỗi nhân đôi của client gốc)
        rd.readByte(); // board lặp lại
        byte from = rd.readByte();
        byte to = rd.readByte();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        if (board == null) return;
        synchronized (board) {
            int seat = board.seatOf(us);
            if (board.phase != 2 || seat < 0 || board.turnSeat != seat) return;
            if (from < 0 || from > 5 || to < 0 || to > 5 || from == to) return;
            if (board.seatBets[seat][from] <= 0) return;
            // Dời TOÀN BỘ tiền ở ô nguồn sang ô đích.
            board.seatBets[seat][to] = (byte) (board.seatBets[seat][to] + board.seatBets[seat][from]);
            board.seatBets[seat][from] = 0;
            // Đồng bộ danh sách đặt của người chơi (cho gói PLAYING).
            List<Byte> put = us.getMoneyPutList();
            if (put != null && put.size() == 6) {
                put.set(to, board.seatBets[seat][to]);
                put.set(from, (byte) 0);
            }
            Message out = boardMsg(Cmd.HA_PHOM, roomID, boardID);
            DataOutputStream ds = out.writer();
            ds.writeByte(seat);
            ds.writeByte(from);
            ds.writeByte(to);
            ds.writeByte(board.seatBets[seat][to]);
            ds.flush();
            broadcast(board, out);
            us.setHaPhom(true);
            advanceTa(board, roomID);
        }
    }

    private void skip(Message ms, User us) throws IOException {
        if (us == null) return;
        BoardInfo board = BoardManager.getInstance().findUserBoard(us);
        if (board == null) return;
        byte roomID = (byte) us.getRoomID();
        if (ms.reader().available() >= 2) {
            roomID = ms.reader().readByte();
            ms.reader().readByte(); // boardID (không cần, đã có bàn theo người)
        }
        if (board.gameType == 3) { // Tiến Lên: gói 49 = SKIP
            TienLenEngine.skip(this.client, board, roomID);
            return;
        }
        if (board.gameType == 7) return; // Phỏm: client không gửi 49
        if (board.gameType == 21) { // Diamond: bỏ lượt
            avatar.minigame.DiamondEngine.skip(this.client, board, roomID);
            return;
        }
        synchronized (board) {
            int seat = board.seatOf(us);
            if (board.phase != 2 || seat < 0 || board.turnSeat != seat) return;
            us.setHaPhom(true);
            advanceTa(board, roomID);
        }
    }

    // ------------------------------------------------------------------
    // Kết quả ván
    // ------------------------------------------------------------------

    private void gameResult(BoardInfo board, byte roomID) {
        board.phase = 3;
        board.phaseGen++;
        board.turnSeat = -1;
        board.phaseDeadline = 0;

        final int[] dice = new int[3];
        try {
            Message out = boardMsg(Cmd.GAME_RESULT, roomID, board.boardID);
            DataOutputStream ds = out.writer();
            for (int i = 0; i < 3; i++) {
                dice[i] = Utils.nextInt(6); // đủ 6 mặt 0..5 (fan bị thiếu Tôm)
                ds.writeByte(dice[i]);
            }
            ds.flush();
            broadcast(board, out);
        } catch (IOException ignored) {
        }

        // Tính delta: mỗi ô đặt s đơn vị, ra k mặt trùng → +k·s; trượt → −s.
        // Chủ bàn (ghế 0) đối ứng toàn bộ (zero-sum).
        int money = board.getMoney();
        int dealerDelta = 0;
        for (int s = 1; s < 5; s++) {
            if (board.seatSnap == null || board.seatSnap[s] == null) {
                board.seatDeltas[s] = 0;
                continue;
            }
            int delta = 0;
            for (int d = 0; d < 6; d++) {
                int stake = board.seatBets[s][d];
                if (stake <= 0) continue;
                int k = 0;
                for (int die : dice) if (die == d) k++;
                delta += (k > 0 ? k * stake : -stake) * money;
            }
            board.seatDeltas[s] = delta;
            dealerDelta -= delta;
        }
        board.seatDeltas[0] = dealerDelta;

        // Chủ bàn không đủ xu trả phần thắng: giảm tiền thắng theo tỉ lệ
        // để chủ bàn không âm xu (người thua vẫn mất đủ phần của mình).
        User dealer = board.seatSnap == null ? null : board.seatSnap[0];
        if (dealer != null && dealerDelta < 0 && dealer.getXu() < -dealerDelta) {
            long winTotal = 0, loseTotal = 0;
            for (int s = 1; s < 5; s++) {
                if (board.seatDeltas[s] > 0) winTotal += board.seatDeltas[s];
                else loseTotal -= board.seatDeltas[s];
            }
            long canPay = dealer.getXu() + loseTotal;
            if (winTotal > 0 && canPay < winTotal) {
                int paid = 0;
                for (int s = 1; s < 5; s++) {
                    if (board.seatDeltas[s] > 0) {
                        board.seatDeltas[s] = (int) (board.seatDeltas[s] * canPay / winTotal);
                        paid += board.seatDeltas[s];
                    }
                }
                board.seatDeltas[0] = (int) (paid - loseTotal) * -1;
            }
        }

        final int gen = board.phaseGen;
        TIMER.schedule(() -> finishRound(board, roomID, gen),
            RESULT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Sau 2,5 giây: cộng/trừ xu thật, phát WIN từng khoản rồi FINISH. */
    private void finishRound(BoardInfo board, byte roomID, int gen) {
        synchronized (board) {
            if (board.phase != 3 || board.phaseGen != gen) return;

            // Áp tiền vào tài khoản (người đã rời nhưng còn phiên thì vẫn
            // nhận qua chính đối tượng User trong ảnh chụp ghế).
            if (board.seatSnap != null) {
                for (int s = 0; s < 5; s++) {
                    User u = board.seatSnap[s];
                    if (u != null && board.seatDeltas[s] != 0) {
                        u.updateXu(board.seatDeltas[s]);
                    }
                }
            }

            // WIN (60): từng khoản chuyển — người thua → chủ bàn,
            // chủ bàn → người thắng. Mỗi khoản một gói như client gốc đọc.
            try {
                for (int s = 1; s < 5; s++) {
                    int delta = board.seatDeltas[s];
                    if (delta == 0) continue;
                    Message win = boardMsg(Cmd.WIN, roomID, board.boardID);
                    DataOutputStream dw = win.writer();
                    if (delta < 0) {
                        dw.writeByte(s);
                        dw.writeByte(0);
                        dw.writeInt(-delta);
                    } else {
                        dw.writeByte(0);
                        dw.writeByte(s);
                        dw.writeInt(delta);
                    }
                    dw.flush();
                    broadcast(board, win);
                }
                Message fin = boardMsg(Cmd.FINISH, roomID, board.boardID);
                DataOutputStream df = fin.writer();
                for (int s = 0; s < 5; s++) df.writeInt(board.seatDeltas[s]);
                df.flush();
                broadcast(board, fin);
            } catch (IOException ignored) {
            }

            board.phase = 0;
            board.phaseGen++;
            board.setPlaying(false);
            board.readyIds.clear();
            clearRoundState(board);
        }
    }
}
