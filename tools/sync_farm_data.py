#!/usr/bin/env python3
"""Đồng bộ dữ liệu farm theo farm_info.dat (client) — lệnh của Tín 2026-11-03:
"Viết lại data cho đồng bộ... Cứ lấy theo client đi".

Nguồn chuẩn DUY NHẤT: docs/farm-info-decoded.json (giải mã từ res/data/farm_info.dat).
Script này sinh ra:
  1) SQL áp vào DB đang chạy (farmitems: phút chín = giờ dat x 60, sản lượng = numProduct,
     gia_san_pham = priceSeedXu, cột mới gia_hat_luong = priceSeedLuong).
  2) Viết lại khối INSERT farmitems + CREATE TABLE trong server/database/avatar_2x.sql.
  3) assets/data/farmtrees.json — bảng cây/vật nuôi/sản phẩm chuẩn cho client Java.
  4) server/.../avatar/Farm/FarmProductData.java — productID của cây động + giá bán
     mọi nông sản (items2 của dat) cho FarmService dùng.
"""
import json
import re
import subprocess
import sys

ROOT = "/home/hatch/workspace/avatardk-java"
dec = json.load(open(f"{ROOT}/docs/farm-info-decoded.json"))

trees = []
for t in dec["trees"]:
    trees.append({
        "id": t["ID"], "name": t["name"], "minutes": t["harvestTime"] * 60,
        "quantity": t["numProduct"], "seedXu": t["priceSeedXu"],
        "seedLuong": t.get("priceSeedLuong", 0), "productId": t["ID"],
        "productPrice": t["priceProduct"], "lv": 0, "img": t["img"],
        "phase": t.get("phase"),
    })
for t in dec["dynamicTrees"]:
    trees.append({
        "id": t["ID"], "name": t["name"], "minutes": t["harvestTime"] * 60,
        "quantity": t["numProduct"], "seedXu": t["priceSeedXu"],
        "seedLuong": t.get("priceSeedLuong", 0), "productId": t["productID"],
        "productPrice": 3, "lv": t.get("lv", 1), "img": t["img"],
        "phase": None,
    })
trees.sort(key=lambda x: x["id"])
assert len(trees) == 25, len(trees)

# ---------- 1) SQL cho DB đang chạy ----------
sql = ["ALTER TABLE `farmitems` ADD COLUMN `gia_hat_luong` int(11) NOT NULL DEFAULT 0;"]
for t in trees:
    sql.append(
        "UPDATE `farmitems` SET `so_phut_chin`=%d, `san_luong_khi_chin`=%d, "
        "`gia_san_pham`=%d, `gia_hat_luong`=%d WHERE `id`=%d;"
        % (t["minutes"], t["quantity"], t["seedXu"], t["seedLuong"], t["id"]))
sql_text = "\n".join(sql) + "\n"
open("/tmp/farm_sync.sql", "w").write(sql_text)
print("[1] /tmp/farm_sync.sql:", len(sql), "câu lệnh")

# ---------- 2) Viết lại dump ----------
dump_path = f"{ROOT}/server/database/avatar_2x.sql"
dump = open(dump_path, encoding="utf-8").read()
# CREATE TABLE: thêm cột gia_hat_luong trước PRIMARY KEY của farmitems
old_create = """CREATE TABLE `farmitems` (
  `id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `so_phut_chin` int(11) NOT NULL,
  `san_luong_khi_chin` int(11) NOT NULL,
  `gia_san_pham` int(11) NOT NULL
)"""
new_create = """CREATE TABLE `farmitems` (
  `id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `so_phut_chin` int(11) NOT NULL,
  `san_luong_khi_chin` int(11) NOT NULL,
  `gia_san_pham` int(11) NOT NULL,
  `gia_hat_luong` int(11) NOT NULL DEFAULT 0
)"""
if old_create in dump:
    dump = dump.replace(old_create, new_create)
else:
    assert new_create in dump, "không thấy CREATE TABLE farmitems trong dump"

# INSERT: thay số của từng dòng theo id, giữ nguyên tên trong dump.
def fix_row(m):
    row_id = int(m.group(1))
    name = m.group(2)
    t = next(x for x in trees if x["id"] == row_id)
    return "(%d, '%s', %d, %d, %d, %d)" % (
        row_id, name, t["minutes"], t["quantity"], t["seedXu"], t["seedLuong"])

pat = re.compile(r"\((\d+), '((?:[^'\\]|\\.)*)', \d+, \d+, \d+(?:, \d+)?\)")
# chỉ áp trong đoạn INSERT của farmitems
start = dump.find("INSERT INTO `farmitems`")
assert start >= 0
end = dump.find(";", start)
seg = dump[start:end]
seg2, n = pat.subn(fix_row, seg)
assert n == 25, f"dump farmitems: sửa {n}/25 dòng"
dump = dump[:start] + seg2 + dump[end:]
open(dump_path, "w", encoding="utf-8").write(dump)
print("[2] dump avatar_2x.sql: CREATE + 25 dòng farmitems đã viết lại theo dat")

# ---------- 3) farmtrees.json cho client ----------
products = {}
for t in trees:
    products[t["productId"]] = {"name": t["name"], "priceXu": t["productPrice"]}
for it in dec["items2_notAdded"]:
    products[it["ID"]] = {"name": it["des"], "priceXu": it["priceXu"], "img": it["IDImg"]}
animals = [{
    "species": a["species"], "name": a["name"], "priceXu": a["priceXu"],
    "priceLuong": a["priceLuong"], "harvestHours": a["harvestTime"],
    "priceProduct": a["priceProduct"], "area": a["area"], "iconID": a["iconID"],
    "idImg": a["idImg"], "des": a["des"],
} for a in dec["animals"]]
farm_items = [{
    "id": i["ID"], "img": i["IDImg"], "des": i["des"], "priceXu": i["priceXu"],
    "priceLuong": i["priceLuong"], "type": i.get("type"), "action": i.get("action"),
} for i in dec["items"]]
# Sản phẩm vật nuôi: client gốc (FarmScr.addProductAnimal) tạo vật phẩm kho với
# ID = species, giá = priceProduct, icon = iconProduct. Bổ sung vào bảng sản phẩm
# để kho/bán/nấu ăn dùng chung một bảng.
for a in dec["animals"]:
    if a["priceProduct"] > 0:
        products[a["species"]] = {"name": a["name"], "priceXu": a["priceProduct"],
                                  "img": a["iconProduct"]}
out = {"trees": trees, "animals": animals, "products": products, "items": farm_items,
       "foods": dec["foods"]}
with open(f"{ROOT}/assets/data/farmtrees.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
print("[3] assets/data/farmtrees.json: 25 cây,", len(animals), "vật nuôi,",
      len(products), "sản phẩm,", len(farm_items), "vật phẩm farm")

# ---------- 4) FarmProductData.java ----------
prod_map = {t["id"]: t["productId"] for t in trees if t["productId"] != t["id"]}
price_map = {pid: p["priceXu"] for pid, p in products.items()}
lines = []
lines.append("package avatar.Farm;")
lines.append("")
lines.append("import java.util.HashMap;")
lines.append("import java.util.Map;")
lines.append("")
lines.append("/**")
lines.append(" * [PORT] Sinh tự động bởi tools/sync_farm_data.py từ farm_info.dat (client)")
lines.append(" * — nguồn chuẩn duy nhất cho dữ liệu farm (lệnh đồng bộ của Tín 2026-11-03).")
lines.append(" * PRODUCT_ID: cây động (id >= 30) thu hoạch ra sản phẩm có id riêng;")
lines.append(" * cây tĩnh thu ra sản phẩm trùng id cây. SELL_PRICE_XU: giá bán mọi nông")
lines.append(" * sản theo bảng sản phẩm của dat (cây = 3 xu, món nấu = giá niêm yết dat).")
lines.append(" */")
lines.append("public final class FarmProductData {")
lines.append("    public static final Map<Integer, Integer> PRODUCT_ID = new HashMap<>();")
lines.append("    public static final Map<Integer, Integer> SELL_PRICE_XU = new HashMap<>();")
lines.append("    static {")
for k in sorted(prod_map):
    lines.append(f"        PRODUCT_ID.put({k}, {prod_map[k]});")
for k in sorted(price_map):
    lines.append(f"        SELL_PRICE_XU.put({k}, {price_map[k]});")
lines.append("    }")
lines.append("")
lines.append("    private FarmProductData() {")
lines.append("    }")
lines.append("")
lines.append("    public static int productIdOf(int treeId) {")
lines.append("        return PRODUCT_ID.getOrDefault(treeId, treeId);")
lines.append("    }")
lines.append("")
lines.append("    /** @return giá bán xu của 1 đơn vị nông sản, -1 nếu dat không niêm yết. */")
lines.append("    public static int sellPriceOf(int productId) {")
lines.append("        return SELL_PRICE_XU.getOrDefault(productId, -1);")
lines.append("    }")
lines.append("}")
open(f"{ROOT}/server/src/main/java/avatar/Farm/FarmProductData.java", "w").write("\n".join(lines) + "\n")
print("[4] FarmProductData.java:", len(prod_map), "productID,", len(price_map), "giá bán")

# ---------- 5) FarmFoodData.java (công thức nấu ăn) ----------
flines = []
flines.append("package avatar.Farm;")
flines.append("")
flines.append("import java.util.HashMap;")
flines.append("import java.util.Map;")
flines.append("")
flines.append("/**")
flines.append(" * [PORT] Sinh tự động bởi tools/sync_farm_data.py từ farm_info.dat (client).")
flines.append(" * Công thức nấu ăn: id món -> {productID, cookTime(phút), materials[][2]}.")
flines.append(" * Nguyên liệu là id nông sản trong kho (sản phẩm vật nuôi mang id = species).")
flines.append(" */")
flines.append("public final class FarmFoodData {")
flines.append("    public static final class Food {")
flines.append("        public final int productId;")
flines.append("        public final int cookMinutes;")
flines.append("        public final int[][] materials;")
flines.append("")
flines.append("        Food(int productId, int cookMinutes, int[][] materials) {")
flines.append("            this.productId = productId;")
flines.append("            this.cookMinutes = cookMinutes;")
flines.append("            this.materials = materials;")
flines.append("        }")
flines.append("    }")
flines.append("")
flines.append("    public static final Map<Integer, Food> FOODS = new HashMap<>();")
flines.append("    static {")
for fd in dec["foods"]:
    mats = ", ".join("{ %d, %d }" % (m[0], m[1]) for m in fd["materials"])
    flines.append("        FOODS.put(%d, new Food(%d, %d, new int[][]{ %s }));"
                  % (fd["ID"], fd["productID"], fd["cookTime"], mats))
flines.append("    }")
flines.append("")
flines.append("    private FarmFoodData() {")
flines.append("    }")
flines.append("}")
open(f"{ROOT}/server/src/main/java/avatar/Farm/FarmFoodData.java", "w").write("\n".join(flines) + "\n")
print("[5] FarmFoodData.java:", len(dec["foods"]), "công thức")

print("XONG — chạy SQL vào DB bằng mysql client ở bước riêng.")
