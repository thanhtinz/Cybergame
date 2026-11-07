#!/usr/bin/env python3
"""Kiểm chứng cầu WebSocket (WsBridge 19130 -> TCP 19128): chạy trọn
luồng bắt tay + đăng nhập + vào zone QUA WebSocket bằng chính giao
thức game, dùng lại Conn của live_test với sock là adapter WS."""
import base64
import os
import socket
import struct
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_test import Conn, write_utf  # noqa: E402


class WsSock:
    """Socket-like: sendall/recv dạng luồng byte trên WebSocket."""

    def __init__(self, host="127.0.0.1", port=19130):
        self.sock = socket.create_connection((host, port), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        req = (
            "GET / HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        )
        self.sock.sendall(req.encode())
        resp = b""
        while b"\r\n\r\n" not in resp:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("bridge đóng khi bắt tay WS")
            resp += chunk
        head, _, rest = resp.partition(b"\r\n\r\n")
        assert b"101" in head.split(b"\r\n")[0], head[:80]
        self.buf = bytearray(rest)

    def settimeout(self, t):
        self.sock.settimeout(t)

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass

    def sendall(self, data: bytes):
        # khung binary FIN, có mặt nạ (bắt buộc phía client)
        mask = os.urandom(4)
        n = len(data)
        if n < 126:
            hdr = bytes([0x82, 0x80 | n])
        elif n <= 0xFFFF:
            hdr = bytes([0x82, 0x80 | 126]) + struct.pack(">H", n)
        else:
            hdr = bytes([0x82, 0x80 | 127]) + struct.pack(">Q", n)
        masked = bytes(b ^ mask[i & 3] for i, b in enumerate(data))
        self.sock.sendall(hdr + mask + masked)

    def _read_more(self):
        # đọc 1 khung WS, nhét payload vào buf
        def rd(n):
            out = b""
            while len(out) < n:
                c = self.sock.recv(n - len(out))
                if not c:
                    raise ConnectionError("bridge đóng kết nối")
                out += c
            return out

        b0, b1 = rd(2)
        opcode = b0 & 0x0F
        ln = b1 & 0x7F
        if ln == 126:
            ln = struct.unpack(">H", rd(2))[0]
        elif ln == 127:
            ln = struct.unpack(">Q", rd(8))[0]
        payload = rd(ln) if ln else b""
        if opcode == 8:
            raise ConnectionError("nhận khung close từ bridge")
        if opcode in (9, 10):
            return  # ping/pong của cầu — bỏ qua, đọc khung tiếp
        self.buf += payload

    def recv(self, n: int) -> bytes:
        while len(self.buf) < n:
            self._read_more()
        out = bytes(self.buf[:n])
        del self.buf[:n]
        return out


def main():
    c = Conn.__new__(Conn)
    c.sock = WsSock()
    c.key = None
    c.cur_r = 0
    c.cur_w = 0
    c.handshake()
    print("PASS bắt tay -27 qua WebSocket (khoá phiên OK)")
    c.openers()
    c.send(-2, write_utf("betest01") + write_utf("abcd1234") + write_utf("2.5.8"))
    deadline = time.time() + 8
    ok = False
    while time.time() < deadline:
        cmd, body = c.recv(timeout=5)
        if cmd == -4:
            ok = True
            break
    assert ok, "không nhận LOGIN_SUCESS qua WS"
    print("PASS đăng nhập betest01 qua WebSocket")
    c.q = []
    c.send(50, struct.pack(">bbhh", 0, -1, 100, 100))  # vào zone map 1 (wire 0)
    deadline = time.time() + 8
    joined = False
    while time.time() < deadline:
        try:
            cmd, body = c.recv(timeout=2)
        except Exception:
            break
        if cmd == 50:
            joined = True
            break
    assert joined, "không nhận gói vào zone (50) qua WS"
    print("PASS vào zone map 1 qua WebSocket")
    print("ALL PASS — cầu WebSocket thông suốt")


if __name__ == "__main__":
    main()
