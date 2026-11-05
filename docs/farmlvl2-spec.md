# Farm cấp 2 + Nuôi ong — đặc tả port theo bản chính 3.4.4

Nguồn: audit A6 + metadata IL2CPP: `UpgradeFarmLvl2Scr` (màn nâng cấp,
có listItemUpgrade = nguyên liệu, listMissionInfo), `DataFarmLvl2` với
7 hạng mục **STORAGE, BARN_CHICKEN, BARN_COW, BARN_DOG, COOKING,
PRODUCT, BEE**, `BeeScr` (LvlBee = cấp ong, product, totalProduct,
perH = sản lượng/giờ, IDImgLvl/IDImg), lệnh tải dữ liệu DOWN_FARMLVL2
= 14. Bản chính không công khai số liệu/gói → giao thức + số liệu dưới
đây là **[PORT]**, cấu trúc bám đúng 7 hạng mục + mô hình ong của gốc.

## Phong bì [PORT]: gói −115 (FARM2), byte đầu = sub

| sub | chiều | nội dung |
|---|---|---|
| 0 INFO | C→S {} → S→C {7 × byte up (0/1) theo thứ tự hạng mục, byte beeLevel, int beeBank, int beeCap, int perH} | |
| 1 UPGRADE | C→S {byte hạng mục} → trừ nguyên liệu kho + xu, S→C INFO + RESULT | |
| 2 RESULT | S→C {bool ok, UTF msg} | |
| 3 BEE_BUY | C→S {} | mua tổ ong 50.000 xu (cần hạng BEE đã nâng), beeLevel = 1 |
| 4 BEE_COLLECT | C→S {} | thu toàn bộ mật đang có vào kho nông sản (product 288) |
| 5 BEE_UPGRADE | C→S {} | nâng cấp ong: xu = cấp × 30.000 + mật 288 × (cấp × 5) trong kho |

Dữ liệu: cột mới `farm_data.lvl2_data` JSON
`{"up":[0,0,0,0,0,0,0], "beeLevel":0, "beeBank":0, "beeLast":millis}`.
Sản xuất mật tính lười: khi INFO/COLLECT, cộng
`min(cap − bank, floor((now − beeLast)/3600000) × perH)` vào bank và
đẩy beeLast theo số giờ đã tính. perH = beeLevel (1–5), cap = 10 ×
beeLevel.

Sản phẩm mới **[PORT]**: 288 "Mật Ong", giá bán 120 xu, img 368 (lọ).
farm_info.dat không có ong — đây là bổ sung ngoài nguồn đồng bộ farm:
ghi tay vào farmtrees.json + FarmProductData (đánh dấu [PORT-ONG]).

## Nguyên liệu nâng cấp [PORT] (trừ từ kho nông sản + xu)

| hạng mục | nguyên liệu | xu | hiệu quả |
|---|---|---|---|
| 0 Kho (STORAGE) | Khế 215 ×10, Bắp 200 ×20 | 30.000 | Tổng nông sản trong kho tối đa 500 → 2.000 |
| 1 Chuồng gà | SP 201 ×15, SP 202 ×15 | 20.000 | Gà/Vịt tối đa 5 → 8 con mỗi loài |
| 2 Chuồng bò | SP 203 ×15, SP 204 ×15 | 20.000 | Bò/Heo/Cừu/Trâu tối đa 5 → 8 con mỗi loài |
| 3 Chuồng chó | SP 248 ×10, SP 252 ×10 | 20.000 | Chó tối đa 5 → 8 con |
| 4 Bếp (COOKING) | SP 249 ×10, SP 253 ×10 | 25.000 | Thời gian nấu giảm một nửa |
| 5 Sản phẩm (PRODUCT) | Khế 215 ×20 | 30.000 | Giá bán nông sản +10% |
| 6 Ong (BEE) | SP 266 ×10, SP 267 ×10 | 40.000 | Mở khoá nuôi ong |

Giới hạn vật nuôi 5 con/loài và sức chứa kho 500 là [PORT] áp mới cho
cả cấp 1 (trước đây không giới hạn) — ghi rõ để không nhầm với gốc.

## Điểm chạm vào FarmService (tối thiểu, có kiểm chứng hồi quy farm)

- `sellFarmitm`: giá × 1,1 (làm tròn) khi hạng PRODUCT đã nâng.
- Nấu ăn: thời gian hiệu dụng = cookMinutes/2 (tối thiểu 1) khi hạng
  COOKING đã nâng — cả cookFinish lẫn số phút gửi về client.
- Mua vật nuôi: chặn khi số con cùng loài ≥ giới hạn theo hạng chuồng.
- Cộng nông sản (thu hoạch cây/vật nuôi/khế/nấu): chặn ở sức chứa kho
  (helper `Farm2Service.roomFor`).
- Trạng thái cấp của người chơi đọc qua cache tĩnh
  `Farm2Service.stateOf(userId)` (nạp từ DB, cập nhật khi thay đổi).

## Client

Panel "Farm cấp 2" 920×600, 2 tab: **Nâng cấp** (7 hàng hạng mục: tên,
hiệu quả, nguyên liệu cần/có, nút Nâng cấp/Đã nâng) và **Nuôi ong**
(cấp ong, mật đang có/tối đa, sản lượng/giờ, nút Mua tổ/Thu hoạch/Nâng
cấp ong; hình tổ ong vẽ bằng icon sản phẩm mật + cấp). Điểm vào: nút
"Farm cấp 2" trong panel Kho farm. HUD ẩn khi mở (theo luật mini game).
