package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;

import java.sql.Connection;
import java.sql.ResultSet;

import avatar.item.Item;
import avatar.server.Utils;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.io.IOException;
import java.io.DataOutputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import avatar.network.Message;
import avatar.network.Session;

public class HomeService extends Service {

    public HomeService(Session cl) {
        super(cl);
    }

    public void buyItemHouse(Message ms) throws IOException {
        short itemId = ms.reader().readShort();
        byte x = ms.reader().readByte();
        byte y = ms.reader().readByte();
        byte type = ms.reader().readByte();
        this.session.user.updateXu(-2000);
        this.session.getAvatarService().updateMoney(0);
        int result = 0;
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("INSERT INTO `house_player_item` (`user_id`, `house_item_id`, `x`, `y`) VALUES (?, ?, ?, ?)");) {

            ps.setInt(1, this.session.user.getId());
            ps.setShort(2, itemId);
            ps.setByte(3, x);
            ps.setByte(4, y);
            result = ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
        if (result > 0) {
            ms = new Message(-74);
            DataOutputStream ds = ms.writer();
            ds.writeShort(itemId);
            ds.writeByte(x);
            ds.writeByte(y);
            ds.flush();
            this.session.sendMessage(ms);
        }
    }

    public void sortItemHouse(Message ms) throws IOException {
        short anchor = ms.reader().readShort();
        byte x = ms.reader().readByte();
        byte y = ms.reader().readByte();
        byte x2 = ms.reader().readByte();
        byte y2 = ms.reader().readByte();
        byte rotate = ms.reader().readByte();
        String UPDATE_HOUSE_ITEM = "UPDATE `house_player_item` SET `x` = ?, `y` = ?, `rotate` = ? WHERE `user_id` = ? AND `house_item_id` = ? AND `x` = ? AND `y` = ? LIMIT 1";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_HOUSE_ITEM);) {
            ps.setByte(1, x2);
            ps.setByte(2, y2);
            ps.setByte(3, rotate);
            ps.setInt(4, this.session.user.getId());
            ps.setShort(5, anchor);
            ps.setByte(6, x);
            ps.setByte(7, y);
            int result_update = ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    public void getTypeHouse(Message ms) throws IOException {
        byte typeHouse = ms.reader().readByte();
        System.out.println("typeHouse = " + typeHouse);
        ms = new Message(-67);
        DataOutputStream ds = ms.writer();
        ds.writeByte(0);
        ds.writeShort(6299);
        ds.writeByte(3);
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void delItemHouse(Message ms) throws IOException {
        short itemId = ms.reader().readShort();
        System.out.println("itemId = " + itemId);
        byte x = ms.reader().readByte();
        byte y = ms.reader().readByte();
        byte rotate = ms.reader().readByte();
        String INSERT_HOUSE_ITEM = "DELETE FROM `house_player_item` WHERE `user_id` = ? AND `house_item_id` = ? AND `x` = ? AND `y` = ? LIMIT 1";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_HOUSE_ITEM);) {
            ps.setInt(1, this.session.user.getId());
            ps.setShort(2, itemId);
            ps.setByte(3, x);
            ps.setByte(4, y);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
        ms = new Message(-66);
        DataOutputStream ds = ms.writer();
        ds.writeShort(itemId);
        ds.writeByte(x);
        ds.writeByte(y);
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void createHome(Message ms) throws IOException {
        try (Connection connection = DbManager.getInstance().getConnection();) {
            String GET_HOUSE_DATA = "SELECT * FROM `house_buy` WHERE `user_id` = ? LIMIT 1";
            PreparedStatement ps = connection.prepareStatement(GET_HOUSE_DATA);
            ps.setInt(1, this.session.user.getId());
            ResultSet res = ps.executeQuery();
            if (res.next()) {
                JSONArray ja_map = (JSONArray) JSONValue.parse(res.getString("map_data"));
                byte[] map_data = new byte[ja_map.size()];
                for (int i = 0; i < ja_map.size(); ++i) {
                    map_data[i] = ((Long) ja_map.get(i)).byteValue();
                }
                short type = ms.reader().readShort();
                short num = ms.reader().readShort();
                byte[] map_data_new = new byte[num];
                Vector<Byte> tileChange = new Vector<>();
                for (int j = 0; j < num; ++j) {
                    map_data_new[j] = ms.reader().readByte();
                    if (map_data_new[j] != map_data[j]) {
                        if (j < 519 || j > 522) {
                            tileChange.add(map_data_new[j]);
                            map_data[j] = map_data_new[j];
                        }
                    }
                }
                ms.reader().readShort();
                for (Byte tile : tileChange) {
                    System.out.println("Change tile: " + tile);
                }
                ps.close();
                if (type == 1) {
                    JSONArray ja_map_new = new JSONArray();
                    for (int k = 0; k < map_data.length; ++k) {
                        ja_map_new.add(map_data[k]);
                    }
                    String UPDATE_HOUSE_MAP = "UPDATE `house_buy` SET `map_data` = ? WHERE `user_id` = ?";
                    ps = connection.prepareStatement(UPDATE_HOUSE_MAP);
                    ps.setString(1, ja_map_new.toJSONString());
                    ps.setInt(2, this.session.user.getId());
                    int result_update = ps.executeUpdate();
                    ps.close();
                    this.session.user.updateXu(-2000);
                    this.session.user.getAvatarService().updateMoney(0);
                    ms = new Message(-46);
                    DataOutputStream ds = ms.writer();
                    ds.writeShort(1);
                    ds.writeUTF("B\u1ea1n \u0111\u00e3 l\u00e1t g\u1ea1ch th\u00e0nh c\u00f4ng v\u00e0 t\u1ed1n 2000 xu v\u00e0 0 l\u01b0\u1ee3ng.");
                    ds.flush();
                    this.session.sendMessage(ms);
                } else {
                    ms = new Message(-46);
                    DataOutputStream ds2 = ms.writer();
                    ds2.writeShort(0);
                    ds2.writeUTF("B\u1ea1n c\u1ea7n 2000 xu v\u00e0 0 l\u01b0\u1ee3ng \u0111\u1ec3 l\u00e1t g\u1ea1ch. B\u1ea1n c\u00f3 \u0111\u1ed3ng \u00fd kh\u00f4ng ?");
                    ds2.flush();
                    this.session.sendMessage(ms);
                }
            }
            if (ps != null) {
                ps.close();
            }
            res.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void getImgObjInfo(Message ms) throws IOException {
        Vector<Tile> tiles = new Vector<Tile>();
        tiles.add(new Tile("BT", -1, -1));
        tiles.add(new Tile("VH", 100, -1));
        tiles.add(new Tile("VS1", 110, -1));
        tiles.add(new Tile("VS2", 150, -1));
        tiles.add(new Tile("CN", 120, -1));
        tiles.add(new Tile("CT1", 200, -1));
        tiles.add(new Tile("CT2", 220, -1));
        tiles.add(new Tile("GH", 240, -1));
        tiles.add(new Tile("TBN", 250, -1));
        tiles.add(new Tile("T", -1, 1));
        tiles.add(new Tile("D", -1, -1));
        tiles.add(new Tile("LT", -1, -1));
        tiles.add(new Tile("CS", -1, -1));
        tiles.add(new Tile("GN", 1000, -1));
        tiles.add(new Tile("GD", -1, 1));
        tiles.add(new Tile("KX1", 1000, -1));
        tiles.add(new Tile("KX2", -1, -1));
        tiles.add(new Tile("DR", -1, 1));
        tiles.add(new Tile("BT", 1500, -1));
        tiles.add(new Tile("KT", -1, -1));
        tiles.add(new Tile("KX3", -1, -1));
        tiles.add(new Tile("tV", 100, -1));
        tiles.add(new Tile("ctV", 500, -1));
        tiles.add(new Tile("tX", 150, -1));
        tiles.add(new Tile("ctX", 700, -1));
        tiles.add(new Tile("tH", 200, -1));
        tiles.add(new Tile("ctH", 900, -1));
        tiles.add(new Tile("tXD", 250, -1));
        tiles.add(new Tile("ctXD", 1100, -1));
        tiles.add(new Tile("tXR", -1, -1));
        tiles.add(new Tile("ctXR", -1, -1));
        tiles.add(new Tile("tXB", -1, -1));
        tiles.add(new Tile("ctXB", -1, -1));
        tiles.add(new Tile("g\u1ea1ch x", -1, -1));
        tiles.add(new Tile("", -1, -1));
        tiles.add(new Tile("", -1, -1));
        tiles.add(new Tile("", -1, -1));
        tiles.add(new Tile("", -1, -1));
        tiles.add(new Tile("", -1, -1));
        tiles.add(new Tile("", -1, -1));
        tiles.add(new Tile("", -1, -1));
        ms = new Message(-43);
        DataOutputStream ds = ms.writer();
        ds.writeShort(tiles.size());
        for (Tile tile : tiles) {
            ds.writeUTF(tile.name);
            ds.writeInt(tile.xu);
            ds.writeInt(tile.luong);
        }
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void onCustomChest(Message ms) throws IOException {

        List<Item> lstChest = this.session.user.chests;

        List<Item> lstChestHome = this.session.user.chestsHome;

        ms = new Message(Cmd.CUSTOM_CHEST);
        DataOutputStream ds = ms.writer();
        ds.writeShort(lstChest.size());
        for (Item item : lstChest) {
            if(item.getId() != 40){
                ds.writeShort(item.getId());
                ds.writeByte(0);
                ds.writeUTF("");
            }
        }
        ds.writeInt(0);
        ds.writeByte(1);

        ds.writeShort(lstChestHome.size());
        for (Item item : lstChestHome) {
            ds.writeShort(item.getId());
            ds.writeByte(0);
            ds.writeUTF("");
        }

        ds.flush();
        this.session.sendMessage(ms); // Gửi thông điệp tới client
    }

    public void transPartChest(Message ms) throws IOException {

        byte i = ms.reader().readByte();// 0 chest to chesthome , 1 = chestHomeToChest
        short ii = ms.reader().readShort();// index
        short Itemid = ms.reader().readShort();
        if(i == 0){
            Item Home = this.session.user.findItemInChestsHome(Itemid);
            Item chest = this.session.user.findItemInChests(Itemid);


            if(chest==null || chest.getId() == 593 || chest.getId() == 683 || chest.getPart().getType() == -2)
            {
                this.session.user.getAvatarService().serverDialog("error -004");
                return;
            }
            if(chest == null)
            {
                this.session.user.getAvatarService().serverDialog("error -001");
                return;
            }

            if (chest != null && Home != null) {
                this.session.user.getAvatarService().serverDialog("error -002");
                return;
            }
            if(chest.getQuantity()> 1 || chest.getExpired()!= -1){
                this.session.user.getAvatarService().serverDialog("error -003");
                return;
            }

            Boolean Slot = this.session.user.getChestHomeSlot() <= this.session.user.chestsHome.size() ? false : true;
            if(Slot){
                this.session.user.removeItemFromChests(chest);
                this.session.user.addItemToChestsHome(chest);
            }

            ms = new Message(Cmd.TRANS_PART_CHEST);
            DataOutputStream ds = ms.writer();
            ds.writeBoolean(Slot);
            ds.writeUTF("Rương nhà đã đầy !");
            ds.flush();
            this.session.sendMessage(ms); // Gửi thông điệp tới client
        }else{
            Item Home = this.session.user.findItemInChestsHome(Itemid);
            Item chest = this.session.user.findItemInChests(Itemid);

            if (chest != null && Home != null) {
                this.session.user.getAvatarService().serverDialog("error -2");
                return;
            }

            Boolean Slot = this.session.user.getChestSlot() <= this.session.user.chests.size() ? false : true;
            if (Slot) {
                this.session.user.removeItemFromChestsHome(Home);
                this.session.user.addItemToChests(Home);
            }

            ms = new Message(Cmd.TRANS_PART_CHEST);
            DataOutputStream ds = ms.writer();
            ds.writeBoolean(Slot);
            ds.writeUTF("Rương đồ đã đầy!");
            ds.flush();
            this.session.sendMessage(ms); // Gửi thông điệp tới client
        }
    }

    public void upgradeChestHome(Message ms) throws IOException {


        int[] chestSlots = {10, 15, 20, 25, 30,35,40,45,50,55}; // Cấp 1 = 10 ô, cấp 2 = 15 ô, ..., cấp 5 = 30 ô
        int[] chestUpgradeCostXu = {10000, 20000, 50000, 100000,200000,500000,1000000,2000000,3000000,5000000}; // Chi phí nâng cấp bằng xu cho từng cấp
        int[] chestUpgradeCostLuong = {10, 20, 30, 40,50,100,200,500,800,1200}; // Chi phí nâng cấp bằng lượng cho từng cấp

        // Giả sử bạn có phương thức để lấy cấp độ rương hiện tại

        int currentChestLevel = this.session.user.getChestLevel(); // Ví dụ: cấp 1, 2, 3, ...

        if (currentChestLevel >= chestSlots.length-1) {
            this.session.getAvatarService().serverDialog("Rương của bạn đã được nâng cấp tối đa!");
            return;
        }

        byte type = ms.reader().readByte();
        if(type == 0){
            int nextLevel = currentChestLevel + 1; // Cấp độ rương tiếp theo
            int nextSlots = chestSlots[nextLevel - 1]; // Số ô sau khi nâng cấp
            int upgradeCostXu = chestUpgradeCostXu[nextLevel - 1]; // Giá xu cần nâng cấp
            int upgradeCostLuong = chestUpgradeCostLuong[nextLevel - 1]; // Giá lượng cần nâ

            ms = new Message(Cmd.UPGRADE_CHEST);
            DataOutputStream ds = ms.writer();
            ds.writeByte(0);
            ds.writeUTF("Bạn có muốn nâng cấp rương nhà từ cấp " + currentChestLevel + " lên cấp " + nextLevel +
                    " (" + nextSlots + " ô) bằng " + upgradeCostXu + " xu và " + upgradeCostLuong + " lượng không?");
            ds.flush();
            this.session.sendMessage(ms); // Gửi thông điệp tới client

        }else {

            // Kiểm tra cấp độ rương hiện tại

            int upgradeCostXu = chestUpgradeCostXu[currentChestLevel]; // Giá xu cần nâng cấp
            int upgradeCostLuong = chestUpgradeCostLuong[currentChestLevel]; // Giá lượng cần nâng cấp

            // Kiểm tra người chơi có đủ xu hoặc lượng để nâng cấp không
            if (this.session.user.xu >= upgradeCostXu && this.session.user.luong >= upgradeCostLuong) {
                this.session.user.updateXu(- upgradeCostXu); // Trừ xu người chơi
                this.session.user.getAvatarService().updateMoney(0);
                this.session.user.updateLuong(- upgradeCostLuong); // Trừ xu người chơi
                this.session.user.getAvatarService().updateMoney(0);
                this.session.user.updateChest_homeSlot(+5);
                this.session.getAvatarService().serverDialog("Đã nâng cấp thành công rương cấp " + (currentChestLevel + 1) + " (" + chestSlots[currentChestLevel] + " ô)");

            } else {
                this.session.getAvatarService().serverDialog("Bạn không đủ xu hoặc lượng để nâng cấp rương!");
            }
        }
    }


    class Tile {

        private String name;
        private int xu;
        private int luong;

        public Tile(String name, int xu, int luong) {
            this.name = name;
            this.xu = xu;
            this.luong = luong;
        }
    }

}
