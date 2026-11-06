#!/usr/bin/env python3
"""Test sống CLAN đợt 2 (2026-11-05, docs/clan-parity-diff.md):
logo clan GỐC (icon 0..3) + ngày thành lập (dob) trong gói INFO +
ĐỔI THÔNG TIN clan (sub 32 [PORT] CHANGE_CLAN) + icon clan chạy
trong đuôi gói zone 50/51 cho người khác thấy cạnh tên.
A tạo clan icon 2 -> INFO có dob dd/MM/yyyy + SET_PLAYER icon 2 ->
B xin vào, A duyệt -> A đổi info (tên/slogan/icon 3) -> B vào zone
map 10: gói 50 B nhận có icon clan của A = 3 ở đuôi; A nhận gói 51
của B có icon clan = 3. Dọn dẹp cuối test.
"""
import re
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

FAILS = []
CLAN = -112
NAMES = ("Clan Logo Test", "Clan Logo Moi")


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAILS.append(label)


def login(user, pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    while True:
        cmd, body = c.recv(timeout=5)
        if cmd == -4:
            break
    c.q = []
    return c


def pump(c, secs=0.5):
    end = time.time() + secs
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cmd, body))
        except Exception:
            break


def wait_clan_latest(c, sub, timeout=6.0):
    c.q = []
    end = time.time() + timeout
    got = None
    while time.time() < end:
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            if cc == CLAN and len(bb) >= 1 and bb[0] == sub:
                got = bb[1:]
                end = min(end, time.time() + 0.35)
        except Exception:
            pass
    return got


def wait_for(c, cmd_want, timeout=6.0):
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == cmd_want:
                c.q.pop(i)
                return bb
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
    return None


def clan_send(c, sub, payload=b""):
    c.send(CLAN, bytes([sub]) + payload)


def scan_clan_last(c, sub):
    """Như scan_clan nhưng lấy gói MỚI NHẤT khớp sub (INFO gửi nhiều
    lần dồn trong hàng đợi — bản cũ gây đọc nhầm)."""
    got = None
    keep = []
    for cc, bb in c.q:
        if cc == CLAN and len(bb) >= 1 and bb[0] == sub:
            got = bb[1:]
        else:
            keep.append((cc, bb))
    c.q = keep
    return got


def scan_clan(c, sub):
    """Tìm gói clan sub trong hàng đợi đã bơm (không xả)."""
    for i, (cc, bb) in enumerate(c.q):
        if cc == CLAN and len(bb) >= 1 and bb[0] == sub:
            c.q.pop(i)
            return bb[1:]
    return None


def parse_info(body):
    """INFO clan: trả dict gồm id/name/slogan/icon/dob."""
    o = 0
    has = body[o]; o += 1
    if not has:
        return {"has": 0}
    cid = struct.unpack_from(">i", body, o)[0]; o += 4
    name = read_utf(body, o); o += 2 + len(name.encode("utf-8"))
    slogan = read_utf(body, o); o += 2 + len(slogan.encode("utf-8"))
    icon = struct.unpack_from(">h", body, o)[0]; o += 2
    o += 1 + 4 + 4 + 4 + 4      # level, exp, expNeed, fundXu, fundLuong
    o += 1 + 1 + 1              # role, memCount, maxMem
    o += 4 + 4 + 4              # myPoints, myDonXu, myDonLuong
    n = body[o]; o += 1
    for _ in range(n):
        o += 4
        nm = read_utf(body, o); o += 2 + len(nm.encode("utf-8"))
        o += 1 + 4 + 1 + 1
    dob = ""
    if o < len(body):
        dob = read_utf(body, o); o += 2 + len(dob.encode("utf-8"))
    return {"has": 1, "id": cid, "name": name, "slogan": slogan,
            "icon": icon, "dob": dob}


def parse_join50(body):
    """Gói 50: trả {userId: (weddingIcon, clanIcon)} theo thuật toán
    client (ZonePlayers.parseJoin) — đuôi có 2 mảng short theo người."""
    o = 0
    o += 1 + 1 + 2 + 2
    num = body[o]; o += 1
    ids = []
    for _ in range(num):
        uid = struct.unpack_from(">i", body, o)[0]; o += 4
        nm = read_utf(body, o); o += 2 + len(nm.encode("utf-8"))
        n = body[o]; o += 1 + 2 * n
        o += 2 + 2 + 1
        ids.append(uid)
    o += num                    # direct
    o += num                    # 101
    o += 2 * num                # -1
    o += 1 + 1
    o += 2                      # nMapItems (chỉ đọc bỏ)
    ntypes = body[o]; o += 1
    for _ in range(ntypes):
        o += 1 + 2 + 1 + 2 + 2
        npos = body[o]; o += 1 + 2 * npos
    nitems = body[o]; o += 1 + 4 * nitems
    wed = [struct.unpack_from(">h", body, o + 2 * i)[0] for i in range(num)]
    o += 2 * num
    clan = [struct.unpack_from(">h", body, o + 2 * i)[0] for i in range(num)]
    return {uid: (wed[i], clan[i]) for i, uid in enumerate(ids)}


def parse_join51(body):
    o = 0
    uid = struct.unpack_from(">i", body, o)[0]; o += 4
    nm = read_utf(body, o); o += 2 + len(nm.encode("utf-8"))
    n = body[o]; o += 1 + 2 * n
    o += 2 + 2 + 1              # x, y, role
    o += 1 + 2                  # byte -1, short idImg
    wed = struct.unpack_from(">h", body, o)[0]; o += 2
    clan = struct.unpack_from(">h", body, o)[0]; o += 2
    return uid, wed, clan


def cleanup():
    for nm in NAMES:
        db("DELETE cm FROM clan_members cm JOIN clans cl ON cl.id = cm.clan_id "
           f"WHERE cl.name = '{nm}'")
        db("DELETE FROM clan_items_own WHERE clan_id IN "
           f"(SELECT id FROM clans WHERE name = '{nm}')")
        db("DELETE FROM clan_quest_state WHERE clan_id IN "
           f"(SELECT id FROM clans WHERE name = '{nm}')")
        db("DELETE FROM clan_invites WHERE clan_id IN "
           f"(SELECT id FROM clans WHERE name = '{nm}')")
        db(f"DELETE FROM clans WHERE name = '{nm}'")
    db("UPDATE players SET xu = 200000 WHERE user_id IN (1000, 1001)")


def main():
    cleanup()
    a = login("betest01")
    b = login("betest02")
    try:
        # --- A tạo clan với logo gốc #2 ---
        clan_send(a, 0, write_utf(NAMES[0]) + write_utf("Logo goc day")
                  + struct.pack(">h", 2))
        pump(a, 1.5)
        info = scan_clan(a, 1)
        check(info is not None, "A nhận INFO sau tạo clan")
        pi = parse_info(info) if info else {"has": 0}
        check(pi.get("icon") == 2, "INFO icon clan = 2 (logo gốc)", str(pi.get("icon")))
        check(re.match(r"^\d{2}/\d{2}/\d{4}$", pi.get("dob", "") or "") is not None,
              "INFO có ngày thành lập dd/MM/yyyy", str(pi.get("dob")))
        clan_id = pi.get("id", -1)
        sp = scan_clan(a, 26)
        check(sp is not None and len(sp) >= 6
              and struct.unpack_from(">h", sp, 4)[0] == 2,
              "SET_PLAYER icon = 2 sau tạo clan", str(sp))

        # --- B xin vào, A duyệt ---
        clan_send(b, 3, struct.pack(">i", clan_id) + b"\x00")
        pump(a, 0.8)
        clan_send(a, 5, struct.pack(">i", 1001))
        pump(b, 0.8)
        clan_send(b, 1)
        binfo = wait_clan_latest(b, 1)
        check(binfo is not None and binfo[0] == 1, "B vào được clan")

        # --- A đổi thông tin clan (sub 32): tên/slogan/logo 3 ---
        clan_send(a, 32, write_utf(NAMES[1]) + write_utf("Slogan moi nhat")
                  + struct.pack(">h", 3))
        pump(a, 1.5)
        info2 = scan_clan_last(a, 1)
        pi2 = parse_info(info2) if info2 else {"has": 0}
        check(pi2.get("name") == NAMES[1], "Đổi tên clan OK", str(pi2.get("name")))
        check(pi2.get("slogan") == "Slogan moi nhat", "Đổi slogan OK")
        check(pi2.get("icon") == 3, "Đổi logo clan = 3 OK", str(pi2.get("icon")))
        sp2 = scan_clan(a, 26)
        check(sp2 is not None and len(sp2) >= 6
              and struct.unpack_from(">h", sp2, 4)[0] == 3,
              "SET_PLAYER icon = 3 sau đổi thông tin", str(sp2))

        # --- Zone map 10: icon clan chạy qua gói 50/51 ---
        a.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
        check(wait_for(a, 50, 6.0) is not None, "A vào zone map 10")
        b.q = []
        b.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
        j50 = wait_for(b, 50, 6.0)
        check(j50 is not None, "B vào zone map 10")
        if j50 is not None:
            try:
                tail = parse_join50(j50)
                check(tail.get(1000, (None, None))[1] == 3,
                      "Gói 50: icon clan của A (đuôi) = 3", str(tail))
            except Exception as e:
                check(False, "Gói 50: parse đuôi", str(e))
        body51 = wait_for(a, 51, 6.0)
        check(body51 is not None, "A nhận gói 51 của B")
        if body51 is not None:
            uid, wed, clan = parse_join51(body51)
            check(uid == 1001 and clan == 3,
                  "Gói 51: icon clan của B = 3", f"uid={uid} clan={clan}")

        # --- dọn: B rời, A giải tán ---
        clan_send(b, 7)
        pump(a, 0.6)
        clan_send(a, 7)
        pump(a, 0.6)
    finally:
        try: a.sock.close()
        except Exception: pass
        try: b.sock.close()
        except Exception: pass
        time.sleep(1)
        cleanup()
    print("ALL PASS" if not FAILS else f"FAILURES: {FAILS}")
    return 0 if not FAILS else 1


if __name__ == "__main__":
    sys.exit(main())
