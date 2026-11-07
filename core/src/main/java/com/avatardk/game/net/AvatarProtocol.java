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
    public static final byte MAIL_DELETE_READ = -114; // doi tu -112 (ban chinh danh cho CLAN) 2026-11-04
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
    /** Thử đồ (bản chính: APPLY_PREVIEW_ITEM): gửi {short partId},
     *  server phát lại cho cả khu {int userId, short partId};
     *  partId 0 = cởi đồ thử. Chỉ hiển thị tạm, không lưu. */
    public static final byte PREVIEW_ITEM = -100;
    /** Rương phụ cá nhân [PORT theo BoxScr bản chính]: phong bì
     *  −118, byte sub đầu: 0 OPEN · 1 UNLOCK {UTF} · 2 SETPASS {UTF} ·
     *  3 CHANGEPASS {UTF cũ, UTF mới} · 4 TRANS {byte hướng, short id}
     *  · 5 UPGRADE {byte 0 hỏi/1 xác nhận} · 10 STATE (S->C). */
    public static final byte BOX = -118;
    /** Tách chồng vật phẩm trong giỏ [PORT theo REMOVE_ITEM_STACK
     *  của bản chính]: gói −119 {short partId, short qty}. */
    public static final byte SPLIT_ITEM = -119;
    /** Danh hiệu [PORT theo TITLE của bản chính; số -98 của bản
     *  chính đã là REQUEST_IMAGE_PART ở bản fan nên port dùng
     *  -120]: sub 0 LIST, 1 SET {short id}, 5 ZONE_REQ; server trả
     *  sub 0 STATE, 3 WEAR (cả khu), 4 ZONE_STATE. */
    public static final byte TITLE = -120;
    /** Đố vui trên map [PORT theo ANSWERQUESTION của bản chính;
     *  số -97 đã là REQUEST_DYNAMIC_PART ở bản fan nên dùng -121]:
     *  server phát sub 0 QUESTION cho cả khu, client trả sub 1
     *  ANSWER {int id, byte chọn}; server trả sub 3 FEEDBACK riêng
     *  và sub 2 RESULT cho cả khu khi hết giờ. */
    public static final byte QUIZ = -121;
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
    /** Tab thông tin server gửi (Cmd.CUSTOM_TAB của gốc): S→C {byte 0,
     *  UTF tiêu đề, UTF nội dung} — dùng cho "Xem hướng dẫn" của các NPC. */
    public static final byte CUSTOM_TAB = -58;
    /** Server mở shop: S→C {byte idShop, UTF tên shop, short số món,
     *  các short idPart} — danh sách hàng do server quyết (Cmd.OPEN_SHOP
     *  của gốc, tới sau khi chọn mục shop trong menu NPC cửa hàng). */
    public static final byte OPEN_SHOP = -49;
    /** Vòng quay may mắn (DialLucky của gốc): C→S {short idPart đã chọn,
     *  short lực 0..180}; S→C phát cả khu {int idUser, short lực, byte số
     *  quà, mỗi quà: byte type (1 item {short idPart, byte ngày hạn, −1 =
     *  vĩnh viễn} · 2 {int xu} · 3 {int xp} · 4 {int lượng})}. Mở danh sách
     *  quà qua OPEN_SHOP idShop=100 từ menu NPC 185 (server trừ tiền theo
     *  loại quay đã chọn ở menu: 5 lượng / 25.000 xu / vé item 593). */
    public static final byte DIAL_LUCKY = -64;

    // ----- Câu cá (đợt mini-game, docs/minigame-spec.md §1; số lệnh
    // DƯƠNG của Cmd gốc trong ngữ cảnh park) -----
    /** Quăng câu (Cmd.QUANG_CAU): C→S rỗng; S→C phát cả khu {int id người quăng}. */
    public static final byte FISH_CAST = 82;
    /** Kết quả giật câu (Cmd.CAU_THANH_CONG): C→S {bool true, byte n,
     *  n byte phím đã bấm}; S→C phát cả khu {int id, short id cá (−1 = hụt)}. */
    public static final byte FISH_FINISH = 84;
    /** Xong lượt câu (Cmd.CAU_CA_XONG): C→S rỗng; S→C phát cả khu {int id}. */
    public static final byte FISH_DONE = 85;
    /** Bắt đầu câu (Cmd.START_CAU_CA): C→S rỗng; S→C {bool ok, UTF lỗi nếu !ok}. */
    public static final byte FISH_START = 86;
    /** Cá cắn câu (Cmd.CAN_CAU, chỉ gửi người câu): {int id, short id cá,
     *  short ms mỗi bước, byte n, n × {short len, PNG mũi tên}}. */
    public static final byte FISH_BITE = 91;

    /** Handler Avatar trong GET_HANDLER (CLIENT_TYPE = 8 của gốc). */
    public static final byte HANDLER_AVATAR = 8;
    /** Handler Farm trong GET_HANDLER (HANDLER_FARM = 10 của gốc). */
    public static final byte HANDLER_FARM = 10;
    /** Handler Nhà trong GET_HANDLER (HANDLER_HOME = 11 của gốc). */
    public static final byte HANDLER_HOME = 11;

    // ----- Hệ nhà (đợt 4, docs/house-spec.md) -----
    /** Vào nhà (gói −65): int id chủ nhà đích; server trả −65 cùng cmd
     *  chứa lưới nhà + nội thất đã bày. */
    public static final byte JOIN_HOUSE = -65;
    /** Hỏi cấp nhà (gói −67): byte 0 = nhà mình; server trả
     *  {byte 0, short verTile, byte houseType}. */
    public static final byte GET_TYPE_HOUSE = -67;
    /** Mua + bày nội thất (gói −74): short typeID, byte xCell, byte yCell,
     *  byte type (1 = Xu, 2 = Lượng). */
    public static final byte BUY_ITEM_HOUSE = -74;
    /** Di chuyển / xoay nội thất (gói −71): short anchor (= typeID),
     *  byte x, byte y (ô cũ), byte x2, byte y2 (ô mới), byte dir. */
    public static final byte SORT_ITEM_HOUSE = -71;
    /** Thu hồi nội thất (gói −66): short typeID, byte xCell, byte yCell,
     *  byte dir. Server xoá hẳn, không hoàn tiền. */
    public static final byte DEL_ITEM_HOUSE = -66;
    /** Nâng cấp rương nhà (gói −90): byte 0 = hỏi giá, 1 = xác nhận. */
    public static final byte UPGRADE_CHEST = -90;

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
    public static final byte FARM_QUICK_COOK = 93;

    /** Nấu nhanh (gói 93 bản chính): step 0 hỏi giá, step 1 trả. */
    public static void farmQuickCook(GameConnection conn, int step) throws IOException {
        Msg m = new Msg(FARM_QUICK_COOK);
        m.writer().writeByte(step);
        m.writer().flush();
        conn.send(m);
    }
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

    public static void titleList(GameConnection conn) throws IOException {
        Msg m = new Msg(TITLE);
        m.writer().writeByte(0);
        conn.send(m);
    }

    public static void titleSet(GameConnection conn, int titleId)
            throws IOException {
        Msg m = new Msg(TITLE);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(1);
        w.writeShort(titleId);
        conn.send(m);
    }

    public static void titleZoneReq(GameConnection conn) throws IOException {
        Msg m = new Msg(TITLE);
        m.writer().writeByte(5);
        conn.send(m);
    }

    public static void quizPing(GameConnection conn) throws IOException {
        Msg m = new Msg(QUIZ);
        m.writer().writeByte(9);
        conn.send(m);
    }

    public static void quizAnswer(GameConnection conn, int quizId, int choice)
            throws IOException {
        Msg m = new Msg(QUIZ);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(1);
        w.writeInt(quizId);
        w.writeByte(choice);
        conn.send(m);
    }

    public static void boxOpen(GameConnection conn) throws IOException {
        Msg m = new Msg(BOX);
        m.writer().writeByte(0);
        conn.send(m);
    }

    public static void boxUnlock(GameConnection conn, String pass) throws IOException {
        Msg m = new Msg(BOX);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(1);
        w.writeUTF(pass);
        conn.send(m);
    }

    public static void boxSetPass(GameConnection conn, String pass) throws IOException {
        Msg m = new Msg(BOX);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(2);
        w.writeUTF(pass);
        conn.send(m);
    }

    public static void boxChangePass(GameConnection conn, String oldPass, String newPass)
            throws IOException {
        Msg m = new Msg(BOX);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(3);
        w.writeUTF(oldPass);
        w.writeUTF(newPass);
        conn.send(m);
    }

    /** Chuyển đồ giỏ <-> rương phụ: dir 0 = giỏ -> rương, 1 = ngược lại. */
    public static void boxTrans(GameConnection conn, int dir, int partId) throws IOException {
        Msg m = new Msg(BOX);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(4);
        w.writeByte(dir);
        w.writeShort(partId);
        conn.send(m);
    }

    public static void boxUpgrade(GameConnection conn, int type) throws IOException {
        Msg m = new Msg(BOX);
        java.io.DataOutputStream w = m.writer();
        w.writeByte(5);
        w.writeByte(type);
        conn.send(m);
    }

    public static void splitItem(GameConnection conn, int partId, int qty)
            throws IOException {
        Msg m = new Msg(SPLIT_ITEM);
        java.io.DataOutputStream w = m.writer();
        w.writeShort(partId);
        w.writeShort(qty);
        conn.send(m);
    }

    /** Thử đồ / cởi đồ thử (gói −100): {short partId} (0 = cởi). */
    public static void applyPreview(GameConnection conn, int partId) throws IOException {
        Msg m = new Msg(PREVIEW_ITEM);
        m.writer().writeShort(partId);
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

    /** Đổi handler phiên (gói −1): 8 Avatar / 9 Park / 10 Farm / 11 Home. */
    public static void setHandler(GameConnection conn, int handler) throws IOException {
        Msg m = new Msg(GET_HANDLER);
        m.writer().writeByte(handler);
        conn.send(m);
    }

    /** Hỏi cấp nhà của mình (gói −67 {byte 0}). */
    public static void getTypeHouse(GameConnection conn) throws IOException {
        Msg m = new Msg(GET_TYPE_HOUSE);
        m.writer().writeByte(0);
        conn.send(m);
    }

    /** Vào nhà của chủ nhà id (gói −65 {int idUser}). */
    public static void joinHouse(GameConnection conn, int ownerId) throws IOException {
        Msg m = new Msg(JOIN_HOUSE);
        m.writer().writeInt(ownerId);
        conn.send(m);
    }

    /** Mua + bày một món nội thất (gói −74). */
    public static void buyItemHouse(GameConnection conn, int typeId, int x, int y, int moneyType)
            throws IOException {
        Msg m = new Msg(BUY_ITEM_HOUSE);
        DataOutputStream w = m.writer();
        w.writeShort(typeId);
        w.writeByte(x);
        w.writeByte(y);
        w.writeByte(moneyType);
        conn.send(m);
    }

    /** Di chuyển / xoay một món nội thất (gói −71). */
    public static void sortItemHouse(GameConnection conn, int typeId,
            int x, int y, int x2, int y2, int dir) throws IOException {
        Msg m = new Msg(SORT_ITEM_HOUSE);
        DataOutputStream w = m.writer();
        w.writeShort(typeId);
        w.writeByte(x);
        w.writeByte(y);
        w.writeByte(x2);
        w.writeByte(y2);
        w.writeByte(dir);
        conn.send(m);
    }

    /** Thu hồi một món nội thất (gói −66). */
    public static void delItemHouse(GameConnection conn, int typeId, int x, int y, int dir)
            throws IOException {
        Msg m = new Msg(DEL_ITEM_HOUSE);
        DataOutputStream w = m.writer();
        w.writeShort(typeId);
        w.writeByte(x);
        w.writeByte(y);
        w.writeByte(dir);
        conn.send(m);
    }

    /** Nâng cấp rương nhà (gói −90): 0 = hỏi giá, 1 = xác nhận nâng. */
    public static void upgradeChest(GameConnection conn, int step) throws IOException {
        Msg m = new Msg(UPGRADE_CHEST);
        m.writer().writeByte(step);
        conn.send(m);
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

    /** Bắt đầu câu cá (gói 86, rỗng): server kiểm cần câu/vé rồi trả
     *  86 {bool ok, UTF lỗi}. */
    public static void fishStart(GameConnection conn) throws IOException {
        conn.send(new Msg(FISH_START));
    }

    /** Quăng câu (gói 82, rỗng): server trừ 1 mồi, phát 82 cả khu và
     *  hẹn giờ cá cắn (trả 91 cho người câu). */
    public static void fishCast(GameConnection conn) throws IOException {
        conn.send(new Msg(FISH_CAST));
    }

    /** Gửi dãy phím QTE khi cá cắn (gói 84): {bool true, byte n,
     *  n byte phím} — phím theo HDPaint: phải 3, lên 2, trái 1, xuống 4;
     *  0 = bước quá giờ/không bấm (server chấm hụt). */
    public static void fishFinish(GameConnection conn, byte[] pads) throws IOException {
        Msg m = new Msg(FISH_FINISH);
        DataOutputStream w = m.writer();
        w.writeBoolean(true);
        w.writeByte(pads.length);
        for (byte p : pads) w.writeByte(p);
        conn.send(m);
    }

    /** Xong lượt câu (gói 85, rỗng): dọn cá/phao phía mọi người. */
    public static void fishDone(GameConnection conn) throws IOException {
        conn.send(new Msg(FISH_DONE));
    }

    // ===== Clan đầy đủ (bản chính 3.4.4; docs/clan-spec.md) =====
    // ---- FARM CẤP 2 + ONG ([PORT]: gói −115, docs/farmlvl2-spec.md) ----
    public static final byte FARM2 = -115;
    public static final byte F2_INFO = 0, F2_UPGRADE = 1, F2_RESULT = 2,
        F2_BEE_BUY = 3, F2_BEE_COLLECT = 4, F2_BEE_UPGRADE = 5;

    public static Msg farm2Msg(byte sub) throws IOException {
        Msg m = new Msg(FARM2); m.writer().writeByte(sub); return m;
    }
    public static void farm2Info(GameConnection conn) throws IOException { conn.send(farm2Msg(F2_INFO)); }
    public static void farm2Upgrade(GameConnection conn, int idx) throws IOException {
        Msg m = farm2Msg(F2_UPGRADE); m.writer().writeByte(idx); conn.send(m);
    }
    public static void farm2BeeBuy(GameConnection conn) throws IOException { conn.send(farm2Msg(F2_BEE_BUY)); }
    public static void farm2BeeCollect(GameConnection conn) throws IOException { conn.send(farm2Msg(F2_BEE_COLLECT)); }
    public static void farm2BeeUpgrade(GameConnection conn) throws IOException { conn.send(farm2Msg(F2_BEE_UPGRADE)); }

    // ---- ĐIỂM DANH + NHIỆM VỤ NGÀY ([PORT]: gói −116, docs/daily-auction-spec.md) ----
    // [PORT] Bảng xếp hạng cặp đôi (ListScr.readCoupleBoard bản chính)
    public static final byte COUPLE = -122;

    public static final byte DAILY = -116;
    public static final byte D_DASHBOARD = 0, D_BAODANH = 1, D_MILESTONE = 2,
        D_GUIDE = 3, D_RESULT = 4, D_QSHOW = 10, D_QCHANGE = 11, D_QDONE = 12, D_QBOX = 13;

    public static Msg dailyMsg(byte sub) throws IOException {
        Msg m = new Msg(DAILY); m.writer().writeByte(sub); return m;
    }
    public static void dailyDashboard(GameConnection conn) throws IOException { conn.send(dailyMsg(D_DASHBOARD)); }

    // [PORT] Bảng xếp hạng tổng hợp (mọi thể loại, một panel riêng)
    public static final byte RANK = -123;

    public static void rankBoard(GameConnection conn, int cat) throws IOException {
        Msg m = new Msg(RANK);
        m.writer().writeByte(cat);
        m.writer().flush();
        conn.send(m);
    }

    public static void coupleBoard(GameConnection conn) throws IOException {
        Msg m = new Msg(COUPLE);
        m.writer().writeByte(0);
        m.writer().flush();
        conn.send(m);
    }

    // [PORT] Bảng Tài Xỉu (phiên/lịch sử/top — cược vẫn qua menu NPC 864)
    public static final byte TAIXIU = -124;

    // ---- ĐƠN HÀNG ỦY THÁC LÁI BUÔN ([PORT]: gói −125, docs/order-spec.md) ----
    public static final byte ORDER = -125;
    public static final byte O_OPEN = 0, O_DELIVER = 1, O_TRASH = 2,
        O_STATE = 10, O_RESULT = 11;

    public static Msg orderMsg(byte sub) throws IOException {
        Msg m = new Msg(ORDER); m.writer().writeByte(sub); return m;
    }
    public static void orderOpen(GameConnection conn) throws IOException {
        conn.send(orderMsg(O_OPEN));
    }
    public static void orderDeliver(GameConnection conn, int slot) throws IOException {
        Msg m = orderMsg(O_DELIVER); m.writer().writeByte(slot); m.writer().flush(); conn.send(m);
    }
    public static void orderTrash(GameConnection conn, int slot) throws IOException {
        Msg m = orderMsg(O_TRASH); m.writer().writeByte(slot); m.writer().flush(); conn.send(m);
    }

    public static void taixiuBoard(GameConnection conn) throws IOException {
        Msg m = new Msg(TAIXIU);
        m.writer().writeByte(0);
        m.writer().flush();
        conn.send(m);
    }
    public static void dailyBaoDanh(GameConnection conn) throws IOException { conn.send(dailyMsg(D_BAODANH)); }
    public static void dailyMilestone(GameConnection conn, int idx) throws IOException {
        Msg m = dailyMsg(D_MILESTONE); m.writer().writeByte(idx); conn.send(m);
    }
    public static void dailyQuestShow(GameConnection conn) throws IOException { conn.send(dailyMsg(D_QSHOW)); }
    public static void dailyQuestChange(GameConnection conn) throws IOException { conn.send(dailyMsg(D_QCHANGE)); }
    public static void dailyQuestDone(GameConnection conn) throws IOException { conn.send(dailyMsg(D_QDONE)); }
    public static void dailyQuestBox(GameConnection conn, int idx) throws IOException {
        Msg m = dailyMsg(D_QBOX); m.writer().writeByte(idx); conn.send(m);
    }

    // ---- ĐẤU GIÁ NGƯỢC ([PORT]: gói −117, docs/daily-auction-spec.md) ----
    public static final byte AUCTION = -117;
    public static final byte A_INFO = 0, A_BID = 1, A_RESULT = 2;

    public static Msg auctionMsg(byte sub) throws IOException {
        Msg m = new Msg(AUCTION); m.writer().writeByte(sub); return m;
    }
    public static void auctionInfo(GameConnection conn) throws IOException { conn.send(auctionMsg(A_INFO)); }
    public static void auctionBid(GameConnection conn) throws IOException { conn.send(auctionMsg(A_BID)); }

    // ---- CHỢ + GIAO DỊCH (bản chính: gói 127 MARKET) ----
    public static final byte MARKET = 127;
    public static final byte MK_LIST = 0, MK_SELL = 1, MK_BUY = 2, MK_CANCEL = 3,
        MK_MY = 4, MK_RESULT = 5, MK_CHEST = 6;
    public static final byte TR_INVITE = 10, TR_INVITE_IN = 11, TR_REPLY = 12,
        TR_OPEN = 13, TR_SET = 14, TR_PARTNER_SET = 15, TR_LOCK = 16,
        TR_LOCK_STATE = 17, TR_ACCEPT = 18, TR_DONE = 19, TR_CANCEL = 20,
        TR_CHAT = 21;

    public static Msg marketMsg(byte sub) throws IOException {
        Msg m = new Msg(MARKET); m.writer().writeByte(sub); return m;
    }

    public static void marketList(GameConnection conn, int page) throws IOException {
        Msg m = marketMsg(MK_LIST); m.writer().writeByte(page); conn.send(m);
    }
    public static void marketSell(GameConnection conn, int partId, int qty, int price) throws IOException {
        Msg m = marketMsg(MK_SELL);
        m.writer().writeShort(partId); m.writer().writeInt(qty); m.writer().writeInt(price);
        conn.send(m);
    }
    public static void marketBuy(GameConnection conn, int listingId) throws IOException {
        Msg m = marketMsg(MK_BUY); m.writer().writeInt(listingId); conn.send(m);
    }
    public static void marketCancel(GameConnection conn, int listingId) throws IOException {
        Msg m = marketMsg(MK_CANCEL); m.writer().writeInt(listingId); conn.send(m);
    }
    public static void marketMy(GameConnection conn) throws IOException { conn.send(marketMsg(MK_MY)); }
    public static void marketChest(GameConnection conn) throws IOException { conn.send(marketMsg(MK_CHEST)); }
    public static void tradeInvite(GameConnection conn, int targetId) throws IOException {
        Msg m = marketMsg(TR_INVITE); m.writer().writeInt(targetId); conn.send(m);
    }
    public static void tradeReply(GameConnection conn, int fromId, boolean ok) throws IOException {
        Msg m = marketMsg(TR_REPLY); m.writer().writeInt(fromId); m.writer().writeBoolean(ok); conn.send(m);
    }
    public static void tradeSet(GameConnection conn, java.util.List<int[]> items,
                                java.util.List<Long> exps, int xu) throws IOException {
        Msg m = marketMsg(TR_SET);
        m.writer().writeByte(items.size());
        for (int i = 0; i < items.size(); i++) {
            m.writer().writeShort(items.get(i)[0]);
            m.writer().writeInt(items.get(i)[1]);
            m.writer().writeLong(exps.get(i));
        }
        m.writer().writeInt(xu);
        conn.send(m);
    }
    public static void tradeLock(GameConnection conn, boolean lock) throws IOException {
        Msg m = marketMsg(TR_LOCK); m.writer().writeBoolean(lock); conn.send(m);
    }
    public static void tradeAccept(GameConnection conn) throws IOException { conn.send(marketMsg(TR_ACCEPT)); }
    public static void tradeCancel(GameConnection conn) throws IOException { conn.send(marketMsg(TR_CANCEL)); }
    public static void tradeChat(GameConnection conn, String text) throws IOException {
        Msg m = marketMsg(TR_CHAT); m.writer().writeUTF(text); conn.send(m);
    }

    // Phong bì CLAN: {byte sub, ...}. Số lệnh chính −112 đúng bản chính;
    // lệnh con theo thứ tự khai báo Cmd_Message của bản chính.
    public static final byte CLAN = -112;
    public static final int CL_REGIS = 0, CL_INFO = 1, CL_LIST = 2,
        CL_REQUEST_JOIN = 3, CL_LIST_REQUEST = 4, CL_ACCEPT = 5,
        CL_DENY = 6, CL_OUT = 7, CL_KICK = 8, CL_CHAT = 9,
        CL_BLOCK_CHAT = 10, CL_DONATION = 11, CL_CHECK_INFO = 12,
        CL_CHECK_CREATE = 13, CL_NOTIFY = 14, CL_LEVEL_INFO = 15,
        CL_LEVEL_UP = 16, CL_SET_DEPUTY = 17, CL_FIRE_DEPUTY = 18,
        CL_LIST_QUEST = 20, CL_INFO_QUEST = 21, CL_SUBMIT_QUEST = 22,
        CL_SUBMIT_ITEM = 23, CL_QUEST_DONE = 24, CL_LIST_NEW = 25,
        CL_SET_PLAYER = 26, CL_REQ_ITEMS = 27, CL_ITEM_OWN = 28,
        CL_BUY_ITEM = 29, CL_RECEIVE = 30, CL_INVITE = 31,
        CL_CHANGE_INFO = 32;

    public static Msg clanMsg(int sub) throws IOException {
        Msg m = new Msg(CLAN);
        m.writer().writeByte(sub);
        return m;
    }

    public static void clanInfo(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_INFO));
    }

    public static void clanList(GameConnection conn, boolean newest) throws IOException {
        if (newest) {
            conn.send(clanMsg(CL_LIST_NEW));
        } else {
            Msg m = clanMsg(CL_LIST);
            m.writer().writeByte(0);
            conn.send(m);
        }
    }

    public static void clanCreate(GameConnection conn, String name, String slogan, int icon)
            throws IOException {
        Msg m = clanMsg(CL_REGIS);
        DataOutputStream w = m.writer();
        w.writeUTF(name);
        w.writeUTF(slogan);
        w.writeShort(icon);
        conn.send(m);
    }

    public static void clanRequestJoin(GameConnection conn, int clanId, boolean viaInvite)
            throws IOException {
        Msg m = clanMsg(CL_REQUEST_JOIN);
        DataOutputStream w = m.writer();
        w.writeInt(clanId);
        w.writeByte(viaInvite ? 1 : 0);
        conn.send(m);
    }

    public static void clanListRequest(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_LIST_REQUEST));
    }

    public static void clanAccept(GameConnection conn, int userId, boolean ok) throws IOException {
        Msg m = clanMsg(ok ? CL_ACCEPT : CL_DENY);
        m.writer().writeInt(userId);
        conn.send(m);
    }

    public static void clanOut(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_OUT));
    }

    public static void clanKick(GameConnection conn, int userId) throws IOException {
        Msg m = clanMsg(CL_KICK);
        m.writer().writeInt(userId);
        conn.send(m);
    }

    /** Gói BOSS_SHOP của gốc (Cmd -78): shop sự kiện (Vô Thường, Pay /
     *  Chay To Win, shop lượng của 861) + nâng cấp Thợ Kim Hoàn. Chiều
     *  client gửi: {int idBoss, byte idShop, short index} = chọn món. */
    public static final byte BOSS_SHOP = -78;

    public static void bossShop(GameConnection conn, int idBoss, int idShop, int index)
            throws IOException {
        Msg m = new Msg(BOSS_SHOP);
        java.io.DataOutputStream w = m.writer();
        w.writeInt(idBoss);
        w.writeByte(idShop);
        w.writeShort(index);
        conn.send(m);
    }

    public static void clanChat(GameConnection conn, String text) throws IOException {
        Msg m = clanMsg(CL_CHAT);
        m.writer().writeUTF(text);
        conn.send(m);
    }

    public static void clanBlockChat(GameConnection conn, int userId, boolean block)
            throws IOException {
        Msg m = clanMsg(CL_BLOCK_CHAT);
        DataOutputStream w = m.writer();
        w.writeInt(userId);
        w.writeBoolean(block);
        conn.send(m);
    }

    public static void clanDonate(GameConnection conn, int xu, int luong) throws IOException {
        Msg m = clanMsg(CL_DONATION);
        DataOutputStream w = m.writer();
        w.writeInt(xu);
        w.writeInt(luong);
        conn.send(m);
    }

    /** Đổi thông tin clan (CHANGE_CLAN của bản chính, sub 32 [PORT]). */
    public static void clanChangeInfo(GameConnection conn, String name,
            String slogan, int icon) throws IOException {
        Msg m = clanMsg(CL_CHANGE_INFO);
        java.io.DataOutputStream w = m.writer();
        w.writeUTF(name);
        w.writeUTF(slogan);
        w.writeShort(icon);
        w.flush();
        conn.send(m);
    }

    public static void clanCheckInfo(GameConnection conn, int clanId) throws IOException {
        Msg m = clanMsg(CL_CHECK_INFO);
        m.writer().writeInt(clanId);
        conn.send(m);
    }

    public static void clanLevelInfo(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_LEVEL_INFO));
    }

    public static void clanLevelUp(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_LEVEL_UP));
    }

    public static void clanDeputy(GameConnection conn, int userId, boolean set) throws IOException {
        Msg m = clanMsg(set ? CL_SET_DEPUTY : CL_FIRE_DEPUTY);
        m.writer().writeInt(userId);
        conn.send(m);
    }

    public static void clanQuests(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_LIST_QUEST));
    }

    public static void clanSubmitQuest(GameConnection conn, int questId) throws IOException {
        Msg m = clanMsg(CL_SUBMIT_QUEST);
        m.writer().writeByte(questId);
        conn.send(m);
    }

    public static void clanSubmitItem(GameConnection conn, int questId) throws IOException {
        Msg m = clanMsg(CL_SUBMIT_ITEM);
        m.writer().writeByte(questId);
        conn.send(m);
    }

    public static void clanShop(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_REQ_ITEMS));
    }

    public static void clanOwn(GameConnection conn) throws IOException {
        conn.send(clanMsg(CL_ITEM_OWN));
    }

    public static void clanBuyItem(GameConnection conn, int partId) throws IOException {
        Msg m = clanMsg(CL_BUY_ITEM);
        m.writer().writeShort(partId);
        conn.send(m);
    }

    public static void clanReceive(GameConnection conn, int ownId) throws IOException {
        Msg m = clanMsg(CL_RECEIVE);
        m.writer().writeInt(ownId);
        conn.send(m);
    }

    public static void clanInvite(GameConnection conn, String playerName) throws IOException {
        Msg m = clanMsg(CL_INVITE);
        m.writer().writeUTF(playerName);
        conn.send(m);
    }

    // ===== Hub MiniGame + Tàu Chiến (bản chính 3.4.4; docs/tauchien-hub-spec.md) =====
    // Phong bì một gói MINIGAME_HUB: {byte gameId, byte sub, ...}.
    // gameId 0 = hub (danh sách bàn/tạo/vào/mời/khán giả), 4 = Tàu Chiến.
    // (Bản chính dùng handler MINIGAME = -90; server Pack5 đã dùng -90 cho
    // nâng cấp rương nhà nên bản port dùng -113 — xem Cmd phía server.)
    public static final byte MINIGAME_HUB = -113;
    public static final int MG_GAME_TAUCHIEN = 4;
    public static final int MG_GAME_CARO = 3;
    public static final int MG_GAME_AINCRAD = 1;
    public static final int MG_GAME_TRUCXANH = 2;
    public static final int MG_GAME_SNAKE = 5;
    // Aincrad subs [PORT theo tên lệnh bản chính: START/PLAYER_MOVE/
    // PLAYER_SHOW/PLAYER_REMOVE_GHOST]
    public static final int AI_START = 1, AI_PICK = 2, AI_MOVE = 3,
        AI_SHOW = 4, AI_GHOST_OFF = 5, AI_END = 6, AI_POS = 7,
        AI_SNAPSHOT = 13, AI_SPEC_COUNT = 14;
    // Cờ Caro subs [PORT theo cấu trúc bản chính]
    public static final int CR_START = 1, CR_MOVE = 2, CR_MOVED = 3,
        CR_CHANGE_TURN = 4, CR_END = 5, CR_SNAPSHOT = 13, CR_SPEC_COUNT = 14;
    // Rắn Thang (gameId 5) [PORT]
    public static final int SN_START = 1, SN_ROLL = 2, SN_ROLLED = 3,
        SN_CHANGE_TURN = 4, SN_END = 5, SN_SNAPSHOT = 13, SN_SPEC_COUNT = 14;
    // Trúc Xanh subs [PORT theo cấu trúc bản chính]
    public static final int TX_DEAL = 1, TX_FLIP = 2, TX_FLIP_RESULT = 3,
        TX_MATCH = 4, TX_MISS = 5, TX_CHANGE_TURN = 6, TX_END = 7,
        TX_SNAPSHOT = 13, TX_SPEC_COUNT = 14;
    // Hub subs (C→S)
    public static final int MG_LIST = 0, MG_CREATE = 1, MG_JOIN = 2,
        MG_JOIN_CODE = 3, MG_INVITE = 4, MG_INVITE_REPLY = 5, MG_LEAVE = 6,
        MG_WATCH = 7, MG_STOP_WATCH = 8, MG_REMATCH = 15;
    public static final int MG_TOP = 9;
    // Hub subs (S→C)
    public static final int MG_S_LIST = 0, MG_S_JOINED = 10, MG_S_OPP_JOINED = 11,
        MG_S_OPP_LEFT = 12, MG_S_REMATCH_OFFER = 16, MG_S_TOP = 19,
        MG_S_INVITE_IN = 20, MG_S_INVITE_RESULT = 21, MG_S_ERROR = 23;
    // Tàu Chiến subs (hai chiều, theo số lệnh bản chính)
    public static final int TC_START_PLACE = 1, TC_PLACE_SHIPS = 2,
        TC_PLACE_RESULT = 3, TC_READY = 4, TC_START_BATTLE = 6, TC_SHOOT = 7,
        TC_SHOT_RESULT = 8, TC_CHANGE_TURN = 9, TC_END = 10, TC_KICK = 12,
        TC_SNAPSHOT = 13, TC_SPEC_COUNT = 14;

    private static Msg mgMsg(int gameId, int sub) throws IOException {
        Msg m = new Msg(MINIGAME_HUB);
        DataOutputStream w = m.writer();
        w.writeByte(gameId);
        w.writeByte(sub);
        return m;
    }

    public static void mgList(GameConnection conn) throws IOException {
        conn.send(mgMsg(0, MG_LIST));
    }

    /** Xin bảng xếp hạng thắng nhiều nhất {byte gameId} (0 = tổng). */
    public static void mgTop(GameConnection conn, int gameId) throws IOException {
        Msg m = mgMsg(0, MG_TOP);
        m.writer().writeByte(gameId);
        conn.send(m);
    }

    public static void mgCreate(GameConnection conn, int gameId, boolean priv)
            throws IOException {
        Msg m = mgMsg(0, MG_CREATE);
        m.writer().writeByte(gameId);
        m.writer().writeBoolean(priv);
        conn.send(m);
    }

    public static void mgJoin(GameConnection conn, int roomId) throws IOException {
        Msg m = mgMsg(0, MG_JOIN);
        m.writer().writeInt(roomId);
        conn.send(m);
    }

    public static void mgJoinCode(GameConnection conn, String code) throws IOException {
        Msg m = mgMsg(0, MG_JOIN_CODE);
        m.writer().writeUTF(code);
        conn.send(m);
    }

    public static void mgInvite(GameConnection conn, int userId, int gameId)
            throws IOException {
        Msg m = mgMsg(0, MG_INVITE);
        m.writer().writeInt(userId);
        m.writer().writeByte(gameId);
        conn.send(m);
    }

    public static void mgInviteReply(GameConnection conn, int fromId, boolean ok)
            throws IOException {
        Msg m = mgMsg(0, MG_INVITE_REPLY);
        m.writer().writeInt(fromId);
        m.writer().writeBoolean(ok);
        conn.send(m);
    }

    public static void mgLeave(GameConnection conn) throws IOException {
        conn.send(mgMsg(0, MG_LEAVE));
    }

    public static void mgWatch(GameConnection conn, int roomId) throws IOException {
        Msg m = mgMsg(0, MG_WATCH);
        m.writer().writeInt(roomId);
        conn.send(m);
    }

    public static void mgStopWatch(GameConnection conn) throws IOException {
        conn.send(mgMsg(0, MG_STOP_WATCH));
    }

    public static void mgRematch(GameConnection conn) throws IOException {
        conn.send(mgMsg(0, MG_REMATCH));
    }

    /** Gửi đội hình Tàu Chiến: {byte n; n × (len, row, col, vert)}. */
    public static void tcPlaceShips(GameConnection conn, java.util.List<int[]> fleet)
            throws IOException {
        Msg m = mgMsg(MG_GAME_TAUCHIEN, TC_PLACE_SHIPS);
        DataOutputStream w = m.writer();
        w.writeByte(fleet.size());
        for (int[] s : fleet) {
            w.writeByte(s[0]);
            w.writeByte(s[1]);
            w.writeByte(s[2]);
            w.writeByte(s[3]);
        }
        conn.send(m);
    }

    public static void tcReady(GameConnection conn) throws IOException {
        conn.send(mgMsg(MG_GAME_TAUCHIEN, TC_READY));
    }

    public static void tcShoot(GameConnection conn, int row, int col) throws IOException {
        Msg m = mgMsg(MG_GAME_TAUCHIEN, TC_SHOOT);
        m.writer().writeByte(row);
        m.writer().writeByte(col);
        conn.send(m);
    }

    public static void tcKick(GameConnection conn) throws IOException {
        conn.send(mgMsg(MG_GAME_TAUCHIEN, TC_KICK));
    }

    /** Cờ Caro: đánh một ô {byte row, byte col} trên bàn 15x15. */
    public static void crMove(GameConnection conn, int row, int col)
            throws IOException {
        Msg m = mgMsg(MG_GAME_CARO, CR_MOVE);
        m.writer().writeByte(row);
        m.writer().writeByte(col);
        conn.send(m);
    }

    /** Rắn Thang: tung xúc xắc (không tham số, server quyết định). */
    public static void snRoll(GameConnection conn) throws IOException {
        conn.send(mgMsg(MG_GAME_SNAKE, SN_ROLL));
    }

    /** Trúc Xanh: lật thẻ {byte idx} (0..29). */
    public static void txFlip(GameConnection conn, int idx) throws IOException {
        Msg m = mgMsg(MG_GAME_TRUCXANH, TX_FLIP);
        m.writer().writeByte(idx);
        conn.send(m);
    }

    /** Aincrad: mở cửa {byte door} (0..2) ở tầng hiện tại. */
    public static void mgAiPick(GameConnection conn, int door) throws IOException {
        Msg m = mgMsg(MG_GAME_AINCRAD, AI_PICK);
        m.writer().writeByte(door);
        conn.send(m);
    }

    /** Aincrad: báo vị trí ngang khi đi bộ {short x}. */
    public static void mgAiPos(GameConnection conn, int x) throws IOException {
        Msg m = mgMsg(MG_GAME_AINCRAD, AI_POS);
        m.writer().writeShort(x);
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
