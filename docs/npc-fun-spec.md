# NPC "sống" — tính năng TẠO MỚI cho 12 NPC chỉ đứng + chat ở bản gốc

Lệnh Tín 2026-11-07 ("Gắn mấy npc vào với sửa lại cho đẹp"). Kiểm kê
docs/npc-wiring-audit.md: 12 NPC sau KHÔNG có tính năng nào trong cả
bản gốc lẫn server fan (chỉ đứng + chat): 465 Vé Số, 645 Cô giáo tiếng
Anh, 646 Cô giáo tiếng Việt, 50 Tiên Cá, 315 Giám Thị, 335 Phi Hành
Gia, 425 Phù Thủy Bóng Tối, 540 Bậc Thầy Phù Thủy, 590 Pretty Witch,
535 Zombie, 741 Ăn Xin, 760 Pharaoh. Đợt này TẠO MỚI tính năng cho cả
12 (đánh dấu [CREATE] — không phải port), theo chủ đề từng NPC, dùng
lại khung sẵn có: menu NPC gốc (−61/−59/−60) + 1 gói mới + 2 panel.

## Giao thức — Cmd.NPCFUN = −126 (handler Avatar)

Client → server:
- 0 LOTTERY_STATE: xin trạng thái xổ số (mở panel)
- 1 LOTTERY_BUY {byte number 0..99}: mua 1 vé
- 3 QUIZ_ANSWER {byte idx}: trả lời câu hiện tại của phiên quiz NPC
- 4 QUIZ_QUIT: huỷ phiên quiz đang chạy

Server → client:
- 10 LOTTERY {UTF lastText, byte n, n×{byte number}, byte buysLeft,
  int price, int claimedXu}
- 11 QUIZ_Q {short npcId, byte pos, byte total, UTF question,
  4×UTF option, short seconds}
- 12 QUIZ_END {UTF summary}
- 13 MSG {boolean ok, UTF text} — kết quả mua vé (panel xổ số hiện
  dòng này; panel đóng thì vào tab Hệ thống)

## Xổ số (NPC 465 Vé Số)

- Vé 5.000 xu, tối đa 5 vé/ngày/người, mỗi vé 1 số 00–99 không trùng.
- Quay lúc 20:00 giờ VN mỗi ngày: vé mua trước 20:00 thuộc kỳ hôm
  nay, sau 20:00 thuộc kỳ mai. Số trúng của 1 ngày xác định bằng
  Random gieo hạt từ chuỗi ngày (ổn định qua restart, không cần cron).
- Trúng: khớp cả 2 số = 200.000 xu; khớp chữ số cuối = 25.000 xu.
- Nhận thưởng lười: mở panel/mua vé là server tự quyết toán mọi vé
  quá kỳ chưa quyết toán của chính người chơi (claimedXu trong STATE).

## Quiz phiên (645, 646, 315, 760) — panel riêng, không phát loa khu

| NPC | Ngân hàng | Số câu | Giây/câu | Xu/câu | Thưởng tuyệt đối | Lượt/ngày |
|---|---|---|---|---|---|---|
| 645 Cô giáo tiếng Anh | EN (12 câu) | 5 | 30 | 400 | +2.000 | 1 |
| 646 Cô giáo tiếng Việt | VN (12 câu) | 5 | 30 | 400 | +2.000 | 1 |
| 315 Giám Thị | Trộn EN+VN+tổng hợp | 8 | 15 | 600 | +5.000 | 1 |
| 760 Pharaoh | Câu đố dân gian (10) | 3 | 45 | 1.500 | — | 1 |

- Trả lời đúng cộng điểm danh hiệu quiz_ok (TitleService.bump).
- Quá giờ trả lời = kết thúc phiên, trả phần đã đạt.
- Lượt tính khi BẮT ĐẦU phiên (bảng npc_fun_daily).

## Tính năng qua menu (dialog kết quả, giới hạn ngày ở npc_fun_daily)

- 50 Tiên Cá — "Nhận phước biển" 1/ngày: 70% 800–2.500 xu ·
  20% 3.000–6.000 xu · 10% 1–3 lượng.
- 335 Phi Hành Gia — "Phóng tàu": lượt đầu/ngày miễn phí, thêm
  10.000 xu/lượt, tối đa 6 lượt/ngày: 30% lạc (0) · 40% 4.000–20.000
  xu · 20% 1–4 lượng · 10% Đá ngũ sắc (3672) ×1.
- 540 Bậc Thầy Phù Thủy — "Xem bói" 1/ngày: Đại cát 15% = 2 lượng ·
  Cát 35% = 1.500–3.000 xu · Bình 35% = 300–800 xu · Hung 15% = 0.
- 425 Phù Thủy Bóng Tối — "Luyện dược đen" 3.000 xu, tối đa 3/ngày:
  35% hỏng · 30% 2.000–7.000 xu · 20% 1–3 lượng · 15% 1 món đồ
  ma quái (pool 10 món, hạn 15 ngày).
- 590 Pretty Witch — "Đổi quà ma thuật" 20.000 xu, tối đa 2/ngày:
  1 món đồ theo giới (pool phù thủy/xinh, lọc gender; 85% hạn 7
  ngày, 15% vĩnh viễn).
- 535 Zombie — "Cho Zombie ăn": menu con liệt kê tối đa 4 món ĐÃ
  NẤU người chơi đang có (lấy qua Farm2Service); ăn 1 món, tối đa
  3/ngày: 50% 1.000–4.000 xu · 25% 1–2 lượng · 25% ói ra (0).
- 741 Ăn Xin — "Cho tiền" 1.000/5.000/20.000 xu → +1/+3/+8 điểm
  Phúc (lưu bền ở npc_state), tối đa 10 lượt cho/ngày; mỗi lần cho:
  20% được trả gấp đôi, 10% được tặng 1 lượng. Đổi 20 Phúc = 5 lượng.

## Bảng dữ liệu

- npc_fun_daily (user_id, npc_id, day, cnt) — day theo giờ VN.
- npc_state (user_id, npc_id, data) — bền: "phuc:N" cho Ăn Xin.
- lottery_tickets (user_id, day, number, settled).

## Sửa đẹp kèm theo

- Tên hiển thị: npcs.json/maps.json giữ khoá kiểu "ve.so" (đúng dữ
  liệu gốc — khoá bản địa hoá của client gốc); bản port bổ sung bảng
  tra tên tiếng Việt đầy đủ dấu trong AvatarDataSet (NPC_NAMES) áp
  cho thẻ tên trên đầu + npcDisplayName, thay cảnh tên NPC hiện
  nguyên khoá "lai.buon"/"ve.so" trên map.
- Câu chat: 12 NPC có chat rỗng trong gốc → bổ sung 2 câu thoại theo
  chủ đề trong bảng tra phía client (chỉ hiển thị bong bóng khi
  tương tác lúc offline/không menu — online menu là chính).
