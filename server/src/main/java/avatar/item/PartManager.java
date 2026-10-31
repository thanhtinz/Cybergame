package avatar.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import avatar.Farm.farmItem;
import avatar.model.UpgradeItem;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import avatar.db.DbManager;
import lombok.Getter;

public class PartManager {

    private static final PartManager instance = new PartManager();

    public static PartManager getInstance() {
        return instance;
    }

    @Getter
    private final List<farmItem> farmItems = new ArrayList<>();


    @Getter
    private final List<Part> parts = new ArrayList<>();
    @Getter
    private final List<UpgradeItem> upgradeItems = new ArrayList<>();
    @Getter
    private final List<Part> Shop0 = new ArrayList<>();///type 19 shop 1
    @Getter
    private final List<Part> Shop1 = new ArrayList<>();///type 19 shop 1
    @Getter
    private final List<Part> Shop2 = new ArrayList<>();///type 14 shop 2
    //private final List<UpgradeItem> upgradeItems = new ArrayList<>();


    public Part findPartById(int id) {
        return getParts().stream()
                .filter(part -> part.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public void load() {
        parts.clear();
        Shop0.clear();
        Shop1.clear();
        Shop2.clear();
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM `items`;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                int coin = rs.getInt("coin");
                int gold = rs.getInt("gold");
                short type = rs.getShort("type");
                String name = rs.getString("name");
                short icon = rs.getShort("icon");
                int expiredDay = rs.getInt("expired_day");
                byte level = rs.getByte("level");
                byte sell = rs.getByte("sell");
                byte zOrder = rs.getByte("zorder");
                byte gender = rs.getByte("gender");
                short[] imgID = new short[15];
                byte[] dx = new byte[15];
                byte[] dy = new byte[15];
                JSONArray animation = (JSONArray) JSONValue.parse(rs.getString("animation"));
                int size = animation.size();
                for (int i = 0; i < size; i++) {
                    JSONObject obj = (JSONObject) animation.get(i);
                    imgID[i] = ((Long) obj.get("img")).shortValue();
                    dx[i] = ((Long) obj.get("dx")).byteValue();
                    dy[i] = ((Long) obj.get("dy")).byteValue();
                }
                parts.add(Part.builder().id(id)
                        .coin(coin)
                        .gold(gold)
                        .type(type)
                        .name(name)
                        .icon(icon)
                        .expiredDay(expiredDay)
                        .level(level)
                        .sell(sell)
                        .zOrder(zOrder)
                        .gender(gender)
                        .imgID(imgID)
                        .dx(dx)
                        .dy(dy)
                        .build());
                System.out.println("id: " + id + " name: " + name);
                if (sell == 14 || sell == 8) {
                    Shop0.add(Part.builder().id(id)
                            .coin(coin)
                            .gold(gold)
                            .type(type)
                            .name(name)
                            .icon(icon)
                            .expiredDay(expiredDay)
                            .level(level)
                            .sell(sell)
                            .zOrder(zOrder)
                            .gender(gender)
                            .imgID(imgID)
                            .dx(dx)
                            .dy(dy)
                            .build());// Add the individual Part object
                }
                if (sell == 19) {
                    Shop1.add(Part.builder().id(id)
                            .coin(coin)
                            .gold(gold)
                            .type(type)
                            .name(name)
                            .icon(icon)
                            .expiredDay(expiredDay)
                            .level(level)
                            .sell(sell)
                            .zOrder(zOrder)
                            .gender(gender)
                            .imgID(imgID)
                            .dx(dx)
                            .dy(dy)
                            .build());// Add the individual Part object
                }
                if (sell == 17) {
                    Shop2.add(Part.builder().id(id)
                            .coin(coin)
                            .gold(gold)
                            .type(type)
                            .name(name)
                            .icon(icon)
                            .expiredDay(expiredDay)
                            .level(level)
                            .sell(sell)
                            .zOrder(zOrder)
                            .gender(gender)
                            .imgID(imgID)
                            .dx(dx)
                            .dy(dy)
                            .build());// Add the individual Part object
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        loadUpgradeItemData();
        loadFarmItemData();
    }
    public void loadFarmItemData() {
        farmItems.clear();
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM `farmitems`;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                int time = rs.getInt("so_phut_chin");
                int quantity = rs.getInt("san_luong_khi_chin");
                int sell = rs.getInt("gia_san_pham");
                farmItems.add(farmItem
                        .builder()
                        .id(id)
                        .name(name)
                        .time(time)
                        .quantity(quantity)
                        .sell(sell)
                        .build()
                );
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }




    public void loadUpgradeItemData() {
        upgradeItems.clear();
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM `upgrade_item`;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                int itemId = rs.getInt("item_id");
                boolean onlyLuong = rs.getInt("is_only_luong") == 1;
                int ratio = rs.getInt("ratio");
                int itemNeed = rs.getInt("item_need");
                int luong = rs.getInt("luong");
                int xu = rs.getInt("xu");
                int scores = rs.getInt("scores");
                upgradeItems.add(UpgradeItem
                        .builder()
                        .id(id)
                        .itemRequest(itemId)
                        .itemNeed(itemNeed)
                        .ratio(ratio)
                        .luong(luong)
                        .isOnlyLuong(onlyLuong)
                        .xu(xu)
                        .scores(scores)
                        .item(new Item(itemId))
                        .build()
                );
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public List<Part> getAvatarPart() {
        return parts.stream().filter((t) -> t.getId() < 2000).collect(Collectors.toList());
    }

    public Part findPartByID(int id) {
        for (Part part : parts) {
            if (part.getId() == id) {
                return part;
            }
        }
        return null;
    }

    public farmItem findFarmitemByID(int id) {
        for (farmItem farmItem : farmItems) {
            if (farmItem.getId() == id) {
                return farmItem;
            }
        }
        return null;
    }

}