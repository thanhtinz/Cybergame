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
                    boolean userExists = false;
                    for (User existingUser : board.lstUsers) {
                        if (existingUser.getId() == user.getId()) {
                            userExists = true;
                            break;
                        }
                    }
                    if (!userExists) {
                        board.nPlayer += 1;
                        board.lstUsers.add(user);
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
            board.nPlayer = 80;//số ng chia 16
            board.maxPlayer = 5; // Đặt maxPlayer là 5
            board.isPass = false;
            board.isPlaying = false;
            board.money = 0;
            board.strMoney = "1000";
            boardList.add(board);
            System.out.println("create board : " + board.boardID);
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
