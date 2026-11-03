#!/usr/bin/env python3
"""Test sống NPC LÁI BUÔN (850) với server Java thật (19128).

Luồng gốc: vào park map server 25 (client 26 — đường ra nông trại) ->
tới cạnh lái buôn (70,45) -> gửi -61 (nói chuyện) -> nhận menu -59
[Điểm Danh / Xem hướng dẫn / Thoát] -> chọn 0: server gốc là stub,
trả tin hệ thống -9 "đang xây dựng" -> mở lại menu, chọn 1: nhận tab
thông tin -58 (CUSTOM_TAB: byte 0 + UTF tiêu đề + UTF nội dung).

Chạy: python3 live_laibuon_test.py
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402

FAIL = []
NPC_WIRE = 2000000000 + 850
MAP = 25  # server map; client 26


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


def drain(c: Conn, secs=2.0):
    got = []
    try:
        while True:
            cmd, body = c.recv(timeout=secs)
            got.append((cmd, body))
            secs = 0.6
    except Exception:
        pass
    return got


def login(user="betest01", pw="abcd1234"):
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
    off += 1  # menuId
    n = body[off]
    off += 1
    names = []
    for _ in range(n):
        ln = struct.unpack(">H", body[off:off + 2])[0]
        names.append(read_utf(body, off))
        off += 2 + ln
    return m_uid, names


def open_menu(c):
    # server có nhịp chống spam khi nói chuyện liên tục: chờ một nhịp
    # rồi mới gửi -61; nếu vẫn nhận -10 (dialog) thì in ra để chẩn đoán
    time.sleep(2.5)
    c.send(-61, struct.pack(">i", NPC_WIRE))
    try:
        body, _ = wait_cmd(c, -59, timeout=6.0)
    except AssertionError:
        got = drain(c, 1.0)
        for cmd, b in got:
            if cmd == -10:
                print("    [dialog -10]", read_utf(b, 0)[:100])
        raise
    return parse_menu(body)


def main():
    c, uid = login()
    check("login id = 1000", uid == 1000, str(uid))

    print("[1] join map 25 (server) + đi tới cạnh lái buôn (70,45)")
    body = struct.pack(">b", MAP) + struct.pack(">b", -1) \
        + struct.pack(">h", 100) + struct.pack(">h", 90)
    c.send(50, body)
    wait_cmd(c, 50)
    for x, y in ((80, 60), (72, 52)):
        c.send(54, struct.pack(">hhb", x, y, 0))
        time.sleep(0.15)
    drain(c, 1.0)

    print("[2] nói chuyện -61 -> menu -59")
    m_uid, names = open_menu(c)
    check("menu thuộc lái buôn", m_uid == NPC_WIRE, str(m_uid))
    check("menu đúng 3 mục của gốc",
          names == ["Điểm Danh", "Xem hướng dẫn", "Thoát"], str(names))

    print("[3] chọn 'Điểm Danh' -> tin hệ thống -9 (stub của gốc)")
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 0))
    body, _ = wait_cmd(c, -9)
    msg = read_utf(body, 0)
    check("nội dung là 'đang xây dựng'", msg == "đang xây dựng", msg)

    print("[4] chọn 'Xem hướng dẫn' -> tab -58 CUSTOM_TAB")
    open_menu(c)
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 1))
    body, _ = wait_cmd(c, -58)
    off = 0
    b0 = body[off]
    off += 1
    title = read_utf(body, off)
    off += 2 + struct.unpack(">H", body[off:off + 2])[0]
    content = read_utf(body, off)
    check("byte đầu = 0", b0 == 0, str(b0))
    check("tiêu đề 'Hướng dẫn'", title == "Hướng dẫn", title)
    check("nội dung có 'Đăng nhập mỗi ngày'", "Đăng nhập mỗi ngày" in content,
          content[:40])

    print("[5] chọn 'Thoát' -> server không gửi gì thêm")
    open_menu(c)
    c.send(-59, struct.pack(">ibb", NPC_WIRE, 0, 2))
    got = drain(c, 1.5)
    check("không có gói nào sau Thoát", len(got) == 0, str([g[0] for g in got]))

    c.sock.close()
    print("\nKẾT QUẢ:", "PASS TẤT CẢ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
