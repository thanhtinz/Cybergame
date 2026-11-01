package com.avatardk.game.music;

import java.io.Closeable;
import java.io.IOException;

/** Nguồn PCM 16-bit little-endian đã giải mã, sẵn sàng đẩy ra thiết bị
 *  âm thanh. Mở lười (open) rồi đọc tuần tự bằng read cho tới -1. */
public interface PcmSource extends Closeable {
    void open() throws IOException;

    int sampleRate();

    int channels();

    /** Đọc tối đa len byte PCM vào buf tại off; -1 khi hết luồng. */
    int read(byte[] buf, int off, int len) throws IOException;
}
