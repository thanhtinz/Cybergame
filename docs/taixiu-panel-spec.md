# Bảng Tài Xỉu [PORT] — 2026-11-06

Bản chính không có TaiXiuScr; cược vẫn qua menu NPC Thần Tài Xỉu 864
theo gốc (textbox nhập số). Panel này chỉ TRÌNH BÀY dữ liệu của hệ
Tài Xỉu phía server (trước nay xem qua tin nhắn chữ rời rạc).

- Gói −124: C->S {byte 0} xin snapshot; S->C {byte 10, int phiên,
  short giây còn lại, 4 long pool (Tài/Xỉu × Xu/Lượng), 4 int cược
  của tôi, byte n ≤ 10 lịch sử {int phiên, UTF kết quả}, 2 × top 5
  thắng ròng (Xu, Lượng) {UTF tên, long ròng}, int ván của tôi,
  int thắng, long ròng Xu, long ròng Lượng}.
- Server: avatar/service/TaiXiuPanelService (đọc betgame/game_rounds
  + TaiXiu.currentGameId()/secondsLeft()).
- Điểm vào: mục đầu "Bảng Tài Xỉu" trong menu NPC 864; trong panel
  có nút Làm mới (gửi lại sub 0).
- Client: panel 880×600 cùng khung BXH (nền đặc 0.98), hai thẻ
  Tài (đỏ) / Xỉu (xanh) + thành tích của tôi + lịch sử + 2 top.
- Test sống live_taixiu_panel_test.py ALL PASS (snapshot trực tiếp,
  mở từ menu, cược thử 10.000 xu Tài hiện trên bảng).
