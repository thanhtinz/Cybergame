#!/usr/bin/env python3
"""Test sống: 2 món shop MỚI lấy từ bản chính (7124/7125 "Sóng biển hồng",
sell 14, nữ, 150 lượng) mua được trên server local sau khi nhập vào DB.

Kịch bản: tài khoản nữ mới -> cấp 500 lượng -> mua 7124 (áo) và 7125
(quần) bằng Lượng, ngày 0 (vĩnh viễn) -> giá 150/món -> DB có 2 món,
expired -1 -> dọn tài khoản.
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register  # noqa: E402
from live_shop_bill_test import db, buy  # noqa: E402


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


def create_female(c):
    parts = [89, 88, 0, 4, 49]
    body = bytes([2, len(parts)]) + b"".join(struct.pack(">h", p) for p in parts)
    c.send(-35, body)
    for _ in range(20):
        cmd, b = c.recv(timeout=5.0)
        if cmd == -35:
            assert b[0] == 1, "tạo nhân vật thất bại"
            return
    raise AssertionError("không nhận được trả lời -35")


def main():
    user = "new" + time.strftime("%H%M%S") + "x"
    print(f"[0] tài khoản test nữ: {user}")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_female(c)
    c.sock.close()
    time.sleep(3)
    uid = db(f"SELECT id FROM users WHERE username = '{user}'")
    assert uid, "không thấy user trong DB"
    db(f"UPDATE players SET luong = 500 WHERE user_id = {uid}")

    c = login(user, "abcd1234")
    print("[A] mua 7124 (áo) bằng Lượng -> giá 150")
    pid, price, xu, luong = buy(c, 7124, 2, 0)
    print(f"  part={pid}, giá={price}, lượng={luong}")
    assert pid == 7124 and price == 150 and luong == 350

    print("[B] mua 7125 (quần) bằng Lượng -> giá 150")
    pid, price, xu, luong = buy(c, 7125, 2, 0)
    print(f"  part={pid}, giá={price}, lượng={luong}")
    assert pid == 7125 and price == 150 and luong == 200

    c.sock.close()
    time.sleep(3)
    print("[C] DB: wearing + chests có 7124/7125, expired -1")
    wear = json.loads(db(f"SELECT wearing FROM players WHERE user_id = {uid}") or "[]")
    chests = json.loads(db(f"SELECT chests FROM players WHERE user_id = {uid}") or "[]")
    exp = {}
    for it in wear + chests:
        exp.setdefault(it["id"], []).append(it["expired"])
    print(" ", exp)
    assert exp.get(7124) == [-1], f"7124 sai: {exp.get(7124)}"
    assert exp.get(7125) == [-1], f"7125 sai: {exp.get(7125)}"

    print("[D] dọn tài khoản test")
    for t in ("letters", "friends", "daily_claims", "farm_data",
              "chests", "clan_members", "itemlimited", "betgame"):
        db(f"DELETE FROM {t} WHERE user_id = {uid}")
    db(f"DELETE FROM friends WHERE friend_id = {uid}")
    db(f"DELETE FROM players WHERE user_id = {uid}")
    db(f"DELETE FROM users WHERE id = {uid}")
    print("LIVE SHOP NEW ITEMS TEST PASS")


if __name__ == "__main__":
    main()
