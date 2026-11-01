package com.avatardk.game.net;

/** Codec G.711 μ-law cho tin nhắn thoại: nén mẫu PCM 16-bit thành 1 byte
 *  (giọng 16 kHz mono = 16 KB/s, đủ rõ cho đoạn thoại ngắn). Thuật toán
 *  chuẩn ITU-T G.711 — encode/decode đối xứng, kiểm chứng bằng test. */
public final class VoiceCodec {
    private VoiceCodec() {}

    private static final int BIAS = 0x84;   // 132
    private static final int CLIP = 32635;

    public static byte encodeSample(int pcm) {
        int sign = 0;
        if (pcm < 0) { sign = 0x80; pcm = -pcm; }
        if (pcm > CLIP) pcm = CLIP;
        pcm += BIAS;
        int exponent = 7;
        for (int mask = 0x4000; (pcm & mask) == 0 && exponent > 0; mask >>= 1) exponent--;
        int mantissa = (pcm >> (exponent + 3)) & 0x0F;
        return (byte) (~(sign | (exponent << 4) | mantissa) & 0xFF);
    }

    public static short decodeSample(byte ulaw) {
        int u = ~ulaw & 0xFF;
        int sign = u & 0x80;
        int exponent = (u >> 4) & 0x07;
        int mantissa = u & 0x0F;
        int pcm = ((mantissa << 3) + BIAS) << exponent;
        pcm -= BIAS;
        return (short) (sign != 0 ? -pcm : pcm);
    }

    public static byte[] encode(short[] pcm, int len) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = encodeSample(pcm[i]);
        return out;
    }

    public static short[] decode(byte[] data) {
        short[] out = new short[data.length];
        for (int i = 0; i < data.length; i++) out[i] = decodeSample(data[i]);
        return out;
    }
}
