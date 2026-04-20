package com.coffee.disguises.command;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.command.argument.FlagArgumentParser;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.packet.PacketInterceptor;
import com.coffee.disguises.packet.SkinFetcher;
import com.coffee.disguises.util.PermissionCompat;
import com.coffee.disguises.watcher.FlagWatcher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
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
                        .requires(PermissionCompat.require("disguises.admin", DisguisesMod.CONFIG.permLevelAdmin))

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

                        // /disguises observer <target> <viewer> <type> [flags]
                        // Sets an observer-specific disguise: <viewer> sees <target> as <type>
                        .then(Commands.literal("observer")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("viewer", EntityArgument.player())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                                            ServerPlayer viewer = EntityArgument.getPlayer(ctx, "viewer");
                                                            String typeStr = StringArgumentType.getString(ctx, "type");
                                                            return setObserverDisguise(ctx.getSource(), target, viewer, typeStr, "");
                                                        })
                                                        .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                                .executes(ctx -> {
                                                                    Entity target = EntityArgument.getEntity(ctx, "target");
                                                                    ServerPlayer viewer = EntityArgument.getPlayer(ctx, "viewer");
                                                                    String typeStr = StringArgumentType.getString(ctx, "type");
                                                                    String flags = StringArgumentType.getString(ctx, "flags");
                                                                    return setObserverDisguise(ctx.getSource(), target, viewer, typeStr, flags);
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )

                        // /disguises removeobserver <target> <viewer>
                        // Removes the observer-specific override; <viewer> sees the default disguise again
                        .then(Commands.literal("removeobserver")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("viewer", EntityArgument.player())
                                                .executes(ctx -> {
                                                    Entity target = EntityArgument.getEntity(ctx, "target");
                                                    ServerPlayer viewer = EntityArgument.getPlayer(ctx, "viewer");
                                                    DisguiseManager.INSTANCE.removeObserverDisguise(target, viewer.getUUID());
                                                    PacketInterceptor.refreshForObserver(viewer, target);
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "§aRemoved observer disguise for §e" + viewer.getName().getString()
                                                                    + " §awatching §e" + target.getUUID()), false);
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /disguises clearobservers <target>
                        // Clears ALL observer-specific overrides for an entity
                        .then(Commands.literal("clearobservers")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                            DisguiseManager.INSTANCE.clearObserverDisguises(target);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "§aCleared all observer disguises for §e" + target.getUUID()), false);
                                            return 1;
                                        })
                                )
                        )
        );
    }

    private static int setObserverDisguise(CommandSourceStack source, Entity target,
                                           ServerPlayer viewer, String typeStr, String flagString) {
        Optional<DisguiseType> opt = DisguiseType.fromId(typeStr);
        if (opt.isEmpty()) {
            source.sendFailure(Component.literal("§cUnknown disguise type: §e" + typeStr));
            return 0;
        }
        DisguiseType type = opt.get();
        FlagWatcher watcher = type.createDefaultWatcher();
        List<String> warnings = FlagArgumentParser.apply(watcher, type, flagString);

        Disguise disguise = Disguise.builder(type).watcher(watcher).build();
        DisguiseManager.INSTANCE.setObserverDisguise(target, viewer.getUUID(), disguise);
        PacketInterceptor.refreshForObserver(viewer, target);

        for (String w : warnings) {
            source.sendSuccess(() -> Component.literal("§e[Warning] " + w), false);
        }
        source.sendSuccess(() -> Component.literal(
                "§aSet observer disguise: §e" + viewer.getName().getString()
                        + " §7sees §e" + target.getUUID()
                        + " §7as §a" + type.getId()), false);
        return 1;
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
