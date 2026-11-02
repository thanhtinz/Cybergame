#!/usr/bin/env python3
"""Test sống TẠO NHÂN VẬT (gói -35 CRE_CHARACTER) với server Java thật.

Kịch bản:
 1) crechar01: đăng ký -> đăng nhập, gói -4 (LOGIN_SUCESS) phải có
    gender = 0 (chưa tạo nhân vật). Gửi -35 với bộ đồ TỰ CHỌN (nữ:
    quần/áo/tóc cấp 0 khác mặc định) -> trả lời boolean true. Ngắt,
    chờ lưu DB: players.gender = 2, wearing chứa đúng đồ đã chọn.
    Đăng nhập lại: -4 báo gender 2 + wearing đúng bộ đã chọn.
 2) crechar02: đăng nhập (gender 0) -> gửi -35 với tóc SAI cấp (level
    26) -> server phải dự phòng về set cố định của gốc và vẫn trả true;
    DB: gender 1, wearing = [89, 88, 0, 4, 14].
 3) Dọn dẹp: xoá cả hai tài khoản test khỏi DB.
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register  # noqa: E402

ITEMS = json.load(open("../../../../assets/data/items.json"))


def pick(zorder, gender, exclude):
    for it in ITEMS:
        if (it["level"] == 0 and it["zorder"] == zorder
                and it["gender"] in (0, gender) and it["id"] < 2000
                and it["id"] not in exclude and it["name"]):
            return it["id"]
    raise AssertionError("không tìm được part")


def bad_hair():
    for it in ITEMS:
        if it["level"] != 0 and it["zorder"] == 50:
            return it["id"]
    raise AssertionError("không tìm được tóc sai cấp")


def login(user, pw):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    for _ in range(30):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -4:
            off = 0
            uid = struct.unpack(">i", body[off:off + 4])[0]
            off += 4
            n = body[off]
            off += 1
            wearing = [struct.unpack(">h", body[off + 2 * i:off + 2 * i + 2])[0]
                       for i in range(n)]
            off += 2 * n
            gender = body[off]
            return c, uid, wearing, gender
    raise AssertionError(f"{user}: không nhận được gói -4")


def create(c, gender, parts):
    body = bytes([gender, len(parts)])
    body += b"".join(struct.pack(">h", p) for p in parts)
    c.send(-35, body)
    for _ in range(20):
        cmd, b = c.recv(timeout=5.0)
        if cmd == -35:
            return b[0] == 1
    raise AssertionError("không nhận được trả lời -35")


def main():
    suf = time.strftime("%H%M%S")
    u1, u2 = "cre" + suf + "a", "cre" + suf + "b"
    hair = pick(50, 2, {49})
    cloth = pick(20, 2, {88})
    pant = pick(10, 2, {89})
    chosen = [pant, cloth, 0, 4, hair]
    print(f"[0] bộ tự chọn cho tài khoản nữ: {chosen} "
          f"(quần {pant}, áo {cloth}, tóc {hair})")

    print(f"[1] {u1}: đăng ký + đăng nhập lần đầu")
    register(u1, "abcd1234")
    c, uid, wearing, gender = login(u1, "abcd1234")
    print(f"  id={uid}, gender={gender}, wearing={wearing}")
    assert gender == 0, "tài khoản mới phải có gender 0 (chưa tạo nhân vật)"

    print("[2] gửi -35 với bộ tự chọn -> phải true")
    ok = create(c, 2, chosen)
    print(f"  trả lời: {ok}")
    assert ok
    c.sock.close()
    time.sleep(3)

    print("[3] đăng nhập lại: gender 2 + wearing đúng bộ đã chọn")
    c, uid2, wearing2, gender2 = login(u1, "abcd1234")
    print(f"  gender={gender2}, wearing={wearing2}")
    assert gender2 == 2, gender2
    assert wearing2 == chosen, (wearing2, chosen)
    c.sock.close()
    time.sleep(3)

    print(f"[4] {u2}: bộ SAI cấp -> dự phòng set cố định của gốc")
    register(u2, "abcd1234")
    c, uid3, wearing3, gender3 = login(u2, "abcd1234")
    assert gender3 == 0
    bad = [89, 88, 0, 4, bad_hair()]
    ok = create(c, 1, bad)
    print(f"  gửi {bad} -> trả lời: {ok}")
    assert ok, "bộ không hợp lệ phải dự phòng set gốc và vẫn true"
    c.sock.close()
    time.sleep(3)
    c, uid4, wearing4, gender4 = login(u2, "abcd1234")
    print(f"  đăng nhập lại: gender={gender4}, wearing={wearing4}")
    assert gender4 == 1
    assert wearing4 == [89, 88, 0, 4, 14], wearing4
    c.sock.close()

    print("LIVE CREATECHAR TEST PASS")


if __name__ == "__main__":
    main()
