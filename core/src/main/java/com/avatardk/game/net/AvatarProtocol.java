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
    public static final byte REMOVE_FRIEND = -20;
    public static final byte REQUEST_FRIENDLIST = -18;
    public static final byte CUSTOM_LIST = -81;
    public static final byte MAIL_LIST = -108;
    public static final byte MAIL_READ = -109;
    public static final byte MAIL_CLAIM = -110;
    public static final byte MAIL_CLAIM_ALL = -111;
    public static final byte MAIL_DELETE_READ = -112;
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
    /** Giỏ đồ: C→S {int id} xin danh sách; S→C {short n, n×(short idPart,
     *  byte time, UTF hạn dùng)} + đuôi mở rộng của bản port: byte sức chứa
     *  giỏ (Cmd.CONTAINER của gốc; MapScr.onContainer mở PopupShop giỏ). */
    public static final byte CONTAINER = -47;
    /** Mặc/cất đồ: C→S {short idPart, byte type} (1 = dùng từ giỏ, 0 = cất
     *  từ đang mặc); S→C {int id người, short idPart} phát cả zone, bên
     *  nhận tự đảo trạng thái mặc của part đó (Cmd.USING_PART của gốc). */
    public static final byte USING_PART = -48;
    /** Xoá đồ: C→S {short idPart, byte type} (1 = trong giỏ, 0 = đang mặc);
     *  S→C {int id người, short idPart} (Cmd.REMOVE_ITEM của gốc). */
    public static final byte REMOVE_ITEM = -36;
    /** Rương nhà: C→S rỗng để xin; S→C {short n1, n1×(short, byte, UTF)
     *  giỏ, int xu-trong-rương (bản port bỏ qua), byte cấp rương, short n2,
     *  n2×(...) rương} + đuôi mở rộng: byte sức chứa giỏ, byte sức chứa
     *  rương (Cmd.CUSTOM_CHEST của gốc). */
    public static final byte CUSTOM_CHEST = -87;
    /** Chuyển đồ giỏ ↔ rương: C→S {byte hướng (0 = giỏ→rương, 1 = rương→
     *  giỏ), short vị trí, short idPart}; S→C {boolean xong, UTF báo}
     *  (Cmd.TRANS_PART_CHEST của gốc). */
    public static final byte TRANS_CHEST = -89;
    /** Hộp thoại từ server (Cmd.SET_MONEY_ERROR của gốc): S→C {UTF}. */
    public static final byte SERVER_DIALOG = -10;
    public static final byte CHANGE_PASS = -62;
    /** Mua đồ ở shop: C→S {short idPart, byte type} (1 = trả Xu, 2 = trả
     *  Lượng); S→C khi thành công {short idPart, int giá đã trừ, byte 1,
     *  UTF thông báo, int xu, int lượng, int lượng khoá} — server tự mặc
     *  món mới và phát gói −48 cho cả khu (Cmd.AVATAR_BUY_ITEM của gốc). */
    public static final byte BUY_ITEM = -24;
    /** Server báo số tiền hiện tại: S→C {int xèng, byte type, int xu,
     *  int lượng, int lượng khoá} (Cmd.UPDATE_MONEY của gốc). */
    public static final byte UPDATE_MONEY = -33;
    /** Nói chuyện với NPC: C→S {int id NPC} — id trên đường truyền =
     *  2.000.000.000 + mã NPC (Npc.ID_ADD của gốc). Server trả menu của
     *  NPC qua gói −59 (Cmd.COMMUNICATE của gốc; khác hẳn gói thoại +61
     *  mở rộng của bản port). */
    public static final byte COMMUNICATE = -61;
    /** Menu lựa chọn của NPC/server: S→C {int id, byte menuId, byte số
     *  mục, các UTF tên mục, các short id mục, UTF tên NPC, UTF lời NPC,
     *  các boolean có-menu-con}; C→S chọn mục {int id, byte menuId,
     *  byte vị trí chọn} (Cmd.MENU_OPTION của gốc, dùng hai chiều). */
    public static final byte MENU_OPTION = -59;
    /** Server mở shop: S→C {byte idShop, UTF tên shop, short số món,
     *  các short idPart} — danh sách hàng do server quyết (Cmd.OPEN_SHOP
     *  của gốc, tới sau khi chọn mục shop trong menu NPC cửa hàng). */
    public static final byte OPEN_SHOP = -49;

    /** Handler Avatar trong GET_HANDLER (CLIENT_TYPE = 8 của gốc). */
    public static final byte HANDLER_AVATAR = 8;
    /** Handler Farm trong GET_HANDLER (HANDLER_FARM = 10 của gốc). */
    public static final byte HANDLER_FARM = 10;

    // ---- Farm (Cmd của gốc, handler FarmMsgHandler) ----
    public static final byte FARM_SET_BIG = 51;
    public static final byte FARM_GET_BIG = 54;
    public static final byte FARM_GET_IMAGE = 55;
    public static final byte FARM_GET_TREE_INFO = 56;
    public static final byte FARM_INVENTORY = 60;
    public static final byte FARM_JOIN = 61;
    public static final byte FARM_BUY_ITEM = 62;
    public static final byte FARM_SELL = 63;
    public static final byte FARM_PLANT = 64;
    public static final byte FARM_USE_ITEM = 65;
    public static final byte FARM_HARVEST = 66;
    public static final byte FARM_PRICE_SLOT = 69;
    public static final byte FARM_OPEN_LAND = 70;
    public static final byte FARM_BUY_ANIMAL = 71;
    public static final byte FARM_PRICE_ANIMAL = 72;
    public static final byte FARM_SELL_ANIMAL = 73;
    public static final byte FARM_HARVEST_ANIMAL = 74;
    public static final byte FARM_HARVEST_STAR = 85;
    public static final byte FARM_COOK = 91;
    public static final byte FARM_HARVEST_COOK = 92;
    public static final byte FARM_CELL_UPDATE = 78;
    public static final byte FARM_ANIMAL_UPDATE = 79;
    public static final byte FARM_GET_IMG = 82;
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

    /** Tạo nhân vật (RegisterScr gốc): byte gender (1 = nam, 2 = nữ) +
     *  byte số part + short id từng part của seriPart đang mặc, theo thứ
     *  tự [quần, áo, thân, mặt, tóc] mà server gốc kiểm tra. Server bản
     *  port dùng đúng bộ đồ này nếu hợp lệ (đồ cấp 0 đúng giới tính),
     *  không hợp lệ thì quay về set khởi đầu cố định của gốc. Cuối gói
     *  thêm UTF tên nhân vật (bổ sung của bản port — server gốc không có
     *  khái niệm tên nhân vật riêng; server cũ bỏ qua phần dư này). */
    public static void createCharacter(GameConnection conn, int gender,
                                       java.util.List<Integer> partIds,
                                       String charName) throws IOException {
        Msg m = new Msg(CRE_CHARACTER);
        DataOutputStream w = m.writer();
        w.writeByte(gender);
        w.writeByte(partIds.size());
        for (int id : partIds) w.writeShort(id);
        w.writeUTF(charName == null ? "" : charName);
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

    /** Gửi hành động nhân vật (gói 57, byte < 100 — cùng gói với biểu
     *  cảm như gốc: MapScr.doAction gọi AvatarService.doFeel): byte là
     *  mã Avatar.action (3 = Ngồi bệt, 4 = Nằm, 5 = Quỳ, 10 = Nhảy,
     *  0 = đứng dậy). */
    public static void actionPark(GameConnection conn, int actionByte) throws IOException {
        Msg m = new Msg(AVATAR_FEEL);
        m.writer().writeByte(actionByte);
        conn.send(m);
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

    /** Xin danh sách bạn (gói −18, không payload) — server trả gói −81
     *  CUSTOM_LIST với id "friendlist" (định dạng ListScr của gốc). */
    public static void requestFriendList(GameConnection conn) throws IOException {
        conn.send(new Msg(REQUEST_FRIENDLIST));
    }

    /** Xin danh sách thư (gói −108, không payload) — server trả −108
     *  với toàn bộ thư của người chơi. */
    public static void requestMailList(GameConnection conn) throws IOException {
        conn.send(new Msg(MAIL_LIST));
    }

    /** Đánh dấu thư đã đọc (gói −109): int id thư. */
    public static void mailRead(GameConnection conn, int letterId) throws IOException {
        Msg m = new Msg(MAIL_READ);
        m.writer().writeInt(letterId);
        conn.send(m);
    }

    /** Nhận quà đính kèm của một thư (gói −110): int id thư. */
    public static void mailClaim(GameConnection conn, int letterId) throws IOException {
        Msg m = new Msg(MAIL_CLAIM);
        m.writer().writeInt(letterId);
        conn.send(m);
    }

    /** Nhận quà mọi thư chưa nhận (gói −111, không payload). */
    public static void mailClaimAll(GameConnection conn) throws IOException {
        conn.send(new Msg(MAIL_CLAIM_ALL));
    }

    /** Xoá các thư đã đọc không còn quà (gói −112, không payload). */
    public static void mailDeleteRead(GameConnection conn) throws IOException {
        conn.send(new Msg(MAIL_DELETE_READ));
    }

    /** Xoá bạn (gói −20): int id của bạn cần xoá. */
    public static void removeFriend(GameConnection conn, int idFriend) throws IOException {
        Msg m = new Msg(REMOVE_FRIEND);
        m.writer().writeInt(idFriend);
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

    /** Hộp nhập văn bản của gốc (TEXT_BOX). */
    public static final byte TEXT_BOX = -60;

    /** Gửi nội dung hộp nhập văn bản (gói −60): {int userId, byte
     *  menuId, UTF text}. menuId 20 = nhập giftcode. */
    public static void sendTextBox(GameConnection conn, int userId, int menuId, String text) throws IOException {
        Msg m = new Msg(TEXT_BOX);
        DataOutputStream w = m.writer();
        w.writeInt(userId);
        w.writeByte(menuId);
        w.writeUTF(text);
        conn.send(m);
    }

    /** Đổi mật khẩu (gói −62 của gốc): UTF mật khẩu cũ + UTF mật khẩu
     *  mới. Server đúng mật khẩu cũ thì cập nhật MD5 mới, trả lại gói −62
     *  {UTF mật khẩu mới} kèm dialog "Đổi mật khẩu thành công."; sai thì
     *  dialog "Mật khẩu cũ không đúng." */
    public static void changePassword(GameConnection conn, String oldPass, String newPass)
            throws IOException {
        Msg m = new Msg(CHANGE_PASS);
        DataOutputStream w = m.writer();
        w.writeUTF(oldPass);
        w.writeUTF(newPass);
        conn.send(m);
    }

    /** Xin thông tin nhân vật của người khác (gói −22): int id. */
    public static void requestYourInfo(GameConnection conn, int id) throws IOException {
        Msg m = new Msg(REQUEST_YOUR_INFO);
        m.writer().writeInt(id);
        conn.send(m);
    }

    /** Xin danh sách giỏ đồ của chính mình (gói −47): int id của mình. */
    public static void requestContainer(GameConnection conn, int id) throws IOException {
        Msg m = new Msg(CONTAINER);
        m.writer().writeInt(id);
        conn.send(m);
    }

    /** Mặc đồ từ giỏ (type 1) / cất đồ đang mặc vào giỏ (type 0) — gói −48. */
    public static void usingPart(GameConnection conn, int partId, int type) throws IOException {
        Msg m = new Msg(USING_PART);
        DataOutputStream w = m.writer();
        w.writeShort(partId);
        w.writeByte(type);
        conn.send(m);
    }

    /** Xoá hẳn một món (gói −36): type 1 = trong giỏ, 0 = đang mặc. */
    public static void removeItem(GameConnection conn, int partId, int type) throws IOException {
        Msg m = new Msg(REMOVE_ITEM);
        DataOutputStream w = m.writer();
        w.writeShort(partId);
        w.writeByte(type);
        conn.send(m);
    }

    /** Xin dữ liệu rương nhà (gói −87): gói rỗng. */
    public static void requestChest(GameConnection conn) throws IOException {
        conn.send(new Msg(CUSTOM_CHEST));
    }

    /** Mua một món ở shop (gói −24): type 1 = trả bằng Xu, 2 = bằng Lượng. */
    public static void buyItem(GameConnection conn, int partId, int type) throws IOException {
        Msg m = new Msg(BUY_ITEM);
        DataOutputStream w = m.writer();
        w.writeShort(partId);
        w.writeByte(type);
        conn.send(m);
    }

    /** Mua theo hoá đơn (gói −24 + 1 byte): days = số ngày hạn dùng
     *  theo mốc đã chọn (0 = Vĩnh viễn, luôn dùng cho Lượng). Server
     *  tính giá theo mốc; client không gửi byte này thì server mua
     *  theo hạn gốc của món như Pack5. */
    public static void buyItem(GameConnection conn, int partId, int type, int days) throws IOException {
        Msg m = new Msg(BUY_ITEM);
        DataOutputStream w = m.writer();
        w.writeShort(partId);
        w.writeByte(type);
        w.writeByte(days);
        conn.send(m);
    }

    /** Nói chuyện với NPC (gói −61): id truyền = 2.000.000.000 + mã NPC. */
    public static void communicate(GameConnection conn, int npcWireId) throws IOException {
        Msg m = new Msg(COMMUNICATE);
        m.writer().writeInt(npcWireId);
        conn.send(m);
    }

    /** Chọn một mục trong menu NPC/server (gói −59 chiều đi). */
    public static void selectMenuOption(GameConnection conn, int userId, int menuId, int select) throws IOException {
        Msg m = new Msg(MENU_OPTION);
        DataOutputStream w = m.writer();
        w.writeInt(userId);
        w.writeByte(menuId);
        w.writeByte(select);
        conn.send(m);
    }

    /** Chuyển một món giữa giỏ và rương (gói −89): dir 0 = giỏ→rương,
     *  1 = rương→giỏ; index là vị trí món trong danh sách nguồn. */
    public static void transChest(GameConnection conn, int dir, int index, int partId)
            throws IOException {
        Msg m = new Msg(TRANS_CHEST);
        DataOutputStream w = m.writer();
        w.writeByte(dir);
        w.writeShort(index);
        w.writeShort(partId);
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
