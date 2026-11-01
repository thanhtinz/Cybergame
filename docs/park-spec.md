# Park spec — gói trong map (đợt 2d)

Đo trực tiếp từ server Java trong project (`server/src/main/java/avatar/...`),
đối chiếu client C# gốc trong Pack5. Hệ toạ độ: **medium** của gốc (client vẽ ×2).
**Map id gửi lên server = client map id − 1** (xem maps-spec.md mục 0).

## Client → Server

| Cmd | Tên | Thân gói |
|-----|-----|----------|
| 50 | AVATAR_JOIN_PARK | byte mapId(server), byte area (−1 = tự chọn khu), short x, short y → `ServerManager.joinAreaMessage` |
| 54 | MOVE_PARK | short x, short y, byte direct → `User.move` (đọc thêm short tuỳ chọn nếu còn) |
| 55 | CHAT_PARK | UTF text → `User.chat` |

`direct` theo `Base.cs` của client gốc: RIGHT = 0, LEFT = 2 (mặc định LEFT).

## Server → Client (MapService của zone phát cho MỌI người trong zone, kể cả người gửi)

| Cmd | Tên | Thân gói |
|-----|-----|----------|
| 50 | AVATAR_JOIN_PARK (trả cho người vừa vào; danh sách KHÔNG gồm chính mình — server gửi trước khi `add`) | byte mapId, byte zoneId, short −1, short −1; byte numUser; mỗi user: int id, UTF name, byte nWearing, short[n] wearing, short x, short y, byte role (0 = NPC); rồi mỗi user: byte direct; mỗi user: byte 101; mỗi user: short −1; byte 0, byte 0; short nMapItems, byte nTypes, mỗi type: byte id, short imgID, byte iconID, short dx, short dy, byte nPos, mỗi pos: byte x, byte y; byte nMapItems2, mỗi item: byte type, byte typeID, byte x, byte y; mỗi user: short −1 |
| 51 | PLAYER_JOIN_PARK (phát cho người đã ở trong zone khi có người mới vào) | int id, UTF name, byte nWearing, short[n], short x, short y, byte role, byte −1, short −1, short −1 |
| 53 | PLAYER_LEAVE_PARK | int id |
| 54 | MOVE_PARK | int id, short x, short y, byte direct |
| 55 / 77 | CHAT_PARK / CHAT_FARM (77 khi server map = 22) | int id, UTF text |

Ghi chú:
- Handler cơ sở `MessageHandler` đã route sẵn 50/54/55 (không cần đổi handler).
- Server NPC cũng nằm trong danh sách zone (role 0) — client chỉ dựng người
  chơi thật (role ≠ 0) từ gói mạng; NPC tĩnh vẫn vẽ theo maps.json như đợt 1.
- Người chơi nhận echo 54/55 của chính mình: client bỏ qua gói mang id của mình
  (di chuyển lấy nội bộ làm chuẩn; chat hiển thị qua echo như client gốc).

## KHU (zone) — mỗi map 10 khu chạy song song

- Server tạo đúng **10 zone (khu 0–9) cho mỗi map** (`MapManager.add(new Map(i, 0, 10))`).
  Khu là "channel" của map: cùng một map, người ở khác khu không thấy nhau.
- Vào khu: gói 50 với byte khu = 0–9 để vào thẳng, hoặc −1 để server tự
  chọn (`joinAreaAutoNumber`: khu đầu tiên có ≤ 10 người). Header gói 50
  trả về chứa **zoneId thật** — client lấy đó làm khu hiện tại.
- Chuyển khu = gửi lại gói 50 cùng map, khu khác, giữ x/y (`ParkListSrc.doSelect`
  của gốc gọi `doJoinPark(roomID, selected)`).
- Danh sách khu: C→S gói **60** (byte mapId server) → S→C gói 60: byte số
  khu (10), rồi mỗi khu 1 byte trạng thái do `Session.doiKhuVuc` tính theo
  số người trong zone (gồm cả NPC phía server): **0 = đầy (≥9), 1 = đông
  (≥4), 2 = vắng (<4)**. Khu đầy thì client gốc không cho chọn.
- Bảng KHU (vật thể **846**) hiện **số khu hiện tại** ngay trên mặt bảng:
  `ImageObj.paint` của gốc vẽ `MapScr.boardID` tại tâm bảng. Ô của bảng
  (mã 98) có type **29** — gốc "dẫm lên" ô này là gửi gói 60 mở danh sách;
  bản port mở panel khi va vào ô type 29 hoặc bấm Tương tác khi đứng gần bảng.
- Kiểm chứng sống (core/src/test/python/live_khu_test.py, 2026-10-31):
  A join −1 → khu 0; B join khu 3 → đúng khu 3; gói 60 → 10 khu, khu 0
  trạng thái 1 (NPC server nằm ở khu 0), khu 3 trạng thái 2; B chuyển
  khu 5 → header gói 50 xác nhận khu 5. PASS.

## Biểu cảm mặt — gói 57 (AVATAR_FEEL)

- C→S gói **57**: đúng **1 byte** — chọn mặt thì gửi `partId mặt + 100`
  (`MapScr.doSellectFeel` của gốc: setFeel(focus) ở máy mình +
  `AvatarService.doFeel(focus + 100)`); byte < 100 là **hành động**
  (doAction — ngồi/nhảy…, bản port chưa làm).
- Server: `User.doAvatarFeel` đọc byte rồi `MapService.doAvatarFeel`
  phát cho **cả zone (kể cả người gửi)** gói 57: **int userId, byte feel**.
- Phía nhận của gốc (`MapScr.onFeel`): byte ≥ 100 → `setFeel(byte − 100)`
  (feel là **partId của part mặt zOrder 40**, Avatar.paint lấy
  `AvatarData.getPart(feel)` thay mặt mặc định 4); byte < 100 → doAction.
- Giới hạn dây: 1 byte có dấu chỉ chứa nổi partId ≤ 27 theo cách đọc của
  gốc. Bản port đọc byte **không dấu** (0–255) nên mã hoá được partId
  ≤ 155 — byte gửi cho partId ≤ 27 y hệt gốc. Mặt có partId > 155
  (19/45 mặt trong sheet: 305–606) **không gửi được**, chỉ hiện ở máy mình.
- Vào sau/không ở trong zone lúc đổi mặt thì không thấy mặt mới (server
  không lưu feel; wearing trong gói 50/51 vẫn là mặt mặc định) — đúng gốc.
- Kiểm chứng sống (core/src/test/python/live_feel_test.py, 2026-10-31):
  A (id 1000) gửi byte 107 → B nhận {1000, 107}; A gửi byte 140 (mặt 40)
  → B nhận {1000, 140}. PASS. Ảnh render: shot-feel-sync.png (Linh nhận
  mặt 107 — mắt nhắm tịt khác hẳn mặt thường trong shot-online.png).

## Tin nhắn thoại — gói 61 (MỞ RỘNG của bản port, game gốc không có)

Theo yêu cầu của chủ dự án (2026-10-31): hình thức **tin nhắn thoại**
(bấm giữ micro để ghi đoạn ngắn, gửi đi, người nhận bấm vào để nghe) —
không phải voice trực tiếp. Cmd **61** còn trống trong cả hai handler
park của server (MessageHandler + ParkMsgHandler) nên dùng cho gói này.

- Âm thanh: PCM **16 kHz mono**, nén **μ-law G.711** (1 byte/mẫu =
  16 KB/s), tối đa **30 giây**/tin. Codec phía client:
  `net/VoiceCodec.java` (bản độc lập của port).
- C→S gói **61**: **int clipId, short seq, short total, byte[] audio**
  (mỗi mẩu ≤ 4000 byte audio). Server (`User.doVoicePark`) chỉ kiểm tra
  độ dài (9..8200 byte) rồi chuyển NGUYÊN VĂN cho
  `MapService.doVoicePark` phát cả zone (kể cả người gửi — echo như chat).
  S→C gói **61**: **int senderId** + nguyên văn thân gói của người gửi.
  Server KHÔNG giải mã âm thanh.
- Phía nhận: ráp các mẩu theo (senderId, clipId), đủ `total` mẩu thì
  giải mã μ-law → PCM, hiện **chip nghe lại** trong tab Gần đây
  ("Tin nhắn thoại 0:05", icon play/stop Font Awesome), chạm chip để
  nghe/dừng; bong bóng trên đầu người gửi hiện "Tin nhắn thoại 0:05".
- LƯU Ý khi đọc gói đến phía server: `Message.getData()` chỉ dùng cho
  gói ĐI (gói đến dựng bằng constructor reader, `os == null`, getData trả
  rỗng) — handler phải đọc thân gói từ `reader()`.
- Chat mang **id ≥ 100.000.000** là tin HỆ THỐNG của server phát qua
  định dạng chat park (vd id 2000000864: thông báo phiên/đếm ngược mỗi
  giây) — client đưa vào tab Hệ thống, không hiện tên "Người chơi".
- Kiểm chứng sống (core/src/test/python/live_voice_test.py, 2026-10-31):
  A gửi clip 777 gồm 3 mẩu (4000+4000+2000 byte) → B nhận đủ 3 gói 61
  {idA, 777, seq, total, data} và ráp đúng nguyên văn 10000 byte; A cũng
  nhận echo đủ. PASS. Unit test: VoiceTest (μ-law roundtrip + parse gói).
  Ảnh render: shot-chat-voice.png. Chưa kiểm chứng được trong sandbox:
  ghi âm bằng micro thật và phát ra loa (VM không có thiết bị âm thanh;
  client Java cũng không nối TCP ra ngoài được trong sandbox) — code có
  bắt lỗi và báo vào tab Hệ thống khi thiết bị không ghi/phát được.

## Tương tác người chơi khác (panel kính, 2026-10-31)

Chạm vào người chơi khác → panel kính (mẫu panel Avatar bản mới): avatar +
tên + 5 nút **Nhắn tin / Kết bạn / Tặng quà / Tương tác / Khác**; Tương tác
mở hàng con **Đánh / Hôn / Mời về nhà / Thông tin**. Giao thức đúng client
gốc (MainMenu.doExchange + ParkService/GlobalService của C#):

- **Nhắn riêng — gói −6 (CHAT_TO)**: C→S {int id nhận, UTF}. S→C
  {int type, UTF tên người gửi, UTF}. Server `AvatarService.chatToUser`
  chuyển thẳng cho người nhận (không echo); Pack5 lấy nhầm tên người nhận
  làm tên người gửi — bản port đã sửa dùng `session.user`.
- **Kết bạn — gói −21 / −19**: C→S −21 {int id nhận} xin mời; server
  `ParkService.handleAddFriendRequest` chuyển cho người nhận gói **−19
  {int id NGƯỜI MỜI, boolean false}** (Pack5 ghi nhầm id người nhận — đã
  sửa). Người nhận trả lời C→S −19 {int id người mời, boolean đồng ý};
  handler mới `handleAddFriendReply` chuyển kết quả về người mời bằng −19
  {int id người trả lời, boolean}. Client phân biệt "lời mời đến" vs "kết
  quả" bằng tập id mình đang chờ (friendPending). Lưu danh sách bạn thuộc
  hệ bạn bè (đợt sau) — hiện chỉ chuyển tiếp thông báo.
- **Thông tin — gói −22 (REQUEST_YOUR_INFO)**: C→S {int id}. S→C {int id,
  byte cấp, byte %cấp, byte thân thiện, byte gây rối (crazy), byte sành
  điệu, byte vui vẻ, byte sức khỏe (100 − hunger), int id người yêu
  (−1 = không có); nếu có: UTF tên, byte số part + short partIds (doll
  người yêu), UTF slogan, short idImage, byte cấp, byte %cấp, UTF tên
  quan hệ, short idAction (−1 = không có; có thì thêm UTF tên hành động);
  cuối gói có thể còn short cấp của chính mình}. Đích khác mình → thẻ
  kính trên panel người chơi (cấp + 5 thanh). Đích là mình → **panel
  nhân vật** (bấm card góc trái trên): cột tiền icon myinfo gốc (Xu,
  Lượng, Lượng khoá, Xèng, Cấp+%), doll đứng lớn + người yêu đứng cạnh
  nếu có, 6 thanh chỉ số như CommandInfo của gốc (myIndex: Lv, T.Thiện,
  G.Rối, S.Điệu, V.Vẻ, S.Khỏe). PlayerInfo.parse cũng đã giữ 5 chỉ số
  trong gói đăng nhập làm số dự phòng khi chưa có −22.
- **Đánh/Hôn — gói 59 (Cmd.ACTION)**: C→S {int id đích, short mã}
  (100 = đánh, 101 = hôn). Server gốc CÓ sẵn đường này ở MessageHandler:
  `User.doAction` → `MapService.doAction` phát cả zone (kể cả người gửi)
  S→C {int nguồn, int đích, short mã, short time=10} (mã −1 thì thay
  time bằng UTF text) đúng format `MapScr.onGivingDefferent` của gốc.
  Client vẽ hiệu ứng tại chỗ nhận gói: hôn = tim hồng bay lên trên đầu
  người bị hôn, đánh = chùm sao vàng.
- **LƯU Ý HANDLER (đã kiểm chứng bằng log + test sống)**: phiên chơi park
  sống ở **MessageHandler** từ đầu tới cuối — client gốc không hề đổi
  handler khi vào park (GET_HANDLER 9 → ParkMsgHandler chỉ là chế độ
  riêng). Mọi gói park (50/54/55/57/59/60/61, −22) đều do MessageHandler
  xử lý. Riêng −6/−21/−19 Pack5 CHỈ có ở AvatarMsgHandler/ParkMsgHandler
  nên ở phiên park thật chúng bị rơi — bản port đã bổ sung 3 case vào
  MessageHandler, uỷ thác cho cùng AvatarService.chatToUser /
  ParkService.handleAddFriendRequest / handleAddFriendReply.
- **Tặng quà (gói 58)** cần shop quà 26 + trừ tiền → đợt Shop. **Mời về
  nhà (gói −68 {byte type, int id})** → đợt Nhà. Cả hai nút báo rõ trong
  tab Hệ thống khi bấm, không giả vờ chạy.
- **Khác** = danh sách lệnh server gửi qua MENU_ROTATE (−83) theo sự
  kiện; chưa có lệnh thì báo rõ.
