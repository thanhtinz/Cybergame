package avatar.model;

import avatar.Farm.*;
import avatar.common.BossShopItem;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.item.Part;
import avatar.lucky.DialLucky;
import avatar.lucky.GiftBox;
import avatar.network.Session;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import avatar.service.*;
import org.json.simple.JSONValue;
import org.json.simple.JSONObject;

import java.io.IOException;

import avatar.network.Message;
import avatar.play.MapService;

import avatar.play.Zone;
import avatar.server.GameString;
import avatar.server.ServerManager;
import avatar.server.UserManager;
import avatar.server.Utils;

import java.util.Date;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;

@Getter
@Setter
public class User {
    private static int chestLevel;
    private static final int[] UPGRADE_COST_COINS = {0, 0, 0, 20000, 50000, 100000, 200000, 200000, 500000, 600000, 70000, 0, 1000000, 1200000, 1500000, 1700000, 2000000, 2500000, 2700000, 3000000, 4000000, 5000000};
    private static final int[] UPGRADE_COST_GOLD = {0, 0, 0, 0, 0, 0, 0, 200, 500, 600, 700, 1000, 1000, 1200, 1500, 1700, 2000, 2500, 2700, 3000, 4000, 5000};
    public boolean AutoFish;

    public int bossMapId;
    public int TopPhaoLuong;
    public int TopPhaoXu;

    public int xu_from_boss;
    private boolean spamclickBoss;

    private int intSpanboss;
    public int spam;

    public int HP;

    private boolean isDefeated;
    private boolean isSpam;
/// //////hen ho
    private int idUsHenHo;
    private String namehh;
    private List<Item> wearingMarry;
    private int levelMarry;
    private int PerLevelMarry;
    private int imginfo;
    private String tenNhan;
/// //////////
    private int storedXuUpdate; // Biến lưu trữ xu đã cập nhật
    private static final Logger logger = Logger.getLogger(User.class);
    public Session session;
    private int id;
    private String username;
    private String password;
    private short idFish;
    private byte gender;
    public long xu;
    public int luong;
    public int luongKhoa;

    private List<Integer> availableSkills;
    private int useSkill;
    private int dame;
    private int dameToXu;
    public long randomTimeInMillis; // Random time in milliseconds
    public long lastTimeSet;
    public int correctAnswer; // Biến lưu trữ kết quả đúng của phép toán

    public int xeng;
    private short clanID;
    private byte role;
    private byte star;
    private int leverMain;
    private int expMain;
    private int leverFarm;
    private byte leverPercen;
    private int expFarm;
    private byte friendly;
    private short crazy;// item sk quatyti
    private byte stylish;
    private byte happy;// 100 hop qua
    private byte hunger;//100 kẹo tối đa
    private byte chestSlot;
    private byte chestHomeSlot;
    private int scores;// điểm sk
    private List<Item> wearing;
    public List<Item> chests;
    public List<Item> chestsHome;


    /// /fam
    public List<LandItem> landItems = new ArrayList<>();
    public List<Animal> Animal = new ArrayList<>();
    public List<HatGiong> hatgiong = new ArrayList<>();
    public List<NongSan> NongSan = new ArrayList<>();
    public List<PhanBon> PhanBon = new ArrayList<>();
    public List<NongSanDacBiet> NongSanDacBiet = new ArrayList<>();
/// /////
    private Zone zone;
    private short x, y;
    private byte direct;
    private List<Menu> menus;
    private DialLucky dialLucky;
    private short idImg = -1;
    private List<Command> listCmd;
    private List<Command> listCmdRotate;

    @Getter
    @Setter
    private boolean loadDataFinish;

    @Getter
    @Setter
    private List<BossShopItem> bossShopItems;
    private List<Part> ShopEvent;


    private List<Byte> boardIDs;
    private byte roomID;
    List<Byte> moneyPutList;
    public boolean isToXong;
    public boolean isHaPhom;

    public User() {
        this.role = -1;
        this.chests = new ArrayList<>();
        this.wearing = new ArrayList<>();

        this.landItems = new ArrayList<>();
        this.Animal = new ArrayList<>();
        this.hatgiong = new ArrayList<>();
        this.NongSan = new ArrayList<>();
        this.NongSanDacBiet = new ArrayList<>();
        this.PhanBon = new ArrayList<>();



        this.listCmd = new ArrayList<>();
        this.listCmdRotate = new ArrayList<>();
        this.isDefeated = false;
        this.isSpam = false;
        this.boardIDs = new ArrayList<>();
        this.moneyPutList = new ArrayList<>();
        this.availableSkills = new ArrayList<>();
        this.useSkill = 0;


        this.wearingMarry = new ArrayList<>();
    }

    public synchronized int getIntSpanboss() {
        return intSpanboss;
    }

    public void incrementIntSpanboss() {
        this.intSpanboss++;
    }

    public void resetIntSpanboss() {
        this.intSpanboss = 0;
    }

    // Phương thức để reset tất cả thông tin của người chơi
    public void resetUser() {
        resetIntSpanboss();
        setspamclickBoss(false);
    }

    public synchronized boolean getspamclickBoss() {
        return spamclickBoss;
    }

    public synchronized void setspamclickBoss(boolean spamclickBoss) {
        this.spamclickBoss = spamclickBoss;
    }

    public synchronized boolean isHaPhom() {
        return this.isHaPhom;
    }

    public synchronized void setHaPhom(boolean isHaPhom) {
        this.isHaPhom = isHaPhom;
    }

    public synchronized boolean isToXong() {
        return this.isToXong;
    }

    public synchronized void setToXong(boolean isToXong) {
        this.isToXong = isToXong;
    }


    public List<Byte> getMoneyPutList() {
        return this.moneyPutList;
    }

    public synchronized void updateMoneyPutList(List<Byte> newMoneyPutList) {
        if (this.moneyPutList == null) {
            this.moneyPutList = new ArrayList<>(); // Khởi tạo danh sách nếu chưa có
        }
        this.moneyPutList.clear(); // Xóa danh sách cũ (nếu cần)
        this.moneyPutList.addAll(newMoneyPutList); // Thêm các phần tử mới
    }

    public synchronized void updateMoneyPutListByIndex(byte indexFrom, byte indexTo) {
        if (this.moneyPutList != null && this.moneyPutList.size() > 0) {
            // Kiểm tra xem indexFrom và indexTo có hợp lệ trong danh sách không
            if (indexFrom >= 0 && indexFrom < this.moneyPutList.size() &&
                    indexTo >= 0 && indexTo < this.moneyPutList.size()) {

                // Lấy giá trị tại vị trí indexFrom
                Byte valueToMove = this.moneyPutList.get(indexFrom);

                // Xóa phần tử tại vị trí indexFrom
                this.moneyPutList.remove(indexFrom);

                // Thêm phần tử vào vị trí indexTo
                this.moneyPutList.add(indexTo, valueToMove);
            }
        }
    }


    public synchronized void setUseSkill(int skill) {
        this.useSkill = skill;
    }

    public List<Integer> getListSkill() {
        return this.availableSkills;
    }

    public synchronized void calculateDameToXu() {
        int totalDamage = 30;
        if (this.getStar() == 2) {
            totalDamage = 80;
        }
        List<Integer> Item1 = Arrays.asList(3440, 3443, 3174, 3972, 4442, 6142, 4121);  // Set mũ
        List<Integer> Item2 = Arrays.asList(3441, 3445, 3176, 3974, 4443, 4122);  // Set áo
        List<Integer> Item3 = Arrays.asList(3442, 3446, 3177, 3975, 4444, 4123);  // Set quần
        int countItem1 = 0, countItem2 = 0, countItem3 = 0;
        boolean cung = false, maybay = false, haoquanhoalong = false, bang = false, hophong = false;
// Kiểm tra toàn bộ items nhân vật đang mặc
        for (Item item : wearing) {
            totalDamage += item.getPart().getLevel();

            // Kiểm tra các item đặc biệt
            cung = cung || item.getId() == 6400;
            maybay = maybay || item.getId() == 4715;
            haoquanhoalong = haoquanhoalong || item.getId() == 5455;
            bang = bang || item.getId() == 6485;
            hophong = hophong || item.getId() == 5828;
            // Kiểm tra nếu item thuộc set siêu anh hùng
            if (Item1.contains(item.getId())) countItem1++;
            if (Item2.contains(item.getId())) countItem2++;
            if (Item3.contains(item.getId())) countItem3++;
        }
// Xử lý các kỹ năng tương ứng
        handleSkillSet(countItem1, countItem2, countItem3);
        handleSkill(cung, 2);  // Kỹ năng cung
        handleSkill(maybay, 4);  // Kỹ năng máy bay
        handleSkill(haoquanhoalong, 5);
        handleSkill(bang, 6);
        handleSkill(hophong, 7);

// Cập nhật damage cuối cùng
        this.dameToXu = totalDamage;
    }

    private void handleSkill(boolean hasItem, int skillId) {
        if (hasItem) {
            addSkill(skillId);  // Thêm kỹ năng
            this.useSkill = skillId;
        } else {
            removeSkill(skillId);  // Xóa kỹ năng nếu không còn item đặc biệt
        }
    }

    private void handleSkillSet(int countItem1, int countItem2, int countItem3) {
        if (countItem1 > 0 && countItem2 > 0 && countItem3 > 0) {
            addSkill(1);  // Thêm skill 1 nếu mặc đủ set trang bị
            this.useSkill = 1;
        } else {
            removeSkill(1);  // Thiếu món nào thì xóa skill 1
        }
    }

    private void addSkill(int skillId) {
        if (!this.availableSkills.contains(skillId)) {
            this.availableSkills.add(skillId);
        }
    }

    private void removeSkill(int skillId) {
        this.availableSkills.remove(Integer.valueOf(skillId));
        this.useSkill = 0;  // Reset skill được sử dụng nếu xóa
    }

    public User(String username, int xuFromBoss) {
        this.username = username;
        this.xu_from_boss = xuFromBoss;
    }

    public User(String username, int xeng, int TopPhaoLuong) {
        this.username = username;
        this.xeng = xeng;
        this.TopPhaoLuong = TopPhaoLuong;
    }

    public User(String username, int id, int xeng, int TopPhaoXu) {
        this.username = username;
        this.id = id;
        this.xeng = xeng;
        this.TopPhaoXu = TopPhaoXu;
    }

    public AvatarService getAvatarService() {
        return session.getAvatarService();
    }

    public FarmService getFarmService() {
        return session.getFarmService();
    }

    public HomeService getHomeService() {
        return session.getHomeService();
    }

    public ParkService getParkService() {
        return session.getParkService();
    }

    public MapService getMapService() {
        if (zone == null) {
            return NoService.getInstance();
        }
        return zone.getService();
    }


    public Service getService() {
        return session.getService();
    }

    public void sortWearing() {
        this.wearing.sort((o1, o2) -> o1.getPart().getZOrder() - o2.getPart().getZOrder());
    }

    public void setGender(byte gender) {
        this.gender = gender;
    }

    public synchronized void updateXu(long xuUp) {
        this.xu += xuUp;
    }

    public synchronized void updateXuKillBoss(int dame) {
        this.storedXuUpdate += dame; // Lưu xu vào biến tạm thời
    }

    public synchronized void applyStoredXuUpdate() {
        //this.updateXu(storedXuUpdate * 5); // Cộng dồn số xu ba lần
        this.Updatexu_from_boss(storedXuUpdate);
        Utils.writeLog(this, "xu : " + storedXuUpdate + " X " + this.getDame() + " dame to xu = >" + this.xu);
        this.storedXuUpdate = 0; // Reset xu đã lưu trữ
    }

    public synchronized void updateCrazy(int crazy) {
        this.crazy += crazy;
    }//1k item câu cá

    public synchronized void updateHappy(int Happy) {
        this.happy += Happy;
    }

    public synchronized void updateHunger(int hunger) {
        this.hunger += (byte) hunger;
    }//100 vp kill bos

    public synchronized void updateXP(int XP) {
        this.expMain += XP;
    }

    public synchronized void Updatexu_from_boss(int xu_from_boss) {
        this.xu_from_boss += xu_from_boss;
    }

    public synchronized void updateLuong(int luongUp) {
        this.luong += luongUp;
        try {
            this.getAvatarService().SendTabmsg("Luong : " + this.luong);
            Utils.writeLog(this, "luong : " + luong);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void updateScores(int ScoresUp) {
        this.scores += ScoresUp;
    }

    public synchronized void updateLuongKhoa(int luongUp) {
        this.luong += luongUp;
    }

    public synchronized void updateXeng(int xengUp) {
        this.xeng += xengUp;
    }

    public synchronized void updateChestSlot(int chestslot) {
        this.chestSlot += (byte) chestslot;
    }

    public synchronized void updateChest_homeSlot(int chestslot) {
        this.chestHomeSlot += (byte) chestslot;
    }

    public synchronized void updateHP(long dame, Boss boss, User us) throws IOException {
        this.HP += dame;
        if (HP <= 0) {
            HP = 0;
            if (!isDefeated) {
                isDefeated = true;
                // Chỉ thực hiện xử lý khi boss chưa bị đánh bại
                boss.handleBossDefeat(boss, us);
            }
        }
    }

    public synchronized void updateSpam(long spams, Boss boss, User us) throws IOException {
        boss.spam += spams;
        System.out.println("Spam " + boss.getSpam());
        if (boss.getSpam() <= 0) {
            boss.spam = 0;
            isSpam = false;
            if (!isSpam) {
                isSpam = true;
                boss.hanlderNhatHopQua(boss, us);
            }
        }
    }

    public boolean isSpam() {
        return isSpam;
    }

    public boolean isDefeated() {
        return isDefeated;
    }

    public synchronized byte getRoomID() {
        return this.roomID;
    }

    public synchronized void setRoomID(byte RoomID) {
        this.roomID = RoomID;
    }

    public long getRandomTimeInMillis() {
        return randomTimeInMillis;
    }

    // Setter cho randomTimeInMillis
    public void setRandomTimeInMillis(long randomTimeInMillis) {
        this.randomTimeInMillis = randomTimeInMillis;
    }

    // Getter cho lastTimeSet
    public long getcorrectAnswer() {
        return this.correctAnswer;
    }

    // Setter cho lastTimeSet
    public void setcorrectAnswer(int sum) {
        this.correctAnswer = sum;
    }

    public void set(long randomTimeInMillis) {
        this.randomTimeInMillis = randomTimeInMillis;
    }

    // Getter cho lastTimeSet
    public long getLastTimeSet() {
        return lastTimeSet;
    }


    public synchronized int getChestLevel() {
        int chestSlotHome = this.getChestHomeSlot(); // Lấy số ô của rương hiện tại

        if (chestSlotHome <= 10) {
            return 1; // Cấp 1 với 10 ô
        } else if (chestSlotHome <= 15) {
            return 2; // Cấp 2 với 15 ô
        } else if (chestSlotHome <= 20) {
            return 3; // Cấp 3 với 20 ô
        } else if (chestSlotHome <= 25) {
            return 4; // Cấp 4 với 25 ô
        } else if (chestSlotHome <= 30) {
            return 5; // Cấp 4 với 25 ô
        } else if (chestSlotHome <= 35) {
            return 6; // Cấp 4 với 25 ô
        } else if (chestSlotHome <= 40) {
            return 7; // Cấp 4 với 25 ô
        } else if (chestSlotHome <= 45) {
            return 8; // Cấp 4 với 25 ô
        } else if (chestSlotHome <= 50) {
            return 9; // Cấp 4 với 25 ô
        } else if (chestSlotHome <= 55) {
            return 10; // Cấp 4 với 25 ô
        }

        return -1; // Trường hợp không hợp lệ
    }

    public synchronized void updateTopPhaoLuong(int luongThaPhao) {
        this.luong += luongThaPhao;
        this.TopPhaoLuong += 1;
        DbManager.getInstance().executeUpdate("UPDATE `players` SET `TopPhaoLuong` = ? WHERE `user_id` = ? LIMIT 1;",
                this.TopPhaoLuong, this.id);
    }

    public synchronized void updateTopPhaoXu(int xuThaPhao) {
        this.xu += xuThaPhao;
        this.TopPhaoXu += 1;
        DbManager.getInstance().executeUpdate("UPDATE `players` SET `TopPhaoXu` = ? WHERE `user_id` = ? LIMIT 1;",
                this.TopPhaoXu, this.id);
    }


    public void sendMessage(Message ms) {
        this.session.sendMessage(ms);
    }

    protected void saveData() {
        DbManager.getInstance().executeUpdate("UPDATE `players` SET `gender` = ?, `friendly` = ?, `crazy` = ?, `stylish` = ?, `happy` = ?, `hunger` = ?, `chest_slot` = ? , `chest_home_slot` = ? WHERE `user_id` = ? LIMIT 1;",
                this.gender, this.friendly, this.crazy, this.stylish, this.happy, this.hunger, this.chestSlot, this.chestHomeSlot, this.id);
        DbManager.getInstance().executeUpdate("UPDATE `players` SET `xu` = ?, `luong` = ?, `luong_khoa` = ?, `xeng` = ?, `level_main` = ?, `exp_main` = ?,`scores` = ? , `xu_from_boss` = ? , `TopPhaoLuong` = ?, `TopPhaoXu` = ? WHERE `user_id` = ? LIMIT 1;",
                this.xu, this.luong, this.luongKhoa, this.xeng, this.leverMain, this.expMain, this.scores, this.xu_from_boss, this.TopPhaoLuong, this.TopPhaoXu, this.id);
        JSONArray jChests = new JSONArray();
        for (Item item : this.chests) {
            JSONObject obj = new JSONObject();
            obj.put("id", item.getId());
            obj.put("expired", item.getExpired());
            obj.put("quantity", item.getQuantity());
            checkItemQuantityLog(item, "saveData error" + item.getPart().getName()

            );
            jChests.add(obj);
        }
        JSONArray jWearing = new JSONArray();
        for (Item item : this.wearing) {
            JSONObject obj = new JSONObject();
            obj.put("id", item.getId());
            obj.put("expired", item.getExpired());
            obj.put("quantity", item.getQuantity());
            jWearing.add(obj);
        }

        JSONArray jChestsHome = new JSONArray();
        for (Item item : this.chestsHome) {
            JSONObject obj = new JSONObject();
            obj.put("id", item.getId());
            obj.put("expired", item.getExpired());
            obj.put("quantity", item.getQuantity());
            jChestsHome.add(obj);
        }

        DbManager.getInstance().executeUpdate("UPDATE `players` SET `chests` = ?, `wearing` = ?, `chests_home` = ? WHERE `user_id` = ? LIMIT 1;",
                jChests.toJSONString(), jWearing.toJSONString(), jChestsHome.toJSONString(), this.id);
        System.out.println("Save data user " + this.getUsername());

        try {
            saveFarmData(this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveFarmData(int userId) throws SQLException {
        // Chuẩn bị dữ liệu để lưu vào cơ sở dữ liệu
        JSONArray landData = new JSONArray();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        for (LandItem landItem : this.session.user.landItems) {
            JSONObject landObject = new JSONObject();
            landObject.put("growthTime", landItem.getGrowthTime());
            landObject.put("type", landItem.getType());//lao
            landObject.put("suckhoe", landItem.getSucKhoe());//skhoe
            landObject.put("resourceCount", landItem.getResourceCount());
            landObject.put("isWatered", landItem.isWatered());
            landObject.put("isFertilized", landItem.isFertilized());
            landObject.put("isHarvestable", landItem.isHarvestable());

            LocalDateTime plantedTime = landItem.getPlantedTime();
            if (plantedTime != null) {
                landObject.put("plantedTime", plantedTime.format(formatter));
            } else {
                landObject.put("plantedTime", "not_planted"); // Hoặc loại bỏ dòng này
            }

            landData.add(landObject);
        }


        JSONArray animalData = new JSONArray();
        for (Animal animal : this.session.user.Animal) {
            JSONObject animalObject = new JSONObject();
            animalObject.put("id", animal.getId());
            animalObject.put("health", animal.getHealth());
            animalObject.put("level", animal.getLevel());
            animalObject.put("resourceCount", animal.getResourceCount());
            animalObject.put("nextProductionTime", animal.getNextProductionTime());
            animalObject.put("isAlive", animal.isAlive());
            animalObject.put("isReadyForBreeding", animal.isReadyForBreeding());
            animalObject.put("isHarvestable", animal.isHarvestable());
            animalData.add(animalObject);
        }

        JSONArray hatgiongData = new JSONArray();
        for (HatGiong hatGiong : this.session.user.hatgiong) {
            JSONObject hatGiongObject = new JSONObject();
            hatGiongObject.put("id", hatGiong.getId());
            hatGiongObject.put("soluong", hatGiong.getSoluong());
            hatgiongData.add(hatGiongObject);
        }


        JSONArray phanbonData = new JSONArray();
        for (PhanBon phanBon : this.session.user.PhanBon) {
            JSONObject phanBonObject = new JSONObject();
            phanBonObject.put("id", phanBon.getId());
            phanBonObject.put("soluong", phanBon.getSoluong());
            phanbonData.add(phanbonData);
        }

        JSONArray nongsanData = new JSONArray();
        for (NongSan nongSan : this.session.user.NongSan) {
            JSONObject nongSanObject = new JSONObject();
            nongSanObject.put("id", nongSan.getId());
            nongSanObject.put("soluong", nongSan.getSoluong());
            nongsanData.add(nongsanData);
        }

        JSONArray nongsandacbietData = new JSONArray();
        for (NongSanDacBiet nongsandacbiet : this.session.user.NongSanDacBiet) {
            JSONObject nongsandacbietObject = new JSONObject();
            nongsandacbietObject.put("id", nongsandacbiet.getId());
            nongsandacbietObject.put("soluong", nongsandacbiet.getSoluong());
            phanbonData.add(phanbonData);
        }


        // Cập nhật cơ sở dữ liệu với dữ liệu đã tạo
        String query = "INSERT INTO `farm_data` (user_id, land_data, animal_data,hatgiong,phanbon,nongsan,nongsandacbiet) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE land_data = ?, animal_data = ?, hatgiong = ?, phanbon = ?, nongsan = ?, nongsandacbiet = ?";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            // Chuyển đổi dữ liệu thành chuỗi JSON
            String landDataString = landData.toString();
            String animalDataString = animalData.toString();
            String hatgiongDataString = hatgiongData.toString();
            String phanbonDataString = phanbonData.toString();
            String nongsanDataString = nongsanData.toString();
            String nongsandacbietDataString = nongsandacbietData.toString();

            // Cập nhật hoặc thêm mới dữ liệu vào bảng `farm_data`
            ps.setInt(1, userId);
            ps.setString(2, landDataString);
            ps.setString(3, animalDataString);
            ps.setString(4, hatgiongDataString);
            ps.setString(5, phanbonDataString);
            ps.setString(6, nongsanDataString);
            ps.setString(7, nongsandacbietDataString);
            ps.setString(8, landDataString);
            ps.setString(9, animalDataString);
            ps.setString(10, hatgiongDataString);
            ps.setString(11, phanbonDataString);
            ps.setString(12, nongsanDataString);
            ps.setString(13, nongsandacbietDataString);
            ps.executeUpdate();
        }
    }

    public void loadFarmData(int userId) throws SQLException {

        String query = "SELECT land_data, animal_data,hatgiong,phanbon,nongsan,nongsandacbiet FROM `farm_data` WHERE user_id = ?";

        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, userId);

            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    String landDataString = res.getString("land_data");
                    String animalDataString = res.getString("animal_data");
                    String hatgiongDataString = res.getString("hatgiong");
                    String phanbonDataString = res.getString("phanbon");
                    String nongsanDataString = res.getString("nongsan");
                    String nongsandacbietDataString = res.getString("nongsandacbiet");

                    // Phân tích dữ liệu ô đất (land_data)
                    JSONArray landData = (JSONArray) JSONValue.parse(landDataString);
                    List<LandItem> landItems = new ArrayList<>();

                    DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

                    for (Object land : landData) {
                        JSONObject obj = (JSONObject) land;
                        int growthTime = ((Long) obj.get("growthTime")).intValue();
                        int type = ((Long) obj.get("type")).intValue();
                        int suckhoe = ((Long) obj.get("suckhoe")).intValue();
                        int resourceCount = ((Long) obj.get("resourceCount")).intValue();
                        boolean isWatered = (Boolean) obj.get("isWatered");
                        boolean isFertilized = (Boolean) obj.get("isFertilized");
                        boolean isHarvestable = (Boolean) obj.get("isHarvestable");

                        String plantedTimeStr = (String) obj.get("plantedTime");
                        LocalDateTime plantedTime = LocalDateTime.parse(plantedTimeStr, formatter);

                        LandItem landItem = new LandItem(growthTime, type,suckhoe, resourceCount, isWatered, isFertilized, isHarvestable, plantedTime);
                        landItems.add(landItem);
                    }
                    // Cập nhật danh sách ô đất cho người chơi
                    this.session.user.landItems = landItems;

                    // Phân tích dữ liệu vật nuôi (animal_data)
                    JSONArray animalData = (JSONArray) JSONValue.parse(animalDataString);
                    List<Animal> animals = new ArrayList<>();

                    for (Object animal : animalData) {
                        JSONObject obj = (JSONObject) animal;
                        int id = ((Long) obj.get("id")).intValue();
                        int health = ((Long) obj.get("health")).intValue();
                        int level = ((Long) obj.get("level")).intValue();
                        int resourceCount = ((Long) obj.get("resourceCount")).intValue();
                        int nextProductionTime = ((Long) obj.get("nextProductionTime")).intValue();
                        boolean isAlive = (Boolean) obj.get("isAlive");
                        boolean isReadyForBreeding = (Boolean) obj.get("isReadyForBreeding");
                        boolean isHarvestable = (Boolean) obj.get("isHarvestable");

                        Animal animalObj = new Animal(id, health, level, resourceCount, nextProductionTime, isAlive, isReadyForBreeding, isHarvestable);
                        animals.add(animalObj);
                    }
                    // Cập nhật danh sách vật nuôi cho người chơi
                    this.session.user.Animal = animals;



                    JSONArray hatgiongdata = (JSONArray) JSONValue.parse(hatgiongDataString);
                    List<HatGiong> hatgiongs = new ArrayList<>();

                    for (Object hatgiong : hatgiongdata) {
                        JSONObject obj = (JSONObject) hatgiong;
                        int id = ((Long) obj.get("id")).intValue();
                        int soluong = ((Long) obj.get("soluong")).intValue();

                        HatGiong animalObj = new HatGiong(id, soluong);
                        hatgiongs.add(animalObj);
                    }
                    this.session.user.hatgiong = hatgiongs;


                    JSONArray phanbondata = (JSONArray) JSONValue.parse(phanbonDataString);
                    List<PhanBon> phanBons = new ArrayList<>();

                    for (Object phanbon : phanbondata) {
                        JSONObject obj = (JSONObject) phanbon;
                        int id = ((Long) obj.get("id")).intValue();
                        int soluong = ((Long) obj.get("soluong")).intValue();

                        PhanBon pb = new PhanBon(id, soluong);
                        phanBons.add(pb);
                    }
                    this.session.user.PhanBon = phanBons;



                    JSONArray nongsandata = (JSONArray) JSONValue.parse(nongsanDataString);
                    List<NongSan> nongSans = new ArrayList<>();

                    for (Object nongsan : nongsandata) {
                        JSONObject obj = (JSONObject) nongsan;
                        int id = ((Long) obj.get("id")).intValue();
                        int soluong = ((Long) obj.get("soluong")).intValue();

                        NongSan ns = new NongSan(id, soluong);
                        nongSans.add(ns);
                    }
                    this.session.user.NongSan = nongSans;


                    JSONArray nongsandbdata = (JSONArray) JSONValue.parse(nongsandacbietDataString);
                    List<NongSanDacBiet> nongSandbs = new ArrayList<>();

                    for (Object nongsandb : nongsandbdata) {
                        JSONObject obj = (JSONObject) nongsandb;
                        int id = ((Long) obj.get("id")).intValue();
                        int soluong = ((Long) obj.get("soluong")).intValue();

                        NongSanDacBiet nsdb = new NongSanDacBiet(id, soluong);
                        NongSanDacBiet.add(nsdb);
                    }
                    this.session.user.NongSanDacBiet = nongSandbs;


                }
            }
        }

        // Nếu không có dữ liệu, tạo mặc định cho người chơi
        if (this.session.user.landItems.isEmpty()) {
            // Tạo mặc định cho 6 ô đất
            List<LandItem> defaultLandItems = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                defaultLandItems.add(new LandItem(0, -1,-1, 0, false, false, false, LocalDateTime.now())); // Cây mặc định
            }
            this.session.user.landItems = defaultLandItems;
        }

        if (this.session.user.Animal.isEmpty()) {
            // Không có vật nuôi, nên không cần thêm gì
            this.session.user.Animal = new ArrayList<>();
        }

        if (this.session.user.hatgiong.isEmpty()) {
            // Không có vật nuôi, nên không cần thêm gì
            this.session.user.hatgiong = new ArrayList<>();
        }
        if (this.session.user.PhanBon.isEmpty()) {
            // Không có vật nuôi, nên không cần thêm gì
            this.session.user.PhanBon = new ArrayList<>();
        }
        if (this.session.user.NongSan.isEmpty()) {
            // Không có vật nuôi, nên không cần thêm gì
            this.session.user.NongSan = new ArrayList<>();
        }
        if (this.session.user.NongSanDacBiet.isEmpty()) {
            // Không có vật nuôi, nên không cần thêm gì
            this.session.user.NongSanDacBiet = new ArrayList<>();
        }

    }

    public synchronized boolean login() {
        if (!ServerManager.active) {
            getService().serverMessage("Máy chủ đang bảo trì. Vui lòng quay lại sau : v");
            return false;
        }

        String ACCOUNT_LOGIN = "SELECT * FROM `users` WHERE `username` = ? AND `password` = ? LIMIT 1 FOR UPDATE;";
        String SET_LOCK_ACCOUNT = "UPDATE `users` SET `login_lock` = 1 WHERE `id` = ?;";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(ACCOUNT_LOGIN)) {

            ps.setString(1, this.username);
            ps.setString(2, Utils.md5(password));
            connection.setAutoCommit(false);  // Bắt đầu transaction

            try (ResultSet red = ps.executeQuery()) {
                if (red.next()) {
                    this.id = red.getInt("id");
                    this.role = (byte) red.getInt("role");
                    boolean active = red.getBoolean("active");
                    if (!active) {
                        getService().serverMessage(GameString.userLoginActive());
                        connection.rollback();
                        return false;
                    }

                    // Kiểm tra khóa đăng nhập
                    if (red.getInt("login_lock") == 1) {
                        getService().serverMessage(GameString.userLoginMany());
                        connection.rollback();  // Rollback nếu phát hiện người dùng đang đăng nhập
                        User us = UserManager.getInstance().find(this.id);
                        if (us != null) {
                            // Ngắt kết nối người dùng cũ
                            us.getService().serverMessage(GameString.userLoginMany()); // Thông báo người dùng cũ bị ngắt kết nối
                            us.session.close(); // Đóng kết nối người dùng cũ
                            UserManager.getInstance().remove(us); // Xóa người dùng cũ khỏi quản lý
                        }
                        String UNLOCK_ACCOUNT_SQL = "UPDATE users SET login_lock = 0 WHERE id = ?";
                        try (Connection connection1 = DbManager.getInstance().getConnection();
                             PreparedStatement ps1 = connection1.prepareStatement(UNLOCK_ACCOUNT_SQL)) {
                            ps1.setInt(1, this.id);
                            ps1.executeUpdate();
                            System.out.println("Account unlocked successfully.");
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                        return false;
                    }

                    // Đặt khóa đăng nhập
                    try (PreparedStatement setLockStmt = connection.prepareStatement(SET_LOCK_ACCOUNT)) {
                        setLockStmt.setInt(1, this.id);
                        setLockStmt.executeUpdate();
                    }

                    // Kiểm tra nếu tài khoản bị cấm
                    JSONObject banData = (JSONObject) ((red.getString("ban") != null)
                            ? JSONValue.parse(red.getString("ban"))
                            : new JSONObject());
                    if (!banData.isEmpty()) {
                        int banType = ((Long) banData.get("type")).intValue();
                        if (banType == 2) {
                            if (banData.get("forever") != null) {
                                getService().serverMessage(GameString.userLoginLockForever());
                                connection.rollback();
                                return false;
                            }
                            int minutes = ((Long) banData.get("minutes")).intValue();
                            Date timeNowwww = new Date();
                            Date banStart = Utils.getDate((String) banData.get("start"));
                            Date banEnd = new Date(banStart.getTime() + 60000 * minutes);
                            if (banEnd.after(timeNowwww)) {
                                minutes = (int) ((banEnd.getTime() - timeNowwww.getTime()) / 60000L);
                                getService().serverMessage(GameString.userLoginLock(minutes));
                                connection.rollback();
                                return false;
                            }
                        }
                    }

                    // Kiểm tra nếu người dùng đã đăng nhập từ thiết bị khác
                    User us = UserManager.getInstance().find(this.id);
                    if (us != null) {
                        getService().serverMessage(GameString.userLoginMany());
                        us.getService().serverMessage(GameString.userLoginMany());
                        Utils.setTimeout(() -> {
                            us.session.close();
                            UserManager.getInstance().remove(this);
                        }, Utils.nextInt(1500));
                        connection.rollback();
                        return false;
                    }

                    // Mọi thứ OK, commit và giữ khóa đăng nhập
                    connection.commit();
                    return true;
                } else {
                    getService().serverMessage(GameString.loginPassFail());
                }
            }
        } catch (SQLException ex) {
            getService().serverMessage(ex.getMessage());
        }
        return false;
    }


    public void GetdataUserHenho() {
        String GET_PLAYER_DATA = "SELECT wearing FROM `players` WHERE `user_id` = ? LIMIT 1;";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(GET_PLAYER_DATA);) {
            ps.setInt(1, this.idUsHenHo);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    this.wearingMarry = new ArrayList<>();
                    JSONArray wearing = (JSONArray) JSONValue.parse(res.getString("wearing"));
                    for (Object o : wearing) {
                        JSONObject obj = (JSONObject) o;
                        int id = ((Long) obj.get("id")).intValue();
                        long expired = ((Long) obj.get("expired"));
                        int quantity = 1;
                        if (obj.containsKey("quantity")) {
                            quantity = ((Long) obj.get("quantity")).intValue();
                        }
                        Item item = Item.builder().id(id)
                                .quantity(quantity)
                                .expired(expired)
                                .build();
                        if (item.reliability() > 0) {
                            this.wearingMarry.add(item);
                        }
                    }
                }
            } catch (SQLException ex) {
            }

        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean loadData() {
        String GET_PLAYER_DATA = "SELECT * FROM `players` WHERE `user_id` = ? LIMIT 1;";
        try (Connection connection = DbManager.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(GET_PLAYER_DATA);) {
            ps.setInt(1, this.id);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    if (res.getInt("user_id") == 7) {
                        //this.id+=(Npc.ID_ADD+1000);
                    }
                    this.leverMain = res.getInt("level_main");
                    this.expMain = res.getInt("exp_main");
                    this.gender = res.getByte("gender");
                    this.chestSlot = res.getByte("chest_slot");
                    this.chestHomeSlot = res.getByte("chest_home_slot");
                    this.xu = res.getLong("xu");
                    Utils.writeLog(this,"xu load :" + this.xu);
                    this.luong = res.getInt("luong");
                    Utils.writeLog(this,"luong load :" + this.luong);
                    this.luongKhoa = res.getInt("luong_khoa");
                    this.xeng = res.getInt("xeng");
                    this.clanID = res.getShort("clan_id");
                    this.friendly = res.getByte("friendly");
                    this.crazy = res.getShort("crazy");//vp sk/
                    this.stylish = res.getByte("stylish");
                    this.happy = res.getByte("happy");
                    this.hunger = res.getByte("hunger");//quà
                    this.star = res.getByte("star");
                    this.scores = res.getInt("scores");
                    this.xu_from_boss = res.getInt("xu_from_boss");
                    this.TopPhaoLuong = res.getInt("TopPhaoLuong");
                    this.TopPhaoXu = res.getInt("TopPhaoXu");
                    this.chests = new ArrayList<>();
                    JSONArray chests = (JSONArray) JSONValue.parse(res.getString("chests"));
                    for (Object chest : chests) {
                        JSONObject obj = (JSONObject) chest;
                        int id = ((Long) obj.get("id")).intValue();
                        long expired = ((Long) obj.get("expired"));
                        int quantity = 1;
                        if (obj.containsKey("quantity")) {
                            quantity = ((Long) obj.get("quantity")).intValue();
                            if(quantity>100||quantity<0){
                                Utils.writeLog(this,"loadData quantity " + quantity);
                            }
                            if(quantity>15000||quantity<0) {
                                Utils.writeLog(this, "loadData quantity và khoa acc" + quantity);
                                Utils.writeLogKhoaAcc(this, "loadData quantity và khoa acc" + quantity);
//                                String sql1 = "UPDATE users SET active = ? WHERE id = ?";
//                                try (Connection connection1 = DbManager.getInstance().getConnection();
//                                     PreparedStatement ps1 = connection1.prepareStatement(sql1)) {
//
//                                    ps1.setInt(1, 0);  // Khóa tài khoản
//                                    ps1.setInt(2, this.id);
//                                    int rowsUpdated = ps1.executeUpdate();
//                                    if (rowsUpdated == 0) {
//                                        Utils.writeLog(this, "Không có bản ghi nào được cập nhật với ID " + this.id);
//                                    }
//                                    this.session.close();
//                                }
                            }
                            //có gì khóa acc
                        }
                        Item item = Item.builder().id(id)
                                .quantity(quantity)
                                .expired(expired)
                                .build();
                        if (item.reliability() > 0) {
                            this.chests.add(item);
                        }
                    }
                    this.wearing = new ArrayList<>();
                    JSONArray wearing = (JSONArray) JSONValue.parse(res.getString("wearing"));
                    for (Object o : wearing) {
                        JSONObject obj = (JSONObject) o;
                        int id = ((Long) obj.get("id")).intValue();
                        long expired = ((Long) obj.get("expired"));
                        int quantity = 1;
                        if (obj.containsKey("quantity")) {
                            quantity = ((Long) obj.get("quantity")).intValue();
                        }
                        Item item = Item.builder().id(id)
                                .quantity(quantity)
                                .expired(expired)
                                .build();
                        if (item.reliability() > 0) {
                            this.wearing.add(item);
                        }
                    }
                    this.chestsHome = new ArrayList<>();
                    JSONArray chestshome = (JSONArray) JSONValue.parse(res.getString("chests_home"));
                    for (Object chest : chestshome) {
                        JSONObject obj = (JSONObject) chest;
                        int id = ((Long) obj.get("id")).intValue();
                        long expired = ((Long) obj.get("expired"));
                        int quantity = 1;
                        if (obj.containsKey("quantity")) {
                            quantity = ((Long) obj.get("quantity")).intValue();
                        }
                        Item item = Item.builder().id(id)
                                .quantity(quantity)
                                .expired(expired)
                                .build();
                        if (item.reliability() > 0) {
                            this.chestsHome.add(item);
                        }
                    }
                    loadFarmData(this.id);
                    calculateDameToXu();



                    String checkExistQuery = "SELECT * FROM marry WHERE idNam = ? OR idNu = ?";
                    int userId = this.id; // ID của người dùng hiện tại

                    try (Connection conn = DbManager.getInstance().getConnection();
                         PreparedStatement psCheck = conn.prepareStatement(checkExistQuery)) {

                        // Cung cấp ID của người dùng để kiểm tra
                        psCheck.setInt(1, userId);
                        psCheck.setInt(2, userId);

                        try (ResultSet rs = psCheck.executeQuery()) {
                            if (rs.next()) {
                                // Nếu có kết quả, chúng ta lấy idNam và idNu
                                int idNam = rs.getInt("idNam");
                                int idNu = rs.getInt("idNu");
                                this.levelMarry = rs.getInt("level");
                                this.PerLevelMarry = rs.getInt("perLevel");
                                // Kiểm tra xem ID người dùng hiện tại là idNam hay idNu, và lấy ID còn lại
                                int otherId = (idNam == userId) ? idNu : idNam;

                                // Lấy thông tin của người còn lại
                                String userInfoQuery = "SELECT * FROM users WHERE id = ?";
                                try (PreparedStatement psUser = conn.prepareStatement(userInfoQuery)) {
                                    psUser.setInt(1, otherId);

                                    try (ResultSet rsUser = psUser.executeQuery()) {
                                        if (rsUser.next()) {
                                            // Lấy thông tin của người còn lại từ bảng users
                                            String username = rsUser.getString("username");
                                            int userID = rsUser.getInt("id");
                                            this.setIdUsHenHo(userID);
                                            this.setNamehh(username);
                                            GetdataUserHenho();
                                            System.out.println("Người còn lại: " + username+ " , id = " + userID);
                                        }
                                    }
                                }
                            } else {
                                System.out.println("Không tìm thấy người dùng hẹn hò hoặc kết hôn với bạn.");
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }



                    setLoadDataFinish(true);
                    return true;
                }
            }



        } catch (Exception ex) {
            ex.printStackTrace();
            getService().serverMessage(ex.getMessage());
        }
        return false;
    }

    public void initAvatar() {
        sortWearing();
        listCmd.add(new Command("Chức năng", 2));
        listCmdRotate.add(new Command((short) 0, "Hội nhóm", 41, (byte) 1));
        listCmdRotate.add(new Command((short) 4, "Oan Tu Xi", 44, (byte) 1));
        //listCmdRotate.add(new Command((short) 33, "Hô phong hoán vũ", 1053, (byte) 0));
        //listCmdRotate.add(new Command((short) 34, "Triệu hồi bia mộ", 1053, (byte) 0));
        //listCmdRotate.add(new Command((short) 35, "Cánh thần hiển linh", 1055, (byte) 0));
        //listCmdRotate.add(new Command((short) 48, "pháo sinh nhật(5 lượng)", 1115, (byte) 0));
        //listCmdRotate.add(new Command((short) 47, "Pháo hạnh phúc (5 lượng)", 242, (byte) 0));


        //listCmdRotate.add(new Command((short) 8, "Pháo thịnh vượng (5 lượng)", 241, (byte) 0));//sk


        //listCmdRotate.add(new Command((short) 9, "triệu hồi con chim k nhớ tên", 1082, (byte) 0));
        //listCmdRotate.add(new Command((short) 10, "Rương chỉ sử dụng không được bỏ(sẽ bị xóa item ở rương gốc)", 1204, (byte) 0));
        //listCmdRotate.add(new Command((short) 11, "thả bóng bay (20k xu)", 577, (byte) 0)); // sk


        listCmdRotate.add(new Command((short) 23, "Đổi Skill", 355, (byte) 0));
        listCmdRotate.add(new Command((short) 36, "Hẹn hò", 1096, (byte) 1));
    }

    public void doAction(Message ms) {
        try {
            int idTo = ms.reader().readInt();
            short action = ms.reader().readShort();
            User us = UserManager.getInstance().find(idTo);

            if(this.getZone().getMap().getId() == 16){
                this.getAvatarService().serverDialog("Bạn không thể hành động ở đây !");
                return;
            }
            switch (action) {
                case 101:
                    DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
                    int dayIndex = dayOfWeek.getValue(); // 1 = Monday, 7 = Sunday
                    if (dayIndex == 5 || dayIndex == 6) {
                        getMapService().doAction(id, idTo, action);
                        break;
                    }
                    if(gender== us.gender) {
                        this.getAvatarService().serverDialog("làm gì vậy bro, đồng giới thì thứ 6 thứ 7");
                        break;
                    }
                default:
                    getMapService().doAction(id, idTo, action);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getExpMax() {
        return (this.leverMain * (this.leverMain + 1) / 2) * 1000;
    }

    public byte getLeverMainPercen() {
        return (byte) (this.expMain * 100 / getExpMax());
    }

    public void viewChest(Message ms) throws IOException {
        int type = ms.reader().readInt();
        if(type!=id)
        {
            List<Item> _chests = chests.stream().filter(item -> {
                return item.getPart().getZOrder() == 30 || item.getPart().getZOrder() == 40;
            }).collect(Collectors.toList());
            getAvatarService().viewChest(_chests);
            return;
        }
        List<Item> _chests = chests.stream().filter(item -> {
            return item.getPart().getZOrder() != 30 && item.getPart().getZOrder() != 40;
        }).collect(Collectors.toList());
        getAvatarService().viewChest(_chests);
    }

    // hỏi nâng cấp
    public String getUpgradeRequirements() {
        if (chestSlot/5 >= UPGRADE_COST_COINS.length - 1) {
            return "Rương đã đạt cấp tối đa";
        }

        int nextLevel = (chestSlot/5)+1;
        int coinCost = UPGRADE_COST_COINS[nextLevel];
        int goldCost = UPGRADE_COST_GOLD[nextLevel];

        return String.format(
                "Để nâng cấp lên rương cấp %d bạn cần %d xu và %d lượng hoặc thẻ nâng cấp rương.",
                nextLevel-2, coinCost, goldCost);
    }
    // nâng cấp rương
    public String upgradeChest() {
        if (chestSlot/5 >= UPGRADE_COST_COINS.length - 1) {
            return "Rương đã đạt cấp tối đa";
        }

        int nextLevel = (chestSlot/5)+1;
        int coinCost = UPGRADE_COST_COINS[nextLevel];
        int goldCost = UPGRADE_COST_GOLD[nextLevel];

        Item theNangCap = findItemInChests(3861);
        if(theNangCap != null) {
            this.removeItem(3861,1);
            this.updateChestSlot(+5);
            return String.format(
                    "chúc mừng bạn đã nâng cấp thành công rương cấp %d và có %d ô rương.",
                    nextLevel-2, this.getChestSlot()
            );
        }

        if (xu >= coinCost && luong >= goldCost) {
            this.updateXu(-coinCost);
            this.getAvatarService().updateMoney(0);
            this.updateLuong(-goldCost);
            this.updateChestSlot(+5);
            this.getAvatarService().updateMoney(0);
            return String.format(
                    "chúc mừng bạn đã nâng cấp thành công rương cấp %d và có %d ô rương.",
                    nextLevel-2, this.getChestSlot()
            );
        }


        return "không đủ xu hoặc lượng";
    }
    public void requestYourInfo(Message ms) {
        try {
            int userId = ms.reader().readInt();
            User us = UserManager.getInstance().find(userId);
            if (us != null) {
                this.getAvatarService().requestYourInfo(us);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doAvatarFeel(Message ms) {
        try {
            if (ms.reader().available() <= 0) {
                return;
            }
            byte idFeel = ms.reader().readByte();
            System.out.println("doAvatarFeel msg 57 = " + idFeel + " ");
            this.getMapService().doAvatarFeel(id, idFeel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        if (zone != null) {
            zone.leave(this);
        }
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        DbManager.getInstance().executeUpdate("UPDATE `players` SET `is_online` = ?, `client_id` = ?, `last_online` = ? WHERE `user_id` = ? LIMIT 1;", 0, session.id, timestamp, this.id);
        if (isLoadDataFinish()) {
            saveData();
        }
    }

    @Override
    public String toString() {
        return "User " + this.username;
    }

    public void move(Message ms) {
        try {
            if (ms.reader().available() < 5) {
                return;
            }
            short x = ms.reader().readShort();
            short y = ms.reader().readShort();
            byte direct = ms.reader().readByte();
            if (ms.reader().available() >= 2) {
                ms.reader().readShort();
            }
            this.x = x;
            this.y = y;
            this.direct = direct;
            getMapService().move(this);
            System.out.println("move " + x + ", y = " + y);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addExp(int exp) {
        this.expMain += exp;
        int expMax = getExpMax();
        if (this.expMain >= expMax) {
            this.leverMain++;
            this.expMain -= expMax;
        }
    }

    public boolean checkFullSlotChest(){
        System.out.println("chestSlot: " + this.chestSlot);
        System.out.println("chests.size(): " + this.chests.size());
        if(this.getChestSlot() <= this.getChests().size())
        {
            getAvatarService().serverDialog("Rương đồ đã đầy");
            return true;
        }
        return false;
    }

    public void addItemToChests(Item item) {
        synchronized (chests) {
            if(this.chestSlot <= chests.size()){
                this.getAvatarService().serverDialog("Rương đồ đã đầy");
                return;
            }
            checkItemQuantityLog(item, "addItemToChests error");

            Item itm = findItemInChests(item.getId());

            if (itm != null) {
                // Nếu item đã tồn tại và loại item cho phép (type == -2), tăng số lượng
                if (itm.getPart().getType() == -2) {
                    itm.increase(this, item.getQuantity(), item.getId()); // Giả định phương thức increase đã xử lý đúng
                } else {
                    // Cập nhật độ tin cậy của item (reliability)
                    setReliabilityForItem(itm, item);
                    this.chests.add(item);
                }
            } else {
                // Nếu không tồn tại trong chests, tìm trong wearing
               // itm = findItemInWearing(item.getId());

                if (itm != null) {
                    // Cập nhật độ tin cậy của item trong wearing nếu tìm thấy
                    setReliabilityForItem(itm, item);
                } else {
                    // Nếu không tồn tại cả trong chests và wearing, thêm item vào chests
                    this.chests.add(item);
                    Utils.writeLogAddChest(this,"add item to chests "+ item.getPart().getName());
                }
            }
        }
    }

    public void checkItemQuantityLog(Item item, String message) {
        if (item == null) {
            Utils.writeLog(this, "Lỗi: item là null trong " + message);
            return; // Dừng nếu item là null
        }

        if (item.getQuantity() >= 2 || item.getQuantity() < -1) {
            Utils.writeLog(this, message + " " + item.getQuantity() + " Item " + (item.getPart() != null ? item.getPart().getName() : "Unknown"));
        }
    }

    public void addItemToChestsHome(Item item) {
        synchronized (chestsHome) {
            if(this.chestHomeSlot <= chestsHome.size()){
                this.getAvatarService().serverDialog("Rương nhà đã đầy");
                return;
            }
            checkItemQuantityLog(item,"addItemToChestsHome error");
            Item itm = findItemInChests(item.getId());
            if (itm != null) {
                if (itm.getPart().getType() == -2) {
                    itm.increase(this,item.getQuantity(), item.getId());
                } else {
                    setReliabilityForItem(itm, item);
                }
                this.chestsHome.add(item);
                Utils.writeLogAddChest(this,"add item to chests 1 "+ item.getPart().getName());

                return;
            } else {
                itm = findItemInChests(item.getId());
                if (itm != null) {
                    setReliabilityForItem(itm, item);
                    return;
                }
            }
            this.chestsHome.add(item);
            Utils.writeLogAddChest(this,"add item to chests 2 "+ item.getPart().getName());
        }

    }


    public void setReliabilityForItem(Item old, Item newI) {
        // item expired == -1;
        if (!old.isForever()) {
            if (newI.isForever() || newI.reliability() > old.reliability()) {
                old.setExpired(newI.getExpired());
            }
        }
    }

    public void removeItemFromChests(Item item) {
        synchronized (chests) {
            this.chests.remove(item);
            this.checkItemQuantityLog(item,"removeItemFromChest bug");
        }
    }

    public void removeItemFromChestsHome(Item item) {
        synchronized (chestsHome) {
            this.chestsHome.remove(item);
        }
    }


    public void addItemToWearing(Item item) {
        synchronized (wearing) {
            checkItemQuantityLog(item,"addItemToWearing error");
            Item itm = findItemInWearing(item.getId());
            if (itm == null) {
                this.wearing.add(item);
            } else {
                itm.increase(this,item.getQuantity(), item.getId());
            }
            calculateDameToXu();
        }
    }



    public void removeItemFromWearing(Item item) {
        synchronized (wearing) {
            this.checkItemQuantityLog(item,"removeItemFromWearning bug");
            this.wearing.remove(item);
            calculateDameToXu();
        }
    }

    public Item findItemInChests(int id) {
        synchronized (chests) {
            for (Item item : chests) {
                if (item.getId() == id) {
                    this.checkItemQuantityLog(item,"FindItemInChests bug");
                    return item;
                }
            }
            return null;
        }
    }

    public Item findItemInWearing(int id) {
        synchronized (wearing) {
            for (Item item : wearing) {
                if (item.getId() == id) {
                    this.checkItemQuantityLog(item,"findItemInWearning bug");
                    return item;
                }
            }
            return null;
        }
    }

    public Item findItemInChestsHome(int id) {
        synchronized (chestsHome) {
            for (Item item : chestsHome) {
                if (item.getId() == id) {
                    return item;
                }
            }
            return null;
        }
    }

    public Item findItemWearingByZOrder(int zOrder) {
        synchronized (wearing) {
            for (Item item : wearing) {
                if (item.getPart().getZOrder() == zOrder) {
                    this.checkItemQuantityLog(item,"findItemWearningByZorder bug");
                    return item;
                }
            }
            return null;
        }
    }

    public boolean removeItem(int id, int quantity) {
        Item item = findItemInChests(id);
        checkItemQuantityLog(item,"removeItem error");
        if (item != null) {
            int q = item.reduce(quantity);
            if (q <= 0) {
                removeItemFromChests(item);
            }
            return true;
        }
        return false;
    }


    public HatGiong findhatgiong(int id) {
        synchronized (hatgiong) {
            for (HatGiong hd : hatgiong) {
                if (hd.getId() == id) {
                    return hd;
                }
            }
            return null;
        }
    }









    public void usingItem(short itemID, byte type) {
        try {
            logger.debug("itemID: " + itemID + " type: " + type);
            if (type == 1) {
                logger.debug("Find Item");
                Item item = findItemInChests(itemID);
                logger.debug("Find Done");
                if (item == null) {
                    getService().serverDialog("Không tìm thấy vật phẩm");
                    return;
                }
                logger.debug("Find Detail Item");
                Part part = item.getPart();
                int gender = part.getGender();
                logger.debug("Item Gender=" + gender);
                if ((gender == 1 || gender == 2) && (this.gender != gender)) {
                    getService().serverDialog("Giới tính không phù hợp");
                    return;
                }
                logger.debug("Get Type");
                short pType = part.getType();
                logger.debug("Type =" + pType);
                if (pType == -1) {

                    // hp quà ma quái
                    if(item.getId()==5532)
                    {
                        if((this.chests.size() >= this.getChestSlot())){
                            getService().serverMessage("Bạn phải có ít nhất 1 ô trống");
                            return;
                        }
                        removeItem(item.getId(), 1);
                        GiftBox giftBox = new GiftBox();
                        giftBox.openHopQuaMaQuai(this,item);
                        return;
                    }
                    int zOrder = part.getZOrder();
                    Item w = findItemWearingByZOrder(zOrder);
                    if(this.chestSlot <= chests.size()){
                        this.getAvatarService().serverDialog("Rương đồ đã đầy 001 ");
                        return;
                    }
                    if (w != null) {
                        removeItemFromWearing(w);
                        addItemToChests(w);
                    }
                    addItemToWearing(item);
                    removeItemFromChests(item);
                    sortWearing();
                    getMapService().usingPart(id, itemID);
                } else if (pType == -2) {
                    if(item.getId()==683)
                    {
                        if((this.chests.size() >= this.getChestSlot())){
                            getService().serverMessage("Bạn phải có ít nhất 1 ô trống");
                            return;
                        }
                        removeItem(item.getId(), 1);
                        GiftBox giftBox = new GiftBox();
                        giftBox.open(this,item);
                        return;
                    }
                    if(item.getId()==5408)
                    {
                        if((this.chests.size() >= this.getChestSlot()-4)){
                            getService().serverMessage("Bạn phải có ít nhất 5 ô trống để mở hộp quà hải tặc");
                            return;
                        }
                        removeItem(item.getId(), 1);
                        GiftBox giftBox = new GiftBox();
                        giftBox.openHaiTac(this,item);
                    }
                    if(item.getId()==5324)
                    {
                        if((this.chests.size() >= this.getChestSlot()-2)){
                            getService().serverMessage("Bạn phải có ít nhất 3 ô trống để mở hộp quà siêu nhân");
                            return;
                        }
                        removeItem(item.getId(), 1);
                        GiftBox giftBox = new GiftBox();
                        giftBox.openSieuNhan(this,item);
                    }
                    if(item.getId()==5880)
                    {
                        if((this.chests.size() >= this.getChestSlot()-3)){
                            getService().serverMessage("Bạn phải có ít nhất 4 ô trống để mở hộp quà siêu anh hùng");
                            return;
                        }
                        removeItem(item.getId(), 1);
                        GiftBox giftBox = new GiftBox();
                        giftBox.openSetVuTru(this,item);
                    }

                    else {
                        getService().serverMessage(String.format("Số lượng: %,d", item.getQuantity()));
                    }

                } else {
//                    item = findItemInWearing(itemID);
//                    removeItemFromWearing(item);
//                    addItemToChests(item);
//                    getMapService().usingPart(id, itemID);
                      getService().serverDialog("error 0020");//Vật phẩm shop Loi, sẽ sớm fix
                }
            } else {
                Item item = findItemInWearing(itemID);
                if (item == null) {
                    return;
                }
                int zOrder = item.getPart().getZOrder();
                if (zOrder == 10 || zOrder == 20 || zOrder == 50) {
                    getService().serverDialog("Không thể cất vật phẩm này.");
                    return;
                }
                if(this.chestSlot <= chests.size()){
                    this.getAvatarService().serverDialog("Rương đồ đã đầy 002 ");
                    return;
                }
                removeItemFromWearing(item);
                addItemToChests(item);
                getMapService().usingPart(id, itemID);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void usingItem(Message ms) {
        try {
            short itemID = ms.reader().readShort();
            byte type = ms.reader().readByte();
            usingItem(itemID, type);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void chat(Message ms) {
        try {
            if (ms.reader().available() < 4) {
                return;
            }
            String message = ms.reader().readUTF();
            getMapService().chat(this, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doRemoveItem(Message ms) {
        try {
            short itemID = ms.reader().readShort();
            byte type = ms.reader().readByte();
            if (type == 0) {
                Item item = findItemInWearing(itemID);
                if (item != null) {
                    int zOrder = item.getPart().getZOrder();
                    if (zOrder == 10 || zOrder == 20 || zOrder == 50) {
                        getAvatarService().serverDialog("error : 001");
                        return;
                    }
                    removeItemFromWearing(item);
                    getMapService().removeItem(id, itemID);
                    if (getStylish() > 0) {
                        setStylish((byte) (getStylish() - 1));
                        getAvatarService().requestYourInfo(this);
                    }
                }
            } else {
                Item item = findItemInChests(itemID);
                if (item != null) {
                    removeItemFromChests(item);
                    getAvatarService().removeItem(id, itemID);
                    if (getStylish() > 0) {
                        setStylish((byte) (getStylish() - 1));
                        getAvatarService().requestYourInfo(this);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void notifyNetWaitMessage() throws IOException {
        synchronized (this.session.obj) {
            this.session.obj.notifyAll();
        }
    }

    public void skillUidToBoss(List<User> players,int us ,int npcID,byte skill1,byte skill2){
        for (User player : players) {
            EffectService.createEffect()
                    .session(player.session)
                    .id(skill1)
                    .style((byte) 0)
                    .loopLimit((byte) 5)
                    .loop((short) 1)
                    .loopType((byte) 1)
                    .radius((short) 1)
                    .idPlayer(us)
                    .send();
            EffectService.createEffect()
                    .session(player.session)
                    .id(skill2)
                    .style((byte) 0)
                    .loopLimit((byte) 5)
                    .loop((short) 1)
                    .loopType((byte) 1)
                    .radius((short) 1)
                    .idPlayer(npcID)
                    .send();
        };
    }
}