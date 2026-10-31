#!/usr/bin/env python3
"""Server giả lập giao thức AvatarDK gốc để kiểm chứng lớp mạng của client Java.

Cài đặt đúng docs/protocol-spec.md:
- Nhận gói rỗng -27 dạng rõ -> trả -27 chứa khoá phiên mã hoá vi phân.
- Sau đó mọi byte XOR khoá lăn (con trỏ đọc/ghi độc lập).
- Nhận cụm mở đầu (-1, -17, -79, -86) rồi gói LOGIN (-2) -> trả LOGIN_SUCESS (-4)
  với các giá trị biết trước để test Java đối chiếu.
- Nhận REQUEST_REGISTER (-30) -> trả SERVER_MESSAGE (-9) như server port bổ sung.
Chạy: python3 mock_server.py [port]
"""
import socket
import struct
import sys

KEY = b"1700000000000_kitakeyos"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 19199


def write_utf(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def build_login_success() -> bytes:
    p = b""
    p += struct.pack(">i", 42)                    # id
    wearing = [0, 80, 46, 474, 628]
    p += struct.pack(">b", len(wearing))          # số part
    for w in wearing:
        p += struct.pack(">h", w)
    p += struct.pack(">b", 1)                     # gender
    p += struct.pack(">b", 50)                    # level chính
    p += struct.pack(">b", 25)                    # phần trăm level
    p += struct.pack(">i", 12500)                 # xu
    p += bytes([10, 10, 100, 100, 90])            # friendly, crazy, stylish, happy, 100-hunger
    p += struct.pack(">i", 260)                   # lượng
    p += struct.pack(">b", 3)                     # star
    for _ in wearing:                             # hạn dùng từng part
        p += struct.pack(">b", 1) + write_utf("")
    p += struct.pack(">h", -1)                    # icon clan
    p += struct.pack(">b", 0)                     # số lệnh menu
    p += struct.pack(">b", 0)                     # số lệnh xoay
    p += struct.pack(">b", 1)                     # isTour (boolean)
    p += struct.pack(">b", 1)                     # hằng 1
    p += struct.pack(">h", 50)                    # level chính lần 2
    p += struct.pack(">h", -1)                    # mã hôn nhân
    p += struct.pack(">b", 1)                     # isNewVersion
    p += struct.pack(">i", 7)                     # xèng
    p += struct.pack(">b", 0)                     # số action hoa/hôn (gốc ghi 4; 0 vẫn hợp lệ khi đọc)
    p += struct.pack(">i", 260)                   # lượng lần 2
    p += struct.pack(">i", 15)                    # lượng khoá
    p += struct.pack(">b", 1)                     # hằng 1
    p += write_utf("tester")                      # username
    return p


class Xor:
    def __init__(self, key: bytes):
        self.key = key
        self.cur_r = 0
        self.cur_w = 0

    def dec(self, b: int) -> int:
        out = self.key[self.cur_r] ^ b
        self.cur_r = (self.cur_r + 1) % len(self.key)
        return out

    def enc(self, b: int) -> int:
        out = self.key[self.cur_w] ^ b
        self.cur_w = (self.cur_w + 1) % len(self.key)
        return out


def read_exact(conn, n):
    data = b""
    while len(data) < n:
        chunk = conn.recv(n - len(data))
        if not chunk:
            raise EOFError("client đóng kết nối")
        data += chunk
    return data


def main():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", PORT))
    srv.listen(1)
    print("mock listening", PORT, flush=True)
    conn, addr = srv.accept()
    print("mock accepted from", addr, flush=True)
    x = Xor(KEY)
    # --- bắt tay: gói rõ -27 ---
    hdr = read_exact(conn, 3)
    print("mock handshake hdr:", list(hdr), flush=True)
    assert hdr[0] == (256 - 27), hdr
    size = (hdr[1] << 8) | hdr[2]
    read_exact(conn, size)
    enc_key = bytes([KEY[0]] + [KEY[i] ^ KEY[i - 1] for i in range(1, len(KEY))])
    payload = bytes([len(KEY)]) + enc_key
    conn.sendall(bytes([256 - 27, (len(payload) >> 8) & 0xFF, len(payload) & 0xFF]) + payload)

    def read_msg():
        cmd = x.dec(read_exact(conn, 1)[0])
        if cmd >= 128:
            cmd -= 256
        hi = x.dec(read_exact(conn, 1)[0])
        lo = x.dec(read_exact(conn, 1)[0])
        n = (hi << 8) | lo
        body = bytes(x.dec(b) for b in read_exact(conn, n))
        return cmd, body

    def send_msg(cmd, body):
        frame = bytes([cmd & 0xFF, (len(body) >> 8) & 0xFF, len(body) & 0xFF]) + body
        conn.sendall(bytes(x.enc(b) for b in frame))

    seen = []
    while True:
        cmd, body = read_msg()
        seen.append(cmd)
        if cmd == -2:  # LOGIN -> trả LOGIN_SUCESS
            send_msg(-4, build_login_success())
        elif cmd == -30:  # REQUEST_REGISTER -> SERVER_MESSAGE
            send_msg(-9, write_utf("Đăng ký thành công! Hãy đăng nhập để bắt đầu chơi."))
        elif cmd == -35:  # CRE_CHARACTER -> boolean true
            send_msg(-35, b"\x01")
        if cmd == -2:
            break
    print("mock seen:", seen, flush=True)
    # giữ kết nối thêm một lúc cho test đọc gói -4
    conn.settimeout(5)
    try:
        while True:
            cmd, body = read_msg()
            if cmd == -30:
                send_msg(-9, write_utf("Đăng ký thành công! Hãy đăng nhập để bắt đầu chơi."))
            elif cmd == -35:
                send_msg(-35, b"\x01")
    except Exception:
        pass
    conn.close()
    srv.close()


if __name__ == "__main__":
    main()
