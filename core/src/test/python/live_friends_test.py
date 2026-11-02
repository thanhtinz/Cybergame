#!/usr/bin/env python3
"""Test sống hệ BẠN BÈ + THƯ (chat riêng) với server Java thật ở 127.0.0.1:19128.

Kịch bản: A (betest01) và B (betest02) cùng vào map 11 khu 0.
 0) Dọn quan hệ bạn cũ bằng gói -20 cả hai chiều.
 1) A xin danh sách bạn (-18) -> nhận -81 "friendlist" rỗng.
 2) A mời kết bạn (-21 {idB}) -> B nhận -21 {idA, "betest01", "...muốn kết bạn..."}.
 3) B đồng ý (-19 {idA, true}) -> A nhận -19 {idB, "betest02", true, "...đồng ý..."}.
 4) A và B xin danh sách (-18) -> mỗi bên thấy người kia, trạng thái trực tuyến.
 5) Chat riêng 2 chiều: gói -6 mang ID NGƯỜI GỬI thật + tên + nội dung.
 6) A xoá bạn (-20 {idB}) -> danh sách cả hai bên rỗng.
 7) Mời lại, B từ chối (-19 false) -> A nhận -19 boolean false, danh sách vẫn rỗng.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import write_utf  # noqa: E402


def read_utf(body: bytes, off: int = 0):
    """Đọc UTF tại off, trả (chuỗi, offset mới)."""
    ln = struct.unpack(">H", body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode("utf-8", "replace"), off + 2 + ln
from live_khu_test import register, recv_until  # noqa: E402
from live_feel_test import login_capture, join  # noqa: E402


def parse_friendlist(body: bytes):
    list_id, off = read_utf(body, 0)
    assert list_id == "friendlist", list_id
    title, off = read_utf(body, off)
    id_list, page_type = struct.unpack(">ib", body[off:off + 5])
    off += 5
    page = body[off]
    off += 1
    count = struct.unpack(">h", body[off:off + 2])[0]
    off += 2
    friends = []
    for _ in range(count):
        nparts = body[off]
        off += 1
        parts = []
        for _ in range(nparts):
            parts.append(struct.unpack(">h", body[off:off + 2])[0])
            off += 2
        fid = struct.unpack(">i", body[off:off + 4])[0]
        off += 4
        id_img, id_wed, id_status = struct.unpack(">hhh", body[off:off + 6])
        off += 6
        name, off = read_utf(body, off)
        text2, off = read_utf(body, off)
        friends.append({"id": fid, "name": name, "text2": text2,
                        "parts": parts, "idImg": id_img,
                        "idWedding": id_wed, "idStatus": id_status})
    menu_count = body[off]
    return title, friends, menu_count


def friendlist(c):
    c.send(-18)
    body = recv_until(c, -81)
    return parse_friendlist(body)


def recv_chat_from(c, want_id, want_name, want_text):
    body = recv_until(c, -6)
    fid = struct.unpack(">i", body[:4])[0]
    name, off = read_utf(body, 4)
    text, _ = read_utf(body, off)
    print(f"  nhận -6: id={fid}, from={name!r}, text={text!r}")
    assert (fid, name, text) == (want_id, want_name, want_text), (fid, name, text)


def main():
    print("[0] đăng ký + dọn bạn cũ")
    register("betest01", "abcd1234")
    register("betest02", "abcd1234")
    a, a_id = login_capture("betest01", "abcd1234")
    za = join(a, 0)
    b, b_id = login_capture("betest02", "abcd1234")
    zb = join(b, 0)
    assert za == 0 and zb == 0, (za, zb)
    print(f"  A id = {a_id}, B id = {b_id}")
    time.sleep(0.4)
    a.send(-20, struct.pack(">i", b_id))
    b.send(-20, struct.pack(">i", a_id))
    time.sleep(0.3)

    print("[1] danh sách bạn ban đầu rỗng")
    title, friends, _ = friendlist(a)
    print(f"  A: title={title!r}, friends={friends}")
    assert friends == [], friends

    print("[2] A mời kết bạn -> B nhận gói -21 đúng định dạng gốc")
    a.send(-21, struct.pack(">i", b_id))
    body = recv_until(b, -21)
    rid = struct.unpack(">i", body[:4])[0]
    name, off = read_utf(body, 4)
    text, _ = read_utf(body, off)
    print(f"  B nhận -21: id={rid}, name={name!r}, text={text!r}")
    assert rid == a_id and name == "betest01" and "muốn kết bạn" in text, (rid, name, text)

    print("[3] B đồng ý -> A nhận gói -19 {idB, tên, true, nội dung}")
    b.send(-19, struct.pack(">i?", a_id, True))
    body = recv_until(a, -19)
    rid = struct.unpack(">i", body[:4])[0]
    name, off = read_utf(body, 4)
    flag = body[off] != 0
    text, _ = read_utf(body, off + 1)
    print(f"  A nhận -19: id={rid}, name={name!r}, accept={flag}, text={text!r}")
    assert rid == b_id and name == "betest02" and flag is True, (rid, name, flag)

    print("[4] danh sách bạn hai bên đều có nhau")
    _, friends_a, _ = friendlist(a)
    _, friends_b, _ = friendlist(b)
    print(f"  A thấy: {friends_a}")
    print(f"  B thấy: {friends_b}")
    assert len(friends_a) == 1 and friends_a[0]["id"] == b_id \
        and friends_a[0]["name"] == "betest02" \
        and friends_a[0]["text2"] == "Đang trực tuyến", friends_a
    assert len(friends_b) == 1 and friends_b[0]["id"] == a_id, friends_b
    # betest01 có đồ đang mặc đã seed -> part phải chảy qua được danh sách
    assert len(friends_b[0]["parts"]) >= 1, friends_b

    print("[5] chat riêng hai chiều (gói -6 mang id người gửi thật)")
    a.send(-6, struct.pack(">i", b_id) + write_utf("Chào riêng nè"))
    recv_chat_from(b, a_id, "betest01", "Chào riêng nè")
    b.send(-6, struct.pack(">i", a_id) + write_utf("Chào lại bạn"))
    recv_chat_from(a, b_id, "betest02", "Chào lại bạn")

    print("[6] A xoá bạn -> danh sách hai bên rỗng")
    a.send(-20, struct.pack(">i", b_id))
    time.sleep(0.3)
    _, friends_a, _ = friendlist(a)
    _, friends_b, _ = friendlist(b)
    assert friends_a == [] and friends_b == [], (friends_a, friends_b)
    print("  đã xoá cả hai chiều: OK")

    print("[7] mời lại, B từ chối -> A nhận -19 false, danh sách vẫn rỗng")
    a.send(-21, struct.pack(">i", b_id))
    body = recv_until(b, -21)
    assert struct.unpack(">i", body[:4])[0] == a_id
    b.send(-19, struct.pack(">i?", a_id, False))
    body = recv_until(a, -19)
    rid = struct.unpack(">i", body[:4])[0]
    name, off = read_utf(body, 4)
    flag = body[off] != 0
    print(f"  A nhận -19: id={rid}, accept={flag}")
    assert rid == b_id and flag is False, (rid, flag)
    _, friends_a, _ = friendlist(a)
    assert friends_a == [], friends_a

    a.sock.close()
    b.sock.close()
    print("FRIENDS + PRIVATE CHAT TEST PASS")


if __name__ == "__main__":
    main()
