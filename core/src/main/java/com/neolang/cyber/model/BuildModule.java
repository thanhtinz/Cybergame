package com.neolang.cyber.model;

/** Module xây dựng trong pod nhà ở. Mọi module chiếm 1 ô, trừ POD_TRON chiếm 2 ô ngang. */
public enum BuildModule {
    POD_TRON("Vỏ Pod Tròn", 300, 2),
    GIUONG("Giường Ngủ", 120, 1),
    BEP("Bếp Điện", 150, 1),
    XUONG("Xưởng Máy", 250, 1),
    TRAM_SAC("Trạm Sạc", 180, 1),
    DOCK_PET("Ổ Pet Máy", 200, 1),
    DEN_MACH("Đèn Mạch", 60, 1),
    BAN_HOLO("Bàn Holo", 100, 1),
    CHAU_CAY("Chậu Cây", 80, 1);

    /** Cấp tối đa của mọi module. */
    public static final int MAX_LEVEL = 3;

    public final String displayName;
    /** Chi phí xây bằng Xu Lá. */
    public final int cost;
    /** Số ô ngang chiếm trên lưới. */
    public final int size;
    public final int maxLevel = MAX_LEVEL;

    BuildModule(String displayName, int cost, int size) {
        this.displayName = displayName;
        this.cost = cost;
        this.size = size;
    }
}
