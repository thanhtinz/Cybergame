# Bảng đối chiếu CLAN — bản chính 3.4.4 vs bản port (2026-11-05)

Theo skill diff-before-parity: kê từ CHÍNH dữ liệu bản chính (metadata
IL2CPP `all_types_full.json`, `string_literals.txt`, APK 2.60), không
kê từ trí nhớ. Trạng thái: CÓ / THIẾU / KHÁC. Đợt này xử lý các mục
THIẾU/KHÁC có thể dựng được; mục chưa rõ ghi rõ [CHƯA RÕ].

## A. Tính năng theo ClanService (bản chính, 35 method)

| Tính năng | Gốc | Port | Ghi chú |
|---|---|---|---|
| Tạo clan (regisClan + checkCreateClan) | có | CÓ | phí 100.000 xu [PORT số liệu] |
| Danh sách clan + clan mới (LIST/LIST_NEW) | có | CÓ | |
| Xem info clan khác (CHECK_INFO_CLAN) | có | CÓ | |
| **Tìm clan (searchClan, "Enter clan ID")** | có | **ĐÃ LÀM (đợt 2026-11-05, main c5edcb4)** | ô nhập ID ở tab danh sách → mở CHECK_INFO |
| Xin vào / duyệt / từ chối | có | CÓ | |
| Mời vào (INVITE + NOTIFY) | có | CÓ | |
| Rời clan / giải tán | có | CÓ | |
| Kick / phong phó / cách phó | có | CÓ | |
| Chat clan + chặn chat | có | CÓ | |
| Góp quỹ (DialogDonationClan: xu + lượng, nút +/−, ô nhập) | có | CÓ | |
| Lên cấp (REQUEST_INFO_LEVEL_UP + LEVEL_UP; chi phí exp/xu/lượng + sĩ số) | có | CÓ | số liệu [PORT] |
| Nhiệm vụ: list/info/nhận/nộp vật phẩm/done; độ khó + điểm quest (pointQuest/maxPointQuest) | có | CÓ (bản tĩnh) | 6 quest tĩnh [PORT]; độ khó có hiển thị |
| Shop clan: giá xu + lượng (trừ quỹ), levelRequire, durationDay, remain | có | CÓ | 10 món [PORT số liệu] |
| Đồ clan đã mua → nhận vào rương | có | CÓ | |
| **Đổi thông tin clan (CHANGE_CLAN — tên/slogan/logo, chủ clan)** | có | **ĐÃ LÀM (đợt 2026-11-05, main c5edcb4)** | ClanScr có tfInfoClan; Pack5 CHANGE_CLAN=−82. Port: sub 32 [PORT] |
| **Ngày thành lập clan (dobClan)** | có | **ĐÃ LÀM (đợt 2026-11-05, main c5edcb4)** | clans.created_at có sẵn; thêm vào gói INFO + hiển thị |
| **Logo clan gốc (listImgClan)** | có | **ĐÃ SỬA (đợt 2026-11-05, main c5edcb4)** | port đang dùng icon object tạm {2,310…}; bộ cl00/cl01/cl10/cl11 (mây) trong APK 2.60 hd/iconMenu là bộ logo clan → nhập làm logo 0–3 |

## B. Nối vào NPC / MAP (điểm Tín nhắc)

| Tính năng | Gốc | Port | Ghi chú |
|---|---|---|---|
| **NPC Hội Nhóm ở Tòa thị chính — xin gia nhập hội nhóm** | có (literal: "Hãy gặp thị trưởng để nhận nhiệm vụ và gặp NPC Hội Nhóm để xin gia nhập vào các hội nhóm") | **ĐÃ LÀM (đợt 2026-11-05, main c5edcb4)** | NPC mới id 870 ở Khu giải trí (map 10, trước toà nhà thị chính), tương tác mở panel clan |
| **NPC Thị Trưởng — nhận nhiệm vụ** | có (cùng literal) | **ĐÃ LÀM (đợt 2026-11-05, main c5edcb4)** | NPC mới id 871 cạnh NPC Hội Nhóm; tương tác mở tab Nhiệm vụ clan (chưa có clan thì hướng dẫn gặp NPC Hội Nhóm) |
| **Icon/logo clan cạnh tên nhân vật TRÊN MAP (SET_PLAYER_CLAN + Avatar.idLogoClan)** | có | **ĐÃ LÀM (đợt 2026-11-05, main c5edcb4)** | server fan ĐÃ ghi icon clan vào gói info nhân vật (AvatarService:256); client đang ĐỌC BỎ — lưu PlayerInfo.clanIcon + vẽ logo cạnh tên mình + người chơi khác |
| Tab chat clan trong màn Tin nhắn (MessageScr.listTextClanTab) | có | KHÁC (để sau) | port chat clan nằm trong panel clan — ghi nhận khác biệt, chưa dời |
| Thông báo clan thứ Hai 10:00 ("Clan announced every Monday at 10:") | có | [CHƯA RÕ] | khả năng là công bố BXH clan tuần — không đủ dữ kiện dựng, ghi nợ |
| rankClan / imgBossClan / nFarmImgBossClan (Avatar) | có field | [CHƯA RÕ] | không có lệnh/handler boss-clan trong Cmd — không dựng, ghi nợ |

## C. Ngoài clan — tính năng phải nối NPC/map (đã biết, ngoài phạm vi đợt này)

- Cửa mini game trong park (doorMiniGame): ĐÃ NỐI (map 10, main 9dde5de3).
- LETTER −88 (thư chính thức) vs Hòm Thư tự dựng: ghi nợ cũ, chưa đối chiếu.
- ITEM_MAP_SPECIAL=48: ghi nợ cũ.
