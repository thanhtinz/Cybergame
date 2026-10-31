package avatar.service;
import java.io.*;
import java.lang.reflect.Field;
import avatar.common.BossShopItem;
import avatar.db.DbManager;
import avatar.handler.BossShopHandler;
import avatar.handler.ShopEventHandler;
import avatar.item.Item;
import avatar.item.PartManager;
import avatar.item.Part;
import avatar.lucky.DialLuckyManager;
import avatar.message.MessageHandler;
import avatar.message.ParkMsgHandler;
import avatar.model.*;
import avatar.server.Avatar;
import avatar.server.ServerManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import avatar.constants.Cmd;
import avatar.network.Message;
import avatar.network.Session;
import avatar.play.Map;
import avatar.play.Zone;
import avatar.server.UserManager;
import lombok.Builder;
import org.apache.log4j.Logger;

import static avatar.constants.NpcName.Chay_To_Win;
import static avatar.constants.NpcName.Pay_To_Win;

public class AvatarService extends Service {

    private static final Logger logger = Logger.getLogger(AvatarService.class);
    private static final java.util.Map<Integer, Long> lastActionTimes = new HashMap<>();
    private static final long ACTION_COOLDOWN_MS = 50; // 2 giây cooldown
    public AvatarService(Session cl) {
        super(cl);
    }

    public User user;
    public void openUIShop(int id, String name, List<Item> items) {
        try {
            System.out.println("openShop lent: " + items.size());
            Message ms = new Message(Cmd.OPEN_SHOP);
            DataOutputStream ds = ms.writer();
            ds.writeByte(id);
            ds.writeUTF(name);
            ds.writeShort(items.size());
            for (Item i : items) {
                ds.writeShort(i.getId());
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("doRequestExpicePet ", ex);
        }
    }

    public void openUIShopEvent(BossShop bossShop, List<BossShopItem> items) {
        try {
            System.out.println("openShop bossShop: " + items.size());
            Message ms = new Message(Cmd.BOSS_SHOP);
            DataOutputStream ds = ms.writer();
            ds.writeByte(bossShop.getTypeShop());
            ds.writeInt(bossShop.getIdBoss());
            ds.writeByte(bossShop.getIdShop());
            ds.writeUTF(bossShop.getName());
            ds.writeShort(items.size());
            for (BossShopItem item : items) {
                ds.writeShort(item.getItemRequest());
                ds.writeUTF(item.initDialog(bossShop));
                if (bossShop.getTypeShop() == 1) {
                    ds.writeUTF(item.initDialog(bossShop));
                }
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("doRequestExpicePet ", ex);
        }
    }

    public void openUIBossShop(BossShop bossShop, List<BossShopItem> items) {
        try {
            System.out.println("openShop bossShop: " + items.size());
            Message ms = new Message(Cmd.BOSS_SHOP);
            DataOutputStream ds = ms.writer();
            ds.writeByte(bossShop.getTypeShop());
            ds.writeInt(bossShop.getIdBoss());
            ds.writeByte(bossShop.getIdShop());
            ds.writeUTF(bossShop.getName());
            ds.writeShort(items.size());
            for (BossShopItem item : items) {
                ds.writeShort(item.getItemRequest());
                ds.writeUTF(item.initDialog(bossShop));
                if (bossShop.getTypeShop() == 1) {
                    ds.writeUTF(item.initDialog(bossShop));
                }
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("doRequestExpicePet ", ex);
        }
    }

    public void doRequestExpicePet(Message mss) {
        try {
            int userID = mss.reader().readInt();
            Message ms = new Message(Cmd.REQUEST_EXPICE_PET);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeByte(0);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("doRequestExpicePet ", ex);
        }
    }

    public void showUICreateChar(byte type) {
        try {
            Message ms = new Message(Cmd.CREATE_CHAR_INFO);
            DataOutputStream ds = ms.writer();
            ds.writeByte(type);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("showUICreateChar ", ex);
        }
    }

    public void viewChest(List<Item> chests) {
        try {
            Message ms = new Message(Cmd.CONTAINER);
            DataOutputStream ds = ms.writer();
            ds.writeShort(chests.size());
            for (Item item : chests) {
                ds.writeShort(item.getId());
                ds.writeByte(100 - item.reliability());
                ds.writeUTF(item.expiredString());
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            logger.error("viewChest ", e);
        }
    }

    public void chatTo(String sender, String content,int type) {
        try {
            Message ms = new Message(Cmd.CHAT_TO);
            DataOutputStream ds = ms.writer();
            ds.writeInt(type);
            ds.writeUTF(sender);
            ds.writeUTF(content);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("chatTo ", ex);
        }
    }

    public void chatToUser(Message ms) {
        try {
            int receiverId = ms.reader().readInt();
            String content = ms.reader().readUTF();
            int senderId = this.session.user.getId(); // ID của người gửi yêu cầu
            User receiver = UserManager.getInstance().find(receiverId);
            User sender = UserManager.getInstance().find(receiverId);
            receiver.getAvatarService().chatTo(sender.getUsername(), content,1);
        } catch (IOException ex) {
            logger.error("chatTo ", ex);
        }
    }

    public void onLoginSuccess() {
        try {
            User us = session.user;
            List<Item> wearing = us.getWearing();
            List<Command> listCmd = us.getListCmd();
            List<Command> listCmdRotate = us.getListCmdRotate();
            Message ms5 = new Message(Cmd.LOGIN_SUCESS);
            DataOutputStream ds = ms5.writer();
            ds.writeInt(us.getId());
            ds.writeByte(wearing.size());
            for (Item itm : wearing) {
                ds.writeShort(itm.getId());
            }
            ds.writeByte(us.getGender());
            ds.writeByte(us.getLeverMain());
            ds.writeByte(us.getLeverMainPercen());
            ds.writeInt(Math.toIntExact(us.getXu()));
            ds.writeByte(us.getFriendly());
            ds.writeByte(10);//us.getCrazy()
            ds.writeByte(100);//us.getStylish()
            ds.writeByte(100);//us.getHappy()
            ds.writeByte(100 - us.getHunger());
            ds.writeInt(us.getLuong());
            ds.writeByte(us.getStar());
            for (Item itm : wearing) {
                ds.writeByte(1);
                ds.writeUTF(itm.expiredString());
            }
            String sql = "SELECT c.icon, c.description FROM clan_members cm JOIN clans c ON cm.clan_id = c.id WHERE cm.user_id = ? AND cm.accept = 1";
            try (Connection connection = DbManager.getInstance().getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, us.getId());  // Giả sử us.getId() trả về ID của user hiện tại
                try (ResultSet res = ps.executeQuery()) {
                    if (res.next()) {
                        // Nếu người dùng tham gia vào một clan, lấy thông tin
                        short icon = res.getShort("icon");
                        String thongbaonhom = res.getString("description");
                        ds.writeShort(icon);  // Ghi ID icon của clan vào DataOutputStream
                        us.getAvatarService().SendTabmsg("Thông báo nhóm: " + thongbaonhom);  // Gửi thông báo nhóm
                    } else {
                        // Nếu không có kết quả (người dùng không tham gia clan nào)
                        ds.writeShort((short) -1);  // Ghi giá trị mặc định -1 cho icon
                        us.getAvatarService().SendTabmsg("Bạn chưa tham gia vào nhóm nào.");  // Gửi thông báo mặc định
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);  // Xử lý lỗi SQL nếu có
            }



            ds.writeByte(listCmd.size());
            for (Command cmd : listCmd) {
                ds.writeUTF(cmd.getName());
                ds.writeShort(cmd.getIcon());
            }
            ds.writeByte(listCmdRotate.size());
            for (Command cmd : listCmdRotate) {
                ds.writeShort(cmd.getAnthor());
                ds.writeUTF(cmd.getName());
                ds.writeShort(cmd.getIcon());
            }
            ds.writeBoolean(true);// isTour
            for (Command cmd : listCmdRotate) {
                ds.writeByte(cmd.getType());
            }
            ds.writeByte(1);
            ds.writeShort(us.getLeverMain());

            //hẹn hò
            if(us.getIdUsHenHo() !=0&&us.getLevelMarry() ==0){
                ds.writeShort(2);
                us.setTenNhan("Cặp đôi hẹn hò");
                us.setImginfo(1114);
            } else if (us.getLevelMarry() > 0 && us.getLevelMarry()<5) {
                ds.writeShort(1153);
                us.setTenNhan("Cặp đôi mới cưới");
                us.setImginfo(1106);
            } else if (us.getLevelMarry() > 4 && us.getLevelMarry()<10) {
                ds.writeShort(1154);
                us.setTenNhan("Cặp đôi gì đó lv hơn 5 dưới 10");
                us.setImginfo(1107);
            } else if (us.getLevelMarry() > 9 && us.getLevelMarry()<15) {
                ds.writeShort(1155);
                us.setTenNhan("Cặp đôi gì đó lv hơn 10 dưới 15");
                us.setImginfo(1108);
            }else if (us.getLevelMarry() > 14 && us.getLevelMarry()<20) {
                ds.writeShort(1156);
                us.setTenNhan("Cặp đôi gì đó lv hơn 15 dưới 20");
                us.setImginfo(1109);
            } else if (us.getLevelMarry() > 19 && us.getLevelMarry()<24) {
                ds.writeShort(1157);
                us.setTenNhan("Cặp đôi gì đó lv hơn 5 dưới 10");
                us.setImginfo(1110);
            }else
                ds.writeShort(-1);

            ds.writeBoolean(session.isNewVersion());//new version
            if (session.isNewVersion()) {
                ds.writeInt(us.getXeng());
            }
            int m = 4;
            ds.writeByte((byte) m);
            short[] IDAction = {103, 102, 104, 107};
            String[] actionName = new String[]{"Tặng Hoa Violet", "Hôn", "Tặng cánh hoa",
                    "Tặng Hoa Tuyết"};
            short[] IDIcon = {1124, 1188, 1187, 1173};
            int[] money = {20000, 2000, 10000, 5};
            byte[] typeMoney = {0, 0, 0, 1};
            for (int i2 = 0; i2 < m; ++i2) {
                ds.writeShort(IDAction[i2]);
                ds.writeUTF(actionName[i2]);
                ds.writeShort(IDIcon[i2]);
                ds.writeInt(money[i2]);
                ds.writeByte(typeMoney[i2]);
            }
            ds.writeInt(us.getLuong());
            ds.writeInt(us.getLuongKhoa());
            ds.writeByte(1);
            ds.writeUTF(us.getUsername());
            ds.flush();
            sendMessage(ms5);

            us.getAvatarService().SendTabmsg("donate lần đầu trên 20k nhận được 5.000.000 xu và 10.000 lượng và 200 thẻ quay số miễn phí" +
                    " Và Auto Câu Cá");
            us.getAvatarService().SendTabmsg("update các loại cáo tiên ở shop nâng cấp chay to win, update pet labubu ở shop cày chay.");
            us.getAvatarService().SendTabmsg("update cách ngũ sắc chay và pay ok hết, có thể chọn nâng cấp 5 loại khác nhau");
            us.getAvatarService().SendTabmsg("den bu bao tri gift code : 14tieng");

        } catch (IOException ex) {
            logger.error("onLoginSuccess err", ex);
        }
    }

    public void SendTabmsg(String content) throws IOException {
        Message ms = new Message(-6);
        DataOutputStream ds = ms.writer();
        ds = ms.writer();
        ds.writeInt(1);
        ds.writeUTF("Admin");
        ds.writeUTF(content);
        ds.flush();
        this.session.sendMessage(ms);
    }
    public void getAvatarPart() {
        try {
            List<Part> parts = PartManager.getInstance().getAvatarPart();
            Message ms = new Message(Cmd.GET_AVATAR_PART);
            DataOutputStream ds = ms.writer();
            ds.writeShort(parts.size());
            for (Part part : parts) {
                ds.writeShort(part.getId());
                ds.writeInt(part.getCoin());
                ds.writeShort(part.getGold());
                short type = part.getType();
                ds.writeShort(type);
                switch (type) {
                    case -2:
                        ds.writeUTF(part.getName());
                        ds.writeByte(part.getSell());
                        ds.writeShort(part.getIcon());
                        break;

                    case -1:
                        ds.writeUTF(part.getName());
                        ds.writeByte(part.getSell());
                        ds.writeByte(part.getZOrder());
                        ds.writeByte(part.getGender());
                        ds.writeByte(part.getLevel());
                        ds.writeShort(part.getIcon());
                        short[] imgID = part.getImgID();
                        byte[] dx = part.getDx();
                        byte[] dy = part.getDy();
                        for (int i = 0; i < 15; i++) {
                            ds.writeShort(imgID[i]);
                            ds.writeByte(dx[i]);
                            ds.writeByte(dy[i]);
                        }
                        break;

                    default:
                        ds.writeShort(part.getIcon());
                        break;
                }
            }
            ds.flush();
            sendMessage(ms);
        } catch (Exception e) {
            logger.error("getAvatarPart() ", e);
        }
    }


    public void inspectMessageData(Message message) {
        DataInputStream dis = message.reader();
        if (dis != null) {
            try {
                while (dis.available() > 0) {  // Vòng lặp cho đến khi hết dữ liệu
                    try {
                        boolean b = dis.readBoolean();
                        System.out.println("Read int: " + b);
                        int intValue = dis.readInt();  // Thử đọc int
                        System.out.println("Read int: " + intValue);
                    } catch (IOException e) {
                        // Nếu không phải int, hãy thử kiểu dữ liệu khác
                        try {
                            String stringValue = dis.readUTF();  // Thử đọc chuỗi
                            System.out.println("Read string: " + stringValue);
                        } catch (IOException ex) {
                            try {
                                byte byteValue = dis.readByte();  // Thử đọc byte
                                System.out.println("Read byte: " + byteValue);
                            } catch (IOException exc) {
                                System.out.println("Unknown data format or end of data.");
                                break;  // Nếu tất cả các thử nghiệm đều thất bại, kết thúc vòng lặp
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading message data: " + e.getMessage());
            }
        } else {
            System.err.println("DataInputStream is null.");
        }
    }

    /**
     * Lấy thông tin item và giá tiền để in lên shop?
     *
     * @param ms
     */
    public void requestJoinAny(Message ms) throws IOException {
        byte id = ms.reader().readByte();
        byte idSelectedMini = ms.reader().readByte();
        short idJoin = ms.reader().readShort();

        switch (idJoin) {
//            case 4:
//                this.session.user.getAvatarService().serverDialog("đang xây dựng");
//                break;
            case 5://Shop 1 hawai

                // Retrieve the shop items
                List<Item> items = Part.shopByPart(PartManager.getInstance().getShop1());

                if (items == null) {
                    System.out.println("Items list is null");
                    return; // Handle the null case
                }

                this.session.user.getAvatarService().openUIShop(5, "shop 1", items);
                break;
            case 9:
                List<Item> itemshop2 = Part.shopByPart(PartManager.getInstance().getShop2());

                if (itemshop2 == null) {
                    System.out.println("Items list is null");
                    return; // Handle the null case
                }

                this.session.user.getAvatarService().openUIShop(5, "shop 2", itemshop2);
                break;
            case 18:
                this.session.user.getAvatarService().serverDialog("Biển Locity đang xây dựng vui lòng quay lại sau !");
                break;
            // Add more cases as needed
            default:
                this.session.user.getZone().leave(this.session.user);

                ms = new Message(Cmd.JOIN_ONGAME_MINI);
                DataOutputStream ds = ms.writer();
//        ds.writeByte(1);
//        ds.writeByte(0);
//        ds.writeShort(4);
                this.session.sendMessage(ms);
                break;
        }

    }


    public void requestPartDynaMic(Message ms) {
        try {
            short itemID = ms.reader().readShort();
            Part part = PartManager.getInstance().findPartByID(itemID);
            // cmd -97
            ms = new Message(Cmd.REQUEST_DYNAMIC_PART);
            DataOutputStream ds = ms.writer();
            ds.writeShort(part.getId());
            ds.writeInt(part.getCoin());
            ds.writeShort(part.getGold());
            short type = part.getType();
            ds.writeShort(type);
            switch (type) {
                case -2:
                    ds.writeUTF(part.getName());
                    ds.writeByte(part.getSell());
                    ds.writeShort(part.getIcon());
                    break;

                case -1:
                    ds.writeUTF(part.getName());
                    ds.writeByte(part.getSell());
                    ds.writeByte(part.getZOrder());
                    ds.writeByte(part.getGender());
                    ds.writeByte(part.getLevel());
                    ds.writeShort(part.getIcon());
                    short[] imgID = part.getImgID();
                    byte[] dx = part.getDx();
                    byte[] dy = part.getDy();
                    for (int i = 0; i < 15; i++) {
                        ds.writeShort(imgID[i]);
                        ds.writeByte(dx[i]);
                        ds.writeByte(dy[i]);
                    }
                    break;
                default:
                    ds.writeShort(part.getIcon());
                    break;
            }
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException ex) {
            logger.error("requestPartDynaMic() ", ex);
        }
    }

    public void enter(Zone z) {
        try {
            List<User> players = z.getPlayers();
            Map map = z.getMap();
            Message ms = new Message(Cmd.AVATAR_JOIN_PARK);
            DataOutputStream ds = ms.writer();
            ds.writeByte(map.getId());
            ds.writeByte(z.getId());
            ds.writeShort(-1);
            ds.writeShort(-1);
            int numUser = players.size();
            ds.writeByte((byte) numUser);
            for (User pl : players) {
                ds.writeInt(pl.getId());
                ds.writeUTF(pl.getUsername());
                ds.writeByte(pl.getWearing().size());
                for (Item item : pl.getWearing()) {
                    ds.writeShort(item.getId());
                }
                ds.writeShort(pl.getX());
                ds.writeShort(pl.getY());
                ds.writeByte(pl.getRole());//0 la npc
            }
            for (User pl : players) {
                ds.writeByte(pl.getDirect());
            }
            for (int i = 0; i < numUser; ++i) {
                ds.writeByte(101);
            }
            for (int i = 0; i < numUser; ++i) {
                ds.writeShort(-1);
            }
            ds.writeByte(0);
            ds.writeByte(0);


            List<MapItem> mapItems = map.getMapItems();
            List<MapItemType> mapItemTypes = map.getMapItemTypes();
            ds.writeShort(mapItems.size());
            ds.writeByte(mapItemTypes.size());
            for (MapItemType mapItemType : mapItemTypes) {
                ds.writeByte(mapItemType.getId());
                ds.writeShort(mapItemType.getImgID());
                ds.writeByte(mapItemType.getIconID());
                ds.writeShort(mapItemType.getDx());
                ds.writeShort(mapItemType.getDy());
                List<Position> positions = mapItemType.getListNotTrans();
                ds.writeByte(positions.size());
                for (Position position : positions) {
                    ds.writeByte(position.getX());
                    ds.writeByte(position.getY());
                }
            }
            ds.writeByte(mapItems.size());
            for (MapItem mapItem : mapItems) {
                ds.writeByte(mapItem.getType());
                ds.writeByte(mapItem.getTypeID());
                ds.writeByte(mapItem.getX());
                ds.writeByte(mapItem.getY());
            }
            for (int i = 0; i < numUser; ++i) {
                ds.writeShort(-1);
            }
            ds.flush();

//            if (map.getId() != 17) {
//                ds.writeShort(0);
//            } else {
//                ds.writeShort(224);
//                short[] objectID = new short[]{11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791, 11791};
//                short[] objectX = new short[]{-8, -9, -15, 0, 0, -20, 0, -26, 0, 0, 0, -10, -2, -22, -4, -5};
//                short[] objectY = new short[]{-37, -58, -8, -33, 0, -6, 0, -23, 0, 0, -9, -12, -15, -22, -13, -9};
//                byte[][] obj_a = new byte[][]{{0, 1, -1, 2}, new byte[0], new byte[0], {0}, {0, 3}, new byte[0], new byte[0], new byte[0], new byte[0], new byte[0], {0}, {0}, {0, 1}, {-1, 0, 1, 2}, {0}, {0}};
//                byte[][] obj_b = new byte[][]{{0, 0, 0, 0}, new byte[0], new byte[0], {0}, {0, 0}, new byte[0], new byte[0], new byte[0], new byte[0], new byte[0], {0}, {0}, {0, 0}, {0, 0, 0, 0}, {0}, {0}};
//                int numObject = 16;
//                ds.writeByte(numObject);
//
//                for(int j = 0; j < numObject; ++j) {
//                    ds.writeByte(j);
//                    ds.writeShort(objectID[j]);
//                    ds.writeByte(0);
//                    ds.writeShort(objectX[j]);
//                    ds.writeShort(objectY[j]);
//                    byte nObj = (byte)obj_a[j].length;
//                    ds.writeByte(nObj);
//
//                    for(int m = 0; m < nObj; ++m) {
//                        ds.writeByte(obj_a[j][m]);
//                        ds.writeByte(obj_b[j][m]);
//                    }
//                }
//
//                byte[] mapItemA = new byte[]{70, 71, 72, 111, 112, 113, 114, 115, 116, 117, 118};
//                byte[] mapItemB = new byte[]{1, 1, 1, 1, 11, 11, 11, 10, 10, 10, 12};
//                byte[] mapItemC = new byte[]{1, 18, 10, 23, 2, 9, 21, 5, 24, 17, 13};
//                byte[] mapItemD = new byte[]{1, 1, 1, 1, 4, 4, 4, 4, 4, 4, 3};
//                ds.writeByte(mapItemA.length);
//
//                for(int k = 0; k < mapItemA.length; ++k) {
//                    ds.writeByte(mapItemA[k]);
//                    ds.writeByte(mapItemB[k]);
//                    ds.writeByte(mapItemC[k]);
//                    ds.writeByte(mapItemD[k]);
//                }
//            }
//
//            for(int i = 0; i < numUser; ++i) {
//                ds.writeShort(-1);
//            }

//            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("enter() ", ex);
        }
    }

    public void getImageData() {
        try {
            List<ImageInfo> imageInfos = GameData.getInstance().getItemImageDatas();
            Message ms = new Message(Cmd.GET_IMAGE);
            DataOutputStream ds = ms.writer();
            ds.writeShort(imageInfos.size());
            for (ImageInfo imageInfo : imageInfos) {
                ds.writeShort(imageInfo.getId());
                ds.writeShort(imageInfo.getBigImageID());
                ds.writeByte(imageInfo.getX());
                ds.writeByte(imageInfo.getY());
                ds.writeByte(imageInfo.getW());
                ds.writeByte(imageInfo.getH());
            }
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException e) {
            logger.error("getImageData() ", e);
        }
    }

    public void getMapItemType() {
        try {
            System.out.println("get map item type");
            List<MapItemType> mapItemTypes = GameData.getInstance().getMapItemTypes();
            Message ms = new Message(Cmd.MAP_ITEM_TYPE);
            DataOutputStream ds = ms.writer();
            ds.writeShort(mapItemTypes.size());
            for (MapItemType mapItemType : mapItemTypes) {
                ds.writeShort(mapItemType.getId());
                ds.writeUTF(mapItemType.getName());
                ds.writeUTF(mapItemType.getDes());
                ds.writeShort(mapItemType.getImgID());
                ds.writeShort(mapItemType.getIconID());
                ds.writeByte(mapItemType.getDx());
                ds.writeByte(mapItemType.getDy());
                ds.writeShort(mapItemType.getPriceXu());
                ds.writeShort(mapItemType.getPriceLuong());
                ds.writeByte(mapItemType.getBuy());
                List<Position> positions = mapItemType.getListNotTrans();
                ds.writeByte(positions.size());
                for (Position p : positions) {
                    ds.writeByte(p.getX());
                    ds.writeByte(p.getY());
                }
            }
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException e) {
            logger.error("getMapItemType() ", e);
        }
    }

    public void getTileMap() {
        try {
            byte[] dat = Avatar.getFile(session.getResourcesPath() + "house/tile.png");
            if (dat == null) {
                return;
            }
            Message ms = new Message(Cmd.GET_TILE_MAP);
            DataOutputStream ds = ms.writer();
            ds.writeShort(21);
            ds.writeInt(dat.length);
            ds.write(dat);
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException e) {
            logger.error("getTileMap() ", e);
        }
    }

    public void getMapItem() {
        try {
            System.out.println("get map item");
            List<MapItem> mapItems = GameData.getInstance().getMapItems();
            Message ms = new Message(Cmd.MAP_ITEM);
            DataOutputStream ds = ms.writer();
            ds.writeShort(mapItems.size());
            for (MapItem mapItem : mapItems) {
                ds.writeShort(mapItem.getId());
                ds.writeShort(mapItem.getTypeID());
                ds.writeByte(mapItem.getType());
                ds.writeByte(mapItem.getX());
                ds.writeByte(mapItem.getY());
            }
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException e) {
            logger.error("getMapItem() ", e);
        }
    }

    public void getMapItems(Message ms) {
        try {
            byte[] dat = Avatar.getFile("res/data/map_item.dat");
            ms = new Message(-41);
            DataOutputStream ds = ms.writer();
            ds.write(dat);
            ds.flush();
            sendMessage(ms);
        } catch (EOFException eof) {
            eof.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getMapItemTypes(Message ms) {
        try {
            byte[] dat = Avatar.getFile("res/data/map_item_type.dat");
            ms = new Message(-40);
            DataOutputStream ds = ms.writer();
            ds.write(dat);
            ds.flush();
            sendMessage(ms);
        } catch (EOFException eof) {
            eof.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void getBigImage(Message ms) {
        try {
            short id = ms.reader().readShort();
            String folder = session.getResourcesPath() + "big/";
            byte[] dat = Avatar.getFile(folder + id + ".png");
            if (dat == null) {
                return;
            }
            ms = new Message(Cmd.GET_BIG);
            DataOutputStream ds = ms.writer();
            ds.writeShort(id);
            ds.writeShort(dat.length);
            ds.writeShort(dat.length);
            ds.write(dat);
            if (id > 20) {
                ds.writeShort(2);
            } else if (id > 10) {
                ds.writeShort(1);
            }
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException e) {
            logger.error("getBigImage() ", e);
        }
    }

    public void getBigData() {
        try {
            Message ms = new Message(Cmd.SET_BIG);
            DataOutputStream ds = ms.writer();
            File file = new File(session.getResourcesPath() + "big/");
            File[] listFiles = file.listFiles();
            ds.writeByte(listFiles.length);
            for (File f : listFiles) {
                String name = f.getName().split("\\.")[0];
                int id = Integer.parseInt(name);
                int size = (int) f.length();
                ds.writeShort(id);
                ds.writeShort(size);
            }
            ds.writeShort(ServerManager.bigImgVersion);
            ds.writeShort(ServerManager.partVersion);
            ds.writeShort(ServerManager.bigItemImgVersion);
            ds.writeShort(ServerManager.itemTypeVersion);
            ds.writeShort(ServerManager.itemVersion);
            ds.writeByte(0);
            ds.writeInt(ServerManager.objectVersion);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("getBigData() ", ex);
        }
    }

    public void updateMoney(int type) {
        try {
            Message ms = new Message(Cmd.UPDATE_MONEY);
            DataOutputStream ds = ms.writer();
            ds.writeInt(session.user.xeng);
            ds.writeByte((byte) type);
            ds.writeInt(Math.toIntExact(session.user.xu));
            ds.writeInt(session.user.luong);
            ds.writeInt(session.user.luongKhoa);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("updateMoney ", ex);
        }
    }

    public void openMenuOption(int userID, int menuID, String... menus) {
        try {
            Message ms = new Message(Cmd.MENU_OPTION);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeByte(menuID);
            ds.writeByte(menus.length);
            for (String menu : menus) {
                ds.writeUTF(menu);
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            logger.error("openMenuOption ", e);
        }
    }

    public void openMenuOption(int userID, int menuID, List<Menu> menus) {
        try {
            Message ms = new Message(Cmd.MENU_OPTION);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeByte(menuID);
            ds.writeByte(menus.size());
            for (Menu menu : menus) {
                ds.writeUTF(menu.getName());
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            logger.error("openMenuOption ", e);
        }
    }

    public void openUIMenu(int userID, int menuID, List<Menu> menus, String npcName, String npcChat) {
        try {
            Message ms = new Message(Cmd.MENU_OPTION);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeByte(menuID);
            ds.writeByte(menus.size());
            for (Menu m : menus) {
                ds.writeUTF(m.getName());
            }
            for (Menu m : menus) {
                ds.writeShort(m.getId());
            }
            if (npcName != null) {
                ds.writeUTF(npcName);
                ds.writeUTF(npcChat);
                for (Menu m : menus) {
                    ds.writeBoolean(m.isMenu());
                }
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            logger.error("openMenuOption ", e);
        }
    }

    public void requestYourInfo(User us) {
        try {
            Message ms = new Message(-22);
            DataOutputStream ds = ms.writer();
            ds.writeInt(us.getId());
            ds.writeByte(us.getLeverMain());
            ds.writeByte(us.getLeverMainPercen());
            ds.writeByte(us.getFriendly());
            ds.writeByte(0); //us.getCrazy()
            ds.writeByte(us.getStylish());
            ds.writeByte(us.getHappy());
            ds.writeByte(100 - us.getHunger());


            if(us.getIdUsHenHo()!=0){
                ds.writeInt(us.getIdUsHenHo());
            }else {
                ds.writeInt(-1);
                ds.writeShort(us.getLeverMain());
                ds.flush();
                sendMessage(ms);
                return;
            }

            //User us2 = UserManager.getInstance().find(1);
            ds.writeUTF(us.getNamehh());
            ds.writeByte(us.getWearingMarry().size());
            for (Item item : us.getWearingMarry()) {
                ds.writeShort(item.getId());
            }

            ds.writeUTF(us.getTenNhan()); // Slogan
            ds.writeShort(us.getImginfo()); // idImage
            ds.writeByte(us.getLevelMarry()); // Level of avatar3
            ds.writeByte(us.getPerLevelMarry()); // Percent level of avatar3
            ds.writeUTF("text 2"); // Relationship
            ds.writeShort(1); // num23
            ds.writeUTF("text 3"); // Action name if num23 != -1

            ds.writeShort(us.getLeverMain());
            ds.flush();

            sendMessage(ms);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void getFoodData() {
        try {
            Message ms = new Message(Cmd.GET_ITEM_INFO);
            DataOutputStream ds = ms.writer();
            List<Food> foods = FoodManager.getInstance().getFoods();
            ds.writeShort(foods.size());
            for (Food food : foods) {
                ds.writeShort(food.getId());
                ds.writeUTF(food.getName());
                ds.writeUTF(food.getDescription());
                ds.writeInt(food.getPrice());
                ds.writeByte(food.getShop());
                ds.writeShort(food.getIcon());
            }
            ds.flush();
            this.sendMessage(ms);
        } catch (IOException e) {
            logger.error("getFoodData ", e);
        }
    }

    public void customTab(String title, String content) {
        try {
            Message ms = new Message(Cmd.CUSTOM_TAB);
            DataOutputStream ds = ms.writer();
            ds.writeByte(0);
            ds.writeUTF(title);
            ds.writeUTF(content);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("customTab ", ex);
        }
    }

    public void sellFish(User us,int idFIsh) throws IOException {
        Item item = us.findItemInChests(idFIsh);
        if (item != null && item.getQuantity() > 0) {
            int sell = item.getPart().getCoin();//*item.getQuantity()
            String message = String.format("Bạn vừa bán %d %s với giá = %d xu.", item.getQuantity(), item.getPart().getName(),item.getPart().getCoin(),item.getQuantity(), sell);
            us.removeItem(item.getId(), item.getQuantity());
            us.updateXu(+sell);
            us.getAvatarService().updateMoney(0);
            us.getAvatarService().SendTabmsg(message);
        }
    }


    public void sendEffectStyle4(byte id, byte loopLimit, short num, byte timeStop) {
        try {
            Message ms = new Message(Cmd.EFFECT_OBJ);
            DataOutputStream ds = ms.writer();
            ds.writeByte(0);
            ds.writeByte(id);
            ds.writeByte(4);
            ds.writeByte(loopLimit);
            ds.writeShort(num);
            ds.writeByte(timeStop);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("send eff ", ex);
        }
    }

    public void sendEffectData(Message mss) {
        try {
            byte id = mss.reader().readByte();
            String folder = session.getResourcesPath() + "effect/";
            byte[] imageData = Avatar.getFile(folder + id + ".png");
            byte[] effData = Avatar.getFile("res/data/effect/" + id + ".dat");


            Message ms = new Message(Cmd.EFFECT_OBJ);
            DataOutputStream ds = ms.writer();
            ds.writeByte(1);
            ds.writeByte(id);
            ds.writeShort(imageData.length);
            ds.write(imageData);
            ds.write(effData);
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public void HandlerMENU_ROTATE(User us, Message mss) {
        try {
            short id = mss.reader().readShort();
            Message ms = new Message(Cmd.REQUEST_YOUR_INFO);
            DataOutputStream ds = ms.writer();
            ds.writeShort(id);
            switch (id) {
//                case 1: {
//                    us.getAvatarService().openMenuOption(1000, 2,
//                            "Hủy hẹn hò ? : không",
//                            "Hủy hẹn hò ? : Có");
//                    break;
//                }
                case 4: {
                    mss = new Message(Cmd.MENU_ROTATE);
                    DataOutputStream ds1 = mss.writer();
                    short num73 = 3;
                    ds1.writeShort(3);
                    int newMoney = 0;
                    ds1.writeInt(newMoney);
                    byte typeBuy = 0;
                    ds1.writeByte(typeBuy);
                    if (num73 != -1)
                    {
                        newMoney = 1;
                        ds1.writeInt(newMoney);
                        typeBuy = 0;
                        ds1.writeByte(typeBuy);
                    }
                    String text5 = "text";
                    ds1.writeUTF(text5);
                    int xu3 = 1;
                    ds1.writeInt(xu3);
                    int luong3 = 2;
                    ds1.writeInt(luong3);
                    int luongKhoa = 3;
                    ds1.writeInt(luongKhoa);
                    ds1.flush();
                    this.session.sendMessage(mss);
                    break;
                }
                //hẹn hò
                case 36: {
                    us.getAvatarService().sendTextBoxPopup(us.getId(), 100, "gửi lời mới hẹn hò tới ? (ghi tên nhân vật)", 0);
                    break;
                }
                case 48: {
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 1)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 6)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 6)
                                .idPlayer(us.getId())
                                .send();
                    });
                    break;
                }
                case 47: {
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 8)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 6)
                                .idPlayer(us.getId())
                                .send();
                    });
                    break;
                }
                case 8: {
                    long currentTime = System.currentTimeMillis();
                    long lastActionTime = lastActionTimes.getOrDefault(us.getId(), 0L);

                    if (currentTime - lastActionTime < ACTION_COOLDOWN_MS) {
                        us.getAvatarService().serverDialog("Từ từ thôi bạn!");
                        return;
                    }
                    // Cập nhật thời gian thực hiện hành động
                    lastActionTimes.put(us.getId(), currentTime);
                    if (us.getLuong() < 5) {
                        us.getAvatarService().serverDialog("Bạn phải có trên 5 Lượng");
                        return;
                    }
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 16)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 6)
                                .idPlayer(us.getId())
                                .send();
                    });


                    us.updateTopPhaoLuong(-5);
                    us.getAvatarService().updateMoney(0);
                    break;
                }
                case 35: {
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 46)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 5)
                                .idPlayer(us.getId())
                                .send();
                    });
                    break;
                }
                case 33: {
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 48)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 5)
                                .idPlayer(us.getId())
                                .send();
                    });
                    break;
                }
                case 34: {
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 45)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 5)
                                .idPlayer(us.getId())
                                .send();
                    });
                    break;
                }
                case 9: {
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 11)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 5)
                                .idPlayer(us.getId())
                                .send();
                    });
                    break;
                }
                case 10: {
                    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
                    try (DataOutputStream dos1 = new DataOutputStream(baos1)) {
                        dos1.writeInt(0);//x
                        dos1.flush();
                        byte[] data1 = baos1.toByteArray();
                        MessageHandler msgHandler = new MessageHandler(us.session);
                        msgHandler.onMessage(new Message(Cmd.CONTAINER, data1));
                    }
                    break;
                }
                case 11: {
                    long currentTime = System.currentTimeMillis();
                    long lastActionTime = lastActionTimes.getOrDefault(us.getId(), 0L);

                    if (currentTime - lastActionTime < ACTION_COOLDOWN_MS) {
                        us.getAvatarService().serverDialog("Từ từ thôi bạn!");
                        return;
                    }
                    // Cập nhật thời gian thực hiện hành động
                    lastActionTimes.put(us.getId(), currentTime);
                    if (us.getXu() < 20000) {
                        us.getAvatarService().serverDialog("Bạn phải có trên 20.000 Xu");
                        return;
                    }
                    us.getZone().getPlayers().forEach(u -> {
                        EffectService.createEffect()
                                .session(u.session)
                                .id((byte) 20)
                                .style((byte) 0)
                                .loopLimit((byte) 6)
                                .loop((short) 1)//so luong lap lai
                                .loopType((byte) 1)
                                .radius((short) 6)
                                .idPlayer(us.getId())
                                .send();
                    });
                    us.updateTopPhaoXu(-20000);
                    us.getAvatarService().updateMoney(0);
                    break;
                }
                case 23:{
                    List<Menu> ListDacBiet = new ArrayList<>();
                    ListDacBiet.add(Menu.builder().name("skill mặc định").action(() -> {
                        us.setUseSkill(0);
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Siêu Anh Hùng").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(1)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(1);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải mặc trên 3 món có dame của các set siêu anh hùng");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Cung").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(2)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(2);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải sử dụng demo 01001");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Thú Cưỡi").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(3)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(3);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải sử dụng demo 01002");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Máy Bay").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(4)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(4);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải sử dụng demo 01003");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Thiêu Đốt").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(5)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(5);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải sử dụng demo 01004");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Băng").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(6)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(6);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải sử dụng demo 01005");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("skill Hô Phong Hoán Vũ").action(() -> {
                        if (us.getListSkill() != null && us.getListSkill().contains(7)) {
                            us.getAvatarService().serverDialog("Đổi thành công");
                            us.setUseSkill(7);
                        } else {
                            us.getAvatarService().serverDialog("Bạn phải sử dụng demo 01005");
                        }
                    }).build());
                    ListDacBiet.add(Menu.builder().name("Thoát").id(0).build());
                    us.setMenus(ListDacBiet);
                    us.getAvatarService().openMenuOption(0, 0, ListDacBiet);
                }
            }
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}