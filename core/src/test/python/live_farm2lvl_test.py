#!/usr/bin/env python3
"""Test sống Farm CẤP 2 + NUÔI ONG (gói -115, docs/farmlvl2-spec.md).

Giai đoạn A: bơm kho nông sản qua DB -> INFO (chưa nâng gì) -> nâng
hạng Kho (trừ khế 215x10 + bắp 200x20 + 30.000 xu) -> nâng hạng Ong
(trừ SP 266/267 x10 + 40.000 xu) -> mua tổ ong (50.000 xu) -> kiểm DB.
Giai đoạn B: sửa beeLast trong DB lùi 10 giờ + RESTART server (xoá
cache RAM của Farm2Service) -> INFO thấy beeBank = 10 (cấp 1: 1 mật/giờ,
cap 10) -> THU HOẠCH -> kho có Mật Ong 288 x10 (kiểm DB).
Giai đoạn C: NÂNG CẤP ONG (30.000 xu + 5 mật trong kho) -> cấp 2,
perH = 2, cap = 20 -> kiểm DB (xu 350.000, mật còn 5).
"""
import json
import struct
import subprocess
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf
from live_createchar_test import db

FAILS = []
F2 = -115
SRV_DIR = "/home/hatch/workspace/avatardk-java/server"
JDK = "/home/hatch/workspace/tools/jdk17/bin/java"


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
    # vào handler farm để server nạp dữ liệu farm vào phiên
    c.send(-1, bytes([10]))
    time.sleep(0.6)
    try:
        while True:
            cmd, body = c.recv(timeout=0.3)
            c.q.append((cmd, body))
    except Exception:
        pass
    return c


def wait_f2(c, sub, timeout=6.0):
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == F2 and len(bb) >= 1 and bb[0] == sub:
                return c.q.pop(i)[1][1:]
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
    return None


def f2(c, sub, payload=b""):
    c.send(F2, bytes([sub]) + payload)


def parse_info(info):
    up = list(info[0:7])
    lvl = info[7]
    bank, cap, perh = struct.unpack_from(">iii", info, 8)
    return up, lvl, bank, cap, perh


def close(c):
    try:
        c.sock.close()
    except Exception:
        pass
    time.sleep(3)


def restart_server():
    out = subprocess.run(["bash", "-c", "ss -tlnp | grep 19128 | grep -o 'pid=[0-9]*' | cut -d= -f2 | head -1"],
                         capture_output=True, text=True).stdout.strip()
    if out:
        subprocess.run(["kill", out])
        for _ in range(20):
            time.sleep(0.5)
            r = subprocess.run(["bash", "-c", "ss -tln | grep -c 19128 || true"],
                               capture_output=True, text=True).stdout.strip()
            if r == "0":
                break
    # KHÔNG capture_output: cháu của bash (java qua setsid) giữ ống pipe
    # khiến run() treo mãi dù bash đã thoát (đã dính 2026-11-05).
    subprocess.run(["bash", "-c",
                    f"cd {SRV_DIR} && setsid nohup {JDK} -cp 'target/classes:lib/*' "
                    "avatar.server.Avatar > /home/hatch/workspace/logs-avatardk/gameserver_1105_farm2.log "
                    "2>&1 < /dev/null & disown"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(0.5)
        r = subprocess.run(["bash", "-c", "ss -tln | grep -c 19128 || true"],
                           capture_output=True, text=True).stdout.strip()
        if r != "0":
            return True
    return False


def nongsan_map(uid):
    raw = db(f"SELECT nongsan FROM farm_data WHERE user_id = {uid}").strip()
    if not raw:
        return {}
    return {x["id"]: x["soluong"] for x in json.loads(raw)}


def main():
    # ---------- Giai đoạn A ----------
    db("UPDATE players SET xu = 500000 WHERE user_id = 1000")
    ns0 = json.dumps([{"id": 215, "soluong": 30}, {"id": 200, "soluong": 25},
                      {"id": 266, "soluong": 12}, {"id": 267, "soluong": 12}])
    db("UPDATE farm_data SET nongsan = '%s', lvl2_data = NULL WHERE user_id = 1000" % ns0)
    c = login("betest01")
    try:
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = info is not None and len(info) >= 20
        if ok:
            up, lvl, bank, cap, perh = parse_info(info)
            ok = up == [0] * 7 and lvl == 0 and bank == 0
        check(ok, "A1 INFO ban đầu: chưa nâng hạng nào, chưa có ong")

        f2(c, 1, bytes([0]))
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "A2 Nâng cấp Kho thành công",
              res[1:].decode("utf-8", "ignore") if res else "")

        f2(c, 1, bytes([6]))
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "A3 Nâng cấp hạng Ong thành công")

        f2(c, 3)
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "A4 Mua tổ ong thành công")

        f2(c, 0)
        info = wait_f2(c, 0)
        ok = False
        if info is not None and len(info) >= 20:
            up, lvl, bank, cap, perh = parse_info(info)
            ok = up[0] == 1 and up[6] == 1 and lvl == 1 and cap == 10 and perh == 1
        check(ok, "A5 INFO: Kho + Ong đã nâng, tổ cấp 1 (cap 10, 1 mật/giờ)")
    finally:
        close(c)

    st = json.loads(db("SELECT lvl2_data FROM farm_data WHERE user_id = 1000").strip())
    check(st["up"][0] == 1 and st["up"][6] == 1 and st["beeLevel"] == 1,
          "A6 DB lvl2_data đúng", json.dumps(st)[:70])
    have = nongsan_map(1000)
    check(have.get(215) == 20 and have.get(200) == 5 and have.get(266, 0) == 2 and have.get(267, 0) == 2,
          "A7 Nguyên liệu trừ đúng (khế -10, bắp -20, 266/267 -10)", str(have))
    xu = int(db("SELECT xu FROM players WHERE user_id = 1000").strip())
    check(xu == 380000, "A8 Xu = 380.000 (500k - 30k - 40k - 50k)", f"xu={xu}")

    # ---------- Giai đoạn B: ong làm mật 10 giờ ----------
    st["beeLast"] = int(time.time() * 1000) - 10 * 3600 * 1000
    st["beeBank"] = 0
    db("UPDATE farm_data SET lvl2_data = '%s' WHERE user_id = 1000" % json.dumps(st))
    check(restart_server(), "B0 Restart server (xoá cache Farm2)")
    time.sleep(15)  # tránh ghì tốc độ đăng nhập

    c = login("betest01")
    try:
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = False
        if info is not None and len(info) >= 20:
            up, lvl, bank, cap, perh = parse_info(info)
            ok = bank == 10 and cap == 10 and perh == 1
        check(ok, "B1 Sau 10 giờ: beeBank = 10 (đầy cap)")

        f2(c, 4)
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "B2 Thu hoạch mật thành công",
              res[1:].decode("utf-8", "ignore") if res else "")
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = info is not None and len(info) >= 20 and parse_info(info)[2] == 0
        check(ok, "B3 INFO sau thu: beeBank = 0")
    finally:
        close(c)
    have = nongsan_map(1000)
    check(have.get(288) == 10, "B4 Kho có Mật Ong 288 x10", str(have.get(288)))

    # ---------- Giai đoạn C: nâng cấp ong ----------
    time.sleep(15)
    c = login("betest01")
    try:
        f2(c, 5)
        res = wait_f2(c, 2)
        check(res is not None and res[0] == 1, "C1 Nâng cấp ong lên cấp 2",
              res[1:].decode("utf-8", "ignore") if res else "")
        f2(c, 0)
        info = wait_f2(c, 0)
        ok = False
        if info is not None and len(info) >= 20:
            up, lvl, bank, cap, perh = parse_info(info)
            ok = lvl == 2 and cap == 20 and perh == 2
        check(ok, "C2 INFO: ong cấp 2 (cap 20, 2 mật/giờ)")
    finally:
        close(c)
    xu = int(db("SELECT xu FROM players WHERE user_id = 1000").strip())
    check(xu == 350000, "C3 Xu = 350.000 (380k - 30k nâng ong)", f"xu={xu}")
    have = nongsan_map(1000)
    check(have.get(288) == 5, "C4 Mật ong còn 5 (10 - 5 nâng cấp)", str(have.get(288)))
    st = json.loads(db("SELECT lvl2_data FROM farm_data WHERE user_id = 1000").strip())
    check(st["beeLevel"] == 2, "C5 DB beeLevel = 2")

    print()
    if FAILS:
        print("FARM2LVL TEST FAIL:", FAILS)
        sys.exit(1)
    print("FARM2LVL TEST ALL PASS")


if __name__ == "__main__":
    main()
