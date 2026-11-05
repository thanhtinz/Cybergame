package avatar.minigame;

/**
 * Luật bài Tiến Lên — port 1-1 từ CardUtils.cs của client gốc Pack5
 * (xem docs/tienlen-spec.md). Bài luôn ở dạng đã sắp tăng dần theo
 * cardID (value = id/4, suit = id%4).
 */
public final class CardRules {

    public static final int T_SINGLE = 0;
    public static final int T_STRAIGHT = 1;
    public static final int T_PAIR = 2;
    public static final int T_TRIPLE = 3;
    public static final int T_3PAIRSEQ = 4;
    public static final int T_4PAIRSEQ = 5;
    public static final int T_FOURKIND = 6;

    private CardRules() {
    }

    public static void sort(byte[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            for (int j = i + 1; j < a.length; j++) {
                if (a[i] > a[j]) {
                    byte b = a[i];
                    a[i] = a[j];
                    a[j] = b;
                }
            }
        }
    }

    public static int getType(byte[] bai) {
        if (is1(bai)) return T_SINGLE;
        if (is2(bai)) return T_PAIR;
        if (is3(bai)) return T_TRIPLE;
        if (is3DoiThong(bai)) return T_3PAIRSEQ;
        if (is4DoiThong(bai)) return T_4PAIRSEQ;
        if (isTuQuy(bai)) return T_FOURKIND;
        if (isSanh(bai)) return T_STRAIGHT;
        return -1;
    }

    public static boolean is1(byte[] bai) {
        return bai.length == 1;
    }

    public static boolean isSanh(byte[] bai) {
        if (bai.length < 3) return false;
        for (int i = 1; i < bai.length; i++) {
            if (bai[i - 1] / 4 != bai[i] / 4 - 1) return false;
        }
        return bai[bai.length - 1] / 4 != 12;
    }

    public static boolean is2(byte[] bai) {
        return bai.length == 2 && bai[0] / 4 == bai[1] / 4;
    }

    public static boolean is3(byte[] bai) {
        return bai.length == 3 && bai[0] / 4 == bai[1] / 4 && bai[1] / 4 == bai[2] / 4;
    }

    public static boolean isTuQuy(byte[] bai) {
        return bai.length == 4 && bai[0] / 4 == bai[1] / 4
            && bai[1] / 4 == bai[2] / 4 && bai[2] / 4 == bai[3] / 4;
    }

    public static boolean is3DoiThong(byte[] bai) {
        return isDoiThong(bai, 6);
    }

    public static boolean is4DoiThong(byte[] bai) {
        return isDoiThong(bai, 8);
    }

    private static boolean isDoiThong(byte[] bai, int len) {
        if (bai.length != len) return false;
        for (int i = 1; i < bai.length; i++) {
            if (i % 2 == 1 && bai[i - 1] / 4 != bai[i] / 4) return false;
            if (i % 2 == 0 && bai[i - 1] / 4 != bai[i] / 4 - 1) return false;
        }
        return true;
    }

    /**
     * Nước sắp đánh có đè được bài trên bàn không — port của
     * CardUtils.available. Trả tiền phạt chặt heo (theo mức bàn
     * money) nếu hợp lệ, −1 nếu không đè được / không hợp lệ.
     * curType = −1 (bàn trống, đang dẫn) thì mọi tổ hợp hợp lệ đều
     * được, phạt 0.
     */
    public static int available(byte[] sapDanh, int typeSap, byte[] baiDanh, int typeDanh, int money) {
        int penalty = 0;
        switch (typeDanh) {
            case -1:
                return typeSap != -1 ? 0 : -1;
            case T_SINGLE:
                if (typeSap == T_SINGLE && sapDanh[0] > baiDanh[0]) return 0;
                if (baiDanh[0] / 4 == 12
                    && (typeSap == T_3PAIRSEQ || typeSap == T_4PAIRSEQ || typeSap == T_FOURKIND)) {
                    penalty = baiDanh[0] % 4 < 2 ? money / 2 : money;
                    return penalty;
                }
                return -1;
            case T_PAIR:
                if (typeSap == T_PAIR && sapDanh[1] > baiDanh[1]) return 0;
                if (baiDanh[0] / 4 == 12 && (typeSap == T_FOURKIND || typeSap == T_4PAIRSEQ)) {
                    if (baiDanh[1] % 4 < 2) penalty = money;
                    else if (baiDanh[0] % 4 >= 2) penalty = 2 * money;
                    else penalty = money + money / 2;
                    return penalty;
                }
                return -1;
            case T_TRIPLE:
                return typeSap == T_TRIPLE && sapDanh[2] > baiDanh[2] ? 0 : -1;
            case T_STRAIGHT:
                return typeSap == T_STRAIGHT && sapDanh.length == baiDanh.length
                    && sapDanh[sapDanh.length - 1] > baiDanh[baiDanh.length - 1] ? 0 : -1;
            case T_3PAIRSEQ:
                if ((typeSap == T_3PAIRSEQ && sapDanh[5] > baiDanh[5])
                    || typeSap == T_FOURKIND || typeSap == T_4PAIRSEQ) {
                    return money;
                }
                return -1;
            case T_FOURKIND:
                if ((typeSap == T_FOURKIND && sapDanh[3] > baiDanh[3])
                    || typeSap == T_4PAIRSEQ) {
                    return money + money / 2;
                }
                return -1;
            case T_4PAIRSEQ:
                if (typeSap == T_4PAIRSEQ && sapDanh[7] > baiDanh[7]) {
                    return 2 * money;
                }
                return -1;
            default:
                return -1;
        }
    }

    /** Tới trắng — port của CardUtils.checkForceFinish. */
    public static boolean checkForceFinish(byte[] array, boolean newgame) {
        int j;
        for (j = 9; j < 13 && array[j] / 4 == 12; j++) {
        }
        if (j == 13) return true;
        for (j = 0; j < 4 && array[j] / 4 == 0; j++) {
        }
        if (j == 4 && newgame) return true;
        int num = 0;
        for (j = 0; j < 12; j++) {
            if (array[j] / 4 == array[j + 1] / 4 - 1 && array[j + 1] / 4 != 12) {
                num++;
            } else if (array[j] / 4 != array[j + 1] / 4) {
                break;
            }
        }
        if (num >= 11) return true;
        num = 0;
        for (j = 0; j < 12; j++) {
            if (array[j] / 4 == array[j + 1] / 4) {
                num++;
                j++;
            }
        }
        if (num >= 6) return true;
        return demDoiThong(array) >= 5;
    }

    private static int demDoiThong(byte[] bai) {
        int num = 0;
        int num2 = 0;
        for (int i = 0; i < bai.length - 1; i++) {
            if (bai.length >= 48) break;
            if (num2 == 0 && bai[i] / 4 == bai[i + 1] / 4) {
                num2 = 1;
            } else if (num2 % 2 == 1) {
                if (bai[i] / 4 == bai[i + 1] / 4 - 1) {
                    num2++;
                } else if (bai[i] / 4 != bai[i + 1] / 4) {
                    if (num2 > num) num = num2;
                    num2 = 0;
                }
            } else if (bai[i] / 4 == bai[i + 1] / 4) {
                num2++;
            } else {
                if (num2 > num) num = num2;
                num2 = 0;
            }
        }
        if (num2 > num) num = num2;
        return (num + 1) / 2;
    }
}
