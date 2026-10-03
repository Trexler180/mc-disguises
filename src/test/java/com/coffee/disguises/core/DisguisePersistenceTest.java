package com.coffee.disguises.core;

import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.disguise.PlayerDisguise;
import com.coffee.disguises.watcher.BlockStateWatcher;
import com.coffee.disguises.watcher.ItemWatcher;
import com.coffee.disguises.watcher.SlimeWatcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisguisePersistenceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // A real server binds item data components when it loads data packs, and
        // ItemStack refuses to exist before that.  These tests only care about item
        // identity and count, so empty component maps stand in for the data load.
        BuiltInRegistries.ITEM.listElements().forEach(item -> {
            if (!item.areComponentsBound()) item.bindComponents(DataComponentMap.EMPTY);
        });
    }

    private static Disguise roundTrip(Disguise disguise) {
        // Through a string, as it goes through the file on disk.
        String json = DisguiseManager.serializeDisguise(disguise).toString();
        return DisguiseManager.deserializeDisguise(JsonParser.parseString(json).getAsJsonObject())
                .orElseThrow();
    }

    @Test
    void customEquipmentSurvives() {
        Disguise disguise = Disguise.builder(DisguiseType.ZOMBIE).build();
        disguise.getWatcher().setCustomEquipment(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        disguise.getWatcher().setCustomEquipment(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));

        var equipment = roundTrip(disguise).getWatcher().getCustomEquipment();

        assertEquals(Items.DIAMOND_SWORD, equipment.get(EquipmentSlot.MAINHAND).getItem());
        assertEquals(Items.CARVED_PUMPKIN, equipment.get(EquipmentSlot.HEAD).getItem());
    }

    @Test
    void itemStackSurvives() {
        Disguise disguise = Disguise.builder(DisguiseType.ITEM).build();
        ((ItemWatcher) disguise.getWatcher()).setItem(new ItemStack(Items.GOLDEN_APPLE, 5));

        ItemStack item = ((ItemWatcher) roundTrip(disguise).getWatcher()).getItem();

        assertEquals(Items.GOLDEN_APPLE, item.getItem());
        assertEquals(5, item.getCount());
    }

    @Test
    void blockStateSurvivesWithItsProperties() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST);
        Disguise disguise = Disguise.builder(DisguiseType.FALLING_BLOCK).build();
        ((BlockStateWatcher) disguise.getWatcher()).setBlock(stairs);

        BlockState restored = ((BlockStateWatcher) roundTrip(disguise).getWatcher()).getBlockState();

        assertEquals(stairs, restored);
    }

    @Test
    void legacyFormatsStillLoad() {
        // Files written before the registry-based format: bare item names and
        // numeric block-state ids.
        BlockState sand = Blocks.SAND.defaultBlockState();
        JsonObject obj = JsonParser.parseString("""
                {"type":"falling_block","selfDisguise":false,"showName":true,
                 "watcher":{"BlockStateWatcher.blockState":%d,
                            "FlagWatcher.customEquipment":{"MAINHAND":"diamond_sword"}}}
                """.formatted(net.minecraft.world.level.block.Block.getId(sand))).getAsJsonObject();

        Disguise disguise = DisguiseManager.deserializeDisguise(obj).orElseThrow();

        assertEquals(sand, ((BlockStateWatcher) disguise.getWatcher()).getBlockState());
        assertEquals(Items.DIAMOND_SWORD,
                disguise.getWatcher().getCustomEquipment().get(EquipmentSlot.MAINHAND).getItem());
    }

    @Test
    void unknownIdsKeepWatcherDefaultsInsteadOfNull() {
        JsonObject obj = JsonParser.parseString("""
                {"type":"falling_block",
                 "watcher":{"BlockStateWatcher.blockState":"nosuchmod:nosuchblock"}}
                """).getAsJsonObject();

        Disguise disguise = DisguiseManager.deserializeDisguise(obj).orElseThrow();

        assertEquals(Blocks.STONE.defaultBlockState(),
                ((BlockStateWatcher) disguise.getWatcher()).getBlockState());
    }

    @Test
    void oversizedSlimeFromFileIsClamped() {
        JsonObject obj = JsonParser.parseString("""
                {"type":"slime","watcher":{"SlimeWatcher.size":2147483647}}
                """).getAsJsonObject();

        Disguise disguise = DisguiseManager.deserializeDisguise(obj).orElseThrow();

        assertEquals(SlimeWatcher.MAX_SIZE, ((SlimeWatcher) disguise.getWatcher()).getSize());
    }

    @Test
    void playerDisguiseKeepsItsName() {
        Disguise restored = roundTrip(PlayerDisguise.builder("Notch").selfDisguise(true).build());

        PlayerDisguise pd = assertInstanceOf(PlayerDisguise.class, restored);
        assertEquals("Notch", pd.getDisguiseName());
        assertTrue(pd.isSelfDisguise());
    }
}
