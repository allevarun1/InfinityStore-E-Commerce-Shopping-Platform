"""
Renders a cinematic motion-design reel for every sample saree photo, with ffmpeg only.
Three shots per saree (a slow push-in on the whole drape, a close-up of the zari and border, a close-up of the
fall of the fabric), cross-dissolved, then graded: film grain, vignette, a gold light leak, letterbox bars and
the saree's name. Output: ecommerce-frontend/public/videos/cine-<slug>.mp4 and .jpg (540x960).

    python tools/make_cinema_videos.py

These are demo clips built from the sample catalogue photos so a fresh dev store has something to play.
A real store replaces them with its own footage from the admin's Videos tab.
"""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PHOTOS = ROOT / "ecommerce-frontend" / "public" / "images" / "sarees"
OUT = ROOT / "ecommerce-frontend" / "public" / "videos"
FONT = "georgiai.ttf"
SYSTEM_FONT = pathlib.Path("C:/Windows/Fonts/georgiai.ttf")
GOLD = "0xE9CF95"
W, H, FPS = 540, 960, 30
SHOT, FADE = 2.6, 0.5  # seconds per shot, cross-dissolve length

SAREES = [
    ("kanchipuram", "kanchipuram-silk-saree.jpg", "Kanchipuram Pure Silk", "MULBERRY SILK  |  GOLD ZARI"),
    ("banarasi", "banarasi-silk-saree.jpg", "Banarasi Katan Silk", "BROCADE PALLU  |  KATAN"),
    ("mysore", "mysore-crepe-silk-saree.jpg", "Mysore Crepe Silk", "SOFT DRAPE  |  CREPE SILK"),
    ("chanderi", "chanderi-silk-cotton-saree.jpg", "Chanderi Silk Cotton", "SHEER GLOW  |  LIGHTWEIGHT"),
    ("bengal", "bengal-handloom-cotton-saree.jpg", "Bengal Handloom Cotton", "HAND WOVEN  |  BREATHABLE"),
    ("kasavu", "kerala-kasavu-saree.jpg", "Kerala Kasavu Cotton", "GOLDEN BORDER  |  PURE COTTON"),
    ("georgette", "designer-georgette-saree.jpg", "Designer Georgette", "PARTY EDIT  |  FLOWING FALL"),
    ("organza", "organza-zari-saree.jpg", "Organza Zari Butta", "FESTIVE  |  ZARI BUTTAS"),
    ("bridal", "bridal-red-zari-saree.jpg", "Bridal Red Zari Silk", "WEDDING DAY  |  HAND EMBELLISHED"),
    # Openly licensed photos (see docs/CREDITS.md); framing differs for fabric flats, standing models and back views.
    ("pochampally", "pochampally-ikat-silk.jpg", "Pochampally Ikat Silk", "TELANGANA  |  HAND-DYED IKAT", "flat"),
    ("sambalpuri", "sambalpuri-ikat-elephant.jpg", "Sambalpuri Ikat Cotton", "ODISHA  |  ELEPHANT MOTIFS", "flat"),
    ("sambalpuri-drape", "sambalpuri-handloom-drape.jpg", "Sambalpuri Handloom", "THE DRAPE  |  ODISHA", "model"),
    ("paithani", "paithani-silk-peacock.jpg", "Paithani Peacock Silk", "MAHARASHTRA  |  PEACOCK PALLU", "back"),
    ("net", "blush-net-wedding.jpg", "Blush Net Wedding Saree", "SHEER NET  |  EMBROIDERED", "model"),
    ("patola", "patola-double-ikat.jpg", "Patola Double Ikat", "GUJARAT  |  HEIRLOOM WEAVE", "flat"),
    ("bandhani", "banarasi-bandhani.jpg", "Banarasi Bandhani", "CRAPE SILK  |  ZARI BORDER", "flat"),
    ("koorai", "koorai-silk-checks.jpg", "Koorai Silk Checks", "TAMIL NADU  |  WEDDING SILK", "flat"),
    ("paisley", "red-paisley-zari-silk.jpg", "Red Paisley Zari Silk", "GOLD ZARI  |  PAISLEY BORDER", "flat"),
    ("bandha-cotton", "sambalpuri-bandha-cotton.jpg", "Sambalpuri Bandha Cotton", "ODISHA  |  TEMPLE BORDER", "flat"),
]

SHOT_SETS = {
    "flat": [(1.00, 1.18, 0.50, 0.50, 0.50, 0.44), (1.90, 2.30, 0.30, 0.70, 0.30, 0.36), (1.80, 2.20, 0.70, 0.30, 0.80, 0.68)],
    "model": [(1.00, 1.14, 0.50, 0.50, 0.50, 0.46), (1.90, 2.20, 0.50, 0.52, 0.15, 0.19), (1.70, 1.95, 0.55, 0.45, 0.72, 0.58)],
    "back": [(1.00, 1.14, 0.50, 0.50, 0.50, 0.46), (1.80, 2.10, 0.45, 0.55, 0.78, 0.70), (1.80, 2.10, 0.50, 0.50, 0.18, 0.24)],
}

# (zoom start, zoom end, focus x start/end, focus y start/end) as fractions of the portrait crop
SHOTS = [
    (1.00, 1.16, 0.50, 0.50, 0.50, 0.46),  # whole drape, slow push-in
    (1.85, 2.15, 0.35, 0.65, 0.34, 0.30),  # close-up: zari and border, drifting across
    (1.70, 1.95, 0.60, 0.42, 0.78, 0.64),  # close-up: fall of the fabric, drifting up
]


def run(cmd, cwd):
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=cwd)
    if r.returncode:
        sys.exit(f"ffmpeg failed:\n{' '.join(cmd)[:500]}\n{r.stderr[-1500:]}")


def shot(photo, spec, out, cwd):
    z0, z1, x0, x1, y0, y1 = spec
    n = int(SHOT * FPS)
    p = f"on/{n}"
    z = f"{z0}+({z1}-{z0})*{p}"
    fx = f"({x0}+({x1}-{x0})*{p})"
    fy = f"({y0}+({y1}-{y0})*{p})"
    graph = (
        "crop=ih*9/16:ih,scale=1080:1920:flags=lanczos,"
        f"zoompan=z='{z}':x='clip({fx}*iw-iw/zoom/2,0,iw-iw/zoom)':y='clip({fy}*ih-ih/zoom/2,0,ih-ih/zoom)':"
        f"d={n}:s={W}x{H}:fps={FPS},format=yuv420p"
    )
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(photo), "-vf", graph, "-t", str(SHOT),
         "-c:v", "libx264", "-crf", "17", "-preset", "fast", "-pix_fmt", "yuv420p", str(out)], cwd)


def text(t, size, colour, y, start, end=None):
    enable = f":enable='between(t,{start},{end})'" if end else f":enable='gte(t,{start})'"
    a = f"min(max((t-{start})/0.7,0),1)"
    return (f"drawtext=fontfile={FONT}:text='{t}':fontsize={size}:fontcolor={colour}:x=(w-text_w)/2:"
            f"y='{y}+34*pow(1-{a},3)':alpha='{a}'{enable}")


def make(slug, photo, title, sub, cwd, kind=None):
    parts = []
    for i, spec in enumerate(SHOT_SETS[kind] if kind else SHOTS):
        p = cwd / f"{slug}_{i}.mp4"
        shot(PHOTOS / photo, spec, p, cwd)
        parts.append(p)
    total = SHOT * 3 - FADE * 2
    off1, off2 = SHOT - FADE, 2 * (SHOT - FADE)
    bar = 96
    graph = ";".join([
        f"[0:v][1:v]xfade=transition=fade:duration={FADE}:offset={off1}[a]",
        f"[a][2:v]xfade=transition=fade:duration={FADE}:offset={off2}[b]",
        # grade: a touch of warmth and contrast, soft vignette, moving film grain
        "[b]eq=contrast=1.08:saturation=1.12:gamma=0.97,curves=r='0/0 0.5/0.53 1/1':b='0/0.02 0.5/0.47 1/0.97',"
        "vignette=angle=PI/4.2,noise=alls=9:allf=t,format=rgba[g]",
        # a gold light leak that sweeps across once, near the start of the close-ups
        f"color=c=black@0:s={W}x{H}:r={FPS}:d={total},format=rgba,"
        f"geq=r=255:g=205:b=120:a='clip(105*exp(-pow((X+Y*0.35-(clip(T-1.9,0,1.6)/1.6*{W * 2.2}-{W * 0.6}))/({W}*0.11),2)),0,105)'[leak]",
        "[g][leak]overlay=format=auto[l]",
        # letterbox bars slide in, then the brand and the saree name
        f"[l]drawbox=x=0:y=0:w=iw:h='{bar}*min(t/0.8,1)':color=black:t=fill,"
        f"drawbox=x=0:y='ih-{bar}*min(t/0.8,1)':w=iw:h={bar}:color=black:t=fill,"
        + ",".join([
            text("REKHA SHOPPING MALL", 20, GOLD, 34, 0.5),
            text(title, 34, "white", 742, 1.0),
            text(sub, 17, GOLD, 792, 1.3),
        ]) + f",fade=t=in:st=0:d=0.35,fade=t=out:st={total - 0.4}:d=0.4[outv]",
    ])
    cmd = ["ffmpeg", "-y", "-loglevel", "error"]
    for p in parts:
        cmd += ["-i", str(p)]
    cmd += ["-filter_complex", graph, "-map", "[outv]", "-r", str(FPS), "-t", f"{total:.2f}",
            "-c:v", "libx264", "-crf", "27", "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
            str(OUT / f"cine-{slug}.mp4")]
    run(cmd, cwd)
    run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "3.2", "-i", str(OUT / f"cine-{slug}.mp4"), "-frames:v", "1", "-q:v", "3",
         str(OUT / f"cine-{slug}.jpg")], cwd)
    for p in parts:
        p.unlink()
    print("made", slug)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = OUT / "_tmp"
    tmp.mkdir(exist_ok=True)
    (tmp / FONT).write_bytes(SYSTEM_FONT.read_bytes())
    only = sys.argv[1:]
    for slug, photo, title, sub, *kind in SAREES:
        if only and slug not in only:
            continue
        make(slug, photo, title, sub, tmp, kind[0] if kind else None)
    for p in tmp.glob("*"):
        p.unlink()
    tmp.rmdir()
    for f in sorted(OUT.glob("cine-*")):
        print(f"{f.name:30s}{f.stat().st_size / 1024:8.0f} KB")


if __name__ == "__main__":
    main()
