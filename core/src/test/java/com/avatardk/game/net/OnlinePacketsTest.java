package com.avatardk.game.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Kiểm chứng bộ đọc gói park (đợt 2d) bằng byte dựng theo docs/park-spec.md
 *  — cùng format server Java ghi ra trong AvatarService.enter / MapService. */
class OnlinePacketsTest {

    private static byte[] bytes(Writer w) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        w.write(out);
        out.flush();
        return bos.toByteArray();
    }

    private interface Writer {
        void write(DataOutputStream w) throws IOException;
    }

    private static void writePlayer(DataOutputStream w, int id, String name,
                                    int[] wearing, int x, int y, int role) throws IOException {
        w.writeInt(id);
        w.writeUTF(name);
        w.writeByte(wearing.length);
        for (int p : wearing) w.writeShort(p);
        w.writeShort(x);
        w.writeShort(y);
        w.writeByte(role);
    }

    @Test
    void joinListParsesPlayersDirectsAndTail() throws IOException {
        byte[] payload = bytes(w -> {
            w.writeByte(9);   // server map id
            w.writeByte(0);   // zone
            w.writeShort(-1);
            w.writeShort(-1);
            w.writeByte(2);   // numUser
            writePlayer(w, 7, "Linh", new int[]{0, 80, 46, 474, 628}, 395, 100, 1);
            writePlayer(w, 9, "NpcAo", new int[]{0, 4}, 100, 90, 0);
            w.writeByte(2);   // direct Linh: trái
            w.writeByte(0);   // direct NpcAo: phải
            w.writeByte(101);
            w.writeByte(101);
            w.writeShort(-1);
            w.writeShort(-1);
            w.writeByte(0);
            w.writeByte(0);
            w.writeShort(0);  // nMapItems
            w.writeByte(0);   // nTypes
            w.writeByte(0);   // nMapItems2
            w.writeShort(-1);
            w.writeShort(-1);
        });
        List<ZonePlayers.RemotePlayer> ps =
            ZonePlayers.parseJoinList(new Msg(AvatarProtocol.AVATAR_JOIN_PARK, payload));
        assertEquals(2, ps.size());
        assertEquals(7, ps.get(0).id);
        assertEquals("Linh", ps.get(0).name);
        assertEquals(5, ps.get(0).wearing.length);
        assertEquals(474, ps.get(0).wearing[3]);
        assertEquals(395, ps.get(0).x);
        assertEquals(100, ps.get(0).y);
        assertEquals(1, ps.get(0).role);
        assertEquals(2, ps.get(0).direct);
        assertEquals(0, ps.get(1).role);
        assertEquals(0, ps.get(1).direct);
    }

    @Test
    void joinHeaderCarriesZoneAndBoardListParses() throws IOException {
        // gói 50: header phải lộ zoneId (khu) server xếp vào — kể cả khi
        // client xin bằng khu −1 (tự chọn)
        byte[] payload = bytes(w -> {
            w.writeByte(23);  // server map id
            w.writeByte(7);   // zoneId = KHU 7
            w.writeShort(-1);
            w.writeShort(-1);
            w.writeByte(1);
            writePlayer(w, 7, "Linh", new int[]{0, 80}, 395, 100, 1);
            w.writeByte(0);
            w.writeByte(101);
            w.writeShort(-1);
            w.writeByte(0);
            w.writeByte(0);
            w.writeShort(0);
            w.writeByte(0);
            w.writeByte(0);
            w.writeShort(-1);
        });
        ZonePlayers.JoinResult jr =
            ZonePlayers.parseJoin(new Msg(AvatarProtocol.AVATAR_JOIN_PARK, payload));
        assertEquals(23, jr.mapId);
        assertEquals(7, jr.zoneId);
        assertEquals(1, jr.players.size());

        // gói 60: byte số khu + mỗi khu một byte trạng thái (0 đầy, 1 đông, 2 vắng)
        byte[] board = bytes(w -> {
            w.writeByte(10);
            for (int s : new int[]{2, 1, 2, 0, 2, 2, 2, 2, 1, 2}) w.writeByte(s);
        });
        int[] st = ZonePlayers.parseBoardList(new Msg(AvatarProtocol.PARK_BOARD_LIST, board));
        assertEquals(10, st.length);
        assertEquals(1, st[1]);
        assertEquals(0, st[3]);
        assertEquals(2, st[9]);
    }

    @Test
    void playerJoinMoveLeaveChatParse() throws IOException {
        byte[] join = bytes(w -> {
            writePlayer(w, 8, "Tuấn", new int[]{0, 80, 46}, 525, 88, 1);
            w.writeByte(-1);
            w.writeShort(-1);
            w.writeShort(-1);
        });
        ZonePlayers.RemotePlayer p =
            ZonePlayers.parsePlayerJoin(new Msg(AvatarProtocol.PLAYER_JOIN, join));
        assertEquals(8, p.id);
        assertEquals("Tuấn", p.name);
        assertEquals(525, p.x);
        assertEquals(2, p.direct); // mặc định LEFT của gốc

        byte[] move = bytes(w -> {
            w.writeInt(8);
            w.writeShort(600);
            w.writeShort(90);
            w.writeByte(0);
        });
        int[] mv = ZonePlayers.parseMove(new Msg(AvatarProtocol.PLAYER_MOVE, move));
        assertEquals(8, mv[0]);
        assertEquals(600, mv[1]);
        assertEquals(90, mv[2]);
        assertEquals(0, mv[3]);

        byte[] leave = bytes(w -> w.writeInt(8));
        assertEquals(8, ZonePlayers.parseLeave(new Msg(AvatarProtocol.PLAYER_LEAVE, leave)));

        byte[] chat = bytes(w -> {
            w.writeInt(7);
            w.writeUTF("Chào Sơn!");
        });
        ZonePlayers.ChatMsg c = ZonePlayers.parseChat(new Msg(AvatarProtocol.CHAT_PARK, chat));
        assertEquals(7, c.id);
        assertEquals("Chào Sơn!", c.text);
    }

    @Test
    void feelPacketParsesAndEncodes() throws IOException {
        // gói 57 server phát (MapService.doAvatarFeel): int id + byte feel
        byte[] feel = bytes(w -> {
            w.writeInt(7);
            w.writeByte(107); // mặt 7 + 100 — byte y hệt client gốc
        });
        int[] f = ZonePlayers.parseFeel(new Msg(AvatarProtocol.AVATAR_FEEL, feel));
        assertEquals(7, f[0]);
        assertEquals(107, f[1]);

        // mặt 40: 40 + 100 = 140 — trên dây là byte -116, đọc không dấu = 140
        byte[] feel2 = bytes(w -> {
            w.writeInt(8);
            w.writeByte(140);
        });
        int[] f2 = ZonePlayers.parseFeel(new Msg(AvatarProtocol.AVATAR_FEEL, feel2));
        assertEquals(8, f2[0]);
        assertEquals(140, f2[1]);

        // mã hoá phía gửi: +100, trần 155; lớn hơn thì không gửi được
        assertEquals(104, AvatarProtocol.encodeFeelPart(4));
        assertEquals(127, AvatarProtocol.encodeFeelPart(27));
        assertEquals(255, AvatarProtocol.encodeFeelPart(155));
        assertEquals(-1, AvatarProtocol.encodeFeelPart(156));
        assertEquals(-1, AvatarProtocol.encodeFeelPart(606));
    }
}
