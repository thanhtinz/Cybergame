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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trạng thái + dữ liệu NHÀ phía client (đợt 4), port theo HouseScr/
 * HomeMsgHandler/MapItem của client gốc (docs/house-spec.md).
 *
 * Nhà không phải map trong maps.json: lưới ô 28x19 do server gửi trong gói
 * −65 (hoặc bản mặc định house-default.json khi chơi offline — chính là
 * map_data mặc định của Session.BuyHouse phía server). Gạch vẽ bằng atlas
 * riêng assets/tiles/house.png (41 frame 48px, numW = 21: tile < 21 là sàn
 * type 80, >= 21 là tường type 88; tile 40 ở hàng cuối = ô cửa ra type 21).
 *
 * Nội thất là catalog MapItemType riêng (70 loại, assets/data/houseitems.json
 * giải mã từ map_item_type.dat — cùng nguồn với HouseItemData phía server).
 * Ảnh món là file object của gốc: assets/house/img/<imgID>.png, vẽ tại
 * (xCell*24 + dx, yCell*24 + dy) đơn vị medium như MapItem.paint của gốc
 * (nhánh TYPEMAP nhà dùng getImgIcon = ảnh object nguyên file).
 */
public class HouseWorld {

    /** Một loại nội thất trong catalog MapItemType. */
    public static class ItemDef {
        public int id;
        public String group = "";   // phần trước dấu ':' của name
        public String name = "";    // phần sau dấu ':' (hoặc cả name nếu không có ':')
        public String des = "";
        public int img, icon, dx, dy;
        public int priceXu, priceLuong;
        public int buy;             // 0 không bán, 1/3 sàn, 2/4 treo tường, 6 đặc biệt
        public int[][] pos = new int[0][]; // footprint (ô phụ bị chiếm, gồm [0,0])
    }

    /** Một món nội thất đã bày trong nhà. */
    public static class Placed {
        public int typeId, x, y, dir; // x/y = chỉ số ô; dir 0 thường, 2 lật ngang
        public Placed(int typeId, int x, int y, int dir) {
            this.typeId = typeId; this.x = x; this.y = y; this.dir = dir;
        }
    }

    public static final int NUM_W = 21;      // ngưỡng sàn/tường của atlas gạch
    public static final int TILE_DOOR = 40;  // frame cuối atlas = ô cửa ra

    private AvatarDkGame game;
    private boolean dataLoaded;

    public final Map<Integer, ItemDef> defs = new HashMap<>();
    /** Nhóm nội thất theo thứ tự xuất hiện trong catalog (cho panel mua). */
    public final LinkedHashMap<String, List<ItemDef>> groups = new LinkedHashMap<>();

    public boolean joined;
    public int houseType;
    public int ownerId = -1;
    public byte[] tiles;             // lưới tile, hàng 0 = hàng trên cùng
    public int w, h;
    public final List<Placed> items = new ArrayList<>();
    /** Type ô đã suy (80 sàn / 88 chặn / 21 cửa / 79 ghế / 67 giường / 111 hoa / 112 hồ). */
    public int[] types;

    private Texture tileTex;
    private TextureRegion[] tileRegions;
    private final Map<Integer, Texture> imgCache = new HashMap<>();
    private final Map<Integer, TextureRegion> regionCache = new HashMap<>();

    /** Nạp catalog + atlas (gọi một lần, lười). */
    public void load(AvatarDkGame g) {
        if (dataLoaded) return;
        dataLoaded = true;
        game = g;
        try {
            JsonValue root = new JsonReader().parse(
                GameFiles.data("data/houseitems.json").readString("UTF-8"));
            for (JsonValue t : root) {
                ItemDef d = new ItemDef();
                d.id = t.getInt("id");
                String full = t.getString("name");
                int colon = full.indexOf(':');
                if (colon >= 0) {
                    d.group = full.substring(0, colon);
                    d.name = full.substring(colon + 1);
                } else {
                    d.group = "linh tinh";
                    d.name = full;
                }
                d.des = t.getString("des", "");
                d.img = t.getInt("img");
                d.icon = t.getInt("icon");
                d.dx = t.getInt("dx");
                d.dy = t.getInt("dy");
                d.priceXu = t.getInt("priceXu");
                d.priceLuong = t.getInt("priceLuong");
                d.buy = t.getInt("buy");
                JsonValue pos = t.get("pos");
                if (pos != null) {
                    d.pos = new int[pos.size][2];
                    for (int i = 0; i < pos.size; i++) {
                        d.pos[i][0] = pos.get(i).getInt(0);
                        d.pos[i][1] = pos.get(i).getInt(1);
                    }
                }
                if (d.pos.length == 0) d.pos = new int[][]{{0, 0}};
                defs.put(d.id, d);
                groups.computeIfAbsent(d.group, k -> new ArrayList<>()).add(d);
            }
        } catch (Exception e) {
            // catalog hỏng: nhà vẫn vào được, chỉ không mua được đồ
        }
        tileTex = game.texture("tiles/house.png");
        int frame = 24 * AvatarDkGame.HD;
        int nFrame = tileTex.getHeight() / frame; // 41
        tileRegions = new TextureRegion[Math.max(nFrame, TILE_DOOR + 1)];
        for (int v = 0; v < tileRegions.length; v++) {
            int col = v / nFrame, row = v % nFrame;
            if ((col + 1) * frame > tileTex.getWidth()
                || (row + 1) * frame > tileTex.getHeight()) continue;
            tileRegions[v] = new TextureRegion(tileTex, col * frame, row * frame, frame, frame);
        }
    }

    public TextureRegion tileRegion(int v) {
        if (tileRegions == null || v < 0 || v >= tileRegions.length) return null;
        return tileRegions[v];
    }

    /** Ảnh nguyên file của một món nội thất (object của gốc). */
    public Texture imgTexture(int imgId) {
        if (imgCache.containsKey(imgId)) return imgCache.get(imgId);
        Texture t = null;
        String path = "house/img/" + imgId + ".png";
        if (GameFiles.data(path).exists()) t = game.texture(path);
        imgCache.put(imgId, t);
        return t;
    }

    /** Region của ảnh món, lật ngang nếu dir == 2 (transform của gốc). */
    public TextureRegion imgRegion(int imgId, boolean flip) {
        int key = flip ? -imgId - 1 : imgId;
        TextureRegion r = regionCache.get(key);
        if (r != null) return r;
        Texture t = imgTexture(imgId);
        if (t == null) return null;
        r = new TextureRegion(t);
        if (flip) r.flip(true, false);
        regionCache.put(key, r);
        return r;
    }

    // ---------- Dựng cảnh ----------

    /** Gói −65 JOIN_HOUSE (HomeMsgHandler.cs:48-72 của gốc). */
    public void parseJoin(Msg m) throws IOException {
        DataInputStream r = m.reader();
        houseType = r.readByte();
        ownerId = r.readInt(); // server fan ghi id người vào (đúng hiện trạng gốc)
        int n = r.readShort();
        byte[] map = new byte[n];
        for (int i = 0; i < n; i++) map[i] = r.readByte();
        w = r.readByte() & 0xFF;
        h = w > 0 ? n / w : 0;
        tiles = map;
        items.clear();
        if (r.available() >= 2) {
            int nItem = r.readShort();
            for (int i = 0; i < nItem; i++) {
                items.add(new Placed(r.readShort(), r.readByte(), r.readByte(), r.readByte()));
            }
        }
        joined = true;
        recomputeTypes();
    }

    /** Nhà mặc định khi chơi offline: đúng map_data của Session.BuyHouse. */
    public void loadDefault() {
        try {
            JsonValue root = new JsonReader().parse(
                GameFiles.data("data/house-default.json").readString("UTF-8"));
            tiles = new byte[root.size];
            for (int i = 0; i < root.size; i++) tiles[i] = (byte) root.getInt(i);
        } catch (Exception e) {
            tiles = new byte[28 * 19];
        }
        w = 28;
        h = tiles.length / w;
        houseType = 3;
        items.clear();
        joined = true;
        recomputeTypes();
    }

    public void clear() {
        joined = false;
        tiles = null;
        types = null;
        items.clear();
    }

    /** Ô cửa ra: tile 40 ở hàng cuối (gốc thay tile bằng ô trên nó khi vẽ). */
    public boolean isDoorCell(int cx, int cy) {
        return tiles != null && cy == h - 1 && cx >= 0 && cx < w
            && (tiles[cy * w + cx] & 0xFF) == TILE_DOOR;
    }

    /** Tile dùng để VẼ một ô (ô cửa vẽ bằng tile của ô ngay trên nó). */
    public int drawTile(int cx, int cy) {
        int v = tiles[cy * w + cx] & 0xFF;
        if (v == TILE_DOOR && cy == h - 1 && cy > 0) {
            return tiles[(cy - 1) * w + cx] & 0xFF;
        }
        return v;
    }

    /** Cột giữa của dải ô cửa (điểm spawn khi vào nhà), đơn vị ô. */
    public float doorCenterCell() {
        int first = -1, last = -1;
        for (int cx = 0; cx < w; cx++) {
            if (isDoorCell(cx, h - 1)) {
                if (first < 0) first = cx;
                last = cx;
            }
        }
        return first < 0 ? w / 2f : (first + last + 1) / 2f;
    }

    /** Suy type ô từ tile + footprint nội thất (setType của HouseScr gốc). */
    public void recomputeTypes() {
        if (tiles == null) { types = null; return; }
        types = new int[w * h];
        for (int i = 0; i < tiles.length; i++) {
            int v = tiles[i] & 0xFF;
            types[i] = v < NUM_W ? 80 : 88;
        }
        for (int cx = 0; cx < w; cx++) {
            if (isDoorCell(cx, h - 1)) types[(h - 1) * w + cx] = 21;
        }
        for (Placed p : items) applyItemType(p, types);
    }

    private void applyItemType(Placed p, int[] t) {
        ItemDef d = defs.get(p.typeId);
        if (d == null) return;
        int cellType = 88;
        if (p.typeId == 68) cellType = 112;       // hồ
        else if (p.typeId == 69) cellType = 111;  // hoa tình yêu
        else if (d.icon == 1) cellType = 79;      // ghế: ngồi được
        else if (d.icon == 2) cellType = 67;      // giường: nằm nghỉ
        for (int[] o : d.pos) {
            int cx = p.x + o[0], cy = p.y + o[1];
            if (cx < 0 || cy < 0 || cx >= w || cy >= h) continue;
            t[cy * w + cx] = cellType;
        }
    }

    /** Type ô tại điểm medium (hệ trên–trái như playerX/playerY). */
    public int typeAt(float mx, float my) {
        if (types == null) return -999;
        int cx = (int) (mx / 24), cy = (int) (my / 24);
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) return -999;
        return types[cy * w + cx];
    }

    public boolean canStand(float mx, float my) {
        int t = typeAt(mx, my);
        // sàn 80 + ghế 79 + giường 67 đi/ngồi được; cửa 21 không đứng (bước
        // vào là thoát, qua tryPortal) — khớp isTrans {80, 51} của gốc cộng
        // hai type tương tác của nội thất
        return t == 80 || t == 79 || t == 67;
    }

    /** Món đã bày tại ô (cx, cy) — khớp theo footprint, món vẽ sau thắng. */
    public Placed itemAtCell(int cx, int cy) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Placed p = items.get(i);
            ItemDef d = defs.get(p.typeId);
            if (d == null) continue;
            for (int[] o : d.pos) {
                if (p.x + o[0] == cx && p.y + o[1] == cy) return p;
            }
        }
        return null;
    }

    /**
     * Kiểm tra đặt được (isDisable của HouseScr): đồ sàn (buy != 2, 4) cần ô
     * đích + mọi ô footprint là sàn trống (type 80); đồ treo tường (buy 2/4)
     * cần ô đích là sàn trống và ô ngay trên là tường (88 từ tile gốc).
     * ignore = món đang di chuyển (không tự chặn chính nó).
     */
    public boolean canPlace(ItemDef d, int cx, int cy, Placed ignore) {
        if (d == null || tiles == null) return false;
        // type tĩnh theo tile (chưa tính nội thất)
        for (int[] o : d.pos) {
            int x = cx + o[0], y = cy + o[1];
            if (x < 0 || y < 0 || x >= w || y >= h) return false;
            int tile = tiles[y * w + x] & 0xFF;
            if (tile >= NUM_W) return false; // footprint phải nằm trên sàn
            if (isDoorCell(x, y)) return false;
        }
        // không trùng ô với món khác
        for (Placed p : items) {
            if (p == ignore) continue;
            ItemDef pd = defs.get(p.typeId);
            if (pd == null) continue;
            for (int[] o : d.pos) {
                for (int[] po : pd.pos) {
                    if (cx + o[0] == p.x + po[0] && cy + o[1] == p.y + po[1]) return false;
                }
            }
        }
        if (d.buy == 2 || d.buy == 4) {
            // treo tường: ô ngay trên ô đích phải là tường theo tile gốc
            if (cy <= 0) return false;
            int above = tiles[(cy - 1) * w + cx] & 0xFF;
            if (above < NUM_W) return false;
        }
        return true;
    }
}
