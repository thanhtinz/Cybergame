#!/usr/bin/env python3
"""Test sống: cấu hình Vòng quay may mắn qua Admin API (cổng 19129).

[1] Đăng nhập admin (betest01, role 1) -> token.
[2] GET /api/wheel/config -> bộ mặc định (giá 25.000 xu / 5 lượng...).
[3] SAVE cấu hình test: giá xu 30.000, chỉ mở quà Xu (tỉ trọng
    0/100/0/0), quà Xu cố định 500 -> GET lại kiểm chứng đã lưu.
[4] Vào game quay thật: nhãn menu con phải là "30.000 xu", 3 quà
    đều là 500 xu, DB: xu = đầu - 30.000 + 1.500.
[5] SAVE trả cấu hình mặc định.

Chạy: python3 live_wheelconfig_test.py   (cwd = thư mục này)
"""
import json
import struct
import sys
import time
import urllib.request

sys.path.insert(0, ".")
from live_diallucky_test import Rd, login, wait_cmd  # noqa: E402
from live_createchar_test import db  # noqa: E402

BASE = "http://127.0.0.1:19129"
NPC_WIRE = 2000000000 + 185
FAIL = []


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name
          + (f"  [{extra}]" if extra else ""), flush=True)
    if not cond:
        FAIL.append(name)


def api(path, body=None, token=None):
    req = urllib.request.Request(BASE + path)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("X-Admin-Token", token)
    with urllib.request.urlopen(req, data=data, timeout=10) as r:
        return json.loads(r.read().decode())


DEFAULTS = {
    "priceXu": 25000, "priceLuong": 5,
    "weightItem": 18, "weightXu": 45, "weightXp": 36, "weightLuong": 1,
    "xuMin": 10, "xuMax": 3000, "xpMin": 10, "xpMax": 100,
    "luongMin": 1, "luongMax": 2, "expireDays": "3,7,15,30",
}


def main():
    print("[1] đăng nhập admin")
    r = api("/api/login", {"username": "betest01", "password": "abcd1234"})
    check("login admin ok", r.get("ok") is True, str(r)[:80])
    token = r["token"]

    print("[2] GET cấu hình -> mặc định")
    cfg = api("/api/wheel/config", token=token)
    same = all(cfg.get(k) == v for k, v in DEFAULTS.items())
    check("cấu hình mặc định đúng", same,
          str({k: cfg.get(k) for k in DEFAULTS}))

    print("[3] SAVE cấu hình test + GET kiểm chứng")
    test_cfg = dict(DEFAULTS)
    test_cfg.update({"priceXu": 30000, "weightItem": 0, "weightXu": 100,
                     "weightXp": 0, "weightLuong": 0,
                     "xuMin": 500, "xuMax": 500})
    r = api("/api/wheel/config/save", test_cfg, token=token)
    check("save ok", r.get("ok") is True, str(r)[:80])
    cfg2 = api("/api/wheel/config", token=token)
    check("đã lưu giá 30.000 + chỉ quà Xu 500",
          cfg2.get("priceXu") == 30000 and cfg2.get("weightXu") == 100
          and cfg2.get("weightItem") == 0 and cfg2.get("xuMin") == 500
          and cfg2.get("xuMax") == 500, str(cfg2)[:120])

    print("[4] quay thật với cấu hình mới")
    db("UPDATE players p JOIN users u ON u.id = p.user_id "
       "SET p.chests='[]', p.xu=200000 WHERE u.username='betest01'")
    xu0 = int(db("SELECT p.xu FROM players p JOIN users u "
                 "ON u.id = p.user_id WHERE u.username='betest01'"))
    c, uid = login("betest01")
    check("login betest01", uid == 1000, str(uid))
    c.send(50, bytes([9, 255]) + struct.pack(">hh", 500, 90))
    try:
        wait_cmd(c, 50, label="join-map9")
    except Exception:
        pass
    c.send(54, bytes([1]) + struct.pack(">hhhh", 500, 90, 508, 55))
    time.sleep(0.3)
    c.send(-61, struct.pack(">i", NPC_WIRE))
    body = wait_cmd(c, -59, label="menu1")
    r = Rd(body)
    r.int(); r.byte()
    n = r.byte()
    menu1 = [r.utf() for _ in range(n)]
    check("menu cấp 1 đúng", menu1 == ["Quay số", "Xem hướng dẫn", "Thoát"],
          str(menu1))
    time.sleep(2.5)
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 0))
    body = wait_cmd(c, -59, label="menu2")
    r = Rd(body)
    r.int(); r.byte()
    n = r.byte()
    menu2 = [r.utf() for _ in range(n)]
    check("nhãn giá mới '30.000 xu'", "30.000 xu" in menu2, str(menu2))
    time.sleep(2.5)
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 1, 1))
    body = wait_cmd(c, -49, label="openshop")
    r = Rd(body)
    r.byte(); r.utf()
    np_ = r.short()
    prizes = [r.short() for _ in range(np_)]
    check("có danh sách quà", np_ >= 1, f"n={np_}")
    c.send(-64, struct.pack(">hh", prizes[0], 90))
    body = wait_cmd(c, -64, label="spin")
    r = Rd(body)
    buid, power, gn = r.int(), r.short(), r.byte()
    gifts = []
    for _ in range(gn):
        t = r.byte()
        if t == 1:
            gifts.append((t, r.short(), r.byte()))
        else:
            gifts.append((t, r.int()))
    check("3 quà đều là 500 xu",
          gifts == [(2, 500), (2, 500), (2, 500)], str(gifts))
    c.sock.close()
    time.sleep(3)
    xu1 = int(db("SELECT p.xu FROM players p JOIN users u "
                 "ON u.id = p.user_id WHERE u.username='betest01'"))
    check("DB: xu = đầu - 30.000 + 1.500", xu1 == xu0 - 30000 + 1500,
          f"{xu0} -> {xu1}")

    print("[5] trả cấu hình mặc định")
    r = api("/api/wheel/config/save", DEFAULTS, token=token)
    cfg3 = api("/api/wheel/config", token=token)
    check("đã trả mặc định", r.get("ok") is True
          and all(cfg3.get(k) == v for k, v in DEFAULTS.items()))

    print("\nKẾT QUẢ:", "PASS TẤT CẢ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
