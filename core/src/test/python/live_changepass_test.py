#!/usr/bin/env python3
"""Test sống ĐỔI MẬT KHẨU (gói −62) trên server thật.

Luồng: đăng nhập betest02 -> đổi mật khẩu sai cũ (phải bị từ chối) ->
đổi đúng (phải nhận gói −62 + dialog thành công) -> đăng nhập lại bằng
mật khẩu MỚI (phải được), mật khẩu CŨ (phải hỏng) -> đổi ngược về mật
khẩu cũ và kiểm chứng đăng nhập lại được. Mật khẩu test luôn được khôi
phục về abcd1234 ở cuối.

Chạy: python3 live_changepass_test.py   (server game phải đang chạy)
"""
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402

FAIL = []
USER = "betest02"
OLD = "abcd1234"
NEW = "newpass99"


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


def collect(c, seconds=3.0):
    """Đọc mọi gói tới trong tối đa `seconds`, trả list (cmd, body)."""
    out = []
    try:
        while True:
            cmd, body = c.recv(timeout=seconds)
            out.append((cmd, body))
            seconds = 1.2
    except Exception:
        pass
    return out


def texts(pkts):
    return [read_utf(b) for cmd, b in pkts if cmd in (-9, -10) and len(b) >= 2]


def login(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    pkts = collect(c)
    ok = any(cmd == -4 for cmd, _ in pkts)
    return c, ok, pkts


def change_pass(c, old, new):
    c.send(-62, write_utf(old) + write_utf(new))
    return collect(c)


def close(c):
    try:
        c.sock.close()
    except Exception:
        pass
    time.sleep(1.0)


def main():
    print("[1] Đăng nhập bằng mật khẩu hiện tại")
    c, ok, _ = login(USER, OLD)
    check("đăng nhập được", ok)
    if not ok:
        print("KẾT QUẢ: FAIL (không đăng nhập được)")
        return 1

    print("[2] Đổi với mật khẩu cũ SAI -> phải bị từ chối")
    pkts = change_pass(c, "sai-pass-123", NEW)
    check("không nhận gói −62 xác nhận", not any(cmd == -62 for cmd, _ in pkts))
    check("dialog báo mật khẩu cũ không đúng",
          any("không đúng" in t for t in texts(pkts)), f"texts={texts(pkts)}")

    print("[3] Đổi với mật khẩu cũ ĐÚNG -> phải thành công")
    pkts = change_pass(c, OLD, NEW)
    rep = [b for cmd, b in pkts if cmd == -62]
    check("nhận gói −62 xác nhận", len(rep) == 1)
    if rep:
        check("gói −62 mang mật khẩu mới", read_utf(rep[0]) == NEW,
              f"body={read_utf(rep[0])!r}")
    check("dialog báo đổi mật khẩu thành công",
          any("thành công" in t for t in texts(pkts)), f"texts={texts(pkts)}")
    close(c)

    print("[4] Đăng nhập lại: mật khẩu MỚI được, mật khẩu CŨ hỏng")
    c2, ok_new, _ = login(USER, NEW)
    check("đăng nhập bằng mật khẩu mới", ok_new)
    c3, ok_old, _ = login(USER, OLD)
    check("đăng nhập bằng mật khẩu cũ bị từ chối", not ok_old)
    close(c3)

    print("[5] Đổi ngược về mật khẩu cũ và kiểm chứng")
    if ok_new:
        pkts = change_pass(c2, NEW, OLD)
        check("đổi ngược thành công",
              any(cmd == -62 for cmd, _ in pkts)
              and any("thành công" in t for t in texts(pkts)),
              f"texts={texts(pkts)}")
    close(c2)
    c4, ok_back, _ = login(USER, OLD)
    check("đăng nhập lại bằng mật khẩu cũ", ok_back)
    close(c4)

    print("KẾT QUẢ:", "PASS toàn bộ" if not FAIL else f"FAIL: {FAIL}")
    return 0 if not FAIL else 1


if __name__ == "__main__":
    sys.exit(main())
