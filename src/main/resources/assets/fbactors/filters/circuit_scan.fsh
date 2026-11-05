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
vec2 grid=texCoord*vec2(48.0,27.0),cell=floor(grid),local=fract(grid);
float v=luma(original.rgb);
float edge=length(vec2(luma(sampleAt(texCoord+vec2(px.x,0)))-v,luma(sampleAt(texCoord+vec2(0,px.y)))-v));
float wire=1.0-smoothstep(0.025,0.07,min(abs(local.x-0.5),abs(local.y-0.5)));
float packet=pow(0.5+0.5*sin(cell.x*0.7+cell.y*1.3-t*4.0),16.0);
vec3 c=original.rgb*0.16+vec3(0.03,0.85,1.0)*(edge*6.0+wire*(0.1+packet*0.9))+vec3(0.4,1.0,0.2)*packet*wire*0.35;
fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);
}
