# Tủ đồ — ClosetScr [PORT]

## Gốc 3.4.4 nói gì
- `ClosetScr` trong metadata bản chính: fields `listCloset`,
  `listBoxContain`, `listCell`, `camWearing`, `partFocus`,
  `slotFilter`, `isFilter`, `isRefreshSort`, `imgRefreshSort`,
  `tempFocusWearing`; methods `doOpenCloset`, `drawPreviewCell`,
  `setSelected`, `setPartFocus`, `sortFilter`, `resetFilter`.
  → Màn quản lý đồ mặc chuyên dụng: lưới đồ có **lọc theo nhóm**
  (slotFilter), **sắp xếp** (sortFilter), **xem trước theo ô**
  (drawPreviewCell) và vùng đồ đang mặc (camWearing).
- Không có lệnh mạng riêng cho Closet trong Cmd_Message → bản port
  dùng lại giao thức giỏ/mặc đồ sẵn có (−47/−48), ghi rõ [PORT].

## Thiết kế bản port
- Tab 2 của panel Giỏ đồ đổi tên "Đang mặc" → **"Tủ đồ"**
  (tab 1 Giỏ đồ và tab 3 Rương phụ giữ nguyên).
- Danh sách tủ = đồ đang mặc (trừ mặt/mắt zOrder 30/40 như tab cũ)
  + đồ mặc được trong giỏ (PartData.zOrder >= 0), khử trùng.
- **Lọc**: 8 chip — Tất cả + 7 nhóm của shop (shopCategory):
  Áo, Quần, Tóc, Nón, Trang sức, Phương tiện, Khác.
- **Sắp xếp**: nút ở cuối hàng tab, xoay 3 chế độ — Mặc định
  (đang mặc trước) / Tên A-Z / Hạn dùng (sắp hết hạn trước,
  vĩnh viễn cuối).
- **Xem trước**: cột trái là doll dựng từ bộ đang mặc gộp món
  đang chọn (thay part cùng zOrder) — chỉ hiển thị trong panel,
  không gửi server (mẫu refreshClosetDoll như sân khấu shop).
- Ô đang mặc: viền xanh lá + huy hiệu "Mặc". Lưới 5×2, phân trang
  bằng hai nút mũi tên khi quá 10 món.
- Thanh dưới hiện trạng thái "Đang mặc · ..." / "Trong giỏ · ..."
  và nút theo trạng thái: **Mặc** / **Cất đồ** + Xoá — đi qua hộp
  xác nhận và gói −48 như cũ (server là nơi quyết định cuối).

## Kiểm chứng
- Kiểm mắt PASS: kiem-closet-v3.png (đủ lọc/sắp xếp/doll xem
  trước gộp Cặp sách/huy hiệu Mặc/phân trang 1/2). Hai lỗi đã sửa
  trong lúc kiểm: form mật khẩu rương của stage trước vẽ đè
  (harness dọn thiếu — đã bổ sung dọn trong debugOpenCloset) và
  chữ chip "Phương tiện"/"Trang sức" tràn chip (đã truncTo),
  huy hiệu "Mặc" nới 34 -> 40px.
- Harness: stage 152 trong ScreenshotMain (debugOpenCloset).
- Không cần test sống riêng: đường mặc/cất/xoá dùng nguyên gói
  −47/−48 và hộp xác nhận của tab Đang mặc cũ đã kiểm chứng sống.
