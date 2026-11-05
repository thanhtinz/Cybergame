package avatar.Farm;

import java.util.HashMap;
import java.util.Map;

/**
 * [PORT] Sinh tự động bởi tools/sync_farm_data.py từ farm_info.dat (client)
 * — nguồn chuẩn duy nhất cho dữ liệu farm (lệnh đồng bộ của Tín 2026-11-03).
 * PRODUCT_ID: cây động (id >= 30) thu hoạch ra sản phẩm có id riêng;
 * cây tĩnh thu ra sản phẩm trùng id cây. SELL_PRICE_XU: giá bán mọi nông
 * sản theo bảng sản phẩm của dat (cây = 3 xu, món nấu = giá niêm yết dat).
 */
public final class FarmProductData {
    public static final Map<Integer, Integer> PRODUCT_ID = new HashMap<>();
    public static final Map<Integer, Integer> SELL_PRICE_XU = new HashMap<>();
    static {
        PRODUCT_ID.put(30, 200);
        PRODUCT_ID.put(31, 201);
        PRODUCT_ID.put(32, 202);
        PRODUCT_ID.put(33, 203);
        PRODUCT_ID.put(34, 204);
        PRODUCT_ID.put(35, 248);
        PRODUCT_ID.put(36, 249);
        PRODUCT_ID.put(37, 252);
        PRODUCT_ID.put(38, 253);
        PRODUCT_ID.put(39, 266);
        PRODUCT_ID.put(40, 267);
        PRODUCT_ID.put(41, 270);
        PRODUCT_ID.put(42, 271);
        PRODUCT_ID.put(43, 286);
        SELL_PRICE_XU.put(0, 3);
        SELL_PRICE_XU.put(1, 3);
        SELL_PRICE_XU.put(2, 3);
        SELL_PRICE_XU.put(3, 3);
        SELL_PRICE_XU.put(4, 3);
        SELL_PRICE_XU.put(5, 3);
        SELL_PRICE_XU.put(6, 3);
        SELL_PRICE_XU.put(7, 3);
        SELL_PRICE_XU.put(8, 3);
        SELL_PRICE_XU.put(9, 3);
        SELL_PRICE_XU.put(10, 3);
        SELL_PRICE_XU.put(50, 30);
        SELL_PRICE_XU.put(51, 60);
        SELL_PRICE_XU.put(55, 90);
        SELL_PRICE_XU.put(56, 30);
        SELL_PRICE_XU.put(200, 3);
        SELL_PRICE_XU.put(201, 3);
        SELL_PRICE_XU.put(202, 3);
        SELL_PRICE_XU.put(203, 3);
        SELL_PRICE_XU.put(204, 3);
        SELL_PRICE_XU.put(205, 5880);
        SELL_PRICE_XU.put(206, 6160);
        SELL_PRICE_XU.put(207, 6211);
        SELL_PRICE_XU.put(208, 8040);
        SELL_PRICE_XU.put(209, 8244);
        SELL_PRICE_XU.put(210, 6030);
        SELL_PRICE_XU.put(211, 7920);
        SELL_PRICE_XU.put(212, 12096);
        SELL_PRICE_XU.put(213, 5418);
        SELL_PRICE_XU.put(214, 10908);
        SELL_PRICE_XU.put(215, 10);
        SELL_PRICE_XU.put(216, 32000);
        SELL_PRICE_XU.put(217, 30000);
        SELL_PRICE_XU.put(218, 8000);
        SELL_PRICE_XU.put(219, 5724);
        SELL_PRICE_XU.put(220, 4560);
        SELL_PRICE_XU.put(221, 0);
        SELL_PRICE_XU.put(222, 0);
        SELL_PRICE_XU.put(223, 0);
        SELL_PRICE_XU.put(224, 0);
        SELL_PRICE_XU.put(225, 0);
        SELL_PRICE_XU.put(229, 0);
        SELL_PRICE_XU.put(230, 0);
        SELL_PRICE_XU.put(231, 0);
        SELL_PRICE_XU.put(232, 0);
        SELL_PRICE_XU.put(233, 0);
        SELL_PRICE_XU.put(234, 0);
        SELL_PRICE_XU.put(235, 0);
        SELL_PRICE_XU.put(236, 0);
        SELL_PRICE_XU.put(237, 0);
        SELL_PRICE_XU.put(238, 0);
        SELL_PRICE_XU.put(239, 0);
        SELL_PRICE_XU.put(240, 0);
        SELL_PRICE_XU.put(241, 0);
        SELL_PRICE_XU.put(242, 0);
        SELL_PRICE_XU.put(243, 0);
        SELL_PRICE_XU.put(244, 0);
        SELL_PRICE_XU.put(245, 0);
        SELL_PRICE_XU.put(246, 0);
        SELL_PRICE_XU.put(247, 0);
        SELL_PRICE_XU.put(248, 3);
        SELL_PRICE_XU.put(249, 3);
        SELL_PRICE_XU.put(250, 0);
        SELL_PRICE_XU.put(251, 0);
        SELL_PRICE_XU.put(252, 3);
        SELL_PRICE_XU.put(253, 3);
        SELL_PRICE_XU.put(254, 0);
        SELL_PRICE_XU.put(255, 0);
        SELL_PRICE_XU.put(256, 30000);
        SELL_PRICE_XU.put(257, 20000);
        SELL_PRICE_XU.put(258, 10000);
        SELL_PRICE_XU.put(259, 25000);
        SELL_PRICE_XU.put(260, 5000);
        SELL_PRICE_XU.put(261, 12500);
        SELL_PRICE_XU.put(262, 18000);
        SELL_PRICE_XU.put(263, 6464);
        SELL_PRICE_XU.put(264, 7000);
        SELL_PRICE_XU.put(265, 17000);
        SELL_PRICE_XU.put(266, 3);
        SELL_PRICE_XU.put(267, 3);
        SELL_PRICE_XU.put(268, 0);
        SELL_PRICE_XU.put(269, 0);
        SELL_PRICE_XU.put(270, 3);
        SELL_PRICE_XU.put(271, 3);
        SELL_PRICE_XU.put(272, 10000);
        SELL_PRICE_XU.put(273, 32000);
        SELL_PRICE_XU.put(274, 20000);
        SELL_PRICE_XU.put(275, 19000);
        SELL_PRICE_XU.put(276, 0);
        SELL_PRICE_XU.put(277, 0);
        SELL_PRICE_XU.put(278, 0);
        SELL_PRICE_XU.put(279, 0);
        SELL_PRICE_XU.put(280, 0);
        SELL_PRICE_XU.put(281, 20000);
        SELL_PRICE_XU.put(282, 0);
        SELL_PRICE_XU.put(283, 0);
        SELL_PRICE_XU.put(284, 0);
        SELL_PRICE_XU.put(285, 0);
        SELL_PRICE_XU.put(286, 3);
        SELL_PRICE_XU.put(287, 3000);
        SELL_PRICE_XU.put(288, 120); // [PORT-ONG] Mật Ong (docs/farmlvl2-spec.md)
    }

    private FarmProductData() {
    }

    public static int productIdOf(int treeId) {
        return PRODUCT_ID.getOrDefault(treeId, treeId);
    }

    /** @return giá bán xu của 1 đơn vị nông sản, -1 nếu dat không niêm yết. */
    public static int sellPriceOf(int productId) {
        return SELL_PRICE_XU.getOrDefault(productId, -1);
    }
}
