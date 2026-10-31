package avatar.play.offline;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.message.MessageHandler;
import avatar.message.ParkMsgHandler;
import avatar.model.Boss;
import avatar.model.Npc;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.play.MapManager;
import avatar.play.Zone;
import avatar.server.Utils;
import lombok.Builder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class botPlayer extends User{
    private List<String> textChats;
    private static botPlayer instance;
    private static Map<Integer, List<int[]>> zoneCoordinates = new HashMap<>();//tọa độ boss di chuyển trong map
    private static int currentCoordinateIndex = 0;


    public botPlayer() {
        super();
        initializeCoordinates();
    }

    public static botPlayer getInstance() {
        if (instance == null) {
            instance = new botPlayer();
        }
        return instance;
    }
    // Khởi tạo danh sách tên và gender





    private void initializeCoordinates() {
        List<int[]> map11 = Arrays.asList(
                new int[]{120, 47},
                new int[]{34, 36},
                new int[]{146, 60},
                new int[]{107, 79},
                new int[]{164, 106},
                new int[]{58, 69},
                new int[]{78, 89},
                new int[]{162, 57},
                new int[]{226, 161},
                new int[]{274, 54},
                new int[]{470, 151},
                new int[]{500, 151},
                new int[]{524, 151},
                new int[]{189, 60},
                new int[]{62, 109},
                new int[]{150, 54}
        );
        zoneCoordinates.put(11, map11);

        List<int[]> map7 = Arrays.asList(
                new int[]{130, 48}
        );
        zoneCoordinates.put(7, map7);

        List<int[]> map1 = Arrays.asList(
                new int[]{165, 100}
        );
        zoneCoordinates.put(1, map1);

        List<int[]> map2 = Arrays.asList(
                new int[]{204, 85}
        );
        zoneCoordinates.put(2, map2);

        List<int[]> map3 = Arrays.asList(
                new int[]{288, 97}
        );
        zoneCoordinates.put(3, map3);

        List<int[]> map5 = Arrays.asList(
                new int[]{188, 89}
        );
        zoneCoordinates.put(5, map5);

        List<int[]> map8 = Arrays.asList(
                new int[]{264, 69}
        );
        zoneCoordinates.put(8, map8);

        List<int[]> map0 = Arrays.asList(
                new int[]{92, 50},
                new int[]{92, 81},
                new int[]{276, 157}
        );
        zoneCoordinates.put(0, map0);

        List<int[]> map9 = Arrays.asList(
                new int[]{258, 156},
                new int[]{494, 44},
                new int[]{534, 48},
                new int[]{543, 76},
                new int[]{450, 88}
        );
        zoneCoordinates.put(9, map9);

        List<int[]> map23 = Arrays.asList(
                new int[]{646, 24},
                new int[]{742, 48   },
                new int[]{702, 60},
                new int[]{746, 72},
                new int[]{654, 96},
                new int[]{750, 115},
                new int[]{830, 120}
        );
        zoneCoordinates.put(23, map23);

        List<int[]> map27 = Arrays.asList(
                new int[]{314, 80},
                new int[]{350, 104},
                new int[]{460, 60},
                new int[]{446, 88},
                new int[]{518, 40},
                new int[]{598, 28},
                new int[]{622, 48}
        );
        zoneCoordinates.put(27, map27);

    }
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final int TOTAL_BOSSES = 400000000; // Tổng số Boss muốn tạo
    public static int currentBossId = 8100; // ID bắt đầu cho Boss
    private static int bossCount = 0; // Đếm số lượng Boss đã được tạo
    private static final SecureRandom RANDOM = new SecureRandom();


    public void addBotToZone(User boss,int Map ,Zone zone) throws IOException {
        if (bossCount >= TOTAL_BOSSES) {
            return; // Dừng nếu đã tạo đủ số lượng Boss
        }

        boss.getWearing().clear();
        boss.setId(currentBossId++);
        assignRandomItemToBoss(boss);
        boss.bossMapId = Map;
        bossCount++;
        sendAndHandleMessages(boss);
        int mapId = zone.getMap().getId();

        if(mapId ==11){
            moveBot(boss,(byte) 0);
        }else{
            int randomInt = (int) (Math.random() * 2); // Kết quả sẽ là 0 hoặc 1
            moveBot(boss,(byte) randomInt);
        }

        List<int[]> coordinates = zoneCoordinates.get(mapId);
        if (coordinates != null && !coordinates.isEmpty()) {
            // Lấy tọa độ theo chỉ mục hiện tại
            int[] coordinate = coordinates.get(currentCoordinateIndex);
            moveBossXY(boss, coordinate[0], coordinate[1]);

            // Cập nhật chỉ mục tiếp theo (quay lại 0 nếu đã tới cuối danh sách)
            currentCoordinateIndex = (currentCoordinateIndex + 1) % coordinates.size();

        } else {
            System.err.println("Không có tọa độ cho bản đồ ID " + mapId);
        }
    }

    private void MoveArea(User boss,byte khu) throws IOException {
        ByteArrayOutputStream joinPank = new ByteArrayOutputStream();
        try (DataOutputStream dos2 = new DataOutputStream(joinPank)) {
            dos2.writeByte(boss.bossMapId);
            System.err.println("bot join " + boss.bossMapId);
            dos2.writeByte(khu);
            dos2.writeShort(boss.getX());//x
            dos2.writeShort(boss.getY());//y
            dos2.flush();
            byte[] dataJoinPak = joinPank.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.AVATAR_JOIN_PARK, dataJoinPak));
        }
        System.out.println("add boss khu :" + boss.getZone().getId());

    }


    private void assignRandomItemToBoss(User boss) {
        Map.Entry<String, Byte> nameAndGender = CharacterInfo.getRandomAndRemove();
        boss.setUsername(nameAndGender.getKey());
        boss.setGender(nameAndGender.getValue());

        String GET_PLAYER_DATA = "SELECT * FROM `players` WHERE `user_id` = ? AND `gender` = ? LIMIT 1;";
        boolean found = false;

        while (!found) {
            try (Connection connection = DbManager.getInstance().getConnection();
                 PreparedStatement ps = connection.prepareStatement(GET_PLAYER_DATA)) {

                int randomUserId = 151 + (int) (Math.random() * (910 - 151 + 1));
                ps.setInt(1, randomUserId);
                ps.setByte(2, boss.getGender()); // Đảm bảo gender khớp với boss

                try (ResultSet res = ps.executeQuery()) {
                    if (res.next()) {
                        found = true;  // Đặt cờ để thoát khỏi vòng lặp

                        // Lấy danh sách wearings từ kết quả
                        List<Item> wearings = new ArrayList<>();
                        JSONArray wearing = (JSONArray) JSONValue.parse(res.getString("wearing"));

                        for (Object o : wearing) {
                            JSONObject obj = (JSONObject) o;
                            int id = ((Long) obj.get("id")).intValue();
                            long expired = ((Long) obj.get("expired"));
                            int quantity = obj.containsKey("quantity") ? ((Long) obj.get("quantity")).intValue() : 1;

                            Item item = Item.builder().id(id)
                                    .quantity(quantity)
                                    .expired(expired)
                                    .build();

                            if (item.reliability() > 0) {
                                wearings.add(item);
                            }
                        }

                        // Thêm items vào wearing của boss
                        for (Item item : wearings) {
                            boss.addItemToWearing(item);
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                getService().serverMessage(ex.getMessage());
            }
        }
    }


    private void sendAndHandleMessages(User boss) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(0);
            dos.writeInt(1024);
            dos.writeUTF("MicroEmulator");
            dos.writeInt(512);
            dos.writeInt(1080);
            dos.writeInt(1920);
            dos.writeBoolean(true);
            dos.writeByte(0);
            dos.writeUTF("v1.0");
            dos.writeUTF("1");
            dos.writeUTF("2");
            dos.writeUTF("3");
            dos.flush();
            byte[] data = baos.toByteArray();

            MessageHandler handler = new MessageHandler(boss.session);
            handler.onMessage(new Message(Cmd.SET_PROVIDER, data));

            byte[] data2 = new byte[]{9};
            boss.session.getHandler(new Message(Cmd.GET_HANDLER, data2));
            if(boss.getId()>2000010000)
            {
                return;
            }
            MoveArea(boss,(byte)(Math.random() * 2));
        }
    }
    private void moveBot(User boss,byte khu) throws IOException {
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        try (DataOutputStream dos1 = new DataOutputStream(baos1)) {
            dos1.writeShort(boss.getX());//x
            dos1.writeShort(boss.getY());//y
            dos1.writeByte(khu);
            dos1.flush();
            byte[] data1 = baos1.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.MOVE_PARK, data1));;
        }
    }
    private void moveBossXY(User boss,int x,int y) throws IOException {
        System.out.println("Di chuyển boss tới tọa độ: (" + x + ", " + y + ")");
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        try (DataOutputStream dos1 = new DataOutputStream(baos1)) {
            dos1.writeShort(x);//x
            dos1.writeShort(y);//y
            dos1.writeByte(Math.random() < 0.5 ? 0 : 2);
            dos1.flush();
            byte[] data1 = baos1.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.MOVE_PARK, data1));
        }
    }

    public static Session createSession(User boss){
        //Cmd.SET_PROVIDER
        try {
            // Tạo một Socket (thay thế bằng thông tin kết nối thực tế)
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", 19128)); // Thay thế bằng địa chỉ IP và cổng thực tế
            int sessionId = boss.getId(); // Ví dụ về ID, có thể là bất kỳ giá trị nào phù hợp
            Session session = new Session(socket, sessionId);
            session.ip = "127.0.0.1";
            session.user = boss;
            session.connected = true;
            session.login = true;
            System.out.println("Session created with ID: " + session.id);
            return session;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    @Builder
    public void addChat(String chat) {
        textChats.add(chat);
    }

    public static void spawnBotesForMap(int mapId) {
        avatar.play.Map m = MapManager.getInstance().find(mapId);
        List<Zone> zones = m.getZones();

        // Lấy số lượng vị trí tương ứng với mapId
        List<int[]> coordinates = zoneCoordinates.get(mapId);
        int numBosses = (coordinates != null) ? coordinates.size() : 0;  // Số lượng boss bằng số lượng vị trí

        for (int i = 0; i < numBosses; i++) {
            botPlayer bot = new botPlayer(); // Tạo boss mới
            bot.session = createSession(bot);

            // Chọn zone ngẫu nhiên từ danh sách các zone trong bản đồ
            Zone randomZone = zones.get(i % zones.size());  // Sử dụng số chỉ để tránh lỗi vượt quá kích thước
            try {
                bot.addBotToZone(bot, mapId, randomZone);
                System.out.println("Bot " + i + " khu " + randomZone.getId() + " map " + mapId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
