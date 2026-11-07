"""Generates the Aerial 管家 launcher icon (gradient + TV glyph) at all densities."""
from PIL import Image, ImageDraw
import pathlib, math

RES = pathlib.Path(r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\aerial-buddy\android\app\src\main\res")
S = 1024
ss = 4


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, top=(124, 92, 255), bottom=(30, 60, 140)):
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        d.line([(0, y), (size, y)], fill=lerp(top, bottom, y / (size - 1)))
    return img


def draw_glyph(img, scale=1.0, offset=(0, 0)):
    d = ImageDraw.Draw(img)
    cx, cy = S / 2 + offset[0], S / 2 + offset[1]
    w, h = 560 * scale, 380 * scale
    # antenna
    d.line([(cx - 90 * scale, cy - h / 2 - 150 * scale), (cx - 20 * scale, cy - h / 2 - 40 * scale)],
           fill=(255, 255, 255, 235), width=int(26 * scale))
    d.line([(cx + 90 * scale, cy - h / 2 - 150 * scale), (cx + 20 * scale, cy - h / 2 - 40 * scale)],
           fill=(255, 255, 255, 235), width=int(26 * scale))
    # screen body
    d.rounded_rectangle([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2],
                        radius=int(64 * scale), fill=(255, 255, 255, 240))
    # inner screen
    d.rounded_rectangle([cx - w / 2 + 34 * scale, cy - h / 2 + 34 * scale,
                         cx + w / 2 - 34 * scale, cy + h / 2 - 34 * scale],
                        radius=int(40 * scale), fill=(96, 78, 200, 255))
    # play triangle
    t = 92 * scale
    d.polygon([(cx - t * 0.55, cy - t), (cx - t * 0.55, cy + t), (cx + t * 0.95, cy)],
              fill=(255, 255, 255, 255))
    return img


def rounded_mask(size, radius_ratio=0.225):
    mask = Image.new("L", (size * ss, size * ss), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size * ss - 1, size * ss - 1], radius=int(size * ss * radius_ratio), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


base = gradient(S)
icon = draw_glyph(base.copy()).convert("RGBA")
icon.putalpha(rounded_mask(S))

for name, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    out = RES / f"mipmap-{name}"
    out.mkdir(parents=True, exist_ok=True)
    icon.resize((size, size), Image.LANCZOS).save(out / "ic_launcher.png", optimize=True)
    # round variant
    m = Image.new("L", (S * ss, S * ss), 0)
    ImageDraw.Draw(m).ellipse([0, 0, S * ss - 1, S * ss - 1], fill=255)
    rnd = icon.copy()
    rnd.putalpha(m.resize((S, S), Image.LANCZOS))
    rnd.resize((size, size), Image.LANCZOS).save(out / "ic_launcher_round.png", optimize=True)
    # adaptive foreground: glyph inset on transparent
    fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fg.alpha_composite(draw_glyph(Image.new("RGBA", (S, S), (0, 0, 0, 0)), 0.72))
    fg.resize((size * 2, size * 2), Image.LANCZOS).save(out / "ic_launcher_foreground.png")
    gradient(S).resize((size * 2, size * 2), Image.LANCZOS).save(out / "ic_launcher_background.png")

icon.resize((512, 512), Image.LANCZOS).save(
    r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\aerial-buddy\icon-preview.png")
print("icons written")
