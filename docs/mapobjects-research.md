# Lớp vật thể gắn map (addObjTree) — nghiên cứu + dữ liệu trích

Nguồn sự thật: `LoadMap.cs` (Pack5, client C#) — hàm `setMap` (dòng 2091),
`addObjTree` (3015/3026), `getWTileImg` (3483); đối chiếu `MapScr.cs`,
`ImageObj.cs`, `SubObject.cs`, `AvatarData.cs`, server `Session.doGetImgIcon`.
Dữ liệu đã ghi vào `assets/data/mapgrids.json`: mỗi map thêm khóa `"o"` =
`[[imgId, anchorX, anchorY], ...]` (đơn vị **medium**, neo **đáy–giữa** —
`ImageObj.paint` vẽ với anchor 33 = BOTTOM|HCENTER), theo đúng thứ tự quét
row-major của gốc. Script mô phỏng: `tools/extract_map_objects.py` (chạy lại sẽ sinh lại đúng khóa "o").

## 1. Tham số `index` của setMap là gì?

**`index` = map id CLIENT (server id + 1).** Bằng chứng:

- `MapScr.cs:2013`: `Canvas.loadMap.load(roomID1 + 1, true)` — roomID1 là id
  phòng/map phía server, cộng 1 trước khi gọi `load`.
- `LoadMap.load(index, ...)` gọi `setMap(dataInputStream, index, true)` rồi
  ngay sau đó gán `TYPEMAP = index - 1` (TYPEMAP luôn là id server — vd điều
  kiện xe buýt `TYPEMAP == -1` là trạng thái offline/chưa vào map).
- `setMapAny` (nội thất nhà): `setMap(null, MapScr.roomID + 1, false)` — cùng
  quy ước +1, và `newType=false`.
- Bên trong vòng quét, code tự dùng `index - 1` mỗi khi cần id server
  (vd `index - 1 != 9`, `index - 1 != 23`, `index - 1 == 25`).

Hệ quả cho các `switch(index)` lồng nhau (giá trị là **client id**):

| switch(index) | Client | Server | Ý nghĩa kiểm chứng |
|---|---|---|---|
| case 18 (ô 129/160) | 18 | 17 | Map farm-path (load(): index−1=17 → colorFarmPath, Hmap=6): shop ở đây là bản **836** thay vì 829. Map 18 trong dữ liệu không có ô 129/160 nên nhánh này không phát sinh vật thể nào trong 40 map (xem §5). |
| case 24 (ô 129/160) | 24 | 23 | **Không đặt vật thể shop** — dải imagemap của map 24 đã vẽ sẵn Shop/Mỹ Viện/Gift trong tranh (đã kiểm chứng bằng screenshot bản port: thấy Shop + Mỹ Viện + Gift trên dải). Cùng lý do, ô 147/148 bị chặn bởi `index - 1 != 23`. |
| case 14 (ô 159) | 14 | 13 | Chỉ đổi `m` (type nền dưới bảng xếp hạng) = 0 thay vì 4/47; vật thể 848 không đổi. |
| case 26 (ô 150/159) | 26 | 25 | Map farm chính; chỉ đổi type/m của ô, vật thể (842/848) không đổi. |
| case 109/110 (ô 159) | 109/110 | 108/109 | Map đua/casino: `m` = 47; vật thể 848 không đổi. Không có trong 40 lưới. |

`switch(index)` NGOÀI vòng quét: client **20** (phòng cưới — chỉ dựng
listChair + NPC "chu hon") và client **108** (map đua — chỉ spawn avatar
đua) thay toàn bộ vòng quét → hai map này có `"o": []` dù lưới có ô đặc biệt.

## 2. Bảng đầy đủ các case sinh vật thể

Neo "tile" = overload `addObjTree(id, j, idx)`: neo = `(x(j) + getWTileImg(j),
y(j) + 24 − 4)`, trong đó `getWTileImg(j)` = (độ dài dải **phẳng** cùng mã
tính từ j — có thể tràn qua biên hàng) × 24 / 2. Neo "explicit" = đúng biểu
thức trong case (x = x(j), y = y(j), ô 24×24 medium). Cột "idx" là tham số
thứ 4 của addObjTree (khóa sắp xếp vẽ của gốc). "Ô đầu" = bộ đếm b* của
setMap == 0 (reset mỗi map).

| Mã ô | Vật thể (idx) | Neo | Điều kiện |
|---|---|---|---|
| 24, 25, 26 | 845 (−1) | explicit (x+12, y+24) | **Mọi ô**; `newType`; nếu server==9 (map 10) chỉ khi `hàng > wMap/2` — so HÀNG với nửa BỀ NGANG (quirk gốc) nên map 10 (wMap=42, 8 hàng) không bao giờ thỏa → map 10 không có 845 dù có 5 ô 24/26 |
| 27 | 844 (−1) | explicit (x+11, y+1) | **Mọi ô**; `newType` |
| 127 | 830 (108) | explicit (x+36, y+22) | Ô đầu (b34, **dùng chung với ô 178**); server != 9 (map 10 bị chặn — nhà đã có trong dải tranh) |
| 129, 160 | 829 (57/62) | tile | Ô đầu (b2); switch(index): client 18 → **836**, client 24 → **không đặt**, còn lại 829 |
| 147, 161 | 832 (58/63) | tile | Ô đầu (b6); server != 23 |
| 148, 162 | 833 (59/64) | explicit (x+48, y+22) | Ô đầu (b7); server != 23 |
| 150 | 842 (93) | tile | Ô đầu (b33) |
| 151 | 843 (78) | tile | Ô đầu (b35) |
| 152 | 835 (81) | tile | Ô đầu (b9) |
| 155 | **−5** (84) | explicit tâm ô (x+12, y+12) | **Mọi ô**; chỉ khi `Cattle.numPig > 0` (máng lợn — SubObject vẽ bằng paintTrough) |
| 156 | **−6** (85) | explicit tâm ô (x+12, y+12) | **Mọi ô**; chỉ khi `Dog.numBer > 0` (máng chó — paintDogTr) |
| 159 | 848 (89) | explicit (x+12, y+20) | Ô đầu (b11); `m` nền thay theo index (26→4, 109/110→47, 14→0) nhưng vật thể không đổi |
| 178 | 830 (109) | explicit (x+24, y+22) | Ô đầu (b34 — **dùng chung với ô 127**: ô nào quét trước thắng) |
| 179 | 837 (18) | tile | Ô đầu (b17) — **b17 bị tăng bởi các ô 175, 176, 177, 180** quét trước đó; nếu một ô như vậy đứng trước ô 179 đầu tiên thì 837 mất |
| 185 | 975 (71) | explicit (x+24, y+24) | b37 == 1 (ô 185 "thứ hai") **và** client == 18; b37 tăng 1/ô nếu client 18, tăng **2/ô** nếu client khác (setPopup(b37++) + b37++ cuối case), và bị **reset về 0 bởi mọi ô rơi vào case default** |
| 187 | **−10** (add thẳng treeLists) | explicit (x+20, y+20) | Ô đầu (b39) **và** farm của mình (`FarmScr.idFarm == avatar.IDDB`); vẽ biển mua đất FarmScr.imgBuyLant, neo BOTTOM\|RIGHT |
| 188 | **−10** (add thẳng) | explicit (x+20, y+20) | **Mọi ô**; farm của mình |
| 190 | 1029 (98) | explicit (x+36, y+20) | Ô đầu (b40) |
| 198 | 1036 (105) | explicit (x+12, y+20) | **Mọi ô** |
| 199 | 1031 (101) | explicit (x+24, y+24) | Ô "thứ hai" (b10 == 1) |
| 200 | **−1** (107) | explicit (x+24, y+24) | Ô "thứ hai" (b25 == 1); SubObject −1 **không vẽ gì** (case paint rỗng) — vật thể vô hình, chỉ mang popup/vùng tương tác |
| 202 | 4 (88) | explicit (x+48, y+24) | `b13 % 4 == 0` (ô #0, #4, #8, …) |
| 98 | 846 (29) | tile | **Mọi ô** (mỗi ô một biển KHU; neo lệch dần theo phần dải còn lại) |
| 110 | 847 (−1) | explicit (x+11, y) | **Mọi ô** (bảng tên farm) |

Các case còn lại **không sinh vật thể** (chỉ popup/type/vị trí — liệt kê để
khỏi tra lại): −1 (byte 255), 28, 63, 65, 68, 69, 97, 102 (vị trí board
casino), 128, 130–138 (park), 139 (popup + điểm dừng xe buýt), 140, 141, 142
(vị trí cây farm), 143, 144, 145, 149, 153, 157 (vị trí xô), 158 (vị trí ổ
gà — không add object), 163–167, 172–174, 175–177 (chỉ tăng b17), 180 (tăng
b17), 181, 182 (vị trí chuồng), 183 (vị trí ao), 184, 186, 189 (cây khế:
add `FarmScr.starFruil` — SubObject động dùng chung, không có imgId, vị trí
= ô 189; **không đưa vào "o"**), 191–197, 201, 203, 204.

Điều kiện bao ngoài mọi addObjTree: `idTileImg == -1` (chế độ nội thất
idTileImg != -1 chặn toàn bộ) và vòng quét chỉ chạy khi vào map bình thường.

## 3. Thống kê dữ liệu đã trích

- **Tổng: 132 vật thể / 40 map**; 25 map có vật thể, 15 map rỗng (20, 58–66,
  101, 102, 104, 105, 108 — nội thất/casino/đua + map 20/108 bị switch ngoài
  thay vòng quét).
- Map nhiều nhất: **map 12 (Bến xe công viên) — 13**; tiếp theo 13/23 (12),
  22 (11), 24 (9), 9 (8).
- Theo id: 845×58, 846×31, 835×7, 842×6, 843×6, 844×3, 829×2, 832×2, 833×2,
  848×2, 4×2, −10×2, 830×1, 837×1, 847×1, 1029×1, 1031×1, 1036×1, −1×1,
  −5×1, −6×1.
- **Id âm** (SubObject trang trại/khác, tập trung ở map 25 = map thăm farm
  và map 10): −5 (map 25, máng lợn), −6 (map 25, máng chó), −10 ×2 (map 25,
  biển mua đất), −1 (map 10 tại (720,48) — vô hình). Đều đã ghi vào "o" với
  id âm như yêu cầu.
- Giả định mô phỏng cho biến ngoài lưới (chỉ ảnh hưởng các ô farm ở map 25):
  `Cattle.numPig > 0`, `Dog.numBer > 0`, farm của mình (idFarm == IDDB) —
  đều lấy **đúng/đặt được**. Nếu runtime muốn đúng gốc tuyệt đối: −5/−6 chỉ
  vẽ khi farm có vật nuôi tương ứng; −10 chỉ hiện ở farm của chính người
  chơi. Map 26 (farm chính) trong Pack5 **không chứa** các ô 155/156/187/188
  — các ô đó chỉ có ở map 25.

## 4. Tồn tại ảnh + khả thi cho từng id

Cơ chế gốc (ImageObj.paint): trước tiên nạp `home/<id>.png` từ bundle
client; **nếu không có file**, `AvatarData.paintImg` → `getImgIcon(id)` →
client gửi cmd **−80 (GET_IMG_ICON)** và server trả PNG thô đọc từ
`res/hd/object/<id>.png` (server `Session.doGetImgIcon`). Tức là: file
thiếu ở client **không phải** ảnh cắt atlas — `assets/data/imgdata.json`
của bản port có mục trùng số (vd id 829 → crop 10×10) nhưng đó là bảng
icon/item khác, **không dùng được** cho các tòa nhà này (kích thước sai
hoàn toàn).

| id | client `hd/home/` | server `res/hd/object/` | Kết luận cho bản port |
|---|---|---|---|
| 829 | **không có** | có (192×208) | Khả thi: chép từ server res |
| 830 | **không có** | có (192×196) | Khả thi: chép từ server res |
| 832 | **không có** | có (192×228) | Khả thi: chép từ server res |
| 833 | **không có** | có (192×186) | Khả thi: chép từ server res |
| 836 | **không có** | có (124×132) | Khả thi: chép từ server res (dù 40 map không map nào kích hoạt) |
| 975 | **không có** | có (192×114) | Khả thi: chép từ server res (không map nào kích hoạt) |
| 1029 | **không có** | có (152×139) | Khả thi: chép từ server res |
| 4 | có (194×178 — ngôi nhà) | có nhưng **khác hẳn** (24×24, item nhỏ) | Dùng bản **client home/4.png** như gốc; TUYỆT ĐỐI không lấy object/4.png (trùng số, khác namespace) |
| 835, 837, 842, 843, 844, 845, 846, 847, 848, 1031, 1036 | có | có (cùng kích thước; bản server là encode lại) | Dùng bản client như hiện tại |
| −1, −5, −6, −10 | — | — | Không có PNG theo id: −1 vô hình; −5/−6 vẽ thủ tục bằng ảnh farm (paintTrough/paintDogTr); −10 dùng FarmScr.imgBuyLant. Thuộc đợt Farm |

Thư mục server `res/hd/object/` có 4.111 file — nguồn offline đầy đủ, bản
port đã giữ server res trong repo (`server/res`) nên không cần mạng.

## 5. Case đặc biệt / điều kiện lạ (đã mô phỏng đúng từng chữ)

1. **Bộ đếm dùng chung**: b34 chung cho ô 127 và 178 (cùng nhà 830, hai neo
   khác nhau — map 21 chỉ sinh 1 vật thể 830 tại (132,262) từ case 127);
   b17 chung cho 175/176/177/179/180; b37 của ô 185 bị case `default` reset.
2. **Client 24 tự triệt tiêu shop**: ô 129 có 4 ô ở map 24 nhưng switch
   index==24 bỏ qua; ô 147/148 bị server==23 chặn — khớp tranh dải có sẵn.
3. **Map 10 (server 9)**: ô 127 bị chặn (không có 830), ô 24/26 không sinh
   845 vì quirk so hàng với wMap/2. Nhưng ô 200 của map 10 vẫn sinh
   SubObject −1 vô hình tại (720,48) — đúng gốc, đừng "sửa".
4. **836 và 975 không bao giờ xuất hiện** trong 40 lưới: 836 cần map client
   18 có ô 129/160 (map 18 không có); 975 cần ô 185 (không map nào có).
5. **getWTileImg là dải phẳng** (flat index) — dải cùng mã có thể nối qua
   biên hàng; script mô phỏng giữ nguyên tính chất này.
6. `orderVector(treeLists)` sắp xếp lại thứ tự vẽ sau khi quét (theo
   index/y) — thứ tự trong "o" là thứ tự quét; khi dựng hình nên sắp xếp
   theo anchorY như `paintObject` của gốc (trộn với nhân vật theo y).
7. Ô 189 (cây khế) và các vị trí farm (142/157/158/182/183) không phải
   ImageObj — xử lý ở đợt Farm.

## 6. Kiểm chứng

- Map 24: `"o"` chứa **đúng một** `[835, 636, 140]` (ô 152 đầu tiên tại
  (24,5), dải 5 ô: neo x = 24×24 + 5×24/2 = 636, y = 6×24 − 4 = 140) ✓;
  không có 829/832/833 dù lưới có ô 129/147/148 ✓ (đúng điều kiện client 24).
- Map 10: `[835, 300, 140]` (dải 152 tại cột 10–14 hàng 5: 10×24 + 60 =
  300) ✓; không có 830 dù có 3 ô 127 ✓; có −1 tại (720,48) từ ô 200 thứ
  hai (các ô 200 tại (28..31, 1)) ✓.
- Map 13 và 23 cho cùng một hàng shop (832@96, 833@240, 829) — hai map
  chị em dùng chung bố cục ✓.
- Map 22: 8 ô 202 → đúng 2 vật thể id 4 (ô #0 và #4) ✓.
- Toàn vẹn file: 40 map giữ nguyên `w`/`g`/`s`, chỉ thêm `o`.
