#!/usr/bin/env python3
"""Test sống Vòng quay may mắn (DialLucky) với server Java thật (19128).

Luồng gốc: NPC 185 "quay số" ở server map 9 (514,48) -> nói chuyện -61
-> menu -59 [Quay số / Xem hướng dẫn / Thoát] -> chọn "Quay số" ->
menu con [5 lượng / 25.000 xu / Q.S miễn phí / Thoát] -> chọn
"25.000 xu" -> server gửi OPEN_SHOP -49 {byte 100, UTF, short n,
n x short idPart} -> client gửi DIAL_LUCKY -64 {short partId,
short lực} -> server phát -64 {int uid, short lực, byte n=3, quà x3}
và trừ 25.000 xu (+ quà xu nếu trúng), quà đồ vào rương.

Chạy: python3 live_diallucky_test.py   (cwd = thư mục này)
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402
from live_createchar_test import db  # noqa: E402

FAIL = []
NPC_WIRE = 2000000000 + 185
MAP = 9  # server map của NPC quay số


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name
          + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


class Rd:
    def __init__(self, b):
        self.b = b
        self.o = 0

    def byte(self):
        v = self.b[self.o]
        self.o += 1
        return v - 256 if v > 127 else v

    def int(self):
        v = struct.unpack(">i", self.b[self.o:self.o + 4])[0]
        self.o += 4
        return v

    def short(self):
        v = struct.unpack(">h", self.b[self.o:self.o + 2])[0]
        self.o += 2
        return v

    def utf(self):
        ln = struct.unpack(">H", self.b[self.o:self.o + 2])[0]
        self.o += 2
        s = self.b[self.o:self.o + ln].decode("utf-8", "replace")
        self.o += ln
        return s


def wait_cmd(c, want, timeout=9.0, label=""):
    end = time.time() + timeout
    seen = []
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.5, end - time.time()))
        except Exception:
            break
        seen.append(cmd)
        if cmd == want:
            return body
        if cmd == -10:
            print(f"    [{label}] dialog server: {read_utf(body)!r}")
    raise AssertionError(f"[{label}] không nhận được cmd {want}; đã thấy {seen}")


def login(user="betest01", pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    body = wait_cmd(c, -4, label="login")
    uid = struct.unpack(">i", body[:4])[0] if len(body) >= 4 else -1
    return c, uid


def parse_menu(body):
    r = Rd(body)
    uid = r.int()
    r.byte()  # menuId
    n = r.byte()
    names = [r.utf() for _ in range(n)]
    return uid, names


def main():
    # dọn rương + nạp lại xu cho chắc chắn qua được cổng của handleDiaLucky
    db("UPDATE players SET chests='[]' WHERE user_id=1000")
    db("UPDATE players SET xu=200000 WHERE user_id=1000")
    xu0 = int(db("SELECT xu FROM players WHERE user_id=1000").split("\t")[0])

    c, uid = login()
    check("login betest01", uid == 1000, str(uid))

    print("[1] join server map 9 + tới cạnh NPC 185 (514,48)")
    body = struct.pack(">b", MAP) + struct.pack(">b", -1) \
        + struct.pack(">h", 500) + struct.pack(">h", 90)
    c.send(50, body)
    wait_cmd(c, 50, label="join")
    c.send(54, struct.pack(">hhb", 508, 55, 0))
    time.sleep(0.3)

    print("[2] nói chuyện -61 -> menu -59 cấp 1")
    c.send(-61, struct.pack(">i", NPC_WIRE))
    m_uid, names = parse_menu(wait_cmd(c, -59, label="menu1"))
    check("menu thuộc NPC 185", m_uid == NPC_WIRE, str(m_uid))
    check("menu cấp 1 đúng gốc",
          names == ["Quay số", "Xem hướng dẫn", "Thoát"], str(names))

    print("[3] chọn 'Quay số' -> menu con các hình thức trả")
    time.sleep(2.5)  # chống dialog spam của server giữa hai lần -59
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 0))
    _, names2 = parse_menu(wait_cmd(c, -59, label="menu2"))
    check("menu con đúng gốc",
          names2 == ["5 lượng", "25.000 xu", "Q.S miễn phí", "Thoát"],
          str(names2))

    print("[4] chọn '25.000 xu' -> OPEN_SHOP -49 idShop=100")
    time.sleep(2.5)
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 1, 1))
    r = Rd(wait_cmd(c, -49, label="prizes"))
    shop_id = r.byte()
    title = r.utf()
    n = r.short()
    prizes = [r.short() for _ in range(n)]
    check("idShop = 100", shop_id == 100, str(shop_id))
    check("tiêu đề 'Quay số'", title == "Quay số", title)
    check("danh sách quà có món", n >= 1, f"n={n}")
    print(f"    pool quà: {prizes[:12]}{'...' if n > 12 else ''}")

    print("[5] quay -64 {partId, lực} -> broadcast -64 với 3 quà")
    pick = prizes[0]
    c.send(-64, struct.pack(">hh", pick, 90))
    r = Rd(wait_cmd(c, -64, label="spin"))
    s_uid = r.int()
    power = r.short()
    gn = r.byte()
    gifts = []
    for _ in range(gn):
        t = r.byte()
        if t == 1:
            gifts.append((1, r.short(), r.byte()))
        elif t in (2, 3, 4):
            gifts.append((t, r.int()))
        else:
            gifts.append((t,))
    check("broadcast là của mình", s_uid == 1000, str(s_uid))
    check("lực echo = 90", power == 90, str(power))
    check("đúng 3 quà", gn == 3, str(gifts))
    xu_gift = sum(g[1] for g in gifts if g[0] == 2)
    item_gifts = [g for g in gifts if g[0] == 1]
    print(f"    quà: {gifts} (xu quà={xu_gift}, đồ={len(item_gifts)})")

    c.sock.close()
    time.sleep(3)  # server lưu DB khi ngắt kết nối

    print("[6] đối chiếu DB sau khi lưu")
    xu1 = int(db("SELECT xu FROM players WHERE user_id=1000").split("\t")[0])
    check("xu = trước - 25.000 + quà xu", xu1 == xu0 - 25000 + xu_gift,
          f"{xu0} -> {xu1}, quà xu {xu_gift}")
    chests = db("SELECT chests FROM players WHERE user_id=1000")
    import json
    n_chest = len(json.loads(chests)) if chests and chests != "NULL" else 0
    check("quà đồ vào rương", n_chest == len(item_gifts),
          f"rương có {n_chest}, quà đồ {len(item_gifts)}")

    print("\nKẾT QUẢ:", "PASS TẤT CẢ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
