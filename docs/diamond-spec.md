# DIAMOND (Kim Cương) — casino gameType 21 [PORT theo Pack5]

Nguồn gốc: client Pack5 có đủ thân hàm `DiamondScr.cs` (1437 dòng) +
`DiamondMessageHandler.cs`; server fan chỉ có stub rỗng nên engine
viết mới theo đúng giao thức client gốc. Bản chính 3.4.4 vẫn còn
DiamondScr + tên "Kim Cương"/"Diamond" trong literals. Diamond là
game casino thứ 4 (TE.cs: nameCasino = Tien Len · Phom · Diamond ·
Bầu cua) — điểm vào bản port: thẻ thứ 4 ở Khu vực 1 của hub.

## Luật (gốc)

- Bàn 2 người, bàn cờ 8x8 CHUNG, 6 loại kim cương (itemID 0..5).
- Mỗi người có HP / MP / defence (khiên) / fight (công).
- Lượt: chọn 1 ô, vuốt/chạm ô kề để hoán đổi; hoán đổi phải tạo
  hàng ngang/dọc >= 3 cùng loại mới được tính; không tạo thì trả lại.
- Xoá hàng -> trọng lực + sinh ô mới -> dây chuyền (combo tăng).
- Hết giờ lượt -> tự Skip. Bàn hết nước đi hợp lệ -> phát bàn mới
  (Out Path), giữ nguyên lượt.
- Thắng khi HP đối thủ <= 0; tiền zero-sum theo mức bàn (51 FINISH).

## Asset (gốc, từ res server object/<id>.png — kênh GET_IMG_ICON)

- `assets/diamond/gems.png` = object 1027 (72x432): dải 6 frame
  72x72, thứ tự trên xuống = loại 0..5: 0 = vàng chữ X đỏ (công) ·
  1 = kim cương trắng khiên cam (phòng thủ) · 2 = tim hồng (HP) ·
  3 = lục giác xanh sét (MP) · 4 = cầu xoáy xanh lá (bom) ·
  5 = ô tím "?" (ngẫu nhiên).
- effect.png = 877 (nổ khi xoá) · select.png = 878 (khung chọn,
  frame dưới viền đỏ) · shield.png = 880 (icon khiên) · ko1/ko2.png
  = 881/882 (icon kiệt sức trên đầu doll, 48x48 frame).
- Nền bàn: ca-rô 2 màu gốc (5197647 / 2697513); doll 2 người đứng
  2 bên bàn, thanh HP đỏ + MP xanh dương + icon khiên + số defence.

## Giao thức (phong bì casino, sau 2 byte room/board)

- 61 {byte 21} chọn game (server chấp nhận thêm 21).
- 20 START (S->C): {byte interval=30, int whoMoveFirst (userId),
  64 byte bàn (hàng x cột, giá trị = loại), x2 người theo ghế:
  int id, short defence, short hp, short mp, short maxMP}
  (hp khởi đầu = maxHP ngụ ý 100).
- 21 MOVE (C->S): {byte iSelected, byte selected} (vị trí = hàng*8+cột).
  S->C phát lại cho cả bàn: {int whoMove, byte iSelected, byte selected}.
- 64 CREATE_CELL (S->C, mỗi đợt dây chuyền một gói): {byte nClear;
  nClear x {byte pos, byte anchor, byte depth = loại mới};
  byte countCombo; byte nText; nText x UTF (text bay, để trống 0);
  x2 người theo ghế: int id, byte fight, short defence, short hp,
  short mp, boolean isNo}.
  anchor/depth theo gốc: ô mới rơi từ anchor (0..7 = từ trên xuống
  theo hàng) — bản port: anchor = số ô mới phía trên trong cùng cột
  tại thời điểm rơi (chỉ để client tính hoạt ảnh), depth = loại mới.
- 49 SKIP (C->S gói rỗng hoặc {room, board}) -> S->C {int whoMove}
  = người được chuyển lượt.
- 24 OUT_PATH (S->C khi hết nước đi): {int whoMove, 64 byte bàn mới}.
- 51 FINISH (S->C): x2 ghế {int id, int delta}; delta > 0 = thắng.
- 71 DATA (S->C cho người vào giữa ván): {64 byte bàn} + trạng thái
  2 người giống đuôi gói 20 [mở rộng PORT cho người xem].

## Chỉ số chiến đấu [PORT — server gốc không còn]

- Bắt đầu: HP 100/100, MP 40/100, defence 0. Đầu lượt +2 MP.
- Mỗi viên xoá được: loại 0 (công) +6 ATK · loại 1 (khiên) +3
  defence (trần 30) · loại 2 (tim) +4 HP · loại 3 (sét) +5 MP ·
  loại 4 (bom) +3 ATK và NỔ: xoá thêm 4 ô kề trực giao (tính dây
  chuyền chung đợt) · loại 5 ("?") +4 vào 1 chỉ số ngẫu nhiên.
- ATK nhân hệ số combo của đợt (đợt 1 x1, đợt 2 x2...).
- Tấn công: ATK >= 5 và MP >= 10 -> đánh: khiên đối thủ hấp thụ
  trước (defence giảm), phần dư trừ HP; tốn 10 MP. Thiếu MP ->
  isNo (kiệt sức), ATK mất. fight trong gói 64 = ATK đã dùng.
- Quá 80 lượt tổng mà chưa phân thắng: so %HP, cao hơn thắng,
  bằng = hoà (delta 0).
- Tiền: mức bàn x1 zero-sum như Tiến Lên (thắng +money, thua
  -money), cộng/trừ qua updateXu lúc FINISH; SET_MONEY cần dự
  trữ x8 như TL/Phỏm.
- Hẹn giờ lượt 30s (interval gửi ở gói 20); hết giờ server tự
  skip. Rời bàn giữa ván = thua (đối thủ thắng đủ tiền) — theo
  mẫu onLeave của Phỏm/TL; chủ bàn rời cũng vậy (khác Bầu Cua).

## Server

- `avatar.minigame.DiamondState` (trạng thái trong BoardInfo.diamond)
  + `avatar.minigame.DiamondEngine` (start/move/skip/onLeave/
  timeout, bàn cờ + giải dây chuyền + chỉ số).
- BoardManager: bàn tĩnh gameType 21 id 50-55, seatCap 2;
  createBoard(21) seatCap 2. Phòng 52-54 [PORT].
- CasinoMsgHandler: joinCasino nhận 21; start/toXong(21)/skip
  rẽ sang DiamondEngine; SET_MONEY dự trữ x8 cho 21; boardList
  playing = diamond.playing.

## Client

- csGame = 21: tên "Kim Cương"; thẻ thứ 4 Khu vực 1 hub (icon =
  frame 1 của gems.png — viên kim cương trắng).
- drawDiamondBoard: panel casino chung; 2 doll 2 bên với HP/MP/
  khiên; bàn 8x8 ca-rô ở giữa (ô 46px); chọn ô -> chạm ô kề để
  đổi; hoạt ảnh đổi chỗ/xoá (effect.png)/rơi theo gói 64; thanh
  đếm giờ lượt; nút Bỏ lượt (gửi 49) khi tới lượt mình.
- Gói: 20/21/24/49/51/64/71 rẽ ở isCasinoCmd + handleCasino.
