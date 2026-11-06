#!/usr/bin/env python3
"""Nhập kho part thu hoạch từ bản chính (2026-11-02) vào bản port.

Nguồn: ~/workspace/apk-avatarhd/harvest/new_parts.json (1.141 record,
bỏ qua id đã có) + ~/workspace/apk-avatarhd/live6/parts703.json (bản
mới của 703 part lõi). Đích: assets/data/items.json + SQL áp vào bảng
items của DB (cùng định dạng — DB và items.json luôn khớp 1:1).

Quyết định đã chốt: expired_day = 0 (vĩnh viễn) cho part mới — gói
−97 của bản chính không mang trường hạn dùng và tên part mới không
có hậu tố "(X ngày)" để suy ra.

Chạy: python3 tools/import_harvest.py  (ghi items.json + dist/import_harvest.sql)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HARVEST = os.path.expanduser("~/workspace/apk-avatarhd/harvest/new_parts.json")
P703 = os.path.expanduser("~/workspace/apk-avatarhd/live6/parts703.json")
ITEMS = os.path.join(ROOT, "assets/data/items.json")
OUTSQL = os.path.join(ROOT, "dist/import_harvest.sql")


def z_signed(z):
    """zOrder của gốc là byte: 255 (lớp đặc biệt) ở dạng signed là
    -1 — đúng quy ước của toàn bộ catalog hiện tại (299 part z=-1,
    0 part z>127) và cột tinyint của DB."""
    if z is None:
        return None
    return z - 256 if z > 127 else z


def anim_str(frames):
    return json.dumps(
        [{"img": f[0], "dx": f[1], "dy": f[2]} for f in frames],
        separators=(",", ":"), ensure_ascii=False)


def esc(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


def sql_val(v):
    if v is None:
        return "NULL"
    if isinstance(v, str):
        return "'" + esc(v) + "'"
    return str(v)


def main():
    items = json.load(open(ITEMS, encoding="utf-8"))
    byid = {it["id"]: it for it in items}
    sql = ["SET NAMES utf8mb4;"]

    # ---- 1) part mới ----
    new_parts = json.load(open(HARVEST, encoding="utf-8"))
    added = 0
    for key, r in new_parts.items():
        pid = int(key)
        if pid in byid:
            continue
        wearable = "frames" in r
        rec = {
            "id": pid,
            "coin": r.get("coin", -1),
            "gold": r.get("gold", -1),
            "type": r.get("follow", -1),
            "icon": r.get("icon", 0),
            "name": r.get("name", ""),
            "sell": r.get("sell", 0),
            "expired_day": 0,
            "zorder": z_signed(r.get("z")) if wearable else None,
            "gender": r.get("gender") if wearable else None,
            "level": r.get("level", 0),
            "animation": anim_str(r["frames"]) if wearable else "[]",
        }
        byid[pid] = rec
        added += 1
        cols = ("id, coin, gold, type, icon, name, sell, expired_day,"
                " zorder, gender, level, animation")
        vals = ", ".join(sql_val(rec[c]) for c in
                         ["id", "coin", "gold", "type", "icon", "name",
                          "sell", "expired_day", "zorder", "gender",
                          "level", "animation"])
        sql.append(f"INSERT INTO items ({cols}) VALUES ({vals});")
    print(f"part moi them: {added}")

    # ---- 2) cập nhật 703 part lõi (giữ expired_day hiện tại) ----
    p703 = json.load(open(P703, encoding="utf-8"))
    diffs = {"level": 0, "name": 0, "sell": 0, "price": 0, "other": 0}
    updated = 0
    for key, r in p703.items():
        pid = int(key)
        cur = byid.get(pid)
        if cur is None:
            print(f"  ! part703 {pid} khong co trong items.json — bo qua")
            continue
        # Record 703 là BẢN GHI TỪNG PHẦN: trường nào vắng thì GIỮ
        # nguyên giá trị hiện tại (540/703 có name/sell/icon, 404/703
        # có z/gender/level/frames). Áp cả record sẽ xoá trắng tên.
        newrec = {}
        if "price" in r:
            newrec["coin"] = r["price"][0]
            newrec["gold"] = r["price"][1]
        if "follow" in r: newrec["type"] = r["follow"]
        if "icon" in r: newrec["icon"] = r["icon"]
        if "name" in r: newrec["name"] = r["name"]
        if "sell" in r: newrec["sell"] = r["sell"]
        if "z" in r: newrec["zorder"] = z_signed(r["z"])
        if "gender" in r: newrec["gender"] = r["gender"]
        if "level" in r: newrec["level"] = r["level"]
        if r.get("frames"):
            # animation cũ lưu dạng escape \\\" — so nội dung đã parse,
            # bằng nhau thì giữ nguyên chuỗi cũ (tránh churn vô ích)
            try:
                old_frames = json.loads(cur.get("animation", "[]").replace('\\"', '"'))
                new_frames = [{"img": f[0], "dx": f[1], "dy": f[2]} for f in r["frames"]]
                if old_frames != new_frames:
                    newrec["animation"] = anim_str(r["frames"])
            except Exception:
                newrec["animation"] = anim_str(r["frames"])
        changed = []
        for f, v in newrec.items():
            if cur.get(f) != v:
                changed.append(f)
                cur[f] = v
        if changed:
            updated += 1
            if "level" in changed: diffs["level"] += 1
            if "name" in changed: diffs["name"] += 1
            if "sell" in changed: diffs["sell"] += 1
            if "coin" in changed or "gold" in changed: diffs["price"] += 1
            if not set(changed) <= {"level", "name", "sell", "coin", "gold"}:
                diffs["other"] += 1
            sets = ", ".join(f"{f}={sql_val(newrec[f])}" for f in changed)
            sql.append(f"UPDATE items SET {sets} WHERE id={pid};")
    print(f"part loi cap nhat: {updated} {diffs}")

    items = sorted(byid.values(), key=lambda it: it["id"])
    with open(ITEMS, "w", encoding="utf-8") as f:
        json.dump(items, f, separators=(",", ":"), ensure_ascii=False)
    print(f"items.json tong: {len(items)}")

    os.makedirs(os.path.dirname(OUTSQL), exist_ok=True)
    with open(OUTSQL, "w", encoding="utf-8") as f:
        f.write("\n".join(sql) + "\n")
    print(f"SQL: {OUTSQL} ({len(sql) - 1} lenh)")


if __name__ == "__main__":
    main()
