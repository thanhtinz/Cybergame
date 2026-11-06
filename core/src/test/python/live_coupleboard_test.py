# -*- coding: utf-8 -*-
"""
Test sống: Bảng xếp hạng cặp đôi [PORT] gói COUPLE −122.
Chạy từ thư mục core/src/test/python:  python3 live_coupleboard_test.py
Luồng: dọn marry -> xin bảng (rỗng) -> chèn 3 cặp mẫu -> xin bảng
(kiểm thứ tự giảm dần theo cấp, cờ "cặp của tôi", hạng của tôi)
-> dọn sạch marry.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, read_utf
from live_createchar_test import db
from live_bossshop_test import login, pump

COUPLE = -122
FAIL = []


def check(name, cond):
    print(("  PASS " if cond else "  FAIL ") + name)
    if not cond:
        FAIL.append(name)


def board(c):
    c.q.clear()
    c.send(COUPLE, struct.pack(">b", 0))
    t0 = time.time()
    while time.time() - t0 < 8:
        pump(c, 0.4)
        for cmd, body in c.q:
            if cmd == COUPLE:
                off = 0
                sub = body[off]; off += 1
                if sub != 10:
                    continue
                n = body[off]; off += 1
                rows = []
                for _ in range(n):
                    n1, off = read_utf(body, off), off + 2 + len(read_utf(body, off).encode("utf-8"))
                    n2 = read_utf(body, off); off += 2 + len(n2.encode("utf-8"))
                    lv, per = body[off], body[off + 1]; off += 2
                    mine = body[off] != 0; off += 1
                    rows.append((n1, n2, lv, per, mine))
                rank = struct.unpack(">h", body[off:off + 2])[0]
                return rows, rank
    return None, None


db("DELETE FROM marry")
c, uid = login()
rows, rank = board(c)
check("bảng rỗng: n=0", rows == [])
check("bảng rỗng: hạng của tôi = 0", rank == 0)

db("INSERT INTO marry (idNam, idNu, level, perLevel, ngay) VALUES "
   "(1, 7, 12, 30, NOW()), (1000, 1001, 7, 45, NOW()), (7, 1, 3, 10, NOW())")
try:
    rows, rank = board(c)
    check("có 3 cặp", rows is not None and len(rows) == 3)
    if rows and len(rows) == 3:
        check("thứ tự cấp giảm dần 12 > 7 > 3",
              rows[0][2] == 12 and rows[1][2] == 7 and rows[2][2] == 3)
        check("cặp của tôi ở hạng 2 + cờ mine", rows[1][4] is True and rank == 2)
        check("hai cặp kia không mang cờ mine", rows[0][4] is False and rows[2][4] is False)
        check("điểm tiến độ đúng (45%)", rows[1][3] == 45)
finally:
    db("DELETE FROM marry")

print("ALL PASS" if not FAIL else "FAILED: %s" % FAIL)
sys.exit(1 if FAIL else 0)
