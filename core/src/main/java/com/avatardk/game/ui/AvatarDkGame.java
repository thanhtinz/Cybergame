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
    /** Luồng vào game Thị Trấn Mặt Trời: banner → đăng nhập → chọn máy chủ → thế giới. */
    public BannerScreen banner;
    public LoginScreen login;
    public ServerSelectScreen serverSelect;
    /** Tên hiển thị của người chơi (tài khoản vừa đăng nhập, hoặc nhân vật mẫu). */
    public String playerName = "";
    /** Kết nối tới server khi chơi online (null khi offline). */
    public com.avatardk.game.net.GameConnection connection;
    /** Bộ tải dữ liệu lần đầu (null khi tắt bằng avatardk.dataUrl=off). */
    public com.avatardk.game.update.DataUpdater dataUpdater;
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
        // Tải dữ liệu về máy ở lần vào đầu tiên: nếu máy đã có dữ liệu khớp
        // phiên bản đã biết thì vào thẳng; chưa có (hoặc bản đã biết cũ hơn)
        // hiện màn hình tải trước. avatardk.dataUrl=off = tắt hẳn (test/dev).
        if ("off".equals(System.getProperty("avatardk.dataUrl"))) {
            initGameData();
            return;
        }
        dataUpdater = new com.avatardk.game.update.DataUpdater(
            com.avatardk.game.GameFiles.dataRootFile(),
            com.avatardk.game.update.DataUpdater.baseUrl());
        com.avatardk.game.update.DataUpdater.Manifest cached = dataUpdater.cachedManifest();
        if (cached != null && dataUpdater.installedVersion() >= cached.version && cached.version > 0) {
            initGameData();
            return;
        }
        setScreen(new DataDownloadScreen(this, dataUpdater, false));
    }

    /** Nạp dữ liệu game + dựng các màn chính — gọi sau khi dữ liệu đã sẵn
     *  sàng (đã tải về máy hoặc có sẵn trong bản cài). */
    public void initGameData() {
        com.avatardk.game.data.GameSettings.load();
        data = AvatarDataSet.fromJson(
            com.avatardk.game.GameFiles.data("data/items.json").readString("UTF-8"),
            com.avatardk.game.GameFiles.data("data/imgdata.json").readString("UTF-8"),
            com.avatardk.game.GameFiles.data("data/npcs.json").readString("UTF-8"));
        // atlas icon của part động (đóng gói từ res Pack5) — thiếu file
        // thì bỏ qua, icon món động để trống như bản trước
        try {
            if (com.avatardk.game.GameFiles.data("data/icondata.json").exists()) {
                data.loadDynIcons(com.avatardk.game.GameFiles.data("data/icondata.json").readString("UTF-8"));
            }
        } catch (Exception e) {
            // icon động không hiện; phần còn lại của game không bị ảnh hưởng
        }
        dollRenderer = new PaperDollRenderer(this);
        world = new WorldScreen(this);
        login = new LoginScreen(this);
        serverSelect = new ServerSelectScreen(this);
        banner = new BannerScreen(this);
        setScreen(banner);
    }

    /** Xoá cache texture trong bộ nhớ (nút "Xoá cache" ở màn banner): nạp lại ở lần vẽ sau. */
    public void clearTextureCache() {
        for (Texture t : atlases.values()) t.dispose();
        for (Texture t : textures.values()) t.dispose();
        atlases.clear();
        textures.clear();
    }

    public Texture atlas(int bigId) {
        Texture t = atlases.get(bigId);
        if (t == null) {
            t = new Texture(com.avatardk.game.GameFiles.data("big/" + bigId + ".png"));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            atlases.put(bigId, t);
        }
        return t;
    }

    public Texture texture(String path) {
        Texture t = textures.get(path);
        if (t == null) {
            t = new Texture(com.avatardk.game.GameFiles.data(path));
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
        EntryUi.dispose();
        Ui.dispose();
    }
}
