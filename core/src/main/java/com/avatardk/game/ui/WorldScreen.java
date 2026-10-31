package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.avatardk.game.data.NpcData;
import com.avatardk.game.data.PaperDoll;
import com.avatardk.game.net.AvatarProtocol;
import com.avatardk.game.net.GameConnection;
import com.avatardk.game.net.Msg;
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
        /** Số nền dùng khi vẽ (map không nền thì tạm dùng 0 cho lớp trời). */
        public int bgNum() { return bg == null ? 0 : bg; }
        public String label() { return name == null || name.isEmpty() ? "Map " + id : name; }
    }

    /** Toàn bộ map dựng được bằng dải imagemap (strips > 0), nạp từ maps.json. */
    private final List<MapDef> maps = new ArrayList<>();

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
        if (Gdx.files.internal(p).exists()) t = game.texture(p);
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
        static final int OBJ = 0, NPC = 1, REMOTE = 2, PLAYER = 3;
        int kind;
        float y;
        float x;
        Texture tex;
        int objId;
        NpcFigure npc;
        RemoteFigure remote;
    }
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
    private int[] boardStatus = null;   // trạng thái từng khu từ gói 60
    private float boardLatch = 0f;      // chống mở panel liên tục khi va bảng
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
        displayXu = fmtMoney(p.xu);
        displayLuong = fmtMoney(p.luong);
        displayLevel = "Lv." + p.levelMain;
        java.util.List<Integer> parts = new ArrayList<>();
        for (int id : p.wearing) parts.add(id);
        playerParts.clear();
        playerParts.addAll(parts);
        playerDoll = game.newDoll(parts);
        if (playerFeel != 4) applyFeel(playerFeel);
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
    private static final float CHAT_H = CHAT_INPUT_H + CHAT_LOG_LINES * CHAT_LINE_H + 36f;
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
    private static class ChatLine { String name, text; boolean system; }
    @SuppressWarnings("unchecked")
    private final List<ChatLine>[] chatLogs = new List[]{
        new ArrayList<ChatLine>(), new ArrayList<ChatLine>(), new ArrayList<ChatLine>()};

    private void addChatLog(int tab, String name, String text, boolean system) {
        ChatLine l = new ChatLine();
        l.name = name; l.text = text; l.system = system;
        chatLogs[tab].add(l);
        while (chatLogs[tab].size() > 40) chatLogs[tab].remove(0);
    }
    private final StringBuilder typeBuffer = new StringBuilder();
    private boolean menuOpen = false;
    // Menu burger xổ xuống dạng LƯỚI Ô (yêu cầu của chủ dự án): mỗi ô là một
    // tính năng với icon gốc trong hd/iconmenu. Map mở bản đồ thành phố; các ô
    // còn lại mở ở các đợt port tương ứng (Thư, Balo...).
    private static final String[][] MENU_ITEMS = {
        {"50", "Map"}, {"msg0", "Thư"}, {"30", "Balo"},
        {"40", "Bạn bè"}, {"20", "Cá nhân"}, {"action0", "Hành động"},
    };
    private static final float MENU_PX = Ui.W - 330f, MENU_PW = 312f;
    private static final float MENU_HEADER = 40f, MENU_CELL = 96f, MENU_CELL_H = 92f;
    private static final float MENU_PH = MENU_HEADER + 2 * MENU_CELL_H + 14f;
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
    private static final float FEEL_BTN_X = 962f, FEEL_BTN_Y = 62f, FEEL_BTN_S = 56f;
    // sheet biểu cảm: neo đáy ngay trên khung chat, cạnh phải thẳng hàng nút feel
    private static final float FEEL_SHEET_W = 472f;
    private static final float FEEL_SHEET_MIN = 168f, FEEL_SHEET_MAX = 400f;
    private float feelH = FEEL_SHEET_MIN, feelHTarget = FEEL_SHEET_MIN;
    private float feelScroll = 0f;
    private int feelPointer = -1;
    private boolean feelDragHandle = false;
    private float feelDragStartY = 0f, feelDragStartH = 0f;
    private float feelDragStartScroll = 0f, feelDragMoved = 0f;
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
        // nạp danh sách map từ maps.json — chỉ giữ map dựng được bằng dải imagemap
        MapDef[] all = new com.badlogic.gdx.utils.Json().fromJson(MapDef[].class,
            Gdx.files.internal("data/maps.json").readString("UTF-8"));
        for (MapDef d : all) if (d.strips > 0) maps.add(d);
        maps.sort((a, b) -> Integer.compare(a.id, b.id));
        // trạm xe buýt từ lưới ô gốc (ô 139) — docs/mapgrid-spec.md
        com.avatardk.game.data.MapGrids grids = new com.avatardk.game.data.MapGrids(
            Gdx.files.internal("data/mapgrids.json").readString("UTF-8"));
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
        byte[] grid = Gdx.files.internal("data/citymap.bin").readBytes();
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

    public MapDef findMap(int id) {
        for (MapDef d : maps) if (d.id == id) return d;
        return maps.get(0);
    }

    public void loadMap(MapDef def) {
        mapDef = def;
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
        if (def.daydien) {
            for (int i = 0; i < 4; i++) {
                String path = "imagemap/" + def.id + "/daydien" + i + ".png";
                if (!Gdx.files.internal(path).exists()) break;
                daydien.add(game.texture(path));
            }
        }
        loadBackground(def);
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
            || (mapDef.bg == null && typeMap < 26);
    }

    /** Nạp lớp nền đúng LoadMap.loadBG: status theo giờ máy (18h–6h = đêm),
     *  ảnh trời bghd/may<num><status> và ảnh silhouette bghd/<num><status>,
     *  mây trôi effect/cl<anchor><status>. */
    private void loadBackground(MapDef def) {
        int hour = java.time.LocalTime.now().getHour();
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

    /** Hình học panel bản đồ thành phố dùng chung cho vẽ + chạm:
     *  {px, py, iw, ih, ix, iy} = panel trái/dưới, ảnh rộng/cao, ảnh trái/dưới. */
    private float[] cityMapGeom() {
        float pw = 940f, ph = 600f;
        float px = (Ui.W - pw) / 2f, py = (Ui.H - ph) / 2f;
        float iw = pw - 48, ih = iw * 900f / 1600f;
        return new float[]{px, py, iw, ih, px + 24, py + ph - 64 - ih};
    }

    /** Hình học sheet biểu cảm {px, py, pw, ph, cell, pad}: sheet neo đáy
     *  ngay phía trên khung chat, cạnh phải thẳng hàng nút mặt cười, chiều
     *  cao ph = feelH hiện tại (thay đổi khi kéo tay cầm). Lưới 7 cột. */
    private float[] feelSheetGeom() {
        float pad = 10f;
        float cell = (FEEL_SHEET_W - 2 * pad) / 7f;
        float px = FEEL_BTN_X + FEEL_BTN_S - FEEL_SHEET_W;
        float py = CHAT_Y + CHAT_H + 12;
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
     *  được chọn — đúng cơ chế feel của Avatar.paint gốc (mặc định 4). */
    public void applyFeel(int feelId) {
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
    public void debugOpenCityMap() { cityMapOpen = true; }
    public void debugCloseCityMap() { cityMapOpen = false; }
    public void debugOpenFeel() { feelOpen = true; }
    public void debugCloseFeel() { feelOpen = false; }
    public void debugClearRemotes() { remotes.clear(); }
    /** Mở sheet biểu cảm ở nấc mở rộng + cuộn xuống (kiểm chứng hình). */
    public void debugExpandFeel(float scroll) {
        feelOpen = true;
        feelH = feelHTarget = FEEL_SHEET_MAX;
        feelScroll = scroll;
    }
    public int feelCount() { return feelIds.size(); }
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
        String msg = typeBuffer.toString().trim();
        typeBuffer.setLength(0);
        if (msg.isEmpty()) return;
        activeChatTab = TAB_NEARBY;
        if (online()) {
            try {
                AvatarProtocol.chatPark(game.connection, msg);
            } catch (IOException e) {
                addChatLog(TAB_SYSTEM, null, "Không gửi được tin nhắn (mất kết nối?).", true);
            }
        } else {
            showBubble(msg);
        }
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
                    boardStatus = ZonePlayers.parseBoardList(m);
                    boardOpen = true;
                    break;
                }
                case AvatarProtocol.PLAYER_JOIN: {
                    addRemote(ZonePlayers.parsePlayerJoin(m));
                    break;
                }
                case AvatarProtocol.PLAYER_LEAVE: {
                    remotes.remove(ZonePlayers.parseLeave(m));
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
                case AvatarProtocol.CHAT_PARK:
                case AvatarProtocol.CHAT_FARM: {
                    ZonePlayers.ChatMsg c = ZonePlayers.parseChat(m);
                    if (c.id == ownId()) {
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

    public void debugOpenBoard(int[] statuses, int area) {
        boardStatus = statuses;
        currentArea = area;
        boardOpen = true;
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
                f.anim.action = AvatarAnimator.RUN;
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
                if (feelOpen) {
                    feelScroll = MathUtils.clamp(feelScroll + amountY * 36f, 0f, feelMaxScroll());
                    return true;
                }
                return false;
            }
        });
    }

    private void onTouch(float x, float y, int pointer) {
        // bản đồ thành phố đang mở là lớp trên cùng: X đóng / chạm vùng để đi
        if (cityMapOpen) {
            float[] g = cityMapGeom();
            float px = g[0], py = g[1], iw = g[2], ih = g[3], ix = g[4], iy = g[5];
            if (x >= px + 900 - 40 && x <= px + 940 && y >= py + 600 - 52 && y <= py + 600) { cityMapOpen = false; return; }
            for (int r = 0; r < regionCells.size() && r < REGION_NAMES.length; r++) {
                int[] cell = regionCells.get(r);
                float cx = ix + (cell[0] + 0.5f) / 50f * iw;
                float cy = iy + ih - (cell[1] + 0.5f) / 26f * ih;
                if (Math.abs(x - cx) <= 30 && Math.abs(y - cy) <= 30) {
                    cityMapOpen = false;
                    loadMap(findMap(REGION_DEST[r]));
                    spawnPlayer();
                    return;
                }
            }
            if (x < px || x > px + 940 || y < py || y > py + 600) cityMapOpen = false;
            return;
        }
        // panel chọn khu đang mở là lớp trên (sau bản đồ thành phố)
        if (boardOpen) {
            if (x >= BOARD_PX + BOARD_PW - 52 && x <= BOARD_PX + BOARD_PW
                && y >= BOARD_PY + BOARD_PH - 52 && y <= BOARD_PY + BOARD_PH) {
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
        // nút burger (phải trên): mở/đóng panel kính
        if (x >= BURGER_X && x <= BURGER_X + BURGER_S && y >= BURGER_Y && y <= BURGER_Y + BURGER_S) {
            menuOpen = !menuOpen;
            return;
        }
        // nút mặt cười kế bên khung chat: mở/đóng hàng chọn biểu cảm
        if (x >= FEEL_BTN_X && x <= FEEL_BTN_X + FEEL_BTN_S && y >= FEEL_BTN_Y && y <= FEEL_BTN_Y + FEEL_BTN_S) {
            feelOpen = !feelOpen;
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
                            cityMapOpen = true;
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
        // khung chat: tab vắt ngang viền (3 tab rộng bằng nhau, ô chạm theo
        // chiều cao thật — tab đang dùng cao hơn một chút) / nút Gửi / ô nhập
        {
            float tw = chatTabWidth();
            float tx = CHAT_X + 12;
            for (int i = 0; i < CHAT_TABS.length; i++) {
                float th = i == activeChatTab ? TAB_H_ACTIVE : TAB_H_IDLE;
                float tabBottom = CHAT_Y + CHAT_H - th / 2f;
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
            // đứng gần bảng KHU (846) thì tương tác = mở danh sách khu
            if (nearestSign() != null) { openBoardRequest(); return; }
            // bong bóng trên đầu như cũ, nhưng trong khung chat thì đây là
            // thông báo HỆ THỐNG (vào tab Hệ thống, không vào tab Gần đây)
            chatBubble = "Không có ai ở gần để tương tác.";
            chatTimer = 4f;
            addChatLog(TAB_SYSTEM, null, "Không có ai ở gần để tương tác.", true);
            return;
        }
        n.bubble = (n.data.chat != null && !n.data.chat.isEmpty())
            ? n.data.chat.get(0) : "Xin chào, ta là " + n.data.name + ".";
        n.bubbleTimer = 4f;
        // Lời NPC chỉ nổi bong bóng trên đầu, KHÔNG đưa vào khung chat (chủ dự án chốt).
    }

    /** Điểm chân (medium, hệ trên–trái) có đứng được không. Hai lớp như
     *  gốc: (1) type ô — di chuyển tự do chỉ đi được type 80/51
     *  (LoadMap.isTrans trong Base.detectCollision), còn lại là nền
     *  cấm, mặt tiền shop, hàng rào, mặt đường…; (2) ô chân vật thể
     *  gắn map. Ngoài lưới = không đứng được. */
    public boolean canStand(float mx, float my) {
        if (mapDef == null || mapDef.gridW <= 0) return true;
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
        // sheet biểu cảm: chiều cao chạy mượt về nấc đích (trừ lúc đang kéo tay cầm)
        if (feelPointer < 0 || !feelDragHandle) {
            feelH += (feelHTarget - feelH) * Math.min(1f, dt * 14f);
            if (Math.abs(feelHTarget - feelH) < 0.6f) feelH = feelHTarget;
        }
        feelScroll = MathUtils.clamp(feelScroll, 0f, feelMaxScroll());
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
            else if (cellTypeAt(nx, playerY) == 29 && boardLatch <= 0f && !boardOpen) openBoardRequest();
            float ny = MathUtils.clamp(playerY + my * SPEED * dt, 30, 190);
            if (canStand(playerX, ny)) playerY = ny;
            else if (cellTypeAt(playerX, ny) == 29 && boardLatch <= 0f && !boardOpen) openBoardRequest();
            if (Math.abs(mx) > 0.05f) playerAnim.facingLeft = mx < 0;
            playerAnim.action = AvatarAnimator.RUN;
        } else {
            playerAnim.action = AvatarAnimator.STAND;
        }
        playerAnim.update(dt);
        updateNet(dt);
        updateRemotes(dt);
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
    }

    @Override
    public void resize(int width, int height) {
        hudViewport.update(width, height, true);
    }

    @Override
    public void render(float dt) {
        update(dt);
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
            busLift = CHAT_H + 20f;
            camY -= busLift; // hạ tâm camera = thế giới nhô lên trên màn hình
        }
        if (worldW <= Ui.W) camX = worldW / 2f;
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
        if (!daydien.isEmpty()) {
            float dx = 0f;
            float bottom = mapBottomPx - 60f;
            for (Texture t : daydien) { batch.draw(t, dx, bottom); dx += t.getWidth(); }
        }
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
                        it.npc.data.name, false);
                    if (it.npc.bubble != null) {
                        drawBubble(it.npc.doll, it.npc.anim, it.npc.feetX, it.npc.feetY, it.npc.bubble);
                    }
                    break;
                case DrawItem.REMOTE:
                    // tên người chơi khác màu xanh nhạt (NPC vàng, mình đỏ)
                    drawFigure(it.remote.doll, it.remote.anim, it.remote.feetX, it.remote.feetY,
                        it.remote.info.name, new Color(0.55f, 0.9f, 1f, 1f));
                    if (it.remote.bubble != null) {
                        drawBubble(it.remote.doll, it.remote.anim, it.remote.feetX, it.remote.feetY,
                            it.remote.bubble);
                    }
                    break;
                default: // PLAYER
                    drawFigure(playerDoll, playerAnim, playerFeetX, playerFeetY, displayName, true);
                    if (chatBubble != null) {
                        drawBubble(playerDoll, playerAnim, playerFeetX, playerFeetY, chatBubble);
                    }
                    break;
            }
        }
        // xe buýt vẽ sau cùng trong thế giới (ở làn đường, trước mọi nhân vật)
        if (busPhase > 0) {
            batch.draw(busTex, busX * AvatarDkGame.HD - busTex.getWidth() / 2f, 2f);
        }
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
        float topY = feetY + game.dollRenderer.heightPx(doll, frame, AvatarDkGame.HD);
        Color c = nameColor;
        Ui.fontSmall.setColor(0f, 0f, 0f, 0.85f);
        layout.setText(Ui.fontSmall, name);
        Ui.fontSmall.draw(batch, name, feetX - layout.width / 2f + 1.5f, topY + 24f - 1.5f);
        Ui.fontSmall.setColor(c);
        Ui.fontSmall.draw(batch, name, feetX - layout.width / 2f, topY + 24f);
    }

    private void drawBubble(PaperDoll doll, AvatarAnimator anim, float feetX, float feetY, String text) {
        layout.setText(Ui.fontSmall, text);
        float w = layout.width + 28f, h = 40f;
        float topY = feetY + game.dollRenderer.heightPx(doll, anim.frameIndex(), AvatarDkGame.HD);
        float x = feetX - w / 2f, y = topY + 32f;
        batch.draw(Ui.roundedRect((int) w, (int) h, 12, new Color(1f, 1f, 1f, 0.95f), new Color(0.2f, 0.2f, 0.2f, 0.9f)), x, y);
        Ui.fontSmall.setColor(0.08f, 0.08f, 0.1f, 1f);
        Ui.fontSmall.draw(batch, text, x + 14f, y + 27f);
    }

    private void drawHud(float playerFeetX, float playerFeetY) {
        hudViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        drawPlayerCard();
        drawBurger();
        if (menuOpen) drawBurgerPanel();
        drawJoystick();
        drawInteractButton();
        drawChatFrame();
        drawFeelUi();
        if (boardOpen) drawBoardPanel();
        if (cityMapOpen) drawCityMap();
        batch.end();
    }

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
        batch.draw(Ui.roundedRect((int) CHAT_W, (int) CHAT_H, 14,
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
            float tabBottom = CHAT_Y + CHAT_H - th / 2f;
            Color fill = active ? new Color(0.10f, 0.17f, 0.26f, 0.97f)
                                : new Color(0.03f, 0.06f, 0.10f, 0.92f);
            Color rim = active ? new Color(0.62f, 0.82f, 1f, 0.60f)
                               : new Color(0.45f, 0.60f, 0.80f, 0.32f);
            batch.draw(Ui.roundedRect((int) tw, (int) th, 8, fill, rim), tx, tabBottom);
            Ui.fontSmall.setColor(active ? Color.WHITE : new Color(1f, 1f, 1f, 0.55f));
            Ui.fontSmall.draw(batch, CHAT_TABS[i], textX, tabBottom + th - 9);
            tx += tw + 6;
        }
        // nhật ký của tab đang chọn: dòng mới nhất nằm ngay trên ô nhập
        List<ChatLine> log = chatLogs[activeChatTab];
        int n = Math.min((int) CHAT_LOG_LINES, log.size());
        for (int i = 0; i < n; i++) {
            ChatLine l = log.get(log.size() - n + i);
            float ly = CHAT_Y + CHAT_INPUT_H + 6 + (n - 1 - i) * CHAT_LINE_H + 15;
            if (l.system) {
                Ui.fontSmall.setColor(1f, 0.62f, 0.35f, 1f);
                Ui.fontSmall.draw(batch, l.text, CHAT_X + 14, ly);
            } else {
                Ui.fontSmall.setColor(0.65f, 0.9f, 1f, 1f);
                Ui.fontSmall.draw(batch, l.name + ":", CHAT_X + 14, ly);
                layout.setText(Ui.fontSmall, l.name + ":");
                Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
                Ui.fontSmall.draw(batch, l.text, CHAT_X + 14 + layout.width + 6, ly);
            }
        }
        if (n == 0) {
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.45f);
            Ui.fontSmall.draw(batch, activeChatTab == TAB_WORLD
                ? "Chưa có tin nhắn thế giới nào." : "Chưa có tin nhắn nào.",
                CHAT_X + 14, CHAT_Y + CHAT_INPUT_H + 27);
        }
        // ô nhập (chừa chỗ cho nút Gửi ở cuối)
        float inputW = CHAT_W - 16 - CHAT_SEND_W - 8;
        Color inRim = typing ? new Color(1f, 0.8f, 0.35f, 0.95f) : GLASS_RIM;
        batch.draw(Ui.roundedRect((int) inputW, (int) CHAT_INPUT_H, 10,
            new Color(1f, 1f, 1f, typing ? 0.96f : 0.88f), inRim), CHAT_X + 8, CHAT_Y + 7);
        if (typing) {
            Ui.fontSmall.setColor(0.1f, 0.1f, 0.12f, 1f);
            Ui.fontSmall.draw(batch, typeBuffer.toString() + "|", CHAT_X + 20, CHAT_Y + 30);
        } else {
            Ui.fontSmall.setColor(0.35f, 0.38f, 0.42f, 1f);
            Ui.fontSmall.draw(batch, "Nhấn Enter hoặc chạm vào đây để chat…", CHAT_X + 20, CHAT_Y + 30);
        }
        // nút Gửi: ICON máy bay giấy Font Awesome trên nền kính cùng tông
        // panel (chủ dự án chốt: bỏ chữ "Gửi" + bỏ màu cam)
        float sendX = CHAT_X + CHAT_W - 8 - CHAT_SEND_W;
        batch.draw(Ui.roundedRect((int) CHAT_SEND_W, (int) CHAT_INPUT_H, 10,
            new Color(0.10f, 0.17f, 0.26f, 0.97f), GLASS_RIM), sendX, CHAT_Y + 7);
        Ui.faIcon(batch, Ui.fa20, Ui.FA_SEND,
            sendX + CHAT_SEND_W / 2f, CHAT_Y + 7 + CHAT_INPUT_H / 2f, Color.WHITE);
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
            Texture icon = game.texture("iconmenu/" + MENU_ITEMS[i][0] + ".png");
            float is = 46f;
            batch.draw(icon, cx + MENU_CELL / 2f - is / 2f, cy + MENU_CELL_H - 8 - is - 8, is, is);
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.92f);
            layout.setText(Ui.fontSmall, MENU_ITEMS[i][1]);
            Ui.fontSmall.draw(batch, MENU_ITEMS[i][1], cx + MENU_CELL / 2f - layout.width / 2f, cy + 24);
        }
    }

    // ---------- Panel chọn KHU (ParkListSrc của gốc, vẽ theo tông kính) ----------
    private static final float BOARD_PW = 560f, BOARD_PH = 308f;
    private static final float BOARD_PX = (Ui.W - BOARD_PW) / 2f, BOARD_PY = (Ui.H - BOARD_PH) / 2f;
    private static final float BOARD_CELL_W = 100f, BOARD_CELL_H = 88f;

    private float boardCellX(int col) { return BOARD_PX + 14 + col * (BOARD_CELL_W + 8); }
    private float boardCellY(int row) { return BOARD_PY + BOARD_PH - 56 - (row + 1) * BOARD_CELL_H - row * 8; }

    private void drawBoardPanel() {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        batch.draw(Ui.roundedRect((int) BOARD_PW, (int) BOARD_PH, 16, GLASS_FILL_DARK, GLASS_RIM),
            BOARD_PX, BOARD_PY);
        Ui.font.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.font.draw(batch, "CHỌN KHU — " + (mapDef.name != null ? mapDef.name : ("MAP " + mapDef.id)),
            BOARD_PX + 22, BOARD_PY + BOARD_PH - 20);
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, "X", BOARD_PX + BOARD_PW - 38, BOARD_PY + BOARD_PH - 18);
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
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.7f);
        Ui.fontSmall.draw(batch, "Khu đông thì chọn khu khác — vẫn là map này, chỉ khác người.",
            BOARD_PX + 22, BOARD_PY + 30);
    }

    /** Bản đồ thành phố toàn màn hình (MiniMap của gốc): ảnh minimap + các điểm
     *  vùng theo lưới citiMap; chạm một vùng để di chuyển tới map của vùng đó. */
    private void drawCityMap() {
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(whiteTex, 0, 0, Ui.W, Ui.H);
        batch.setColor(Color.WHITE);
        float[] g = cityMapGeom();
        float px = g[0], py = g[1], iw = g[2], ih = g[3], ix = g[4], iy = g[5];
        float pw = 940f, ph = 600f;
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 16, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Ui.font.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.font.draw(batch, "BẢN ĐỒ THÀNH PHỐ", px + 24, py + ph - 20);
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, "X", px + pw - 40, py + ph - 18);
        if (minimapTex == null) minimapTex = game.texture("minimap.png");
        batch.draw(minimapTex, ix, iy, iw, ih);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.75f);
        Ui.fontSmall.draw(batch, "Chạm vào một vùng để di chuyển đến đó.", px + 24, py + 30);
        for (int r = 0; r < regionCells.size() && r < REGION_NAMES.length; r++) {
            int[] cell = regionCells.get(r);
            float cx = ix + (cell[0] + 0.5f) / 50f * iw;
            float cy = iy + ih - (cell[1] + 0.5f) / 26f * ih;
            boolean current = mapDef.id == REGION_DEST[r];
            batch.setColor(current ? new Color(0.3f, 0.9f, 0.4f, 1f) : new Color(1f, 0.55f, 0.1f, 1f));
            batch.draw(whiteTex, cx - 6, cy - 6, 12, 12);
            batch.setColor(Color.WHITE);
            layout.setText(Ui.fontSmall, REGION_NAMES[r]);
            float lx = MathUtils.clamp(cx - layout.width / 2f, px + 8, px + pw - 8 - layout.width);
            Ui.fontSmall.setColor(1f, 0.95f, 0.6f, 1f);
            Ui.fontSmall.draw(batch, REGION_NAMES[r], lx, cy + 26);
        }
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
