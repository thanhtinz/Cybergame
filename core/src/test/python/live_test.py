#!/usr/bin/env python3
"""Client Python sống để test server Java thật ở 127.0.0.1:19128.

Cài đặt độc lập giao thức (docs/protocol-spec.md): bắt tay -27 dạng rõ để lấy
khoá phiên (mã hoá vi phân), sau đó XOR khoá lăn với con trỏ đọc/ghi độc lập.
Việc: kết nối 1 -> đăng ký tài khoản mới (-30); kết nối 2 -> đăng nhập (-2),
tạo nhân vật (-35) nếu server yêu cầu. Chạy: python3 live_test.py [user] [pass]
"""
import socket
import struct
import sys

HOST, PORT = "127.0.0.1", 19128
VERSION = "2.5.8"


def write_utf(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


class Conn:
    def __init__(self):
        self.sock = socket.create_connection((HOST, PORT), timeout=10)
        self.key = None
        self.cur_r = 0
        self.cur_w = 0

    def _read_exact(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("server đóng kết nối")
            buf += chunk
        return buf

    def _xor_read(self, data: bytes) -> bytes:
        out = bytearray()
        for b in data:
            out.append(self.key[self.cur_r] ^ b)
            self.cur_r = (self.cur_r + 1) % len(self.key)
        return bytes(out)

    def _xor_write(self, data: bytes) -> bytes:
        out = bytearray()
        for b in data:
            out.append(self.key[self.cur_w] ^ b)
            self.cur_w = (self.cur_w + 1) % len(self.key)
        return bytes(out)

    def handshake(self):
        self.sock.sendall(bytes([256 - 27, 0, 0]))
        head = self._read_exact(3)
        cmd = head[0] - 256 if head[0] > 127 else head[0]
        ln = (head[1] << 8) | head[2]
        payload = self._read_exact(ln)
        assert cmd == -27, f"cmd bắt tay lạ: {cmd}"
        klen = payload[0]
        enc = payload[1:1 + klen]
        # Giải mã vi phân: key[0]=t[0]; key[i]=t[i]^key[i-1] (XOR với byte khoá
        # ĐÃ GIẢI của vị trí trước, không phải byte truyền đi).
        dec = bytearray()
        for i, tb in enumerate(enc):
            dec.append(tb if i == 0 else tb ^ dec[i - 1])
        key = bytes(dec)
        self.key = key
        print(f"  khoá phiên: {key.decode()}")

    def send(self, cmd: int, body: bytes = b""):
        raw = bytes([cmd & 0xFF, (len(body) >> 8) & 0xFF, len(body) & 0xFF]) + body
        self.sock.sendall(self._xor_write(raw))

    def recv(self, timeout=6.0):
        self.sock.settimeout(timeout)
        head = self._xor_read(self._read_exact(3))
        cmd = head[0] - 256 if head[0] > 127 else head[0]
        ln = (head[1] << 8) | head[2]
        body = self._xor_read(self._read_exact(ln)) if ln else b""
        return cmd, body

    def openers(self):
        self.send(-1, bytes([8]))
        intro = struct.pack(">b", 0) + struct.pack(">i", 10000) + write_utf("uni") \
            + struct.pack(">i", 10000) + struct.pack(">i", 1280) + struct.pack(">i", 720) \
            + b"\x01" + bytes([1]) + write_utf(VERSION)
        self.send(-17, intro)
        self.send(-79, write_utf("0"))
        self.send(-86, bytes([2, 1]) + write_utf("desktop-java"))


def read_utf(body: bytes, off: int = 0):
    ln = struct.unpack(">H", body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode("utf-8", "replace")


def describe(cmd: int, body: bytes) -> str:
    if cmd == -9:
        return f"SERVER_MESSAGE: {read_utf(body)!r}"
    if cmd == -4:
        return f"LOGIN_SUCESS ({len(body)} bytes)"
    if cmd == -35:
        return f"CREATE_CHAR prompt {body.hex()}"
    return f"cmd {cmd} ({len(body)} bytes) {body[:24].hex()}"


def drain(c: Conn, label: str, seconds=3.0):
    got = []
    try:
        while True:
            cmd, body = c.recv(timeout=seconds)
            print(f"  [{label}] <- {describe(cmd, body)}")
            got.append(cmd)
            seconds = 1.0
    except (socket.timeout, TimeoutError):
        pass
    return got


def main():
    user = sys.argv[1] if len(sys.argv) > 1 else "betest01"
    pw = sys.argv[2] if len(sys.argv) > 2 else "abcd1234"

    print("[1] Kết nối đăng ký")
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-30, write_utf(user) + write_utf(pw))
    drain(c, "register")
    c.sock.close()

    print("[2] Kết nối đăng nhập")
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    got = drain(c, "login")
    if -35 in got:
        print("  -> server yêu cầu tạo nhân vật, gửi -35 (giới tính nam)")
        c.send(-35, bytes([1, 0]))
        drain(c, "create")
    c.sock.close()
    print("XONG")


if __name__ == "__main__":
    main()
