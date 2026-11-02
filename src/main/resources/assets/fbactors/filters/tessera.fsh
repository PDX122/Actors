#version 330 core
uniform sampler2D InSampler;
uniform float BlockSize;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec2 size=vec2(textureSize(InSampler,0));
    float block=max(2.0,BlockSize*size.y/1080.0);
    vec2 origin=floor(texCoord*size/block)*block;
    vec3 color=vec3(0.0);
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 p=origin+(vec2(x,y)+0.5)*block/4.0;
        color+=texture(InSampler,clamp(p/size,0.5/size,1.0-0.5/size)).rgb;
    }
    fragColor=vec4(color/16.0,texture(InSampler,texCoord).a);
}
