"""Bake the extracted supplied procedural mesh into independently animated BVHs."""
import ctypes,json
from pathlib import Path
import numpy as np
import meshoptimizer
from PIL import Image

root=Path(__file__).resolve().parents[1]
source=root/'build/asset-tools/icbm'
out=root/'src/main/resources/assets/fbactors/events/icbm'
out.mkdir(parents=True,exist_ok=True)
lib=meshoptimizer.simplifier.lib.meshopt_simplifyWithAttributes
lib.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_void_p,ctypes.c_size_t,ctypes.c_float,ctypes.c_uint,ctypes.c_void_p]
lib.restype=ctypes.c_size_t
parts=json.loads((source/'geometry.json').read_text())
headers=np.zeros((len(parts),4),dtype='<f4');chunks=[headers];cursor=len(parts);total=0;node_count=0;info=[]
for part in parts:
    v=np.array(part['vertices'],dtype='<f4').reshape(-1,8)
    indices=np.array(part['indices'],dtype=np.uint32)
    # Weld byte-identical vertices before QEM; keep normals and atlas seams.
    v,inverse=np.unique(v,axis=0,return_inverse=True);indices=inverse[indices].astype(np.uint32)
    simplified=np.empty(indices.size,dtype=np.uint32)
    count=meshoptimizer.simplify_with_attributes(simplified,indices,np.ascontiguousarray(v[:,:3]),
        np.ascontiguousarray(v[:,[4,5,6,3,7]]),np.array([.03,.03,.03,.10,.10],dtype=np.float32),
        target_index_count=max(1200,int(len(indices)*.30))//3*3,target_error=.008)
    faces=simplified[:count].reshape(-1,3)
    normal=v[:,4:7];tangent=np.cross(normal,np.where(np.abs(normal[:,1:2])>.95,np.array([[0,0,1]]),np.array([[0,1,0]])))
    tangent/=np.maximum(np.linalg.norm(tangent,axis=1,keepdims=True),.00001)
    vertices=np.concatenate([v,np.column_stack([tangent,np.ones(len(v))])],axis=1).astype('<f4')
    triangles=vertices[faces];low=triangles[:,:,:3].min(axis=1);high=triangles[:,:,:3].max(axis=1);centers=(low+high)*.5
    nodes=[];ordered=[]
    def build(ids):
        index=len(nodes);node=np.zeros((3,4),dtype='<f4');nodes.append(node)
        node[0,:3]=low[ids].min(axis=0)-.00001;node[1,:3]=high[ids].max(axis=0)+.00001
        if len(ids)<=6:
            node[0,3]=len(ordered);node[1,3]=len(ids);ordered.extend(ids)
        else:
            axis=np.ptp(centers[ids],axis=0).argmax();ids=ids[np.argsort(centers[ids,axis],kind='stable')];mid=len(ids)//2
            build(ids[:mid]);build(ids[mid:])
        node[2,0]=len(nodes)
    build(np.arange(len(triangles)))
    headers[len(info)]=[len(nodes),cursor+len(nodes)*3,len(triangles),cursor]
    data=np.concatenate([np.concatenate(nodes),triangles[ordered].reshape(-1,4)])
    chunks.append(data);cursor+=len(data);total+=len(triangles);node_count+=len(nodes)
    info.append({'name':part['name'],'triangles':len(triangles),'sourceTriangles':len(indices)//3})
data=np.concatenate(chunks);width=1024;height=(len(data)+width-1)//width
padded=np.zeros((height*width,4),dtype='<f4');padded[:len(data)]=data
(out/'mesh.bin').write_bytes(padded.tobytes())
(out/'mesh.json').write_text(json.dumps({'width':width,'height':height,'triangles':total,'nodes':node_count,'parts':info,'mips':7},indent=2)+'\n')
for name in ['albedo','material']:
    Image.open(source/(name+'.png')).convert('RGB').save(out/(name+'.png'),optimize=True)
Image.new('RGB',(64,64),(128,128,255)).save(out/'normal.png')
# Both models get filtered material mip chains; geometry data must stay nearest.
for directory in [out,root/'src/main/resources/assets/fbactors/events/atomic_bomb']:
    metadata=json.loads((directory/'mesh.json').read_text());metadata['mips']=13;(directory/'mesh.json').write_text(json.dumps(metadata,indent=2)+'\n')
    for name in ['albedo','material','normal']:
        image=Image.open(directory/(name+'.png')).convert('RGB')
        for level in range(1,1+int(np.floor(np.log2(max(image.size))))):
            image=image.resize((max(1,image.width//2),max(1,image.height//2)),Image.Resampling.LANCZOS)
            image.save(directory/(name+'_'+str(level)+'.png'),optimize=True)
(out/'source.txt').write_text('Geometry and livery adapted from user-supplied icbm-model.zip / icbm-model.html.\nStandalone demo controls, environment and flight simulation excluded.\nMeshes scaled uniformly to 31.4 m, attribute-simplified and baked as six independent parts.\n')
print(json.dumps({'triangles':total,'parts':info,'height':height}))
