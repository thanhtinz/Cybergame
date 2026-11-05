# Kết hôn / Hẹn hò — Đặc tả port

Nguồn: client-src VÀNG (MapScr.onWeddingStart, ParkMsgHandler case 93,
ParkService.doRequestWedding, MainMenu.IActionOkWedding, Avatar.idWedding)
+ server fan (bảng `marry`, GlobalHandler TEXT_BOX 100/101,
ParkService.WEDDING_BIGINHanlder — stub hardcode, AvatarService icon theo
level). Hệ fan làm dở dang → bản port hoàn thiện theo cùng giao thức.

## Dữ kiện gốc (Teamobi, từ client Pack5)

- Lễ đường: map client 11 "Khu lễ đường" (server map 10), NPC Chủ Hôn
  525 (part 2480) — phía client gốc Chủ Hôn là đối tượng IDDB −100;
  chạm vào → hộp OK "Bạn có muốn bắt đầu lễ cưới không?" → client gửi
  **93 {byte roomID = map hiện tại, byte boardID}**.
- S→C **93 {int boyId, int girlId}** phát cả khu →
  `onWeddingStart(boy, girl)`: nhạc nền 82; cô dâu/chú rể bị dời về
  x=0, hàng y=8 ô; mặc đồ cưới TẠM: dâu addPart(2475, 20) +
  (2476, 10) + (300, 60) + (302, 70); rể addPart(2477, 20) +
  (2478, 10); khách mời bị xếp vào ghế (listChair của map). Trạng
  thái lễ giữ tới khi rời map (isWedding=false ở luồng đổi map).
- `idWedding` (short, −1 = độc thân) là id ẢNH OBJECT vẽ cạnh tên
  nhân vật: gói 50 (vào khu) mang một mảng idWedding theo từng người
  (sau map items, chỉ client type 9 đọc); gói 51 (người vào khu) mang
  ở đuôi block; friendlist −81 cũng mang. Icon: **2 = hẹn hò** (tim
  đôi hồng), **1153–1157 = nhẫn cưới** theo cấp (xem mốc dưới). Ảnh
  lấy từ res object của server gốc (đã có trong server-full).

## Phần fan (server Pack5) — giữ làm nền

- Bảng `marry (id, idNam, idNu, level, perLevel)`; level 0 = hẹn hò,
  ≥1 = đã cưới. Dữ liệu mẫu gốc: (1, 7, 1, 20, 10).
- Hẹn hò: menu lệnh xoay **MENU_ROTATE (−83) case 36** → server mở
  TEXT_BOX −60 **menuId 100** "gửi lời mời hẹn hò tới? (ghi tên nhân
  vật)" → người kia nhận TEXT_BOX **menuId 101** hỏi đồng ý, trả lời
  "ok"/"yes" → INSERT marry level 0; cả hai nhận dialog + biết đồ
  của nhau (wearingMarry — chỉ phục vụ panel thông tin, bản port bỏ
  qua). Một người chỉ thuộc một cặp (kiểm tra cả idNam lẫn idNu).
- Lễ cưới (fan stub): nhận 93 → `accceptMarry` UPDATE marry SET
  level=1, perLevel=0 WHERE idNam = người gửi; stub phát {7, 1}
  hardcode — bản port thay bằng cặp thật.
- Icon theo levelMarry (AvatarService): hẹn hò (có cặp, level 0) = 2;
  cưới 1–4 → 1153 "Cặp đôi mới cưới"; 5–9 → 1154; 10–14 → 1155;
  15–19 → 1156; 20–23 → 1157. Tên nhẫn của fan đặt bừa — không dùng.
- Panel thông tin người chơi (gói info): mang levelMarry + perLevel —
  bản port chưa có panel info người khác, để sau.

## [PORT] — quyết định triển khai

Server (sửa trên nền fan sẵn có trong bản port):
1. `User.weddingIconId()`: level ≥ 1 → theo mốc 1153..1157 (≥24 →
   1157); có cặp level 0 → 2; không cặp → −1. `User` nạp marry khi
   login đã có (levelMarry/PerLevelMarry); bổ sung nạp luôn
   idUsHenHo = id đối tác.
2. `AvatarService.enter` (gói 50): mảng short −1 theo người → thay
   bằng weddingIconId của từng người (client port đọc vào — vị trí
   này client gốc đọc SAU map items, bản port giữ vị trí hiện tại
   của server fan cho khớp parser sẵn có).
3. `play/MapService.addPlayer` (gói 51): short cuối (đang −1) →
   weddingIconId của người vào.
4. `ParkService.WEDDING_BIGINHanlder` viết lại: tra cặp marry của
   người gửi (idNam hoặc idNu); chưa có cặp → dialog "Bạn chưa hẹn
   hò với ai."; bạn đời phải online + cùng zone; nếu level = 0 →
   UPDATE level = 1 (theo id dòng), cập nhật levelMarry cho cả hai
   User đang online; phát 93 {boyId, girlId} cho cả zone (boy =
   idNam, girl = idNu).
5. Chia tay [PORT thêm]: MENU_ROTATE case **37** → mở TEXT_BOX
   menuId **103** "Gõ ok để chia tay"; GlobalHandler.handleTextBox
   case 103: text "ok" → DELETE dòng marry của người gửi, reset
   levelMarry/idUsHenHo cả hai phía (nếu online), dialog thông báo.

Client:
1. ZonePlayers: RemotePlayer.idWedding; parseJoin đọc mảng short ở
   phần đuôi (thay cho đọc bỏ) gán theo thứ tự người; parsePlayerJoin
   đọc phần đuôi {byte, short, short} nếu còn dữ liệu → idWedding =
   short cuối. RemoteFigure mang idWedding.
2. Vẽ icon: cạnh tên người chơi trong map (sau tên, ~18px) qua
   house.imgTexture(idWedding); nạp ảnh 2, 1153–1157 vào
   assets/house/img/ + .b64.
3. Nhận gói **93** khi ở park: weddingBoy/weddingGirl + weddingOn;
   rời map thì tắt. Khi weddingOn: figure của boy/girl được vẽ tại
   điểm lễ cố định trước NPC Chủ Hôn (map 11) bằng doll cưới tạm:
   nữ {0, 300, 2475, 2476, 302}, nam {0, 2477, 2478} (kiểm chứng
   bằng ảnh harness, chỉnh sau). Khách mời giữ nguyên vị trí (bỏ
   qua xếp ghế listChair của gốc — ghi nợ).
4. Điểm vào [PORT]: chạm Tương tác với NPC 525 → sheet 3 lựa chọn
   (Hẹn hò / Bắt đầu lễ cưới / Chia tay) thay cho đối tượng −100
   của gốc: Hẹn hò → gửi −83 {short 36}; Lễ cưới → hộp xác nhận
   (đúng câu gốc) → gửi 93 {byte mapServer, byte −1}; Chia tay →
   gửi −83 {short 37}. TEXT_BOX dùng hạ tầng −60 sẵn có.

## Chưa làm / ghi nợ

- Xếp ghế khách mời + di chuyển đi bộ ra lễ của gốc (listChair).
- Tăng level cưới theo perLevel (gốc/fan không có cơ chế tăng rõ
  ràng ngoài lễ cưới set 1) — icon dừng ở 1153 trừ dữ liệu có sẵn.
- Mục hôn nhân trong panel thông tin người chơi khác.
