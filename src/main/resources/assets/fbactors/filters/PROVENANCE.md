# Actors shader collection

All 32 filters and the grading compositor are original Actors Studio shader implementations. The collection contains no imported Dope shaders. Replay thumbnails use the same programs at reduced resolution. Misc applies shaders at native scene resolution. Most actor looks apply shaders to private material copies before normal lighting. Mosaic uses an original depth-masked screen-space compositor with flat averaged blocks. Shared source textures and non-actor scene pixels are preserved. Synthetic test patterns are used only in GPU verification.
