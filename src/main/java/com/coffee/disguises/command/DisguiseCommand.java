package com.coffee.disguises.command;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.command.argument.DisguiseTypeArgument;
import com.coffee.disguises.command.argument.FlagArgumentParser;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.disguise.PlayerDisguise;
import com.coffee.disguises.packet.PacketInterceptor;
import com.coffee.disguises.packet.SkinFetcher;
import com.coffee.disguises.watcher.FlagWatcher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Registers all /disguise subcommands.
 *
 * ── Self disguise ─────────────────────────────────────────────────────────────
 *   /disguise <type> [flags...]
 *   /disguise as <skinName>          — disguise as a named player's skin
 *   /disguise player <name> [flags]  — alias for /disguise as (avoids type-arg collision)
 *
 * ── Other-player disguise ─────────────────────────────────────────────────────
 *   /disguise <type> <target> [flags...]
 *   /disguise <type> <target> player <name> [flags]
 *
 * ── Non-player entity disguise ────────────────────────────────────────────────
 *   /disguise entity <selector> <type> [flags...]
 *
 * ── Radius disguise ───────────────────────────────────────────────────────────
 *   /disguise radius <num> <type> [flags...]
 *
 * ── Modify / self-view ────────────────────────────────────────────────────────
 *   /disguise modify [flags...]
 *   /disguise viewself [on|off]
 *
 * ── Flag tokens ───────────────────────────────────────────────────────────────
 * Special tokens handled here (not passed to FlagArgumentParser):
 *   selfview / selfView   — enable self-disguise view
 *   notselfview           — disable self-view (default)
 *   showname / show_name  — show name tag above the disguise
 *   hidename / hide_name  — hide name tag (default)
 *
 * All other tokens are forwarded to FlagArgumentParser (setColor, setBaby, etc.).
 */
public class DisguiseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("disguise")

                        // ── /disguise player <name> [flags] ───────────────────────────
                        // Declared before <type> arg so the "player" literal wins over
                        // "player" as a type id string.
                        .then(Commands.literal("player")
                                .requires(Permissions.require("disguises.disguise.self",
                                        DisguisesMod.CONFIG.permLevelSelf))
                                .then(Commands.argument("playerName", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "playerName");
                                            return disguiseSelfAsPlayer(ctx.getSource(), name, "");
                                        })
                                        .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                .suggests(DisguiseTypeArgument::suggestFlags)
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "playerName");
                                                    String flags = StringArgumentType.getString(ctx, "flags");
                                                    return disguiseSelfAsPlayer(ctx.getSource(), name, flags);
                                                })
                                        )
                                )
                        )

                        // ── /disguise as <skinName> ────────────────────────────────────
                        // Self-disguise as a named player's skin.
                        // Exists because /disguise player ... routes to the literal above.
                        .then(Commands.literal("as")
                                .requires(Permissions.require("disguises.disguise.self",
                                        DisguisesMod.CONFIG.permLevelSelf))
                                .then(Commands.argument("skinName", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "skinName");
                                            return disguiseSelfAsPlayer(ctx.getSource(), name, "");
                                        })
                                        .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                .suggests(DisguiseTypeArgument::suggestFlags)
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "skinName");
                                                    String flags = StringArgumentType.getString(ctx, "flags");
                                                    return disguiseSelfAsPlayer(ctx.getSource(), name, flags);
                                                })
                                        )
                                )
                        )

                        // ── /disguise entity <selector> <type> [flags] ────────────────
                        // Disguise any non-player entity by selector.
                        // For player-skin disguises:
                        //   /disguise entity <selector> player <skinName> [flags]
                        .then(Commands.literal("entity")
                                .requires(Permissions.require("disguises.disguise.entity",
                                        DisguisesMod.CONFIG.permLevelEntity))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        // entity → player <skinName> [flags]
                                        .then(Commands.literal("player")
                                                .then(Commands.argument("skinName", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                                            String skin = StringArgumentType.getString(ctx, "skinName");
                                                            return disguiseEntityAsPlayer(ctx.getSource(), target, skin, "");
                                                        })
                                                        .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                                .suggests(DisguiseTypeArgument::suggestFlags)
                                                                .executes(ctx -> {
                                                                    Entity target = EntityArgument.getEntity(ctx, "target");
                                                                    String skin = StringArgumentType.getString(ctx, "skinName");
                                                                    String flags = StringArgumentType.getString(ctx, "flags");
                                                                    return disguiseEntityAsPlayer(ctx.getSource(), target, skin, flags);
                                                                })
                                                        )
                                                )
                                        )
                                        // entity → <type> [flags]
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(DisguiseTypeArgument::suggest)
                                                .executes(ctx -> {
                                                    Entity target = EntityArgument.getEntity(ctx, "target");
                                                    DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                                    return disguiseEntity(ctx.getSource(), target, type, "");
                                                })
                                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                        .suggests(DisguiseTypeArgument::suggestFlags)
                                                        .executes(ctx -> {
                                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                                            DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                                            String flags = StringArgumentType.getString(ctx, "flags");
                                                            return disguiseEntity(ctx.getSource(), target, type, flags);
                                                        })
                                                )
                                        )
                                )
                        )

                        // ── /disguise radius <num> <type> [flags] ─────────────────────
                        // Disguise all entities within radius of the command source.
                        .then(Commands.literal("radius")
                                .requires(Permissions.require("disguises.disguise.radius",
                                        DisguisesMod.CONFIG.permLevelRadius))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 256.0))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(DisguiseTypeArgument::suggest)
                                                .executes(ctx -> {
                                                    double radius = DoubleArgumentType.getDouble(ctx, "radius");
                                                    DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                                    return disguiseRadius(ctx.getSource(), radius, type, "");
                                                })
                                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                        .suggests(DisguiseTypeArgument::suggestFlags)
                                                        .executes(ctx -> {
                                                            double radius = DoubleArgumentType.getDouble(ctx, "radius");
                                                            DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                                            String flags = StringArgumentType.getString(ctx, "flags");
                                                            return disguiseRadius(ctx.getSource(), radius, type, flags);
                                                        })
                                                )
                                        )
                                )
                        )

                        // ── /disguise modify [flags] ───────────────────────────────────
                        // Apply additional flag mutations to an already-active disguise.
                        .then(Commands.literal("modify")
                                .requires(Permissions.require("disguises.disguise.self",
                                        DisguisesMod.CONFIG.permLevelSelf))
                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                        .suggests(DisguiseTypeArgument::suggestFlags)
                                        .executes(ctx -> {
                                            String flags = StringArgumentType.getString(ctx, "flags");
                                            return modifySelf(ctx.getSource(), flags);
                                        })
                                )
                        )

                        // ── /disguise viewself [on|off] ───────────────────────────────
                        // Toggle whether the disguised player sees their own disguise.
                        .then(Commands.literal("viewself")
                                .requires(Permissions.require("disguises.viewself",
                                        DisguisesMod.CONFIG.permLevelSelf))
                                .executes(ctx -> viewSelf(ctx.getSource(), null))
                                .then(Commands.literal("on")
                                        .executes(ctx -> viewSelf(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> viewSelf(ctx.getSource(), false)))
                        )

                        // ── /disguise <type> [flags] ──────────────────────────────────
                        // Self-disguise. Also serves as the base for the
                        // /disguise <type> <target> [flags] branch below.
                        //
                        // NOTE: All literal subcommands above must be declared BEFORE
                        // this argument node so Brigadier resolves them first.
                        .then(Commands.argument("type", StringArgumentType.word())
                                .requires(Permissions.require("disguises.disguise.self",
                                        DisguisesMod.CONFIG.permLevelSelf))
                                .suggests(DisguiseTypeArgument::suggest)
                                .executes(ctx -> {
                                    DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                    return disguiseSelf(ctx.getSource(), type, "");
                                })
                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                        .suggests(DisguiseTypeArgument::suggestFlags)
                                        .executes(ctx -> {
                                            DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                            String flags = StringArgumentType.getString(ctx, "flags");
                                            return disguiseSelf(ctx.getSource(), type, flags);
                                        })
                                )

                                // ── /disguise <type> <target> [flags] ─────────────────
                                // Disguise another player. Type comes first to distinguish
                                // from the self branch cleanly.
                                .then(Commands.argument("target", EntityArgument.player())
                                        .requires(Permissions.require("disguises.disguise.others",
                                                DisguisesMod.CONFIG.permLevelOthers))
                                        .executes(ctx -> {
                                            DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            return disguiseOther(ctx.getSource(), target, type, "");
                                        })
                                        .then(Commands.argument("otherFlags",
                                                        StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    String flags = StringArgumentType.getString(ctx, "otherFlags");
                                                    return disguiseOther(ctx.getSource(), target, type, flags);
                                                })
                                        )

                                        // /disguise <type> <target> player <name> [flags]
                                        .then(Commands.literal("player")
                                                .then(Commands.argument("targetPlayerName",
                                                                StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                            String name = StringArgumentType.getString(ctx, "targetPlayerName");
                                                            return disguiseOtherAsPlayer(ctx.getSource(), target, name, "");
                                                        })
                                                        .then(Commands.argument("targetFlags",
                                                                        StringArgumentType.greedyString())
                                                                .executes(ctx -> {
                                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                                    String name = StringArgumentType.getString(ctx, "targetPlayerName");
                                                                    String flags = StringArgumentType.getString(ctx, "targetFlags");
                                                                    return disguiseOtherAsPlayer(ctx.getSource(), target, name, flags);
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    // =========================================================================
    // Self disguise
    // =========================================================================

    private static int disguiseSelf(CommandSourceStack source, DisguiseType type,
                                    String flagString)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (type == DisguiseType.PLAYER) {
            source.sendFailure(Component.literal(
                    "§cUse §e/disguise player <name>§c or §e/disguise as <name>§c to disguise as a player."));
            return 0;
        }

        if (!isTypeAllowed(source, type)) return 0;

        FlagWatcher watcher = type.createDefaultWatcher();
        ParseResult parsed = parseFlags(flagString, watcher, type);

        Disguise disguise = Disguise.builder(type)
                .watcher(watcher)
                .selfDisguise(parsed.selfView())
                .showName(parsed.showName())
                .build();

        boolean applied = DisguiseManager.INSTANCE.applyDisguise(player, disguise);
        if (!applied) {
            source.sendFailure(Component.literal("§cDisguise was cancelled by an event listener."));
            return 0;
        }

        sendWarnings(source, parsed.warnings());
        source.sendSuccess(() -> Component.literal(
                "§aYou are now disguised as §e" + type.getId()
                        + (parsed.selfView() ? " §7(self-view on)" : "")), false);
        return 1;
    }

    private static int disguiseSelfAsPlayer(CommandSourceStack source, String skinName,
                                            String flagString)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!isTypeAllowed(source, DisguiseType.PLAYER)) return 0;

        FlagWatcher watcher = DisguiseType.PLAYER.createDefaultWatcher();
        ParseResult parsed = parseFlags(flagString, watcher, DisguiseType.PLAYER);

        PlayerDisguise.PlayerBuilder pb = PlayerDisguise.builder(skinName);
        pb.watcher(watcher);
        pb.selfDisguise(parsed.selfView());
        pb.showName(parsed.showName());
        PlayerDisguise disguise = pb.build();

        boolean applied = DisguiseManager.INSTANCE.applyDisguise(player, disguise);
        if (!applied) {
            source.sendFailure(Component.literal("§cDisguise was cancelled."));
            return 0;
        }

        sendWarnings(source, parsed.warnings());
        source.sendSuccess(() -> Component.literal(
                "§aDisguising as player §e" + skinName + "§a, fetching skin…"), false);

        SkinFetcher.fetchByName(skinName, source.getServer(), profile -> {
            if (profile != null) {
                disguise.setSkinProfile(profile);
                PacketInterceptor.refreshForNearbyPlayers(player, disguise);
                source.sendSuccess(() -> Component.literal(
                        "§aSkin for §e" + skinName + " §aloaded."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "§7Could not fetch skin for §e" + skinName
                                + "§7. Using default Steve skin."), false);
            }
        });

        return 1;
    }

    // =========================================================================
    // Other-player disguise
    // =========================================================================

    private static int disguiseOther(CommandSourceStack source, ServerPlayer target,
                                     DisguiseType type, String flagString) {
        if (type == DisguiseType.PLAYER) {
            source.sendFailure(Component.literal(
                    "§cUse §e/disguise <type> <target> player <name>§c for a player-skin disguise."));
            return 0;
        }

        if (!isTypeAllowed(source, type)) return 0;

        FlagWatcher watcher = type.createDefaultWatcher();
        ParseResult parsed = parseFlags(flagString, watcher, type);

        Disguise disguise = Disguise.builder(type)
                .watcher(watcher)
                .selfDisguise(parsed.selfView())
                .showName(parsed.showName())
                .build();

        boolean applied = DisguiseManager.INSTANCE.applyDisguise(target, disguise);
        if (!applied) {
            source.sendFailure(Component.literal("§cDisguise was cancelled."));
            return 0;
        }
        sendWarnings(source, parsed.warnings());
        source.sendSuccess(() -> Component.literal(
                "§aDisguised §e" + target.getName().getString()
                        + " §aas §e" + type.getId()), true);
        return 1;
    }

    private static int disguiseOtherAsPlayer(CommandSourceStack source, ServerPlayer target,
                                             String skinName, String flagString) {
        if (!isTypeAllowed(source, DisguiseType.PLAYER)) return 0;

        FlagWatcher watcher = DisguiseType.PLAYER.createDefaultWatcher();
        ParseResult parsed = parseFlags(flagString, watcher, DisguiseType.PLAYER);

        PlayerDisguise.PlayerBuilder pb = PlayerDisguise.builder(skinName);
        pb.watcher(watcher);
        pb.selfDisguise(parsed.selfView());
        pb.showName(parsed.showName());
        PlayerDisguise disguise = pb.build();

        boolean applied = DisguiseManager.INSTANCE.applyDisguise(target, disguise);
        if (!applied) {
            source.sendFailure(Component.literal("§cDisguise was cancelled."));
            return 0;
        }

        sendWarnings(source, parsed.warnings());
        source.sendSuccess(() -> Component.literal(
                "§aDisguising §e" + target.getName().getString()
                        + " §aas player §e" + skinName + "§a, fetching skin…"), true);

        SkinFetcher.fetchByName(skinName, source.getServer(), profile -> {
            if (profile != null) {
                disguise.setSkinProfile(profile);
                PacketInterceptor.refreshForNearbyPlayers(target, disguise);
            }
        });
        return 1;
    }

    // =========================================================================
    // Non-player entity disguise
    // =========================================================================

    private static int disguiseEntity(CommandSourceStack source, Entity target,
                                      DisguiseType type, String flagString) {
        if (!isTypeAllowed(source, type)) return 0;

        FlagWatcher watcher = type.createDefaultWatcher();
        ParseResult parsed = parseFlags(flagString, watcher, type);

        Disguise disguise = Disguise.builder(type)
                .watcher(watcher)
                .showName(parsed.showName())
                .build();

        boolean applied = DisguiseManager.INSTANCE.applyDisguise(target, disguise);
        if (!applied) {
            source.sendFailure(Component.literal("§cDisguise was cancelled."));
            return 0;
        }
        sendWarnings(source, parsed.warnings());
        source.sendSuccess(() -> Component.literal(
                "§aDisguised entity as §e" + type.getId()), true);
        return 1;
    }

    private static int disguiseEntityAsPlayer(CommandSourceStack source, Entity target,
                                              String skinName, String flagString) {
        if (!isTypeAllowed(source, DisguiseType.PLAYER)) return 0;

        FlagWatcher watcher = DisguiseType.PLAYER.createDefaultWatcher();
        ParseResult parsed = parseFlags(flagString, watcher, DisguiseType.PLAYER);

        PlayerDisguise.PlayerBuilder pb = PlayerDisguise.builder(skinName);
        pb.watcher(watcher);
        pb.showName(parsed.showName());
        PlayerDisguise disguise = pb.build();

        boolean applied = DisguiseManager.INSTANCE.applyDisguise(target, disguise);
        if (!applied) {
            source.sendFailure(Component.literal("§cDisguise was cancelled."));
            return 0;
        }

        sendWarnings(source, parsed.warnings());
        source.sendSuccess(() -> Component.literal(
                "§aDisguising entity as player §e" + skinName + "§a, fetching skin…"), true);

        SkinFetcher.fetchByName(skinName, source.getServer(), profile -> {
            if (profile != null) {
                disguise.setSkinProfile(profile);
                PacketInterceptor.refreshForNearbyPlayers(target, disguise);
                source.sendSuccess(() -> Component.literal(
                        "§aSkin for §e" + skinName + " §aloaded."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "§7Could not fetch skin for §e" + skinName
                                + "§7. Using default Steve skin."), false);
            }
        });
        return 1;
    }

    // =========================================================================
    // Radius disguise
    // =========================================================================

    private static int disguiseRadius(CommandSourceStack source, double radius,
                                      DisguiseType type, String flagString) {
        if (!isTypeAllowed(source, type)) return 0;

        if (!(source.getLevel() instanceof ServerLevel level)) return 0;

        AABB box = AABB.ofSize(source.getPosition(), radius * 2, radius * 2, radius * 2);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);

        int count = 0;
        for (Entity entity : entities) {
            // Don't disguise the command executor themselves via the radius command
            if (entity == source.getEntity()) continue;

            FlagWatcher watcher = type.createDefaultWatcher();
            ParseResult parsed = parseFlags(flagString, watcher, type);

            Disguise disguise = Disguise.builder(type)
                    .watcher(watcher)
                    .showName(parsed.showName())
                    .build();

            if (DisguiseManager.INSTANCE.applyDisguise(entity, disguise)) {
                count++;
            }
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                "§aDisguised §e" + finalCount + " §aentities as §e" + type.getId()), true);
        return finalCount;
    }

    // =========================================================================
    // Modify active disguise
    // =========================================================================

    private static int modifySelf(CommandSourceStack source, String flagString)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        Disguise existing = DisguiseManager.INSTANCE.getDisguise(player);
        if (existing == null) {
            source.sendFailure(Component.literal("§cYou are not currently disguised."));
            return 0;
        }

        List<String> warnings = FlagArgumentParser.apply(
                existing.getWatcher(), existing.getType(), flagString);
        sendWarnings(source, warnings);

        // Re-apply to push the updated metadata to nearby players
        DisguiseManager.INSTANCE.applyDisguise(player, existing);

        source.sendSuccess(() -> Component.literal("§aDisguise modified."), false);
        return 1;
    }

    // =========================================================================
    // Toggle self-view
    // =========================================================================

    private static int viewSelf(CommandSourceStack source, Boolean value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        Disguise existing = DisguiseManager.INSTANCE.getDisguise(player);
        if (existing == null) {
            source.sendFailure(Component.literal("§cYou are not currently disguised."));
            return 0;
        }

        boolean newValue = (value == null) ? !existing.isSelfDisguise() : value;

        if (newValue == existing.isSelfDisguise()) {
            source.sendSuccess(() -> Component.literal(
                    "§7Self-view is already " + (newValue ? "§aON" : "§cOFF") + "§7."), false);
            return 1;
        }

        existing.setSelfDisguise(newValue);

        // Apply the change immediately: add or remove the self-view packets
        if (newValue) {
            PacketInterceptor.applySelfView(player, existing);
        } else {
            PacketInterceptor.removeSelfView(player);
        }

        source.sendSuccess(() -> Component.literal(
                "§7Self-disguise view: " + (newValue ? "§aON" : "§cOFF")), false);
        return 1;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Extract special tokens (selfview, showname) and forward the rest to
     * FlagArgumentParser. Returns a ParseResult with the resolved values.
     */
    private static ParseResult parseFlags(String flagString, FlagWatcher watcher,
                                          DisguiseType type) {
        boolean selfView = DisguisesMod.CONFIG.selfDisguiseDefault;
        boolean showName = false;
        StringBuilder remaining = new StringBuilder();

        if (flagString != null && !flagString.isBlank()) {
            for (String token : flagString.trim().split("\\s+")) {
                switch (token.toLowerCase()) {
                    case "selfview", "self_view"           -> selfView = true;
                    case "notselfview", "noselfview",
                         "no_self_view"                    -> selfView = false;
                    case "showname", "show_name"           -> showName = true;
                    case "hidename", "hide_name"           -> showName = false;
                    default -> {
                        if (!remaining.isEmpty()) remaining.append(' ');
                        remaining.append(token);
                    }
                }
            }
        }

        List<String> warnings = FlagArgumentParser.apply(watcher, type, remaining.toString());
        return new ParseResult(selfView, showName, warnings);
    }

    private static boolean isTypeAllowed(CommandSourceStack source, DisguiseType type) {
        if (DisguisesMod.CONFIG.disabledEntityTypes.contains(type.getId())) {
            source.sendFailure(Component.literal(
                    "§cThe entity type §e" + type.getId() + " §cis disabled on this server."));
            return false;
        }
        if (DisguisesMod.CONFIG.enforceTypePermissions) {
            boolean hasPerm = Permissions.check(source, "disguises.type." + type.getId(), 4);
            if (!hasPerm) {
                source.sendFailure(Component.literal(
                        "§cYou do not have permission to use the disguise type §e"
                                + type.getId() + "§c."));
                return false;
            }
        }
        return true;
    }

    private static void sendWarnings(CommandSourceStack source, List<String> warnings) {
        for (String w : warnings) {
            source.sendSuccess(() -> Component.literal("§7[Disguises] §eWarning: " + w), false);
        }
    }

    private record ParseResult(boolean selfView, boolean showName, List<String> warnings) {}
}