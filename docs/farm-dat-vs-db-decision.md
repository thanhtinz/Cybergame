# Farm: phân xử dat ↔ DB — bảng chốt cho đợt 3 (soạn 2026-11-01)

Bổ sung cho `farm-client-spec.md` mục 2. Đã đối chiếu đủ 25 cây ở cả ba nguồn:
`farm_info.dat` (client hiển thị), bảng `farmitems` trong DB `avatar_2x` (đọc trực tiếp
2026-11-01), và code server gốc (`PartManager.findFarmitemByID` nạp từ DB;
`FarmService` thu hoạch = `(quantity % 100) * sức khỏe / 100`).

## Kết luận gốc rễ

- **Gameplay gốc chạy theo DB**: server nạp `farmitems` để trồng/thu hoạch.
  File dat chỉ là số client *hiển thị* trong bảng thông tin cây.
- Hai nguồn **khớp nhau phần lớn**: sản lượng khớp 20/25 cây, giờ chín khớp 20/25 cây.
  Game gốc vốn đã lệch nhau ở đúng các hàng dưới đây — người chơi xưa nhìn một đằng,
  server chạy một nẻo.
- Bản port dùng một codebase chung nên phải chọn một chuẩn. **Đề xuất: DB thắng cho
  gameplay (đúng cách server gốc vận hành thật), và sửa số hiển thị theo DB** —
  trừ 1 hàng DB trông như lỗi chép (Chuối, xem dưới).

## Chênh lệch giờ chín — chỉ 5 cây

| Cây | dat (hiển thị) | DB (server chạy thật) | Ghi chú |
|---|---|---|---|
| Cà Chua | 4h (240') | 260' | DB +20' |
| Cà Rốt | 6h (360') | 380' | DB +20' |
| Tỏi | 4h (240') | 260' | DB +20' |
| Lúa | 48h | 2280' = 38h | lệch lớn nhất |
| Bắp | 24h | 1140' = 19h | |

20 cây còn lại khớp tuyệt đối (kể cả Vải 72h, Dưa Leo 48h, Đào 48h).

## Chênh lệch sản lượng — chỉ 5 cây, toàn cây động (id 30–43)

| Cây | dat | DB | Nhận định |
|---|---|---|---|
| Nha Đam (31) | 1345 | 45 | dat **hỏng rõ**: quy luật chung ≈15 sản phẩm/giờ → 2h phải ≈45, đúng số DB. Lấy DB. |
| Chuối (36) | 180 | 97 | DB **nghi lỗi chép**: 97 là y hệt sản lượng Dâu Tây (id 35) ở cả hai nguồn; cây 11h theo quy luật ≈180 = số dat. Đây là hàng duy nhất đề xuất lấy **dat**. |
| Đào (37) | 1000 | 720 | Mọi cây 48h khác (Lúa, Dưa Leo) đều 720 ở cả hai nguồn → dat Đào phá quy luật. Lấy DB. |
| Vải (38) | 1500 | 1200 | 72h: quy luật 15/giờ cho ≈1080; DB gần hơn. Lấy DB. |
| Khoai Tây (42) | 120 | 100 | Cây 6h khác (Cà Rốt, Hoa Tulip) đều 108 ở cả hai nguồn; hai số đều lệch quy luật, DB là số server chạy thật. Lấy DB. |

## Câu chốt duy nhất cần Tín

> **"Farm theo DB, riêng Chuối lấy dat"** — hoặc Tín chỉ đích danh hàng nào muốn khác.

Chốt xong, khi code đợt 3 chỉ còn: sửa 9 số hiển thị phía client cho khớp chuẩn đã
chọn (5 giờ + 4 sản lượng, Chuối giữ nguyên phía DB→sửa DB thành 180 nếu theo đề xuất),
và giá mở ô đất (không có ở bất cứ đâu trong Pack5, chỉ server live mới có — không
chặn các phần farm còn lại).

---

## QUYẾT ĐỊNH CUỐI + ĐÃ THỰC THI (2026-11-03)

Tín chốt khác đề xuất trên: **"Viết lại data cho đồng bộ vì farm là lõi của game
này mà chênh lệch vậy ko đc. Cứ lấy theo client đi"** — lấy **dat (client) làm
chuẩn cho toàn bộ**, viết lại data phía server cho khớp, không chừa ngoại lệ.

Đã thực thi bằng `tools/sync_farm_data.py` (nguồn chuẩn duy nhất =
`docs/farm-info-decoded.json`):

- DB `farmitems` (cả DB đang chạy lẫn dump `server/database/avatar_2x.sql`):
  `so_phut_chin` = giờ dat × 60, `san_luong_khi_chin` = numProduct dat,
  `gia_san_pham` = priceSeedXu, **cột mới `gia_hat_luong`** = priceSeedLuong
  (chỉ Nha Đam có giá lượng = 1, còn lại 0 = không bán bằng lượng).
- `assets/data/farmtrees.json`: bảng chuẩn 25 cây + 9 vật nuôi + 96 sản phẩm +
  14 vật phẩm + 30 món nấu cho client Java dùng chung nguồn.
- `avatar/Farm/FarmProductData.java` (sinh tự động): productID của 14 cây động
  (30→200 … 43→286) + giá bán xu của 96 nông sản theo dat.
- Server dùng các số này trong mua hạt (giá theo đúng loại tiền, 0 = không bán),
  thu hoạch (sản lượng dat × sức khoẻ/100, ra đúng productID), bán nông sản
  (giá dat). Kiểm chứng: `live_farm_test.py` PASS toàn bộ (2026-11-03).

Còn lại của đợt 3 không đổi: giá mở ô đất chỉ server live mới có.
