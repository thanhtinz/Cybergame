package avatar.message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import avatar.constants.NpcName;
import avatar.handler.NpcHandler;
import avatar.item.Item;
import avatar.message.minigame.BauCuaMsgHandler;
import avatar.model.BoardInfo;
import avatar.model.Menu;
import avatar.model.Npc;
import avatar.model.User;
import avatar.network.Message;
import avatar.network.Session;
import avatar.server.BoardManager;
import avatar.server.ServerManager;
import avatar.server.UserManager;
import avatar.server.Utils;
import avatar.service.EffectService;
import avatar.service.FarmService;
import avatar.constants.Cmd;

import static avatar.server.BoardManager.boardList;
import static avatar.server.BoardManager.users;

public class CasinoMsgHandler extends MessageHandler {
    private BauCuaMsgHandler service;

    public CasinoMsgHandler(Session client) {
        super(client);
        this.service = new BauCuaMsgHandler(client);

    }
    @Override

    public void onMessage(Message mss) {
        try {
            System.out.println("casino mess: " + mss.getCommand());
            switch (mss.getCommand()) {
                case 61:
                    service.joinCasino(mss);
                    break;
                case Cmd.REQUEST_ROOMLIST:
                    requestRoomList();
                    break;
                case Cmd.GET_IMG_ICON: {
                    if (this.client.user != null) {
                        this.client.doGetImgIcon(mss);
                        break;
                    }}
                case Cmd.REQUEST_BOARDLIST:
                    BoardList(mss);
                    break;
                case Cmd.JOIN_BOARD:
                    joinBoard(mss,this.client.user);
                    break;
                case Cmd.CHAT_TO_BOARD:
                    chatToBoard(mss);
                    break;
                case Cmd.LEAVE_BOARD:
                    leaveBoard(mss,this.client.user);
                    break;
                case Cmd.START:
                    Start(mss);
                    break;
                case Cmd.READY:
                    Ready(mss,this.client.user);
                    break;
                case Cmd.TO_XONG:
                    toXong(mss,this.client.user);
                    break;
                case 65:
                    haPhom(mss,this.client.user);
                    break;
                case 49:
                    Skip(mss,this.client.user);
                    break;
                case Cmd.SET_MONEY:
                    setMoney(mss,this.client.user);
                    break;
                default:
                    System.out.println("casino mess: " + mss.getCommand());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestRoomList() throws IOException {//ms 6
        Message ms = new Message(Cmd.REQUEST_ROOMLIST);
        DataOutputStream ds = ms.writer();

        for (int i = 0; i < 3; i++) {
            ds.writeByte(43+i);//id
            ds.writeByte(1);//roomfree//vàng 0 đỏ 2 xanh
            ds.writeByte(0+i);//roomWait
            ds.writeByte(0+i);//lv
        }
        ds.flush();
        this.client.user.sendMessage(ms);
    }

    private void BoardList(Message ms) throws IOException {//ms 7

        byte id = ms.reader().readByte();
        ms = new Message(Cmd.REQUEST_BOARDLIST);
        DataOutputStream ds = ms.writer();
        ds.writeByte(id);

        List<BoardInfo> boardInfos = BoardManager.getInstance().boardList;


        for(BoardInfo a : boardInfos)
        {
            ds.writeByte(a.boardID);
            ds.writeByte(a.nPlayer);
            if(a.isPass){ds.writeByte(1);}
            else{ds.writeByte(0);}
            ds.writeInt(a.getMoney());
        }
        ds.flush();

        this.client.user.sendMessage(ms);

    }


    private void chatToBoard(Message ms) throws IOException {//ms 7
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        String text = ms.reader().readUTF();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();

        ms = new Message(Cmd.CHAT_TO_BOARD);
        DataOutputStream ds = ms.writer();
        ds.writeByte(roomID);
        ds.writeByte(boardID);
        ds.writeByte(this.client.user.getId());//lv
        ds.writeUTF(text);
        ds.flush();

        for(User u : BoardUs)
        {
            u.sendMessage(ms);
        }
        //this.client.user.sendMessage(ms);

    }

    private void joinBoard(Message ms, User us) throws IOException {//ms 8
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        String pass = ms.reader().readUTF();
        if (this.client.isResourceHD())
        {
            this.client.user.getAvatarService().serverDialog("error 0011");
            return;
        }
        BoardInfo board = BoardManager.getInstance().find(boardID);

        BoardManager.getInstance().increaseMaxPlayer(boardID,roomID,us);
        List<User> BoardUs = board.getLstUsers();


        if(BoardUs.size() > 1)
        {
            for (int i = 0; i < BoardUs.size(); i++) {
                Message ms1 = new Message(Cmd.SOMEONE_JOINBOARD);
                DataOutputStream ds1 = ms1.writer();
                ds1.writeByte(BoardUs.indexOf(us));//seat // vi tri
                ds1.writeInt(us.getId());//seat // vi tri
                ds1.writeUTF(us.getUsername());//seat // vi tri
                ds1.writeInt(0);// tien

                ds1.writeByte(us.getWearing().size()); // Số phần mặc
                for (Item item : us.getWearing()) {
                    ds1.writeShort(item.getId()); // ID item
                }
                ds1.writeInt(0);// tien
                ds1.writeInt(1);// tien
                ds1.flush();
                BoardUs.get(i).session.sendMessage(ms1);

                for (int j = 1; j < BoardUs.size(); j++) {
                    ms = new Message(Cmd.READY);//16
                    DataOutputStream ds = ms.writer();

                    ds.writeInt(BoardUs.get(i).getId());
                    ds.writeBoolean(false);
                    ds.flush();

                    for(User u : BoardUs)
                    {
                        u.session.sendMessage(ms);
                    }
                }
            }
        }


        ms = new Message(Cmd.JOIN_BOARD);
        DataOutputStream ds = ms.writer();

        ds.writeByte(roomID);
        ds.writeByte(boardID);
        ds.writeInt(BoardUs.get(0).getId()); // ID user
        ds.writeInt(board.getMoney()); // số tiền cược ở phòng


        for (User user : BoardUs) {
            ds.writeInt(user.getId()); // IDDB
            ds.writeUTF(user.getUsername()); // Username
            ds.writeInt(us.getXeng()); // Số tiền của user
            ds.writeByte(user.getWearing().size()); // Số phần mặc
            for (Item item : user.getWearing()) {
                ds.writeShort(item.getId()); // ID item
            }

            ds.writeInt(10); // Kinh nghiệm
            ds.writeBoolean(false); // Trạng thái sẵn sàng
            ds.writeShort(user.getIdImg()); // ID hình ảnh
        }

        for (int i = BoardUs.size(); i < 5; i++) {
            ds.writeInt(-1); // IDDB placeholder for empty slots
        }

        ds.flush();
        this.client.user.sendMessage(ms);

        if(board.isPlaying()){
            Message ms2 = new Message(Cmd.PLAYING);
            DataOutputStream ds2 = ms2.writer();
            ds2.writeByte(roomID);
            ds2.writeByte(boardID);
            ds2.writeByte(10);
            ds2.flush();
            us.getSession().sendMessage(ms2);
            us.setToXong(true);
            us.setHaPhom(true);
        }
    }

    private void leaveBoard(Message ms, User us) throws IOException {
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        BoardInfo board = BoardManager.getInstance().find(boardID);

        List<User> BoardUs = board.getLstUsers();
        if(board.isPlaying() && BoardUs.size()>1)
        {
            us.setToXong(true);
            us.setHaPhom(true);
            for(User user : BoardUs) {
                if (!user.isHaPhom())
                {
                    System.out.println("Gửi Lượt Hạ Phỏm "+user.getUsername());
                    try {
                        setTurn(BoardUs,user,roomID,boardID,BoardUs.indexOf(user));
                        board.nPlayer--;
                        List<User> updatedBoardUs = new ArrayList<>(BoardUs);
                        updatedBoardUs.remove(us);
                        board.setLstUsers(updatedBoardUs);
                        BoardUs = updatedBoardUs;
                        ms = new Message(Cmd.SOMEONE_LEAVEBOARD);//14
                        DataOutputStream ds = ms.writer();
                        ds.writeInt(us.getId());
                        ds.writeInt(BoardUs.get(0).getId());
                        for (User user1 : board.getLstUsers()) {
                            user1.getSession().sendMessage(ms);
                        }

                        return;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            try {
                gameResult(roomID,boardID);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


        board.nPlayer--;
        List<User> updatedBoardUs = new ArrayList<>(BoardUs);
        updatedBoardUs.remove(us);
        board.setLstUsers(updatedBoardUs);
        BoardUs = updatedBoardUs;

        if(BoardUs.size() == 0)
        {
            return;
        }


        ms = new Message(Cmd.SOMEONE_LEAVEBOARD);//14
        DataOutputStream ds = ms.writer();
        ds.writeInt(us.getId());
        ds.writeInt(BoardUs.get(0).getId());
        for (User user : board.getLstUsers()) {
            user.getSession().sendMessage(ms);
        }

        if(board.isPlaying() && BoardUs.size()==1)
        {
            Message ms3 = new Message(Cmd.FINISH);
            DataOutputStream ds3 = ms3.writer();
            ds3.writeByte(roomID);
            ds3.writeByte(boardID);
            for (int i = 0; i < 5; i++)
            {
                ds3.writeInt(0);
            }
            ds3.flush();
            BoardUs.get(0).session.sendMessage(ms3);
        }

    }


    private void Ready(Message ms,User us) throws IOException {//ms 20
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        Boolean isReady = ms.reader().readBoolean();

        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();

        ms = new Message(Cmd.READY);//16
        DataOutputStream ds = ms.writer();

        ds.writeInt(us.getId());
        ds.writeBoolean(isReady);
        ds.flush();

        for(User u : BoardUs)
        {
            u.session.sendMessage(ms);
        }
    }


    private void Start(Message ms) throws IOException {//20
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();
        for(User u : BoardUs){
            List<Byte> moneyPutList = u.getMoneyPutList();
            moneyPutList.clear();
            u.setHaPhom(false);
            u.setToXong(false);
            u.getMoneyPutList().clear();
        }
        BoardUs.get(0).setToXong(true);
        BoardUs.get(0).setHaPhom(true);
        board.setPlaying(true);

        ms = new Message(Cmd.START);//20
        DataOutputStream ds = ms.writer();

        ds.writeByte(roomID);
        ds.writeByte(boardID);
        ds.writeByte(10); // ID user hoặc ID bàn
        ds.flush();

        for (User user : BoardUs) {
            user.session.sendMessage(ms);
        }
    }


    private void toXong(Message ms, User us) throws IOException { // ms 21
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();

        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();
        List<Byte> moneyPutList = us.getMoneyPutList();


        // Nếu chưa có putlist thì thêm putlist mới
        if (us.getMoneyPutList().size() <= 0 && BoardUs.indexOf(us) != 0) {
            while (ms.reader().available() > 0) {
                byte moneyPut = ms.reader().readByte();
                moneyPutList.add(moneyPut);
            }
            us.setMoneyPutList(moneyPutList);
            ms = new Message(Cmd.TO_XONG);
            DataOutputStream ds = ms.writer();
            ds.writeByte(roomID);
            ds.writeByte(boardID);
            ds.writeByte(BoardUs.indexOf(us));
            for (Byte moneyPut : moneyPutList) {
                ds.writeByte(moneyPut);
            }
            ds.flush();
            us.getSession().sendMessage(ms);
            us.setToXong(true);
            System.out.println(us.getUsername() + " đã đặt xong ");

            for (User user : BoardUs) {
                user.session.sendMessage(ms);
            }
        }

        // Kiểm tra xem tất cả người chơi đã "to xong" chưa
        Boolean allToXong = true;
        for (User user : BoardUs) {
            if (!user.isToXong()) {
                System.out.println(user.getUsername() + " chưa to xong ");
                allToXong = false;
                break; // Thoát khỏi vòng lặp nếu có một người chưa to xong
            }
        }

        if (allToXong) {
            for (User user : BoardUs) {
                if (!user.isHaPhom()) {
                    System.out.println("luot ta => "+user.getUsername());
                    try {
                        setTurn(BoardUs,user,roomID,boardID,BoardUs.indexOf(user));
                        return;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }



    private void haPhom(Message ms,User us) throws IOException, InterruptedException {//ms 65
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        byte indexFrom = ms.reader().readByte();
        byte indexTo = ms.reader().readByte();

        System.out.println("indexFrom: " + indexFrom);
        System.out.println("indexTo: " + indexTo);

        BoardInfo board1 = BoardManager.getInstance().find(boardID);
        List<User> boardUsers = board1.getLstUsers();

        // Lấy danh sách tiền đã đặt của người chơi hiện tại (us)
        List<Byte> userMoneyPutList = us.getMoneyPutList();
        System.out.println("Initial Money Put List for " + us.getUsername() + ": " + userMoneyPutList);

        // Tổng số tiền cược từ indexFrom đến indexTo
        int totalSum = 0;

        // Cộng dồn tổng tiền từ indexFrom đến indexTo
        if (indexFrom <= indexTo) {
            for (int i = indexFrom; i <= indexTo; i++) {
                totalSum += userMoneyPutList.get(i);
            }
        } else {
            for (int i = indexFrom; i >= indexTo; i--) {
                totalSum += userMoneyPutList.get(i);
            }
        }

        System.out.println("Total Sum from indexFrom (" + indexFrom + ") to indexTo (" + indexTo + "): " + totalSum);

        // Cập nhật danh sách putMoneyList của người chơi us tại indexTo
        userMoneyPutList.set(indexTo, (byte) (userMoneyPutList.get(indexTo) + totalSum));

        // Đặt lại tiền ở indexFrom về 0, giữ nguyên tiền ở các vị trí khác
        userMoneyPutList.set(indexFrom, (byte) 0);

        System.out.println("Updated Money Put List for " + us.getUsername() + ": " + userMoneyPutList);

        ms = new Message(Cmd.HA_PHOM); // 65
        DataOutputStream ds = ms.writer();
        ds.writeByte(roomID);
        ds.writeByte(boardID);

        ds.writeByte(boardUsers.indexOf(us)); // Ghi lại chỉ số của người chơi hiện tại
        ds.writeByte(indexFrom); // Ghi giá trị indexFrom
        ds.writeByte(indexTo);
        ds.writeByte(userMoneyPutList.get(indexTo));
        ds.flush();

        System.out.println(us.getUsername()+"đã Hạ Phỏm thành công ");
        for (User user : boardUsers) {
            user.getSession().sendMessage(ms);
        }

        for(User user : boardUsers) {
            if (!user.isHaPhom())
            {
                System.out.println("Gửi Lượt Hạ Phỏm "+user.getUsername());
                try {
                    setTurn(boardUsers,user,roomID,boardID,boardUsers.indexOf(user));
                    return;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        gameResult(roomID,boardID);
    }


    private void Skip(Message ms,User us) throws IOException, InterruptedException {//ms 6

        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        //us.getService().serverDialog("skip dang xay dung");
        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();
        us.setHaPhom(true);
        ms = new Message(Cmd.HA_PHOM); // 65
        DataOutputStream ds = ms.writer();
        ds.writeByte(roomID);
        ds.writeByte(boardID);

        for(User user : BoardUs) {
            if (!user.isHaPhom())
            {
                System.out.println("Gửi Lượt Hạ Phỏm "+user.getUsername());
                try {
                    setTurn(BoardUs,user,roomID,boardID,BoardUs.indexOf(user));
                    return;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        gameResult(roomID,boardID);
    }



    private void setTurn(List<User> lstus,User us,byte roomID,byte boardID,int index) throws IOException, InterruptedException {
        Message ms1 = new Message(Cmd.SET_TURN);
        DataOutputStream ds1 = ms1.writer();
        ds1.writeByte(roomID);
        ds1.writeByte(boardID);
        ds1.writeByte(index);
        ds1.flush();
        us.getSession().sendMessage(ms1);
        us.setHaPhom(true);
        System.out.println("Người chơi "+us.getUsername()+" đang hạ phỏm");
        for (User user1 : lstus) {
            user1.session.sendMessage(ms1);
        }
    }


    private void gameResult(byte roomID,byte boardID) throws IOException, InterruptedException {

        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();
        System.out.println("game resutl");
        Message ms1 = new Message(Cmd.GAME_RESULT);

        DataOutputStream ds1 = ms1.writer();
        ds1.writeByte(roomID);
        ds1.writeByte(boardID);

        for (int i = 0; i < 3; i++) {
            int xn = Utils.nextInt(5);
            ds1.writeByte(xn);
        }

        ds1.flush();
        for (User user : BoardUs) {
            user.getSession().sendMessage(ms1);
        }

        Thread.sleep(2500);
        Message ms2 = new Message(Cmd.WIN);
        DataOutputStream ds2 = ms2.writer();
        ds2.writeByte(roomID);
        ds2.writeByte(boardID);
        for (User user : BoardUs) {
            ds2.writeByte(BoardUs.indexOf(user));
            ds2.writeByte(1);
            ds2.writeByte(999);//money
            ds2.flush();
            user.getSession().sendMessage(ms2);
        }

        Message ms3 = new Message(Cmd.FINISH);
        DataOutputStream ds3 = ms3.writer();
        ds3.writeByte(roomID);
        ds3.writeByte(boardID);
        for (int i = 0; i < 5; i++)
        {
            ds3.writeInt(999);
        }
        ds3.flush();
        for (User user : BoardUs) {
            user.getSession().sendMessage(ms3);
        }

        board.setPlaying(false);
    }



    private void setMoney(Message ms,User us) throws IOException {
        byte roomID = ms.reader().readByte();
        byte boardID = ms.reader().readByte();
        BoardInfo board = BoardManager.getInstance().find(boardID);
        List<User> BoardUs = board.getLstUsers();

        int Money = ms.reader().readInt();
        ms = new Message(Cmd.SET_MONEY);

        if (us.getXeng() < Money*48)
        {
            int xengValue = us.getXeng();
            int result = xengValue / 50;
            us.getAvatarService().serverDialog("Để đặt được "+Money+" thì bạn cần có số tiền là "+ Money*48);
            return;
        }
        if (Money > 100000)
        {
            us.getAvatarService().serverDialog("Vui lòng đặt nhỏ hơn 100.000");
            return;
        }
        for (User user : BoardUs) {
            board.setMoney(Money);
            DataOutputStream ds = ms.writer();
            ds.writeByte(roomID);
            ds.writeByte(boardID);
            ds.writeInt(Money);
            user.getSession().sendMessage(ms);
        }

//        byte id = ms.reader().readByte();
//        ms = new Message(Cmd.REQUEST_BOARDLIST);
//        DataOutputStream ds = ms.writer();
//        ds.writeByte(id);
//
//        List<BoardInfo> boardInfos = BoardManager.getInstance().boardList;
//
//
//        for(BoardInfo a : boardInfos)
//        {
//            ds.writeByte(a.boardID);
//            ds.writeByte(a.nPlayer);
//            if(a.isPass){ds.writeByte(1);}
//            else{ds.writeByte(0);}
//            ds.writeInt(0);
//        }
//        ds.flush();
//
//        this.client.user.sendMessage(ms);
//

    }


}
