# -*- coding: utf-8 -*-
"""Test song DANH HIEU [PORT, goi -120]:
Chuan bi: betest01 xu = 2.000.000 (dat "Dai Gia"), mg_stats wins = 30
(dat "Cao Thu Mini Game").
1. A LIST -> STATE: 12 dinh nghia, equipped 0, owned co 1, 5, 6;
   danh hieu moi cap (fresh) co 5 va 6.
2. A SET 5 -> STATE equipped 5 + WEAR {1000, 5}; B cung khu cung
   nhan WEAR; B ZONE_REQ -> ZONE_STATE co {1000: 5}.
3. A SET 4 (chua so huu neu cap < 30) -> dialog loi -10.
4. A SET 0 -> WEAR {1000, 0}; DB players.title_id = 0.
5. Dang nhap lai: LIST -> equipped 0, owned van co 5 va 6.
Don dep: title_id ve 0, xoa player_titles cua 1000, khoi phuc xu
va mg_stats."""
import struct
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login
from live_preview_test import join_map, wait_pkt
from live_createchar_test import db

TITLE = -120
ok = True

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
    snap_xu = db("SELECT xu FROM players WHERE user_id=1000")
    snap_mg = db("SELECT COUNT(*) FROM mg_stats WHERE user_id=1000")
    db("UPDATE players SET xu=2000000, title_id=0 WHERE user_id=1000")
    db("DELETE FROM player_titles WHERE user_id=1000")
    db("DELETE FROM mg_stats WHERE user_id=1000")
    db("INSERT INTO mg_stats (user_id, game_id, wins, plays) VALUES (1000, 3, 30, 40)")
    try:
        a, ga = login('betest01', 'abcd1234')
        assert ga != 0
        assert join_map(a), 'A vao map'
        time.sleep(14)
        b, gb = login('betest02', 'abcd1234')
        assert gb != 0
        assert join_map(b), 'B vao map'
        a.send(TITLE, struct.pack('>b', 0))
        body = wait_title(a, 0)
        check('STATE nhan duoc', body is not None)
        equipped, defs, owned, fresh = parse_state(body)
        check('12 dinh nghia, equipped 0', len(defs) == 12 and equipped == 0)
        check('owned co 1, 5, 6', all(t in owned for t in (1, 5, 6)))
        check('fresh co 5 va 6', 5 in fresh and 6 in fresh)
        a.send(TITLE, struct.pack('>bh', 1, 5))
        body = wait_title(a, 0)
        equipped, _, _, _ = parse_state(body)
        check('SET 5 -> equipped 5', equipped == 5)
        check('B nhan WEAR {1000, 5}', wait_wear(b, 1000, timeout=6.0) == 5)
        b.send(TITLE, struct.pack('>b', 5))
        body = wait_title(b, 4)
        zone = {}
        if body:
            off = 1
            n = struct.unpack_from('>h', body, off)[0]; off += 2
            for _ in range(n):
                uid, tid = struct.unpack_from('>ih', body, off); off += 6
                zone[uid] = tid
        check('ZONE_STATE co {1000: 5}', zone.get(1000) == 5)
        a.send(TITLE, struct.pack('>bh', 1, 4))
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
        check('dang nhap lai: owned van co 5, 6 va fresh rong',
              5 in owned2 and 6 in owned2 and fresh2 == [])
        a2.sock.close()
    finally:
        db("UPDATE players SET xu=%s, title_id=0 WHERE user_id=1000" % (snap_xu or '0'))
        db("DELETE FROM player_titles WHERE user_id IN (1000, 1001)")
        db("DELETE FROM mg_stats WHERE user_id=1000")
        if snap_mg and snap_mg != '0':
            print('CANH BAO: mg_stats cua 1000 truoc test co %s dong - da xoa, kiem tra lai' % snap_mg)
    print('ALL PASS' if ok else 'CO FAIL')

main()
