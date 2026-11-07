#!/usr/bin/env python3
"""[PORT] Huy chương cặp đôi Rank_Couple: byte cuối gói −22 (của
chính mình) = hạng của cặp trên bảng marry khi thuộc top 3."""
import struct
import time

from live_couple_test import login, parse_info22, pump
from live_createchar_test import db


def info_title(c, uid=1000):
    c.q = []
    c.send(-22, struct.pack(">i", uid))
    pump(c, 1.2)
    for cmd, body in c.q:
        if cmd != -22:
            continue
        out = parse_info22(body)
        if out.get("uid") != uid:
            continue
        # tính offset sau marryDate: parse lại thủ công phần đuôi
        off = 4 + 7 + 4
        if out.get("pid", -1) == -1:
            # không đối tác: short + join UTF + "" UTF rồi byte title
            from live_test import read_utf
            off += 2
            s = read_utf(body, off); off += 2 + len(s.encode())
            s = read_utf(body, off); off += 2 + len(s.encode())
        else:
            # có đối tác: parse_info22 dừng ở cuối marryDate? —
            # nó không trả offset; suy từ đuôi: byte title là byte CUỐI
            # khi gói có đủ marryDate; kiểm bằng độ dài hợp lệ:
            return body[-1] if len(body) > 0 else None
        return body[off] if off < len(body) else None
    raise AssertionError("không nhận được gói −22")


def main():
    db("DELETE FROM marry")
    # cặp của betest01: cấp 12; cặp admin+ad: cấp 20 -> hạng của tôi = 2
    db("INSERT INTO marry (idNam, idNu, level, perLevel)"
       " VALUES (1000, 1001, 12, 30), (1, 7, 20, 0)")
    a = login("betest01")
    t = info_title(a)
    assert t == 2, f"hạng 2 phải cho huy chương bạc (2), nhận {t}"
    print("PASS cặp hạng 2 -> byte huy chương = 2 (bạc)")

    db("DELETE FROM marry WHERE idNam = 1")
    t = info_title(a)
    assert t == 1, f"một mình trên đỉnh phải là vàng (1), nhận {t}"
    print("PASS cặp hạng 1 -> byte huy chương = 1 (vàng)")

    # thêm 3 cặp cao hơn nữa: marry có FK sang users nên tạo 6 user
    # rác (chỉ làm mốc FK, không đăng nhập) rồi xoá sau test
    for i in range(2001, 2007):
        db("INSERT INTO users (id, username, password, role, active,"
           " phone, gmail, vnd, tongnap, timeCreate) VALUES"
           f" ({i}, 'ct{i}', 'x', -1, 1, '', '', 0, 0, '2025-01-01')")
    db("INSERT INTO marry (idNam, idNu, level, perLevel)"
       " VALUES (2001, 2002, 30, 0), (2003, 2004, 28, 0),"
       " (2005, 2006, 26, 0)")
    t = info_title(a)
    assert t == 0, f"ngoài top 3 phải là 0, nhận {t}"
    print("PASS cặp hạng 4 -> byte huy chương = 0")

    try:
        a.sock.close()
    except Exception:
        pass
    time.sleep(1.0)
    db("DELETE FROM marry")
    db("DELETE FROM users WHERE id BETWEEN 2001 AND 2006")
    print("ALL PASS")


if __name__ == "__main__":
    main()
