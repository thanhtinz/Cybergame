#!/usr/bin/env python3
"""Test sống gói 57 HÀNH ĐỘNG (byte < 100) với server Java thật.

Kịch bản: A (betest01) và B (betest02) cùng vào map 11 khu 0.
A gửi gói 57 byte 3 (Ngồi bệt) -> B phải nhận gói 57 {int idA, byte 3}.
A gửi byte 10 (Nhảy), byte 4 (Nằm), byte 0 (đứng dậy) -> B nhận đủ.
Chiều ngược: B gửi byte 5 (Quỳ) -> A nhận {int idB, byte 5}.
"""
import struct
import sys

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register, recv_until, join  # noqa: E402
from live_feel_test import login_capture  # noqa: E402


def expect_action(dst, src_id, byte, label):
    # server phát cho cả khu KỂ CẢ người gửi: bên nhận còn tồn các gói
    # dội lại của chính mình -> đọc cho tới khi gặp gói của src_id
    for _ in range(12):
        body = recv_until(dst, 57)
        rid, rbyte = struct.unpack(">i", body[:4])[0], body[4] & 0xFF
        if rid == src_id:
            print(f"  nhận {label}: id={rid}, byte={rbyte}")
            assert rbyte == byte, (rid, rbyte, src_id, byte)
            return
        print(f"  (bỏ qua gói dội của id={rid}, byte={rbyte})")
    raise AssertionError(f"không thấy gói 57 từ id={src_id} byte={byte}")


def main():
    print("[0] đăng ký betest01 + betest02")
    register("betest01", "abcd1234")
    register("betest02", "abcd1234")

    print("[1] A và B cùng vào map 11 khu 0")
    a, a_id = login_capture("betest01", "abcd1234")
    za = join(a, 0)
    b, b_id = login_capture("betest02", "abcd1234")
    zb = join(b, 0)
    assert za == 0 and zb == 0, (za, zb)
    print(f"  A id = {a_id}, B id = {b_id}")

    print("[2] A diễn Ngồi bệt (byte 3)")
    a.send(57, bytes([3]))
    expect_action(b, a_id, 3, "Ngồi bệt")

    print("[3] A diễn Nhảy (byte 10) rồi Nằm (byte 4)")
    a.send(57, bytes([10]))
    expect_action(b, a_id, 10, "Nhảy")
    a.send(57, bytes([4]))
    expect_action(b, a_id, 4, "Nằm")

    print("[4] A đứng dậy (byte 0)")
    a.send(57, bytes([0]))
    expect_action(b, a_id, 0, "Đứng dậy")

    print("[5] B diễn Quỳ (byte 5) -> A nhận")
    b.send(57, bytes([5]))
    expect_action(a, b_id, 5, "Quỳ")

    print("PASS live_action_test")


if __name__ == "__main__":
    main()
