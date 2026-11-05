#!/usr/bin/env python3
"""Test sống Đua thú (handler 12) với server Java thật (19128).

Luồng: -1 {12} -> gói 1 xin trạng thái -> lineup mở cược (byte 0,
6 thú + timeRemain) -> gói 2 thông tin thú -> gói 5 đặt cược 100 xu
vào CẢ 6 thú (chắc chắn trúng thú về nhất) -> chờ gói 1 bắt đầu
chặng (byte 1 + bool true + đoạn tốc độ) -> chờ phiếu thắng gói 10
-> đối chiếu công thức [PORT]: ăn = cược x rate, thuế 10%, nhận =
ăn - thuế -> gói 8 lịch sử -> đóng phiên, kiểm xu trong DB.

Chặng cược 60s + chạy ~40s nên test mất khoảng 2 phút.
Chạy: python3 live_race_test.py   (cwd = thư mục này)
"""
import json
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402
from live_createchar_test import db  # noqa: E402

FAIL = []


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name
          + (f"  [{extra}]" if extra else ""), flush=True)
    if not cond:
        FAIL.append(name)


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

    def bool(self):
        return self.byte() != 0

    def int(self):
        v = struct.unpack(">i", self.b[self.o:self.o + 4])[0]
        self.o += 4
        return v

    def short(self):
        v = struct.unpack(">h", self.b[self.o:self.o + 2])[0]
        self.o += 2
        return v

    def utf(self):
        ln = struct.unpack(">H", self.b[self.o:self.o + 2])[0]
        self.o += 2
        s = self.b[self.o:self.o + ln].decode("utf-8", "replace")
        self.o += ln
        return s


def wait_for(c, pred, timeout, label):
    """Đọc gói tới khi pred(cmd, body) đúng; trả (cmd, body)."""
    end = time.time() + timeout
    seen = []
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.5, end - time.time()))
        except Exception:
            break
        seen.append(cmd)
        if cmd == -10:
            print(f"    [{label}] dialog: {read_utf(body)!r}", flush=True)
            continue
        if pred(cmd, body):
            return cmd, body
    raise AssertionError(f"[{label}] hết giờ; đã thấy {seen}")


def login(user="betest01", pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    _, body = wait_for(c, lambda cmd, b: cmd == -4, 9, "login")
    uid = struct.unpack(">i", body[:4])[0] if len(body) >= 4 else -1
    return c, uid


def main():
    db("UPDATE players SET xu=200000 WHERE user_id=1000")
    xu0 = int(db("SELECT xu FROM players WHERE user_id=1000").split("\t")[0])

    c, uid = login()
    check("login betest01", uid == 1000, str(uid))

    print("[1] vào handler đua 12 + xin trạng thái -> lineup mở cược")
    c.send(-1, bytes([12]))
    c.send(1)
    _, body = wait_for(c, lambda cmd, b: cmd == 1 and len(b) > 0
                       and (b[0] - 256 if b[0] > 127 else b[0]) == 0,
                       9, "lineup")
    r = Rd(body)
    assert r.byte() == 0
    pets = []
    for _ in range(6):
        pets.append((r.byte(), r.byte(), r.short(), r.short()))
    remain = r.short()
    check("6 thú, id 0..5", [p[0] for p in pets] == [0, 1, 2, 3, 4, 5],
          str(pets))
    check("rate trong 2..6", all(2 <= p[1] <= 6 for p in pets),
          str([p[1] for p in pets]))
    check("timeRemain 1..60", 1 <= remain <= 60, str(remain))
    rates = {p[0]: p[1] for p in pets}
    imgs = {p[0]: p[2] for p in pets}

    print("[2] thông tin thú 0")
    c.send(2, bytes([0]))
    _, body = wait_for(c, lambda cmd, b: cmd == 2, 9, "info")
    r = Rd(body)
    img, name, win, tile, form, health = (r.short(), r.utf(), r.short(),
                                          r.byte(), r.byte(), r.byte())
    check("info khớp lineup thú 0",
          img == imgs[0] and tile == rates[0] and name != "",
          f"{name} img={img} win={win}% tile={tile} form={form} health={health}")

    print("[3] đặt 100 xu vào cả 6 thú -> pool mỗi thú tăng đúng 100")
    pools = {}
    raw5 = []
    for p in range(6):
        c.send(5, bytes([p]) + struct.pack(">i", 100))
        _, body = wait_for(c, lambda cmd, b: cmd == 5, 9, f"bet{p}")
        raw5.append(body.hex())
        r = Rd(body)
        pet_id = r.byte()   # tách 2 câu lệnh: dict[f()] = g() chạy g() trước!
        pools[pet_id] = r.int()
    # pool có thể đã có sẵn tiền từ phiên test trước còn dở trong cùng
    # chặng — chỉ chắc chắn: đủ 6 thú và mỗi thú >= 100, tăng đều.
    check("pool cả 6 thú >= 100", set(pools) == set(range(6))
          and all(v >= 100 for v in pools.values()),
          f"{pools} raw={raw5}")

    print(f"[4] chờ chặng bắt đầu (còn ~{remain}s cược) -> gói 1 dạng chạy")
    _, body = wait_for(c,
                       lambda cmd, b: cmd == 1 and len(b) > 0
                       and (b[0] - 256 if b[0] > 127 else b[0]) == 1,
                       remain + 45, "start")
    r = Rd(body)
    assert r.byte() == 1
    is_present = r.bool()
    segs = []
    if is_present:
        for _ in range(6):
            n = r.ubyte()
            segs.append([(r.short(), r.short()) for _ in range(n)])
    else:
        for _ in range(6):
            r.byte(); r.short()
            n = r.ubyte()
            segs.append([(r.short(), r.short()) for _ in range(n)])
    t_remain = r.short()
    t_start = r.short()
    check("gói bắt đầu đúng dạng (có mặt từ đầu)", is_present)
    check("6 thú đều có đoạn tốc độ", all(len(s) > 0 for s in segs),
          str([len(s) for s in segs]))
    check("timeStart > timeRemain > 0", t_start > t_remain > 0,
          f"{t_start}/{t_remain}")

    # Thú thắng theo luật client gốc: hết đoạn trước là về trước
    # (tổng numTick nhỏ nhất; hoà thì con số nhỏ hơn thắng).
    def finish_tick(seg):
        return sum(num for num, _ in seg)

    winner = min(range(6), key=lambda p: finish_tick(segs[p]))
    print(f"    mô phỏng: thú thắng = {winner} "
          f"(ticks: {[finish_tick(s) for s in segs]})")

    print("[5] chờ phiếu thắng (gói 10) sau khi chặng kết thúc")
    _, body = wait_for(c, lambda cmd, b: cmd == 10, t_remain + 60, "win")
    r = Rd(body)
    w_pet, w_name = r.byte(), r.utf()
    w_rate, w_stake, w_an, w_thue, w_nhan = (r.byte(), r.int(), r.int(),
                                             r.int(), r.int())
    check("thú thắng khớp mô phỏng", w_pet == winner,
          f"server={w_pet} sim={winner} ({w_name})")
    check("rate khớp lineup", w_rate == rates[winner], str(w_rate))
    check("tiền cược = 100", w_stake == 100, str(w_stake))
    check("tiền ăn = cược x rate", w_an == 100 * w_rate, str(w_an))
    check("thuế = 10% tiền ăn", w_thue == w_an // 10, str(w_thue))
    check("tiền nhận = ăn - thuế", w_nhan == w_an - w_thue, str(w_nhan))

    print("[6] lịch sử: chặng vừa xong đứng đầu, icon = thú thắng")
    time.sleep(1)
    c.send(8)
    _, body = wait_for(c, lambda cmd, b: cmd == 8, 9, "history")
    r = Rd(body)
    n = r.byte()
    first = (r.short(), r.utf()) if n > 0 else None
    check("lịch sử có ít nhất 1 chặng", n >= 1, f"n={n}")
    check("mục đầu là thú vừa thắng",
          first is not None and first[0] == imgs[winner], str(first))

    c.sock.close()
    time.sleep(3)
    print("[7] đối chiếu xu trong DB")
    xu1 = int(db("SELECT xu FROM players WHERE user_id=1000").split("\t")[0])
    check("xu = đầu - 600 + tiền nhận", xu1 == xu0 - 600 + w_nhan,
          f"{xu0} -> {xu1}, nhận {w_nhan}")

    print("\nKẾT QUẢ:", "PASS TẤT CẢ" if not FAIL else f"FAIL: {FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
