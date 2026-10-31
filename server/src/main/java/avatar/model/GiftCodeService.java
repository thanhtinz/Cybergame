package avatar.model;

import java.sql.PreparedStatement;
import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import avatar.item.Item;
import avatar.server.UserManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import avatar.db.DbManager;
public class GiftCodeService {

    public GiftCodeService() {
        loadGiftCodes(); // Tải mã quà tặng khi khởi tạo lớp
    }


    private Map<String, GiftCode> giftCodes = new HashMap<>();


    public void loadGiftCodes() {
        String sql = "SELECT * FROM giftcode";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String code = rs.getString("code");
                GiftCode giftCode = new GiftCode(
                        rs.getInt("id"),
                        code,
                        rs.getString("message"),
                        rs.getString("data"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getInt("num"),
                        rs.getInt("create_by"),
                        rs.getTimestamp("create_time")
                );
                giftCodes.put(code, giftCode);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Phương thức kiểm tra tính hợp lệ của mã quà tặng
    public boolean isValidGiftCode(String code) {
        GiftCode giftCode = giftCodes.get(code);
        if (giftCode == null) {
            return false; // Mã không tồn tại
        }
        long now = System.currentTimeMillis();
        return giftCode.startTime.getTime() <= now && giftCode.endTime.getTime() >= now && giftCode.num > 0;
    }

    // Cập nhật số lượng mã quà tặng trong cơ sở dữ liệu
    private void updateGiftCodeInDatabase(GiftCode giftCode) throws SQLException {
        String sql = "UPDATE giftcode SET num = ? WHERE code = ?";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, giftCode.num);
            ps.setString(2, giftCode.code);
            ps.executeUpdate();
        }
    }

    // Ghi nhận việc sử dụng mã quà tặng vào bảng giftcode_use
    private void recordGiftCodeUsage(int userId, int giftCodeId) throws SQLException {
        // Kiểm tra xem người dùng đã sử dụng mã quà tặng này chưa
        String checkSql = "SELECT COUNT(*) FROM giftcode_use WHERE user = ? AND giftcode_id = ?";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement checkPs = connection.prepareStatement(checkSql)) {
            checkPs.setInt(1, userId);
            checkPs.setInt(2, giftCodeId);
            ResultSet rs = checkPs.executeQuery();
            rs.next();
            int count = rs.getInt(1);

            if (count > 0) {
                // Nếu đã dùng, không thực hiện chèn và có thể thông báo lỗi
                UserManager.getInstance().find(userId).getAvatarService().serverDialog("Bạn đã dùng mã quà tặng đã được sử dụng trước đó");
            }

            // Nếu chưa tồn tại, chèn bản ghi mới
            String insertSql = "INSERT INTO giftcode_use (user, giftcode_id) VALUES (?, ?)";
            try (PreparedStatement insertPs = connection.prepareStatement(insertSql)) {
                insertPs.setInt(1, userId);
                insertPs.setInt(2, giftCodeId);
                insertPs.executeUpdate();
            }
        }
    }



    // Sử dụng mã quà tặng
    public boolean useGiftCode(int userId, String code) {
        GiftCode giftCode = giftCodes.get(code);
        if (giftCode == null) {
            return false; // Mã không tồn tại
        }

        long now = System.currentTimeMillis();
        if (giftCode.startTime.getTime() <= now && giftCode.endTime.getTime() >= now && giftCode.num > 0) {
            // Giảm số lượng mã quà tặng trong bộ nhớ
            giftCode.num -= 1;

            try {
                // Cập nhật cơ sở dữ liệu
                updateGiftCodeInDatabase(giftCode);

                // Ghi nhận việc sử dụng mã quà tặng
                recordGiftCodeUsage(userId, giftCode.id);

                // Phân phối quà tương ứng
                distributeGift(userId, giftCode);

            } catch (SQLException e) {
                e.printStackTrace();
                return false; // Xử lý lỗi nếu không thể cập nhật cơ sở dữ liệu
            }

            return true;
        }
        return false; // Mã không hợp lệ hoặc hết số lượng
    }


    private void distributeGift(int userId, GiftCode giftCode) {

        String MaCode = giftCode.code;
        User us = UserManager.getInstance().find(userId);

        switch (MaCode) {

            case "14tieng":
                if(us.chests.size() >= us.getChestSlot()-6){
                    us.getAvatarService().serverDialog("chào bạn " + us.getUsername() +" bạn phải trống trên 7 ô rương");
                    return;
                }
                List<Integer> itemIds = new ArrayList<>();
                itemIds.add(4732);
                itemIds.add(4733);
                itemIds.add(5724);
                itemIds.add(6112);
                itemIds.add(6112);
                itemIds.add(6670);
                // Bước 2: Tạo một đối tượng Random
                Random random = new Random();// Bước 3: Lấy một ID ngẫu nhiên từ danh sách
                int randomIndex = random.nextInt(itemIds.size()); // Lấy chỉ số ngẫu nhiên
                int randomItemId = itemIds.get(randomIndex); // Lấy ID ngẫu nhiên
                Item daisen = new Item(randomItemId);
                daisen.setExpired(System.currentTimeMillis() + (86400000L * 3));
                us.getAvatarService().serverDialog("bạn vừa nhận được " + daisen.getPart().getName() + " 3 ngay");
                us.addItemToChests(daisen);
                Item traiTim = new Item(6793);
                traiTim.setExpired(System.currentTimeMillis() + (86400000L * 3));
                Item traiTim1 = new Item(6794);
                traiTim1.setExpired(System.currentTimeMillis() + (86400000L * 3));
                us.addItemToChests(traiTim);
                us.addItemToChests(traiTim1);

                Item hopqua = new Item(593,-1,140);
                //hopqua.setExpired(System.currentTimeMillis() + (86400000L * time));
                if(us.findItemInChests(593) !=null){
                    int quantity = us.findItemInChests(593).getQuantity();
                    us.findItemInChests(593).setQuantity(quantity+140);
                }else {
                    us.addItemToChests(hopqua);
                }


                break;

            case "20thang10":

                if(us.chests.size() >= us.getChestSlot()-6){
                    us.getAvatarService().serverDialog("chào bạn " + us.getUsername() +" bạn phải trống trên 7 ô rương");
                    return;
                }

                Item hopquask1 = new Item(683,-1,200);
                if(us.findItemInChests(683) !=null){
                    int quantity = us.findItemInChests(683).getQuantity();
                    us.findItemInChests(683).setQuantity(quantity+200);
                }else {
                    us.addItemToChests(hopquask1);
                }
                Item qs = new Item(593, -1, 100);
                us.addItemToChests(qs);

                Item canh = new Item(6723);
                canh.setExpired(System.currentTimeMillis() + (86400000L * 3));
                us.addItemToChests(canh);

                Item daixen = new Item(6670);

                if(us.getGender() == 2)
                {
                    daixen.setExpired(System.currentTimeMillis() + (86400000L * 3));
                    Item hoahong = new Item(5485);
                    hoahong.setExpired(System.currentTimeMillis() + (86400000L * 3));
                    us.addItemToChests(hoahong);
                }else {
                    daixen.setExpired(System.currentTimeMillis() + (86400000L * 1));
                }
                us.addItemToChests(daixen);
                Item traiTim11 = new Item(6793);
                traiTim11.setExpired(System.currentTimeMillis() + (86400000L * 3));
                Item traiTim1111 = new Item(6794);
                traiTim1111.setExpired(System.currentTimeMillis() + (86400000L * 3));
                us.addItemToChests(traiTim11);
                us.addItemToChests(traiTim1111);
                break;

            case "tanthu":

                us.updateXu(+500000);

                Item canCau = new Item(446);
                canCau.setExpired(System.currentTimeMillis() + (86400000L * 30));
                us.addItemToChests(canCau);

                Item NcRuong = new Item(3861,System.currentTimeMillis() + (86400000L * 30),1);
                us.addItemToChests(NcRuong);
                us.getAvatarService().serverDialog("tanthu bạn nhận được 1 thẻ nâng cấp rương 30 ngày, và 1 cần câu vip 30 ngày");

                Item vecau = new Item(460,System.currentTimeMillis() + (86400000L * 30),1);
                us.addItemToChests(vecau);
                break;


            case "denbu":

                Item hopqua12 = new Item(683,-1,100);
                //hopqua.setExpired(System.currentTimeMillis() + (86400000L * time));
                if(us.findItemInChests(683) !=null){
                    int quantity = us.findItemInChests(683).getQuantity();
                    us.findItemInChests(683).setQuantity(quantity+100);
                }else {
                    us.addItemToChests(hopqua12);
                }
                Item itemqs = new Item(593, -1, 200);
                us.addItemToChests(itemqs);
                us.getAvatarService().serverDialog("denbu bạn nhận được 100 hộp quà, và 100 thẻ quay số");
//                String[] data = giftCode.data.split(":");// Ví dụ data = "itemId:quantity"
//                int itemId = Integer.parseInt(data[0]);
//                int quantity = Integer.parseInt(data[1]);
//                Item useGift = new Item(itemId,-1,100);
//                UserManager.getInstance().find(userId).addItemToChests(useGift);
                break;
            case "trungthu":

                Item hopquatt = new Item(683,-1,200);
                //hopqua.setExpired(System.currentTimeMillis() + (86400000L * time));
                if(us.findItemInChests(683) !=null){
                    int quantity = us.findItemInChests(683).getQuantity();
                    us.findItemInChests(683).setQuantity(200);
                }else {
                    us.addItemToChests(hopquatt);
                }
                Item itemqs1 = new Item(593, -1, 400);
                us.addItemToChests(itemqs1);
                us.getAvatarService().serverDialog("denbu bạn nhận được 100 hộp quà, và 400 thẻ quay số");
//                String[] data = giftCode.data.split(":");// Ví dụ data = "itemId:quantity"
//                int itemId = Integer.parseInt(data[0]);
//                int quantity = Integer.parseInt(data[1]);
//                Item useGift = new Item(itemId,-1,100);
//                UserManager.getInstance().find(userId).addItemToChests(useGift);
                break;
            case "100tv":

                Item hopquask = new Item(683,-1,200);
                //hopqua.setExpired(System.currentTimeMillis() + (86400000L * time));
                if(us.findItemInChests(683) !=null){
                    int quantity = us.findItemInChests(683).getQuantity();
                    us.findItemInChests(683).setQuantity(quantity+200);
                }else {
                    us.addItemToChests(hopquask);
                }
                Item qs1 = new Item(593, -1, 200);
                us.addItemToChests(qs1);
                us.getAvatarService().serverDialog("100tv bạn nhận được 100 hộp quà và");
                if(us.getGender() == 2)
                {
                    Item nuhoangparty1 = new Item(3271);
                    nuhoangparty1.setExpired(System.currentTimeMillis() + (86400000L * 10));
                    Item nuhoangparty2 = new Item(3270);
                    nuhoangparty2.setExpired(System.currentTimeMillis() + (86400000L * 10));

                    Item nuhoangparty3 = new Item(2288);
                    nuhoangparty3.setExpired(System.currentTimeMillis() + (86400000L * 30));

                    us.addItemToChests(nuhoangparty1);
                    us.addItemToChests(nuhoangparty2);
                    us.addItemToChests(nuhoangparty3);

                }else {
                    Item onghoangparty1 = new Item(3276);
                    onghoangparty1.setExpired(System.currentTimeMillis() + (86400000L * 7));
                    Item onghoangparty2 = new Item(3277);
                    onghoangparty2.setExpired(System.currentTimeMillis() + (86400000L * 7));
                    Item onghoangparty3 = new Item(2288);
                    onghoangparty3.setExpired(System.currentTimeMillis() + (86400000L * 30));

                    us.addItemToChests(onghoangparty1);
                    us.addItemToChests(onghoangparty2);
                    us.addItemToChests(onghoangparty3);
                }

                break;
            default:
                us.getAvatarService().serverDialog("mã quà tặng không hợp lệ");
                // Có thể thêm thông báo cho người chơi hoặc ghi log lỗi
        }
    }


    private static class GiftCode {
        private int id;
        private String code;
        private String message;
        private String data;
        private java.sql.Timestamp startTime;
        private java.sql.Timestamp endTime;
        private int num;
        private int createBy;
        private java.sql.Timestamp createTime;

        public GiftCode(int id, String code, String message, String data, java.sql.Timestamp startTime,
                        java.sql.Timestamp endTime, int num, int createBy, java.sql.Timestamp createTime) {
            this.id = id;
            this.code = code;
            this.message = message;
            this.data = data;
            this.startTime = startTime;
            this.endTime = endTime;
            this.num = num;
            this.createBy = createBy;
            this.createTime = createTime;
        }

        // Getters and setters
    }
}
