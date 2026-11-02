#!/usr/bin/env python3
"""Test sống LUỒNG MUA HOÁ ĐƠN (gói -24 + byte số ngày) với server thật.

User chốt bật thật 2026-11-02. Luật giá phải khớp billTotal client:
- Xu: quy về giá 30 ngày (món có hạn: giá gốc x 30 / ngày gốc; món
  vĩnh viễn: giá gốc / 5), mốc d ngày = giá 30 ngày x d / 30,
  Vĩnh viễn = giá 30 ngày x 5.
- Lượng: luôn Vĩnh viễn, đúng giá niêm yết.

Kịch bản (tài khoản nam mới, cấp sẵn 100.000 xu + 100 lượng qua SQL
giữa hai phiên đăng nhập):
 A) part 2017 "short(30 ngày)" coin 5000 hạn 30 -> mua 7 ngày Xu:
    giá = round(5000 x 7 / 30) = 1167; hạn dùng lưu DB ~ +7 ngày.
 B) part 294 "tai mèo" coin 20000 vĩnh viễn -> mua mốc 30 ngày Xu:
    giá = (20000 / 5) = 4000; hạn dùng ~ +30 ngày.
 C) part 294 mua Vĩnh viễn bằng Xu: giá = 4000 x 5 = 20000; expired -1.
 D) part 472 "giỏ hoa" gold 5 -> mua bằng Lượng: giá 5; expired -1.
Mọi giá + số dư kiểm qua gói trả -24; hạn dùng kiểm trong DB sau khi
ngắt kết nối (server lưu khi thoát).
"""
import json
import struct
import subprocess
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register  # noqa: E402

MYSQL = ["/home/hatch/workspace/tools/mariadb/bin/mysql",
         "--socket=/run/mysqld/mysqld.sock", "-u", "root",
         "-pavatardk-local", "avatar_2x", "-N", "-e"]


def db(sql):
    env = {"LD_LIBRARY_PATH": "/home/hatch/workspace/tools/mariadb/lib",
           "PATH": "/usr/bin:/bin"}
    out = subprocess.run(MYSQL + [sql], capture_output=True, text=True, env=env)
    return out.stdout.strip()


def login(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    for _ in range(30):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -4:
            return c
    raise AssertionError(f"{user}: không nhận được gói -4")


def create_male(c):
    parts = [89, 88, 0, 4, 14]
    body = bytes([1, len(parts)]) + b"".join(struct.pack(">h", p) for p in parts)
    c.send(-35, body)
    for _ in range(20):
        cmd, b = c.recv(timeout=5.0)
        if cmd == -35:
            assert b[0] == 1, "tạo nhân vật thất bại"
            return
    raise AssertionError("không nhận được trả lời -35")


def buy(c, part_id, typ, days):
    c.send(-24, struct.pack(">h", part_id) + bytes([typ, days]))
    for _ in range(30):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -24:
            off = 0
            pid = struct.unpack(">h", body[off:off + 2])[0]
            off += 2
            price = struct.unpack(">i", body[off:off + 4])[0]
            off += 4
            off += 1  # byte cờ 1
            ln = struct.unpack(">H", body[off:off + 2])[0]
            off += 2 + ln  # UTF thông báo
            xu = struct.unpack(">i", body[off:off + 4])[0]
            luong = struct.unpack(">i", body[off + 4:off + 8])[0]
            return pid, price, xu, luong
    raise AssertionError(f"không nhận được trả lời -24 cho part {part_id}")


def main():
    user = "shop" + time.strftime("%H%M%S") + "x"
    print(f"[0] tài khoản test: {user}")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_male(c)
    c.sock.close()
    time.sleep(3)
    uid = db(f"SELECT id FROM users WHERE username = '{user}'")
    assert uid, "không thấy user trong DB"
    db(f"UPDATE players SET xu = 100000, luong = 100 WHERE user_id = {uid}")

    c = login(user, "abcd1234")
    print("[A] 2017 mua 7 ngày bằng Xu -> giá 1167")
    pid, price, xu, luong = buy(c, 2017, 1, 7)
    print(f"  part={pid}, giá={price}, xu={xu}, lượng={luong}")
    assert pid == 2017 and price == 1167 and xu == 100000 - 1167

    print("[B] 294 mua mốc 30 ngày bằng Xu -> giá 4000")
    pid, price, xu, luong = buy(c, 294, 1, 30)
    print(f"  part={pid}, giá={price}, xu={xu}, lượng={luong}")
    assert pid == 294 and price == 4000 and xu == 100000 - 1167 - 4000

    print("[C] 294 mua Vĩnh viễn bằng Xu -> giá 20000")
    pid, price, xu, luong = buy(c, 294, 1, 0)
    print(f"  part={pid}, giá={price}, xu={xu}, lượng={luong}")
    assert pid == 294 and price == 20000 and xu == 100000 - 1167 - 4000 - 20000

    print("[D] 472 mua bằng Lượng -> giá 5, vĩnh viễn")
    pid, price, xu, luong = buy(c, 472, 2, 0)
    print(f"  part={pid}, giá={price}, xu={xu}, lượng={luong}")
    assert pid == 472 and price == 5 and luong == 95

    c.sock.close()
    time.sleep(3)
    print("[E] hạn dùng trong DB (wearing + chests)")
    wear = json.loads(db(f"SELECT wearing FROM players WHERE user_id = {uid}") or "[]")
    chests = json.loads(db(f"SELECT chests FROM players WHERE user_id = {uid}") or "[]")
    exp = {}
    for it in wear + chests:
        exp.setdefault(it["id"], []).append(it["expired"])
    print(" ", exp)
    now = int(time.time() * 1000)
    day = 86400000
    e2017 = exp[2017][0]
    assert abs(e2017 - (now + 7 * day)) < day, f"2017 expired {e2017} không ~+7 ngày"
    e294 = sorted(exp[294])
    assert e294[0] == -1, "294 phải có 1 món vĩnh viễn (expired -1)"
    assert abs(e294[1] - (now + 30 * day)) < day, "294 mốc 30 ngày sai hạn"
    assert exp[472][0] == -1, "472 mua Lượng phải vĩnh viễn"

    print("[F] dọn tài khoản test")
    for t in ("letters", "friends", "daily_claims", "farm_data",
              "chests", "clan_members", "itemlimited", "betgame"):
        col = "user_id" if t != "friends" else "user_id"
        db(f"DELETE FROM {t} WHERE {col} = {uid}")
    db(f"DELETE FROM friends WHERE friend_id = {uid}")
    db(f"DELETE FROM players WHERE user_id = {uid}")
    db(f"DELETE FROM users WHERE id = {uid}")
    print("LIVE SHOP BILL TEST PASS")


if __name__ == "__main__":
    main()
