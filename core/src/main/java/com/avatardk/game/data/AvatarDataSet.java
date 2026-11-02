package com.avatardk.game.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bộ dữ liệu gốc của AvatarDK đã trích từ Pack5: items.json (5.496 part),
 *  imgdata.json (1.139 ImageInfo), npcs.json (28 NPC).
 *  Lớp này chỉ parse chuỗi JSON (platform-clean, test headless được), không đụng file/đồ họa.
 *
 *  Cách parse animation: trường "animation" trong items.json là chuỗi bị escape hai lớp
 *  ([{\\"img\\":0,\\"dx\\":-12,\\"dy\\":-30}, ...]). Sau khi bỏ backslash và ngoặc kép,
 *  mỗi khối {...} chứa đúng 3 số nguyên theo thứ tự img, dx, dy — trích bằng regex số. */
public class AvatarDataSet {
    public final Map<Integer, PartData> parts = new HashMap<>();
    public final Map<Integer, ImgInfo> imgByItemId = new HashMap<>();
    /** Tra theo ID ảnh của gốc (ImageInfo.ID) — dữ liệu trích có id dòng
     *  = item_id + 1 trên toàn bộ bảng nên ánh xạ được (xem ImgInfo). */
    public final Map<Integer, ImgInfo> imgById = new HashMap<>();
    /** Ảnh của part ĐỘNG (id >= 2000) — cả icon lẫn từng frame mặc lên
     *  người: theo Part.cs/APartInfo.cs của gốc, part động lấy ảnh từ
     *  ảnh riêng theo id ảnh (server stream, gói −14) chứ KHÔNG dùng
     *  imgdata — tra nhầm imgdata sẽ vớ phải hình của part tĩnh trùng
     *  số. Bản port đóng gói các ảnh đó vào atlas
     *  iconatlas/<image_id>.png + data/icondata.json (toạ độ pixel HD
     *  thật, khác imgdata vốn là đơn vị medium); tra theo ID ẢNH. */
    public final Map<Integer, ImgInfo> dynImgById = new HashMap<>();
    public final List<NpcData> npcs = new ArrayList<>();


    public static AvatarDataSet fromJson(String itemsJson, String imgDataJson, String npcsJson) {
        AvatarDataSet ds = new AvatarDataSet();
        JsonReader reader = new JsonReader();

        JsonValue imgs = reader.parse(imgDataJson);
        for (JsonValue r = imgs.child; r != null; r = r.next) {
            ImgInfo info = new ImgInfo(
                r.getInt("item_id"), r.getInt("image_id"),
                r.getInt("x"), r.getInt("y"), r.getInt("w"), r.getInt("h"));
            ds.imgByItemId.put(info.itemId, info);
            ds.imgById.put(info.itemId + 1, info);
        }

        JsonValue items = reader.parse(itemsJson);
        for (JsonValue r = items.child; r != null; r = r.next) {
            PartData p = new PartData(
                r.getInt("id"),
                r.getString("name", ""),
                r.getInt("zorder", 0),
                r.getInt("gender", 0),
                r.getInt("level", 0),
                r.getInt("icon", -1),
                r.getInt("coin", -1),
                r.getInt("gold", -1),
                r.getInt("sell", -1),
                r.getInt("expired_day", 0),
                r.getInt("type", -1));
            // animation: bản cài là chuỗi "[{img:0,dx:-12,...}]"; bản WEB
            // được tools/build_web.py chuyển thành mảng số nguyên phẳng
            // [img,dx,dy,...] vì JsonReader của GWT chết RangeError khi
            // chuỗi chứa '{' kèm số (đã kiểm chứng bisect 2026-11-02).
            JsonValue animV = r.get("animation");
            if (animV != null && animV.isArray()) {
                int[] tri = new int[3];
                int ti = 0;
                for (JsonValue v = animV.child; v != null; v = v.next) {
                    tri[ti++] = v.asInt();
                    if (ti == 3) {
                        p.frames.add(new PartData.Frame(tri[0], tri[1], tri[2]));
                        ti = 0;
                    }
                }
            } else {
                parseAnimation(r.getString("animation", ""), p);
            }
            ds.parts.put(p.id, p);
        }

        JsonValue npcs = reader.parse(npcsJson);
        for (JsonValue r = npcs.child; r != null; r = r.next) {
            NpcData n = new NpcData(
                r.getInt("id"), r.getString("name", ""),
                r.getInt("map"), r.getInt("x"), r.getInt("y"));
            // items của NPC: bản cài là chuỗi "[628, 474]"; bản WEB có thể
            // là mảng số nguyên (cùng lý do với animation ở trên).
            JsonValue npcItemsV = r.get("items");
            if (npcItemsV != null && npcItemsV.isArray()) {
                for (JsonValue v = npcItemsV.child; v != null; v = v.next) {
                    n.items.add(v.asInt());
                }
            } else {
                scanInts(r.getString("items", "[]"), n.items);
            }
            // chat: chuỗi JSON lồng, bị escape hai lớp giống animation
            String chatRaw = r.getString("chat", "[]").replace("\\", "");
            int ci = 0;
            while (true) {
                int q1 = chatRaw.indexOf('"', ci);
                if (q1 < 0) break;
                int q2 = chatRaw.indexOf('"', q1 + 1);
                if (q2 < 0) break;
                String seg = chatRaw.substring(q1 + 1, q2).trim();
                if (!seg.isEmpty()) n.chat.add(seg);
                ci = q2 + 1;
            }
            // Dự phòng cho bản WEB: dữ liệu web ghi chat dạng "câu 1 | câu 2"
            // (không có ngoặc kép vì bộ parse JSON của GWT không nuốt được
            // ký tự escape — xem tools/build_web.py). Dữ liệu gốc luôn có
            // ngoặc kép khi có thoại nên nhánh này không bao giờ chạy ở
            // bản cài.
            if (n.chat.isEmpty()) {
                String flat = chatRaw.replace("[", "").replace("]", "").trim();
                if (!flat.isEmpty()) {
                    for (String s : flat.split("\\|")) {
                        String t = s.trim();
                        if (!t.isEmpty()) n.chat.add(t);
                    }
                }
            }
            ds.npcs.add(n);
        }
        return ds;
    }

    /** Nạp icondata.json (atlas icon của part động) vào dataset đã có.
     *  Tách khỏi fromJson để test/khởi chạy thiếu file vẫn chạy được —
     *  khi đó icon món động đơn giản là không hiện, như trước khi có atlas. */
    public void loadDynIcons(String iconDataJson) {
        JsonReader reader = new JsonReader();
        JsonValue rows = reader.parse(iconDataJson);
        for (JsonValue r = rows.child; r != null; r = r.next) {
            ImgInfo info = new ImgInfo(
                r.getInt("item_id"), r.getInt("image_id"),
                r.getInt("x"), r.getInt("y"), r.getInt("w"), r.getInt("h"));
            dynImgById.put(info.itemId, info);
        }
    }

    /** Quét mọi số nguyên (dạng -?\\d+) trong chuỗi theo thứ tự, viết
     *  tay thay cho Matcher.find(): bản mô phỏng regex của GWT không
     *  tiến vị trí sau lần khớp cuối nên vòng while (find()) treo vô
     *  hạn trên chuỗi có số (kiểm chứng 2026-11-02: treo ở items NPC). */
    static void scanInts(String s, List<Integer> out) {
        int i = 0, len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                int j = i + 1;
                boolean digit = c >= '0' && c <= '9';
                while (j < len && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
                    j++;
                    digit = true;
                }
                if (digit) {
                    out.add(Integer.parseInt(s.substring(i, j)));
                    i = j;
                    continue;
                }
            }
            i++;
        }
    }

    static void parseAnimation(String animation, PartData p) {
        if (animation == null) return;
        String clean = animation.replace("\\", "").replace("\"", "");
        int start = 0;
        while (true) {
            int open = clean.indexOf('{', start);
            if (open < 0) break;
            int close = clean.indexOf('}', open);
            if (close < 0) break;
            List<Integer> nums = new ArrayList<>();
            scanInts(clean.substring(open, close), nums);
            if (nums.size() >= 3) p.frames.add(new PartData.Frame(nums.get(0), nums.get(1), nums.get(2)));
            start = close + 1;
        }
    }

    public PartData part(int id) { return parts.get(id); }

    public List<NpcData> npcsForMap(int mapId) {
        List<NpcData> out = new ArrayList<>();
        for (NpcData n : npcs) if (n.map == mapId) out.add(n);
        return out;
    }
}
