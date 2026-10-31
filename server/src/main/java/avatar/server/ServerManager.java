package avatar.server;

import avatar.constants.Cmd;
import avatar.message.CasinoMsgHandler;
import avatar.minigame.TaiXiu;
import avatar.model.*;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.item.PartManager;
import avatar.network.Session;
import avatar.play.Zone;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import avatar.play.offline.botPlayer;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import avatar.network.Message;

import java.sql.ResultSet;
import java.sql.PreparedStatement;

import avatar.play.Map;
import avatar.play.MapManager;
import avatar.play.NpcManager;

import java.net.ServerSocket;

public class ServerManager {

    public static String cityName;
    public static String hashSettings;
    public static boolean active;
    protected static short port;
    public static String notify;
    public static int bigImgVersion;
    public static int partVersion;
    public static int bigItemImgVersion;
    public static int itemTypeVersion;
    public static int itemVersion;
    public static int objectVersion;
    public static String resHDPath;
    public static String resMediumPath;
    public static int numClients;
    public static ArrayList<Session> clients;
    protected static ServerSocket server;
    protected static boolean start;
    protected static int id;
    private static boolean debug;

    private static void loadConfigFile() {
        try {
            FileInputStream input = new FileInputStream(new File("config.properties"));
            Properties props = new Properties();
            props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            ServerManager.port = Short.parseShort(props.getProperty("server.port"));
            ServerManager.cityName = props.getProperty("game.city.name");
            ServerManager.active = Boolean.parseBoolean(props.getProperty("server.active"));
            ServerManager.debug = Boolean.parseBoolean(props.getProperty("server.debug"));
            if (props.containsKey("game.notify")) {
                ServerManager.notify = props.getProperty("game.notify");
            }
            ServerManager.bigImgVersion = Short.parseShort(props.getProperty("game.big.image.version"));
            ServerManager.partVersion = Short.parseShort(props.getProperty("game.part.version"));
            ServerManager.bigItemImgVersion = Short.parseShort(props.getProperty("game.big.item.image.version"));
            ServerManager.itemTypeVersion = Short.parseShort(props.getProperty("game.itemtype.version"));
            ServerManager.itemVersion = Integer.parseInt(props.getProperty("game.item.version"));
            ServerManager.objectVersion = Integer.parseInt(props.getProperty("game.object.version"));
            ServerManager.resHDPath = props.getProperty("game.resources.hd.path");
            ServerManager.resMediumPath = props.getProperty("game.resources.medium.path");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected static void loadSettings() {
        System.out.println("Load settings in database");
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM `settings`;");
             ResultSet res = ps.executeQuery();) {
            HashMap<String, String> settings = new HashMap<>();
            while (res.next()) {
                String name = res.getString("name");
                String value = res.getString("value");
                settings.put(name, value);
            }
            if (settings.containsKey("hash_settings")) {
                ServerManager.hashSettings = settings.get("hash_settings");
            }
            if (settings.containsKey("bao_tri")) {
                ServerManager.active = Boolean.parseBoolean(settings.get("bao_tri"));
            }
            if (settings.containsKey("thong_bao")) {
                ServerManager.notify = settings.get("thong_bao");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    protected static void init() {
        ServerManager.start = false;
        DbManager.getInstance().start();
        loadConfigFile();
        loadSettings();
        GameData.getInstance().load();
        PartManager.getInstance().load();
        FoodManager.getInstance().load();
        int numMap = 60;
        for (int i = 0; i < numMap; ++i) {
            MapManager.getInstance().add(new Map(i, 0, 10));
        }
        System.out.println("Load NPC data start ...");
        loadNpcData();
        System.out.println("Reset player online ...");
        DbManager.getInstance().executeUpdate("UPDATE `players` SET `is_online` = 0, `client_id` = -1");
        System.out.println("Reset player online successfully");
        BoardManager.getInstance().initBoards();

    }

    private static void loadNpcData() {
        int numNPC = 0;
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM `npc`;");
             ResultSet res = ps.executeQuery();) {
            while (res.next()) {
                int botID = res.getInt("id");
                String botName = res.getString("name");
                byte map = res.getByte("map");
                short X = res.getShort("x");
                short Y = res.getShort("y");
                ArrayList<Item> items = new ArrayList<>();
                JSONArray listItem = (JSONArray) JSONValue.parse(res.getString("items"));
                for (Object o : listItem) {
                    Item item = new Item(((Long) o).intValue());
                    items.add(item);
                }
                List<String> chat = new ArrayList<>();
                JSONArray listChat = (JSONArray) JSONValue.parse(res.getString("chat"));
                for (Object o : listChat) {
                    chat.add((String) o);
                }
                Map m = MapManager.getInstance().find(map);
                if (m != null) {
                    int zoneCount = 0;
                    List<Zone> zones = m.getZones();
                    for (Zone z : zones) {
                        zoneCount++;
                        if (zoneCount >= 3) {
                            break; // Dừng vòng lặp khi đã xử lý đủ 3 khu vực
                        }

                        // Kiểm tra nếu botID là 186
                        if (botID == 186) {
                            // Chỉ load 1 NPC và lưu lại
                            Npc npc = Npc.builder()
                                    .id(botID)
                                    .name(botName)
                                    .x(X)
                                    .y(Y)
                                    .wearing(items)
                                    .build();
                            npc.setTextChats(chat);
                            NpcManager.getInstance().add(npc);
                            z.enter(npc, X, Y);
                            break; // Dừng lại sau khi đã thêm NPC
                        }
                        else if (botID == 864) { // Thay đổi ID theo ý muốn
                            Npc npc = Npc.builder()
                                    .id(botID)
                                    .name(botName) // Tên boss, thay đổi theo ý muốn
                                    .x(X)
                                    .y(Y)
                                    .wearing(items)
                                    .build();
                            npc.setTextChats(chat);
                            TaiXiu.getInstance().setNpcTaiXiu(npc);
                            NpcManager.getInstance().add(npc);
                            z.enter(npc, X, Y);
                            break;
                        } else {
                            // Tạo NPC cho các ID khác
                            Npc npc = Npc.builder()
                                    .id(botID)
                                    .name(botName)
                                    .x(X)
                                    .y(Y)
                                    .wearing(items)
                                    .build();
                            npc.setTextChats(chat);
                            NpcManager.getInstance().add(npc);
                            z.enter(npc, X, Y);
                        }

                    }
                }
                System.out.println("  + NPC " + Utils.removeAccent(botName) + " - " + botID);
                ++numNPC;
            }
            //auto đấu giá
            //AuctionScheduler scheduler = new AuctionScheduler();
            //scheduler.startScheduling();
            res.close();
            ps.close();
            System.out.println("Load success " + numNPC + " NPC !");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }


    protected static void start() {
        System.out.println("Start socket port = " + ServerManager.port);
        try {
            ServerManager.clients = new ArrayList<>();
            ServerManager.server = new ServerSocket(ServerManager.port);
            ServerManager.id = 0;
            ServerManager.numClients = 0;
            ServerManager.start = true;
            System.out.println("Start server Success !");
            List<Integer> mapIds = List.of(11, 1, 7, 2, 3, 5, 8);
            for (int mapId : mapIds) {
                Boss.spawnBossesForMap(mapId, 2);
            }
            //call bot join map
            List<Integer> mapIds1 = List.of(11,9,23,0);

            // bật bot người chơi
//            for (int mapId : mapIds1) {
//                botPlayer.getInstance().spawnBotesForMap(mapId);
//            }

            while (ServerManager.start) {
                try {
                    Socket client = ServerManager.server.accept();
                    Session cl = new Session(client, ++ServerManager.id);
                    ServerManager.clients.add(cl);
                    ++ServerManager.numClients;
                    log("Accept socket " + cl + " done!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void stop() {
        if (ServerManager.start) {
            System.out.println("--->[ CLOSE ]<---");
            close();
            ServerManager.start = false;
            System.gc();
        }
    }

    protected static void close() {
        try {
            ServerManager.server.close();
            ServerManager.server = null;
            while (ServerManager.clients.size() > 0) {
                Session c = ServerManager.clients.get(0);
                c.close();
                --ServerManager.numClients;
            }
            ServerManager.clients = null;
            DbManager.getInstance().shutdown();
            System.gc();
            System.out.println("End socket");
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    public static void log(String s) {
        if (ServerManager.debug) {
            System.out.println(s);
        }
    }

    public static void disconnect(Session cl) throws IOException {
        synchronized (ServerManager.clients) {
            ServerManager.clients.remove(cl);
            --ServerManager.numClients;
            System.out.println("Disconnect client: " + cl);

            BoardInfo board = BoardManager.getInstance().findUserBoard(cl.user);
            if(board != null) {
                ByteArrayOutputStream leaveBoard = new ByteArrayOutputStream();
                try (DataOutputStream dos2 = new DataOutputStream(leaveBoard)) {

                    dos2.writeByte(cl.user.getRoomID());
                    dos2.writeByte(board.boardID);
                    dos2.flush();
                    byte[] dataJoinPak = leaveBoard.toByteArray();
                    CasinoMsgHandler csnMsgHandler1 = new CasinoMsgHandler(cl);
                    csnMsgHandler1.onMessage(new Message(Cmd.LEAVE_BOARD, dataJoinPak));
                }
            }

            String UNLOCK_ACCOUNT_SQL = "UPDATE users SET login_lock = 0 WHERE id = ?";
            try (Connection connection = DbManager.getInstance().getConnection();
                 PreparedStatement ps = connection.prepareStatement(UNLOCK_ACCOUNT_SQL)) {
                ps.setInt(1, cl.user.getId());
                ps.executeUpdate();
                System.out.println("Account unlocked successfully.");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }


        }
    }

    public static void joinAreaMessage(User us, Message ms) throws IOException {
        byte map = 11;
        byte area = -1;
        short x = us.getX();
        short y = us.getY();
        if (ms != null && ms.reader() != null) {
            try {
                // Đọc dữ liệu từ Message nếu có
                map = ms.reader().readByte();
                area = ms.reader().readByte();
                x = ms.reader().readShort();
                y = ms.reader().readShort();
            } catch (IOException e) {
                // Xử lý lỗi đọc dữ liệu
                e.printStackTrace();
                // Bạn có thể đặt các giá trị mặc định hoặc thông báo lỗi ở đây nếu cần
            }
        }
        if (area < 0) {
            area = joinAreaAutoNumber(map);
        }
        Map m = MapManager.getInstance().find(map);
        if (m != null) {
            List<Zone> zones = m.getZones();
            Zone zone = zones.get(area);
            zone.enter(us, x, y);
            System.out.println("map: " + map + " area: " + area + " x: " + x + " y: " + y);
        }
    }

    private static byte joinAreaAutoNumber(byte map) {
        Map m = MapManager.getInstance().find(map);
        List<Zone> zones = m.getZones();
        int i = 0;
        for (Zone z : zones) {
            if (z.getPlayers().size() <= 10) {
                if (z.getPlayers().size() > 2) {
                    //getAvatarService().serverDialog("Khu vực đã đầy");
                }
                return (byte) i;
            }
            i++;
        }
        return 0;
    }
}
