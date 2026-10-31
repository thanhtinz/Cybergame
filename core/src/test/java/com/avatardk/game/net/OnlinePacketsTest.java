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
}
