# Rà soát hình ảnh bằng mắt — tour chụp AvatarDK, 2026-11-04

## KẾT LUẬN ĐỐI CHIẾU (Bé muse kiểm lại từng mục bằng ảnh phóng to + dữ liệu gốc, 2026-11-04 17:xx)

**ĐÃ SỬA (lỗi thật):**
1. Panel glass quá trong (GLASS_FILL 0.62→0.85, GLASS_FILL_DARK 0.78→0.92) — cả hệ panel.
2. Sheet biểu cảm: ĐÍNH CHÍNH — ảnh shot-feel-big là trạng thái CUỘN CỐ Ý của harness (debugExpandFeel(150) để kiểm tra cuộn), hàng đầu bị cắt là hành vi cuộn bình thường; ảnh shot-feel (cuộn 0) cho thấy mọi mặt nguyên vẹn. Chỉnh hình học nhẹ cho gọn: gridTop 34→44 khớp mép clip, feelMaxScroll đồng bộ, cap scale mặt 2.2→2.0.
3. Avatar panel tương tác chỉ hiện mảnh cánh (doll cánh rộng): căn lại theo TÂM MẶT (part zOrder 40) thay cho tâm bounds.
4. Cá chép trong hồ hiện thành quả BÍ NGÔ: idImg vật nuôi bị tra nhầm vào không gian farm/img (id 330 ở đó là bí ngô). Hình thân vật nuôi chuyển sang thư mục riêng farm/animal/ (chưa có dữ liệu → không vẽ, đúng nợ đào từ server live đã ghi).
5. Shop nhà: món "Emty" (id 47, giá 0/0, placeholder của dataset gốc) lẫn vào danh sách bán → lọc món không có giá khỏi shop. Đồng thời shop nhà chuyển sang hình thức mua THỐNG NHẤT toàn game: thẻ không hiện giá nữa, bấm Xem → hoá đơn chung (SB_HOUSE) → Mua → cầm đi đặt, tiền trừ theo loại tiền đã chốt ở hoá đơn.
6. Map 22 (Khu nhà ở): thiếu bg + tên trong maps.json → bg=1, tên "Khu nhà ở".
7. Tàu Chiến: danh sách tàu mờ không đọc được khi hết tàu (alpha 0.4→0.75); chú thích "Trượt" chui dưới bàn địch (nén cụm chú thích vào panel giữa, kết thúc ở x=798); banner bọc chữ 330→300 tránh dính nhãn hàng bàn địch.
8. Thẻ Snake ở màn chọn mini game trống icon → giữ chỗ khung xanh + chữ S (gốc không có icon select cho Snake).
9. Shop đồ câu: icon cần câu tre quá mảnh ở 38px → 46px.

**KHÔNG PHẢI LỖI (đã kiểm chứng, giữ nguyên):**
- NPC "Tuấn" đứng giữa hồ (map 16) / lơ lửng ở Bến cảng (map 28) / bị cột che (map 30): Tuấn là NGƯỜI CHƠI GIẢ do harness chụp ảnh tiêm vào ở toạ độ cố định (525, 80) — map nào toạ độ đó rơi xuống nước thì trông như lỗi; người chơi thật không có nhân vật này.
- Icon Cá chép ở shop vật nuôi (hình cá cam/xanh của object 314 — nhìn nhỏ dễ nhầm người bơi), icon Lúa (bông lúa vàng img[7]=43 trong farmimgdata — khác hẳn nải chuối farm/img/134 của Chuối), icon sản phẩm Cừu (thùng len object 1018): đều là hình gốc đúng dữ liệu.
- Mũi tên QTE câu cá: ảnh phóng to là mũi tên xanh dương rõ ràng (arrow5); hai file arrow0/arrow9 nền đen là dữ liệu gốc từ server.
- NPC đấu giá/lái buôn bị quầy/cây che một phần: thứ tự lớp vẽ theo chân (y-sort) — chủ quầy đứng sau quầy là đúng gốc.
- Map 61/67 (nội thất nhỏ trên nền đen): map nội thất không nền, gốc cũng tô tối xung quanh; map 67 là nội thất Mỹ Viện đầy đủ. Ô đen ở map 67 là ô cửa ra vào.
- Tên món/part bị cắt có dấu "…" (shop đồ câu, tạo nhân vật, kho farm): cắt chữ đúng chuẩn đo thật (truncTo), tên gốc dài vì kèm "(30 ngày)".
- Panel Caro thắng che bàn: panel kết quả hiện sau 1,6s như gốc, dãy thắng đã hiện trước đó.
- Map 108 trống: đường đua chưa port (đã có trong hàng đợi) — không phải lỗi hình.

---

# BÁO CÁO GỐC CỦA LƯỢT RÀ SOÁT (giữ nguyên để đối chiếu)

Phạm vi đã xem: **139/189 ảnh** — toàn bộ **127 ảnh của tour mới nhất (mtime 2026-11-04)** + 12 ảnh cũ chọn lọc (31/10–03/11). 50 ảnh còn lại là biến thể UI/map cũ đã bị thay thế trong quá trình phát triển (hub v1–v4, chatframe v1–v2, tabs, biển trạm xe các phiên bản, feel các phiên bản, menu v2–v3, bg v1–v2, map vẽ tay cũ) — đã đối chiếu đại diện, không tính là lỗi hiện hành. Chỉ ghi lỗi NHÌN THẤY RÕ trong ảnh, không suy đoán.

## TOP 5 LỖI NẶNG NHẤT

| # | File | Lỗi |
|---|------|-----|
| 1 | `shot-tile-map61.png` | Map gần như TRỐNG RỖNG: chỉ một mảng sàn gỗ nhỏ + 1 bàn + 2 ghế đẩu giữa nền đen mênh mông; không tường, không vật thể, không NPC. |
| 2 | `shot-map344-22.png` | Mất toàn bộ nền trời: phía trên và phía dưới dãy nhà là ĐEN đặc, nhà nổi trên nền đen (các map khác đều có trời xanh). |
| 3 | `shot-mail.png` | Panel Hòm Thư gần như TRONG SUỐT: toàn bộ map, NPC và khung chat phía sau hiện xuyên qua, chữ trong panel lẫn vào cảnh nền, rất khó đọc. |
| 4 | `shot-map344-16.png` | NPC Tuấn đứng GIỮA HỒ NƯỚC — chỉ nổi phần thân trên mặt nước trong hồ. |
| 5 | `shot-tile-map28.png` + `shot-map344-28.png` | NPC Tuấn LƠ LỬNG ngoài mép cầu tàu Bến cảng, thân treo trên mặt nước cạnh thuyền, chân không chạm sàn (2 ảnh, cùng vị trí, xác nhận lẫn nhau). |

## BẢNG LỖI ĐẦY ĐỦ

### Nhóm MAP

| File | Vị trí | Mô tả | Mức |
|------|-------|-------|-----|
| shot-tile-map61.png | Toàn ảnh | Map chỉ có 1 mảng sàn gỗ + bàn + 2 ghế giữa nền đen; thiếu gần như toàn bộ nội dung map | Nặng |
| shot-map344-22.png | Nửa trên + dưới ảnh | Nền trời đen đặc sau dãy nhà dân cư; không có lớp nền như các map khác | Nặng |
| shot-tile-map67.png | Góc phải–dưới | Một ô tile cỏ xanh đốm trắng LƠ LỬNG, bị cắt dở, nằm ngoài vùng map trên nền đen | Vừa |
| shot-tile-map67.png | Dải tường phía trên | Nhiều ô cửa sổ là Ô ĐEN trống trên tường vàng (cạnh các tranh treo có hình bình thường) — nghi thiếu hình cửa sổ | Vừa |
| shot-map108.png (và bản cũ shot-map108-v3-1119.png) | Toàn ảnh | Map đường đua gần như trống: chỉ hàng rào gỗ + làn đường xanh, không khán đài, không NPC, không vật thể nào | Vừa |
| shot-citymap.png / shot-mapzoom.png | Góc dưới–phải | Nhãn vùng "Nông trại" bị thẻ thông tin đang mở che mất chữ đầu, chỉ còn "ông trại" | Nhẹ |

### Nhóm NPC / nhân vật

| File | Vị trí | Mô tả | Mức |
|------|-------|-------|-----|
| shot-map344-16.png | Bên phải, trong hồ | NPC Tuấn đứng giữa hồ nước, chìm tới ngang thân | Nặng |
| shot-tile-map28.png, shot-map344-28.png | Mép phải cầu tàu | NPC Tuấn lơ lửng trên mặt nước, ngoài sàn cầu | Nặng |
| shot-map344-30.png | Mép phải | NPC Tuấn bị cột đen (dải biên map hẹp) che mất một phần thân | Vừa |
| shot-city.png, shot-chat-emoji.png | Giữa–phải | NPC "đấu giá" bị bụi cây/quầy che gần hết thân, chỉ còn đầu + vương miện; NPC cánh bướm sau quầy quay số cũng chỉ lộ một phần cánh | Vừa |
| shot-farm.png, shot-farm-star.png | Giữa–trên | NPC đội nón rơm sau cây khế/luống cây chỉ thấy nón + đầu, thân bị cây che; Tuấn đứng chồng lên cây trồng trong luống, tên bị cây che | Vừa |
| shot-portal-farmway.png, shot-map26.png, shot-map12.png | Mép trái / giữa | NPC lai.buon và Than.Tai.Xiu bị bụi cỏ/hoa che phần lớn thân dưới, tên bị che một phần (cùng một mẫu lỗi lớp vẽ NPC sau vật trang trí) | Nhẹ |
| shot-player-panel.png | Ô avatar trong panel | Avatar tròn của người chơi Linh chỉ hiện một MẢNH CÁNH TÍM, không thấy đầu/mặt — crop avatar sai | Vừa |
| shot-map28.png | Giữa ảnh | Cụm pixel đỏ lạ chồng ngay trên đầu NPC cua.hang (không xuất hiện ở các ảnh khác của cùng NPC) — chưa rõ bản chất | Nhẹ |

### Nhóm HÌNH / ICON SAI HOẶC THIẾU

| File | Vị trí | Mô tả | Mức |
|------|-------|-------|-----|
| shot-farm-shop-animals.png | Ô "Cá chép" | Icon Cá chép là hình NGƯỜI BƠI/LẶN màu xanh–cam, không phải cá | Vừa |
| shot-farm-animal.png | Ô hồ góc phải | Ô thông tin hồ "Cá chép" hiện một QUẢ BÍ NGÔ giữa mặt hồ, không có hình cá nào | Vừa |
| shot-farm-shop.png, shot-farm-store-full.png, shot-farm-store-products.png | Ô "Lúa" | Icon của Lúa là NẢI CHUỐI — trùng icon của Chuối | Vừa |
| shot-farm-store-products-p2.png | Ô "Cừu" | Icon sản phẩm Cừu là hình PLACEHOLDER tranh ảnh (khung hình núi/mặt trời), không phải cừu/sản phẩm cừu | Vừa |
| shot-shop.png | Ô đầu tiên tab Áo | Icon trông như nắm tay/cục thịt màu da, không nhận ra là áo | Nhẹ |
| shot-fish-shop.png | Ô "cần câu tre" | Icon cần câu tre gần như vô hình — chỉ còn một vệt xanh mảnh phía trên chữ | Nhẹ |
| shot-shop-vehicle.png | Tab "Phương tiện" | Cả tab không có phương tiện nào: toàn hoa, gậy cù léc, dù, bát, mặt nạ Doraemon | Nhẹ |
| shot-mg-select-zone2.png | Thẻ "Snake" | Thẻ game trống hoàn toàn, không có icon (các thẻ khác đều có) | Nhẹ |

### Nhóm PANEL / UI / CHỮ

| File | Vị trí | Mô tả | Mức |
|------|-------|-------|-----|
| shot-mail.png | Toàn panel | Hòm Thư trong suốt — xem Top 5 | Nặng |
| shot-msg-inbox.png, shot-msg-chat.png, shot-friends.png, shot-friend-tap-chat.png, shot-settings.png | Toàn panel | Cùng hệ: panel Tin nhắn / Bạn bè / Cài đặt bán trong suốt, thấy rõ map + NPC + khung chat phía sau xuyên qua nền panel | Vừa |
| shot-mg-select.png, shot-mg-select-zone2.png, shot-mg-lobby.png, shot-mg-top.png | Trong panel | Panel Mini game bán trong suốt, nhìn xuyên thấy lưới "Cửa hàng đồ câu" của panel phía dưới chưa đóng — hai giao diện chồng nhau | Vừa |
| shot-giftform.png | Giữa ảnh | Ba lớp chồng nhau (Hòm Thư + Cài đặt + form giftcode); dòng chữ hướng dẫn trong form bị chữ của panel phía sau xuyên lên chồng chữ, khó đọc | Vừa |
| shot-player-panel-sub.png | Panel Thông tin dưới | Panel bị khung chat đè lên; các dòng chỉ số chồng với chữ chat phía sau | Vừa |
| shot-tc-place.png | Danh sách tàu bên phải | Các dòng tàu không được chọn bị làm mờ quá mức: tên tàu + chữ "còn 0" gần như không đọc được | Vừa |
| shot-tc-battle.png, shot-tc-spectate.png | Cụm chú thích giữa 2 bàn | Nhãn "Trượt" bị cắt cụt, chỉ còn "Tr…" vì vướng bàn cờ bên phải | Vừa |
| shot-tc-battle.png | Nhãn hàng bàn địch | Bàn địch thiếu nhãn số hàng "1" (nhãn bắt đầu từ 2), bàn mình đủ 1–10 | Nhẹ |
| shot-tc-battle.png | Dòng hướng dẫn | "Lượt của bạn — chọn ô để1 bắn." — ký tự "1" lạ dính vào chữ "để" | Nhẹ |
| shot-feel-big.png, shot-online.png | Hàng đầu sheet biểu cảm | Hàng mặt đầu tiên bị cắt mất nửa trên, chỉ thấy nửa dưới của các mặt | Vừa |
| shot-fish-qte.png | Giữa màn hình + thanh QTE | Icon yêu cầu bấm hiển thị là DẤU CỘNG (+) xanh pixel thay vì mũi tên hướng, trong khi các nút bấm là mũi tên | Vừa |
| shot-house-buy.png | Ô hàng 2 | Món nội thất tên "Emty", giá "0 lượng" — món rỗng/thiếu dữ liệu vẫn nằm trong danh sách bán | Vừa |
| shot-house-buy.png | Ô "màn che" | Dòng giá bị cắt cụt: "40.000 xu · 15 lượ…" | Nhẹ |
| shot-fish-shop.png | 4 ô tên dài | Tên món bị cắt cụt: "cần câu sắt(20 ng…", "cần câu VIP(30 ng…", "vé câu cá lóc(5 ng…", "vé câu cá mập(2 n…" | Nhẹ |
| shot-createchar.png | Các ô chọn part | Tên part bị cắt cụt: "Tóc công tử chung tì…", "Bạch mã công tử(30 …", "Quần thun đen(30 ng…" | Nhẹ |
| shot-farm-cook.png | Danh sách món | Tên món "Tinh dầu hướng d…" bị cắt cụt | Nhẹ |
| shot-farm-store-products-p2.png | 2 ô tên dài | "Hoa Hướng …" và "Trà sữa trái …" bị cắt cụt | Nhẹ |
| shot-chat-system.png | Dòng thông báo 2 | Chữ cuối dòng chạm/cắt ở mép phải khung chat ("…người chơi khác.") | Nhẹ |
| shot-caro-battle.png | Bàn sau panel WIN | Phần bàn lộ ra gần như không thấy dãy 5 quân thắng — chỉ 1 dấu X nhỏ phía dưới panel | Nhẹ |

## NHÓM ẢNH SẠCH (không thấy lỗi)

- **Map thế giới (tour mới):** shot-map344-1, -5, -18, -21, -23, -24, -26, -27, -29; shot-tile-map13, -20, -21; shot-map1, -11, -12, -18, -21, -26; shot-link-27; shot-citymap-maze; shot-day-park; shot-day-bus; shot-bus-arrive; shot-bus-stop; shot-portal-city; shot-portal-farm.
- **Farm:** shot-farm-west, shot-farm-shed, shot-farm-store, shot-farm-seeds, shot-farm-bill, shot-farm-shop-items, shot-farm-shop-items-p3, shot-farm-kitchen-hint.
- **Nhà:** shot-house-room, shot-house-menu (shot-house sạch ngoài phần bị khung chat che theo bố cục chụp).
- **Câu cá:** shot-fish-cast, shot-fish-bill.
- **Mini game:** shot-aincrad-battle, shot-mg-invite (màn Aincrad), shot-trucxanh-battle.
- **Màn hình vào game:** shot-banner, shot-login, shot-server, shot-server-panel, shot-download, shot-createchar-nu, shot-board.
- **Panel còn lại:** shot-character (doll khớp bản gốc), shot-char-panel, shot-bag, shot-bag-wearing, shot-shop-quan, shot-shop-bill-xu, shot-shop-choice, shot-chest, shot-chest-confirm, shot-chat, shot-chat-big, shot-chat-voice, shot-textbox, shot-laibuon-guide, shot-laibuon-menu, shot-music-search, shot-music-playlist, shot-npc-menu, shot-pass-form, shot-objects, shot-menu-grid, shot-hub-menu, shot-action-sheet, shot-action-pose, shot-feel, shot-feel-sync, shot-mapzoom-in.
- **Ảnh cũ đã đối chiếu (sạch về map):** shot-xe-vao-tram-1830, shot-tram-day-du-2005, shot-bang-khu-so-2110, shot-map10-team-1905, shot-map12-vat-the-2005, shot-fix-map19-1240.

## Mẫu lỗi lặp lại đáng chú ý

1. **Panel glass xuyên sáng quá mức** — Hòm Thư tệ nhất; Tin nhắn, Bạn bè, Cài đặt, Mini game đều thấy cảnh phía sau. Đây là nhóm lỗi diện rộng nhất, khớp cảm giác "giao diện lỗi nhiều" khi chơi thật.
2. **NPC bị lớp vật trang trí che thân / đứng sai mặt đất** — Tuấn trong hồ + lơ lửng ở Bến cảng là lỗi vị trí; đấu giá/lai.buon/người nón rơm bị cây–quầy che là lỗi thứ tự lớp vẽ.
3. **Icon nông sản/vật nuôi lấy sai không gian ảnh** — Cá chép (2 chỗ), Lúa, Cừu đều sai icon theo cùng một kiểu.
