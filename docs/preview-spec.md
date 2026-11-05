# Thử đồ — APPLY_PREVIEW_ITEM (−100) [PORT]

## Gốc 3.4.4 nói gì
- `Cmd_Message` của bản chính có hằng `APPLY_PREVIEW_ITEM = -100`, `TITLE = -98`,
  `ANSWERQUESTION = -97`, `DOWN_TITLE`.
- Không có màn hình/handler riêng cho Preview trong metadata (không có
  PreviewScr) → thử đồ là một lệnh nhỏ: áp một món lên nhân vật để xem
  trước, không sở hữu. Định dạng gói của bản chính không khôi phục được
  → định dạng dưới đây là [PORT].

## Thiết kế bản port
- Điểm vào: hoá đơn shop quần áo chính (drawShopBill) — nút **Thử đồ**
  (đen, chuẩn nút phụ) cạnh nút Mua ngay (đã thu hẹp còn 284px). Đồ sai
  giới thì nút xám. Đang thử chính món đang xem → nút xanh dương
  "Đang thử — cởi ra", bấm lại để cởi.
- Gói **−100** (đúng số của bản chính):
  - Client → Server: `{short partId}` (0 = cởi đồ thử).
  - Server → cả khu: `{int userId, short partId}` (0 = cởi).
- Server (`User.applyPreview` + `MapService.previewPart`):
  - Kiểm tra part tồn tại + giới tính như mặc đồ thật (sai giới →
    dialog "Giới tính không phù hợp", không phát gói).
  - Chỉ lưu `previewPartId` trong RAM, **không đổi wearing, không lưu DB**.
  - Mặc/cất đồ thật (case USING_PART) tự cởi đồ thử (`clearPreview`).
  - Đổi map/đăng xuất: figure dựng lại từ wearing thật nên tự sạch.
- Client:
  - `previewSelf` cho mình, `RemoteFigure.previewPart` cho người khác;
    doll dựng = wearing thật + món thử thay part cùng zOrder
    (`rebuildSelfDoll` / `refreshRemoteDoll`) — không đụng
    playerParts/bagItems.
  - Bấm Thử đồ online: gửi gói, server phát lại cho cả khu (gồm mình).
    Offline (Chơi thử ngay): áp cục bộ.
  - Bấm thử xong đóng hoá đơn + panel shop để ra map xem; dòng chat hệ
    thống báo đang thử món gì.

## Kiểm chứng
- Test sống `live_preview_test.py` **ALL PASS**: thử đúng giới cả hai
  phía đều nhận, sai giới bị chặn, cởi thử, mặc đồ thật tự cởi thử.
  (Chú ý: tài khoản test betest01/02 từng bị players.gender ghi về 0 —
  đã sửa lại 1/2 trong DB test.)
- Kiểm mắt PASS: `kiem-thudo-bill-v3.png` (hoá đơn 2173 có nút Thử đồ),
  `kiem-thudo-wear-v4.png` (Sơn mặc áo khoác thể thao 2173 trong map).
- Harness: stage 148 (hoá đơn) + 149 (trong map) trong ScreenshotMain;
  debugCloseHubGames nay dọn thêm snOpen/daily/auction/market/trade/farm2.
