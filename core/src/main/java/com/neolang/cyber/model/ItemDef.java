package com.neolang.cyber.model;

/**
 * Định nghĩa tĩnh của một vật phẩm. Mọi trường đều public để UI đọc trực tiếp.
 *
 * currency: 0 = Xu Lá, 1 = Tinh Thể (chỉ dùng cho giá mua/bán).
 * outfitSlot / suitColor: chỉ có ý nghĩa với OUTFIT.
 * buildModule: chỉ có ý nghĩa với FURNITURE, là tên enum BuildModule liên kết.
 * cropId / growSeconds: chỉ có ý nghĩa với SEED.
 */
public class ItemDef {
    /** Đơn vị tiền tệ: Xu Lá. */
    public static final int CURRENCY_XU_LA = 0;
    /** Đơn vị tiền tệ: Tinh Thể. */
    public static final int CURRENCY_TINH_THE = 1;

    public String id;
    public String name;
    public ItemType type;
    public int buyPrice;
    public int sellPrice;
    /** Lượng hồi độ no khi ăn/dùng (FOOD, một phần CROP). */
    public float foodSatiety;
    /** Lượng hồi độ ấm khi ăn/dùng. */
    public float foodWarmth;
    /** Lượng hồi pin khi dùng. */
    public float batteryCharge;
    /** Thời gian trồng, giây (chỉ SEED). */
    public int growSeconds;
    /** Id nông sản thu hoạch được (chỉ SEED). */
    public String cropId;
    /** Vị trí mặc: "ao", "bo", "kinh"... (chỉ OUTFIT). */
    public String outfitSlot;
    /** Màu bộ đồ, RGB hex (chỉ OUTFIT). */
    public int suitColor;
    /** 0 = Xu Lá, 1 = Tinh Thể. */
    public int currency;
    /** Tên enum BuildModule liên kết (chỉ FURNITURE), null nếu không có. */
    public String buildModule;

    public ItemDef(String id, String name, ItemType type, int buyPrice, int sellPrice) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.foodSatiety = 0f;
        this.foodWarmth = 0f;
        this.batteryCharge = 0f;
        this.growSeconds = 0;
        this.cropId = null;
        this.outfitSlot = null;
        this.suitColor = 0;
        this.currency = CURRENCY_XU_LA;
        this.buildModule = null;
    }
}
