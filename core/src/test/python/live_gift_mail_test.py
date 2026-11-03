#!/usr/bin/env python3
"""Test sống: GIFTCODE -> HÒM THƯ + ADMIN GỬI QUÀ QUA THƯ
(user chốt 2026-11-02: quà giftcode và quà admin gửi đều vào thư).

Kịch bản (betest02, id 1001):
 1) Nhập code đúng (TEXT_BOX -60 {int uid, byte 20, UTF code}) ->
    dialog -10 báo quà đã vào Hòm Thư.
 2) Danh sách thư (-108) có đúng 1 thư kind=giftcode với đính kèm
    5.000 xu + 3 lượng + item 294 x2 hạn 7 ngày.
 3) Nhận quà (-110) -> ok=1.
 4) Nhập lại cùng code -> dialog "đã dùng", không thêm thư.
 5) Nhập code sai -> dialog "không đúng".
 6) DB sau khi ngắt: xu +5.000, lượng +3, rương có part 294 hạn ~7
    ngày, đúng 1 thư giftcode.
 7) Admin API /api/mail/send -> thư kind=gift, đính kèm đúng.
Dọn dẹp: xoá code + thư test + khôi phục số dư/rương của betest02."""
import json
import os
import struct
import subprocess
import sys
import time
import urllib.request

sys.path.insert(0, ".")
from live_test import Conn, write_utf  # noqa: E402
from live_feel_test import login_capture  # noqa: E402
from live_mail_test import parse_list, read_utf  # noqa: E402

MYSQL = ["/home/hatch/workspace/tools/mariadb/bin/mysql",
         "--socket=/run/mysqld/mysqld.sock", "-u", "root",
         "-pavatardk-local", "-N", "avatar_2x"]
ENV = dict(os.environ, LD_LIBRARY_PATH="/home/hatch/workspace/tools/mariadb/lib")


def sql(q):
    r = subprocess.run(MYSQL + ["-e", q], capture_output=True, text=True, env=ENV)
    if r.returncode != 0:
        raise RuntimeError(r.stderr)
    return r.stdout.strip()


def textbox(c, uid, code):
    c.send(-60, struct.pack(">i", uid) + bytes([20]) + write_utf(code))


def wait_dialog(c, *needles, tries=24):
    """Đọc dialog -10 cho tới khi gặp một câu chứa needle (bỏ qua các
    dialog khác như câu chào lúc đăng nhập)."""
    for _ in range(tries):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -10:
            text = read_utf(body, 0)[0]
            if any(n in text for n in needles):
                return text
            print("    (bỏ qua dialog:", text[:60], ")")
    raise AssertionError(f"không thấy dialog chứa {needles}")


def main():
    base = sql("SELECT CONCAT(xu,'/',luong) FROM players WHERE user_id=1001")
    base_xu, base_luong = (int(x) for x in base.split("/"))
    base_chests = sql("SELECT chests FROM players WHERE user_id=1001")
    # dọn tàn dư của lần chạy hỏng trước (nếu có) cho test độc lập
    sql("DELETE FROM letters WHERE user_id=1001 AND kind='giftcode'")
    sql("DELETE FROM giftcode_use WHERE user=1001")
    sql("DELETE FROM giftcode WHERE code='TESTMAIL1'")
    sql("INSERT INTO giftcode (code, message, data, start_time, end_time, num, create_by)"
        " VALUES ('TESTMAIL1', 'Quà test nè',"
        " '{\"xu\":5000,\"luong\":3,\"items\":[{\"id\":294,\"day\":7,\"quantity\":2}]}',"
        " '2020-01-01', '2030-01-01', 5, 1)")

    c, uid = login_capture("betest02", "abcd1234")
    assert uid == 1001, uid
    c.sock.settimeout(1.2)
    try:
        while True:
            c.recv(timeout=1.2)
    except Exception:
        pass
    c.sock.settimeout(10)

    print("[1] nhập code đúng")
    textbox(c, uid, "TESTMAIL1")
    dlg = wait_dialog(c, "Hòm Thư")
    assert "Hòm Thư" in dlg, dlg
    print("    dialog:", dlg)

    print("[2] danh sách thư")
    c.send(-108, b"")
    letters = None
    for _ in range(14):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -108:
            letters = parse_list(body)
            break
    assert letters is not None
    gift = [l for l in letters if l["kind"] == "giftcode"]
    assert len(gift) == 1, f"số thư giftcode: {len(gift)}"
    atts = json.loads(gift[0]["atts"])
    tien = {a["t"]: a for a in atts if a["t"] != "item"}
    items = [a for a in atts if a["t"] == "item"]
    assert tien["xu"]["n"] == 5000 and tien["luong"]["n"] == 3, atts
    assert items and items[0]["id"] == 294 and items[0]["n"] == 2 \
        and items[0].get("d") == 7, items
    print("    thư:", gift[0]["title"], "| đính kèm:", gift[0]["atts"])

    print("[3] nhận quà trong thư")
    c.send(-110, struct.pack(">i", gift[0]["id"]))
    ok = None
    for _ in range(14):
        cmd, body = c.recv(timeout=5.0)
        if cmd == -110:
            ok = body[4]
            break
    assert ok == 1, f"claim trả {ok}"
    print("    claim ok=1")

    print("[4] nhập lại cùng code -> chặn")
    textbox(c, uid, "TESTMAIL1")
    dlg2 = wait_dialog(c, "đã dùng")
    assert "đã dùng" in dlg2, dlg2
    print("    dialog:", dlg2)

    print("[5] code sai")
    textbox(c, uid, "SAI123")
    dlg3 = wait_dialog(c, "không đúng")
    assert "không đúng" in dlg3, dlg3
    print("    dialog:", dlg3)

    c.sock.close()
    time.sleep(3)

    print("[6] kiểm DB")
    row = sql("SELECT CONCAT(xu,'/',luong) FROM players WHERE user_id=1001")
    xu2, luong2 = (int(x) for x in row.split("/"))
    assert xu2 == base_xu + 5000, f"xu {base_xu} -> {xu2}"
    assert luong2 == base_luong + 3, f"luong {base_luong} -> {luong2}"
    arr = json.loads(sql("SELECT chests FROM players WHERE user_id=1001") or "[]")
    mine = [e for e in arr if e.get("id") == 294]
    assert mine, "không thấy part 294 trong rương"
    exp = mine[0].get("expired", -1)
    now = int(time.time() * 1000)
    assert 6.9 * 86400000 < exp - now < 7.1 * 86400000, \
        f"hạn item: còn {(exp - now) / 86400000:.2f} ngày"
    n_gift = int(sql("SELECT COUNT(*) FROM letters WHERE user_id=1001 AND kind='giftcode'"))
    assert n_gift == 1, f"số thư giftcode trong DB: {n_gift}"
    print(f"    xu {base_xu}->{xu2}, lượng {base_luong}->{luong2},"
          f" part 294 hạn ~7 ngày, đúng 1 thư giftcode")

    print("[7] admin gửi quà qua thư")
    def api(path, body, token=None):
        req = urllib.request.Request(
            "http://127.0.0.1:19129" + path,
            data=json.dumps(body).encode(), method="POST",
            headers={"Content-Type": "application/json",
                     **({"X-Admin-Token": token} if token else {})})
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read())
    login = api("/api/login", {"username": "betest01", "password": "abcd1234"})
    token = login.get("token")
    assert token, f"admin login: {login}"
    r = api("/api/mail/send", {"to": "betest02", "title": "Quà admin test",
            "body": "quà từ trang quản trị", "xu": 1111, "luong": 0,
            "items": [{"id": 472, "quantity": 1, "day": 0}]}, token)
    assert r.get("sent") == 1, f"mail/send: {r}"
    grow = sql("SELECT attachments FROM letters WHERE user_id=1001"
               " AND kind='gift' ORDER BY id DESC LIMIT 1")
    ga = json.loads(grow)
    assert {a["t"] for a in ga} == {"xu", "item"} and ga[0]["n"] == 1111, ga
    print("    sent=1, đính kèm:", grow)

    print("[dọn] xoá dữ liệu test + khôi phục betest02")
    sql("DELETE FROM letters WHERE user_id=1001 AND kind IN ('giftcode','gift')")
    sql("DELETE FROM giftcode_use WHERE user=1001")
    sql("DELETE FROM giftcode WHERE code='TESTMAIL1'")
    sql(f"UPDATE players SET xu={base_xu}, luong={base_luong},"
        f" chests='{base_chests.replace(chr(39), chr(39) * 2)}' WHERE user_id=1001")
    print("LIVE GIFT->MAIL TEST PASS")


if __name__ == "__main__":
    main()
