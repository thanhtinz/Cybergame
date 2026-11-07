# Test song: Don hang uy thac Lai Buon (goi -125) + chan ban thang.
# - STATE co 6 o, o 0 duoc seed don biet truoc qua DB
# - DELIVER: tru nong san, cong xu/luong (kiem DB sau khi ngat ket noi),
#   +5 diem nhiem vu ngay, o chuyen sang cho ~600s
# - TRASH: o chuyen sang cho ~300s
# - Ban thang trong Kho (farm 63) bi chan: khong cong xu, co dialog
import struct
import sys
import time

sys.path.insert(0, ".")
from live_couple_test import login, pump  # noqa: E402
from live_createchar_test import db  # noqa: E402

ORDER = -125
UID = 1000


def order_packets(c):
    return [(cmd, body) for (cmd, body) in c.q if cmd == ORDER]


def parse_state(body):
    off = 0
    sub = body[off]; off += 1
    assert sub == 10, f"sub STATE phai la 10, nhan {sub}"
    n = body[off]; off += 1
    slots = []
    for _ in range(n):
        kind = body[off]; off += 1
        if kind == 1:
            secs = struct.unpack_from(">i", body, off)[0]; off += 4
            slots.append({"kind": 1, "secs": secs})
        else:
            ln = struct.unpack_from(">H", body, off)[0]; off += 2
            cust = body[off:off + ln].decode("utf-8"); off += ln
            avatar = body[off]; off += 1
            ni = body[off]; off += 1
            items = []
            for _ in range(ni):
                pid, need, have = struct.unpack_from(">hhh", body, off); off += 6
                items.append((pid, need, have))
            rx = struct.unpack_from(">i", body, off)[0]; off += 4
            rl = body[off]; off += 1
            slots.append({"kind": 0, "cust": cust, "avatar": avatar,
                          "items": items, "rx": rx, "rl": rl})
    return slots


def main():
    # don sach trang thai don + diem nhiem vu cua betest01
    db("DELETE FROM player_orders WHERE user_id = %d" % UID)
    db("DELETE FROM daily_state WHERE user_id = %d" % UID)
    # fixture: nap san 215 (khe) = 40 trong kho de co hang giao don
    import json as _json
    ns = _json.loads(db("SELECT nongsan FROM farm_data WHERE user_id = %d" % UID))
    found = False
    for e in ns:
        if e.get("id") == 215:
            e["soluong"] = 40
            found = True
    if not found:
        ns.insert(0, {"id": 215, "soluong": 40})
    db("UPDATE farm_data SET nongsan = '%s' WHERE user_id = %d"
       % (_json.dumps(ns, separators=(",", ":")), UID))
    row = db("SELECT xu, luong FROM players WHERE user_id = %d" % UID).strip().split("\t")
    xu0, luong0 = int(row[0]), int(row[1])
    print("truoc: xu", xu0, "luong", luong0)

    c = login("betest01")
    c.q = []
    # 1) OPEN -> STATE 6 o
    c.send(ORDER, struct.pack(">b", 0))
    pump(c, 2.0)
    sts = [b for (cmd, b) in order_packets(c) if b[0] == 10]
    assert sts, "khong nhan duoc STATE"
    slots = parse_state(sts[-1])
    assert len(slots) == 6, f"STATE phai co 6 o, nhan {len(slots)}"
    print("STATE ok: 6 o,", sum(1 for s in slots if s["kind"] == 0), "don dang mo")

    # 2) seed o 0 thanh don biet truoc: can 10 khế (215), thuong 9999 xu + 2 luong
    db("INSERT INTO player_orders (user_id, slot, customer, avatar, items,"
       " reward_xu, reward_luong, regen_at) VALUES (%d, 0, 'Khach Test', 0,"
       " '215:10', 9999, 2, 0) ON DUPLICATE KEY UPDATE customer = VALUES(customer),"
       " items = VALUES(items), reward_xu = VALUES(reward_xu),"
       " reward_luong = VALUES(reward_luong), regen_at = VALUES(regen_at)" % UID)
    c.q = []
    c.send(ORDER, struct.pack(">b", 0))
    pump(c, 2.0)
    sts = [b for (cmd, b) in order_packets(c) if b[0] == 10]
    slots = parse_state(sts[-1])
    s0 = slots[0]
    assert s0["kind"] == 0 and s0["items"] and s0["items"][0][0] == 215 \
        and s0["items"][0][1] == 10, f"o 0 khong dung don seed: {s0}"
    have215 = s0["items"][0][2]
    print("o 0:", s0["cust"], s0["items"], "thuong", s0["rx"], "+", s0["rl"], "luong")
    assert have215 >= 10, f"khong du khe de giao: co {have215}"

    # 3) DELIVER o 0
    c.q = []
    c.send(ORDER, struct.pack(">bb", 1, 0))
    pump(c, 2.0)
    res = [b for (cmd, b) in order_packets(c) if b[0] == 11]
    assert res, "khong nhan RESULT sau DELIVER"
    ok = res[-1][1] == 1
    assert ok, "DELIVER phai ok"
    sts = [b for (cmd, b) in order_packets(c) if b[0] == 10]
    slots = parse_state(sts[-1])
    assert slots[0]["kind"] == 1 and 500 <= slots[0]["secs"] <= 600, \
        f"o 0 phai cho ~600s sau giao: {slots[0]}"
    print("DELIVER ok, o 0 cho", slots[0]["secs"], "s")

    # 4) TRASH o 1 (don dang mo bat ky)
    assert slots[1]["kind"] == 0, "o 1 phai dang mo de test TRASH"
    c.q = []
    c.send(ORDER, struct.pack(">bb", 2, 1))
    pump(c, 2.0)
    res = [b for (cmd, b) in order_packets(c) if b[0] == 11]
    assert res and res[-1][1] == 1, "TRASH phai ok"
    sts = [b for (cmd, b) in order_packets(c) if b[0] == 10]
    slots = parse_state(sts[-1])
    assert slots[1]["kind"] == 1 and 200 <= slots[1]["secs"] <= 300, \
        f"o 1 phai cho ~300s sau bo: {slots[1]}"
    print("TRASH ok, o 1 cho", slots[1]["secs"], "s")

    # 5) Ban thang trong Kho bi chan: vao handler farm, gui 63 {215}
    c.send(-1, struct.pack(">b", 10))
    pump(c, 1.0)
    c.q = []
    c.send(63, struct.pack(">h", 215))
    pump(c, 2.0)
    dialog = False
    for (cmd, body) in c.q:
        if "Lái Buôn".encode("utf-8") in body:
            dialog = True
    assert dialog, "khong thay dialog huong sang Lai Buon sau khi gui 63"
    print("ban thang bi chan, co dialog huong dan")

    # 6) Kiem tra ngay trong phien (deterministic): goi -33 UPDATE_MONEY
    # sau DELIVER phai bao xu/luong moi; STATE cua don khac thay 215 da tru.
    sc = db("SELECT score FROM daily_state WHERE user_id = %d" % UID).strip()
    assert sc == "5", f"diem nhiem vu phai la 5 sau 1 don, nhan '{sc}'"
    db("INSERT INTO player_orders (user_id, slot, customer, avatar, items,"
       " reward_xu, reward_luong, regen_at) VALUES (%d, 2, 'Khach Kiem', 1,"
       " '215:5', 100, 0, 0) ON DUPLICATE KEY UPDATE customer = VALUES(customer),"
       " items = VALUES(items), reward_xu = VALUES(reward_xu),"
       " reward_luong = VALUES(reward_luong), regen_at = VALUES(regen_at)" % UID)
    c.q = []
    c.send(ORDER, struct.pack(">b", 0))
    pump(c, 2.0)
    sts = [b for (cmd, b) in order_packets(c) if b[0] == 10]
    slots = parse_state(sts[-1])
    have2 = slots[2]["items"][0][2]
    assert have2 == have215 - 10, f"215 trong kho phai con {have215 - 10}, STATE bao {have2}"
    print(f"trong phien ok: diem NV = 5, khe 215 con {have2} (da tru 10)")

    # 7) Xu/luong: server luu DB khi phien dong that su (socket dong +
    # server tu phat hien, co the cham) — poll dai toi 240s.
    try:
        c.s.close()
    except Exception:
        pass
    xu1 = luong1 = -1
    for _ in range(48):
        time.sleep(5.0)
        row = db("SELECT xu, luong FROM players WHERE user_id = %d" % UID).strip().split("\t")
        xu1, luong1 = int(row[0]), int(row[1])
        if xu1 == xu0 + 9999 and luong1 == luong0 + 2:
            break
    assert xu1 == xu0 + 9999, f"xu phai +9999 (chi tu giao don): {xu0} -> {xu1}"
    assert luong1 == luong0 + 2, f"luong phai +2: {luong0} -> {luong1}"
    ns = db("SELECT nongsan FROM farm_data WHERE user_id = %d" % UID)
    assert ('"id":215,"soluong":%d' % (have215 - 10)) in ns.replace(" ", ""), \
        f"nongsan 215 phai giam 10: {ns[:200]}"
    print(f"DB ok: xu {xu0}->{xu1}, luong {luong0}->{luong1}, khe con {have215 - 10}")
    print("LIVE ORDER TEST: ALL PASS")


if __name__ == "__main__":
    main()
