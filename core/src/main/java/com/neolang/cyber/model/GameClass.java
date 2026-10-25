package com.neolang.cyber.model;

/**
 * Nghề (class) của người chơi.
 *
 * QUAN TRỌNG: người chơi KHÔNG chọn nghề lúc tạo nhân vật. Mọi nhân vật mới
 * đều bắt đầu ở NONE (chưa có nghề, dùng bộ kỹ năng cơ bản chung). Nghề thật
 * chỉ được gán tại Trạm Định Hướng trong game qua {@link ClassChoice}
 * (điều kiện: cấp >= 5 và đã xong nhiệm vụ giới thiệu "q0_chao_lang").
 */
public enum GameClass {
    NONE("Chưa Có Nghề",
            "Cư dân mới của Neo Làng, chưa định hướng nghề. Dùng bộ kỹ năng cơ bản chung.",
            100, 10, 5, 160f, 0x9E9E9E, "Chưa định hướng",
            new String[]{"Đòn Cơ Bản", "Lăn Né", "Tia Năng Lượng"},
            new float[]{1.0f, 0.0f, 1.2f},
            new float[]{0.6f, 3.0f, 2.5f},
            new int[]{0, 10, 15},
            new SkillKind[]{SkillKind.DAMAGE, SkillKind.DASH, SkillKind.DAMAGE}),

    BLADE_RUNNER("Kiếm Sĩ Mạch Điện",
            "Chiến binh cận chiến dùng kiếm năng lượng, tốc độ cao, chuyên săn quái Glitch.",
            130, 16, 8, 190f, 0xFF5252, "Thợ Săn Glitch",
            new String[]{"Chém Xoáy Mạch", "Lốc Kiếm Neon", "Đột Kích Tia Chớp"},
            new float[]{1.0f, 1.8f, 2.4f},
            new float[]{0.6f, 5.0f, 9.0f},
            new int[]{0, 20, 35},
            new SkillKind[]{SkillKind.DAMAGE, SkillKind.AOE, SkillKind.DASH}),

    DRONE_MASTER("Chủ Nhân Drone",
            "Điều khiển bầy drone hỗ trợ chiến đấu và thu thập, mạnh về diện rộng và phòng thủ.",
            105, 14, 6, 170f, 0x40C4FF, "Kỹ Thuật Viên Drone",
            new String[]{"Bắn Liên Thanh", "Bầy Drone Tấn Công", "Lá Chắn Drone"},
            new float[]{1.0f, 1.7f, 0.0f},
            new float[]{0.5f, 6.0f, 8.0f},
            new int[]{0, 25, 30},
            new SkillKind[]{SkillKind.DAMAGE, SkillKind.AOE, SkillKind.SHIELD}),

    BIO_HACKER("Bio-Hacker",
            "Hacker sinh học thao túng cây trồng và độc tố dữ liệu, có khả năng hồi phục.",
            110, 15, 5, 175f, 0x69F0AE, "Nông Dân Thủy Canh",
            new String[]{"Tia Sinh Học", "Hồi Phục Nano", "Mưa Axit Dữ Liệu"},
            new float[]{1.0f, 0.0f, 1.9f},
            new float[]{0.55f, 7.0f, 9.0f},
            new int[]{0, 25, 35},
            new SkillKind[]{SkillKind.DAMAGE, SkillKind.HEAL, SkillKind.AOE}),

    ENGINEER("Kỹ Sư Mạch",
            "Kỹ sư xây dựng và bố phòng, phòng thủ cao, đặt tháp súng và khiên mạch điện.",
            125, 12, 10, 155f, 0xFFAB40, "Thợ Xây Dựng",
            new String[]{"Bắn Đinh Năng Lượng", "Tháp Súng Tự Động", "Khiên Mạch Điện"},
            new float[]{1.0f, 1.6f, 0.0f},
            new float[]{0.6f, 8.0f, 9.0f},
            new int[]{0, 30, 30},
            new SkillKind[]{SkillKind.DAMAGE, SkillKind.AOE, SkillKind.SHIELD});

    /** Số kỹ năng nghề của một nghề thật. */
    public static final int CLASS_SKILL_COUNT = 3;
    /** Số kỹ năng trong bộ cơ bản chung (đánh thường, lăn né, bắn năng lượng). */
    public static final int BASIC_SKILL_COUNT = 3;

    public final String displayName;
    public final String desc;
    public final int baseHp;
    public final int baseAtk;
    public final int baseDef;
    public final float speed;
    /** Màu đại diện, RGB hex (0xRRGGBB). */
    public final int color;
    /** Nghề sống gắn với class này. */
    public final String lifeRole;

    private final String[] skillNames;
    private final float[] skillMults;
    private final float[] skillCds;
    private final int[] skillCosts;
    private final SkillKind[] skillKinds;

    GameClass(String displayName, String desc, int baseHp, int baseAtk, int baseDef,
              float speed, int color, String lifeRole,
              String[] skillNames, float[] skillMults, float[] skillCds,
              int[] skillCosts, SkillKind[] skillKinds) {
        this.displayName = displayName;
        this.desc = desc;
        this.baseHp = baseHp;
        this.baseAtk = baseAtk;
        this.baseDef = baseDef;
        this.speed = speed;
        this.color = color;
        this.lifeRole = lifeRole;
        this.skillNames = skillNames;
        this.skillMults = skillMults;
        this.skillCds = skillCds;
        this.skillCosts = skillCosts;
        this.skillKinds = skillKinds;
    }

    /**
     * Số kỹ năng nghề: 3 cho nghề thật, 1 cho NONE (chỉ tính đòn đánh thường
     * là kỹ năng nghề; Lăn Né và Tia Năng Lượng thuộc bộ cơ bản chung, vẫn
     * truy cập được qua skillName/skillMult/... với chỉ số 1 và 2).
     */
    public int skillCount() {
        return this == NONE ? 1 : CLASS_SKILL_COUNT;
    }

    /** Số kỹ năng trong bộ đầy đủ của class này (NONE có 3 kỹ năng cơ bản). */
    public int basicSkillCount() {
        return skillNames.length;
    }

    public String skillName(int i) {
        return (i >= 0 && i < skillNames.length) ? skillNames[i] : "";
    }

    /** Hệ số sát thương của kỹ năng (0 với kỹ năng không gây sát thương). */
    public float skillMult(int i) {
        return (i >= 0 && i < skillMults.length) ? skillMults[i] : 0f;
    }

    /** Thời gian hồi chiêu, tính bằng giây. */
    public float skillCd(int i) {
        return (i >= 0 && i < skillCds.length) ? skillCds[i] : 0f;
    }

    /** Chi phí năng lượng của kỹ năng. */
    public int skillCost(int i) {
        return (i >= 0 && i < skillCosts.length) ? skillCosts[i] : 0;
    }

    public SkillKind skillKind(int i) {
        return (i >= 0 && i < skillKinds.length) ? skillKinds[i] : SkillKind.DAMAGE;
    }
}
