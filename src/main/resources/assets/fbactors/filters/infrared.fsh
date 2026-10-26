#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Foliage;
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
    float vegetation=smoothstep(-0.035,0.14,c.g-max(c.r,c.b)*0.92);
    vec3 cool=vec3(c.b*0.45+c.r*0.16,c.b*0.85+c.g*0.14,c.g*0.84+c.b*0.2);
    vec3 hot=vec3(0.5+l*0.8,l*0.23+0.035,0.22+l*0.45);
    c=mix(cool,hot,vegetation*Foliage);
    c=mix(c,vec3(1.0,0.9,0.85),smoothstep(0.76,1.0,l)*0.65);
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
