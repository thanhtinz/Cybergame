# Nghiên cứu: BỘ THÚ ĐUA THẬT của Avatar gốc (2026-11-04)

Kết luận nhanh: **thú đua thật là 6 con pet chuyên dụng, ảnh là sprite sheet THÂN ĐẦY ĐỦ 5 hàng trong không gian ảnh vật thể (object / GET_IMG_ICON), idImg 1056–1061** — không phải icon đầu vật nuôi farm (310/311/312/1024/1025/1224) mà bản port đã dùng tạm (và bị Tín bác đúng).

## 1. Roster chính thức (6 con)

| idImg | Tên gốc | Nhận dạng từ sprite | Kích thước HD | Mỗi frame |
|---|---|---|---|---|
| 1056 | **Khủng Long** | khủng long đỏ, mặc áo đỏ logo "S" | 64×290 | 64×58 |
| 1057 | **Chó Siêu Nhân** | chó pug mặc bộ đồ Siêu Nhân đỏ, emblem "S" | 48×180 | 48×36 |
| 1058 | **Vẹt** | vẹt xanh dương | 34×200 | 34×40 |
| 1059 | **Mèo** | mèo vàng kem | 38×150 | 38×30 |
| 1060 | **Tuần Lộc** | tuần lộc: sừng nhánh, mũi đỏ | 50×290 | 50×58 |
| 1061 | **PiKaChu** | Pikachu (đuôi tia sét, má đỏ, khăn cam) | 56×230 | 56×46 |

Nguồn tên (2 nguồn độc lập, khớp nhau và khớp sprite 1-1):
- Bài giới thiệu tính năng Đua Pet của Avatar 230 (9/2013, trích nguyên văn thông báo Teamobi) trên diễn đàn Uhm.VN: *"Sẽ có 6 con vật bao gồm: Tuần Lộc, Chó Siêu Nhân – PiKaChu – Mèo – Khủng Long và Vẹt"*. Bài này cũng ghi đúng 9 mệnh giá cược 100/500/1k/2k/5k/10k/20k/30k/50k và ví dụ "tỉ lệ của Tuần Lộc là x3" — khớp giao thức bản port đã làm.
  https://uhm.vn/forum/Thread-phi%C3%AAn-b%E1%BA%A3n-Avatar-225-phi%C3%AAn-b%E1%BA%A3n-d%C3%A0nh-cho-c%C3%A1c-n%C3%B4ng-d%C3%A2n-%C4%91%91ch-th%E1%BB%B1c
- HieuMobile, bài "Trải nghiệm Đua Pet trong game Avatar": *"đặt cược 6 con pet được liệt kê sẵn: chó, tuần lộc, khủng long, vẹt, mèo và Pikachu"*.
  https://hieumobile.com/game/tin-tuc-game/dua-pet-avatar

Thứ tự 6 con trong một chặng là do server xếp (ảnh chụp 2013 xếp làn khác thứ tự id) — roster theo id ở bảng trên là cách sắp tự nhiên cho bản port; tên viết hoa đúng nguồn: "PiKaChu", "Chó Siêu Nhân", "Tuần Lộc".

## 2. Ba sheet anh em chưa có tài liệu đua (1052→ mở rộng: 1062–1064)

Cùng block id liên tục, cùng đúng định dạng sheet 5 hàng, nhưng **không xuất hiện trong bất kỳ danh sách thú đua nào tìm được** (đã search web 2 lượt):

| idImg | Con vật | Kích thước HD | Mỗi frame |
|---|---|---|---|
| 1062 | Rắn (xanh lá) | 52×170 | 52×34 |
| 1063 | Thiên Mã (ngựa có cánh) | 44×240 | 44×48 |
| 1064 | Thỏ (trắng) | 36×180 | 36×36 |

Khả năng: pet dự phòng/bổ sung ở phiên bản sau mà không còn tài liệu, hoặc sprite cho hệ pet đồng hành (Avatar có vật phẩm "Pet … vĩnh viễn" như Pet Vẹt Siêu quậy, Pet Pikachu ở sự kiện Tết 2015). **Không tự đưa vào roster đua nếu muốn đúng gốc**; có thể dùng làm pet luân phiên nếu Tín muốn mở rộng.

## 3. Bằng chứng kỹ thuật từ source gốc

- `RaceMsgHandler.PetRace.paint()` (client Pack5, còn nguyên ở bản chính 3.4.4 — metadata `all_types_full.json` cho thấy class PetRace ở 3.4.4 giống hệt): lấy `AvatarData.getImgIcon(idImg)` (không gian object, cùng kênh GET_IMG_ICON −80 / thư mục `res/hd/object/`) và cắt **chiều cao / 5** làm 5 hàng frame: `drawRegion(img, 0, FRAME[action][frame/2] * (h/5), w, h/5, …, HCENTER|BOTTOM)`.
- `RaceScr.FRAME` (hằng trong RaceScr.cs): action 0 (đứng) = hàng 0,1; action 1 (chạy) = hàng 2,3; action 2 (nghỉ/tè) = hàng 4. Cả 9 sheet 1056–1064 đều khớp tạo hình này khi xem từng hàng: hàng 0–1 đứng, hàng 2–3 chạy, hàng 4 mệt lè lưỡi/nằm nghỉ.
- Client **không hardcode** thú nào: lineup (gói 1) gửi `{id, rate, idImg, idIcon}`, info (gói 2) gửi `{idImg, tên, %thắng, tỉ lệ, phong độ, sức khỏe}` — tên và ảnh đều từ server. Server fan Pack5 không có engine đua (handler rỗng) nên roster gốc chỉ khôi phục được từ res + tài liệu web như trên.
- Quét toàn bộ `res/hd/object` (4.111 PNG): các sheet động vật 5 hàng gồm (a) thân vật nuôi farm — idImg trong `farm_info.dat`: {297–306, 309, 329, 330, 1014–1016, 1019–1021, 1225–1227, 1246–1248}, và (b) block **1056–1064 không thuộc farm** (đã đối chiếu: không id nào của block này xuất hiện trong farm-info-decoded.json). Block 1056–1064 bị chặn hai đầu bởi 1055 (icon nhỏ) và 1065 (sprite đếm ngược đua 76×168) — đúng một họ pet đua khép kín.
- Lỗi của bản port hiện tại: 310/311/312/1024/1025/1224 là **iconID (icon đầu)** của vật nuôi farm theo farm_info.dat (Gà 310, Bò 311, Heo 312, Chó 313, Cừu 1024, Vịt 1025, Trâu 1224) — ảnh đầu tĩnh, không phải thú đua, cũng không có frame chạy.

## 4. File ảnh (đã có sẵn trên máy, chưa đưa vào assets client của port)

- `~/workspace/avatardk-incoming/server-full/Avatar-Sv-master-master/res/hd/object/1056.png … 1064.png` (+ bản medium bằng nửa kích thước ở `res/medium/object/`)
- `~/workspace/avatardk-incoming/res-x/res/hd/object/` (bản sao tương tự)
- `~/workspace/avatardk-java/server/res/hd/object/1056.png … 1064.png` — **server của port đã có sẵn file** (phục vụ GET_IMG_ICON được ngay).
- Chưa có trong `~/workspace/avatardk-java/assets/house/img/` — muốn client vẽ offline thì chép 1056–1064 vào đó kèm file .b64 trong assets-src như các icon object khác.

## 5. Lưu ý khi áp dụng vào bản port

- Thay roster trong `RaceService` (server) + `WorldScreen` (client): id 0..5 ↔ img 1056..1061, tên Khủng Long / Chó Siêu Nhân / Vẹt / Mèo / Tuần Lộc / PiKaChu. (Thứ tự id↔con là chọn lựa của port — server gốc xếp ngẫu nhiên mỗi chặng; giữ thứ tự id tăng dần là hợp lý.)
- Client phải vẽ **theo frame hàng** như gốc: hàng = FRAME[action][tick], neo đáy giữa (HCENTER|BOTTOM), thay cho cách vẽ tĩnh hiện tại. Chiều cao frame từng con khác nhau (30–58px HD) — gốc vẽ theo kích thước thật của sheet, không ép về một cỡ.
- `idIcon` trong lineup: gốc vẽ bằng `AvatarData.paintImg` (vẽ nguyên ảnh, không cắt) ở ô ~31×35 hd trong danh sách cược — nếu gửi idIcon = idImg (sheet cao 150–290px) sẽ tràn ô; suy đoán server gốc gửi idIcon khác idImg, nhưng không còn dữ kiện nào cho thấy icon riêng của từng pet trong không gian object. Khuyến nghị cho port: gửi idIcon = idImg và phía client tự cắt hàng 0 khi vẽ danh sách cược + panel thông tin (đẹp và đúng ý đồ hiển thị).
- Tên gọi "Trường đua pet" (không phải "đua thú") là tên gốc theo mô tả map của bản chính 3.4.4: *"Trường đua pet sôi động"*; client Pack5 cũng dùng `nameCasinoOngame`/NPC Đua Pet. Bản port đang gọi "Đua thú" — không sai nội dung nhưng khác tên gốc.
