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
    public static final byte PLAYER_JOIN = 51;
    public static final byte PLAYER_LEAVE = 53;
    public static final byte PLAYER_MOVE = 54;
    public static final byte CHAT_PARK = 55;
    public static final byte CHAT_FARM = 77;

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
        Msg m = new Msg(AVATAR_JOIN_PARK);
        DataOutputStream w = m.writer();
        w.writeByte(clientMapId - 1);
        w.writeByte(-1);
        w.writeShort(x);
        w.writeShort(y);
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
}
