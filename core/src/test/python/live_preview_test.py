# -*- coding: utf-8 -*-
"""Test sống THỬ ĐỒ (gói −100, bản chính APPLY_PREVIEW_ITEM):
A (nam) + B (nữ) cùng vào map 10.
1. A thử 627 (nam) -> cả hai nhận −100 {1000, 627}.
2. A thử món nữ 49 -> bị chặn giới, không ai nhận −100.
3. B thử 49 (nữ) -> cả hai nhận −100 {1001, 49}.
4. B cởi đồ thử (−100 {0}) -> cả hai nhận −100 {1001, 0}.
5. A đang thử 627, A cất đồ thật đang mặc (part 446, gói −48 type 0)
   -> server tự cởi đồ thử: cả hai nhận −100 {1000, 0}.
"""
import struct
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login

PREVIEW = -100
USING_PART = -48


def join_map(c):
    c.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
    end = time.time() + 8
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=2.0)
        except (TimeoutError, OSError):
            break
        if cmd == 50:
            return True
    return False


def wait_pkt(c, cmd_want, timeout=8.0, label=""):
    """Đọc tới khi gặp gói cmd_want; trả body. Các gói khác bỏ qua
    nhưng gói −100/−48 không mong đợi sẽ được ghi lại để kiểm tra."""
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.3, end - time.time()))
        except (TimeoutError, OSError):
            break
        if cmd == cmd_want:
            return body
    raise AssertionError(f"không nhận gói {cmd_want} ({label})")


def expect_preview(c, uid, part, label):
    body = wait_pkt(c, PREVIEW, label=label)
    got_uid, got_part = struct.unpack(">ih", body[:6])
    assert (got_uid, got_part) == (uid, part), \
        f"{label}: nhận ({got_uid},{got_part}) != ({uid},{part})"
    print(f"  ok {label}: −100 {{{uid}, {part}}}")


def expect_no_preview(c, secs=3.0, label=""):
    end = time.time() + secs
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.3, end - time.time()))
        except (TimeoutError, OSError):
            break
        assert cmd != PREVIEW, f"{label}: vẫn nhận −100 {body.hex()}"
    print(f"  ok {label}: không có −100 (bị chặn đúng)")


def main():
    a, _ = login("betest01", "abcd1234")
    time.sleep(3)
    b, _ = login("betest02", "abcd1234")
    time.sleep(2)
    assert join_map(a), "A vào map 10"
    time.sleep(1)
    assert join_map(b), "B vào map 10"
    time.sleep(1)

    print("1) A thử món nam 627")
    a.send(PREVIEW, struct.pack(">h", 627))
    expect_preview(a, 1000, 627, "A tự nhận")
    expect_preview(b, 1000, 627, "B thấy A thử")

    print("2) A thử món nữ 49 — chặn giới")
    a.send(PREVIEW, struct.pack(">h", 49))
    expect_no_preview(a, 3.0, "A")
    expect_no_preview(b, 1.0, "B")

    print("3) B thử món nữ 49")
    b.send(PREVIEW, struct.pack(">h", 49))
    expect_preview(a, 1001, 49, "A thấy B thử")
    expect_preview(b, 1001, 49, "B tự nhận")

    print("4) B cởi đồ thử")
    b.send(PREVIEW, struct.pack(">h", 0))
    expect_preview(a, 1001, 0, "A nhận cởi")
    expect_preview(b, 1001, 0, "B nhận cởi")

    print("5) A đang thử 627 -> cất đồ thật part 446 -> tự cởi đồ thử")
    a.send(PREVIEW, struct.pack(">h", 627))
    expect_preview(a, 1000, 627, "A thử lại")
    expect_preview(b, 1000, 627, "B thấy A thử lại")
    a.send(USING_PART, struct.pack(">hb", 446, 0))
    # thứ tự: −48 (cất) và −100 {1000, 0} (tự cởi thử) đều phải tới
    got = set()
    end = time.time() + 8
    while time.time() < end and len(got) < 2:
        try:
            cmd, body = a.recv(timeout=2.0)
        except (TimeoutError, OSError):
            break
        if cmd == USING_PART:
            uid, pid = struct.unpack(">ih", body[:6])
            assert (uid, pid) == (1000, 446), (uid, pid)
            got.add("using")
        elif cmd == PREVIEW:
            uid, pid = struct.unpack(">ih", body[:6])
            assert (uid, pid) == (1000, 0), (uid, pid)
            got.add("clear")
    assert got == {"using", "clear"}, got
    print("  ok A: cất đồ thật -> đồ thử tự cởi")

    a.sock.close()
    b.sock.close()
    time.sleep(2)
    print("ALL PASS")


if __name__ == "__main__":
    main()
