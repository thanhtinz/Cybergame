package avatar.play.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CharacterInfo {
    // Khai báo Map là static để có thể truy cập từ nhiều class khác nhau
    private static final Map<String, Byte> characterMap = new ConcurrentHashMap<>();
    static {
        // Khởi tạo sẵn các phần tử (tên và giới tính) vào Map
        // 1 đại diện cho nam 2 nữ
        characterMap.put("viethung", (byte) 1);
        characterMap.put("nghien96", (byte) 1);
        characterMap.put("siiidoo", (byte) 1);
        characterMap.put("vuarong99", (byte) 1);
        characterMap.put("besen2k2", (byte) 2);
        characterMap.put("nhimeomeo", (byte) 2);
        characterMap.put("baodzvl", (byte) 1);
        characterMap.put("taixiu", (byte) 1);
        characterMap.put("shizuka", (byte) 2);
        characterMap.put("trienn1", (byte) 1);
        characterMap.put("soaicavn", (byte) 1);
        characterMap.put("bestgamer", (byte) 1);
        characterMap.put("hoahaiduong123", (byte) 2);
        characterMap.put("cafesua12", (byte) 2);
        characterMap.put("phuthuynho121", (byte) 2);
        characterMap.put("giangcute199", (byte) 2);
        characterMap.put("duylongok", (byte) 1);
        characterMap.put("ngoctiem9x", (byte) 2);


        characterMap.put("souuth", (byte) 1);
        characterMap.put("sadboy", (byte) 1);
        characterMap.put("dunglk2", (byte) 1);
        characterMap.put("girldeptrai", (byte) 2);
        characterMap.put("bedung2kk", (byte) 2);
        characterMap.put("meomeo212", (byte) 2);
        characterMap.put("Baodzvl1", (byte) 1);
        characterMap.put("taixiu1", (byte) 1);
        characterMap.put("shizukane", (byte) 2);
        characterMap.put("nobinobi12", (byte) 1);
        characterMap.put("soaicavn1", (byte) 1);
        characterMap.put("nerverdje", (byte) 1);
        characterMap.put("hoahuongduong198", (byte) 2);
        characterMap.put("girlxinhjj", (byte) 2);
        characterMap.put("x0000x", (byte) 2);
        characterMap.put("giangcute1991", (byte) 2);
        characterMap.put("lplplp0", (byte) 1);
        characterMap.put("baongoc91", (byte) 2);

        characterMap.put("bengoc097", (byte) 2);
        characterMap.put("adugamevip", (byte) 1);
        characterMap.put("0987888712", (byte) 2);
        shuffleCharacterMap();
    }
    // Method thêm tên và giới tính vào Map
    public static void addCharacter(String name, Byte gender) {
        characterMap.put(name, gender);
    }
    private static void shuffleCharacterMap() {
        List<Map.Entry<String, Byte>> entries = new ArrayList<>(characterMap.entrySet());
        Collections.shuffle(entries);  // Xáo trộn danh sách các entry

        // Xóa các phần tử cũ và đưa vào các phần tử đã xáo trộn
        characterMap.clear();
        for (Map.Entry<String, Byte> entry : entries) {
            characterMap.put(entry.getKey(), entry.getValue());
        }
    }
    // Method lấy tên ngẫu nhiên và xóa nó khỏi Map
    public static Map.Entry<String, Byte> getRandomAndRemove() {
        if (characterMap.isEmpty()) {
            return null; // Trả về null nếu Map rỗng
        }

        // Lấy một phần tử bất kỳ từ Map (Java không hỗ trợ lấy ngẫu nhiên trực tiếp từ Map)
        Map.Entry<String, Byte> entry = characterMap.entrySet().iterator().next();

        // Xóa phần tử này khỏi Map
        characterMap.remove(entry.getKey());

        // Trả về phần tử đã lấy
        return entry;
    }

    // Method kiểm tra xem Map có rỗng không
    public static boolean isEmpty() {
        return characterMap.isEmpty();
    }
}
