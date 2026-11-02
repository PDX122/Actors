#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Gain;
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
    float signal=pow(clamp(l*Gain,0.0,1.0),0.65);
    float scan=0.93+0.07*cos(gl_FragCoord.y*3.14159265);
    c=mix(vec3(0.005,0.025,0.008),vec3(0.45,1.0,0.12),signal)*scan;
    c+=(hash(gl_FragCoord.xy+floor(Time*12.0))-0.5)*0.025;
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
