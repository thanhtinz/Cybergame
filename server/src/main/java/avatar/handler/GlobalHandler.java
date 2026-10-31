package avatar.handler;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import avatar.db.DbManager;
import avatar.item.Item;
import avatar.minigame.TaiXiu;
import avatar.model.GiftCodeService;
import avatar.model.Menu;
import avatar.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Random;
import java.io.IOException;
import java.io.DataOutputStream;
import java.util.List;


import avatar.network.Message;
import avatar.server.Avatar;
import avatar.server.ServerManager;
import avatar.server.UserManager;
import avatar.service.EffectService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


public class GlobalHandler {
    private User us;
    private List<User> lst;
    public GlobalHandler(User user) {

        this.us = user;
        this.lst = UserManager.users;
    }

    public void handleOptionMenu(Message ms) throws IOException {
        int userId = ms.reader().readInt();
        byte menuId = ms.reader().readByte();
        byte select = ms.reader().readByte();
        System.out.println("userId = " + userId + ", menuId = " + menuId + ", select = " + select);
        menuOptionHandle(userId, menuId, select);
        if (userId >= 2000000000 || userId == 1) {
            //NpcHandler.handlerAction(this.us, userId, menuId, select);
            return;
        } else{
            switch (userId) {
                case 5:
                    switch (menuId) {
                        case 0:
                            switch (select) {
                                case 0:
                                    sendCityMap();
                                    break;
                                case 1:
                                    sendCityMap();
                                    break;
                                case 2:
                                    sendCityMap();
                                    break;
                            }
                            break;
                    }
                    break;
                case 1000:
                    switch (menuId) {
                        case 1: {
                            if (select != 2) {
                                this.us.incrementIntSpanboss(); // Tăng spam lên 1
                                if (this.us.getIntSpanboss() >= 500) { // Giả sử MAX_SPAM là 10
                                    this.us.resetUser();
                                    UserManager.getInstance().find(this.us.getId()).session.close();
                                }
                                return;
                            }
                            switch (select) {
                                case 0:
                                    break;
                                case 1:
                                    break;
                                case 2:
                                    Random random = new Random();
                                    int number1 = 1 + random.nextInt(10);
                                    int number2 = 1 + random.nextInt(10);
                                    this.us.setcorrectAnswer(number1 + number2);
                                    String equation = number1 + " + " + number2 + " =  ?";
                                    this.us.getAvatarService().sendTextBoxPopup(this.us.getId(), 102, equation, 1);
                                    break;
                                case 3:

                                    break;
                            }
                            break;
                        }
                        case 2:{
                            if(select == 1){

                            }
                        }
                    }
                    break;
            }
        }
    }

    private void menuOptionHandle(int npcId, byte menuId, byte select) {
        List<Menu> menus = us.getMenus();
        if (menus != null && select < menus.size()) {
            Menu menu = menus.get(select);
            if (menu.isMenu()) {
                us.setMenus(menu.getMenus());
                us.getAvatarService().openUIMenu(npcId, menuId + 1, menu.getMenus(), menu.getNpcName(), menu.getNpcChat());
            } else if (menu.getAction() != null) {
                menu.perform();
            } else {
                switch (menu.getId()) {

                }
            }
            return;
        }
    }


    private void acceptHenHo(){
        String insertQuery = "INSERT INTO marry (idNam, idNu, level, perLevel) VALUES (?, ?, ?, ?)";
        int idNvNam,idNu;
        if(this.us.getGender() == 1){
            idNvNam = this.us.getId();
            idNu = this.us.getIdUsHenHo();
        }else {
            idNvNam = this.us.getIdUsHenHo();
            idNu = this.us.getId();
        }
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(insertQuery)) {
            ps.setInt(1, idNvNam);
                                ps.setInt(2, idNu);
                                ps.setInt(3, 0);
                                ps.setInt(4, 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void handleTextBox(Message ms) throws IOException {
        int userId = ms.reader().readInt();
        byte menuId = ms.reader().readByte();
        String text = ms.reader().readUTF();

        switch (menuId) {
            case 100:
                String nameU = text;
                if(nameU.equals(us.getUsername())){
                    this.us.getAvatarService().serverDialog("không chơi tự sướng nha b");
                    return;
                }
                int idNvNam, idNu;
                if (this.us.getGender() == 1) {
                    idNvNam = this.us.getId();
                    idNu = this.us.getIdUsHenHo();
                } else {
                    idNvNam = this.us.getIdUsHenHo();
                    idNu = this.us.getId();
                }
                    String checkExistQuery = "SELECT COUNT(*) FROM marry WHERE idNam = ? OR idNu = ?";
                    try (Connection conn = DbManager.getInstance().getConnection()) {
                        // Kiểm tra xem cặp idNam và idNu có tồn tại hay không
                        try (PreparedStatement psCheck = conn.prepareStatement(checkExistQuery)) {
                            psCheck.setInt(1, idNvNam);
                            psCheck.setInt(2, idNu);
                            try (ResultSet rs = psCheck.executeQuery()) {
                                if (rs.next() && rs.getInt(1) == 0) {
                                    UserManager.users.forEach(user -> {
                                        if(user.getUsername().equals(nameU)){
                                            this.us.getAvatarService().serverDialog("ok gửi lời mới hẹn hò tới " + nameU);
                                            this.us.setIdUsHenHo(user.getId());
                                            user.setIdUsHenHo(this.us.getId());
                                            user.getAvatarService().sendTextBoxPopup(user.getId(), 101, "Bạn có muốn hẹn hò với "+us.getUsername() +" không ? nếu muốn thì trả lời ok hoặc yes", 1);
                                        }
                                    });
                                } else {
                                    us.getAvatarService().serverDialog("đã hẹn hò hoặc kết hôn rồi");
                                }
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }


                break;
            case 101:
                try {
                    if(text.equals("ok")||text.equals("yes")){
                        acceptHenHo();
                        UserManager.users.forEach(user -> {
                            if(user.getId() == us.getIdUsHenHo()){
                                user.getAvatarService().serverDialog("Bạn đã hẹn hò thành công với " + us.getUsername());
                                us.getAvatarService().serverDialog("Bạn đã hẹn hò thành công với "+ user.getUsername());

                                user.setWearingMarry(us.getWearing());
                                user.setNamehh(us.getUsername());
                                us.setWearingMarry(user.getWearing());
                                us.setNamehh(user.getUsername());
                            }
                        });
                    }else{

                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                break;
            case 0: // Cược Tài (Xu)
            case 1: // Cược Xỉu (Xu)
            case 2: // Cược Tài (Lượng)
            case 3: // Cược Xỉu (Lượng)
                TaiXiu.getInstance().handleBetWithInput(this.us, menuId, userId, text);
                break;
            case 102:
                try {
                    int userAnswer = Integer.parseInt(text);
                    if (userAnswer == this.us.getcorrectAnswer()) {
                        this.us.getAvatarService().serverDialog("Chúc mừng! Bạn đã trả lời đúng.");
                        this.us.setspamclickBoss(false);
                        this.us.resetUser();
                    } else {
                        this.us.getAvatarService().serverDialog("Sai rồi! Kết quả đúng là: " + this.us.getcorrectAnswer());
                    }
                } catch (NumberFormatException e) {
                    this.us.getAvatarService().serverDialog("Vui lòng nhập một số hợp lệ.");
                }
                break;
            case 20:
                GiftCodeService giftCodeService = new GiftCodeService();
                giftCodeService.useGiftCode(this.us.getId(), text);
                break;
            case 98:
                if (Integer.parseInt(text) == 1) {
                    UserManager.users.forEach(user -> {
                        user.getAvatarService().serverInfo((String.format("ad : bảo trì sau 2p vui lòng off để tránh mất item")));
                        user.getAvatarService().serverDialog("bảo trì sau 2p vui lòng off : v");
                    });
                }
                break;
            case 99:
                String[] parts = text.split(" ");
                byte value1 = Byte.parseByte(parts[0]);
                short value2 = Short.parseShort(parts[1]);
                us.getZone().getPlayers().forEach(u -> {
                    EffectService.createEffect()
                            .session(u.session)
                            .id((byte) value1)
                            .style((byte) 0)
                            .loopLimit((byte) 5)
                            .loop((short) value2)
                            .loopType((byte) 1)
                            .radius((short) 20)
                            .idPlayer(us.getId())
                            .send();
                });
                break;
            case 7:
                try {
                    // Tách id và username
                    String[] idAndName = text.split(" ");  // Tách phần trước và sau dấu cách
                    String idPart = idAndName[0];          // Phần chứa id
                    String usernamePart = idAndName[1];    // Phần chứa username
                    // Chuyển đổi id từ chuỗi sang số ngắn (short)
                    short idItem = Short.parseShort(idPart);
                    // In kết quả
                    System.out.println("ID: " + idItem);
                    System.out.println("Username: " + usernamePart);

                    if((short) idItem>0 && (short) idItem<9999){
                        Item item = new Item(idItem, -1, -0);
                        for (int i = 0; i < lst.stream().count(); i++) {
                            if(lst.get(i).getUsername().equals(usernamePart)){
                                lst.get(i).addItemToChests(item);
                                String content = "admin : Bạn được tặng item " + item.getPart().getName();
                                lst.get(i).getAvatarService().SendTabmsg(content);
                                lst.get(i).getAvatarService().serverDialog("added " + item.getPart().getName() + " into chests");
                                lst.get(i).getAvatarService().serverInfo(content);
                                us.getAvatarService().serverDialog("added " + item.getPart().getName() + " into my chests");
                            }
                        }
                    }else{
                        us.getAvatarService().serverDialog("id lớn hơn 2000 và nhỏ hơn 6795");
                    }
                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
                break;
            case 8:
                try {
                    if (us.getId() == 7) {
                        String noidung = text.toString();
                        for (int i = 0; i < lst.stream().count(); i++) {
                            lst.get(i).getAvatarService().serverInfo(noidung);
                        }
                    }

                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
                break;
            case 9:
                try {
                    if (us.getId() == 7) {
                        byte weather = Byte.parseByte(text);
                        us.getAvatarService().weather(weather);
                    }

                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
                break;
            case 10:
                try {
                    if (us.getId() == 7) {

                        //save data account
                        List<Integer> ids = new ArrayList<>();

                        for (User us : lst) {
                            ids.add(us.getId());
                        }
                        for (Integer id : ids) {
                            try
                            {
                                UserManager.getInstance().find(id).session.close();

                            }catch (Exception e) {

                            }
                        }
                    }

                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
                break;
            case 11:
                try {
                    if (us.getId() == 7) {
                        if (Integer.parseInt(text) == 1) {
                            UserManager.users.forEach(user -> {
                                user.getAvatarService().serverInfo((String.format("ad : thành phố  %s. có %d  đang online. chúc mọi người vui vẻ", ServerManager.cityName, ServerManager.clients.size())));
                            });
                        }
                    }

                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
                break;
            case 12:
                try {
                    if (us.getId() == 7) {
                        if (Integer.parseInt(text) == 1) {
                            for (int i = 0; i < lst.stream().count(); i++) {
                                lst.get(i).getAvatarService().serverInfo((String.format("ad : thành phố  %s. có %d  đang online. chúc mọi người vui vẻ", ServerManager.cityName, ServerManager.clients.size())));
                            }
                        }
                    }

                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
            case 13:
                try {
                    if (us.getId() == 7) {
                        if (Integer.parseInt(text) == 1) {
                            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                            int threadCount = threadMXBean.getThreadCount();
                            System.out.println("Number of threads: " + threadCount);
                            us.getAvatarService().serverDialog("theard = "+threadCount);
                        }
                    }

                } catch (NumberFormatException e) {
                    us.getAvatarService().serverDialog("invalid input, item code must be number");
                }
                break;
        }

    }

    private void sendCityMap() throws IOException {
        String folder = "res/map/";
        byte[] data = Avatar.getFile(folder + "cityMap.dat");
        byte[] image = Avatar.getFile(folder + "cityMap.png");
        byte[] map27 = Avatar.getFile(folder + "27.dat");
        byte[] map_bg = Avatar.getFile(folder + "bg/27.png");

        Message ms = new Message(-92);
        DataOutputStream ds = ms.writer();
        ds.writeByte(1);
        ds.writeInt(image.length);
        ds.write(image);

        ds.writeInt(data.length);
        ds.writeByte(34);
        ds.write(data);

        short[] idImg = new short[]{821, 827, 850};
        String[] doorName = new String[]{"Sân Bay", "Bãi Biển", "Trung Tâm Giải Trí"};
        byte[] x = new byte[]{23, 9, 21};
        byte[] y = new byte[]{7, 13, 18};


        ds.writeByte(idImg.length);
        int i = 0;
        while (i < idImg.length) {
            ds.writeByte(i);
            ds.writeShort(idImg[i]);
            ds.writeUTF(doorName[i]);
            ds.writeByte(x[i]);
            ds.writeByte(y[i]);
            ++i;
        }
        ds.flush();
        this.us.sendMessage(ms);


        ms = new Message(-93);
        ds = ms.writer();

        ds.writeByte(27);
        ds.writeByte(1);
        ds.writeShort(306);
        ds.writeByte(34);
        ds.writeShort(map27.length);
        ds.write(map27);

        short[] arr = {828, -1, 835, 853, 852, 836, 832, 832, 851, 833, 837, 842, 843, -1, -1};
        ds.writeByte(arr.length);
        for (int j = 0; j < arr.length; j++) {
            ds.writeShort(arr[j]);
        }

        ds.writeShort(map_bg.length);
        ds.write(map_bg);

        try {
            Connection connection = DbManager.getInstance().getConnection();
            int mapId = 27;

            String GET_MAP_ITEM_TYPE = "SELECT * FROM `map_item_typem` WHERE `map_id` = ?";
            PreparedStatement ps = connection.prepareStatement(GET_MAP_ITEM_TYPE,
                    ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setInt(1, mapId);

            ResultSet res = ps.executeQuery();
            if (res != null) {
                ds.writeShort(204);
                res.last();
                int rows = res.getRow();
                ds.writeByte(rows);
                res.beforeFirst();

                while (res.next()) {
                    ds.writeByte(res.getByte("id_type"));
                    ds.writeShort(res.getShort("id_img"));
                    ds.writeByte(res.getByte("icon_id"));
                    ds.writeShort(res.getShort("dx"));
                    ds.writeShort(res.getShort("dy"));


                    JSONArray av_position = (JSONArray) JSONValue.parse(res.getString("av_position"));
                    ds.writeByte(av_position.size());
                    for (int m = 0; m < av_position.size(); m++) {
                        JSONObject av_position_element = (JSONObject) av_position.get(m);
                        ds.writeByte(((Long) av_position_element.get("x")).shortValue());
                        ds.writeByte(((Long) av_position_element.get("y")).shortValue());
                    }
                }
                byte[] mapItemA = {26, 27, 28, 29, 31, 64};
                byte[] mapItemB = {0, 0, 0, 0, 0, 4};
                byte[] mapItemC = {2, 7, 27, 20, 33, 15};
                byte[] mapItemD = {1, 1, 1, 1, 1, 0};


                ds.writeByte(mapItemA.length);
                for (int k = 0; k < mapItemA.length; k++) {
                    ds.writeByte(mapItemA[k]);
                    ds.writeByte(mapItemB[k]);
                    ds.writeByte(mapItemC[k]);
                    ds.writeByte(mapItemD[k]);
                }
            } else {
                ds.writeShort(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        ds.flush();
        this.us.sendMessage(ms);

    }
}