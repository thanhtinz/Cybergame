"""Mockup luồng vào game v3 — tên mới "Thị Trấn Mặt Trời (Sunny Town)":
1) banner: góc phải trên có 3 nút dọc Thông báo / Xoá cache / Hỗ trợ;
2) đăng nhập/đăng ký làm đẹp lại;
3) chọn server KIỂU MỚI: khung nhỏ chứa tên server + nút Đổi, dưới là nút
   VÀO THỊ TRẤN; 3b) panel chọn server khi bấm Đổi: cột trái = CỤM, phải = server trong cụm.
Chạy: python3 tools/build_entry_mockups_v3.py <tag>"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os, sys

ROOT = os.path.expanduser("~/workspace/avatardk-java")
A = os.path.join(ROOT, "assets")
ART = os.path.join(ROOT, "mockups/media-generation-avatardk-banner-art-0-53d6cca8-6e5b-4708-87ee-952e60940289.webp")
SON = os.path.expanduser("~/workspace/goals/game-s-ng-v-t-phong-c-ch-thu-n-vi-t/hidden_files/pack5/chars/son.png")
W, H = 1280, 720
BALOO = os.path.join(A, "fonts/Baloo2-ExtraBold.ttf")
NUN_B = os.path.join(A, "fonts/Nunito-Bold.ttf")
NUN_X = os.path.join(A, "fonts/Nunito-ExtraBold.ttf")
TAG = sys.argv[1] if len(sys.argv) > 1 else "v3"
FA_SOLID = os.path.join(A, "fonts/fa-solid-900.ttf")
FA_BRANDS = os.path.join(A, "fonts/fa-brands-400.ttf")

def font(p, s): return ImageFont.truetype(p, s)
def fa(d, cx, cy, code, size, color, brands=False):
    """Vẽ 1 glyph Font Awesome thật, căn giữa tại (cx, cy)."""
    f = font(FA_BRANDS if brands else FA_SOLID, size)
    bb = d.textbbox((0, 0), code, font=f)
    d.text((cx - (bb[0] + bb[2]) / 2, cy - (bb[1] + bb[3]) / 2), code, font=f, fill=color)
def scene():
    art = Image.open(ART).convert("RGBA")
    sc = max(W / art.width, H / art.height)
    art = art.resize((int(art.width * sc) + 1, int(art.height * sc) + 1))
    x, y = (art.width - W) // 2, (art.height - H) // 2
    return art.crop((x, y, x + W, y + H))
def text_c(d, cx, y, s, f, fill, stroke=0, stroke_fill=(0, 0, 0)):
    w = d.textlength(s, font=f)
    d.text((cx - w / 2, y), s, font=f, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill)
def rrect(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)
def sun(d, cx, cy, r, color=(255, 190, 50, 255)):
    fa(d, cx, cy, "\uf185", int(r * 2.1), color)  # Font Awesome: sun
def gradient_btn(img, box, c1, c2, r):
    x0, y0, x1, y1 = box
    grad = Image.new("RGBA", (x1 - x0, y1 - y0))
    gd = ImageDraw.Draw(grad)
    for yy in range(y1 - y0):
        t = yy / max(1, y1 - y0)
        gd.line([(0, yy), (x1 - x0, yy)], fill=tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,))
    mask = Image.new("L", grad.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, grad.width, grad.height), radius=r, fill=255)
    img.paste(grad, (x0, y0), mask)

GLASS = (8, 16, 28, 222)
RIM = (140, 175, 220, 255)
ORANGE = (232, 116, 20, 255)
GOLD = (255, 196, 64)
base = scene()

# ================= 1) BANNER =================
m1 = base.copy()
grad = Image.new("RGBA", (W, 320), (0, 0, 0, 0))
gd = ImageDraw.Draw(grad)
for yy in range(320):
    gd.line([(0, yy), (W, yy)], fill=(4, 10, 20, int(120 * (1 - yy / 320))))
m1.alpha_composite(grad, (0, 0))
d = ImageDraw.Draw(m1, "RGBA")
text_c(d, W / 2, 40, "Thị Trấn Mặt Trời", font(BALOO, 88), GOLD, stroke=3, stroke_fill=(90, 40, 0))
text_c(d, W / 2, 148, "S U N N Y   T O W N", font(NUN_X, 21), (255, 255, 255), stroke=1, stroke_fill=(20, 40, 60))
text_c(d, W / 2, 182, "Gặp gỡ · Kết bạn · Vui cả ngày", font(NUN_X, 27), (255, 255, 255), stroke=2, stroke_fill=(20, 40, 60))
# 3 nút tiện ích dọc góc phải trên: Thông báo / Xoá cache / Hỗ trợ (icon + nhãn dưới)
def util_btn(d, cx, top, kind, label):
    r = 27
    d.ellipse((cx - r, top, cx + r, top + 2 * r), fill=(8, 16, 28, 180), outline=(210, 228, 248, 200), width=2)
    cy = top + r
    glyph = {"bell": "\uf0f3", "trash": "\uf2ed", "headset": "\uf590"}[kind]  # Font Awesome
    fa(d, cx, cy, glyph, 26, (255, 255, 255))
    text_c(d, cx, top + 2 * r + 5, label, font(NUN_B, 15), (255, 255, 255), stroke=1, stroke_fill=(20, 40, 60))
util_btn(d, W - 62, 26, "bell", "Thông báo")
util_btn(d, W - 62, 128, "trash", "Xoá cache")
util_btn(d, W - 62, 230, "headset", "Hỗ trợ")
rrect(d, (W / 2 - 176, 624, W / 2 + 176, 676), 26, fill=ORANGE)
text_c(d, W / 2, 632, "CHẠM ĐỂ BẮT ĐẦU", font(NUN_X, 26), (255, 255, 255))
vs = "Phiên bản 2.5.8"; vw = d.textlength(vs, font=font(NUN_B, 17))
d.text((18, H - 34), vs, font=font(NUN_B, 17), fill=(255, 255, 255, 220))
m1.convert("RGB").save(os.path.join(ROOT, f"mockup-1-banner-{TAG}.png"))

dim = Image.alpha_composite(base.filter(ImageFilter.GaussianBlur(3)).convert("RGBA"),
                             Image.new("RGBA", (W, H), (4, 8, 16, 115)))

# ================= 2) ĐĂNG NHẬP (đẹp lại) =================
m2 = dim.copy(); d = ImageDraw.Draw(m2, "RGBA")
pw, ph = 540, 628; px, py = (W - pw) // 2, (H - ph) // 2
# panel gradient dọc
panel = Image.new("RGBA", (pw, ph))
pd = ImageDraw.Draw(panel)
for yy in range(ph):
    t = yy / ph
    pd.line([(0, yy), (pw, yy)], fill=(int(18 - 10 * t), int(34 - 18 * t), int(58 - 28 * t), 235))
mask = Image.new("L", (pw, ph), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, pw, ph), radius=24, fill=255)
m2.paste(panel, (px, py), mask)
d = ImageDraw.Draw(m2, "RGBA")
rrect(d, (px, py, px + pw, py + ph), 24, outline=RIM, width=2)
sun(d, px + 52, py + 52, 13)
d.text((px + 76, py + 30), "Thị Trấn Mặt Trời", font=font(BALOO, 33), fill=GOLD)
d.text((px + 77, py + 68), "Chào mừng bạn đến với thị trấn", font=font(NUN_B, 17), fill=(170, 195, 225))
# segmented tabs
tx, ty = px + 32, py + 112
rrect(d, (tx, ty, px + pw - 32, ty + 50), 14, fill=(16, 28, 46, 255))
rrect(d, (tx + 4, ty + 4, tx + 4 + (pw - 72) // 2, ty + 46), 11, fill=ORANGE)
text_c(d, tx + 4 + (pw - 72) // 4, ty + 13, "Đăng nhập", font(NUN_X, 21), (255, 255, 255))
text_c(d, tx + 8 + (pw - 72) // 2 + (pw - 72) // 4, ty + 13, "Đăng ký", font(NUN_X, 21), (200, 215, 235))
# inputs có icon
def field(iy, label, placeholder, is_pass=False):
    d.text((px + 36, iy), label, font=font(NUN_B, 18), fill=(195, 215, 240))
    rrect(d, (px + 32, iy + 27, px + pw - 32, iy + 75), 13, fill=(243, 247, 251, 255))
    icx, icy = px + 58, iy + 51
    fa(d, icx, icy, "\uf023" if is_pass else "\uf007", 19, (110, 125, 145))  # FA lock / user
    if is_pass:
        fa(d, px + pw - 57, icy, "\uf06e", 18, (110, 125, 145))  # FA eye
    d.text((px + 78, iy + 39), placeholder, font=font(NUN_B, 20), fill=(120, 135, 155))
field(py + 182, "Tài khoản", "Nhập tài khoản")
field(py + 274, "Mật khẩu", "••••••••", is_pass=True)
ry = py + 366
rrect(d, (px + 36, ry, px + 54, ry + 18), 5, fill=ORANGE)
fa(d, px + 45, ry + 9, "\uf00c", 12, (255, 255, 255))  # FA check
d.text((px + 62, ry - 1), "Nhớ đăng nhập", font=font(NUN_B, 18), fill=(200, 215, 235))
s = "Quên mật khẩu?"; w = d.textlength(s, font=font(NUN_B, 18))
d.text((px + pw - 36 - w, ry - 1), s, font=font(NUN_B, 18), fill=(140, 190, 255))
by = py + 402
gradient_btn(m2, (px + 32, by, px + pw - 32, by + 58), (246, 133, 32), (213, 88, 8), 14)
d = ImageDraw.Draw(m2, "RGBA")
text_c(d, W / 2, by + 15, "ĐĂNG NHẬP", font(NUN_X, 25), (255, 255, 255))
dyy = by + 88
d.line([(px + 40, dyy), (px + 178, dyy)], fill=(150, 170, 200), width=2)
text_c(d, W / 2, dyy - 11, "hoặc tiếp tục với", font(NUN_B, 17), (185, 200, 225))
d.line([(px + pw - 178, dyy), (px + pw - 40, dyy)], fill=(150, 170, 200), width=2)
gy = dyy + 24
half = (pw - 72) // 2
rrect(d, (px + 32, gy, px + 32 + half, gy + 52), 12, fill=(255, 255, 255, 255))
# logo Google = glyph Font Awesome brands
fa(d, px + 62, gy + 26, "\uf1a0", 26, (60, 64, 68), brands=True)
s = "Google"; d.text((px + 96, gy + 14), s, font=font(NUN_X, 21), fill=(40, 45, 55))
ax0 = px + 32 + half + 8
rrect(d, (ax0, gy, ax0 + half, gy + 52), 12, fill=(10, 10, 12, 255))
fa(d, ax0 + 30, gy + 26, "\uf179", 28, (255, 255, 255), brands=True)  # FA apple
d.text((ax0 + 52, gy + 14), "Apple", font=font(NUN_X, 21), fill=(255, 255, 255))
text_c(d, W / 2, py + ph - 36, "Tiếp tục nghĩa là bạn đồng ý với Điều khoản & Chính sách bảo mật", font(NUN_B, 14), (140, 160, 185))
m2.convert("RGB").save(os.path.join(ROOT, f"mockup-2-login-{TAG}.png"))

# ================= 3) CHỌN SERVER — khung nhỏ + nút Đổi + VÀO THỊ TRẤN =================
m3 = base.copy()  # KHÔNG làm mờ banner ở màn chọn server
grad3 = Image.new("RGBA", (W, 300), (0, 0, 0, 0))
gd3 = ImageDraw.Draw(grad3)
for yy in range(300):
    gd3.line([(0, yy), (W, yy)], fill=(4, 10, 20, int(110 * (1 - yy / 300))))
m3.alpha_composite(grad3, (0, 0))
d = ImageDraw.Draw(m3, "RGBA")
text_c(d, W / 2, 40, "Thị Trấn Mặt Trời", font(BALOO, 88), GOLD, stroke=3, stroke_fill=(90, 40, 0))
text_c(d, W / 2, 148, "S U N N Y   T O W N", font(NUN_X, 21), (255, 255, 255), stroke=1, stroke_fill=(20, 40, 60))
text_c(d, W / 2, 182, "Gặp gỡ · Kết bạn · Vui cả ngày", font(NUN_X, 27), (255, 255, 255), stroke=2, stroke_fill=(20, 40, 60))
util_btn(d, W - 62, 26, "bell", "Thông báo")
util_btn(d, W - 62, 128, "trash", "Xoá cache")
util_btn(d, W - 62, 230, "headset", "Hỗ trợ")
text_c(d, W / 2, 424, "Xin chào, Tín", font(NUN_X, 27), (255, 255, 255), stroke=2, stroke_fill=(20, 40, 60))
fw = 500; fx = (W - fw) // 2; fy = 480
rrect(d, (fx, fy, fx + fw, fy + 84), 18, fill=GLASS, outline=RIM, width=2)
sun(d, fx + 40, fy + 42, 12)
d.text((fx + 64, fy + 16), "S1 · Sao Mai", font=font(NUN_X, 27), fill=(255, 255, 255))
d.ellipse((fx + 66, fy + 54, fx + 76, fy + 64), fill=(64, 200, 90))
d.text((fx + 82, fy + 50), "Trạng thái: Tốt · 1.240 người chơi", font=font(NUN_B, 16), fill=(175, 198, 225))
rrect(d, (fx + fw - 118, fy + 18, fx + fw - 18, fy + 66), 12, fill=ORANGE)
text_c(d, fx + fw - 68, fy + 30, "Đổi", font(NUN_X, 22), (255, 255, 255))
by = fy + 108
gradient_btn(m3, (fx, by, fx + fw, by + 66), (246, 133, 32), (213, 88, 8), 16)
d = ImageDraw.Draw(m3, "RGBA")
text_c(d, W / 2, by + 17, "VÀO THỊ TRẤN", font(BALOO, 30), (255, 255, 255))
s = "Đổi tài khoản"
text_c(d, W / 2, by + 86, s, font(NUN_X, 21), (255, 255, 255), stroke=2, stroke_fill=(15, 30, 50))
m3.convert("RGB").save(os.path.join(ROOT, f"mockup-3-server-{TAG}.png"))

# ================= 3b) PANEL CHỌN SERVER (bấm Đổi) =================
m4 = Image.alpha_composite(m3.convert("RGBA"), Image.new("RGBA", (W, H), (4, 8, 16, 110)))
d = ImageDraw.Draw(m4, "RGBA")
pw, ph = 820, 545; px, py = (W - pw) // 2, (H - ph) // 2
rrect(d, (px, py, px + pw, py + ph), 22, fill=GLASS, outline=RIM, width=2)
d.text((px + 28, py + 22), "CHỌN MÁY CHỦ", font=font(BALOO, 32), fill=GOLD)
fa(d, px + pw - 40, py + 38, "\uf00d", 24, (255, 255, 255))  # FA xmark
# cột trái: các CỤM
lx, lw = px + 24, 224
d.text((lx + 4, py + 78), "CỤM MÁY CHỦ", font=font(NUN_X, 15), fill=(150, 175, 205))
clusters = [("Cụm Mặt Trời", "3 máy chủ", True), ("Cụm Sao Băng", "2 máy chủ", False), ("Cụm Thiên Hà", "1 máy chủ", False)]
for i, (nm, sub, sel) in enumerate(clusters):
    cy = py + 108 + i * 76
    rrect(d, (lx, cy, lx + lw, cy + 64), 13,
          fill=(52, 66, 88, 255) if sel else (22, 34, 52, 255),
          outline=(255, 170, 60, 255) if sel else (90, 115, 150, 255), width=2 if sel else 1)
    d.text((lx + 16, cy + 9), nm, font=font(NUN_X, 20), fill=(255, 255, 255))
    d.text((lx + 16, cy + 36), sub, font=font(NUN_B, 15), fill=(165, 188, 218))
# cột phải: server trong cụm đang chọn
rx = px + 272; rw = pw - 272 - 24
d.text((rx + 4, py + 78), "MÁY CHỦ TRONG CỤM MẶT TRỜI", font=font(NUN_X, 15), fill=(150, 175, 205))
servers = [
    ("S1 · Sao Mai", "Trạng thái: Tốt", "1.240 người chơi", (64, 200, 90), True, None),
    ("S2 · Sao Hôm", "Trạng thái: Tốt", "860 người chơi", (64, 200, 90), False, None),
    ("S3 · Sao Chổi", "Trạng thái: Đông", "2.310 người chơi", (245, 165, 40), False, None),
]
for i, (name, st, players, dot, sel, tag) in enumerate(servers):
    ry = py + 108 + i * 84
    rrect(d, (rx, ry, rx + rw, ry + 70), 14,
          fill=(52, 66, 88, 255) if sel else (24, 36, 54, 255),
          outline=(255, 170, 60, 255) if sel else RIM, width=2 if sel else 1)
    d.ellipse((rx + 18, ry + 27, rx + 34, ry + 43), fill=dot)
    d.text((rx + 46, ry + 10), name, font=font(NUN_X, 23), fill=(255, 255, 255))
    d.text((rx + 46, ry + 40), st, font=font(NUN_B, 16), fill=(170, 190, 215))
    w = d.textlength(players, font=font(NUN_B, 16))
    d.text((rx + rw - 18 - w, ry + 12), players, font=font(NUN_B, 16), fill=(190, 205, 230))
    if sel:
        s = "Đang chọn"; w = d.textlength(s, font=font(NUN_X, 16))
        d.text((rx + rw - 18 - w, ry + 40), s, font=font(NUN_X, 16), fill=(255, 190, 80))
# hàng nút cuối panel
by = py + ph - 74
rrect(d, (px + pw - 300, by, px + pw - 176, by + 50), 12, fill=(30, 45, 66, 255), outline=RIM, width=1)
text_c(d, px + pw - 238, by + 13, "HUỶ", font(NUN_X, 20), (220, 232, 245))
gradient_btn(m4, (px + pw - 164, by, px + pw - 24, by + 50), (246, 133, 32), (213, 88, 8), 12)
d = ImageDraw.Draw(m4, "RGBA")
text_c(d, px + pw - 94, by + 13, "XÁC NHẬN", font(NUN_X, 20), (255, 255, 255))
m4.convert("RGB").save(os.path.join(ROOT, f"mockup-3b-server-panel-{TAG}.png"))
print("mockups v3 done:", TAG)
