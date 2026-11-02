# Actors shader collection

All 60 filters and the grading compositor are original Actors Studio shader implementations. The collection contains no imported Dope shaders. Replay thumbnails use the same programs at reduced resolution. Misc applies shaders at native scene resolution. Most actor looks apply shaders to private material copies before normal lighting. Mosaic uses an original depth-masked screen-space compositor with flat averaged blocks. Shared source textures and non-actor scene pixels are preserved. Synthetic test patterns are used only in GPU verification.

35mm, Noir, Desert Chrome and Moonlight are original Actors color grades. Their GPU shaders and CPU material equivalents share the same color transforms. No external film LUTs are bundled.
