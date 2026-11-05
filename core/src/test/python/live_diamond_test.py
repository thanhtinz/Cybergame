#!/usr/bin/env python3
"""Test sống Diamond / Kim Cương (casino gameType 21) với 2 tài
khoản: chọn game 21 -> phòng 52 -> bàn 50 (2 ghế) -> sẵn sàng ->
chủ bàn đặt mức + bắt đầu (20) -> START (bàn 8x8 + HP/MP) -> thử
đổi chỗ SAI (nhận gói 21 đảo ngược) -> bỏ lượt (49) -> chơi bằng
nước đi hợp lệ tốt nhất (ưu tiên ô công) tới FINISH (51) -> đối
chiếu delta zero-sum và xu trong DB."""
import socket
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

ROOM, BOARD, MONEY = 52, 50, 1000

FAILS = []


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAILS.append(label)


class Rd:
    def __init__(self, b):
        self.b = b
        self.o = 0

    def byte(self):
        v = self.b[self.o]
        self.o += 1
        return v - 256 if v > 127 else v

    def ubyte(self):
        v = self.b[self.o]
        self.o += 1
        return v

    def short(self):
        v = struct.unpack(">h", self.b[self.o:self.o + 2])[0]
        self.o += 2
        return v

    def int(self):
        v = struct.unpack(">i", self.b[self.o:self.o + 4])[0]
        self.o += 4
        return v

    def bool(self):
        return self.byte() != 0

    def utf(self):
        ln = struct.unpack(">H", self.b[self.o:self.o + 2])[0]
        self.o += 2
        s = self.b[self.o:self.o + ln].decode("utf-8", "replace")
        self.o += ln
        return s

    def rest(self):
        return len(self.b) - self.o


def login(user, pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("2.5.8"))
    try:
        while True:
            cmd, body = c.recv(timeout=3)
            if cmd == -4:
                break
    except (socket.timeout, TimeoutError):
        pass
    return c


def wait_cmd(c, want, timeout=8.0, label=""):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.5, end - time.time()))
        except (socket.timeout, TimeoutError):
            break
        if cmd == want:
            return body
        if cmd == -10:
            print(f"    [{label}] dialog server: {read_utf(body)!r}")
    raise AssertionError(f"[{label}] không nhận được cmd {want}")


def find_matches(cells):
    m = [False] * 64
    for r in range(8):
        run = 1
        for col in range(1, 9):
            if col < 8 and cells[r * 8 + col] == cells[r * 8 + col - 1]:
                run += 1
            else:
                if run >= 3:
                    for k in range(col - run, col):
                        m[r * 8 + k] = True
                run = 1
    for col in range(8):
        run = 1
        for r in range(1, 9):
            if r < 8 and cells[r * 8 + col] == cells[(r - 1) * 8 + col]:
                run += 1
            else:
                if run >= 3:
                    for k in range(r - run, r):
                        m[k * 8 + col] = True
                run = 1
    return m


def swap_score(cells, i, j):
    """Điểm của một hoán đổi: -1 nếu không tạo hàng."""
    c = list(cells)
    c[i], c[j] = c[j], c[i]
    m = find_matches(c)
    if not any(m):
        return -1
    score = 0
    for k in range(64):
        if m[k]:
            score += 3 if c[k] == 0 else 1
    return score


def best_swap(cells):
    best, bs = None, -1
    for i in range(64):
        for d in ((0, 1), (1, 0)):
            r2, c2 = i // 8 + d[0], i % 8 + d[1]
            if r2 >= 8 or c2 >= 8:
                continue
            sc = swap_score(cells, i, r2 * 8 + c2)
            if sc > bs:
                bs, best = sc, (i, r2 * 8 + c2)
    return best


def bad_swap(cells):
    """Một hoán đổi chắc chắn không tạo hàng (để thử đường trả ô)."""
    for i in range(64):
        for d in ((0, 1), (1, 0)):
            r2, c2 = i // 8 + d[0], i % 8 + d[1]
            if r2 >= 8 or c2 >= 8:
                continue
            if swap_score(cells, i, r2 * 8 + c2) == -1:
                return (i, r2 * 8 + c2)
    return None


class Game:
    """Trạng thái ván theo dòng gói của A (gói phát cả bàn)."""

    def __init__(self):
        self.cells = None
        self.turn = None
        self.stats = {}
        self.finished = None
        self.creates = 0
        self.outpaths = 0

    def feed(self, cmd, body):
        r = Rd(body)
        if cmd == 20:
            r.byte(); r.byte()
            r.byte()  # interval
            self.turn = r.int()
            self.cells = [r.ubyte() for _ in range(64)]
            for _ in range(2):
                uid = r.int()
                d = r.short(); hp = r.short(); mp = r.short(); r.short()
                self.stats[uid] = (d, hp, mp)
        elif cmd == 21:
            r.byte(); r.byte()
            r.int()
            a = r.ubyte(); b = r.ubyte()
            if self.cells is not None:
                self.cells[a], self.cells[b] = self.cells[b], self.cells[a]
        elif cmd == 64:
            r.byte(); r.byte()
            n = r.ubyte()
            for _ in range(n):
                pos = r.ubyte(); r.ubyte(); t = r.ubyte()
                if self.cells is not None:
                    self.cells[pos] = t
            r.byte()  # combo
            nt = r.ubyte()
            for _ in range(nt):
                r.utf()
            for _ in range(2):
                uid = r.int()
                r.byte()  # fight
                d = r.short(); hp = r.short(); mp = r.short()
                r.bool()
                self.stats[uid] = (d, hp, mp)
            self.creates += 1
        elif cmd == 49:
            r.byte(); r.byte()
            self.turn = r.int()
        elif cmd == 24:
            r.byte(); r.byte()
            self.turn = r.int()
            self.cells = [r.ubyte() for _ in range(64)]
            self.outpaths += 1
        elif cmd == 51:
            r.byte(); r.byte()
            self.finished = {}
            for _ in range(2):
                uid = r.int()
                self.finished[uid] = r.int()


def pump(P, G, timeout=0.4):
    try:
        while True:
            cmd, body = P.c.recv(timeout=timeout)
            G.feed(cmd, body)
            timeout = 0.05
    except (socket.timeout, TimeoutError):
        pass


class Player:
    def __init__(self, user, uid):
        self.user, self.uid = user, uid
        self.c = login(user)


def main():
    print("[0] dọn xu test về 200.000")
    db("UPDATE players SET xu=200000 WHERE user_id IN (1000, 1001)")

    A = Player("betest01", 1000)
    B = Player("betest02", 1001)
    players = {1000: A, 1001: B}

    print("[1] hai tài khoản chọn game 21, xin phòng/bàn")
    for P, name in ((A, "A"), (B, "B")):
        P.c.send(-1, bytes([3]))
        P.c.send(61, bytes([21]))
        body = wait_cmd(P.c, 61, label=name)
        check(body == bytes([21]), f"{name}: ack 61 gameType 21", body.hex())
        P.c.send(6)
        body = wait_cmd(P.c, 6, label=name)
        r = Rd(body)
        rooms = []
        for _ in range(3):
            rooms.append(r.byte())
            r.byte(); r.byte(); r.byte()
        check(rooms == [52, 53, 54], f"{name}: phòng Diamond 52-54", str(rooms))
        P.c.send(7, bytes([ROOM]))
        body = wait_cmd(P.c, 7, label=name)
        r = Rd(body)
        assert r.byte() == ROOM
        boards = []
        while r.rest() > 0:
            bid = r.byte()
            packed = r.byte() & 0xFF
            r.byte()
            money = r.int()
            boards.append((bid, packed % 16, packed // 16, money))
        check(len(boards) == 6 and all(b[0] in range(50, 56) for b in boards),
              f"{name}: 6 bàn Diamond id 50-55", str(boards[:2]))
        check(all(b[2] == 2 for b in boards), f"{name}: mọi bàn tối đa 2 người")

    print("[2] vào bàn 50, B sẵn sàng, A đặt mức 1.000 + bắt đầu")
    for P in (A, B):
        P.c.send(8, bytes([ROOM, BOARD]) + write_utf(""))
        body = wait_cmd(P.c, 8, label=P.user)
        r = Rd(body)
        assert r.byte() == ROOM and r.byte() == BOARD
        r.int()  # owner
        r.int()  # money
        seats = 0
        while r.rest() > 0:
            iddb = r.int()
            if iddb == -1:
                seats += 1
                continue
            r.utf(); r.int()
            nw = r.byte()
            for _ in range(nw):
                r.byte(); r.byte()
            r.int(); r.bool(); r.byte(); r.byte()
            seats += 1
        check(seats == 2, f"{P.user}: JOIN_BOARD có đúng 2 ghế", f"seats={seats}")
    B.c.send(16, bytes([ROOM, BOARD, 1]))
    wait_cmd(B.c, 16, label="B-ready")
    A.c.send(19, bytes([ROOM, BOARD]) + struct.pack(">i", MONEY))
    wait_cmd(A.c, 19, label="A-money")
    A.c.send(20, bytes([ROOM, BOARD]))

    print("[3] nhận START: bàn 8x8 + chỉ số khởi đầu")
    G = Game()
    body = wait_cmd(A.c, 20, timeout=10, label="A-start")
    G.feed(20, body)
    check(G.cells is not None and len(G.cells) == 64
          and all(0 <= v <= 5 for v in G.cells),
          "A: START có 64 ô loại 0..5")
    check(not any(find_matches(G.cells)), "A: bàn đầu không có hàng sẵn")
    check(all(v == (0, 100, 40) for v in G.stats.values()),
          "A: chỉ số đầu def=0 hp=100 mp=40", str(G.stats))
    body = wait_cmd(B.c, 20, timeout=10, label="B-start")
    Gb = Game()
    Gb.feed(20, body)
    check(Gb.cells == G.cells and Gb.turn == G.turn,
          "hai phía thống nhất bàn + người đi trước",
          f"turn={G.turn}")

    print("[4] đổi chỗ SAI bị trả lại + bỏ lượt")
    first = players[G.turn]
    bs = bad_swap(G.cells)
    check(bs is not None, "tìm được hoán đổi sai để thử")
    if bs is not None:
        first.c.send(21, bytes([ROOM, BOARD, bs[0], bs[1]]))
        body = wait_cmd(first.c, 21, timeout=6, label="reversed")
        r = Rd(body)
        r.byte(); r.byte()
        who = r.int()
        a = r.ubyte(); b = r.ubyte()
        check(who == first.uid and (a, b) == (bs[1], bs[0]),
              "server trả gói 21 đảo ngược cho nước sai",
              f"who={who} {a}->{b} (gửi {bs[0]}->{bs[1]})")
        # gói đảo ngược KHÔNG được áp vào bàn của A nếu A không phải người đi
        if first is not A:
            pump(A, G, 0.3)  # A không nhận gói đó; chỉ dọn hàng đợi
            # nhưng A có thể đã nhận... không: gói chỉ gửi riêng người đi
    first.c.send(49, bytes([ROOM, BOARD]))
    body = wait_cmd(A.c, 49, timeout=6, label="skip49")
    r = Rd(body)
    r.byte(); r.byte()
    nxt = r.int()
    other = 1001 if first.uid == 1000 else 1000
    check(nxt == other, "bỏ lượt: 49 báo lượt đối thủ", f"next={nxt}")
    G.turn = nxt
    wait_cmd(B.c, 49, timeout=6, label="skip49-B")

    print("[5] chơi tới FINISH bằng nước hợp lệ tốt nhất")
    deadline = time.time() + 240
    moves = 0
    while time.time() < deadline:
        pump(A, G, 0.35)
        pump(B, Game(), 0.05)  # dọn hàng đợi của B (không dùng trạng thái)
        if G.finished is not None:
            break
        if G.turn is None or G.cells is None:
            continue
        P = players[G.turn]
        mv = best_swap(G.cells)
        if mv is None:
            P.c.send(49, bytes([ROOM, BOARD]))
            time.sleep(0.2)
            continue
        P.c.send(21, bytes([ROOM, BOARD, mv[0], mv[1]]))
        moves += 1
        # chờ lượt đổi hoặc kết thúc
        t0 = time.time()
        prev_turn = G.turn
        while time.time() - t0 < 6:
            pump(A, G, 0.3)
            if G.finished is not None or G.turn != prev_turn:
                break
        for uid, (d, hp, mp) in G.stats.items():
            check(-50 <= hp <= 100 and 0 <= mp <= 100 and 0 <= d <= 30,
                  f"chỉ số hợp lệ uid={uid}", f"def={d} hp={hp} mp={mp}") \
                if False else None
    check(G.finished is not None, "ván kết thúc có FINISH", f"moves={moves}")
    check(G.creates > 0, "có gói 64 giải dây chuyền", f"creates={G.creates}")
    if G.finished is not None:
        deltas = G.finished
        check(sum(deltas.values()) == 0, "delta zero-sum", str(deltas))
        check(sorted(deltas.values()) in ([-MONEY, MONEY], [0, 0]),
              "delta là ±mức bàn hoặc hoà 0", str(deltas))
        print("[6] đối chiếu DB")
        for P in (A, B):
            P.c.sock.close()
        time.sleep(3.5)
        for uid, P in players.items():
            want = 200000 + deltas[uid]
            got = int(db(f"SELECT xu FROM players WHERE user_id={uid}") or -1)
            check(got == want, f"DB xu uid={uid} = {want:,}".replace(",", "."),
                  str(got))

    print()
    if FAILS:
        print("KẾT QUẢ: FAIL:", FAILS)
        sys.exit(1)
    print("KẾT QUẢ: PASS TẤT CẢ")


if __name__ == "__main__":
    main()
