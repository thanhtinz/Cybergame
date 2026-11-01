#!/usr/bin/env python3
"""Test sống chuỗi SHOP qua NPC Cửa Hàng với server Java thật (19128).

Điều kiện: MariaDB đã áp tools/fix-expired-day.sql (1.432 món có hạn);
betest01 (id 1000) seed xu = 20.000, lượng = 500.

Luồng đúng gốc: đăng nhập -> join map 27 (server; client 28 — nơi NPC
cua.hang đứng ở 334,72) -> đi tới cạnh NPC -> gửi -61 (nói chuyện) ->
nhận menu -59 (3 mục, mục đầu "Mua sắm") -> chọn mục 0 -> nhận -49
(124 món sell 8/14) -> mua 302 (hoa cưới, 15 Lượng, hạn 30 ngày) bằng
type 2 -> mua tiếp 494 (bán cả hai: 50.000 Xu / 10 Lượng) bằng type 2:
bản port đã sửa phải trừ 10 Lượng và KHÔNG đụng Xu (code Pack5 gốc sẽ
trừ Xu trước và báo không đủ vì chỉ có 20.000) -> ngắt kết nối cho
server lưu -> kiểm DB: tiền + wearing + hạn dùng ≈ now + 30 ngày.

Chạy: python3 live_shop_test.py
"""
import json
import os
import struct
import subprocess
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402

FAIL = []
NPC_WIRE = 2000000000 + 761
MAP = 27


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
            timeout = 2.0
    except Exception:
        raise AssertionError(f"không nhận được cmd {want}; đã thấy {seen}")


def login(user="betest01", pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    body, _ = wait_cmd(c, -4)
    uid = struct.unpack(">i", body[:4])[0]
    return c, uid


def sql(q):
    env = dict(os.environ, LD_LIBRARY_PATH="/home/hatch/workspace/tools/mariadb/lib")
    out = subprocess.run(
        ["/home/hatch/workspace/tools/mariadb/bin/mysql",
         "--socket=/run/mysqld/mysqld.sock", "-u", "root",
         "-pavatardk-local", "-N", "-e", q, "avatar_2x"],
        capture_output=True, text=True, env=env)
    return out.stdout.strip()


def main():
    print("[0] trạng thái DB trước test")
    before = sql("SELECT xu, luong FROM players WHERE user_id = 1000").split("\t")
    check("seed xu=20000, luong=500", before == ["20000", "500"], str(before))

    c, uid = login()
    check("login id = 1000", uid == 1000, str(uid))

    print("[1] join map 27 + đi tới cạnh NPC Cửa Hàng (334,72)")
    body = struct.pack(">b", MAP) + struct.pack(">b", -1) \
        + struct.pack(">h", 300) + struct.pack(">h", 120)
    c.send(50, body)
    wait_cmd(c, 50)
    for x, y in ((320, 100), (334, 80)):
        c.send(54, struct.pack(">hhb", x, y, 0))
        time.sleep(0.15)

    print("[2] gửi -61 nói chuyện với NPC -> chờ menu -59")
    c.send(-61, struct.pack(">i", NPC_WIRE))
    body, _ = wait_cmd(c, -59)
    off = 0
    m_uid = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    m_mid = body[off]
    off += 1
    n = body[off]
    off += 1
    names = []
    for _ in range(n):
        ln = struct.unpack(">H", body[off:off + 2])[0]
        names.append(read_utf(body, off))
        off += 2 + ln
    check("menu thuộc NPC cửa hàng", m_uid == NPC_WIRE, str(m_uid))
    check("menu có 3 mục", n == 3, str(names))
    check("mục đầu là 'Mua sắm'", bool(names) and names[0] == "Mua sắm", str(names))

    print("[3] chọn mục 0 -> chờ gói mở shop -49")
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 0))
    body, _ = wait_cmd(c, -49)
    off = 0
    off += 1  # idShop
    shop_name = read_utf(body, off)
    off += 2 + struct.unpack(">H", body[off:off + 2])[0]
    cnt = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    ids = [struct.unpack(">h", body[off + 2 * i:off + 2 * i + 2])[0] for i in range(cnt)]
    check("shop mở đúng tên server gửi", shop_name == "shop 0", shop_name)
    check("shop có 124 món (sell 8/14)", cnt == 124, str(cnt))
    check("302 có trong shop", 302 in ids)

    print("[4] mua 302 bằng Lượng (type 2)")
    c.send(-24, struct.pack(">hb", 302, 2))
    body, _ = wait_cmd(c, -24)
    off = 0
    pid = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    price = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    off += 1  # byte 1
    msg = read_utf(body, off)
    off += 2 + struct.unpack(">H", body[off:off + 2])[0]
    xu, luong = struct.unpack(">ii", body[off:off + 8])
    check("mua 302 thành công", pid == 302 and "thành công" in msg, msg)
    check("giá trừ = 15 lượng", price == 15, str(price))
    check("lượng còn 485, xu nguyên 20000", (xu, luong) == (20000, 485), f"{xu}/{luong}")

    print("[5] mua 494 (50.000 Xu / 10 Lượng) bằng type 2 — phép thử quirk")
    c.send(-24, struct.pack(">hb", 494, 2))
    body, _ = wait_cmd(c, -24)
    off = 0
    pid = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    price = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    off += 1
    msg = read_utf(body, off)
    off += 2 + struct.unpack(">H", body[off:off + 2])[0]
    xu, luong = struct.unpack(">ii", body[off:off + 8])
    check("mua 494 thành công bằng Lượng", pid == 494 and "thành công" in msg, msg)
    check("trừ đúng 10 lượng (không trừ xu)", price == 10 and (xu, luong) == (20000, 475),
          f"price={price} xu={xu} luong={luong}")

    print("[6] ngắt kết nối (server lưu khi thoát) rồi kiểm DB")
    c.sock.close()
    time.sleep(3)
    row = sql("SELECT xu, luong, wearing FROM players WHERE user_id = 1000").split("\t")
    check("DB tiền khớp", row[0] == "20000" and row[1] == "475", str(row[:2]))
    wearing = {w["id"]: w["expired"] for w in json.loads(row[2])}
    now_ms = int(time.time() * 1000)
    for pid in (302, 494):
        exp = wearing.get(pid)
        ok = exp is not None and abs(exp - (now_ms + 30 * 86400000)) < 3600000
        check(f"wearing có {pid} với hạn ≈ +30 ngày", ok, f"expired={exp}")

    print("\nKẾT QUẢ:", "PASS TẤT CẢ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
