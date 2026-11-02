#version 330 core
uniform sampler2D InSampler;
uniform vec2 InputSize;
uniform float Time;
uniform float Amount;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 original = texture(InSampler, texCoord);
    fragColor = vec4(mix(original.rgb, 1.0 - original.rgb, Amount), original.a);
}
