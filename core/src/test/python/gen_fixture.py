#!/usr/bin/env python3
"""Sinh fixture byte cho NetProtocolTest (kiểm chứng lớp mạng Java không cần socket,
vì sandbox chặn TCP chiều ra của tiến trình Java — xem AGENTS.md).

Cài đặt ĐỘC LẬP giao thức gốc (docs/protocol-spec.md) và sinh hai file:
- server_stream.bin: toàn bộ byte server gửi: gói -27 chứa khoá (mã hoá vi phân),
  rồi lần lượt -4 (LOGIN_SUCESS), -9 (trả lời đăng ký), -35 (tạo nhân vật) —
  tất cả XOR khoá lăn theo con trỏ ghi của server.
- client_expected.bin: toàn bộ byte một client ĐÚNG phải gửi: gói -27 dạng rõ,
  rồi cụm mở đầu (-1, -17, -79, -86), LOGIN (-2), REGISTER (-30), CRE_CHARACTER
  (-35) — XOR khoá lăn theo con trỏ ghi của client (độc lập với server).
Chạy: python3 gen_fixture.py <thư_mục_ra>
"""
import os
import struct
import sys

KEY = b"1700000000000_kitakeyos"
OUT = sys.argv[1]


def write_utf(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def build_login_success() -> bytes:
    p = b""
    p += struct.pack(">i", 42)
    wearing = [0, 80, 46, 474, 628]
    p += struct.pack(">b", len(wearing))
    for w in wearing:
        p += struct.pack(">h", w)
    p += struct.pack(">b", 1)
    p += struct.pack(">b", 50)
    p += struct.pack(">b", 25)
    p += struct.pack(">i", 12500)
    p += bytes([10, 10, 100, 100, 90])
    p += struct.pack(">i", 260)
    p += struct.pack(">b", 3)
    for _ in wearing:
        p += struct.pack(">b", 1) + write_utf("")
    p += struct.pack(">h", -1)
    p += struct.pack(">b", 0)
    p += struct.pack(">b", 0)
    p += struct.pack(">b", 1)
    p += struct.pack(">b", 1)
    p += struct.pack(">h", 50)
    p += struct.pack(">h", -1)
    p += struct.pack(">b", 1)
    p += struct.pack(">i", 7)
    p += struct.pack(">b", 0)
    p += struct.pack(">i", 260)
    p += struct.pack(">i", 15)
    p += struct.pack(">b", 1)
    p += write_utf("tester")
    return p


class XorWriter:
    def __init__(self, key: bytes):
        self.key = key
        self.cur = 0
        self.buf = bytearray()

    def frame(self, cmd: int, body: bytes):
        raw = bytes([cmd & 0xFF, (len(body) >> 8) & 0xFF, len(body) & 0xFF]) + body
        for b in raw:
            self.buf.append(self.key[self.cur] ^ b)
            self.cur = (self.cur + 1) % len(self.key)


def main():
    os.makedirs(OUT, exist_ok=True)
    # ---- luồng server ----
    enc_key = bytes([KEY[0]] + [KEY[i] ^ KEY[i - 1] for i in range(1, len(KEY))])
    hs_payload = bytes([len(KEY)]) + enc_key
    server = bytearray()
    server += bytes([256 - 27, (len(hs_payload) >> 8) & 0xFF, len(hs_payload) & 0xFF]) + hs_payload
    sw = XorWriter(KEY)
    sw.frame(-4, build_login_success())
    sw.frame(-9, write_utf("Đăng ký thành công! Hãy đăng nhập để bắt đầu chơi."))
    sw.frame(-35, b"\x01")
    server += sw.buf
    with open(os.path.join(OUT, "server_stream.bin"), "wb") as f:
        f.write(server)

    # ---- luồng client kỳ vọng ----
    client = bytearray()
    client += bytes([256 - 27, 0, 0])  # gói -27 rỗng dạng rõ
    cw = XorWriter(KEY)
    cw.frame(-1, bytes([8]))
    intro = struct.pack(">b", 0) + struct.pack(">i", 10000) + write_utf("uni") \
        + struct.pack(">i", 10000) + struct.pack(">i", 1280) + struct.pack(">i", 720) \
        + b"\x01" + bytes([1]) + write_utf("2.5.8")
    cw.frame(-17, intro)
    cw.frame(-79, write_utf("0"))
    cw.frame(-86, bytes([2, 1]) + write_utf("desktop-java"))
    cw.frame(-2, write_utf("tester") + write_utf("secret") + write_utf("2.5.8"))
    cw.frame(-30, write_utf("newbie") + write_utf("abcd"))
    cw.frame(-35, bytes([1, 0]))
    client += cw.buf
    with open(os.path.join(OUT, "client_expected.bin"), "wb") as f:
        f.write(client)
    print("fixtures written to", OUT)


if __name__ == "__main__":
    main()
