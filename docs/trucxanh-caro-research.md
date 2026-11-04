# Trúc Xanh (id 1) + Cờ Caro (id 3) — nghiên cứu từ bản chính 3.4.4

Ngày đào: 2026-11-03, từ `deep/metadata/all_types_full.json` (chỉ có tên
field/method — luật chi tiết suy luận từ cấu trúc, như aincrad-research.md).
Cả hai chạy trên khung hub đã dựng (phong bì −113 phía port, MiniGameScr:
ghế/lượt/đếm giờ/sẵn sàng/khán giả/hộp quà `idBoxGift`/lịch sử thắng
`winHistory` — hai mục cuối bản port CHƯA làm).

## Trúc Xanh — game lật thẻ nhớ cặp (memory match)

Bằng chứng: `TrucXanhGameScr` có `listCard`, `imgBackCard` (mặt úp),
`checkOpen`, `removeCard` (mở trúng cặp thì gỡ thẻ), `listOpenDelay`
(hàng chờ đóng thẻ sai), `pointMe/pointPlayer` + `pointToMe/pointToPlayer`
(điểm hai phía), lưới `numW × numH`, thẻ `wCard` + tỉ lệ `wCardScale/
hCardScale`, `percent`. `TrucXanhService`: `joinGame`, `sendCard`
(gửi thẻ chọn lật), `outGame`. `TrucXanhGameScr.setTurn`, `checkPoint`,
`applySnapshot` (khán giả xem qua snapshot như Tàu Chiến), `resetCard`.

Suy luận luật (khớp game "Trúc Xanh" quen thuộc):
- Bàn lưới thẻ úp; hai người thay phiên lật 2 thẻ; trùng cặp → ăn điểm
  + gỡ cặp (+ có thể lật tiếp — `checkPoint`/`pointTo*` gợi ý cộng điểm
  theo thẻ); hết thẻ → điểm cao thắng; thắng nhận hộp quà hub.
- Asset: `deep/textures/trucxanh.png` (đã nhập vào assets/minigame/);
  ảnh mặt thẻ/thẻ bài riêng CHƯA rõ (có thể dùng bộ bài `hd/card` hoặc
  ảnh linh thú — cần soi thêm khi dựng; imgCard là mảng ảnh thẻ).

## Cờ Caro — X/O trên bàn cuộn được

Bằng chứng: `CaroGameScr.configureBoardSize` (cỡ bàn cấu hình được —
không chốt 15×15), `listCellCaro` + `CellCaro` (`myMark`, `imgX/imgO`,
`imgF_X/imgF_O` = ô đang focus, `imgWinX/imgWinO` = ô thuộc hàng thắng,
`status`, `isWinCell`), camera cuộn (`cam`, `clampCamera`, cửa sổ hiện
`cShow/rShow` min/max), `CaroService.joinGame/sendMove/outGame`,
`onMove`, `onChangeTurn`, `detectWinDirection` (client tự dò hướng
thắng để tô hàng thắng), `applySnapshot`, `endGame(pendingWinner,
pendingReason, pendingGiftBox)`, `onKicked`.

Suy luận luật: caro 5 ô liên tiếp thắng (chuẩn VN — cần chốt khi dựng:
chặn 2 đầu hay không, cỡ bàn mặc định của server gốc). Asset:
`deep/textures/caro.png` (icon, đã nhập) + `BG_Caro2_0..8` trong
textassets_png (nền bàn 3×3 lát?) — soi khi dựng.

## Thứ tự dựng (sau đợt push hiện tại)

1. Caro trước (luật đơn giản nhất): thêm gameId 3 vào MiniGameService
   (phòng/lượt/snapshot theo khuôn Tàu Chiến), client vẽ bàn + X/O
   bằng asset gốc, dò thắng phía server (5 liên tiếp; mặc định luật
   [PORT]: không chặn 2 đầu, bàn 15×15 — ghi rõ chờ đối chiếu).
2. Trúc Xanh: gameId 1; server chia thẻ ngẫu nhiên (lưới [PORT] 6×5 =
   15 cặp nếu chưa chốt được numW/numH gốc), lật 2 thẻ/lượt, trúng ở
   lại + đi tiếp, sai úp lại sau delay; điểm = số cặp; hết thẻ phân
   thắng thua.
3. Bổ sung hub còn thiếu ở cả hai: hộp quà thắng (idBoxGift) + lịch
   sử thắng (winHistory) — làm một lần ở khung hub.
