# Đặc tả đợt mini-game / vòng quay / clan / kết hôn

Nguồn: client C# Pack5 (Assembly-CSharp), server fan trong `server/`,
kiểm kê `docs/completion-audit.md`. Phạm vi đã chốt (Tín, 2026-11-03):
chỉ **Vòng quay may mắn + Clan là TẠO MỚI**; còn lại **port theo gốc**.
Chỗ nào server fan tự chế/hỏng (bot AutoFish, quảng cáo, Thread.sleep
trong handler...) thì bản port làm sạch và ghi rõ [PORT].

## 1. Câu cá (ĐÃ ĐẶC TẢ ĐẦY ĐỦ — làm đầu tiên)

### Điểm vào
- Map câu cá phía client: **15 / 16 / 17** (server 14/15/16). Ô **type 54
  = ghế câu** (LoadMap.doJoin case 54 → `FishingScr.doSat(x, y)`):
  map 15 có 8 ghế (x=4 và 22, hàng data 3–6), map 16 có 12 ghế
  (x=3/15/19/31, hàng 2–4), map 17 có 6 ghế (x=9/17, hàng 3–5).
- doSat: ghế ngồi tại tâm ô ghế; hướng mặt theo ô kề phía đông trong
  lưới THÔ: `map[num+1] ∈ {100, 16, 13}` (nước) → mặt PHẢI, ngược lại
  mặt TRÁI. Rồi gửi **86 START_CAU_CA**.
- NPC **430 Thợ câu** (client map 14): menu server có sẵn —
  "Câu cá" → shop −49 (openUIShop) bán {446 cần câu VIP, 460 vé cá mập,
  448 trứng kiến}; "Bán cá" → bán cá trong rương; "Xem hướng dẫn" →
  tab −58 (text fan là quảng cáo tool auto — [PORT] viết lại hướng dẫn
  thật); "Thoát".

### Vật phẩm (items.json)
| id | tên | giá | vai trò |
|---|---|---|---|
| 446 | cần câu VIP (30 ngày) | 100 lượng | ĐEO trên người (zorder 70, có animation — cần hiện trên tay), hạn 30 ngày |
| 448 | trứng kiến | 30 xu | mồi — trừ 1 mỗi lần quăng câu |
| 460 | vé câu cá mập (2 ngày) | 2 lượng | [PORT] chỉ bắt buộc ở map 17 (khu cá mập) — bản fan bắt mọi map là chế cho event của họ |
| 444/449/450/451/452 | cá rô / lòng tong / chép vàng / lóc / nóc | 50/50/60/70/80 xu | cá thường |
| 454/455/456 | cá chim / đuối / ngựa | 100/110/120 xu | cá vừa |
| 2130/2131/2132 | cá hề / đuôi gai / thiên thần | 1.000/1.500/2.000 xu | cá hiếm |
| 457 | cá mập | 500.000 xu | cực hiếm (trọng số 1/~880) |

Tỉ lệ cá cắn (model Fish của fan, giữ nguyên): mọi loài trọng số 80,
cá mập 1. [PORT] Bỏ cơ chế id âm của fan (−444... = "cá tuột" cài sẵn
bất kể người chơi bấm đúng hay sai — đó là van tiết lưu farm bot):
bản port chỉ lăn loài dương; câu trúng hay hụt do QTE quyết định.

### Giao thức (handler park; bản port đăng ký thêm vào MessageHandler
như các hệ trước vì client port dùng handler 8)
- C→S **86** rỗng → S→C **86** {bool ok, UTF lỗi nếu !ok}: kiểm cần
  446 đang đeo + vé 460 nếu ở map 17. Lỗi gốc fan: "bạn phải có cần
  câu vip" / "bạn phải vé câu cá mập".
- C→S **82** rỗng (quăng câu) → S→C **82** {int idUser} PHÁT CẢ KHU
  (fan chỉ gửi mình mình — [PORT] sửa thành broadcast zone như client
  gốc mong đợi: onQuanCau chạy cho mọi avatar trong map). Trừ 1 mồi
  448; hết mồi → dialog "Hết mồi rồi sếp" (chữ của fan, giữ).
- Sau quăng 8–15 giây (fan: sleep 12–18s TRONG handler — [PORT] thay
  bằng lịch hẹn ScheduledExecutor, không chặn thread): S→C **91
  CAN_CAU** {int idUser, short idFish, short timeDelay=3000,
  byte n, n × {short len, PNG}}: n = 4–10 ảnh mũi tên lấy từ 10 PNG
  của model Fish (mỗi ảnh = 1 hướng: chỉ số ảnh → hướng →
  phím đệm kỳ vọng: →=3, ↑=2, ←=1, ↓=4; ánh xạ chỉ số ảnh đã XEM
  TỪNG ẢNH kiểm chứng: [→,↑,←,←,↓,←,→,→,←,→] → pad
  [3,2,1,1,4,1,3,3,1,3]. ⚠️ Ảnh 2 và 3 trong dữ liệu fan TRÙNG
  nhau từng byte (đều là mũi tên trái); bản ghi cũ
  [→,↑,←,↓,↑,←...] là SAI ở chỉ số 3/4 — lấy theo bảng đã kiểm
  chứng này}.
- Client QTE: hiện ảnh mũi tên hiện tại + đệm 4 hướng (hình thoi,
  HDPaint.paintKeyArrow: phải=3, trái=1, xuống=4, lên=2); mỗi bước có
  timeDelay ms — quá giờ tự ghi 0 (sai). Hết chuỗi → C→S **84**
  {bool true, byte n, n × byte phím đã bấm}.
- Server chấm: đúng hết → trúng; sai bất kỳ bước → tuột. S→C **84**
  {int idUser, short idFish hoặc −1} PHÁT CẢ KHU. Trúng: cá vào
  rương (Item idFish, vĩnh viễn); cá mập 457 → thêm thông báo toàn
  server như fan (serverInfo chúc mừng).
- C→S **85** rỗng (xong/đóng lượt) → S→C **85** {int idUser} cả khu:
  client cho cá vừa câu bay lên (fly icon của part) rồi dọn Fish.
  Đứng dậy khỏi ghế cũng gửi 85 (doClose của gốc).
- **87 STATUS_FISH / 88 INFO_FISH**: fan có code nhưng KHÔNG được gọi
  ở đâu (chết). [PORT] v1 chưa làm: người vào map sau không thấy
  người đang câu từ trước; các gói 82/84/85 broadcast vẫn chạy cho
  lượt mới. Ghi nợ, làm sau nếu cần.
- **88** còn là INFO cấp câu (lv/perLv/numFish/idPart phần thưởng) —
  hệ cấp câu cá không có dữ liệu phía server fan → chưa làm.

### Client port (WorldScreen)
- Va ô 54 (tryPortal) → ngồi: player về tâm ô ghế, đặt hướng theo
  doSat, gửi 86. Đang câu thì khóa di chuyển (joystick/nhấn di chuyển
  = đứng dậy + gửi 85 như doClose).
- Tư thế: action 13 (ngồi câu) / 2 (giật cần) — MapScr.doAction(13);
  bản port dùng khung anim sẵn có của doll nếu hỗ trợ, tối thiểu ngồi
  (3) + vẽ cần/dây/phao.
- Thanh câu cá (glass, trên khung chat): nút **Quăng câu** (sau 86
  OK) / trạng thái chờ cắn / khi cắn: ảnh mũi tên to + đệm 4 nút
  hướng hình thoi + thanh thời gian bước; nút **Xong** (85) và
  **Đứng dậy**.
- Vẽ dây + phao: đầu cần ≈ vị trí người + hướng ×26, cao −30; phao ở
  hướng ×(70 + rnd 25), bobbing nhẹ; cá cắn → phao chìm + chữ "Cá
  cắn câu!" bay (fly text); trúng → icon cá bay lên như onCauCaXong.
- Người khác câu: theo 82/84/85 broadcast — vẽ dây/phao tĩnh theo
  trạng thái (chờ/cắn/xong) tại vị trí của họ.

### Server port (ParkService + MessageHandler)
- Đăng ký case 82/84/85/86 trong MessageHandler → ParkService (giữ
  ParkMsgHandler nguyên trạng cho tương thích).
- handleStartFishing: bỏ check vé 460 toàn cục → chỉ map server 16
  (client 17). Cần 446: findItemInWearing.
- handleQuangCau: broadcast zone (Zone.sendMessage — xem cách các
  gói park khác phát), trừ mồi, hẹn giờ cắn bằng executor dùng
  chung; hủy hẹn khi user rời map/ngắt.
- onCanCau: sinh loài (bảng trọng số), sinh chuỗi ảnh, LƯU chuỗi
  kỳ vọng + idFish vào User (fishSeq/fishExpect).
- case 84 (mới): đọc {bool, n, inputs}; chấm theo fishExpect; gọi
  CauThanhCong(sửa: tham số idFish kết quả, broadcast zone, bỏ
  AutoFish + bán tự động + quà event của fan).
- sellFish (menu Bán cá): [PORT] bán đủ 12 loài, giá × số lượng,
  một tin tổng kết (fan chỉ bán 7 loài lớn và giá không nhân số
  lượng — lỗi).

## 2. Tài Xỉu (NPC 864 + minigame/TaiXiu.java)
- ĐÃ CHẠY: menu −59 lồng (Xu/Lượng → Tài/Xỉu) → TEXT_BOX −60 nhập
  tiền → TaiXiu.handleBetWithInput; phiên 40 giây, kết quả phát
  dialog/chat (đã test sống ở đợt NPC 1: cược 100 xu OK).
- Phía server fan ĐÃ CÓ ĐỦ: TaiXiu.java (766 dòng) — vòng 40s
  (startCountdown + NPC đếm ngược qua bong bóng chat updateNpcChat),
  lưu cược DB, viewBetHistory (lịch sử cược của mình),
  viewGameRoundHistory (lịch sử 10 ván), getTopWinerXu/getTopLossXu/
  getTopWinLuong/getTopLossLuong (bảng top thắng/thua 2 loại tiền).
- Còn thiếu phía client: panel xem phiên/lịch sử/top cho đẹp thay
  vì chỉ tin nhắn tab — khi làm cần đọc cách các view trên được gửi
  (customTab −58 hay tab message) + đối chiếu CasinoScr/MapScr gốc
  xem bản gốc trình bày gì trong lúc phiên chạy.

## 3. Vòng quay may mắn (TẠO MỚI — NPC 185)
- Tham chiếu: DialLuckyScr.cs (441 dòng) + server `avatar/lucky`
  (DialLucky.java, DialLuckyManager, GiftBox) + handler
  NpcHandler.handleDiaLucky (menu đã nối ở đợt NPC 1).
- Giá gốc: 5 lượng / 25.000 xu / vé quay. Assets `hd/diallucky/` có
  sẵn trong Pack5. Thiết kế panel theo hệ kính của game khi làm.

## 4. Đua thú + cược (map 108)
- Client: RaceScr.cs (897 dòng) + RaceMsgHandler.cs; map 108 ép
  tile + màn đua riêng (chưa làm ở đợt tile).
- Server: message/RaceMsgHandler.java riêng + Cmd nhóm race
  (JOIN_ROOM_RACE=1, HISTORY_PET_RACE=8...) trong không gian lệnh
  của Race handler (handler riêng, không phải park).
- Đặc tả chi tiết khi làm (đọc RaceScr + RaceMsgHandler hai phía).

## 5. Mini-game bài
- Client: Card.cs/CardUtils.cs + assets `hd/card/`; các màn bài nằm
  trong CasinoScr (CasinoMsgHandler.cs 293 + CasinoService.cs 582
  phía client).
- Server: message/CasinoMsgHandler.java (handler 3) đã có khung.
- Phạm vi theo gốc: Bài Cào / Tiến Lên (theo dữ liệu client có gì
  làm nấy) — đặc tả chi tiết khi làm.

## 6. Kết hôn (NPC 525 Chủ Hôn, map 11 + phòng cưới 20)
- Không qua menu −61: chạy bằng **93 WEDDING_BIGIN** tại lễ đường;
  server có ParkService.WEDDING_BIGINHanlder (case ở ParkMsgHandler
  — bản port đăng ký thêm vào MessageHandler như câu cá).
- Phòng cưới client 20 đã dựng (atlas wedding, NPC chủ hôn part
  2480 tại ô (26,8)); TRANS_ICON_WEDDING −103 liên quan icon cưới.
- User có sẵn dữ liệu hôn nhân phía server (tin "không tìm thấy
  người dùng hẹn hò hoặc kết hôn với bạn" ở log boot).
- Đặc tả chi tiết khi làm (đọc WEDDING_BIGINHanlder + luồng C#).

## 7. Clan (TẠO MỚI)
- Gốc: client KHÔNG có màn hình clan (chỉ tag tên + dữ liệu);
  server: players.clan_id đã có cột + User.clanID nạp sẵn; chưa có
  bảng/handler clan trong bản fan đã rà (cần kiểm lại schema khi
  làm: clan_members...).
- Tạo mới toàn bộ: bảng clan + thành viên, panel kính (tạo clan,
  danh sách thành viên, mời/rời/giải tán), tag clan cạnh tên trong
  map. Thiết kế khi làm, trình Tín duyệt hướng trước vì là tạo mới.
