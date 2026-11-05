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
float aspect=size.x/size.y;
vec2 p=(texCoord-0.5)*vec2(aspect,1.0);
float r=length(p),wedge=1.04719755;
float angle=abs(mod(atan(p.y,p.x)+t*0.22,wedge)-wedge*0.5);
vec2 uv=0.5+vec2(cos(angle),sin(angle))*r/vec2(aspect,1.0);
vec3 c=sampleAt(uv);
c.r=sampleAt(uv+vec2(0.008*sin(t+r*8.0),0)).r;
c.b=sampleAt(uv-vec2(0.008*sin(t+r*8.0),0)).b;
fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);
}
