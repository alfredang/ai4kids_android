#!/usr/bin/env python3
"""Generate Google Play store assets for AI4Kids from brand palette + device screenshots."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "..", "docs", "screenshots")
OUT = HERE
os.makedirs(os.path.join(OUT, "phone"), exist_ok=True)
os.makedirs(os.path.join(OUT, "tablet"), exist_ok=True)

# Brand palette (from ui/theme/Theme.kt + screenshots)
PURPLE = (115, 76, 235)
PURPLE_DK = (88, 52, 196)
INK = (43, 35, 80)
YELLOW = (255, 209, 41)
WHITE = (255, 255, 255)
RED = (255, 89, 95)
LAV = (239, 234, 251)   # lavender (bg top)
PEACH = (253, 239, 234)  # peach (bg bottom)

def font(size, bold=True):
    cands = [
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial.ttf",
    ]
    for c in cands:
        if os.path.exists(c):
            try:
                return ImageFont.truetype(c, size)
            except Exception:
                pass
    return ImageFont.load_default()

def vgradient(w, h, top, bot):
    base = Image.new("RGB", (w, h), top)
    d = ImageDraw.Draw(base)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bot[i]-top[i])*t) for i in range(3)))
    return base

def diag_gradient(w, h, c1, c2):
    base = Image.new("RGB", (w, h), c1)
    d = ImageDraw.Draw(base)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(c1[i] + (c2[i]-c1[i])*t) for i in range(3)))
    return base

def rounded(img, rad):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]], radius=rad, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

def draw_robot(d, cx, cy, s, head_fill=WHITE, eye=PURPLE, antenna=YELLOW, smile=RED):
    """Robot face like the launcher icon, scaled by s (head ~ 1.4s wide)."""
    # antenna
    d.rectangle([cx-0.06*s, cy-0.95*s, cx+0.06*s, cy-0.55*s], fill=antenna)
    d.ellipse([cx-0.16*s, cy-1.12*s, cx+0.16*s, cy-0.80*s], fill=antenna)
    # head
    hw, hh = 0.95*s, 0.78*s
    d.rounded_rectangle([cx-hw, cy-hh, cx+hw, cy+hh], radius=0.28*s, fill=head_fill)
    # eyes
    er = 0.14*s
    d.ellipse([cx-0.42*s-er, cy-0.12*s-er, cx-0.42*s+er, cy-0.12*s+er], fill=eye)
    d.ellipse([cx+0.42*s-er, cy-0.12*s-er, cx+0.42*s+er, cy-0.12*s+er], fill=eye)
    # smile (arc)
    d.arc([cx-0.45*s, cy-0.05*s, cx+0.45*s, cy+0.55*s], start=20, end=160, fill=smile, width=max(3, int(0.10*s)))

# ---------- 1. App icon 512x512 ----------
def make_icon():
    S = 512
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    # purple rounded tile with subtle vertical gradient
    tile = vgradient(S, S, PURPLE, PURPLE_DK).convert("RGBA")
    tile = rounded(tile, 110)
    img.alpha_composite(tile)
    d = ImageDraw.Draw(img)
    draw_robot(d, S//2, int(S*0.52), int(S*0.30))
    img.convert("RGB").save(os.path.join(OUT, "play_store_512.png"))
    # also a 1024 master
    img.resize((1024, 1024), Image.LANCZOS).convert("RGB").save(os.path.join(OUT, "icon_1024.png"))

# ---------- 2. Feature graphic 1024x500 ----------
def make_feature():
    W, H = 1024, 500
    img = diag_gradient(W, H, PURPLE, PURPLE_DK).convert("RGBA")
    d = ImageDraw.Draw(img)
    # soft circles
    for (cx, cy, r, col, a) in [(880, 90, 220, WHITE, 22), (120, 440, 180, YELLOW, 28), (980, 430, 120, (255,255,255), 16)]:
        ov = Image.new("RGBA", (W, H), (0,0,0,0))
        ImageDraw.Draw(ov).ellipse([cx-r, cy-r, cx+r, cy+r], fill=col+(a,))
        img.alpha_composite(ov)
    d = ImageDraw.Draw(img)
    # robot mascot on right
    draw_robot(d, 820, 250, 150)
    # title + tagline
    d.text((70, 150), "AI4Kids", font=font(120), fill=WHITE)
    d.text((76, 290), "Play. Learn. Create.", font=font(46), fill=(255,255,255))
    d.text((76, 360), "Fun learning games for ages 4–16", font=font(34, bold=False), fill=(230, 222, 255))
    img.convert("RGB").save(os.path.join(OUT, "feature_graphic_1024x500.png"))

# ---------- 3. Framed screenshots ----------
CAPTIONS = {
    "home.png": "Four playful activities in one app",
    "story_builder.png": "Build your own illustrated story",
    "code_puzzles.png": "Guide the robot — learn to code",
    "brain_arcade.png": "Brain Arcade — card games with friends",
}
ORDER = ["home.png", "story_builder.png", "code_puzzles.png", "brain_arcade.png"]

def frame(shot_path, canvas_w, canvas_h, caption, out_path):
    bg = vgradient(canvas_w, canvas_h, LAV, PEACH).convert("RGBA")
    d = ImageDraw.Draw(bg)
    # caption
    f = font(int(canvas_w*0.052))
    tb = d.textbbox((0,0), caption, font=f)
    tw = tb[2]-tb[0]
    cap_y = int(canvas_h*0.045)
    d.text(((canvas_w-tw)//2, cap_y), caption, font=f, fill=INK)
    # device image area below caption
    top_pad = int(canvas_h*0.16)
    bot_pad = int(canvas_h*0.05)
    avail_h = canvas_h - top_pad - bot_pad
    shot = Image.open(shot_path).convert("RGB")
    sw, sh = shot.size
    scale = min((canvas_w*0.84)/sw, avail_h/sh)
    nw, nh = int(sw*scale), int(sh*scale)
    shot = shot.resize((nw, nh), Image.LANCZOS)
    shot = rounded(shot, int(nw*0.05))
    # shadow
    sh_img = Image.new("RGBA", (canvas_w, canvas_h), (0,0,0,0))
    px = (canvas_w-nw)//2
    py = top_pad + (avail_h-nh)//2
    shadow = Image.new("RGBA", (nw+60, nh+60), (0,0,0,0))
    ImageDraw.Draw(shadow).rounded_rectangle([30,30,nw+30,nh+30], radius=int(nw*0.05), fill=(60,40,120,120))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    sh_img.alpha_composite(shadow, (px-30, py-18))
    bg.alpha_composite(sh_img)
    bg.alpha_composite(shot, (px, py))
    bg.convert("RGB").save(out_path)

def make_shots():
    for i, name in enumerate(ORDER, 1):
        sp = os.path.join(SHOTS, name)
        if not os.path.exists(sp):
            continue
        frame(sp, 1080, 1920, CAPTIONS[name], os.path.join(OUT, "phone", f"{i}_{name}"))
        # tablet: 10-inch portrait 1600x2560 (ratio 1.6, <=2:1)
        frame(sp, 1600, 2560, CAPTIONS[name], os.path.join(OUT, "tablet", f"{i}_{name}"))

make_icon()
make_feature()
make_shots()
print("DONE")
for root, _, files in os.walk(OUT):
    for fn in sorted(files):
        if fn.endswith(".png"):
            p = os.path.join(root, fn)
            with Image.open(p) as im:
                print(f"{im.size[0]}x{im.size[1]}  {os.path.relpath(p, OUT)}")
