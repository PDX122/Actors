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
vec2 px=2.0/vec2(textureSize(InSampler,0));vec3 source=sampleAt(texCoord);float v=luma(source);
float edge=length(vec2(luma(sampleAt(texCoord+vec2(px.x,0)))-v,luma(sampleAt(texCoord+vec2(0,px.y)))-v));
vec3 spectrum=0.5+0.5*cos(6.28318*(vec3(0,0.33,0.67)+v*0.8+texCoord.x*0.35-Time*Speed*0.12));
vec3 c=floor(source*5.0)/5.0*0.35+spectrum*(edge*6.0+smoothstep(0.35,0.9,v)*0.55);
finish(c);}
