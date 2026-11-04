# -*- coding: utf-8 -*-
"""Test sống Hub MiniGame + Tàu Chiến (cmd −113):
P1 tạo bàn → P2 vào → cả hai xếp tàu + sẵn sàng → P3 (tài khoản tạm
betest03, xoá sau test) vào xem → bắn trượt đổi lượt → P1 bắn chìm
hết hạm đội P2 → END. Sau đó test luồng mời giao hữu + chủ bàn rời.
"""
import socket
import struct
import sys
import time

sys.path.insert(0, '.')
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

MG = -113
FLEET_QTY = {1: 6, 2: 4, 3: 2, 4: 1, 5: 1}


def login(user, pw):
    """Trả (conn, gender) — gender đọc từ gói −4 (0 = chưa tạo nhân vật)."""
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    end = time.time() + 90
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=5.0)
        except (socket.timeout, TimeoutError):
            continue
        if cmd == -4:
            r = Rd(body)
            r.i32()
            n = r.u8()
            r.take(2 * n)
            gender = r.u8()
            return c, gender
    raise AssertionError(f"{user}: không nhận -4")


class Rd:
    def __init__(self, body):
        self.b = body
        self.o = 0

    def u8(self):
        v = self.b[self.o]
        self.o += 1
        return v

    def bool(self):
        return self.u8() != 0

    def i32(self):
        v = struct.unpack(">i", self.b[self.o:self.o + 4])[0]
        self.o += 4
        return v

    def i16(self):
        v = struct.unpack(">h", self.b[self.o:self.o + 2])[0]
        self.o += 2
        return v

    def utf(self):
        ln = struct.unpack(">H", self.b[self.o:self.o + 2])[0]
        v = self.b[self.o + 2:self.o + 2 + ln].decode("utf-8", "replace")
        self.o += 2 + ln
        return v

    def take(self, n):
        v = self.b[self.o:self.o + n]
        self.o += n
        return v


def mg(c, game, sub, extra=b""):
    c.send(MG, bytes([game, sub]) + extra)


def wait_mg(c, game, sub, timeout=8.0, label=""):
    """Chờ gói −113 đúng (game, sub); bỏ qua các gói khác."""
    end = time.time() + timeout
    while time.time() < end:
        cmd, body = c.recv(timeout=max(0.5, end - time.time()))
        if cmd != MG:
            continue
        r = Rd(body)
        g, s = r.u8(), r.u8()
        if g == game and s == sub:
            return r
    raise AssertionError(f"không nhận được mg game={game} sub={sub} ({label})")


def rand_fleet(rng):
    ships = []
    for length in (5, 4, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1):
        ships.append([length, -1, -1, 0])

    def cells(sh):
        return [(sh[1] + (i if sh[3] else 0), sh[2] + (0 if sh[3] else i))
                for i in range(sh[0])]

    def ok(sh):
        cs = cells(sh)
        for (r, cc) in cs:
            if not (0 <= r < 10 and 0 <= cc < 10):
                return False
        for other in ships:
            if other is sh or other[1] < 0:
                continue
            for (r1, c1) in cs:
                for (r2, c2) in cells(other):
                    if max(abs(r1 - r2), abs(c1 - c2)) < 2:
                        return False
        return True

    def place(idx):
        if idx == len(ships):
            return True
        sh = ships[idx]
        cand = [(r, cc, v) for r in range(10) for cc in range(10) for v in (0, 1)]
        rng.shuffle(cand)
        for (r, cc, v) in cand:
            sh[1], sh[2], sh[3] = r, cc, v
            if ok(sh) and place(idx + 1):
                return True
            sh[1] = -1
        return False

    assert place(0), "không xếp được hạm đội"
    return ships


def place_and_ready(c, fleet, label):
    body = bytes([len(fleet)])
    for (length, r, cc, v) in fleet:
        body += bytes([length, r, cc, v])
    mg(c, 4, 2, body)  # PLACE_SHIPS
    rd = wait_mg(c, 4, 3, label=label + " place_result")
    assert rd.bool(), label + ": PLACE_RESULT phải ok"
    assert rd.utf() == ""
    mg(c, 4, 4)  # READY


def ship_cells(fleet):
    out = []
    for (length, r, cc, v) in fleet:
        for i in range(length):
            out.append((r + (i if v else 0), cc + (0 if v else i)))
    return out


def main():
    import random
    # --- tài khoản khán giả tạm betest03 (đăng ký qua game nếu chưa có;
    # xoá cuối test) ---
    from live_khu_test import register
    uid3 = db("SELECT id FROM users WHERE username = 'betest03'").strip()
    if not uid3:
        register('betest03', 'abcd1234')
        time.sleep(2)
        uid3 = db("SELECT id FROM users WHERE username = 'betest03'").strip()
    assert uid3, "đăng ký betest03 hỏng"
    print("betest03 id", uid3)

    p1, _ = login('betest01', 'abcd1234')
    time.sleep(5)
    p2, _ = login('betest02', 'abcd1234')
    time.sleep(5)
    print("login ok")

    # P1 tạo bàn công khai
    mg(p1, 0, 1, bytes([4, 0]))  # CREATE game 4, public
    rd = wait_mg(p1, 0, 10, label="p1 joined")
    room_id = rd.i32()
    seat = rd.u8()
    assert seat == 0, seat
    print("room", room_id)

    # P2 xem danh sách rồi vào bàn
    mg(p2, 0, 0)  # LIST
    rd = wait_mg(p2, 0, 0, label="list")
    n = rd.i16()
    found = None
    for _ in range(n):
        rid = rd.i32(); g = rd.u8(); n1 = rd.utf(); n2 = rd.utf()
        sp = rd.u8(); pv = rd.bool(); st = rd.u8()
        if rid == room_id:
            found = (n1, n2, st)
    assert found and found[0] == 'betest01' and found[2] == 0, found
    mg(p2, 0, 2, struct.pack(">i", room_id))  # JOIN
    rd = wait_mg(p2, 0, 10, label="p2 joined")
    assert rd.i32() == room_id and rd.u8() == 1
    wait_mg(p1, 0, 11, label="p1 opp_joined")
    wait_mg(p1, 4, 1, label="p1 start_place")
    wait_mg(p2, 4, 1, label="p2 start_place")
    print("ca hai vao pha xep tau")

    # xếp tàu + sẵn sàng
    f1 = rand_fleet(random.Random(11))
    f2 = rand_fleet(random.Random(22))
    place_and_ready(p1, f1, "p1")
    place_and_ready(p2, f2, "p2")
    rd = wait_mg(p1, 4, 6, label="p1 start_battle")
    assert rd.u8() == 0  # ghế 0 bắn trước
    wait_mg(p2, 4, 6, label="p2 start_battle")
    print("battle bat dau")

    # P3 vào xem (tài khoản chưa có nhân vật thì tạo trước — server chỉ
    # gắn user sau khi có nhân vật)
    p3, g3 = login('betest03', 'abcd1234')
    time.sleep(2)
    if g3 == 0:
        parts = [89, 88, 0, 4, 14]
        body = bytes([1, len(parts)]) + b"".join(
            struct.pack(">h", p) for p in parts)
        body += write_utf("Khan Gia")
        p3.send(-35, body)
        ok = False
        for _ in range(20):
            cmd, b = p3.recv(timeout=5.0)
            if cmd == -35:
                assert b[0] == 1, "tạo nhân vật betest03 thất bại"
                ok = True
                break
        assert ok, "không nhận trả lời -35"
        time.sleep(1)
    mg(p3, 0, 7, struct.pack(">i", room_id))  # WATCH
    rd = wait_mg(p3, 0, 10, label="p3 joined")
    rd.i32(); assert rd.u8() == 2
    rd = wait_mg(p3, 4, 13, label="p3 snapshot")
    rd.i32(); status = rd.u8()
    assert status == 2, status
    rd.utf(); rd.utf(); rd.u8()
    s0 = rd.take(100); s1 = rd.take(100)
    assert s0 == bytes(100) and s1 == bytes(100)
    print("p3 xem duoc snapshot")

    cells2 = ship_cells(f2)
    water2 = [(r, cc) for r in range(10) for cc in range(10)
              if (r, cc) not in cells2]
    cells1 = ship_cells(f1)
    water1 = [(r, cc) for r in range(10) for cc in range(10)
              if (r, cc) not in cells1]

    # P1 bắn trượt -> đổi lượt
    mg(p1, 4, 7, bytes(water2[0]))  # SHOOT
    for c, lab in ((p1, "p1"), (p2, "p2"), (p3, "p3")):
        rd = wait_mg(c, 4, 8, label=lab + " shot_result miss")
        assert (rd.u8(), rd.u8(), rd.u8(), rd.u8()) == (0, water2[0][0], water2[0][1], 0)
    rd = wait_mg(p1, 4, 9, label="change turn")
    assert rd.u8() == 1
    wait_mg(p2, 4, 9, label="change turn p2")
    # P2 bắn trượt -> về lượt P1
    mg(p2, 4, 7, bytes(water1[0]))
    rd = wait_mg(p2, 4, 8, label="p2 shot miss")
    assert rd.u8() == 1 and rd.u8() == water1[0][0]
    rd.u8(); assert rd.u8() == 0
    rd = wait_mg(p1, 4, 9, label="change back")
    assert rd.u8() == 0
    wait_mg(p2, 4, 9, label="change back p2")
    print("doi luot dung")

    # P1 bắn trúng hết hạm đội P2
    sunk_expect = {}
    for (length, r, cc, v) in f2:
        cs = [(r + (i if v else 0), cc + (0 if v else i)) for i in range(length)]
        for cell in cs[:-1]:
            sunk_expect[cell] = 1
        sunk_expect[cs[-1]] = 2
    end_seen = {}
    for cell in cells2:
        mg(p1, 4, 7, bytes(cell))
        rd = wait_mg(p1, 4, 8, label="p1 hit")
        assert rd.u8() == 0
        assert (rd.u8(), rd.u8()) == cell
        res = rd.u8()
        assert res == sunk_expect[cell], (cell, res, sunk_expect[cell])
        if res == 2:
            rd.u8()  # sunkLen
    rd = wait_mg(p1, 4, 10, label="p1 end")
    assert rd.u8() == 0 and rd.u8() == 0  # winner 0, reason 0
    rd = wait_mg(p2, 4, 10, label="p2 end")
    assert rd.u8() == 0
    wait_mg(p3, 4, 10, label="p3 end")
    print("END dung: p1 thang")

    # rời bàn sau ván
    mg(p1, 0, 6)
    mg(p2, 0, 6)
    mg(p3, 0, 6)
    time.sleep(0.5)

    # --- luồng mời giao hữu ---
    mg(p1, 0, 4, struct.pack(">i", 1001) + bytes([4]))  # INVITE betest02
    rd = wait_mg(p2, 0, 20, label="invite_in")
    assert rd.i32() == 1000 and rd.utf() == 'betest01' and rd.u8() == 4
    mg(p2, 0, 5, struct.pack(">i", 1000) + bytes([1]))  # REPLY accept
    rd = wait_mg(p1, 0, 10, label="p1 friendly joined")
    rd.i32(); assert rd.u8() == 0
    rd = wait_mg(p2, 0, 10, label="p2 friendly joined")
    rd.i32(); assert rd.u8() == 1
    wait_mg(p1, 4, 1, label="friendly start_place p1")
    wait_mg(p2, 4, 1, label="friendly start_place p2")
    # chủ bàn (ghế 0) rời -> bàn giải tán, ghế 1 nhận OPP_LEFT
    mg(p1, 0, 6)
    wait_mg(p2, 0, 12, label="p2 opp_left dissolve")
    print("moi giao huu + giai tan dung")

    for c in (p1, p2, p3):
        try:
            c.sock.close()
        except Exception:
            pass
    time.sleep(3)
    db(f"DELETE FROM farm_data WHERE user_id = {uid3}")
    db(f"DELETE FROM players WHERE user_id = {uid3}")
    db(f"DELETE FROM users WHERE id = {uid3}")
    print("TAUCHIEN LIVE TEST: ALL PASS")


if __name__ == "__main__":
    main()
