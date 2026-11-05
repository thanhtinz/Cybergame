# Rương phụ (BoxScr) — nghiên cứu gốc 3.4.4 (2026-11-05)

## Bằng chứng từ metadata bản chính
- `BoxScr`: fields `listItemBox`, `listItemContainer`, `slot`, `maxSlot`,
  `selectInven`, `selectBox`, `imgCellLock`, `listSelectedBox`,
  `listSelectedInventory`, `isSetContainer`; methods `setInfo`, `init`.
  → Màn hình HAI danh sách: đồ trong **Box** (rương phụ) ↔ đồ trong
  **Container** (giỏ chính), chọn được nhiều món mỗi phía, box có sức
  chứa riêng (slot/maxSlot) và Ô KHOÁ (imgCellLock).
- `SubContain {idIndex, list, type}` + `DialogSubContainer`
  {`listSubContain`, `idGive`, `idShow`, `subIDGive`, `isContainer`,
  `cmdGhep`, `setButtonContain`, `checkSubContainer`} → container có
  **ngăn phụ (sub-container)**.
- `Cmd_Message` bản chính có: `CHANGE_PASS_CONTAINER` (−54 theo audit),
  `SUB_CONTAINER`, `UPDATE_CONTAINER`, `CONTAINER`, `BUY_ITEM_STACK`,
  `REMOVE_ITEM_STACK`. `IActionGetBox` / `IActionGetContainer` là hành
  động mở hai màn này (điểm vào cụ thể trong bản chính chưa xác định).
- String literals: "Rương đồ", "Mở rương", "Nâng cấp rương",
  "Bỏ vào rương?", "Bạn có muốn nâng cấp rương không?" — khớp hệ rương.
  Không có literal riêng cho mật khẩu rương (các chuỗi mật khẩu đào
  được đều là mật khẩu tài khoản).

## Đối chiếu bản port hiện tại
- Giỏ chính: −47/−48 ✓. Rương NHÀ: −87/−89 + nâng cấp rương ✓ (hai
  phía giỏ ↔ rương, chọn + chuyển) — bao phủ phần lớn chức năng BoxScr
  nhưng gắn với nhà, không có mật khẩu và không mở được mọi nơi.
- Số −54 của bản fan đã dùng cho `SMS` (−54) và `MOVE_PARK` (54) →
  mật khẩu rương của bản port phải dùng số gói trống riêng [PORT],
  như các đợt trước (bản chính đã đánh số lại gần hết).

## Hướng triển khai [PORT] (đợt sau)
- "Rương phụ" cá nhân: kho thứ hai lưu DB (cột riêng trong players),
  mở từ panel Giỏ đồ (tab "Rương phụ"), chuyển đồ qua lại với giỏ
  (chọn món → nút chuyển, như panel rương nhà), sức chứa khởi điểm =
  rương nhà cấp 1, nâng cấp bằng xu như rương nhà.
- Mật khẩu rương: đặt mật khẩu lần đầu / nhập để mở khi đã đặt; lưu
  băm trong DB; gói riêng [PORT] số trống. Ô khoá cuối rương theo
  cấp nâng cấp (imgCellLock) — vẽ ô xám có khoá như gốc.

## Triển khai [PORT] — ĐÃ XONG (2026-11-05)
- Phong bì **−118 Cmd.BOX** (số −54 của bản chính xung đột SMS của fan).
  Sub: OPEN 0 · UNLOCK 1 · SETPASS 2 · CHANGEPASS 3 · TRANS 4 ·
  UPGRADE 5 · STATE 10. STATE mang cả list rương phụ + list giỏ để
  client đồng bộ hai phía sau mỗi lần chuyển.
- Server `avatar/service/BoxService.java`: bảng `player_box`
  (user_id PK, items JSON như chests_home, slot, pass_hash MD5).
  Mở khoá chỉ trong phiên (RAM); Session.close -> BoxService
  .onUserGone khoá lại. Chuyển đồ theo luật rương nhà (không nhận
  đồ có hạn/số lượng, không trùng món hai phía). Nâng cấp dùng
  đúng BẢNG GIÁ rương nhà: ô 10 + 5/cấp, tối đa 55 ô.
- Client: mở từ TAB "Rương phụ" trong panel Giỏ đồ -> panel song
  song dùng chung khung rương nhà (chestMode=1). Phía rương có nút
  Nâng cấp + Mật khẩu; form mật khẩu 3 chế độ (mở khoá/đặt/đổi,
  để trống mật khẩu mới = gỡ) theo khuôn form đổi mật khẩu tài
  khoản; rương khoá thì lưới phủ icon khoá + form mở khoá tự bật.
  Tên món ở thanh dưới được cắt (truncTo) theo nút trái nhất —
  bản đầu bị nút Mật khẩu đè chữ, đã sửa + thu nút còn 100px.
- Kiểm chứng: test sống `live_box_test.py` **ALL PASS** (9 bước:
  mở/đặt pass/chuyển 2 chiều/nâng cấp trừ đúng tiền/khoá theo
  phiên/mở khoá sai-đúng/gỡ pass/dọn DB). Kiểm mắt PASS:
  kiem-box-v3.png (panel), kiem-box-lock-v1.png (form mở khoá).
  Harness stage 150/151 trong ScreenshotMain.
