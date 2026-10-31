#!/usr/bin/env python3
"""Relay TCP 127.0.0.1:3307 -> 127.0.0.1:3306 (MariaDB).

Chỉ dùng trong sandbox phát triển: tiến trình Java bị chặn kết nối cơ sở dữ
liệu trực tiếp bởi lớp quyền của Muse (thẻ duyệt theo đích 3306 từng hết
hạn và bị ghi nhớ từ chối), trong khi Python không bị chặn và đích 3307
là một đích TCP mới. Server game trỏ database.properties sang cổng 3307.
Khi chạy ngoài sandbox (máy/VPS thật) KHÔNG cần relay này.
"""
import socket
import threading

LISTEN = ("127.0.0.1", 3307)
TARGET = ("127.0.0.1", 3306)


def pump(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        try:
            dst.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass


def handle(client):
    try:
        upstream = socket.create_connection(TARGET)
    except OSError:
        client.close()
        return
    threading.Thread(target=pump, args=(client, upstream), daemon=True).start()
    threading.Thread(target=pump, args=(upstream, client), daemon=True).start()


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(LISTEN)
    srv.listen(50)
    print(f"db relay {LISTEN} -> {TARGET}", flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()


if __name__ == "__main__":
    main()
