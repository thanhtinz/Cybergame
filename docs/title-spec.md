# Danh hiệu — TITLE [PORT]

## Gốc 3.4.4 nói gì
- `Avatar` (nhân vật trong map) có `idTitle`, `timeTitle`,
  `_iconTitle` — danh hiệu đeo trên người, hiện kèm tên trong map.
- `T` có `ownedTitleTab` / `emptyOwnedTitleTab` / `titleInfo` —
  panel nhân vật có tab "Danh hiệu đang sở hữu".
- `MapScr` có `openTitlePopup`, `listTitle`, `indexSelectTitle` —
  popup chọn danh hiệu. `AvatarData` có `listImgTitle`/`verTitle`
  (ảnh danh hiệu tải theo phiên bản), sprite `backTabTitle`,
  `imgTitle0..2`. Chuỗi "Sở hữu {0} ngày" — danh hiệu có hạn.
- Hằng TITLE = -98 (+ DOWN_TITLE tải dữ liệu). Ở bản fan, -98 đã
  là REQUEST_IMAGE_PART -> bản port dùng số riêng **-120**.

## Thiết kế bản port
- Gói **-120**: sub 0 LIST -> STATE {equipped, 12 định nghĩa
  (tên/màu/điều kiện), danh sách sở hữu, danh sách vừa cấp mới};
  sub 1 SET {short id} (0 = gỡ); sub 3 WEAR {int userId, short id}
  phát cả khu; sub 4 ZONE_STATE; sub 5 ZONE_REQ.
- Server `avatar/service/TitleService.java`: bảng `player_titles`
  (sở hữu vĩnh viễn) + cột `players.title_id` (đang đeo). Mở panel
  là quét điều kiện và CẤP TỰ ĐỘNG từ các hệ đã có: cấp 5/15/30,
  giữ 1.000.000 xu, thắng 25 ván mini game, có cần câu 447/448,
  chủ clan, đã kết hôn, farm cấp 2, điểm danh 7 điểm, sưu tầm 30
  món. 12 danh hiệu, mỗi cái một màu chữ riêng.
- Zone.enter phát WEAR của người mới vào cho cả khu
  (TitleService.announceEnter) — **bỏ qua Boss của bản fan**:
  chèn thao tác vào enter làm boss (session nội bộ mô phỏng) bị
  ngắt giữa chừng và kéo sập luồng khởi động server.
- Client: pill "Danh hiệu: ..." ở đáy panel Nhân vật mở panel
  Danh hiệu (lưới 2 cột, thẻ theo màu, nhãn Đang đeo/Đeo/Chưa có);
  danh hiệu vẽ thành dòng chữ màu riêng NẰM TRÊN tên nhân vật
  trong map (cho mình và mọi người qua remoteTitles).

## Kiểm chứng
- Test sống live_title_test.py **ALL PASS** 10 mục: LIST trả 12
  định nghĩa + cấp mới đúng điều kiện (xu 2 triệu + 30 ván thắng
  -> Đại Gia + Cao Thủ), SET phát WEAR cho người cùng khu,
  ZONE_STATE đúng, SET danh hiệu chưa có bị chặn (dialog -10),
  gỡ danh hiệu + DB + sở hữu bền qua đăng nhập lại.
- Hai lỗi đã sửa trong lúc làm: (1) `Set<Integer>.contains(short)`
  không bao giờ khớp vì autoboxing Short != Integer — phải ép
  (int); (2) dữ liệu test của betest02 bị rác từ các đợt test cũ
  (cột chests chứa mảnh SQL do snapshot khôi phục qua helper db)
  làm loadData NPE — đã dọn dữ liệu hai tài khoản test về chuẩn.
- Kiểm mắt PASS: kiem-title-panel-v1.png, kiem-title-map-v1.png
  ("Đại Gia" vàng trên đầu nhân vật). Harness stage 155/156.
- Lần khởi động server đầu của đợt chết luồng accept vì NPE
  Boss.MoveArea (bản fan) đúng lúc spawn boss — đã xử lý bằng
  cách bỏ qua Boss trong announceEnter; chẩn đoán ban đầu về
  handshake timeout là SAI (gói bắt tay do client gửi trước),
  đã xác minh lại bằng thread dump + log accept.

## Sửa theo lệnh Tín 2026-11-05 — Danh hiệu là TAB trong panel nhân vật

- BỎ panel Danh hiệu riêng + pill ở panel kính. Bản chốt (Cards) của panel Thông tin nhân vật nay có THANH TAB "Thông tin | Danh hiệu" (đúng ownedTitleTab của bản chính) tại py+382; phía phải hàng tab hiện "Đang đeo: <tên>". CP_H tăng 470 -> 520 cho thoáng; chip tiền neo cố định py+308.
- Tab Danh hiệu: lưới 2 cột x 6 hàng, thẻ 292x48 (tên fontSmall đủ dài, dòng điều kiện fontTiny, nhãn Đeo/Đang đeo/Chưa có góc phải). Bấm thẻ đã sở hữu = đeo/gỡ (online gửi gói -120 sub 1).
- Client có FALLBACK_TITLE_DEFS (12 định nghĩa khớp server) dùng khi chưa nhận STATE. CHẾ ĐỘ CHƠI THỬ OFFLINE: đeo thử tự do mọi danh hiệu (chỉ hiển thị trên máy, myTitleId + remoteTitles của chính mình) để xem danh hiệu hiện trên đầu nhân vật; online vẫn theo sở hữu thật ở server.
- Chuỗi điều kiện rút gọn cho vừa thẻ: "Danh hiệu khởi đầu", "Nắm giữ 1.000.000 xu", "Sở hữu cần câu xịn", "Sở hữu 30 món đồ" (đồng bộ server + client + harness).
- Kiểm mắt: kiem-title-tab-v1.png (tab), kiem-title-dau-v1.png (danh hiệu "Đại Gia" vàng trên đầu nhân vật trong map). Test sống live_title_test.py vẫn ALL PASS sau khi sửa.

## V2 — ẢNH DANH HIỆU GỐC (Tín hỏi 2026-11-05 "Ko có danh hiệu ảnh à")

- GIẢI MÃ: ảnh nằm trong TextAsset `Title` (manifest: int 21 + từng
  cặp short id/short len) và `Title1..Title21` (short id + short len
  + PNG) ở `apk-avatardk-new/deep/textassets/`. Đã trích ra
  `assets/titles/title-<id>.png` (+ `.b64` trong `assets-src/`).
- NỘI DUNG ẢNH: 7 danh hiệu x 3 hạng (trong mỗi bộ ba: id đầu =
  Vàng, giữa = Bạc, cuối = Đồng): Nhà Thông Thái (cánh) · Thần Nông
  (lá) · Thần Câu (cá) · Cao Thủ 3X (cánh lớn) · Vua Đầu Bếp (xẻng
  + mũ bếp) · Nông Dân Chăm Chỉ (bông lúa) · Ngư Dân Chăm Chỉ (cá).
- ĐỊNH NGHĨA MỚI (thay hẳn bộ 12 tự soạn): 21 def, ngưỡng [PORT]
  theo bộ đếm `title_counters` (quiz_ok/fish/harvest/cook) + mg_stats
  (mini game): Nhà Thông Thái quiz 10/30/75 · Thần Nông thu hoạch
  40/120/300 · Thần Câu câu 30/80/200 · Cao Thủ 3X thắng 10/25/50
  · Vua Đầu Bếp nấu 15/40/100 · Nông Dân Chăm Chỉ thu hoạch 15/60/150
  · Ngư Dân Chăm Chỉ câu 10/25/60. Màu def: 4 vàng, 7 bạc, 8 đồng.
- BỘ ĐẾM: `TitleService.bump(userId, col)` (whitelist, nuốt lỗi);
  hook ở QuizService (trả lời đúng), ParkService.handleFinishFishing
  (câu được cá), FarmService.treeHarvest + harvestCook. Migration 1
  lần (title_meta defs_v2): xoá player_titles + title_id cũ vì bộ
  định nghĩa đổi hẳn; quét lại cấp từ bộ đếm (bắt đầu từ 0).
- HIỂN THỊ: trên đầu nhân vật vẽ ẢNH (rộng 118px, lọc Linear) thay
  dòng chữ màu; thiếu ảnh mới lùi về chữ. Tab Danh hiệu: thẻ 292x74
  hiện ảnh gốc + dòng điều kiện + nhãn Đeo/Đang đeo/Chưa có, phân
  trang 8 thẻ/trang (1/3, 2/3, 3/3). Hạng "Đang đeo: ..." ở hàng tab
  dùng fontTiny + truncTo 244 cho khỏi chồng nút tab.
- Test sống live_title_test.py v2 ALL PASS (21 defs, owned đúng bộ
  {2,3,6,9,11,12,18,20,21}); bump quiz đã kiểm bằng live_quiz_test
  (quiz_ok của 1001 tăng lên 1). Kiểm mắt: kiem-title-anh-tab-v1.png
  + kiem-title-anh-dau-v1.png.
