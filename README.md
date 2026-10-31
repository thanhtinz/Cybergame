# AvatarDK — dựng lại hoàn toàn bằng Java (LibGDX)

Port nguyên bản 100% game **AvatarDK** (release Pack5, github.com/Dreamtech-VN/Cozy_farming/releases/tag/Pack5)
sang Java, lần lượt từng hệ thống. Client Unity C# dịch ngược của gốc bị bỏ hẳn;
server gốc vốn là Java sẽ được giữ nền và nối lại ở đợt 2.

**Trạng thái: Đợt 1 (nền tảng) + hub v3 — ĐÃ PUSH lên `thanhtinz/Cybergame` (nhánh main).**

## Nếu bạn vừa clone từ GitHub

Asset nhị phân đã nằm sẵn trong `assets/` — clone về là build được ngay, không cần
khôi phục gì. (Thư mục `assets-src/` + `tools/restore_assets.py` là di sản của đợt
chuẩn bị push bằng snapshot trước đây; bản git hiện tại không cần tới chúng, giữ lại
chỉ để tham chiếu.)

**Không có trên GitHub:** thư mục `local-maven/` (238 MiB jar phụ thuộc offline,
đã chặn bằng .gitignore). Khi clone ở máy khác, bỏ cờ `--offline` để Gradle tải
dependency từ Maven Central, hoặc copy thư mục `local-maven/` từ máy chủ dự án sang.

## Môi trường build

```bash
export JAVA_HOME=$HOME/workspace/tools/jdk17
export GRADLE_USER_HOME=$HOME/workspace/tools/gradle-home
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv6Addresses=true"   # sandbox chặn loopback IPv4 của Java
export ANDROID_HOME=$HOME/workspace/tools/android-sdk
GRADLE=$HOME/workspace/tools/gradle-8.10.2/bin/gradle
```

| Việc | Lệnh |
|---|---|
| Test headless (paper-doll + animator) | `$GRADLE --offline :core:test` |
| Smoke nạp dữ liệu không giao diện | `$GRADLE --offline :desktop:smoke` |
| Chạy game desktop | `$GRADLE --offline :desktop:run` |
| Chụp ảnh kiểm chứng (cần Xvfb) | `xvfb-run -a $GRADLE --offline :desktop:shot` → `shot-city.png`, `shot-character.png`, `shot-farm.png` |
| Build APK debug | `$GRADLE --offline :android:assembleDebug` |

## Đợt 1 có gì

- **Khung LibGDX 1.14.0** (core / desktop LWJGL3 / android / ios stub), landscape 1280×720,
  dependency offline từ `local-maven/` (cùng cách làm với dự án LTS/TFT của chủ dự án).
- **Nạp dữ liệu gốc nguyên bản** từ Pack5 vào `assets/` (đã kiểm sha256 khớp file gốc ở các file mẫu):
  atlas nhân vật `big/0..4.png` (+ các file 11/21–25 đi kèm trong pack), `data/items.json`
  (5.496 part), `data/imgdata.json` (1.139 ImageInfo), `data/npcs.json` (28 NPC),
  các dải imagemap của map **10 (thành phố)**, **24 (khu mua sắm)**, **25 (nông trại)**.
- **Paper-doll assembler** (`core/.../data/PaperDoll.java`) — xem mục thuật toán bên dưới.
- **Màn thế giới**: ghép map từ các dải imagemap đúng cách của gốc, nhân vật Sơn đi lại bằng
  WASD / phím mũi tên / joystick cảm ứng, đổi hướng trái–phải (lật ngang), chu trình frame
  đứng/đi đúng bảng FRAME của gốc, NPC đứng theo tọa độ npcs.json, tên trên đầu (người chơi
  đỏ, NPC vàng như gốc), chat bong bóng local (Enter → gõ → Enter), HUD tối thiểu: thanh tên +
  cấp + tiền (ghi rõ là số liệu mẫu), nút Menu (mở panel hướng dẫn), nút đổi map 1/2/3.
- **Kiểm chứng đợt 1**: 9 test JUnit PASS; smoke PASS; 3 ảnh chụp Xvfb đã tự mở kiểm bằng mắt —
  ảnh zoom cho thấy nhân vật lắp bằng Java khớp gần như pixel-perfect với ảnh đối chiếu gốc
  `chars/son.png`; ảnh thành phố có Sơn + NPC đấu giá / quay số + bong bóng chat tiếng Việt có dấu.

## Thuật toán paper-doll đã đọc từ client gốc (không đoán)

Nguồn: `project-client-avt-main/Assets/Scripts/Assembly-CSharp/` trong Pack5.

- `AvatarData.readImageData` + `ImageInfo`: mỗi ImageInfo gồm ID, bigID, x0, y0, w, h theo
  đơn vị **medium**; khi vẽ nhân với `AvMain.hd` (= 2 ở bản hd).
  Trong bản trích `imgdata.json`, khóa tra cứu là cột **`item_id`** (0..1138); cột `id` chỉ là
  id dòng (= item_id + 1 trên cả 1.139 dòng). Cột `image_id` = bigID.
- `APartInfo.paintAvatar`: part có 15 bộ (imgID, dx, dy) — trong items.json là chuỗi
  `animation` 15 phần tử {img, dx, dy}; vùng vẽ = atlas `big[bigID]` tại
  (x0·hd, y0·hd, w·hd, h·hd), vị trí = điểm chân + (dx·hd, dy·hd); quay trái (direct = LEFT = 2)
  thì trừ thêm (2·dx + w)·hd khỏi x và lật ngang.
- `Avatar.paint` + `Avatar.orderSeriesPath`: các part được sắp tăng dần theo **zOrder**
  (10 quần · 20 áo · 30 mặt · 40 mắt · 50 tóc · …) rồi vẽ lần lượt.
- `Avatar.FRAME` (static init trong Avatar.cs) + `Base` (STAND = 0, RUN = 1):
  frame hiển thị = FRAME[action][cFrame], cFrame vòng 0..9. STAND dùng nhịp 0×5 rồi 1×5;
  RUN dùng 0×5 rồi 2×5; action 2..13 dùng frame cố định = action + 1. Di chuyển lên/xuống
  không đổi hàng frame — chỉ hướng ngang quyết định lật.
- Ghép map: `LoadMap.loadImageMapFull` nạp `imagemap/<id>/0.png, 1.png, ...`;
  `LoadMap.paintCreateMap` đặt các dải cạnh nhau (dải sau chồng 1px: x = −1 + Σ(w−2)),
  đáy mọi dải trùng đáy map (Hmap·w·hd; với map 10/24/25, Hmap = 8, w = 24, hd = 2 → 384px);
  `LoadMap.paintM` vẽ tiếp 3 ảnh `daydien*.png` nối tiếp nhau ở phía trên.
  Tọa độ nhân vật/NPC trong dữ liệu là đơn vị medium, gốc trên–trái, tính từ điểm chân.

## Giả định / hạn chế nói thẳng

- **Part động (id ≥ 2000)**: ảnh do server stream riêng (`AvatarData.getImagePart`), không có
  trong atlas offline — assembler bỏ qua và ghi vào `skippedParts`, không bịa ảnh.
  Hệ quả: NPC duy nhất của map 10 trong dữ liệu, **Chủ Hôn (278, 68), chỉ mặc part động 2480**
  nên hiện chỉ thấy tên. Đợt 1 đặt thêm 2 NPC của **map 9** (khu thành phố còn lại trong dữ
  liệu gốc: quay số, đấu giá) vào map 10 để có NPC lắp được.
- **756 part có `animation = "[]"` trong DB** (404 part tĩnh có đủ 15 frame; tổng part lắp
  trọn được offline là 4.740/5.496): đó là các PartFollow/PartSmall của gốc — dữ kiện
  follow/color nằm trong luồng binary do server cấp (`AvatarData.readAvatarPart`), DB không
  chứa. Ví dụ NPC lai.buon (map 25) lắp được 6/7 part, thiếu part 273.
- **Bảng `map_item` trong DB rỗng** (1 dòng mẫu) như kiểm kê Pack5 đã ghi: vị trí vật thể lấy
  từ dải imagemap dựng sẵn, không dựng lại từ DB.
- Chưa port lớp nền background (`LoadMap.loadBG`) và hiệu ứng thời tiết/ngày–đêm của gốc;
  phần trời phía trên dải map hiện là nền tối của màn hình.
- Tọa độ đứng của NPC lấy nguyên từ npcs.json; lai.buon (map 25, y = 45) đứng gần mép trên
  nên nhãn tên bị thanh HUD che một phần trong ảnh chụp.
- Joystick vẽ bằng hình bo góc tự tạo, hiển thị hơi đặc hơn dự kiến; cảm giác chạm thật chưa
  kiểm chứng trên thiết bị.
- Atlas `big/11.png, 21–25.png` trong pack là các file rất nhỏ (577–628 byte, header ghi
  cùng kích thước với atlas 1/2) — được copy nguyên bản cho đủ bộ nhưng game không nạp
  (image_id trong dữ liệu chỉ trỏ 0–4).
- Số liệu HUD (Lv.50, 12.500 Xu) là **mẫu** — tài khoản/tiền tệ thật thuộc đợt 2/5.
- Âm thanh chưa có (gốc có nhạc/SFX, port ở các đợt sau). iOS là stub (cần Mac + Xcode).
  APK Android chưa build kiểm ở đợt 1 này; bản đã kiểm chứng là desktop.

## Các đợt tiếp theo (theo GOAL.md của dự án)

- Đợt 2 — Tài khoản + online: đăng ký/đăng nhập, chọn server, nối server Java gốc,
  nhiều client thấy nhau, di chuyển đồng bộ, chat thật.
- Đợt 3 — Farm. Đợt 4 — Nhà + nội thất.
- Đợt 5 — Shop / thời trang / túi đồ / tiền tệ / ATM / giftcode.
- Đợt 6 — Mini-game: vòng quay, Bầu Cua, Tài Xỉu, Phỏm, Tiến Lên, kim cương, đua, câu cá.
- Đợt 7 — Clan, kết hôn, rương, nâng cấp vật phẩm, sự kiện; đóng gói APK kiểm thử thật.
