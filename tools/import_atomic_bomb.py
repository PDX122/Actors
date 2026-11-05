"""Bake the supplied glTF into a stackless triangle BVH and bounded-size PBR textures.

Usage: python import_atomic_bomb.py path/to/atomic_bomb_-_easter_freebie.zip
Requires numpy, Pillow and meshoptimizer at authoring time; no runtime dependency.
"""
import io
import json
from pathlib import Path
import sys
import zipfile
import numpy as np
import meshoptimizer
import ctypes

# The Windows Python wrapper omits this function's ctypes declaration.
meshoptimizer.simplifier.lib.meshopt_simplifyWithAttributes.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_float,ctypes.c_uint,ctypes.c_void_p]
meshoptimizer.simplifier.lib.meshopt_simplifyWithAttributes.restype=ctypes.c_size_t
from PIL import Image

out = Path(__file__).resolve().parents[1] / "src/main/resources/assets/fbactors/events/atomic_bomb"
out.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(sys.argv[1]) as archive:
    doc = json.loads(archive.read("scene.gltf"))
    binary = archive.read("scene.bin")
    def accessor(index, width):
        item = doc["accessors"][index]
        view = doc["bufferViews"][item["bufferView"]]
        dtype = "<f4" if item["componentType"] == 5126 else "<u4"
        offset = view.get("byteOffset", 0) + item.get("byteOffset", 0)
        return np.frombuffer(binary, dtype=dtype, count=item["count"]*width, offset=offset).reshape(-1, width).copy()
    position, normal, tangent, uv, indices = accessor(0,3), accessor(1,3), accessor(2,4), accessor(3,2), accessor(4,1).reshape(-1,3)
    source_triangles=len(indices)
    # Attribute-aware QEM preserves texture seams, fins and shading while removing
    # triangles that contribute little to the silhouette. This runs only at authoring time.
    simplified=np.empty(indices.size,dtype=np.uint32)
    error=np.zeros(1,dtype=np.float32)
    count=meshoptimizer.simplify_with_attributes(simplified,indices.ravel(),position,
        np.concatenate([normal,uv],axis=1),np.array([.03,.03,.03,.15,.15],dtype=np.float32),
        target_index_count=4000*3,target_error=.015,result_error=error)
    indices=simplified[:count].reshape(-1,3)
    # The model's longitudinal axis is Z. Preserve selected source vertices and UVs,
    # rotate it to nose-up Y, centre it, and give the complete shell a 4 m height.
    factor = 4 / np.ptp(position[:,2])
    position = position[:,[0,2,1]] * np.array([factor,factor,-factor])
    position[:,1] -= (position[:,1].min()+position[:,1].max())*.5
    normal = normal[:,[0,2,1]] * np.array([1,1,-1])
    tangent[:,:3] = tangent[:,[0,2,1]] * np.array([1,1,-1])
    vertices = np.concatenate([position,uv[:,0:1],normal,uv[:,1:2],tangent],axis=1).astype('<f4')
    triangles = vertices[indices]
    bounds_min = triangles[:,:,:3].min(axis=1)
    bounds_max = triangles[:,:,:3].max(axis=1)
    centers = (bounds_min+bounds_max)*.5
    nodes, ordered = [], []
    def build(ids):
        index = len(nodes)
        node = np.zeros((3,4),dtype='<f4');nodes.append(node)
        node[0,:3]=bounds_min[ids].min(axis=0)-.00001;node[1,:3]=bounds_max[ids].max(axis=0)+.00001
        if len(ids)<=6:
            node[0,3]=len(ordered);node[1,3]=len(ids);ordered.extend(ids)
        else:
            axis=np.ptp(centers[ids],axis=0).argmax()
            ids=ids[np.argsort(centers[ids,axis],kind='stable')];middle=len(ids)//2
            build(ids[:middle]);build(ids[middle:])
        node[2,0]=len(nodes)
        return index
    build(np.arange(len(triangles)))
    header=np.zeros((1,4),dtype='<f4');header[0]=[len(nodes),1+len(nodes)*3,len(triangles),1]
    data=np.concatenate([header,np.concatenate(nodes),triangles[ordered].reshape(-1,4)])
    width=1024;height=(len(data)+width-1)//width
    padded=np.zeros((height*width,4),dtype='<f4');padded[:len(data)]=data
    (out/'mesh.bin').write_bytes(padded.tobytes())
    (out/'mesh.json').write_text(json.dumps({'width':width,'height':height,'nodes':len(nodes),'triangles':len(triangles),'sourceTriangles':source_triangles,'simplificationError':float(error[0]),'boundsMin':position.min(axis=0).tolist(),'boundsMax':position.max(axis=0).tolist()},indent=2)+'\n')
    for original,name in [('baseColor','albedo'),('metallicRoughness','material'),('normal','normal')]:
        image=Image.open(io.BytesIO(archive.read('textures/material_'+original+'.png'))).convert('RGB')
        image.thumbnail((1536,1536),Image.Resampling.LANCZOS)
        image.save(out/(name+'.png'),optimize=True)
    (out/'license.txt').write_bytes(archive.read('license.txt'))
print(json.dumps({'triangles':len(triangles),'bvhNodes':len(nodes),'texture':[width,height],'output':str(out)}))
