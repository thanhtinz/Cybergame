#!/usr/bin/env python3
"""[PORT] Bảng Tài Xỉu −124: mở từ menu NPC Thần Tài 864 (mục đầu),
xin snapshot trực tiếp, cược thử 1 ván qua menu rồi kiểm pool/tôi."""
import struct
import time

from live_bossshop_test import Rd, join, login, wait_cmd
from live_createchar_test import db

TX = -124
NPC_WIRE = 2000000000 + 864


def parse2(body):
    import io
    bio = io.BytesIO(body)

    def rd(fmt):
        n = struct.calcsize(fmt)
        return struct.unpack(fmt, bio.read(n))[0]

    def utf():
        n = rd(">H")
        return bio.read(n).decode("utf-8")

    assert rd(">b") == 10
    d = {"round": rd(">i"), "secs": rd(">h"),
         "pools": [rd(">q") for _ in range(4)],
         "mine": [rd(">i") for _ in range(4)]}
    nh = rd(">b")
    d["hist"] = [(rd(">i"), utf()) for _ in range(nh)]
    for key in ("topXu", "topLuong"):
        n = rd(">b")
        d[key] = [(utf(), rd(">q")) for _ in range(n)]
    d["myBets"] = rd(">i")
    d["myWins"] = rd(">i")
    d["netXu"] = rd(">q")
    d["netLuong"] = rd(">q")
    return d


def main():
    xu0 = int(db("SELECT xu FROM players WHERE user_id = 1000").strip())
    c, uid = login("betest01")
    d = parse2(_snap(c))
    assert d["round"] > 0, d
    print(f"PASS snapshot trực tiếp: phiên #{d['round']} còn {d['secs']}s,"
          f" pool={d['pools']}, hist={len(d['hist'])} phiên")

    # mở menu NPC 864 -> mục đầu là Bảng Tài Xỉu
    join(c, 11, 60, 60)
    c.q = []
    c.send(-61, struct.pack(">i", NPC_WIRE))
    r = Rd(wait_cmd(c, -59, label="menu 864"))
    r.int(); r.byte()
    n = r.ubyte()
    names = [r.utf() for _ in range(n)]
    assert names[0] == "Bảng Tài Xỉu", names
    c.q = []
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 0))
    d2 = parse2(wait_cmd(c, TX, label="snapshot qua menu"))
    assert d2["round"] == d["round"] or d2["round"] == d["round"] + 1, d2
    print(f"PASS mở bảng từ menu NPC: {names}")

    # cược thử: nếu sắp hết phiên thì chờ phiên mới
    if d2["secs"] <= 8:
        time.sleep(d2["secs"] + 7)
        d2 = parse2(_snap(c))
    c.q = []
    c.send(-61, struct.pack(">i", NPC_WIRE))
    Rd(wait_cmd(c, -59, label="menu 864 (2)"))
    time.sleep(0.5)
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 1))  # Cược bằng Xu
    r = Rd(wait_cmd(c, -59, label="submenu Xu"))
    r.int(); r.byte()
    n = r.ubyte()
    subs = [r.utf() for _ in range(n)]
    assert subs and "Tài" in subs[0], subs
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 0))  # Cược Tài (Xu)
    time.sleep(0.8)
    c.q = []
    c.send(-60, struct.pack(">i", uid) + bytes([0]) + _utf("10000"))
    time.sleep(1.0)
    d3 = parse2(_snap(c))
    assert d3["mine"][0] >= 10000, f"cược của tôi không lên bảng: {d3['mine']}"
    assert d3["pools"][0] >= 10000, f"pool Tài Xu không tăng: {d3['pools']}"
    print(f"PASS cược Tài 10.000 xu hiện trên bảng: mine={d3['mine']}")

    try:
        c.sock.close()
    except Exception:
        pass
    time.sleep(1.5)
    db("DELETE FROM betgame WHERE user_id = 1000")
    db("UPDATE players SET xu = %d WHERE user_id = 1000" % xu0)
    print("ALL PASS")


def _snap(c):
    c.q = []
    c.send(TX, bytes([0]))
    return wait_cmd(c, TX, label="snapshot xin lại")


def _utf(s):
    b = s.encode()
    return struct.pack(">H", len(b)) + b


if __name__ == "__main__":
    main()
