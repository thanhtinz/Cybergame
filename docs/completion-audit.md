# Kiểm kê HOÀN THIỆN TOÀN GAME — AvatarDK port (2026-11-03)

Lệnh của Tín (2026-11-03): "Phải dựng lại nguyên game đầy đủ — cái nào cần mở lên
thì mới dựng panel, còn cái nào là map thì phải import lại đầy đủ."
=> Mọi panel gốc có là phải dựng; mọi map gốc có là phải import đủ (ảnh + lưới +
cổng chuyển map), không làm nửa vời.

Nguồn sự thật: `docs/maps-spec.md` (kiểm kê map/NPC từ Pack5), code gốc
[CL] client C# Pack5, [SV] server Java, trạng thái port tại main d953599c0fd0.

## A. MAP — 48 map trong maps.json

| Nhóm | Map (client id) | Trạng thái port | Việc phải làm |
|---|---|---|---|
| Có imagemap (31) | 1–12, 14–19, 22, 24, 25, 26, 58, 59, 60, 65, 101, 102, 104, 105, 108 | Vẽ được bằng dải ảnh | Còn thiếu CỔNG theo type ô (xem A3) |
| Chỉ có lưới tile (12) | 13, 20, 21, 23, 61, 62, 63, 64, 66, 67, 68, 106 | Có lưới (mapgrids) nhưng CHƯA vẽ được — gốc dựng bằng tileset | Dựng engine tile (A1) |
| Thiếu dữ liệu client (5) | 28, 31, 34, 56, 57 | Không lưới, không ảnh — vào map chỉ có nền + NPC | Săn dữ liệu (A2). **28 là map Cửa Hàng (NPC 761 + 861) — ưu tiên cao nhất** |
| Farm (25) | — | Xong (cơ chế riêng FarmScr) | — |
| Offline (58–60, 101, 102, 104, 105) | — | Có ảnh; luồng vào offline chưa nối đủ | Nối doJoinMapOffline theo type ô |

### A1. Engine tile cho 12 map
- Gốc: `LoadMap.loadMapImage` dựng FrameImage từ `hd/data/h<status>` +
  `hd/data/data` ([CL] LoadMap.cs:1851-1898); map 20 dùng riêng `hd/wedding`.
  Cả 3 file đều CÓ trong Pack5: `Resources/hd/data/{data,h0,h1}.bytes`.
- Đường thứ hai: server cấp tileset lúc chạy — client gửi `idTileImg`, server
  trả bytes của `tilemap/<id>.png` qua cmd REQUEST_TILE_MAP
  ([SV] Session.java:1907-1921); server có sẵn tilemap 0–4.png
  (`server/res/hd/tilemap/` + `server/res/map/imgTileMap/`).
- Phải giải mã: data.bytes (bảng frame), h0/h1 (atlas), cách setMapPaint gán
  frame cho từng ô + type ô.

### A2. Năm map thiếu dữ liệu (28, 31, 34, 56, 57)
- **ĐÃ GIẢI MÃ (2026-11-03): `server/res/map/27.dat` chính là lưới của server
  map 27 = client 28.** 272 byte = 34 cột × 8 hàng (Hmap mặc định 8), mọi giá
  trị 0–34 (tile hợp lệ, không marker): hàng 0/6/7 là viền (11/3/9) với đoạn
  34/18/10 ở cột 14–17 (cửa), hàng 1 rải rác nội thất (20/21 = ghế), hàng 2–5
  sàn 4/5/6. NPC Cửa Hàng (334,72) → ô (13,3) và Phù thủy (438,72) → ô (18,3)
  đều rơi đúng ô sàn 4. Việc còn lại: chạy pipeline mô phỏng o/t/d cho lưới
  này, gắn vào mapgrids.json khóa "28", chụp kiểm chứng.
- 4 map còn lại (31, 34, 56, 57) — ĐÃ VÉT HẾT NGUỒN TĨNH (2026-11-03):
  Pack5 client (không có), res server Pack5 (chỉ có 27.dat), APK 2.60 (không
  phải Unity — chỉ có art rời, không lưới), bundle Unity của client 3.4.4
  (`data.unity3d`): bộ TextAsset `DataMap<N>` (khóa theo id CLIENT, có header
  2 byte rồi tới lưới thô) gồm 36 map {1–12, 14–19, 21–30, 58, 59, 60, 65,
  101, 102, 104, 105} — vẫn KHÔNG có 31/34/56/57 (cũng không có 13/20, hai
  map này Pack5 có sẵn). Lưu ý: lưới 3.4.4 đã TIẾN HÓA so với Pack5 (map 1
  rộng 28 cột thay vì 26; DataMap28 35×8 khác hẳn lưới 27.dat 34×8) → bản
  port bám Pack5 nên chỉ dùng 27.dat cho map 28, KHÔNG lấy lưới 3.4.4.
  => 31/34/56/57 chỉ còn đường bắt dữ liệu từ server live (đang kẹt mật khẩu
  tài khoản 230723 như nợ hình vật nuôi farm). Cả 4 NPC ở đó (tiên cá, pharaoh,
  zombie, phù thủy) đều không có chức năng trong server gốc.
- Luồng server cấp lúc chạy: `onSelectedMiniMap(map, idMap, idTileImg, ...)`
  ([CL] GlobalMessageHandler.cs:951-1015) + `onTileImg` → `setMapAny()`.
- Nguồn dự phòng: assets của APK live 2.60 (`~/workspace/apk-avatarhd/260/assets/`)
  và dữ liệu bắt từ server live (gói tile khi vào các map này) — đường cuối khi
  các nguồn tĩnh cạn (theo luật: vét mọi lớp dữ liệu gốc trước khi kết luận thiếu).

### A3. Cổng chuyển map theo TYPE Ô (áp cho MỌI map)
- Gốc KHÔNG có bảng portal: đích đến nằm trong type của từng ô trong lưới
  `Resources/map/<index>`; `LoadMap.doJoin(x, y)` tra type → hành động
  ([CL] LoadMap.cs:1298-1580): type 0–19 → doJoinPark(type), 24 → farm mình,
  25 → đường ra nông trại, 52 → cửa hàng farm, 57–70 → shop/nhà, 72–77 →
  casino, 100/101/103/104 → map offline, 71 → bản đồ thành phố...
- Port hiện tại: CHƯA trích type ô (mapgrids chỉ có type đứng được/blocked +
  vật thể). Cơ chế mép đường tự chế (farm ↔ 26 ↔ khu 1) chỉ là xấp xỉ cục bộ.
- Việc: mở rộng giải mã lưới → xuất type từng ô vào mapgrids.json; cài doJoin
  theo đúng bảng type của gốc; giữ mép đường làm dự phòng nơi lưới không có ô cổng.

## B. PANEL / CHỨC NĂNG THEO NPC (bảng NPC đầy đủ ở maps-spec §3)

| NPC | Map client | Chức năng gốc | Trạng thái port |
|---|---|---|---|
| 761 Cửa Hàng | 28 | Shop quần áo (id 5) + Hướng dẫn | XONG (shop + hoá đơn + panel −58) |
| 850 Lái buôn | 26 | Điểm Danh (stub gốc) + Hướng dẫn | XONG 2026-11-03 |
| 185 Quay số | 10 | DialLucky: 5 lượng / 25.000 xu / vé | CHƯA — tạo mới theo DialLuckyScr + server handleDiaLucky (đợt vòng quay) |
| 186 Đấu giá | 10 | Gốc đang TẮT: dialog "đấu giá đang bảo trì..." | CHƯA — chỉ cần dialog gốc |
| 864 Thần Tài Xỉu | 12 | Mini-game Tài Xỉu (cược Xu/Lượng, lịch sử, top) | CHƯA — port theo gốc (đợt mini-game) |
| 860 Tiên tri | 12 | Dự đoán người yêu (chạy thật DuDoanNY); xổ số/tài xỉu "Comingsion" | CHƯA |
| 858 Bạch vô thường | 1 | Menu sự kiện: đổi quà (shop sự kiện), góp kẹo, đua top, thành tích, BXH | CHƯA |
| 859 Hắc vô thường | 1 | Menu quà thẻ VIP (3 hạng) + hướng dẫn | CHƯA |
| 165 Saitama | 3 | Quản lý rương: mặt/mắt, xem ô trống, xoá trùng | CHƯA |
| 430 Thợ câu | 14 | Shop đồ câu + Bán cá + hướng dẫn | CHƯA — gắn với hệ câu cá (map 14–17) |
| 862 Pay To Win | 18 | Shop đổi quà + nâng cấp PTW + hướng dẫn | CHƯA |
| 863 Chay To Win | 18 | Shop đổi quà (xu) + nâng cấp + đổi đá ngũ sắc | CHƯA |
| 857 Chủ đầu tư | 22 | "Mua biệt thự" → Session.BuyHouse() | CHƯA — thuộc đợt Nhà |
| 95 Thợ kim hoàn | 24 | Form nâng cấp vật phẩm (xu/lượng) + hướng dẫn | CHƯA (mảnh lẻ đã ghi) |
| 861 Phù thủy bóng tối | 28 | Shop Đổi Quà sự kiện 5 món + hướng dẫn | CHƯA |
| 525 Chủ Hôn | 11 | Không qua NPC: cưới chạy bằng cmd 93 WEDDING_BIGIN ở lễ đường (map 11 + phòng 20) | CHƯA — đợt kết hôn |
| 741, 645, 646, 335, 315, 50, 760, 535, 425, 590, 540 | — | Gốc không có case (chỉ đứng + chat) | Đã đặt đúng map; không có panel để dựng |
| Boss (server tự sinh) | 1,2,3,5,7,8,11 (server) | Tương tác nhánh riêng NpcHandler:122-231 | CHƯA — đánh giá sau |

## C. HỆ LỚN CÒN LẠI (theo thứ tự đợt đã chốt)

1. **Đợt 4 — Nhà/nội thất**: HouseScr (vào nhà client 22), mua biệt thự (857),
   đặt/bày nội thất (dữ liệu chests_home đã có), rương mở trong nhà, nội thất
   tile (map 21, 23, 61–68, 106 — phụ thuộc A1). Asset: `Resources/hd/home/` có sẵn.
2. **Mini-game**: Tài Xỉu (864), bài (assets `hd/card/`), đua thú (RaceScr,
   map 108 đã có ảnh), casino handler (type ô 72–77), câu cá (map 14–17 + 430).
3. **Vòng quay may mắn**: tạo mới theo DialLuckyScr (assets `hd/diallucky/` có sẵn)
   + logic server gốc handleDiaLucky.
4. **Đợt 7 — Clan (tạo mới) + Kết hôn** (cmd 93, lễ đường 11/20, part chủ hôn 2480).
5. Mảnh lẻ đã liệt kê từ trước: shop Tặng quà (idShop 26, gói 58), điểm danh/
   chuyên cần thật (hiện stub như gốc), mật khẩu rương (−88), nâng cấp rương
   (−90), cất/rút Xu rương, cuộn rương >18 món, PaintPopup gốc, nâng cấp ô
   đất/kho farm, nấu nhanh bằng lượng (cmd 93 farm).

## Thứ tự thi công (hệ chính trước, theo phụ thuộc)

A1 (engine tile) → A2 (5 map thiếu, ưu tiên 28) → A3 (cổng type ô toàn game)
→ C1 Nhà → B (panel NPC theo gốc, gộp vào các đợt liên quan) → C2/C3/C4.
