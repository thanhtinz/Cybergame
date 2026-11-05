# Đặc tả Điểm danh + Nhiệm vụ ngày + Đấu giá ngược (bản chính 3.4.4)

Nguồn: metadata IL2CPP bản chính (`deep/metadata/all_types_full.json`:
DailyLoginScr, DailyGiftData, DailyGiftReward, DailyQuestScr,
AuctionScr), string literals ("REVERSE AUCTION — đấu giá ngược",
"Lịch sử đặt giá"), audit ở docs/features-344-audit.md mục B3/B4.
Luật/số liệu server của bản chính không công khai → phần số liệu là
[PORT], cấu trúc bám đúng metadata.

## 1. Điểm danh (DailyLoginScr + DailyGiftData)

Bản chính: lệnh nhóm DAILY_GIFT=20 (không gian handler riêng):
SUB_DAILY_GIFT_DASHBOARD=0, SUB_DAILY_GIFT_BAODANH=1 (báo danh),
SUB_DAILY_GIFT_OPENSHOP=2, SUB_DAILY_GIFT_GUIDE=3.
DailyGiftData: NUM_DAY_SLOT, dayCount, countBaoDanhToday,
secondToNextBaoDanh, totalPoint, rewards[].
DailyGiftReward: type (0 AVATAR_PART · 1 POTION · 2 POINT · 3 TEXT),
idItem, count, expireDay, name.

Bản port: phong bì **Cmd.DAILY = −116** (tránh đụng START=20 của
board phía fan; −115 đã là FARM2). Sub:
- DASHBOARD 0 {} → {byte dayOfMonth, byte daysInMonth,
  int dayCount (= totalPoint/10), int totalPoint,
  byte countBaoDanhToday, int secondToNextBaoDanh,
  int milestones (bitmask mốc đã nhận), byte nClaimed +
  nClaimed × byte day} — client tự dựng lịch tháng.
- BAODANH 1 {} → RESULT 4 {bool, UTF} rồi DASHBOARD mới. Mỗi ngày
  báo danh 1 lần: nhận quà của ngày hôm nay trong tháng (bảng quà
  [PORT] theo ngày 1..31, xem dưới) + totalPoint += 10.
- MILESTONE 2 {byte idx} → nhận quà mốc điểm (mốc 50/120/220/350/500
  điểm [PORT]); RESULT + DASHBOARD.
- GUIDE 3 {} → RESULT chứa text hướng dẫn (client có sẵn text tĩnh,
  sub này chỉ để khớp cấu trúc — port trả RESULT rỗng, client dùng
  text tĩnh).

Bảng quà ngày [PORT] (theo ngày trong tháng, lặp modulo):
- Ngày thường: 2.000 xu.
- Ngày 7/14/21/28: 5.000 xu + 2 lượng.
- Ngày 15: part ngẫu nhiên cố định 620 (kính) hạn 7 ngày.
- Ngày 30/31: 10.000 xu + 5 lượng.
Quà xu/lượng cộng thẳng; part vào rương (hạn expireDay ngày, 0 =
vĩnh viễn quy ước expire −1 phía rương khi expireDay = 0; ngày 15
dùng 7 ngày).
Mốc điểm [PORT]: 50 → 5.000 xu · 120 → 5 lượng · 220 → 15.000 xu ·
350 → part 2052 hạn 15 ngày · 500 → 20 lượng. Mỗi mốc nhận 1 lần
(lưu claimedMilestones bitmask).

Dữ liệu: bảng mới `daily_state` (user_id PK, last_day TEXT
'yyyy-MM-dd', month_key TEXT 'yyyy-MM', claimed_days TEXT CSV,
total_point INT, milestones INT). Điểm danh mở từ NPC Lái Buôn 850
mục "Điểm Danh" (online: thay luồng menu server gốc hiện tại bằng
mở panel điểm danh — ghi chú: menu −61 gốc của fan là menu nhiệm vụ
cũ, giữ nguyên không đụng; mục Điểm Danh trong menu cục bộ NPC 850
đổi sang mở panel này).

## 2. Nhiệm vụ ngày (DailyQuestScr)

Cấu trúc bản chính: 1 nhiệm vụ hiện tại gồm danh sách vật phẩm cần
nộp (vecItem_nhiemvu) + phần thưởng (vecItem_phanthuong: xu / lượng /
lượng khoá / exp / item / potion), điểm score cộng dồn theo ngày,
thanh điểm có các mốc hộp quà (vecPoint → GETBOX), đổi nhiệm vụ tốn
phí (MONEY_FREE lần đầu / MONEY_XU / MONEY_LUONG), lệnh con
DAILYQUEST_SHOW / CHANGE / DONE / GETBOX (field của Scr).

Bản port: cùng phong bì −116, sub riêng:
- Q_SHOW 10 {} → {UTF questName, byte nReq, nReq × (short itemId,
  int qty, int have), byte nRew, nRew × (byte type, int idOrAmount),
  int score, int maxScore mốc kế, byte nBox, nBox × (int point,
  bool claimed, bool big), byte changeType (0 free · 1 xu · 2 lượng),
  int changeCost}
  - Vật phẩm nhiệm vụ = NÔNG SẢN trong kho farm (id sản phẩm) hoặc
    vật phẩm rương? Metadata dùng "item" chung. [PORT]: yêu cầu là
    nông sản kho farm (giống quest clan nộp từ kho) — nhất quán với
    hệ farm; phần thưởng type: 0 xu · 1 lượng · 2 exp · 3 nông sản.
- Q_CHANGE 11 {} → trừ phí đổi (lần đầu ngày miễn phí, sau đó
  5.000 xu [PORT]) → Q_SHOW mới.
- Q_DONE 12 {} → kiểm đủ vật phẩm → trừ kho, cộng thưởng + score
  (điểm = 20 + số loại yêu cầu × 5 [PORT]) → sinh nhiệm vụ mới →
  Q_SHOW + RESULT.
- Q_BOX 13 {byte idx} → nhận hộp quà mốc điểm: mốc [PORT] 40 (Hộp
  thường: 3.000 xu) · 90 (Hộp thường: 3 lượng) · 150 (Hộp lớn:
  10.000 xu) · 240 (Hộp lớn: 10 lượng) · 360 (Hộp lớn: part 2090
  hạn 15 ngày). Score reset 0h hằng ngày cùng claimed hộp.

Nhiệm vụ tĩnh [PORT] (xoay ngẫu nhiên, tránh trùng nhiệm vụ vừa xong):
1. "Thu hoạch đồng quê": khế 215×5 + bắp 200×5 → 4.000 xu
2. "Tiệc rau củ": cà tím 203×8 + tỏi 204×8 → 5.000 xu + 20 exp
3. "Giỏ trái cây": chuối 249×6 + vải 253×6 → 6.000 xu
4. "Mật ngọt đầu ngày": mật ong 288×3 → 2 lượng
5. "Hoa thơm tặng nàng": cúc chuồn chuồn 266×6 + sen 267×6 → 7.000 xu
6. "Bữa sáng nông trại": trứng gà (SP 50)×5 → 3.500 xu — SP vật nuôi
   mang id loài trong kho (50 gà) theo FarmService hiện hành.
7. "Đặc sản miền quê": dâu tây 248×6 + đào 252×6 → 6.500 xu + 30 exp
8. "Kho đầy ắp": khế 215×10 → 1 lượng + 4.000 xu

Dữ liệu: gộp vào `daily_state` thêm cột quest_idx INT, quest_day
TEXT, score INT, box_mask INT, change_used INT.

Client: panel "Điểm danh & Nhiệm vụ" 920×600, 2 tab:
- Tab Điểm danh: lưới ô ngày trong tháng (7 cột), ô đã báo danh có
  dấu check, ô hôm nay viền cam + nút "Báo danh"; dòng tổng điểm +
  5 mốc quà với nút Nhận khi đủ.
- Tab Nhiệm vụ ngày: tên nhiệm vụ, danh sách vật phẩm cần (icon +
  có/cần), phần thưởng, thanh điểm score + các mốc hộp, nút "Hoàn
  thành" (bật khi đủ), nút "Đổi nhiệm vụ" (hiện phí).

## 3. Đấu giá ngược (AuctionScr — REVERSE AUCTION)

Bản chính: NPC 186 (đang giữ dialog "đấu giá đang bảo trì" của
Pack5). AuctionScr: item, price hiện tại, priceLowestCurrent,
timeBeginEnd (khung giờ phiên), namePlayerTop, arrHistory (lịch sử
đặt giá), typeMoney, sendPrice/get_price. Không có nhóm lệnh riêng
trong Cmd_Message → bản chính đi qua kênh riêng không khôi phục được
byte. [PORT] toàn bộ giao thức:

Luật đấu giá ngược [PORT]: server mở phiên tự động theo chu kỳ:
mỗi phiên 1 vật phẩm (part hiếm xoay vòng), giá khởi điểm cao, giá
GIẢM dần tuyến tính xuống giá sàn trong thời gian phiên (30 phút).
Người chơi ĐẶT GIÁ CHỐT (sendPrice) = đồng ý mua ở giá hiện tại hiển
thị: ai chốt đầu tiên thắng, trả giá tại thời điểm chốt, phiên kết
thúc ngay. Nếu hết giờ không ai chốt → phiên sau vật phẩm khác.
priceLowestCurrent = giá hiện tại (thấp nhất tới thời điểm này).
Lịch sử = các phiên đã kết thúc (người thắng + giá chốt).

Phong bì **Cmd.AUCTION = −117**. Sub:
- INFO 0 {} → {bool active, short partId (client tự tra tên/icon),
  int priceNow,
  int priceStart, int priceFloor, int secondsLeft, UTF topName,
  byte nHist, nHist × (UTF winner, short partId, int price,
  long endAt)} — priceNow server tính theo thời gian thực.
- BID 1 {} → chốt mua ở giá hiện tại: kiểm xu ≥ giá (đấu giá bằng
  XU [PORT], typeMoney = xu), trừ xu, part vào rương (vĩnh viễn),
  đóng phiên, phát INFO mới cho mọi người đang mở panel (chỉ người
  thắng + người đang xem qua lần INFO kế — port không broadcast
  chéo, client tự refresh 5 giây/lần khi panel mở).
- RESULT 2 {bool, UTF}.

Vật phẩm xoay vòng [PORT]: part 2090, 2052, 2019, 2343, 620, 2099 —
giá khởi điểm = giá trị ước lượng ×3, sàn = ×0,8 (bảng trong
AuctionService; ước lượng theo giá shop clan [PORT] đã có).
Phiên lưu RAM + bảng `auction_history` (winner, part, price, endAt)
để lịch sử bền; phiên hiện tại tính từ mốc thời gian cố định
(epoch chia 30 phút) → không cần lưu trạng thái phiên.

Client: panel Đấu giá mở từ NPC 186 (thay dialog bảo trì khi online;
offline giữ dialog gốc): thẻ vật phẩm (icon + tên), giá hiện tại to,
giá gốc/sàn, đồng hồ đếm ngược, nút "Chốt giá này" (xác nhận 2 chạm),
danh sách lịch sử các phiên trước.
