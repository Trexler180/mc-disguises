package com.coffee.disguises.disguise;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.chicken.ChickenSoundVariants;
import net.minecraft.world.entity.animal.cow.CowSoundVariants;
import net.minecraft.world.entity.animal.feline.CatSoundVariants;
import net.minecraft.world.entity.animal.pig.PigSoundVariants;

final class SoundCompat {

    private SoundCompat() {
    }

    static SoundEvent catAmbientSound() {
        return SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC).adultSounds().ambientSound().value();
    }

    static SoundEvent catHurtSound() {
        return SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC).adultSounds().hurtSound().value();
    }

    static SoundEvent catDeathSound() {
        return SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC).adultSounds().deathSound().value();
    }

    static SoundEvent chickenAmbientSound() {
        return SoundEvents.CHICKEN_SOUNDS.get(ChickenSoundVariants.SoundSet.CLASSIC).adultSounds().ambientSound().value();
    }

    static SoundEvent chickenHurtSound() {
        return SoundEvents.CHICKEN_SOUNDS.get(ChickenSoundVariants.SoundSet.CLASSIC).adultSounds().hurtSound().value();
    }

    static SoundEvent chickenDeathSound() {
        return SoundEvents.CHICKEN_SOUNDS.get(ChickenSoundVariants.SoundSet.CLASSIC).adultSounds().deathSound().value();
    }

    static SoundEvent cowAmbientSound() {
        return SoundEvents.COW_SOUNDS.get(CowSoundVariants.SoundSet.CLASSIC).ambientSound().value();
    }

    static SoundEvent cowHurtSound() {
        return SoundEvents.COW_SOUNDS.get(CowSoundVariants.SoundSet.CLASSIC).hurtSound().value();
    }

    static SoundEvent cowDeathSound() {
        return SoundEvents.COW_SOUNDS.get(CowSoundVariants.SoundSet.CLASSIC).deathSound().value();
    }

    static SoundEvent pigAmbientSound() {
        return SoundEvents.PIG_SOUNDS.get(PigSoundVariants.SoundSet.CLASSIC).adultSounds().ambientSound().value();
    }

    static SoundEvent pigHurtSound() {
        return SoundEvents.PIG_SOUNDS.get(PigSoundVariants.SoundSet.CLASSIC).adultSounds().hurtSound().value();
    }

    static SoundEvent pigDeathSound() {
        return SoundEvents.PIG_SOUNDS.get(PigSoundVariants.SoundSet.CLASSIC).adultSounds().deathSound().value();
    }
}
