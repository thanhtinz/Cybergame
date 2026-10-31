#!/usr/bin/env python3
"""Test sống gói 57 AVATAR_FEEL với server Java thật ở 127.0.0.1:19128.

Kịch bản: A (betest01) và B (betest02) cùng vào map 11 khu 0.
A gửi gói 57 với byte 107 (mặt 7 + 100, kiểu client gốc) -> B phải nhận
gói 57 {int idA, byte 107}. A gửi tiếp byte 140 (mặt 40 + 100, trên dây
là byte -116) -> B nhận byte không dấu phải là 140.
"""
import struct
import sys

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register, recv_until, join  # noqa: E402


def login_capture(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    uid = None
    for _ in range(30):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -35:  # tài khoản chưa có nhân vật -> tạo luôn
            c.send(-35, bytes([1, 0]))
        elif cmd == -4:  # LOGIN_SUCESS: int id ở đầu thân gói
            uid = struct.unpack(">i", body[:4])[0]
            break
    assert uid is not None, f"{user}: không nhận được gói -4"
    return c, uid


def main():
    print("[0] đăng ký betest01 + betest02")
    register("betest01", "abcd1234")
    register("betest02", "abcd1234")

    print("[1] A và B cùng vào map 11 khu 0")
    a, a_id = login_capture("betest01", "abcd1234")
    print(f"  A id = {a_id}")
    za = join(a, 0)
    b, b_id = login_capture("betest02", "abcd1234")
    print(f"  B id = {b_id}")
    zb = join(b, 0)
    assert za == 0 and zb == 0, (za, zb)

    print("[2] A gửi biểu cảm mặt 7 (byte 107)")
    a.send(57, bytes([107]))
    body = recv_until(b, 57)
    rid, rfeel = struct.unpack(">i", body[:4])[0], body[4] & 0xFF
    print(f"  B nhận: id={rid}, byte={rfeel}")
    assert rid == a_id and rfeel == 107, (rid, rfeel)

    print("[3] A gửi biểu cảm mặt 40 (byte 140 = -116 trên dây)")
    a.send(57, bytes([140]))  # byte trên dây 140 = -116 theo số có dấu của Java
    body = recv_until(b, 57)
    rid, rfeel = struct.unpack(">i", body[:4])[0], body[4] & 0xFF
    print(f"  B nhận: id={rid}, byte={rfeel}")
    assert rid == a_id and rfeel == 140, (rid, rfeel)

    a.sock.close()
    b.sock.close()
    print("FEEL TEST PASS")


if __name__ == "__main__":
    main()
