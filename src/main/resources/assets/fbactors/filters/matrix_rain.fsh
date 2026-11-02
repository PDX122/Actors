#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Amount;
uniform float Speed;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 sampleAt(vec2 p){return texture(InSampler,clamp(p,vec2(0.001),vec2(0.999))).rgb;}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
void finish(vec3 c){vec4 original=texture(InSampler,texCoord);fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);}
void main(){
vec2 size=vec2(textureSize(InSampler,0));vec2 cell=vec2(7,10)*max(1.0,size.y/720.0);
vec2 grid=texCoord*size/cell;vec2 id=floor(grid);vec2 p=fract(grid);
float column=hash(vec2(id.x,2));float head=mod(Time*Speed*(5.0+column*9.0)+column*60.0,size.y/cell.y);
float distance=mod(head-id.y+size.y/cell.y,size.y/cell.y);
float trail=exp(-distance*0.16);float digit=step(0.45,hash(vec2(floor(p.x*4.0)+id.x*7.0,floor(p.y*6.0)+id.y*11.0+floor(Time*Speed*2.0))));
digit*=step(p.x,0.77)*step(p.y,0.84);
float shape=luma(sampleAt((id+0.5)*cell/size));
vec3 c=vec3(0.001,0.01,0.003)+vec3(0.04,1.0,0.22)*digit*(trail*0.65+shape*0.7);
c+=vec3(0.6,1.0,0.7)*digit*step(distance,1.0)*0.65;
finish(c);}
