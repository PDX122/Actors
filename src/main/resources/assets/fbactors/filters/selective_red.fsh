#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Tolerance;
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
    float red=c.r-max(c.g,c.b);
    float keep=smoothstep(0.025,max(0.04,Tolerance),red);
    float mono=pow(max(l,0.0),1.13);
    vec3 accent=mix(vec3(l),c,1.45)*vec3(1.13,0.96,0.91);
    c=mix(vec3(mono)*vec3(0.96,0.99,1.03),accent,keep);
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
