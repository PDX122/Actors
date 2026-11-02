#version 330 core
uniform sampler2D InSampler;
uniform float Amount;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float luminance(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
void main(){
    vec4 source=texture(InSampler,texCoord); float l=luminance(source.rgb);
    vec3 c=mix(vec3(l),source.rgb,0.72);
    c=pow(max(c,vec3(0.0)),vec3(1.08));
    c*=mix(vec3(0.89,1.025,1.07),vec3(1.10,1.025,0.86),smoothstep(0.15,0.85,l));
    c=c/(vec3(1.0)+c*0.06)*1.04;
    fragColor=vec4(mix(source.rgb,clamp(c,0.0,1.0),clamp(Amount,0.0,1.0)),source.a);
}
