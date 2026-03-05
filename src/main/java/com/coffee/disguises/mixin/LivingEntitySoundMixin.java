package com.coffee.disguises.mixin;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes hurt and death sounds for disguised living entities.
 *
 * NOTE: getAmbientSound() is NOT in LivingEntity — it lives in Mob.
 * Ambient sound substitution is handled by MobSoundMixin.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySoundMixin {

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void disguises$substituteHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (!DisguisesMod.CONFIG.disguiseSounds) return;

        Disguise disguise = DisguiseManager.INSTANCE.getDisguise((Entity) (Object) this);
        if (disguise == null) return;

        SoundEvent sound = disguise.getType().getHurtSound();
        if (sound != null) cir.setReturnValue(sound);
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void disguises$substituteDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (!DisguisesMod.CONFIG.disguiseSounds) return;

        Disguise disguise = DisguiseManager.INSTANCE.getDisguise((Entity) (Object) this);
        if (disguise == null) return;

        SoundEvent sound = disguise.getType().getDeathSound();
        if (sound != null) cir.setReturnValue(sound);
    }
}