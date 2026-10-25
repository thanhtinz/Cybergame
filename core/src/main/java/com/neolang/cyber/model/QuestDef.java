package com.neolang.cyber.model;

/** Định nghĩa tĩnh của một nhiệm vụ. */
public class QuestDef {
    public String id;
    public String name;
    public String desc;
    public ObjectiveType type;
    public int target;
    public int rewardXp;
    public int rewardXu;
    /** Vật phẩm thưởng, null nếu không có. */
    public String rewardItem;
    public int rewardItemCount;

    public QuestDef(String id, String name, String desc, ObjectiveType type, int target,
                    int rewardXp, int rewardXu, String rewardItem, int rewardItemCount) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.type = type;
        this.target = target;
        this.rewardXp = rewardXp;
        this.rewardXu = rewardXu;
        this.rewardItem = rewardItem;
        this.rewardItemCount = rewardItemCount;
    }
}
