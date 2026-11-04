# -*- coding: utf-8 -*-
"""Test sống Cờ Caro (game 3) + Trúc Xanh (game 1) qua hub −113:
CARO: P1 tạo bàn → P2 vào → CR_START → hai bên đánh xen kẽ, P1 đủ 5 ô
hàng ngang → CR_END winner 0 + hàng thắng 5 ô.
TRÚC XANH: tạo bàn mới → TX_DEAL → lật thẻ theo trí nhớ từ
TX_FLIP_RESULT (biết cặp nào thì ăn cặp đó) → TX_MATCH cộng điểm →
hết 15 cặp → TX_END, tổng điểm hai bên = 15.
"""
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login, mg, wait_mg, Rd

CARO = 3
TX = 1


def pump_until(c, game, sub, timeout=10.0, label=""):
    return wait_mg(c, game, sub, timeout, label)


def test_caro():
    p1, _ = login("betest01", "abcd1234")
    r = None
    time.sleep(2)
    # tạo bàn caro
    mg(p1, 0, 1, bytes([CARO, 0]))
    r = pump_until(p1, 0, 10, label="caro JOINED p1")
    room = r.i32()
    seat = r.u8()
    game = r.u8()
    assert seat == 0 and game == CARO, (seat, game)
    print("CARO room:", room)
    time.sleep(2)
    p2, _ = login("betest02", "abcd1234")
    mg(p2, 0, 2, room.to_bytes(4, "big", signed=True))
    r = pump_until(p2, 0, 10, label="caro JOINED p2")
    assert r.i32() == room
    # cả hai nhận CR_START
    pump_until(p1, CARO, 1, label="caro START p1")
    pump_until(p2, CARO, 1, label="caro START p2")
    # P1 đánh hàng 7, cột 3..7; P2 đánh hàng 9, cột 3..6 xen kẽ
    def move(c, row, col):
        mg(c, CARO, 2, bytes([row, col]))
    seq = []
    for i in range(4):
        seq.append((p1, 7, 3 + i))
        seq.append((p2, 9, 3 + i))
    seq.append((p1, 7, 7))  # ô thắng
    ends = {}
    for (c, row, col) in seq:
        move(c, row, col)
        # cả hai đều nhận CR_MOVED
        for cc, nm in ((p1, "p1"), (p2, "p2")):
            rr = pump_until(cc, CARO, 3, label=f"caro MOVED {nm}")
            st, rw, cl = rr.u8(), rr.u8(), rr.u8()
            assert (rw, cl) == (row, col), (rw, cl, row, col)
    # CR_END: winner 0, reason 0, 5 ô thắng
    for cc, nm in ((p1, "p1"), (p2, "p2")):
        rr = pump_until(cc, CARO, 5, label=f"caro END {nm}")
        winner, reason, n = rr.u8(), rr.u8(), rr.u8()
        ends[nm] = (winner, reason, n)
        assert winner == 0 and reason == 0 and n == 5, (winner, reason, n)
    print("CARO PASS", ends)
    # rời bàn dọn phòng
    mg(p1, 0, 6)
    mg(p2, 0, 6)
    p1.sock.close()
    p2.sock.close()
    time.sleep(2)


def test_trucxanh():
    p1, _ = login("betest01", "abcd1234")
    time.sleep(2)
    mg(p1, 0, 1, bytes([TX, 0]))
    r = pump_until(p1, 0, 10, label="tx JOINED p1")
    room = r.i32()
    assert r.u8() == 0 and r.u8() == TX
    print("TX room:", room)
    time.sleep(2)
    p2, _ = login("betest02", "abcd1234")
    mg(p2, 0, 2, room.to_bytes(4, "big", signed=True))
    pump_until(p2, 0, 10, label="tx JOINED p2")
    for cc in (p1, p2):
        rr = pump_until(cc, TX, 1, label="tx DEAL")
        assert rr.u8() == 6 and rr.u8() == 5
    print("TX dealt 6x5")

    known = {}       # idx -> value (đã từng lật)
    matched = set()  # idx đã ăn
    scores = [0, 0]
    turn = [0]       # ghế đang lật (0 = p1)
    conns = [p1, p2]
    end_info = [None]

    def recv_mg(c, timeout=10.0):
        end = time.time() + timeout
        while time.time() < end:
            try:
                cmd, body = c.recv(timeout=max(0.5, end - time.time()))
            except (TimeoutError, OSError):
                break
            if cmd != -113:
                continue
            rr = Rd(body)
            return rr.u8(), rr.u8(), rr
        raise AssertionError("timeout gói mg")

    def broadcast_expect(game, subs, label=""):
        """Đọc từ cả hai máy cho tới khi mỗi máy nhận đủ các sub yêu cầu
        (ít nhất một gói thuộc subs). Trả dict sub -> Rd (của p1)."""
        got = {0: {}, 1: {}}
        end = time.time() + 12
        while time.time() < end:
            done = True
            for i, c in enumerate(conns):
                for s in subs:
                    if s not in got[i]:
                        done = False
            if done:
                return got[0]
            for i, c in enumerate(conns):
                try:
                    g, s, rr = recv_mg(c, timeout=0.3)
                except AssertionError:
                    continue
                if g != game:
                    continue
                handle(s, rr)
                got[i][s] = rr
        raise AssertionError(f"broadcast thiếu {subs} ({label})")

    def handle(s, rr):
        if s == 3:  # FLIP_RESULT
            seat, idx, val = rr.u8(), rr.u8(), rr.u8()
            known[idx] = val
        elif s == 4:  # MATCH
            seat, i1, i2, s0, s1 = rr.u8(), rr.u8(), rr.u8(), rr.u8(), rr.u8()
            matched.add(i1)
            matched.add(i2)
            scores[0], scores[1] = s0, s1
        elif s == 5:  # MISS
            rr.u8(); rr.u8()
        elif s == 6:  # CHANGE_TURN
            turn[0] = rr.u8()
        elif s == 7:  # END
            end_info[0] = (rr.u8(), rr.u8(), rr.u8())

    def broadcast_resolve(label=""):
        """Sau lật thẻ thứ hai: chờ cả hai máy nhận MATCH (4) hoặc
        MISS (5) + CHANGE_TURN (6); END (7) cũng có thể về."""
        got = {0: set(), 1: set()}
        end = time.time() + 12
        while time.time() < end:
            ok = True
            for i in (0, 1):
                if 4 not in got[i] and not ({5, 6} <= got[i]) \
                        and 7 not in got[i]:
                    ok = False
            if ok:
                return
            for i, c in enumerate(conns):
                try:
                    g, s, rr = recv_mg(c, timeout=0.3)
                except AssertionError:
                    continue
                if g != TX:
                    continue
                handle(s, rr)
                got[i].add(s)
        raise AssertionError(f"không phân giải được lượt ({label})")

    guard = 0
    while end_info[0] is None:
        guard += 1
        assert guard < 300, "vòng lật không kết thúc"
        if len(matched) == 30:
            # đã ăn hết 15 cặp: chỉ còn chờ TX_END
            broadcast_expect(TX, [7], "tx end")
            break
        me = conns[turn[0]]
        # tìm cặp đã biết
        pair = None
        by_val = {}
        for idx, val in known.items():
            if idx in matched:
                continue
            if val in by_val:
                pair = (by_val[val], idx)
                break
            by_val[val] = idx
        if pair is not None:
            mg(me, TX, 2, bytes([pair[0]]))
            broadcast_expect(TX, [3], "flip pair 1")
            mg(me, TX, 2, bytes([pair[1]]))
            broadcast_expect(TX, [3], "flip pair 2")
            broadcast_resolve("pair")
        else:
            unknown = [i for i in range(30)
                       if i not in known and i not in matched]
            assert unknown, "hết thẻ lạ mà chưa hết ván"
            mg(me, TX, 2, bytes([unknown[0]]))
            broadcast_expect(TX, [3], "flip new 1")
            # sau khi biết giá trị thẻ 1: có cặp với thẻ đã biết không?
            v1 = known.get(unknown[0])
            mate = None
            for idx, val in known.items():
                if idx != unknown[0] and idx not in matched and val == v1:
                    mate = idx
                    break
            second = mate if mate is not None else (
                unknown[1] if len(unknown) > 1 else None)
            assert second is not None
            mg(me, TX, 2, bytes([second]))
            broadcast_expect(TX, [3], "flip new 2")
            broadcast_resolve("new")
    winner, s0, s1 = end_info[0]
    assert s0 + s1 == 15, (s0, s1)
    assert len(matched) == 30, len(matched)
    print("TX PASS: winner", winner, "diem", s0, s1)
    mg(p1, 0, 6)
    mg(p2, 0, 6)
    p1.sock.close()
    p2.sock.close()
    time.sleep(2)


if __name__ == "__main__":
    test_caro()
    test_trucxanh()
    print("ALL PASS")
