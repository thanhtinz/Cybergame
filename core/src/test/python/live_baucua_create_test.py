#!/usr/bin/env python3
"""Test sống Bầu Cua — TỰ TẠO BÀN [PORT]: client gửi JOIN_BOARD (8) với
boardID = -1 -> server cấp bàn mới (id >= 6) và cho người tạo vào ghế 0
làm chủ bàn; người thứ hai thấy bàn mới trong REQUEST_BOARDLIST (7) và
vào được; cả hai rời -> bàn tự tạo trống bị dọn khỏi danh sách.

Chạy: python3 live_baucua_create_test.py   (cwd = thư mục này)
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_baucua_test import Rd, login, wait_cmd  # noqa: E402

FAIL = []
ROOM = 43


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name
          + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


def board_list(c, label):
    c.send(7, bytes([ROOM]))
    r = Rd(wait_cmd(c, 7, label=label))
    assert r.byte() == ROOM
    boards = []
    while r.rest() > 0:
        bid = r.byte()
        packed = r.ubyte()
        flags = r.ubyte()
        money = r.int()
        boards.append((bid, packed % 16, packed // 16, flags, money))
    return boards


def enter_casino(c, label):
    c.send(-1, bytes([3]))
    c.send(61, bytes([22]))
    body = wait_cmd(c, 61, label=label)
    assert body == bytes([22])
    c.send(6)
    wait_cmd(c, 6, label=label)


def join_board(c, board_id, label):
    body = bytes([ROOM, board_id & 0xFF]) + struct.pack(">H", 0)
    c.send(8, body)
    r = Rd(wait_cmd(c, 8, label=label))
    room = r.byte()
    bid = r.byte()
    owner = r.int()
    r.int()  # board money
    seats = []
    for _ in range(5):
        sid = r.int()
        if sid == -1:
            seats.append(None)
        else:
            name = r.utf()
            r.int()   # money
            nw = r.byte()
            for _ in range(nw):
                r.short()
            r.int()   # exp
            r.bool()  # isReady
            r.short()  # idImg
            seats.append((sid, name))
    return room, bid, owner, seats


def leave_board(c, board_id):
    c.send(15, bytes([ROOM, board_id & 0xFF]))
    time.sleep(0.4)


def main():
    a = login("betest01")
    b = login("betest02")
    enter_casino(a, "A")
    enter_casino(b, "B")

    print("[1] danh sách bàn ban đầu: 6 bàn gốc")
    boards0 = board_list(a, "A-list0")
    check("6 bàn gốc", len(boards0) == 6, str([x[0] for x in boards0]))

    print("[2] A tạo bàn mới (JOIN_BOARD id=-1)")
    room, new_id, owner, seats = join_board(a, -1, "A-create")
    check("vào đúng phòng", room == ROOM, str(room))
    check("bàn mới có id >= 6", new_id >= 6, str(new_id))
    check("A là chủ bàn", owner == 1000, str(owner))
    check("A ngồi ghế 0", seats[0] is not None and seats[0][0] == 1000,
          str(seats))

    print("[3] B thấy bàn mới trong danh sách và vào được ghế 1")
    boards1 = board_list(b, "B-list1")
    ids = [x[0] for x in boards1]
    check("danh sách có bàn mới", new_id in ids, str(ids))
    nb = [x for x in boards1 if x[0] == new_id][0]
    check("bàn mới báo 1 người", nb[1] == 1, str(nb))
    _, bid2, owner2, seats2 = join_board(b, new_id, "B-join")
    check("B vào đúng bàn mới", bid2 == new_id, str(bid2))
    check("chủ bàn vẫn là A", owner2 == 1000, str(owner2))
    check("B ngồi ghế 1", seats2[1] is not None and seats2[1][0] == 1001,
          str(seats2))

    print("[4] cả hai rời -> bàn tự tạo bị dọn khỏi danh sách")
    leave_board(b, new_id)
    leave_board(a, new_id)
    time.sleep(0.5)
    boards2 = board_list(a, "A-list2")
    ids2 = [x[0] for x in boards2]
    check("bàn tự tạo đã biến mất", new_id not in ids2, str(ids2))
    check("về lại 6 bàn gốc", len(boards2) == 6, str(ids2))

    a.sock.close()
    b.sock.close()
    print("\nKẾT QUẢ:", "PASS TẤT CẢ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
