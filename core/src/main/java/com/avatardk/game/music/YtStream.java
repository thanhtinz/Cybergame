package com.avatardk.game.music;

/** Luồng audio trực tiếp của một video (link googlevideo có hạn dùng
 *  khoảng 6 giờ — hết hạn thì resolve lại). */
public class YtStream {
    public String videoId = "";
    public String url = "";
    public String mimeType = "";
    public int itag = 0;
    public int bitrate = 0;
    public int durationSec = 0;
    public String title = "";
    public String author = "";
}
