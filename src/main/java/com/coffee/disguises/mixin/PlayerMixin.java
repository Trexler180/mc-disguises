package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.PlayerDisguise;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void disguises$replaceName(CallbackInfoReturnable<Component> cir) {
        Disguise disguise = DisguiseManager.INSTANCE.getDisguise((Player) (Object) this);
        if (disguise != null) {
            if (disguise instanceof PlayerDisguise pd) { cir.setReturnValue(Component.literal(pd.getDisguiseName())); return; }
            cir.setReturnValue(disguise.getType().getEntityType().getDescription());
        }
    }
}
