#version 330 core
uniform sampler2D InSampler;
uniform float Amount;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float luminance(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
void main(){
    vec4 source=texture(InSampler,texCoord);
    float l=luminance(source.rgb);
    vec3 c=vec3(smoothstep(0.055,0.92,l));
    fragColor=vec4(mix(source.rgb,c,clamp(Amount,0.0,1.0)),source.a);
}
