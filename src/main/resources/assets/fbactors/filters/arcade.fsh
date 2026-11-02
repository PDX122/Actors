#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float PixelSize;
in vec2 texCoord;
out vec4 fragColor;
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
vec3 sampleColor(vec2 uv){vec2 px=0.5/vec2(textureSize(InSampler,0));return texture(InSampler,clamp(uv,px,1.0-px)).rgb;}
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float edge(vec2 uv){vec2 px=1.0/vec2(textureSize(InSampler,0));float x=luma(sampleColor(uv+vec2(px.x,0)))-luma(sampleColor(uv-vec2(px.x,0)));float y=luma(sampleColor(uv+vec2(0,px.y)))-luma(sampleColor(uv-vec2(0,px.y)));return length(vec2(x,y));}
void main(){
    vec4 original=texture(InSampler,texCoord);
    vec3 c=clamp(original.rgb,0.0,1.0);
    float l=luma(c);
    vec2 size=vec2(textureSize(InSampler,0));
    float block=max(1.0,PixelSize*max(size.y/540.0,0.5));
    vec2 cell=floor(texCoord*size/block);
    float tone=luma(sampleColor((cell+0.5)*block/size));
    int x=int(mod(cell.x,4.0)),y=int(mod(cell.y,4.0));
    int ranks[16]=int[16](0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5);
    tone=clamp(tone+(float(ranks[y*4+x])/16.0-0.5)*0.22,0.0,0.999);
    vec3 palette[4]=vec3[4](vec3(0.055,0.11,0.15),vec3(0.18,0.32,0.31),vec3(0.53,0.64,0.39),vec3(0.92,0.9,0.63));
    c=palette[int(floor(tone*4.0))];
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
