package com.avatardk.game.net;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Dữ liệu người chơi trong zone + bộ đọc các gói park 50/51/53/54/55 (77).
 *  Format đo từ server gốc — xem docs/park-spec.md. Toạ độ là đơn vị medium. */
public final class ZonePlayers {
    private ZonePlayers() {}

    /** Một người chơi (hoặc NPC phía server, role = 0) trong danh sách zone. */
    public static class RemotePlayer {
        public int id;
        public String name = "";
        public int[] wearing = new int[0];
        public int x, y;      // medium
        public int role;      // 0 = NPC
        public int direct;    // 0 = phải, 2 = trái (Base của gốc)
    }

    /** Gói 50 (trả lời join): danh sách người đang có trong zone (không gồm mình). */
    public static List<RemotePlayer> parseJoinList(Msg m) throws IOException {
        DataInputStream r = m.reader();
        r.readByte(); // mapId (server)
        r.readByte(); // zoneId
        r.readShort(); r.readShort(); // -1, -1
        int num = r.readByte();
        List<RemotePlayer> out = new ArrayList<>();
        for (int i = 0; i < num; i++) out.add(readPlayerBlock(r));
        for (RemotePlayer p : out) p.direct = r.readByte();
        // phần đuôi (hằng số + vật thể map): đọc bỏ theo đúng format server
        try {
            for (int i = 0; i < num; i++) r.readByte();  // 101
            for (int i = 0; i < num; i++) r.readShort(); // -1
            r.readByte(); r.readByte();                 // 0, 0
            r.readShort();                              // nMapItems
            int nTypes = r.readByte();
            for (int i = 0; i < nTypes; i++) {
                r.readByte(); r.readShort(); r.readByte(); r.readShort(); r.readShort();
                int nPos = r.readByte();
                for (int j = 0; j < nPos; j++) { r.readByte(); r.readByte(); }
            }
            int nItems = r.readByte();
            for (int i = 0; i < nItems; i++) { r.readByte(); r.readByte(); r.readByte(); r.readByte(); }
            for (int i = 0; i < num; i++) r.readShort(); // -1 cuối mỗi user
        } catch (IOException tail) {
            // đuôi gói lệch cũng không được làm mất danh sách người chơi đã đọc
        }
        return out;
    }

    /** Gói 51: một người mới vào zone. */
    public static RemotePlayer parsePlayerJoin(Msg m) throws IOException {
        DataInputStream r = m.reader();
        RemotePlayer p = readPlayerBlock(r);
        p.direct = 2; // mặc định LEFT như Base của gốc (gói 51 không chở direct)
        return p;
    }

    private static RemotePlayer readPlayerBlock(DataInputStream r) throws IOException {
        RemotePlayer p = new RemotePlayer();
        p.id = r.readInt();
        p.name = r.readUTF();
        int n = r.readByte();
        p.wearing = new int[n];
        for (int i = 0; i < n; i++) p.wearing[i] = r.readShort();
        p.x = r.readShort();
        p.y = r.readShort();
        p.role = r.readByte();
        return p;
    }

    /** Gói 53: id người rời zone. */
    public static int parseLeave(Msg m) throws IOException {
        return m.reader().readInt();
    }

    /** Gói 54: {id, x, y, direct}. */
    public static int[] parseMove(Msg m) throws IOException {
        DataInputStream r = m.reader();
        return new int[]{r.readInt(), r.readShort(), r.readShort(), r.readByte()};
    }

    /** Gói 55/77: {id, text}. */
    public static ChatMsg parseChat(Msg m) throws IOException {
        DataInputStream r = m.reader();
        ChatMsg c = new ChatMsg();
        c.id = r.readInt();
        c.text = r.readUTF();
        return c;
    }

    public static class ChatMsg {
        public int id;
        public String text = "";
    }
}
