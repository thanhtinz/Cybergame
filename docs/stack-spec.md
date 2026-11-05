# Mua nhiều + Tách chồng — DialogBuyManyItems [PORT]

## Gốc 3.4.4 nói gì
- `DialogBuyManyItems`: fields `imgMinus/imgPlus/tfInput`,
  `TYPE_SEED`, `TYPE_BUY_ITEM`, `maxNumBuy`, `priceXu/priceGold`,
  `nTypeMoney`, `num`; methods `initBuyItem`, `initBuySeed`,
  `initSplitItem`, `calCulateString`, `setTypeMoney`.
  → Một hộp thoại dùng cho BA việc: mua ITEM theo số lượng, mua
  HẠT GIỐNG theo số lượng, và TÁCH một chồng vật phẩm.
- Hằng bản chính: BUY_ITEM_STACK = -43, REMOVE_ITEM_STACK = -63
  (hai số này trong không gian lệnh của bản port đã có chủ khác:
  -43 là đất farm của bản fan -> bản port dùng số riêng, ghi rõ).

## Bản port
### Mua nhiều (đã có sẵn từ các đợt shop)
- Hoá đơn chung (shop hạt giống / vật phẩm farm / vài đồ câu) đã
  có stepper số lượng: `farmBuy(id, qty, type)` gửi byte số lượng
  trong gói 62; server `FarmService.Buy_item_farm` nhân giá theo
  số lượng, cộng dồn vào kho (trần cộng dồn 99/loại) — đúng luồng
  TYPE_SEED/TYPE_BUY_ITEM của gốc.
- Đợt này bổ sung `maxNumBuy` kiểu gốc: trần chọn số lượng của
  hạt/vật phẩm farm = min(99, tiền đang có / đơn giá theo loại
  tiền đang chọn), tối thiểu 1 (trước đây cứng 20).

### Tách chồng (mới)
- Giao thức [PORT]: gói **-119 Cmd.SPLIT_ITEM** {short partId,
  short qty}. Server `AvatarService.splitItem`: tìm chồng đầu
  tiên có quantity > qty, giảm số lượng chồng gốc, thêm chồng
  mới cùng món/hạn vào giỏ; chặn khi giỏ đầy (tách tốn thêm 1 ô)
  hoặc số lượng không hợp lệ (dialog lỗi chuẩn -10).
- Gói -47 (giỏ) thêm ĐUÔI MỞ RỘNG 2: sau byte sức chứa là cờ 1 +
  byte số lượng của từng món theo thứ tự — client cũ dừng ở sức
  chứa nên không ảnh hưởng; client mới đọc được số lượng để hiện
  huy hiệu "xN" ở góc ô + dòng "Số lượng: N" ở thanh dưới.
- Panel Giỏ tab Giỏ đồ: món có quantity > 1 có thêm nút **Tách**
  (giữa Dùng và Xoá) -> mở hộp thoại Tách vật phẩm (mẫu
  DialogBuyManyItems): tên món, "Đang có: N", stepper -/+ chọn
  1..N-1, nút Tách (cam) / Huỷ (đen).

## Kiểm chứng
- Test sống live_split_test.py **ALL PASS**: giỏ 446 x12 -> tách 5
  thành hai chồng 7 và 5 (gói -47 mới có đuôi số lượng đúng);
  tách hết chồng bị chặn; DB sau ngắt kết nối có đủ 2 entry
  quantity 7 và 5. (Lần chạy đầu FAIL do test gửi sai lệnh -41
  thay vì -47 kèm int id — lỗi của test, đã sửa.)
- Kiểm mắt PASS: kiem-split-bag-v1.png (huy hiệu x12 + 3 nút
  Dùng/Tách/Xoá), kiem-split-dialog-v1.png (hộp thoại tách).
  Dòng 2 của thanh dưới panel Giỏ được ĐO PIXEL xác nhận nằm
  trọn trong thanh (không bị cắt như cảm giác từ ảnh thu nhỏ).
- Harness: stage 153/154 (debugOpenSplit).
