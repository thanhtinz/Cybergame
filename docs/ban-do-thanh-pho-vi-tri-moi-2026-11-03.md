# Bản đồ thành phố — đặc tả ảnh mới + điểm đến mới (rà soát 2026-11-03, theo lệnh của Tín 00:29 UTC)

## Hiện trạng đã kiểm chứng trong code (WorldScreen.java)
- Panel "Bản đồ thị trấn" dùng ảnh `assets/minimap.png` GỐC 1600×900 + lưới `assets/data/citymap.bin` 50×26; ô giá trị 69 = điểm vùng, quét trên→dưới, trái→phải.
- Lưới gốc chỉ có **8 ô 69**. Code tự ghim thêm điểm thứ 9 ở ô (2,24) — góc trái dưới — cho Mê cung hoa hồng (map 29) vì bản chính không có cổng LinkMap vào map này.
- `REGION_NAMES` / `REGION_DESC` có 9 mục; `REGION_DEST = {1, 14, 21, 10, 24, 12, 18, 25, 29}`.
- **Map 27 (khu đá ven biển) và map 30 (quán cà phê) KHÔNG có điểm đến nào trên bản đồ thành phố** — người chơi chỉ tới được bằng cổng LinkMap đi bộ, không chọn được từ bản đồ.
- Toạ độ ghim được tính từ ô lưới qua không gian ảnh 1600×900 (`regionPoint`: (col+0.5)/50×1600, (row+0.5)/26×900) — ảnh mới phải giữ đúng khung 1600×900 (hoặc sửa cả hàm quy đổi).

## Việc cần làm khi thay ảnh mới
1. Vẽ ảnh minimap mới 1600×900 có đủ 11 vị trí: 8 vùng gốc + Mê cung hoa hồng (29) vẽ hẳn vào ảnh thay vì ghim tạm ở góc + Khu đá ven biển (27) + Quán cà phê (30).
2. Mỗi vị trí mới cần: 1 ô ghim (thêm ô 69 vào bản vá lưới citymap, hoặc thêm ghim cứng như cách map 29 — chọn MỘT cách nhất quán), 1 dòng REGION_NAMES, 1 dòng REGION_DESC, 1 số trong REGION_DEST (27 và 30).
3. Tên hiển thị trong maps.json của 27/30 hiện vẫn là "Khu 27" / "Khu 30" — đặt tên vùng khớp tên này khi chốt nhãn trên ảnh (đá ven biển / quán cà phê theo cách gọi trong báo cáo 2026-11-03).

## Chưa rõ — chờ Tín chỉ (đã hỏi ở chat AvatarDK 00:31 UTC, chưa trả lời)
- "Map mới không cân đối" là map nào (27/29/30) và lệch theo chiều nào.
- "Một vài item vẫn sai ảnh" là item ở khu nào (farm / shop quần áo / đồ câu).
Hai ý này KHÔNG đoán trước; đặc tả trên chỉ xử lý phần bản đồ đã xác minh được.
