#!/usr/bin/env python3
"""Danh hiệu CÓ HẠN (timeTitle): cấp qua SQL (mô phỏng admin grant),
kiểm client nhận hạn dùng ở đuôi gói TITLE, bản ghi hết hạn bị xoá,
danh hiệu đang đeo hết hạn thì tự gỡ khi đăng nhập lại."""
import struct
import sys
import time

from live_couple_test import login, pump
from live_createchar_test import db
from live_test import read_utf

TITLE = -120
UID = 1000


def title_state(c):
    c.q = []
    c.send(TITLE, bytes([0]))
    pump(c, 1.2)
    for cmd, body in c.q:
        if cmd != TITLE or not body or body[0] != 0:
            continue
        off = 1
        equipped = struct.unpack_from(">h", body, off)[0]
        off += 2
        nd = struct.unpack_from(">h", body, off)[0]
        off += 2
        for _ in range(nd):
            off += 2
            name = read_utf(body, off)
            off += 2 + len(name.encode())
            off += 1
            cond = read_utf(body, off)
            off += 2 + len(cond.encode())
        no = struct.unpack_from(">h", body, off)[0]
        off += 2
        owned = []
        for _ in range(no):
            owned.append(struct.unpack_from(">h", body, off)[0])
            off += 2
        nf = body[off] if off < len(body) else 0
        off += 1 + 2 * nf
        expiry = {}
        if off + 2 <= len(body):
            ne = struct.unpack_from(">h", body, off)[0]
            off += 2
            for _ in range(ne):
                tid = struct.unpack_from(">h", body, off)[0]
                exp = struct.unpack_from(">q", body, off + 2)[0]
                off += 10
                expiry[tid] = exp
        return equipped, owned, expiry
    raise AssertionError("không nhận được TITLE state")


def main():
    now = int(time.time() * 1000)
    db("DELETE FROM player_titles WHERE user_id = %d" % UID)
    # danh hiệu 3: còn hạn 2 ngày; danh hiệu 2: ĐÃ hết hạn
    db("INSERT INTO player_titles (user_id, title_id, expires_at)"
       " VALUES (%d, 3, %d), (%d, 2, %d)"
       % (UID, now + 2 * 86400000, UID, now - 3600000))
    c = login("betest01")
    equipped, owned, expiry = title_state(c)
    assert 3 in owned, f"danh hiệu còn hạn phải có trong owned: {owned}"
    assert 2 not in owned, f"danh hiệu hết hạn không được có: {owned}"
    assert expiry.get(3, 0) > now + 86400000, f"đuôi hạn dùng sai: {expiry}"
    assert db("SELECT COUNT(*) FROM player_titles"
              " WHERE user_id = %d AND title_id = 2" % UID).strip() == "0", \
        "bản ghi hết hạn phải bị xoá khỏi DB"
    print("PASS cấp danh hiệu có hạn + đuôi hạn dùng + xoá bản ghi hết hạn")

    # đeo danh hiệu 3 rồi cho hết hạn -> đăng nhập lại phải tự gỡ
    c.q = []
    c.send(TITLE, bytes([1]) + struct.pack(">h", 3))
    pump(c, 1.0)
    assert db("SELECT title_id FROM players WHERE user_id = %d" % UID).strip() == "3"
    db("UPDATE player_titles SET expires_at = %d"
       " WHERE user_id = %d AND title_id = 3" % (now - 1000, UID))
    try:
        c.sock.close()
    except Exception:
        pass
    time.sleep(1.5)
    c2 = login("betest01")
    equipped2, owned2, _ = title_state(c2)
    assert equipped2 == 0, f"danh hiệu hết hạn vẫn đang đeo: {equipped2}"
    assert 3 not in owned2
    print("PASS danh hiệu đang đeo hết hạn -> tự gỡ khi đăng nhập lại")

    db("DELETE FROM player_titles WHERE user_id = %d" % UID)
    db("UPDATE players SET title_id = 0 WHERE user_id = %d" % UID)
    print("ALL PASS")


if __name__ == "__main__":
    main()
