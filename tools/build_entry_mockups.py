"""Mockup luồng vào game (v2 — nền là TRANH VẼ key art, không ghép ảnh game):
1) banner, 2) panel đăng nhập/đăng ký + Google/Apple, 3) chọn máy chủ.
Chạy: python3 tools/build_entry_mockups.py <tiền tố thời gian vd 1407>"""
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
TAG = sys.argv[1] if len(sys.argv) > 1 else "v2"

def font(path, size): return ImageFont.truetype(path, size)

def scene():
    art = Image.open(ART).convert("RGBA")
    scale = max(W / art.width, H / art.height)
    art = art.resize((int(art.width * scale) + 1, int(art.height * scale) + 1))
    x = (art.width - W) // 2
    y = (art.height - H) // 2
    return art.crop((x, y, x + W, y + H))

def text_c(d, cx, y, s, f, fill, stroke=0, stroke_fill=(0, 0, 0)):
    w = d.textlength(s, font=f)
    d.text((cx - w / 2, y), s, font=f, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill)
    return w

def rrect(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

GLASS = (8, 16, 28, 218)
RIM = (140, 175, 220, 255)
ORANGE = (232, 116, 20, 255)
base = scene()

# ---------- 1: BANNER ----------
m1 = base.copy()
grad = Image.new("RGBA", (W, 300), (0, 0, 0, 0))
gd = ImageDraw.Draw(grad)
for yy in range(300):
    gd.line([(0, yy), (W, yy)], fill=(4, 10, 20, int(110 * (1 - yy / 300))))
m1.alpha_composite(grad, (0, 0))
d = ImageDraw.Draw(m1, "RGBA")
text_c(d, W / 2, 46, "AvatarDK", font(BALOO, 104), (255, 196, 64), stroke=3, stroke_fill=(90, 40, 0))
text_c(d, W / 2, 172, "Thế giới Avatar — gặp gỡ, kết bạn, vui cả ngày", font(NUN_X, 29), (255, 255, 255), stroke=2, stroke_fill=(20, 40, 60))
rrect(d, (W / 2 - 170, 624, W / 2 + 170, 676), 26, fill=ORANGE)
text_c(d, W / 2, 632, "CHẠM ĐỂ VÀO GAME", font(NUN_X, 26), (255, 255, 255))
vs = "Phiên bản 2.5.8"; vw = d.textlength(vs, font=font(NUN_B, 17))
d.text((W - 18 - vw, H - 34), vs, font=font(NUN_B, 17), fill=(255, 255, 255, 220))
m1.convert("RGB").save(os.path.join(ROOT, f"mockup-1-banner-ve-{TAG}.png"))

dim = Image.alpha_composite(base.filter(ImageFilter.GaussianBlur(3)).convert("RGBA"),
                             Image.new("RGBA", (W, H), (4, 8, 16, 110)))

# ---------- 2: ĐĂNG NHẬP / ĐĂNG KÝ ----------
m2 = dim.copy(); d = ImageDraw.Draw(m2, "RGBA")
pw, ph = 520, 596; px, py = (W - pw) // 2, (H - ph) // 2
rrect(d, (px, py, px + pw, py + ph), 22, fill=GLASS, outline=RIM, width=2)
text_c(d, W / 2, py + 22, "AvatarDK", font(BALOO, 44), (255, 196, 64))
tw = (pw - 64) // 2
rrect(d, (px + 32, py + 92, px + 32 + tw, py + 140), 12, fill=ORANGE)
text_c(d, px + 32 + tw / 2, py + 102, "Đăng nhập", font(NUN_X, 22), (255, 255, 255))
rrect(d, (px + 32 + tw + 16, py + 92, px + pw - 32, py + 140), 12, fill=(30, 45, 66, 255), outline=RIM, width=1)
text_c(d, px + 32 + tw + 16 + tw / 2, py + 102, "Đăng ký", font(NUN_X, 22), (255, 255, 255))
for i, (lab, ph_txt) in enumerate([("Tài khoản", "Nhập tài khoản"), ("Mật khẩu", "••••••••")]):
    iy = py + 168 + i * 86
    d.text((px + 36, iy), lab, font=font(NUN_B, 19), fill=(200, 220, 245))
    rrect(d, (px + 32, iy + 28, px + pw - 32, iy + 76), 12, fill=(240, 246, 252, 255))
    d.text((px + 48, iy + 40), ph_txt, font=font(NUN_B, 20), fill=(120, 135, 155))
by = py + 348
rrect(d, (px + 32, by, px + pw - 32, by + 56), 14, fill=ORANGE)
text_c(d, W / 2, by + 13, "ĐĂNG NHẬP", font(NUN_X, 24), (255, 255, 255))
d.text((px + 36, by + 70), "Quên mật khẩu?", font=font(NUN_B, 18), fill=(150, 195, 255))
s = "Chơi thử ngay"; w = d.textlength(s, font=font(NUN_B, 18))
d.text((px + pw - 36 - w, by + 70), s, font=font(NUN_B, 18), fill=(150, 195, 255))
dy = by + 116
d.line([(px + 40, dy), (px + 190, dy)], fill=(160, 180, 205), width=2)
text_c(d, W / 2, dy - 12, "hoặc tiếp tục với", font(NUN_B, 18), (190, 205, 230))
d.line([(px + pw - 190, dy), (px + pw - 40, dy)], fill=(160, 180, 205), width=2)
gy = dy + 28
rrect(d, (px + 32, gy, px + pw - 32, gy + 52), 12, fill=(255, 255, 255, 255))
d.text((px + 58, gy + 8), "G", font=font(BALOO, 30), fill=(66, 133, 244))
s = "Tiếp tục với Google"; w = d.textlength(s, font=font(NUN_X, 21))
d.text((W / 2 - w / 2 + 8, gy + 14), s, font=font(NUN_X, 21), fill=(40, 45, 55))
ay = gy + 64
rrect(d, (px + 32, ay, px + pw - 32, ay + 52), 12, fill=(10, 10, 12, 255))
ax, ayy = px + 58, ay + 13
d.ellipse((ax, ayy + 6, ax + 13, ayy + 24), fill=(255, 255, 255))
d.ellipse((ax + 9, ayy + 6, ax + 22, ayy + 24), fill=(255, 255, 255))
d.ellipse((ax + 5, ayy + 17, ax + 17, ayy + 27), fill=(255, 255, 255))
d.line((ax + 11, ayy + 7, ax + 13, ayy + 1), fill=(255, 255, 255), width=2)
d.ellipse((ax + 12, ayy - 2, ax + 21, ayy + 4), fill=(255, 255, 255))
s = "Tiếp tục với Apple"; w = d.textlength(s, font=font(NUN_X, 21))
d.text((W / 2 - w / 2 + 8, ay + 14), s, font=font(NUN_X, 21), fill=(255, 255, 255))
m2.convert("RGB").save(os.path.join(ROOT, f"mockup-2-login-ve-{TAG}.png"))

# ---------- 3: CHỌN SERVER ----------
m3 = dim.copy(); d = ImageDraw.Draw(m3, "RGBA")
pw, ph = 560, 540; px, py = (W - pw) // 2, (H - ph) // 2
rrect(d, (px, py, px + pw, py + ph), 22, fill=GLASS, outline=RIM, width=2)
text_c(d, W / 2, py + 24, "CHỌN MÁY CHỦ", font(BALOO, 36), (255, 196, 64))
rrect(d, (px + 32, py + 84, px + 300, py + 128), 22, fill=(30, 45, 66, 255), outline=RIM, width=1)
son2 = Image.open(SON).convert("RGBA"); son2 = son2.resize((30, 40), Image.NEAREST)
m3.alpha_composite(son2, (px + 44, py + 86))
d.text((px + 84, py + 95), "Xin chào, Tín", font=font(NUN_X, 20), fill=(255, 255, 255))
s = "Đổi tài khoản"; w = d.textlength(s, font=font(NUN_B, 18))
d.text((px + pw - 32 - w, py + 96), s, font=font(NUN_B, 18), fill=(150, 195, 255))
servers = [
    ("S1 · Sao Mai", "Trạng thái: Tốt", "1.240 người chơi", (64, 200, 90), True, None),
    ("S2 · Sao Băng", "Trạng thái: Đông", "3.975 người chơi", (245, 165, 40), False, None),
    ("S3 · Thiên Hà", "Máy chủ mới", "128 người chơi", (64, 200, 90), False, "MỚI"),
]
for i, (name, st, players, dot, sel, tag) in enumerate(servers):
    ry = py + 148 + i * 84
    rrect(d, (px + 32, ry, px + pw - 32, ry + 68), 14,
          fill=(52, 66, 88, 255) if sel else (24, 36, 54, 255),
          outline=(255, 170, 60, 255) if sel else RIM, width=2 if sel else 1)
    d.ellipse((px + 52, ry + 26, px + 68, ry + 42), fill=dot)
    d.text((px + 82, ry + 10), name, font=font(NUN_X, 23), fill=(255, 255, 255))
    d.text((px + 82, ry + 40), st, font=font(NUN_B, 17), fill=(170, 190, 215))
    w = d.textlength(players, font=font(NUN_B, 17))
    d.text((px + pw - 52 - w, ry + 12), players, font=font(NUN_B, 17), fill=(190, 205, 230))
    if sel:
        s = "Đang chọn"; w = d.textlength(s, font=font(NUN_X, 17))
        d.text((px + pw - 52 - w, ry + 38), s, font=font(NUN_X, 17), fill=(255, 190, 80))
    if tag:
        twd = d.textlength(tag, font=font(NUN_X, 15)) + 16
        rrect(d, (px + pw - 52 - twd, ry + 36, px + pw - 52, ry + 60), 8, fill=(30, 160, 80, 255))
        d.text((px + pw - 52 - twd + 8, ry + 40), tag, font=font(NUN_X, 15), fill=(255, 255, 255))
by = py + ph - 96
rrect(d, (px + 32, by, px + pw - 32, by + 60), 14, fill=ORANGE)
text_c(d, W / 2, by + 15, "VÀO GAME", font(NUN_X, 26), (255, 255, 255))
m3.convert("RGB").save(os.path.join(ROOT, f"mockup-3-server-ve-{TAG}.png"))
print("mockups v2 done:", TAG)
