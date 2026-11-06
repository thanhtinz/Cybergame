# Kiểm kê TÍNH NĂNG MỚI của bản chính 3.4.4 so với Pack5 và bản port

Ngày rà soát: 2026-11-03 (chỉ nghiên cứu, không sửa code). Lệnh của Tín:
"phải thêm cho đủ như bản gốc" — bản gốc ở đây là **bản chính đang phát
hành 3.4.4** (com.TeaM.Avatar), không chỉ Pack5.

## Nguồn và phương pháp

- Client 3.4.4 đã trích ở `~/workspace/apk-avatardk-new/`:
  `metadata_strings.txt` (chuỗi IL2CPP), `all_types.json` (8.287 type),
  `cmd_message_344_ordered.txt` (341 field lệnh), `cmd_diff.json`,
  `textassets.json` (2.364 TextAsset), `extract/data/*.bin`.
- Cây source của 3.4.4 khôi phục từ đường dẫn nhúng trong metadata:
  **239 file** `Assets\src\...cs`. Diff theo basename với 201 file
  `Assembly-CSharp` của Pack5 → **78 file mới** (lọc bỏ hạ tầng còn
  ~35 file thuộc tính năng).
- Bảng lệnh: Pack5 có 217 field, 3.4.4 có 341 field → **131 lệnh chỉ có
  ở 3.4.4**, 7 lệnh bị bỏ, và **hầu hết lệnh chung bị ĐÁNH SỐ LẠI**
  (vd SERVER_MESSAGE −9 → −39, TEXT_BOX −60 → −70). Hệ quả: giao thức
  3.4.4 không tương thích trực tiếp với server Pack5/bản port — port
  từng tính năng của 3.4.4 là viết client + server mới theo luồng của
  nó, không phải nối dây lại.
- Quét 813 Texture2D/Sprite trong `data.unity3d` để tìm asset theo hệ.
- Đối chiếu trạng thái port tại `docs/completion-audit.md` +
  `docs/minigame-spec.md` (đợt mini-game đang làm: câu cá XONG; hàng
  đợi: Tài Xỉu → vòng quay → đua → bài → kết hôn → clan).

## Kết luận nhanh

3.4.4 thêm **một tầng mini-game online hoàn toàn mới** (hub + mời +
khán giả, chứa 4 game: Trúc Xanh, Aincrad, Caro, Tàu Chiến, cộng game
offline Rắn Săn Mồi), **Clan đầy đủ** (không phải bản tối thiểu như kế
hoạch port hiện tại), **Chợ + Giao dịch trực tiếp**, **Đấu giá thật**,
**Điểm danh + Nhiệm vụ ngày**, **Farm cấp 2 + nuôi ong**, và một loạt
màn hình/hệ phụ (Thư chính thức, Tủ đồ, Hộp quà, Danh hiệu, Thử đồ).

---

## A. TÍNH NĂNG LỚN

### A1. Hub MiniGame + Mời + Khán giả (nền tảng, MỚI)
- Bằng chứng class: `screen/minigame/MiniGameScr.cs`,
  `MiniGameSelectScr.cs`, `networklogic/minigame/MiniGameMsgHandler.cs`,
  `MiniGameService.cs`, `networklogic/IMiniGameMsgHandler.cs`,
  `MiniGameInviteMsgHandler.cs`, `MiniGameSpectateMsgHandler.cs`;
  định danh `doorMiniGame`, `doInviteMiniGame`, `doReplyInviteMiniGame`.
- Bằng chứng lệnh: handler **MINIGAME = −90** (chiếm đúng số −90 mà
  Pack5 dùng cho UPGRADE_CHEST), bảng game: TRUCXANH=1, AINCRAD=2,
  GAMEID_CARO_GAME=3, TAUCHIEN=4; nhóm **INVITE_MINIGAME=21**
  (INVITE_SEND/REPLY/RESULT + INVITE_CODE_SENT/INVITE_CODE_JOIN — mời
  bằng mã); nhóm **SPECTATE_MINIGAME=22** (SPECTATE_LIST/WATCH/STOP/
  RESULT/ENDED) dùng chung cho mọi game.
- Điểm vào: màn chọn game MiniGameSelectScr; trong park có "cửa
  mini-game" (`doorMiniGame`) + nút sự kiện (model `EventButton`,
  lệnh EVENT_BUTTON_CLICKED=−59 vốn có từ Pack5, sprite
  `eventbuttonbg`). Nhận lời mời có thể vào thẳng ván.
- Port: CHƯA có gì. Đây là khung bắt buộc phải dựng trước nếu làm
  bất kỳ game nào ở A2–A5 theo đúng bản chính.

### A2. Aincrad (MỚI — game id 2)
- Bằng chứng: `screen/minigame/AincradGameScr.cs`,
  `networklogic/minigame/AincradMsgHandler.cs`, `AincradService.cs`.
- Là game riêng trong hub MINIGAME (AINCRAD=2). Không tìm thấy
  texture riêng tên "aincrad" trong bundle (có thể dùng lại asset
  park/quái sẵn có). Luật chơi chi tiết (tháp/PvE?) **chưa đọc được**
  từ metadata — cần dịch IL2CPP sâu hơn hoặc bắt gói live.
- Ước lượng: LỚN (màn hình + handler + service riêng, khả năng là
  chế độ chơi có tiến trình riêng).

### A3. Clan ĐẦY ĐỦ (bản chính có sẵn — port đang định làm tối thiểu)
- Bằng chứng class: `screen/ClanScr.cs`,
  `networklogic/clan/ClanMsgHandler.cs`, `ClanService.cs`,
  `screen/DialogDonationClan.cs`; sprite `nganquyclan` (ngân quỹ clan).
- Bằng chứng lệnh: handler **CLAN = −112** với **31 lệnh con**:
  REGIS_CLAN, INFO_CLAN, LIST_CLAN, PLAYER_REQUEST_CLAN,
  LIST_PLAYER_REQUEST_CLAN, ACCEPT/DENY_JOIN_CLAN, OUT_CLAN,
  KICK_OUT_CLAN, CHAT_CLAN, BLOCK_CHAT_CLAN, **DONATION_CLAN**
  (góp quỹ), CHECK_INFO/CREATE_CLAN, NOTIFY_CLAN,
  REQUEST_INFO_LEVEL_UP_CLAN + **LEVEL_UP_CLAN** (cấp clan),
  SET_DEPUTY/FIRE_DEPUTY_CLAN (phó chủ), **QUEST_CLAN** +
  GET_LIST/GET_INFO_QUEST_CLAN + SUBMIT_QUEST + SUBMIT_ITEM_QUEST +
  QUEST_DONE (nhiệm vụ clan, nộp vật phẩm), LIST_CLAN_NEW,
  SET_PLAYER_CLAN, **shop clan**: REQUEST_CLAN_ITEM, CLAN_ITEM_OWN,
  BUY_ITEM_CLAN, RECEIVE_ITEM_CLAN, INVITE_JOIN_CLAN. Lệnh chính
  CHANGE_CLAN=−82 (đổi thông tin clan) vốn có từ Pack5.
- Port: kế hoạch hiện tại là "tạo mới tối thiểu: panel + clan_members"
  (minigame-spec §7) — **thiếu xa so với bản chính**: thiếu quest
  clan, shop clan, cấp clan, quỹ/đóng góp, phó chủ. Cần nâng phạm vi
  đợt clan thành port theo 3.4.4.
- Ước lượng: LỚN.

### A4. Chợ người chơi (Market) + Giao dịch trực tiếp (Trading) (MỚI)
- Bằng chứng class: `screen/MarketScr.cs`,
  `networklogic/market/MarketMsgHandler.cs`, `MarketService.cs`;
  `screen/TradingScr.cs`, `screen/DialogSubContainer.cs`,
  model `TransMoneyDlg` (vốn có).
- Bằng chứng lệnh: MARKET=127 và cụm giao dịch đồng giá trị 127
  trong bảng field (TRADE_ITEM, ITEM_TRADING, LOCK_TRADE,
  CANCEL_TRADE, SUB_CONTAINER, SEND_SUB_CONTAIN, CHAT_ANIM — các
  field này thuộc lớp lệnh riêng của handler giao dịch/chợ, số 127
  là giá trị field thô trong Cmd_Message), kèm GIVE_CONTAIN=−73,
  CHANGE_PASS_CONTAINER=−54 (mật khẩu rương phụ/giao dịch).
- Nội dung suy ra: người chơi treo bán đồ trên chợ; hai người giao
  dịch trực tiếp qua "sub container" có khóa (lock) chống lừa, có
  hủy giao dịch, có chat trong lúc giao dịch.
- Port: CHƯA có (rương hiện tại là rương nhà + mật khẩu rương còn
  trong danh sách mảnh lẻ). Ước lượng: LỚN.

### A5. Tàu Chiến / Bắn Tàu (MỚI — game id 4) — xem mục riêng §D

### A6. Farm cấp 2 + Nuôi ong (mở rộng farm, MỚI)
- Bằng chứng: `screen/UpgradeFarmLvl2Scr.cs` (màn nâng cấp farm
  lvl2), lệnh **DOWN_FARMLVL2=14** trong nhóm tải dữ liệu (farm lvl2
  có dữ liệu/asset tải riêng), `screen/InfoFarmScr.cs`.
- Nuôi ong: `screen/BeeScr.cs` + định danh `BeeCollection`,
  `IDLvlBee`/`LvlBee` (cấp ong), `IDImgCollectBee`,
  `IDImgNotProductBee`, `isCollectBee`/`isCollectProductBee`
  (thu hoạch ong/sản phẩm), `paintBee`, `setInfoBee` — nuôi ong theo
  cấp, thu mật/sản phẩm ngay trong farm.
- Port: farm đang ở cấp 1 theo Pack5 (đợt 3 đã xong); CHƯA có lvl2
  và ong. Ước lượng: VỪA–LỚN (map/dữ liệu farm mới + hệ ong).

---

## B. TÍNH NĂNG VỪA

### B1. Cờ Caro online (MỚI — game id 3)
- Class: `screen/minigame/CaroGameScr.cs`,
  `networklogic/minigame/CaroMsgHandler.cs`, `CaroService.cs`,
  model `CellCaro.cs`.
- Lệnh (hub −90): CAROGAME_JOIN/START/MOVE/CHANGE_TURN/END/
  PLAYER_LEAVE/KICK + CAROGAME_SPECTATE_SNAPSHOT=7,
  CAROGAME_SPECTATOR_COUNT=8.
- Asset: sprite `caro` + 9 nền bàn `BG_Caro2_0`..`BG_Caro2_8`.
- Lưu ý: không gian lệnh bàn cờ của casino Pack5 vốn có sẵn
  MOVE_CARO=21 (bản 3.4.4 là 31) — caro từng tồn tại ở tầng bàn cờ
  cũ; bản 3.4.4 làm lại thành game riêng trong hub có khán giả.
- Port: CHƯA có (hàng đợi "bài" hiện tại là Bài Cào/Tiến Lên/Phỏm
  theo Pack5, không gồm caro). Ước lượng: VỪA.

### B2. Trúc Xanh (MỚI — game id 1)
- Class: `screen/minigame/TrucXanhGameScr.cs`,
  `TrucXanhMsgHandler.cs`, `TrucXanhService.cs` (thư mục
  networklogic/minigame).
- Lệnh: JOIN_TRUCXANH, START_TRUCXANH, OPENCARD_TRUCXANH (lật thẻ),
  SETTURN_TRUCXANH, ENDCARD_TRUCXANH, OUTCARD_TRUCXANH +
  TRUCXANH_SPECTATE_SNAPSHOT=7, TRUCXANH_SPECTATOR_COUNT=8.
- Asset: sprite `trucxanh`. Thể loại: lật thẻ nhớ hình theo lượt
  (đúng tên "trúc xanh" của dòng game lật hình ăn điểm).
- Port: CHƯA có. Ước lượng: VỪA.

### B3. Điểm danh + Nhiệm vụ ngày (Daily, MỚI)
- Class: `screen/DailyLoginScr.cs`, `DailyQuestScr.cs`,
  `DailyRewardScr` (có type, không nằm trong cây src đã trích),
  `networklogic/DailyLoginMsgHandler.cs`, model `DailyGiftData.cs`,
  `DailyGiftReward.cs`; định danh `doDailyLogin`,
  `OpenDailyGiftScreen`, `ItemDailyReward`,
  `doYouWantChangeDailyQuest` (đổi nhiệm vụ ngày),
  `ListDetailQuest`/`ListItemQuest`.
- Lệnh: nhóm **DAILY_GIFT=20**: SUB_DAILY_GIFT_DASHBOARD=0,
  SUB_DAILY_GIFT_BAODANH=1 (báo danh), SUB_DAILY_GIFT_OPENSHOP=2,
  SUB_DAILY_GIFT_GUIDE=3. Asset: sprite `daily0`, `daily1`.
- Port: mới có stub "Điểm Danh" ở NPC 850 (giữ đúng stub Pack5) +
  thư quà ngày tự thêm [PORT] — chưa có hệ điểm danh/nhiệm vụ ngày
  thật. Ước lượng: VỪA.

### B4. Đấu giá thật (Auction, MỚI so với Pack5)
- Class: `screen/AuctionScr.cs`; định danh `doAuction`,
  `getGuideAuction`.
- Pack5: NPC 186 chỉ còn dialog "đấu giá đang bảo trì..." (port đã
  giữ đúng dialog). 3.4.4 có màn đấu giá riêng → hệ đã mở lại/nâng
  cấp ở bản chính. Chưa thấy nhóm lệnh riêng trong Cmd_Message
  (có thể đi qua menu NPC + lệnh dùng chung).
- Port: CHƯA có màn hình. Ước lượng: VỪA.

### B5. Thư (Letter) chính thức
- Class: `screen/LetterScr.cs`; lệnh **LETTER = −88** (số −88 ở
  Pack5 là PASS_CHEST — bản chính tái sử dụng cho thư trong không
  gian handler của nó).
- Port: ĐÃ có Hòm Thư nhưng là bản tự dựng [PORT] với gói riêng
  −108..−112 + bảng letters riêng. Nếu bám bản chính tuyệt đối thì
  phải đối chiếu lại giao thức LetterScr/−88. Ước lượng: VỪA (chủ
  yếu là đối chiếu + đổi giao thức, UI đã có).

---

## C. TÍNH NĂNG NHỎ / MÀN HÌNH TÁCH RIÊNG

| Hệ | Bằng chứng | Ghi chú / trạng thái port |
|---|---|---|
| Rắn Săn Mồi (Snake) | `screen/minigame/SnakeGameScr.cs`, `imgRuleSnake`, sprite `Snake_1` | Không handler/service, không game id trong hub → game **offline 1 người**, mở từ danh sách mini-game. Port chưa có. NHỎ. |
| Hộp quà (Box) | `screen/BoxScr.cs`, sprite `bg_box_0`..`bg_box_7`, `giftbox_big/normal/checkreceived...` | Màn mở hộp quà (sự kiện/quà tặng). Port chưa có. NHỎ–VỪA. |
| Tủ đồ (Closet) | `screen/ClosetScr.cs`, sprite `btcloset` | Màn tủ đồ riêng (Pack5 gộp trong thông tin nhân vật). Port chưa có màn riêng. NHỎ. |
| Thử đồ (Preview) | lệnh APPLY_PREVIEW_ITEM=−100 | Xem thử món trước khi mua/áp. Port chưa có. NHỎ. |
| Mua nhiều / stack | `DialogBuyManyItems.cs`, BUY_ITEM_STACK=−43, REMOVE_ITEM_STACK=−63 | Mua/xóa item theo số lượng. Port chưa có. NHỎ. |
| Danh hiệu (Title) | lệnh TITLE=−98; sprite UI `backtitle`, `backtabtitle` | Hệ danh hiệu người chơi. Port chưa có. NHỎ–VỪA. |
| Vật phẩm đặc biệt trên map | model `ItemMapSpecial` (id/name/idImg/idMenu/type/listItemMap/itemTouch/itemBlock/isTran/isBlockMap) | ĐÃ RÀ 2026-11-06: đây là MODEL dữ liệu vật thể đặc biệt trên map của client mới (vẽ + chạm + chặn + cổng dịch chuyển), không phải lệnh/gói riêng — phía port vai trò này do objects trong maps.json + LinkMap + điểm tương tác trong thế giới đảm nhiệm. Không còn việc riêng. |
| Trả lời câu hỏi (Quiz) | lệnh ANSWERQUESTION=−97, định danh `AnswerQuestion` | Chưa rõ điểm vào (sự kiện/nhiệm vụ ngày?). NHỎ, cần đào thêm. |
| Nấu ăn tách màn hình | `screen/CookingScr.cs`, sprite `cooking/cooking2/cookoff` | Chỉ là tách UI khỏi FarmScr — chức năng port ĐÃ có trong farm. Không phải tính năng mới. |
| Thông tin farm | `screen/InfoFarmScr.cs`, OPEN_INFO_FARM=104, OPEN_SHOP_FARM=103 | Màn thông tin farm; shop farm port đã có. NHỎ. |
| Hộp đồ câu | `screen/FishingBoxScr.cs` | Màn phụ của câu cá (hệ port vừa làm xong theo Pack5). NHỎ. |
| Shop tách màn hình | `screen/ShopScr.cs` | UI shop riêng; chức năng shop port đã có (mở qua NPC 761). Không phải tính năng mới. |
| Livestream / quảng cáo | `screen/LivestreamListDlg.cs` (`roomsLivestream`), model `ObjAd.cs` | Danh sách phòng livestream + đối tượng quảng cáo — nhiều khả năng là tính năng phụ/sự kiện, bằng chứng mỏng. NHỎ, cần đào thêm nếu muốn làm. |
| Nạp tiền IAP | `screen/TopUpScr.cs`, `Purchaser.cs` | Hạ tầng thanh toán thật (Google/Apple IAP) — ngoài phạm vi port hiện tại (port dùng panel nạp theo gốc Pack5 + MoneyScr vốn có ở cả hai bản). |
| Đăng ký thông tin | `screen/RegisterInfoScr.cs` | Hạ tầng tài khoản. NHỎ. |

Hạ tầng không tính là tính năng: AntiCheat, EULADlg, LocalNotification,
UniGif, DOWNLOAD_DATA=−51 + nhóm DOWN_* (phân loại dữ liệu tải —
port đã có hệ tải dữ liệu riêng [PORT]), LOGIN_ACC/LOGIN_IMEI,
TEXT_BOX_SERVER/INFO_TEXT_BOX_SERVER, CHECK_PLAYER_FLY=−107
(chưa rõ, có thể là kiểm tra trạng thái bay), nhóm sự kiện
TOPUP_GIFT=−14 / TOP_BOARD=−13 / VOUCHER=−75 / REQUEST_TICKET=−55 /
BUY_ITEM_EVENT=−74 (khung sự kiện + shop sự kiện — NPC vô thường
858/859 của Pack5 đã có menu sự kiện tương ứng, port chưa nối đợt
riêng như đã ghi ở completion-audit).

---

## D. Tàu Chiến — đào sâu theo yêu cầu

**Luồng chơi (từ bảng lệnh hub MINIGAME=−90, game TAUCHIEN=4):**
JOIN_TAUCHIEN=0 → START_PLACE_TAUCHIEN=1 (vào pha xếp tàu) →
PLACE_SHIPS_TAUCHIEN=2 (gửi vị trí tàu) → PLACE_RESULT_TAUCHIEN=3 →
PLAYER_READY_TAUCHIEN=4; AUTO_PLACE_TAUCHIEN=5 (xếp tự động) →
START_BATTLE_TAUCHIEN=6 → SHOOT_TAUCHIEN=7 → SHOT_RESULT_TAUCHIEN=8
→ CHANGE_TURN_TAUCHIEN=9 → END_TAUCHIEN=10; PLAYER_LEAVE_TAUCHIEN=11,
KICK_TAUCHIEN=12, SNAPSHOT_TAUCHIEN=13 (khán giả xem lại ván),
SPECTATOR_COUNT_TAUCHIEN=14.

**Class/model:** `screen/minigame/TauChienGameScr.cs`,
`networklogic/minigame/TauChienMsgHandler.cs`, `TauChienService.cs`,
model `ShipTauChien.cs`, `CellTauChien.cs`.

**Cách xếp tàu (từ định danh method/field):** `listDragShip`,
`paintListDragShip`, `dragShip`, `doRotateShip` (xoay tàu),
`selectedShip`, `isAllShipPlaced`, `sendPlaceShips`,
`layoutShipList`, `fontShipListTitle`/`getShipListTitleFont`;
khi bắn: `FindShipAt`, `makeSunkShip`, `paintSunkEnemyShips`,
`paintMyShips`, `END_SHIP_BLINK_MS` (nhấp nháy khi tàu chìm).

**Asset (Texture2D/Sprite trong data.unity3d):** `ListShip_BG`
(nền danh sách tàu), `ListShip_Focus` (khung chọn), **`Ship_lv1` …
`Ship_lv5`** (5 sprite tàu), `tauchien` + `imgTauChien` +
`pm_tauChien` (nền/popup của màn). Field ảnh: `imgShip`,
`imgListShipBg`, `imgListShipFocus`.

**Số tàu / cỡ lưới:** sprite tàu đánh số **lv1–lv5** → suy ra 5 loại
tàu phân theo "cấp" (nhiều khả năng là độ dài 1–5 ô; có
`imgShipVertical` cho bản tàu dọc). **Chưa đọc được** số lượng từng
loại và kích thước lưới (hằng số nằm trong field defaults IL2CPP/
code, không có trong các file đã trích; dataset TextAsset không
chứa cấu hình TauChien). Muốn số chính xác phải dịch IL2CPP sâu
hơn hoặc bắt gói live 3.4.4 (đang kẹt bắt tay server mới như đã
biết). Ước lượng độ lớn: VỪA–LỚN (luồng đầy đủ đã rõ; thiếu mỗi
hằng số cấu hình).

**Điểm vào chơi:** qua hub mini-game (MiniGameSelectScr, "cửa
mini-game" trong park, hoặc lời mời INVITE_MINIGAME kèm mã) — không
thấy NPC riêng cho Tàu Chiến trong dữ liệu client.

---

## E. NPC mới của 3.4.4 — không trích được từ client

`docs/maps-344-audit.md` đã kết luận: bundle 3.4.4 **không chứa
dataset NPC**; ở cả hai bản, NPC là thực thể do **server** phát khi
vào zone. Vì vậy không có danh sách "NPC mới của 3.4.4" để diff với
28 NPC Pack5 từ phía client — muốn biết phải bắt dữ liệu server live
bản chính (đang kẹt bắt tay). Tên `tien.ca` (tiên cá) xuất hiện trong
metadata 3.4.4 trùng NPC cũ của Pack5 ở map 31. Các hệ mới ở mục A–C
đều vào qua hub/menu/panel chứ không qua NPC mới (trừ Đấu giá qua
NPC 186 sẵn có).

## F. Lệnh bị XÓA ở 3.4.4 (để
...[truncated 909 chars]