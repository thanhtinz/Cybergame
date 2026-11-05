#!/usr/bin/env python3
"""Test sống Phỏm (casino gameType 7) với 2 tài khoản: chọn game 7
-> phòng 49 -> bàn 40 (4 ghế) -> sẵn sàng -> chủ bàn bắt đầu (20)
-> nhận 9 lá (20 S->C) -> chơi máy trạng thái theo gói: tới lượt
thì Ăn (nếu lá trên bàn tạo phỏm được với 2-3 lá trên tay) hoặc
Bốc (63), lượt cuối Hạ (65) theo phân hoạch tối ưu rồi Đánh (21)
-> FINISH (51) hoặc Ù (69/70+51) -> đối chiếu zero-sum + xu DB.
"""
import itertools
import socket
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

ROOM, BOARD, MONEY = 49, 40, 1000

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


# ---- luật phỏm phía client test (khớp PhomRules) ----

def is_phom(cards):
    if len(cards) < 3:
        return False
    a = sorted(cards)
    sanh = all(a[i + 1] // 4 - a[i] // 4 == 1 and a[i] % 4 == a[i + 1] % 4
               for i in range(len(a) - 1))
    bala = all(a[i] // 4 == a[i + 1] // 4 for i in range(len(a) - 1))
    return sanh or bala


def best_partition(cards):
    """Trả (melds, rac) tối ưu: nhiều lá vào phỏm nhất, hoà điểm rác nhỏ nhất."""
    cards = sorted(cards)
    best = {"melds": [], "rac": list(cards)}

    def rec(rest, melds, rac):
        if len(rac) + 0 > len(best["rac"]):
            return
        if not rest:
            if (len(rac) < len(best["rac"]) or
                    (len(rac) == len(best["rac"]) and
                     sum(c // 4 + 1 for c in rac) < sum(c // 4 + 1 for c in best["rac"]))):
                best["melds"] = [list(g) for g in melds]
                best["rac"] = list(rac)
            return
        first, others = rest[0], rest[1:]
        # first làm rác
        rec(others, melds, rac + [first])
        # mọi phỏm chứa first
        for k in range(2, min(5, len(rest))):
            for combo in itertools.combinations(others, k - 1):
                g = [first] + list(combo)
                if is_phom(g):
                    rec([c for c in others if c not in combo], melds + [g], rac)

    rec(cards, [], [])
    return best["melds"], best["rac"]


def find_eat(hand, table_card):
    """Tìm 2-3 lá trên tay tạo phỏm với lá trên bàn, hoặc None."""
    for k in (2, 3):
        for combo in itertools.combinations(sorted(set(hand)), k):
            if is_phom(list(combo) + [table_card]):
                return list(combo)
    return None


class Player:
    def __init__(self, user, uid, seat):
        self.user, self.uid, self.seat = user, uid, seat
        self.c = login(user)
        self.hand = []
        self.locked = set()
        self.turn = False
        self.phase = 0          # 0 chờ Ăn/Bốc · 1 chờ Hạ/Đánh
        self.ha_done = False
        self.my_fires = 0
        self.table_card = -1
        self.table_from = -1
        self.acted_key = None   # (lượt, phase, ha_done) đã xử lý
        self.turn_seq = 0
        self.finish = None      # (money[4], score[4], rac_bytes)
        self.den = None
        self.once_win = None
        self.stopped = False

    def pump(self, timeout=0.4):
        try:
            while True:
                cmd, body = self.c.recv(timeout=timeout)
                self.on_packet(cmd, body)
                timeout = 0.05
        except (socket.timeout, TimeoutError):
            pass

    def on_packet(self, cmd, body):
        r = Rd(body)
        if cmd in (20, 21, 49, 51, 63, 64, 65, 67, 69, 50):
            r.byte(); r.byte()  # room, board
        if cmd == 20:
            r.byte()  # interval
            self.hand = sorted(r.byte() & 0xFF for _ in range(9))
            first = r.int()
            r.int()  # firHa
            self.turn = first == self.uid
            self.phase = 0
            self.turn_seq += 1
        elif cmd == 21:
            nxt = r.int()
            firer = r.int()
            card = r.byte() & 0xFF
            r.byte()  # numberCard
            if card != 0xFF:
                self.table_card = card
                self.table_from = firer
                if firer == self.uid:
                    if card in self.hand:
                        self.hand.remove(card)
                    self.my_fires += 1
            self.turn = nxt == self.uid
            if self.turn:
                self.phase = 0
                self.turn_seq += 1
        elif cmd == 49:
            nxt = r.int()
            r.int()  # firHa
            self.turn = nxt == self.uid
            if self.turn:
                self.phase = 0
                self.turn_seq += 1
        elif cmd == 63:
            card = r.byte() & 0xFF
            self.hand = sorted(self.hand + [card])
            self.phase = 1
        elif cmd == 64:
            fir_ha = r.int()
            is_eat = r.bool()
            if is_eat:
                r.byte(); r.byte()  # numEat, nCard
                if self.pending_eat is not None:
                    used, eaten = self.pending_eat
                    for cd in used:
                        self.hand.remove(cd)
                    self.hand = sorted(self.hand + [eaten])
                    self.locked |= set(used) | {eaten}
                    self.pending_eat = None
                self.table_card = -1
                self.table_from = -1
                if self.turn:
                    self.phase = 1
            else:
                r.byte()  # nCard
                self.pending_eat = None
                self.eat_failed = True
                self.turn_seq += 1  # đổi key để act() bốc thay thế
        elif cmd == 65:
            cur = r.int()
            is_ha = r.bool()
            is_u = r.bool()
            if is_ha:
                deltas = [r.int() for _ in range(4)]
                if is_u:
                    self.u_deltas = deltas
            if cur == self.uid and is_ha:
                self.ha_done = True
            if is_u:
                self.u_end = True
        elif cmd == 69:
            win = r.int()
            deltas = [r.int() for _ in range(4)]
            self.den = (win, deltas)
        elif cmd == 70:
            self.once_win = r.int()
        elif cmd == 51:
            money = [r.int() for _ in range(4)]
            score = [r.int() for _ in range(4)]
            rac = body[r.o:]
            self.finish = (money, score, rac)
        elif cmd == 50:
            self.stopped = True

    pending_eat = None
    eat_failed = False
    u_end = False
    ha_sent = False
    u_deltas = None

    def act(self):
        """Hành động theo trạng thái hiện tại; gọi sau mỗi lần pump."""
        if self.finish or self.den or self.u_end:
            return
        key = (self.turn_seq, self.phase, self.ha_done)
        if not self.turn or key == self.acted_key:
            return
        self.acted_key = key
        if self.phase == 0:
            eat = None
            if self.eat_failed:
                self.eat_failed = False
            elif self.table_card >= 0 and self.table_from != self.uid:
                eat = find_eat([c for c in self.hand if c not in self.locked],
                               self.table_card)
            if eat is not None:
                self.pending_eat = (eat, self.table_card)
                payload = bytes([ROOM, BOARD]) + bytes(eat) + bytes([0])
                self.c.send(64, payload)
            else:
                self.c.send(63, bytes([ROOM, BOARD]))
        elif self.phase == 1:
            if self.my_fires >= 3 and not self.ha_done:
                if self.ha_sent:
                    return  # đã gửi Hạ, chờ broadcast xác nhận
                rest = [c for c in self.hand if c not in self.locked]
                melds, _rac = best_partition(rest)
                payload = bytearray([ROOM, BOARD])
                for i, g in enumerate(melds):
                    if i > 0:
                        payload.append(0xFF)
                    payload += bytes(g)
                self.c.send(65, bytes(payload))
                self.ha_sent = True
            else:
                fireable = [c for c in self.hand if c not in self.locked]
                if fireable:
                    self.c.send(21, bytes([ROOM, BOARD, min(fireable)]))


def join_board(P):
    P.c.send(8, bytes([ROOM, BOARD]) + write_utf(""))
    body = wait_cmd(P.c, 8, label=P.user)
    r = Rd(body)
    assert r.byte() == ROOM and r.byte() == BOARD
    owner = r.int()
    r.int()
    seats = 0
    while r.rest() > 0:
        iddb = r.int()
        if iddb == -1:
            seats += 1
            continue
        r.utf() if False else None
        # đọc UTF thủ công qua live_test.read_utf trên phần còn lại
        ln = struct.unpack(">H", body[r.o:r.o + 2])[0]
        r.o += 2 + ln
        r.int()
        nw = r.byte()
        r.o += nw * 2
        r.int()
        r.bool()
        r.o += 2
        seats += 1
    return owner, seats


def main():
    print("[0] dọn xu test về 200.000")
    db("UPDATE players SET xu=200000 WHERE user_id IN (1000, 1001)")

    A = Player("betest01", 1000, 0)
    B = Player("betest02", 1001, 1)
    print("[1] hai tài khoản chọn game 7, xin phòng/bàn")
    for P, name in ((A, "A"), (B, "B")):
        P.c.send(-1, bytes([3]))
        P.c.send(61, bytes([7]))
        body = wait_cmd(P.c, 61, label=name)
        check(body == bytes([7]), f"{name}: ack 61 gameType 7", body.hex())
        P.c.send(6)
        body = wait_cmd(P.c, 6, label=name)
        r = Rd(body)
        rooms = []
        for _ in range(3):
            rooms.append(r.byte())
            r.byte(); r.byte(); r.byte()
        check(rooms == [49, 50, 51], f"{name}: phòng Phỏm 49-51", str(rooms))
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
        check(len(boards) == 6 and all(b[0] in range(40, 46) for b in boards),
              f"{name}: 6 bàn Phỏm id 40-45", str(boards[:2]))
        check(all(b[2] == 4 for b in boards), f"{name}: mọi bàn tối đa 4 người")

    print("[2] A vào bàn 40 (chủ bàn), B vào theo; B sẵn sàng; A đặt mức + bắt đầu")
    owner, seats = join_board(A)
    check(owner == 1000 and seats == 4, "A: JOIN_BOARD 4 ghế, chủ bàn A",
          f"owner={owner} seats={seats}")
    owner, seats = join_board(B)
    check(seats == 4, "B: JOIN_BOARD 4 ghế", f"seats={seats}")
    B.c.send(16, bytes([ROOM, BOARD, 1]))
    wait_cmd(B.c, 16, label="B-ready")
    A.c.send(19, bytes([ROOM, BOARD]) + struct.pack(">i", MONEY))
    wait_cmd(A.c, 19, label="A-money")
    A.c.send(20, bytes([ROOM, BOARD]))

    print("[3] nhận START: 9 lá + người đi trước giữ lá 0")
    deadline = time.time() + 15
    while time.time() < deadline and (not A.hand or not B.hand):
        A.pump(0.3)
        B.pump(0.3)
    check(len(A.hand) == 9 and len(B.hand) == 9, "mỗi người 9 lá",
          f"A={len(A.hand)} B={len(B.hand)}")
    check(not set(A.hand) & set(B.hand), "hai bộ bài không trùng lá")
    holder = A if 0 in A.hand else B
    check(holder.turn, "ván đầu: người giữ A bích (lá 0) đi trước")

    print("[4] chơi tới FINISH/Ù (ăn khi được, không thì bốc; hạ ở lượt cuối)")
    deadline = time.time() + 300
    while time.time() < deadline:
        if A.finish and B.finish:
            break
        if A.den and B.den:
            break
        if A.u_deltas and B.u_deltas:
            break
        A.pump(0.25)
        B.pump(0.25)
        A.act()
        B.act()
    over = (A.finish and B.finish) or (A.den and B.den) or (A.u_deltas and B.u_deltas)
    check(bool(over), "ván kết thúc có FINISH/ĐỀN/Ù-HẠ",
          f"A.finish={bool(A.finish)} B.finish={bool(B.finish)} den={bool(A.den)}")

    print("[5] đối chiếu tiền zero-sum + DB")
    deltas = None
    if A.den:
        deltas = A.den[1]
        check(A.den[0] in (1000, 1001), "ĐỀN: winID là một người chơi",
              str(A.den[0]))
    elif A.u_deltas:
        deltas = A.u_deltas
    elif A.finish:
        deltas = A.finish[0]
        scores = A.finish[1]
        check(scores[2] == -1 and scores[3] == -1,
              "FINISH: ghế trống score −1", str(scores))
        check(sum(deltas) == 0, "FINISH: tổng delta = 0", str(deltas))
    if deltas is not None:
        check(sum(deltas) == 0, "tổng delta cả bàn = 0", str(deltas))
        check(deltas[0] != 0 or deltas[1] != 0, "có tiền di chuyển", str(deltas))
        time.sleep(0.5)
        A.c.sock.close()
        B.c.sock.close()
        time.sleep(3)
        xa = db("SELECT xu FROM players WHERE user_id=1000").strip()
        xb = db("SELECT xu FROM players WHERE user_id=1001").strip()
        check(int(xa) == 200000 + deltas[0], "DB: xu A khớp delta",
              f"{xa} vs {200000 + deltas[0]}")
        check(int(xb) == 200000 + deltas[1], "DB: xu B khớp delta",
              f"{xb} vs {200000 + deltas[1]}")

    print("LIVE PHOM TEST:", "ALL PASS" if not FAILS else f"FAIL {FAILS}")
    sys.exit(1 if FAILS else 0)


if __name__ == "__main__":
    main()
