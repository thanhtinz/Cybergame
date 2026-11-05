package avatar.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import avatar.model.User;
import avatar.network.Message;

/**
 * Engine Đua thú [PORT] — server fan không có engine đua (handler 12
 * rỗng), bản port viết mới theo đúng giao thức client gốc Pack5
 * (RaceScr / RaceMsgHandler, xem docs/race-spec.md).
 *
 * Một chặng toàn server: MỞ CƯỢC 60s → CHẠY theo đoạn tốc độ (mô
 * phỏng 20 tick/giây) → TRẢ THƯỞNG → nghỉ 8s → chặng mới.
 * Trả thưởng [PORT]: tiền ăn = cược × rate, thuế 10% tiền ăn,
 * tiền nhận = tiền ăn − thuế. Rời trường đua giữa chặng = mất cược
 * (đúng câu thoại thoát của gốc).
 */
public class RaceService {

    public static final int[] PET_IMG = {313, 310, 312, 1024, 1025, 1224};
    public static final String[] PET_NAME = {
        "Tia Chớp", "Gà Bay", "Heo Hồng", "Cừu Mây", "Vịt Vàng", "Trâu Đất"};

    /** Quãng đường đua (đơn vị SD của client gốc: xuất phát x=20,
     *  đường đua map 108 dài ~2886 SD). */
    static final int TRACK_SD = 2866;
    static final int BET_SECONDS = 60;
    static final int REST_SECONDS = 8;
    static final int TAX_PERCENT = 10;

    private static final RaceService INSTANCE = new RaceService();
    public static RaceService getInstance() { return INSTANCE; }

    private final Random rnd = new Random();
    private final ScheduledExecutorService timer =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "race-timer");
            t.setDaemon(true);
            return t;
        });

    // ---- trạng thái chặng ----
    private int phase;               // 0 mở cược · 1 đang chạy · 2 nghỉ
    private long phaseEndMs;
    private int cycleGen;
    private final byte[] rate = new byte[6];
    private final byte[] form = new byte[6];
    private final byte[] health = new byte[6];
    private int[][] segNum, segV;    // đoạn tốc độ của chặng đang chạy
    private int[] finishTick;
    private int winner = -1;
    private int raceTotalTicks;

    // ---- người xem + cược ----
    private final Map<Integer, User> watchers = new LinkedHashMap<>();
    private final Map<Integer, int[]> stakes = new LinkedHashMap<>();
    private final int[] pool = new int[6];

    // ---- thống kê + lịch sử ----
    private final int[] petWins = new int[6];
    private final int[] petRaces = new int[6];
    private final List<int[]> histIcon = new ArrayList<>();
    private final List<String> histTime = new ArrayList<>();

    private RaceService() {
        startBetting();
    }

    // ------------------------------------------------------------------
    // Chu kỳ chặng
    // ------------------------------------------------------------------

    private synchronized void startBetting() {
        phase = 0;
        winner = -1;
        segNum = segV = null;
        stakes.clear();
        for (int i = 0; i < 6; i++) {
            pool[i] = 0;
            rate[i] = (byte) (2 + rnd.nextInt(5));      // 2..6
            form[i] = (byte) rnd.nextInt(3);
            health[i] = (byte) rnd.nextInt(3);
        }
        phaseEndMs = System.currentTimeMillis() + BET_SECONDS * 1000L;
        final int gen = ++cycleGen;
        broadcastLineup();
        timer.schedule(() -> {
            synchronized (RaceService.this) {
                if (gen == cycleGen && phase == 0) startRace();
            }
        }, BET_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void startRace() {
        phase = 1;
        segNum = new int[6][];
        segV = new int[6][];
        finishTick = new int[6];
        raceTotalTicks = 0;
        for (int p = 0; p < 6; p++) {
            genSegments(p);
            finishTick[p] = simulate(p);
            raceTotalTicks = Math.max(raceTotalTicks, finishTick[p]);
        }
        winner = 0;
        for (int p = 1; p < 6; p++) {
            if (finishTick[p] < finishTick[winner]) winner = p;
        }
        int raceSecs = (int) Math.ceil(raceTotalTicks / 20.0);
        phaseEndMs = System.currentTimeMillis() + raceSecs * 1000L + 1500;
        // Gói bắt đầu chặng (dạng bool true) cho người đang có mặt.
        Message m = new Message(1);
        try {
            DataOutputStream ds = m.writer();
            ds.writeByte(1);
            ds.writeBoolean(true);
            writeSegments(ds);
            ds.writeShort(raceSecs);
            ds.writeShort(raceSecs + REST_SECONDS);
            ds.flush();
        } catch (IOException ignored) {
        }
        broadcast(m);
        final int gen = cycleGen;
        long settleMs = raceTotalTicks * 50L + 1500;
        timer.schedule(() -> {
            synchronized (RaceService.this) {
                if (gen == cycleGen && phase == 1) settle();
            }
        }, settleMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void settle() {
        phase = 2;
        for (int p = 0; p < 6; p++) petRaces[p]++;
        petWins[winner]++;
        histIcon.add(0, new int[]{PET_IMG[winner]});
        histTime.add(0, new SimpleDateFormat("HH:mm dd/MM").format(new Date()));
        while (histIcon.size() > 10) {
            histIcon.remove(histIcon.size() - 1);
            histTime.remove(histTime.size() - 1);
        }
        // Trả thưởng cho người cược trúng thú về nhất.
        for (Map.Entry<Integer, int[]> e : stakes.entrySet()) {
            int stake = e.getValue()[winner];
            if (stake <= 0) continue;
            User u = watchers.get(e.getKey());
            if (u == null) continue; // đã rời trường đua: mất cược (gốc)
            int an = stake * rate[winner];
            int thue = an * TAX_PERCENT / 100;
            int nhan = an - thue;
            u.updateXu(nhan);
            u.getAvatarService().updateMoney(0);
            Message m = new Message(10);
            try {
                DataOutputStream ds = m.writer();
                ds.writeByte(winner);
                ds.writeUTF(PET_NAME[winner]);
                ds.writeByte(rate[winner]);
                ds.writeInt(stake);
                ds.writeInt(an);
                ds.writeInt(thue);
                ds.writeInt(nhan);
                ds.flush();
                u.sendMessage(m);
            } catch (IOException ignored) {
            }
        }
        stakes.clear();
        phaseEndMs = System.currentTimeMillis() + REST_SECONDS * 1000L;
        final int gen = cycleGen;
        timer.schedule(() -> {
            synchronized (RaceService.this) {
                if (gen == cycleGen && phase == 2) startBetting();
            }
        }, REST_SECONDS, TimeUnit.SECONDS);
    }

    /** Sinh đoạn tốc độ cho một thú: tốc độ quanh mức theo sức mạnh,
     *  thỉnh thoảng có đoạn nghỉ (v=0). Tổng quãng ≈ TRACK_SD. */
    private void genSegments(int p) {
        int strength = (7 - rate[p]) + form[p] + health[p];   // 1..9
        double center = 2.2 + strength * 0.33;
        List<Integer> nums = new ArrayList<>();
        List<Integer> vs = new ArrayList<>();
        int covered = 0;
        while (covered < TRACK_SD) {
            int len = 15 + rnd.nextInt(50);
            int v;
            if (rnd.nextInt(100) < 8) {
                v = 0;
                len = 10 + rnd.nextInt(16);
            } else {
                v = (int) Math.round(center + (rnd.nextDouble() * 3 - 1.5));
                if (v < 1) v = 1;
                if (v > 6) v = 6;
            }
            nums.add(len);
            vs.add(v);
            covered += v * len;
        }
        segNum[p] = nums.stream().mapToInt(Integer::intValue).toArray();
        segV[p] = vs.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Thời gian chạy hết đoạn của một thú (tick). Client gốc xếp
     *  hạng về đích theo thứ tự HẾT ĐOẠN (PetRace.update gán nWin khi
     *  count >= numTick.length), và tổng quãng của các đoạn luôn phủ
     *  hết đường đua nên hết đoạn ≈ về đích — server dùng đúng luật
     *  này để kết quả trả thưởng khớp thứ hạng người chơi nhìn thấy. */
    private int simulate(int p) {
        int tick = 0;
        for (int n : segNum[p]) tick += n;
        return tick;
    }

    private void writeSegments(DataOutputStream ds) throws IOException {
        for (int p = 0; p < 6; p++) {
            ds.writeByte(segNum[p].length);
            for (int i = 0; i < segNum[p].length; i++) {
                ds.writeShort(segNum[p][i]);
                ds.writeShort(segV[p][i]);
            }
        }
    }

    // ------------------------------------------------------------------
    // Người xem
    // ------------------------------------------------------------------

    public synchronized void addWatcher(User u) {
        if (u != null) watchers.put(u.getId(), u);
    }

    public synchronized void leave(User u) {
        if (u != null) watchers.remove(u.getId());
    }

    private void broadcast(Message m) {
        List<User> copy;
        synchronized (this) {
            copy = new ArrayList<>(watchers.values());
        }
        for (User u : copy) {
            try {
                u.sendMessage(m);
            } catch (Exception e) {
                synchronized (this) {
                    watchers.remove(u.getId());
                }
            }
        }
    }

    private synchronized void broadcastLineup() {
        Message m = lineupMessage();
        if (m != null) broadcast(m);
    }

    private Message lineupMessage() {
        try {
            Message m = new Message(1);
            DataOutputStream ds = m.writer();
            ds.writeByte(0);
            for (int p = 0; p < 6; p++) {
                ds.writeByte(p);
                ds.writeByte(rate[p]);
                ds.writeShort(PET_IMG[p]);
                ds.writeShort(PET_IMG[p]);
            }
            ds.writeShort(secondsLeft());
            ds.flush();
            return m;
        } catch (IOException e) {
            return null;
        }
    }

    private int secondsLeft() {
        long left = (phaseEndMs - System.currentTimeMillis()) / 1000;
        return (int) Math.max(0, left);
    }

    // ------------------------------------------------------------------
    // Các lệnh từ client (qua RaceMsgHandler)
    // ------------------------------------------------------------------

    /** Gói 1: xin trạng thái — trả đúng pha hiện tại. */
    public synchronized void onRequestState(User u) throws IOException {
        addWatcher(u);
        if (phase == 0) {
            Message m = lineupMessage();
            if (m != null) u.sendMessage(m);
            sendPools(u);
        } else if (phase == 1) {
            Message m = new Message(1);
            DataOutputStream ds = m.writer();
            ds.writeByte(1);
            ds.writeBoolean(false);
            for (int p = 0; p < 6; p++) {
                ds.writeByte(p);
                ds.writeShort(PET_IMG[p]);
                ds.writeByte(segNum[p].length);
                for (int i = 0; i < segNum[p].length; i++) {
                    ds.writeShort(segNum[p][i]);
                    ds.writeShort(segV[p][i]);
                }
            }
            ds.writeShort(secondsLeft());
            ds.writeShort((int) Math.ceil(raceTotalTicks / 20.0));
            ds.flush();
            u.sendMessage(m);
            sendPools(u);
        } else {
            // Đang nghỉ giữa hai chặng: gửi đội hình chặng vừa xong kèm
            // timeRemain = giây tới chặng mới (client hiện đếm ngược).
            Message m = lineupMessage();
            if (m != null) u.sendMessage(m);
        }
    }

    private void sendPools(User u) throws IOException {
        for (int p = 0; p < 6; p++) {
            if (pool[p] <= 0) continue;
            Message m = new Message(5);
            DataOutputStream ds = m.writer();
            ds.writeByte(p);
            ds.writeInt(pool[p]);
            ds.flush();
            u.sendMessage(m);
        }
    }

    /** Gói 2: thông tin một thú. */
    public synchronized void onPetInfo(User u, int petId) throws IOException {
        if (petId < 0 || petId >= 6) return;
        Message m = new Message(2);
        DataOutputStream ds = m.writer();
        ds.writeShort(PET_IMG[petId]);
        ds.writeUTF(PET_NAME[petId]);
        int winPct = petRaces[petId] > 0 ? petWins[petId] * 100 / petRaces[petId] : 0;
        ds.writeShort(winPct);
        ds.writeByte(rate[petId]);
        ds.writeByte(form[petId]);
        ds.writeByte(health[petId]);
        ds.flush();
        u.sendMessage(m);
    }

    /** Gói 5: đặt cược. Trả true nếu đặt thành công. */
    public synchronized boolean onBet(User u, int petId, int money) {
        if (phase != 0 || petId < 0 || petId >= 6) return false;
        if (money < 100 || u.getXu() < money) return false;
        u.updateXu(-money);
        u.getAvatarService().updateMoney(0);
        int[] mine = stakes.computeIfAbsent(u.getId(), k -> new int[6]);
        mine[petId] += money;
        pool[petId] += money;
        Message m = new Message(5);
        try {
            DataOutputStream ds = m.writer();
            ds.writeByte(petId);
            ds.writeInt(pool[petId]);
            ds.flush();
        } catch (IOException ignored) {
        }
        broadcast(m);
        return true;
    }

    /** Gói 8: lịch sử 10 chặng gần nhất. */
    public synchronized void onHistory(User u) throws IOException {
        Message m = new Message(8);
        DataOutputStream ds = m.writer();
        ds.writeByte(histIcon.size());
        for (int i = 0; i < histIcon.size(); i++) {
            ds.writeShort(histIcon.get(i)[0]);
            ds.writeUTF(histTime.get(i));
        }
        ds.flush();
        u.sendMessage(m);
    }

    /** Gói 9: chat trong trường đua — phát cho mọi người xem. */
    public void onChat(User u, String text) {
        if (text == null || text.isEmpty()) return;
        Message m = new Message(9);
        try {
            DataOutputStream ds = m.writer();
            ds.writeUTF(text);
            ds.flush();
        } catch (IOException ignored) {
        }
        broadcast(m);
    }
}
