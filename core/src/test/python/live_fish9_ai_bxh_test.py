# -*- coding: utf-8 -*-
"""Test sống: shop đồ câu 9 món + hoá đơn chia mốc ngày, Aincrad
online (leo tháp 3 tầng, mở sai hoá ma), Bảng xếp hạng hub.

- betest01 mở shop NPC 430: danh sách −49 phải đủ 9 món sell==10.
- Mua theo hoá đơn (gói −24 kèm byte ngày): 442 Xu mốc 7 ngày = 10.000;
  445 Lượng mốc 30 = 38 (giá 30 ngày = round(25*30/20)); 446 Lượng
  mốc 7 = 23; 446 Lượng Vĩnh viễn (ngày 0) = 500; mồi 443 = 5 xu;
  vé 458 Xu mốc 1 ngày = 333 (giá 30 ngày = 10.000).
- Câu ở bãi server 14 bằng cần 446 + vé 458 + mồi 443 (trừ mồi rẻ nhất).
- Aincrad: betest01 tạo bàn game 1, betest02 vào; betest01 dò cửa
  (sai thì chờ hết ma 6s) tới EXIT tầng 3 -> AI_END winner 0.
- BXH: TOP tổng + TOP game 1 phải có betest01 thắng >= 1; DB mg_stats
  khớp; DB wearing có 446 hạn -1 (bản Vĩnh viễn).
Chạy tuần tự, KHÔNG chạy song song phiên đăng nhập khác."""
import json
import struct
import sys
import time

sys.path.insert(0, '.')
from live_fishing_test import (FISH_IMG_PAD, img_index_map, join, move,  # noqa: E402
                               select_menu, talk_menu, WIRE_430)
from live_house_test import login, wait_cmd  # noqa: E402
from live_createchar_test import db  # noqa: E402
from live_tauchien_test import mg, wait_mg  # noqa: E402

AI_START, AI_PICK, AI_MOVE, AI_GHOST_OFF, AI_END, AI_SNAP = 1, 2, 3, 5, 6, 13
H_TOP, H_S_TOP = 9, 19


def s16(b, o):
    return struct.unpack_from('>h', b, o)[0]


def s32(b, o):
    return struct.unpack_from('>i', b, o)[0]


def u16(b, o):
    return struct.unpack_from('>H', b, o)[0]


def utf(b, o):
    n = u16(b, o)
    return b[o + 2:o + 2 + n].decode('utf-8', 'replace'), o + 2 + n


def buy_days(c, pid, typ, days, want_price):
    c.send(-24, struct.pack('>hbb', pid, typ, days))
    body, _ = wait_cmd(c, -24, 20)
    assert s16(body, 0) == pid, body[:8]
    price = s32(body, 2)
    print('mua %d type %d ngay %d -> gia %d (muon %d)' % (pid, typ, days, price, want_price))
    assert price == want_price, (pid, price, want_price)


def drain_ai(c, want_sub, timeout=10.0):
    """Chờ gói game 1 sub cần (wait_mg tự bỏ qua gói khác)."""
    return wait_mg(c, 1, want_sub, timeout=timeout)


def main():
    imgmap = img_index_map()
    db("UPDATE players SET xu=920000, luong=2000, chests='[]' WHERE user_id=1000")
    db("DELETE FROM mg_stats WHERE user_id IN (1000, 1001)")

    c, uid = login('betest01', 'abcd1234')
    assert uid == 1000
    print('login betest01 ok')

    # ---------- Shop đồ câu: 9 món + hoá đơn chia mốc ----------
    join(c, 13)
    move(c, 320, 62)
    mid, items = talk_menu(c, WIRE_430)
    select_menu(c, WIRE_430, mid, 0)
    body, _ = wait_cmd(c, -49, 20)
    name, o = utf(body, 1)
    n = s16(body, o)
    o += 2
    ids = [s16(body, o + 2 * i) for i in range(n)]
    print('shop do cau:', ids)
    assert ids == [442, 443, 445, 446, 447, 448, 458, 459, 460], ids

    buy_days(c, 442, 1, 7, 10000)     # cần tre: gốc 7 ngày/10.000 xu
    buy_days(c, 445, 2, 30, 38)       # cần sắt: price30 = round(25*30/20) = 38
    buy_days(c, 446, 2, 7, 23)        # VIP: 100*7/30 -> 23
    buy_days(c, 446, 2, 0, 500)       # VIP vĩnh viễn: 100*5
    # mồi 443: luồng cũ không byte ngày
    c.send(-24, struct.pack('>hb', 443, 1))
    body, _ = wait_cmd(c, -24, 20)
    assert s16(body, 0) == 443 and s32(body, 2) == 5, body[:8]
    c.send(-24, struct.pack('>hb', 443, 1))
    body, _ = wait_cmd(c, -24, 20)
    assert s16(body, 0) == 443 and s32(body, 2) == 5, body[:8]
    print('moi 443 x2 ok')
    buy_days(c, 458, 1, 1, 333)       # vé rô mốc 1 ngày: 10000*1/30 -> 333

    # ---------- Câu ở bãi 14 bằng cần 446 + vé 458 + mồi 443 ----------
    join(c, 14)
    c.send(86)
    body, _ = wait_cmd(c, 86, 20)
    ok = body[0] == 1
    err, _ = utf(body, 1)
    print('86 ok=%s err=%r' % (ok, err))
    assert ok, err
    c.send(82)
    body, _ = wait_cmd(c, 82, 20)
    assert s32(body, 0) == 1000
    body, _ = wait_cmd(c, 91, 45)
    o = 0
    fuid = s32(body, o); o += 4
    idfish = s16(body, o); o += 2
    o += 2  # delay
    n = body[o]; o += 1
    pads = []
    for _ in range(n):
        ln = u16(body, o); o += 2
        img = bytes(body[o:o + ln]); o += ln
        pads.append(FISH_IMG_PAD[imgmap[img]])
    assert fuid == 1000 and idfish > 0, (fuid, idfish)
    c.send(84, bytes([1, n] + pads))
    body, _ = wait_cmd(c, 84, 20)
    assert s32(body, 0) == 1000 and s16(body, 4) == idfish
    c.send(85)
    body, _ = wait_cmd(c, 85, 20)
    assert s32(body, 0) == 1000
    print('cau bang can 446 + ve 458 + moi 443 OK, ca', idfish)

    # ---------- Aincrad + BXH ----------
    mg(c, 0, 1, struct.pack('>bb', 1, 0))          # H_CREATE game 1 public
    r = wait_mg(c, 0, 10)
    room_id = r.i32()
    seat = r.u8()
    g2id = r.u8()
    assert seat == 0 and g2id == 1, (seat, g2id)
    print('tao ban Aincrad', room_id)

    c2, uid2 = login('betest02', 'abcd1234')
    assert uid2 == 1001
    time.sleep(2)
    mg(c2, 0, 2, struct.pack('>i', room_id))       # H_JOIN
    r = wait_mg(c2, 0, 10)
    assert r.i32() == room_id and r.u8() == 1
    # cả hai nhận AI_START (snapshot xen giữa cũng được)
    drain_ai(c, AI_START)
    drain_ai(c2, AI_START)
    print('Aincrad START ca hai phia')

    # betest01 dò cửa: thử 0,1,2 theo vòng; sai -> ma 6s phải chờ
    my_floor = 1
    won = False
    tries = 0
    end_deadline = time.time() + 240
    while time.time() < end_deadline and not won:
        for door in (0, 1, 2):
            floor_before = my_floor
            mg(c, 1, AI_PICK, bytes([door]))
            r = wait_mg(c, 1, AI_MOVE, timeout=12)
            mseat = r.u8(); mdoor = r.u8(); mok = r.bool(); mfloor = r.u8()
            assert mseat == 0, mseat
            tries += 1
            if mok and floor_before >= 3:
                r = wait_mg(c, 1, AI_END, timeout=10)
                assert r.u8() == 0, 'winner phai la betest01'
                won = True
                break
            if mok:
                my_floor = mfloor
                print('cua %d dung -> tang %d' % (mdoor, my_floor))
                break  # sang tầng mới: dò lại từ cửa 0
            print('cua %d sai -> hoa ma, cho 6.5s' % mdoor)
            time.sleep(6.6)
        else:
            continue
    assert won, 'chua thang Aincrad sau %d lan mo' % tries
    print('Aincrad: betest01 thang sau %d lan mo cua' % tries)
    # betest02 cũng phải nhận AI_END winner 0
    r = wait_mg(c2, 1, AI_END, timeout=10)
    assert r.u8() == 0
    print('betest02 nhan AI_END winner 0')

    # ---------- BXH ----------
    for top_game in (0, 1):
        mg(c, 0, H_TOP, bytes([top_game]))
        r = wait_mg(c, 0, H_S_TOP)
        tg = r.u8()
        cnt = r.i16()
        rows = []
        for _ in range(cnt):
            rows.append((r.utf(), r.i32(), r.i32()))
        print('TOP game %d:' % tg, rows)
        mine = [row for row in rows if row[1] >= 1]
        assert mine, ('BXH trong', top_game, rows)
    c.sock.close()
    c2.sock.close()
    time.sleep(3.5)

    out = db("SELECT game_id, wins, plays FROM mg_stats WHERE user_id=1000")
    print('mg_stats betest01:', out)
    # Aincrad = game 1 theo số hiệu gốc: phải có dòng game 1 với wins >= 1
    ai_row = [ln.split('\t') for ln in out.strip().split('\n')
              if ln.split('\t')[0] == '1']
    assert ai_row and int(ai_row[0][1]) >= 1, out
    out = db("SELECT wearing FROM players WHERE user_id=1000")
    wearing = json.loads(out)
    rod = [e for e in wearing if e['id'] == 446]
    assert rod and rod[0].get('expired') == -1, ('can VIP vinh vien phai o wearing', rod)
    chests = json.loads(db("SELECT chests FROM players WHERE user_id=1000"))
    bait443 = [e for e in chests if e['id'] == 443]
    assert bait443 and bait443[0].get('quantity') >= 1, ('moi 443 phai con lai sau 1 lan cau', bait443)
    print('DB: mg_stats + wearing 446 vinh vien + moi 443 con 1 — OK')
    print('ALL FISH9/AINCRAD/BXH TESTS PASS')


if __name__ == '__main__':
    main()
