# Đối chiếu MAP: bản port (Pack5) vs bản chính thức đang phát hành (client 3.4.4)

Ngày: 2026-11-03. Lệnh của user: "coi với bản chính đang phát hành bung ra dựng map cho đúng".

Nguồn:
- Port: `assets/data/mapgrids.json` (44 lưới, khoá id client; `g` = base64 lưới thô
  không header, `w` = số cột) + `assets/data/maps.json` (48 map, trường `strips`, `bg`, `hmap`).
- Bản chính 3.4.4: `~/workspace/apk-avatardk-new/extract/assets/bin/Data/data.unity3d`
  (2.364 TextAsset; trích byte-chính-xác bằng UnityPy 1.25.4, kiểm chứng chéo với
  `/tmp/datamaps344.json` — khớp 36/36 file).
- Code Pack5 đối chiếu: `LoadMap.cs` (bg[], Hmap switch, setMap, loadImageMapFull).

Lưới 3.4.4 đã giải mã: **`docs/datamaps344.json`** — 36 map, khoá id client:
`{w, h, bg, cells[]}` (cells = byte không dấu, thứ tự row-major).

---

## 1. Format DataMap của 3.4.4 — ĐÃ GIẢI MÃ

```
DataMap<N>.bytes  (N = id CLIENT)
  [0..1]  int16 BE CÓ DẤU = bg id (mã nền trời) của map; -1 = không có nền
  [2..]   lưới ô thô, 1 byte/ô, row-major, KHÔNG nén — giống hệt format
          file map/<index> của Pack5 (Pack5 không có 2 byte đầu này)
```

### 1.1. Bằng chứng header = bg id

- Pack5 giữ bg trong mảng cứng `LoadMap.bg[26]` (khoá id SERVER, LoadMap.cs:288):
  client 1..26 → `1,1,1,1,1,1,1,1,1,0,1,1,3,3,3,3,3,2,-1,-1,-1,0,0,0,2,2`.
  Header DataMap **khớp 22/26** giá trị này (mọi map 1–17, 19, 24, 25, 26).
- 4 map ĐỔI bg ở 3.4.4: **18: 2→4, 21: −1→5, 22: 0→−1, 23: 0→8**.
- Map mới: 27→bg 7, 28→bg 6, 29→bg 1, 30→bg −1; các phòng nội thất
  58/59/60/65/101/102/104/105 đều −1 (khớp bg=null phía port).
- Bundle có đủ asset nền cho bg 0..8: `BGMap<bg><status>` (status 0/1 = ngày/đêm),
  đúng quy ước Pack5 `loadBG` nạp `bgHD/<num><status>`. Ngoài 0..8 và −1 không
  xuất hiện giá trị header nào khác.
- Vậy byte thứ nhất chỉ là byte cao của int16 (0x00, hoặc 0xFF khi −1); "255,255"
  của map nội thất = −1 nguyên vẹn, không phải hai cờ riêng.

### 1.2. Số hàng (Hmap) suy từ đâu

3.4.4 **không lưu** Hmap trong file. Số hàng H được chốt bằng 4 nguồn độc lập:

1. **Ước số của số ô**: sau header, số ô phải chia hết cho W.
2. **Bề rộng strip PNG**: tranh HD vẽ theo ô 48px → W ≈ stripWidth/48
   (sai lệch ≤ +32px phần art tràn lề; map nội thất khớp tuyệt đối, vd
   map 19: 912 = 19×48, map 30: 1104 = 23×48, map 18: 2976 = 62×48).
3. **Header của LinkMap** (8 map có): ghi thẳng W×10 và H×10 — khớp 8/8 với
   kết quả suy luận (xem §4).
4. **Tính liên tục không gian** khi dựng ASCII: biên/sàn/tường phải thành hình
   (đã kiểm tra mắt cho map 2, 18, 21, 23, 27, 28, 29, 30).

So với bảng Hmap của Pack5 (số HÀNG): đa số giữ nguyên (8 cho map phố, 11 cho
phòng, 9 cho map 11, 10 cho map 19, 7 cho map 22/26). **4 map đổi số hàng:
2: 8→9, 18: 6→7, 21: 11→7, 28: 8→10.**

### 1.3. Các file "khó" — đã xử lý dứt khoát

| File | Cỡ | Giải mã | Căn cứ |
|---|---|---|---|
| DataMap2 | 254B = 2 + 252 ô | **28×9** | 252 không chia hết cho 8; strip 1372px ≈ 28×48; ASCII: biên 28/31/11 hoàn hảo cả 9 hàng |
| DataMap18 | 436B = 2 + 434 ô | **62×7** | strip 2976px = 62×48 **chẵn tuyệt đối**; LinkMap18 header ghi W=62, H=7 |
| DataMap21 | 198B = 2 + 196 ô | **28×7** | strip 1350px ≈ 28×48; LinkMap21 header ghi 28×7; ASCII phòng mạch lạc (sàn 4, tường 17) |
| DataMap29 | 6061B = 2 + 6059 ô | **73×83** | 6059 = 73×83 (cặp ước duy nhất khả thi); strip 3508px ≈ 73×48; LinkMap29 header ghi 73×83; ASCII = **mê cung** có hành lang lặp đôi theo hàng + 4 ô 139 (trạm xe) — không thể là dữ liệu rác |
| DataMap30 | 462B = 2 + 460 ô | **23×20** | strip 1104×960 = 23×48 × 20×48 **chẵn cả hai chiều**; LinkMap30 ghi 23×20; ASCII = nội thất nhiều phòng (tường 99, sàn 47, cửa 67/62/64) |

Không ô nào trong cả 36 lưới chứa giá trị 255 — lưới 3.4.4 không dùng quy ước
255→−1 của Pack5 setMap; mọi byte đều là tile/marker thật (0..218).

---

## 2. Các định dạng phụ trong bundle 3.4.4

### 2.1. Map strips — TextAsset `Map<N>` (N = mapId×10 + stripIndex)

Cấu trúc: **4 byte tiền tố + đúng 1 PNG** (PNG chạy tới EOF, không đuôi thừa;
đã kiểm chứng IEND ở 12 mẫu):

```
[0]   byte 0
[1]   byte = id CLIENT của map
[2..3] uint16 BE = thẻ 16-bit của map (xem dưới)
```

- Khác Pack5 về bản chất đóng gói: Pack5/port để strip là **file PNG rời**
  trong `imagemap/<id>/<i>.png`; 3.4.4 **nhúng PNG vào TextAsset** kèm tiền tố
  4 byte. Nội dung vẫn là PNG thường, trích ra được nguyên vẹn.
- Khác cả cách cắt: Pack5 cắt tranh thành nhiều slice dọc bottom-aligned
  (map 1: 3 slice 418px, cao 516/468/516). 3.4.4 dùng **1 PNG toàn cảnh duy
  nhất** cho 34/36 map (vd map 1: 1376×722). Chỉ 2 ngoại lệ giữ kiểu slice:
  map 22 (3 slice 802px) và map 25 farm (6 slice, tổng rộng 2037px so với
  2030px của port — cùng bố cục, art cao hơn: 638px vs ~516px).
- Thẻ 16-bit [2..3]: xuất hiện đồng nhất trong tiền tố của MỌI strip thuộc
  cùng map và trong asset manifest **`Map`** (148B = int32 count 36 + 36 bản
  ghi `{byte 0, byte id SERVER, uint16 thẻ}`; đối chiếu khớp 36/36 cả id lẫn
  thẻ). Đã thử sum16, CRC32 (nửa cao/thấp), Adler32, CRC16-Modbus/CCITT,
  FNV-1a trên PNG và trên DataMap — **không hàm nào khớp**; coi là thẻ
  checksum/version nội bộ của pipeline đóng gói, không cần giải mã để dựng map.
- Đối chiếu pixel với strip của port (các map lưới giống hệt): **cùng kích
  thước canvas** (58: 576×526, 101: 960×528, 102: 672×528, 19: 912×480) nhưng
  **nội dung pixel khác** — map 19 gần như trùng (meanAbsDiff 1,47), các map
  58/60/101/102 lệch mạnh (meanAbsDiff 34–41, file PNG nặng ~1,5×) → 3.4.4
  đã vẽ lại/re-export art, không tái sử dụng file của Pack5.

### 2.2. `BGMap<bg><status>` và manifest `BGMap`

- `BGMap00`..`BGMap81`: 18 file cho bg 0..8 × status 0/1. Cấu trúc: header
  9 byte `{int16 bgId, uint16 thẻ, int32 ?, byte cờ}` + 1 PNG nền
  (vd BGMap00: 956×465; BGMap41: 2976×681 — nền co theo bề rộng map dùng nó).
- `BGMap` (40B): manifest kiểu như `Map` — danh sách (bgId, thẻ).

### 2.3. `LinkMap<N>` — ĐÃ GIẢI MÃ TOÀN PHẦN (chỉ 8 map có)

```
[int32 W×10] [int32 H×10] [int32 số link]
mỗi link 18 byte: [int16 id SERVER đích] [int32 x1] [int32 y1] [int32 x2] [int32 y2]
```
Toạ độ tính bằng 1/10 ô (W×10 của map 10 = 420 = 42 ô). Đây là **cổng chuyển
map dạng hình chữ nhật đặt ở mép map** — cơ chế bổ sung/thay thế cổng theo
type ô cho các map bị dựng lại. Cả 8 file parse hết đúng tới EOF:

| Map | W×H | Link (đích client, x1,y1,x2,y2) |
|---|---|---|
| 10 | 42×8 | →30: (70,15)-(90,25) |
| 18 | 62×7 | →21: (80,0)-(100,10); →23: (310,0)-(340,10); →27: (510,0)-(540,10) |
| 21 | 28×7 | →18: (120,60)-(140,70); →28: (0,50)-(10,70) |
| 23 | 28×8 | →18: (120,70)-(160,80) |
| 27 | 28×8 | →18: (110,70)-(140,80) |
| 28 | 28×10 | →21: (270,70)-(280,90) |
| 29 | 73×83 | (0 link — mê cung khép kín, vào bằng đường khác) |
| 30 | 23×20 | →10: (120,190)-(140,200) |

Nhận xét: 8 map có LinkMap chính là các map bị dựng lại nặng nhất (18, 21, 23,
28), map mới (27, 29, 30) và map 10 (cổng sang map mới 30). Cụm 10↔30 và
18↔21/23/27, 28→21 là khu vực map MỚI/liên kết mới của 3.4.4.

### 2.4. `DataMoveCityMap` (92B) và `avatarMapType` (2B)

- `DataMoveCityMap` = int32 0, int32 2, rồi 11 cặp (x, y) pixel:
  (52,48), (1432,166), (1242,410), (1082,131), (1022,104), (960,63),
  (1004,24), (52,24), (630,805), (471,865), (10,886) — điểm neo "di chuyển
  đến" trên bản đồ thành phố của client mới.
- `avatarMapType` = 2 byte `00 00` — **rỗng** (verMapType = 0 khớp `avatarVs`).
  Dataset này KHÔNG chứa bố trí NPC hay map item nào; phía client, NPC là thực
  thể server gửi khi vào zone (bản port lấy npcs.json từ DB server Pack5, không
  phải từ client) → trong bundle 3.4.4 **không tồn tại dataset NPC** để tra.

---

## 3. Bảng đối chiếu từng map (id client 1..110)

"Port" = maps.json (strips) + mapgrids.json (lưới). "3.4.4" = DataMap + strip.
Ô lệch = so từng byte của hai lưới thô khi cùng tổng số ô.

| id | Port (Pack5) | 3.4.4 | Kết luận |
|---|---|---|---|
| 1 | imagemap×3, lưới 26×8 | DataMap 28×8 bg=1, strip[1]: 1376×722 | KHÁC CỠ (port 26×8 vs 344 28×8) |
| 2 | imagemap×1, lưới 14×8 | DataMap 28×9 bg=1, strip[1]: 1372×679 | KHÁC CỠ (port 14×8 vs 344 28×9) |
| 3 | imagemap×1, lưới 16×8 | DataMap 27×8 bg=1, strip[1]: 1313×747 | KHÁC CỠ (port 16×8 vs 344 27×8) |
| 4 | imagemap×1, lưới 19×8 | DataMap 27×8 bg=1, strip[1]: 1295×627 | KHÁC CỠ (port 19×8 vs 344 27×8) |
| 5 | imagemap×3, lưới 28×8 | DataMap 28×8 bg=1, strip[1]: 1350×694 | cùng cỡ, lệch 48 ô |
| 6 | imagemap×1, lưới 20×8 | DataMap 27×8 bg=1, strip[1]: 1327×675 | KHÁC CỠ (port 20×8 vs 344 27×8) |
| 7 | imagemap×1, lưới 21×8 | DataMap 28×8 bg=1, strip[1]: 1376×694 | KHÁC CỠ (port 21×8 vs 344 28×8) |
| 8 | imagemap×3, lưới 22×8 | DataMap 27×8 bg=1, strip[1]: 1342×672 | KHÁC CỠ (port 22×8 vs 344 27×8) |
| 9 | imagemap×3, lưới 22×8 | DataMap 28×8 bg=1, strip[1]: 1344×713 | KHÁC CỠ (port 22×8 vs 344 28×8) |
| 10 | imagemap×3, lưới 42×8 | DataMap 42×8 bg=0, strip[1]: 2016×820, LinkMap(1) | cùng cỡ, lệch 24 ô |
| 11 | imagemap×1, lưới 29×9 | DataMap 29×9 bg=1, strip[1]: 1392×561 | GIỐNG HỆT |
| 12 | imagemap×3, lưới 44×8 | DataMap 44×8 bg=1, strip[1]: 2118×694 | cùng cỡ, lệch 36 ô |
| 13 | tile, lưới 44×8 | - | chỉ port có (344 không đóng gói) |
| 14 | imagemap×1, lưới 21×8 | DataMap 27×8 bg=3, strip[1]: 1295×556 | KHÁC CỠ (port 21×8 vs 344 27×8) |
| 15 | imagemap×1, lưới 21×8 | DataMap 28×8 bg=3, strip[1]: 1344×612 | KHÁC CỠ (port 21×8 vs 344 28×8) |
| 16 | imagemap×3, lưới 35×8 | DataMap 35×8 bg=3, strip[1]: 1686×472 | cùng cỡ, lệch 4 ô |
| 17 | imagemap×3, lưới 27×8 | DataMap 27×8 bg=3, strip[1]: 1299×528 | cùng cỡ, lệch 3 ô |
| 18 | imagemap×3, lưới 27×6 | DataMap 62×7 bg=4, strip[1]: 2976×511, LinkMap(3) | KHÁC CỠ (port 27×6 vs 344 62×7) |
| 19 | imagemap×1, lưới 19×10 | DataMap 19×10 bg=-1, strip[1]: 912×480 | GIỐNG HỆT |
| 20 | tile, lưới 28×13 | - | chỉ port có (344 không đóng gói) |
| 21 | tile, lưới 24×11 | DataMap 28×7 bg=5, strip[1]: 1350×694, LinkMap(2) | KHÁC CỠ (port 24×11 vs 344 28×7) |
| 22 | imagemap×3, lưới 50×7 | DataMap 50×7 bg=-1, strip[3]: 802×468, 802×473, 802×469 | GIỐNG HỆT (lưới) — nhưng bg đổi 0→−1 |
| 23 | tile, lưới 48×8 | DataMap 28×8 bg=8, strip[1]: 1376×552, LinkMap(1) | KHÁC CỠ (port 48×8 vs 344 28×8) |
| 24 | imagemap×3, lưới 44×8 | DataMap 44×8 bg=0, strip[1]: 2112×714 | cùng cỡ, lệch 6 ô |
| 25 | imagemap×6, lưới 42×8 | DataMap 42×8 bg=2, strip[6]: 99×638, 50×638, 827×638, 863×638, 50×638, 148×638 | cùng cỡ, lệch 51 ô |
| 26 | imagemap×3, lưới 27×7 | DataMap 27×7 bg=2, strip[1]: 1302×565 | cùng cỡ, lệch 17 ô |
| 27 | - | DataMap 28×8 bg=7, strip[1]: 1346×699, LinkMap(1) | MỚI ở 3.4.4 / port thiếu |
| 28 | tile, lưới 34×8 | DataMap 28×10 bg=6, strip[1]: 1350×694, LinkMap(1) | KHÁC CỠ (port 34×8 vs 344 28×10) |
| 29 | - | DataMap 73×83 bg=1, strip[1]: 3508×4230, LinkMap(0) | MỚI ở 3.4.4 / port thiếu |
| 30 | - | DataMap 23×20 bg=-1, strip[1]: 1104×960, LinkMap(1) | MỚI ở 3.4.4 / port thiếu |
| 31 | không dữ liệu | - | KHÔNG BẢN NÀO CÓ — 3.4.4 đã bỏ |
| 34 | không dữ liệu | - | KHÔNG BẢN NÀO CÓ — 3.4.4 đã bỏ |
| 56 | không dữ liệu | - | KHÔNG BẢN NÀO CÓ — 3.4.4 đã bỏ |
| 57 | không dữ liệu | - | KHÔNG BẢN NÀO CÓ — 3.4.4 đã bỏ |
| 58 | imagemap×1, lưới 12×11 | DataMap 12×11 bg=-1, strip[1]: 576×526 | GIỐNG HỆT |
| 59 | imagemap×1, lưới 12×11 | DataMap 12×11 bg=-1, strip[1]: 576×526 | GIỐNG HỆT |
| 60 | imagemap×1, lưới 12×11 | DataMap 12×11 bg=-1, strip[1]: 576×522 | GIỐNG HỆT |
| 61 | tile, lưới 7×5 | - | chỉ port có (344 không đóng gói) |
| 62 | tile, lưới 7×5 | - | chỉ port có (344 không đóng gói) |
| 63 | tile, lưới 12×11 | - | chỉ port có (344 không đóng gói) |
| 64 | tile, lưới 12×11 | - | chỉ port có (344 không đóng gói) |
| 65 | imagemap×1, lưới 12×11 | DataMap 12×11 bg=-1, strip[1]: 576×528 | GIỐNG HỆT |
| 66 | tile, lưới 7×5 | - | chỉ port có (344 không đóng gói) |
| 67 | tile, lưới 27×8 | - | chỉ port có (344 không đóng gói) |
| 68 | tile, lưới 27×8 | - | chỉ port có (344 không đóng gói) |
| 101 | imagemap×1, lưới 20×11 | DataMap 20×11 bg=-1, strip[1]: 960×528 | GIỐNG HỆT |
| 102 | imagemap×1, lưới 14×11 | DataMap 14×11 bg=-1, strip[1]: 672×528 | GIỐNG HỆT |
| 104 | imagemap×1, lưới 18×11 | DataMap 18×11 bg=-1, strip[1]: 864×528 | GIỐNG HỆT |
| 105 | imagemap×1, lưới 14×11 | DataMap 14×11 bg=-1, strip[1]: 672×528 | GIỐNG HỆT |
| 106 | tile, lưới 19×8 | - | chỉ port có (344 không đóng gói) |
| 108 | imagemap×6, lưới 240×16 | - | chỉ port có (344 không đóng gói) |

Các id còn lại trong 1..110 — **59 id không có dữ liệu ở BẤT KỲ nguồn nào**
(không ở port, không ở 3.4.4): 32, 33, 35–55, 69–100, 103, 107, 109, 110.
Đây là các id chưa từng được dùng làm map trong cả hai bản.

### Tổng kết số liệu

- Map hai bản đều có lưới: **33**. Trong đó:
  - **Giống hệt từng byte: 11** (11, 19, 22, 58, 59, 60, 65, 101, 102, 104, 105).
  - **Cùng cỡ, lệch ít ô: 8** (5: 48 ô, 10: 24, 12: 36, 16: 4, 17: 3, 24: 6,
    25: 51, 26: 17). Ô lệch chủ yếu là marker vật thể/cổng, vd map 24 mất hai
    marker 148 (Gift) và 4 marker 195→11; map 10 mất dải marker 127 (Hội Đánh
    Cờ); map 25 (farm) các marker **142 (gốc khối đất) DỜI CỘT** — (25,1),
    (28,1), (31,1) → vị trí mới quanh cột 29, kèm marker 51 mới: bố cục ô đất
    farm của 3.4.4 khác Pack5.
  - **Khác cỡ: 14** (1, 2, 3, 4, 6, 7, 8, 9, 14, 15, 18, 21, 23, 28). Họa tiết
    chung: các map phố Pack5 rộng tùy tiện 14–26 ô được 3.4.4 **chuẩn hoá
    27–28 ô**; map 18 phình 27×6 → 62×7; map 23 thu 48×8 → 28×8 và đổi từ tile
    sang imagemap+bg8; map 21 và 28 tương tự (tile → imagemap, thêm bg/LinkMap).
- Map **mới hoàn toàn** ở 3.4.4: **27, 29, 30** (đủ lưới + art + bg + LinkMap).
- Map port có mà 3.4.4 **không đóng gói**: 13, 20, 61, 62, 63, 64, 66, 67, 68,
  106, 108 (11 map — xem khuyến nghị §5.3 trước khi kết luận bị xoá).

---

## 4. Số phận map 31 / 34 / 56 / 57 — KẾT LUẬN DỨT KHOÁT: BẢN CHÍNH ĐÃ BỎ

Bằng chứng (4 lớp dữ liệu độc lập trong bundle 3.4.4, tất cả đều phủ đúng
cùng một vũ trụ 36 map):

1. **DataMap**: đủ 36 file {1–12, 14–19, 21–30, 58, 59, 60, 65, 101, 102, 104,
   105} — không có DataMap31/34/56/57.
2. **Map strips**: đủ 43 file, phủ đúng 36 map trên — không có
   Map310/340/560/570.
3. **Manifest `Map`**: count = 36, đối chiếu khớp 36/36 — không có mục cho
   server id 30/33/55/56 (tức client 31/34/56/57).
4. **LinkMap + BGMap**: không mục nào tham chiếu 31/34/56/57.

Kiểm tra phụ:
- `avatarMapType` rỗng (2 byte) và **không có dataset NPC trong client** (NPC
  do server phát khi vào zone — cả hai bản đều vậy; tên NPC "tien.ca /
  pharaoh / zombie / phu.thuy" không xuất hiện ở bất kỳ string nào trong
  metadata IL2CPP của 3.4.4). Tức là không còn đường nào để 4 NPC Pack5
  (50 tiên cá @31, 760 pharaoh @34, 535 zombie @56, 425 phù thủy @57) tồn tại
  cùng map của chúng trong bản chính hiện hành.
- TextAsset tên lạ đã rà: `17`, `11`... là dữ liệu frame của hệ pic/Anim
  (1.581 asset `pic*` khớp phần 2 của avatarImgData), `BGMap*` là nền,
  `DataMapSpecial` chỉ là tên field trong metadata, không phải asset map.

**Kết luận: map 31/34/56/57 đã bị loại khỏi bản chính đang phát hành.** Việc
săn lưới của chúng trên server live là vô ích cho mục tiêu "đúng bản chính":
cụm server hiện hành (19129, giao thức đổi số) thuộc về client 3.4.4 vốn không
còn các map này. (Chúng chỉ từng tồn tại ở tầng server Pack5 — ngay Pack5
client cũng không có dữ liệu của chúng.)

---

## 5. Khuyến nghị nguồn cho từng nhóm map

Khung quyết định: "đúng" = đúng bản chính 3.4.4. Nhưng phải nhớ **server của
bản port là server Pack5**: toạ độ NPC, cổng theo type ô và logic farm của
server đều neo theo hình học Pack5. Đổi lưới mà không đổi đồng bộ các lớp kia
sẽ lệch nhau (đã có bài học marker 142 của farm).

### 5.1. Giữ nguyên port (11 map): 11, 19, 22, 58, 59, 60, 65, 101, 102, 104, 105
Lưới hai bản trùng tuyệt đối — không việc gì phải làm. (Riêng map 22: bg phía
port đang là 0, bản chính là −1 — chỉ cần đổi bg nếu port chuyển sang dùng bg
theo DataMap.)

### 5.2. Lấy lưới 3.4.4, giữ art port (8 map cùng cỡ): 16, 17, 24 (lệch 3–6 ô — an toàn)
Lệch rất nhỏ, toàn marker vật thể. Thay `g` bằng cells của DataMap tương ứng
rồi chạy lại pipeline suy `o/t/d`. Map 5, 10, 12, 26 (lệch 17–48 ô) cũng làm
được nhưng phải soi lại vị trí vật thể/cổng sau khi thay. **Map 25 (farm):
khoan đổi** — marker 142 dời cột làm thay đổi bố cục khối đất; phải đổi cùng
lúc với logic farm phía server/port và kiểm chứng lại toàn bộ đợt farm.

### 5.3. Đổi cả gói (lưới + art + bg) nếu muốn đúng bản chính (14 map khác cỡ)
1, 2, 3, 4, 6, 7, 8, 9, 14, 15, 18, 21, 23, 28: lưới 3.4.4 không ghép được
với art Pack5 (bề rộng khác hẳn). Muốn đúng bản chính phải lấy đồng bộ:
DataMap (đã có ở datamaps344.json) + PNG strip trích từ TextAsset Map<N>
(bỏ 4 byte tiền tố) + bg từ header + LinkMap (nếu có). Hệ quả bắt buộc:
suy lại `o/t/d` cho lưới mới, và đối chiếu lại toạ độ NPC/cổng với server
Pack5 (vd map 28: NPC Cửa Hàng (334,72) và Phù thủy (438,72) rơi vào ô
(13,3)/(18,3) của lưới 28×10 — đều là ô 14, đi đứng được, khả thi; map 18:
NPC 862/863 ở (173,48)/(54,48) vẫn nằm trong lưới 62×7).
Đây là thay đổi lớn theo đúng nghĩa "dựng lại map" — nên làm theo từng map,
chụp kiểm chứng từng map, không thay hàng loạt mù.

### 5.4. Thêm mới (3 map): 27, 29, 30
Dữ liệu đầy đủ phía client (lưới + art + bg + LinkMap). Nhưng server Pack5
không có zone cho server id 26/28/29 → chỉ dựng được ở chế độ offline/local
hoặc phải bổ sung server. Map 29 (mê cung 73×83, LinkMap 0 link) nhiều khả
năng là map sự kiện của bản mới.

### 5.5. Giữ Pack5, đừng xoá (11 map): 13, 20, 61, 62, 63, 64, 66, 67, 68, 106, 108
3.4.4 không đóng gói sẵn các map này, nhưng đây **không phải bằng chứng bị
xoá**: code 3.4.4 vẫn còn nguyên cơ chế nhận map từ server lúc chạy
(`onSelectedMiniMap` / `setMapAny` còn trong metadata; asset tileset
`wedding` 12.064B cho map 20 vẫn còn). Các map phòng shop (61–68), phòng cưới
(20), phố tile (13) và trường đua (108) rất có thể được server cấp động.
Bản port cứ giữ dữ liệu Pack5 cho nhóm này.

### 5.6. Đóng hồ sơ (4 map): 31, 34, 56, 57
Bỏ khỏi danh sách việc phải làm: bản chính đã loại bỏ (§4). Nếu vẫn muốn giữ
chúng như nội dung riêng của port thì phải tự dựng (không còn nguồn gốc nào
để "đúng" theo), và không nên tiếp tục đường bắt dữ liệu server live cho
nhóm này.

---

## 6. File kèm theo

- `docs/datamaps344.json` — lưới đã giải mã của 36 map 3.4.4
  (`{w, h, bg, cells}`), sẵn sàng cho pipeline suy `o/t/d` của port.
- Trích thô byte-chính-xác dùng trong phân tích: `/tmp/maps344/*.bin`
  (DataMap*, Map*, BGMap*, LinkMap*, Map, DataMoveCityMap).

## TIẾN ĐỘ DỰNG LẠI (ghi bổ sung 2026-11-03)
- Đợt 1 (push 5a3cf1859db3): map 16/17/24 đã thay lưới 3.4.4 + mô phỏng lại o/t/d; chỉ 3 map đổi; chụp kiểm chứng PASS.
- Đợt 2 (push c32ee6014767): map 5/10/12/26 đã thay lưới 3.4.4 + o/t/d; map 26 giữ đủ cổng -1/24/52/53 (ô type 93 IceDream không còn trên lưới mới); chụp map 5 + 26 PASS.
- FARM 25 — phân tích hình học (chưa thay): khối đất 142 đổi BỐ CỤC: cũ 4 khối hàng ngang y=1 (x 22/25/28/31, mỗi khối 3 cột x 4 hàng = 12 ô); mới 4 khối 2x2 tại (22,1),(29,1),(22,4),(29,4) (mỗi khối 6 cột x 2 hàng = 12 ô). Khế 189 giữ nguyên (19,1). Khi làm: đổi hình học khối trong FarmWorld (ô i: cũ (i%3, i/3) -> mới (i%6, i/6)), nhập strips Map250-255, rà lại neo hồ/bếp/kho, chạy regression farm đầy đủ. Chỉ số ô phía server không đổi (theo index) nên dữ liệu farm của người chơi an toàn.
