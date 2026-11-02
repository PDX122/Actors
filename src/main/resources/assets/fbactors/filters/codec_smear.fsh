#version 330 core
uniform sampler2D InSampler;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
uniform float Damage;
uniform float Drift;
uniform float Speed;

vec4 at(vec2 uv){return texture(InSampler,clamp(uv,vec2(0.00001),vec2(0.99999)));}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec2 size=vec2(textureSize(InSampler,0));float t=Time*Speed,frame=floor(t*8.0);
    vec2 block=floor(texCoord*size/vec2(32,16));
    float failure=step(1.0-Damage,hash(block+vec2(frame,frame*0.17)));
    float direction=hash(vec2(block.y,floor(t*2.0)))*2.0-1.0;
    vec2 offset=vec2(direction*48.0*Drift,floor(sin(block.x+frame)*2.0))*failure/size;
    vec3 color=at(texCoord+offset).rgb;
    for(int i=1;i<=4;i++)color+=at(texCoord+offset+vec2(float(i)*direction*6.0*Drift/size.x,0)).rgb*failure*0.45;
    color/=1.0+failure*1.8;
    vec2 chromaUV=(floor((texCoord+offset)*size/8.0)+0.5)*8.0/size;
    vec3 coarse=at(chromaUV).rgb;float value=luma(color);
    color=mix(color,coarse-vec3(luma(coarse))+vec3(value),failure*0.45);
    fragColor=vec4(clamp(color,0.0,1.0),at(texCoord).a);
}
