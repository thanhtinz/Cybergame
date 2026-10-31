package com.avatardk.game.ui;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.avatardk.game.data.AvatarDataSet;
import com.avatardk.game.data.PaperDoll;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Khung game AvatarDK (đợt 1): nạp dữ liệu gốc + atlas big, giữ texture map. */
public class AvatarDkGame extends Game {
    /** AvMain.hd của bản hd trong client gốc = 2 (mọi tọa độ medium nhân 2). */
    public static final int HD = 2;

    public AvatarDataSet data;
    public PaperDollRenderer dollRenderer;
    /** Màn thế giới dùng chung: vào thẳng khi chơi offline, hoặc sau đăng nhập. */
    public WorldScreen world;
    /** Kết nối tới server khi chơi online (null khi offline). */
    public com.avatardk.game.net.GameConnection connection;
    /** Thông tin nhân vật từ gói LOGIN_SUCESS (null khi offline). */
    public com.avatardk.game.net.PlayerInfo player;
    private final Map<Integer, Texture> atlases = new HashMap<>();
    private final Map<String, Texture> textures = new HashMap<>();

    /** Bộ part của nhân vật người chơi mẫu "Sơn" — theo bản lắp đối chiếu pack5/chars/son.png
     *  (ghi trong hồ sơ dự án LTS): mặt 0, mắt 80 "xanh nước biển", tóc 46, áo 474 "thun tình yêu",
     *  quần 628. Thứ tự trong danh sách không quan trọng: PaperDoll tự sắp theo z-order. */
    public static final List<Integer> SON_PARTS = List.of(628, 474, 0, 80, 46);
    public static final String SON_NAME = "Sơn";

    @Override
    public void create() {
        Ui.initFonts();
        data = AvatarDataSet.fromJson(
            Gdx.files.internal("data/items.json").readString("UTF-8"),
            Gdx.files.internal("data/imgdata.json").readString("UTF-8"),
            Gdx.files.internal("data/npcs.json").readString("UTF-8"));
        dollRenderer = new PaperDollRenderer(this);
        world = new WorldScreen(this);
        setScreen(new LoginScreen(this));
    }

    public Texture atlas(int bigId) {
        Texture t = atlases.get(bigId);
        if (t == null) {
            t = new Texture(Gdx.files.internal("big/" + bigId + ".png"));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            atlases.put(bigId, t);
        }
        return t;
    }

    public Texture texture(String path) {
        Texture t = textures.get(path);
        if (t == null) {
            t = new Texture(Gdx.files.internal(path));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(path, t);
        }
        return t;
    }

    public PaperDoll newDoll(List<Integer> partIds) { return new PaperDoll(data, partIds); }

    @Override
    public void dispose() {
        for (Texture t : atlases.values()) t.dispose();
        for (Texture t : textures.values()) t.dispose();
        atlases.clear();
        textures.clear();
        Ui.dispose();
    }
}
