#version 330 core
uniform sampler2D InSampler;
uniform sampler2D ActorDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D EntityDepthSampler;
uniform float ReverseDepth;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    float actor=texture(ActorDepthSampler,texCoord).r;
    float scene=texture(SceneDepthSampler,texCoord).r;
    float entity=texture(EntityDepthSampler,texCoord).r;
    bool visible=ReverseDepth>0.5 ? actor>0.0000001 && actor>=scene-0.0000001 && actor>=entity
        : actor<0.9999999 && actor<=scene+0.0000001 && actor<=entity;
    fragColor=visible ? texture(InSampler,texCoord) : vec4(0.0);
}
