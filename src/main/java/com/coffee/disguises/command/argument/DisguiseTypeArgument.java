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
 * Helper for parsing and suggesting DisguiseType and flag arguments.
 *
 * WHY NOT A CUSTOM ARGUMENTTYPE:
 * Custom ArgumentType implementations must be registered in ArgumentTypeInfos or
 * the server crashes when sending the command tree to clients.  Rather than
 * registering a custom serializer, we use StringArgumentType in the command
 * tree and parse/suggest the DisguiseType here at execution and suggestion time.
 *
 * USAGE IN COMMAND REGISTRATION:
 *   // type argument — resolves to DisguiseType
 *   Commands.argument("type", StringArgumentType.word())
 *       .suggests(DisguiseTypeArgument::suggest)
 *       ...
 *       .executes(ctx -> {
 *           DisguiseType type = DisguiseTypeArgument.get(ctx, "type");
 *           ...
 *       })
 *
 *   // flags argument — context-sensitive suggestions (flag names + values)
 *   Commands.argument("flags", StringArgumentType.greedyString())
 *       .suggests(DisguiseTypeArgument::suggestFlags)
 *       .executes(ctx -> {
 *           String flags = StringArgumentType.getString(ctx, "flags");
 *           ...
 *       })
 */
public final class DisguiseTypeArgument {

    private static final SimpleCommandExceptionType INVALID_TYPE = new SimpleCommandExceptionType(
            Component.literal("Unknown entity type. Use tab-complete to see valid options.")
    );

    private DisguiseTypeArgument() {}

    // =========================================================================
    // Parsing
    // =========================================================================

    /**
     * Parse and return the DisguiseType from a word argument named {@code argName}.
     * Throws a user-friendly error if unrecognised.
     */
    public static DisguiseType get(CommandContext<CommandSourceStack> ctx, String argName)
            throws CommandSyntaxException {
        String value = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, argName)
                .toLowerCase();
        return DisguiseType.fromId(value).orElseThrow(INVALID_TYPE::create);
    }

    // =========================================================================
    // Suggestions — type names
    // =========================================================================

    /**
     * Suggests all available disguise type IDs.
     * Wire to the "type" argument: .suggests(DisguiseTypeArgument::suggest)
     */
    public static CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> ids = Arrays.stream(DisguiseType.values())
                .map(DisguiseType::getId)
                .collect(Collectors.toList());
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    // =========================================================================
    // Suggestions — flag names and values (context-aware)
    // =========================================================================

    /**
     * Context-aware suggestions for the greedy "flags" argument.
     *
     * Reads the DisguiseType already parsed from the command context (requires
     * the "type" argument to appear earlier in the command tree), then:
     *
     *   • If the cursor is at the start of a new token (or the very first token):
     *     suggest applicable flag names for that type.
     *
     *   • If the previous token was a flag that takes a value (setColor, setSize…):
     *     suggest valid values for that flag (DyeColor names, size integers, etc.).
     *
     * How Brigadier passes input to this method:
     *   SuggestionsBuilder.getRemaining() returns the partial string for the
     *   current argument.  For a greedyString argument this is everything typed
     *   after the type name, e.g. "setColor " or "setBaby setColor R".
     *
     * Wire to the flags argument: .suggests(DisguiseTypeArgument::suggestFlags)
     */
    public static CompletableFuture<Suggestions> suggestFlags(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {

        // Resolve type from context — may not be available if tab-completing type still
        DisguiseType type = null;
        try {
            type = get(ctx, "type");
        } catch (Exception ignored) {}

        String soFar = builder.getRemaining();
        List<String> candidates = FlagArgumentParser.suggest(soFar, type);

        if (candidates.isEmpty()) {
            return builder.buildFuture();
        }

        // Build an offset builder positioned at the START of the partial token
        // so only the partial is replaced, not the whole flags string.
        int offsetFromStart = computeTokenStart(soFar);
        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + offsetFromStart);

        return SharedSuggestionProvider.suggest(candidates, offsetBuilder);
    }

    /**
     * Returns the character offset within {@code soFar} at which the current
     * (last) token starts.  This is used to tell Brigadier how much of the input
     * to replace when the user selects a suggestion.
     *
     * Examples:
     *   ""                → 0   (empty: replace from start)
     *   "setColor "       → 9   (next token starts at position 9)
     *   "setBaby setC"    → 8   (partial "setC" starts at position 8)
     */
    private static int computeTokenStart(String soFar) {
        if (soFar.isEmpty()) return 0;
        if (soFar.endsWith(" ")) return soFar.length();
        int lastSpace = soFar.lastIndexOf(' ');
        return lastSpace < 0 ? 0 : lastSpace + 1;
    }
}
