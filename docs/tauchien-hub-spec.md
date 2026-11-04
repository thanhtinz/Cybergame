# Đặc tả thi công: Hub MiniGame + Bắn Tàu (Tàu Chiến) — theo bản chính 3.4.4

Tài liệu nghiên cứu phục vụ đợt port Hub MiniGame + Tàu Chiến. Mọi khẳng
định đều có nhãn:

- **[GỐC]** — sự thật có bằng chứng cụ thể trong dữ liệu 3.4.4 đã trích
  (metadata IL2CPP `deep/metadata/all_types_full.json`, giá trị mặc định
  field trong `global-metadata.dat`, bảng lệnh `Cmd_Message`,
  string literals, asset trong `deep/textures/`).
- **[CHƯA RÕ]** — chưa đọc được từ metadata; kèm đề xuất hướng xử lý.

Nguồn đối chiếu nền: `docs/features-344-audit.md` §A1, §D.

---

## 1. Kiến trúc tổng thể hub

- **[GỐC]** Handler hub: `MINIGAME = -90` trong `Cmd_Message` (type
  1869, 341 field). Bốn game online trong hub, id cố định:
  `TRUCXANH = 1`, `AINCRAD = 2`, `GAMEID_CARO_GAME = 3`,
  `TAUCHIEN = 4`.
- **[GỐC]** Class khung:
  - `MiniGameService` — gửi gói hub: `send`, `CheckIsDownloadCmd`,
    `joinGame`.
  - `MiniGameMsgHandler` — `onHandler`, `onMessage`; interface
    `IMiniGameMsgHandler.onMessage`; handler được giữ ở
    `GlobalMessageHandler.miniGameMessageHandler` và
    `CasinoMsgHandler.miniGameMessageHandler` → gói hub được định
    tuyến theo game id tới handler của từng game.
  - `MiniGameScr` — màn nền dùng chung cho cả 4 game (ghế người chơi,
    lượt, đếm giờ, sẵn sàng, thắng/thua/hòa, lịch sử thắng, khán giả).
  - `MiniGameSelectScr` — màn chọn game.
  - Mỗi game có bộ ba riêng: `…GameScr`, `…MsgHandler`, `…Service`
    (TauChien/Caro/TrucXanh/Aincrad đều đủ bộ ba).

### 1.1 Màn nền MiniGameScr (dùng chung) — [GỐC] theo field/method

- Hai ghế: `player` (đối thủ), avatar 2 ghế (`seat1Avatar`,
  `emptySeat`, `leftAvatar`), tên 2 người (`fMe`, `fPlayer`).
- Lượt + giờ: `isTurn`, `isPlayed`, `timeMax`, `time`, `countTime` —
  có đếm giờ theo lượt ở tầng hub.
- Sẵn sàng: `isReady`, `tReady`, `countReady`, ảnh `imgReady`.
- Kết thúc: `imgWin`, `imgLose`, `imgDraw`; lịch sử thắng
  `winHistory` (giới hạn `WIN_HISTORY_MAX`), thời gian hiện kết quả
  `RESULT_SHOW_MS`; **hộp quà khi thắng**: `isBoxWin`, `idBoxGift`.
- Ván giao hữu: `friendlyGameID`, `fontFriendly`, `isFriendly()`,
  `getOutText()`, `paintFriendly` — ván lập qua lời mời là ván giao
  hữu (xem §3).
- Khán giả — tích hợp ngay trong màn nền: `spectateGameID`,
  `spectateBoardID`, `spectatorCount`, `isSpectating()`,
  `setSpectateSeat`, `showSpectateResult`, `askStopSpectate`,
  `closeSpectateResult`, `initSpectateUI`, `paintSpectate`,
  `paintViewerBadge` (huy hiệu số người xem: `imgViewerBg`,
  `imgViewer`, `fontViewer`), `imgRoomPill`, `fontRoomPill`
  (nhãn thông tin phòng/bàn). Có action `IActionStopSpectate`
  (dừng xem qua menu).

### 1.2 Tải asset theo game

- **[GỐC]** `MiniGameService.CheckIsDownloadCmd` + các đường dẫn tài
  nguyên theo game trong string literals: `/minigame/1/…` (asset
  Aincrad: `1FL_Subdoor`, `2FL_Fencing`, `3FL_Ex`, `BG`, `Ghost`),
  `/minigame/2/…` (Trúc Xanh: `BG`, `Card`, `IMG`, `Stroke`,
  `bgScore`), `/minigame/3/…` (Caro: `BG_Caro2_`, `X`, `O`, `F_X`,
  `F_O`, `WIN_X`, `WIN_O`, `BG_Box_`), `/minigame/4/…` (Tàu Chiến,
  xem §6), `/minigame/5/…` (chỉ có tiền tố thư mục), dùng chung:
  `/minigame/ready`, `/minigame/Go`, `/minigame/Panel_`,
  `/minigame/tx_Win`, `/minigame/tx_Lose`, `/minigame/tx_Draw`,
  icon màn chọn: `/minigame/select/trucxanh`,
  `/minigame/select/caro`, `/minigame/select/tauchien`.
  → Mỗi game có gói asset tải riêng khi vào lần đầu (giống mẫu
  DOWN_FARMLVL2 của farm lvl2).
- **[CHƯA RÕ]** Ánh xạ thư mục tải ↔ game id không khớp 1-1 hiển
  nhiên (thư mục 1 chứa asset Aincrad trong khi AINCRAD = 2), và thư
  mục `/minigame/5/` thuộc về game/hệ nào. Hướng xử lý: khi thi công
  chỉ cần giữ đúng bộ asset theo từng game như liệt kê ở §6, không
  cần tái tạo cơ chế thư mục tải của gốc.

### 1.3 Hằng số màn chọn game (MiniGameSelectScr) — [GỐC], đọc từ field default values

| Hằng | Giá trị | Ghi chú |
|---|---|---|
| `ZONE_1` | 2 | xem [CHƯA RÕ] bên dưới |
| `ZONE_2` | 4 | |
| `MAX_GAME` | 12 | tối đa 12 game trong danh sách |
| `MAX_PER_ROW` | 6 | 6 icon mỗi hàng |
| `PADDING_X` | 48 | |
| `ICON_LABEL_GAP` | 12 | |
| `LABEL_LINE_H` | 32 | |
| `LABEL_BOTTOM_PAD` | 24 | |
| `ANCHOR_SCREEN_MARGIN` | 20 | neo theo màn hình (màn chọn mở dạng neo phía trên màn hiện tại: `anchorX/anchorY`, `ANCHOR_GAP_ABOVE`) |
| `ANCHOR_GAP_ABOVE` | 28 | |
| `ANIM_DURATION_MS` | — | [CHƯA RÕ] giá trị (không giải mã chắc chắn được từ blob) |

- **[GỐC]** Cấu trúc dữ liệu: `zoneGames` (danh sách game theo zone),
  `currentZone`, `registerGame(...)` — từng game tự đăng ký vào màn
  chọn; `getCurrentList()`.
- **[CHƯA RÕ]** Ý nghĩa `ZONE_1 = 2`, `ZONE_2 = 4` (số game mỗi
  zone? id zone?) và zone nào chứa game nào. Hướng xử lý: dựng màn
  chọn 1 lưới 4 game (đúng danh sách thực tế hiện tại) với cùng
  hằng số lưới (6/hàng), zone chỉ làm khi có thêm bằng chứng.

---

## 2. Điểm vào chơi

- **[GỐC]** Trong park có **cửa mini-game**: field `doorMiniGame`
  (type `Avatar`), và `LoadMap` có đủ hằng kiểu map cho hub:
  `T_MINIGAME_SELECT`, `T_MINIGAME_TRUCXANH`, `T_MINIGAME_AINCRAD`,
  `T_MINIGAME_CARO`, `T_MINIGAME_SHIP` → vào hub/bàn chơi đi qua cơ
  chế nạp map như các cửa khác trong park.
- **[GỐC]** Có thêm nút sự kiện: model `EventButton`, lệnh
  `EVENT_BUTTON_CLICKED = -59` (đã có từ Pack5), sprite
  `eventbuttonbg` (theo audit §A1).
- **[GỐC]** Vào thẳng ván qua lời mời (§3) — không cần qua màn chọn.
- **[CHƯA RÕ]** Vị trí chính xác của cửa mini-game trên map park
  (dữ liệu map phía server, client không chứa). Hướng xử lý: đặt cửa
  theo cùng cơ chế doJoin/LoadMap của port khi thi công; vị trí lấy
  theo map park hiện hành của bản port.

---

## 3. Mời chơi — handler `INVITE_MINIGAME = 21`

### 3.1 Lệnh con — [GỐC] theo Cmd_Message

| Lệnh | Giá trị | Chiều (suy từ tên + handler) |
|---|---|---|
| `INVITE_SEND` | 0 | C→S gửi lời mời |
| `INVITE_REPLY` | 1 | C→S trả lời (đồng ý/từ chối) |
| `INVITE_RESULT` | 2 | S→C kết quả lời mời |
| `INVITE_CODE_SENT` | 0 | S→C (hoặc C→S) đã tạo/gửi mã mời |
| `INVITE_CODE_JOIN` | — | vào ván bằng mã; **[CHƯA RÕ]** số lệnh — dòng này bị lỗi parse trong bảng lệnh đã trích (`bytes:0101ffff010101`), cần trích lại từ metadata khi thi công |

### 3.2 Cơ chế — bằng chứng từ MiniGameInviteMsgHandler

- **[GỐC]** Field: `inviterID` (id người mời), `inviteGameID`
  (game được mời), `inviteDeadline` (hạn chót của lời mời),
  `lastInviteTick`, hằng `INVITE_LOCK_MS`.
- **[GỐC]** `INVITE_LOCK_MS = 4263` (đọc từ field default value,
  int32) — khóa chống spam giữa hai lần gửi mời, khớp chuỗi
  "Bạn vừa gửi lời mời, vui lòng chờ vài giây".
- **[GỐC]** Method: `sendInvite`, `onInvite` (nhận lời mời),
  `onResult` (nhận kết quả), `getGameName` (tên game theo id).
  Phía gửi đi qua `GlobalService.doInviteMiniGame` /
  `doReplyInviteMiniGame`.
- **[GỐC]** Nội dung hộp thoại mời (string literal):
  `"{0} mời bạn chơi {1}.\nVán giao hữu - không nhận quà."` →
  **ván qua lời mời là ván giao hữu, không có quà/thưởng**. Chuỗi
  liên quan: `"Bạn có muốn mời …"` (xác nhận trước khi gửi).
- **[CHƯA RÕ]** (a) Định dạng "mã mời" của cơ chế `INVITE_CODE_*`
  (mã số? chuỗi? ai tạo, hạn dùng bao lâu); (b) giá trị
  `inviteDeadline` (thời hạn trả lời lời mời trực tiếp); (c) điều
  kiện gửi mời (phải là bạn bè? người cùng khu?). Hướng xử lý: làm
  cơ chế mời trực tiếp (send/reply/result + deadline + lock 4263ms)
  trước; cơ chế mã mời bổ sung sau khi có bằng chứng — không chặn
  đợt Tàu Chiến.

---

## 4. Khán giả — handler `SPECTATE_MINIGAME = 22`

### 4.1 Lệnh con — [GỐC]

| Lệnh | Giá trị |
|---|---|
| `SPECTATE_LIST` | 0 |
| `SPECTATE_WATCH` | 1 |
| `SPECTATE_STOP` | 2 |
| `SPECTATE_RESULT` | 3 |
| `SPECTATE_ENDED` | 4 |

Phía client gửi qua `GlobalService.doSpectateList`,
`doSpectateWatch`, `doSpectateStop`.

### 4.2 Cơ chế — bằng chứng

- **[GỐC]** `MiniGameSpectateMsgHandler`: `onLogin`, `probe`,
  `openList`, `onList`, `statusText`, `onResult`, `onEnded`,
  `screenOf` (mở đúng màn game theo game id). Field phụ trợ:
  `adminState`, `traceUntil`, `probing` (có vẻ là đường n dò/thử
  trạng thái — không cần tái tạo nguyên si khi port).
- **[GỐC]** Model danh sách ván đang chơi: type `Room` — field
  `gameID`, `status`, `boardID`, `spectators` (số khán giả),
  `isPrivate` (bàn riêng — liên quan chuỗi "Bạn có muốn rời bàn
  chơi riêng? Bàn sẽ giải tán."), `seat1`, `seat2`, `name1`,
  `name2`, `roomText`, `statusText`, `icon`, `statusImg`.
  → `SPECTATE_LIST` trả danh sách `Room`; chọn một Room gửi
  `SPECTATE_WATCH` (gameID + boardID); server trả `SPECTATE_RESULT`
  kèm **snapshot riêng của từng game** (§5.6); `SPECTATE_ENDED`
  báo ván kết thúc khi đang xem.
- **[GỐC]** `MapScr.addSpectateMenu` — menu xem trận cũng được gắn
  ở màn map. Type `LivestreamListDlg` có field `imgTauChien` —
  danh sách xem có thể dùng chung khung UI danh sách livestream
  (bằng chứng yếu, chỉ 1 field).

---

## 5. Tàu Chiến (game id 4)

### 5.1 Pha chơi — [GỐC] từ field default values của TauChienGameScr

`PHASE_PLACING = 0` → `PHASE_WAIT_READY = 1` → `PHASE_BATTLE = 2`
→ `PHASE_END = 3`.

### 5.2 Lưới + tàu

- **[GỐC]** Hằng `BOARD_SIZE = 20` (giá trị byte đọc trực tiếp từ
  field default value trong metadata).
- **[CHƯA RÕ]** Diễn giải `BOARD_SIZE = 20`: lưới 20×20, hay 20 là
  tổng hai bàn 10×10, hay kích thước mảng phẳng. Đây là thông số
  quan trọng nhất còn thiếu. Đề xuất khi thi công: đối chiếu tỉ lệ
  layout (ô native 36px — xem dưới; panel hai bàn trong
  TauChienGameScr có các field `xTotalBoard/wTotalBoard`,
  `GAP_BOARDS_CELLS`, `BORDER_PAD_CELLS`, `MID_PAD_CELLS`…) và
  playtest cảm giác; nếu không có thêm nguồn, chọn lưới 10×10 mỗi
  bàn (chuẩn Battleship) và ghi rõ là quyết định port.
- **[GỐC]** Ô native: texture `Map_Slot` = 36×36 px; các sprite tàu
  dài ≥3 ô đều cao đúng 36px và rộng 108/144/180px = 3/4/5 ô →
  ô native của bàn là **36px** (khớp field `SLOT_NATIVE_PX` của
  `CellTauChien`, dù giá trị hằng của nó nằm trong .cctor —
  [CHƯA RÕ] giá trị khai báo, nhưng suy ra 36 từ asset).
- **[GỐC]** 5 loại tàu — enum trong `ShipTauChien` (giá trị đọc từ
  field defaults): `TYPE_DESTROYER = 0`, `TYPE_CRUISER = 1`,
  `TYPE_SUBMARINE = 2`, `TYPE_BATTLESHIP = 3`, `TYPE_CARRIER = 4`,
  `NUM_TYPE = 5`. Tên hiển thị (string literals, hàm
  `getShipName`): **Khu trục hạm, Tuần dương hạm, Tàu ngầm, Thiết
  giáp hạm, Tàu sân bay**.
- **[GỐC]** `FLEET_SIZE = 14` (field default value). **[CHƯA RÕ]**
  14 là tổng số tàu hay tổng số ô tàu — cần `QUANTITY_BY_TYPE` mới
  phân xử được.
- **[CHƯA RÕ]** `LENGTH_BY_TYPE`, `QUANTITY_BY_TYPE`,
  `FLEET_LENGTH_BY_PROTOCOL_INDEX` là mảng khởi tạo trong .cctor
  (code), metadata không chứa giá trị. Bằng chứng gián tiếp mạnh:
  5 sprite `Ship_lv1..lv5` có độ dài đúng 1..5 ô (đo pixel, §6) và
  `ShipTauChien` có `typeFromLength` (suy type từ độ dài) +
  `length` là field chính của tàu → mỗi loại tương ứng một độ dài
  trong 1..5. Ánh xạ type ↔ độ dài cụ thể và số lượng từng loại:
  **chưa chốt**. Đề xuất khi thi công: nếu không bắt được gói live,
  dùng bộ tham chiếu hợp lý nhất với `FLEET_SIZE = 14` (ví dụ diễn
  giải 14 = tổng số ô tàu) và ghi [PORT] trong code.
- **[GỐC]** Model tàu (`ShipTauChien`): `type`, `length`, `row`,
  `col`, `isVertical`, `hitCount`, `isSunk`, `isPlaced`;
  method `place`, `getCells`, `containsCell`, `onHit`,
  `buildFleet`, `toProtocolOrder`, `hasPosition`.

### 5.3 Luật xếp tàu — [GỐC] từ string literals (thông báo lỗi của `placeErrorText`)

1. Không chồng tàu: `"Có hai tàu đè lên nhau"`.
2. Không ra ngoài bàn: `"Có tàu nằm ngoài bàn cờ"`.
3. **Hai tàu phải cách nhau ít nhất 1 ô nước**:
   `"Hai tàu phải cách nhau ít nhất 1 ô nước"` — tàu không được kề
   nhau (kể cả chéo, theo cách hiểu "cách 1 ô nước").
4. Hướng hợp lệ: `"Hướng đặt tàu không hợp lệ"`.

Thao tác xếp — [GỐC] từ method/field của TauChienGameScr:
kéo–thả trực tiếp trên bàn (`dragShip`, `listDragShip`,
`updateListDrag`, `paintListDragShip`, `DRAG_THRESHOLD_PX`), xoay
tàu (`doRotateShip`, nút `imgBtnRotate`, handle xoay
`getRotateHandleRect`), hoặc chọn loại tàu rồi bấm đặt
(`selectType`, `selectedType`, `doAddToBoard`, `paintAddButton`,
`isAddEnabled`), vị trí mặc định (`assignDefaultPosition`,
`FindFirstUnplaced`), kiểm tra vị trí (`canPlaceAt`,
`isCellOccupied`, `FindShipAt`), gợi ý vùng hợp lệ
(`paintValidHint`, `paintDragInvalidHint`, `imgValidHint`), xếp tự
động (`onAutoPlace` + nút `"Xếp ngẫu nhiên"`), đếm tàu chưa đặt
(`countUnplaced`, `isAllShipPlaced`) — chỉ sẵn sàng khi đã đặt hết.

### 5.4 Luật bắn + lượt

- **[GỐC]** Bắn theo 2 bước chọn–xác nhận: chọn ô
  (`selectedShootCell`, `checkShootTouch`, `setSelected`) rồi bấm
  xác nhận (`doFireSelectedCell`, nút chấp nhận
  `Bt_Function_Accept`, chuỗi `"Xác nhận bắn"`).
- **[GỐC]** Trạng thái ô (`CellTauChien`, giá trị từ field
  defaults): `NONE = 0`, `SHIP = 1`, `FIRE = 2` (trúng/đang cháy),
  `WATER = 3` (đã bắn — nước/trượt), `SUNK = 4` (tàu chìm). Method:
  `setShotState`, `setWaterRevealed`, `canShoot` (ô đã bắn thì
  không bắn lại), hiệu ứng cháy/trượt: `paintBurn`, `paintMiss`,
  `Eff_Burn_0`, `Eff_Miss_0`, hằng nhịp khung `BURN_FRAME_MS`,
  `MISS_FRAME_MS` ([CHƯA RÕ] giá trị ms — nằm trong .cctor).
- **[GỐC]** Chuỗi kết quả: `"Bắn trượt"`, `"Đã bắn hạ"`,
  `"Đang bắn"`, banner lượt: `"Lượt của đối thủ"`, `"Lượt đối
  thủ"`, `"Chờ đối thủ"`, `"Chờ đến lượt."` (nhóm chữ dùng
  chung hub).
- **[CHƯA RÕ]** Trúng có được bắn tiếp hay không. Cấu trúc lệnh
  tách `SHOT_RESULT` (kết quả phát bắn) khỏi `CHANGE_TURN` (đổi
  lượt) gợi ý đổi lượt là sự kiện riêng do server quyết định —
  khả năng cao theo luật Battleship phổ biến (trượt mới mất lượt).
  Hướng xử lý: thi công theo luật "trúng bắn tiếp, trượt đổi
  lượt", để server quyết và client chỉ nghe `CHANGE_TURN`; đánh
  dấu cần xác minh nếu bắt được gói live.

### 5.5 Thắng/thua, thoát, hết giờ

- **[GỐC]** Thắng = bắn chìm toàn bộ hạm đội đối thủ: `onEnd` +
  `endWinFlag`; khi kết thúc có pha **lộ diện hạm đội**
  (`updateEndReveal`, `END_REVEAL_MS`, `paintRevealedFleet`,
  `revealedFleetSeat1`, `revealedFleetSeat2`, tàu chìm của địch
  nhấp nháy `END_SHIP_BLINK_MS` — hằng này cũng có ở tầng
  CellTauChien), tổng thời gian hiện kết quả `RESULT_TOTAL_SEC`,
  4 biến thể tab kết quả: `END_TAB_ME_WIN`, `END_TAB_ME_LOSE`,
  `END_TAB_OPP_WIN`, `END_TAB_OPP_LOSE` (giá trị [CHƯA RÕ]).
  Sau đó áp cơ chế chung của MiniGameScr: ảnh Win/Lose/Draw, lịch
  sử thắng, hộp quà (`idBoxGift`) nếu thắng.
- **[GỐC]** Thoát giữa ván: lệnh `PLAYER_LEAVE_TAUCHIEN = 11`;
  phía còn lại thắng — chuỗi `"Đối thủ thoát. Bạn thắng"`. Có
  chuỗi xác nhận thoát dùng chung: `"Nếu thoát bạn sẽ bị xử thua.
  Bạn có muốn thoát?"`.
- **[GỐC]** Đuổi người: `KICK_TAUCHIEN = 12` + `onKick`.
  **[CHƯA RÕ]** ai được đuổi (chủ bàn?) và đuổi ở pha nào.
- **[GỐC]** Đếm ngược bắt đầu ván: `START_COUNTDOWN_STEP_MS`,
  `startCountdownTick`, `updateStartCountdown`,
  `paintStartCountdown` ([CHƯA RÕ] giá trị ms/số bước).
- **[CHƯA RÕ]** Xử lý hết giờ từng lượt (hub có `timeMax` ở
  MiniGameScr nhưng giá trị + hậu quả hết giờ — tự mất lượt? xử
  thua? — không đọc được), và có đếm giờ ở pha xếp tàu/sẵn sàng
  hay không (`PHASE_WAIT_READY` + `"Đối thủ chưa sẵn sàng"` chỉ
  cho thấy trạng thái chờ sẵn sàng).

### 5.6 Luồng gói đầy đủ — handler hub −90, game TAUCHIEN = 4

Thứ tự + lệnh con [GỐC] (Cmd_Message); chiều gói và handler phía
client (`TauChienGameScr.on*`, `TauChienService`,
`TauChienMsgHandler.onMessage` + `readFleet`):

| # | Lệnh | Giá trị | Luồng |
|---|---|---|---|
| 1 | `JOIN_TAUCHIEN` | 0 | C→S vào bàn (qua `TauChienService.joinGame`, thường sau hub/mời/chọn đối thủ); S→C xác nhận ghế (`setInfo`, `mySeat`) |
| 2 | `START_PLACE_TAUCHIEN` | 1 | S→C mở pha xếp tàu (`onStartPlace`) |
| 3 | `PLACE_SHIPS_TAUCHIEN` | 2 | C→S gửi đội hình (`sendPlaceShips`) — xem ghi chú payload dưới |
| 4 | `PLACE_RESULT_TAUCHIEN` | 3 | S→C kết quả xếp (hợp lệ/lỗi → `onPlaceResult`, `placeErrorText`) |
| 5 | `PLAYER_READY_TAUCHIEN` | 4 | hai chiều: C→S báo sẵn sàng / S→C báo đối thủ sẵn sàng (`onPlayerReady`, `myReady`, `oppReady`) |
| 6 | `AUTO_PLACE_TAUCHIEN` | 5 | xếp tự động (`onAutoPlace`) — [CHƯA RÕ] client tự xếp rồi gửi PLACE_SHIPS, hay server xếp hộ |
| 7 | `START_BATTLE_TAUCHIEN` | 6 | S→C bắt đầu bắn, kèm lượt đầu (`onStartBattle`, sau đếm ngược) |
| 8 | `SHOOT_TAUCHIEN` | 7 | C→S bắn 1 ô (`sendShoot` — tọa độ ô đã chọn + xác nhận) |
| 9 | `SHOT_RESULT_TAUCHIEN` | 8 | S→C kết quả phát bắn cho cả hai phía (`onShotResult`: trúng/trượt/chìm + ô) |
| 10 | `CHANGE_TURN_TAUCHIEN` | 9 | S→C đổi lượt (`onChangeTurn`) |
| 11 | `END_TAUCHIEN` | 10 | S→C kết thúc ván (`onEnd`: thắng/thua + lộ diện hạm đội) |
| 12 | `PLAYER_LEAVE_TAUCHIEN` | 11 | rời ván (`onPlayerLeave`) |
| 13 | `KICK_TAUCHIEN` | 12 | đuổi khỏi bàn (`onKick`) |
| 14 | `SNAPSHOT_TAUCHIEN` | 13 | S→C ảnh chụp ván cho khán giả/người vào lại (`applySnapshot`) |
| 15 | `SPECTATOR_COUNT_TAUCHIEN` | 14 | S→C số khán giả hiện tại |

Payload đã rõ ở mức cấu trúc:

- **[GỐC]** `PLACE_SHIPS`: client có `toProtocolOrder()` +
  hằng `FLEET_LENGTH_BY_PROTOCOL_INDEX` và handler có
  `readFleet` → đội hình được truyền theo **một thứ tự giao thức
  cố định** (theo độ dài tàu), mỗi tàu kèm vị trí (row, col) +
  hướng (`isVertical`); phía nhận dựng lại tàu bằng
  `typeFromLength`. **[CHƯA RÕ]** layout byte chính xác (thứ tự
  các độ dài trong `FLEET_LENGTH_BY_PROTOCOL_INDEX`, mã hóa
  hướng/vị trí) — khi thi công tự định nghĩa format cho server
  port, giữ đúng ngữ nghĩa trên.
- **[GỐC]** `SNAPSHOT` (cho khán giả): `applySnapshot` +
  `revealedFleetSeat1/revealedFleetSeat2` +
  `syncCellStates` → snapshot chứa trạng thái ô của cả hai bàn +
  phần hạm đội đã lộ của hai ghế (tàu đã chìm/hư hại được thấy),
  đủ để dựng lại ván đang dở trên màn khán giả (kèm
  `SPECTATOR_COUNT` cập nhật riêng).
- **[CHƯA RÕ]** Layout byte của mọi lệnh (metadata không chứa
  phần thân method). Nguyên tắc port: giữ nguyên số lệnh + thứ
  tự luồng như bảng trên, tự thiết kế payload tối thiểu đủ ngữ
  nghĩa (ghế, ô, trạng thái) cho server của port.

---

## 6. Asset Tàu Chiến

Đường dẫn tải gốc: tiền tố `/minigame/4/`. File có sẵn trong
bundle 3.4.4 (đã trích ở `deep/textures/`):

| File | Kích thước (px) | Vai trò |
|---|---|---|
| `ListShip_BG.png` | 256×237 | Nền panel danh sách tàu (sọc xanh xen kẽ — vẽ kéo giãn/9-slice) |
| `ListShip_Focus.png` | 256×46 | Khung highlight 1 hàng tàu trong danh sách (chiều cao hàng = 46) |
| `Ship_lv1.png` | 32×32 | Tàu dài 1 ô — hình vật tròn tím (chưa rõ tên gọi riêng) |
| `Ship_lv2.png` | 64×32 | Tàu dài 2 ô — hình tàu ngầm cam |
| `Ship_lv3.png` | 108×36 | Tàu dài 3 ô — tàu chiến xanh lá |
| `Ship_lv4.png` | 144×36 | Tàu dài 4 ô — tàu chiến đỏ |
| `Ship_lv5.png` | 180×36 | Tàu dài 5 ô — tàu sân bay |
| `tauchien.png` | 64×64 | Icon Tàu Chiến (hub/nút sự kiện) |
| `thuyenchien.png` | 54×54 | Icon phụ Tàu Chiến |
| `Map_Slot.png` | 36×36 | Ô bàn cơ bản (ô native = 36px) |
| `Map_Slot_dongbang.png` | 36×36 | Ô "đóng băng" (trạng thái khóa — khán giả/chưa mở bắn) |
| `Map_Focus.png` | 50×50 | Con trỏ focus trên bàn |
| `Map_Stroke.png` | 72×72 | Viền bàn (vẽ 9-slice, `slice9`) |
| `Panel_Center_BG.png` | 261×103 | Nền panel giữa hai bàn (đồng hồ + tab lượt) |
| `Panel_Center_Clock.png` | 53×53 | Mặt đồng hồ đếm giờ giữa bàn |
| `Panel_Center_Turn_1.png` | 111×26 | Tab báo lượt — phía 1 |
| `Panel_Center_Turn_2.png` | 111×26 | Tab báo lượt — phía 2 |
| `ColumnRow_BG_1.png` | 31×20 | Nhãn cột (chữ cái — `COL_LETTERS`) |
| `ColumnRow_BG_2.png` | 22×31 | Nhãn hàng (số — `ROW_TEXTS`) |
| `ColumnRow_BG_3.png` | 22×31 | Nhãn hàng/cột biến thể |
| `Eff_Burn_0.png` | 72×288 | Dải khung hiệu ứng cháy (cắt dọc — `sliceFramesVertical`) |
| `Eff_Miss_0.png` | 116×280 | Dải khung hiệu ứng bắn trượt (vệt nước) |
| `Button_bg3.png` | 169×60 | Nền nút hành động (Sẵn sàng/Xác nhận…) |
| `Bt_Function_Rotate.png` | 64×64 | Nút xoay tàu |
| `BG_1.png`, `BG_2.png` | 156×156 | Nền trang trí panel (dựng nền nhiều lớp) |
| `icnStatus_1.png` | 67×29 | Icon trạng thái bàn/người chơi |
| `icnStatus_2.png` | 36×37 | Icon trạng thái |

Chỉ có đường dẫn tải, **không có trong bundle** (phải tự thay thế
hoặc bỏ qua khi port — [CHƯA RÕ] hình dạng): `BG_3`,
`BG_3_decor_L`, `Bt_Function_Accept` (nút xác nhận bắn — quan
trọng), `Bt_Function_Move` (nút di chuyển tàu), `icnStatus_3`,
`icnStatus_4`.

Asset hub dùng chung: `/minigame/select/{trucxanh,caro,tauchien}`
(icon màn chọn), `/minigame/ready`, `/minigame/Go`,
`/minigame/Panel_`, `/minigame/tx_Win`, `/minigame/tx_Lose`,
`/minigame/tx_Draw` (chữ kết quả ở màn nền).

---

## 7. Nhãn chữ / hướng dẫn tiếng Việt (string literals)

### 7.1 Riêng Tàu Chiến — độ tin cậy cao

| Chuỗi | Dùng ở |
|---|---|
| `Tàu Chiến` / `Tàu chiến` | Tên game (hub, lời mời — `getGameName`) |
| `Tàu của bạn` | Nhãn bàn của mình |
| `Khu trục hạm` | Tên tàu (getShipName) |
| `Tuần dương hạm` | Tên tàu |
| `Tàu ngầm` | Tên tàu |
| `Thiết giáp hạm` | Tên tàu |
| `Tàu sân bay` | Tên tàu |
| `Đặt tàu` | Tiêu đề/nút pha xếp tàu |
| `Đang xếp tàu` / `Đang xếp` | Trạng thái pha xếp |
| `Xếp ngẫu nhiên` | Nút xếp tự động |
| `Xếp xong` | Nút hoàn tất xếp |
| `Có hai tàu đè lên nhau` | Lỗi xếp tàu |
| `Có tàu nằm ngoài bàn cờ` | Lỗi xếp tàu |
| `Hai tàu phải cách nhau ít nhất 1 ô nước` | Lỗi xếp tàu (luật cách 1 ô nước) |
| `Hướng đặt tàu không hợp lệ` | Lỗi xếp tàu |
| `Xác nhận bắn` | Nút xác nhận phát bắn |
| `Đang bắn` | Trạng thái pha bắn |
| `Bắn trượt` | Kết quả phát bắn |
| `Đã bắn hạ` | Kết quả bắn chìm/trúng hạ |

### 7.2 Dùng chung hub/mini-game

| Chuỗi | Ghi chú |
|---|---|
| `{0} mời bạn chơi {1}.\nVán giao hữu - không nhận quà.` | Nội dung lời mời |
| `Bạn vừa gửi lời mời, vui lòng chờ vài giây` | Khớp INVITE_LOCK_MS |
| `Bạn có muốn mời` | Tiền tố hộp xác nhận mời (chuỗi đầy đủ bị cắt ở literal) |
| `Đối thủ chưa sẵn sàng` | Chờ sẵn sàng |
| `Đối thủ thoát. Bạn thắng` | Đối thủ rời ván |
| `Nếu thoát bạn sẽ bị xử thua. Bạn có muốn thoát?` | Xác nhận thoát ván |
| `Lượt của đối thủ` / `Lượt đối thủ` / `Chờ đối thủ` / `Chờ đến lượt.` | Banner lượt |
| `Bạn thắng!` / `Bạn thua!` / `{0} thắng!` | Kết quả ván |
| `Bạn có muốn rời bàn chơi riêng? Bàn sẽ giải tán.` | Bàn riêng (liên quan `Room.isPrivate`) |

Không tìm thấy chuỗi "hướng dẫn chơi Tàu Chiến" dài (luật chơi
đầy đủ dạng văn bản) trong string literals — luật được truyền qua
chính các thông báo lỗi xếp tàu ở §7.1. **[CHƯA RÕ]** có màn hình
hướng dẫn riêng hay không.

---

## 8. Tổng hợp [CHƯA RÕ] + đề xuất xử lý khi thi công

1. **`BOARD_SIZE = 20` là lưới bao nhiêu** — thông số quan trọng
   nhất. Xử lý: thử dựng 10×10/bàn (chuẩn) và đối chiếu cảm giác
   layout bằng các hằng cell-based của TauChienGameScr; ghi
   quyết định vào code.
2. **`LENGTH_BY_TYPE` / `QUANTITY_BY_TYPE` / ánh xạ type↔độ dài /
   ý nghĩa `FLEET_SIZE = 14`** — mảng trong .cctor. Xử lý: nếu
   không bắt được gói live 3.4.4 (đang kẹt bắt tay server mới),
   chọn bộ số [PORT] hợp lý, ưu tiên diễn giải làm 14 = tổng số
   ô tàu, giữ đúng 5 loại + độ dài 1..5 theo sprite.
3. **Trúng có được bắn tiếp không** — xử lý: server quyết qua
   `CHANGE_TURN`; mặc định luật "trúng bắn tiếp".
4. **`INVITE_CODE_JOIN` (số lệnh + định dạng mã + hạn mã)** —
   trích lại bảng Cmd từ metadata khi thi công; cơ chế mã mời
   làm sau đợt mời trực tiếp.
5. **Layout byte của 15 lệnh Tàu Chiến + payload snapshot** —
   tự thiết kế cho server port, giữ nguyên số lệnh/luồng.
6. **Giá trị các hằng thời gian**: `INVITE_LOCK_MS = 4263` đã rõ;
   còn `timeMax` mỗi lượt, `START_COUNTDOWN_STEP_MS`,
   `END_REVEAL_MS`, `RESULT_TOTAL_SEC`, `BURN_FRAME_MS`,
   `MISS_FRAME_MS`, `WIN_HISTORY_MAX`, `RESULT_SHOW_MS`,
   `ANIM_DURATION_MS` — đều trong .cctor/code. Xử lý: chọn giá
   trị hợp lý theo nhịp game (ghi [PORT]).
7. **Vị trí cửa mini-game trong park + điều kiện KICK + xử lý
   hết giờ** — dữ liệu server/luật server. Xử lý: theo cơ chế
   chung của port (doJoin, chủ bàn kick, hết giờ mất lượt).
8. **Asset thiếu trong bundle** (`Bt_Function_Accept`,
   `Bt_Function_Move`, `BG_3`, `icnStatus_3/4`) — chỉ có path
   tải. Xử lý: dùng nút chữ/nền `Button_bg3` thay thế tạm, ghi
   nợ asset.
9. **Ý nghĩa `ZONE_1/ZONE_2` của màn chọn game** — xử lý: lưới
   1 zone cho 4 game hiện có.

---

## 9. Quyết định thi công của bản port (2026-11-03, đã làm + test sống PASS)

Các điểm [CHƯA RÕ] ở trên được xử lý như sau (đánh dấu [PORT], sửa
một chỗ duy nhất nếu sau này bắt được gói live 3.4.4):

1. **Bàn 10×10 mỗi người** — BOARD_SIZE=20 hiểu là tổng hai bàn.
   Bằng chứng bổ sung: layout gốc tính theo ô native 36px, hai bàn
   10 ô (360px) + panel giữa 261px vừa khung 1280px; bàn 20 ô/bên
   sẽ tràn khung.
2. **Hạm đội 14 tàu**: dài 5×1, 4×1, 3×2, 2×4, 1×6 (tổng 14 tàu,
   29 ô). Ánh xạ tên theo thứ tự enum: 1 Khu trục hạm, 2 Tuần dương
   hạm, 3 Tàu ngầm, 4 Thiết giáp hạm, 5 Tàu sân bay. Hằng số nằm ở
   `MiniGameService.FLEET_QTY` (server) + `WorldScreen.TC_FLEET_QTY`.
3. **Trúng được bắn tiếp, trượt đổi lượt** (server phát CHANGE_TURN
   riêng như cấu trúc lệnh gốc). Lượt tối đa **30 giây**, hết giờ tự
   mất lượt.
4. **Phong bì gói**: bản chính dùng handler MINIGAME=−90 nhưng server
   Pack5 đã dùng −90 cho UPGRADE_CHEST (handler Nhà) → bản port dùng
   gói **−113** với payload {byte gameId, byte sub, ...}; số lệnh con
   giữ nguyên bản chính (hub riêng: LIST/CREATE/JOIN/JOIN_CODE/
   INVITE/REPLY/LEAVE/WATCH/STOP_WATCH/REMATCH + S→C JOINED=10,
   OPP_JOINED=11, OPP_LEFT=12, REMATCH_OFFER=16, INVITE_IN=20,
   INVITE_RESULT=21, ERROR=23). Đăng ký vào MessageHandler như câu cá.
5. **Điểm vào**: ô "Mini game" trong menu burger (icon tauchien.png
   của gốc) — vị trí cửa mini-game trên map park của gốc là dữ liệu
   server không trích được.
6. **Mã bàn riêng**: 4 chữ số suy từ id bàn; khán giả không xem được
   bàn riêng/bàn giao hữu (theo Room.isPrivate của gốc).
7. **Asset thiếu trong bundle** (Bt_Function_Accept/Move, BG_3,
   icnStatus_3/4): nút xác nhận dùng Button_bg3 + chữ; vẫn ghi nợ.
8. **Hạn lời mời** 60 giây; khóa chống spam giữ đúng 4263ms [GỐC].
9. Kiểm chứng: `core/src/test/python/live_tauchien_test.py` PASS
   toàn bộ (tạo/vào/xem/bắn/đổi lượt/END/mời giao hữu/giải tán) +
   harness stage 107–112.
