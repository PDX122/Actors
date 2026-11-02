#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Amount;
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
    float contour=smoothstep(0.025,0.25,edge(texCoord)*Amount);
    vec3 neon=mix(vec3(0.05,0.95,1.0),vec3(1.0,0.08,0.66),smoothstep(0.15,0.75,l));
    c=c*vec3(0.13,0.09,0.22)+neon*contour;
    c+=vec3(0.03,0.008,0.055)*(1.0-contour);
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
