# Vòng quay may mắn (DialLucky) — đặc tả port

Nguồn: DialLuckyScr.cs (Pack5, 441 dòng) + GlobalService/GlobalMessageHandler/MapScr (C#) +
server fan: avatar/lucky/{DialLucky, DialLuckyManager, GiftBox}, NpcHandler.handleDiaLucky,
Session.doDialLucky, MapService.dialLucky. Đối chiếu 2026-11-04.

## Luồng gốc
1. NPC 185 "Quay số" (map 10, sạp quay số) → menu −59: "Quay số" {5 lượng · 25.000 xu ·
   Q.S miễn phí · Thoát} + "Xem hướng dẫn" (CUSTOM_TAB −58, server đã có sẵn).
2. handleDiaLucky(type): kiểm tra ≥3 ô rương trống; XU cần ≥25.000 xu; LUONG cần ≥5 lượng;
   MIEN_PHI cần item 593 "Quay số miễn phí" trong rương. Đạt → user.setDialLucky(dl) +
   dl.show() → OPEN_SHOP −49 {byte idShop=100, UTF "Quay số", short n, n×short idPart}
   = danh sách quà tiềm năng (bảng DB `dial_lucky`, 129 dòng, lọc theo giới tính người chơi).
3. Client gốc hiện PopupShop; chạm 1 món → DialLuckyScr.switchToMe(idPart) (món đã chọn).
4. Màn quay: giữ phím chỉnh kim (degreeKim 90→270; số chọn = degreeKim−90 = "lực" 0..180)
   rồi bấm Quay → C→S **−64 {short partId, short lực}**.
5. Server Session.doDialLucky: trừ tiền theo loại đã lưu (vé: −1 item 593; xu: −25.000;
   lượng: −5) rồi DialLucky.doDial lăn **3 quà**:
   - trọng số loại: ITEM 18 · XU 45 · XP 36 · LUONG 1.
   - ITEM: ngẫu nhiên trong pool theo ratio; **trúng đúng món đã chọn → vĩnh viễn**,
     khác → hạn 3/7/15/30 ngày ngẫu nhiên; cho vào rương.
   - XU: 10–3.000 (nextInt(1,300)×10). XP: 10–100 (nextInt(1,10)×10).
   - LUONG: fan để trống (không thưởng) → [PORT sửa: 1–3 lượng].
6. Server phát cả khu **−64 {int idUser, short lực, byte n=3, n×quà}**; quà: byte type;
   type 1 {short idPart, byte expireDay (−1 = vĩnh viễn)} · 2 {int xu} · 3 {int xp} · 4 {int luong}.
   Người quay: client chạy vòng quay rồi lật quà lần lượt; người khác trong khu: chữ bay
   (fly text) trên đầu avatar của người quay.

## Hình ảnh màn quay (DialLuckyScr.paint)
- Tâm vòng ở (Canvas.w, Canvas.hh) = mép phải-giữa màn hình gốc; bán kính 90×hd = 180.
- 12 ô cầu (part=30°, num=12); ô ở góc a (chỉ vẽ 82°..278° = nửa vòng phía trái):
  pos = tâm + (180·cos a, 180·sin a) [hệ GL y-lên tương đương].
  Mỗi ô vẽ 3 lớp: cb (cầu xanh) → q (dấu ?) → c (vòng kính trắng).
- sq (imgDo, 128×248 = nửa đĩa mặt trời xanh lá) vẽ tâm tại (tâm.x − 64, tâm.y) — nền sau vòng.
- Kim: dot 22×22 ở bán kính 90, góc degreeKim.
- Khi quay: offset góc giảm dần (g từ 100+lực, giảm tốc; dừng khi g<10 và góc khớp ô 30°);
  pháo hoa (hạt sparkle từ st, frame 22×22) bắn ở các ô ngẫu nhiên lúc quay nhanh.
- Dừng: 3 quà hiện lần lượt (mỗi 0,5s) tại các ô góc 150°/180°/210° kèm pháo hoa tại ô;
  quà item vẽ icon part + hạn dùng; quà tiền/xp vẽ nhãn + số.
- Nút gốc: Quay (giữa) → Xin chờ → Đóng (về thế giới + chữ bay quà trên đầu mình).

## Port (client Java) — quyết định
- Server fan ĐÃ ĐỦ toàn bộ (handler −64, trừ tiền, lăn quà, phát cả khu, bảng pool 129 dòng)
  → chỉ sửa 3 lỗi nhỏ: check xu khi quay 15.000→25.000 cho khớp giá menu; NPE thứ tự
  kiểm tra vé miễn phí trong handleDiaLucky; nhánh LUONG không thưởng → thưởng 1–3 lượng.
- Client: −49 với idShop=100 → panel chọn quà riêng (lưới phân trang, kính) thay PopupShop;
  chạm món → màn quay overlay toàn màn trên nền thế giới (modal như casino).
- Bố cục ngang [PORT]: tâm vòng đặt (880, 370) để trọn nửa vòng + đĩa lọt trong màn hình
  ngang 1280 (gốc đặt tâm ở mép phải vì màn hình dọc hẹp).
- Điều khiển [PORT cảm ứng]: nút "GIỮ ĐỂ QUAY" — giữ thì kim/lực tăng 90→270, thả ra là
  quay ngay với lực đó (gộp hai thao tác giữ-phím-5 + bấm-Quay của gốc thành một chạm giữ-thả).
- "Quay tiếp" [PORT tiện ích]: quay lại cùng món đã chọn (server vẫn trừ tiền qua loại
  quay đã lưu trên user — Session.doDialLucky không xoá dialLucky sau mỗi lượt).
- Người khác quay: chữ bay trên đầu doll của họ như gốc (item vẽ icon part).
- Asset: hd/diallucky/{c,cb,dot,q,sq,st}.bytes đều là PNG thô → assets/diallucky/*.png.
