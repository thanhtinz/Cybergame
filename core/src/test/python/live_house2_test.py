#!/usr/bin/env python3
"""Test sống ĐỢT 4 NHÀ phần 2: rương trong nhà + nâng cấp rương + thoát nhà.

Tiền điều kiện: live_house_test.py đã chạy (betest01 có nhà type 3,
xu 940.000). Test này:
- DB: đặt chests của betest01 = [part 89], lượng = 100, chest_home_slot = 10.
- Login -> handler 11 -> -67 -> -65 vào nhà.
- -87 (rương): parse 2 list: list1 có part 89, level byte = 1,
  đuôi mở rộng: bagSlot + homeSlot = 10.
- -89 {0, 0, 89}: giỏ -> rương nhà; chờ -89 {true}; -87 lại: part 89
  đã sang list2.
- -90 {0}: nhận -90 {0, UTF} chứa "20" (giá 20.000 xu / 20 lượng theo
  bảng HomeService cấp 1 -> 2). -90 {1}: dialog -10 thành công; ngắt
  kết nối -> DB: chest_home_slot = 15, xu = 920.000, lượng = 80.
- Thoát nhà: -1 {8} + join park 50 {21} -> nhận 50 (về phố nhà OK).

Chạy: python3 live_house2_test.py
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402
from live_createchar_test import db  # noqa: E402
from live_house_test import check, wait_cmd, login, FAIL  # noqa: E402


def parse_chest(body):
    off = 0
    n1 = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    list1 = []
    for _ in range(n1):
        pid = struct.unpack(">h", body[off:off + 2])[0]
        off += 2
        off += 1  # reliability byte
        ln = struct.unpack(">H", body[off:off + 2])[0]
        read_utf(body, off)
        off += 2 + ln
        list1.append(pid)
    off += 4  # moneyOnChest
    level = body[off]
    off += 1
    n2 = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    list2 = []
    for _ in range(n2):
        pid = struct.unpack(">h", body[off:off + 2])[0]
        off += 2
        off += 1
        ln = struct.unpack(">H", body[off:off + 2])[0]
        read_utf(body, off)
        off += 2 + ln
        list2.append(pid)
    tail = body[off:off + 2]
    return list1, level, list2, tail


def main():
    print("[0] DB: chests=[89], lượng=100, chest_home_slot=10 cho betest01")
    db("UPDATE players SET chests = '[{\"id\": 89, \"expired\": -1}]', "
       "chests_home = '[]', xu = 940000, luong = 100, chest_home_slot = 10 "
       "WHERE user_id = 1000")

    c, uid = login("betest01", "abcd1234")
    check("login betest01", uid == 1000, str(uid))

    print("[1] vào nhà: join park 21 -> -1 {11} -> -67 -> -65")
    body = struct.pack(">b", 21) + struct.pack(">b", -1) \
        + struct.pack(">h", 600) + struct.pack(">h", 40)
    c.send(50, body)
    wait_cmd(c, 50)
    c.send(-1, struct.pack(">b", 11))
    time.sleep(0.5)
    c.send(-67, struct.pack(">b", 0))
    wait_cmd(c, -67)
    c.send(-65, struct.pack(">i", 1000))
    b = wait_cmd(c, -65)[0]
    check("-65 vào nhà OK", b[0] == 3, str(b[0]))

    print("[2] -87 mở rương trong nhà")
    c.send(-87, b"")
    list1, level, list2, tail = parse_chest(wait_cmd(c, -87)[0])
    check("list1 (giỏ) có part 89", list1 == [89], str(list1))
    check("level rương = 1", level == 1, str(level))
    check("list2 (rương nhà) trống", list2 == [], str(list2))
    check("đuôi: bagSlot/homeSlot đọc được", len(tail) == 2 and tail[1] == 10,
          str(list(tail)))

    print("[3] -89 chuyển 89 từ giỏ sang rương nhà")
    c.send(-89, struct.pack(">bhh", 0, 0, 89))
    b = wait_cmd(c, -89)[0]
    check("-89 trả ok=true", b[0] == 1, str(list(b[:3])))
    c.send(-87, b"")
    list1, level, list2, tail = parse_chest(wait_cmd(c, -87)[0])
    check("sau chuyển: giỏ trống, rương có 89", list1 == [] and list2 == [89],
          f"{list1} / {list2}")

    print("[4] -90 nâng cấp rương: hỏi -> xác nhận")
    c.send(-90, struct.pack(">b", 0))
    b = wait_cmd(c, -90)[0]
    idx = b[0]
    q = read_utf(b, 1)
    check("-90 hỏi: idx 0 + câu hỏi có giá 20000/20",
          idx == 0 and "20000" in q and "20 lượng" in q, q)
    c.send(-90, struct.pack(">b", 1))
    dlg = read_utf(wait_cmd(c, -10)[0], 0)
    check("dialog nâng cấp thành công", "thành công" in dlg, dlg)

    print("[5] thoát nhà: -1 {8} + join park 50 {21}")
    c.send(-1, struct.pack(">b", 8))
    time.sleep(0.5)
    body = struct.pack(">b", 21) + struct.pack(">b", -1) \
        + struct.pack(">h", 600) + struct.pack(">h", 40)
    c.send(50, body)
    wait_cmd(c, 50)
    check("join lại phố nhà OK", True)

    c.sock.close()
    time.sleep(3)
    row = db("SELECT chest_home_slot, xu, luong FROM players WHERE user_id = 1000")
    check("DB: slot 15, xu 920000, lượng 80",
          row.replace("\t", " ") == "15 920000 80", row)

    print()
    if FAIL:
        print("FAIL:", FAIL)
        sys.exit(1)
    print("ALL PASS — rương trong nhà + nâng cấp + thoát nhà OK")


if __name__ == "__main__":
    main()
