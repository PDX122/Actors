#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Amount;
in vec2 texCoord;
out vec4 fragColor;
float luminance(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
vec3 linearColor(vec3 c){return pow(max(c,vec3(0.0)),vec3(2.2));}
vec3 displayColor(vec3 c){return pow(clamp(c,0.0,1.0),vec3(1.0/2.2));}
float noise(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 sampleLinear(vec2 uv){return linearColor(texture(InSampler,clamp(uv,0.001,0.999)).rgb);}
vec3 glow(vec2 uv){
    vec2 px=1.0/vec2(textureSize(InSampler,0));
    float radius=max(float(textureSize(InSampler,0).y)/1080.0,1.0)*2.5;
    vec3 sum=vec3(0.0);float total=0.0;
    for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++){
        float w=exp(-float(x*x+y*y)*0.45);
        vec3 c=sampleLinear(uv+vec2(x,y)*px*radius);
        sum+=c*smoothstep(0.48,1.05,luminance(c))*w;total+=w;
    }
    return sum/total;
}
vec3 splitTone(vec3 c,vec3 shadow,vec3 highlight,float strength){
    float l=luminance(c);
    vec3 balance=mix(shadow,highlight,smoothstep(0.05,0.75,l));
    return c*mix(vec3(1.0),balance,strength);
}
vec3 finishColor(vec3 c,float grain){
    vec3 color=displayColor(c);
    float n=noise(gl_FragCoord.xy+floor(Time*24.0)*vec2(19.7,7.3))-0.5;
    return clamp(color+n*grain,0.0,1.0);
}


void main(){
vec2 p=1.0/vec2(textureSize(InSampler,0));vec3 c=texture(InSampler,texCoord).rgb;float dx=luminance(texture(InSampler,texCoord+vec2(p.x,0)).rgb)-luminance(texture(InSampler,texCoord-vec2(p.x,0)).rgb);float dy=luminance(texture(InSampler,texCoord+vec2(0,p.y)).rgb)-luminance(texture(InSampler,texCoord-vec2(0,p.y)).rgb);float e=smoothstep(0.015,0.3,length(vec2(dx,dy)));c=c*0.22+e*vec3(0.2,0.85,1.0)*Amount;fragColor=vec4(clamp(c,0.0,1.0),texture(InSampler,texCoord).a);
}
