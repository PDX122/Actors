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
    float cell=max(2.0,Spacing*max(size.y/540.0,0.5));
    vec2 p=texCoord*size/cell;
    vec3 ink=1.0-c;
    mat2 rotate=mat2(0.866,-0.5,0.5,0.866);
    float dc=length(fract(rotate*p)-0.5);
    float dm=length(fract(transpose(rotate)*p)-0.5);
    float dy=length(fract(p)-0.5);
    vec3 dots=vec3(1.0-smoothstep(sqrt(ink.r)*0.58-0.045,sqrt(ink.r)*0.58+0.045,dc),1.0-smoothstep(sqrt(ink.g)*0.58-0.045,sqrt(ink.g)*0.58+0.045,dm),1.0-smoothstep(sqrt(ink.b)*0.58-0.045,sqrt(ink.b)*0.58+0.045,dy));
    c=(1.0-dots*0.91)*vec3(1.0,0.96,0.86);
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
