# Đơn hàng ủy thác của Lái Buôn — [PORT theo lệnh Tín 2026-11-07]

Lệnh của Tín: bỏ luồng gốc "mở Kho rồi bán đồ"; thay bằng đến NPC
Lái Buôn (850, map 26 Đường ra nông trại) mở panel ủy thác kiểu
đơn hàng Hay Day — khách cần gì, mình giao đúng món đó. Tín chốt
qua nút chọn: **chỉ bán qua đơn hàng, gỡ bán thẳng trong Kho**.

## Giao thức — phong bì ORDER = −125 [PORT]

| Sub | Hướng | Nội dung |
| --- | --- | --- |
| 0 OPEN | C→S | xin trạng thái |
| 1 DELIVER | C→S | {byte slot} giao đơn |
| 2 TRASH | C→S | {byte slot} bỏ đơn |
| 10 STATE | S→C | {byte n=6; mỗi ô: byte kind (0 đơn đang mở / 1 đang chờ); kind 0: UTF khách, byte avatar, byte nMón, nMón × {short pid, short cần, short đang có}, int thưởng Xu, byte thưởng Lượng; kind 1: int giây còn lại} |
| 11 RESULT | S→C | {boolean ok, UTF text} — theo quy ước RESULT của DailyService |

## Luật chơi [PORT — Tín duyệt thiết kế 2026-11-07]

- 6 ô đơn cùng lúc. Mỗi đơn: 1 khách (tên + doll mẫu theo index
  avatar), yêu cầu 1–3 món (nông sản hoặc món đã nấu — mọi pid có
  giá bán gốc > 0 trong FarmProductData), số lượng theo bậc giá:
  ≤10 xu → 15–40 · ≤100 → 8–20 · ≤1000 → 4–10 · >1000 → 1–3.
- Ưu tiên lấy món người chơi đang có trong kho làm yêu cầu đầu
  (70% mỗi lượt chọn khi còn món có sẵn chưa được chọn).
- Thưởng Xu = 130% tổng giá gốc (hạng Sản phẩm Farm cấp 2 cộng
  +10% vào giá gốc trước khi nhân, như giá bán cũ). ~25% số đơn
  kèm thưởng 1–3 Lượng.
- Giao đơn: trừ hàng trong kho (Farm2Service.takeProduct trên cả
  NongSan + NongSanDacBiet), cộng tiền ngay, **+5 điểm nhiệm vụ
  ngày** (DailyService.addQuestScore), ô trống chờ **10 phút**.
- Bỏ đơn: ô trống chờ **5 phút**. Đơn mới sinh lười khi đọc STATE.
- Dữ liệu: bảng `player_orders` (PK user_id + slot), ghi DB ngay.

## Gỡ bán thẳng

- Server: FarmMsgHandler case GET_CARD (63, handler farm) không
  còn gọi sellFarmitm — trả dialog hướng sang Lái Buôn. (Bán cá ở
  NPC Thợ Câu và bán vật nuôi giữ nguyên — ngoài phạm vi lệnh.)
- Client: thẻ nông sản trong Kho bỏ dòng giá + nút Bán, về cùng
  bố cục thẻ hạt giống/vật phẩm (icon + tên + pill số lượng);
  dòng phụ tab Nông sản đổi thành "Giao đơn hàng tại Lái Buôn";
  phụ đề panel Nhà bếp đổi "món chín giao đơn ở Lái Buôn".

## Điểm vào

Menu cục bộ NPC Lái Buôn (client chặn như Điểm Danh/Chợ):
"Điểm Danh · **Đơn hàng ủy thác** · Chợ người chơi · Xem hướng
dẫn · Thoát". Panel theo khung panel sẵn có, ẩn HUD như panel
Điểm danh. Offline: báo cần online (như Điểm danh); bản chụp
kiểm chứng dùng dữ liệu mẫu (debugOpenOrders, stage 179).
