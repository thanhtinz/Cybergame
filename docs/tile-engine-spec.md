# Đặc tả ENGINE TILE — client gốc AvatarDK (Pack5, C#)

Nguồn: `Assets/Scripts/Assembly-CSharp/LoadMap.cs` (3559 dòng), `FrameImage.cs`,
`CRes.cs`, `DataInputStream.cs`, `T.cs` trong Pack5
(`archive_x/project-client-avt-main/`). Mọi khẳng định đều có trích dẫn
`File.cs:dòng`. Tài liệu này đủ để cài lại engine tile trong Java/LibGDX mà
không cần đọc lại C#.

Thuật ngữ xuyên suốt:

- **index** = id map phía CLIENT = id map server + 1. `load(index)` nhận index;
  `TYPEMAP = index - 1` (= id server) (`LoadMap.cs:1965`, `LoadMap.cs:2075`).
- **ô (cell)**: một phần tử của lưới map. Kích thước ô logic `w = 24` đơn vị
  (`LoadMap.cs:335`); khi vẽ nhân với `AvMain.hd` (hệ số phóng: bản "hd" hd=2,
  "medium" hd=1 — `T.cs:667`: `hd = {"normal","medium","hd"}`,
  `T.getPath()` trả về thư mục theo style, `T.cs:1155`).
- **map[]**: `short[]` — giá trị TILE của từng ô (chỉ số frame trong atlas),
  đọc từ file dữ liệu map. **type[]**: `sbyte[]` — LOẠI gameplay của từng ô
  (đi được/không, cổng sang park nào, shop, farm...). Hai mảng song song, cùng
  kích thước `Hmap * wMap`, thứ tự hàng-trước (row-major): ô (x,y) ở vị trí
  `y * wMap + x`; tọa độ pixel của ô `i`: `x(i) = i % wMap * w`,
  `y(i) = i / wMap * w` (`LoadMap.cs:3036-3044`).

---

## 1. Hai đường dựng map và điều kiện chọn (trong `load()`)

`LoadMap.load(int index, bool isCreate)` (`LoadMap.cs:1965`) luôn chạy các
bước sau theo đúng thứ tự:

1. Tính `status` theo giờ máy (xem §3).
2. `loadBG(index - 1)` — nạp nền trời/cây/mây (xem §3).
3. `loadImageMapFull(index)` (`LoadMap.cs:1937`): thử nạp tối đa 10 ảnh lát
   `T.getPath() + "/imageMap/" + index + "/" + i` (i = 0..9, dừng ở ảnh đầu
   tiên null) vào `imgCreateMap[]`, cộng 3 ảnh "dây điện" tùy chọn
   `daydien0/1/2` cùng thư mục. Thư mục trên đĩa:
   `Assets/Resources/hd/imagemap/<index>/`.
4. **Điều kiện rẽ nhánh** (`LoadMap.cs:2007-2015`):

   ```csharp
   if (imgCreateMap == null || index - 1 == 107)
       loadMapImage(index);        // ĐƯỜNG TILE
   else {
       imgMap = null; isCreate = false;   // ĐƯỜNG IMAGEMAP
   }
   ```

   Tức: **map đi đường TILE khi thư mục `imagemap/<index>` không có ảnh nào**
   (hoặc map 108 phía server — map đua, `index-1==107` — luôn ép tile dù có
   ảnh). Ngược lại đi đường IMAGEMAP: khi vẽ, `paintM()` thấy
   `imgCreateMap != null` thì vẽ các lát ảnh nối ngang, neo đáy tại
   `Hmap * w * hd` (`LoadMap.cs:1579-1597`), **không** gọi `paintMap()`.
5. Dù đi đường nào, dữ liệu lưới vẫn được đọc và `setMap()` vẫn chạy
   (`LoadMap.cs:2074`) — lưới `map[]/type[]` vẫn cần cho va chạm, tìm đường và
   cổng chuyển map. Đường imagemap chỉ thay PHẦN VẼ NỀN.

Lưu ý: `idTileImg != -1` là một chế độ thứ ba — map động do server gửi lúc
chạy (phòng tự tạo kiểu JOIN_ANY, xem §7) — không phải lựa chọn lúc load file.

---

## 2. Dữ liệu lưới map: `Resources/map/<index>`

- `loadDataMap(index)` (`LoadMap.cs:1924`): mở stream `"map/" + index` qua
  `DataInputStream.getResourceAsStream`, tức `Resources.Load("map/<index>",
  TextAsset)` (`DataInputStream.cs:18-22`). Trên đĩa:
  `Assets/Resources/map/<index>.bytes` (một số index lưu dạng `.txt`:
  58–68, 101, 104 — cùng cơ chế TextAsset; tổng 43 file).
- File là **chuỗi byte thô, mỗi byte = 1 ô**, đọc vòng lặp
  `map[i] = (byte)ip.readByte(); if (map[i] == 255) map[i] = -1;`
  (`LoadMap.cs:2202-2213`). Giá trị byte 255 → tile −1 = **ô không vẽ**
  (paintMap bỏ qua `num4 == -1`, `LoadMap.cs:1676`).
- **Chiều cao Hmap cố định theo map**, đặt TRƯỚC khi đọc (`LoadMap.cs:2019-
  2073`): mặc định `Hmap = 8`; bảng ngoại lệ theo `index - 1`:
  25→7, 21→7, 9→8, 17→6, 18→10, 19→13, 10→9, 107→16,
  {20,57,58,59,62,63,64,100,101,103,104,108,109}→11, {60,61,65}→5.
- **Chiều rộng suy ra từ kích thước file**: `wMap = ip.available() / Hmap`
  (`LoadMap.cs:2197`). Ví dụ kiểm chứng trên đĩa: map 1 = 208 byte → 26×8;
  map 25 (file, = server 24 farm) = 336 byte → 42×8; map 26 (file) = 189 byte
  → Hmap 7 → 27×7; map 20 (file, đám cưới) = 364 byte → Hmap 13 → 28×13.

---

## 3. `status` (ngày/đêm), nền `bg[]`, và cách chọn `h<status>`

- `status` là `sbyte` tĩnh (`LoadMap.cs:339`), tính lại mỗi lần `load()`:
  giờ hiện tại ≥ 18 hoặc < 6 → `status = 1` (đêm); còn lại `status = 0` (ngày)
  (`LoadMap.cs:1998-2005`).
- `status` quyết định 3 thứ:
  1. **File đầu PNG của atlas tile**: `data/h<status>` — `h0` ngày, `h1` đêm
     (xem §4).
  2. **Màu trời khi không có tile động**: `colorBg = {6143735, 21}` chọn theo
     `colorBg[status]` trong `paintBackGround` (`LoadMap.cs:433`,
     `LoadMap.cs:1713-1720`); sao trời `setStar()` chỉ hiện khi `status != 0`
     (`LoadMap.cs:1900-1905`).
  3. **Hậu tố của mọi ảnh nền**: `loadBG` nạp
     `bgHD/may<bg><status>`, `bgHD/<bg><status>`, `effect/cl<0|1><status>`
     (`LoadMap.cs:482-530`).
- Bảng `bg[]` (26 phần tử, khóa theo TYPEMAP = index−1) (`LoadMap.cs:325-330`):

  ```
  index-1: 0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25
  bg:      1  1  1  1  1  1  1  1  1  0  1  1  3  3  3  3  3  2  -1 -1 -1 0  0  0  2  2
  ```

  `bg == -1` (map 19, 20, 21 phía server) → không có nền trời (`imgBG = null`,
  `paintBackGround` tô đen toàn cảnh). Map 108 (đua) ép `bg = 0`.
  Cache: `loadBG` bỏ qua nếu `rememBg == bg[i] && rememMap == status` không
  đổi (`LoadMap.cs:497-500`).

---

## 4. Atlas tile: file `h<status>` + file `data` = MỘT PNG tách đôi

### 4.1 Cách client dựng ảnh

`loadMapImage(int index)` (`LoadMap.cs:1851`) — trường hợp thường:

```csharp
DataInputStream h    = getResourceAsStream(T.getPath() + "/data/h" + status);
DataInputStream data = getResourceAsStream(T.getPath() + "/data/data");
imgMap = new FrameImage(CRes.createImgByHeader(hBytes, dataBytes), w*hd, w*hd);
```

`CRes.createImgByHeader(header, data)` (`CRes.cs:294-300`) **chỉ nối hai mảng
byte lại** (`header` trước, `data` sau) rồi tạo Image từ PNG hoàn chỉnh. Cache:
nếu `rememMap == status && imgMap != null` thì dùng lại (`LoadMap.cs:1854`).

### 4.2 Cấu trúc thật của hai file (đã kiểm chứng byte trên đĩa, bản `hd`)

Thư mục: `Assets/Resources/hd/data/` (bản `medium` song song, số đo ở ngoặc).

**`h0.bytes` / `h1.bytes` — 874 byte — phần ĐẦU của PNG:**

| Offset | Nội dung |
|---|---|
| 0 | Chữ ký PNG (8 byte) |
| 8 | IHDR (13 byte): **336×864** (medium 168×432), bit depth 8, **color type 3 (palette)**, không interlace |
| 33 | tIME (7), pHYs (9), gAMA (4) — chunk phụ, bỏ qua được |
| 89 | PLTE (768 byte = **256 màu** RGB) |
| 869 | Đầu chunk tRNS: length=1 + chữ `t` (file cắt đúng tại đây) |

`h0` và `h1` cùng kích thước, cùng IHDR; **khác nhau 744/874 byte — chính là
bảng palette PLTE ngày/đêm**. Pixel index dùng chung, chỉ đổi palette → đây là
cơ chế ngày/đêm của tile.

**`data.bytes` — 41.974 byte (medium 17.977) — phần ĐUÔI của PNG:**

- Mở đầu bằng 3 ký tự ASCII **`RNS`** — nối với `t` ở cuối file `h` thành tên
  chunk `tRNS`.
- Tiếp: 1 byte dữ liệu tRNS = `0x00` → **palette index 0 trong suốt hoàn toàn**
  (kiểm chứng: đây là alpha duy nhất ≠ 255), 4 byte CRC.
- Chunk **IDAT** (41.942 byte ở bản hd; 17.945 ở medium): dữ liệu zlib.
- Chunk IEND kết thúc.

**IDAT sau giải nén zlib (kiểm chứng):** 291.168 byte (medium 73.008) =
864 dòng quét × (1 byte filter + 336 byte chỉ số palette). **Mọi dòng đều
filter type 0 (None)** — pixel là chỉ số palette thô, không cần unfilter.
Medium: 432 dòng × 169 byte, cũng filter 0.

Ghép trong Java: `byte[] png = concat(h, data)` → nạp thẳng bằng bộ giải mã
PNG chuẩn (LibGDX `Pixmap`), hoặc tự giải nén IDAT + tra PLTE. **Không có
bảng frame riêng nào khác**: "frame table" của engine chính là phép chia lưới
ở §4.3.

### 4.3 Lớp `FrameImage` — cắt frame bằng lưới đều

`FrameImage.cs` (52 dòng, trích gần trọn):

- Constructor `FrameImage(Image img, int width, int height)` (`FrameImage.cs:
  13-19`): lưu `frameWidth/frameHeight`, tính
  **`nFrame = img.getHeight() / height`** — số frame theo CHIỀU DỌC.
- `drawFrame(idx, x, y, ...)` (`FrameImage.cs:26-32`): cắt vùng
  `(0, idx*frameHeight, frameWidth, frameHeight)` — dải dọc cột 0.
- `drawFrameXY(idx, idy, x, y, ...)` (`FrameImage.cs:40-46`): cắt vùng
  `(idx*frameWidth, idy*frameHeight, frameWidth, frameHeight)` — lưới 2 chiều,
  có kiểm tra `idx < nFrame` (lưu ý: chỉ kiểm tra idx, không kiểm tra idy).
- `drawFrameXY(idx, x, y, ...)` 1 tham số (`FrameImage.cs:48-51`): tự tách
  `col = idx / nFrame`, `row = idx % nFrame`.

Với atlas tile: frame vuông `w*hd` = 48px (hd) / 24px (medium) →
`nFrame = 864/48 = 18`. Atlas = **7 cột × 18 hàng = 126 tile** (id 0..125).

### 4.4 Gán tile cho từng ô — `paintMap`

`paintMap(MyGraphics g)` (`LoadMap.cs:1663-1690`): chỉ vẽ ô trong khung camera;
với mỗi ô `i = row*wMap + col` có `map[i] != -1`:

```csharp
int idx = map[i] / imgMap.nFrame;   // = v / 18  → CỘT trong atlas
imgMap.drawFrameXY(idx, map[i] % imgMap.nFrame,   // v % 18 → HÀNG
                   col * (w*hd), row * w * hd, 0, g);
```

Vậy **giá trị ô v → nguồn atlas (cột = v/18, hàng = v%18)**, đích tại
`(col*24*hd, row*24*hd)` tính từ góc trên-trái của map (hệ tọa độ C# gốc
y-xuống; khi sang LibGDX y-lên phải lật hàng: hàng vẽ = Hmap−1−row).

---

## 5. `loadMapImage` — hai ngoại lệ theo index

Trong `loadMapImage` (`LoadMap.cs:1851-1897`), trước nhánh thường ở §4.1:

- **`index == 20` (file) — map đám cưới (server 19, T_LE_CUOI):**
  `rememMap = -1` (tắt cache) và
  `imgMap = new FrameImage(Image.createImage(T.getPath() + "/wedding"),
  w*hd, w*hd)` (`LoadMap.cs:1861-1865`). Tức atlas KHÔNG lấy từ bộ h/data mà
  lấy ảnh đơn `Assets/Resources/hd/wedding.bytes` (trên đĩa là một file
  `.bytes` riêng), vẫn cắt lưới vuông `w*hd` như thường (nFrame suy từ chiều
  cao ảnh đó). Map này còn có xử lý `setMap` riêng — xem §6.3.
- **`index == 108` (file) — map đua (server 107):** đổi cỡ ô **`w = 12`**
  (thay vì 24), `rememMap = -1`, đặt lệch nền
  `x0_imgBG = 30*hd, x0_imgTreeBg = -30*hd`, rồi `return` — **không nạp
  imgMap trong hàm này** (`LoadMap.cs:1866-1883`); việc dựng cảnh đua do
  RaceScr đảm nhiệm. (Constructor/`load()` đặt lại `w = 24` cho các map khác:
  `LoadMap.cs:445`.)

---

## 6. `setMap` — suy ra `type[]`, vật thể và popup từ `map[]`

`setMap(DataInputStream ip, int index, bool newType)` (`LoadMap.cs:2091`).
`newType = true` khi load từ file (khởi tạo lại `type[]`); `false` khi gọi từ
`setMapAny` (giữ type vừa suy ra — §7). Cuối hàm còn: nạp vật thể map
(`setMapItem`, §6.4), đặt vị trí xuất phát của người chơi tại ô đầu tiên có
`type == TYPEMAP` (quét tuyến tính, dùng `setPosPlayer` nới rộng theo dải ô
liền kề cùng type, `LoadMap.cs:2966-2990`), và nếu đang ở farm người khác thì
đổi `TYPEMAP 24 → 53` (`LoadMap.cs:2959-2962`).

### 6.1 Type mặc định theo giá trị tile (nhánh `default`, `LoadMap.cs:2228-2277`)

Áp cho mọi ô không phải marker đặc biệt (và không thuộc hai map ngoại lệ):

| Giá trị `map[j]` | `type[j]` |
|---|---|
| −4 | 80 (đi được) — giá trị nội bộ do `setMapAny` sinh, §7 |
| −5 | 88 (chặn) — nt |
| −3, −6 | giữ nguyên, không gán |
| −1 | 88 (gán trong switch con, `LoadMap.cs:2281-2283`) |
| 0..6 (`< 7`) | 80 |
| 44..55 | 80 (ghi đè sau) |
| 111, 112 | 80 |
| 114..119 | 80 |
| 120..123 | 80 |
| 20..23 | 79 (ghế thường — ngồi được) |
| 67, 85 | 92 (ghế đỏ) |
| còn lại (7..19, 24..43, 56..66, 68..110, 113, 124..) | 88 (chặn) |
| Đặc biệt: tile 62 (khi không phải các map offline 100/101/103/104 và không phải map 63 file) | 56 (T_JOIN — cổng vào phòng bất kỳ) |

Hai helper dùng khắp nơi:

- `setTypeMap(i, t, m)` (`LoadMap.cs:3046`): `type[i] = t; map[i] = m` — gán
  type ĐỒNG THỜI thay tile vẽ bằng `m` (thường là tile "nền" 0/4/5/6/13/17/
  39/43/46/47/51/55/77/96/112...).
- `setTypePark(i, t)` (`LoadMap.cs:3052`): `type[i] = t; map[i] = 43` nếu ô ở
  hàng 0, ngược lại `map[i] = 6` (tile cổng park chuẩn).

### 6.2 Bảng marker: tile đặc biệt → type + vật thể + popup

Switch con trong `setMap` (`LoadMap.cs:2279-2945`, chạy khi
`ip != null || CLIENT_TYPE != 11`). `setPopup(j, counter, kind)`: chỉ ô ĐẦU
TIÊN của một dải ô liền kề cùng marker (counter == 0) mới sinh popup chữ
"Tương tác" (`T.joinA`; kind 1 = `T.exit`, kind 2 = lệch thêm w/2 xuống)
treo trên dải ô đó (`LoadMap.cs:3491`). `addObjTree(id, x, y, index)` thêm
vật thể tĩnh (ImageObj/SubObject) vào `treeLists` để vẽ theo thứ tự y
(`LoadMap.cs:2997`) — bị vô hiệu khi `idTileImg != -1`.

| Tile | Type gán | Ghi chú (vật thể / hành vi) |
|---|---|---|
| 24, 25, 26 | (giữ 88) | Thêm cây `ImageObj 845` tại ô (trừ map city phần trên) |
| 27 | (giữ 88) | Thêm vật `ImageObj 844` |
| 28 | — | Offline: `map[j] = 4` |
| 63, 65 | 27 (T_POPUP) | Popup shop offline + popup "Tương tác" theo phía map |
| 68, 69 | `72 + counter` | CHỈ khi TYPEMAP ∈ {108, 109} hoặc `isCasino` — ghế bàn casino (xem 184) |
| 97 | 54 (ghế câu cá) | |
| 98 | 29 (điểm chọn khu) | Thêm `ImageObj 846`; ở map casino `map[j] = 56` |
| 102 | 92 | Đăng ký vị trí ngồi xem casino (`BoardScr.listPosAvatar/Casino`) |
| 110 | — | Bảng tên farm: `ImageObj 847`, `FarmScr.posName` |
| 127 | 108 (casino) | Vật 830 + popup; tile vẽ 96 |
| 128 | 55 (ngân hàng) | Popup; tile vẽ lấy từ ô dưới (`map[j+wMap]`) |
| 129 / 160 | 57 / 62 (shop / shop 2) | Vật 829 (map 18 file: 836); tile 96 |
| 130..138 | park `tile − 130` (0..8) | `setTypePark` + popup theo counter riêng từng park |
| 139 | −1 (T_MAPMAIN, thoát) | Popup; ghi `Bus.posBusStop` + chạy xe buýt nếu đang ở city; `setMapPaint` |
| 140 | 25 (đường farm) | Popup |
| 141 | 24 (farm nhà mình) | Tile lấy từ ô dưới |
| 142 | 80 | Ô đất trồng: ghi `FarmScr.posTree[k]` theo thứ tự gặp |
| 143 | 52 (cửa hàng farm) | Tile ô dưới |
| 144 | 53 (farm bạn bè) | |
| 145 | 9 / 17 / 23 theo ngữ cảnh | Cổng về city: TYPEMAP 109-file hoặc (57-file & TYPEMAP 17) → 17 (khu ổ chuột); TYPEMAP 23 → 23; còn lại 9; ở map 101-file tile vẽ 47 |
| 147 / 161 | 58 / 63 (salon / salon 2) | Vật 832; tile 96 |
| 148 / 162 | 59 / 64 (quà / quà 2) | Vật 833; `map[j] = 0` trước khi setTypeMap nên nhánh 162 thực tế cũng cho 59 — lỗi gốc, giữ nguyên khi port |
| 149 | 28 (kho farm) | Chỉ hiện popup khi là farm nhà mình |
| 150 | 93 (kem) | Vật 842; map 26 file → tile nền 4, map khác → 0 |
| 151 | 78 (thức ăn pet) | Vật 843 |
| 152 | 81 (bến xe buýt) | Vật 835; ở farm (25 server) tile nền 55, nơi khác 0 |
| 153 | 11 (đường park) | `setTypePark` |
| 155 | 80; nếu có heo → 84 (máng heo) | Vật −5 (SubObject máng), ghi `Cattle.posPigTr` |
| 156 | 80; nếu có chó → 85 (máng chó) | Vật −6, ghi `Dog.posDosTr` |
| 157 | 80 | Xô sữa: ghi `Cattle.posBucket` (type 86 sinh động khi thu hoạch — xem doJoin 86) |
| 158 | 80 | Ổ gà: ghi `Chicken.posNest` |
| 159 | 89 (top farm/bảng) | Vật 848; tile nền theo map: 26-file→4, 109/110-file→47, 14-file→0 |
| 163 | 12 (park cao cấp) | Popup |
| 164 | 13 (câu cá cao cấp) | Popup, tile 6 |
| 165 | 14, 166 | 15, 167 | 16 (3 điểm câu 1/2/3) | Popup; tile 0/0/43 |
| 172, 173, 174 | 88 | Tile 96 (vật cản trang trí) |
| 175 / 176 / 177 | 68 / 69 / 70 (nhà bạn 1/2/3) | Popup; tile 96 |
| 178 | 109 (casino 2) | Vật 830 + popup; tile 96 |
| 179 | 18 (nhà tù) | Vật 837 + popup; tile 96 |
| 180 | 17 (khu ổ chuột) | Popup; tile 77; map 102-file → tile 0 |
| 181 | 56 (T_JOIN) | Popup "Tương tác" (trừ map offline); tile 46 |
| 182 | 80 | Chuồng: `FarmScr.posBarn`; tile 39 |
| 183 | 88 | Hồ cá: `FarmScr.posPond`; tile 13 |
| 184 | `72 + counter` (0..5) | Sáu bàn casino liên tiếp theo thứ tự gặp: TL/P/CT/CR/DM/BC |
| 185 | 71 (vé máy bay / bản đồ thành phố) | Map 18 file: vật 975 + popup ở ô thứ 3; tile 43 (riêng map 18) / 47 |
| 186 | 94 (shop cá) | Popup ở ô thứ 3; tile 17 |
| 187 | 95 nếu farm nhà mình, ngược lại 80 | Mua/nâng ô đất: SubObject −10 (biển mua đất); tile 4 |
| 188 | 96 nếu farm nhà mình, ngược lại 80 | Nâng hồ cá: SubObject −10; tile 4 |
| 189 | 97 (cây khế) | `FarmScr.starFruil` vào treeLists; tile 4; popup "trom" nếu farm bạn bè & được trộm |
| 190 | 98 (bếp nấu ăn) | Vật 1029; farm người khác → 88 |
| 191 | 23 (khu mua sắm) | Tile vẽ xen kẽ 46/44 theo counter (map 105-file → 0) |
| 192 | 99 (quay số) | Tile 4 |
| 193 | 100 (nhiệm vụ) | Tile 4 |
| 194 | 106 (đấu giá) | Tile 4 |
| 195 | 102 (nâng cấp đồ) | Tile 4 |
| 196 | 103 (shop premium) | Tile 4 |
| 197 | 104 (shop pet) | Tile 4 |
| 198 | 105 (thợ khóa) | Vật 1036; tile 4 |
| 199 | 101 (Mr Doom) | Vật 1031 ở ô thứ 2; tile 4 |
| 200 | 107 (đua) | Vật −1 ở ô thứ 2; tile 5 |
| 201 | 19 (sảnh cưới) | Tile 5 |
| 202 | 88 | Cứ 4 ô thêm vật cây `ImageObj 4` khổ rộng 2 ô |
| 203 | 110 (nhà 4) | Popup; tile 96 |
| 204 | 10 (cổng sảnh cưới) | Tile 43 |

### 6.3 Riêng map đám cưới (`index == 20`, `LoadMap.cs:2161-2200`)

Không dùng bảng §6.1/§6.2: `map < 32` → type 80; còn lại type 88; tile 27 →
đăng ký ghế vào `MapScr.listChair` (index ghế suy từ thứ tự); tile 65 → type
10 (cổng sảnh cưới), tile vẽ đổi thành 1 (cặp đầu tiên) / 16 (cặp thứ hai —
đồng thời teleport người chơi + popup); cuối hàm cộng một Avatar NPC "chu
hon" (IDDB −100, part 2480) đứng ở ô (26, 8).

### 6.4 `setMapItem` / `setMapItemType` — vật thể động chồng lên lưới

Sau lưới tĩnh, `setMap(index)` gọi `setMapItem(index)` (`LoadMap.cs:3138`):
mọi `MapItem` trong `AvatarData.listMapItem` có `type == index` được thêm
vào `treeLists` (vẽ vật thể: ghế, bàn, đồ nội thất...), đồng thời
`setTypeSeat` (`LoadMap.cs:3529`) ghi đè type các ô bị vật che
(`listNotTrans` của MapItemType): iconID 1 → type 79, iconID 2 → type 67,
mặc định 88. Vật quảng cáo (ObjAd) ghi type 83 vào các ô điểm của nó + popup
tiêu đề. Bản đồ động (§7) dùng biến thể `setMapItemType()` lấy dữ liệu từ
server thay vì AvatarData.

---

## 7. Map động từ server: `onTileImg` + `setMapAny` (JOIN_ANY)

Khi server gửi một bộ tile riêng (phòng tự tạo / clan / nhà thuê...):

- `onTileImg(sbyte idTileMap, sbyte[] arr)` (`LoadMap.cs:3368`):
  `idTileImg = idTileMap`;
  `imgMap = new FrameImage(CRes.createImgByByteArray(arr), w*hd, hd*w)`
  — **atlas là một PNG trọn vẹn do server gửi**, không dùng h/data; rồi gọi
  `setMapAny()`.
- `setMapAny()` (`LoadMap.cs:3388`): sao `map[]` gốc vào `array[]`, tạo
  `type[]` mới, rồi diễn giải lại từng ô theo atlas động (nFrame của atlas
  mới). Đặt `num = map[j] − 2*nFrame`:
  - `map[j] < nFrame` → `map[j] = -4` (→ type 80, đi được);
  - `nFrame ≤ map[j] < 2*nFrame` → `map[j] = -5` (→ type 88, chặn);
  - `num == 0` → tile 98 + vật 846 (điểm chọn khu); `num == 2` → tile 139
    (thoát); `num == 3` → tile 152 (xe buýt); `num == 12` → tile 150 (kem);
    `num == 13` → tile 151 (thức ăn pet);
  - `num == 14` → `isCasino = true`, ô thành marker 184 (bàn casino),
    `array[j] = 33`;
  - `num == 15` → marker 185 (vé/bản đồ), `array[j] = 0`;
  - còn lại (default): `type[j] = -125 + num` — **cổng JOIN_ANY tổng quát**
    (xem §8 default), tile −3, kèm popup; nếu `MapScr.idImg[num-1] != -1`
    (bảng id ảnh do server gửi kèm khi chọn minimap, `MapScr.cs:1467`,
    `MapScr.cs:3203`) thì thêm ImageObj biển hiệu tại ô.
  - Hầu hết ô sau diễn giải đều qua `setMapPaint(j, array)` (§6.5) để tile vẽ
    không để lỗ thủng.
- Sau đó: `setMap(null, MapScr.roomID + 1, false)` (diễn giải type bằng chính
  bảng §6 trên các marker vừa sinh, `newType=false`), `map = array`,
  `TYPEMAP = MapScr.roomID`, `setMapItemType()`, và cuối cùng
  `ParkService.doJoinPark(roomID, -1)` để vào phòng thật.

### 6.5 `setMapPaint(i, m)` (đặt ở đây vì dùng chung)

`LoadMap.cs:3376`: sửa giá trị VẼ của ô `i` trong mảng `m` bằng cách **sao
chép ô kề ngang**: ô ở cột 0 lấy ô bên phải (`m[i] = m[i+1]`), còn lại lấy ô
bên trái (`m[i] = m[i-1]`). Dùng cho các ô marker vô hình (139, các ô
JOIN_ANY...) để nền không bị thủng một ô khác màu.

---

## 8. Di chuyển: đi xuyên, tìm đường, chạm vào ô

- `isTrans(x, y)` (`LoadMap.cs:3261`): ô **đi xuyên được** khi type ∈ {80, 51}
  (51 = T_PLANT, ô đất farm); khi nhân vật đang trong trạng thái chạm-tìm
  đường (`task == -5`) thì cộng thêm {79 ghế, 92 ghế đỏ, 81 bến xe, 67 nằm
  nghỉ} — tức được bước LÊN ô đích tương tác để ngồi/nằm.
- `findPath` (`LoadMap.cs:1059`): BFS 4 hướng trên lưới; một ô bị chặn trừ
  khi `type ∈ {80, 51}` hoặc `setTypeJoint(type)`; ô đích được mở khóa nếu
  `setTypeFind(type)`.
- `setTypeJoint(int type)` (`LoadMap.cs:1249`) — **41 giá trị** liệt kê + dải
  −125..−1: các type được phép ĐI VÀO/ĐỨNG LÊN như ô đất thường (cổng park,
  lối đi, cổng farm...):
  `0,1,2,3,4,5,6,7,8,9,11,12,13,14,15,16,17,18,21,24,25,51,52,53,56,68,69,70,
  71,72,73,74,75,76,77,95,96,110,111,112`.
- `setTypeFind(int type)` (`LoadMap.cs:1262`) — **42 giá trị** + dải âm: các
  type được phép làm ĐÍCH của tìm đường (ô tương tác đứng cạnh / bước lên):
  `10,19,23,27,28,29,52,54,55,57,58,59,62,63,64,67,71,78,79,81,83,84,85,86,87,
  89,92,93,94,95,96,97,98,99,100,101,103,104,106,107,108,109`.
- `setTypeFindEnd(int type)` (`LoadMap.cs:1275`) — **38 giá trị**: như
  setTypeFind nhưng bỏ {67, 79, 81, 92} (ghế/bến — đến nơi thì không kích hoạt
  hành động kết thúc).
- `setJoin(int type)` (`LoadMap.cs:888`) — **27 giá trị**: áp cho INDEX vật
  thể (không phải type ô) khi người chơi chạm vào một vật trong `treeLists`:
  `18,24,25,29,52,55,58,59,63,64,68,69,70,89,93,94,97,98,100,103,104,107,108,
  109,110,111,112`.
- Luồng chạm: `updatePointer` → tính ô dưới điểm chạm → nếu type ô là 80,
  hoặc đang ở farm (TYPEMAP 24), hoặc `setTypeJoint(type)`, hoặc
  `setTypeFind(type)` thì `change()` chạy tìm đường (`LoadMap.cs:838-845`);
  khi tới nơi, `updatePathAvatar` → `Avatar.doJoin(dx, dy)` — lớp Avatar
  chuyển tiếp sang `LoadMap.doJoin(x, y)` của §9 khi ô đích là ô tương tác.

---

## 9. `doJoin(x, y)` — bảng type → hành động ĐẦY ĐỦ

`LoadMap.doJoin(int x, int y)` (`LoadMap.cs:1288-1560`): `x, y` là tọa độ
pixel; hàm tự lấy `typeMap = getTypeMap(x, y)` (`LoadMap.cs:3275`; ngoài map
→ −2 → trả false). Switch có **72 nhãn `case`** phủ 71 giá trị + nhánh
`default`. "move()" = `MapScr.gI().move()` (gửi gói di chuyển lần cuối trước
khi chuyển cảnh). Bảng trích nguyên:

| Type | Hành động |
|---|---|
| −1 (T_MAPMAIN) | Thoát về city: nếu đang ở farmway (TYPEMAP 25) gọi `FarmScr.doExitBus()`; nếu có nền trời (`imgBG != null`) → `bus.setBus(-1)` (đi xe buýt về), ngược lại `MapScr.doExit()`. |
| 0,1,2,3,4,5,6,7,8 (park 0–8) | `move()` + `ParkService.doJoinPark(type, -1)` |
| 9 (T_CITY) | Hộp thoại OK "có muốn thoát" → `getHandler(9)` (về city) |
| 10 (T_CONG_SANH_CUOI) | `move()` + `doJoinPark(10, -1)` |
| 11 (T_PARK_PATH) | thuộc nhóm park ở trên: `doJoinPark(11, -1)` |
| 12 (T_PARK_ADVANCED) | Hộp thoại OK thoát → `getHandler(9)` |
| 13,14,15,16 (câu cá) | `doJoinPark(type, -1)` |
| 17 (T_SLUM) | Hộp thoại OK thoát → `getHandler(9)` |
| 18 (T_PRISON) | `doJoinPark(18, -1)` |
| 19 (T_LE_CUOI) | `doJoinPark(19, -1)` (kèm `rememMap = -1`) |
| 20 (T_SAN_BAY) | `GlobalService.requestJoinAny(0)` — cổng sân bay = JOIN_ANY id 0 |
| 21 (T_ROAD_HOUSE) | `HouseScr.doOut()` — ra khỏi nhà |
| 23 (T_KHU_MUA_SAM) | `GlobalService.getHandler(9)` + chờ |
| 24 (T_FARM) | Farm nhà mình: chưa có dữ liệu → `FarmScr.doJoinFarm(IDDB, true)`; đã có → `FarmScr.onJoin(...)` dựng lại ngay |
| 25 (T_FARMWAY) | `FarmScr.doGoFarmWay()` |
| 27 (T_POPUP), 56 (T_JOIN) | CHỈ offline (`CLIENT_TYPE == 8`) và không ở casino/nhà tù: `MapScr.doOpenShopOffline(avatar, 0)` |
| 28 (T_STORE) | `FarmScr.doOpenKhoHang()` — mở KHO farm |
| 29 (T_CHECK_POINT) | `ParkService.doRequestBoardList(roomID)` — danh sách bàn/phòng |
| 52 (T_CUAHANG) | `FarmScr.doOpenCuaHang()` — cửa hàng farm |
| 53 (T_FARM_FRIEND) | `FarmScr.doMenuFarmFriend()` |
| 54 (T_FISHING_CHAIR) | `FishingScr.doSat(x, y)` — ngồi câu (trả về kết quả của doSat) |
| 55 (T_BANK) | `GlobalService.requestChargeMoneyInfo()` — ngân hàng / nạp tiền |
| 57 (T_SHOP) | `move()` + `MapScr.doJoinShop(1)` |
| 58 (T_BEAUTYSALON) | `doJoinShop(2)` |
| 59 (T_GIFT) | `doJoinShop(3)` |
| 62 (T_SHOP_2) | `move()` + `doJoinShop(6)` |
| 63 (T_BEAUTYSALON_2) | `doJoinShop(7)` |
| 64 (T_GIFT_2) | `doJoinShop(8)` |
| 68,69,70 (T_HOUSE_1/2/3) | `HouseScr.doJoinFriendHome(type − 67)` |
| 71 (T_VE_BAY) | `GlobalService.requestCityMap(-1)` — mở bản đồ thành phố |
| 72..77 (TL/P/CT/CR/DM/BC) | Casino: ghi `indexMap`, tọa độ quay lại; riêng type 74 (CT) khi `Canvas.iOpenOngame == 1` → `requestJoinAny(4)`; còn lại `MapScr.doGetHandlerCasino(type − 72)` |
| 78 (T_FOOD_PET) | `MapScr.doOpenIceDream(T.food, 5)` |
| 83 (T_AD) | `getAd(x/w, y/w)` — quảng cáo tại ô |
| 84 (T_PIG_TROUGH) | `FarmScr.doCattleFeeding(2, 5)` — cho heo ăn |
| 85 (T_DOG_TROUGH) | `FarmScr.doCattleFeeding(3, 5)` — cho chó ăn |
| 86 (T_MILK_BUCKET) | Thu hoạch sữa: `doHarvestAnimal(2, posÔ − posXô, listBucket)` |
| 87 (T_NEST) | Thu trứng: `doHarvestAnimal(1, posÔ − posỔ, listNest)` |
| 89 (T_TOP_FARM) | `GlobalService.doCommunicate(n)`: n=1 nếu ở casino (TYPEMAP 108/109), n=2 nếu câu cá cao cấp (13), còn lại n=3 — nói chuyện NPC theo ngữ cảnh |
| 93 (T_ICE_CREAM) | `MapScr.doOpenIceDream(T.food, 4)` |
| 94 (T_FISH_SHOP) | `doCommunicate(4)` — NPC shop cá |
| 95 (T_UPDATE_CATTLE) | `FarmService.doUpdateFarm(0, 0)` (ghi nhớ vị trí trước) |
| 96 (T_UPDATE_FISH) | `FarmService.doUpdateFish(0, 0)` |
| 97 (T_STAR_FRUIT) | `FarmScr.doMenuStarFruit()` — menu cây khế |
| 98 (T_COOKING) | `FarmScr.doOpenCooking()` — bếp |
| 100 (T_TASK) | `MapScr.doJoinMapOffline(5)` — bản đồ nhiệm vụ offline |
| 101 (T_MR_DOOM) | `doJoinMapOffline(6)` |
| 103 (T_PRIMEUM_SHOP) | `doJoinMapOffline(3)` |
| 104 (T_PET_SHOP) | `doJoinMapOffline(4)` |
| 107 (T_RACE) | `GlobalService.getHandler(12)` — handler đua |
| 108, 109 (T_CASINO / T_CASINO_2) | `requestJoinAny(4)` (ghi tọa độ quay lại, `idCityMap = 1`) |
| 110 (T_HOUSE_4) | `AvatarService.doJoinHouse4(IDDB)` |
| 111 (T_FLOWER_LOVE) | `GlobalService.doFlowerLove()` |
| **default: 112** (T_HO_HOUSE) | Nếu nhân vật chưa ở tư thế 14: ghi vị trí, dịch tới giữa ô, `doAction(14)` + gửi cảm xúc 14 (tư thế "hồ"/ngồi đặc biệt tại ô) |
| **default: −125..−1** (T_JOIN_ANY + n) | `requestJoinAny(typeMap + 125)` — cổng tổng quát của map động §7 |
| default: còn lại | trả `false` (không làm gì) |

Các type có tên trong bảng hằng (§10) nhưng KHÔNG xuất hiện ở doJoin (chỉ
dùng cho va chạm/tìm đường/vẽ): 22 (không có hằng), 26, 30–51 trừ 51, 60, 61
(board chờ), 65, 66, 67 (nằm nghỉ), 79 (ghế), 80 (trống), 82 (vật thể), 88
(chặn), 90 (cấm vào), 91 (cấm câu), 92 (ghế đỏ), 99 (quay số — chỉ nằm trong
setTypeFind; hành động quay số đi qua đường khác), 102 (nâng cấp), 105 (thợ
khóa), 106 (đấu giá).

---

## 10. Bảng hằng số type (`LoadMap.cs:195-431`)

Đầy đủ theo file (giá trị sbyte): 0–8 park 0–8; 9 CITY; 10 CONG_SANH_CUOI;
11 PARK_PATH; 12 PARK_ADVANCED; 13 FISHING_ADVANCED; 14/15/16 FISING_1/2/3;
17 SLUM; 18 PRISON; 19 LE_CUOI; 20 SAN_BAY; 21 ROAD_HOUSE; 23 KHU_MUA_SAM;
24 FARM; 25 FARMWAY; 27 POPUP; 28 STORE; 29 CHECK_POINT; 51 PLANT; 52 CUAHANG;
53 FARM_FRIEND; 54 FISHING_CHAIR; 55 BANK; 56 JOIN; 57 SHOP; 58 BEAUTYSALON;
59 GIFT; 60/61 BOARD_WAIT_2/4; 62 SHOP_2; 63 BEAUTYSALON_2; 64 GIFT_2;
65 BOARD_WAIT_5; 67 NAM_NGHI; 68/69/70 HOUSE_1/2/3; 71 VE_BAY; 72 TL; 73 P;
74 CT; 75 CR; 76 DM; 77 BC; 78 FOOD_PET; 79 CHAIR; 80 EMPTY; 81 BUS_STOP;
82 OBJECT; 83 AD; 84 PIG_TROUGH; 85 DOG_TROUGH; 86 MILK_BUCKET; 87 NEST;
88 ROCK; 89 TOP_FARM; 90 NOTENTER; 91 NOT_FISHING; 92 CHAIR_RED; 93 ICE_CREAM;
94 FISH_SHOP; 95 UPDATE_CATTLE; 96 UPDATE_FISH; 97 STAR_FRUIT; 98 COOKING;
99 QUAY_SO; 100 TASK; 101 MR_DOOM; 102 UPGRADE; 103 PRIMEUM_SHOP; 104 PET_SHOP;
105 THO_KHOA; 106 DAU_GIA; 107 RACE; 108 CASINO; 109 CASINO_2; 110 HOUSE_4;
111 FLOWER_LOVE; 112 HO_HOUSE; −1 MAPMAIN; −125 JOIN_ANY (mốc: type âm
−125+n = cổng JOIN_ANY id n).

---

## 11. Ghi chú cài đặt cho bản port Java/LibGDX

1. **Tài nguyên cần trích từ Pack5**: `Resources/map/*` (43 file lưới),
   `Resources/hd/data/{h0,h1,data}.bytes` (+ bản medium nếu cần hd=1),
   `Resources/hd/wedding.bytes`, `Resources/hd/bgHD/*`, `Resources/hd/imagemap/
   <index>/*` (cho các map đi đường imagemap), `Resources/hd/effect/*`
   (mây cl0/cl1 theo status, focus, s0, den).
2. **Ghép PNG**: `h<status> ++ data` là một PNG palette 336×864 hoàn chỉnh —
   nạp bằng Pixmap/Gdx2DPixmap hoặc giải nén IDAT thủ công (filter luôn 0).
   Giữ NGUYÊN hai biến thể palette để đổi ngày/đêm bằng cách hoán palette,
   không cần hai atlas pixel.
3. **Hệ tọa độ**: C# gốc dùng y-xuống từ góc trên-trái của map; công thức
   frame `col = v/18, row = v%18` tính trong không gian atlas gốc (hàng 0 =
   đỉnh ảnh). Khi vẽ bằng LibGDX (y-lên) phải lật cả vị trí ô lẫn thứ tự hàng
   atlas một cách nhất quán.
4. **Thứ tự dựng lại một map**: đọc lưới → Hmap theo bảng §2 → wMap = len/Hmap
   → setMap đầy đủ (type mặc định §6.1 + marker §6.2 + vật thể §6.4) → chọn
   đường vẽ theo §1. Bỏ qua bất kỳ lớp nào trong setMap đều làm sai va chạm/
   cổng chuyển map dù hình vẽ trông đúng.
5. **Ô 255 → −1** là ô duy nhất không vẽ; tile −4/−5/−3/−6 là giá trị nội bộ
   phát sinh khi diễn giải (marker/setMapAny), không có trong file gốc.
6. Map đám cưới (file 20) và map đua (file 108) có đường riêng (§5, §6.3) —
   đừng chạy chúng qua bảng marker chung.
