package com.avatardk.game.music;

/** Một video/bài hát trên YouTube (dữ liệu tối thiểu cho trình phát nhạc). */
public class YtVideo {
    public String id = "";
    public String title = "";
    public String channel = "";
    /** Thời lượng (giây); 0 = chưa rõ (ví dụ phát trực tiếp). */
    public int durationSec = 0;
    /** URL ảnh bìa (thumbnail) lớn nhất YouTube trả về. */
    public String thumbUrl = "";

    public YtVideo() {
    }

    public YtVideo(String id, String title, String channel, int durationSec, String thumbUrl) {
        this.id = id;
        this.title = title;
        this.channel = channel;
        this.durationSec = durationSec;
        this.thumbUrl = thumbUrl;
    }

    /** Ảnh bìa 16:9 chất lượng cao suy từ id (có thể không tồn tại với
     *  video cũ — nơi dùng phải có đường lùi về thumbUrl). */
    public String hqThumbUrl() {
        return "https://i.ytimg.com/vi/" + id + "/hq720.jpg";
    }

    public String durationText() {
        return fmt(durationSec);
    }

    public static String fmt(int sec) {
        if (sec <= 0) return "--:--";
        int h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s)
            : String.format("%d:%02d", m, s);
    }

    /** "4:39" / "1:02:03" -> giây; không đọc được -> 0. */
    public static int parseDuration(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            String[] p = text.trim().split(":");
            int v = 0;
            for (String s : p) v = v * 60 + Integer.parseInt(s.trim());
            return v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
