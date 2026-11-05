# -*- coding: utf-8 -*-
"""Test song TACH CHONG [PORT REMOVE_ITEM_STACK, goi -119] + duoi
quantity cua goi -47, bang betest01:
1. Nap gio = 1 chong 446 x12 -> mo gio -41 nhan -47: qty 12.
2. Gui -119 {446, 5} -> -47 moi: hai chong 7 va 5.
3. Gui -119 {446, 7} (tach het chong) -> bi chan, danh sach giu nguyen.
4. Ngat ket noi -> DB chests co 2 entry 446 quantity 7 va 5.
Khoi phuc chests goc khi xong."""
import struct
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login
from live_preview_test import join_map
from live_createchar_test import db

ok = True

def check(name, cond):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name)
    ok = ok and cond

def read_utf_off(body, off):
    ln = struct.unpack('>H', body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode('utf-8', 'replace'), off + 2 + ln

def parse_container(body):
    n = struct.unpack_from('>h', body, 0)[0]
    off = 2
    items = []
    for _ in range(n):
        pid = struct.unpack_from('>h', body, off)[0]; off += 2
        off += 1
        _, off = read_utf_off(body, off)
        items.append([pid, 1])
    cap = None
    if off < len(body):
        cap = body[off]; off += 1
    if off < len(body) and body[off] == 1:
        off += 1
        for it in items:
            it[1] = body[off]; off += 1
    return items, cap

def wait_container(c, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=2.0)
        except (TimeoutError, OSError):
            continue
        if cmd == -47:
            return parse_container(body)
    return None, None

def main():
    snap = db("SELECT chests FROM players WHERE user_id=1000")
    db('UPDATE players SET chests=\'[{"id":446,"expired":-1,"quantity":12}]\' WHERE user_id=1000')
    try:
        c, gender = login('betest01', 'abcd1234')
        assert gender != 0, 'betest01 chua co nhan vat'
        assert join_map(c), 'vao map'
        c.send(-47, struct.pack('>i', 1000))
        items, cap = wait_container(c)
        check('gio 1 chong 446 x12 (cap=%s)' % cap,
              items is not None and len(items) == 1
              and items[0][0] == 446 and items[0][1] == 12)
        c.send(-119, struct.pack('>hh', 446, 5))
        items, _ = wait_container(c)
        qs = sorted(q for pid, q in (items or []) if pid == 446)
        check('tach 5 -> hai chong 7 va 5', qs == [5, 7] and len(items) == 2)
        c.send(-119, struct.pack('>hh', 446, 7))
        time.sleep(1.5)
        c.send(-47, struct.pack('>i', 1000))
        items, _ = wait_container(c)
        qs = sorted(q for pid, q in (items or []) if pid == 446)
        check('tach het chong bi chan, van 7 va 5', qs == [5, 7])
        c.sock.close()
        time.sleep(2.5)
        dbc = db("SELECT chests FROM players WHERE user_id=1000").replace(' ', '')
        check('DB luu 2 chong 446 (7 va 5)',
              '"quantity":7' in dbc and '"quantity":5' in dbc)
    finally:
        db("UPDATE players SET chests='%s' WHERE user_id=1000" % snap.replace("'", "''"))
    print('ALL PASS' if ok else 'CO FAIL')

main()
