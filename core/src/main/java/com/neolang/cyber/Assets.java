package com.neolang.cyber;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

import java.util.HashSet;

/** Loads textures, the 1x1 white texture and the Vietnamese fonts. Missing art is tolerated (null). */
public class Assets {
    public BitmapFont font;      // 22px regular
    public BitmapFont fontBig;   // 40px bold
    public BitmapFont fontSmall; // 17px regular
    public Texture keyartWide;
    public Texture town;
    public Texture classesTex;
    public Texture adventure;
    public Texture heroMale;
    // NOTE: hero_female.jpg is the twin-tails (toc hai ben) version of the heroine.
    public Texture heroFemale;
    // Customization sheets, v2: cute chibi eyes (round, shiny, gradient iris + sparkles).
    public Texture customMale;
    public Texture customFemale;
    public Texture createMale;
    public Texture createFemale;
    public Texture plotBg;
    // Painted sprites cut from the design sheets by tools/prep_sprites.py.
    // Index 0 = NAM, 1 = NU. Any entry may be null -> callers fall back to shape drawing.
    public Texture[] heroFront = new Texture[2];
    public Texture[] heroSide = new Texture[2];
    public Texture[] heroBack = new Texture[2];
    public Texture[][] custHead = new Texture[2][3];
    public Texture[][] custEyes = new Texture[2][3];
    public Texture[][] custFig = new Texture[2][3];
    public Texture petSpr;
    public Texture monBeetle, monBlob, monWraith, monMush, monJelly, monSpike;
    // Layered-map pass: clean background plates (far layer only, nothing baked in
    // the foreground) + separate building / prop sprites placed at interaction points.
    public Texture townPlate;
    public Texture advPlate;
    public Texture bldCafe, bldFashion, bldBank, bldPet, bldStation, bldPortal;
    public Texture propPod0, propPod1, propPod2; // hydroponic pod: sprout / plant / fruit
    public Texture propBoard, propLamp, propChest, propTable, propFlower;
    // Painted HUD icons (icon sheet 4x4, cut by prep_sprites.py --hud) + round
    // face portraits cropped from the hero front sprites. All HUD icons in the
    // game are these painted assets — never shape-drawn glyphs.
    public Texture icoHeart, icoBolt, icoFood, icoFlame, icoBattery, icoCoin, icoCrystal, icoStar;
    public Texture icoMail, icoGear, icoSmile, icoSword, icoSwirl, icoOrb, icoQuest, icoBag;
    // COZY wooden leaf-carved portrait frame (large hole) replacing the
    // old cyber ring, and the painted wooden joystick base / amber-orb knob pair.
    public Texture framePortrait;
    public Texture joyBase, joyKnob;
    // Round painted interact button (wooden ring + cream chat bubble) and the painted
    // minimap frame (wood rim + parchment pane kept whole as the map surface).
    public Texture btnTalk;
    public Texture minimapFrame;
    public Texture[] heroFace = new Texture[2];
    public Texture white;
    public float uiScale = 1f;

    private static final String VIET =
        "ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ" +
        "ĂÂĐÊÔƠƯÁÀẢÃẠẤẦẨẪẬẮẰẲẴẶÉÈẺẼẸẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌỐỒỔỖỘỚỜỞỠỢÚÙỦŨỤỨỪỬỮỰÝỲỶỸỴ" +
        "₫·—…";

    private static String buildChars() {
        StringBuilder sb = new StringBuilder();
        HashSet<Character> seen = new HashSet<Character>();
        for (char c = 32; c < 127; c++) { if (seen.add(c)) sb.append(c); }
        for (int i = 0; i < VIET.length(); i++) {
            char c = VIET.charAt(i);
            if (seen.add(c)) sb.append(c);
        }
        return sb.toString();
    }

    private Texture tex(String path) {
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal(path);
            if (!fh.exists()) {
                System.err.println("[NeoLang/Assets] Khong tim thay art: " + path);
                return null;
            }
            Texture t = new Texture(fh);
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            System.err.println("[NeoLang/Assets] Da nap " + path + " -> " + t.getWidth() + "x" + t.getHeight());
            return t;
        } catch (Exception e) {
            System.err.println("[NeoLang/Assets] Loi nap art " + path + ": " + e);
            return null;
        }
    }

    public void load(float scale) {
        keyartWide = tex("art/keyart_wide.jpg");
        town = tex("art/town.jpg");
        classesTex = tex("art/classes.jpg");
        adventure = tex("art/adventure.jpg");
        heroMale = tex("art/hero_male.jpg");
        heroFemale = tex("art/hero_female.jpg");
        customMale = tex("art/custom_male.jpg");
        customFemale = tex("art/custom_female.jpg");
        createMale = tex("art/create_male.jpg");
        createFemale = tex("art/create_female.jpg");
        plotBg = tex("art/plot.jpg");
        String[] gp = {"m", "f"};
        for (int i = 0; i < 2; i++) {
            heroFront[i] = tex("art/sprites/hero_" + gp[i] + "_front.png");
            heroSide[i] = tex("art/sprites/hero_" + gp[i] + "_side.png");
            heroBack[i] = tex("art/sprites/hero_" + gp[i] + "_back.png");
            for (int j = 0; j < 3; j++) {
                custHead[i][j] = tex("art/sprites/cust_" + gp[i] + "_head" + j + ".png");
                custEyes[i][j] = tex("art/sprites/cust_" + gp[i] + "_eyes" + j + ".png");
                custFig[i][j] = tex("art/sprites/cust_" + gp[i] + "_fig" + j + ".png");
            }
        }
        petSpr = tex("art/sprites/pet.png");
        monBeetle = tex("art/sprites/mon_beetle.png");
        monBlob = tex("art/sprites/mon_blob.png");
        monWraith = tex("art/sprites/mon_wraith.png");
        monMush = tex("art/sprites/mon_mush.png");
        monJelly = tex("art/sprites/mon_jelly.png");
        monSpike = tex("art/sprites/mon_spike.png");
        townPlate = tex("art/town_plate.jpg");
        advPlate = tex("art/adv_plate.jpg");
        bldCafe = tex("art/sprites/bld_cafe.png");
        bldFashion = tex("art/sprites/bld_fashion.png");
        bldBank = tex("art/sprites/bld_bank.png");
        bldPet = tex("art/sprites/bld_pet.png");
        bldStation = tex("art/sprites/bld_station.png");
        bldPortal = tex("art/sprites/bld_portal.png");
        propPod0 = tex("art/sprites/prop_pod0.png");
        propPod1 = tex("art/sprites/prop_pod1.png");
        propPod2 = tex("art/sprites/prop_pod2.png");
        propBoard = tex("art/sprites/prop_board.png");
        propLamp = tex("art/sprites/prop_lamp.png");
        propChest = tex("art/sprites/prop_chest.png");
        propTable = tex("art/sprites/prop_table.png");
        propFlower = tex("art/sprites/prop_flower.png");
        icoHeart = tex("art/sprites/ico_heart.png");
        icoBolt = tex("art/sprites/ico_bolt.png");
        icoFood = tex("art/sprites/ico_food.png");
        icoFlame = tex("art/sprites/ico_flame.png");
        icoBattery = tex("art/sprites/ico_battery.png");
        icoCoin = tex("art/sprites/ico_coin.png");
        icoCrystal = tex("art/sprites/ico_crystal.png");
        icoStar = tex("art/sprites/ico_star.png");
        icoMail = tex("art/sprites/ico_mail.png");
        icoGear = tex("art/sprites/ico_gear.png");
        icoSmile = tex("art/sprites/ico_smile.png");
        icoSword = tex("art/sprites/ico_sword.png");
        icoSwirl = tex("art/sprites/ico_swirl.png");
        icoOrb = tex("art/sprites/ico_orb.png");
        icoQuest = tex("art/sprites/ico_quest.png");
        icoBag = tex("art/sprites/ico_bag.png");
        framePortrait = tex("art/sprites/frame_portrait.png");
        joyBase = tex("art/sprites/joy_base.png");
        joyKnob = tex("art/sprites/joy_knob.png");
        btnTalk = tex("art/sprites/btn_talk.png");
        minimapFrame = tex("art/sprites/minimap_frame.png");
        heroFace[0] = tex("art/sprites/hero_m_face.png");
        heroFace[1] = tex("art/sprites/hero_f_face.png");
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        white = new Texture(pm);
        pm.dispose();
        rebuildFonts(scale);
    }

    public void rebuildFonts(float scale) {
        uiScale = scale;
        if (font != null) font.dispose();
        if (fontBig != null) fontBig.dispose();
        if (fontSmall != null) fontSmall.dispose();
        String chars = buildChars();
        try {
            FreeTypeFontGenerator reg = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Regular.ttf"));
            FreeTypeFontGenerator bold = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Bold.ttf"));
            font = gen(reg, Math.max(12, Math.round(22 * scale)), chars);
            fontSmall = gen(reg, Math.max(10, Math.round(17 * scale)), chars);
            fontBig = gen(bold, Math.max(18, Math.round(40 * scale)), chars);
            reg.dispose();
            bold.dispose();
        } catch (Exception e) {
            // Fallback: default bitmap font (no Vietnamese glyphs, but never crash).
            if (font == null) font = new BitmapFont();
            if (fontSmall == null) fontSmall = new BitmapFont();
            if (fontBig == null) fontBig = new BitmapFont();
        }
    }

    private BitmapFont gen(FreeTypeFontGenerator g, int size, String chars) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size;
        p.characters = chars;
        p.color = Color.WHITE;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        return g.generateFont(p);
    }

    public void dispose() {
        if (font != null) font.dispose();
        if (fontBig != null) fontBig.dispose();
        if (fontSmall != null) fontSmall.dispose();
        Texture[] all = {keyartWide, town, classesTex, adventure, heroMale, heroFemale, customMale, customFemale, createMale, createFemale, plotBg, petSpr,
            monBeetle, monBlob, monWraith, monMush, monJelly, monSpike, white,
            townPlate, advPlate, bldCafe, bldFashion, bldBank, bldPet, bldStation, bldPortal,
            propPod0, propPod1, propPod2, propBoard, propLamp, propChest, propTable, propFlower,
            icoHeart, icoBolt, icoFood, icoFlame, icoBattery, icoCoin, icoCrystal, icoStar,
            icoMail, icoGear, icoSmile, icoSword, icoSwirl, icoOrb, icoQuest, icoBag,
            framePortrait, joyBase, joyKnob, btnTalk, minimapFrame,
            heroFace[0], heroFace[1]};
        for (Texture t : all) if (t != null) t.dispose();
        for (int i = 0; i < 2; i++) {
            if (heroFront[i] != null) heroFront[i].dispose();
            if (heroSide[i] != null) heroSide[i].dispose();
            if (heroBack[i] != null) heroBack[i].dispose();
            for (int j = 0; j < 3; j++) {
                if (custHead[i][j] != null) custHead[i][j].dispose();
                if (custEyes[i][j] != null) custEyes[i][j].dispose();
                if (custFig[i][j] != null) custFig[i][j].dispose();
            }
        }
    }
}
