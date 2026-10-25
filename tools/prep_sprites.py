#!/usr/bin/env python3
"""Cat sprite nhan vat / quai tu cac sheet tranh ve cua Neo Lang: Cyber Grove.

Nguon:
  - core/src/main/resources/art/hero_male.jpg / hero_female.jpg
    (sheet 3 goc nhin: truoc lon ben trai, ben tren-phai, sau duoi-phai, pet robot o goc duoi)
  - ~/workspace/imagine_media/media-generation-{male,female}-customization-v2-*.webp
    (bang tuy bien v2: hang 1 = 3 kieu toc (dau), hang 2 = 3 mau mat (mat),
     hang 3 = 3 bo do (figure toan than))
  - ~/workspace/imagine_media/media-generation-monster-sheet-*.webp (luoi 3x2, nen kem)
  - ~/workspace/imagine_media/media-generation-neo-lang-survival-building-*.webp (nen man dat nha)
  - ~/workspace/imagine_media/media-generation-town-buildings-sheet-*.webp
    (luoi 3x2: cafe / thoi trang / ngan hang | pet / tram dinh huong / portal — moi o
     gop MOI thanh phan co tam nam trong o, vi dau chan holo cua pet pod va man hinh
     holo cua kiosk la thanh phan roi)
  - ~/workspace/imagine_media/media-generation-props-sheet-*.webp
    (KHONG phai luoi deu: 8 mon xep 2 hang lech nhau — cat theo hop hat giong,
     moi thanh phan chi thuoc ve 1 mon dau tien chua tam cua no)
  - 2 plate nen sach: town-bg-plate -> art/town_plate.jpg, adventure-bg-plate -> art/adv_plate.jpg
    (chi la JPG phu, khong cat — man dung plate lam nen xa, toa nha/do vat la sprite rieng)

Ky thuat: xoa nen bang flood-fill tu mep anh (chi xoa vung mau-nen lien thong voi mep,
nen ao trang / vung sang BEN TRONG nhan vat duoc giu nguyen), feather alpha 1px +
color-bleed de tranh vien toi khi loc tuyen tinh. Moi sprite lay thanh phan lien
thong lon nhat trong o cua no (bo cac cham glitch roi rac quanh quai).

Dau ra:
  - PNG trong suot vao core/src/main/resources/art/sprites/
  - ban .b64 tuong ung vao assets-src/core/src/main/resources/art/sprites/ (quy uoc repo)
  - custom_male.jpg / custom_female.jpg duoc thay bang ban v2, them art/plot.jpg

Chay:  python3 tools/prep_sprites.py
"""
import base64
import glob
import pathlib
import sys

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES_ART = ROOT / "core" / "src" / "main" / "resources" / "art"
SPR_OUT = RES_ART / "sprites"
B64_ART = ROOT / "assets-src" / "core" / "src" / "main" / "resources" / "art"
MEDIA = pathlib.Path.home() / "workspace" / "imagine_media"

TOL = 34.0          # nguong khoang cach mau so voi mau nen (RGB euclid)
FEATHER = 1.0       # sigma lam mo alpha o mep


# ------------------------------------------------------------------ nen
def bg_shades(arr):
    """Cac sac mau nen chinh, lay tu toan bo pixel mep anh (luong tu hoa)."""
    h, w, _ = arr.shape
    border = np.concatenate([
        arr[0, :, :], arr[-1, :, :], arr[:, 0, :], arr[:, -1, :]]).astype(np.int32)
    q = (border // 16)
    keys = q[:, 0] * 256 + q[:, 1] * 16 + q[:, 2]
    vals, counts = np.unique(keys, return_counts=True)
    shades = []
    for v, c in sorted(zip(vals, counts), key=lambda t: -t[1]):
        if c < len(border) * 0.004:
            break
        shades.append(np.array([(v // 256) * 16 + 8, ((v // 16) % 16) * 16 + 8, (v % 16) * 16 + 8]))
        if len(shades) >= 6:
            break
    return shades


def alpha_mask(img):
    """Tra ve alpha float 0..1: 0 o nen (lien thong voi mep), 1 o nhan vat."""
    arr = np.asarray(img.convert("RGB")).astype(np.float32)
    h, w, _ = arr.shape
    dist = np.full((h, w), 1e9, np.float32)
    for s in bg_shades(arr.astype(np.int32)):
        d = np.sqrt(((arr - s.astype(np.float32)) ** 2).sum(axis=2))
        dist = np.minimum(dist, d)
    like = dist < TOL
    lab, _ = ndimage.label(like)
    border_labels = set(np.unique(np.concatenate([
        lab[0, :], lab[-1, :], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border_labels))
    alpha = np.where(bg, 0.0, 1.0).astype(np.float32)
    alpha = ndimage.gaussian_filter(alpha, FEATHER)
    alpha = np.clip(alpha * 1.5 - 0.25, 0, 1)
    return alpha


def components(alpha):
    """Danh sach thanh phan lien thong cua vung alpha>0.5, kem bbox/dien tich/tam."""
    mask = alpha > 0.5
    lab, n = ndimage.label(mask)
    out = []
    objs = ndimage.find_objects(lab)
    for i, sl in enumerate(objs, start=1):
        if sl is None:
            continue
        area = int((lab[sl] == i).sum())
        ys, xs = sl
        out.append({
            "label": i, "area": area,
            "bbox": (xs.start, ys.start, xs.stop, ys.stop),  # x0,y0,x1,y1 (anh)
            "cx": (xs.start + xs.stop) / 2.0, "cy": (ys.start + ys.stop) / 2.0,
            "slice": sl,
        })
    return lab, out


def extract(img, alpha, lab, comp, pad=8, max_h=None):
    """Cat 1 thanh phan thanh PNG RGBA (chi giu alpha cua chinh thanh phan do)."""
    h, w = alpha.shape
    x0, y0, x1, y1 = comp["bbox"]
    keep = (lab == comp["label"])
    a = np.where(keep, alpha, 0.0)
    # feather lai theo thanh phan (tranh goc cung tu mask)
    a = ndimage.gaussian_filter(a, 0.7)
    a = np.clip(a * 1.6 - 0.3, 0, 1)
    x0 = max(0, x0 - pad); y0 = max(0, y0 - pad)
    x1 = min(w, x1 + pad); y1 = min(h, y1 + pad)
    rgb = np.asarray(img.convert("RGB")).astype(np.float32)
    bleed = np.asarray(img.convert("RGB").filter(ImageFilter.GaussianBlur(10))).astype(np.float32)
    solid = a > 0.98
    rgb = np.where(solid[..., None], rgb, bleed)
    rgba = np.dstack([rgb, (a * 255).astype(np.uint8)]).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA").crop((x0, y0, x1, y1))
    if max_h and out.height > max_h:
        r = max_h / out.height
        out = out.resize((round(out.width * r), max_h), Image.LANCZOS)
    return out


def extract_group(img, alpha, lab, comps, pad=8, max_h=None):
    """Nhu extract() nhung gop nhieu thanh phan (toa nha co bo phan roi nhu
    dau chan holo / man hinh holo; dao cu nhu bo ban ghe gom nhieu manh)."""
    h, w = alpha.shape
    keep = np.isin(lab, [c["label"] for c in comps])
    a = np.where(keep, alpha, 0.0)
    a = ndimage.gaussian_filter(a, 0.7)
    a = np.clip(a * 1.6 - 0.3, 0, 1)
    x0 = max(0, min(c["bbox"][0] for c in comps) - pad)
    y0 = max(0, min(c["bbox"][1] for c in comps) - pad)
    x1 = min(w, max(c["bbox"][2] for c in comps) + pad)
    y1 = min(h, max(c["bbox"][3] for c in comps) + pad)
    rgb = np.asarray(img.convert("RGB")).astype(np.float32)
    bleed = np.asarray(img.convert("RGB").filter(ImageFilter.GaussianBlur(10))).astype(np.float32)
    solid = a > 0.98
    rgb = np.where(solid[..., None], rgb, bleed)
    rgba = np.dstack([rgb, (a * 255).astype(np.uint8)]).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA").crop((x0, y0, x1, y1))
    if max_h and out.height > max_h:
        r = max_h / out.height
        out = out.resize((round(out.width * r), max_h), Image.LANCZOS)
    return out


def save_sprite(img_rgba, name):
    SPR_OUT.mkdir(parents=True, exist_ok=True)
    p = SPR_OUT / name
    img_rgba.save(p, "PNG", optimize=True)
    rel = pathlib.Path("core/src/main/resources/art/sprites") / name
    b64p = ROOT / "assets-src" / (str(rel) + ".b64")
    b64p.parent.mkdir(parents=True, exist_ok=True)
    b64p.write_text(base64.b64encode(p.read_bytes()).decode("ascii"), encoding="ascii")
    print("SPRITE %-22s %4dx%-4d %7d bytes" % (name, img_rgba.width, img_rgba.height, p.stat().st_size))
    return p


def save_jpg(img, name, width, quality=87):
    if img.width != width:
        img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
    p = RES_ART / name
    img.convert("RGB").save(p, "JPEG", quality=quality)
    b64p = B64_ART / (name + ".b64")
    b64p.write_text(base64.b64encode(p.read_bytes()).decode("ascii"), encoding="ascii")
    print("JPG    %-22s %4dx%-4d %7d bytes" % (name, img.width, img.height, p.stat().st_size))
    return p


# ------------------------------------------------------------------ hero sheets
def process_hero(path, prefix):
    img = Image.open(path).convert("RGB")
    alpha = alpha_mask(img)
    lab, comps = components(alpha)
    comps = [c for c in comps if c["area"] >= 1200]
    comps.sort(key=lambda c: -c["area"])
    if len(comps) < 4:
        raise RuntimeError("%s: chi tim thay %d thanh phan, can 4 (truoc/ben/sau/pet)" % (path, len(comps)))
    front = comps[0]
    rest = comps[1:]
    pet = min(rest, key=lambda c: c["area"])
    rest = [c for c in rest if c is not pet]
    side = min(rest, key=lambda c: c["cy"])   # o tren = goc ben
    back = max(rest, key=lambda c: c["cy"])   # o duoi = goc sau
    save_sprite(extract(img, alpha, lab, front, max_h=740), prefix + "_front.png")
    save_sprite(extract(img, alpha, lab, side, max_h=620), prefix + "_side.png")
    save_sprite(extract(img, alpha, lab, back, max_h=620), prefix + "_back.png")
    return extract(img, alpha, lab, pet, max_h=300)


# ------------------------------------------------------------------ grid sheets
def process_grid(path, prefix, rows, cols, names, max_h):
    """names[row][col] -> ten file. Moi o lay thanh phan lon nhat co tam nam trong o."""
    img = Image.open(path).convert("RGB")
    alpha = alpha_mask(img)
    lab, comps = components(alpha)
    comps = [c for c in comps if c["area"] >= 900]
    W, H = img.size
    for r in range(rows):
        for c in range(cols):
            cx0, cx1 = W * c / cols, W * (c + 1) / cols
            cy0, cy1 = H * r / rows, H * (r + 1) / rows
            inside = [k for k in comps if cx0 - 4 <= k["cx"] <= cx1 + 4 and cy0 - 4 <= k["cy"] <= cy1 + 4]
            if not inside:
                raise RuntimeError("%s o (%d,%d) khong co thanh phan" % (path, r, c))
            best = max(inside, key=lambda k: k["area"])
            spr = extract(img, alpha, lab, best, max_h=max_h)
            save_sprite(spr, names[r][c])


def process_grid_group(path, rows, cols, names, max_h, min_area=500):
    """Nhu process_grid nhung moi o GOP moi thanh phan co tam nam trong o
    (toa nha: than + bien/dau holo roi deu thuoc ve cung 1 sprite)."""
    img = Image.open(path).convert("RGB")
    alpha = alpha_mask(img)
    lab, comps = components(alpha)
    comps = [c for c in comps if c["area"] >= min_area]
    W, H = img.size
    for r in range(rows):
        for c in range(cols):
            cx0, cx1 = W * c / cols, W * (c + 1) / cols
            cy0, cy1 = H * r / rows, H * (r + 1) / rows
            inside = [k for k in comps if cx0 - 4 <= k["cx"] <= cx1 + 4 and cy0 - 4 <= k["cy"] <= cy1 + 4]
            if not inside:
                raise RuntimeError("%s o (%d,%d) khong co thanh phan" % (path, r, c))
            save_sprite(extract_group(img, alpha, lab, inside, max_h=max_h), names[r][c])


def process_boxes(path, boxes, min_area=200):
    """Cat sheet khong theo luoi deu: boxes = [(ten, x0, y0, x1, y1, max_h)] theo
    ti le anh (goc trai-tren). Moi thanh phan chi duoc claim boi hop DAU TIEN
    chua tam cua no -> thu tu boxes quan trong o vung giao nhau (hoa neon phai
    claim truoc bang nhiem vu vi canh hoa cham vao hop cua bang)."""
    img = Image.open(path).convert("RGB")
    alpha = alpha_mask(img)
    lab, comps = components(alpha)
    comps = [c for c in comps if c["area"] >= min_area]
    W, H = img.size
    pool = list(comps)
    for name, fx0, fy0, fx1, fy1, max_h in boxes:
        mine = [k for k in pool if fx0 * W - 4 <= k["cx"] <= fx1 * W + 4
                and fy0 * H - 4 <= k["cy"] <= fy1 * H + 4]
        if not mine:
            raise RuntimeError("%s hop %s khong co thanh phan" % (path, name))
        pool = [k for k in pool if k not in mine]
        save_sprite(extract_group(img, alpha, lab, mine, max_h=max_h), name)


def one(pattern):
    hits = sorted(glob.glob(str(MEDIA / pattern)))
    if not hits:
        raise RuntimeError("Khong tim thay file: " + pattern)
    return pathlib.Path(hits[0])


def main():
    # --- hero: nhan vat chinh + pet ---
    pet_m = process_hero(RES_ART / "hero_male.jpg", "hero_m")
    pet_f = process_hero(RES_ART / "hero_female.jpg", "hero_f")
    # pet cua 2 sheet la cung 1 robot; dung ban nam (ro net hon) lam sprite pet chung
    save_sprite(pet_m, "pet.png")

    # --- bang tuy bien v2: dau / mat / figure ---
    male_v2 = one("media-generation-male-customization-v2-*.webp")
    female_v2 = one("media-generation-female-customization-v2-*.webp")
    for path, pfx in ((male_v2, "cust_m"), (female_v2, "cust_f")):
        names = [
            [pfx + "_head0.png", pfx + "_head1.png", pfx + "_head2.png"],
            [pfx + "_eyes0.png", pfx + "_eyes1.png", pfx + "_eyes2.png"],
            [pfx + "_fig0.png", pfx + "_fig1.png", pfx + "_fig2.png"],
        ]
        process_grid(path, pfx, 3, 3, names, max_h=660)

    # --- quai: luoi 3x2 ---
    monster = one("media-generation-monster-sheet-*.webp")
    process_grid(monster, "mon", 2, 3, [
        ["mon_beetle.png", "mon_blob.png", "mon_wraith.png"],
        ["mon_mush.png", "mon_jelly.png", "mon_spike.png"],
    ], max_h=460)

    # --- toa nha thi tran: luoi 3x2, moi o gop het thanh phan trong o ---
    buildings = one("media-generation-town-buildings-sheet-*.webp")
    process_grid_group(buildings, 2, 3, [
        ["bld_cafe.png", "bld_fashion.png", "bld_bank.png"],
        ["bld_pet.png", "bld_station.png", "bld_portal.png"],
    ], max_h=640)

    # --- dao cu: sheet khong deu, cat theo hop (hoa claim truoc bang) ---
    props = one("media-generation-props-sheet-*.webp")
    process_boxes(props, [
        ("prop_flower.png", 0.79, 0.46, 0.995, 0.99, 520),
        ("prop_board.png",  0.76, 0.07, 0.995, 0.54, 460),
        ("prop_pod0.png",   0.02, 0.04, 0.26, 0.54, 520),   # pod mam
        ("prop_pod1.png",   0.30, 0.01, 0.53, 0.56, 560),   # pod cay
        ("prop_pod2.png",   0.55, 0.05, 0.76, 0.56, 520),   # pod qua
        ("prop_lamp.png",   0.01, 0.52, 0.28, 0.99, 560),   # den canh cay
        ("prop_chest.png",  0.27, 0.58, 0.54, 0.98, 460),   # ruong kim loai
        ("prop_table.png",  0.52, 0.58, 0.80, 0.97, 460),   # ban ghe cafe
    ])

    # --- jpg thay moi: bang tuy bien v2 + nen dat nha ---
    save_jpg(Image.open(male_v2), "custom_male.jpg", 1400)
    save_jpg(Image.open(female_v2), "custom_female.jpg", 1400)
    save_jpg(Image.open(one("media-generation-neo-lang-survival-building-*.webp")), "plot.jpg", 1600, quality=85)

    # --- plate nen sach (map xay theo lop: plate xa + sprite gan) ---
    save_jpg(Image.open(one("media-generation-town-bg-plate-*.webp")), "town_plate.jpg", 1920, quality=86)
    save_jpg(Image.open(one("media-generation-adventure-bg-plate-*.webp")), "adv_plate.jpg", 1920, quality=86)
    print("Xong.")


if __name__ == "__main__":
    sys.exit(main())
