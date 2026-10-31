# Đặc tả FARM — AvatarDK gốc (kiểm kê 2026-10-31, chuẩn bị đợt 3)

Mục đích: chốt dữ kiện để port hệ Farm (đợt 3 theo thứ tự đã chốt: sau đăng nhập/online)
khớp bản gốc, trước khi viết code. Mọi khẳng định đều có trích dẫn `file:dòng`.

Nguồn viết tắt (như maps-spec.md):
- **[SV]** = `server/src/main/java/avatar/...` (server Java trong project này)
- **[CL]** = `~/workspace/avatardk-incoming/client-src/project-client-avt-main/Assets/Scripts/Assembly-CSharp/...`
- **[DB]** = `server/database/avatar_2x.sql`
- **[RES]** = `server/res/...`

Đã đối chiếu: `service/FarmService.java` và `message/FarmMsgHandler.java` trong project
**giống hệt** bản server trong Avatar-Sv-full.zip (chỉ `MessageHandler.java` và
`network/Session.java` khác — phần đăng ký gói −30 do đợt 2 thêm). Nghĩa là các chỗ
stub/cứng số ghi dưới đây là **tình trạng của chính server gốc đang có**, không phải
lỗi sao chép của bản port.

---

## 1. Kết luận quan trọng nhất: server gốc Farm CHỈ LÀM THẬT MỘT NỬA

| Nhóm | Trạng thái trong server gốc | Hệ quả khi port |
|---|---|---|
| Trồng / thu hoạch / mở ô / mua hạt | Có logic thật (xem mục 4) | Port theo server, đối chiếu payload với client |
| Bán nông sản (`GET_CARD` 63 → `sellFarmitm`) | **Stub**: chỉ đọc id rồi mở dialog "id" ([SV] `service/FarmService.java:40-46`) | Giá bán phải lấy từ client C# / DB, server không có đáp án |
| Mua vật nuôi (`BUY_ANIMAL` 71) | **Nửa stub**: tạo `Animal(n, 2000, 100, 0, 20, ...)` với giá/số trong gói trả về toàn 0 ([SV] `FarmService.java:219-236`) | Bảng giá vật nuôi chưa có trong server — tra client |
| Chuồng bò (80) / hồ cá (81) | **Chưa làm**: handler chỉ trả dialog "đang xây dựng" ([SV] `message/FarmMsgHandler.java:105-112`) | Bản gốc server vốn không có tính năng này chạy được |
| Nấu ăn (`COOKING` 91) | **Rỗng**: case bị comment hết ([SV] `FarmMsgHandler.java:113-116`) | Như trên |
| Ăn trộm / bạn bè farm (`REQUEST_FRIENDLIST` −18) | Dialog đùa "Ăn trộm đang xây dựng" ([SV] `FarmMsgHandler.java:93-96`) | Không có logic thật |
| `joinFarm` (61) + `getInventory` (60) | Chạy được nhưng **cứng số**: cây khế (cấp 10, ảnh 43/46, 180 quả), chuồng bò/hồ cá đều 10, nông sản đặc biệt cứng 3 dòng (255 thịt cá ×20, 215 khế ×680, 214 tinh dầu hướng dương ×4), đuôi gói có `writeInt(64000)` không rõ nghĩa ([SV] `FarmService.java:279-415`) | Khi port phải tách phần dữ kiện thật của user khỏi các hằng này; các hằng nhiều khả năng là dữ kiện phía client mong đợi — cần đọc [CL] `FarmScr.cs`/`FarmData.cs` để phân định |

Điểm tựa đáng tin nhất cho UI + payload Farm là **client C#**: `FarmScr.cs`,
`FarmData.cs`, `FarmItem.cs`, `CellFarm.cs`, `FishFarm.cs`, `FarmMsgHandler.cs`,
`FarmService.cs` (bản client) — đợt 3 bắt đầu bằng việc đọc các file này, giống cách
đợt 2 đọc giao thức từ cả hai phía.

## 2. Dữ liệu bền (DB)

Bảng `farm_data` — 1 dòng / user ([DB] `avatar_2x.sql` sau dòng 1511):

| Cột | Kiểu | Nội dung |
|---|---|---|
| `user_id` | int | chủ farm |
| `land_data` | longtext (JSON hợp lệ) | danh sách ô đất |
| `animal_data` | longtext (JSON hợp lệ) | danh sách vật nuôi |
| `hatgiong` | longtext | hạt giống đang có (id + số lượng) |
| `nongsan` | longtext | nông sản đã thu hoạch |
| `phanbon` | longtext | phân bón |
| `nongsandacbiet` | longtext | nông sản đặc biệt |

Mô hình trong bộ nhớ ([SV] `Farm/`): `LandItem` (type cây, sucKhoe 0–100, resourceCount,
watered, fertilized, harvestable, plantedTime — chín tính bằng
`getMinutesSincePlanted()` so với `so_phut_chin` của cây), `Animal` (health, level,
resourceCount, nextProductionTime, alive, readyForBreeding, harvestable),
`HatGiong`/`NongSan`/`PhanBon`/`NongSanDacBiet` đều là cặp (id, soluong).

## 3. Bảng cây trồng — `farmitems` ([DB] `avatar_2x.sql:1488-1512`)

Server nạp toàn bộ lúc khởi động: `PartManager.loadFarmItemData()` chạy
`SELECT * FROM farmitems` ([SV] `item/PartManager.java:157-160`), ánh xạ sang
`farmItem(id, name, time=so_phut_chin, quantity=san_luong_khi_chin, sell=gia_san_pham)`.

Đủ **25 cây** (id 0–10 và 30–43; id 11–29 không tồn tại trong dump):

| id | Cây | Phút chín | Sản lượng | Giá SP |
|---|---|---|---|---|
| 0 | Cà Chua | 260 | 75 | 10 |
| 1 | Cà Rốt | 380 | 108 | 10 |
| 2 | Khóm | 600 | 165 | 10 |
| 3 | Dưa Hấu | 480 | 138 | 10 |
| 4 | Nho | 960 | 240 | 10 |
| 5 | Hoa Hồng | 120 | 45 | 10 |
| 6 | Lúa | 2280 | 720 | 10 |
| 7 | Xoài | 1440 | 360 | 10 |
| 8 | Thanh Long | 720 | 189 | 10 |
| 9 | Hoa Hướng Dương | 720 | 189 | 10 |
| 10 | Hoa Tulip | 360 | 108 | 10 |
| 30 | Bắp | 1140 | 360 | 10 |
| 31 | Nha Đam | 120 | 45 | 10 |
| 32 | Dưa Leo | 2880 | 720 | 10 |
| 33 | Cà Tím | 480 | 138 | 10 |
| 34 | Tỏi | 260 | 75 | 10 |
| 35 | Dâu Tây | 300 | 97 | 10 |
| 36 | Chuối | 660 | 97 | 10 |
| 37 | Đào | 2880 | 720 | 10 |
| 38 | Vải | 4320 | 1200 | 10 |
| 39 | Cúc Chuồn Chuồn | 180 | 62 | 10 |
| 40 | Sen | 480 | 140 | 10 |
| 41 | Bí Ngô | 120 | 80 | 10 |
| 42 | Khoai Tây | 360 | 100 | 10 |
| 43 | Cây Ớt | 60 | 45 | 10 |

Lưu ý: `gia_san_pham` = 10 cho **cả 25 cây** trong dump — đây là giá server dùng khi
mua hạt (`Buy_item_farm` trừ `itemf.getSell()*n`, [SV] `FarmService.java:175-217`),
không phân biệt đó là giá mua hạt hay giá bán sản phẩm; tên cột/cách dùng mâu thuẫn
nhau, cần đối chiếu [CL] trước khi chốt kinh tế farm.

## 4. Lệnh giao thức Farm ([SV] `constants/Cmd.java`, `message/FarmMsgHandler.java:37-121`)

| Cmd | Tên | Hướng / handler | Payload & logic đã xác minh |
|---|---|---|---|
| 51 | SET_BIG_FARM | → `setBigFarm` | Trả danh sách ảnh bigFarm; bản trong project đã sửa (ảnh {99, 206}) so với bản "apk gốc" để lại trong comment ({66, 6}) — [SV] `FarmService.java:139-173`. **Chưa rõ bản nào khớp client hiện hành** |
| 54 | GET_BIG_FARM | → `getBigFarm` | Client gửi `short imageID`, server trả bytes PNG từ `res/bigFarm/<id>.png` (cmd 54) |
| 55 | GET_IMAGE_FARM | → `getImageData` | Trả toàn bộ `GameData.getFarmImageDatas()`: id, bigImageID, x, y, w, h |
| 56 | GET_TREE_INFO | → `getTreeInfo` | Trả nguyên file `res/data/farm_info.dat` |
| 60 | INVENTORY | → `getInventory` | Hạt giống + nông sản + xu + cấp farm (`leverFarm`, `leverPercen`) + phân bón + nông sản đặc biệt; có đoạn lặp và hằng cứng (mục 1) |
| 61 | JOIN | → `joinFarm` | Vào farm của user id (int). Trả: số ô đất + từng ô (type; nếu có cây: phút đã trồng short, sức khoẻ byte, byte 1 = "100 là héo", watered, fertilized, harvestable) + số vật nuôi + từng con (health int, level, resourceCount, nextProductionTime, alive, breeding, harvestable) + các hằng khế/chuồng/hồ ở cuối |
| 62 | BUY_ITEM | → `Buy_item_farm` | Mua hạt: `short id, byte n, byte type` (type 1 = xu, khác = lượng). Chặn: không đủ tiền, tổng hạt > 99. Trừ tiền theo `sell*n` |
| 63 | GET_CARD | → `sellFarmitm` | **Stub** (mục 1) |
| 64 | PLANT_SEED | → `plandSeed` | `int idUser, byte indexCell, byte idSeed`. Trừ 1 hạt, ô đất: type=idSeed, sucKhoe=100, growthTime=0, chưa tưới/bón/thu hoạch. Phát lại cmd 64 cho chính session |
| 66 | TREE_HARVEST | → `treeHarvest` | `int idUser, byte indexCell`. Sản lượng thật = `(quantity % 100) * sucKhoe / 100` — công thức này rất đáng ngờ (quantity ≥ 100 thì chỉ lấy phần dư), ghi lại nguyên văn để đối chiếu client; ô đất trả về type −1 |
| 69 | REQUEST_SLOT | → `doRequestslot` | Chỉ trả câu hỏi mẫu "Bạn có muốn mở ô đất @ với giá @ xu hoặc @ lượng không?" — **giá mở ô không có trong server** |
| 70 | OPEN_LAND | → `openLand` | Thêm 1 `LandItem` trống; trả kèm xu/lượng hiện tại. Không trừ tiền trong handler này |
| 71 | BUY_ANIMAL | → `Buy_ANIMAL` | **Nửa stub** (mục 1) |
| 77 | CHAT_FARM | chat riêng map farm | Đã ghi trong protocol-spec.md: ở map farm (server id 22) chat phát bằng cmd 77 thay vì 55 |
| 80 / 81 | UPDATE_FARM_CATTLE / FISH | — | Dialog "đang xây dựng" |
| 82 | GET_IMG_FARM | → `getImgFarm` | Trả PNG từ `res/farm/<imageID>.png` |
| 91 | COOKING | — | Rỗng |
| −18 | REQUEST_FRIENDLIST | — | Dialog "Ăn trộm đang xây dựng" |
| −23 | REQUEST_CHARGE_MONEY_INFO | — | Dialog "Pay To Win hả" |
| −61 | COMMUNICATE | → NPC lái buôn | Route về `NpcHandler.handlerCommunicate(Npc.ID_ADD + LAI_BUON)` — NPC này ở map 26 (client), xem maps-spec.md |

Ảnh farm phía client có sẵn trong Pack5: `Assets/Resources/hd/farm/` và
`medium/farm/`, `bigFarm/` tương ứng; server stream ảnh khi client thiếu (54/82).

## 5. Việc cần làm khi bắt đầu đợt 3 (theo thứ tự)

1. Đọc [CL] `FarmScr.cs` + `FarmData.cs` + `FarmMsgHandler.cs` (client): chốt layout ô
   đất trên map 25/26, payload client thật sự đọc cho cmd 60/61, và ý nghĩa byte
   "100 là héo" + hằng 64000 trong gói INVENTORY.
2. Chốt các dữ kiện server thiếu: giá mở ô đất, giá vật nuôi + chu kỳ sản xuất,
   giá bán nông sản — nguồn ưu tiên: hằng trong client C#, sau đó mới tới `farm_info.dat`
   ([RES] `res/data/farm_info.dat`, chính là file cmd 56 trả về).
3. Quyết định phạm vi đợt 3 theo đúng tình trạng gốc: trồng/thu hoạch/mở ô/mua hạt/kho
   là phần gốc chạy được; chuồng bò/hồ cá/nấu ăn/ăn trộm server gốc **chưa từng chạy**
   — port "nguyên bản 100%" ở đây nghĩa là giữ nguyên trạng thái đó và ghi rõ, không
   tự invent tính năng mới thay cho gốc.
4. Kiểm chứng tăng trưởng: server tính chín từ `plantedTime` thật (phút), không có
   tick cập nhật sucKhoe nào trong `FarmService` — cần tìm nơi (nếu có) làm cây héo/
   giảm sức khoẻ theo thời gian trước khi hứa hẹn hành vi offline.

*Kiểm kê này không sửa code nào; file duy nhất được tạo là tài liệu này.*
