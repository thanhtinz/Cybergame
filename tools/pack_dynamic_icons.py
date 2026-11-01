#!/usr/bin/env python3
"""Đóng gói icon của part ĐỘNG (id >= 2000) thành atlas cho bản port.

Cơ chế gốc (Part.cs paint): part tĩnh lấy icon từ imgdata (vùng trên
sheet big), part động lấy icon từ ẢNH RIÊNG theo id icon — phía client
gốc là ảnh server stream về (gói -14), trong release Pack5 chúng nằm ở
res/hd/item/<iconId>.png. Bản port chưa có lớp ảnh này nên drawPartIcon
tra nhầm sang imgdata: icon của món động hoặc trống, hoặc vớ phải hình
của món tĩnh trùng số (ví dụ icon 537 trong imgdata là con chó của part
605, trong khi part động 2169 "thuyền trưởng" cũng trỏ icon 537).

Script này shelf-pack mọi icon động mà part tham chiếu (và imgdata
không có) vào các sheet 2048x2048 ở assets/iconatlas/, kèm
assets/data/icondata.json cùng định dạng dòng với imgdata nhưng toạ độ
là pixel HD thật trên sheet atlas. Chạy lại khi items.json thay đổi:

    python3 tools/pack_dynamic_icons.py [duong_dan_res_item]
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_ITEM = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/workspace/goals/game-s-ng-v-t-phong-c-ch-thu-n-vi-t/"
    "hidden_files/pack5/res_x/res/hd/item")
SHEET = 2048
PAD = 2


def main():
    items = json.load(open(f"{ROOT}/assets/data/items.json", encoding="utf-8"))

    # Không gian id ảnh của part động ĐỘC LẬP với imgdata: cùng một số
    # icon có thể tồn tại ở cả hai nơi với hai hình khác nhau (ví dụ 537:
    # trong imgdata là frame con chó của part tĩnh 605, còn ảnh động 537
    # là vải áo của "thuyền trưởng" 2169). Nên ảnh của part động (icon VÀ
    # từng frame trong animation) luôn lấy từ ảnh riêng nếu có file, bất
    # kể imgdata có trùng số hay không.
    need = {}

    def take(img_id):
        if img_id is None or img_id in need:
            return
        path = f"{RES_ITEM}/{img_id}.png"
        if os.path.exists(path):
            need[img_id] = path

    for it in items:
        if it["id"] < 2000:
            continue
        take(it.get("icon"))
        anim = it.get("animation")
        if isinstance(anim, str) and anim:
            try:
                for fr in json.loads(anim.replace('\\"', '"')):
                    take(fr.get("img"))
            except Exception:
                pass
    print(f"anh dong can dong goi: {len(need)}")

    # shelf packing: xếp theo chiều cao giảm dần cho gọn
    entries = []
    for icon, path in need.items():
        with Image.open(path) as im:
            entries.append((icon, path, im.size[0], im.size[1]))
    entries.sort(key=lambda e: -e[3])

    os.makedirs(f"{ROOT}/assets/iconatlas", exist_ok=True)
    rows = []
    sheet_idx = -1
    sheet = None
    x = y = shelf_h = 0
    for icon, path, w, h in entries:
        if w > SHEET or h > SHEET:
            print(f"  bỏ qua icon {icon}: {w}x{h} lớn hơn sheet")
            continue
        if sheet is None or x + w > SHEET:
            x = 0
            y += shelf_h + PAD
            shelf_h = 0
            if sheet is None or y + h > SHEET:
                if sheet is not None:
                    sheet.save(f"{ROOT}/assets/iconatlas/{sheet_idx}.png")
                sheet_idx += 1
                sheet = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
                x = y = shelf_h = 0
        with Image.open(path) as im:
            sheet.paste(im.convert("RGBA"), (x, y))
        rows.append({"item_id": icon, "image_id": sheet_idx,
                     "x": x, "y": y, "w": w, "h": h})
        x += w + PAD
        shelf_h = max(shelf_h, h)
    if sheet is not None:
        sheet.save(f"{ROOT}/assets/iconatlas/{sheet_idx}.png")

    rows.sort(key=lambda r: r["item_id"])
    with open(f"{ROOT}/assets/data/icondata.json", "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, separators=(",", ":"))
    print(f"đã tạo {sheet_idx + 1} sheet atlas + icondata.json ({len(rows)} icon)")


if __name__ == "__main__":
    main()
