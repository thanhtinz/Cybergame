# Phỏm (Tá lả) — Đặc tả port (casino gameType 7)

Nguồn: client-src VÀNG — PhomMsgHandler.cs, PhomService.cs,
PBoardScr.cs (2.775 dòng), Card.cs. Server fan chỉ có stub rỗng
(PhomMsgHandler.java 4 dòng) → engine là [PORT] viết mới theo giao
thức + luật client gốc, dùng chung khung lobby Casino với Bầu Cua /
Tiến Lên (docs/baucua-spec.md, docs/tienlen-spec.md).

## Bài và phỏm (theo Card.cs + PBoardScr.checkPhom*)

- cardID 0..51 RIÊNG cho Phỏm: **value = id/4** với 0=A, 1=2, 2=3,
  ..., 9=10, 10=J, 11=Q, 12=K; **suit = id%4**. Điểm rác = value + 1
  (A=1 … K=13) — đúng `array[i] / 4 + 1` của client gốc.
- Ảnh hiển thị: client gốc dùng cardMapping xoay
  {11, 12, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10} — ảnh file là bộ bài
  Tiến Lên (0="3" … 11="A", 12="2") nên ảnh của lá Phỏm c là
  `card/<mapping[c/4]*4 + c%4>.png`.
- Phỏm hợp lệ (trên dãy đã sắp tăng dần, đúng checkPhomSanh/BaLa):
  - **Sảnh**: ≥3 lá liên tiếp value, cùng suit. A chỉ đứng thấp
    (0,1,2 hợp lệ; Q,K,A = 11,12,0 KHÔNG hợp lệ — gốc kiểm liên tiếp
    thuần, không vòng).
  - **Ba lá**: ≥3 lá cùng value.
- Bài luôn giữ sắp tăng dần theo cardID.

## Luồng ván (theo PBoardScr)

- 4 ghế, chia mỗi người **9 lá**, nọc còn **16 lá**. Người đi trước
  cũng bắt đầu 9 lá: lượt = (Ăn lá người trước vừa đánh | Bốc nọc)
  → Đánh 1 lá. Mỗi người đánh đúng **4 lượt** (cardShow 4 cột).
- Lượt cuối (đã đánh 3): sau Ăn/Bốc phải **Hạ phỏm** (65) trước khi
  đánh lá cuối. Hạ sai → server trả isHa=false, client báo
  "phỏm không khớp" và cho hạ lại.
- **Ù**: sau Ăn/Bốc, nếu 10 lá trên tay phân hoạch trọn thành 3 phỏm
  (không còn rác) → Ù, ván kết thúc ngay.
  - Ù do **Bốc** (hoặc ăn lá không phải chốt): ù thường.
  - Ù do **Ăn lá chốt** (lá do người trước đánh ở lượt cuối của họ,
    fireCount == 3): người đánh lá đó **Đền ù** — trả thay cả bàn.
- **Gửi bài** (67): [PORT đơn giản hoá] sau khi người cuối cùng đánh
  lá cuối, server TỰ gửi: lá rác nào của mỗi người gắn được vào một
  phỏm đã hạ trên bàn (của bất kỳ ai) thì gắn — luôn có lợi (giảm
  điểm rác), không cần thao tác. Client gốc cho gửi tay qua gói 67;
  server vẫn nhận 67 (gắn theo yêu cầu) trong pha hạ của chính
  người gửi vào phỏm đã hạ của mình — không bắt buộc dùng.
- **Cháy (móm)**: hết ván mà một người không có phỏm nào (ăn + hạ
  đều 0) → score = −2, trả gấp đôi.
- Tính điểm: điểm = tổng điểm rác còn trên tay (sau gửi). Người
  điểm thấp nhất thắng; hoà điểm → người hạ trước thắng (thứ tự hạ),
  rồi tới ghế nhỏ hơn.
- Tiền (zero-sum, mức bàn = money):
  - Thường: mỗi người thua trả thắng (điểm của mình × mức); cháy
    trả ×2. Người thắng nhận tổng.
  - Ù thường: mỗi người thua trả thắng (10 + điểm rác hiện tại của
    mình) × mức.
  - Ù đền: người đền trả thắng tổng 3 phần của cả bàn theo công
    thức ù; hai người còn lại không mất.
- Hết giờ 30s/lượt: chưa Ăn/Bốc → Bỏ lượt hẳn (gói 49, người kế
  vẫn ăn được lá trên bàn); đã Ăn/Bốc mà chưa đánh → tự đánh lá rác
  nhỏ nhất; pha Hạ → tự hạ theo phân hoạch tốt nhất rồi đánh.

## Giao thức (tiền tố {byte room, byte board} mọi gói con)

C→S:
- 20 START (chủ bàn, qua khung casino chung)
- 21 FIRE {byte cardID}
- 49 SKIP — client gốc không gửi; server tự bỏ lượt khi hết giờ.
- 63 GET_CARD {} — bốc nọc
- 64 EAT {n byte cardID trên tay tạo phỏm, byte index} — index là vị
  trí lá ăn khi chèn vào nhóm đã sắp (client gốc); server chỉ cần
  danh sách lá trên tay, bỏ qua index.
- 65 HA_PHOM {các nhóm lá cách nhau bằng byte −1}
- 67 ADD_CARD {các byte cardID} — gửi tay (tuỳ chọn)
- 68 RESET_EAT — [PORT] không dùng: Ăn được kiểm nguyên tử phía
  server, sai trả 64 isEat=false ngay.

S→C:
- 20 START {byte interval=30, 9 × byte cardID, int firstMove,
  int firHa}
- 21 FIRE {int nextPlayer, int firer, byte cardID (−1 = nước sai,
  lượt giữ nguyên), byte numberCard = chỉ số lá đó trong chồng bài
  đã đánh của người đánh (0-based)}
- 49 SKIP {int nextPlayer, int firHa}
- 62 PLAYING — [PORT] không gửi snapshot: vào giữa ván thì ngồi xem,
  chờ ván sau (như Tiến Lên).
- 63 GET_CARD {byte cardID} — CHỈ gửi người bốc.
- 64 EAT {int firHa, bool isEat, [byte numEat = số lá trên tay đã
  dùng], byte nCard = số lá còn trong chồng của người bị ăn}
- 65 HA_PHOM {int curID, bool isHa, bool isU, [khi isHa: 4 × int
  dMoney (chỉ khác 0 khi isU), các byte lá đã hạ, nhóm cách nhau −1]}
- 67 ADD_CARD {bool isAdd, byte cardID} — trả từng lá gửi tay.
- 69 DEN_BAI {int winID (IDDB người ù), 4 × int dMoney theo ghế}
- 70 ONCE_WIN {int totalWin} — CHỈ gửi người ù thường.
- 51 FINISH {4 × int score theo ghế (−2 cháy, −1 = người ù thường),
  4 × int dMoney theo ghế, rồi bài rác của 4 ghế, mỗi ghế cách nhau
  bằng byte −1} — khớp trình tự đọc của PhomMsgHandler gốc (score
  array5 đọc sau money array6 trong handler nhưng onFinish nhận
  (score, cardRac, money) — xem code engine để biết thứ tự ghi thật:
  engine ghi score trước hay money trước phải khớp handler: handler
  đọc 4 int đầu = array5 → truyền làm THAM SỐ THỨ BA (moneyPlayers)
  cho onFinish... thực tế handler đọc array5 (4 int), array6 (4 int)
  rồi gọi onFinish(array6, array7, array5) — tức 4 int ĐẦU trên dây
  là dMoney, 4 int SAU là score. Engine ghi theo đúng thứ tự dây:
  dMoney[4], score[4], rác.)

## [PORT] phía server

- Phòng 49, 50, 51; bàn đầu id 40–45 (4 ghế). SET_MONEY cần
  xu ≥ 8 × mức (như Tiến Lên).
- Bắt đầu: chủ bàn gửi 20 khi ≥2 người ngồi, mọi người ngồi (trừ
  chủ bàn) sẵn sàng, mức > 0.
- Người đi trước: ván đầu = ghế có lá 0 (A bích — cardID 0 luôn
  được chia vào ván như lá 0 của Tiến Lên: chia đảm bảo 1 ghế nhận
  lá 0 + 8 lá khác); ván sau = người thắng ván trước.
- Rời bàn/mất kết nối giữa ván → huỷ ván (gói 50 STOP của khung
  casino — client Phỏm gốc không có case 50; client port tự xử lý
  như kết thúc không quyết toán), không quyết toán.
