#!/usr/bin/env python3
"""Test sống TAB CẶP ĐÔI (bản chính 3.4.4: CommandCouple):
A+B hẹn hò (luồng cưới sẵn có) -> marry.ngay được ghi -> gói −22
mang khối người yêu ĐÚNG: tên hiển thị đối tác, tenQuanHe theo
cấp ("Cặp đôi hạnh phúc" ở cấp 7), action (37, "Chia tay"),
dateDay ở đuôi -> điểm danh cộng +10% tiến độ cấp đôi ->
đủ 100% lên cấp. Dọn dẹp: xoá marry + daily_state của cặp test.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

FAILS = []
DL = -116


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
    return c


def pump(c, secs=0.6):
    end = time.time() + secs
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cmd, body))
        except Exception:
            break


def wait_for(c, cmd, timeout=6.0, menu_id=None, sub=None):
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == cmd:
                if menu_id is not None and not (len(bb) >= 5 and bb[4] == menu_id):
                    continue
                if sub is not None and not (len(bb) >= 1 and bb[0] == sub):
                    continue
                c.q.pop(i)
                return bb
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            break
    return None


def read_utf_at(b, off):
    ln = struct.unpack_from(">H", b, off)[0]
    return b[off + 2:off + 2 + ln].decode("utf-8"), off + 2 + ln


def parse_info22(body):
    """Parse gói −22 theo đúng thứ tự client port đọc."""
    off = 0
    uid = struct.unpack_from(">i", body, off)[0]; off += 4
    off += 7  # 7 byte chỉ số
    pid = struct.unpack_from(">i", body, off)[0]; off += 4
    out = {"uid": uid, "pid": pid}
    if pid == -1:
        return out
    out["name"], off = read_utf_at(body, off)
    n = body[off]; off += 1
    off += 2 * n  # parts của đối tác
    out["slogan"], off = read_utf_at(body, off)
    out["img"] = struct.unpack_from(">h", body, off)[0]; off += 2
    out["lv"] = body[off]; off += 1
    out["pct"] = body[off]; off += 1
    out["rel"], off = read_utf_at(body, off)
    out["act"] = struct.unpack_from(">h", body, off)[0]; off += 2
    if out["act"] != -1:
        out["actName"], off = read_utf_at(body, off)
    off += 2  # short cấp gốc
    out["join"], off = read_utf_at(body, off)
    if off + 2 <= len(body):
        out["date"], off = read_utf_at(body, off)
    return out


def main():
    db("DELETE FROM marry WHERE idNam IN (1000,1001) OR idNu IN (1000,1001)")
    db("DELETE FROM daily_state WHERE user_id = 1000")
    a = login("betest01")
    b = login("betest02")
    try:
        # --- hẹn hò qua luồng gốc ---
        a.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
        b.send(50, struct.pack(">bbhh", 10, -1, 278, 68))
        pump(a, 0.5); pump(b, 0.5)
        a.send(-83, struct.pack(">h", 36))
        check(wait_for(a, -60, 6.0, menu_id=100) is not None, "A nhận TEXT_BOX 100")
        a.send(-60, struct.pack(">i", 1000) + bytes([100]) + write_utf("betest02"))
        check(wait_for(b, -60, 6.0, menu_id=101) is not None, "B nhận TEXT_BOX 101")
        b.send(-60, struct.pack(">i", 1001) + bytes([101]) + write_utf("ok"))
        pump(a, 1.0)
        row = db("SELECT level, ngay IS NOT NULL FROM marry WHERE idNam=1000 AND idNu=1001")
        check("0" in row and "1" in row, "marry level 0 + ngay đã ghi", row.strip())

        # --- đặt cấp đôi mẫu: cấp 7, 40%, ngày 14/02/2025 ---
        db("UPDATE marry SET level=7, perLevel=40, ngay='2025-02-14 10:00:00'"
           " WHERE idNam=1000 AND idNu=1001")
    finally:
        a.sock.close(); b.sock.close()
    time.sleep(3)

    # --- đăng nhập lại để nạp marry vào RAM, xin gói −22 ---
    a = login("betest01")
    try:
        a.send(-22, struct.pack(">i", 1000))
        body = wait_for(a, -22, 6.0)
        check(body is not None, "nhận gói −22")
        if body:
            info = parse_info22(body)
            check(info["pid"] == 1001, "partnerId = 1001", str(info["pid"]))
            check(info.get("name") == "betest02", "tên đối tác (tên nhân vật của tài khoản)", info.get("name", ""))
            check(info.get("rel") == "Cặp đôi hạnh phúc", "tenQuanHe theo cấp 7", info.get("rel", ""))
            check(info.get("lv") == 7 and info.get("pct") == 40, "cấp đôi 7 + 40%",
                  f"{info.get('lv')}/{info.get('pct')}")
            check(info.get("act") == 37 and info.get("actName") == "Chia tay",
                  "action = (37, Chia tay)", f"{info.get('act')}/{info.get('actName')}")
            check(info.get("date") == "14/02/2025", "dateDay ở đuôi gói", info.get("date", ""))

        # --- điểm danh: +10% tiến độ cấp đôi ---
        a.send(DL, bytes([1]))
        res = wait_for(a, DL, 6.0, sub=4)
        check(res is not None and len(res) > 1 and res[1] == 1, "điểm danh thành công")
        pump(a, 0.6)
        row = db("SELECT level, perLevel FROM marry WHERE idNam=1000 AND idNu=1001").strip()
        check(row == "7\t50", "perLevel 40 -> 50 sau điểm danh", row)

        # --- đủ 100% thì lên cấp ---
        db("UPDATE marry SET perLevel=95 WHERE idNam=1000 AND idNu=1001")
        db("DELETE FROM daily_state WHERE user_id = 1000")
        a.send(DL, bytes([1]))
        res = wait_for(a, DL, 6.0, sub=4)
        check(res is not None and len(res) > 1 and res[1] == 1, "điểm danh lần 2 thành công")
        pump(a, 0.6)
        row = db("SELECT level, perLevel FROM marry WHERE idNam=1000 AND idNu=1001").strip()
        check(row == "8\t5", "95 + 10 -> cấp 8, dư 5%", row)
    finally:
        a.sock.close()
        time.sleep(3)
        db("DELETE FROM marry WHERE idNam IN (1000,1001) OR idNu IN (1000,1001)")
        db("DELETE FROM daily_state WHERE user_id = 1000")
    print("ALL PASS" if not FAILS else f"FAILED: {FAILS}")
    return 0 if not FAILS else 1


if __name__ == "__main__":
    sys.exit(main())
