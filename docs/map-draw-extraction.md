# Trích mảng TILE VẼ "d" cho mapgrids.json — mô phỏng LoadMap.setMap

Ngày làm: 2026-11-03. Công cụ: `tools/extract_map_objects.py` (hàm mới
`simulate_draw`, chạy cùng một lượt với `simulate`/`simulate_types` để
o/t/d nhất quán). Nguồn sự thật: `LoadMap.cs` của Pack5, hàm `setMap`
(dòng 2091–2990) + `setTypeMap`/`setTypePark`/`setMapPaint` (dòng
3046–3064, 3376) — đọc trực tiếp code C#, không chỉ dựa vào bảng §6.2
của `docs/tile-engine-spec.md`.

## Phương pháp

- Với mỗi map: bắt đầu từ mảng gốc trong khóa `g` (byte 255 → −1, còn
  lại giữ 0..254 như `map[]` short của gốc), quét row-major
  j = 0..wMap*Hmap−1, áp đúng đột biến của case tương ứng lên chính ô j
  (đột biến tại chỗ, một list duy nhất). Nhờ quét row-major:
  - case 128/141/143 đọc `map[j+wMap]` = giá trị GỐC của ô dưới (chưa
    quét tới) — khớp ghi chú trong yêu cầu;
  - `setMapPaint` (case 139): cột 0 sao ô PHẢI (chưa quét = giá trị
    gốc), cột khác sao ô TRÁI (đã quét xong = giá trị vẽ cuối của ô đó).
- Đường load() thường: `newType=true`, `idTileImg=−1`, `TYPEMAP` lúc
  nạp = −1 — cùng giả định với phần `o`/`t` sẵn có. Pre-pass đầu vòng
  quét chỉ gán `type[]`, không chạm `map[]`.
- Nếu case 128/141/143 hoặc setMapPaint đọc ngoài mảng, C# gốc văng
  ngoại lệ và toàn bộ setMap dừng (try bao cả vòng lặp) — đã mô phỏng
  đúng hành vi dừng + ghi cảnh báo. Thực tế không map nào kích hoạt
  (đã kiểm: không ô 128/141/143 nào nằm ở hàng cuối).
- Kết quả lưu ở khóa `d`: base64 của các byte `v & 0xFF` row-major.
  Với mọi giá trị trong [−1, 125] cách đóng gói này trùng khớp sbyte;
  riêng giá trị 128 (xem Ngoại lệ 2) giữ byte 128 như `map[]` short gốc.
- Map client 20 (cưới): vòng riêng của `switch(index)` — chỉ tile 65
  đổi: ô đầu → 1, ô thứ hai → 16 (đồng thời teleport + popup), từ ô
  thứ ba → 1 (bộ đếm b42). Map 108: bỏ qua, không có khóa `d`.
- Thêm 3 map còn thiếu từ `Resources/map/`: client 67 (`67.txt`), 68
  (`68.txt`), 106 (`106.bytes`). Cả ba dùng Hmap = 8 (mặc định; server
  66/67/105 không có case riêng trong bảng Hmap), `wMap = size // 8`
  (đúng công thức gốc `available()/Hmap`), chỉ lấy wMap*Hmap byte đầu:
  67/68 → w=27, 216 ô (file 220 byte, bỏ 4 byte cuối); 106 → w=19,
  152 ô (file 154 byte, bỏ 2 byte cuối). Sinh đủ g/w/o/t/d bằng cùng
  các hàm mô phỏng; cả ba không có ô 139 nên không có khóa `s`.

## Các case đột biến tile vẽ đã cài (theo C# gốc)

| Case | Tile vẽ sau setMap |
|---|---|
| 127, 129, 160, 147, 161, 148, 162, 172, 173, 174, 175, 176, 177, 178, 179, 202, 203 | 96 (qua setTypeMap) |
| 128 | bằng ô dưới (setTypeMap(55, 20) rồi ghi đè `map[j+wMap]`); CHỈ khi server ≠ 25 |
| 130–138, 153 | setTypePark: 43 ở hàng 0, 6 ở hàng khác |
| 139 | setMapPaint: sao ô kề (phải nếu cột 0, trái nếu không) |
| 140 | 55 |
| 141 | bằng ô dưới (setTypeMap(24, 5) rồi ghi đè) |
| 142 | 4 — setTypeMap(80, 4); xem Ngoại lệ 1 |
| 143 | bằng ô dưới (setTypeMap(52, 51) rồi ghi đè) |
| 144 | 5 |
| 145 | −1; riêng client 101 (server 100) → 47 |
| 149 | 4 |
| 150 | 4 nếu client 26, còn lại 0 |
| 151 | 0 |
| 152 | 55 nếu server 25 (client 26), còn lại 0 |
| 155 | 55, rồi 112 khi numPig > 0 (giả định có — xem Giả định) |
| 156 | 5 (cả hai nhánh numBer) |
| 157 | 111 |
| 158 | 5 |
| 159 | client 26 → 4; client 109/110 → 47; client 14 → 0; mặc định 4 |
| 163 | −1 |
| 164 | 6 |
| 165, 166 | 0 |
| 167 | 43 |
| 180 | 77; riêng client 102 (server 101) → 0 |
| 181 | 46; giữ nguyên ở các map server 100/101/103/104 |
| 182 | 39 |
| 183 | 13 |
| 185 | client 18 → 43, còn lại 47 |
| 186 | 17 |
| 187, 188 | 4 |
| 189, 190 | 4 (gán trực tiếp) |
| 191 | server 104 → 0; còn lại xen kẽ 46/44 theo bộ đếm b18 |
| 192–199 | 4 |
| 200, 201 | 5 |
| 204 | 43 |
| 98 | 56 chỉ ở map casino (server 108/109 — không có trong danh sách map) |
| 65 (chỉ map 20) | 1 / 16 / 1 theo bộ đếm b42 |

Các case KHÔNG chạm tile vẽ (giữ nguyên giá trị gốc): −1, 24, 25, 26,
27, 28 (khi đang kết nối — xem Giả định), 63, 65 (ngoài map 20), 68,
69, 97, 102, 110, 184 (chỉ gán type), và default.

## Kết quả kiểm chứng (assert trong script, đều PASS)

- (a) Mọi giá trị trong `d` của 42 map có `d` thuộc {−1} ∪ [0, 125] —
  ngoại lệ duy nhất ở Ngoại lệ 2 dưới đây. Không có marker nào lọt qua
  chưa được xử lý.
- (b) Mọi ô không thuộc tập marker có thể đổi tile đều giữ nguyên giá
  trị gốc (đối chiếu từng ô, cả 43 map).
- (c) Thống kê ô đổi tile theo map (tổng **426 ô** / 42 map):
  1:11, 2:4, 3:6, 4:5, 5:6, 6:4, 7:6, 8:6, 9:8, 10:32, 11:8, 12:16,
  13:36, 14:24, 15:2, 16:2, 17:3, 18:15, 19:5, 20:3, 21:12, 22:48,
  23:36, 24:38, 25:22, 26:17, 58:4, 59:4, 60:3, 61:0, 62:0, 63:4,
  64:4, 65:3, 66:0, 67:6, 68:6, 101:4, 102:3, 104:4, 105:3, 106:3.
- (d) Kiểm chứng tay:
  - map 24, ô 147 đầu tiên (tọa độ (2,1)) → **96** ✓
  - map 24, ô 139 đầu tiên (tọa độ (24,7), cột ≠ 0) → **7**, bằng giá
    trị vẽ cuối của ô kề trái theo setMapPaint ✓
  - map 25, ô 189 → **4** ✓
  - map 25, cả 4 ô 142 → **4** theo C# gốc — xem Ngoại lệ 1.
- Đối chiếu độc lập sau khi ghi file: 40 map cũ giữ nguyên từng khóa
  g/w/s/o/t (chỉ thêm `d`); map 108 không có `d`; mọi `d` có độ dài
  bằng `g`; file giữ format compact (separators `,`/`:`); thứ tự khóa
  map giữ như cũ (sắp theo chuỗi), khóa trong entry là w, g, [s], o,
  t, d. Map 67/68: w=27, 216 ô, mỗi map có 1 vật thể 832 (từ marker
  147); map 106: w=19, 152 ô. Map 20: ba ô 65 → 1, 16, 1 đúng nhịp b42.

## Giả định và ngoại lệ còn lại

1. **Ô 142 (ô đất farm) — lệch với câu chữ trong yêu cầu.** Yêu cầu
  viết "ô 142 giữ nguyên 142 (setMap không đổi)", nhưng code C# gốc
  case 142 là `setTypeMap(j, 80, 4)` — helper này gán đồng thời
  `type[j]=80` VÀ `map[j]=4`. Bản trích này theo C# (được chỉ định là
  sự thật duy nhất): cả 4 ô 142 của map 25 thành tile 4 (nền cỏ); hình
  ô đất trồng do FarmScr vẽ riêng theo `posTree` như ghi chú của yêu
  cầu. Nếu phía Java muốn giữ 142 thì phải sửa ở tầng vẽ, không phải
  ở dữ liệu này.
2. **Map 26 còn 3 ô tile 128.** Case 128 bị chính điều kiện gốc
   `if (index - 1 != 25)` vô hiệu trên map server 25, nên 3 ô 128 ở
   hàng 0 của map 26 giữ nguyên 128 trong `map[]` gốc (vẽ ra ngoài
   atlas 0..125 — hành vi gốc, không phải sót case). Hệ quả đóng gói:
   `d` của map 26 chứa byte 128 ở 3 ô đó; phía Java đọc `d` theo sbyte
   sẽ thấy −128, đọc theo unsigned sẽ thấy 128 — cần chốt ở bước cài
   Java. Đây cũng là lý do assert (a) có ngoại lệ tường minh cho đúng
   3 ô này thay vì kẹp cưỡng bức.
3. **Case 28 phụ thuộc trạng thái kết nối.** C# chỉ đổi `map[j]=4`
   khi `!SessionHelper.GetSession().isConnected()`. Bản trích giả
   định load() chạy khi ĐÃ kết nối (đường chơi thường của game gốc —
   khớp chú thích "Offline:" trong spec §6.2). Nếu sau này cần bản
   offline, 91 ô 28 trên 17 map (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
   12, 13, 18, 24, 25, 26) sẽ thành 4.
4. **Giả định thừa hưởng từ phần o/t:** TYPEMAP lúc nạp = −1 (ảnh
   hưởng nhánh case 145 — với TYPEMAP khác, 145 có thể mang type khác
   nhưng tile vẽ vẫn −1, trừ client 101 → 47); farm của mình = client
   26; `Cattle.numPig > 0` và `Dog.numBer > 0` (chỉ ảnh hưởng case
   155: 1 ô duy nhất trong toàn bộ dữ liệu — có heo thì 112, không
   thì 55).
5. **Case đã cài nhưng không map nào dùng:** 181, 184, 185 không xuất
   hiện trong bất kỳ map nào của danh sách hiện tại. Lưu ý case 184
   theo C# KHÔNG đổi tile (chỉ gán type 72+b30) — nếu một map tương
   lai chứa ô 184, giá trị vẽ 184 sẽ lọt qua assert (a) và phải xử lý
   ở tầng vẽ giống Ngoại lệ 2, không được kẹp ở tầng dữ liệu.
6. Map 61, 62, 66 có 0 ô đổi tile — đúng dữ liệu gốc (không marker
   đổi tile), không phải lỗi mô phỏng.
