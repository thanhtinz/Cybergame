#!/usr/bin/env python3
"""Test sống Chợ + Giao dịch (gói 127, docs/market-trade-spec.md):
A đăng bán cá 444 x2 giá 10.000 -> B thấy trong chợ -> B mua -> tiền:
A nhận 9.500 (thuế 5%), B trừ 10.000, đồ vào rương B (kiểm DB sau khi
ngắt kết nối) -> Giao dịch: A mời B, B đồng ý, A đặt 1 cá 444 + 5.000
xu, B đặt 3.000 xu, cả hai khoá + chấp nhận -> DB/xu hai bên khớp.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf
from live_createchar_test import db

FAILS = []
MK = 127


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + (f"  [{extra}]" if extra else ""))
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


def wait_mk(c, sub, timeout=6.0, latest=False):
    if latest:
        c.q = []
    end = time.time() + timeout
    got = None
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == MK and len(bb) >= 1 and bb[0] == sub:
                got = c.q.pop(i)[1][1:]
                if not latest:
                    return got
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
        if latest and got is not None:
            end = min(end, time.time() + 0.3)
    return got


def mk(c, sub, payload=b""):
    c.send(MK, bytes([sub]) + payload)


def main():
    db("DELETE FROM market_listings")
    db("UPDATE players SET xu = 100000, chests = '[{\"id\": 444, \"expired\": -1, \"quantity\": 5}]' "
       "WHERE user_id = 1000")
    db("UPDATE players SET xu = 100000, chests = '[]' WHERE user_id = 1001")
    a = login("betest01")
    b = login("betest02")
    try:
        # --- A xin rương (sub 6) ---
        mk(a, 6)
        chest = wait_mk(a, 6)
        ok = chest is not None and chest[0] == 1 and struct.unpack_from(">h", chest, 1)[0] == 444 \
            and struct.unpack_from(">i", chest, 3)[0] == 5
        check(ok, "A nhận danh sách rương: cá 444 x5")

        # --- A đăng bán (sub 1): 444 x2 giá 10.000 ---
        mk(a, 1, struct.pack(">hii", 444, 2, 10000))
        res = wait_mk(a, 5)
        check(res is not None and res[0] == 1, "A đăng bán thành công")
        my = wait_mk(a, 0, latest=True)
        lid = None
        if my is not None and my[2] >= 1:
            lid = struct.unpack_from(">i", my, 3)[0]
        check(lid is not None, "Tin của A xuất hiện", f"id={lid}")

        # --- B xem chợ (sub 0) ---
        mk(b, 0, b"\x00")
        lst = wait_mk(b, 0)
        found = False
        if lst is not None and lst[2] >= 1:
            off = 3
            lid_b = struct.unpack_from(">i", lst, off)[0]; off += 4
            off += 4  # seller
            ln = struct.unpack_from(">H", lst, off)[0]; off += 2 + ln
            pid = struct.unpack_from(">h", lst, off)[0]; off += 2
            qty = struct.unpack_from(">i", lst, off)[0]; off += 4
            price = struct.unpack_from(">i", lst, off)[0]
            found = lid_b == lid and pid == 444 and qty == 2 and price == 10000
        check(found, "B thấy tin bán của A trong chợ")

        # --- B mua (sub 2) ---
        mk(b, 2, struct.pack(">i", lid))
        res2 = wait_mk(b, 5)
        check(res2 is not None and res2[0] == 1, "B mua thành công")

        # --- Giao dịch: A mời B (sub 10/12) ---
        mk(a, 10, struct.pack(">i", 1001))
        inv = wait_mk(b, 11)
        check(inv is not None and struct.unpack_from(">i", inv, 0)[0] == 1000,
              "B nhận lời mời giao dịch từ A")
        mk(b, 12, struct.pack(">i", 1000) + b"\x01")
        open_a = wait_mk(a, 13)
        open_b = wait_mk(b, 13)
        check(open_a is not None and open_b is not None, "Cả hai mở màn giao dịch")

        # A đặt: cá 444 x1 + 5.000 xu (sub 14)
        payload = bytes([1]) + struct.pack(">hiq", 444, 1, -1) + struct.pack(">i", 5000)
        mk(a, 14, payload)
        pset = wait_mk(b, 15)
        ok_set = False
        if pset is not None and pset[0] == 1:
            pid = struct.unpack_from(">h", pset, 1)[0]
            qty = struct.unpack_from(">i", pset, 3)[0]
            xu_b = struct.unpack_from(">i", pset, 15)[0]
            ok_set = pid == 444 and qty == 1 and xu_b == 5000
        check(ok_set, "B thấy phần giao dịch của A (cá 444 x1 + 5.000 xu)")
        # B đặt: 3.000 xu, không đồ
        mk(b, 14, bytes([0]) + struct.pack(">i", 3000))
        wait_mk(a, 15)
        # cả hai khoá (sub 16) rồi chấp nhận (sub 18)
        mk(a, 16, b"\x01")
        mk(b, 16, b"\x01")
        time.sleep(0.4)
        mk(a, 18)
        mk(b, 18)
        done_a = wait_mk(a, 19)
        done_b = wait_mk(b, 19)
        check(done_a is not None and done_a[0] == 1, "A nhận Giao dịch thành công")
        check(done_b is not None and done_b[0] == 1, "B nhận Giao dịch thành công")
    finally:
        try: a.sock.close()
        except Exception: pass
        try: b.sock.close()
        except Exception: pass
        time.sleep(3)

    # --- kiểm DB sau khi server đã lưu ---
    xu_a = int(db("SELECT xu FROM players WHERE user_id = 1000").strip())
    xu_b = int(db("SELECT xu FROM players WHERE user_id = 1001").strip())
    # A: 100.000 + 9.500 (bán chợ) - 5.000 (GD) + 3.000 (GD) = 107.500
    # B: 100.000 - 10.000 (mua chợ) + 5.000 (GD) - 3.000 (GD) = 92.000
    check(xu_a == 107500, "DB xu A = 107.500", f"xu_a={xu_a}")
    check(xu_b == 92000, "DB xu B = 92.000", f"xu_b={xu_b}")
    ch_a = db("SELECT chests FROM players WHERE user_id = 1000").strip()
    ch_b = db("SELECT chests FROM players WHERE user_id = 1001").strip()
    # A còn 444: 5 - 2 (chợ) - 1 (GD) = 2 ; B có 444: 2 (chợ) + 1 (GD) = 3
    check('"id": 444' in ch_a or '"id":444' in ch_a, "Rương A còn cá 444", ch_a[:80])
    check('"id": 444' in ch_b or '"id":444' in ch_b, "Rương B có cá 444", ch_b[:80])
    import json
    qa = sum(x.get("quantity", 1) for x in json.loads(ch_a) if x["id"] == 444)
    qb = sum(x.get("quantity", 1) for x in json.loads(ch_b) if x["id"] == 444)
    check(qa == 2, "Số lượng cá A = 2", f"qa={qa}")
    check(qb == 3, "Số lượng cá B = 3", f"qb={qb}")
    db("DELETE FROM market_listings")

    print()
    if FAILS:
        print("MARKET TEST FAIL:", FAILS)
        sys.exit(1)
    print("MARKET TEST ALL PASS")


if __name__ == "__main__":
    main()
