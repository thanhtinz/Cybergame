# Kiểm kê đấu nối NPC / map (2026-11-05)

Đối chiếu 30 NPC trên map (assets/data/maps.json) với luồng tương tác
của bản port và handler phía server gốc (avatar/handler/NpcHandler.java
+ Session.handleBossShop trong bản server fan).

## Đã nối đầy đủ

| NPC | Map (client) | Luồng |
|---|---|---|
| 761 Cửa Hàng | 28 | menu server -> shop chính 126 món |
| 185 Quay Số | 10 | menu -> vòng quay (gói -49/-64) |
| 186 Đấu Giá | 10 | client mở thẳng panel đấu giá ngược |
| 850 Lái Buôn | 26 | client mở panel Điểm danh / Nhiệm vụ ngày |
| 525 Chủ Hôn | 11 | menu cưới client |
| 870 NPC Hội Nhóm | 10 | mở panel clan |
| 871 Thị Trưởng | 10 | clan -> tab Nhiệm vụ; chưa clan -> hướng dẫn + panel clan |
| 864 Thần Tài Xỉu | 12 | menu server -> cược qua TEXT_BOX |
| 860 Lovanga | 12 | menu server (bói toán) |
| 857 Chủ Đầu Tư | 22 | menu server |
| 430 Thợ Câu | 14 | menu server (hướng dẫn câu) |
| 862 Pay To Win | 18 | menu -> shop sự kiện (gói -78) |
| 863 Chay To Win | 18 | menu -> shop sự kiện (gói -78) |
| 165 Saitama | 3 | menu server (quản lý item) — client bổ sung vào npcHasServerMenu 2026-11-05 |
| 858 Bạch Vô Thường | 1 | menu -> shop sự kiện 33 món (gói -78) — client bổ sung 2026-11-05 |
| 859 Hắc Vô Thường | 1 | menu -> 3 shop sự kiện (gói -78) — client bổ sung 2026-11-05 |
| 861 Shop Lượng | 28 | menu "Đổi Quà" -> shop 5 món bằng Lượng (gói -78) |
| 95 Thợ Kim Hoàn | 24 | menu nâng cấp -> danh sách nâng cấp (gói -78, typeShop theo nhánh xu/lượng/đá/sen/mảnh) |

## Gói BOSS_SHOP -78 (mới nối phía client 2026-11-05)

Phía server fan đã đầy đủ từ trước (openUIShopEvent + Session.handleBossShop
cho 95/858/859/861/862/863) nhưng client port KHÔNG có handler -78 nên mọi
shop trên mở menu xong chọn là im lặng. Đã làm:

- AvatarProtocol.BOSS_SHOP = -78 + bossShop(conn, idBoss, idShop, index)
  (đúng ParkService.doBossShop của gốc).
- WorldScreen.handleBossShopPacket: parse {byte type, int idBoss,
  byte idShop, UTF name, short n, n x {short part, UTF dialog[, UTF2]}}
  -> panel danh sách (icon món + dòng điều kiện server gửi) -> nút Chọn
  -> hộp xác nhận [PORT: gốc chọn là gửi ngay] -> gửi lại -78.
- Test sống core/src/test/python/live_bossshop_test.py ALL PASS:
  861 menu -> -78 đủ 5 món -> đổi 3672 thành công ("Chúc mừng bạn đã
  đổi thành công"); 858 menu -> -78 đủ 33 món.

## NPC chỉ đứng + chat (đúng gốc, không có tính năng để nối)

Kiểm chứng: constants có trong NpcName nhưng KHÔNG có case/handler nào
trong toàn bộ server fan (kể cả bản incoming đầy đủ):

- 465 Vé Số (map 12): case trong NpcHandler bị comment và chỉ là stub
  demo ("demo item", "donate đi") — gốc fan chưa từng làm xổ số.
- 645 Cô giáo tiếng Anh, 646 Cô giáo tiếng Việt (map 7)
- 50 Tiên Cá (31), 315 Giám Thị (19), 335 Phi Hành Gia (15),
  425 Phù Thủy Bóng Tối (57), 540 Bậc Thầy Phù Thủy (59),
  590 Pretty Witch (58), 535 Zombie (56), 741 Ăn Xin (4), 760 Pharaoh (34)

Các NPC này trong port đã hiện đúng doll + câu chat gốc từ npcs.json,
khớp hành vi bản fan. Muốn có tính năng cho chúng là TẠO MỚI ngoài gốc.
