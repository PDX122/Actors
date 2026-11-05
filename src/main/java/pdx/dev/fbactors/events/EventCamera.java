package pdx.dev.fbactors.events;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public record EventCamera(Vec3 position, Quaternionf rotation, Matrix4f projection) {}
