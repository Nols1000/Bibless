#!/usr/bin/env python3
"""Generates the Bibless app icon: a stylized QR code with a runner in the middle.

All geometry is defined once in QR module units (a 25x25 grid) and rendered to:
- iOS / watchOS 1024px PNGs (Xcode asset catalogs)
- Android adaptive icon vector drawables (phone + Wear OS)
- the 512px Google Play store icon

Usage: python3 tools/icon/generate_icon.py [--preview out.png]
Requires Pillow.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]

BACKGROUND = "#0B6E5B"  # deep teal
MODULE = "#7FD1BF"  # mint, subdued so the runner stands out
FINDER = "#FFFFFF"
RUNNER = "#FFFFFF"

GRID = 25
CENTER = GRID / 2
CLEAR_RADIUS = 5.2  # modules within this distance of the center are left empty for the runner
RUNNER_MARGIN = 1.0  # modules closer than this to the runner outline are left empty too
MODULE_CORNER = 0.32  # corner radius as a fraction of a module
STROKE = 1.5  # runner limb thickness in modules
RUNNER_SCALE = 1.12  # scales the pose below around the grid center

# Runner pose in module coordinates (x to the right, y down), running to the right
HEAD = ((14.35, 8.55), 1.15)
LIMBS = [
    [(13.55, 10.55), (11.95, 14.05)],  # torso
    [(13.3, 11.2), (11.25, 12.1), (10.15, 10.75)],  # back arm
    [(13.3, 11.2), (14.75, 12.85), (16.35, 12.0)],  # front arm
    [(11.95, 14.05), (14.2, 15.1), (13.75, 17.35)],  # front leg
    [(11.95, 14.05), (10.75, 16.1), (8.75, 16.35)],  # back leg
]


def runner():
    """The runner pose scaled around the grid center: (limbs, head center, head radius)."""
    def t(pt):
        return CENTER + (pt[0] - CENTER) * RUNNER_SCALE, CENTER + 0.3 + (pt[1] - CENTER) * RUNNER_SCALE
    (head, radius) = HEAD
    return [[t(pt) for pt in limb] for limb in LIMBS], t(head), radius * RUNNER_SCALE


def finder_origins():
    return [(0, 0), (GRID - 7, 0), (0, GRID - 7)]


def in_finder_zone(x, y):
    # Finder pattern plus its one-module separator
    return any(ox - 1 <= x <= ox + 7 and oy - 1 <= y <= oy + 7 for ox, oy in finder_origins())


def near_runner(x, y):
    """Whether the module at (x, y) would touch the runner."""
    cx, cy = x + 0.5, y + 0.5
    limbs, (hx, hy), hr = runner()
    if math.hypot(cx - hx, cy - hy) < hr + RUNNER_MARGIN:
        return True
    for limb in limbs:
        for (ax, ay), (bx, by) in zip(limb, limb[1:]):
            dx, dy = bx - ax, by - ay
            t = max(0.0, min(1.0, ((cx - ax) * dx + (cy - ay) * dy) / (dx * dx + dy * dy)))
            if math.hypot(cx - ax - t * dx, cy - ay - t * dy) < STROKE / 2 + RUNNER_MARGIN:
                return True
    return False


def data_modules():
    """Deterministic pseudo-random modules that look like QR data."""
    modules = []
    for y in range(GRID):
        for x in range(GRID):
            if in_finder_zone(x, y):
                continue
            if math.hypot(x + 0.5 - CENTER, y + 0.5 - CENTER) < CLEAR_RADIUS or near_runner(x, y):
                continue
            if x == 6 or y == 6:  # timing patterns
                if (x + y) % 2 == 0:
                    modules.append((x, y))
                continue
            if hashlib.sha256(f"bibless-{x}-{y}".encode()).digest()[0] < 118:
                modules.append((x, y))
    return modules


# ---------------------------------------------------------------- PNG rendering

def render_png(size, qr_fraction, supersample=4):
    s = size * supersample
    img = Image.new("RGB", (s, s), BACKGROUND)
    d = ImageDraw.Draw(img)
    m = s * qr_fraction / GRID  # module size in px
    off = (s - m * GRID) / 2

    def px(x, y):
        return off + x * m, off + y * m

    for x, y in data_modules():
        x0, y0 = px(x + 0.06, y + 0.06)
        x1, y1 = px(x + 0.94, y + 0.94)
        d.rounded_rectangle((x0, y0, x1, y1), radius=m * MODULE_CORNER, fill=MODULE)

    for ox, oy in finder_origins():
        d.rounded_rectangle((*px(ox, oy), *px(ox + 7, oy + 7)), radius=m * 1.6, fill=FINDER)
        d.rounded_rectangle((*px(ox + 1, oy + 1), *px(ox + 6, oy + 6)), radius=m * 1.0, fill=BACKGROUND)
        d.rounded_rectangle((*px(ox + 2, oy + 2), *px(ox + 5, oy + 5)), radius=m * 0.7, fill=FINDER)

    limbs, (hx, hy), hr = runner()
    w = STROKE * m
    for limb in limbs:
        pts = [px(x, y) for x, y in limb]
        d.line(pts, fill=RUNNER, width=round(w), joint="curve")
        for cx, cy in pts:  # round caps and joints
            d.ellipse((cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2), fill=RUNNER)
    cx, cy = px(hx, hy)
    d.ellipse((cx - hr * m, cy - hr * m, cx + hr * m, cy + hr * m), fill=RUNNER)

    return img.resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------- Android vector drawables

def fmt(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def rounded_rect_path(x, y, w, h, r):
    return (f"M{fmt(x + r)},{fmt(y)}h{fmt(w - 2 * r)}a{fmt(r)},{fmt(r)} 0 0 1 {fmt(r)},{fmt(r)}"
            f"v{fmt(h - 2 * r)}a{fmt(r)},{fmt(r)} 0 0 1 {fmt(-r)},{fmt(r)}h{fmt(-(w - 2 * r))}"
            f"a{fmt(r)},{fmt(r)} 0 0 1 {fmt(-r)},{fmt(-r)}v{fmt(-(h - 2 * r))}"
            f"a{fmt(r)},{fmt(r)} 0 0 1 {fmt(r)},{fmt(-r)}z")


def android_foreground(qr_size=50.0, viewport=108.0):
    m = qr_size / GRID
    off = (viewport - qr_size) / 2

    def p(v):
        return off + v * m

    modules = "".join(
        rounded_rect_path(p(x + 0.06), p(y + 0.06), 0.88 * m, 0.88 * m, m * MODULE_CORNER)
        for x, y in data_modules()
    )
    finders = "".join(
        rounded_rect_path(p(ox), p(oy), 7 * m, 7 * m, 1.6 * m)
        + rounded_rect_path(p(ox + 1), p(oy + 1), 5 * m, 5 * m, 1.0 * m)
        + rounded_rect_path(p(ox + 2), p(oy + 2), 3 * m, 3 * m, 0.7 * m)
        for ox, oy in finder_origins()
    )
    runner_limbs, (hx, hy), hr = runner()
    limbs = "".join(
        "M" + "L".join(f"{fmt(p(x))},{fmt(p(y))}" for x, y in limb) for limb in runner_limbs
    )
    r = hr * m
    head = (f"M{fmt(p(hx) - r)},{fmt(p(hy))}a{fmt(r)},{fmt(r)} 0 1 0 {fmt(2 * r)},0"
            f"a{fmt(r)},{fmt(r)} 0 1 0 {fmt(-2 * r)},0z")

    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/icon/generate_icon.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{MODULE}"
        android:pathData="{modules}" />
    <path
        android:fillColor="{FINDER}"
        android:fillType="evenOdd"
        android:pathData="{finders}" />
    <path
        android:strokeColor="{RUNNER}"
        android:strokeWidth="{fmt(STROKE * m)}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{limbs}" />
    <path
        android:fillColor="{RUNNER}"
        android:pathData="{head}" />
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
        render_png(1024, qr_fraction=0.80).save(args.preview)
        return

    ios = render_png(1024, qr_fraction=0.80)
    write_asset_catalog(ROOT / "app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset", ios)
    write_asset_catalog(ROOT / "app/iosApp/watchApp/Assets.xcassets/AppIcon.appiconset", ios)

    play_icon = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
    play_icon.parent.mkdir(parents=True, exist_ok=True)
    render_png(512, qr_fraction=0.80).save(play_icon)

    for module in ("androidApp", "wearApp"):
        res = ROOT / "app" / module / "src/main/res"
        (res / "drawable-v24/ic_launcher_foreground.xml").write_text(android_foreground())
        (res / "drawable/ic_launcher_background.xml").write_text(ANDROID_BACKGROUND)
        (res / "mipmap-anydpi-v26/ic_launcher.xml").write_text(ADAPTIVE_ICON)
        (res / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(ADAPTIVE_ICON)


if __name__ == "__main__":
    main()
