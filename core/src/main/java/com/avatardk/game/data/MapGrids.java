package com.avatardk.game.data;

import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.Json;

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
        private byte[] cells;

        public byte[] cells() {
            if (cells == null) cells = Base64Coder.decode(g);
            return cells;
        }

        public boolean hasStop() { return s != null && s.length >= 2; }

        /** Điểm trạm (medium, gốc trên–trái) theo đúng luật của gốc:
         *  x = tx*24 + 12, y = ty*24 − 12 với (tx, ty) = ô 139 cuối cùng. */
        public int stopX() { return s[s.length - 2] * 24 + 12; }
        public int stopY() { return s[s.length - 1] * 24 - 12; }
        public int stopRow() { return s[s.length - 1]; }
        /** Ô 139 đầu tiên của dải trạm (đặt biển trạm ở mép trái dải). */
        public int stopFirstTileX() { return s[0]; }
    }

    private final Map<String, Grid> byId;

    @SuppressWarnings("unchecked")
    public MapGrids(String json) {
        byId = new Json().fromJson(HashMap.class, Grid.class, json);
    }

    public Grid grid(int mapId) { return byId.get(String.valueOf(mapId)); }
}
