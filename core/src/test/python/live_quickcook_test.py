#!/usr/bin/env python3
"""Nấu nhanh (gói 93 bản chính): nấu món 3 (30 phút) -> 93 step 0
hỏi giá (1 lượng / 5 phút còn lại) -> step 1 trừ lượng, chín ngay
-> 92 nhận món vào kho."""
import struct
import time

from live_farm_test import R, create_male, farm_conn, login, wait_cmd
from live_khu_test import register
from live_shop_bill_test import db


def main():
    user = "qc" + time.strftime("%H%M%S")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_male(c)
    c.sock.close()
    time.sleep(3)
    uid = int(db(f"SELECT id FROM users WHERE username = '{user}'"))
    db(f"UPDATE players SET luong = 100 WHERE user_id = {uid}")
    try:
        # vào farm lần đầu để server tạo farm_data rồi seed nguyên liệu
        c = farm_conn(user, "abcd1234", uid)
        c.send(61, struct.pack(">i", uid))
        wait_cmd(c, 61)
        c.sock.close()
        time.sleep(2)
        db("UPDATE farm_data SET nongsan = '[{\"id\":6,\"soluong\":1500}]'"
           f" WHERE user_id = {uid}")
        assert "1500" in db(f"SELECT nongsan FROM farm_data WHERE user_id = {uid}")

        c = farm_conn(user, "abcd1234", uid)
        c.send(61, struct.pack(">i", uid))
        wait_cmd(c, 61)

        # bắt đầu nấu món 3 (30 phút, nguyên liệu 6 x1440)
        c.send(91, struct.pack(">h", 3))
        r = R(wait_cmd(c, 91))
        assert r.short() == 3 and r.short() == 30
        print("PASS bắt đầu nấu món 3 (30 phút)")

        # step 0: hỏi giá -> {0, UTF "...giá 6 lượng?"}
        c.send(93, bytes([0]))
        r = R(wait_cmd(c, 93))
        assert r.byte() == 0
        info = r.utf()
        assert "6 lượng" in info, info
        print(f"PASS step 0 hỏi giá: {info}")

        # step 1: trả lượng -> {1, int giá, int lượng mới, int khoá}
        c.send(93, bytes([1]))
        r = R(wait_cmd(c, 93))
        assert r.byte() == 1
        price, luong, khoa = r.int(), r.int(), r.int()
        assert price == 6 and luong == 94, (price, luong, khoa)
        print(f"PASS step 1: trừ {price} lượng, còn {luong}")

        # nhận món ngay bằng 92, kiểm kho có sản phẩm 207
        c.send(92)
        wait_cmd(c, 92)
        c.send(60)
        r = R(wait_cmd(c, 60))
        for _ in range(r.ubyte()):
            r.byte(); r.short()
        for _ in range(r.ubyte()):
            r.byte(); r.short()
        r.int(); r.byte(); r.byte()
        for _ in range(r.ubyte()):
            r.short(); r.short()
        for _ in range(r.ubyte()):
            r.short(); r.short()
        r.byte(); r.int(); r.bool(); r.short(); r.byte()
        products = [(r.short(), r.int()) for _ in range(r.ubyte())]
        assert (207, 1) in products, products
        print(f"PASS nhận món sau nấu nhanh: kho có {products}")
        c.sock.close()
        time.sleep(1)
        print("ALL PASS")
    finally:
        db(f"DELETE FROM farm_data WHERE user_id = {uid}")
        db(f"DELETE FROM players WHERE user_id = {uid}")
        db(f"DELETE FROM users WHERE id = {uid}")


if __name__ == "__main__":
    main()
