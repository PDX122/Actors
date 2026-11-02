#version 330 core
uniform sampler2D InSampler;
uniform float Amount;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float luminance(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
void main(){
    vec4 source=texture(InSampler,texCoord); vec3 c=source.rgb; float l=luminance(c);
    vec3 cool=mix(vec3(l),c,0.68)*vec3(0.56,0.74,1.06);
    cool=pow(max(cool,vec3(0.0)),vec3(1.12));
    float warm=clamp((c.r-max(c.g,c.b))*3.5,0.0,1.0)*smoothstep(0.4,0.95,max(c.r,max(c.g,c.b)));
    c=mix(cool,c*vec3(1.0,0.93,0.82),warm*0.85);
    fragColor=vec4(mix(source.rgb,clamp(c,0.0,1.0),clamp(Amount,0.0,1.0)),source.a);
}
