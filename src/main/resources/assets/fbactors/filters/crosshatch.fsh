#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Spacing;
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
    float cell=max(3.0,Spacing*max(size.y/540.0,0.5));
    vec2 p=texCoord*size/cell;
    float a=1.0-smoothstep(0.08,0.23,abs(fract(p.x+p.y)-0.5));
    float b=1.0-smoothstep(0.08,0.23,abs(fract(p.x-p.y)-0.5));
    float d=1.0-smoothstep(0.08,0.23,abs(fract(p.x*1.7+p.y*0.25)-0.5));
    float ink=max(a*(1.0-smoothstep(0.18,0.8,l)),max(b*(1.0-smoothstep(0.05,0.5,l)),d*(1.0-smoothstep(0.0,0.22,l))));
    ink=max(ink,smoothstep(0.05,0.28,edge(texCoord))*0.8);
    c=mix(vec3(0.97,0.93,0.82),vec3(0.07,0.055,0.1),ink);
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
