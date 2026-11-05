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
vec2 p=(texCoord-0.5)*vec2(size.x/size.y,1.0);
float v=luma(original.rgb);
float field=sin(p.x*15.0+t*1.7)+sin(p.y*19.0-t*1.3)+sin(length(p)*28.0-t*2.0);
vec3 palette=0.5+0.5*cos(vec3(0.0,2.1,4.2)+field*1.7+v*4.0);
float edge=length(vec2(luma(sampleAt(texCoord+vec2(px.x,0)))-v,luma(sampleAt(texCoord+vec2(0,px.y)))-v));
vec3 c=palette*(0.18+v*0.85)+vec3(0.1,0.8,1.0)*edge*4.0;
fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);
}
