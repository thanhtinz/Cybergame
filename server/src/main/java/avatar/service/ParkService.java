package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.lib.RandomCollection;
import avatar.lucky.GiftBox;
import avatar.message.ParkMsgHandler;
import avatar.model.Fish;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.UserManager;
import avatar.server.Utils;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public class ParkService extends Service {
    private final RandomCollection<Integer> randomItemList1 = new RandomCollection<>();
    short time;
    private static final Logger logger = Logger.getLogger(AvatarService.class);
    private static final Fish a = new Fish();
    public ParkService(Session cl) {
        super(cl);
        randomItemList1.add(80, 2383);//nro
        randomItemList1.add(20, 2384);
    }
///le duong — [PORT hoàn thiện từ stub fan hardcode {7,1}]:
    // người gửi phải có cặp trong bảng marry, bạn đời phải đang
    // online cùng khu; lần đầu làm lễ thì marry.level 0 -> 1.
    public void WEDDING_BIGINHanlder(User user, Message ms) {
        try {
            if (user.getZone() == null) return;
            int[] couple = findCouple(user.getId());
            if (couple == null) {
                user.getAvatarService().serverDialog(
                    "Bạn chưa hẹn hò với ai — hãy hẹn hò trước khi làm lễ cưới.");
                return;
            }
            int idNam = couple[0], idNu = couple[1], level = couple[2];
            int partnerId = user.getId() == idNam ? idNu : idNam;
            User partner = null;
            for (User u : user.getZone().getPlayers()) {
                if (u.getId() == partnerId) { partner = u; break; }
            }
            if (partner == null) {
                user.getAvatarService().serverDialog(
                    "Bạn đời của bạn chưa tới lễ đường này.");
                return;
            }
            if (level < 1) {
                accceptMarry(idNam);
                user.setLevelMarry(1);
                partner.setLevelMarry(1);
            }
            Message ms1 = new Message(Cmd.WEDDING_BIGIN);
            DataOutputStream ds = ms1.writer();
            ds.writeInt(idNam);
            ds.writeInt(idNu);
            ds.flush();
            // gửi từng người, bỏ qua phần tử session == null (NPC
            // phía server cũng nằm trong getPlayers() — lambda
            // forEach của stub gốc chết NPE ở NPC nên không ai nhận)
            for (User u : user.getZone().getPlayers()) {
                try {
                    u.session.sendMessage(ms1);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Tra cặp marry của một user: {idNam, idNu, level} hoặc null. */
    public static int[] findCouple(int userId) {
        String q = "SELECT idNam, idNu, level FROM marry WHERE idNam = ? OR idNu = ? LIMIT 1";
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)};
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void accceptMarry(int IDuserNam){

        String updateQuery = "UPDATE marry SET level = ?, perLevel = ? WHERE idNam = ?";
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement psUpdate = conn.prepareStatement(updateQuery)) {
            psUpdate.setInt(1, 1);
            psUpdate.setInt(2, 0);
            psUpdate.setInt(3, IDuserNam);
            psUpdate.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    /** Lời mời kết bạn đang chờ, khoá "idNgườiMời:idNgườiNhận" — chỉ lời
     *  mời có thật mới được trả lời (chống giả mạo gói -19). */
    private static final java.util.Set<String> pendingFriendReq =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    static boolean isFriend(int a, int b) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM `friends` WHERE `user_id` = ? AND `friend_id` = ?")) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static void addFriendship(int a, int b) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO `friends` (`user_id`, `friend_id`) VALUES (?, ?), (?, ?)")) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            ps.setInt(3, b);
            ps.setInt(4, a);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void removeFriendship(int a, int b) {
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM `friends` WHERE (`user_id` = ? AND `friend_id` = ?)"
                             + " OR (`user_id` = ? AND `friend_id` = ?)")) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            ps.setInt(3, b);
            ps.setInt(4, a);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Lời mời kết bạn (gói -21 từ người mời): {int id người nhận}.
     *  Chuyển tới người nhận bằng đúng gói -21 phía nhận của client gốc:
     *  {int id người mời, UTF tên người mời, UTF nội dung} —
     *  GlobalMessageHandler case -21 → MessageScr hiện "Lời mời kết bạn"
     *  trong hộp Tin đến kèm nút Đồng ý/Từ chối. (Server Pack5 không hề
     *  gửi gói này; nó gửi -19 sai định dạng — bản port làm đúng phía
     *  client gốc.) */
    public void handleAddFriendRequest(Message ms) {
        try {
            int userId = ms.reader().readInt(); // id người nhận
            User me = this.session.user;
            if (userId == me.getId()) {
                return;
            }
            User user = UserManager.getInstance().find(userId);
            if (user == null || user.session == null) {
                return;
            }
            if (isFriend(me.getId(), userId)) {
                me.getAvatarService().serverDialog("Hai bạn đã là bạn bè rồi.");
                return;
            }
            pendingFriendReq.add(me.getId() + ":" + userId);
            Message out = new Message(Cmd.AVATAR_REQUEST_ADD_FRIEND);
            DataOutputStream ds = out.writer();
            ds.writeInt(me.getId());
            ds.writeUTF(me.getDisplayName());
            ds.writeUTF(me.getDisplayName() + " muốn kết bạn với bạn.");
            ds.flush();
            user.session.sendMessage(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Trả lời lời mời kết bạn (gói -19 từ người nhận): {int id người mời,
     *  boolean đồng ý}. Đồng ý thì lưu quan hệ bạn vào bảng `friends`
     *  (2 chiều). Kết quả trả về người mời bằng gói -19 phía nhận của
     *  client gốc: {int id người trả lời, UTF tên, boolean, UTF nội dung}
     *  → client hiện dialog nội dung. */
    public void handleAddFriendReply(Message ms) {
        try {
            int requesterId = ms.reader().readInt();
            boolean accept = ms.reader().readBoolean();
            User me = this.session.user;
            if (!pendingFriendReq.remove(requesterId + ":" + me.getId())) {
                return; // không có lời mời đang chờ — bỏ qua
            }
            if (accept) {
                addFriendship(me.getId(), requesterId);
            }
            User requester = UserManager.getInstance().find(requesterId);
            if (requester == null || requester.session == null) {
                return;
            }
            Message out = new Message(Cmd.ADD_FRIEND);
            DataOutputStream ds = out.writer();
            ds.writeInt(me.getId());
            ds.writeUTF(me.getDisplayName());
            ds.writeBoolean(accept);
            ds.writeUTF(accept
                    ? me.getDisplayName() + " đã đồng ý kết bạn. Hai bạn đã trở thành bạn bè."
                    : me.getDisplayName() + " đã từ chối lời mời kết bạn.");
            ds.flush();
            requester.session.sendMessage(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Xoá bạn (gói -20): {int id bạn} — xoá quan hệ cả 2 chiều trong DB.
     *  Client gốc có doRemoveFriend nhưng server Pack5 không có handler. */
    public void handleRemoveFriend(Message ms) {
        try {
            int friendId = ms.reader().readInt();
            removeFriendship(this.session.user.getId(), friendId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ================= CÂU CÁ (port theo gốc, docs/minigame-spec.md §1) =====
    // [PORT] Các sửa so với bản fan: vé cá mập 460 chỉ bắt buộc ở map
    // server 16 (client 17); quăng câu/84/85 PHÁT CẢ KHU như client gốc
    // mong đợi; cá cắn được hẹn bằng executor thay vì Thread.sleep trong
    // handler; gói 84 được CHẤM THẬT theo chuỗi phím client gửi lên (fan
    // không đọc payload); bỏ AutoFish (bot tự mua mồi/tự câu), bỏ bán
    // tự động + quà sự kiện khi câu trúng — cá vào rương, người chơi tự
    // bán ở NPC Thợ câu.

    /** Bộ hẹn giờ cá cắn dùng chung (thread nền, không chặn handler). */
    private static final java.util.concurrent.ScheduledExecutorService FISH_TIMER =
            java.util.concurrent.Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "fish-timer");
                t.setDaemon(true);
                return t;
            });

    /** Pad kỳ vọng suy từ chỉ số ảnh mũi tên (đã XEM TỪNG ẢNH kiểm
     *  chứng 2026-11-03): ảnh theo chỉ số = [→, ↑, ←, ←, ↓, ←, →, →,
     *  ←, →] — ảnh 2 và 3 TRÙNG NHAU từng byte trong dữ liệu fan (đều
     *  là mũi tên trái). HDPaint quy ước: phải=3, lên=2, trái=1,
     *  xuống=4. */
    private static final byte[] FISH_IMG_PAD = {3, 2, 1, 1, 4, 1, 3, 3, 1, 3};

    /** Gửi gói cho mọi người trong khu hiện tại của user này. */
    private void fishBroadcast(Message ms) {
        try {
            if (this.session.user.getZone() != null) {
                this.session.user.getZone().getPlayers().forEach(u -> {
                    try {
                        u.session.sendMessage(ms);
                    } catch (Exception ignored) {
                    }
                });
            } else {
                this.sendMessage(ms);
            }
        } catch (Exception ex) {
            logger.error("fishBroadcast() ", ex);
        }
    }

    /** Cần câu (cả 3 loại của shop gốc đều đeo được, zorder 70). */
    private static final int[] FISH_RODS = {442, 445, 446};
    /** Mồi dùng được, theo thứ tự ưu tiên trừ (rẻ trước). */
    private static final int[] FISH_BAITS = {443, 447, 448};
    /** Vé theo map server: 14 = cá rô (458), 15 = cá lóc (459),
     *  16 = cá mập (460). */
    private static int fishTicketForMap(int mapId) {
        switch (mapId) {
            case 14: return 458;
            case 15: return 459;
            case 16: return 460;
            default: return -1;
        }
    }

    public void handleStartFishing(Message ms) {
        try {
            // Cần câu phải đang ĐEO (đúng gốc: findItemInWearing) —
            // cả 3 loại cần của shop đều được [PORT, fan chỉ nhận 446].
            boolean hasRod = false;
            for (int rod : FISH_RODS) {
                if (this.session.user.findItemInWearing(rod) != null) {
                    hasRod = true;
                    break;
                }
            }
            if (!hasRod) {
                fishStartReply(false, "bạn phải có cần câu");
                return;
            }
            // [PORT] Mỗi bãi câu cần vé riêng của nó trong rương.
            if (this.session.user.getZone() != null
                    && this.session.user.getZone().getMap() != null) {
                int mapId = this.session.user.getZone().getMap().getId();
                int ticket = fishTicketForMap(mapId);
                if (ticket > 0
                        && this.session.user.findItemInChests(ticket) == null) {
                    String name = ticket == 458 ? "vé câu cá rô"
                        : ticket == 459 ? "vé câu cá lóc" : "vé câu cá mập";
                    fishStartReply(false, "bạn phải có " + name);
                    return;
                }
            }
            fishStartReply(true, "");
        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

    private void fishStartReply(boolean ok, String err) throws IOException {
        Message response = new Message(Cmd.START_CAU_CA);
        DataOutputStream ds = response.writer();
        ds.writeBoolean(ok);
        ds.writeUTF(err);
        ds.flush();
        this.sendMessage(response);
    }

    public void handleQuangCau(Message ms) {
        try {
            User us = this.session.user;
            // Mồi: dùng loại rẻ nhất đang có (443 cơm, 447 trùng,
            // 448 trứng kiến) — trừ ĐÚNG 1 (removeItemFromChests của
            // fan xoá CẢ chồng, phải dùng removeItem(id, 1)).
            Item bait = null;
            for (int b : FISH_BAITS) {
                bait = us.findItemInChests(b);
                if (bait != null) break;
            }
            if (bait == null) {
                us.getAvatarService().serverDialog("Hết mồi rồi sếp");
                return;
            }
            us.removeItem(bait.getId(), 1);
            // Hủy hẹn cắn của lần quăng trước (nếu còn) bằng số thế hệ.
            final int castGen = us.nextFishCastGen();
            us.setIdFish((short) -1);
            us.setFishExpect(null);

            Message response = new Message(Cmd.QUANG_CAU);
            DataOutputStream ds = response.writer();
            ds.writeInt(us.getId());
            ds.flush();
            fishBroadcast(response);

            // Cá cắn sau 8–15 giây (gốc fan: 12–18s bằng sleep — giữ
            // khoảng chờ tương đương nhưng không chặn luồng xử lý).
            long delay = 8000 + new Random().nextInt(7000);
            final Session sess = this.session;
            FISH_TIMER.schedule(() -> {
                try {
                    if (sess.user == null || sess.user.getFishCastGen() != castGen) {
                        return; // đã quăng lại / đã xong / đã thoát
                    }
                    new ParkService(sess).onCanCau();
                } catch (Exception ignored) {
                }
            }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (IOException ex) {
            logger.error("handleQuangCau() ", ex);
        }
    }

    public void onCanCau() {
        try {
            User us = this.session.user;
            short idFish = (short) a.getRandomFishPositiveID();
            us.setIdFish(idFish);
            Random random = new Random();
            int n = random.nextInt((12 - 6) + 1) + 4;
            byte[] expect = new byte[n];
            Message ms = new Message(Cmd.CAN_CAU);
            DataOutputStream ds = ms.writer();
            ds.writeInt(us.getId());
            ds.writeShort(idFish);
            ds.writeShort(3000);
            ds.writeByte((byte) n);
            for (int i = 0; i < n; i++) {
                int idx = random.nextInt(a.images.length);
                expect[i] = FISH_IMG_PAD[idx];
                byte[] img = a.images[idx];
                ds.writeShort(img.length);
                ds.write(img);
            }
            ds.flush();
            us.setFishExpect(expect);
            this.sendMessage(ms);
        } catch (IOException ex) {
            logger.error("onCanCau() ", ex);
        }
    }

    /** C→S 84: {bool, byte n, n byte phím}. Chấm theo chuỗi đã sinh ở
     *  onCanCau; trúng thì cá vào rương, sai/thiếu thì broadcast −1. */
    public void handleFinishFishing(Message ms) {
        try {
            User us = this.session.user;
            byte[] expect = us.getFishExpect();
            if (expect == null) {
                return; // chưa có cá cắn — bỏ qua gói lạc
            }
            boolean ok = true;
            try {
                ms.reader().readBoolean();
                int n = ms.reader().readByte();
                if (n != expect.length) {
                    ok = false;
                }
                for (int i = 0; i < n; i++) {
                    byte pad = ms.reader().readByte();
                    if (i < expect.length && pad != expect[i]) {
                        ok = false;
                    }
                }
            } catch (Exception e) {
                ok = false;
            }
            short result = ok ? us.getIdFish() : (short) -1;
            us.setFishExpect(null);
            us.nextFishCastGen(); // hủy mọi hẹn còn treo của lượt này

            Message out = new Message(Cmd.CAU_THANH_CONG);
            DataOutputStream ds = out.writer();
            ds.writeInt(us.getId());
            ds.writeShort(result);
            ds.flush();
            fishBroadcast(out);

            if (result > 0) {
                us.addItemToChests(new Item(result, -1, 1));
                if (result == 457) {
                    UserManager.users.forEach(u -> {
                        try {
                            u.getAvatarService().serverInfo("Chúc mừng bạn : "
                                    + us.getUsername() + " đã câu được 1 Cá Mập");
                        } catch (Exception ignored) {
                        }
                    });
                    Utils.writeLogCaMap(us, "cau duoc 1 ca map");
                }
            }
        } catch (IOException ex) {
            logger.error("handleFinishFishing() ", ex);
        }
    }

    public void CauCaXong() {
        try {
            User us = this.session.user;
            us.setFishExpect(null);
            us.nextFishCastGen();
            Message ms = new Message(Cmd.CAU_CA_XONG);
            DataOutputStream ds = ms.writer();
            ds.writeInt(us.getId());
            ds.flush();
            fishBroadcast(ms);
        } catch (IOException ex) {
            logger.error("CauCaXong() ", ex);
        }
    }

    public void onStatusFish() {
        try {
            int userID = this.session.user.getId();
            Message ms = new Message(Cmd.STATUS_FISH);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeByte(1);
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException ex) {
            logger.error("onStatusFish() ", ex);
        }
    }

    public void onInfoFish() {
        try {
            Message ms = new Message(Cmd.INFO_FISH);
            DataOutputStream ds = ms.writer();
            ds.writeInt(this.session.user.getId());
            ds.writeByte(1);
            ds.writeByte(1);
            ds.writeInt(1);
            ds.writeShort(457);
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException ex) {
            logger.error("onInfoFish() ", ex);
        }
    }
}
