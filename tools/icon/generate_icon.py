#!/usr/bin/env python3
"""Generates the Bibless app icon from the logo (tools/icon/logo.svg): a slanted "B" set between barcode bars.

The logo's geometry is rebuilt here from tools/icon/logo.svg's numbers, in its 512x512 coordinates
(x to the right, y down):
- a black block left of a slanted line, which also forms the B's stem,
- the B's two bowls, each a circle of radius R_OUTER with a counter of radius R_INNER,
- a black bar along the right edge.
The block and the bar run past the logo's square, so the mark can be scaled down for round
masks (watch face, Android launchers) while still bleeding off the icon's edges.

Rendered to:
- iOS / watchOS 1024px PNGs (Xcode asset catalogs)
- Android adaptive icon vector drawables (phone + Wear OS)
- the 512px Google Play store icon

Usage: python3 tools/icon/generate_icon.py [--preview out.png]
Requires Pillow and shapely (pip install pillow shapely).
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw
from shapely import affinity
from shapely.geometry import Point, Polygon, box
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parents[2]

BACKGROUND = "#FFFFFF"
MARK = "#000000"

# ---------------------------------------------------------------- logo geometry (from logo.svg)

SIZE = 512.0
SLANT_TOP_X, SLANT_BOTTOM_X = 193.453, 95.957  # the slanted edge meets y=0 and y=512 here
R_OUTER, R_INNER = 127.273, 63.273  # bowl radii; the stroke is their difference
TOP_BOWL = (227.491, 160.728)  # centers
BOTTOM_BOWL = (224.001, 351.272)
BAR_X = 400.607  # the right bar runs from here to the right edge
FAR = 4 * SIZE  # how far the block and the bar extend past the square


def slant_x(y):
    return SLANT_BOTTOM_X + (SLANT_TOP_X - SLANT_BOTTOM_X) * (SIZE - y) / SIZE


def logo():
    left_of_slant = Polygon([(-FAR, -FAR), (slant_x(-FAR), -FAR), (slant_x(SIZE + FAR), SIZE + FAR),
                             (-FAR, SIZE + FAR)])
    (tx, ty), (bx, by) = TOP_BOWL, BOTTOM_BOWL
    # Outer shape of the B: the bowls plus the bars that join them to the stem
    outer = unary_union([
        Point(tx, ty).buffer(R_OUTER, quad_segs=64), box(-FAR, ty - R_OUTER, tx, ty + R_OUTER),
        Point(bx, by).buffer(R_OUTER, quad_segs=64), box(-FAR, by - R_OUTER, bx, by + R_OUTER),
    ])
    # Counters stop at the slanted edge, which keeps the stem's width
    counters = unary_union([
        Point(tx, ty).buffer(R_INNER, quad_segs=64), box(-FAR, ty - R_INNER, tx, ty + R_INNER),
        Point(bx, by).buffer(R_INNER, quad_segs=64), box(-FAR, by - R_INNER, bx, by + R_INNER),
    ]).difference(left_of_slant)
    bar = box(BAR_X, -FAR, SIZE + FAR, SIZE + FAR)
    # The B's own extent (without the block it merges into), used to fit it into round masks
    letter = outer.intersection(box(SLANT_BOTTOM_X, 0, SIZE, SIZE))
    return unary_union([left_of_slant, outer.difference(counters), bar]), letter


def mark(canvas, fit, circle=False):
    """The logo scaled so the B fits a square (or circle) of fit * canvas, clipped to the canvas."""
    shape, b = logo()
    x0, y0, x1, y1 = b.bounds
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    if circle:
        extent = 2 * max(math.hypot(x - cx, y - cy) for x, y in b.exterior.coords)
    else:
        cx, cy, extent = SIZE / 2, SIZE / 2, SIZE  # the logo's own framing
    scale = fit * canvas / extent
    shape = affinity.translate(affinity.scale(shape, scale, scale, origin=(cx, cy)),
                               canvas / 2 - cx, canvas / 2 - cy)
    return shape.intersection(box(0, 0, canvas, canvas))


# ---------------------------------------------------------------- PNG rendering

PHONE_FIT = 1.0  # the logo as designed, bleeding off the edges
WATCH_FIT = 0.86  # diameter of the circle the B fits in, relative to the round watch face


def render_png(size, fit, circle=False, supersample=4):
    s = size * supersample
    img = Image.new("RGB", (s, s), BACKGROUND)
    shape = mark(s, fit, circle)
    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)
    for p in getattr(shape, "geoms", [shape]):
        d.polygon(list(p.exterior.coords), fill=255)
        for hole in p.interiors:
            d.polygon(list(hole.coords), fill=0)
    img.paste(MARK, mask=mask)
    return img.resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------- Android vector drawables

def fmt(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def android_foreground(viewport=108.0, safe_zone=66.0):
    # Launchers may mask adaptive icons down to a 66dp circle; the B must fit inside it, while
    # the block and the bar still run out to the edges of the 108dp layer
    shape = mark(viewport, 0.97 * safe_zone / viewport, circle=True).simplify(0.01)

    def ring(coords):
        return "M" + "L".join(f"{fmt(x)},{fmt(y)}" for x, y in list(coords)[:-1]) + "Z"

    path = "".join(ring(p.exterior.coords) + "".join(ring(h.coords) for h in p.interiors)
                   for p in getattr(shape, "geoms", [shape]))
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/icon/generate_icon.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{MARK}"
        android:fillType="evenOdd"
        android:pathData="{path}" />
</vector>
"""


ANDROID_BACKGROUND = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/icon/generate_icon.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{BACKGROUND}"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""

ADAPTIVE_ICON = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""


def write_asset_catalog(appiconset: Path, image: Image.Image):
    image.save(appiconset / "AppIcon.png")
    contents = json.loads((appiconset / "Contents.json").read_text())
    for entry in contents["images"]:
        if "appearances" not in entry:  # default (light) slot; dark/tinted fall back to it
            entry["filename"] = "AppIcon.png"
    (appiconset / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="only write a preview PNG to this path")
    args = parser.parse_args()

    if args.preview:
        render_png(512, PHONE_FIT).save(args.preview)
        return

    write_asset_catalog(ROOT / "app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset",
                        render_png(1024, PHONE_FIT))
    # watchOS crops the icon to a circle
    write_asset_catalog(ROOT / "app/iosApp/watchApp/Assets.xcassets/AppIcon.appiconset",
                        render_png(1024, WATCH_FIT, circle=True))

    play_icon = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
    play_icon.parent.mkdir(parents=True, exist_ok=True)
    render_png(512, PHONE_FIT).save(play_icon)

    for module in ("androidApp", "wearApp"):
        res = ROOT / "app" / module / "src/main/res"
        (res / "drawable-v24/ic_launcher_foreground.xml").write_text(android_foreground())
        (res / "drawable/ic_launcher_background.xml").write_text(ANDROID_BACKGROUND)
        (res / "mipmap-anydpi-v26/ic_launcher.xml").write_text(ADAPTIVE_ICON)
        (res / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(ADAPTIVE_ICON)


if __name__ == "__main__":
    main()
