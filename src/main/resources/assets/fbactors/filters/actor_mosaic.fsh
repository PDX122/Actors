#version 330 core
uniform sampler2D InSampler;
uniform sampler2D ActorDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D ExtentSampler;
uniform float BlockSize;
uniform float Intensity;
uniform float ReverseDepth;
in vec2 texCoord;
out vec4 fragColor;
float visible(vec2 uv){
    float actor=texture(ActorDepthSampler,uv).r;
    float scene=texture(SceneDepthSampler,uv).r;
    if(ReverseDepth>0.5)return actor>0.0000001 && actor>=scene-0.0000001 ? 1.0 : 0.0;
    return actor<0.9999999 && actor<=scene+0.0000001 ? 1.0 : 0.0;
}
void main(){
    vec4 original=texture(InSampler,texCoord);
    if(visible(texCoord)<0.5){discard;}
    vec2 size=vec2(textureSize(InSampler,0));
    vec4 extent=texelFetch(ExtentSampler,ivec2(0),0);
    float actorHeight=max(1.0,(extent.w-extent.y)*size.y);
    float block=max(2.0,BlockSize*min(size.y,actorHeight*2.0)/1080.0);
    vec2 anchor=extent.xy*size;
    vec2 origin=anchor+floor((texCoord*size-anchor)/block)*block;
    vec3 color=vec3(0.0);float count=0.0;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 uv=clamp((origin+(vec2(x,y)+0.5)*block/4.0)/size,0.5/size,1.0-0.5/size);
        float weight=visible(uv);color+=texture(InSampler,uv).rgb*weight;count+=weight;
    }
    vec3 mosaic=count>0.0 ? color/count : original.rgb;
    fragColor=vec4(mix(original.rgb,mosaic,Intensity),original.a);
}
