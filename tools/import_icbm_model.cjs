// Authoring only: npm install --prefix build/asset-tools three@0.128.0 @napi-rs/canvas
// Extract only the supplied model/livery declarations, never its controls or animation.
const fs=require('fs'),path=require('path'),vm=require('vm');
const root=path.resolve(__dirname,'..'),modules=path.join(root,'build/asset-tools/node_modules');
const T=require(path.join(modules,'three')),{createCanvas,Path2D}=require(path.join(modules,'@napi-rs/canvas'));
const source=fs.readFileSync(process.argv[2],'utf8'),out=path.join(root,'build/asset-tools/icbm');
fs.mkdirSync(out,{recursive:true});
const declarations=source.slice(source.indexOf('  // ---------- Livery'),source.indexOf('  // ---------- Ground'));
if(!declarations.includes('var parts3='))throw Error('Supplied model declarations not found');
const context=vm.createContext({T,Path2D,scene:new T.Scene(),document:{createElement(name){if(name!=='canvas')throw Error(name);return createCanvas(1,1);}}});
vm.runInContext(declarations,context,{timeout:10000});
const materials=[...new Set(context.mats)],tile=1024,pad=4,grid=4,atlas=createCanvas(tile*grid,tile*grid),a=atlas.getContext('2d');
const surface=createCanvas(tile*grid,tile*grid),m=surface.getContext('2d');
materials.forEach((mat,index)=>{
 const x=index%grid*tile,y=Math.floor(index/grid)*tile;
 if(mat.map)a.drawImage(mat.map.image,x,y,tile,tile);
 else {a.fillStyle='#'+mat.color.getHexString();a.fillRect(x,y,tile,tile);}
 // Replicate a small border so atlas filtering never exposes another material.
 const temp=createCanvas(tile,tile),c=temp.getContext('2d');c.drawImage(atlas,x,y,tile,tile,0,0,tile,tile);
 a.drawImage(temp,x+pad,y+pad,tile-pad*2,tile-pad*2);
 m.fillStyle=`rgb(0,${Math.round(mat.roughness*255)},${Math.round(mat.metalness*255)})`;m.fillRect(x,y,tile,tile);
});
fs.writeFileSync(path.join(out,'albedo.png'),atlas.toBuffer('image/png'));
fs.writeFileSync(path.join(out,'material.png'),surface.toBuffer('image/png'));
const parts=[context.g1,context.g2,context.g3,context.halfL,context.halfR,context.gB];
const names=['first_stage','second_stage','third_stage','shroud_left','shroud_right','post_boost'];
const result=[];
for(let part=0;part<parts.length;part++){
 const group=parts[part];group.updateMatrixWorld(true);const inverse=group.matrixWorld.clone().invert(),vertices=[],indices=[];
 group.traverse(mesh=>{
  if(!mesh.isMesh)return;
  const geometry=mesh.geometry,transform=inverse.clone().multiply(mesh.matrixWorld),normalMatrix=new T.Matrix3().getNormalMatrix(transform);
  const p=geometry.attributes.position,n=geometry.attributes.normal,uv=geometry.attributes.uv,offset=vertices.length/8;
  const slot=materials.indexOf(mesh.material);if(slot<0)throw Error('Unknown material');
  for(let i=0;i<p.count;i++){
   const point=new T.Vector3().fromBufferAttribute(p,i).applyMatrix4(transform).multiplyScalar(31.4/18.77);
   const normal=new T.Vector3().fromBufferAttribute(n,i).applyMatrix3(normalMatrix).normalize();
   const u=mesh.material.map && uv?uv.getX(i):.5,v=mesh.material.map && uv?uv.getY(i):.5;
   vertices.push(point.x,point.y,point.z,(slot%grid*tile+pad+u*(tile-pad*2))/(tile*grid),normal.x,normal.y,normal.z,(Math.floor(slot/grid)*tile+pad+(1-v)*(tile-pad*2))/(tile*grid));
  }
  const ix=geometry.index?Array.from(geometry.index.array):Array.from({length:p.count},(_,i)=>i);
  indices.push(...ix.map(i=>i+offset));
 });
 result.push({name:names[part],vertices,indices});
}
fs.writeFileSync(path.join(out,'geometry.json'),JSON.stringify(result));
console.log(result.map(p=>({part:p.name,triangles:p.indices.length/3})));
