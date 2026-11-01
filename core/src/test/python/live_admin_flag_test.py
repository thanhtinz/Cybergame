#!/usr/bin/env python3
"""Test sống CỜ BẢO TRÌ của trang admin (AdminServer + FeatureFlags).

Bật cờ maintenance qua API admin -> đăng nhập bằng nick thường
(betest02, role -1) PHẢI bị chặn kèm thông báo bảo trì; nick admin
(betest01, role 2) PHẢI vào được. Tắt cờ -> nick thường vào lại được.

Chạy: python3 live_admin_flag_test.py   (server game + admin phải đang chạy)
"""
import json
import sys
import urllib.request

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402

ADMIN = "http://127.0.0.1:19129"
FAIL = []


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


def admin_token():
    req = urllib.request.Request(
        ADMIN + "/api/login",
        data=json.dumps({"username": "betest01", "password": "abcd1234"}).encode(),
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["token"]


def set_flag(token, name, value):
    req = urllib.request.Request(
        ADMIN + "/api/flags/set",
        data=json.dumps({"name": name, "value": value}).encode(),
        headers={"Content-Type": "application/json", "X-Admin-Token": token})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["ok"]


def try_login(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    got, msgs = [], []
    try:
        while True:
            cmd, body = c.recv(timeout=3.0)
            got.append(cmd)
            if cmd == -9:
                msgs.append(read_utf(body))
            if cmd in (-4, -35):
                break
    except Exception:
        pass
    try:
        c.sock.close()
    except Exception:
        pass
    return got, msgs


def main():
    token = admin_token()
    print("[1] Bật cờ bảo trì qua API admin")
    check("set maintenance=1", set_flag(token, "maintenance", 1))

    print("[2] Nick thường đăng nhập khi bảo trì -> phải bị chặn")
    got, msgs = try_login("betest02", "abcd1234")
    check("không nhận LOGIN_SUCCESS/-35", -4 not in got and -35 not in got, f"cmds={got}")
    check("nhận thông báo bảo trì", any("bảo trì" in m for m in msgs), f"msgs={msgs}")

    print("[3] Nick admin đăng nhập khi bảo trì -> phải vào được")
    got, _ = try_login("betest01", "abcd1234")
    check("nhận LOGIN_SUCCESS hoặc tạo nhân vật", -4 in got or -35 in got, f"cmds={got}")

    print("[4] Tắt cờ bảo trì, nick thường vào lại được")
    check("set maintenance=0", set_flag(token, "maintenance", 0))
    got, _ = try_login("betest02", "abcd1234")
    check("nhận LOGIN_SUCCESS hoặc tạo nhân vật", -4 in got or -35 in got, f"cmds={got}")

    print("KẾT QUẢ:", "PASS toàn bộ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
