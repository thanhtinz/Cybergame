# -*- coding: utf-8 -*-
"""Test sống câu cá (đợt mini-game, docs/minigame-spec.md §1):
betest01 mua đồ câu ở NPC 430 (cần 446 bằng lượng + mồi 448 bằng xu)
-> ra map server 14 (client 15) bắt đầu câu (86 ok) -> quăng câu (82)
-> chờ gói cá cắn 91, giải chuỗi mũi tên -> gửi 84 đúng phím -> nhận
84 broadcast cá >= 0 -> bán cá ở NPC 430 -> xu tăng đúng giá cá.
betest02 (không cần câu) -> 86 phải trả ok=false.
Chạy tuần tự, KHÔNG chạy song song phiên đăng nhập khác."""
import hashlib  # noqa: F401  (giữ cho debug hash ảnh)
import json
import re
import struct
import sys
import time

sys.path.insert(0, '.')
from live_test import Conn  # noqa: E402
from live_house_test import login, wait_cmd  # noqa: E402
from live_createchar_test import db  # noqa: E402

WIRE_430 = 2000000000 + 430
# Ánh xạ ảnh mũi tên -> pad (đã xem từng ảnh kiểm chứng 2026-11-03):
# Fish.java img1..img10 = [→,↑,←,←,↓,←,→,→,←,→] -> pad [3,2,1,1,4,1,3,3,1,3]
FISH_IMG_PAD = [3, 2, 1, 1, 4, 1, 3, 3, 1, 3]
FISH_PRICE = {444: 50, 449: 50, 450: 60, 451: 70, 452: 80, 454: 100,
              455: 110, 456: 120, 2130: 1000, 2131: 1500, 2132: 2000,
              457: 500000}


def img_index_map():
    src = open('../../../../server/src/main/java/avatar/model/Fish.java').read()
    out = {}
    for i in range(1, 11):
        m = re.search(r'img%d\s*=\s*new byte\[\]\s*\{(.*?)\};' % i, src, re.S)
        vals = [int(x) for x in m.group(1).replace('\n', ' ').split(',') if x.strip()]
        out[bytes((v & 0xFF) for v in vals)] = i - 1
    return out


def s16(b, o):
    return struct.unpack_from('>h', b, o)[0]


def u16(b, o):
    return struct.unpack_from('>H', b, o)[0]


def s32(b, o):
    return struct.unpack_from('>i', b, o)[0]


def utf(b, o):
    n = u16(b, o)
    return b[o + 2:o + 2 + n].decode('utf-8', 'replace'), o + 2 + n


def join(c, map_server, x=100, y=100):
    c.send(50, struct.pack('>bbhh', map_server, -1, x, y))
    body, _ = wait_cmd(c, 50, 20)
    return body


def move(c, x, y):
    c.send(54, struct.pack('>hhb', x, y, 0))
    time.sleep(0.4)


def talk_menu(c, wire):
    time.sleep(2.5)  # server chống spam khi nói chuyện liên tục
    c.send(-61, struct.pack('>i', wire))
    body, _ = wait_cmd(c, -59, 20)
    o = 0
    uid = s32(body, o)
    o += 4
    mid = body[o]
    o += 1
    n = body[o]
    o += 1
    items = []
    for _ in range(n):
        t, o = utf(body, o)
        items.append(t)
    assert uid == wire, (uid, wire)
    return mid, items


def select_menu(c, wire, mid, idx):
    c.send(-59, struct.pack('>ibb', wire, mid, idx))


def main():
    imgmap = img_index_map()
    assert len(imgmap) == 9, 'phai co 9 anh khac nhau (2 anh trung nhau)'

    # --- betest01: đặt tiền + dọn đồ câu cũ qua DB (server lưu khi thoát,
    # nên đặt TRƯỚC khi đăng nhập)
    db("UPDATE players SET xu=920000, luong=200, chests='[]' WHERE user_id=1000")

    c, uid = login('betest01', 'abcd1234')
    assert uid == 1000, uid
    print('login betest01 ok')

    # --- NPC 430 ở server map 13 (326,64): đi tới cạnh rồi mở menu
    join(c, 13)
    move(c, 320, 62)
    mid, items = talk_menu(c, WIRE_430)
    print('menu 430:', items)
    assert len(items) == 4 and 'Câu' in items[0], items
    select_menu(c, WIRE_430, mid, 0)
    body, _ = wait_cmd(c, -49, 20)
    shop_id = body[0]
    name, o = utf(body, 1)
    n = s16(body, o)
    o += 2
    ids = [s16(body, o + 2 * i) for i in range(n)]
    print('shop -49 id byte=%d name=%r items=%s' % (shop_id, name, ids))
    assert ids == [442, 443, 445, 446, 447, 448, 458, 459, 460], ids

    # --- Mua cần 446 bằng Lượng (type 2), mồi 448 x3 bằng Xu (type 1),
    # vé cá rô 458 (bãi server 14 nay cần vé riêng — đợt shop 9 món)
    for pid, typ, want_price in [(446, 2, 100), (448, 1, 30), (448, 1, 30), (448, 1, 30), (458, 1, 1000)]:
        c.send(-24, struct.pack('>hb', pid, typ))
        body, _ = wait_cmd(c, -24, 20)
        assert s16(body, 0) == pid, body[:8]
        price = s32(body, 2)
        print('mua %d gia %d ok' % (pid, price))
        assert price == want_price, price

    # --- Ra map câu server 14 (client 15): bắt đầu câu 86 -> ok
    join(c, 14)
    c.send(86)
    body, _ = wait_cmd(c, 86, 20)
    ok = body[0] == 1
    err, _ = utf(body, 1)
    print('86 ok=%s err=%r' % (ok, err))
    assert ok, err

    # --- Quăng câu 82 -> nhận 82 broadcast; chờ cá cắn 91 (tối đa 45s)
    c.send(82)
    body, _ = wait_cmd(c, 82, 20)
    assert s32(body, 0) == 1000, body
    print('82 broadcast ok')
    body, _ = wait_cmd(c, 91, 45)
    o = 0
    fuid = s32(body, o); o += 4
    idfish = s16(body, o); o += 2
    delay = s16(body, o); o += 2
    n = body[o]; o += 1
    pads = []
    for _ in range(n):
        ln = u16(body, o); o += 2
        img = bytes(body[o:o + ln]); o += ln
        pads.append(FISH_IMG_PAD[imgmap[img]])
    print('91: ca %d delay %d chuoi %d buoc pads=%s' % (idfish, delay, n, pads))
    assert fuid == 1000 and idfish > 0 and 4 <= n <= 10

    # --- Gửi 84 đúng phím -> nhận 84 broadcast trúng đúng con cá đó
    c.send(84, bytes([1, n] + pads))
    body, _ = wait_cmd(c, 84, 20)
    ruid = s32(body, 0)
    rid = s16(body, 4)
    print('84: user %d ca %d' % (ruid, rid))
    assert ruid == 1000 and rid == idfish, (ruid, rid, idfish)

    # --- Xong lượt 85
    c.send(85)
    body, _ = wait_cmd(c, 85, 20)
    assert s32(body, 0) == 1000
    print('85 ok')

    # --- Quay lại NPC 430 bán cá: ghi xu TRƯỚC từ gói tiền (mua xong
    # server da gui -24 kem xu con lai) — đơn giản nhất: đọc DB sau khi
    # đóng phiên trước (server lưu khi ngắt) không tiện giữa phiên, nên
    # kiểm bằng DB sau khi bán + thoát: tổng xu phải khớp phép tính:
    # 920000 - 3*30 (mồi) - 1000 (vé rô) + giá cá; lượng 200 - 100 (cần) = 100.
    join(c, 13)
    move(c, 320, 62)
    mid, items = talk_menu(c, WIRE_430)
    idx_ban = next(i for i, t in enumerate(items) if 'Bán' in t)
    select_menu(c, WIRE_430, mid, idx_ban)
    time.sleep(2.5)
    c.sock.close()
    time.sleep(3.5)

    out = db("SELECT xu, luong, chests FROM players WHERE user_id=1000")
    parts = out.split('\t')
    xu_after, luong_after = int(parts[0]), int(parts[1])
    chests = json.loads(parts[2])
    left = [e for e in chests if e['id'] == idfish]
    assert not left, ('ca van con trong ruong', left)
    bait = [e for e in chests if e['id'] == 448]
    assert bait and bait[0].get('quantity') == 2, ('moi phai con 2 (mua 3, quang 1)', bait)
    want_xu = 920000 - 90 - 1000 + FISH_PRICE[idfish]
    print('DB: xu=%d (muon %d) luong=%d (muon 100) ruong=%s'
          % (xu_after, want_xu, luong_after, parts[2]))
    assert xu_after == want_xu, (xu_after, want_xu)
    assert luong_after == 100, luong_after
    print('BAN CA OK')

    # --- betest02: không cần câu -> 86 false
    c2, uid2 = login('betest02', 'abcd1234')
    join(c2, 14)
    c2.send(86)
    body, _ = wait_cmd(c2, 86, 20)
    ok2 = body[0] == 1
    err2, _ = utf(body, 1)
    print('betest02 86 ok=%s err=%r' % (ok2, err2))
    assert not ok2 and 'cần câu' in err2, (ok2, err2)
    c2.sock.close()
    time.sleep(2)
    print('ALL FISHING TESTS PASS')


if __name__ == '__main__':
    main()
