#version 330 core
uniform sampler2D InSampler;
uniform sampler2D EventSampler;
uniform sampler2D SceneDepthSampler;
uniform float ResolveX;
uniform float ResolveY;
uniform float DepthA;
uniform float DepthB;
uniform float ReverseDepth;
in vec2 texCoord;
out vec4 fragColor;
float depth(vec2 uv) {
    float raw=texture(SceneDepthSampler,uv).r;
    if((ReverseDepth>.5 && raw<.000001) || (ReverseDepth<.5 && raw>.999999))return 100000.;
    return abs(DepthB/((ReverseDepth>.5?raw:raw*2.-1.)+DepthA));
}
void main() {
    vec2 scale=vec2(ResolveX,ResolveY),size=vec2(textureSize(EventSampler,0))*scale;
    vec2 p=texCoord*size-.5,base=floor(p),f=fract(p);
    float z=depth(texCoord),total=0.;vec3 delta=vec3(0);
    for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
        vec2 uv=clamp((base+vec2(x,y)+.5)/size,.5/size,1.-.5/size);
        float weight=(x==0?1.-f.x:f.x)*(y==0?1.-f.y:f.y);
        weight*=exp(-abs(depth(uv)-z)/max(.2,z*.015));
        delta+=texture(EventSampler,uv*scale).rgb*weight;total+=weight;
    }
    vec4 original=texture(InSampler,texCoord);
    fragColor=vec4(max(vec3(0),original.rgb+delta/max(.00001,total)),original.a);
}
