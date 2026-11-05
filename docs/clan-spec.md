# Đặc tả CLAN — port theo bản chính 3.4.4 (gói −112, 31 lệnh con)

Ngày soạn: 2026-11-04 (sau đợt Kết hôn, main 564e9c2ee6ea).
Mục tiêu: clan ĐẦY ĐỦ như bản chính — tạo/xin vào/mời, chủ + phó chủ,
quỹ + đóng góp, cấp clan, nhiệm vụ clan (nhận/nộp vật phẩm/hoàn thành),
shop clan (mua bằng quỹ, nhận đồ), chat clan. Tín chốt 2026-11-03:
"clan chuyển thành PORT ĐẦY ĐỦ theo bản chính".

## 1. Nguồn

- `docs/features-344-audit.md` mục A3: handler **CLAN = −112**, 31 lệnh con,
  class `screen/ClanScr.cs`, `networklogic/clan/ClanMsgHandler.cs`,
  `ClanService.cs`, `screen/DialogDonationClan.cs`, sprite `nganquyclan`.
- Metadata IL2CPP bản chính 3.4.4 (`~/workspace/apk-avatardk-new/deep/
  metadata/all_types_full.json`):
  - `ClanService`: đủ 35 method — regisClan, notifyClan, infoClan,
    getInfoListClan, checkInfoListClan, outClan, kickOutClan, chatClan,
    blockUnblockChatClan, requestLevelUpClan, levelUpClan, donationClan,
    requestItemClan, requestItemClanOwn, buyItemClan, receiveItemClan,
    acceptedJoinClan, deniedJoinClan, setDeputy, submitItemQuest,
    acceptedQuest, getInfoQuest, getListQuest, questDone, requestClan,
    requestListClan, inviteJoinClan, searchClan, checkCreateClan.
  - `ClanScr` fields: ownerClan, deputyClan, nameClan, logoClan, dobClan,
    levelClan, moneyClan, numMember/maxMember, pointQuest/maxPointQuest,
    pointClan, listMember, listItem (ItemClan), listRequestClan,
    listMemQuest/listSourceQuest/listQuest, focusTab, tfRegisClan
    (tên + info khi tạo), hdrXu/hdrLuong/hdrPct (quỹ + % exp cấp),
    ITEM_PER_PAGE + phân trang shop, ownIds/ownRemain (đồ clan đã mua).
  - `ItemClan` fields: idPart, priceXu, priceLuong, durationDay,
    levelRequire, isOwned, remainSeconds; nút BTN_BUY / BTN_TAKE /
    BTN_LOCK theo trạng thái.
  - `ListClan` fields: idClan, clanName, sloganClan, clanLvl, logoClan,
    clanXu, clanLuong, curMem, maxMem.
  - `DialogDonationClan`: numXu + numLuong, nút +/− từng loại, ô nhập.
  - `InfoLevelClan`: bảng thông tin cấp (số phó chủ theo cấp — addInfoNumDeputy).
- `Cmd_Message` của bản chính (thứ tự khai báo — xem mục 2).
- DB gốc (Pack5): bảng `clans (id, users_id, name, icon, xu, luong,
  max_members, created_at, description)` + `clan_members (id, clan_id,
  user_id, join_date, role, points, xu, luong, accept)` — ĐÃ CÓ SẴN trong
  DB port, kèm clan mẫu id 6 "BQT" (chủ: user 7). Server fan đọc icon clan
  trong gói thông tin nhân vật (đoạn clan icon short + "Thông báo nhóm").
  CHANGE_CLAN = −82 (đổi thông tin clan) có từ Pack5.

## 2. Giao thức [PORT theo khung bản chính]

Không có thân hàm IL để đối chiếu byte → port dùng đúng SỐ LỆNH CHÍNH
−112 của bản chính, lệnh con (byte đầu payload) theo THỨ TỰ KHAI BÁO
trong `Cmd_Message` của bản chính (thứ tự này là suy luận tốt nhất hiện
có; client + server đều của port nên tự nhất quán):

| sub | Tên bản chính | Hướng |
|---|---|---|
| 0 | REGIS_CLAN | C→S tạo clan |
| 1 | INFO_CLAN | hai chiều: xin/nhận info clan của mình |
| 2 | LIST_CLAN | danh sách clan |
| 3 | PLAYER_REQUEST_CLAN | C→S xin gia nhập |
| 4 | LIST_PLAYER_REQUEST_CLAN | danh sách chờ duyệt |
| 5 | ACCEPT_JOIN_CLAN | duyệt vào |
| 6 | DENY_JOIN_CLAN | từ chối |
| 7 | OUT_CLAN | rời clan |
| 8 | KICK_OUT_CLAN | trục xuất |
| 9 | CHAT_CLAN | chat trong clan |
| 10 | BLOCK_CHAT_CLAN | chủ/phó chặn/mở chat của thành viên |
| 11 | DONATION_CLAN | góp quỹ |
| 12 | CHECK_INFO_CLAN | xem info một clan (trước khi xin vào) |
| 13 | CHECK_CREATE_CLAN | kiểm tra tên/icon trước khi tạo |
| 14 | NOTIFY_CLAN | S→C thông báo clan (duyệt/từ chối/mời...) |
| 15 | REQUEST_INFO_LEVEL_UP_CLAN | xin bảng chi phí lên cấp |
| 16 | LEVEL_UP_CLAN | lên cấp |
| 17 | SET_DEPUTY_CLAN | phong phó chủ |
| 18 | FIRE_DEPUTY_CLAN | cách chức phó chủ |
| 19 | QUEST_CLAN | (gói nhóm nhiệm vụ — port dùng làm ping danh sách) |
| 20 | GET_LIST_QUEST_CLAN | danh sách nhiệm vụ clan |
| 21 | GET_INFO_QUEST_CLAN | chi tiết một nhiệm vụ |
| 22 | SUBMIT_QUEST | nhận/đăng ký nhiệm vụ (acceptedQuest) |
| 23 | SUBMIT_ITEM_QUEST | nộp vật phẩm nhiệm vụ |
| 24 | QUEST_DONE | S→C báo hoàn thành + thưởng |
| 25 | LIST_CLAN_NEW | danh sách clan mới tạo |
| 26 | SET_PLAYER_CLAN | S→C gán clan cho người chơi (icon cạnh tên) |
| 27 | REQUEST_CLAN_ITEM | danh sách shop clan |
| 28 | CLAN_ITEM_OWN | danh sách đồ clan đã mua (kho clan của mình) |
| 29 | BUY_ITEM_CLAN | mua đồ clan (trừ QUỸ clan) |
| 30 | RECEIVE_ITEM_CLAN | nhận đồ đã mua vào rương cá nhân |
| 31 | INVITE_JOIN_CLAN | mời người chơi vào clan |

Định dạng chi tiết từng gói: định nghĩa trong `ClanService.java`
(server) + `AvatarProtocol`/WorldScreen (client) ở đợt triển khai;
nguyên tắc chung: UTF = writeUTF, tiền = int, id = int.

## 3. Mô hình dữ liệu (mở rộng DB gốc, không phá bảng cũ)

- `clans` thêm: `level INT DEFAULT 1`, `exp BIGINT DEFAULT 0`,
  `slogan VARCHAR(255)` dùng lại `description` làm slogan/thông báo.
  (`icon` là varchar trong gốc — lưu số dạng chuỗi, giữ nguyên.)
- `clan_members.role`: 'Chủ Bang' | 'Phó Bang' | 'Thành viên'
  (gốc dùng 'Chủ Bang'). `points` = điểm nhiệm vụ cá nhân trong clan.
  `accept`: 1 = thành viên, 0 = đang xin vào.
- Bảng mới `clan_items_own (id, clan_id, user_id, part_id, bought_at,
  expire_at, received)` — đồ shop clan đã mua bằng quỹ, chờ nhận.
- Bảng mới `clan_quests (id, name, difficulty, need_part, need_qty,
  reward_xu, reward_point)` — bộ nhiệm vụ tĩnh [PORT số liệu, xem mục 5]
  + `clan_quest_state (clan_id, quest_id, progress, done_by, updated_at)`
  tiến độ nộp vật phẩm theo clan.
- Cấp clan [PORT số liệu]: exp cần = level × 1000; maxMember =
  30 + (level−1) × 5; số phó chủ tối đa = 1 + (level ≥ 3 ? 1 : 0) +
  (level ≥ 5 ? 1 : 0); chi phí lên cấp trừ quỹ: xu = level × 50.000,
  lượng = level × 10. Điểm nhiệm vụ clan (pointQuest) cộng vào exp.

## 4. Luật nghiệp vụ

- Tạo clan: chưa thuộc clan nào; tên 3–20 ký tự, không trùng; phí tạo
  [PORT] 100.000 xu (trừ người tạo); người tạo = Chủ Bang; chọn icon
  trong bộ icon clan (id ảnh object — dùng bộ icon sẵn có của game).
- Xin vào: gửi PLAYER_REQUEST (accept=0); chủ/phó duyệt (accept=1) hoặc
  từ chối (xoá dòng). Mời (INVITE): chủ/phó mời người online → người đó
  nhận NOTIFY → đồng ý thì vào thẳng (accept=1).
- Rời clan: thành viên/phó tự rời; chủ bang phải giải tán hoặc chuyển
  chủ — [PORT] chủ rời = giải tán clan (xoá clan + members) sau xác nhận.
- Kick: chủ kick được phó + thành viên; phó chỉ kick thành viên.
- Phó chủ: SET_DEPUTY (chủ, còn slot theo cấp), FIRE_DEPUTY (chủ).
- Quỹ: DONATION trừ xu/lượng cá nhân → cộng quỹ clan + ghi vào
  clan_members.xu/luong (tổng đã góp). Mọi thành viên góp được.
- Chat clan: CHAT_CLAN phát cho mọi thành viên online; BLOCK_CHAT:
  chủ/phó chặn một thành viên (cờ trong clan_members thêm cột
  `chat_blocked`) — người bị chặn gửi chat bị bỏ qua + báo.
- Nhiệm vụ clan: danh sách tĩnh; thành viên SUBMIT_QUEST nhận nhiệm vụ
  (mỗi clan một nhiệm vụ đang chạy [PORT đơn giản hoá]), nộp vật phẩm
  bằng SUBMIT_ITEM_QUEST (trừ item trong rương theo part), đủ số lượng →
  QUEST_DONE: cộng quỹ (reward_xu), cộng exp clan (reward_point),
  cộng points cho người nộp theo tỉ lệ đóng góp.
- Shop clan: danh sách ItemClan tĩnh [PORT số liệu, part thật của game]
  gồm giá xu/lượng (trừ QUỸ clan khi mua — người mua cần là thành viên;
  ghi [PORT]: bản chính trừ quỹ theo header quỹ ở shop), yêu cầu cấp
  clan (levelRequire), hạn dùng (durationDay). Mua → CLAN_ITEM_OWN;
  RECEIVE → item vào rương cá nhân với hạn dùng.
- SET_PLAYER_CLAN: server gửi khi đăng nhập/đổi clan để client hiện
  icon clan (đợt này: client lưu clanId + icon của mình; vẽ cạnh tên
  trong panel — vẽ trên map để đợt sau nếu cần).

## 5. Số liệu [PORT] (bản chính không công khai)

- Phí tạo clan: 100.000 xu.
- Nhiệm vụ mẫu (need_part là sản phẩm farm thật): Thu hoạch nông sản
  (nộp sản phẩm 200 ×10, thưởng 20.000 xu quỹ + 100 điểm), Nộp quả khế
  (215 ×5, 15.000 + 80), Vật phẩm câu cá (444 ×3, 25.000 + 120),
  Nộp trứng gà (sản phẩm vật nuôi), Nấu ăn nộp món... — chốt danh sách
  khi code, dùng part/sản phẩm THẬT có trong dữ liệu game.
- Shop clan mẫu: 8–10 món part thật (ưu tiên món sell=0 hiếm), giá quỹ
  xu/lượng + levelRequire 1–5, hạn 7–30 ngày.

## 6. Điểm vào client [PORT]

- Panel Cá nhân thêm nút "Clan / Bang hội" → mở panel Clan.
- Panel Clan (kính, theo chuẩn panel của game): chưa có clan → 2 tab
  [Danh sách] [Tạo clan]; đã có clan → header clan + 5 tab [Thành viên]
  [Nhiệm vụ] [Cửa hàng] [Quỹ] [Xin vào (chủ/phó)] + nút Chat clan
  (mở panel Tin nhắn kênh clan — đợt này: chat qua khung chat với
  tiền tố gửi gói CHAT_CLAN khi panel clan đang mở? — chốt khi code:
  nút "Chat" trong panel clan mở ô nhập riêng trong panel).
- Hộp góp quỹ (DialogDonationClan): hai hàng Xu/Lượng, nút −/+, ô nhập
  số, nút Góp.

## 7. Kiểm chứng

- Test sống Python 2 tài khoản: tạo clan → tài khoản 2 thấy trong danh
  sách → xin vào → chủ duyệt → thành viên góp quỹ (DB khớp) → phong phó
  → nhiệm vụ: nhận + nộp vật phẩm (item trừ đúng, quỹ/exp cộng đúng) →
  shop: mua bằng quỹ → nhận đồ vào rương → chat clan hai phía → kick →
  rời/giải tán. Regression: login thường + cưới không gãy (gói −112
  đăng ký ở MessageHandler, không đụng handler khác).
- Tour kiểm mắt: panel clan (chưa clan / đã có clan / shop / nhiệm vụ).
