package com.avatardk.game.ui;

import com.avatardk.game.GameFiles;
import com.avatardk.game.net.Msg;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trạng thái + dữ liệu FARM phía client (đợt 3), port theo FarmScr/FarmData/
 * FarmMsgHandler của client gốc. Dữ liệu tĩnh (cây/vật nuôi/sản phẩm) lấy từ
 * assets/data/farmtrees.json — BẢNG CHUẨN DUY NHẤT sinh từ farm_info.dat
 * (tools/sync_farm_data.py), cùng nguồn với phía server nên không lệch số.
 * Hình: cây tĩnh cắt từ 2 sheet bigFarm theo farmimgdata.json (bảng
 * farm_image_data của DB gốc); cây động + vật nuôi là file farm/img/<id>.png
 * (chính là nội dung server gốc phát qua gói 82).
 */
public class FarmWorld {

    public static class TreeDef {
        public int id;
        public String name;
        public int minutes;      // phút chín (dat: giờ x 60)
        public int quantity;     // sản lượng khi chín
        public int seedXu, seedLuong;
        public int productId, productPrice, lv;
        public int[] img = new int[0]; // 8 ảnh: 0..5 lớn dần, 6 = chín, 7 = icon
    }

    public static class AnimalDef {
        public int species;
        public String name;
        public int priceXu, priceLuong, harvestHours, priceProduct, area, iconID;
        public int[] idImg = new int[0]; // 3 giai đoạn lớn
    }

    public static class Cell {
        public int idTree = -1;
        public int time;         // phút đã trồng
        public int vitality;     // sức khoẻ %
        public int hervestPer;   // % lớn
        public boolean arid, worm, grass;
        public int level = 1;
    }

    public static class Animal {
        public int species;
        public int bornTime;     // giây epoch lúc mua
        public int health, harvestPer, numEggOne;
        public boolean hunger, dis0, dis1;
        public float phase;      // lệch pha lang thang cho từng con
    }

    public final Map<Integer, TreeDef> trees = new HashMap<>();
    public final Map<Integer, AnimalDef> animalDefs = new HashMap<>();
    private final Map<Integer, int[]> imgRects = new HashMap<>(); // id -> {big,x,y,w,h}

    public final List<Cell> cells = new ArrayList<>();
    public final List<Animal> animals = new ArrayList<>();
    // kho farm (gói 60)
    public final List<int[]> seeds = new ArrayList<>();
    public final List<int[]> products = new ArrayList<>();
    public final List<int[]> ferts = new ArrayList<>();
    public final List<int[]> specials = new ArrayList<>();
    public final List<int[]> usableItems = new ArrayList<>(); // listItemFarm của gốc (vật phẩm dùng trong farm)
    public int lvFarm, perLv, levelStore = 1, capacity = 64000;
    public boolean invLoaded, joined;
    // cây khế (gói 61)
    public int starLv, starImg = 43, starFruit = 46, starNum, starAnTrom;
    public int barn, pond, foodId, foodRemain;

    private Texture big0, big1;
    private final Map<Integer, TextureRegion> regionCache = new HashMap<>();
    private final Map<Integer, Texture> dynCache = new HashMap<>();
    private AvatarDkGame game;
    private boolean dataLoaded;

    /** Nạp dữ liệu tĩnh + 2 sheet bigFarm (gọi một lần, lười). */
    public void load(AvatarDkGame g) {
        if (dataLoaded) return;
        dataLoaded = true;
        game = g;
        try {
            JsonValue root = new JsonReader().parse(
                GameFiles.data("data/farmtrees.json").readString("UTF-8"));
            for (JsonValue t : root.get("trees")) {
                TreeDef d = new TreeDef();
                d.id = t.getInt("id");
                d.name = t.getString("name");
                d.minutes = t.getInt("minutes");
                d.quantity = t.getInt("quantity");
                d.seedXu = t.getInt("seedXu");
                d.seedLuong = t.getInt("seedLuong");
                d.productId = t.getInt("productId");
                d.productPrice = t.getInt("productPrice");
                d.lv = t.getInt("lv");
                JsonValue im = t.get("img");
                d.img = new int[im.size];
                for (int i = 0; i < im.size; i++) d.img[i] = im.getInt(i);
                trees.put(d.id, d);
            }
            for (JsonValue a : root.get("animals")) {
                AnimalDef d = new AnimalDef();
                d.species = a.getInt("species");
                d.name = a.getString("name");
                d.priceXu = a.getInt("priceXu");
                d.priceLuong = a.getInt("priceLuong");
                d.harvestHours = a.getInt("harvestHours");
                d.priceProduct = a.getInt("priceProduct");
                d.area = a.getInt("area");
                d.iconID = a.getInt("iconID");
                JsonValue im = a.get("idImg");
                d.idImg = new int[im.size];
                for (int i = 0; i < im.size; i++) d.idImg[i] = im.getInt(i);
                animalDefs.put(d.species, d);
            }
            JsonValue rects = new JsonReader().parse(
                GameFiles.data("data/farmimgdata.json").readString("UTF-8"));
            for (JsonValue r : rects) {
                imgRects.put(r.getInt("id"), new int[]{
                    r.getInt("big"), r.getInt("x"), r.getInt("y"),
                    r.getInt("w"), r.getInt("h")});
            }
        } catch (Exception e) {
            System.out.println("FarmWorld.load loi: " + e.getMessage());
        }
    }

    private Texture bigSheet(int big) {
        if (big == 0) {
            if (big0 == null) big0 = game.texture("farm/big0.png");
            return big0;
        }
        if (big1 == null) big1 = game.texture("farm/big1.png");
        return big1;
    }

    /** Vùng hình của một ảnh tĩnh (không gian farm_image_data, id 0..79). */
    public TextureRegion staticRegion(int imgId) {
        TextureRegion r = regionCache.get(imgId);
        if (r != null) return r;
        int[] rc = imgRects.get(imgId);
        if (rc == null) return null;
        r = new TextureRegion(bigSheet(rc[0]), rc[1], rc[2], rc[3], rc[4]);
        regionCache.put(imgId, r);
        return r;
    }

    /** File hình động (cây động/vật nuôi/sản phẩm) — null nếu Pack5 không có file này. */
    public Texture dynTexture(int imgId) {
        if (dynCache.containsKey(imgId)) return dynCache.get(imgId);
        Texture t = null;
        String path = "farm/img/" + imgId + ".png";
        if (GameFiles.data(path).exists()) t = game.texture(path);
        dynCache.put(imgId, t);
        return t;
    }

    /** Giai đoạn vẽ của ô: 0..5 lớn dần, 6 = chín (setInfoCell của gốc). */
    public int stageOf(Cell c) {
        TreeDef d = trees.get(c.idTree);
        if (d == null || d.minutes <= 0) return 0;
        int stage = c.time / Math.max(1, d.minutes / 5);
        if (stage > 5) stage = 5;
        if (c.hervestPer >= 100) stage = 6;
        return stage;
    }

    // ---------- Đọc gói theo đúng client gốc ----------

    private static void readCell(DataInputStream r, Cell c) throws IOException {
        c.time = r.readShort();
        c.vitality = r.readByte();
        c.hervestPer = r.readByte();
        c.arid = r.readBoolean();
        c.worm = r.readBoolean();
        c.grass = r.readBoolean();
    }

    private static void readAnimal(DataInputStream r, Animal a) throws IOException {
        a.bornTime = r.readInt();
        a.health = r.readByte();
        a.harvestPer = r.readByte();
        a.numEggOne = r.readByte();
        a.hunger = r.readBoolean();
        a.dis0 = r.readBoolean();
        a.dis1 = r.readBoolean();
    }

    /** Gói 61 JOIN — FarmMsgHandler.readFarmData của gốc. */
    public void parseJoin(Msg m) {
        try {
            DataInputStream r = m.reader();
            int idFarm = r.readInt();
            cells.clear();
            animals.clear();
            if (idFarm != -1) {
                int n = r.readUnsignedByte();
                for (int i = 0; i < n; i++) {
                    Cell c = new Cell();
                    c.idTree = r.readByte();
                    if (c.idTree != -1) readCell(r, c);
                    cells.add(c);
                }
                int na = r.readUnsignedByte();
                for (int j = 0; j < na; j++) {
                    int sp = r.readByte();
                    if (sp == -1) continue;
                    Animal a = new Animal();
                    a.species = sp;
                    a.phase = j * 1.7f;
                    readAnimal(r, a);
                    animals.add(a);
                }
            }
            barn = r.readUnsignedByte();
            pond = r.readUnsignedByte();
            starLv = r.readShort();
            starImg = r.readShort();
            starFruit = r.readShort();
            starNum = r.readShort();
            r.readShort(); // bỏ qua (gốc cũng bỏ)
            starAnTrom = r.readShort();
            r.readShort(); // timeFinish (x60 giây) — GĐ1 chưa dùng đồng hồ khế
            for (Cell c : cells) {
                if (r.available() > 0) c.level = r.readByte();
            }
            if (r.available() > 0) {
                foodId = r.readShort();
                foodRemain = r.readShort() * 60;
            } else {
                foodId = 0;
                foodRemain = 0;
            }
            joined = true;
        } catch (IOException e) {
            System.out.println("FarmWorld.parseJoin loi: " + e.getMessage());
        }
    }

    /** Gói 60 INVENTORY — case 60 của FarmMsgHandler gốc (đọc 2 lượt). */
    public void parseInventory(Msg m) {
        try {
            DataInputStream r = m.reader();
            seeds.clear();
            products.clear();
            ferts.clear();
            specials.clear();
            List<int[]> merged = new ArrayList<>();
            int n1 = r.readUnsignedByte();
            for (int i = 0; i < n1; i++) {
                int id = r.readByte();
                int num = r.readShort();
                if (id > 100) merged.add(new int[]{id, num});
                else seeds.add(new int[]{id, num});
            }
            int n2 = r.readUnsignedByte();
            for (int i = 0; i < n2; i++) ferts.add(new int[]{r.readByte(), r.readShort()});
            r.readInt(); // lượng (client gốc nạp vào money[1]; bản port đã có tiền từ gói park)
            lvFarm = r.readByte();
            perLv = r.readByte();
            int n4 = r.readUnsignedByte();
            usableItems.clear();
            for (int i = 0; i < n4; i++) usableItems.add(new int[]{r.readShort(), r.readShort()});
            int n5 = r.readUnsignedByte();
            // lượt 1 của danh sách đặc biệt {short, short} — client gốc đọc vào
            // rồi XOÁ và đọc lại lượt 2 {short, int} ở cuối gói; chỉ lượt 2 tính.
            for (int i = 0; i < n5; i++) { r.readShort(); r.readShort(); }
            levelStore = r.readByte();
            capacity = r.readInt();
            r.readBoolean(); // isNew
            lvFarm = r.readShort();
            perLv = r.readByte();
            int n6 = r.readUnsignedByte();
            for (int i = 0; i < n6; i++) products.add(new int[]{r.readShort(), r.readInt()});
            int n7 = r.readUnsignedByte();
            for (int i = 0; i < n7; i++) specials.add(new int[]{r.readShort(), r.readInt()});
            invLoaded = true;
        } catch (IOException e) {
            System.out.println("FarmWorld.parseInventory loi: " + e.getMessage());
        }
    }

    /** Gói 78: server cập nhật một ô đất. */
    public void parseCellUpdate(Msg m) {
        try {
            DataInputStream r = m.reader();
            int idx = r.readUnsignedByte();
            if (idx < 0 || idx >= cells.size()) return;
            Cell c = cells.get(idx);
            c.idTree = r.readByte();
            if (c.idTree != -1) readCell(r, c);
        } catch (IOException e) {
            System.out.println("FarmWorld.parseCellUpdate loi: " + e.getMessage());
        }
    }

    /** Gói 79: server cập nhật một vật nuôi. */
    public void parseAnimalUpdate(Msg m) {
        try {
            DataInputStream r = m.reader();
            int idx = r.readUnsignedByte();
            int sp = r.readByte();
            if (sp == -1) return;
            Animal a = null;
            for (Animal x : animals) if (x.species == sp) { a = x; break; }
            if (a == null) {
                a = new Animal();
                a.species = sp;
                animals.add(a);
            }
            readAnimal(r, a);
        } catch (IOException e) {
            System.out.println("FarmWorld.parseAnimalUpdate loi: " + e.getMessage());
        }
    }

    public void clear() {
        cells.clear();
        animals.clear();
        joined = false;
        invLoaded = false;
    }

    // ---------- Hình học trên map farm ----------

    /** Các gốc khối đất (ô 142 trong lưới map) theo thứ tự đọc của LoadMap gốc. */
    public static List<int[]> plotBlocks(byte[] gridCells, int gridW) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < gridCells.length; i++) {
            if ((gridCells[i] & 0xFF) == 142) out.add(new int[]{i % gridW, i / gridW});
        }
        return out;
    }

    /** Vị trí ô thứ idx (đơn vị medium): {trung tâm x, chân y tính từ ĐÁY map}.
     *  Gốc: x = xCell*24 + 12, y (từ trên) = yCell*24 + 18; khối 12 ô, 4 ô/cột. */
    public static float[] cellPos(List<int[]> blocks, int idx, int gridH) {
        int block = idx / 12;
        int j = idx % 12;
        if (block >= blocks.size()) return null;
        int cx = blocks.get(block)[0] + j / 4;
        int cy = blocks.get(block)[1] + j % 4;
        return new float[]{cx * 24f + 12f, gridH * 24f - (cy * 24f + 18f)};
    }

    /** Vị trí cây khế: ô 189 trong lưới map. {trung tâm x, chân y từ đáy}. */
    public static float[] starPos(byte[] gridCells, int gridW, int gridH) {
        for (int i = 0; i < gridCells.length; i++) {
            if ((gridCells[i] & 0xFF) == 189) {
                int cx = i % gridW, cy = i / gridW;
                return new float[]{cx * 24f + 12f, gridH * 24f - (cy * 24f + 24f)};
            }
        }
        return null;
    }

    /** Mỏ neo theo khu vật nuôi (đơn vị medium): 1 chuồng gà, 2 chuồng, 3 giữ nhà, 4 hồ cá. */
    public static float[] animalAnchor(int area, int slot) {
        float dx = (slot % 3) * 26f - 26f;
        switch (area) {
            case 1: return new float[]{120f + dx, 66f};
            case 2: return new float[]{520f + dx, 84f};
            case 3: return new float[]{330f + dx, 120f};
            case 4: return new float[]{920f + dx, 80f}; // hồ cá ở góc phải-dưới của map
            default: return new float[]{200f + dx, 80f};
        }
    }

    /** Dữ liệu mẫu cho harness chụp hình (offline). */
    public void demo() {
        cells.clear();
        animals.clear();
        int[][] demoTrees = {
            {0, 300, 100, 100},   // cà chua chín
            {43, 60, 100, 100},   // ớt chín
            {43, 25, 90, 42},     // ớt đang lớn, khô
            {5, 100, 100, 84},    // hoa hồng gần chín
            {30, 700, 100, 49},   // bắp nửa đường
            {-1, 0, 0, 0},
            {36, 660, 100, 100},  // chuối chín
            {10, 30, 80, 9},      // tulip mới trồng
            {-1, 0, 0, 0}, {-1, 0, 0, 0}, {-1, 0, 0, 0}, {-1, 0, 0, 0},
        };
        for (int[] d : demoTrees) {
            Cell c = new Cell();
            c.idTree = d[0];
            c.time = d[1];
            c.vitality = d[2];
            c.hervestPer = d[3];
            c.arid = d[0] == 43 && d[3] == 42;
            cells.add(c);
        }
        Animal fish = new Animal();
        fish.species = 54;
        fish.health = 100;
        animals.add(fish);
        starLv = 10;
        starImg = 43;
        starFruit = 46;
        starNum = 180;
        barn = 10;
        pond = 10;
        lvFarm = 3;
        perLv = 40;
        joined = true;
        invLoaded = true;
    }
}
