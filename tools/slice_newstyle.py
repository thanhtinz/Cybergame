#!/usr/bin/env python3
"""Cat cac sheet asset nhan vat moi (user gui 2026-10-31) thanh tung mon:
tay nen ca-ro gan-trang bang flood-fill tu bien (chi xoa vung nen cham
bien anh — mau trang nam TRONG trang phuc duoc bao ve nho vien dam),
sau do cat theo cum pixel mo duc (alpha) cua tung mon.
Chay lai duoc: python3 tools/slice_newstyle.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

SRC = os.path.expanduser("~/workspace/user/media_library/image")
ROOT = os.path.expanduser("~/workspace/avatardk-java")
OUT = os.path.join(ROOT, "assets", "newstyle")


def remove_bg(im: Image.Image) -> Image.Image:
    """Tra ve RGBA da xoa nen (flood tu bien qua vung sang trung tinh)."""
    if im.mode == "RGBA" and np.asarray(im)[:, :, 3].min() == 0:
        return im.copy()  # da co alpha that
    a = np.asarray(im.convert("RGB")).astype(int)
    light = (a.min(axis=2) >= 236) & ((a.max(axis=2) - a.min(axis=2)) <= 14)
    lab, _ = ndimage.label(light)
    border = set(np.unique(np.concatenate(
        [lab[0, :], lab[-1, :], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    # vien sang con sot lai sat ria trong suot: xoa them 2 vong
    for _ in range(2):
        near = ndimage.binary_dilation(bg) & light & ~bg
        if not near.any():
            break
        bg |= near
    rgba = np.dstack([a.astype(np.uint8), np.where(bg, 0, 255).astype(np.uint8)])
    return Image.fromarray(rgba, "RGBA")


def clusters(mask: np.ndarray, min_len: int = 8):
    """Cac doan [start, end) lien tiep co True, loc doan qua ngan."""
    out, s = [], None
    for i, v in enumerate(mask):
        if v and s is None:
            s = i
        elif not v and s is not None:
            if i - s >= min_len:
                out.append((s, i))
            s = None
    if s is not None and len(mask) - s >= min_len:
        out.append((s, len(mask)))
    return out


def _component_boxes(alpha):
    lab, _ = ndimage.label(alpha)
    objs = ndimage.find_objects(lab)
    boxes = []  # [y0, y1, x0, x1, area]
    for i, sl in enumerate(objs):
        if sl is None:
            continue
        area = int((lab[sl] == i + 1).sum())
        if area < 800:
            continue  # rac nho: tia sang le, cham trang tri roi
        boxes.append([sl[0].start, sl[0].stop, sl[1].start, sl[1].stop, area])
    # Chi gop cum NHO (phu kien roi) vao cum lon gan nhat — cac mon chinh
    # (dien tich lon) khong bao gio gop vao nhau du bbox sat nhau vai px,
    # neu khong se dinh thanh mot khoi duy nhat.
    big = [b for b in boxes if b[4] >= 15000]
    for b in [b for b in boxes if b[4] < 15000]:
        best = None
        for g in big:
            gap = max(g[0] - b[1], b[0] - g[1], g[2] - b[3], b[2] - g[3], 0)
            if gap <= 12 and (best is None or gap < best[0]):
                best = (gap, g)
        if best is not None:
            g = best[1]
            g[0], g[1] = min(g[0], b[0]), max(g[1], b[1])
            g[2], g[3] = min(g[2], b[2]), max(g[3], b[3])
            g[4] += b[4]
        else:
            big.append(b)
    return big


def _split_rows(boxes, expect_rows):
    boxes = sorted(boxes, key=lambda b: (b[0] + b[1]) / 2)
    rows = [boxes]
    while len(rows) < expect_rows:
        cand = None
        for ri, row in enumerate(rows):
            centers = [(b[0] + b[1]) / 2 for b in row]
            for k in range(len(centers) - 1):
                gap = centers[k + 1] - centers[k]
                if cand is None or gap > cand[0]:
                    cand = (gap, ri, k)
        _, ri, k = cand
        rows[ri:ri + 1] = [rows[ri][:k + 1], rows[ri][k + 1:]]
    return rows


def _clean_crop(crop):
    """Bo manh la (vet cua mon ke ben bi cat dut) trong mot o da cat:
    chi giu thanh phan lon nhat va cac thanh phan >= 25% cua no."""
    a = np.asarray(crop)[:, :, 3] > 8
    lab, n = ndimage.label(a)
    if n <= 1:
        return crop
    areas = [(int((lab == i + 1).sum()), i + 1) for i in range(n)]
    areas.sort(reverse=True)
    keep = {areas[0][1]} | {i for ar, i in areas if ar >= 0.25 * areas[0][0]}
    out = np.asarray(crop).copy()
    out[:, :, 3] = np.where(np.isin(lab, list(keep)), out[:, :, 3], 0)
    return Image.fromarray(out, "RGBA")


def slice_sheet(path, subdir, prefix, expect_rows, expect_cols):
    im = remove_bg(Image.open(path))
    alpha = np.asarray(im)[:, :, 3] > 8
    boxes = _component_boxes(alpha)
    crops = []
    if len(boxes) == expect_rows * expect_cols:
        # Duong 1: moi mon la mot cum rieng — cat theo bbox cua cum
        # (manh hon chieu hang/cot khi pham vi y cua hai hang giong nhau).
        for row in _split_rows(boxes, expect_rows):
            for b in sorted(row, key=lambda b: b[2]):
                crops.append(im.crop((b[2], b[0], b[3], b[1])))
    else:
        # Duong 2: cac mon cham nhau thanh khoi — cat theo LUOI deu,
        # duong cat cot tai diem cuc tieu cua hinh chieu alpha trong dai
        # quanh duong chia deu (cau noi giua cac mon rat mong).
        row_has = alpha.sum(axis=1) > max(6, alpha.shape[1] * 0.004)
        bands = clusters(row_has, 20)
        assert len(bands) == expect_rows, f"{path}: luoi — thay {len(bands)} hang"
        pitch = alpha.shape[1] / expect_cols
        for (r0, r1) in bands:
            colsum = alpha[r0:r1, :].sum(axis=0).astype(float)
            smooth = np.convolve(colsum, np.ones(5) / 5, mode="same")
            cuts = [0]
            for k in range(1, expect_cols):
                lo = int(k * pitch - pitch * 0.35)
                hi = int(k * pitch + pitch * 0.35)
                cuts.append(lo + int(np.argmin(smooth[lo:hi])))
            cuts.append(alpha.shape[1])
            for k in range(expect_cols):
                cell = alpha[r0:r1, cuts[k]:cuts[k + 1]]
                ys, xs = np.where(cell)
                crop = im.crop((cuts[k] + xs.min(), r0 + ys.min(),
                                cuts[k] + xs.max() + 1, r0 + ys.max() + 1))
                crops.append(_clean_crop(crop))
    assert len(crops) == expect_rows * expect_cols, \
        f"{path}: cat duoc {len(crops)}, ky vong {expect_rows * expect_cols}"
    items = []
    for n, crop in enumerate(crops, 1):
        name = f"{prefix}_{n:02d}"
        crop.save(os.path.join(OUT, subdir, name + ".png"))
        items.append({"id": name, "w": crop.width, "h": crop.height})
    return items


def main():
    catalog = {}
    catalog["hair_m"] = slice_sheet(
        os.path.join(SRC, "E14B6A72-E1AB-493D-B1A6-F88F52602334.png"),
        "hair_m", "hair_m", 2, 5)
    catalog["hair_f"] = slice_sheet(
        os.path.join(SRC, "FB55250B-3F88-4A45-AD81-5688EC35772F.png"),
        "hair_f", "hair_f", 2, 5)
    catalog["outfit_m"] = slice_sheet(
        os.path.join(SRC, "1E0C3C7D-1B4A-443F-8EDF-D095F15E08A9.png"),
        "outfit_m", "outfit_m", 2, 10)
    catalog["outfit_f"] = slice_sheet(
        os.path.join(SRC, "1C458060-165A-4735-91C8-314DCC269AC6.jpg"),
        "outfit_f", "outfit_f", 2, 10)
    # cap doi mau: alpha that, tach 2 cum theo cot
    im = Image.open(os.path.join(SRC, "458F9700-0301-45B1-AD00-382484A44CBA.png")).convert("RGBA")
    alpha = np.asarray(im)[:, :, 3] > 8
    cols = clusters(alpha.any(axis=0), 30)
    heroes = []
    for i, (c0, c1) in enumerate(cols):
        sub = alpha[:, c0:c1]
        ys, xs = np.where(sub)
        crop = im.crop((c0 + xs.min(), ys.min(), c0 + xs.max() + 1, ys.max() + 1))
        name = "hero_m" if i == 0 else "hero_f"
        crop.save(os.path.join(OUT, "hero", name + ".png"))
        heroes.append({"id": name, "w": crop.width, "h": crop.height})
    catalog["hero"] = heroes
    with open(os.path.join(ROOT, "assets", "data", "newstyle.json"), "w") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    for k, v in catalog.items():
        print(k, len(v), "mon; kich thuoc dau/cuoi:", v[0], v[-1])


if __name__ == "__main__":
    sys.exit(main())
