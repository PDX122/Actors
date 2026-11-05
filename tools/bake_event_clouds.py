"""Bake deterministic tiled 3D value-noise slices into a bilinear RG lookup."""
from pathlib import Path
import numpy as np
from PIL import Image

out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/fbactors/events/clouds'
out.mkdir(parents=True,exist_ok=True)
values=np.random.default_rng(0xFBA770).integers(0,256,(512,512),dtype=np.uint8)
next_slice=np.roll(values,(-17,-37),axis=(0,1))
Image.fromarray(np.stack([values,next_slice,values],axis=-1)).save(out/'noise.png',optimize=True)
(out/'mesh.json').write_text('{"mips":1}\n')
