# Đặc tả BẦU CUA — bản chính (Pack5 client + 3.4.4) & server fan

> Nguồn bằng chứng:
> - **[CL]** client C# gốc Pack5: `~/workspace/avatardk-incoming/client-src/project-client-avt-main/Assets/Scripts/Assembly-CSharp/` — `BCBoardScr.cs`, `BaucuaMsgHandler.cs`, `BaucuaService.cs`, `CasinoMsgHandler.cs`, `CasinoService.cs`, `Xingau.cs`, `PimgBC.cs`, `BoardScr.cs`, `MapScr.cs`, `T.cs`.
> - **[SV]** server fan Java (bản copy của port): `~/workspace/avatardk-java/server/src/main/java/avatar/` — `message/CasinoMsgHandler.java`, `message/minigame/BauCuaMsgHandler.java`, `server/BoardManager.java`, `model/BoardInfo.java`, `constants/Cmd.java`, `network/Session.java`.
> - **[344]** metadata IL2CPP bản chính 3.4.4: `~/workspace/apk-avatardk-new/deep/metadata/all_types_full.json` (chỉ có tên field/method, không có thân hàm).
> - **[IMG]** ảnh object gốc: `~/workspace/avatardk-incoming/server-full/Avatar-Sv-master-master/res/hd/object/87x.png` (đã xem từng ảnh).
>
> Quy ước: **[GỐC]** = có bằng chứng file/dòng. **[FAN]** = chỉ có ở server fan, chưa chắc đúng bản live gốc. **[PORT]** = đề xuất/quy ước khi port. **[CHƯA RÕ]** = chưa có nguồn khẳng định.

---

## 1. Luật chơi

### 1.1 Kiến trúc chung [GỐC]

- Bầu Cua **không thuộc hub MiniGame** (−90/−113 của bản port). Nó thuộc **khung Casino/Board dùng chung** với Tiến Lên (gameType 3), Phỏm (7), Kim Cương/Diamond (21): một handler riêng (GET_HANDLER **3**), danh sách phòng → danh sách bàn → màn bàn. Bầu Cua = **gameType 22** (`CasinoMsgHandler.cs` [CL] case 61: `22 → BaucuaMsgHandler.onHandler()`).
- Bàn **5 ghế** (`BoardScr.numPlayer = 5`, `BoardListOnScr.STYLE_5PLAYER` — `BaucuaMsgHandler.onHandler` [CL]). Ghế đầu tiên (người tạo/chủ bàn, `BoardScr.ownerID`) là **nhà cái**: không đặt cược, không "tả"; đặt mức tiền bàn, bấm bắt đầu, đuổi người. Tối đa **4 người đặt** đấu với nhà cái.
- Client 3.4.4 [344]: `BCBoardScr` / `BaucuaMsgHandler` / `BaucuaService` còn nguyên, tên field/method gần như trùng Pack5 (3.4.4 thêm field kéo–thả `selectedT/selectedF/pxLast/pyLast/isMoveTa`). `BaucuaService` ở cả hai bản đều **rỗng** (chỉ singleton + session) — mọi lệnh gửi đi nằm ở `CasinoService` dùng chung.

### 1.2 Sáu cửa và xúc xắc [GỐC — IMG]

- Bàn có **6 ô cửa** xếp lưới **3 cột × 2 hàng**, thứ tự chỉ số 0..5 theo hàng (0,1,2 trên; 3,4,5 dưới) — `BCBoardScr.loadIMG()` [CL].
- Mặt các cửa lấy từ **icon object 872** (dải dọc 6 frame). Thứ tự frame = chỉ số cửa, đã xem ảnh trực tiếp:

  | Chỉ số | 0 | 1 | 2 | 3 | 4 | 5 |
  |---|---|---|---|---|---|---|
  | Linh vật | **Nai** | **Bầu** | **Gà** | **Cá** | **Cua** | **Tôm** |

- Kết quả ván = **3 viên xúc xắc**, mỗi viên là một mặt 0..5 theo cùng thứ tự trên (icon object **874**, frame 0..5; frame 6,7 là ảnh xúc xắc đang lăn). Server gửi 3 byte kết quả trong gói GAME_RESULT (xem §2).

### 1.3 Luồng một ván [GỐC — CL]

1. **Chuẩn bị**: chủ bàn đặt mức tiền của bàn (lệnh SET_MONEY, §2) — một "đơn vị cược" của bàn. Người chơi vào bàn, bấm Sẵn sàng (READY). Chủ bàn bấm Bắt đầu (START).
2. **Pha đặt cược**: server phát START kèm **1 byte thời gian** (giây) cho pha. Mỗi người đặt (không phải chủ bàn) **chạm vào ô cửa để đặt +1 đơn vị tiền bàn vào ô đó** (`pointerFire → setMoney: pimgBC.moneyPut++`). Client giới hạn **`countEnter < 6`**: tối đa **6 lần chạm = 6 đơn vị/người/ván**, phân bổ tự do vào các ô. Đồng hồ đếm ngược hiện giữa màn; hiện chữ "Bắt đầu tả" ở đầu pha tả (xem bước 3).
   - Chốt đặt: nút **"Xong"** (hoặc hết giờ → client tự chốt `putMoneyFN`) → gửi lệnh **21 (TO_XONG)** kèm **6 byte tiền từng ô** (không có byte đếm — `CasinoService.PutMoneyOk` [CL]).
   - Server phát lại lệnh 21 cho cả bàn kèm ghế người đặt + 6 byte của họ (mảng `moneySV[ghế][6 ô]`) để mọi người thấy tiền đã đặt của nhau.
3. **Pha "tả"** (dời cược): sau khi tất cả đã chốt đặt, server gọi lần lượt từng người đặt bằng lệnh **100 (SET_TURN)** kèm cùng bộ đếm thời gian. Người được gọi có thể **dời toàn bộ tiền từ một ô sang ô khác** ("tả"): chọn ô nguồn có tiền (nút giữa hiện **"Tả"**) → chọn ô đích → client gửi lệnh **65** (định dạng đặc biệt, xem §2). Server dời tiền, phát lại 65 cho cả bàn kèm tổng tiền mới ở ô đích. Có nút **"Chọn lại"** để huỷ chọn ô nguồn, nút **"Bỏ lượt"** (lệnh **49**, gói rỗng phía client BC) để qua lượt. Hết giờ → client tự bỏ lượt (`autoLuot 3 → onSkip`). Chủ bàn không có lượt tả. Nhãn gốc: `T.startTa = "Bắt đầu tả"`, `T.ta = "Tả"` [CL T.cs].
4. **Mở xúc xắc**: server phát **37 (GAME_RESULT)** với 3 byte mặt. Client tạo 3 viên xúc xắc lăn (hoạt hình `Xingau`, §3) — xúc xắc chỉ **dừng hẳn khi nhận FINISH**.
5. **Trả thưởng**: server phát một hoặc nhiều gói **60 (WIN)**: {ghế đưa tiền, ghế nhận tiền, int số tiền} — client hiện số tiền "bay" từ avatar này sang avatar kia (`showFlyText5Baucua`). Sau đó phát **51 (FINISH)**: một `int` cho **mỗi ghế** = số tiền thắng/thua ròng của ghế đó trong ván — client cộng vào tiền hiển thị của từng avatar, hiện số qua bong bóng chat trên đầu, dừng xúc xắc ở 3 mặt kết quả, hiện nút **"Tiếp tục"**.
6. **Vào giữa ván**: người mới vào bàn đang chơi nhận **62 (PLAYING)**: {byte thời gian còn lại, mỗi ghế 6 byte tiền đã đặt} → client khoá nút sẵn sàng, chỉ xem. Rời bàn giữa ván [FAN]: server đánh dấu bỏ lượt cho người rời; còn 1 người thì FINISH toàn 0.

### 1.4 Công thức trả thưởng — server quyết [CHƯA RÕ công thức gốc]

- Client **không tự tính tiền**: mọi số tiền đều do server gửi qua 60/51. Server gốc đóng mã nguồn, server fan chỉ gửi **placeholder 999** (xem §4) → công thức gốc **không có nguồn khẳng định**.
- Quy ước Bầu Cua phổ thông (suy luận, dùng làm cơ sở port): với mỗi ô người chơi đã đặt `s` đơn vị, nếu có `k` viên xúc xắc (1..3) ra đúng mặt ô đó → người chơi nhận lại cược + lãi `k × s`; `k = 0` → mất `s`. Nhà cái thu/trả phần chênh với từng người (ván zero-sum trong bàn: tổng delta 5 ghế = 0 nếu nhà cái đối ứng toàn bộ).

### 1.5 Tiền tệ và điều kiện tiền [FAN]

- Tiền cược là **xu** của người chơi (`User.xeng` trong server fan; `SET_MONEY` kiểm tra `us.getXeng() < Money * 48` → dialog "Để đặt được X thì bạn cần có số tiền là X*48"; trần mức bàn `Money > 100000` → dialog "Vui lòng đặt nhỏ hơn 100.000"). Hằng số **×48 không rõ cơ số** (có thể = 4 người × 6 đơn vị × 2) — [CHƯA RÕ], là logic fan tự chế, giữ hay bỏ cần quyết khi port.

---

## 2. Giao thức

Khung chung: mọi gói trong handler Casino đều bắt đầu bằng **2 byte `roomID, boardID`** (kể cả S→C — client `BoardScr.setR_B(room, board)` kiểm tra khớp bàn hiện tại rồi mới xử lý tiếp [CL BoardScr.cs:1159]). Lệnh là `cmd` 1 byte của khung gói thường. Đăng ký handler: client gửi GET_HANDLER −1 {byte **3**} → server `Session.getHandler` case 3 gắn `CasinoMsgHandler` [SV Session.java:314].

### 2.1 Lệnh vào game (C→S 61 / S→C 61) [GỐC]

| Hướng | Cmd | Payload | Ghi chú |
|---|---|---|---|
| C→S | 61 | byte gameType = **22** | `GlobalService.setGameType(22)` — gửi sau khi server ack GET_HANDLER 3; `typeCasino = 3` ở map/menu → gameType 22 (`MapScr.onJoinCasino`) |
| S→C | 61 | byte gameType | Client rẽ theo byte này: 3 Tiến Lên, 7 Phỏm, 21 Kim Cương, **22 Bầu Cua** → `BaucuaMsgHandler.onHandler()` rồi tự gửi tiếp cmd 6. [FAN] `BauCuaMsgHandler.joinCasino` **luôn trả 22** bất kể byte nhận được |

### 2.2 Lệnh khung bàn (dùng chung casino) [GỐC CL + SV]

| Cmd | Tên | C→S payload | S→C payload |
|---|---|---|---|
| 6 | REQUEST_ROOMLIST | — (rỗng) | lặp tới hết gói, mỗi phòng 4 byte: `id, roomFree, roomWait, lv`. [FAN] 3 phòng id **43, 44, 45**, roomFree=1, roomWait=i, lv=i (số fan tự chế) |
| 7 | REQUEST_BOARDLIST | byte roomID | byte roomID, rồi mỗi bàn: `byte boardID, byte packed (nPlayer = v%16, maxPlayer = v/16), byte flags (bit0 có pass, bit1 đang chơi), int money` — client giải mã đúng như vậy [CL case 7]. [FAN] server ghi rời `nPlayer, isPass` không đóng gói → lệch nghĩa (xem §4) |
| 8 | JOIN_BOARD | byte room, byte board, UTF pass | byte room, byte board, `int ownerID, int boardMoney`, rồi **đúng 5 ghế**: mỗi ghế `int IDDB` (−1 = trống, kết thúc phần ghế đó); nếu có người: `UTF name, int money (xu), byte nWearing, nWearing × short partId, int exp, bool isReady, short idImg`. Sau khi nhận, client nạp nền map **65** cho bàn 5 người [CL case 8] |
| 12 | SOMEONE_JOINBOARD | — | `byte seat, int IDDB, UTF name, int money, byte nWearing, shorts..., int exp, short idImg` |
| 14 | SOMEONE_LEAVEBOARD | — | `int leaveID, int ownerID mới` |
| 15 | LEAVE_BOARD | byte room, byte board | (dùng 14 để báo cho người còn lại) |
| 16 | READY | byte room, byte board, bool isReady | `int userID, bool isReady` (không có room/board ở đầu) |
| 19 | SET_MONEY | byte room, byte board, int money | byte room, byte board, int money |
| 9 | CHAT_TO_BOARD | byte room, byte board, UTF text | byte room, byte board, `int fromID`, UTF text |
| 11 | KICK | byte room, byte board, int kickID | byte room, byte board, int kickID (người bị đuổi nhận dialog "youAreKicked") |
| 18 | SET_PASSWORD | byte room, byte board, UTF pass | — (client có gửi; [SV] fan **không có case xử lý**) |
| 56 | SET_MAX_PLAYER | byte room, byte board, byte max | — (client có; fan không xử lý) |
| 28 | JOIN_ANY_BOARD | — (rỗng) | — (client có; fan không xử lý) |
| 52 | (báo đổi tiền) | — | byte room, byte board, int userID, int delta, UTF text — client cộng tiền + fly text; BC không dùng (dùng 60/51) |

### 2.3 Lệnh riêng của Bầu Cua [GỐC CL BaucuaMsgHandler + CasinoService]

Mọi gói S→C dưới đây đều có 2 byte đầu `room, board` (client đọc trước khi rẽ lệnh).

| Cmd | Tên (Cmd.java fan) | C→S | S→C |
|---|---|---|---|
| 20 | START | byte room, byte board (chủ bàn bấm) | byte room, byte board, **byte time** (giây của pha; client lưu `saveTime` → `BoardScr.interval`). [FAN] ghi cứng time = 10 |
| 21 | TO_XONG (chốt đặt) | byte room, byte board, **6 × byte tiền từng ô 0..5** (không byte đếm) | byte room, byte board, **byte seat**, **6 × byte tiền của seat đó**. Nếu seat == **−1**: client hiểu là "tới phiên mình đặt" → hiện nút Đặt/Xong, khoá con trỏ (`setBotCmd`) — xem [CHƯA RÕ] §5.2 |
| 100 | SET_TURN (gọi lượt tả) | — | byte room, byte board, byte seat được gọi. Client của người được gọi: bật chế độ tả, đếm giờ bằng `saveTime` |
| 65 | TA / HA_PHOM (dời cược) | **6 byte: room, board, room, board, from, to** — `CasinoService.ta()` ghi room/board của BoardScr rồi ghi lại cặp tham số (room, board) lần nữa rồi mới from/to [CL — lỗi nhân đôi của client gốc, phải theo đúng khi port server]. Ô nguồn/đích là chỉ số cửa 0..5 | byte room, byte board, byte seat, byte from, byte to, **byte tổng tiền mới tại ô đích** (client chỉ áp dụng nếu `from != to && value > 0`) |
| 49 | SKIP (bỏ lượt tả / kết thúc lượt) | client BC gửi **gói rỗng** (`CasinoService.skip(room, board)` không ghi payload) — khác bản `skip()` không tham số có ghi room/board | [SV] fan không phát lại 49; fan dùng 65/100 để chuyển lượt |
| 37 | GAME_RESULT | — | byte room, byte board, **3 × byte mặt xúc xắc (0..5)** |
| 60 | WIN (chuyển tiền) | — | byte room, byte board, byte seatFrom, byte seatTo, **int số tiền** → hiệu ứng tiền bay from → to |
| 51 | FINISH | — | byte room, byte board, **n × int delta tiền ròng**, n = số ghế có người (client đọc đúng `avatarInfos.size()` int) |
| 62 | PLAYING (vào giữa ván) | — | byte room, byte board, byte time, rồi mỗi ghế **6 × byte tiền đã đặt** |

---

## 3. Hằng số

| Hằng | Giá trị | Nguồn |
|---|---|---|
| Số ghế / bàn | 5 (1 chủ bàn + tối đa 4 người đặt) | [CL] BaucuaMsgHandler.onHandler |
| Số ô cửa | 6, lưới 3×2 | [CL] BCBoardScr.loadIMG |
| Thứ tự mặt 0..5 | Nai, Bầu, Gà, Cá, Cua, Tôm | [IMG] icon 872/874 (xem §1.2) |
| Số xúc xắc | 3 | [CL] GAME_RESULT đọc 3 byte |
| Cỡ ô cửa | 96×96 (HD) / 48×48 (SD); khoảng cách ngang +10, dọc +8 so với cỡ ô | [CL] BCBoardScr: `rW = hH = 48` (SD), `96` khi hd==2 |
| Vị trí lưới ô | `xbg = W/2 − rW − rW/2 − 10`, `ybg = H/2 − hH − 12` (toạ độ client gốc, màn hình nhỏ kiểu J2ME — chỉ tham khảo tỉ lệ) | [CL] loadIMG |
| Cỡ xúc xắc | 108×108 (HD) / 54×50 (SD); 3 viên cách nhau 64×hd px, y = 10 | [CL] Xingau, BCBoardScr.loadXingau |
| Hoạt hình xúc xắc | mỗi viên một dãy 8 frame, đổi frame mỗi 2 tick: viên 0 `{6,0,7,1,6,2,7,3}`, viên 1 `{6,5,7,4,6,3,7,2}`, viên 2 `{7,4,6,1,7,3,6,5}`; dừng = frame `result[i]` | [CL] BCBoardScr constructor + Xingau.update |
| Đặt tối đa | 6 đơn vị tiền bàn / người / ván (mỗi chạm +1) | [CL] `countEnter < 6` trong pointerFire/doFire |
| Tiền mỗi ô trên giao thức | sbyte (tối đa 127 đơn vị) | [CL] `moneySV[5][6]` sbyte |
| Thời gian pha | byte giây do server gửi trong START (pha đặt) và dùng lại cho từng lượt tả | [CL] saveTime/interval |
| Thời gian pha [FAN] | 10 giây; nghỉ 2,5 giây giữa GAME_RESULT và WIN | [SV] CasinoMsgHandler.Start/gameResult |
| Mức tiền bàn tối đa [FAN] | 100.000 xu; chủ bàn cần xu ≥ mức × 48 | [SV] setMoney |
| Số phòng / bàn [FAN] | 3 phòng (43, 44, 45) × 6 bàn (id 0..5), maxPlayer 5, money mặc định 0 | [SV] requestRoomList, BoardManager.initBoards |
| Vị trí avatar 5 ghế quanh bàn | ghế theo `posAvatar5`: trái-trên, trái-giữa, **giữa-dưới (ghế mình khi xem)**, phải-giữa, phải-trên (toạ độ tương đối màn hình gốc) | [CL] BCBoardScr.init/setPosPlaying |
| Chữ giao diện [GỐC T.cs] | Đặt = "Đặt", Xong = "Xong", Bỏ lượt = "Bỏ lượt", Tả = "Tả", Bắt đầu tả = "Bắt đầu tả", Chọn = "Chọn", Chọn lại = "Chọn lại", Tiếp tục = "Tiếp tục", Xin chờ = "Xin chờ", Đánh = "Đánh" | [CL] T.cs |
| Nền màn bàn | client gốc nạp **map 65** làm nền khi vào bàn 5 người | [CL] CasinoMsgHandler case 8 |
| Hằng bản chính 3.4.4 | **không có** trong `layout_defaults.txt` (dump defaults không chứa BCBoardScr/Baucua*) → mọi hằng lấy từ code Pack5 như trên | [344] |

---

## 4. Asset

### 4.1 Asset của game — icon object (tải từ server) [GỐC]

Bầu Cua **không có thư mục asset riêng**: đã kiểm tra `Assets/Resources/hd/` của Pack5 — có `card/`, `diallucky/`, `farm/`, `race/`… nhưng **không có thư mục baucua**. Toàn bộ hình của game là **icon object** phía server (client lấy bằng `AvatarData.getImgIcon(id)`, tương ứng gói GET_IMG_ICON −80 của bản port):

| Icon | Kích thước | Nội dung / cách dùng | Nguồn file |
|---|---|---|---|
| **872** | 96×576 (6 frame 96×96 xếp dọc) | Mặt 6 ô cửa: frame 0 Nai, 1 Bầu, 2 Gà, 3 Cá, 4 Cua, 5 Tôm (đã xem ảnh). Vẽ bằng drawRegion y = type × 96 | `server-full/.../res/hd/object/872.png` (cũng có bản `res/hd/item/872.png` và bản medium) |
| **874** | 108×864 (8 frame 108×108) | Xúc xắc 3D: frame 0..5 = 6 mặt (cùng thứ tự 872), frame 6,7 = xúc xắc đang lăn nghiêng | `res/hd/object/874.png` |
| **871** | 12×48 (4 frame 12×12) | Mũi tên chỉ hướng tiền bay theo vị trí ghế tương đối (map `getIndex`: vị trí 0→3, 1→0, 2→1, 3→2), vẽ cạnh tên người chơi | `res/hd/object/871.png` |
| 873 | 32×192 (6 frame 32×32) | Cùng dãy số, **không thấy BCBoardScr/PimgBC/Xingau dùng** — [CHƯA RÕ] | `res/hd/object/873.png` |
| 875 | 48×288 (6 frame 48×48) | Như trên — có thể là bản SD của dải mặt cửa — [CHƯA RÕ] | `res/hd/object/875.png` |

Trạng thái bản port: `~/workspace/avatardk-java/assets/house/img/` **chưa có** 871–875 → khi port phải nhập 3 file 871/872/874 (+ .b64) theo cùng pipeline các icon object khác.

### 4.2 Bundle bản chính 3.4.4 [344]

`deep/textures/` **không có** texture nào mang tên baucua/bc. Có một bộ xúc xắc **điểm số** (không phải linh vật): `BG_Dice_0.png` 168×168, `BG_Dice_focus.png` 152×152, `BtDice.png`/`BtDiceF.png` 184×92, `DiceNo_1..6.png` 108×108, `DiceNo_Spin_1..3.png` 108×108 — nhiều khả năng thuộc **Tài Xỉu** [suy luận: mặt số 1–6 thay vì linh vật]. Bầu Cua 3.4.4 giữ nguyên cơ chế icon object 871/872/874 như Pack5 (metadata BCBoardScr/Xingau trùng cấu trúc).

### 4.3 Điểm vào game [GỐC]

Chuỗi vào game đầy đủ:

1. Ngoài thế giới: người chơi bước lên **ô cổng casino** trên map (nhóm type 72–77 trong doJoin — bản port **chưa nối** nhóm này) hoặc chọn từ menu on-game → `doGetHandlerCasino(i)` với **i = 3 cho Bầu Cua** (`MapScr.doGetHandlerCasino`, `onMainMenu.doGetHandlerCasino` [CL]).
2. Client gửi GET_HANDLER −1 {3}; server đổi handler phiên sang CasinoMsgHandler và ack.
3. Client `GlobalLogicHandler.doGetHandler(3)` → `MapScr.onJoinCasino()`: `typeCasino 3 → gameType 22` → `GlobalService.setGameType(22)` = gói **61 {22}**.
4. Server trả **61 {22}** → client `BaucuaMsgHandler.onHandler()` → tự gửi **6** xin danh sách phòng → màn phòng → **7** danh sách bàn → **8** vào bàn → màn bàn (nền map 65 ở client gốc).

Bản port hiện tại: ô "Mini game" trong menu burger mở hub −113 (4 game kia) — Bầu Cua **không** đi đường đó; điểm vào đúng gốc là cổng casino trên map + handler 3.

---

## 5. Lệch fan-server và điểm [CHƯA RÕ]

### 5.1 Lỗi/lệch của server fan so với client gốc (phải sửa khi port)

1. **Xúc xắc thiếu một mặt**: `gameResult` lăn `Utils.nextInt(5)` → 0..4, **không bao giờ ra mặt 5 (Tôm)**. Gốc phải là 0..5.
2. **Trả thưởng placeholder**: WIN gửi `writeByte(999)` (bị cắt thành 1 byte) cho mọi người; FINISH gửi 5 × int 999 bất kể số ghế; không cộng/trừ xu thật.
3. **HA_PHOM đọc sai gói**: server đọc {room, board, from, to} trong khi client gửi {room, board, **room, board**, from, to} → server lấy nhầm room/board làm from/to, không đọc 2 byte cuối; lại còn cộng dồn tiền theo **khoảng** from..to thay vì dời toàn bộ một ô → một ô.
4. **SKIP**: client BC gửi gói 49 **rỗng**, server fan lại `readByte` 2 lần → lỗi EOF.
5. **BoardList không đóng gói**: client chờ byte packed nPlayer|maxPlayer + byte flags; fan ghi rời nPlayer và isPass (chỉ khớp độ dài 7 byte/bàn, sai nghĩa — maxPlayer hiện 0; hack `nPlayer = 80` lúc initBoards bị ghi đè khi có người vào).
6. **JOIN_BOARD ghi sai tiền**: fan ghi `us.getXeng()` của **người vừa vào** cho mọi ghế; SOMEONE_JOINBOARD thừa 1 int và thiếu `short idImg` cuối; CHAT_TO_BOARD ghi fromID bằng **byte** thay vì int.
7. **joinCasino luôn trả 22**: fan bỏ qua byte gameType client gửi — chỉ đúng khi vào Bầu Cua; các game casino khác (3/7/21) chưa làm.
8. Client `onResult` tự gửi lại TO_XONG với 6 ở ô `result[0]` — hành vi lạ của client gốc, fan server bỏ qua vì người chơi đã chốt đặt [CHƯA RÕ chủ ý; port **không** cần mô phỏng].

### 5.2 Điểm chưa rõ cần quyết khi thi công

1. **Công thức trả thưởng gốc** — chỉ có quy ước phổ thông ở §1.4. Cần Tín quyết nếu muốn khác (ví dụ lãi k× hay hoàn cược riêng).
2. **Gói 21 với seat = −1**: server gốc gửi trong tình huống nào (có vẻ là tín hiệu riêng cho người vừa chốt/bắt đầu pha đặt) — port tự quy ước: gửi seat thật cho cả bàn kể cả người đặt, bỏ nhánh −1, hoặc giữ −1 cho chính người đặt.
3. **Thời gian pha gốc**: fan dùng 10 giây cho mọi pha — số của server gốc không có nguồn.
4. **Điều kiện xu ×48 và trần 100.000** của SET_MONEY là fan tự chế — giữ nguyên hay bỏ khi port.
5. **Danh sách phòng/bàn gốc**: id phòng 43–45 và 6 bàn là của fan; server gốc có thể khác (client đọc động theo gói nên không ảnh hưởng client).
6. **Icon 873/875** dùng ở đâu (nếu có) — chưa tìm thấy tham chiếu trong client Pack5.
7. **Nền màn bàn**: client gốc dùng map 65; bản port cần đối chiếu map 65 là gì trong bộ map hiện tại, hoặc tự vẽ nền bàn riêng [PORT].
8. **Bản đồ chứa cổng casino** (ô type 72–77) nằm ở map nào trong bộ map 3.4.4 của bản port — cần rà soát lưới map khi nối cổng (mục riêng ngoài spec này).
9. **3.4.4 có kéo–thả tiền** (`isMoveTa`, `selectedT/F`, `pxLast/pyLast` trong metadata BCBoardScr) — chi tiết thao tác không khôi phục được từ metadata; port theo thao tác chạm của Pack5 trước.

---

## 6. Đề xuất kế hoạch port [PORT]

### 6.1 Server — giữ nguyên khung, sửa lỗi fan

- **Giữ nguyên**: `CasinoMsgHandler` (handler 3) + `BoardManager` (3 phòng × 6 bàn × 5 ghế) + `BauCuaMsgHandler.joinCasino` trả 61 {22}; các lệnh khung 6/7/8/9/12/14/15/16/19/20 đã có sẵn khung xử lý.
- **Sửa** (theo §5.1): xúc xắc `nextInt(6)`; HA_PHOM đọc đủ 6 byte và dời toàn bộ tiền ô nguồn → ô đích; SKIP chấp nhận gói rỗng; BoardList đóng gói byte packed + flags đúng định dạng client; JOIN_BOARD/SOMEONE_JOINBOARD/CHAT sửa đúng field (tiền từng người, short idImg, int fromID).
- **Viết mới**: trả thưởng thật trong `gameResult` — tính delta từng ghế từ `moneyPutList` × mức tiền bàn so với 3 mặt xúc xắc (công thức §1.4, nhà cái đối ứng), cộng/trừ xu thật + lưu DB, phát WIN 60 cho từng khoản chuyển (người thua → chủ bàn, chủ bàn → người thắng) rồi FINISH 51 đúng số ghế. Giữ SET_MONEY nguyên trạng (gồm điều kiện ×48) trừ khi Tín quyết khác.

### 6.2 Client Java — luồng casino riêng (không qua hub −113)

- **Điểm vào**: nối ô cổng casino type 72–77 trong `doJoin` → gửi GET_HANDLER −1 {3} → khi ack: gửi 61 {22} → nhận 61 {22} → mở màn danh sách phòng. (Có thể thêm lối tắt tạm qua menu cho tới khi rà soát xong map chứa cổng — ghi rõ là tạm.)
- **Màn hình cần dựng** (theo khung glass chung của game, bố cục theo [CL]):
  1. **Danh sách phòng** (cmd 6): 3 phòng, trạng thái free/wait/lv.
  2. **Danh sách bàn** (cmd 7): bàn id, số người/max, mức tiền, cờ có pass/đang chơi; nút vào bàn (cmd 8, UTF pass rỗng).
  3. **Màn bàn Bầu Cua**: 5 ghế avatar (doll renderer, vị trí quanh bàn theo §3), lưới 6 ô cửa 3×2 ở giữa dùng icon **872** (chạm để +1 đơn vị, hiện chồng tiền như MoneyPut gốc), 3 xúc xắc icon **874** lăn theo dãy frame §3 và dừng ở kết quả khi FINISH, đồng hồ giây của pha, tiền đã đặt của từng người (moneySV) hiện cạnh ô, nút theo ngữ cảnh: người đặt = Đặt/Xong (chốt), Bỏ lượt, Tả/Chọn lại, Tiếp tục; chủ bàn = Đặt tiền (nhập mức, cmd 19), Bắt đầu (cmd 20), Đuổi (cmd 11); hiệu ứng số tiền bay giữa các avatar khi nhận 60; bong bóng chat trong bàn (cmd 9).
  4. Trạng thái xem giữa ván (62) và xử lý bị đuổi/rời bàn (14/11).
- **Asset**: nhập `assets/house/img/871.png, 872.png, 874.png` (+ .b64) từ res gốc; client nạp qua cùng đường icon object hiện có.

### 6.3 Kiểm chứng đề xuất

- Test sống Python 2 tài khoản (chủ bàn + người đặt) qua handler 3: 61 → 6 → 7 → 8 → 19 → 16 → 20 → 21 (đặt) → 100/65/49 (tả) → 37 → 60 → 51; đối chiếu xu trong DB trước/sau khớp tổng delta (zero-sum), và xúc xắc đủ 6 mặt qua nhiều ván.
