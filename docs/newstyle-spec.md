# Hệ nhân vật PHONG CÁCH MỚI (newstyle) — spec

Asset do user gửi 2026-10-31 (5 ảnh): 1 cặp nhân vật mẫu, sheet 10 tóc nam,
10 tóc nữ, 20 trang phục nam, 20 trang phục nữ. Đây là hệ nhân vật THÊM
song song với paper-doll gốc (không thay thế, không đụng dữ liệu gốc).

## Kho asset (assets/newstyle/)

- `hero/hero_m.png`, `hero/hero_f.png` — cặp mẫu nguyên khối (có alpha thật).
- `hair_m/hair_m_01..10.png`, `hair_f/hair_f_01..10.png` — tóc (kèm tai +
  phụ kiện: nơ, kẹp, trâm).
- `outfit_m/outfit_m_01..20.png`, `outfit_f/outfit_f_01..20.png` — trang
  phục nguyên thân (áo + quần/váy + giày, kèm cổ áo da).
- `base/base_m.png`, `base_f.png` — đầu base không tóc (xem mục dựng base).
- `assets/data/newstyle.json` — danh mục + khối hiệu chuẩn `male`/`female`.

Nguồn: 4 sheet không có alpha thật (nền ca-rô vẽ chết trong ảnh, hai tông
gần trắng 247/254). `tools/slice_newstyle.py`: tẩy nền bằng flood-fill từ
biên qua vùng sáng trung tính (màu trắng TRONG trang phục được giữ nhờ
viền đậm bao quanh), cắt theo thành phần liên thông; sheet trang phục các
món chạm nhau thành khối nên cắt theo lưới đều tại cực tiểu hình chiếu
alpha rồi lọc mảnh lạ. Chạy lại được, ghi đè đúng tên file.

## Đầu base (tools/build_base.py)

Không "xoá tóc" khỏi hero (lũ quét ăn mất con ngươi — mi mắt nối liền mái).
Cách chốt: vòm đầu ellipse điền da phẳng (tông lấy từ má hero, tối dần về
viền + viền da đậm), chỉ GHÉP từ hero: dải mắt (cả hai mắt + mi + tròng,
mờ ria 12px), ô miệng, má hồng vẽ mềm. Tâm mặt đo từ cụm con ngươi của
hero: nam (344, ~426), nữ (350, ~394) trong canvas hero — lệch phải so với
tâm canvas vì tóc hero xoè lệch trái. Bán kính vòm: nam 184, nữ 140 (=
bề ngang mặt + ~14px mỗi bên để mép vòm nấp dưới khối tóc; vòm to hơn sẽ
lộ "quầng hói" quanh tóc).

## Phối hình (tools/composite_test.py = bản đo; game: ui/NewStyle.java)

Không gian phối = canvas hero (nam 600×815, nữ 608×784). Thứ tự lớp:
outfit → base → tóc. Hiệu chuẩn trong newstyle.json:

| | kHair | kOutfit | bodyCx | faceCx | hairFrac | hairDy |
|---|---|---|---|---|---|---|
| nam | 600/369 ≈ 1.626 | = kHair | 305 | 344 | 344/600 | −28 |
| nữ | 588/357 ≈ 1.647 | 1.15 | 304 | 350 | 350/588 | −32 |

- outfit: đáy chạm đất (đáy canvas), tâm ngang = bodyCx.
- tóc: tâm lỗ mặt của tóc (frac × bề rộng đã phóng) trùng faceCx; dy dời
  lên để đường mái của sheet (dài hơn mái hero) không che mắt — chỉnh
  bằng lưới test dy −10..−44, chốt theo mắt.
- nữ kOutfit nhỏ hơn hẳn kHair vì sheet đồ nữ vẽ thân dài/chân dài hơn
  tỉ lệ heroine; ép chung k sẽ quá cao.

Kiểm chứng: sheet phối 20 tổ hợp (mỗi giới: 5 tóc × đồ cố định + 4 đồ ×
tóc cố định + hero đối chứng) — tóc không lộ vòm, mắt mở đủ, cổ khớp.

## Trong game

- `NewStyle.compose(gender, hairId, outfitId)` phối bằng Pixmap một lần,
  cache Texture; `NewStyle.hero(gender)` nạp hình mẫu.
- WorldScreen: `StyleFigure` vẽ tĩnh + nhịp thở sin 1.6px, trộn độ sâu
  cùng DrawItem như NPC; chiều cao vẽ STYLE_H = 76px hd (≈ chiều cao
  paper-doll; bản 150px đầu tiên bị lố — người khổng lồ so với Sơn).
- Gian trưng bày ở map 10: Người Mẫu Nam (672,114) + Người Mẫu Nữ
  (722,120) trước Pet Racing; Khách Nam = hair_m_08 × outfit_m_03
  (538,104); Khách Nữ = hair_f_10 × outfit_f_15 (592,110). Tọa độ medium
  cùng hệ NPC. Hai vị trí đã loại: sau biển 835 (bị che ngang thân) và
  trước biển (lọt vùng HUD chat/joystick).
- Sân khấu chụp: ScreenshotMain stage 25 → shot-newstyle.png (khẳng định
  styleCount == 4).

## Giới hạn đã biết

- Asset chỉ có MẶT TRƯỚC, một tư thế: nhân vật phong cách mới hiện chỉ
  đứng trưng bày. Cho người chơi dùng cần: hiệu ứng đi (nhún + lật
  ngang), hướng lên/xuống dùng chung mặt trước, và UI tủ đồ — thuộc
  đợt 5 (Shop/thời trang) hoặc một đợt riêng khi user chốt.
- Tóc/trang phục phối tự do theo giới; chưa có dữ liệu giá/tên món —
  bổ sung khi làm shop.

## Bản CRISP — khớp ngôn ngữ pixel của game (user yêu cầu 2026-10-31)

Asset nguồn là minh hoạ AI: gradient mềm, chống răng cưa — đứng cạnh
sprite gốc (pixel cứng, tông phẳng) là lệch hẳn. `tools/crisp_newstyle.py`
sinh bản `assets/newstyle-crisp/` (game CHỈ nạp bản này):

- Hạ cỡ box-average trên RGBA premultiply về canvas cao 152px (= 2× cỡ vẽ
  76 trong game); mọi lớp được phóng sẵn về CỠ PHỐI CUỐI (tóc ×kHair×s,
  đồ ×kOutfit×s) để Java chỉ blit 1:1, không nội suy thêm.
- Alpha snap nhị phân (≥128), palette tối đa 56 màu/món không dither —
  tông phẳng kiểu cel như asset gốc; texture khoá filter Nearest.
- Mắt/miệng của base quá nhỏ nên box-average nuốt nét: cấy lại dải mắt +
  ô miệng từ hero CRISP (cùng canvas, giữ được nét) với khớp tông da
  (chỉ dịch pixel da sáng, giữ nguyên pixel tối) + mặt nạ lông vũ 2px.
- Hiệu chuẩn crisp ghi ở khối `maleCrisp`/`femaleCrisp` trong
  newstyle.json (k giữ nguyên, hình học ×s). Bản full-res giữ nguyên làm
  nguồn; phối kiểm chứng ở cỡ full-res vẫn qua composite_test.py.
