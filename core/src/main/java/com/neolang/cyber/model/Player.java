package com.neolang.cyber.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Người chơi. Tạo nhân vật chỉ gồm giới tính + tên + ngoại hình;
 * gameClass luôn khởi tạo NONE, nghề thật chỉ gán qua Trạm Định Hướng
 * (xem {@link ClassChoice}).
 */
public class Player {
    /** HP cộng thêm mỗi cấp. */
    public static final int HP_PER_LEVEL = 12;
    /** ATK cộng thêm mỗi cấp. */
    public static final int ATK_PER_LEVEL = 2;
    /** DEF cộng thêm mỗi cấp. */
    public static final int DEF_PER_LEVEL = 1;
    /** Tỉ lệ HP hồi khi lên cấp. */
    public static final float LEVEL_UP_HEAL_RATIO = 0.30f;
    /** Xu Lá khởi đầu. */
    public static final int START_XU_LA = 150;
    /** Tinh Thể khởi đầu. */
    public static final int START_TINH_THE = 5;

    /** Bảng màu mắt: 0 = Nâu Hạt Dẻ, 1 = Cyan Mạch Điện, 2 = Tím Neon. */
    public static final int[] EYE_COLORS = {0x6B4226, 0x22D3EE, 0xA855F7};
    /** Ba tông da cho tab Da ở màn tạo nhân vật: sáng / trung bình / ngăm. */
    public static final int[] SKIN_COLORS = {0xFFDCB8, 0xF2BC8D, 0xC98A54};
    public static final String[] SKIN_NAMES = {"Sáng", "Trung Bình", "Ngăm"};
    /** Tên hiển thị của các màu mắt, theo chỉ số EYE_COLORS. */
    public static final String[] EYE_COLOR_NAMES = {"Nâu Hạt Dẻ", "Cyan Mạch Điện", "Tím Neon"};
    /** Bảng màu tóc (6 màu). */
    public static final int[] HAIR_COLORS = {0x263238, 0x6D4C41, 0xF9A825, 0xE53935, 0x8E24AA, 0x00ACC1};
    /** Bảng màu bộ đồ khởi đầu (8 màu). */
    public static final int[] SUIT_COLORS = {0x0E7C86, 0xFF7043, 0x5C6BC0, 0x66BB6A, 0xAB47BC, 0xFFCA28, 0x78909C, 0xEC407A};

    public String name;
    public Gender gender;
    public GameClass gameClass = GameClass.NONE;
    public int level = 1;
    public int xp = 0;
    public int xuLa = START_XU_LA;
    public int tinhThe = START_TINH_THE;
    public float hp;
    public float satiety = 90f;
    public float warmth = 90f;
    public float battery = 90f;
    public float x = 300f;
    public float y = 0f;
    public int suitColorIdx = 0;
    public int hairColorIdx = 0;
    /** Kiểu tóc 0..2 (tên hiển thị theo giới do UI lo). */
    public int hairStyleIdx = 0;
    /** Màu mắt 0..2, xem EYE_COLORS. */
    public int eyeColorIdx = 0;
    /** Tông da 0..2, xem SKIN_COLORS. */
    public int skinToneIdx = 0;
    public String outfitId = "bo_tan_cu_dan";
    public Map<String, Integer> inventory = new LinkedHashMap<>();

    public Player(String name, Gender gender) {
        this.name = name == null ? "" : name;
        this.gender = gender == null ? Gender.NAM : gender;
        this.gameClass = GameClass.NONE;
        this.hp = maxHp();
    }

    /** XP cần để lên cấp tiếp theo từ cấp hiện tại. */
    public int xpNext() {
        return 80 + level * 40;
    }

    /**
     * Cộng XP, tự lên cấp (có thể nhiều cấp) nếu đủ.
     * Khi lên cấp hồi 30% HP tối đa mới.
     *
     * @return true nếu có lên cấp
     */
    public boolean gainXp(int amount) {
        if (amount <= 0) return false;
        xp += amount;
        boolean leveled = false;
        while (xp >= xpNext()) {
            xp -= xpNext();
            level++;
            leveled = true;
            hp = Math.min(maxHp(), hp + maxHp() * LEVEL_UP_HEAL_RATIO);
        }
        if (hp > maxHp()) hp = maxHp();
        return leveled;
    }

    /** HP tối đa: base theo class + (cấp - 1) * 12. NONE dùng base 100. */
    public int maxHp() {
        GameClass gc = gameClass == null ? GameClass.NONE : gameClass;
        return gc.baseHp + (level - 1) * HP_PER_LEVEL;
    }

    public int atk() {
        GameClass gc = gameClass == null ? GameClass.NONE : gameClass;
        return gc.baseAtk + (level - 1) * ATK_PER_LEVEL;
    }

    public int def() {
        GameClass gc = gameClass == null ? GameClass.NONE : gameClass;
        return gc.baseDef + (level - 1) * DEF_PER_LEVEL;
    }

    public float speed() {
        GameClass gc = gameClass == null ? GameClass.NONE : gameClass;
        return gc.speed;
    }

    public void addItem(String id, int count) {
        if (id == null || count <= 0) return;
        Integer cur = inventory.get(id);
        inventory.put(id, (cur == null ? 0 : cur) + count);
    }

    public boolean removeItem(String id, int count) {
        if (id == null || count <= 0) return false;
        int cur = countItem(id);
        if (cur < count) return false;
        if (cur == count) inventory.remove(id);
        else inventory.put(id, cur - count);
        return true;
    }

    public int countItem(String id) {
        if (id == null) return 0;
        Integer v = inventory.get(id);
        return v == null ? 0 : v;
    }

    /** Trừ Xu Lá; false (và không trừ) nếu không đủ. Số dư không bao giờ âm. */
    public boolean spendXu(int amount) {
        if (amount < 0) return false;
        if (xuLa < amount) return false;
        xuLa -= amount;
        return true;
    }

    /** Trừ Tinh Thể; false (và không trừ) nếu không đủ. */
    public boolean spendTinh(int amount) {
        if (amount < 0) return false;
        if (tinhThe < amount) return false;
        tinhThe -= amount;
        return true;
    }

    /** Tông da hiện tại (RGB hex), chỉ số ngoài khoảng được kẹp về 0. */
    public int skinColor() {
        return SKIN_COLORS[(skinToneIdx >= 0 && skinToneIdx < SKIN_COLORS.length) ? skinToneIdx : 0];
    }

    /** Màu mắt hiện tại (RGB hex), chỉ số ngoài khoảng được kẹp về 0. */
    public int eyeColor() {
        return EYE_COLORS[(eyeColorIdx >= 0 && eyeColorIdx < EYE_COLORS.length) ? eyeColorIdx : 0];
    }

    /** Màu tóc hiện tại (RGB hex), chỉ số ngoài khoảng được kẹp về 0. */
    public int hairColor() {
        return HAIR_COLORS[(hairColorIdx >= 0 && hairColorIdx < HAIR_COLORS.length) ? hairColorIdx : 0];
    }

    /** Màu bộ đồ khởi đầu hiện tại (RGB hex), chỉ số ngoài khoảng được kẹp về 0. */
    public int suitColor() {
        return SUIT_COLORS[(suitColorIdx >= 0 && suitColorIdx < SUIT_COLORS.length) ? suitColorIdx : 0];
    }

    /** Kẹp một chỉ số sinh tồn về khoảng 0..100. */
    public static float clampStat(float v) {
        if (v < 0f) return 0f;
        if (v > 100f) return 100f;
        return v;
    }
}
