package com.avatardk.game.data;

import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

/** Lưới ô dữ liệu gốc của từng map (trích từ Resources/map/&lt;id&gt;.bytes|.txt
 *  của client gốc vào assets/data/mapgrids.json; xem docs/mapgrid-spec.md).
 *  Mỗi ô là 1 byte mã thuộc tính/vật thể; LoadMap.setMap của gốc quét lưới
 *  này để đặt vật thể lên map và tìm điểm đặc biệt. Mã 139 = ô TRẠM XE BUÝT:
 *  gốc gán Bus.posBusStop cho từng ô 139 nên ô quét sau cùng (thứ tự hàng
 *  ngang, trái→phải, trên→dưới) là điểm trạm hiệu lực. */
public class MapGrids {

    public static class Grid {
        /** Chiều rộng lưới (số ô ngang) = cỡ file / Hmap. */
        public int w;
        /** Lưới ô thô, base64. */
        public String g;
        /** Các ô 139 dẹt [x0, y0, x1, y1, ...] theo thứ tự quét; null = không có trạm. */
        public int[] s;
        /** Vật thể gắn map đã mô phỏng theo LoadMap.setMap: mỗi phần tử
         *  [imgId, anchorX, anchorY] đơn vị medium, neo đáy–giữa (anchor 33
         *  của gốc); id âm = SubObject trang trại. Xem docs/mapgrid-spec.md. */
        public int[][] o;
        /** Type ô (sbyte) của LoadMap sau setMap, base64 — trích cùng đợt
         *  vật thể (docs/mapobjects-research.md mục 7). Di chuyển tự do
         *  của gốc (Base.detectCollision → LoadMap.isTrans) chỉ cho
         *  đứng lên type 80 và 51; mọi type khác đều chặn. */
        public String t;
        /** Tile VẼ (sbyte) của từng ô SAU khi setMap đã đổi các ô marker
         *  thành tile nền (docs/tile-engine-spec.md mục 6.2), base64 —
         *  dùng cho các map đi đường tile (không có dải imagemap):
         *  ô -1 = không vẽ, còn lại 0..125 là chỉ số frame trong atlas
         *  tile (frame = v/18 cột, v%18 hàng). */
        public String d;
        private byte[] cells;
        private byte[] types;
        private byte[] drawTiles;

        public byte[] cells() {
            if (cells == null) cells = Base64Coder.decode(g);
            return cells;
        }

        public byte[] drawTiles() {
            if (drawTiles == null && d != null) drawTiles = Base64Coder.decode(d);
            return drawTiles;
        }

        public byte[] types() {
            if (types == null && t != null) types = Base64Coder.decode(t);
            return types;
        }

        public boolean hasStop() { return s != null && s.length >= 2; }

        /** Điểm trạm (medium, gốc trên–trái) theo đúng luật của gốc:
         *  x = tx*24 + 12, y = ty*24 − 12 với (tx, ty) = ô 139 cuối cùng. */
        public int stopX() { return s[s.length - 2] * 24 + 12; }
        public int stopY() { return s[s.length - 1] * 24 - 12; }
        public int stopRow() { return s[s.length - 1]; }
        /** Ô 139 đầu tiên của dải trạm. */
        public int stopFirstTileX() { return s[0]; }
        /** Ô 139 cuối cùng của dải trạm. */
        public int stopLastTileX() { return s[s.length - 2]; }

        /**
         * Neo của vật thể 835 (tấm biển TeaM mà người chơi quen gọi là
         * trạm xe): gốc đặt tại ô 152 đầu tiên qua addObjTree — tâm x =
         * x(ô đầu) + bề rộng dải ô 152 / 2, đáy y = đáy hàng ô − 4
         * (đơn vị medium). Trả null nếu map không có ô 152.
         */
        public int[] boardAnchor() {
            byte[] cells = cells();
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == (byte) 152) {
                    int tx = i % w, ty = i / w, run = 0;
                    while (i + run < cells.length && cells[i + run] == (byte) 152
                        && (i + run) / w == ty) {
                        run++;
                    }
                    return new int[] { tx * 24 + run * 12, (ty + 1) * 24 - 4 };
                }
            }
            return null;
        }
    }

    private final Map<String, Grid> byId;

    public MapGrids(String json) {
        // Đọc bằng JsonReader (không dùng Json.fromJson phản chiếu —
        // GWT không có bảng kiểu, cùng lý do với parseMapDefs).
        byId = new HashMap<>();
        JsonValue root = new JsonReader().parse(json);
        for (JsonValue r = root.child; r != null; r = r.next) {
            Grid g = new Grid();
            g.w = r.getInt("w", 0);
            g.g = r.getString("g", null);
            g.t = r.getString("t", null);
            g.d = r.getString("d", null);
            JsonValue sv = r.get("s");
            if (sv != null && sv.isArray()) {
                java.util.List<Integer> tmp = new java.util.ArrayList<>();
                for (JsonValue v = sv.child; v != null; v = v.next) tmp.add(v.asInt());
                g.s = new int[tmp.size()];
                for (int i = 0; i < tmp.size(); i++) g.s[i] = tmp.get(i);
            }
            JsonValue ov = r.get("o");
            if (ov != null && ov.isArray()) {
                java.util.List<int[]> rows = new java.util.ArrayList<>();
                for (JsonValue row = ov.child; row != null; row = row.next) {
                    java.util.List<Integer> tmp = new java.util.ArrayList<>();
                    for (JsonValue v = row.child; v != null; v = v.next) tmp.add(v.asInt());
                    int[] arr = new int[tmp.size()];
                    for (int i = 0; i < tmp.size(); i++) arr[i] = tmp.get(i);
                    rows.add(arr);
                }
                g.o = rows.toArray(new int[0][]);
            }
            byId.put(r.name, g);
        }
    }

    public Grid grid(int mapId) { return byId.get(String.valueOf(mapId)); }
}
