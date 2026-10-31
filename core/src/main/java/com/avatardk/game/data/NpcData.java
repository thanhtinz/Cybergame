package com.avatardk.game.data;

import java.util.ArrayList;
import java.util.List;

/** NPC theo npcs.json (bảng npc trong avatar_2x.sql): tên, danh sách part mặc (items),
 *  map + tọa độ x/y theo đơn vị medium của gốc (điểm chân, gốc tọa độ trên–trái như
 *  Avatar.paint của client gốc), và các câu chat mẫu. */
public class NpcData {
    public final int id;
    public final String name;
    public final List<Integer> items = new ArrayList<>();
    public final int map;
    public final int x, y;
    public final List<String> chat = new ArrayList<>();

    public NpcData(int id, String name, int map, int x, int y) {
        this.id = id; this.name = name; this.map = map; this.x = x; this.y = y;
    }
}
