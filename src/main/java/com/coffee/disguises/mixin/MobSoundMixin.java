package com.coffee.disguises.mixin;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes the ambient sound for disguised mobs.
 *
 * getAmbientSound() is defined on Mob (not LivingEntity) in 1.21.x.
 * Players (which extend LivingEntity directly) don't have an ambient sound
 * method here, so this mixin safely covers all mob-type disguise targets.
 */
@Mixin(Mob.class)
public abstract class MobSoundMixin {

    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
    private void disguises$substituteAmbientSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (!DisguisesMod.CONFIG.disguiseSounds) return;

        Disguise disguise = DisguiseManager.INSTANCE.getDisguise((Entity) (Object) this);
        if (disguise == null) return;

        SoundEvent sound = disguise.getType().getAmbientSound();
        if (sound != null) cir.setReturnValue(sound);
    }
}
