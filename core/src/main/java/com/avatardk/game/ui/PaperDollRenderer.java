package com.avatardk.game.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.avatardk.game.data.PaperDoll;
import java.util.HashMap;
import java.util.Map;

/** Vẽ một PaperDoll lên SpriteBatch.
 *  Quy ước tọa độ (từ APartInfo.paintAvatar của gốc): (feetX, feetY) là điểm chân theo
 *  hệ TRÁI–DƯỚI của LibGDX; mỗi part vẽ tại offset (dx, dy) đơn vị medium nhân với "unit"
 *  (số pixel cho 1 đơn vị medium; bản hd gốc unit = AvMain.hd = 2), dy âm nghĩa là part
 *  nằm TRÊN điểm chân. Vùng atlas cắt từ big[bigId] tại (x*2, y*2, w*2, h*2) — atlas hd
 *  là bản x2 của medium (AvatarData.paintPart nhân mọi tọa độ với hd).
 *  Quay trái: gốc dùng transform LEFT=2 của drawRegion kèm công thức trừ (2*dx + w)*hd
 *  khỏi x — tương đương lật ngang part quanh trục đứng qua điểm chân; ở đây dùng
 *  TextureRegion đã flip và đặt cạnh trái tại feetX - dx*unit - w*unit. */
public class PaperDollRenderer {
    private final AvatarDkGame game;
    private final Map<String, TextureRegion> cache = new HashMap<>();

    public PaperDollRenderer(AvatarDkGame game) { this.game = game; }

    private TextureRegion region(PaperDoll.DrawPart d, boolean flip) {
        String key = d.partId + ":" + d.img.itemId + ":" + flip;
        TextureRegion r = cache.get(key);
        if (r == null) {
            Texture atlas = game.atlas(d.img.bigId);
            r = new TextureRegion(atlas, d.img.x * AvatarDkGame.HD, d.img.y * AvatarDkGame.HD,
                d.img.w * AvatarDkGame.HD, d.img.h * AvatarDkGame.HD);
            if (flip) r.flip(true, false);
            cache.put(key, r);
        }
        return r;
    }

    /** Vẽ doll với điểm chân tại (feetX, feetY), unit = pixel cho 1 đơn vị medium. */
    public void draw(SpriteBatch batch, PaperDoll doll, int frameIndex, float feetX, float feetY,
                     float unit, boolean facingLeft) {
        for (PaperDoll.DrawPart d : doll.frame(frameIndex)) {
            TextureRegion r = region(d, facingLeft);
            float wPx = d.img.w * unit;
            float hPx = d.img.h * unit;
            float x = facingLeft ? feetX - d.dx * unit - wPx : feetX + d.dx * unit;
            float y = feetY - d.dy * unit - hPx;
            batch.draw(r, x, y, wPx, hPx);
        }
    }

    /** Chiều cao (pixel, theo unit) của doll ở frame cho trước — để đặt tên/bong bóng chat. */
    public float heightPx(PaperDoll doll, int frameIndex, float unit) {
        int[] b = doll.bounds(frameIndex);
        return (b[3] - b[1]) * unit;
    }
}
