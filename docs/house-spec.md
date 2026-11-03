# Đặc tả hệ NHÀ (House) — AvatarDK gốc Pack5 → hướng cài cho bản port Java/LibGDX

> Nguồn trích dẫn (chỉ đọc, không sửa):
> - `[CL]` = client C# `project-client-avt-main/Assets/Scripts/Assembly-CSharp/`
> - `[SV]` = server Java `Avatar-Sv-master-master/src/main/java/`
> - `[SQL]` = `Avatar-Sv-master-master/database/avatar_2x.sql`
> - Mọi khẳng định đều kèm `file:dòng`. Số cmd là **sbyte** trên dây (khớp hệ
>   khung `[cmd 1B][len 2B BE][payload]` bản port đã dùng).

## 0. Kiến trúc tổng quan

- Nhà **không phải một map trong maps.json**. Nội thất nhà là một *map động*:
  lưới ô (`map_data`) lưu theo từng người chơi trong DB, server gửi xuống khi
  vào nhà (gói −65). Client dựng cảnh bằng **atlas gạch riêng của nhà** (gói
  −73, file `res/hd/house/tile.png`), không dùng atlas tile phố cũng không
  dùng imagemap.
- Phía client, màn hình nhà là `HouseScr` ([CL] `HouseScr.cs`, 2264 dòng);
  handler gói khi ở nhà là `HomeMsgHandler` (handler **11** qua GET_HANDLER
  −1 {byte 11} — [SV] `Session.java` switch getHandler, case 11 →
  `new HomeMsgHandler(this)` ~dòng 322; [CL] `GlobalLogicHandler.cs:467-477`).
- Phía server, logic nhà nằm ở `[SV] avatar/service/HomeService.java` +
  `Session.joinHouse/BuyHouse/doJoinHouse4`. Handler nhà phía server
  (`[SV] avatar/message/HomeMsgHandler.java:25-57`) chỉ hứng 8 cmd:
  −74, −71, −67, −66, −46, −43, −87, −89, −90 (xem bảng §2). Các cmd nhà còn
  lại (−65, −104, −73, −40, −41) nằm ở handler gốc `MessageHandler.java` nên
  gửi lúc nào cũng tới.
- Đồ nội thất **không phải part** trong items.json. Chúng thuộc catalog riêng
  `MapItemType` (70 loại) — xem §5. Đây là điểm khác biệt quan trọng nhất so
  với shop quần áo.

## 1. Luồng vào nhà

### 1.1. Điểm chạm trên map phố nhà (client map 22)

Map 22 là phố trước nhà (npcs.json của port: NPC 857 đứng ở map này, x 598
y 24; npcs ghi `map: 21` = id server). Trên lưới map 22 có các ô cổng:

| Type ô | Hằng ([CL] `LoadMap.cs`) | Hành động khi bước vào (`LoadMap.doJoin`) |
|---|---|---|
| 68 | `T_HOUSE_1` (dòng 178) | `HouseScr.doJoinFriendHome(typeMap − 67)` → nhà cấp 1 (`LoadMap.cs:1469-1473`) |
| 69 | `T_HOUSE_2` (dòng 180) | như trên → nhà cấp 2 |
| 70 | `T_HOUSE_3` (dòng 182) | như trên → nhà cấp 3 |
| 110 | `T_HOUSE_4` (dòng 262) | `AvatarService.doJoinHouse4(IDDB của mình)` → gói **−104 {int id}** (`LoadMap.cs:1474-1477`, `AvatarService.cs:314`) |
| 21 | `T_ROAD_HOUSE` (dòng 130) | `HouseScr.doOut()` — ra khỏi nhà (`LoadMap.cs:1466-1468`) |

`doJoinFriendHome(houseType)` ([CL] `HouseScr.cs:1962`):
- Nếu cấp nhà của chính mình (`avatar.typeHome`) bằng houseType hoặc chưa có
  (−1): mở menu 2 mục — **"Về nhà"** → `doJoinHouse(IDDB của mình)`
  (commandActionPointer case 0, `HouseScr.cs:1978-1983`) và **"Vào nhà bạn"**
  → `onRoadFriend()` (danh sách bạn có nhà cùng cấp, §8).
- Nếu khác cấp: đi thẳng `onRoadFriend()`.

### 1.2. Chuỗi gói vào nhà mình / nhà bạn (cùng một đường)

```
Client                                          Server
  │  GET_HANDLER −1 {byte 11}  ───────────────►  Session.getHandler → HomeMsgHandler
  │  JOIN park 50 {byte 21, byte 0, short x, short y}   (rời map hiện tại,
  │     doJoinPark(21, 0) — [CL] GlobalLogicHandler.cs:470)
  │  GET_TYPE_HOUSE −67 {byte 0} ─────────────►  HomeService.getTypeHouse
  │  ◄──────────────  −67 {byte 0, short verTile, byte houseType}
  │  (nếu verTile ≠ bản cache RMS "avatarTileMap")
  │  GET_TILE_MAP −73 {} ─────────────────────►  AvatarService.getTileMap
  │  ◄──────────────  −73 {short wNum=21, int len, byte[] PNG tile.png}
  │  JOIN_HOUSE −65 {int idUser} ─────────────►  Session.joinHouse
  │  ◄──────────────  −65 {byte houseType, int idUser, short n, n×byte map,
  │                        byte w, short nItem, item×{short id, byte x,
  │                        byte y, byte dir}, listPlayer}
  │  HouseScr.onJoin(...) → dựng cảnh, switchToMe
```

Chi tiết từng bước:

1. **Đổi handler**: vào nhà luôn bắt đầu bằng GET_HANDLER 11 khi
   `MapScr.idHouse != −1` (idHouse = id chủ nhà đích: nhà mình, nhà bạn được
   chọn, hoặc người mời — [CL] `MapScr.cs:1441` field, gán ở
   `MapScr.cs:1661` khi nhận lời mời). Client đồng thời gửi JOIN park 50 vào
   "room 21" để rời map cũ ([CL] `GlobalLogicHandler.cs:467-477`).
   Sau khi join park trả về mà `LoadMap.TYPEMAP == 21` (tức server map 21 =
   client map 22), client cũng tự gọi `getTypeHouse(0)` +
   `HomeMsgHandler.onHandler()` ([CL] `GlobalMessageHandler.cs:467`).
2. **GET_TYPE_HOUSE −67 {byte type}**: type 0 = hỏi nhà mình, type 1 = hỏi
   danh sách bạn (xem §8). Trả lời S→C −67 do client đọc tại
   [CL] `HomeMsgHandler.cs:101-121`: `{byte type}`; nếu type == 0:
   `{short verTile, byte houseType}`; nếu type != 0: `{short n, n×{int IDDB,
   byte typeHome}}`. → `HouseScr.onGetTypeHouse` (`HouseScr.cs:1870`).
   ⚠️ Phía server fan chỉ implement nhánh type 0 và **hardcode**
   `verTile = 6299, houseType = 3` cho mọi người chơi
   ([SV] `HomeService.java:80-90`) — nó thậm chí không đọc hết ý nghĩa của
   byte type gửi lên ngoài việc in log.
3. **Atlas gạch −73**: client cache atlas trong RMS `avatarTileMap` dạng
   `{short ver, short numW, byte[] PNG}` (`HouseScr.cs:1623-1672`
   loadTileMap/saveTileMap). Nếu ver lệch → gửi −73 {} (rỗng,
   `AvatarService.cs:251` doGetTileMap) và **hoãn join** cho tới khi tải
   xong: sau `saveTileMap`, nếu `MapScr.idHouse != −1` thì mới
   `doJoinHouse(idHouse)`. Server trả −73 `{short 21, int len, PNG}` đọc từ
   file `house/tile.png` dưới thư mục res ([SV] `AvatarService.java:683-700`
   — ghi chú: code ghi `"house/tile.png"` nối với resources path; file thật
   trong Pack5 nằm ở `res/hd/house/tile.png` và `res/medium/house/tile.png`).
   Số 21 = `numW`: ngưỡng phân loại ô — tile index **< 21 = sàn, ≥ 21 =
   tường** (xem §4).
4. **JOIN_HOUSE −65 {int idUser}** (`AvatarService.cs:154-167` doJoinHouse):
   idUser là **chủ nhà đích** (mình hoặc bạn). Server
   `Session.joinHouse` ([SV] `Session.java:1956-2017`):
   - Đọc `house_buy` theo **userId trong gói** → `map_data` (JSON mảng byte).
   - Đọc `house_player_item` theo userId đó → danh sách nội thất.
   - `user.getZone().leave(user)` (rời zone hiện tại; **không** join zone
     chung nào cho nhà — hệ quả ở §8/§9).
   - Trả −65: `{byte houseType = 3 (hardcode), int id = ID CỦA CHÍNH NGƯỜI
     VÀO (không phải chủ nhà!), short n = map_data.length, n byte map,
     byte w = 28 (hardcode), short nItem, mỗi item: short itemId, byte x,
     byte y, byte rotate}`.
   - Client đọc −65 tại [CL] `HomeMsgHandler.cs:48-72`: houseType (byte),
     idUser (int), n (short) rồi **n byte đọc bằng readByte (sbyte)** thành
     mảng tile, w (byte), nItem (short), mỗi item `{short typeID,
     byte xCell, byte yCell, byte dir}` với **x = xCell × 24, y = yCell ×
     24** (toạ độ pixel theo ô 24), cuối cùng `readListPlayer(msg)`.
   - ⚠️ Server fan **không ghi listPlayer** vào −65. Client vẫn an toàn vì
     `readListPlayer` ([CL] `GlobalMessageHandler.cs:1392`) bọc try/catch:
     hết dữ liệu → trả danh sách rỗng. Kết quả thực tế: **vào nhà không thấy
     người chơi khác** (kể cả chủ nhà khi thăm bạn).
   - Nếu userId chưa mua nhà (không có dòng `house_buy`): server **im lặng
     không trả gói nào** — client treo ở wait dialog. Port cần xử lý trường
     hợp này (ít nhất trả dialog "chưa có nhà").

### 1.3. Ra khỏi nhà

- Menu nhà → "Thoát" (commandActionPointer case 13) hoặc ô type 21 →
  `HouseScr.doOut()` (`HouseScr.cs:2167`): xoá cache rương,
  `ParkService.doJoinPark(21, 0)` (JOIN 50 {21, 0, x, y}) +
  `LoadMap.rememMap = −1`. Tức thoát nhà = join lại server map 21 (client
  map 22, phố nhà).
- Ngoài ra khi ở trong nhà, các gói di chuyển/chat dùng lại cmd park 54/55
  và 76/77 ([CL] `HomeMsgHandler.cs` case 51/54/55/76/77 → chuyển
  ParkMsgHandler/GlobalMessageHandler xử lý chung).

## 2. Bảng gói tin hệ nhà (đầy đủ)

### 2.1. Client → Server

| Cmd | Tên (Cmd.java [SV]) | Payload | Gửi từ (client) | Server xử lý |
|---|---|---|---|---|
| −1 {11} | GET_HANDLER | byte 11 | GlobalLogicHandler khi vào nhà | Session.getHandler → HomeMsgHandler ([SV] Session.java ~322) |
| −65 | JOIN_HOUSE | int idUser (chủ nhà đích) | `AvatarService.cs:154` | `MessageHandler.java` case JOIN_HOUSE → `Session.joinHouse` (1956) |
| −104 | JOIN_HOUSE_4 | int id (của mình) | `AvatarService.cs:314` | `MessageHandler.java:24` → `Session.doJoinHouse4` — **STUB** (1053) |
| −67 | GET_TYPE_HOUSE | byte type (0 mình / 1 bạn) | `AvatarService.cs:186` | `HomeService.getTypeHouse` (80) — trả lời hardcode |
| −73 | GET_TILE_MAP | (rỗng) | `AvatarService.cs:251` | `MessageHandler` case GET_TILE_MAP → `AvatarService.getTileMap` (683) |
| −43 | GET_IMG_OBJ_INFO | (rỗng) | `AvatarService.cs:111` | `HomeService.getImgObjInfo` (178) — danh sách gạch lát, hardcode |
| −46 | CREATE_HOME | short type (0 hỏi / 1 xác nhận), short n, n×byte map mới, short 0 (đuôi) | `AvatarService.cs:117` | `HomeService.createHome` (115) |
| −74 | BUY_ITEM_HOUSE | short typeID, byte xCell, byte yCell, byte type (=1 xu / 2 lượng — trường `MapItem.type` do `Canvas.getTypeMoney` chọn) | `AvatarService.cs:137` | `HomeService.buyItemHouse` (30) |
| −71 | SORT_ITEM_HOUSE | short anchor (= typeID), byte x, byte y (ô cũ), byte x2, byte y2 (ô mới), byte dir | `AvatarService.cs:235` | `HomeService.sortItemHouse` (58) |
| −66 | DEL_ITEM_HOUSE | short typeID, byte xCell, byte yCell, byte dir | `AvatarService.cs:171` (dodelItem) | `HomeService.delItemHouse` (92) |
| −75 | SET_PASS_MY_HOUSE | byte type, UTF pass; nếu type==1 thêm int idUser | `AvatarService.cs:253` | **không có case** phía server fan (xem §9) |
| −69 | (kick khỏi nhà) | int iddb người bị kick | `AvatarService.cs:203` | **không có case** phía server fan |
| −68 | INVITE_TO_MY_HOME | byte type, int iddb người được mời | `ParkService.cs` doInviteToMyHome | **không có case** phía server fan (chỉ có hằng Cmd.java:43) |
| −87 | CUSTOM_CHEST | (rỗng) | `GlobalService.cs:656` | `HomeService.onCustomChest` (229) |
| −89 | TRANS_PART_CHEST | byte chiều (0: rương đồ → rương nhà; 1: ngược lại — theo comment server), short index, short idPart | `GlobalService.cs:678` | `HomeService.transPartChest` (256) |
| −88 | PASS_CHEST | byte 0 + UTF pass (nhập mật khẩu); hoặc byte 1 + UTF pass cũ + UTF pass mới (đổi mật khẩu) | `GlobalService.cs:641, 662` | **không có case** trong HomeMsgHandler/MessageHandler của fan |
| −90 | UPGRADE_CHEST | byte 0 (hỏi giá) / byte 1 (xác nhận) | `GlobalService.cs:626` | `HomeService.upgradeChestHome` (315) |
| −107 | SHOP_HOUSE | byte typeShop, short idItem | `GlobalService.cs:930` | **không có case** phía server fan |
| −40 | MAP_ITEM_TYPE | (rỗng — gói xin dataset) | luồng dataset đầu phiên | `AvatarService.getMapItemTypes` (735): trả nguyên file `res/data/map_item_type.dat` |
| −41 | MAP_ITEM | (rỗng — gói xin dataset) | luồng dataset đầu phiên | `AvatarService.getMapItems` (722): trả nguyên file `res/data/map_item.dat` (file 2 byte = rỗng) |

### 2.2. Server → Client

| Cmd | Payload | Client đọc tại |
|---|---|---|
| −67 (type 0) | byte 0, short verTile, byte houseType | `HomeMsgHandler.cs:101-121` → `HouseScr.onGetTypeHouse` |
| −67 (type 1) | byte 1, short n, n×{int IDDB, byte typeHome} | như trên |
| −73 | short wNum (21), int len, byte[] PNG atlas gạch | `HomeMsgHandler.cs:127-134` → `HouseScr.saveTileMap` |
| −65 | byte houseType, int idUser, short n, n×sbyte tile, byte w, short nItem, item×{short typeID, byte xCell, byte yCell, byte dir}, [listPlayer] | `HomeMsgHandler.cs:48-72` → `HouseScr.onJoin` (1484) |
| −43 | short n, n×{UTF name, int priceXu, int priceLuong} | `HomeMsgHandler.cs:34-43` → `HouseScr.onGetTileInfo` |
| −46 | short type (0 = hỏi xác nhận, 1 = thành công, 2 = huỷ/trả map cũ), UTF thông báo | `HomeMsgHandler.cs:85-89` → `HouseScr.onCreateHome` (1760) |
| −74 | short itemId, byte x, byte y — **chỉ gửi khi INSERT DB thành công; client KHÔNG có case −74** trong HomeMsgHandler.cs (client đã tự thêm item vào cảnh từ trước khi gửi — `IActionBuyItem.perform` gọi doBuyItemHouse rồi `doSelectedItem` lại; `HouseScr.onBuyItemHouse` (859) là đường cộng item khi nhận phản hồi qua cơ chế khác) | server `HomeService.java:47-55` |
| −66 | short itemId, byte x, byte y (ô, **client đọc thô không ×24** rồi khớp item theo ô — `HomeMsgHandler.cs:92-97`, `HouseScr.onRemoveItem` 1822) | server `HomeService.java:104-111` |
| −87 | short n1, n1×{short idPart, byte time, UTF expire}, int moneyOnChest, byte levelChest, short n2, n2×{short idPart, byte time, UTF expire} | `GlobalMessageHandler.cs:882-908` → `HouseScr.onCustomChest` (2175) |
| −89 | boolean ok, UTF thông báo lỗi | `GlobalMessageHandler.cs:912` → `HouseScr.onTransChestPart` |
| −88 | (rỗng) — yêu cầu nhập mật khẩu rương | `GlobalMessageHandler.cs:909` → `HouseScr.onEnterPass` |
| −90 | byte index, UTF câu hỏi/nội dung | `GlobalMessageHandler.cs:409-416` → `GlobalLogicHandler.onUpdateCHest` → hộp xác nhận → client gửi lại −90 {1} |
| −75 | int idUser — yêu cầu đặt mật khẩu nhà (mở bàn phím) | `HomeMsgHandler.cs:142-147` |
| −107 | byte loại shop, UTF tên shop, short n, n×{...} (2 biến thể b==0/b==1) | `GlobalMessageHandler.cs:1179+` → `HouseScr.onOpenShop` — **server fan không gửi gói này** (không có handler −107) |

## 3. Mua biệt thự — NPC 857 Chủ đầu tư

- NPC 857 `chu.dau.tu` đứng ở client map 22 (port npcs.json: map 21 server,
  x 598, y 24, items [2403]).
- Menu gốc: đúng **1 mục "Mua biệt thự"** → `us.session.BuyHouse()`
  ([SV] `NpcHandler.java` case `CHU_DAU_TU` ~dòng 486-499; menu mở qua
  gói −59 như mọi NPC).
- `Session.BuyHouse()` ([SV] `Session.java:2019-2072`):
  1. Đã có dòng trong `house_buy` → dialog "Bạn đã mua nhà rồi !".
  2. Xu < **1.000.000** → dialog "Bạn không đủ tiền để mua nhà !".
  3. Trừ 1.000.000 xu, INSERT `house_buy (user_id, type = 3, map_data =
     <chuỗi JSON mặc định>, date_expired = "2000-01-01")`, dialog "Bạn đã
     mua nhà thành công!".
- Bảng giá tham chiếu trong DB ([SQL] bảng `house`, 3 dòng):
  (1, 'Nhà Thuê', 20000, expire 30 ngày), (2, 'Nhà Phố', 500000, −1),
  (3, 'Biệt Thự', 1000000, −1). Nhưng **BuyHouse của fan hardcode cấp 3 +
  giá 1.000.000** — không có luồng mua Nhà Thuê/Nhà Phố, không kiểm tra hạn
  thuê (`date_expired` ghi "2000-01-01" = đã hết hạn ngay, và không nơi nào
  đọc lại cột này). "Các cấp nhà" vì vậy chỉ tồn tại ở phía client
  (typeHome 1/2/3 = 3 kiểu cổng trên map 22 + typeHome 4 = nhà loại 4 qua
  −104), server fan luôn cấp nhà type 3.
- `map_data` mặc định: **532 số = 28 cột × 19 hàng** (đếm trực tiếp từ chuỗi
  trong `Session.java:2041-2045`), giá trị tile 0..40, hàng cuối có 4 ô tile
  **40** (= frame cuối của atlas → ô cửa ra, xem §4).

## 4. Bên trong nhà: lưới, tile, spawn, thoát

`HouseScr.onJoin(houseType, idUser, map[], w, items, players)`
(`HouseScr.cs:1484-1600`):

- `LoadMap.wMap = w` (28 từ server), `Hmap = map.length / w` (19 với nhà
  mặc định). `LoadMap.map = map[]` (tile index từng ô).
- **Loại ô suy từ tile**: tile < numW (21) → type **80** (sàn, đi được);
  tile ≥ 21 → type **88** (tường, chặn) (`HouseScr.cs:1517-1530`).
- **Ô cửa ra**: quét hàng cuối (hàng Hmap−1): ô nào có tile ==
  `atlasHeight / (24 × hd) − 1` (frame cuối của atlas; atlas HD 48×1970 →
  frame 48×48, tổng 41 frame, index cuối = **40**) thì tile được thay bằng
  tile của ô ngay trên nó và type đặt **21 (T_ROAD_HOUSE)** → bước vào là
  thoát nhà (§1.3). Điểm spawn `posJoin = (trung tâm dải ô cửa × 24,
  Hmap × 24 − 30)`.
- `LoadMap.load(68 + typeHome)` (typeHome 4 → load(111)): chỉ để đặt
  TYPEMAP/bối cảnh — lưới thật luôn là `map[]` từ server, Pack5 không có
  file `Resources/map/69|70|71` tương ứng.
- Camera: `AvCamera.init(70 + typeHome)`; nhạc nền nhà: SoundManager BG 83
  (`HouseScr.cs` switchToMe).
- Menu trong nhà (chỉ khi `idHouse == IDDB của mình`, `HouseScr.cs:602`
  doMenuHouse): **Rương** (cmd 6 → −87), **Sửa nhà** (cmd 7 → menu Mua đồ /
  Lát gạch / Bán đồ), **Kick** (cmd 8, chỉ hiện khi có người khác trong
  nhà), **Đặt mật khẩu nhà** (cmd 9 → bàn phím → −75 {0, pass}), **Thoát**
  (cmd 13). Vì bug idUser ở §1.2, trên bản fan khách thăm nhà cũng thấy
  menu chủ nhà.

## 5. Nội thất (MapItemType / MapItem)

### 5.1. Catalog — không nằm trong items.json

- Catalog là dataset **`map_item_type`** xin bằng gói **−40** lúc đầu phiên;
  server trả nguyên file `res/data/map_item_type.dat` (3.063 byte trong
  Pack5 — [SV] `AvatarService.java:735-746`). Client giải mã tại
  [CL] `AvatarData.cs:499-537` (readMapItemType) và cache RMS:
  `short count`, mỗi loại: `short idType, UTF name, UTF des, short imgID,
  short iconID, byte dx, byte dy, short priceXu (32767 → −1; khác −1 thì
  ×1000), short priceLuong, byte buy, byte nPos, nPos×{byte x, byte y}`
  (footprint `listNotTrans` — các ô phụ bị chiếm).
- Giải mã thực tế file .dat của Pack5: **70 loại**, phân bố `buy`:
  0 → 3 loại (không mua được — đồ sự kiện/đặc biệt), 1 → 34, 2 → 8 (treo
  tường), 3 → 20, 4 → 4 (tường), 6 → 1. Tên có dạng `"Nhóm:Tên"` (vd
  `"giường:nệm xanh"`, `"tủ:tủ quần áo"`, `"bàn ghế:bàn học"`) — client
  tách nhóm theo dấu `:` để dựng menu mua (`HouseScr.cs:730-800`).
  Ví dụ: id 3 giường nệm xanh imgID 923, giá 60 (→ 60.000 xu) / 20 lượng,
  buy 1. id 69 "cây tình yêu" buy 6.
- Nguồn song song phía server: bảng DB `map_item_type` (cột id, name,
  description, image, icon, price_coin, price_gold, buy, dx, dy, position
  JSON) được `GameData.loadMapItemType` nạp ([SV] `GameData.java:121-158`)
  nhưng luồng −40 thực tế dùng file .dat. Bảng `map_item` (vật thể đặt sẵn
  trên map công cộng) + file `map_item.dat` trong Pack5 **rỗng (2 byte)**.
- **Phân biệt với quần áo**: part mặc trên người có `type/zorder/gender`
  trong items.json (5.496 part) và đi qua shop −49/−24; MapItemType không
  có các trường đó, chỉ có imgID/iconID/buy/dx/dy/footprint và **không bao
  giờ vào rương đồ thường**. Trong nhà còn 2 id đặc biệt phía client:
  `IDHo = 68` (hồ — setType cho type ô 112), `IDHoa = 69` (hoa tình yêu —
  type ô 111, cấm bán) (`HouseScr.cs` khai báo field + `setType` 1843).

### 5.2. Mua & bày

Luồng mua (menu Sửa nhà → Mua đồ, `HouseScr.cs:730-870`):
1. Chọn nhóm → chọn món (CommandItem hiện icon + giá) → món được "cầm"
   theo con trỏ ở chế độ `isTranItemBuy`, kéo tới vị trí đặt.
2. Kiểm tra đặt được (`isDisable`, `HouseScr.cs:802`):
   - Đồ sàn (buy ≠ 2, 4): ô đích và mọi ô footprint phải có type **80**.
   - Đồ tường (buy = 2 hoặc 4): ô đích type 80 **và ô ngay trên type 88**
     (treo lên tường); không được trùng ô với món cùng loại đã đặt.
3. Chọn tiền qua `Canvas.getTypeMoney(priceXu, priceLuong, ...)` → type 1
   (xu) hoặc 2 (lượng) → `AvatarService.doBuyItemHouse` gửi **−74**
   {typeID, xCell, yCell, type} (`HouseScr.cs:849-858`, `AvatarService.cs:137`).
4. Server `buyItemHouse` ([SV] `HomeService.java:30-56`): **trừ cứng 2.000
   xu** bất kể món/giá/loại tiền (không đọc giá catalog, không trừ lượng),
   INSERT `house_player_item (user_id của người gửi, house_item_id, x, y)`
   — **không lưu rotate**; thành công thì trả −74 {itemId, x, y} (client
   không có case −74 — item đã được client tự cộng vào cảnh; xem bảng §2.2).

### 5.3. Di chuyển / xoay / thu hồi ("bán")

- Chọn món trong nhà (commandAction case 11/12/13, `HouseScr.cs` ~2050):
  **Di chuyển** (kéo thả; khi thả hợp lệ → **−71 SORT** {anchor = typeID,
  ô cũ, ô mới, dir}), **Xoay** (đổi `dir` 0 ↔ 2 rồi gửi −71 với ô cũ = ô
  mới), **Bán** (hộp hỏi → **−66 DEL** {typeID, xCell, yCell, dir}).
- Server: `sortItemHouse` UPDATE dòng khớp (user_id, house_item_id, x, y)
  → set x, y, rotate ([SV] `HomeService.java:58-78`) — **không trả gói
  nào**. `delItemHouse` DELETE dòng khớp rồi trả −66 {itemId, x, y}
  ([SV] `HomeService.java:92-113`) — **xoá hẳn, không hoàn tiền** (nút "Bán"
  là thu hồi vĩnh viễn).
- Vị trí: theo **ô 24×24** (toạ độ lưu là chỉ số ô; pixel = ô × 24), không
  phải pixel tự do. Khi join, món ở (0,0) được client tự rải vào ô sàn
  trống đầu tiên và gửi −71 để chốt (`HouseScr.cs:1547-1575`).

### 5.4. Lưu trữ & vẽ

- Lưu trữ: bảng `house_player_item (id, user_id, house_item_id, x, y,
  rotate)` ([SQL] dòng 75970) — mỗi món một dòng; nhà (gạch) ở
  `house_buy.map_data` (§6). Rương nhà là cột `players.chests_home` (JSON)
  + `chest_home_slot` — khác hẳn kho nội thất đã bày.
- Vẽ: MapItem.paint ([CL] `MapItem.cs:52-85`): ảnh lấy theo **imgID** của
  MapItemType qua hệ imgdata (`AvatarData.listImgInfo[imgID].paintPart`
  hoặc `getImgIcon(imgID)` — cùng cơ chế ảnh theo id mà farm của port đang
  dùng), vẽ tại `(x + dx, y + dy)` tính theo đơn vị medium; `dir` là
  transform lật (0 = thường, 2 = lật ngang). Thứ tự vẽ: `LoadMap.treeLists`
  được `orderVector` sắp theo y (vật ở dưới vẽ sau — cùng luật sắp xếp vật
  thể map của gốc). Đồ treo tường được cộng `y + 1` pixel khi hiển thị
  (`HouseScr.cs` isSetTuong/onBuyItemHouse).
- `setType` khi đặt món (`HouseScr.cs:1843`): ghi type chặn cho các ô
  footprint: mặc định **88**; idType 68 (hồ) → **112**; idType 69 (hoa) →
  **111**; iconID 1 → **79** (ghế — ngồi được); iconID 2 → **67** (nằm
  nghỉ). Thu hồi món thì `removeTrans` trả các ô footprint về 80 nếu không
  còn món nào khác chung ô.

## 6. Lát gạch (đổi sàn/tường) — CREATE_HOME −46

- Menu Sửa nhà → Lát gạch: client xin danh sách gạch bằng **−43** (rỗng);
  server `getImgObjInfo` trả −43 với **41 Tile hardcode** trong code (tên
  mã hoá kiểu "BT", "VH", "VS1", "GN", "ctXD"...; giá xu/lượng từng loại,
  −1 = không bán) ([SV] `HomeService.java:178-227`). Danh sách này khớp 41
  frame của atlas (tile index 0..40).
- Người chơi chọn gạch (chỉ thay ô **cùng nhóm**: sàn < numW thay sàn, tường
  ≥ numW thay tường — `doBrick`/`updateKeyBuyTile`, `HouseScr.cs:868`),
  sửa trực tiếp trên `LoadMap.map` cục bộ, bấm Hoàn thành → nếu có thay
  đổi: gửi **−46 type 0** {short 0, short n, n byte map, short 0}.
- Server `createHome` ([SV] `HomeService.java:115-176`): so map mới với
  `house_buy.map_data`, gom ô thay đổi (**bỏ qua các ô index 519..522** —
  4 ô cửa ra ở hàng cuối, không cho lát lại), nếu type == 0 → trả −46
  {0, "Bạn cần 2000 xu và 0 lượng để lát gạch. Bạn có đồng ý không ?"};
  client hiện hộp Yes/No (commandTab case 50/51, `HouseScr.cs` ~1700):
  Yes → gửi lại **−46 type 1** → server lưu map mới, **trừ 2.000 xu**, trả
  −46 {1, "Bạn đã lát gạch thành công và tốn 2000 xu và 0 lượng."};
  No → client khôi phục map cũ (biến temp).

## 7. Rương trong nhà — và đối chiếu panel rương của bản port

### 7.1. Luồng gốc

- Mở: menu nhà → Rương (cmd 6) → **−87 {}** → server `onCustomChest`
  ([SV] `HomeService.java:229-254`) trả −87 gồm **hai danh sách**:
  (1) `user.chests` (rương đồ thường — server ghi chú bỏ qua item id 40
  nhưng lại ghi count = size đầy đủ → **bug lệch count** nếu người chơi có
  item 40), (2) `user.chestsHome` (rương nhà); kèm `int moneyOnChest = 0`
  (hardcode) và `byte levelChest = 1` (hardcode). Client dựng PopupShop 2
  tab "Giỏ / Rương" (`HouseScr.onCustomChest`, 2175).
- Chuyển món: **−89** {byte chiều, short index, short idPart} → server
  `transPartChest` (256-313): kiểm tra trùng 2 phía, hết hạn/số lượng > 1
  bị chặn (chiều 0), còn slot (`getChestHomeSlot()` / `getChestSlot()`) →
  chuyển list trong bộ nhớ (lưu DB khi ngắt kết nối như các list khác) →
  trả −89 {boolean ok, UTF "Rương nhà đã đầy !" / "Rương đồ đã đầy!"}.
  Lưu ý chiều: comment server ghi 0 = chest → chestHome; code nhánh 0 tìm
  item trong `chests` và chuyển sang `chestsHome`.
- Nâng cấp rương nhà: **−90 {0}** hỏi → server trả −90 {0, UTF câu hỏi kèm
  giá} → xác nhận **−90 {1}** → trừ **cả xu lẫn lượng**, `chest_homeSlot
  += 5`. Bảng giá/slot hardcode trong `HomeService.java:315-362`:
  slots {10,15,20,...,55}, xu {10000, 20000, 50000, 100000, 200000,
  500000, 1000000, 2000000, 3000000, 5000000}, lượng {10, 20, 30, 40, 50,
  100, 200, 500, 800, 1200}. Cấp suy từ số slot: `User.getChestLevel()`
  ([SV] `User.java:514-541`).
- Mật khẩu rương: **−88** — server fan **không có handler** (xem §9) dù
  client có đủ hai chiều (S→C −88 rỗng → mở bàn phím nhập pass; C→S −88
  {0, pass} hoặc {1, pass cũ, pass mới}).

### 7.2. Đối chiếu với bản port hiện tại

Panel rương 2 phía của port đang chạy bằng gói **−47 (CONTAINER) / −48
(USING_PART) / −36 (REMOVE_ITEM)** — đó là bộ gói rương **ngoài nhà**
(Cmd.java:62-63, 72 — luồng container của AvatarMsgHandler ở park/thành
phố), cộng **−87/−89** đã nối sẵn. Kết luận đối chiếu:

- Trong nhà, gốc **chỉ dùng −87/−89/−90** (mở qua menu nhà cmd 6), không
  dùng −47/−48/−36. Ba gói −47/−48/−36 không thuộc HomeMsgHandler hai
  phía.
- Vì −87/−89 có format y hệt ở mọi ngữ cảnh (HomeService là nơi duy nhất
  phía server implement chúng), panel rương hiện tại của port **dùng lại
  được nguyên trạng** cho đợt nhà: chỉ cần thêm điểm mở trong nhà + nút
  nâng cấp −90. Điểm mở rương trong nhà theo chốt của dự án là **mở trong
  nhà ở đợt Nhà** (không có ô Rương trong menu burger) — khớp với gốc.

## 8. Nhà bạn bè, lời mời, mật khẩu nhà, kick

- **Danh sách bạn có nhà**: từ `doJoinFriendHome` → "Vào nhà bạn" →
  `onRoadFriend` (`HouseScr.cs:1909-1960`): nếu chưa có danh sách bạn thì
  xin friend list trước; gửi **−67 {byte 1}** → server phải trả −67 type 1
  {n, {IDDB, typeHome}} — ⚠️ **server fan không implement nhánh type 1**
  (getTypeHouse luôn trả bản type 0 hardcode) → client lọc bạn theo
  typeHome không bao giờ khớp đúng; tính năng thăm nhà bạn **chết ở fan
  server**. Vào được nhà bạn (nếu biết IDDB) vẫn qua −65 {int idBạn} như
  §1.2 — server đọc đúng nhà của bạn đó, nhưng: gói trả về ghi **id của
  khách** (HouseScr tưởng khách là chủ nhà → hiện đủ menu chủ nhà), **không
  có listPlayer** (không thấy chủ nhà), và các thao tác mua/xoá/nâng cấp
  phía server đều áp lên **user_id của người gửi** (mua đồ trong nhà bạn =
  trừ tiền mình và INSERT vào nhà mình). Port khi làm tính năng này cần sửa
  server ở 3 điểm đó nếu muốn đúng ý đồ gốc.
- **Mời vào nhà**: `ParkService.doInviteToMyHome` gửi **−68** {byte type,
  int iddb}; phía nhận, `MapScr.onInviteToMyHome` ([CL] `MapScr.cs:1653`):
  type 0 → hộp "Bạn được mời tới nhà của X. Bạn có muốn?" → đồng ý thì đặt
  `idHouse = idNgườiMời` + GET_HANDLER 11 (đi tiếp theo §1.2). ⚠️ Server
  fan **không có case −68** → lời mời không bao giờ được chuyển.
- **Mật khẩu nhà**: menu nhà → Đặt mật khẩu (cmd 9) → −75 {byte 0, UTF
  pass} (đặt cho nhà mình); server có thể gửi −75 {int idUser} yêu cầu
  client mở bàn phím đặt pass (HomeMsgHandler.cs:142-147, callback gửi lại
  −75 {byte 1, UTF pass, int idUser}). ⚠️ Server fan **không có case −75**
  cả hai chiều → mật khẩu nhà không hoạt động.
- **Kick**: menu nhà (khi có người khác) → chọn người → **−69** {int iddb}
  (`AvatarService.cs:203`). ⚠️ Không có case phía server fan.
- **Nhà cấp 4**: ô type 110 → −104 {int id mình}; server
  `Session.doJoinHouse4` ([SV] `Session.java:1053-1055`) chỉ
  `System.out.println` rồi thôi — **stub toàn phần**; client kỳ vọng sau đó
  vẫn là luồng −67/−65 với houseType 4 (onJoin có nhánh typeHome == 4 →
  load(111)) nhưng không gói nào được gửi.

## 9. Server gốc (fan) — thật / stub / bug

**Có logic thật (dùng được ngay):**
- `BuyHouse` — mua nhà 1.000.000 xu, cấp type 3 + map mặc định (Session.java:2019).
- `joinHouse` — đọc house_buy + house_player_item, đóng gói −65 đúng format client (Session.java:1956).
- `buyItemHouse` — INSERT house_player_item + trả −74 (HomeService.java:30). (Giá sai: hardcode 2.000 xu.)
- `sortItemHouse` — UPDATE vị trí/rotate (HomeService.java:58).
- `delItemHouse` — DELETE + trả −66 (HomeService.java:92).
- `createHome` — lát gạch 2 bước hỏi/xác nhận, lưu map_data, trừ 2.000 xu (HomeService.java:115).
- `getImgObjInfo` — danh sách 41 gạch hardcode khớp atlas (HomeService.java:178).
- `onCustomChest` / `transPartChest` / `upgradeChestHome` — rương nhà (HomeService.java:229/256/315).
- `getTileMap` — trả file atlas + wNum 21 (AvatarService.java:683).
- Dataset −40/−41 — trả file .dat nguyên bản (AvatarService.java:722/735).

**Stub / thiếu / hardcode:**
- `getTypeHouse`: luôn {verTile 6299, houseType 3}; nhánh type 1 (bạn bè) không có (HomeService.java:80).
- `doJoinHouse4` (−104): chỉ in log (Session.java:1053).
- Không có handler: **−68 mời nhà, −69 kick, −75 mật khẩu nhà, −88 mật
  khẩu rương, −107 shop nhà** (Cmd.java có hằng nhưng MessageHandler/
  HomeMsgHandler không có case).
- `joinHouse` không join zone, không gửi listPlayer, ghi sai idUser (id
  người vào thay vì chủ nhà); chưa mua nhà thì im lặng.
- `buyItemHouse` không lưu rotate, trừ cứng 2.000 xu, không kiểm tra
  tiền/giá/loại tiền, INSERT cả khi xu âm.
- `onCustomChest`: count list 1 lệch khi có item id 40; moneyOnChest = 0
  và levelChest = 1 hardcode (không đọc số Xu cất trong rương / cấp thật).
- `upgradeChestHome`: cấp lấy theo `getChestLevel()` (suy từ slot rương
  nhà) nhưng các mảng giá index theo cấp có lệch nhịp (nhánh hỏi dùng
  `nextLevel − 1`, nhánh trừ dùng `currentChestLevel`) — port nên viết
  lại bảng cho khớp thay vì sao chép nguyên xi.
- `createHome`: bỏ qua ô 519..522 khi so sánh (cửa ra) nhưng khi **lưu**
  vẫn ghi toàn bộ map client gửi (kể cả 4 ô đó) — chỉ là không tính chúng
  vào danh sách "đã đổi" để in log.
- `house` table có 3 cấp giá (Thuê/Phố/Biệt Thự) nhưng không code nào đọc.

## 10. Dữ liệu & schema liên quan

| Bảng/cột | Nội dung |
|---|---|
| `house` ([SQL] ~75870) | Catalog cấp nhà: (1, Nhà Thuê, 20000, 30 ngày), (2, Nhà Phố, 500000, −1), (3, Biệt Thự, 1000000, −1) — không được code đọc |
| `house_buy` ([SQL] ~75892) | `id, user_id, type (default 1), map_data longtext JSON mảng byte (CHECK json_valid), date_expired date` — 1 dòng/người |
| `house_player_item` ([SQL] ~75970) | `id, user_id, house_item_id (= idType của MapItemType), x, y (chỉ số ô), rotate` |
| `players.chest_slot / chest_home_slot` | Số ô rương thường / rương nhà; lưu ở `User.java:562-563` khi ngắt kết nối |
| `players.chests_home` | JSON list item trong rương nhà (`User.chestsHome`, User.java:114) |
| `map_item_type` (DB + file .dat) | Catalog nội thất 70 loại (§5.1) |
| `res/hd/house/tile.png` | Atlas gạch nhà: 48 × 1970 px, frame 48×48 → **41 frame** (index 0..40), numW = 21 (0..20 sàn, 21..40 tường/cửa) |

## 11. Đối chiếu completion-audit (đợt 4) & phạm vi tối thiểu

Audit hiện ghi: "Đợt 4 — Nhà/nội thất: HouseScr (vào nhà client 22), mua
biệt thự (857), đặt/bày nội thất (dữ liệu chests_home đã có), rương mở
trong nhà, nội thất tile (map 21, 23, 61–68, 106 — phụ thuộc A1). Asset:
Resources/hd/home/ có sẵn." Đối chiếu với đặc tả này, cần đính chính 2 ý:

1. `chests_home` là dữ liệu **rương nhà** (item cất giữ), **không** phải
   nội thất đã bày — nội thất đã bày nằm ở `house_player_item` +
   `house_buy.map_data`, port chưa có hai bảng này trong luồng chơi (cần
   thêm vào server copy + AdminServer.ensureSchema như các bảng phụ khác).
2. "Nội thất tile (map 21, 23, 61–68, 106)" là các **map phòng shop/nội
   thất công cộng** của engine tile (A1 đã xong) — không liên quan trực
   tiếp tới nhà riêng; nhà riêng dùng atlas `hd/house/tile.png` riêng +
   lưới động từ server.

**Phạm vi tối thiểu "vào được + mua được + bày được + rương mở được":**

| # | Việc | Phía | Ghi chú |
|---|---|---|---|
| 1 | NPC 857 menu "Mua biệt thự" → BuyHouse | server (đã có sẵn trong server copy — kiểm tra đã nối case CHU_DAU_TU) | 1.000.000 xu, type 3, map mặc định 532 ô |
| 2 | Client gửi −1 {11} + −67 {0}; nạp atlas −73 (hoặc đóng gói sẵn tile.png + numW 21 vào assets như các atlas khác) | client | Có thể bỏ qua cơ chế ver/RMS: port nạp thẳng file |
| 3 | Client gửi −65 {ownId}; dựng cảnh nhà từ map_data (28×19), suy type 80/88 theo numW 21, ô tile 40 ở hàng cuối → type 21 + spawn | client | Cần màn/hệ "map động" tương tự FarmWorld; di chuyển/chat dùng lại khung park |
| 4 | Bước vào ô type 68/69/70 trên map 22 → vào nhà mình (đơn giản hoá: bỏ menu bạn bè ở bản đầu); ô 110 để sau (server stub) | client | A3 tryPortal thêm case 68/69/70 → luồng nhà; 21 → doOut |
| 5 | Catalog nội thất: giải mã `map_item_type.dat` (70 loại) → JSON cho client (như farmtrees.json) | data | Ảnh theo imgID dùng cơ chế imgdata sẵn có |
| 6 | Menu Sửa nhà tối thiểu: Mua đồ (chọn nhóm → món → kéo đặt → −74), Di chuyển/Xoay (−71), Thu hồi (−66) | client + server (HomeService đã có) | Server nên sửa giá: trừ theo catalog thay vì 2.000 xu cứng, và lưu rotate khi mua |
| 7 | Rương trong nhà: điểm mở trong nhà → dùng lại panel rương 2 phía của port (−87/−89), thêm nâng cấp −90 | client + server (đã có) | Sửa bug count item-40 trong onCustomChest phía server |
| 8 | Thoát nhà: doOut → join map 22 | client | |

Để sau (không thuộc tối thiểu): nhà bạn bè + mời (−68) + mật khẩu nhà
(−75) + kick (−69) + mật khẩu rương (−88) + nâng cấp nhà/thuê nhà cấp 1/2
+ nhà cấp 4 (−104) + shop nhà (−107) + cất/rút Xu trong rương
(moneyOnChest) — tất cả đều là stub/thiếu phía server gốc, làm sau theo
quyết định riêng.
