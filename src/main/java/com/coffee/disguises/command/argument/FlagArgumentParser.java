package com.coffee.disguises.command.argument;

import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.watcher.*;
import net.minecraft.world.item.DyeColor;

import java.util.*;

/**
 * Parses a space-separated flag chain into mutations on a FlagWatcher.
 *
 * Format: <flagName> [value] <flagName> [value] ...
 *
 * Example: "setColor RED setBaby setSize 3"
 *
 * Returns a list of errors for any unrecognised flags (logged as warnings,
 * not fatal — unknown flags are skipped).
 *
 * This is intentionally lenient: bad values produce a warning rather than
 * failing the entire command.
 */
public class FlagArgumentParser {

    /**
     * Apply flag tokens to the given watcher.
     *
     * @param watcher     the watcher to mutate (must already be the correct subtype)
     * @param type        the DisguiseType (used to validate which flags are applicable)
     * @param flagString  the raw flag string from the command argument
     * @return list of parse warnings (non-fatal)
     */
    public static List<String> apply(FlagWatcher watcher, DisguiseType type, String flagString) {
        List<String> warnings = new ArrayList<>();
        if (flagString == null || flagString.isBlank()) return warnings;

        String[] tokens = flagString.trim().split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String flag = tokens[i].toLowerCase();
            i++;

            switch (flag) {
                // ---- Universal flags ----
                case "setfire", "fire" ->
                        watcher.setOnFire(parseBool(tokens, i++, true));
                case "setinvisible", "invisible" ->
                        watcher.setInvisible(parseBool(tokens, i++, true));
                case "setglowing", "glowing" ->
                        watcher.setGlowing(parseBool(tokens, i++, true));
                case "setsilent", "silent" ->
                        watcher.setSilent(parseBool(tokens, i++, true));
                case "setnogravity", "nogravity" ->
                        watcher.setNoGravity(parseBool(tokens, i++, true));
                case "setcustomname", "customname" -> {
                    if (i < tokens.length) {
                        watcher.setCustomName(tokens[i++]);
                        watcher.setCustomNameVisible(true);
                    }
                }
                case "setcustomnamevisible", "shownametag" ->
                        watcher.setCustomNameVisible(parseBool(tokens, i++, true));

                // ---- AgeableMob ----
                case "setbaby", "baby" -> {
                    if (watcher instanceof AgeableWatcher aw) {
                        aw.setBaby(parseBool(tokens, i++, true));
                    } else warnings.add("'baby' is not applicable to " + type.getId());
                }

                // ---- Sheep ----
                case "setcolor", "color", "setcolour", "colour" -> {
                    if (watcher instanceof SheepWatcher sw) {
                        if (i < tokens.length) {
                            try {
                                sw.setColor(DyeColor.valueOf(tokens[i++].toUpperCase()));
                            } catch (IllegalArgumentException e) {
                                warnings.add("Unknown dye color: " + tokens[i - 1]);
                            }
                        }
                    } else warnings.add("'color' is not applicable to " + type.getId());
                }
                case "setsheared", "sheared" -> {
                    if (watcher instanceof SheepWatcher sw) {
                        sw.setSheared(parseBool(tokens, i++, true));
                    } else warnings.add("'sheared' is not applicable to " + type.getId());
                }

                // ---- Creeper ----
                case "setpowered", "powered", "charged" -> {
                    if (watcher instanceof CreeperWatcher cw) {
                        cw.setPowered(parseBool(tokens, i++, true));
                    } else warnings.add("'powered' is not applicable to " + type.getId());
                }
                case "setignited", "ignited" -> {
                    if (watcher instanceof CreeperWatcher cw) {
                        cw.setIgnited(parseBool(tokens, i++, true));
                    } else warnings.add("'ignited' is not applicable to " + type.getId());
                }

                // ---- Slime / Magma Cube ----
                case "setsize", "size" -> {
                    if (watcher instanceof SlimeWatcher slw && i < tokens.length) {
                        try {
                            slw.setSize(Integer.parseInt(tokens[i++]));
                        } catch (NumberFormatException e) {
                            warnings.add("Invalid size value: " + tokens[i - 1]);
                        }
                    } else if (!(watcher instanceof SlimeWatcher)) {
                        warnings.add("'size' is not applicable to " + type.getId());
                    }
                }

                // ---- Wolf ----
                case "settamed", "tamed" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        ww.setTamed(parseBool(tokens, i++, true));
                    } else warnings.add("'tamed' is not applicable to " + type.getId());
                }
                case "setsitting", "sitting" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        ww.setSitting(parseBool(tokens, i++, true));
                    } else warnings.add("'sitting' is not applicable to " + type.getId());
                }
                case "setangry", "angry" -> {
                    if (watcher instanceof WolfWatcher ww) {
                        ww.setAngry(parseBool(tokens, i++, true));
                    } else warnings.add("'angry' is not applicable to " + type.getId());
                }
                case "setcollarcolor", "collarcolor" -> {
                    if (watcher instanceof WolfWatcher ww && i < tokens.length) {
                        try {
                            ww.setCollarColor(DyeColor.valueOf(tokens[i++].toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            warnings.add("Unknown dye color: " + tokens[i - 1]);
                        }
                    } else if (!(watcher instanceof WolfWatcher)) {
                        warnings.add("'collarColor' is not applicable to " + type.getId());
                    }
                }

                // ---- Enderman ----
                case "setscreaming", "screaming" -> {
                    if (watcher instanceof EndermanWatcher ew) {
                        ew.setScreaming(parseBool(tokens, i++, true));
                    } else warnings.add("'screaming' is not applicable to " + type.getId());
                }

                // ---- LivingEntity ----
                case "sethealth", "health" -> {
                    if (watcher instanceof LivingEntityWatcher lew && i < tokens.length) {
                        try {
                            lew.setHealth(Float.parseFloat(tokens[i++]));
                        } catch (NumberFormatException e) {
                            warnings.add("Invalid health value: " + tokens[i - 1]);
                        }
                    }
                }

                default -> {
                    warnings.add("Unknown flag: '" + flag + "' (skipped)");
                    // Do NOT consume the next token — it might be the next flag name
                }
            }
        }
        return warnings;
    }

    /** Parse an optional boolean from the next token. Returns defaultValue if token missing or not a boolean. */
    private static boolean parseBool(String[] tokens, int index, boolean defaultValue) {
        if (index >= tokens.length) return defaultValue;
        return switch (tokens[index].toLowerCase()) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> defaultValue;
        };
    }

    /** Returns all valid flag names for tab completion (optionally filtered by DisguiseType). */
    public static List<String> getValidFlags(DisguiseType type) {
        List<String> flags = new ArrayList<>(List.of(
                "setFire", "setInvisible", "setGlowing", "setSilent", "setNoGravity",
                "setCustomName", "setCustomNameVisible"
        ));
        Class<?> watcherClass = type.getWatcherClass();
        if (isAssignable(watcherClass, AgeableWatcher.class)) {
            flags.add("setBaby");
        }
        if (watcherClass == com.coffee.disguises.watcher.SheepWatcher.class) {
            flags.addAll(List.of("setColor", "setSheared"));
        }
        if (watcherClass == com.coffee.disguises.watcher.CreeperWatcher.class) {
            flags.addAll(List.of("setPowered", "setIgnited"));
        }
        if (watcherClass == com.coffee.disguises.watcher.SlimeWatcher.class) {
            flags.add("setSize");
        }
        if (watcherClass == com.coffee.disguises.watcher.WolfWatcher.class) {
            flags.addAll(List.of("setTamed", "setSitting", "setAngry", "setCollarColor"));
        }
        if (watcherClass == com.coffee.disguises.watcher.EndermanWatcher.class) {
            flags.add("setScreaming");
        }
        return flags;
    }

    private static boolean isAssignable(Class<?> child, Class<?> parent) {
        return parent.isAssignableFrom(child);
    }
}
