package com.avatardk.game.music;

import java.util.ArrayList;
import java.util.List;

/** Playlist trên YouTube vừa nhập về (tên gốc + các bài theo thứ tự). */
public class YtPlaylistData {
    public String id = "";
    public String name = "";
    public List<YtVideo> videos = new ArrayList<>();
}
