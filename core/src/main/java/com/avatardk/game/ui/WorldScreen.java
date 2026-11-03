package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.avatardk.game.data.NpcData;
import com.avatardk.game.data.PaperDoll;
import com.avatardk.game.music.FfmpegPcmSource;
import com.avatardk.game.music.MusicLibrary;
import com.avatardk.game.music.MusicPlayer;
import com.avatardk.game.music.MusicService;
import com.avatardk.game.music.ThumbCache;
import com.avatardk.game.util.BgThread;
import com.avatardk.game.music.YtVideo;
import com.avatardk.game.net.AvatarProtocol;
import com.avatardk.game.net.GameConnection;
import com.avatardk.game.net.Msg;
import com.avatardk.game.net.VoiceCodec;
import com.avatardk.game.net.ZonePlayers;
import com.avatardk.game.sim.AvatarAnimator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Màn thế giới đợt 1.
 *
 *  Ghép map đúng LoadMap của gốc:
 *  - loadImageMapFull: nạp các dải imagemap/<mapId>/0.png, 1.png, ... cho tới khi hết.
 *  - paintCreateMap: các dải đặt CẠNH nhau theo chiều ngang, mỗi dải sau lùi 1px chồng
 *    (x = -1 + tổng (w_i - 2) của các dải trước), và ĐÁY của mọi dải trùng với đáy map
 *    (Hmap * w * hd). Với 3 map của đợt 1, Hmap = 8 (LoadMap.load: index-1 = 9 -> 8;
 *    23 và 24 không có case riêng -> mặc định 8), w = 24, hd = 2 -> đáy map ở 384px.
 *  - paintM: sau các dải, vẽ 3 ảnh daydien (dây điện) nối tiếp nhau theo chiều ngang,
 *    đáy của chúng ở (đáy map tính từ trên xuống) - (w + w + w/2) — chuyển sang hệ
 *    dưới–lên: bottomDaydien = 384 - 60 (px, ở thang file hd).
 *
 *  Tọa độ nhân vật/NPC: đơn vị medium của gốc, gốc tọa độ TRÊN–TRÁI, điểm chân.
 *  Chuyển sang LibGDX (dưới–lên, thang file hd): feetX = x * 2,
 *  feetY = 384 - y * 2.
 *
 *  Map + NPC: nạp từ assets/data/maps.json (kiểm kê docs/maps-spec.md) — 31 map
 *  dựng được bằng dải imagemap; NPC gán theo hệ client (client map = npc.map + 1):
 *  quay số/đấu giá ở map 10, Chủ Hôn ở map 11 (chỉ hiện tên — part 2480 do server
 *  stream), lai.buon ở map 26 (đường ra nông trại) chứ không phải trong farm 25. */
public class WorldScreen extends ScreenAdapter {
    /** Một NPC đặt trong map theo maps.json (toạ độ medium của gốc). */
    public static class MapNpc {
        public int id, x, y;
        public String name;
    }

    /** Hàng dữ liệu map trong assets/data/maps.json (kiểm kê ở docs/maps-spec.md:
     *  id = chỉ số load phía client = server zone id + 1; bg = số nền theo bảng
     *  bg[] của LoadMap gốc tra theo id-1, null = không có ảnh nền). */
    public static class MapDef {
        public int id, strips, hmap;
        public String name;
        public Integer bg;
        public boolean daydien;
        public List<MapNpc> npcs = new ArrayList<>();
        /** Trạm xe buýt từ lưới ô gốc (ô 139, MapGrids): stopX/stopY = điểm
         *  trạm (medium, gốc trên–trái); stopX < 0 = map không có trạm. */
        public int stopX = -1, stopY, stopRow, stopFirstTileX, stopLastTileX;
        /** Vật thể gắn map [imgId, anchorX, anchorY] (medium, neo đáy–giữa). */
        public final java.util.List<int[]> objects = new java.util.ArrayList<>();
        /** Lưới va chạm: blocked[ty*gridW+tx] = ô chân vật thể, không đứng được. */
        public boolean[] blocked;
        public int gridW, gridH;
        /** Type ô gốc (sbyte) sau LoadMap.setMap — lớp va chạm nền. */
        public byte[] types;
        /** Tile vẽ (sbyte) sau setMap cho map đi đường tile (strips == 0):
         *  -1 = không vẽ, 0..125 = frame atlas tile. Xem MapGrids.Grid.d. */
        public byte[] drawTiles;
        /** Số nền dùng khi vẽ (map không nền thì tạm dùng 0 cho lớp trời). */
        public int bgNum() { return bg == null ? 0 : bg; }
        public String label() { return name == null || name.isEmpty() ? "Map " + id : name; }
        /** Map dựng bằng engine tile của gốc (không có dải imagemap) —
         *  điều kiện đúng như LoadMap.load: imgCreateMap == null. */
        public boolean tileMode() { return strips == 0 && drawTiles != null && gridW > 0; }
    }

    /** Toàn bộ map trong maps.json: map có dải imagemap (strips > 0) vẽ
     *  bằng dải ảnh, map còn lại vẽ bằng engine tile (docs/tile-engine-spec.md). */
    private final List<MapDef> maps = new ArrayList<>();

    // ---- Engine tile (port LoadMap.paintMap của gốc) ----
    /** Atlas tile hiện hành của map đang đứng (day/night theo status,
     *  map 20 dùng atlas cưới riêng) + các frame đã cắt sẵn. */
    private Texture tileTex;
    private com.badlogic.gdx.graphics.g2d.TextureRegion[] tileRegions;

    // ---- Nền (port từ LoadMap.loadBG + paintBackGround của gốc) ----
    // status: 0 = ngày (06–18h), 1 = đêm — gốc lấy theo giờ máy lúc vào map.
    // colorBg[status] của gốc: ngày 6143735 = #5DBCE7, đêm 21 = #000015.
    private static final Color[] SKY_COLOR = {
        new Color(0x5D / 255f, 0xBC / 255f, 0xE7 / 255f, 1f),
        new Color(0f, 0f, 0x15 / 255f, 1f),
    };
    /** Màu tô phẳng cho map không nền (LoadMap.paintBackGround khi imgBG == null). */
    private static final Color NO_BG_FILL = new Color(0.02f, 0.02f, 0.03f, 1f);
    /** x0_imgTreeBg theo số nền (đơn vị hd): bg0=20, bg1=25, bg2=10, bg3=33 (×hd). */
    private static final int[] X0_TREE_HD = {20, 25, 10, 33};
    /** x0_imgBG theo số nền (đơn vị hd): bg0=2, bg1=20, bg2=15, bg3=0 (×hd). */
    private static final int[] X0_BG_HD = {2, 20, 15, 0};
    private int bgStatus = 0;
    /** Ghi đè ngày/đêm cho ảnh kiểm chứng (debug); -1 = theo giờ thật. */
    private int bgStatusOverride = -1;
    private Texture bgSkyTex, bgLineTex;
    private final Texture[] cloudTex = new Texture[2];
    private static class Cloud { float x100, yW, speed; int anchor; }
    private final List<Cloud> clouds = new ArrayList<>();
    private Texture whiteTex;
    /** Đáy map theo gốc: Hmap * w(24) * hd(2) px ở thang file hd — Hmap lấy theo
     *  đúng bảng switch trong LoadMap.load của gốc (mặc định 8; map 108 = 16,
     *  map 19 = 10, map 18 = 6...), đã ghi vào maps.json. */
    private float mapBottomPx = 8 * 24 * AvatarDkGame.HD;
    private static final float SPEED = 78f; // medium/giây (gốc: v = 4 medium/tick)

    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport hudViewport = new FitViewport(Ui.W, Ui.H);
    private final OrthographicCamera worldCam = new OrthographicCamera(Ui.W, Ui.H);
    private final GlyphLayout layout = new GlyphLayout();

    private MapDef mapDef;

    // ---- Xe buýt + trạm xe (lớp lưới ô của gốc: ô 139 = trạm; Bus.cs gốc cho
    // xe chạy vào trạm thả người chơi mỗi khi vào map có trạm) ----
    // Thân xe đã vẽ lại cho game mình: hành khách chibi + biển "Thị Trấn Mặt
    // Trời"; biển trạm cũng là asset riêng của game (sprites/busstop.png).
    private Texture busTex;
    /** Cache ảnh vật thể gắn map theo id (null = id không có ảnh offline). */
    private final java.util.Map<Integer, Texture> objTexCache = new java.util.HashMap<>();

    private Texture objTexture(int id) {
        if (objTexCache.containsKey(id)) return objTexCache.get(id);
        Texture t = null;
        String p = "sprites/obj" + id + ".png";
        if (com.avatardk.game.GameFiles.data(p).exists()) t = game.texture(p);
        objTexCache.put(id, t);
        return t;
    }
    /** 0 = không có xe, 1 = xe đang vào trạm, 2 = đang dừng thả khách, 3 = đang rời trạm. */
    private int busPhase = 0;
    private float busX, busV, busTimer;
    /** Tâm xe khi dừng: cửa trước (đầu xe bên trái) khớp điểm trạm. */
    private float busTargetX() { return mapDef.stopX + 55f; }

    private final List<Texture> strips = new ArrayList<>();
    private final List<Float> stripLefts = new ArrayList<>();
    private final List<Texture> daydien = new ArrayList<>();
    private float worldW, worldH;

    private static class NpcFigure {
        NpcData data; PaperDoll doll; AvatarAnimator anim = new AvatarAnimator();
        float feetX, feetY;
        String bubble = null;
        float bubbleTimer = 0f;
    }
    private final List<NpcFigure> npcFigures = new ArrayList<>();


    /** Một món trong lượt vẽ trộn độ sâu (vật thể / NPC / người chơi khác /
     *  chính mình): sắp theo y đáy trong thế giới, y lớn (cao hơn trên màn
     *  hình) vẽ trước — đúng thứ tự merge của paintObject gốc. */
    private static class DrawItem {
        static final int OBJ = 0, NPC = 1, REMOTE = 2, PLAYER = 3, FARM = 4, FARM_INFO = 5, FARM_ANIMAL_INFO = 6, FARM_STAR = 7;
        int kind;
        float y;
        float x;
        Texture tex;
        com.badlogic.gdx.graphics.g2d.TextureRegion region;
        float dw, dh;
        int objId;
        NpcFigure npc;
        RemoteFigure remote;
    }

    // ---- Farm (đợt 3): trạng thái nông trại + cờ đang ở map farm ----
    private final FarmWorld farm = new FarmWorld();
    private boolean farmIn;
    private java.util.List<int[]> farmBlocks;
    private float[] farmStarPos;
    private final List<DrawItem> drawItems = new ArrayList<>();

    // ---- Người chơi khác qua mạng (đợt 2d, docs/park-spec.md) ----
    /** Một người chơi khác trong zone: hình paper-doll lắp từ part server gửi,
     *  vị trí hiển thị nội suy về đích (tx, ty) cho mượt. */
    private static class RemoteFigure {
        ZonePlayers.RemotePlayer info;
        PaperDoll doll;
        AvatarAnimator anim = new AvatarAnimator();
        float feetX, feetY;   // vị trí vẽ hiện tại (px hd)
        int tx, ty;           // đích theo server (medium)
        String bubble = null;
        float bubbleTimer = 0f;
        int pose = 0;         // tư thế hành động đang giữ (gói 57 byte < 100)
        float jumpT = -1f;    // tiến trình cú nhảy của người này

        float jumpOffY() { return jumpOffsetY(jumpT); }
    }
    private final Map<Integer, RemoteFigure> remotes = new LinkedHashMap<>();
    /** Client map id đã gửi gói join (50); -1 = chưa join / đã rớt mạng. */
    private int joinedClientMap = -1;
    private boolean netLossAnnounced = false;
    // KHU (zone) của map: mỗi map có 10 khu 0–9 chạy song song; bảng 846
    // hiện số khu hiện tại; đổi khu = join lại cùng map với khu khác.
    private int currentArea = 0;
    private Integer pendingArea = null; // khu muốn chuyển tới ở lần join sau
    private boolean boardOpen = false;
    // Panel CÀI ĐẶT (OptionScr của gốc): mở từ ô Cài đặt trong menu
    // burger; 3 hàng Tên / Chỉ đường / Âm thanh với nút bước ‹ › hai bên
    // như gốc, nút "Xong" đóng và lưu (gốc lưu RMS khi đóng).
    private boolean settingsOpen = false;
    private static final float SET_PW = 560f, SET_PH = 468f;
    private static final float SET_PX = (Ui.W - SET_PW) / 2f, SET_PY = (Ui.H - SET_PH) / 2f;

    private float setRowCenter(int i) { return SET_PY + SET_PH - 118 - i * 76; }

    // ---------- Panel NHẠC (tính năng MỚI ngoài gốc, chủ dự án đặt hàng
    // 2026-11-01): nghe nhạc YouTube ngay trong game thay nhạc gốc — tìm
    // kiếm bài, thêm nhiều playlist từ YouTube, phát nền không quảng cáo.
    // Mở từ ô Nhạc của menu burger. Giao diện đã duyệt 12:37; phần ruột là
    // core thật ở com.avatardk.game.music (MusicService/YoutubeClient/
    // ffmpeg) — panel này chỉ hiển thị + chuyển thao tác xuống core. ----
    private boolean musicOpen = false;
    private int musicTab = 0; // 0 = Tìm kiếm, 1 = Playlist
    private static final float MUS_PW = 920f, MUS_PH = 600f;
    private static final float MUS_PX = (Ui.W - MUS_PW) / 2f, MUS_PY = (Ui.H - MUS_PH) / 2f;

    private MusicService music;
    private ThumbCache musicThumbs;
    private final StringBuilder musicQuery = new StringBuilder();
    private final StringBuilder musicLink = new StringBuilder();
    private int musicFocus = 0; // 0 = không gõ, 1 = ô tìm kiếm, 2 = ô dán link
    private String musicStatus = "";
    private int musicListSel = 0;
    private int musicListCount = 1;

    private MusicLibrary.Playlist curPlaylist() {
        if (music == null || music.library() == null || music.library().playlists.isEmpty()) return null;
        return music.library().playlists.get(
            Math.min(musicListSel, music.library().playlists.size() - 1));
    }

    /** Khởi tạo core nhạc ở lần chạm/vẽ đầu tiên (thread vẽ — nơi an
     *  toàn để mở AudioDevice). */
    private void initMusic() {
        if (music != null) return;
        music = new MusicService(new MusicPlayer.Sink() {
            private com.badlogic.gdx.audio.AudioDevice dev;
            private final short[] shorts = new short[8192];
            private float vol = 0.7f;

            {
                try {
                    dev = Gdx.audio.newAudioDevice(FfmpegPcmSource.SAMPLE_RATE, false);
                    dev.setVolume(vol);
                } catch (Exception e) {
                    dev = null; // môi trường không có thiết bị âm thanh: panel vẫn chạy
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                if (dev == null) return;
                int n = Math.min(len / 2, shorts.length);
                for (int i = 0; i < n; i++) {
                    shorts[i] = (short) ((b[off + 2 * i] & 0xFF) | (b[off + 2 * i + 1] << 8));
                }
                dev.writeSamples(shorts, 0, n);
            }

            @Override
            public void setVolume(float v) {
                vol = v;
                if (dev != null) dev.setVolume(v);
            }
        });
        music.setVolume(0.7f);
        // bản web không có file local: truyền null, thư viện nhạc sống trong bộ nhớ
        music.initLibrary(Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.WebGL
            ? null : Gdx.files.local("music/library.json").file());
        musicListCount = music.library().playlists.size();
        musicThumbs = new ThumbCache(music.client());
        music.setListener(new MusicService.Listener() {
            @Override
            public void onSearchDone(List<YtVideo> results) {
                Gdx.app.postRunnable(() -> musicStatus = results.isEmpty()
                    ? "Không tìm thấy kết quả nào." : "");
            }

            @Override
            public void onSearchError(String message) {
                Gdx.app.postRunnable(() -> musicStatus = message);
            }

            @Override
            public void onPlaylistImported(MusicLibrary.Playlist playlist) {
                Gdx.app.postRunnable(() -> {
                    musicListSel = music.library().playlists.indexOf(playlist);
                    musicStatus = "Đã thêm playlist " + playlist.name
                        + " (" + playlist.tracks.size() + " bài).";
                    musicLink.setLength(0);
                });
            }

            @Override
            public void onImportError(String message) {
                Gdx.app.postRunnable(() -> musicStatus = message);
            }

            @Override
            public void onTrackLoading(YtVideo track) {
                Gdx.app.postRunnable(() -> musicStatus = "");
            }

            @Override
            public void onTrackStarted(YtVideo track) {
                Gdx.app.postRunnable(() -> musicStatus = "");
            }

            @Override
            public void onTrackError(String message) {
                Gdx.app.postRunnable(() -> musicStatus = message);
            }

            @Override
            public void onQueueEnded() {
            }
        });
        if (!FfmpegPcmSource.isAvailable()) {
            musicStatus = "Máy chưa có ffmpeg — cài ffmpeg để phát được nhạc (tìm kiếm vẫn dùng được).";
        }
    }

    private float musTop() { return MUS_PY + MUS_PH; }

    /** Đáy ô nhập của cột phải: tab Playlist thấp hơn vì còn hàng chip
     *  chọn playlist (34px + 2 khoảng 12) nằm giữa tab và ô nhập. */
    private float musInY() {
        float tabY = musTop() - 80 - 40;
        return musicTab == 1 ? tabY - 12 - 34 - 12 - 44 : tabY - 14 - 44;
    }

    private float musListTop() { return musInY() - 12; }
    private float setStepLx() { return SET_PX + 262; }
    private float setStepRx() { return SET_PX + SET_PW - 58; }
    private int[] boardStatus = null;   // trạng thái từng khu từ gói 60
    private float boardLatch = 0f;      // chống mở panel liên tục khi va bảng
    private float portalLatch = 0f;     // chống kích hoạt cổng ô liên tục khi va
    private boolean exitConfirmOpen;    // hộp hỏi thoát về thành phố (ô type 9/12/17)
    private boolean portalFarmShopPending; // cổng 52 ở farmway: vào farm xong mở cửa hàng
    private float lastMoveSentAt = 0f, netTime = 0f;
    private int lastSentX = Integer.MIN_VALUE, lastSentY = Integer.MIN_VALUE;
    private boolean lastSentLeft = false;

    private boolean online() {
        return game.connection != null && game.player != null;
    }

    private int ownId() {
        return game.player != null ? game.player.id : -1;
    }

    private PaperDoll playerDoll;
    private final AvatarAnimator playerAnim = new AvatarAnimator();
    private float playerX = 200f, playerY = 150f; // medium, gốc trên–trái

    // Thông tin hiển thị trên card: mặc định là nhân vật mẫu "Sơn" (chế độ offline);
    // khi đăng nhập thành công, applyPlayerInfo() thay bằng dữ liệu thật từ server.
    private String displayName = AvatarDkGame.SON_NAME;
    private String displayXu = "12.500";
    private String displayLuong = "260";
    private String displayLevel = "Lv.50";
    /** Số tiền dạng số (khớp hai chuỗi hiển thị ở trên) — panel shop dùng để
     *  xét đủ/thiếu tiền theo giá thật; cập nhật từ gói −4/−33/−24. */
    private int myXu = 12500, myLuong = 260, myLuongKhoa = 0, myXeng = 0;

    private void syncMoneyText() {
        displayXu = fmtMoney(myXu);
        displayLuong = fmtMoney(myLuong);
    }

    private static String fmtMoney(int v) {
        String s = String.valueOf(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /** Nạp thông tin nhân vật thật (gói LOGIN_SUCESS) vào thế giới: tên, tiền, cấp
     *  trên card và lắp lại paper-doll theo đúng part đang mặc của tài khoản. */
    public void applyPlayerInfo(com.avatardk.game.net.PlayerInfo p) {
        displayName = p.username;
        myXu = p.xu;
        myLuong = p.luong;
        myLuongKhoa = p.luongKhoa;
        myXeng = p.xeng;
        syncMoneyText();
        displayLevel = "Lv." + p.levelMain;
        java.util.List<Integer> parts = new ArrayList<>();
        for (int id : p.wearing) parts.add(id);
        playerParts.clear();
        playerParts.addAll(parts);
        wornExpire.clear();
        for (int i = 0; i < p.wearing.length; i++) {
            if (p.wearingExpire != null && i < p.wearingExpire.length
                && p.wearingExpire[i] != null && !p.wearingExpire[i].isEmpty()) {
                wornExpire.put(p.wearing[i], p.wearingExpire[i]);
            }
        }
        playerDoll = game.newDoll(parts);
        if (playerFeel != 4) applyFeelLocal(playerFeel);
        addChatLog(TAB_SYSTEM, null, "Đăng nhập thành công: " + p.username
            + " (cấp " + p.levelMain + ")", true);
    }

    private String chatBubble = null;
    private float chatTimer = 0f;
    private boolean typing = false;

    // ---- Khung chat GIỮA DƯỚI (theo yêu cầu của chủ dự án: khung chat luôn hiện
    // ở giữa dưới màn hình thay vì phải bấm icon chat như bản gốc; có 3 tab
    // Hệ thống / Thế giới / Gần đây và NÚT GỬI ở cuối ô nhập) ----
    private static final float CHAT_W = 620f, CHAT_X = Ui.W / 2f - CHAT_W / 2f, CHAT_Y = 12f;
    private static final float CHAT_INPUT_H = 36f, CHAT_LOG_LINES = 4, CHAT_LINE_H = 22f;
    private static final float CHAT_SEND_W = 46f;
    private static final float CHAT_H = CHAT_INPUT_H + CHAT_LOG_LINES * CHAT_LINE_H + 44f;
    /** Chiều cao panel chat khi bấm nút phóng to (nút FA expand ở góc panel). */
    private static final float CHAT_H_BIG = 400f;
    /** Chiều cao panel chat HIỆN TẠI (chạy mượt giữa CHAT_H và CHAT_H_BIG). */
    private float chatH = CHAT_H;
    private boolean chatExpanded = false;
    // Tab kiểu "thẻ thư mục": TRUNG TÂM của tab nằm đúng trên đường viền trên
    // của khung (nửa tab nhô lên ngoài, nửa tab lọt vào trong khung — như một
    // khối liền với khung). Cả 3 tab RỘNG BẰNG NHAU; tab đang dùng chỉ CAO
    // hơn một chút (32 so với 27) chứ không to nhỏ lệch hẳn — chủ dự án chốt
    // 2026-10-31.
    private static final float TAB_H_ACTIVE = 32f, TAB_H_IDLE = 27f;
    private static final int TAB_SYSTEM = 0, TAB_WORLD = 1, TAB_NEARBY = 2;
    private static final String[] CHAT_TABS = {"Hệ thống", "Thế giới", "Gần đây"};
    private int activeChatTab = TAB_NEARBY;
    private boolean chatSeeded = false;
    private static class ChatLine {
        String name, text, time; boolean system;
        /** Tin nhắn thoại: mẫu PCM 16 kHz đã giải mã từ μ-law (null = tin chữ). */
        short[] voice;
        int voiceSecs;
        boolean voicePlaying;
    }
    @SuppressWarnings("unchecked")
    private final List<ChatLine>[] chatLogs = new List[]{
        new ArrayList<ChatLine>(), new ArrayList<ChatLine>(), new ArrayList<ChatLine>()};

    /** Giờ hiện tại dạng HH:mm (viết tay để chạy được cả trên bản web —
     *  SimpleDateFormat không có trong GWT). */
    static String hhmmNow() {
        java.util.Date d = new java.util.Date();
        int h = d.getHours(), m = d.getMinutes();
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    private void addChatLog(int tab, String name, String text, boolean system) {
        ChatLine l = new ChatLine();
        l.name = name; l.text = text; l.system = system;
        // giờ gửi hiện ở góc phải mỗi tin nhắn: lấy giờ máy lúc tin tới
        // (giao thức gốc không chở mốc giờ — đồng hồ người nhận là chuẩn chung)
        l.time = hhmmNow();
        chatLogs[tab].add(l);
        while (chatLogs[tab].size() > 40) chatLogs[tab].remove(0);
    }

    /** Thêm một tin nhắn THOẠI vào nhật ký (chip nghe lại vẽ ở drawChatFrame). */
    private void addVoiceLog(int tab, String name, short[] samples) {
        ChatLine l = new ChatLine();
        l.name = name;
        l.voice = samples;
        l.voiceSecs = Math.max(1, (samples.length + VOICE_RATE - 1) / VOICE_RATE);
        l.time = hhmmNow();
        chatLogs[tab].add(l);
        while (chatLogs[tab].size() > 40) chatLogs[tab].remove(0);
    }

    /** "0:05" từ số mẫu PCM (16 kHz). */
    private static String voiceDurText(short[] samples) {
        int secs = Math.max(1, (samples.length + VOICE_RATE - 1) / VOICE_RATE);
        return "0:" + (secs < 10 ? "0" : "") + secs;
    }

    // Ô emoji của khung chat: EMOJI THẬT (ảnh Twemoji, CC-BY 4.0 —
    // github.com/twitter/twemoji) chèn vào nội dung dạng mã :ten: và vẽ
    // inline cùng dòng chữ. Chủ yếu là các mặt cười theo yêu cầu của chủ
    // dự án ("Emoji là mấy cái mặt cười như này mà 🤣", 2026-10-31) —
    // bản icon Font Awesome trước đó đã bị thay vì không phải emoji thật.
    private static final String[][] CHAT_EMOJIS = {
        {"grin", "1f600"}, {"beam", "1f601"}, {"joy", "1f602"}, {"rofl", "1f923"},
        {"smile", "1f60a"}, {"love", "1f60d"}, {"kiss", "1f618"}, {"cool", "1f60e"},
        {"wow", "1f929"}, {"party", "1f973"}, {"smirk", "1f60f"}, {"tongue", "1f61c"},
        {"zany", "1f92a"}, {"think", "1f914"}, {"eyeroll", "1f644"}, {"cry", "1f622"},
        {"sob", "1f62d"}, {"scream", "1f631"}, {"flushed", "1f633"}, {"angry", "1f621"},
        {"heart", "2764"}, {"like", "1f44d"}, {"clap", "1f44f"}, {"fire", "1f525"},
    };
    private static final java.util.Map<String, Texture> EMOJI_TEX = new java.util.HashMap<>();

    /** Texture của emoji theo tên mã (nạp lười, cache dùng chung). */
    private static Texture emojiTex(String name) {
        Texture t = EMOJI_TEX.get(name);
        if (t == null) {
            for (String[] e : CHAT_EMOJIS) {
                if (e[0].equals(name)) {
                    t = new Texture(com.avatardk.game.GameFiles.data("emoji/" + e[1] + ".png"));
                    EMOJI_TEX.put(name, t);
                    break;
                }
            }
        }
        return t;
    }

    private static final Color RICH_DEFAULT = new Color(1f, 1f, 1f, 0.95f);
    private boolean emojiOpen = false;
    private static final float EMOJI_BTN_W = 40f, EMOJI_CELL = 38f;
    private static final float EMOJI_PANEL_W = 6 * EMOJI_CELL + 12f;
    private static final float EMOJI_PANEL_H = 4 * EMOJI_CELL + 12f;

    /** Mã emoji :ten: có hợp lệ (có ảnh) hay không. */
    private static boolean isEmoji(String name) {
        return emojiTex(name) != null;
    }

    /** Bề rộng thật của một dòng chat (chữ + icon emoji inline). */
    private float richWidth(String text) {
        float w = 0;
        int i = 0;
        while (i < text.length()) {
            int s = text.indexOf(':', i);
            if (s < 0) { layout.setText(Ui.fontSmall, text.substring(i)); w += layout.width; break; }
            int e = text.indexOf(':', s + 1);
            boolean emoji = e > s + 1 && isEmoji(text.substring(s + 1, e));
            if (!emoji) {
                layout.setText(Ui.fontSmall, text.substring(i, s + 1));
                w += layout.width;
                i = s + 1;
            } else {
                if (s > i) { layout.setText(Ui.fontSmall, text.substring(i, s)); w += layout.width; }
                w += 23; // ảnh emoji 20px + lề 3
                i = e + 1;
            }
        }
        return w;
    }

    /** Vẽ một dòng chat có thể chứa mã emoji :ten: (ảnh emoji thật vẽ
     *  inline cùng dòng chữ). Trả về x ngay sau ký tự cuối. */
    private float drawRichText(String text, float x, float baselineY, Color color) {
        float cx = x;
        int i = 0;
        while (i < text.length()) {
            int s = text.indexOf(':', i);
            if (s < 0) {
                Ui.fontSmall.setColor(color != null ? color : RICH_DEFAULT);
                Ui.fontSmall.draw(batch, text.substring(i), cx, baselineY);
                layout.setText(Ui.fontSmall, text.substring(i));
                cx += layout.width;
                break;
            }
            int e = text.indexOf(':', s + 1);
            String name = e > s + 1 ? text.substring(s + 1, e) : null;
            Texture tex = name != null ? emojiTex(name) : null;
            if (tex == null) {
                Ui.fontSmall.setColor(color != null ? color : RICH_DEFAULT);
                Ui.fontSmall.draw(batch, text.substring(i, s + 1), cx, baselineY);
                layout.setText(Ui.fontSmall, text.substring(i, s + 1));
                cx += layout.width;
                i = s + 1;
            } else {
                if (s > i) {
                    Ui.fontSmall.setColor(color != null ? color : RICH_DEFAULT);
                    Ui.fontSmall.draw(batch, text.substring(i, s), cx, baselineY);
                    layout.setText(Ui.fontSmall, text.substring(i, s));
                    cx += layout.width;
                }
                batch.draw(tex, cx + 1.5f, baselineY - 15, 20, 20);
                cx += 23;
                i = e + 1;
            }
        }
        return cx;
    }
    private final StringBuilder typeBuffer = new StringBuilder();
    private boolean menuOpen = false;
    // Menu burger xổ xuống dạng LƯỚI Ô (yêu cầu của chủ dự án): mỗi ô là một
    // tính năng với icon trong hd/iconmenu. Đã nối thật: Map, Balo, Cá nhân,
    // Cài đặt. Rương KHÔNG nằm ở menu (chủ dự án chốt 2026-11-01: rương là
    // vào nhà mở rương trong nhà — panel rương sẽ mở từ vật thể rương ở đợt
    // Nhà); Hành động cũng đưa ra ngoài thành nút HUD cạnh nút biểu cảm.
    // Icon Balo (asset ba lô vẽ theo tay bộ icon gốc) và Cài đặt (asset
    // bánh răng) là asset vẽ thật — kho gốc không có hai icon này (giỏ đồ
    // gốc dùng icon giỏ hàng "30", cài đặt gốc mở từ menu chữ).
    private static final String[][] MENU_ITEMS = {
        {"50", "Map"}, {"msg0", "Thư"}, {"balo", "Balo"},
        {"40", "Bạn bè"}, {"20", "Cá nhân"}, {"gear", "Cài đặt"},
        {"music", "Nhạc"},
    };
    private static final float MENU_PX = Ui.W - 330f, MENU_PW = 312f;
    private static final float MENU_HEADER = 40f, MENU_CELL = 96f, MENU_CELL_H = 92f;
    private static final int MENU_ROWS = (MENU_ITEMS.length + 2) / 3;
    private static final float MENU_PH = MENU_HEADER + MENU_ROWS * MENU_CELL_H + 14f;
    private static final float MENU_PY = Ui.H - 84 - MENU_PH;
    // Bản đồ thành phố (MiniMap của gốc): ảnh minimap.png + lưới citiMap 50x26,
    // ô 69 = điểm vùng theo thứ tự quét; tên vùng T.nameRegion; đích là map park
    // theo MapScr.doJoin (server {0,13,20,9,23,11,17} -> client +1), vùng cuối
    // là Nông trại (client 25).
    private static final String[] REGION_NAMES = {
        "Khu nhà ở", "Khu sinh thái", "Sân bay", "Khu giải trí",
        "Khu mua sắm", "Công viên", "Khu ngoại ô", "Nông trại"};
    private static final int[] REGION_DEST = {1, 14, 21, 10, 24, 12, 18, 25};
    private final List<int[]> regionCells = new ArrayList<>(); // {col, row}
    private boolean cityMapOpen = false;
    private Texture minimapTex;
    // Biểu cảm (feel của gốc): nút mặt cười feel0 đặt KẾ BÊN khung chat, mở
    // SHEET biểu cảm kéo lên được (yêu cầu của chủ dự án: bản cũ chỉ có 8 mặt
    // {4..11} theo MapScr.doFeel là quá ít). Sheet chứa MỌI part mặt (zOrder
    // 40) dựng được offline trong dữ liệu gốc; chọn mặt = thay part zOrder 40
    // của người chơi bằng part mặt đó (Avatar.paint của gốc).
    private final List<Integer> feelIds = new ArrayList<>();
    private boolean feelOpen = false;
    private int playerFeel = 4;
    private final List<Integer> playerParts = new ArrayList<>(AvatarDkGame.SON_PARTS);
    private final List<PaperDoll> feelDolls = new ArrayList<>();
    // Nút mở sheet biểu cảm + nút hành động: xếp VÒNG CUNG quanh nút
    // Tương tác (chủ dự án chốt 2026-11-01) — cả hai nằm trên đường tròn
    // bán kính 106 quanh tâm nút Tương tác (1196, 92): biểu cảm ở góc
    // 180 độ (ngang trái, tâm 1090, 92), hành động ở góc 135 độ (chéo
    // lên trái, tâm 1121, 167).
    private static final float FEEL_BTN_X = 1062f, FEEL_BTN_Y = 64f, FEEL_BTN_S = 56f;
    // sheet biểu cảm: neo đáy ngay trên khung chat, cạnh phải thẳng hàng nút feel
    private static final float FEEL_SHEET_W = 472f;
    private static final float FEEL_SHEET_MIN = 168f, FEEL_SHEET_MAX = 400f;
    private float feelH = FEEL_SHEET_MIN, feelHTarget = FEEL_SHEET_MIN;
    private float feelScroll = 0f;
    private int feelPointer = -1;
    private boolean feelDragHandle = false;
    private float feelDragStartY = 0f, feelDragStartH = 0f;
    private float feelDragStartScroll = 0f, feelDragMoved = 0f;
    // Hành động nhân vật (MapScr.doSellectAction của gốc): nút riêng ở HUD
    // (chủ dự án chốt 2026-11-01 đưa ra khỏi menu burger) nằm trên vòng
    // cung quanh nút Tương tác ở góc 135 độ — tâm (1121, 167); mở sheet 4
    // hành động gốc — tên T.actionStr {Nhảy, Nằm, Ngồi bệt, Quỳ}, mã gói
    // 57 ac[] {10, 4, 3, 5}, icon là khung 7..10 của sheet effect/cmd
    // (CommandAction.paint của gốc). Tư thế giữ nguyên cho tới khi di
    // chuyển (đúng Avatar.doAction của gốc).
    private static final float ACT_BTN_X = 1093f, ACT_BTN_Y = 139f, ACT_BTN_S = FEEL_BTN_S;
    private static final String[] ACTION_NAMES = {"Nhảy", "Nằm", "Ngồi bệt", "Quỳ"};
    private static final int[] ACTION_BYTES = {10, 4, 3, 5};
    private static final int[] ACTION_FRAMES = {7, 8, 9, 10};
    private static final float ACT_SHEET_W = 372f, ACT_SHEET_H = 118f;
    private boolean actionOpen = false;
    private int playerPose = 0;       // tư thế đang giữ (0 = đứng)
    private float playerJumpT = -1f;  // tiến trình cú nhảy 0..1; -1 = không nhảy

    private float actSheetX() { return ACT_BTN_X + ACT_BTN_S - ACT_SHEET_W; }
    private float actSheetY() { return ACT_BTN_Y + ACT_BTN_S + 10f; }
    private float actCellX(int i) { return actSheetX() + 12f + i * 87f; }

    /** Độ nâng hình khi nhảy: cung sin cao 42px trong 0.5s (doJumps của gốc). */
    private static float jumpOffsetY(float t) {
        return t < 0f ? 0f : (float) Math.sin(Math.PI * Math.min(1f, t)) * 42f;
    }

    private NpcFigure nearestNpc = null;

    // --- Hub theo spec của chủ dự án (2026-10-31) ---
    // Trái trên: card glass (avatar + tên, dưới là tiền tệ). Phải trên: nút burger.
    // Trái dưới: joystick. Phải dưới: nút tương tác (ngữ cảnh theo NPC gần nhất).
    private static final Color GLASS_FILL = new Color(0.05f, 0.11f, 0.18f, 0.62f);
    private static final Color GLASS_RIM = new Color(0.62f, 0.82f, 1f, 0.38f);
    private static final Color GLASS_FILL_DARK = new Color(0.04f, 0.08f, 0.14f, 0.78f);
    private static final float CARD_X = 14f, CARD_W = 300f, CARD_H = 104f;
    private static final float BURGER_X = Ui.W - 70f, BURGER_Y = Ui.H - 70f, BURGER_S = 56f;
    private static final float ACT_CX = Ui.W - 84f, ACT_CY = 92f, ACT_R = 46f;
    private static final float INTERACT_RANGE = 80f; // đơn vị medium của gốc

    // joystick
    private int joyPointer = -1;
    private float joyVecX = 0f, joyVecY = 0f;
    private static final float JOY_CX = 118f, JOY_CY = 118f, JOY_R = 64f;

    public WorldScreen(AvatarDkGame game) {
        this.game = game;
        this.playerDoll = game.newDoll(AvatarDkGame.SON_PARTS);
        // nạp danh sách map từ maps.json — giữ TẤT CẢ map: có dải thì vẽ
        // bằng imagemap, không có dải thì vẽ bằng engine tile của gốc
        for (MapDef d : parseMapDefs(
                com.avatardk.game.GameFiles.data("data/maps.json").readString("UTF-8"))) {
            maps.add(d);
        }
        maps.sort((a, b) -> Integer.compare(a.id, b.id));
        // trạm xe buýt từ lưới ô gốc (ô 139) — docs/mapgrid-spec.md
        com.avatardk.game.data.MapGrids grids = new com.avatardk.game.data.MapGrids(
            com.avatardk.game.GameFiles.data("data/mapgrids.json").readString("UTF-8"));
        for (MapDef d : maps) {
            com.avatardk.game.data.MapGrids.Grid g = grids.grid(d.id);
            if (g == null) continue;
            if (g.hasStop()) {
                d.stopX = g.stopX();
                d.stopY = g.stopY();
                d.stopRow = g.stopRow();
                d.stopFirstTileX = g.stopFirstTileX();
                d.stopLastTileX = g.stopLastTileX();
            }
            if (g.o != null) {
                for (int[] o : g.o) d.objects.add(o);
            }
            d.types = g.types();
            d.drawTiles = g.drawTiles();
            // Va chạm vật thể: ô chứa chân (hàng đáy) của mỗi vật thể theo
            // bề ngang sprite — nhân vật không đi xuyên/lên vật thể được.
            d.gridW = g.w;
            d.gridH = g.w > 0 ? g.cells().length / g.w : 0;
            d.blocked = new boolean[g.cells().length];
            for (int[] o : d.objects) {
                if (o[0] < 0) continue;
                Texture t = objTexture(o[0]);
                if (t == null) continue;
                int halfW = t.getWidth() / 4; // hd -> nửa bề ngang theo medium
                int row = o[2] / 24;
                if (row < 0 || row >= d.gridH) continue;
                int c0 = Math.max(0, (int) Math.floor((o[1] - halfW) / 24.0));
                int c1 = Math.min(d.gridW - 1, (int) Math.floor((o[1] + halfW - 1) / 24.0));
                for (int c = c0; c <= c1; c++) d.blocked[row * d.gridW + c] = true;
            }
        }
        busTex = game.texture("sprites/bus.png");
        // lưới bản đồ thành phố: byte 69 = điểm vùng, thứ tự quét trên->dưới, trái->phải
        byte[] grid = com.avatardk.game.GameFiles.data("data/citymap.bin").readBytes();
        for (int i = 0; i < 26 && i * 50 < grid.length; i++)
            for (int j = 0; j < 50 && i * 50 + j < grid.length; j++)
                if (grid[i * 50 + j] == 69) regionCells.add(new int[]{j, i});
        // Danh sách biểu cảm: mọi part mặt (zOrder 40) dựng được offline —
        // không phải part động do server stream và đủ 15 frame như PaperDoll
        // yêu cầu. Doll xem trước = mặt nền (part 0) + part mặt, đúng cách
        // CommandFeel.paint của gốc vẽ.
        List<Integer> allIds = new ArrayList<>(game.data.parts.keySet());
        allIds.sort(Integer::compareTo);
        for (int id : allIds) {
            com.avatardk.game.data.PartData pd = game.data.part(id);
            if (pd != null && pd.zorder == 40 && !pd.isDynamic()
                && pd.frames.size() == com.avatardk.game.data.PaperDoll.FRAMES) {
                feelIds.add(id);
                feelDolls.add(game.newDoll(java.util.List.of(0, id)));
            }
        }
        loadMap(findMap(10));
        beginBusArrival();
    }

    /** Đọc maps.json bằng JsonReader (không dùng Json.fromJson phản
     *  chiếu — GWT không có bảng kiểu cho MapDef, đã kiểm chứng chết
     *  "Couldn't find Type" trên bản web 2026-11-02). */
    private static java.util.List<MapDef> parseMapDefs(String json) {
        java.util.List<MapDef> out = new java.util.ArrayList<>();
        JsonValue root = new JsonReader().parse(json);
        for (JsonValue r = root.child; r != null; r = r.next) {
            MapDef d = new MapDef();
            d.id = r.getInt("id", 0);
            d.name = r.getString("name", null);
            JsonValue bgv = r.get("bg");
            d.bg = bgv == null || bgv.isNull() ? null : bgv.asInt();
            d.strips = r.getInt("strips", 0);
            d.hmap = r.getInt("hmap", 0);
            d.daydien = r.getBoolean("daydien", false);
            JsonValue npcs = r.get("npcs");
            if (npcs != null) {
                for (JsonValue nv = npcs.child; nv != null; nv = nv.next) {
                    MapNpc mn = new MapNpc();
                    mn.id = nv.getInt("id", 0);
                    mn.name = nv.getString("name", null);
                    mn.x = nv.getInt("x", 0);
                    mn.y = nv.getInt("y", 0);
                    d.npcs.add(mn);
                }
            }
            out.add(d);
        }
        return out;
    }

    public MapDef findMap(int id) {
        for (MapDef d : maps) if (d.id == id) return d;
        return maps.get(0);
    }

    public void loadMap(MapDef def) {
        mapDef = def;
        if (def.id == 25) {
            // map Nông trại: nạp dữ liệu farm + tính vị trí các khối đất (ô 142)
            // và cây khế (ô 189) từ lưới map
            farm.load(game);
            com.avatardk.game.data.MapGrids.Grid g =
                new com.avatardk.game.data.MapGrids(
                    com.avatardk.game.GameFiles.data("data/mapgrids.json").readString("UTF-8"))
                    .grid(25);
            if (g != null) {
                farmBlocks = FarmWorld.plotBlocks(g.cells(), g.w);
                farmStarPos = FarmWorld.starPos(g.cells(), g.w,
                    g.w > 0 ? g.cells().length / g.w : 0);
            }
        }
        mapBottomPx = def.hmap * 24 * AvatarDkGame.HD;
        strips.clear(); stripLefts.clear(); daydien.clear();
        float acc = -1f;
        // Chiều cao thế giới = chiều cao LƯỚI của gốc (Hmap * 24 * hd), KHÔNG phải
        // chiều cao ảnh dải: dải cao hơn lưới thì phần thừa nhô lên trên (gốc vẽ ở
        // y âm), dải thấp hơn thì nền trời phủ phần còn lại — xem LoadMap.paintCreateMap.
        worldH = mapBottomPx;
        for (int i = 0; i < def.strips; i++) {
            Texture t = game.texture("imagemap/" + def.id + "/" + i + ".png");
            strips.add(t);
            stripLefts.add(acc);
            acc += t.getWidth() - 2;
        }
        worldW = acc + 1f;
        if (worldW <= 0) worldW = 1280f; // map chưa có dữ liệu (28/31/34/56/57): giữ khung an toàn
        if (def.daydien) {
            for (int i = 0; i < 4; i++) {
                String path = "imagemap/" + def.id + "/daydien" + i + ".png";
                if (!com.avatardk.game.GameFiles.data(path).exists()) break;
                daydien.add(game.texture(path));
            }
        }
        loadBackground(def);
        // engine tile (gốc: loadMapImage + FrameImage): map không có dải
        // imagemap thì bề ngang thế giới lấy theo lưới ô; atlas chọn theo
        // status ngày/đêm vừa tính ở loadBackground, riêng map 20 (phòng
        // cưới) dùng atlas hd/wedding của gốc. Frame 48px (24 * hd),
        // nFrame = chiều cao atlas / 48, frame v ở cột v/nFrame, hàng v%nFrame.
        tileTex = null;
        tileRegions = null;
        if (def.tileMode()) {
            worldW = def.gridW * 24f * AvatarDkGame.HD;
            tileTex = game.texture(def.id == 20 ? "tiles/wedding.png"
                : bgStatus == 1 ? "tiles/night.png" : "tiles/day.png");
            int frame = 24 * AvatarDkGame.HD;
            int nFrame = tileTex.getHeight() / frame;
            tileRegions = new com.badlogic.gdx.graphics.g2d.TextureRegion[256];
            for (int v = 0; v < 256; v++) {
                int col = v / nFrame, row = v % nFrame;
                if ((col + 1) * frame > tileTex.getWidth()
                    || (row + 1) * frame > tileTex.getHeight()) continue;
                tileRegions[v] = new com.badlogic.gdx.graphics.g2d.TextureRegion(
                    tileTex, col * frame, row * frame, frame, frame);
            }
        }
        buildNpcs();
        // giữ người chơi trong biên map mới
        playerX = MathUtils.clamp(playerX, 24, worldW / AvatarDkGame.HD - 24);
        playerY = MathUtils.clamp(playerY, 36, Math.max(60, def.hmap * 24 - 30));
        // nếu điểm hiện tại rơi vào ô cấm (đổi map), tìm ô đứng được
        // gần nhất theo vòng tròn ô mở rộng dần
        if (!canStand(playerX, playerY)) {
            boolean found = false;
            for (int r = 1; r <= 6 && !found; r++) {
                for (int dx = -r; dx <= r && !found; dx++) {
                    for (int dy = -r; dy <= r && !found; dy++) {
                        float tx = playerX + dx * 24, ty = playerY + dy * 24;
                        if (canStand(tx, ty)) { playerX = tx; playerY = ty; found = true; }
                    }
                }
            }
        }
    }

    /** Map không có nền (bg = null trong maps.json: bg bảng = -1 hoặc TYPEMAP
     *  ngoài bảng 0–25): LoadMap.loadBG trả imgBG = null và paintBackGround chỉ
     *  tô phẳng một màu tối (setColor(1)) — không trời, không mây, không silhouette. */
    private boolean centerVertically() {
        int typeMap = mapDef.id - 1;
        return typeMap == 57 || typeMap == 58 || typeMap == 59
            || (mapDef.bg == null && typeMap < 26)
            || (mapDef.tileMode() && mapDef.bg == null);
    }

    /** Nạp lớp nền đúng LoadMap.loadBG: status theo giờ máy (18h–6h = đêm),
     *  ảnh trời bghd/may<num><status> và ảnh silhouette bghd/<num><status>,
     *  mây trôi effect/cl<anchor><status>. */
    /** Dựng các mục vẽ farm (cây trên ô đất, cây khế, vật nuôi) vào cùng
     *  lượt sắp xếp độ sâu với vật thể/nhân vật. Tọa độ: đơn vị medium của
     *  gốc x HD; cây neo đáy–giữa tại chân ô như CellFarm.paint của gốc. */
    private void buildFarmDrawItems() {
        int hd = AvatarDkGame.HD;
        int gridH = mapDef.hmap;
        // cây trên từng ô đất
        if (farmBlocks != null) {
            for (int i = 0; i < farm.cells.size(); i++) {
                FarmWorld.Cell c = farm.cells.get(i);
                if (c.idTree < 0) continue;
                FarmWorld.TreeDef def = farm.trees.get(c.idTree);
                if (def == null || def.img.length == 0) continue;
                float[] pos = FarmWorld.cellPos(farmBlocks, i, gridH);
                if (pos == null) continue;
                int stage = farm.stageOf(c);
                int imgId = def.img[Math.min(stage, def.img.length - 1)];
                DrawItem it = new DrawItem();
                it.kind = DrawItem.FARM;
                it.y = pos[1] * hd;
                if (c.idTree < 30) {
                    // sheet bigFarm trong res của bản fan là bản medium -> phóng xHD
                    it.region = farm.staticRegion(imgId);
                    if (it.region == null) continue;
                    it.dw = it.region.getRegionWidth() * hd;
                    it.dh = it.region.getRegionHeight() * hd;
                } else {
                    it.tex = farm.dynTexture(imgId);
                    if (it.tex == null) continue;
                    it.dw = it.tex.getWidth();
                    it.dh = it.tex.getHeight();
                }
                it.x = pos[0] * hd - it.dw / 2f;
                it.y = pos[1] * hd; // chân ô — sprite vươn lên từ đây (paint BOTTOM của gốc)
                drawItems.add(it);
                // ô tiêu điểm có cây: thêm mục thông tin (thanh sức khoẻ +
                // % lớn + thời gian còn lại) như paint của CellFarm gốc
                if (farmFocus == i) {
                    DrawItem info = new DrawItem();
                    info.kind = DrawItem.FARM_INFO;
                    info.objId = i;
                    info.x = pos[0] * hd;
                    info.y = pos[1] * hd + it.dh + 46f;
                    drawItems.add(info);
                }
                // icon trạng thái: khô đất (w) / sâu (wg) / cỏ (g) của gốc
                String icon = c.arid ? "w" : (c.worm ? "wg" : (c.grass ? "g" : null));
                if (icon != null) {
                    Texture ic = game.texture("farm/ui/" + icon + ".png");
                    DrawItem ii = new DrawItem();
                    ii.kind = DrawItem.FARM;
                    ii.tex = ic;
                    ii.dw = ic.getWidth();
                    ii.dh = ic.getHeight();
                    ii.x = pos[0] * hd + 6f;
                    ii.y = pos[1] * hd + 4f + (float) Math.sin(netTime * 3 + i) * 2f;
                    drawItems.add(ii);
                }
            }
        }
        // Cây khế (ô 189): client gốc đặt StarFruitObj vẽ bằng ẢNH ĐỘNG
        // farm/img/<imgID>.png (43 = nguyên cây 146×130) neo đáy–giữa tại
        // ô, quả 46 rải trên tán. Thân cây KHÔNG nằm trong nền map — bản
        // cũ tưởng vậy nên cây biến mất hẳn. Một DrawItem FARM_STAR vẽ cả
        // cây + quả + nhãn để thứ tự sâu không tách rời nhau.
        if (farmStarPos != null) {
            Texture tree = farm.dynTexture(farm.starImg);
            if (tree != null) {
                DrawItem it = new DrawItem();
                it.kind = DrawItem.FARM_STAR;
                it.tex = tree;
                it.dw = tree.getWidth();
                it.dh = tree.getHeight();
                it.x = farmStarPos[0] * hd - it.dw / 2f;
                it.y = farmStarPos[1] * hd;
                drawItems.add(it);
            }
        }
        // vật nuôi theo khu, lang thang nhẹ quanh mỏ neo
        for (int i = 0; i < farm.animals.size(); i++) {
            FarmWorld.Animal a = farm.animals.get(i);
            FarmWorld.AnimalDef def = farm.animalDefs.get(a.species);
            if (def == null || def.idImg.length == 0) continue;
            long ageH = a.bornTime > 0
                ? (System.currentTimeMillis() / 1000L - a.bornTime) / 3600L : 9999;
            int stage = ageH >= def.harvestHours ? 2 : (ageH >= def.harvestHours / 2 ? 1 : 0);
            Texture t = farm.dynTexture(def.idImg[Math.min(stage, def.idImg.length - 1)]);
            if (t == null) continue; // loài chưa có file hình trong Pack5 (chờ đào từ server live)
            float[] anchor = FarmWorld.animalAnchor(def.area, i);
            float wx = anchor[0] + (float) Math.sin(netTime * 0.25f + a.phase) * 10f;
            DrawItem it = new DrawItem();
            it.kind = DrawItem.FARM;
            it.tex = t;
            it.dw = t.getWidth();
            it.dh = t.getHeight();
            it.x = wx * hd - it.dw / 2f;
            it.y = anchor[1] * hd;
            drawItems.add(it);
            if (farmAnimalFocus == i) { // nhãn thông tin trên con đang chọn
                DrawItem info = new DrawItem();
                info.kind = DrawItem.FARM_ANIMAL_INFO;
                info.objId = i;
                info.x = wx * hd;
                info.y = anchor[1] * hd + it.dh + 34f;
                drawItems.add(info);
            }
        }
    }

    /** Nền đất từng ô + mốc mua đất: gốc sơn cùng lớp map (dưới mọi vật
     *  thể/nhân vật) nên vẽ ngay sau các dải nền, không qua lượt sắp xếp sâu. */
    private void drawFarmSoil() {
        if (farmBlocks == null || !farm.joined) return;
        int hd = AvatarDkGame.HD;
        int totalSlots = farmBlocks.size() * 12;
        for (int i = 0; i < totalSlots; i++) {
            float[] pos = FarmWorld.cellPos(farmBlocks, i, mapDef.hmap);
            if (pos == null) continue;
            if (i < farm.cells.size()) {
                FarmWorld.Cell c = farm.cells.get(i);
                int status;
                if (c.level >= 2) status = c.idTree < 0 ? 5 : (c.arid ? 6 : 4);
                else status = c.idTree < 0 ? 1 : (c.arid ? 2 : 0);
                Texture t = game.texture("farm/ui/cell" + status + ".png");
                batch.draw(t, (pos[0] - 14f) * hd, (pos[1] - 8f) * hd);
                if (farmFocus == i) { // viền chọn ô tiêu điểm (imgFocusCel của gốc)
                    batch.draw(Ui.roundedRect(56, 60, 8, new Color(0, 0, 0, 0),
                        new Color(0.45f, 0.95f, 1f, 0.95f)),
                        (pos[0] - 14f) * hd - 2, (pos[1] - 8f) * hd - 2);
                }
            } else if (i == farm.cells.size()) {
                Texture t = game.texture("farm/ui/buyLand.png");
                batch.draw(t, pos[0] * hd - t.getWidth() / 2f, pos[1] * hd);
                if (farmFocus == i) {
                    batch.draw(Ui.roundedRect(48, 58, 8, new Color(0, 0, 0, 0),
                        new Color(0.45f, 0.95f, 1f, 0.95f)),
                        pos[0] * hd - 24, pos[1] * hd - 2);
                }
            }
        }
    }

    // ---- Farm GĐ2: ô tiêu điểm + thanh hành động + các panel chọn ----
    private int farmFocus = -1;
    private boolean farmSeedOpen, farmFertOpen, farmLandOpen;
    private String farmLandText = "";

    /** Ô tiêu điểm = ô đất gần người chơi nhất (gốc: ô dưới chân nhân vật);
     *  chỉ số == cells.size() là mốc mua đất. */
    private void updateFarmFocus() {
        farmFocus = -1;
        farmAnimalFocus = -1;
        if (mapDef == null || mapDef.id != 25 || !farm.joined || farmBlocks == null) return;
        float best = 32f;
        int n = Math.min(farm.cells.size() + 1, farmBlocks.size() * 12);
        for (int i = 0; i < n; i++) {
            float[] pos = FarmWorld.cellPos(farmBlocks, i, mapDef.hmap);
            if (pos == null) continue;
            float d = (float) Math.hypot(playerX - pos[0], playerY - (pos[1] - 4f));
            if (d < best) {
                best = d;
                farmFocus = i;
            }
        }
        // Vật nuôi gần nhất trong 38 đơn vị; thắng ô đất khi nó gần hơn ô.
        // Riêng cá (khu 4): cá bơi trong hồ, người đứng trên BỜ hồ nên đo
        // khoảng cách tới điểm bờ (848) thay vì tới vị trí con cá.
        float bestA = 38f;
        for (int i = 0; i < farm.animals.size(); i++) {
            FarmWorld.Animal a = farm.animals.get(i);
            FarmWorld.AnimalDef def = farm.animalDefs.get(a.species);
            if (def == null) continue;
            float[] anchor = FarmWorld.animalAnchor(def.area, i);
            float wx = anchor[0] + (float) Math.sin(netTime * 0.25f + a.phase) * 10f;
            if (def.area == 4) wx = 848f;
            float d = (float) Math.hypot(playerX - wx, playerY - anchor[1]);
            if (d < bestA) {
                bestA = d;
                farmAnimalFocus = i;
            }
        }
        if (farmAnimalFocus >= 0 && farmFocus >= 0 && farmFocus != farm.cells.size()
                && bestA > best) {
            farmAnimalFocus = -1; // ô đất gần hơn hẳn -> ưu tiên ô đất
        }
    }

    private int farmAnimalFocus = -1;

    private void farmSend(Msg m) {
        if (!online() || !farmIn) return;
        try {
            game.connection.send(m);
        } catch (Exception ignored) {
        }
    }

    private void farmPlant(int seedId) {
        Msg m = new Msg(AvatarProtocol.FARM_PLANT);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmFocus);
            m.writer().writeByte(seedId);
        } catch (Exception ignored) {
        }
        farmSend(m);
        farmSeedOpen = false;
    }

    private void farmUseItem(int itemId) {
        Msg m = new Msg(AvatarProtocol.FARM_USE_ITEM);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmFocus);
            m.writer().writeShort(itemId);
        } catch (Exception ignored) {
        }
        farmSend(m);
        if (itemId == 100 && farmFocus >= 0 && farmFocus < farm.cells.size()) {
            farm.cells.get(farmFocus).arid = false; // client gốc cũng cập nhật tại chỗ
        }
        farmFertOpen = false;
    }

    private void farmHarvestFocus() {
        Msg m = new Msg(AvatarProtocol.FARM_HARVEST);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmFocus);
        } catch (Exception ignored) {
        }
        farmSend(m);
    }

    private void farmAskLand() {
        Msg m = new Msg(AvatarProtocol.FARM_PRICE_SLOT);
        try {
            m.writer().writeInt(ownId());
        } catch (Exception ignored) {
        }
        farmSend(m);
        if (!online()) { // offline (harness): hiện hộp xác nhận bằng bảng giá gương
            farmLandText = "Bạn có muốn mở ô đất với giá "
                + fmtMoney(FarmWorld.landPriceXu(farm.cells.size()))
                + " xu hoặc " + FarmWorld.landPriceLuong(farm.cells.size()) + " lượng không ?";
            farmLandOpen = true;
        }
    }

    private void farmBuyLand(int type) {
        Msg m = new Msg(AvatarProtocol.FARM_OPEN_LAND);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(type);
        } catch (Exception ignored) {
        }
        farmSend(m);
        farmLandOpen = false;
    }

    private boolean farmHasFert() {
        for (int[] u : farm.usableItems) {
            if (u[0] >= 111 && u[0] <= 115 && u[1] > 0) return true;
        }
        return false;
    }

    // ---- Tương tác farm bằng NÚT TRÒN TƯƠNG TÁC (bên phải màn hình):
    // đi lại gần mục tiêu rồi bấm nút là LÀM NGAY hành động chính (chín
    // thì thu hoạch, khô thì tưới, đói thì cho ăn...) — KHÔNG bảng chọn
    // trung gian, KHÔNG chạm thẳng vào cây như bản gốc. Chỉ những việc
    // bắt buộc phải chọn mới mở panel: ô trống -> chọn hạt, cây đang lớn
    // cần bón -> chọn phân, mở ô -> chọn tiền, bán vật nuôi -> xác nhận.
    // Vị trí công trình trên map farm (đo từ ảnh dựng cảnh đã duyệt):
    // NHÀ BẾP ở x≈252, nhà kho rơm ở x≈346, hàng đứng tương tác y≈96.
    // Nhà kho chia hai nửa: đứng nửa TRÁI mở Kho, nửa PHẢI mở Cửa hàng.
    private static final float FARM_KITCHEN_X = 252f, FARM_SHED_X = 346f;
    private static final float FARM_BUILD_Y = 96f, FARM_BUILD_R = 80f;

    private boolean farmAnyPanelOpen() {
        return farmSeedOpen || farmFertOpen || farmLandOpen || farmStoreOpen
            || farmShopOpen || farmCookOpen || farmAnimalSellOpen;
    }

    /** Mục tiêu tương tác farm tại chỗ đứng hiện tại: 0 không có, 1 ô đất,
     *  2 vật nuôi, 3 nhà bếp, 4 nhà kho/cửa hàng, 5 mốc mở ô, 6 cây khế. */
    private int farmTargetKind() {
        if (mapDef == null || mapDef.id != 25 || !farm.joined) return 0;
        if (farmAnimalFocus >= 0) return 2;
        if (farmFocus >= 0) return farmFocus == farm.cells.size() ? 5 : 1;
        if (farmStarPos != null
                && Math.hypot(playerX - farmStarPos[0], playerY - farmStarPos[1]) <= 52f) return 6;
        // Hai công trình đứng gần nhau (bếp x252, kho x346): vùng 80 của
        // bếp chạm tới x332, chồng lên nửa trái nhà kho — phải chọn công
        // trình GẦN HƠN, không kiểm theo thứ tự cố định.
        double dKitchen = Math.hypot(playerX - FARM_KITCHEN_X, playerY - FARM_BUILD_Y);
        double dShed = Math.hypot(playerX - FARM_SHED_X, playerY - FARM_BUILD_Y);
        boolean inKitchen = dKitchen <= FARM_BUILD_R;
        boolean inShed = dShed <= FARM_BUILD_R;
        if (inKitchen && inShed) return dKitchen <= dShed ? 3 : 4;
        if (inKitchen) return 3;
        if (inShed) return 4;
        return 0;
    }

    /** Nhãn gợi ý hiện phía trên nút Tương tác: ghi đúng việc nút sẽ làm. */
    private String farmTargetLabel() {
        switch (farmTargetKind()) {
            case 1: {
                FarmWorld.Cell c = farm.cells.get(farmFocus);
                if (c.idTree < 0) return "Trồng cây";
                FarmWorld.TreeDef d = farm.trees.get(c.idTree);
                String n = d != null ? d.name : "Cây";
                if (c.hervestPer >= 100) return "Thu hoạch " + n;
                if (c.arid) return "Tưới nước";
                return farmHasFert() ? "Bón phân" : n + " đang lớn";
            }
            case 2: {
                FarmWorld.Animal a = farm.animals.get(farmAnimalFocus);
                FarmWorld.AnimalDef d = farm.animalDefs.get(a.species);
                String n = d != null ? d.name : "Vật nuôi";
                if (d != null && d.priceProduct > 0 && a.numEggOne > 0) return "Thu hoạch " + n;
                if (a.hunger && d != null && farmFoodFor(d.area) > 0) return "Cho ăn " + n;
                return "Bán " + n;
            }
            case 3: return "Nấu ăn";
            case 4: return playerX < FARM_SHED_X ? "Mở Kho" : "Mở Cửa hàng";
            case 5: return "Mở ô đất";
            case 6: return farm.starNum > 0 ? "Thu hoạch khế" : "Cây khế";
            default: return null;
        }
    }

    /** Nút tròn Tương tác khi đang ở trong farm: bấm là làm ngay. */
    private boolean farmInteract() {
        if (mapDef == null || mapDef.id != 25 || !farm.joined) return false;
        if (farmAnyPanelOpen()) return false;
        int kind = farmTargetKind();
        if (kind == 0) return false;
        if (kind == 5) { farmAskLand(); return true; }
        if (kind == 3) { farmCookOpen = true; return true; }
        if (kind == 4) {
            if (playerX < FARM_SHED_X) {
                farmStoreOpen = true;
            } else {
                farmShopOpen = true;
                farmShopTab = 0;
            }
            return true;
        }
        if (kind == 6) { farmHarvestStar(); return true; }
        if (kind == 2) {
            FarmWorld.Animal a = farm.animals.get(farmAnimalFocus);
            FarmWorld.AnimalDef def = farm.animalDefs.get(a.species);
            if (def != null && def.priceProduct > 0 && a.numEggOne > 0) {
                farmHarvestAnimalFocus();
            } else if (a.hunger && def != null && farmFoodFor(def.area) > 0) {
                farmFeedAnimalFocus();
            } else {
                farmAskSellAnimal();
            }
            return true;
        }
        // kind == 1: ô đất
        FarmWorld.Cell c = farm.cells.get(farmFocus);
        if (c.idTree < 0) {
            farmSeedOpen = true;
        } else if (c.hervestPer >= 100) {
            farmHarvestFocus();
        } else if (c.arid) {
            farmUseItem(100);
        } else if (farmHasFert()) {
            farmFertOpen = true;
        } else {
            FarmWorld.TreeDef def = farm.trees.get(c.idTree);
            addChatLog(TAB_SYSTEM, null,
                (def != null ? def.name : "Cây") + " đang lớn, chưa cần chăm gì.", true);
        }
        return true;
    }

    /** Thu hoạch cây khế (gói 85): server trả {short productId, short số quả}. */
    private void farmHarvestStar() {
        if (farm.starNum <= 0) {
            addChatLog(TAB_SYSTEM, null, "Cây khế chưa có quả chín.", true);
            return;
        }
        farmSend(new Msg(AvatarProtocol.FARM_HARVEST_STAR));
        if (!online()) { // offline (harness): giả lập thu tại chỗ
            int n = farm.starNum;
            farm.starNum = 0;
            addFarmProductLocal(215, n);
            addChatLog(TAB_SYSTEM, null, "Thu hoạch " + n + " quả khế.", true);
        }
    }

    /** Cộng nông sản vào kho local (gộp theo id) — dùng cho khế offline/online. */
    private void addFarmProductLocal(int productId, int num) {
        for (int[] p : farm.products) {
            if (p[0] == productId) { p[1] += num; return; }
        }
        farm.products.add(new int[]{productId, num});
    }

    // --- Panel chọn hạt (gieo từ kho giống) ---
    private static final float FS_PW = 640f, FS_PH = 470f;
    private static final float FS_PX = (Ui.W - FS_PW) / 2f, FS_PY = (Ui.H - FS_PH) / 2f;
    private int farmSeedPage;
    private final java.util.List<float[]> farmSeedBtns = new java.util.ArrayList<>();

    private void drawFarmSeedPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FS_PW, (int) FS_PH, 16, GLASS_FILL_DARK, GLASS_RIM), FS_PX, FS_PY);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Chọn hạt giống", FS_PX + 26, FS_PY + FS_PH - 30);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), FS_PX + FS_PW - 54, FS_PY + FS_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, FS_PX + FS_PW - 36, FS_PY + FS_PH - 36, new Color(1f, 1f, 1f, 0.9f));
        if (farm.seeds.isEmpty()) {
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.75f);
            Ui.centered(batch, Ui.fontSmall, "Chưa có hạt giống nào trong kho.",
                FS_PX + FS_PW / 2f, FS_PY + FS_PH / 2f + 20, new Color(1f, 1f, 1f, 0.75f));
            Ui.centered(batch, Ui.fontSmall, "Mua hạt ở cửa hàng trong nông trại.",
                FS_PX + FS_PW / 2f, FS_PY + FS_PH / 2f - 8, new Color(1f, 1f, 1f, 0.55f));
            return;
        }
        // Phân trang 15 hạt/trang (kho có thể đủ 25 loại hạt) — bản cũ
        // cắt cứng 15, hạt thứ 16 trở đi không trồng được.
        farmSeedBtns.clear();
        int seedPages = Math.max(1, (farm.seeds.size() + 14) / 15);
        if (farmSeedPage >= seedPages) farmSeedPage = seedPages - 1;
        if (farmSeedPage < 0) farmSeedPage = 0;
        int seedStart = farmSeedPage * 15;
        float cw = 112f, ch = 112f, gx = 14f, gy = 10f;
        float x0 = FS_PX + (FS_PW - (5 * cw + 4 * gx)) / 2f, yTop = FS_PY + FS_PH - 70f;
        for (int i = seedStart; i < farm.seeds.size() && i < seedStart + 15; i++) {
            int k = i - seedStart;
            int[] s = farm.seeds.get(i);
            FarmWorld.TreeDef def = farm.trees.get(s[0]);
            if (def == null) continue;
            float cx = x0 + (k % 5) * (cw + gx);
            float cy = yTop - (k / 5) * (ch + gy) - ch;
            batch.draw(Ui.roundedRect((int) cw, (int) ch, 12,
                new Color(0.10f, 0.16f, 0.24f, 0.9f), GLASS_RIM), cx, cy);
            int iconId = def.img.length > 7 ? def.img[7] : (def.img.length > 0 ? def.img[0] : 0);
            if (s[0] < 30) {
                com.badlogic.gdx.graphics.g2d.TextureRegion rg = farm.staticRegion(iconId);
                if (rg != null) batch.draw(rg, cx + cw / 2f - 22, cy + ch - 58, 44, 44);
            } else {
                Texture tx = farm.dynTexture(iconId);
                if (tx != null) batch.draw(tx, cx + cw / 2f - 22, cy + ch - 58,
                    Math.min(44, tx.getWidth()), Math.min(44, tx.getHeight()));
            }
            Ui.centered(batch, Ui.fontSmall, truncTo(Ui.fontSmall, def.name, cw - 8), cx + cw / 2f, cy + 40, new Color(1f, 1f, 1f, 0.92f));
            Ui.centered(batch, Ui.fontSmall, "x" + s[1], cx + cw / 2f, cy + 19, new Color(1f, 0.85f, 0.38f, 1f));
        }
        drawFarmPager(farmSeedBtns, FS_PX + FS_PW / 2f, FS_PY + 6, farmSeedPage, seedPages);
    }

    private void farmSeedTouch(float x, float y) {
        if (x >= FS_PX + FS_PW - 54 && x <= FS_PX + FS_PW - 18
                && y >= FS_PY + FS_PH - 54 && y <= FS_PY + FS_PH - 18) {
            farmSeedOpen = false;
            return;
        }
        for (float[] b : farmSeedBtns) {
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) {
                if ((int) b[4] == -200) farmSeedPage = Math.max(0, farmSeedPage - 1);
                else if ((int) b[4] == -201) farmSeedPage++;
                return;
            }
        }
        float cw = 112f, ch = 112f, gx = 14f, gy = 10f;
        float x0 = FS_PX + (FS_PW - (5 * cw + 4 * gx)) / 2f, yTop = FS_PY + FS_PH - 70f;
        int seedStart = farmSeedPage * 15;
        for (int i = seedStart; i < farm.seeds.size() && i < seedStart + 15; i++) {
            int k = i - seedStart;
            float cx = x0 + (k % 5) * (cw + gx);
            float cy = yTop - (k / 5) * (ch + gy) - ch;
            if (x >= cx && x <= cx + cw && y >= cy && y <= cy + ch) {
                farmPlant(farm.seeds.get(i)[0]);
                return;
            }
        }
        if (x < FS_PX || x > FS_PX + FS_PW || y < FS_PY || y > FS_PY + FS_PH) farmSeedOpen = false;
    }

    // --- Panel chọn phân bón ---
    private static final float FF_PW = 540f;
    private static final float FF_PX = (Ui.W - FF_PW) / 2f;

    private java.util.List<int[]> farmFertList() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int[] u : farm.usableItems) {
            if (u[0] >= 111 && u[0] <= 115 && u[1] > 0) out.add(u);
        }
        return out;
    }

    private float farmFertH() {
        return 120f + farmFertList().size() * 62f;
    }

    private void drawFarmFertPanel() {
        float ph = farmFertH(), py = (Ui.H - ph) / 2f;
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FF_PW, (int) ph, 16, GLASS_FILL_DARK, GLASS_RIM), FF_PX, py);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Bón phân", FF_PX + 26, py + ph - 30);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), FF_PX + FF_PW - 54, py + ph - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, FF_PX + FF_PW - 36, py + ph - 36, new Color(1f, 1f, 1f, 0.9f));
        java.util.List<int[]> list = farmFertList();
        for (int i = 0; i < list.size(); i++) {
            int[] u = list.get(i);
            float ry = py + ph - 76f - (i + 1) * 62f + 8f;
            batch.draw(Ui.roundedRect((int) FF_PW - 40, 54, 12,
                new Color(0.10f, 0.16f, 0.24f, 0.9f), GLASS_RIM), FF_PX + 20, ry);
            Texture tx = farm.dynTexture(FarmWorld.fertImg(u[0]));
            if (tx != null) batch.draw(tx, FF_PX + 34, ry + 11, 32, 32);
            Ui.font.setColor(1f, 1f, 1f, 0.95f);
            Ui.font.draw(batch, FarmWorld.fertName(u[0]), FF_PX + 78, ry + 36);
            Ui.centered(batch, Ui.fontSmall, "x" + u[1], FF_PX + FF_PW - 52, ry + 33,
                new Color(1f, 0.85f, 0.38f, 1f));
        }
    }

    private void farmFertTouch(float x, float y) {
        float ph = farmFertH(), py = (Ui.H - ph) / 2f;
        if (x >= FF_PX + FF_PW - 54 && x <= FF_PX + FF_PW - 18
                && y >= py + ph - 54 && y <= py + ph - 18) {
            farmFertOpen = false;
            return;
        }
        java.util.List<int[]> list = farmFertList();
        for (int i = 0; i < list.size(); i++) {
            float ry = py + ph - 76f - (i + 1) * 62f + 8f;
            if (x >= FF_PX + 20 && x <= FF_PX + FF_PW - 20 && y >= ry && y <= ry + 54) {
                farmUseItem(list.get(i)[0]);
                return;
            }
        }
        if (x < FF_PX || x > FF_PX + FF_PW || y < py || y > py + ph) farmFertOpen = false;
    }

    // --- Hộp xác nhận mở ô đất ---
    private static final float FL_PW = 580f, FL_PH = 300f;
    private static final float FL_PX = (Ui.W - FL_PW) / 2f, FL_PY = (Ui.H - FL_PH) / 2f;

    private void drawFarmLandPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FL_PW, (int) FL_PH, 16, GLASS_FILL_DARK, GLASS_RIM), FL_PX, FL_PY);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Mở ô đất", FL_PX + 26, FL_PY + FL_PH - 30);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), FL_PX + FL_PW - 54, FL_PY + FL_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, FL_PX + FL_PW - 36, FL_PY + FL_PH - 36, new Color(1f, 1f, 1f, 0.9f));
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.9f);
        layout.setText(Ui.fontSmall, farmLandText);
        float tw = layout.width;
        if (tw > FL_PW - 52) { // rút gọn hiển thị nếu quá dài (đo thực tế bằng layout)
            Ui.fontSmall.draw(batch, farmLandText, FL_PX + 26, FL_PY + FL_PH - 96);
        } else {
            Ui.fontSmall.draw(batch, farmLandText, FL_PX + (FL_PW - tw) / 2f, FL_PY + FL_PH - 96);
        }
        float bw = 216f, bh = 56f, by = FL_PY + 34f;
        float bx1 = FL_PX + 40f, bx2 = FL_PX + FL_PW - 40f - bw;
        batch.draw(Ui.gradientRect((int) bw, (int) bh, 14, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx1, by);
        Ui.centered(batch, Ui.font, "Mua bằng Xu", bx1 + bw / 2f, by + 37, new Color(1f, 1f, 1f, 1f));
        batch.draw(Ui.gradientRect((int) bw, (int) bh, 14, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx2, by);
        Ui.centered(batch, Ui.font, "Mua bằng Lượng", bx2 + bw / 2f, by + 37, new Color(1f, 1f, 1f, 1f));
    }

    private void farmLandTouch(float x, float y) {
        if (x >= FL_PX + FL_PW - 54 && x <= FL_PX + FL_PW - 18
                && y >= FL_PY + FL_PH - 54 && y <= FL_PY + FL_PH - 18) {
            farmLandOpen = false;
            return;
        }
        float bw = 216f, bh = 56f, by = FL_PY + 34f;
        float bx1 = FL_PX + 40f, bx2 = FL_PX + FL_PW - 40f - bw;
        if (y >= by && y <= by + bh) {
            if (x >= bx1 && x <= bx1 + bw) { farmBuyLand(1); return; }
            if (x >= bx2 && x <= bx2 + bw) { farmBuyLand(2); return; }
        }
        if (x < FL_PX || x > FL_PX + FL_PW || y < FL_PY || y > FL_PY + FL_PH) farmLandOpen = false;
    }

    /** Mở panel chọn hạt cho harness chụp kiểm chứng. */
    public void debugOpenFarmSeeds() {
        farmSeedOpen = true;
    }

    // ---- Farm GĐ3: panel kho (bán nông sản) + shop farm — mở bằng nút
    // Tương tác tại nhà kho (xem farmInteract), không còn nút nổi. ----
    private boolean farmStoreOpen, farmShopOpen;
    private int farmShopTab; // 0 = hạt giống, 1 = phân bón/vật phẩm
    private int farmShopPage; // trang hiện tại trong tab cửa hàng farm

    private void farmBuy(int id, int type) {
        Msg m = new Msg(AvatarProtocol.FARM_BUY_ITEM);
        try {
            m.writer().writeShort(id);
            m.writer().writeByte(1);
            m.writer().writeByte(type);
        } catch (Exception ignored) {
        }
        farmSend(m);
    }

    private void farmSell(int id) {
        Msg m = new Msg(AvatarProtocol.FARM_SELL);
        try {
            m.writer().writeShort(id);
        } catch (Exception ignored) {
        }
        farmSend(m);
    }

    /** Đầu panel farm dùng chung: huy hiệu icon FA trong ô màu + tiêu đề
     *  vàng + dòng phụ + nút tắt ô vuông đỏ theo hệ nút thống nhất. */
    private void drawFarmPanelHeader(float px, float py, float pw, float ph,
            String icon, Color iconBg, String title, String sub) {
        batch.draw(Ui.roundedRect(46, 46, 12, iconBg, GLASS_RIM), px + 22, py + ph - 70);
        Ui.faIcon(batch, Ui.fa26, icon, px + 45, py + ph - 47, new Color(1f, 1f, 1f, 0.95f));
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, title, px + 82, py + ph - 30);
        if (sub != null) {
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.62f);
            Ui.fontSmall.draw(batch, sub, px + 82, py + ph - 58);
        }
        Ui.font.setColor(Color.WHITE);
        Ui.fontSmall.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + pw - 54, py + ph - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + pw - 36, py + ph - 36,
            new Color(1f, 1f, 1f, 0.9f));
    }

    /** Thẻ mục trong panel farm: nền bo góc + icon trong ô medallion. */
    private void drawFarmCardBg(float x, float y, float w, float h) {
        batch.draw(Ui.roundedRect((int) w, (int) h, 12,
            new Color(0.07f, 0.12f, 0.19f, 0.88f), GLASS_RIM), x, y);
    }

    private void drawFarmMedallion(float x, float y, float size) {
        batch.draw(Ui.roundedRect((int) size, (int) size, 10,
            new Color(0.16f, 0.24f, 0.34f, 0.95f), GLASS_RIM), x, y);
    }

    /** Nhãn số lượng kiểu "x5" canh phải tại (rightX, baselineY). */
    private void drawFarmQtyPill(float rightX, float centerY, String text) {
        layout.setText(Ui.fontSmall, text);
        float w = layout.width + 18f, h = 24f;
        batch.draw(Ui.roundedRect((int) w, (int) h, 8,
            new Color(0.10f, 0.17f, 0.26f, 0.95f), GLASS_RIM), rightX - w, centerY - h / 2f);
        Ui.fontSmall.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.fontSmall.draw(batch, text, rightX - w + 9, centerY + 6);
        Ui.fontSmall.setColor(Color.WHITE);
    }

    // --- Panel KHO nông trại ---
    private static final float FK_PW = 800f, FK_PH = 560f;
    private static final float FK_PX = (Ui.W - FK_PW) / 2f, FK_PY = (Ui.H - FK_PH) / 2f;
    private final java.util.List<float[]> farmSellBtns = new java.util.ArrayList<>();

    /** Hàng phân trang dùng chung cho các panel farm: nút < > + nhãn
     *  "Trang a/b", ghi nút vào btns với mã -200 (trước) / -201 (sau).
     *  Chỉ vẽ khi có từ 2 trang trở lên. */
    private void drawFarmPager(java.util.List<float[]> btns, float cx, float y, int page, int pages) {
        if (pages <= 1) return;
        float bw = 52f, bh = 34f;
        float bx1 = cx - 132f;
        batch.draw(Ui.roundedRect((int) bw, (int) bh, 10,
            page > 0 ? GLASS_FILL : new Color(0.22f, 0.22f, 0.25f, 0.9f), GLASS_RIM), bx1, y);
        Ui.centered(batch, Ui.font, "<", bx1 + bw / 2f, y + 25,
            new Color(1f, 1f, 1f, page > 0 ? 0.95f : 0.35f));
        if (page > 0) btns.add(new float[]{bx1, y, bw, bh, -200});
        float bx2 = cx + 132f - bw;
        batch.draw(Ui.roundedRect((int) bw, (int) bh, 10,
            page < pages - 1 ? GLASS_FILL : new Color(0.22f, 0.22f, 0.25f, 0.9f), GLASS_RIM), bx2, y);
        Ui.centered(batch, Ui.font, ">", bx2 + bw / 2f, y + 25,
            new Color(1f, 1f, 1f, page < pages - 1 ? 0.95f : 0.35f));
        if (page < pages - 1) btns.add(new float[]{bx2, y, bw, bh, -201});
        Ui.centered(batch, Ui.fontSmall, "Trang " + (page + 1) + "/" + pages, cx, y + 23,
            new Color(1f, 1f, 1f, 0.75f));
    }

    /** Kẹp vùng vẽ trong khung panel: bảo hiểm chống tràn tuyệt đối trên
     *  mọi bản build (kể cả khi metrics font lệch nhau giữa các nền). */
    private boolean pushPanelClip(float x, float y, float w, float h) {
        Rectangle clip = new Rectangle(x, y, w, h);
        Rectangle scissors = new Rectangle();
        batch.flush();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        return ScissorStack.pushScissors(scissors);
    }

    private void popPanelClip() {
        batch.flush();
        ScissorStack.popScissors();
    }

    /** Cắt ngắn chuỗi theo bề rộng ĐO THẬT của font (thêm "…" khi cắt). */
    private String truncTo(com.badlogic.gdx.graphics.g2d.BitmapFont font, String s, float maxW) {
        layout.setText(font, s);
        if (layout.width <= maxW) return s;
        while (s.length() > 3) {
            s = s.substring(0, s.length() - 1);
            layout.setText(font, s + "…");
            if (layout.width <= maxW) return s + "…";
        }
        return s;
    }

    // --- Panel KHO nông trại: 3 tab dạng lưới thẻ như Cửa hàng, có phân
    // trang — bố cục cũ 2 cột (thẻ hạt cao không giới hạn) tràn khung khi
    // người chơi có nhiều loại hạt/nông sản nên đã thay hẳn. ---
    private int farmStoreTab = 0;   // 0 hạt, 1 vật phẩm, 2 nông sản
    private int farmStorePage = 0;

    private java.util.List<int[]> farmStoreList() {
        if (farmStoreTab == 0) return farm.seeds;
        if (farmStoreTab == 1) return farm.usableItems;
        java.util.List<int[]> all = new java.util.ArrayList<>();
        all.addAll(farm.products);
        all.addAll(farm.specials);
        return all;
    }

    private void drawFarmStorePanel() {
        farmSellBtns.clear();
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FK_PW, (int) FK_PH, 16, GLASS_FILL_DARK, GLASS_RIM), FK_PX, FK_PY);
        drawFarmPanelHeader(FK_PX, FK_PY, FK_PW, FK_PH, Ui.FA_BOX_ARCHIVE,
            new Color(0.10f, 0.45f, 0.55f, 0.95f), "Kho nông trại",
            "Cấp " + farm.lvFarm + " · Sức chứa " + fmtMoney(farm.capacity)
                + (farmStoreTab == 2 ? " · Bấm Bán để bán cả chồng" : ""));
        // 3 tab (mã nút -100-tab)
        String[] tabs = {"Hạt giống", "Vật phẩm", "Nông sản"};
        float tw = 168f, th = 42f, tx0 = FK_PX + 26, ty = FK_PY + FK_PH - 122f;
        for (int i = 0; i < 3; i++) {
            float tx = tx0 + i * (tw + 10);
            if (farmStoreTab == i) {
                batch.draw(Ui.gradientRect((int) tw, (int) th, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), tx, ty);
            } else {
                batch.draw(Ui.roundedRect((int) tw, (int) th, 12, GLASS_FILL, GLASS_RIM), tx, ty);
            }
            Ui.centered(batch, Ui.fontSmall, tabs[i], tx + tw / 2f, ty + 28, new Color(1f, 1f, 1f, 0.95f));
            farmSellBtns.add(new float[]{tx, ty, tw, th, -100 - i});
        }
        float gridTop = ty - 12f;
        java.util.List<int[]> list = farmStoreList();
        int perPage = 15;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        if (farmStorePage >= pages) farmStorePage = pages - 1;
        if (farmStorePage < 0) farmStorePage = 0;
        // lưới thẻ 5 x 3, kẹp trong khung panel
        float cw = 140f, ch = 126f, gx = 12f, gy = 10f;
        float x0 = FK_PX + (FK_PW - (5 * cw + 4 * gx)) / 2f;
        boolean clipped = pushPanelClip(FK_PX + 6, FK_PY + 52, FK_PW - 12, gridTop - FK_PY - 52);
        if (list.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall,
                farmStoreTab == 2 ? "Chưa thu hoạch gì" : "Chưa có gì trong mục này",
                FK_PX + FK_PW / 2f, gridTop - 120, new Color(1f, 1f, 1f, 0.55f));
        }
        int start = farmStorePage * perPage;
        for (int k = 0; k < perPage && start + k < list.size(); k++) {
            int[] e = list.get(start + k);
            float cx = x0 + (k % 5) * (cw + gx);
            float cy = gridTop - (k / 5 + 1) * (ch + gy) + gy;
            batch.draw(Ui.roundedRect((int) cw, (int) ch, 12,
                new Color(0.09f, 0.14f, 0.22f, 0.92f), GLASS_RIM), cx, cy);
            if (farmStoreTab == 2) {
                drawFarmStoreProductCard(cx, cy, cw, ch, e);
            } else if (farmStoreTab == 0) {
                FarmWorld.TreeDef def = farm.trees.get(e[0]);
                if (def == null) continue;
                drawFarmMedallion(cx + cw / 2f - 26, cy + ch - 64, 52);
                drawFarmIcon(def, e[0], cx + cw / 2f - 20, cy + ch - 58, 40);
                drawFarmCardName(cx, cw, cy, def.name);
                drawFarmQtyPillCentered(cx + cw / 2f, cy + 18, "x" + e[1]);
            } else {
                FarmWorld.FarmItemDef fd = farm.farmItemDefs.get(e[0]);
                drawFarmMedallion(cx + cw / 2f - 26, cy + ch - 64, 52);
                Texture tx = fd != null ? farm.dynTexture(fd.img) : null;
                if (tx != null) batch.draw(tx, cx + cw / 2f - 20, cy + ch - 58, 40, 40);
                drawFarmCardName(cx, cw, cy, fd != null ? fd.name : FarmWorld.fertName(e[0]));
                drawFarmQtyPillCentered(cx + cw / 2f, cy + 18, "x" + e[1]);
            }
        }
        if (clipped) popPanelClip();
        drawFarmPager(farmSellBtns, FK_PX + FK_PW / 2f, FK_PY + 13, farmStorePage, pages);
    }

    /** Nhãn số lượng canh giữa theo tâm x cho trước. */
    private void drawFarmQtyPillCentered(float centerX, float centerY, String text) {
        layout.setText(Ui.fontSmall, text);
        drawFarmQtyPill(centerX + (layout.width + 18f) / 2f, centerY, text);
    }

    /** Tên trong thẻ lưới: 1 dòng canh giữa, dài thì xuống 2 dòng (đo thật). */
    private void drawFarmCardName(float cx, float cw, float cy, String name) {
        layout.setText(Ui.fontSmall, name);
        if (layout.width > cw - 10 && name.contains(" ")) {
            int sp = name.lastIndexOf(' ');
            Ui.centered(batch, Ui.fontSmall, name.substring(0, sp), cx + cw / 2f, cy + 58,
                new Color(1f, 1f, 1f, 0.92f));
            Ui.centered(batch, Ui.fontSmall, name.substring(sp + 1), cx + cw / 2f, cy + 41,
                new Color(1f, 1f, 1f, 0.92f));
        } else {
            Ui.centered(batch, Ui.fontSmall, truncTo(Ui.fontSmall, name, cw - 10),
                cx + cw / 2f, cy + 50, new Color(1f, 1f, 1f, 0.92f));
        }
    }

    /** Thẻ nông sản trong Kho: icon + tên + giá/tổng + nút Bán + số lượng. */
    private void drawFarmStoreProductCard(float cx, float cy, float cw, float ch, int[] e) {
        FarmWorld.ProductDef pd = farm.productDefs.get(e[0]);
        String name = pd != null ? pd.name : ("Sản phẩm " + e[0]);
        int price = pd != null ? pd.priceXu : 0;
        // Bố cục đo lại sau khi chụp thấy dòng giá đè lên nút Bán: mỗi
        // dòng chữ cách nhau >= 20px baseline, nút thấp xuống đáy thẻ.
        drawFarmMedallion(cx + cw / 2f - 19, cy + 78, 38);
        drawProductIcon(e[0], pd, cx + cw / 2f - 14, cy + 83, 28);
        Ui.centered(batch, Ui.fontSmall, truncTo(Ui.fontSmall, name, cw - 10),
            cx + cw / 2f, cy + 70, new Color(1f, 1f, 1f, 0.97f));
        if (price > 0) {
            // Dòng giá: đủ chỗ thì kèm tổng tiền, chật thì chỉ giá/cái —
            // không cắt giữa chữ (bản chụp trước ra chữ "tổn..." rất xấu).
            String priceLine = fmtMoney(price) + " xu/cái · tổng " + fmtMoney(price * e[1]) + " xu";
            layout.setText(Ui.fontSmall, priceLine);
            if (layout.width > cw - 10) priceLine = fmtMoney(price) + " xu/cái";
            Ui.centered(batch, Ui.fontSmall, priceLine,
                cx + cw / 2f, cy + 50, new Color(1f, 1f, 1f, 0.62f));
            float bw = 76f, bh = 24f, bx = cx + 8, byy = cy + 4;
            batch.draw(Ui.gradientRect((int) bw, (int) bh, 9, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, byy);
            Ui.centered(batch, Ui.fontSmall, "Bán", bx + bw / 2f, byy + 17, new Color(1f, 1f, 1f, 1f));
            farmSellBtns.add(new float[]{bx, byy, bw, bh, e[0]});
            Ui.fontSmall.setColor(1f, 0.85f, 0.38f, 1f);
            layout.setText(Ui.fontSmall, "x" + e[1]);
            Ui.fontSmall.draw(batch, "x" + e[1], cx + cw - 10 - layout.width, cy + 21);
            Ui.fontSmall.setColor(Color.WHITE);
        } else {
            Ui.centered(batch, Ui.fontSmall, "Chưa bán được", cx + cw / 2f, cy + 43,
                new Color(1f, 1f, 1f, 0.5f));
            Ui.centered(batch, Ui.fontSmall, "x" + e[1], cx + cw / 2f, cy + 20,
                new Color(1f, 0.85f, 0.38f, 1f));
        }
    }

    /** Icon cây (giai đoạn icon img[7]) tại (x, y) cỡ size. */
    private void drawFarmIcon(FarmWorld.TreeDef def, int treeId, float x, float y, float size) {
        int iconId = def.img.length > 7 ? def.img[7] : (def.img.length > 0 ? def.img[0] : 0);
        if (treeId < 30) {
            com.badlogic.gdx.graphics.g2d.TextureRegion rg = farm.staticRegion(iconId);
            if (rg != null) batch.draw(rg, x, y, size, size);
        } else {
            Texture tx = farm.dynTexture(iconId);
            if (tx != null) batch.draw(tx, x, y, size, size);
        }
    }

    /** Icon nông sản: cây tĩnh dùng icon cây; còn lại dùng ảnh sản phẩm (img của dat). */
    private void drawProductIcon(int productId, FarmWorld.ProductDef pd, float x, float y, float size) {
        if (productId >= 0 && productId < 30) {
            FarmWorld.TreeDef def = farm.trees.get(productId);
            if (def != null) {
                drawFarmIcon(def, productId, x, y, size);
                return;
            }
        }
        if (pd != null && pd.img >= 0) {
            Texture tx = farm.dynTexture(pd.img);
            if (tx != null) {
                batch.draw(tx, x, y, size, size);
                return;
            }
            com.badlogic.gdx.graphics.g2d.TextureRegion rg = farm.staticRegion(pd.img);
            if (rg != null) batch.draw(rg, x, y, size, size);
        }
    }

    private void farmStoreTouch(float x, float y) {
        if (x >= FK_PX + FK_PW - 54 && x <= FK_PX + FK_PW - 18
                && y >= FK_PY + FK_PH - 54 && y <= FK_PY + FK_PH - 18) {
            farmStoreOpen = false;
            return;
        }
        for (float[] b : farmSellBtns) {
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) {
                int code = (int) b[4];
                if (code <= -100 && code > -200) { // tab
                    farmStoreTab = -100 - code;
                    farmStorePage = 0;
                } else if (code == -200) {
                    farmStorePage = Math.max(0, farmStorePage - 1);
                } else if (code == -201) {
                    farmStorePage++;
                } else if (code >= 0) {
                    farmSell(code);
                }
                return;
            }
        }
        if (x < FK_PX || x > FK_PX + FK_PW || y < FK_PY || y > FK_PY + FK_PH) farmStoreOpen = false;
    }

    // --- Panel CỬA HÀNG farm (mua hạt + vật phẩm) ---
    private static final float FH_PW = 720f, FH_PH = 560f;
    private static final float FH_PX = (Ui.W - FH_PW) / 2f, FH_PY = (Ui.H - FH_PH) / 2f;
    private final java.util.List<float[]> farmShopBtns = new java.util.ArrayList<>();

    /** Nhãn giá nhỏ canh giữa trong thẻ cửa hàng farm. */
    private void drawFarmPricePill(float cx, float y, String text) {
        layout.setText(Ui.fontSmall, text);
        float w = layout.width + 20f, h = 26f;
        batch.draw(Ui.roundedRect((int) w, (int) h, 9,
            new Color(0.13f, 0.20f, 0.30f, 0.95f), GLASS_RIM), cx - w / 2f, y);
        Ui.centered(batch, Ui.fontSmall, text, cx, y + 19, new Color(1f, 0.85f, 0.38f, 1f));
    }

    private void drawFarmShopPanel() {
        farmShopBtns.clear();
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FH_PW, (int) FH_PH, 16, GLASS_FILL_DARK, GLASS_RIM), FH_PX, FH_PY);
        drawFarmPanelHeader(FH_PX, FH_PY, FH_PW, FH_PH, Ui.FA_STORE,
            new Color(0.16f, 0.52f, 0.24f, 0.95f), "Cửa hàng nông trại",
            "Bạn có " + fmtMoney(myXu) + " xu · " + fmtMoney(myLuong) + " lượng — bấm vào món để mua");
        // 3 tab
        String[] tabs = {"Hạt giống", "Vật phẩm", "Vật nuôi"};
        float tw = 168f, th = 42f, tx0 = FH_PX + 26, ty = FH_PY + FH_PH - 120f;
        for (int i = 0; i < 3; i++) {
            float tx = tx0 + i * (tw + 10);
            if (farmShopTab == i) {
                batch.draw(Ui.gradientRect((int) tw, (int) th, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), tx, ty);
            } else {
                batch.draw(Ui.roundedRect((int) tw, (int) th, 12, GLASS_FILL, GLASS_RIM), tx, ty);
            }
            Ui.centered(batch, Ui.fontSmall, tabs[i], tx + tw / 2f, ty + 28, new Color(1f, 1f, 1f, 0.95f));
            farmShopBtns.add(new float[]{tx, ty, tw, th, 100 + i});
        }
        float gridTop = ty - 12f;
        // Phân trang: hạt có 25 loại (2 trang), vật phẩm 14 (3 trang x 5),
        // vật nuôi 9 (1 trang). Bản cũ cắt cứng 15 hạt / 6 vật phẩm mà
        // không có cách xem tiếp, và hàng thẻ thứ 3 tràn đáy panel 14px —
        // hình học đã đo lại toàn bộ (đáy hàng 3 = FH_PY + 16).
        int total = farmShopTab == 0 ? farm.trees.size()
            : farmShopTab == 1 ? farm.farmItemDefs.size() : farm.animalDefs.size();
        int perPage = farmShopTab == 1 ? 5 : 15;
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        if (farmShopPage >= pages) farmShopPage = pages - 1;
        if (farmShopPage < 0) farmShopPage = 0;
        int start = farmShopPage * perPage;
        boolean clipped = pushPanelClip(FH_PX + 6, FH_PY + 50, FH_PW - 12, gridTop - FH_PY - 50);
        if (farmShopTab == 0) {
            // lưới hạt: thẻ có medallion icon + tên + nhãn giá
            float cw = 122f, ch = 132f, gx = 12f, gy = 8f;
            float x0 = FH_PX + (FH_PW - (5 * cw + 4 * gx)) / 2f;
            int i = 0;
            for (FarmWorld.TreeDef def : farm.trees.values()) {
                if (i < start) { i++; continue; }
                if (i >= start + perPage) break;
                int k = i - start;
                float cx = x0 + (k % 5) * (cw + gx);
                float cy = gridTop - (k / 5 + 1) * (ch + gy) + gy;
                batch.draw(Ui.roundedRect((int) cw, (int) ch, 12,
                    new Color(0.09f, 0.14f, 0.22f, 0.92f), GLASS_RIM), cx, cy);
                drawFarmMedallion(cx + cw / 2f - 25, cy + ch - 59, 50);
                drawFarmIcon(def, def.id, cx + cw / 2f - 19, cy + ch - 53, 38);
                // tên dài (vd "Hoa hướng dương") xuống 2 dòng; mực dòng 2
                // kết thúc ở cy+33, nhãn giá bắt đầu cy+32 — không chạm.
                layout.setText(Ui.fontSmall, def.name);
                if (layout.width > cw - 8 && def.name.contains(" ")) {
                    int sp = def.name.lastIndexOf(' ');
                    Ui.centered(batch, Ui.fontSmall, def.name.substring(0, sp), cx + cw / 2f, cy + 67, new Color(1f, 1f, 1f, 0.92f));
                    Ui.centered(batch, Ui.fontSmall, def.name.substring(sp + 1), cx + cw / 2f, cy + 50, new Color(1f, 1f, 1f, 0.92f));
                } else {
                    Ui.centered(batch, Ui.fontSmall, truncTo(Ui.fontSmall, def.name, cw - 8), cx + cw / 2f, cy + 57, new Color(1f, 1f, 1f, 0.92f));
                }
                String price = def.seedXu > 0 ? fmtMoney(def.seedXu) + " xu" : def.seedLuong + " lượng";
                drawFarmPricePill(cx + cw / 2f, cy + 6, price);
                farmShopBtns.add(new float[]{cx, cy, cw, ch, def.id});
                i++;
            }
        } else if (farmShopTab == 2) {
            // lưới vật nuôi: medallion + tên + giá — bấm là mua 1 con.
            // Icon loài chưa có trong Pack5 thì vẽ chữ cái đầu trong medallion.
            float cw = 122f, ch = 132f, gx = 12f, gy = 8f;
            float x0 = FH_PX + (FH_PW - (5 * cw + 4 * gx)) / 2f;
            int i = 0;
            for (FarmWorld.AnimalDef def : farm.animalDefs.values()) {
                if (i < start) { i++; continue; }
                if (i >= start + perPage) break;
                int k = i - start;
                float cx = x0 + (k % 5) * (cw + gx);
                float cy = gridTop - (k / 5 + 1) * (ch + gy) + gy;
                batch.draw(Ui.roundedRect((int) cw, (int) ch, 12,
                    new Color(0.09f, 0.14f, 0.22f, 0.92f), GLASS_RIM), cx, cy);
                drawFarmMedallion(cx + cw / 2f - 25, cy + ch - 59, 50);
                Texture tx = farm.dynTexture(def.iconID);
                if (tx != null) {
                    batch.draw(tx, cx + cw / 2f - 19, cy + ch - 53, 38, 38);
                } else {
                    Ui.centered(batch, Ui.font, def.name.substring(0, 1),
                        cx + cw / 2f, cy + ch - 26, new Color(1f, 0.95f, 0.55f, 1f));
                }
                Ui.centered(batch, Ui.fontSmall, truncTo(Ui.fontSmall, def.name, cw - 8), cx + cw / 2f, cy + 57, new Color(1f, 1f, 1f, 0.92f));
                String price = def.priceXu > 0 ? fmtMoney(def.priceXu) + " xu" : def.priceLuong + " lượng";
                drawFarmPricePill(cx + cw / 2f, cy + 6, price);
                farmShopBtns.add(new float[]{cx, cy, cw, ch, def.species});
                i++;
            }
        } else {
            // danh sách vật phẩm (phân bón/thức ăn/thuốc) — bấm cả hàng là mua 1
            float y = gridTop - 6f;
            int i = 0;
            for (java.util.Map.Entry<Integer, FarmWorld.FarmItemDef> e : farm.farmItemDefs.entrySet()) {
                if (i < start) { i++; continue; }
                if (i >= start + perPage) break;
                FarmWorld.FarmItemDef fd = e.getValue();
                float ry = y - 56f;
                batch.draw(Ui.roundedRect((int) FH_PW - 52, 56, 12,
                    new Color(0.09f, 0.14f, 0.22f, 0.92f), GLASS_RIM), FH_PX + 26, ry);
                drawFarmMedallion(FH_PX + 36, ry + 8, 40);
                Texture tx = farm.dynTexture(fd.img);
                if (tx != null) batch.draw(tx, FH_PX + 41, ry + 13, 30, 30);
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.97f);
                Ui.fontSmall.draw(batch, truncTo(Ui.fontSmall, fd.name, FH_PW - 240), FH_PX + 90, ry + 36);
                String price = fd.priceXu > 0 ? fmtMoney(fd.priceXu) + " xu" : fd.priceLuong + " lượng";
                layout.setText(Ui.fontSmall, price);
                float pw2 = layout.width + 20f;
                batch.draw(Ui.roundedRect((int) pw2, 26, 9,
                    new Color(0.13f, 0.20f, 0.30f, 0.95f), GLASS_RIM), FH_PX + FH_PW - 36 - pw2, ry + 15);
                Ui.fontSmall.setColor(1f, 0.85f, 0.38f, 1f);
                Ui.fontSmall.draw(batch, price, FH_PX + FH_PW - 36 - pw2 + 10, ry + 34);
                Ui.fontSmall.setColor(Color.WHITE);
                farmShopBtns.add(new float[]{FH_PX + 26, ry, FH_PW - 52, 56, e.getKey()});
                y -= 64f;
                i++;
            }
        }
        if (clipped) popPanelClip();
        drawFarmPager(farmShopBtns, FH_PX + FH_PW / 2f, FH_PY + 10, farmShopPage, pages);
    }

    private void farmShopTouch(float x, float y) {
        if (x >= FH_PX + FH_PW - 54 && x <= FH_PX + FH_PW - 18
                && y >= FH_PY + FH_PH - 54 && y <= FH_PY + FH_PH - 18) {
            farmShopOpen = false;
            return;
        }
        for (float[] b : farmShopBtns) {
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) {
                int id = (int) b[4];
                if (id == -200) { farmShopPage = Math.max(0, farmShopPage - 1); return; }
                if (id == -201) { farmShopPage++; return; }
                if (id == 100 || id == 101 || id == 102) { // 3 tab
                    farmShopTab = id - 100;
                    farmShopPage = 0;
                    return;
                }
                if (farmShopTab == 0) {
                    FarmWorld.TreeDef def = farm.trees.get(id);
                    if (def != null) farmBuy(id, def.seedXu > 0 ? 1 : 2);
                } else if (farmShopTab == 1) {
                    FarmWorld.FarmItemDef fd = farm.farmItemDefs.get(id);
                    if (fd != null) farmBuy(id, fd.priceXu > 0 ? 1 : 2);
                } else {
                    FarmWorld.AnimalDef ad = farm.animalDefs.get(id);
                    if (ad != null) {
                        Msg m = new Msg(AvatarProtocol.FARM_BUY_ANIMAL);
                        try {
                            m.writer().writeByte(id);
                            m.writer().writeByte(ad.priceXu > 0 ? 1 : 2);
                        } catch (Exception ignored) {
                        }
                        farmSend(m);
                    }
                }
                return;
            }
        }
        if (x < FH_PX || x > FH_PX + FH_PW || y < FH_PY || y > FH_PY + FH_PH) farmShopOpen = false;
    }

    // ---- Farm GĐ4: vật nuôi (thu hoạch/cho ăn/bán) + nấu ăn ----

    /** Id thức ăn của một khu (116 hồ cá, 117 gia cầm, 118 gia súc, 119 chó)
     *  nếu kho đang có; -1 nếu không có. */
    private int farmFoodFor(int area) {
        int want = area == 4 ? 116 : area == 1 ? 117 : area == 3 ? 119 : 118;
        for (int[] u : farm.usableItems) {
            if (u[0] == want && u[1] > 0) return want;
        }
        return -1;
    }

    private void farmHarvestAnimalFocus() {
        if (farmAnimalFocus < 0) return;
        Msg m = new Msg(AvatarProtocol.FARM_HARVEST_ANIMAL);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmAnimalFocus);
        } catch (Exception ignored) {
        }
        farmSend(m);
        if (!online()) { // bản offline (harness): giả lập thu tại chỗ
            FarmWorld.Animal a = farm.animals.get(farmAnimalFocus);
            a.numEggOne = 0;
        }
    }

    private void farmFeedAnimalFocus() {
        if (farmAnimalFocus < 0) return;
        FarmWorld.Animal a = farm.animals.get(farmAnimalFocus);
        FarmWorld.AnimalDef def = farm.animalDefs.get(a.species);
        if (def == null) return;
        int food = farmFoodFor(def.area);
        if (food <= 0) return;
        Msg m = new Msg(AvatarProtocol.FARM_USE_ITEM);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmAnimalFocus);
            m.writer().writeShort(food);
        } catch (Exception ignored) {
        }
        farmSend(m);
        if (!online()) a.hunger = false;
    }

    private boolean farmAnimalSellOpen, farmAnimalSellable;
    private String farmAnimalSellText = "";
    private int farmAnimalSellIdx = -1;

    private void farmAskSellAnimal() {
        if (farmAnimalFocus < 0) return;
        Msg m = new Msg(AvatarProtocol.FARM_PRICE_ANIMAL);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmAnimalFocus);
        } catch (Exception ignored) {
        }
        farmSend(m);
        if (!online()) { // offline: mở hộp với giá bán lại trong bảng dat (mục des)
            FarmWorld.Animal a = farm.animals.get(farmAnimalFocus);
            FarmWorld.AnimalDef def = farm.animalDefs.get(a.species);
            farmAnimalSellIdx = farmAnimalFocus;
            farmAnimalSellable = def != null && !def.name.equals("Chó");
            farmAnimalSellText = farmAnimalSellable
                ? "Bạn có muốn bán " + def.name + " không?"
                : def.name + " là chó giữ nhà, không bán được.";
            farmAnimalSellOpen = true;
        }
    }

    private void farmSellAnimalConfirmed() {
        Msg m = new Msg(AvatarProtocol.FARM_SELL_ANIMAL);
        try {
            m.writer().writeInt(ownId());
            m.writer().writeByte(farmAnimalSellIdx);
        } catch (Exception ignored) {
        }
        farmSend(m);
        farmAnimalSellOpen = false;
        if (!online() && farmAnimalSellIdx >= 0 && farmAnimalSellIdx < farm.animals.size()) {
            farm.animals.remove(farmAnimalSellIdx);
        }
    }

    private static final float FA_PW = 560f, FA_PH = 280f;
    private static final float FA_PX = (Ui.W - FA_PW) / 2f, FA_PY = (Ui.H - FA_PH) / 2f;

    private void drawFarmAnimalSellPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FA_PW, (int) FA_PH, 16, GLASS_FILL_DARK, GLASS_RIM), FA_PX, FA_PY);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Bán vật nuôi", FA_PX + 26, FA_PY + FA_PH - 32);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), FA_PX + FA_PW - 54, FA_PY + FA_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, FA_PX + FA_PW - 36, FA_PY + FA_PH - 36, new Color(1f, 1f, 1f, 0.9f));
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.9f);
        java.util.List<String> lines = wrapMsg(farmAnimalSellText, FA_PW - 52);
        float y = FA_PY + FA_PH - 92f;
        for (String ln : lines) {
            Ui.fontSmall.draw(batch, ln, FA_PX + 26, y);
            y -= 26f;
        }
        float bw = 170f, bh = 50f, by = FA_PY + 30f;
        if (farmAnimalSellable) {
            float bx = FA_PX + FA_PW / 2f - bw - 8;
            batch.draw(Ui.gradientRect((int) bw, (int) bh, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, by);
            Ui.centered(batch, Ui.font, "Bán luôn", bx + bw / 2f, by + 33, new Color(1f, 1f, 1f, 1f));
            float cx = FA_PX + FA_PW / 2f + 8;
            batch.draw(Ui.roundedRect((int) bw, (int) bh, 12, Ui.BTN_CANCEL, GLASS_RIM), cx, by);
            Ui.centered(batch, Ui.font, "Để sau", cx + bw / 2f, by + 33, new Color(1f, 1f, 1f, 0.95f));
        } else {
            float bx = FA_PX + FA_PW / 2f - bw / 2f;
            batch.draw(Ui.roundedRect((int) bw, (int) bh, 12, Ui.BTN_CANCEL, GLASS_RIM), bx, by);
            Ui.centered(batch, Ui.font, "Để sau", bx + bw / 2f, by + 33, new Color(1f, 1f, 1f, 0.95f));
        }
    }

    private void farmAnimalSellTouch(float x, float y) {
        if (x >= FA_PX + FA_PW - 54 && x <= FA_PX + FA_PW - 18
                && y >= FA_PY + FA_PH - 54 && y <= FA_PY + FA_PH - 18) {
            farmAnimalSellOpen = false;
            return;
        }
        float bw = 170f, bh = 50f, by = FA_PY + 30f;
        if (y >= by && y <= by + bh) {
            if (farmAnimalSellable) {
                float bx = FA_PX + FA_PW / 2f - bw - 8;
                if (x >= bx && x <= bx + bw) {
                    farmSellAnimalConfirmed();
                    return;
                }
                float cx = FA_PX + FA_PW / 2f + 8;
                if (x >= cx && x <= cx + bw) farmAnimalSellOpen = false;
            } else {
                float bx = FA_PX + FA_PW / 2f - bw / 2f;
                if (x >= bx && x <= bx + bw) farmAnimalSellOpen = false;
            }
            return;
        }
        if (x < FA_PX || x > FA_PX + FA_PW || y < FA_PY || y > FA_PY + FA_PH) farmAnimalSellOpen = false;
    }

    // --- Panel NẤU ĂN (bếp) ---
    private boolean farmCookOpen;
    private int farmCookSel = 1;
    private static final float FC_PW = 820f, FC_PH = 560f;
    private static final float FC_PX = (Ui.W - FC_PW) / 2f, FC_PY = (Ui.H - FC_PH) / 2f;
    private final java.util.List<float[]> farmCookBtns = new java.util.ArrayList<>();

    private int farmHaveProduct(int productId) {
        int n = 0;
        for (int[] p : farm.products) if (p[0] == productId) n += p[1];
        for (int[] p : farm.specials) if (p[0] == productId) n += p[1];
        return n;
    }

    private void drawFarmCookPanel() {
        farmCookBtns.clear();
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) FC_PW, (int) FC_PH, 16, GLASS_FILL_DARK, GLASS_RIM), FC_PX, FC_PY);
        drawFarmPanelHeader(FC_PX, FC_PY, FC_PW, FC_PH, Ui.FA_FIRE,
            new Color(0.72f, 0.30f, 0.07f, 0.95f), "Nhà bếp",
            "Nấu món từ nông sản trong kho — món chín bán lại trong Kho");
        float topY = FC_PY + FC_PH - 92f;
        // Trạng thái bếp: dải trạng thái có icon lửa
        FarmWorld.FoodDef cooking = null;
        for (FarmWorld.FoodDef f : farm.foods) if (f.id == farm.foodId) cooking = f;
        if (farm.foodId > 0 && cooking != null) {
            long remain = farm.cookEndEpoch - System.currentTimeMillis() / 1000L;
            batch.draw(Ui.roundedRect((int) FC_PW - 44, 54, 12,
                new Color(0.22f, 0.13f, 0.05f, 0.92f), GLASS_RIM), FC_PX + 22, topY - 54);
            Ui.faIcon(batch, Ui.fa20, Ui.FA_FIRE, FC_PX + 50, topY - 27,
                new Color(1f, 0.62f, 0.25f, 1f));
            Ui.fontSmall.setColor(1f, 0.92f, 0.68f, 1f);
            String st = remain > 0
                ? "Đang nấu: " + cooking.name + " — còn " + (remain / 60) + " phút " + (remain % 60) + " giây"
                : "Đã chín: " + cooking.name + " — bấm Nhận món!";
            Ui.fontSmall.draw(batch, st, FC_PX + 74, topY - 21);
            Ui.fontSmall.setColor(Color.WHITE);
            if (remain <= 0) {
                float bw = 130f, bh = 40f, bx = FC_PX + FC_PW - 22 - bw, byy = topY - 47;
                batch.draw(Ui.gradientRect((int) bw, (int) bh, 10, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, byy);
                Ui.centered(batch, Ui.fontSmall, "Nhận món", bx + bw / 2f, byy + 26, new Color(1f, 1f, 1f, 1f));
                farmCookBtns.add(new float[]{bx, byy, bw, bh, -2});
            }
            topY -= 66f;
        }
        // Danh sách món: thẻ hàng 2 cột x 15 hàng, mỗi hàng có số phút nấu
        float colW = 252f, rowH = 30f;
        for (int i = 0; i < farm.foods.size(); i++) {
            FarmWorld.FoodDef f = farm.foods.get(i);
            int col = i / 15, row = i % 15;
            float rx = FC_PX + 22 + col * (colW + 12);
            float ry = topY - (row + 1) * rowH;
            boolean selected = f.id == farmCookSel;
            if (selected) {
                batch.draw(Ui.gradientRect((int) colW, (int) rowH - 4, 8,
                    EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), rx, ry - 4);
            } else {
                batch.draw(Ui.roundedRect((int) colW, (int) rowH - 4, 8,
                    new Color(0.09f, 0.14f, 0.22f, 0.80f), GLASS_RIM), rx, ry - 4);
            }
            String dn = f.name;
            String timeTxt = f.cookMinutes + "p";
            layout.setText(Ui.fontSmall, timeTxt);
            float timeW = layout.width;
            layout.setText(Ui.fontSmall, dn);
            while (layout.width > colW - 24 - timeW && dn.length() > 4) {
                dn = dn.substring(0, dn.length() - 2);
                layout.setText(Ui.fontSmall, dn + "…");
            }
            if (!dn.equals(f.name)) dn = dn + "…";
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
            Ui.fontSmall.draw(batch, dn, rx + 10, ry + 10);
            Ui.fontSmall.setColor(selected ? new Color(1f, 1f, 1f, 0.85f) : new Color(1f, 0.85f, 0.38f, 1f));
            Ui.fontSmall.draw(batch, timeTxt, rx + colW - 10 - timeW, ry + 10);
            Ui.fontSmall.setColor(Color.WHITE);
            farmCookBtns.add(new float[]{rx, ry - 4, colW, rowH, f.id});
        }
        // Thẻ chi tiết món đang chọn (cột phải)
        FarmWorld.FoodDef sel = null;
        for (FarmWorld.FoodDef f : farm.foods) if (f.id == farmCookSel) sel = f;
        if (sel != null) {
            float dx = FC_PX + 22 + 2 * (colW + 12) + 4;
            float dw = FC_PX + FC_PW - 22 - dx;
            float cardBot = FC_PY + 20f;
            drawFarmCardBg(dx, cardBot, dw, topY - cardBot);
            float cy = topY - 26f;
            drawFarmMedallion(dx + 16, cy - 48, 64);
            FarmWorld.ProductDef pd = farm.productDefs.get(sel.productId);
            drawProductIcon(sel.productId, pd, dx + 24, cy - 40, 48);
            Ui.font.setColor(1f, 0.95f, 0.55f, 1f);
            Ui.font.draw(batch, truncTo(Ui.font, sel.name, dw - 100), dx + 92, cy - 8);
            Ui.font.setColor(Color.WHITE);
            // Thẻ chi tiết hẹp (244px): thời gian và giá bán XẾP DỌC,
            // để cạnh nhau sẽ đè chữ (đã dính khi chụp kiểm chứng).
            Ui.faIcon(batch, Ui.fa20, Ui.FA_CLOCK, dx + 102, cy - 36, new Color(1f, 0.85f, 0.38f, 1f));
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
            Ui.fontSmall.draw(batch, sel.cookMinutes + " phút", dx + 118, cy - 30);
            if (pd != null && pd.priceXu > 0) {
                Ui.faIcon(batch, Ui.fa20, Ui.FA_COINS, dx + 102, cy - 62, new Color(1f, 0.85f, 0.38f, 1f));
                Ui.fontSmall.draw(batch, fmtMoney(pd.priceXu) + " xu", dx + 118, cy - 56);
            }
            Ui.fontSmall.setColor(Color.WHITE);
            float my = cy - 92f;
            Ui.fontSmall.setColor(1f, 0.95f, 0.55f, 1f);
            Ui.fontSmall.draw(batch, "NGUYÊN LIỆU", dx + 16, my);
            my -= 12f;
            boolean enough = true;
            for (int[] mat : sel.materials) {
                my -= 34f;
                FarmWorld.ProductDef mp = farm.productDefs.get(mat[0]);
                String mn = mp != null ? mp.name : ("Nguyên liệu " + mat[0]);
                int have = farmHaveProduct(mat[0]);
                boolean ok = have >= mat[1];
                if (!ok) enough = false;
                batch.draw(Ui.roundedRect((int) dw - 32, 30, 8,
                    new Color(0.11f, 0.18f, 0.27f, 0.95f), GLASS_RIM), dx + 16, my - 8);
                batch.setColor(ok ? new Color(0.35f, 0.95f, 0.45f, 1f) : new Color(1f, 0.4f, 0.35f, 1f));
                batch.draw(whiteTex, dx + 26, my + 2, 8, 8);
                batch.setColor(Color.WHITE);
                String hv = have + "/" + mat[1];
                layout.setText(Ui.fontSmall, hv);
                float pillW = layout.width + 18f;
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
                Ui.fontSmall.draw(batch,
                    truncTo(Ui.fontSmall, mn, dw - 24 - pillW - 52), dx + 44, my + 8);
                drawFarmQtyPill(dx + dw - 24, my + 7, hv);
            }
            Ui.fontSmall.setColor(Color.WHITE);
            float bw = dw - 32f, bh = 52f, bx = dx + 16, byy = cardBot + 16f;
            if (farm.foodId > 0) {
                batch.draw(Ui.roundedRect((int) bw, (int) bh, 12, new Color(0.25f, 0.25f, 0.28f, 0.9f), GLASS_RIM), bx, byy);
                Ui.centered(batch, Ui.fontSmall, "Bếp đang bận", bx + bw / 2f, byy + 32, new Color(1f, 1f, 1f, 0.6f));
            } else if (enough) {
                batch.draw(Ui.gradientRect((int) bw, (int) bh, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, byy);
                Ui.centered(batch, Ui.font, "Nấu món này", bx + bw / 2f, byy + 34, new Color(1f, 1f, 1f, 1f));
                farmCookBtns.add(new float[]{bx, byy, bw, bh, -1});
            } else {
                batch.draw(Ui.roundedRect((int) bw, (int) bh, 12, new Color(0.25f, 0.25f, 0.28f, 0.9f), GLASS_RIM), bx, byy);
                Ui.centered(batch, Ui.fontSmall, "Thiếu nguyên liệu", bx + bw / 2f, byy + 32, new Color(1f, 1f, 1f, 0.6f));
            }
        }
    }

    private void farmCookTouch(float x, float y) {
        if (x >= FC_PX + FC_PW - 54 && x <= FC_PX + FC_PW - 18
                && y >= FC_PY + FC_PH - 54 && y <= FC_PY + FC_PH - 18) {
            farmCookOpen = false;
            return;
        }
        for (float[] b : farmCookBtns) {
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) {
                int code = (int) b[4];
                if (code == -1) {
                    Msg m = new Msg(AvatarProtocol.FARM_COOK);
                    try {
                        m.writer().writeShort(farmCookSel);
                    } catch (Exception ignored) {
                    }
                    farmSend(m);
                    if (!online()) {
                        FarmWorld.FoodDef sel = null;
                        for (FarmWorld.FoodDef f : farm.foods) if (f.id == farmCookSel) sel = f;
                        if (sel != null) {
                            farm.foodId = sel.id;
                            farm.cookEndEpoch = System.currentTimeMillis() / 1000L + sel.cookMinutes * 60L;
                        }
                    }
                } else if (code == -2) {
                    Msg m = new Msg(AvatarProtocol.FARM_HARVEST_COOK);
                    farmSend(m);
                    if (!online()) farm.foodId = 0;
                } else {
                    farmCookSel = code;
                }
                return;
            }
        }
        if (x < FC_PX || x > FC_PX + FC_PW || y < FC_PY || y > FC_PY + FC_PH) farmCookOpen = false;
    }

    /** Mở kho / cửa hàng farm cho harness chụp kiểm chứng. */
    public void debugOpenFarmStore() {
        farmSeedOpen = farmFertOpen = farmLandOpen = farmShopOpen = false;
        farmStoreOpen = true;
    }

    /** Mở panel nấu ăn cho harness chụp kiểm chứng. */
    public void debugOpenFarmCook() {
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookSel = 1;
        farmCookOpen = true;
    }

    /** Đứng trước nửa TRÁI nhà kho rơm (điểm tương tác mở Kho). */
    public void debugShowFarmShed() {
        debugShowFarm();
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookOpen = false;
        farmAnimalSellOpen = false;
        playerX = FARM_SHED_X - 18f;
        playerY = FARM_BUILD_Y + 4f;
    }

    /** Đứng trước nửa PHẢI nhà kho rơm (điểm tương tác mở Cửa hàng). */
    public void debugShowFarmShopFront() {
        debugShowFarm();
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookOpen = false;
        farmAnimalSellOpen = false;
        playerX = FARM_SHED_X + 18f;
        playerY = FARM_BUILD_Y + 4f;
    }

    /** Đứng trước NHÀ BẾP (điểm tương tác Nấu ăn). */
    public void debugShowFarmKitchen() {
        debugShowFarm();
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookOpen = false;
        farmAnimalSellOpen = false;
        playerX = FARM_KITCHEN_X;
        playerY = FARM_BUILD_Y + 4f;
    }

    /** Đứng dưới gốc cây khế (điểm tương tác thu hoạch khế). */
    public void debugShowFarmStar() {
        debugShowFarm();
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookOpen = false;
        farmAnimalSellOpen = false;
        if (farmStarPos != null) {
            playerX = farmStarPos[0];
            playerY = farmStarPos[1] - 26f;
        }
    }

    /** Đứng ở đầu trái map farm để soi toàn cảnh phía tây (đo vùng tương tác). */
    public void debugShowFarmWest() {
        debugShowFarm();
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookOpen = false;
        farmAnimalSellOpen = false;
        playerX = 150f;
        playerY = 100f;
    }

    /** Đứng cạnh hồ cá để chụp thanh hành động vật nuôi (cá có hình trong
     *  Pack5; gà/bò chờ đào hình từ server live). */
    public void debugShowFarmAnimal() {
        debugShowFarm();
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmShopOpen = false;
        farmCookOpen = false;
        farmAnimalSellOpen = false;
        // Đứng trên BỜ TÂY của hồ cá (không xuống nước) để tương tác với cá.
        playerX = 842f;
        playerY = 84f;
    }

    public void debugOpenFarmShop() {
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = false;
        farmShopOpen = true;
        farmShopTab = 0;
    }

    /** Mở cửa hàng farm ở tab Vật nuôi cho harness chụp kiểm chứng. */
    public void debugOpenFarmShopAnimals() {
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = false;
        farmCookOpen = false;
        farmShopOpen = true;
        farmShopTab = 2;
    }

    /** Đổ đầy kho bằng MỌI loại hạt/vật phẩm + 20 loại nông sản để kiểm
     *  chứng panel không tràn khung khi dữ liệu nhiều (chỉ harness). */
    public void debugFillFarmStore() {
        farm.seeds.clear();
        for (Integer id : farm.trees.keySet()) farm.seeds.add(new int[]{id, 3});
        farm.usableItems.clear();
        for (Integer id : farm.farmItemDefs.keySet()) farm.usableItems.add(new int[]{id, 2});
        farm.products.clear();
        int n = 0;
        for (Integer id : farm.productDefs.keySet()) {
            if (n++ >= 20) break;
            farm.products.add(new int[]{id, 7});
        }
        farm.specials.clear();
        farm.specials.add(new int[]{215, 180});
    }

    public void debugOpenFarmStoreTab(int tab) {
        farmSeedOpen = farmFertOpen = farmLandOpen = farmShopOpen = farmCookOpen = false;
        farmAnimalSellOpen = false;
        farmStoreOpen = true;
        farmStoreTab = tab;
        farmStorePage = 0;
    }

    public void debugStoreNextPage() { farmStorePage++; }

    public void debugOpenFarmShopTab(int tab) {
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = farmCookOpen = false;
        farmAnimalSellOpen = false;
        farmShopOpen = true;
        farmShopTab = tab;
        farmShopPage = 0;
    }

    public void debugShopNextPage() { farmShopPage++; }

    /** Chip kính giữa-trên khi ở farm: tên + cấp nông trại (HUD farm của gốc). */
    private void drawFarmChip() {
        if (mapDef == null || mapDef.id != 25) return;
        String label = "Nông trại" + (farm.invLoaded ? " · Cấp " + farm.lvFarm : "");
        float w = 240f, h = 46f, x = Ui.W / 2f - w / 2f, y = Ui.H - h - 12f;
        batch.draw(Ui.roundedRect((int) w, (int) h, 14, GLASS_FILL, GLASS_RIM), x, y);
        Ui.font.setColor(1f, 0.87f, 0.35f, 1f);
        layout.setText(Ui.font, label);
        Ui.font.draw(batch, label, x + w / 2f - layout.width / 2f, y + h - 13f);
        Ui.font.setColor(Color.WHITE);
        // Gợi ý phía trên nút Tương tác: đang đứng gần mục tiêu farm nào
        // thì bấm nút sẽ tương tác với mục tiêu đó.
        if (!farmAnyPanelOpen()) {
            String hint = farmTargetLabel();
            if (hint != null) {
                String text = "Tương tác: " + hint;
                layout.setText(Ui.fontSmall, text);
                float pw = layout.width + 32f, ph = 34f;
                // mép phải của pill thẳng hàng mép phải nút Tương tác
                // (tâm nút ở Ui.W − 84, bán kính 46) để pill dài không tràn màn hình
                float px = Ui.W - 38f - pw, py = 198f;
                batch.draw(Ui.roundedRect((int) pw, (int) ph, 17, GLASS_FILL_DARK, GLASS_RIM), px, py);
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
                Ui.fontSmall.draw(batch, text, px + 16f, py + 23f);
                Ui.fontSmall.setColor(Color.WHITE);
            }
        }
    }

    /** Harness: mở thẳng cảnh farm offline với dữ liệu mẫu (ScreenshotMain). */
    public void debugShowFarm() {
        msgOpen = mailOpen = friendOpen = settingsOpen = musicOpen = false;
        passFormOpen = giftFormOpen = menuOpen = boardOpen = false;
        farmSeedOpen = farmFertOpen = farmLandOpen = farmStoreOpen = false;
        farmShopOpen = farmCookOpen = farmAnimalSellOpen = false;
        npcMenuOpen = false;
        guideOpen = false;
        confirmText = null;
        loadMap(findMap(25));
        farm.load(game);
        farm.demo();
        playerX = 540f; // đứng ngay ô 0 (cà chua chín) để thấy thanh Thu hoạch
        playerY = 128f;
    }

    private void loadBackground(MapDef def) {
        int hour = new java.util.Date().getHours();
        bgStatus = bgStatusOverride >= 0 ? bgStatusOverride
            : (hour >= 18 || hour < 6) ? 1 : 0;
        if (def.bg == null) {
            bgSkyTex = null;
            bgLineTex = null;
            cloudTex[0] = null;
            cloudTex[1] = null;
            clouds.clear();
            ensureWhiteTex();
            return;
        }
        bgSkyTex = game.texture("bghd/may" + def.bgNum() + bgStatus + ".png");
        bgLineTex = game.texture("bghd/" + def.bgNum() + bgStatus + ".png");
        cloudTex[0] = game.texture("effect/cl0" + bgStatus + ".png");
        cloudTex[1] = game.texture("effect/cl1" + bgStatus + ".png");
        clouds.clear();
        java.util.Random rnd = new java.util.Random(1234 + def.id);
        for (int i = 0; i < 8; i++) {
            Cloud c = new Cloud();
            c.anchor = i % 2;
            c.x100 = rnd.nextFloat() * (2200 + 800) * 100f;
            // dải mây của gốc nằm trên nóc ảnh silhouette một đoạn
            c.yW = mapBottomPx + bgLineTex.getHeight() / 2f + 30 + rnd.nextFloat() * 170f;
            c.speed = 900 + rnd.nextFloat() * 900; // đơn vị x100/giây (~9–18 px/giây)
            clouds.add(c);
        }
        ensureWhiteTex();
    }

    private void ensureWhiteTex() {
        if (whiteTex == null) {
            com.badlogic.gdx.graphics.Pixmap pm =
                new com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            whiteTex = new Texture(pm);
            pm.dispose();
        }
    }

    private void buildNpcs() {
        npcFigures.clear();
        // Vị trí + map của NPC lấy theo maps.json (đã đổi hệ: client id = npc.map + 1,
        // xem docs/maps-spec.md mục 0); hình + câu chat lấy từ dữ liệu NPC theo id.
        java.util.Map<Integer, NpcData> byId = new java.util.HashMap<>();
        for (NpcData n : game.data.npcs) byId.put(n.id, n);
        for (MapNpc mn : mapDef.npcs) {
            NpcData n = byId.get(mn.id);
            if (n == null) continue;
            NpcFigure f = new NpcFigure();
            f.data = n;
            f.doll = game.newDoll(n.items);
            f.feetX = mn.x * AvatarDkGame.HD;
            f.feetY = mapBottomPx - mn.y * AvatarDkGame.HD;
            f.anim.action = AvatarAnimator.STAND;
            npcFigures.add(f);
        }
    }

    // ---------- API cho ScreenshotMain / kiểm chứng ----------
    public void debugSetMap(int mapId) {
        loadMap(findMap(mapId));
        spawnPlayer();
        debugBusSkip();
    }

    /** Hủy sự kiện xe buýt (giữ nguyên vị trí spawn) — cho ảnh chụp/tour tĩnh. */
    public void debugBusSkip() { busPhase = 0; }

    /** Đóng panel hướng dẫn + menu NPC (dọn cảnh sau stage lái buôn). */
    public void debugCloseGuide() { guideOpen = false; npcMenuOpen = false; }

    /** Đặt xe đang vào trạm ở cự ly vừa khung hình (kiểm chứng hình). */
    public void debugBusArrive() {
        if (mapDef.stopX < 0) return;
        busPhase = 1;
        busX = mapDef.stopX + 118f;
    }

    /** Đặt xe đang dừng ở trạm, người chơi đã xuống tại điểm trạm. */
    public void debugBusDwell() {
        if (mapDef.stopX < 0) return;
        busPhase = 2;
        busTimer = 5f;
        busX = busTargetX();
        playerX = mapDef.stopX;
        playerY = mapDef.stopY;
    }

    /** Hình học sheet biểu cảm {px, py, pw, ph, cell, pad}: sheet neo đáy
     *  ngay phía trên khung chat, cạnh phải thẳng hàng nút mặt cười, chiều
     *  cao ph = feelH hiện tại (thay đổi khi kéo tay cầm). Lưới 7 cột. */
    private float[] feelSheetGeom() {
        float pad = 10f;
        float cell = (FEEL_SHEET_W - 2 * pad) / 7f;
        float px = FEEL_BTN_X + FEEL_BTN_S - FEEL_SHEET_W;
        float py = CHAT_Y + chatH + 12;
        return new float[]{px, py, FEEL_SHEET_W, feelH, cell, pad};
    }

    /** Độ cuộn tối đa của lưới mặt trong sheet ở chiều cao hiện tại. */
    private float feelMaxScroll() {
        float[] fg = feelSheetGeom();
        int rows = (feelIds.size() + 6) / 7;
        return Math.max(0f, rows * fg[4] - (fg[3] - 44f));
    }

    /** Ô thứ idx trong lưới sheet: {trái, dưới}; hàng 0 ở trên cùng, vị trí
     *  đã cộng độ cuộn feelScroll (cuộn lên = thấy các hàng dưới). */
    private float[] feelCellXY(float[] fg, int idx) {
        float px = fg[0], py = fg[1], ph = fg[3], cell = fg[4], pad = fg[5];
        int col = idx % 7, row = idx / 7;
        float gridTop = py + ph - 34f;
        return new float[]{px + pad + col * cell, gridTop - (row + 1) * cell + feelScroll};
    }

    /** Đổi biểu cảm: thay part zOrder 40 (mắt/mặt) của người chơi bằng part mặt
     *  được chọn — đúng cơ chế feel của Avatar.paint gốc (mặc định 4).
     *  Bản LOCAL: chỉ đổi hình của mình (nạp lại sau đăng nhập, chưa vào map
     *  thì không được gửi gói — server chưa có MapService cho mình). */
    public void applyFeelLocal(int feelId) {
        playerFeel = feelId;
        List<Integer> parts = new ArrayList<>(playerParts);
        boolean replaced = false;
        for (int i = 0; i < parts.size(); i++) {
            com.avatardk.game.data.PartData pd = game.data.part(parts.get(i));
            if (pd != null && pd.zorder == 40) { parts.set(i, feelId); replaced = true; break; }
        }
        if (!replaced) parts.add(feelId);
        playerParts.clear();
        playerParts.addAll(parts);
        playerDoll = game.newDoll(parts);
    }

    /** Chọn biểu cảm từ sheet: đổi hình của mình + gửi gói 57 để cả khu
     *  thấy mặt mới (chỉ khi online và đã vào map). Mặt có partId > 155
     *  không mã hoá được vào 1 byte kiểu gốc — giữ cục bộ, không gửi. */
    public void applyFeel(int feelId) {
        applyFeelLocal(feelId);
        if (online() && joinedClientMap >= 0) {
            try {
                AvatarProtocol.feelPark(game.connection, feelId);
            } catch (IOException e) {
                // mất một gói biểu cảm thì bỏ qua, không chặn việc chọn mặt
            }
        }
    }

    /** Áp biểu cảm (từ gói 57) cho một người chơi khác: lắp lại doll của họ
     *  với part mặt mới thay cho part zOrder 40 trong bộ đồ server gửi. */
    private void applyRemoteFeel(RemoteFigure f, int partId) {
        List<Integer> parts = new ArrayList<>();
        for (int id : f.info.wearing) parts.add(id);
        boolean replaced = false;
        for (int i = 0; i < parts.size(); i++) {
            com.avatardk.game.data.PartData pd = game.data.part(parts.get(i));
            if (pd != null && pd.zorder == 40) { parts.set(i, partId); replaced = true; break; }
        }
        if (!replaced) parts.add(partId);
        f.doll = game.newDoll(parts);
    }

    /** Chọn hành động từ sheet: diễn ngay trên máy mình + gửi gói 57 để
     *  cả khu thấy (đúng MapScr.doAction của gốc). Nhảy (10) là hành động
     *  thoáng qua không giữ tư thế; chạm lại tư thế đang diễn = đứng dậy
     *  (gửi byte 0). Di chuyển sẽ tự huỷ tư thế ở vòng cập nhật. */
    public void applyAction(int b) {
        int send = b;
        if (b == 10) {
            playerJumpT = 0f;
            playerPose = 0;
        } else if (b == playerPose) {
            playerPose = 0;
            send = 0;
        } else {
            playerPose = b;
        }
        if (online() && joinedClientMap >= 0) {
            try {
                AvatarProtocol.actionPark(game.connection, send);
            } catch (IOException e) {
                // mất một gói hành động thì bỏ qua, không chặn việc chọn
            }
        }
    }

    /** Áp hành động (gói 57, byte < 100) cho một người chơi khác — đúng
     *  MapScr.onFeel của gốc (idFeel < 100 -> avatar.doAction). */
    private void applyRemoteAction(RemoteFigure f, int b) {
        if (b == 10) {
            f.jumpT = 0f;
            f.pose = 0;
        } else {
            f.pose = b; // b = 0 -> đứng dậy
        }
    }

    /** Đặt người chơi ở điểm vào map khi chuyển map qua menu: x = 200 medium,
     *  y ở 72% chiều cao map (điểm đứng trên đường đi của từng map; khi online,
     *  toạ độ vào map do server cấp trong gói join). */
    public void spawnPlayer() {
        playerX = Math.min(200f, worldW / AvatarDkGame.HD - 40);
        playerY = mapDef.hmap * 24 * 0.72f;
        beginBusArrival();
    }

    /** Bắt đầu sự kiện xe buýt vào trạm (map có trạm): xe chạy từ phải vào,
     *  người chơi đang "ngồi trên xe" nên ẩn + khóa di chuyển tới khi xe dừng. */
    public void beginBusArrival() {
        if (mapDef == null || mapDef.stopX < 0) { busPhase = 0; return; }
        busPhase = 1;
        busX = mapDef.stopX + 340f;
        busV = 0f;
    }

    /** Cập nhật xe buýt theo pha: vào (giảm tốc về điểm dừng) → dừng thả
     *  người chơi xuống tại điểm trạm → rời trạm (tăng tốc sang trái). */
    private void updateBus(float dt) {
        if (busPhase == 1) {
            float target = busTargetX();
            float dist = busX - target;
            float v = MathUtils.clamp(dist * 1.7f, 34f, 300f);
            busX -= v * dt;
            if (dist <= 1.5f) {
                busX = target;
                busPhase = 2;
                busTimer = 0.9f;
                playerX = mapDef.stopX;
                playerY = mapDef.stopY;
            }
        } else if (busPhase == 2) {
            busTimer -= dt;
            if (busTimer <= 0) { busPhase = 3; busV = 50f; }
        } else if (busPhase == 3) {
            busV = Math.min(340f, busV + 300f * dt);
            busX -= busV * dt;
            if (busX < -160f) busPhase = 0;
        }
    }
    public void debugOpenMenu() { menuOpen = true; }
    public void debugCloseMenu() { menuOpen = false; }
    public void debugOpenCityMap() { openCityMap(); }
    public void debugCloseCityMap() { cityMapOpen = false; }
    public void debugOpenFeel() { feelOpen = true; }

    /** Harness chụp hình: mở sheet hành động (đóng các panel đang chồng). */
    public void debugOpenActionSheet() {
        settingsOpen = false;
        passFormOpen = false;
        menuOpen = false;
        actionOpen = true;
    }

    /** Harness chụp hình: đóng sheet và ngồi bệt (tư thế gói 57 byte 3). */
    public void debugSitPose() {
        actionOpen = false;
        applyAction(3);
    }

    /** Harness chụp hình: mở panel Nhạc ở tab cho trước (mockup). */
    public void debugOpenMusic(int tab) {
        settingsOpen = false;
        passFormOpen = false;
        menuOpen = false;
        actionOpen = false;
        musicTab = tab;
        musicOpen = true;
    }

    /** Harness chụp hình: nạp kết quả tìm kiếm THẬT đã bắt từ YouTube
     *  (tools/yt-fixtures/search_seed.json) + bìa tải sẵn trong thư mục
     *  thumbs, để panel hiển thị đúng dữ liệu thật khi chụp kiểm chứng
     *  (mạng Java trong sandbox bị chặn nên không gọi trực tiếp được). */
    public void debugSeedMusic(String seedJsonPath, String thumbsDirPath) {
        initMusic();
        try {
            String text = Gdx.files.absolute(seedJsonPath).readString("UTF-8");
            JsonValue root = new JsonReader().parse(text);
            List<YtVideo> vids = new ArrayList<>();
            for (JsonValue jv : root.get("videos")) {
                vids.add(new YtVideo(jv.getString("id", ""), jv.getString("title", ""),
                    jv.getString("channel", ""), YtVideo.parseDuration(jv.getString("duration", "")),
                    jv.getString("thumb", "")));
            }
            music.searchResults = vids;
            MusicLibrary lib = music.library();
            lib.playlists.clear();
            MusicLibrary.Playlist fav = new MusicLibrary.Playlist("Yêu thích");
            fav.tracks.addAll(vids.subList(0, Math.min(3, vids.size())));
            lib.playlists.add(fav);
            MusicLibrary.Playlist chill = new MusicLibrary.Playlist("Chill đêm khuya");
            if (vids.size() > 4) chill.tracks.add(vids.get(4));
            if (!vids.isEmpty()) chill.tracks.add(vids.get(0));
            lib.playlists.add(chill);
            musicListSel = 0;
            musicQuery.append("Sơn Tùng M-TP");
            for (YtVideo v : vids) {
                com.badlogic.gdx.files.FileHandle f =
                    Gdx.files.absolute(thumbsDirPath + "/" + v.id + ".jpg");
                if (f.exists()) {
                    Texture t = new Texture(f);
                    musicThumbs.putLocal("row:" + v.id, t);
                    musicThumbs.putLocal("big:" + v.id, t);
                }
            }
            if (!vids.isEmpty()) {
                music.queue = new ArrayList<>(vids);
                music.queueIndex = 1 % vids.size();
                music.current = vids.get(music.queueIndex);
                music.currentDurationSec = music.current.durationSec;
            }
        } catch (Exception e) {
            musicStatus = "seed lỗi: " + e.getMessage();
        }
    }
    public void debugCloseFeel() { feelOpen = false; }
    public void debugClearRemotes() { remotes.clear(); }
    /** Mở sheet biểu cảm ở nấc mở rộng + cuộn xuống (kiểm chứng hình). */
    public void debugExpandFeel(float scroll) {
        feelOpen = true;
        feelH = feelHTarget = FEEL_SHEET_MAX;
        feelScroll = scroll;
    }
    public int feelCount() { return feelIds.size(); }
    public java.util.List<Integer> debugFeelIds() { return feelIds; }
    public void debugInteract() { interact(); }
    public void debugSetPlayer(float mediumX, float mediumY) { playerX = mediumX; playerY = mediumY; }
    public boolean debugCanStand(float mediumX, float mediumY) { return canStand(mediumX, mediumY); }
    /** Ép nền ban ngày cho ảnh kiểm chứng (chỉ harness chụp hình dùng). */
    public void debugForceDay() {
        bgStatusOverride = 0;
        if (mapDef != null) loadBackground(mapDef);
    }
    public void debugChat(String text) { showBubble(text); }
    public void debugChatTab(int tab) { activeChatTab = tab; }
    public int mapId() { return mapDef.id; }
    public int npcCount() { return npcFigures.size(); }
    /** Danh sách id các map dựng được (cho công cụ chụp tour kiểm chứng). */
    public java.util.List<Integer> mapIds() {
        java.util.List<Integer> ids = new ArrayList<>();
        for (MapDef d : maps) ids.add(d.id);
        return ids;
    }
    public int currentMapId() { return mapDef.id; }
    public String debugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("map=").append(mapDef.id).append(" strips=");
        for (Texture t : strips) sb.append(t.getWidth()).append("x").append(t.getHeight()).append(" ");
        sb.append("worldW=").append(worldW).append(" worldH=").append(worldH);
        sb.append(" playerMedium=").append(playerX).append(",").append(playerY);
        sb.append(" npcs=").append(npcFigures.size());
        sb.append(" cam=").append(worldCam.position.x).append(",").append(worldCam.position.y);
        return sb.toString();
    }

    private void showBubble(String text) {
        chatBubble = text;
        chatTimer = 4f; // ChatPopup của gốc hiện vài giây rồi tự tắt (Base.update -> setOut)
        addChatLog(TAB_NEARBY, displayName, text, false);
    }

    /** Gửi nội dung đang gõ (dùng chung cho phím Enter và nút Gửi). Offline:
     *  bong bóng + tab Gần đây ngay. Online: gửi gói 55 lên server — bong bóng
     *  và dòng chat của mình hiện khi server phát lại (echo) như client gốc. */
    private void sendChat() {
        typing = false;
        emojiOpen = false;
        String msg = typeBuffer.toString().trim();
        typeBuffer.setLength(0);
        if (msg.isEmpty()) return;
        activeChatTab = TAB_NEARBY;
        if (privateTargetId != 0) {
            // đang nhắn riêng (gói −6): server chỉ chuyển cho người nhận,
            // không echo lại — tự ghi vào nhật ký như MessageScr của gốc
            if (online()) {
                try {
                    AvatarProtocol.chatTo(game.connection, privateTargetId, msg);
                    addChatLog(TAB_NEARBY, "Riêng · " + privateTargetName, msg, false);
                } catch (IOException e) {
                    addChatLog(TAB_SYSTEM, null, "Không gửi được tin nhắn riêng (mất kết nối?).", true);
                }
            } else {
                addChatLog(TAB_SYSTEM, null, "Mất kết nối — không gửi được tin nhắn riêng.", true);
            }
        } else if (online()) {
            try {
                AvatarProtocol.chatPark(game.connection, msg);
            } catch (IOException e) {
                addChatLog(TAB_SYSTEM, null, "Không gửi được tin nhắn (mất kết nối?).", true);
            }
        } else {
            showBubble(msg);
        }
    }

    // ---------- Tin nhắn thoại (gói 61 — mở rộng của bản port, game gốc
    // không có): bấm GIỮ nút micro để ghi (tối đa 30 giây, PCM 16 kHz nén
    // μ-law còn 16 KB/s, cắt gói 4000 byte), thả ra là gửi; server chỉ
    // chuyển tiếp mù theo zone, bên nhận ráp đủ các mẩu rồi mới hiện chip
    // nghe trong nhật ký, chạm vào chip để nghe/dừng. ----------
    private static final int VOICE_RATE = 16000;
    private static final int VOICE_MAX_SECS = 30;
    private static final int VOICE_CHUNK = 4000;
    private static final float MIC_BTN_W = 40f;
    private boolean recording = false;
    private com.badlogic.gdx.audio.AudioRecorder recorder;
    private BgThread recThread;
    private volatile boolean recActive;
    private final short[] recBuf = new short[VOICE_RATE * VOICE_MAX_SECS];
    private volatile int recCount;
    private volatile boolean recError;

    private static class VoiceAsm { byte[][] chunks; int got; }
    private final Map<String, VoiceAsm> voiceAsm = new java.util.HashMap<>();

    private ChatLine playingLine;
    private BgThread playThread;
    private volatile int playGen;
    /** Vùng chạm các chip thoại đã vẽ ở khung hình trước: {x, y, w, h, ChatLine}. */
    private final List<Object[]> voiceHits = new ArrayList<>();

    /** Bắt đầu ghi âm (gọi khi chạm GIỮ nút micro). */
    private void startRecording() {
        if (recording) return;
        if (!online()) {
            activeChatTab = TAB_SYSTEM;
            addChatLog(TAB_SYSTEM, null, "Vào thế giới online mới gửi được tin nhắn thoại.", true);
            return;
        }
        try {
            if (recorder == null) recorder = Gdx.audio.newAudioRecorder(VOICE_RATE, true);
        } catch (Throwable t) {
            activeChatTab = TAB_SYSTEM;
            addChatLog(TAB_SYSTEM, null, "Không mở được micro trên thiết bị này.", true);
            return;
        }
        recCount = 0;
        recError = false;
        recActive = true;
        recording = true;
        recThread = new BgThread(() -> {
            short[] chunk = new short[1600];
            while (recActive && recCount < recBuf.length) {
                int n = Math.min(chunk.length, recBuf.length - recCount);
                try {
                    recorder.read(chunk, 0, n);
                } catch (Throwable t) {
                    recError = true;
                    break;
                }
                System.arraycopy(chunk, 0, recBuf, recCount, n);
                recCount += n;
            }
        });
        recThread.setDaemon(true);
        recThread.start();
    }

    /** Dừng ghi và gửi (gọi khi thả tay khỏi micro / ghi đủ 30 giây). */
    private void finishRecording() {
        if (!recording) return;
        recording = false;
        recActive = false;
        if (recThread != null) {
            try { recThread.join(400); } catch (InterruptedException ignored) {}
            recThread = null;
        }
        int n = recCount;
        if (recError && n == 0) {
            activeChatTab = TAB_SYSTEM;
            addChatLog(TAB_SYSTEM, null, "Micro không ghi được âm thanh.", true);
            return;
        }
        if (n < VOICE_RATE / 2) {
            addChatLog(TAB_SYSTEM, null, "Tin nhắn thoại quá ngắn — bấm giữ micro lâu hơn một chút.", true);
            return;
        }
        if (!online()) {
            addChatLog(TAB_SYSTEM, null, "Mất kết nối — không gửi được tin nhắn thoại.", true);
            return;
        }
        short[] pcm = java.util.Arrays.copyOf(recBuf, n);
        byte[] data = VoiceCodec.encode(pcm, n);
        int clipId = (int) (System.currentTimeMillis() & 0x7ffffff) ^ (ownId() << 7);
        int total = (data.length + VOICE_CHUNK - 1) / VOICE_CHUNK;
        try {
            for (int s = 0; s < total; s++) {
                int off = s * VOICE_CHUNK;
                int len = Math.min(VOICE_CHUNK, data.length - off);
                AvatarProtocol.voicePark(game.connection, clipId, s, total,
                    java.util.Arrays.copyOfRange(data, off, off + len));
            }
        } catch (IOException e) {
            addChatLog(TAB_SYSTEM, null, "Không gửi được tin nhắn thoại (mất kết nối?).", true);
        }
        // dòng thoại của mình hiện khi server phát lại (echo) như tin chữ
    }

    /** Nhận một mẩu thoại (gói 61): ráp theo (người gửi, clipId), đủ mẩu
     *  thì giải mã và thêm vào nhật ký Gần đây + bong bóng trên đầu. */
    private void onVoiceChunk(ZonePlayers.VoiceChunk v) {
        if (v.total <= 0 || v.total > 200 || v.seq < 0 || v.seq >= v.total) return;
        String key = v.senderId + ":" + v.clipId;
        VoiceAsm a = voiceAsm.get(key);
        if (a == null) {
            a = new VoiceAsm();
            a.chunks = new byte[v.total][];
            voiceAsm.put(key, a);
        }
        if (a.chunks[v.seq] == null) {
            a.chunks[v.seq] = v.data;
            a.got++;
        }
        if (a.got < v.total) return;
        voiceAsm.remove(key);
        int len = 0;
        for (byte[] c : a.chunks) len += c.length;
        byte[] all = new byte[len];
        int off = 0;
        for (byte[] c : a.chunks) {
            System.arraycopy(c, 0, all, off, c.length);
            off += c.length;
        }
        short[] pcm = VoiceCodec.decode(all);
        if (v.senderId == ownId()) {
            chatBubble = "Tin nhắn thoại " + voiceDurText(pcm);
            chatTimer = 4f;
            addVoiceLog(TAB_NEARBY, displayName, pcm);
        } else {
            RemoteFigure f = remotes.get(v.senderId);
            if (f != null) {
                f.bubble = "Tin nhắn thoại " + voiceDurText(pcm);
                f.bubbleTimer = 4f;
                addVoiceLog(TAB_NEARBY, f.info.name, pcm);
            } else {
                addVoiceLog(TAB_NEARBY, "Người chơi", pcm);
            }
        }
    }

    /** Chạm chip thoại: đang nghe thì dừng, chưa nghe thì phát. */
    private void toggleVoicePlay(ChatLine l) {
        if (l == null || l.voice == null) return;
        if (playingLine == l) {
            stopVoicePlay();
            return;
        }
        stopVoicePlay();
        playingLine = l;
        l.voicePlaying = true;
        final int gen = ++playGen;
        final short[] pcm = l.voice;
        playThread = new BgThread(() -> {
            com.badlogic.gdx.audio.AudioDevice dev = null;
            try {
                dev = Gdx.audio.newAudioDevice(VOICE_RATE, true);
                dev.setVolume(com.avatardk.game.data.GameSettings.volumeScale());
                int o = 0;
                while (o < pcm.length && gen == playGen) {
                    int n = Math.min(4096, pcm.length - o);
                    dev.writeSamples(pcm, o, n);
                    o += n;
                }
            } catch (Throwable ignored) {
                // thiết bị không có loa/tai nghe: bỏ qua, trạng thái tự dọn ở update()
            } finally {
                if (dev != null) dev.dispose();
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    private void stopVoicePlay() {
        playGen++;
        if (playingLine != null) {
            playingLine.voicePlaying = false;
            playingLine = null;
        }
    }

    /** Kiểm chứng bằng ảnh: thêm 2 tin thoại mẫu (sóng sine) vào tab Gần đây,
     *  một tin đặt ở trạng thái đang nghe để thấy icon dừng. */
    public void debugVoiceDemo() {
        short[] a = new short[VOICE_RATE * 5];
        short[] b = new short[VOICE_RATE * 3];
        for (int i = 0; i < a.length; i++) a[i] = (short) (Math.sin(i * 0.05) * 9000);
        for (int i = 0; i < b.length; i++) b[i] = (short) (Math.sin(i * 0.03) * 7000);
        addVoiceLog(TAB_NEARBY, "Linh", a);
        addVoiceLog(TAB_NEARBY, displayName, b);
        List<ChatLine> log = chatLogs[TAB_NEARBY];
        ChatLine last = log.get(log.size() - 1);
        last.voicePlaying = true;
        playingLine = last;
    }

    // ---------- Mạng: người chơi khác trong zone (đợt 2d) ----------

    private void addRemote(ZonePlayers.RemotePlayer p) {
        if (p.role == 0 || p.id == ownId()) return; // NPC server đã có hình tĩnh; bỏ chính mình
        RemoteFigure f = new RemoteFigure();
        f.info = p;
        List<Integer> parts = new ArrayList<>();
        for (int id : p.wearing) parts.add(id);
        f.doll = game.newDoll(parts);
        f.tx = p.x;
        f.ty = p.y;
        f.feetX = p.x * AvatarDkGame.HD;
        f.feetY = mapBottomPx - p.y * AvatarDkGame.HD;
        f.anim.action = AvatarAnimator.STAND;
        f.anim.facingLeft = p.direct == 2;
        remotes.put(p.id, f);
    }

    /** Xử lý một gói park từ server (50/51/53/54/55/77). Cũng là lối vào cho
     *  ScreenshotMain bơm gói giả để kiểm chứng hình (debugInjectNet). */
    public void handleNetMsg(Msg m) {
        try {
            switch (m.command) {
                case AvatarProtocol.AVATAR_JOIN_PARK: {
                    ZonePlayers.JoinResult jr = ZonePlayers.parseJoin(m);
                    currentArea = jr.zoneId; // khu thật server xếp mình vào
                    remotes.clear();
                    for (ZonePlayers.RemotePlayer p : jr.players) addRemote(p);
                    break;
                }
                case AvatarProtocol.PARK_BOARD_LIST: {
                    // cmd 60 dùng chung 2 nghĩa theo handler: park = danh sách
                    // khu, farm = kho nông trại (gốc đổi cả handler nên không
                    // lẫn; bản port rẽ theo cờ farmIn)
                    if (farmIn) {
                        farm.parseInventory(m);
                    } else {
                        boardStatus = ZonePlayers.parseBoardList(m);
                        boardOpen = true;
                    }
                    break;
                }
                case AvatarProtocol.PLAYER_JOIN: {
                    // cmd 51 ở handler farm là trả lời SET_BIG_FARM, không phải
                    // người chơi vào park — đang ở farm thì bỏ qua
                    if (!farmIn) addRemote(ZonePlayers.parsePlayerJoin(m));
                    break;
                }
                case AvatarProtocol.PLAYER_LEAVE: {
                    RemoteFigure lf = remotes.remove(ZonePlayers.parseLeave(m));
                    if (lf != null && lf == panelPlayer) { panelPlayer = null; panelSub = false; }
                    break;
                }
                case AvatarProtocol.CHAT_TO: {
                    // nhắn riêng (gói −6): {int id NGƯỜI GỬI, UTF tên
                    // người gửi, UTF nội dung} -> vào cuộc trò chuyện trong
                    // hộp Tin đến (MessageScr.addPlayer của gốc)
                    java.io.DataInputStream r = m.reader();
                    int fromId = r.readInt();
                    String from = r.readUTF();
                    String text = r.readUTF();
                    convoAdd(fromId, from, text, false);
                    for (RemoteFigure f : remotes.values()) {
                        if (f.info.id == fromId) {
                            f.bubble = text;
                            f.bubbleTimer = 4f;
                            break;
                        }
                    }
                    break;
                }
                case AvatarProtocol.REQUEST_ADD_FRIEND: {
                    // lời mời kết bạn (gói −21): {int id người mời, UTF
                    // tên, UTF nội dung} -> một mục "Lời mời kết bạn"
                    // trong hộp Tin đến (MessageScr của gốc); panel đang
                    // đóng thì bật thêm hộp thoại Đồng ý/Từ chối ngay.
                    java.io.DataInputStream r = m.reader();
                    int id = r.readInt();
                    String name = r.readUTF();
                    String text = r.readUTF();
                    Convo c = convo(id, name);
                    c.friendReq = true;
                    c.reqText = text;
                    c.preview = "Lời mời kết bạn";
                    if (!(msgOpen && msgTab == 2 && chatConvo == c)) c.unread++;
                    if (!msgOpen && !friendOpen) {
                        friendInviteId = id;
                        friendInviteName = name;
                    }
                    break;
                }
                case AvatarProtocol.ADD_FRIEND: {
                    // kết quả lời mời mình đã gửi (gói −19): {int id người
                    // trả lời, UTF tên, boolean đồng ý, UTF nội dung} —
                    // client gốc hiện nội dung bằng OK dialog; ở đây ghi
                    // vào tab Hệ thống của khung chat.
                    java.io.DataInputStream r = m.reader();
                    r.readInt();
                    r.readUTF();
                    r.readBoolean();
                    String text = r.readUTF();
                    addChatLog(TAB_SYSTEM, null, text, true);
                    if (friendOpen) requestFriends();
                    break;
                }
                case AvatarProtocol.MAIL_LIST: {
                    // gói −108: {byte số thư; mỗi thư: int id, UTF loại,
                    // UTF tiêu đề, UTF người gửi, UTF nội dung, UTF đính
                    // kèm JSON, byte đã đọc, byte đã nhận quà, long millis}
                    java.io.DataInputStream r = m.reader();
                    int n = r.readUnsignedByte();
                    mailLetters.clear();
                    for (int i = 0; i < n; i++) {
                        MailLetter l = new MailLetter();
                        l.id = r.readInt();
                        l.kind = r.readUTF();
                        l.title = r.readUTF();
                        l.sender = r.readUTF();
                        l.body = r.readUTF();
                        String atts = r.readUTF();
                        l.read = r.readByte() != 0;
                        l.claimed = r.readByte() != 0;
                        l.created = r.readLong();
                        parseMailAtts(atts, l.atts);
                        mailLetters.add(l);
                    }
                    mailLoaded = true;
                    if (mailSelId < 0 && !mailLetters.isEmpty()) {
                        mailSelId = mailLetters.get(0).id;
                    }
                    mailScroll = MathUtils.clamp(mailScroll, 0f, mailMaxScroll());
                    break;
                }
                case AvatarProtocol.MAIL_CLAIM: {
                    // gói −110 trả: {int id thư, byte thành công}
                    java.io.DataInputStream r = m.reader();
                    int id = r.readInt();
                    boolean ok = r.readByte() != 0;
                    if (ok) {
                        for (MailLetter l : mailLetters) {
                            if (l.id == id) {
                                l.claimed = true;
                                l.read = true;
                            }
                        }
                    }
                    addChatLog(TAB_SYSTEM, null, ok
                        ? "Đã nhận quà từ thư."
                        : "Thư này không còn quà để nhận.", true);
                    break;
                }
                case AvatarProtocol.MAIL_CLAIM_ALL: {
                    // gói −111 trả: {byte số thư đã nhận} (+ −108 ngay sau)
                    int cnt = m.reader().readUnsignedByte();
                    if (cnt > 0) {
                        addChatLog(TAB_SYSTEM, null,
                            "Đã nhận quà từ " + cnt + " thư.", true);
                    }
                    break;
                }
                case AvatarProtocol.CUSTOM_LIST: {
                    // gói −81: {UTF id danh sách, bytes dữ liệu} — chỉ xử
                    // "friendlist" (ListScr của gốc), các danh sách khác
                    // thuộc các đợt sau.
                    java.io.DataInputStream r = m.reader();
                    String listId = r.readUTF();
                    if ("friendlist".equals(listId)) parseFriendList(r);
                    break;
                }
                case AvatarProtocol.REQUEST_YOUR_INFO: {
                    // gói −22: {int id, byte cấp, byte %cấp, byte thân thiện,
                    // byte gây rối, byte sành điệu, byte vui vẻ, byte sức khỏe,
                    // int id người yêu (−1 = không có) + khối người yêu}
                    java.io.DataInputStream r = m.reader();
                    int id = r.readInt();
                    int[] st = new int[]{
                        r.readByte() & 0xFF,
                        MathUtils.clamp(r.readByte(), 0, 100),
                        MathUtils.clamp(r.readByte(), 0, 100),
                        MathUtils.clamp(r.readByte(), 0, 100),
                        MathUtils.clamp(r.readByte(), 0, 100),
                        MathUtils.clamp(r.readByte(), 0, 100),
                        MathUtils.clamp(r.readByte(), 0, 100)};
                    if (id == ownId()) {
                        selfStats = st;
                        partnerName = null;
                        partnerDoll = null;
                        partnerImgId = -1;
                        try {
                            if (r.available() >= 4) {
                                int pid = r.readInt();
                                if (pid != -1) {
                                    partnerName = r.readUTF();
                                    int n = r.readByte();
                                    java.util.List<Integer> parts = new java.util.ArrayList<>();
                                    for (int i = 0; i < n; i++) parts.add((int) r.readShort());
                                    partnerSlogan = r.readUTF();
                                    partnerImgId = r.readShort(); // idImage quan hệ (icon đôi)
                                    partnerLv = r.readByte() & 0xFF;
                                    partnerPct = MathUtils.clamp(r.readByte(), 0, 100);
                                    partnerRel = r.readUTF();
                                    if (r.available() >= 2) {
                                        short act = r.readShort();
                                        if (act != -1 && r.available() > 0) r.readUTF();
                                    }
                                    partnerDoll = new PaperDoll(game.data, parts);
                                }
                            }
                        } catch (Exception ignored) {
                            // đuôi người yêu thiếu/hỏng: bỏ qua, chỉ số chính vẫn dùng được
                        }
                        // Đuôi mở rộng của server bản port: short cấp của
                        // gốc + UTF ngày vào game (dd/MM/yyyy) — có thì lấy.
                        try {
                            if (r.available() >= 2) {
                                r.readShort();
                                if (r.available() > 0) {
                                    String jd = r.readUTF();
                                    selfJoinDate = jd.isEmpty() ? null : jd;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    } else {
                        infoStats = st;
                        infoForId = id;
                        RemoteFigure f = remotes.get(id);
                        if (f != null) panelPlayer = f;
                    }
                    break;
                }
                case AvatarProtocol.GIVING_DEFERR: {
                    // gói 59: {int nguồn, int đích, short mã, short thời gian}
                    java.io.DataInputStream r = m.reader();
                    r.readInt();
                    int to = r.readInt();
                    int gid = r.readShort();
                    if (gid == -1) {
                        if (r.available() > 0) r.readUTF();
                    } else if (r.available() >= 2) {
                        r.readShort();
                    }
                    if (gid == 100 || gid == 101) {
                        if (to == ownId()) {
                            spawnActFx(null, gid);
                        } else {
                            RemoteFigure f = remotes.get(to);
                            if (f != null) spawnActFx(f, gid);
                        }
                    }
                    break;
                }
                case AvatarProtocol.PLAYER_MOVE: {
                    int[] mv = ZonePlayers.parseMove(m);
                    RemoteFigure f = remotes.get(mv[0]);
                    if (f != null) {
                        f.tx = mv[1];
                        f.ty = mv[2];
                        f.info.direct = mv[3];
                    }
                    break;
                }
                case AvatarProtocol.AVATAR_FEEL: {
                    int[] fl = ZonePlayers.parseFeel(m);
                    // server phát cho cả khu kể cả người gửi — của mình đã
                    // áp ngay lúc chọn nên bỏ qua gói mang id của mình.
                    // Byte >= 100 = biểu cảm mặt; byte < 100 = hành động
                    // (MapScr.onFeel của gốc: < 100 -> doAction).
                    if (fl[0] != ownId()) {
                        RemoteFigure f = remotes.get(fl[0]);
                        if (f != null) {
                            if (fl[1] >= 100) applyRemoteFeel(f, fl[1] - 100);
                            else applyRemoteAction(f, fl[1]);
                        }
                    }
                    break;
                }
                case AvatarProtocol.VOICE_PARK: {
                    // cmd 61: park = tin thoại, farm = dữ liệu nông trại (JOIN)
                    if (farmIn) {
                        farm.parseJoin(m);
                    } else {
                        onVoiceChunk(ZonePlayers.parseVoice(m));
                    }
                    break;
                }
                case AvatarProtocol.CHAT_PARK:
                case AvatarProtocol.CHAT_FARM: {
                    ZonePlayers.ChatMsg c = ZonePlayers.parseChat(m);
                    if (c.id >= 100_000_000) {
                        // id hệ thống của server (vd 2000000864 — thông báo
                        // phiên đấu giá/đếm ngược phát mỗi giây): vào tab
                        // Hệ thống, không đội tên "Người chơi" ở tab Gần đây
                        addChatLog(TAB_SYSTEM, null, c.text, true);
                    } else if (c.id == ownId()) {
                        showBubble(c.text);
                    } else {
                        RemoteFigure f = remotes.get(c.id);
                        if (f != null) {
                            f.bubble = c.text;
                            f.bubbleTimer = 4f;
                            addChatLog(TAB_NEARBY, f.info.name, c.text, false);
                        } else {
                            addChatLog(TAB_NEARBY, "Người chơi", c.text, false);
                        }
                    }
                    break;
                }
                case AvatarProtocol.UPDATE_MONEY: {
                    // gói −33: {int xèng, byte type, int xu, int lượng,
                    // int lượng khoá} — server báo lại số tiền hiện tại
                    java.io.DataInputStream r = m.reader();
                    myXeng = r.readInt();
                    r.readByte();
                    myXu = r.readInt();
                    myLuong = r.readInt();
                    myLuongKhoa = r.readInt();
                    syncMoneyText();
                    break;
                }
                case AvatarProtocol.MENU_OPTION: {
                    // gói −59: menu lựa chọn server gửi (mở từ NPC) —
                    // {int id, byte menuId, byte n, n×UTF tên mục,
                    // n×short id mục, UTF tên NPC, UTF lời NPC, n×boolean
                    // có menu con}; phần đuôi có thể vắng ở biến thể
                    // openMenuOption khác nên đọc phòng thủ
                    java.io.DataInputStream r = m.reader();
                    int uid = r.readInt();
                    int mid = r.readByte();
                    int n = r.readByte() & 0xFF;
                    java.util.List<String> names = new ArrayList<>();
                    for (int i = 0; i < n; i++) names.add(r.readUTF());
                    try {
                        if (r.available() >= 2 * n) {
                            for (int i = 0; i < n; i++) r.readShort();
                        }
                        if (r.available() > 0) {
                            r.readUTF();
                            if (r.available() > 0) r.readUTF();
                            for (int i = 0; i < n && r.available() > 0; i++) r.readBoolean();
                        }
                    } catch (Exception ignored) {
                        // tên các mục đã đọc đủ để hiện menu
                    }
                    npcMenuLocal = false;
                    npcMenuUserId = uid;
                    npcMenuId = mid;
                    npcMenuItems.clear();
                    npcMenuItems.addAll(names);
                    npcMenuTitle = uid >= NPC_ID_ADD
                        ? npcDisplayName(uid - NPC_ID_ADD) : "Menu";
                    npcMenuOpen = true;
                    break;
                }
                case AvatarProtocol.SERVER_MESSAGE: {
                    // gói −9 của gốc: {UTF} — tin hệ thống toàn cục
                    // (vd "Điểm Danh" của lái buôn trả "đang xây dựng")
                    String text = m.reader().readUTF();
                    if (!text.isEmpty()) {
                        addChatLog(TAB_SYSTEM, null, text, true);
                    }
                    break;
                }
                case AvatarProtocol.CUSTOM_TAB: {
                    // gói −58 của gốc: {byte 0, UTF tiêu đề, UTF nội dung}
                    // — tab thông tin/hướng dẫn server gửi (NPC "Xem
                    // hướng dẫn"); bản port trước đây bỏ qua gói này.
                    java.io.DataInputStream r = m.reader();
                    r.readByte();
                    openGuide(r.readUTF(), r.readUTF());
                    break;
                }
                case AvatarProtocol.OPEN_SHOP: {
                    // gói −49: {byte idShop, UTF tên shop, short n,
                    // n×short idPart} — mở panel shop theo danh sách này
                    java.io.DataInputStream r = m.reader();
                    r.readByte();
                    String shopName = r.readUTF();
                    int n = r.readShort();
                    java.util.List<Integer> ids = new ArrayList<>();
                    for (int i = 0; i < n; i++) ids.add((int) r.readShort());
                    openShopFromServer(ids, shopName);
                    break;
                }
                case AvatarProtocol.BUY_ITEM: {
                    // gói −24 báo mua thành công: {short idPart, int giá,
                    // byte 1, UTF thông báo, int xu, int lượng, int lượng
                    // khoá}. Server đã tự mặc món mới và phát −48 (về trước
                    // gói này) nên ở đây chỉ cập nhật tiền + báo người chơi;
                    // panel shop đang mở thì dựng lại doll mặc thử theo
                    // đúng bộ đồ hiện tại.
                    java.io.DataInputStream r = m.reader();
                    r.readShort();
                    r.readInt();
                    r.readByte();
                    String msgBuy = r.readUTF();
                    myXu = r.readInt();
                    myLuong = r.readInt();
                    myLuongKhoa = r.readInt();
                    syncMoneyText();
                    addChatLog(TAB_SYSTEM, null, msgBuy, true);
                    if (shopOpen) refreshShopTryDoll();
                    break;
                }
                case AvatarProtocol.CONTAINER: {
                    // gói −47: danh sách giỏ đồ -> mở panel giỏ (gốc:
                    // MapScr.onContainer -> doStore)
                    java.io.DataInputStream r = m.reader();
                    int n = r.readShort();
                    bagItems.clear();
                    for (int i = 0; i < n; i++) {
                        bagItems.add(new InvItem(r.readShort(), r.readByte(), r.readUTF()));
                    }
                    if (r.available() >= 1) bagCapacity = r.readByte() & 0xFF;
                    bagTab = 0;
                    bagSel = 0;
                    chestPanel = false;
                    bagPanel = true;
                    break;
                }
                case AvatarProtocol.USING_PART: {
                    // gói −48: {int id người, short idPart} — đảo trạng
                    // thái mặc của part đó trên nhân vật tương ứng
                    java.io.DataInputStream r = m.reader();
                    int uid = r.readInt();
                    int partId = r.readShort();
                    if (uid == ownId()) {
                        onUsingPartSelf(partId);
                        // gốc đóng PopupShop ngay khi server xác nhận
                        if (bagPanel) bagPanel = false;
                    } else {
                        RemoteFigure f = remotes.get(uid);
                        if (f != null) toggleRemotePart(f, partId);
                    }
                    break;
                }
                case AvatarProtocol.REMOVE_ITEM: {
                    // gói −36: {int id người, short idPart} — món bị xoá
                    // hẳn; nếu đang mặc thì tháo khỏi doll
                    java.io.DataInputStream r = m.reader();
                    int uid = r.readInt();
                    int partId = r.readShort();
                    if (uid == ownId()) {
                        if (playerParts.remove((Integer) partId)) {
                            wornExpire.remove(partId);
                            playerDoll = game.newDoll(playerParts);
                        }
                        removeInvItem(bagItems, partId);
                        removeInvItem(chestConItems, partId);
                        removeInvItem(chestItems, partId);
                    } else {
                        RemoteFigure f = remotes.get(uid);
                        if (f != null) removeRemotePart(f, partId);
                    }
                    break;
                }
                case AvatarProtocol.CUSTOM_CHEST: {
                    // gói −87: giỏ + rương nhà -> mở panel rương (gốc:
                    // HouseScr.onCustomChest)
                    java.io.DataInputStream r = m.reader();
                    int n1 = r.readShort();
                    chestConItems.clear();
                    for (int i = 0; i < n1; i++) {
                        chestConItems.add(new InvItem(r.readShort(), r.readByte(), r.readUTF()));
                    }
                    r.readInt(); // xu trong rương: bản port không hiển thị
                    chestLevel = r.readByte();
                    int n2 = r.readShort();
                    chestItems.clear();
                    for (int i = 0; i < n2; i++) {
                        chestItems.add(new InvItem(r.readShort(), r.readByte(), r.readUTF()));
                    }
                    if (r.available() >= 2) {
                        bagCapacity = r.readByte() & 0xFF;
                        chestCapacity = r.readByte() & 0xFF;
                    }
                    conSel = 0;
                    homeSel = 0;
                    bagPanel = false;
                    chestPanel = true;
                    break;
                }
                case AvatarProtocol.TRANS_CHEST: {
                    // gói −89: {boolean xong, UTF báo} — xong thì tự
                    // chuyển món đang chọn giữa hai danh sách như gốc
                    java.io.DataInputStream r = m.reader();
                    boolean ok = r.readBoolean();
                    String msg = r.readUTF();
                    if (ok) {
                        moveChestSelection();
                    } else if (!msg.isEmpty()) {
                        addChatLog(TAB_SYSTEM, null, msg, true);
                    }
                    break;
                }
                case AvatarProtocol.SERVER_DIALOG: {
                    // gói −10: hộp thoại của server -> đưa vào tab Hệ thống;
                    // form đổi mật khẩu đang mở thì hiện luôn vào dòng trạng
                    // thái của form (server báo kết quả đổi mật khẩu ở đây)
                    String dlg = m.reader().readUTF();
                    addChatLog(TAB_SYSTEM, null, dlg, true);
                    if (passFormOpen) {
                        setPassStatus(dlg, dlg.contains("thành công") ? 1 : 2);
                    }
                    break;
                }
                case AvatarProtocol.CHANGE_PASS: {
                    // gói −62: server xác nhận đã đổi mật khẩu {UTF mới}
                    m.reader().readUTF();
                    if (passFormOpen) {
                        setPassStatus("Đổi mật khẩu thành công.", 1);
                        for (StringBuilder b : passFields) b.setLength(0);
                    }
                    break;
                }
                case AvatarProtocol.FARM_USE_ITEM: {
                    // gói 65 server xác nhận đã dùng vật phẩm: {byte ô, short
                    // idItem} — client gốc trừ kho listItemFarm tại đây; phân
                    // giảm giờ thì cộng phút cho ô tại chỗ.
                    java.io.DataInputStream r = m.reader();
                    int idx = r.readUnsignedByte();
                    int itemId = r.readShort();
                    if (itemId == 100) {
                        if (idx < farm.cells.size()) farm.cells.get(idx).arid = false;
                    } else if (itemId >= 116 && itemId <= 119) {
                        // thức ăn: idx là chỉ số vật nuôi (quy ước của bản port ở GĐ4)
                        if (idx < farm.animals.size()) farm.animals.get(idx).hunger = false;
                        for (int[] u : farm.usableItems) {
                            if (u[0] == itemId && u[1] > 0) { u[1]--; break; }
                        }
                    } else {
                        for (int[] u : farm.usableItems) {
                            if (u[0] == itemId && u[1] > 0) { u[1]--; break; }
                        }
                        if (idx < farm.cells.size()) {
                            FarmWorld.Cell fc = farm.cells.get(idx);
                            if (itemId == 113) fc.time += 15;
                            else if (itemId == 114) fc.time += 30;
                            else if (itemId == 115) fc.time += 60;
                            else if (itemId == 111) fc.vitality = Math.min(100, fc.vitality + 10);
                            else if (itemId == 112) fc.vitality = 100;
                            if ((itemId == 113 || itemId == 114 || itemId == 115)
                                    && fc.idTree >= 0) {
                                FarmWorld.TreeDef td = farm.trees.get(fc.idTree);
                                if (td != null && td.minutes > 0) {
                                    fc.hervestPer = Math.min(100, fc.time * 100 / td.minutes);
                                }
                            }
                        }
                    }
                    break;
                }
                case AvatarProtocol.FARM_PRICE_SLOT: {
                    // gói 69: {UTF câu giá} -> hộp xác nhận mở ô (2 nút Xu/Lượng)
                    farmLandText = m.reader().readUTF();
                    farmLandOpen = true;
                    break;
                }
                case AvatarProtocol.FARM_OPEN_LAND: {
                    // gói 70: {int idFarm, int curMoney, byte type, UTF báo,
                    // int xu, int lượng, int lượng khoá} -> cập nhật tiền +
                    // xin lại dữ liệu farm cho thấy ô mới (gốc cũng doJoinFarm lại)
                    java.io.DataInputStream r = m.reader();
                    r.readInt();
                    r.readInt();
                    r.readByte();
                    String text = r.readUTF();
                    myXu = r.readInt();
                    myLuong = r.readInt();
                    myLuongKhoa = r.readInt();
                    syncMoneyText();
                    addChatLog(TAB_SYSTEM, null, text, true);
                    Msg join = new Msg(AvatarProtocol.FARM_JOIN);
                    join.writer().writeInt(ownId());
                    farmSend(join);
                    break;
                }
                case AvatarProtocol.FARM_CELL_UPDATE: {
                    farm.parseCellUpdate(m);
                    break;
                }
                case AvatarProtocol.FARM_ANIMAL_UPDATE: {
                    farm.parseAnimalUpdate(m);
                    break;
                }
                case AvatarProtocol.FARM_HARVEST_STAR: {
                    // gói 85: {short productId, short số quả} — khế về kho
                    java.io.DataInputStream r = m.reader();
                    int pid = r.readShort();
                    int num = r.readShort();
                    farm.starNum = 0;
                    if (num > 0) {
                        addFarmProductLocal(pid, num);
                        addChatLog(TAB_SYSTEM, null, "Thu hoạch " + num + " quả khế.", true);
                    }
                    break;
                }
                case AvatarProtocol.FARM_PLANT: {
                    // gói 64 echo: {int idUser, byte ô, byte hạt} — đặt cây lên ô
                    java.io.DataInputStream r = m.reader();
                    r.readInt();
                    int idx = r.readUnsignedByte();
                    int seed = r.readByte();
                    if (idx < farm.cells.size()) {
                        FarmWorld.Cell c = farm.cells.get(idx);
                        c.idTree = seed;
                        c.time = 0;
                        c.vitality = 100;
                        c.hervestPer = 0;
                        c.arid = true;
                    }
                    break;
                }
                case AvatarProtocol.FARM_HARVEST: {
                    // gói 66: {byte ô, short số lượng} — ô trống lại
                    java.io.DataInputStream r = m.reader();
                    int idx = r.readUnsignedByte();
                    r.readShort();
                    if (idx < farm.cells.size()) {
                        farm.cells.get(idx).idTree = -1;
                    }
                    if (farmIn) { // xin lại kho cho khớp số nông sản
                        try {
                            game.connection.send(new Msg(AvatarProtocol.FARM_INVENTORY));
                        } catch (Exception ignored) {
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        } catch (IOException e) {
            // gói lỗi bỏ qua, không để sập vòng lặp render
        }
    }

    /** Bơm gói mạng giả (chỉ dùng cho kiểm chứng/ScreenshotMain). */
    public void debugInjectNet(byte cmd, byte[] payload) {
        handleNetMsg(new Msg(cmd, payload));
    }

    // ---------- KHU (zone) ----------

    /** Mở panel chọn khu: online thì xin trạng thái thật từ server
     *  (gói 60), offline thì mở với trạng thái trống để xem/chọn cục bộ. */
    public void openBoardRequest() {
        boardOpen = true;
        boardLatch = 1.2f;
        GameConnection conn = game.connection;
        if (conn != null && game.player != null && !conn.isClosed()) {
            boardStatus = null; // chờ gói 60 trả về
            try {
                AvatarProtocol.requestBoardList(conn, mapDef.id);
            } catch (IOException e) {
                boardStatus = new int[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
            }
        } else if (boardStatus == null) {
            boardStatus = new int[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
        }
    }

    /** Chuyển sang khu khác của CÙNG map, giữ nguyên vị trí — đúng cách
     *  gốc làm khi chọn khu trong ParkListSrc (doJoinPark với board mới). */
    public void switchArea(int area) {
        boardOpen = false;
        if (area == currentArea) return;
        GameConnection conn = game.connection;
        if (conn == null || game.player == null || conn.isClosed()) {
            currentArea = area; // offline: chỉ đổi số hiển thị trên bảng
            return;
        }
        pendingArea = area;
        joinedClientMap = -1; // ép updateNet join lại với khu mới
        remotes.clear();
    }

    /** Bảng KHU (vật thể 846) gần nhất trong tầm tương tác, hoặc null. */
    private int[] nearestSign() {
        int[] best = null;
        float bd = INTERACT_RANGE;
        for (int[] o : mapDef.objects) {
            if (o[0] != 846) continue;
            float d = (float) Math.hypot(playerX - o[1], playerY - o[2]);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /** Type ô tại điểm (medium); −999 nếu map không có dữ liệu type. */
    private int cellTypeAt(float mx, float my) {
        if (mapDef == null || mapDef.types == null || mapDef.gridW <= 0) return -999;
        int cx = (int) (mx / 24), cy = (int) (my / 24);
        if (cx < 0 || cy < 0 || cx >= mapDef.gridW || cy >= mapDef.gridH) return -999;
        return mapDef.types[cy * mapDef.gridW + cx];
    }

    /** Va vào một ô không đứng được: nếu ô đó là CỔNG theo bảng doJoin của
     *  gốc (LoadMap.doJoin, docs/tile-engine-spec.md §9) thì kích hoạt thay
     *  vì chỉ chặn đường — đúng cách gốc gọi doJoin trước khi xét va chạm. */
    private void tryPortal(float mx, float my) {
        int type = cellTypeAt(mx, my);
        if (type == -999) return;
        if (type == 29) { // CHECK_POINT: danh sách khu/bàn (cơ chế có sẵn)
            if (portalLatch <= 0f && boardLatch <= 0f && !boardOpen) openBoardRequest();
            return;
        }
        if (portalLatch > 0f || busPhase > 0 || anyModalOpen()) return;
        if (!isPortalActionable(type)) return;
        portalLatch = 1.5f;
        doJoinCell(type);
    }

    /** Các type ô đã nối hành động ở bản port (phần còn lại của bảng doJoin
     *  — shop 57–64, casino 72–77/108, nhà 21/68–70/110, câu cá 54... —
     *  thuộc các đợt riêng, tạm thời không làm gì). */
    private boolean isPortalActionable(int type) {
        switch (type) {
            case -1: case 0: case 1: case 2: case 3: case 4: case 5: case 6:
            case 7: case 8: case 9: case 10: case 11: case 12: case 13:
            case 14: case 15: case 16: case 17: case 18: case 19: case 23:
            case 24: case 25: case 28: case 52: case 84: case 85: case 97:
                return true;
            default: return false;
        }
    }

    /** Hành động khi bước vào ô cổng (bảng doJoin của gốc). */
    private void doJoinCell(int type) {
        switch (type) {
            // doJoinPark(type): byte JOIN của gốc chính là id map server —
            // type 0..19 ↔ client map type+1 (phòng cưới 19→20, câu cá
            // 13→14, nhà tù 18→19... khớp đúng dữ liệu map hai chiều)
            case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
            case 8: case 10: case 11: case 13: case 14: case 15: case 16:
            case 18: case 19:
                portalJoinMap(type + 1);
                break;
            case -1: // MAPMAIN: thoát về thành phố
            case 23: // KHU_MUA_SAM: getHandler(9) về thành phố
                portalJoinMap(1);
                break;
            case 9: case 12: case 17: // hỏi xác nhận rồi mới thoát
                exitConfirmOpen = true;
                break;
            case 24: portalJoinMap(25); break; // ô cổng vào farm (ở farmway)
            case 25: portalJoinMap(26); break; // ô cổng farm → farmway
            case 28: // STORE: mở kho farm (ô nhà kho trên map farm)
                if (farmIn && farm != null) farmStoreOpen = true;
                break;
            case 52: // CUAHANG: cửa hàng farm (ô ở farmway — vào farm rồi mở)
                portalJoinMap(25);
                if (!online()) {
                    farmShopOpen = true;
                    farmShopTab = 0;
                } else {
                    portalFarmShopPending = true;
                }
                break;
            case 84: farmFeedArea(2); break; // máng heo (khu chuồng 2)
            case 85: farmFeedArea(3); break; // máng chó (khu 3)
            case 97: // STAR_FRUIT: cây khế — thu ngay như nút Tương tác
                if (farmIn && farm != null) farmHarvestStar();
                break;
            default: break;
        }
    }

    /** Chuyển map qua cổng: online gửi gói JOIN 50 như ParkService của gốc
     *  (byte đầu là id map server), offline nạp map tại chỗ. */
    private void portalJoinMap(int clientMapId) {
        GameConnection conn = game.connection;
        if (conn != null && game.player != null && !conn.isClosed()) {
            try {
                AvatarProtocol.joinPark(conn, clientMapId, (int) playerX, (int) playerY);
            } catch (IOException ignored) {
            }
        } else {
            loadMap(findMap(clientMapId));
            spawnPlayer();
        }
    }

    /** Cho ăn cả khu chuồng khi va vào ô máng (type 84/85 của gốc:
     *  doCattleFeeding) — cho con đang đói đầu tiên có sẵn thức ăn. */
    private void farmFeedArea(int area) {
        if (farm == null) return;
        for (int i = 0; i < farm.animals.size(); i++) {
            FarmWorld.Animal a = farm.animals.get(i);
            FarmWorld.AnimalDef d = farm.animalDefs.get(a.species);
            if (d == null || d.area != area) continue;
            if (a.hunger && farmFoodFor(area) > 0) {
                farmAnimalFocus = i;
                farmFeedAnimalFocus();
                return;
            }
        }
    }

    /** Hộp hỏi thoát về thành phố (ô type 9/12/17): Đồng ý / Huỷ. */
    private void drawExitConfirm() {
        float pw = 480, ph = 176, px = (Ui.W - pw) / 2f, py = (Ui.H - ph) / 2f;
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 16,
            new Color(0.08f, 0.13f, 0.20f, 0.98f), GLASS_RIM), px, py);
        Ui.centered(batch, Ui.font, "Bạn muốn thoát về thành phố?",
            px + pw / 2f, py + ph - 42, new Color(1f, 0.85f, 0.38f, 1f));
        batch.draw(Ui.gradientRect(170, 48, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            px + 40, py + 26);
        Ui.centered(batch, Ui.fontSmall, "Đồng ý", px + 40 + 85, py + 57, Color.WHITE);
        batch.draw(Ui.roundedRect(170, 48, 12, Ui.BTN_CANCEL, GLASS_RIM),
            px + pw - 40 - 170, py + 26);
        Ui.centered(batch, Ui.fontSmall, "Huỷ", px + pw - 40 - 85, py + 57, Color.WHITE);
    }

    /** Chạm khi hộp hỏi thoát đang mở: trả true nếu đã nuốt chạm. */
    private boolean exitConfirmTouch(float x, float y) {
        if (!exitConfirmOpen) return false;
        float pw = 480, ph = 176, cx0 = (Ui.W - pw) / 2f, cy0 = (Ui.H - ph) / 2f;
        exitConfirmOpen = false;
        if (x >= cx0 + 40 && x <= cx0 + 210 && y >= cy0 + 26 && y <= cy0 + 74) {
            portalJoinMap(1);
        }
        return true;
    }

    /** Kích hoạt thẳng một ô cổng (harness kiểm chứng bảng doJoin). */
    public void debugDoJoin(int type) { doJoinCell(type); }

    public void debugOpenBoard(int[] statuses, int area) {
        boardStatus = statuses;
        currentArea = area;
        boardOpen = true;
    }
    public void debugCloseBoard() { boardOpen = false; }
    public void debugToggleChatBig() { chatExpanded = !chatExpanded; }
    public void debugOpenEmoji() { emojiOpen = true; typing = true; }
    public void debugCloseEmoji() { emojiOpen = false; }
    /** Nạp vài dòng chat mẫu vào tab Gần đây để kiểm chứng panel chat. */
    public void debugChatDemo() {
        String[][] demo = {
            {"Linh", "Chào Sơn! Map đông vui quá nè"},
            {"Sơn", "Ừ, khu này đang đông thật"},
            {"Tuấn", "Ai đi săn boss chung không?"},
            {"Linh", "Tớ nè :heart: chờ tớ qua cổng cái"},
            {"Sơn", "Ok đứng ở trạm xe nhé"},
            {"Tuấn", "Xe buýt sắp tới rồi đó :fire:"},
            {"Linh", "Thấy rồi, lên xe thôi nào"},
            {"Sơn", "Nhớ đổi khu 2 cho đỡ lag nha :like:"},
        };
        for (String[] d : demo) addChatLog(TAB_NEARBY, d[0], d[1], false);
        activeChatTab = TAB_NEARBY;
    }
    public int debugArea() { return currentArea; }

    /** Số người chơi khác đang hiển thị (kiểm chứng). */
    public int remoteCount() {
        return remotes.size();
    }

    /** Việc mạng mỗi frame: join khi vào/đổi map, nhận gói, gửi di chuyển,
     *  nội suy vị trí người chơi khác, phát hiện rớt mạng. */
    private void updateNet(float dt) {
        netTime += dt;
        GameConnection conn = game.connection;
        if (conn == null || game.player == null) return;
        if (conn.isClosed()) {
            if (!netLossAnnounced) {
                netLossAnnounced = true;
                addChatLog(TAB_SYSTEM, null, "Mất kết nối tới máy chủ.", true);
            }
            joinedClientMap = -1;
            remotes.clear();
            return;
        }
        netLossAnnounced = false;
        if (joinedClientMap != mapDef.id) {
            try {
                // Farm: vào map Nông trại (client 25) thì đổi handler sang
                // Farm (10) rồi xin đồng bộ farm như GlobalLogicHandler gốc;
                // rời farm thì trả handler Avatar (8) trước khi join park lại.
                if (mapDef.id == 25 && !farmIn) {
                    farmIn = true;
                    Msg sw = new Msg(AvatarProtocol.GET_HANDLER);
                    sw.writer().writeByte(AvatarProtocol.HANDLER_FARM);
                    conn.send(sw);
                    conn.send(new Msg(AvatarProtocol.FARM_SET_BIG));
                    conn.send(new Msg(AvatarProtocol.FARM_GET_IMAGE));
                    conn.send(new Msg(AvatarProtocol.FARM_GET_TREE_INFO));
                    conn.send(new Msg(AvatarProtocol.FARM_INVENTORY));
                    Msg join = new Msg(AvatarProtocol.FARM_JOIN);
                    join.writer().writeInt(ownId());
                    conn.send(join);
                } else if (mapDef.id != 25 && farmIn) {
                    farmIn = false;
                    farm.clear();
                    Msg sw = new Msg(AvatarProtocol.GET_HANDLER);
                    sw.writer().writeByte(AvatarProtocol.HANDLER_AVATAR);
                    conn.send(sw);
                }
                int area = pendingArea != null ? pendingArea : -1;
                AvatarProtocol.joinPark(conn, mapDef.id, area, (int) playerX, (int) playerY);
                pendingArea = null;
                joinedClientMap = mapDef.id;
                remotes.clear();
                lastSentX = Integer.MIN_VALUE;
            } catch (IOException e) {
                // thử lại ở frame sau
            }
        }
        Msg m;
        while ((m = conn.poll()) != null) handleNetMsg(m);
        // gửi di chuyển của mình (giới hạn ~8 lần/giây, và khi đổi hướng)
        int px = Math.round(playerX), py = Math.round(playerY);
        boolean left = playerAnim.facingLeft;
        if ((px != lastSentX || py != lastSentY || left != lastSentLeft)
            && netTime - lastMoveSentAt >= 0.12f) {
            try {
                AvatarProtocol.movePark(conn, px, py, left);
                lastSentX = px;
                lastSentY = py;
                lastSentLeft = left;
                lastMoveSentAt = netTime;
            } catch (IOException e) {
                // bỏ qua, frame sau thử lại
            }
        }
    }

    /** Nội suy vị trí + hoạt ảnh người chơi khác (chạy cả khi bơm gói giả để
     *  kiểm chứng hình, không phụ thuộc kết nối thật). */
    private void updateRemotes(float dt) {
        float speedPx = SPEED * AvatarDkGame.HD * 1.2f;
        for (RemoteFigure f : remotes.values()) {
            float txPx = f.tx * AvatarDkGame.HD;
            float tyPx = mapBottomPx - f.ty * AvatarDkGame.HD;
            float dx = txPx - f.feetX, dy = tyPx - f.feetY;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 3f) {
                float step = Math.min(dist, speedPx * dt);
                f.feetX += dx / dist * step;
                f.feetY += dy / dist * step;
                if (Math.abs(dx) > 1f) f.anim.facingLeft = dx < 0;
                // di chuyển huỷ mọi tư thế hành động (đúng gốc)
                f.pose = 0;
                f.jumpT = -1f;
                f.anim.action = AvatarAnimator.RUN;
            } else if (f.jumpT >= 0f) {
                f.jumpT += dt / 0.5f;
                if (f.jumpT >= 1f) {
                    f.jumpT = -1f;
                    f.anim.action = AvatarAnimator.STAND;
                } else {
                    f.anim.action = 10;
                }
            } else if (f.pose != 0) {
                f.anim.action = f.pose;
            } else {
                f.anim.action = AvatarAnimator.STAND;
                f.anim.facingLeft = f.info.direct == 2;
            }
            f.anim.update(dt);
            if (f.bubbleTimer > 0) { f.bubbleTimer -= dt; if (f.bubbleTimer <= 0) f.bubble = null; }
        }
    }

    @Override
    public void show() {
        if (!chatSeeded) {
            chatSeeded = true;
            addChatLog(TAB_SYSTEM, null, "Chào mừng bạn đến với AvatarDK!", true);
            addChatLog(TAB_SYSTEM, null, "Đợt 2: đăng nhập/online đã nối — vào map sẽ thấy người chơi khác.", true);
        }
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char c) {
                if (msgOpen && msgTab == 2) {
                    if (c == '\b') {
                        if (msgInput.length() > 0) msgInput.deleteCharAt(msgInput.length() - 1);
                    } else if (c == '\r' || c == '\n') {
                        msgSend();
                    } else if (c >= 32 && msgInput.length() < 200) {
                        msgInput.append(c);
                    }
                    return true;
                }
                if (musicOpen && musicFocus != 0) {
                    StringBuilder b = musicFocus == 1 ? musicQuery : musicLink;
                    if (c == '\b') {
                        if (b.length() > 0) b.deleteCharAt(b.length() - 1);
                    } else if (c == '\r' || c == '\n') {
                        if (musicFocus == 1) runMusicSearch();
                        else runMusicImport();
                    } else if (c >= 32 && b.length() < 300) {
                        b.append(c);
                    }
                    return true;
                }
                if (passFormOpen) {
                    StringBuilder b = passFields[passFocus];
                    if (c == '\b') {
                        if (b.length() > 0) b.deleteCharAt(b.length() - 1);
                    } else if (c == '\r' || c == '\n') {
                        submitPassForm();
                    } else if (c >= 32 && b.length() < 24) {
                        b.append(c);
                    }
                    return true;
                }
                if (giftFormOpen) {
                    if (c == '\b') {
                        if (giftField.length() > 0) giftField.deleteCharAt(giftField.length() - 1);
                    } else if (c == '\r' || c == '\n') {
                        submitGiftForm();
                    } else if (c >= 32 && giftField.length() < 32) {
                        giftField.append(c);
                    }
                    return true;
                }
                if (!typing) return false;
                if (c == '\b') {
                    if (typeBuffer.length() > 0) typeBuffer.deleteCharAt(typeBuffer.length() - 1);
                } else if (c >= 32 && typeBuffer.length() < 60) {
                    typeBuffer.append(c);
                }
                return true;
            }
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                onTouch(v.x, v.y, pointer);
                return true;
            }
            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                if (pointer == msgPointer && msgOpen) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dy = v.y - msgDragStartY;
                    msgDragMoved = Math.max(msgDragMoved, Math.abs(dy));
                    msgScroll = MathUtils.clamp(msgDragStartScroll - dy, 0f, msgMaxScroll());
                    return true;
                }
                if (pointer == frPointer && friendOpen) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dy = v.y - frDragStartY;
                    frDragMoved = Math.max(frDragMoved, Math.abs(dy));
                    frScroll = MathUtils.clamp(frDragStartScroll - dy, 0f, frMaxScroll());
                    return true;
                }
                if (pointer == mailPointer && mailOpen) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dy = v.y - mailDragStartY;
                    mailDragMoved = Math.max(mailDragMoved, Math.abs(dy));
                    mailScroll = MathUtils.clamp(mailDragStartScroll - dy, 0f, mailMaxScroll());
                    return true;
                }
                if (pointer == shopPointer && shopOpen) {
                    // kéo trong lưới shop: cuộn theo tay như sheet biểu cảm
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dy = v.y - shopDragStartY;
                    shopDragMoved = Math.max(shopDragMoved, Math.abs(dy));
                    shopScroll = MathUtils.clamp(shopDragStartScroll + dy, 0f, shopMaxScroll());
                    return true;
                }
                if (pointer == mapPointer && cityMapOpen) {
                    // kéo bản đồ: nội dung đi theo tay, tâm camera trừ hao theo zoom
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dx = v.x - mapDragStartX, dy = v.y - mapDragStartY;
                    mapDragMoved = Math.max(mapDragMoved, (float) Math.hypot(dx, dy));
                    mapCamX = mapDragCamX - dx / mapScale;
                    mapCamY = mapDragCamY + dy / mapScale;
                    clampMapCam();
                    return true;
                }
                if (pointer == feelPointer) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dy = v.y - feelDragStartY;
                    feelDragMoved = Math.max(feelDragMoved, Math.abs(dy));
                    if (feelDragHandle) {
                        // kéo tay cầm: sheet cao theo tay (kẹp 110..MAX)
                        feelH = MathUtils.clamp(feelDragStartH + dy, 110f, FEEL_SHEET_MAX);
                        feelHTarget = feelH;
                    } else {
                        // kéo trong lưới: cuộn theo tay (kéo lên = xem hàng dưới)
                        feelScroll = MathUtils.clamp(feelDragStartScroll + dy, 0f, feelMaxScroll());
                    }
                    return true;
                }
                if (pointer == joyPointer) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    float dx = v.x - JOY_CX, dy = v.y - JOY_CY;
                    float len = (float) Math.hypot(dx, dy);
                    if (len > 8f) { joyVecX = dx / len; joyVecY = dy / len; }
                }
                return false;
            }
            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                // đang giữ micro ghi âm: thả tay ra là gửi tin nhắn thoại
                if (recording) { finishRecording(); return true; }
                if (pointer == msgPointer) {
                    msgPointer = -1;
                    if (msgOpen && msgDragMoved < 10f && msgPendingTap >= 0
                        && msgPendingTap < convos.size()) {
                        Convo c = convos.get(msgPendingTap);
                        if (!c.friendReq) openConvoChat(c);
                    }
                    msgPendingTap = -1;
                    return true;
                }
                if (pointer == frPointer) {
                    frPointer = -1;
                    if (friendOpen && frDragMoved < 10f && frPendingIdx >= 0
                        && frPendingIdx < friends.size()) {
                        friendRowAction(frPendingIdx, frPendingKind);
                    }
                    frPendingIdx = -1;
                    frPendingKind = -1;
                    return true;
                }
                if (pointer == mailPointer) {
                    mailPointer = -1;
                    if (mailOpen && mailDragMoved < 10f && mailPendingIdx >= 0) {
                        mailSelect(mailPendingIdx);
                    }
                    mailPendingIdx = -1;
                    return true;
                }
                if (pointer == shopPointer) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    if (shopOpen && shopDragMoved < 10f) {
                        // chạm nhẹ (không kéo) = chọn ô tại điểm thả
                        float gx = shopGridX(), gt = shopGridTop();
                        java.util.List<com.avatardk.game.data.PartData> l = shopLists.get(shopTab);
                        for (int i = 0; i < l.size(); i++) {
                            float cx = gx + (i % SHOP_COLS) * (SHOP_CELL + SHOP_GAP);
                            float cy = gt + shopScroll - (i / SHOP_COLS + 1) * SHOP_CELL
                                - (i / SHOP_COLS) * SHOP_GAP;
                            if (v.x >= cx && v.x <= cx + SHOP_CELL
                                && v.y >= cy && v.y <= cy + SHOP_CELL) {
                                if (i != shopSel) {
                                    shopSel = i;
                                    refreshShopTryDoll();
                                }
                                break;
                            }
                        }
                    }
                    shopPointer = -1;
                    return true;
                }
                if (pointer == mapPointer) {
                    mapPointer = -1;
                    if (cityMapOpen && mapDragMoved < 10f) {
                        // chạm nhẹ (không kéo) = chọn vùng gần nhất trong tầm 48px
                        Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                        int best = -1;
                        float bd = 48f;
                        for (int r = 0; r < regionCells.size() && r < REGION_NAMES.length; r++) {
                            float[] p = regionPoint(r);
                            float d = (float) Math.hypot(v.x - mapSx(p[0]), v.y - mapSy(p[1]));
                            if (d < bd) { bd = d; best = r; }
                        }
                        if (best >= 0) selRegion = best;
                    }
                    return true;
                }
                if (pointer == feelPointer) {
                    Vector3 v = hudViewport.unproject(new Vector3(sx, sy, 0));
                    if (feelDragHandle) {
                        // thả tay cầm: kéo xuống quá thấp thì đóng hẳn; còn lại
                        // hít về nấc gọn hoặc nấc mở rộng
                        if (feelH < 130f) {
                            feelOpen = false;
                            feelHTarget = FEEL_SHEET_MIN;
                        } else {
                            feelHTarget = feelH < (FEEL_SHEET_MIN + FEEL_SHEET_MAX) / 2f
                                ? FEEL_SHEET_MIN : FEEL_SHEET_MAX;
                        }
                    } else if (feelDragMoved < 10f) {
                        // chạm nhẹ (không kéo) = chọn mặt tại vị trí thả; sheet
                        // GIỮ MỞ để thử tiếp mặt khác
                        float[] fg = feelSheetGeom();
                        float cell = fg[4];
                        for (int i = 0; i < feelIds.size(); i++) {
                            float[] cxy = feelCellXY(fg, i);
                            if (v.x >= cxy[0] && v.x <= cxy[0] + cell
                                && v.y >= cxy[1] && v.y <= cxy[1] + cell) {
                                applyFeel(feelIds.get(i));
                                break;
                            }
                        }
                    }
                    feelPointer = -1;
                    return true;
                }
                if (pointer == joyPointer) { joyPointer = -1; joyVecX = 0; joyVecY = 0; }
                return false;
            }
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (msgOpen) {
                    msgScroll = MathUtils.clamp(msgScroll + amountY * 36f, 0f, msgMaxScroll());
                    return true;
                }
                if (friendOpen) {
                    frScroll = MathUtils.clamp(frScroll + amountY * 36f, 0f, frMaxScroll());
                    return true;
                }
                if (shopOpen) {
                    shopScroll = MathUtils.clamp(shopScroll + amountY * 36f, 0f, shopMaxScroll());
                    return true;
                }
                if (cityMapOpen) {
                    zoomMap(amountY < 0 ? 1.12f : 1f / 1.12f);
                    return true;
                }
                if (feelOpen) {
                    feelScroll = MathUtils.clamp(feelScroll + amountY * 36f, 0f, feelMaxScroll());
                    return true;
                }
                return false;
            }
        });
    }

    private void onTouch(float x, float y, int pointer) {
        // hộp hỏi thoát về thành phố (ô cổng 9/12/17) là lớp trên cùng
        if (exitConfirmTouch(x, y)) return;
        // form Đổi mật khẩu đang mở là lớp trên cùng (modal, nuốt mọi chạm)
        if (passFormOpen) {
            passFormTouch(x, y);
            return;
        }
        // form giftcode đang mở là lớp trên cùng (modal, nuốt mọi chạm)
        if (giftFormOpen) {
            giftFormTouch(x, y);
            return;
        }
        // các panel farm (chọn hạt / bón phân / mở ô) là modal
        if (farmSeedOpen) {
            farmSeedTouch(x, y);
            return;
        }
        if (farmFertOpen) {
            farmFertTouch(x, y);
            return;
        }
        if (farmLandOpen) {
            farmLandTouch(x, y);
            return;
        }
        if (farmAnimalSellOpen) {
            farmAnimalSellTouch(x, y);
            return;
        }
        if (farmCookOpen) {
            farmCookTouch(x, y);
            return;
        }
        if (farmShopOpen) {
            farmShopTouch(x, y);
            return;
        }
        if (farmStoreOpen) {
            farmStoreTouch(x, y);
            return;
        }
        // panel Cài đặt đang mở là lớp trên cùng (modal, nuốt mọi chạm)
        if (settingsOpen) {
            settingsTouch(x, y);
            return;
        }
        // panel Nhạc đang mở là lớp trên cùng (modal, nuốt mọi chạm)
        if (musicOpen) {
            musicTouch(x, y);
            return;
        }
        // bản đồ thành phố đang mở là lớp trên cùng: nút ăn ngay, còn lại kéo
        if (cityMapOpen) {
            cityMapTouchDown(x, y, pointer);
            return;
        }
        // panel chọn khu đang mở là lớp trên (sau bản đồ thành phố)
        if (boardOpen) {
            if (x >= BOARD_PX + BOARD_PW - 54 && x <= BOARD_PX + BOARD_PW - 18
                && y >= BOARD_PY + BOARD_PH - 54 && y <= BOARD_PY + BOARD_PH - 18) {
                boardOpen = false;
                return;
            }
            if (boardStatus != null) {
                for (int k = 0; k < boardStatus.length && k < 10; k++) {
                    float cx = boardCellX(k % 5), cy = boardCellY(k / 5);
                    if (x >= cx && x <= cx + BOARD_CELL_W && y >= cy && y <= cy + BOARD_CELL_H) {
                        if (boardStatus[k] != 0) switchArea(k); else boardOpen = false;
                        return;
                    }
                }
            }
            if (x < BOARD_PX || x > BOARD_PX + BOARD_PW || y < BOARD_PY || y > BOARD_PY + BOARD_PH) {
                boardOpen = false;
            }
            return;
        }
        // hộp thoại mời kết bạn đang chờ trả lời: lớp modal, nuốt mọi chạm
        if (friendInviteId != 0) {
            float pw = 440, ph = 168, px = (Ui.W - pw) / 2f, py = (Ui.H - ph) / 2f;
            boolean accept = x >= px + 52 && x <= px + 52 + 150 && y >= py + 22 && y <= py + 22 + 46;
            boolean refuse = x >= px + pw - 52 - 150 && x <= px + pw - 52 && y >= py + 22 && y <= py + 22 + 46;
            if (accept || refuse) {
                replyFriendInvite(friendInviteId, accept);
            }
            return;
        }
        // panel Hòm Thư đang mở: lớp modal
        if (mailOpen) {
            mailTouch(x, y, pointer);
            return;
        }
        // panel Tin nhắn đang mở: lớp modal
        if (msgOpen) {
            msgTouch(x, y, pointer);
            return;
        }
        // panel Bạn bè đang mở: lớp modal
        if (friendOpen) {
            friendTouch(x, y, pointer);
            return;
        }
        // panel nhân vật đang mở: X đóng / trong panel nuốt / ngoài thì đóng
        if (charPanel) {
            float[] g = charPanelGeom();
            float px = g[0], py = g[1];
            // nút đóng: ô vuông trong góc phải trên (như panel Bạn bè)
            if (x >= px + CP_W - 54 && x <= px + CP_W - 18
                    && y >= py + CP_H - 54 && y <= py + CP_H - 18) {
                charPanel = false;
                return;
            }
            if (x >= px && x <= px + CP_W && y >= py && y <= py + CP_H) return;
            charPanel = false;
            return;
        }
        // panel tab hướng dẫn (−58) đang mở: modal, trên cả menu NPC
        if (guideOpen) {
            guideTouch(x, y);
            return;
        }
        // menu NPC đang mở: modal, nuốt mọi chạm
        if (npcMenuOpen) {
            npcMenuTouch(x, y);
            return;
        }
        // panel shop đang mở: modal, nuốt mọi chạm
        if (shopOpen) {
            shopTouch(x, y, pointer);
            return;
        }
        // panel giỏ đồ / rương đồ đang mở: modal, nuốt mọi chạm
        if (bagPanel) {
            invTouch(x, y);
            return;
        }
        if (chestPanel) {
            chestTouch(x, y);
            return;
        }
        // panel tương tác người chơi khác đang mở
        if (panelPlayer != null) {
            float[] g = playerPanelGeom();
            float px = g[0], py = g[1];
            if (x >= px + PP_W - 54 && x <= px + PP_W - 18 && y >= py + PP_H - 54 && y <= py + PP_H - 18) {
                panelPlayer = null;
                panelSub = false;
                return;
            }
            if (panelSub) {
                float sy = ppSubY();
                for (int i = 0; i < 4; i++) {
                    float bx = ppSubBtnX(i);
                    if (x >= bx && x <= bx + 100 && y >= sy + 6 && y <= sy + 82) {
                        panelSubAction(i);
                        return;
                    }
                }
                if (x >= px && x <= px + PP_W && y >= sy && y <= sy + PP_SUB_H) return;
            }
            // thẻ thông tin đang hiện cũng thuộc panel: chạm vào thì nuốt
            if (infoStats != null && infoForId == panelPlayer.info.id) {
                float iy = ppInfoY();
                if (x >= px && x <= px + PP_W && y >= iy && y <= iy + 130) return;
            }
            for (int i = 0; i < 5; i++) {
                float bx = ppBtnX(i);
                if (x >= bx && x <= bx + 68 && y >= py + 6 && y <= ppBtnY() + 62) {
                    panelAction(i);
                    return;
                }
            }
            if (x >= px && x <= px + PP_W && y >= py && y <= py + PP_H) return;
            panelPlayer = null; // chạm ra ngoài: đóng, chạm đó xử lý tiếp ở dưới
            panelSub = false;
        }
        // card nhân vật (trái trên): mở panel nhân vật
        if (x >= CARD_X && x <= CARD_X + CARD_W && y >= Ui.H - 14 - CARD_H && y <= Ui.H - 14) {
            openCharPanel();
            return;
        }
        // nút burger (phải trên): mở/đóng panel kính
        if (x >= BURGER_X && x <= BURGER_X + BURGER_S && y >= BURGER_Y && y <= BURGER_Y + BURGER_S) {
            menuOpen = !menuOpen;
            return;
        }
        // nút hành động (asset action0) trên vòng cung quanh nút Tương tác: mở/đóng sheet hành động
        if (x >= ACT_BTN_X && x <= ACT_BTN_X + ACT_BTN_S && y >= ACT_BTN_Y && y <= ACT_BTN_Y + ACT_BTN_S) {
            actionOpen = !actionOpen;
            if (actionOpen) feelOpen = false;
            return;
        }
        // sheet hành động đang mở: chạm ô = diễn hành động (sheet giữ mở để
        // đổi tư thế tiếp); chạm ra ngoài thì đóng
        if (actionOpen) {
            float px = actSheetX(), py = actSheetY();
            if (x >= px && x <= px + ACT_SHEET_W && y >= py && y <= py + ACT_SHEET_H) {
                for (int i = 0; i < 4; i++) {
                    float cx = actCellX(i);
                    if (x >= cx && x <= cx + 84f && y >= py + 8 && y <= py + ACT_SHEET_H - 8) {
                        applyAction(ACTION_BYTES[i]);
                        return;
                    }
                }
                return;
            }
            actionOpen = false;
            return;
        }
        // nút mặt cười kế bên khung chat: mở/đóng hàng chọn biểu cảm
        if (x >= FEEL_BTN_X && x <= FEEL_BTN_X + FEEL_BTN_S && y >= FEEL_BTN_Y && y <= FEEL_BTN_Y + FEEL_BTN_S) {
            feelOpen = !feelOpen;
            if (feelOpen) actionOpen = false;
            return;
        }
        // sheet biểu cảm đang mở: chạm vào sheet = bắt đầu kéo (30px trên cùng
        // là tay cầm kéo đổi chiều cao; trong lưới là kéo cuộn; chạm nhẹ không
        // kéo = chọn mặt, xử lý ở touchUp). Chạm ra ngoài sheet thì đóng.
        if (feelOpen) {
            float[] fg = feelSheetGeom();
            float px = fg[0], py = fg[1], pw = fg[2], ph = fg[3];
            if (x >= px && x <= px + pw && y >= py && y <= py + ph) {
                feelPointer = pointer;
                feelDragHandle = y >= py + ph - 30f;
                feelDragStartY = y;
                feelDragStartH = feelH;
                feelDragStartScroll = feelScroll;
                feelDragMoved = 0f;
                return;
            }
            feelOpen = false;
            return;
        }
        // panel burger đang mở: lưới ô tính năng; chạm ra ngoài thì đóng
        if (menuOpen) {
            float px = MENU_PX, pw = MENU_PW, ph = MENU_PH, py = MENU_PY;
            if (x >= px && x <= px + pw && y >= py && y <= py + ph) {
                for (int i = 0; i < MENU_ITEMS.length; i++) {
                    int col = i % 3, row = i / 3;
                    float cx = px + 10 + col * MENU_CELL;
                    float cy = py + ph - MENU_HEADER - (row + 1) * MENU_CELL_H + 6;
                    if (x >= cx && x <= cx + MENU_CELL && y >= cy && y <= cy + MENU_CELL_H) {
                        menuOpen = false;
                        if (i == 0) {
                            openCityMap();
                        } else if (i == 1) {
                            openMailPanel();
                        } else if (i == 2) {
                            openBag();
                        } else if (i == 3) {
                            openFriendPanel();
                        } else if (i == 4) {
                            openCharPanel();
                        } else if (i == 5) {
                            settingsOpen = true;
                        } else if (i == 6) {
                            musicOpen = true;
                        } else {
                            addChatLog(TAB_SYSTEM, null, "Tính năng " + MENU_ITEMS[i][1] + " đang được port ở đợt tiếp theo.", true);
                        }
                        return;
                    }
                }
                return;
            }
            menuOpen = false;
            return;
        }
        // khung chat: nút phóng to/thu nhỏ ở góc trên phải của panel,
        // xích vào trong chỉ nhô ra ~12px (khớp vị trí vẽ ở drawChatFrame)
        {
            float ebX = CHAT_X + CHAT_W - 26, ebY = CHAT_Y + chatH - 26;
            if (x >= ebX && x <= ebX + 38 && y >= ebY && y <= ebY + 38) {
                chatExpanded = !chatExpanded;
                return;
            }
        }
        // chip tin nhắn thoại trong nhật ký: chạm để nghe / dừng nghe
        for (Object[] h : voiceHits) {
            float hx = (Float) h[0], hy = (Float) h[1], hw = (Float) h[2], hh = (Float) h[3];
            if (x >= hx && x <= hx + hw && y >= hy && y <= hy + hh) {
                toggleVoicePlay((ChatLine) h[4]);
                return;
            }
        }
        // ô emoji đang mở: chạm một icon để chèn mã :ten: vào ô nhập
        if (emojiOpen) {
            float[] g = emojiPanelGeom();
            if (x >= g[0] && x <= g[0] + EMOJI_PANEL_W && y >= g[1] && y <= g[1] + EMOJI_PANEL_H) {
                int col = (int) ((x - g[0] - 6) / EMOJI_CELL);
                int row = (int) ((g[1] + EMOJI_PANEL_H - 6 - y) / EMOJI_CELL);
                int idx = row * 6 + col;
                if (col >= 0 && col < 6 && row >= 0 && row < 4 && idx < CHAT_EMOJIS.length) {
                    String token = ":" + CHAT_EMOJIS[idx][0] + ":";
                    if (typeBuffer.length() + token.length() <= 60) typeBuffer.append(token);
                    typing = true;
                }
                return;
            }
        }
        // nút mở ô emoji (nằm giữa ô nhập và nút Gửi)
        {
            float micX = CHAT_X + CHAT_W - 8 - CHAT_SEND_W - 6 - MIC_BTN_W;
            float emojiX = micX - 6 - EMOJI_BTN_W;
            if (x >= emojiX && x <= emojiX + EMOJI_BTN_W
                    && y >= CHAT_Y + 7 && y <= CHAT_Y + 7 + CHAT_INPUT_H) {
                emojiOpen = !emojiOpen;
                if (emojiOpen) typing = true;
                return;
            }
            // nút micro: bấm GIỮ để ghi tin nhắn thoại, thả ra là gửi
            // (việc dừng/gửi xử lý ở touchUp)
            if (x >= micX && x <= micX + MIC_BTN_W
                    && y >= CHAT_Y + 7 && y <= CHAT_Y + 7 + CHAT_INPUT_H) {
                startRecording();
                return;
            }
        }
        // khung chat: tab vắt ngang viền (3 tab rộng bằng nhau, ô chạm theo
        // chiều cao thật — tab đang dùng cao hơn một chút) / nút Gửi / ô nhập
        {
            float tw = chatTabWidth();
            float tx = CHAT_X + 12;
            for (int i = 0; i < CHAT_TABS.length; i++) {
                float th = i == activeChatTab ? TAB_H_ACTIVE : TAB_H_IDLE;
                float tabBottom = CHAT_Y + chatH - th / 2f;
                if (x >= tx && x <= tx + tw && y >= tabBottom && y <= tabBottom + th) {
                    activeChatTab = i;
                    return;
                }
                tx += tw + 6;
            }
        }
        float sendX = CHAT_X + CHAT_W - 8 - CHAT_SEND_W;
        if (x >= sendX && x <= sendX + CHAT_SEND_W && y >= CHAT_Y + 7 && y <= CHAT_Y + 7 + CHAT_INPUT_H) {
            if (typing) sendChat();
            return;
        }
        if (x >= CHAT_X && x <= sendX - 6 && y >= CHAT_Y && y <= CHAT_Y + CHAT_INPUT_H + 6) {
            typing = true;
            return;
        }
        // nút tương tác (phải dưới)
        if (Math.hypot(x - ACT_CX, y - ACT_CY) <= ACT_R + 10) { interact(); return; }
        // chạm vào người chơi khác trên phố: mở panel tương tác của họ
        for (RemoteFigure f : remotes.values()) {
            float sx = f.feetX - lastCamX + Ui.W / 2f;
            float sy = f.feetY - lastCamY + Ui.H / 2f;
            if (Math.abs(x - sx) <= 32 && y >= sy - 8 && y <= sy + 112) {
                panelPlayer = f;
                panelSub = false;
                return;
            }
        }
        // joystick
        if (Math.hypot(x - JOY_CX, y - JOY_CY) <= JOY_R + 26) {
            joyPointer = pointer;
            float dx = x - JOY_CX, dy = y - JOY_CY;
            float len = (float) Math.hypot(dx, dy);
            if (len > 8f) { joyVecX = dx / len; joyVecY = dy / len; }
        }
    }

    /** NPC gần người chơi nhất trong tầm tương tác (đơn vị medium), null nếu không có ai. */
    private NpcFigure findNearestNpc() {
        NpcFigure best = null;
        float bestD = INTERACT_RANGE;
        for (NpcFigure f : npcFigures) {
            float d = (float) Math.hypot(playerX - f.data.x, playerY - f.data.y);
            if (d < bestD) { bestD = d; best = f; }
        }
        return best;
    }

    /** Hành động của nút tương tác: NPC gần nhất cất lời chào theo dữ liệu chat gốc của nó;
     *  không có ai ở gần thì nhân vật tự nói một câu báo rõ. */
    public void interact() {
        NpcFigure n = findNearestNpc();
        if (n == null) {
            // trong farm: nút Tương tác dùng cho ô đất / vật nuôi / công
            // trình gần nhất (đi lại gần rồi bấm nút, đúng mô hình của game)
            if (farmInteract()) return;
            // đứng gần bảng KHU (846) thì tương tác = mở danh sách khu
            if (nearestSign() != null) { openBoardRequest(); return; }
            // bong bóng trên đầu như cũ, nhưng trong khung chat thì đây là
            // thông báo HỆ THỐNG (vào tab Hệ thống, không vào tab Gần đây)
            chatBubble = "Không có ai ở gần để tương tác.";
            chatTimer = 4f;
            addChatLog(TAB_SYSTEM, null, "Không có ai ở gần để tương tác.", true);
            return;
        }
        // NPC Lái Buôn khi offline (bản web chơi thử): mở menu cục bộ
        // đúng 3 mục của gốc để xem được panel; hành động chạy tại chỗ
        // (xem openLaiBuonLocalMenu / laiBuonLocalSelect).
        if (n.data.id == NPC_LAI_BUON && game.connection == null) {
            openLaiBuonLocalMenu();
            return;
        }
        // NPC Cửa Hàng + Lái Buôn: nói chuyện qua server (gói −61) để
        // nhận menu NPC — đúng luồng gốc, thay cho lời chào; chế độ
        // offline (không có kết nối) thì vẫn chào như NPC thường
        if ((n.data.id == NPC_CUA_HANG || n.data.id == NPC_LAI_BUON)
                && game.connection != null) {
            try {
                AvatarProtocol.communicate(game.connection, NPC_ID_ADD + n.data.id);
                return;
            } catch (Exception e) {
                // gửi hỏng: rơi xuống lời chào mặc định phía dưới
            }
        }
        n.bubble = (n.data.chat != null && !n.data.chat.isEmpty())
            ? n.data.chat.get(0) : "Xin chào, ta là " + n.data.name + ".";
        n.bubbleTimer = 4f;
        // Lời NPC chỉ nổi bong bóng trên đầu, KHÔNG đưa vào khung chat (chủ dự án chốt).
    }

    // ---------- Panel tương tác người chơi khác ----------
    // Chạm vào một người chơi khác → panel KÍNH theo mẫu panel của Avatar
    // bản mới user gửi (avatar + tên + 5 nút Nhắn tin / Kết bạn / Tặng quà /
    // Tương tác / Khác) nhưng chất liệu kính mờ đồng bộ HUD của bản port.
    // Giao thức đúng client gốc (docs/park-spec.md): nhắn riêng gói −6,
    // kết bạn −21/−19, thông tin −22, Đánh/Hôn gói 59 (mã 100/101);
    // Tặng quà + Mời về nhà thuộc đợt Shop/Nhà nên báo rõ khi bấm.
    private static final float PP_W = 470f, PP_H = 184f;
    private static final String[] PP_LABELS = {"Nhắn tin", "Kết bạn", "Tặng quà", "Tương tác", "Khác"};
    private static final String[] PP_ICONS = {Ui.FA_COMMENT_DOTS, Ui.FA_USER_PLUS, Ui.FA_GIFT, Ui.FA_HEART, Ui.FA_FACE_SMILE};
    private static final String[] PP_SUB_LABELS = {"Đánh", "Hôn", "Mời về nhà", "Thông tin"};
    private static final String[] PP_SUB_ICONS = {Ui.FA_HAND_FIST, Ui.FA_HEART, Ui.FA_HOUSE, Ui.FA_CIRCLE_INFO};
    private RemoteFigure panelPlayer;
    private boolean panelSub;
    private int privateTargetId;
    private String privateTargetName;
    private int friendInviteId;
    private String friendInviteName;

    // ===== Thư / Tin nhắn (MessageScr của gốc) + Bạn bè (ListScr) =====
    // MessageScr của gốc gồm 3 tab: "Chung" (chat công cộng), "Tin den"
    // (hộp thư đến: các cuộc trò chuyện riêng + lời mời kết bạn) và tab
    // chat riêng mang tên người đang chat. Danh sách bạn là ListScr với
    // dữ liệu từ gói −81 "friendlist".
    private static class ConvoMsg {
        String text; boolean owner;
        ConvoMsg(String t, boolean o) { text = t; owner = o; }
    }
    private static class Convo {
        int id; String name;
        final List<ConvoMsg> msgs = new ArrayList<>();
        int unread;
        boolean friendReq;
        String reqText;
        String preview = "";
    }
    private final List<Convo> convos = new ArrayList<>();
    private boolean msgOpen = false;
    private int msgTab = 1; // 1 Tin đến, 2 chat riêng — panel Thư chỉ còn chat riêng
    // (chat chung đã có khung chat dưới HUD, user chốt 2026-11-02)
    private Convo chatConvo;
    private final StringBuilder msgInput = new StringBuilder();
    private float msgScroll = 0f;
    private int msgPointer = -1;
    private float msgDragStartY, msgDragStartScroll, msgDragMoved;
    private int msgPendingTap = -1; // hàng Tin đến chờ nhả tay (không kéo) mới mở
    private static final float MSG_PW = 920f, MSG_PH = 600f;
    private static final float MSG_PX = (Ui.W - MSG_PW) / 2f, MSG_PY = (Ui.H - MSG_PH) / 2f;

    private static class FriendEntry {
        int id; String name; String status;
        List<Integer> parts = new ArrayList<>();
        PaperDoll doll;
    }
    private final List<FriendEntry> friends = new ArrayList<>();
    private boolean friendOpen = false;
    private boolean friendLoading = false;
    private int friendArmDelete = -1;
    private float frScroll = 0f;
    private int frPointer = -1;
    private float frDragStartY, frDragStartScroll, frDragMoved;
    private int frPendingIdx = -1, frPendingKind = -1; // nút trên hàng chờ nhả tay
    private static final float FR_PW = 720f, FR_PH = 600f;
    private static final float FR_PX = (Ui.W - FR_PW) / 2f, FR_PY = (Ui.H - FR_PH) / 2f;
    private int[] infoStats;   // {cấp, %cấp, thân thiện, phong cách, vui vẻ, độ no} từ gói −22
    private int infoForId;
    private float lastCamX = 640f, lastCamY = 360f;

    /** Hiệu ứng Đánh/Hôn (gói 59) đang chạy trên đầu một nhân vật. */
    private static class ActFx {
        RemoteFigure target;   // null = chính mình
        int kind;              // 100 = đánh, 101 = hôn
        float t;
    }
    private final java.util.List<ActFx> actFx = new java.util.ArrayList<>();

    private static final float PP_SUB_H = 88f;
    private static final float PP_TAIL = 14f;
    private Texture tailTex;

    /** Hình học panel: panel chính LUÔN neo trên đầu người được chọn
     *  (đáy panel cách đỉnh đầu một đoạn đuôi nhọn), kẹp mép màn hình.
     *  Trả {px, py = đáy panel, 1}. */
    private float[] playerPanelGeom() {
        if (panelPlayer == null) {
            return new float[]{(Ui.W - PP_W) / 2f, CHAT_Y + chatH + 14f, 1f};
        }
        float sx = panelPlayer.feetX - lastCamX + Ui.W / 2f;
        float feetScr = panelPlayer.feetY - lastCamY + Ui.H / 2f;
        // đỉnh đầu thật theo bounds của doll (mỗi doll cao thấp khác nhau)
        // + khoảng cho tên nhân vật vẽ trên đầu
        int[] b = panelPlayer.doll.bounds(panelPlayer.anim.frameIndex());
        float headTop = feetScr + (-b[1]) * AvatarDkGame.HD + 28f;
        float px = MathUtils.clamp(sx - PP_W / 2f, 8f, Ui.W - PP_W - 8f);
        float py = Math.min(headTop + PP_TAIL, Ui.H - PP_H - 6f);
        return new float[]{px, Math.max(py, 8f), 1f};
    }
    /** Hàng con + thẻ thông tin xếp tiếp PHÍA TRÊN panel nếu đủ chỗ;
     *  không đủ thì cả cụm phụ xếp xuống DƯỚI panel (panel chính vẫn
     *  bám trên đầu nhân vật). */
    private boolean ppExtrasUp() {
        float[] g = playerPanelGeom();
        boolean infoShown = infoStats != null && panelPlayer != null
            && infoForId == panelPlayer.info.id;
        float extras = (panelSub ? PP_SUB_H + 8f : 0f) + (infoShown ? 130f + 8f : 0f);
        return g[1] + PP_H + extras <= Ui.H - 6f;
    }
    private float ppBtnX(int i) { return playerPanelGeom()[0] + 41f + i * 80f; }
    private float ppBtnY() { return playerPanelGeom()[1] + 28f; }
    private float ppSubBtnX(int i) { return playerPanelGeom()[0] + 23f + i * 108f; }
    private float ppSubY() {
        float[] g = playerPanelGeom();
        return ppExtrasUp() ? g[1] + PP_H + 8f : g[1] - 8f - PP_SUB_H;
    }
    private float ppInfoY() {
        float[] g = playerPanelGeom();
        if (ppExtrasUp()) {
            return (panelSub ? ppSubY() + PP_SUB_H : g[1] + PP_H) + 8f;
        }
        return (panelSub ? ppSubY() : g[1]) - 8f - 130f;
    }
    private float panelTailCx() {
        float[] g = playerPanelGeom();
        float sx = panelPlayer != null ? panelPlayer.feetX - lastCamX + Ui.W / 2f : Ui.W / 2f;
        return MathUtils.clamp(sx, g[0] + 26f, g[0] + PP_W - 26f);
    }
    private Texture tailTexture() {
        if (tailTex == null) {
            Pixmap pm = new Pixmap(28, 16, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fillTriangle(1, 1, 27, 1, 14, 15);
            tailTex = new Texture(pm);
            pm.dispose();
        }
        return tailTex;
    }
    private Texture circleTex, ringTex;
    private Texture circleTexture() {
        if (circleTex == null) {
            Pixmap pm = new Pixmap(76, 76, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fillCircle(38, 38, 37);
            circleTex = new Texture(pm);
            pm.dispose();
        }
        return circleTex;
    }
    private Texture ringTexture() {
        if (ringTex == null) {
            Pixmap pm = new Pixmap(76, 76, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fillCircle(38, 38, 37);
            pm.setBlending(Pixmap.Blending.None);
            pm.setColor(0f, 0f, 0f, 0f);
            pm.fillCircle(38, 38, 30);
            ringTex = new Texture(pm);
            pm.dispose();
        }
        return ringTex;
    }

    private String remoteName(int id) {
        RemoteFigure f = remotes.get(id);
        return f != null ? f.info.name : ("Người chơi " + id);
    }

    private void spawnActFx(RemoteFigure target, int kind) {
        ActFx fx = new ActFx();
        fx.target = target;
        fx.kind = kind;
        fx.t = 0f;
        actFx.add(fx);
    }

    private void panelAction(int i) {
        RemoteFigure f = panelPlayer;
        if (f == null) return;
        int id = f.info.id;
        String name = f.info.name;
        switch (i) {
            case 0: // Nhắn tin riêng: mở panel Tin nhắn ở tab chat riêng
                // (MessageScr.startChat của gốc)
                if (!online()) {
                    addChatLog(TAB_SYSTEM, null, "Vào thế giới online mới nhắn tin riêng được.", true);
                    return;
                }
                panelPlayer = null;
                panelSub = false;
                startChat(id, name);
                break;
            case 1: // Kết bạn (gói −21)
                if (!online()) {
                    addChatLog(TAB_SYSTEM, null, "Vào thế giới online mới kết bạn được.", true);
                    return;
                }
                try {
                    AvatarProtocol.requestAddFriend(game.connection, id);
                    addChatLog(TAB_SYSTEM, null, "Đã gửi lời mời kết bạn tới " + name + ".", true);
                } catch (IOException e) {
                    addChatLog(TAB_SYSTEM, null, "Không gửi được lời mời (mất kết nối?).", true);
                }
                break;
            case 2: // Tặng quà — shop quà thuộc đợt Shop
                addChatLog(TAB_SYSTEM, null, "Tặng quà sẽ mở cùng đợt Shop sắp tới.", true);
                break;
            case 3: // Tương tác: mở/đóng hàng con
                panelSub = !panelSub;
                break;
            default: // Khác: lệnh đặc biệt do server gửi theo sự kiện (MENU_ROTATE)
                addChatLog(TAB_SYSTEM, null,
                    "Mục Khác hiện các lệnh đặc biệt theo sự kiện (server gửi) — hiện chưa có lệnh nào.", true);
                break;
        }
    }

    private void panelSubAction(int i) {
        RemoteFigure f = panelPlayer;
        if (f == null) return;
        int id = f.info.id;
        switch (i) {
            case 0:
            case 1: { // Đánh (100) / Hôn (101) — gói 59
                int act = i == 0 ? 100 : 101;
                if (online()) {
                    try {
                        AvatarProtocol.givingDeferrent(game.connection, id, act);
                    } catch (IOException e) {
                        addChatLog(TAB_SYSTEM, null, "Không gửi được hành động (mất kết nối?).", true);
                    }
                } else {
                    spawnActFx(f, act); // offline: hiệu ứng tại chỗ lên nhân vật mẫu
                }
                break;
            }
            case 2: // Mời về nhà — đợt Nhà
                addChatLog(TAB_SYSTEM, null, "Mời về nhà sẽ mở ở đợt Nhà sắp tới.", true);
                break;
            default: // Thông tin (gói −22)
                if (!online()) {
                    addChatLog(TAB_SYSTEM, null, "Vào thế giới online mới xem được thông tin.", true);
                    return;
                }
                try {
                    AvatarProtocol.requestYourInfo(game.connection, id);
                } catch (IOException e) {
                    addChatLog(TAB_SYSTEM, null, "Không gửi được yêu cầu (mất kết nối?).", true);
                }
                break;
        }
    }

    /** Vẽ các hiệu ứng Đánh/Hôn trong không gian thế giới (gọi khi batch
     *  đang ở projection của worldCam, sau khi vẽ nhân vật). */
    private void drawActFx(float playerFeetX, float playerFeetY) {
        for (ActFx fx : actFx) {
            float fxX = fx.target != null ? fx.target.feetX : playerFeetX;
            float fxY = fx.target != null ? fx.target.feetY : playerFeetY;
            float headY = fxY + 108f;
            if (fx.kind == 101) {
                // hôn: 5 trái tim hồng bay lên và tan dần
                for (int k = 0; k < 5; k++) {
                    float ph = fx.t * 0.9f - k * 0.13f;
                    if (ph < 0f || ph > 1f) continue;
                    float hx = fxX + (k - 2) * 11f + (float) Math.sin(k * 2.3f + fx.t * 4f) * 5f;
                    float hy = headY + 6f + ph * 44f;
                    Color c = new Color(1f, 0.38f, 0.58f, 1f - ph * 0.85f);
                    Ui.faIcon(batch, k == 2 ? Ui.fa26 : Ui.fa20, Ui.FA_HEART, hx, hy, c);
                }
            } else {
                // đánh: chùm sao vàng bung ra quanh đầu
                float ph = Math.min(1f, fx.t * 1.6f);
                for (int k = 0; k < 6; k++) {
                    float ang = k * (float) Math.PI / 3f + 0.4f;
                    float r = ph * 27f;
                    Color c = new Color(1f, 0.8f, 0.3f, 1f - ph * 0.8f);
                    Ui.faIcon(batch, Ui.fa20, Ui.FA_STAR,
                        fxX + (float) Math.cos(ang) * r, headY + 8f + (float) Math.sin(ang) * r * 0.7f, c);
                }
                Ui.faIcon(batch, Ui.fa26, Ui.FA_STAR, fxX, headY + 8f,
                    new Color(1f, 0.85f, 0.35f, 1f - ph * 0.6f));
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawPlayerPanel() {
        RemoteFigure f = panelPlayer;
        if (f == null) return;
        if (!remotes.containsValue(f)) { panelPlayer = null; panelSub = false; return; }
        float[] g = playerPanelGeom();
        float px = g[0], py = g[1];
        // đuôi nhọn dưới đáy panel, chỉ xuống đầu nhân vật được chọn;
        // gốc đuôi cắm sâu vào trong panel + miếng vá cùng màu phủ lên
        // viền đáy panel ở đúng đoạn đuôi mọc ra -> LIỀN MẠCH một khối
        float tailCx = panelTailCx();
        batch.setColor(GLASS_RIM);
        batch.draw(tailTexture(), tailCx - 22, py - 25, 44, 31);
        batch.setColor(0.10f, 0.16f, 0.24f, 0.96f);
        batch.draw(tailTexture(), tailCx - 17, py - 21, 34, 25);
        batch.setColor(Color.WHITE);
        // hàng con Tương tác (ô icon + nhãn DƯỚI ô như panel chính)
        if (panelSub) {
            float sy = ppSubY();
            batch.draw(Ui.roundedRect((int) PP_W, (int) PP_SUB_H, 14, GLASS_FILL_DARK, GLASS_RIM), px, sy);
            for (int i = 0; i < 4; i++) {
                float bx = ppSubBtnX(i);
                batch.draw(Ui.roundedRect(100, 52, 10, GLASS_FILL, GLASS_RIM), bx, sy + 24);
                Ui.faIcon(batch, Ui.fa20, PP_SUB_ICONS[i], bx + 50, sy + 50,
                    i == 1 ? new Color(1f, 0.45f, 0.6f, 1f) : Color.WHITE);
                Ui.centered(batch, Ui.fontTiny, PP_SUB_LABELS[i], bx + 50, sy + 15,
                    new Color(1f, 1f, 1f, 0.92f));
            }
        }
        if (infoStats != null && infoForId == f.info.id) {
            float iy = ppInfoY();
            batch.draw(Ui.roundedRect((int) PP_W, 130, 14, GLASS_FILL_DARK, GLASS_RIM), px, iy);
            Ui.fontSmall.setColor(1f, 0.85f, 0.45f, 1f);
            Ui.fontSmall.draw(batch, "THÔNG TIN", px + 18, iy + 130 - 14);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.92f);
            String lv = "Cấp " + infoStats[0] + " · " + infoStats[1] + "%";
            layout.setText(Ui.fontSmall, lv);
            Ui.fontSmall.draw(batch, lv, px + PP_W - 18 - layout.width, iy + 130 - 14);
            String[] labels = {"Thân thiện", "Gây rối", "Sành điệu", "Vui vẻ", "Sức khỏe"};
            Color[] cols = {new Color(0.45f, 0.9f, 0.5f, 0.95f), new Color(0.9f, 0.45f, 0.6f, 0.95f),
                new Color(0.6f, 0.72f, 1f, 0.95f), new Color(1f, 0.8f, 0.35f, 0.95f),
                new Color(1f, 0.55f, 0.4f, 0.95f)};
            for (int i = 0; i < 5; i++) {
                float by = iy + 90 - i * 19;
                Ui.fontTiny.setColor(1f, 1f, 1f, 0.8f);
                Ui.fontTiny.draw(batch, labels[i], px + 18, by + 4);
                batch.draw(Ui.roundedRect(300, 10, 5, new Color(0f, 0f, 0f, 0.45f), GLASS_RIM),
                    px + 108, by - 4);
                int v = MathUtils.clamp(infoStats[2 + i], 0, 100);
                if (v > 0) {
                    batch.draw(Ui.roundedRect(Math.max(10, 300 * v / 100), 10, 5, cols[i], cols[i]),
                        px + 108, by - 4);
                }
                Ui.fontTiny.setColor(1f, 1f, 1f, 0.9f);
                Ui.fontTiny.draw(batch, String.valueOf(v), px + 418, by + 4);
            }
        }
        // panel chính
        batch.draw(Ui.roundedRect((int) PP_W, (int) PP_H, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        // vá liền mạch: phủ viền đáy panel đúng đoạn đuôi mọc ra
        batch.setColor(0.10f, 0.16f, 0.24f, 0.96f);
        batch.draw(whiteTex, tailCx - 15, py - 1, 30, 6);
        batch.setColor(Color.WHITE);
        // avatar TRÒN nằm GỌN TRONG panel như mẫu (hàng riêng phía trên,
        // không nhô ra ngoài): nền tròn + chân dung doll + vòng viền sáng
        float avCx = px + 48, avCy = py + PP_H - 44;
        float avX = avCx - 29, avY = avCy - 29, avS = 58;
        batch.setColor(0.35f, 0.55f, 0.75f, 0.55f);
        batch.draw(circleTexture(), avCx - 30, avCy - 30, 60, 60);
        batch.setColor(Color.WHITE);
        int[] b = f.doll.bounds(f.anim.frameIndex());
        float unit = 4f;
        float feetX = avX + avS / 2f - ((b[0] + b[2]) / 2f) * unit;
        float feetY = avY + avS + b[1] * unit;
        Rectangle clip = new Rectangle(avX + 2, avY + 2, avS - 4, avS - 4);
        Rectangle scissors = new Rectangle();
        batch.flush();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            game.dollRenderer.draw(batch, f.doll, f.anim.frameIndex(), feetX, feetY, unit, f.anim.facingLeft);
            batch.flush();
            ScissorStack.popScissors();
        }
        // vòng viền sáng quanh avatar (đè lên góc vuông của chân dung)
        batch.setColor(0.72f, 0.84f, 1f, 1f);
        batch.draw(ringTexture(), avCx - 36, avCy - 36, 72, 72);
        batch.setColor(Color.WHITE);
        // tên vàng VIỀN NÂU ĐẬM như mẫu, canh giữa theo avatar tròn
        String nm = f.info.name;
        float nx = px + 100, ny = avCy + 11;
        Ui.font.setColor(0.30f, 0.16f, 0.04f, 1f);
        Ui.font.draw(batch, nm, nx - 1.6f, ny);
        Ui.font.draw(batch, nm, nx + 1.6f, ny);
        Ui.font.draw(batch, nm, nx, ny - 1.6f);
        Ui.font.draw(batch, nm, nx, ny + 1.6f);
        Ui.font.setColor(1f, 0.83f, 0.38f, 1f);
        Ui.font.draw(batch, nm, nx, ny);
        Ui.font.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + PP_W - 54, py + PP_H - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + PP_W - 36, py + PP_H - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        // 5 nút chính: ô icon bo góc + nhãn nằm DƯỚI ô (đúng mẫu)
        for (int i = 0; i < 5; i++) {
            float bx = ppBtnX(i);
            boolean active = i == 3 && panelSub;
            batch.draw(Ui.roundedRect(68, 62, 12,
                active ? new Color(0.16f, 0.3f, 0.44f, 0.9f) : GLASS_FILL, GLASS_RIM), bx, ppBtnY());
            Ui.faIcon(batch, Ui.fa26, PP_ICONS[i], bx + 34, ppBtnY() + 31,
                i == 3 ? new Color(1f, 0.45f, 0.6f, 1f) : Color.WHITE);
            Ui.centered(batch, Ui.fontTiny, PP_LABELS[i], bx + 34, py + 20,
                new Color(1f, 1f, 1f, 0.92f));
        }
    }

    /** Hộp thoại lời mời kết bạn (gói −19 server chuyển tới): Đồng ý /
     *  Từ chối — trả lời cũng bằng gói −19 như client gốc. */
    private void drawFriendInvite() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        float pw = 440, ph = 168, px = (Ui.W - pw) / 2f, py = (Ui.H - ph) / 2f;
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Ui.font.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.centered(batch, Ui.font, "LỜI MỜI KẾT BẠN", px + pw / 2f, py + ph - 22, new Color(1f, 0.85f, 0.45f, 1f));
        Ui.centered(batch, Ui.fontSmall, friendInviteName + " muốn kết bạn với bạn.",
            px + pw / 2f, py + ph - 62, new Color(1f, 1f, 1f, 0.92f));
        batch.draw(Ui.gradientRect(150, 46, 10, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            px + 52, py + 22);
        Ui.centered(batch, Ui.font, "Đồng ý", px + 52 + 75, py + 22 + 31, Color.WHITE);
        batch.draw(Ui.roundedRect(150, 46, 10, Ui.BTN_CANCEL, GLASS_RIM),
            px + pw - 52 - 150, py + 22);
        Ui.centered(batch, Ui.font, "Từ chối", px + pw - 52 - 75, py + 22 + 31, Color.WHITE);
    }

    // ================= Thư / Tin nhắn + Bạn bè =================
    // Port của MessageScr (3 tab: Chung / Tin đến / chat riêng) và
    // ListScr friendlist của gốc, vẽ theo tông kính của bản port.

    private Convo findConvo(int id) {
        for (Convo c : convos) if (c.id == id) return c;
        return null;
    }

    private Convo convo(int id, String name) {
        Convo c = findConvo(id);
        if (c == null) {
            c = new Convo();
            c.id = id;
            c.name = name != null && !name.isEmpty() ? name : ("Người chơi " + id);
            convos.add(c);
        } else if (name != null && !name.isEmpty()) {
            c.name = name;
        }
        return c;
    }

    /** MessageScr.addPlayer của gốc: thêm tin vào cuộc trò chuyện, tăng
     *  số chưa đọc nếu không đang xem chính cuộc trò chuyện đó. */
    private void convoAdd(int id, String name, String text, boolean owner) {
        Convo c = convo(id, name);
        if (text != null && !text.isEmpty()) {
            c.msgs.add(new ConvoMsg(text, owner));
            while (c.msgs.size() > 60) c.msgs.remove(0);
            c.preview = text;
        }
        boolean viewing = msgOpen && msgTab == 2 && chatConvo == c;
        if (!owner && !viewing) c.unread++;
        if (viewing) msgScroll = 99999f; // bám đáy, được kẹp lại khi vẽ
    }

    private int totalUnread() {
        int n = 0;
        for (Convo c : convos) n += c.unread;
        return n + mailUnread();
    }

    /** Đóng các panel modal khác để panel Thư/Bạn bè không chồng lên
     *  chúng (kính chồng kính không đọc được chữ). */
    private void closeOtherPanels() {
        musicOpen = false;
        mailOpen = false;
        shopOpen = false;
        bagPanel = false;
        chestPanel = false;
        charPanel = false;
        settingsOpen = false;
        passFormOpen = false;
        giftFormOpen = false;
        cityMapOpen = false;
        boardOpen = false;
        npcMenuOpen = false;
        panelPlayer = null;
        panelSub = false;
        menuOpen = false;
    }

    private void openMsgPanel() {
        closeOtherPanels();
        friendOpen = false;
        msgOpen = true;
        msgTab = 1;
        msgScroll = 0f;
    }

    private void openConvoChat(Convo c) {
        chatConvo = c;
        c.unread = 0;
        msgTab = 2;
        msgScroll = 99999f;
    }

    /** MessageScr.startChat của gốc: mở tab chat riêng với một người. */
    private void startChat(int id, String name) {
        closeOtherPanels();
        friendOpen = false;
        msgOpen = true;
        openConvoChat(convo(id, name));
    }

    /** Trả lời lời mời kết bạn (gói −19) — dùng chung cho hộp thoại bật
     *  ngay và nút Đồng ý/Từ chối trong hộp Tin đến. */
    private void replyFriendInvite(int id, boolean accept) {
        if (online()) {
            try {
                AvatarProtocol.addFriendReply(game.connection, id, accept);
            } catch (IOException e) {
                addChatLog(TAB_SYSTEM, null, "Không gửi được trả lời (mất kết nối?).", true);
            }
        }
        Convo c = findConvo(id);
        String nm = c != null ? c.name : friendInviteName;
        if (c != null) {
            c.friendReq = false;
            c.unread = 0;
            c.preview = accept ? "Đã trở thành bạn bè" : "Đã từ chối lời mời";
            c.msgs.add(new ConvoMsg(accept
                ? "Hai bạn đã trở thành bạn bè."
                : "Bạn đã từ chối lời mời kết bạn.", true));
        }
        addChatLog(TAB_SYSTEM, null, accept
            ? "Bạn đã đồng ý kết bạn với " + nm + "."
            : "Bạn đã từ chối lời mời của " + nm + ".", true);
        if (friendInviteId == id) {
            friendInviteId = 0;
            friendInviteName = null;
        }
    }

    /** Gửi tin từ ô nhập của panel: tab Chung = chat công cộng (gói 55),
     *  tab chat riêng = gói −6 + tự ghi vào cuộc trò chuyện như gốc. */
    private void msgSend() {
        String text = msgInput.toString().trim();
        if (text.isEmpty()) return;
        msgInput.setLength(0);
        if (msgTab == 2 && chatConvo != null) {
            if (!online()) {
                addChatLog(TAB_SYSTEM, null, "Mất kết nối — không gửi được tin nhắn riêng.", true);
                return;
            }
            try {
                AvatarProtocol.chatTo(game.connection, chatConvo.id, text);
            } catch (IOException e) {
                addChatLog(TAB_SYSTEM, null, "Không gửi được tin nhắn riêng (mất kết nối?).", true);
                return;
            }
            convoAdd(chatConvo.id, chatConvo.name, text, true);
            msgScroll = 99999f;
        }
    }

    private void openFriendPanel() {
        closeOtherPanels();
        msgOpen = false;
        friendOpen = true;
        friendArmDelete = -1;
        frScroll = 0f;
        requestFriends();
    }

    private void requestFriends() {
        if (!online()) {
            friendLoading = false;
            return;
        }
        friendLoading = true;
        try {
            AvatarProtocol.requestFriendList(game.connection);
        } catch (IOException e) {
            friendLoading = false;
        }
    }

    /** Đọc dữ liệu "friendlist" trong gói −81 theo đúng ListScr.readList
     *  của gốc: UTF tiêu đề, int idList, byte loại, byte trang, short số
     *  bạn; mỗi bạn: byte số part, short part..., int IDDB, short idImg,
     *  short idWedding, short idStatus, UTF tên, UTF trạng thái; cuối là
     *  byte số mục menu (bản port tự vẽ nút nên bỏ qua nội dung menu). */
    private void parseFriendList(java.io.DataInputStream r) {
        try {
            r.readUTF();
            r.readInt();
            int type = r.readByte();
            r.readByte();
            int count = r.readShort();
            List<FriendEntry> list = new ArrayList<>();
            if (type == 1) {
                for (int i = 0; i < count; i++) {
                    FriendEntry fe = new FriendEntry();
                    int n = r.readByte();
                    for (int k = 0; k < n; k++) fe.parts.add((int) r.readShort());
                    fe.id = r.readInt();
                    r.readShort();
                    r.readShort();
                    r.readShort();
                    fe.name = r.readUTF();
                    fe.status = r.readUTF();
                    fe.doll = new PaperDoll(game.data, fe.parts);
                    list.add(fe);
                }
            }
            friends.clear();
            friends.addAll(list);
            friendLoading = false;
            frScroll = MathUtils.clamp(frScroll, 0f, frMaxScroll());
        } catch (Exception e) {
            friendLoading = false;
        }
    }

    /** Nút trên một hàng bạn: 0 = Nhắn tin, 1 = Xoá bạn (chạm 2 lần). */
    private void friendRowAction(int idx, int kind) {
        if (idx < 0 || idx >= friends.size()) return;
        FriendEntry fe = friends.get(idx);
        if (kind == 0) {
            friendOpen = false;
            startChat(fe.id, fe.name);
        } else if (kind == 1) {
            if (friendArmDelete != idx) {
                friendArmDelete = idx;
                return;
            }
            friendArmDelete = -1;
            if (online()) {
                try {
                    AvatarProtocol.removeFriend(game.connection, fe.id);
                } catch (IOException ignored) {
                }
            }
            friends.remove(idx);
            addChatLog(TAB_SYSTEM, null, "Đã xoá " + fe.name + " khỏi danh sách bạn.", true);
        }
    }

    // ----- hình học panel Tin nhắn -----
    private float msgContentTop() { return MSG_PY + MSG_PH - 132f; }
    private float msgContentBottom() { return msgTab == 1 ? MSG_PY + 18f : MSG_PY + 76f; }
    private float msgViewH() { return msgContentTop() - msgContentBottom(); }

    private float chatBlockH(ConvoMsg m) {
        return wrapMsg(m.text, 520f).size() * 23f + 14f;
    }

    private float msgContentH() {
        if (msgTab == 1) return convos.size() * 66f;
        if (chatConvo == null) return 0f;
        float h = 6f;
        for (ConvoMsg m : chatConvo.msgs) h += chatBlockH(m);
        return h;
    }

    private float msgMaxScroll() { return Math.max(0f, msgContentH() - msgViewH()); }

    private float chatTabW() {
        if (chatConvo == null) return 0f;
        layout.setText(Ui.fontSmall, chatConvo.name);
        return Math.min(280f, layout.width + 48f);
    }

    // ----- hình học panel Bạn bè -----
    private float frContentTop() { return FR_PY + FR_PH - 84f; }
    private float frContentBottom() { return FR_PY + 18f; }
    private float frMaxScroll() {
        return Math.max(0f, friends.size() * 92f - 8f - (frContentTop() - frContentBottom()));
    }

    /** Ngắt dòng theo bề rộng font (như TextMsg của gốc ngắt ở nửa màn). */
    private List<String> wrapMsg(String text, float maxW) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            layout.setText(Ui.fontSmall, trial);
            if (layout.width > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(trial);
            }
            while (cur.length() > 1) {
                layout.setText(Ui.fontSmall, cur.toString());
                if (layout.width <= maxW) break;
                int cut = cur.length() - 1;
                while (cut > 1) {
                    layout.setText(Ui.fontSmall, cur.substring(0, cut));
                    if (layout.width <= maxW) break;
                    cut--;
                }
                out.add(cur.substring(0, cut));
                cur = new StringBuilder(cur.substring(cut));
            }
        }
        if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
        return out;
    }

    /** Cắt chuỗi cho vừa bề rộng, thêm "…" nếu phải cắt. */
    private String fitText(com.badlogic.gdx.graphics.g2d.BitmapFont font, String s, float maxW) {
        if (s == null) return "";
        layout.setText(font, s);
        if (layout.width <= maxW) return s;
        while (s.length() > 0) {
            s = s.substring(0, s.length() - 1);
            layout.setText(font, s + "…");
            if (layout.width <= maxW) return s + "…";
        }
        return "…";
    }

    private void drawMsgTab(int t, float x, float tabY, float w, String label) {
        boolean sel = msgTab == t;
        batch.draw(Ui.roundedRect((int) w, 40, 12,
            sel ? new Color(0.85f, 0.45f, 0.12f, 0.95f) : GLASS_FILL,
            sel ? new Color(1f, 0.75f, 0.4f, 0.9f) : GLASS_RIM), x, tabY);
        Ui.centered(batch, Ui.fontSmall, label, x + w / 2f, tabY + 26, Color.WHITE);
    }

    private void drawMsgPanel() {
        msgScroll = MathUtils.clamp(msgScroll, 0f, msgMaxScroll());
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        float px = MSG_PX, py = MSG_PY, top = py + MSG_PH;
        batch.draw(Ui.roundedRect((int) MSG_PW, (int) MSG_PH, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Ui.faIcon(batch, Ui.fa26, Ui.FA_ENVELOPE, px + 40, top - 36, new Color(1f, 0.85f, 0.45f, 1f));
        Ui.fontTitle.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontTitle.draw(batch, "Tin nhắn", px + 64, top - 27);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + MSG_PW - 54, top - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + MSG_PW - 36, top - 36, new Color(0.9f, 0.93f, 1f, 1f));
        batch.draw(Ui.roundedRect((int) (MSG_PW - 44), 2, 1, new Color(1f, 1f, 1f, 0.14f), null), px + 22, top - 66);
        // hàng tab
        float tabY = top - 118;
        drawMsgTab(1, px + 22, tabY, 150, "Tin đến");
        if (chatConvo != null) {
            drawMsgTab(2, px + 182, tabY, chatTabW(), fitText(Ui.fontSmall, chatConvo.name, 220));
        }
        // nội dung (cắt theo vùng cuộn)
        float cTop = msgContentTop(), cBot = msgContentBottom();
        Rectangle clip = new Rectangle(px + 12, cBot, MSG_PW - 24, cTop - cBot);
        Rectangle scissors = new Rectangle();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            if (msgTab == 1) drawMsgInbox(px, cTop);
            else drawMsgChat(px, cTop);
            batch.flush();
            ScissorStack.popScissors();
        }
        // huy hiệu chưa đọc trên tab Tin đến — vẽ SAU nội dung để số
        // không bị chữ/số trong vùng cuộn ghi đè mất
        if (totalUnread() > 0) {
            batch.setColor(0.92f, 0.25f, 0.2f, 1f);
            batch.draw(circleTexture(), px + 22 + 150 - 13, tabY + 27, 24, 24);
            batch.setColor(Color.WHITE);
            Ui.centered(batch, Ui.fontSmall, String.valueOf(Math.min(totalUnread(), 99)),
                px + 22 + 150 - 1, tabY + 43, Color.WHITE);
        }
        if ((msgTab == 1 && convos.isEmpty())
            || (msgTab == 2 && (chatConvo == null || chatConvo.msgs.isEmpty()))) {
            Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.8f);
            String s = msgTab == 1 ? "Chưa có tin nhắn nào."
                : "Chưa có tin nhắn nào — chào bạn ấy một câu đi.";
            layout.setText(Ui.fontSmall, s);
            Ui.fontSmall.draw(batch, s, px + MSG_PW / 2f - layout.width / 2f, (cTop + cBot) / 2f);
        }
        // ô nhập + nút Gửi (chỉ ở tab chat riêng)
        if (msgTab == 2) {
            float inY = py + 18;
            batch.draw(Ui.roundedRect((int) (MSG_PW - 44 - 128), 46, 10,
                new Color(1f, 1f, 1f, 0.08f), GLASS_RIM), px + 22, inY);
            String shown = msgInput.toString();
            if (shown.isEmpty()) {
                Ui.fontSmall.setColor(0.7f, 0.76f, 0.88f, 0.75f);
                Ui.fontSmall.draw(batch, chatConvo != null
                    ? "Nhắn cho " + chatConvo.name + "…" : "Nhập tin nhắn…", px + 36, inY + 29);
            } else {
                while (!shown.isEmpty()) {
                    layout.setText(Ui.fontSmall, shown);
                    if (layout.width <= MSG_PW - 44 - 128 - 24) break;
                    shown = shown.substring(1);
                }
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.96f);
                Ui.fontSmall.draw(batch, shown, px + 36, inY + 29);
            }
            batch.draw(Ui.gradientRect(118, 46, 10, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
                px + MSG_PW - 22 - 118, inY);
            Ui.centered(batch, Ui.font, "Gửi", px + MSG_PW - 22 - 59, inY + 30, Color.WHITE);
        }
    }

    private void drawMsgInbox(float px, float cTop) {
        for (int i = 0; i < convos.size(); i++) {
            float rowTop = cTop + msgScroll - i * 66f;
            if (rowTop < msgContentBottom() - 4 || rowTop - 66 > cTop + 4) continue;
            Convo c = convos.get(i);
            Ui.font.setColor(1f, 1f, 1f, 0.96f);
            Ui.font.draw(batch, fitText(Ui.font, c.name, 380), px + 22, rowTop - 25);
            if (c.friendReq) {
                Ui.fontSmall.setColor(1f, 0.72f, 0.35f, 1f);
                Ui.fontSmall.draw(batch, "Lời mời kết bạn", px + 22, rowTop - 50);
                float by = rowTop - 58;
                batch.draw(Ui.gradientRect(96, 38, 10, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
                    px + MSG_PW - 22 - 200, by);
                Ui.centered(batch, Ui.fontSmall, "Đồng ý", px + MSG_PW - 22 - 200 + 48, by + 25, Color.WHITE);
                batch.draw(Ui.roundedRect(96, 38, 10, Ui.BTN_CANCEL, GLASS_RIM),
                    px + MSG_PW - 22 - 96, by);
                Ui.centered(batch, Ui.fontSmall, "Từ chối", px + MSG_PW - 22 - 96 + 48, by + 25, Color.WHITE);
            } else {
                Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.85f);
                Ui.fontSmall.draw(batch, fitText(Ui.fontSmall, c.preview, 560), px + 22, rowTop - 50);
                if (c.unread > 0) {
                    batch.setColor(0.92f, 0.25f, 0.2f, 1f);
                    batch.draw(circleTexture(), px + MSG_PW - 22 - 62, rowTop - 45, 24, 24);
                    batch.setColor(Color.WHITE);
                    Ui.centered(batch, Ui.fontSmall, String.valueOf(Math.min(c.unread, 99)),
                        px + MSG_PW - 22 - 50, rowTop - 29, Color.WHITE);
                }
                Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + MSG_PW - 22 - 15, rowTop - 33,
                    new Color(0.7f, 0.76f, 0.88f, 0.8f));
            }
            batch.draw(Ui.roundedRect((int) (MSG_PW - 44), 1, 1, new Color(1f, 1f, 1f, 0.07f), null),
                px + 22, rowTop - 65);
        }
    }

    private void drawMsgChat(float px, float cTop) {
        if (chatConvo == null) return;
        float yCursor = cTop + msgScroll;
        for (ConvoMsg m : chatConvo.msgs) {
            float blockH = chatBlockH(m);
            float blockTop = yCursor;
            yCursor -= blockH;
            if (blockTop < msgContentBottom() - 4 || blockTop - blockH > cTop + 4) continue;
            List<String> lines = wrapMsg(m.text, 520f);
            float textW = 0f;
            for (String ln : lines) {
                layout.setText(Ui.fontSmall, ln);
                textW = Math.max(textW, layout.width);
            }
            float bw = textW + 26;
            float bx = m.owner ? px + MSG_PW - 22 - bw : px + 22;
            batch.draw(Ui.roundedRect((int) bw, (int) (blockH - 4), 10,
                m.owner ? new Color(0.85f, 0.45f, 0.12f, 0.75f) : GLASS_FILL,
                m.owner ? new Color(1f, 0.75f, 0.4f, 0.7f) : GLASS_RIM), bx, blockTop - blockH + 2);
            for (int j = 0; j < lines.size(); j++) {
                layout.setText(Ui.fontSmall, lines.get(j));
                float lx = m.owner ? bx + bw - 13 - layout.width : bx + 13;
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.97f);
                Ui.fontSmall.draw(batch, lines.get(j), lx, blockTop - 20 - j * 23f);
            }
        }
    }

    private void msgTouch(float x, float y, int pointer) {
        float px = MSG_PX, top = MSG_PY + MSG_PH;
        if (x >= px + MSG_PW - 54 && x <= px + MSG_PW - 18 && y >= top - 54 && y <= top - 18) {
            msgOpen = false;
            msgPointer = -1;
            return;
        }
        float tabY = top - 118;
        if (y >= tabY && y <= tabY + 40) {
            if (x >= px + 22 && x <= px + 172) {
                msgTab = 1;
                msgScroll = 0f;
                return;
            }
            if (chatConvo != null && x >= px + 182 && x <= px + 182 + chatTabW()) {
                msgTab = 2;
                msgScroll = 99999f;
                return;
            }
        }
        float cTop = msgContentTop(), cBot = msgContentBottom();
        if (msgTab == 1 && y >= cBot && y <= cTop && x >= px + 12 && x <= px + MSG_PW - 12) {
            int idx = (int) ((cTop + msgScroll - y) / 66f);
            if (idx >= 0 && idx < convos.size()) {
                Convo c = convos.get(idx);
                float rowTop = cTop + msgScroll - idx * 66f;
                if (c.friendReq) {
                    float by = rowTop - 58;
                    if (y >= by && y <= by + 38) {
                        if (x >= px + MSG_PW - 22 - 200 && x <= px + MSG_PW - 22 - 104) {
                            replyFriendInvite(c.id, true);
                            return;
                        }
                        if (x >= px + MSG_PW - 22 - 96 && x <= px + MSG_PW - 22) {
                            replyFriendInvite(c.id, false);
                            return;
                        }
                    }
                    return;
                }
                if (x >= px + MSG_PW - 54 && x <= px + MSG_PW - 18
                    && y >= rowTop - 50 && y <= rowTop - 16) {
                    convos.remove(idx);
                    if (chatConvo == c) chatConvo = null;
                    return;
                }
                msgPendingTap = idx;
            }
        }
        if (msgTab == 2) {
            float inY = MSG_PY + 18;
            if (y >= inY && y <= inY + 46 && x >= px + MSG_PW - 22 - 118 && x <= px + MSG_PW - 22) {
                msgSend();
                return;
            }
        }
        if (y >= cBot && y <= cTop && x >= px && x <= px + MSG_PW) {
            msgPointer = pointer;
            msgDragStartY = y;
            msgDragStartScroll = msgScroll;
            msgDragMoved = 0f;
        }
    }

    private void drawFriendPanel() {
        frScroll = MathUtils.clamp(frScroll, 0f, frMaxScroll());
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        float px = FR_PX, py = FR_PY, top = py + FR_PH;
        batch.draw(Ui.roundedRect((int) FR_PW, (int) FR_PH, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Ui.faIcon(batch, Ui.fa26, Ui.FA_USER_GROUP, px + 40, top - 36, new Color(1f, 0.85f, 0.45f, 1f));
        Ui.fontTitle.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontTitle.draw(batch, "Bạn bè", px + 64, top - 27);
        Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.85f);
        Ui.fontSmall.draw(batch, friends.size() + " bạn bè", px + 214, top - 40);
        batch.draw(Ui.roundedRect(36, 36, 10, GLASS_FILL, GLASS_RIM), px + FR_PW - 100, top - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_ROTATE_RIGHT, px + FR_PW - 82, top - 36, new Color(0.9f, 0.93f, 1f, 1f));
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + FR_PW - 54, top - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + FR_PW - 36, top - 36, new Color(0.9f, 0.93f, 1f, 1f));
        batch.draw(Ui.roundedRect((int) (FR_PW - 44), 2, 1, new Color(1f, 1f, 1f, 0.14f), null), px + 22, top - 66);
        float cTop = frContentTop(), cBot = frContentBottom();
        if (!online() && friends.isEmpty()) {
            drawFriendNote("Vào thế giới online mới xem được danh sách bạn.", cTop, cBot);
            return;
        }
        if (friendLoading && friends.isEmpty()) {
            drawFriendNote("Đang tải danh sách bạn…", cTop, cBot);
            return;
        }
        if (friends.isEmpty()) {
            drawFriendNote("Chưa có bạn bè nào.", cTop, cBot);
            Ui.fontSmall.setColor(0.7f, 0.76f, 0.88f, 0.75f);
            String s = "Gặp người chơi khác, mở panel tương tác rồi chọn Kết bạn.";
            layout.setText(Ui.fontSmall, s);
            Ui.fontSmall.draw(batch, s, px + FR_PW / 2f - layout.width / 2f, (cTop + cBot) / 2f - 28);
            return;
        }
        Rectangle clip = new Rectangle(px + 12, cBot, FR_PW - 24, cTop - cBot);
        Rectangle scissors = new Rectangle();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            for (int i = 0; i < friends.size(); i++) {
                float rowTop = cTop + frScroll - i * 92f;
                if (rowTop < cBot - 4 || rowTop - 84 > cTop + 4) continue;
                FriendEntry fe = friends.get(i);
                boolean on = fe.status != null && fe.status.startsWith("Đang");
                // thẻ hàng: nền kính sáng nhẹ, bo góc
                batch.draw(Ui.roundedRect((int) (FR_PW - 36), 84, 12,
                    new Color(1f, 1f, 1f, on ? 0.09f : 0.05f),
                    new Color(1f, 1f, 1f, on ? 0.22f : 0.13f)), px + 18, rowTop - 84);
                // khung avatar riêng cho doll
                batch.draw(Ui.roundedRect(62, 62, 10,
                    new Color(0f, 0f, 0f, 0.28f), new Color(1f, 1f, 1f, 0.16f)),
                    px + 30, rowTop - 73);
                if (fe.doll != null) {
                    game.dollRenderer.draw(batch, fe.doll, 0, px + 61, rowTop - 67, 1.15f, false);
                }
                Ui.font.setColor(1f, 1f, 1f, 0.97f);
                Ui.font.draw(batch, fitText(Ui.font, fe.name, 280), px + 108, rowTop - 20);
                // chấm trạng thái + chữ
                batch.setColor(on ? new Color(0.35f, 0.88f, 0.45f, 1f)
                    : new Color(0.5f, 0.56f, 0.68f, 1f));
                batch.draw(circleTexture(), px + 109, rowTop - 54, 10, 10);
                batch.setColor(Color.WHITE);
                Ui.fontSmall.setColor(on ? new Color(0.55f, 0.93f, 0.6f, 1f)
                    : new Color(0.7f, 0.76f, 0.88f, 0.85f));
                Ui.fontSmall.draw(batch, fe.status != null ? fe.status : "", px + 126, rowTop - 43);
                float by = rowTop - 63;
                batch.draw(Ui.gradientRect(108, 42, 11, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
                    px + FR_PW - 228, by);
                Ui.centered(batch, Ui.fontSmall, "Nhắn tin", px + FR_PW - 228 + 54, by + 27, Color.WHITE);
                boolean armed = friendArmDelete == i;
                batch.draw(Ui.roundedRect(92, 42, 11,
                    armed ? new Color(0.55f, 0.12f, 0.1f, 0.92f) : Ui.BTN_CANCEL, GLASS_RIM),
                    px + FR_PW - 110, by);
                Ui.centered(batch, Ui.fontSmall, armed ? "Xoá?" : "Xoá bạn",
                    px + FR_PW - 110 + 46, by + 27, Color.WHITE);
            }
            batch.flush();
            ScissorStack.popScissors();
        }
    }

    private void drawFriendNote(String s, float cTop, float cBot) {
        Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.85f);
        layout.setText(Ui.fontSmall, s);
        Ui.fontSmall.draw(batch, s, FR_PX + FR_PW / 2f - layout.width / 2f, (cTop + cBot) / 2f);
    }

    private void friendTouch(float x, float y, int pointer) {
        float px = FR_PX, top = FR_PY + FR_PH;
        if (x >= px + FR_PW - 54 && x <= px + FR_PW - 18 && y >= top - 54 && y <= top - 18) {
            friendOpen = false;
            frPointer = -1;
            return;
        }
        if (x >= px + FR_PW - 100 && x <= px + FR_PW - 64 && y >= top - 54 && y <= top - 18) {
            requestFriends();
            return;
        }
        float cTop = frContentTop(), cBot = frContentBottom();
        if (y >= cBot && y <= cTop && x >= px && x <= px + FR_PW) {
            int idx = (int) ((cTop + frScroll - y) / 92f);
            if (idx >= 0 && idx < friends.size()) {
                float rowTop = cTop + frScroll - idx * 92f;
                float by = rowTop - 63;
                if (y >= by && y <= by + 42) {
                    if (x >= px + FR_PW - 228 && x <= px + FR_PW - 120) {
                        frPendingIdx = idx;
                        frPendingKind = 0;
                    } else if (x >= px + FR_PW - 110 && x <= px + FR_PW - 18) {
                        frPendingIdx = idx;
                        frPendingKind = 1;
                    }
                }
            }
            frPointer = pointer;
            frDragStartY = y;
            frDragStartScroll = frScroll;
            frDragMoved = 0f;
        }
    }


    // ================= Hòm Thư (theo mẫu user chốt 2026-11-02) =================
    // Panel thư 2 cột pastel theo đúng ảnh mẫu: trái = danh sách thư,
    // phải = chi tiết + vật phẩm đính kèm + nút Nhận thưởng. Dữ liệu thật
    // từ server (gói −108..−112, bảng letters).
    private static final float ML_PW = 1160f, ML_PH = 620f;
    private boolean mailOpen = false;
    private boolean mailLoaded = false;
    private int mailSelId = -1;
    private float mailScroll = 0f;
    private int mailPointer = -1;
    private float mailDragStartY, mailDragStartScroll, mailDragMoved;
    private int mailPendingIdx = -1;

    private static class MailAtt {
        String t;
        long n;
        int partId;
    }

    private static class MailLetter {
        int id;
        String kind = "system", title = "", sender = "", body = "";
        boolean read, claimed;
        long created;
        final java.util.ArrayList<MailAtt> atts = new java.util.ArrayList<>();
    }

    private final java.util.ArrayList<MailLetter> mailLetters = new java.util.ArrayList<>();

    private static final Color ML_DARK = new Color(0.180f, 0.165f, 0.373f, 1f);
    private static final Color ML_PILL = new Color(0.200f, 0.176f, 0.400f, 1f);
    private static final Color ML_GRAY = new Color(0.545f, 0.529f, 0.659f, 1f);
    private static final Color ML_RIM = new Color(0.812f, 0.788f, 0.933f, 1f);
    private static final Color ML_BODY = new Color(0.227f, 0.212f, 0.388f, 1f);

    /** Quét JSON đính kèm viết tay (không regex — GWT không chạy được
     *  vòng find() của Matcher): [{"t":"xu","n":50000},...]. */
    private static void parseMailAtts(String json, java.util.ArrayList<MailAtt> out) {
        out.clear();
        if (json == null) return;
        int i = 0;
        while (true) {
            int open = json.indexOf('{', i);
            if (open < 0) break;
            int close = json.indexOf('}', open);
            if (close < 0) break;
            String obj = json.substring(open, close + 1);
            MailAtt a = new MailAtt();
            int ti = obj.indexOf("\"t\"");
            if (ti >= 0) {
                int q1 = obj.indexOf('"', ti + 3);
                int q2 = q1 >= 0 ? obj.indexOf('"', q1 + 1) : -1;
                if (q1 >= 0 && q2 > q1) a.t = obj.substring(q1 + 1, q2);
            }
            a.n = scanJsonLong(obj, "\"n\"");
            a.partId = (int) scanJsonLong(obj, "\"id\"");
            if (a.t != null) out.add(a);
            i = close + 1;
        }
    }

    private static long scanJsonLong(String obj, String key) {
        int ki = obj.indexOf(key);
        if (ki < 0) return 0;
        int colon = obj.indexOf(':', ki + key.length());
        if (colon < 0) return 0;
        long v = 0;
        boolean any = false, neg = false;
        for (int j = colon + 1; j < obj.length(); j++) {
            char c = obj.charAt(j);
            if (c == '-' && !any) {
                neg = true;
                continue;
            }
            if (c < '0' || c > '9') {
                if (any) break;
                continue;
            }
            v = v * 10 + (c - '0');
            any = true;
        }
        return neg ? -v : v;
    }

    private int mailUnread() {
        int c = 0;
        for (MailLetter l : mailLetters) if (!l.read) c++;
        return c;
    }

    private MailLetter mailSelected() {
        for (MailLetter l : mailLetters) if (l.id == mailSelId) return l;
        return mailLetters.isEmpty() ? null : mailLetters.get(0);
    }

    private float mailMaxScroll() {
        return Math.max(0f, mailLetters.size() * 100f - 8f - (ML_PH - 208f));
    }

    private void openMailPanel() {
        closeOtherPanels();
        msgOpen = false;
        friendOpen = false;
        mailOpen = true;
        mailScroll = 0f;
        if (online()) {
            try {
                AvatarProtocol.requestMailList(game.connection);
            } catch (IOException ignored) {
            }
        }
    }

    private void mailSelect(int idx) {
        if (idx < 0 || idx >= mailLetters.size()) return;
        MailLetter l = mailLetters.get(idx);
        mailSelId = l.id;
        if (!l.read) {
            l.read = true;
            if (online()) {
                try {
                    AvatarProtocol.mailRead(game.connection, l.id);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static long mailDayIndex(long ms) {
        long off = new java.util.Date(ms).getTimezoneOffset() * 60000L;
        return (ms - off) / 86400000L;
    }

    private static String mailDayLabel(long ms) {
        long diff = mailDayIndex(System.currentTimeMillis()) - mailDayIndex(ms);
        if (diff <= 0) return "Hôm nay";
        if (diff == 1) return "Hôm qua";
        if (diff < 30) return diff + " ngày trước";
        java.util.Date d = new java.util.Date(ms);
        return d.getDate() + "/" + (d.getMonth() + 1);
    }

    private static String mailTimeLabel(long ms) {
        java.util.Date d = new java.util.Date(ms);
        int h = d.getHours(), m = d.getMinutes();
        return mailDayLabel(ms) + ", " + (h < 10 ? "0" : "") + h
            + ":" + (m < 10 ? "0" : "") + m;
    }

    private static String mailKindIcon(String kind) {
        if ("daily".equals(kind) || "gift".equals(kind)) return Ui.FA_GIFT;
        if ("event".equals(kind)) return Ui.FA_BULLHORN;
        if ("friend".equals(kind)) return Ui.FA_HEART;
        if ("update".equals(kind)) return Ui.FA_ROCKET;
        return Ui.FA_ENVELOPE;
    }

    private static Color mailKindBg(String kind) {
        // ô icon kính kiểu ô đồ trong giỏ
        return new Color(0.16f, 0.24f, 0.34f, 0.9f);
    }

    private static Color mailKindFg(String kind) {
        // một màu thống nhất cho mọi icon thư (user chê nhiều màu)
        return new Color(0.85f, 0.92f, 1f, 1f);
    }

    private static String trimToWidth(com.badlogic.gdx.graphics.g2d.BitmapFont f,
            String text, float maxW) {
        if (Ui.textWidth(f, text) <= maxW) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (Ui.textWidth(f, sb.toString() + "...") > maxW) {
                sb.setLength(sb.length() - 1);
                break;
            }
        }
        return sb.toString() + "...";
    }

    private static String fmtNum(long v) {
        String t = String.valueOf(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            if (i > 0 && (t.length() - i) % 3 == 0) sb.append('.');
            sb.append(t.charAt(i));
        }
        return sb.toString();
    }

    private void drawMailPanel() {
        float px = (Ui.W - ML_PW) / 2f, py = (Ui.H - ML_PH) / 2f, top = py + ML_PH;
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) ML_PW, (int) ML_PH, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        // header kiểu panel Bạn bè: icon vàng + tiêu đề vàng + số thư
        Ui.faIcon(batch, Ui.fa26, Ui.FA_ENVELOPE, px + 40, top - 36,
            new Color(1f, 0.85f, 0.45f, 1f));
        Ui.fontTitle.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontTitle.draw(batch, "Hòm Thư", px + 64, top - 27);
        String cnt = mailLetters.size() + " thư"
            + (mailUnread() > 0 ? " · " + mailUnread() + " chưa đọc" : "");
        Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.85f);
        Ui.fontSmall.draw(batch, cnt,
            px + 64 + Ui.textWidth(Ui.fontTitle, "Hòm Thư") + 18, top - 40);
        // nút làm mới + đóng (ô kính 36x36 như panel Bạn bè)
        batch.draw(Ui.roundedRect(36, 36, 10, GLASS_FILL, GLASS_RIM), px + ML_PW - 100, top - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_ROTATE_RIGHT, px + ML_PW - 82, top - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + ML_PW - 54, top - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + ML_PW - 36, top - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        batch.draw(Ui.roundedRect((int) (ML_PW - 44), 2, 1,
            new Color(1f, 1f, 1f, 0.14f), null), px + 22, top - 66);
        // vạch phân cột mờ
        batch.draw(Ui.roundedRect(2, (int) (ML_PH - 190), 1,
            new Color(1f, 1f, 1f, 0.10f), null), px + 452, py + 108);

        float lx = px + 24, lw = 404;
        float listTop = top - 80, listBot = py + 128;
        Rectangle clip = new Rectangle(lx, listBot, lw, listTop - listBot);
        Rectangle scissors = new Rectangle();
        batch.flush();
        ScissorStack.calculateScissors(hudViewport.getCamera(),
            batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            for (int i = 0; i < mailLetters.size(); i++) {
                drawMailCard(mailLetters.get(i), i, lx, listTop);
            }
            batch.flush();
            ScissorStack.popScissors();
        }
        if (mailLetters.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall,
                mailLoaded ? "Chưa có thư nào" : "Đang tải thư...",
                lx + lw / 2f, (listTop + listBot) / 2f + 8,
                new Color(0.75f, 0.82f, 0.95f, 0.85f));
        }
        // chân cột trái: Xoá đã đọc (kính) + Nhận tất cả (cam)
        batch.draw(Ui.roundedRect(192, 52, 14, Ui.BTN_CANCEL, GLASS_RIM), lx, py + 16);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_TRASH, lx + 30, py + 42,
            new Color(0.9f, 0.93f, 1f, 1f));
        Ui.font.setColor(1f, 1f, 1f, 0.95f);
        Ui.font.draw(batch, "Xoá đã đọc", lx + 54, py + 50);
        batch.draw(Ui.gradientRect(200, 52, 14, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            lx + 204, py + 16);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_DOWNLOAD, lx + 234, py + 42, Color.WHITE);
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, "Nhận tất cả", lx + 258, py + 50);

        drawMailDetail(px, py, top);
    }

    private void drawMailCard(MailLetter l, int i, float lx, float listTop) {
        float cardTop = listTop - mailScroll - i * 100f;
        float cy = cardTop - 92f;
        boolean sel = mailSelected() == l;
        batch.draw(Ui.roundedRect(404, 92, 12,
            sel ? new Color(0.16f, 0.30f, 0.44f, 0.92f) : GLASS_FILL,
            sel ? new Color(0.62f, 0.82f, 1f, 0.95f) : GLASS_RIM), lx, cy);
        batch.draw(Ui.roundedRect(58, 58, 12, mailKindBg(l.kind), null), lx + 14, cy + 17);
        Ui.faIcon(batch, Ui.fa26, mailKindIcon(l.kind), lx + 43, cy + 46, mailKindFg(l.kind));
        if (l.read) {
            Ui.fontBaloo30.setColor(0.82f, 0.88f, 0.98f, 0.9f);
        } else {
            Ui.fontBaloo30.setColor(Color.WHITE);
        }
        Ui.fontBaloo30.draw(batch, trimToWidth(Ui.fontBaloo30, l.title, 270), lx + 88, cy + 68);
        Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.85f);
        Ui.fontSmall.draw(batch, l.sender, lx + 88, cy + 30);
        String day = mailDayLabel(l.created);
        Ui.fontSmall.draw(batch, day, lx + 390 - Ui.textWidth(Ui.fontSmall, day), cy + 30);
        if (!l.read) {
            batch.draw(Ui.roundedRect(13, 13, 7,
                new Color(0.941f, 0.267f, 0.220f, 1f), null), lx + 380, cardTop - 20);
        }
    }

    private void drawMailDetail(float px, float py, float top) {
        float dx = px + 476, dw = ML_PW - 476 - 24;
        float dTop = top - 80;
        MailLetter l = mailSelected();
        if (l == null) {
            Ui.centered(batch, Ui.fontSmall, "Chọn một thư để xem nội dung",
                dx + dw / 2f, (dTop + py) / 2f,
                new Color(0.75f, 0.82f, 0.95f, 0.85f));
            return;
        }
        batch.draw(Ui.roundedRect(64, 64, 14, mailKindBg(l.kind), null), dx, dTop - 64);
        Ui.faIcon(batch, Ui.fa30, mailKindIcon(l.kind), dx + 32, dTop - 32, mailKindFg(l.kind));
        Ui.fontBaloo36.setColor(Color.WHITE);
        Ui.fontBaloo36.draw(batch, trimToWidth(Ui.fontBaloo36, l.title, dw - 200), dx + 80, dTop - 20);
        // tem mèo trang trí góc phải (theo mẫu) — bản kính mờ
        com.badlogic.gdx.graphics.Texture stamp = Ui.roundedRect(74, 88, 8,
            GLASS_FILL, GLASS_RIM);
        batch.draw(stamp, dx + dw - 84, dTop - 92, 37, 44, 74, 88, 1f, 1f, -8f,
            0, 0, 74, 88, false, false);
        Ui.faIcon(batch, Ui.fa30, Ui.FA_CAT, dx + dw - 47, dTop - 48,
            new Color(0.85f, 0.92f, 1f, 0.8f));
        // chip người gửi + giờ gửi
        float chipY = dTop - 108;
        float chipW = Ui.textWidth(Ui.fontSmall, l.sender) + 36;
        batch.draw(Ui.roundedRect((int) chipW, 36, 18, GLASS_FILL, GLASS_RIM), dx, chipY);
        Ui.centered(batch, Ui.fontSmall, l.sender, dx + chipW / 2f, chipY + 24,
            new Color(0.85f, 0.92f, 1f, 1f));
        Ui.faIcon(batch, Ui.fa20, Ui.FA_CLOCK, dx + chipW + 24, chipY + 18,
            new Color(0.75f, 0.82f, 0.95f, 0.85f));
        Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.85f);
        Ui.fontSmall.draw(batch, mailTimeLabel(l.created), dx + chipW + 42, chipY + 24);
        // vạch ngăn mờ thay dải tem thư của mẫu
        batch.draw(Ui.roundedRect((int) dw, 2, 1,
            new Color(1f, 1f, 1f, 0.14f), null), dx, chipY - 22);
        // nội dung thư
        float by = chipY - 52;
        Ui.fontSmall.setColor(0.92f, 0.95f, 1f, 0.95f);
        for (String para : l.body.split("\n")) {
            if (para.isEmpty()) {
                by -= 12;
                continue;
            }
            for (String line : wrapMsg(para, dw - 8)) {
                if (by < py + 313) break;
                Ui.fontSmall.draw(batch, line, dx + 2, by);
                by -= 26;
            }
            if (by < py + 313) break;
        }
        // hộp vật phẩm đính kèm (ô inset kiểu thanh dưới của Giỏ đồ)
        float boxY = py + 126;
        batch.draw(Ui.roundedRect((int) dw, 168, 14,
            new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), dx, boxY);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_GIFT, dx + 34, boxY + 140,
            new Color(0.9f, 0.93f, 1f, 1f));
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, "Vật phẩm đính kèm", dx + 54, boxY + 148);
        if (l.atts.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall, "Không có vật phẩm đính kèm",
                dx + dw / 2f, boxY + 74, new Color(0.75f, 0.82f, 0.95f, 0.85f));
        } else {
            for (int i = 0; i < Math.min(l.atts.size(), 5); i++) {
                MailAtt a = l.atts.get(i);
                float tx = dx + 18 + i * 118, ty = boxY + 14;
                batch.draw(Ui.roundedRect(104, 104, 12,
                    new Color(0.16f, 0.24f, 0.34f, 0.9f), GLASS_RIM), tx, ty);
                if ("xu".equals(a.t)) {
                    batch.draw(game.texture("myinfo/icon0.png"), tx + 25, ty + 34, 54, 54);
                } else if ("luong".equals(a.t)) {
                    batch.draw(game.texture("myinfo/icon1.png"), tx + 25, ty + 34, 54, 54);
                } else if ("item".equals(a.t)) {
                    drawPartIcon(a.partId, tx + 52, ty + 62, 60);
                }
                Ui.centered(batch, Ui.fontBaloo30, "x" + fmtNum(a.n), tx + 52, ty + 26, Color.WHITE);
            }
        }
        // nút nhận thưởng: cam (nút chính của game) / kính khi đã nhận
        if (!l.atts.isEmpty()) {
            float bw = 360, bh = 62, bx = dx + (dw - bw) / 2f, byy = py + 22;
            if (l.claimed) {
                batch.draw(Ui.roundedRect((int) bw, (int) bh, 16, GLASS_FILL, GLASS_RIM), bx, byy);
                Ui.faIcon(batch, Ui.fa20, Ui.FA_CHECK, bx + 84, byy + 31,
                    new Color(0.75f, 0.82f, 0.95f, 0.85f));
                Ui.centered(batch, Ui.fontBaloo30, "Đã nhận thưởng", bx + bw / 2f + 14, byy + 40,
                    new Color(0.75f, 0.82f, 0.95f, 0.85f));
            } else {
                batch.draw(Ui.gradientRect((int) bw, (int) bh, 16,
                    EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, byy);
                Ui.faIcon(batch, Ui.fa20, Ui.FA_DOWNLOAD, bx + 74, byy + 31, Color.WHITE);
                Ui.centered(batch, Ui.fontBaloo30, "Nhận thưởng", bx + bw / 2f + 14, byy + 40, Color.WHITE);
            }
        }
    }

    private void mailTouch(float x, float y, int pointer) {
        float px = (Ui.W - ML_PW) / 2f, py = (Ui.H - ML_PH) / 2f, top = py + ML_PH;
        // nút đóng 36x36 góc phải header
        if (x >= px + ML_PW - 54 && x <= px + ML_PW - 18 && y >= top - 54 && y <= top - 18) {
            mailOpen = false;
            mailPointer = -1;
            return;
        }
        // nút làm mới: xin lại danh sách thư
        if (x >= px + ML_PW - 100 && x <= px + ML_PW - 64 && y >= top - 54 && y <= top - 18) {
            if (online()) {
                try {
                    AvatarProtocol.requestMailList(game.connection);
                } catch (IOException ignored) {
                }
            }
            return;
        }
        float lx = px + 24;
        // nút Xoá đã đọc
        if (x >= lx && x <= lx + 192 && y >= py + 16 && y <= py + 68) {
            if (online()) {
                try {
                    AvatarProtocol.mailDeleteRead(game.connection);
                } catch (IOException ignored) {
                }
            } else {
                for (int i = mailLetters.size() - 1; i >= 0; i--) {
                    MailLetter l = mailLetters.get(i);
                    if (l.read && (l.claimed || l.atts.isEmpty())) mailLetters.remove(i);
                }
            }
            return;
        }
        // nút Nhận tất cả
        if (x >= lx + 204 && x <= lx + 404 && y >= py + 16 && y <= py + 68) {
            if (online()) {
                try {
                    AvatarProtocol.mailClaimAll(game.connection);
                } catch (IOException ignored) {
                }
            }
            return;
        }
        // nút Nhận thưởng của thư đang xem
        MailLetter sel = mailSelected();
        if (sel != null && !sel.atts.isEmpty() && !sel.claimed) {
            float dx = px + 476, dw = ML_PW - 476 - 24;
            float bx = dx + (dw - 360) / 2f;
            if (x >= bx && x <= bx + 360 && y >= py + 22 && y <= py + 84) {
                if (online()) {
                    try {
                        AvatarProtocol.mailClaim(game.connection, sel.id);
                    } catch (IOException ignored) {
                    }
                }
                return;
            }
        }
        // vùng danh sách: chờ chọn hàng + kéo cuộn
        float listTop = top - 80, listBot = py + 128;
        if (x >= lx && x <= lx + 404 && y >= listBot && y <= listTop) {
            int idx = (int) ((listTop - mailScroll - y) / 100f);
            mailPendingIdx = idx >= 0 && idx < mailLetters.size() ? idx : -1;
            mailPointer = pointer;
            mailDragStartY = y;
            mailDragStartScroll = mailScroll;
            mailDragMoved = 0f;
        }
    }

    /** Mở panel Hòm Thư cho harness — KHÔNG nạp thư mẫu nữa (user
     *  lệnh 2026-11-02 xoá mấy thư mẫu): panel hiện dữ liệu thật,
     *  rỗng thì hiện trạng thái "Chưa có thư nào". */
    public void debugOpenMailPanel() {
        closeOtherPanels();
        msgOpen = false;
        friendOpen = false;
        mailLoaded = true;
        mailOpen = true;
    }

    // ----- Harness chụp kiểm chứng (chỉ ScreenshotMain dùng) -----
    public void debugSeedSocial() {
        Convo a = convo(1001, "betest02");
        a.msgs.clear();
        a.msgs.add(new ConvoMsg("Chào bạn, hôm nay trời đẹp quá chừng luôn á!", false));
        a.msgs.add(new ConvoMsg("Ừ, lát ra công viên chơi không?", true));
        a.msgs.add(new ConvoMsg("Đi chứ, đợi mình thay đồ đã nha", false));
        a.preview = "Đi chứ, đợi mình thay đồ đã nha";
        a.unread = 2;
        Convo b = convo(1002, "nguoila01");
        b.friendReq = true;
        b.reqText = "nguoila01 muốn kết bạn với bạn.";
        b.preview = "Lời mời kết bạn";
        b.unread = 1;
        Convo c = convo(1003, "banxom99");
        c.msgs.clear();
        c.msgs.add(new ConvoMsg("Mai qua nhà mình chơi nhé", false));
        c.preview = "Mai qua nhà mình chơi nhé";
        friends.clear();
        FriendEntry f1 = new FriendEntry();
        f1.id = 1001;
        f1.name = "betest02";
        f1.status = "Đang trực tuyến";
        f1.parts = new ArrayList<>(java.util.Arrays.asList(628, 474, 0, 80, 46));
        f1.doll = new PaperDoll(game.data, f1.parts);
        friends.add(f1);
        FriendEntry f2 = new FriendEntry();
        f2.id = 1003;
        f2.name = "banxom99";
        f2.status = "Ngoại tuyến";
        f2.parts = new ArrayList<>(java.util.Arrays.asList(494, 0, 302));
        f2.doll = new PaperDoll(game.data, f2.parts);
        friends.add(f2);
        friendLoading = false;
    }

    public void debugOpenMsgInbox() {
        debugSeedSocial();
        closeOtherPanels();
        friendOpen = false;
        msgOpen = true;
        msgTab = 1;
        msgScroll = 0f;
    }

    public void debugOpenMsgChat() {
        debugSeedSocial();
        closeOtherPanels();
        friendOpen = false;
        msgOpen = true;
        openConvoChat(convo(1001, "betest02"));
    }

    /** Mô phỏng đúng cú chạm nút "Nhắn tin" của bạn đầu tiên trong panel
     *  Bạn bè (đi qua friendRowAction như chạm thật) — để kiểm chứng bấm
     *  Nhắn tin là mở thẳng chat với người đó. */
    public void debugFriendTapMessage() {
        debugSeedSocial();
        closeOtherPanels();
        msgOpen = false;
        friendOpen = true;
        friendRowAction(0, 0);
    }

    public void debugOpenFriendPanel() {
        debugSeedSocial();
        closeOtherPanels();
        msgOpen = false;
        friendOpen = true;
    }

    // ================= Panel nhân vật (bấm card góc trái) =================
    // Theo CommandInfo của gốc: cột tiền bằng icon myinfo + doll đứng lớn
    // (+ người yêu nếu có) + 6 thanh chỉ số ở dưới.
    private static final float CP_W = 640f, CP_H = 470f;
    private boolean charPanel = false;
    private int[] selfStats;
    /** Icon đôi (idImage của gói −22) + ngày vào game (đuôi mở rộng −22). */
    private int partnerImgId = -1;
    private String selfJoinDate;
    private String partnerName, partnerRel, partnerSlogan;
    private int partnerLv, partnerPct;
    private PaperDoll partnerDoll;

    private void openCharPanel() {
        charPanel = true;
        panelPlayer = null;
        panelSub = false;
        GameConnection conn = game.connection;
        if (conn != null && !conn.isClosed()) {
            try {
                AvatarProtocol.requestYourInfo(conn, ownId());
            } catch (Exception ignored) {
            }
        }
    }

    /** Chỉ số hiển thị: ưu tiên −22 tươi, chưa có thì lấy số lúc đăng nhập. */
    private int[] charStats() {
        if (selfStats != null) return selfStats;
        com.avatardk.game.net.PlayerInfo p = game.player;
        if (p == null) return null;
        return new int[]{p.levelMain, p.levelPercent,
            MathUtils.clamp(p.friendly, 0, 100), MathUtils.clamp(p.crazy, 0, 100),
            MathUtils.clamp(p.stylish, 0, 100), MathUtils.clamp(p.happy, 0, 100),
            MathUtils.clamp(p.health, 0, 100)};
    }

    private float[] charPanelGeom() {
        return new float[]{(Ui.W - CP_W) / 2f, (Ui.H - CP_H) / 2f};
    }

    /** Kiểu panel nhân vật (3 = thẻ hiện đại — user đã chốt 2026-11-01;
     *  0/1/2 giữ lại để so sánh khi cần). */
    private int charVariant = 3;

    private void drawCharPanel() {
        if (charVariant == 2) {
            drawCharPanelOriginal();
            return;
        }
        if (charVariant == 3) {
            drawCharPanelCards();
            return;
        }
        drawCharPanelGlass(charVariant == 1);
    }

    private void drawCharPanelGlass(boolean light) {
        com.avatardk.game.net.PlayerInfo p = game.player;
        if (p == null) {
            charPanel = false;
            return;
        }
        Color cTitle = light ? new Color(0.10f, 0.25f, 0.50f, 1f) : new Color(1f, 0.85f, 0.45f, 1f);
        Color cText = light ? new Color(0.08f, 0.22f, 0.45f, 1f) : new Color(1f, 1f, 1f, 0.94f);
        Color cStage = light ? new Color(1f, 1f, 1f, 0.38f) : new Color(0.30f, 0.45f, 0.62f, 0.35f);
        Color cTrack = light ? new Color(1f, 1f, 1f, 0.55f) : new Color(0f, 0f, 0f, 0.45f);
        float[] g = charPanelGeom();
        float px = g[0], py = g[1];
        batch.setColor(0f, 0f, 0f, light ? 0.35f : 0.55f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) CP_W, (int) CP_H, 18,
            light ? new Color(0.60f, 0.79f, 0.96f, 0.98f) : new Color(0.07f, 0.11f, 0.17f, 0.97f),
            GLASS_RIM), px, py);
        Ui.fontSmall.setColor(cTitle);
        Ui.fontSmall.draw(batch, "THÔNG TIN NHÂN VẬT", px + 24, py + CP_H - 22);
        Ui.font.setColor(light ? cText : Color.WHITE);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + CP_W - 54, py + CP_H - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + CP_W - 36, py + CP_H - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        int[] st = charStats();
        // cột tiền bằng icon myinfo gốc: Xu, Lượng, Lượng khoá, Xèng, Cấp
        int[] icons = {0, 1, 4, 2};
        String[] vals = {fmtMoney(p.xu), fmtMoney(p.luong), fmtMoney(p.luongKhoa), fmtMoney(p.xeng)};
        float rowTop = py + CP_H - 78;
        for (int i = 0; i < 4; i++) {
            float ry = rowTop - i * 46;
            batch.draw(game.texture("myinfo/icon" + icons[i] + ".png"), px + 24, ry, 26, 26);
            Ui.fontSmall.setColor(cText);
            Ui.fontSmall.draw(batch, vals[i], px + 62, ry + 19);
        }
        float ry = rowTop - 4 * 46;
        batch.draw(game.texture("myinfo/icon3.png"), px + 24, ry, 26, 26);
        Ui.fontSmall.setColor(cText);
        Ui.fontSmall.draw(batch, st != null ? st[0] + " + " + st[1] + "%" : "—", px + 62, ry + 19);
        // tên nhân vật (vàng) dưới cột tiền — nền sáng thì thêm viền nâu
        if (light) {
            Ui.font.setColor(0.30f, 0.16f, 0.04f, 1f);
            for (float[] o : new float[][]{{-1.6f, 0}, {1.6f, 0}, {0, -1.6f}, {0, 1.6f}}) {
                Ui.font.draw(batch, p.username, px + 24 + o[0], py + 176 + o[1]);
            }
        }
        Ui.font.setColor(1f, 0.83f, 0.38f, 1f);
        Ui.font.draw(batch, p.username, px + 24, py + 176);
        Ui.font.setColor(Color.WHITE);
        // sân khấu doll bên phải (+ người yêu nếu gói −22 báo có)
        float stX = px + 306, stY = py + 196, stW = 310, stH = 216;
        batch.draw(Ui.roundedRect((int) stW, (int) stH, 14, cStage, GLASS_RIM), stX, stY);
        float floorY = stY + 36;
        boolean hasPartner = partnerDoll != null;
        float selfCx = hasPartner ? stX + 96 : stX + stW / 2f;
        float partCx = stX + 218;
        batch.setColor(0f, 0f, 0f, 0.30f);
        batch.draw(circleTexture(), selfCx - 52, floorY - 8, 104, 16);
        if (hasPartner) batch.draw(circleTexture(), partCx - 46, floorY - 8, 92, 15);
        batch.setColor(Color.WHITE);
        game.dollRenderer.draw(batch, playerDoll, playerAnim.frameIndex(), selfCx, floorY, 3.4f, false);
        if (hasPartner) {
            game.dollRenderer.draw(batch, partnerDoll, 0, partCx, floorY, 3.4f, true);
            if (partnerRel != null && !partnerRel.isEmpty()) {
                Ui.centered(batch, Ui.fontSmall, partnerRel, partCx, stY + stH - 14,
                    light ? new Color(0.80f, 0.25f, 0.45f, 1f) : new Color(1f, 0.75f, 0.85f, 1f));
            }
            Ui.centered(batch, Ui.fontTiny, partnerName + " · Cấp " + partnerLv + " + " + partnerPct + "%",
                partCx, stY + 18, cText);
        }
        // đường chia + 6 thanh chỉ số (2 cột × 3 hàng) như gốc
        batch.setColor(GLASS_RIM);
        batch.draw(whiteTex, px + 24, py + 158, CP_W - 48, 2);
        batch.setColor(Color.WHITE);
        String[] labels = {"Cấp", "Thân thiện", "Gây rối", "Sành điệu", "Vui vẻ", "Sức khỏe"};
        Color[] cols = {new Color(0.55f, 0.85f, 1f, 0.95f), new Color(0.45f, 0.9f, 0.5f, 0.95f),
            new Color(0.9f, 0.45f, 0.6f, 0.95f), new Color(0.6f, 0.72f, 1f, 0.95f),
            new Color(1f, 0.8f, 0.35f, 0.95f), new Color(1f, 0.55f, 0.4f, 0.95f)};
        int[] barVals = st == null ? new int[6]
            : new int[]{st[1], st[2], st[3], st[4], st[5], st[6]};
        for (int i = 0; i < 6; i++) {
            int col = i % 2, row = i / 2;
            float bx = px + 24 + col * 306;
            float by = py + 118 - row * 38;
            Ui.fontTiny.setColor(cText);
            Ui.fontTiny.draw(batch, labels[i], bx, by + 4);
            batch.draw(Ui.roundedRect(150, 12, 6, cTrack, GLASS_RIM), bx + 84, by - 4);
            int v = MathUtils.clamp(barVals[i], 0, 100);
            if (v > 0) {
                batch.draw(Ui.roundedRect(Math.max(12, 150 * v / 100), 12, 6, cols[i], cols[i]), bx + 84, by - 4);
            }
            Ui.fontTiny.setColor(cText);
            Ui.fontTiny.draw(batch, st == null ? "—"
                : (i == 0 ? st[0] + " · " + st[1] + "%" : String.valueOf(barVals[i])), bx + 242, by + 4);
        }
    }

    /** Kiểu C — NGUYÊN BẢN GỐC: nền giấy PopupShop, chữ xanh, nền myinfo/back
     *  thật, thanh chỉ số bằng ảnh thanh0/1/2 gốc. */
    private void drawCharPanelOriginal() {
        com.avatardk.game.net.PlayerInfo p = game.player;
        if (p == null) {
            charPanel = false;
            return;
        }
        float[] g = charPanelGeom();
        float px = g[0], py = g[1];
        Color blue = new Color(0.10f, 0.28f, 0.62f, 1f);
        Color brown = new Color(0.45f, 0.32f, 0.18f, 1f);
        batch.setColor(0f, 0f, 0f, 0.40f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) CP_W, (int) CP_H, 14,
            new Color(0.97f, 0.95f, 0.89f, 1f), brown), px, py);
        Ui.font.setColor(brown);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + CP_W - 54, py + CP_H - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + CP_W - 36, py + CP_H - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        int[] st = charStats();
        // tên xanh đậm trên cùng trái (vị trí tab tên của PopupShop gốc)
        Ui.font.setColor(blue);
        Ui.font.draw(batch, p.username, px + 24, py + CP_H - 24);
        // cột tiền icon gốc + dòng cấp
        int[] icons = {0, 1, 4, 2};
        String[] vals = {fmtMoney(p.xu), fmtMoney(p.luong), fmtMoney(p.luongKhoa), fmtMoney(p.xeng)};
        for (int i = 0; i < 4; i++) {
            float ry = py + CP_H - 104 - i * 40;
            batch.draw(game.texture("myinfo/icon" + icons[i] + ".png"), px + 24, ry, 28, 28);
            Ui.fontSmall.setColor(blue);
            Ui.fontSmall.draw(batch, vals[i], px + 64, ry + 20);
        }
        float ry = py + CP_H - 104 - 4 * 40;
        batch.draw(game.texture("myinfo/icon3.png"), px + 24, ry, 28, 28);
        Ui.fontSmall.setColor(blue);
        Ui.fontSmall.draw(batch, st != null ? st[0] + " + " + st[1] + "%" : "—", px + 64, ry + 20);
        // nền sân khấu gốc back.png + doll đứng trên đó
        float backX = px + CP_W - 345 - 14, backY = py + 262;
        batch.draw(game.texture("myinfo/back.png"), backX, backY, 345, 145);
        float floorY = backY + 22;
        boolean hasPartner = partnerDoll != null;
        float selfCx = backX + 105;
        float partCx = backX + 262;
        game.dollRenderer.draw(batch, playerDoll, playerAnim.frameIndex(), selfCx, floorY, 2.6f, false);
        if (hasPartner) {
            game.dollRenderer.draw(batch, partnerDoll, 0, partCx, floorY, 2.6f, true);
            Ui.centered(batch, Ui.fontSmall, partnerRel != null ? partnerRel : "",
                partCx, py + CP_H - 28, new Color(0.80f, 0.25f, 0.45f, 1f));
            Ui.centered(batch, Ui.fontTiny, partnerName + " · " + partnerLv + " + " + partnerPct + "%",
                partCx, backY + 4, blue);
        } else {
            // chưa có người yêu: bóng placeholder như gốc
            batch.setColor(1f, 1f, 1f, 0.55f);
            batch.draw(game.texture("myinfo/shadow.png"), partCx - 26, floorY - 2, 52, 84);
            batch.setColor(Color.WHITE);
        }
        // đường chia nâu + 6 chỉ số bằng ảnh thanh gốc
        batch.setColor(brown);
        batch.draw(whiteTex, px + 24, py + 186, CP_W - 48, 2);
        batch.setColor(Color.WHITE);
        String[] labels = {"Lv:", "Thân thiện", "Gây rối", "Sành điệu", "Vui vẻ", "Sức khỏe"};
        int[] barVals = st == null ? new int[6]
            : new int[]{st[1], st[2], st[3], st[4], st[5], st[6]};
        com.badlogic.gdx.graphics.Texture thanh0 = game.texture("myinfo/thanh0.png");
        com.badlogic.gdx.graphics.Texture thanh1 = game.texture("myinfo/thanh1.png");
        com.badlogic.gdx.graphics.Texture thanh2 = game.texture("myinfo/thanh2.png");
        for (int i = 0; i < 6; i++) {
            int col = i % 2, row = i / 2;
            float bx = px + 24 + col * 306;
            float by = py + 120 - row * 44;
            Ui.fontTiny.setColor(blue);
            Ui.fontTiny.draw(batch, i == 0 && st != null ? "Lv: " + st[0] : labels[i], bx, by + 17);
            batch.draw(thanh2, bx + 84, by, 150, 22);
            int v = MathUtils.clamp(barVals[i], 0, 100);
            if (v > 0) {
                batch.draw(thanh1, bx + 84, by, 150f * v / 100f, 22,
                    0, 0, Math.max(1, (int) (116f * v / 100f)), 25, false, true);
            }
            batch.draw(thanh0, bx + 84, by + 8, 150, 6);
            Ui.fontTiny.setColor(blue);
            Ui.fontTiny.draw(batch, st == null ? "—" : String.valueOf(barVals[i]), bx + 244, by + 17);
        }
    }

    /** Kiểu D — THẺ HIỆN ĐẠI: header avatar tròn + chip tiền, sân khấu rộng,
     *  6 thẻ chỉ số dạng lưới 3×2. */
    private void drawCharPanelCards() {
        com.avatardk.game.net.PlayerInfo p = game.player;
        if (p == null) {
            charPanel = false;
            return;
        }
        float[] g = charPanelGeom();
        float px = g[0], py = g[1];
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) CP_W, (int) CP_H, 18,
            new Color(0.07f, 0.11f, 0.17f, 0.97f), GLASS_RIM), px, py);
        int[] st = charStats();
        // header: avatar tròn + tên + cấp
        float avCx = px + 62, avCy = py + CP_H - 52;
        batch.setColor(0.35f, 0.55f, 0.75f, 0.55f);
        batch.draw(circleTexture(), avCx - 39, avCy - 39, 78, 78);
        batch.setColor(Color.WHITE);
        int[] b = playerDoll.bounds(playerAnim.frameIndex());
        float unit = 4.2f;
        float feetX = avCx - ((b[0] + b[2]) / 2f) * unit;
        // nâng doll lên 14px so với neo đỉnh thuần: tóc Sơn to, neo đỉnh làm
        // vòng tròn toàn tóc đen — dịch lên để mặt vào giữa vòng
        float feetY = avCy + 31 + b[1] * unit + 14;
        Rectangle clip = new Rectangle(avCx - 31, avCy - 31, 62, 62);
        Rectangle scissors = new Rectangle();
        batch.flush();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            game.dollRenderer.draw(batch, playerDoll, playerAnim.frameIndex(), feetX, feetY, unit, false);
            batch.flush();
            ScissorStack.popScissors();
        }
        batch.draw(ringTexture(), avCx - 37, avCy - 37, 74, 74);
        Ui.font.setColor(1f, 0.83f, 0.38f, 1f);
        Ui.font.draw(batch, p.username, px + 116, avCy + 14);
        if (p.gender == 1 || p.gender == 2) {
            Ui.faIcon(batch, Ui.fa20, p.gender == 1 ? Ui.FA_MARS : Ui.FA_VENUS,
                px + 116 + Ui.textWidth(Ui.font, p.username) + 16, avCy + 4,
                new Color(p.gender == 1 ? 0.45f : 1f, p.gender == 1 ? 0.75f : 0.55f,
                    p.gender == 1 ? 1f : 0.75f, 1f));
        }
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.92f);
        Ui.fontSmall.draw(batch, st != null ? "Cấp " + st[0] : "—", px + 116, avCy - 18);
        // phía trên dòng người yêu: ngày vào game (đuôi mở rộng gói −22),
        // canh phải thẳng hàng với dòng người yêu ở dưới
        if (selfJoinDate != null) {
            String jd = "Vào game: " + selfJoinDate;
            layout.setText(Ui.fontSmall, jd);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.78f);
            Ui.fontSmall.draw(batch, jd, px + CP_W - 70 - layout.width, avCy + 14);
        }
        // bên phải hàng cấp: "Người yêu: <tên>" — chưa có thì để trống
        if (partnerName != null) {
            String rel = (partnerRel != null && !partnerRel.isEmpty() ? partnerRel : "Người yêu")
                + ": " + partnerName;
            layout.setText(Ui.fontSmall, rel);
            Ui.fontSmall.setColor(1f, 0.75f, 0.85f, 1f);
            Ui.fontSmall.draw(batch, rel, px + CP_W - 70 - layout.width, avCy - 18);
        }
        // nút đóng: ô vuông đỏ nằm TRONG góc phải trên như panel Bạn bè
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + CP_W - 54, py + CP_H - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + CP_W - 36, py + CP_H - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        // hàng chip tiền (4 chip ngang dưới header)
        int[] icons = {0, 1, 4, 2};
        String[] vals = {fmtMoney(p.xu), fmtMoney(p.luong), fmtMoney(p.luongKhoa), fmtMoney(p.xeng)};
        for (int i = 0; i < 4; i++) {
            float cx = px + 24 + i * 152;
            float cy = py + CP_H - 148;
            batch.draw(Ui.roundedRect(140, 36, 10, new Color(0f, 0f, 0f, 0.35f), GLASS_RIM), cx, cy);
            batch.draw(game.texture("myinfo/icon" + icons[i] + ".png"), cx + 8, cy + 7, 22, 22);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.94f);
            Ui.fontSmall.draw(batch, vals[i], cx + 38, cy + 24);
        }
        // sân khấu rộng
        float stX = px + 24, stY = py + 168, stW = CP_W - 48, stH = 140;
        batch.draw(Ui.roundedRect((int) stW, (int) stH, 14,
            new Color(0.30f, 0.45f, 0.62f, 0.35f), GLASS_RIM), stX, stY);
        float floorY = stY + 26;
        boolean hasPartner = partnerDoll != null;
        float selfCx = hasPartner ? stX + 150 : stX + stW / 2f;
        float partCx = stX + 442;
        batch.setColor(0f, 0f, 0f, 0.30f);
        batch.draw(circleTexture(), selfCx - 48, floorY - 7, 96, 15);
        if (hasPartner) batch.draw(circleTexture(), partCx - 42, floorY - 7, 84, 14);
        batch.setColor(Color.WHITE);
        game.dollRenderer.draw(batch, playerDoll, playerAnim.frameIndex(), selfCx, floorY, 2.5f, false);
        if (hasPartner) {
            game.dollRenderer.draw(batch, partnerDoll, 0, partCx, floorY, 2.5f, true);
            // Giữa hai nhân vật: icon đôi (idImage của gói −22 — panel gốc
            // cũng vẽ icon này ở giữa cặp) + tên nhẫn của đôi ở dưới icon.
            float midX = (selfCx + partCx) / 2f;
            float iconCy = floorY + 82;
            com.avatardk.game.data.ImgInfo ii = partnerImgId > 0
                ? game.data.imgById.get(partnerImgId) : null;
            if (ii != null) {
                Texture tex = game.texture("big/" + ii.bigId + ".png");
                TextureRegion reg = new TextureRegion(tex,
                    ii.x * 2, ii.y * 2, ii.w * 2, ii.h * 2);
                float sc = 38f / Math.max(1, ii.h * 2);
                float dw = ii.w * 2 * sc;
                batch.draw(reg, midX - dw / 2f, iconCy - 19, dw, 38);
            } else {
                Ui.faIcon(batch, Ui.fa30, Ui.FA_HEART, midX, iconCy,
                    new Color(1f, 0.45f, 0.6f, 1f));
            }
            if (partnerSlogan != null && !partnerSlogan.isEmpty()) {
                Ui.centered(batch, Ui.fontTiny, partnerSlogan, midX, iconCy - 36,
                    new Color(1f, 0.8f, 0.85f, 1f));
            }
        }
        // lưới 6 thẻ chỉ số 3×2
        String[] labels = {"Cấp", "Thân thiện", "Gây rối", "Sành điệu", "Vui vẻ", "Sức khỏe"};
        Color[] cols = {new Color(0.55f, 0.85f, 1f, 0.95f), new Color(0.45f, 0.9f, 0.5f, 0.95f),
            new Color(0.9f, 0.45f, 0.6f, 0.95f), new Color(0.6f, 0.72f, 1f, 0.95f),
            new Color(1f, 0.8f, 0.35f, 0.95f), new Color(1f, 0.55f, 0.4f, 0.95f)};
        int[] barVals = st == null ? new int[6]
            : new int[]{st[1], st[2], st[3], st[4], st[5], st[6]};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            float cx = px + 24 + col * 202;
            float cy = py + 88 - row * 72;
            batch.draw(Ui.roundedRect(190, 62, 10, new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), cx, cy);
            Ui.fontTiny.setColor(1f, 1f, 1f, 0.85f);
            Ui.fontTiny.draw(batch, labels[i], cx + 12, cy + 48);
            Ui.fontTiny.setColor(1f, 1f, 1f, 0.95f);
            String vt = st == null ? "—" : (i == 0 ? st[0] + " · " + st[1] + "%" : String.valueOf(barVals[i]));
            layout.setText(Ui.fontTiny, vt);
            Ui.fontTiny.draw(batch, vt, cx + 190 - 12 - layout.width, cy + 48);
            batch.draw(Ui.roundedRect(166, 10, 5, new Color(0f, 0f, 0f, 0.45f), GLASS_RIM), cx + 12, cy + 12);
            int v = MathUtils.clamp(barVals[i], 0, 100);
            if (v > 0) {
                batch.draw(Ui.roundedRect(Math.max(10, 166 * v / 100), 10, 5, cols[i], cols[i]), cx + 12, cy + 12);
            }
        }
    }

    // ===== GIỎ ĐỒ / RƯƠNG ĐỒ (nối thật 2026-11-01, mockup đã duyệt) =====
    // Giỏ: gói −47 (xin/trả danh sách), −48 mặc/cất, −36 xoá — theo
    // MapScr.doStore của gốc. Rương nhà: −87 dữ liệu, −89 chuyển đồ —
    // theo HouseScr.onCustomChest. Panel kính theo mockup đã duyệt:
    // bỏ chip tiền, sức chứa ở cuối hàng tab, cấp rương cạnh tiêu đề.
    /** Một món đồ trong giỏ/rương: part + byte time + chuỗi hạn dùng. */
    private static class InvItem {
        final int partId;
        final int time;
        final String expire;
        InvItem(int partId, int time, String expire) {
            this.partId = partId;
            this.time = time;
            this.expire = expire;
        }
    }

    private boolean bagPanel, chestPanel;
    private final java.util.List<InvItem> bagItems = new ArrayList<>();      // giỏ (gói −47)
    private final java.util.List<InvItem> chestConItems = new ArrayList<>(); // giỏ (gói −87)
    private final java.util.List<InvItem> chestItems = new ArrayList<>();    // rương nhà (gói −87)
    /** Hạn dùng của đồ đang mặc theo partId (từ gói đăng nhập + khi mặc từ giỏ). */
    private final java.util.Map<Integer, String> wornExpire = new java.util.HashMap<>();
    private int bagCapacity = 20, chestCapacity = 20, chestLevel = 1;
    private int bagTab = 0, bagSel = 0;
    /** Panel rương song song: vị trí đang chọn ở phía giỏ / phía rương. */
    private int conSel = 0, homeSel = 0;
    private static final float INV_W = 720, INV_H = 500;
    // Hộp xác nhận dùng chung cho panel đồ (văn bản hỏi đúng của gốc:
    // T.trasContainter + T.doYouWantDel). confirmAction: 1 = dùng,
    // 2 = cất, 3 = xoá, 4 = chuyển giỏ<->rương.
    private String confirmText;
    private int confirmAction, confirmPart, confirmIndex;
    /** Phía nguồn của lần chuyển rương đang chờ xác nhận (0 giỏ, 1 rương). */
    private int confirmSide;

    /** Vẽ icon của một part (theo idIcon gốc) canh giữa tại (cx, cy). */
    private void drawPartIcon(int partId, float cx, float cy, float maxBox) {
        com.avatardk.game.data.PartData pd = game.data.parts.get(partId);
        if (pd == null) return;
        // Part.cs của gốc: part ĐỘNG (id >= 2000) lấy icon từ ảnh riêng
        // theo id icon (atlas iconatlas của bản port), part TĨNH lấy từ
        // imgdata. Trộn hai nguồn này là nguyên nhân icon hiện sai hình
        // (món động vớ phải hình món tĩnh trùng số icon).
        if (pd.isDynamic()) {
            com.avatardk.game.data.ImgInfo di = game.data.dynImgById.get(pd.icon);
            if (di != null) {
                Texture tex = game.texture("iconatlas/" + di.bigId + ".png");
                TextureRegion reg = new TextureRegion(tex, di.x, di.y, di.w, di.h);
                float sc = Math.min(maxBox / Math.max(1, di.w), maxBox / Math.max(1, di.h));
                float dw = di.w * sc, dh = di.h * sc;
                batch.draw(reg, cx - dw / 2f, cy - dh / 2f, dw, dh);
                return;
            }
        }
        com.avatardk.game.data.ImgInfo ii = game.data.imgByItemId.get(pd.icon);
        if (ii == null) return;
        Texture tex = game.texture("big/" + ii.bigId + ".png");
        TextureRegion reg = new TextureRegion(tex, ii.x * 2, ii.y * 2, ii.w * 2, ii.h * 2);
        float sc = Math.min(maxBox / Math.max(1, ii.w * 2), maxBox / Math.max(1, ii.h * 2));
        float dw = ii.w * 2 * sc, dh = ii.h * 2 * sc;
        batch.draw(reg, cx - dw / 2f, cy - dh / 2f, dw, dh);
    }

    /** Danh sách tab đang xem của panel GIỎ: Giỏ đồ (gói −47) hoặc
     *  Đang mặc — suy từ playerParts, lọc zOrder 30/40 như
     *  MapScr.getListYourPart của gốc. (Panel rương không dùng tab nữa:
     *  hai phía giỏ/rương hiện song song, xem drawChestPanels.) */
    private java.util.List<InvItem> invList(boolean chest) {
        if (bagTab == 0) return bagItems;
        java.util.List<InvItem> out = new ArrayList<>();
        for (int id : playerParts) {
            com.avatardk.game.data.PartData pd = game.data.part(id);
            if (pd == null || pd.zorder == 30 || pd.zorder == 40) continue;
            out.add(new InvItem(id, 0, wornExpire.get(id)));
        }
        return out;
    }

    private static InvItem removeInvItem(java.util.List<InvItem> list, int partId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).partId == partId) return list.remove(i);
        }
        return null;
    }

    /** Mở giỏ đồ từ menu: xin danh sách thật (gói −47); server trả về thì
     *  handler mở panel. Offline thì báo rõ thay vì mở panel trống. */
    public void openBag() {
        if (!online()) {
            addChatLog(TAB_SYSTEM, null, "Đăng nhập để dùng giỏ đồ.", true);
            return;
        }
        try {
            AvatarProtocol.requestContainer(game.connection, ownId());
        } catch (IOException e) {
            addChatLog(TAB_SYSTEM, null, "Không mở được giỏ đồ (mất kết nối?).", true);
        }
    }

    /** Mở rương nhà từ menu: xin dữ liệu rương (gói −87). */
    public void openChest() {
        if (!online()) {
            addChatLog(TAB_SYSTEM, null, "Đăng nhập để dùng rương đồ.", true);
            return;
        }
        try {
            AvatarProtocol.requestChest(game.connection);
        } catch (IOException e) {
            addChatLog(TAB_SYSTEM, null, "Không mở được rương đồ (mất kết nối?).", true);
        }
    }

    /** Gói −48 cho chính mình: đảo trạng thái mặc của part, giữ đồng bộ
     *  giỏ / hạn dùng / doll như server đã làm (mặc vào thì món cùng
     *  zOrder đang mặc được trả về giỏ). */
    private void onUsingPartSelf(int partId) {
        int at = playerParts.indexOf(partId);
        if (at >= 0) {
            // đang mặc -> cất vào giỏ
            playerParts.remove(at);
            String exp = wornExpire.remove(partId);
            bagItems.add(new InvItem(partId, 0, exp));
            chestConItems.add(new InvItem(partId, 0, exp));
        } else {
            // mặc vào: lấy hạn dùng từ bản ghi trong giỏ (nếu có)
            InvItem fromBag = removeInvItem(bagItems, partId);
            removeInvItem(chestConItems, partId);
            String exp = fromBag != null ? fromBag.expire : null;
            com.avatardk.game.data.PartData np = game.data.part(partId);
            if (np != null) {
                for (int i = 0; i < playerParts.size(); i++) {
                    com.avatardk.game.data.PartData op = game.data.part(playerParts.get(i));
                    if (op != null && op.id != partId && op.zorder == np.zorder) {
                        int old = playerParts.remove(i);
                        String oe = wornExpire.remove(old);
                        bagItems.add(new InvItem(old, 0, oe));
                        chestConItems.add(new InvItem(old, 0, oe));
                        break;
                    }
                }
            }
            playerParts.add(partId);
            if (exp != null) wornExpire.put(partId, exp);
        }
        playerDoll = game.newDoll(playerParts);
    }

    /** Gói −48 cho người chơi khác: đảo trạng thái mặc trên doll của họ. */
    private void toggleRemotePart(RemoteFigure f, int partId) {
        java.util.List<Integer> parts = new ArrayList<>();
        for (int id : f.info.wearing) parts.add(id);
        if (parts.contains(partId)) {
            parts.remove((Integer) partId);
        } else {
            com.avatardk.game.data.PartData np = game.data.part(partId);
            if (np != null) {
                for (int i = 0; i < parts.size(); i++) {
                    com.avatardk.game.data.PartData op = game.data.part(parts.get(i));
                    if (op != null && op.id != partId && op.zorder == np.zorder) {
                        parts.remove(i);
                        break;
                    }
                }
            }
            parts.add(partId);
        }
        f.info.wearing = parts.stream().mapToInt(Integer::intValue).toArray();
        f.doll = game.newDoll(parts);
    }

    /** Gói −36 cho người chơi khác: tháo part khỏi doll của họ. */
    private void removeRemotePart(RemoteFigure f, int partId) {
        java.util.List<Integer> parts = new ArrayList<>();
        boolean changed = false;
        for (int id : f.info.wearing) {
            if (id != partId) parts.add(id); else changed = true;
        }
        if (changed) {
            f.info.wearing = parts.stream().mapToInt(Integer::intValue).toArray();
            f.doll = game.newDoll(parts);
        }
    }

    /** Gói −89 trả về thành công: chuyển món đã xác nhận (confirmSide +
     *  confirmIndex) sang phía bên kia, chọn theo món vừa chuyển. */
    private void moveChestSelection() {
        if (confirmSide == 0) {
            if (confirmIndex < 0 || confirmIndex >= chestConItems.size()) return;
            InvItem it = chestConItems.remove(confirmIndex);
            chestItems.add(it);
            removeInvItem(bagItems, it.partId);
            homeSel = chestItems.size() - 1;
            conSel = Math.min(confirmIndex, Math.max(0, chestConItems.size() - 1));
        } else {
            if (confirmIndex < 0 || confirmIndex >= chestItems.size()) return;
            InvItem it = chestItems.remove(confirmIndex);
            chestConItems.add(it);
            bagItems.add(new InvItem(it.partId, it.time, it.expire));
            conSel = chestConItems.size() - 1;
            homeSel = Math.min(confirmIndex, Math.max(0, chestItems.size() - 1));
        }
    }

    /** Nhãn nút hành động của panel giỏ theo tab đang xem. */
    private String[] invActions(boolean chest) {
        return bagTab == 0 ? new String[]{"Dùng", "Xoá"} : new String[]{"Cất đồ", "Xoá"};
    }

    /** Chạm vào nút hành động: mở hộp xác nhận đúng câu hỏi của gốc.
     *  side chỉ dùng cho rương: 0 = nút ở panel Giỏ, 1 = ở panel Rương. */
    private void invActionTapped(boolean chest, int btn, int side) {
        if (chest) {
            java.util.List<InvItem> items = side == 0 ? chestConItems : chestItems;
            int sel = side == 0 ? conSel : homeSel;
            if (sel < 0 || sel >= items.size()) return;
            confirmAction = 4;
            confirmSide = side;
            confirmPart = items.get(sel).partId;
            confirmIndex = sel;
            confirmText = side == 0 ? "Bỏ vào rương ?" : "Bỏ vào giỏ đồ ?";
            return;
        }
        java.util.List<InvItem> items = invList(false);
        int sel = bagSel;
        if (sel < 0 || sel >= items.size()) return;
        InvItem it = items.get(sel);
        confirmPart = it.partId;
        confirmIndex = sel;
        if (btn == 1) {
            confirmAction = 3;
            confirmText = "Bỏ món đồ này ?";
        } else if (bagTab == 0) {
            confirmAction = 1;
            confirmText = "Sử dụng vật phẩm này ?";
        } else {
            confirmAction = 2;
            confirmText = "Bỏ món đồ này vào giỏ ?";
        }
    }

    /** Đồng ý ở hộp xác nhận: gửi gói tương ứng. Xoá thì gỡ khỏi danh
     *  sách ngay như client gốc; mặc/cất chờ gói −48 server xác nhận;
     *  chuyển rương chờ gói −89. */
    private void invConfirmYes() {
        int action = confirmAction, part = confirmPart, index = confirmIndex;
        confirmText = null;
        if (!online()) return;
        try {
            switch (action) {
                case 1:
                    AvatarProtocol.usingPart(game.connection, part, 1);
                    break;
                case 2:
                    AvatarProtocol.usingPart(game.connection, part, 0);
                    break;
                case 3: {
                    boolean worn = bagTab == 1;
                    AvatarProtocol.removeItem(game.connection, part, worn ? 0 : 1);
                    if (worn) {
                        if (playerParts.remove((Integer) part)) {
                            wornExpire.remove(part);
                            playerDoll = game.newDoll(playerParts);
                        }
                    } else {
                        removeInvItem(bagItems, part);
                        removeInvItem(chestConItems, part);
                    }
                    if (bagSel > 0 && bagSel >= invList(false).size()) bagSel--;
                    break;
                }
                case 4:
                    AvatarProtocol.transChest(game.connection, confirmSide, index, part);
                    break;
                default:
                    break;
            }
        } catch (IOException e) {
            addChatLog(TAB_SYSTEM, null, "Không gửi được yêu cầu (mất kết nối?).", true);
        }
    }

    private void drawInvPanel(boolean chest) {
        java.util.List<InvItem> items = invList(false);
        int sel = bagSel;
        if (sel >= items.size()) sel = Math.max(0, items.size() - 1);
        bagSel = sel;
        float px = (Ui.W - INV_W) / 2f, py = (Ui.H - INV_H) / 2f;
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) INV_W, (int) INV_H, 18,
            new Color(0.07f, 0.11f, 0.17f, 0.97f), GLASS_RIM), px, py);
        // tiêu đề + nút đóng tròn trên viền góc (như panel nhân vật)
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Giỏ đồ", px + 28, py + INV_H - 24);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + INV_W - 54, py + INV_H - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + INV_W - 36, py + INV_H - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        // tabs; cuối hàng tab là sức chứa của phía đang xem
        String[] tabs = {"Giỏ đồ", "Đang mặc"};
        float tabY = py + INV_H - 108;
        for (int i = 0; i < 2; i++) {
            float tx = px + 28 + i * 162;
            boolean act = i == bagTab;
            batch.draw(Ui.roundedRect(150, 38, 12,
                act ? new Color(0.25f, 0.45f, 0.65f, 0.95f) : new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), tx, tabY);
            Ui.centered(batch, Ui.fontSmall, tabs[i], tx + 75, tabY + 25,
                act ? Color.WHITE : new Color(1f, 1f, 1f, 0.65f));
        }
        String cap = bagTab == 1
            ? "Đang mặc: " + items.size() + " món"
            : "Sức chứa: " + items.size() + "/" + bagCapacity + " ô";
        layout.setText(Ui.fontSmall, cap);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
        Ui.fontSmall.draw(batch, cap, px + INV_W - 28 - layout.width, tabY + 25);
        // lưới ô đồ 7 × 3
        float gridTop = py + INV_H - 128;
        int cols = 7, cell = 84, gap = 10;
        for (int idx = 0; idx < cols * 3; idx++) {
            int col = idx % cols, row = idx / cols;
            float cx = px + 28 + col * (cell + gap);
            float cy = gridTop - (row + 1) * cell - row * gap;
            boolean has = idx < items.size();
            boolean isSel = has && idx == sel;
            batch.draw(Ui.roundedRect(cell, cell, 12,
                has ? new Color(0.16f, 0.24f, 0.34f, 0.9f) : new Color(0f, 0f, 0f, 0.18f),
                isSel ? new Color(1f, 0.78f, 0.30f, 1f) : GLASS_RIM), cx, cy);
            if (has) drawPartIcon(items.get(idx).partId, cx + cell / 2f, cy + cell / 2f, 58);
        }
        // thanh dưới: món đang chọn + nút hành động
        float by = py + 22;
        batch.draw(Ui.roundedRect((int) INV_W - 56, 66, 12, new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), px + 28, by);
        if (items.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall, "Chưa có món nào", px + INV_W / 2f, by + 41,
                new Color(1f, 1f, 1f, 0.55f));
        } else {
            InvItem cur = items.get(sel);
            drawPartIcon(cur.partId, px + 66, by + 33, 44);
            com.avatardk.game.data.PartData pd = game.data.part(cur.partId);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.96f);
            Ui.fontSmall.draw(batch, pd != null ? pd.name : ("Vật phẩm #" + cur.partId), px + 100, by + 44);
            Ui.fontTiny.setColor(1f, 1f, 1f, 0.72f);
            String exp = cur.expire == null || cur.expire.isEmpty() ? "Vĩnh viễn" : cur.expire;
            Ui.fontTiny.draw(batch, "Hạn dùng: " + exp, px + 100, by + 20);
            String[] acts = invActions(false);
            for (int i = 0; i < acts.length; i++) {
                float bx = px + INV_W - 28 - (acts.length - i) * 132 + 12;
                if (i == 0) {
                    batch.draw(Ui.gradientRect(120, 40, 10,
                        EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, by + 13);
                } else {
                    batch.draw(Ui.roundedRect(120, 40, 10, Ui.BTN_CANCEL, GLASS_RIM),
                        bx, by + 13);
                }
                Ui.centered(batch, Ui.fontSmall, acts[i], bx + 60, by + 39, Color.WHITE);
            }
        }
        if (confirmText != null) drawInvConfirm();
    }

    // ===== RƯƠNG: HAI PANEL SONG SONG, KHÔNG TAB (chủ dự án chốt
    // 2026-11-01 thay cho bản 2 tab) — trái Giỏ đồ, phải Rương đồ; mỗi
    // phía chọn món của mình rồi bấm Di chuyển sang phía bên kia. =====
    private static final float CH_PW = 604, CH_PH = 500, CH_GAP = 16;
    private static final float CH_X0 = (Ui.W - (CH_PW * 2 + CH_GAP)) / 2f;
    private static final float CH_PY = (Ui.H - CH_PH) / 2f;

    private void drawChestPanels() {
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        drawChestSide(CH_X0, 0);
        drawChestSide(CH_X0 + CH_PW + CH_GAP, 1);
        // nút đóng tròn trên viền góc của panel phải
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), CH_X0 + CH_PW * 2 + CH_GAP - 54, CH_PY + CH_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, CH_X0 + CH_PW * 2 + CH_GAP - 36, CH_PY + CH_PH - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        if (confirmText != null) drawInvConfirm();
    }

    /** Một phía của panel rương song song: side 0 = Giỏ đồ, 1 = Rương đồ. */
    private void drawChestSide(float px, int side) {
        java.util.List<InvItem> items = side == 0 ? chestConItems : chestItems;
        int sel = side == 0 ? conSel : homeSel;
        if (sel >= items.size()) sel = Math.max(0, items.size() - 1);
        if (side == 0) conSel = sel; else homeSel = sel;
        float py = CH_PY;
        batch.draw(Ui.roundedRect((int) CH_PW, (int) CH_PH, 18,
            new Color(0.07f, 0.11f, 0.17f, 0.97f), GLASS_RIM), px, py);
        // hàng tiêu đề: tên + (rương: huy hiệu cấp) + sức chứa canh phải
        String title = side == 0 ? "Giỏ đồ" : "Rương đồ";
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, title, px + 28, py + CH_PH - 24);
        if (side == 1) {
            String badge = "Cấp " + chestLevel;
            float bw = Ui.textWidth(Ui.fontSmall, badge) + 30;
            float tw = Ui.textWidth(Ui.font, title);
            float bx = px + 28 + tw + 16, byy = py + CH_PH - 48;
            batch.draw(Ui.roundedRect((int) bw, 32, 10, new Color(0.95f, 0.55f, 0.20f, 0.9f), GLASS_RIM), bx, byy);
            Ui.centered(batch, Ui.fontSmall, badge, bx + bw / 2f, byy + 22, Color.WHITE);
        }
        String cap = "Sức chứa: " + items.size() + "/"
            + (side == 0 ? bagCapacity : chestCapacity) + " ô";
        layout.setText(Ui.fontSmall, cap);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
        Ui.fontSmall.draw(batch, cap, px + CH_PW - 70 - layout.width, py + CH_PH - 24);
        // lưới ô đồ 6 × 3
        float gridTop = py + CH_PH - 128;
        int cols = 6, cell = 84, gap = 8;
        float gridX = px + (CH_PW - (cols * cell + (cols - 1) * gap)) / 2f;
        for (int idx = 0; idx < cols * 3; idx++) {
            int col = idx % cols, row = idx / cols;
            float cx = gridX + col * (cell + gap);
            float cy = gridTop - (row + 1) * cell - row * gap;
            boolean has = idx < items.size();
            boolean isSel = has && idx == sel;
            batch.draw(Ui.roundedRect(cell, cell, 12,
                has ? new Color(0.16f, 0.24f, 0.34f, 0.9f) : new Color(0f, 0f, 0f, 0.18f),
                isSel ? new Color(1f, 0.78f, 0.30f, 1f) : GLASS_RIM), cx, cy);
            if (has) drawPartIcon(items.get(idx).partId, cx + cell / 2f, cy + cell / 2f, 58);
        }
        // thanh dưới: món đang chọn + nút Di chuyển (mũi tên chỉ hướng sang panel kia)
        float by = py + 22;
        batch.draw(Ui.roundedRect((int) CH_PW - 56, 66, 12, new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), px + 28, by);
        if (items.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall, "Chưa có món nào", px + CH_PW / 2f, by + 41,
                new Color(1f, 1f, 1f, 0.55f));
            return;
        }
        InvItem cur = items.get(sel);
        drawPartIcon(cur.partId, px + 66, by + 33, 44);
        com.avatardk.game.data.PartData pd = game.data.part(cur.partId);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.96f);
        Ui.fontSmall.draw(batch, pd != null ? pd.name : ("Vật phẩm #" + cur.partId), px + 100, by + 44);
        Ui.fontTiny.setColor(1f, 1f, 1f, 0.72f);
        String exp = cur.expire == null || cur.expire.isEmpty() ? "Vĩnh viễn" : cur.expire;
        Ui.fontTiny.draw(batch, "Hạn dùng: " + exp, px + 100, by + 20);
        float bx = px + CH_PW - 28 - 132 + 12;
        batch.draw(Ui.gradientRect(120, 40, 10, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, by + 13);
        String label = "Di chuyển";
        float lw = Ui.textWidth(Ui.fontSmall, label);
        Ui.fontSmall.setColor(Color.WHITE);
        Ui.fontSmall.draw(batch, label, bx + 60 - lw / 2f - 8, by + 39);
        Ui.faIcon(batch, Ui.fa20, side == 0 ? Ui.FA_ARROW_RIGHT : Ui.FA_ARROW_LEFT,
            bx + 60 + lw / 2f + 6, by + 33, Color.WHITE);
    }

    /** Hộp xác nhận nhỏ giữa màn hình (Đồng ý / Huỷ). */
    private void drawInvConfirm() {
        float pw = 480, ph = 176, px = (Ui.W - pw) / 2f, py = (Ui.H - ph) / 2f;
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 16,
            new Color(0.08f, 0.13f, 0.20f, 0.98f), GLASS_RIM), px, py);
        Ui.centered(batch, Ui.font, confirmText, px + pw / 2f, py + ph - 42,
            new Color(1f, 0.85f, 0.38f, 1f));
        batch.draw(Ui.gradientRect(170, 48, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
            px + 40, py + 26);
        Ui.centered(batch, Ui.fontSmall, "Đồng ý", px + 40 + 85, py + 57, Color.WHITE);
        batch.draw(Ui.roundedRect(170, 48, 12, Ui.BTN_CANCEL, GLASS_RIM),
            px + pw - 40 - 170, py + 26);
        Ui.centered(batch, Ui.fontSmall, "Huỷ", px + pw - 40 - 85, py + 57, Color.WHITE);
    }

    /** Chạm khi hộp xác nhận đang mở: trả true nếu đã nuốt chạm. */
    private boolean invConfirmTouch(float x, float y) {
        if (confirmText == null) return false;
        float pw = 480, ph = 176, cx0 = (Ui.W - pw) / 2f, cy0 = (Ui.H - ph) / 2f;
        if (x >= cx0 + 40 && x <= cx0 + 210 && y >= cy0 + 26 && y <= cy0 + 74) {
            invConfirmYes();
        } else if (x >= cx0 + pw - 210 && x <= cx0 + pw - 40 && y >= cy0 + 26 && y <= cy0 + 74) {
            confirmText = null;
        }
        return true;
    }

    /** Xử lý chạm cho panel GIỎ (2 tab). Toạ độ khớp drawInvPanel. */
    private void invTouch(float x, float y) {
        float px = (Ui.W - INV_W) / 2f, py = (Ui.H - INV_H) / 2f;
        if (invConfirmTouch(x, y)) return;
        // nút đóng: ô vuông trong góc phải trên
        if (x >= px + INV_W - 54 && x <= px + INV_W - 18
                && y >= py + INV_H - 54 && y <= py + INV_H - 18) {
            bagPanel = false;
            return;
        }
        if (x < px || x > px + INV_W || y < py || y > py + INV_H) {
            bagPanel = false;
            return;
        }
        // tabs
        float tabY = py + INV_H - 108;
        for (int i = 0; i < 2; i++) {
            float tx = px + 28 + i * 162;
            if (x >= tx && x <= tx + 150 && y >= tabY && y <= tabY + 38) {
                bagTab = i;
                bagSel = 0;
                return;
            }
        }
        // lưới ô
        java.util.List<InvItem> items = invList(false);
        float gridTop = py + INV_H - 128;
        int cols = 7, cell = 84, gap = 10;
        for (int idx = 0; idx < cols * 3; idx++) {
            int col = idx % cols, row = idx / cols;
            float cx = px + 28 + col * (cell + gap);
            float cy = gridTop - (row + 1) * cell - row * gap;
            if (x >= cx && x <= cx + cell && y >= cy && y <= cy + cell) {
                if (idx < items.size()) bagSel = idx;
                return;
            }
        }
        // nút hành động ở thanh dưới
        if (!items.isEmpty() && bagSel < items.size()) {
            String[] acts = invActions(false);
            float by = py + 22;
            for (int i = 0; i < acts.length; i++) {
                float bx = px + INV_W - 28 - (acts.length - i) * 132 + 12;
                if (x >= bx && x <= bx + 120 && y >= by + 13 && y <= by + 53) {
                    invActionTapped(false, i, 0);
                    return;
                }
            }
        }
    }

    /** Xử lý chạm cho panel RƯƠNG song song. Toạ độ khớp drawChestPanels. */
    private void chestTouch(float x, float y) {
        if (invConfirmTouch(x, y)) return;
        float py = CH_PY;
        // nút đóng: ô vuông trong góc phải trên của panel phải
        if (x >= CH_X0 + CH_PW * 2 + CH_GAP - 54 && x <= CH_X0 + CH_PW * 2 + CH_GAP - 18
                && y >= py + CH_PH - 54 && y <= py + CH_PH - 18) {
            chestPanel = false;
            return;
        }
        boolean inside = false;
        for (int side = 0; side < 2; side++) {
            float px = CH_X0 + side * (CH_PW + CH_GAP);
            if (x < px || x > px + CH_PW || y < py || y > py + CH_PH) continue;
            inside = true;
            java.util.List<InvItem> items = side == 0 ? chestConItems : chestItems;
            int sel = side == 0 ? conSel : homeSel;
            // lưới ô 6 × 3
            float gridTop = py + CH_PH - 128;
            int cols = 6, cell = 84, gap = 8;
            float gridX = px + (CH_PW - (cols * cell + (cols - 1) * gap)) / 2f;
            for (int idx = 0; idx < cols * 3; idx++) {
                int col = idx % cols, row = idx / cols;
                float cx = gridX + col * (cell + gap);
                float cy = gridTop - (row + 1) * cell - row * gap;
                if (x >= cx && x <= cx + cell && y >= cy && y <= cy + cell) {
                    if (idx < items.size()) {
                        if (side == 0) conSel = idx; else homeSel = idx;
                    }
                    return;
                }
            }
            // nút Di chuyển ở thanh dưới của phía này
            if (!items.isEmpty() && sel < items.size()) {
                float by = py + 22;
                float bx = px + CH_PW - 28 - 132 + 12;
                if (x >= bx && x <= bx + 120 && y >= by + 13 && y <= by + 53) {
                    invActionTapped(true, 0, side);
                    return;
                }
            }
            return; // trong panel: nuốt chạm
        }
        if (!inside) chestPanel = false;
    }



    /** Sân khấu kiểm chứng: nạp bộ đồ mẫu vào cấu trúc thật rồi mở giỏ. */
    public void debugOpenBag() {
        int[] ids = {701, 693, 314, 93, 109, 98, 685, 670, 291, 126, 627, 108};
        String[] exps = {"Còn 30 ngày", "Còn 30 ngày", "Còn 30 ngày", "Còn 30 ngày",
            "Vĩnh viễn", "Còn 30 ngày", "Vĩnh viễn", "Vĩnh viễn", "Vĩnh viễn",
            "Vĩnh viễn", "Còn 30 ngày", "Vĩnh viễn"};
        bagItems.clear();
        for (int i = 0; i < ids.length; i++) bagItems.add(new InvItem(ids[i], 0, exps[i]));
        bagCapacity = 20;
        bagTab = 0;
        bagSel = 0;
        charPanel = false;
        chestPanel = false;
        bagPanel = true;
    }

    /** Sân khấu kiểm chứng: tab Đang mặc của giỏ (đồ mẫu Sơn). */
    public void debugOpenBagWearing() {
        debugOpenBag();
        bagTab = 1;
        bagSel = 0;
    }

    /** Sân khấu kiểm chứng: nạp bộ đồ mẫu rồi mở rương song song,
     *  phía rương đang chọn sẵn món thiên thần như mockup đã duyệt. */
    public void debugOpenChest() {
        int[] con = {701, 693, 314, 93};
        int[] ids = {98, 494, 116, 605, 633, 620, 476, 114, 128};
        String[] exps = {"Còn 30 ngày", "Còn 30 ngày", "Vĩnh viễn", "Còn 30 ngày",
            "Còn 30 ngày", "Vĩnh viễn", "Vĩnh viễn", "Vĩnh viễn", "Vĩnh viễn"};
        chestConItems.clear();
        for (int id : con) chestConItems.add(new InvItem(id, 0, "Vĩnh viễn"));
        chestItems.clear();
        for (int i = 0; i < ids.length; i++) chestItems.add(new InvItem(ids[i], 0, exps[i]));
        chestLevel = 3;
        bagCapacity = 20;
        chestCapacity = 30;
        conSel = 0;
        homeSel = 5;
        bagPanel = false;
        chestPanel = true;
    }

    /** Sân khấu kiểm chứng: hộp xác nhận chuyển đồ từ phía giỏ. */
    public void debugChestConfirm() {
        debugOpenChest();
        conSel = 0;
        invActionTapped(true, 0, 0);
    }


    /** Sân khấu chụp kiểm chứng: mở panel nhân vật. */
    public void debugOpenCharPanel() {
        openCharPanel();
    }

    /** Sân khấu kiểm chứng: đặt kiểu panel nhân vật (0–3). */
    public void debugSetCharVariant(int v) {
        charVariant = v;
    }

    public void debugOpenPlayerPanel() {
        if (!remotes.isEmpty()) {
            panelPlayer = remotes.values().iterator().next();
            panelSub = false;
        }
    }
    public void debugPanelSub(boolean open) { panelSub = open; }
    public int debugPanelPlayerId() { return panelPlayer != null ? panelPlayer.info.id : -1; }

    /** Điểm chân (medium, hệ trên–trái) có đứng được không. Hai lớp như
     *  gốc: (1) type ô — di chuyển tự do chỉ đi được type 80/51
     *  (LoadMap.isTrans trong Base.detectCollision), còn lại là nền
     *  cấm, mặt tiền shop, hàng rào, mặt đường…; (2) ô chân vật thể
     *  gắn map. Ngoài lưới = không đứng được. */
    public boolean canStand(float mx, float my) {
        if (mapDef == null || mapDef.gridW <= 0) return true;
        // Hồ cá ở farm (ô type 88, cột 36–40): nhân vật chỉ đứng trên bờ,
        // TUYỆT ĐỐI không đi xuống nước — chặn cứng theo hình chữ nhật hồ
        // (tọa độ medium từ đáy map) phòng khi lớp type lệch hệ tọa độ.
        if (mapDef.id == 25 && mx > 858f && mx < 990f && my > 20f && my < 150f) return false;
        int cx = (int) (mx / 24), cy = (int) (my / 24);
        if (cx < 0 || cy < 0 || cx >= mapDef.gridW || cy >= mapDef.gridH) return false;
        int idx = cy * mapDef.gridW + cx;
        if (mapDef.types != null) {
            int t = mapDef.types[idx];
            if (t != 80 && t != 51) return false;
        }
        return mapDef.blocked == null || !mapDef.blocked[idx];
    }

    private void update(float dt) {
        if (boardLatch > 0f) boardLatch -= dt;
        if (portalLatch > 0f) portalLatch -= dt;
        // cổng cửa hàng farm ở farmway (type 52): đã vào farm + có dữ liệu
        // thì mở panel cửa hàng như gốc (FarmScr.doOpenCuaHang)
        if (portalFarmShopPending && farmIn && farm != null && farm.joined) {
            portalFarmShopPending = false;
            farmShopOpen = true;
            farmShopTab = 0;
        }
        // hiệu ứng Đánh/Hôn: chạy 1,4 giây rồi tự dọn
        for (int i = actFx.size() - 1; i >= 0; i--) {
            ActFx fx = actFx.get(i);
            fx.t += dt;
            if (fx.t > 1.4f) actFx.remove(i);
        }
        // nghe thoại: luồng phát chạy nền, phát xong thì tự dọn trạng thái
        if (playingLine != null && playThread != null && !playThread.isAlive()) {
            playingLine.voicePlaying = false;
            playingLine = null;
            playThread = null;
        }
        // ghi âm đủ 30 giây thì tự dừng và gửi
        if (recording && recCount >= recBuf.length - 1600) finishRecording();
        // chiều cao panel chat chạy mượt tới nấc đích khi bấm phóng to/thu nhỏ
        float chatTarget = chatExpanded ? CHAT_H_BIG : CHAT_H;
        chatH += (chatTarget - chatH) * Math.min(1f, dt * 12f);
        if (Math.abs(chatTarget - chatH) < 0.6f) chatH = chatTarget;
        // sheet biểu cảm: chiều cao chạy mượt về nấc đích (trừ lúc đang kéo tay cầm)
        if (feelPointer < 0 || !feelDragHandle) {
            feelH += (feelHTarget - feelH) * Math.min(1f, dt * 14f);
            if (Math.abs(feelHTarget - feelH) < 0.6f) feelH = feelHTarget;
        }
        feelScroll = MathUtils.clamp(feelScroll, 0f, feelMaxScroll());
        // (nhạc nền do MusicService tự chạy ở thread riêng — panel đóng
        // vẫn phát tiếp, hết bài tự chuyển theo hàng đợi)
        for (Cloud c : clouds) {
            c.x100 += c.speed * dt;
            if (c.x100 / 100f > 3200f) c.x100 = -40000f;
        }
        float mx = 0f, my = 0f; // medium, hệ trên–trái: my > 0 = đi xuống màn hình
        if (!typing) {
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mx -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mx += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) my -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) my += 1;
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) loadMap(findMap(10));
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) loadMap(findMap(24));
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) loadMap(findMap(25));
        }
        if (joyPointer >= 0) { mx += joyVecX; my -= joyVecY; }
        updateBus(dt);
        // đang ngồi trên xe buýt vào trạm: khóa di chuyển cho tới khi xe dừng
        if (busPhase == 1) { mx = 0f; my = 0f; }
        float len = (float) Math.hypot(mx, my);
        boolean moving = len > 0.01f;
        if (moving) {
            mx /= Math.max(1f, len); my /= Math.max(1f, len);
            // di chuyển tách trục + va chạm: trục nào đụng vật thể thì
            // dừng trục đó, trục còn lại trượt dọc theo vật (đúng cảm
            // giác game gốc); nhân vật không đi xuyên/lên vật thể.
            float nx = MathUtils.clamp(playerX + mx * SPEED * dt, 16,
                worldW / AvatarDkGame.HD - 16);
            if (canStand(nx, playerY)) playerX = nx;
            else tryPortal(nx, playerY);
            float ny = MathUtils.clamp(playerY + my * SPEED * dt, 30, 190);
            if (canStand(playerX, ny)) playerY = ny;
            else tryPortal(playerX, ny);
            if (Math.abs(mx) > 0.05f) playerAnim.facingLeft = mx < 0;
            // di chuyển huỷ mọi tư thế hành động (đúng gốc)
            playerPose = 0;
            playerJumpT = -1f;
            playerAnim.action = AvatarAnimator.RUN;
        } else if (playerJumpT >= 0f) {
            playerJumpT += dt / 0.5f;
            if (playerJumpT >= 1f) {
                playerJumpT = -1f;
                playerAnim.action = AvatarAnimator.STAND;
            } else {
                playerAnim.action = 10;
            }
        } else if (playerPose != 0) {
            playerAnim.action = playerPose;
        } else {
            playerAnim.action = AvatarAnimator.STAND;
        }
        playerAnim.update(dt);
        updateNet(dt);
        updateRemotes(dt);
        updateEdgeTransitions(dt);
        for (NpcFigure f : npcFigures) {
            f.anim.update(dt);
            if (f.bubbleTimer > 0) { f.bubbleTimer -= dt; if (f.bubbleTimer <= 0) f.bubble = null; }
        }
        nearestNpc = findNearestNpc();
        if (!typing && (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE))) {
            interact();
        }
        if (chatTimer > 0) { chatTimer -= dt; if (chatTimer <= 0) chatBubble = null; }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (!typing) { typing = true; typeBuffer.setLength(0); }
            else sendChat();
        }
        if (typing && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) typing = false;
        // ESC ngoài lúc gõ: đóng panel nhân vật trước, rồi thoát nhắn
        // riêng, rồi tới đóng panel người chơi
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (confirmText != null) {
                confirmText = null;
            } else if (bagPanel || chestPanel) {
                bagPanel = false;
                chestPanel = false;
            } else if (charPanel) {
                charPanel = false;
            } else if (privateTargetId != 0) {
                privateTargetId = 0;
                privateTargetName = null;
                addChatLog(TAB_SYSTEM, null, "Đã thoát nhắn riêng.", true);
            } else if (panelPlayer != null) {
                panelPlayer = null;
                panelSub = false;
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        hudViewport.update(width, height, true);
    }

    @Override
    public void render(float dt) {
        update(dt);
        updateFarmFocus();
        // Đặt lại glViewport về đúng backbuffer mỗi frame: FitViewport của HUD ở frame
        // trước có thể đã thu viewport (đặc biệt khi resize chưa được chuyển tiếp).
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        // Map không nền: gốc tô phẳng màu tối (setColor(1)) thay cho màu trời.
        Color sky = mapDef.bg == null ? NO_BG_FILL : SKY_COLOR[bgStatus];
        Gdx.gl.glClearColor(sky.r, sky.g, sky.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float playerFeetX = playerX * AvatarDkGame.HD;
        float playerFeetY = mapBottomPx - playerY * AvatarDkGame.HD;
        // camera bám người chơi, kẹp biên thế giới; lúc xe buýt đang vào trạm
        // thì camera nhảy tới điểm trạm như AvCamera của gốc (Bus.setBus)
        float focusX = (busPhase == 1 && mapDef.stopX >= 0)
            ? mapDef.stopX * AvatarDkGame.HD : playerFeetX;
        float camX = MathUtils.clamp(focusX, Ui.W / 2f, Math.max(Ui.W / 2f, worldW - Ui.W / 2f));
        // Camera dọc theo AvCamera của gốc: mặc định NEO ĐÁY map vào đáy màn hình
        // (yTo = Hmap*w - hCan). Ngoại lệ căn giữa dọc: TYPEMAP 57/58/59 (client
        // 58/59/60) trong AvCamera.init và các map không nền (bg = -1) trong setLimit.
        float camY;
        if (worldH <= Ui.H) {
            // neo đáy: camY = H/2 (đáy map ở đáy màn hình); căn giữa: camY = tâm map
            camY = centerVertically() ? worldH / 2f : Ui.H / 2f;
        } else {
            camY = MathUtils.clamp(playerFeetY + 120, Ui.H / 2f, worldH - Ui.H / 2f);
        }
        // Xe buýt đang chạy (sự kiện đến nơi): NÂNG khung hình lên khỏi khung
        // chat để thấy trọn xe + biển trạm + người chơi lúc xuống xe — khung
        // chat luôn hiện của mình sẽ che mất làn đường ở đáy map nếu giữ
        // nguyên camera neo đáy. Mọi map có trạm đều thấp (Hmap <= 8) nên
        // chỉ áp dụng ở nhánh neo đáy; xe rời trạm xong camera tự trả về.
        float busLift = 0f;
        if (busPhase > 0 && worldH <= Ui.H && !centerVertically()) {
            busLift = chatH + 20f;
            camY -= busLift; // hạ tâm camera = thế giới nhô lên trên màn hình
        }
        if (worldW <= Ui.W) camX = worldW / 2f;
        lastCamX = camX;
        lastCamY = camY;
        worldCam.position.set(camX, camY, 0);
        worldCam.update();

        drawBackground(camX, camY);

        batch.setProjectionMatrix(worldCam.combined);
        batch.begin();
        // dải tối dưới đáy map khi camera nâng vì xe buýt (che khoảng trống
        // dưới đường vừa lộ ra, cùng tông hai cột đen hai bên của gốc)
        if (busLift > 0 && whiteTex != null) {
            batch.setColor(0.03f, 0.04f, 0.05f, 1f);
            batch.draw(whiteTex, -60f, -busLift, worldW + 120f, busLift);
            batch.setColor(Color.WHITE);
        }
        for (int i = 0; i < strips.size(); i++) {
            Texture t = strips.get(i);
            batch.draw(t, stripLefts.get(i), 0);
        }
        if (mapDef.tileMode() && tileRegions != null) {
            // paintMap của gốc: mỗi ô vẽ frame drawTiles[idx] tại
            // (cột*48, hàng*48) từ góc trên-trái map; ở thang GL (y lên)
            // hàng r nằm tại y = đáy map - (r+1)*48. Ô -1 không vẽ.
            int gw = mapDef.gridW;
            int cell = 24 * AvatarDkGame.HD;
            byte[] dTiles = mapDef.drawTiles;
            for (int idx = 0; idx < dTiles.length; idx++) {
                int v = dTiles[idx] & 0xFF;
                if (v == 255) continue;
                com.badlogic.gdx.graphics.g2d.TextureRegion r = tileRegions[v];
                if (r == null) continue;
                batch.draw(r, (idx % gw) * cell, mapBottomPx - (idx / gw + 1) * cell);
            }
        }
        if (!daydien.isEmpty()) {
            float dx = 0f;
            float bottom = mapBottomPx - 60f;
            for (Texture t : daydien) { batch.draw(t, dx, bottom); dx += t.getWidth(); }
        }
        if (mapDef.id == 25) drawFarmSoil();
        // Vật thể gắn map + nhân vật vẽ TRỘN THEO ĐỘ SÂU như paintObject
        // của gốc: đáy/chân ở càng cao trên màn hình (worldY càng lớn) vẽ
        // càng trước. Vật thể neo đáy–giữa tại (anchorX, anchorY) của lưới.
        drawItems.clear();
        for (int[] o : mapDef.objects) {
            if (o[0] < 0) continue; // SubObject trang trại: đợt farm riêng
            Texture t = objTexture(o[0]);
            if (t == null) continue;
            DrawItem it = new DrawItem();
            it.kind = DrawItem.OBJ;
            it.y = mapBottomPx - o[2] * AvatarDkGame.HD;
            it.tex = t;
            it.objId = o[0];
            it.x = o[1] * AvatarDkGame.HD - t.getWidth() / 2f;
            drawItems.add(it);
        }
        if (mapDef.id == 25 && farm.joined) {
            buildFarmDrawItems();
        }
        for (NpcFigure f : npcFigures) {
            DrawItem it = new DrawItem();
            it.kind = DrawItem.NPC;
            it.y = f.feetY;
            it.npc = f;
            drawItems.add(it);
        }
        for (RemoteFigure f : remotes.values()) {
            DrawItem it = new DrawItem();
            it.kind = DrawItem.REMOTE;
            it.y = f.feetY;
            it.remote = f;
            drawItems.add(it);
        }
        if (busPhase != 1) { // đang ngồi trên xe (pha 1) thì chưa hiện hình
            DrawItem it = new DrawItem();
            it.kind = DrawItem.PLAYER;
            it.y = playerFeetY;
            drawItems.add(it);
        }
        // vòng chọn dưới chân người chơi đang mở panel tương tác
        if (panelPlayer != null && remotes.containsValue(panelPlayer)) {
            float pulse = 0.55f + 0.3f * (float) Math.sin(netTime * 5f);
            batch.draw(Ui.roundedRect(64, 18, 9, new Color(0f, 0f, 0f, 0f),
                new Color(0.45f, 0.95f, 1f, pulse)),
                panelPlayer.feetX - 32, panelPlayer.feetY - 7);
        }
        drawItems.sort((a, b) -> Float.compare(b.y, a.y));
        for (DrawItem it : drawItems) {
            switch (it.kind) {
                case DrawItem.OBJ:
                    batch.draw(it.tex, it.x, it.y);
                    if (it.objId == 846) {
                        // bảng KHU: gốc vẽ SỐ KHU hiện tại lên mặt bảng
                        // (ImageObj.paint của gốc) — chữ đen giữa bảng
                        String num = String.valueOf(currentArea);
                        Ui.font.setColor(0.05f, 0.1f, 0.25f, 1f);
                        layout.setText(Ui.font, num);
                        Ui.font.draw(batch, num,
                            it.x + it.tex.getWidth() / 2f - layout.width / 2f,
                            it.y + 56f);
                    }
                    break;
                case DrawItem.NPC:
                    drawFigure(it.npc.doll, it.npc.anim, it.npc.feetX, it.npc.feetY,
                        com.avatardk.game.data.GameSettings.showName ? it.npc.data.name : null,
                        false);
                    if (it.npc.bubble != null) {
                        drawBubble(it.npc.doll, it.npc.anim, it.npc.feetX, it.npc.feetY, it.npc.bubble);
                    }
                    break;
                case DrawItem.REMOTE:
                    // tên người chơi khác màu xanh nhạt (NPC vàng, mình đỏ);
                    // đang nhảy thì hình (và bong bóng) nâng lên theo cung nhảy
                    drawFigure(it.remote.doll, it.remote.anim, it.remote.feetX,
                        it.remote.feetY + it.remote.jumpOffY(),
                        com.avatardk.game.data.GameSettings.showName ? it.remote.info.name : null,
                        new Color(0.55f, 0.9f, 1f, 1f));
                    if (it.remote.bubble != null) {
                        drawBubble(it.remote.doll, it.remote.anim, it.remote.feetX,
                            it.remote.feetY + it.remote.jumpOffY(), it.remote.bubble);
                    }
                    break;
                case DrawItem.FARM:
                    if (it.region != null) {
                        batch.draw(it.region, it.x, it.y, it.dw, it.dh);
                    } else if (it.tex != null) {
                        batch.draw(it.tex, it.x, it.y, it.dw, it.dh);
                    }
                    break;
                case DrawItem.FARM_ANIMAL_INFO: {
                    FarmWorld.Animal fa = farm.animals.get(it.objId);
                    FarmWorld.AnimalDef fd = farm.animalDefs.get(fa.species);
                    if (fd != null) {
                        Ui.fontSmall.setColor(1f, 0.95f, 0.45f, 1f);
                        layout.setText(Ui.fontSmall, fd.name);
                        Ui.fontSmall.draw(batch, fd.name, it.x - layout.width / 2f, it.y + 20f);
                        String st = fa.numEggOne > 0
                            ? "Có " + fa.numEggOne + " sản phẩm chờ thu"
                            : (fa.hunger ? "Đang đói — hãy cho ăn" : "");
                        if (!st.isEmpty()) {
                            Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
                            layout.setText(Ui.fontSmall, st);
                            Ui.fontSmall.draw(batch, st, it.x - layout.width / 2f, it.y);
                        }
                    }
                    break;
                }
                case DrawItem.FARM_STAR: {
                    // Cây khế: vẽ thân/tán, rải quả lên tán, nhãn Lv + số quả.
                    if (it.tex != null) batch.draw(it.tex, it.x, it.y, it.dw, it.dh);
                    float cxS = it.x + it.dw / 2f;
                    if (farm.starNum > 0) {
                        Texture fruit = farm.dynTexture(farm.starFruit);
                        if (fruit != null) {
                            float[][] off = {
                                {-52f, 62f}, {-18f, 84f}, {16f, 70f}, {48f, 88f},
                                {-38f, 100f}, {6f, 104f}, {34f, 52f}, {-6f, 56f}};
                            for (float[] o : off) {
                                batch.draw(fruit,
                                    cxS + o[0] - fruit.getWidth() / 2f,
                                    it.y + o[1]);
                            }
                        }
                    }
                    Ui.fontSmall.setColor(1f, 0.95f, 0.45f, 1f);
                    String t = "Cây khế · Lv" + farm.starLv;
                    layout.setText(Ui.fontSmall, t);
                    Ui.fontSmall.draw(batch, t, cxS - layout.width / 2f, it.y + it.dh + 34f);
                    if (farm.starNum > 0) {
                        Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
                        String s = farm.starNum + " quả";
                        layout.setText(Ui.fontSmall, s);
                        Ui.fontSmall.draw(batch, s, cxS - layout.width / 2f, it.y + it.dh + 16f);
                    }
                    break;
                }
                case DrawItem.FARM_INFO: {
                    FarmWorld.Cell fc = farm.cells.get(it.objId);
                    FarmWorld.TreeDef td = farm.trees.get(fc.idTree);
                    if (td != null) {
                        float bx = it.x - 40f, by = it.y;
                        Ui.fontSmall.setColor(1f, 0.95f, 0.45f, 1f);
                        layout.setText(Ui.fontSmall, td.name);
                        Ui.fontSmall.draw(batch, td.name, it.x - layout.width / 2f, by + 44f);
                        // thanh sức khoẻ (xanh lá) + thanh lớn (vàng) như gốc
                        batch.setColor(0f, 0f, 0f, 0.75f);
                        batch.draw(whiteTex, bx - 1, by + 22, 82, 8);
                        batch.draw(whiteTex, bx - 1, by + 10, 82, 8);
                        batch.setColor(0.25f, 0.9f, 0.3f, 1f);
                        batch.draw(whiteTex, bx, by + 23, 80f * fc.vitality / 100f, 6);
                        batch.setColor(1f, 0.85f, 0.2f, 1f);
                        batch.draw(whiteTex, bx, by + 11, 80f * fc.hervestPer / 100f, 6);
                        batch.setColor(Color.WHITE);
                        String tail;
                        if (fc.hervestPer >= 100) {
                            tail = "Có thể thu hoạch";
                        } else {
                            int remain = Math.max(0, td.minutes - fc.time);
                            tail = remain >= 60 ? (remain / 60) + "g " + (remain % 60) + "p" : remain + " phút";
                        }
                        Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
                        layout.setText(Ui.fontSmall, tail);
                        Ui.fontSmall.draw(batch, tail, it.x - layout.width / 2f, by + 2f);
                    }
                    break;
                }
                default: // PLAYER
                    drawFigure(playerDoll, playerAnim, playerFeetX,
                        playerFeetY + jumpOffsetY(playerJumpT), displayName, true);
                    if (chatBubble != null) {
                        drawBubble(playerDoll, playerAnim, playerFeetX,
                            playerFeetY + jumpOffsetY(playerJumpT), chatBubble);
                    }
                    break;
            }
        }
        // xe buýt vẽ sau cùng trong thế giới (ở làn đường, trước mọi nhân vật)
        if (busPhase > 0) {
            batch.draw(busTex, busX * AvatarDkGame.HD - busTex.getWidth() / 2f, 2f);
        }
        drawActFx(playerFeetX, playerFeetY);
        batch.end();

        drawSideBars(camX);
        drawHud(playerFeetX, playerFeetY);
    }

    /** Hai cột ĐEN hai bên khi map hẹp hơn màn hình — cuối paintM của gốc:
     *  phần ngoài chiều rộng map được tô đen (setColor(0)) thay vì để nền trời. */
    private void drawSideBars(float camX) {
        if (worldW >= Ui.W || whiteTex == null) return;
        float visLeft = camX - Ui.W / 2f;
        float mapLeftOnScreen = -visLeft;
        float mapRightOnScreen = worldW - visLeft;
        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        batch.setColor(0f, 0f, 0f, 1f);
        if (mapLeftOnScreen > 0) batch.draw(whiteTex, 0, 0, mapLeftOnScreen, Ui.H);
        if (mapRightOnScreen < Ui.W) batch.draw(whiteTex, mapRightOnScreen, 0, Ui.W - mapRightOnScreen, Ui.H);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** Vẽ nền theo paintBackGround của gốc, tính theo không gian màn hình:
     *  1) tô màu trời colorBg[status] toàn màn (đã làm ở glClearColor),
     *  2) ảnh trời "may" lát liền theo chiều ngang, neo vào mép trái camera
     *     (hệ số thị sai 30/30), đáy nằm trên đường đáy map một khoảng x0_imgTreeBg,
     *  3) mây trôi (thị sai 30/40 cho anchor 0, 30/35 cho anchor 1),
     *  4) ảnh silhouette lát liền, thị sai 30/50, đáy thấp hơn đường đáy map x0_imgBG.
     *  Đổi sang hệ dưới–lên của mình: đáy map ở y=0 ↔ y gốc (trên–xuống) = 384. */
    private void drawBackground(float camX, float camY) {
        if (bgSkyTex == null || bgLineTex == null) return;
        hudViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        float visBottom = camY - Ui.H / 2f; // y thế giới ở đáy màn hình
        int bg = mapDef.bgNum();
        // lớp trời (may)
        float skyBottomY = mapBottomPx + X0_TREE_HD[bg] * AvatarDkGame.HD - visBottom;
        int step = bgSkyTex.getWidth() - 2;
        for (float x = -1; x < Ui.W; x += step) batch.draw(bgSkyTex, x, skyBottomY);
        // mây trôi
        for (Cloud c : clouds) {
            Texture t = cloudTex[c.anchor];
            float pf = (c.anchor == 0) ? 0.25f : (1f - 30f / 35f);
            float sx = c.x100 / 100f - camX * pf;
            if (sx < -t.getWidth() || sx > Ui.W) continue;
            batch.draw(t, sx, c.yW - visBottom);
        }
        // lớp silhouette (bg)
        float lineBottomY = mapBottomPx - X0_BG_HD[bg] * AvatarDkGame.HD - visBottom;
        int step2 = bgLineTex.getWidth() - 2;
        float startX = -1f - (camX * 0.4f) % step2;
        for (float x = startX; x < Ui.W; x += step2) batch.draw(bgLineTex, x, lineBottomY);
        batch.end();
    }

    private void drawFigure(PaperDoll doll, AvatarAnimator anim, float feetX, float feetY, String name, boolean isPlayer) {
        // tên trên đầu: người chơi màu đỏ, NPC màu vàng — đúng Avatar.paintName của gốc
        drawFigure(doll, anim, feetX, feetY, name,
            isPlayer ? new Color(1f, 0.35f, 0.3f, 1f) : new Color(1f, 0.9f, 0.35f, 1f));
    }

    private void drawFigure(PaperDoll doll, AvatarAnimator anim, float feetX, float feetY, String name, Color nameColor) {
        int frame = anim.frameIndex();
        game.dollRenderer.draw(batch, doll, frame, feetX, feetY, AvatarDkGame.HD, anim.facingLeft);
        if (name == null) {
            return; // cài đặt "Tên: ẩn" — nơi gọi truyền null cho người khác/NPC
        }
        float topY = feetY + game.dollRenderer.heightPx(doll, frame, AvatarDkGame.HD);
        Color c = nameColor;
        Ui.fontSmall.setColor(0f, 0f, 0f, 0.85f);
        layout.setText(Ui.fontSmall, name);
        Ui.fontSmall.draw(batch, name, feetX - layout.width / 2f + 1.5f, topY + 24f - 1.5f);
        Ui.fontSmall.setColor(c);
        Ui.fontSmall.draw(batch, name, feetX - layout.width / 2f, topY + 24f);
    }

    private void drawBubble(PaperDoll doll, AvatarAnimator anim, float feetX, float feetY, String text) {
        float w = richWidth(text) + 28f, h = 40f;
        float topY = feetY + game.dollRenderer.heightPx(doll, anim.frameIndex(), AvatarDkGame.HD);
        float x = feetX - w / 2f, y = topY + 32f;
        batch.draw(Ui.roundedRect((int) w, (int) h, 12, new Color(1f, 1f, 1f, 0.95f), new Color(0.2f, 0.2f, 0.2f, 0.9f)), x, y);
        drawRichText(text, x + 14f, y + 27f, new Color(0.08f, 0.08f, 0.1f, 1f));
    }

    private void drawHud(float playerFeetX, float playerFeetY) {
        hudViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        drawPlayerCard();
        drawFarmChip();
        drawBurger();
        if (menuOpen) drawBurgerPanel();
        drawJoystick();
        drawInteractButton();
        drawChatFrame();
        drawFeelUi();
        drawActionUi();
        if (panelPlayer != null) drawPlayerPanel();
        if (friendInviteId != 0) drawFriendInvite();
        if (charPanel) drawCharPanel();
        if (bagPanel) drawInvPanel(false);
        if (chestPanel) drawChestPanels();
        if (shopOpen) drawShop();
        if (npcMenuOpen) drawNpcMenu();
        if (guideOpen) drawGuidePanel();
        if (exitConfirmOpen) drawExitConfirm();
        if (boardOpen) drawBoardPanel();
        if (cityMapOpen) drawCityMap();
        if (farmSeedOpen) drawFarmSeedPanel();
        if (farmFertOpen) drawFarmFertPanel();
        if (farmLandOpen) drawFarmLandPanel();
        if (farmStoreOpen) drawFarmStorePanel();
        if (farmShopOpen) drawFarmShopPanel();
        if (farmCookOpen) drawFarmCookPanel();
        if (farmAnimalSellOpen) drawFarmAnimalSellPanel();
        if (settingsOpen) drawSettingsPanel();
        if (musicOpen) drawMusicPanel();
        if (msgOpen) drawMsgPanel();
        if (mailOpen) drawMailPanel();
        if (friendOpen) drawFriendPanel();
        if (passFormOpen) drawPassFormPanel();
        if (giftFormOpen) drawGiftFormPanel();
        batch.end();
    }

    /** Panel NHẠC: cột trái là trình phát — bìa lớn (thumbnail YouTube
     *  của bài đang phát), tên bài/kênh, thanh tiến trình chạm để tua,
     *  hàng nút điều khiển (trộn / trước / phát-dừng / sau / lặp) và
     *  thanh âm lượng; cột phải là 2 tab Tìm kiếm / Playlist với ô nhập
     *  gõ trực tiếp, danh sách bài (bìa 16:9 tải thật từ YouTube, bài
     *  đang phát có chip sóng nhạc) và hàng chip NHIỀU playlist. */
    private void drawMusicPanel() {
        initMusic();
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        float px = MUS_PX, py = MUS_PY, top = musTop();
        batch.draw(Ui.roundedRect((int) MUS_PW, (int) MUS_PH, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        // tiêu đề + nút đóng
        Texture mIcon = game.texture("iconmenu/music.png");
        batch.draw(mIcon, px + 24, top - 52, 32, 32);
        Ui.fontTitle.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontTitle.draw(batch, "Nhạc YouTube", px + 66, top - 27);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + MUS_PW - 54, top - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + MUS_PW - 36, top - 36, new Color(0.9f, 0.93f, 1f, 1f));
        batch.draw(Ui.roundedRect((int) (MUS_PW - 44), 2, 1, new Color(1f, 1f, 1f, 0.14f), null), px + 22, top - 66);

        YtVideo cur = music.current;
        boolean playing = music.isPlaying();
        // ----- cột trái: trình phát -----
        float lx = px + 26;
        float coverY = top - 80 - 252;
        batch.draw(Ui.roundedRect(256, 256, 12, new Color(0f, 0f, 0f, 0f), GLASS_RIM), lx - 2, coverY - 2);
        drawMusicCover(cur, lx, coverY, 252);
        Ui.font.setColor(1f, 1f, 1f, 0.96f);
        drawFittedName(cur != null ? cur.title : "Chưa chọn bài", lx, coverY - 18, 252);
        Ui.fontSmall.setColor(0.75f, 0.82f, 0.95f, 0.9f);
        drawFittedSmall(cur != null ? cur.channel : "Tìm bài hoặc mở playlist để nghe", lx, coverY - 46, 252);
        if (music.loadingTrack) {
            Ui.fontSmall.setColor(1f, 0.72f, 0.35f, 1f);
            Ui.fontSmall.draw(batch, "Đang tải nhạc…", lx, coverY - 70);
        }
        // tiến trình + mốc thời gian
        float barY = py + 168;
        int dur = music.currentDurationSec > 0 ? music.currentDurationSec
            : (cur != null ? cur.durationSec : 0);
        double pos = music.positionSec();
        if (dur > 0) pos = Math.min(pos, dur);
        float ratio = dur == 0 ? 0f : MathUtils.clamp((float) (pos / dur), 0f, 1f);
        Ui.fontTiny.setColor(0.75f, 0.82f, 0.95f, 0.9f);
        Ui.fontTiny.draw(batch, pos <= 0 ? "0:00" : YtVideo.fmt((int) pos), lx, barY + 26);
        layout.setText(Ui.fontTiny, YtVideo.fmt(dur));
        Ui.fontTiny.draw(batch, YtVideo.fmt(dur), lx + 252 - layout.width, barY + 26);
        batch.draw(Ui.roundedRect(252, 8, 4, new Color(1f, 1f, 1f, 0.18f), null), lx, barY);
        batch.draw(Ui.roundedRect(Math.max(8, (int) (252 * ratio)), 8, 4, new Color(1f, 0.62f, 0.25f, 1f), null), lx, barY);
        batch.setColor(1f, 0.62f, 0.25f, 1f);
        batch.draw(circleTexture(), lx + 252 * ratio - 7, barY - 3, 14, 14);
        batch.setColor(Color.WHITE);
        // hàng điều khiển
        float cy = py + 104;
        Color togOn = new Color(1f, 0.62f, 0.25f, 1f), togOff = new Color(0.75f, 0.82f, 0.95f, 0.7f);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_SHUFFLE, lx + 14, cy, music.shuffle ? togOn : togOff);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_REPEAT, lx + 238, cy, music.repeatOne ? togOn : togOff);
        batch.draw(Ui.roundedRect(42, 42, 21, GLASS_FILL, GLASS_RIM), lx + 62 - 21, cy - 21);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_BACKWARD_STEP, lx + 62, cy, new Color(0.92f, 0.95f, 1f, 1f));
        batch.draw(Ui.roundedRect(42, 42, 21, GLASS_FILL, GLASS_RIM), lx + 190 - 21, cy - 21);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_FORWARD_STEP, lx + 190, cy, new Color(0.92f, 0.95f, 1f, 1f));
        batch.draw(Ui.roundedRect(58, 58, 29, new Color(0.90f, 0.48f, 0.13f, 0.98f), new Color(1f, 0.75f, 0.4f, 0.9f)), lx + 126 - 29, cy - 29);
        Ui.faIcon(batch, Ui.fa26, playing ? Ui.FA_PAUSE : Ui.FA_PLAY, lx + 126, cy, Color.WHITE);
        // âm lượng
        float vy = py + 44;
        float vol = music.volume();
        Ui.faIcon(batch, Ui.fa20, Ui.FA_VOLUME_HIGH, lx + 10, vy + 3, togOff);
        batch.draw(Ui.roundedRect(220, 6, 3, new Color(1f, 1f, 1f, 0.18f), null), lx + 32, vy);
        batch.draw(Ui.roundedRect(Math.max(6, (int) (220 * vol)), 6, 3, new Color(1f, 0.62f, 0.25f, 1f), null), lx + 32, vy);
        batch.setColor(Color.WHITE);
        batch.draw(circleTexture(), lx + 32 + 220 * vol - 6, vy - 3, 12, 12);

        // ----- cột phải: tab + ô nhập + danh sách -----
        float rx = px + 302, rw = MUS_PW - 302 - 26;
        float tabY = top - 80 - 40;
        for (int t = 0; t < 2; t++) {
            float tx = rx + t * 160;
            boolean sel = musicTab == t;
            batch.draw(Ui.roundedRect(148, 40, 12,
                sel ? new Color(0.85f, 0.45f, 0.12f, 0.95f) : GLASS_FILL,
                sel ? new Color(1f, 0.75f, 0.4f, 0.9f) : GLASS_RIM), tx, tabY);
            Ui.centered(batch, Ui.fontSmall, t == 0 ? "Tìm kiếm" : "Playlist", tx + 74, tabY + 26, Color.WHITE);
        }
        if (musicTab == 1) {
            // hàng chip chọn playlist: mỗi playlist một chip, chip cam là
            // đang chọn; chip + tạo playlist mới; thùng rác ở cuối hàng
            // xoá playlist đang chọn
            float chipY = tabY - 12 - 34;
            float cx = rx;
            List<MusicLibrary.Playlist> pls = music.library().playlists;
            for (int i = 0; i < pls.size(); i++) {
                layout.setText(Ui.fontSmall, pls.get(i).name);
                float cw = layout.width + 30;
                if (cx + cw > rx + rw - 46) break; // quá bề ngang thì dừng (bản thật sẽ cuộn ngang)
                boolean sel = i == musicListSel;
                batch.draw(Ui.roundedRect((int) cw, 34, 10,
                    sel ? new Color(0.85f, 0.45f, 0.12f, 0.95f) : GLASS_FILL,
                    sel ? new Color(1f, 0.75f, 0.4f, 0.9f) : GLASS_RIM), cx, chipY);
                Ui.centered(batch, Ui.fontSmall, pls.get(i).name, cx + cw / 2f, chipY + 22, Color.WHITE);
                cx += cw + 8;
            }
            batch.draw(Ui.roundedRect(36, 34, 10, GLASS_FILL, GLASS_RIM), cx, chipY);
            Ui.faIcon(batch, Ui.fa20, Ui.FA_PLUS, cx + 18, chipY + 17, new Color(0.92f, 0.95f, 1f, 1f));
            batch.draw(Ui.roundedRect(34, 34, 10, GLASS_FILL, GLASS_RIM), rx + rw - 34, chipY);
            Ui.faIcon(batch, Ui.fa20, Ui.FA_TRASH, rx + rw - 17, chipY + 17, new Color(0.92f, 0.95f, 1f, 1f));
        }
        float inY = musInY();
        boolean focused = musicFocus == (musicTab == 0 ? 1 : 2);
        batch.draw(Ui.roundedRect((int) (rw - 108), 44, 10, new Color(1f, 1f, 1f, 0.08f),
            focused ? new Color(1f, 0.62f, 0.25f, 0.95f) : GLASS_RIM), rx, inY);
        Ui.faIcon(batch, Ui.fa20, musicTab == 0 ? Ui.FA_MAGNIFYING_GLASS : Ui.FA_LIST, rx + 24, inY + 22, togOff);
        StringBuilder field = musicTab == 0 ? musicQuery : musicLink;
        String placeholder = musicTab == 0 ? "Tìm bài hát, ca sĩ trên YouTube…"
            : "Dán link playlist YouTube…";
        if (field.length() == 0) {
            Ui.fontSmall.setColor(0.62f, 0.68f, 0.78f, 0.9f);
            Ui.fontSmall.draw(batch, placeholder, rx + 46, inY + 28);
        } else {
            String shown = field.toString();
            Ui.fontSmall.setColor(0.95f, 0.97f, 1f, 1f);
            layout.setText(Ui.fontSmall, shown);
            while (layout.width > rw - 108 - 62 && shown.length() > 4) {
                shown = shown.substring(1);
                layout.setText(Ui.fontSmall, shown);
            }
            Ui.fontSmall.draw(batch, shown, rx + 46, inY + 28);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Ui.fontSmall.draw(batch, "|", rx + 48 + layout.width, inY + 28);
            }
        }
        batch.draw(Ui.gradientRect(96, 44, 10, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), rx + rw - 96, inY);
        Ui.centered(batch, Ui.fontSmall, musicTab == 0 ? "Tìm" : "Thêm", rx + rw - 48, inY + 28, Color.WHITE);
        List<YtVideo> list = musicTab == 0 ? music.searchResults
            : (curPlaylist() != null ? curPlaylist().tracks : new ArrayList<>());
        float listTop = musListTop();
        int rows = Math.min(musicTab == 1 ? 4 : 5, list.size());
        for (int i = 0; i < rows; i++) {
            YtVideo tr = list.get(i);
            float ry = listTop - (i + 1) * 66 - i * 6;
            boolean isCur = cur != null && tr.id.equals(cur.id);
            batch.draw(Ui.roundedRect((int) rw, 66, 10,
                isCur ? new Color(0.55f, 0.38f, 0.10f, 0.55f) : new Color(1f, 1f, 1f, 0.05f),
                isCur ? new Color(1f, 0.8f, 0.35f, 0.8f) : null), rx, ry);
            drawMusicThumb(tr, rx + 8, ry + 8, 88, 50);
            if (isCur && playing) {
                // chip sóng nhạc trên bìa của bài đang phát
                batch.draw(Ui.roundedRect(30, 20, 5, new Color(0.02f, 0.04f, 0.08f, 0.72f), null), rx + 12, ry + 12);
                for (int b = 0; b < 3; b++) {
                    batch.draw(Ui.roundedRect(4, 6 + b * 4, 2, new Color(1f, 0.62f, 0.25f, 1f), null),
                        rx + 17 + b * 8, ry + 16);
                }
            }
            Ui.font.setColor(1f, 1f, 1f, 0.96f);
            drawFittedName(tr.title, rx + 106, ry + 42, rw - 226);
            Ui.fontTiny.setColor(0.72f, 0.79f, 0.92f, 0.9f);
            Ui.fontTiny.draw(batch, tr.channel + " • " + tr.durationText(), rx + 106, ry + 20);
            batch.draw(Ui.roundedRect(34, 34, 17, GLASS_FILL, GLASS_RIM), rx + rw - 92, ry + 16);
            Ui.faIcon(batch, Ui.fa20, isCur && playing ? Ui.FA_PAUSE : Ui.FA_PLAY,
                rx + rw - 75, ry + 33, new Color(0.92f, 0.95f, 1f, 1f));
            batch.draw(Ui.roundedRect(34, 34, 17, GLASS_FILL, GLASS_RIM), rx + rw - 50, ry + 16);
            String sub = musicTab == 0
                ? (curPlaylist() != null && curPlaylist().contains(tr.id) ? Ui.FA_CHECK : Ui.FA_PLUS)
                : Ui.FA_XMARK;
            Ui.faIcon(batch, Ui.fa20, sub, rx + rw - 33, ry + 33, new Color(0.92f, 0.95f, 1f, 1f));
        }
        if (list.isEmpty()) {
            String hint;
            if (musicTab == 0) {
                hint = music.searching ? "Đang tìm trên YouTube…"
                    : "Gõ tên bài hát, ca sĩ rồi bấm Tìm.";
            } else {
                hint = music.importing ? "Đang nhập playlist…" : "Playlist này đang trống.";
            }
            Ui.fontSmall.setColor(0.72f, 0.79f, 0.92f, 0.9f);
            Ui.centered(batch, Ui.fontSmall, hint, rx + rw / 2f, listTop - 52, new Color(0.72f, 0.79f, 0.92f, 0.9f));
        }
        // chân panel: trạng thái / thông tin playlist
        Ui.fontSmall.setColor(1f, 0.78f, 0.45f, 0.95f);
        if (!musicStatus.isEmpty()) {
            drawFittedSmall(musicStatus, rx, py + 18, rw);
        } else if (musicTab == 1 && curPlaylist() != null) {
            Ui.fontSmall.setColor(0.72f, 0.79f, 0.92f, 0.9f);
            Ui.fontSmall.draw(batch, "Playlist " + curPlaylist().name + " • "
                + curPlaylist().tracks.size() + " bài • Tổng "
                + YtVideo.fmt(curPlaylist().totalSec()), rx, py + 18);
        } else if (musicTab == 0 && !list.isEmpty()) {
            Ui.fontSmall.setColor(0.72f, 0.79f, 0.92f, 0.9f);
            Ui.fontSmall.draw(batch, list.size() + " kết quả • Bấm vào bài để phát", rx, py + 18);
        }
    }

    /** Bìa lớn của bài đang phát: thumbnail HQ crop vuông giữa; chưa
     *  tải được thì dùng bìa mẫu theo mã bài. */
    private void drawMusicCover(YtVideo v, float x, float y, float size) {
        Texture t = null;
        if (v != null && musicThumbs != null) {
            t = musicThumbs.get("big:" + v.id, v.hqThumbUrl(), v.thumbUrl);
        }
        if (t != null) {
            int w = t.getWidth(), h = t.getHeight();
            int side = Math.min(w, h);
            batch.draw(new TextureRegion(t, (w - side) / 2, (h - side) / 2, side, side), x, y, size, size);
        } else {
            int idx = v == null ? 1 : 1 + Math.floorMod(v.id.hashCode(), 4);
            batch.draw(game.texture("music/cover" + idx + ".png"), x, y, size, size);
        }
    }

    /** Bìa hàng danh sách: thumbnail crop giữa theo khung 16:9 (gỡ
     *  được cả viền đen của bìa hqdefault 4:3); chưa tải được thì bìa mẫu. */
    private void drawMusicThumb(YtVideo v, float x, float y, float w, float h) {
        Texture t = musicThumbs != null ? musicThumbs.get("row:" + v.id, v.thumbUrl, null) : null;
        if (t != null) {
            int tw = t.getWidth(), th = t.getHeight();
            float target = w / h;
            int rh = Math.min(th, Math.max(1, (int) (tw / target)));
            int rw2 = Math.min(tw, (int) (rh * target));
            batch.draw(new TextureRegion(t, (tw - rw2) / 2, (th - rh) / 2, rw2, rh), x, y, w, h);
        } else {
            int idx = 1 + Math.floorMod(v.id.hashCode(), 4);
            Texture ct = game.texture("music/cover" + idx + ".png");
            batch.draw(new TextureRegion(ct, 0, 52, 240, 136), x, y, w, h);
        }
    }

    /** Vẽ một dòng fontSmall cắt ngắn theo bề ngang cho trước. */
    private void drawFittedSmall(String text, float x, float baseline, float maxW) {
        String s = text == null ? "" : text;
        layout.setText(Ui.fontSmall, s);
        if (layout.width > maxW) {
            while (s.length() > 4) {
                s = s.substring(0, s.length() - 1);
                layout.setText(Ui.fontSmall, s + "…");
                if (layout.width <= maxW) break;
            }
            s = s + "…";
        }
        Ui.fontSmall.draw(batch, s, x, baseline);
    }

    /** Chạm trong panel Nhạc: mọi thao tác chuyển xuống MusicService. */
    private void musicTouch(float x, float y) {
        initMusic();
        float px = MUS_PX, py = MUS_PY, top = musTop();
        if (x >= px + MUS_PW - 54 && x <= px + MUS_PW - 18 && y >= top - 54 && y <= top - 18) {
            musicOpen = false;
            musicFocus = 0;
            return;
        }
        if (x < px || x > px + MUS_PW || y < py || y > py + MUS_PH) {
            musicOpen = false;
            musicFocus = 0;
            return;
        }
        float lx = px + 26;
        float cy = py + 104;
        if (Math.hypot(x - (lx + 126), y - cy) <= 32) {
            if (music.current != null) music.togglePlayPause();
            return;
        }
        if (Math.hypot(x - (lx + 62), y - cy) <= 24) { music.prev(); return; }
        if (Math.hypot(x - (lx + 190), y - cy) <= 24) { music.next(true); return; }
        if (Math.hypot(x - (lx + 14), y - cy) <= 17) { music.shuffle = !music.shuffle; return; }
        if (Math.hypot(x - (lx + 238), y - cy) <= 17) { music.repeatOne = !music.repeatOne; return; }
        float barY = py + 168;
        if (x >= lx && x <= lx + 252 && y >= barY - 8 && y <= barY + 16) {
            int dur = music.currentDurationSec > 0 ? music.currentDurationSec
                : (music.current != null ? music.current.durationSec : 0);
            if (dur > 0 && music.current != null) {
                music.seek(dur * MathUtils.clamp((x - lx) / 252f, 0f, 1f));
            }
            return;
        }
        float vy = py + 44;
        if (x >= lx + 32 && x <= lx + 252 && y >= vy - 8 && y <= vy + 14) {
            music.setVolume(MathUtils.clamp((x - (lx + 32)) / 220f, 0f, 1f));
            return;
        }
        float rx = px + 302, rw = MUS_PW - 302 - 26;
        float tabY = top - 80 - 40;
        if (y >= tabY && y <= tabY + 40) {
            for (int t = 0; t < 2; t++) {
                float tx = rx + t * 160;
                if (x >= tx && x <= tx + 148) {
                    musicTab = t;
                    musicFocus = 0;
                    return;
                }
            }
        }
        if (musicTab == 1) {
            // hàng chip: chọn playlist / chip + tạo mới / thùng rác xoá
            float chipY = tabY - 12 - 34;
            if (y >= chipY && y <= chipY + 34) {
                List<MusicLibrary.Playlist> pls = music.library().playlists;
                if (x >= rx + rw - 34 && x <= rx + rw) {
                    if (pls.size() > 1) {
                        pls.remove(musicListSel);
                        musicListSel = Math.min(musicListSel, pls.size() - 1);
                        music.saveLibrary();
                    }
                    return;
                }
                float cx = rx;
                for (int i = 0; i < pls.size(); i++) {
                    layout.setText(Ui.fontSmall, pls.get(i).name);
                    float cw = layout.width + 30;
                    if (cx + cw > rx + rw - 46) break;
                    if (x >= cx && x <= cx + cw) { musicListSel = i; return; }
                    cx += cw + 8;
                }
                if (x >= cx && x <= cx + 36) {
                    pls.add(new MusicLibrary.Playlist("Playlist mới " + (++musicListCount)));
                    musicListSel = pls.size() - 1;
                    music.saveLibrary();
                    return;
                }
                return;
            }
        }
        // ô nhập: chạm vào là đặt focus gõ; nút bấm cạnh ô chạy hành động
        float inY = musInY();
        if (y >= inY && y <= inY + 44) {
            if (x >= rx + rw - 96 && x <= rx + rw) {
                if (musicTab == 0) runMusicSearch();
                else runMusicImport();
                return;
            }
            if (x >= rx && x <= rx + rw - 108) {
                musicFocus = musicTab == 0 ? 1 : 2;
                return;
            }
        }
        musicFocus = 0;
        List<YtVideo> list = musicTab == 0 ? music.searchResults
            : (curPlaylist() != null ? curPlaylist().tracks : new ArrayList<>());
        float listTop = musListTop();
        int rows = Math.min(musicTab == 1 ? 4 : 5, list.size());
        for (int i = 0; i < rows; i++) {
            float ry = listTop - (i + 1) * 66 - i * 6;
            if (x >= rx && x <= rx + rw && y >= ry && y <= ry + 66) {
                YtVideo tr = list.get(i);
                if (x >= rx + rw - 67 && x <= rx + rw - 33 && y >= ry + 12 && y <= ry + 54) {
                    if (musicTab == 0) {
                        MusicLibrary.Playlist p = curPlaylist();
                        if (p != null && !p.contains(tr.id)) {
                            p.tracks.add(tr);
                            music.saveLibrary();
                            musicStatus = "Đã thêm vào playlist " + p.name + ".";
                        }
                    } else if (curPlaylist() != null) {
                        curPlaylist().tracks.remove(tr);
                        music.saveLibrary();
                    }
                    return;
                }
                if (x >= rx + rw - 92 && x <= rx + rw - 58 && y >= ry + 12 && y <= ry + 54
                    && music.current != null && tr.id.equals(music.current.id)) {
                    music.togglePlayPause();
                    return;
                }
                music.playQueue(list, i);
                return;
            }
        }
    }

    /** Bấm Tìm / Enter ở ô tìm kiếm. */
    private void runMusicSearch() {
        initMusic();
        String q = musicQuery.toString().trim();
        if (q.isEmpty()) {
            musicStatus = "Hãy gõ tên bài hát hoặc ca sĩ trước.";
            return;
        }
        musicStatus = "Đang tìm trên YouTube…";
        music.searchAsync(q);
    }

    /** Bấm Thêm / Enter ở ô dán link playlist. */
    private void runMusicImport() {
        initMusic();
        String link = musicLink.toString().trim();
        if (link.isEmpty()) {
            musicStatus = "Hãy dán link playlist YouTube vào ô trên.";
            return;
        }
        musicStatus = "Đang nhập playlist…";
        music.importPlaylistAsync(link);
    }

    /** Panel Cài đặt (OptionScr của gốc) theo tông kính của game — bản 2
     *  theo yêu cầu của chủ dự án: bỏ mũi tên bước, ba hàng Tên / Chỉ
     *  đường / Âm thanh dùng CÔNG TẮC GẠT (bật là áp dụng và lưu ngay,
     *  không còn nút Xong); thêm hàng Đổi mật khẩu mở form riêng. */
    private void drawSettingsPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) SET_PW, (int) SET_PH, 16, GLASS_FILL_DARK, GLASS_RIM),
            SET_PX, SET_PY);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Cài đặt", SET_PX + 26, SET_PY + SET_PH - 30);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM),
            SET_PX + SET_PW - 54, SET_PY + SET_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, SET_PX + SET_PW - 36, SET_PY + SET_PH - 36,
            new Color(1f, 1f, 1f, 0.9f));
        String[] labels = {"Tên", "Chỉ đường", "Âm thanh", "Đổi mật khẩu", "Giftcode"};
        for (int i = 0; i < 5; i++) {
            float cy = setRowCenter(i);
            if (i > 0) {
                batch.draw(Ui.roundedRect((int) SET_PW - 48, 2, 1,
                    new Color(1f, 1f, 1f, 0.07f), new Color(1f, 1f, 1f, 0.07f)),
                    SET_PX + 24, cy + 38);
            }
            Ui.font.setColor(1f, 1f, 1f, 0.95f);
            Ui.font.draw(batch, labels[i], SET_PX + 30, cy + 10);
            if (i < 3) {
                drawSetToggle(i, cy);
            } else {
                // hàng Đổi mật khẩu / Giftcode: nút mở form riêng
                float bw = 116, bh = 46, bx = SET_PX + SET_PW - 26 - bw, by = cy - bh / 2f;
                batch.draw(Ui.roundedRect((int) bw, (int) bh, 12,
                    new Color(0.98f, 0.58f, 0.16f, 1f), new Color(0.98f, 0.58f, 0.16f, 1f)), bx, by);
                Ui.centered(batch, Ui.font, i == 3 ? "Đổi" : "Nhập", bx + bw / 2f, by + 31, new Color(1f, 1f, 1f, 1f));
            }
        }
    }

    private boolean setRowOn(int i) {
        if (i == 0) return com.avatardk.game.data.GameSettings.showName;
        if (i == 1) return com.avatardk.game.data.GameSettings.showDir;
        return com.avatardk.game.data.GameSettings.volume > 0;
    }

    /** Công tắc gạt một hàng cài đặt: thân pill (xanh lá khi bật, xám khi
     *  tắt) + núm tròn trượt hai đầu + chữ trạng thái hiện/ẩn, bật/tắt. */
    private void drawSetToggle(int i, float cy) {
        boolean on = setRowOn(i);
        float cx = SET_PX + SET_PW - 70;
        String st = i == 2 ? (on ? "bật" : "tắt") : (on ? "hiện" : "ẩn");
        layout.setText(Ui.fontSmall, st);
        Ui.fontSmall.setColor(on ? new Color(0.55f, 0.95f, 0.60f, 1f)
            : new Color(0.62f, 0.65f, 0.70f, 1f));
        Ui.fontSmall.draw(batch, st, cx - 46 - layout.width, cy + 7);
        batch.draw(Ui.roundedRect(68, 38, 19,
            on ? new Color(0.25f, 0.72f, 0.42f, 0.95f) : new Color(0.30f, 0.33f, 0.40f, 0.95f),
            GLASS_RIM), cx - 34, cy - 19);
        batch.setColor(Color.WHITE);
        batch.draw(circleTexture(), (on ? cx + 16 : cx - 16) - 15, cy - 15, 30, 30);
    }

    private void closeSettings() {
        com.avatardk.game.data.GameSettings.save();
        settingsOpen = false;
    }

    /** Gạt một hàng cài đặt: Tên/Chỉ đường lật hiện-ẩn; Âm thanh tắt thì
     *  nhớ mức cũ vào lastVolume, bật lại khôi phục đúng mức đó. Mọi thay
     *  đổi lưu ngay (panel không còn nút Xong). */
    private void applySettingsToggle(int row) {
        if (row == 0) {
            com.avatardk.game.data.GameSettings.showName =
                !com.avatardk.game.data.GameSettings.showName;
        } else if (row == 1) {
            com.avatardk.game.data.GameSettings.showDir =
                !com.avatardk.game.data.GameSettings.showDir;
        } else if (row == 2) {
            if (com.avatardk.game.data.GameSettings.volume > 0) {
                com.avatardk.game.data.GameSettings.lastVolume =
                    com.avatardk.game.data.GameSettings.volume;
                com.avatardk.game.data.GameSettings.volume = 0;
            } else {
                com.avatardk.game.data.GameSettings.volume =
                    com.avatardk.game.data.GameSettings.lastVolume > 0
                        ? com.avatardk.game.data.GameSettings.lastVolume : 100;
            }
        }
        com.avatardk.game.data.GameSettings.save();
    }

    private void settingsTouch(float x, float y) {
        if (x >= SET_PX + SET_PW - 54 && x <= SET_PX + SET_PW - 18
                && y >= SET_PY + SET_PH - 54 && y <= SET_PY + SET_PH - 18) {
            closeSettings();
            return;
        }
        for (int i = 0; i < 5; i++) {
            if (Math.abs(y - setRowCenter(i)) <= 36) {
                if (i < 3) {
                    applySettingsToggle(i);
                } else if (i == 3) {
                    openPassForm();
                } else {
                    openGiftForm();
                }
                return;
            }
        }
        if (x < SET_PX || x > SET_PX + SET_PW || y < SET_PY || y > SET_PY + SET_PH) {
            closeSettings();
        }
    }

    // ---------- Form ĐỔI MẬT KHẨU (InputFace của gốc, gói −62) ----------
    // Gốc mở từ menu của MainMenu (mục Đổi mật khẩu cạnh Cài đặt), form 3
    // ô Mật khẩu Cũ / Mật khẩu Mới / Nhập lại Mật khẩu, câu báo lỗi lấy
    // nguyên T.enterPass. Gửi gói −62 {UTF cũ, UTF mới}; server trả −62
    // kèm dialog thành công hoặc dialog "Mật khẩu cũ không đúng.".
    private boolean passFormOpen = false;
    private int passFocus = 0;
    private final StringBuilder[] passFields =
        {new StringBuilder(), new StringBuilder(), new StringBuilder()};
    private String passStatus = null;
    private int passStatusKind = 0; // 0 xám (thông tin), 1 xanh (thành công), 2 đỏ (lỗi)
    private static final float PF_PW = 560f, PF_PH = 478f;
    private static final float PF_PX = (Ui.W - PF_PW) / 2f, PF_PY = (Ui.H - PF_PH) / 2f;
    private static final String[] PASS_LABELS =
        {"Mật khẩu cũ", "Mật khẩu mới", "Nhập lại mật khẩu"};

    private float passBoxY(int i) { return PF_PY + PF_PH - 168 - i * 92; }

    private void setPassStatus(String s, int kind) {
        passStatus = s;
        passStatusKind = kind;
    }

    private void openPassForm() {
        settingsOpen = false;
        passFormOpen = true;
        passFocus = 0;
        passStatus = null;
        for (StringBuilder b : passFields) b.setLength(0);
    }

    private void closePassForm() {
        passFormOpen = false;
        settingsOpen = true;
        Gdx.input.setOnscreenKeyboardVisible(false);
    }

    private void drawPassFormPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) PF_PW, (int) PF_PH, 16, GLASS_FILL_DARK, GLASS_RIM),
            PF_PX, PF_PY);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Đổi mật khẩu", PF_PX + 26, PF_PY + PF_PH - 30);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM),
            PF_PX + PF_PW - 54, PF_PY + PF_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, PF_PX + PF_PW - 36, PF_PY + PF_PH - 36,
            new Color(1f, 1f, 1f, 0.9f));
        for (int i = 0; i < 3; i++) {
            float by = passBoxY(i);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
            Ui.fontSmall.draw(batch, PASS_LABELS[i], PF_PX + 26, by + 80);
            boolean focused = passFocus == i;
            batch.draw(Ui.roundedRect((int) PF_PW - 52, 54, 12,
                new Color(0.10f, 0.13f, 0.22f, 0.97f),
                focused ? new Color(1f, 0.62f, 0.25f, 1f) : GLASS_RIM),
                PF_PX + 26, by);
            StringBuilder dots = new StringBuilder();
            for (int d = 0; d < passFields[i].length(); d++) dots.append('•');
            Ui.font.setColor(1f, 1f, 1f, 0.95f);
            Ui.font.draw(batch, dots.toString(), PF_PX + 42, by + 37);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                layout.setText(Ui.font, dots.toString());
                batch.setColor(1f, 1f, 1f, 0.85f);
                batch.draw(whiteTex, PF_PX + 44 + layout.width, by + 14, 3, 26);
                batch.setColor(Color.WHITE);
            }
        }
        if (passStatus != null) {
            Color sc = passStatusKind == 1 ? new Color(0.55f, 0.95f, 0.60f, 1f)
                : passStatusKind == 2 ? new Color(1f, 0.45f, 0.40f, 1f)
                : new Color(1f, 1f, 1f, 0.65f);
            Ui.centered(batch, Ui.fontSmall, passStatus, PF_PX + PF_PW / 2f, PF_PY + 104, sc);
        }
        float bw = 240, bh = 56, bx = PF_PX + (PF_PW - bw) / 2f, by = PF_PY + 26;
        batch.draw(Ui.gradientRect((int) bw, (int) bh, 14, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, by);
        Ui.centered(batch, Ui.font, "Đổi mật khẩu", PF_PX + PF_PW / 2f, by + 37,
            new Color(1f, 1f, 1f, 1f));
    }

    private void passFormTouch(float x, float y) {
        if (x >= PF_PX + PF_PW - 54 && x <= PF_PX + PF_PW - 18
                && y >= PF_PY + PF_PH - 54 && y <= PF_PY + PF_PH - 18) {
            closePassForm();
            return;
        }
        float bw = 240, bh = 56, bx = PF_PX + (PF_PW - bw) / 2f, by = PF_PY + 26;
        if (x >= bx && x <= bx + bw && y >= by && y <= by + bh) {
            submitPassForm();
            return;
        }
        for (int i = 0; i < 3; i++) {
            float fby = passBoxY(i);
            if (x >= PF_PX + 26 && x <= PF_PX + PF_PW - 26 && y >= fby && y <= fby + 54) {
                passFocus = i;
                Gdx.input.setOnscreenKeyboardVisible(true);
                return;
            }
        }
        if (x < PF_PX || x > PF_PX + PF_PW || y < PF_PY || y > PF_PY + PF_PH) {
            closePassForm();
        }
    }

    /** Kiểm tra đúng 5 câu T.enterPass của gốc rồi gửi gói −62. */
    private void submitPassForm() {
        String oldP = passFields[0].toString();
        String newP = passFields[1].toString();
        String cf = passFields[2].toString();
        if (oldP.isEmpty()) { setPassStatus("Xin nhập mật khẩu hiện tại.", 2); return; }
        if (newP.isEmpty()) { setPassStatus("Xin nhập mật khẩu mới.", 2); return; }
        if (cf.isEmpty()) { setPassStatus("Xin nhập lại mật khẩu mới.", 2); return; }
        if (!newP.equals(cf)) { setPassStatus("Hai mật khẩu mới phải giống nhau.", 2); return; }
        if (newP.equals(oldP)) { setPassStatus("Mật khẩu mới phải khác mật khẩu cũ.", 2); return; }
        if (!online()) { setPassStatus("Chưa kết nối máy chủ.", 2); return; }
        try {
            AvatarProtocol.changePassword(game.connection, oldP, newP);
            setPassStatus("Đang đổi mật khẩu…", 0);
        } catch (IOException e) {
            setPassStatus("Không gửi được (mất kết nối?).", 2);
        }
    }

    // ---------- Form NHẬP GIFTCODE (gốc: TEXT_BOX −60, menuId 20) ----------
    // Mở từ hàng Giftcode trong panel Cài đặt. Server trả dialog kết
    // quả qua gói −10; quà của mã đúng được gửi vào HÒM THƯ (user
    // chốt 2026-11-02), người chơi mở Thư để nhận.
    private boolean giftFormOpen = false;
    private final StringBuilder giftField = new StringBuilder();
    private String giftStatus = null;
    private int giftStatusKind = 0; // 0 xám, 1 xanh, 2 đỏ
    private static final float GF_PW = 560f, GF_PH = 320f;
    private static final float GF_PX = (Ui.W - GF_PW) / 2f, GF_PY = (Ui.H - GF_PH) / 2f;

    private void openGiftForm() {
        giftField.setLength(0);
        giftStatus = null;
        giftFormOpen = true;
    }

    private void closeGiftForm() {
        giftFormOpen = false;
        Gdx.input.setOnscreenKeyboardVisible(false);
    }

    private void setGiftStatus(String s, int kind) {
        giftStatus = s;
        giftStatusKind = kind;
    }

    private void drawGiftFormPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) GF_PW, (int) GF_PH, 16, GLASS_FILL_DARK, GLASS_RIM),
            GF_PX, GF_PY);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Nhập giftcode", GF_PX + 26, GF_PY + GF_PH - 30);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM),
            GF_PX + GF_PW - 54, GF_PY + GF_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, GF_PX + GF_PW - 36, GF_PY + GF_PH - 36,
            new Color(1f, 1f, 1f, 0.9f));
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
        Ui.fontSmall.draw(batch, "Mã quà tặng", GF_PX + 26, GF_PY + GF_PH - 92);
        float by = GF_PY + GF_PH - 168;
        batch.draw(Ui.roundedRect((int) GF_PW - 52, 54, 12,
            new Color(0.10f, 0.13f, 0.22f, 0.97f), new Color(1f, 0.62f, 0.25f, 1f)),
            GF_PX + 26, by);
        Ui.font.setColor(1f, 1f, 1f, 0.95f);
        Ui.font.draw(batch, giftField.toString(), GF_PX + 42, by + 37);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            layout.setText(Ui.font, giftField.toString());
            batch.setColor(1f, 1f, 1f, 0.85f);
            batch.draw(whiteTex, GF_PX + 44 + layout.width, by + 14, 3, 26);
            batch.setColor(Color.WHITE);
        }
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.55f);
        Ui.fontSmall.draw(batch, "Quà sẽ được gửi vào Hòm Thư, mở Thư để nhận.",
            GF_PX + 26, by - 26);
        if (giftStatus != null) {
            Color sc = giftStatusKind == 1 ? new Color(0.55f, 0.95f, 0.60f, 1f)
                : giftStatusKind == 2 ? new Color(1f, 0.45f, 0.40f, 1f)
                : new Color(1f, 1f, 1f, 0.65f);
            Ui.centered(batch, Ui.fontSmall, giftStatus, GF_PX + GF_PW / 2f, GF_PY + 104, sc);
        }
        float bw = 240, bh = 56, bx = GF_PX + (GF_PW - bw) / 2f, sby = GF_PY + 26;
        batch.draw(Ui.gradientRect((int) bw, (int) bh, 14, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), bx, sby);
        Ui.centered(batch, Ui.font, "Nhận quà", GF_PX + GF_PW / 2f, sby + 37, new Color(1f, 1f, 1f, 1f));
    }

    private void giftFormTouch(float x, float y) {
        if (x >= GF_PX + GF_PW - 54 && x <= GF_PX + GF_PW - 18
                && y >= GF_PY + GF_PH - 54 && y <= GF_PY + GF_PH - 18) {
            closeGiftForm();
            return;
        }
        float bw = 240, bh = 56, bx = GF_PX + (GF_PW - bw) / 2f, sby = GF_PY + 26;
        if (x >= bx && x <= bx + bw && y >= sby && y <= sby + bh) {
            submitGiftForm();
            return;
        }
        float by = GF_PY + GF_PH - 168;
        if (x >= GF_PX + 26 && x <= GF_PX + GF_PW - 26 && y >= by && y <= by + 54) {
            Gdx.input.setOnscreenKeyboardVisible(true);
            return;
        }
        if (x < GF_PX || x > GF_PX + GF_PW || y < GF_PY || y > GF_PY + GF_PH) {
            closeGiftForm();
        }
    }

    private void submitGiftForm() {
        String code = giftField.toString().trim();
        if (code.isEmpty()) { setGiftStatus("Chưa nhập mã giftcode.", 2); return; }
        if (!online()) { setGiftStatus("Chưa kết nối máy chủ.", 2); return; }
        try {
            AvatarProtocol.sendTextBox(game.connection, ownId(), 20, code);
            setGiftStatus("Đã gửi mã, chờ phản hồi…", 0);
        } catch (IOException e) {
            setGiftStatus("Không gửi được (mất kết nối?).", 2);
        }
    }

    /** Mở form giftcode cho harness chụp kiểm chứng. */
    public void debugOpenGiftForm() {
        settingsOpen = true;
        openGiftForm();
        giftField.append("TANTHU2026");
    }

    /** Mở form Đổi mật khẩu cho harness chụp kiểm chứng (điền sẵn để thấy
     *  ô nhập che dấu chấm). */
    public void debugOpenPassForm() {
        openPassForm();
        passFields[0].append("abcd1234");
        passFields[1].append("matkhaumoi");
        passFields[2].append("matkhaumoi");
        passFocus = 1;
    }

    /** Mở panel Cài đặt cho harness chụp kiểm chứng. */
    public void debugOpenSettings() { settingsOpen = true; }

    /** Đóng shop + hoá đơn cho harness chụp kiểm chứng. */
    public void debugCloseShop() { shopOpen = false; billOpen = false; }

    /** Rộng của mỗi tab chat: nhãn dài nhất + lề, dùng chung cho vẽ và chạm
     *  để cả 3 tab rộng bằng nhau. */
    private float chatTabWidth() {
        float max = 0;
        for (String t : CHAT_TABS) {
            layout.setText(Ui.fontSmall, t);
            max = Math.max(max, layout.width);
        }
        return max + 26;
    }

    /** Khung chat GIỮA DƯỚI, luôn hiện, gồm: hàng TAB (Hệ thống / Thế giới /
     *  Gần đây) ở trên cùng, nhật ký của tab đang chọn ở giữa, ô nhập + NÚT GỬI
     *  ở dưới. Enter/chạm vào ô để gõ, Enter hoặc nút Gửi để gửi (lời chat
     *  đồng thời nổi bong bóng trên đầu như gốc). Lời NPC không vào khung này.
     *  Khi nối server: tab Gần đây = gói chat 55/77, Thế giới = kênh toàn server,
     *  Hệ thống = gói SERVER_MESSAGE (-9) của gốc. */
    private void drawChatFrame() {
        batch.draw(Ui.roundedRect((int) CHAT_W, (int) chatH, 14,
            new Color(0.04f, 0.09f, 0.15f, 0.82f), GLASS_RIM), CHAT_X, CHAT_Y);
        // Hàng tab kiểu "thẻ thư mục" VẮT NGANG ĐƯỜNG VIỀN TRÊN của khung
        // (chủ dự án chốt): trung tâm mỗi tab nằm đúng trên đường viền — nửa
        // tab nhô lên ngoài khung, nửa tab lọt vào trong, thân tab che luôn
        // đoạn viền đi qua nó nên tab + khung trông như MỘT KHỐI. Tab đang
        // dùng CAO hơn hẳn và sáng tông kính; tab chưa dùng lún thấp, tối
        // hơn, chữ mờ. Bỏ hẳn màu cam cũ cho hợp tông panel.
        float tw = chatTabWidth();
        float tx = CHAT_X + 12;
        for (int i = 0; i < CHAT_TABS.length; i++) {
            boolean active = i == activeChatTab;
            layout.setText(Ui.fontSmall, CHAT_TABS[i]);
            float textX = tx + (tw - layout.width) / 2f;
            float th = active ? TAB_H_ACTIVE : TAB_H_IDLE;
            float tabBottom = CHAT_Y + chatH - th / 2f;
            Color fill = active ? new Color(0.10f, 0.17f, 0.26f, 0.97f)
                                : new Color(0.03f, 0.06f, 0.10f, 0.92f);
            Color rim = active ? new Color(0.62f, 0.82f, 1f, 0.60f)
                               : new Color(0.45f, 0.60f, 0.80f, 0.32f);
            batch.draw(Ui.roundedRect((int) tw, (int) th, 8, fill, rim), tx, tabBottom);
            Ui.fontSmall.setColor(active ? Color.WHITE : new Color(1f, 1f, 1f, 0.55f));
            Ui.fontSmall.draw(batch, CHAT_TABS[i], textX, tabBottom + th - 9);
            tx += tw + 6;
        }
        // nhật ký của tab đang chọn: dòng mới nhất nằm trên ô nhập, CÁCH
        // ô nhập một khoảng thoáng (chủ dự án yêu cầu); panel phóng to
        // thì hiện thêm dòng theo chiều cao thật
        List<ChatLine> log = chatLogs[activeChatTab];
        int maxLines = (int) CHAT_LOG_LINES + (int) ((chatH - CHAT_H) / CHAT_LINE_H);
        int n = Math.min(maxLines, log.size());
        voiceHits.clear();
        for (int i = 0; i < n; i++) {
            ChatLine l = log.get(log.size() - n + i);
            float ly = CHAT_Y + CHAT_INPUT_H + 14 + (n - 1 - i) * CHAT_LINE_H + 15;
            if (l.system) {
                drawRichText(l.text, CHAT_X + 14, ly, new Color(1f, 0.62f, 0.35f, 1f));
            } else {
                Ui.fontSmall.setColor(0.65f, 0.9f, 1f, 1f);
                Ui.fontSmall.draw(batch, l.name + ":", CHAT_X + 14, ly);
                layout.setText(Ui.fontSmall, l.name + ":");
                float tx2 = CHAT_X + 14 + layout.width + 6;
                if (l.voice != null) {
                    // chip nghe lại của tin nhắn thoại: icon play (hoặc stop
                    // đỏ khi đang nghe) + nhãn + độ dài; chạm chip để nghe
                    String label = "Tin nhắn thoại " + voiceDurText(l.voice);
                    layout.setText(Ui.fontSmall, label);
                    float chipW = layout.width + 46;
                    float chipX = tx2, chipY = ly - 15;
                    batch.draw(Ui.roundedRect((int) chipW, 20, 10,
                        new Color(0.10f, 0.17f, 0.26f, 0.97f), GLASS_RIM), chipX, chipY);
                    Ui.faIcon(batch, Ui.fa20, l.voicePlaying ? Ui.FA_STOP : Ui.FA_PLAY,
                        chipX + 15, chipY + 10, l.voicePlaying
                            ? new Color(1f, 0.45f, 0.4f, 1f) : Color.WHITE);
                    Ui.fontSmall.setColor(Color.WHITE);
                    Ui.fontSmall.draw(batch, label, chipX + 29, ly);
                    voiceHits.add(new Object[]{chipX, chipY, chipW, 20f, l});
                } else {
                    drawRichText(l.text, tx2, ly, null);
                }
            }
            // giờ gửi nằm ở góc phải của chính dòng tin nhắn đó (chữ mờ).
            // Nút phóng to nằm ở góc panel nhưng dòng trên cùng cách mép
            // trên đủ xa (và góc nút bo tròn) nên không phải né nữa.
            float timeRight = CHAT_X + CHAT_W - 14;
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.42f);
            layout.setText(Ui.fontSmall, l.time);
            Ui.fontSmall.draw(batch, l.time, timeRight - layout.width, ly);
        }
        if (n == 0) {
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.45f);
            Ui.fontSmall.draw(batch, activeChatTab == TAB_WORLD
                ? "Chưa có tin nhắn thế giới nào." : "Chưa có tin nhắn nào.",
                CHAT_X + 14, CHAT_Y + CHAT_INPUT_H + 35);
        }
        // ô nhập (chừa chỗ cho nút emoji + nút micro + nút Gửi ở cuối)
        float inputW = CHAT_W - 16 - CHAT_SEND_W - 8 - MIC_BTN_W - 6 - EMOJI_BTN_W - 6;
        Color inRim = typing ? new Color(1f, 0.8f, 0.35f, 0.95f) : GLASS_RIM;
        batch.draw(Ui.roundedRect((int) inputW, (int) CHAT_INPUT_H, 10,
            new Color(1f, 1f, 1f, typing ? 0.96f : 0.88f), inRim), CHAT_X + 8, CHAT_Y + 7);
        if (recording) {
            // đang ghi âm: chấm đỏ nhấp nháy + đồng hồ giây ngay trong ô nhập
            int secs = recCount / VOICE_RATE;
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                batch.draw(Ui.roundedRect(12, 12, 6,
                    new Color(0.9f, 0.2f, 0.2f, 1f), new Color(0.9f, 0.2f, 0.2f, 1f)),
                    CHAT_X + 20, CHAT_Y + 19);
            }
            Ui.fontSmall.setColor(0.75f, 0.15f, 0.15f, 1f);
            Ui.fontSmall.draw(batch, "Đang ghi âm 0:" + (secs < 10 ? "0" : "") + secs
                + " — thả micro để gửi", CHAT_X + 42, CHAT_Y + 30);
        } else if (typing) {
            float endX = drawRichText(typeBuffer.toString(), CHAT_X + 20, CHAT_Y + 30,
                new Color(0.1f, 0.1f, 0.12f, 1f));
            Ui.fontSmall.setColor(0.1f, 0.1f, 0.12f, 1f);
            Ui.fontSmall.draw(batch, "|", endX + 1, CHAT_Y + 30);
        } else {
            Ui.fontSmall.setColor(0.35f, 0.38f, 0.42f, 1f);
            Ui.fontSmall.draw(batch, privateTargetId != 0
                ? "Nhắn riêng với " + privateTargetName + "…"
                : "Nhập nội dung", CHAT_X + 20, CHAT_Y + 30);
        }
        // nút mở ô emoji: ảnh emoji cười lăn (rofl) trên nền kính cùng tông
        float micX = CHAT_X + CHAT_W - 8 - CHAT_SEND_W - 6 - MIC_BTN_W;
        float emojiX = micX - 6 - EMOJI_BTN_W;
        batch.draw(Ui.roundedRect((int) EMOJI_BTN_W, (int) CHAT_INPUT_H, 10,
            new Color(0.10f, 0.17f, 0.26f, 0.97f), GLASS_RIM), emojiX, CHAT_Y + 7);
        Texture roflTex = emojiTex("rofl");
        if (roflTex != null) {
            batch.draw(roflTex, emojiX + EMOJI_BTN_W / 2f - 12,
                CHAT_Y + 7 + CHAT_INPUT_H / 2f - 12, 24, 24);
        } else {
            Ui.faIcon(batch, Ui.fa20, Ui.FA_FACE_SMILE,
                emojiX + EMOJI_BTN_W / 2f, CHAT_Y + 7 + CHAT_INPUT_H / 2f, Color.WHITE);
        }
        // nút micro của tin nhắn thoại: bấm giữ để ghi, đỏ lên khi đang ghi
        batch.draw(Ui.roundedRect((int) MIC_BTN_W, (int) CHAT_INPUT_H, 10,
            recording ? new Color(0.55f, 0.12f, 0.12f, 0.97f)
                      : new Color(0.10f, 0.17f, 0.26f, 0.97f), GLASS_RIM), micX, CHAT_Y + 7);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_MICROPHONE,
            micX + MIC_BTN_W / 2f, CHAT_Y + 7 + CHAT_INPUT_H / 2f, Color.WHITE);
        // nút Gửi: ICON máy bay giấy Font Awesome trên nền kính cùng tông
        // panel (chủ dự án chốt: bỏ chữ "Gửi" + bỏ màu cam)
        float sendX = CHAT_X + CHAT_W - 8 - CHAT_SEND_W;
        batch.draw(Ui.roundedRect((int) CHAT_SEND_W, (int) CHAT_INPUT_H, 10,
            new Color(0.10f, 0.17f, 0.26f, 0.97f), GLASS_RIM), sendX, CHAT_Y + 7);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_SEND,
            sendX + CHAT_SEND_W / 2f, CHAT_Y + 7 + CHAT_INPUT_H / 2f, Color.WHITE);
        // nút phóng to / thu nhỏ panel chat: nằm ở GÓC trên phải của panel,
        // xích vào trong — chỉ nhô ra ngoài ~12px (chủ dự án chốt: lên góc
        // nhưng "đừng ra ngoài nhiều quá"); FA expand khi đang nhỏ,
        // FA compress khi đang to
        float ebS = 38;
        float ebX = CHAT_X + CHAT_W - ebS + 12, ebY = CHAT_Y + chatH - ebS + 12;
        batch.draw(Ui.roundedRect((int) ebS, (int) ebS, 9,
            new Color(0.10f, 0.17f, 0.26f, 0.97f), GLASS_RIM), ebX, ebY);
        Ui.faIcon(batch, Ui.fa20, chatExpanded ? Ui.FA_COMPRESS : Ui.FA_EXPAND,
            ebX + ebS / 2f, ebY + ebS / 2f, Color.WHITE);
        // ô emoji (popover): lưới 6×4 ảnh emoji thật ngay trên hàng nhập,
        // neo phải; chạm một emoji là chèn mã :ten: vào ô nhập, panel giữ
        // mở để chèn tiếp
        if (emojiOpen) {
            float[] g = emojiPanelGeom();
            batch.draw(Ui.roundedRect((int) EMOJI_PANEL_W, (int) EMOJI_PANEL_H, 12,
                new Color(0.05f, 0.10f, 0.17f, 0.97f), GLASS_RIM), g[0], g[1]);
            for (int i = 0; i < CHAT_EMOJIS.length; i++) {
                int col = i % 6, row = i / 6;
                float cx = g[0] + 6 + col * EMOJI_CELL + EMOJI_CELL / 2f;
                float cy = g[1] + EMOJI_PANEL_H - 6 - row * EMOJI_CELL - EMOJI_CELL / 2f;
                Texture t = emojiTex(CHAT_EMOJIS[i][0]);
                if (t != null) batch.draw(t, cx - 15, cy - 15, 30, 30);
            }
        }
    }

    /** Hình học ô emoji {px, py} dùng chung cho vẽ và chạm. */
    private float[] emojiPanelGeom() {
        return new float[]{CHAT_X + CHAT_W - 8 - EMOJI_PANEL_W, CHAT_Y + CHAT_INPUT_H + 14};
    }

    /** Trái trên — card glass gọn: avatar trong KHUNG VUÔNG (huy hiệu Lv nằm ở góc
     *  khung), tên nhân vật bên phải, dưới tên là hàng tiền tệ GỌN dùng đúng asset
     *  tiền của game gốc (myinfo/icon0 = đồng xu bạc của Xu, icon1 = thỏi vàng của
     *  Lượng — đúng cặp icon hàm paintMoney của client gốc dùng). Không hiện vị trí/map.
     *  Số tiền/cấp hiện là số MẪU cho tới đợt 2 (tài khoản + server). */
    private void drawPlayerCard() {
        float cardY = Ui.H - 14 - CARD_H;
        batch.draw(Ui.roundedRect((int) CARD_W, (int) CARD_H, 16, GLASS_FILL, GLASS_RIM), CARD_X, cardY);
        // khung VUÔNG ngoài avatar
        float avX = CARD_X + 12, avY = cardY + 12, avS = 80;
        batch.draw(Ui.roundedRect((int) avS, (int) avS, 6,
            new Color(0.35f, 0.55f, 0.75f, 0.35f), GLASS_RIM), avX, avY);
        // chân dung: vẽ doll thật, cắt (scissor) theo khung — chỉ lấy đầu + vai
        int[] b = playerDoll.bounds(playerAnim.frameIndex());
        float unit = 4.4f;
        float feetX = avX + avS / 2f - ((b[0] + b[2]) / 2f) * unit;
        float feetY = avY + avS + b[1] * unit; // đỉnh doll chạm đúng nóc khung
        Rectangle clip = new Rectangle(avX + 2, avY + 2, avS - 4, avS - 4);
        Rectangle scissors = new Rectangle();
        batch.flush();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            game.dollRenderer.draw(batch, playerDoll, playerAnim.frameIndex(), feetX, feetY, unit, false);
            batch.flush();
            ScissorStack.popScissors();
        }
        // huy hiệu Lv NHỎ ở góc dưới của khung vuông (chồng nhẹ lên cạnh khung,
        // không che avatar — chữ thu 0.7 lần, đo/vẽ xong trả scale về 1)
        Ui.fontSmall.getData().setScale(0.7f);
        layout.setText(Ui.fontSmall, displayLevel);
        float badgeW = layout.width + 10, badgeH = 17;
        float badgeX = avX + avS - badgeW + 5, badgeY = avY - 5;
        batch.draw(Ui.roundedRect((int) badgeW, (int) badgeH, 6,
            new Color(0.10f, 0.08f, 0.02f, 0.92f), new Color(1f, 0.8f, 0.35f, 0.9f)), badgeX, badgeY);
        Ui.fontSmall.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontSmall.draw(batch, displayLevel, badgeX + 5, badgeY + 13.5f);
        Ui.fontSmall.getData().setScale(1f);
        // tên nhân vật
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, displayName, CARD_X + 106, cardY + CARD_H - 24);
        // tiền tệ GỌN một hàng: [icon xu] số · [icon lượng] số — icon là asset gốc
        Texture iconXu = game.texture("myinfo/icon0.png");
        Texture iconLuong = game.texture("myinfo/icon1.png");
        float cy = cardY + 20;
        batch.draw(iconXu, CARD_X + 106, cy, 21, 22);
        Ui.fontSmall.setColor(Color.WHITE);
        Ui.fontSmall.draw(batch, displayXu, CARD_X + 133, cy + 17);
        layout.setText(Ui.fontSmall, displayXu);
        float x2 = CARD_X + 133 + layout.width + 14;
        batch.draw(iconLuong, x2, cy + 2, 24, 17);
        Ui.fontSmall.draw(batch, displayLuong, x2 + 30, cy + 17);
    }

    /** Phải trên — nút burger glass (3 vạch vẽ bằng hình, không emoji). */
    private void drawBurger() {
        batch.draw(Ui.roundedRect((int) BURGER_S, (int) BURGER_S, 14, GLASS_FILL, GLASS_RIM), BURGER_X, BURGER_Y);
        float bx = BURGER_X + 14;
        for (int i = 0; i < 3; i++) {
            batch.draw(Ui.roundedRect(28, 5, 2, new Color(1f, 1f, 1f, 0.92f), null),
                bx, BURGER_Y + BURGER_S - 20 - i * 11);
        }
        // chấm đỏ trên nút burger khi có tin nhắn chưa đọc
        if (!menuOpen && totalUnread() > 0) {
            batch.setColor(0.92f, 0.25f, 0.2f, 1f);
            batch.draw(circleTexture(), BURGER_X + BURGER_S - 15, BURGER_Y + BURGER_S - 15, 13, 13);
            batch.setColor(Color.WHITE);
        }
    }

    /** Panel kính mở từ nút burger: danh sách CHỌN MAP (toàn bộ map dựng được
     *  bằng dải imagemap theo maps.json), phân trang 8 map/trang, map đang đứng
     *  sáng viền vàng. Chạm tên map để chuyển. (Vị trí/tọa độ KHÔNG đưa vào menu
     *  theo yêu cầu của chủ dự án — chỉ tên map.) */
    private void drawBurgerPanel() {
        float px = MENU_PX, pw = MENU_PW, ph = MENU_PH, py = MENU_PY;
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 14, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Ui.fontSmall.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontSmall.draw(batch, "MENU", px + 18, py + ph - 24);
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int col = i % 3, row = i / 3;
            float cx = px + 10 + col * MENU_CELL;
            float cy = py + ph - MENU_HEADER - (row + 1) * MENU_CELL_H + 6;
            // Chỉ asset + nhãn, KHÔNG đè ô kính lên icon nữa (yêu cầu của chủ dự án)
            float is = 46f;
            Texture icon = game.texture("iconmenu/" + MENU_ITEMS[i][0] + ".png");
            batch.draw(icon, cx + MENU_CELL / 2f - is / 2f, cy + MENU_CELL_H - 8 - is - 8, is, is);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.92f);
            layout.setText(Ui.fontSmall, MENU_ITEMS[i][1]);
            Ui.fontSmall.draw(batch, MENU_ITEMS[i][1], cx + MENU_CELL / 2f - layout.width / 2f, cy + 24);
            // huy hiệu số tin chưa đọc trên ô Thư (nummsg của gốc)
            if (i == 1 && totalUnread() > 0) {
                float bx2 = cx + MENU_CELL - 34, by2 = cy + MENU_CELL_H - 36;
                batch.setColor(0.92f, 0.25f, 0.2f, 1f);
                batch.draw(circleTexture(), bx2, by2, 26, 26);
                batch.setColor(Color.WHITE);
                Ui.centered(batch, Ui.fontSmall, String.valueOf(Math.min(totalUnread(), 99)),
                    bx2 + 13, by2 + 18, Color.WHITE);
            }
        }
    }

    // ---------- Panel chọn KHU (ParkListSrc của gốc, vẽ theo tông kính) ----------
    private static final float BOARD_PW = 560f, BOARD_PH = 336f;
    private static final float BOARD_PX = (Ui.W - BOARD_PW) / 2f, BOARD_PY = (Ui.H - BOARD_PH) / 2f;
    private static final float BOARD_CELL_W = 100f, BOARD_CELL_H = 88f;

    private float boardCellX(int col) { return BOARD_PX + 14 + col * (BOARD_CELL_W + 8); }
    private float boardCellY(int row) { return BOARD_PY + BOARD_PH - 84 - (row + 1) * BOARD_CELL_H - row * 8; }

    private void drawBoardPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) BOARD_PW, (int) BOARD_PH, 16, GLASS_FILL_DARK, GLASS_RIM),
            BOARD_PX, BOARD_PY);
        Ui.font.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.font.draw(batch, "CHỌN KHU — " + (mapDef.name != null ? mapDef.name : ("MAP " + mapDef.id)),
            BOARD_PX + 22, BOARD_PY + BOARD_PH - 20);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM),
            BOARD_PX + BOARD_PW - 54, BOARD_PY + BOARD_PH - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, BOARD_PX + BOARD_PW - 36, BOARD_PY + BOARD_PH - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        if (boardStatus == null) {
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.85f);
            String s = "Đang tải danh sách khu…";
            layout.setText(Ui.fontSmall, s);
            Ui.fontSmall.draw(batch, s, BOARD_PX + BOARD_PW / 2f - layout.width / 2f,
                BOARD_PY + BOARD_PH / 2f);
            return;
        }
        for (int k = 0; k < boardStatus.length && k < 10; k++) {
            int col = k % 5, row = k / 5;
            float cx = boardCellX(col), cy = boardCellY(row);
            int st = boardStatus[k];
            Color fill = st == 0 ? new Color(0.32f, 0.09f, 0.09f, 0.88f)
                : st == 1 ? new Color(0.34f, 0.23f, 0.05f, 0.88f)
                : new Color(0.07f, 0.24f, 0.12f, 0.88f);
            Color rim = k == currentArea ? new Color(0.45f, 0.95f, 1f, 0.95f) : GLASS_RIM;
            batch.draw(Ui.roundedRect((int) BOARD_CELL_W, (int) BOARD_CELL_H, 10, fill, rim), cx, cy);
            Ui.font.setColor(1f, 1f, 1f, 0.95f);
            String t = "KHU " + k;
            layout.setText(Ui.font, t);
            Ui.font.draw(batch, t, cx + BOARD_CELL_W / 2f - layout.width / 2f, cy + BOARD_CELL_H - 16);
            String s = k == currentArea ? "ĐANG Ở"
                : st == 0 ? "ĐẦY" : st == 1 ? "ĐÔNG" : "VẮNG";
            Ui.fontSmall.setColor(k == currentArea ? new Color(0.45f, 0.95f, 1f, 1f)
                : st == 0 ? new Color(1f, 0.45f, 0.4f, 1f)
                : st == 1 ? new Color(1f, 0.8f, 0.35f, 1f)
                : new Color(0.45f, 1f, 0.6f, 1f));
            layout.setText(Ui.fontSmall, s);
            Ui.fontSmall.draw(batch, s, cx + BOARD_CELL_W / 2f - layout.width / 2f, cy + 26);
        }
    }

    // ===== BẢN ĐỒ THÀNH PHỐ ZOOM/KÉO (chủ dự án chốt 2026-11-01, thay
    // bản đồ panel cũ): toàn màn hình trên tranh minimap gốc 1600×900;
    // kéo để xem xung quanh, nút +/− (và lăn chuột) để zoom, chạm vùng để
    // chọn điểm đến rồi bấm "Di chuyển đến" mới bay tới (đích REGION_DEST
    // theo MapScr.doJoin của gốc, đi bằng loadMap + spawnPlayer như cũ). =====
    private float mapScale = 0.8f, mapCamX = 800f, mapCamY = 450f;
    private int selRegion = -1;
    private int mapPointer = -1;
    private float mapDragStartX, mapDragStartY, mapDragCamX, mapDragCamY, mapDragMoved;
    private static final String[] REGION_DESC = {
        "Khu dân cư yên bình — nhà của bạn ở đây.",
        "Rừng xanh, hồ nước và thảm cỏ mát.",
        "Cổng trời của thị trấn — đi xa từ đây.",
        "Mini-game, lễ hội và khu vui chơi.",
        "Chợ và các cửa hàng thời trang.",
        "Tản bộ, hẹn hò bên hồ nước.",
        "Vùng ven yên tĩnh, đường ra nông trại.",
        "Trồng trọt, chăn nuôi và thu hoạch."};

    /** Tâm vùng theo không gian ảnh gốc 1600×900 (y tính từ trên xuống). */
    private float[] regionPoint(int r) {
        int[] cell = regionCells.get(r);
        return new float[]{(cell[0] + 0.5f) / 50f * 1600f, (cell[1] + 0.5f) / 26f * 900f};
    }

    private float mapSx(float mx) { return Ui.W / 2f + (mx - mapCamX) * mapScale; }
    private float mapSy(float my) { return Ui.H / 2f - (my - mapCamY) * mapScale; }

    /** Vùng chứa map hiện tại, −1 nếu map hiện tại không phải đích vùng nào. */
    private int currentRegion() {
        if (mapDef == null) return -1;
        for (int r = 0; r < REGION_DEST.length; r++) {
            if (mapDef.id == REGION_DEST[r]) return r;
        }
        return -1;
    }

    /** Mở bản đồ: toàn cảnh 100%, chọn sẵn vùng đang đứng (nếu có). */
    public void openCityMap() {
        cityMapOpen = true;
        mapScale = 0.8f;
        mapCamX = 800f;
        mapCamY = 450f;
        selRegion = currentRegion();
        mapPointer = -1;
    }

    /** Kẹp tâm camera: không cho kéo bản đồ tuột khỏi màn hình (chừa mép nhỏ). */
    private void clampMapCam() {
        float hw = Ui.W / 2f / mapScale, hh = Ui.H / 2f / mapScale;
        if (1600f * mapScale <= Ui.W) mapCamX = 800f;
        else mapCamX = MathUtils.clamp(mapCamX, hw - 90, 1600f - hw + 90);
        if (900f * mapScale <= Ui.H) mapCamY = 450f;
        else mapCamY = MathUtils.clamp(mapCamY, hh - 50, 900f - hh + 50);
    }

    private void zoomMap(float factor) {
        mapScale = MathUtils.clamp(mapScale * factor, 0.8f, 2.4f);
        clampMapCam();
    }

    /** Sân khấu kiểm chứng bản đồ: mode 0 = toàn cảnh đã chọn Sân bay,
     *  mode 1 = phóng to vào Sân bay. */
    public void debugMapZoomMock(int mode) {
        bagPanel = false;
        chestPanel = false;
        openCityMap();
        selRegion = 2; // Sân bay
        if (mode == 1) {
            float[] p = regionPoint(2);
            mapScale = 1.9f;
            mapCamX = p[0];
            mapCamY = p[1];
            clampMapCam();
        }
    }

    private void drawCityMap() {
        if (minimapTex == null) minimapTex = game.texture("minimap.png");
        int cur = currentRegion();
        batch.setColor(0.03f, 0.05f, 0.09f, 1f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        float dx = Ui.W / 2f - mapCamX * mapScale;
        float dy = Ui.H / 2f + mapCamY * mapScale - 900f * mapScale;
        batch.draw(minimapTex, dx, dy, 1600f * mapScale, 900f * mapScale);
        // đường chấm tuyến: vùng đang đứng -> vùng đang chọn (cài đặt
        // "Chỉ đường: ẩn" thì không vẽ — tương đương ẩn PopupName của gốc)
        if (com.avatardk.game.data.GameSettings.showDir
            && cur >= 0 && selRegion >= 0 && selRegion != cur && selRegion < regionCells.size()) {
            float[] a = regionPoint(cur), b = regionPoint(selRegion);
            float ax = mapSx(a[0]), ay = mapSy(a[1]), bx = mapSx(b[0]), by = mapSy(b[1]);
            float dist = (float) Math.hypot(bx - ax, by - ay);
            int dots = (int) (dist / 30f);
            batch.setColor(1f, 0.62f, 0.18f, 0.95f);
            for (int i = 1; i < dots; i++) {
                float t = (float) i / dots;
                batch.draw(circleTexture(), ax + (bx - ax) * t - 4, ay + (by - ay) * t - 4, 8, 8);
            }
            batch.setColor(Color.WHITE);
        }
        // các điểm vùng: chấm + pill tên; vùng chọn có thêm ghim cam
        for (int r = 0; r < regionCells.size() && r < REGION_NAMES.length; r++) {
            float[] p = regionPoint(r);
            float sx = mapSx(p[0]), sy = mapSy(p[1]);
            if (sx < -90 || sx > Ui.W + 90 || sy < -70 || sy > Ui.H + 70) continue;
            boolean isCur = r == cur, sel = r == selRegion;
            float dotR = isCur ? 12 : 8; // vùng đang đứng: chấm to hơn hẳn
            batch.setColor(isCur ? new Color(0.30f, 0.85f, 0.35f, 1f)
                : sel ? new Color(1f, 0.55f, 0.12f, 1f) : new Color(0.08f, 0.12f, 0.18f, 0.9f));
            batch.draw(circleTexture(), sx - dotR, sy - dotR, dotR * 2, dotR * 2);
            batch.setColor(Color.WHITE);
            batch.draw(ringTexture(), sx - dotR, sy - dotR, dotR * 2, dotR * 2);
            float inR = isCur ? 5 : 3;
            batch.draw(circleTexture(), sx - inR, sy - inR, inR * 2, inR * 2);
            String label = REGION_NAMES[r];
            float tw = Ui.textWidth(Ui.fontSmall, label);
            float pw = tw + 24, ph = 32;
            float lx = MathUtils.clamp(sx - pw / 2f, 8, Ui.W - 8 - pw);
            float ly = sy + 16;
            if (ly + ph > Ui.H - 8) ly = sy - 16 - ph;
            batch.draw(Ui.roundedRect((int) pw, (int) ph, 10,
                sel ? new Color(0.98f, 0.55f, 0.16f, 0.95f) : new Color(0.05f, 0.09f, 0.14f, 0.82f), GLASS_RIM), lx, ly);
            Ui.centered(batch, Ui.fontSmall, label, lx + pw / 2f, ly + 22, Color.WHITE);
            if (isCur) {
                // nhãn vị trí của mình: pill nền tối viền xanh + chữ xanh
                // sáng cỡ fontSmall — chữ xanh trần trên nền cỏ xanh của
                // tranh là chìm hẳn, không ai đọc được (chủ dự án bắt lỗi)
                String cap = "Bạn đang ở đây";
                float capW = Ui.textWidth(Ui.fontSmall, cap) + 24;
                float capX = MathUtils.clamp(sx - capW / 2f, 8, Ui.W - 8 - capW);
                float capY = sy - 48;
                batch.draw(Ui.roundedRect((int) capW, 30, 10,
                    new Color(0.03f, 0.09f, 0.04f, 0.94f), new Color(0.45f, 1f, 0.55f, 0.95f)), capX, capY);
                Ui.centered(batch, Ui.fontSmall, cap, capX + capW / 2f, capY + 21,
                    new Color(0.55f, 1f, 0.62f, 1f));
            }
            if (sel) {
                batch.setColor(0.98f, 0.55f, 0.16f, 1f);
                batch.draw(circleTexture(), sx - 17, ly + ph + 6, 34, 34);
                batch.setColor(Color.WHITE);
                Ui.faIcon(batch, Ui.fa20, Ui.FA_LOCATION_DOT, sx, ly + ph + 23, Color.WHITE);
            }
        }
        // khối tiêu đề + gợi ý thao tác ở góc trên trái
        String title = "Bản đồ thị trấn";
        String hint = "Kéo để di chuyển • Chạm vùng để chọn điểm đến";
        float bw = Math.max(Ui.textWidth(Ui.font, title), Ui.textWidth(Ui.fontTiny, hint)) + 40;
        batch.draw(Ui.roundedRect((int) bw, 80, 14, new Color(0.05f, 0.09f, 0.14f, 0.85f), GLASS_RIM),
            22, Ui.H - 102);
        Ui.font.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.font.draw(batch, title, 42, Ui.H - 44);
        Ui.fontTiny.setColor(1f, 1f, 1f, 0.75f);
        Ui.fontTiny.draw(batch, hint, 42, Ui.H - 76);
        // nút đóng: ô vuông đỏ chuẩn như mọi panel, góc trên phải
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), Ui.W - 54, Ui.H - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, Ui.W - 36, Ui.H - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        // cụm zoom ở mép phải: + / mức zoom / −, dưới là nút về vị trí của bạn
        float zx = Ui.W - 80, zy = 300;
        batch.draw(Ui.roundedRect(58, 176, 16, new Color(0.05f, 0.09f, 0.14f, 0.85f), GLASS_RIM), zx, zy);
        Ui.faIcon(batch, Ui.fa26, Ui.FA_PLUS, zx + 29, zy + 140, Color.WHITE);
        Ui.centered(batch, Ui.fontTiny, Math.round(mapScale / 0.8f * 100f) + "%", zx + 29, zy + 93,
            new Color(1f, 1f, 1f, 0.8f));
        Ui.faIcon(batch, Ui.fa26, Ui.FA_MINUS, zx + 29, zy + 38, Color.WHITE);
        batch.draw(Ui.roundedRect(58, 58, 16, new Color(0.05f, 0.09f, 0.14f, 0.85f), GLASS_RIM), zx, zy - 74);
        Ui.faIcon(batch, Ui.fa26, Ui.FA_LOCATION_CROSSHAIRS, zx + 29, zy - 45,
            new Color(0.45f, 0.9f, 1f, 1f));
        // thẻ điểm đến ở đáy giữa
        if (selRegion >= 0 && selRegion < REGION_NAMES.length) {
            float cw = 620, ch = 106, cx = (Ui.W - cw) / 2f, cy = 22;
            batch.draw(Ui.roundedRect((int) cw, (int) ch, 16,
                new Color(0.06f, 0.10f, 0.16f, 0.94f), GLASS_RIM), cx, cy);
            Ui.font.setColor(1f, 0.85f, 0.45f, 1f);
            Ui.font.draw(batch, REGION_NAMES[selRegion], cx + 24, cy + ch - 26);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.82f);
            Ui.fontSmall.draw(batch, REGION_DESC[selRegion], cx + 24, cy + 34);
            if (selRegion == cur) {
                batch.draw(Ui.roundedRect(190, 52, 12, new Color(0.16f, 0.45f, 0.20f, 0.95f), GLASS_RIM),
                    cx + cw - 214, cy + 27);
                Ui.centered(batch, Ui.fontSmall, "Bạn đang ở đây", cx + cw - 119, cy + 61, Color.WHITE);
            } else {
                batch.draw(Ui.gradientRect(190, 52, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT),
                    cx + cw - 214, cy + 27);
                Ui.centered(batch, Ui.fontSmall, "Di chuyển đến", cx + cw - 119, cy + 61, Color.WHITE);
            }
        }
    }

    /** Chạm xuống khi bản đồ mở: các nút ăn ngay; còn lại bắt đầu kéo.
     *  Chọn vùng xảy ra ở touchUp khi chạm nhẹ không kéo (mẫu sheet biểu cảm). */
    private void cityMapTouchDown(float x, float y, int pointer) {
        if (x >= Ui.W - 54 && x <= Ui.W - 18 && y >= Ui.H - 54 && y <= Ui.H - 18) {
            cityMapOpen = false;
            mapPointer = -1;
            return;
        }
        float zx = Ui.W - 80, zy = 300;
        if (x >= zx && x <= zx + 58 && y >= zy + 118 && y <= zy + 176) { zoomMap(1.3f); return; }
        if (x >= zx && x <= zx + 58 && y >= zy && y <= zy + 58) { zoomMap(1f / 1.3f); return; }
        if (x >= zx && x <= zx + 58 && y >= zy - 74 && y <= zy - 16) {
            int cur = currentRegion();
            if (cur >= 0) {
                float[] p = regionPoint(cur);
                mapScale = Math.max(mapScale, 1.5f);
                mapCamX = p[0];
                mapCamY = p[1];
                clampMapCam();
            } else {
                mapScale = 0.8f;
                mapCamX = 800f;
                mapCamY = 450f;
            }
            return;
        }
        if (selRegion >= 0 && selRegion != currentRegion()) {
            float cw = 620, cx = (Ui.W - cw) / 2f, cy = 22;
            if (x >= cx + cw - 214 && x <= cx + cw - 24 && y >= cy + 27 && y <= cy + 79) {
                int dest = REGION_DEST[selRegion];
                cityMapOpen = false;
                mapPointer = -1;
                loadMap(findMap(dest));
                spawnPlayer();
                return;
            }
        }
        mapPointer = pointer;
        mapDragStartX = x;
        mapDragStartY = y;
        mapDragCamX = mapCamX;
        mapDragCamY = mapCamY;
        mapDragMoved = 0f;
    }


    // ---------- Menu NPC (gói −59, mở bằng gói −61 khi tương tác NPC) ----
    // Server gửi danh sách lựa chọn dạng chữ; người chơi chạm một mục thì
    // client gửi lại vị trí chọn qua chính gói −59 (chiều đi) và server tự
    // chạy hành động tương ứng (mở shop, mở hướng dẫn, đóng...). Panel vẽ
    // theo ngôn ngữ kính của các panel đã chốt, neo giữa màn hình.
    private boolean npcMenuOpen = false;
    /** Menu NPC mở cục bộ khi offline (hiện chỉ lái buôn): chọn mục thì
     *  chạy tại chỗ thay vì gửi gói chọn menu lên server. */
    private boolean npcMenuLocal = false;
    private int npcMenuUserId, npcMenuId;
    private String npcMenuTitle = "";
    private final java.util.List<String> npcMenuItems = new ArrayList<>();
    private static final float NM_W = 400f, NM_HEAD = 56f, NM_ROW = 56f;

    private String npcDisplayName(int code) {
        if (code == NPC_CUA_HANG) return "Cửa Hàng";
        if (code == NPC_LAI_BUON) return "Lái buôn";
        for (com.avatardk.game.data.NpcData nd : game.data.npcs) {
            if (nd.id == code) return nd.name.replace('.', ' ');
        }
        return "NPC";
    }

    private float npcMenuH() {
        return NM_HEAD + npcMenuItems.size() * NM_ROW + 16;
    }

    private void drawNpcMenu() {
        float w = NM_W, h = npcMenuH();
        float px = (Ui.W - w) / 2f, py = (Ui.H - h) / 2f;
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) w, (int) h, 16,
            new Color(0.07f, 0.11f, 0.17f, 0.97f), GLASS_RIM), px, py);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, npcMenuTitle, px + 24, py + h - 22);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + w - 54, py + h - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + w - 36, py + h - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        for (int i = 0; i < npcMenuItems.size(); i++) {
            float ry = py + h - NM_HEAD - (i + 1) * NM_ROW + 6;
            batch.draw(Ui.roundedRect((int) w - 32, (int) NM_ROW - 10, 12,
                new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), px + 16, ry);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
            Ui.fontSmall.draw(batch, npcMenuItems.get(i), px + 36, ry + 31);
        }
    }

    /** Sân khấu chụp: hiện menu NPC Cửa Hàng như server sẽ gửi qua −59. */
    public void debugOpenNpcMenu() {
        npcMenuUserId = NPC_ID_ADD + NPC_CUA_HANG;
        npcMenuId = 0;
        npcMenuTitle = "Cửa Hàng";
        npcMenuItems.clear();
        npcMenuItems.add("Mua sắm");
        npcMenuItems.add("Hướng dẫn");
        npcMenuItems.add("Thoát");
        npcMenuOpen = true;
    }

    private void npcMenuTouch(float x, float y) {
        float w = NM_W, h = npcMenuH();
        float px = (Ui.W - w) / 2f, py = (Ui.H - h) / 2f;
        if (x >= px + w - 54 && x <= px + w - 18 && y >= py + h - 54 && y <= py + h - 18) {
            npcMenuOpen = false;
            npcMenuLocal = false;
            return;
        }
        if (x < px || x > px + w || y < py || y > py + h) {
            npcMenuOpen = false;
            npcMenuLocal = false;
            return;
        }
        for (int i = 0; i < npcMenuItems.size(); i++) {
            float ry = py + h - NM_HEAD - (i + 1) * NM_ROW + 6;
            if (x >= px + 16 && x <= px + w - 16 && y >= ry && y <= ry + NM_ROW - 10) {
                npcMenuOpen = false;
                if (npcMenuLocal) {
                    npcMenuLocal = false;
                    laiBuonLocalSelect(i);
                    return;
                }
                if (game.connection != null) {
                    try {
                        AvatarProtocol.selectMenuOption(game.connection,
                            npcMenuUserId, npcMenuId, i);
                    } catch (Exception e) {
                        // mất kết nối: menu đã đóng, server không chạy gì thêm
                    }
                }
                return;
            }
        }
    }


    // ---------- LÁI BUÔN (NPC 850, map 26) + PANEL TAB HƯỚNG DẪN ----------
    // Gốc: nói chuyện (gói −61) → server trả menu −59 "Điểm Danh /
    // Xem hướng dẫn / Thoát"; "Điểm Danh" phía server gốc là stub chỉ
    // trả tin hệ thống "đang xây dựng" (gói −9), "Xem hướng dẫn" trả
    // tab thông tin qua gói −58 (CUSTOM_TAB). Online chạy đúng luồng
    // đó; offline (bản web chơi thử) mở menu cục bộ cùng nội dung để
    // xem/chạm được panel.

    /** Nội dung tab hướng dẫn của lái buôn — nguyên văn server gốc. */
    private static final String LAI_BUON_GUIDE =
        "Đăng nhập mỗi ngày để nhận quà.\n"
        + "Dùng điểm chuyên cần để nhận được những món quà có giá trị trong tương lai";

    private void openLaiBuonLocalMenu() {
        npcMenuLocal = true;
        npcMenuUserId = NPC_ID_ADD + NPC_LAI_BUON;
        npcMenuId = 0;
        npcMenuTitle = "Lái buôn";
        npcMenuItems.clear();
        npcMenuItems.add("Điểm Danh");
        npcMenuItems.add("Xem hướng dẫn");
        npcMenuItems.add("Thoát");
        npcMenuOpen = true;
    }

    private void laiBuonLocalSelect(int i) {
        if (i == 0) {
            // đúng stub của server gốc: chỉ báo "đang xây dựng"
            addChatLog(TAB_SYSTEM, null, "đang xây dựng", true);
            chatBubble = "đang xây dựng";
            chatTimer = 3f;
        } else if (i == 1) {
            openGuide("Hướng dẫn", LAI_BUON_GUIDE);
        }
    }

    // ----- Panel tab thông tin (gói −58 CUSTOM_TAB của gốc) -----
    private boolean guideOpen = false;
    private String guideTitle = "";
    private String guideText = "";
    private static final float GP_W = 700f, GP_H = 500f;
    private static final float GP_PX = (Ui.W - GP_W) / 2f, GP_PY = (Ui.H - GP_H) / 2f;

    private void openGuide(String title, String text) {
        guideTitle = title != null ? title : "";
        guideText = text != null ? text : "";
        guideOpen = true;
    }

    /** Ngắt dòng theo bề rộng đo thật của font (giữ nguyên ngắt dòng \n). */
    private java.util.List<String> wrapGuideLines() {
        java.util.List<String> lines = new ArrayList<>();
        float maxW = GP_W - 56f;
        for (String para : guideText.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String cand = cur.length() == 0 ? word : cur + " " + word;
                layout.setText(Ui.fontSmall, cand);
                if (layout.width > maxW && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(cand);
                }
            }
            lines.add(cur.toString());
        }
        return lines;
    }

    private void drawGuidePanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        // nền đặc như panel menu NPC (0.97): bản đầu dùng GLASS_FILL_DARK
        // 0.78 nên nền cảnh lọt qua rất rõ ở vùng trống
        batch.draw(Ui.roundedRect((int) GP_W, (int) GP_H, 16,
            new Color(0.07f, 0.11f, 0.17f, 0.97f), GLASS_RIM), GP_PX, GP_PY);
        drawFarmPanelHeader(GP_PX, GP_PY, GP_W, GP_H, Ui.FA_CIRCLE_INFO,
            new Color(0.13f, 0.42f, 0.60f, 0.95f), guideTitle, "Thông tin từ NPC");
        java.util.List<String> lines = wrapGuideLines();
        // dòng đầu ở GP_H−122: mực chữ (tới +5 trên baseline) vẫn nằm
        // trong vùng clip [GP_PY+10, GP_PY+GP_H−90] — bản đầu bị clip
        // cắt mất đỉnh dòng đầu
        float top = GP_PY + GP_H - 122f;
        boolean clipped = pushPanelClip(GP_PX + 6, GP_PY + 10, GP_W - 12, GP_H - 90);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.94f);
        for (int i = 0; i < lines.size(); i++) {
            float y = top - i * 25f;
            if (y < GP_PY + 44) break;
            Ui.fontSmall.draw(batch, lines.get(i), GP_PX + 28, y);
        }
        Ui.fontSmall.setColor(Color.WHITE);
        if (clipped) popPanelClip();
    }

    private void guideTouch(float x, float y) {
        if (x >= GP_PX + GP_W - 54 && x <= GP_PX + GP_W - 18
                && y >= GP_PY + GP_H - 54 && y <= GP_PY + GP_H - 18) {
            guideOpen = false;
            return;
        }
        if (x < GP_PX || x > GP_PX + GP_W || y < GP_PY || y > GP_PY + GP_H) {
            guideOpen = false;
        }
        // chạm trong panel: nuốt, không làm gì thêm
    }

    /** Harness: đứng cạnh lái buôn ở map 26 rồi bấm Tương tác (offline
     *  sẽ mở menu cục bộ của ổng). */
    public void debugShowLaiBuon() {
        loadMap(findMap(26));
        debugBusSkip();
        for (NpcFigure f : npcFigures) {
            if (f.data.id == NPC_LAI_BUON) {
                // đứng sát phải–dưới NPC: findNearestNpc đo tới tọa độ
                // dữ liệu gốc của NPC (hệ y trên–xuống) nên phải đặt
                // người chơi lệch xuống mới lọt tầm tương tác 80
                playerX = f.feetX / AvatarDkGame.HD + 14f;
                playerY = f.feetY / AvatarDkGame.HD - 6f;
                break;
            }
        }
        interact();
    }

    /** Harness: chọn mục "Xem hướng dẫn" trong menu lái buôn cục bộ. */
    public void debugLaiBuonGuide() {
        if (npcMenuOpen && npcMenuLocal) {
            npcMenuOpen = false;
            npcMenuLocal = false;
            laiBuonLocalSelect(1);
        }
    }

    /** Harness: đặt người chơi ở miệng đường phía tây nông trại để
     *  kiểm chứng tự chuyển sang map 26 khi đi bộ ra. */
    public void debugPlaceAtFarmExit() {
        playerX = 49f;
        playerY = 130f;
    }

    // ----- Chuyển map ở mép đường: nông trại ↔ đường ra (26) ↔ khu 1 -----
    // Gốc ra/vào nông trại bằng cách ĐI BỘ qua miệng đường (FarmScr gửi
    // doJoinPark(25) khi ra đường); bản port trước đây chỉ nhảy map qua
    // bản đồ thành phố nên map 26 không tới được. Chỉ 3 map này nối
    // nhau bằng mép đường; các map khác vẫn đi qua bản đồ thành phố.
    private float edgeCd = 0f;

    private void updateEdgeTransitions(float dt) {
        if (edgeCd > 0f) {
            edgeCd -= dt;
            return;
        }
        if (mapDef == null || anyModalOpen()) return;
        if (mapDef.id == 25) {
            // miệng đường phía tây nông trại (cột ô 0–1, nửa trên cảnh —
            // nơi bếp/kho đứng) → sang đường ra nông trại
            if (playerX <= 50f && playerY >= 110f) {
                gotoMap(26, -70f, 130f);
            }
        } else if (mapDef.id == 26) {
            float eastX = worldW / AvatarDkGame.HD - 50f;
            if (playerX >= eastX) {
                gotoMap(25, 70f, 130f); // mép đông đường ra = cổng nông trại
            } else if (playerX <= 50f) {
                loadMap(findMap(1)); // mép tây = về Khu Mặt trời
                spawnPlayer();
                edgeCd = 1.2f;
            }
        }
    }

    /** Sang map kề qua mép đường: spawnX âm = lùi từ mép phải vào. */
    private void gotoMap(int mapId, float spawnX, float spawnY) {
        loadMap(findMap(mapId));
        playerX = spawnX < 0 ? worldW / AvatarDkGame.HD + spawnX : spawnX;
        playerY = spawnY;
        if (!canStand(playerX, playerY)) {
            boolean found = false;
            for (int r = 1; r <= 6 && !found; r++) {
                for (int dx = -r; dx <= r && !found; dx++) {
                    for (int dy = -r; dy <= r && !found; dy++) {
                        float tx = playerX + dx * 24, ty = playerY + dy * 24;
                        if (canStand(tx, ty)) {
                            playerX = tx;
                            playerY = ty;
                            found = true;
                        }
                    }
                }
            }
        }
        edgeCd = 1.2f;
    }

    /** Có panel modal nào đang mở không (đang mở thì không chuyển map). */
    private boolean anyModalOpen() {
        return npcMenuOpen || guideOpen || shopOpen || bagPanel || chestPanel
            || charPanel || boardOpen || cityMapOpen || menuOpen || msgOpen
            || mailOpen || friendOpen || settingsOpen || musicOpen
            || farmSeedOpen || farmFertOpen || farmLandOpen || farmStoreOpen
            || farmShopOpen || farmCookOpen || farmAnimalSellOpen
            || panelPlayer != null || confirmText != null || exitConfirmOpen;
    }


    // ===== PANEL SHOP (nối thật 2026-11-01 theo mockup user đã duyệt) =====
    // Điểm mở ĐÚNG GỐC (user sửa 2026-11-01: "shop là phải đến shop"):
    // người chơi tới gặp NPC Cửa Hàng (mã 761, map 28), bấm Tương tác →
    // client gửi gói −61 → server trả menu NPC qua gói −59 → chọn mục
    // shop → server gửi gói −49 kèm DANH SÁCH HÀNG (món có sell 8/14,
    // PartManager.Shop0 phía server) → panel mở theo đúng danh sách đó.
    // Phân mục theo zOrder đúng luật của gốc (MapScr.onOpenShop của menu
    // tặng quà): 20 Áo, 10 Quần, 52/53/5 Trang sức, 60 Nón, 70 Cầm tay;
    // bản port tách thêm 50 thành Tóc và gọi 70 là Phương tiện như
    // mockup đã chốt — vẫn thuần theo zOrder, không món nào lạc mục.
    // Mặc thử chép đúng setAvatarShop của gốc: lấy bộ đang mặc, thay món
    // cùng zOrder bằng món đang chọn (chưa có thì thêm vào). Mua bằng
    // gói −24: server trừ tiền theo loại đã chọn (bản port đã sửa quirk
    // gốc luôn trừ Xu), tự mặc món mới, món cũ cùng loại trả về giỏ,
    // phát −48 cho cả khu; tiền cập nhật qua gói −24/−33.
    private boolean shopOpen = false;
    private PaperDoll shopTryDoll;
    private String shopTitle = "Cửa hàng";
    private int shopTab = 0;
    private int shopSel = 0;
    private float shopScroll = 0f;
    private int shopPointer = -1;
    private float shopDragStartY, shopDragStartScroll, shopDragMoved;
    private java.util.List<java.util.List<com.avatardk.game.data.PartData>> shopLists;
    /** MOCKUP luồng mua mới (user yêu cầu 2026-11-01, đang chờ duyệt):
     *  true = cột chi tiết KHÔNG hiện dòng "Hạn dùng" và chỉ còn MỘT nút
     *  "Mua"; bấm vào mở panel kiểu HOÁ ĐƠN: chọn loại tiền (Xu/Lượng),
     *  chọn thời hạn (Theo ngày / Vĩnh viễn), panel hiện tổng tiền và
     *  nút Mua xác nhận riêng ở đáy. User chốt bật thật 2026-11-02
     *  kèm luật giá theo mốc trong billTotal; server đã nối theo. */
    private boolean shopNewFlow = true;
    /** Panel hoá đơn đang mở hay không. */
    private boolean billOpen = false;
    /** Loại tiền đang chọn trong hoá đơn: 1 = Xu, 2 = Lượng. */
    private int billCur = 2;
    /** Mốc thời hạn đang chọn khi mua bằng Xu: chỉ số vào
     *  BILL_DAY_TIERS (0..4 = 1/3/7/15/30 ngày), bằng số mốc = Vĩnh viễn. */
    private int billTier = 4;
    /** Hệ số giá Vĩnh viễn so với giá Theo ngày — con số ĐỀ XUẤT trong mockup. */
    private static final int SHOP_PERM_MULT = 5;
    /** Số ngày của lựa chọn "Theo ngày" đối với món vốn vĩnh viễn trong dữ liệu. */
    private static final int SHOP_RENT_DAYS = 30;
    private static final String[] SHOP_TABS = {"Áo", "Quần", "Tóc", "Nón", "Trang sức", "Phương tiện", "Khác"};
    private static final float SHOP_PW = 1160f, SHOP_PH = 620f;
    private static final int SHOP_COLS = 6, SHOP_CELL = 96, SHOP_GAP = 12;
    /** Mã NPC Cửa Hàng trong npcs.json (NpcName.CUA_HANG của gốc). */
    private static final int NPC_CUA_HANG = 761;
    /** Mã NPC Lái Buôn (NpcName.LAI_BUON của gốc): đứng ở map 26
     *  (đường ra nông trại), menu Điểm Danh / Xem hướng dẫn / Thoát. */
    private static final int NPC_LAI_BUON = 850;
    /** Cộng số này vào mã NPC ra id trên đường truyền (Npc.ID_ADD). */
    private static final int NPC_ID_ADD = 2000000000;

    private float shopPx() { return (Ui.W - SHOP_PW) / 2f; }
    private float shopPy() { return (Ui.H - SHOP_PH) / 2f; }
    private float shopGridX() { return shopPx() + 344; }
    private float shopGridTop() { return shopPy() + SHOP_PH - 122 - 14; }
    private float shopGridBottom() { return shopGridTop() - 3 * SHOP_CELL - 2 * SHOP_GAP; }

    /** Mục của một món theo zOrder — đúng luật phân mục của gốc. */
    static int shopCategory(com.avatardk.game.data.PartData p) {
        switch (p.zorder) {
            case 20: return 0; // Áo
            case 10: return 1; // Quần
            case 50: return 2; // Tóc
            case 60: return 3; // Nón
            case 52: case 53: case 5: case 65: return 4; // Trang sức
            case 70: return 5; // Phương tiện / cầm tay
            default: return 6; // Khác
        }
    }

    /** Dựng các danh sách theo mục từ danh sách id hàng (giữ nguyên thứ
     *  tự server đưa; món lạ/không tên thì bỏ qua). */
    /** Món thuộc giới khác với nhân vật của mình: vẫn hiện trong shop
     *  để phân biệt NAM/NỮ (user chốt lại 2026-11-02 sau khi bản lọc ẩn
     *  làm kho trông quá ít món) nhưng làm mờ và không mua được —
     *  server cũng chặn mua đồ sai giới. */
    private boolean shopOtherGender(com.avatardk.game.data.PartData p) {
        int my = game.player != null ? game.player.gender : 0;
        return p.gender != 0 && my != 0 && p.gender != my;
    }

    private java.util.List<java.util.List<com.avatardk.game.data.PartData>> buildShopLists(
            java.util.List<Integer> ids) {
        java.util.List<java.util.List<com.avatardk.game.data.PartData>> lists = new ArrayList<>();
        for (int i = 0; i < SHOP_TABS.length; i++) lists.add(new ArrayList<>());
        for (int id : ids) {
            com.avatardk.game.data.PartData p = game.data.part(id);
            if (p != null && p.name != null && !p.name.isEmpty()) {
                lists.get(shopCategory(p)).add(p);
            }
        }
        return lists;
    }

    /** Mở panel shop theo danh sách hàng server cấp qua gói −49. */
    public void openShopFromServer(java.util.List<Integer> ids, String title) {
        bagPanel = false;
        chestPanel = false;
        charPanel = false;
        menuOpen = false;
        cityMapOpen = false;
        boardOpen = false;
        npcMenuOpen = false;
        shopLists = buildShopLists(ids);
        // tên "shop 0" là chữ tạm của dev fan phía server — hiện tên tử tế
        shopTitle = (title == null || title.startsWith("shop")) ? "Cửa hàng" : title;
        shopOpen = true;
        // mở vào mục đầu tiên có hàng
        shopTab = 0;
        for (int i = 0; i < SHOP_TABS.length; i++) {
            if (!shopLists.get(i).isEmpty()) { shopTab = i; break; }
        }
        shopSel = 0;
        shopScroll = 0f;
        refreshShopTryDoll();
    }

    /** Mở shop offline (sân khấu chụp + chế độ chơi thử): dựng danh sách
     *  hàng y như server — món có sell 8/14 và có giá (PartManager.Shop0). */
    public void openShop() {
        java.util.List<com.avatardk.game.data.PartData> all =
            new ArrayList<>(game.data.parts.values());
        all.sort(java.util.Comparator.comparingInt(p -> p.id));
        java.util.List<Integer> ids = new ArrayList<>();
        for (com.avatardk.game.data.PartData p : all) {
            if ((p.sell == 8 || p.sell == 14) && (p.coin > 0 || p.gold > 0)) {
                ids.add(p.id);
            }
        }
        openShopFromServer(ids, "Cửa hàng");
    }

    private com.avatardk.game.data.PartData shopSelPart() {
        if (shopLists == null) return null;
        java.util.List<com.avatardk.game.data.PartData> l = shopLists.get(shopTab);
        if (l.isEmpty()) return null;
        return l.get(Math.min(shopSel, l.size() - 1));
    }

    /** Dựng lại doll mặc thử theo món đang chọn (luật setAvatarShop của gốc). */
    private void refreshShopTryDoll() {
        com.avatardk.game.data.PartData sel = shopSelPart();
        if (sel == null) {
            shopTryDoll = null;
            return;
        }
        java.util.List<Integer> parts = new ArrayList<>();
        boolean replaced = false;
        for (int id : playerParts) {
            com.avatardk.game.data.PartData pd = game.data.part(id);
            if (pd != null && pd.zorder == sel.zorder) {
                parts.add(sel.id);
                replaced = true;
            } else {
                parts.add(id);
            }
        }
        if (!replaced) parts.add(sel.id);
        shopTryDoll = game.newDoll(parts);
    }

    private float shopMaxScroll() {
        if (shopLists == null) return 0f;
        int n = shopLists.get(shopTab).size();
        int rows = (n + SHOP_COLS - 1) / SHOP_COLS;
        float content = rows * (SHOP_CELL + SHOP_GAP) - SHOP_GAP;
        return Math.max(0f, content - (3 * SHOP_CELL + 2 * SHOP_GAP));
    }

    /** Gửi gói mua −24 cho món đang chọn; type 1 = Xu, 2 = Lượng. Nút bấm
     *  đã tự mờ khi món không bán bằng loại tiền đó hoặc tiền không đủ. */
    private void shopBuy(int type) {
        com.avatardk.game.data.PartData sel = shopSelPart();
        if (sel == null || game.connection == null) return;
        int price = type == 1 ? sel.coin : sel.gold;
        if (price <= 0) return;
        if ((type == 1 ? myXu : myLuong) < price) return;
        try {
            AvatarProtocol.buyItem(game.connection, sel.id, type);
        } catch (Exception e) {
            // mất kết nối giữa chừng: server chưa trừ tiền, bỏ qua lần bấm này
        }
    }

    /** Chạm trong panel shop (modal): đóng / chọn tab / mua / kéo lưới. */
    private void shopTouch(float x, float y, int pointer) {
        if (billOpen) {
            billTouch(x, y);
            return;
        }
        float px = shopPx(), py = shopPy();
        if (x >= px + SHOP_PW - 54 && x <= px + SHOP_PW - 18
                && y >= py + SHOP_PH - 54 && y <= py + SHOP_PH - 18) {
            shopOpen = false;
            shopPointer = -1;
            return;
        }
        if (x < px || x > px + SHOP_PW || y < py || y > py + SHOP_PH) {
            shopOpen = false;
            shopPointer = -1;
            return;
        }
        float tabY = py + SHOP_PH - 122;
        if (y >= tabY && y <= tabY + 38) {
            float tx = shopGridX();
            for (int i = 0; i < SHOP_TABS.length; i++) {
                float tw = Ui.textWidth(Ui.fontSmall, SHOP_TABS[i]) + 28;
                if (x >= tx && x <= tx + tw) {
                    if (i != shopTab) {
                        shopTab = i;
                        shopSel = 0;
                        shopScroll = 0f;
                        refreshShopTryDoll();
                    }
                    return;
                }
                tx += tw + 8;
            }
            return;
        }
        com.avatardk.game.data.PartData sel = shopSelPart();
        if (sel != null) {
            float stX = px + 24, bW = 296, bH = 44;
            if (shopNewFlow) {
                // nút Xem duy nhất -> mở hoá đơn xem giá (đồ giới khác
                // thì nút đang xám, chạm không làm gì)
                if (x >= stX && x <= stX + bW && y >= py + 34 && y <= py + 90) {
                    if (!shopOtherGender(sel)) openBill();
                    return;
                }
            } else {
                if (x >= stX && x <= stX + bW && y >= py + 66 && y <= py + 66 + bH) {
                    shopBuy(2);
                    return;
                }
                if (x >= stX && x <= stX + bW && y >= py + 14 && y <= py + 14 + bH) {
                    shopBuy(1);
                    return;
                }
            }
        }
        float gx = shopGridX(), gt = shopGridTop(), gb = shopGridBottom();
        if (x >= gx - 6 && x <= gx + SHOP_COLS * (SHOP_CELL + SHOP_GAP)
            && y >= gb - 6 && y <= gt + 6) {
            shopPointer = pointer;
            shopDragStartY = y;
            shopDragStartScroll = shopScroll;
            shopDragMoved = 0f;
        }
    }

    /** Nút mua một loại tiền: price <= 0 = món không bán bằng loại tiền
     *  này; balance là số dư hiện tại để quyết định đủ/thiếu. */
    private void drawShopBuyButton(float x, float y, float w, float h, int price, String unit, int balance) {
        if (price <= 0) {
            batch.draw(Ui.roundedRect((int) w, (int) h, 12, new Color(0f, 0f, 0f, 0.35f), GLASS_RIM), x, y);
            Ui.centered(batch, Ui.fontSmall, "Chỉ bán bằng " + ("Xu".equals(unit) ? "Lượng" : "Xu"),
                x + w / 2f, y + 30, new Color(1f, 1f, 1f, 0.45f));
        } else if (balance >= price) {
            batch.draw(Ui.gradientRect((int) w, (int) h, 12, EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), x, y);
            Ui.centered(batch, Ui.fontSmall, "Mua — " + fmtMoney(price) + " " + unit, x + w / 2f, y + 30, Color.WHITE);
        } else {
            batch.draw(Ui.roundedRect((int) w, (int) h, 12, new Color(0f, 0f, 0f, 0.35f), GLASS_RIM), x, y);
            Ui.centered(batch, Ui.fontSmall, "Mua — " + fmtMoney(price) + " " + unit + " (không đủ)",
                x + w / 2f, y + 30, new Color(1f, 1f, 1f, 0.45f));
        }
    }

    private void drawShop() {
        float pw = SHOP_PW, ph = SHOP_PH, px = shopPx(), py = shopPy();
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 18,
            new Color(0.07f, 0.11f, 0.17f, 0.97f), GLASS_RIM), px, py);
        // tiêu đề + nút đóng (user lệnh: cửa hàng KHÔNG hiện tiền tệ)
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, shopTitle, px + 28, py + ph - 26);
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), px + pw - 54, py + ph - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, px + pw - 36, py + ph - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        // ===== cột trái: sân khấu mặc thử =====
        float stX = px + 24, stW = 296, stY = py + 196, stH = ph - 84 - 196;
        batch.draw(Ui.roundedRect((int) stW, (int) stH, 14, new Color(0.10f, 0.16f, 0.23f, 0.9f), GLASS_RIM), stX, stY);
        // bóng đổ phải NẰM ĐÚNG dưới chân doll (chân ở stY + 52): trước
        // đây bóng đặt thấp hơn chân 20px nên doll trông như lơ lửng
        batch.setColor(0f, 0f, 0f, 0.35f);
        batch.draw(circleTexture(), stX + stW / 2f - 80, stY + 39, 160, 26);
        batch.setColor(Color.WHITE);
        if (shopTryDoll != null) {
            game.dollRenderer.draw(batch, shopTryDoll, 0,
                stX + stW / 2f, stY + 52, 6.0f, false);
        }
        com.avatardk.game.data.PartData sel = shopSelPart();
        float bW = stW, bH = 44;
        if (sel != null) {
            // tên món + hai dòng thông tin CHỮ TO RÕ (user sửa 2026-11-01:
            // bản trước chữ nhỏ và dính vào nút mua): hạn dùng và giới
            // tính/cấp dùng fontSmall, giãn dòng hẳn hoi, nút mua hạ
            // xuống để chừa khoảng thở phía dưới dòng meta
            drawFittedName(sel.name, stX + 4, py + 182, stW - 8);
            // Luồng mua mới (mockup): hạn dùng KHÔNG hiện ở đây nữa —
            // người chơi chọn thời hạn trong panel riêng sau khi bấm
            // nút mua. Luồng cũ vẫn giữ dòng hạn dùng như đã chốt.
            if (!shopNewFlow) {
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.88f);
                String han = "Hạn dùng: ";
                Ui.fontSmall.draw(batch, han, stX + 4, py + 154);
                Ui.fontSmall.setColor(1f, 0.85f, 0.38f, 1f);
                Ui.fontSmall.draw(batch, sel.expiredDay > 0
                    ? sel.expiredDay + " ngày" : "Vĩnh viễn",
                    stX + 4 + Ui.textWidth(Ui.fontSmall, han), py + 154);
            }
            String meta = sel.gender == 1 ? "Chỉ cho Nam"
                : sel.gender == 2 ? "Chỉ cho Nữ" : "Nam/Nữ đều mặc được";
            if (sel.level > 0) meta += " • Cấp yêu cầu: " + sel.level;
            Ui.fontSmall.setColor(sel.gender == 0
                ? new Color(1f, 1f, 1f, 0.75f) : new Color(1f, 0.72f, 0.45f, 1f));
            Ui.fontSmall.draw(batch, meta, stX + 4, shopNewFlow ? py + 148 : py + 126);
            if (shopNewFlow) {
                // nút XEM — bấm vào mở hoá đơn, trong hoá đơn mới thấy
                // giá (user lệnh: giá chỉ hiện trong hoá đơn)
                if (shopOtherGender(sel)) {
                    // đồ giới khác: nút Xem xám, không bấm được
                    batch.draw(Ui.roundedRect((int) bW, 56, 12,
                        new Color(0.35f, 0.38f, 0.44f, 0.55f), GLASS_RIM), stX, py + 34);
                    Ui.centered(batch, Ui.font, "Xem", stX + bW / 2f, py + 34 + 37,
                        new Color(1f, 1f, 1f, 0.55f));
                } else {
                    batch.draw(Ui.gradientRect((int) bW, 56, 12,
                        EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), stX, py + 34);
                    Ui.centered(batch, Ui.font, "Xem", stX + bW / 2f, py + 34 + 37, Color.WHITE);
                }
            } else {
                drawShopBuyButton(stX, py + 66, bW, bH, sel.gold, "Lượng", myLuong);
                drawShopBuyButton(stX, py + 14, bW, bH, sel.coin, "Xu", myXu);
            }
        }
        // ===== cột phải: tab danh mục =====
        float rx = shopGridX();
        float tabY = py + ph - 122;
        float tabX = rx;
        for (int i = 0; i < SHOP_TABS.length; i++) {
            float tw = Ui.textWidth(Ui.fontSmall, SHOP_TABS[i]) + 28;
            boolean act = i == shopTab;
            batch.draw(Ui.roundedRect((int) tw, 38, 12,
                act ? new Color(0.25f, 0.45f, 0.65f, 0.95f) : new Color(0f, 0f, 0f, 0.30f), GLASS_RIM), tabX, tabY);
            Ui.centered(batch, Ui.fontSmall, SHOP_TABS[i], tabX + tw / 2f, tabY + 25,
                act ? Color.WHITE : new Color(1f, 1f, 1f, 0.65f));
            tabX += tw + 8;
        }
        // lưới món: cuộn dọc, cắt đúng khung 3 hàng bằng scissor
        java.util.List<com.avatardk.game.data.PartData> list = shopLists.get(shopTab);
        float gridTop = shopGridTop(), gridBottom = shopGridBottom();
        com.badlogic.gdx.math.Rectangle clip =
            new com.badlogic.gdx.math.Rectangle(rx - 4, gridBottom - 4,
                SHOP_COLS * (SHOP_CELL + SHOP_GAP) - SHOP_GAP + 8, gridTop - gridBottom + 8);
        com.badlogic.gdx.math.Rectangle scissors = new com.badlogic.gdx.math.Rectangle();
        batch.flush();
        com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.calculateScissors(
            hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.pushScissors(scissors)) {
            for (int idx = 0; idx < list.size(); idx++) {
                int col = idx % SHOP_COLS, row = idx / SHOP_COLS;
                float cx = rx + col * (SHOP_CELL + SHOP_GAP);
                float cy = gridTop + shopScroll - (row + 1) * SHOP_CELL - row * SHOP_GAP;
                if (cy + SHOP_CELL < gridBottom - 8 || cy > gridTop + 8) continue;
                com.avatardk.game.data.PartData it = list.get(idx);
                boolean isSel = idx == shopSel;
                batch.draw(Ui.roundedRect(SHOP_CELL, SHOP_CELL, 12,
                    new Color(0.16f, 0.24f, 0.34f, 0.9f),
                    isSel ? new Color(1f, 0.78f, 0.30f, 1f) : GLASS_RIM), cx, cy);
                drawPartIcon(it.id, cx + SHOP_CELL / 2f, cy + SHOP_CELL / 2f + 2, 56);
                if (shopOtherGender(it)) {
                    // đồ giới khác: phủ mờ cả ô cho khác hẳn đồ mặc được
                    batch.draw(Ui.roundedRect(SHOP_CELL, SHOP_CELL, 12,
                        new Color(0.04f, 0.06f, 0.10f, 0.52f), GLASS_RIM), cx, cy);
                }
                // KHÔNG hiện giá dưới sản phẩm (user lệnh) — bấm Xem ở
                // cột chi tiết để vào hoá đơn xem giá.
                if (it.gender != 0) {
                    // asset giới tính ở góc trái trên của ô (user lệnh:
                    // đổi thẻ chữ NAM/NỮ thành asset)
                    Texture gtex = game.texture(it.gender == 1
                        ? "iconmenu/gender-male.png" : "iconmenu/gender-female.png");
                    if (gtex != null) {
                        batch.draw(gtex, cx + 5, cy + SHOP_CELL - 31, 26, 26);
                    }
                }
            }
            batch.flush();
            com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.popScissors();
        }
        if (list.isEmpty()) {
            Ui.centered(batch, Ui.fontSmall, "Mục này chưa có món nào trong shop",
                rx + (SHOP_COLS * (SHOP_CELL + SHOP_GAP) - SHOP_GAP) / 2f,
                (gridTop + gridBottom) / 2f, new Color(1f, 1f, 1f, 0.6f));
        }
        // thanh cuộn mảnh ở mép phải lưới + chú thích số món
        float sbX = rx + SHOP_COLS * (SHOP_CELL + SHOP_GAP) + 6;
        float trackH = gridTop - gridBottom;
        batch.draw(Ui.roundedRect(8, (int) trackH, 4, new Color(0f, 0f, 0f, 0.30f), GLASS_RIM),
            sbX, gridBottom);
        float maxSc = shopMaxScroll();
        if (maxSc > 0f) {
            float contentH = trackH + maxSc;
            float thumbH = Math.max(36f, trackH * trackH / contentH);
            float thumbY = gridTop - thumbH - (shopScroll / maxSc) * (trackH - thumbH);
            batch.draw(Ui.roundedRect(8, (int) thumbH, 4, new Color(1f, 1f, 1f, 0.45f), GLASS_RIM), sbX, thumbY);
        }
        Ui.fontTiny.setColor(1f, 1f, 1f, 0.55f);
        Ui.fontTiny.draw(batch, list.size() + " món trong mục " + SHOP_TABS[shopTab]
            + (maxSc > 0f ? " — kéo để xem thêm" : ""), rx, py + 34);
        if (billOpen) drawShopBill();
    }

    /** Vẽ tên món VỪA KHUNG (user phàn nàn chữ tràn/dính 2026-11-01):
     *  vừa cỡ chính thì vẽ cỡ chính; dài quá thì hạ xuống fontSmall;
     *  vẫn dài nữa thì cắt ngắn và thêm "…" — không bao giờ để chữ
     *  tràn khỏi khung của nó đè lên phần khác. */
    private void drawFittedName(String name, float x, float baseline, float maxW) {
        if (Ui.textWidth(Ui.font, name) <= maxW) {
            Ui.font.setColor(1f, 1f, 1f, 0.97f);
            Ui.font.draw(batch, name, x, baseline);
        } else if (Ui.textWidth(Ui.fontSmall, name) <= maxW) {
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.97f);
            Ui.fontSmall.draw(batch, name, x, baseline);
        } else {
            String t = name;
            while (t.length() > 4 && Ui.textWidth(Ui.fontSmall, t + "…") > maxW) {
                t = t.substring(0, t.length() - 1);
            }
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.97f);
            Ui.fontSmall.draw(batch, t + "…", x, baseline);
        }
    }

    /** Các mốc ngày mua bằng Xu trong hoá đơn (mốc cuối = Vĩnh viễn). */
    private static final int[] BILL_DAY_TIERS = {1, 3, 7, 15, 30};

    /** Tổng tiền hiện tại của hoá đơn theo món + loại tiền + mốc đang
     *  chọn. Luật giá (user chốt 2026-11-02): giá niêm yết là giá của
     *  hạn gốc ghi trong dữ liệu món; quy về giá 30 ngày rồi nhân tỉ lệ
     *  theo mốc; Vĩnh viễn = giá 30 ngày x SHOP_PERM_MULT. Mua bằng
     *  Lượng: luôn Vĩnh viễn với đúng giá Lượng niêm yết. */
    private int billTotal(com.avatardk.game.data.PartData sel) {
        int base = billCur == 2 ? sel.gold : sel.coin;
        if (base <= 0) return -1;
        if (billCur == 2) return base;
        int price30 = sel.expiredDay > 0
            ? Math.max(1, Math.round(base * 30f / sel.expiredDay))
            : Math.max(1, base / SHOP_PERM_MULT);
        if (billTier >= BILL_DAY_TIERS.length) return price30 * SHOP_PERM_MULT;
        return Math.max(1, Math.round(price30 * BILL_DAY_TIERS[billTier] / 30f));
    }

    /** Panel HOÁ ĐƠN mua (luồng mới, đang ở dạng mockup chờ duyệt):
     *  hiện đè giữa màn hình sau khi bấm nút "Mua" duy nhất ở cột chi
     *  tiết — chọn loại tiền (món chỉ có giá ở một loại thì loại kia bị
     *  khoá), mua bằng Xu chọn mốc ngày / Vĩnh viễn, mua bằng Lượng
     *  luôn Vĩnh viễn; cuối panel là tổng tiền + nút Mua xác nhận. */
    private void drawShopBill() {
        com.avatardk.game.data.PartData sel = shopSelPart();
        if (sel == null) { billOpen = false; return; }
        float cw = 640, ch = 520, cx = (Ui.W - cw) / 2f, cy = (Ui.H - ch) / 2f;
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) cw, (int) ch, 18,
            new Color(0.08f, 0.12f, 0.19f, 0.98f), GLASS_RIM), cx, cy);
        Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
        Ui.font.draw(batch, "Hoá đơn mua", cx + 28, cy + ch - 30);
        // nút đóng: ô vuông đỏ chuẩn như mọi panel
        batch.draw(Ui.roundedRect(36, 36, 10, Ui.BTN_CLOSE, GLASS_RIM), cx + cw - 54, cy + ch - 54);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_XMARK, cx + cw - 36, cy + ch - 36,
            new Color(0.9f, 0.93f, 1f, 1f));
        // món đang mua: icon + tên + giới tính/cấp
        drawPartIcon(sel.id, cx + 62, cy + ch - 100, 76);
        drawFittedName(sel.name, cx + 116, cy + ch - 82, cw - 116 - 24);
        String meta = sel.gender == 1 ? "Chỉ cho Nam"
            : sel.gender == 2 ? "Chỉ cho Nữ" : "Nam/Nữ đều mặc được";
        if (sel.level > 0) meta += " • Cấp yêu cầu: " + sel.level;
        Ui.fontSmall.setColor(sel.gender == 0
            ? new Color(1f, 1f, 1f, 0.75f) : new Color(1f, 0.72f, 0.45f, 1f));
        Ui.fontSmall.draw(batch, meta, cx + 116, cy + ch - 110);
        // chọn loại tiền (cả mục hạ xuống một chút cho thoáng theo ý
        // user 2026-11-01; số dư KHÔNG hiện trong hoá đơn nữa)
        int bal = billCur == 2 ? myLuong : myXu;
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.75f);
        Ui.fontSmall.draw(batch, "Thanh toán bằng", cx + 24, cy + ch - 174);
        drawBillPill(cx + 24, cy + ch - 234, 180, 44, "Xu", sel.coin > 0, billCur == 1);
        drawBillPill(cx + 216, cy + ch - 234, 180, 44, "Lượng", sel.gold > 0, billCur == 2);
        // thời hạn
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.75f);
        Ui.fontSmall.draw(batch, "Thời hạn", cx + 24, cy + ch - 264);
        if (billCur == 2) {
            batch.draw(Ui.roundedRect((int) (cw - 48), 48, 12,
                new Color(0.20f, 0.16f, 0.10f, 0.95f), new Color(1f, 0.62f, 0.25f, 1f)),
                cx + 24, cy + ch - 340);
            Ui.centered(batch, Ui.fontSmall, "Vĩnh viễn — mua bằng Lượng luôn là vĩnh viễn",
                cx + cw / 2f, cy + ch - 308, new Color(1f, 0.85f, 0.45f, 1f));
        } else {
            for (int i = 0; i <= BILL_DAY_TIERS.length; i++) {
                String label = i < BILL_DAY_TIERS.length
                    ? BILL_DAY_TIERS[i] + " ngày" : "Vĩnh viễn";
                drawBillPill(cx + 24 + i * 96, cy + ch - 340, 88, 48, label,
                    true, billTier == i);
            }
        }
        // tổng tiền + số dư sau mua + nút xác nhận
        int total = billTotal(sel);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.75f);
        Ui.fontSmall.draw(batch, "Tổng tiền", cx + 24, cy + ch - 380);
        if (total > 0) {
            String tot = fmtMoney(total) + (billCur == 2 ? " Lượng" : " Xu");
            Ui.font.setColor(1f, 0.85f, 0.38f, 1f);
            Ui.font.draw(batch, tot, cx + cw - 24 - Ui.textWidth(Ui.font, tot), cy + ch - 376);
            if (bal < total) {
                // không đủ tiền: chỉ báo còn thiếu bao nhiêu (hoá đơn
                // không hiện số dư theo ý user 2026-11-01)
                String lack = "Còn thiếu: " + fmtMoney(total - bal)
                    + (billCur == 2 ? " Lượng" : " Xu");
                Ui.fontSmall.setColor(1f, 0.45f, 0.40f, 1f);
                Ui.fontSmall.draw(batch, lack, cx + 24, cy + ch - 412);
            }
        }
        boolean afford = total > 0 && bal >= total;
        if (afford) {
            batch.draw(Ui.gradientRect((int) (cw - 48), 54, 12,
                EntryUi.ORANGE_TOP, EntryUi.ORANGE_BOT), cx + 24, cy + 24);
        } else {
            batch.draw(Ui.roundedRect((int) (cw - 48), 54, 12,
                new Color(0f, 0f, 0f, 0.35f), GLASS_RIM), cx + 24, cy + 24);
        }
        Ui.centered(batch, Ui.font, afford ? "Mua ngay" : "Không đủ tiền",
            cx + cw / 2f, cy + 24 + 36,
            afford ? Color.WHITE : new Color(1f, 1f, 1f, 0.45f));
    }

    /** Một nút chọn dạng pill trong hoá đơn: bị khoá (món không bán
     *  bằng loại tiền này) thì xám hẳn; đang chọn thì nền cam. */
    private void drawBillPill(float x, float y, float w, float h,
                              String label, boolean enabled, boolean active) {
        if (!enabled) {
            batch.draw(Ui.roundedRect((int) w, (int) h, 12,
                new Color(0f, 0f, 0f, 0.35f), GLASS_RIM), x, y);
            Ui.centered(batch, Ui.fontSmall, label, x + w / 2f, y + h / 2f + 7,
                new Color(1f, 1f, 1f, 0.35f));
        } else if (active) {
            batch.draw(Ui.roundedRect((int) w, (int) h, 12,
                new Color(0.95f, 0.55f, 0.20f, 1f), GLASS_RIM), x, y);
            Ui.centered(batch, Ui.fontSmall, label, x + w / 2f, y + h / 2f + 7, Color.WHITE);
        } else {
            batch.draw(Ui.roundedRect((int) w, (int) h, 12,
                new Color(0.13f, 0.20f, 0.29f, 0.95f), GLASS_RIM), x, y);
            Ui.centered(batch, Ui.fontSmall, label, x + w / 2f, y + h / 2f + 7,
                new Color(1f, 1f, 1f, 0.85f));
        }
    }

    /** Chạm khi hoá đơn đang mở: X / ra ngoài là đóng; chạm pill tiền
     *  (nếu món bán bằng loại đó) hoặc chip mốc ngày thì đổi lựa chọn;
     *  nút Mua ngay gửi gói −24 kèm số ngày đã chọn (server tính tiền
     *  + hạn dùng theo đúng luật giá của billTotal rồi trừ tiền). */
    private void billTouch(float x, float y) {
        com.avatardk.game.data.PartData sel = shopSelPart();
        float cw = 640, ch = 520, cx = (Ui.W - cw) / 2f, cy = (Ui.H - ch) / 2f;
        if ((x >= cx + cw - 54 && x <= cx + cw - 18 && y >= cy + ch - 54 && y <= cy + ch - 18)
            || x < cx || x > cx + cw || y < cy || y > cy + ch) {
            billOpen = false;
            return;
        }
        if (sel == null) { billOpen = false; return; }
        if (y >= cy + 24 && y <= cy + 78 && x >= cx + 24 && x <= cx + cw - 24) {
            int total = billTotal(sel);
            int bal = billCur == 2 ? myLuong : myXu;
            if (total > 0 && bal >= total && game.connection != null) {
                int days = billCur == 2 || billTier >= BILL_DAY_TIERS.length
                    ? 0 : BILL_DAY_TIERS[billTier];
                try {
                    AvatarProtocol.buyItem(game.connection, sel.id, billCur, days);
                } catch (Exception ignored) {
                }
            }
            billOpen = false;
            return;
        }
        if (y >= cy + ch - 234 && y <= cy + ch - 190) {
            if (x >= cx + 24 && x <= cx + 204 && sel.coin > 0) billCur = 1;
            if (x >= cx + 216 && x <= cx + 396 && sel.gold > 0) billCur = 2;
            return;
        }
        if (billCur == 1 && y >= cy + ch - 340 && y <= cy + ch - 292) {
            for (int i = 0; i <= BILL_DAY_TIERS.length; i++) {
                if (x >= cx + 24 + i * 96 && x <= cx + 24 + i * 96 + 88) {
                    billTier = i;
                    return;
                }
            }
        }
    }

    /** Mở hoá đơn cho món đang chọn: mặc định chọn loại tiền món có
     *  giá (ưu tiên Xu nếu có cả hai), mốc 30 ngày. */
    private void openBill() {
        com.avatardk.game.data.PartData sel = shopSelPart();
        if (sel == null) return;
        billCur = sel.coin > 0 ? 1 : 2;
        billTier = BILL_DAY_TIERS.length - 1;
        billOpen = true;
    }

    /** Sân khấu chụp MOCKUP hoá đơn — bản LƯỢNG: món 2169 "thuyền
     *  trưởng" chỉ có giá Lượng (25) nên pill Xu bị khoá và thời hạn
     *  cố định Vĩnh viễn. */
    public void debugOpenShopChoiceMock() {
        debugOpenShop(0, 2169);
        shopNewFlow = true;
        openBill();
    }

    /** Sân khấu chụp MOCKUP hoá đơn — bản XU: món 2173 "áo khoác thể
     *  thao" chỉ có giá Xu (24.850 cho 30 ngày) nên hiện đủ 6 chip mốc
     *  1/3/7/15/30 ngày + Vĩnh viễn; chọn sẵn mốc 30 ngày. Ví chụp mẫu
     *  được bơm lên 100.000 Xu để nút Mua ở trạng thái bấm được. */
    public void debugOpenBillMockXu() {
        debugOpenShop(0, 2173);
        shopNewFlow = true;
        myXu = 100000;
        openBill();
    }

    /** Sân khấu chụp: mở shop thật tại một tab và chọn sẵn một món theo id. */
    public void debugOpenShop(int tab, int partId) {
        openShop();
        shopTab = tab;
        shopScroll = 0f;
        shopSel = 0;
        java.util.List<com.avatardk.game.data.PartData> l = shopLists.get(tab);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id == partId) { shopSel = i; break; }
        }
        refreshShopTryDoll();
    }


    /** Nút mặt cười (asset iconmenu/feel0 gốc) kế bên khung chat + SHEET biểu
     *  cảm kéo lên được: viên pill ở mép trên là tay cầm kéo (kéo lên mở rộng,
     *  kéo xuống thu gọn/đóng), lưới 7 cột cuộn dọc chứa mọi part mặt. */
    private void drawFeelUi() {
        Texture icon = game.texture("iconmenu/feel0.png");
        batch.draw(Ui.roundedRect((int) FEEL_BTN_S, (int) FEEL_BTN_S, (int) (FEEL_BTN_S / 2),
            feelOpen ? new Color(0.55f, 0.38f, 0.10f, 0.92f) : GLASS_FILL, GLASS_RIM),
            FEEL_BTN_X, FEEL_BTN_Y);
        batch.draw(icon, FEEL_BTN_X + 8, FEEL_BTN_Y + 8, FEEL_BTN_S - 16, FEEL_BTN_S - 16);
        if (!feelOpen) return;
        float[] fg = feelSheetGeom();
        float px = fg[0], py = fg[1], pw = fg[2], ph = fg[3], cell = fg[4];
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 14, GLASS_FILL_DARK, GLASS_RIM), px, py);
        // tay cầm kéo: viên pill xám ở giữa mép trên của sheet
        batch.draw(Ui.roundedRect(64, 9, 4, new Color(1f, 1f, 1f, 0.45f),
            new Color(1f, 1f, 1f, 0.22f)), px + pw / 2f - 32f, py + ph - 20f);
        // lưới mặt, cắt trong vùng lưới của sheet (dưới tay cầm, trên đáy 8px)
        Rectangle clip = new Rectangle(px + 2, py + 8, pw - 4, ph - 44);
        Rectangle scissors = new Rectangle();
        batch.flush();
        ScissorStack.calculateScissors(hudViewport.getCamera(), batch.getTransformMatrix(), clip, scissors);
        batch.flush();
        if (ScissorStack.pushScissors(scissors)) {
            for (int i = 0; i < feelIds.size(); i++) {
                float[] cxy = feelCellXY(fg, i);
                float cx = cxy[0], cy = cxy[1];
                if (cy + cell < clip.y || cy > clip.y + clip.height) continue;
                if (feelIds.get(i) == playerFeel) {
                    batch.draw(Ui.roundedRect((int) (cell - 4), (int) (cell - 4), 9,
                        new Color(0.55f, 0.38f, 0.10f, 0.85f), new Color(1f, 0.8f, 0.35f, 0.9f)),
                        cx + 2, cy + 2);
                }
                // mặt xem trước: doll chỉ gồm mặt nền + part mặt, căn giữa ô theo bounds
                PaperDoll fd = feelDolls.get(i);
                int[] b = fd.bounds(0);
                float u = Math.min(2.2f, 44f / Math.max(1, b[3] - b[1]));
                float fx = cx + cell / 2f - (b[0] + b[2]) / 2f * u;
                float fy = cy + cell / 2f + (b[1] + b[3]) / 2f * u;
                game.dollRenderer.draw(batch, fd, 0, fx, fy, u, false);
            }
            batch.flush();
            ScissorStack.popScissors();
        }
    }

    /** Nút hành động (asset iconmenu/action0 gốc) trên vòng cung quanh nút
     *  Tương tác + SHEET hành động: 4 ô — icon là khung 7..10 của sheet effect/cmd vẽ
     *  nguyên trạng (nền teal của sheet gốc), tên đúng T.actionStr; ô của
     *  tư thế đang diễn sáng viền vàng như sheet biểu cảm. */
    private void drawActionUi() {
        Texture icon = game.texture("iconmenu/action0.png");
        batch.draw(Ui.roundedRect((int) ACT_BTN_S, (int) ACT_BTN_S, (int) (ACT_BTN_S / 2),
            actionOpen ? new Color(0.55f, 0.38f, 0.10f, 0.92f) : GLASS_FILL, GLASS_RIM),
            ACT_BTN_X, ACT_BTN_Y);
        batch.draw(icon, ACT_BTN_X + 8, ACT_BTN_Y + 8, ACT_BTN_S - 16, ACT_BTN_S - 16);
        if (!actionOpen) return;
        float px = actSheetX(), py = actSheetY();
        batch.draw(Ui.roundedRect((int) ACT_SHEET_W, (int) ACT_SHEET_H, 14, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Texture cmd = game.texture("effect/cmd.png");
        for (int i = 0; i < 4; i++) {
            float cx = actCellX(i);
            if (playerPose == ACTION_BYTES[i]) {
                batch.draw(Ui.roundedRect(80, (int) (ACT_SHEET_H - 16), 10,
                    new Color(0.55f, 0.38f, 0.10f, 0.85f), new Color(1f, 0.8f, 0.35f, 0.9f)),
                    cx + 2, py + 8);
            }
            // sheet cmd: 96x576 = 2 cột x 12 hàng khung 48; khung index ở
            // cột index/12, hàng index%12 (đúng CommandAction.paint gốc)
            int fr = ACTION_FRAMES[i];
            TextureRegion reg = new TextureRegion(cmd, (fr / 12) * 48, (fr % 12) * 48, 48, 48);
            batch.draw(reg, cx + 14, py + ACT_SHEET_H - 66, 56, 56);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.92f);
            layout.setText(Ui.fontSmall, ACTION_NAMES[i]);
            Ui.fontSmall.draw(batch, ACTION_NAMES[i], cx + 42 - layout.width / 2f, py + 24);
        }
    }

    /** Trái dưới — joystick glass. */
    private void drawJoystick() {
        batch.draw(Ui.roundedRect((int) (JOY_R * 2), (int) (JOY_R * 2), (int) JOY_R,
            new Color(0.05f, 0.11f, 0.18f, 0.45f), GLASS_RIM), JOY_CX - JOY_R, JOY_CY - JOY_R);
        float kx = JOY_CX + joyVecX * 26f, ky = JOY_CY + joyVecY * 26f;
        batch.draw(Ui.roundedRect(56, 56, 28, new Color(1f, 1f, 1f, 0.30f), GLASS_RIM), kx - 28, ky - 28);
    }

    /** Phải dưới — nút tương tác glass: tông kính slate khớp panel chat khi
     *  có NPC trong tầm (bỏ hẳn tông vàng/nâu cũ) và KHÔNG hiện nhãn tên NPC
     *  ở phía trên nữa — chủ dự án chốt 2026-10-31. Chưa có ai thì để mờ. */
    private void drawInteractButton() {
        boolean active = nearestNpc != null;
        Color fill = active ? new Color(0.10f, 0.17f, 0.26f, 0.97f) : GLASS_FILL;
        Color rim = active ? new Color(0.62f, 0.82f, 1f, 0.90f) : GLASS_RIM;
        batch.draw(Ui.roundedRect((int) (ACT_R * 2), (int) (ACT_R * 2), (int) ACT_R, fill, rim),
            ACT_CX - ACT_R, ACT_CY - ACT_R);
        // glyph bong bóng thoại vẽ bằng hình (không emoji)
        batch.draw(Ui.roundedRect(36, 26, 8, new Color(1f, 1f, 1f, active ? 0.95f : 0.6f), null),
            ACT_CX - 18, ACT_CY - 6);
        batch.draw(Ui.roundedRect(10, 10, 2, new Color(1f, 1f, 1f, active ? 0.95f : 0.6f), null),
            ACT_CX - 10, ACT_CY - 14);
        Ui.fontSmall.setColor(0.1f, 0.12f, 0.16f, 1f);
        Ui.fontSmall.draw(batch, "...", ACT_CX - 11, ACT_CY + 14);
        // (chữ "Tương tác" dưới nút và nhãn tên NPC phía trên đã bỏ theo yêu
        // cầu của chủ dự án)
    }

    @Override
    public void dispose() { batch.dispose(); }
}
