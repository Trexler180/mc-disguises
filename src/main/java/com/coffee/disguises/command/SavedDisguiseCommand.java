package com.coffee.disguises.command;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.core.SavedDisguisesManager;
import com.coffee.disguises.util.PermissionCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /savedisguise - manage named disguise presets.
 *
 * Usage:
 *   /savedisguise save <name> <type> [flags...]   — save a preset
 *   /savedisguise apply <name>                    — apply a saved preset
 *   /savedisguise list                            — list your presets
 *   /savedisguise delete <name>                   — delete a preset
 */
public class SavedDisguiseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("savedisguise")
                        .requires(PermissionCompat.require("disguises.savedisguise",
                                DisguisesMod.CONFIG.permLevelSelf))

                        // /savedisguise list
                        .then(Commands.literal("list")
                                .executes(ctx -> listPresets(ctx.getSource()))
                        )

                        // /savedisguise save <name> <type+flags>
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("disguiseString",
                                                        StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String dstr = StringArgumentType.getString(ctx, "disguiseString");
                                                    return savePreset(ctx.getSource(), name, dstr);
                                                })
                                        )
                                )
                        )

                        // /savedisguise apply <name>
                        .then(Commands.literal("apply")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            try {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                List<String> presets = SavedDisguisesManager.INSTANCE.list(
                                                        player.getUUID());
                                                String partial = builder.getRemaining().toLowerCase();
                                                for (String p : presets) {
                                                    if (p.startsWith(partial)) builder.suggest(p);
                                                }
                                            } catch (Exception ignored) {}
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return applyPreset(ctx.getSource(), name);
                                        })
                                )
                        )

                        // /savedisguise delete <name>
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            try {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                List<String> presets = SavedDisguisesManager.INSTANCE.list(
                                                        player.getUUID());
                                                String partial = builder.getRemaining().toLowerCase();
                                                for (String p : presets) {
                                                    if (p.startsWith(partial)) builder.suggest(p);
                                                }
                                            } catch (Exception ignored) {}
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return deletePreset(ctx.getSource(), name);
                                        })
                                )
                        )
        );
    }

    private static int listPresets(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<String> presets = SavedDisguisesManager.INSTANCE.list(player.getUUID());
        if (presets.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7You have no saved disguise presets."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("§6Saved presets (").append(presets.size()).append("):\n");
        for (String name : presets) {
            String value = SavedDisguisesManager.INSTANCE.get(player.getUUID(), name);
            sb.append("§7- §e").append(name).append(" §7→ §a").append(value).append("\n");
        }
        final String msg = sb.toString().trim();
        source.sendSuccess(() -> Component.literal(msg), false);
        return presets.size();
    }

    private static int savePreset(CommandSourceStack source, String name, String disguiseString)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SavedDisguisesManager.INSTANCE.save(player.getUUID(), name, disguiseString);
        source.sendSuccess(() -> Component.literal(
                "§aSaved disguise preset §e" + name + " §7→ §a" + disguiseString), false);
        return 1;
    }

    private static int applyPreset(CommandSourceStack source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String disguiseString = SavedDisguisesManager.INSTANCE.get(player.getUUID(), name);
        if (disguiseString == null) {
            source.sendFailure(Component.literal(
                    "§cNo preset named §e" + name + "§c. Use §e/savedisguise list§c to see your presets."));
            return 0;
        }
        // Delegate to the /disguise command to avoid duplicating the disguise logic
        source.getServer().getCommands().performPrefixedCommand(source, "disguise " + disguiseString);
        source.sendSuccess(() -> Component.literal("§aApplied preset §e" + name), false);
        return 1;
    }

    private static int deletePreset(CommandSourceStack source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean removed = SavedDisguisesManager.INSTANCE.delete(player.getUUID(), name);
        if (!removed) {
            source.sendFailure(Component.literal("§cNo preset named §e" + name + "§c."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aDeleted preset §e" + name), false);
        return 1;
    }
}
