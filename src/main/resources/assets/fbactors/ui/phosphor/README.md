# Phosphor regular icons

Source: https://github.com/phosphor-icons/core
Revision: 2b75f3ad12b420c9504ef05df8d2564a28f8500e
License: MIT (see LICENSE in this directory).

The original SVG assets are retained beside icons.json. The JSON contains
24-pixel alpha spans generated with tools/build_ui_icons.py using CairoSVG and
Pillow. ImGui draws these spans using its native solid geometry, so the icons
work on OpenGL and Vulkan without changing the font atlas or loading textures.
