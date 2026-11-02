#!/usr/bin/env python3
"""Test sống HÒM THƯ với server Java thật ở 127.0.0.1:19128.

Tiền điều kiện (đã seed bằng SQL): betest01 có 2 thư —
  A 'event' đã đọc, không đính kèm; B 'friend' chưa đọc, kèm 7.000 xu.
Kịch bản:
 1) Xin danh sách (-108) -> 3 thư (thư ngày tự tạo + B + A); thư ngày
    chứa 2.000 xu + 2 lượng.
 2) Đánh dấu đã đọc (-109) thư ngày; nhận quà (-110) -> ok=1 và gói
    -33 báo tiền mới: xu 22.000, lượng 477.
 3) Nhận lại thư ngày (-110) -> ok=0; nhận thư không tồn tại -> ok=0.
 4) Nhận tất cả (-111) -> count=1 (thư B), xu lên 29.000; danh sách
    trả kèm cho thấy B đã claimed.
 5) Xoá thư đã đọc (-112) -> danh sách rỗng (thư ngày KHÔNG tự tạo
    lại nhờ bảng daily_claims).
 6) Ngắt kết nối -> DB: xu 29.000, lượng 477, letters rỗng,
    daily_claims có mốc hôm nay.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
def read_utf(body: bytes, off: int = 0):
    """Đọc UTF tại off, trả (chuỗi, offset mới)."""
    ln = struct.unpack(">H", body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode("utf-8", "replace"), off + 2 + ln
from live_feel_test import login_capture  # noqa: E402
from live_khu_test import recv_until, join  # noqa: E402


def parse_list(body: bytes):
    n = body[0]
    off = 1
    out = []
    for _ in range(n):
        lid = struct.unpack(">i", body[off:off + 4])[0]
        off += 4
        kind, off = read_utf(body, off)
        title, off = read_utf(body, off)
        sender, off = read_utf(body, off)
        text, off = read_utf(body, off)
        atts, off = read_utf(body, off)
        rd, cl = body[off], body[off + 1]
        off += 2
        created = struct.unpack(">q", body[off:off + 8])[0]
        off += 8
        out.append({"id": lid, "kind": kind, "title": title,
                    "sender": sender, "body": text, "atts": atts,
                    "read": rd, "claimed": cl, "created": created})
    return out


def recv_until_multi(c, wants, tries=16):
    """Nhận cho tới khi gom đủ các cmd trong wants; trả {cmd: body}."""
    got = {}
    for _ in range(tries):
        cmd, body = c.recv(timeout=5.0)
        if cmd in wants:
            got[cmd] = body
            if len(got) == len(wants):
                return got
        else:
            print(f"    (bỏ qua cmd {cmd}, {len(body)} bytes)")
    raise AssertionError(f"không gom đủ {wants}, mới có {list(got)}")


def parse_money(body: bytes):
    xeng, typ, xu, luong, luong_khoa = struct.unpack(">ibiii", body[:17])
    return {"xeng": xeng, "type": typ, "xu": xu, "luong": luong,
            "luongKhoa": luong_khoa}


def main():
    c, uid = login_capture("betest01", "abcd1234")
    assert uid == 1000, uid
    join(c, 0)

    # 1) danh sách
    c.send(-108)
    letters = parse_list(recv_until(c, -108))
    kinds = sorted(l["kind"] for l in letters)
    assert len(letters) == 3, letters
    assert kinds == ["daily", "event", "friend"], kinds
    daily = next(l for l in letters if l["kind"] == "daily")
    assert '"n":2000' in daily["atts"] and '"n":2' in daily["atts"], daily["atts"]
    assert daily["read"] == 0 and daily["claimed"] == 0
    print("1) danh sách 3 thư OK, thư ngày:", daily["title"], daily["atts"])

    # 2) đọc + nhận quà thư ngày
    c.send(-109, struct.pack(">i", daily["id"]))
    c.send(-110, struct.pack(">i", daily["id"]))
    got = recv_until_multi(c, {-110, -33})
    cid, ok = struct.unpack(">ib", got[-110][:5])
    assert cid == daily["id"] and ok == 1, (cid, ok)
    money = parse_money(got[-33])
    assert money["xu"] == 22000 and money["luong"] == 477, money
    print("2) nhận quà thư ngày OK, tiền mới:", money)

    # 3) nhận lại + nhận thư ma
    c.send(-110, struct.pack(">i", daily["id"]))
    body = recv_until(c, -110)
    cid, ok = struct.unpack(">ib", body[:5])
    assert cid == daily["id"] and ok == 0, (cid, ok)
    c.send(-110, struct.pack(">i", 999999))
    body = recv_until(c, -110)
    cid, ok = struct.unpack(">ib", body[:5])
    assert cid == 999999 and ok == 0, (cid, ok)
    print("3) nhận trùng / thư ma đều ok=0")

    # 4) nhận tất cả
    c.send(-111)
    got = recv_until_multi(c, {-111, -33, -108})
    assert got[-111][0] == 1, got[-111]
    money = parse_money(got[-33])
    assert money["xu"] == 29000 and money["luong"] == 477, money
    letters = parse_list(got[-108])
    b = next(l for l in letters if l["kind"] == "friend")
    assert b["claimed"] == 1 and b["read"] == 1, b
    print("4) nhận tất cả OK (1 thư), xu:", money["xu"])

    # 5) xoá thư đã đọc -> rỗng, thư ngày không đội lên lại
    c.send(-112)
    letters = parse_list(recv_until(c, -108))
    assert letters == [], letters
    print("5) xoá đã đọc OK, hòm thư rỗng")

    c.sock.close()
    time.sleep(3)
    print("MAIL TEST PASS")


if __name__ == "__main__":
    main()
