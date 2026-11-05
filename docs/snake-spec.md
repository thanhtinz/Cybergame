# Rắn Thang (Snake Ladder) — bản chính 3.4.4 → port

Đính chính audit cũ: Snake KHÔNG phải rắn săn mồi offline. Bằng chứng
bản chính (metadata IL2CPP + string literals, đào 2026-11-05):

- `SnakeGameScr` có GAME_ID, setMap/setBoard/loadMapImage/
  requestMapImage/onMapImage, TURN_BAR/CLOCK/RING (đồng hồ lượt),
  TOP_CHAR/CHAR_X/CHAR_Y/NAME (nhân vật + tên trên bàn), SLOT (ô quân).
- Literals: "Snake Ladder", "Rắn thang", luật tiếng Việt nguyên văn:
  "Dẫm chân thang: leo lên đỉnh thang", "Dừng đúng ô có quân đối thủ
  (kể cả sau khi leo thang, tuột rắn): đá quân đó lùi về đầu rắn gần
  nhất phía sau rồi tuột xuống đuôi. Không có rắn phía sau thì về
  ô 1", "Land on a snake head: get swallowed and slide down to its
  tail", và chuỗi cấu hình map: "Sai số liệu. Dòng Map: tên hình (vd
  mapranthang2), thang / rắn mỗi cặp 2 ô 1..66 khác nhau"
  → bàn 66 ô, cặp thang/rắn do server cấu hình theo map.
- Game id 5 trong hub MiniGame (cùng khung 2 ghế + khán giả + BXH).
- Icon `Snake_1.png` (rắn xanh 3D) đã chép vào assets/minigame/snake.png
  — dùng làm icon chọn game ở hub.

## Luật port [PORT — số liệu bản chính không công bố]

- 2 người qua hub −113, ghế 0 tung trước; lượt 30 giây (dùng chung
  timer hub), bỏ lượt 3 lần liên tiếp bị xử thua (như Caro).
- Xúc xắc server Random 1–6. Ô đích = vị trí + xúc xắc; vượt 66 thì
  BẬT LẠI (66 − phần vượt) — biến thể phổ biến, bản chính không
  công bố luật vượt đích.
- Tới đúng ô 66 = thắng ngay.
- Cặp thang/rắn TĨNH của port:
  - Thang: 4-25, 9-16, 17-34, 28-46, 39-58
    (17-34 lấy từ ví dụ trong chuỗi cấu hình của bản chính).
  - Rắn: 15-5, 23-7, 27-11, 36-18, 49-30, 63-42.
- Đá đối thủ theo đúng literal bản chính: dừng đúng ô đối thủ (sau
  thang/rắn) → đối thủ về ĐẦU RẮN GẦN NHẤT PHÍA SAU ô đó rồi tuột
  xuống ĐUÔI rắn đó; không có đầu rắn phía sau → về ô 1.

## Giao thức [PORT] — phong bì −113, gameId 5

Sub: SN_START 1 {byte firstTurn} · SN_ROLL 2 (client→server, rỗng) ·
SN_ROLLED 3 {seat, dice, from, to, kind (0 thường · 1 thang · 2 rắn),
oppTo (−1 = không đá)} · SN_CHANGE_TURN 4 {turn} · SN_END 5 {winner,
reason (0 tới đích · 1 đối thủ thoát · 3 bỏ lượt)} · SN_SNAPSHOT 13
{int roomId, byte status, UTF name0, UTF name1, byte turn, byte pos0,
byte pos1, byte nSpec} · SN_SPEC_COUNT 14.

- Server: mở rộng `avatar.minigame.MiniGameService` (gamePlayable +
  dispatch + startSnake + timer bỏ lượt dùng chung nhánh Caro +
  snapshot/end riêng); kết quả ghi mg_stats game_id 5 (BXH hub có sẵn).
- Client: màn Rắn Thang theo khung sáng của bản chính (như Caro):
  bàn 66 ô zigzag 6 cột × 11 hàng (hàng dưới trái→phải là 1..6), ô
  chân thang ghi T xanh lá, đầu rắn ghi R đỏ, ô 66 ghi Đ; quân là vòng
  tròn đỏ/xanh theo ghế; cột phải có xúc xắc vẽ chấm, nút Tung xúc
  xắc, chú giải cặp thang/rắn; ẩn HUD theo luật mini game (snOpen).

## Kiểm chứng

- Test sống `live_snake_test.py` ALL PASS: test tự tính lại đích đến
  (bật lại/thang/rắn/đá) đối chiếu từng gói ROLLED của cả hai máy;
  ván mẫu 23 lượt (4 thang, 1 rắn, 2 bật lại), END nhất quán, mg_stats
  game 5 ghi đủ 2 người. Sự kiện ĐÁ đối thủ chưa xuất hiện trong các
  ván mẫu (công thức đã đối chiếu ở mọi lượt, chưa có mẫu đá thật).
- Kiểm mắt: stage 146 (giữa ván) + 147 (kết quả) của tour chụp.
