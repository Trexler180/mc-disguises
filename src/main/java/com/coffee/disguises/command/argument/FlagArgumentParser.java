package com.coffee.disguises.command.argument;

import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.watcher.*;
import com.coffee.disguises.watcher.BlockStateWatcher;
import com.coffee.disguises.watcher.MinecartWatcher;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

        String[] tokens = tokenize(flagString);
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
                        watcher.setCustomName(name);
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
                    } else if (watcher instanceof FoxWatcher fxw) {
                        fxw.setSitting(nextBool(tokens, i, true));
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
                    } else if (watcher instanceof GoatWatcher gw) {
                        gw.setScreaming(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setScreaming' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Phantom ───────────────────────────────────────────────────

                case "setphantomsize", "phantomsize", "psize" -> {
                    if (watcher instanceof PhantomWatcher phw) {
                        int sz = nextInt(tokens, i, -1);
                        if (sz < 0) warnings.add("'setPhantomSize' requires a non-negative integer.");
                        else phw.setSize(sz);
                    } else {
                        warnings.add("'setPhantomSize' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Goat ─────────────────────────────────────────────────────

                case "setlefthorn", "lefthorn" -> {
                    if (watcher instanceof GoatWatcher gw) {
                        gw.setHasLeftHorn(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setLeftHorn' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setrighthorn", "righthorn" -> {
                    if (watcher instanceof GoatWatcher gw) {
                        gw.setHasRightHorn(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setRightHorn' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Bee ───────────────────────────────────────────────────────

                case "setangertime", "angertime" -> {
                    if (watcher instanceof BeeWatcher bw) {
                        int t = nextInt(tokens, i, -1);
                        if (t < 0) warnings.add("'setAngerTime' requires a non-negative integer (ticks).");
                        else bw.setRemainingAngerTime(t);
                    } else {
                        warnings.add("'setAngerTime' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setstung", "stung" -> {
                    if (watcher instanceof BeeWatcher bw) {
                        bw.setHasStung(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setStung' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setnectar", "nectar", "pollinating" -> {
                    if (watcher instanceof BeeWatcher bw) {
                        bw.setHasNectar(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setNectar' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Strider ───────────────────────────────────────────────────

                case "setshaking", "shaking" -> {
                    if (watcher instanceof StriderWatcher strw) {
                        strw.setShaking(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setShaking' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setsaddled", "saddled", "saddle" -> {
                    if (watcher instanceof StriderWatcher strw) {
                        strw.setSaddled(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setSaddled' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Piglin ────────────────────────────────────────────────────

                case "setdancing", "dancing" -> {
                    if (watcher instanceof PiglinWatcher piw) {
                        piw.setDancing(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setDancing' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcelebrating", "celebrating" -> {
                    if (watcher instanceof PiglinWatcher piw) {
                        piw.setCelebrating(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setCelebrating' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setchargingcrossbow", "chargingcrossbow", "charging" -> {
                    if (watcher instanceof PiglinWatcher piw) {
                        piw.setChargingCrossbow(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setChargingCrossbow' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── ArmorStand ────────────────────────────────────────────────

                case "setsmall", "small" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        asw.setSmall(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setSmall' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setshowarms", "showarms", "arms" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        asw.setShowArms(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setShowArms' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setnobaseplate", "nobaseplate", "hidebase" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        asw.setNoBasePlate(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setNoBasePlate' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Parrot ────────────────────────────────────────────────────

                case "setparrotvariant", "parrotvariant", "parrottype" -> {
                    if (watcher instanceof ParrotWatcher parw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, PARROT_VARIANTS);
                        if (v < 0) warnings.add("'setParrotVariant' requires 0-4 or: red, blue, green, cyan, gray.");
                        else parw.setVariant(v);
                    } else {
                        warnings.add("'setParrotVariant' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── TropicalFish ──────────────────────────────────────────────

                case "setbodycolor", "bodycolor" -> {
                    if (watcher instanceof TropicalFishWatcher tfw) {
                        String raw = nextString(tokens, i);
                        DyeColor color = raw != null ? parseDyeColor(raw) : null;
                        if (color == null) warnings.add("'setBodyColor' requires a dye color name.");
                        else tfw.setBodyColor(color);
                    } else {
                        warnings.add("'setBodyColor' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setpatterncolor", "patterncolor" -> {
                    if (watcher instanceof TropicalFishWatcher tfw) {
                        String raw = nextString(tokens, i);
                        DyeColor color = raw != null ? parseDyeColor(raw) : null;
                        if (color == null) warnings.add("'setPatternColor' requires a dye color name.");
                        else tfw.setPatternColor(color);
                    } else {
                        warnings.add("'setPatternColor' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setfishshape", "fishshape", "fishpattern" -> {
                    if (watcher instanceof TropicalFishWatcher tfw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, TROPICAL_FISH_SHAPES);
                        if (v < 0) warnings.add("'setFishShape' requires 0-5 or: flopper, stripey, glitter, blockfish, betty, clayfish.");
                        else tfw.setPatternShape(v);
                    } else {
                        warnings.add("'setFishShape' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setlargefish", "largefish", "largefishvariant" -> {
                    if (watcher instanceof TropicalFishWatcher tfw) {
                        tfw.setLarge(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setLargeFish' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Rabbit ────────────────────────────────────────────────────

                case "setrabbittype", "rabbittype" -> {
                    if (watcher instanceof RabbitWatcher rw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, RABBIT_TYPES);
                        if (v < 0) warnings.add("'setRabbitType' requires 0-5, 99, or: brown, white, black, black_white, gold, salt, killer.");
                        else rw.setType(v);
                    } else {
                        warnings.add("'setRabbitType' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Fox ───────────────────────────────────────────────────────

                case "setfoxtype", "foxtype" -> {
                    if (watcher instanceof FoxWatcher fxw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, FOX_TYPES);
                        if (v < 0) warnings.add("'setFoxType' requires 0-1 or: red, snow.");
                        else fxw.setType(v);
                    } else {
                        warnings.add("'setFoxType' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setsleeping", "sleeping", "sleep" -> {
                    if (watcher instanceof FoxWatcher fxw) {
                        fxw.setSleeping(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setSleeping' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setinterested", "interested" -> {
                    if (watcher instanceof FoxWatcher fxw) {
                        fxw.setInterested(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setInterested' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcrouching_fox", "foxcrouch" -> {
                    if (watcher instanceof FoxWatcher fxw) {
                        fxw.setCrouching(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'foxCrouch' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Panda ─────────────────────────────────────────────────────

                case "setmaingene", "maingene" -> {
                    if (watcher instanceof PandaWatcher pdw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, PANDA_GENES);
                        if (v < 0) warnings.add("'setMainGene' requires 0-6 or: normal, lazy, worried, playful, brown, weak, aggressive.");
                        else pdw.setMainGene(v);
                    } else {
                        warnings.add("'setMainGene' is not applicable to " + type.getId() + ".");
                    }
                }

                case "sethiddengene", "hiddengene" -> {
                    if (watcher instanceof PandaWatcher pdw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, PANDA_GENES);
                        if (v < 0) warnings.add("'setHiddenGene' requires 0-6 or: normal, lazy, worried, playful, brown, weak, aggressive.");
                        else pdw.setHiddenGene(v);
                    } else {
                        warnings.add("'setHiddenGene' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Axolotl ───────────────────────────────────────────────────

                case "setaxolotlvariant", "axolotlvariant", "axolotltype" -> {
                    if (watcher instanceof AxolotlWatcher axw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, AXOLOTL_VARIANTS);
                        if (v < 0) warnings.add("'setAxolotlVariant' requires 0-4 or: lucy, wild, gold, cyan, blue.");
                        else axw.setVariant(v);
                    } else {
                        warnings.add("'setAxolotlVariant' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setplayingdead", "playingdead", "playdead" -> {
                    if (watcher instanceof AxolotlWatcher axw) {
                        axw.setPlayingDead(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setPlayingDead' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Horse ────────────────────────────────────────────────────────

                case "sethorsecolor", "horsecolor", "horsecolour" -> {
                    if (watcher instanceof HorseWatcher hw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, HORSE_COLORS);
                        if (v < 0) warnings.add("'setHorseColor' requires 0-6 or: white, creamy, chestnut, brown, black, gray, dark_brown.");
                        else hw.setColor(v);
                    } else {
                        warnings.add("'setHorseColor' is not applicable to " + type.getId() + ".");
                    }
                }

                case "sethorsestyle", "horsestyle", "horsemarkings" -> {
                    if (watcher instanceof HorseWatcher hw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, HORSE_STYLES);
                        if (v < 0) warnings.add("'setHorseStyle' requires 0-4 or: none, white, white_field, white_dots, black_dots.");
                        else hw.setMarkings(v);
                    } else {
                        warnings.add("'setHorseStyle' is not applicable to " + type.getId() + ".");
                    }
                }

                case "sethorsetamed", "horsetamed" -> {
                    if (watcher instanceof HorseWatcher hw) {
                        hw.setTame(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setHorseTamed' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Llama ─────────────────────────────────────────────────────

                case "setchest", "chest", "haschest" -> {
                    if (watcher instanceof LlamaWatcher lw) {
                        lw.setHasChest(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setChest' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setstrength", "strength" -> {
                    if (watcher instanceof LlamaWatcher lw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 1 || v > 5) warnings.add("'setStrength' requires 1-5.");
                        else lw.setStrength(v);
                    } else {
                        warnings.add("'setStrength' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcarpetcolor", "carpetcolor", "carpet" -> {
                    if (watcher instanceof LlamaWatcher lw) {
                        String raw = nextString(tokens, i);
                        if ("none".equalsIgnoreCase(raw)) {
                            lw.setCarpetColor(-1);
                        } else {
                            DyeColor color = raw != null ? parseDyeColor(raw) : null;
                            if (color == null) warnings.add("'setCarpetColor' requires a dye color name or 'none'.");
                            else lw.setCarpetColor(color.getId());
                        }
                    } else {
                        warnings.add("'setCarpetColor' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setllamavariant", "llamavariant", "llamatype", "llamacolor" -> {
                    if (watcher instanceof LlamaWatcher lw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, LLAMA_VARIANTS);
                        if (v < 0) warnings.add("'setLlamaVariant' requires 0-3 or: creamy, white, brown, gray.");
                        else lw.setVariant(v);
                    } else {
                        warnings.add("'setLlamaVariant' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Cat ───────────────────────────────────────────────────────

                case "setcatvariant", "catvariant", "cattype" -> {
                    if (watcher instanceof CatWatcher catw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, CAT_VARIANTS);
                        if (v < 0) warnings.add("'setCatVariant' requires 0-10 or: tabby, black, red, siamese, british_shorthair, calico, persian, ragdoll, white, jellie, all_black.");
                        else catw.setVariant(v);
                    } else {
                        warnings.add("'setCatVariant' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcattamed", "cattamed" -> {
                    if (watcher instanceof CatWatcher catw) {
                        catw.setTame(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setCatTamed' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcatsitting", "catsitting" -> {
                    if (watcher instanceof CatWatcher catw) {
                        catw.setSitting(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setCatSitting' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setlying", "lying", "liedown" -> {
                    if (watcher instanceof CatWatcher catw) {
                        catw.setLying(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setLying' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setrelaxed", "relaxed", "relax" -> {
                    if (watcher instanceof CatWatcher catw) {
                        catw.setRelaxed(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setRelaxed' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setcatcollar", "catcollar", "catcollarcolor" -> {
                    if (watcher instanceof CatWatcher catw) {
                        String raw = nextString(tokens, i);
                        DyeColor color = raw != null ? parseDyeColor(raw) : null;
                        if (color == null) warnings.add("'setCatCollar' requires a dye color name.");
                        else catw.setCollarColor(color);
                    } else {
                        warnings.add("'setCatCollar' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Frog ─────────────────────────────────────────────────────

                case "setfrogvariant", "frogvariant", "frogtype" -> {
                    if (watcher instanceof FrogWatcher frogw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, FROG_VARIANTS);
                        if (v < 0) warnings.add("'setFrogVariant' requires 0-2 or: temperate, warm, cold.");
                        else frogw.setVariant(v);
                    } else {
                        warnings.add("'setFrogVariant' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Mooshroom ─────────────────────────────────────────────────

                case "setmooshroomtype", "mooshroomtype", "mushroomtype" -> {
                    if (watcher instanceof MooshroomWatcher mw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, MOOSHROOM_TYPES);
                        if (v < 0) warnings.add("'setMooshroomType' requires 0-1 or: brown, red.");
                        else mw.setVariant(v);
                    } else {
                        warnings.add("'setMooshroomType' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Villager / ZombieVillager ─────────────────────────────────

                case "setvillagertype", "villagertype", "villagerbiome" -> {
                    if (watcher instanceof VillagerWatcher vw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, VILLAGER_TYPES);
                        if (v < 0) warnings.add("'setVillagerType' requires 0-6 or: desert, jungle, plains, savanna, snow, swamp, taiga.");
                        else vw.setType(v);
                    } else if (watcher instanceof ZombieVillagerWatcher zvw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, VILLAGER_TYPES);
                        if (v < 0) warnings.add("'setVillagerType' requires 0-6 or: desert, jungle, plains, savanna, snow, swamp, taiga.");
                        else zvw.setType(v);
                    } else {
                        warnings.add("'setVillagerType' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setprofession", "profession" -> {
                    if (watcher instanceof VillagerWatcher vw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, VILLAGER_PROFESSIONS);
                        if (v < 0) warnings.add("'setProfession' requires 0-14 or a profession name.");
                        else vw.setProfession(v);
                    } else if (watcher instanceof ZombieVillagerWatcher zvw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, VILLAGER_PROFESSIONS);
                        if (v < 0) warnings.add("'setProfession' requires 0-14 or a profession name.");
                        else zvw.setProfession(v);
                    } else {
                        warnings.add("'setProfession' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setvillagerlevel", "villagerlevel", "traderlevel" -> {
                    if (watcher instanceof VillagerWatcher vw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 1 || v > 5) warnings.add("'setVillagerLevel' requires 1-5 (novice to master).");
                        else vw.setLevel(v);
                    } else if (watcher instanceof ZombieVillagerWatcher zvw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 1 || v > 5) warnings.add("'setVillagerLevel' requires 1-5 (novice to master).");
                        else zvw.setLevel(v);
                    } else {
                        warnings.add("'setVillagerLevel' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setconverting", "converting" -> {
                    if (watcher instanceof ZombieVillagerWatcher zvw) {
                        zvw.setConverting(nextBool(tokens, i, true));
                    } else {
                        warnings.add("'setConverting' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── Shulker ───────────────────────────────────────────────────

                case "setattachface", "attachface", "face" -> {
                    if (watcher instanceof ShulkerWatcher shuw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0) v = parseNamedVariant(tokens, i, DIRECTIONS);
                        if (v < 0) warnings.add("'setAttachFace' requires 0-5 or: down, up, north, south, west, east.");
                        else shuw.setAttachFace(v);
                    } else {
                        warnings.add("'setAttachFace' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setpeek", "peek", "openamount" -> {
                    if (watcher instanceof ShulkerWatcher shuw) {
                        int v = nextInt(tokens, i, -1);
                        if (v < 0 || v > 100) warnings.add("'setPeek' requires 0-100.");
                        else shuw.setPeek(v);
                    } else {
                        warnings.add("'setPeek' is not applicable to " + type.getId() + ".");
                    }
                }

                case "setshulkercolor", "shulkercolor", "shulkercolour" -> {
                    if (watcher instanceof ShulkerWatcher shuw) {
                        String raw = nextString(tokens, i);
                        if ("default".equalsIgnoreCase(raw) || "purple".equalsIgnoreCase(raw) || "none".equalsIgnoreCase(raw)) {
                            shuw.setColor(ShulkerWatcher.COLOR_DEFAULT);
                        } else {
                            DyeColor color = raw != null ? parseDyeColor(raw) : null;
                            if (color == null) warnings.add("'setShulkerColor' requires a dye color name or 'default'.");
                            else shuw.setColor(color.getId());
                        }
                    } else {
                        warnings.add("'setShulkerColor' is not applicable to " + type.getId() + ".");
                    }
                }

                // ── ArmorStand pose ───────────────────────────────────────────

                case "setheadpose", "headpose" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        float x = nextFloat(tokens, i, 0); float y = nextFloat(tokens, i, 0); float z = nextFloat(tokens, i, 0);
                        asw.setHeadPose(x, y, z);
                    } else warnings.add("'setHeadPose' is not applicable to " + type.getId() + ".");
                }

                case "setbodypose", "bodypose" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        float x = nextFloat(tokens, i, 0); float y = nextFloat(tokens, i, 0); float z = nextFloat(tokens, i, 0);
                        asw.setBodyPose(x, y, z);
                    } else warnings.add("'setBodyPose' is not applicable to " + type.getId() + ".");
                }

                case "setleftarmpose", "leftarmpose" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        float x = nextFloat(tokens, i, 0); float y = nextFloat(tokens, i, 0); float z = nextFloat(tokens, i, 0);
                        asw.setLeftArmPose(x, y, z);
                    } else warnings.add("'setLeftArmPose' is not applicable to " + type.getId() + ".");
                }

                case "setrightarmpose", "rightarmpose" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        float x = nextFloat(tokens, i, 0); float y = nextFloat(tokens, i, 0); float z = nextFloat(tokens, i, 0);
                        asw.setRightArmPose(x, y, z);
                    } else warnings.add("'setRightArmPose' is not applicable to " + type.getId() + ".");
                }

                case "setleftlegpose", "leftlegpose" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        float x = nextFloat(tokens, i, 0); float y = nextFloat(tokens, i, 0); float z = nextFloat(tokens, i, 0);
                        asw.setLeftLegPose(x, y, z);
                    } else warnings.add("'setLeftLegPose' is not applicable to " + type.getId() + ".");
                }

                case "setrightlegpose", "rightlegpose" -> {
                    if (watcher instanceof ArmorStandWatcher asw) {
                        float x = nextFloat(tokens, i, 0); float y = nextFloat(tokens, i, 0); float z = nextFloat(tokens, i, 0);
                        asw.setRightLegPose(x, y, z);
                    } else warnings.add("'setRightLegPose' is not applicable to " + type.getId() + ".");
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

                // ── Custom equipment ──────────────────────────────────────────────
                // Works on any LivingEntity disguise (anything that can wear/hold items).

                case "sethelmet", "helmet" -> {
                    String raw = nextString(tokens, i);
                    ItemStack item = raw != null ? parseItem(raw) : null;
                    if (item == null) warnings.add("'setHelmet' requires a valid item name (e.g. diamond_helmet).");
                    else watcher.setCustomEquipment(EquipmentSlot.HEAD, item);
                }

                case "setchestplate", "chestplate" -> {
                    String raw = nextString(tokens, i);
                    ItemStack item = raw != null ? parseItem(raw) : null;
                    if (item == null) warnings.add("'setChestplate' requires a valid item name (e.g. iron_chestplate).");
                    else watcher.setCustomEquipment(EquipmentSlot.CHEST, item);
                }

                case "setleggings", "leggings" -> {
                    String raw = nextString(tokens, i);
                    ItemStack item = raw != null ? parseItem(raw) : null;
                    if (item == null) warnings.add("'setLeggings' requires a valid item name (e.g. golden_leggings).");
                    else watcher.setCustomEquipment(EquipmentSlot.LEGS, item);
                }

                case "setboots", "boots" -> {
                    String raw = nextString(tokens, i);
                    ItemStack item = raw != null ? parseItem(raw) : null;
                    if (item == null) warnings.add("'setBoots' requires a valid item name (e.g. leather_boots).");
                    else watcher.setCustomEquipment(EquipmentSlot.FEET, item);
                }

                case "setmainhand", "mainhand", "setitem", "item" -> {
                    String raw = nextString(tokens, i);
                    ItemStack item = raw != null ? parseItem(raw) : null;
                    if (item == null) warnings.add("'setItem'/'setMainHand' requires a valid item name (e.g. diamond).");
                    else if (watcher instanceof ItemWatcher iw) iw.setItem(item);
                    else watcher.setCustomEquipment(EquipmentSlot.MAINHAND, item);
                }

                case "setoffhand", "offhand" -> {
                    String raw = nextString(tokens, i);
                    ItemStack item = raw != null ? parseItem(raw) : null;
                    if (item == null) warnings.add("'setOffHand' requires a valid item name (e.g. shield).");
                    else watcher.setCustomEquipment(EquipmentSlot.OFFHAND, item);
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
        if (wc == EndermanWatcher.class || wc == GoatWatcher.class) {
            flags.add("setScreaming");
        }
        if (wc == PhantomWatcher.class) {
            flags.add("setPhantomSize");
        }
        if (wc == GoatWatcher.class) {
            flags.addAll(List.of("setLeftHorn", "setRightHorn"));
        }
        if (wc == BeeWatcher.class) {
            flags.addAll(List.of("setAngerTime", "setStung", "setNectar"));
        }
        if (wc == StriderWatcher.class) {
            flags.addAll(List.of("setShaking", "setSaddled"));
        }
        if (wc == PiglinWatcher.class) {
            flags.addAll(List.of("setDancing", "setCelebrating", "setChargingCrossbow"));
        }
        if (wc == ArmorStandWatcher.class) {
            flags.addAll(List.of("setSmall", "setShowArms", "setNoBasePlate"));
        }
        if (wc == ParrotWatcher.class) {
            flags.add("setParrotVariant");
        }
        if (wc == TropicalFishWatcher.class) {
            flags.addAll(List.of("setBodyColor", "setPatternColor", "setFishShape", "setLargeFish"));
        }
        if (wc == RabbitWatcher.class) {
            flags.add("setRabbitType");
        }
        if (wc == FoxWatcher.class) {
            flags.addAll(List.of("setSitting", "setFoxType", "setSleeping", "setInterested", "foxCrouch"));
        }
        if (wc == PandaWatcher.class) {
            flags.addAll(List.of("setMainGene", "setHiddenGene"));
        }
        if (wc == AxolotlWatcher.class) {
            flags.addAll(List.of("setAxolotlVariant", "setPlayingDead"));
        }
        if (wc == HorseWatcher.class) {
            flags.addAll(List.of("setHorseColor", "setHorseStyle", "setHorseTamed"));
        }
        if (wc == LlamaWatcher.class) {
            flags.addAll(List.of("setChest", "setStrength", "setCarpetColor", "setLlamaVariant"));
        }
        if (wc == CatWatcher.class) {
            flags.addAll(List.of("setCatVariant", "setCatTamed", "setCatSitting", "setLying", "setRelaxed", "setCatCollar"));
        }
        if (wc == FrogWatcher.class) {
            flags.add("setFrogVariant");
        }
        if (wc == MooshroomWatcher.class) {
            flags.add("setMooshroomType");
        }
        if (wc == VillagerWatcher.class) {
            flags.addAll(List.of("setVillagerType", "setProfession", "setVillagerLevel"));
        }
        if (wc == ZombieVillagerWatcher.class) {
            flags.addAll(List.of("setConverting", "setVillagerType", "setProfession", "setVillagerLevel"));
        }
        if (wc == ShulkerWatcher.class) {
            flags.addAll(List.of("setAttachFace", "setPeek", "setShulkerColor"));
        }
        if (wc == ArmorStandWatcher.class) {
            flags.addAll(List.of("setHeadPose", "setBodyPose", "setLeftArmPose", "setRightArmPose",
                    "setLeftLegPose", "setRightLegPose"));
        }
        if (isAssignable(wc, BlockStateWatcher.class)) {
            flags.add("setBlock");
        }
        if (wc == MinecartWatcher.class) {
            flags.add("setDisplayOffset");
        }
        // Item entity disguise: setItem sets the displayed item stack
        if (wc == ItemWatcher.class) {
            flags.add("setItem");
        }
        // Custom equipment is available on all living-entity disguises
        if (!type.isInanimate() || wc == ArmorStandWatcher.class) {
            flags.addAll(List.of("setHelmet", "setChestplate", "setLeggings", "setBoots",
                    "setMainHand", "setOffHand"));
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

    /**
     * Splits a flag string on whitespace, but keeps double-quoted segments
     * as a single token (with embedded spaces preserved).  An escaped quote
     * {@code \"} inside a quoted run becomes a literal {@code "}.
     *
     * Example:
     *   setCustomName "Lord Notch" setHealth 20
     * yields tokens: ["setCustomName", "Lord Notch", "setHealth", "20"].
     *
     * Unterminated quotes are tolerated — the run extends to end-of-input.
     */
    static String[] tokenize(String flagString) {
        if (flagString == null) return new String[0];
        String s = flagString.trim();
        if (s.isEmpty()) return new String[0];

        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;

        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (inQuote) {
                if (c == '\\' && k + 1 < s.length() && s.charAt(k + 1) == '"') {
                    cur.append('"');
                    k++;
                } else if (c == '"') {
                    inQuote = false;
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    if (cur.length() > 0) {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                    inQuote = true;
                } else if (Character.isWhitespace(c)) {
                    if (cur.length() > 0) {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                } else {
                    cur.append(c);
                }
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
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
     * Looks up an Item by name using reflection on the Items class.
     * Accepts short names ("diamond_sword") and namespaced IDs ("minecraft:diamond_sword").
     * Returns a single-count ItemStack, or null if not found.
     */
    private static ItemStack parseItem(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw.toLowerCase();
        if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
        if ("air".equals(name) || "none".equals(name)) return ItemStack.EMPTY;
        String fieldName = name.toUpperCase();
        try {
            java.lang.reflect.Field f = Items.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(null);
            if (val instanceof Item item) return new ItemStack(item);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        return null;
    }

    /** Cached sorted list of all item names, built once by reflecting on the Items class. */
    private static volatile List<String> ALL_ITEM_NAMES = null;

    private static List<String> getAllItemNames() {
        if (ALL_ITEM_NAMES != null) return ALL_ITEM_NAMES;
        List<String> names = new ArrayList<>();
        for (java.lang.reflect.Field f : Items.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!Item.class.isAssignableFrom(f.getType())) continue;
            names.add(f.getName().toLowerCase());
        }
        names.sort(String::compareTo);
        ALL_ITEM_NAMES = names;
        return names;
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
    // ── Named variant lookup tables ───────────────────────────────────────────

    private static final Map<String, Integer> HORSE_COLORS = Map.of(
            "white", 0, "creamy", 1, "chestnut", 2, "brown", 3,
            "black", 4, "gray", 5, "dark_brown", 6
    );
    private static final Map<String, Integer> HORSE_STYLES = Map.of(
            "none", 0, "white", 1, "white_field", 2, "white_dots", 3, "black_dots", 4
    );
    private static final Map<String, Integer> LLAMA_VARIANTS = Map.of(
            "creamy", 0, "white", 1, "brown", 2, "gray", 3
    );
    private static final Map<String, Integer> CAT_VARIANTS = new HashMap<>(Map.of(
            "tabby", 0, "black", 1, "red", 2, "siamese", 3,
            "british_shorthair", 4, "calico", 5, "persian", 6,
            "ragdoll", 7, "white", 8, "jellie", 9
    )) {{ put("all_black", 10); }};
    private static final Map<String, Integer> FROG_VARIANTS = Map.of(
            "temperate", 0, "warm", 1, "cold", 2
    );
    private static final Map<String, Integer> MOOSHROOM_TYPES = Map.of(
            "brown", 0, "red", 1
    );
    private static final Map<String, Integer> VILLAGER_TYPES = Map.of(
            "desert", 0, "jungle", 1, "plains", 2, "savanna", 3,
            "snow", 4, "swamp", 5, "taiga", 6
    );
    private static final Map<String, Integer> VILLAGER_PROFESSIONS = new HashMap<>(Map.of(
            "none", 0, "armorer", 1, "butcher", 2, "cartographer", 3, "cleric", 4,
            "farmer", 5, "fisherman", 6, "fletcher", 7, "leatherworker", 8, "librarian", 9
    )) {{
        put("mason", 10); put("nitwit", 11); put("shepherd", 12);
        put("toolsmith", 13); put("weaponsmith", 14);
    }};
    private static final Map<String, Integer> DIRECTIONS = Map.of(
            "down", 0, "up", 1, "north", 2, "south", 3, "west", 4, "east", 5
    );

    private static final Map<String, Integer> PARROT_VARIANTS = Map.of(
            "red", 0, "blue", 1, "green", 2, "cyan", 3, "gray", 4
    );
    private static final Map<String, Integer> TROPICAL_FISH_SHAPES = Map.of(
            "flopper", 0, "stripey", 1, "glitter", 2, "blockfish", 3, "betty", 4, "clayfish", 5
    );
    private static final Map<String, Integer> RABBIT_TYPES = new HashMap<>(Map.of(
            "brown", 0, "white", 1, "black", 2, "black_white", 3,
            "gold", 4, "salt", 5, "killer", 99
    ));
    private static final Map<String, Integer> FOX_TYPES = Map.of(
            "red", 0, "snow", 1
    );
    private static final Map<String, Integer> PANDA_GENES = Map.of(
            "normal", 0, "lazy", 1, "worried", 2, "playful", 3,
            "brown", 4, "weak", 5, "aggressive", 6
    );
    private static final Map<String, Integer> AXOLOTL_VARIANTS = Map.of(
            "lucy", 0, "wild", 1, "gold", 2, "cyan", 3, "blue", 4
    );

    /**
     * Tries to read the next token as a named variant from the given map.
     * Returns the mapped int, or -1 if the next token is absent or not in the map
     * (without consuming the token).
     */
    private static int parseNamedVariant(String[] tokens, int[] i, Map<String, Integer> map) {
        if (i[0] >= tokens.length) return -1;
        Integer v = map.get(tokens[i[0]].toLowerCase());
        if (v != null) { i[0]++; return v; }
        return -1;
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
                 "setdisplayoffset", "displayoffset", "blockoffset", "offset",
                 "setphantomsize", "phantomsize", "psize",
                 "setangertime", "angertime",
                 "setparrotvariant", "parrotvariant", "parrottype",
                 "setbodycolor", "bodycolor",
                 "setpatterncolor", "patterncolor",
                 "setfishshape", "fishshape", "fishpattern",
                 "setrabbittype", "rabbittype",
                 "setfoxtype", "foxtype",
                 "setmaingene", "maingene",
                 "sethiddengene", "hiddengene",
                 "setaxolotlvariant", "axolotlvariant", "axolotltype",
                 "sethorsecolor", "horsecolor", "horsecolour",
                 "sethorsestyle", "horsestyle", "horsemarkings",
                 "setstrength", "strength",
                 "setcarpetcolor", "carpetcolor", "carpet",
                 "setllamavariant", "llamavariant", "llamatype", "llamacolor",
                 "setcatvariant", "catvariant", "cattype",
                 "setcatcollar", "catcollar", "catcollarcolor",
                 "setfrogvariant", "frogvariant", "frogtype",
                 "setmooshroomtype", "mooshroomtype", "mushroomtype",
                 "setvillagertype", "villagertype", "villagerbiome",
                 "setprofession", "profession",
                 "setvillagerlevel", "villagerlevel", "traderlevel",
                 "setattachface", "attachface", "face",
                 "setpeek", "peek", "openamount",
                 "setshulkercolor", "shulkercolor", "shulkercolour",
                 "sethelmet", "helmet",
                 "setchestplate", "chestplate",
                 "setleggings", "leggings",
                 "setboots", "boots",
                 "setmainhand", "mainhand", "setitem", "item",
                 "setoffhand", "offhand" -> true;
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
        List<String> dyeNames = new ArrayList<>();
        for (DyeColor c : DyeColor.values()) dyeNames.add(c.name());

        List<String> values = switch (flag.toLowerCase()) {
            case "setcolor", "color", "setcolour", "colour", "wool",
                 "setcollarcolor", "collarcolor", "collar",
                 "setbodycolor", "bodycolor",
                 "setpatterncolor", "patterncolor" -> dyeNames;
            case "sethealth", "health", "hp" ->
                    List.of("1", "5", "10", "20", "40", "100");
            case "setsize", "size", "sz" ->
                    List.of("1", "2", "3", "4");
            case "setblock", "block", "blocktype", "tile" ->
                    getAllBlockNames();
            case "setdisplayoffset", "displayoffset", "blockoffset", "offset" ->
                    List.of("0", "6", "8", "12", "16");
            case "setphantomsize", "phantomsize", "psize" ->
                    List.of("0", "1", "2", "3", "4");
            case "setangertime", "angertime" ->
                    List.of("0", "200", "400", "600");
            case "setparrotvariant", "parrotvariant", "parrottype" ->
                    List.of("red", "blue", "green", "cyan", "gray");
            case "setfishshape", "fishshape", "fishpattern" ->
                    List.of("flopper", "stripey", "glitter", "blockfish", "betty", "clayfish");
            case "setrabbittype", "rabbittype" ->
                    List.of("brown", "white", "black", "black_white", "gold", "salt", "killer");
            case "setfoxtype", "foxtype" ->
                    List.of("red", "snow");
            case "setmaingene", "maingene",
                 "sethiddengene", "hiddengene" ->
                    List.of("normal", "lazy", "worried", "playful", "brown", "weak", "aggressive");
            case "setaxolotlvariant", "axolotlvariant", "axolotltype" ->
                    List.of("lucy", "wild", "gold", "cyan", "blue");
            case "sethorsecolor", "horsecolor", "horsecolour" ->
                    List.of("white", "creamy", "chestnut", "brown", "black", "gray", "dark_brown");
            case "sethorsestyle", "horsestyle", "horsemarkings" ->
                    List.of("none", "white", "white_field", "white_dots", "black_dots");
            case "setstrength", "strength" ->
                    List.of("1", "2", "3", "4", "5");
            case "setcarpetcolor", "carpetcolor", "carpet" -> {
                List<String> r = new ArrayList<>(dyeNames); r.add(0, "none"); yield r;
            }
            case "setllamavariant", "llamavariant", "llamatype", "llamacolor" ->
                    List.of("creamy", "white", "brown", "gray");
            case "setcatvariant", "catvariant", "cattype" ->
                    List.of("tabby", "black", "red", "siamese", "british_shorthair", "calico",
                            "persian", "ragdoll", "white", "jellie", "all_black");
            case "setcatcollar", "catcollar", "catcollarcolor" -> dyeNames;
            case "setfrogvariant", "frogvariant", "frogtype" ->
                    List.of("temperate", "warm", "cold");
            case "setmooshroomtype", "mooshroomtype", "mushroomtype" ->
                    List.of("brown", "red");
            case "setvillagertype", "villagertype", "villagerbiome" ->
                    List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga");
            case "setprofession", "profession" ->
                    List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer",
                            "fisherman", "fletcher", "leatherworker", "librarian", "mason",
                            "nitwit", "shepherd", "toolsmith", "weaponsmith");
            case "setvillagerlevel", "villagerlevel", "traderlevel" ->
                    List.of("1", "2", "3", "4", "5");
            case "setattachface", "attachface", "face" ->
                    List.of("down", "up", "north", "south", "west", "east");
            case "setpeek", "peek", "openamount" ->
                    List.of("0", "25", "50", "75", "100");
            case "setshulkercolor", "shulkercolor", "shulkercolour" -> {
                List<String> r = new ArrayList<>(dyeNames); r.add(0, "default"); yield r;
            }
            case "sethelmet", "helmet",
                 "setchestplate", "chestplate",
                 "setleggings", "leggings",
                 "setboots", "boots",
                 "setmainhand", "mainhand", "setitem", "item",
                 "setoffhand", "offhand" -> getAllItemNames();
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
