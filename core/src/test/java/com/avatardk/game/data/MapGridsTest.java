package com.avatardk.game.data;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm chứng trích điểm trạm xe buýt từ lưới ô (docs/mapgrid-spec.md):
 *  ô 139 cuối cùng theo thứ tự quét thắng, đúng luật Bus.posBusStop của gốc. */
class MapGridsTest {

    private static String json() {
        String g24 = Base64.getEncoder().encodeToString(new byte[]{0, 0, (byte) 139, (byte) 139});
        String g10 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        return "{\"24\":{\"w\":4,\"g\":\"" + g24 + "\",\"s\":[2,0,3,0]},"
            + "\"10\":{\"w\":3,\"g\":\"" + g10 + "\"}}";
    }

    @Test
    void stopPointUsesLastStopTile() {
        MapGrids grids = new MapGrids(json());
        MapGrids.Grid g = grids.grid(24);
        assertTrue(g.hasStop());
        assertEquals(3 * 24 + 12, g.stopX());
        assertEquals(-12, g.stopY());
        assertEquals(0, g.stopRow());
        assertEquals(2, g.stopFirstTileX());
        assertEquals(4, g.cells().length);
        assertEquals((byte) 139, g.cells()[2]);
    }

    @Test
    void mapWithoutStop() {
        MapGrids grids = new MapGrids(json());
        assertFalse(grids.grid(10).hasStop());
        assertNull(grids.grid(77));
    }
}
