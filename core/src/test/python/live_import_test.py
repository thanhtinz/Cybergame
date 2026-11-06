# -*- coding: utf-8 -*-
"""
Test sống: kho part sau khi nhập 1.138 part từ bản chính.
Cấp betest01 món mới 7932 (Nón quỷ sừng hỏa lam) qua rương, mở giỏ
thấy món, mặc lên người được, wearing lưu DB sau khi thoát.
Dọn dẹp: gỡ món khỏi rương + wearing về như cũ.
Chạy từ thư mục core/src/test/python:  python3 live_import_test.py
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_createchar_test import db
from live_bossshop_test import login, pump

FAIL = []
NEW_PART = 7932


def check(name, cond):
    print(("  PASS " if cond else "  FAIL ") + name)
    if not cond:
        FAIL.append(name)


old_chests = db("SELECT chests FROM players WHERE user_id=1000")
old_wearing = db("SELECT wearing FROM players WHERE user_id=1000")
chests = json.loads(old_chests) if old_chests else []
if not any(it.get("id") == NEW_PART for it in chests):
    chests.append({"id": NEW_PART, "expired": -1})
    db("UPDATE players SET chests='%s' WHERE user_id=1000"
       % json.dumps(chests).replace("'", "''"))

c, uid = login()
try:
    c.send(50, bytes([0, 255]) + struct.pack(">h", 200) + struct.pack(">h", 150))
    pump(c, 1.5)
    c.q.clear()
    c.send(-47, struct.pack(">i", 1000))
    pump(c, 2.5)
    bag_ids = set()
    for cmd, body in c.q:
        if cmd == -47:
            # quét thô: part id dạng short big-endian xuất hiện trong gói
            for i in range(len(body) - 1):
                bag_ids.add(struct.unpack(">h", body[i:i + 2])[0])
    check("giỏ có món mới 7932", NEW_PART in bag_ids)

    c.q.clear()
    c.send(-48, struct.pack(">h", NEW_PART) + bytes([1]))
    pump(c, 2.0)
    got48 = any(cmd == -48 for cmd, _ in c.q)
    check("server phản hồi gói mặc -48", got48)
finally:
    c.sock.close()
time.sleep(3)

wearing = json.loads(db("SELECT wearing FROM players WHERE user_id=1000") or "[]")
check("wearing trong DB có 7932", any(it.get("id") == NEW_PART for it in wearing))

db("UPDATE players SET chests='%s', wearing='%s' WHERE user_id=1000"
   % (old_chests.replace("'", "''"), old_wearing.replace("'", "''")))
print("ALL PASS" if not FAIL else "FAILED: %s" % FAIL)
sys.exit(1 if FAIL else 0)
