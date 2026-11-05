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
vec2 p=texCoord*vec2(size.x/size.y,1.0)*22.0;
vec2 scale=vec2(1.7320508,3.0);
vec2 a=mod(p,scale)-scale*0.5,b=mod(p-scale*0.5,scale)-scale*0.5;
vec2 h=dot(a,a)<dot(b,b)?a:b;
float d=max(abs(h.x)*0.8660254+abs(h.y)*0.5,abs(h.x));
float rim=smoothstep(0.78,0.866,d);
float pulse=0.5+0.5*sin(length(p)*0.55-t*3.0);
vec3 c=original.rgb*vec3(0.55,0.75,0.9)+vec3(0.05,0.7,1.0)*rim*(0.3+0.7*pulse);
fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);
}
