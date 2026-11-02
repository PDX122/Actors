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
float t=Time*Speed;vec2 uv=texCoord;
float flow=sin(uv.x*23.0+t*1.3)+0.5*sin(uv.x*51.0-t*0.7);
uv.y+=flow*0.035*(0.35+0.65*uv.y);
uv.x+=sin(uv.y*18.0+t*1.7)*0.018;
vec3 c=sampleAt(uv);c.r=sampleAt(uv+vec2(flow*0.004,0)).r;
c.b=sampleAt(uv-vec2(flow*0.004,0)).b;
finish(c);}
