package com.avatardk.game.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;

/** Tin nhắn thoại (gói 61): codec μ-law G.711 + định dạng gói ráp mẩu. */
class VoiceTest {

    @Test
    void mulawSilenceAndRoundtrip() {
        // im lặng tuyệt đối phải thành byte 0xFF chuẩn G.711 và giải về 0
        assertEquals((byte) 0xFF, VoiceCodec.encodeSample(0));
        assertEquals(0, VoiceCodec.decodeSample((byte) 0xFF));
        // roundtrip trong sai số lượng tử hoá của μ-law (bước nhảy tăng theo biên độ)
        int[] probes = {0, 100, -100, 500, -500, 2000, -2000, 8000, -8000,
            16000, -16000, 30000, -30000, 32767, -32768};
        for (int p : probes) {
            int back = VoiceCodec.decodeSample(VoiceCodec.encodeSample(p));
            int tol = Math.max(24, Math.abs(p) / 7);
            assertTrue(Math.abs(back - p) <= tol,
                "pcm " + p + " -> " + back + " (tol " + tol + ")");
        }
        // cả dải sóng sine
        short[] wave = new short[16000];
        for (int i = 0; i < wave.length; i++) {
            wave[i] = (short) (Math.sin(i * 0.05) * 10000);
        }
        byte[] enc = VoiceCodec.encode(wave, wave.length);
        assertEquals(wave.length, enc.length); // 16-bit -> 1 byte/mẫu
        short[] dec = VoiceCodec.decode(enc);
        long err = 0;
        for (int i = 0; i < wave.length; i++) err += Math.abs(dec[i] - wave[i]);
        assertTrue(err / wave.length < 200, "lỗi trung bình quá lớn: " + (err / wave.length));
    }

    @Test
    void voicePacketParsesBack() throws Exception {
        // thân gói phía server phát: {int senderId, int clipId, short seq,
        // short total, byte[] audio} — dựng tay rồi parse bằng ZonePlayers
        byte[] audio = new byte[1234];
        for (int i = 0; i < audio.length; i++) audio[i] = (byte) (i * 31 + 7);
        Msg m = new Msg(AvatarProtocol.VOICE_PARK);
        DataOutputStream w = m.writer();
        w.writeInt(1001);
        w.writeInt(777);
        w.writeShort(2);
        w.writeShort(5);
        w.write(audio);
        w.flush();
        ZonePlayers.VoiceChunk v = ZonePlayers.parseVoice(new Msg(m.command, m.payload()));
        assertEquals(1001, v.senderId);
        assertEquals(777, v.clipId);
        assertEquals(2, v.seq);
        assertEquals(5, v.total);
        assertArrayEquals(audio, v.data);
    }
}
