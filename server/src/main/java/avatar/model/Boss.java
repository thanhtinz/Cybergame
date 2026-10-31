package avatar.model;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.lib.RandomCollection;
import avatar.message.MessageHandler;
import avatar.message.ParkMsgHandler;
import avatar.network.Message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Arrays;

import avatar.network.Session;
import avatar.play.MapManager;
import avatar.play.Zone;
import avatar.server.ServerManager;
import avatar.server.UserManager;
import avatar.server.Utils;
import avatar.service.EffectService;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import static avatar.server.ServerManager.clients;

public class Boss extends User {
    @Getter
    @Setter
    private List<String> textChats;
    private Map<Integer, List<int[]>> zoneCoordinates = new HashMap<>();//tọa độ boss di chuyển trong map
    public Boss() {
        super();
        initializeCoordinates();

         //tọa độ boss dichuyeeren
//        List<int[]> map11 = Arrays.asList(
//                new int[]{182, 121},
//                new int[]{282, 142},
//                new int[]{282, 88},
//                new int[]{326, 150}
//        );
//        zoneCoordinates.put(11, map11);
//        List<int[]> map7 = Arrays.asList(
//                new int[]{212, 90},
//                new int[]{112, 118},
//                new int[]{292, 118}
//        );
//        zoneCoordinates.put(7, map7);
//        List<int[]> map1 = Arrays.asList(
//                new int[]{80, 105},
//                new int[]{244, 109},
//                new int[]{172, 154}
//        );
//        zoneCoordinates.put(1, map1);

        //autoChatBot.start();
    }

    private void initializeCoordinates() {
        List<int[]> map11 = Arrays.asList(
                new int[]{270, 100}
        );
        zoneCoordinates.put(11, map11);

        List<int[]> map7 = Arrays.asList(
                new int[]{216, 97}
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
    }


    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final int TOTAL_BOSSES = 400000000; // Tổng số Boss muốn tạo
    public static int currentBossId = 1001 + Npc.ID_ADD; // ID bắt đầu cho Boss
    private static int bossCount = 0; // Đếm số lượng Boss đã được tạo
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Thread autoChatBot = new Thread(() -> {
        while (true) {
            try {
                if (textChats == null) {
                    textChats = new ArrayList<>(); // Hoặc khởi tạo với một giá trị mặc định
                }
                for (String text : textChats) {
                    getMapService().chat(this, text);
                    Thread.sleep(6000);
                }
                if (textChats == null || textChats.size() == 0) {
                    Thread.sleep(10000);
                }
//                int[][] pairs = {
//                        {23, 24},
//                        {25, 26},
//                };
//                if(this.getHP()>0)
//                {
//                    Random rand = new Random();
//                    int randomIndex = rand.nextInt(pairs.length);
//                    int[] selectedPair = pairs[randomIndex];
//                    BossSkillRanDomUser((byte)selectedPair[0], (byte)selectedPair[1]);
//
//                    // Tạo đối tượng Random để chọn tọa độ ngẫu nhiên
//                    int mapId = this.getZone().getMap().getId();
//                    List<int[]> coordinates = zoneCoordinates.get(mapId);
//                    if (coordinates != null && !coordinates.isEmpty()) {
//                        int[] randomCoordinate = coordinates.get(new Random().nextInt(coordinates.size()));
//                        moveBossXY(this, randomCoordinate[0], randomCoordinate[1]);
//                    } else {
//                        // Nếu không có tọa độ nào, có thể xử lý trường hợp này ở đây
//                        System.err.println("Không có tọa độ cho bản đồ ID " + mapId);
//                    }
//                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt(); // Đảm bảo xử lý gián đoạn
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
        }}
    });



    public synchronized void handleBossDefeat(Boss boss, User us) throws IOException {
        //update lượt boss.
//        us.applyStoredXuUpdate();
//        DbManager.getInstance().executeUpdate("UPDATE `players` SET `xu_from_boss` = ? WHERE `user_id` = ? LIMIT 1;",
//                us.xu_from_boss, us.getId());
        String username = us.getUsername();

        if(us.getHunger() < 100){
            int idItems = 2385;
            Item keoAcMa = new Item(2385,-1,1);
            if(us.findItemInChests(idItems) !=null){
                int quantity = us.findItemInChests(idItems).getQuantity();
                us.findItemInChests(idItems).setQuantity(quantity+1);
            }else {
                us.addItemToChests(keoAcMa);
            }
            us.updateHunger(+1);//+1 cho slot 100 hop
            us.getAvatarService().SendTabmsg("Bạn vừa nhận được 1 "+ " " + keoAcMa.getPart().getName());
        }

        if(boss.getWearing().get(1).getId() == 5112 && us.getHappy() < 100){

            us.updateHappy(+1);//+1 cho slot 100 hop
            Item hopqua = new Item(5532,System.currentTimeMillis() + (86400000L * 7),1);
            us.addItemToChests(hopqua);
            UserManager.users.forEach(user -> {
                user.getAvatarService().serverInfo("Chúc mừng bạn : " + us.getUsername()+" đã Kill được trùm ma bí và nhận 1 hộp quà Ma Quái ("+us.getHappy() +"/100) mọi người đều ngưỡng mộ.");
            });
        }

        String message = String.format("Khá lắm bạn %s đã kill được %s", username, boss.getUsername().substring(3, boss.getUsername().length() - 6));
        List<String> newMessages = Arrays.asList(message,"Ta sẽ quay lại sau!!!");
        this.textChats = new ArrayList<>(newMessages);

        for (String chatMessage : textChats) {
            getMapService().chat(boss, chatMessage);
            textChats.remove(chatMessage);
        }

        scheduler.schedule(() -> {
            try {
                LocalTime now = LocalTime.now();
                LocalTime tenAM = LocalTime.of(10, 0);
                LocalTime twoPM = LocalTime.of(14, 0);
                LocalTime sevenPM = LocalTime.of(17, 0);
                LocalTime elevenPM = LocalTime.of(23, 0);

                //tạo qu trong time
                if ((now.isAfter(tenAM) && now.isBefore(twoPM)) || (now.isAfter(sevenPM) && now.isBefore(elevenPM))) {
                    createNearbyGiftBoxes(boss, boss.getZone(), boss.getX(), boss.getY(), Boss.currentBossId + 10000);
                }
                    //boss.session.close();
                Utils random = null;
                avatar.play.Map m = MapManager.getInstance().find(boss.getBossMapId());
                List<Zone> zones = m.getZones();
                Zone randomZone = zones.get(random.nextInt(zones.size()));
                boss.getZone().leave(boss);
                addBossToZone(boss,boss.bossMapId,randomZone,(short) 0,(short) 0,Utils.nextInt(70000,120000));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 5, TimeUnit.SECONDS); // 4 giây trễ trước khi thực hiện các hành động khác

        // Gửi hiệu ứng cho người chơi trong khu vực
//        boss.getZone().getPlayers().forEach(u -> {
//            EffectService.createEffect()
//                    .session(u.session)
//                    .id((byte) 45)
//                    .style((byte) 0)
//                    .loopLimit((byte) 6)
//                    .loop((short) 1) // Số lần lặp lại
//                    .loopType((byte) 1)
//                    .radius((short) 5)
//                    .idPlayer(boss.getId())
//                    .send();
//        });
    }

    public synchronized void hanlderNhatHopQua(User boss, User us) throws IOException {
        us.getAvatarService().serverDialog("bạn đã nhặt được hộp quà");
        //int time = Utils.getRandomInArray(new int[]{3, 7, 15, 30});
        Item hopqua = new Item(683,-1,1);
        //hopqua.setExpired(System.currentTimeMillis() + (86400000L * time));

        if(us.findItemInChests(683) !=null){
            int quantity = us.findItemInChests(683).getQuantity();
            us.findItemInChests(683).setQuantity(quantity+1);
        }else {
            us.addItemToChests(hopqua);
        }
        ServerManager.disconnect(boss.session);
        boss.session.close();
    }


    public void addBossToZone(User boss,int Map ,Zone zone, short x, short y,int hp) throws IOException {
        if (bossCount >= TOTAL_BOSSES) {
            return; // Dừng nếu đã tạo đủ số lượng Boss
        }

        boss.getWearing().clear();
        boss.setId(currentBossId++);
        boss.setDefeated(false);
//        List<String> chatMessages = Arrays.asList("YAAAA", "YOOOO");
//        ((Boss) boss).setTextChats(chatMessages);
        assignRandomItemToBoss(boss);
        boss.setHP(hp);
        if(boss.getWearing().get(1).getId() == 5112){
            boss.setHP(hp+90000);
            List<String> chatMessages = Arrays.asList("gãi ngứa hả tên kia", "Mau nộp kẹo cho taaaa");
            ((Boss) boss).setTextChats(chatMessages);
        }
        boss.bossMapId = Map;
        bossCount++; // Tăng số lượng Boss đã tạo
        sendAndHandleMessages(boss);
        moveBoss(boss);
        int mapId = zone.getMap().getId();
///
        List<int[]> coordinates = zoneCoordinates.get(mapId);
        if (coordinates != null && !coordinates.isEmpty()) {
            int[] randomCoordinate = coordinates.get(new Random().nextInt(coordinates.size()));
            moveBossXY(boss, randomCoordinate[0], randomCoordinate[1]);
        } else {
            // Nếu không có tọa độ nào, có thể xử lý trường hợp này ở đây
            System.err.println("Không có tọa độ cho bản đồ ID " + mapId);
        }
    }


    private void MoveArea(User boss) throws IOException {
        ByteArrayOutputStream joinPank = new ByteArrayOutputStream();
        try (DataOutputStream dos2 = new DataOutputStream(joinPank)) {
            dos2.writeByte(boss.bossMapId);
            System.err.println("joinmaopboss " + boss.bossMapId);
            dos2.writeByte(Utils.nextInt(9));
            dos2.writeShort(boss.getX());//x
            dos2.writeShort(boss.getY());//y
            dos2.flush();
            byte[] dataJoinPak = joinPank.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.AVATAR_JOIN_PARK, dataJoinPak));
        }
        System.out.println("add boss khu :" + boss.getZone().getId());

    }

    private User createBoss(short x, short y,int id) {
        User boss = new Boss();
        boss.setId(id);
        boss.setX(x);
        boss.setY(y);
        return boss;
    }

    private void createGiftBox(Zone zone, short x, short y, int giftId) throws IOException {
        User giftBox = createBoss(x, y, giftId);
        assignGiftItemToBoss(giftBox);// Gán item cho hộp quà
        giftBox.setUsername("");
        giftBox.session = createSession(giftBox);
        giftBox.setSpam(10);
        sendAndHandleMessages(giftBox);
        addGiftToZone(giftBox,zone);
        moveGift(giftBox);
    }

    public void createNearbyGiftBoxes(User boss, Zone zone, short x, short y, int baseGiftId) throws IOException {
        // Tạo hộp quà ở các vị trí gần Boss
        createGiftBox(zone, (short) (boss.getX()+(short)20),(short) (boss.getY()+(short)20),baseGiftId);
        createGiftBox(zone, (short) (boss.getX()-(short)20), (short) (boss.getY()-(short)20), baseGiftId + 1);
        createGiftBox(zone, (short) (boss.getX()+(short)20), (short) (boss.getY()-(short)20), baseGiftId + 2);
        createGiftBox(zone, (short) (boss.getX()-(short)20), (short) (boss.getY()+(short)20), baseGiftId + 3);
    }

    private void assignGiftItemToBoss(User boss) {
        // Gán item cụ thể cho hộp quà phân thân, nếu khác với Boss chính
        List<Integer> giftItems = Arrays.asList(2215, 2215, 2215); // Ví dụ các item cho hộp quà
        int randomItemId = giftItems.get(new Random().nextInt(giftItems.size()));
        boss.addItemToWearing(new Item(randomItemId));
    }

//đồ của boss
    private void assignRandomItemToBoss(User boss) {
        List<Integer> itemIds = Arrays.asList(0,5112, 2469, 2470,6428,6431,4304);//sen bo hung
        List<Integer> itemIds1 = Arrays.asList(0,2468, 2469, 2470,2282,4304);//ma bu
        List<Integer> itemIds2 = Arrays.asList(0,8,2471, 2472, 2473,3495,4304);//ma bu map
        List<Integer> itemIds3 = Arrays.asList(10, 2049, 2050, 2051);
        List<Integer> itemIds4 = Arrays.asList(10, 2099, 2100, 2101);
        List<Integer> itemIds5 = Arrays.asList(10, 6036, 6037, 6038);


        List<Integer> itemIds6 = Arrays.asList(10, 2049, 2050, 2051);
        List<Integer> itemIds7 = Arrays.asList(10, 2099, 2100, 2101);


        Map<List<Integer>, String> itemListToName = new HashMap<>();
        itemListToName.put(itemIds, "TrumMaBi");
        itemListToName.put(itemIds1, "MaBi");
        itemListToName.put(itemIds2, "Frankeinstein");
        itemListToName.put(itemIds3, "XuongKho");
        itemListToName.put(itemIds4, "XacUop");
        itemListToName.put(itemIds5, "TrumXacUop");

        itemListToName.put(itemIds6, "XuongKho");
        itemListToName.put(itemIds7, "XacUop");

        List<List<Integer>> allItemLists = Arrays.asList(itemIds,itemIds1,itemIds2,itemIds3,itemIds4,itemIds5,itemIds6,itemIds7);
        Random random = new Random();
        int randomIndex = random.nextInt(allItemLists.size());
        List<Integer> randomList = allItemLists.get(randomIndex);
        String bossName = itemListToName.get(randomList);

        for (int itemId : randomList) {
            Item item = new Item(itemId);
            boss.addItemToWearing(item);
        }
        String bossUsername = generateRandomUsername(4).toLowerCase();
        String bossUsername1 = generateRandomUsername(3).toLowerCase();;
        boss.setUsername(bossUsername+bossName+bossUsername1);
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
            MoveArea(boss);
        }
    }

    private void moveBoss(User boss) throws IOException {
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        try (DataOutputStream dos1 = new DataOutputStream(baos1)) {
            dos1.writeShort(boss.getX());//x
            dos1.writeShort(boss.getY());//y
            int ranArea = Utils.nextInt(9);
            dos1.writeByte((byte)ranArea);
            dos1.flush();
            byte[] data1 = baos1.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.MOVE_PARK, data1));;
        }
    }

    private void addGiftToZone(User gift,Zone zone) {
        ByteArrayOutputStream joinPank = new ByteArrayOutputStream();
        try (DataOutputStream dos2 = new DataOutputStream(joinPank)) {
            dos2.writeByte(zone.getMap().getId());
            dos2.writeByte(zone.getId());
            dos2.writeShort(gift.getX());//x
            dos2.writeShort(gift.getY());//y
            dos2.flush();
            byte[] dataJoinPak = joinPank.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(gift.session);
            parkMsgHandler1.onMessage(new Message(Cmd.AVATAR_JOIN_PARK, dataJoinPak));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void moveGift(User boss) throws IOException {
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        try (DataOutputStream dos1 = new DataOutputStream(baos1)) {
            dos1.writeShort(boss.getX());//x
            dos1.writeShort(boss.getY());//y
            dos1.writeByte(0);
            dos1.flush();
            byte[] data1 = baos1.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.MOVE_PARK, data1));
            //getMapService().chat(this, "ta đến rồi đây");
            //System.out.println("gift move : X = " + boss.getX() + ", y = " + boss.getY());

            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    boss.session.close();
                }
            }, 120000); // 2 phút = 120000 ms

        }
    }

    private void moveBossXY(User boss,int x,int y) throws IOException {
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        try (DataOutputStream dos1 = new DataOutputStream(baos1)) {
            dos1.writeShort(x);//x
            dos1.writeShort(y);//y
            dos1.writeByte(2);
            dos1.flush();
            byte[] data1 = baos1.toByteArray();
            ParkMsgHandler parkMsgHandler1 = new ParkMsgHandler(boss.session);
            parkMsgHandler1.onMessage(new Message(Cmd.MOVE_PARK, data1));
            //System.out.println("boss move : X = " + boss.getX() + ", y = " + boss.getY());
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
    @Override
    public void sendMessage(Message ms) {

    }
    public void BossSkillRanDomUser(byte skill1,byte skill2){
        Random rand = new Random();
        List<User> players = this.session.user.getZone().getPlayers();
        User randomPlayer = null;
        while (randomPlayer == null) {
            int rplayerIndex = rand.nextInt(players.size());
            User playerss = players.get(rplayerIndex);

            if (playerss.getId() < Npc.ID_ADD) {
                randomPlayer = playerss;
            }
        }
        for (User player : players) {
            EffectService.createEffect()
                    .session(player.session)
                    .id(skill1)
                    .style((byte) 0)
                    .loopLimit((byte) 5)
                    .loop((short) 3)
                    .loopType((byte) 1)
                    .radius((short) 1)
                    .idPlayer(this.session.user.getId())
                    .send();
            EffectService.createEffect()
                    .session(player.session)
                    .id(skill2)
                    .style((byte) 0)
                    .loopLimit((byte) 5)
                    .loop((short) 3)
                    .loopType((byte) 1)
                    .radius((short) 1)
                    .idPlayer(randomPlayer.getId())
                    .send();
        };
    }
    public static String generateRandomUsername(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(randomIndex));
        }
        return sb.toString();
    }

    public static void spawnBossesForMap(int mapId, int numBosses) {
        Utils random = null;
        avatar.play.Map m = MapManager.getInstance().find(mapId);
        List<Zone> zones = m.getZones();
        for (int i = 0; i < numBosses; i++) {
            Boss boss = new Boss(); // Tạo boss mới
            List<String> chatMessages = Arrays.asList("YAAAA", "YOOOO");
            ((Boss) boss).setTextChats(chatMessages);
            boss.session = createSession(boss);

            Zone randomZone = zones.get(random.nextInt(zones.size()));
            try {
                boss.addBossToZone(boss,mapId,randomZone, (short) 50, (short) 50, (int) 50000);
                System.out.println("Boss " + i + " khu " + randomZone.getId() + " map " + mapId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}