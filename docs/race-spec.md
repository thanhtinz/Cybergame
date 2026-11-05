# Đua thú + cược (Race) — đặc tả port

Nguồn gốc: `RaceScr.cs` (897 dòng) + `RaceMsgHandler.cs` (606 dòng) ở
client-src Pack5 đầy đủ thân hàm; `GlobalService.cs` (doDatCuoc /
doPetInfo / doHistoryRace / chatToBoard); `LoadMap.cs` (doJoin type
107 → getHandler(12)). Server fan chỉ có `RaceMsgHandler` rỗng (10
dòng, không override onMessage) → **engine đua phía server là [PORT]**
viết mới theo đúng giao thức client gốc; luật trả thưởng gốc không có
nguồn (giống Bầu Cua) nên chốt quy ước ở mục 3.

## 1. Vào / ra

- Ô type **107** trên map → `getHandler(12)` (handler Đua thú riêng,
  như Casino = 3). Thoát: `getHandler(9)` về Park.
- [PORT] Điểm vào thực tế: client port vào map **108 "Trường đua thú"**
  (strips 6, thế giới 5772×768) là tự gửi −1 {12} + gói 1 xin trạng
  thái; thoát thì về map trước khi vào (mặc định 10).
- Vào giữa chặng đua: server gửi kèm dữ liệu chặng để client tua
  nhanh tới vị trí hiện tại (cơ chế gốc, xem gói 1 dạng 3).

## 2. Giao thức (trong handler 12, lệnh 1 byte)

C→S:
- **1** (rỗng): xin trạng thái → S→C 1.
- **2** {byte petId}: xin thông tin thú → S→C 2.
- **5** {byte petId, int money}: đặt cược → S→C 5 phát cả trường đua.
- **8** (rỗng): lịch sử các chặng gần nhất → S→C 8.
- **9** {UTF text}: chat trong trường đua → S→C 9 phát cả trường đua.

S→C:
- **1** — ba dạng theo byte đầu:
  - `0` (đang mở cược): 6 thú × {byte id, byte rate, short idImg,
    short idIcon}, rồi short timeRemain (giây còn lại để cược).
  - `≠0` + bool **true** (chặng bắt đầu, người đang ở sẵn): 6 thú ×
    {byte nSeg, nSeg × {short numTick, short vTick}}, short
    timeRemain, short timeStart.
  - `≠0` + bool **false** (vào giữa chặng): 6 thú × {byte id, short
    idImg, byte nSeg, segments}, short timeRemain, short timeStart.
- **2** thông tin thú: {short idImg, UTF tên, short numWin (% thắng),
  byte tỉ lệ, byte phong độ (0/1/2), byte sức khỏe (0/1/2)}.
- **5** {byte petId, int money}: tổng tiền cược DỒN lên thú đó (pool).
- **8** {byte n, n × {short idIcon, UTF thời gian}}: lịch sử thắng.
- **9** {UTF text}: chat.
- **10** phiếu thắng (chỉ gửi người có cược trúng): {byte petId,
  UTF tên thú, byte rate, int tiền cược, int tiền ăn, int tiền thuế,
  int tiền nhận được}.

Thú đua: 6 con, làn y = 80 + làn×12 (SD), xuất phát x = 20 (SD).
Chặng chạy bằng **đoạn tốc độ** (numTick, vTick): mỗi tick (20
tick/giây) thú chạy vTick px (SD) trong numTick tick; vTick = 0 =
đứng nghỉ (hiệu ứng "tè"), = 2 = hiệu ứng nước, = 5 = hiệu ứng lửa +
bụi. Hết đoạn mà chưa tới đích thì chạy tiếp với vTick cuối. Thứ hạng
về đích client tự suy từ mô phỏng; tiền thưởng do server quyết qua
gói 10. Đếm ngược xuất phát 48 tick (icon vật 1065, 4 frame).

Khung hình thú gốc: sheet 5 hàng (FRAME: đứng 0/1, chạy 2/3, dừng 4).
Asset UI gốc ở `hd/race/` (popup bt0/bt1/tile1/time, hiệu ứng 28/29,
bui d00–d04, w0–w2) + tileDuaXe.

## 3. Engine server [PORT]

Một chặng toàn server, chu kỳ: **MỞ CƯỢC 60 giây → CHẠY (mô phỏng
theo đoạn, ~30–40 giây) → TRẢ THƯỞNG → mở chặng mới**. Người vào sau
nhận đúng pha hiện tại qua gói 1.

- Roster 6 thú cố định (id 0–5) — **ROSTER THẬT CỦA GỐC** (thay
  bản tạm icon đầu vật nuôi farm bị Tín bác 2026-11-04; chi tiết ở
  docs/race-pets-research.md): sheet thân đầy đủ 5 hàng ở không
  gian vật (house/img) 1056 "Khủng Long", 1057 "Chó Siêu Nhân",
  1058 "Vẹt", 1059 "Mèo", 1060 "Tuần Lộc", 1061 "PiKaChu".
  idImg = idIcon; client cắt frame theo RACE_FRAME của gốc
  (đứng = hàng 0–1, chạy = 2–3, nghỉ = 4).
- Mỗi chặng: mỗi thú lăn rate (2–6), phong độ + sức khỏe (0–2) —
  hiện ở panel thông tin; tỉ lệ thắng lịch sử (numWin) lưu trong bộ
  nhớ server theo thú.
- Sinh đoạn: sức mạnh = (7 − rate) + phong độ + sức khỏe; tốc độ
  đoạn lăn quanh sức mạnh (v 0–6, có đoạn nghỉ 0), tổng quãng đường
  = chiều dài đường đua; mô phỏng 20 tick/giây tìm thứ hạng về đích
  (đích = x ≥ cuối đường). Ngựa về nhất thật từ mô phỏng.
- Cược: mệnh giá gốc {100, 500, 1000, 2000, 5000, 10000, 20000,
  30000, 50000} xu; trừ xu ngay khi đặt; đặt được nhiều thú; rời
  trường đua giữa chặng = mất cược (đúng câu thoại gốc khi thoát).
- Trả thưởng [PORT, gốc không có nguồn]: tiền ăn = cược × rate;
  thuế = 10% tiền ăn; tiền nhận = tiền ăn − thuế; cộng xu + gửi gói
  10 cho từng người thắng. Lịch sử giữ 10 chặng gần nhất.

## 4. Client [PORT bố cục]

- Màn đua dùng chính nền map 108 đang render; thú vẽ ở 6 làn (toạ độ
  làn đo trên ảnh chụp map), ảnh thú = ảnh vật (không có sheet 5
  hàng trong dữ liệu trích được → vẽ ảnh tĩnh + nhún/nghiêng theo
  trạng thái, kèm hiệu ứng nước/lửa/bụi/tè bằng asset race gốc).
- Panel cược kính (theo bố cục gốc): cột trái = thông tin thú đang
  chọn (ảnh, Thắng %, Tỉ lệ X, Phong độ, Sức khỏe, đồng hồ thời
  gian còn, số xu của mình); cột phải = 6 hàng thú (nút ảnh + "X
  rate", nút tiền hiện pool hoặc "Đặt cược") → mở bảng chọn mệnh
  giá 9 ô.
- Trong chặng: camera bám thú đang focus (nút "Tiếp" đổi focus như
  gốc), hiện tổng cược từng thú cạnh thú khi chạy; hết chặng hiện
  đồng hồ đếm tới chặng sau; người thắng nhận popup phiếu thắng 4
  dòng tiền như dialogWin gốc.
- Nút "Lịch sử" + "Thoát" ở góc màn đua; chat trong trường đua đi
  qua ô chat HUD (gói 9) [PORT nối dây].
- Người chơi đứng xem ở mép dưới màn hình như gốc (avatar bám đáy
  camera).
