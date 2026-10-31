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
import com.avatardk.game.sim.AvatarAnimator;
import java.util.ArrayList;
import java.util.List;

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
 *  NPC: lọc npcs.json theo map. Riêng map 10, NPC duy nhất trong dữ liệu (Chủ Hôn, 278/68)
 *  chỉ mặc part động 2480 (server stream, không có ảnh offline) nên chỉ hiện tên;
 *  đợt 1 đồng thời đặt 2 NPC của map 9 — khu thành phố còn lại trong dữ liệu gốc
 *  (quay số 514/48, đấu giá 410/48) — để có NPC lắp được đứng trong thành phố. */
public class WorldScreen extends ScreenAdapter {
    private static class MapDef {
        final int id; final String name; final int strips; final boolean daydien;
        /** Số nền theo bảng bg[] của LoadMap gốc (chỉ số = id map - 1). */
        final int bgNum;
        MapDef(int id, String name, int strips, boolean daydien, int bgNum) {
            this.id = id; this.name = name; this.strips = strips; this.daydien = daydien;
            this.bgNum = bgNum;
        }
    }
    private static final MapDef[] MAPS = {
        new MapDef(10, "Thành phố", 3, true, 0),
        new MapDef(24, "Khu mua sắm", 3, false, 0),
        new MapDef(25, "Nông trại", 6, false, 2),
    };

    // ---- Nền (port từ LoadMap.loadBG + paintBackGround của gốc) ----
    // status: 0 = ngày (06–18h), 1 = đêm — gốc lấy theo giờ máy lúc vào map.
    // colorBg[status] của gốc: ngày 6143735 = #5DBCE7, đêm 21 = #000015.
    private static final Color[] SKY_COLOR = {
        new Color(0x5D / 255f, 0xBC / 255f, 0xE7 / 255f, 1f),
        new Color(0f, 0f, 0x15 / 255f, 1f),
    };
    /** x0_imgTreeBg theo số nền (đơn vị hd): bg0=20, bg1=25, bg2=10, bg3=33 (×hd). */
    private static final int[] X0_TREE_HD = {20, 25, 10, 33};
    /** x0_imgBG theo số nền (đơn vị hd): bg0=2, bg1=20, bg2=15, bg3=0 (×hd). */
    private static final int[] X0_BG_HD = {2, 20, 15, 0};
    private int bgStatus = 0;
    private Texture bgSkyTex, bgLineTex;
    private final Texture[] cloudTex = new Texture[2];
    private static class Cloud { float x100, yW, speed; int anchor; }
    private final List<Cloud> clouds = new ArrayList<>();
    private Texture whiteTex;
    /** Đáy map theo gốc: Hmap(8) * w(24) * hd(2) = 384px ở thang file hd. */
    private static final float MAP_BOTTOM_PX = 8 * 24 * AvatarDkGame.HD;
    private static final float SPEED = 78f; // medium/giây (gốc: v = 4 medium/tick)

    private final AvatarDkGame game;
    private final SpriteBatch batch = new SpriteBatch();
    private final FitViewport hudViewport = new FitViewport(Ui.W, Ui.H);
    private final OrthographicCamera worldCam = new OrthographicCamera(Ui.W, Ui.H);
    private final GlyphLayout layout = new GlyphLayout();

    private MapDef mapDef;
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

    private PaperDoll playerDoll;
    private final AvatarAnimator playerAnim = new AvatarAnimator();
    private float playerX = 200f, playerY = 150f; // medium, gốc trên–trái

    private String chatBubble = null;
    private float chatTimer = 0f;
    private boolean typing = false;

    // ---- Khung chat GIỮA DƯỚI (theo yêu cầu của chủ dự án: khung chat luôn hiện
    // ở giữa dưới màn hình thay vì phải bấm icon chat như bản gốc) ----
    private static final float CHAT_W = 620f, CHAT_X = Ui.W / 2f - CHAT_W / 2f, CHAT_Y = 12f;
    private static final float CHAT_INPUT_H = 36f, CHAT_LOG_LINES = 4, CHAT_LINE_H = 22f;
    private static final float CHAT_H = CHAT_INPUT_H + CHAT_LOG_LINES * CHAT_LINE_H + 16f;
    private static class ChatLine { String name, text; boolean npc; }
    private final List<ChatLine> chatLog = new ArrayList<>();

    private void addChatLog(String name, String text, boolean npc) {
        ChatLine l = new ChatLine();
        l.name = name; l.text = text; l.npc = npc;
        chatLog.add(l);
        while (chatLog.size() > 40) chatLog.remove(0);
    }
    private final StringBuilder typeBuffer = new StringBuilder();
    private boolean menuOpen = false;
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
        loadMap(MAPS[0]);
    }

    public void loadMap(MapDef def) {
        mapDef = def;
        strips.clear(); stripLefts.clear(); daydien.clear();
        float acc = -1f;
        worldH = 0f;
        for (int i = 0; i < def.strips; i++) {
            Texture t = game.texture("imagemap/" + def.id + "/" + i + ".png");
            strips.add(t);
            stripLefts.add(acc);
            acc += t.getWidth() - 2;
            worldH = Math.max(worldH, t.getHeight());
        }
        worldW = acc + 1f;
        if (def.daydien) {
            for (int i = 0; i < 3; i++) daydien.add(game.texture("imagemap/" + def.id + "/daydien" + i + ".png"));
        }
        loadBackground(def);
        buildNpcs();
        // giữ người chơi trong biên map mới
        playerX = MathUtils.clamp(playerX, 24, worldW / AvatarDkGame.HD - 24);
        playerY = MathUtils.clamp(playerY, 36, 186);
    }

    /** Nạp lớp nền đúng LoadMap.loadBG: status theo giờ máy (18h–6h = đêm),
     *  ảnh trời bghd/may<num><status> và ảnh silhouette bghd/<num><status>,
     *  mây trôi effect/cl<anchor><status>. */
    private void loadBackground(MapDef def) {
        int hour = java.time.LocalTime.now().getHour();
        bgStatus = (hour >= 18 || hour < 6) ? 1 : 0;
        bgSkyTex = game.texture("bghd/may" + def.bgNum + bgStatus + ".png");
        bgLineTex = game.texture("bghd/" + def.bgNum + bgStatus + ".png");
        cloudTex[0] = game.texture("effect/cl0" + bgStatus + ".png");
        cloudTex[1] = game.texture("effect/cl1" + bgStatus + ".png");
        clouds.clear();
        java.util.Random rnd = new java.util.Random(1234 + def.id);
        for (int i = 0; i < 8; i++) {
            Cloud c = new Cloud();
            c.anchor = i % 2;
            c.x100 = rnd.nextFloat() * (2200 + 800) * 100f;
            // dải mây của gốc nằm trên nóc ảnh silhouette một đoạn
            c.yW = MAP_BOTTOM_PX + bgLineTex.getHeight() / 2f + 30 + rnd.nextFloat() * 170f;
            c.speed = 900 + rnd.nextFloat() * 900; // đơn vị x100/giây (~9–18 px/giây)
            clouds.add(c);
        }
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
        List<NpcData> list = new ArrayList<>(game.data.npcsForMap(mapDef.id));
        if (mapDef.id == 10) list.addAll(game.data.npcsForMap(9)); // xem ghi chú đầu lớp
        for (NpcData n : list) {
            NpcFigure f = new NpcFigure();
            f.data = n;
            f.doll = game.newDoll(n.items);
            f.feetX = n.x * AvatarDkGame.HD;
            f.feetY = MAP_BOTTOM_PX - n.y * AvatarDkGame.HD;
            f.anim.action = AvatarAnimator.STAND;
            npcFigures.add(f);
        }
    }

    // ---------- API cho ScreenshotMain / kiểm chứng ----------
    public void debugSetMap(int mapId) {
        for (MapDef d : MAPS) if (d.id == mapId) { loadMap(d); return; }
    }
    public void debugOpenMenu() { menuOpen = true; }
    public void debugInteract() { interact(); }
    public void debugSetPlayer(float mediumX, float mediumY) { playerX = mediumX; playerY = mediumY; }
    public void debugChat(String text) { showBubble(text); }
    public int mapId() { return mapDef.id; }
    public int npcCount() { return npcFigures.size(); }
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
        addChatLog(AvatarDkGame.SON_NAME, text, false);
    }

    @Override
    public void show() {
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
                if (pointer == joyPointer) { joyPointer = -1; joyVecX = 0; joyVecY = 0; }
                return false;
            }
        });
    }

    private void onTouch(float x, float y, int pointer) {
        // nút burger (phải trên): mở/đóng panel kính
        if (x >= BURGER_X && x <= BURGER_X + BURGER_S && y >= BURGER_Y && y <= BURGER_Y + BURGER_S) {
            menuOpen = !menuOpen;
            return;
        }
        // panel burger đang mở: chạm trong panel thì giữ, chạm ra ngoài thì đóng
        // (các vị trí/map đã bỏ khỏi menu theo yêu cầu — chuyển map tạm bằng phím 1/2/3)
        if (menuOpen) {
            float px = Ui.W - 330f, pw = 312f, ph = 104f, py = Ui.H - 84 - ph;
            if (x >= px && x <= px + pw && y >= py && y <= py + ph) return;
            menuOpen = false;
            return;
        }
        // ô nhập của khung chat giữa dưới: chạm vào là gõ
        if (x >= CHAT_X && x <= CHAT_X + CHAT_W && y >= CHAT_Y && y <= CHAT_Y + CHAT_INPUT_H + 6) {
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
            showBubble("Không có ai ở gần để tương tác.");
            return;
        }
        n.bubble = (n.data.chat != null && !n.data.chat.isEmpty())
            ? n.data.chat.get(0) : "Xin chào, ta là " + n.data.name + ".";
        n.bubbleTimer = 4f;
        // Lời NPC chỉ nổi bong bóng trên đầu, KHÔNG đưa vào khung chat (chủ dự án chốt).
    }

    private void update(float dt) {
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
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) loadMap(MAPS[0]);
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) loadMap(MAPS[1]);
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) loadMap(MAPS[2]);
        }
        if (joyPointer >= 0) { mx += joyVecX; my -= joyVecY; }
        float len = (float) Math.hypot(mx, my);
        boolean moving = len > 0.01f;
        if (moving) {
            mx /= Math.max(1f, len); my /= Math.max(1f, len);
            playerX += mx * SPEED * dt;
            playerY += my * SPEED * dt;
            playerX = MathUtils.clamp(playerX, 16, worldW / AvatarDkGame.HD - 16);
            playerY = MathUtils.clamp(playerY, 30, 190);
            if (Math.abs(mx) > 0.05f) playerAnim.facingLeft = mx < 0;
            playerAnim.action = AvatarAnimator.RUN;
        } else {
            playerAnim.action = AvatarAnimator.STAND;
        }
        playerAnim.update(dt);
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
            else {
                typing = false;
                String msg = typeBuffer.toString().trim();
                if (!msg.isEmpty()) showBubble(msg);
            }
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
        Color sky = SKY_COLOR[bgStatus];
        Gdx.gl.glClearColor(sky.r, sky.g, sky.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float playerFeetX = playerX * AvatarDkGame.HD;
        float playerFeetY = MAP_BOTTOM_PX - playerY * AvatarDkGame.HD;
        // camera bám người chơi, kẹp biên thế giới
        float camX = MathUtils.clamp(playerFeetX, Ui.W / 2f, Math.max(Ui.W / 2f, worldW - Ui.W / 2f));
        // map thấp hơn màn hình: neo đáy map vào đáy màn hình (phần trên nhường cho nền trời)
        float camY = worldH <= Ui.H ? Ui.H / 2f : MathUtils.clamp(playerFeetY + 120, Ui.H / 2f, worldH - Ui.H / 2f);
        if (worldW <= Ui.W) camX = worldW / 2f;
        worldCam.position.set(camX, camY, 0);
        worldCam.update();

        drawBackground(camX, camY);

        batch.setProjectionMatrix(worldCam.combined);
        batch.begin();
        for (int i = 0; i < strips.size(); i++) {
            Texture t = strips.get(i);
            batch.draw(t, stripLefts.get(i), 0);
        }
        if (!daydien.isEmpty()) {
            float dx = 0f;
            float bottom = MAP_BOTTOM_PX - 60f;
            for (Texture t : daydien) { batch.draw(t, dx, bottom); dx += t.getWidth(); }
        }
        // NPC trước/người chơi sau không theo độ sâu ở đợt 1: vẽ NPC rồi người chơi
        for (NpcFigure f : npcFigures) {
            drawFigure(f.doll, f.anim, f.feetX, f.feetY, f.data.name, false);
            if (f.bubble != null) drawBubble(f.doll, f.anim, f.feetX, f.feetY, f.bubble);
        }
        drawFigure(playerDoll, playerAnim, playerFeetX, playerFeetY, AvatarDkGame.SON_NAME, true);
        if (chatBubble != null) drawBubble(playerDoll, playerAnim, playerFeetX, playerFeetY, chatBubble);
        batch.end();

        drawHud(playerFeetX, playerFeetY);
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
        int bg = mapDef.bgNum;
        // lớp trời (may)
        float skyBottomY = MAP_BOTTOM_PX + X0_TREE_HD[bg] * AvatarDkGame.HD - visBottom;
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
        float lineBottomY = MAP_BOTTOM_PX - X0_BG_HD[bg] * AvatarDkGame.HD - visBottom;
        int step2 = bgLineTex.getWidth() - 2;
        float startX = -1f - (camX * 0.4f) % step2;
        for (float x = startX; x < Ui.W; x += step2) batch.draw(bgLineTex, x, lineBottomY);
        batch.end();
    }

    private void drawFigure(PaperDoll doll, AvatarAnimator anim, float feetX, float feetY, String name, boolean isPlayer) {
        int frame = anim.frameIndex();
        game.dollRenderer.draw(batch, doll, frame, feetX, feetY, AvatarDkGame.HD, anim.facingLeft);
        float topY = feetY + game.dollRenderer.heightPx(doll, frame, AvatarDkGame.HD);
        // tên trên đầu: người chơi màu đỏ, NPC màu vàng — đúng Avatar.paintName của gốc
        Color c = isPlayer ? new Color(1f, 0.35f, 0.3f, 1f) : new Color(1f, 0.9f, 0.35f, 1f);
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
        batch.end();
    }

    /** Khung chat GIỮA DƯỚI, luôn hiện: trên là nhật ký chat gần nhất (tên người
     *  chơi màu trắng, NPC màu vàng), dưới là ô nhập — Enter/chạm vào ô để gõ,
     *  Enter lần nữa để gửi (lời chat đồng thời nổi bong bóng trên đầu như gốc).
     *  Khi nối server ở đợt 2, khung này dùng luôn cho gói chat 55/77 của gốc. */
    private void drawChatFrame() {
        batch.draw(Ui.roundedRect((int) CHAT_W, (int) CHAT_H, 14,
            new Color(0.04f, 0.09f, 0.15f, 0.62f), GLASS_RIM), CHAT_X, CHAT_Y);
        // nhật ký: dòng mới nhất nằm ngay trên ô nhập
        int n = Math.min((int) CHAT_LOG_LINES, chatLog.size());
        for (int i = 0; i < n; i++) {
            ChatLine l = chatLog.get(chatLog.size() - n + i);
            float ly = CHAT_Y + CHAT_INPUT_H + 12 + (n - 1 - i) * CHAT_LINE_H + 15;
            Ui.fontSmall.setColor(l.npc ? new Color(1f, 0.85f, 0.45f, 1f) : new Color(0.65f, 0.9f, 1f, 1f));
            Ui.fontSmall.draw(batch, l.name + ":", CHAT_X + 14, ly);
            layout.setText(Ui.fontSmall, l.name + ":");
            Ui.fontSmall.setColor(1f, 1f, 1f, 0.95f);
            Ui.fontSmall.draw(batch, l.text, CHAT_X + 14 + layout.width + 6, ly);
        }
        // ô nhập
        Color inRim = typing ? new Color(1f, 0.8f, 0.35f, 0.95f) : GLASS_RIM;
        batch.draw(Ui.roundedRect((int) (CHAT_W - 16), (int) CHAT_INPUT_H, 10,
            new Color(1f, 1f, 1f, typing ? 0.96f : 0.88f), inRim), CHAT_X + 8, CHAT_Y + 7);
        if (typing) {
            Ui.fontSmall.setColor(0.1f, 0.1f, 0.12f, 1f);
            Ui.fontSmall.draw(batch, typeBuffer.toString() + "|", CHAT_X + 20, CHAT_Y + 30);
        } else {
            Ui.fontSmall.setColor(0.35f, 0.38f, 0.42f, 1f);
            Ui.fontSmall.draw(batch, "Nhấn Enter hoặc chạm vào đây để chat…", CHAT_X + 20, CHAT_Y + 30);
        }
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
        layout.setText(Ui.fontSmall, "Lv.50");
        float badgeW = layout.width + 10, badgeH = 17;
        float badgeX = avX + avS - badgeW + 5, badgeY = avY - 5;
        batch.draw(Ui.roundedRect((int) badgeW, (int) badgeH, 6,
            new Color(0.10f, 0.08f, 0.02f, 0.92f), new Color(1f, 0.8f, 0.35f, 0.9f)), badgeX, badgeY);
        Ui.fontSmall.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontSmall.draw(batch, "Lv.50", badgeX + 5, badgeY + 13.5f);
        Ui.fontSmall.getData().setScale(1f);
        // tên nhân vật
        Ui.font.setColor(Color.WHITE);
        Ui.font.draw(batch, AvatarDkGame.SON_NAME, CARD_X + 106, cardY + CARD_H - 24);
        // tiền tệ GỌN một hàng: [icon xu] số · [icon lượng] số — icon là asset gốc
        Texture iconXu = game.texture("myinfo/icon0.png");
        Texture iconLuong = game.texture("myinfo/icon1.png");
        float cy = cardY + 20;
        batch.draw(iconXu, CARD_X + 106, cy, 21, 22);
        Ui.fontSmall.setColor(Color.WHITE);
        Ui.fontSmall.draw(batch, "12.500", CARD_X + 133, cy + 17);
        layout.setText(Ui.fontSmall, "12.500");
        float x2 = CARD_X + 133 + layout.width + 14;
        batch.draw(iconLuong, x2, cy + 2, 24, 17);
        Ui.fontSmall.draw(batch, "260", x2 + 30, cy + 17);
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

    /** Panel kính mở từ nút burger. Các vị trí/map ĐÃ BỎ khỏi menu theo yêu cầu
     *  của chủ dự án — panel chỉ còn tiêu đề và ghi chú; mục tính năng sẽ thêm
     *  khi từng đợt port mở ra (chuyển map tạm thời bằng phím 1/2/3). */
    private void drawBurgerPanel() {
        float px = Ui.W - 330f, pw = 312f, ph = 104f;
        float py = Ui.H - 84 - ph;
        batch.draw(Ui.roundedRect((int) pw, (int) ph, 14, GLASS_FILL_DARK, GLASS_RIM), px, py);
        Ui.fontSmall.setColor(1f, 0.85f, 0.45f, 1f);
        Ui.fontSmall.draw(batch, "MENU", px + 18, py + ph - 22);
        Ui.fontSmall.setColor(1f, 1f, 1f, 0.7f);
        Ui.fontSmall.draw(batch, "Tính năng mở theo từng đợt.", px + 18, py + 46);
        Ui.fontSmall.draw(batch, "Đợt 2: tài khoản + online.", px + 18, py + 22);
    }

    /** Trái dưới — joystick glass. */
    private void drawJoystick() {
        batch.draw(Ui.roundedRect((int) (JOY_R * 2), (int) (JOY_R * 2), (int) JOY_R,
            new Color(0.05f, 0.11f, 0.18f, 0.45f), GLASS_RIM), JOY_CX - JOY_R, JOY_CY - JOY_R);
        float kx = JOY_CX + joyVecX * 26f, ky = JOY_CY + joyVecY * 26f;
        batch.draw(Ui.roundedRect(56, 56, 28, new Color(1f, 1f, 1f, 0.30f), GLASS_RIM), kx - 28, ky - 28);
    }

    /** Phải dưới — nút tương tác glass: sáng viền vàng khi có NPC trong tầm,
     *  kèm nhãn tên NPC đó; chưa có ai thì để mờ. */
    private void drawInteractButton() {
        boolean active = nearestNpc != null;
        Color fill = active ? new Color(0.45f, 0.32f, 0.08f, 0.72f) : GLASS_FILL;
        Color rim = active ? new Color(1f, 0.8f, 0.35f, 0.95f) : GLASS_RIM;
        batch.draw(Ui.roundedRect((int) (ACT_R * 2), (int) (ACT_R * 2), (int) ACT_R, fill, rim),
            ACT_CX - ACT_R, ACT_CY - ACT_R);
        // glyph bong bóng thoại vẽ bằng hình (không emoji)
        batch.draw(Ui.roundedRect(36, 26, 8, new Color(1f, 1f, 1f, active ? 0.95f : 0.6f), null),
            ACT_CX - 18, ACT_CY - 6);
        batch.draw(Ui.roundedRect(10, 10, 2, new Color(1f, 1f, 1f, active ? 0.95f : 0.6f), null),
            ACT_CX - 10, ACT_CY - 14);
        Ui.fontSmall.setColor(0.1f, 0.12f, 0.16f, 1f);
        Ui.fontSmall.draw(batch, "...", ACT_CX - 11, ACT_CY + 14);
        // nhãn dưới nút
        Ui.fontSmall.setColor(0f, 0f, 0f, 0.7f);
        layout.setText(Ui.fontSmall, "Tương tác");
        Ui.fontSmall.draw(batch, "Tương tác", ACT_CX - layout.width / 2f + 1, ACT_CY - ACT_R - 9);
        Ui.fontSmall.setColor(Color.WHITE);
        Ui.fontSmall.draw(batch, "Tương tác", ACT_CX - layout.width / 2f, ACT_CY - ACT_R - 8);
        if (active) {
            layout.setText(Ui.fontSmall, nearestNpc.data.name);
            float pw = layout.width + 22;
            batch.draw(Ui.roundedRect((int) pw, 32, 10, GLASS_FILL_DARK, rim),
                ACT_CX - pw / 2f, ACT_CY + ACT_R + 10);
            Ui.fontSmall.setColor(1f, 0.9f, 0.5f, 1f);
            Ui.fontSmall.draw(batch, nearestNpc.data.name, ACT_CX - layout.width / 2f, ACT_CY + ACT_R + 31);
        }
    }

    @Override
    public void dispose() { batch.dispose(); }
}
