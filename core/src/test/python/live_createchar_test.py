#!/usr/bin/env python3
"""Test sống TẠO NHÂN VẬT (gói -35 CRE_CHARACTER) với server Java thật.

Kịch bản:
 1) Tài khoản mới (nữ): đăng nhập lần đầu -> gói -4 có gender = 0.
    Gửi -35 với bộ đồ TỰ CHỌN + tên nhân vật "Linh Chi" -> true.
    DB: players.name = 'Linh Chi' ngay sau khi tạo.
 2) Vào park: tài khoản thứ hai (betest02) vào cùng khu phải thấy tên
    "Linh Chi" trong danh sách người chơi của gói 50.
 3) Đăng nhập lại: gói -4 chứa tên hiển thị "Linh Chi"; gender 2;
    wearing đúng bộ đã chọn.
 4) Tài khoản mới (nam): gửi -35 với tóc SAI cấp -> server dự phòng
    set cố định của gốc [89, 88, 0, 4, 14], vẫn trả true.
"""
import json
import struct
import subprocess
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_khu_test import register  # noqa: E402

ITEMS = json.load(open("../../../../assets/data/items.json"))
CHAR_NAME = "Linh Chi"
MYSQL = ["/home/hatch/workspace/tools/mariadb/bin/mysql",
         "--socket=/run/mysqld/mysqld.sock", "-u", "root",
         "-pavatardk-local", "avatar_2x", "-N", "-e"]


def db(sql):
    env = {"LD_LIBRARY_PATH": "/home/hatch/workspace/tools/mariadb/lib",
           "PATH": "/usr/bin:/bin"}
    out = subprocess.run(MYSQL + [sql], capture_output=True, text=True, env=env)
    return out.stdout.strip()


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
            return c, uid, wearing, gender, body
    raise AssertionError(f"{user}: không nhận được gói -4")


def create(c, gender, parts, name=None):
    body = bytes([gender, len(parts)])
    body += b"".join(struct.pack(">h", p) for p in parts)
    if name is not None:
        body += write_utf(name)
    c.send(-35, body)
    for _ in range(20):
        cmd, b = c.recv(timeout=5.0)
        if cmd == -35:
            return b[0] == 1
    raise AssertionError("không nhận được trả lời -35")


def has_utf(body, text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw in body


def main():
    suf = time.strftime("%H%M%S")
    u1, u2 = "ten" + suf + "a", "ten" + suf + "b"
    hair = pick(50, 2, {49})
    cloth = pick(20, 2, {88})
    pant = pick(10, 2, {89})
    chosen = [pant, cloth, 0, 4, hair]
    print(f"[0] bộ tự chọn (nữ): {chosen}; tên nhân vật: {CHAR_NAME!r}")

    print(f"[1] {u1}: đăng ký + đăng nhập lần đầu (gender phải = 0)")
    register(u1, "abcd1234")
    a, uid, wearing, gender, _ = login(u1, "abcd1234")
    print(f"  id={uid}, gender={gender}, wearing={wearing}")
    assert gender == 0

    print("[2] gửi -35 kèm tên nhân vật -> true; DB có name ngay")
    assert create(a, 2, chosen, CHAR_NAME)
    dbname = db(f"SELECT `name` FROM `players` WHERE `user_id` = {uid}")
    print(f"  players.name = {dbname!r}")
    assert dbname == CHAR_NAME, dbname

    print("[3] A vào park; betest02 vào cùng khu phải thấy tên 'Linh Chi'")
    a.send(50, struct.pack(">b", 10) + struct.pack(">b", 0)
           + struct.pack(">h", 300) + struct.pack(">h", 120))
    for _ in range(12):
        cmd, _b = a.recv(timeout=5.0)
        if cmd == 50:
            break
    b, bid, bw, bg, _ = login("betest02", "abcd1234")
    b.send(50, struct.pack(">b", 10) + struct.pack(">b", 0)
           + struct.pack(">h", 320) + struct.pack(">h", 120))
    seen = False
    for _ in range(16):
        cmd, body = b.recv(timeout=5.0)
        if cmd == 50 and has_utf(body, CHAR_NAME):
            seen = True
            break
    print(f"  betest02 thấy 'Linh Chi' trong gói 50: {seen}")
    assert seen
    b.sock.close()
    a.sock.close()
    time.sleep(3)

    print("[4] đăng nhập lại: -4 chứa tên hiển thị + gender 2 + đúng bộ đồ")
    a, uid2, wearing2, gender2, body4 = login(u1, "abcd1234")
    print(f"  gender={gender2}, wearing={wearing2}, -4 chứa tên: {has_utf(body4, CHAR_NAME)}")
    assert gender2 == 2 and wearing2 == chosen and has_utf(body4, CHAR_NAME)
    a.sock.close()
    time.sleep(3)

    print(f"[5] {u2}: bộ SAI cấp -> dự phòng set cố định của gốc")
    register(u2, "abcd1234")
    c, uid3, w3, g3, _ = login(u2, "abcd1234")
    assert g3 == 0
    assert create(c, 1, [89, 88, 0, 4, bad_hair()])
    c.sock.close()
    time.sleep(3)
    c, uid4, w4, g4, _ = login(u2, "abcd1234")
    print(f"  đăng nhập lại: gender={g4}, wearing={w4}")
    assert g4 == 1 and w4 == [89, 88, 0, 4, 14], (g4, w4)
    c.sock.close()

    print("LIVE CREATECHAR TEST PASS")


if __name__ == "__main__":
    main()
