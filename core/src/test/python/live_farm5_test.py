#!/usr/bin/env python3
"""Test sống FARM — thu hoạch khế (gói 85, [PORT]).

Tài khoản mới -> join farm: cây khế báo 180 quả -> gửi 85 -> trả
{215, 180} -> kho có (215, 180) -> join lại: khế báo 0 quả -> gửi 85
lần nữa -> trả {215, 0} -> bán 215 qua 63: +1.800 xu (180 x 10) ->
đóng socket -> DB: star_num = 0, star_regrow > now (mốc +24h).
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_farm_test import R, login, create_male, farm_conn, wait_cmd  # noqa: E402
from live_farm4_test import inventory_products  # noqa: E402
from live_khu_test import register  # noqa: E402
from live_shop_bill_test import db  # noqa: E402


def join_star(c, uid):
    """Gói 61: trả list 7 short của StarFruit {lv, img, fruit, num, skip, anTrom, timeFinish}."""
    c.send(61, struct.pack(">i", uid))
    body = wait_cmd(c, 61)
    r = R(body)
    assert r.int() == uid
    n = r.ubyte()
    for _ in range(n):
        tid = r.byte()
        if tid >= 0:
            r.short(); r.byte(); r.byte(); r.bool(); r.bool(); r.bool()
    na = r.ubyte()
    for _ in range(na):
        r.byte()  # species
        r.int(); r.byte(); r.byte(); r.byte(); r.bool(); r.bool(); r.bool()
    r.ubyte(); r.ubyte()  # numBarn, numPond
    star = [r.short() for _ in range(7)]
    return star


def main():
    user = "farm5" + time.strftime("%H%M%S")
    print(f"[0] tài khoản test: {user}")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_male(c)
    c.sock.close()
    time.sleep(3)
    uid = int(db(f"SELECT id FROM users WHERE username = '{user}'"))
    db(f"UPDATE players SET xu = 100000, luong = 100 WHERE user_id = {uid}")

    c = farm_conn(user, "abcd1234", uid)
    print("[A] join farm lần đầu: cây khế phải báo 180 quả (img 43, fruit 46)")
    star = join_star(c, uid)
    assert star[0] == 10 and star[1] == 43 and star[2] == 46 and star[3] == 180, star
    print(f"  StarFruit = {star}")

    print("[B] thu hoạch khế (85) -> {215, 180}")
    c.send(85)
    body = wait_cmd(c, 85)
    r = R(body)
    pid, num = r.short(), r.short()
    r.done("85")
    assert pid == 215 and num == 180, (pid, num)
    products, _ = inventory_products(c)
    assert (215, 180) in products, products
    print(f"  kho có Quả Khế x180: {products}")

    print("[C] join lại: khế còn 0 quả; gửi 85 nữa -> {215, 0}")
    star = join_star(c, uid)
    assert star[3] == 0, star
    c.send(85)
    body = wait_cmd(c, 85)
    r = R(body)
    pid, num = r.short(), r.short()
    assert pid == 215 and num == 0, (pid, num)

    print("[D] bán khế (63) -> +1.800 xu")
    c.send(63, struct.pack(">h", 215))
    body = wait_cmd(c, 63)
    r = R(body)
    total, cur_xu, sold_id = r.int(), r.int(), r.short()
    r.done("63")
    assert total == 1800 and sold_id == 215 and cur_xu == 100000 + 1800, (total, cur_xu, sold_id)
    print(f"  bán được {total} xu, xu hiện tại {cur_xu}")

    c.sock.close()
    time.sleep(3)
    print("[E] DB: star_num = 0, star_regrow ở tương lai (+24h); dọn tài khoản")
    assert int(db(f"SELECT star_num FROM farm_data WHERE user_id = {uid}")) == 0
    regrow = int(db(f"SELECT star_regrow FROM farm_data WHERE user_id = {uid}"))
    now = int(time.time())
    assert now + 23 * 3600 < regrow <= now + 24 * 3600 + 60, (regrow, now)
    for t in ("letters", "friends", "daily_claims", "farm_data"):
        db(f"DELETE FROM {t} WHERE user_id = {uid}")
    db(f"DELETE FROM players WHERE user_id = {uid}")
    db(f"DELETE FROM users WHERE id = {uid}")
    print("PASS TOÀN BỘ — THU HOẠCH KHẾ (85)")


if __name__ == "__main__":
    main()
