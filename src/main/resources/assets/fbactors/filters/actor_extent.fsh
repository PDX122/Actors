#version 330 core
uniform sampler2D InSampler;
uniform float FirstPass;
uniform float ReverseDepth;
uniform vec2 InputSize;
out vec4 fragColor;
void main(){
    ivec2 origin=ivec2(gl_FragCoord.xy)*2;
    vec2 lo=vec2(1.0),hi=vec2(0.0);
    for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        ivec2 p=origin+ivec2(x,y);
        if(any(greaterThanEqual(p,ivec2(InputSize))))continue;
        vec4 bounds=texelFetch(InSampler,p,0);
        if(FirstPass>0.5){
            float depth=bounds.r;
            bool valid=ReverseDepth>0.5 ? depth>0.0000001 : depth<0.9999999;
            bounds=valid ? vec4(vec2(p)/InputSize,vec2(p+1)/InputSize) : vec4(1.0,1.0,0.0,0.0);
        }
        lo=min(lo,bounds.xy);hi=max(hi,bounds.zw);
    }
    fragColor=vec4(lo,hi);
}
