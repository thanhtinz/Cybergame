package com.avatardk.game.net;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm chứng lớp mạng KHÔNG qua socket: sandbox chặn TCP chiều ra của tiến
 * trình Java ("Other TCP connections is turned off"), nên fixture byte do
 * script Python cài đặt độc lập giao thức gốc sinh ra
 * (src/test/python/gen_fixture.py). Test bơm luồng server vào GameConnection
 * qua cặp luồng byte, chạy trọn phiên bắt tay → mở đầu → đăng nhập → đăng ký →
 * tạo nhân vật, rồi đối chiếu byte client gửi ra với kỳ vọng của Python.
 */
class NetProtocolTest {
    private static final File FIXTURE_DIR = new File("build/test-fixtures");
    private static byte[] serverStream;
    private static byte[] clientExpected;

    @BeforeAll
    static void genFixtures() throws Exception {
        Process p = new ProcessBuilder("python3", "src/test/python/gen_fixture.py",
            FIXTURE_DIR.getPath())
            .redirectErrorStream(true)
            .start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "gen_fixture.py lỗi: " + out);
        serverStream = Files.readAllBytes(new File(FIXTURE_DIR, "server_stream.bin").toPath());
        clientExpected = Files.readAllBytes(new File(FIXTURE_DIR, "client_expected.bin").toPath());
    }

    private Msg waitFor(GameConnection conn, byte cmd, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        List<Msg> stash = new ArrayList<>();
        Msg found = null;
        while (System.currentTimeMillis() < deadline && found == null) {
            Msg m = conn.poll();
            if (m == null) {
                Thread.sleep(20);
                continue;
            }
            if (m.command == cmd) found = m;
            else stash.add(m);
        }
        return found;
    }

    @Test
    void handshakeLoginRegisterCreateChar() throws Exception {
        ByteArrayOutputStream clientOut = new ByteArrayOutputStream();
        GameConnection conn = new GameConnection(new ByteArrayInputStream(serverStream), clientOut);
        conn.startHandshake();
        long deadline = System.currentTimeMillis() + 5000;
        while (!conn.isKeyReady() && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertTrue(conn.isKeyReady(), "phải lấy được khoá phiên từ gói -27, lỗi: " + conn.error());

        AvatarProtocol.sendIntro(conn, 1280, 720);
        AvatarProtocol.login(conn, "tester", "secret");
        Msg ok = waitFor(conn, AvatarProtocol.LOGIN_SUCESS, 5000);
        assertNotNull(ok, "phải nhận LOGIN_SUCESS (-4)");
        PlayerInfo p = PlayerInfo.parse(ok);
        assertEquals(42, p.id);
        assertArrayEquals(new int[]{0, 80, 46, 474, 628}, p.wearing);
        assertEquals(1, p.gender);
        assertEquals(50, p.levelMain);
        assertEquals(12500, p.xu);
        assertEquals(260, p.luong);
        assertEquals(15, p.luongKhoa);
        assertEquals(7, p.xeng);
        assertEquals("tester", p.username);

        AvatarProtocol.register(conn, "newbie", "abcd");
        Msg reg = waitFor(conn, AvatarProtocol.SERVER_MESSAGE, 5000);
        assertNotNull(reg, "đăng ký phải nhận SERVER_MESSAGE (-9)");
        DataInputStream r = reg.reader();
        assertTrue(r.readUTF().contains("Đăng ký thành công"));

        AvatarProtocol.createCharacter(conn, 1);
        Msg cre = waitFor(conn, AvatarProtocol.CRE_CHARACTER, 5000);
        assertNotNull(cre, "tạo nhân vật phải nhận gói -35");
        assertTrue(cre.reader().readBoolean());
        conn.close();

        // Toàn bộ byte client đã gửi phải khớp từng byte với kỳ vọng độc lập của Python
        assertArrayEquals(clientExpected, clientOut.toByteArray(),
            "byte client gửi ra phải khớp fixture Python (giao thức + XOR lăn)");
    }
}
