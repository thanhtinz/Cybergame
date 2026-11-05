#!/usr/bin/env python3
"""Test sống Đấu giá ngược (gói -117, docs/daily-auction-spec.md):
INFO phiên đang mở (giá trong [sàn, khởi điểm]) -> A chốt giá thắng
-> INFO báo phiên đã đóng + tên người thắng -> B chốt sau bị chặn ->
DB: history 1 dòng đúng giá, xu A trừ đúng giá chốt, rương A có vật phẩm.
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf
from live_createchar_test import db

FAILS = []
AU = -117
PARTS = {2090, 2052, 2019, 2343, 620, 2099}


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
    return c


def wait_au(c, sub, timeout=6.0):
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == AU and len(bb) >= 1 and bb[0] == sub:
                return c.q.pop(i)[1][1:]
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
    return None


def au(c, sub, payload=b""):
    c.send(AU, bytes([sub]) + payload)


def read_utf_at(b, off):
    ln = struct.unpack_from(">H", b, off)[0]
    return b[off + 2:off + 2 + ln].decode("utf-8"), off + 2 + ln


def parse_info(b):
    active = b[0] == 1
    part, price, start, floor, secs = struct.unpack_from(">hiiii", b, 1)
    top, off = read_utf_at(b, 19)
    n = b[off]
    return active, part, price, start, floor, secs, top, n


def parse_result(b):
    txt, _ = read_utf_at(b, 1)
    return b[0] == 1, txt


def main():
    db("DELETE FROM auction_history")
    db("UPDATE players SET xu = 2000000, chests = '[]' WHERE user_id = 1000")
    db("UPDATE players SET xu = 100000 WHERE user_id = 1001")

    a = login("betest01")
    b = login("betest02")
    win_price = None
    win_part = None
    try:
        au(a, 0)
        info = wait_au(a, 0)
        ok = info is not None and len(info) >= 20
        if ok:
            active, part, price, start, floor, secs, top, nh = parse_info(info)
            ok = active and part in PARTS and floor <= price <= start and 0 < secs <= 1800
            win_part = part
        check(ok, "A1 INFO: phiên đang mở, giá trong [sàn, khởi điểm]",
              f"part={win_part}")

        au(a, 1)
        res = wait_au(a, 2)
        ok1, txt1 = parse_result(res) if res else (False, "")
        check(ok1, "A2 A chốt giá thắng phiên", txt1)
        import re
        m = re.search(r"giá (\d+) xu", txt1)
        if m:
            win_price = int(m.group(1))
        info = wait_au(a, 0)
        if info is not None:
            active, part, price, start, floor, secs, top, nh = parse_info(info)
            check(not active and top == "betest01",
                  "A3 INFO sau chốt: phiên đóng, người thắng = betest01", f"top={top}")
        else:
            check(False, "A3 INFO sau chốt: phiên đóng, người thắng = betest01")

        au(b, 1)
        res = wait_au(b, 2)
        ok2, _ = parse_result(res) if res else (True, "")
        check(not ok2, "A4 B chốt sau khi phiên đã đóng bị chặn")
    finally:
        for c in (a, b):
            try: c.sock.close()
            except Exception: pass
        time.sleep(3)

    rows = db("SELECT winner_name, part_id, price FROM auction_history").strip()
    parts = rows.split("\t") if rows else []
    check(len(parts) == 3 and parts[0] == "betest01" and int(parts[1]) == win_part
          and int(parts[2]) == win_price,
          "DB1 auction_history: 1 dòng, đúng người/part/giá", rows)
    xu = int(db("SELECT xu FROM players WHERE user_id = 1000").strip())
    check(win_price is not None and xu == 2000000 - win_price,
          "DB2 Xu A trừ đúng giá chốt", f"xu={xu}, gia={win_price}")
    chests = db("SELECT chests FROM players WHERE user_id = 1000").strip()
    items = json.loads(chests) if chests else []
    check(any(it.get("id") == win_part for it in items),
          "DB3 Rương A có vật phẩm thắng đấu giá", chests[:80])

    print()
    if FAILS:
        print("AUCTION TEST FAIL:", FAILS)
        sys.exit(1)
    print("AUCTION TEST ALL PASS")


if __name__ == "__main__":
    main()
