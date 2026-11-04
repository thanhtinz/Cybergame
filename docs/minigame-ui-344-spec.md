# Đặc tả GIAO DIỆN mini game theo bản chính 3.4.4 (đào 2026-11-03 đêm)

Lệnh Tín 2026-11-03 22:25: "Làm như bản mới nhất của nph" — dựng lại giao diện
hub + 4 mini game khớp bản chính 3.4.4. Tài liệu này là nguồn đối chiếu khi
dựng lại, thay cho bố cục tự dựng [PORT] trước đây (spec §9/§10 trong
tauchien-hub-spec.md giữ phần LUẬT/giao thức, phần BỐ CỤC bị thay bởi file này).

Nguồn: (1) field default values giải từ global-metadata.dat (script
`~/workspace/apk-avatardk-new/deep/dump_layout_defaults.py`, kết quả
`deep/metadata/layout_defaults.txt`); (2) danh sách field đầy đủ trong
`deep/metadata/all_types_full.json`; (3) string literals (đường asset
`/minigame/...` + chuỗi hiển thị); (4) kích thước/nội dung texture trong
`deep/textures/`. Hằng gán trong .cctor/layout() KHÔNG có trong blob
defaults → mục nào không có số thì ghi [CHƯA RÕ].

## 0. Số hiệu game GỐC (đính chính quan trọng)

Đường asset theo game: `/minigame/1/` = AINCRAD (1FL_Subdoor, Ghost, BG),
`/minigame/2/` = TRÚC XANH (Card, bgScore), `/minigame/3/` = CARO
(BG_Caro2_, WIN_X/O), `/minigame/4/` = TÀU CHIẾN (Ship_lv, ListShip_BG),
`/minigame/5/` = SNAKE (SnakeGameScr.GAME_ID = 5, PATH = "/minigame/5/").

→ **gameId gốc: Aincrad 1 · Trúc Xanh 2 · Caro 3 · Tàu Chiến 4 · Snake 5.**
Bản port hiện tại đang để Trúc Xanh 1 / Aincrad 2 (NGƯỢC) — phải đảo lại ở
client + server + nhãn BXH khi dựng lại. (Số liệu mg_stats cũ chỉ là dữ liệu
test của betest01/02, chấp nhận lệch nhãn quá khứ.)

## 1. Màn chọn mini game (MiniGameSelectScr)

Hằng [GỐC, từ defaults]: MAX_GAME = 12, MAX_PER_ROW = 6, PADDING_X = 48,
ICON_LABEL_GAP = 12, LABEL_LINE_H = 32, LABEL_BOTTOM_PAD = 24,
ANCHOR_SCREEN_MARGIN = 20, ANCHOR_GAP_ABOVE = 28, ZONE_1 = 2, ZONE_2 = 4
[CHƯA RÕ ngữ nghĩa ZONE — giả thuyết làm việc: màn chọn có 2 zone/tab;
zoneGames + currentZone là danh sách game theo zone hiện tại].
ANIM_DURATION_MS [CHƯA GIẢI ĐƯỢC — blob 2 byte 0x81b8, không khớp int/float
thường; không chặn việc dựng].

Cấu trúc: lưới neo theo anchor (anchorX/anchorY, cellX/cellY, curCellW/H) —
icon game + nhãn 2 dòng (LABEL_LINE_H 32) dưới icon, khoảng cách icon↔nhãn
12, đệm dưới nhãn 24, lưới tối đa 6 icon/hàng, lề màn hình 20, khoảng trống
phía trên lưới 28.

Asset icon chọn game [GỐC]: `/minigame/select/caro` (64×64),
`/minigame/select/tauchien` (64×64), `/minigame/select/trucxanh` (54×54).
KHÔNG thấy literal icon select cho Aincrad — [CHƯA RÕ] (có thể vào Aincrad
bằng cửa riêng trong park như LoadMap có T_AINCRAD; bản port vẫn để thẻ
Aincrad trong hub, icon dùng Ghost).

Asset phụ: Panel_0/Panel_1/Panel_2 (44×600, dải đứng mảnh — [CHƯA RÕ vai,
có thể là dải tab zone bên cạnh]), `/minigame/Go` (go.png 48×144 = 3 icon
48×48 xếp dọc: dấu chân / người / cửa — nhiều khả năng là cụm icon khán
giả/phòng), nền BG.png 1040×642 (tranh phố anime — [CHƯA RÕ thuộc màn chọn
hay Trúc Xanh/Aincrad do 2 game dùng chung tên asset "BG", bản trích chỉ
còn 1 file; cần trích lại theo object khi dựng tới game đó).

## 2. Khung chung trong ván (MiniGameScr)

Asset [GỐC]: imgBG (nền riêng từng game), imgBox, imgReady =
`/minigame/ready` (323×102, chữ "READY" bong bóng xanh), imgWin =
`/minigame/tx_Win` (112×57 "WIN" vàng), imgLose = tx_Lose (135×57),
imgDraw = tx_Draw (163×57). Kết quả hiện bằng BANNER ẢNH, giữ
RESULT_SHOW_MS [CHƯA RÕ số] rồi đóng (resultCloseTick).

Thành phần khung: 2 ghế người chơi (fMe/fPlayer, posPlayer), đồng hồ lượt
(timeMax/time, countTime), sẵn sàng (isReady/tReady/countReady), **lịch sử
thắng trong ván** (winHistory + WIN_HISTORY_MAX [CHƯA RÕ số] + paintWinHistory),
**hộp quà khi thắng** (isBoxWin + idBoxGift), pill phòng (imgRoomPill +
fontRoomPill + pillX/pillY/pillTextDY + winPillH/winPillMinW + imgWinLogo +
fontWinName — pill hiện tên + số ván thắng), pill khán giả riêng
(imgViewerBg + imgViewer + paintSpectate/paintViewerBadge: badgeX/badgeW/
badgeH/badgeBottom, viewerIcon*/viewerText*), pill GIAO HỮU (paintFriendly +
fontFriendly + friendlyGameID + pillYFriendly) khi ván là giao hữu,
spectate (spectateGameID/spectateBoardID/spectatorCount).

Chuỗi gốc: "Bạn thắng!" / "Bạn thua!" / "Hòa!" / "Ván hòa." / "{0} thắng!" /
" đã chiến thắng" / bản "(bàn thử)": "Bạn thắng! (bàn thử)" — tồn tại khái
niệm BÀN THỬ [CHƯA RÕ cách tạo; bản port tạm gắn với bàn riêng/giao hữu].

## 3. Tàu Chiến (TauChienGameScr — 245 field)

Hằng [GỐC]: BOARD_SIZE = 20, FLEET_SIZE = 14 (scalar — byte đầu blob,
kiểm chứng chéo dataIndex kề), PHASE_PLACING/WAIT_READY/BATTLE/END = 0..3,
ô native 36px (Map_Slot 36×36), Ship_lv1..5 = 32×32 / 64×32 / 108×36 /
144×36 / 180×36 (dài đúng 1..5 ô, cao 1 ô).

Bàn: **20×20** [diễn giải theo BOARD_SIZE + field zoom/BASE_CELL_PX:
bàn vẽ có zoom, cell hiển thị = BASE_CELL_PX × zoom; hai bàn cùng lúc
xBoardMain/xBoardEnemy — thay diễn giải cũ "tổng 2 bàn 10×10"].
Hằng bố cục theo Ô [tên GỐC, số CHƯA RÕ vì gán trong code]: BORDER_PAD_CELLS,
MID_PAD_CELLS, MID_PAD_V_CELLS, OUTER_PAD_CELLS, GAP_BOARDS_CELLS,
PANEL_POKE_CELLS; layout*(): layoutForPhase/layoutCenterPanel/
layoutBoardLabels/layoutLegend/layoutAvatars/layoutWaitBox/layoutShipList.

Thành phần [GỐC theo field + asset]:
- Hai bàn + nhãn cột bằng CHỮ (COL_LETTERS) và nhãn hàng (ROW_TEXTS),
  nền nhãn imgColBg/imgRowBg/imgColRowFocusBg (ColumnRow_BG_1 31×20,
  ColumnRow_BG_2/3 22×31).
- Panel giữa: imgPanelCenterBg = Panel_Center_BG 261×103 (tấm gỗ + bánh
  lái tàu), đồng hồ imgPanelCenterClock = Panel_Center_Clock 53×53
  (CLOCK_SCALE/CLOCK_SHRINK, fontClockRed khi sắp hết giờ), 2 tab
  lượt imgTabActive/imgTabIdle = Panel_Center_Turn_1/2 (111×26),
  TAB_UNDER_CLOCK (tab nằm dưới đồng hồ).
- Danh sách tàu (pha xếp): imgListShipBg = ListShip_BG 256×237 (nền sọc
  xanh 5 dải), hàng chọn imgListShipFocus = ListShip_Focus 256×46
  (hShipRow ≈ 46), mỗi hàng: icon tàu (shipIcon*) + nhãn + số lượng.
- Nút hành động: Bt_Function_Rotate 64×64 [có asset], Bt_Function_Move /
  Bt_Function_Accept [THIẾU asset — chỉ có path tải], nền nút imgButtonBg3 =
  Button_bg3 169×60; các hằng BTN_W_CELLS/BTN_EXTRA_H/BTN_FONT_SIZE.
- Avatar 2 người chơi: avatarFeetY/avatarNameY/avatar1X/avatar2X,
  AVATAR_ICON_W/H [số chưa rõ].
- Legend dưới bàn (SHOW_BOTTOM_LEGEND = true): legendIcons/legendLabels
  (icnStatus_1 67×29, icnStatus_2 36×37 có asset; icnStatus_3/4 THIẾU),
  imgLegendMine, imgPanelLegendBg.
- Hiệu ứng: Eff_Burn_0 (72×288 = strip 4 frame 72×72) cháy khi trúng,
  Eff_Miss_0 (116×280) tóe nước khi trượt, Map_Focus 50×50 (khung ngắm ô
  đang chọn bắn), Map_Stroke 72×72 + Map_Slot_dongbang 36×36 (ô "đóng
  băng" [CHƯA RÕ vai — có thể là ô tàu chìm]),
- Mở màn: đếm ngược từng bước (startCountdownTick + START_COUNTDOWN_STEP_MS
  [chưa rõ số]) kèm banner box (bannerText/bannerBox*); hộp chờ đối thủ
  (waitBox*/waitTextY); pha END: lộ diện hạm đội (END_REVEAL_MS), tàu chìm
  nhấp nháy (END_SHIP_BLINK_MS), tổng thời gian hiện kết quả RESULT_TOTAL_SEC,
  tab kết quả riêng 4 trạng thái END_TAB_ME_WIN/ME_LOSE/OPP_WIN/OPP_LOSE.
- Asset THIẾU (chỉ có path, không có trong bundle): BG_3, BG_3_decor_L,
  Bt_Function_Accept, Bt_Function_Move, icnStatus_3, icnStatus_4 → thay tạm
  bằng Button_bg3 + chữ/Function_Rotate, ghi nợ asset.
- BG_1/BG_2 (156×156) [CHƯA RÕ vai — có thể là nền ô vùng avatar/trang trí].

Luật giữ nguyên theo spec §9 (đã kiểm chứng từ chuỗi gốc + handler): xếp
không đè/không ra ngoài/cách nhau ≥ 1 ô nước cả chéo; bắn 2 bước chọn ô →
"Xác nhận bắn"; ô NONE/SHIP/FIRE/WATER/SUNK = 0..4 (CellTauChien defaults
khớp); thắng khi chìm hết tàu địch; đối thủ thoát → "Đối thủ thoát.
Bạn thắng". [PORT còn phải đối chiếu khi dựng lại: bàn 20×20 thay 10×10 —
đội hình 14 tàu theo FLEET_SIZE, phân bổ dài tàu CHƯA CÓ số gốc (blob sau
byte 14 còn 7 byte 02 02 00 01 01 01 01 chưa giải nghĩa được — có thể là
default của tham số chen giữa, giữ phân bổ port hiện tại tới khi có bằng
chứng khác); trúng bắn tiếp/trượt đổi lượt + lượt 30s là quy ước port cũ,
gốc có đồng hồ lượt thật (timeMax ở MiniGameScr) nên giữ.]

## 4. Cờ Caro (CaroGameScr)

Cấu trúc [GỐC]: bàn có CAMERA cuộn — cửa sổ hiện cShow/rShow (Min/Max),
focusCell/focusOld/focusOpp, cờ isMovePending; kích thước bàn cấu hình
được (configureBoardSize; xBoard/yBoard/wBoard/hBoard + wCell là field
instance — số do server/cấu hình cấp, KHÔNG có hằng defaults).
Asset: nền bàn BG_Caro2_0..8 (9 lát 190px — port đang dùng ✓), quân
X 30×35 / O 31×35, **khung focus F_X/F_O 132×133 = 4 góc đỏ** bao vùng
ô focus, **WIN_X 48×58 / WIN_O 51×56** = quân thắng phát sáng phủ lên
5 ô thắng, hộp quà cuối ván BG_Box_ (BG_Box_0..7) + pendingGiftBox.
Kết ván: pendingEndShow + END_EFFECT_DELAY_SECONDS = 1.6 [GỐC defaults],
REASON_WIN/DRAW/OPPONENT_DROP/TOO_MANY_FORFEIT = 0/1/2/3 [GỐC defaults] —
có xử thua vì BỎ LƯỢT QUÁ NHIỀU LẦN (port chưa có: phải thêm đếm bỏ lượt).
LUẬT THẮNG [chuỗi gốc]: **"Chặn 2 đầu không thắng"** — 5 liên tiếp bị chặn
cả 2 đầu thì KHÔNG tính thắng (port hiện tại cho thắng cả khi bị chặn —
phải sửa khi dựng lại). Hoà khi kín bàn [PORT giữ].

## 5. Trúc Xanh (TrucXanhGameScr)

Cấu trúc [GỐC]: lưới numW × numH (instance — server cấp khi chia bài;
không có hằng defaults), thẻ: imgCard/imgBackCard, wCard + percent +
wCardScale/hCardScale (cỡ thẻ SUY từ phần trăm màn hình × scale — không
phải số cứng), mặt thẻ IMG 128×128 [CHƯA RÕ: texture IMG trích được là
hình đen đặc 128×128 — có thể là mặt nạ/atlas lỗi trích; mặt cặp của gốc
chưa xác định được, port tạm giữ icon nông sản farm], khung thẻ imgStrock
= Stroke 154×154 (viền trắng phát sáng xanh — hiện quanh thẻ đang
mở/đang chọn), điểm: imgScore = bgScore 78×43 (pill điểm sẫm) cho
pointMe/pointPlayer + pointToMe/pointToPlayer (điểm ván + điểm tổng?),
listOpenDelay (hàng đợi lật úp sau khi sai), checkOpen/removeCard phía
client. Asset: Card = mặt SAU thẻ 150×150 (tím sọc, sao vàng, dấu "?"),
CardF 170×170 [CHƯA RÕ — có thể là khung thẻ lật], BG riêng [trùng tên,
xem §1]. Kích thước thẻ gốc quy chiếu: Card 150×150 → khi dựng lại lấy
wCard sao cho lưới numW × numH vừa khung chơi (port hiện dùng 6×5 thẻ
80×96 — thay bằng thẻ VUÔNG theo asset gốc + Stroke khi mở).

## 6. Aincrad (AincradGameScr)

Cấu trúc [GỐC — KHÁC HẲN bản port chạm-3-cửa]: game ĐI BỘ NGANG —
nhân vật có x/y thật (x0, x, y, yg, ydone), di chuyển setMove, camera
cuộn theo (updCamera), giới hạn đi limx_left/limx_right [blob 2 byte
0x80b4/0x835c — CHƯA GIẢI ĐƯỢC số; suy từ sân khấu khi dựng], các cửa
nằm DỌC ĐƯỜNG ĐI: cửa chính (xmaindoor/ymaindoor/wdoor/hdoor) + cửa phụ
(nsubDoor), vecDoor/createDoor/checkDoor, chọn cửa bằng ĐI TỚI cửa rồi
tương tác (AincradService.aincrad_sendDoor). Trang trí theo tầng: hàng
rào (a_xfen1/a_xfen2/a_yf, imgFenci = 2FL_Fencing), imgEx (3FL_Ex = dãy
quạt thông gió — KHÔNG phải biển EXIT, đã đính chính), ảnh tầng imgFL,
cửa 1FL/2FL + biến thể F. Ma: STT_NON = 1, STT_GHOST = 3 [GỐC defaults]
(trạng thái nhân vật), cơ chế kiểm tra ma timeTest/timeghosttest/
isghostTest + tesst() [luật chi tiết CHƯA RÕ — port giữ quy ước: mở sai
→ hoá ma 6 giây không mở được cửa, đánh dấu [PORT]], thắng: FLOOR_WINNER
+ idWinner (tới cửa EXIT tầng cao nhất trước). numFL = số tầng [instance;
port đang dùng 3 tầng theo 3 bộ asset 1FL/2FL/3FL — giữ].

## 7. Thứ tự dựng lại

1. Đảo số hiệu game (Aincrad 1 ↔ Trúc Xanh 2) ở client + server + BXH.
2. Màn chọn theo §1 (+ nhập asset select/Panel_/tx_*/ready/go vào game).
3. Khung chung §2 dùng cho cả 4 game (banner ảnh, READY, winHistory,
   hộp quà, pill khán giả/giao hữu/phòng).
4. Tàu Chiến §3 (bàn 20×20 zoom + 2 bàn + panel giữa + list tàu + legend).
5. Caro §4 (luật chặn 2 đầu + forfeit + hộp quà + focus/WIN overlay).
6. Trúc Xanh §5 (thẻ vuông asset gốc + Stroke + bgScore).
7. Aincrad §6 (đi bộ ngang + camera + cửa dọc đường).
8. Test sống 4 game + tour chụp kiểm mắt; push gộp cả đợt đang chờ
   (shop đồ câu 9 món + BXH + sửa ghim minimap).

## 8. Bổ sung phân định asset BG (đào thêm 2026-11-03 22:36)

Bundle có ĐÚNG 2 texture tên "BG": 1040×642 (path_id 27, phố anime ban
ngày) và 1040×911 (path_id 142, trời đêm sao + skyline thành phố + 2
thuyền buồm dưới nước). Literal chỉ có /minigame/1/BG (Aincrad) và
/minigame/2/BG (Trúc Xanh) → mỗi game một BG. Phân định [SUY LUẬN cấu
trúc]: Aincrad = bản ĐÊM 1040×911 (cao hơn màn hình — khớp camera cuộn
lên theo tầng + tông ma mị); Trúc Xanh = bản NGÀY 1040×642 (một màn
hình). Đã nhập vào game: `BG_Aincrad.png` (đêm) + `BG_TrucXanh.png`
(ngày) ở assets/minigame kèm .b64.
