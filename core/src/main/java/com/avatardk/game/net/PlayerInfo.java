package com.avatardk.game.net;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Thông tin nhân vật giải từ gói LOGIN_SUCESS (-4) của server —
 * thứ tự trường đúng AvatarService.onLoginSuccess của gốc
 * (docs/protocol-spec.md mục 4.4).
 */
public class PlayerInfo {
    public int id;
    public int[] wearing = new int[0];
    public int gender;
    public int levelMain;
    public int levelPercent;
    public int xu;
    public int luong;
    public int luongKhoa;
    public int xeng;
    public String username = "";

    public static PlayerInfo parse(Msg m) throws IOException {
        DataInputStream r = m.reader();
        PlayerInfo p = new PlayerInfo();
        p.id = r.readInt();
        int nParts = r.readByte();
        p.wearing = new int[nParts];
        for (int i = 0; i < nParts; i++) p.wearing[i] = r.readShort();
        p.gender = r.readByte();
        p.levelMain = r.readByte();
        p.levelPercent = r.readByte();
        p.xu = r.readInt();
        r.readByte(); // friendly
        r.readByte(); // crazy (gốc ghi cứng 10)
        r.readByte(); // stylish (100)
        r.readByte(); // happy (100)
        r.readByte(); // 100 - hunger
        p.luong = r.readInt();
        r.readByte(); // star
        for (int i = 0; i < nParts; i++) {
            r.readByte();
            r.readUTF(); // hạn dùng của part
        }
        r.readShort(); // icon clan (-1 nếu chưa có)
        int nCmd = r.readByte();
        for (int i = 0; i < nCmd; i++) {
            r.readUTF();
            r.readShort();
        }
        int nRotate = r.readByte();
        for (int i = 0; i < nRotate; i++) {
            r.readShort();
            r.readUTF();
            r.readShort();
        }
        r.readBoolean(); // isTour
        for (int i = 0; i < nRotate; i++) r.readByte();
        r.readByte(); // hằng 1
        r.readShort(); // level chính (lần 2)
        r.readShort(); // mã trạng thái hôn nhân
        boolean newVersion = r.readBoolean();
        if (newVersion) p.xeng = r.readInt();
        int nAction = r.readByte();
        for (int i = 0; i < nAction; i++) {
            r.readShort();
            r.readUTF();
            r.readShort();
            r.readInt();
            r.readByte();
        }
        p.luong = r.readInt();
        p.luongKhoa = r.readInt();
        r.readByte(); // hằng 1
        p.username = r.readUTF();
        return p;
    }
}
