#!/usr/bin/env python3
"""Draws the Google Play feature graphic: the app icon, name and tagline next to a phone and a
round watch showing a QR code, on the sunrise gradient of the store screenshots (frame.py).

The code is the demo athlete ID from DemoData, so no real barcode appears in the store. Google Play
wants 1024x500 without transparency and may cover the edges, so everything keeps clear of them.

Usage: python3 tools/screenshots/feature_graphic.py
Writes fastlane/metadata/android/en-US/images/featureGraphic.png, which the metadata lane uploads.
Requires Pillow and segno (pip install pillow segno).
"""
from __future__ import annotations

from PIL import Image, ImageDraw, ImageFilter, ImageFont
import segno

from frame import FONT, OUTLINE, ROOT, SHADOW, TEXT, panorama

SIZE = (1024, 500)
OUTPUT = ROOT / "fastlane/metadata/android/en-US/images/featureGraphic.png"
ICON = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
TAGLINE = "Your parkrun barcode on your phone and watch"
NAME, ATHLETE_ID = "Me", "A0123456"  # DemoData's first barcode


def font(size: int, weight: int) -> ImageFont.FreeTypeFont:
    face = ImageFont.truetype(str(FONT), size)
    face.set_variation_by_axes([32, weight])  # display optical size
    return face


def shadow(canvas: Image.Image, shape, blur: float, offset: int = 0, strength: int = 140) -> None:
    """Casts a soft shadow of [shape] (drawn by a function on an ImageDraw) onto [canvas]."""
    mask = Image.new("L", canvas.size, 0)
    shape(ImageDraw.Draw(mask), offset, strength)
    canvas.paste(SHADOW, (0, 0), mask.filter(ImageFilter.GaussianBlur(blur)))


def qr(size: int) -> Image.Image:
    """The demo code as black modules on white, [size] pixels square including the quiet zone."""
    code = segno.make_qr(ATHLETE_ID, error="m")
    modules = [[bool(v) for v in row] for row in code.matrix]
    quiet = 4
    count = len(modules) + 2 * quiet
    module = size // count
    image = Image.new("RGB", (size, size), "white")
    draw = ImageDraw.Draw(image)
    left = (size - module * count) // 2 + quiet * module
    for y, row in enumerate(modules):
        for x, dark in enumerate(row):
            if dark:
                draw.rectangle(
                    (left + x * module, left + y * module, left + (x + 1) * module - 1, left + (y + 1) * module - 1),
                    fill="black",
                )
    return image


def barcode_screen(width: int, height: int, labels: bool) -> Image.Image:
    """A white barcode screen as the apps show it: the name above the code, the ID below."""
    screen = Image.new("RGB", (width, height), "white")
    code = qr(round(min(width, height) * 0.86))
    # A little above the middle, since the bottom of the phone runs off the graphic
    top = round(height * 0.42 - code.height / 2)
    screen.paste(code, ((width - code.width) // 2, top))
    if labels:
        draw = ImageDraw.Draw(screen)
        label = font(round(width * 0.075), 500)
        draw.text((width / 2, top), NAME, font=label, fill="black", anchor="md")
        draw.text((width / 2, top + code.height), ATHLETE_ID, font=label, fill="black", anchor="mt")
    return screen


def phone(canvas: Image.Image, x: int, y: int, width: int, height: int) -> None:
    """A phone showing the code, its bottom running off [canvas] like in the store screenshots."""
    radius = round(width * 0.14)
    bezel = round(width * 0.045)
    shadow(
        canvas,
        lambda d, o, s: d.rounded_rectangle((x, y + o, x + width, y + height + o), radius, fill=s),
        blur=canvas.width * 0.02,
        offset=round(canvas.height * 0.03),
    )
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((x - 2, y - 2, x + width + 2, y + height + 2), radius + 2, fill=OUTLINE)
    draw.rounded_rectangle((x, y, x + width, y + height), radius, fill=(20, 20, 24))
    inner = (width - 2 * bezel, height - 2 * bezel)
    screen = barcode_screen(*inner, labels=True)
    mask = Image.new("L", inner, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, inner[0] - 1, inner[1] - 1), radius - bezel, fill=255)
    canvas.paste(screen, (x + bezel, y + bezel), mask)


def watch(canvas: Image.Image, cx: int, cy: int, diameter: int) -> None:
    """A round watch showing the code inside the square that fits its display."""
    r = diameter // 2
    shadow(
        canvas,
        lambda d, o, s: d.ellipse((cx - r, cy - r + o, cx + r, cy + r + o), fill=s),
        blur=canvas.width * 0.015,
        offset=round(canvas.height * 0.025),
        strength=170,
    )
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2), fill=OUTLINE)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(20, 20, 24))
    display = round(diameter * 0.88)
    face = Image.new("RGB", (display, display), "white")
    code = qr(round(display / 2 ** 0.5))
    face.paste(code, ((display - code.width) // 2, (display - code.height) // 2))
    mask = Image.new("L", face.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, display - 1, display - 1), fill=255)
    canvas.paste(face, (cx - display // 2, cy - display // 2), mask)


def icon(canvas: Image.Image, x: int, y: int, size: int) -> None:
    radius = round(size * 0.22)
    shadow(
        canvas,
        lambda d, o, s: d.rounded_rectangle((x, y + o, x + size, y + size + o), radius, fill=s),
        blur=size * 0.08,
        offset=round(size * 0.04),
    )
    image = Image.open(ICON).convert("RGB").resize((size, size), Image.LANCZOS)
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius, fill=255)
    ImageDraw.Draw(canvas).rounded_rectangle((x - 2, y - 2, x + size + 2, y + size + 2), radius + 2, fill=OUTLINE)
    canvas.paste(image, (x, y), mask)


def text(canvas: Image.Image, position: tuple[int, int], value: str, face: ImageFont.FreeTypeFont) -> None:
    """White text with the soft shadow of the screenshot headlines."""
    shadow(canvas, lambda d, o, s: d.text(position, value, font=face, fill=120), blur=canvas.width * 0.006)
    ImageDraw.Draw(canvas).text(position, value, font=face, fill=TEXT)


def main() -> None:
    width, height = SIZE
    # Two screenshot widths of the mesh give the wide canvas a few color spots
    canvas = panorama((width // 2, height), 2).resize(SIZE, Image.BICUBIC)

    left = 64
    icon(canvas, left, 118, 112)
    text(canvas, (left, 250), "Bibless", font(88, 700))
    tagline = font(30, 600)
    line1, line2 = "Your parkrun barcode", "on your phone and watch"
    text(canvas, (left, 360), line1, tagline)
    text(canvas, (left, 398), line2, tagline)

    # The watch overlaps the phone below its code
    phone(canvas, 600, 56, 236, 500)
    watch(canvas, 872, 372, 176)

    canvas.convert("RGB").save(OUTPUT, optimize=True)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({canvas.width}x{canvas.height})")


if __name__ == "__main__":
    main()
