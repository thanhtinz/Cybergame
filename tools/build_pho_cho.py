#!/usr/bin/env python3
"""Dựng map MỚI 109 "Phố Chợ" (không có trong Pack5 — map tự chế theo ảnh
user gửi IMG_8025, mockup đã duyệt 2026-10-31): phố tường graffiti, vỉa hè
trống, đường nhựa + xe buýt chạy qua (ambient trong WorldScreen).

Mọi linh kiện cắt từ chính ảnh mẫu (art gốc của game); vỉa hè tổng hợp từ
màu mẫu của ảnh. Đầu ra:
- assets/imagemap/109/{0,1,2}.png — 3 dải 704x800 (chồng mép 2px như gốc,
  tổng rộng 2108 hd = lưới 44 ô) + bản .b64 trong assets-src.
- maps.json thêm map 109; mapgrids.json thêm lưới (đi được hàng 1-7,
  chặn chân đèn/biển/thùng/trụ nước ở hàng 5); citymap.bin thêm ô vùng 69
  tại (38,15) — cặp với REGION_NAMES/REGION_DEST trong WorldScreen.
Chạy: python3 tools/build_pho_cho.py
"""
import base64
import json
import pathlib
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

ROOT = pathlib.Path(__file__).resolve().parent.parent
REF = Image.open(ROOT.parent / 'user/media_library/image/IMG_8025.jpeg').convert('RGB')
W, H = 2108, 800
K = 3.0  # tỉ lệ phóng linh kiện từ ảnh mẫu


def cut_prop(box, tol=26):
    crop = REF.crop(box).convert('RGB')
    a = np.asarray(crop, dtype=np.float32)
    corners = np.concatenate([a[:4, :4].reshape(-1, 3), a[:4, -4:].reshape(-1, 3),
                              a[-4:, :4].reshape(-1, 3), a[-4:, -4:].reshape(-1, 3)])
    bg = np.median(corners, axis=0)
    bgmask = np.abs(a - bg).max(axis=2) < tol
    lab, n = ndimage.label(bgmask)
    border = set(np.unique(np.concatenate([lab[0, :], lab[-1, :], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    lab2, n2 = ndimage.label(fg)
    if n2:
        sizes = ndimage.sum(fg, lab2, range(1, n2 + 1))
        keep = {i + 1 for i, s in enumerate(sizes) if s >= 0.015 * fg.size}
        fg = np.isin(lab2, list(keep))
    pockets = bgmask & fg
    lab3, n3 = ndimage.label(pockets)
    if n3:
        sizes3 = ndimage.sum(pockets, lab3, range(1, n3 + 1))
        drop = {i + 1 for i, s in enumerate(sizes3) if s < 1500}
        fg[np.isin(lab3, list(drop))] = False
    return Image.fromarray(np.dstack([np.asarray(crop), (fg * 255).astype(np.uint8)]), 'RGBA')


def patch(box):
    return REF.crop(box).convert('RGB')


def tile_x(img, width):
    out = Image.new('RGB', (width, img.height))
    x, flip = 0, False
    while x < width:
        out.paste(img.transpose(Image.FLIP_LEFT_RIGHT) if flip else img, (x, 0))
        x += img.width
        flip = not flip
    return out


scene = Image.new('RGB', (W, H))

# --- trời: mảng phẳng theo màu đỉnh dải skyline + dải skyline/cây ---
band = patch((0, 0, 696, 72)).resize((W, 218), Image.LANCZOS)
top_col = tuple(np.median(np.asarray(band)[:4].reshape(-1, 3), axis=0).astype(int))
scene.paste(Image.new('RGB', (W, 82), top_col), (0, 0))
scene.paste(band, (0, 82))

# --- tường (300..378) + decal graffiti ---
wall = tile_x(patch((505, 70, 596, 118)).resize((276, 78), Image.LANCZOS), W)
scene.paste(wall, (0, 300))
for img, w2, h2, dx, dy in ((patch((136, 74, 199, 118)), 95, 72, 430, 303),
                            (patch((364, 74, 449, 118)), 128, 72, 1080, 303),
                            (patch((622, 84, 676, 116)), 81, 48, 1690, 318)):
    d = img.resize((w2, h2), Image.LANCZOS).convert('RGBA')
    m = Image.new('L', (w2, h2), 0)
    ImageDraw.Draw(m).rectangle([5, 5, w2 - 6, h2 - 6], fill=255)
    d.putalpha(m.filter(ImageFilter.GaussianBlur(4)))
    scene.paste(Image.alpha_composite(scene.crop((dx, dy, dx + w2, dy + h2)).convert('RGBA'), d).convert('RGB'), (dx, dy))

# --- vỉa hè tổng hợp (378..672) ---
pave = Image.new('RGB', (W, 294), (206, 204, 209))
pd = ImageDraw.Draw(pave)
rnd = random.Random(11)
T = 48
for gy in range(0, pave.height, T):
    for gx in range(0, pave.width, T):
        r = rnd.random()
        if r < 0.10:
            pd.rectangle([gx + 2, gy + 2, gx + T - 3, gy + T - 3], fill=(231, 226, 232))
        elif r < 0.18:
            pd.rectangle([gx + 2, gy + 2, gx + T - 3, gy + T - 3], fill=(196, 194, 200))
for gy in range(0, pave.height + T, T):
    pd.line([0, gy, W, gy], fill=(186, 188, 195), width=2)
for gx in range(0, pave.width + T, T):
    pd.line([gx, 0, gx, pave.height], fill=(186, 188, 195), width=2)
scene.paste(pave, (0, 378))

# --- lề (672..690) + đường (690..800) + vạch sang đường ---
scene.paste(tile_x(patch((90, 254, 180, 268)).resize((270, 18), Image.LANCZOS), W), (0, 672))
scene.paste(tile_x(patch((400, 268, 495, 312)).resize((285, 110), Image.LANCZOS), W), (0, 690))
cw = patch((193, 266, 247, 312)).resize((162, 110), Image.LANCZOS)
scene.paste(cw, (620, 690))
scene.paste(cw, (1560, 690))

scene = scene.convert('RGBA')


def shadow(cx, base_y, w, alpha=55):
    sh = Image.new('RGBA', (w, 18), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([2, 2, w - 2, 16], fill=(20, 24, 28, alpha))
    scene.alpha_composite(sh.filter(ImageFilter.GaussianBlur(4)), (cx - w // 2, base_y - 8))


def place(img, dx, dy, shadow_w=None):
    im = img.resize((round(img.width * K), round(img.height * K)), Image.LANCZOS)
    if shadow_w:
        shadow(dx + im.width // 2, dy + im.height, shadow_w)
    scene.alpha_composite(im, (dx, dy))


pole = cut_prop((50, 8, 68, 127), tol=38)
for dx in (170, 1000, 1900):
    place(pole, dx, 34)
binw = cut_prop((242, 88, 266, 121), tol=30)
place(binw, 760, 278)
place(binw, 1470, 278)
lamp = cut_prop((668, 193, 692, 264), tol=26)
place(lamp, 1130, 459, shadow_w=64)
place(lamp, 2015, 459, shadow_w=64)
place(cut_prop((23, 206, 52, 257), tol=22), 90, 519, shadow_w=70)
place(cut_prop((437, 238, 453, 262), tol=26), 1332, 594, shadow_w=52)
place(cut_prop((603, 236, 627, 264), tol=26), 1850, 588, shadow_w=60)

scene = scene.convert('RGB')
scene.save('/tmp/phocho-strip-full.png')

# --- cắt 3 dải (chồng mép 2px như luật nạp của gốc) + .b64 ---
out_dir = ROOT / 'assets/imagemap/109'
out_dir.mkdir(parents=True, exist_ok=True)
for i, x0 in enumerate((0, 702, 1404)):
    strip = scene.crop((x0, 0, x0 + 704, H))
    strip.save(out_dir / f'{i}.png')
    dst = ROOT / 'assets-src/assets/imagemap/109' / f'{i}.png.b64'
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(base64.b64encode((out_dir / f'{i}.png').read_bytes()).decode(), encoding='ascii')
print('strips done')

# --- maps.json ---
maps_path = ROOT / 'assets/data/maps.json'
maps = json.loads(maps_path.read_text(encoding='utf-8'))
if not any(m['id'] == 109 for m in maps):
    maps.append({'id': 109, 'name': 'Phố Chợ', 'bg': None, 'strips': 3,
                 'daydien': False, 'npcs': [], 'hmap': 8})
    maps_path.write_text(json.dumps(maps, ensure_ascii=False, indent=1), encoding='utf-8')
print('maps.json ok')

# --- mapgrids.json: hàng 1..7 đi được (80), hàng 0 chặn; chặn chân vật thể
# hàng 5 (strip y 656..704): biển KHU tx1-2, đèn tx23 & tx41, thùng nhỏ
# tx27, trụ nước tx38 ---
GW, GH = 44, 8
types = bytearray(GW * GH)
for ty in range(1, GH):
    for tx in range(GW):
        types[ty * GW + tx] = 80
for tx in (1, 2, 23, 27, 38, 41):
    types[5 * GW + tx] = 0
grids_path = ROOT / 'assets/data/mapgrids.json'
grids = json.loads(grids_path.read_text(encoding='utf-8'))
# Trạm xe như map gốc: dải ô 139 ở hàng 6 (mép đường) tx 14..18 -> sự kiện
# xe đến/xuống xe dùng chung cơ chế Bus của gốc; type ô 139 = -1 (đứng được).
cells = bytearray(GW * GH)
stop = []
for tx in range(14, 19):
    cells[6 * GW + tx] = 139
    types[6 * GW + tx] = 0xFF
    stop += [tx, 6]
grids['109'] = {
    'w': GW,
    'g': base64.b64encode(bytes(cells)).decode(),
    's': stop,
    'o': [],
    't': base64.b64encode(bytes(types)).decode(),
}
grids_path.write_text(json.dumps(grids, ensure_ascii=False, indent=1), encoding='utf-8')
print('mapgrids.json ok')

# --- citymap.bin: ô vùng (38,15) + .b64 ---
cm_path = ROOT / 'assets/data/citymap.bin'
data = bytearray(cm_path.read_bytes())
assert data[15 * 50 + 38] in (0, 69)
data[15 * 50 + 38] = 69
cm_path.write_bytes(bytes(data))
dst = ROOT / 'assets-src/assets/data/citymap.bin.b64'
dst.write_text(base64.b64encode(bytes(data)).decode(), encoding='ascii')
print('citymap.bin ok')
