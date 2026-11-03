package avatar.Farm;

import java.util.HashMap;
import java.util.Map;

/**
 * [PORT] Sinh tự động bởi tools/sync_farm_data.py từ farm_info.dat (client).
 * Công thức nấu ăn: id món -> {productID, cookTime(phút), materials[][2]}.
 * Nguyên liệu là id nông sản trong kho (sản phẩm vật nuôi mang id = species).
 */
public final class FarmFoodData {
    public static final class Food {
        public final int productId;
        public final int cookMinutes;
        public final int[][] materials;

        Food(int productId, int cookMinutes, int[][] materials) {
            this.productId = productId;
            this.cookMinutes = cookMinutes;
            this.materials = materials;
        }
    }

    public static final Map<Integer, Food> FOODS = new HashMap<>();
    static {
        FOODS.put(1, new Food(205, 60, new int[][]{ { 50, 12 }, { 6, 720 } }));
        FOODS.put(2, new Food(206, 60, new int[][]{ { 51, 20 }, { 6, 720 } }));
        FOODS.put(3, new Food(207, 30, new int[][]{ { 6, 1440 } }));
        FOODS.put(4, new Food(208, 60, new int[][]{ { 6, 1440 }, { 50, 12 } }));
        FOODS.put(5, new Food(209, 60, new int[][]{ { 6, 1440 }, { 1, 108 } }));
        FOODS.put(6, new Food(210, 60, new int[][]{ { 2, 338 }, { 4, 480 } }));
        FOODS.put(7, new Food(211, 60, new int[][]{ { 7, 1440 } }));
        FOODS.put(8, new Food(212, 60, new int[][]{ { 51, 72 }, { 215, 96 } }));
        FOODS.put(9, new Food(213, 60, new int[][]{ { 3, 276 }, { 2, 330 } }));
        FOODS.put(10, new Food(214, 180, new int[][]{ { 9, 756 } }));
        FOODS.put(11, new Food(216, 4320, new int[][]{ { 10, 324 }, { 5, 145 }, { 9, 378 } }));
        FOODS.put(12, new Food(217, 1440, new int[][]{ { 212, 1 }, { 218, 1 } }));
        FOODS.put(13, new Food(218, 60, new int[][]{ { 8, 378 }, { 2, 330 }, { 3, 276 }, { 7, 720 } }));
        FOODS.put(14, new Food(220, 60, new int[][]{ { 215, 240 } }));
        FOODS.put(19, new Food(260, 30, new int[][]{ { 56, 100 } }));
        FOODS.put(18, new Food(262, 180, new int[][]{ { 51, 100 } }));
        FOODS.put(17, new Food(265, 60, new int[][]{ { 218, 1 }, { 51, 50 } }));
        FOODS.put(16, new Food(259, 120, new int[][]{ { 253, 500 }, { 51, 50 }, { 262, 1 } }));
        FOODS.put(15, new Food(264, 120, new int[][]{ { 252, 1000 } }));
        FOODS.put(20, new Food(261, 30, new int[][]{ { 262, 1 } }));
        FOODS.put(22, new Food(257, 30, new int[][]{ { 254, 2 } }));
        FOODS.put(23, new Food(258, 30, new int[][]{ { 255, 2 } }));
        FOODS.put(24, new Food(256, 240, new int[][]{ { 6, 2000 }, { 50, 100 }, { 221, 5 } }));
        FOODS.put(25, new Food(263, 1440, new int[][]{ { 6, 5000 }, { 221, 5 }, { 254, 1 }, { 255, 1 }, { 50, 200 } }));
        FOODS.put(26, new Food(272, 120, new int[][]{ { 3, 1000 } }));
        FOODS.put(27, new Food(273, 240, new int[][]{ { 51, 100 }, { 50, 400 } }));
        FOODS.put(28, new Food(274, 60, new int[][]{ { 271, 500 }, { 50, 100 } }));
        FOODS.put(29, new Food(275, 60, new int[][]{ { 200, 1500 }, { 50, 300 } }));
        FOODS.put(30, new Food(281, 360, new int[][]{ { 0, 1000 } }));
        FOODS.put(33, new Food(287, 30, new int[][]{ { 286, 500 } }));
    }

    private FarmFoodData() {
    }
}
