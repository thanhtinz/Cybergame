# -*- coding: utf-8 -*-
"""Test song DANH HIEU v2 [PORT, goi -120] — bo 21 danh hieu goc
(7 danh hieu x 3 hang Dong/Bac/Vang theo anh Title1..21 cua ban
chinh), cap theo bo dem title_counters + mg_stats:
Chuan bi betest01: quiz_ok=35, fish=30, harvest=45, cook=0,
mg_stats wins=30.
  -> quiz: 3 (10), 2 (30) | fish: 21 (10), 20 (25), 9 (30)
     harvest: 18 (15), 6 (40) | mgwins: 12 (10), 11 (25)
  owned ky vong = {2,3,6,9,11,12,18,20,21}, 21 dinh nghia.
1. A LIST -> STATE: 21 defs, equipped 0, owned dung bo tren,
   fresh = owned (migration da xoa sach).
2. A SET 2 -> equipped 2 + WEAR {1000, 2}; B cung khu nhan WEAR;
   B ZONE_REQ -> ZONE_STATE co {1000: 2}.
3. A SET 1 (chua so huu) -> dialog -10.
4. A SET 0 -> WEAR {1000, 0}; DB players.title_id = 0.
5. Dang nhap lai: owned con nguyen, fresh rong.
Don dep: title_id=0, xoa player_titles + title_counters + mg_stats
cua 1000."""
import struct
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login
from live_preview_test import join_map, wait_pkt
from live_createchar_test import db

TITLE = -120
ok = True
EXPECT_OWNED = {2, 3, 6, 9, 11, 12, 18, 20, 21}

def check(name, cond):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name)
    ok = ok and cond

def rd_utf(body, off):
    ln = struct.unpack('>H', body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode('utf-8', 'replace'), off + 2 + ln

def parse_state(body):
    off = 1
    equipped = struct.unpack_from('>h', body, off)[0]; off += 2
    nd = struct.unpack_from('>h', body, off)[0]; off += 2
    defs = {}
    for _ in range(nd):
        tid = struct.unpack_from('>h', body, off)[0]; off += 2
        name, off = rd_utf(body, off)
        color = body[off]; off += 1
        req, off = rd_utf(body, off)
        defs[tid] = (name, color, req)
    no = struct.unpack_from('>h', body, off)[0]; off += 2
    owned = []
    for _ in range(no):
        owned.append(struct.unpack_from('>h', body, off)[0]); off += 2
    fresh = []
    if off < len(body):
        nf = body[off]; off += 1
        for _ in range(nf):
            fresh.append(struct.unpack_from('>h', body, off)[0]); off += 2
    return equipped, defs, owned, fresh

def wait_title(c, sub, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=2.0)
        except (TimeoutError, OSError):
            continue
        if cmd == TITLE and body and body[0] == sub:
            return body
    return None

def wait_wear(c, uid, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=2.0)
        except (TimeoutError, OSError):
            continue
        if cmd == TITLE and body and body[0] == 3:
            u2, t2 = struct.unpack_from('>ih', body, 1)
            if u2 == uid:
                return t2
    return None

def main():
    snap_mg = db("SELECT COUNT(*) FROM mg_stats WHERE user_id=1000")
    db("UPDATE players SET title_id=0 WHERE user_id=1000")
    db("DELETE FROM player_titles WHERE user_id=1000")
    db("DELETE FROM mg_stats WHERE user_id=1000")
    db("INSERT INTO mg_stats (user_id, game_id, wins, plays) VALUES (1000, 3, 30, 40)")
    try:
        a, ga = login('betest01', 'abcd1234')
        assert ga != 0
        assert join_map(a), 'A vao map'
        # bang title_counters chi duoc tao khi TitleService chay lan
        # dau (luc vao khu) -> dat bo dem SAU khi A da vao map
        db("DELETE FROM title_counters WHERE user_id=1000")
        db("INSERT INTO title_counters (user_id, quiz_ok, fish, harvest, cook)"
           " VALUES (1000, 35, 30, 45, 0)")
        time.sleep(14)
        b, gb = login('betest02', 'abcd1234')
        assert gb != 0
        assert join_map(b), 'B vao map'
        a.send(TITLE, struct.pack('>b', 0))
        body = wait_title(a, 0)
        check('STATE nhan duoc', body is not None)
        equipped, defs, owned, fresh = parse_state(body)
        check('21 dinh nghia, equipped 0', len(defs) == 21 and equipped == 0)
        check('def 1 la Nha Thong Thai (Vang)', defs.get(1, ('',))[0].startswith('Nhà Thông Thái'))
        check('def 21 la Ngu Dan Cham Chi (Dong)', defs.get(21, ('',))[0].startswith('Ngư Dân'))
        check('owned dung bo ky vong', set(owned) == EXPECT_OWNED)
        check('fresh = owned (cap moi ca bo)', set(fresh) == EXPECT_OWNED)
        a.send(TITLE, struct.pack('>bh', 1, 2))
        body = wait_title(a, 0)
        equipped, _, _, _ = parse_state(body)
        check('SET 2 -> equipped 2', equipped == 2)
        check('B nhan WEAR {1000, 2}', wait_wear(b, 1000, timeout=6.0) == 2)
        b.send(TITLE, struct.pack('>b', 5))
        body = wait_title(b, 4)
        zone = {}
        if body:
            off = 1
            n = struct.unpack_from('>h', body, off)[0]; off += 2
            for _ in range(n):
                uid, tid = struct.unpack_from('>ih', body, off); off += 6
                zone[uid] = tid
        check('ZONE_STATE co {1000: 2}', zone.get(1000) == 2)
        a.send(TITLE, struct.pack('>bh', 1, 1))
        dlg = wait_pkt(a, -10, timeout=6.0, label='dialog')
        check('SET danh hieu chua co bi chan (dialog -10)', dlg is not None)
        a.send(TITLE, struct.pack('>bh', 1, 0))
        check('go danh hieu -> WEAR 0', wait_wear(b, 1000, timeout=6.0) == 0)
        a.sock.close(); b.sock.close()
        time.sleep(2.0)
        tid_db = db("SELECT title_id FROM players WHERE user_id=1000")
        check('DB title_id = 0 sau khi go', tid_db == '0')
        a2, _ = login('betest01', 'abcd1234')
        assert join_map(a2), 'A dang nhap lai'
        a2.send(TITLE, struct.pack('>b', 0))
        body = wait_title(a2, 0)
        _, _, owned2, fresh2 = parse_state(body)
        check('dang nhap lai: owned con nguyen va fresh rong',
              set(owned2) == EXPECT_OWNED and fresh2 == [])
        a2.sock.close()
    finally:
        db("UPDATE players SET title_id=0 WHERE user_id=1000")
        db("DELETE FROM player_titles WHERE user_id IN (1000, 1001)")
        db("DELETE FROM title_counters WHERE user_id=1000")
        db("DELETE FROM mg_stats WHERE user_id=1000")
        if snap_mg and snap_mg != '0':
            print('CANH BAO: mg_stats cua 1000 truoc test co %s dong - da xoa, kiem tra lai' % snap_mg)
    print('ALL PASS' if ok else 'CO FAIL')

main()
