#!/usr/bin/env python3
"""Test sống các gói tương tác người chơi với server Java thật ở 127.0.0.1:19128.

Kịch bản: A (betest01) và B (betest02) cùng vào map 11 khu 0.
 1) A gửi 59 {idB, 100} (Đánh) -> B và A đều nhận 59 {idA, idB, 100, 5}.
    (Không dùng 101 Hôn: server gốc chỉ cho hôn đồng giới vào thứ 6/7,
    hai tài khoản test cùng giới tính nên ngoài T6/T7 sẽ bị từ chối.)
 2) A gửi -21 {idB} (mời kết bạn) -> B nhận -21 {idA, "betest01", text}
    (đúng định dạng client gốc; server Pack5 gửi -19 sai định dạng).
 3) B gửi -19 {idA, true} (đồng ý) -> A nhận -19 {idB, "betest02", true, text};
    quan hệ bạn lưu DB (dọn bằng -20 ở bước 6b của test bạn bè riêng).
 4) A gửi -6 {idB, "Chào riêng nè"} -> B nhận -6 {idA, "betest01", text}.
 5) A gửi -22 {idB} (xem thông tin) -> A nhận -22 {idB, ...}.
"""
import struct
import sys

sys.path.insert(0, ".")
from live_test import write_utf  # noqa: E402
from live_khu_test import register, recv_until  # noqa: E402
from live_feel_test import login_capture, join  # noqa: E402


def main():
    print("[0] đăng ký betest01 + betest02")
    register("betest01", "abcd1234")
    register("betest02", "abcd1234")

    print("[1] A và B cùng vào map 11 khu 0")
    a, a_id = login_capture("betest01", "abcd1234")
    za = join(a, 0)
    b, b_id = login_capture("betest02", "abcd1234")
    zb = join(b, 0)
    print(f"  A id = {a_id}, B id = {b_id}, khu = {za}/{zb}")
    assert za == 0 and zb == 0, (za, zb)
    # xả hàng đợi để các gói join/move lẫn nhau không lọt vào bước sau
    import time
    time.sleep(0.4)

    print("[2] A ĐÁNH B (gói 59, mã 100)")
    a.send(59, struct.pack(">ih", b_id, 100))
    body = recv_until(b, 59)
    frm, to, gid, tim = struct.unpack(">iihh", body[:12])
    print(f"  B nhận: from={frm}, to={to}, mã={gid}, time={tim}")
    assert (frm, to, gid) == (a_id, b_id, 100), (frm, to, gid)
    body = recv_until(a, 59)
    frm, to, gid, tim = struct.unpack(">iihh", body[:12])
    assert (frm, to, gid) == (a_id, b_id, 100), ("echo A", frm, to, gid)
    print("  A cũng nhận echo 59: OK")

    print("[2b] dọn quan hệ bạn cũ để lời mời đi được")
    a.send(-20, struct.pack(">i", b_id))
    b.send(-20, struct.pack(">i", a_id))
    time.sleep(0.3)

    print("[3] A mời kết bạn (gói -21) -> B nhận -21 {idA, tên, nội dung}")
    a.send(-21, struct.pack(">i", b_id))
    body = recv_until(b, -21)
    rid = struct.unpack(">i", body[:4])[0]
    ln = struct.unpack(">H", body[4:6])[0]
    rname = body[6:6 + ln].decode("utf-8")
    rest = body[6 + ln:]
    ln2 = struct.unpack(">H", rest[:2])[0]
    rtext = rest[2:2 + ln2].decode("utf-8")
    print(f"  B nhận -21: id={rid}, name={rname!r}, text={rtext!r}")
    assert rid == a_id and rname == "betest01" and "muốn kết bạn" in rtext, (rid, rname, rtext)

    print("[4] B đồng ý (gói -19 {idA, true}) -> A nhận -19 {idB, tên, true, nội dung}")
    b.send(-19, struct.pack(">i?", a_id, True))
    body = recv_until(a, -19)
    rid = struct.unpack(">i", body[:4])[0]
    ln = struct.unpack(">H", body[4:6])[0]
    rname = body[6:6 + ln].decode("utf-8")
    flag = body[6 + ln] != 0
    print(f"  A nhận -19: id={rid}, name={rname!r}, boolean={flag}")
    assert rid == b_id and rname == "betest02" and flag is True, (rid, rname, flag)

    print("[5] A nhắn riêng B (gói -6)")
    a.send(-6, struct.pack(">i", b_id) + write_utf("Chào riêng nè"))
    body = recv_until(b, -6)
    typ = struct.unpack(">i", body[:4])[0]
    ln = struct.unpack(">H", body[4:6])[0]
    sender = body[6:6 + ln].decode("utf-8")
    rest = body[6 + ln:]
    ln2 = struct.unpack(">H", rest[:2])[0]
    text = rest[2:2 + ln2].decode("utf-8")
    print(f"  B nhận -6: id={typ}, from={sender!r}, text={text!r}")
    assert typ == a_id and sender == "betest01" and text == "Chào riêng nè", (typ, sender, text)

    print("[6] A xem thông tin B (gói -22)")
    a.send(-22, struct.pack(">i", b_id))
    body = recv_until(a, -22)
    rid = struct.unpack(">i", body[:4])[0]
    stats = list(body[4:11])
    print(f"  A nhận -22: id={rid}, 7 byte chỉ số={stats}")
    assert rid == b_id, rid

    a.sock.close()
    b.sock.close()
    print("PLAYER INTERACT TEST PASS")


if __name__ == "__main__":
    main()
