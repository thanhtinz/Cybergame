# Park spec — gói trong map (đợt 2d)

Đo trực tiếp từ server Java trong project (`server/src/main/java/avatar/...`),
đối chiếu client C# gốc trong Pack5. Hệ toạ độ: **medium** của gốc (client vẽ ×2).
**Map id gửi lên server = client map id − 1** (xem maps-spec.md mục 0).

## Client → Server

| Cmd | Tên | Thân gói |
|-----|-----|----------|
| 50 | AVATAR_JOIN_PARK | byte mapId(server), byte area (−1 = tự chọn khu), short x, short y → `ServerManager.joinAreaMessage` |
| 54 | MOVE_PARK | short x, short y, byte direct → `User.move` (đọc thêm short tuỳ chọn nếu còn) |
| 55 | CHAT_PARK | UTF text → `User.chat` |

`direct` theo `Base.cs` của client gốc: RIGHT = 0, LEFT = 2 (mặc định LEFT).

## Server → Client (MapService của zone phát cho MỌI người trong zone, kể cả người gửi)

| Cmd | Tên | Thân gói |
|-----|-----|----------|
| 50 | AVATAR_JOIN_PARK (trả cho người vừa vào; danh sách KHÔNG gồm chính mình — server gửi trước khi `add`) | byte mapId, byte zoneId, short −1, short −1; byte numUser; mỗi user: int id, UTF name, byte nWearing, short[n] wearing, short x, short y, byte role (0 = NPC); rồi mỗi user: byte direct; mỗi user: byte 101; mỗi user: short −1; byte 0, byte 0; short nMapItems, byte nTypes, mỗi type: byte id, short imgID, byte iconID, short dx, short dy, byte nPos, mỗi pos: byte x, byte y; byte nMapItems2, mỗi item: byte type, byte typeID, byte x, byte y; mỗi user: short −1 |
| 51 | PLAYER_JOIN_PARK (phát cho người đã ở trong zone khi có người mới vào) | int id, UTF name, byte nWearing, short[n], short x, short y, byte role, byte −1, short −1, short −1 |
| 53 | PLAYER_LEAVE_PARK | int id |
| 54 | MOVE_PARK | int id, short x, short y, byte direct |
| 55 / 77 | CHAT_PARK / CHAT_FARM (77 khi server map = 22) | int id, UTF text |

Ghi chú:
- Handler cơ sở `MessageHandler` đã route sẵn 50/54/55 (không cần đổi handler).
- Server NPC cũng nằm trong danh sách zone (role 0) — client chỉ dựng người
  chơi thật (role ≠ 0) từ gói mạng; NPC tĩnh vẫn vẽ theo maps.json như đợt 1.
- Người chơi nhận echo 54/55 của chính mình: client bỏ qua gói mang id của mình
  (di chuyển lấy nội bộ làm chuẩn; chat hiển thị qua echo như client gốc).
