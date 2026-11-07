#!/usr/bin/env python3
"""Phục vụ bản web (build/web) qua HTTP trong mạng nhà để test online
trên điện thoại: mở http://<IP-may-chay-server>:8080/ trên iPhone
(cùng WiFi) — trang HTTP nên game nối ws:// tới cổng 19130 của chính
máy đó, khỏi cần HTTPS/wss như trên GitHub Pages.

Chạy SAU khi game server đã lên (19128 + 19130):
    python3 tools/serve_web_local.py [port]
"""
import http.server
import os
import socket
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build", "web")
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


if __name__ == "__main__":
    os.chdir(ROOT)
    handler = http.server.SimpleHTTPRequestHandler
    httpd = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), handler)
    print(f"Bản web: http://{lan_ip()}:{PORT}/  (điện thoại cùng WiFi mở địa chỉ này)")
    print("Game server cần đang chạy ở cổng 19128/19130 trên chính máy này.")
    httpd.serve_forever()
