#!/usr/bin/env python3
"""Test sống hệ KHU (zone) với server Java thật ở 127.0.0.1:19128.

Kịch bản: A (betest01) và B (betest02, tự đăng ký) cùng đăng nhập.
A join map 11 (server) với khu −1 (tự chọn) -> đọc gói 50 lấy zoneId thật.
B join khu 3 -> zoneId phải là 3. A xin danh sách khu (gói 60) -> 10 khu,
trạng thái hợp lệ. B chuyển khu 5 (vẫn map 11) -> zoneId phải là 5.
"""
import socket
import struct
import sys

sys.path.insert(0, ".")
from live_test import Conn, write_utf, drain  # noqa: E402

MAP = 11  # server map id (client 12: Bến xe công viên)


def login(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    got = drain(c, f"login {user}", seconds=2.0)
    if -35 in got:
        c.send(-35, bytes([1, 0]))
        drain(c, f"create {user}", seconds=2.0)
    return c


def register(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-30, write_utf(user) + write_utf(pw))
    drain(c, f"register {user}", seconds=2.0)
    c.sock.close()


def recv_until(c, want, tries=12):
    for _ in range(tries):
        cmd, body = c.recv(timeout=5.0)
        if cmd == want:
            return body
        print(f"    (bỏ qua cmd {cmd}, {len(body)} bytes)")
    raise AssertionError(f"không thấy gói {want}")


def join(c, area, x=300, y=120):
    body = struct.pack(">b", MAP) + struct.pack(">b", area) \
        + struct.pack(">h", x) + struct.pack(">h", y)
    c.send(50, body)
    resp = recv_until(c, 50)
    map_id, zone_id = struct.unpack(">bb", resp[:2])
    assert map_id == MAP, f"mapId lạ: {map_id}"
    return zone_id


def main():
    print("[0] đăng ký betest01 + betest02 (DB dev mới import lại)")
    register("betest01", "abcd1234")
    register("betest02", "abcd1234")

    print("[1] A đăng nhập, join khu tự chọn (-1)")
    a = login("betest01", "abcd1234")
    za = join(a, -1)
    print(f"  A ở khu {za}")
    assert 0 <= za <= 9

    print("[2] B đăng nhập, join thẳng khu 3")
    b = login("betest02", "abcd1234")
    zb = join(b, 3)
    print(f"  B ở khu {zb}")
    assert zb == 3, f"B phải ở khu 3, nhận {zb}"
    assert zb != za or True  # za có thể trùng 3 nếu server xếp vậy — không bắt buộc

    print("[3] A xin danh sách khu (gói 60)")
    a.send(60, struct.pack(">b", MAP))
    resp = recv_until(a, 60)
    n = resp[0]
    statuses = list(resp[1:1 + n])
    print(f"  số khu: {n}, trạng thái: {statuses}")
    assert n == 10 and len(statuses) == 10
    assert all(s in (0, 1, 2) for s in statuses)
    # B một mình ở khu 3 (NPC của server chỉ nằm ở khu 0) -> "vắng" = 2;
    # khu của A chứa cả NPC phía server nên có thể là 1 (đông) hoặc 2
    assert statuses[3] == 2
    assert statuses[za] in (1, 2)

    print("[4] B chuyển sang khu 5 (vẫn map 11)")
    zb2 = join(b, 5)
    print(f"  B giờ ở khu {zb2}")
    assert zb2 == 5

    a.sock.close()
    b.sock.close()
    print("KHU TEST PASS")


if __name__ == "__main__":
    main()
