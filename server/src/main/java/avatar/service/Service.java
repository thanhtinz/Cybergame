package avatar.service;

import avatar.constants.Cmd;
import avatar.convert.ItemConverter;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import avatar.play.Map;
import avatar.server.UserManager;
import avatar.server.Utils;
import org.apache.log4j.Logger;

public class Service {

    private static final Logger logger = Logger.getLogger(Service.class);
    protected Session session;

    public Service(Session cl) {
        this.session = cl;
    }

    public void removeItem(int userID, short itemID) {
        try {
            Message ms = new Message(Cmd.REMOVE_ITEM);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userID);
            ds.writeShort(itemID);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("removeItem() ", ex);
        }


    }

    public void serverDialog(String message) {
        try {
            Message ms = new Message(Cmd.SET_MONEY_ERROR);
            DataOutputStream ds = ms.writer();
            ds.writeUTF(message);
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendTextBoxPopup(int userId, int menuId, String message, int type) {
        try {
            Message ms = new Message(Cmd.TEXT_BOX);
            DataOutputStream ds = ms.writer();
            ds.writeInt(userId);
            ds.writeByte(menuId);
            ds.writeUTF(message);
            ds.writeByte(type);
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void serverMessage(String message) {
        try {
            Message ms = new Message(Cmd.SERVER_MESSAGE);
            DataOutputStream ds = ms.writer();
            ds.writeUTF(message);
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            logger.error("serverMessage ", e);
        }
    }

    public void serverInfo(String message) {
        try {
            Message ms = new Message(Cmd.SERVER_INFO);
            DataOutputStream ds = ms.writer();
            ds.writeUTF(message);
            ds.flush();
            sendMessage(ms);
        } catch (IOException e) {
            logger.error("serverMessage ", e);
        }
    }

    public void weather(byte weather) {
        try {
            System.out.println("weather: " + weather);
            Message ms = new Message(Cmd.WEATHER);
            DataOutputStream ds = ms.writer();
            ds.writeByte(weather);
            ds.flush();
            sendMessage(ms);
        } catch (IOException ex) {
            logger.error("weather() ", ex);
        }
    }

    public List<User> getTop10PlayersByXuFromBoss() {
        List<User> topPlayers = new ArrayList<>();
        String sql = "SELECT u.username, p.xu_from_boss " +
                "FROM players p " +
                "JOIN users u ON p.user_id = u.id " +
                "ORDER BY p.xu_from_boss DESC " +
                "LIMIT 10";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                int xuFromBoss = rs.getInt("xu_from_boss");

                // In ra giá trị đọc từ ResultSet để kiểm tra
                System.out.println("Username: " + username + ", Xu From Boss: " + xuFromBoss);

                User player = new User(username, xuFromBoss);
                topPlayers.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Xử lý ngoại lệ khi truy vấn thất bại
        }
        return topPlayers;
    }
    public List<User> getAllPlayersByxu_fromboss() {
        List<User> allPlayers = new ArrayList<>();
        String sql = "SELECT u.username, p.xu_from_boss " +
                "FROM players p " +
                "JOIN users u ON p.user_id = u.id " +
                "WHERE p.xu_from_boss > 0 " + // Chỉ lấy những người có TopPhaoLuong > 0
                "ORDER BY p.xu_from_boss DESC";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                int xu_from_boss = rs.getInt("xu_from_boss");

                User player = new User(username, xu_from_boss);
                allPlayers.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Xử lý ngoại lệ khi truy vấn thất bại
        }
        return allPlayers;
    }
    public int getUserRankXuBoss(User currentUser) {
        List<User> allPlayers = getAllPlayersByxu_fromboss();

        // Đảm bảo currentUser có giá trị TopPhaoLuong hợp lệ
        if (currentUser.getXu_from_boss() <= 0) {
            return -1; // Chỉ ra rằng người dùng không có mặt trong danh sách
        }

        int rank = 1;
        for (User player : allPlayers) {
            if (currentUser.getXu_from_boss() >= player.getXu_from_boss()) {
                return rank; // Trả về vị trí nếu giá trị của người dùng lớn hơn hoặc bằng
            }
            rank++;
        }

        return -1; // Nếu người dùng không có mặt trong danh sách
    }

    public List<User> getTopPhaoLuong() {
        List<User> topPlayers = new ArrayList<>();
        String sql = "SELECT u.username, p.TopPhaoLuong " +
                "FROM players p " +
                "JOIN users u ON p.user_id = u.id " +
                "ORDER BY p.TopPhaoLuong DESC " +
                "LIMIT 10";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                int TopPhaoLuong  = rs.getInt("TopPhaoLuong");

                // In ra giá trị đọc từ ResultSet để kiểm tra
                System.out.println("Username: " + username + ", phao luong " + TopPhaoLuong);

                User player = new User(username,0,TopPhaoLuong );
                topPlayers.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Xử lý ngoại lệ khi truy vấn thất bại
        }
        return topPlayers;
    }
    public List<User> getAllPlayersByPhaoLuong() {
        List<User> allPlayers = new ArrayList<>();
        String sql = "SELECT u.username, p.TopPhaoLuong " +
                "FROM players p " +
                "JOIN users u ON p.user_id = u.id " +
                "WHERE p.TopPhaoLuong > 0 " + // Chỉ lấy những người có TopPhaoLuong > 0
                "ORDER BY p.TopPhaoLuong DESC";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                int topPhaoLuong = rs.getInt("TopPhaoLuong");

                // Tạo đối tượng User từ dữ liệu truy vấn và thêm vào danh sách
                User player = new User(username, 0, topPhaoLuong);
                allPlayers.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Xử lý ngoại lệ khi truy vấn thất bại
        }
        return allPlayers;
    }
    public int getUserRankPhaoLuong(User currentUser) {
        List<User> allPlayers = getAllPlayersByPhaoLuong();

        // Đảm bảo currentUser có giá trị TopPhaoLuong hợp lệ
        if (currentUser.getTopPhaoLuong() <= 0) {
            return -1; // Chỉ ra rằng người dùng không có mặt trong danh sách
        }

        int rank = 1;
        for (User player : allPlayers) {
            if (currentUser.getTopPhaoLuong() >= player.getTopPhaoLuong()) {
                return rank; // Trả về vị trí nếu giá trị của người dùng lớn hơn hoặc bằng
            }
            rank++;
        }

        return -1; // Nếu người dùng không có mặt trong danh sách
    }

    public List<User> getTopPhaoXu() {
        List<User> topPlayers = new ArrayList<>();
        String sql = "SELECT u.username,u.id, p.TopPhaoXu " +
                "FROM players p " +
                "JOIN users u ON p.user_id = u.id " +
                "ORDER BY p.TopPhaoXu DESC " +
                "LIMIT 10";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                int TopPhaoXu  = rs.getInt("TopPhaoXu");
                int userid  = rs.getInt("id");
                // In ra giá trị đọc từ ResultSet để kiểm tra
                System.out.println("Username: " + username + ", phao xu " + TopPhaoXu);

                User player = new User(username,userid,0,TopPhaoXu );
                topPlayers.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Xử lý ngoại lệ khi truy vấn thất bại
        }
        return topPlayers;
    }
    public List<User> getAllPlayersByPhaoXu() {
        List<User> allPlayers = new ArrayList<>();
        String sql = "SELECT u.username,u.id, p.TopPhaoXu " +
                "FROM players p " +
                "JOIN users u ON p.user_id = u.id " +
                "WHERE p.TopPhaoXu > 0 " + // Chỉ lấy những người có TopPhaoLuong > 0
                "ORDER BY p.TopPhaoXu DESC";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                int userid  = rs.getInt("id");
                int topPhaoLuong = rs.getInt("TopPhaoXu");

                // Tạo đối tượng User từ dữ liệu truy vấn và thêm vào danh sách
                User player = new User(username,userid, 0, topPhaoLuong);
                allPlayers.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Xử lý ngoại lệ khi truy vấn thất bại
        }
        return allPlayers;
    }

    public int getUserRankPhaoXu(User currentUser) {
        List<User> allPlayers = getAllPlayersByPhaoXu();

        // Đảm bảo currentUser có giá trị TopPhaoLuong hợp lệ
        if (currentUser.getTopPhaoXu() <= 0) {
            return -1; // Chỉ ra rằng người dùng không có mặt trong danh sách
        }

        int rank = 1;
        for (User player : allPlayers) {
            if (currentUser.getTopPhaoXu() >= player.getTopPhaoXu()) {
                return rank; // Trả về vị trí nếu giá trị của người dùng lớn hơn hoặc bằng
            }
            rank++;
        }

        return -1; // Nếu người dùng không có mặt trong danh sách
    }



    public String DuDoanNY(User us){

        List<User> lstUs = UserManager.users;
        String result = "";
        byte gender = us.getGender();
        int randomIndex = Utils.nextInt(lstUs.size());
        User ulove = lstUs.get(randomIndex);
        String map = checkNameMap(ulove.getZone().getMap());
        if(gender == 1){
            if(ulove.getGender() == gender){
                result = ulove.getUsername() + " (cú có gai) đang ở" +
                        " Map : " + map + " Khu :" + ulove.getZone().getId();
            }else
                result = ulove.getUsername() + " (girl) đang ở" +
                        " Map : " + map + " Khu :" + ulove.getZone().getId();
        }else
        {
            if(ulove.getGender() == gender){
                result = ulove.getUsername() + "(Gái đó : v) đang ở" +
                        " Map : " + map + " Khu :" + ulove.getZone().getId();
            }else
                result = ulove.getUsername() + " (boy nè) đang ở" +
                        " Map : " + map + " Khu :" + ulove.getZone().getId();
        }
        return result;
    }

    public String checkNameMap(Map m){
        int mapid = m.getId();
        String Map = "";
        switch (mapid) {
            case 0:
                Map = "Khu Mặt trời";
                break;
            case 1:
                Map = "Khu quay số cũ";
                break;
            case 2:
                Map = "Khu đấu giá cũ";
                break;
            case 3:
                Map = "Khu ăn xin trái";
                break;
            case 4:
                Map = "Khu cưới , clan";
                break;
            case 5:
                Map = "Khu ăn xin phải";
                break;
            case 6:
                Map = "Khu cô giáo";
                break;
            case 7:
                Map = "Khu dưới cô giáo";
                break;
            case 9:
                Map = "Khu giải trí";
                break;
            case 10:
                Map = "Khu lễ đường";
                break;
            case 11:
                Map = "Bến xe công viên";
                break;
            case 13:
                Map = "Khu sinh thái";
                break;
            case 14:
                Map = "Khu câu cá rô";
                break;
            case 15:
                Map = "Khu câu cá lóc";
                break;
            case 16:
                Map = "Khu câu cá mập";
                break;
            case 17:
                Map = "Khu ngoại ô";
                break;
            case 18:
                Map = "Trong nhà tù";
                break;
            case 19:
                Map = "Trong lễ đường";
                break;
            case 23:
                Map = "Khu mua sắm";
                break;
        }
        return Map;
    }


    public void sendMessage(Message ms) {
        session.sendMessage(ms);
    }
}
