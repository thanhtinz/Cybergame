package avatar.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Luật phỏm (tá lả) — port từ PBoardScr.checkPhomSanh /
 * checkPhomBaLa của client gốc (docs/phom-spec.md).
 *
 * Bài Phỏm: cardID 0..51, value = id / 4 (0=A … 12=K),
 * suit = id % 4, điểm rác = value + 1. Bài giữ sắp tăng dần.
 */
public final class PhomRules {

    private PhomRules() {
    }

    public static int value(int card) {
        return card / 4;
    }

    public static int suit(int card) {
        return card % 4;
    }

    public static int points(int card) {
        return card / 4 + 1;
    }

    /** Một nhóm lá (đã sắp tăng dần) có phải phỏm hợp lệ không:
     *  sảnh (liên tiếp cùng chất, A chỉ thấp) hoặc ba lá (cùng
     *  value), tối thiểu 3 lá — đúng hai hàm check của gốc. */
    public static boolean isPhom(List<Integer> cards) {
        if (cards == null || cards.size() < 3) return false;
        List<Integer> a = new ArrayList<>(cards);
        Collections.sort(a);
        boolean sanh = true, bala = true;
        for (int i = 0; i + 1 < a.size(); i++) {
            int x = a.get(i), y = a.get(i + 1);
            if (x == y) return false;
            if (!(value(y) - value(x) == 1 && suit(x) == suit(y))) sanh = false;
            if (value(x) != value(y)) bala = false;
        }
        return sanh || bala;
    }

    /** Mọi phỏm tối đa chứa lá `first` trong tập `rest` (rest đã gồm
     *  first): dùng cho tìm phân hoạch. Sinh: nhóm cùng value (mọi
     *  tập con ≥3 chứa first) + các sảnh cùng chất chứa first
     *  (mọi cửa sổ liên tiếp độ dài 3..5 chứa value của first mà đủ
     *  lá trong rest). */
    private static List<List<Integer>> phomsContaining(List<Integer> rest, int first) {
        List<List<Integer>> out = new ArrayList<>();
        int v = value(first), s = suit(first);
        // cùng value
        List<Integer> sameVal = new ArrayList<>();
        for (int c : rest) if (value(c) == v && c != first) sameVal.add(c);
        int n = sameVal.size();
        for (int mask = 0; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) < 2) continue; // + first = ≥3
            List<Integer> g = new ArrayList<>();
            g.add(first);
            for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) g.add(sameVal.get(i));
            out.add(g);
        }
        // sảnh cùng chất: các lá cùng chất theo value
        int[] byVal = new int[13];
        java.util.Arrays.fill(byVal, -1);
        for (int c : rest) if (suit(c) == s) byVal[value(c)] = c;
        for (int lo = Math.max(0, v - 4); lo <= v; lo++) {
            for (int hi = Math.min(12, lo + 2); hi <= Math.min(12, lo + 4); hi++) {
                if (v < lo || v > hi || hi - lo < 2) continue;
                List<Integer> g = new ArrayList<>();
                boolean ok = true;
                for (int vv = lo; vv <= hi; vv++) {
                    if (byVal[vv] < 0) { ok = false; break; }
                    g.add(byVal[vv]);
                }
                if (ok) out.add(g);
            }
        }
        return out;
    }

    /** Kết quả phân hoạch tối ưu: các phỏm + điểm rác còn lại. */
    public static class Partition {
        public final List<List<Integer>> melds = new ArrayList<>();
        public int racScore;
        public int racCount;
    }

    /** Phân hoạch tối ưu một tập lá: tối đa hoá số lá vào phỏm,
     *  hoà thì điểm rác nhỏ nhất. Tìm kiếm đệ quy trên ≤13 lá. */
    public static Partition bestPartition(List<Integer> cards) {
        List<Integer> sorted = new ArrayList<>(cards);
        Collections.sort(sorted);
        Partition best = new Partition();
        best.racScore = Integer.MAX_VALUE;
        best.racCount = Integer.MAX_VALUE;
        searchAcc(sorted, new ArrayList<>(), 0, 0, best);
        return best;
    }

    private static void searchAcc(List<Integer> rest, List<List<Integer>> melds,
                                  int racScore, int racCount, Partition best) {
        if (rest.isEmpty()) {
            consider(melds, racScore, racCount, best);
            return;
        }
        if (racCount > best.racCount) return;
        if (racCount == best.racCount && racScore >= best.racScore) return;
        int first = rest.get(0);
        List<Integer> rest1 = new ArrayList<>(rest);
        rest1.remove(0);
        searchAcc(rest1, melds, racScore + points(first), racCount + 1, best);
        for (List<Integer> g : phomsContaining(rest, first)) {
            List<Integer> rest2 = new ArrayList<>(rest);
            rest2.removeAll(g);
            melds.add(g);
            searchAcc(rest2, melds, racScore, racCount, best);
            melds.remove(melds.size() - 1);
        }
    }

    private static void consider(List<List<Integer>> melds, int racScore,
                                 int racCount, Partition best) {
        if (racCount < best.racCount
            || (racCount == best.racCount && racScore < best.racScore)) {
            best.melds.clear();
            for (List<Integer> g : melds) best.melds.add(new ArrayList<>(g));
            best.racScore = racScore;
            best.racCount = racCount;
        }
    }

    /** Tập lá có phân hoạch TRỌN thành phỏm không (điều kiện Ù:
     *  9 lá = 3+3+3, 10 lá = 3+3+4). */
    public static boolean isFullPhom(List<Integer> cards) {
        if (cards.isEmpty()) return false;
        Partition p = bestPartition(cards);
        return p.racCount == 0;
    }

    /** Lá `card` có gắn được vào phỏm `meld` không (gắn vào vẫn
     *  là phỏm hợp lệ). */
    public static boolean extendsPhom(List<Integer> meld, int card) {
        if (meld.contains(card)) return false;
        List<Integer> g = new ArrayList<>(meld);
        g.add(card);
        return isPhom(g);
    }
}
