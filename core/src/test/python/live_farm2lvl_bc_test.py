#!/usr/bin/env python3
"""Test sống Farm cấp 2 + Ong — Giai đoạn B + C (tiếp live_farm2lvl_test
giai đoạn A đã PASS qua DB: up[0]=up[6]=1, ong cấp 1, xu 380.000;
beeLast đã lùi >10 giờ, server đã restart để cache sạch).

B: INFO thấy beeBank = 10 (cấp 1: 1 mật/giờ, cap 10) -> THU HOẠCH ->
INFO bank = 0 -> DB kho có Mật Ong 288 x10.
C: NÂNG CẤP ONG (30.000 xu + 5 mật) -> cấp 2 (cap 20, 2 mật/giờ) ->
DB: xu 350.000, mật còn 5, beeLevel = 2.
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf
from live_createchar_test import db

FAILS = []
F2 = -115


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + (f"  [{extra}]" if extra else ""), flush=True)
    if not cond:
        FAILS.append(label)


def login(user, pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    while True:
        cmd, body = c.recv(timeout=5)
        if cmd == -4:
            break
    c.q = []
    c.send(-1, bytes([10]))
    time.sleep(0.6)
    try:
        while True:
            cmd, body = c.recv(timeout=0.3)
            c.q.append((cmd, body))
    except Exception:
        pass
    return c


def wait_f2(c, sub, timeout=6.0):
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == F2 and len(bb) >= 1 and bb[0] == sub:
                return c.q.pop(i)[1][1:]
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
    return None


def f2(c, sub, payload=b""):
    c.send(F2, bytes([sub]) + payload)


def parse_info(info):
    return list(info[0:7]), info[7], *struct.unpack_from(">iii", info, 8)


def close(c):
    try:
        c.sock.close()
    except Exception:
        pass
    time.sleep(3)


def nongsan_map(uid):
    raw = db(f"SELECT nongsan FROM farm_data WHERE user_id = {uid}").strip()
    return {x["id"]: x["soluong"] for x in json.loads(raw)} if raw else {}


def main():
    c = login("betest01")
    try:
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = False
        if info is not None and len(info) >= 20:
            up, lvl, bank, cap, perh = parse_info(info)
            ok = bank == 10 and cap == 10 and perh == 1 and lvl == 1
        check(ok, "B1 Sau >10 giờ: beeBank = 10 (đầy cap cấp 1)")

        f2(c, 4)
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "B2 Thu hoạch mật thành công",
              res[1:].decode("utf-8", "ignore") if res else "")
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = info is not None and len(info) >= 20 and parse_info(info)[2] == 0
        check(ok, "B3 INFO sau thu: beeBank = 0")
    finally:
        close(c)
    have = nongsan_map(1000)
    check(have.get(288) == 10, "B4 Kho có Mật Ong 288 x10", str(have.get(288)))

    time.sleep(15)
    c = login("betest01")
    try:
        f2(c, 5)
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "C1 Nâng cấp ong lên cấp 2",
              res[1:].decode("utf-8", "ignore") if res else "")
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = False
        if info is not None and len(info) >= 20:
            up, lvl, bank, cap, perh = parse_info(info)
            ok = lvl == 2 and cap == 20 and perh == 2
        check(ok, "C2 INFO: ong cấp 2 (cap 20, 2 mật/giờ)")
    finally:
        close(c)
    xu = int(db("SELECT xu FROM players WHERE user_id = 1000").strip())
    check(xu == 350000, "C3 Xu = 350.000 (380k - 30k nâng ong)", f"xu={xu}")
    have = nongsan_map(1000)
    check(have.get(288) == 5, "C4 Mật ong còn 5 (10 - 5 nâng cấp)", str(have.get(288)))
    st = json.loads(db("SELECT lvl2_data FROM farm_data WHERE user_id = 1000").strip())
    check(st["beeLevel"] == 2, "C5 DB beeLevel = 2")

    print()
    if FAILS:
        print("FARM2LVL-BC TEST FAIL:", FAILS)
        sys.exit(1)
    print("FARM2LVL-BC TEST ALL PASS")


if __name__ == "__main__":
    main()
