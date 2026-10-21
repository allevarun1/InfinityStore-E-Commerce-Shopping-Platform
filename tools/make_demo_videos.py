"""
Renders the demo motion-design clips for the storefront from the sample saree photos, using ffmpeg only.
Output goes to ecommerce-frontend/public/videos (mp4 + cover jpg). Re-run after changing the text below.

    python tools/make_demo_videos.py

Needs ffmpeg on PATH and the Georgia italic font (Windows). These clips exist so a fresh dev store has
something to play; a real store replaces them with its own footage from the admin's Videos tab.
"""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PHOTOS = ROOT / "ecommerce-frontend" / "public" / "images" / "sarees"
OUT = ROOT / "ecommerce-frontend" / "public" / "videos"
FONT = "georgiai.ttf"  # copied next to the run so the filter needs no drive-letter escaping
SYSTEM_FONT = pathlib.Path("C:/Windows/Fonts/georgiai.ttf")
CWD = None
GOLD = "0xE9CF95"
FPS = 30


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=CWD)
    if r.returncode:
        sys.exit(f"ffmpeg failed:\n{' '.join(cmd)[:400]}\n{r.stderr[-1500:]}")


def segment(photo, title, subtitle, out, w, h, fgs, fgy, ty, ts, dur, fades=True):
    """One animated card: blurred backdrop, slow-zoom photo with a light sweep, gold lines, kinetic text."""
    frames = int(dur * FPS)
    fx = (w - fgs) // 2
    text = lambda t, size, colour, y, delay, extra="": (
        f"drawtext=fontfile={FONT}:text='{t}':fontsize={size}:fontcolor={colour}:x=(w-text_w)/2:"
        f"y='{y}+50*pow(1-min(max((t-{delay})/0.8,0),1),3)':alpha='min(max((t-{delay})/0.8,0),1)'{extra}")
    graph = ";".join([
        "[0:v]split=2[a][b]",
        f"[a]scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},gblur=sigma=26,eq=brightness=-0.22:saturation=1.15,format=yuv420p[bg]",
        f"[b]scale={fgs * 2}:{fgs * 2},zoompan=z='1.0+0.12*on/{frames}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={fgs}x{fgs}:fps={FPS},format=rgba[fg]",
        f"[1:v]format=rgba,geq=r=255:g=255:b=255:a='clip(150*exp(-pow((X+Y*0.45-(mod(T,2.6)*{fgs}*1.7-{fgs}*0.6))/({fgs}*0.06),2)),0,150)'[sw]",
        "[fg][sw]overlay=format=auto,fade=t=in:st=0:d=0.7:alpha=1[fgs]",
        f"[bg][fgs]overlay=x={fx}:y='{fgy}+44*pow(1-min(t/0.9,1),3)':format=auto[v1]",
        "[3:v]split=2[l1][l2]",
        f"[v1][l1]overlay=x='-{w}+{w}*min(t/0.8,1)':y={fgy - 16}[v2]",
        f"[v2][l2]overlay=x='{w}-{w}*min(t/0.8,1)':y={fgy + fgs + 14}[v3]",
        f"[v3][2:v]overlay=x='-{w}+{w}*t/{dur}':y={h - 6}[v4]",
        "[v4]" + ",".join([
            text("INFINITYSTORE", 22, GOLD, 56, 0.0),
            text(title, ts, "white", ty, 0.3),
            text(subtitle, 22, GOLD, ty + ts + 26, 0.6),
        ]) + (f",fade=t=in:st=0:d=0.3,fade=t=out:st={dur - 0.3}:d=0.3" if fades else "") + "[outv]",
    ])
    run(["ffmpeg", "-y", "-loglevel", "error",
         "-loop", "1", "-framerate", str(FPS), "-t", str(dur), "-i", str(photo),
         "-f", "lavfi", "-i", f"color=c=white:s={fgs}x{fgs}:r={FPS}:d={dur}",
         "-f", "lavfi", "-i", f"color=c={GOLD}:s={w}x4:r={FPS}:d={dur}",
         "-f", "lavfi", "-i", f"color=c={GOLD}:s={w}x2:r={FPS}:d={dur}",
         "-filter_complex", graph, "-map", "[outv]", "-r", str(FPS), "-t", str(dur),
         "-c:v", "libx264", "-crf", "26", "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", str(out)])


def cover(video, out, at):
    run(["ffmpeg", "-y", "-loglevel", "error", "-ss", str(at), "-i", str(video), "-frames:v", "1", "-q:v", "3", str(out)])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = OUT / "_tmp"
    tmp.mkdir(exist_ok=True)
    global CWD
    CWD = tmp
    (tmp / FONT).write_bytes(SYSTEM_FONT.read_bytes())

    reels = [
        ("reel-kanchipuram", "kanchipuram-silk-saree.jpg", "Kanchipuram Pure Silk Saree", "PURE MULBERRY SILK  |  GOLD ZARI BORDER"),
        ("reel-banarasi", "banarasi-silk-saree.jpg", "Banarasi Katan Silk Saree", "LUSTROUS KATAN SILK  |  BROCADE PALLU"),
        ("reel-kasavu", "kerala-kasavu-saree.jpg", "Kerala Kasavu Cotton Saree", "PURE COTTON  |  GOLDEN KASAVU BORDER"),
    ]
    for name, photo, title, sub in reels:
        segment(PHOTOS / photo, title, sub, OUT / f"{name}.mp4", 540, 960, 480, 170, 730, 34, 7)
        cover(OUT / f"{name}.mp4", OUT / f"{name}.jpg", 1.6)
        print("made", name)

    cards = [
        ("kanchipuram-silk-saree.jpg", "Silk", "KANCHIPURAM  |  BANARASI  |  MYSORE"),
        ("bengal-handloom-cotton-saree.jpg", "Cotton", "BENGAL HANDLOOM  |  KERALA KASAVU"),
        ("designer-georgette-saree.jpg", "Designer", "GEORGETTE  |  ORGANZA"),
        ("bridal-red-zari-saree.jpg", "Bridal", "ZARI  |  HAND EMBELLISHED"),
    ]
    parts = []
    for i, (photo, title, sub) in enumerate(cards):
        p = tmp / f"s{i}.mp4"
        segment(PHOTOS / photo, title, sub, p, 720, 900, 600, 120, 770, 54, 3.5, fades=False)
        parts.append(p)
    xf, last, offset = [], "[0:v]", 0.0
    for i in range(1, len(parts)):
        offset += 3.5 - 0.5
        tag = f"[x{i}]"
        xf.append(f"{last}[{i}:v]xfade=transition=fade:duration=0.5:offset={offset}{tag}")
        last = tag
    total = offset + 3.5
    xf.append(f"{last}fade=t=in:st=0:d=0.3,fade=t=out:st={total - 0.4}:d=0.4[outv]")
    cmd = ["ffmpeg", "-y", "-loglevel", "error"]
    for p in parts:
        cmd += ["-i", str(p)]
    cmd += ["-filter_complex", ";".join(xf), "-map", "[outv]", "-c:v", "libx264", "-crf", "26", "-preset", "slow",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", str(OUT / "story-collection.mp4")]
    run(cmd)
    cover(OUT / "story-collection.mp4", OUT / "story-collection.jpg", 1.4)
    print("made story-collection")

    for p in tmp.glob("*"):
        p.unlink()
    tmp.rmdir()
    for f in sorted(OUT.glob("*")):
        print(f"{f.name:28s}{f.stat().st_size / 1024:8.0f} KB")


if __name__ == "__main__":
    main()
