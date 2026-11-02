#version 330 core
uniform sampler2D InSampler;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
uniform float Levels;
uniform float PixelSize;
uniform float Dither;
uniform float Speed;

vec4 at(vec2 uv){return texture(InSampler,clamp(uv,vec2(0.00001),vec2(0.99999)));}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec2 size=vec2(textureSize(InSampler,0));float t=Time*Speed;
    float scale=PixelSize*max(1.0,size.y/720.0);
    vec2 pixel=floor(texCoord*size/scale);
    vec3 color=at((pixel+0.5)*scale/size).rgb;
    float noise=(hash(pixel+floor(t*8.0))-0.5)*Dither;
    float levels=max(2.0,floor(Levels));
    color=floor(clamp(color+noise/(levels-1.0),0.0,1.0)*(levels-1.0)+0.5)/(levels-1.0);
    float scan=step(0.97,fract(pixel.y*0.071+t*0.08));
    color*=1.0-scan*Dither*0.3;
    fragColor=vec4(clamp(color,0.0,1.0),at(texCoord).a);
}
