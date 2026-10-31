#!/usr/bin/env python3
"""
Mô phỏng vòng quét LoadMap.setMap (bản client Unity C# của Pack5) để trích
lớp vật thể gắn map (addObjTree) cho 40 lưới trong mapgrids.json.

Pháp lý mô phỏng (đối chiếu LoadMap.cs):
- index truyền vào setMap = map id CLIENT (MapScr: load(roomID1 + 1, true);
  TYPEMAP = index - 1 = id server). newType = true (đường load() thường).
- Quét row-major j = 0..Hmap*wMap-1 trên mảng map[]; giá trị ô là SBYTE
  (byte 128..255 -> -128..-1; 255 -> -1 như code gốc).
- Bộ đếm b..b42 là biến cục bộ của setMap -> reset mỗi map.
- case default của switch: b37 = 0; b29 = 0 (chỉ b37 ảnh hưởng vật thể).
- addObjTree(type, j, idx) [tile-overload]: neo = (x(j) + getWTileImg(j),
  y(j) + w - 4); getWTileImg = (độ dài dải phẳng cùng mã tính từ j) * w / 2,
  w = 24. Dải tính trên mảng GỐC: mọi case chỉ sửa map[j] của chính ô đang
  quét (đã qua) nên dải hướng về phía trước luôn là dữ liệu gốc.
- Giả định ngoài lưới (ghi rõ trong báo cáo): Cattle.numPig > 0,
  Dog.numBer > 0, FarmScr.idFarm == avatar.IDDB (nông trại của mình),
  idTileImg == -1 (mọi addObjTree đều bị chặn nếu idTileImg != -1).
"""
import json, base64, sys
from collections import Counter

W = 24
PATH = "/home/hatch/workspace/avatardk-java/assets/data/mapgrids.json"

SWITCH_VALUES = {
    255, 24, 25, 26, 27, 28, 63, 65, 68, 69, 97, 98, 102, 110,
    127, 128, 129, 160, 130, 131, 132, 133, 134, 135, 136, 137, 138, 153,
    139, 140, 141, 142, 143, 144, 145, 147, 161, 148, 162, 149, 150, 151,
    152, 155, 156, 157, 158, 159, 163, 164, 165, 166, 167, 172, 173, 174,
    175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188,
    189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202,
    203, 204,
}

warnings = []

def get_w_tile(m, i, ctx):
    """Đúng getWTileImg: quét phẳng từ i, gặp ô khác mã đầu tiên ở vị trí j
    thì trả (j-i+1)*W/2. Nếu dải chạm cuối mảng, C# gốc đọc m[len] -> văng
    ngoại lệ và setMap dừng sớm; ở đây ghi cảnh báo và trả độ dài còn lại."""
    j = i
    while True:
        if j + 1 >= len(m):
            warnings.append(f"{ctx}: getWTileImg chạm cuối mảng tại j={j} (gốc sẽ văng ngoại lệ)")
            return (j - i + 1) * W // 2
        if m[j] != m[j + 1]:
            return (j - i + 1) * W // 2
        j += 1

def simulate(client_id, wmap, cells_unsigned):
    """cells_unsigned: list byte 0..255. Trả list [id, anchorX, anchorY] medium."""
    srv = client_id - 1
    if client_id in (20, 108):
        return []  # switch(index) ngoài thay toàn bộ vòng quét, không có ImageObj
    # Dispatch switch theo giá trị KHÔNG DẤU của byte (mã 128..255 trong file
    # là sbyte âm trong C#; case -1 của switch tương ứng byte 255). So sánh
    # bằng nhau trong getWTileImg không phụ thuộc biểu diễn dấu.
    m = list(cells_unsigned)
    objs = []
    b = dict.fromkeys(
        ["b2", "b34", "b6", "b7", "b33", "b35", "b9", "b11", "b17", "b37",
         "b40", "b10", "b25", "b13", "b39"], 0)

    def tile_obj(img, j):
        x = (j % wmap) * W + get_w_tile(m, j, f"map {client_id}")
        y = (j // wmap) * W + W - 4
        objs.append([img, x, y])

    for j, v in enumerate(m):
        x = (j % wmap) * W
        y = (j // wmap) * W
        if v in (24, 25, 26):
            if srv != 9 or (srv == 9 and (j // wmap) > wmap // 2):
                objs.append([845, x + W // 2, y + W])
        elif v == 27:
            objs.append([844, x + 11, y + 1])
        elif v == 127:
            if b["b34"] == 0:
                if srv != 9:
                    objs.append([830, x + 36, y + W - 2])
                b["b34"] += 1
        elif v in (129, 160):
            if b["b2"] == 0:
                if client_id == 18:
                    tile_obj(836, j)
                elif client_id == 24:
                    pass  # switch(index) case 24: break — không đặt vật thể
                else:
                    tile_obj(829, j)
                b["b2"] += 1
        elif v in (147, 161):
            if b["b6"] == 0:
                if srv != 23:
                    tile_obj(832, j)
                b["b6"] += 1
        elif v in (148, 162):
            if b["b7"] == 0:
                if srv != 23:
                    objs.append([833, x + 48, y + W - 2])
                b["b7"] += 1
        elif v == 150:
            if b["b33"] == 0:
                tile_obj(842, j)
            b["b33"] += 1
        elif v == 151:
            if b["b35"] == 0:
                tile_obj(843, j)
            b["b35"] += 1
        elif v == 152:
            if b["b9"] == 0:
                tile_obj(835, j)
            b["b9"] += 1
        elif v == 155:  # Cattle.numPig > 0 (giả định có lợn)
            objs.append([-5, x + W // 2, y + W // 2])
        elif v == 156:  # Dog.numBer > 0 (giả định có chó)
            objs.append([-6, x + W // 2, y + W // 2])
        elif v == 159:
            if b["b11"] == 0:
                objs.append([848, x + 12, y + 20])
            b["b11"] += 1
        elif v == 175 or v == 176 or v == 177:
            b["b17"] += 1
        elif v == 178:
            if b["b34"] == 0:
                objs.append([830, x + W, y + W - 2])
                b["b34"] += 1
        elif v == 179:
            if b["b17"] == 0:
                tile_obj(837, j)
                b["b17"] += 1
        elif v == 180:
            b["b17"] += 1
        elif v == 185:
            if b["b37"] == 1 and client_id == 18:
                objs.append([975, x + 24, y + 24])
            if client_id != 18:
                b["b37"] += 1  # setPopup(j, b37++, 0) ở nhánh else
            b["b37"] += 1      # b37++ cuối case
        elif v == 187:  # idFarm == IDDB (giả định farm của mình), chỉ ô đầu
            if b["b39"] == 0:
                objs.append([-10, x + 20, y + 20])
            b["b39"] += 1
        elif v == 188:  # idFarm == IDDB, mọi ô
            objs.append([-10, x + 20, y + 20])
        elif v == 190:
            if b["b40"] == 0:
                objs.append([1029, x + 36, y + 20])
            b["b40"] += 1
        elif v == 198:
            objs.append([1036, x + 12, y + 20])
        elif v == 199:
            if b["b10"] == 1:
                objs.append([1031, x + 24, y + 24])
            b["b10"] += 1
        elif v == 200:
            if b["b25"] == 1:
                objs.append([-1, x + 24, y + 24])
            b["b25"] += 1
        elif v == 202:
            if b["b13"] % 4 == 0:
                objs.append([4, x + W * 2, y + W])
            b["b13"] += 1
        elif v == 98:
            tile_obj(846, j)
        elif v == 110:
            objs.append([847, x + 11, y])
        elif v in SWITCH_VALUES:
            pass  # các case còn lại: chỉ popup/type/vị trí, không sinh ImageObj
        else:
            b["b37"] = 0  # case default: b37 = 0; b29 = 0
    return objs

def main():
    with open(PATH, encoding="utf-8") as f:
        data = json.load(f)
    total = 0
    per_map = {}
    id_counter = Counter()
    for key in sorted(data, key=int):
        g = data[key]
        cells = list(base64.b64decode(g["g"]))
        objs = simulate(int(key), g["w"], cells)
        g["o"] = objs
        per_map[int(key)] = objs
        total += len(objs)
        for o in objs:
            id_counter[o[0]] += 1
    with open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"Đã ghi 'o' cho {len(data)} map. Tổng vật thể: {total}")
    print("Theo id:", dict(sorted(id_counter.items())))
    print("Số vật thể từng map:", {k: len(v) for k, v in sorted(per_map.items())})
    print("\n== MAP 24 ==")
    for o in per_map[24]:
        print(o)
    print("\n== MAP 10 ==")
    for o in per_map[10]:
        print(o)
    assert [835, 636, 140] in per_map[24], "SAI: map 24 phải có 835 neo (636,140)"
    assert sum(1 for o in per_map[24] if o[0] == 835) == 1, "SAI: map 24 phải có đúng 1 vật thể 835"
    print("\nKIỂM CHỨNG map 24: 835 neo (636,140) — ĐÚNG")
    for wmsg in warnings:
        print("CẢNH BÁO:", wmsg)

if __name__ == "__main__":
    main()
