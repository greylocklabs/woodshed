"""Generates the Woodshed logo: assets/logo.svg and the Android launcher icon layers.

One shape list feeds both outputs, so the SVG and the app icon cannot drift apart.
Run from the repo root: python3 tools/logo.py
"""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"

BARK = ("#c99269", "#8c5735")  # warm brown bark, lighter at the top
CUT = ("#f7e4cb", "#e9c9a1")  # pale sawn wood
KNOB_SHADE = ("#b87d55", "#7b4a2c")  # a little deeper than the bark, so the knob stands off the log
LEAF = ("#a8dd8f", "#5faa6a")
RIM, RING, STEM = "#6e4026", "#c69a6e", "#5faa6a"
BACKGROUND = "#fbf3ea"

# A brown log lying on its side, its cut end to the right, a leaf sprouting from the top and a knob, a
# sawn-off branch, on its side pointing down toward the cut end.
LOG = "M38 48 H96 V108 H38 Q8 108 8 78 Q8 48 38 48 Z"
END = (96, 78)
KNOB_END, KNOB_RIM, KNOB_CUT, KNOB_TILT = (62, 103), (10, 6), (7.8, 4.4), 32


def _knob():
    """The knob's body: from a rounded base up on the log to the rim of its cut end, leaning [KNOB_TILT]."""
    t = math.radians(KNOB_TILT)
    along, across = (math.sin(t), math.cos(t)), (math.cos(t), -math.sin(t))
    rx = KNOB_RIM[0]

    def at(point, v, k):
        return round(point[0] + v[0] * k, 1), round(point[1] + v[1] * k, 1)

    a, b = at(KNOB_END, across, rx), at(KNOB_END, across, -rx)
    a0, b0 = at(a, along, -16), at(b, along, -16)
    return f"M{b0[0]} {b0[1]} A{rx} {rx * 0.8} {-KNOB_TILT} 0 1 {a0[0]} {a0[1]} L{a[0]} {a[1]} L{b[0]} {b[1]} Z"


KNOB = _knob()
RIM_RADII, CUT_RADII = (23, 30), (19, 26)
# Growth rings, kept faint so they read as texture rather than detail at icon size.
RINGS = [(14, 19.5), (9, 12.5), (4, 5.5)]
RING_ALPHA = 0.55
STEM_PATH = "M58 49 Q57 41 62 36"
LEAF_PATH = "M61 37 Q66 16 92 18 Q86 42 61 37 Z"


def ellipse(cx, cy, rx, ry, clockwise=False, tilt=0):
    """Clockwise and counter-clockwise ellipses cancel under the nonzero fill rule, which cuts holes.
    [tilt] rotates the ellipse by that many degrees."""
    sweep = 1 if clockwise else 0
    r = math.radians(tilt)
    x1, y1, x2, y2 = cx - rx * math.cos(r), cy - rx * math.sin(r), cx + rx * math.cos(r), cy + rx * math.sin(r)
    return f"M{x1:.2f} {y1:.2f} A{rx} {ry} {tilt} 1 {sweep} {x2:.2f} {y2:.2f} A{rx} {ry} {tilt} 1 {sweep} {x1:.2f} {y1:.2f} Z"


def hole(cx, cy, rx, ry):
    """A counter-clockwise cut-out of an ellipse. Its left half lies over the log's body as well as the rim,
    so that half is cut twice."""
    near = f"M{cx} {cy - ry} A{rx} {ry} 0 0 0 {cx} {cy + ry} Z"
    far = f"M{cx} {cy + ry} A{rx} {ry} 0 0 0 {cx} {cy - ry} Z"
    return " ".join([near, near, far])


def svg():
    def grad(id_, stops, x2, y2="1"):
        return (f'<linearGradient id="{id_}" x1="0" y1="0" x2="{x2}" y2="{y2}">'
                f'<stop offset="0" stop-color="{stops[0]}"/><stop offset="1" stop-color="{stops[1]}"/></linearGradient>')
    (cx, cy) = END
    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" height="128" role="img" aria-labelledby="t">',
        '  <title id="t">Woodshed</title>',
        f'  <defs>{grad("bark", BARK, "0")}{grad("knob", KNOB_SHADE, "0")}{grad("cut", CUT, "0.4")}{grad("leaf", LEAF, "1")}</defs>',
        "  <!-- a brown log on its side: cut end to the right, a leaf on top, a sawn-off knob on its side -->",
        f'  <path d="{LOG}" fill="url(#bark)"/>',
        f'  <path d="{KNOB}" fill="url(#knob)"/>',
        f'  <path d="{ellipse(*KNOB_END, *KNOB_RIM, tilt=-KNOB_TILT)}" fill="{RIM}"/>',
        f'  <path d="{ellipse(*KNOB_END, *KNOB_CUT, tilt=-KNOB_TILT)}" fill="url(#cut)"/>',
        f'  <ellipse cx="{cx}" cy="{cy}" rx="{RIM_RADII[0]}" ry="{RIM_RADII[1]}" fill="{RIM}"/>',
        f'  <ellipse cx="{cx}" cy="{cy}" rx="{CUT_RADII[0]}" ry="{CUT_RADII[1]}" fill="url(#cut)"/>',
        f'  <g fill="none" stroke="{RING}" stroke-width="2" opacity="{RING_ALPHA}">'
        + "".join(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}"/>' for rx, ry in RINGS) + "</g>",
        f'  <path d="{STEM_PATH}" fill="none" stroke="{STEM}" stroke-width="4" stroke-linecap="round"/>',
        f'  <path d="{LEAF_PATH}" fill="url(#leaf)"/>',
        "</svg>",
    ]
    return "\n".join(parts) + "\n"


# The drawing spans x 8-119 and y 18-111 of its 128 units, centred on (63.5, 64.1).
CENTER = (63.5, 64.1)


def group(scale):
    """Scales the drawing and centres it in the icon's 108 units."""
    tx, ty = 54 - CENTER[0] * scale, 54 - CENTER[1] * scale
    return f'<group android:translateX="{tx:.2f}" android:translateY="{ty:.2f}" android:scaleX="{scale}" android:scaleY="{scale}">'


# Color: 72 of 108 units, inside the visible circle. Themed icons draw the monochrome layer alone, so its
# glyph is kept to about 48 units, as launchers expect, or it looks oversized beside other themed icons.
GROUP = group(0.5625)
MONO_GROUP = group(0.42)
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/logo.py; edit that instead. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')
# The logo alone at its own 128-unit size, for showing inside the app.
LOGO_HEAD = HEAD.replace('android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"',
                         'android:width="128dp" android:height="128dp" android:viewportWidth="128" android:viewportHeight="128"')


def vd_path(d, fill=None, gradient=None, stroke=None, width=None, alpha=None):
    attrs = [f'android:pathData="{d}"']
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if stroke:
        attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{width}"', 'android:strokeLineCap="round"']
        if alpha is not None:
            attrs.append(f'android:strokeAlpha="{alpha}"')
    body = ""
    if gradient:
        (top, bottom), (x1, y1, x2, y2) = gradient
        body = (f'<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
                f'android:endX="{x2}" android:endY="{y2}" android:startColor="{top}" android:endColor="{bottom}"/></aapt:attr>')
    return f'  <path {" ".join(attrs)}>{body}</path>\n'


def foreground(head=HEAD, group=GROUP):
    (cx, cy) = END
    out = head + group + "\n"
    kx, ky = KNOB_END
    out += vd_path(LOG, gradient=(BARK, (0, 48, 0, 108)))
    out += vd_path(KNOB, gradient=(KNOB_SHADE, (0, 84, 0, 109)))
    out += vd_path(ellipse(kx, ky, *KNOB_RIM, tilt=-KNOB_TILT), fill=RIM)
    out += vd_path(ellipse(kx, ky, *KNOB_CUT, tilt=-KNOB_TILT), gradient=(CUT, (kx - 4, ky - 4, kx + 4, ky + 4)))
    out += vd_path(ellipse(cx, cy, *RIM_RADII), fill=RIM)
    out += vd_path(ellipse(cx, cy, *CUT_RADII), gradient=(CUT, (cx - 19, cy - 26, cx - 4, cy + 26)))
    out += "".join(vd_path(ellipse(cx, cy, rx, ry), stroke=RING, width=2, alpha=RING_ALPHA) for rx, ry in RINGS)
    out += vd_path(STEM_PATH, stroke=STEM, width=4)
    out += vd_path(LEAF_PATH, gradient=(LEAF, (61, 16, 92, 42)))
    return out + "</group>\n</vector>\n"


def monochrome():
    """Themed icons tint one silhouette. The cut end is a hole, leaving the bark rim and a heart dot, so the
    shape still reads as a log rather than a fruit. The knob sits inside the outline, so it is left out."""
    (cx, cy) = END
    solid = " ".join([
        LOG, ellipse(cx, cy, *RIM_RADII, clockwise=True), hole(cx, cy, *CUT_RADII), ellipse(cx, cy, 6, 8, clockwise=True),
    ])
    out = HEAD + MONO_GROUP + "\n"
    out += vd_path(solid, fill="#000000")
    out += vd_path(STEM_PATH, stroke="#000000", width=4) + vd_path(LEAF_PATH, fill="#000000")
    return out + "</group>\n</vector>\n"


def background():
    return HEAD + f'  <path android:pathData="M0,0h108v108h-108z" android:fillColor="{BACKGROUND}"/>\n</vector>\n'


if __name__ == "__main__":
    (ROOT / "assets").mkdir(exist_ok=True)
    (ROOT / "assets/logo.svg").write_text(svg())
    (RES / "ic_launcher_foreground.xml").write_text(foreground())
    (RES / "ic_launcher_monochrome.xml").write_text(monochrome())
    (RES / "ic_launcher_background.xml").write_text(background())
    (RES / "logo.xml").write_text(foreground(LOGO_HEAD, "<group>"))
    print("wrote assets/logo.svg, the launcher icon layers and the in-app logo")
