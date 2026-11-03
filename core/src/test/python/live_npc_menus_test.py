#!/usr/bin/env python3
"""Test sống NPC TÀI XỈU (864) + TIÊN TRI (860) với server Java thật (19128).

Cả hai ở server map 11 (client 12). Luồng gốc (NpcHandler):
- 864: -61 -> menu -59 [Cược bằng Xu / Cược bằng Lượng / lịch sử 10 ván /
  top... / Thành tích / Thoát] -> chọn "Cược bằng Xu" -> menu con -59
  [Cược Tài (Xu) / Cược Xỉu (Xu)] -> chọn -> server mở TEXT_BOX -60
  {int uid, byte menuId=0, UTF câu hỏi, byte type} -> client trả -60
  {int uid, byte 0, UTF "100"} -> TaiXiu.handleBetWithInput xử lý cược.
- 860: -61 -> menu -59 [Dự đoán vị trí người yêu / Dự đoán kết quả sổ số /
  Dự đoán kết quả Tài Xỉu / Thoát] -> chọn 0 -> dialog -10 (DuDoanNY).

Chạy: python3 live_npc_menus_test.py
"""
import struct
import sys
import time

sys.path.insert(0, ".")
from live_test import Conn, write_utf, read_utf, VERSION  # noqa: E402

FAIL = []
MAP = 11  # server map; client 12
TX_WIRE = 2000000000 + 864
TT_WIRE = 2000000000 + 860


def check(name, cond, extra=""):
    print(("  PASS " if cond else "  FAIL ") + name + (f"  [{extra}]" if extra else ""))
    if not cond:
        FAIL.append(name)


def wait_cmd(c: Conn, want, timeout=8.0):
    seen = []
    try:
        while True:
            cmd, body = c.recv(timeout=timeout)
            seen.append(cmd)
            if cmd == want:
                return body, seen
            timeout = 2.0
    except Exception:
        raise AssertionError(f"không nhận được cmd {want}; đã thấy {seen}")


def drain(c: Conn, secs=2.0):
    got = []
    try:
        while True:
            cmd, body = c.recv(timeout=secs)
            got.append((cmd, body))
            secs = 0.6
    except Exception:
        pass
    return got


def login(user="betest01", pw="abcd1234"):
    c = Conn()
    c.handshake()
    c.openers()
    c.send(-2, write_utf(user) + write_utf(pw) + write_utf(VERSION))
    body, _ = wait_cmd(c, -4)
    return c, struct.unpack(">i", body[:4])[0]


def parse_menu(body):
    off = 0
    m_uid = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    menu_id = body[off]
    off += 1
    n = body[off]
    off += 1
    names = []
    for _ in range(n):
        ln = struct.unpack(">H", body[off:off + 2])[0]
        names.append(read_utf(body, off))
        off += 2 + ln
    return m_uid, menu_id, names


def open_menu(c, wire):
    # server chống spam khi nói chuyện liên tục: chờ một nhịp rồi gửi -61
    time.sleep(2.5)
    c.send(-61, struct.pack(">i", wire))
    body, _ = wait_cmd(c, -59, timeout=6.0)
    return parse_menu(body)


def select(c, wire, menu_id, idx):
    c.send(-59, struct.pack(">ibb", wire, menu_id, idx))


def move(c, pts):
    for x, y in pts:
        c.send(54, struct.pack(">hhb", x, y, 0))
        time.sleep(0.15)
    drain(c, 1.0)


def main():
    c, uid = login()
    check("login id = 1000", uid == 1000, str(uid))

    print("[1] join map 11 (server) + tới cạnh Tài Xỉu (80,50)")
    body = struct.pack(">b", MAP) + struct.pack(">b", -1) \
        + struct.pack(">h", 100) + struct.pack(">h", 90)
    c.send(50, body)
    wait_cmd(c, 50)
    move(c, [(90, 60), (84, 56)])

    print("[2] Tài Xỉu: -61 -> menu -59 cấp 1")
    m_uid, m_id, names = open_menu(c, TX_WIRE)
    check("menu thuộc Tài Xỉu", m_uid == TX_WIRE, str(m_uid))
    check("menu cấp 1 đúng gốc",
          names == ["Cược bằng Xu", "Cược bằng Lượng", "lịch sử 10 ván",
                    "top cao thủ thắng và thua nhiều nhất", "Thành tích", "Thoát"],
          str(names))

    print("[3] chọn 'Cược bằng Xu' -> menu con -59")
    select(c, TX_WIRE, m_id, 0)
    body, _ = wait_cmd(c, -59)
    m_uid2, m_id2, names2 = parse_menu(body)
    check("menu con cược Xu", names2 == ["Cược Tài (Xu)", "Cược Xỉu (Xu)"], str(names2))

    print("[4] chọn 'Cược Tài (Xu)' -> TEXT_BOX -60 hỏi số xu")
    select(c, TX_WIRE, m_id2, 0)
    body, _ = wait_cmd(c, -60)
    off = 0
    tb_uid = struct.unpack(">i", body[off:off + 4])[0]
    off += 4
    tb_menu = body[off]
    off += 1
    prompt = read_utf(body, off)
    check("textbox menuId = 0 (Cược Tài Xu)", tb_menu == 0, str(tb_menu))
    check("câu hỏi nhắc Tài", "Tài" in prompt, prompt)

    print("[5] trả lời -60 cược 100 xu -> server phản hồi")
    c.send(-60, struct.pack(">ib", uid, 0) + write_utf("100"))
    got = drain(c, 3.0)
    resp = [(cmd, read_utf(b, 0)[:80]) for cmd, b in got if cmd in (-9, -10) and b]
    check("có phản hồi cược (-9/-10)", len(resp) > 0, str(resp[:2]))

    print("[6] Tiên tri: đi tới (250,64) + -61 -> menu -59")
    move(c, [(150, 60), (210, 62), (246, 60)])
    m_uid, m_id, names = open_menu(c, TT_WIRE)
    check("menu thuộc Tiên tri", m_uid == TT_WIRE, str(m_uid))
    check("menu tiên tri đúng gốc",
          names == ["Dự đoán vị trí người yêu", "Dự đoán kết quả sổ số",
                    "Dự đoán kết quả Tài Xỉu", "Thoát"], str(names))

    print("[7] chọn 'Dự đoán vị trí người yêu' -> dialog -10")
    select(c, TT_WIRE, m_id, 0)
    body, _ = wait_cmd(c, -10)
    dlg = read_utf(body, 0)
    check("dialog dự đoán có nội dung", len(dlg) > 0, dlg[:80])

    c.close()
    print()
    if FAIL:
        print("KẾT QUẢ: FAIL ->", FAIL)
        sys.exit(1)
    print("KẾT QUẢ: PASS TẤT CẢ")


if __name__ == "__main__":
    main()
