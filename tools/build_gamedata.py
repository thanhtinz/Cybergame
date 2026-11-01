#!/usr/bin/env python3
"""Đóng gói dữ liệu game (thư mục assets/) thành các gói zip + manifest.json
để client tải về máy ở lần vào đầu tiên (DataUpdater).

Cách dùng:
    python3 tools/build_gamedata.py [version] [outdir]
Mặc định: version=1, outdir=dist/

Mỗi gói <= ~14MB (đủ nhỏ để GitHub Releases phục vụ thẳng từng file).
Manifest: {"version":N, "totalSize":..., "fileCount":...,
           "bundles":[{"file":"gamedata-1.zip","size":...,"sha256":"..."}]}
"""
import hashlib
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
MAX_BUNDLE = 14 * 1024 * 1024


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    version = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    outdir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "dist")
    os.makedirs(outdir, exist_ok=True)

    files = []
    for dirpath, _dirnames, filenames in os.walk(ASSETS):
        for name in filenames:
            full = os.path.join(dirpath, name)
            arc = os.path.relpath(full, ASSETS).replace(os.sep, "/")
            files.append((arc, full, os.path.getsize(full)))
    # deterministic: theo thư mục cấp 1 rồi đường dẫn
    files.sort(key=lambda t: (t[0].split("/")[0], t[0]))

    groups = []
    cur, cur_size = [], 0
    for item in files:
        if cur and cur_size + item[2] > MAX_BUNDLE:
            groups.append(cur)
            cur, cur_size = [], 0
        cur.append(item)
        cur_size += item[2]
    if cur:
        groups.append(cur)

    bundles = []
    total = 0
    for i, group in enumerate(groups, 1):
        zname = f"gamedata-{i}.zip"
        zpath = os.path.join(outdir, zname)
        with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for arc, full, _size in group:
                z.write(full, arc)
        size = os.path.getsize(zpath)
        total += size
        bundles.append({"file": zname, "size": size, "sha256": sha256_of(zpath)})
        print(f"{zname}: {len(group)} files, {size/1048576:.1f} MB, sha256 {bundles[-1]['sha256'][:16]}…")

    manifest = {"version": version, "totalSize": total,
                "fileCount": len(files), "bundles": bundles}
    mpath = os.path.join(outdir, "manifest.json")
    with open(mpath, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    print(f"manifest.json: version {version}, {len(files)} files, "
          f"{len(bundles)} bundles, total {total/1048576:.1f} MB -> {outdir}")


if __name__ == "__main__":
    main()
