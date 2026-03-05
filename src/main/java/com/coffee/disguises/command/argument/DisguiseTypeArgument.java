package com.coffee.disguises.command.argument;

import com.coffee.disguises.disguise.DisguiseType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Helper for parsing DisguiseType from a plain StringArgumentType.word() argument.
 *
 * Custom ArgumentType implementations must be registered in ArgumentTypeInfos
 * or the server crashes when sending the command tree to clients. Rather than
 * registering a custom serializer, we use StringArgumentType.word() in the
 * command tree and parse the DisguiseType here at execution time.
 *
 * Usage in command registration:
 *   Commands.argument("type", StringArgumentType.word())
 *       .suggests(DisguiseTypeArgument::suggest)
 *       ...
 *       .executes(ctx -> {
 *           DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
 *           ...
 *       })
 */
public final class DisguiseTypeArgument {

    private static final SimpleCommandExceptionType INVALID_TYPE = new SimpleCommandExceptionType(
            Component.literal("Unknown entity type. Use tab-complete to see valid options.")
    );

    private DisguiseTypeArgument() {}

    /**
     * Parse and return the DisguiseType from a string argument named {@code argName}.
     * Throws a user-friendly CommandSyntaxException if the value is unrecognised.
     */
    public static DisguiseType get(CommandContext<CommandSourceStack> ctx, String argName)
            throws CommandSyntaxException {
        String value = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, argName)
                .toLowerCase();
        return DisguiseType.fromId(value).orElseThrow(INVALID_TYPE::create);
    }

    /**
     * SuggestionsProvider — wire this to .suggests(DisguiseTypeArgument::suggest) in the command tree.
     */
    public static CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> ids = Arrays.stream(DisguiseType.values())
                .map(DisguiseType::getId)
                .collect(Collectors.toList());
        return SharedSuggestionProvider.suggest(ids, builder);
    }
}