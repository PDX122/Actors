package pdx.dev.fbactors.morph;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;


public final class WalkDriver {
    private WalkDriver() {
    }

    private static final String OWNER = "net/minecraft/world/entity/WalkAnimationState";


    record Fields(Field speedOld, Field speed, Field position, Field positionScale) {
    }

    private static volatile Fields fields;
    private static volatile boolean fieldsTried;
    private static volatile boolean pathLogged;

    private record UpdateFallback(Method threeArg, Method twoArg) {
    }

    private static volatile UpdateFallback fallback;
    private static volatile boolean fallbackTried;


    public static void drive(LivingEntity mob, float target) {
        Fields known = fieldsFor(mob);
        if (known != null) {
            try {
                Object state = mob.walkAnimation;
                known.speedOld.setFloat(state, target);
                known.speed.setFloat(state, target);
                known.position.setFloat(state, known.position.getFloat(state) + target);
                if (known.positionScale != null) {
                    known.positionScale.setFloat(state, 1.0F);
                }
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {

            }
        }
        driveViaUpdate(mob, target);
    }


    public static void driveTo(LivingEntity mob, float speedOld, float speed, float position) {
        Fields known = fieldsFor(mob);
        if (known != null) {
            try {
                Object state = mob.walkAnimation;
                known.speedOld.setFloat(state, speedOld);
                known.speed.setFloat(state, speed);
                known.position.setFloat(state, position);
                if (known.positionScale != null) {
                    known.positionScale.setFloat(state, 1.0F);
                }
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {

            }
        }
        driveViaUpdate(mob, speed);
    }


    private static Fields fieldsFor(LivingEntity mob) {
        Fields known = fields;
        if (known == null && !fieldsTried) {
            known = resolveFields();
            String how = "resolver";
            if (known == null) {
                known = mob == null ? null : resolveByShape(mob.walkAnimation);
                how = "shape";
            }
            fields = known;
            fieldsTried = true;
            logPath(known != null, how);
        }
        return known;
    }

    private static void logPath(boolean direct, String how) {
        if (pathLogged) {
            return;
        }
        pathLogged = true;
        try {
            if (direct) {
                pdx.dev.fbactors.FlashbackActors.LOGGER.info(
                    "WalkDriver: direct walk-state fields ({}), steady legs", how);
            } else {
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "WalkDriver: field resolution failed, update() fallback");
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static Fields resolveFields() {
        try {
            net.fabricmc.loader.api.MappingResolver resolver =
                net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver();
            Class<?> state = Class.forName("net.minecraft.world.entity.WalkAnimationState");
            Field speedOld = declared(state,
                resolver.mapFieldName("named", OWNER, "speedOld", "F"));
            Field speed = declared(state,
                resolver.mapFieldName("named", OWNER, "speed", "F"));
            Field position = declared(state,
                resolver.mapFieldName("named", OWNER, "position", "F"));
            Field positionScale = null;
            try {
                positionScale = declared(state,
                    resolver.mapFieldName("named", OWNER, "positionScale", "F"));
            } catch (ReflectiveOperationException | RuntimeException ignored) {

            }
            if (speedOld == null || speed == null || position == null) {
                return null;
            }
            return new Fields(speedOld, speed, position, positionScale);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field declared(Class<?> owner, String runtimeName)
        throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(runtimeName);
        field.setAccessible(true);
        return field;
    }


    static Fields resolveByShape(Object walkState) {
        if (walkState == null) {
            return null;
        }
        try {
            List<Field> floats = new ArrayList<>();
            for (Field field : walkState.getClass().getDeclaredFields()) {
                if (field.getType() == float.class
                    && !Modifier.isStatic(field.getModifiers())) {
                    floats.add(field);
                }
            }
            if (floats.size() != 3 && floats.size() != 4) {
                return null;
            }
            for (Field field : floats) {
                field.setAccessible(true);
            }
            return new Fields(floats.get(0), floats.get(1), floats.get(2),
                floats.size() == 4 ? floats.get(3) : null);
        } catch (RuntimeException ignored) {


            return null;
        }
    }

    private static void driveViaUpdate(LivingEntity mob, float target) {
        UpdateFallback known = fallback;
        if (known == null && !fallbackTried) {
            known = resolveFallback();
            fallback = known;
            fallbackTried = true;
        }
        if (known == null) {
            return;
        }
        try {
            Object state = mob.walkAnimation;
            if (known.threeArg != null) {
                known.threeArg.invoke(state, target, 0.4F, 1.0F);
            } else if (known.twoArg != null) {
                known.twoArg.invoke(state, target, 0.4F);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static UpdateFallback resolveFallback() {
        try {
            Class<?> state = Class.forName("net.minecraft.world.entity.WalkAnimationState");
            Method threeArg = null;
            Method twoArg = null;
            try {
                threeArg = state.getMethod("update", float.class, float.class, float.class);
            } catch (NoSuchMethodException ignored) {

            }
            try {
                twoArg = state.getMethod("update", float.class, float.class);
            } catch (NoSuchMethodException ignored) {


            }
            if (threeArg == null && twoArg == null) {
                return null;
            }
            return new UpdateFallback(threeArg, twoArg);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}

