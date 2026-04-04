package com.coffee.disguises.command.argument;

import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.watcher.*;
import com.coffee.disguises.watcher.BlockStateWatcher;
import com.coffee.disguises.watcher.MinecartWatcher;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Parses a space-separated flag string into mutations on a FlagWatcher.
 *
 * ─── FORMAT ───────────────────────────────────────────────────────────────────
 *
 * Each flag is a keyword, optionally followed by a value token:
 *
 *   /disguise sheep setColor RED
 *   /disguise sheep setBaby setColor RED setSheared
 *   /disguise creeper setPowered setIgnited true
 *   /disguise slime setSize 3
 *   /disguise wolf setTamed setAngry setCollarColor BLUE
 *
 * Boolean flags accept an OPTIONAL value token (true/false/yes/no/on/off/1/0).
 * If the next token is NOT one of those keywords, the flag defaults to TRUE and
 * the token is NOT consumed — it will be parsed as the next flag name.
 *
 * ─── THE OLD i++ BUG (FIXED) ─────────────────────────────────────────────────
 *
 * The previous implementation used:
 *
 *   case "setbaby" -> watcher.setOnFire(parseBool(tokens, i++, true));
 *
 * Java evaluates i++ BEFORE calling parseBool, so i was incremented regardless
 * of whether the next token was actually a boolean. This meant:
 *
 *   setBaby setColor RED
 *   ├── setBaby → parseBool(tokens, 1, true)
 *   │   tokens[1] = "setColor" → not bool → returns true ✓ BUT i=2 (skipped "setColor")
 *   └── "RED" → Unknown flag error
 *
 * The fix: use int[] as a mutable index. nextBool() only increments the index
 * when the next token IS a recognised boolean keyword; otherwise it returns the
 * default and leaves the index unchanged so the next iteration parses the token
 * as a flag name.
 */
public class FlagArgumentParser {

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Apply flag tokens to the given watcher.
     *
     * @param watcher    the watcher to mutate
     * @param type       the DisguiseType (used for applicability checks)
     * @param flagString the raw flag string from the command argument
     * @return list of parse warnings (non-fatal; shown to the user as yellow messages)
     */
    public static List<String> apply(FlagWatcher watcher, DisguiseType type, String flagString) {
        List<String> warnings = new ArrayList<>();
        if (flagString == null || flagString.isBlank()) return warnings;

        String[] tokens = flagString.trim().split("\\s+");
        int[] i = {0};  // mutable index — helpers advance it only when they consume a token

        while (i[0] < tokens.length) {
            String flag = tokens[i[0]++].toLowerCase();

            switch (flag) {

                // ── Universal Entity flags ───────────────────────────────────

                case "setfire", "fire", "onfire" ->
                        watcher.setOnFire(nextBool(tokens, i, true));

                case "setinvisible", "invisible" ->
                        watcher.setInvisible(nextBool(tokens, i, true));

                case "setglowing", "glowing", "glow" ->
                        watcher.setGlowing(nextBool(tokens, i, true));

                case "setsilent", "silent" ->
                        watcher.setSilent(nextBool(tokens, i, true));

                case "setnogravity", "nogravity", "fly" ->
                        watcher.setNoGravity(nextBool(tokens, i, true));

                case "setcrouching", "crouching", "sneak", "crouch" ->
                        watcher.setCrouching(nextBool(tokens, i, true));

                case "setsprinting", "sprinting", "sprint" ->
                        watcher.setSprinting(nextBool(tokens, i, true));

                case "setswimming", "swimming", "swim" ->
                        watcher.setSwimming(nextBool(tokens, i, true));

                case "setcustomname", "customname", "name" -> {
                    String name = nextString(tokens, i);
                    if (name != null) {
                        watcher.setCustomName(name.replace('_', ' '));
                        watcher.setCustomNameVisible(true);
                    } else {
                        warnings.add("'setCustomName' requires a name value.");
                    }
                }

                case "setcustomnamevisible", "shownametag", "nametag" ->
                        watcher.setCustomNameVisible(nextBool(tokens, i, true));

                // ── LivingEntity ─────────────────────────────────────────────

                case "sethealth", "health", "hp" -> {
                    if (watcher instanceof LivingEntityWatcher lew) {
                        float hp = nextFloat(tokens, i, -1f);
                        if (hp < 0) warnings.add("'setHealth' requires a numeric value (e.g. setHealth 20).");
                        else lew.setHealth(hp);
                    } else {
                        warnings.add("'setHealth' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── AgeableMob (Cow, Sheep, Pig, Horse, etc.) ────────────────

                case "setbaby", "baby" -> {
                    if (watcher instanceof AgeableWatcher aw) {
                        aw.setBaby(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setBaby' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setadult", "adult" -> {
                    if (watcher instanceof AgeableWatcher aw) {
                        aw.setBaby(false);
                    } else {
                        warnings.add("'setAdult' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Sheep ─────────────────────────────────────────────────────

                case "setcolor", "color", "setcolour", "colour", "wool" -> {
                    if (watcher instanceof SheepWatcher sw) {
                        String raw = nextString(tokens, i);
                        if (raw == null) {
                            warnings.add("'setColor' requires a color name. Valid: " + dyeColorList());
                        } else {
                            DyeColor color = parseDyeColor(raw);
                            if (color == null) {
                                warnings.add("Unknown color '" + raw + "'. Valid: " + dyeColorList());
                            } else {
                                sw.setColor(color);
                            }
                        }
                    } else {
                        warnings.add("'setColor' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setsheared", "sheared", "shear" -> {
                    if (watcher instanceof SheepWatcher sw) {
                        sw.setSheared(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setSheared' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Creeper ───────────────────────────────────────────────────

                case "setpowered", "powered", "charged", "charge" -> {
                    if (watcher instanceof CreeperWatcher cw) {
                        cw.setPowered(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setPowered' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setignited", "ignited", "ignite", "fuse" -> {
                    if (watcher instanceof CreeperWatcher cw) {
                        cw.setIgnited(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setIgnited' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Slime / Magma Cube ────────────────────────────────────────

                case "setsize", "size", "sz" -> {
                    if (watcher instanceof SlimeWatcher slw) {
                        int sz = nextInt(tokens, i, -1);
                        if (sz < 1) {
                            warnings.add("'setSize' requires a positive integer (1=tiny, 2=small, 4=large).");
                        } else {
                            slw.setSize(sz);
                        }
                    } else {
                        warnings.add("'setSize' is not applicable to " + type.getId() + ". (Only slime / magma_cube)");
                    }
                }

                // ── Wolf ──────────────────────────────────────────────────────

                case "settamed", "tamed", "tame" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        ww.setTamed(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setTamed' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setsitting", "sitting", "sit" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        ww.setSitting(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setSitting' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setangry", "angry", "anger" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        ww.setAngry(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setAngry' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcollarcolor", "collarcolor", "collar" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        String raw = nextString(tokens, i);
                        if (raw == null) {
                            warnings.add("'setCollarColor' requires a color name. Valid: " + dyeColorList());
                        } else {
                            DyeColor color = parseDyeColor(raw);
                            if (color == null) {
                                warnings.add("Unknown color '" + raw + "'. Valid: " + dyeColorList());
                            } else {
                                ww.setCollarColor(color);
                            }
                        }
                    } else {
                        warnings.add("'setCollarColor' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Enderman ──────────────────────────────────────────────────

                case "setscreaming", "screaming", "scream", "angry_eyes" -> {
                    if (watcher instanceof EndermanWatcher ew) {
                        ew.setScreaming(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setScreaming' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── BlockStateWatcher (falling_block, block_display, minecarts) ──

                case "setblock", "block", "blocktype", "tile" -> {
                    if (watcher instanceof BlockStateWatcher bsw) {
                        String raw = nextString(tokens, i);
                        if (raw == null) {
                            warnings.add("'setBlock' requires a block name (e.g. setBlock minecraft:diamond_block or setBlock stone).");
                        } else {
                            Block block = parseBlock(raw);
                            if (block == null) {
                                warnings.add("Unknown block '" + raw + "'. Use a namespaced ID like 'minecraft:grass_block' or a short name like 'stone'.");
                            } else {
                                bsw.setBlock(block);
                            }
                        }
                    } else {
                        warnings.add("'setBlock' is not applicable to " + type.getId() + ". (Only falling_block, block_display, and minecart types)");
                    }
                }

                // ── MinecartWatcher extras ─────────────────────────────────────

                case "setdisplayoffset", "displayoffset", "blockoffset", "offset" -> {
                    if (watcher instanceof MinecartWatcher mw) {
                        int offset = nextInt(tokens, i, -1);
                        if (offset < 0) {
                            warnings.add("'setDisplayOffset' requires a non-negative integer (default 6).");
                        } else {
                            mw.setDisplayOffset(offset);
                        }
                    } else {
                        warnings.add("'setDisplayOffset' is only applicable to minecart types.");
                    }
                }

                default ->
                        warnings.add("Unknown flag: '§e" + flag + "§7'. "
                                + "Valid flags for §e" + type.getId() + "§7: "
                                + String.join(", ", getValidFlagNames(type)));
            }
        }
        return warnings;
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    /**
     * Returns a list of strings to suggest for tab-completing the flag portion
     * of a disguise command.
     *
     * Called from DisguiseTypeArgument.suggestFlags() which is wired to the
     * flags argument via .suggests().
     *
     * @param soFar  the text typed so far in the flags argument
     * @param type   the disguise type (determines which flags are applicable)
     * @return list of suggestion strings (may include values like color names)
     */
    public static List<String> suggest(String soFar, DisguiseType type) {
        if (type == null) return Collections.emptyList();

        // Split what's been typed. If it ends with a space, we're starting a new token.
        // If not, the last token is partially typed.
        boolean endsWithSpace = soFar.endsWith(" ");
        String[] parts = soFar.trim().isEmpty() ? new String[0] : soFar.trim().split("\\s+");

        String partial;
        String precedingFlag;

        if (endsWithSpace || parts.length == 0) {
            partial = "";
            precedingFlag = parts.length > 0 ? parts[parts.length - 1].toLowerCase() : null;
        } else {
            partial = parts[parts.length - 1];
            precedingFlag = parts.length >= 2 ? parts[parts.length - 2].toLowerCase() : null;
        }

        // If the preceding token is a flag that expects a value, suggest values
        if (precedingFlag != null && flagExpectsValue(precedingFlag)) {
            return suggestValuesFor(precedingFlag, type, partial);
        }

        // Otherwise suggest flag names
        return filterByPrefix(getValidFlagNames(type), partial);
    }

    /** Returns valid flag names for the given type. */
    public static List<String> getValidFlagNames(DisguiseType type) {
        if (type == null) return Collections.emptyList();
        Class<? extends FlagWatcher> wc = type.getWatcherClass();

        List<String> flags = new ArrayList<>(List.of(
                "setFire", "setInvisible", "setGlowing", "setSilent", "setNoGravity",
                "setCrouching", "setSprinting", "setSwimming",
                "setCustomName", "setCustomNameVisible"
        ));

        if (isAssignable(wc, LivingEntityWatcher.class)) {
            flags.add("setHealth");
        }
        if (isAssignable(wc, AgeableWatcher.class)) {
            flags.addAll(List.of("setBaby", "setAdult"));
        }
        if (wc == SheepWatcher.class || isAssignable(wc, SheepWatcher.class)) {
            flags.addAll(List.of("setColor", "setSheared"));
        }
        if (wc == CreeperWatcher.class) {
            flags.addAll(List.of("setPowered", "setIgnited"));
        }
        if (wc == SlimeWatcher.class) {
            flags.add("setSize");
        }
        if (wc == WolfWatcher.class) {
            flags.addAll(List.of("setTamed", "setSitting", "setAngry", "setCollarColor"));
        }
        if (wc == EndermanWatcher.class) {
            flags.add("setScreaming");
        }
        if (isAssignable(wc, BlockStateWatcher.class)) {
            flags.add("setBlock");
        }
        if (wc == MinecartWatcher.class) {
            flags.add("setDisplayOffset");
        }

        return flags;
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Reads the next token as a boolean only if it IS a boolean keyword.
     * If the next token is missing OR is not a boolean keyword, returns
     * {@code defaultValue} WITHOUT advancing the index.
     * This prevents consuming the next flag name as a value.
     */
    private static boolean nextBool(String[] tokens, int[] i, boolean defaultValue) {
        if (i[0] >= tokens.length) return defaultValue;
        return switch (tokens[i[0]].toLowerCase()) {
            case "true", "yes", "on", "1"  -> { i[0]++; yield true; }
            case "false", "no", "off", "0" -> { i[0]++; yield false; }
            default -> defaultValue;  // DO NOT advance i — next token is another flag
        };
    }

    /** Reads the next token as a string and advances the index, or returns null. */
    private static String nextString(String[] tokens, int[] i) {
        return (i[0] < tokens.length) ? tokens[i[0]++] : null;
    }

    /** Reads the next token as an int, advances if successful, returns default if not. */
    private static int nextInt(String[] tokens, int[] i, int defaultValue) {
        if (i[0] >= tokens.length) return defaultValue;
        try {
            int v = Integer.parseInt(tokens[i[0]]);
            i[0]++;
            return v;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Reads the next token as a float, advances if successful, returns default if not. */
    private static float nextFloat(String[] tokens, int[] i, float defaultValue) {
        if (i[0] >= tokens.length) return defaultValue;
        try {
            float v = Float.parseFloat(tokens[i[0]]);
            i[0]++;
            return v;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Looks up a Block by name using reflection on the Blocks class.
     *
     * Accepts both short names ("stone", "oak_log") and namespaced IDs
     * ("minecraft:stone") — the "minecraft:" prefix is stripped before lookup.
     * Field names on Blocks are upper-cased, so "oak_log" → "OAK_LOG".
     *
     * This avoids any dependency on ResourceLocation or BuiltInRegistries,
     * matching the reflection-first approach used by MetadataBuilder.
     *
     * Returns null if the block is not found.
     */
    private static Block parseBlock(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Strip namespace prefix
        String name = raw.toLowerCase();
        if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
        // Convert to upper-case field name (e.g. "oak_log" → "OAK_LOG")
        String fieldName = name.toUpperCase();
        try {
            java.lang.reflect.Field f = Blocks.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(null);
            if (val instanceof Block block) return block;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        return null;
    }
    private static DyeColor parseDyeColor(String raw) {
        try {
            return DyeColor.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try matching by getName() which can differ from enum name
            for (DyeColor c : DyeColor.values()) {
                if (c.getName().equalsIgnoreCase(raw) || c.name().equalsIgnoreCase(raw)) return c;
            }
            return null;
        }
    }

    private static String dyeColorList() {
        StringBuilder sb = new StringBuilder();
        DyeColor[] colors = DyeColor.values();
        for (int idx = 0; idx < colors.length; idx++) {
            if (idx > 0) sb.append(", ");
            sb.append(colors[idx].name());
        }
        return sb.toString();
    }

    private static boolean isAssignable(Class<?> child, Class<?> parent) {
        return parent.isAssignableFrom(child);
    }

    /** Returns true if the given flag keyword expects a value on the next token. */
    private static boolean flagExpectsValue(String flag) {
        return switch (flag.toLowerCase()) {
            case "setcolor", "color", "setcolour", "colour", "wool",
                 "setcollarcolor", "collarcolor", "collar",
                 "setcustomname", "customname", "name",
                 "sethealth", "health", "hp",
                 "setsize", "size", "sz",
                 "setblock", "block", "blocktype", "tile",
                 "setdisplayoffset", "displayoffset", "blockoffset", "offset" -> true;
            default -> false;
        };
    }

    /** Returns completion suggestions for the value of a specific flag. */
    /**
     * Cached sorted list of all block names, built once by reflecting on the Blocks class.
     * Using the same reflection strategy as parseBlock() — upper-case field name → lower-case id.
     */
    private static volatile List<String> ALL_BLOCK_NAMES = null;

    private static List<String> getAllBlockNames() {
        if (ALL_BLOCK_NAMES != null) return ALL_BLOCK_NAMES;
        List<String> names = new ArrayList<>();
        for (java.lang.reflect.Field f : Blocks.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!Block.class.isAssignableFrom(f.getType())) continue;
            // Convert UPPER_SNAKE field name to lower_snake block name
            names.add(f.getName().toLowerCase());
        }
        names.sort(String::compareTo);
        ALL_BLOCK_NAMES = names;
        return names;
    }

    private static List<String> suggestValuesFor(String flag, DisguiseType type, String partial) {
        List<String> values = switch (flag.toLowerCase()) {
            case "setcolor", "color", "setcolour", "colour", "wool",
                 "setcollarcolor", "collarcolor", "collar" -> {
                List<String> colors = new ArrayList<>();
                for (DyeColor c : DyeColor.values()) colors.add(c.name());
                yield colors;
            }
            case "sethealth", "health", "hp" ->
                    List.of("1", "5", "10", "20", "40", "100");
            case "setsize", "size", "sz" ->
                    List.of("1", "2", "3", "4");
            case "setblock", "block", "blocktype", "tile" ->
                    getAllBlockNames();
            case "setdisplayoffset", "displayoffset", "blockoffset", "offset" ->
                    List.of("0", "6", "8", "12", "16");
            default -> Collections.emptyList();
        };
        return filterByPrefix(values, partial);
    }

    private static List<String> filterByPrefix(List<String> candidates, String partial) {
        if (partial.isEmpty()) return candidates;
        String lp = partial.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(lp)) result.add(c);
        }
        return result;
    }
}