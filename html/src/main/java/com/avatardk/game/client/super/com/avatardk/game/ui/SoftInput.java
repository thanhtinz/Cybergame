package com.avatardk.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bản WEB của SoftInput (super-source thay bản core no-op): phủ một ô
 * &lt;input&gt; DOM thật lên đúng vị trí ô nhập của game. Listener chạm
 * gắn thẳng vào canvas nên việc focus() xảy ra NGAY trong handler chạm
 * của trình duyệt — điều kiện để iOS/Android mở bàn phím mềm. Chữ trong
 * ô DOM được đồng bộ về Target (StringBuilder của game) mỗi khung hình;
 * game vẫn tự vẽ ô nhập của nó ở dưới lớp phủ.
 */
public class SoftInput {

    public interface Target {
        String get();
        void set(String value);
        void onEnter();
    }

    public static Target of(final StringBuilder b, final int max, final Runnable enter) {
        return new Target() {
            @Override public String get() { return b.toString(); }
            @Override public void set(String v) {
                if (v == null) v = "";
                v = v.replace('\n', ' ').replace('\r', ' ');
                if (v.length() > max) v = v.substring(0, max);
                if (!v.contentEquals(b)) {
                    b.setLength(0);
                    b.append(v);
                }
            }
            @Override public void onEnter() { if (enter != null) enter.run(); }
        };
    }

    private static class Reg {
        Target target;
        boolean password;
        boolean dark;
        float x, y, w, h, pad;
    }

    private static final Map<String, Reg> REGS = new HashMap<String, Reg>();
    private static final Map<String, float[]> KEEP = new HashMap<String, float[]>();
    private static final Set<String> SEEN = new HashSet<String>();
    private static final Set<String> SEEN_KEEP = new HashSet<String>();
    private static Viewport viewport;
    private static String activeId;
    private static boolean domReady;

    public static void setViewport(Viewport v) {
        viewport = v;
        pushMap();
    }

    private static void pushMap() {
        if (viewport == null) return;
        nativeSetMap(viewport.getScreenX(), viewport.getScreenY(),
            viewport.getScreenWidth(), viewport.getScreenHeight());
    }

    public static void beginFrame() {
        SEEN.clear();
        SEEN_KEEP.clear();
        pushMap();
    }

    public static void register(String id, Target t, boolean password, boolean dark,
                                float x, float y, float w, float h, float padLeft) {
        Reg r = REGS.get(id);
        if (r == null) {
            r = new Reg();
            REGS.put(id, r);
        }
        r.target = t;
        r.password = password;
        r.dark = dark;
        r.x = x; r.y = y; r.w = w; r.h = h; r.pad = padLeft;
        SEEN.add(id);
    }

    public static void registerKeepAlive(String id, float x, float y, float w, float h) {
        KEEP.put(id, new float[]{x, y, w, h});
        SEEN_KEEP.add(id);
    }

    public static void endFrame() {
        if (activeId == null) return;
        Reg r = REGS.get(activeId);
        if (r == null || !SEEN.contains(activeId)) {
            closeActive();
            return;
        }
        r.target.set(nativeValue());
        nativeSetType(r.password);
        positionActive(r);
    }

    public static void hide() {
        if (activeId != null) closeActive();
        nativeHide();
    }

    private static void closeActive() {
        if (activeId != null) {
            Reg r = REGS.get(activeId);
            if (r != null) r.target.set(nativeValue());
        }
        activeId = null;
        nativeHide();
    }

    /** DOM gọi đồng bộ trong handler chạm thật của canvas. */
    public static void onDomPoint(float gx, float gy) {
        for (Map.Entry<String, Reg> e : REGS.entrySet()) {
            if (!SEEN.contains(e.getKey())) continue;
            Reg r = e.getValue();
            if (gx >= r.x && gx <= r.x + r.w && gy >= r.y && gy <= r.y + r.h) {
                openActive(e.getKey(), r);
                return;
            }
        }
        for (Map.Entry<String, float[]> e : KEEP.entrySet()) {
            if (!SEEN_KEEP.contains(e.getKey())) continue;
            float[] k = e.getValue();
            if (gx >= k[0] && gx <= k[0] + k[2] && gy >= k[1] && gy <= k[1] + k[3]) return;
        }
        if (activeId != null) closeActive();
    }

    /** DOM gọi khi bấm Enter trong ô nhập. */
    public static void onDomEnter() {
        if (activeId == null) return;
        Reg r = REGS.get(activeId);
        if (r != null) {
            r.target.set(nativeValue());
            r.target.onEnter();
        }
    }

    private static void openActive(String id, Reg r) {
        if (!domReady) {
            nativeInstall();
            domReady = true;
            pushMap();
        }
        if (id.equals(activeId)) {
            positionActive(r);
            nativeFocus();
            return;
        }
        if (activeId != null) {
            Reg old = REGS.get(activeId);
            if (old != null) old.target.set(nativeValue());
        }
        activeId = id;
        nativeShow(r.target.get(), r.password, r.dark);
        positionActive(r);
        nativeFocus();
    }

    private static void positionActive(Reg r) {
        if (viewport == null) return;
        double vx = viewport.getScreenX(), vy = viewport.getScreenY();
        double vw = viewport.getScreenWidth(), vh = viewport.getScreenHeight();
        double dx = vx + (r.x / Ui.W) * vw;
        double dw = (r.w / Ui.W) * vw;
        double dh = (r.h / Ui.H) * vh;
        double dTop = vy + (1.0 - (r.y + r.h) / (double) Ui.H) * vh;
        double padCss = r.pad * dw / Math.max(1f, r.w);
        nativePosition(dx, dTop, dw, dh, padCss);
    }

    // ---------- JSNI ----------

    private static native void nativeInstall() /*-{
        var doc = $doc;
        var canvas = doc.querySelector('canvas');
        if (!canvas) return;
        var el = doc.getElementById('avatardk-softinput');
        if (!el) {
            el = doc.createElement('input');
            el.id = 'avatardk-softinput';
            el.setAttribute('autocomplete', 'off');
            el.setAttribute('autocapitalize', 'off');
            el.setAttribute('spellcheck', 'false');
            el.style.position = 'absolute';
            el.style.display = 'none';
            el.style.zIndex = '99999';
            el.style.boxSizing = 'border-box';
            el.style.borderRadius = '10px';
            el.style.outline = 'none';
            el.style.fontFamily = 'sans-serif';
            doc.body.appendChild(el);
            el.addEventListener('keydown', function (ev) {
                if (ev.key === 'Enter' || ev.keyCode === 13) {
                    @com.avatardk.game.ui.SoftInput::onDomEnter()();
                }
            });
        }
        $wnd.__siEl = el;
        $wnd.__siCanvas = canvas;
        var toGame = function (clientX, clientY) {
            var rect = canvas.getBoundingClientRect();
            if (rect.width <= 0 || rect.height <= 0) return null;
            var devX = (clientX - rect.left) * canvas.width / rect.width;
            var devY = (clientY - rect.top) * canvas.height / rect.height;
            var m = $wnd.__siMap;
            if (!m) return null;
            var gx = (devX - m[0]) / m[2] * 1280;
            var gy = (1 - (devY - m[1]) / m[3]) * 720;
            return [gx, gy];
        };
        var handler = function (ev) {
            var t = (ev.changedTouches && ev.changedTouches.length) ? ev.changedTouches[0] : ev;
            if (!t || t.clientX === undefined) return;
            var p = toGame(t.clientX, t.clientY);
            if (p) @com.avatardk.game.ui.SoftInput::onDomPoint(FF)(p[0], p[1]);
        };
        canvas.addEventListener('touchstart', handler, {passive: true});
        canvas.addEventListener('touchend', handler, {passive: true});
        canvas.addEventListener('mousedown', handler);
    }-*/;

    private static native void nativeSetMap(double vx, double vy, double vw, double vh) /*-{
        $wnd.__siMap = [vx, vy, vw, vh];
    }-*/;

    private static native void nativeShow(String value, boolean password, boolean dark) /*-{
        var el = $wnd.__siEl;
        if (!el) return;
        el.value = value;
        el.type = password ? 'password' : 'text';
        if (dark) {
            el.style.background = 'rgba(16,22,38,0.98)';
            el.style.color = '#ffffff';
            el.style.border = '2px solid #ff9e40';
        } else {
            el.style.background = '#f2f7fb';
            el.style.color = '#2a2e38';
            el.style.border = '2px solid #ffab3d';
        }
        el.style.display = 'block';
    }-*/;

    private static native void nativeSetType(boolean password) /*-{
        var el = $wnd.__siEl;
        if (!el) return;
        var t = password ? 'password' : 'text';
        if (el.type !== t) el.type = t;
    }-*/;

    private static native void nativePosition(double dx, double dTop, double dw, double dh,
                                              double padGameCss) /*-{
        var el = $wnd.__siEl, canvas = $wnd.__siCanvas;
        if (!el || !canvas) return;
        var rect = canvas.getBoundingClientRect();
        var sx = rect.width / canvas.width, sy = rect.height / canvas.height;
        var w = dw * sx, h = dh * sy;
        el.style.left = (rect.left + $wnd.pageXOffset + dx * sx) + 'px';
        el.style.top = (rect.top + $wnd.pageYOffset + dTop * sy) + 'px';
        el.style.width = Math.max(w, 60) + 'px';
        el.style.height = Math.max(h, 30) + 'px';
        el.style.fontSize = Math.max(16, Math.min(30, h * 0.52)) + 'px';
        el.style.paddingLeft = Math.max(4, Math.round(padGameCss * sx)) + 'px';
        el.style.paddingRight = '8px';
    }-*/;

    private static native String nativeValue() /*-{
        var el = $wnd.__siEl;
        return el ? el.value : '';
    }-*/;

    private static native void nativeFocus() /*-{
        var el = $wnd.__siEl;
        if (el) el.focus();
    }-*/;

    private static native void nativeHide() /*-{
        var el = $wnd.__siEl;
        if (el) {
            el.style.display = 'none';
            el.blur();
        }
    }-*/;
}
