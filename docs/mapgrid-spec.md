# Lưới ô dữ liệu map (map grid) — trích từ client gốc

Mỗi map còn một **lưới ô** riêng (`Resources/map/<id>.bytes`, vài map là
`<id>.txt` chứa cùng dữ liệu dạng byte thô): mỗi ô 1 byte, chiều rộng
`wMap = cỡ file / Hmap` (Hmap theo `maps.json`). Client gốc nạp lưới này
trong `LoadMap.load` → `setMap` và quét từng ô để:

- gán thuộc tính đi/đứng (`type[]`: nước, ghế, vùng park…),
- **đặt vật thể lên map** qua `addObjTree` khi gặp ô đánh dấu,
- tìm điểm đặc biệt: **ô 139 = trạm xe buýt** (`Bus.posBusStop`).

Bản trích nằm ở `assets/data/mapgrids.json`:
`{ "<id>": {"w": wMap, "g": "<base64 lưới thô>", "s": [x0,y0,x1,y1,...]} }`
— `s` là danh sách dẹt các ô 139 theo thứ tự quét (chỉ có khi map có trạm).

## Trạm xe buýt (ô 139)

- Gốc gán `Bus.posBusStop = (tx*24 + 12, ty*24 − 12)` cho **mọi** ô 139,
  nên ô quét sau cùng thắng (dải trạm nằm ngang một hàng → ô phải nhất).
- 9 map có trạm: **10, 12 (Bến xe công viên), 13, 14, 18, 22, 23, 24, 26**.
- Khi vào map có trạm (và `TYPEMAP == -1`, map ≠ 21): `Bus.setBus(1)` —
  camera nhảy tới điểm trạm, xe buýt (ảnh `home/839`) chạy từ
  `posBusStop.x + 300` sang trái, giảm tốc, dừng; người chơi xuống xe tại
  điểm trạm rồi xe chạy tiếp rời khung hình. Đường bình thường không có xe
  (luật đã chốt: xe buýt chỉ là sự kiện đến nơi).
- Bản port: thân xe vẽ lại (`assets/sprites/bus.png` — hành khách chibi
  theo phong cách game + biển “Thị Trấn Mặt Trời”), biển trạm là asset
  riêng (`assets/sprites/busstop.png`), sự kiện dựng trong `WorldScreen`
  (`beginBusArrival`/`updateBus`, pha 1 vào → 2 dừng → 3 rời).

## Mã vật thể tiêu biểu trong lưới (cho đợt lớp vật thể sau)

| Ô | Vật thể (ảnh `hd/object/<id>.png`) |
|---|---|
| 127 | 830 — Hội Đánh Cờ (trừ map client 10) |
| 128 | 836/829 — nhà / Shop |
| 129, 160 | 829 — Shop (map 18: 836; map 24: không đặt) |
| 140–147, 161 | 832 — Mỹ Viện |
| 148, 162 | 833 — Gift |
| 149, 150 | 842 — quầy ăn |
| 151 | 843 |
| 152 | 835 — quầy TeaM |
| 155–159 | 848 — Bảng xếp hạng |
| 179 | 837 — chung cư |
| 180–185 | 975 |
| 186–190 | 1029 |
| 191–198 | 1036 |
| 199 | 1031 |
| 200–202 | 4 |
| 203, 204 | 846 |

Phần lớn nhà chính đã có sẵn trong tranh dải imagemap (ví dụ map 24 thấy
Mỹ Viện + Gift trong bản port); lớp vật thể đầy đủ (đặt + sắp xếp độ sâu
theo hàng) là việc của đợt sau — lưới thô đã trích sẵn trong
`mapgrids.json` để dùng tiếp. Ba map 67/68/106 có cỡ file không chia hết
cho Hmap trong `maps.json` nên chưa trích được lưới (cần đối chiếu lại
Hmap của chúng khi làm lớp vật thể).
