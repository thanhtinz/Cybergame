# Tab Cặp Đôi — Đặc tả port (bản chính 3.4.4)

Nguồn: đào sâu APK 3.4.4 (deep/metadata): MyInfoScr có tab thứ 3
"Tặp Đôi" (T.couple); nội dung là widget CommandCouple {avatar,
partner, avaIcon, partnerIcon, dateDay, level, perLevel, idImage,
commandList}; GlobalService.doRequestCmdCouple; CoupleList
{lvlCouple, perLvlCouple, idTitleCouple} + ListScr.readCoupleBoard
(bảng top cặp đôi — CHƯA làm, xem ghi nợ). Chuỗi gốc: "Cặp Đôi",
"Ngày hẹn hò", "Quan hệ".

## Nền dữ liệu (bản fan, giữ nguyên)
- Bảng marry (idNam, idNu, level, perLevel): level 0 = hẹn hò,
  >= 1 = đã cưới. Luồng hẹn hò/lễ cưới/chia tay đã port ở đợt Kết
  hôn (docs/wedding-spec.md).
- Gói −22 (thông tin bản thân) mang sẵn khối người yêu: tên, parts
  doll, slogan, idImage, level, perLevel, tenQuanHe, action
  (idActionWedding + nameAction; IActionWedding.perform gọi
  doRequestCmdRotate(id) -> action là lệnh MENU_ROTATE −83).

## [PORT] — phần bổ sung của đợt này
1. marry.ngay (DATETIME): ngày bắt đầu quan hệ = dateDay của gốc.
   Ghi khi hẹn hò được chấp nhận (acceptHenHo, NOW()), giữ qua lễ
   cưới. MarryService.ensureSchema ALTER khi thiếu cột.
2. Sửa khối người yêu trong AvatarService.requestYourInfo (bản fan
   để chữ rác): tenQuanHe "text 2" -> tên quan hệ theo cấp
   (MarryService.relationName: 0 "Hẹn hò", 1-4 "Cặp đôi mới cưới"
   [tên nhẫn 1153 của fan], 5-9 "Cặp đôi hạnh phúc", 10-14 "Cặp
   đôi viên mãn", 15-19 "Cặp đôi vàng", >=20 "Cặp đôi kim cương");
   action (1, "text 3") -> (37, "Chia tay") đúng lệnh gốc;
   slogan null -> "". Tên đối tác ưu tiên players.name (tên nhân
   vật) thay cho username trần.
3. Đuôi gói −22: sau UTF ngày vào game, thêm UTF ngày quan hệ
   (dd/MM/yyyy, "" nếu chưa có). Client đọc tuỳ chọn như các đuôi
   mở rộng khác.
4. Tăng cấp đôi (bản fan KHÔNG có cơ chế): MarryService
   .addCouplePoints — điểm danh mỗi ngày +10% cho cặp của người
   điểm danh (DailyService.doBaoDanh). Đủ 100% lên 1 cấp (trần
   99); đang hẹn hò (level 0) tiến độ dồn tối đa 99, chỉ lên cấp
   sau khi đã cưới (level >= 1 đồng nghĩa đã cưới). Ghi DB ngay +
   đồng bộ RAM cả hai nếu online.
5. Client: tab 3 "Cặp Đôi" trong panel nhân vật (bản Cards) —
   tab co theo độ rộng chữ (charTabRect, như tab Shop). Nội dung:
   sân khấu 2 doll đối mặt + tim + tên quan hệ + slogan; dòng
   Người yêu/Bạn đời, Quan hệ, Ngày hẹn hò, Cấp đôi + thanh tiến
   độ %; nút hành động theo server gửi (Chia tay -> −83 {37},
   dùng lại weddingLocalSelect). Độc thân: doll + tim xám + dòng
   hướng dẫn tới Chủ Hôn ở Khu lễ đường.

## Ghi nợ
- Bảng xếp hạng cặp đôi: ĐÃ LÀM [PORT] 2026-11-05 — phong bì riêng
  Cmd.COUPLE −122 (CoupleBoardService): C→S sub 0; S→C sub 10
  {byte n ≤ 20; n × {UTF tên nam, UTF tên nữ, byte level, byte per,
  boolean là cặp của tôi}; short hạng của tôi}. Xếp level → per
  giảm dần từ bảng marry. Client: nút "Top cặp đôi" (góc trái dưới
  tab Cặp Đôi, cạnh nút Chia tay) → panel Top cặp đôi, hàng của
  mình tô hồng. Test sống live_coupleboard_test.py ALL PASS.
  Còn lại: danh hiệu cặp đôi theo cấp (CoupleList.idTitleCouple)
  vẫn chưa có dữ liệu — chưa làm.
- Mục hôn nhân ở panel thông tin NGƯỜI KHÁC: ĐÃ LÀM 2026-11-06 —
  gói −22 vốn đã mang đủ khối người yêu cho mọi id đích (server
  requestYourInfo(us) dùng chung); client nay đọc khối đó ở nhánh
  người khác và hiện dải “♥ <đối tác> · <quan hệ> · Cấp đôi <lv>
  (<per>%)” dưới 5 thanh chỉ số của thẻ thông tin (thẻ cao 130 ->
  172 khi người đó có cặp). Test sống live_otherinfo_test.py
  ALL PASS.
