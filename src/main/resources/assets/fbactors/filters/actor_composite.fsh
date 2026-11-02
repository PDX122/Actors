#version 330 core
uniform sampler2D InSampler;
uniform sampler2D OriginalSampler;
uniform sampler2D ActorDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D EntityDepthSampler;
uniform float ReverseDepth;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec4 original=texture(OriginalSampler,texCoord);
    float actor=texture(ActorDepthSampler,texCoord).r;
    float scene=texture(SceneDepthSampler,texCoord).r;
    float entity=texture(EntityDepthSampler,texCoord).r;
    bool visible=ReverseDepth>0.5 ? actor>0.0000001 && actor>=scene-0.0000001 && actor>=entity
        : actor<0.9999999 && actor<=scene+0.0000001 && actor<=entity;
    if(!visible){discard;}
    fragColor=vec4(texture(InSampler,texCoord).rgb,original.a);
}
