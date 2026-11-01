#!/usr/bin/env python3
"""Đưa asset phong cách mới về ĐÚNG NGÔN NGỮ PIXEL của game (asset cũ):
posterize phẳng tông (palette không dither) + hạ cỡ box-average trên RGBA
premultiply về lưới pixel thấp + alpha nhị phân + texture vẽ 2:1 Nearest.

Đầu ra: assets/newstyle-crisp/ — mọi lớp ĐÃ ở cỡ phối cuối cùng trong
không gian canvas crisp (cao 152px = 2x cỡ vẽ 76px trong game), để
NewStyle.java chỉ cần BLIT 1:1, không nội suy thêm lần nào.

Hiệu chuẩn crisp ghi vào assets/data/newstyle.json (maleCrisp/femaleCrisp).
Chạy: python3 tools/crisp_newstyle.py
"""
import json
import pathlib

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'assets/newstyle'
DST = ROOT / 'assets/newstyle-crisp'
TARGET_H = 152          # chiều cao canvas crisp (game vẽ 76 -> tỉ lệ 2:1)
PALETTE = 56            # số màu tối đa mỗi món — ép tông phẳng kiểu cel


def crisp(img: Image.Image, out_w: int, out_h: int) -> Image.Image:
    """Hạ cỡ box-average premultiply -> snap alpha -> lượng hoá palette."""
    a = np.asarray(img, dtype=np.float32)
    alpha = a[..., 3:4] / 255.0
    premul = np.concatenate([a[..., :3] * alpha, a[..., 3:4]], axis=2)
    pm = Image.fromarray(np.clip(premul, 0, 255).astype(np.uint8), 'RGBA')
    pm = pm.resize((out_w, out_h), Image.BOX)
    b = np.asarray(pm, dtype=np.float32)
    al = b[..., 3:4]
    rgb = np.where(al > 0, b[..., :3] * 255.0 / np.maximum(al, 1e-6), 0)
    out_a = np.where(al[..., 0] >= 128, 255, 0).astype(np.uint8)
    out = np.dstack([np.clip(rgb, 0, 255).astype(np.uint8), out_a])
    im = Image.fromarray(out, 'RGBA')
    # Lượng hoá palette trên phần RGB (không dither) rồi gắn lại alpha
    q = im.convert('RGB').quantize(colors=PALETTE, method=Image.MEDIANCUT,
                                   dither=Image.Dither.NONE)
    res = q.convert('RGB')
    res.putalpha(Image.fromarray(out_a, 'L'))
    return res


def main():
    cat = json.load(open(ROOT / 'assets/data/newstyle.json', encoding='utf-8'))
    crisp_calib = {}
    for g, gkey in (('m', 'male'), ('f', 'female')):
        cal = cat[gkey]
        cw, ch = cal['canvas']
        s = TARGET_H / ch
        ccw, cch = round(cw * s), TARGET_H
        crisp_calib[gkey + 'Crisp'] = {
            'canvas': [ccw, cch],
            'faceCx': round(cal['faceCx'] * s),
            'bodyCx': round(cal['bodyCx'] * s),
            'hairDy': round(cal['hairDy'] * s),
            'hairFrac': cal['hairFrac'],
        }

        def save(img, sub, name, ow, oh):
            d = DST / sub
            d.mkdir(parents=True, exist_ok=True)
            crisp(img, max(1, ow), max(1, oh)).save(d / name)

        hero = Image.open(SRC / f'hero/hero_{g}.png')
        save(hero, 'hero', f'hero_{g}.png', ccw, cch)
        base = Image.open(SRC / f'base/base_{g}.png')
        save(base, 'base', f'base_{g}.png', ccw, cch)
        # GHÉP LẠI NÉT MẶT từ hero crisp: mắt/miệng của base quá nhỏ, box-average
        # nuốt mất lòng trắng + miệng; hero cùng canvas giữ được nét (đậm tương
        # phản) nên cấy đúng tọa độ (đã nhân s) sang base crisp.
        feats = {  # (x0, y0, x1, y1) không gian hero full-res: dải mắt + ô miệng
            'm': [(200, 380, 497, 470), (318, 464, 370, 492)],
            'f': [(215, 352, 487, 428), (324, 452, 376, 480)],
        }[g]
        hero_c = Image.open(DST / f'hero/hero_{g}.png')
        base_c = Image.open(DST / f'base/base_{g}.png')
        for (x0, y0, x1, y1) in feats:
            box = (round(x0 * s), round(y0 * s), round(x1 * s), round(y1 * s))
            patch = hero_c.crop(box)
            region = base_c.crop(box)
            # Khớp tông da: dịch RGB của patch theo hiệu trung vị da hai bên
            pa = np.asarray(patch, dtype=np.float32)
            ra = np.asarray(region, dtype=np.float32)

            def skin_med(a):
                m = (a[..., 3] > 200) & (a[..., 0] > 140) & (a[..., 0] - a[..., 2] > 15)
                return np.median(a[..., :3][m], axis=0) if m.sum() > 10 else None

            pm_, rm_ = skin_med(pa), skin_med(ra)
            if pm_ is not None and rm_ is not None:
                delta = np.clip(rm_ - pm_, -40, 40)
                # CHỈ dịch các pixel da sáng — pixel tối (mi, tròng, miệng) giữ
                # nguyên tương phản, nếu không mắt bị rửa thành nâu nhạt
                m = (pa[..., 3] > 200) & (pa[..., 0] > 140) & (pa[..., 0] - pa[..., 2] > 15)
                rgb = pa[..., :3].copy()
                rgb[m] = np.clip(rgb[m] + delta, 0, 255)
                pa[..., :3] = rgb
                patch = Image.fromarray(pa.astype(np.uint8), 'RGBA')
            # Mặt nạ lông vũ 2px để mép cấy tan vào da base
            w, h = box[2] - box[0], box[3] - box[1]
            mask = Image.new('L', (w, h), 0)
            from PIL import ImageDraw, ImageFilter
            ImageDraw.Draw(mask).rectangle([2, 2, w - 3, h - 3], fill=255)
            mask = mask.filter(ImageFilter.GaussianBlur(1.4))
            base_c.paste(patch, box[:2], mask)
        base_c.save(DST / f'base/base_{g}.png')
        for it in cat['hair_' + g]:
            im = Image.open(SRC / f"hair_{g}/{it['id']}.png")
            k = cal['kHair']
            save(im, f'hair_{g}', f"{it['id']}.png",
                 round(it['w'] * k * s), round(it['h'] * k * s))
        for it in cat['outfit_' + g]:
            im = Image.open(SRC / f"outfit_{g}/{it['id']}.png")
            k = cal['kOutfit']
            save(im, f'outfit_{g}', f"{it['id']}.png",
                 round(it['w'] * k * s), round(it['h'] * k * s))
        print(g, 'scale', round(s, 4), 'canvas', (ccw, cch),
              'crisp calib', crisp_calib[gkey + 'Crisp'])

    cat.update(crisp_calib)
    with open(ROOT / 'assets/data/newstyle.json', 'w', encoding='utf-8') as f:
        json.dump(cat, f, ensure_ascii=False, indent=1)
    print('newstyle-crisp done ->', DST)


if __name__ == '__main__':
    main()
