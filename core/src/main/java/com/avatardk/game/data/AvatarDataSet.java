package com.avatardk.game.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern INT = Pattern.compile("-?\\d+");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

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
            parseAnimation(r.getString("animation", ""), p);
            ds.parts.put(p.id, p);
        }

        JsonValue npcs = reader.parse(npcsJson);
        for (JsonValue r = npcs.child; r != null; r = r.next) {
            NpcData n = new NpcData(
                r.getInt("id"), r.getString("name", ""),
                r.getInt("map"), r.getInt("x"), r.getInt("y"));
            Matcher mi = INT.matcher(r.getString("items", "[]"));
            while (mi.find()) n.items.add(Integer.parseInt(mi.group(0)));
            // chat: chuỗi JSON lồng, bị escape hai lớp giống animation
            String chatRaw = r.getString("chat", "[]").replace("\\", "");
            Matcher mc = QUOTED.matcher(chatRaw);
            boolean first = true;
            while (mc.find()) {
                // phần tử đầu tiên sau khi bóc escape là chính chuỗi rỗng của vỏ ngoài — bỏ qua mẫu "[]"
                String s = mc.group(1).trim();
                if (!s.isEmpty()) n.chat.add(s);
                first = false;
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

    static void parseAnimation(String animation, PartData p) {
        if (animation == null) return;
        String clean = animation.replace("\\", "").replace("\"", "");
        int start = 0;
        while (true) {
            int open = clean.indexOf('{', start);
            if (open < 0) break;
            int close = clean.indexOf('}', open);
            if (close < 0) break;
            Matcher m = INT.matcher(clean.substring(open, close));
            List<Integer> nums = new ArrayList<>();
            while (m.find()) nums.add(Integer.parseInt(m.group(0)));
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
