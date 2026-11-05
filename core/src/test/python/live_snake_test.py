# -*- coding: utf-8 -*-
"""Test sống Rắn Thang (Snake Ladder, game 5) qua hub −113:
P1 tạo bàn → P2 vào → SN_START → hai bên tung xúc xắc xen kẽ theo
lượt; test tự tính lại vị trí đích (bật lại ở 66, leo thang, tuột
rắn, đá đối thủ về đầu rắn gần nhất phía sau rồi tuột đuôi — không
có rắn phía sau thì về ô 1) và đối chiếu từng gói SN_ROLLED; chơi
tới SN_END winner nhất quán hai phía + ghi mg_stats game 5.
"""
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login, mg, wait_mg
from live_createchar_test import db

SN = 5
LADDERS = {4: 25, 9: 16, 17: 34, 28: 46, 39: 58}
SNAKES = {15: 5, 23: 7, 27: 11, 36: 18, 49: 30, 63: 42}


def expect_to(pos, dice, opp_pos):
    """(to, kind, opp_to) theo luật port — khớp server."""
    to = pos + dice
    if to > 66:
        to = 66 - (to - 66)
    kind = 0
    if to in LADDERS:
        to = LADDERS[to]
        kind = 1
    elif to in SNAKES:
        to = SNAKES[to]
        kind = 2
    opp_to = -1
    if to != 66 and opp_pos == to and to > 0:
        heads = [h for h in SNAKES if h < to]
        opp_to = SNAKES[max(heads)] if heads else 1
    return to, kind, opp_to


def main():
    p1, _ = login("betest01", "abcd1234")
    time.sleep(2)
    mg(p1, 0, 1, bytes([SN, 0]))
    r = wait_mg(p1, 0, 10, label="snake JOINED p1")
    room = r.i32()
    assert r.u8() == 0 and r.u8() == SN
    print("SNAKE room:", room)
    time.sleep(2)
    p2, _ = login("betest02", "abcd1234")
    mg(p2, 0, 2, room.to_bytes(4, "big", signed=True))
    r = wait_mg(p2, 0, 10, label="snake JOINED p2")
    assert r.i32() == room and r.u8() == 1
    conns = [p1, p2]
    for cc in conns:
        rr = wait_mg(cc, SN, 1, label="snake START")
        assert rr.u8() == 0  # ghế 0 tung trước
    print("SNAKE START ok")

    pos = [0, 0]
    turn = 0
    winner = None
    rolls = 0
    stats = {"ladder": 0, "snake": 0, "kick": 0, "bounce": 0}
    while winner is None:
        rolls += 1
        assert rolls <= 800, "ván quá dài"
        me = conns[turn]
        before = pos[turn]
        mg(me, SN, 2, b"")
        got = {}
        for i, cc in enumerate(conns):
            rr = wait_mg(cc, SN, 3, label=f"snake ROLLED máy {i}")
            raw = [rr.u8() for _ in range(6)]
            rolled = tuple(raw[:5]) + \
                (raw[5] - 256 if raw[5] > 127 else raw[5],)
            endp = None
            if rolled[3] == 66:  # tới đích: END về ngay sau ROLLED
                rr = wait_mg(cc, SN, 5, label=f"snake END máy {i}")
                endp = (rr.u8(), rr.u8())
            got[i] = (rolled, endp)
        (seat, dice, frm, to, kind, opp_to), endp1 = got[0]
        (seat2, dice2, frm2, to2, kind2, opp_to2), endp2 = got[1]
        assert (seat, dice, frm, to, kind, opp_to) == \
            (seat2, dice2, frm2, to2, kind2, opp_to2), "hai máy lệch ROLLED"
        assert seat == turn and frm == before, (seat, turn, frm, before)
        assert 1 <= dice <= 6, dice
        exp_to, exp_kind, exp_opp = expect_to(before, dice, pos[1 - turn])
        assert to == exp_to and kind == exp_kind and opp_to == exp_opp, \
            (before, dice, to, exp_to, kind, exp_kind, opp_to, exp_opp)
        if before + dice > 66:
            stats["bounce"] += 1
        if kind == 1:
            stats["ladder"] += 1
        if kind == 2:
            stats["snake"] += 1
        if opp_to >= 0:
            stats["kick"] += 1
            pos[1 - turn] = opp_to
        pos[turn] = to
        if to == 66:
            assert endp1 is not None and endp2 is not None, "thiếu END"
            assert endp1 == endp2 == (turn, 0), (endp1, endp2, turn)
            winner = turn
        turn = 1 - turn
    print(f"SNAKE GAME PASS sau {rolls} lượt tung, winner ghế {winner},",
          "sự kiện:", stats, "pos cuối:", pos)

    # rời bàn + đóng socket để server lưu; kiểm mg_stats
    mg(p1, 0, 6)
    mg(p2, 0, 6)
    p1.sock.close()
    p2.sock.close()
    time.sleep(3)
    out = db("SELECT user_id, wins, plays FROM mg_stats WHERE game_id=5 "
             "ORDER BY user_id")
    print("mg_stats game 5:", out.strip())
    rows = [ln.split("\t") for ln in out.strip().splitlines() if ln.strip()]
    by_user = {int(r[0]): (int(r[1]), int(r[2])) for r in rows}
    w_uid = 1000 if winner == 0 else 1001
    assert by_user.get(1000, (0, 0))[1] >= 1, by_user
    assert by_user.get(1001, (0, 0))[1] >= 1, by_user
    assert by_user.get(w_uid, (0, 0))[0] >= 1, by_user
    print("SNAKE STATS PASS")
    print("ALL PASS")


if __name__ == "__main__":
    main()
