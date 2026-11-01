#!/usr/bin/env python3
"""Test sống giỏ đồ + rương nhà với server Java thật (127.0.0.1:19128).

Điều kiện: players của betest01 (id 1000) đã seed:
  wearing    = [0, 126]
  chests     = [701, 314, 93(hạn 30 ngày), 98, 670]
  chests_home= [109, 291]
Luồng: đăng nhập -> vào park -> -47 xem giỏ -> -48 mặc 314 -> -48 mặc 93
(trùng zOrder 60, server phải trả 314 về giỏ) -> -48 cất 93 -> -36 xoá 670
-> -87 xem rương -> -89 chuyển 701 giỏ->rương -> -89 chuyển ngược ->
-89 chuyển món có hạn dùng (phải bị từ chối error -003) -> đăng nhập lại
kiểm bền dữ liệu. Chạy: python3 live_bag_chest_test.py
"""
import socket
import struct
import sys

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402

FAIL = []


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


def wait_cmd(c: Conn, want, timeout=8.0):
    """Đọc tới khi gặp cmd mong muốn; trả (cmd, body) của nó."""
    seen = []
    try:
        while True:
            cmd, body = c.recv(timeout=timeout)
            seen.append(cmd)
            if cmd == want:
                return body, seen
            timeout = 2.0
    except (socket.timeout, TimeoutError):
        raise AssertionError(f"không nhận được cmd {want}; đã thấy {seen}")


def parse_items(body, off=0):
    n = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    items = []
    for _ in range(n):
        pid = struct.unpack(">h", body[off:off + 2])[0]
        off += 2
        tm = body[off]
        off += 1
        exp, ln = read_utf(body, off), struct.unpack(">H", body[off:off + 2])[0]
        off += 2 + ln
        items.append((pid, tm, exp))
    return items, off


def login(user="betest01", pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    body, _ = wait_cmd(c, -4)
    uid = struct.unpack(">i", body[:4])[0]
    return c, uid


def get_bag(c, uid):
    c.send(-47, struct.pack(">i", uid))
    body, _ = wait_cmd(c, -47)
    items, off = parse_items(body)
    cap = body[off] if off < len(body) else None
    return items, cap


def main():
    print("[1] Đăng nhập betest01 + vào park")
    c, uid = login()
    check("login id = 1000", uid == 1000, str(uid))
    c.send(50, bytes([0, 255]) + struct.pack(">h", 200) + struct.pack(">h", 150))
    wait_cmd(c, 50)

    print("[2] Gói -47: danh sách giỏ ban đầu")
    items, cap = get_bag(c, uid)
    ids = [i[0] for i in items]
    check("giỏ = [701, 314, 93, 98, 670]", ids == [701, 314, 93, 98, 670], str(ids))
    check("đuôi sức chứa = 10", cap == 10, str(cap))
    exp93 = dict((i[0], i[2]) for i in items).get(93, "")
    check("món 93 có hạn dùng khác vĩnh viễn", exp93 not in ("", "Vĩnh viễn"), exp93)

    print("[3] Gói -48 type 1: mặc 314")
    c.send(-48, struct.pack(">h", 314) + bytes([1]))
    body, _ = wait_cmd(c, -48)
    uid2, pid = struct.unpack(">i", body[:4])[0], struct.unpack(">h", body[4:6])[0]
    check("broadcast -48 (1000, 314)", (uid2, pid) == (1000, 314), f"{uid2},{pid}")
    items, _ = get_bag(c, uid)
    check("314 rời giỏ", [i[0] for i in items] == [701, 93, 98, 670], str([i[0] for i in items]))

    print("[4] Gói -48 type 1: mặc 93 (trùng zOrder 60 với 314)")
    c.send(-48, struct.pack(">h", 93) + bytes([1]))
    wait_cmd(c, -48)
    items, _ = get_bag(c, uid)
    ids = [i[0] for i in items]
    check("93 rời giỏ + 314 bị trả về giỏ", 93 not in ids and 314 in ids, str(ids))

    print("[5] Gói -48 type 0: cất 93")
    c.send(-48, struct.pack(">h", 93) + bytes([0]))
    wait_cmd(c, -48)
    items, _ = get_bag(c, uid)
    check("93 về lại giỏ", 93 in [i[0] for i in items], str([i[0] for i in items]))

    print("[6] Gói -36: xoá 670 khỏi giỏ")
    c.send(-36, struct.pack(">h", 670) + bytes([1]))
    try:
        wait_cmd(c, -36, timeout=4.0)
    except AssertionError:
        print("  (không có broadcast -36 — chấp nhận nếu giỏ đã mất 670)")
    items, _ = get_bag(c, uid)
    check("670 đã mất khỏi giỏ", 670 not in [i[0] for i in items], str([i[0] for i in items]))

    print("[7] Gói -87: dữ liệu rương")
    c.send(-87)
    body, _ = wait_cmd(c, -87)
    con, off = parse_items(body)
    money = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    level = body[off]
    off += 1
    home, off = parse_items(body, off)
    tail = body[off:off + 2]
    check("giỏ (list1) khớp giỏ hiện tại", [i[0] for i in con] == [i[0] for i in items],
          str([i[0] for i in con]))
    check("moneyOnChest = 0", money == 0, str(money))
    check("cấp rương = 1 (10 ô)", level == 1, str(level))
    check("rương = [109, 291]", [i[0] for i in home] == [109, 291], str([i[0] for i in home]))
    check("đuôi sức chứa = (10, 10)", list(tail) == [10, 10], str(list(tail)))

    print("[8] Gói -89 dir 0: chuyển 701 giỏ -> rương")
    c.send(-89, bytes([0]) + struct.pack(">h", 0) + struct.pack(">h", 701))
    body, _ = wait_cmd(c, -89)
    ok = body[0] == 1
    check("server báo thành công", ok, body[:12].hex())
    c.send(-87)
    body, _ = wait_cmd(c, -87)
    con, off = parse_items(body)
    off += 5
    home, off = parse_items(body, off)
    check("701 sang rương", 701 not in [i[0] for i in con] and 701 in [i[0] for i in home],
          f"con={[i[0] for i in con]} home={[i[0] for i in home]}")

    print("[9] Gói -89 dir 1: chuyển 701 rương -> giỏ")
    idx = [i[0] for i in home].index(701)
    c.send(-89, bytes([1]) + struct.pack(">h", idx) + struct.pack(">h", 701))
    body, _ = wait_cmd(c, -89)
    check("server báo thành công", body[0] == 1, body[:12].hex())

    print("[10] Gói -89 chuyển món có hạn dùng 93: phải bị từ chối")
    c.send(-89, bytes([0]) + struct.pack(">h", 0) + struct.pack(">h", 93))
    got_dialog = False
    try:
        body, seen = wait_cmd(c, -10, timeout=5.0)
        got_dialog = "error" in read_utf(body)
    except AssertionError:
        pass
    check("nhận hộp thoại error -003", got_dialog)

    c.sock.close()

    print("[11] Đăng nhập lại: kiểm bền dữ liệu")
    c, uid = login()
    items, _ = get_bag(c, uid)
    ids = [i[0] for i in items]
    check("giỏ sau relogin không còn 670, còn 701/314/93/98",
          670 not in ids and all(x in ids for x in (701, 314, 93, 98)), str(ids))
    c.send(-87)
    body, _ = wait_cmd(c, -87)
    con, off = parse_items(body)
    off += 5
    home, off = parse_items(body, off)
    check("rương sau relogin = [109, 291]", [i[0] for i in home] == [109, 291],
          str([i[0] for i in home]))
    c.sock.close()

    print("KẾT QUẢ:", "FAIL " + str(FAIL) if FAIL else "TẤT CẢ PASS")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
