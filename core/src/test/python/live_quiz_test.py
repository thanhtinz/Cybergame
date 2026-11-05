# -*- coding: utf-8 -*-
"""Test song DO VUI TREN MAP [PORT, goi -121]:
A (admin) + B cung khu.
1. A FORCE (sub 8) -> ca hai nhan QUESTION sub 0 (4 dap an, 30s).
2. B tra loi (thu tung lua chon qua cac cau FORCE tiep theo cho
   toi khi dung) -> FEEDBACK sub 3 {1, xu>0, dap an dung}; xu B
   tang dung bang so xu thuong (doc qua goi -33 hoac DB sau khi
   ngat ket noi thi updateXu ghi ngay).
3. A tra loi SAI o mot cau moi -> FEEDBACK {0, 0, dap an dung}.
4. A tra loi lai cung cau -> khong duoc cong them (answered).
Don dep: khong (xu thuong la that, so tien nho)."""
import struct
import sys
import time

sys.path.insert(0, '.')
from live_tauchien_test import login
from live_preview_test import join_map
from live_createchar_test import db

QUIZ = -121
ok = True

def check(name, cond):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name)
    ok = ok and cond

def rd_utf(body, off):
    ln = struct.unpack('>H', body[off:off + 2])[0]
    return body[off + 2:off + 2 + ln].decode('utf-8', 'replace'), off + 2 + ln

def wait_quiz(c, sub, timeout=12.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            cmd, body = c.recv(timeout=2.0)
        except (TimeoutError, OSError):
            continue
        if cmd == QUIZ and body and body[0] == sub:
            return body
    return None

def parse_question(body):
    off = 1
    qid = struct.unpack_from('>i', body, off)[0]; off += 4
    q, off = rd_utf(body, off)
    answers = []
    for _ in range(4):
        a, off = rd_utf(body, off)
        answers.append(a)
    secs = body[off]; off += 1
    maxxu = struct.unpack_from('>i', body, off)[0]; off += 4
    return qid, q, answers, secs, maxxu

def parse_feedback(body):
    return body[1], struct.unpack_from('>i', body, 2)[0], rd_utf(body, 6)[0]

def main():
    a, ga = login('betest01', 'abcd1234')
    assert ga != 0
    assert join_map(a), 'A vao map'
    time.sleep(14)
    b, gb = login('betest02', 'abcd1234')
    assert gb != 0
    assert join_map(b), 'B vao map'
    xu_b0 = int(db("SELECT xu FROM players WHERE user_id=1001") or 0)
    got_right = False
    wrong_checked = False
    reward_total = 0
    for rnd in range(4):
        a.send(QUIZ, struct.pack('>b', 8))
        body_b = wait_quiz(b, 0)
        check('cau %d: B nhan QUESTION' % (rnd + 1), body_b is not None)
        if body_b is None:
            break
        qid, q, answers, secs, maxxu = parse_question(body_b)
        if rnd == 0:
            check('QUESTION co 4 dap an, 30 giay, thuong 2000',
                  len(answers) == 4 and secs == 30 and maxxu == 2000)
        # A tra loi lua chon 0 o cau dau de kiem FEEDBACK (dung/sai deu co)
        a.send(QUIZ, struct.pack('>bib', 1, qid, 0))
        fb_a = wait_quiz(a, 3)
        check('cau %d: A nhan FEEDBACK' % (rnd + 1), fb_a is not None)
        if fb_a:
            okf, rew, right = parse_feedback(fb_a)
            if okf == 0:
                wrong_checked = True
                check('dap an dung tra ve nam trong 4 dap an', right in answers)
            else:
                reward_total += 0  # A dung thi B van thu tiep binh thuong
        # B thu lua chon theo vong (0,1,2,3)
        b.send(QUIZ, struct.pack('>bib', 1, qid, rnd))
        fb_b = wait_quiz(b, 3)
        if fb_b:
            okf, rew, right = parse_feedback(fb_b)
            if okf == 1:
                got_right = True
                reward_total = rew
                check('B tra loi dung, thuong %d xu' % rew, rew in (2000, 1200, 600))
                break
    check('B co mot cau tra loi dung', got_right)
    check('co kiem chung FEEDBACK sai cua A', wrong_checked or got_right)
    b.sock.close()
    time.sleep(2.5)
    xu_b1 = int(db("SELECT xu FROM players WHERE user_id=1001") or 0)
    check('xu B tang dung bang thuong (%d -> %d)' % (xu_b0, xu_b1),
          xu_b1 - xu_b0 == reward_total and reward_total > 0)
    a.sock.close()
    print('ALL PASS' if ok else 'CO FAIL')

main()
