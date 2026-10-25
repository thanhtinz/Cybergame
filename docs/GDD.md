# Neo Làng: Cyber Grove — GDD tóm tắt (bản triển khai v1)

*Bản đầy đủ: tài liệu PDF "Tài liệu thiết kế game Neo Làng" v1.3 (25/10/2026).
Tài liệu này là phần đã khóa và đã triển khai trong code.*

## Spec đã khóa

- Thuần 2D, nhìn ngang side-view, **màn hình ngang** cho mọi màn chơi. Không 3D,
  không 2.5D/isometric, không chế độ dọc.
- Chỉ mượn **gameplay** của Ninja School + Avatar; đồ họa, nhân vật, kiến trúc là
  Cyber Trunk nguyên bản. Cấm tạo hình ninja truyền thống và nông dân truyền thống.
- UI tiếng Việt, không emoji (icon vẽ bằng hình học/CSS-style).

## Tạo nhân vật & chọn nghề (luồng đã chốt)

- Tạo nhân vật: **chỉ Nam/Nữ + tên + tuỳ biến**. Không chọn class.
- Bố cục màn tạo nhân vật (mockup đã duyệt, `art/create_male.jpg` / `create_female.jpg`):
  trái = nhân vật lớn trên bệ vòng sáng trong pod kính + pet robot; phải = panel
  (toggle Nam/Nữ trên cùng, 4 tab Tóc/Mắt/Da/Trang phục, lưới 3 thẻ lớn — thẻ chọn có
  viền cyan + dấu tick — ô tên + nút xúc xắc ngẫu nhiên cả ngoại hình lẫn gợi ý tên,
  nút XÁC NHẬN cam lớn). Không dùng slider chi tiết.
- Tuỳ biến dữ liệu nhân vật: `hairStyleIdx` (0–2 theo giới; nữ mặc định = Tóc Hai Bên),
  `eyeColorIdx` (0=Nâu Hạt Dẻ, 1=Cyan Mạch Điện, 2=Tím Neon — chỉ đổi màu iris;
  mắt luôn vẽ kiểu **chibi dễ thương**: tròn, long lanh, chuyển màu mượt + 2 điểm sáng
  trắng — không hoa văn mạch điện trong mắt, không vẽ kiểu thật),
  `skinToneIdx` (0=Sáng, 1=Trung Bình, 2=Ngăm),
  `hairColorIdx`, `suitColorIdx`, `outfitId` (3 bộ: `bo_tan_cu_dan`,
  `bo_street_neon`, `bo_tham_hiem` — mặc từ đầu hoặc mua ở shop thời trang).
- Nhân vật gốc: cư dân Cyber Trunk, set "tân cư dân" trung tính dùng cho cả hai giới
  (áo khoác techwear teal viền mạch cyan, balo pin, boots LED; nam thêm khăn chip cam,
  nữ tóc hai bên hồng san hô + vòng LED + váy xếp ly tech). Kỹ năng cơ bản chung:
  Đòn Cơ Bản, Lăn Né, Tia Năng Lượng.
- Thiết kế chuẩn: `core/src/main/resources/art/hero_male.jpg`, `hero_female.jpg`.
- Class chọn **trong game** tại **Trạm Định Hướng** (pod ở giữa thị trấn, có NPC
  Hướng Đạo Viên), điều kiện: **cấp ≥ 5** và xong nhiệm vụ giới thiệu
  **q0 "Chào Neo Làng"**. Đổi nghề miễn phí tại trạm **từ cấp 10**.
- 4 class: Blade Runner (lưỡi năng lượng neon / thám hiểm), Drone Master (bầy drone /
  vận chuyển), Bio-Hacker (hồi phục, vườn kính thủy canh / trồng trọt công nghệ),
  Engineer (khiên năng lượng, tháp / chế tạo). Mỗi class 3 kỹ năng.

## Thế giới

- Quảng trường Gốc Cây (hub): dải đất phẳng nửa dưới, hàng mặt tiền pod shop phía sau
  nhân vật — cafe Pod Kính, shop thời trang, ngân hàng trong rễ cây, pet shop,
  Trạm Định Hướng, bảng nhiệm vụ hologram. Tên trên đầu, bong bóng chat, mũi tên
  "Vào" phát sáng ở cửa shop. HUD thị trấn tối giản (Menu, Thư, Chat trên-trái;
  Chạy + Cảm xúc trên-phải).
- Bìa Rừng Mạch Điện (farm): HUD chiến đấu (HP/năng lượng trên-trái, minimap
  trên-phải, joystick dưới-trái, kỹ năng dưới-phải), quái glitch theo cấp.
- Khu Nhà Cành (cá nhân): vườn kính 6 ô + lưới xây 10×6.
- Ngày–đêm: 1 ngày game = 480 giây. Bão Dữ Liệu: chu kỳ ~5 phút chờ / 90 giây bão,
  mục tiêu dọn 6 quái, thưởng Xu Lá + Nhựa Sáng.

## Hệ thống

- Sinh tồn nhẹ: No bụng / Ấm áp / Pin thiết bị (0–100). Về 0 → pet kéo về nhà,
  không mất đồ. Hồi bằng thức ăn cafe/tự nấu, trạm sưởi/áo ấm, trạm sạc.
- Nhiệm vụ chính (chuỗi): q0 Chào Neo Làng (nói chuyện) → q1 Trồng cây đầu tiên →
  q2 Thu hoạch → q3 Diệt glitch → q4 Chọn nghề → q5 Xây pod → q6 Ăn ngon →
  q7 Bão Dữ Liệu → q8 Lên cấp 8. Nhiệm vụ ngày: diệt quái / thu hoạch / xây dựng.
- Kinh tế: Xu Lá (mềm) + Tinh Thể Mạch (cứng, chỉ trang trí/tiện ích — không bán
  sức mạnh).
- Pet robot: đi theo, phụ nhặt đồ rơi, nâng cấp được ở pet shop.
- Lưu local toàn bộ trạng thái (nhân vật, vườn, xây dựng, nhiệm vụ, đồng hồ).

## Kỹ thuật

- Java 17 + LibGDX 1.12.1; desktop LWJGL3; Android module (landscape, min SDK 24).
- Khung hình gốc 1280×720, ExtendViewport: máy dài hơn mở rộng tầm nhìn ngang,
  HUD trong vùng an toàn, nút chạm ≥ 48dp, tùy chọn phóng UI 100–130%, FPS 30/60.
- Âm thanh tự tổng hợp bằng code (WAV PCM), nhạc vòng lặp nhẹ.
