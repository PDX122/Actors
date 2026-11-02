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
float t=floor(Time*Speed*10.0);vec2 uv=texCoord;
float row=floor(uv.y*24.0);float n=hash(vec2(row,t));
float tear=step(0.72,n);uv.x+=(n-0.5)*0.27*tear;
vec2 cell=vec2(textureSize(InSampler,0))/mix(1.0,12.0,tear);
uv=mix(uv,(floor(uv*cell)+0.5)/cell,tear);
vec3 c=sampleAt(uv);float shift=0.007+tear*0.025;
c.r=sampleAt(uv+vec2(shift,0)).r;c.b=sampleAt(uv-vec2(shift,0)).b;
float dropout=step(0.96,hash(floor(texCoord*vec2(18,22))+t));
c=mix(c,vec3(0.05,0.95,0.85)*hash(vec2(row,t+7.0)),dropout*0.7);
finish(c);}
