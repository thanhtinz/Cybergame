# Neo Làng: Cyber Grove

Game chibi **thuần 2D, màn hình ngang** (Java + LibGDX) — gameplay của Ninja School
(lên cấp, nhiệm vụ, farm quái) gặp đời sống xã hội của Avatar (thị trấn, thời trang,
cafe, trồng trọt), đặt trong thế giới **Cyber Trunk** nguyên bản: một Cây Mẹ khổng lồ
có mạch neon, cư dân sống trong các pod kim loại – kính gắn trên thân cây.

> Đồ họa 100% Cyber Trunk tự thiết kế: techwear neon, kính visor, balo pin, pet robot.
> Không tạo hình ninja truyền thống, không nông dân nón rơm, không nhà tranh.
> Ảnh tham khảo bố cục thị trấn chỉ để định hướng — mọi thiết kế trong game là mới.

## Luồng chơi

1. **Màn hình chính** → Tạo nhân vật.
2. **Tạo nhân vật — chỉ chọn Nam hoặc Nữ, đặt tên, và tuỳ biến ngoại hình.**
   *Không chọn class ở bước này.* Bố cục màn hình (theo mockup đã duyệt): **bên trái**
   là nhân vật đứng lớn trên bệ vòng sáng trong **pod kính** (nền thân cây cyber) kèm
   pet robot; **bên phải** là panel gồm toggle **NAM/NỮ** trên cùng, **4 tab
   (Tóc / Mắt / Da / Trang phục)** với lưới 3 thẻ lớn cho tab đang chọn (thẻ được chọn
   có viền cyan + dấu tick), ô nhập tên + **nút xúc xắc** (ngẫu nhiên cả ngoại hình lẫn
   gợi ý tên), và nút **XÁC NHẬN** cam lớn ở dưới cùng. Chỉ chạm chọn thẻ — không có
   thanh trượt chi tiết. Tuỳ biến gồm: **kiểu tóc** (nam: Rối Tự Nhiên /
   Undercut + Băng Phát Sáng / Xoăn Phồng + Mầm Cây; nữ: **Tóc Hai Bên (mặc định)** /
   Dài Thẳng Hime / Bob + Tai Nghe) + màu tóc, **màu mắt** (Nâu Hạt Dẻ / Cyan Mạch Điện /
   Tím Neon — mắt chibi tròn long lanh, chỉ đổi màu iris), **3 tông da** (Sáng /
   Trung Bình / Ngăm), và **bộ đồ khởi đầu** (Tân Cư Dân teal / Street Neon
   đen – hồng / Thám Hiểm ô-liu – cam cho nam, bạc hà – cam cho nữ — cũng mua/đổi
   được ở shop thời trang). Nhân vật là "tân cư dân" Cyber Trunk, dùng bộ kỹ năng
   cơ bản chung cho tới khi chọn nghề. Bảng tuỳ biến chuẩn:
   `art/custom_male.jpg`, `art/custom_female.jpg`; mockup màn tạo nhân vật:
   `art/create_male.jpg`, `art/create_female.jpg`; tạo hình gốc:
   `art/hero_male.jpg`, `art/hero_female.jpg` (bản nữ tóc hai bên).
3. **Thị trấn (hub):** dải đất ngang, hàng pod shop mặt tiền (cafe, thời trang,
   ngân hàng gắn vào rễ cây, pet shop), NPC có tên trên đầu và bong bóng chat,
   bảng nhiệm vụ hologram. HUD thị trấn tối giản.
4. **Chọn nghề tại "Trạm Định Hướng"** ở giữa thị trấn — mở khi **cấp ≥ 5** và đã
   hoàn thành nhiệm vụ giới thiệu *"Chào Neo Làng"*:
   - **Blade Runner** — lưỡi năng lượng neon, tốc độ cao, nghề thám hiểm.
   - **Drone Master** — bầy drone tấn công/khiên, nghề vận chuyển – trinh sát.
   - **Bio-Hacker** — hồi phục/buff, vận hành vườn kính thủy canh bằng drone.
   - **Engineer** — khiên năng lượng, tháp hỗ trợ, nghề chế tạo.
   Đổi nghề miễn phí tại trạm từ cấp 10.
5. **Khu Vành Đai Neon (map phiêu lưu):** HUD chiến đấu riêng (HP/năng lượng,
   minimap, joystick, nút kỹ năng), quái glitch robot – bọ, rơi Linh Kiện / Nhựa Sáng.
6. **Sinh tồn nhẹ:** 3 chỉ số *No bụng – Ấm áp – Pin thiết bị*; về 0 chỉ bị pet kéo
   về nhà, **không mất đồ, không tụt cấp**.
7. **Khu Nhà Cành:** vườn kính thủy canh 6 ô (trồng – lớn theo thời gian – thu hoạch)
   và xây pod theo ô 10×6 (vỏ pod, giường, bếp, xưởng, trạm sạc, dock pet...).
8. **Sự kiện Bão Dữ Liệu:** định kỳ quái tràn vào bìa rừng; dọn đủ mục tiêu nhận
   Nhựa Sáng và Xu Lá.
9. Chu trình ngày – đêm, nhiệm vụ chính theo chuỗi + nhiệm vụ ngày, 2 tiền tệ
   (**Xu Lá**, **Tinh Thể Mạch** — chỉ mua đồ trang trí/tiện ích, không bán sức mạnh),
   pet robot đi theo phụ nhặt đồ, lưu/tải local.

## Công nghệ

- Java 17 + LibGDX 1.12.1, một codebase: `core` (logic + màn hình), `desktop`
  (LWJGL3), `android` (khóa landscape trong manifest).
- Logic gameplay nằm ở package `com.neolang.cyber.model` thuần Java (không phụ
  thuộc LibGDX) để kiểm chứng headless.
- Font Noto Sans (hỗ trợ đầy đủ tiếng Việt) sinh bằng FreeType lúc chạy.
- Âm thanh tự tổng hợp (WAV sinh bằng code), không dùng tài nguyên thương mại.

## Chạy thử

Yêu cầu JDK 17 và Gradle 8.10+.

```bash
# Khôi phục tài nguyên nhị phân (ảnh/font) từ bản .b64 trong repo (chỉ cần 1 lần)
python3 tools/restore_assets.py

# Kiểm chứng logic headless (class/chọn nghề, chiến đấu, vườn, xây dựng, nhiệm vụ,
# sinh tồn, bão, lưu/tải)
gradle :core:verifyLogic

# Chạy bản desktop
gradle :desktop:run

# Chụp ảnh màn hình tự động (title/town/adventure/plot) để kiểm tra hình ảnh
gradle :desktop:run --args="--screenshot screenshots"

# Build Android (cần Android SDK, biến ANDROID_HOME hoặc sdk.dir trong local.properties)
gradle :android:assembleDebug
```

## Cấu trúc thư mục

```
core/src/main/java/com/neolang/cyber/
  model/    Logic thuần: Player, GameClass, ClassChoice, Combat, Garden,
            BuildPlot, QuestLog, Survival, DataStorm, WorldClock, SaveData...
  ui/       Tiện ích vẽ (Ui) + vẽ nhân vật/pod/quái/pet bằng shapes (Draw)
  screens/  Title, Create, Town, Adventure, Plot
  verify/   VerifyMain — bộ kiểm tra logic headless
core/src/main/resources/
  art/      Tranh vẽ tay (key art, thị trấn, 4 class, nhân vật chính nam/nữ)
  fonts/    Noto Sans Regular/Bold
assets-src/ Các file .b64 của tài nguyên nhị phân + tools/restore_assets.py
docs/       Tài liệu thiết kế (GDD) tóm tắt
```

Tài liệu thiết kế đầy đủ: xem `docs/GDD.md`.
