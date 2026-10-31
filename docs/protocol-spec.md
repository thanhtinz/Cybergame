# Đặc tả giao thức mạng AvatarDK (bản gốc) — phục vụ đợt port online

Nghiên cứu từ mã nguồn gốc trong Pack5, ngày 2026-10-31. Mọi khẳng định đều có trích dẫn `file:dòng`.

Quy ước đường dẫn viết tắt:

- **SRV** = `archive_x/Avatar-Sv-master-master/src/main/java/` (server Java, package `avatar.*`)
- **CLI** = `archive_x/project-client-avt-main/Assets/Scripts/Assembly-CSharp/` (client Unity C# dịch ngược)
- **SRVROOT** = `archive_x/Avatar-Sv-master-master/` (nơi chứa `config.properties`, `database.properties`, `database/avatar_2x.sql`)

Lưu ý chung: đây là giao thức Teamobi cổ (họ game Avatar/MobiArmy). Command id là **byte có dấu** (Java `byte` / C# `sbyte`), nên phần lớn lệnh có giá trị âm. Cùng một số có thể được tái sử dụng ở các handler/bối cảnh khác nhau (ví dụ 50 = `STOP_GAME` trong Cmd chung nhưng = `AVATAR_JOIN_PARK` ở luồng avatar — xem `SRV/avatar/constants/Cmd.java:133` và `:153`).

---

## 1. Định dạng khung gói tin trên socket

### 1.1. Kết nối

- TCP socket thuần, cổng **19128**: `SRVROOT/config.properties:1` (`server.port=19128`), server đọc tại `SRV/avatar/server/ServerManager.java:63`.
- Phía client: `TcpClient.Connect(host, port)`, luồng đọc/ghi là `BinaryReader`/`BinaryWriter` trên `NetworkStream` (`CLI/Session_ME.cs:325-333`).

### 1.2. Khung gói tin

```
[command : 1 byte] [length : 2 byte, big-endian, không dấu] [payload : length byte]
```

- Server ghi: `SRV/avatar/network/Session.java:124-161` (`doSendMessage`) — ghi command, ghi độ dài payload dạng 2 byte (cao trước), rồi payload.
- Server đọc: `SRV/avatar/network/Session.java:2160-2183` (`MessageCollector.readMessage`) — `readByte()` lấy command, hai byte độ dài ghép `(b1 << 8) | b2`, rồi đọc đúng `size` byte payload.
- Client ghi/đọc đối xứng: `CLI/Session_ME.cs:351-419` (`doSendMessage`) và `CLI/Session_ME.cs:145-195` (`readMessage`).
- Thứ tự byte (endianness): theo `java.io.DataInputStream/DataOutputStream` — **big-endian** cho short/int/long; payload được xây bằng `DataOutputStream` trong `SRV/avatar/network/Message.java:20-24, 53-59`. Phía client dùng `myWriter/myReader` tương thích ngược với định dạng này (`CLI/Message.cs:5-43`).
- Chuỗi: `writeUTF/readUTF` kiểu Java — 2 byte độ dài big-endian + modified UTF-8.

### 1.3. Ngoại lệ: command 90

Khi gửi gói có command **90** (`UPDATE_LAND`), server **không** dùng khung chuẩn: độ dài được ghi bằng `writeInt` (4 byte) và payload ghi **thẳng, không XOR** (`SRV/avatar/network/Session.java:133-136`). Đây là bất đối xứng chỉ ở chiều server→client trong mã server; code gửi phía client dịch ngược không có nhánh tương tự (`CLI/Session_ME.cs:351-419`).

### 1.4. Mã hoá XOR bằng khoá phiên (sau bắt tay)

Sau khi bắt tay xong (mục 2), **mọi byte trên đường truyền đều bị XOR** — gồm byte command, 2 byte độ dài và toàn bộ payload:

- Bên gửi: command qua `writeKey()`, từng byte độ dài qua `writeKey()`, rồi từng byte payload cũng qua `writeKey()` trước khi ghi (`SRV/avatar/network/Session.java:124-161`; phía client `CLI/Session_ME.cs:358-395`).
- Bên nhận: giải XOR theo đúng thứ tự đó (`SRV/avatar/network/Session.java:2160-2183`; `CLI/Session_ME.cs:145-187`).
- Thuật toán khoá lăn (rolling key), hai phía giống hệt nhau:

```
out = key[cur] XOR in;  cur = (cur + 1) mod key.length
```

có hai con trỏ độc lập cho chiều đọc (`curR`) và chiều ghi (`curW`), đều bắt đầu từ 0 khi kết nối (`SRV/avatar/network/Session.java:163-185`; phía client `CLI/Session_ME.cs:423-441`). Con trỏ **không reset theo từng gói** — nó chạy liên tục qua command + độ dài + payload của mọi gói nối tiếp nhau, nên hai phía phải giữ đồng bộ tuyệt đối theo từng byte.

- Trước khi bắt tay (`connected == false` phía server / `getKeyComplete == false` phía client), gói tin đi **dạng rõ (plaintext)** với cùng khung ở 1.2.

---

## 2. Bắt tay (handshake) — lấy khoá phiên

Khoá phiên là **một khoá tĩnh cấp server**, sinh lúc server khởi động và dùng chung cho mọi kết nối:

```java
private static final byte[] key = (System.currentTimeMillis() + "_kitakeyos").getBytes();
```

(`SRV/avatar/network/Session.java:54`) — tức khoá là chuỗi `"<millis lúc server start>_kitakeyos"`.

Các bước:

1. Client vừa kết nối TCP xong thì **gửi ngay một gói rỗng command −27** ở dạng rõ (`CLI/Session_ME.cs:336`: `doSendMessage(new Message(-27))` sau khi mở socket).
2. Server nhận command −27 trong handler mặc định và gọi `handshakeMessage()` (`SRV/avatar/message/MessageHandler.java:228-231`).
3. Server trả gói **−27** (vẫn dạng rõ, vì cờ `connected` chỉ bật sau khi gửi) với payload (`SRV/avatar/network/Session.java:278-289`):

   ```
   [keyLength : 1 byte] [key[0] : 1 byte] [key[i] XOR key[i-1] : 1 byte, với i = 1..keyLength-1]
   ```

   tức khoá được mã hoá vi phân (delta-XOR) trước khi gửi. Sau đó server đặt `connected = true` và bắt đầu luồng gửi.
4. Client nhận gói −27 thì chạy `getKey()`: đọc độ dài + các byte, rồi khôi phục khoá bằng XOR luỹ kế `key[j+1] ^= key[j]`, đặt `getKeyComplete = true` (`CLI/Session_ME.cs:73-75` và `120-140`).
5. Từ gói tiếp theo trở đi, cả hai chiều đều dùng XOR lăn như mục 1.4.

---

## 3. Chuỗi gói tin mở đầu sau khi bắt tay (chưa đăng nhập)

Ngay khi vào màn hình đăng nhập, client gửi một cụm 4 gói (`CLI/GlobalService.cs:153-190`, hàm `setProviderAndClientType`):

| # | Command | Hằng (SRV Cmd.java) | Payload theo thứ tự ghi |
|---|---------|--------------------|--------------------------|
| 1 | −1 | `GET_HANDLER` (`Cmd.java:100`) | `byte` = `CLIENT_TYPE` (= **8** nghĩa là handler Avatar; `CLI/GlobalService.cs:155-157`, giá trị tại `CLI/GameMidlet.cs` khởi tạo tĩnh) |
| 2 | −17 | `SET_PROVIDER` (`Cmd.java:86`) | `byte` provider; `int` memory (=10000 phía client); `UTF` platform (client ghi `"uni"`); `int` rmsSize (=10000); `int` width; `int` height; `boolean` (true); `byte` resourceType (0 = medium, 1 = HD theo `hd - 1`); `UTF` version (`"2.5.8"`) |
| 3 | −79 | `SET_AGENT` (`Cmd.java:32`) | `UTF` agent (client gửi `GameMidlet.AGENT` = `"0"`) |
| 4 | −86 | *(không có hằng phía server)* | `byte` = 2; nếu không phải Android: thêm `byte` versionCode + `UTF` deviceUniqueIdentifier |

Phía server xử lý:

- `GET_HANDLER`: `Session.getHandler()` đọc 1 byte "index" và chuyển handler: 3 → Casino, **8 → AvatarMsgHandler**, 9 → Park, 10 → Farm, 11 → Home, còn lại → MessageHandler mặc định; đồng thời server **trả lại một gói `GET_HANDLER` chứa đúng byte đó** (`SRV/avatar/network/Session.java:292-325`). Riêng index 8 còn làm user rời zone hiện tại trước (`Session.java:296-299`). Handler chỉ được đổi khi đã đăng nhập (`SRV/avatar/message/MessageHandler.java:263-269`).
- `SET_PROVIDER`: `Session.clientInfo()` đọc đúng thứ tự trên; nếu còn dư byte thì đọc thêm 3 `UTF` nữa (`SRV/avatar/network/Session.java:376-395`; dispatch tại `MessageHandler.java:236-239`). `resourceType` quyết định thư mục tài nguyên server trả về sau này: `res/hd/` hay `res/medium/` (`Session.java:271-276`, `SRVROOT/config.properties:12-13`).
- `SET_AGENT`: `Session.agentInfo()` chỉ đọc 1 `UTF` và in log (`SRV/avatar/network/Session.java:397-400`).
- Gói −86: **không tìm thấy** hằng số hay nhánh xử lý phía server trong mã nguồn này (gửi đi và bị bỏ qua ở tầng handler mặc định).

---

## 4. Luồng đăng nhập

### 4.1. Gói đăng nhập (client → server)

Command **−2** (`LOGIN`, `SRV/avatar/constants/Cmd.java:99`; khớp phía client `CLI/Cmd_Message.cs:189`). Client xây tại `CLI/GlobalService.cs:204-219`:

| Thứ tự | Kiểu | Nội dung |
|--------|------|----------|
| 1 | UTF | username (server `trim()`) |
| 2 | UTF | password dạng rõ (chỉ được bảo vệ bởi XOR phiên) |
| 3 | UTF | version client |

Server đọc đúng 3 UTF này tại `SRV/avatar/network/Session.java:449-467` (`doLogin`), chỉ xử lý khi session chưa đăng nhập và khi `client.user == null` (`SRV/avatar/message/MessageHandler.java:256-261`).

### 4.2. Kiểm tra phía server

`User.login()` (`SRV/avatar/model/User.java:872-…`):

- Nếu `server.active=false` (bảo trì) → trả thông báo và thất bại (`User.java:873-876`).
- Truy vấn: `SELECT * FROM users WHERE username = ? AND password = ? LIMIT 1 FOR UPDATE` với mật khẩu được băm **`Utils.md5(password)`** trước khi so (`User.java:879-885`) — tức DB lưu MD5 của mật khẩu.
- Kiểm tra cột `active` (chưa kích hoạt → báo lỗi, `User.java:891-896`), `login_lock` (đang có phiên khác → ngắt phiên cũ, báo lỗi, `User.java:898-923`), và JSON `ban` (`User.java:930-…`).
- Sai mật khẩu: server gửi gói thông báo `SERVER_MESSAGE` (−9) chứa text lỗi qua `Service.serverMessage()` (`User.java:~976`; `SRV/avatar/service/Service.java:77-85`).
- Hằng `LOGIN_FAIL = -5` tồn tại hai phía (`Cmd.java:97`, `CLI/Cmd_Message.cs:185`) nhưng **không tìm thấy chỗ server gửi gói −5** trong luồng đăng nhập của mã nguồn này.

### 4.3. Sau khi đăng nhập đúng — phía server

`Session.enter()` (`SRV/avatar/network/Session.java:475-…`):

1. `user.loadData()`: `SELECT * FROM players WHERE user_id = ?` (`SRV/avatar/model/User.java:1013-…`) — nạp level, gender, xu (long), lượng, lượng khoá, xèng, clan_id, các chỉ số friendly/crazy/stylish/happy/hunger/star, và hai cột JSON **`chests`** (rương) + **`wearing`** (đồ đang mặc, mỗi phần tử `{id, expired, quantity?}`).
2. Nếu **chưa có dòng `players`** (tài khoản mới): server tự `INSERT INTO players(user_id, level_main, gender, scores) VALUES (?, 1, 0, 0)` rồi gọi lại `enter()` (`SRV/avatar/network/Session.java:516-523`).
3. Cập nhật `players.is_online=1, client_id, ip_address`; `user.initAvatar()`; đăng ký vào `UserManager`; rồi gửi gói `LOGIN_SUCESS` (mục 4.4), kèm các gói phụ: hộp thoại chào mừng `serverDialog` (gói −10, xem `SRV/avatar/service/Service.java:50-62`) và `serverInfo` (gói −8, `Service.java:88-97`).

### 4.4. Gói LOGIN_SUCESS (server → client)

Command **−4** (`LOGIN_SUCESS`, `Cmd.java:98`). Toàn bộ payload do `AvatarService.onLoginSuccess()` ghi (`SRV/avatar/service/AvatarService.java:185-310`), theo đúng thứ tự:

| Thứ tự | Kiểu | Nội dung |
|--------|------|----------|
| 1 | int | user id |
| 2 | byte | số part đang mặc |
| 3 | short × n | id từng part đang mặc |
| 4 | byte | gender |
| 5 | byte | level chính (leverMain) |
| 6 | byte | phần trăm level (leverMainPercen) |
| 7 | int | xu |
| 8 | byte | friendly |
| 9 | byte | crazy — server ghi cứng `10` |
| 10 | byte | stylish — ghi cứng `100` |
| 11 | byte | happy — ghi cứng `100` |
| 12 | byte | `100 - hunger` |
| 13 | int | lượng |
| 14 | byte | star |
| 15 | lặp theo số part mặc | mỗi part: `byte` = 1 + `UTF` chuỗi hạn dùng (`expiredString()`) |
| 16 | short | icon clan (truy vấn `clan_members JOIN clans`, `AvatarService.java:213-233`); −1 nếu chưa có clan |
| 17 | byte | số lệnh menu (listCmd); mỗi lệnh: `UTF` tên + `short` icon |
| 18 | byte | số lệnh menu xoay (listCmdRotate); mỗi lệnh: `short` anthor + `UTF` tên + `short` icon |
| 19 | boolean | isTour — ghi cứng `true` |
| 20 | byte × (số lệnh xoay) | type của từng lệnh xoay |
| 21 | byte | hằng `1` |
| 22 | short | level chính (lần nữa) |
| 23 | short | mã nhãn trạng thái hôn nhân/hẹn hò (−1 nếu không có; 2 / 1153…1157 theo cấp) |
| 24 | boolean | isNewVersion (server luôn trả `true`, `Session.java:… isNewVersion()`) |
| 25 | int | xèng (chỉ khi newVersion = true) |
| 26 | byte | số action tặng hoa/hôn — ghi cứng `4`; mỗi action: `short` id + `UTF` tên + `short` icon + `int` giá + `byte` loại tiền |
| 27 | int | lượng (lần nữa) |
| 28 | int | lượng khoá |
| 29 | byte | hằng `1` |
| 30 | UTF | username |

Sau gói này server còn gửi thêm vài gói text tab (`SendTabmsg`) về sự kiện/nạp thẻ (`AvatarService.java:311-315`).

### 4.5. Tạo nhân vật (tài khoản chưa có nhân vật)

Command **−35** cả hai chiều (`CRE_CHARACTER`, `Cmd.java:73`).

- Client → server (`Session.createCharacter`, `SRV/avatar/network/Session.java:~1030-1062`): đọc `byte` gender (1 = nam, 2 = nữ), `byte` numItem. **Lưu ý:** server bỏ qua danh sách đồ client chọn — nó tự gán bộ khởi đầu cố định theo giới tính: nam `{89, 88, 0, 4, 14}`, nữ `{89, 88, 0, 4, 49}`.
- Server → client: gói −35 với `boolean` true/false báo tạo thành công/thất bại.

Điểm chưa rõ: vị trí chính xác trong luồng client (màn hình nào kích hoạt gửi −35 sau khi đăng nhập tài khoản trống) chưa được truy vết hết trong `LoginScr.cs`/`GlobalMessageHandler.cs` — xem mục 10.

---

## 5. Luồng đăng ký tài khoản

- Client có hai hàm gửi (`CLI/GlobalService.cs:235-262`), đều dùng command **−30** (`REQUEST_REGISTER`, `Cmd.java:75`; `CLI/Cmd_Message.cs:141`):
  - Bản 3 trường: `UTF` username, `UTF` password, `UTF` introductionCode (mã giới thiệu).
  - Bản 2 trường: `UTF` username, `UTF` password.
  - Tham số `email` của bản 3 trường **không được ghi vào gói**.
- Phía server: **không tìm thấy xử lý đăng ký qua socket trong mã nguồn này**:
  - `MessageHandler.onMessage` không có nhánh `case` cho −30 (đã rà toàn bộ switch trong `SRV/avatar/message/MessageHandler.java`).
  - Hàm duy nhất liên quan là `Session.regMessage()` (`SRV/avatar/network/Session.java:990-993`) chỉ đọc username + password rồi **không làm gì thêm**, và không có nơi nào gọi tới nó.
  - `REGISTER_BY_EMAIL = -25` (`Cmd.java:78`) cũng chỉ là hằng số phía server; phía client có nhánh nhận −25 trong `CLI/GlobalMessageHandler.cs:105` nhưng không có luồng gửi tương ứng được truy vết.
- Kết luận cho bản port: đăng ký trong bản gốc này hoặc được làm ngoài socket (web/admin/chèn SQL trực tiếp vào bảng `users`), hoặc nằm ở một bản server khác không có trong Pack5. Khi port phải **tự quyết định** cơ chế đăng ký (ví dụ vẫn nhận gói −30 và tự cài đặt phía server).

---

## 6. Danh sách server / chọn server

Không có API danh sách server động theo nghĩa thông thường — địa chỉ được **gắn cứng trong client**:

- `CLI/GameMidlet.cs` (khởi tạo tĩnh, ~dòng 78-104): `IP[0][0] = { "14.225.213.114" }`, `PORT[0][0] = { 19128 }`; bản English: `IPEng = "14.225.213.114"`, `PORTEng = 19128`, tên hiển thị `"International Server"`, `"Aries City"`; tên server tiếng Việt lấy từ `T.getNameServer()`; `CLIENT_TYPE = 8`.
- Client có thêm cơ chế ghi đè host qua file text trên GitHub raw: `linkGetHost` = hai URL giống hệt nhau `https://raw.githubusercontent.com/HairModNRO/WebRequest/refs/heads/main/thanhphoplay` (`CLI/GameMidlet.cs:~100-101`). **Không tìm thấy nơi gọi tải các URL này** trong thư mục Assembly-CSharp đã dịch ngược (chỉ thấy khai báo) — xem mục 10.
- `CLI/ServerListScr.cs` là màn hình danh sách dựng trên các mảng `GameMidlet.IP/PORT` trên; bên trong nó chỉ có link web phụ (FAQ/cập nhật trỏ `wap.teamobi.com`, `ServerListScr.cs:131-134`), không có lời gọi lấy danh sách server qua HTTP.
- Một "server" ở đây = một tiến trình server Java nghe cổng 19128 với tên thành phố lấy từ `config.properties:4` (`game.city.name`); việc "chọn server" phía client thực chất là chọn cặp IP:port gắn cứng trên.

---

## 7. Vào map và đồng bộ người chơi

Bối cảnh: các lệnh map của handler Avatar dùng họ lệnh "PARK" (50/51/53/54/55). Handler hiện hành phải là Avatar (đã gửi `GET_HANDLER` với byte 8) hoặc handler mặc định — cả hai đều route các lệnh này (`SRV/avatar/message/MessageHandler.java:270-291`; `AvatarMsgHandler` kế thừa và uỷ quyền về `MessageHandler`, `SRV/avatar/message/AvatarMsgHandler.java:14-25`).

Mỗi map có 10 **zone** (khu); một zone giữ tối đa khoảng 10 người chơi theo logic chọn khu tự động (mục 7.1).

### 7.1. Xin vào map (client → server)

Command **50** (`AVATAR_JOIN_PARK`, `Cmd.java:153`), payload do server đọc tại `SRV/avatar/server/ServerManager.java:327-355` (`joinAreaMessage`):

| Thứ tự | Kiểu | Nội dung |
|--------|------|----------|
| 1 | byte | mapId |
| 2 | byte | area (zone id); −1 = tự chọn khu |
| 3 | short | x |
| 4 | short | y |

Nếu payload trống/không đọc được, server dùng mặc định map = 11, area = −1, vị trí hiện tại của user. Khi area < 0, `joinAreaAutoNumber` chọn zone đầu tiên có ≤ 10 người (`ServerManager.java:357-371`).

### 7.2. Server xử lý vào zone

`Zone.enter()` (`SRV/avatar/play/Zone.java:61-79`) theo thứ tự:

1. Nếu user đang ở zone khác → `zone.leave(user)` trước (phát gói rời map, mục 7.5).
2. Phát cho **những người đang có mặt trong zone** gói **51** báo người mới vào (mục 7.4) — qua `MapService.addPlayer`.
3. Gửi gói thời tiết `WEATHER` (−63) với byte 2 cho chính người vào (`Service.java:100-…`, `Zone.java:71`).
4. Gửi cho chính người vào gói **50** chứa trạng thái toàn zone (mục 7.3).
5. Chỉ **sau đó** mới thêm user vào danh sách players của zone (`Zone.java:75`) — hệ quả: danh sách trong gói 50 ở bước 4 **chưa bao gồm chính người vừa vào**, và gói 51 ở bước 2 cũng chỉ tới những người cũ.

### 7.3. Gói trạng thái zone khi vào map (server → client, cmd 50)

Do `AvatarService.enter(Zone)` ghi (`SRV/avatar/service/AvatarService.java:513-…`):

| Thứ tự | Kiểu | Nội dung |
|--------|------|----------|
| 1 | byte | mapId |
| 2 | byte | zoneId |
| 3 | short | −1 |
| 4 | short | −1 |
| 5 | byte | numUser = số người đang có trong zone |
| 6 | lặp numUser | mỗi người: `int` id, `UTF` username, `byte` số part mặc, `short` × part id, `short` x, `short` y, `byte` role (0 = NPC) |
| 7 | byte × numUser | direct (hướng quay) của từng người |
| 8 | byte × numUser | hằng `101` |
| 9 | short × numUser | −1 |
| 10 | byte | 0 |
| 11 | byte | 0 |
| 12 | short | số lượng mapItem (tổng) |
| 13 | byte | số loại mapItemType |
| 14 | lặp theo loại | `byte` typeId, `short` imgID, `byte` iconID, `short` dx, `short` dy, `byte` số vị trí không xuyên qua, mỗi vị trí: `byte` x + `byte` y |
| 15 | byte | số mapItem; mỗi item: `byte` type, `byte` typeID, `byte` x, `byte` y |
| 16 | short × numUser | −1 |

(Lưu ý thực tế đã biết từ kiểm kê Pack5: dữ liệu map_item trong DB rỗng nên các khối 12-15 thường trống/rỗng trong bản gốc này.)

### 7.4. Người mới vào map (server → những người cũ, cmd 51)

`PLAYER_JOIN_PARK = 51` (`Cmd.java:154`), payload tại `SRV/avatar/play/MapService.java:145-166` (`addPlayer`):

`int` userId, `UTF` username, `byte` số part mặc, `short` × part id, `short` x, `short` y, `byte` role, `byte` −1, `short` −1, `short` −1.

### 7.5. Người rời map (server → cả zone, cmd 53)

`PLAYER_LEAVE_PARK = 53` (`Cmd.java:155`), payload chỉ gồm `int` userId (`SRV/avatar/play/MapService.java:38-48`). Phát khi `Zone.leave()` chạy (`Zone.java:81-85`) — gồm các trường hợp: chuyển map/khu, đổi handler (gói `GET_HANDLER` index 8 làm rời zone trước, `Session.java:296-299`), và ngắt kết nối (`Session.close()` → `user.close()`).

### 7.6. Di chuyển

- Client → server: command **54** (`MOVE_PARK`, `Cmd.java:156`): `short` x, `short` y, `byte` direct (`CLI/ParkService.cs:41-54`). Server đọc thêm một `short` phụ nếu payload còn ≥ 2 byte (`SRV/avatar/model/User.java:1354-1373`).
- Tần suất: **theo sự kiện, không có timer cố định** — client gọi `doMove` từ vòng di chuyển của nhân vật khi vị trí/hướng thay đổi (`CLI/Avatar.cs:835`, `1032-1047`). Phía server có cơ chế chống spam chung: bảng `lastActionTimes` với `ACTION_COOLDOWN_MS = 100` ms (`SRV/avatar/network/Session.java:84-85`) — mức độ áp dụng cụ thể cho gói move chưa truy vết tới nơi dùng.
- Server → cả zone (gồm **cả người gửi**, vì `MapService.sendMessage` lặp toàn bộ players của zone, `SRV/avatar/play/MapService.java:181-188`): command **54**, payload: `int` userId, `short` x, `short` y, `byte` direct (`MapService.java:50-63`).

### 7.7. Chat trong map

- Client → server: command **55** (`CHAT_PARK`, `Cmd.java:157`): `UTF` text (`CLI/ParkService.cs:57-70`). Nếu `CLIENT_TYPE == 10` client dùng command 77 (`CHAT_FARM`) thay thế. Server yêu cầu payload còn ≥ 4 byte mới xử lý (`SRV/avatar/model/User.java:1736-1746`).
- Server → cả zone: command **55** với payload `int` userId người nói + `UTF` text (`SRV/avatar/play/MapService.java:65-76`). Ngoại lệ: nếu map id = 22 (farm) server phát bằng command 77 (`CHAT_FARM`) dù đang ở handler Avatar.

### 7.8. Lệnh phụ liên quan map

- `PARK_BOARD_LIST = 60`: client gửi `byte` mapId để hỏi mật độ 10 khu; server trả gói 60: `byte` số khu (=10) + mỗi khu 1 `byte` mật độ (0 = ≥9 người, 1 = ≥4, 2 = <4) (`SRV/avatar/network/Session.java` hàm `doiKhuVuc`; dispatch `MessageHandler.java:307-310`).
- `AVATAR_FEEL = 57`: client gửi `byte` idFeel; server phát lại cho zone gói 57: `int` userId + `byte` idFeel (`MapService.java:132-143`).
- `ACTION = 59`: tương tác giữa hai người; server phát gói 59: `int` userId, `int` idTo, `short` action (+ `UTF`/`short` phụ tuỳ action) (`MapService.java:113-130`).
- `USING_PART = -48`: khi ai đó thay part đang mặc, server phát cho zone: `int` userId + `short` itemId (`MapService.java:168-179`).

---

## 8. Cơ sở dữ liệu

### 8.1. Cấu hình kết nối

`DbManager` đọc file **`database.properties` ở thư mục làm việc** khi khởi tạo (`SRV/avatar/db/DbManager.java:47-57`), các key:

| Key trong code | Dòng | Ghi chú |
|----------------|------|---------|
| `driver` | DbManager.java:50 | class driver JDBC |
| `host` | :51 | |
| `port` | :52 | |
| `dbname` | :53 | |
| `username` | :54 | |
| `passeword` | :55 | ⚠️ **Code đọc key `passeword` (sai chính tả)** trong khi file `database.properties` đi kèm dùng key `password` — khi dựng lại phải khớp một trong hai, nếu không mật khẩu sẽ null. (Giá trị mật khẩu không ghi trong tài liệu này.) |
| `max_connection` | :56 | |
| `min_connection` | :57 | |

Kết nối qua **HikariCP**, JDBC URL `jdbc:mysql://<host>:<port>/<dbname>` (`DbManager.java:69-80`) — tức MySQL/MariaDB.

Cấu hình server (cổng, tên thành phố, đường dẫn res, phiên bản dữ liệu) nằm ở `config.properties` cùng thư mục gốc, được `ServerManager` nạp (`SRV/avatar/server/ServerManager.java:63-72`): `server.port=19128`, `server.active`, `game.city.name`, `game.big.image.version=30314`, `game.part.version=15294`, `game.resources.hd.path=res/hd/`, `game.resources.medium.path=res/medium/` (`SRVROOT/config.properties:1-13`).

### 8.2. Các bảng trong avatar_2x.sql

File `SRVROOT/database/avatar_2x.sql` có **34 bảng**: `atm_check`, `atm_lichsu`, `avatar_img_data`, `betgame`, `chests`, `clans`, `clan_members`, `cpanel`, `dial_lucky`, `farmitems`, `farm_data`, `farm_image_data`, `foods`, `game_rounds`, `giaodich_logs`, `giftcode`, `giftcode_use`, `gioithieu`, `house`, `house_buy`, `house_player_item`, `image_data`, `itemlimited`, `items`, `map_item`, `map_item_type`, `map_item_typem`, `marry`, `napthe`, `npc`, `players`, `settings`, `upgrade_item`, `users` (vị trí `CREATE TABLE`: `users` tại avatar_2x.sql:84650, `players` tại :84215, `items` tại :78388, `clans` tại :1251, `clan_members` tại :1276).

### 8.3. Nhóm bảng tối thiểu để đăng nhập + vào map chạy được

Suy ra trực tiếp từ các truy vấn chạy trên đường đăng nhập/vào map:

| Bảng | Vì sao bắt buộc | Trích dẫn |
|------|------------------|-----------|
| `users` | Đăng nhập kiểm tra username + MD5 password, `active`, `login_lock`, `ban`; các hàm thưởng nạp đọc thêm `tongNap`, `nhanthuongTongNap`, `ThuongNap*` | `User.java:879-885`, `Session.java` (enter/checkThuongNap*) |
| `players` | `loadData()` đọc toàn bộ dòng theo `user_id` (level/gender/xu/lượng/chests JSON/wearing JSON…); tự INSERT khi chưa có; cập nhật `is_online` khi vào | `User.java:1013-…`, `Session.java:516-523` |
| `clans` + `clan_members` | `onLoginSuccess()` chạy JOIN hai bảng này và **ném RuntimeException nếu lỗi SQL** — thiếu bảng sẽ làm gãy luồng sau đăng nhập | `AvatarService.java:213-233` |
| `items` | `PartManager.load()` nạp toàn bộ part từ `SELECT * FROM items` lúc server chạy — wearing/chests chỉ lưu id, mọi metadata part (zorder, giá, hạn…) lấy từ đây | `SRV/avatar/item/PartManager.java:52-58` |
| `farmitems`, `upgrade_item` | Cũng được `PartManager` nạp cùng lúc khởi tạo (`SELECT * FROM farmitems`, `SELECT * FROM upgrade_item`) — cần cho server boot trọn vẹn, không trực tiếp cho map | `PartManager.java:154-190` |

Không cần cho MVP online: toàn bộ bảng farm/house/minigame/napthe/giftcode/marry/atm… Map tĩnh (hình, vật thể) server đọc từ cây `res/` chứ không từ DB (đường dẫn res ở `config.properties:12-13`; và bảng `map_item` trong dump vốn rỗng — đã kiểm kê trước đây). Dữ liệu ảnh part khi client thiếu được server stream từ file trong `res/` qua các lệnh −98/−80 (xem bảng lệnh), cũng không qua DB trên đường vào map.

---

## 9. Bảng command MVP online

Giá trị là byte có dấu. "C→S" = client gửi, "S→C" = server gửi.

| Hằng | Giá trị | Chiều | Dùng để | Nguồn hằng |
|------|---------|-------|---------|------------|
| *(bắt tay)* | −27 | hai chiều | Xin khoá phiên / trả khoá phiên (không có hằng đặt tên; dùng số trực tiếp) | `Session.java:278`, `Session_ME.cs:336` |
| `GET_HANDLER` | −1 | hai chiều | Chọn handler (8 = Avatar) + server xác nhận lại | `Cmd.java:100` |
| `SET_PROVIDER` | −17 | C→S | Thông tin client (provider, màn hình, loại tài nguyên, version) | `Cmd.java:86` |
| `SET_AGENT` | −79 | C→S | Chuỗi agent | `Cmd.java:32` |
| `LOGIN` | −2 | C→S | Đăng nhập: UTF user, UTF pass, UTF version | `Cmd.java:99` |
| `LOGIN_SUCESS` | −4 | S→C | Toàn bộ thông tin nhân vật sau đăng nhập (mục 4.4) | `Cmd.java:98` |
| `SERVER_MESSAGE` | −9 | S→C | Thông báo lỗi/thường (UTF) — gồm báo sai mật khẩu | `Cmd.java:93` |
| `SET_MONEY_ERROR` | −10 | S→C | Hộp thoại server (UTF) — chào mừng, dialog | `Cmd.java:92` |
| `SERVER_INFO` | −8 | S→C | Thông tin server/sự kiện (UTF) | `Cmd.java:94` |
| `REQUEST_REGISTER` | −30 | C→S | Đăng ký (client có gửi; **server bản này không xử lý** — mục 5) | `Cmd.java:75` |
| `CRE_CHARACTER` | −35 | hai chiều | Tạo nhân vật / trả boolean kết quả | `Cmd.java:73` |
| `AVATAR_JOIN_PARK` | 50 | hai chiều | C→S xin vào map (map, area, x, y); S→C trạng thái toàn zone | `Cmd.java:153` |
| `PLAYER_JOIN_PARK` | 51 | S→C | Báo người mới vào zone cho người cũ | `Cmd.java:154` |
| `PLAYER_LEAVE_PARK` | 53 | S→C | Báo người rời zone (int userId) | `Cmd.java:155` |
| `MOVE_PARK` | 54 | hai chiều | Di chuyển: C→S (x, y, direct); S→C phát cả zone (userId, x, y, direct) | `Cmd.java:156` |
| `CHAT_PARK` | 55 | hai chiều | Chat trong map (UTF / userId + UTF) | `Cmd.java:157` |
| `CHAT_FARM` | 77 | hai chiều | Chat khi ở map farm (id 22) / biến thể CLIENT_TYPE 10 | `Cmd.java:196` |
| `PARK_BOARD_LIST` | 60 | hai chiều | Hỏi/trả mật độ 10 khu của một map | `Cmd.java:162` |
| `AVATAR_FEEL` | 57 | hai chiều | Biểu cảm (byte idFeel / userId + idFeel) | `Cmd.java:159` |
| `ACTION` | 59 | hai chiều | Tương tác giữa người chơi | `Cmd.java:161` |
| `USING_PART` | −48 | S→C | Ai đó đổi part đang mặc (userId + partId) | `Cmd.java:62` |
| `WEATHER` | −63 | S→C | Thời tiết khi vào zone (byte) | `Cmd.java:48` |
| `UPDATE_MONEY` | −33 | S→C | Cập nhật tiền (dùng sau các giao dịch; ngoài MVP map nhưng cần cho HUD tiền) | `Cmd.java:74` |
| `REQUEST_IMAGE_PART` | −98 | hai chiều | Client xin ảnh part theo imageId; server trả short id + short len + bytes PNG từ `res/` | `Cmd.java:17`, `Session.java` (`requestImagePart`) |
| `GET_IMG_ICON` | −80 | hai chiều | Tương tự cho ảnh object/icon | `Cmd.java:31` |
| `SERVER_KICK` | −72 | S→C | Server đá kết nối | `Cmd.java:39` |

---

## 10. Điểm chưa chắc chắn / không tìm thấy trong mã nguồn

1. **Đăng ký qua socket không được cài đặt phía server** trong bản nguồn này (mục 5): không có nhánh xử lý −30, `regMessage()` là hàm chết không nơi gọi. Không rõ bản gốc vận hành thật dùng kênh nào để tạo tài khoản (web/admin/SQL tay) — **không tìm thấy trong code**.
2. **Gói `LOGIN_FAIL` (−5)**: có hằng hai phía nhưng không tìm thấy nơi server gửi trong luồng đăng nhập; lỗi đăng nhập hiện đi qua `SERVER_MESSAGE` (−9). Chưa rà soát mọi handler phụ (casino/farm/home) xem có dùng −5 ở bối cảnh khác không.
3. **`linkGetHost` (GitHub raw)**: chỉ thấy khai báo URL ghi đè host trong `GameMidlet.cs`; không tìm thấy đoạn code tải nội dung URL đó trong Assembly-CSharp đã dịch ngược — không khẳng định được định dạng file host đó.
4. **Tần suất gửi gói di chuyển**: chỉ xác định được là theo sự kiện thay đổi vị trí trong `Avatar.cs`; không tìm thấy hằng số throttle (ms) phía client, và nơi áp dụng `ACTION_COOLDOWN_MS = 100` phía server cho từng loại gói chưa được truy vết tới điểm dùng cuối.
5. **Điểm kích hoạt tạo nhân vật (−35) phía client**: đã rõ payload và xử lý phía server, nhưng chưa truy vết chính xác màn hình/luồng client nào gửi −35 sau khi đăng nhập tài khoản chưa có nhân vật (cần đọc kỹ `LoginScr.cs` + `GlobalMessageHandler.cs` nếu muốn port khớp 100% đoạn này).
6. **Định dạng `myWriter/myReader` phía client**: khẳng định big-endian + UTF kiểu Java dựa trên việc hai phía tương thông và phía server dùng `DataInput/OutputStream`; mã nguồn lớp `myWriter/myReader` không nằm trong phần Assembly-CSharp đã liệt kê nên chưa đối chiếu từng hàm.
7. **Gói −86 sau bắt tay**: không có hằng/handler phía server; mục đích (định danh thiết bị chống đa nick?) chỉ là suy đoán từ payload, **chưa có bằng chứng trong code server**.
8. Dòng trích dẫn `Session.java` cho một số hàm (`createCharacter`, `enter`) dựa trên bản đọc từng đoạn của file 2100+ dòng; số dòng tuyệt đối của các hàm này nên đối chiếu lại khi cài đặt nếu cần chính xác đến từng dòng.
