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
///le duong
    public void WEDDING_BIGINHanlder(User user, Message ms) {
        try {
            Message ms1 = new Message(Cmd.WEDDING_BIGIN);
            DataOutputStream ds = ms1.writer();
            ds.writeInt(7);
            ds.writeInt(1);//id girl
            ds.flush();
            user.getZone().getPlayers().forEach(u -> {
                u.session.sendMessage(ms1);
            });
            accceptMarry(user.getId());
            user.setLevelMarry(1
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            ds.writeUTF(me.getUsername());
            ds.writeUTF(me.getUsername() + " muốn kết bạn với bạn.");
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
            ds.writeUTF(me.getUsername());
            ds.writeBoolean(accept);
            ds.writeUTF(accept
                    ? me.getUsername() + " đã đồng ý kết bạn. Hai bạn đã trở thành bạn bè."
                    : me.getUsername() + " đã từ chối lời mời kết bạn.");
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

    public void handleStartFishing(Message ms) {
        try {
            if(!CheckItemAreaFish(460,"bạn phải vé câu cá mập")){
                return;
            }
            if(!CheckItemAreaFish(446,"bạn phải có cần câu vip")){
                return;
            }

            if(this.session.user.AutoFish){
                ByteArrayOutputStream sendFish = new ByteArrayOutputStream();
                try (DataOutputStream dos2 = new DataOutputStream(sendFish)) {

                    dos2.flush();
                    byte[] dataQuangCau = sendFish.toByteArray();
                    ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(this.session);
                    parkMsgHandler1.onMessage(new Message(Cmd.QUANG_CAU, dataQuangCau));
                }
            }



//            if(!CheckItemAreaFish(448,"bạn phải có mồi câu cá")){
//                return;
//            }
//            Item MoiCau = this.session.user.findItemInChests(448);
//            if(MoiCau!=null){
//                this.session.user.removeItemFromChests(MoiCau);
//            }


        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

    private boolean CheckItemAreaFish(int ItemID,String messenger) throws IOException {
        Message response = new Message(Cmd.START_CAU_CA);
        DataOutputStream ds = response.writer();
        boolean isSuccess = true;
        Item item= null;
        if(ItemID == 446){
            item = this.session.user.findItemInWearing(ItemID);
        }else {
            item = this.session.user.findItemInChests(ItemID);
        }
        if(item==null){
            isSuccess = false;
            ds.writeBoolean(isSuccess);
            ds.writeUTF(messenger);
            ds.flush();
            this.sendMessage(response);
            return false;
        }
        ds.writeBoolean(isSuccess);
        ds.writeUTF("");
        ds.flush();
        this.sendMessage(response);
        return true;
    }

    public void handleQuangCau(Message ms) {
        try {

            Item item = this.session.user.findItemInChests(448);
            if(item==null){
                if(this.session.user.AutoFish)
                {
                    Item moi = new Item(448,-1,1);
                    this.session.user.addItemToChests(moi);
                    this.session.user.updateXu(-30);
                    this.session.user.getAvatarService().updateMoney(0);
                }else {
                    this.session.user.getAvatarService().serverDialog("Hết mồi rồi sếp");
                    return;
                }
            }
            this.session.user.removeItemFromChests(item);
            int userID = this.session.user.getId();
            Message response = new Message(Cmd.QUANG_CAU);
            DataOutputStream ds = response.writer();
            ds.writeInt(userID);
            ds.flush();
            this.sendMessage(response);

        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

    public void onStatusFish() {
        try {
            int userID = this.session.user.getId();
            Message ms = new Message(Cmd.STATUS_FISH);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeByte(1);//ca can cau
            ds.flush();
            this.sendMessage(ms);

        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

    public void onCanCau() {
        try {

            //us = UserManager.getInstance().find(this.session.user.getId());
            short idFish = (short) a.getRandomFishID();
            this.session.user.setIdFish(idFish);
            time = 3000;
            if(this.session.user.AutoFish){
                time = 20;
            }
            if(idFish<0)
            {
                time = -1;
                this.session.user.setIdFish(idFish);
            }
            Random random = new Random();
            Message ms = new Message(Cmd.CAN_CAU);
            DataOutputStream ds = ms.writer();
            ds.writeInt(this.session.user.getId());
            ds.writeShort(this.session.user.getIdFish());
            ds.writeShort(time);
            int randomNumber = random.nextInt((12 - 6) + 1) + 4;
            ds.writeByte((byte) randomNumber);
            for (int i = 0; i < randomNumber; i++) {
                int randomIndex = random.nextInt(a.images.length);
                byte[] randomImage = a.images[randomIndex];
                ds.writeShort(randomImage.length);
                ds.write(randomImage);
            }
            ds.flush();
            this.sendMessage(ms);

        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

    public void CauThanhCong() {
        try {

            int userID = this.session.user.getId();
            Message ms = new Message(Cmd.CAU_THANH_CONG);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeShort(this.session.user.getIdFish());
            int IDFISH = this.session.user.getIdFish();
            if(IDFISH>0){
                Item item = new Item(IDFISH,-1,1);
                this.session.user.addItemToChests(item);
                this.session.user.getAvatarService().sellFish(this.session.user,item.getId());
                if(IDFISH == 457) {
                    Item keoAcMa = new Item(6822,-1,1);
                    if(this.session.user.findItemInChests(6822) !=null){
                        int quantity = this.session.user.findItemInChests(6822).getQuantity();
                        this.session.user.findItemInChests(6822).setQuantity(quantity+1);
                    }else {
                        this.session.user.addItemToChests(keoAcMa);
                    }
                    UserManager.users.forEach(user -> {
                        user.getAvatarService().serverInfo("Chúc mừng bạn : " + this.session.user.getUsername()+" đã câu được 1 Cá Mập");
                    });
                    Utils.writeLogCaMap(this.session.user,"bú 1 cá mập");
                }
                addVatPhamSuKienFish(this.session.user);
            }
            ds.flush();
            this.sendMessage(ms);

            if(this.session.user.AutoFish)
            {
                CauCaXong();
                ByteArrayOutputStream sendFish = new ByteArrayOutputStream();
                try (DataOutputStream dos2 = new DataOutputStream(sendFish)) {

                    dos2.flush();
                    byte[] dataQuangCau = sendFish.toByteArray();
                    ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(this.session);
                    parkMsgHandler1.onMessage(new Message(Cmd.QUANG_CAU, dataQuangCau));
                }
            }
        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

    private void addVatPhamSuKienFish(User us) throws IOException {

        if(this.session.user.getCrazy() >=1000){
            return;
        }
        int ok =  (Utils.nextInt(100) < 70) ? 1 : 0;
        if(ok==1){

            RandomCollection<Integer> chosenItemCollection = chooseItemCollection();
            int idItems = chosenItemCollection.next();
            Item Nro = new Item(idItems,-1,1);
            if(us.findItemInChests(idItems) !=null){
                int quantity = us.findItemInChests(idItems).getQuantity();
                us.findItemInChests(idItems).setQuantity(quantity+1);
            }else {
                us.addItemToChests(Nro);
                us.updateCrazy(+1);
            }
            us.getAvatarService().SendTabmsg("Bạn vừa nhận được 1 "+ " " + Nro.getPart().getName());

        }
    }

    private RandomCollection<Integer> chooseItemCollection() {
        RandomCollection<RandomCollection<Integer>> itemCollections = new RandomCollection<>();
        itemCollections.add(100, randomItemList1);
        return itemCollections.next();
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
            logger.error("handleStartFishing() ", ex);
        }
    }


    public void CauCaXong() {
        try {
            int userID = this.session.user.getId();
            Message ms = new Message(Cmd.CAU_CA_XONG);
            int IDFISH = this.session.user.getIdFish();
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeInt(IDFISH);
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException ex) {
            logger.error("handleStartFishing() ", ex);
        }
    }

}