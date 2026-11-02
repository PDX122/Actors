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
vec2 px=1.0/vec2(textureSize(InSampler,0));float t=Time*Speed;
float v=luma(sampleAt(texCoord));
float edge=length(vec2(luma(sampleAt(texCoord+vec2(px.x,0)))-v,luma(sampleAt(texCoord+vec2(0,px.y)))-v));
float scan=0.45+0.55*pow(0.5+0.5*sin(gl_FragCoord.y*1.8+t*3.0),3.0);
float beam=exp(-pow((texCoord.y-fract(t*0.22))/0.02,2.0));
vec3 c=vec3(0.02,0.82,1.0)*(v*scan*1.25+edge*5.0+beam*0.35);
c*=0.9+0.1*sin(t*21.0);finish(c);}
