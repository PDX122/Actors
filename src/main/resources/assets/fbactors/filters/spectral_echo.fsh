#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Amount;
uniform float Speed;
in vec2 texCoord;
out vec4 fragColor;
vec3 sampleAt(vec2 p){return texture(InSampler,clamp(p,vec2(0.001),vec2(0.999))).rgb;}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
void main(){
vec4 original=texture(InSampler,texCoord);
vec2 size=vec2(textureSize(InSampler,0)),px=1.0/size;
float t=Time*Speed;
vec2 p=texCoord-0.5;
float spread=0.025+0.02*sin(t*1.7);
vec2 offset=vec2(cos(t*0.8),sin(t*0.6))*spread;
vec3 a=sampleAt(0.5+p*(1.0+spread*2.0)+offset);
vec3 b=sampleAt(0.5+p*(1.0-spread*2.0)-offset);
vec3 c=original.rgb*0.5+a*vec3(0.0,0.85,1.0)*0.75+b*vec3(1.0,0.05,0.7)*0.75;
fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);
}
