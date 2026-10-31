# Đặc tả MAP + NPC + chức năng — AvatarDK gốc (kiểm kê 2026-10-31)

Mục đích: chốt dữ kiện để port khớp 100% toàn bộ map, vị trí NPC và chức năng.
Dữ liệu máy tương ứng: `assets/data/maps.json` (48 map theo **chỉ số load phía client**,
28 NPC đã gán map). Mọi khẳng định dưới đây đều có trích dẫn `file:dòng`.

Nguồn viết tắt:
- **[SV]** = `server/src/main/java/avatar/...` (server Java trong project này)
- **[CL]** = `.../project-client-avt-main/Assets/Scripts/Assembly-CSharp/...` (client C# dịch ngược, Pack5)
- **[DB]** = `server/database/avatar_2x.sql`
- **[RES]** = `.../Assets/Resources/hd/imagemap/<id>/` và `.../Assets/Resources/map/<id>.*`

---

## 0. Kết luận quan trọng nhất: có HAI hệ id map, lệch nhau 1

| Hệ | Là gì | Bằng chứng |
|---|---|---|
| **Server zone id** (0–59) | Server tạo đúng 60 map `new Map(i, 0, 10)` với i = 0..59, mỗi map 10 zone | [SV] `server/ServerManager.java:118-120` |
| **Client load index** (= server id + 1) | Khi server báo vào zone `roomID`, client gọi `Canvas.loadMap.load(roomID1 + 1, true)`; trong `LoadMap.load`, `TYPEMAP = index - 1` | [CL] `MapScr.cs:2018`, `LoadMap.cs:2073-2074` |

Hệ quả:
- Trường `map` trong bảng `npc` là **server zone id** (server nạp thẳng vào `MapManager.find(map)` —
  [SV] `server/ServerManager.java:131-153`), nên **map hiển thị phía client = `npc.map + 1`**.
- Bảng `bg[]` của client tra theo `TYPEMAP = clientIndex - 1 = server id` ([CL] `LoadMap.cs:288-293`, `LoadMap.cs:2001` gọi `loadBG(index - 1)`).
- `maps.json` dùng **client load index** làm `id` (khớp thư mục imagemap + cách project hiện tại đang định nghĩa map 10/24/25).

Kiểm chứng chéo 3 trường hợp:
- NPC quay số/đấu giá có `map = 9` ([DB] `avatar_2x.sql:84183-84184`) → client 10 = "Khu giải trí",
  đúng cảnh thành phố có sạp GAME/quay số/đấu giá mà đợt 1 đã dựng. Client cũng tự gọi
  `ParkService.doJoinPark(9, -1)` cho khu này ([CL] `LoadMap.cs:21,36`).
- NPC tho.kim.hoan `map = 23` ([DB] `avatar_2x.sql:84181`) → client 24 = "Khu mua sắm" (tên ở mục 1).
- NPC lai.buon `map = 25` → client 26: `FarmScr.doGoFarmWay()` gửi `doJoinPark(25, 0)`
  ([CL] `FarmScr.cs:4339`) và `onJoinPark` load index 26 — tức **lai.buon đứng ở ĐƯỜNG RA NÔNG TRẠI
  (map 26), không phải trong nông trại (map 25)**. Đợt 1 đặt lai.buon vào map 25 là sai theo gốc.

Lỗi cần sửa ở đợt 1 (ghi nhận, không sửa trong tài liệu này): bộ lọc NPC theo `npc.map == mapId`
đang lệch 1 cho mọi map online; Chủ Hôn (`map = 10`) thuộc client **11** (Khu lễ đường), không phải 10.

## 1. Danh sách map

### 1.1 Số đếm đo được (đừng dùng con số "33 map / 30 thư mục" nữa — không khớp dữ liệu)

- Server: **60 zone map** id 0–59 ([SV] `ServerManager.java:118-120`).
- Client: **43 file dữ liệu map** trong [RES] `Resources/map/`: id 1–26, 58–68, 101, 102, 104, 105, 106, 108.
- Client: **31 thư mục imagemap HD** (đo trực tiếp trên đĩa, không phải 30): id
  1,2,3,4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,22,24,25,26,58,59,60,65,101,102,104,105,108
  (bản medium chỉ có 29 — thiếu 1 và 60).
- `maps.json` gồm **48 map** = 43 map có file dữ liệu ∪ 5 map chỉ có bằng chứng zone+NPC phía
  server (client 28, 31, 34, 56, 57 — xem 1.4). Không tìm thấy trong code/dữ liệu bất kỳ danh sách
  nào có đúng 33 map; con số 33 trong kiểm kê cũ không truy được nguồn.

### 1.2 Tên map

Nguồn tên duy nhất dạng bảng là `Service.checkNameMap(Map)` phía server, khoá theo **server id**
([SV] `service/Service.java:355-433`). Đổi sang client id = server id + 1:

| Client id | Tên (theo server) | Client id | Tên |
|---|---|---|---|
| 1 | Khu Mặt trời | 12 | Bến xe công viên |
| 2 | Khu quay số cũ | 14 | Khu sinh thái |
| 3 | Khu đấu giá cũ | 15 | Khu câu cá rô |
| 4 | Khu ăn xin trái | 16 | Khu câu cá lóc |
| 5 | Khu cưới , clan | 17 | Khu câu cá mập |
| 6 | Khu ăn xin phải | 18 | Khu ngoại ô |
| 7 | Khu cô giáo | 19 | Trong nhà tù |
| 8 | Khu dưới cô giáo | 20 | Trong lễ đường |
| 10 | Khu giải trí | 24 | Khu mua sắm |
| 11 | Khu lễ đường | | |

Hai tên lấy từ nhãn giao diện client (không có trong `checkNameMap`):
- Client 25 = **"Nông trại"** (nhãn `farm` trong [CL] `HDPaint.cs:961`, `MediumPaint.cs:925`).
- Client 108 = **"Trường đua thú"** (văn bản hướng dẫn [CL] `T.cs:1071`; màn hình `RaceScr`).

Các client id còn lại (9, 13, 21, 22, 23, 26, 28, 31, 34, 56–68, 101, 102, 104, 105, 106)
**không có tên hiển thị trong code** → `name` để rỗng trong maps.json. Riêng:
- Client 22 (server 21): map nhà/biệt thự — `HouseScr` gọi `doJoinPark(21, 0)` ([CL] `HouseScr.cs:2171`),
  NPC chu.dau.tu bán biệt thự ở đây (mục 3).
- Client 25/26 phân biệt rõ: 25 = nông trại của người chơi (vẽ bằng `LoadMap.load(25, false)` từ
  `FarmScr.onJoin`, [CL] `FarmScr.cs:3457`; `TYPEMAP` sau load là 24), 26 = đường ra nông trại
  (server zone 25, `TYPEMAP` 25).

### 1.3 Loại map

- **Có imagemap** (31 id ở 1.1): dựng bằng các dải ảnh `imagemap/<id>/<n>.png` chồng nhau
  (`LoadMap.loadImageMapFull`, [CL] `LoadMap.cs:1938-1963`). Số dải + cờ daydien đã đếm trên đĩa
  và ghi vào maps.json (`strips`, `daydien`). Chỉ 2 map có daydien: **10** (3 file) và **18** (2 file).
- **Có file dữ liệu tile nhưng không imagemap** (12 id): 13, 20, 21, 23, 61, 62, 63, 64, 66, 67, 68, 106.
  Các map này đi đường tile: `LoadMap.load` vẫn đọc lưới ô từ `Resources/map/<index>`
  ([CL] `LoadMap.cs:1924-1935`), còn ảnh ô lấy từ tileset — `loadMapImage` dựng `FrameImage` từ
  `hd/data/h<status>` + `hd/data/data` ([CL] `LoadMap.cs:1851-1898`; riêng index 20 dùng ảnh
  `hd/wedding`), hoặc nhận tileset từ server qua `onTileImg(idTileMap, arr)` rồi `setMapAny()`
  ([CL] `LoadMap.cs:3366-3373`). Đây chính là nhóm "nội thất/tile (idTileImg)".
- **Farm**: client 25 (nông trại riêng từng người chơi, dữ liệu ô/cây/vật nuôi do FarmScr/FarmMsgHandler
  quản, không phải zone park thường) và client 26 (đường ra nông trại, là zone park server 25 thật).
- **Offline**: client 59, 60, 58, 104, 105, 101, 102 — mảng ánh xạ slot→load index nằm ngay trong
  `MapScr.onJoinOfflineMap`: `array = {59, 60, 58, 104, 105, 101, 102}` ([CL] `MapScr.cs:3483-3486`);
  vào bằng `doJoinMapOffline(i)` ([CL] `MapScr.cs:3531-3537`). Riêng 101 là map chào/hướng dẫn
  người mới ([CL] `MapScr.cs:3525-3528`).

### 1.4 Map có zone + NPC phía server nhưng KHÔNG có dữ liệu map phía client trong Pack5

Client 28 (server 27), 31 (server 30), 34 (server 33), 56 (server 55), 57 (server 56):
server vẫn tạo zone và NPC vẫn được nạp vào ([SV] `ServerManager.java:118-120,153`), nhưng
Pack5 không có `Resources/map/<id>` lẫn `imagemap/<id>` cho các id này. Khả năng: map sự kiện/
nội thất mà lưới ô + tileset được server gửi lúc chạy (luồng `onSelectedMiniMap`/`onTileImg`,
[CL] `GlobalMessageHandler.cs:951-1015`, `LoadMap.cs:3366-3373`), hoặc dữ liệu đã thiếu sẵn
trong bộ nguồn này. **Chưa chốt được** — ghi rõ để không port đoán.

## 2. Nền (bg) với map id > 26

Bảng `bg` có đúng 26 phần tử, tra theo `TYPEMAP` 0–25 ([CL] `LoadMap.cs:288-293`).
`loadBG(i)` xử lý ngoài bảng như sau ([CL] `LoadMap.cs:482-490`):

```java
if (i != 107 && (i < 0 || i >= bg.Length || bg[i] == -1)) { rememBg = -1; imgBG = imgTreeBg = imgClound = null; return; }
```

- TYPEMAP 26–106 (client 27–107): **không có ảnh nền** — `paintBackGround` chỉ tô phẳng một màu
  ([CL] `LoadMap.cs:1757-1764`).
- TYPEMAP 107 (client **108**, trường đua): ngoại lệ cứng trong code — `loadBG` bỏ qua kiểm tra
  độ dài và dùng nền số **0** (`num = (i != 107) ? bg[i] : 0`, [CL] `LoadMap.cs:500-503`);
  `loadMapImage` còn đặt riêng `x0_imgBG = 30*hd` cho map này ([CL] `LoadMap.cs:1866-1876`).
  Vì vậy maps.json ghi `bg = 0` cho map 108.
- TYPEMAP có `bg = -1` (client 19, 20, 21): cũng không ảnh nền (cùng nhánh guard trên).
- Trong maps.json: `bg = null` nghĩa là "không có ảnh nền" (gồm cả hai trường hợp -1 và ngoài bảng).

## 3. NPC — bảng đầy đủ 28 NPC

Nguồn dữ kiện: bảng `npc` ([DB] `avatar_2x.sql:84164-84207`) — npcs.json trong project là bản
trích đúng của bảng này. Server nạp NPC vào **tối đa 3 zone đầu** của map; riêng id 186 (đấu giá)
và 864 (Than.Tai.Xiu) chỉ nạp **1 zone đầu** rồi dừng ([SV] `ServerManager.java:157-202`).
Toạ độ x, y giữ nguyên đơn vị medium của DB.

Cột "Chức năng" đọc từ `NpcHandler.handlerCommunicate` ([SV] `handler/NpcHandler.java:232-741`,
switch theo `npcId - Npc.ID_ADD`; hằng id ở [SV] `constants/NpcName.java:12-47`).
NPC **không có case** trong switch = bản server này không mở menu/chức năng nào khi tương tác
(chỉ đứng + câu chat trong DB nếu có).

| NPC id | Tên (DB) | Server map → Client map | x, y | Chức năng khi tương tác |
|---|---|---|---|---|
| 858 | Bạch.vô.thường | 0 → **1** | 60, 65 | Menu sự kiện: Đổi Quà (shop sự kiện), Góp Kẹo, Nhận thưởng đua top, Thành tích bản thân, BXH thả pháo lượng / điểm đánh boss, hướng dẫn (`NpcHandler.java:359-459`, case `bunma`) |
| 859 | Hắc.vô.thường | 0 → **1** | 188, 157 | Menu quà thẻ VIP: Đổi quà VIP PREMIUM / VIP Cao Cấp / VIP, hướng dẫn (`NpcHandler.java:460-487`, case `Vegeta`) |
| 165 | saitama | 2 → **3** | 40, 77 | Quản lý rương: Quản Lý Mặt, Quản Lý Mắt, Xem ô rương trống, Xoá vật phẩm trùng (Mặt+Mắt) (`NpcHandler.java:293-358`) |
| 741 | an.xin | 3 → **4** | 168, 157 | Không có case — không chức năng trong bản server này |
| 645 | co.giao.tieng.anh | 6 → **7** | 150, 100 | Không có case |
| 646 | co.giao.tieng.viet | 6 → **7** | 250, 50 | Không có case |
| 185 | quay số | 9 → **10** | 514, 48 | Quay số may mắn: 5 lượng / 25.000 xu / miễn phí (vé quay số), hướng dẫn (`NpcHandler.java:529-560`, `handleDiaLucky` dòng 43-90) |
| 186 | đấu giá | 9 → **10** | 410, 48 | Chỉ trả dialog "đấu giá đang bảo trì các bạn vui lòng quay lại sau" — đấu giá ngược đang TẮT (`NpcHandler.java:503-506`) |
| 525 | Chủ Hôn | 10 → **11** | 278, 68 | Không có case. Lễ cưới không qua NPC này: chạy bằng cmd 93 `WEDDING_BIGIN` ở map lễ đường ([SV] `message/ParkMsgHandler.java:31-32`, `service/ParkService.java:36`); "chủ hôn" trong phòng lễ (client 20) là avatar cứng phía client, part 2480 tại ô (26,8) ([CL] `LoadMap.cs:2161,2199`) |
| 465 | ve.so | 11 → **12** | 638, 68 | Case `VE_SO` bị comment toàn bộ (`NpcHandler.java:507-528`) → không chức năng |
| 860 | Tien.chi.mu.Lovanga | 11 → **12** | 250, 64 | Menu dự đoán: Dự đoán vị trí người yêu (chạy thật, `Service.DuDoanNY`), xổ số và Tài Xỉu đều trả "Comingsion" (`NpcHandler.java:276-292`) |
| 864 | Than.Tai.Xiu | 11 → **12** | 80, 50 | Mini-game **Tài Xỉu**: cược Tài/Xỉu bằng Xu hoặc Lượng, lịch sử 10 ván, top thắng/thua, thành tích (`NpcHandler.java:233-275`); server đăng ký NPC này làm mặt chat đếm ngược phiên ([SV] `ServerManager.java:178-190`, `minigame/TaiXiu.java:39-43`) |
| 430 | tho.cau | 13 → **14** | 326, 64 | Menu câu cá: mở shop đồ câu (cần câu vip item 446, vé câu 460, mồi 448), **Bán cá**, hướng dẫn (`NpcHandler.java:605-633`) |
| 335 | phi.hanh.gia | 14 → **15** | 40, 49 | Không có case (tên gợi sân bay nhưng DB đặt ở map câu cá rô; không có menu bay trong bản server này) |
| 862 | Pay.To.Win | 17 → **18** | 173, 48 | Shop đổi quà Pay To Win + đổi cả set + Shop Nâng Cấp Pay To Win + hướng dẫn Sen/Đá Ngũ Sắc (`NpcHandler.java:672-714`) |
| 863 | Chay.To.Win | 17 → **18** | 54, 48 | Shop đổi quà Chay To Win (xu) + Shop Nâng Cấp Chay + Shop đổi bằng Đá Ngũ Sắc + hướng dẫn (`NpcHandler.java:715-740`) |
| 315 | giam.thi | 18 → **19** | 274, 101 | Không có case |
| 857 | chu.dau.tu | 21 → **22** | 598, 24 | Menu 1 mục "Mua biệt thự" → `Session.BuyHouse()` (`NpcHandler.java:488-502`) |
| 95 | tho.kim.hoan | 23 → **24** | 686, 52 | **Nâng cấp vật phẩm**: Nâng cấp bằng xu / bằng lượng + hướng dẫn (`NpcHandler.java:561-587`) |
| 850 | lai.buon | 25 → **26** | 70, 45 | Menu Điểm Danh — action hiện chỉ trả "đang xây dựng" + hướng dẫn điểm chuyên cần (`NpcHandler.java:588-604`). Trong farm, lệnh COMMUNICATE cũng được route về NPC này ([SV] `message/FarmMsgHandler.java:119`) |
| 761 | cua.hang | 27 → **28** | 334, 72 | Menu shop "shop noname": mở UI shop id 5 "shop 0" (danh sách `Part.shopByPart(PartManager.getShop0())`) + hướng dẫn (`NpcHandler.java:634-655`) |
| 861 | phu.thuy.bong.toi | 27 → **28** | 438, 72 | Menu Đổi Quà — shop sự kiện 5 item (3672, 5898, 4331, 3112, 4736) + hướng dẫn (`NpcHandler.java:656-671`, case `Shop_Buy_Luong`) |
| 50 | tien.ca | 30 → **31** | 180, 81 | Không có case |
| 760 | pharaoh | 33 → **34** | 240, 49 | Không có case |
| 535 | zombie | 55 → **56** | 402, 40 | Không có case |
| 425 | phu.thuy.bong.toi | 56 → **57** | 244, 197 | Không có case |
| 590 | pretty.witch | 57 → **58** | 200, 201 | Không có case |
| 540 | bac.thay.phu.thuy | 58 → **59** | 252, 221 | Không có case |

Lưu ý đặt tên: hằng `NpcName.bunma = 858`, `Vegeta = 859` ([SV] `NpcName.java:38-39`) nhưng tên
DB của 858/859 là Bạch/Hắc vô thường — khi port theo DB thì giữ tên DB; menu sự kiện Halloween
gắn theo id, không theo tên.

Ngoài bảng `npc`, server còn tự sinh **boss** ở các server map 11, 1, 7, 2, 3, 5, 8 khi khởi động
([SV] `ServerManager.java:236-239`, `Boss.spawnBossesForMap(mapId, 2)`) — boss không phải NPC
đứng cố định trong bảng npc, và tương tác boss đi nhánh riêng trong `handlerCommunicate`
([SV] `NpcHandler.java:122-231`).

## 4. Chuyển map (portal / exits)

**Không tồn tại bảng portal tĩnh nào** — cả phía server lẫn client:

- Server: `Map.load()` chỉ đọc vật thể từ DB `map_item` theo `map_id` ([SV] `play/Map.java:39-58`),
  nhưng schema bảng `map_item` trong dump này **không có cột `map_id`** và chỉ có đúng 1 dòng mẫu
  `(0, 11, 11, 11, 11)` ([DB] `avatar_2x.sql:83970-83984`) → với bộ dữ liệu này server thực tế
  không cấp vật thể/cổng nào qua DB.
- Cơ chế thật nằm ở **lưới ô phía client**: mỗi ô trong `Resources/map/<index>` được gán một
  `type`; các type "có thể tương tác" được liệt kê ở `LoadMap.setTypeJoint/setTypeFind`
  ([CL] `LoadMap.cs:1249-1296`). Khi người chơi chạm/kích hoạt ô, `LoadMap.doJoin(x, y)` tra
  `typeMap` của ô và **chính giá trị type là đích đến** ([CL] `LoadMap.cs:1298-1580`), ví dụ:
  - type 0–8, 11, 13–16, 18 → `ParkService.doJoinPark(typeMap, -1)` — sang server map cùng số
    ([CL] `LoadMap.cs:1330-1350`); type 19 → park 19, type 10 → park 10 ([CL] `LoadMap.cs:1536-1542`).
  - type 25 → `FarmScr.doGoFarmWay()` (đường ra nông trại), type 24 → vào nông trại của mình,
    type 52 → cửa hàng farm, type 53 → menu farm bạn bè ([CL] `LoadMap.cs:1358-1375`).
  - type 57/62, 58/63, 59/64 → các shop 1/6, 2/7, 3/8 (`doJoinShop`), type 21 → ra khỏi nhà,
    type 68–70 → nhà bạn bè, type 110 → nhà loại 4 ([CL] `LoadMap.cs:1330-1500`).
  - type 72–77 → casino/mini-game (`doGetHandlerCasino(typeMap - 72)`, [CL] `LoadMap.cs:1407`),
    type 20 → `requestJoinAny(0)` (dòng 1480), type 71 → `requestCityMap(-1)` (mở bản đồ thành phố),
    type 23 → handler 9, type 100/101/103/104 → `doJoinMapOffline(5/6/3/4)` (dòng 1504+).
  → Muốn port "vị trí cổng khớp 100%" phải trích các ô có type thuộc hai tập trên từ từng file
  `Resources/map/<index>` (việc trích riêng, chưa làm trong đợt kiểm kê này).
- **Chọn khu (zone) trong cùng map**: `ParkListSrc` gửi `doJoinPark(roomID, selected)`
  ([CL] `ParkListSrc.cs:60`; định dạng gói ở [CL] `ParkService.cs:23-39`: byte map, byte zone,
  short x, short y).
- **Bản đồ thành phố / MiniMap**: `MapScr.doJoin()` ánh xạ 7 vùng hướng dẫn →
  server map `{0, 13, 20, 9, 23, 11, 17}` ([CL] `MapScr.cs:3066-3067`); bản đồ thành phố lớn
  (`joinCitymap`) đọc resource `citiMap` 50×26 ô, các ô mã 69 là điểm vùng với tên lấy từ
  `T.nameRegion` ([CL] `MapScr.cs:3090-3125`, `T.cs:823`). Lưới ô của một số map cũng có thể do
  server gửi xuống qua `requestCityMap` → `onSelectedMiniMap(map, idMap, idTileImg, ...)`
  ([CL] `GlobalMessageHandler.cs:951-1015`).
- **Xe buýt**: có lớp `Bus` + trạm (`Bus.posBusStop`, [CL] `Bus.cs:19`) và trạng thái `Bus.isRun`
  được `onJoinPark` xử lý để đặt lại vị trí người chơi tại trạm ([CL] `MapScr.cs:2078-2085`);
  khi ở map đường nông trại (TYPEMAP 25) thoát bằng `FarmScr.doExitBus()` ([CL] `LoadMap.cs:1300-1310`).
  Không tìm thấy bảng tuyến buýt tĩnh trong code — buýt là hiệu ứng di chuyển khi chuyển map,
  không phải hệ portal riêng.

## 4b. Bổ sung: nguồn gốc con số "33 map" + bằng chứng server cấp dữ liệu map lúc chạy

- Con số "33 map" xuất phát từ ghi chú kiểm kê cũ: `pack5/INVENTORY.md:28` viết imagemap có
  "cho 33 map (1–26, 58–65, 101–108)". Chính các khoảng đó cộng lại là 42 id tiềm năng, và số
  thư mục đo trực tiếp trên đĩa là 31 (mục 1.1) — nên "33" là ghi chú tay chưa kiểm chứng,
  không phải con số từ code.
- Server **có cơ chế cấp tileset lúc chạy**, củng cố giả thuyết ở mục 1.4: client gửi yêu cầu
  kèm `idTileImg`, server đọc `tilemap/<idTileImg>.png` và trả nguyên bytes qua cmd
  `REQUEST_TILE_MAP` ([SV] `network/Session.java:1907-1921`). Phía server sẵn có 5 tileset
  `0.png`–`4.png` (ở cả `server/res/map/imgTileMap/` và `server/res/hd/tilemap/`).
- Bản đồ thành phố cũng do server cấp: `GlobalHandler` đọc `res/map/cityMap.dat` +
  `res/map/cityMap.png` để trả cho client ([SV] `handler/GlobalHandler.java:397-399`).
- Trong `server/res/map/` còn đúng một file lưới lạ: `27.dat` (272 byte) — trùng số với server
  map 27 (client 28, map của cua.hang/phu.thuy.bong.toi). Chưa tìm thấy code nào đọc file này
  (grep toàn server chỉ thấy `cityMap.dat` được đọc) → ghi nhận là manh mối, chưa kết luận.

## 5. Điểm chưa chắc chắn (nói rõ, không đoán)

1. Con số "33 map" và "30 thư mục imagemap" trong kiểm kê cũ **không khớp** số đo trực tiếp
   (60 zone server / 43 file dữ liệu map / 31 thư mục imagemap HD). maps.json theo số đo trực tiếp.
2. Tên hiển thị của 25/48 map chưa có nguồn trong code (để rỗng). Tên vùng trong `T.nameRegion`
   (Khu nhà ở, Sân bay...) là tên điểm trên bản đồ thành phố, không chứng minh được là tên của
   từng load index cụ thể nên không gán.
3. Client 28, 31, 34, 56, 57: có zone + NPC phía server nhưng thiếu dữ liệu map phía client
   trong Pack5 (mục 1.4) — chưa rõ bản gốc lấy lưới ô từ server hay bộ nguồn thiếu file.
4. Client 21, 23, 61–68, 106: có file dữ liệu ô nhưng chưa truy hết chúng thuộc màn hình nào
   (nhiều khả năng là nội thất nhà/shop theo luồng tile) — chỉ khẳng định được cơ chế dựng (mục 1.3).
5. Vị trí cổng chuyển map nằm trong lưới ô từng map (mục 4) — đợt này chưa trích toạ độ ô;
   muốn khớp 100% phải giải mã `Resources/map/<index>` + logic gán type trong
   `LoadMap.setMap/setMapPaint` ([CL] `LoadMap.cs:2091+, 3376-3386`).
6. NPC không có case trong `NpcHandler` có thể vẫn có vai trò ở phía client (chat, sự kiện theo
   mùa) — trong phạm vi code server này, kết luận "không mở chức năng" chỉ đúng cho tương tác
   qua `handlerCommunicate`.
