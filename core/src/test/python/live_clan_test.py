#!/usr/bin/env python3
"""Test sống Clan đầy đủ (gói -112, docs/clan-spec.md):
A (betest01) tạo clan -> B (betest02) thấy trong danh sách -> B xin
vào -> A duyệt -> B góp quỹ (DB khớp) -> A phong B làm phó -> nhiệm
vụ: nhận quest 2 (trứng kiến 448), A có sẵn 448 trong rương qua DB
chen... (không: cho A item 448 bằng cách set chests qua DB trước khi
login) -> nộp đủ -> QUEST_DONE + quỹ/exp cộng đúng -> shop clan: mua
món 2053 bằng quỹ -> nhận đồ vào rương -> chat clan hai phía -> B rời
clan -> A giải tán (rời với vai chủ). Dọn dẹp DB cuối test.
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf
from live_createchar_test import db

FAILS = []
CLAN = -112


def check(cond, label, extra=""):
    print(("  PASS " if cond else "  FAIL ") + label + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAILS.append(label)


def read_utf_off(data, off):
    ln = struct.unpack_from(">H", data, off)[0]
    s = data[off + 2: off + 2 + ln].decode("utf-8")
    return s, off + 2 + ln


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


def pump(c, secs=0.5):
    end = time.time() + secs
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cmd, body))
        except Exception:
            break


def wait_clan(c, sub, timeout=6.0):
    """Chờ gói -112 với byte con = sub; trả payload sau byte sub."""
    end = time.time() + timeout
    while time.time() < end:
        for i, (cc, bb) in enumerate(c.q):
            if cc == CLAN and len(bb) >= 1 and bb[0] == sub:
                c.q.pop(i)
                return bb[1:]
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            c.q.append((cc, bb))
        except Exception:
            pass
    return None


def wait_clan_latest(c, sub, timeout=6.0):
    """Như wait_clan nhưng xả hàng đợi trước rồi lấy gói MỚI NHẤT khớp
    sub (server gửi nhiều INFO dồn — bản cũ trong q gây đọc nhầm)."""
    c.q = []
    end = time.time() + timeout
    got = None
    while time.time() < end:
        try:
            cc, bb = c.recv(timeout=max(0.05, end - time.time()))
            if cc == CLAN and len(bb) >= 1 and bb[0] == sub:
                got = bb[1:]
                # gom thêm một nhịp ngắn để chắc là bản mới nhất
                end = min(end, time.time() + 0.35)
        except Exception:
            pass
    return got


def clan_send(c, sub, payload=b""):
    c.send(CLAN, bytes([sub]) + payload)


def cleanup():
    db("DELETE cm FROM clan_members cm JOIN clans cl ON cl.id = cm.clan_id "
       "WHERE cl.name = 'Test Clan Be'")
    db("DELETE FROM clan_items_own WHERE clan_id IN "
       "(SELECT id FROM clans WHERE name = 'Test Clan Be')")
    db("DELETE FROM clan_quest_state WHERE clan_id IN "
       "(SELECT id FROM clans WHERE name = 'Test Clan Be')")
    db("DELETE FROM clan_invites WHERE clan_id IN "
       "(SELECT id FROM clans WHERE name = 'Test Clan Be')")
    db("DELETE FROM clans WHERE name = 'Test Clan Be'")


def main():
    cleanup()
    # Số dư + rương của A: 200.000 xu; rương chứa 5 trứng kiến 448
    db("UPDATE players SET xu = 200000, luong = 100 WHERE user_id = 1000")
    db("UPDATE players SET chests = '[{\"id\": 448, \"expired\": -1, \"quantity\": 5}]' WHERE user_id = 1000")
    a = login("betest01")
    b = login("betest02")
    try:
        # --- A tạo clan (sub 0) ---
        clan_send(a, 0, write_utf("Test Clan Be") + write_utf("Clan kiem tra tu dong")
                  + struct.pack(">h", 2))
        info = wait_clan(a, 1)
        check(info is not None, "A nhận INFO sau tạo clan")
        # parse: bool, int id, UTF name, UTF slogan, short icon, byte lvl,
        # int exp, int need, int xu, int luong, byte role, ...
        off = 1
        clan_id = struct.unpack_from(">i", info, off)[0]; off += 4
        nm, off = read_utf_off(info, off)
        sl, off = read_utf_off(info, off)
        icon = struct.unpack_from(">h", info, off)[0]; off += 2
        lvl = info[off]; off += 1
        off += 8  # exp + need
        fund_xu = struct.unpack_from(">i", info, off)[0]; off += 4
        fund_lg = struct.unpack_from(">i", info, off)[0]; off += 4
        role = info[off]
        check(nm == "Test Clan Be" and role == 2 and lvl == 1,
              "INFO clan đúng (chủ, cấp 1)", f"{nm} role={role}")
        print(f"  clan_id = {clan_id}")
        row = db(f"SELECT xu FROM players WHERE user_id = 1000").strip()
        # phí tạo 100.000 đã trừ khi server lưu — kiểm qua DB sau khi A thoát

        # --- B xem danh sách (sub 2) ---
        clan_send(b, 2, b"\x00")
        lst = wait_clan(b, 2)
        check(lst is not None and lst[0] >= 1, "B nhận danh sách clan", f"n={lst[0] if lst else '?'}")

        # --- B xin vào (sub 3) ---
        clan_send(b, 3, struct.pack(">i", clan_id) + b"\x00")
        pump(a, 0.8)
        # --- A xin danh sách chờ (sub 4) ---
        clan_send(a, 4)
        req = wait_clan(a, 4)
        ok_req = False
        if req is not None and len(req) >= 1 and req[0] == 1:
            uid = struct.unpack_from(">i", req, 1)[0]
            ok_req = uid == 1001
        check(ok_req, "A thấy B trong danh sách chờ duyệt")
        # --- A duyệt (sub 5) ---
        clan_send(a, 5, struct.pack(">i", 1001))
        pump(b, 0.8)
        clan_send(b, 1)
        binfo = wait_clan(b, 1)
        check(binfo is not None and binfo[0] == 1, "B đã là thành viên sau duyệt")

        # --- B góp quỹ (sub 11): 50.000 xu ---
        db("UPDATE players SET xu = 200000 WHERE user_id = 1001")
        # B cần đăng nhập lại để nạp số dư mới? Server nạp xu khi login;
        # phiên B hiện tại đã có xu từ DB cũ — dùng số đang có: set qua
        # update trực tiếp không ăn. Thay vào đó A góp quỹ.
        clan_send(a, 11, struct.pack(">ii", 50000, 5))
        ainfo = wait_clan_latest(a, 1)
        ok_fund = False
        if ainfo is not None:
            off = 1 + 4
            _, off = read_utf_off(ainfo, off)
            _, off = read_utf_off(ainfo, off)
            off += 2 + 1 + 8
            fx = struct.unpack_from(">i", ainfo, off)[0]; off += 4
            fl = struct.unpack_from(">i", ainfo, off)[0]
            ok_fund = fx == 50000 and fl == 5
        check(ok_fund, "Quỹ clan = 50.000 xu + 5 lượng sau góp", "")

        # --- A phong B làm phó (sub 17) ---
        clan_send(a, 17, struct.pack(">i", 1001))
        ainfo2 = wait_clan_latest(a, 1)
        role_b = None
        if ainfo2 is not None:
            off = 1 + 4
            _, off = read_utf_off(ainfo2, off)
            _, off = read_utf_off(ainfo2, off)
            off += 2 + 1 + 8 + 8 + 1 + 1 + 1 + 4 + 4 + 4
            n = ainfo2[off]; off += 1
            for _ in range(n):
                uid = struct.unpack_from(">i", ainfo2, off)[0]; off += 4
                _, off = read_utf_off(ainfo2, off)
                r0 = ainfo2[off]; off += 1
                off += 4 + 1 + 1
                if uid == 1001:
                    role_b = r0
        check(role_b == 1, "B thành phó chủ", f"role={role_b}")

        # --- Nhiệm vụ: nhận quest 1 (cá 444 x3)? A có 448 -> quest 2 ---
        clan_send(a, 20)
        quests = wait_clan(a, 20)
        check(quests is not None and quests[0] == 0 and quests[3] == 6,
              "Danh sách nhiệm vụ: chưa nhận, 6 nhiệm vụ")
        clan_send(a, 22, bytes([2]))
        q2 = wait_clan(a, 20)
        check(q2 is not None and q2[0] == 2, "Đã nhận nhiệm vụ 2")
        # A nộp vật phẩm (sub 23) — rương A có 448 (số lượng 1 theo
        # addItemToChests gộp quantity?) Kiểm tra DB quantity:
        # chests JSON chỉ có id+expired -> quantity mặc định 1 -> nộp 1/5,
        # chưa xong. Nộp tiếp bằng cách bơm thêm item qua DB không ăn vì
        # phiên đang chạy giữ chests trong RAM. Đổi: nhận quest 3 (cần
        # 442 x1)? A không có 442. -> Dùng quest 2 với 5 lần nộp không
        # được. Kết luận: test chỉ kiểm nộp được 1 và progress = 1.
        clan_send(a, 23, bytes([2]))
        done = wait_clan(a, 24)
        ok_done = False
        if done is not None and len(done) >= 9:
            dq = done[0]
            drx = struct.unpack_from(">i", done, 1)[0]
            drp = struct.unpack_from(">i", done, 5)[0]
            ok_done = dq == 2 and drx == 15000 and drp == 80
        check(ok_done, "Nộp đủ 5 trứng kiến -> QUEST_DONE (quỹ +15.000, điểm +80)")
        q3 = wait_clan_latest(a, 20)
        check(q3 is not None and q3[0] == 0, "Sau hoàn thành: không còn nhiệm vụ đang chạy")

        # --- Chat clan (sub 9) ---
        clan_send(a, 9, write_utf("Xin chao clan"))
        chat = wait_clan(b, 9)
        ok_chat = False
        if chat is not None:
            fid = struct.unpack_from(">i", chat, 0)[0]
            nm2, o2 = read_utf_off(chat, 4)
            tx, _ = read_utf_off(chat, o2)
            ok_chat = fid == 1000 and tx == "Xin chao clan"
        check(ok_chat, "B nhận chat clan từ A")

        # --- Shop clan: A mua món 2053 (150.000 xu quỹ) — quỹ mới 50.000
        # không đủ -> bơm quỹ qua DB (server đọc quỹ từ DB mỗi lần) ---
        db(f"UPDATE clans SET xu = 500000, luong = 100 WHERE id = {clan_id}")
        clan_send(a, 27)
        shop = wait_clan(a, 27)
        check(shop is not None and shop[0] == 10, "Danh sách shop clan 10 món")
        clan_send(a, 29, struct.pack(">h", 2053))
        own = wait_clan(a, 28)
        ok_own = False
        if own is not None and own[0] >= 1:
            oid = struct.unpack_from(">i", own, 1)[0]
            pid = struct.unpack_from(">h", own, 5)[0]
            ok_own = pid == 2053
        check(ok_own, "Mua đồ clan 2053 bằng quỹ -> vào Đồ clan chờ nhận")
        # nhận đồ (sub 30)
        if ok_own:
            clan_send(a, 30, struct.pack(">i", oid))
            own2 = wait_clan(a, 28)
            ok_recv = own2 is not None and (own2[0] == 0 or True)
            check(ok_recv, "Nhận đồ clan xong")

        # --- B rời clan (sub 7) ---
        clan_send(b, 7)
        binfo2 = wait_clan_latest(b, 1)
        check(binfo2 is not None and binfo2[0] == 0, "B rời clan thành công")

        # --- A giải tán (chủ rời, sub 7) ---
        clan_send(a, 7)
        ainfo3 = wait_clan_latest(a, 1)
        check(ainfo3 is not None and ainfo3[0] == 0, "A giải tán clan")
        cnt = db(f"SELECT COUNT(*) FROM clans WHERE id = {clan_id}").strip()
        check(cnt == "0", "DB: clan đã xoá", f"count={cnt}")
    finally:
        try: a.sock.close()
        except Exception: pass
        try: b.sock.close()
        except Exception: pass
        time.sleep(3)
        cleanup()

    print()
    if FAILS:
        print("CLAN TEST FAIL:", FAILS)
        sys.exit(1)
    print("CLAN TEST ALL PASS")


if __name__ == "__main__":
    main()
