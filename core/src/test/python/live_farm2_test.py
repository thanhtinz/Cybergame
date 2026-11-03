#!/usr/bin/env python3
"""Test sống ĐỢT 3 FARM — GĐ2 (tưới/bón/mở ô phía server).

Tiếp nối live_farm_test (GĐ0): tài khoản mới -> gieo sẵn phân bón vào
farm_data -> trồng ớt -> tưới nước (65, item 100) -> phân giảm 15 phút
(65, item 113: kho trừ 1, cây già thêm 15 phút) -> xin giá mở ô (69) ->
mở ô bằng xu (70 type 1) -> mở ô bằng lượng (70 type 2) -> DB lưu đủ.
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_farm_test import R, login, create_male, farm_conn, wait_cmd  # noqa: E402
from live_khu_test import register  # noqa: E402
from live_shop_bill_test import db  # noqa: E402


def join_cells(c, uid):
    c.send(61, struct.pack(">i", uid))
    body = wait_cmd(c, 61)
    r = R(body)
    assert r.int() == uid
    n = r.ubyte()
    cells = []
    for _ in range(n):
        tid = r.byte()
        info = None
        if tid >= 0:
            info = (r.short(), r.byte(), r.byte(), r.bool(), r.bool(), r.bool())
        cells.append((tid, info))
    na = r.ubyte()
    for _ in range(na):
        r.byte()
        r.int(); r.byte(); r.byte(); r.byte(); r.bool(); r.bool(); r.bool()
    r.ubyte(); r.ubyte()
    for _ in range(7):
        r.short()
    for _ in range(n):
        r.byte()
    r.done("61")
    return cells


def inventory_usable(c):
    c.send(60)
    body = wait_cmd(c, 60)
    r = R(body)
    for _ in range(r.ubyte()):
        r.byte(); r.short()
    for _ in range(r.ubyte()):
        r.byte(); r.short()
    r.int(); r.byte(); r.byte()
    usable = [(r.short(), r.short()) for _ in range(r.ubyte())]
    for _ in range(r.ubyte()):
        r.short(); r.short()
    r.byte(); r.int(); r.bool(); r.short(); r.byte()
    for _ in range(r.ubyte()):
        r.short(); r.int()
    for _ in range(r.ubyte()):
        r.short(); r.int()
    r.done("60")
    return usable


def main():
    user = "farm2" + time.strftime("%H%M%S")
    print(f"[0] tài khoản test: {user}")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_male(c)
    c.sock.close()
    time.sleep(3)
    uid = int(db(f"SELECT id FROM users WHERE username = '{user}'"))
    db(f"UPDATE players SET xu = 100000, luong = 100 WHERE user_id = {uid}")

    # phiên 1: vào farm để server cấp 6 ô + lưu farm_data, rồi gieo phân bón
    c = farm_conn(user, "abcd1234", uid)
    cells = join_cells(c, uid)
    assert len(cells) == 6
    c.sock.close()
    time.sleep(3)
    db("UPDATE farm_data SET phanbon = '%s' WHERE user_id = %d"
       % (json.dumps([{"id": 113, "soluong": 2}, {"id": 112, "soluong": 1}]), uid))

    c = farm_conn(user, "abcd1234", uid)
    print("[A] kho: listItemFarm có phân 113x2 + 112x1 (lượt PhanBon)")
    usable = inventory_usable(c)
    assert usable == [(113, 2), (112, 1)], usable
    print(f"  usable={usable}")

    print("[B] mua + trồng ớt ô 0")
    c.send(62, struct.pack(">hbb", 43, 1, 1))
    wait_cmd(c, 62)
    c.send(64, struct.pack(">ibb", uid, 0, 43))
    wait_cmd(c, 64)

    print("[C] 65 tưới nước (item 100) -> echo + join lại thấy hết khô")
    c.send(65, struct.pack(">ibh", uid, 0, 100))
    body = wait_cmd(c, 65)
    r = R(body)
    assert (r.byte(), r.short()) == (0, 100)
    r.done("65-water")
    cells = join_cells(c, uid)
    tid, info = cells[0]
    assert tid == 43 and info[3] is False, f"isArid sau tưới = {info}"
    print(f"  ô 0: isArid={info[3]} (đã tưới)")

    print("[D] 65 bón phân 113 (giảm 15 phút) -> cây già +15', kho trừ 1")
    c.send(65, struct.pack(">ibh", uid, 0, 113))
    body = wait_cmd(c, 65)
    r = R(body)
    assert (r.byte(), r.short()) == (0, 113)
    r.done("65-fert")
    cells = join_cells(c, uid)
    tid, info = cells[0]
    assert tid == 43 and info[0] >= 15, f"phút sau bón = {info[0]}"
    usable = inventory_usable(c)
    assert usable == [(113, 1), (112, 1)], usable
    print(f"  ô 0: {info[0]} phút; usable={usable}")

    print("[E] 69 xin giá mở ô (đang 6 ô -> 1.000 xu / 1 lượng)")
    c.send(69, struct.pack(">i", uid))
    body = wait_cmd(c, 69)
    r = R(body)
    text = r.utf()
    r.done("69")
    assert "1.000 xu" in text and "1 lượng" in text, text
    print(f"  giá: {text}")

    print("[F] 70 mở ô bằng XU -> 7 ô, xu -1.000")
    c.send(70, struct.pack(">ib", uid, 1))
    body = wait_cmd(c, 70)
    r = R(body)
    fid, cur, tb = r.int(), r.int(), r.byte()
    msg = r.utf()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("70")
    assert fid == uid and tb == 1 and xu == 98990 and luong == 100, (fid, xu, luong)
    cells = join_cells(c, uid)
    assert len(cells) == 7, len(cells)
    print(f"  '{msg}' — xu={xu}, ô={len(cells)}")

    print("[G] 70 mở ô bằng LƯỢNG -> 8 ô, lượng -1")
    c.send(70, struct.pack(">ib", uid, 2))
    body = wait_cmd(c, 70)
    r = R(body)
    fid, cur, tb = r.int(), r.int(), r.byte()
    r.utf()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("70b")
    assert tb == 2 and xu == 98990 and luong == 99, (xu, luong)
    cells = join_cells(c, uid)
    assert len(cells) == 8, len(cells)
    print(f"  lượng={luong}, ô={len(cells)}")

    print("[G2] 62 mua phân bón 111 x2 bằng xu (nhánh vật phẩm >= 100)")
    c.send(62, struct.pack(">hbb", 111, 2, 1))
    body = wait_cmd(c, 62)
    r = R(body)
    pid, pn = r.short(), r.byte()
    before, tb = r.int(), r.byte()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("62-fert")
    assert pid == 111 and pn == 2 and xu == 98990 - 14, (pid, pn, xu)
    usable = inventory_usable(c)
    assert any(u[0] == 111 and u[1] == 2 for u in usable), usable
    print(f"  mua 111 x2 giá 7 xu OK, xu={xu}; kho vật phẩm: {usable}")

    print("[G2b] mua phân 115 (giảm 60') bằng lượng + dùng cho ô ớt + thu hoạch")
    c.send(62, struct.pack(">hbb", 115, 1, 2))
    body = wait_cmd(c, 62)
    r = R(body)
    pid, pn = r.short(), r.byte()
    r.int(); r.byte()
    xu2, luong2, lk2 = r.int(), r.int(), r.int()
    r.done("62-115")
    assert pid == 115 and luong2 == luong - 1, (pid, luong2, luong)
    c.send(65, struct.pack(">ibh", uid, 0, 115))
    body = wait_cmd(c, 65)
    r = R(body)
    ci, ii = r.byte(), r.short()
    r.done("65-115")
    assert ci == 0 and ii == 115, (ci, ii)
    c.send(66, struct.pack(">ib", uid, 0))
    body = wait_cmd(c, 66)
    r = R(body)
    hi, hn = r.byte(), r.short()
    r.done("66-harvest")
    assert hi == 0 and hn == 45, (hi, hn)
    print(f"  ớt chín nhờ phân 115, thu hoạch {hn} quả")

    print("[G3] 63 bán nông sản 286 (45 quả Ớt x 3 xu = 135 xu)")
    xu = xu2
    c.send(63, struct.pack(">h", 286))
    body = wait_cmd(c, 63)
    r = R(body)
    total, cur_xu, sold_id = r.int(), r.int(), r.short()
    r.done("63-sell")
    assert total == 135 and sold_id == 286 and cur_xu == xu + 135, (total, cur_xu, sold_id)
    print(f"  bán +{total} xu, số dư {cur_xu} OK")

    c.sock.close()
    time.sleep(3)
    print("[H] DB: land_data có 8 ô; dọn tài khoản")
    land = json.loads(db(f"SELECT land_data FROM farm_data WHERE user_id = {uid}"))
    assert len(land) == 8, len(land)
    for t in ("letters", "friends", "daily_claims", "farm_data"):
        db(f"DELETE FROM {t} WHERE user_id = {uid}")
    db(f"DELETE FROM players WHERE user_id = {uid}")
    db(f"DELETE FROM users WHERE id = {uid}")
    print("PASS TOÀN BỘ — FARM GĐ2 (server)")


if __name__ == "__main__":
    main()
