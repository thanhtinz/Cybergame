#!/usr/bin/env python3
"""Test sống ĐỢT 4 NHÀ (phía server) với server Java thật (19128).

Luồng:
- betest01: nạp 2.000.000 xu (DB) -> join server map 21 (phố nhà) cạnh
  NPC 857 -> -61 -> menu -59 ["Mua biệt thự"] -> chọn -> dialog -10 mua
  thành công + DB có house_buy type 3.
- Đổi handler -1 {11} (Home) -> -67 {0} -> -67 {0, 6299, type=3}.
- -65 {1000} -> -65 {3, 1000, n=532, map..., w=28, nItem=0}.
- -74 mua nội thất id 3 (giường nệm xanh, 60.000 xu) tại (5,5) type 1
  -> echo -74 + DB house_player_item + xu trừ đúng 60.000 (kiểm sau khi
  ngắt kết nối: 2.000.000 - 1.000.000 - 60.000 = 940.000).
- -71 dời món sang (6,5) rotate 2 -> DB cập nhật. -66 thu hồi -> DB mất.
- betest02 (chưa có nhà): -67 -> type 0; -65 -> dialog -10 "chưa có nhà".

Chạy: python3 live_house_test.py
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402
from live_createchar_test import db  # noqa: E402

FAIL = []
WIRE_857 = 2000000000 + 857


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


def wait_cmd(c: Conn, want, timeout=8.0):
    seen = []
    try:
        while True:
            cmd, body = c.recv(timeout=timeout)
            seen.append(cmd)
            if cmd == want:
                return body, seen
    except TimeoutError:
        raise AssertionError(f"timeout chờ cmd {want}; đã thấy {seen}")


def login(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    body, _ = wait_cmd(c, -4)
    return c, struct.unpack(">i", body[:4])[0]


def parse_menu(body):
    off = 0
    m_uid = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    menu_id = body[off]
    off += 1
    n = body[off]
    off += 1
    names = []
    for _ in range(n):
        ln = struct.unpack(">H", body[off:off + 2])[0]
        names.append(read_utf(body, off))
        off += 2 + ln
    return m_uid, menu_id, names


def read_dialog(body):
    return read_utf(body, 0)


def main():
    print("[0] dọn DB + nạp xu betest01")
    db("DELETE FROM house_buy WHERE user_id IN (1000, 1001)")
    db("DELETE FROM house_player_item WHERE user_id IN (1000, 1001)")
    db("UPDATE players SET xu = 2000000 WHERE user_id = 1000")

    c, uid = login("betest01", "abcd1234")
    check("login betest01 uid=1000", uid == 1000, str(uid))

    print("[1] join map 21 cạnh Chủ đầu tư (598,24) + mở menu 857")
    body = struct.pack(">b", 21) + struct.pack(">b", -1) \
        + struct.pack(">h", 595) + struct.pack(">h", 28)
    c.send(50, body)
    wait_cmd(c, 50)
    time.sleep(2.5)  # chống spam -61
    c.send(-61, struct.pack(">i", WIRE_857))
    m_uid, m_id, names = parse_menu(wait_cmd(c, -59)[0])
    check("menu 857 = [Mua biệt thự]", names == ["Mua biệt thự"], str(names))

    print("[2] chọn Mua biệt thự -> dialog thành công + DB")
    c.send(-59, struct.pack(">ibb", WIRE_857, m_id, 0))
    dlg = read_dialog(wait_cmd(c, -10)[0])
    check("dialog mua nhà thành công", "thành công" in dlg, dlg)
    row = db("SELECT type FROM house_buy WHERE user_id = 1000")
    check("DB house_buy type 3", row == "3", row)

    print("[3] handler Home: -67 -> type 3; -65 -> dữ liệu nhà")
    c.send(-1, struct.pack(">b", 11))
    time.sleep(0.5)
    c.send(-67, struct.pack(">b", 0))
    b = wait_cmd(c, -67)[0]
    check("-67 format {0, 6299, 3}",
          b[0] == 0 and struct.unpack(">h", b[1:3])[0] == 6299 and b[3] == 3,
          str(list(b[:4])))
    c.send(-65, struct.pack(">i", 1000))
    b = wait_cmd(c, -65)[0]
    htype = b[0]
    owner = struct.unpack(">i", b[1:5])[0]
    n = struct.unpack(">h", b[5:7])[0]
    w = b[7 + n]
    n_item = struct.unpack(">h", b[8 + n:10 + n])[0]
    check("-65 houseType 3", htype == 3, str(htype))
    check("-65 id người vào = 1000", owner == 1000, str(owner))
    check("-65 map 532 ô, w=28, 0 nội thất", n == 532 and w == 28 and n_item == 0,
          f"n={n} w={w} items={n_item}")

    print("[4] -74 mua giường (id 3, 60.000 xu) tại (5,5)")
    c.send(-74, struct.pack(">hbbb", 3, 5, 5, 1))
    b = wait_cmd(c, -74)[0]
    check("-74 echo {3,5,5}", struct.unpack(">h", b[:2])[0] == 3 and b[2] == 5 and b[3] == 5,
          str(list(b[:4])))
    time.sleep(0.5)
    row = db("SELECT house_item_id, x, y FROM house_player_item WHERE user_id = 1000")
    check("DB có món 3 tại (5,5)", row.replace("\t", " ") == "3 5 5", row)

    print("[5] -71 dời -> (6,5) rotate 2; -66 thu hồi")
    c.send(-71, struct.pack(">hbbbbb", 3, 5, 5, 6, 5, 2))
    time.sleep(0.7)
    row = db("SELECT x, y, rotate FROM house_player_item WHERE user_id = 1000")
    check("DB món ở (6,5) rotate 2", row.replace("\t", " ") == "6 5 2", row)
    c.send(-66, struct.pack(">hbbb", 3, 6, 5, 2))
    b = wait_cmd(c, -66)[0]
    check("-66 echo {3,6,5}", struct.unpack(">h", b[:2])[0] == 3 and b[2] == 6 and b[3] == 5,
          str(list(b[:4])))
    time.sleep(0.5)
    row = db("SELECT COUNT(*) FROM house_player_item WHERE user_id = 1000")
    check("DB hết nội thất", row == "0", row)

    c.sock.close()
    time.sleep(3)
    xu = db("SELECT xu FROM players WHERE user_id = 1000")
    check("xu sau cùng = 940.000", xu == "940000", xu)

    print("[6] betest02 chưa có nhà: -67 type 0, -65 dialog")
    c2, uid2 = login("betest02", "abcd1234")
    check("login betest02 uid=1001", uid2 == 1001, str(uid2))
    c2.send(-1, struct.pack(">b", 11))
    time.sleep(0.5)
    c2.send(-67, struct.pack(">b", 0))
    b = wait_cmd(c2, -67)[0]
    check("-67 type 0 khi chưa có nhà", b[3] == 0, str(list(b[:4])))
    c2.send(-65, struct.pack(">i", 1001))
    dlg = read_dialog(wait_cmd(c2, -10)[0])
    check("dialog chưa có nhà", "chưa có nhà" in dlg, dlg)
    c2.sock.close()

    print()
    if FAIL:
        print("FAIL:", FAIL)
        sys.exit(1)
    print("ALL PASS — luồng nhà phía server OK")


if __name__ == "__main__":
    main()
