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
float t=Time*Speed; vec2 uv=texCoord;
float band=step(0.88,hash(vec2(floor(uv.y*38.0),floor(t*12.0))));
uv.x+=(hash(vec2(floor(uv.y*38.0),floor(t*12.0)+9.0))-0.5)*band*0.12;
uv.x+=sin(uv.y*21.0+t*5.0)*0.0025;
float rolling=exp(-pow((uv.y-fract(t*0.17))/0.025,2.0));
vec3 c=vec3(sampleAt(uv+vec2(0.006,0)).r,sampleAt(uv).g,sampleAt(uv-vec2(0.008,0)).b);
c*=0.86+0.14*sin(uv.y*float(textureSize(InSampler,0).y)*3.14159);
c+=rolling*(hash(gl_FragCoord.xy+floor(t*24.0))-0.35)*0.6;
c+=vec3(hash(gl_FragCoord.xy+floor(t*24.0))-0.5)*0.055;
finish(c);}
