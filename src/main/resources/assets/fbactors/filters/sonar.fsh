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
vec2 px=2.0/vec2(textureSize(InSampler,0));float v=luma(sampleAt(texCoord));
float edge=length(vec2(luma(sampleAt(texCoord+vec2(px.x,0)))-luma(sampleAt(texCoord-vec2(px.x,0))),luma(sampleAt(texCoord+vec2(0,px.y)))-luma(sampleAt(texCoord-vec2(0,px.y)))));
vec2 p=texCoord-0.5;p.x*=float(textureSize(InSampler,0).x)/float(textureSize(InSampler,0).y);
float wave=fract(Time*Speed*0.25);float r=length(p);
float ring=exp(-pow((r-wave*1.3)/0.025,2.0));float tail=exp(-max(0.0,wave*1.3-r)*9.0)*step(r,wave*1.3);
vec3 c=vec3(0.004,0.018,0.04)+vec3(0.04,0.9,1.0)*(edge*(0.18+tail*3.0)+ring*0.55+v*tail*0.08);
finish(c);}
