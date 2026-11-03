#!/usr/bin/env python3
"""Sinh avatar/Home/HouseItemData.java (giá catalog nội thất) từ
assets/data/houseitems.json — bản thân file JSON được giải mã từ
server/res/data/map_item_type.dat (xem docs/house-spec.md §5.1).
Chạy: python3 tools/sync_house_data.py"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
items = json.load(open(os.path.join(ROOT, "assets/data/houseitems.json")))
items.sort(key=lambda i: i["id"])
assert [i["id"] for i in items] == list(range(len(items))), "id nội thất phải liên tục từ 0"

def arr(field):
    return ", ".join(str(i[field]) for i in items)

out = f"""package avatar.Home;

/** Catalog nội thất (MapItemType) — SINH TỰ ĐỘNG bằng tools/sync_house_data.py
 *  từ assets/data/houseitems.json (giải mã map_item_type.dat của gốc).
 *  Đừng sửa tay. priceXu đã nhân 1000 như client gốc hiển thị; 0 = không bán bằng loại tiền đó. */
public final class HouseItemData {{
    public static final int COUNT = {len(items)};
    public static final int[] PRICE_XU = {{{arr('priceXu')}}};
    public static final int[] PRICE_LUONG = {{{arr('priceLuong')}}};
    public static final byte[] BUY = {{{arr('buy')}}};

    private HouseItemData() {{}}

    public static int priceXu(int id) {{
        return id >= 0 && id < COUNT ? PRICE_XU[id] : -1;
    }}

    public static int priceLuong(int id) {{
        return id >= 0 && id < COUNT ? PRICE_LUONG[id] : -1;
    }}
}}
"""
dst = os.path.join(ROOT, "server/src/main/java/avatar/Home/HouseItemData.java")
os.makedirs(os.path.dirname(dst), exist_ok=True)
open(dst, "w", encoding="utf-8").write(out)
print("đã sinh", dst, f"({len(items)} món)")
