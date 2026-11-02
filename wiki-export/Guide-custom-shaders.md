# Custom Looks shaders

Open **Effects → Looks** and select **Import shader**. Choose a `.fsh`, `.frag`
or `.glsl` file, then give it a name and description. Import compiles the shader
on the active graphics backend before saving it. Compiler errors stay in the
dialog, and a failed import does not create a gallery entry.

The gallery shows imported shaders under **Custom**. Select one to preview it
on the demo photo. Choose **Whole scene** or an actor and press **Add effect**,
or drag the shader onto a timeline track. Imported actor shaders process the
actor's rendered image and use the same occlusion checks as bundled effects.

Use a standalone GLSL 330 fragment shader with these declarations:

```glsl
#version 330 core
uniform sampler2D InSampler;
uniform vec2 InputSize;
uniform float Time;
uniform float Amount;
uniform float Speed;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 original = texture(InSampler, texCoord);
    fragColor = vec4(mix(original.rgb, 1.0 - original.rgb, Amount), original.a);
}
```

Preserve alpha to keep actor silhouettes clean. Additional textures, uniform
types, includes, storage buffers, shader-pack ZIPs and shader extensions are
not part of this importer. Files are limited to 64 KiB and the library holds
128 imports. The bundled example is
`assets/fbactors/filters/examples/custom-negative.fsh` inside the mod jar.

Imports are copied into `flashback/fbactors/shaders/library.json`, so their
original file is no longer required. A selected import's photo preview runs
through its actual shader on OpenGL or Minecraft 26.3 native Vulkan.

**Delete shader** removes an import and its code. Bundled shaders also have a
Delete action, with this confirmation: **You will have to reinstall the mod to
get it back.** Their deletion is recorded for the installed jar; replacing the
mod jar restores bundled entries. Custom imports survive that replacement.
Deleted shaders stop contributing to saved effects when those effects load or
evaluate. Remaining filters and grading settings are retained.


`InputSize` contains the current input dimensions and `Time` is replay time in seconds. Optional `Amount` and `Speed` uniforms add Strength and Animation speed controls, both defaulting to 1.

## Backend support

Custom shader imports depend on the active Minecraft rendering backend. The current 26.2 bridge disables custom imports; opening the import dialog does not establish support. The 26.3 native Vulkan path and supported OpenGL paths have separate validation. If compilation is refused, retain the source file and use a supported build rather than assuming it imported successfully.

