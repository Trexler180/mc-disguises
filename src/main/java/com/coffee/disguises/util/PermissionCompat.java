package com.coffee.disguises.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.function.IntSupplier;

/**
 * Runtime bridge for fabric-permissions-api.
 *
 * The upstream API is optional here because the unobfuscated 26.1 toolchain can
 * outpace precompiled API jars. If the API is present at runtime we delegate to
 * it reflectively; otherwise we fall back to vanilla op-level checks.
 */
public final class PermissionCompat {

    private static final Method CHECK_METHOD = findCheckMethod();

    private PermissionCompat() {}

    /**
     * Creates a permission predicate whose vanilla fallback level is resolved for
     * every check. This keeps registered command predicates in sync after a config
     * reload replaces {@code DisguisesMod.CONFIG}.
     *
     * Deliberately supplier-only: an {@code int} overload would capture the level at
     * command-registration time, so editing permLevel* and running /disguises reload
     * would silently have no effect. Commands are registered once per server start,
     * which makes that failure mode both easy to introduce and hard to notice.
     */
    public static Predicate<CommandSourceStack> require(
            String permission, IntSupplier fallbackLevel) {
        return source -> check(source, permission, fallbackLevel.getAsInt());
    }

    public static boolean check(CommandSourceStack source, String permission, int fallbackLevel) {
        if (CHECK_METHOD != null) {
            try {
                Object result = CHECK_METHOD.invoke(null, source, permission, fallbackLevel);
                if (result instanceof Boolean allowed) {
                    return allowed;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to vanilla permissions below.
            }
        }

        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(fallbackLevel)));
    }

    private static Method findCheckMethod() {
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return permissionsClass.getMethod("check", CommandSourceStack.class, String.class, int.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
