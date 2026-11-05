#!/usr/bin/env python3
"""Test sống Kết hôn/Hẹn hò: A (betest01, nam 1000) + B (betest02,
nữ 1001) cùng vào lễ đường (server map 10) -> A mở hẹn hò
(MENU_ROTATE -83 {36}) -> TEXT_BOX 100 nhập tên B -> B nhận
TEXT_BOX 101, trả "ok" -> DB marry level 0 -> icon đuôi gói 51 = 2
-> A gửi 93 bắt đầu lễ -> cả hai nhận 93 {1000, 1001}, DB level 1
-> B chia tay qua -83 {37} + TEXT_BOX 103 "ok" -> DB marry rỗng.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

FAILS = []


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


def pump(c, secs=0.6):
    end = time.time() + secs
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cmd, body))
        except Exception:
            break


def wait_for(c, cmd, timeout=6.0, menu_id=None):
    """Tìm gói cmd trong hàng đợi / luồng; menu_id áp cho -60."""
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == cmd:
                if menu_id is None or (len(bb) >= 5 and bb[4] == menu_id):
                    c.q.pop(i)
                    return bb
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            break
    return None


def join_wedding_map(c):
    c.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
    return wait_for(c, 50, 6.0) is not None


def parse_join51(body):
    r = memoryview(body)
    o = 0
    uid = struct.unpack(">i", r[o:o + 4])[0]; o += 4
    name = read_utf(body, o)
    o += 2 + len(name.encode("utf-8"))
    n = body[o]; o += 1
    o += 2 * n          # wearing
    o += 4              # x, y
    o += 1              # role
    o += 1              # byte -1
    o += 2              # short idImg
    icon = struct.unpack(">h", r[o:o + 2])[0]
    return uid, name, icon


def main():
    db("DELETE FROM marry WHERE idNam IN (1000,1001) OR idNu IN (1000,1001)")
    a = login("betest01")
    b = login("betest02")
    try:
        check(join_wedding_map(a), "A vào lễ đường (map 10)")
        # B vào sau: A nhận gói 51 của B — icon đuôi phải là -1 (độc thân)
        join_wedding_map(b)
        body51 = wait_for(a, 51, 6.0)
        check(body51 is not None, "A nhận gói 51 khi B vào khu")
        if body51:
            uid, name, icon = parse_join51(body51)
            check(uid == 1001 and icon == -1, "icon B lúc độc thân = -1", f"uid={uid} icon={icon}")
        pump(a, 0.4); pump(b, 0.4)

        # A mở hẹn hò
        a.send(-83, struct.pack(">h", 36))
        tb = wait_for(a, -60, 6.0, menu_id=100)
        check(tb is not None, "A nhận TEXT_BOX 100 (nhập tên người hẹn hò)")
        # A nhập tên B
        a.send(-60, struct.pack(">i", 1000) + bytes([100]) + write_utf("betest02"))
        tb = wait_for(b, -60, 6.0, menu_id=101)
        check(tb is not None, "B nhận TEXT_BOX 101 (lời mời hẹn hò)")
        # B đồng ý
        b.send(-60, struct.pack(">i", 1001) + bytes([101]) + write_utf("ok"))
        pump(a, 1.0); pump(b, 0.5)
        row = db("SELECT idNam, idNu, level FROM marry WHERE idNam=1000 AND idNu=1001")
        check("1000" in row and "1001" in row, "DB marry có cặp (1000,1001)", row.strip())
        check(row.strip().endswith("0"), "marry level = 0 (hẹn hò)", row.strip())

        # B rời + vào lại khu: A nhận 51 của B, icon = 2 (hẹn hò)
        b.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
        body51 = wait_for(a, 51, 6.0)
        if body51:
            uid, name, icon = parse_join51(body51)
            check(icon == 2, "icon B khi hẹn hò = 2", f"icon={icon}")
        else:
            check(False, "A nhận lại gói 51 của B sau hẹn hò")
        pump(b, 0.5)

        # A bắt đầu lễ cưới
        a.send(93, bytes([10, 255]))
        wa = wait_for(a, 93, 6.0)
        wb = wait_for(b, 93, 6.0)
        ok93 = False
        if wa and len(wa) >= 8:
            boy, girl = struct.unpack(">ii", wa[:8])
            ok93 = (boy, girl) == (1000, 1001)
        check(ok93 and wb is not None, "cả hai nhận 93 {boy=1000, girl=1001}")
        row = db("SELECT level FROM marry WHERE idNam=1000 AND idNu=1001")
        check(row.strip() == "1", "DB marry level = 1 sau lễ", row.strip())

        # B chia tay
        b.send(-83, struct.pack(">h", 37))
        tb = wait_for(b, -60, 6.0, menu_id=103)
        check(tb is not None, "B nhận TEXT_BOX 103 (xác nhận chia tay)")
        b.send(-60, struct.pack(">i", 1001) + bytes([103]) + write_utf("ok"))
        pump(a, 0.8)
        row = db("SELECT COUNT(*) FROM marry WHERE idNam IN (1000,1001) OR idNu IN (1000,1001)")
        check(row.strip() == "0", "DB marry rỗng sau chia tay", row.strip())
    finally:
        a.sock.close()
        b.sock.close()
        time.sleep(3)
    print("ALL PASS" if not FAILS else f"FAILED: {FAILS}")
    return 0 if not FAILS else 1


if __name__ == "__main__":
    sys.exit(main())
