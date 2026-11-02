#!/usr/bin/env python3
"""Liệt kê part TĨNH hỏng trong pool tạo nhân vật (Lv0, zOrder 10/20/50):
icon hoặc bất kỳ frame nào trỏ ra ngoài atlas big/ hoặc trong suốt hoàn
toàn — dữ liệu rác của Pack5 (vd part 319 icon 923 ở (245,1) trong khi
big/0.png chỉ 70x38). Kết quả dán vào AvatarDataSet.brokenParts.
Chạy: python3 tools/check_broken_parts.py (từ gốc project)."""
import json, re
from PIL import Image

items = json.load(open('assets/data/items.json'))
img = {r['item_id']: r for r in json.load(open('assets/data/imgdata.json'))}
atlases = {}

def atlas(iid):
    if iid not in atlases:
        atlases[iid] = Image.open(f'assets/big/{iid}.png').convert('RGBA')
    return atlases[iid]

def region_ok(item_id):
    r = img.get(item_id)
    if r is None:
        return False
    a = atlas(r['image_id'])
    if (r['x'] + r['w']) * 2 > a.width or (r['y'] + r['h']) * 2 > a.height:
        return False
    c = a.crop((r['x'] * 2, r['y'] * 2, (r['x'] + r['w']) * 2, (r['y'] + r['h']) * 2))
    return any(p[3] > 0 for p in c.getdata())

def frames_of(p):
    s = str(p['animation']).replace('\\"', '"')
    return [int(m) for m in re.findall(r'"img"\s*:\s*(\d+)', s)]

def part_ok(p):
    fr = frames_of(p)
    return region_ok(p['icon']) and len(fr) > 0 and all(region_ok(f) for f in fr)

pool = [p for p in items if p['level'] == 0 and p['zorder'] in (10, 20, 50)
        and p['id'] < 2000 and p['name']]
bad = [p['id'] for p in pool if not part_ok(p)]
print(len(bad), bad)
