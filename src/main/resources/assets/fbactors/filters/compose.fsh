#version 330 core
uniform sampler2D InSampler;
uniform sampler2D OriginalSampler;
uniform float Intensity;
uniform float Exposure;
uniform float Contrast;
uniform float Saturation;
uniform float Temperature;
uniform float Tint;
uniform float Vignette;
uniform float Grain;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec4 original=texture(OriginalSampler,texCoord);
    vec3 c=texture(InSampler,texCoord).rgb;
    c*=exp2(Exposure);
    c*=vec3(1.0+Temperature*0.12+Tint*0.04,1.0-Tint*0.08,1.0-Temperature*0.12+Tint*0.04);
    float l=dot(c,vec3(0.2126,0.7152,0.0722));
    c=mix(vec3(l),c,Saturation);
    c=(c-0.5)*Contrast+0.5;
    vec2 p=texCoord*2.0-1.0;
    c*=1.0-Vignette*smoothstep(0.25,1.5,dot(p,p));
    c+=(hash(gl_FragCoord.xy+floor(Time*24.0)*vec2(17.0,7.0))-0.5)*Grain;
    fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Intensity),original.a);
}
