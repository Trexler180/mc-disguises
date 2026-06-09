package com.coffee.disguises.disguise;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class SoundCompat {

    private SoundCompat() {
    }

    static SoundEvent catAmbientSound() {
        return SoundEvents.CAT_AMBIENT;
    }

    static SoundEvent catHurtSound() {
        return SoundEvents.CAT_HURT;
    }

    static SoundEvent catDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    static SoundEvent chickenAmbientSound() {
        return SoundEvents.CHICKEN_AMBIENT;
    }

    static SoundEvent chickenHurtSound() {
        return SoundEvents.CHICKEN_HURT;
    }

    static SoundEvent chickenDeathSound() {
        return SoundEvents.CHICKEN_DEATH;
    }

    static SoundEvent cowAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    static SoundEvent cowHurtSound() {
        return SoundEvents.COW_HURT;
    }

    static SoundEvent cowDeathSound() {
        return SoundEvents.COW_DEATH;
    }

    static SoundEvent pigAmbientSound() {
        return SoundEvents.PIG_AMBIENT;
    }

    static SoundEvent pigHurtSound() {
        return SoundEvents.PIG_HURT;
    }

    static SoundEvent pigDeathSound() {
        return SoundEvents.PIG_DEATH;
    }
}
