#!/usr/bin/env python3
"""Test sống ĐỢT 3 FARM — GĐ0 (server sửa lệch nghĩa gói + data đồng bộ theo client dat).

Kịch bản: tài khoản mới -> chuyển handler farm (-1 {10}) -> 51 đồng bộ ->
60 kho (parse ĐÚNG thứ tự client C# đọc, phải khớp tuyệt đối) -> 61 vào farm
(6 ô khởi đầu, không còn đuôi "đang nấu cơm" của bản fan) -> mua hạt ớt bằng xu,
mua nha đam bằng lượng (hạt duy nhất bán lượng), mua nha đam bằng xu phải bị từ chối ->
trồng ớt -> thu hoạch lúc chưa chín phải bị từ chối -> già hoá cây qua DB ->
vào lại thấy hervestPer=100/isArid=true đúng nghĩa client -> thu hoạch ra
sản phẩm 286 (productID của cây động) x45 -> bán 286 giá 3 xu -> mua gà 500 xu ->
join thấy vật nuôi với bornTime/health đúng nghĩa -> DB lưu đủ -> dọn tài khoản.
"""
import json
import re
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register  # noqa: E402
from live_shop_bill_test import db  # noqa: E402


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


class R:
    """Đọc gói đúng định dạng client C# (big-endian), kiểm tra đọc hết gói."""

    def __init__(self, b):
        self.b = b
        self.p = 0

    def byte(self):
        v = struct.unpack_from(">b", self.b, self.p)[0]
        self.p += 1
        return v

    def ubyte(self):
        v = self.b[self.p]
        self.p += 1
        return v

    def short(self):
        v = struct.unpack_from(">h", self.b, self.p)[0]
        self.p += 2
        return v

    def int(self):
        v = struct.unpack_from(">i", self.b, self.p)[0]
        self.p += 4
        return v

    def bool(self):
        return self.ubyte() != 0

    def utf(self):
        n = struct.unpack_from(">H", self.b, self.p)[0]
        self.p += 2
        s = self.b[self.p:self.p + n].decode("utf-8")
        self.p += n
        return s

    def done(self, what):
        assert self.p == len(self.b), f"{what}: còn thừa {len(self.b) - self.p} byte chưa đọc"


NOISE = {-6, -8, -9, -1, -10}  # thông báo/sự kiện/dialog chào mừng/echo đổi handler sau đăng nhập


def wait_cmd(c, want, timeout=6.0, skip=(), expect_dialog=False):
    """Chờ gói cmd == want; bỏ qua gói nhiễu. expect_dialog=True: dialog -10 được trả về dạng OTHER
    (dùng cho các bước kiểm tra bị server từ chối)."""
    t0 = time.time()
    while time.time() - t0 < timeout:
        cmd, body = c.recv(timeout=timeout)
        if cmd == want:
            return body
        if cmd == -10 and expect_dialog:
            return ("OTHER", cmd, body)
        if cmd in NOISE or cmd in skip:
            continue
        return ("OTHER", cmd, body)
    raise AssertionError(f"quá hạn chờ gói {want}")


def farm_conn(user, pw, uid):
    c = login(user, pw)
    c.send(-1, bytes([10]))  # GET_HANDLER -> FarmMsgHandler
    time.sleep(0.3)
    return c


def main():
    user = "farm" + time.strftime("%H%M%S")
    print(f"[0] tài khoản test: {user}")
    register(user, "abcd1234")
    c = login(user, "abcd1234")
    create_male(c)
    c.sock.close()
    time.sleep(3)
    uid = int(db(f"SELECT id FROM users WHERE username = '{user}'"))
    db(f"UPDATE players SET xu = 100000, luong = 100 WHERE user_id = {uid}")

    c = farm_conn(user, "abcd1234", uid)

    print("[A] 51 SET_BIG_FARM")
    c.send(51)
    body = wait_cmd(c, 51)
    r = R(body)
    n = r.ubyte()
    pairs = [(r.short(), r.short()) for _ in range(n)]
    v1, v2 = r.int(), r.int()
    r.done("51")
    assert pairs == [(0, 99), (1, 206)], pairs
    print(f"  cặp ảnh={pairs} ver={v1}/{v2}")

    print("[B] 60 INVENTORY — parse đúng thứ tự client")
    c.send(60)
    body = wait_cmd(c, 60)
    r = R(body)
    seeds = [(r.byte(), r.short()) for _ in range(r.ubyte())]
    ferts = [(r.byte(), r.short()) for _ in range(r.ubyte())]
    luong_slot = r.int()
    lv1, per1 = r.byte(), r.byte()
    spec_a = [(r.short(), r.short()) for _ in range(r.ubyte())]
    spec_b = [(r.short(), r.short()) for _ in range(r.ubyte())]
    level_store, capacity, is_new = r.byte(), r.int(), r.bool()
    lv2, per2 = r.short(), r.byte()
    products = [(r.short(), r.int()) for _ in range(r.ubyte())]
    spec_c = [(r.short(), r.int()) for _ in range(r.ubyte())]
    r.done("60")
    assert luong_slot == 100, f"ô lượng = {luong_slot} (bản fan ghi xu vào đây)"
    assert level_store == 1 and capacity == 64000 and is_new
    assert seeds == [] and products == [] and spec_a == []
    print(f"  lượng={luong_slot} levelStore={level_store} capacity={capacity} lvFarm={lv2}")

    print("[C] 61 JOIN — 6 ô khởi đầu, không đuôi nấu ăn")
    c.send(61, struct.pack(">i", uid))
    body = wait_cmd(c, 61)
    r = R(body)
    assert r.int() == uid
    ncells = r.ubyte()
    trees = []
    for _ in range(ncells):
        tid = r.byte()
        if tid >= 0:
            trees.append((tid, r.short(), r.byte(), r.byte(), r.bool(), r.bool(), r.bool()))
    nani = r.ubyte()
    barn, pond = r.ubyte(), r.ubyte()
    star = [r.short() for _ in range(7)]
    levels = [r.byte() for _ in range(ncells)]
    r.done("61")
    assert ncells == 6, f"số ô = {ncells}"
    assert trees == [] and nani == 0 and barn == 10 and pond == 10
    assert star == [10, 43, 46, 180, 0, 0, 0], star
    print(f"  ô={ncells} chuồng={barn} hồ={pond} khế={star} levels={levels}")

    print("[D] 62 mua 3 hạt ớt (43) bằng xu — 10 xu/hạt")
    c.send(62, struct.pack(">hbb", 43, 3, 1))
    body = wait_cmd(c, 62)
    r = R(body)
    assert (r.short(), r.byte()) == (43, 3)
    before, tb = r.int(), r.byte()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("62")
    assert before == 100000 and tb == 1 and xu == 99970 and luong == 100, (before, xu, luong)
    print(f"  xu 100000 -> {xu}")

    print("[E] 62 mua nha đam (31) bằng XU phải bị từ chối (chỉ bán lượng)")
    c.send(62, struct.pack(">hbb", 31, 1, 1))
    got = wait_cmd(c, 62, expect_dialog=True)
    assert got[0] == "OTHER" and got[1] == -10, f"mong dialog -10, nhận {got[:2]}"
    print("  bị từ chối bằng dialog ✓")

    print("[F] 62 mua 1 hạt nha đam bằng LƯỢNG — 1 lượng")
    c.send(62, struct.pack(">hbb", 31, 1, 2))
    body = wait_cmd(c, 62)
    r = R(body)
    assert (r.short(), r.byte()) == (31, 1)
    before, tb = r.int(), r.byte()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("62b")
    assert before == 100 and tb == 2 and xu == 99970 and luong == 99, (before, xu, luong)
    print(f"  lượng 100 -> {luong}")

    print("[G] 64 trồng ớt vào ô 0")
    c.send(64, struct.pack(">ibb", uid, 0, 43))
    body = wait_cmd(c, 64)
    r = R(body)
    assert (r.int(), r.byte(), r.byte()) == (uid, 0, 43)
    r.done("64")
    print("  trồng xong")

    print("[H] 66 thu hoạch ớt CHƯA chín phải bị từ chối")
    c.send(66, struct.pack(">ib", uid, 0))
    got = wait_cmd(c, 66, expect_dialog=True)
    assert got[0] == "OTHER" and got[1] == -10, f"mong dialog -10, nhận {got[:2]}"
    print("  bị từ chối bằng dialog ✓")

    c.sock.close()
    time.sleep(3)

    print("[I] DB: farm_data đã lưu (hạt 43x2 + 31x1, ô 0 có ớt)")
    row = db(f"SELECT land_data FROM farm_data WHERE user_id = {uid}")
    assert row, "chưa có dòng farm_data"
    land = json.loads(row)
    assert land[0]["type"] == 43, land[0]
    hg = json.loads(db(f"SELECT hatgiong FROM farm_data WHERE user_id = {uid}"))
    hgmap = {h["id"]: h["soluong"] for h in hg}
    assert hgmap.get(43) == 2 and hgmap.get(31) == 1, hgmap
    print(f"  hạt={hgmap}")
    # già hoá: plantedTime của ô 0 lùi 2 giờ (ớt chín 60 phút)
    old = land[0]["plantedTime"]
    m = re.match(r"(\d+)-(\d+)-(\d+)T(\d+):(\d+):(\d+)", old)
    assert m, old
    import datetime
    dt = datetime.datetime(*[int(x) for x in m.groups()]) - datetime.timedelta(hours=2)
    land[0]["plantedTime"] = dt.strftime("%Y-%m-%dT%H:%M:%S")
    db("UPDATE farm_data SET land_data = '%s' WHERE user_id = %d"
       % (json.dumps(land).replace("'", "''"), uid))

    c = farm_conn(user, "abcd1234", uid)
    print("[J] 61 vào lại — ô 0: ớt, hervestPer=100, isArid=true")
    c.send(61, struct.pack(">i", uid))
    body = wait_cmd(c, 61)
    r = R(body)
    assert r.int() == uid
    ncells = r.ubyte()
    cell0 = None
    for i in range(ncells):
        tid = r.byte()
        if tid >= 0:
            info = (r.short(), r.byte(), r.byte(), r.bool(), r.bool(), r.bool())
            if i == 0:
                cell0 = (tid, info)
    nani = r.ubyte()
    for _ in range(nani):
        r.byte()
        r.int(); r.byte(); r.byte(); r.byte(); r.bool(); r.bool(); r.bool()
    r.ubyte(); r.ubyte()
    [r.short() for _ in range(7)]
    [r.byte() for _ in range(ncells)]
    r.done("61b")
    assert cell0 is not None and cell0[0] == 43, cell0
    tmin, vit, herv, arid, worm, grass = cell0[1]
    assert tmin >= 120 and vit == 100 and herv == 100 and arid and not worm and not grass, cell0
    print(f"  ô 0: phút={tmin} sức khoẻ={vit} %lớn={herv} khô={arid}")

    print("[K] 66 thu hoạch ớt chín -> 45 sản phẩm (id 286)")
    c.send(66, struct.pack(">ib", uid, 0))
    body = wait_cmd(c, 66)
    r = R(body)
    idx, num = r.byte(), r.short()
    r.done("66")
    assert idx == 0 and num == 45, (idx, num)
    print(f"  thu {num} sản phẩm")

    print("[L] 63 bán sản phẩm 286 — giá 3 xu -> +135 xu")
    c.send(63, struct.pack(">h", 286))
    body = wait_cmd(c, 63)
    r = R(body)
    total, cur, iid = r.int(), r.int(), r.short()
    r.done("63")
    assert total == 135 and cur == 99970 + 135 and iid == 286, (total, cur, iid)
    print(f"  +{total} xu -> {cur}")

    print("[M] 71 mua gà (50) bằng xu — 500 xu")
    xu_before = cur
    c.send(71, bytes([50, 1]))
    body = wait_cmd(c, 71)
    r = R(body)
    sp, before, tb = r.byte(), r.int(), r.byte()
    xu, luong, lk = r.int(), r.int(), r.int()
    r.done("71")
    assert sp == 50 and before == xu_before and xu == xu_before - 500 and luong == 99, (sp, before, xu, luong)
    print(f"  xu {xu_before} -> {xu}")

    print("[N] 61 vào lại — có 1 con gà, bornTime/health đúng nghĩa")
    c.send(61, struct.pack(">i", uid))
    body = wait_cmd(c, 61)
    r = R(body)
    assert r.int() == uid
    ncells = r.ubyte()
    for _ in range(ncells):
        tid = r.byte()
        if tid >= 0:
            r.short(); r.byte(); r.byte(); r.bool(); r.bool(); r.bool()
    nani = r.ubyte()
    ani = None
    for _ in range(nani):
        sp = r.byte()
        born, health, harv, eggs = r.int(), r.byte(), r.byte(), r.byte()
        hunger, d0, d1 = r.bool(), r.bool(), r.bool()
        ani = (sp, born, health, harv, eggs, hunger, d0, d1)
    r.ubyte(); r.ubyte()
    [r.short() for _ in range(7)]
    [r.byte() for _ in range(ncells)]
    r.done("61c")
    assert nani == 1 and ani[0] == 50, ani
    assert abs(ani[1] - int(time.time())) < 300, f"bornTime={ani[1]}"
    assert ani[2] == 100 and ani[4] == 0, ani
    print(f"  gà: bornTime={ani[1]} health={ani[2]}")

    c.sock.close()
    time.sleep(3)
    print("[O] DB: animal_data có gà + bornTime; dọn tài khoản")
    ad = json.loads(db(f"SELECT animal_data FROM farm_data WHERE user_id = {uid}"))
    assert len(ad) == 1 and ad[0]["id"] == 50 and ad[0].get("bornTime", 0) > 0, ad
    for t in ("letters", "friends", "daily_claims", "farm_data", "players"):
        db(f"DELETE FROM {t} WHERE user_id = {uid}" if t != "players"
           else f"DELETE FROM players WHERE user_id = {uid}")
    db(f"DELETE FROM users WHERE id = {uid}")
    print("PASS TOÀN BỘ — FARM GĐ0")


if __name__ == "__main__":
    main()
