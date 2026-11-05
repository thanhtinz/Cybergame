# Đặc tả Tiến Lên (casino gameType 3) — đào 2026-11-04

Nguồn: client-src VÀNG — TienLenMsgHandler.cs, TLBoardScr.cs,
CardUtils.cs, Card.cs, CasinoService.cs, HDPaint.cs. Server fan chỉ
có stub rỗng → engine là [PORT] viết mới theo giao thức client gốc,
dùng chung khung lobby casino với Bầu Cua (docs/baucua-spec.md).

## Họ casino

gameType: **3 = Tiến Lên** (TienLenMsgHandler), 7 = Phỏm, 21 =
Diamond, 22 = Bầu Cua. KHÔNG có "Bài Cào" trong gốc (đã grep cả
string literals 3.4.4). Bàn Tiến Lên: **4 ghế** (BoardScr.numPlayer
= 4, STYLE_4PLAYER). Phòng của bản port: 46, 47, 48 [PORT]; bàn đầu
id 30–35 [PORT].

## Bài

- cardID 0..51; value = id/4 (0 = "3" … 11 = "A", 12 = "Heo"/2);
  suit = id%4; màu: suit >= 2 đỏ, còn lại đen. So sánh trong cùng tổ
  hợp theo cardID (value trước, suit sau).
- Ảnh: `hd/card/<cardID>.png` 144×194 + `card/down.png` (mặt úp).
  Paint của gốc vẽ qua cardMapping — Tiến Lên là mapping thẳng
  (ảnh = cardID); Phỏm mới dùng mapping xoay.
- Tổ hợp (CardUtils.getType): 0 = đơn · 1 = sảnh (≥3 lá liên tiếp,
  không chứa Heo) · 2 = đôi · 3 = ba · 4 = 3 đôi thông · 5 = 4 đôi
  thông · 6 = tứ quý · −1 = không hợp lệ.
- Chặt (CardUtils.available — kèm tiền phạt theo mức bàn `money`):
  - Đơn Heo (suit < 2 đen): bị 3 đôi thông / 4 đôi thông / tứ quý
    chặt → phạt money/2; Heo đỏ (suit >= 2) → phạt money.
  - Đôi Heo: bị tứ quý / 4 đôi thông chặt → cả đôi đen: money; cả
    đôi đỏ: 2×money; lẫn màu: 1,5×money.
  - 3 đôi thông bị tổ hợp cùng loại lớn hơn / tứ quý / 4 đôi thông
    chặt → phạt money.
  - Tứ quý bị tứ quý lớn hơn / 4 đôi thông chặt → phạt 1,5×money.
  - 4 đôi thông bị 4 đôi thông lớn hơn chặt → phạt 2×money.
  Phạt là chuyển tiền người bị chặt → người chặt, cộng vào quyết
  toán khi một người về đích (net theo từng ghế).

## Gói tin (tiền tố chung của handler con: {byte room, byte board})

- S→C **20 START** {byte interval (giây/lượt), 13 × byte cardID
  (bài của người nhận), int whoMoveFirst}. Client nhận xong TỰ gửi
  53 forceFinish — server phải tự kiểm tra tới trắng, sai thì bỏ
  qua lặng lẽ.
- C→S **21 MOVE** {room, board, byte n, n × byte cardID}; S→C 21
  {int whoMove, byte n, cards, int nextMove}.
- C→S **49 SKIP** {room, board}; S→C 49 {int whoSkip, int
  nextMove, bool isClearCurrentCards} — true ở lượt bỏ hoàn tất
  vòng (mọi người bỏ qua bài trên bàn) → dọn bàn, người kế tiếp dẫn
  vòng mới.
- S→C **51 FINISH** {int whoFinish, byte finishPosition (**0-based**,
  client hiện "về thứ position+1"), int dMoney, int dExp} — gửi
  ngay khi người đó hết bài; người cuối cùng còn bài tự về chót.
- S→C **50 STOP** dừng ván; còn byte thì {int whoShow, byte n,
  n × byte cardID} → lộ bài người đó (ván huỷ giữa chừng).
- C→S **53 FORCE_FINISH** {room, board}; S→C 53 {int who, 13 ×
  byte cardID} khi tới trắng thật.
- S→C **54 MOVE_ERROR** {UTF info} — nước đi không hợp lệ, lượt
  giữ nguyên.

## Tới trắng (CardUtils.checkForceFinish, bài đã sắp tăng dần)

1. 4 lá Heo (4 lá cuối đều value 12).
2. 4 lá 3 (4 lá đầu value 0) — CHỈ khi newgame (ván đầu của bàn).
3. Sảnh 12 lá liên tiếp (không Heo).
4. 6 đôi.
5. 5 đôi thông (demDoiThong >= 5).

## Luật bàn [PORT — phần server gốc không còn]

- Nước đầu ván đầu của bàn: người giữ lá 0 (3 bích) đi trước và
  nước đầu PHẢI có lá 0 (client gốc có forceMove3Bich). Ván sau:
  người thắng ván trước đi trước.
- Bắt đầu: chủ bàn bấm START (20 C→S như Bầu Cua) khi ≥ 2 người
  ngồi và mọi người ngồi đều sẵn sàng; mức bàn ≥ 1.000... (mức do
  chủ bàn đặt bằng SET_MONEY; điều kiện đặt cho bàn Tiến Lên: xu
  ≥ 8 × mức [PORT], thay quy tắc ×48 của Bầu Cua).
- interval = 30 giây/lượt. Hết giờ: đang dẫn → tự đánh lá đơn nhỏ
  nhất; đang theo → tự bỏ lượt.
- Quyết toán zero-sum: delta vị trí = (N − 1 − 2×pos) × mức bàn
  (N = số người chơi ván đó), cộng net phạt chặt; trừ không quá số
  dư hiện có. dExp = (N − pos) × 2.
- Rời bàn / mất kết nối giữa ván → huỷ ván: phát 50 STOP kèm bài
  còn lại của người rời, không quyết toán.
- Vào bàn giữa ván: xem (không có bài), chờ ván sau.

## Client [PORT bố cục glass như Bầu Cua]

Điểm vào: nút "Tiến Lên · Casino" ở màn chọn Mini game. Lobby dùng
lại màn phòng/bàn của Bầu Cua (tham số gameType). Màn bàn: 4 ghế
quanh bàn (mình dưới–giữa), bài mình xếp quạt dưới đáy (chạm để
chọn/bỏ chọn), bài trên bàn ở giữa, nút Đánh / Bỏ lượt (khoá khi
đang dẫn vòng), đồng hồ lượt, sẵn sàng / bắt đầu / đặt mức như Bầu
Cua. Offline: bàn tập với 3 máy (AI đánh tổ hợp hợp lệ nhỏ nhất
hoặc bỏ lượt) để bản web chơi thử được.
