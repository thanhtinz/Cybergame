# -*- coding: utf-8 -*-
"""Test sống [CREATE] tính năng 12 NPC (docs/npc-fun-spec.md):
xổ số Vé Số (quyết toán vé trúng gieo hạt Java Random), quiz phiên
645 + 760 (trả lời đúng hết), giới hạn ngày, và các tính năng qua
menu (Tiên Cá / Phi Hành Gia / bói / luyện dược / gacha / Zombie /
Ăn Xin + điểm Phúc). Chia 2 phase: A = tiền xác định, B = ngẫu nhiên
(chỉ kiểm dialog + bộ đếm DB)."""
import struct, time, sys
from live_test import Conn, write_utf
from live_createchar_test import db
from live_bossshop_test import login, join, Rd, pump

UID = 1000
FAILS = []

def check(name, cond, extra=""):
    print(("  OK  " if cond else "  FAIL") + f" {name} {extra}")
    if not cond:
        FAILS.append(name)

# ---------- Java Random + String.hashCode (khớp NpcFunService) ----------
def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - 0x100000000 if h >= 0x80000000 else h

class JavaRandom:
    def __init__(self, seed):
        self.seed = ((seed & 0xFFFFFFFFFFFFFFFF) ^ 0x5DEECE66D) & ((1 << 48) - 1)
    def next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.seed >> (48 - bits)
    def nextInt(self, n):
        while True:
            bits = self.next(31)
            val = bits % n
            if ((bits - val + (n - 1)) & 0xFFFFFFFF) < 0x80000000:
                return val

def win_number(day_iso):
    return JavaRandom(java_hash("AVDK-LOTTERY-" + day_iso)).nextInt(100)

EN_BANK = {
    "\"Apple\" trong tiếng Anh nghĩa là gì?": "Quả táo",
    "Từ nào sau đây là động từ?": "Run",
    "\"Good morning\" là lời chào vào lúc nào?": "Buổi sáng",
    "Số \"seven\" trong tiếng Anh là số mấy?": "7",
    "Con mèo trong tiếng Anh gọi là gì?": "Cat",
    "\"Thank you\" nghĩa là gì?": "Cảm ơn",
    "Màu đỏ trong tiếng Anh là gì?": "Red",
    "Từ trái nghĩa của \"big\" là gì?": "Small",
    "Điền từ đúng: \"I ___ a student.\"": "am",
    "Thứ Hai trong tiếng Anh là gì?": "Monday",
    "\"Water\" trong tiếng Anh nghĩa là gì?": "Nước",
    "Số \"twenty\" trong tiếng Anh là số mấy?": "20",
}
RIDDLE_BANK = {
    "Câu đố của Nhân Sư: Cái gì càng đào càng lớn?": "Cái hố",
    "Câu đố của Nhân Sư: Bánh gì mà không ăn được?": "Bánh xe",
    "Câu đố của Nhân Sư: Cái gì đi thì nằm, đứng thì nằm, nhưng nằm thì đứng?": "Bàn chân",
    "Câu đố của Nhân Sư: Quả gì có rất nhiều mắt?": "Quả dứa",
    "Câu đố của Nhân Sư: Cái gì đen khi mua, đỏ khi dùng, xám xịt khi vứt đi?": "Than",
    "Câu đố của Nhân Sư: Cái gì chỉ tăng mà không bao giờ giảm?": "Tuổi",
    "Câu đố của Nhân Sư: Cái gì là của bạn, nhưng người khác dùng nhiều hơn bạn?": "Tên của bạn",
    "Câu đố của Nhân Sư: Cái gì càng kéo càng ngắn?": "Điếu thuốc",
    "Câu đố của Nhân Sư: Con gì mang được khúc gỗ to mà không mang nổi hòn sỏi nhỏ?": "Con sông",
    "Câu đố của Nhân Sư: Đường gì ngọt mà không đi lên được?": "Đường cát",
}

def wait_cmd(c, want, timeout=12, label=""):
    end = time.time() + timeout
    while time.time() < end:
        pump(c, 0.3)
        for i, (cmd, body) in enumerate(c.q):
            if cmd == want:
                c.q.pop(i)
                return body
    raise TimeoutError(f"timeout chờ cmd {want} ({label}); q={[x[0] for x in c.q]}")

def wait_npcfun(c, want_sub, timeout=12, label=""):
    end = time.time() + timeout
    while time.time() < end:
        pump(c, 0.3)
        for i, (cmd, body) in enumerate(c.q):
            if cmd == -126 and body and body[0] == want_sub:
                c.q.pop(i)
                return body[1:]
    raise TimeoutError(f"timeout chờ NPCFUN sub {want_sub} ({label})")

def wait_dialog(c, needle, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        pump(c, 0.3)
        for i, (cmd, body) in enumerate(list(c.q)):
            if cmd == -10:
                try:
                    txt = Rd(body).utf()
                except Exception:
                    continue
                if needle in txt:
                    c.q.pop(i)
                    return txt
    return None

def open_menu(c, npc_id, map_wire, x, y):
    join(c, map_wire, x, y)
    wire = 2000000000 + npc_id
    c.send(-61, struct.pack(">i", wire))
    r = Rd(wait_cmd(c, -59, label=f"menu {npc_id}"))
    uid = r.int(); r.byte(); n = r.ubyte()
    names = [r.utf() for _ in range(n)]
    time.sleep(1.6)
    return wire, names

def select(c, wire, idx, menu_id=0):
    c.send(-59, struct.pack(">ibb", wire, menu_id, idx))
    time.sleep(0.6)

def run_quiz(c, bank, label):
    """Nhận câu hỏi (sub 11), trả lời đúng theo bank, tới sub 12."""
    for step in range(12):
        body = wait_npcfun(c, 11, label=f"{label} q{step}")
        r = Rd(body)
        npc = r.short(); pos = r.byte(); total = r.byte()
        q = r.utf(); opts = [r.utf() for _ in range(4)]; secs = r.short()
        correct = bank.get(q)
        check(f"{label}: câu {pos}/{total} có trong bank", correct is not None, q[:40])
        idx = opts.index(correct) if correct in opts else 0
        c.send(-126, struct.pack(">bb", 3, idx))
        time.sleep(0.4)
        # câu tiếp theo HOẶC kết thúc: nhìn trộm hàng đợi
        end = time.time() + 6
        done_body = None
        while time.time() < end:
            pump(c, 0.3)
            hit = None
            for i, (cmd, b) in enumerate(c.q):
                if cmd == -126 and b and b[0] == 12:
                    hit = i; done_body = b[1:]; break
                if cmd == -126 and b and b[0] == 11:
                    hit = -1; break
            if hit is not None:
                if hit >= 0:
                    c.q.pop(hit)
                break
        if done_body is not None:
            return Rd(done_body).utf()
    raise TimeoutError(f"{label}: quiz không kết thúc")

def vn_today():
    import datetime
    return (datetime.datetime.now(datetime.timezone.utc)
            + datetime.timedelta(hours=7)).date()

def main():
    import json as _json
    today = vn_today()
    yday = today.fromordinal(today.toordinal() - 1)
    win = win_number(yday.isoformat())
    ld = (win + 10) % 100
    if ld == win:
        ld = (win + 20) % 100
    print(f"[setup] số trúng hôm qua ({yday}) = {win:02d}, vé số cuối = {ld:02d}")
    # tạo bảng trước (service tạo lười khi dùng lần đầu — seed phải
    # chạy sau khi bảng tồn tại, db() nuốt lỗi nên INSERT sẽ mất hút)
    db("CREATE TABLE IF NOT EXISTS npc_fun_daily (user_id INT, npc_id INT,"
       " day VARCHAR(10), cnt INT DEFAULT 0, PRIMARY KEY (user_id, npc_id, day))")
    db("CREATE TABLE IF NOT EXISTS npc_state (user_id INT, npc_id INT,"
       " data VARCHAR(255) DEFAULT '', PRIMARY KEY (user_id, npc_id))")
    db("CREATE TABLE IF NOT EXISTS lottery_tickets (user_id INT, day VARCHAR(10),"
       " number INT, settled TINYINT DEFAULT 0, PRIMARY KEY (user_id, day, number))")
    db("DELETE FROM npc_fun_daily WHERE user_id = %d" % UID)
    db("DELETE FROM npc_state WHERE user_id = %d" % UID)
    db("DELETE FROM lottery_tickets WHERE user_id = %d" % UID)
    db("INSERT INTO lottery_tickets (user_id, day, number, settled) VALUES (%d, '%s', %d, 0)"
       % (UID, yday.isoformat(), win))
    db("INSERT INTO lottery_tickets (user_id, day, number, settled) VALUES (%d, '%s', %d, 0)"
       % (UID, yday.isoformat(), ld))
    db("UPDATE players SET xu = 200000, luong = 99900 WHERE user_id = %d" % UID)
    ns = _json.loads(db("SELECT nongsan FROM farm_data WHERE user_id = %d" % UID))
    found = False
    for e in ns:
        if e.get("id") == 207:
            e["soluong"] = max(e.get("soluong", 0), 3); found = True
    if not found:
        ns.append({"id": 207, "soluong": 3})
    db("UPDATE farm_data SET nongsan = '%s' WHERE user_id = %d"
       % (_json.dumps(ns, separators=(",", ":")), UID))

    # ================= PHASE A: tiền xác định =================
    c, uid = login()
    check("login betest01", uid == UID, str(uid))

    print("[A1] Vé Số 465: menu + quyết toán vé hôm qua + mua vé")
    wire, names = open_menu(c, 465, 11, 630, 68)
    check("465 menu", names[:1] == ["Mua vé số"], str(names))
    select(c, wire, 0)
    body = wait_npcfun(c, 10, label="lottery state")
    r = Rd(body)
    info = r.utf(); n = r.byte(); tickets = [r.byte() for _ in range(n)]
    left = r.byte(); price = r.int(); claimed = r.int()
    check("465: quyết toán trúng 200.000 + 25.000", claimed == 225000, f"claimed={claimed} info={info}")
    check("465: chưa có vé kỳ mới", tickets == [] and left == 5 and price == 5000,
          f"tickets={tickets} left={left} price={price}")
    c.send(-126, struct.pack(">bb", 1, 42))
    msg = wait_npcfun(c, 13, label="buy msg")
    r = Rd(msg); ok = r.byte() != 0; txt = r.utf()
    check("465: mua vé 42 OK", ok and "42" in txt, txt)
    body = wait_npcfun(c, 10, label="lottery state 2")
    r = Rd(body); r.utf(); n = r.byte(); tickets = [r.byte() for _ in range(n)]
    left = r.byte(); r.int(); r.int()
    check("465: vé kỳ mới = [42], còn 4 lượt", tickets == [42] and left == 4,
          f"tickets={tickets} left={left}")

    print("[A2] Cô giáo tiếng Anh 645: quiz 5/5 + chặn làm lại")
    wire, names = open_menu(c, 645, 6, 140, 100)
    select(c, wire, 0)
    end_txt = run_quiz(c, EN_BANK, "645")
    check("645: đúng 5/5, nhận 4.000 xu", "5/5" in end_txt and "4.000" in end_txt, end_txt)
    wire, names = open_menu(c, 645, 6, 140, 100)
    select(c, wire, 0)
    dlg = wait_dialog(c, "đã làm rồi")
    check("645: làm lại bị chặn", dlg is not None, str(dlg))

    print("[A3] Pharaoh 760: giải đố 3/3")
    wire, names = open_menu(c, 760, 33, 232, 49)
    select(c, wire, 0)
    end_txt = run_quiz(c, RIDDLE_BANK, "760")
    check("760: đúng 3/3, nhận 4.500 xu", "3/3" in end_txt and "4.500" in end_txt, end_txt)

    try:
        c.sock.close()
    except Exception:
        pass
    expect_xu = 200000 + 225000 - 5000 + 4000 + 4500
    got = None
    end = time.time() + 150
    while time.time() < end:
        v = db("SELECT xu FROM players WHERE user_id = %d" % UID)
        if v and int(v) == expect_xu:
            got = int(v); break
        time.sleep(5)
    check("A: xu DB khớp tuyệt đối", got == expect_xu, f"expect={expect_xu} got={v}")

    # ================= PHASE B: tính năng ngẫu nhiên =================
    chests0 = _json.loads(db("SELECT chests FROM players WHERE user_id = %d" % UID) or "[]")
    c, uid = login()

    print("[B1] Tiên Cá 50: nhận phước + chặn lần 2")
    wire, names = open_menu(c, 50, 30, 172, 81)
    select(c, wire, 0)
    dlg = wait_dialog(c, "Tiên Cá")
    check("50: nhận phước có dialog", dlg is not None, str(dlg))
    wire, names = open_menu(c, 50, 30, 172, 81)
    select(c, wire, 0)
    dlg = wait_dialog(c, "đã ban phước")
    check("50: lần 2 bị chặn", dlg is not None, str(dlg))
    cnt = db("SELECT cnt FROM npc_fun_daily WHERE user_id=%d AND npc_id=50 AND day='%s'"
             % (UID, today.isoformat()))
    check("50: daily cnt = 1", cnt == "1", str(cnt))

    print("[B2] Phi Hành Gia 335: 2 chuyến bay")
    wire, names = open_menu(c, 335, 14, 48, 49)
    select(c, wire, 0)
    dlg = wait_dialog(c, "Phi Hành Gia")
    check("335: chuyến 1 có kết quả", dlg is not None, str(dlg))
    wire, names = open_menu(c, 335, 14, 48, 49)
    select(c, wire, 0)
    dlg = wait_dialog(c, "Phi Hành Gia")
    check("335: chuyến 2 có kết quả", dlg is not None, str(dlg))
    cnt = db("SELECT cnt FROM npc_fun_daily WHERE user_id=%d AND npc_id=335 AND day='%s'"
             % (UID, today.isoformat()))
    check("335: daily cnt = 2", cnt == "2", str(cnt))

    print("[B3] Bậc Thầy Phù Thủy 540: xem bói")
    wire, names = open_menu(c, 540, 58, 244, 221)
    select(c, wire, 0)
    dlg = wait_dialog(c, "Quẻ")
    check("540: quẻ có dialog", dlg is not None and dlg.startswith("Quẻ"), str(dlg))

    print("[B4] Phù Thủy Bóng Tối 425: luyện dược")
    wire, names = open_menu(c, 425, 56, 236, 197)
    select(c, wire, 0)
    dlg = wait_dialog(c, "Phù Thủy Bóng Tối")
    check("425: luyện dược có kết quả", dlg is not None, str(dlg))

    print("[B5] Pretty Witch 590: đổi quà")
    wire, names = open_menu(c, 590, 57, 192, 201)
    select(c, wire, 0)
    dlg = wait_dialog(c, "quà ma thuật")
    check("590: đổi quà có dialog", dlg is not None, str(dlg))

    print("[B6] Zombie 535: cho ăn món Cơm qua menu con")
    wire, names = open_menu(c, 535, 55, 394, 40)
    select(c, wire, 0)
    r = Rd(wait_cmd(c, -59, label="zombie submenu"))
    r.int(); r.byte(); n = r.ubyte()
    subs = [r.utf() for _ in range(n)]
    time.sleep(1.2)
    check("535: menu con có món Cơm", any("Cơm" in t for t in subs), str(subs))
    idx = next((i for i, t in enumerate(subs) if "Cơm" in t), 0)
    select(c, wire, idx, menu_id=1)
    dlg = wait_dialog(c, "Zombie")
    check("535: cho ăn có kết quả", dlg is not None, str(dlg))
    cnt = db("SELECT cnt FROM npc_fun_daily WHERE user_id=%d AND npc_id=535 AND day='%s'"
             % (UID, today.isoformat()))
    check("535: daily cnt = 1", cnt == "1", str(cnt))

    print("[B7] Ăn Xin 741: cho tiền tích Phúc + đổi Phúc")
    wire, names = open_menu(c, 741, 3, 160, 157)
    select(c, wire, 0)
    dlg = wait_dialog(c, "điểm Phúc")
    check("741: cho 1.000 có dialog Phúc", dlg is not None and "hiện có 1" in dlg, str(dlg))
    st = db("SELECT data FROM npc_state WHERE user_id=%d AND npc_id=741" % UID)
    check("741: npc_state = phuc:1", st == "phuc:1", str(st))
    for _ in range(3):
        wire, names = open_menu(c, 741, 3, 160, 157)
        select(c, wire, 2)
        dlg = wait_dialog(c, "điểm Phúc")
    check("741: cho 20.000 x3 xong", dlg is not None and "hiện có 25" in (dlg or ""), str(dlg))
    wire, names = open_menu(c, 741, 3, 160, 157)
    select(c, wire, 3)
    dlg = wait_dialog(c, "+5 lượng")
    check("741: đổi 20 Phúc = +5 lượng", dlg is not None, str(dlg))
    st = db("SELECT data FROM npc_state WHERE user_id=%d AND npc_id=741" % UID)
    check("741: npc_state còn phuc:5", st == "phuc:5", str(st))

    try:
        c.sock.close()
    except Exception:
        pass
    # rương: phải có thêm ít nhất 1 món từ các pool quà (590 chắc chắn +1)
    new_ids = []
    end = time.time() + 150
    while time.time() < end:
        chests1 = _json.loads(db("SELECT chests FROM players WHERE user_id = %d" % UID) or "[]")
        if len(chests1) > len(chests0):
            ids0 = [e["id"] for e in chests0]
            new_ids = [e["id"] for e in chests1 if e["id"] not in ids0 or ids0.remove(e["id"])]
            break
        time.sleep(5)
    pools = {291, 569, 570, 2263, 2411, 2412, 2413, 2414, 2565, 2279,
             2261, 2262, 2296, 2297, 2878, 2879, 2880, 2881, 596, 599, 600,
             618, 619, 477, 2283, 3672}
    check("B: rương có món quà mới thuộc pool", len(new_ids) >= 1 and all(i in pools for i in new_ids),
          f"new={new_ids}")

    print("LIVE NPCFUN TEST:", "ALL PASS" if not FAILS else f"FAIL: {FAILS}")
    sys.exit(1 if FAILS else 0)

if __name__ == "__main__":
    main()
