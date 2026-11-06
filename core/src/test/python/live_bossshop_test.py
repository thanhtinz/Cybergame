# Test sống: đấu nối shop sự kiện (gói BOSS_SHOP -78) cho NPC 861
# (Shop_Buy_Luong, map 28) và 858 Bạch Vô Thường (map 1). Luồng gốc:
# tương tác NPC -61 -> menu -59 -> chọn "Đổi Quà" -> server gửi -78
# danh sách món -> client gửi lại -78 {idBoss, idShop, index} để đổi.
import struct, sys, time
sys.path.insert(0, '.')
from live_test import Conn, write_utf
from live_createchar_test import db

FAILS = []
def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name + (f" ({extra})" if extra else ""))
    if not cond: FAILS.append(name)

class Rd:
    def __init__(self, b): self.b = b; self.o = 0
    def byte(self):
        v = struct.unpack(">b", self.b[self.o:self.o+1])[0]; self.o += 1; return v
    def ubyte(self):
        v = self.b[self.o]; self.o += 1; return v
    def int(self):
        v = struct.unpack(">i", self.b[self.o:self.o+4])[0]; self.o += 4; return v
    def short(self):
        v = struct.unpack(">h", self.b[self.o:self.o+2])[0]; self.o += 2; return v
    def utf(self):
        n = struct.unpack(">H", self.b[self.o:self.o+2])[0]; self.o += 2
        v = self.b[self.o:self.o+n].decode('utf-8'); self.o += n; return v

def pump(c, secs=0.5):
    end = time.time() + secs
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cmd, body))
        except Exception:
            break

def wait_cmd(c, want, timeout=9.0, label=""):
    end = time.time() + timeout
    while time.time() < end:
        pump(c, 0.3)
        for i, (cmd, body) in enumerate(c.q):
            if cmd == want:
                c.q.pop(i)
                return body
    raise TimeoutError(f"timeout chờ cmd {want} ({label}); q={[x[0] for x in c.q]}")

def login(user="betest01", pw="abcd1234"):
    c = Conn(); c.q = []; c.handshake(); c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf("3.4.4"))
    body = wait_cmd(c, -4, label="login")
    return c, struct.unpack(">i", body[:4])[0]

def join(c, map_id, x, y):
    body = struct.pack(">b", map_id) + struct.pack(">b", -1) \
        + struct.pack(">h", x) + struct.pack(">h", y)
    c.send(50, body)
    wait_cmd(c, 50, label="join")
    c.send(54, struct.pack(">hhb", x, y, 0))
    time.sleep(0.3)

def talk_shop(c, npc_id, map_id, x, y, want_n, label):
    wire = 2000000000 + npc_id
    join(c, map_id, x, y)
    c.send(-61, struct.pack(">i", wire))
    r = Rd(wait_cmd(c, -59, label=label + " menu"))
    uid = r.int(); r.byte(); n = r.ubyte()
    names = [r.utf() for _ in range(n)]
    check(label + ": menu thuộc NPC", uid == wire, str(uid))
    check(label + ": menu có 'Đổi Quà' đầu", names and names[0] == "Đổi Quà", str(names))
    time.sleep(2.5)  # chống dialog spam giữa hai lần -59
    c.send(-59, struct.pack(">ibb", wire, 0, 0))
    r = Rd(wait_cmd(c, -78, label=label + " bossshop"))
    typ = r.byte(); id_boss = r.int(); id_shop = r.byte(); shop_name = r.utf()
    cnt = r.short()
    items = []
    for _ in range(cnt):
        part = r.short(); dlg = r.utf()
        if typ == 1: r.utf()
        items.append((part, dlg))
    check(label + ": idBoss đúng", id_boss == wire, str(id_boss))
    check(label + f": đủ {want_n} món", cnt == want_n, f"n={cnt}")
    print(f"    {label}: shop '{shop_name}' type={typ} idShop={id_shop} "
          f"món đầu: {items[0][0]} | {items[0][1][:60]}")
    return wire, id_shop, items

def main():
    # nạp lượng cho tài khoản test để mua được ở shop 861
    db("UPDATE players SET luong=100000 WHERE user_id=1000")
    c, uid = login()
    check("login betest01", uid == 1000, str(uid))

    print("[1] NPC 861 shop Lượng (map 28)")
    wire, id_shop, items = talk_shop(c, 861, 27, 430, 72, 5, "861")  # map wire = id client - 1
    check("861: món đầu là 3672 (đá ngũ sắc)", items[0][0] == 3672, str(items[0][0]))

    print("[2] đổi món đầu ở 861 -> dialog thành công")
    c.q.clear()
    c.send(-78, struct.pack(">ibh", wire, id_shop, 0))
    dlg = None
    end = time.time() + 6
    while time.time() < end and dlg is None:
        pump(c, 0.3)
        for cmd, body in list(c.q):
            if cmd == -10:
                try: dlg = Rd(body).utf()
                except Exception: dlg = "?"
    check("861: server trả dialog", dlg is not None, str(dlg))
    check("861: đổi thành công", dlg is not None and "thành công" in dlg, str(dlg))

    print("[3] NPC 858 Bạch Vô Thường (map 1)")
    talk_shop(c, 858, 0, 66, 65, 33, "858")  # map wire = id client - 1

    print("ALL PASS" if not FAILS else f"FAIL: {FAILS}")
    sys.exit(1 if FAILS else 0)

if __name__ == "__main__":
    main()
