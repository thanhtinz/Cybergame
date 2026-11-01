#!/usr/bin/env python3
"""Test sống gói 61 VOICE_PARK (tin nhắn thoại, mở rộng của bản port).

Kịch bản: A (betest01) và B (betest02) cùng vào map 11 khu 0. A gửi một
clip thoại giả gồm 3 mẩu (clipId 777, dữ liệu μ-law là dãy byte xác định).
B phải nhận đủ 3 gói 61 {int idA, int clipId, short seq, short total,
byte[] data} và ráp lại đúng nguyên văn; A cũng nhận echo của chính mình.
"""
import struct
import sys

sys.path.insert(0, ".")
from live_khu_test import register, recv_until, join  # noqa: E402
from live_feel_test import login_capture  # noqa: E402


def recv_voice(c, want_sender, clip_id, total):
    chunks = {}
    for _ in range(40):
        cmd, body = c.recv(timeout=5.0)
        if cmd != 61:
            print(f"    (bỏ qua cmd {cmd}, {len(body)} bytes)")
            continue
        sender, cid, seq, tot = struct.unpack(">iihh", body[:12])
        data = body[12:]
        print(f"    nhận mẩu seq={seq}/{tot} từ id={sender}, {len(data)} bytes")
        assert sender == want_sender and cid == clip_id and tot == total
        chunks[seq] = data
        if len(chunks) == total:
            return b"".join(chunks[i] for i in range(total))
    raise AssertionError("không nhận đủ mẩu thoại")


def main():
    print("[0] đăng ký betest01 + betest02")
    register("betest01", "abcd1234")
    register("betest02", "abcd1234")

    print("[1] A và B cùng vào map 11 khu 0")
    a, a_id = login_capture("betest01", "abcd1234")
    join(a, 0)
    b, b_id = login_capture("betest02", "abcd1234")
    join(b, 0)
    print(f"  A id = {a_id}, B id = {b_id}")

    clip = bytes((i * 37 + 11) % 256 for i in range(10000))  # μ-law giả
    parts = [clip[:4000], clip[4000:8000], clip[8000:]]
    print(f"[2] A gửi clip 777 gồm {len(parts)} mẩu, tổng {len(clip)} bytes")
    for seq, part in enumerate(parts):
        body = struct.pack(">ihh", 777, seq, len(parts)) + part
        a.send(61, body)

    print("[3] B nhận + ráp lại")
    got = recv_voice(b, a_id, 777, len(parts))
    assert got == clip, "dữ liệu ráp lại lệch!"
    print("  B ráp đúng nguyên văn", len(got), "bytes")

    print("[4] A nhận echo của chính mình")
    got_a = recv_voice(a, a_id, 777, len(parts))
    assert got_a == clip
    print("  echo đúng nguyên văn")

    a.sock.close()
    b.sock.close()
    print("VOICE TEST PASS")


if __name__ == "__main__":
    main()
