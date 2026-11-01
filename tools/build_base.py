#!/usr/bin/env python3
"""Dung dau base (dau tron khong toc) cho he nhan vat phong cach moi — v3.

Cach lam sach va chac chan thay vi phau thuat xoa toc:
- Vom dau: ellipse dien da phang (tong lay tu ma hero) + vien da dam.
- Chi GHEP tu hero: dai mat (ca hai mat + mi + trang mat, cat chu nhat
  va lam mo ria 12px de hoa vao da vom), o mieng nho, doi tai (nam).
- Ma hong ve mem bang ellipse ban trong suot tai vi tri ma that.
Tam mat (face cx) do tu cum con nguoi cua hero: nam 344, nu 350 —
lech phai so voi tam canvas vi toc hero xoe lech trai.
"""
import os
import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.expanduser("~/workspace/avatardk-java")
NS = os.path.join(ROOT, "assets", "newstyle")

CFG = {
    # chin, face_cx, rx vom, dinh vom, dai mat (x0,y0,x1,y1), mieng (cx,cy), ma hong [(x,y)...], tai: True/False
    "m": dict(hero="hero_m", chin=498, cx=344, rx=184, top=76,
              eyes=(210, 378, 478, 456), mouth=(344, 478),
              blush=[(272, 464), (416, 464)], ears=True),
    "f": dict(hero="hero_f", chin=484, cx=350, rx=140, top=64,
              eyes=(219, 348, 478, 426), mouth=(350, 466),
              blush=[(276, 448), (424, 448)], ears=False),
}
OUTLINE = (176, 116, 76, 255)


def feather_alpha(w, h, f=12):
    """Mat na alpha: 255 o giua, mo dan ra ria trong f px."""
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    return np.clip(d / f, 0, 1)


def build(gender):
    cfg = CFG[gender]
    hero = Image.open(os.path.join(NS, "hero", cfg["hero"] + ".png")).convert("RGBA")
    a = np.asarray(hero)
    H, W = a.shape[:2]
    r = a[:, :, 0].astype(int); g = a[:, :, 1].astype(int); b = a[:, :, 2].astype(int)
    # tong da tu vung ma quanh tam mat
    skin = (a[:, :, 3] > 8) & (r > 198) & (g > 148) & (g < 232) & (b > 116) & (b < 210) & (r - b > 24)
    yy, xx = np.where(skin)
    sel = (np.abs(yy - (cfg["chin"] - 60)) < 50) & (np.abs(xx - cfg["cx"]) < 80)
    tone = np.median(a[yy[sel], xx[sel], :3], axis=0)
    print(gender, "skin tone:", tone)

    # vom
    cy = (cfg["top"] + cfg["chin"]) / 2
    ry = (cfg["chin"] - cfg["top"]) / 2 + 4
    Y, X = np.mgrid[0:H, 0:W]
    dome = (((X - cfg["cx"]) / cfg["rx"]) ** 2 + ((Y - cy) / ry) ** 2) <= 1.0
    dist = ndimage.distance_transform_edt(dome)
    out = np.zeros((H, W, 4), np.uint8)
    shade = (np.clip(dist / 30.0, 0, 1) * 0.13 + 0.87)
    for c in range(3):
        out[:, :, c][dome] = (tone[c] * shade[dome]).astype(np.uint8)
    out[:, :, 3][dome] = 255
    edge = dome & (dist < 5)
    out[edge] = OUTLINE

    def paste_patch(x0, y0, x1, y1):
        patch = a[y0:y1, x0:x1].astype(float)
        fa = feather_alpha(x1 - x0, y1 - y0)[:, :, None]
        base = out[y0:y1, x0:x1].astype(float)
        m = fa * (patch[:, :, 3:4] / 255.0)
        out[y0:y1, x0:x1] = (patch * m + base * (1 - m)).astype(np.uint8)

    # tai nam: hai cum da nam ngoai dai mat, ngang tam mat
    if cfg["ears"]:
        lab, n = ndimage.label(skin)
        eye_cy = (cfg["eyes"][1] + cfg["eyes"][3]) // 2
        for i, sl in enumerate(ndimage.find_objects(lab)):
            if sl is None:
                continue
            ar = int((lab[sl] == i + 1).sum())
            yc = (sl[0].start + sl[0].stop) // 2
            if ar > 500 and abs(yc - eye_cy) < 90 and (sl[1].stop < cfg["eyes"][0] + 10 or sl[1].start > cfg["eyes"][2] - 10):
                paste_patch(max(0, sl[1].start - 6), max(0, sl[0].start - 6),
                            min(W, sl[1].stop + 6), min(H, sl[0].stop + 6))
                print(gender, "ear patch:", sl)
    # dai mat + mieng
    paste_patch(*cfg["eyes"])
    mx, my = cfg["mouth"]
    paste_patch(mx - 22, my - 9, mx + 22, my + 10)
    # ma hong mem
    img = Image.fromarray(out, "RGBA")
    bl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    barr = np.asarray(bl).copy()
    for (bx, by) in cfg["blush"]:
        YY, XX = np.mgrid[0:H, 0:W]
        d2 = ((XX - bx) / 30.0) ** 2 + ((YY - by) / 15.0) ** 2
        al = np.clip(1 - d2, 0, 1) ** 1.5 * 105
        m2 = al > barr[:, :, 3]
        barr[:, :, 0][m2] = 247; barr[:, :, 1][m2] = 178; barr[:, :, 2][m2] = 160
        barr[:, :, 3] = np.maximum(barr[:, :, 3], al.astype(np.uint8))
    img.alpha_composite(Image.fromarray(barr, "RGBA"))
    # gioi han lai trong vom (patch co the tran ra ngoai vom o ria)
    res = np.asarray(img).copy()
    res[:, :, 3] = np.minimum(res[:, :, 3], np.where(dome, 255, 0).astype(np.uint8))
    Image.fromarray(res, "RGBA").save(os.path.join(NS, "base", f"base_{gender}.png"))
    print(gender, "-> base_%s.png" % gender)


if __name__ == "__main__":
    build("m")
    build("f")
