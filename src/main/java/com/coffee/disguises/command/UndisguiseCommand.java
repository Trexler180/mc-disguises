package com.coffee.disguises.command;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.util.PermissionCompat;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Registers:
 *   /undisguise                  → remove your own disguise
 *   /undisguise <player>         → remove another player's disguise (elevated perm)
 *   /undisguise <selector>       → remove disguise from any entity (elevated perm)
 *   /undisguise radius <num>     → remove disguise from all entities in radius
 */
public class UndisguiseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("undisguise")
                        // /undisguise — self
                        .requires(PermissionCompat.require("disguises.undisguise.self",
                                DisguisesMod.CONFIG.permLevelSelf))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return undisguise(ctx.getSource(), player);
                        })

                        // /undisguise radius <num>
                        .then(Commands.literal("radius")
                                .requires(PermissionCompat.require("disguises.undisguise.radius",
                                        DisguisesMod.CONFIG.permLevelRadius))
                                .then(Commands.argument("radius",
                                                com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.5, 256.0))
                                        .executes(ctx -> {
                                            double radius = com.mojang.brigadier.arguments.DoubleArgumentType
                                                    .getDouble(ctx, "radius");
                                            CommandSourceStack source = ctx.getSource();
                                            ServerLevel level = source.getLevel();
                                            AABB box = AABB.ofSize(source.getPosition(),
                                                    radius * 2, radius * 2, radius * 2);

                                            int count = 0;
                                            for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
                                                if (DisguiseManager.INSTANCE.isDisguised(entity)) {
                                                    DisguiseManager.INSTANCE.removeDisguise(entity, true);
                                                    count++;
                                                }
                                            }
                                            final int finalCount = count;
                                            source.sendSuccess(() -> Component.literal(
                                                    "§aRemoved disguises from §e" + finalCount
                                                            + " §aentities."), true);
                                            return finalCount;
                                        })
                                )
                        )

                        // /undisguise <entity selector>  — players OR any entity
                        // EntityArgument.entity() accepts both @n[type=sheep] and a player name,
                        // so this single branch handles both cases.
                        .then(Commands.argument("target", EntityArgument.entity())
                                .requires(PermissionCompat.require("disguises.undisguise.others",
                                        DisguisesMod.CONFIG.permLevelOthers))
                                .executes(ctx -> {
                                    Entity target = EntityArgument.getEntity(ctx, "target");
                                    return undisguise(ctx.getSource(), target);
                                })
                        )
        );
    }

    private static int undisguise(CommandSourceStack source, Entity target) {
        if (!DisguiseManager.INSTANCE.isDisguised(target)) {
            String name = target instanceof ServerPlayer sp
                    ? sp.getName().getString()
                    : target.getType().toShortString();
            source.sendFailure(Component.literal(name + " is not disguised."));
            return 0;
        }

        boolean removed = DisguiseManager.INSTANCE.removeDisguise(target, true);
        if (removed) {
            String name = target instanceof ServerPlayer sp
                    ? sp.getName().getString()
                    : target.getType().toShortString();
            source.sendSuccess(() -> Component.literal(
                    "§aRemoved disguise from §e" + name), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Undisguise was cancelled."));
            return 0;
        }
    }
}
