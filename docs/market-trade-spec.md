# Chợ người chơi + Giao dịch trực tiếp — đặc tả port theo bản chính 3.4.4

Nguồn: audit A4 + metadata IL2CPP 3.4.4 (`MarketScr`, `MarketMsgHandler`,
`MarketService` chỉ có `send`, `TradingScr` với `listTradeMe`/
`listTradeOrther`/`isLockMe`/`isLockOrther`/`cmd_Lock`/`cmd_Accept`/
`cmd_Trade`, `SubContain` {idIndex, list, type}) + Cmd_Message bản chính:
**MARKET = 127** là handler chung của chợ + giao dịch (cùng giá trị 127:
TRADE_ITEM, ITEM_TRADING, LOCK_TRADE, CANCEL_TRADE, SUB_CONTAINER,
SEND_SUB_CONTAIN, CHAT_ANIM). Bản chính không công khai định dạng gói
con → toàn bộ gói con dưới đây là **[PORT]** theo đúng mô hình TradingScr.

## Phong bì: gói 127, byte đầu = sub

### Chợ (Market)
| sub | chiều | nội dung |
|---|---|---|
| 0 LIST | C→S {byte page} | xin danh sách tin bán (20 tin/trang, mới nhất trước) |
| 0 LIST | S→C {byte page, byte totalPages, byte n, mỗi tin: int listingId, int sellerId, UTF sellerName, short partId, int qty, int priceXu, long expireAt (0 = vĩnh viễn)} | |
| 1 SELL | C→S {short partId, int qty, int priceXu} | đăng bán: trừ item khỏi rương (theo hạn dùng của bản ghi khớp đầu tiên) |
| 2 BUY | C→S {int listingId} | mua: trừ xu người mua, đồ vào rương người mua, người bán nhận xu − thuế 5% khi đăng nhập lần sau (cộng ngay nếu online) |
| 3 CANCEL | C→S {int listingId} | gỡ tin của mình, đồ trả về rương |
| 4 MY_LIST | C→S {} → S→C cùng định dạng LIST với page=0 | tin của tôi |
| 5 RESULT | S→C {bool ok, UTF msg} | kết quả thao tác |
| 6 CHEST | C→S {} → S→C {byte n, mỗi món: short partId, int qty, long expireAt} | rương của mình (gộp theo part + hạn dùng) — dùng cho Đăng bán và chọn đồ Giao dịch |

Bảng DB mới `market_listings (id PK AI, seller_id, part_id, qty,
price_xu, expire_at BIGINT (hạn dùng của món, -1 vĩnh viễn), created_at)`.
Thuế chợ 5% [PORT]. Giới hạn 10 tin/người [PORT]. Giá 100 – 999.999.999 xu.

Điểm vào [PORT]: NPC 850 Lái Buôn → menu cục bộ thêm "Chợ người chơi".

### Giao dịch trực tiếp (Trading)
| sub | chiều | nội dung |
|---|---|---|
| 10 INVITE | C→S {int targetId} | mời người cùng khu |
| 11 INVITE_IN | S→C {int fromId, UTF fromName} | lời mời tới (hạn 60s) |
| 12 REPLY | C→S {int fromId, bool accept} | đồng ý → cả hai nhận OPEN |
| 13 OPEN | S→C {int otherId, UTF otherName} | mở màn giao dịch |
| 14 SET_ITEMS | C→S {byte n, mỗi món: short partId, int qty, long expireAt, int xu} | cập nhật phần của mình (đồ + xu) — server phát lại cho đối tác bằng sub 15 |
| 15 PARTNER_SET | S→C {byte n, mỗi món như trên, int xu} | phần của đối tác |
| 16 LOCK | C→S {bool lock} | khoá/mở khoá phần của mình → S→C 17 {byte who (0 mình/1 đối tác), bool lock} cả hai |
| 18 ACCEPT | C→S {} | chỉ khi CẢ HAI đã khoá; cả hai cùng ACCEPT → thực hiện trao đổi nguyên tử → S→C 19 {bool ok, UTF msg} |
| 20 CANCEL | C→S {} / S→C {UTF msg} | huỷ — đồ giữ nguyên (chưa trừ cho tới khi ACCEPT đủ hai phía) |
| 21 CHAT | C→S {UTF} → S→C {UTF fromName, UTF text} | chat trong giao dịch |

An toàn: item chỉ bị trừ khỏi rương tại thời điểm hoàn tất (cả hai
ACCEPT sau khi cả hai LOCK); server kiểm lại số lượng/hạn dùng tại
thời điểm đó; xu kiểm số dư tại thời điểm hoàn tất. Một người chỉ có
một phiên giao dịch tại một thời điểm; rời map/ngắt kết nối = huỷ.

Điểm vào [PORT]: panel thông tin người chơi khác (chạm vào người chơi
khác) thêm nút "Giao dịch".

## Client
- Panel Chợ 920×600, 3 tab: **Chợ** (danh sách + phân trang + nút Mua),
  **Đăng bán** (chọn đồ trong rương + nhập số lượng + giá → Đăng),
  **Tin của tôi** (danh sách + nút Gỡ). Giá chỉ hiện trong panel chợ —
  đây là chợ người chơi tự đặt giá, không phải cửa hàng NPC (luật hoá
  đơn Xem áp cho shop NPC; chợ hiện giá trực tiếp theo đúng bản chất chợ).
- Màn Giao dịch 920×600: hai cột (Bạn / Đối tác), mỗi cột lưới đồ +
  dòng xu; nút Khoá (đen/cam khi đã khoá), Chấp nhận (chỉ bật khi cả
  hai khoá), Huỷ (đỏ); ô chat nhỏ phía dưới.
- HUD ẩn khi mở chợ/giao dịch theo luật mini game (thêm vào
  inMiniGameScreen: marketOpen || tradeOpen).
