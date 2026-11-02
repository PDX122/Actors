#version 330 core
uniform sampler2D InSampler;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
uniform float Damage;
uniform float BlockSize;
uniform float Speed;

vec4 at(vec2 uv){return texture(InSampler,clamp(uv,vec2(0.00001),vec2(0.99999)));}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec2 size=vec2(textureSize(InSampler,0));float t=Time*Speed,frame=floor(t*12.0);
    float scale=BlockSize*max(1.0,size.y/720.0);
    vec2 block=floor(texCoord*size/scale);
    float dropout=step(1.0-Damage*0.75,hash(block+vec2(floor(t*3.0),17.0)));
    vec3 original=at(texCoord).rgb;
    vec2 q=(floor(texCoord*size/3.0)+0.5)*3.0/size;
    vec3 color=at(q).rgb;
    float noise=hash(floor(texCoord*size)+frame*11.0);
    float band=step(0.91,hash(vec2(floor(texCoord.y*size.y/4.0),frame)));
    vec3 corrupt=vec3(floor(color.r*3.0)/3.0,floor(color.g*2.0)/2.0,floor(color.b*4.0)/4.0);
    corrupt=mix(corrupt,vec3(noise*0.7,noise*0.12,noise),0.45);
    color=mix(original,corrupt,dropout);
    color=mix(color,vec3(noise),band*Damage*0.8);
    fragColor=vec4(clamp(color,0.0,1.0),at(texCoord).a);
}
