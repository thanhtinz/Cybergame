#!/usr/bin/env python3
"""Phoi thu nhan vat phong cach moi trong khong gian hero:
outfit (day cham dat, tam than) -> dau base (tai cho goc) -> toc
(neo theo tam mat do tu hero). Xuat sheet kiem chung tren nen troi/dat.
"""
import os
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.expanduser("~/workspace/avatardk-java")
NS = os.path.join(ROOT, "assets", "newstyle")

# canvas, face_cx, k toc, tam than, k outfit (nu rieng vi ti le chan dai hon)
G = {
    "m": dict(W=600, H=815, face=344, kh=600 / 369, body=305, ko=600 / 369, frac=344 / 600, dy=-28),
    "f": dict(W=608, H=784, face=350, kh=588 / 357, body=304, ko=1.15, frac=350 / 588, dy=-32),
}


def compose(gender, hair_id, outfit_id):
    c = G[gender]
    cv = Image.new("RGBA", (c["W"], c["H"]), (0, 0, 0, 0))
    out = Image.open(os.path.join(NS, f"outfit_{gender}", outfit_id + ".png")).convert("RGBA")
    out = out.resize((round(out.width * c["ko"]), round(out.height * c["ko"])), Image.LANCZOS)
    cv.alpha_composite(out, (round(c["body"] - out.width / 2), c["H"] - out.height))
    base = Image.open(os.path.join(NS, "base", f"base_{gender}.png")).convert("RGBA")
    cv.alpha_composite(base, (0, 0))
    hair = Image.open(os.path.join(NS, f"hair_{gender}", hair_id + ".png")).convert("RGBA")
    hair = hair.resize((round(hair.width * c["kh"]), round(hair.height * c["kh"])), Image.LANCZOS)
    cv.alpha_composite(hair, (round(c["face"] - c["frac"] * hair.width), c["dy"]))
    return cv


def cell(cv, w=270):
    sc = w / cv.width
    im = cv.resize((w, round(cv.height * sc)), Image.LANCZOS)
    bg = Image.new("RGBA", (w + 8, im.height + 8), (168, 205, 235, 255))
    d = ImageDraw.Draw(bg)
    d.ellipse([w * 0.18, im.height - 16, w * 0.82, im.height + 2], fill=(120, 170, 105, 255))
    bg.alpha_composite(im, (4, 0))
    return bg


def main():
    rows = [
        [compose("m", h, "outfit_m_01") for h in ["hair_m_01", "hair_m_02", "hair_m_05", "hair_m_08", "hair_m_10"]],
        [compose("m", "hair_m_01", o) for o in ["outfit_m_03", "outfit_m_06", "outfit_m_14", "outfit_m_20"]]
        + [Image.open(os.path.join(NS, "hero", "hero_m.png")).convert("RGBA")],
        [compose("f", h, "outfit_f_03") for h in ["hair_f_01", "hair_f_03", "hair_f_06", "hair_f_09", "hair_f_10"]],
        [compose("f", "hair_f_06", o) for o in ["outfit_f_01", "outfit_f_06", "outfit_f_10", "outfit_f_15"]]
        + [Image.open(os.path.join(NS, "hero", "hero_f.png")).convert("RGBA")],
    ]
    cw = max(cell(r[0]).width for r in rows)
    chh = max(cell(r[0]).height for r in rows)
    sheet = Image.new("RGB", (5 * cw, 4 * chh), (230, 230, 230))
    for ri, row in enumerate(rows):
        for ci, cv in enumerate(row):
            sheet.paste(cell(cv), (ci * cw, ri * chh))
    sheet.save("/tmp/newstyle-compose-2219.png")
    print("saved", sheet.size)


if __name__ == "__main__":
    main()
