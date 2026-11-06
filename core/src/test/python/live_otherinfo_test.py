# -*- coding: utf-8 -*-
"""
Test sống: xem thông tin NGƯỜI KHÁC (gói −22 mang id đích) phải có đủ
khối cặp đôi của người đó — bản chính onInfoPlayer hiện đối tác +
quan hệ + cấp đôi; bản port trước đây bỏ qua khối này ở phía client.
Luồng: cưới betest01+betest02 qua giao thức gốc, đặt cấp đôi 7/40%,
đăng nhập lại, betest02 xin −22 của betest01 và kiểm khối người yêu.
Chạy từ thư mục core/src/test/python:  python3 live_otherinfo_test.py
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_createchar_test import db
from live_couple_test import login, pump, wait_for, parse_info22

FAIL = []


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + ((" | " + str(extra)) if extra else ""))
    if not cond:
        FAIL.append(label)


db("DELETE FROM marry WHERE idNam IN (1000,1001) OR idNu IN (1000,1001)")
a = login("betest01")
b = login("betest02")
try:
    a.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
    b.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
    pump(a, 0.5); pump(b, 0.5)
    a.send(-83, struct.pack(">h", 36))
    check(wait_for(a, -60, 6.0, menu_id=100) is not None, "A nhận TEXT_BOX 100")
    from live_couple_test import write_utf
    a.send(-60, struct.pack(">i", 1000) + bytes([100]) + write_utf("betest02"))
    check(wait_for(b, -60, 6.0, menu_id=101) is not None, "B nhận TEXT_BOX 101")
    b.send(-60, struct.pack(">i", 1001) + bytes([101]) + write_utf("ok"))
    pump(a, 1.0)
    db("UPDATE marry SET level=7, perLevel=40 WHERE idNam=1000 AND idNu=1001")
finally:
    a.sock.close(); b.sock.close()
time.sleep(3)

a = login("betest01")
b = login("betest02")
try:
    b.send(-22, struct.pack(">i", 1000))
    body = wait_for(b, -22, 6.0)
    check(body is not None, "B nhận −22 của A")
    if body:
        info = parse_info22(body)
        check(info["uid"] == 1000, "uid = 1000 (A)", info["uid"])
        check(info["pid"] == 1001, "khối người yêu: pid = 1001 (B)", info["pid"])
        check(info.get("rel") == "Cặp đôi hạnh phúc", "quan hệ theo cấp 7", info.get("rel"))
        check(info.get("lv") == 7 and info.get("pct") == 40, "cấp đôi 7 + 40%",
              f"{info.get('lv')}/{info.get('pct')}")
finally:
    a.sock.close(); b.sock.close()
    db("DELETE FROM marry WHERE idNam IN (1000,1001) OR idNu IN (1000,1001)")

print("ALL PASS" if not FAIL else "FAILED: %s" % FAIL)
sys.exit(1 if FAIL else 0)
