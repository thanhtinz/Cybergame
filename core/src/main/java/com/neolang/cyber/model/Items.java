package com.neolang.cyber.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Danh mục tĩnh toàn bộ vật phẩm của game. */
public final class Items {

    public static final Map<String, ItemDef> ALL;

    static {
        Map<String, ItemDef> m = new LinkedHashMap<>();

        // ---- Thức ăn ----
        ItemDef comNam = food("com_nam", "Cơm Nấm Neon", 60, 20, 35f, 5f, 0f);
        m.put(comNam.id, comNam);
        ItemDef caPhe = food("ca_phe", "Cà Phê Mạch", 45, 15, 5f, 20f, 10f);
        m.put(caPhe.id, caPhe);
        ItemDef banhLa = food("banh_la", "Bánh Lá Năng Lượng", 30, 10, 20f, 5f, 5f);
        m.put(banhLa.id, banhLa);

        // ---- Hạt giống + nông sản ----
        putSeed(m, seed("seed_ca_chua", "Hạt Cà Chua Mạch", 25, 8, 60, "ca_chua"));
        putSeed(m, seed("seed_nam", "Hạt Nấm Neon", 20, 6, 45, "nam_neon"));
        putSeed(m, seed("seed_dau", "Hạt Dâu Hologram", 40, 12, 90, "dau_holo"));
        putSeed(m, seed("seed_bi", "Hạt Bí Ngô Pin", 70, 20, 150, "bi_pin"));

        ItemDef caChua = crop("ca_chua", "Cà Chua Mạch", 30, 10f, 0f);
        m.put(caChua.id, caChua);
        ItemDef namNeon = crop("nam_neon", "Nấm Neon", 25, 8f, 5f);
        m.put(namNeon.id, namNeon);
        ItemDef dauHolo = crop("dau_holo", "Dâu Hologram", 55, 12f, 0f);
        m.put(dauHolo.id, dauHolo);
        ItemDef biPin = crop("bi_pin", "Bí Ngô Pin", 90, 18f, 0f);
        m.put(biPin.id, biPin);

        // ---- Nguyên liệu ----
        put(m, mat("go_mach", "Gỗ Mạch", 30, 12));
        put(m, mat("linh_kien", "Linh Kiện Glitch", 120, 50));
        put(m, mat("nhua_sang", "Nhựa Sáng", 50, 20));
        put(m, mat("hat_giong_hiem", "Hạt Giống Hiếm", 300, 120));

        // ---- Trang phục ----
        put(m, outfit("bo_tan_cu_dan", "Bộ Tân Cư Dân", 0, 0, "body", 0x0E7C86, ItemDef.CURRENCY_XU_LA));
        put(m, outfit("bo_street_neon", "Bộ Street Neon", 400, 160, "body", 0x1F2430, ItemDef.CURRENCY_XU_LA));
        put(m, outfit("bo_tham_hiem", "Bộ Thám Hiểm", 3, 1, "body", 0x6B7F3E, ItemDef.CURRENCY_TINH_THE));
        put(m, outfit("mac_dinh", "Bộ Cư Dân Mới", 0, 0, "bo", 0x26A69A, ItemDef.CURRENCY_XU_LA));
        put(m, outfit("ao_khoac_xanh", "Áo Khoác Xanh Mạch", 200, 80, "ao", 0x2196F3, ItemDef.CURRENCY_XU_LA));
        put(m, outfit("bo_techwear_tim", "Bộ Techwear Tím", 450, 180, "bo", 0x9C27B0, ItemDef.CURRENCY_XU_LA));
        put(m, outfit("bo_ky_su_cam", "Bộ Kỹ Sư Cam", 12, 5, "bo", 0xFF9800, ItemDef.CURRENCY_TINH_THE));
        put(m, outfit("kinh_visor_hong", "Kính Visor Hồng", 8, 3, "kinh", 0xFF4081, ItemDef.CURRENCY_TINH_THE));

        // ---- Nội thất (liên kết module xây dựng) ----
        put(m, furniture("vo_pod", "Vỏ Pod Tròn", 300, 120, "POD_TRON"));
        put(m, furniture("noi_giuong", "Giường Ngủ Mạch", 120, 48, "GIUONG"));
        put(m, furniture("noi_bep", "Bếp Điện Mini", 150, 60, "BEP"));
        put(m, furniture("noi_xuong", "Xưởng Máy Mini", 250, 100, "XUONG"));
        put(m, furniture("noi_tram_sac", "Trạm Sạc Cá Nhân", 180, 72, "TRAM_SAC"));
        put(m, furniture("noi_dock_pet", "Ổ Pet Máy", 200, 80, "DOCK_PET"));
        put(m, furniture("noi_den", "Đèn Mạch Để Bàn", 60, 24, "DEN_MACH"));
        put(m, furniture("noi_ban", "Bàn Holo", 100, 40, "BAN_HOLO"));
        put(m, furniture("noi_chau", "Chậu Cây Thủy Canh", 80, 32, "CHAU_CAY"));

        // ---- Đồ cho pet ----
        ItemDef pinPet = new ItemDef("pin_pet", "Pin Cho Pet", ItemType.PET, 40, 16);
        pinPet.batteryCharge = 20f;
        put(m, pinPet);
        put(m, new ItemDef("do_choi_pet", "Đồ Chơi Cho Pet", ItemType.PET, 35, 14));

        ALL = Collections.unmodifiableMap(m);
    }

    private Items() {
    }

    public static ItemDef get(String id) {
        return id == null ? null : ALL.get(id);
    }

    // ---- helpers tạo ItemDef ----

    private static void put(Map<String, ItemDef> m, ItemDef d) {
        m.put(d.id, d);
    }

    private static void putSeed(Map<String, ItemDef> m, ItemDef d) {
        m.put(d.id, d);
    }

    private static ItemDef food(String id, String name, int buy, int sell,
                                 float satiety, float warmth, float battery) {
        ItemDef d = new ItemDef(id, name, ItemType.FOOD, buy, sell);
        d.foodSatiety = satiety;
        d.foodWarmth = warmth;
        d.batteryCharge = battery;
        return d;
    }

    private static ItemDef seed(String id, String name, int buy, int sell, int growSeconds, String cropId) {
        ItemDef d = new ItemDef(id, name, ItemType.SEED, buy, sell);
        d.growSeconds = growSeconds;
        d.cropId = cropId;
        return d;
    }

    private static ItemDef crop(String id, String name, int sell, float satiety, float warmth) {
        ItemDef d = new ItemDef(id, name, ItemType.CROP, 0, sell);
        d.foodSatiety = satiety;
        d.foodWarmth = warmth;
        return d;
    }

    private static ItemDef mat(String id, String name, int buy, int sell) {
        return new ItemDef(id, name, ItemType.MATERIAL, buy, sell);
    }

    private static ItemDef outfit(String id, String name, int buy, int sell,
                                  String slot, int suitColor, int currency) {
        ItemDef d = new ItemDef(id, name, ItemType.OUTFIT, buy, sell);
        d.outfitSlot = slot;
        d.suitColor = suitColor;
        d.currency = currency;
        return d;
    }

    private static ItemDef furniture(String id, String name, int buy, int sell, String buildModule) {
        ItemDef d = new ItemDef(id, name, ItemType.FURNITURE, buy, sell);
        d.buildModule = buildModule;
        return d;
    }
}
