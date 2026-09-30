#!/usr/bin/env python3
"""Builds the phone store screenshots from the captured screens: a headline over one screen, two
screens cascading, or two screens split diagonally, on a sunrise gradient.

fastlane/screenshots/captions.json lists the store screenshots in order. Each names the captured
screens it shows ("shots"); two of them cascade ("layout": "pair") or are split diagonally
("layout": "split"):

    "02-themes": {"caption": "...", "shots": ["barcode-code128", "barcode-dark"], "layout": "split"}

The captured screens are found by the names the UI tests gave them, in the file names fastlane writes:
- iOS (snapshot):      "iPhone 17 Pro Max-barcode.png"; written as "iPhone 17 Pro Max-01-barcode.png"
                       at the same 1320x2868, so deliver still files it under the 6.9" slot
- Android (screengrab): "barcode_1790716283646.png"; written as "01-barcode.png" at 1080x1920, since
                       Google Play takes no screenshot longer than twice its width
The newest readable capture of each screen is used, and all captures are deleted afterwards, so
only the store screenshots get uploaded; the ones written here carry a PNG text marker and are never
taken for captured screens.

The background is one wide mesh gradient (soft color spots on an orange to magenta ramp) laid out
behind all the screenshots side by side; each gets its own slice, so the set flows into one another
in the store. Screens run off the bottom edge; the bottom of every screen is empty anyway.

Watch screenshots get no headline: Google Play wants Wear OS screenshots to show the app alone, and
the App Store's watch slot is the size of the display. With --wear, Wear OS screenshots below Google
Play's 384x384 minimum (a square watch's 360x360) are only scaled up to the round watch's 454x454.

Usage: python3 tools/screenshots/frame.py (--ios | --android | --wear) <screenshot dir>
Requires Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, PngImagePlugin

ROOT = Path(__file__).resolve().parents[2]
CAPTIONS = ROOT / "fastlane/screenshots/captions.json"
FONT = Path(__file__).resolve().parent / "Inter.ttf"

SIZES = {"ios": (1320, 2868), "android": (1080, 1920)}
IOS_DEVICE = "iPhone"
WEAR_MINIMUM = 384
WEAR_SIZE = 454
MARKER = "bibless-framed"

TOP = (255, 140, 80)  # orange
BOTTOM = (190, 30, 110)  # magenta
# Color spots of the mesh: x in screenshot widths from the left of the set, y in screenshot
# heights, radius in screenshot widths
SPOTS = [
    (0.15, 0.08, 0.8, (255, 200, 90)),  # gold
    (0.95, 0.55, 0.8, (123, 47, 190)),  # violet
    (1.25, 0.95, 0.7, (236, 64, 122)),  # pink
    (1.7, 0.05, 0.75, (255, 94, 98)),  # coral
    (2.3, 0.65, 0.85, (255, 200, 90)),  # gold
    (2.95, 0.15, 0.75, (123, 47, 190)),  # violet
    (3.55, 0.6, 0.85, (255, 179, 138)),  # peach
    (3.95, 0.02, 0.65, (236, 64, 122)),  # pink
    (4.5, 0.45, 0.8, (123, 47, 190)),  # violet
    (4.85, 0.05, 0.6, (255, 200, 90)),  # gold
]
TEXT = (255, 255, 255)
SHADOW = (120, 20, 70)
OUTLINE = (255, 214, 196)
# The mesh is drawn this many times smaller and scaled up, which blurs it smooth for free
MESH_SCALE = 8


def wrap(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines: list[str] = []
    for word in text.split():
        if lines and draw.textlength(f"{lines[-1]} {word}", font=font) <= width:
            lines[-1] += f" {word}"
        else:
            lines.append(word)
    return lines


def panorama(size: tuple[int, int], count: int) -> Image.Image:
    """The background of [count] screenshots of [size] side by side."""
    width, height = size
    small = (math.ceil(width * count / MESH_SCALE), math.ceil(height / MESH_SCALE))
    ramp = Image.linear_gradient("L").resize(small)
    mesh = Image.composite(Image.new("RGB", small, BOTTOM), Image.new("RGB", small, TOP), ramp)
    unit = width / MESH_SCALE
    for x, y, radius, color in SPOTS:
        spot = Image.new("L", small, 0)
        cx, cy, r = x * unit, y * height / MESH_SCALE, radius * unit
        ImageDraw.Draw(spot).ellipse((cx - r, cy - r, cx + r, cy + r), fill=200)
        mesh.paste(color, (0, 0), spot.filter(ImageFilter.GaussianBlur(r * 0.6)))
    return mesh.resize((width * count, height), Image.BICUBIC)


def headline(canvas: Image.Image, caption: str) -> int:
    """Draws [caption] at the top of [canvas]; returns where the screens start below it."""
    width, height = canvas.size
    font = ImageFont.truetype(str(FONT), round(width * 0.07))
    font.set_variation_by_axes([32, 700])  # display optical size, bold
    line_height = round(font.size * 1.2)
    top = round(height * 0.055)
    lines = wrap(ImageDraw.Draw(canvas), caption, font, round(width * 0.84))
    # A soft shadow keeps the headline readable over the bright spots
    shadow = Image.new("L", canvas.size, 0)
    for i, line in enumerate(lines):
        ImageDraw.Draw(shadow).text((width / 2, top + i * line_height), line, font=font, fill=120, anchor="mt")
    canvas.paste(SHADOW, (0, 0), shadow.filter(ImageFilter.GaussianBlur(width * 0.012)))
    for i, line in enumerate(lines):
        ImageDraw.Draw(canvas).text((width / 2, top + i * line_height), line, font=font, fill=TEXT, anchor="mt")
    return top + len(lines) * line_height + round(height * 0.04)


def place(canvas: Image.Image, screen: Image.Image, x: int, y: int, width: int) -> None:
    """Puts [screen], scaled to [width], at [x], [y] with rounded corners, a hairline and a soft shadow."""
    screen = screen.convert("RGB").resize((width, round(screen.height * width / screen.width)), Image.LANCZOS)
    radius = round(width * 0.07)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.width - 1, screen.height - 1), radius, fill=255)

    shadow = Image.new("L", canvas.size, 0)
    offset = round(canvas.width * 0.015)
    ImageDraw.Draw(shadow).rounded_rectangle((x, y + offset, x + screen.width, y + screen.height + offset), radius, fill=150)
    canvas.paste(SHADOW, (0, 0), shadow.filter(ImageFilter.GaussianBlur(canvas.width * 0.03)))
    outline = max(2, round(canvas.width * 0.0025))
    ImageDraw.Draw(canvas).rounded_rectangle(
        (x - outline, y - outline, x + screen.width - 1 + outline, y + screen.height - 1 + outline),
        radius + outline,
        fill=OUTLINE,
    )
    canvas.paste(screen, (x, y), mask)


def content_bottom(screen: Image.Image) -> int:
    """Where the content in the top three quarters of [screen] ends, just below the code on a code
    screen; the rest of it is empty background."""
    screen = screen.convert("RGB")
    width, height = screen.size
    background = Image.new("RGB", screen.size, screen.getpixel((width // 2, round(height * 0.85))))
    content = ImageChops.difference(screen, background).convert("L").point(lambda v: 255 if v > 24 else 0)
    box = content.crop((0, 0, width, round(height * 0.75))).getbbox()
    return box[3] if box else height


def split(first: Image.Image, second: Image.Image) -> Image.Image:
    """[first] above and left of a diagonal, [second] below and right of it."""
    second = second.convert("RGB").resize(first.size)
    width, height = first.size
    # From 65% along the top edge down to 35% along the bottom edge
    line = [(width * 0.65, 0), (width * 0.35, height)]
    mask = Image.new("L", first.size, 0)
    ImageDraw.Draw(mask).polygon([line[0], (width, 0), (width, height), line[1]], fill=255)
    screen = first.convert("RGB")
    screen.paste(second, (0, 0), mask)
    return screen


def compose(screens: list[Image.Image], layout: str | None, caption: str, backdrop: Image.Image) -> Image.Image:
    """Lays [screens] and their [caption] over [backdrop], this screenshot's slice of the panorama."""
    canvas = backdrop.copy()
    width, height = canvas.size
    y = headline(canvas, caption)
    if layout == "pair":
        # Two large screens cascading: the second overlaps the first below its code, so both codes
        # show whole
        screen_width = round(width * 0.62)
        margin = round(width * 0.04)
        first, second = screens
        place(canvas, first, margin, y, screen_width)
        below_code = round(content_bottom(first) * screen_width / first.width)
        place(canvas, second, width - margin - screen_width, y + below_code + round(height * 0.02), screen_width)
    else:
        screen = split(*screens) if layout == "split" else screens[0]
        screen_width = round(width * 0.84)
        place(canvas, screen, (width - screen_width) // 2, y, screen_width)
    return canvas


def captures(mode: str, directory: Path, shot: str) -> list[Path]:
    """The files the UI tests captured as [shot], oldest first; screengrab adds a timestamp, and
    can bring along empty files for screens left on the device by an earlier run."""
    found = []
    for path in sorted(directory.glob("*.png")):
        if mode == "ios":
            matches = path.stem.startswith(IOS_DEVICE) and path.stem.endswith(f"-{shot}")
        else:
            matches = path.stem == shot or path.stem.startswith(f"{shot}_")
        if matches and not framed(path):
            found.append(path)
    return found


def framed(path: Path) -> bool:
    try:
        with Image.open(path) as image:
            return MARKER in image.info
    except OSError:  # empty or broken, so not one of ours
        return False


def readable(path: Path) -> bool:
    try:
        with Image.open(path) as image:
            image.verify()
        return True
    except OSError:
        return False


def scale_up(directory: Path) -> None:
    """Scales the Wear OS screenshots in [directory] that are below Google Play's minimum up to the
    round watch's size."""
    for path in sorted(directory.glob("*.png")):
        with Image.open(path) as image:
            if min(image.size) >= WEAR_MINIMUM:
                continue
            scaled = image.convert("RGB").resize((WEAR_SIZE, WEAR_SIZE), Image.LANCZOS)
        scaled.save(path)
        print(f"Scaled up {path.name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    platform = parser.add_mutually_exclusive_group(required=True)
    platform.add_argument("--ios", action="store_const", const="ios", dest="mode")
    platform.add_argument("--android", action="store_const", const="android", dest="mode")
    platform.add_argument("--wear", action="store_const", const="wear", dest="mode")
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    if args.mode == "wear":
        scale_up(args.directory)
        return

    screenshots: dict[str, dict] = json.loads(CAPTIONS.read_text())
    shots = {shot for entry in screenshots.values() for shot in entry["shots"]}
    found = {shot: captures(args.mode, args.directory, shot) for shot in shots}
    if not any(found.values()):
        return  # nothing captured, or already built
    sources = {shot: next((path for path in reversed(paths) if readable(path)), None) for shot, paths in found.items()}
    if missing := sorted(shot for shot, path in sources.items() if path is None):
        raise SystemExit(f"Missing captured screens in {args.directory}: {', '.join(missing)}")

    width, height = size = SIZES[args.mode]
    background = panorama(size, len(screenshots))
    # iOS keeps the device name in front, which deliver needs
    prefix = sources[next(iter(shots))].stem.split("-")[0] + "-" if args.mode == "ios" else ""
    for index, (name, entry) in enumerate(screenshots.items()):
        screens = [Image.open(sources[shot]) for shot in entry["shots"]]
        left = index * width
        framed = compose(screens, entry.get("layout"), entry["caption"], background.crop((left, 0, left + width, height)))
        info = PngImagePlugin.PngInfo()
        info.add_text(MARKER, "1")
        framed.save(args.directory / f"{prefix}{name}.png", pnginfo=info)
        print(f"Wrote {prefix}{name}.png")
    for paths in found.values():
        for path in paths:
            path.unlink()


if __name__ == "__main__":
    main()
