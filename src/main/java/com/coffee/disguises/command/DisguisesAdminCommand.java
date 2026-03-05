package com.coffee.disguises.command;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.packet.SkinFetcher;
import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Registers:
 *   /disguises reload               → reload config
 *   /disguises info [player]        → show active disguise for a player
 *   /disguises list                 → list all disguised entities
 *   /disguises clearcache           → clear skin cache
 */
public class DisguisesAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("disguises")
                        .requires(Permissions.require("disguises.admin", DisguisesMod.CONFIG.permLevelAdmin))

                        // /disguises reload
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    DisguisesMod.CONFIG = com.coffee.disguises.DisguisesConfig.load();
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§aDisguises config reloaded."), true);
                                    return 1;
                                })
                        )

                        // /disguises info [player]
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return printInfo(ctx.getSource(), player);
                                })
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            return printInfo(ctx.getSource(), target);
                                        })
                                )
                        )

                        // /disguises list
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    java.util.Collection<UUID> disguised = DisguiseManager.INSTANCE.getAllDisguisedUUIDs();
                                    if (disguised.isEmpty()) {
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§7No entities are currently disguised."), false);
                                        return 0;
                                    }
                                    StringBuilder sb = new StringBuilder("§aCurrently disguised (")
                                            .append(disguised.size()).append("):\n");
                                    for (UUID uuid : disguised) {
                                        Disguise d = DisguiseManager.INSTANCE.getDisguise(uuid);
                                        if (d != null) {
                                            sb.append("§7- §e").append(uuid)
                                                    .append(" §7→ §a").append(d.getType().getId()).append("\n");
                                        }
                                    }
                                    final String msg = sb.toString().trim();
                                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                    return disguised.size();
                                })
                        )

                        // /disguises clearcache
                        .then(Commands.literal("clearcache")
                                .executes(ctx -> {
                                    SkinFetcher.clearCache();
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§aSkin cache cleared."), true);
                                    return 1;
                                })
                        )
        );
    }

    private static int printInfo(CommandSourceStack source, ServerPlayer target) {
        Disguise disguise = DisguiseManager.INSTANCE.getDisguise(target);
        if (disguise == null) {
            source.sendSuccess(() ->
                    Component.literal("§e" + target.getName().getString() + " §7is not disguised."), false);
        } else {
            source.sendSuccess(() ->
                    Component.literal("§e" + target.getName().getString() + " §7is disguised as §a"
                            + disguise.getType().getId()
                            + " §7(selfView=" + disguise.isSelfDisguise() + ")"), false);
        }
        return 1;
    }
}