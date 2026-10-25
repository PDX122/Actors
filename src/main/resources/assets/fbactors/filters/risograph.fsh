#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Misregister;
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
    vec2 px=1.0/size;
    float shifted=luma(sampleColor(texCoord+vec2(Misregister,0.6*Misregister)*px));
    vec3 paper=vec3(1.0,0.93,0.78);
    float cool=1.0-smoothstep(0.12,0.83,l);
    float warm=smoothstep(0.25,0.75,shifted)*(0.4+0.6*max(c.r-c.b,0.0));
    float grain=hash(floor(texCoord*size*0.65));
    float screen=0.78+0.22*step(0.45,fract((gl_FragCoord.x+gl_FragCoord.y)/3.0));
    c=paper*mix(vec3(1.0),vec3(0.16,0.19,0.48),cool*screen);
    c*=mix(vec3(1.0),vec3(1.0,0.3,0.13),warm*0.85);
    c+=(grain-0.5)*0.075;
    fragColor=vec4(clamp(c,0.0,1.0),original.a);
}
