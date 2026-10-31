/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package avatar.lucky;

import avatar.convert.ItemConverter;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.lib.RandomCollection;
import avatar.model.Gift;
import avatar.model.User;
import avatar.server.Utils;
import avatar.service.AvatarService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.Getter;

/**
 * @author kitakeyos - Hoàng Hữu Dũng
 */
public class DialLucky {

    public static final byte ITEM = 1;
    public static final byte XU = 2;
    public static final byte XP = 3;
    public static final byte LUONG = 4;
    public static final byte ITEM2 = 5;
    @Getter
    private byte type;
    private final RandomCollection<Byte> randomType = new RandomCollection<>();
    private final RandomCollection<Item> randomItem = new RandomCollection<>();
    private final RandomCollection<Item> randomItem2 = new RandomCollection<>();
    public DialLucky(byte type) {
        this.type = type;
        //randomType.add(55, ITEM2);
        randomType.add(18, ITEM);
        randomType.add(45, XU);
        randomType.add(36, XP);
        randomType.add(1, LUONG);



        //randomItem2.add
        load();
    }

    public void load() {
        String text = null;
        switch (this.type) {
            case DialLuckyManager.XU:
                text = "SELECT * FROM `dial_lucky` WHERE `xu` = 1;";
                break;

            case DialLuckyManager.LUONG:
                text = "SELECT * FROM `dial_lucky` WHERE `luong` = 1;";
                break;

            case DialLuckyManager.MIEN_PHI:
                text = "SELECT * FROM `dial_lucky` WHERE `free` = 1;";
                break;
        }
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(text);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int itemID = rs.getInt("item_id");
                int ratio = rs.getInt("ratio");
                Item item = new Item(itemID);
                randomItem.add(ratio, item);//Item add qs shop

            }
        } catch (SQLException ex) {
            Logger.getLogger(DialLucky.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void show(User us) {
        AvatarService service = us.getAvatarService();
        NavigableMap<Double, Item> map = randomItem.getMap();
        List<Item> items = new ArrayList<>();
        map.entrySet().stream().forEach((t) -> {
            Item item = t.getValue();
            byte gender = item.getPart().getGender();
            if (!((gender == 2 || gender == 1) && (us.getGender() != gender))) {
                items.add(item);
            }
        });
        service.openUIShop(100, "Quay số", items);
    }

    public void doDial(User us, int itemID, int degree) {
        List<Gift> gifts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            byte type = randomType.next();
            Gift gift = new Gift();
            gift.setType((byte) type);

            if (type == ITEM) {
                Item item = randomItem.next();

                Item itemchestUser = us.findItemInChests(item.getId());
                if (itemchestUser!=null && itemchestUser.getExpired() == -1) {
                    System.err.println("Lỗi khi duyệt danh sách người dùng: ");
                    break;
                }
                item = ItemConverter.getInstance().newItem(item);//Item item = new Item(itemCode, -1, 0)
                gift.setId(item.getId());


                if (item.getId() == itemID) {
                    item.setExpired(-1);
                    gift.setExpireDay(-1);
                } else {
                    int time = Utils.getRandomInArray(new int[]{3, 7, 15, 30});
                    item.setExpired(System.currentTimeMillis() + (86400000L * time));
                    gift.setExpireDay(time);
                }
                us.addItemToChests(item);

            } else if (type == XU) {
                int xu = Utils.nextInt(1, 300) * 10;

                gift.setXu(xu);
                us.updateXu(xu);
                us.getAvatarService().updateMoney(0);
            } else if (type == XP) {
                int xp = Utils.nextInt(1, 10) * 10;
                gift.setXp(xp);
                us.addExp(xp);
            } else if (type == LUONG) {
            }

            gifts.add(gift);
        }
        us.getMapService().dialLucky(us, (short) degree, gifts);
    }
}
