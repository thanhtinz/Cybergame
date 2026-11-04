package avatar.model;

import java.util.ArrayList;
import java.util.List;

public class BoardInfo
{
    public byte boardID;// id

    public byte nPlayer;//số người

    public byte maxPlayer;//max người

    public boolean isPass;

    public boolean isPlaying;

    public int money;

    public String strMoney;

    public List<User> lstUsers;

    // ===== Trạng thái bàn Bầu Cua (bản port viết lại luồng casino) =====
    /** Ghế cố định 0..4: ghế KHÔNG đổi khi có người rời/ vào — client
     *  gốc đánh số ghế theo thứ tự gói JOIN_BOARD nên ghế phải ổn định. */
    public final User[] seats = new User[5];
    /** Pha ván: 0 rảnh · 1 đang đặt cược · 2 đang tả · 3 chờ công bố. */
    public int phase = 0;
    /** Tăng mỗi lần đổi pha — hẹn giờ của pha cũ tự vô hiệu. */
    public int phaseGen = 0;
    /** Ghế đang được tả (theo số ghế), -1 = không ai. */
    public int turnSeat = -1;
    /** Mốc hết giờ của pha hiện tại (ms). */
    public long phaseDeadline = 0;
    /** Mật khẩu bàn (rỗng = không khoá). */
    public String password = "";
    /** IDDB các người chơi đang ở trạng thái sẵn sàng. */
    public final java.util.Set<Integer> readyIds = new java.util.HashSet<>();
    /** Ảnh chụp ghế lúc bắt đầu ván (theo ghế 0..4), null khi rảnh. */
    public User[] seatSnap = null;
    /** Tiền đặt theo ghế ảnh chụp [ghế][6 ô], đơn vị = số lần chạm. */
    public byte[][] seatBets = new byte[5][6];
    /** Delta xu từng ghế của ván đang/chờ công bố. */
    public int[] seatDeltas = new int[5];

    public BoardInfo() {
        lstUsers = new ArrayList<>(); // Khởi tạo danh sách người dùng
    }

    /** Vào bàn ở ghế trống thấp nhất; trả số ghế hoặc -1 khi đầy. */
    public synchronized int addUser(User u) {
        if (seatOf(u) >= 0) return seatOf(u);
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                seats[i] = u;
                syncUserList();
                return i;
            }
        }
        return -1;
    }

    /** Rời bàn: giải phóng ghế. Trả true nếu người chơi có ở bàn. */
    public synchronized boolean removeUser(User u) {
        int s = seatOf(u);
        if (s < 0) return false;
        seats[s] = null;
        readyIds.remove(u.getId());
        syncUserList();
        return true;
    }

    public synchronized int seatOf(User u) {
        if (u == null) return -1;
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].getId() == u.getId()) return i;
        }
        return -1;
    }

    public synchronized int presentCount() {
        int n = 0;
        for (User s : seats) if (s != null) n++;
        return n;
    }

    /** Chủ bàn = người ở ghế 0. */
    public synchronized User owner() {
        return seats[0];
    }

    private void syncUserList() {
        lstUsers.clear();
        for (User s : seats) if (s != null) lstUsers.add(s);
        nPlayer = (byte) lstUsers.size();
    }

    public List<User> getLstUsers() {
        return lstUsers;
    }


    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    public synchronized void setPlaying(boolean isPlaying) {
        this.isPlaying = isPlaying;
    }

    public synchronized void setMoney(int Money) {
        this.money = Money;
    }

    public synchronized int getMoney() {
        return money;
    }



    // Phương thức để cập nhật danh sách người chơi
    public synchronized void setLstUsers(List<User> lstUsers) {
        this.lstUsers = lstUsers;
        this.nPlayer = (byte)lstUsers.size(); // Cập nhật số người chơi
    }
}
