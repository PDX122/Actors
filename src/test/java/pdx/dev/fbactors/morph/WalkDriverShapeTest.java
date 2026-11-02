package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.WalkAnimationState;

import org.junit.jupiter.api.Test;


class WalkDriverShapeTest {

    @Test
    void resolvesInDeclarationOrder() throws Exception {
        WalkAnimationState state = new WalkAnimationState();
        WalkDriver.Fields fields = WalkDriver.resolveByShape(state);
        assertNotNull(fields, "the live class shape must match");
        assertEquals("speedOld", fields.speedOld().getName());
        assertEquals("speed", fields.speed().getName());
        assertEquals("position", fields.position().getName());
        if (fields.positionScale() != null) {
            assertEquals("positionScale", fields.positionScale().getName());
        }
    }

    @Test
    void matchedFieldsDrive() throws Exception {
        WalkAnimationState state = new WalkAnimationState();
        WalkDriver.Fields fields = WalkDriver.resolveByShape(state);
        assertNotNull(fields);
        fields.speedOld().setFloat(state, 0.5f);
        fields.speed().setFloat(state, 0.5f);
        fields.position().setFloat(state, fields.position().getFloat(state) + 0.5f);
        assertEquals(0.5f, state.speed(), 0.0001f);
        assertEquals(0.5f, state.position(), 0.0001f);
    }
}

