package avatar.Home;

/** Catalog nội thất (MapItemType) — SINH TỰ ĐỘNG bằng tools/sync_house_data.py
 *  từ assets/data/houseitems.json (giải mã map_item_type.dat của gốc).
 *  Đừng sửa tay. priceXu đã nhân 1000 như client gốc hiển thị; 0 = không bán bằng loại tiền đó. */
public final class HouseItemData {
    public static final int COUNT = 70;
    public static final int[] PRICE_XU = {0, 0, 0, 60000, 0, 40000, 60000, 0, 0, 50000, 0, 30000, 40000, 0, 40000, 20000, 20000, 20000, 25000, 0, 0, 0, 0, 60000, 120000, 0, 0, 0, 40000, 0, 0, 40000, 20000, 0, 40000, 30000, 25000, 25000, 0, 0, 0, 55000, 0, 30000, 0, 0, 75000, 0, 0, 0, 70000, 0, 15000, 15000, 30000, 30000, 30000, 0, 26000, 32000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] PRICE_LUONG = {0, 0, 0, 20, 25, 15, 20, 30, 25, 17, 35, 10, 15, 35, 15, 5, 5, 5, 10, 20, 20, 20, 30, 20, 40, 100, 35, 35, 15, 25, 25, 15, 5, 5, 15, 10, 7, 7, 10, 20, 20, 20, 10, 10, 10, 73, 30, 0, 120, 50, 28, 10, 10, 10, 16, 16, 16, 7, 7, 6, 12, 10, 28, 25, 10, 73, 52, 28, 58, 0};
    public static final byte[] BUY = {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 4, 3, 3, 3, 3, 3, 4, 3, 3, 6};

    private HouseItemData() {}

    public static int priceXu(int id) {
        return id >= 0 && id < COUNT ? PRICE_XU[id] : -1;
    }

    public static int priceLuong(int id) {
        return id >= 0 && id < COUNT ? PRICE_LUONG[id] : -1;
    }
}
