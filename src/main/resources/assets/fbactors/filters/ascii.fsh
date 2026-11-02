#version 330 core
uniform sampler2D InSampler;
uniform float Amount;
uniform float GlyphSize;
uniform float ColorMix;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec4 original=texture(InSampler,texCoord);
    vec2 size=vec2(textureSize(InSampler,0));
    vec2 cell=vec2(0.75,1.0)*GlyphSize*max(1.0,size.y/1080.0);
    vec2 index=floor(texCoord*size/cell);
    vec2 local=fract(texCoord*size/cell);

    vec3 source=vec3(0.0);float weight=0.0;
    for(int y=0;y<3;y++)for(int x=0;x<3;x++){
        vec2 uv=clamp((index+(vec2(x,y)+0.5)/3.0)*cell/size,0.5/size,1.0-0.5/size);
        vec4 sampleColor=texture(InSampler,uv);
        source+=sampleColor.rgb*sampleColor.a;weight+=sampleColor.a;
    }
    source/=max(weight,0.0001);
    float value=dot(source,vec3(0.2126,0.7152,0.0722));
    const uvec2 glyphs[10]=uvec2[10](uvec2(0u,0u),uvec2(402653184u,3u),uvec2(415248768u,0u),uvec2(32537600u,0u),uvec2(139432064u,0u),uvec2(720353952u,0u),uvec2(2515893578u,2u),uvec2(416354675u,0u),uvec2(2212165166u,7u),uvec2(4294967295u,7u));
    ivec2 p=ivec2(floor(local*vec2(6.0,8.0)));
    float ink=0.0;
    if(p.x<5&&p.y<7){
        uint bit=uint((6-p.y)*5+p.x);
        uvec2 glyph=glyphs[int(clamp(value*10.0,0.0,9.0))];
        ink=float(((bit<32u?glyph.x:glyph.y)>>(bit%32u))&1u);
    }
    vec3 tint=mix(vec3(0.15,1.0,0.6),source/max(max(source.r,source.g),max(source.b,0.01)),ColorMix);
    vec3 terminal=vec3(0.005,0.012,0.009)+tint*ink*(0.5+value);
    fragColor=vec4(mix(original.rgb,clamp(terminal,0.0,1.0),Amount),original.a);
}
