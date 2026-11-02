package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MorphPreviewIsolationTest {
    private static final List<String> COMPATS = List.of(
        "mc1211", "mc1214", "mc1215", "mc1218", "mc12110",
        "mc12111", "mc261", "mc262", "mc263");

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("settings.gradle"))) return dir;
            dir = dir.getParent();
        }
        fail("Could not locate repo root (no settings.gradle above user.dir)");
        throw new AssertionError();
    }

    private static String read(Path root, String relative) {
        try {
            return Files.readString(root.resolve(relative));
        } catch (IOException e) {
            fail("Could not read " + relative, e);
            throw new AssertionError();
        }
    }

    private static String methodBody(String src, String anchor) {
        int start = src.indexOf(anchor);
        assertTrue(start >= 0, "Expected to find " + anchor);
        int brace = src.indexOf('{', start);
        assertTrue(brace > start, "Expected body for " + anchor);
        int depth = 0;
        for (int i = brace; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return stripComments(src.substring(brace, i + 1));
            }
        }
        fail("Unbalanced braces in " + anchor);
        throw new AssertionError();
    }

    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) out.append(src.charAt(++i));
                else if (c == '"') inString = false;
            } else if (inChar) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) out.append(src.charAt(++i));
                else if (c == '\'') inChar = false;
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '\'') {
                inChar = true;
                out.append(c);
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
                out.append('\n');
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i++;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @Test void animatorUsesPreviewOnlySwing() {
        Path root = repoRoot();
        String animator = stripComments(read(root, "src/main/java/pdx/dev/fbactors/morph/MorphPreviewAnimator.java"));
        assertTrue(animator.contains("Compat.previewSwing("),
            "Animator must start attack swings via Compat.previewSwing (local-only)");
        assertFalse(animator.contains("Compat.swing("),
            "Animator must not call Compat.swing (ServerLevel broadcast path)");
    }

    @Test void previewSwingNeverBroadcasts() {
        Path root = repoRoot();
        for (String compat : COMPATS) {
            String src = read(root, "compat/" + compat + "/java/pdx/dev/fbactors/compat/Compat.java");
            String body = methodBody(src, "public static void previewSwing(");
            for (String leak : List.of("broadcast", "Serverbound", "Clientbound", "gameEvent", ".send(", "addEntity", "playSound")) {
                assertFalse(body.contains(leak),
                    () -> compat + ".previewSwing must stay local-only but mentions " + leak);
            }
            assertTrue(body.contains("false"),
                () -> compat + ".previewSwing must pass updateSelf=false");
        }
    }

    @Test void worldSwingPathIsPreserved() {

        Path root = repoRoot();
        for (String compat : COMPATS) {
            String src = read(root, "compat/" + compat + "/java/pdx/dev/fbactors/compat/Compat.java");
            assertTrue(src.contains("private static void mirrorSwing("),
                () -> compat + " lost mirrorSwing");
            String mirror = methodBody(src, "private static void mirrorSwing(");
            assertTrue(mirror.contains("swing("), () -> compat + ".mirrorSwing must keep using Compat.swing");
            assertFalse(mirror.contains("previewSwing("),
                () -> compat + ".mirrorSwing must not use the preview-only swing");
        }
    }

    @Test void previewEntityGetsFreshIdentityAndIsNeverAdded() {
        Path root = repoRoot();
        for (String compat : COMPATS) {
            String src = read(root, "compat/" + compat + "/java/pdx/dev/fbactors/compat/Compat.java");
            String body = methodBody(src, "createMorphPreview(String id)");
            assertTrue(body.contains("setUUID(UUID.randomUUID())"),
                () -> compat + ".createMorphPreview must assign a fresh random UUID");
            for (String leak : List.of("addEntity", "addFreshEntity", "broadcast", "gameEvent")) {
                assertFalse(body.contains(leak),
                    () -> compat + ".createMorphPreview must not touch the level but mentions " + leak);
            }
        }
    }

    @Test void legacyPreviewRendersIntoPrivateBuffers() {

        Path root = repoRoot();
        for (String compat : List.of("mc1211", "mc1214", "mc1215")) {
            String src = stripComments(read(root,
                "compat/" + compat + "/java/pdx/dev/fbactors/morph/MorphPreviewBridge.java"));
            assertTrue(src.contains("MultiBufferSource.immediate("),
                () -> compat + " must build a private BufferSource");
            assertTrue(src.contains("GuiGraphics(client, previewBuffers)"),
                () -> compat + " must render into the private buffers");
            assertTrue(src.contains("dispatch.cameraOrientation()")
                    && src.contains("dispatch.overrideCameraOrientation(savedOrientation)"),
                () -> compat + " must snapshot/restore the shared dispatcher orientation");
            assertTrue(src.contains("dispatch.setRenderShadow(true)"),
                () -> compat + " must restore the shadow flag vanilla clears");
            assertFalse(src.contains("renderBuffers().bufferSource()"),
                () -> compat + " must not touch the world's bufferSource");
            String close = methodBody(src, "public void close()");
            assertTrue(close.contains("previewBuilder.close()"),
                () -> compat + " must release the private native buffer on close");
        }
    }

    @Test void directPipPreviewOwnsDispatcherAndBuffers() {

        Path root = repoRoot();
        String src = stripComments(read(root,
            "compat/mc1218/java/pdx/dev/fbactors/morph/MorphPreviewBridge.java"));
        assertTrue(src.contains("EntityRenderDispatcher("),
            "mc1218 must build a private EntityRenderDispatcher");
        assertTrue(src.contains("MultiBufferSource.immediate("),
            "mc1218 must build a private BufferSource");
        assertTrue(src.contains("GuiEntityRenderer(previewBuffers, dispatch)"),
            "mc1218 must wire the renderer to the private pipeline");
        assertFalse(src.contains("GuiEntityRenderer(client.renderBuffers()"),
            "mc1218 must not hand the renderer the world's buffers");
        String close = methodBody(src, "public void close()");
        assertTrue(close.contains("previewBuilder.close()"),
            "mc1218 must release the private native buffer on close");
    }

    @Test void sharedStoragePreviewOwnsEntirePipeline() {

        Path root = repoRoot();
        for (String compat : List.of("mc12110", "mc12111", "mc261")) {
            String bridge = stripComments(read(root,
                "compat/" + compat + "/java/pdx/dev/fbactors/morph/MorphPreviewBridge.java"));
            assertTrue(bridge.contains("PreviewEntityRenderer.create(client)"),
                () -> compat + " must build the isolated renderer");
            assertTrue(bridge.contains("renderer.endFrame()"),
                () -> compat + " must drain the private storage after prepare");
            assertFalse(bridge.contains("renderBuffers().bufferSource()")
                    || bridge.contains("getEntityRenderDispatcher()"),
                () -> compat + " must not share world buffers or dispatcher");
            String sub = stripComments(read(root,
                "compat/" + compat + "/java/pdx/dev/fbactors/morph/PreviewEntityRenderer.java"));
            assertTrue(sub.contains("new EntityRenderDispatcher("),
                () -> compat + " must build a private EntityRenderDispatcher");
            assertTrue(sub.contains("new SubmitNodeStorage()"),
                () -> compat + " must build a private SubmitNodeStorage");
            assertTrue(sub.contains("new FeatureRenderDispatcher("),
                () -> compat + " must build a private FeatureRenderDispatcher");
            assertTrue(sub.contains("MultiBufferSource.immediate("),
                () -> compat + " must build private buffers");
            assertFalse(sub.contains("getSubmitNodeStorage()"),
                () -> compat + " must never touch the shared storage");
            String close = methodBody(sub, "public void close()");
            assertTrue(close.contains("features.close()"),
                () -> compat + " must dispose the private feature dispatcher");
        }
    }

    @Test void ownedStoragePreviewOwnsDispatcher() {

        Path root = repoRoot();
        for (String compat : List.of("mc262", "mc263")) {
            String src = stripComments(read(root,
                "compat/" + compat + "/java/pdx/dev/fbactors/morph/MorphPreviewBridge.java"));
            assertTrue(src.contains("EntityRenderDispatcher("),
                () -> compat + " must build a private EntityRenderDispatcher");
            assertTrue(src.contains("GuiEntityRenderer(dispatch)"),
                () -> compat + " must wire the renderer to the private dispatcher");
            assertFalse(src.contains("getEntityRenderDispatcher()"),
                () -> compat + " must not share the world's dispatcher");
        }
    }

    @Test void previewVariantsStayLocal() {        Path root = repoRoot();
        for (String compat : COMPATS) {
            String src = read(root, "compat/" + compat + "/java/pdx/dev/fbactors/morph/MorphPreviewVariants.java");
            String body = methodBody(src, "public static void apply(");
            assertFalse(body.contains("setSize(size, true)"),
                () -> compat + ".MorphPreviewVariants must not heal-flag slime resizes");
            for (String leak : List.of("broadcast", "gameEvent", "addEntity", "playSound", "Serverbound", "setUUID")) {
                assertFalse(body.contains(leak),
                    () -> compat + ".MorphPreviewVariants.apply must stay local but mentions " + leak);
            }
        }
    }
    @Test void stagedPreviewNeverReusesWorldPreparedFrameOrVertexBuffers() {
        Path root = repoRoot();
        for (String compat : List.of("mc262", "mc263")) {
            String bridge = stripComments(read(root,
                "compat/" + compat + "/java/pdx/dev/fbactors/morph/MorphPreviewBridge.java"));
            assertFalse(bridge.contains("gameRenderer.featureRenderDispatcher()"),
                () -> compat + " must not overwrite the world's reusable PreparedFrame");
            assertTrue(bridge.contains("new net.minecraft.client.renderer.RenderBuffers(0)"),
                () -> compat + " must own the staged vertex buffers used by preview features");
            assertTrue(bridge.contains("new net.minecraft.client.renderer.feature.FeatureRenderDispatcher("));
            assertTrue(bridge.contains("renderer.prepare(draw, output, previewFeatures, 1)"));
            String render = methodBody(bridge, "MorphThumbnail render(");
            assertTrue(render.contains("finally {") && render.contains("previewBuffers.endFrame()"));
            String close = methodBody(bridge, "public void close()");
            assertTrue(close.contains("previewFeatures.close()") && close.contains("previewBuffers.close()"));
        }
    }
}
