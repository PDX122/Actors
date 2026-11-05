"""Fetch credited Earth source imagery and pack two mipmapped runtime textures."""
from pathlib import Path
import json,urllib.request
from PIL import Image

root=Path(__file__).resolve().parents[1]
cache=root/'build/asset-tools/earth';cache.mkdir(parents=True,exist_ok=True)
out=root/'src/main/resources/assets/fbactors/events/earth';out.mkdir(parents=True,exist_ok=True)
sources={
 'day_large.jpg':'https://assets.science.nasa.gov/content/dam/science/esd/eo/images/bmng/bmng-base/july/world.200407.3x21600x10800.jpg',
 'clouds.jpg':'https://play.solarsystemscope.com/textures/download/8k_earth_clouds.jpg',
 'night.jpg':'https://play.solarsystemscope.com/textures/download/8k_earth_nightmap.jpg',
 'specular.tif':'https://play.solarsystemscope.com/textures/download/8k_earth_specular_map.tif'}
for name,url in sources.items():
    if not (cache/name).exists():
        request=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Referer':'https://play.solarsystemscope.com/textures/' if 'solarsystemscope' in url else 'https://science.nasa.gov/'})
        with urllib.request.urlopen(request) as response:(cache/name).write_bytes(response.read())
def image(name,mode,size=(4096,2048)):
    return Image.open(cache/name).convert(mode).resize(size,Image.Resampling.LANCZOS)
Image.MAX_IMAGE_PIXELS=300_000_000
day=image('day_large.jpg','RGB',(8192,4096));day.putalpha(image('specular.tif','L',(8192,4096)))
weather=image('night.jpg','RGB',(8192,4096));weather.putalpha(image('clouds.jpg','L',(8192,4096)))
for name,im in [('surface',day),('weather',weather)]:
    level=0
    while True:
        im.save(out/(name+('' if level==0 else '_'+str(level))+'.png'),optimize=True)
        if max(im.size)==1:break
        # Alpha stores ocean/cloud data, not transparency. RGBA resize would
        # premultiply by it and erase land colors/night lights in smaller mips.
        size=(max(1,im.width//2),max(1,im.height//2))
        im=Image.merge('RGBA',[channel.resize(size,Image.Resampling.LANCZOS) for channel in im.split()]);level+=1
(out/'mesh.json').write_text('{"mips":14}\n')
(out/'source.txt').write_text('''Surface: NASA Blue Marble Next Generation, July 2004.
Reto Stockli / NASA Earth Observatory. Surface resized to 8192x4096.
https://science.nasa.gov/earth/earth-observatory/blue-marble-next-generation/base-map/
Clouds, night lights and ocean specular mask: Solar System Scope, CC BY 4.0.
https://play.solarsystemscope.com/textures/
https://creativecommons.org/licenses/by/4.0/
Images packed into surface RGB / ocean A and night RGB / cloud A; mipmapped.
Historical composite imagery, not live weather or a map of the Minecraft scene.
''')
print(json.dumps({'output':str(out),'resolution':[8192,4096],'sources':sources}))
