package avatar.server;
import avatar.message.CasinoMsgHandler;
import avatar.model.BoardInfo;
import avatar.model.User;

import java.util.*;


public class BoardManager {

    private static final BoardManager instance = new BoardManager();

    public static BoardManager getInstance() {
        return instance;
    }

    public static final List<BoardInfo> boardList = new ArrayList<>();

    public static final List<User> users = new LinkedList<>();

    public BoardManager() {
    }


    public BoardInfo find(byte id) {
        synchronized (this) {
            for (BoardInfo board : boardList) {
                if (board.boardID == id) {
                    return board;
                }
            }
        }
        return null; // Trả về null nếu không tìm thấy
    }


    public void increaseMaxPlayer(int id,byte roomID, User user) {
        synchronized (this) {
            for (BoardInfo board : boardList) {
                if (board.boardID == id) {
                    if (board.seatOf(user) < 0 && board.addUser(user) >= 0) {
                        user.setRoomID(roomID);
                    }
                }
            }
        }
    }

    public void remove(User us) {
        synchronized(users) {
            users.remove(us);
        }
    }
    public void initBoards() {
        for (int i = 0; i < 6; i++) {
            BoardInfo board = new BoardInfo();
            board.boardID = (byte) i;
            board.nPlayer = 0;
            board.maxPlayer = 5; // Đặt maxPlayer là 5
            board.isPass = false;
            board.isPlaying = false;
            board.money = 0;
            board.strMoney = "1000";
            boardList.add(board);
            System.out.println("create board : " + board.boardID);
        }
        // Bàn Tiến Lên (gameType 3): id 30–35, 4 ghế [PORT].
        for (int i = 0; i < 6; i++) {
            BoardInfo board = new BoardInfo();
            board.boardID = (byte) (30 + i);
            board.gameType = 3;
            board.seatCap = 4;
            board.nPlayer = 0;
            board.maxPlayer = 4;
            board.isPass = false;
            board.isPlaying = false;
            board.money = 0;
            board.strMoney = "1000";
            boardList.add(board);
            System.out.println("create board TL : " + board.boardID);
        }
        // Bàn Phỏm (gameType 7): id 40–45, 4 ghế [PORT].
        for (int i = 0; i < 6; i++) {
            BoardInfo board = new BoardInfo();
            board.boardID = (byte) (40 + i);
            board.gameType = 7;
            board.seatCap = 4;
            board.nPlayer = 0;
            board.maxPlayer = 4;
            board.isPass = false;
            board.isPlaying = false;
            board.money = 0;
            board.strMoney = "1000";
            boardList.add(board);
            System.out.println("create board Phom : " + board.boardID);
        }
        // Bàn Diamond (gameType 21): id 50–55, 2 ghế [PORT].
        for (int i = 0; i < 6; i++) {
            BoardInfo board = new BoardInfo();
            board.boardID = (byte) (50 + i);
            board.gameType = 21;
            board.seatCap = 2;
            board.nPlayer = 0;
            board.maxPlayer = 2;
            board.isPass = false;
            board.isPlaying = false;
            board.money = 0;
            board.strMoney = "1000";
            boardList.add(board);
            System.out.println("create board Diamond : " + board.boardID);
        }
    }

    /** [PORT] Tạo bàn mới, id chạy tiếp sau bàn lớn nhất hiện có.
     *  Tối đa 60 bàn tất cả; quá thì trả null. */
    public BoardInfo createBoard() {
        return createBoard(22);
    }

    public BoardInfo createBoard(int gameType) {
        synchronized (this) {
            if (boardList.size() >= 60) {
                return null;
            }
            int next = 0;
            for (BoardInfo b : boardList) {
                next = Math.max(next, b.boardID + 1);
            }
            BoardInfo board = new BoardInfo();
            board.boardID = (byte) next;
            board.gameType = gameType;
            board.dynamic = true;
            board.seatCap = gameType == 21 ? 2 : (gameType == 3 || gameType == 7) ? 4 : 5;
            board.nPlayer = 0;
            board.maxPlayer = (byte) board.seatCap;
            board.isPass = false;
            board.isPlaying = false;
            board.money = 0;
            board.strMoney = "1000";
            boardList.add(board);
            System.out.println("create board : " + board.boardID);
            return board;
        }
    }

    /** [PORT] Bàn tự tạo đã trống thì dọn khỏi danh sách. */
    public void cleanupEmptyDynamic(BoardInfo board) {
        synchronized (this) {
            if (board != null && board.dynamic && board.presentCount() == 0) {
                boardList.remove(board);
            }
        }
    }

    public BoardInfo findUserBoard(User user) {
        synchronized (this) {
            for (BoardInfo board : boardList) {
                if (board.lstUsers.contains(user)) {
                    return board; // Trả về bàn nếu người dùng đang tham gia
                }
            }
        }
        return null; // Trả về null nếu người dùng không tham gia bàn nào
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // Kiểm tra nếu đối tượng so sánh chính là đối tượng hiện tại
        if (o == null || getClass() != o.getClass()) return false; // Kiểm tra nếu đối tượng null hoặc khác lớp
        User user = (User) o; // Ép kiểu Object sang User
        return user.getId() == user.getId(); // So sánh ID của hai đối tượng
    }

}
