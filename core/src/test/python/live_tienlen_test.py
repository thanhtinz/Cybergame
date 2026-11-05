#!/usr/bin/env python3
"""Test sống Tiến Lên (casino gameType 3) với 2 tài khoản: chọn game
3 -> phòng 46 -> bàn 30 (4 ghế) -> sẵn sàng -> chủ bàn bắt đầu (20)
-> nhận bài (20 S->C) -> đánh/bỏ lượt theo luật đơn giản (lá đơn
thấp nhất đè được, không thì bỏ) -> FINISH (51) 2 người + STOP (50)
-> đối chiếu delta (N=2: nhất +mức, chót -mức) và xu trong DB."""
import socket
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

ROOM, BOARD, MONEY = 46, 30, 1000

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


class Player:
    def __init__(self, user, uid):
        self.user, self.uid = user, uid
        self.c = login(user)
        self.hand = []
        self.finished = {}   # id -> (pos, dmoney)
        self.stopped = False

    def pump(self, timeout=0.5):
        """Đọc mọi gói đang chờ; cập nhật tay bài/trạng thái ván."""
        events = []
        try:
            while True:
                cmd, body = self.c.recv(timeout=timeout)
                events.append((cmd, body))
                timeout = 0.05
        except (socket.timeout, TimeoutError):
            pass
        return events


def parse_start(body):
    r = Rd(body)
    assert r.byte() == ROOM and r.byte() == BOARD
    interval = r.byte()
    cards = [r.byte() & 0xFF for _ in range(13)]
    first = r.int()
    return interval, cards, first


def main():
    print("[0] dọn xu test về 200.000")
    db("UPDATE players SET xu=200000 WHERE user_id IN (1000, 1001)")

    A = Player("betest01", 1000)
    B = Player("betest02", 1001)
    print("[1] hai tài khoản chọn game 3, xin phòng/bàn")
    for P, name in ((A, "A"), (B, "B")):
        P.c.send(-1, bytes([3]))
        P.c.send(61, bytes([3]))
        body = wait_cmd(P.c, 61, label=name)
        check(body == bytes([3]), f"{name}: ack 61 gameType 3", body.hex())
        P.c.send(6)
        body = wait_cmd(P.c, 6, label=name)
        r = Rd(body)
        rooms = []
        for _ in range(3):  # mỗi phòng: id, free, wait, lv
            rooms.append(r.byte())
            r.byte(); r.byte(); r.byte()
        check(rooms == [46, 47, 48], f"{name}: phòng TL 46-48", str(rooms))
        P.c.send(7, bytes([ROOM]))
        body = wait_cmd(P.c, 7, label=name)
        r = Rd(body)
        assert r.byte() == ROOM
        boards = []
        while r.rest() > 0:
            bid = r.byte()
            packed = r.byte() & 0xFF
            flags = r.byte()
            money = r.int()
            boards.append((bid, packed % 16, packed // 16, money))
        check(len(boards) == 6 and all(b[0] in range(30, 36) for b in boards),
              f"{name}: 6 bàn TL id 30-35", str(boards[:2]))
        check(all(b[2] == 4 for b in boards), f"{name}: mọi bàn tối đa 4 người")

    print("[2] A vào bàn 30 (chủ bàn), B vào theo; B sẵn sàng; A đặt mức + bắt đầu")
    for P in (A, B):
        P.c.send(8, bytes([ROOM, BOARD]) + write_utf(""))
        body = wait_cmd(P.c, 8, label=P.user)
        r = Rd(body)
        assert r.byte() == ROOM and r.byte() == BOARD
        owner = r.int()
        r.int()  # money
        seats = 0
        while r.rest() > 0:
            iddb = r.int()
            if iddb == -1:
                seats += 1
                continue
            r.utf()
            r.int()
            nw = r.byte()
            for _ in range(nw):
                r.byte(); r.byte()  # short = 2 bytes; đọc qua int16 bên dưới nếu lệch sẽ lộ ở assert ghế
            r.int()
            r.bool()
            r.byte(); r.byte()
            seats += 1
        check(seats == 4, f"{P.user}: JOIN_BOARD có đúng 4 ghế", f"seats={seats} owner={owner}")
    B.c.send(16, bytes([ROOM, BOARD, 1]))
    wait_cmd(B.c, 16, label="B-ready")
    A.c.send(19, bytes([ROOM, BOARD]) + struct.pack(">i", MONEY))
    body = wait_cmd(A.c, 19, label="A-money")
    A.c.send(20, bytes([ROOM, BOARD]))

    print("[3] nhận START: 13 lá + người đi trước")
    starts = {}
    for P in (A, B):
        body = wait_cmd(P.c, 20, timeout=10, label=P.user)
        interval, cards, first = parse_start(body)
        starts[P.uid] = (cards, first)
        P.hand = sorted(cards)
        check(interval == 30 and len(cards) == 13 and len(set(cards)) == 13,
              f"{P.user}: 13 lá khác nhau, interval 30", f"first={first}")
    allcards = starts[1000][0] + starts[1001][0]
    check(len(set(allcards)) == 26, "hai bộ bài không trùng lá")
    first_id = starts[1000][1]
    check(first_id == starts[1001][1], "hai phía thống nhất người đi trước")
    holder = 1000 if 0 in starts[1000][0] else 1001
    check(first_id == holder, "ván đầu: người giữ 3 bích đi trước",
          f"first={first_id} holder={holder}")

    print("[4] chơi tới khi có FINISH x2 + STOP (chiến lược: lá đơn nhỏ nhất)")
    turn = first_id
    table = []       # bài trên bàn (list cardID)
    players = {1000: A, 1001: B}
    deadline = time.time() + 120
    while time.time() < deadline:
        if len(A.finished) >= 2 and A.stopped:
            break
        acted = False
        for P in (A, B):
            for cmd, body in P.pump(0.3):
                if cmd == 21:
                    r = Rd(body)
                    r.byte(); r.byte()
                    who = r.int()
                    n = r.byte()
                    cards = [r.byte() & 0xFF for _ in range(n)]
                    nxt = r.int()
                    table = cards
                    if who == P.uid:
                        for cd in cards:
                            if cd in P.hand:
                                P.hand.remove(cd)
                    if nxt != -1:
                        turn = nxt
                elif cmd == 49:
                    r = Rd(body)
                    r.byte(); r.byte()
                    r.int()
                    nxt = r.int()
                    clear = r.bool()
                    if clear:
                        table = []
                    if nxt != -1:
                        turn = nxt
                elif cmd == 51:
                    r = Rd(body)
                    r.byte(); r.byte()
                    who = r.int()
                    pos = r.byte()
                    dm = r.int()
                    de = r.int()
                    A.finished[who] = (pos, dm, de)
                    B.finished[who] = (pos, dm, de)
                elif cmd == 50:
                    A.stopped = B.stopped = True
                elif cmd == 54:
                    r = Rd(body)
                    r.byte(); r.byte()
                    print(f"    MOVE_ERROR -> {P.user}: {r.utf()}")
        if len(A.finished) >= 2 and A.stopped:
            break
        # tới lượt ai thì người đó đi
        P = players.get(turn)
        if P is None or P.uid in A.finished:
            continue
        if not P.hand:
            continue
        if not table:
            play = [P.hand[0]]  # dẫn: lá nhỏ nhất (ván đầu có thể là lá 0)
            if A.finished == {} and first_id == P.uid and 0 in P.hand and play != [0]:
                play = [0]
            P.c.send(21, bytes([ROOM, BOARD, len(play)]) + bytes(play))
        else:
            top = table[-1]
            bigger = [c for c in P.hand if c > top]
            if bigger:
                P.c.send(21, bytes([ROOM, BOARD, 1]) + bytes([bigger[0]]))
            else:
                P.c.send(49, bytes([ROOM, BOARD]))
        acted = True
        time.sleep(0.15)
        if not acted:
            time.sleep(0.1)

    check(len(A.finished) == 2, "cả hai đều có FINISH", str(A.finished))
    check(A.stopped, "nhận STOP kết thúc ván")
    if len(A.finished) == 2:
        by_pos = sorted(A.finished.items(), key=lambda kv: kv[1][0])
        (win_id, (wp, wdm, wde)), (lose_id, (lp, ldm, lde)) = by_pos
        check(wp == 0 and lp == 1, "vị trí 0/1 đủ", f"{by_pos}")
        check(wdm == MONEY and ldm == -MONEY,
              "delta zero-sum N=2: nhất +1000, chót -1000 (không chặt heo)",
              f"win +{wdm}, lose {ldm}")
        print("[5] đối chiếu DB")
        time.sleep(1.0)
        for P in (A, B):
            P.c.sock.close()
        time.sleep(3.5)
        xw = int(db(f"SELECT xu FROM players WHERE user_id={win_id}") or -1)
        xl = int(db(f"SELECT xu FROM players WHERE user_id={lose_id}") or -1)
        check(xw == 200000 + MONEY, f"DB người thắng = 201.000", str(xw))
        check(xl == 200000 - MONEY, f"DB người thua = 199.000", str(xl))

    print()
    if FAILS:
        print("KẾT QUẢ: FAIL:", FAILS)
        sys.exit(1)
    print("KẾT QUẢ: PASS TẤT CẢ")


if __name__ == "__main__":
    main()
