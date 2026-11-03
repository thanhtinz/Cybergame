#!/usr/bin/env python3
"""Test sống ĐỢT 3 FARM — GĐ4 (vật nuôi + nấu ăn phía server).

Tài khoản mới -> mua gà (71) -> tua thời gian qua DB (bornTime/lastHarvest
-7h, lastFeed -1h) -> join thấy 20 trứng chờ thu, không đói -> thu hoạch
(74) được 20 sản phẩm id=50 -> cho ăn (65, thức ăn 117) khi đói -> xin giá
bán (72) có 950 -> nấu Bánh Trứng (91, món 1: 12 trứng + 720 lúa) -> gói
92 sớm không trả lời -> tua cook_finish qua DB -> 92 nhận món (205 x1) ->
bán gà (73) +950 xu -> bán 8 trứng còn lại (63) +240 xu -> DB sạch.
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_farm_test import R, login, create_male, farm_conn, wait_cmd  # noqa: E402
from live_khu_test import register  # noqa: E402
from live_shop_bill_test import db  # noqa: E402


def join_full(c, uid):
    """Gói 61: trả (animals, tail) — tail là phần dư sau cấu trúc cố định
    (đuôi nấu ăn foodID+remain khi đang nấu, rỗng khi không nấu)."""
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
    animals = []
    for _ in range(na):
        sp = r.byte()
        born = r.int()
        health = r.byte()
        hper = r.byte()
        negg = r.byte()
        hunger = r.bool()
        r.bool(); r.bool()
        animals.append((sp, born, health, hper, negg, hunger))
    r.ubyte(); r.ubyte()
    for _ in range(7):
        r.short()
    for _ in range(n):
        r.byte()
    tail = body[r.p:]
    return animals, tail


def inventory_products(c):
    c.send(60)
    body = wait_cmd(c, 60)
    r = R(body)
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
    specials = [(r.short(), r.int()) for _ in range(r.ubyte())]
    return products, specials


def patch_farm(uid, **fields):
    row = db(f"SELECT land_data, animal_data, hatgiong, phanbon, nongsan, nongsandacbiet, food_id, cook_finish FROM farm_data WHERE user_id = {uid}")
    return row


def main():
    user = "farm4" + time.strftime("%H%M%S")
    print(f"[0] tài khoản test: {user}")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_male(c)
    c.sock.close()
    time.sleep(3)
    uid = int(db(f"SELECT id FROM users WHERE username = '{user}'"))
    db(f"UPDATE players SET xu = 100000, luong = 100 WHERE user_id = {uid}")

    c = farm_conn(user, "abcd1234", uid)
    join_full(c, uid)  # vào farm lần đầu để server cấp ô + tạo dòng farm_data

    print("[A] mua gà (71, species 50, xu) -> 500 xu")
    c.send(71, struct.pack(">bb", 50, 1))
    body = wait_cmd(c, 71)
    r = R(body)
    # khuôn 71 của bản fan: {byte species, int before, byte type, int xu, int luong, int luongK}
    sp = r.byte(); before = r.int(); tb = r.byte()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("71")
    assert sp == 50 and xu == 100000 - 500, (sp, xu)
    animals, _ = join_full(c, uid)
    assert len(animals) == 1 and animals[0][0] == 50, animals
    print(f"  gà đã vào chuồng, xu={xu}, bornTime={animals[0][1]}")

    # tua thời gian: gà đã sống 7 giờ, mới ăn 1 giờ trước
    c.sock.close()
    time.sleep(3)
    now = int(time.time())
    adata = json.loads(db(f"SELECT animal_data FROM farm_data WHERE user_id = {uid}"))
    adata[0]["bornTime"] = now - 7 * 3600
    adata[0]["lastHarvest"] = now - 7 * 3600
    adata[0]["lastFeed"] = now - 3600
    db("UPDATE farm_data SET animal_data = '%s' WHERE user_id = %d" % (json.dumps(adata), uid))

    c = farm_conn(user, "abcd1234", uid)
    print("[B] join lại: gà có 20 trứng chờ thu (1 chu kỳ 6h), không đói")
    animals, _ = join_full(c, uid)
    sp, born, health, hper, negg, hunger = animals[0]
    assert negg == 20 and hper == 100 and hunger is False, animals[0]
    print(f"  numEgg={negg}, harvestPer={hper}, hunger={hunger}")

    print("[C] thu hoạch gà (74) -> 20 sản phẩm id=50 vào kho")
    c.send(74, struct.pack(">ib", uid, 0))
    body = wait_cmd(c, 74)
    r = R(body)
    idx, num = r.byte(), r.short()
    r.done("74")
    assert idx == 0 and num == 20, (idx, num)
    products, _ = inventory_products(c)
    assert (50, 20) in products, products
    print(f"  kho: {products}")

    print("[D] đói: tua lastFeed -7h qua DB -> join thấy hunger=True; mua + cho ăn 117")
    c.sock.close()
    time.sleep(3)
    adata = json.loads(db(f"SELECT animal_data FROM farm_data WHERE user_id = {uid}"))
    adata[0]["lastFeed"] = int(time.time()) - 7 * 3600
    db("UPDATE farm_data SET animal_data = '%s' WHERE user_id = %d" % (json.dumps(adata), uid))
    c = farm_conn(user, "abcd1234", uid)
    animals, _ = join_full(c, uid)
    assert animals[0][5] is True, animals[0]
    c.send(62, struct.pack(">hbb", 117, 1, 1))
    wait_cmd(c, 62)
    c.send(65, struct.pack(">ibh", uid, 0, 117))
    body = wait_cmd(c, 65)
    r = R(body)
    assert (r.byte(), r.short()) == (0, 117)
    r.done("65-feed")
    animals, _ = join_full(c, uid)
    assert animals[0][5] is False, animals[0]
    print("  cho ăn xong: hunger=False")

    print("[E] xin giá bán gà (72) -> câu chữ có 950")
    c.send(72, struct.pack(">ib", uid, 0))
    body = wait_cmd(c, 72)
    r = R(body)
    idx = r.byte(); text = r.utf()
    r.done("72")
    assert idx == 0 and "950" in text, text
    print(f"  '{text}'")

    print("[F] nấu Bánh Trứng (91 món 1): gieo 720 lúa qua DB trước")
    c.sock.close()
    time.sleep(3)
    ns = json.loads(db(f"SELECT nongsan FROM farm_data WHERE user_id = {uid}"))
    ns.append({"id": 6, "soluong": 720})
    db("UPDATE farm_data SET nongsan = '%s' WHERE user_id = %d" % (json.dumps(ns), uid))
    c = farm_conn(user, "abcd1234", uid)
    c.send(91, struct.pack(">h", 1))
    body = wait_cmd(c, 91)
    r = R(body)
    fid, mins = r.short(), r.short()
    r.done("91")
    assert fid == 1 and mins == 60, (fid, mins)
    animals, tail = join_full(c, uid)
    assert len(tail) == 4, f"đuôi nấu ăn trong gói 61: {tail!r}"
    tfid, tremain = struct.unpack(">hh", tail)
    assert tfid == 1 and 59 <= tremain <= 60, (tfid, tremain)
    products, _ = inventory_products(c)
    assert (50, 8) in products and all(p[0] != 6 for p in products), products
    print(f"  đang nấu (đuôi 61: món {tfid}, còn {tremain}'), nguyên liệu đã trừ: {products}")

    print("[G] gói 92 khi chưa chín: server không trả lời")
    c.send(92)
    try:
        c.recv(timeout=3)
        raise AssertionError("92 sớm mà server lại trả lời")
    except TimeoutError:
        print("  im lặng như kỳ vọng")

    print("[H] tua cook_finish qua DB -> 92 nhận Bánh Trứng (205 x1)")
    c.sock.close()
    time.sleep(3)
    db(f"UPDATE farm_data SET cook_finish = {int(time.time()) - 10} WHERE user_id = {uid}")
    c = farm_conn(user, "abcd1234", uid)
    c.send(92)
    wait_cmd(c, 92)
    products, _ = inventory_products(c)
    assert (205, 1) in products, products
    print(f"  kho: {products}")

    print("[I] bán gà (73) +950 xu; bán 8 trứng còn lại (63) +240 xu")
    xu_before = int(db(f"SELECT xu FROM players WHERE user_id = {uid}"))
    c.send(73, struct.pack(">ib", uid, 0))
    body = wait_cmd(c, 73)
    r = R(body)
    fid2, idx2, xu2 = r.int(), r.byte(), r.int()
    r.done("73")
    assert idx2 == 0 and xu2 == xu_before + 950, (xu2, xu_before)
    animals, _ = join_full(c, uid)
    assert animals == [], animals
    c.send(63, struct.pack(">h", 50))
    body = wait_cmd(c, 63)
    r = R(body)
    total, cur_xu, sold_id = r.int(), r.int(), r.short()
    r.done("63-egg")
    assert total == 240 and sold_id == 50 and cur_xu == xu2 + 240, (total, cur_xu)
    print(f"  bán gà +950 (xu={xu2}), bán trứng +240 (xu={cur_xu})")

    c.sock.close()
    time.sleep(3)
    print("[J] DB: food_id = 0, animal_data rỗng; dọn tài khoản")
    assert int(db(f"SELECT food_id FROM farm_data WHERE user_id = {uid}")) == 0
    assert json.loads(db(f"SELECT animal_data FROM farm_data WHERE user_id = {uid}")) == []
    for t in ("letters", "friends", "daily_claims", "farm_data"):
        db(f"DELETE FROM {t} WHERE user_id = {uid}")
    db(f"DELETE FROM players WHERE user_id = {uid}")
    db(f"DELETE FROM users WHERE id = {uid}")
    print("PASS TOÀN BỘ — FARM GĐ4 (server)")


if __name__ == "__main__":
    main()
