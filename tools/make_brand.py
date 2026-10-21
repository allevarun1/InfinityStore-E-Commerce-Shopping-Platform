"""
Builds the Rekha Shopping Mall logo from the store's own typefaces, so it matches the site exactly and stays crisp.

    python tools/make_brand.py

The mark: an upright R (the loom: structure, tradition) with an italic S (the pallu: flow) woven through its leg,
set in a jharokha arch with a fine zari border. Letters come from Cormorant Garamond (SIL Open Font License),
the words "SHOPPING MALL" from DM Sans (OFL), converted to outlines so the files need no fonts.

Writes:
  ecommerce-frontend/src/components/brandPaths.js   glyph outlines used by <BrandMark/> on the site
  ecommerce-frontend/public/favicon.svg              tile icon
  ecommerce-frontend/public/brand/*.svg              emblem, tile, horizontal and stacked lockups (light and dark)
PNG copies (icons, Open Graph card, logo PNGs for print and WhatsApp) are rasterised from these SVGs in a browser.

Needs: pip install fonttools brotli
"""
import json
import pathlib

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parent.parent / "ecommerce-frontend"
CG = ROOT / "node_modules" / "@fontsource" / "cormorant-garamond" / "files"
DM = ROOT / "node_modules" / "@fontsource-variable" / "dm-sans" / "files" / "dm-sans-latin-wght-normal.woff2"
OUT = ROOT / "public" / "brand"

MAROON, MAROON_DEEP, GAP = "#8e2433", "#4f0f19", "#5e1520"
INK, IVORY, CREAM, GOLD_INK = "#1e1915", "#fbf7f1", "#fbf3e2", "#a87a2c"

num = lambda v: f"{v:.1f}".rstrip("0").rstrip(".")


def outline(font, text, size, tracking=0.0):
    """Returns (svg path d, width) for text set at `size` with its baseline at y=0."""
    gs, cmap = font.getGlyphSet(), font.getBestCmap()
    s = size / font["head"].unitsPerEm
    pen, x = SVGPathPen(gs, ntos=num), 0.0
    for i, ch in enumerate(text):
        g = cmap[ord(ch)]
        if ch != " ":
            gs[g].draw(TransformPen(pen, (s, 0, 0, -s, x, 0)))
        x += gs[g].width * s + (tracking * size if i < len(text) - 1 else 0)
    return pen.getCommands(), x


def glyph(font, ch):
    gs = font.getGlyphSet()
    g = font.getBestCmap()[ord(ch)]
    pen = SVGPathPen(gs, ntos=num)
    gs[g].draw(TransformPen(pen, (1, 0, 0, -1, 0, 0)))
    return pen.getCommands()


R = glyph(TTFont(CG / "cormorant-garamond-latin-700-normal.woff"), "R")
S = glyph(TTFont(CG / "cormorant-garamond-latin-700-italic.woff"), "S")
SERIF = TTFont(CG / "cormorant-garamond-latin-700-normal.woff")
SANS = instancer.instantiateVariableFont(TTFont(DM), {"wght": 600})

# Monogram geometry, in a 100-wide box. Found by eye at 16 px to 512 px; see the comments for the why.
R_AT = "translate(14 90) scale(.08)"        # the upright R
S_AT = "translate(52 94) scale(.09)"        # the italic S, a touch larger and lower so it reads as a partner, not a subscript
TRIM = (50, 64, 13, 18)                      # hides the S's lower hook tip, which otherwise pokes out beside the leg
MONO_CENTRE = (55.5, 66)                     # centre of the monogram's ink


def monogram(uid, gold, s_fill=CREAM, gap=GAP):
    """The S is laid down first and the R's leg sweeps across it, edged with a fine maroon keyline like a thread
    crossing the warp. One crossing, drawn in plain stacking order, so there are no seams at any size."""
    tx, ty, tw, th = TRIM
    return (
        f'<mask id="t{uid}" maskUnits="userSpaceOnUse" x="0" y="0" width="140" height="140"><rect width="140" height="140" fill="#fff"/>'
        f'<rect x="{tx}" y="{ty}" width="{tw}" height="{th}" fill="#000"/></mask>'
        f'<g mask="url(#t{uid})"><path transform="{S_AT}" d="{S}" fill="none" stroke="{gap}" stroke-width="30" stroke-linejoin="round"/>'
        f'<path class="rs-s" transform="{S_AT}" d="{S}" fill="{s_fill}"/></g>'
        f'<path transform="{R_AT}" d="{R}" fill="none" stroke="{gap}" stroke-width="32.5" stroke-linejoin="round"/>'
        f'<path class="rs-r" transform="{R_AT}" d="{R}" fill="{gold}"/>'
    )


def gradients(uid):
    return (
        f'<linearGradient id="g{uid}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f7e3b0"/>'
        f'<stop offset=".45" stop-color="#e2bf74"/><stop offset=".7" stop-color="#c99a45"/><stop offset="1" stop-color="#f1d898"/></linearGradient>'
        f'<linearGradient id="m{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{MAROON}"/><stop offset="1" stop-color="{MAROON_DEEP}"/></linearGradient>'
    )


ARCH = "M8 132 V58 C8 34 30 24 41 14 C46 9.5 49 5 50 1 C51 5 54 9.5 59 14 C70 24 92 34 92 58 V132 Z"
ARCH_IN = "M13 127 V59 C13 38 33 29 43 19.5 C46.5 16 49 13 50 9.5 C51 13 53.5 16 57 19.5 C67 29 87 38 87 59 V127 Z"
DIAMOND = "M50 24 L52.6 28 L50 32 L47.4 28 Z"


def placed(scale, cx, cy):
    mx, my = MONO_CENTRE
    return f"translate({cx} {cy}) scale({scale}) translate({-mx} {-my})"


def emblem_body(uid):
    """The arch emblem, 100 x 134."""
    g = f"url(#g{uid})"
    return (
        f'<defs>{gradients(uid)}</defs>'
        f'<path d="{ARCH}" fill="url(#m{uid})"/>'
        f'<path class="rs-zari" d="{ARCH_IN}" fill="none" stroke="{g}" stroke-width="1.6"/>'
        f'<path d="{DIAMOND}" fill="{g}"/>'
        f'<g transform="{placed(0.8, 50, 78)}">{monogram(uid, g)}</g>'
        f'<path d="M33 117 H45 M55 117 H67" stroke="{g}" stroke-width="1.1"/><path d="M50 114.6 L52.4 117 L50 119.4 L47.6 117 Z" fill="{g}"/>'
    )


def tile_body(uid, full_bleed=False, safe=1.0):
    """Square icon, 120 x 120. full_bleed: no rounded corners (iOS rounds them itself). safe: shrink for maskable icons."""
    g = f"url(#g{uid})"
    bg = (f'<rect width="120" height="120" fill="url(#m{uid})"/>' if full_bleed
          else f'<rect width="120" height="120" rx="27" fill="url(#m{uid})"/>')
    ring = "" if safe < 1 else f'<rect x="6" y="6" width="108" height="108" rx="{17 if full_bleed else 22}" fill="none" stroke="{g}" stroke-width="1.3"/>'
    return f'<defs>{gradients(uid)}</defs>{bg}{ring}<g transform="{placed(1.18 * safe, 60, 62)}">{monogram(uid, g)}</g>'


def svg(view, body, label="Rekha Shopping Mall"):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" role="img" aria-label="{label}">{body}</svg>\n'


def lockup(dark=False, stacked=False):
    word, ww = outline(SERIF, "Rekha", 60)
    tag, tw = outline(SANS, "SHOPPING MALL", 11.2, tracking=0.42)
    ink, gold = (IVORY, "#e3c787") if dark else (INK, GOLD_INK)
    uid = "d" if dark else "l"
    if stacked:
        w = max(ww, tw + 40) + 40
        cx = w / 2
        body = (f'<g transform="translate({cx - 50} 0)">{emblem_body(uid)}</g>'
                f'<path transform="translate({cx - ww / 2} 196)" d="{word}" fill="{ink}"/>'
                f'<path d="M{cx - tw / 2 - 26} 218 H{cx - tw / 2 - 8} M{cx + tw / 2 + 8} 218 H{cx + tw / 2 + 26}" stroke="{gold}" stroke-width="1"/>'
                f'<path transform="translate({cx - tw / 2} 222)" d="{tag}" fill="{gold}"/>')
        return svg(f"0 0 {num(w)} 232", body)
    x = 100 * 0.72 + 22
    w = x + max(ww, tw) + 4
    body = (f'<g transform="scale(.72)">{emblem_body(uid)}</g>'
            f'<path transform="translate({x} 58)" d="{word}" fill="{ink}"/>'
            f'<path transform="translate({x + 2} 84)" d="{tag}" fill="{gold}"/>')
    return svg(f"0 0 {num(w)} 97", body)


SIG = {
    "light": dict(r=["#8a5a1c", "#c99a45", "#e7c77e", "#9b6a22"], s=["#c2185b", "#9d1149", "#6e0d33"], gap=IVORY,
                  word=["#6f4513", "#a87a2c", "#c99a45", "#7f5117"], sub=INK),
    "dark": dict(r=["#f7e3b0", "#e2bf74", "#c99a45", "#f1d898"], s=["#fff4dc", "#f4cfd9", "#f0a8c0"], gap=INK,
                 word=["#f7e3b0", "#e2bf74", "#f1d898", "#c99a45"], sub="#eadfcd"),
}


def grad(gid, stops, x2=1, y2=1):
    n = len(stops) - 1
    return (f'<linearGradient id="{gid}" x1="0" y1="0" x2="{x2}" y2="{y2}">'
            + "".join(f'<stop offset="{i / n:.2f}" stop-color="{c}"/>' for i, c in enumerate(stops)) + "</linearGradient>")


def signature(tone="light", background=False):
    """The header lockup: free-standing monogram, REKHA in spaced capitals, "— SHOPPING MALL —" centred beneath."""
    c, uid = SIG[tone], "sig" + tone[0]
    tx, ty, tw, th = TRIM
    word, ww = outline(SERIF, "REKHA", 56, tracking=0.1)
    tag, tw2 = outline(SANS, "SHOPPING MALL", 10.5, tracking=0.3)
    mono_w = 84 * 1.2                                    # the monogram box (84 x 66) drawn at 1.2x
    x0 = mono_w + 20
    cx = x0 + ww / 2
    w = x0 + max(ww, tw2 + 52) + 6
    body = (
        f'<defs>{grad("r" + uid, c["r"])}{grad("s" + uid, c["s"], 0.6, 1)}{grad("w" + uid, c["word"], 1, 0)}'
        f'<mask id="t{uid}" maskUnits="userSpaceOnUse" x="0" y="0" width="140" height="140"><rect width="140" height="140" fill="#fff"/>'
        f'<rect x="{tx}" y="{ty}" width="{tw}" height="{th}" fill="#000"/>'
        # the gap where the R crosses the S is cut out of the S, so the logo sits on any background (no keyline colour)
        f'<path transform="{R_AT}" d="{R}" fill="#000" stroke="#000" stroke-width="34" stroke-linejoin="round"/></mask></defs>'
        + (f'<rect width="100%" height="100%" fill="{c["gap"]}"/>' if background else "")
        + f'<g transform="translate(0 2) scale(1.2) translate(-14 -33)">'
        f'<g mask="url(#t{uid})"><path transform="{S_AT}" d="{S}" fill="url(#s{uid})"/></g>'
        f'<path transform="{R_AT}" d="{R}" fill="url(#r{uid})"/></g>'
        f'<path transform="translate({x0} 50)" d="{word}" fill="url(#w{uid})"/>'
        f'<path d="M{cx - tw2 / 2 - 22} 69 H{cx - tw2 / 2 - 7} M{cx + tw2 / 2 + 7} 69 H{cx + tw2 / 2 + 22}" stroke="{c["sub"]}" stroke-width="0.9"/>'
        f'<path transform="translate({cx - tw2 / 2} 73)" d="{tag}" fill="{c["sub"]}"/>'
    )
    return svg(f"0 0 {num(w)} 82", body)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = {
        OUT / "rekha-emblem.svg": svg("0 0 100 134", emblem_body("e")),
        OUT / "rekha-tile.svg": svg("0 0 120 120", tile_body("t")),
        OUT / "rekha-tile-full.svg": svg("0 0 120 120", tile_body("f", full_bleed=True)),
        OUT / "rekha-tile-maskable.svg": svg("0 0 120 120", tile_body("k", full_bleed=True, safe=0.78)),
        OUT / "rekha-logo.svg": lockup(),
        OUT / "rekha-logo-dark.svg": lockup(dark=True),
        OUT / "rekha-logo-stacked.svg": lockup(stacked=True),
        OUT / "rekha-logo-stacked-dark.svg": lockup(dark=True, stacked=True),
        OUT / "rekha-signature.svg": signature("light"),
        OUT / "rekha-signature-dark.svg": signature("dark", background=True),
        ROOT / "public" / "favicon.svg": svg("0 0 120 120", tile_body("v")),
    }
    for path, text in files.items():
        path.write_text(text, encoding="utf-8")
        print(f"{path.relative_to(ROOT)}  {len(text) // 1024} KB")
    js = ROOT / "src" / "components" / "brandPaths.js"
    geometry = {"R": R, "S": S, "R_AT": R_AT, "S_AT": S_AT, "TRIM": TRIM,
                "ARCH": ARCH, "ARCH_IN": ARCH_IN, "DIAMOND": DIAMOND, "MONO": placed(0.8, 50, 78)}
    js.write_text("// Generated by tools/make_brand.py from Cormorant Garamond (OFL). Do not edit by hand.\n"
                  + "".join(f"export const {k} = {json.dumps(v)};\n" for k, v in geometry.items()), encoding="utf-8")
    print(f"{js.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
