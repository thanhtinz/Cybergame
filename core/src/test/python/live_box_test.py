# -*- coding: utf-8 -*-
"""Test sống RƯƠNG PHỤ [PORT BoxScr, gói −118] bằng betest01:
1. OPEN lần đầu: chưa mật khẩu, mở khoá sẵn, rương rỗng, giỏ có 2090+446.
2. SETPASS 1234 -> STATE hasPass=1.
3. TRANS giỏ->rương 2090, 446 -> rương đủ 2 món, giỏ rỗng dần.
4. TRANS rương->giỏ 446 -> trả về giỏ.
5. UPGRADE hỏi giá (câu hỏi cấp 2) + xác nhận -> slot 10 -> 15,
   xu/lượng trừ đúng bảng rương nhà (20.000 xu + 20 lượng).
6. Ngắt kết nối -> đăng nhập lại: rương KHOÁ (unlocked=0).
7. UNLOCK sai -> dialog lỗi; UNLOCK đúng -> thấy lại món 2090.
8. CHANGEPASS sai cũ -> dialog; CHANGEPASS gỡ mật khẩu (mới rỗng).
9. Dọn dẹp: chuyển 2090 về giỏ, khôi phục số dư; kiểm DB cuối.
"""
import struct
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login
from live_createchar_test import db

BOX = -118
DIALOG = -10


def utf(s):
    b = s.encode('utf-8')
    return struct.pack(">H", len(b)) + b


def read_utf(body, off):
    ln = struct.unpack(">H", body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode('utf-8'), off + 2 + ln


def parse_state(body):
    sub, has, unl, lvl, slot, cslot = struct.unpack(">bbbbbb", body[:6])
    st = {"has": has, "unlocked": unl, "level": lvl, "slot": slot}
    off = 6
    if unl:
        n = struct.unpack(">h", body[off:off + 2])[0]
        off += 2
        if n == -1:
            q, off = read_utf(body, off)
            st["ask"] = q
            return st
        box = []
        for _ in range(n):
            pid = struct.unpack(">h", body[off:off + 2])[0]
            off += 3  # id + reliability byte
            _, off = read_utf(body, off)
            box.append(pid)
        n2 = struct.unpack(">h", body[off:off + 2])[0]
        off += 2
        bag = []
        for _ in range(n2):
            pid = struct.unpack(">h", body[off:off + 2])[0]
            off += 3
            _, off = read_utf(body, off)
            bag.append(pid)
        st["box"] = box
        st["bag"] = bag
    return st


def wait_state(c, timeout=8.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.3, end - time.time()))
        except (TimeoutError, OSError):
            break
        if cmd == BOX:
            return parse_state(body)
    raise AssertionError("không nhận STATE rương phụ")


def wait_dialog(c, timeout=8.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.3, end - time.time()))
        except (TimeoutError, OSError):
            break
        if cmd == DIALOG:
            txt, _ = read_utf(body, 0)
            return txt
    raise AssertionError("không nhận dialog")


def money(uid):
    row = db(f"SELECT xu, luong FROM players WHERE user_id={uid}").strip().split("\n")[0]
    xu, luong = row.split("\t")
    return int(xu), int(luong)


def main():
    # trạng thái rương phụ phải trắng từ đầu (CACHE phía server giữ
    # theo phiên tiến trình -> chạy test này sau khi restart server)
    db("DELETE FROM player_box WHERE user_id=1000")
    xu0, lu0 = money(1000)
    # đảm bảo đủ tiền nâng cấp cho tài khoản test
    db("UPDATE players SET xu=500000, luong=500 WHERE user_id=1000")

    a, _ = login("betest01", "abcd1234")
    time.sleep(1)

    print("1) OPEN lần đầu")
    a.send(BOX, b"\x00")
    st = wait_state(a)
    assert st["has"] == 0 and st["unlocked"] == 1 and st["slot"] == 10, st
    assert st["box"] == [] and 2090 in st["bag"] and 446 in st["bag"], st
    print("  ok:", st)

    print("2) SETPASS 1234")
    a.send(BOX, b"\x02" + utf("1234"))
    st = wait_state(a)
    assert st["has"] == 1 and st["unlocked"] == 1, st
    print("  ok")

    print("3) TRANS giỏ -> rương 2090 + 446")
    a.send(BOX, b"\x04\x00" + struct.pack(">h", 2090))
    st = wait_state(a)
    assert st["box"] == [2090] and 2090 not in st["bag"], st
    a.send(BOX, b"\x04\x00" + struct.pack(">h", 446))
    st = wait_state(a)
    assert sorted(st["box"]) == [446, 2090] and st["bag"] == [], st
    print("  ok:", st["box"])

    print("4) TRANS rương -> giỏ 446")
    a.send(BOX, b"\x04\x01" + struct.pack(">h", 446))
    st = wait_state(a)
    assert st["box"] == [2090] and st["bag"] == [446], st
    print("  ok")

    print("5) UPGRADE hỏi + xác nhận (cấp 1 -> 2)")
    a.send(BOX, b"\x05\x00")
    st = wait_state(a)
    assert "ask" in st and "cấp 2" in st["ask"], st
    print("  ok hỏi:", st["ask"][:60], "...")
    a.send(BOX, b"\x05\x01")
    dlg = wait_dialog(a)
    assert "Đã nâng cấp" in dlg, dlg
    st = wait_state(a)
    assert st["slot"] == 15 and st["level"] == 2, st
    print("  ok nâng cấp:", dlg)

    a.sock.close()
    time.sleep(4)

    print("6) Đăng nhập lại: rương đã khoá")
    a, _ = login("betest01", "abcd1234")
    time.sleep(1)
    a.send(BOX, b"\x00")
    st = wait_state(a)
    assert st["has"] == 1 and st["unlocked"] == 0 and "box" not in st, st
    print("  ok khoá theo phiên")

    print("7) UNLOCK sai / đúng")
    a.send(BOX, b"\x01" + utf("9999"))
    dlg = wait_dialog(a)
    assert "không đúng" in dlg, dlg
    a.send(BOX, b"\x01" + utf("1234"))
    st = wait_state(a)
    assert st["unlocked"] == 1 and st["box"] == [2090], st
    print("  ok mở khoá, rương còn 2090")

    print("8) CHANGEPASS: sai cũ bị chặn, gỡ mật khẩu")
    a.send(BOX, b"\x03" + utf("0000") + utf("abcd"))
    dlg = wait_dialog(a)
    assert "Mật khẩu cũ không đúng" in dlg, dlg
    a.send(BOX, b"\x03" + utf("1234") + utf(""))
    st = wait_state(a)
    assert st["has"] == 0, st
    print("  ok đã gỡ mật khẩu")

    print("9) Dọn dẹp: 2090 về giỏ + khôi phục số dư")
    a.send(BOX, b"\x04\x01" + struct.pack(">h", 2090))
    st = wait_state(a)
    assert st["box"] == [] and sorted(st["bag"]) == [446, 2090], st
    a.sock.close()
    time.sleep(4)
    db(f"UPDATE players SET xu={xu0}, luong={lu0} WHERE user_id=1000")
    row = db("SELECT items, LENGTH(pass_hash) FROM player_box WHERE user_id=1000").strip()
    assert row == "[]\t0", row
    print("  ok DB sạch:", row.replace("\t", " | "))

    print("ALL PASS")


if __name__ == "__main__":
    main()
