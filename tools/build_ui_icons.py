"""Rebuild the bundled Phosphor regular icons as backend-independent alpha spans."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from urllib.request import urlopen
import io, json
import cairosvg
from PIL import Image

REV = "2b75f3ad12b420c9504ef05df8d2564a28f8500e"
NAMES = ["users", "bookmark-simple", "folder", "magnifying-glass", "user-focus", "arrow-u-up-left", "person-simple-walk", "floppy-disk", "dots-three", "check", "upload-simple"]
NAMES += ["trash", "plus", "record", "microphone", "subtitles", "sliders-horizontal", "palette"]
NAMES += ["play", "boat", "cube", "camera", "film-strip", "steering-wheel"]
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/fbactors/ui/phosphor"

def asset(name):
    svg = urlopen(f"https://raw.githubusercontent.com/phosphor-icons/core/{REV}/assets/regular/{name}.svg",timeout=30).read()
    image = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg,output_width=24,output_height=24))).convert("RGBA")
    spans=[]
    for y in range(24):
        x=0
        while x<24:
            alpha=image.getpixel((x,y))[3]
            end=x+1
            while end<24 and image.getpixel((end,y))[3]==alpha: end+=1
            if alpha: spans.extend([x,y,end-x,alpha])
            x=end
    return name, svg, spans

if __name__ == "__main__":
    OUT.mkdir(parents=True,exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        assets=list(pool.map(asset,NAMES))
    for name,svg,spans in assets: (OUT/(name+".svg")).write_bytes(svg)
    (OUT/"icons.json").write_text(json.dumps({"size":24,"revision":REV,"icons":{name:spans for name,svg,spans in assets}},separators=(",",":")),encoding="utf-8")
    (OUT/"LICENSE").write_bytes(urlopen(f"https://raw.githubusercontent.com/phosphor-icons/core/{REV}/LICENSE",timeout=30).read())
    print(f"Built {len(assets)} Phosphor icons at {REV}")
