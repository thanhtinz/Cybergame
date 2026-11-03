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

Mảng TYPE (va chạm) — khóa "t", base64 sbyte row-major:
- type[] cấp phát toàn 0 (dòng 2146) NHƯNG với mọi map trừ client 20/108,
  đầu vòng quét có PRE-PASS gán type theo dải giá trị của map[j] (mặc định
  88; <7/44..55/114..123 -> 80; 20..23 -> 79; 67/85 -> 92; 62 -> 56 có
  điều kiện index), rồi setTypeMap/gán trực tiếp trong switch ghi đè theo
  từng case. Lưu trữ map[] là KHÔNG DẤU (dòng đọc đổi 255 -> -1 trước vòng
  lặp — bằng chứng trong mã nguồn; cast (byte) ở bản dịch ngược là giả).
- switch(index) client 20: vòng riêng (map<32 -> 80, còn lại 88, mã 65 ->
  10); client 108: type giữ toàn 0.
- Giả định riêng cho type (theo yêu cầu đợt này): FarmScr.idFarm = farm
  của NGƯỜI KHÁC, trừ map farm chính client 26 (của mình); TYPEMAP lúc
  nạp = -1 (ảnh hưởng case 145 -> type 9). numPig/numBer > 0 như phần "o".
"""
import json, base64, struct, sys
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

# ---------- Mảng TILE VẼ (sau setMap) — khóa "d" ----------
#
# Mô phỏng MỌI đột biến lên map[] trong vòng quét setMap (đường load()
# thường: newType=true, idTileImg=-1, TYPEMAP lúc nạp = -1 như phần type).
# Các helper gốc: setTypeMap(i,t,m) gán map[i]=m; setTypePark gán 43 ở
# hàng 0, 6 ở hàng khác; setMapPaint(i) sao ô kề (cột 0 lấy ô PHẢI — còn
# nguyên vì chưa quét tới; cột khác lấy ô TRÁI — đã là giá trị SAU đột
# biến). Quét row-major, đột biến tại chỗ nên mảng Python dùng chung một
# list là khớp: ô dưới (j+wMap) của case 128/141/143 chưa bị quét nên vẫn
# là giá trị GỐC, đúng ghi chú trong yêu cầu.
# Giả định bổ sung (ghi trong báo cáo docs/map-draw-extraction.md):
# - SessionHelper.isConnected() == True khi load() chạy (game gốc nạp map
#   sau đăng nhập) -> case 28 KHÔNG đổi map[j] (chỉ offline mới gán 4).
# - Cattle.numPig > 0, Dog.numBer > 0 (như phần "o"/"t"): case 155 kết
#   thúc ở setTypeMap(84, 112) -> tile vẽ 112.
# - Map client 20 dùng vòng riêng của switch(index); map 108 bỏ qua
#   (không thuộc danh sách tile, trả None).
# - Nếu case 128/141/143 (hoặc setMapPaint ở ô cuối) đọc ngoài mảng, C#
#   gốc văng ngoại lệ và TOÀN BỘ setMap dừng tại đó (try bao cả vòng lặp);
#   ở đây mô phỏng đúng: dừng quét, các ô còn lại giữ nguyên, ghi cảnh báo.

def simulate_draw(client_id, wmap, cells_unsigned):
    """Trả list tile vẽ (sbyte: -1 hoặc 0..254) row-major, hoặc None cho
    map 108. cells_unsigned: list byte 0..255 như trong khóa 'g'."""
    srv = client_id - 1
    m = [-1 if c == 255 else c for c in cells_unsigned]
    n = len(m)
    if client_id == 108:
        return None
    if client_id == 20:
        # Vòng riêng map cưới (LoadMap.cs:2161): chỉ tile 65 đổi —
        # ô 65 đầu tiên -> 1, ô thứ hai -> 16, từ ô thứ ba -> 1.
        b42 = 0
        for k in range(n):
            if m[k] == 65:
                m[k] = 1
                if b42 == 1:
                    m[k] = 16
                b42 += 1
        return m
    b18 = 0  # case 191: tile xen kẽ 46/44 theo thứ tự gặp
    for j in range(n):
        v = m[j]  # tại thời điểm quét, m[j] vẫn là giá trị gốc (mọi đột
        # biến chỉ ghi vào chính ô đang quét hoặc đọc ô kề không ghi)
        if v == 127:
            m[j] = 96  # setTypeMap(108, 96)
        elif v == 128:
            if srv != 25:
                if j + wmap >= n:
                    warnings.append(f"map {client_id}: case 128 tại j={j} đọc ngoài mảng (gốc dừng setMap)")
                    break
                m[j] = m[j + wmap]  # setTypeMap(55, 20) rồi ghi đè ô dưới
        elif v in (129, 160):
            m[j] = 96  # setTypeMap(57/62, 96)
        elif 130 <= v <= 138 or v == 153:
            m[j] = 43 if j // wmap == 0 else 6  # setTypePark
        elif v == 139:
            # setMapPaint: cột 0 sao ô phải (chưa quét), còn lại sao ô
            # trái (đã quét xong = giá trị vẽ cuối của ô đó)
            if j % wmap == 0:
                if j + 1 >= n:
                    warnings.append(f"map {client_id}: setMapPaint tại j={j} đọc ngoài mảng (gốc dừng setMap)")
                    break
                m[j] = m[j + 1]
            else:
                m[j] = m[j - 1]
        elif v == 140:
            m[j] = 55  # setTypeMap(25, 55)
        elif v == 141:
            if j + wmap >= n:
                warnings.append(f"map {client_id}: case 141 tại j={j} đọc ngoài mảng (gốc dừng setMap)")
                break
            m[j] = m[j + wmap]  # setTypeMap(24, 5) rồi ghi đè ô dưới
        elif v == 142:
            # setTypeMap(80, 4) trong C# gốc: tile vẽ ĐỔI THÀNH 4 (nền cỏ);
            # ô đất trồng thật do FarmScr vẽ theo posTree. Xem báo cáo —
            # điểm này khác câu chữ "giữ nguyên 142" trong yêu cầu.
            m[j] = 4
        elif v == 143:
            if j + wmap >= n:
                warnings.append(f"map {client_id}: case 143 tại j={j} đọc ngoài mảng (gốc dừng setMap)")
                break
            m[j] = m[j + wmap]  # setTypeMap(52, 51) rồi ghi đè ô dưới
        elif v == 144:
            m[j] = 5  # setTypeMap(53, 5)
        elif v == 145:
            # setTypeMap(..., -1) ở cả 3 nhánh (TYPEMAP=-1); riêng
            # index-1 == 100 (client 101) bị ghi đè 47 sau cùng
            m[j] = 47 if srv == 100 else -1
        elif v in (147, 161):
            m[j] = 96  # setTypeMap(58/63, 96)
        elif v in (148, 162):
            m[j] = 96  # map[j]=0 trước, rồi setTypeMap(64, 96)
        elif v == 149:
            m[j] = 4  # setTypeMap(28, 4)
        elif v == 150:
            m[j] = 4 if client_id == 26 else 0  # setTypeMap(93, ...)
        elif v == 151:
            m[j] = 0  # setTypeMap(78, 0)
        elif v == 152:
            m[j] = 55 if srv == 25 else 0  # setTypeMap(81, ...)
        elif v == 155:
            m[j] = 112  # setTypeMap(80, 55) rồi numPig>0: setTypeMap(84, 112)
        elif v == 156:
            m[j] = 5  # setTypeMap(80, 5); numBer>0 vẫn là 5
        elif v == 157:
            m[j] = 111  # setTypeMap(80, 111)
        elif v == 158:
            m[j] = 5  # setTypeMap(80, 5)
        elif v == 159:
            mm = 4
            if client_id in (109, 110):
                mm = 47
            elif client_id == 14:
                mm = 0
            m[j] = mm  # setTypeMap(89, mm); client 26 -> 4 (mặc định)
        elif v == 163:
            m[j] = -1  # setTypeMap(12, -1)
        elif v == 164:
            m[j] = 6  # setTypeMap(13, 6)
        elif v == 165:
            m[j] = 0  # setTypeMap(14, 0)
        elif v == 166:
            m[j] = 0  # setTypeMap(15, 0)
        elif v == 167:
            m[j] = 43  # setTypeMap(16, 43)
        elif v in (172, 173, 174):
            m[j] = 96  # setTypeMap(88, 96)
        elif v in (175, 176, 177):
            m[j] = 96  # setTypeMap(68/69/70, 96)
        elif v == 178:
            m[j] = 96  # setTypeMap(109, 96)
        elif v == 179:
            m[j] = 96  # setTypeMap(18, 96)
        elif v == 180:
            m[j] = 0 if srv == 101 else 77  # setTypeMap(17, 77); client 102 -> 0
        elif v == 181:
            if srv not in (100, 101, 103, 104):
                m[j] = 46  # setTypeMap(56, 46); các map offline giữ nguyên
        elif v == 182:
            m[j] = 39  # setTypeMap(80, 39)
        elif v == 183:
            m[j] = 13  # setTypeMap(88, 13)
        elif v == 185:
            m[j] = 43 if client_id == 18 else 47  # setTypeMap(71, ...)
        elif v == 186:
            m[j] = 17  # setTypeMap(94, 17)
        elif v == 187:
            m[j] = 4  # setTypeMap(95/80, 4)
        elif v == 188:
            m[j] = 4  # setTypeMap(96/80, 4)
        elif v == 189:
            m[j] = 4  # gán trực tiếp map[j] = 4
        elif v == 190:
            m[j] = 4  # gán trực tiếp map[j] = 4
        elif v == 191:
            if srv == 104:
                m[j] = 0
            else:
                m[j] = 46 if b18 % 2 == 0 else 44
            b18 += 1
        elif v in (192, 193, 194, 195, 196, 197, 198, 199):
            m[j] = 4  # type[j]=...; map[j]=4 trực tiếp
        elif v == 200:
            m[j] = 5
        elif v == 201:
            m[j] = 5
        elif v == 202:
            m[j] = 96  # setTypeMap(88, 96)
        elif v == 203:
            m[j] = 96  # setTypeMap(110, 96)
        elif v == 204:
            m[j] = 43  # gán trực tiếp map[j] = 43
        elif v == 98:
            if srv in (108, 109):  # map casino: map[j] = 56
                m[j] = 56
        # Các case còn lại không chạm map[j]: -1, 24, 25, 26, 27, 28 (đang
        # kết nối), 63, 65, 68, 69, 97, 102, 110, 184 (chỉ gán type),
        # và default.
    return m


# Các giá trị marker mà setMap CÓ THỂ đổi tile vẽ (dùng cho kiểm chứng b:
# ô ngoài tập này phải giữ nguyên giá trị). 98 chỉ đổi ở map casino
# (server 108/109 — không có trong danh sách map), 181 chỉ đổi ngoài các
# map offline; cả hai vẫn liệt kê ở đây và kiểm chứng chặt hơn nằm ở (a).
DRAW_MUTATING = {
    98, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139,
    140, 141, 142, 143, 144, 145, 147, 148, 149, 150, 151, 152, 153, 155,
    156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 172, 173,
    174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 185, 186, 187, 188,
    189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202,
    203, 204,
}

MAP_DIR = ("/home/hatch/workspace/goals/game-s-ng-v-t-phong-c-ch-thu-n-vi-t"
           "/hidden_files/pack5/archive_x/project-client-avt-main"
           "/Assets/Resources/map")
NEW_MAP_FILES = {67: "67.txt", 68: "68.txt", 106: "106.bytes"}
NEW_MAP_HMAP = 8  # mặc định LoadMap; server 66/67/105 không có case riêng


def build_new_entry(client_id):
    """Sinh entry mapgrids cho map chưa có: đọc file Resources/map, Hmap=8,
    wMap = size // Hmap (đúng công thức gốc available()/Hmap), chỉ lấy
    wMap*Hmap byte đầu. Sinh đủ g/w/s/o/t/d bằng các hàm mô phỏng chung."""
    with open(f"{MAP_DIR}/{NEW_MAP_FILES[client_id]}", "rb") as f:
        raw = f.read()
    wmap = len(raw) // NEW_MAP_HMAP
    cells = list(raw[: wmap * NEW_MAP_HMAP])
    entry = {"w": wmap, "g": base64.b64encode(bytes(cells)).decode()}
    stops = []
    for j, v in enumerate(cells):
        if v == 139:
            stops += [j % wmap, j // wmap]
    if stops:
        entry["s"] = stops
    entry["o"] = simulate(client_id, wmap, cells)
    types = simulate_types(client_id, wmap, cells)
    entry["t"] = base64.b64encode(
        struct.pack("<%db" % len(types), *types)).decode()
    return entry


# ---------- Mảng type (va chạm) ----------

# Tập type ĐI ĐƯỢC trích từ LoadMap.doJoin (dòng 1288): mọi case của
# switch đều kết thúc ở "return true" (case 54 trả kết quả doSat — vẫn
# tính là bước vào được, xem báo cáo), cộng type 112 và dải portal
# -125..-1 của nhánh default. Ngoài tập này (và -2 = ngoài map) -> false.
_DOJOIN_CASES = {
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
    20, 21, 23, 24, 25, 27, 28, 29, 52, 53, 54, 55, 56, 57, 58, 59, 62, 63,
    64, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 83, 84, 85, 86, 87, 89,
    93, 94, 95, 96, 97, 98, 100, 101, 103, 104, 107, 108, 109, 110, 111,
}
WALKABLE = set(range(-125, 0)) | _DOJOIN_CASES | {112}

TYPEMAP_AT_LOAD = -1  # giả định: nạp từ trạng thái chưa ở map nào


def _prepass(v, srv):
    """Pre-pass đầu vòng quét (nhánh default của switch(index)): gán type
    theo dải giá trị map[j]. v là mã KHÔNG DẤU (255 đã đổi thành -1)."""
    if v == -1 or 120 <= v <= 123 or 114 <= v <= 119 or v < 7:
        t = 80
    elif v == 67 or v == 85:
        t = 92
    elif 20 <= v <= 23:
        t = 79
    else:
        t = 88
    if 44 <= v <= 55:
        t = 80
    if v == 62 and srv not in (100, 101, 103, 104) and srv != 62:
        t = 56
    if v == 111 or v == 112:
        t = 80
    return t


def simulate_types(client_id, wmap, cells_unsigned):
    """Trả list type (sbyte) row-major, đúng thứ tự gán của setMap."""
    srv = client_id - 1
    m = [-1 if c == 255 else c for c in cells_unsigned]
    if client_id == 108:
        return [0] * len(m)
    if client_id == 20:
        out = []
        for v in m:
            t = 80 if v < 32 else 88
            if v == 65:
                t = 10
            out.append(t)
        return out
    own_farm = client_id == 26  # map farm chính = farm của mình
    b30 = 0  # chỉ tăng ở case 69 (map casino server 108/109)
    b38 = 0  # chỉ tăng ở case 68 (map casino server 108/109)
    out = []
    for v in m:
        t = _prepass(v, srv)
        if v == -1:
            t = 88
        elif v == 127:
            t = 108
        elif v == 128:
            if srv != 25:
                t = 55
        elif v in (129, 160):
            t = 57 if v == 129 else 62
        elif 130 <= v <= 138:
            t = v - 130  # setTypePark
        elif v == 153:
            t = 11  # setTypePark
        elif v == 139:
            t = -1  # gán trực tiếp: ô điểm dừng xe buýt
        elif v == 140:
            t = 25
        elif v == 141:
            t = 24
        elif v == 142:
            t = 80
        elif v == 143:
            t = 52
        elif v == 144:
            t = 53
        elif v == 145:
            if srv == 109 or (srv == 57 and TYPEMAP_AT_LOAD == 17):
                t = 17
            elif TYPEMAP_AT_LOAD == 23:
                t = 23
            else:
                t = 9
        elif v in (147, 161):
            t = 58 if v == 147 else 63
        elif v in (148, 162):
            t = 64  # map[j] bị gán 0 TRƯỚC setTypeMap -> luôn nhánh 64
        elif v == 149:
            t = 28
        elif v == 150:
            t = 93
        elif v == 151:
            t = 78
        elif v == 152:
            t = 81
        elif v == 155:
            t = 84  # setTypeMap(80) rồi bị ghi đè 84 khi numPig > 0
        elif v == 156:
            t = 85  # setTypeMap(80) rồi bị ghi đè 85 khi numBer > 0
        elif v == 157:
            t = 80
        elif v == 158:
            t = 80
        elif v == 159:
            t = 89
        elif v == 163:
            t = 12
        elif v == 164:
            t = 13
        elif v == 165:
            t = 14
        elif v == 166:
            t = 15
        elif v == 167:
            t = 16
        elif v in (172, 173, 174):
            t = 88
        elif v == 175:
            t = 68
        elif v == 176:
            t = 69
        elif v == 177:
            t = 70
        elif v == 178:
            t = 109
        elif v == 179:
            t = 18
        elif v == 180:
            t = 17
        elif v == 181:
            if srv not in (100, 101, 103, 104):
                t = 56
        elif v == 182:
            t = 80
        elif v == 183:
            t = 88
        elif v == 184:
            t = 72 + b30  # case này KHÔNG tăng b30 (chỉ case 69 mới tăng)
        elif v == 185:
            t = 71
        elif v == 186:
            t = 94
        elif v == 187:
            t = 95 if own_farm else 80
        elif v == 188:
            t = 96 if own_farm else 80
        elif v == 189:
            t = 97
        elif v == 190:
            t = 98 if own_farm else 88  # farm người khác: bị ghi đè 88
        elif v == 191:
            t = 23
        elif v == 192:
            t = 99
        elif v == 193:
            t = 100
        elif v == 194:
            t = 106
        elif v == 195:
            t = 102
        elif v == 196:
            t = 103
        elif v == 197:
            t = 104
        elif v == 198:
            t = 105
        elif v == 199:
            t = 101
        elif v == 200:
            t = 107
        elif v == 201:
            t = 19
        elif v == 202:
            t = 88
        elif v == 203:
            t = 110
        elif v == 204:
            t = 10
        elif v in (63, 65):
            if srv not in (100, 101, 103, 104):
                t = 27
        elif v == 97:
            t = 54
        elif v == 98:
            t = 29
        elif v == 102:
            t = 92
        elif v == 68:
            if srv in (108, 109):  # isCasino = false khi quét (load() reset)
                t = 72 + b38
                b38 += 1
        elif v == 69:
            if srv in (108, 109):
                t = 72 + b30
                b30 += 1
        # 24/25/26, 27, 28, 110 và default: switch không gán type -> pre-pass
        out.append(t)
    return out


def main():
    with open(PATH, encoding="utf-8") as f:
        data = json.load(f)
    # Bổ sung 3 map còn thiếu trong Pack5 (client 67, 68, 106) — entry
    # sinh mới hoàn toàn bằng cùng các hàm mô phỏng.
    for cid in sorted(NEW_MAP_FILES):
        if str(cid) not in data:
            data[str(cid)] = build_new_entry(cid)
            print(f"Đã thêm map mới {cid} (w={data[str(cid)]['w']})")
    # Giữ thứ tự khóa như file hiện tại: sắp xếp theo CHUỖI ("1","10","101"...)
    data = {k: data[k] for k in sorted(data)}
    total = 0
    per_map = {}
    id_counter = Counter()
    per_types = {}
    blocked = {}
    per_draw = {}
    draw_changed = {}
    for key in sorted(data, key=int):
        g = data[key]
        cells = list(base64.b64decode(g["g"]))
        objs = simulate(int(key), g["w"], cells)
        g["o"] = objs
        per_map[int(key)] = objs
        total += len(objs)
        for o in objs:
            id_counter[o[0]] += 1
        types = simulate_types(int(key), g["w"], cells)
        per_types[int(key)] = (types, g["w"])
        g["t"] = base64.b64encode(struct.pack("<%db" % len(types), *types)).decode()
        blocked[int(key)] = sum(1 for t in types if t not in WALKABLE)
        draw = simulate_draw(int(key), g["w"], cells)
        per_draw[int(key)] = (draw, cells, g["w"])
        if draw is not None:  # map 108: không có khóa "d"
            # Đóng gói byte thô (v & 0xFF): trùng khớp sbyte với mọi giá
            # trị trong [-1,125]; riêng 3 ô 128 của map 26 (xem kiểm chứng
            # a) giữ byte 128 như map[] short của gốc.
            g["d"] = base64.b64encode(
                bytes(v & 0xFF for v in draw)).decode()
            orig = [-1 if c == 255 else c for c in cells]
            draw_changed[int(key)] = sum(
                1 for a, b in zip(orig, draw) if a != b)
    with open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"Đã ghi 'o' + 't' cho {len(data)} map. Tổng vật thể: {total}")
    print("Ô BỊ CHẶN (type không đi được) từng map:",
          {k: blocked[k] for k in sorted(blocked)})
    print(f"Tổng ô bị chặn: {sum(blocked.values())}")

    def cell(k, x, y):
        types, w = per_types[k]
        return types[y * w + x]

    def grid_str(k):
        types, w = per_types[k]
        return "\n".join(
            " ".join(f"{types[y * w + x]:>4}" for x in range(w))
            for y in range(len(types) // w))

    print("\n== TYPE MAP 24 ==\n" + grid_str(24))
    print("\n== TYPE MAP 12 ==\n" + grid_str(12))
    assert cell(24, 2, 1) == 58, "map24 (2,1) mã 147 -> type 58"
    assert cell(24, 24, 5) == 81, "map24 (24,5) mã 152 -> type 81"
    assert cell(24, 24, 7) == -1, "map24 (24,7) mã 139 -> type -1 (gán trực tiếp)"
    assert cell(24, 13, 1) == 55, "map24 (13,1) mã 128 -> type 55"
    assert cell(12, 1, 2) == 88, "map12 (1,2) mã 24 -> type 88 (pre-pass)"
    assert cell(12, 19, 5) == 81, "map12 (19,5) mã 152 -> type 81"
    print("\nKIỂM CHỨNG TYPE: map24 (2,1)=58, (24,5)=81, (24,7)=-1, (13,1)=55; "
          "map12 (1,2)=88, (19,5)=81 — ĐÚNG")
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

    # ===== Kiểm chứng mảng TILE VẼ "d" =====
    print("\n== THỐNG KÊ Ô ĐỔI TILE VẼ theo map ==")
    print({k: draw_changed[k] for k in sorted(draw_changed)})
    print(f"Tổng ô đổi tile: {sum(draw_changed.values())}")
    for k in sorted(per_draw):
        draw, cells, w = per_draw[k]
        if draw is None:
            assert k == 108 and "d" not in data[str(k)]
            continue
        orig = [-1 if c == 255 else c for c in cells]
        # (a) mọi giá trị vẽ phải thuộc {-1} u [0,125] — sót case đột biến
        # nào là còn marker > 125 lọt qua. Ngoại lệ DUY NHẤT đã đối chiếu
        # C#: map 26 (server 25) có 3 ô 128 — case 128 trong gốc bị chính
        # điều kiện `index - 1 != 25` vô hiệu trên map này nên tile giữ
        # nguyên 128 (không phải sót case).
        bad = sorted({v for v in draw if not (v == -1 or 0 <= v <= 125)})
        if bad:
            assert k == 26 and bad == [128] and draw.count(128) == 3, \
                f"map {k}: giá trị vẽ ngoài [-1,125]: {bad}"
            print("KIỂM CHỨNG d (a): ngoại lệ map 26 — 3 ô 128 giữ nguyên "
                  "vì case 128 bị vô hiệu trên server map 25 (đúng C# gốc)")
        # (b) ô không phải marker có thể đổi tile thì giữ nguyên giá trị
        mut = {65} if k == 20 else DRAW_MUTATING
        for j, (o0, d0) in enumerate(zip(orig, draw)):
            if o0 not in mut:
                assert d0 == o0, f"map {k} ô {j}: {o0} -> {d0} (không phải marker đổi tile)"
    print("KIỂM CHỨNG d (a): mọi giá trị thuộc {-1} U [0,125] — ĐÚNG")
    print("KIỂM CHỨNG d (b): ô không phải marker đổi tile giữ nguyên — ĐÚNG")
    # (d) kiểm chứng tay
    d24, c24, w24 = per_draw[24]
    j147 = c24.index(147)
    assert d24[j147] == 96, f"map24 ô 147 đầu tiên -> {d24[j147]}, phải là 96"
    j139 = c24.index(139)
    expect139 = d24[j139 - 1] if j139 % w24 != 0 else \
        (-1 if c24[j139 + 1] == 255 else c24[j139 + 1])
    assert d24[j139] == expect139, \
        f"map24 ô 139 đầu tiên -> {d24[j139]}, setMapPaint kỳ vọng {expect139}"
    print(f"KIỂM CHỨNG d (d): map24 ô 147 -> 96; ô 139 -> {expect139} "
          f"(sao ô kề theo setMapPaint) — ĐÚNG")
    d25, c25, w25 = per_draw[25]
    j189 = c25.index(189)
    assert d25[j189] == 4, f"map25 ô 189 -> {d25[j189]}, phải là 4"
    j142 = [j for j, v in enumerate(c25) if v == 142]
    assert all(d25[j] == 4 for j in j142), \
        f"map25 các ô 142 -> {[d25[j] for j in j142]}, C# gốc cho 4"
    print("KIỂM CHỨNG d (d): map25 ô 189 -> 4; các ô 142 -> 4 — ĐÚNG theo "
          "C# gốc (setTypeMap(80, 4)); LƯU Ý: khác câu chữ 'giữ nguyên 142' "
          "trong yêu cầu — xem docs/map-draw-extraction.md")
    for wmsg in warnings:
        print("CẢNH BÁO:", wmsg)

if __name__ == "__main__":
    main()
