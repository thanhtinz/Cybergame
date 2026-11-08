package avatar.service;

import avatar.constants.Cmd;
import avatar.db.DbManager;
import avatar.item.Item;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [CREATE] Tính năng cho 12 NPC vốn chỉ đứng + chat ở bản gốc
 * (lệnh Tín 2026-11-07, docs/npc-fun-spec.md). Phong bì −126:
 * xổ số Vé Số 465 (panel riêng) + quiz phiên (645/646/315/760,
 * panel riêng) + các tính năng qua menu NPC (NpcHandler gọi các
 * hàm tĩnh trong lớp này): quà ngày, phóng tàu, xem bói, luyện
 * dược, đổi quà, cho Zombie ăn, cho tiền Ăn Xin + điểm Phúc.
 *
 * Giới hạn ngày nằm ở bảng npc_fun_daily (ngày giờ VN); điểm Phúc
 * của Ăn Xin lưu bền ở npc_state; vé số ở lottery_tickets.
 */
public class NpcFunService extends Service {

    public static final byte F_LOTTERY_STATE = 0;
    public static final byte F_LOTTERY_BUY = 1;
    public static final byte F_QUIZ_ANSWER = 3;
    public static final byte F_QUIZ_QUIT = 4;
    public static final byte S_LOTTERY = 10;
    public static final byte S_QUIZ_Q = 11;
    public static final byte S_QUIZ_END = 12;
    public static final byte S_MSG = 13;

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int TICKET_PRICE = 5000;
    static final int TICKET_MAX = 5;
    static final int PRIZE_EXACT = 200_000;
    static final int PRIZE_LAST = 25_000;

    private static final Random RND = new Random();

    public NpcFunService(Session session) {
        super(session);
    }

    // ==================== hạ tầng dữ liệu ====================

    static boolean schemaReady = false;

    static synchronized void ensureSchema() {
        if (schemaReady) return;
        schemaReady = true;
        try (Connection c = DbManager.getInstance().getConnection()) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS npc_fun_daily ("
                    + "user_id INT, npc_id INT, day VARCHAR(10), cnt INT DEFAULT 0,"
                    + " PRIMARY KEY (user_id, npc_id, day))");
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS npc_state ("
                    + "user_id INT, npc_id INT, data VARCHAR(255) DEFAULT '',"
                    + " PRIMARY KEY (user_id, npc_id))");
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS lottery_tickets ("
                    + "user_id INT, day VARCHAR(10), number INT, settled TINYINT DEFAULT 0,"
                    + " PRIMARY KEY (user_id, day, number))");
        } catch (Exception ignored) {
        }
    }

    static String today() {
        return LocalDate.now(ZONE).toString();
    }

    static int dailyCount(int userId, int npcId) {
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT cnt FROM npc_fun_daily WHERE user_id=? AND npc_id=? AND day=?")) {
            ps.setInt(1, userId);
            ps.setInt(2, npcId);
            ps.setString(3, today());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {
        }
        return 0;
    }

    static void bumpDaily(int userId, int npcId) {
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO npc_fun_daily (user_id, npc_id, day, cnt) VALUES (?, ?, ?, 1)"
                     + " ON DUPLICATE KEY UPDATE cnt = cnt + 1")) {
            ps.setInt(1, userId);
            ps.setInt(2, npcId);
            ps.setString(3, today());
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    static String getState(int userId, int npcId) {
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data FROM npc_state WHERE user_id=? AND npc_id=?")) {
            ps.setInt(1, userId);
            ps.setInt(2, npcId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (Exception ignored) {
        }
        return "";
    }

    static void setState(int userId, int npcId, String data) {
        ensureSchema();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO npc_state (user_id, npc_id, data) VALUES (?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE data = VALUES(data)")) {
            ps.setInt(1, userId);
            ps.setInt(2, npcId);
            ps.setString(3, data);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    static String fmt(long v) {
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(v);
    }

    private void send(Message m) {
        try { session.sendMessage(m); } catch (Exception ignored) { }
    }

    private void sendMsg(boolean ok, String text) throws IOException {
        Message m = new Message(Cmd.NPCFUN);
        DataOutputStream w = m.writer();
        w.writeByte(S_MSG);
        w.writeBoolean(ok);
        w.writeUTF(text);
        send(m);
    }

    // ==================== gói vào ====================

    public void onMessage(Message ms) throws IOException {
        User me = session.user;
        if (me == null) return;
        DataInputStream r = ms.reader();
        if (r.available() < 1) return;
        int sub = r.readByte();
        switch (sub) {
            case F_LOTTERY_STATE:
                sendLottery(me);
                break;
            case F_LOTTERY_BUY:
                if (r.available() >= 1) buyTicket(me, r.readByte() & 0xFF);
                break;
            case F_QUIZ_ANSWER:
                if (r.available() >= 1) answerQuiz(me, r.readByte());
                break;
            case F_QUIZ_QUIT:
                RUNS.remove(me.getId());
                break;
            default:
                break;
        }
    }

    // ==================== XỔ SỐ (465) ====================

    /** Ngày của kỳ quay sắp tới: trước 20:00 là hôm nay, sau là mai. */
    static LocalDate drawDay() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate d = now.toLocalDate();
        return now.toLocalTime().isBefore(LocalTime.of(20, 0)) ? d : d.plusDays(1);
    }

    static int winNumber(LocalDate day) {
        return new Random(("AVDK-LOTTERY-" + day).hashCode()).nextInt(100);
    }

    /** Quyết toán mọi vé đã qua giờ quay của người chơi; trả tổng xu trúng. */
    static long settle(User me) {
        ensureSchema();
        long total = 0;
        LocalDateTime now = LocalDateTime.now(ZONE);
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT day, number FROM lottery_tickets WHERE user_id=? AND settled=0")) {
            ps.setInt(1, me.getId());
            ResultSet rs = ps.executeQuery();
            List<String> done = new ArrayList<>();
            while (rs.next()) {
                LocalDate day = LocalDate.parse(rs.getString(1));
                int num = rs.getInt(2);
                if (day.atTime(20, 0).isAfter(now)) continue; // chưa tới giờ quay
                int win = winNumber(day);
                long prize = num == win ? PRIZE_EXACT
                        : (num % 10 == win % 10 ? PRIZE_LAST : 0);
                total += prize;
                done.add(day + ":" + num);
            }
            if (!done.isEmpty()) {
                try (PreparedStatement up = c.prepareStatement(
                        "UPDATE lottery_tickets SET settled=1 WHERE user_id=? AND day=? AND number=?")) {
                    for (String d : done) {
                        String[] p = d.split(":");
                        up.setInt(1, me.getId());
                        up.setString(2, p[0]);
                        up.setInt(3, Integer.parseInt(p[1]));
                        up.executeUpdate();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (total > 0) me.updateXu(total);
        return total;
    }

    public void sendLottery(User me) throws IOException {
        long claimed = settle(me);
        LocalDate dd = drawDay();
        List<Integer> mine = new ArrayList<>();
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT number FROM lottery_tickets WHERE user_id=? AND day=? ORDER BY number")) {
            ps.setInt(1, me.getId());
            ps.setString(2, dd.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) mine.add(rs.getInt(1));
        } catch (Exception ignored) {
        }
        // kỳ gần nhất đã quay
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate last = now.toLocalTime().isBefore(LocalTime.of(20, 0))
                ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        String lastText = "Kỳ " + last.getDayOfMonth() + "/" + last.getMonthValue()
                + ": số trúng " + String.format("%02d", winNumber(last))
                + " · Kỳ tới quay 20:00 ngày " + dd.getDayOfMonth() + "/" + dd.getMonthValue();
        Message m = new Message(Cmd.NPCFUN);
        DataOutputStream w = m.writer();
        w.writeByte(S_LOTTERY);
        w.writeUTF(lastText);
        w.writeByte(mine.size());
        for (int n : mine) w.writeByte(n);
        w.writeByte(Math.max(0, TICKET_MAX - mine.size()));
        w.writeInt(TICKET_PRICE);
        w.writeInt((int) Math.min(Integer.MAX_VALUE, claimed));
        send(m);
    }

    void buyTicket(User me, int number) throws IOException {
        if (number < 0 || number > 99) {
            sendMsg(false, "Số vé phải từ 00 đến 99.");
            return;
        }
        LocalDate dd = drawDay();
        int owned = 0;
        boolean dup = false;
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT number FROM lottery_tickets WHERE user_id=? AND day=?")) {
            ps.setInt(1, me.getId());
            ps.setString(2, dd.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                owned++;
                if (rs.getInt(1) == number) dup = true;
            }
        } catch (Exception ignored) {
        }
        if (dup) {
            sendMsg(false, "Bạn đã mua vé số " + String.format("%02d", number) + " cho kỳ này rồi.");
            return;
        }
        if (owned >= TICKET_MAX) {
            sendMsg(false, "Mỗi kỳ chỉ mua tối đa " + TICKET_MAX + " vé.");
            return;
        }
        if (me.getXu() < TICKET_PRICE) {
            sendMsg(false, "Không đủ xu: một vé giá " + fmt(TICKET_PRICE) + " xu.");
            return;
        }
        try (Connection c = DbManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO lottery_tickets (user_id, day, number, settled) VALUES (?, ?, ?, 0)")) {
            ps.setInt(1, me.getId());
            ps.setString(2, dd.toString());
            ps.setInt(3, number);
            ps.executeUpdate();
        } catch (Exception e) {
            sendMsg(false, "Mua vé không thành công, thử lại nhé.");
            return;
        }
        me.updateXu(-TICKET_PRICE);
        sendMsg(true, "Đã mua vé số " + String.format("%02d", number) + " kỳ "
                + dd.getDayOfMonth() + "/" + dd.getMonthValue() + ". Chúc bạn may mắn!");
        sendLottery(me);
    }

    // ==================== QUIZ PHIÊN (645/646/315/760) ====================

    static final String[][] EN_BANK = {
        {"\"Apple\" trong tiếng Anh nghĩa là gì?", "Quả táo", "Quả cam", "Quả chuối", "Quả nho"},
        {"Từ nào sau đây là động từ?", "Run", "Happy", "Table", "Blue"},
        {"\"Good morning\" là lời chào vào lúc nào?", "Buổi sáng", "Buổi trưa", "Buổi tối", "Ban đêm"},
        {"Số \"seven\" trong tiếng Anh là số mấy?", "7", "6", "8", "5"},
        {"Con mèo trong tiếng Anh gọi là gì?", "Cat", "Dog", "Bird", "Fish"},
        {"\"Thank you\" nghĩa là gì?", "Cảm ơn", "Xin chào", "Tạm biệt", "Xin lỗi"},
        {"Màu đỏ trong tiếng Anh là gì?", "Red", "Green", "Blue", "Yellow"},
        {"Từ trái nghĩa của \"big\" là gì?", "Small", "Tall", "Long", "Fast"},
        {"Điền từ đúng: \"I ___ a student.\"", "am", "is", "are", "be"},
        {"Thứ Hai trong tiếng Anh là gì?", "Monday", "Friday", "Sunday", "Tuesday"},
        {"\"Water\" trong tiếng Anh nghĩa là gì?", "Nước", "Lửa", "Đất", "Gió"},
        {"Số \"twenty\" trong tiếng Anh là số mấy?", "20", "12", "30", "22"},
    };

    static final String[][] VN_BANK = {
        {"Điền vào tục ngữ: \"Ăn quả nhớ kẻ ___\"", "trồng cây", "hái quả", "bán cây", "tưới cây"},
        {"Điền vào tục ngữ: \"Gần mực thì đen, gần ___ thì sáng\"", "đèn", "trăng", "lửa", "gương"},
        {"Truyện Kiều là tác phẩm của ai?", "Nguyễn Du", "Nguyễn Trãi", "Hồ Xuân Hương", "Chu Mạnh Trinh"},
        {"Từ nào viết đúng chính tả?", "Xanh xao", "Sanh sao", "Xanh sao", "Sanh xao"},
        {"Điền vào ca dao: \"Công cha như núi ___\"", "Thái Sơn", "Trường Sơn", "Hoàng Liên Sơn", "Bạch Mã"},
        {"Điền vào tục ngữ: \"Học ăn, học nói, học gói, học ___\"", "mở", "làm", "chơi", "ngủ"},
        {"Tác giả của \"Bình Ngô đại cáo\" là ai?", "Nguyễn Trãi", "Lê Lợi", "Trần Hưng Đạo", "Ngô Quyền"},
        {"Từ \"tri thức\" thuộc nhóm từ nào?", "Từ Hán Việt", "Từ thuần Việt", "Từ láy", "Từ mượn tiếng Anh"},
        {"Điền vào tục ngữ: \"Một cây làm chẳng nên non, ___ cây chụm lại nên hòn núi cao\"", "Ba", "Hai", "Bốn", "Năm"},
        {"Điền vào tục ngữ: \"Chim có tổ, người có ___\"", "tông", "nhà", "quê", "gốc"},
        {"Từ nào sau đây là từ láy?", "Lung linh", "Học sinh", "Bàn ghế", "Sông núi"},
        {"Điền vào tục ngữ: \"Tốt gỗ hơn tốt ___\"", "nước sơn", "màu sơn", "lớp sơn", "vân gỗ"},
    };

    static final String[][] RIDDLE_BANK = {
        {"Câu đố của Nhân Sư: Cái gì càng đào càng lớn?", "Cái hố", "Cái túi", "Cái thùng", "Cái nợ"},
        {"Câu đố của Nhân Sư: Bánh gì mà không ăn được?", "Bánh xe", "Bánh chưng", "Bánh mì", "Bánh bao"},
        {"Câu đố của Nhân Sư: Cái gì đi thì nằm, đứng thì nằm, nhưng nằm thì đứng?", "Bàn chân", "Cái bóng", "Con thuyền", "Cái gối"},
        {"Câu đố của Nhân Sư: Quả gì có rất nhiều mắt?", "Quả dứa", "Quả cam", "Quả táo", "Quả nho"},
        {"Câu đố của Nhân Sư: Cái gì đen khi mua, đỏ khi dùng, xám xịt khi vứt đi?", "Than", "Gỗ", "Sắt", "Giấy"},
        {"Câu đố của Nhân Sư: Cái gì chỉ tăng mà không bao giờ giảm?", "Tuổi", "Tiền", "Cân nặng", "Chiều cao"},
        {"Câu đố của Nhân Sư: Cái gì là của bạn, nhưng người khác dùng nhiều hơn bạn?", "Tên của bạn", "Tiền của bạn", "Áo của bạn", "Nhà của bạn"},
        {"Câu đố của Nhân Sư: Cái gì càng kéo càng ngắn?", "Điếu thuốc", "Sợi dây", "Con đường", "Cái bóng"},
        {"Câu đố của Nhân Sư: Con gì mang được khúc gỗ to mà không mang nổi hòn sỏi nhỏ?", "Con sông", "Con voi", "Con kiến", "Con ngựa"},
        {"Câu đố của Nhân Sư: Đường gì ngọt mà không đi lên được?", "Đường cát", "Đường nhựa", "Đường mòn", "Đường làng"},
    };

    static class Run {
        int npc;
        List<String[]> qs;
        int pos;
        int correct;
        int correctIdx;
        long deadline;
        int secs;
        int perXu;
        int bonus;
    }

    static final Map<Integer, Run> RUNS = new ConcurrentHashMap<>();

    /** Bắt đầu phiên quiz cho 1 NPC (gọi từ menu NPC). */
    public void startQuiz(User me, int npcId) throws IOException {
        List<String[]> bank;
        int nQ, secs, perXu, bonus;
        String who;
        switch (npcId) {
            case 645:
                bank = new ArrayList<>(List.of(EN_BANK)); nQ = 5; secs = 30; perXu = 400; bonus = 2000;
                who = "Cô giáo tiếng Anh";
                break;
            case 646:
                bank = new ArrayList<>(List.of(VN_BANK)); nQ = 5; secs = 30; perXu = 400; bonus = 2000;
                who = "Cô giáo tiếng Việt";
                break;
            case 315:
                bank = new ArrayList<>();
                bank.addAll(List.of(EN_BANK));
                bank.addAll(List.of(VN_BANK));
                bank.addAll(List.of(QuizService.BANK));
                nQ = 8; secs = 15; perXu = 600; bonus = 5000;
                who = "Giám Thị";
                break;
            case 760:
                bank = new ArrayList<>(List.of(RIDDLE_BANK)); nQ = 3; secs = 45; perXu = 1500; bonus = 0;
                who = "Pharaoh";
                break;
            default:
                return;
        }
        if (dailyCount(me.getId(), npcId) >= 1) {
            me.getAvatarService().serverDialog(who + ": Hôm nay bạn đã làm rồi. Ngày mai quay lại nhé!");
            return;
        }
        bumpDaily(me.getId(), npcId);
        Collections.shuffle(bank, RND);
        Run run = new Run();
        run.npc = npcId;
        run.qs = bank.subList(0, Math.min(nQ, bank.size()));
        run.secs = secs;
        run.perXu = perXu;
        run.bonus = bonus;
        RUNS.put(me.getId(), run);
        sendQuizQ(me, run);
    }

    private void sendQuizQ(User me, Run run) throws IOException {
        String[] q = run.qs.get(run.pos);
        List<String> opts = new ArrayList<>(List.of(q[1], q[2], q[3], q[4]));
        Collections.shuffle(opts, RND);
        run.correctIdx = opts.indexOf(q[1]);
        run.deadline = System.currentTimeMillis() + run.secs * 1000L;
        Message m = new Message(Cmd.NPCFUN);
        DataOutputStream w = m.writer();
        w.writeByte(S_QUIZ_Q);
        w.writeShort(run.npc);
        w.writeByte(run.pos + 1);
        w.writeByte(run.qs.size());
        w.writeUTF(q[0]);
        for (String o : opts) w.writeUTF(o);
        w.writeShort(run.secs);
        send(m);
    }

    void answerQuiz(User me, int idx) throws IOException {
        Run run = RUNS.get(me.getId());
        if (run == null) return;
        if (System.currentTimeMillis() > run.deadline + 2000) {
            endQuiz(me, run, true);
            return;
        }
        if (idx == run.correctIdx) {
            run.correct++;
            TitleService.bump(me.getId(), "quiz_ok");
        }
        run.pos++;
        if (run.pos >= run.qs.size()) {
            endQuiz(me, run, false);
        } else {
            sendQuizQ(me, run);
        }
    }

    private void endQuiz(User me, Run run, boolean expired) throws IOException {
        RUNS.remove(me.getId());
        int total = run.qs.size();
        long pay = (long) run.correct * run.perXu;
        boolean perfect = run.correct == total;
        if (perfect && run.bonus > 0) pay += run.bonus;
        if (pay > 0) me.updateXu(pay);
        StringBuilder sb = new StringBuilder();
        if (expired) sb.append("Hết giờ! ");
        sb.append("Bạn đúng ").append(run.correct).append("/").append(total).append(" câu.");
        if (pay > 0) sb.append(" Nhận ").append(fmt(pay)).append(" xu.");
        if (perfect && run.bonus > 0) sb.append(" Tuyệt đối! Thưởng thêm ").append(fmt(run.bonus)).append(" xu đã tính trong tổng.");
        if (run.correct == 0 && pay == 0) sb.append(" Đừng nản, mai làm lại nhé!");
        Message m = new Message(Cmd.NPCFUN);
        DataOutputStream w = m.writer();
        w.writeByte(S_QUIZ_END);
        w.writeUTF(sb.toString());
        send(m);
    }

    // ==================== TÍNH NĂNG QUA MENU ====================

    /** 50 Tiên Cá — nhận phước biển 1 lần/ngày. */
    public static void mermaidGift(User us) throws IOException {
        if (dailyCount(us.getId(), 50) >= 1) {
            us.getAvatarService().serverDialog("Tiên Cá: Hôm nay ta đã ban phước cho bạn rồi, mai quay lại nhé!");
            return;
        }
        bumpDaily(us.getId(), 50);
        int roll = RND.nextInt(100);
        if (roll < 70) {
            int xu = 800 + RND.nextInt(1701);
            us.updateXu(xu);
            us.getAvatarService().serverDialog("Tiên Cá vẫy đuôi, bọt biển hoá thành " + fmt(xu) + " xu tặng bạn!");
        } else if (roll < 90) {
            int xu = 3000 + RND.nextInt(3001);
            us.updateXu(xu);
            us.getAvatarService().serverDialog("Tiên Cá hát một khúc, sóng dâng tặng bạn " + fmt(xu) + " xu!");
        } else {
            int luong = 1 + RND.nextInt(3);
            us.updateLuong(luong);
            us.getAvatarService().serverDialog("Tiên Cá tặng bạn viên ngọc biển: +" + luong + " lượng!");
        }
    }

    /** 335 Phi Hành Gia — phóng tàu thám hiểm: lượt đầu ngày miễn phí. */
    public static void astronautLaunch(User us) throws IOException {
        int cnt = dailyCount(us.getId(), 335);
        if (cnt >= 6) {
            us.getAvatarService().serverDialog("Phi Hành Gia: Hôm nay tàu đã bay đủ 6 chuyến rồi, mai bay tiếp nhé!");
            return;
        }
        if (cnt >= 1) {
            if (us.getXu() < 10_000) {
                us.getAvatarService().serverDialog("Phi Hành Gia: Chuyến bay thêm tốn 10.000 xu nhiên liệu, bạn chưa đủ xu.");
                return;
            }
            us.updateXu(-10_000);
        }
        bumpDaily(us.getId(), 335);
        int roll = RND.nextInt(100);
        if (roll < 30) {
            us.getAvatarService().serverDialog("Phi Hành Gia: Tàu bay lạc vào vành đai thiên thạch, trở về tay trắng. Mai thử lại nhé!");
        } else if (roll < 70) {
            int xu = 4000 + RND.nextInt(16001);
            us.updateXu(xu);
            us.getAvatarService().serverDialog("Phi Hành Gia: Tàu mang về quặng sao bán được " + fmt(xu) + " xu!");
        } else if (roll < 90) {
            int luong = 1 + RND.nextInt(4);
            us.updateLuong(luong);
            us.getAvatarService().serverDialog("Phi Hành Gia: Tìm thấy tinh thể năng lượng: +" + luong + " lượng!");
        } else {
            us.addItemToChests(new Item(3672, -1, 1));
            us.getAvatarService().serverDialog("Phi Hành Gia: Nhặt được 1 viên Đá ngũ sắc từ vũ trụ, đã cất vào rương của bạn!");
        }
    }

    static final String[][] FORTUNES = {
        {"Đại Cát", "Sao tốt chiếu mệnh, hôm nay làm gì cũng thuận buồm xuôi gió!"},
        {"Đại Cát", "Tài lộc gõ cửa, vận may đang ở đỉnh cao!"},
        {"Cát", "Niềm vui nhỏ đang trên đường tới với bạn."},
        {"Cát", "Hôm nay bạn sẽ gặp quý nhân phù trợ."},
        {"Bình", "Mọi sự yên ổn, cứ thong thả đừng nóng vội."},
        {"Bình", "Giữ gìn sức khoẻ, tiền bạc ổn định là phúc."},
        {"Hung", "Hôm nay hơi xui, cẩn thận kẻo... thôi, đùa đấy, không mất gì đâu!"},
        {"Hung", "Tránh tranh cãi hôm nay, ở nhà trồng cây là nhất."},
    };

    /** 540 Bậc Thầy Phù Thủy — xem bói 1 lần/ngày. */
    public static void witchFortune(User us) throws IOException {
        if (dailyCount(us.getId(), 540) >= 1) {
            us.getAvatarService().serverDialog("Bậc Thầy Phù Thủy: Quẻ hôm nay đã gieo rồi, mai ta xem tiếp cho.");
            return;
        }
        bumpDaily(us.getId(), 540);
        String[] f = FORTUNES[RND.nextInt(FORTUNES.length)];
        String extra = "";
        switch (f[0]) {
            case "Đại Cát":
                us.updateLuong(2);
                extra = " Nhận +2 lượng lấy may!";
                break;
            case "Cát":
                int xu1 = 1500 + RND.nextInt(1501);
                us.updateXu(xu1);
                extra = " Nhận +" + fmt(xu1) + " xu lấy may!";
                break;
            case "Bình":
                int xu2 = 300 + RND.nextInt(501);
                us.updateXu(xu2);
                extra = " Nhận +" + fmt(xu2) + " xu lấy may!";
                break;
            default:
                break;
        }
        us.getAvatarService().serverDialog("Quẻ " + f[0] + ": " + f[1] + extra);
    }

    // pool đồ ma quái (gender 0, cả hai giới mặc được) — đã kiểm
    // chứng tên + icon/frame khớp dữ liệu gốc (lệnh Tín 2026-11-07)
    static final int[] DARK_POOL = {291, 569, 570, 2263, 2411, 2412, 2413, 2414, 2565, 2279};

    /** 425 Phù Thủy Bóng Tối — luyện dược đen 3.000 xu, tối đa 3/ngày. */
    public static void darkBrew(User us) throws IOException {
        if (dailyCount(us.getId(), 425) >= 3) {
            us.getAvatarService().serverDialog("Phù Thủy Bóng Tối: Nồi dược hôm nay đã cạn, mai quay lại.");
            return;
        }
        if (us.getXu() < 3000) {
            us.getAvatarService().serverDialog("Phù Thủy Bóng Tối: Luyện dược tốn 3.000 xu, ngươi chưa đủ xu đâu.");
            return;
        }
        us.updateXu(-3000);
        bumpDaily(us.getId(), 425);
        int roll = RND.nextInt(100);
        if (roll < 35) {
            us.getAvatarService().serverDialog("Phù Thủy Bóng Tối: Bùm! Nồi dược nổ tung, mất trắng. Khà khà!");
        } else if (roll < 65) {
            int xu = 2000 + RND.nextInt(5001);
            us.updateXu(xu);
            us.getAvatarService().serverDialog("Phù Thủy Bóng Tối: Dược thành vàng lỏng, bán được " + fmt(xu) + " xu!");
        } else if (roll < 85) {
            int luong = 1 + RND.nextInt(3);
            us.updateLuong(luong);
            us.getAvatarService().serverDialog("Phù Thủy Bóng Tối: Đáy nồi kết tinh +" + luong + " lượng!");
        } else {
            int part = DARK_POOL[RND.nextInt(DARK_POOL.length)];
            us.addItemToChests(new Item(part, System.currentTimeMillis() + 15L * 86400000L, 1));
            us.getAvatarService().serverDialog("Phù Thủy Bóng Tối: Trong nồi có một món đồ ma quái (hạn 15 ngày), đã cất vào rương của bạn!");
        }
    }

    // pool Pretty Witch: {partId, gender} — gender 0 = cả hai giới;
    // đã kiểm chứng tên + icon/frame khớp dữ liệu gốc
    static final int[][] PRETTY_POOL = {
        {2261, 2}, {2262, 2}, {2296, 2}, {2297, 2}, {2878, 2}, {2879, 2},
        {2880, 2}, {2881, 2}, {596, 2}, {599, 2}, {600, 2}, {618, 2},
        {619, 2}, {477, 2}, {2283, 1},
        {291, 0}, {2263, 0}, {2411, 0}, {569, 0}, {570, 0},
    };

    /** 590 Pretty Witch — đổi quà ma thuật 20.000 xu, tối đa 2/ngày. */
    public static void prettyGacha(User us) throws IOException {
        if (dailyCount(us.getId(), 590) >= 2) {
            us.getAvatarService().serverDialog("Pretty Witch: Hôm nay ta hết quà rồi, mai quay lại nhé!");
            return;
        }
        if (us.getXu() < 20_000) {
            us.getAvatarService().serverDialog("Pretty Witch: Đổi quà tốn 20.000 xu, bạn chưa đủ xu rồi.");
            return;
        }
        us.updateXu(-20_000);
        bumpDaily(us.getId(), 590);
        List<Integer> pool = new ArrayList<>();
        int myGender = us.getGender();
        for (int[] e : PRETTY_POOL) {
            if (e[1] == 0 || e[1] == myGender) pool.add(e[0]);
        }
        int part = pool.get(RND.nextInt(pool.size()));
        boolean permanent = RND.nextInt(100) < 15;
        long expired = permanent ? -1 : System.currentTimeMillis() + 7L * 86400000L;
        us.addItemToChests(new Item(part, expired, 1));
        us.getAvatarService().serverDialog("Pretty Witch: Tặng bạn một món quà ma thuật"
                + (permanent ? " VĨNH VIỄN" : " (hạn 7 ngày)") + ", đã cất vào rương của bạn!");
    }

    // tên món đã nấu theo productId (từ farmtrees.json phía client)
    public static final Map<Integer, String> DISH_NAMES = Map.ofEntries(
            Map.entry(205, "Bánh Trứng"), Map.entry(206, "Bánh sữa"),
            Map.entry(207, "Cơm"), Map.entry(208, "Cơm trứng"),
            Map.entry(209, "Cơm cà rốt"), Map.entry(210, "Nước ép trái cây"),
            Map.entry(211, "Gỏi xoài"), Map.entry(212, "Sữa chua Khế"),
            Map.entry(213, "Trái cây dằm"), Map.entry(214, "Tinh dầu hướng dương"),
            Map.entry(216, "Nước hoa"), Map.entry(217, "Sữa chua trái cây"),
            Map.entry(218, "Trái cây dĩa"), Map.entry(220, "Salad khế"),
            Map.entry(256, "Bánh Xèo"), Map.entry(257, "Bò viên"),
            Map.entry(258, "Cá viên"), Map.entry(259, "Chè khúc bạch"),
            Map.entry(260, "Hột vịt lộn"), Map.entry(261, "Kem chiên"),
            Map.entry(262, "Kem tươi"), Map.entry(263, "Pizza"),
            Map.entry(264, "Trà đào"), Map.entry(265, "Trà sữa trái cây"),
            Map.entry(272, "Nước ép dưa hấu"), Map.entry(273, "Phô mai"),
            Map.entry(274, "Khoai tây chiên"), Map.entry(275, "Bắp Chiên Bơ"),
            Map.entry(281, "Tương Cà Chua"), Map.entry(287, "Tương ớt"));

    /** Danh sách món đã nấu người chơi đang có (tối đa 4) cho menu Zombie. */
    public static List<int[]> zombieDishes(User us) {
        List<int[]> out = new ArrayList<>();
        List<Integer> pids = new ArrayList<>(DISH_NAMES.keySet());
        Collections.sort(pids);
        for (int pid : pids) {
            if (Farm2Service.countProduct(us, pid) > 0) {
                out.add(new int[]{pid, Farm2Service.countProduct(us, pid)});
                if (out.size() >= 4) break;
            }
        }
        return out;
    }

    /** 535 Zombie — cho ăn 1 món đã nấu, tối đa 3 lần/ngày. */
    public static void feedZombie(User us, int pid) throws IOException {
        if (dailyCount(us.getId(), 535) >= 3) {
            us.getAvatarService().serverDialog("Zombie: No... quá... rồi... Mai... cho... ăn... tiếp...");
            return;
        }
        if (Farm2Service.countProduct(us, pid) < 1) {
            us.getAvatarService().serverDialog("Zombie: Món đó... hết... rồi...");
            return;
        }
        Farm2Service.takeProduct(us, pid, 1);
        bumpDaily(us.getId(), 535);
        String dish = DISH_NAMES.getOrDefault(pid, "món ngon");
        int roll = RND.nextInt(100);
        if (roll < 50) {
            int xu = 1000 + RND.nextInt(3001);
            us.updateXu(xu);
            us.getAvatarService().serverDialog("Zombie ăn " + dish + " ngon lành, vui quá nên nhả ra " + fmt(xu) + " xu tặng bạn!");
        } else if (roll < 75) {
            int luong = 1 + RND.nextInt(2);
            us.updateLuong(luong);
            us.getAvatarService().serverDialog("Zombie ăn " + dish + " xong ho ra +" + luong + " lượng, chắc nuốt nhầm từ hồi còn sống!");
        } else {
            us.getAvatarService().serverDialog("Zombie ăn " + dish + " xong... ói ra hết. Xui cho bạn rồi!");
        }
    }

    /** Điểm Phúc hiện có của người chơi ở Ăn Xin. */
    public static int beggarPhuc(int userId) {
        String data = getState(userId, 741);
        if (data.startsWith("phuc:")) {
            try { return Integer.parseInt(data.substring(5)); } catch (Exception ignored) { }
        }
        return 0;
    }

    /** 741 Ăn Xin — cho tiền xu, nhận điểm Phúc + cơ hội được trả ơn. */
    public static void beggarGive(User us, int amount) throws IOException {
        if (dailyCount(us.getId(), 741) >= 10) {
            us.getAvatarService().serverDialog("Ăn Xin: Hôm nay bạn đã cho ta nhiều rồi, phước đức vô lượng!");
            return;
        }
        if (us.getXu() < amount) {
            us.getAvatarService().serverDialog("Ăn Xin: Bạn không đủ " + fmt(amount) + " xu để cho ta.");
            return;
        }
        us.updateXu(-amount);
        bumpDaily(us.getId(), 741);
        int gain = amount >= 20_000 ? 8 : (amount >= 5_000 ? 3 : 1);
        int phuc = beggarPhuc(us.getId()) + gain;
        setState(us.getId(), 741, "phuc:" + phuc);
        int roll = RND.nextInt(100);
        String extra = "";
        if (roll < 20) {
            us.updateXu(amount * 2L);
            extra = " Ông lão cười lớn: \"Ta giàu ngầm đấy!\" và trả lại bạn gấp đôi: +" + fmt(amount * 2L) + " xu!";
        } else if (roll < 30) {
            us.updateLuong(1);
            extra = " Ông lão móc túi tặng lại bạn +1 lượng!";
        }
        us.getAvatarService().serverDialog("Ăn Xin: Đa tạ thí chủ! Bạn nhận +" + gain
                + " điểm Phúc (hiện có " + phuc + ")." + extra);
    }

    /** 741 Ăn Xin — đổi 20 điểm Phúc lấy 5 lượng. */
    public static void beggarExchange(User us) throws IOException {
        int phuc = beggarPhuc(us.getId());
        if (phuc < 20) {
            us.getAvatarService().serverDialog("Ăn Xin: Đổi quà cần 20 điểm Phúc, bạn mới có " + phuc + " điểm.");
            return;
        }
        setState(us.getId(), 741, "phuc:" + (phuc - 20));
        us.updateLuong(5);
        us.getAvatarService().serverDialog("Ăn Xin: Phước đức đủ đầy! Tặng bạn +5 lượng. (Còn " + (phuc - 20) + " điểm Phúc)");
    }
}
