package com.coffee.disguises.command;

import com.coffee.disguises.command.argument.FlagArgumentParser;
import com.coffee.disguises.disguise.DisguiseType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /disguisehelp [type]
 *
 * Without type: lists all available disguise types.
 * With type: lists the valid flags for that type.
 */
public class DisguiseHelpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("disguisehelp")

                        // /disguisehelp → list all types
                        .executes(ctx -> listAllTypes(ctx.getSource()))

                        // /disguisehelp <type> → show flags for type
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String soFar = builder.getRemaining().toLowerCase();
                                    for (DisguiseType t : DisguiseType.values()) {
                                        if (t.getId().startsWith(soFar)) builder.suggest(t.getId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String typeName = StringArgumentType.getString(ctx, "type");
                                    Optional<DisguiseType> opt = DisguiseType.fromId(typeName);
                                    if (opt.isEmpty()) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§cUnknown disguise type: §e" + typeName));
                                        return 0;
                                    }
                                    return showTypeFlags(ctx.getSource(), opt.get());
                                })
                        )
        );
    }

    private static int listAllTypes(CommandSourceStack source) {
        // Group by category
        String passiveTypes = Arrays.stream(DisguiseType.values())
                .filter(t -> !t.isInanimate() && !t.isPlayer())
                .map(DisguiseType::getId)
                .collect(Collectors.joining("§7, §a"));
        String inanimateTypes = Arrays.stream(DisguiseType.values())
                .filter(DisguiseType::isInanimate)
                .map(DisguiseType::getId)
                .collect(Collectors.joining("§7, §a"));

        source.sendSuccess(() -> Component.literal(
                "§6=== Disguise Types ===\n" +
                "§7Use §e/disguisehelp <type> §7to see flags for a specific type.\n" +
                "§aLiving entities§7: §a" + passiveTypes + "\n" +
                "§aInanimate§7: §a" + inanimateTypes
        ), false);
        return DisguiseType.values().length;
    }

    private static int showTypeFlags(CommandSourceStack source, DisguiseType type) {
        List<String> flags = FlagArgumentParser.getValidFlagNames(type);

        source.sendSuccess(() -> Component.literal(
                "§6=== Flags for §e" + type.getId() + " §6===\n" +
                "§7Usage: §e/disguise " + type.getId() + " §b[flags...]\n" +
                "§7Available flags:\n  §a" + String.join("§7, §a", flags)
        ), false);
        return 1;
    }
}
