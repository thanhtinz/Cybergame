#!/usr/bin/env python3
"""Test sống Điểm danh + Nhiệm vụ ngày (gói -116, docs/daily-auction-spec.md):
DASHBOARD ban đầu -> BAODANH (+10 điểm, quà ngày) -> BAODANH trùng bị
chặn -> MILESTONE chưa đủ điểm bị chặn -> Q_SHOW -> Q_DONE (trừ đúng
nguyên liệu của quest hiện tại, score tăng đúng công thức) -> Q_DONE
lần 2 -> Q_BOX mốc 40 mở được -> kiểm DB daily_state.
"""
import json
import struct
import sys
import time
from datetime import datetime, timedelta, timezone

sys.path.insert(0, ".")
from live_test import Conn, write_utf
from live_createchar_test import db

FAILS = []
DL = -116
VN = timezone(timedelta(hours=7))

# bản sao bảng quest phía server (theo tên) để đối chiếu nguyên liệu
QUESTS = {
    "Thu hoạch đồng quê": [(215, 5), (200, 5)],
    "Tiệc rau củ": [(203, 8), (204, 8)],
    "Giỏ trái cây": [(249, 6), (253, 6)],
    "Mật ngọt đầu ngày": [(288, 3)],
    "Hoa thơm tặng nàng": [(266, 6), (267, 6)],
    "Bữa sáng nông trại": [(50, 5)],
    "Đặc sản miền quê": [(248, 6), (252, 6)],
    "Kho đầy ắp": [(215, 10)],
}


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + (f"  [{extra}]" if extra else ""), flush=True)
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
    c.send(-1, bytes([10]))
    time.sleep(0.6)
    try:
        while True:
            cmd, body = c.recv(timeout=0.3)
            c.q.append((cmd, body))
    except Exception:
        pass
    return c


def wait_dl(c, sub, timeout=6.0):
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == DL and len(bb) >= 1 and bb[0] == sub:
                return c.q.pop(i)[1][1:]
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
    return None


def dl(c, sub, payload=b""):
    c.send(DL, bytes([sub]) + payload)


def read_utf_at(b, off):
    ln = struct.unpack_from(">H", b, off)[0]
    return b[off + 2:off + 2 + ln].decode("utf-8"), off + 2 + ln


def parse_result(b):
    ok = b[0] == 1
    txt, _ = read_utf_at(b, 1)
    return ok, txt


def parse_qshow(b):
    name, off = read_utf_at(b, 0)
    n = b[off]; off += 1
    reqs = []
    for _ in range(n):
        pid, qty, have = struct.unpack_from(">hii", b, off)
        off += 10
        reqs.append((pid, qty, have))
    n2 = b[off]; off += 1
    rews = []
    for _ in range(n2):
        t = b[off]
        a, bb2 = struct.unpack_from(">ii", b, off + 1)
        off += 9
        rews.append((t, a, bb2))
    score = struct.unpack_from(">i", b, off)[0]; off += 4
    n3 = b[off]; off += 1
    boxes = []
    for _ in range(n3):
        pt = struct.unpack_from(">i", b, off)[0]
        claimed = b[off + 4] == 1
        off += 5
        boxes.append((pt, claimed))
    return name, reqs, rews, score, boxes


def nongsan_map(uid):
    raw = db(f"SELECT nongsan FROM farm_data WHERE user_id = {uid}").strip()
    return {x["id"]: x["soluong"] for x in json.loads(raw)} if raw else {}


def main():
    today_vn = datetime.now(VN)
    dom = today_vn.day
    db("DELETE FROM daily_state WHERE user_id = 1000")
    db("UPDATE players SET xu = 100000 WHERE user_id = 1000")
    full = [{"id": 215, "soluong": 40}, {"id": 200, "soluong": 10},
            {"id": 203, "soluong": 16}, {"id": 204, "soluong": 16},
            {"id": 249, "soluong": 12}, {"id": 253, "soluong": 12},
            {"id": 288, "soluong": 6}, {"id": 266, "soluong": 12},
            {"id": 267, "soluong": 12}, {"id": 50, "soluong": 10},
            {"id": 248, "soluong": 12}, {"id": 252, "soluong": 12}]
    db("UPDATE farm_data SET nongsan = '%s' WHERE user_id = 1000" % json.dumps(full))

    c = login("betest01")
    try:
        dl(c, 0)
        dash = wait_dl(c, 0)
        ok = dash is not None and len(dash) >= 15
        if ok:
            d0 = dash[0]
            total = struct.unpack_from(">i", dash, 6)[0]
            cnt_today = dash[10]
            ok = d0 == dom and total == 0 and cnt_today == 0
        check(ok, "D1 DASHBOARD ban đầu: đúng ngày VN, 0 điểm, chưa báo danh")

        dl(c, 1)
        res = wait_dl(c, 4)
        ok1, txt1 = parse_result(res) if res else (False, "")
        check(ok1, "D2 BAODANH lần đầu thành công", txt1)
        dash = wait_dl(c, 0)
        ok = False
        if dash is not None:
            total = struct.unpack_from(">i", dash, 6)[0]
            cnt_today = dash[10]
            ncl = dash[19] if len(dash) > 19 else 0
            claimed = list(dash[20:20 + ncl]) if len(dash) >= 20 + ncl else []
            ok = total == 10 and cnt_today == 1 and dom in claimed
        check(ok, "D3 Sau báo danh: +10 điểm, hôm nay đã báo danh, ô ngày sáng")

        dl(c, 1)
        res = wait_dl(c, 4)
        ok2, _ = parse_result(res) if res else (True, "")
        check(not ok2, "D4 BAODANH trùng trong ngày bị chặn")

        dl(c, 2, bytes([0]))
        res = wait_dl(c, 4)
        ok3, _ = parse_result(res) if res else (True, "")
        check(not ok3, "D5 MILESTONE mốc 50 bị chặn khi mới 10 điểm")

        # --- nhiệm vụ ngày ---
        dl(c, 10)
        qs = wait_dl(c, 10)
        name, reqs, rews, score, boxes = parse_qshow(qs)
        check(name in QUESTS, "Q1 Q_SHOW nhận nhiệm vụ", name)
        expect_reqs = QUESTS.get(name, [])
        got_reqs = [(p, q) for (p, q, h) in reqs]
        check(got_reqs == expect_reqs, "Q2 Yêu cầu khớp bảng quest", str(got_reqs))
        check(all(h >= q for (p, q, h) in reqs), "Q3 Kho đủ mọi nguyên liệu (have >= qty)")

        score0 = score
        dl(c, 12)
        res = wait_dl(c, 4)
        ok4, txt4 = parse_result(res) if res else (False, "")
        check(ok4, "Q4 Q_DONE lần 1 thành công", txt4)
        qs2 = wait_dl(c, 10)
        name2, reqs2, rews2, score2, boxes2 = parse_qshow(qs2)
        expect_score = score0 + 20 + 5 * len(expect_reqs)
        check(score2 == expect_score, "Q5 Score tăng đúng công thức",
              f"{score0} -> {score2} (kỳ vọng {expect_score})")
        check(name2 != name, "Q6 Nhiệm vụ mới khác nhiệm vụ vừa xong", name2)

        dl(c, 12)
        res = wait_dl(c, 4)
        ok5, txt5 = parse_result(res) if res else (False, "")
        check(ok5, "Q7 Q_DONE lần 2 thành công", txt5)
        qs3 = wait_dl(c, 10)
        _, _, _, score3, _ = parse_qshow(qs3)
        check(score3 >= 50, "Q8 Score sau 2 nhiệm vụ >= 50", f"score={score3}")

        dl(c, 13, bytes([0]))
        res = wait_dl(c, 4)
        ok6, txt6 = parse_result(res) if res else (False, "")
        check(ok6, "Q9 Q_BOX mốc 40 mở thành công", txt6)
        dl(c, 13, bytes([0]))
        res = wait_dl(c, 4)
        ok7, _ = parse_result(res) if res else (True, "")
        check(not ok7, "Q10 Q_BOX mở trùng bị chặn")

        first_quest_reqs = expect_reqs
        second_quest_reqs = QUESTS.get(name2, [])
        globals()["_consumed"] = first_quest_reqs + second_quest_reqs
    finally:
        try: c.sock.close()
        except Exception: pass
        time.sleep(3)

    row = db("SELECT total_point, score, box_mask FROM daily_state WHERE user_id = 1000").strip()
    parts = row.split("\t")
    check(len(parts) == 3 and parts[0] == "10" and int(parts[1]) >= 50 and (int(parts[2]) & 1) == 1,
          "DB1 daily_state: point=10, score>=50, box_mask bit0=1", row)
    have = nongsan_map(1000)
    consumed = globals().get("_consumed", [])
    exp_have = {x["id"]: x["soluong"] for x in full}
    for pid, q in consumed:
        exp_have[pid] = exp_have.get(pid, 0) - q
    bad = [pid for pid, q in exp_have.items() if have.get(pid, 0) != q]
    check(not bad, "DB2 Nguyên liệu kho trừ đúng cho cả 2 nhiệm vụ", f"sai: {bad}")

    print()
    if FAILS:
        print("DAILY TEST FAIL:", FAILS)
        sys.exit(1)
    print("DAILY TEST ALL PASS")


if __name__ == "__main__":
    main()
