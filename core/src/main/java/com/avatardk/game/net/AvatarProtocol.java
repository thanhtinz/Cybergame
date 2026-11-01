package com.avatardk.game.net;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Các gói tin phía client theo đúng client gốc (docs/protocol-spec.md):
 * cụm mở đầu sau bắt tay, đăng nhập, đăng ký, tạo nhân vật, vào map, chat.
 */
public final class AvatarProtocol {
    public static final byte GET_HANDLER = -1;
    public static final byte LOGIN = -2;
    public static final byte LOGIN_SUCESS = -4;
    public static final byte SERVER_MESSAGE = -9;
    public static final byte SET_PROVIDER = -17;
    public static final byte REQUEST_REGISTER = -30;
    public static final byte CRE_CHARACTER = -35;
    public static final byte SET_AGENT = -79;
    public static final byte AVATAR_JOIN_PARK = 50;
    /** Xin danh sách khu (board) của một map + server trả trạng thái từng khu. */
    public static final byte PARK_BOARD_LIST = 60;
    public static final byte PLAYER_JOIN = 51;
    public static final byte PLAYER_LEAVE = 53;
    public static final byte PLAYER_MOVE = 54;
    public static final byte CHAT_PARK = 55;
    /** Biểu cảm mặt trong map (Cmd.AVATAR_FEEL của server). */
    public static final byte AVATAR_FEEL = 57;
    /** Tin nhắn thoại trong map — GÓI MỞ RỘNG của bản port (game gốc
     *  không có voice): cmd 61 còn trống trong cả hai handler park của
     *  server; server chỉ chuyển tiếp mù theo zone, không giải mã audio. */
    public static final byte VOICE_PARK = 61;
    public static final byte CHAT_FARM = 77;
    /** Nhắn tin riêng 1-1: C→S {int id người nhận, UTF}; S→C {int type,
     *  UTF tên người gửi, UTF} (Cmd.CHAT_TO của gốc). */
    public static final byte CHAT_TO = -6;
    /** Kết bạn — trả lời lời mời: {int id người mời, boolean đồng ý}. Gói
     *  này server cũng dùng để CHUYỂN lời mời tới người nhận (boolean=false)
     *  và báo kết quả về người mời; client phân biệt bằng danh sách lời mời
     *  mình đang chờ (Cmd.ADD_FRIEND của gốc). */
    public static final byte ADD_FRIEND = -19;
    /** Gửi lời mời kết bạn: {int id người nhận} (Cmd.AVATAR_REQUEST_ADD_FRIEND). */
    public static final byte REQUEST_ADD_FRIEND = -21;
    /** Xin/xem thông tin nhân vật: C→S {int id}; S→C {int id, byte cấp,
     *  byte % cấp, byte thân thiện, byte điên rồ, byte phong cách,
     *  byte vui vẻ, byte độ no, ...} (Cmd.REQUEST_YOUR_INFO của gốc). */
    public static final byte REQUEST_YOUR_INFO = -22;
    /** Hành động đùa giữa hai người chơi (Đánh = 100, Hôn = 101):
     *  C→S {int id đích, short mã hành động}; S→C {int id nguồn, int id
     *  đích, short mã, short thời gian} — client gốc MapScr.onGivingDefferent.
     *  Server Pack5 thiếu handler nên bản port bổ sung phát cả zone như gói 61. */
    public static final byte GIVING_DEFERR = 59;

    /** Handler Avatar trong GET_HANDLER (CLIENT_TYPE = 8 của gốc). */
    public static final byte HANDLER_AVATAR = 8;
    public static final String CLIENT_VERSION = "2.5.8";

    private AvatarProtocol() {
    }

    /** Cụm 4 gói mở đầu client gốc gửi ngay khi vào màn đăng nhập
     *  (GlobalService.setProviderAndClientType). */
    public static void sendIntro(GameConnection conn, int width, int height) throws IOException {
        Msg m1 = new Msg(GET_HANDLER);
        m1.writer().writeByte(HANDLER_AVATAR);
        conn.send(m1);

        Msg m2 = new Msg(SET_PROVIDER);
        DataOutputStream w = m2.writer();
        w.writeByte(0);          // provider
        w.writeInt(10000);       // memory
        w.writeUTF("uni");       // platform
        w.writeInt(10000);       // rmsSize
        w.writeInt(width);
        w.writeInt(height);
        w.writeBoolean(true);
        w.writeByte(1);          // resourceType: 1 = HD
        w.writeUTF(CLIENT_VERSION);
        conn.send(m2);

        Msg m3 = new Msg(SET_AGENT);
        m3.writer().writeUTF("0");
        conn.send(m3);

        Msg m4 = new Msg((byte) -86);
        m4.writer().writeByte(2);
        m4.writer().writeByte(1);            // versionCode (nhánh không phải Android của gốc)
        m4.writer().writeUTF("desktop-java");
        conn.send(m4);
    }

    public static void login(GameConnection conn, String username, String password) throws IOException {
        Msg m = new Msg(LOGIN);
        DataOutputStream w = m.writer();
        w.writeUTF(username);
        w.writeUTF(password);
        w.writeUTF(CLIENT_VERSION);
        conn.send(m);
    }

    /** Đăng ký: bản 2 trường của client gốc (username, password). Server gốc
     *  không xử lý gói này — bản port đã bổ sung phía server (Session.doRegister). */
    public static void register(GameConnection conn, String username, String password) throws IOException {
        Msg m = new Msg(REQUEST_REGISTER);
        DataOutputStream w = m.writer();
        w.writeUTF(username);
        w.writeUTF(password);
        conn.send(m);
    }

    /** Tạo nhân vật: byte gender (1 = nam, 2 = nữ) + byte số đồ chọn.
     *  Server bỏ qua đồ client chọn, tự gán set khởi đầu theo giới tính. */
    public static void createCharacter(GameConnection conn, int gender) throws IOException {
        Msg m = new Msg(CRE_CHARACTER);
        DataOutputStream w = m.writer();
        w.writeByte(gender);
        w.writeByte(0);
        conn.send(m);
    }

    /** Xin vào map (docs/park-spec.md): tham số là CLIENT map id — trên dây
     *  gửi server map id = client id − 1; byte area (-1 = tự chọn khu),
     *  short x, short y (đơn vị medium). */
    public static void joinPark(GameConnection conn, int clientMapId, int x, int y) throws IOException {
        joinPark(conn, clientMapId, -1, x, y);
    }

    /** Vào map ở một KHU (zone) cụ thể: byte map (server), byte khu
     *  (0–9; −1 = server tự chọn khu còn trống), short x, short y.
     *  Chuyển khu = gửi lại gói này với khu khác, vẫn map đó, giữ vị trí. */
    public static void joinPark(GameConnection conn, int clientMapId, int area, int x, int y)
            throws IOException {
        Msg m = new Msg(AVATAR_JOIN_PARK);
        DataOutputStream w = m.writer();
        w.writeByte(clientMapId - 1);
        w.writeByte(area);
        w.writeShort(x);
        w.writeShort(y);
        conn.send(m);
    }

    /** Xin trạng thái các khu của map (gói 60): server trả byte số khu
     *  rồi mỗi khu một byte trạng thái (0 = đầy, 1 = đông, 2 = vắng). */
    public static void requestBoardList(GameConnection conn, int clientMapId) throws IOException {
        Msg m = new Msg(PARK_BOARD_LIST);
        m.writer().writeByte(clientMapId - 1);
        conn.send(m);
    }

    /** Báo di chuyển trong map (gói 54): short x, short y, byte direct
     *  (0 = phải, 2 = trái theo Base của gốc). */
    public static void movePark(GameConnection conn, int x, int y, boolean facingLeft) throws IOException {
        Msg m = new Msg(PLAYER_MOVE);
        DataOutputStream w = m.writer();
        w.writeShort(x);
        w.writeShort(y);
        w.writeByte(facingLeft ? 2 : 0);
        conn.send(m);
    }

    /** Chat trong map (gói 55): UTF text. Server phát lại cho cả zone bằng
     *  gói 55 (hoặc 77 ở server map 22), kèm id người nói. */
    public static void chatPark(GameConnection conn, String text) throws IOException {
        Msg m = new Msg(CHAT_PARK);
        m.writer().writeUTF(text);
        conn.send(m);
    }

    /** Mã hoá part mặt thành byte gói 57 theo đúng client gốc
     *  (MapScr.doSellectFeel: gửi partId + 100; bên nhận MapScr.onFeel:
     *  byte ≥ 100 → đổi mặt partId = byte − 100, byte < 100 → hành động).
     *  Byte đọc KHÔNG DẤU ở phía nhận của bản port nên partId mã hoá được
     *  tới 155 (100 + 155 = 255); partId ≤ 27 cho byte y hệt client gốc.
     *  Mặt có partId lớn hơn không có chỗ trên dây — trả -1 (không gửi). */
    public static int encodeFeelPart(int partId) {
        return partId >= 0 && partId <= 155 ? partId + 100 : -1;
    }

    /** Gửi đổi biểu cảm (gói 57): 1 byte = partId mặt + 100. Trả false nếu
     *  partId không mã hoá được — mặt đó chỉ hiện ở máy mình. */
    public static boolean feelPark(GameConnection conn, int partId) throws IOException {
        int enc = encodeFeelPart(partId);
        if (enc < 0) return false;
        Msg m = new Msg(AVATAR_FEEL);
        m.writer().writeByte(enc);
        conn.send(m);
        return true;
    }

    /** Nhắn tin riêng (gói −6): int id người nhận + UTF nội dung. */
    public static void chatTo(GameConnection conn, int idTo, String text) throws IOException {
        Msg m = new Msg(CHAT_TO);
        DataOutputStream w = m.writer();
        w.writeInt(idTo);
        w.writeUTF(text);
        conn.send(m);
    }

    /** Gửi lời mời kết bạn (gói −21): int id người nhận. */
    public static void requestAddFriend(GameConnection conn, int idTo) throws IOException {
        Msg m = new Msg(REQUEST_ADD_FRIEND);
        m.writer().writeInt(idTo);
        conn.send(m);
    }

    /** Trả lời lời mời kết bạn (gói −19): int id người mời + boolean đồng ý. */
    public static void addFriendReply(GameConnection conn, int idFrom, boolean accept)
            throws IOException {
        Msg m = new Msg(ADD_FRIEND);
        DataOutputStream w = m.writer();
        w.writeInt(idFrom);
        w.writeBoolean(accept);
        conn.send(m);
    }

    /** Xin thông tin nhân vật của người khác (gói −22): int id. */
    public static void requestYourInfo(GameConnection conn, int id) throws IOException {
        Msg m = new Msg(REQUEST_YOUR_INFO);
        m.writer().writeInt(id);
        conn.send(m);
    }

    /** Hành động đùa với người chơi khác (gói 59): int id đích + short mã
     *  (100 = đánh, 101 = hôn). Hiệu ứng hiện khi server phát lại cho zone. */
    public static void givingDeferrent(GameConnection conn, int idTo, int actionId)
            throws IOException {
        Msg m = new Msg(GIVING_DEFERR);
        DataOutputStream w = m.writer();
        w.writeInt(idTo);
        w.writeShort(actionId);
        conn.send(m);
    }

    /** Gửi một mẩu tin nhắn thoại (gói 61): int clipId, short seq,
     *  short total, rồi các byte audio μ-law (tối đa vài KB mỗi gói).
     *  Bên nhận ráp đủ total mẩu theo (người gửi, clipId) mới phát lại. */
    public static void voicePark(GameConnection conn, int clipId, int seq, int total,
                                 byte[] chunk) throws IOException {
        Msg m = new Msg(VOICE_PARK);
        DataOutputStream w = m.writer();
        w.writeInt(clipId);
        w.writeShort(seq);
        w.writeShort(total);
        w.write(chunk);
        conn.send(m);
    }
}
