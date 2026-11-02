#version 330 core
uniform sampler2D InSampler;
uniform float Amount;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float luminance(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
uniform float FilmGrain;
void main(){
    vec4 source=texture(InSampler,texCoord); vec3 c=source.rgb;
    float l=luminance(c); c=mix(vec3(l),c,0.88);
    c=mix(c,c*c*(3.0-2.0*c),0.28);
    float hi=smoothstep(0.25,0.95,l);
    c*=mix(vec3(0.97,1.0,1.035),vec3(1.055,1.015,0.955),hi);
    c=c*0.955+vec3(0.018,0.017,0.02);
    float frame=floor(Time*24.0);
    float n=fract(sin(dot(gl_FragCoord.xy+frame*vec2(19.7,7.3),vec2(127.1,311.7)))*43758.5453)-0.5;
    c+=n*FilmGrain;
    fragColor=vec4(mix(source.rgb,clamp(c,0.0,1.0),clamp(Amount,0.0,1.0)),source.a);
}
