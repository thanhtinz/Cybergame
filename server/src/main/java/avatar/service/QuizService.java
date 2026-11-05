package avatar.service;

import avatar.constants.Cmd;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ĐỐ VUI TRÊN MAP [PORT theo ANSWERQUESTION của bản chính 3.4.4].
 *
 * Bản chính: type AnswerQuestion (question/answer/listQuest/
 * titleAns/timeQuestion/point/idBoss/imgTime/imgPoint/timeTick)
 * là một thực thể câu hỏi hiện TRÊN MAP gắn với boss — người
 * chơi bấm vào, trả lời trắc nghiệm trong thời gian đếm ngược,
 * đúng thì được điểm. GlobalService.doAnswerQuestion xử lý gói.
 * Ngân hàng câu hỏi của bản chính nằm ở server nhà phát hành
 * (không có trong APK) nên ngân hàng câu ở đây là bản port tự
 * soạn; số gói -97 của bản chính đã là REQUEST_DYNAMIC_PART ở
 * bản fan -> port dùng -121.
 *
 * Luồng port: một luồng nền cứ 180 giây chọn khu đang có người
 * nhất và phát một câu hỏi cho cả khu (sub 0). Mỗi người trả lời
 * một lần (sub 1); đúng thì nhận xu theo tốc độ (<=10s: 100%,
 * <=20s: 60%, còn lại: 30% của 2.000 xu) + FEEDBACK riêng (sub 3).
 * Hết giờ server phát RESULT cho cả khu (sub 2: đáp án đúng +
 * tên người đúng nhanh nhất).
 */
public class QuizService extends Service {

    public static final String[][] BANK = {
        {"Thủ đô của Việt Nam là gì?", "Hà Nội", "Huế", "Đà Nẵng", "Cần Thơ"},
        {"Sông nào dài nhất Việt Nam?", "Sông Hồng", "Sông Mê Kông", "Sông Đồng Nai", "Sông Đà"},
        {"Hành tinh nào gần Mặt Trời nhất?", "Sao Thủy", "Sao Kim", "Sao Hỏa", "Trái Đất"},
        {"Công thức hoá học của nước là gì?", "H2O", "CO2", "O2", "H2SO4"},
        {"Con vật nào được gọi là chúa sơn lâm?", "Hổ", "Sư tử", "Báo", "Voi"},
        {"Việt Nam có bao nhiêu tỉnh thành (trước sáp nhập 2025)?", "63", "58", "64", "34"},
        {"Ai là tác giả Truyện Kiều?", "Nguyễn Du", "Nguyễn Trãi", "Hồ Xuân Hương", "Nguyễn Đình Chiểu"},
        {"Một tuần có bao nhiêu giờ?", "168", "148", "158", "178"},
        {"Đỉnh núi cao nhất Việt Nam là?", "Fansipan", "Bà Đen", "Lang Biang", "Chư Yang Sin"},
        {"Biển nào giáp phía Đông Việt Nam?", "Biển Đông", "Biển Hoa Đông", "Biển Andaman", "Biển Philippine"},
        {"Số nào là số nguyên tố nhỏ nhất?", "2", "1", "3", "0"},
        {"Loài cá nào bơi ngược dòng để đẻ trứng nổi tiếng?", "Cá hồi", "Cá chép", "Cá trê", "Cá rô"},
        {"Ai phát minh ra bóng đèn điện dây tóc thực dụng?", "Edison", "Newton", "Einstein", "Tesla"},
        {"Quốc gia nào đông dân nhất thế giới (2024)?", "Ấn Độ", "Trung Quốc", "Mỹ", "Indonesia"},
        {"Môn thể thao nào được gọi là môn thể thao vua?", "Bóng đá", "Bóng rổ", "Quần vợt", "Cầu lông"},
        {"Chữ cái đầu tiên trong bảng chữ cái tiếng Việt là?", "A", "Ă", "Â", "B"},
        {"Một năm nhuận có bao nhiêu ngày?", "366", "365", "364", "367"},
        {"Hồ nước ngọt lớn nhất Việt Nam là?", "Hồ Ba Bể", "Hồ Tây", "Hồ Trị An", "Hồ Dầu Tiếng"},
        {"Vịnh nào của Việt Nam là Di sản thiên nhiên thế giới?", "Vịnh Hạ Long", "Vịnh Lăng Cô", "Vịnh Xuân Đài", "Vịnh Cam Ranh"},
        {"Ai là vị vua đầu tiên của nhà Nguyễn?", "Gia Long", "Minh Mạng", "Tự Đức", "Khải Định"},
        {"Nhiệt độ sôi của nước ở điều kiện thường là?", "100°C", "90°C", "80°C", "120°C"},
        {"Con sông nào chảy qua Huế?", "Sông Hương", "Sông Hàn", "Sông Lam", "Sông Thu Bồn"},
        {"Trái Đất quay quanh Mặt Trời mất bao lâu?", "365 ngày", "30 ngày", "7 ngày", "12 giờ"},
        {"Loài chim nào không biết bay nhưng bơi rất giỏi?", "Chim cánh cụt", "Đà điểu", "Chim kiwi", "Gà"},
    };

    private static class Active {
        int id;
        int correctIdx;      // vị trí đáp án đúng SAU khi xáo (0..3)
        String correctText;
        long deadline;
        avatar.play.Zone zone;
        Set<Integer> answered = new HashSet<>();
        String fastestName = null;
        long fastestMs = Long.MAX_VALUE;
    }

    private static volatile Active active = null;
    private static volatile boolean tickerOn = false;
    private static int seq = 0;
    private static int lastBankIdx = -1;

    public QuizService(Session session) {
        super(session);
        ensureTicker();
    }

    /** Bật luồng sự kiện nền (một lần duy nhất trong tiến trình). */
    public static synchronized void ensureTicker() {
        if (tickerOn) return;
        tickerOn = true;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(60000L); // câu đầu sau 1 phút
                while (true) {
                    tickOnce();
                    Thread.sleep(180000L);
                }
            } catch (InterruptedException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void tickOnce() {
        try {
            if (active != null) closeActive();
            // chọn phiên có user đang ở khu park đông nhất
            Session best = null;
            int bestCount = 0;
            for (Session ss : avatar.server.ServerManager.clients) {
                if (ss == null || ss.user == null || ss.user.getZone() == null) continue;
                int cnt = 0;
                for (User pl : ss.user.getZone().getPlayers()) {
                    if (!(pl instanceof avatar.model.Npc)
                            && !(pl instanceof avatar.model.Boss)) cnt++;
                }
                if (cnt > bestCount) {
                    bestCount = cnt;
                    best = ss;
                }
            }
            if (best == null) return;
            User host = best.user;
            int bi;
            do {
                bi = (int) (Math.random() * BANK.length);
            } while (bi == lastBankIdx);
            lastBankIdx = bi;
            String[] row = BANK[bi];
            List<String> answers = new ArrayList<>();
            for (int i = 1; i <= 4; i++) answers.add(row[i]);
            Collections.shuffle(answers);
            Active a = new Active();
            a.id = ++seq;
            a.correctText = row[1];
            a.correctIdx = answers.indexOf(row[1]);
            a.deadline = System.currentTimeMillis() + 30000L;
            a.zone = host.getZone();
            active = a;
            Message ms = new Message(Cmd.QUIZ);
            DataOutputStream ds = ms.writer();
            ds.writeByte(0);
            ds.writeInt(a.id);
            ds.writeUTF(row[0]);
            for (String an : answers) ds.writeUTF(an);
            ds.writeByte(30);
            ds.writeInt(2000);
            ds.flush();
            host.getMapService().sendMessage(ms);
            // luồng đóng câu khi hết giờ
            final Active cur = a;
            Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(30500L);
                } catch (InterruptedException ignored) {
                }
                if (active == cur) closeActive();
            });
            closer.setDaemon(true);
            closer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void closeActive() {
        Active a = active;
        if (a == null) return;
        active = null;
        try {
            Message ms = new Message(Cmd.QUIZ);
            DataOutputStream ds = ms.writer();
            ds.writeByte(2);
            ds.writeUTF(a.correctText);
            ds.writeUTF(a.fastestName == null ? "" : a.fastestName);
            ds.flush();
            // phát cho mọi phiên còn ở khu đó
            for (Session ss : avatar.server.ServerManager.clients) {
                if (ss != null && ss.user != null && ss.user.getZone() == a.zone) {
                    ss.user.getMapService().sendMessage(ms);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onMessage(Message ms) throws IOException {
        User u = this.session.user;
        if (u == null) return;
        byte sub = ms.reader().readByte();
        if (sub == 9) { // PING: client vào khu, chỉ để chắc ticker chạy
            ensureTicker();
            return;
        }
        if (sub == 8) { // FORCE: admin phát câu hỏi ngay (kiểm chứng)
            if (u.getRole() >= 0) tickOnce();
            return;
        }
        if (sub != 1) return;
        int qid = ms.reader().readInt();
        int choice = ms.reader().readByte();
        Active a = active;
        int reward = 0;
        boolean correct = false;
        boolean accepted = a != null && a.id == qid
                && System.currentTimeMillis() <= a.deadline
                && u.getZone() == a.zone
                && !a.answered.contains(u.getId());
        if (accepted) {
            a.answered.add(u.getId());
            long used = 30000L - (a.deadline - System.currentTimeMillis());
            correct = choice == a.correctIdx;
            if (correct) {
                reward = used <= 10000L ? 2000 : used <= 20000L ? 1200 : 600;
                u.updateXu(reward);
                if (used < a.fastestMs) {
                    a.fastestMs = used;
                    a.fastestName = u.getDisplayName();
                }
            }
        }
        Message fb = new Message(Cmd.QUIZ);
        DataOutputStream ds = fb.writer();
        ds.writeByte(3);
        ds.writeByte(accepted && correct ? 1 : 0);
        ds.writeInt(reward);
        ds.writeUTF(a != null ? a.correctText : "");
        ds.flush();
        this.session.sendMessage(fb);
    }
}
